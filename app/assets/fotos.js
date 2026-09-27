/* Fotos de los animales: se toman con la cámara dentro de la app y se guardan en el teléfono
   (IndexedDB, por id de animal). Nunca salen del teléfono.
   Uso en la página: <img data-foto="ID"> se llena solo; Fotos.tomar() devuelve un Blob JPEG o null. */
(function(){
'use strict';
const DBN='rumentis-fotos',ST='fotos';
let dbp=null;
function db(){
  if(!dbp)dbp=new Promise((ok,mal)=>{try{const r=indexedDB.open(DBN,1);r.onupgradeneeded=()=>r.result.createObjectStore(ST);r.onsuccess=()=>ok(r.result);r.onerror=()=>mal(r.error);}catch(e){mal(e);}});
  return dbp;
}
async function tx(modo,fn){const d=await db();return new Promise((ok,mal)=>{const t=d.transaction(ST,modo);const s=t.objectStore(ST);const r=fn(s);t.oncomplete=()=>ok(r&&r.result);t.onerror=()=>mal(t.error);});}
const URLS=new Map();
async function guardar(id,blob){await tx('readwrite',s=>s.put(blob,id));const u=URLS.get(id);if(u){URL.revokeObjectURL(u);URLS.delete(id);}pintar(document);}
async function leer(id){try{return await tx('readonly',s=>s.get(id));}catch(e){return null;}}
async function borrar(id){try{await tx('readwrite',s=>s.delete(id));}catch(e){}const u=URLS.get(id);if(u){URL.revokeObjectURL(u);URLS.delete(id);}}
async function ids(){try{return await tx('readonly',s=>s.getAllKeys());}catch(e){return [];}}
async function url(id){if(URLS.has(id))return URLS.get(id);const b=await leer(id);if(!b)return null;const u=URL.createObjectURL(b);URLS.set(id,u);return u;}

/* llena las <img data-foto> que aparezcan en pantalla */
function pintar(root){
  (root||document).querySelectorAll('img[data-foto]').forEach(async im=>{
    const id=im.dataset.foto;if(im.dataset.puesta===id)return;im.dataset.puesta=id;
    const u=await url(id);const box=im.closest('.foto');
    if(u){im.src=u;im.hidden=false;if(box)box.classList.add('con');}else{im.removeAttribute('src');im.hidden=true;if(box)box.classList.remove('con');}
  });
}
new MutationObserver(ms=>{for(const m of ms)for(const n of m.addedNodes)if(n.nodeType===1){if(n.matches&&n.matches('img[data-foto]'))pintar(n.parentNode);else if(n.querySelector&&n.querySelector('img[data-foto]'))pintar(n);}}).observe(document.documentElement,{childList:true,subtree:true});

/* reduce una imagen a 720 px de lado mayor, JPEG */
function aJpeg(fuente,w,h){
  const k=Math.min(1,720/Math.max(w,h));const c=document.createElement('canvas');c.width=Math.round(w*k);c.height=Math.round(h*k);
  c.getContext('2d').drawImage(fuente,0,0,c.width,c.height);
  return new Promise(ok=>c.toBlob(b=>ok(b),'image/jpeg',0.74));
}
function deArchivo(){
  return new Promise(ok=>{
    const i=document.createElement('input');i.type='file';i.accept='image/*';i.setAttribute('capture','environment');
    i.onchange=()=>{const f=i.files&&i.files[0];if(!f)return ok(null);const im=new Image();const u=URL.createObjectURL(f);
      im.onload=async()=>{const b=await aJpeg(im,im.naturalWidth,im.naturalHeight);URL.revokeObjectURL(u);ok(b);};im.onerror=()=>{URL.revokeObjectURL(u);ok(null);};im.src=u;};
    i.click();
  });
}
/* cámara dentro de la app */
function tomar(titulo){
  if(!(navigator.mediaDevices&&navigator.mediaDevices.getUserMedia))return deArchivo();
  return new Promise(async ok=>{
    let stream;
    try{stream=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'},width:{ideal:1280},height:{ideal:960}},audio:false});}
    catch(e){toast('No pude abrir la cámara. Elige una foto de tu galería.');return ok(await deArchivo());}
    const w=document.createElement('dialog');w.className='cam';w.setAttribute('role','dialog');w.setAttribute('aria-label','Tomar foto');
    w.innerHTML=`<div class="cam-top"><b>${esc(titulo||'Foto del animal')}</b><button type="button" class="cam-x" aria-label="Cerrar">${ico('x',2.4)}</button></div>
      <div class="cam-v"><video playsinline autoplay muted></video><i class="cam-guia"></i></div>
      <div class="cam-bar"><button type="button" class="cam-gal">Galería</button><button type="button" class="cam-disp" aria-label="Tomar foto"><i></i></button><span></span></div>`;
    document.body.appendChild(w);try{w.showModal();}catch(e){w.setAttribute('open','');}
    w.addEventListener('cancel',e=>{e.preventDefault();fin(null);});
    const v=w.querySelector('video');v.srcObject=stream;
    const fin=b=>{stream.getTracks().forEach(t=>t.stop());w.remove();ok(b);};
    w.querySelector('.cam-x').onclick=()=>fin(null);
    w.querySelector('.cam-gal').onclick=async()=>{stream.getTracks().forEach(t=>t.stop());w.remove();ok(await deArchivo());};
    w.querySelector('.cam-disp').onclick=async()=>{if(!v.videoWidth)return;w.classList.add('flash');const b=await aJpeg(v,v.videoWidth,v.videoHeight);fin(b);};
  });
}
/* borra fotos de animales que ya no existen (por ejemplo, un lote que no se guardó) */
async function limpiar(){
  const vivos=new Set();for(const l of Object.values(S.lotes||{}))for(const a of (l.animales||[]))vivos.add(a.id);
  if(!vivos.size&&!Object.keys(S.lotes||{}).length)return;
  for(const k of await ids())if(!vivos.has(k)&&!(window.FOTOS_TEMP&&FOTOS_TEMP.has(k)))await borrar(k);
}
setTimeout(limpiar,8000);
window.Fotos={guardar,leer,borrar,url,tomar,pintar,ids,limpiar};
})();
