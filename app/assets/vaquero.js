/* Rumentis Equipo: la app del personal (se arma con los mismos archivos; config.js dice app:'vaquero').
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
// el nombre de esta app en el idioma del teléfono: Rumentis Equipo, Team o Equipe
const NOMBRE_APP=()=>({en:'Team',pt:'Equipe'})[window.I18N&&I18N.lang?I18N.lang():'es']||'Equipo';
// con internet (servidor del equipo al alcance) todo viaja solo; sin él, por archivo
const enLinea=()=>!!servidor()&&!VQ.err&&navigator.onLine!==false;

/* ---------- operaciones: lo que anota el vaquero ---------- */
let APLICANDO=0;
function nuevaOp(o){VQ.seq=(+VQ.seq||0)+1;const op={...o,seq:VQ.seq,ts:Date.now()};VQ.cola=(VQ.cola||[]).concat(op);guardarVQ();enviarPronto();return op;}
const iguales=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
function registrarOps(col,id,data){
  if(col==='diario'){
    const antes=new Map(((S.diario[id]||{}).items||[]).map(i=>[i.id,i])),despues=new Map(((data||{}).items||[]).map(i=>[i.id,i]));
    const nuevos=[];
    for(const [k,it] of despues){if(!antes.has(k)){nuevaOp({k:'item+',it});nuevos.push(it);}else if(!iguales(antes.get(k),it))nuevaOp({k:'item~',it});}
    for(const k of antes.keys())if(!despues.has(k))nuevaOp({k:'item-',id:k});
    if(nuevos.length)setTimeout(()=>autoTareas(nuevos),0);
  }else if(col==='lotes'){
    const l=S.lotes[id];if(!l||!data)return;const campos={};
    for(const k of ['racion','animales','estado','fechaCierre'])if(!iguales(l[k],data[k]))campos[k]=data[k];
    if(Object.keys(campos).length)nuevaOp({k:'lote~',id,campos});
  }else if(col==='ajustes'&&data){
    const h0=S.config.hechas||{},h1=data.hechas||{};
    // cada tarea se termina con su foto de evidencia (FOTO_TAREA la pone vqHecha justo antes de marcarla)
    const foto=FOTO_TAREA||'',nota=NOTA_TAREA||'';
    for(const [k,f] of Object.entries(h1))if(h0[k]!==f){const t=(window.Agenda?Agenda.tareas():[]).find(x=>x.key===k);nuevaOp({k:'hecha',key:k,f,txt:t?t.t:'',foto,nota});logHecho(t?t.t:'',f,{foto,key:k,nota});}
    // tareas asignadas: la de una vez al marcarla; las que se repiten, cada día que se hacen
    const t0=new Map((S.config.tareas||[]).map(t=>[t.id,t]));
    for(const t of data.tareas||[]){const a=t0.get(t.id);if(!a)continue;
      if((t.rep||'una')==='una'){if(t.hecho&&!a.hecho){nuevaOp({k:'tarea',id:t.id,f:hoy(),foto,nota});logHecho(t.t,hoy(),{foto,id:t.id,nota});}}
      else{const ya=new Set((a.hechos||[]).map(x=>x.f));for(const h of t.hechos||[])if(!ya.has(h.f)){nuevaOp({k:'tarea',id:t.id,f:h.f,foto,nota});logHecho(t.t,h.f,{foto,id:t.id,nota});}}}
  }
}
let FOTO_TAREA='',NOTA_TAREA='';
/* Tareas ligadas a una acción (entregar alimento, pesar, sanidad; en un lote o en cualquiera): al anotar esa acción la
   tarea queda lista y solo falta su foto de evidencia para terminarla. */
function autoTareas(items){
  if(!activo())return;const L=[];
  for(const t of S.config.tareas||[]){
    const a=t.acc;if(!a||!a.k)continue;
    const it=items.find(i=>i.tipo===a.k&&(!a.lote||a.lote===i.lote)&&(!t.f||i.f>=t.f));if(!it)continue;
    const p=Agenda.progreso(t,it.f);if(p.hoy||p.completa)continue;
    VQ.listas={...(VQ.listas||{}),[t.id]:it.f};L.push(t);
  }
  if(!L.length)return;guardarVQ();
  const t=L[0];
  setTimeout(()=>{EVI={k:'propia:'+t.id,p:t.id,t:t.t,blob:null,url:'',nota:'',anotada:true};abrirEvidencia();},450);
}
// lo que hizo cada día (para el resumen del reporte): tarea, foto y a qué tarea corresponde
// (si la administración pidió repetirla, la nueva reemplaza a la anterior de ese día)
function logHecho(t,f,extra){f=f||hoy();const k=extra&&(extra.id||extra.key);
  VQ.hechosLog=(VQ.hechosLog||[]).filter(x=>x.f>=addDias(hoy(),-14)&&!(k&&x.f===f&&(x.id||x.key)===k)).concat({f,t:String(t||'Una tarea'),...(extra||{})});}
/* Terminar una tarea: una hoja con la foto de evidencia (se toma con la cámara, se puede repetir), un comentario
   opcional y el botón Enviar, que se pone en verde cuando ya hay foto. Sin foto la tarea sigue pendiente. */
let EVI=null;
function abrirEvidencia(){
  const x=EVI;if(!x)return;const tt=x.p?(S.config.tareas||[]).find(z=>z.id===x.p):null;
  openSheet(shHead('Terminar tarea',esc(x.t))+`<div class="sh-body vq-evi">
    ${x.anotada?`<p class="vq-evi-ok">${ico('check',2.4)}<span>Ya anotaste lo que pide esta tarea. Falta la foto.</span></p>`:''}
    ${tt&&tt.nota?`<div class="vq-ind" data-no-tr>${esc(tt.nota)}</div>`:''}
    <button type="button" class="vq-evi-foto${x.url?' con':''}" data-act="vqEviFoto" aria-label="${x.url?'Tomar otra foto':'Tomar foto de evidencia'}">${x.url?`<img src="${x.url}" alt=""><span class="vq-evi-otra">${icono('camara','i3')}Tomar otra</span>`:`<span class="vq-evi-ic">${icono('camara','i3')}</span><b>Tomar foto de evidencia</b><span>Obligatoria para terminar la tarea</span>`}</button>
    ${q('Comentario para la administración',`<textarea class="in" id="vqEviNota" rows="2" maxlength="300" placeholder="Opcional">${esc(x.nota||'')}</textarea>`)}
  </div><div class="sh-foot"><button type="button" class="btn" data-act="cerrar" style="flex:1">Cancelar</button><button type="button" class="btn vq-enviar${x.blob?' listo':''}" data-act="vqEviEnviar"${x.blob?'':' disabled'} style="flex:1.6">${ico('send')}Enviar</button></div>`);
}
ACTS.vqHecha=el=>{
  if(!window.Fotos){toast('Este teléfono no puede tomar fotos.',3500);return;}
  EVI={k:el.dataset.k||'',p:el.dataset.p||'',t:el.dataset.t||'Tarea',blob:null,url:'',nota:''};abrirEvidencia();
};
ACTS.vqEviFoto=async()=>{const x=EVI;if(!x)return;const n=$('#vqEviNota');if(n)x.nota=n.value;
  const b=await Fotos.tomar('Evidencia · '+x.t,{soloCamara:true});
  if(b){if(x.url)URL.revokeObjectURL(x.url);x.blob=b;x.url=URL.createObjectURL(b);}
  abrirEvidencia();};
ACTS.vqEviEnviar=async()=>{
  const x=EVI;if(!x)return;if(!x.blob){toast('Falta la foto de evidencia.',3000);return;}
  const n=$('#vqEviNota');x.nota=String(n?n.value:x.nota||'').trim().slice(0,300);
  const fid=N.b64u(N.azar(12));
  try{await Fotos.guardar('eq-'+fid,x.blob);}catch(e){toast('No se pudo guardar la foto. Intenta de nuevo.',3500);return;}
  VQ.fotos={...(VQ.fotos||{}),[fid]:{ts:Date.now(),t:x.t,env:0}};
  FOTO_TAREA=fid;NOTA_TAREA=x.nota;try{ACTS.agHecha({dataset:{k:x.k||('propia:'+x.p),p:x.p}});}finally{FOTO_TAREA='';NOTA_TAREA='';}
  if(x.p){const L={...(VQ.listas||{})};delete L[x.p];VQ.listas=L;VQ.nuevas=(VQ.nuevas||[]).filter(z=>z!==x.p);}
  if(x.url)URL.revokeObjectURL(x.url);EVI=null;
  guardarVQ();closeSheet();enviarPronto(300);render();toast(enLinea()?'Tarea enviada a la administración':'Tarea terminada: va en tu reporte del día',3000);
};
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
  else if(op.k==='tarea'){S.config.tareas=(S.config.tareas||[]).map(t=>t.id!==op.id?t:(t.rep||'una')==='una'?{...t,hecho:true}
    :(t.hechos||[]).some(x=>x.f===op.f)?t:{...t,hechos:(t.hechos||[]).concat({f:op.f||hoy(),v:VQ.vid,n:VQ.nombre})});}
}
function aplicarEstado(est){
  APLICANDO=1;
  try{
    const yo=(est.vaqueros||{})[VQ.vid];
    if(yo&&yo.estado==='baja'&&(!yo.lic||N.normLic(yo.lic)===N.normLic(VQ.lic))){darmeDeBaja();return;}
    if(yo)VQ.permisos=yo.permisos||VQ.permisos;
    VQ.nombres=Object.values(est.vaqueros||{}).map(v=>v.n).filter(Boolean);   // los compañeros: sus nombres no se traducen
    S.lotes=est.lotes||{};S.raciones=est.raciones||{};
    const di={};for(const it of est.items||[]){const k=String(it.f).slice(0,7);(di[k]=di[k]||{items:[]}).items.push(it);}S.diario=di;
    const c=est.cfg||{};
    const antes=new Set((S.config.tareas||[]).map(t=>t.id));
    S.config={...DEF_CFG,...c,finca:c.finca||VQ.finca||DEF_CFG.finca,tareas:(c.tareas||[]).filter(t=>t.para==='todos'||t.para===VQ.vid),hechas:c.hechas||{},repetirAg:c.repetirAg||{}};
    // tareas nuevas: se marcan hasta que las vea
    const nuevas=S.config.tareas.filter(t=>!antes.has(t.id)).map(t=>t.id);if(nuevas.length&&VQ.estadoTs)VQ.nuevas=[...new Set((VQ.nuevas||[]).concat(nuevas))];
    const ack=(est.acks||{})[VQ.vid]||0;VQ.cola=(VQ.cola||[]).filter(o=>o.seq>ack);VQ.ack=ack;
    if(c.horaReporte!=null)VQ.horaRep=+c.horaReporte;
    const rv=(est.rev||{})[VQ.vid];if(rv&&(!VQ.rev||rv.ts>VQ.rev.ts))VQ.rev=rv;
    for(const op of VQ.cola)aplicarLocal(op);
    VQ.estadoTs=est.ts||Date.now();VQ.finca=S.config.finca;guardarVQ();
    ver++;lsSave();
  }finally{APLICANDO=0;}
  ajustarAviso();render();
}

/* ---------- mensajes del jefe ---------- */
async function procesar(s){
  if(!s||s.e!==VQ.e||(s.para!==VQ.vid&&s.para!=='todos'))return 0;
  const vis=VQ.vistos||[];if(vis.includes(s.id))return 0;
  const jefe=VQ.ficha&&VQ.ficha.j,caja={pub:VQ.ficha&&VQ.ficha.k,sec:VQ.yo&&VQ.yo.caja.sec};let hecho=1;
  // bienvenida, rechazo y baja son de una licencia: los de una licencia anterior no cuentan
  if(/^(bienvenida|rechazo|baja)$/.test(s.t)){const c=await N.abrir(s,{caja,firmaPub:jefe});
    if(c&&c.lic&&N.normLic(c.lic)!==N.normLic(VQ.lic)){VQ.vistos=(VQ.vistos||[]).concat(s.id).slice(-400);guardarVQ();return 0;}}
  if(s.t==='bienvenida'){const c=await N.abrir(s,{caja,firmaPub:jefe});if(!c)return 0;
    if(c.prueba&&!CFG.prueba){VQ.estado='rechazada';VQ.motivo='Esa licencia es de la app de prueba. Pide a la administración una licencia de la app de Google Play.';}
    else if(c.compra&&N.compraReal(c.compra.json,c.compra.firma)===false){VQ.estado='rechazada';VQ.motivo='Esa licencia no salió de una compra válida.';}
    else{VQ.estado='activo';VQ.vid=c.vid||VQ.vid;VQ.clave=c.clave;VQ.gen=c.gen;VQ.claves={...(VQ.claves||{}),[c.gen]:c.clave};VQ.permisos=c.permisos||{};VQ.finca=c.finca||VQ.finca;VQ.desde=hoy();if(c.horaReporte!=null)VQ.horaRep=+c.horaReporte;ver++;ajustarAviso();
      toast(`¡Listo, ${primerNombre()}! Ya estás en el equipo de ${VQ.finca||'la finca'}.`,4500);}}
  else if(s.t==='rechazo'){const c=await N.abrir(s,{caja,firmaPub:jefe});if(!c)return 0;if(VQ.estado!=='activo'){VQ.estado='rechazada';VQ.motivo=c.motivo||'La administración no aceptó la licencia.';}}
  else if(s.t==='clave'){const c=await N.abrir(s,{caja,firmaPub:jefe});if(!c)return 0;VQ.claves={...(VQ.claves||{}),[c.gen]:c.clave};if(c.gen>(+VQ.gen||0)){VQ.gen=c.gen;VQ.clave=c.clave;}}
  else if(s.t==='baja'){const c=await N.abrir(s,{caja,firmaPub:jefe});if(!c)return 0;darmeDeBaja();}
  // invitación a la cámara enlazada (beta): vale 10 minutos
  else if(s.t==='cam'){const c=await N.abrir(s,{caja,firmaPub:jefe});if(!c)return 0;if(Date.now()-(+c.ts||0)<10*60e3&&window.Enlace){VQ.camInv={t:c.t,rol:c.rol,ts:+c.ts};toast('La administración te invita a la cámara para pesaje.',4500);}}
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
  if(fi.p&&!CFG.prueba){VQ.estado='rechazada';VQ.motivo='Esa licencia es de la app de prueba. Pide a la administración una licencia de la app de Google Play.';guardarVQ();render();return false;}
  VQ.ficha=fi;VQ.e=fi.e;VQ.finca=fi.fn||VQ.finca;guardarVQ();ver++;return true;
}
/* Licencia revocada: la sesión se cierra y los datos de la finca se borran de este teléfono (registros, fotos y llaves).
   Queda solo el aviso; con una licencia nueva se empieza de cero, con llaves nuevas. */
async function darmeDeBaja(){
  const finca=VQ.finca||'';
  try{if(window.Fotos)for(const k of await Fotos.ids())if(/^eq-/.test(k))await Fotos.borrar(k);}catch(e){}
  try{localStorage.removeItem(LS_KEY);}catch(e){}
  VQ={estado:'baja',fincaAnterior:finca,bajaTs:Date.now()};guardarVQ();
  location.hash='#hoy';location.reload();
}
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
  VQ.nombre=nombre;VQ.lic=lic;VQ.ficha=ficha;VQ.e=ficha.e;VQ.vid=N.idDe(VQ.yo.firma.pub);VQ.finca=ficha.fn||'';VQ.estado='pendiente';VQ.motivo='';VQ.altaEnviada=0;VQ.cursor=0;ver++;
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
  if(nombre.length<3||!/\s/.test(nombre)){err.textContent=aUnidad('Escribe tu nombre y apellido.');return;}
  if(N.esLicPrueba(txt))return modoPrueba(nombre);
  const enl=N.leerEnlace(txt)||N.leerEnlace(VQ.enlace||'');let lic=enl?enl.lic:N.normLic(txt);
  if(enl&&N.normLic(txt)&&N.licValida(N.normLic(txt))&&N.normLic(txt)!==enl.lic)lic=N.normLic(txt);
  if(!N.licValida(lic)){err.textContent=aUnidad('Revisa la licencia: son 20 letras y números, en grupos de 4 después de RV.');return;}
  const b=f.querySelector('.btn.pri');b.disabled=true;b.textContent=aUnidad('Buscando tu licencia…');
  try{
    let ficha=enl&&enl.lic===lic?enl.ficha:null;
    if(!ficha){try{ficha=await buscarFicha(lic);}catch(e){ficha=null;}}   // sin internet: se sigue por archivo
    if(ficha===false){err.textContent=aUnidad('No encuentro esa licencia. Revísala con la administración.');return;}
    if(!ficha)return activarSinFicha(nombre,lic);
    if(!(await N.fichaValida(ficha))){err.textContent=aUnidad('Esa licencia no es válida.');return;}
    if(ficha.p&&!CFG.prueba){err.textContent=aUnidad('Esa licencia es de la app de prueba. Pide a la administración una licencia de la app de Google Play.');return;}
    await activar(nombre,lic,ficha);
  }finally{if(b.isConnected){b.disabled=false;b.textContent=aUnidad('Activar');}}
};
ACTS.vqEscanear=async()=>{const t=await N.escanearQR();if(!t)return;const enl=N.leerEnlace(t);
  if(!enl){toast('Ese QR no es una licencia de Rumentis.',3500);return;}VQ.enlace=t;guardarVQ();const f=$('#vqForm');if(f){f.elements.lic.value=N.fmtLic(enl.lic);f.elements.lic.dispatchEvent(new Event('input'));}
  toast(`Licencia de ${enl.ficha.fn||'la finca'} lista`,3000);};
ACTS.vqPegar=async()=>{try{const t=await navigator.clipboard.readText();const f=$('#vqForm');if(!f||!t)return;const enl=N.leerEnlace(t);
  if(enl){VQ.enlace=t;guardarVQ();f.elements.lic.value=N.fmtLic(enl.lic);}else f.elements.lic.value=t.trim();}catch(e){toast('Mantén presionado el campo y elige Pegar.',3500);}};
ACTS.vqOtra=()=>{VQ.estado='nuevo';VQ.alta=null;VQ.ficha=null;VQ.enlace='';guardarVQ();render();};
ACTS.vqRevisar=async()=>{if(!servidor()){toast('Pide a la administración tu archivo de acceso.',4000);return;}toast('Revisando…');await sincronizar();if(!activo())toast('La administración todavía no confirma.',3000);};
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
/* Lo que falta mandar a la administración: los registros (si cambiaron), las fotos de evidencia (hasta 3 por
   solicitud) y el perfil. Cada uno trae marca() para anotarlo como entregado. */
function hayPendientes(){
  return !!(((VQ.cola||[]).length&&VQ.enviado!==VQ.seq)||Object.values(VQ.fotos||{}).some(f=>!f.env)||(VQ.perfil&&!VQ.perfil.env));
}
async function sobreFoto(fid){
  const f=(VQ.fotos||{})[fid]||{},jpg=await N.fotoB64('eq-'+fid);if(!jpg)return null;
  return N.sellar('foto',{e:VQ.e,de:VQ.vid,para:'jefe',carga:{fid,jpg,t:f.t||'',ts:f.ts||Date.now()},clave:VQ.clave,g:VQ.gen,firmaSec:VQ.yo.firma.sec});
}
async function sobrePerfil(){
  const p=VQ.perfil;if(!p)return null;const jpg=p.foto?await N.fotoB64('eq-pf-yo',480):'';
  return N.sellar('perfil',{e:VQ.e,de:VQ.vid,para:'jefe',carga:{cargo:p.cargo||'',tel:p.tel||'',jpg,ts:p.ts||Date.now()},clave:VQ.clave,g:VQ.gen,firmaSec:VQ.yo.firma.sec});
}
async function pendientes(){
  const L=[];if(!activo()||VQ.demo)return L;
  if((VQ.cola||[]).length&&(VQ.enviado!==VQ.seq||Date.now()-(VQ.enviadoTs||0)>10*60e3)){const seq=VQ.seq,s=await sobreOps();
    if(s)L.push({s,marca:()=>{VQ.enviado=seq;VQ.enviadoTs=Date.now();}});}
  let n=0;for(const [fid,f] of Object.entries(VQ.fotos||{})){if(f.env)continue;if(n>=3)break;
    const s=await sobreFoto(fid);const marca=()=>{VQ.fotos={...VQ.fotos,[fid]:{...VQ.fotos[fid],env:1}};};
    if(!s){marca();continue;}L.push({s,marca});n++;}
  if(VQ.perfil&&!VQ.perfil.env){const s=await sobrePerfil();if(s)L.push({s,marca:()=>{VQ.perfil={...VQ.perfil,env:1};}});}
  for(const x of (VQ.salidaCam||[]))L.push({s:x,marca:()=>{VQ.salidaCam=(VQ.salidaCam||[]).filter(z=>z.id!==x.id);}});
  return L;
}
async function sincronizar(){
  if(ocupado)return ocupado;const base=servidor();if(!base||VQ.demo||!VQ.yo||!VQ.e||VQ.estado==='nuevo'||VQ.estado==='baja')return null;
  ocupado=(async()=>{let n=0;
    try{
      const yo=VQ.yo.firma;
      if(VQ.estado==='pendiente'&&!VQ.altaEnviada&&VQ.alta){await N.pedir(base,'/v1/alta',{h:N.hashLic(VQ.lic),sobre:VQ.alta});VQ.altaEnviada=Date.now();guardarVQ();}
      // una solicitud por vuelta: manda registros, fotos y perfil pendientes y trae lo nuevo
      for(let i=0;i<10;i++){
        const env=activo()?await pendientes():[];let r;
        try{r=await N.pedir(base,'/v1/sync',{e:VQ.e,quien:VQ.vid,firma:yo.pub,desde:VQ.cursor||0,sobres:env.map(x=>x.s)},yo.sec);}
        catch(e){if(e.status!==403||!env.length)throw e;   // recién aceptado: el servidor todavía no lo tiene como miembro
          r=await N.pedir(base,'/v1/sync',{e:VQ.e,quien:VQ.vid,firma:yo.pub,desde:VQ.cursor||0},yo.sec);env.length=0;}
        const ok=new Set(r.guardados||[]),mal=new Set((r.rechazados||[]).map(x=>x.id));
        for(const x of env)if(ok.has(x.s.id)||mal.has(x.s.id))x.marca();
        n+=await procesarVarios(r.sobres||[]);VQ.cursor=r.hasta||VQ.cursor;guardarVQ();
        if(!r.mas&&!(env.length&&hayPendientes()))break;
      }
      VQ.ult=Date.now();VQ.err='';guardarVQ();T.abrir();
    }catch(err){VQ.err=String(err&&err.message||err);guardarVQ();}
    finally{ocupado=null;updateSync();if(n||!activo()||/^#(mas|hoy)?$/.test(location.hash.split('/')[0]))render();}
    return n;})();
  return ocupado;
}
// en vivo: el servidor avisa en cuanto el jefe manda algo (el estado, una tarea, la bienvenida)
const T=N.timbre(async()=>{const base=servidor();if(!base||VQ.demo||!VQ.yo||!VQ.e||VQ.estado==='nuevo'||VQ.estado==='baja'||VQ.estado==='rechazada')return null;
  return {base,cuerpo:{e:VQ.e,quien:VQ.vid,firma:VQ.yo.firma.pub},firmaSec:VQ.yo.firma.sec};},()=>enviarPronto(150),()=>updateSync());
// sin conexión en vivo, cada minuto y medio; con ella, solo cada 5 minutos por si acaso
setInterval(()=>{if(!document.hidden&&!(T.vivo()&&Date.now()-(VQ.ult||0)<5*60e3))sincronizar();},VQ.estado==='pendiente'?20000:90000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden)sincronizar();});
addEventListener('online',()=>{sincronizar();render();});addEventListener('offline',()=>render());
ACTS.vqSinc=async()=>{if(!servidor()){FORMS.vqReporte();return;}toast('Sincronizando…');const n=await sincronizar();
  toast(VQ.err?'No se pudo conectar. Revisa tu internet.':n?'Listo: tu finca está al día':'Todo al día',3200);};
const miNombre=()=>String(VQ.nombre||'personal').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'');
// pendiente: la solicitud de acceso; activo: el reporte del día
ACTS.vqEnviarArchivo=async()=>{
  if(activo()){FORMS.vqReporte();return;}
  if(VQ.estado!=='pendiente'||!VQ.alta)return;
  N.compartirArchivo(N.nombreArchivo(`solicitud-${miNombre()}`),await N.armarArchivo([VQ.alta],VQ.vid),aUnidad('Solicitud de acceso'));
};

/* ---------- reporte del día ----------
   Al terminar la jornada (a la hora que fija la administración) el colaborador envía su reporte: lo que registró, las
   tareas del día hechas y pendientes, cómo van las de la semana y sus novedades. Va cifrado en un archivo .rumentis por
   WhatsApp o correo; la administración lo revisa en Rumentis y lo registra en su finca. Lo que la administración
   todavía no registra vuelve a ir en el siguiente reporte (sin duplicarse: cada registro lleva su número). */
const horaRep=()=>{const h=+VQ.horaRep;return VQ.horaRep!=null&&h>=0&&h<=23?h:17;};
const repHoy=()=>{const r=VQ.ultRep;return r&&r.fecha===hoy()?r:null;};
const nuevosDesdeReporte=()=>{const s=(VQ.ultRep&&VQ.ultRep.seq)||0;return (VQ.cola||[]).filter(o=>o.seq>s).length;};
const hhmm=ts=>new Date(ts).toTimeString().slice(0,5);
function resumenDia(H){
  const todas=misTareas().filter(t=>t.f<=H),abiertas=new Set(todas.map(t=>t.propia||t.key).filter(Boolean)),pendT=todas.filter(t=>t.propia);
  // si la administración pidió repetir una tarea, lo que se hizo antes ya no cuenta como terminado
  const hechas=(VQ.hechosLog||[]).filter(x=>x.f===H&&!abiertas.has(x.id||x.key)).map(x=>({t:x.t,ok:1,foto:x.foto||'',id:x.id||'',key:x.key||'',nota:x.nota||''}));
  const pend=pendT.map(t=>({t:t.t,ok:0,id:t.propia||'',key:t.propia?'':t.key}));
  const regs={};for(const i of misRegistros(H))regs[i.tipo]=(regs[i.tipo]||0)+1;
  const sem=(S.config.tareas||[]).filter(t=>/^s\d$/.test(t.rep||'')).map(t=>{const p=Agenda.progreso(t,H);return {t:t.t,n:p.n,meta:p.meta};});
  return {fecha:H,tareas:hechas.concat(pend),regs,sem};
}
async function sobreReporte(nota){
  const res=resumenDia(hoy());
  return N.sellar('reporte',{e:VQ.e,de:VQ.vid,para:'jefe',carga:{rid:N.b64u(N.azar(9)),ts:Date.now(),res,nota:String(nota||'').slice(0,1500),ops:VQ.cola||[]},
    clave:VQ.clave,g:VQ.gen,firmaSec:VQ.yo.firma.sec});
}
const filaRT=t=>`<div class="row vq-rt"><span class="vq-ck${t.ok?' ok':''}" aria-hidden="true">${t.ok?ico('check',2.6):''}</span><div class="tx"><b>${esc(t.t)}</b><span>${t.ok?'Terminada':'Pendiente'}</span></div></div>`;
const notaHoy=()=>VQ.notaDia&&VQ.notaDia.f===hoy()?VQ.notaDia.txt||'':'';
/* Novedades del día: con internet se guardan y van en el reporte (si ya salió, se manda actualizado); sin internet,
   la hoja muestra lo que lleva el reporte y lo envía como archivo. */
FORMS.vqReporte=()=>{
  if(VQ.demo){toast('En el modo de prueba el reporte no sale de este teléfono.',3500);return;}
  if(!activo())return;
  const H=hoy(),r=resumenDia(H),hechas=r.tareas.filter(t=>t.ok&&t.id).length,tot=r.tareas.filter(t=>t.id).length,nregs=Object.values(r.regs).reduce((a,b)=>a+b,0);
  const nota=q('Novedades del día',`<textarea class="in" name="nota" rows="3" maxlength="1500" placeholder="Un animal enfermo, un bebedero dañado, algo que haga falta…">${esc(notaHoy())}</textarea>`);
  if(enLinea()){openSheet(shHead('Novedades del día',ffc(H))+formWrap('vqReporte',nota+`<p class="hint">Van en tu reporte de hoy${repHoy()?'; como ya salió, se envía de nuevo con ellas':` de las ${horaRep()}:00`}.</p>`,foot('Guardar')));return;}
  const body=`<div class="kpis vq-k"><div class="kpi"><b>${hechas} de ${tot}</b><span>tareas asignadas</span></div><div class="kpi"><b>${nregs}</b><span>registros hoy</span></div></div>
    ${tot?`<p class="rh">Tareas de hoy</p><div class="card rows">${r.tareas.map(filaRT).join('')}</div>`:''}
    ${nota}
    <p class="hint">Sin internet, el reporte se envía como archivo: elige WhatsApp o correo y mándalo a la administración, que lo abre en Rumentis.</p>`;
  openSheet(shHead('Reporte del día',ffc(H))+formWrap('vqReporte',body,foot('Enviar por WhatsApp o correo')));
};
// el reporte se arma solo con lo del día: enviarlo es un toque (las novedades son opcionales)
// con internet (servidor del equipo) sale solo; sin señal, como archivo por WhatsApp o correo
async function enviarReporte(nota,{auto=false,act=false}={}){
  if(VQ.demo){if(!auto)toast('En el modo de prueba el reporte no sale de este teléfono.',3500);return false;}
  if(!activo())return false;
  const base=servidor();
  if(base){try{await sincronizar();}catch(e){}}   // primero las fotos y los registros pendientes
  // el reporte actualizado conserva las novedades que ya se habían escrito hoy
  if(!nota)nota=notaHoy()||(repHoy()&&repHoy().nota)||'';
  const s=await sobreReporte(nota||'');
  const listo=()=>{VQ.ultRep={fecha:hoy(),ts:Date.now(),seq:+VQ.seq||0,n:(VQ.cola||[]).length,red:!!base,nota:String(nota||'').slice(0,1500)};VQ.reportes=[VQ.ultRep].concat(VQ.reportes||[]).slice(0,30);guardarVQ();ver++;closeSheet();render();};
  if(base&&navigator.onLine!==false){try{await N.pedir(base,'/v1/enviar',{e:VQ.e,quien:VQ.vid,firma:VQ.yo.firma.pub,sobres:[s]},VQ.yo.firma.sec);VQ.err='';listo();
      if(!act)toast(auto?'Reporte del día enviado':'Reporte enviado a la administración',3800);return true;}catch(e){VQ.err=String(e&&e.message||e);guardarVQ();if(auto||act){render();return false;}}}
  if(auto)return false;
  // por archivo van también las fotos de evidencia y el perfil que falten
  const fids=new Set([...(VQ.cola||[]).map(o=>o.foto).filter(Boolean),...Object.entries(VQ.fotos||{}).filter(([k,f])=>!f.env).map(([k])=>k)]);
  const extra=[];for(const fid of fids)extra.push(await sobreFoto(fid));if(VQ.perfil)extra.push(await sobrePerfil());
  N.compartirArchivo(N.nombreArchivo(`reporte-${miNombre()}-${hoy()}`),await N.armarArchivo([s,...extra.filter(Boolean)],VQ.vid),aUnidad('Reporte del día'));
  listo();toast('Elige WhatsApp o correo y envíalo a la administración.',4000);return true;
}
/* A la hora del reporte, con internet, se envía solo (si falla, se reintenta cada 10 minutos). Si después de enviarlo
   anota algo o termina otra tarea, la administración recibe el reporte actualizado (como mucho cada 10 minutos). */
let autoT=0;
function autoReporte(){
  if(!servidor()||!activo()||VQ.demo||document.hidden)return;
  if(new Date().getHours()<horaRep()||Date.now()-autoT<10*60e3)return;
  const r=repHoy();if(r&&!((+VQ.seq||0)>(+r.seq||0)&&Date.now()-r.ts>=10*60e3))return;
  // sin nada hecho hoy (ni registros, ni tareas terminadas, ni novedades) no sale un reporte vacío
  if(!r){const res=resumenDia(hoy());if(!res.tareas.some(t=>t.ok)&&!Object.values(res.regs).some(n=>n>0)&&!notaHoy())return;}
  autoT=Date.now();enviarReporte('',{auto:true,act:!!r}).then(ok=>{if(ok)autoT=0;},()=>{});
}
setInterval(autoReporte,60000);document.addEventListener('visibilitychange',()=>setTimeout(autoReporte,1500));setTimeout(autoReporte,4000);
ACTS.vqEnviarRep=()=>enviarReporte('');
SAVE.vqReporte=async f=>{const txt=String((f.elements.nota||{}).value||'').trim().slice(0,1500);VQ.notaDia={f:hoy(),txt};guardarVQ();
  if(enLinea()){closeSheet();if(repHoy()){const ok=await enviarReporte(txt,{act:true});toast(ok?'Novedades enviadas en tu reporte':'Novedades guardadas',3000);}else toast(`Novedades guardadas: van en tu reporte de las ${horaRep()}:00`,3500);render();return;}
  return enviarReporte(txt);};
// al tocar el aviso de la hora del reporte, la app se abre y lo deja listo para enviar
function desdeAviso(){if(location.hash==='#hoy/enviar'){history.replaceState(null,'','#hoy');render();setTimeout(()=>enviarReporte(''),500);}}
addEventListener('hashchange',desdeAviso);setTimeout(desdeAviso,900);
// el aviso en el teléfono a la hora del reporte (los próximos 7 días, por si no abre la app)
function ajustarAviso(){
  const A=window.Android;if(!A||!A.avisosActivar||VQ.demo||!activo())return;
  const h=horaRep();if(VQ.avisoHora===h)return;
  try{A.avisosActivar(true,h);VQ.avisoHora=h;guardarVQ();}catch(e){}
}
window.colaAvisosPropia=()=>{
  if(!activo()||VQ.demo)return [];const H=hoy(),L=[];
  for(let i=0;i<7;i++){const d=addDias(H,i);if(i===0&&repHoy()&&!nuevosDesdeReporte())continue;
    L.push(servidor()?{id:'rep:'+d,f:d,hasta:d,t:'Hora de tu reporte del día',x:'Toca para enviarlo a la administración.',p:1,ir:'#hoy/enviar'}
      :{id:'rep:'+d,f:d,hasta:d,t:'Tu reporte del día está listo',x:'Toca aquí y elige WhatsApp o correo para enviarlo a la administración.',p:1,ir:'#hoy/enviar'});}
  return L;
};
/* Tu reporte de hoy: qué lleva y cuándo sale. Con internet no hay botón de enviar (sale solo a la hora fijada);
   sin internet, el botón para mandarlo como archivo, con su explicación. */
function tarjetaReporte(){
  if(VQ.demo)return '';
  const H=hoy(),r=repHoy(),hr=horaRep(),tarde=new Date().getHours()>=hr,red=enLinea();
  const res=resumenDia(H),A=res.tareas.filter(t=>t.id),hechas=A.filter(t=>t.ok).length,tot=A.length,nregs=Object.values(res.regs).reduce((a,b)=>a+b,0),fotos=res.tareas.filter(t=>t.ok&&t.foto).length;
  const cambios=r&&(+VQ.seq||0)>(+r.seq||0);
  let tono='a',tit='Tu reporte de hoy',sub=`Se envía a las ${hr}:00`;
  if(r&&!cambios){tono='v';sub=`Enviado a las ${hhmm(r.ts)}`;}
  else if(r){tono='y';sub=`Enviado a las ${hhmm(r.ts)} · hay registros nuevos`;}
  else if(tarde&&!red){tono='r';sub=`Pendiente desde las ${hr}:00`;}
  const rv=VQ.rev&&VQ.rev.fecha>=addDias(H,-3)?VQ.rev:null;
  const offline=!red?`<div class="vq-off">${ico('aviso',2)}<p><b>Sin internet.</b> Envía el reporte como archivo por WhatsApp o correo; la administración lo abre en Rumentis.</p></div>
    <button type="button" class="btn${tono==='a'?'':' pri'} full" data-act="f" data-f="vqReporte">${ico('send')}${r?'Enviar de nuevo':'Enviar por WhatsApp o correo'}</button>`:'';
  return `<section class="sec"><div class="card pad vq-rep vq-rep-${tono}"><div class="vq-rep-h"><span class="mas-ic t-${tono}">${icono('recibo','i3')}</span><div><b>${tit}</b><span>${sub}</span></div></div>
    <div class="vq-rep-n"><div><b>${hechas}/${tot}</b><span>tareas asignadas</span></div><div><b>${nregs}</b><span>registros</span></div><div><b>${fotos}</b><span>fotos</span></div></div>
    ${offline}
    <button type="button" class="lnk vq-rep-l" data-act="f" data-f="vqReporte">${notaHoy()?'Editar novedades':'Agregar novedades'}</button>
    ${rv?`<p class="vq-conf">${ico('check',2.4)}<span><span>Reporte del ${ffc(rv.fecha)} registrado por la administración.</span>${rv.omit?` <span>${pl(rv.omit,'registro no se incluyó','registros no se incluyeron')}.</span>`:''}</span></p>`:''}</div></section>`;
}
async function alRecibir(o){
  if(!(await adoptarFicha(o))){toast('Ese archivo no es de tu finca.',4000);return false;}
  const n=await procesarVarios(o.sobres);toast(n?'Listo: tu finca está al día':'Nada nuevo para ti en ese archivo',3500);render();return true;}
ACTS.vqRecibirArchivo=async()=>{const u=await N.elegirArchivo();if(u)await N.recibirArchivo(u,alRecibir);};
// al tocar el archivo .campo de la administración en WhatsApp se abre la app y llega aquí
N.escucharArchivos(alRecibir);
/* ---------- perfil: foto, cargo y teléfono (le llegan a la administración) ---------- */
const avatarYo=(cls='xl')=>`<span class="eq-av ${cls}${VQ.perfil&&VQ.perfil.foto?' con-foto':''}" aria-hidden="true" data-no-tr><span>${esc(N.iniciales(VQ.nombre))}</span>${VQ.perfil&&VQ.perfil.foto?'<img data-foto="eq-pf-yo" alt="" hidden>':''}</span>`;
FORMS.vqPerfil=()=>{const p=VQ.perfil||{};
  openSheet(shHead('Tu perfil',esc(VQ.nombre||''))+formWrap('vqPerfil',`<div class="vq-pf">${avatarYo()}<button type="button" class="btn sm" data-act="vqFotoPerfil">${icono('camara','i3')}${p.foto?'Cambiar foto':'Agregar foto'}</button></div>
    ${q('Cargo',`<input class="in" name="cargo" value="${esc(p.cargo||'')}" placeholder="Encargado de corrales" autocomplete="off" maxlength="40">`)}
    ${q('Teléfono',`<input class="in" name="tel" inputmode="tel" value="${esc(p.tel||'')}" placeholder="+504 9999 9999" autocomplete="tel" maxlength="24">`)}`,foot('Guardar')));};
SAVE.vqPerfil=f=>{VQ.perfil={...(VQ.perfil||{}),cargo:fv(f,'cargo').trim().slice(0,40),tel:fv(f,'tel').trim().slice(0,24),ts:Date.now(),env:0};
  guardarVQ();closeSheet();render();enviarPronto(500);toast('Perfil guardado');};
ACTS.vqFotoPerfil=async()=>{if(!window.Fotos)return;const b=await Fotos.tomar('Foto de perfil');if(!b)return;
  try{await Fotos.guardar('eq-pf-yo',b);}catch(e){toast('No se pudo guardar la foto.');return;}
  VQ.perfil={...(VQ.perfil||{}),foto:Date.now(),ts:Date.now(),env:0};guardarVQ();enviarPronto(500);FORMS.vqPerfil();render();};

/* ---------- lo que se ve: la sincronización ---------- */
syncTxt=function(){
  if(VQ.demo)return 'Modo de prueba';
  const p=nuevosDesdeReporte();
  if(!servidor())return p?`${pl(p,'registro','registros')} sin reportar`:repHoy()?'Reporte enviado':'';
  if(VQ.err)return p?`Sin conexión · ${pl(p,'registro','registros')} por enviar`:'Sin conexión';
  return p?`${pl(p,'registro','registros')} por enviar`:VQ.ult?`Sincronizado ${hhmm(VQ.ult)}`:'';
};

/* ---------- pantallas ---------- */
const PERMITIDAS=new Set(['hoy','lotes','lote','registrar','tareas','mas']);
const hdV=(t,sub,back)=>`<header class="hd">${back?hdBack(back[0],back[1],'<span class="sync"></span>'):hdTop('<span class="sync"></span>')}<div class="ttl"${back?' style="margin-top:-6px"':''}>${!back?`<span class="eyebrow">${esc(VQ.finca||S.config.finca||'')}</span>`:''}<h1>${t}</h1>${sub?`<p class="sub">${sub}</p>`:''}</div></header>`;
const REG=[['alimento','ic-y','Alimento','Entrega a cada lote','alimento'],['pesaje','ic-a','Pesaje','Peso del lote o de cada animal','pesaje'],['sanidad','ic-v','Sanidad','Vacuna, desparasitante, tratamiento','sanidad'],['baja','ic-r','Muerte','Cabezas y causa','bajas']];
const tilesReg=(lote)=>{const T=REG.filter(r=>P()[r[4]]);if(!T.length)return `<p class="hint">Todavía no tienes permiso para registrar. Pídelo a la administración.</p>`;
  return `<div class="tiles">${T.map(([k,c,t,s])=>`<button type="button" class="tile" data-act="f" data-f="${k}"${lote?` data-lote="${esc(lote)}"`:''}><span class="ic ${c}">${ico(k)}</span><div><b>${t}</b><span>${s}</span></div></button>`).join('')}</div>`;};
// tareas del colaborador: las de su trabajo y las que le asignó la administración
function misTareas(){
  const T=window.Agenda?Agenda.tareas():[];const p=P();
  return T.filter(t=>{const k=t.key||'';
    if(k.startsWith('ent:'))return p.alimento;if(k.startsWith('pes:'))return p.pesaje;if(k.startsWith('ref:'))return p.sanidad;if(k.startsWith('dia:rev:'))return true;
    if(k.startsWith('propia:'))return true;return false;});
}
const ICT=t=>icono(t.ic==='alimento'?'alimento':t.ic==='pesaje'?'pesaje':t.ic==='sanidad'?'jeringa':t.ic==='nota'?'lista':'ojo','i3');
const ACCION={alimento:['alimento','Anotar la entrega'],pesaje:['pesaje','Anotar el pesaje'],sanidad:['sanidad','Anotar la sanidad']};
// pendientes de los lotes (los calcula Rumentis): se hacen con su formulario y se terminan con foto
function filaTarea(t,H){
  const hacer=t.act&&t.act.t==='form'&&P()[{alimento:'alimento',pesaje:'pesaje',sanidad:'sanidad'}[t.act.k]]?`<button type="button" class="btn sm" data-act="f" data-f="${t.act.k}"${t.act.p&&t.act.p.lote?` data-lote="${esc(t.act.p.lote)}"`:''}${t.act.n?` data-n="${t.act.n}"`:''}>Hacer</button>`:'';
  const ok=P().tareas?`<button type="button" class="ag-ok ag-cam" data-act="vqHecha" data-k="${esc(t.key)}" data-p="" data-t="${esc(t.t)}" aria-label="Terminar con foto de evidencia">${icono('camara','i3')}</button>`:'';
  const rep=(S.config.repetirAg||{})[t.key];
  return `<div class="row ag-row"><span class="mas-ic t-${t.tono||'a'}">${ICT(t)}</span><div class="tx"><b>${esc(t.t)}</b><span>${rep?'<span class="vq-et t-r">Repetir con otra foto</span>':`${esc(t.s)}${t.f>H?` · ${ffc(t.f)}`:t.f<H?' · atrasada':''}`}</span></div><div class="ag-acts">${hacer}${ok}</div></div>`;
}
// una tarea asignada por la administración: qué es, sus indicaciones y el botón para hacerla
function tarjetaTarea(t,H){
  const tt=(S.config.tareas||[]).find(x=>x.id===t.propia)||{},a=tt.acc&&tt.acc.k,lista=(VQ.listas||{})[t.propia],repetir=!!tt.repetir,nueva=(VQ.nuevas||[]).includes(t.propia);
  const frec=Agenda.REP[tt.rep||'una']||'',futura=t.f>H;
  const chips=[nueva?'<span class="vq-et t-v">Nueva</span>':'',repetir?'<span class="vq-et t-r">Repetir con otra foto</span>':'',lista&&!repetir?'<span class="vq-et t-y">Falta la foto</span>':'',
    `<span class="vq-chip">${frec}</span>`,futura?`<span class="vq-chip">${ffc(t.f)}</span>`:t.f<H&&(tt.rep||'una')==='una'?'<span class="vq-chip t-r">Atrasada</span>':'',
    /^s\d$/.test(tt.rep||'')?`<span class="vq-chip">${esc(t.s)}</span>`:''].filter(Boolean).join('');
  const puedeAcc=a&&P()[a]&&!lista&&!repetir;
  const btn=futura?'':puedeAcc?`<button type="button" class="btn vq-tc-b" data-act="f" data-f="${ACCION[a][0]}"${tt.acc.lote?` data-lote="${esc(tt.acc.lote)}"`:''}>${ico(a==='sanidad'?'sanidad':a)}${ACCION[a][1]}</button>`
    :`<button type="button" class="btn pri vq-tc-b" data-act="vqHecha" data-k="${esc(t.key)}" data-p="${esc(t.propia)}" data-t="${esc(t.t)}">${icono('camara','i3')}Terminar con foto</button>`;
  return `<div class="card vq-tc${nueva?' nueva':''}"><div class="vq-tc-h"><span class="mas-ic t-${t.tono||'a'}">${ICT(t)}</span><div><b>${esc(t.t)}</b><span class="vq-chips">${chips}</span></div></div>
    ${tt.nota?`<p class="vq-ind" data-no-tr>${esc(tt.nota)}</p>`:''}
    ${a&&!futura?`<p class="vq-tc-p">${lista?'Ya la anotaste: toma la foto para terminarla.':`Al anotar ${a==='alimento'?'la entrega':a==='pesaje'?'el pesaje':'la sanidad'} se pide la foto de evidencia.`}</p>`:''}
    ${btn}</div>`;
}
// lo que terminó hoy, con su foto
function terminadasHoy(H){const L=(VQ.hechosLog||[]).filter(x=>x.f===H);if(!L.length)return '';
  return `<section class="sec">${secH('Terminadas hoy',L.length)}<div class="card rows">${L.slice().reverse().map(x=>`<div class="row vq-rt"><span class="vq-ck ok" aria-hidden="true">${ico('check',2.6)}</span><div class="tx"><b>${esc(x.t)}</b>${x.nota?`<span data-no-tr>${esc(x.nota)}</span>`:''}</div>${x.foto?`<span class="eq-th sm foto"><img data-foto="eq-${esc(x.foto)}" alt="" hidden><span>${icono('camara','i3')}</span></span>`:''}</div>`).join('')}</div></section>`;}
const RTX={alimento:i=>`${nf(i.kg)} kg de alimento a ${nomL(i.lote)}`,pesaje:i=>i.prom?`Pesaje de ${nomL(i.lote)}: ${wtxt(i.prom)}`:`Pesaje de animales de ${nomL(i.lote)}`,sanidad:i=>`${i.producto||i.clase} en ${nomL(i.lote)}`,baja:i=>`${pl(+i.cab||1,'muerte','muertes')} en ${nomL(i.lote)}`};
const nomL=id=>S.lotes[id]?S.lotes[id].nombre:'un lote';
function misRegistros(dia){const mios=new Set((VQ.cola||[]).filter(o=>o.k==='item+').map(o=>o.it.id));
  return allItems().filter(i=>(!dia||i.f===dia)&&((i.por&&i.por.v===VQ.vid)||mios.has(i.id))).reverse();}
function filaReg(i){const pend=(VQ.cola||[]).some(o=>o.k==='item+'&&o.it.id===i.id);
  return `<div class="row"><span class="mas-ic t-${i.tipo==='baja'?'r':i.tipo==='alimento'?'y':i.tipo==='sanidad'?'v':'a'}">${icono(i.tipo==='baja'?'alerta':i.tipo==='sanidad'?'jeringa':i.tipo,'i3')}</span><div class="tx"><b>${esc((RTX[i.tipo]||(()=>i.tipo))(i))}</b><span><span>${cuando(i.f)}</span> · <span>${pend?'Por enviar':'Enviado'}</span></span></div></div>`;}

function pantallaActivar(){
  const e=VQ.estado;
  const logo=`<div class="vq-logo">${typeof LOGO_HD!=='undefined'?LOGO_HD:''}<span data-no-tr>${NOMBRE_APP()}</span></div>`;
  if(e==='pendiente')return `<main class="vq-act">${logo}<div class="card pad vq-card">
    <div class="vq-espera" aria-hidden="true"><i></i><i></i><i></i></div>
    <h1>Esperando confirmación</h1><p>${VQ.finca?`Tu licencia ${N.fmtLic(VQ.lic)} es de ${esc(VQ.finca)}.`:`Tu licencia es ${N.fmtLic(VQ.lic)}.`}</p>
    ${servidor()&&VQ.ficha?`<button type="button" class="btn pri full" data-act="vqRevisar">Revisar ahora</button>`:`<ol class="vq-pasos"><li>Envía tu solicitud de acceso a la administración por WhatsApp o correo.</li><li>Te devuelven un archivo de acceso: tócalo y elige Rumentis Equipo.</li></ol>
    <button type="button" class="btn pri full" data-act="vqEnviarArchivo">Enviar solicitud de acceso</button><button type="button" class="btn full" data-act="vqRecibirArchivo">Abrir archivo de acceso</button>`}
    <button type="button" class="lnk" data-act="vqOtra">Usar otra licencia</button></div></main>`;
  if(e==='rechazada')return `<main class="vq-act">${logo}<div class="card pad vq-card"><h1>No se pudo activar</h1><p>${esc(VQ.motivo||'La administración no aceptó la licencia.')}</p>
    <button type="button" class="btn pri full" data-act="vqOtra">Intentar con otra licencia</button></div></main>`;
  if(e==='baja')return `<main class="vq-act">${logo}<div class="card pad vq-card">${VQ.fincaAnterior?`<span class="eyebrow" data-no-tr>${esc(VQ.fincaAnterior)}</span>`:''}<h1>Sesión cerrada</h1><p>La administración revocó tu licencia. Los datos de la finca se borraron de este teléfono.</p>
    <button type="button" class="btn pri full" data-act="vqOtra">Activar otra licencia</button></div></main>`;
  const pre=VQ.enlace?N.leerEnlace(VQ.enlace):null;
  return `<main class="vq-act">${logo}<div class="card pad vq-card"><h1>Bienvenido al equipo</h1><p>Con la licencia que te dio la administración anotas aquí la comida, los pesajes y la sanidad, y al terminar el día envías tu reporte.</p>
    <form id="vqForm" onsubmit="return false" novalidate>
    ${q('Tu nombre completo',`<input class="in" name="nombre" autocomplete="name" autocapitalize="words" placeholder="Nombre y apellido" value="${esc(VQ.nombre||'')}">`,'Como aparece en tus documentos.')}
    ${q('Licencia',`<input class="in vq-lic" name="lic" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="RV-XXXX-XXXX-XXXX-XXXX-XXXX" data-no-tr value="${pre?N.fmtLic(pre.lic):''}">`)}
    <div class="vq-bt"><button type="button" class="btn" data-act="vqEscanear">${icono('ojo','i3')}Escanear QR</button><button type="button" class="btn" data-act="vqPegar">Pegar</button></div>
    <p class="err"></p><button type="button" class="btn pri full" data-act="vqActivar">Activar</button></form>
    ${CFG.prueba?`<p class="hint vq-prueba">App de prueba: con la licencia <b data-no-tr>${N.LIC_PRUEBA}</b> entras a una finca de muestra.</p>`:''}</div></main>`;
}

/* cámara para pesaje (beta): este teléfono como cámara enlazada al de la administración */
const camInv=()=>VQ.camInv&&Date.now()-VQ.camInv.ts<10*60e3&&window.Enlace&&Enlace.ROLES[VQ.camInv.rol]?VQ.camInv:null;
function tarjetaCam(){if(!CFG.beta||!window.Enlace||VQ.demo)return '';const inv=camInv();
  return `<section class="sec">${secH('Cámara para pesaje')}<div class="card pad vq-cam"><p class="hint" style="margin:0">Este teléfono toma al animal al mismo tiempo que el de la administración. Conéctense al mismo Wi-Fi o al punto de acceso de uno de los teléfonos.</p>
    ${inv?`<button type="button" class="btn pri full" data-act="vqCamUnir">Unirme: ${Enlace.ROLES[inv.rol].nombre}</button>`:''}
    <button type="button" class="btn${inv?'':' pri'} full" data-act="enUnirme">Leer el código de la administración</button></div></section>`;}
ACTS.vqCamUnir=async()=>{const inv=camInv();if(!inv)return;VQ.camInv=null;guardarVQ();
  await Enlace.aceptarInvitacion(inv,async carga=>{if(!servidor()||!VQ.ficha)return false;
    const s=await N.sellar('camr',{e:VQ.e,de:VQ.vid,para:'jefe',carga,caja:{pub:VQ.ficha.k,sec:VQ.yo.caja.sec},firmaSec:VQ.yo.firma.sec});
    VQ.salidaCam=(VQ.salidaCam||[]).concat(s);guardarVQ();await sincronizar();return true;});render();};
/* Hoy: el día de trabajo. Arriba lo asignado por la administración (con su avance), después anotar, los pendientes
   que Rumentis calcula para los lotes, el reporte del día y lo anotado. */
PAGES.hoy=()=>{
  const C=calc(),H=hoy(),T=misTareas().filter(t=>t.f<=H),asig=T.filter(t=>t.propia),lotesT=T.filter(t=>!t.propia);
  const res=resumenDia(H),A=res.tareas.filter(t=>t.id),hechas=A.filter(t=>t.ok).length,tot=A.length,pct=tot?Math.round(hechas/tot*100):0;
  const regs=misRegistros(H);
  return `<header class="hd vq-hd">${hdTop(fechaLarga())}<div class="ttl"><span class="eyebrow">${esc(VQ.finca||S.config.finca||'')}</span><h1>Hola, ${esc(primerNombre())}</h1>
    <div class="vq-prog"><div class="vq-prog-t"><b>${tot?`${hechas} de ${tot}`:'Sin tareas'}</b><span>${tot?'tareas asignadas terminadas hoy':'asignadas para hoy'}</span></div><div class="vq-prog-b" aria-hidden="true"><i style="width:${pct}%"></i></div></div></div></header>
  <main class="bd">
   ${VQ.demo?`<div class="eq-prueba">${ico('check',2.2)}<p><b>Modo de prueba.</b> Estás en una finca de muestra; lo que anotes se queda en este teléfono.</p></div>`:''}
   <section class="sec">${secH('Asignadas por la administración',asig.length)}${asig.length?`<div class="vq-tcs">${asig.map(t=>tarjetaTarea(t,H)).join('')}</div>`:`<p class="hint">No tienes tareas asignadas para hoy.</p>`}</section>
   ${terminadasHoy(H)}
   ${tarjetaCam()}
   <section class="sec">${secH('Anotar')}${tilesReg()}</section>
   ${lotesT.length?`<section class="sec">${secH('Pendientes de los lotes',lotesT.length,lnk('#tareas','Ver todo'))}<p class="hint vq-sub">Los calcula Rumentis con los datos de cada lote.</p><div class="card rows">${lotesT.slice(0,5).map(t=>filaTarea(t,H)).join('')}</div></section>`:''}
   ${tarjetaReporte()}
   <section class="sec">${secH('Lo que anotaste hoy',regs.length)}${regs.length?`<div class="card rows">${regs.slice(0,10).map(filaReg).join('')}</div>`:`<p class="hint">Todavía nada hoy.</p>`}</section>
  </main>`;
};
PAGES.registrar=()=>{const regs=misRegistros().slice(0,12);
  return hdV('Registrar','Cada registro queda con tu nombre.')+`<main class="bd"><section class="sec">${secH('Anotar')}${tilesReg()}</section>
   <section class="sec">${secH('Lo último que anotaste')}${regs.length?`<div class="card rows">${regs.map(filaReg).join('')}</div>`:`<p class="hint">Todavía no anotas nada.</p>`}</section></main>`;};
PAGES.tareas=()=>{const H=hoy(),T=misTareas();const asig=T.filter(t=>t.propia),lot=T.filter(t=>!t.propia);
  const hoyA=asig.filter(t=>t.f<=H),prox=asig.filter(t=>t.f>H),lotHoy=lot.filter(t=>t.f<=H),lotProx=lot.filter(t=>t.f>H&&t.f<=addDias(H,7));
  return hdV('Tareas','Cada tarea se termina con una foto de evidencia.')+`<main class="bd">
   <section class="sec">${secH('Asignadas para hoy',hoyA.length)}${hoyA.length?`<div class="vq-tcs">${hoyA.map(t=>tarjetaTarea(t,H)).join('')}</div>`:`<p class="hint">No tienes tareas asignadas para hoy.</p>`}</section>
   ${prox.length?`<section class="sec">${secH('Asignadas para después',prox.length)}<div class="vq-tcs">${prox.map(t=>tarjetaTarea(t,H)).join('')}</div></section>`:''}
   ${terminadasHoy(H)}
   ${lotHoy.length||lotProx.length?`<section class="sec">${secH('Pendientes de los lotes',lotHoy.length+lotProx.length)}<p class="hint vq-sub">Los calcula Rumentis con los datos de cada lote: pesajes, vacunas y entregas.</p>
     ${lotHoy.length?`<div class="card rows">${lotHoy.map(t=>filaTarea(t,H)).join('')}</div>`:''}
     ${lotProx.length?`<p class="rh">Próximos 7 días</p><div class="card rows">${lotProx.map(t=>filaTarea(t,H)).join('')}</div>`:''}</section>`:''}
  </main>`;};
PAGES.lotes=()=>{const C=calc();
  const card=x=>`<a class="card lote vq-lote" href="#lote/${encodeURIComponent(x.id)}"><div class="l1"><div><span class="nm">${esc(x.l.nombre)}</span><span class="sm">${pl(x.cab,'cabeza','cabezas')}</span></div>${ico('chev')}</div>
    <div class="l2"><b>${nf(x.pesoHoy)} kg</b><span class="sm">${x.estimado?'estimado, ':'promedio, '}día ${x.dec} de engorde</span></div>
    <div class="vq-l3"><span>${x.racion&&S.raciones[x.racion]?esc(S.raciones[x.racion].nombre):'Sin ración asignada'}</span><span>Último pesaje: <span>${cuando(x.ult.f)}</span></span></div></a>`;
  return hdV('Lotes',C.act.length?`${pl(C.act.length,'lote','lotes')} con ${pl(C.cabT,'cabeza','cabezas')}.`:'')+`<main class="bd">${C.act.length?C.act.map(card).join(''):`<div class="card"><p class="empty">La finca todavía no tiene lotes en engorde.</p></div>`}</main>`;};
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
  if(sub==='acerca')return hdV(`Acerca de <span data-no-tr>Rumentis ${NOMBRE_APP()}</span>`,'',['#mas','Más'])+`<main class="bd"><section class="sec"><div class="card pad acerca">${typeof FIRMA!=='undefined'?FIRMA:''}<p><b>Versión ${VERSION_APP}</b></p>
    <p>Lo que anotas se guarda en tu teléfono y viaja cifrado en tu reporte: solo la administración de tu finca puede leerlo.</p><p>Tu licencia es solo tuya; no la compartas.</p></div></section></main>`;
  if(sub==='idioma')return hdV('Idioma','',['#mas','Más'])+`<main class="bd"><section class="sec"><div class="card rows">${(window.I18N?I18N.IDIOMAS:[]).map(([k,t])=>`<button type="button" class="row" data-act="vqIdioma" data-l="${k}"><div class="tx"><b data-no-tr>${esc(t)}</b></div>${(window.I18N&&I18N.lang()===k)?ico('check',2.4):''}</button>`).join('')}</div></section></main>`;
  const reps=(VQ.reportes||[]).slice(0,7);
  const pf=VQ.perfil||{};
  return `<header class="hd">${hdTop('<span class="sync"></span>')}<div class="ttl"><span class="eyebrow">${esc(VQ.finca||'')}</span><h1>Más</h1></div></header>
  <main class="bd">
   <section class="sec"><button type="button" class="card pad vq-yo vq-yo-b" data-act="f" data-f="vqPerfil">${avatarYo()}<div><b>${esc(VQ.nombre||'')}</b>${pf.cargo?`<span data-no-tr>${esc(pf.cargo)}</span>`:''}<span>Licencia <span data-no-tr>${VQ.demo?N.LIC_PRUEBA:N.fmtLic(VQ.lic||'')}</span></span><span>${esc(VQ.finca||'')}${VQ.desde?` · desde el ${ffc(VQ.desde)}`:''}</span></div>${ico('chev')}</button>
    ${!pf.foto&&!VQ.demo?`<button type="button" class="lnk" data-act="f" data-f="vqPerfil">Agregar foto de perfil</button>`:''}</section>
   ${VQ.demo?'':`<section class="sec">${secH('Reporte diario')}<div class="card pad eq-sinc"><div class="eq-sinc-h"><span class="mas-ic t-v">${icono('recibo','i3')}</span><div><b>${horaRep()}:00</b><span>${repHoy()?`Enviado hoy a las ${hhmm(repHoy().ts)}`:'Hora del reporte'}</span></div></div>
    ${enLinea()?'':`<div class="vq-off">${ico('aviso',2)}<p><b>Sin internet.</b> El reporte se envía como archivo y la actualización de la administración llega como archivo: tócalo en WhatsApp o ábrelo aquí.</p></div>
    <div class="acts"><button type="button" class="btn pri" data-act="f" data-f="vqReporte">Enviar reporte</button><button type="button" class="btn" data-act="vqRecibirArchivo">Abrir archivo</button></div>`}
    ${reps.length?`<div class="rows vq-reps">${reps.map(r=>`<div class="row"><div class="tx"><b>${ffc(r.fecha)}, ${hhmm(r.ts)}</b><span>${pl(r.n||0,'registro','registros')}</span></div></div>`).join('')}</div>`:''}</div></section>`}

   <section class="sec">${secH('Ajustes')}<div class="card rows">
    ${masFila(mico('paleta'),'Apariencia','Modo claro u oscuro y colores','href="#mas/apariencia"','t-v')}
    ${masFila(mico('ayuda'),'Idioma',window.I18N?(I18N.IDIOMAS.find(x=>x[0]===I18N.lang())||['',''])[1]:'','href="#mas/idioma"','t-a')}
    ${masFila(mico('info'),'Acerca de','Versión y privacidad','href="#mas/acerca"','t-a')}</div></section>
   <div class="firma">${typeof FIRMA!=='undefined'?FIRMA:''}<span><span data-no-tr>Rumentis ${NOMBRE_APP()}</span> · <span>versión ${VERSION_APP}</span></span></div></main>`;
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
// alimento: el mismo formulario, sin precios ni horarios (los pone la administración)
FORMS.alimento=({n}={})=>{
  const C=calc();if(!C.act.length){toast('La finca todavía no tiene lotes.');return;}
  const Ne=nEntregas();n=+n||C.sigEnt||1;const R=Object.entries(S.raciones).sort((a,b)=>byName(a[1],b[1]));
  const racSel=R.length?q('Ración',opts('racion',[{v:'_lote',t:'La de cada lote',s:'la que tiene asignada'}].concat(R.map(([k,r])=>({v:k,t:r.nombre}))),'_lote',{lo:true})):'';
  const blocks=C.act.map(x=>{const pd=programadoDe(x,n),p=pd.kg;return `<div data-l="${esc(x.id)}"><div class="fh"><b>${esc(x.l.nombre)}</b><span class="sm">${pl(x.cab,'cabeza','cabezas')}${x.racion&&S.raciones[x.racion]?', '+esc(S.raciones[x.racion].nombre):''}</span></div>
    <div class="unit"><input class="in" name="kg_${esc(x.id)}" inputmode="decimal" placeholder="${nf(W(p))}" data-prog="${Math.round(W(p)/5)*5}" aria-label="Kilos para ${esc(x.l.nombre)}" autocomplete="off"><em>kg</em></div>
    <small class="feed-sug">${esc(pd.txt)}</small>
    <div class="lect" data-name="lect_${esc(x.id)}" role="group" aria-label="Cómo estaba el comedero">${LECT.map((t,i)=>`<button type="button" data-v="${i}" aria-pressed="false">${t}</button>`).join('')}</div><input type="hidden" name="lect_${esc(x.id)}" value=""></div>`;}).join('');
  const entSel=conHoras()?q('Entrega',`<select class="in" name="n">${[...Array(Ne).keys()].map(i=>`<option value="${i+1}"${i+1===n?' selected':''}>${esc(horaDe(i+1))}</option>`).join('')}</select>`):`<input type="hidden" name="n" value="${n}">`;
  const body=`<div class="two">${fecha()}${entSel}</div>${racSel}
   <button type="button" class="btn full ar-soft" data-act="llenarProg">${ico('check',2.4)}Poner la cantidad sugerida a todos</button>
   <div class="sec"><div class="sec-h"><div><h2 style="font-size:17px">Cantidad por lote</h2></div></div>
   <p class="hint" style="margin-top:-4px">Escribe lo que serviste a cada lote. Debajo de cada uno está la cantidad sugerida y de dónde sale. Si quieres, marca cómo estaba el comedero.</p>
   <div class="card feed">${blocks}</div></div>`;
  openSheet(shHead(`Entrega ${esc(entDe(n))}`,'Alimento')+formWrap('alimento',body,foot('Guardar entrega','<b data-sum>0 kg</b><span>en total</span>')));
};
// los registros no se borran desde Campo: si hay un error, lo corrige la administración
ACTS.delItems=()=>toast('Los registros no se pueden borrar. Si hay un error, avisa a la administración.',3800);

/* ---------- barra de abajo del vaquero ---------- */
const NAVV=[['hoy','Hoy'],['lotes','Lotes'],['registrar','Registrar'],['tareas','Tareas'],['mas','Más']];
function barra(){const nav=$('nav.bottom .in');if(!nav||nav.dataset.vq)return;nav.dataset.vq='1';
  nav.innerHTML=NAVV.map(([k,t])=>k==='registrar'?`<a href="#registrar" data-nav="registrar" class="reg"><span class="sq">${ico('registrar',2.6)}</span><span>${t}</span></a>`:`<a href="#${k}" data-nav="${k}">${ico(k==='tareas'?'tareas':k)}<span>${t}</span></a>`).join('');}
barra();
document.addEventListener('rumentis-pintado',()=>{const r=route();const cur=r.p==='lote'?'lotes':r.p;$$('nav.bottom [data-nav]').forEach(a=>{if(a.dataset.nav===cur)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');});});

window.Vaquero={VQ:()=>VQ,autoReporte,yo:()=>({v:VQ.vid,n:VQ.nombre}),alRecibir,sobreReporte,resumenDia,vivo:()=>T.vivo(),sincronizar,procesar,procesarVarios,aplicarEstado,activar,activarSinFicha,adoptarFicha,modoPrueba,sobreOps,sobreAlta};
if(!Object.keys(S.lotes).length&&activo()&&!VQ.demo)S.config.finca=VQ.finca||S.config.finca;
document.documentElement.classList.add('vq-listo');
render();
if(VQ.estado==='pendiente'||activo())setTimeout(()=>sincronizar(),1500);
})();
