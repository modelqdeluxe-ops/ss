/* Rumentis Beta: visión en el teléfono. Todo se calcula aquí: el video no sale del teléfono.
   Dos modelos, cada uno en su Web Worker (el video no se traba) con onnxruntime-web (MIT) en WebAssembly:
   - rápido  (silueta.onnx, 13 MB): LR-ASPP MobileNetV3 (torchvision, BSD-3) afinado para fondo / persona / vaca
             (modelo/vision/entrenar_silueta.py). Corre en cada cuadro del video: ~60–90 ms en una PC.
   - preciso (seg.onnx, 33 MB): RF-DETR Seg Nano (Roboflow, Apache 2.0), int8. Solo en las fotos que se capturan,
             en segundo plano, para la medida final (~1.4 s en una PC).
   Vision.motor('rapido'|'preciso') → {modo, correr(px, clases)}; px son los píxeles RGBA del cuadro llevado a R×R
   (motor.R). Devuelve por clase COCO (1 persona, 21 vaca) la silueta en una rejilla G×G que cubre todo el cuadro:
   {ok, score, area, caja:[x0,y0,x1,y1], filas (ancho por fila), mask (Uint8 G×G)}.
   Los archivos están en assets/vision/ (solo en la app beta); en Android se sirven desde https://rumentis.local/. */
(function(){
'use strict';
const BASE=window.VISION_BASE||(location.protocol==='file:'?'https://rumentis.local/vision/':'vision/');
const abs=f=>new URL(f,new URL(BASE,location.href)).href;
const MODELOS={rapido:{archivo:'silueta.onnx',R:320,G:160,tipo:'sem'},preciso:{archivo:'seg.onnx',R:312,G:312,tipo:'detr'}};

/* El núcleo: preparar el cuadro y leer lo que devuelve el modelo. Se usa igual en la página y dentro del worker
   (se pasa como texto), así que no puede tocar nada de afuera. */
function NUCLEO(){
  const MEAN=[0.485,0.456,0.406],STD=[0.229,0.224,0.225];
  // RF-DETR: si no hay vaca, otros animales grandes con forma parecida (a veces confunde un bovino con caballo u oveja)
  const PARECIDOS={21:[19,20,22,23,24,25]},SEM={1:1,21:2};   // clase COCO → canal del modelo rápido
  const sig=x=>1/(1+Math.exp(-x));
  function prep(d,R){const n=R*R,f=new Float32Array(3*n);
    for(let i=0;i<n;i++){const p=i*4;f[i]=(d[p]/255-MEAN[0])/STD[0];f[n+i]=(d[p+1]/255-MEAN[1])/STD[1];f[2*n+i]=(d[p+2]/255-MEAN[2])/STD[2];}
    return f;}
  /* margen (logits, >0 es la clase) de mw×mh llevado a G×G con interpolación bilineal; se queda con la mancha más
     grande (así no se suma otra persona o un pedazo suelto) */
  function silueta(M,mw,mh,G){
    const v=new Float32Array(G*G);
    for(let y=0;y<G;y++){const fy=Math.min(mh-1,Math.max(0,(y+.5)*mh/G-.5)),a=Math.floor(fy),b=Math.min(mh-1,a+1),ty=fy-a;
      for(let x=0;x<G;x++){const fx=Math.min(mw-1,Math.max(0,(x+.5)*mw/G-.5)),c=Math.floor(fx),d=Math.min(mw-1,c+1),tx=fx-c;
        v[y*G+x]=(M[a*mw+c]*(1-tx)+M[a*mw+d]*tx)*(1-ty)+(M[b*mw+c]*(1-tx)+M[b*mw+d]*tx)*ty;}}
    const lab=new Int32Array(G*G),pila=new Int32Array(G*G);let mejor=0,mejorN=0,id=0;
    for(let i=0;i<G*G;i++){if(v[i]<=0||lab[i])continue;id++;let n=0,t=0;pila[t++]=i;lab[i]=id;
      while(t){const j=pila[--t];n++;const x=j%G,y=(j-x)/G;
        if(x>0&&v[j-1]>0&&!lab[j-1]){lab[j-1]=id;pila[t++]=j-1;}if(x<G-1&&v[j+1]>0&&!lab[j+1]){lab[j+1]=id;pila[t++]=j+1;}
        if(y>0&&v[j-G]>0&&!lab[j-G]){lab[j-G]=id;pila[t++]=j-G;}if(y<G-1&&v[j+G]>0&&!lab[j+G]){lab[j+G]=id;pila[t++]=j+G;}}
      if(n>mejorN){mejorN=n;mejor=id;}}
    if(!mejor)return {ok:false,area:0};
    const mask=new Uint8Array(G*G),filas=new Uint16Array(G);let x0=G,y0=G,x1=-1,y1=-1,s=0;
    for(let y=0;y<G;y++){let a=G,b=-1;for(let x=0;x<G;x++){const i=y*G+x;if(lab[i]!==mejor)continue;mask[i]=1;s+=sig(v[i]);if(x<a)a=x;b=x;}
      if(b>=0){filas[y]=b-a+1;if(a<x0)x0=a;if(b>x1)x1=b;if(y<y0)y0=y;y1=y;}}
    return {ok:mejorN>G*G*.004,area:mejorN,caja:[x0,y0,x1+1,y1+1],filas,mask,conf:s/mejorN};
  }
  // un cuadro: la silueta de cada clase pedida
  function cuadro(out,tipo,clases,G){
    const r={G,suj:{}};
    if(tipo==='sem'){
      const o=out.logits,[,C,h,w]=o.dims,L=o.data,n=h*w;
      for(const cl of clases){const k=SEM[cl],M=new Float32Array(n);
        for(let i=0;i<n;i++){let m=-1e9;for(let c=0;c<C;c++)if(c!==k&&L[c*n+i]>m)m=L[c*n+i];M[i]=L[k*n+i]-m;}
        const s=silueta(M,w,h,G);s.score=s.ok?s.conf:0;r.suj[cl]=s;}
    }else{
      const L=out.labels.data,Q=out.labels.dims[1],C=out.labels.dims[2],Mk=out.masks.data,mw=out.masks.dims[3];
      for(const cl of clases){let q=-1,sc=0;
        for(let i=0;i<Q;i++){const v=sig(L[i*C+cl]);if(v>sc){sc=v;q=i;}}
        if(sc<.35&&PARECIDOS[cl])for(const k of PARECIDOS[cl])for(let i=0;i<Q;i++){const v=sig(L[i*C+k])*.85;if(v>sc){sc=v;q=i;}}
        if(q<0||sc<.3){r.suj[cl]={ok:false,area:0,score:sc};continue;}
        const s=silueta(Mk.subarray(q*mw*mw,(q+1)*mw*mw),mw,mw,G);s.score=sc;r.suj[cl]=s;}
    }
    return r;
  }
  return {prep,silueta,cuadro,sig};
}
const N=NUCLEO();

/* ---------- el worker ----------
   La página se abre desde file://: un worker de otro origen no se permite, así que se arma desde un Blob con el núcleo.
   El worker no pide nada por red: la página le pasa el código de onnxruntime, el .wasm y el modelo ya descargados
   (así funciona igual aunque el WebView no entregue a los workers los archivos de https://rumentis.local/). */
const TRABAJO=`'use strict';
const N=(${NUCLEO.toString()})();let o=null,s=null,cfg=null;
self.onmessage=async e=>{const m=e.data;
  try{
    if(m.t==='init'){cfg=m.cfg;
      const u=URL.createObjectURL(new Blob([m.ort],{type:'text/javascript'}));const mod=await import(u);o=mod.default||mod;
      o.env.wasm.wasmBinary=m.wasm;o.env.wasm.numThreads=1;o.env.wasm.proxy=false;o.env.logLevel='error';
      s=await o.InferenceSession.create(new Uint8Array(m.modelo),{executionProviders:['wasm'],graphOptimizationLevel:'all'});
      self.postMessage({t:'listo'});
    }else if(m.t==='run'){
      const t0=performance.now();const out=await s.run({input:new o.Tensor('float32',N.prep(new Uint8ClampedArray(m.px),cfg.R),[1,3,cfg.R,cfg.R])});
      const r=N.cuadro(out,cfg.tipo,m.clases,cfg.G);r.t='res';r.id=m.id;r.ms=performance.now()-t0;
      const t=[];for(const k in r.suj){const x=r.suj[k];if(x.mask)t.push(x.mask.buffer,x.filas.buffer);}
      self.postMessage(r,t);
    }
  }catch(err){self.postMessage({t:'error',id:m.id,msg:String(err&&err.message||err)});}
};`;
let ortP=null,bytesP={};
async function ort(){
  if(!ortP)ortP=import(abs('ort.bundle.js')).then(m=>{
    const o=m.default||m;o.env.wasm.wasmPaths={wasm:abs('ort-wasm-simd-threaded.wasm')};o.env.wasm.numThreads=1;o.env.logLevel='error';return o;});
  ortP.catch(()=>{ortP=null;});return ortP;
}
// descarga una sola vez (el .wasm lo usan los dos motores)
function bajar(f,texto){if(!bytesP[f]){bytesP[f]=fetch(abs(f)).then(r=>{if(!r.ok)throw new Error('No se encontró '+f);return texto?r.text():r.arrayBuffer();});bytesP[f].catch(()=>{delete bytesP[f];});}return bytesP[f];}
const motores={};
function motor(nombre='rapido'){
  if(motores[nombre])return motores[nombre];
  const cfg=MODELOS[nombre];
  const p=(async()=>{
    let w=null,modo='hilo',ses=null;
    try{
      const [js,wasm,mod]=await Promise.all([bajar('ort.bundle.js',true),bajar('ort-wasm-simd-threaded.wasm').then(b=>b.slice(0)),bajar(cfg.archivo).then(b=>b.slice(0))]);
      w=new Worker(URL.createObjectURL(new Blob([TRABAJO],{type:'text/javascript'})));
      await new Promise((ok,mal)=>{const t=setTimeout(()=>mal(new Error('El worker no respondió')),90000);
        w.onmessage=e=>{if(e.data.t==='listo'){clearTimeout(t);ok();}else if(e.data.t==='error'){clearTimeout(t);mal(new Error(e.data.msg));}};
        w.onerror=e=>{clearTimeout(t);mal(new Error(e.message||'worker'));};
        w.postMessage({t:'init',ort:js,wasm,modelo:mod,cfg},[wasm,mod]);});
      modo='worker';delete bytesP[cfg.archivo];   // el modelo ya vive en el worker
    }catch(e){if(w)w.terminate();w=null;console.warn('Visión en la página (sin worker):',e&&e.message);}
    if(!w){const o=await ort();ses=await o.InferenceSession.create(new Uint8Array(await bajar(cfg.archivo)),{executionProviders:['wasm'],graphOptimizationLevel:'all'});}
    let id=0;const esperan=new Map();
    if(w){w.onmessage=e=>{const m=e.data,f=esperan.get(m.id);if(!f)return;esperan.delete(m.id);m.t==='error'?f.mal(new Error(m.msg)):f.ok(m);};
      w.onerror=e=>{for(const f of esperan.values())f.mal(new Error(e.message||'worker'));esperan.clear();};}
    async function correr(px,clases){
      if(w)return new Promise((ok,mal)=>{const k=++id;esperan.set(k,{ok,mal});w.postMessage({t:'run',id:k,px:px.buffer,clases},[px.buffer]);});
      const o=await ort(),t0=performance.now();
      const out=await ses.run({input:new o.Tensor('float32',N.prep(px,cfg.R),[1,3,cfg.R,cfg.R])});
      const r=N.cuadro(out,cfg.tipo,clases,cfg.G);r.ms=performance.now()-t0;return r;
    }
    return {modo,R:cfg.R,G:cfg.G,correr};
  })();
  motores[nombre]=p;p.catch(()=>{delete motores[nombre];});
  return p;
}
window.Vision={motor,BASE,MODELOS,silueta:N.silueta,
  // clases COCO que usa la app
  PERSONA:1,VACA:21};
})();
