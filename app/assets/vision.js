/* Rumentis Beta: visión en el teléfono.
   Segmentación de instancias con RF-DETR Seg Nano (Roboflow, Apache 2.0) exportado a ONNX y cuantizado a int8,
   corriendo con onnxruntime-web (MIT) en WebAssembly. Todo se calcula en el teléfono: la foto no sale de ahí.
   - Vision.segmentar(imagen): busca al animal (bovino) y devuelve su silueta (máscara) y qué tan seguro está.
   - Los archivos están en assets/vision/ (solo en la app beta): ort.bundle.js, ort-wasm-simd-threaded.wasm y seg.onnx.
     En Android se sirven desde https://rumentis.local/ (el WebView los entrega desde assets). */
(function(){
'use strict';
const BASE=window.VISION_BASE||(location.protocol==='file:'?'https://rumentis.local/vision/':'vision/');
const RES=312,MASK=78;
// clases COCO (índice de 91): 21 vaca; si no hay vaca, otros animales grandes con forma parecida (el modelo base
// a veces confunde un bovino con caballo, oveja u oso)
const VACA=21,PARECIDOS=[19,20,22,23,24,25];
const MEAN=[0.485,0.456,0.406],STD=[0.229,0.224,0.225];
let ortP=null,sesP=null;
async function ort(){
  if(!ortP)ortP=import(new URL('ort.bundle.js',new URL(BASE,location.href)).href).then(m=>{
    const o=m.default||m;o.env.wasm.wasmPaths={wasm:new URL('ort-wasm-simd-threaded.wasm',new URL(BASE,location.href)).href};o.env.wasm.numThreads=1;o.env.logLevel='error';return o;});
  return ortP;
}
async function sesion(){
  if(!sesP)sesP=(async()=>{const o=await ort();const r=await fetch(new URL('seg.onnx',new URL(BASE,location.href)).href);
    if(!r.ok)throw new Error('No se encontró el modelo de visión');const b=await r.arrayBuffer();
    return o.InferenceSession.create(b,{executionProviders:['wasm'],graphOptimizationLevel:'all'});})();
  sesP.catch(()=>{sesP=null;});return sesP;
}
const sig=x=>1/(1+Math.exp(-x));
// imagen (img, canvas, video o ImageBitmap) a lienzo de trabajo de hasta `max` px de lado
function lienzo(src,max=720){
  const w0=src.naturalWidth||src.videoWidth||src.width,h0=src.naturalHeight||src.videoHeight||src.height;const k=Math.min(1,max/Math.max(w0,h0));
  const c=document.createElement('canvas');c.width=Math.round(w0*k);c.height=Math.round(h0*k);c.getContext('2d').drawImage(src,0,0,c.width,c.height);return c;
}
function tensor(o,c){
  const t=document.createElement('canvas');t.width=t.height=RES;const x=t.getContext('2d');x.drawImage(c,0,0,RES,RES);
  const d=x.getImageData(0,0,RES,RES).data,n=RES*RES,f=new Float32Array(3*n);
  for(let i=0;i<n;i++){const p=i*4;f[i]=(d[p]/255-MEAN[0])/STD[0];f[n+i]=(d[p+1]/255-MEAN[1])/STD[1];f[2*n+i]=(d[p+2]/255-MEAN[2])/STD[2];}
  return new o.Tensor('float32',f,[1,3,RES,RES]);
}
/* Busca al animal. Devuelve {ok, score, clase, caja:[x0,y0,x1,y1] en px del lienzo, W, H, mask:Uint8Array(W*H), lienzo, ms}
   o {ok:false, motivo} si no hay un animal claro. */
async function segmentar(src,{max=720,umbral=0.35}={}){
  const o=await ort(),s=await sesion(),c=lienzo(src,max),W=c.width,H=c.height,t0=performance.now();
  const out=await s.run({input:tensor(o,c)});
  const L=out.labels.data,D=out.dets.data,Mk=out.masks.data,Q=out.labels.dims[1],C=out.labels.dims[2];
  let q=-1,sc=0,cl=VACA;
  for(let i=0;i<Q;i++){const v=sig(L[i*C+VACA]);if(v>sc){sc=v;q=i;}}
  if(sc<umbral){for(const k of PARECIDOS)for(let i=0;i<Q;i++){const v=sig(L[i*C+k])*.85;if(v>sc){sc=v;q=i;cl=k;}}}
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
window.Vision={segmentar,cargar:async()=>{await sesion();return true;},listo:()=>!!sesP,BASE};
})();
