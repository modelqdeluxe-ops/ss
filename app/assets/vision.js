/* Rumentis Beta: visión en el teléfono.
   Segmentación de instancias con RF-DETR Seg Nano (Roboflow, Apache 2.0) exportado a ONNX y cuantizado a int8,
   corriendo con onnxruntime-web (MIT) en WebAssembly. Todo se calcula en el teléfono: la foto no sale de ahí.
   - Vision.segmentar(imagen,{clase}): busca al animal (o a la persona) en una foto y devuelve su silueta.
   - Vision.motor(): para video en vivo. Corre el modelo en un Web Worker (el video no se traba) y devuelve por cuadro
     la silueta en una rejilla de 312 (área, caja y ancho de cada fila). Si el worker no arranca, corre en la página.
   - Los archivos están en assets/vision/ (solo en la app beta): ort.bundle.js, ort-wasm-simd-threaded.wasm y seg.onnx.
     En Android se sirven desde https://rumentis.local/ (el WebView los entrega desde assets). */
(function(){
'use strict';
const BASE=window.VISION_BASE||(location.protocol==='file:'?'https://rumentis.local/vision/':'vision/');
const abs=f=>new URL(f,new URL(BASE,location.href)).href;

/* El núcleo: preparar la imagen y leer lo que devuelve el modelo. Se usa igual en la página y dentro del worker
   (se pasa como texto), así que no puede tocar nada de afuera. */
function NUCLEO(){
  const RES=312,MASK=78,MEAN=[0.485,0.456,0.406],STD=[0.229,0.224,0.225];
  // clases COCO (índice de 91): 1 persona, 21 vaca; si no hay vaca, otros animales grandes con forma parecida
  // (el modelo base a veces confunde un bovino con caballo, oveja u oso)
  const PARECIDOS={21:[19,20,22,23,24,25]};
  const sig=x=>1/(1+Math.exp(-x));
  // píxeles RGBA de 312×312 → tensor normalizado (canales separados)
  function prep(d){const n=RES*RES,f=new Float32Array(3*n);
    for(let i=0;i<n;i++){const p=i*4;f[i]=(d[p]/255-MEAN[0])/STD[0];f[n+i]=(d[p+1]/255-MEAN[1])/STD[1];f[2*n+i]=(d[p+2]/255-MEAN[2])/STD[2];}
    return f;}
  // la mejor detección de la clase pedida
  function elegir(L,Q,C,clase,umbral){
    let q=-1,sc=0,cl=clase;
    for(let i=0;i<Q;i++){const v=sig(L[i*C+clase]);if(v>sc){sc=v;q=i;}}
    if(sc<umbral&&PARECIDOS[clase])for(const k of PARECIDOS[clase])for(let i=0;i<Q;i++){const v=sig(L[i*C+k])*.85;if(v>sc){sc=v;q=i;cl=k;}}
    return {q,sc,cl};
  }
  // la silueta en una rejilla de G×G (bilineal desde 78×78): área, caja y ancho de cada fila (en celdas)
  function silueta(Mk,q,G){
    const base=q*MASK*MASK,filas=new Uint16Array(G);let area=0,x0=G,y0=G,x1=-1,y1=-1;
    for(let y=0;y<G;y++){const fy=Math.min(MASK-1,Math.max(0,(y+.5)*MASK/G-.5)),a=Math.floor(fy),b=Math.min(MASK-1,a+1),ty=fy-a;let fx0=G,fx1=-1;
      for(let x=0;x<G;x++){const fx=Math.min(MASK-1,Math.max(0,(x+.5)*MASK/G-.5)),c=Math.floor(fx),d=Math.min(MASK-1,c+1),tx=fx-c;
        const v=(Mk[base+a*MASK+c]*(1-tx)+Mk[base+a*MASK+d]*tx)*(1-ty)+(Mk[base+b*MASK+c]*(1-tx)+Mk[base+b*MASK+d]*tx)*ty;
        if(v>0){area++;if(x<fx0)fx0=x;fx1=x;}}
      if(fx1>=0){filas[y]=fx1-fx0+1;if(fx0<x0)x0=fx0;if(fx1>x1)x1=fx1;if(y<y0)y0=y;y1=y;}}
    return {area,caja:x1<0?null:[x0,y0,x1+1,y1+1],filas};
  }
  // un cuadro de video: todo normalizado a la rejilla de 312 (el cuadro se estira a cuadrado para el modelo)
  function cuadro(out,clase,umbral){
    const L=out.labels.data,Q=out.labels.dims[1],C=out.labels.dims[2],Mk=out.masks.data;
    const {q,sc,cl}=elegir(L,Q,C,clase,umbral);
    if(q<0||sc<umbral)return {ok:false,score:sc};
    const s=silueta(Mk,q,RES),m=new Float32Array(MASK*MASK);m.set(Mk.subarray(q*MASK*MASK,(q+1)*MASK*MASK));
    return {ok:s.area>40,score:sc,clase:cl,G:RES,area:s.area,caja:s.caja,filas:s.filas,mask:m,MASK};
  }
  return {RES,MASK,prep,elegir,silueta,cuadro,sig};
}
const N=NUCLEO(),RES=N.RES,MASK=N.MASK;

/* ---------- en la página ---------- */
let ortP=null,sesP=null;
async function ort(){
  if(!ortP)ortP=import(abs('ort.bundle.js')).then(m=>{
    const o=m.default||m;o.env.wasm.wasmPaths={wasm:abs('ort-wasm-simd-threaded.wasm')};o.env.wasm.numThreads=1;o.env.logLevel='error';return o;});
  ortP.catch(()=>{ortP=null;});return ortP;
}
async function sesion(){
  if(!sesP)sesP=(async()=>{const o=await ort();const r=await fetch(abs('seg.onnx'));
    if(!r.ok)throw new Error('No se encontró el modelo de visión');const b=await r.arrayBuffer();
    return o.InferenceSession.create(b,{executionProviders:['wasm'],graphOptimizationLevel:'all'});})();
  sesP.catch(()=>{sesP=null;});return sesP;
}
// imagen (img, canvas, video o ImageBitmap) a lienzo de trabajo de hasta `max` px de lado
function lienzo(src,max=720){
  const w0=src.naturalWidth||src.videoWidth||src.width,h0=src.naturalHeight||src.videoHeight||src.height;const k=Math.min(1,max/Math.max(w0,h0));
  const c=document.createElement('canvas');c.width=Math.round(w0*k);c.height=Math.round(h0*k);c.getContext('2d').drawImage(src,0,0,c.width,c.height);return c;
}
function pixeles(src){const t=document.createElement('canvas');t.width=t.height=RES;const x=t.getContext('2d');x.drawImage(src,0,0,RES,RES);return x.getImageData(0,0,RES,RES).data;}
/* Busca al animal (clase 21, por defecto) o a la persona (clase 1). Devuelve {ok, score, clase, caja:[x0,y0,x1,y1] en
   px del lienzo, W, H, mask:Uint8Array(W*H), lienzo, ms} o {ok:false, motivo} si no hay uno claro. */
async function segmentar(src,{max=720,umbral=0.35,clase=21}={}){
  const o=await ort(),s=await sesion(),c=lienzo(src,max),W=c.width,H=c.height,t0=performance.now();
  const out=await s.run({input:new o.Tensor('float32',N.prep(pixeles(c)),[1,3,RES,RES])});
  const L=out.labels.data,D=out.dets.data,Mk=out.masks.data;
  const {q,sc,cl}=N.elegir(L,out.labels.dims[1],out.labels.dims[2],clase,umbral);
  const ms=performance.now()-t0;
  if(q<0||sc<umbral)return {ok:false,motivo:'sin-animal',score:sc,ms,lienzo:c,W,H};
  // la silueta: 78×78 logits del modelo, llevados al tamaño del lienzo con interpolación bilineal
  const base=q*MASK*MASK,mask=new Uint8Array(W*H);let area=0;
  for(let y=0;y<H;y++){const fy=Math.min(MASK-1,Math.max(0,(y+.5)*MASK/H-.5)),y0=Math.floor(fy),y1=Math.min(MASK-1,y0+1),ty=fy-y0;
    for(let x=0;x<W;x++){const fx=Math.min(MASK-1,Math.max(0,(x+.5)*MASK/W-.5)),x0=Math.floor(fx),x1=Math.min(MASK-1,x0+1),tx=fx-x0;
      const v=(Mk[base+y0*MASK+x0]*(1-tx)+Mk[base+y0*MASK+x1]*tx)*(1-ty)+(Mk[base+y1*MASK+x0]*(1-tx)+Mk[base+y1*MASK+x1]*tx)*ty;
      if(v>0){mask[y*W+x]=1;area++;}}}
  const d=D.subarray(q*4,q*4+4),caja=[(d[0]-d[2]/2)*W,(d[1]-d[3]/2)*H,(d[0]+d[2]/2)*W,(d[1]+d[3]/2)*H];
  return {ok:area>50,score:sc,clase:cl,caja,W,H,mask,area,lienzo:c,ms};
}

/* ---------- motor para video: Web Worker ----------
   La página se abre desde file://: un worker de otro origen no se permite, así que se arma desde un Blob con el núcleo.
   El worker no pide nada por red: la página le pasa el código de onnxruntime, el .wasm y el modelo ya descargados
   (así funciona igual aunque el WebView no entregue a los workers los archivos de https://rumentis.local/). */
const TRABAJO=`'use strict';
const N=(${NUCLEO.toString()})();let o=null,s=null;
self.onmessage=async e=>{const m=e.data;
  try{
    if(m.t==='init'){
      const u=URL.createObjectURL(new Blob([m.ort],{type:'text/javascript'}));const mod=await import(u);o=mod.default||mod;
      o.env.wasm.wasmBinary=m.wasm;o.env.wasm.numThreads=1;o.env.wasm.proxy=false;o.env.logLevel='error';
      s=await o.InferenceSession.create(new Uint8Array(m.modelo),{executionProviders:['wasm'],graphOptimizationLevel:'all'});
      self.postMessage({t:'listo'});
    }else if(m.t==='run'){
      const t0=performance.now();const out=await s.run({input:new o.Tensor('float32',N.prep(new Uint8ClampedArray(m.px)),[1,3,N.RES,N.RES])});
      const r=N.cuadro(out,m.clase,m.umbral);r.t='res';r.id=m.id;r.ms=performance.now()-t0;
      self.postMessage(r,r.mask?[r.mask.buffer,r.filas.buffer]:[]);
    }
  }catch(err){self.postMessage({t:'error',id:m.id,msg:String(err&&err.message||err)});}
};`;
let motorP=null;
function motor(){
  if(motorP)return motorP;
  motorP=(async()=>{
    let w=null,modo='hilo';
    try{
      const [js,wasm,mod]=await Promise.all(['ort.bundle.js','ort-wasm-simd-threaded.wasm','seg.onnx'].map(async(f,i)=>{
        const r=await fetch(abs(f));if(!r.ok)throw new Error('No se encontró '+f);return i?r.arrayBuffer():r.text();}));
      w=new Worker(URL.createObjectURL(new Blob([TRABAJO],{type:'text/javascript'})));
      await new Promise((ok,mal)=>{const t=setTimeout(()=>mal(new Error('El worker no respondió')),60000);
        w.onmessage=e=>{if(e.data.t==='listo'){clearTimeout(t);ok();}else if(e.data.t==='error'){clearTimeout(t);mal(new Error(e.data.msg));}};
        w.onerror=e=>{clearTimeout(t);mal(new Error(e.message||'worker'));};
        w.postMessage({t:'init',ort:js,wasm,modelo:mod},[wasm,mod]);});
      modo='worker';
    }catch(e){if(w)w.terminate();w=null;console.warn('Visión en la página (sin worker):',e&&e.message);}
    if(!w)await sesion();
    let id=0;const esperan=new Map();
    if(w){w.onmessage=e=>{const m=e.data,f=esperan.get(m.id);if(!f)return;esperan.delete(m.id);m.t==='error'?f.mal(new Error(m.msg)):f.ok(m);};
      w.onerror=e=>{for(const f of esperan.values())f.mal(new Error(e.message||'worker'));esperan.clear();};}
    /* correr(px, {clase, umbral}): px son los píxeles RGBA de un cuadro llevado a 312×312 (el buffer se transfiere).
       Devuelve {ok, score, clase, G:312, area, caja:[x0,y0,x1,y1], filas, mask(78×78 logits), MASK, ms}. */
    async function correr(px,{clase=21,umbral=0.3}={}){
      if(w)return new Promise((ok,mal)=>{const k=++id;esperan.set(k,{ok,mal});w.postMessage({t:'run',id:k,px:px.buffer,clase,umbral},[px.buffer]);});
      const o=await ort(),s=await sesion(),t0=performance.now();
      const out=await s.run({input:new o.Tensor('float32',N.prep(px),[1,3,RES,RES])});
      const r=N.cuadro(out,clase,umbral);r.ms=performance.now()-t0;return r;
    }
    return {modo,correr,cerrar(){if(w)w.terminate();w=null;motorP=null;}};
  })();
  motorP.catch(()=>{motorP=null;});
  return motorP;
}
window.Vision={segmentar,motor,cargar:async()=>{await sesion();return true;},listo:()=>!!sesP,BASE,RES,MASK,sig:N.sig,
  // clases COCO que usa la app
  PERSONA:1,VACA:21};
})();
