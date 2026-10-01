/* Rumentis Beta: teléfonos enlazados para el peso con cámara.
   Hasta tres teléfonos toman al mismo animal en el mismo instante: el de la administración (de costado, con la persona
   de referencia) y uno o dos del personal (por detrás, del otro costado o desde arriba). El de la administración manda
   la señal de disparo; cada teléfono toma sus fotos en ese momento y se las manda; el de la administración las mide
   todas con el modelo preciso y calcula el peso.
   Conexión: directa entre teléfonos (WebRTC, canal de datos cifrado con DTLS), por el mismo Wi-Fi o por el punto de
   acceso (anclaje de red) de uno de los teléfonos; no hace falta internet. Para enlazarse, los teléfonos se pasan una
   vez los datos de conexión:
   - con el código QR: el teléfono del personal lee el código de la administración y muestra el suyo, que lee la
     administración (sin internet ni servidor);
   - con el equipo: si hay servidor del equipo (en internet o en una computadora de la finca), la invitación y la
     respuesta van en sobres cifrados del equipo y no hay que leer códigos.
   Mensajes (JSON; las fotos en trozos binarios): hola, estado (lo que ve cada cámara), disparo, foto + trozos + fin,
   nofoto, resultado, adios. */
(function(){
'use strict';
const CFG=window.RUMENTIS||{};
if(!CFG.beta||typeof RTCPeerConnection==='undefined')return;
const N=()=>window.EquipoNucleo;
const ROLES={
  atras:{nombre:'Por detrás',ref:true,instr:'El animal de espaldas a la cámara, con la persona de referencia a su lado.',girar:'Que el animal quede de espaldas a la cámara',ang:{max:.9},dist:{eje:'alto',min:.45,max:.85},banda:[.05,.55]},
  otro:{nombre:'Otro costado',ref:false,instr:'El animal de costado, del lado contrario al de la administración.',girar:'Que el animal quede de costado',ang:{min:1.15},dist:{eje:'ancho',min:.5,max:.88}},
  arriba:{nombre:'Desde arriba',ref:false,instr:'El teléfono arriba del animal (desde la pasarela o la cerca de la manga), mirando hacia abajo.',girar:'Que el animal se vea completo desde arriba',ang:{},dist:{eje:'max',min:.45,max:.9}}};
const ICE=()=>({iceServers:navigator.onLine?[{urls:'stun:stun.cloudflare.com:3478'}]:[],iceCandidatePoolSize:0});
const TROZO=16000,PREFIJO='RCAM1.';
const ahora=()=>Date.now();

/* ---------- texto del enlace (para el QR o el sobre) ---------- */
// del SDP se quitan los candidatos TCP y los IPv6 (el QR queda más chico; en el Wi-Fi o el anclaje basta IPv4)
function sdpMin(s){
  const L=String(s).split(/\r?\n/).filter(l=>l&&!/^a=(extmap-allow-mixed|msid-semantic)/.test(l));
  const cand=L.filter(l=>l.startsWith('a=candidate:'));
  const util=c=>!/ tcp /i.test(c)&&!/ [0-9a-f]*:[0-9a-f:]+ \d+ typ/i.test(c);
  const quedan=cand.filter(util);const fuera=new Set(quedan.length?cand.filter(c=>!util(c)):[]);
  return L.filter(l=>!fuera.has(l)).join('\r\n')+'\r\n';
}
async function zip(u){if(typeof CompressionStream==='undefined')return null;const cs=new CompressionStream('deflate-raw');const w=cs.writable.getWriter();w.write(u);w.close();return new Uint8Array(await new Response(cs.readable).arrayBuffer());}
async function unzip(u){const ds=new DecompressionStream('deflate-raw');const w=ds.writable.getWriter();w.write(u);w.close();return new Uint8Array(await new Response(ds.readable).arrayBuffer());}
async function empacar(o){const n=N(),j=n.enc(JSON.stringify(o)),z=await zip(j);return PREFIJO+(z?'z':'j')+n.b64u(z||j);}
async function desempacar(t){
  const m=/RCAM1\.([zj])([A-Za-z0-9_-]+)/.exec(String(t||''));if(!m)return null;
  try{const n=N();let u=n.deB64u(m[2]);if(m[1]==='z')u=await unzip(u);const o=JSON.parse(n.dec(u));return o&&o.v===1&&o.d?o:null;}catch(e){return null;}
}
// espera a que el teléfono junte sus direcciones (como mucho `ms`: sin internet el servidor STUN no contesta)
function juntarICE(pc,ms=2500){return new Promise(ok=>{if(pc.iceGatheringState==='complete')return ok();const t=setTimeout(ok,ms);
  pc.addEventListener('icegatheringstatechange',()=>{if(pc.iceGatheringState==='complete'){clearTimeout(t);ok();}});});}
/* con la cámara abierta el teléfono da sus direcciones reales en la red local (sin ella, nombres .local que algunos
   puntos de acceso no resuelven) */
async function conCamara(fn){let s=null;try{s=await navigator.mediaDevices.getUserMedia({video:{width:{ideal:320}},audio:false});}catch(e){}
  try{return await fn();}finally{try{s&&s.getTracks().forEach(t=>t.stop());}catch(e){}}}
const nombreYo=()=>{try{const v=window.Vaquero&&Vaquero.VQ?Vaquero.VQ():null;if(v&&v.nombre)return String(v.nombre).slice(0,40);}catch(e){}
  return CFG.app==='vaquero'?'Personal':String((S.config&&S.config.finca)||'Administración').slice(0,40);};

/* ---------- un par: la conexión con otro teléfono ---------- */
const escuchas=new Set();const avisar=()=>{for(const f of [...escuchas])try{f();}catch(e){}};
function Par(pc,rol,lado){
  const p={pc,rol,lado,dc:null,estado:'enlazando',nombre:'',vista:null,entrantes:{},alMsg:null,t0:ahora()};
  const caer=()=>{if(p.estado==='cerrado')return;p.estado='caido';avisar();};
  pc.addEventListener('connectionstatechange',()=>{if(['failed','disconnected','closed'].includes(pc.connectionState))caer();});
  p.usar=dc=>{p.dc=dc;dc.binaryType='arraybuffer';
    dc.onopen=()=>{p.estado='conectado';p.enviar({t:'hola',n:nombreYo(),rol,app:CFG.app});avisar();};
    dc.onclose=caer;
    let foto=null;
    dc.onmessage=ev=>{
      if(typeof ev.data!=='string'){if(foto){foto.partes.push(ev.data);foto.llegaron+=ev.data.byteLength;}return;}
      let m;try{m=JSON.parse(ev.data);}catch(e){return;}
      if(m.t==='foto'){foto={...m,partes:[],llegaron:0};return;}
      if(m.t==='fin'&&foto){const f=foto;foto=null;if(f.llegaron===f.b)recibirFoto(p,f);return;}
      if(m.t==='hola'){p.nombre=String(m.n||'').slice(0,40);avisar();}
      else if(m.t==='estado'){p.vista={c:m.c,m:m.m,p:m.p,t:ahora()};avisar();}
      else if(m.t==='adios'){p.estado='cerrado';try{pc.close();}catch(e){}avisar();}
      if(p.alMsg)try{p.alMsg(m);}catch(e){}
    };
    if(dc.readyState==='open')dc.onopen();};
  p.enviar=o=>{try{if(p.dc&&p.dc.readyState==='open'){p.dc.send(JSON.stringify(o));return true;}}catch(e){}return false;};
  // una foto en trozos (con espera si el canal está lleno)
  p.enviarFoto=async(id,i,n,blob,meta)=>{const dc=p.dc;if(!dc||dc.readyState!=='open')return false;
    const u=new Uint8Array(await blob.arrayBuffer());p.enviar({t:'foto',id,i,n,b:u.length,...meta});
    for(let k=0;k<u.length;k+=TROZO){
      while(dc.bufferedAmount>1<<20){await new Promise(z=>setTimeout(z,20));if(dc.readyState!=='open')return false;}
      dc.send(u.slice(k,k+TROZO));}
    p.enviar({t:'fin',id,i});return true;};
  p.cerrar=()=>{p.enviar({t:'adios'});p.estado='cerrado';setTimeout(()=>{try{pc.close();}catch(e){}},300);avisar();};
  return p;
}

/* ---------- administración: la sesión con sus pares ---------- */
const SES={id:null,pares:{},esperas:{}};
const nuevoId=()=>N().b64u(N().azar(6));
function sesion(){if(!SES.id)SES.id=nuevoId();return SES;}
function recibirFoto(p,f){const w=SES.esperas[f.id];if(!w)return;
  const blob=new Blob(f.partes,{type:'image/jpeg'});(w.fotos[p.rol]||(w.fotos[p.rol]=[]))[f.i]={blob,n:f.n,w:f.w,h:f.h};w.revisar();}
// la oferta para un rol: devuelve el texto del QR (o del sobre)
async function ofertar(rol){
  sesion();const viejo=SES.pares[rol];if(viejo)viejo.cerrar();
  return conCamara(async()=>{
    const pc=new RTCPeerConnection(ICE()),p=Par(pc,rol,'admin');p.usar(pc.createDataChannel('rumentis',{ordered:true}));
    SES.pares[rol]=p;avisar();
    await pc.setLocalDescription(await pc.createOffer());await juntarICE(pc);
    p.oferta=await empacar({v:1,k:'o',s:SES.id,r:rol,n:nombreYo(),d:sdpMin(pc.localDescription.sdp)});return p.oferta;});
}
// la respuesta del otro teléfono (texto del QR o del sobre)
async function responder(t){
  const o=await desempacar(t);if(!o||o.k!=='a')return {ok:false,msg:'Ese código no es la respuesta de una cámara enlazada.'};
  if(o.s!==SES.id)return {ok:false,msg:'Ese código es de otro enlace. Vuelve a empezar en el otro teléfono.'};
  const p=SES.pares[o.r];if(!p||p.estado==='cerrado')return {ok:false,msg:'Ese enlace ya no está abierto.'};
  if(p.pc.signalingState!=='have-local-offer')return {ok:p.estado==='conectado',msg:''};
  try{await p.pc.setRemoteDescription({type:'answer',sdp:o.d});p.nombre=String(o.n||'').slice(0,40);}catch(e){return {ok:false,msg:'No se pudo enlazar con ese código.'};}
  return {ok:true,rol:o.r};
}
const pares=()=>Object.values(SES.pares).filter(p=>p.estado!=='cerrado');
const conectados=()=>pares().filter(p=>p.estado==='conectado');
/* ¿están listas las cámaras enlazadas? (su silueta en verde hace menos de 1.5 s) */
const listas=()=>conectados().every(p=>p.vista&&p.vista.c==='verde'&&ahora()-p.vista.t<1500);
const esperando=()=>conectados().filter(p=>!(p.vista&&p.vista.c==='verde'&&ahora()-p.vista.t<1500)).map(p=>ROLES[p.rol].nombre);
/* el disparo: todas las cámaras enlazadas toman sus fotos ya. Devuelve una promesa con las fotos de cada rol
   ({rol: [{blob, w, h}]}), cuando llegan todas o al pasar `ms` */
function disparar(ms=12000){
  const id=nuevoId(),c=conectados();const w={fotos:{},esperan:new Set(c.map(p=>p.rol)),nada:new Set()};
  const pr=new Promise(ok=>{w.fin=()=>{clearTimeout(w.t);delete SES.esperas[id];ok({fotos:w.fotos,faltan:[...w.esperan].filter(r=>!(w.fotos[r]&&w.fotos[r].filter(Boolean).length)),nada:[...w.nada]});};
    w.t=setTimeout(w.fin,ms);
    w.revisar=()=>{for(const r of [...w.esperan]){const f=w.fotos[r];if(w.nada.has(r)||(f&&f.length&&f.filter(Boolean).length>=f.find(Boolean).n))w.esperan.delete(r);}if(!w.esperan.size)w.fin();};});
  SES.esperas[id]=w;
  for(const p of c){p.alMsg=m=>{if(m.t==='nofoto'&&m.id===id){w.nada.add(p.rol);w.revisar();}};if(!p.enviar({t:'disparo',id}))w.esperan.delete(p.rol);}
  if(!w.esperan.size)setTimeout(()=>w.fin(),0);
  return {id,fotos:pr};
}
function avisarResultado(txt){for(const p of conectados())p.enviar({t:'resultado',m:String(txt||'').slice(0,80)});}
function soltar(rol){const p=SES.pares[rol];if(p){p.cerrar();delete SES.pares[rol];avisar();}}
function soltarTodo(){for(const r of Object.keys(SES.pares))soltar(r);SES.id=null;}

/* ---------- personal: unirse a una sesión ---------- */
const INV={p:null};
async function unirse(t){
  const o=await desempacar(t);if(!o||o.k!=='o')return {ok:false,msg:'Ese código no es de una cámara enlazada.'};
  if(!ROLES[o.r])return {ok:false,msg:'Ese código es de una versión más nueva de Rumentis.'};
  if(INV.p)INV.p.cerrar();
  return conCamara(async()=>{
    const pc=new RTCPeerConnection(ICE()),p=Par(pc,o.r,'personal');p.admin=String(o.n||'').slice(0,40);p.sesion=o.s;INV.p=p;
    pc.addEventListener('datachannel',e=>p.usar(e.channel));
    try{await pc.setRemoteDescription({type:'offer',sdp:o.d});await pc.setLocalDescription(await pc.createAnswer());await juntarICE(pc);}
    catch(e){INV.p=null;try{pc.close();}catch(e2){}return {ok:false,msg:'No se pudo preparar el enlace en este teléfono.'};}
    p.respuesta=await empacar({v:1,k:'a',s:o.s,r:o.r,n:nombreYo(),d:sdpMin(pc.localDescription.sdp)});
    avisar();return {ok:true,rol:o.r,admin:p.admin,respuesta:p.respuesta,par:p};});
}
// espera a que el canal abra (o ms)
function esperarConexion(p,ms=30000){return new Promise(ok=>{const t0=ahora();const f=()=>{if(p.estado==='conectado')return ok(true);if(p.estado==='caido'||p.estado==='cerrado'||ahora()-t0>ms)return ok(false);setTimeout(f,150);};f();});}

/* la cámara del personal: la vista de su rol, en vivo; cuando llega el disparo toma las fotos y las manda */
async function camaraPersonal(p){
  if(!window.CamVivo||!window.Vision){toast('Este teléfono no tiene la cámara con visión: instala la versión beta.',4500);return;}
  const R=ROLES[p.rol];
  await CamVivo.abrir({titulo:'Cámara enlazada',clase:Vision.VACA,ref:R.ref,buscar:'Apunta al animal, que se vea completo',
    pasos:[{id:p.rol,nombre:R.nombre,instr:R.instr,girar:R.girar,ang:R.ang,dist:R.dist,banda:R.banda}],
    personal:{
      admin:p.admin,
      estado:e=>p.enviar({t:'estado',c:e.c,m:e.m,p:e.p}),
      vivo:()=>p.estado==='conectado',
      alDisparo:fn=>{p.alMsg=m=>{if(m.t==='disparo')fn(m.id);else if(m.t==='resultado')fn(null,m.m);};},
      enviar:async(id,fotos)=>{if(!fotos.length){p.enviar({t:'nofoto',id});return;}
        for(let i=0;i<fotos.length;i++)await p.enviarFoto(id,i,fotos.length,fotos[i].blob,{w:fotos[i].w,h:fotos[i].h});}}});
}

/* ---------- por el equipo (servidor): invitación y respuesta en sobres ---------- */
// la administración invita a un colaborador (equipo.js manda el sobre 'cam'); su respuesta llega como 'camr'
async function invitarEquipo(rol,vid){
  if(!window.EquipoJefe||!EquipoJefe.hayServidor())return {ok:false,msg:'Sin servidor del equipo: enlaza con el código QR.'};
  const t=await ofertar(rol);const ok=await EquipoJefe.enviarCam(vid,{t,rol,ts:ahora()});
  return ok?{ok:true}:{ok:false,msg:'No se pudo enviar la invitación. Enlaza con el código QR.'};
}
window.alCamEquipo=async c=>{if(c&&c.t)await responder(c.t);};   // la respuesta del colaborador (desde equipo.js)

/* ---------- pantallas ---------- */
const estadoTxt=p=>p.estado==='conectado'?(p.vista&&p.vista.c==='verde'&&ahora()-p.vista.t<1500?'Lista':'Conectada'):p.estado==='enlazando'?'Esperando el otro teléfono':p.estado==='caido'?'Se perdió la conexión':'Cerrada';
function tarjeta(){
  const P=SES.pares,colab=window.EquipoJefe&&EquipoJefe.hayServidor()?EquipoJefe.activos():[];
  return `<section class="sec">${secH('Teléfonos enlazados',conectados().length||'')}<div class="card pad en-card">
    <p class="hint" style="margin:0">Hasta dos teléfonos más toman al animal al mismo tiempo que este: por detrás y del otro costado (o desde arriba). Se conectan por el mismo Wi-Fi o por el punto de acceso de un teléfono, sin internet.</p>
    <div class="rows en-roles">${Object.entries(ROLES).map(([r,R])=>{const p=P[r];return `<div class="row"><div class="tx"><b>${R.nombre}</b><span>${p&&p.estado!=='cerrado'?`${p.nombre?`<span data-no-tr>${esc(p.nombre)}</span> · `:''}<span>${estadoTxt(p)}</span>`:'Sin enlazar'}</span></div>
      ${p&&p.estado!=='cerrado'?`<button type="button" class="btn sm" data-act="enSoltar" data-r="${r}">Quitar</button>`:`<button type="button" class="btn sm" data-act="enEnlazar" data-r="${r}">Enlazar</button>`}</div>`;}).join('')}</div>
    ${colab.length?`<p class="hint" style="margin:0">Con el servidor del equipo también puedes invitar a un colaborador sin leer códigos.</p>`:''}
    <button type="button" class="lnk" data-act="enUnirme">Usar este teléfono como cámara de otro</button></div></section>`;
}
function hojaEnlace(rol){
  const R=ROLES[rol],colab=window.EquipoJefe&&EquipoJefe.hayServidor()?EquipoJefe.activos():[];
  openSheet(shHead('Enlazar un teléfono',R.nombre)+`<div class="sh-body en-hoja"><p class="hint">En el otro teléfono abre Rumentis Equipo (o Rumentis Beta), toca Cámara para pesaje y lee este código.</p>
    <div class="en-qr"><span class="spin"></span></div><p class="hint">Después el otro teléfono muestra su código: léelo con este.</p>
    <button type="button" class="btn pri full" data-act="enLeer">Leer el código del otro teléfono</button>
    ${colab.length?`<p class="hint">O invita a un colaborador por el equipo:</p><div class="rows">${colab.map(v=>`<div class="row"><div class="tx"><b data-no-tr>${esc(v.nombre||'')}</b></div><button type="button" class="btn sm" data-act="enInvitar" data-r="${rol}" data-v="${esc(v.id)}">Invitar</button></div>`).join('')}</div>`:''}
    <p class="hint en-est"></p></div><div class="sh-foot"><button type="button" class="btn" data-act="cerrar" style="flex:1">Listo</button></div>`);
  HOJA.rol=rol;
  ofertar(rol).then(async t=>{const b=$('#sheet .en-qr');if(b&&HOJA.rol===rol){b.innerHTML=await N().qrSvg(t,{ecc:'L'});b.dataset.t=t;}}).catch(e=>{console.warn(e);const b=$('#sheet .en-qr');if(b)b.innerHTML='<p class="hint">No se pudo preparar el enlace en este teléfono.</p>';});
}
const HOJA={rol:null};
// la hoja se actualiza sola cuando cambia el estado de un par
escuchas.add(()=>{const e=$('#sheet .en-est');if(e&&HOJA.rol){const p=SES.pares[HOJA.rol];e.textContent=p?estadoTxt(p):'';if(p&&p.estado==='conectado'){toast(`${ROLES[HOJA.rol].nombre}: enlazado`);HOJA.rol=null;closeSheet();}}
  if(route().p==='pesocam'&&!$('#sheet.open')&&!document.querySelector('dialog.cv'))render();});
async function leerCodigo(){const t=await N().escanearQR('Apunta al código QR del otro teléfono');return t;}
ACTS.enEnlazar=el=>{const r=el.dataset.r;if(ROLES[r])hojaEnlace(r);};
ACTS.enSoltar=el=>{soltar(el.dataset.r);render();};
ACTS.enLeer=async()=>{const t=await leerCodigo();if(!t)return;const r=await responder(t);const e=$('#sheet .en-est');if(!r.ok&&r.msg){if(e)e.textContent=r.msg;else toast(r.msg,4000);}else if(e)e.textContent='Conectando…';};
ACTS.enInvitar=async el=>{const r=await invitarEquipo(el.dataset.r,el.dataset.v);const e=$('#sheet .en-est');if(e)e.textContent=r.ok?'Invitación enviada. Espera a que el colaborador la acepte.':r.msg;};
/* el personal: leer el código de la administración, mostrar el suyo y abrir la cámara */
async function unirseCon(t){
  const r=await unirse(t);if(!r.ok){toast(r.msg,4500);return null;}
  openSheet(shHead('Cámara enlazada',ROLES[r.rol].nombre)+`<div class="sh-body en-hoja"><p class="hint">Que la administración lea este código con su teléfono.</p><div class="en-qr">${await N().qrSvg(r.respuesta,{ecc:'L'})}</div>
    <p class="hint en-est">Esperando la conexión…</p></div><div class="sh-foot"><button type="button" class="btn" data-act="enCancelar" style="flex:1">Cancelar</button></div>`);
  const ok=await esperarConexion(r.par,120000);
  if(!ok){if(INV.p===r.par){const e=$('#sheet .en-est');if(e)e.textContent='No se pudieron conectar. Que los dos teléfonos estén en el mismo Wi-Fi o en el punto de acceso de uno de ellos.';}return null;}
  closeSheet();await camaraPersonal(r.par);if(INV.p===r.par){r.par.cerrar();INV.p=null;}return r.par;
}
ACTS.enUnirme=async()=>{closeSheet();const t=await leerCodigo();if(t)await unirseCon(t);};
ACTS.enCancelar=()=>{if(INV.p){INV.p.cerrar();INV.p=null;}closeSheet();};
// invitación por el equipo (en Rumentis Equipo, desde vaquero.js): se responde por sobre y se abre la cámara
async function aceptarInvitacion(c,enviarRespuesta){
  const r=await unirse(c.t);if(!r.ok){toast(r.msg,4500);return false;}
  const ok0=await enviarRespuesta({t:r.respuesta,rol:r.rol,ts:ahora()});if(!ok0){toast('No se pudo responder. Enlaza con el código QR.',4500);return false;}
  toast('Conectando con la administración…',3000);
  if(!await esperarConexion(r.par,45000)){toast('No se pudieron conectar. Que los dos teléfonos estén en el mismo Wi-Fi o en el punto de acceso de uno de ellos.',6000);r.par.cerrar();return false;}
  await camaraPersonal(r.par);if(INV.p===r.par){r.par.cerrar();INV.p=null;}return true;
}

window.Enlace={ROLES,ofertar,responder,unirse,unirseCon,aceptarInvitacion,esperarConexion,pares,conectados,listas,esperando,disparar,avisarResultado,
  soltar,soltarTodo,tarjeta,sesion,escuchar:f=>{escuchas.add(f);return ()=>escuchas.delete(f);},empacar,desempacar,sdpMin,INV};
})();
