/* Fotos de los animales: se toman con la cámara dentro de la app y se guardan en el teléfono.
   En la app Android van como archivos JPEG en la memoria interna (Android.fotoGuardar/fotoLeer);
   en un navegador, en IndexedDB. Nunca salen del teléfono.
   Uso en la página: <img data-foto="ID"> se llena solo; Fotos.tomar() devuelve un Blob JPEG o null. */
(function(){
'use strict';
const NATIVO=!!(window.Android&&Android.fotoGuardar&&Android.fotoLeer);
const DBN='rumentis-fotos',ST='fotos';
let dbp=null;
function db(){
  if(!dbp)dbp=new Promise((ok,mal)=>{try{const r=indexedDB.open(DBN,1);r.onupgradeneeded=()=>r.result.createObjectStore(ST);r.onsuccess=()=>ok(r.result);r.onerror=()=>mal(r.error);}catch(e){mal(e);}});
  return dbp;
}
async function tx(modo,fn){const d=await db();return new Promise((ok,mal)=>{const t=d.transaction(ST,modo);const s=t.objectStore(ST);const r=fn(s);t.oncomplete=()=>ok(r&&r.result);t.onerror=()=>mal(t.error);});}
const aB64=b=>new Promise((ok,mal)=>{const r=new FileReader();r.onload=()=>ok(String(r.result).split(',')[1]||'');r.onerror=()=>mal(r.error);r.readAsDataURL(b);});
const URLS=new Map();
function olvidar(id){const u=URLS.get(id);if(u&&u.startsWith('blob:'))URL.revokeObjectURL(u);URLS.delete(id);
  document.querySelectorAll('img[data-foto]').forEach(im=>{if(im.dataset.foto===id)im.dataset.puesta='';});}
async function guardar(id,blob){
  if(NATIVO){const ok=Android.fotoGuardar(id,await aB64(blob));if(ok===false)throw new Error('No se pudo guardar la foto');}
  else await tx('readwrite',s=>s.put(blob,id));
  olvidar(id);pintar(document);
}
async function borrar(id){try{if(NATIVO)Android.fotoBorrar(id);else await tx('readwrite',s=>s.delete(id));}catch(e){}olvidar(id);pintar(document);}
async function ids(){try{if(NATIVO)return String(Android.fotoLista()||'').split(',').filter(Boolean).map(f=>f.replace(/\.jpg$/,''));return await tx('readonly',s=>s.getAllKeys());}catch(e){return [];}}
async function url(id){
  if(URLS.has(id))return URLS.get(id);let u=null;
  try{if(NATIVO){const b=Android.fotoLeer(id);if(b)u='data:image/jpeg;base64,'+b;}else{const b=await tx('readonly',s=>s.get(id));if(b)u=URL.createObjectURL(b);}}catch(e){u=null;}
  if(u)URLS.set(id,u);return u;
}
async function tiene(id){return !!(await url(id));}

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
  const k=Math.min(1,1024/Math.max(w,h));const c=document.createElement('canvas');c.width=Math.round(w*k);c.height=Math.round(h*k);
  c.getContext('2d').drawImage(fuente,0,0,c.width,c.height);
  return new Promise(ok=>c.toBlob(b=>ok(b),'image/jpeg',0.8));
}
function deArchivo(){
  return new Promise(ok=>{
    const i=document.createElement('input');i.type='file';i.accept='image/*';
    let hecho=false;const fin=b=>{if(hecho)return;hecho=true;ok(b);};
    i.onchange=()=>{const f=i.files&&i.files[0];if(!f)return fin(null);const im=new Image();const u=URL.createObjectURL(f);
      im.onload=async()=>{const b=await aJpeg(im,im.naturalWidth,im.naturalHeight);URL.revokeObjectURL(u);fin(b);};im.onerror=()=>{URL.revokeObjectURL(u);toast('No pude abrir esa imagen.');fin(null);};im.src=u;};
    addEventListener('focus',()=>setTimeout(()=>{if(!i.files||!i.files.length)fin(null);},1500),{once:true});
    i.click();
  });
}
/* elige la cámara trasera principal (lente 1x), no la gran angular ni la de acercamiento.
   En Android las etiquetas son como "camera2 0, facing back": la principal suele ser el número más bajo. */
const CAM_KEY='rumentis-cam';
async function traseras(){
  let ds=[];try{ds=(await navigator.mediaDevices.enumerateDevices()).filter(d=>d.kind==='videoinput');}catch(e){}
  const back=ds.filter(d=>/back|rear|trasera|environment|posterior/i.test(d.label));
  const lista=(back.length?back:ds.filter(d=>!/front|frontal|user/i.test(d.label)));
  const n=d=>{const m=String(d.label).match(/(\d+)/);return m?+m[1]:99;};
  return lista.sort((a,b)=>n(a)-n(b));
}
async function abrirCam(devId){
  const base={width:{ideal:1920},height:{ideal:1440}};
  const v=devId?{...base,deviceId:{exact:devId}}:{...base,facingMode:{ideal:'environment'}};
  return navigator.mediaDevices.getUserMedia({video:v,audio:false});
}
async function zoom1(stream){
  try{const t=stream.getVideoTracks()[0];const c=t.getCapabilities?t.getCapabilities():{};
    if(c.zoom){const z=Math.min(Math.max(1,c.zoom.min||1),c.zoom.max||1);await t.applyConstraints({advanced:[{zoom:z}]});}}catch(e){}
}
function tomar(titulo){
  return new Promise(async ok=>{
    const w=document.createElement('dialog');w.className='cam';w.setAttribute('aria-label','Tomar foto');
    w.innerHTML=`<div class="cam-top"><b>${esc(titulo||'Foto del animal')}</b><button type="button" class="cam-x" aria-label="Cerrar">${ico('x',2.4)}</button></div>
      <div class="cam-v"><video playsinline autoplay muted></video><i class="cam-guia"></i><p class="cam-msg">Abriendo la cámara…</p></div>
      <div class="cam-bar"><button type="button" class="cam-gal">Galería</button><button type="button" class="cam-disp" aria-label="Tomar foto" disabled><i></i></button><button type="button" class="cam-lente" hidden>Cambiar lente</button></div>`;
    document.body.appendChild(w);try{w.showModal();}catch(e){w.setAttribute('open','');}
    const v=w.querySelector('video'),msg=w.querySelector('.cam-msg'),disp=w.querySelector('.cam-disp'),lente=w.querySelector('.cam-lente');
    let stream=null,hecho=false,lista=[],idx=0;
    const parar=()=>{if(stream)stream.getTracks().forEach(t=>t.stop());stream=null;};
    const fin=b=>{if(hecho)return;hecho=true;parar();try{w.close();}catch(e){}w.remove();ok(b);};
    w.addEventListener('cancel',e=>{e.preventDefault();fin(null);});
    w.querySelector('.cam-x').onclick=()=>fin(null);
    w.querySelector('.cam-gal').onclick=async()=>{parar();w.classList.add('espera');const b=await deArchivo();fin(b);};
    disp.onclick=async()=>{
      if(!v.videoWidth){try{await v.play();}catch(e){}await new Promise(r=>setTimeout(r,300));}
      if(!v.videoWidth){toast('La cámara aún no está lista.');return;}
      w.classList.add('flash');const b=await aJpeg(v,v.videoWidth,v.videoHeight);fin(b);
    };
    const mostrar=async s=>{stream=s;v.srcObject=s;await zoom1(s);try{await v.play();}catch(e){}msg.hidden=true;disp.disabled=false;
      const lab=(s.getVideoTracks()[0]||{}).label||'';lente.hidden=lista.length<2;lente.textContent=lista.length>1?`Lente ${idx+1} de ${lista.length}`:'';};
    lente.onclick=async()=>{if(lista.length<2)return;idx=(idx+1)%lista.length;parar();disp.disabled=true;
      try{await mostrar(await abrirCam(lista[idx].deviceId));try{localStorage.setItem(CAM_KEY,lista[idx].deviceId);}catch(e){}}catch(e){toast('No pude abrir ese lente.');}};
    try{
      if(!(navigator.mediaDevices&&navigator.mediaDevices.getUserMedia))throw new Error('sin cámara');
      // 1) permiso con cualquier cámara trasera; 2) con las etiquetas ya visibles, abrir la principal
      let s=await abrirCam(null);
      if(hecho){s.getTracks().forEach(t=>t.stop());return;}
      lista=await traseras();
      let guard=null;try{guard=localStorage.getItem(CAM_KEY);}catch(e){}
      idx=Math.max(0,lista.findIndex(d=>d.deviceId===guard));
      const actual=(s.getVideoTracks()[0].getSettings()||{}).deviceId;
      if(lista[idx]&&lista[idx].deviceId&&lista[idx].deviceId!==actual){s.getTracks().forEach(t=>t.stop());s=await abrirCam(lista[idx].deviceId);}
      else if(actual){const k=lista.findIndex(d=>d.deviceId===actual);if(k>=0)idx=k;}
      if(hecho){s.getTracks().forEach(t=>t.stop());return;}
      await mostrar(s);
    }catch(e){
      msg.textContent='No pude abrir la cámara. Revisa que Rumentis tenga permiso de cámara en los ajustes del teléfono, o elige una foto de tu galería.';
      w.classList.add('sin');
    }
  });
}
/* borra fotos de animales que ya no existen (por ejemplo, un lote que no se guardó) */
async function limpiar(){
  const vivos=new Set();for(const l of Object.values(S.lotes||{}))for(const a of (l.animales||[]))vivos.add(a.id);
  if(!vivos.size)return;
  for(const k of await ids())if(!vivos.has(k)&&!(window.FOTOS_TEMP&&FOTOS_TEMP.has(k)))await borrar(k);
}
setTimeout(limpiar,15000);
window.Fotos={guardar,borrar,url,tiene,tomar,pintar,ids,limpiar,NATIVO};
})();
