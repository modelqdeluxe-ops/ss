/* Rumentis Beta: visión en el teléfono. Todo se calcula aquí: el video no sale del teléfono.
   Modelos con onnxruntime-web (MIT) en WebAssembly, en Web Workers (el video no se traba):
   - rápido  (silueta.onnx, 13 MB, ganado; silueta_p.onnx, personas): LR-ASPP MobileNetV3 (torchvision, BSD-3) afinado
             para fondo / persona / vaca (modelo/vision/entrenar_silueta.py; el de personas con un refinamiento a 1/4,
             entrenar_silueta2.py). Entrada 256×256. Corre en cada cuadro del video, en dos workers a la
             vez si el teléfono tiene núcleos de sobra (el doble de cuadros por segundo).
   - preciso (seg.onnx, 33 MB): RF-DETR Seg Nano (Roboflow, Apache 2.0), int8. Solo en las fotos que se capturan,
             en segundo plano, para la medida final.
   - pose    (cuerpo.onnx, animal_m.onnx): RTMW y RTMPose (OpenMMLab, Apache 2.0). Los puntos del cuerpo (hasta los
             dedos), en el video y en las fotos, para ver y medir cada parte donde va.
   Los pesos de los modelos van guardados en 16 bits (la mitad de tamaño) y se pasan a 32 al cargar: el cálculo es el
   mismo (modelo/vision/pesos16.py).
   Vision.motor(nombre) → {modo, R, W, H, G, n, correr(entrada, clases)}. La entrada es {bmp} (un ImageBitmap
   del cuadro ya recortado y reducido a W×H: el worker lo lee sin pasar por la página) o {px} (píxeles RGBA de W×H).
   Los de pose devuelven {kp}: x, y, confianza por punto (x, y en fracciones de la entrada).
   Devuelve por clase COCO (1 persona, 21 vaca) la silueta en una rejilla G×G que cubre la entrada:
   {ok, score, area, caja:[x0,y0,x1,y1], filas (ancho por fila), mask (Uint8 G×G), cont (contorno: x,y,x,y… en celdas)}.
   Los archivos están en assets/vision/ (solo en la app beta); en Android se sirven desde https://rumentis.local/. */
(function(){
'use strict';
const BASE=window.VISION_BASE||(location.protocol==='file:'?'https://rumentis.local/vision/':'vision/');
const abs=f=>new URL(f,new URL(BASE,location.href)).href;
const NUC=(navigator.hardwareConcurrency||4);
// copias del modelo rápido en paralelo: 3 en teléfonos de 8 núcleos, 2 con 6, 1 con menos (la cámara da ~30 cuadros/s)
// rapidoP: el de personas (v2, con refinamiento a 1/4: bordes más finos); rapido: el de ganado (v1, mejor en vacas)
/* pose (puntos del cuerpo, como un traje de captura de movimiento), de OpenMMLab (Apache 2.0):
   cuerpo: RTMW (cuerpo completo, 133 puntos: cuerpo, pies, cara, manos y dedos), en el video y en las fotos;
   animal: RTMPose de animales (AP-10K, 17 puntos). La entrada es un recorte alrededor del sujeto de W×H; devuelve los
   puntos en fracciones del recorte. */
const MODELOS={rapido:{archivo:'silueta.onnx',R:256,G:128,tipo:'sem',n:NUC>=8?3:NUC>=6?2:1},
  rapidoP:{archivo:'silueta_p.onnx',R:256,G:128,tipo:'sem',n:NUC>=8?3:NUC>=6?2:1},preciso:{archivo:'seg.onnx',R:312,G:312,tipo:'detr',n:1},
  cuerpo:{archivo:'cuerpo.onnx',W:192,H:256,tipo:'pose',n:1},
  animal:{archivo:'animal_m.onnx',W:256,H:256,tipo:'pose',n:1}};
for(const k in MODELOS){const m=MODELOS[k];m.W=m.W||m.R;m.H=m.H||m.R;m.R=m.R||m.W;}
/* los puntos y cómo se unen. Personas (COCO-WholeBody): 0 nariz, 1-2 ojos, 3-4 orejas, 5-6 hombros, 7-8 codos,
   9-10 muñecas, 11-12 caderas, 13-14 rodillas, 15-16 tobillos (izquierda, derecha); 17-22 pies (dedo gordo, dedo
   chico y talón de cada uno); 23-90 cara; 91-111 mano izquierda y 112-132 derecha (raíz y 4 puntos por dedo, del
   pulgar al meñique). Animales (AP-10K): 0-1 ojos, 2 nariz, 3 cuello, 4 base de la cola, 5-7 hombro, codo y pata
   delantera izquierdos, 8-10 los derechos, 11-13 cadera, rodilla y pata trasera izquierdas, 14-16 las derechas. */
const mano=r=>[0,1,2,3,4].flatMap(d=>[[r,r+1+4*d],[r+1+4*d,r+2+4*d],[r+2+4*d,r+3+4*d],[r+3+4*d,r+4+4*d]]);
const ESQUELETO={persona:[[5,6],[5,7],[7,9],[6,8],[8,10],[5,11],[6,12],[11,12],[11,13],[13,15],[12,14],[14,16],[0,5],[0,6],
    [15,17],[15,18],[15,19],[16,20],[16,21],[16,22],...mano(91),...mano(112)],
  animal:[[0,2],[1,2],[2,3],[3,4],[3,5],[5,6],[6,7],[3,8],[8,9],[9,10],[4,11],[11,12],[12,13],[4,14],[14,15],[15,16]]};

/* El núcleo: preparar el cuadro y leer lo que devuelve el modelo. Se usa igual en la página y dentro del worker
   (se pasa como texto), así que no puede tocar nada de afuera. */
function NUCLEO(){
  const MEAN=[0.485,0.456,0.406],STD=[0.229,0.224,0.225];
  // RF-DETR: si no hay vaca, otros animales grandes con forma parecida (a veces confunde un bovino con caballo u oveja)
  const PARECIDOS={21:[19,20,22,23,24,25]},SEM={1:1,21:2};   // clase COCO → canal del modelo rápido
  const sig=x=>1/(1+Math.exp(-x));
  function prep(d,n){const f=new Float32Array(3*n);
    for(let i=0;i<n;i++){const p=i*4;f[i]=(d[p]/255-MEAN[0])/STD[0];f[n+i]=(d[p+1]/255-MEAN[1])/STD[1];f[2*n+i]=(d[p+2]/255-MEAN[2])/STD[2];}
    return f;}
  /* el borde de la silueta como una línea: se recorre el contorno exterior (vecinos de Moore, en sentido horario) y se
     suaviza con un promedio móvil; x,y en celdas de la rejilla */
  const DX=[1,1,0,-1,-1,-1,0,1],DY=[0,1,1,1,0,-1,-1,-1];
  function contorno(mask,G){
    let s=-1;for(let i=0;i<G*G;i++)if(mask[i]){s=i;break;}if(s<0)return new Float32Array(0);
    const en=(x,y)=>x>=0&&y>=0&&x<G&&y<G&&mask[y*G+x]===1,dir=(dx,dy)=>DX.findIndex((v,i)=>v===dx&&DY[i]===dy);
    const x0=s%G,y0=(s-x0)/G,P=[];let x=x0,y=y0,d=4,p1=null;
    for(let k=0;k<G*G*2;k++){
      let nx=-1,ny=-1,nd=0;
      for(let j=1;j<=8;j++){const dd=(d+j)%8;if(en(x+DX[dd],y+DY[dd])){nx=x+DX[dd];ny=y+DY[dd];const b=(d+j+7)%8;nd=dir(x+DX[b]-nx,y+DY[b]-ny);break;}}
      if(nx<0){P.push(x,y);break;}   // una sola celda
      if(k===0)p1=[nx,ny];else if(x===x0&&y===y0&&nx===p1[0]&&ny===p1[1])break;
      P.push(x,y);x=nx;y=ny;d=nd;
    }
    const n=P.length/2,w=Math.max(1,Math.round(G/64)),out=[],paso=Math.max(1,Math.floor(n/240));
    for(let i=0;i<n;i+=paso){let sx=0,sy=0,c=0;for(let j=-w;j<=w;j++){const k=((i+j)%n+n)%n;sx+=P[2*k];sy+=P[2*k+1];c++;}out.push(sx/c+.5,sy/c+.5);}
    return new Float32Array(out);
  }
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
    return {ok:mejorN>G*G*.004,area:mejorN,caja:[x0,y0,x1+1,y1+1],filas,mask,conf:s/mejorN,cont:contorno(mask,G)};
  }
  // un cuadro: la silueta de cada clase pedida
  /* pose (SimCC): por punto, dos curvas (x e y, a medio píxel de la entrada); el máximo de cada una con una parábola
     alrededor (fracción de píxel). Puntos en fracciones de la entrada y su confianza (la menor de las dos). */
  function pico(v,o,n){let j=0,m=-1e9;for(let i=0;i<n;i++)if(v[o+i]>m){m=v[o+i];j=i;}let x=j;
    if(j>0&&j<n-1){const a=v[o+j-1],c=v[o+j+1],d=a-2*m+c;if(d<0)x+=.5*(a-c)/d;}return [x,m];}
  function puntos(out){const X=out.simcc_x,Y=out.simcc_y,K=X.dims[1],nx=X.dims[2],ny=Y.dims[2],kp=new Float32Array(K*3);
    for(let k=0;k<K;k++){const [x,cx]=pico(X.data,k*nx,nx),[y,cy]=pico(Y.data,k*ny,ny);kp[3*k]=(x+.5)/nx;kp[3*k+1]=(y+.5)/ny;kp[3*k+2]=Math.min(cx,cy);}
    return kp;}
  function cuadro(out,tipo,clases,G){
    const r={G,suj:{}};
    if(tipo==='pose'){r.kp=puntos(out);return r;}
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
  const transferir=r=>{const t=r.kp?[r.kp.buffer]:[];for(const k in r.suj){const x=r.suj[k];if(x.mask)t.push(x.mask.buffer,x.filas.buffer,x.cont.buffer);}return t;};
  return {prep,silueta,contorno,cuadro,sig,transferir,puntos};
}
const N=NUCLEO();

/* ---------- el worker ----------
   La página se abre desde file://: un worker de otro origen no se permite, así que se arma desde un Blob con el núcleo.
   El worker no pide nada por red: la página le pasa el código de onnxruntime, el .wasm y el modelo ya descargados
   (así funciona igual aunque el WebView no entregue a los workers los archivos de https://rumentis.local/). */
const TRABAJO=`'use strict';
const N=(${NUCLEO.toString()})();let o=null,s=null,cfg=null,oc=null,ox=null;
self.onmessage=async e=>{const m=e.data;
  try{
    if(m.t==='init'){cfg=m.cfg;
      const u=URL.createObjectURL(new Blob([m.ort],{type:'text/javascript'}));const mod=await import(u);o=mod.default||mod;
      o.env.wasm.wasmBinary=m.wasm;o.env.wasm.numThreads=1;o.env.wasm.proxy=false;o.env.logLevel='error';
      s=await o.InferenceSession.create(new Uint8Array(m.modelo),{executionProviders:['wasm'],graphOptimizationLevel:'all'});
      self.postMessage({t:'listo',bmp:typeof OffscreenCanvas!=='undefined'});
    }else if(m.t==='run'){
      const t0=performance.now();let px=m.px?new Uint8ClampedArray(m.px):null;
      if(m.bmp){if(!oc){oc=new OffscreenCanvas(cfg.W,cfg.H);ox=oc.getContext('2d',{willReadFrequently:true});}
        ox.drawImage(m.bmp,0,0,cfg.W,cfg.H);m.bmp.close();px=ox.getImageData(0,0,cfg.W,cfg.H).data;}
      const out=await s.run({input:new o.Tensor('float32',N.prep(px,cfg.W*cfg.H),[1,3,cfg.H,cfg.W])});
      const r=N.cuadro(out,cfg.tipo,m.clases,cfg.G);r.t='res';r.id=m.id;r.ms=performance.now()-t0;
      self.postMessage(r,N.transferir(r));
    }
  }catch(err){self.postMessage({t:'error',id:m.id,msg:String(err&&err.message||err)});}
};`;
let ortP=null,bytesP={};
async function ort(){
  if(!ortP)ortP=import(abs('ort.bundle.js')).then(m=>{
    const o=m.default||m;o.env.wasm.wasmPaths={wasm:abs('ort-wasm-simd-threaded.wasm')};o.env.wasm.numThreads=1;o.env.logLevel='error';return o;});
  ortP.catch(()=>{ortP=null;});return ortP;
}
// descarga una sola vez (el .wasm lo usan todos los workers)
function bajar(f,texto){if(!bytesP[f]){bytesP[f]=fetch(abs(f)).then(r=>{if(!r.ok)throw new Error('No se encontró '+f);return texto?r.text():r.arrayBuffer();});bytesP[f].catch(()=>{delete bytesP[f];});}return bytesP[f];}
async function unWorker(cfg){
  const [js,wasm,mod]=await Promise.all([bajar('ort.bundle.js',true),bajar('ort-wasm-simd-threaded.wasm').then(b=>b.slice(0)),bajar(cfg.archivo).then(b=>b.slice(0))]);
  const w=new Worker(URL.createObjectURL(new Blob([TRABAJO],{type:'text/javascript'})));
  try{
    const bmp=await new Promise((ok,mal)=>{const t=setTimeout(()=>mal(new Error('El worker no respondió')),90000);
      w.onmessage=e=>{if(e.data.t==='listo'){clearTimeout(t);ok(e.data.bmp);}else if(e.data.t==='error'){clearTimeout(t);mal(new Error(e.data.msg));}};
      w.onerror=e=>{clearTimeout(t);mal(new Error(e.message||'worker'));};
      w.postMessage({t:'init',ort:js,wasm,modelo:mod,cfg},[wasm,mod]);});
    const esperan=new Map();
    w.onmessage=e=>{const m=e.data,f=esperan.get(m.id);if(!f)return;esperan.delete(m.id);m.t==='error'?f.mal(new Error(m.msg)):f.ok(m);};
    w.onerror=e=>{for(const f of esperan.values())f.mal(new Error(e.message||'worker'));esperan.clear();};
    return {w,esperan,bmp};
  }catch(e){w.terminate();throw e;}
}
const motores={};
function motor(nombre='rapido'){
  if(motores[nombre])return motores[nombre];
  const cfg=MODELOS[nombre];
  const p=(async()=>{
    let ws=[],modo='hilo',ses=null;
    try{ws.push(await unWorker(cfg));modo='worker';
      // los demás workers se suman cuando estén listos (el primero ya sirve)
      for(let i=1;i<cfg.n;i++)unWorker(cfg).then(x=>ws.push(x)).catch(()=>{});
    }catch(e){console.warn('Visión en la página (sin worker):',e&&e.message);}
    if(!ws.length){const o=await ort();ses=await o.InferenceSession.create(new Uint8Array(await bajar(cfg.archivo)),{executionProviders:['wasm'],graphOptimizationLevel:'all'});}
    else if(cfg.n===1)delete bytesP[cfg.archivo];   // el modelo ya vive en el worker
    let id=0;const cv=document.createElement('canvas');cv.width=cfg.W;cv.height=cfg.H;const cx=cv.getContext('2d',{willReadFrequently:true});
    // en la página: el ImageBitmap se pasa a píxeles aquí
    const pixeles=e=>{if(e.px)return e.px;cx.drawImage(e.bmp,0,0,cfg.W,cfg.H);e.bmp.close&&e.bmp.close();return cx.getImageData(0,0,cfg.W,cfg.H).data;};
    function correr(e,clases){
      if(ws.length){const x=ws.reduce((a,b)=>b.esperan.size<a.esperan.size?b:a);
        if(e.bmp&&!x.bmp)e={px:pixeles(e)};
        return new Promise((ok,mal)=>{const k=++id;x.esperan.set(k,{ok,mal});
          if(e.bmp)x.w.postMessage({t:'run',id:k,bmp:e.bmp,clases},[e.bmp]);else x.w.postMessage({t:'run',id:k,px:e.px.buffer,clases},[e.px.buffer]);});}
      return (async()=>{const o=await ort(),t0=performance.now(),px=pixeles(e);
        const out=await ses.run({input:new o.Tensor('float32',N.prep(px,cfg.W*cfg.H),[1,3,cfg.H,cfg.W])});
        const r=N.cuadro(out,cfg.tipo,clases,cfg.G);r.ms=performance.now()-t0;return r;})();
    }
    return {modo,R:cfg.R,W:cfg.W,H:cfg.H,G:cfg.G,get n(){return Math.max(1,ws.length);},correr};
  })();
  motores[nombre]=p;p.catch(()=>{delete motores[nombre];});
  return p;
}
window.Vision={motor,BASE,MODELOS,ESQUELETO,silueta:N.silueta,contorno:N.contorno,puntos:N.puntos,
  // clases COCO que usa la app
  PERSONA:1,VACA:21};
})();
