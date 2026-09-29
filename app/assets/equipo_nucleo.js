/* Rumentis: núcleo del equipo (lo usan la app del jefe y la del vaquero).
   - Licencias: códigos al azar de 95 bits (RV-XXXX-XXXX-XXXX-XXXX-XXXX, alfabeto de Crockford sin I, L, O ni U) con una
     letra de control al final para atrapar errores al escribirlos. Cada licencia es para una sola persona.
   - Llaves: cada teléfono tiene una llave de firma (Ed25519) y una de cifrado (X25519), de tweetnacl.
   - Sobres: todo lo que viaja entre el jefe y los vaqueros va cifrado y firmado. El alta del vaquero y la bienvenida del
     jefe van con cifrado de llave pública (nacl.box); lo demás con la clave del equipo (nacl.secretbox), que el jefe
     cambia cuando da de baja a alguien. El servidor de relevo solo guarda y entrega sobres: no puede leerlos.
   - Transporte: por el servidor de relevo (servidor/equipo) si está configurado, y siempre también por archivo
     (se comparte por WhatsApp y se abre en la otra app), para fincas sin señal.
   - Enlace de licencia: página pública (vaquero/) con la ficha del equipo firmada por el jefe; el mismo enlace va en el QR. */
(function(){
'use strict';
const CFG=window.RUMENTIS||{app:'jefe',prueba:false};
const D=()=>window.Documentos;

/* ---------- librerías ---------- */
const CARGA={};
function cargar(src){return CARGA[src]||(CARGA[src]=new Promise((ok,mal)=>{const s=document.createElement('script');s.src=src;
  s.onload=()=>ok();s.onerror=()=>{delete CARGA[src];s.remove();mal(new Error('lib'));};document.head.appendChild(s);}));}
async function nacl_(){if(!window.nacl)await cargar('lib/nacl-fast.min.js');return window.nacl;}
async function qrLib(){if(!window.qrcode)await cargar('lib/qrcode.js');if(window.qrcode&&qrcode.stringToBytesFuncs)qrcode.stringToBytes=qrcode.stringToBytesFuncs['UTF-8'];return window.qrcode;}

/* ---------- bytes y texto ---------- */
const enc=s=>new TextEncoder().encode(String(s));
const dec=u=>new TextDecoder().decode(u);
function b64(u){u=new Uint8Array(u);let s='';for(let i=0;i<u.length;i+=0x8000)s+=String.fromCharCode.apply(null,u.subarray(i,i+0x8000));return btoa(s);}
const deB64=t=>{const s=atob(t);const u=new Uint8Array(s.length);for(let i=0;i<s.length;i++)u[i]=s.charCodeAt(i);return u;};
const b64u=u=>b64(u).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
const deB64u=t=>deB64(String(t).replace(/-/g,'+').replace(/_/g,'/')+'==='.slice((String(t).length+3)%4));
const azar=n=>{const b=new Uint8Array(n);crypto.getRandomValues(b);return b;};
const canon=v=>D().canon(v);
const sha=s=>D().sha256(s);
async function comprimir(u){
  if(typeof CompressionStream==='undefined')return null;
  try{const cs=new CompressionStream('deflate-raw');const w=cs.writable.getWriter();w.write(u);w.close();return new Uint8Array(await new Response(cs.readable).arrayBuffer());}catch(e){return null;}
}
async function descomprimir(u){
  const ds=new DecompressionStream('deflate-raw');const w=ds.writable.getWriter();w.write(u);w.close();return new Uint8Array(await new Response(ds.readable).arrayBuffer());
}

/* ---------- licencias ---------- */
const ALF='0123456789ABCDEFGHJKMNPQRSTVWXYZ';
const control=s=>ALF[parseInt(sha('RV|'+s).slice(0,4),16)%32];
function nuevaLicencia(){
  const b=azar(19);let s='';for(const x of b)s+=ALF[x&31];   // 19 x 5 bits = 95 bits al azar
  return s+control(s);
}
// lo que escriba la persona: minúsculas, espacios, guiones, O por 0, I y L por 1 (como Crockford)
function normLic(t){
  let s=String(t||'').toUpperCase().replace(/[OÓ]/g,'0').replace(/[IÍL]/g,'1').replace(/[^0-9A-Z]/g,'');
  if(s.length===22&&s.startsWith('RV'))s=s.slice(2);
  return s;
}
const licValida=s=>/^[0-9A-HJKMNP-TV-Z]{20}$/.test(s)&&control(s.slice(0,19))===s[19];
const fmtLic=s=>'RV-'+s.match(/.{1,4}/g).join('-');
const hashLic=s=>sha('RUMENTIS-LIC-1|'+s);
// licencia de prueba para siempre: solo la aceptan las apps de prueba (entra a un equipo de muestra sin jefe)
const LIC_PRUEBA='RV-PRUEBA-2026';
const esLicPrueba=t=>CFG.prueba&&String(t||'').toUpperCase().replace(/[^0-9A-Z]/g,'')==='RVPRUEBA2026';

/* ---------- llaves ---------- */
async function nuevasLlaves(){const n=await nacl_();const f=n.sign.keyPair(),c=n.box.keyPair();
  return {firma:{pub:b64(f.publicKey),sec:b64(f.secretKey)},caja:{pub:b64(c.publicKey),sec:b64(c.secretKey)}};}
const idDe=firmaPub=>'v'+sha('RUMENTIS-VAQ|'+firmaPub).slice(0,12);
async function firmar(obj,sec){const n=await nacl_();return b64u(n.sign.detached(enc(canon(obj)),deB64(sec)));}
async function verificar(obj,firma,pub){
  try{const n=await nacl_();const o={...obj};delete o.f;return n.sign.detached.verify(enc(canon(o)),deB64u(firma),deB64(pub));}catch(e){return false;}
}
const huella=pub=>{const h=sha('RUMENTIS-HUELLA|'+pub).toUpperCase();return `${h.slice(0,4)} ${h.slice(4,8)} ${h.slice(8,12)}`;};

/* ---------- sobres ----------
   {v:1, t: tipo, e: equipo, de, para, ts, id, z (comprimido), x: 'box'|'sb', g (generación de la clave), n, c, f}
   caja: {pub: la del que recibe, sec: la mía}  o  clave: la del equipo (base64) */
// extra: campos que van a la vista (p. ej. las llaves públicas del vaquero en su alta)
async function sellar(t,{e,de,para,carga,caja,clave,g,firmaSec,extra}){
  const n=await nacl_();let datos=enc(JSON.stringify(carga));let z=0;
  if(datos.length>700){const c=await comprimir(datos);if(c&&c.length<datos.length){datos=c;z=1;}}
  const nonce=n.randomBytes(24);let c;
  if(caja)c=n.box(datos,nonce,deB64(caja.pub),deB64(caja.sec));else c=n.secretbox(datos,nonce,deB64(clave));
  const s={...(extra||{}),v:1,t,e,de,para,ts:Date.now(),id:b64u(n.randomBytes(9)),z,x:caja?'box':'sb',g:g||0,n:b64u(nonce),c:b64u(c)};
  s.f=await firmar(s,firmaSec);return s;
}
// alta sin llaves del jefe (el vaquero solo tiene el código de su licencia y no hay servidor): va firmada pero sin
// cifrar, dentro del archivo que el vaquero le manda al jefe por WhatsApp
async function sobrePlano(t,{de,para,carga,firmaSec,extra}){
  const n=await nacl_();const s={...(extra||{}),v:1,t,e:null,de,para,ts:Date.now(),id:b64u(n.randomBytes(9)),x:'plano',c:JSON.stringify(carga)};
  s.f=await firmar(s,firmaSec);return s;
}
// abre un sobre; firmaPub: la llave de firma de quien lo manda (si se sabe). Devuelve la carga o null.
async function abrir(s,{caja,clave,firmaPub}={}){
  try{
    if(!s||s.v!==1)return null;
    if(firmaPub&&!(await verificar(s,s.f,firmaPub)))return null;
    const n=await nacl_();let d;
    if(s.x==='box'){if(!caja)return null;d=n.box.open(deB64u(s.c),deB64u(s.n),deB64(caja.pub),deB64(caja.sec));}
    else{if(!clave)return null;d=n.secretbox.open(deB64u(s.c),deB64u(s.n),deB64(clave));}
    if(!d)return null;if(s.z)d=await descomprimir(d);
    return JSON.parse(dec(d));
  }catch(e){return null;}
}

/* ---------- ficha del equipo y enlace de la licencia ----------
   ficha: {v, e (equipo), j (firma del jefe), k (caja del jefe), fn (finca), s (servidor), p (prueba), f (firma)} */
async function ficha({e,firma,caja,finca,servidor,prueba}){
  const o={v:1,e,j:firma.pub,k:caja.pub,fn:String(finca||'').slice(0,60),s:servidor||'',p:prueba?1:0};o.f=await firmar(o,firma.sec);return o;
}
async function fichaValida(o){if(!(o&&o.v===1&&o.e&&o.j&&o.k))return false;return o.f?await verificar(o,o.f,o.j):true;}
// el enlace no lleva la firma de la ficha: lo entrega el jefe en persona y así el QR es más fácil de leer
function enlace(fi,lic){
  const o={c:lic,e:fi.e,j:fi.j,k:fi.k,fn:fi.fn,s:fi.s,p:fi.p};
  return (CFG.paginaVaquero||'')+'#'+b64u(enc(JSON.stringify(o)));
}
// de un enlace, un texto de WhatsApp o un QR: la ficha y la licencia (o null)
function leerEnlace(t){
  const m=/#([A-Za-z0-9_-]{60,})/.exec(String(t||''))||/^([A-Za-z0-9_-]{60,})$/.exec(String(t||'').trim());if(!m)return null;
  try{const o=JSON.parse(dec(deB64u(m[1])));if(!o||!o.c||!o.e)return null;const lic=normLic(o.c);
    const ficha={v:1,e:o.e,j:o.j,k:o.k,fn:o.fn,s:o.s,p:o.p};if(o.f)ficha.f=o.f;return {lic,ficha};}catch(e){return null;}
}

/* ---------- servidor de relevo ---------- */
const urlOk=u=>{u=String(u||'').trim().replace(/\/+$/,'');return /^https:\/\/[^\s/]+|^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?/.test(u)?u:'';};
async function pedir(base,ruta,cuerpo,firmaSec){
  base=urlOk(base);if(!base)throw new Error('sin servidor');
  const b={...cuerpo,ts:Date.now()};if(firmaSec)b.f=await firmar(b,firmaSec);
  const c=typeof AbortController!=='undefined'?new AbortController():null,t=setTimeout(()=>c&&c.abort(),15000);
  try{
    // text/plain: sin pedido previo de permiso (CORS) desde la app
    const r=await fetch(base+ruta,{method:'POST',headers:{'Content-Type':'text/plain'},body:JSON.stringify(b),cache:'no-store',signal:c?c.signal:undefined});
    const j=await r.json().catch(()=>({}));if(!r.ok||!j.ok){const e=new Error(j.error||('http '+r.status));e.status=r.status;throw e;}return j;
  }finally{clearTimeout(t);}
}

/* ---------- archivo para WhatsApp ---------- */
// extra: p. ej. la ficha del equipo en el archivo del jefe (así el vaquero que se activó sin enlace conoce sus llaves)
function archivoTexto(sobres,de,extra){return JSON.stringify({rumentis:'equipo',v:1,de,ts:Date.now(),...(extra||{}),sobres});}
function leerArchivo(txt){try{const o=JSON.parse(txt);if(o&&o.rumentis==='equipo'&&Array.isArray(o.sobres))return o;}catch(e){}return null;}
function compartirArchivo(nombre,txt,titulo){
  const A=window.Android;
  if(A&&A.compartirArchivo){try{if(A.compartirArchivo(nombre,b64(enc(txt)),'application/json',titulo||nombre))return true;}catch(e){}}
  try{const f=new File([txt],nombre,{type:'application/json'});if(navigator.canShare&&navigator.canShare({files:[f]})){navigator.share({files:[f],title:titulo||nombre}).catch(()=>{});return true;}}catch(e){}
  const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([txt],{type:'application/json'}));a.download=nombre;document.body.appendChild(a);a.click();
  setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove();},4000);return true;
}
function elegirArchivo(){return new Promise(ok=>{const i=document.createElement('input');i.type='file';i.accept='.json,.rumentis,application/json,text/plain,*/*';
  i.onchange=()=>{const f=i.files&&i.files[0];if(!f){ok(null);return;}const rd=new FileReader();rd.onload=()=>ok(String(rd.result||''));rd.onerror=()=>ok(null);rd.readAsText(f);};i.click();});}
function compartirTexto(t){
  if(window.Android&&Android.compartir){try{Android.compartir(t);return;}catch(e){}}
  if(navigator.share){navigator.share({text:t}).catch(()=>{});return;}
  try{navigator.clipboard.writeText(t);toast('Copiado');}catch(e){}
}

/* ---------- QR ---------- */
async function qrSvg(texto,{color='#1A2B3A'}={}){
  const Q=await qrLib();const q=Q(0,'M');q.addData(texto,'Byte');q.make();const n=q.getModuleCount(),m=2;
  let p='';for(let y=0;y<n;y++)for(let x=0;x<n;x++)if(q.isDark(y,x))p+=`M${x+m} ${y+m}h1v1h-1z`;
  return `<svg class="qr" viewBox="0 0 ${n+2*m} ${n+2*m}" shape-rendering="crispEdges" role="img" aria-label="Código QR"><rect width="100%" height="100%" fill="#fff"/><path d="${p}" fill="${color}"/></svg>`;
}
/* lector de QR con la cámara: BarcodeDetector si el teléfono lo tiene; si no, jsQR (Apache 2.0, lib/jsQR.js) */
let lector=null;
function cerrarLector(){if(!lector)return;try{lector.stream.getTracks().forEach(t=>t.stop());}catch(e){}cancelAnimationFrame(lector.raf);const w=lector.w;lector=null;if(w)w.remove();}
async function escanearQR(){
  cerrarLector();
  return new Promise(async ok=>{
    const w=document.createElement('div');w.className='eq-lector';w.innerHTML=`<video playsinline muted></video><div class="eq-marco"><i></i></div><p>Apunta al código QR que te dio tu jefe</p><button type="button" class="btn">Cancelar</button>`;
    document.body.appendChild(w);const v=w.querySelector('video');let fin=false;const listo=t=>{if(fin)return;fin=true;cerrarLector();ok(t);};
    w.querySelector('button').onclick=()=>listo(null);
    let stream;try{stream=await navigator.mediaDevices.getUserMedia({video:{facingMode:'environment',width:{ideal:1280},height:{ideal:720}},audio:false});}
    catch(e){w.remove();toast('No se pudo abrir la cámara. Pega el enlace o escribe la licencia.',4000);ok(null);return;}
    lector={w,stream,raf:0};v.srcObject=stream;try{await v.play();}catch(e){}
    let det=null;try{if('BarcodeDetector' in window){const f=await BarcodeDetector.getSupportedFormats();if(f.includes('qr_code'))det=new BarcodeDetector({formats:['qr_code']});}}catch(e){det=null;}
    if(!det){try{if(!window.jsQR)await cargar('lib/jsQR.js');}catch(e){}}
    const cv=document.createElement('canvas'),cx=cv.getContext('2d',{willReadFrequently:true});let ocupado=false,ult=0;
    const paso=async t=>{if(!lector||fin)return;lector.raf=requestAnimationFrame(paso);if(ocupado||t-ult<160||v.readyState<2)return;ult=t;ocupado=true;
      try{
        if(det){const r=await det.detect(v);if(r&&r[0]&&r[0].rawValue)listo(r[0].rawValue);}
        else if(window.jsQR){const W=Math.min(720,v.videoWidth),H=Math.round(v.videoHeight*W/v.videoWidth);cv.width=W;cv.height=H;cx.drawImage(v,0,0,W,H);
          const d=cx.getImageData(0,0,W,H);const r=jsQR(d.data,W,H,{inversionAttempts:'dontInvert'});if(r&&r.data)listo(r.data);}
      }catch(e){}ocupado=false;};
    lector.raf=requestAnimationFrame(paso);
  });
}

/* ---------- Google Play (puente nativo Pagos, solo en la app de Google Play) ---------- */
const PAGOS={escuchas:new Set()};
window.pagosEvento=j=>{let o=j;try{if(typeof j==='string')o=JSON.parse(j);}catch(e){return;}for(const f of [...PAGOS.escuchas])try{f(o);}catch(e){}};
const pagosHay=()=>!!(window.Pagos&&typeof Pagos.comprar==='function');
function pagosEscuchar(f){PAGOS.escuchas.add(f);return ()=>PAGOS.escuchas.delete(f);}
// comprueba una compra con la llave pública de Google Play (RSA). null si no se puede comprobar aquí.
function compraReal(json,firma){
  const k=CFG.playLlave;if(!k||!json||!firma)return null;
  try{if(window.Cripto&&Cripto.verificar)return !!Cripto.verificar(k,json,firma);}catch(e){}
  return null;
}

/* ---------- fechas para la gente ---------- */
function haceCuanto(ts){if(!ts)return 'nunca';const m=Math.round((Date.now()-ts)/60000);
  if(m<1)return 'hace un momento';if(m<60)return `hace ${pl(m,'minuto','minutos')}`;const h=Math.round(m/60);if(h<24)return `hace ${pl(h,'hora','horas')}`;
  const d=Math.round(h/24);return d===1?'ayer':`hace ${pl(d,'día','días')}`;}
const iniciales=n=>String(n||'?').trim().split(/\s+/).slice(0,2).map(p=>p[0]||'').join('').toUpperCase()||'?';

window.EquipoNucleo={CFG,nacl:nacl_,cargar,enc,dec,b64,deB64,b64u,deB64u,azar,sha,canon,
  nuevaLicencia,normLic,licValida,fmtLic,hashLic,LIC_PRUEBA,esLicPrueba,
  nuevasLlaves,idDe,firmar,verificar,huella,sellar,sobrePlano,abrir,ficha,fichaValida,enlace,leerEnlace,
  urlOk,pedir,archivoTexto,leerArchivo,compartirArchivo,elegirArchivo,compartirTexto,qrSvg,escanearQR,cerrarLector,
  pagosHay,pagosEscuchar,compraReal,haceCuanto,iniciales};
})();
