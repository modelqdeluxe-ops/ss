/* Rumentis Vaquero: la app del equipo (se arma con los mismos archivos; config.js dice app:'vaquero').
   - La primera vez: la bienvenida de Rumentis y luego dos datos, su nombre real y la licencia que le dio el jefe
     (escrita, pegada o escaneada del QR). Con servidor, la licencia se busca ahí; sin servidor, llega en el enlace o QR.
   - Solo lo básico del día: Hoy (tareas y registrar), Lotes (sin dinero), Registrar, Tareas y Más. Sin Rumi, sin
     finanzas, sin precios, sin documentos. Lo que puede registrar lo decide el jefe.
   - Todo lo que anota se guarda en el teléfono y queda en una cola de operaciones numeradas que se manda al jefe
     (servidor o archivo). Cuando llega el estado del jefe, se toma como base y se vuelven a aplicar las operaciones
     que el jefe todavía no confirma: así nunca se pierde nada ni se duplica.
   - Si el jefe lo da de baja, la app se bloquea y se pueden borrar los datos. */
(function(){
'use strict';
const N=window.EquipoNucleo;if(!N||N.CFG.app!=='vaquero')return;
const CFG=N.CFG;
IC.tareas='<rect x="4" y="4" width="16" height="17" rx="2.5"/><path d="M8 9.5l1.6 1.6L12.5 8M8 15.5l1.6 1.6 2.9-3.1M14.5 10h2.5M14.5 16h2.5"/>';

/* ---------- estado del vaquero (fuera de S: el estado del jefe reemplaza S entero) ---------- */
const LSV='rumentis-vq';
let VQ={};try{VQ=JSON.parse(localStorage.getItem(LSV)||'{}')||{};}catch(e){VQ={};}
if(!VQ.estado)VQ.estado='nuevo';
const guardarVQ=()=>{try{localStorage.setItem(LSV,JSON.stringify(VQ));}catch(e){}};
const activo=()=>VQ.estado==='activo';
const P=()=>VQ.permisos||{};
const servidor=()=>N.urlOk((VQ.ficha&&VQ.ficha.s)||CFG.servidorEquipo);
const primerNombre=()=>String(VQ.nombre||'').trim().split(/\s+/)[0]||'';

/* ---------- operaciones: lo que anota el vaquero ---------- */
let APLICANDO=0;
function nuevaOp(o){VQ.seq=(+VQ.seq||0)+1;const op={...o,seq:VQ.seq,ts:Date.now()};VQ.cola=(VQ.cola||[]).concat(op);guardarVQ();enviarPronto();return op;}
const iguales=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
function registrarOps(col,id,data){
  if(col==='diario'){
    const antes=new Map(((S.diario[id]||{}).items||[]).map(i=>[i.id,i])),despues=new Map(((data||{}).items||[]).map(i=>[i.id,i]));
    for(const [k,it] of despues){if(!antes.has(k))nuevaOp({k:'item+',it});else if(!iguales(antes.get(k),it))nuevaOp({k:'item~',it});}
    for(const k of antes.keys())if(!despues.has(k))nuevaOp({k:'item-',id:k});
  }else if(col==='lotes'){
    const l=S.lotes[id];if(!l||!data)return;const campos={};
    for(const k of ['racion','animales','estado','fechaCierre'])if(!iguales(l[k],data[k]))campos[k]=data[k];
    if(Object.keys(campos).length)nuevaOp({k:'lote~',id,campos});
  }else if(col==='ajustes'&&data){
    const h0=S.config.hechas||{},h1=data.hechas||{};
    for(const [k,f] of Object.entries(h1))if(h0[k]!==f){const t=(window.Agenda?Agenda.tareas():[]).find(x=>x.key===k);nuevaOp({k:'hecha',key:k,f,txt:t?t.t:''});}
    const t0=new Map((S.config.tareas||[]).map(t=>[t.id,t]));
    for(const t of data.tareas||[])if(t.hecho&&t0.has(t.id)&&!t0.get(t.id).hecho)nuevaOp({k:'tarea',id:t.id});
  }
}
const _put=window.put;
window.put=put=function(col,id,data){
  if(!APLICANDO&&activo()){
    if(col==='raciones')return;   // el vaquero no cambia raciones
    if(col==='lotes'&&!S.lotes[id])return;   // ni crea lotes
    try{registrarOps(col,id,data);}catch(e){}
  }
  return _put(col,id,data);
};
// aplica una operación a los datos del teléfono (al volver a poner las que el jefe no ha confirmado)
function aplicarLocal(op){
  if(op.k==='item+'){const it=op.it;if(!it||!it.f)return;const k=it.f.slice(0,7);const d=S.diario[k]||(S.diario[k]={items:[]});if(!(d.items||[]).some(i=>i.id===it.id))d.items=(d.items||[]).concat(it);}
  else if(op.k==='item-'){for(const d of Object.values(S.diario))d.items=(d.items||[]).filter(i=>i.id!==op.id);}
  else if(op.k==='item~'){for(const d of Object.values(S.diario))d.items=(d.items||[]).map(i=>i.id===op.it.id?op.it:i);}
  else if(op.k==='lote~'){const l=S.lotes[op.id];if(l)S.lotes[op.id]={...l,...op.campos};}
  else if(op.k==='hecha'){S.config.hechas={...(S.config.hechas||{}),[op.key]:op.f};}
  else if(op.k==='tarea'){S.config.tareas=(S.config.tareas||[]).map(t=>t.id===op.id?{...t,hecho:true}:t);}
}
function aplicarEstado(est){
  APLICANDO=1;
  try{
    const yo=(est.vaqueros||{})[VQ.vid];
    if(yo&&yo.estado==='baja'){darmeDeBaja();return;}
    if(yo)VQ.permisos=yo.permisos||VQ.permisos;
    VQ.nombres=Object.values(est.vaqueros||{}).map(v=>v.n).filter(Boolean);   // los compañeros: sus nombres no se traducen
    S.lotes=est.lotes||{};S.raciones=est.raciones||{};
    const di={};for(const it of est.items||[]){const k=String(it.f).slice(0,7);(di[k]=di[k]||{items:[]}).items.push(it);}S.diario=di;
    const c=est.cfg||{};
    S.config={...DEF_CFG,...c,finca:c.finca||VQ.finca||DEF_CFG.finca,tareas:(c.tareas||[]).filter(t=>t.para==='todos'||t.para===VQ.vid),hechas:c.hechas||{}};
    const ack=(est.acks||{})[VQ.vid]||0;VQ.cola=(VQ.cola||[]).filter(o=>o.seq>ack);VQ.ack=ack;
    for(const op of VQ.cola)aplicarLocal(op);
    VQ.estadoTs=est.ts||Date.now();VQ.finca=S.config.finca;guardarVQ();
    ver++;lsSave();
  }finally{APLICANDO=0;}
  render();
}

/* ---------- mensajes del jefe ---------- */
async function procesar(s){
  if(!s||s.e!==VQ.e||(s.para!==VQ.vid&&s.para!=='todos'))return 0;
  const vis=VQ.vistos||[];if(vis.includes(s.id))return 0;
  const jefe=VQ.ficha&&VQ.ficha.j,caja={pub:VQ.ficha&&VQ.ficha.k,sec:VQ.yo&&VQ.yo.caja.sec};let hecho=1;
  if(s.t==='bienvenida'){const c=await N.abrir(s,{caja,firmaPub:jefe});if(!c)return 0;
    if(c.prueba&&!CFG.prueba){VQ.estado='rechazada';VQ.motivo='Esa licencia es de la app de prueba. Pídele a tu jefe una licencia de la app de Google Play.';}
    else if(c.compra&&N.compraReal(c.compra.json,c.compra.firma)===false){VQ.estado='rechazada';VQ.motivo='Esa licencia no salió de una compra válida.';}
    else{VQ.estado='activo';VQ.vid=c.vid||VQ.vid;VQ.clave=c.clave;VQ.gen=c.gen;VQ.claves={...(VQ.claves||{}),[c.gen]:c.clave};VQ.permisos=c.permisos||{};VQ.finca=c.finca||VQ.finca;VQ.desde=hoy();ver++;
      toast(`¡Listo, ${primerNombre()}! Ya estás en el equipo de ${VQ.finca||'la finca'}.`,4500);}}
  else if(s.t==='rechazo'){const c=await N.abrir(s,{caja,firmaPub:jefe});if(!c)return 0;if(VQ.estado!=='activo'){VQ.estado='rechazada';VQ.motivo=c.motivo||'Tu jefe no aceptó la licencia.';}}
  else if(s.t==='clave'){const c=await N.abrir(s,{caja,firmaPub:jefe});if(!c)return 0;VQ.claves={...(VQ.claves||{}),[c.gen]:c.clave};if(c.gen>(+VQ.gen||0)){VQ.gen=c.gen;VQ.clave=c.clave;}}
  else if(s.t==='baja'){const c=await N.abrir(s,{caja,firmaPub:jefe});if(!c)return 0;darmeDeBaja();}
  else if(s.t==='estado'){if(!activo())return 0;const k=(VQ.claves||{})[s.g];if(!k){VQ.estadoEspera=s;guardarVQ();return 0;}
    const c=await N.abrir(s,{clave:k,firmaPub:jefe});if(!c)return 0;if((c.ts||0)>=(VQ.estadoTs||0))aplicarEstado(c);}
  else hecho=0;
  VQ.vistos=(VQ.vistos||[]).concat(s.id).slice(-400);guardarVQ();
  if(VQ.estadoEspera&&(VQ.claves||{})[VQ.estadoEspera.g]){const e=VQ.estadoEspera;VQ.estadoEspera=null;await procesar(e);}
  return hecho;
}
// el archivo del jefe trae la ficha del equipo: si me activé sin enlace, la tomo (solo si el archivo trae algo para mí)
async function adoptarFicha(o){
  if(VQ.ficha)return true;const fi=o&&o.ficha;if(!fi)return true;
  if(!(o.sobres||[]).some(s=>s.para===VQ.vid&&s.e===fi.e))return false;
  if(!(await N.fichaValida(fi)))return false;
  if(fi.p&&!CFG.prueba){VQ.estado='rechazada';VQ.motivo='Esa licencia es de la app de prueba. Pídele a tu jefe una licencia de la app de Google Play.';guardarVQ();render();return false;}
  VQ.ficha=fi;VQ.e=fi.e;VQ.finca=fi.fn||VQ.finca;guardarVQ();ver++;return true;
}
function darmeDeBaja(){VQ.estado='baja';VQ.clave=null;VQ.claves={};VQ.cola=[];guardarVQ();render();}
// los mensajes de una vez: primero las claves, luego lo demás, el estado al final
const ORDEN={bienvenida:0,rechazo:1,clave:2,baja:3,estado:4};
async function procesarVarios(L){let n=0;for(const s of [...L].sort((a,b)=>(ORDEN[a.t]??5)-(ORDEN[b.t]??5)||a.ts-b.ts))n+=await procesar(s);return n;}

/* ---------- alta ---------- */
async function sobreAlta(){
  const carga={lic:VQ.lic,nombre:VQ.nombre,disp:(navigator.userAgent.match(/Android[^;)]*;[^;)]*?([^;)]+)\)/)||[])[1]||'',ts:Date.now()};
  return N.sellar('alta',{e:VQ.e,de:VQ.vid,para:'jefe',carga,caja:{pub:VQ.ficha.k,sec:VQ.yo.caja.sec},firmaSec:VQ.yo.firma.sec,extra:{k:VQ.yo.caja.pub,j:VQ.yo.firma.pub}});
}
async function activar(nombre,lic,ficha){
  if(!VQ.yo)VQ.yo=await N.nuevasLlaves();
  VQ.nombre=nombre;VQ.lic=lic;VQ.ficha=ficha;VQ.e=ficha.e;VQ.vid=N.idDe(VQ.yo.firma.pub);VQ.finca=ficha.fn||'';VQ.estado='pendiente';VQ.motivo='';VQ.altaEnviada=0;VQ.cursor=0;VQ.vistos=[];ver++;
  VQ.alta=await sobreAlta();guardarVQ();render();
  if(servidor())await sincronizar();
}
// sin servidor y sin enlace: el vaquero queda pendiente con solo su licencia; su alta va firmada en un archivo para
// el jefe, y del archivo que el jefe le devuelve toma las llaves del equipo
async function activarSinFicha(nombre,lic){
  if(!VQ.yo)VQ.yo=await N.nuevasLlaves();
  VQ.nombre=nombre;VQ.lic=lic;VQ.ficha=null;VQ.e=null;VQ.vid=N.idDe(VQ.yo.firma.pub);VQ.finca='';VQ.estado='pendiente';VQ.motivo='';VQ.altaEnviada=0;VQ.cursor=0;VQ.vistos=[];ver++;
  VQ.alta=await N.sobrePlano('alta',{de:VQ.vid,para:'jefe',carga:{lic,nombre,disp:'',ts:Date.now()},firmaSec:VQ.yo.firma.sec,extra:{k:VQ.yo.caja.pub,j:VQ.yo.firma.pub}});
  guardarVQ();render();
}
async function buscarFicha(lic){
  const base=N.urlOk(CFG.servidorEquipo);if(!base)return null;
  try{const r=await N.pedir(base,'/v1/ficha',{h:N.hashLic(lic)});return r.ficha||null;}catch(e){if(e.status===404)return false;throw e;}
}
ACTS.vqActivar=async()=>{
  const f=$('#vqForm');if(!f)return;const nombre=(f.elements.nombre.value||'').trim().replace(/\s+/g,' '),txt=f.elements.lic.value||'';const err=$('.err',f);err.textContent='';
  if(nombre.length<3||!/\s/.test(nombre)){err.textContent=aUnidad('Escribe tu nombre y apellido, como te conoce tu jefe.');return;}
  if(N.esLicPrueba(txt))return modoPrueba(nombre);
  const enl=N.leerEnlace(txt)||N.leerEnlace(VQ.enlace||'');let lic=enl?enl.lic:N.normLic(txt);
  if(enl&&N.normLic(txt)&&N.licValida(N.normLic(txt))&&N.normLic(txt)!==enl.lic)lic=N.normLic(txt);
  if(!N.licValida(lic)){err.textContent=aUnidad('Revisa la licencia: son 20 letras y números, en grupos de 4 después de RV.');return;}
  const b=f.querySelector('.btn.pri');b.disabled=true;b.textContent=aUnidad('Buscando tu licencia…');
  try{
    let ficha=enl&&enl.lic===lic?enl.ficha:null;
    if(!ficha){try{ficha=await buscarFicha(lic);}catch(e){ficha=null;}}   // sin internet: se sigue por archivo
    if(ficha===false){err.textContent=aUnidad('No encuentro esa licencia. Revísala con tu jefe.');return;}
    if(!ficha)return activarSinFicha(nombre,lic);
    if(!(await N.fichaValida(ficha))){err.textContent=aUnidad('Esa licencia no es válida.');return;}
    if(ficha.p&&!CFG.prueba){err.textContent=aUnidad('Esa licencia es de la app de prueba. Pídele a tu jefe una licencia de la app de Google Play.');return;}
    await activar(nombre,lic,ficha);
  }finally{if(b.isConnected){b.disabled=false;b.textContent=aUnidad('Activar');}}
};
ACTS.vqEscanear=async()=>{const t=await N.escanearQR();if(!t)return;const enl=N.leerEnlace(t);
  if(!enl){toast('Ese QR no es una licencia de Rumentis.',3500);return;}VQ.enlace=t;guardarVQ();const f=$('#vqForm');if(f){f.elements.lic.value=N.fmtLic(enl.lic);f.elements.lic.dispatchEvent(new Event('input'));}
  toast(`Licencia de ${enl.ficha.fn||'tu jefe'} lista`,3000);};
ACTS.vqPegar=async()=>{try{const t=await navigator.clipboard.readText();const f=$('#vqForm');if(!f||!t)return;const enl=N.leerEnlace(t);
  if(enl){VQ.enlace=t;guardarVQ();f.elements.lic.value=N.fmtLic(enl.lic);}else f.elements.lic.value=t.trim();}catch(e){toast('Mantén presionado el campo y elige Pegar.',3500);}};
ACTS.vqOtra=()=>{VQ.estado='nuevo';VQ.alta=null;VQ.ficha=null;VQ.enlace='';guardarVQ();render();};
ACTS.vqRevisar=async()=>{if(!servidor()){toast('Sin servidor: pide a tu jefe el archivo del equipo.',4000);return;}toast('Revisando…');await sincronizar();if(!activo())toast('Tu jefe todavía no confirma.',3000);};
function modoPrueba(nombre){
  VQ={...VQ,estado:'activo',demo:true,nombre,lic:'RVPRUEBA2026',vid:'vprueba',finca:'Finca de prueba',permisos:{alimento:true,pesaje:true,sanidad:true,bajas:true,tareas:true},cola:[],seq:0,desde:hoy()};
  APLICANDO=1;try{if(typeof cargarDemo==='function')cargarDemo();VQ.finca=S.config.finca||VQ.finca;lsSave();}finally{APLICANDO=0;}guardarVQ();ver++;
  toast('Licencia de prueba: todo se queda en este teléfono.',4500);location.hash='#hoy';render();
}

/* ---------- sincronizar ---------- */
let ocupado=null,prontoT=0;
function enviarPronto(ms=2500){clearTimeout(prontoT);prontoT=setTimeout(()=>sincronizar(),ms);}
async function sobreOps(){if(!activo()||!(VQ.cola||[]).length||VQ.demo)return null;
  return N.sellar('ops',{e:VQ.e,de:VQ.vid,para:'jefe',carga:{ops:VQ.cola},clave:VQ.clave,g:VQ.gen,firmaSec:VQ.yo.firma.sec});}
async function sincronizar(){
  if(ocupado)return ocupado;const base=servidor();if(!base||VQ.demo||!VQ.yo||!VQ.e||VQ.estado==='nuevo'||VQ.estado==='baja')return null;
  ocupado=(async()=>{let n=0;
    try{
      const yo=VQ.yo.firma;
      if(VQ.estado==='pendiente'&&!VQ.altaEnviada&&VQ.alta){await N.pedir(base,'/v1/alta',{h:N.hashLic(VQ.lic),sobre:VQ.alta});VQ.altaEnviada=Date.now();guardarVQ();}
      for(let i=0;i<10;i++){const r=await N.pedir(base,'/v1/recibir',{e:VQ.e,quien:VQ.vid,firma:yo.pub,desde:VQ.cursor||0},yo.sec);
        n+=await procesarVarios(r.sobres||[]);VQ.cursor=r.hasta||VQ.cursor;guardarVQ();if(!r.mas)break;}
      const s=await sobreOps();
      if(s&&(VQ.enviado!==VQ.seq||Date.now()-(VQ.enviadoTs||0)>10*60e3)){await N.pedir(base,'/v1/enviar',{e:VQ.e,quien:VQ.vid,firma:yo.pub,sobres:[s]},yo.sec);VQ.enviado=VQ.seq;VQ.enviadoTs=Date.now();}
      VQ.ult=Date.now();VQ.err='';guardarVQ();
    }catch(err){VQ.err=String(err&&err.message||err);guardarVQ();}
    finally{ocupado=null;updateSync();if(!activo()||/^#(mas|hoy)?$/.test(location.hash.split('/')[0]))render();}
    return n;})();
  return ocupado;
}
setInterval(()=>{if(!document.hidden)sincronizar();},VQ.estado==='pendiente'?20000:60000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden)sincronizar();});
addEventListener('online',()=>sincronizar());
ACTS.vqSinc=async()=>{if(!servidor()){toast('Sin servidor: manda tus registros a tu jefe por archivo.',4000);return;}toast('Sincronizando…');const n=await sincronizar();
  toast(VQ.err?'No se pudo conectar. Se envía sola cuando haya internet.':n?'Listo: datos nuevos de tu jefe':'Todo al día',3200);};
ACTS.vqEnviarArchivo=async()=>{
  const L=[];if(VQ.estado==='pendiente'&&VQ.alta)L.push(VQ.alta);const s=await sobreOps();if(s)L.push(s);
  if(!L.length){toast('No tienes nada por mandar.',3000);return;}
  const nm=String(VQ.nombre||'vaquero').toLowerCase().normalize('NFD').replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'');
  N.compartirArchivo(N.nombreArchivo(`${nm}-${hoy()}`),await N.armarArchivo(L,VQ.vid),aUnidad('Mis registros para el jefe'));
};
async function alRecibir(o){
  if(!(await adoptarFicha(o))){toast('Ese archivo no es de tu jefe.',4000);return false;}
  const n=await procesarVarios(o.sobres);toast(n?'Listo: datos de tu jefe al día':'Nada nuevo para ti en ese archivo',3500);render();return true;}
ACTS.vqRecibirArchivo=async()=>{const u=await N.elegirArchivo();if(u)await N.recibirArchivo(u,alRecibir);};
// al tocar el archivo .rumentis del jefe en WhatsApp se abre la app y llega aquí
N.escucharArchivos(alRecibir);
ACTS.vqBorrar=()=>confirmar('¿Borrar los datos de este teléfono?','Se borran tu licencia y los datos de la finca. Lo que ya le llegó a tu jefe se queda con él.','Borrar',()=>{
  try{localStorage.removeItem(LSV);localStorage.removeItem(LS_KEY);}catch(e){}location.hash='#hoy';location.reload();});

/* ---------- lo que se ve: la sincronización ---------- */
syncTxt=function(){
  if(VQ.demo)return 'Modo de prueba';
  const p=(VQ.cola||[]).length;
  if(!servidor())return p?`${pl(p,'registro','registros')} por mandar`:'Guardado en este teléfono';
  if(p)return VQ.err?`${pl(p,'registro','registros')} por enviar`:'Enviando al jefe';
  return VQ.ult?'Todo enviado':'Guardado en este teléfono';
};

/* ---------- pantallas ---------- */
const PERMITIDAS=new Set(['hoy','lotes','lote','registrar','tareas','mas']);
const hdV=(t,sub,back)=>`<header class="hd">${back?hdBack(back[0],back[1],'<span class="sync"></span>'):hdTop('<span class="sync"></span>')}<div class="ttl"${back?' style="margin-top:-6px"':''}>${!back?`<span class="eyebrow">${esc(VQ.finca||S.config.finca||'')}</span>`:''}<h1>${t}</h1>${sub?`<p class="sub">${sub}</p>`:''}</div></header>`;
const REG=[['alimento','ic-y','Alimento','Entrega a cada lote','alimento'],['pesaje','ic-a','Pesaje','Peso del lote o de cada animal','pesaje'],['sanidad','ic-v','Sanidad','Vacuna, desparasitante, tratamiento','sanidad'],['baja','ic-r','Muerte','Cabezas y causa','bajas']];
const tilesReg=(lote)=>{const T=REG.filter(r=>P()[r[4]]);if(!T.length)return `<p class="hint">Tu jefe todavía no te da permiso para registrar. Pídeselo.</p>`;
  return `<div class="tiles">${T.map(([k,c,t,s])=>`<button type="button" class="tile" data-act="f" data-f="${k}"${lote?` data-lote="${esc(lote)}"`:''}><span class="ic ${c}">${ico(k)}</span><div><b>${t}</b><span>${s}</span></div></button>`).join('')}</div>`;};
// tareas para el vaquero: las de su trabajo y las que le asignó el jefe
function misTareas(){
  const T=window.Agenda?Agenda.tareas():[];const p=P();
  return T.filter(t=>{const k=t.key||'';
    if(k.startsWith('ent:'))return p.alimento;if(k.startsWith('pes:'))return p.pesaje;if(k.startsWith('ref:'))return p.sanidad;if(k.startsWith('dia:rev:'))return true;
    if(k.startsWith('propia:'))return true;return false;});
}
function filaTarea(t,H){
  const hacer=t.act&&t.act.t==='form'&&P()[{alimento:'alimento',pesaje:'pesaje',sanidad:'sanidad'}[t.act.k]]?`<button type="button" class="btn sm" data-act="f" data-f="${t.act.k}"${t.act.p&&t.act.p.lote?` data-lote="${esc(t.act.p.lote)}"`:''}${t.act.n?` data-n="${t.act.n}"`:''}>Hacer</button>`:'';
  const ok=P().tareas?`<button type="button" class="ag-ok" data-act="agHecha" data-k="${esc(t.key)}" data-p="${esc(t.propia||'')}" aria-label="Marcar como hecha">${ico('check',2.4)}</button>`:'';
  return `<div class="row ag-row"><span class="mas-ic t-${t.tono||'a'}">${icono(t.ic==='alimento'?'alimento':t.ic==='pesaje'?'pesaje':t.ic==='sanidad'?'jeringa':t.ic==='nota'?'nota':'ojo','i3')}</span><div class="tx"><b>${esc(t.t)}</b><span>${t.propia?'Te la pidió tu jefe':esc(t.s)}${t.f>H?` · ${ffc(t.f)}`:t.f<H?' · atrasada':''}</span></div><div class="ag-acts">${hacer}${ok}</div></div>`;
}
const RTX={alimento:i=>`${nf(i.kg)} kg de alimento a ${nomL(i.lote)}`,pesaje:i=>i.prom?`Pesaje de ${nomL(i.lote)}: ${wtxt(i.prom)}`:`Pesaje de animales de ${nomL(i.lote)}`,sanidad:i=>`${i.producto||i.clase} en ${nomL(i.lote)}`,baja:i=>`${pl(+i.cab||1,'muerte','muertes')} en ${nomL(i.lote)}`};
const nomL=id=>S.lotes[id]?S.lotes[id].nombre:'un lote';
function misRegistros(dia){const mios=new Set((VQ.cola||[]).filter(o=>o.k==='item+').map(o=>o.it.id));
  return allItems().filter(i=>(!dia||i.f===dia)&&((i.por&&i.por.v===VQ.vid)||mios.has(i.id))).reverse();}
function filaReg(i){const pend=(VQ.cola||[]).some(o=>o.k==='item+'&&o.it.id===i.id);
  return `<div class="row"><span class="mas-ic t-${i.tipo==='baja'?'r':i.tipo==='alimento'?'y':i.tipo==='sanidad'?'v':'a'}">${icono(i.tipo==='baja'?'alerta':i.tipo==='sanidad'?'jeringa':i.tipo,'i3')}</span><div class="tx"><b>${esc((RTX[i.tipo]||(()=>i.tipo))(i))}</b><span><span>${cuando(i.f)}</span> · <span>${pend?'por enviar':'enviado'}</span></span></div>${pend?`<button type="button" class="del" data-act="delItems" data-ids="${i.id}" aria-label="Borrar">${ico('papelera')}</button>`:''}</div>`;}

function pantallaActivar(){
  const e=VQ.estado;
  const logo=`<div class="vq-logo">${typeof LOGO_HD!=='undefined'?LOGO_HD:''}<span>Vaquero</span></div>`;
  if(e==='pendiente')return `<main class="vq-act">${logo}<div class="card pad vq-card">
    <div class="vq-espera" aria-hidden="true"><i></i><i></i><i></i></div>
    <h1>Esperando a tu jefe</h1><p>${VQ.finca?`Tu licencia ${N.fmtLic(VQ.lic)} es de ${esc(VQ.finca)}.`:`Tu licencia es ${N.fmtLic(VQ.lic)}.`}${servidor()&&VQ.ficha?' En cuanto tu jefe abra Rumentis, entras al equipo.':''}</p>
    ${servidor()&&VQ.ficha?`<button type="button" class="btn pri full" data-act="vqRevisar">Revisar ahora</button>`:`<p class="hint">Sin internet del equipo: manda tu alta a tu jefe por WhatsApp y abre el archivo que él te devuelva.</p>
    <button type="button" class="btn pri full" data-act="vqEnviarArchivo">Mandar mi alta al jefe</button><button type="button" class="btn full" data-act="vqRecibirArchivo">Abrir archivo del jefe</button>`}
    <button type="button" class="lnk" data-act="vqOtra">Usar otra licencia</button></div></main>`;
  if(e==='rechazada')return `<main class="vq-act">${logo}<div class="card pad vq-card"><h1>No se pudo activar</h1><p>${esc(VQ.motivo||'Tu jefe no aceptó la licencia.')}</p>
    <button type="button" class="btn pri full" data-act="vqOtra">Intentar con otra licencia</button></div></main>`;
  if(e==='baja')return `<main class="vq-act">${logo}<div class="card pad vq-card"><h1>Ya no estás en el equipo</h1><p>Tu jefe dio de baja tu licencia. Lo que registraste se queda con él.</p>
    <button type="button" class="btn danger full" data-act="vqBorrar">Borrar los datos de este teléfono</button><button type="button" class="lnk" data-act="vqOtra">Tengo otra licencia</button></div></main>`;
  const pre=VQ.enlace?N.leerEnlace(VQ.enlace):null;
  return `<main class="vq-act">${logo}<div class="card pad vq-card"><h1>Bienvenido al equipo</h1><p>Tu jefe te dio una licencia. Con ella anotas desde aquí la comida, los pesajes y la sanidad, y a él le llega todo.</p>
    <form id="vqForm" onsubmit="return false" novalidate>
    ${q('Tu nombre completo',`<input class="in" name="nombre" autocomplete="name" autocapitalize="words" placeholder="Nombre y apellido" value="${esc(VQ.nombre||'')}">`,'Como te conoce tu jefe.')}
    ${q('Licencia',`<input class="in vq-lic" name="lic" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="RV-XXXX-XXXX-XXXX-XXXX-XXXX" data-no-tr value="${pre?N.fmtLic(pre.lic):''}">`)}
    <div class="vq-bt"><button type="button" class="btn" data-act="vqEscanear">${icono('ojo','i3')}Escanear QR</button><button type="button" class="btn" data-act="vqPegar">Pegar</button></div>
    <p class="err"></p><button type="button" class="btn pri full" data-act="vqActivar">Activar</button></form>
    ${CFG.prueba?`<p class="hint vq-prueba">App de prueba: con la licencia <b data-no-tr>${N.LIC_PRUEBA}</b> entras a una finca de muestra sin jefe.</p>`:''}</div></main>`;
}

PAGES.hoy=()=>{
  const C=calc(),H=hoy(),T=misTareas(),hoyT=T.filter(t=>t.f<=H);
  const regs=misRegistros(H);
  return `<header class="hd">${hdTop(fechaLarga())}<div class="ttl"><span class="eyebrow">${esc(VQ.finca||S.config.finca||'')}</span><h1>Hola, ${esc(primerNombre())}</h1><p class="sub">${hoyT.length?pl(hoyT.length,'tarea para hoy','tareas para hoy'):'Todo al día por ahora'}${C.act.length?`, ${pl(C.act.length,'lote','lotes')} con ${pl(C.cabT,'cabeza','cabezas')}`:''}.</p></div></header>
  <main class="bd">
   ${VQ.demo?`<div class="eq-prueba">${ico('check',2.2)}<p><b>Modo de prueba.</b> Estás en una finca de muestra; lo que anotes se queda en este teléfono.</p></div>`:''}
   <section class="sec">${secH('Tareas de hoy',hoyT.length,hoyT.length?lnk('#tareas','Ver todas'):'')}${hoyT.length?`<div class="card rows">${hoyT.slice(0,6).map(t=>filaTarea(t,H)).join('')}</div>`:`<p class="hint">Nada pendiente. Si tu jefe te asigna algo, aparece aquí.</p>`}</section>
   <section class="sec">${secH('Anotar')}${tilesReg()}</section>
   <section class="sec">${secH('Lo que anotaste hoy',regs.length)}${regs.length?`<div class="card rows">${regs.slice(0,10).map(filaReg).join('')}</div>`:`<p class="hint">Todavía nada hoy.</p>`}</section>
  </main>`;
};
PAGES.registrar=()=>{const regs=misRegistros().slice(0,12);
  return hdV('Registrar','Toca lo que quieres anotar. Le llega a tu jefe con tu nombre.')+`<main class="bd"><section class="sec">${secH('Anotar')}${tilesReg()}</section>
   <section class="sec">${secH('Lo último que anotaste')}${regs.length?`<div class="card rows">${regs.map(filaReg).join('')}</div>`:`<p class="hint">Todavía no anotas nada.</p>`}</section></main>`;};
PAGES.tareas=()=>{const H=hoy(),T=misTareas();const hoyT=T.filter(t=>t.f<=H),sem=T.filter(t=>t.f>H&&t.f<=addDias(H,7)),mas=T.filter(t=>t.f>addDias(H,7));
  const sec=(t,L,v)=>`<section class="sec">${secH(t,L.length)}${L.length?`<div class="card rows">${L.map(x=>filaTarea(x,H)).join('')}</div>`:`<p class="hint">${v}</p>`}</section>`;
  return hdV('Tareas','Lo que toca hacer: lo que te pide tu jefe y lo del manejo de los lotes.')+`<main class="bd">${sec('Hoy',hoyT,'Nada pendiente para hoy.')}${sec('Próximos 7 días',sem,'Nada en los próximos días.')}${mas.length?sec('Más adelante',mas,''):''}</main>`;};
PAGES.lotes=()=>{const C=calc();
  const card=x=>`<a class="card lote vq-lote" href="#lote/${encodeURIComponent(x.id)}"><div class="l1"><div><span class="nm">${esc(x.l.nombre)}</span><span class="sm">${pl(x.cab,'cabeza','cabezas')}</span></div>${ico('chev')}</div>
    <div class="l2"><b>${nf(x.pesoHoy)} kg</b><span class="sm">${x.estimado?'estimado, ':'promedio, '}día ${x.dec} de engorde</span></div>
    <div class="vq-l3"><span>${x.racion&&S.raciones[x.racion]?esc(S.raciones[x.racion].nombre):'Sin ración asignada'}</span><span>Último pesaje: <span>${cuando(x.ult.f)}</span></span></div></a>`;
  return hdV('Lotes',C.act.length?`${pl(C.act.length,'lote','lotes')} con ${pl(C.cabT,'cabeza','cabezas')}.`:'')+`<main class="bd">${C.act.length?C.act.map(card).join(''):`<div class="card"><p class="empty">Tu jefe todavía no tiene lotes en engorde.</p></div>`}</main>`;};
PAGES.lote=id=>{const C=calc(),x=C.L[id];if(!x)return hdV('Lote no encontrado','',['#lotes','Lotes'])+`<main class="bd"><p class="hint">Puede que ya se haya cerrado.</p></main>`;
  const regs=allItems().filter(i=>i.lote===x.id&&RTX[i.tipo]).slice(-10).reverse();
  const an=(x.l.animales||[]).filter(a=>!a.baja&&!a.vendido).slice(0,300);
  return hdV(esc(x.l.nombre),`${pl(x.cab,'cabeza','cabezas')} · día ${x.dec} de engorde`,['#lotes','Lotes'])+`<main class="bd">
   <section class="sec"><div class="kpis vq-k"><div class="kpi"><b>${nf(x.pesoHoy)} kg</b><span>${x.estimado?'peso estimado':'peso promedio'}</span></div><div class="kpi"><b>${x.racion&&S.raciones[x.racion]?esc(S.raciones[x.racion].nombre):'—'}</b><span>ración</span></div><div class="kpi"><b>${cuando(x.ult.f)}</b><span>último pesaje</span></div></div></section>
   <section class="sec">${secH('Anotar en este lote')}${tilesReg(x.id)}</section>
   <section class="sec">${secH('Últimos registros',regs.length)}${regs.length?`<div class="card rows">${regs.map(i=>`<div class="row"><div class="tx"><b>${esc(RTX[i.tipo](i))}</b><span><span>${cuando(i.f)}</span>${i.por?` · <span data-no-tr>${esc(i.por.n)}</span>`:''}</span></div></div>`).join('')}</div>`:`<p class="hint">Sin registros recientes.</p>`}</section>
   ${an.length?`<section class="sec">${secH('Animales',an.length)}<div class="card rows vq-an">${an.map(a=>`<div class="row"><div class="tx"><b>Arete ${esc(a.arete)}</b><span>${esc([a.raza,a.color].filter(Boolean).join(', '))}</span></div></div>`).join('')}</div></section>`:''}
  </main>`;};
PAGES.mas=sub=>{
  if(sub==='apariencia'&&MAS_SUB.apariencia)return MAS_SUB.apariencia();
  if(sub==='acerca')return hdV('Acerca de Rumentis Vaquero','',['#mas','Más'])+`<main class="bd"><section class="sec"><div class="card pad acerca">${typeof FIRMA!=='undefined'?FIRMA:''}<p><b>Versión ${VERSION_APP}</b></p>
    <p>Lo que anotas se guarda en tu teléfono y se le manda cifrado a tu jefe: nadie más puede leerlo.</p><p>Tu licencia es solo tuya; no la compartas.</p></div></section></main>`;
  if(sub==='idioma')return hdV('Idioma','',['#mas','Más'])+`<main class="bd"><section class="sec"><div class="card rows">${(window.I18N?I18N.IDIOMAS:[]).map(([k,t])=>`<button type="button" class="row" data-act="vqIdioma" data-l="${k}"><div class="tx"><b data-no-tr>${esc(t)}</b></div>${(window.I18N&&I18N.lang()===k)?ico('check',2.4):''}</button>`).join('')}</div></section></main>`;
  const srv=servidor(),pend=(VQ.cola||[]).length;
  return `<header class="hd">${hdTop('<span class="sync"></span>')}<div class="ttl"><span class="eyebrow">${esc(VQ.finca||'')}</span><h1>Más</h1><p class="sub">Tu cuenta, el envío a tu jefe y ajustes.</p></div></header>
  <main class="bd">
   <section class="sec">${secH('Tu cuenta')}<div class="card pad vq-yo"><span class="eq-av xl" aria-hidden="true" data-no-tr>${esc(N.iniciales(VQ.nombre))}</span><div><b>${esc(VQ.nombre||'')}</b><span>Licencia <span data-no-tr>${VQ.demo?N.LIC_PRUEBA:N.fmtLic(VQ.lic||'')}</span></span><span>En el equipo de ${esc(VQ.finca||'')}${VQ.desde?` desde el ${ffc(VQ.desde)}`:''}</span></div></div></section>
   <section class="sec">${secH('Envío a tu jefe')}<div class="card pad eq-sinc"><div class="eq-sinc-h"><span class="mas-ic ${srv?'t-v':'t-y'}">${icono(srv?'rayo':'datos','i3')}</span><div><b>${VQ.demo?'Modo de prueba':srv?'Automático por internet':'Por archivo (WhatsApp)'}</b><span><span>${VQ.demo?'Nada sale de este teléfono':pend?pl(pend,'registro por enviar','registros por enviar'):'Todo enviado'}</span>${srv&&VQ.ult&&!VQ.demo?` · <span>${N.haceCuanto(VQ.ult)}</span>`:''}</span></div></div>
    ${VQ.demo?'':`<div class="acts">${srv?`<button type="button" class="btn" data-act="vqSinc">Enviar ahora</button>`:''}<button type="button" class="btn" data-act="vqEnviarArchivo">Mandar por archivo</button><button type="button" class="btn" data-act="vqRecibirArchivo">Abrir archivo del jefe</button></div><p class="hint">Los archivos del equipo terminan en .rumentis. En WhatsApp tócalo y elige Rumentis para abrirlo. Cada archivo se abre una sola vez.</p>`}</div></section>
   <section class="sec">${secH('Ajustes')}<div class="card rows">
    ${masFila(mico('paleta'),'Apariencia','Modo claro u oscuro y colores','href="#mas/apariencia"','t-v')}
    ${masFila(mico('ayuda'),'Idioma',window.I18N?(I18N.IDIOMAS.find(x=>x[0]===I18N.lang())||['',''])[1]:'','href="#mas/idioma"','t-a')}
    ${masFila(mico('info'),'Acerca de','Versión y privacidad','href="#mas/acerca"','t-a')}
    ${masFila(mico('aviso'),'Borrar datos de este teléfono','Si dejas el equipo o cambias de teléfono','data-act="vqBorrar"','t-r')}</div></section>
   <div class="firma">${typeof FIRMA!=='undefined'?FIRMA:''}<span>Vaquero · versión ${VERSION_APP}</span></div></main>`;
};
ACTS.vqIdioma=el=>{if(window.I18N)I18N.cambiar(el.dataset.l);};
// fuera de lo permitido: a Hoy. Sin activar: la pantalla de activación
for(const k of Object.keys(PAGES))if(!PERMITIDAS.has(k))PAGES[k]=()=>PAGES.hoy();
const _render=render;
render=window.render=function(){
  document.documentElement.classList.toggle('vq-sin',!activo());
  if(!activo()){const app=$('#app');document.body.dataset.pg='activar';pintarEn(app,aUnidad(pantallaActivar()));document.dispatchEvent(new Event('rumentis-pintado'));return;}
  return _render.apply(this,arguments);
};
// ni Rumi, ni recorridos, ni opciones del dueño
for(const k of ['rumi','tour','demo','borrarTodo','respaldo','importar','tutPagina'])ACTS[k]=()=>{};
if(typeof mostrarPaso==='function')mostrarPaso=function(){};   // el "siguiente paso" es de Rumi
// alimento: el mismo formulario, sin precios ni horarios (los pone el jefe)
FORMS.alimento=({n}={})=>{
  const C=calc();if(!C.act.length){toast('Tu jefe todavía no tiene lotes.');return;}
  const Ne=nEntregas();n=+n||C.sigEnt||1;const R=Object.entries(S.raciones).sort((a,b)=>byName(a[1],b[1]));
  const racSel=R.length?q('Ración',opts('racion',[{v:'_lote',t:'La de cada lote',s:'la que tiene asignada'}].concat(R.map(([k,r])=>({v:k,t:r.nombre}))),'_lote',{lo:true})):'';
  const blocks=C.act.map(x=>{const p=programado(x,n);return `<div data-l="${esc(x.id)}"><div class="fh"><b>${esc(x.l.nombre)}</b><span class="sm">${pl(x.cab,'cabeza','cabezas')}${x.racion&&S.raciones[x.racion]?', '+esc(S.raciones[x.racion].nombre):''}</span></div>
    <div class="unit"><input class="in" name="kg_${esc(x.id)}" inputmode="decimal" placeholder="unos ${nf(W(p))}" data-prog="${Math.round(W(p)/5)*5}" aria-label="Kilos para ${esc(x.l.nombre)}" autocomplete="off"><em>kg</em></div>
    <div class="lect" data-name="lect_${esc(x.id)}" role="group" aria-label="Cómo estaba el comedero">${LECT.map((t,i)=>`<button type="button" data-v="${i}" aria-pressed="false">${t}</button>`).join('')}</div><input type="hidden" name="lect_${esc(x.id)}" value=""></div>`;}).join('');
  const entSel=conHoras()?q('Entrega',`<select class="in" name="n">${[...Array(Ne).keys()].map(i=>`<option value="${i+1}"${i+1===n?' selected':''}>${esc(horaDe(i+1))}</option>`).join('')}</select>`):`<input type="hidden" name="n" value="${n}">`;
  const body=`<div class="two">${fecha()}${entSel}</div>${racSel}
   <button type="button" class="btn full ar-soft" data-act="llenarProg">${ico('check',2.4)}Servir lo de siempre a todos</button>
   <div class="sec"><div class="sec-h"><div><h2 style="font-size:17px">Kilos por lote</h2></div></div>
   <p class="hint" style="margin-top:-4px">Escribe lo que serviste a cada lote, o toca arriba para llenar todo con lo de siempre. Debajo, si quieres, marca cómo estaba el comedero.</p>
   <div class="card feed">${blocks}</div></div>`;
  openSheet(shHead(`Entrega ${esc(entDe(n))}`,'Alimento')+formWrap('alimento',body,foot('Guardar entrega','<b data-sum>0 kg</b><span>en total</span>')));
};
// solo sus propios registros que todavía no se mandan se pueden borrar
const _delItems=ACTS.delItems;
ACTS.delItems=el=>{const ids=(el.dataset.ids||'').split(',');const mios=new Set((VQ.cola||[]).filter(o=>o.k==='item+').map(o=>o.it.id));
  if(!ids.every(i=>mios.has(i))){toast('Eso ya le llegó a tu jefe: pídele que lo corrija.',3500);return;}
  // se quita de la cola: al jefe nunca le llega
  VQ.cola=(VQ.cola||[]).filter(o=>!(o.k==='item+'&&ids.includes(o.it.id)));guardarVQ();APLICANDO=1;try{for(const i of ids)removeItem(i);}finally{APLICANDO=0;}render();};

/* ---------- barra de abajo del vaquero ---------- */
const NAVV=[['hoy','Hoy'],['lotes','Lotes'],['registrar','Registrar'],['tareas','Tareas'],['mas','Más']];
function barra(){const nav=$('nav.bottom .in');if(!nav||nav.dataset.vq)return;nav.dataset.vq='1';
  nav.innerHTML=NAVV.map(([k,t])=>k==='registrar'?`<a href="#registrar" data-nav="registrar" class="reg"><span class="sq">${ico('registrar',2.6)}</span><span>${t}</span></a>`:`<a href="#${k}" data-nav="${k}">${ico(k==='tareas'?'tareas':k)}<span>${t}</span></a>`).join('');}
barra();
document.addEventListener('rumentis-pintado',()=>{const r=route();const cur=r.p==='lote'?'lotes':r.p;$$('nav.bottom [data-nav]').forEach(a=>{if(a.dataset.nav===cur)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');});});

window.Vaquero={VQ:()=>VQ,alRecibir,sincronizar,procesar,procesarVarios,aplicarEstado,activar,activarSinFicha,adoptarFicha,modoPrueba,sobreOps,sobreAlta};
if(!Object.keys(S.lotes).length&&activo()&&!VQ.demo)S.config.finca=VQ.finca||S.config.finca;
document.documentElement.classList.add('vq-listo');
render();
if(VQ.estado==='pendiente'||activo())setTimeout(()=>sincronizar(),1500);
})();
