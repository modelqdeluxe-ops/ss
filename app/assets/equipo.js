/* Rumentis: Equipo (app del jefe).
   - El dueño compra licencias en Google Play (producto consumible, una por vaquero). Cada licencia es un código al
     azar para una sola persona. En la app de prueba la compra se simula y no se cobra.
   - Con la primera licencia aparece la pestaña Equipo: licencias con su QR y enlace, vaqueros con sus permisos,
     lo que registró cada uno, tareas asignadas y la sincronización.
   - El vaquero se activa con su nombre y la licencia. Su alta llega cifrada; si la licencia está libre se acepta
     (o se pide confirmar, si el jefe lo prefiere) y se le manda la bienvenida con la clave del equipo.
   - Lo que registra el vaquero llega como operaciones (entregas, pesajes, sanidad, muertes, tareas hechas); se
     comprueban sus permisos, se aplican a los datos del jefe con su nombre y quedan en la actividad.
   - El jefe publica el estado para el equipo (lotes activos, raciones, registros recientes y tareas, sin dinero),
     cifrado con la clave del equipo. Al dar de baja a alguien, la clave cambia.
   - Todo viaja por el servidor de relevo si hay uno, y siempre se puede pasar por archivo (WhatsApp). */
(function(){
'use strict';
const N=window.EquipoNucleo;if(!N||N.CFG.app!=='jefe')return;
const CFG=N.CFG;
IC.equipo='<circle cx="9" cy="8" r="3.2"/><path d="M3.2 19.5c.6-3.3 2.9-5.2 5.8-5.2s5.2 1.9 5.8 5.2"/><circle cx="17.2" cy="9.2" r="2.5"/><path d="M15.9 14.4c2.7-.2 4.6 1.5 5 4.4"/>';

/* ---------- estado ---------- */
const LSS='rumentis-eq-sync';   // lo del servidor y la cola de salida: no va en el respaldo, se rehace solo
let SY={};try{SY=JSON.parse(localStorage.getItem(LSS)||'{}')||{};}catch(e){SY={};}
const guardarSY=()=>{try{localStorage.setItem(LSS,JSON.stringify(SY));}catch(e){}};
const EQ=()=>S.config.equipo||null;
const guardarEQ=o=>put('ajustes','finca',{...S.config,equipo:{...(EQ()||{}),...o}});
const PERM=[['alimento','Entregar alimento'],['pesaje','Pesar'],['sanidad','Sanidad'],['bajas','Muertes'],['tareas','Tareas de la agenda']];
const PERM_DEF={alimento:true,pesaje:true,sanidad:true,bajas:true,tareas:true};
const PERM_TIPO={alimento:'alimento',pesaje:'pesaje',sanidad:'sanidad',baja:'bajas'};
const servidor=()=>N.urlOk((EQ()&&EQ().servidor)||CFG.servidorEquipo);
const horaRep=()=>{const h=(EQ()||{}).horaReporte;return h!=null&&+h>=0&&+h<=23?+h:17;};
const firmaJ=()=>{const f=S.config.firma;return f&&f.pub&&f.sec?{pub:f.pub,sec:f.sec}:null;};
const conEquipo=()=>{const e=EQ();return !!(e&&e.id&&Object.keys(e.licencias||{}).length);};
const activos=()=>Object.values((EQ()||{}).vaqueros||{}).filter(v=>v.estado==='activo');
// el precio lo pone Google Play; mientras no responde, el de config.js (US$1.99)
const precioLic=()=>{try{return localStorage.getItem('rumentis-precio-lic')||CFG.precioLicencia||'';}catch(e){return CFG.precioLicencia||'';}};
/* Modo dueño: con el código del dueño de la app (solo se guarda su huella SHA-256), la app de Google Play crea
   licencias sin cobrar. Son licencias reales: la app Rumentis Vaquero las acepta. */
const HUELLA_DUENO='c53a11605d52e083e4790cbcf5cd51e647d34557bcbeaa521658191e1b4511dc';
const esDueno=()=>!!(EQ()&&EQ().dueno);
const gratis=()=>CFG.prueba||esDueno();
const tr=t=>window.I18N&&I18N.txt?I18N.txt(t):t;
// "Equipo" en el catálogo es equipamiento (inventario): el nombre de esta pestaña va aparte
const EQT=()=>`<span data-no-tr>${({en:'Team',pt:'Equipe'})[window.I18N&&I18N.lang?I18N.lang():'es']||'Equipo'}</span>`;

async function asegurarEquipo(){
  const e=EQ();if(e&&e.id&&e.caja&&e.clave)return e;
  const n=await N.nacl();Documentos.llave();
  const c=n.box.keyPair();
  guardarEQ({id:'e'+N.b64u(n.randomBytes(12)),creado:hoy(),caja:{pub:N.b64(c.publicKey),sec:N.b64(c.secretKey)},clave:N.b64(n.randomBytes(32)),gen:1,claves:{},
    licencias:{},vaqueros:{},solicitudes:{},act:[],auto:true});
  return EQ();
}
function actividad(o){const e=EQ();const a=[{ts:Date.now(),...o}].concat(e.act||[]).slice(0,250);return a;}
function registrarAct(o){guardarEQ({act:actividad(o)});}

/* ---------- licencias ---------- */
function crearLicencia(c,compra){
  const e=EQ();if(e.licencias&&e.licencias[c])return e.licencias[c];
  const L={...e.licencias,[c]:{c,creada:hoy(),ts:Date.now(),compra,estado:'libre'}};
  guardarEQ({licencias:L,act:actividad({ic:'lic',txt:(compra&&(compra.dueno||compra.prueba)?`Creaste la licencia ${N.fmtLic(c)}`:`Compraste la licencia ${N.fmtLic(c)}`)})});sincronizarPronto();return L[c];
}
async function fichaEq(){
  const e=await asegurarEquipo();Documentos.llave();const f=firmaJ(),s=servidor(),fn=String(S.config.finca||'').slice(0,60),p=CFG.prueba?1:0;
  if(e.ficha&&e.ficha.s===s&&e.ficha.fn===fn&&e.ficha.j===f.pub&&e.ficha.p===p&&e.ficha.k===e.caja.pub)return e.ficha;
  const fi=await N.ficha({e:e.id,firma:f,caja:e.caja,finca:fn,servidor:s,prueba:CFG.prueba});guardarEQ({ficha:fi});return fi;
}
const cuentaOfuscada=()=>N.sha('RUMENTIS-CUENTA|'+(EQ()||{}).id).slice(0,40);
function textoLicencia(c,url){
  return tr(`Hola, te doy una licencia de Rumentis Campo para trabajar con el equipo de ${S.config.finca||'la finca'}.`)+'\n\n'+
    tr('1. Descarga la app (gratis):')+' '+CFG.tiendaVaquero+'\n'+tr('2. Ábrela, escribe tu nombre y esta licencia:')+' '+N.fmtLic(c)+'\n\n'+
    tr('O abre este enlace desde tu teléfono:')+' '+url;
}

/* ---------- compra con Google Play ---------- */
ACTS.eqComprar=async()=>{
  await asegurarEquipo();
  if(esDueno()&&!CFG.prueba){
    openSheet(shHead('Licencia sin costo',EQT())+`<div class="sh-body"><div class="eq-prueba">${ico('check',2.2)}<p><b>Código maestro:</b> la licencia se crea al instante, sin costo, y sirve en Rumentis Campo como cualquier otra.</p></div></div>
      <div class="sh-foot"><button type="button" class="btn" data-act="cerrar" style="flex:1">Cancelar</button><button type="button" class="btn pri" data-act="eqCompraPrueba" style="flex:1">Crear licencia</button></div>`);return;}
  if(CFG.prueba){
    openSheet(shHead('Compra de prueba',EQT())+`<div class="sh-body"><div class="eq-prueba">${ico('check',2.2)}<p>Esta es la <b>app de prueba</b>: la licencia se crea al instante y no se cobra nada. En la app de Google Play el pago lo hace Google Play.</p></div></div>
      <div class="sh-foot"><button type="button" class="btn" data-act="cerrar" style="flex:1">Cancelar</button><button type="button" class="btn pri" data-act="eqCompraPrueba" style="flex:1">Crear licencia</button></div>`);return;}
  if(!N.pagosHay()){toast('Las licencias se compran en la app de Rumentis de Google Play.',4500);return;}
  const c=N.nuevaLicencia();SY.pend={c,ts:Date.now()};guardarSY();
  try{Pagos.comprar(CFG.productoLicencia,cuentaOfuscada(),c);}catch(e){toast('No se pudo abrir Google Play.',4000);}
};
ACTS.eqCompraPrueba=async()=>{await asegurarEquipo();if(!gratis())return;const c=N.nuevaLicencia();crearLicencia(c,CFG.prueba?{prueba:true}:{dueno:true,ts:Date.now()});closeSheet();toast('Licencia creada');
  if(!location.hash.startsWith('#equipo'))location.hash='#equipo';setTimeout(()=>abrirLicencia(c),380);};
N.pagosEscuchar(async o=>{
  if(!o||!o.tipo)return;
  if(o.tipo==='precio'&&o.producto===CFG.productoLicencia){try{localStorage.setItem('rumentis-precio-lic',o.precio||'');}catch(e){}scheduleRender();return;}
  if(o.tipo==='error'){if(o.codigo!=='cancelada')toast(o.mensaje||'Google Play no pudo completar la compra.',4500);return;}
  if(o.tipo!=='compra'||o.producto!==CFG.productoLicencia)return;
  if(o.estado==='pendiente'){toast('Tu pago está pendiente. La licencia aparece cuando Google Play lo confirme.',5500);return;}
  await asegurarEquipo();const e=EQ();
  const ya=Object.values(e.licencias||{}).find(L=>L.compra&&L.compra.token&&L.compra.token===o.token);
  if(!ya){
    const real=N.compraReal(o.json,o.firma);if(real===false){toast('Esta compra no pasó la verificación de Google Play.',5000);return;}
    let c=N.normLic(o.perfil||'');if(!N.licValida(c))c=(SY.pend&&SY.pend.c)||N.nuevaLicencia();
    crearLicencia(c,{orden:o.orden||'',token:o.token,json:o.json,firma:o.firma,ts:Date.now()});
    SY.pend=null;guardarSY();toast('¡Licencia lista! Compártela con la persona que la va a usar.',4000);
    if(!location.hash.startsWith('#equipo'))location.hash='#equipo';setTimeout(()=>abrirLicencia(c),450);
  }
  // consumida: así se puede comprar otra (la licencia ya quedó guardada)
  try{Pagos.consumir(o.token);}catch(err){}
});

/* ---------- licencia: QR, copiar y compartir ---------- */
async function abrirLicencia(c){
  const fi=await fichaEq(),url=N.enlace(fi,c);let svg='';try{svg=await N.qrSvg(url);}catch(e){}
  openSheet(shHead('Licencia de acceso',EQT())+`<div class="sh-body eq-lic">
    ${svg?`<div class="eq-qr">${svg}</div>`:''}
    <div class="eq-cod"><span>Licencia</span><b data-no-tr>${N.fmtLic(c)}</b></div>
    <ol class="eq-pasos"><li>La persona escanea este código con la cámara de su teléfono y descarga <b>Rumentis Campo</b>, gratis en Google Play.</li>
    <li>Al abrirla escribe su nombre y la licencia, o toca <b>Escanear QR</b>.</li><li>Te envía su solicitud de acceso; la abres aquí y le devuelves la actualización. Desde ahí te envía su reporte cada día.</li></ol>
    <p class="hint">Cada licencia es para una sola persona. No la publiques.</p></div>
    <div class="sh-foot"><button type="button" class="btn" data-act="eqCopiar" data-c="${c}" style="flex:1">Copiar</button><button type="button" class="btn pri" data-act="eqCompartir" data-c="${c}" style="flex:1.4">Enviar por WhatsApp</button></div>`);
}
ACTS.eqLicencia=el=>abrirLicencia(el.dataset.c);
ACTS.eqCompartir=async el=>{const c=el.dataset.c,fi=await fichaEq();N.compartirTexto(textoLicencia(c,N.enlace(fi,c)));};
ACTS.eqCopiar=async el=>{const c=el.dataset.c,fi=await fichaEq();const t=textoLicencia(c,N.enlace(fi,c));
  try{await navigator.clipboard.writeText(t);toast('Copiado');}catch(e){N.compartirTexto(t);}};

/* ---------- salida: sobres para el equipo ---------- */
function encolar(s){SY.salida=(SY.salida||[]).concat(s);SY.archivo=(SY.archivo||[]).filter(x=>x.ts>Date.now()-30*864e5).concat(s).slice(-80);guardarSY();sincronizarPronto();}
async function enviarSalida(base,f){
  const q=SY.salida||[];if(!q.length)return;
  await N.pedir(base,'/v1/enviar',{e:EQ().id,quien:'jefe',sobres:q},f.sec);
  SY.salida=(SY.salida||[]).filter(s=>!q.includes(s));guardarSY();
}

/* ---------- altas ---------- */
async function procesarAlta(s){
  const e=EQ();let carga=null;
  if(s.x==='plano'){try{if(await N.verificar(s,s.f,s.j))carga=JSON.parse(s.c);}catch(err){carga=null;}}
  else carga=await N.abrir(s,{caja:{pub:s.k,sec:e.caja.sec},firmaPub:s.j});
  if(!carga||N.idDe(s.j)!==s.de)return;
  const c=N.normLic(carga.lic),L=(e.licencias||{})[c],vid=N.idDe(s.j),nombre=String(carga.nombre||'').trim().slice(0,60)||'Colaborador';
  const rechazar=async m=>{const f=firmaJ();encolar(await N.sellar('rechazo',{e:e.id,de:'jefe',para:vid,carga:{ok:false,motivo:m,lic:c},caja:{pub:s.k,sec:e.caja.sec},firmaSec:f.sec}));};
  if(!L)return rechazar('Esa licencia no es de este equipo.');
  if(L.estado==='baja')return rechazar('Esa licencia fue dada de baja.');
  if(L.estado==='activa'){if(L.vid===vid)return bienvenida(vid);return rechazar('Esa licencia ya la usa otra persona.');}
  const sol={vid,nombre,lic:c,firma:s.j,caja:s.k,disp:String(carga.disp||'').slice(0,60),ts:Date.now()};
  if(e.auto===false){if(!(e.solicitudes||{})[vid]){guardarEQ({solicitudes:{...(e.solicitudes||{}),[vid]:sol},act:actividad({ic:'alta',v:vid,n:nombre,txt:`${nombre} solicita acceso con la licencia ${N.fmtLic(c)}`})});toast(`${nombre} solicita acceso al equipo`,4000);}return;}
  await aceptar(sol);
}
async function aceptar(sol){
  const e=EQ();const L=e.licencias[sol.lic];if(!L||L.estado!=='libre')return;
  const sols={...(e.solicitudes||{})};delete sols[sol.vid];
  const v={id:sol.vid,nombre:sol.nombre,lic:sol.lic,firma:sol.firma,caja:sol.caja,disp:sol.disp,alta:hoy(),ts:Date.now(),permisos:{...PERM_DEF},estado:'activo',seq:0};
  guardarEQ({vaqueros:{...e.vaqueros,[v.id]:v},licencias:{...e.licencias,[sol.lic]:{...L,estado:'activa',vid:v.id,nombre:v.nombre,activada:hoy()}},solicitudes:sols,
    act:actividad({ic:'alta',v:v.id,n:v.nombre,txt:`${v.nombre} entró al equipo con la licencia ${N.fmtLic(sol.lic)}`})});
  toast(`${v.nombre} entró al equipo. Envíale la actualización para que empiece.`,4500);
  await bienvenida(v.id);await publicarEstado(true);
}
async function bienvenida(vid){
  const e=EQ(),v=e.vaqueros[vid],L=e.licencias[v.lic],f=firmaJ();
  const real=L.compra&&!L.compra.prueba&&L.compra.json?{json:L.compra.json,firma:L.compra.firma}:null;
  const carga={ok:true,vid,nombre:v.nombre,lic:v.lic,clave:e.clave,gen:e.gen,permisos:v.permisos,finca:S.config.finca||'',compra:real,prueba:!!(L.compra&&L.compra.prueba),horaReporte:horaRep()};
  encolar(await N.sellar('bienvenida',{e:e.id,de:'jefe',para:vid,carga,caja:{pub:v.caja,sec:e.caja.sec},firmaSec:f.sec}));
}
ACTS.eqAceptar=async el=>{const s=(EQ().solicitudes||{})[el.dataset.v];if(s){await aceptar(s);closeSheet();}};
ACTS.eqRechazar=async el=>{const e=EQ(),s=(e.solicitudes||{})[el.dataset.v];if(!s)return;const f=firmaJ();
  encolar(await N.sellar('rechazo',{e:e.id,de:'jefe',para:s.vid,carga:{ok:false,motivo:'La administración no aceptó la solicitud.',lic:s.lic},caja:{pub:s.caja,sec:e.caja.sec},firmaSec:f.sec}));
  const sols={...e.solicitudes};delete sols[s.vid];guardarEQ({solicitudes:sols});closeSheet();toast('Solicitud rechazada');};

/* ---------- lo que registran los vaqueros ---------- */
const MES=f=>String(f).slice(0,7);
function buscarItem(id){for(const [k,d] of Object.entries(S.diario))for(const it of (d.items||[]))if(it.id===id)return {k,it};return null;}
function costoAlimento(it){const r=it.racion&&S.raciones[it.racion];if(r)return +r.costoKg||0;const x=calc().L[it.lote];return x&&x.costoKgR||0;}
function nombreLote(id){const l=S.lotes[id];return l?l.nombre:'un lote';}
function textoItem(v,it){
  const n=v.nombre,l=nombreLote(it.lote);
  if(it.tipo==='alimento')return `${n} entregó ${nf(it.kg)} kg de alimento a ${l}`;
  if(it.tipo==='pesaje')return it.prom?`${n} pesó ${l}: ${wtxt(it.prom)} de promedio`:`${n} pesó animales de ${l}`;
  if(it.tipo==='sanidad')return `${n} aplicó ${it.producto||'un producto'} a ${l}`;
  if(it.tipo==='baja')return `${n} anotó ${pl(+it.cab||1,'muerte','muertes')} en ${l}`;
  return `${n} anotó un registro en ${l}`;
}
function aplicarOps(v,ops,omitir){
  const e=EQ();let seq=+v.seq||0,cambios=0;const act=[];const P=v.permisos||{};
  const cfgNueva={...S.config};let cfgCambio=false;
  for(const op of [...ops].sort((a,b)=>a.seq-b.seq)){
    if(!(op.seq>seq))continue;seq=op.seq;
    if(omitir&&omitir.has(op.seq))continue;   // la administración no lo quiso registrar
    try{
      if(op.k==='item+'){const it={...op.it};const p=PERM_TIPO[it.tipo];if(!p||!P[p]||!S.lotes[it.lote]||!it.id||!/^\d{4}-\d\d-\d\d$/.test(it.f||''))continue;if(buscarItem(it.id))continue;
        it.por={v:v.id,n:v.nombre};it.t=it.t||Date.now();if(it.tipo==='alimento')it.costoKg=costoAlimento(it);if(it.tipo==='sanidad')it.costo=+it.costo||0;
        const doc=clone(S.diario[MES(it.f)])||{items:[]};doc.items=(doc.items||[]).concat(it);put('diario',MES(it.f),doc);cambios++;act.push({ic:it.tipo,v:v.id,n:v.nombre,txt:textoItem(v,it)});}
      else if(op.k==='item-'){const b=buscarItem(op.id);if(!b||!b.it.por||b.it.por.v!==v.id)continue;removeItem(op.id);cambios++;act.push({ic:'borrar',v:v.id,n:v.nombre,txt:`${v.nombre} borró un registro de ${nombreLote(b.it.lote)}`});}
      else if(op.k==='item~'){const b=buscarItem(op.it&&op.it.id);if(!b||!b.it.por||b.it.por.v!==v.id)continue;const it={...op.it,por:b.it.por};if(it.tipo==='alimento')it.costoKg=costoAlimento(it);
        const doc=clone(S.diario[b.k]);doc.items=doc.items.map(x=>x.id===it.id?it:x);put('diario',b.k,doc);cambios++;}
      else if(op.k==='lote~'){const l=S.lotes[op.id];if(!l||l.estado==='cerrado')continue;const c=op.campos||{},d={};
        if('racion' in c&&P.alimento&&(c.racion===null||S.raciones[c.racion]))d.racion=c.racion;
        if('animales' in c&&(P.pesaje||P.bajas)&&Array.isArray(c.animales))d.animales=c.animales.map(a=>{const o=(l.animales||[]).find(z=>z.id===a.id||z.arete===a.arete);return o?{...a,costo:o.costo}:a;});
        if(c.estado==='cerrado'&&P.bajas){d.estado='cerrado';d.fechaCierre=c.fechaCierre||hoy();}
        if(Object.keys(d).length){put('lotes',op.id,{...l,...d});cambios++;}}
      else if(op.k==='hecha'&&P.tareas){cfgNueva.hechas={...(cfgNueva.hechas||{}),[op.key]:op.f||hoy()};cfgCambio=true;act.push({ic:'tarea',v:v.id,n:v.nombre,txt:`${v.nombre} marcó hecha: ${op.txt||'una tarea'}`});}
      else if(op.k==='tarea'){const t=(cfgNueva.tareas||[]).find(z=>z.id===op.id);if(!t||(t.para!=='todos'&&t.para!==v.id))continue;const f=/^\d{4}-\d\d-\d\d$/.test(op.f||'')?op.f:hoy();
        cfgNueva.tareas=cfgNueva.tareas.map(z=>z.id!==t.id?z:(z.rep||'una')==='una'?{...z,hecho:true,hechoPor:v.nombre,hechoF:f}
          :(z.hechos||[]).some(x=>x.f===f&&x.v===v.id)?z:{...z,hechos:(z.hechos||[]).filter(x=>x.f>=addDias(hoy(),-70)).concat({f,v:v.id,n:v.nombre})});
        cfgCambio=true;cambios++;act.push({ic:'tarea',v:v.id,n:v.nombre,txt:`${v.nombre} hizo: ${t.t}`});}
    }catch(err){}
  }
  const vs={...EQ().vaqueros,[v.id]:{...v,seq,ts:Date.now()}};
  let a=(cfgCambio?cfgNueva:S.config).equipo.act||[];for(const x of act)a=[{ts:Date.now(),...x}].concat(a);
  put('ajustes','finca',{...(cfgCambio?cfgNueva:S.config),equipo:{...EQ(),vaqueros:vs,act:a.slice(0,250)}});
  if(act.length&&!omitir)toast(act.length===1?act[0].txt:`${v.nombre}: ${pl(act.length,'registro nuevo','registros nuevos')}`,3500);
  return cambios;
}
async function procesarOps(s){
  const e=EQ(),v=(e.vaqueros||{})[s.de];if(!v||v.estado!=='activo')return;
  const clave=s.g===e.gen?e.clave:(e.claves||{})[s.g];if(!clave)return;
  const carga=await N.abrir(s,{clave,firmaPub:v.firma});if(!carga)return;
  aplicarOps(v,carga.ops||[]);
}
async function procesar(s){
  const e=EQ();if(!e||!s||s.para!=='jefe')return 0;
  // el alta sin cifrar (el vaquero se activó solo con el código, sin servidor) todavía no sabe el número del equipo
  if(s.e!==e.id&&!(s.t==='alta'&&s.x==='plano'&&!s.e))return 0;
  const vis=SY.vistos||[];if(vis.includes(s.id))return 0;SY.vistos=vis.concat(s.id).slice(-600);guardarSY();
  if(s.t==='alta')await procesarAlta(s);else if(s.t==='ops')await procesarOps(s);else if(s.t==='reporte')return await procesarReporte(s);else return 0;
  return 1;
}

/* ---------- reportes del día ----------
   Cada colaborador envía al final de su jornada un reporte (archivo .rumentis): sus registros, las tareas del día hechas
   y pendientes, cómo van las de la semana y sus novedades. Aquí se guarda sin tocar la finca; la administración lo
   revisa, puede dejar fuera algún registro y toca Registrar. La confirmación le llega al colaborador con la siguiente
   actualización (.campo). */
let ultimosRep=[];
async function procesarReporte(s){
  const e=EQ(),v=(e.vaqueros||{})[s.de];if(!v||v.estado!=='activo')return 0;
  const clave=s.g===e.gen?e.clave:(e.claves||{})[s.g];if(!clave)return 0;
  const c=await N.abrir(s,{clave,firmaPub:v.firma});if(!c||!c.rid)return 0;
  const R={...(e.reportes||{})};if(R[c.rid])return 0;
  const fecha=/^\d{4}-\d\d-\d\d$/.test((c.res||{}).fecha||'')?c.res.fecha:hoy();
  // uno anterior de la misma persona que siga sin registrar queda dentro de este (lleva todo lo que falta)
  for(const x of Object.values(R))if(x.vid===v.id&&x.estado==='nuevo'&&x.ts<c.ts)R[x.rid]={...x,estado:'incluido',en:c.rid};
  R[c.rid]={rid:c.rid,vid:v.id,n:v.nombre,ts:+c.ts||s.ts,rec:Date.now(),fecha,res:c.res||{},nota:String(c.nota||'').slice(0,1500),
    ops:(Array.isArray(c.ops)?c.ops:[]).slice(0,3000),estado:'nuevo'};
  const orden=Object.values(R).sort((a,b)=>b.ts-a.ts);for(const x of orden.slice(120))if(x.estado!=='nuevo')delete R[x.rid];
  guardarEQ({reportes:R,vaqueros:{...e.vaqueros,[v.id]:{...v,ts:Date.now()}},act:actividad({ic:'reporte',v:v.id,n:v.nombre,txt:`${v.nombre} envió su reporte del ${ffc(fecha)}`})});
  // automático: queda registrado en cuanto llega (por archivo o por internet)
  if(EQ().autoReg!==false)registrarReporte(c.rid,[],true);
  ultimosRep.push(c.rid);return 1;
}
const REP=()=>Object.values((EQ()||{}).reportes||{}).sort((a,b)=>b.ts-a.ts);
const porRegistrar=()=>REP().filter(r=>r.estado==='nuevo');
// los registros de un reporte que todavía no están en la finca
const opsNuevas=r=>{const v=(EQ().vaqueros||{})[r.vid];const s0=v?+v.seq||0:Infinity;return (r.ops||[]).filter(o=>o.seq>s0);};
function registrarReporte(rid,omitir,auto){
  const e=EQ(),r=(e.reportes||{})[rid],v=r&&(e.vaqueros||{})[r.vid];if(!r||!v||r.estado!=='nuevo')return null;
  const ops=opsNuevas(r),om=new Set(omitir||[]);
  const hechos=aplicarOps(v,ops,om),omit=ops.filter(o=>om.has(o.seq)).length;
  const e2=EQ(),R={...(e2.reportes||{})};
  R[rid]={...R[rid],estado:'registrado',reg:hechos,omit,regTs:Date.now(),auto:!!auto};
  guardarEQ({reportes:R,rev:{...(e2.rev||{}),[v.id]:{rid,fecha:r.fecha,reg:hechos,omit,ts:Date.now()}},
    act:actividad({ic:'alta',v:v.id,n:v.nombre,txt:`Registraste el reporte de ${v.nombre} del ${ffc(r.fecha)}`})});
  publicarEstado(true);
  return {reg:hechos,omit};
}

/* ---------- estado para el equipo (sin dinero) ---------- */
const DINERO=/precio|costo|flete|comision|monto|pago|valor|venta|compra/i;
function sinDinero(o){if(Array.isArray(o))return o.map(sinDinero);if(!o||typeof o!=='object')return o;const r={};
  for(const [k,v] of Object.entries(o))r[k]=DINERO.test(k)&&typeof v==='number'?0:DINERO.test(k)&&typeof v==='string'&&/^\d/.test(v)?'':sinDinero(v);return r;}
function estadoEquipo(){
  const e=EQ(),C=calc(),desde=addDias(hoy(),-120),lotes={},raciones={};
  for(const x of C.act)lotes[x.id]=sinDinero(S.lotes[x.id]);
  for(const [k,r] of Object.entries(S.raciones))raciones[k]={nombre:r.nombre,ings:(r.ings||[]).map(g=>({n:g.n,p:g.p})),costoKg:0};
  const items=allItems().filter(i=>i.f>=desde&&lotes[i.lote]&&PERM_TIPO[i.tipo]).map(i=>sinDinero(i));
  const vaqs={},acks={};for(const v of Object.values(e.vaqueros||{})){vaqs[v.id]={n:v.nombre,permisos:v.permisos,estado:v.estado};acks[v.id]=+v.seq||0;}
  const cfg={finca:S.config.finca,ubicacion:S.config.ubicacion,pais:S.config.pais,unidad:S.config.unidad,unidadPrecio:S.config.unidadPrecio,horas:S.config.horas,
    diasSinPesar:S.config.diasSinPesar,metaKg:S.config.metaKg,gdpEsperada:S.config.gdpEsperada,desbaste:S.config.desbaste,
    hechas:S.config.hechas||{},tareas:(S.config.tareas||[]).filter(t=>t.para&&!t.hecho),horaReporte:horaRep()};
  return {v:1,gen:e.gen,lotes,raciones,items,cfg,vaqueros:vaqs,acks,rev:e.rev||{}};
}
async function publicarEstado(forzar){
  const e=EQ();if(!e||!e.id||!activos().length)return null;
  const est=estadoEquipo(),h=N.sha(N.canon(est));
  if(!forzar&&SY.hEst===h&&SY.estado)return SY.estado;
  const f=firmaJ();const s=await N.sellar('estado',{e:e.id,de:'jefe',para:'todos',carga:{...est,ts:Date.now()},clave:e.clave,g:e.gen,firmaSec:f.sec,extra:{r:'estado'}});
  SY.hEst=h;SY.estado=s;SY.estadoPend=1;guardarSY();sincronizarPronto();return s;
}

/* ---------- dar de baja ---------- */
async function darDeBaja(vid){
  const e=EQ(),v=e.vaqueros[vid];if(!v)return;const f=firmaJ(),n=await N.nacl();
  const nueva=N.b64(n.randomBytes(32)),gen=(+e.gen||1)+1;
  const L=e.licencias[v.lic];
  guardarEQ({vaqueros:{...e.vaqueros,[vid]:{...v,estado:'baja',baja:hoy()}},licencias:{...e.licencias,[v.lic]:{...L,estado:'baja',baja:hoy()}},
    claves:{...(e.claves||{}),[e.gen]:e.clave},clave:nueva,gen,act:actividad({ic:'baja',v:vid,n:v.nombre,txt:`Diste de baja a ${v.nombre}`})});
  encolar(await N.sellar('baja',{e:e.id,de:'jefe',para:vid,carga:{baja:true,lic:v.lic},caja:{pub:v.caja,sec:e.caja.sec},firmaSec:f.sec}));
  // la clave nueva a los que siguen: quien se fue ya no puede leer lo que venga
  for(const o of activos())encolar(await N.sellar('clave',{e:e.id,de:'jefe',para:o.id,carga:{clave:nueva,gen},caja:{pub:o.caja,sec:e.caja.sec},firmaSec:f.sec}));
  await publicarEstado(true);closeSheet();toast(`${v.nombre} ya no está en el equipo`,3500);
}
ACTS.eqBaja=el=>{const v=EQ().vaqueros[el.dataset.v];if(!v)return;
  confirmar(`¿Dar de baja a ${esc(v.nombre)}?`,`Su app deja de funcionar y su licencia ${N.fmtLic(v.lic)} queda anulada: no se puede pasar a otra persona. Lo que ya registró se queda en tus datos.`,'Dar de baja',()=>darDeBaja(v.id));};

/* ---------- sincronizar por el servidor ---------- */
let ocupado=null,prontoT=0;
function sincronizarPronto(ms=1500){clearTimeout(prontoT);prontoT=setTimeout(()=>sincronizar(),ms);}
async function sincronizar(){
  if(ocupado)return ocupado;const e=EQ(),base=servidor();if(!e||!e.id||!base||!navigator.onLine&&navigator.onLine!==undefined)return null;
  ocupado=(async()=>{let nuevos=0;ultimosRep=[];
    try{
      const f=firmaJ();if(!f)return;
      if(SY.srv!==base){SY={...SY,srv:base,reg:0,cursor:0,lics:{},miembros:{},estadoPend:1};guardarSY();}
      if(!SY.reg){await N.pedir(base,'/v1/equipo',{e:e.id,firma:f.pub},f.sec);SY.reg=1;guardarSY();}
      const fi=await fichaEq();const lics=[],bajas=[],cs=[];
      for(const L of Object.values(EQ().licencias||{})){const h=N.hashLic(L.c);
        if(L.estado==='baja'){if(SY.lics[L.c]!=='baja'){bajas.push(h);cs.push([L.c,'baja']);}}
        else if(SY.lics[L.c]!==fi.f){lics.push({h,ficha:fi});cs.push([L.c,fi.f]);}}
      if(lics.length||bajas.length){await N.pedir(base,'/v1/licencias',{e:e.id,quien:'jefe',lics,bajas},f.sec);for(const [c,x] of cs)SY.lics[c]=x;guardarSY();}
      for(let i=0;i<10;i++){const r=await N.pedir(base,'/v1/recibir',{e:e.id,quien:'jefe',desde:SY.cursor||0},f.sec);
        for(const x of r.sobres||[])nuevos+=await procesar(x);SY.cursor=r.hasta||SY.cursor;guardarSY();if(!r.mas)break;}
      // después de leer las altas: los aceptados ya pueden mandar sus registros
      const alt=[],baj=[];for(const v of Object.values(EQ().vaqueros||{})){if(v.estado==='activo'&&SY.miembros[v.id]!=='ok')alt.push({vid:v.id,firma:v.firma});if(v.estado==='baja'&&SY.miembros[v.id]!=='baja')baj.push(v.id);}
      if(alt.length||baj.length){await N.pedir(base,'/v1/miembros',{e:e.id,quien:'jefe',altas:alt,bajas:baj},f.sec);for(const a of alt)SY.miembros[a.vid]='ok';for(const b of baj)SY.miembros[b]='baja';guardarSY();}
      await publicarEstado();
      if(SY.estadoPend&&SY.estado){SY.salida=(SY.salida||[]).filter(s=>s.r!=='estado').concat(SY.estado);SY.estadoPend=0;guardarSY();}
      await enviarSalida(base,f);
      SY.ult=Date.now();SY.err='';guardarSY();T.abrir();
      // reportes que llegaron por internet
      const reps=ultimosRep.slice();if(reps.length){const r=EQ().reportes[reps[reps.length-1]];
        toast(reps.length===1?(r.estado==='nuevo'?`Llegó el reporte de ${r.n}`:`Reporte de ${r.n} registrado en tu finca`):`${pl(reps.length,'reporte nuevo','reportes nuevos')}`,4000);}
    }catch(err){SY.err=String(err&&err.message||err);guardarSY();}
    finally{ocupado=null;if(nuevos||location.hash.startsWith('#equipo'))scheduleRender();}
    return nuevos;})();
  return ocupado;
}
// en vivo: el servidor avisa en cuanto un vaquero manda algo y la app lo pide al instante
const T=N.timbre(async()=>{const e=EQ(),base=servidor(),f=firmaJ();if(!e||!e.id||!base||!f||!SY.reg||SY.srv!==base)return null;
  return {base,cuerpo:{e:e.id,quien:'jefe'},firmaSec:f.sec};},()=>sincronizarPronto(150),()=>{if(location.hash.startsWith('#equipo'))scheduleRender();});
ACTS.eqSinc=async()=>{if(!servidor()){toast('Sin servidor: pásale los datos a tu equipo por archivo.',4000);return;}toast('Sincronizando…');const n=await sincronizar();
  toast(SY.err?'No se pudo sincronizar. Revisa tu internet.':n?`Listo: ${pl(n,'mensaje nuevo','mensajes nuevos')}`:'Todo al día',3000);};
// cada vez que cambian tus datos, el equipo recibe el estado nuevo (si tienes vaqueros)
let pubT=0;document.addEventListener('rumentis-pintado',()=>{if(!conEquipo()||!activos().length)return;clearTimeout(pubT);pubT=setTimeout(()=>publicarEstado(),2500);});
// sin conexión en vivo, cada minuto; con ella, solo de vez en cuando por si acaso
setInterval(()=>{if(!document.hidden&&conEquipo()&&servidor()&&!(T.vivo()&&Date.now()-(SY.ult||0)<5*60e3))sincronizar();},60000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&conEquipo()&&servidor())sincronizar();});

/* ---------- por archivo (WhatsApp) ---------- */
ACTS.eqEnviarArchivo=async()=>{
  const e=EQ();if(!e)return;const est=await publicarEstado(true);
  const sobres=(SY.archivo||[]).filter(s=>s.ts>Date.now()-30*864e5).concat(est?[est]:[]);
  if(!sobres.length){toast('Todavía no hay nada que enviar: primero suma a un colaborador.',4000);return;}
  closeSheet();
  N.compartirArchivo(N.nombreArchivo(`actualizacion-${S.config.finca||'finca'}-${hoy()}`),await N.armarArchivo(sobres,'jefe',{ficha:await fichaEq()}),tr('Actualización para el equipo'));
};
async function alRecibir(o){
  await asegurarEquipo();ultimosRep=[];let n=0;for(const s of o.sobres)n+=await procesar(s);
  const reps=ultimosRep.slice(),auto=EQ().autoReg!==false;
  // automático: el reporte queda registrado al abrirlo (lo que no tiene permiso o ya no aplica se deja fuera solo)
  if(auto)for(const rid of reps)registrarReporte(rid,[],true);
  if(reps.length===1){const r=EQ().reportes[reps[0]];toast(auto?`Reporte de ${r.n} registrado en tu finca`:`Reporte de ${r.n}: revísalo y regístralo`,3500);location.hash='#equipo/reporte/'+encodeURIComponent(reps[0]);}
  else if(reps.length){toast(auto?`${pl(reps.length,'reporte registrado','reportes registrados')} en tu finca`:`${pl(reps.length,'reporte nuevo','reportes nuevos')} por registrar`,3500);location.hash='#equipo';}
  else toast(n?`Listo: ${pl(n,'mensaje leído','mensajes leídos')}`:'Nada nuevo en ese archivo',3500);
  scheduleRender();return true;
}
ACTS.eqRecibirArchivo=async()=>{const u=await N.elegirArchivo();if(u)await N.recibirArchivo(u,alRecibir);};
// al tocar un reporte .rumentis en WhatsApp o en el correo se abre la app y llega aquí
N.escucharArchivos(alRecibir);

/* ---------- permisos, tareas y ajustes ---------- */
ACTS.eqPermiso=el=>{const e=EQ(),v=e.vaqueros[el.dataset.v];if(!v)return;const k=el.dataset.k;const p={...v.permisos,[k]:!v.permisos[k]};
  guardarEQ({vaqueros:{...e.vaqueros,[v.id]:{...v,permisos:p}}});el.setAttribute('aria-checked',String(p[k]));publicarEstado();};
ACTS.eqAuto=el=>{const on=!(EQ().auto!==false);guardarEQ({auto:on});el.setAttribute('aria-checked',String(on));};
ACTS.eqVaquero=el=>abrirVaquero(el.dataset.v);
ACTS.eqSolicitud=el=>{const s=(EQ().solicitudes||{})[el.dataset.v];if(!s)return;
  openSheet(shHead(esc(s.nombre),'Solicita acceso')+`<div class="sh-body"><p>Licencia <b data-no-tr>${N.fmtLic(s.lic)}</b>${s.disp?` · ${esc(s.disp)}`:''}</p><p class="hint">Huella de su teléfono: <span data-no-tr>${N.huella(s.firma)}</span></p></div>
    <div class="sh-foot"><button type="button" class="btn" data-act="eqRechazar" data-v="${s.vid}" style="flex:1">Rechazar</button><button type="button" class="btn pri" data-act="eqAceptar" data-v="${s.vid}" style="flex:1">Aceptar</button></div>`);};
/* Tareas: libres (se marcan a mano) o ligadas a una acción, que se cumplen solas cuando el colaborador la anota:
   entregar alimento, pesar o sanidad, en un lote o en cualquiera. */
const ACC={alimento:'Entregar alimento',pesaje:'Pesar',sanidad:'Sanidad'};
function textoAcc(k,lote){const n=lote&&S.lotes[lote]?S.lotes[lote].nombre:'';
  return k==='alimento'?(n?`Entregar alimento a ${n}`:'Entregar alimento'):k==='pesaje'?(n?`Pesar ${n}`:'Pesar un lote'):k==='sanidad'?(n?`Sanidad en ${n}`:'Aplicar sanidad'):'';}
FORMS.eqTarea=({id}={})=>{
  const vs=activos(),C=calc();
  const body=`${q('¿Qué tipo de tarea?',opts('acc',[{v:'alimento',t:'Entregar alimento',s:'se cumple al anotarla'},{v:'pesaje',t:'Pesar',s:'se cumple al anotarlo'},{v:'sanidad',t:'Sanidad',s:'se cumple al anotarla'},{v:'',t:'Otra',s:'se marca a mano'}],'alimento'))}
   ${q('¿En qué lote?',`<select class="in" name="lote"><option value="">Cualquier lote</option>${C.act.map(x=>`<option value="${esc(x.id)}">${esc(x.l.nombre)}</option>`).join('')}</select>`,'Para alimento, pesaje y sanidad.')}
   ${q('¿Qué hay que hacer?',`<input class="in" name="t" placeholder="Revisar bebederos del corral 3…" autocomplete="off">`,'En alimento, pesaje y sanidad puedes dejarlo vacío: se escribe solo.')}
   ${q('¿Para quién?',opts('para',[{v:'todos',t:'Todo el equipo'}].concat(vs.map(v=>({v:v.id,t:v.nombre}))),id||'todos'))}
   ${q('¿Cada cuánto?',`<select class="in" name="rep">${['una','dia','s1','s2','s3','s4','s5'].map(k=>`<option value="${k}">${Agenda.REP[k]}</option>`).join('')}</select>`,'Las diarias y semanales se repiten solas; el avance sale en cada reporte.')}
   ${q('¿Desde cuándo?',`<input class="in" type="date" name="f" value="${hoy()}" min="${hoy()}">`)}`;
  openSheet(shHead('Asignar una tarea',EQT())+formWrap('eqTarea',body,foot('Asignar')));
};
SAVE.eqTarea=f=>{const acc=ACC[fv(f,'acc')]?fv(f,'acc'):'',lote=acc&&S.lotes[fv(f,'lote')]?fv(f,'lote'):'';
  const t=fv(f,'t').trim()||textoAcc(acc,lote);if(!t)return ferr(f,'Escribe qué hay que hacer.');const para=fv(f,'para')||'todos';
  const quien=para==='todos'?'todo el equipo':((EQ().vaqueros||{})[para]||{}).nombre||'';
  const rep=Agenda.REP[fv(f,'rep')]?fv(f,'rep'):'una';
  put('ajustes','finca',{...S.config,tareas:(S.config.tareas||[]).concat({id:uid('t'),t,f:fv(f,'f')||hoy(),hecho:false,para,rep,hechos:[],acc:acc?{k:acc,lote}:null})});closeSheet();
  toast(`Tarea para ${quien}. Les llega con la próxima actualización.`,4000);publicarEstado();};
ACTS.eqTareaBorrar=el=>{put('ajustes','finca',{...S.config,tareas:(S.config.tareas||[]).filter(t=>t.id!==el.dataset.id)});publicarEstado();};
ACTS.eqServidor=()=>{const e=EQ()||{};
  openSheet(shHead('Servidor del equipo',EQT())+formWrap('eqServidor',`${q('Dirección del servidor',`<input class="in" name="s" value="${esc(e.servidor||'')}" placeholder="${esc(CFG.servidorEquipo||'192.168.1.10:8790')}" autocomplete="off" inputmode="url" data-no-tr>`,'La dirección que muestra el servidor de la finca al encenderlo. Sin servidor, el equipo se pasa los datos por archivo.')}`,foot('Guardar')));};
SAVE.eqServidor=f=>{const s=fv(f,'s').trim();if(s&&!N.urlOk(s))return ferr(f,'Esa dirección no sirve. Escribe la que muestra el servidor de la finca, por ejemplo 192.168.1.10:8790.');
  guardarEQ({servidor:s?N.urlOk(s):''});closeSheet();toast('Guardado');T.reiniciar();sincronizarPronto(200);};

/* ---------- pantalla ---------- */
const avatar=(n,cls='')=>`<span class="eq-av ${cls}" aria-hidden="true" data-no-tr>${esc(N.iniciales(n))}</span>`;
const hhmm=ts=>new Date(ts).toTimeString().slice(0,5);
const hoyDe=vid=>allItems().filter(i=>i.f===hoy()&&i.por&&i.por.v===vid).length;
const repDe=(vid,f)=>REP().find(r=>r.vid===vid&&r.fecha===f&&r.estado!=='incluido')||REP().find(r=>r.vid===vid&&r.fecha===f);
const semanaDe=f=>{const L=Agenda.lunesDe(f);return [L,addDias(L,6)];};
const diasConReporte=(vid,f)=>{const [a,b]=semanaDe(f);return new Set(REP().filter(r=>r.vid===vid&&r.fecha>=a&&r.fecha<=b).map(r=>r.fecha)).size;};
const tareasDe=vid=>(S.config.tareas||[]).filter(t=>t.para==='todos'||t.para===vid);
const repTxt=t=>Agenda.REP[t.rep||'una']||'Una vez';
// cómo va su reporte de hoy
function estadoHoy(v){
  const r=repDe(v.id,hoy());
  if(r)return r.estado==='nuevo'?{tono:'y',txt:`Reporte de hoy por revisar, ${hhmm(r.rec)}`,corto:`Por revisar · ${hhmm(r.ts)}`}
    :{tono:'v',txt:'Reporte de hoy registrado',corto:`Reportó · ${hhmm(r.ts)}`};
  if(new Date().getHours()>=horaRep())return {tono:'r',txt:'Reporte de hoy pendiente',corto:'Sin reporte'};
  return {tono:'a',txt:`Reporte de hoy a las ${horaRep()}:00`,corto:`Reporta a las ${horaRep()}:00`};
}
// cada registro del reporte, en palabras, y si se puede registrar
function descOp(v,op){
  const L=id=>nombreLote(id),P=v.permisos||{};
  if(op.k==='item+'){const it=op.it||{};const t0=textoItem(v,it).replace(v.nombre+' ',''),t=t0.charAt(0).toUpperCase()+t0.slice(1);const p=PERM_TIPO[it.tipo];
    if(buscarItem(it.id))return {t,no:'Ya está registrado'};if(!p||!P[p])return {t,no:'Sin permiso para registrarlo'};if(!S.lotes[it.lote])return {t,no:'El lote ya no existe'};
    return {t,f:it.f};}
  if(op.k==='item-')return {t:`Borró un registro${(buscarItem(op.id)||{}).it?' de '+L(buscarItem(op.id).it.lote):''}`};
  if(op.k==='item~')return {t:`Corrigió un registro de ${L((op.it||{}).lote)}`};
  if(op.k==='lote~'){const c=op.campos||{};return {t:c.estado==='cerrado'?`Cerró ${L(op.id)}`:'animales' in c?`Actualizó los animales de ${L(op.id)}`:`Cambió la ración de ${L(op.id)}`};}
  if(op.k==='hecha')return {t:`Hizo: ${op.txt||'una tarea de la agenda'}`,f:op.f};
  if(op.k==='tarea'){const t=(S.config.tareas||[]).find(z=>z.id===op.id);return {t:`Hizo: ${t?t.t:'una tarea asignada'}`,f:op.f,no:t?'':'La tarea ya no existe'};}
  return {t:'Un cambio'};
}
function paginaReporte(rid){
  const e=EQ()||{},r=(e.reportes||{})[rid];
  const back=`<header class="hd">${hdBack('#equipo',EQT(),'<span class="sync"></span>')}`;
  if(!r)return back+`<div class="ttl" style="margin-top:-6px"><h1>Reporte no encontrado</h1></div></header><main class="bd"><p class="hint">Puede que se haya borrado.</p></main>`;
  const v=(e.vaqueros||{})[r.vid]||{id:r.vid,nombre:r.n,permisos:{}},res=r.res||{},nuevo=r.estado==='nuevo';
  const ops=nuevo?opsNuevas(r):[],om=(UI.eqOmit&&UI.eqOmit[rid])||{};
  const filas=ops.map(o=>({o,d:descOp(v,o)})),validas=filas.filter(x=>!x.d.no),aReg=validas.filter(x=>!om[x.o.seq]).length;
  const regs=filas.filter(x=>x.o.k==='item+').length;
  const T=res.tareas||[],hechas=T.filter(t=>t.ok).length;
  // semanales: lo que ya está en la finca más lo de este reporte
  const [lu,do_]=semanaDe(r.fecha);
  const sem=tareasDe(v.id).filter(t=>/^s\d$/.test(t.rep||'')).map(t=>{const p=Agenda.progreso(t,r.fecha);
    const extra=nuevo?ops.filter(o=>o.k==='tarea'&&o.id===t.id&&o.f>=lu&&o.f<=do_&&!(t.hechos||[]).some(x=>x.f===o.f&&x.v===v.id)).length:0;
    return {t:t.t,n:p.n+extra,meta:p.meta};});
  const ck=ok=>`<span class="vq-ck${ok?' ok':''}" aria-hidden="true">${ok?ico('check',2.6):''}</span>`;
  const estado=nuevo?'':r.estado==='incluido'?`<div class="eq-prueba">${ico('check',2.2)}<p>Sus registros van en un reporte más reciente de ${esc(r.n)}.</p></div>`
    :`<div class="eq-hecho">${ico('check',2.4)}<div><b>${r.auto?'Registrado al abrirlo':'Registrado'}</b><span><span>${pl(r.reg||0,'cambio en tu finca','cambios en tu finca')}</span>${r.omit?` · <span>${pl(r.omit,'registro dejado fuera','registros dejados fuera')}</span>`:''}</span></div>
      ${r.regTs>Date.now()-864e5?`<button type="button" class="btn pri sm" data-act="eqEnviarArchivo">Enviar actualización</button>`:''}</div>`;
  return back+`<div class="ttl" style="margin-top:-6px"><span class="eyebrow">Reporte del día</span><h1>${esc(r.n)}</h1><p class="sub">${ffl(r.fecha)} · enviado a las ${hhmm(r.ts)}</p>
    <div class="kpis"><div class="kpi"><b>${nuevo?regs:(r.reg||0)}</b><span>${nuevo?'registros nuevos':'cambios registrados'}</span></div><div class="kpi"><b>${hechas} de ${T.length}</b><span>tareas del día</span></div><div class="kpi"><b>${diasConReporte(v.id,r.fecha)} de 7</b><span>días reportados esta semana</span></div></div></div></header>
  <main class="bd">${estado}
   ${r.nota?`<section class="sec">${secH('Novedades')}<div class="card pad eq-nota"><p>${esc(r.nota)}</p></div></section>`:''}
   <section class="sec">${secH('Tareas del día',T.length)}${T.length?`<div class="card rows">${T.map(t=>`<div class="row vq-rt">${ck(t.ok)}<div class="tx"><b>${esc(t.t)}</b><span>${t.ok?'Hecha':'Pendiente'}</span></div></div>`).join('')}</div>`:`<p class="hint">No tenía tareas para ese día.</p>`}</section>
   ${sem.length?`<section class="sec">${secH('Tareas de la semana')}<div class="card rows">${sem.map(x=>`<div class="row vq-rt">${ck(x.n>=x.meta)}<div class="tx"><b>${esc(x.t)}</b><span>${x.n} de ${x.meta} esta semana</span></div><div class="eq-barra" aria-hidden="true"><i style="width:${Math.min(100,Math.round(x.n/x.meta*100))}%"></i></div></div>`).join('')}</div></section>`:''}
   ${nuevo?`<section class="sec">${secH('Para registrar en tu finca',validas.length)}${filas.length?`<p class="hint" style="margin-top:-4px">Quita la marca de lo que no quieras registrar.</p><div class="card rows">${filas.map(({o,d})=>d.no
      ?`<div class="row eq-op off">${ck(false)}<div class="tx"><b>${esc(d.t)}</b><span>${d.no}</span></div></div>`
      :`<button type="button" class="row eq-op" data-act="eqOpTog" data-r="${esc(rid)}" data-s="${o.seq}" aria-pressed="${!om[o.seq]}">${ck(!om[o.seq])}<div class="tx"><b>${esc(d.t)}</b><span>${d.f?ffc(d.f):''}</span></div></button>`).join('')}</div>`
      :`<p class="hint">Este reporte no trae registros nuevos.</p>`}</section>
   <div class="eq-regbar"><button type="button" class="btn" data-act="eqRepLuego">Ahora no</button><button type="button" class="btn pri" data-act="eqRegistrar" data-r="${esc(rid)}">${aReg?`Registrar ${pl(aReg,'cambio','cambios')}`:'Marcar como revisado'}</button></div>`
   :r.estado==='registrado'&&(r.ops||[]).length?`<section class="sec">${secH('Lo que se registró',(r.ops||[]).length)}<div class="card rows">${(r.ops||[]).map(o=>{
      const d=descOp(v,o),ok=o.k!=='item+'||!!buscarItem((o.it||{}).id);
      return `<div class="row vq-rt">${ck(ok)}<div class="tx"><b>${esc(d.t)}</b><span>${ok?(d.f?ffc(d.f):''):'Quedó fuera'}</span></div></div>`;}).join('')}</div></section>`:''}
  </main>`;
}
ACTS.eqOpTog=el=>{const r=el.dataset.r,sq=el.dataset.s;UI.eqOmit=UI.eqOmit||{};const m={...(UI.eqOmit[r]||{})};if(m[sq])delete m[sq];else m[sq]=1;UI.eqOmit[r]=m;render();};
ACTS.eqRepLuego=()=>{location.hash='#equipo';};
ACTS.eqRegistrar=el=>{const rid=el.dataset.r,om=Object.keys((UI.eqOmit||{})[rid]||{}).map(Number);
  const x=registrarReporte(rid,om);if(!x)return;if(UI.eqOmit)delete UI.eqOmit[rid];
  const r=EQ().reportes[rid],quedan=porRegistrar().length;
  openSheet(shHead('Reporte registrado',EQT())+`<div class="sh-body"><div class="eq-prueba eq-ok">${ico('check',2.2)}<p><b data-no-tr>${esc(r.n)}</b><br><span>${pl(x.reg,'cambio en tu finca','cambios en tu finca')}</span>${x.omit?` · <span>${pl(x.omit,'registro dejado fuera','registros dejados fuera')}</span>`:''}</p></div>
    <p>Envía la actualización al equipo: les llega la confirmación, sus tareas y los lotes al día.</p>${quedan?`<p class="hint">Te ${quedan===1?'queda':'quedan'} ${pl(quedan,'reporte','reportes')} por registrar.</p>`:''}</div>
    <div class="sh-foot"><button type="button" class="btn" data-act="eqCerrarReg" style="flex:1">Luego</button><button type="button" class="btn pri" data-act="eqEnviarArchivo" style="flex:1.6">Enviar actualización</button></div>`);};
ACTS.eqCerrarReg=()=>closeSheet();
ACTS.eqReporteVer=el=>{location.hash='#equipo/reporte/'+encodeURIComponent(el.dataset.r);};
document.addEventListener('change',ev=>{const t=ev.target;if(!t||!t.matches||!t.matches('[data-eqhora]'))return;
  guardarEQ({horaReporte:+t.value});publicarEstado(true);toast(`Reporte a las ${t.value}:00. Envía la actualización para que le llegue a tu equipo.`,4500);});
function abrirVaquero(vid){
  const e=EQ(),v=(e.vaqueros||{})[vid];if(!v)return;
  const reps=REP().filter(r=>r.vid===vid).slice(0,7);
  const tareas=tareasDe(vid).filter(t=>(t.rep||'una')!=='una'||!t.hecho);
  openSheet(shHead(esc(v.nombre),'Colaborador')+`<div class="sh-body eq-vq">
    <div class="eq-vq-h">${avatar(v.nombre,'xl')}<div><b data-no-tr>${N.fmtLic(v.lic)}</b><span>${v.estado==='activo'?`En el equipo desde el ${ffc(v.alta)}`:`Dado de baja el ${ffc(v.baja||hoy())}`}</span><span>Esta semana: ${diasConReporte(vid,hoy())} de 7 días con reporte</span></div></div>
    <p class="rh">Reportes</p>${reps.length?`<div class="card rows">${reps.map(r=>`<button type="button" class="row" data-act="eqReporteVer" data-r="${esc(r.rid)}"><div class="tx"><b>${ffc(r.fecha)}, ${hhmm(r.ts)}</b><span>${r.estado==='nuevo'?'Por registrar':r.estado==='incluido'?'Incluido en uno más reciente':'Registrado'}</span></div>${ico('chev')}</button>`).join('')}</div>`:`<p class="hint">Todavía no envía reportes.</p>`}
    ${v.estado==='activo'?`<p class="rh">Qué puede registrar</p><div class="card rows eq-perm">${PERM.map(([k,t])=>`<div class="row"><div class="tx"><b>${t}</b></div><button type="button" class="tog" role="switch" aria-checked="${!!(v.permisos||{})[k]}" aria-label="${t}" data-act="eqPermiso" data-v="${vid}" data-k="${k}"><span></span></button></div>`).join('')}</div>`:''}
    <p class="rh">Tareas asignadas</p>${tareas.length?`<div class="card rows">${tareas.map(t=>{const p=Agenda.progreso(t);return `<div class="row"><div class="tx"><b>${esc(t.t)}</b><span><span>${t.para==='todos'?'Todo el equipo':'Solo para '+esc(v.nombre)}</span> · <span>${repTxt(t)}</span>${/^s\d$/.test(t.rep||'')?` · <span>${p.n} de ${p.meta} esta semana</span>`:''}</span></div></div>`;}).join('')}</div>`:`<p class="hint">Sin tareas asignadas.</p>`}
    ${v.estado==='activo'?`<button type="button" class="btn full" data-act="f" data-f="eqTarea" data-id="${vid}">${ico('nuevo')}Asignarle una tarea</button>`:''}
    <p class="hint">Huella de su teléfono: <span data-no-tr>${N.huella(v.firma)}</span>${v.disp?` · ${esc(v.disp)}`:''}</p></div>
    ${v.estado==='activo'?`<div class="sh-foot"><button type="button" class="btn danger" data-act="eqBaja" data-v="${vid}" style="flex:1">Dar de baja</button></div>`:''}`);
}
const ICA={alimento:'alimento',pesaje:'pesaje',sanidad:'sanidad',baja:'alerta',lic:'recibo',alta:'estrella',tarea:'lista',borrar:'nota',reporte:'recibo'};
function filaAct(a){return `<div class="row eq-act"><span class="mas-ic t-${a.ic==='baja'||a.ic==='borrar'?'r':a.ic==='alimento'?'y':a.ic==='lic'||a.ic==='alta'?'v':'a'}">${icono(ICA[a.ic]||'info','i3')}</span><div class="tx"><b>${esc(a.txt)}</b><span>${N.haceCuanto(a.ts)}</span></div></div>`;}
function botonComprar(txt){const p=gratis()?'':precioLic();return `<button type="button" class="btn pri full eq-comprar" data-act="eqComprar">${ico('nuevo')}<span>${txt}</span>${p?`<span data-no-tr> · ${esc(p)}</span>`:''}</button>`;}
function filaRep(r){const T=(r.res||{}).tareas||[],n=(r.ops||[]).filter(o=>o.k==='item+').length;
  return `<a class="row eq-v" href="#equipo/reporte/${encodeURIComponent(r.rid)}">${avatar(r.n,r.estado==='nuevo'?'c':'')}<div class="tx"><b>${esc(r.n)}</b><span><span>${ffc(r.fecha)}, ${hhmm(r.ts)}</span> · <span>${pl(n,'registro','registros')}</span>${T.length?` · <span>${T.filter(t=>t.ok).length} de ${T.length} tareas</span>`:''}${r.estado==='nuevo'?'':r.estado==='incluido'?' · <span>incluido</span>':' · <span>registrado</span>'}</span></div>${ico('chev')}</a>`;}
// ---------- la pestaña Equipo ----------
const TONO_ACC={alimento:'y',pesaje:'a',sanidad:'v'},IC_ACC={alimento:'alimento',pesaje:'pesaje',sanidad:'jeringa'};
const pill=(tono,txt)=>`<span class="eq-pill t-${tono}">${txt}</span>`;
function tarjetaColab(v){
  const s=estadoHoy(v),r=repDe(v.id,hoy()),T=r?((r.res||{}).tareas||[]):[],n=r?(r.ops||[]).filter(o=>o.k==='item+').length:0;
  return `<button type="button" class="eq-colab" data-act="eqVaquero" data-v="${v.id}">${avatar(v.nombre,'eq-ring t-'+s.tono)}
    <div class="eq-colab-tx"><b>${esc(v.nombre)}</b>${pill(s.tono,s.corto)}
    <span class="eq-colab-d">${r?`<span>${T.filter(t=>t.ok).length} de ${T.length} tareas</span> · <span>${pl(n,'registro','registros')}</span>`:`<span>Esta semana: ${diasConReporte(v.id,hoy())} de 7 días con reporte</span>`}</span></div>${ico('chev')}</button>`;
}
function filaTareaEq(t,nombreDe){
  const p=Agenda.progreso(t),a=t.acc&&t.acc.k,sem=/^s\d$/.test(t.rep||'');
  const estado=(t.rep||'una')==='una'?(t.hecho?pill('v','Hecha'):pill('a',ffc(t.f))):t.rep==='dia'?(p.hoy?pill('v','Hecha hoy'):pill('a','Hoy pendiente')):pill(p.completa?'v':'a',`${p.n} de ${p.meta}`);
  return `<div class="row eq-tarea"><span class="mas-ic t-${a?TONO_ACC[a]:'t'}">${icono(a?IC_ACC[a]:'lista','i3')}</span><div class="tx"><b>${esc(t.t)}</b>
    <span class="eq-chips"><span class="eq-chip">${esc(nombreDe(t.para))}</span><span class="eq-chip">${repTxt(t)}</span>${a?'<span class="eq-chip eq-auto">Se cumple sola</span>':''}</span>
    ${sem?`<span class="eq-barra ancha" aria-hidden="true"><i style="width:${Math.min(100,Math.round(p.n/p.meta*100))}%"></i></span>`:''}</div>
    <div class="eq-tarea-d">${estado}<button type="button" class="del" data-act="eqTareaBorrar" data-id="${t.id}" aria-label="Quitar tarea">${ico('papelera')}</button></div></div>`;
}
FORMS.eqAjustes=()=>{const e=EQ()||{},hr=horaRep();
  openSheet(shHead('Ajustes del equipo',EQT())+`<div class="sh-body eq-aj">
    <div class="q"><label for="eqHora">Hora del reporte</label><select class="in" id="eqHora" data-eqhora>${[12,13,14,15,16,17,18,19,20,21].map(x=>`<option value="${x}"${x===hr?' selected':''}>${x}:00</option>`).join('')}</select><small class="hint">A esa hora a tu personal le llega un aviso con el reporte listo para enviar.</small></div>
    <div class="card rows">
     <div class="row eq-tg"><div class="tx"><b>Registrar los reportes al abrirlos</b><span>Si lo apagas, cada reporte espera a que lo revises y toques Registrar.</span></div><button type="button" class="tog" role="switch" aria-checked="${e.autoReg!==false}" aria-label="Registrar al abrir" data-act="eqAutoReg"><span></span></button></div>
     <div class="row eq-tg"><div class="tx"><b>Aceptar solicitudes de acceso solas</b><span>Si lo apagas, te pregunto antes de dejar entrar a alguien.</span></div><button type="button" class="tog" role="switch" aria-checked="${e.auto!==false}" aria-label="Aceptar solas" data-act="eqAuto"><span></span></button></div></div>
    <div class="eq-arch-info"><p><b>.rumentis</b><span>Reportes de tu personal: en WhatsApp o en el correo tócalo y elige Rumentis.</span></p>
     <p><b>.campo</b><span>Tu actualización para el equipo: tareas, lotes y la confirmación de lo registrado. Se abre en Rumentis Campo.</span></p>
     <small class="hint">Cada archivo se abre una sola vez.</small></div></div>`);};
ACTS.eqAjustes=()=>FORMS.eqAjustes();
ACTS.eqAutoReg=el=>{const on=!((EQ()||{}).autoReg!==false);guardarEQ({autoReg:on});el.setAttribute('aria-checked',String(on));};
ACTS.eqLicencias=()=>{const L=Object.values((EQ()||{}).licencias||{}).filter(l=>l.estado==='libre');
  openSheet(shHead('Licencias sin usar',EQT())+`<div class="sh-body">${L.length?`<div class="card rows">${L.map(l=>`<button type="button" class="row eq-l" data-act="eqLicencia" data-c="${l.c}"><span class="mas-ic t-v">${icono('recibo','i3')}</span><div class="tx"><b data-no-tr>${N.fmtLic(l.c)}</b><span>Toca para ver el QR y enviarla</span></div>${ico('chev')}</button>`).join('')}</div>`:`<p class="hint">No tienes licencias libres.</p>`}
    ${botonComprar(gratis()?'Crear otra licencia':'Comprar otra licencia')}</div>`);};
PAGES.equipo=(sub,id)=>{
  if(sub==='reporte')return paginaReporte(id);
  const e=EQ()||{},L=Object.values(e.licencias||{}),V=Object.values(e.vaqueros||{}),act=V.filter(v=>v.estado==='activo'),libres=L.filter(l=>l.estado==='libre');
  const sols=Object.values(e.solicitudes||{}),pend=porRegistrar(),hist=REP().filter(r=>r.estado!=='nuevo').slice(0,UI.eqHist?40:5);
  const conHoy=act.filter(v=>repDe(v.id,hoy())).length;
  const hd=`<header class="hd">${hdTop('<span class="sync"></span>')}<div class="ttl"><span class="eyebrow">${esc(S.config.finca||'Mi engorde')}</span><h1>${EQT()}</h1><p class="sub">Tu personal registra en Rumentis Campo y te envía su reporte del día.</p>
    ${L.length?`<div class="kpis"><div class="kpi"><b>${act.length}</b><span>${act.length===1?'colaborador':'colaboradores'}</span></div><div class="kpi"><b>${conHoy} de ${act.length}</b><span>reportes de hoy</span></div><div class="kpi"><b>${horaRep()}:00</b><span>hora del reporte</span></div></div>`:''}</div></header>`;
  const aviso=CFG.prueba?`<div class="eq-prueba">${ico('check',2.2)}<p><b>App de prueba.</b> Las licencias se crean sin costo. Tu personal usa <b>Campo Prueba</b>.</p></div>`
    :esDueno()?`<div class="eq-prueba">${ico('check',2.2)}<p><b>Código maestro activo.</b> Tus licencias se crean sin costo y sirven en Rumentis Campo.</p></div>`:'';
  if(!L.length)return hd+`<main class="bd">${aviso}<section class="sec"><div class="card pad eq-intro">
    <div class="eq-ilus" aria-hidden="true">${avatar('Juan Pérez')}${avatar('María López','b')}${avatar('Pedro Díaz','c')}</div>
    <h2>Suma a tu personal</h2>
    <ul class="eq-lista"><li>${ico('check',2.4)}<span>Cada colaborador usa <b>Rumentis Campo</b> en su teléfono: gratis en Google Play.</span></li>
     <li>${ico('check',2.4)}<span>Anota entregas de alimento, pesajes, sanidad y muertes; sus tareas se cumplen solas al anotarlas.</span></li>
     <li>${ico('check',2.4)}<span>Al terminar el día te envía su reporte por WhatsApp o correo y, al abrirlo, queda registrado en tu finca.</span></li>
     <li>${ico('check',2.4)}<span>No ve tus finanzas, precios ni a Rumi.</span></li></ul>
    <p class="hint">Cada licencia se paga una sola vez y es para una persona. Compras las que necesites.</p>
    ${botonComprar(gratis()?'Crear mi primera licencia':'Comprar mi primera licencia')}</div></section>
    ${!gratis()?`<p class="hint eq-dueno"><button type="button" class="lnk" data-act="f" data-f="eqDueno">Tengo un código maestro</button></p>`:''}</main>`;
  const tareas=(S.config.tareas||[]).filter(t=>t.para&&((t.rep||'una')!=='una'||!t.hecho));
  const nombreDe=x=>x==='todos'?'Todo el equipo':((e.vaqueros||{})[x]||{}).nombre||'';
  return hd+`<main class="bd">${aviso}
   ${servidor()?`<p class="eq-red${SY.err?' off':''}"><i></i>${SY.err?'Sin conexión: se reintenta solo. Mientras, también sirven los archivos.':'Conectado por internet: los registros y reportes llegan solos.'}</p>`:''}
   <div class="eq-acciones"><button type="button" class="btn pri" data-act="eqEnviarArchivo">${ico('send')}Enviar actualización</button><button type="button" class="btn" data-act="eqRecibirArchivo">Abrir reporte</button><button type="button" class="btn eq-gear" data-act="eqAjustes" aria-label="Ajustes del equipo">${mico('config')}</button></div>
   ${pend.length?`<section class="sec">${secH('Por revisar',pend.length)}<div class="card rows eq-pend">${pend.map(filaRep).join('')}</div></section>`:''}
   ${sols.length?`<section class="sec">${secH('Solicitudes de acceso',sols.length)}<div class="card rows">${sols.map(s=>`<button type="button" class="row eq-v" data-act="eqSolicitud" data-v="${s.vid}">${avatar(s.nombre,'c')}<div class="tx"><b>${esc(s.nombre)}</b><span>Licencia <span data-no-tr>${N.fmtLic(s.lic)}</span></span></div>${ico('chev')}</button>`).join('')}</div></section>`:''}
   <section class="sec">${secH('Tu equipo hoy',act.length)}<div class="eq-colabs">${act.map(tarjetaColab).join('')}
    <button type="button" class="eq-colab eq-sumar" data-act="${libres.length?'eqLicencias':'eqComprar'}"><span class="eq-mas">${ico('nuevo')}</span><div class="eq-colab-tx"><b>Sumar a alguien</b><span class="eq-colab-d">${libres.length?pl(libres.length,'licencia sin usar','licencias sin usar'):gratis()?'Crear una licencia':`Comprar una licencia${precioLic()?` · <span data-no-tr>${esc(precioLic())}</span>`:''}`}</span></div>${ico('chev')}</button></div></section>
   <section class="sec">${secH('Tareas',tareas.length,lnkB('f','Asignar','data-f="eqTarea"'))}${tareas.length?`<div class="card rows">${tareas.map(t=>filaTareaEq(t,nombreDe)).join('')}</div>`:`<button type="button" class="card pad eq-vacio" data-act="f" data-f="eqTarea"><span class="mas-ic t-y">${icono('lista','i3')}</span><span><b>Asigna la primera tarea</b><span>De una vez, diaria o semanal. Las de alimento, pesaje y sanidad se cumplen solas.</span></span></button>`}</section>
   ${hist.length?`<section class="sec">${secH('Reportes')}<div class="card rows">${hist.map(filaRep).join('')}</div>${!UI.eqHist&&REP().filter(r=>r.estado!=='nuevo').length>5?`<button type="button" class="lnk" data-act="eqHistMas">Ver todos</button>`:''}</section>`:''}
   ${(e.act||[]).length?`<section class="sec">${secH('Actividad')}<div class="card rows">${(e.act||[]).slice(0,UI.eqAct?60:5).map(filaAct).join('')}</div>${(e.act||[]).length>5&&!UI.eqAct?`<button type="button" class="lnk" data-act="eqActMas">Ver más</button>`:''}</section>`:''}
   ${!gratis()?`<p class="hint eq-dueno"><button type="button" class="lnk" data-act="f" data-f="eqDueno">Tengo un código maestro</button></p>`:''}
  </main>`;
};
ACTS.eqActMas=()=>{UI.eqAct=1;render();};
ACTS.eqHistMas=()=>{UI.eqHist=1;render();};
FORMS.eqDueno=()=>{openSheet(shHead('Código maestro',EQT())+formWrap('eqDueno',`${q('Código',`<input class="in vq-lic" name="c" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="RD-XXXX-XXXX-XXXX-XXXX-XXXX" data-no-tr>`,'Solo para el titular de Rumentis: con él las licencias se crean sin costo.')}`,foot('Activar')));};
SAVE.eqDueno=async f=>{const c=String(fv(f,'c')||'').toUpperCase().replace(/[OÓ]/g,'0').replace(/[IÍL]/g,'1').replace(/[^0-9A-Z]/g,'').replace(/^RD/,'');
  if(N.sha('RUMENTIS-DUENO|'+c)!==HUELLA_DUENO)return ferr(f,'Ese código no es válido.');
  await asegurarEquipo();guardarEQ({dueno:true,act:actividad({ic:'alta',txt:'Activaste el código maestro'})});closeSheet();toast('Código maestro activado: tus licencias no se cobran.',4000);};

/* ---------- pestaña en la barra y fila en Más ---------- */
function pestana(){
  const nav=$('nav.bottom .in');if(!nav)return;let a=nav.querySelector('[data-nav="equipo"]');const on=true;   // siempre: aquí se compra la primera licencia
  if(on&&!a){a=document.createElement('a');a.href='#equipo';a.dataset.nav='equipo';a.innerHTML=ico('equipo')+`<span>${EQT()}</span>`;nav.insertBefore(a,nav.querySelector('[data-nav="mas"]'));}
  else if(!on&&a)a.remove();
  nav.classList.toggle('ocho',!!(on&&nav.children.length>7));
  const r=route();const cur=r.p==='equipo';if(a){if(cur)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');}
}
document.addEventListener('rumentis-pintado',pestana);
const _mas=PAGES.mas;
PAGES.mas=(sub,...r)=>{const h=_mas(sub,...r);if(sub)return h;
  const e=EQ()||{},n=Object.values(e.vaqueros||{}).filter(v=>v.estado==='activo').length;
  const pend=porRegistrar().length;
  const fila=`<section class="sec">${secH(EQT())}<div class="card rows">${masFila(ico('equipo'),'Equipo de trabajo',pend?pl(pend,'reporte por registrar','reportes por registrar'):n?pl(n,'colaborador activo','colaboradores activos'):'Suma a tu personal con su propia app','href="#equipo"','t-v')}</div></section>`;
  const i=h.indexOf('<section class="sec"><div class="sec-h"><div><h2>Datos y personalización');return i>0?h.slice(0,i)+fila+h.slice(i):h+fila;};

/* ---------- arranque ---------- */
if(N.pagosHay()){try{Pagos.iniciar(CFG.productoLicencia);}catch(e){}}
if(conEquipo()&&servidor())setTimeout(()=>sincronizar(),2500);
window.Equipo={alRecibir,registrarReporte,porRegistrar,REP,vivo:()=>T.vivo(),sincronizar,publicarEstado,estadoEquipo,procesar,aplicarOps,crearLicencia,asegurarEquipo,fichaEq,abrirLicencia,darDeBaja,SY:()=>SY};
pestana();if(route().p==='equipo'||route().p==='mas')render();
})();
