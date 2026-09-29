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
  return tr(`Hola, te doy una licencia de Rumentis Vaquero para trabajar con el equipo de ${S.config.finca||'la finca'}.`)+'\n\n'+
    tr('1. Descarga la app (gratis):')+' '+CFG.tiendaVaquero+'\n'+tr('2. Ábrela, escribe tu nombre y esta licencia:')+' '+N.fmtLic(c)+'\n\n'+
    tr('O abre este enlace desde tu teléfono:')+' '+url;
}

/* ---------- compra con Google Play ---------- */
ACTS.eqComprar=async()=>{
  await asegurarEquipo();
  if(esDueno()&&!CFG.prueba){
    openSheet(shHead('Licencia de dueño',EQT())+`<div class="sh-body"><div class="eq-prueba">${ico('check',2.2)}<p><b>Modo dueño:</b> la licencia se crea al instante, sin cobrar, y sirve en Rumentis Vaquero como cualquier otra.</p></div></div>
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
    SY.pend=null;guardarSY();toast('¡Licencia lista! Compártela con tu vaquero.',4000);
    if(!location.hash.startsWith('#equipo'))location.hash='#equipo';setTimeout(()=>abrirLicencia(c),450);
  }
  // consumida: así se puede comprar otra (la licencia ya quedó guardada)
  try{Pagos.consumir(o.token);}catch(err){}
});

/* ---------- licencia: QR, copiar y compartir ---------- */
async function abrirLicencia(c){
  const fi=await fichaEq(),url=N.enlace(fi,c);let svg='';try{svg=await N.qrSvg(url);}catch(e){}
  openSheet(shHead('Licencia para tu vaquero',EQT())+`<div class="sh-body eq-lic">
    ${svg?`<div class="eq-qr">${svg}</div>`:''}
    <div class="eq-cod"><span>Licencia</span><b data-no-tr>${N.fmtLic(c)}</b></div>
    <ol class="eq-pasos"><li>Tu vaquero escanea este código con la cámara de su teléfono y descarga <b>Rumentis Vaquero</b>, gratis en Google Play.</li>
    <li>Al abrirla escribe su nombre y la licencia, o toca <b>Escanear QR</b>.</li><li>Listo: aparece en Equipo y te llega lo que registra.</li></ol>
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
  const c=N.normLic(carga.lic),L=(e.licencias||{})[c],vid=N.idDe(s.j),nombre=String(carga.nombre||'').trim().slice(0,60)||'Vaquero';
  const rechazar=async m=>{const f=firmaJ();encolar(await N.sellar('rechazo',{e:e.id,de:'jefe',para:vid,carga:{ok:false,motivo:m,lic:c},caja:{pub:s.k,sec:e.caja.sec},firmaSec:f.sec}));};
  if(!L)return rechazar('Esa licencia no es de este equipo.');
  if(L.estado==='baja')return rechazar('Esa licencia fue dada de baja.');
  if(L.estado==='activa'){if(L.vid===vid)return bienvenida(vid);return rechazar('Esa licencia ya la usa otra persona.');}
  const sol={vid,nombre,lic:c,firma:s.j,caja:s.k,disp:String(carga.disp||'').slice(0,60),ts:Date.now()};
  if(e.auto===false){if(!(e.solicitudes||{})[vid]){guardarEQ({solicitudes:{...(e.solicitudes||{}),[vid]:sol},act:actividad({ic:'alta',v:vid,n:nombre,txt:`${nombre} quiere entrar con la licencia ${N.fmtLic(c)}`})});toast(`${nombre} quiere entrar al equipo`,4000);}return;}
  await aceptar(sol);
}
async function aceptar(sol){
  const e=EQ();const L=e.licencias[sol.lic];if(!L||L.estado!=='libre')return;
  const sols={...(e.solicitudes||{})};delete sols[sol.vid];
  const v={id:sol.vid,nombre:sol.nombre,lic:sol.lic,firma:sol.firma,caja:sol.caja,disp:sol.disp,alta:hoy(),ts:Date.now(),permisos:{...PERM_DEF},estado:'activo',seq:0};
  guardarEQ({vaqueros:{...e.vaqueros,[v.id]:v},licencias:{...e.licencias,[sol.lic]:{...L,estado:'activa',vid:v.id,nombre:v.nombre,activada:hoy()}},solicitudes:sols,
    act:actividad({ic:'alta',v:v.id,n:v.nombre,txt:`${v.nombre} entró al equipo con la licencia ${N.fmtLic(sol.lic)}`})});
  toast(`${v.nombre} entró al equipo`,3500);
  await bienvenida(v.id);await publicarEstado(true);
}
async function bienvenida(vid){
  const e=EQ(),v=e.vaqueros[vid],L=e.licencias[v.lic],f=firmaJ();
  const real=L.compra&&!L.compra.prueba&&L.compra.json?{json:L.compra.json,firma:L.compra.firma}:null;
  const carga={ok:true,vid,nombre:v.nombre,lic:v.lic,clave:e.clave,gen:e.gen,permisos:v.permisos,finca:S.config.finca||'',compra:real,prueba:!!(L.compra&&L.compra.prueba)};
  encolar(await N.sellar('bienvenida',{e:e.id,de:'jefe',para:vid,carga,caja:{pub:v.caja,sec:e.caja.sec},firmaSec:f.sec}));
}
ACTS.eqAceptar=async el=>{const s=(EQ().solicitudes||{})[el.dataset.v];if(s){await aceptar(s);closeSheet();}};
ACTS.eqRechazar=async el=>{const e=EQ(),s=(e.solicitudes||{})[el.dataset.v];if(!s)return;const f=firmaJ();
  encolar(await N.sellar('rechazo',{e:e.id,de:'jefe',para:s.vid,carga:{ok:false,motivo:'Tu jefe no aceptó la solicitud.',lic:s.lic},caja:{pub:s.caja,sec:e.caja.sec},firmaSec:f.sec}));
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
function aplicarOps(v,ops){
  const e=EQ();let seq=+v.seq||0,cambios=0;const act=[];const P=v.permisos||{};
  const cfgNueva={...S.config};let cfgCambio=false;
  for(const op of [...ops].sort((a,b)=>a.seq-b.seq)){
    if(!(op.seq>seq))continue;seq=op.seq;
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
      else if(op.k==='tarea'&&P.tareas){cfgNueva.tareas=(cfgNueva.tareas||[]).map(t=>t.id===op.id?{...t,hecho:true,hechoPor:v.nombre,hechoF:hoy()}:t);cfgCambio=true;
        const t=(S.config.tareas||[]).find(z=>z.id===op.id);act.push({ic:'tarea',v:v.id,n:v.nombre,txt:`${v.nombre} marcó hecha: ${t?t.t:'una tarea'}`});}
    }catch(err){}
  }
  const vs={...EQ().vaqueros,[v.id]:{...v,seq,ts:Date.now()}};
  let a=(cfgCambio?cfgNueva:S.config).equipo.act||[];for(const x of act)a=[{ts:Date.now(),...x}].concat(a);
  put('ajustes','finca',{...(cfgCambio?cfgNueva:S.config),equipo:{...EQ(),vaqueros:vs,act:a.slice(0,250)}});
  if(act.length)toast(act.length===1?act[0].txt:`${v.nombre}: ${pl(act.length,'registro nuevo','registros nuevos')}`,3500);
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
  if(s.t==='alta')await procesarAlta(s);else if(s.t==='ops')await procesarOps(s);else return 0;
  return 1;
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
    hechas:S.config.hechas||{},tareas:(S.config.tareas||[]).filter(t=>t.para&&!t.hecho)};
  return {v:1,gen:e.gen,lotes,raciones,items,cfg,vaqueros:vaqs,acks};
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
  ocupado=(async()=>{let nuevos=0;
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
      SY.ult=Date.now();SY.err='';guardarSY();
    }catch(err){SY.err=String(err&&err.message||err);guardarSY();}
    finally{ocupado=null;if(location.hash.startsWith('#equipo'))scheduleRender();}
    return nuevos;})();
  return ocupado;
}
ACTS.eqSinc=async()=>{if(!servidor()){toast('Sin servidor: pásale los datos a tu equipo por archivo.',4000);return;}toast('Sincronizando…');const n=await sincronizar();
  toast(SY.err?'No se pudo sincronizar. Revisa tu internet.':n?`Listo: ${pl(n,'mensaje nuevo','mensajes nuevos')}`:'Todo al día',3000);};
// cada vez que cambian tus datos, el equipo recibe el estado nuevo (si tienes vaqueros)
let pubT=0;document.addEventListener('rumentis-pintado',()=>{if(!conEquipo()||!activos().length)return;clearTimeout(pubT);pubT=setTimeout(()=>publicarEstado(),2500);});
setInterval(()=>{if(!document.hidden&&conEquipo()&&servidor())sincronizar();},60000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&conEquipo()&&servidor())sincronizar();});

/* ---------- por archivo (WhatsApp) ---------- */
ACTS.eqEnviarArchivo=async()=>{
  const e=EQ();if(!e)return;const est=await publicarEstado(true);
  const sobres=(SY.archivo||[]).filter(s=>s.ts>Date.now()-30*864e5).concat(est?[est]:[]);
  if(!sobres.length){toast('Todavía no hay nada que mandar: primero activa a un vaquero.',4000);return;}
  N.compartirArchivo(N.nombreArchivo(`equipo-${S.config.finca||'finca'}-${hoy()}`),await N.armarArchivo(sobres,'jefe',{ficha:await fichaEq()}),tr('Datos para el equipo'));
};
async function alRecibir(o){
  await asegurarEquipo();let n=0;for(const s of o.sobres)n+=await procesar(s);
  toast(n?`Listo: ${pl(n,'mensaje leído','mensajes leídos')}`:'Nada nuevo en ese archivo',3500);scheduleRender();return true;
}
ACTS.eqRecibirArchivo=async()=>{const u=await N.elegirArchivo();if(u)await N.recibirArchivo(u,alRecibir);};
// al tocar un archivo .rumentis en WhatsApp se abre la app y llega aquí
N.escucharArchivos(alRecibir);

/* ---------- permisos, tareas y ajustes ---------- */
ACTS.eqPermiso=el=>{const e=EQ(),v=e.vaqueros[el.dataset.v];if(!v)return;const k=el.dataset.k;const p={...v.permisos,[k]:!v.permisos[k]};
  guardarEQ({vaqueros:{...e.vaqueros,[v.id]:{...v,permisos:p}}});el.setAttribute('aria-checked',String(p[k]));publicarEstado();};
ACTS.eqAuto=el=>{const on=!(EQ().auto!==false);guardarEQ({auto:on});el.setAttribute('aria-checked',String(on));};
ACTS.eqVaquero=el=>abrirVaquero(el.dataset.v);
ACTS.eqSolicitud=el=>{const s=(EQ().solicitudes||{})[el.dataset.v];if(!s)return;
  openSheet(shHead(esc(s.nombre),'Quiere entrar al equipo')+`<div class="sh-body"><p>Licencia <b data-no-tr>${N.fmtLic(s.lic)}</b>${s.disp?` · ${esc(s.disp)}`:''}</p><p class="hint">Huella de su teléfono: <span data-no-tr>${N.huella(s.firma)}</span></p></div>
    <div class="sh-foot"><button type="button" class="btn" data-act="eqRechazar" data-v="${s.vid}" style="flex:1">Rechazar</button><button type="button" class="btn pri" data-act="eqAceptar" data-v="${s.vid}" style="flex:1">Aceptar</button></div>`);};
FORMS.eqTarea=({id}={})=>{
  const vs=activos();
  const body=`${q('¿Qué hay que hacer?',`<input class="in" name="t" placeholder="Revisar bebederos del corral 3…" required autocomplete="off">`)}
   ${q('¿Para quién?',opts('para',[{v:'todos',t:'Todo el equipo'}].concat(vs.map(v=>({v:v.id,t:v.nombre}))),id||'todos'))}
   ${q('¿Cuándo?',`<input class="in" type="date" name="f" value="${hoy()}" min="${hoy()}">`)}`;
  openSheet(shHead('Asignar una tarea',EQT())+formWrap('eqTarea',body,foot('Asignar')));
};
SAVE.eqTarea=f=>{const t=fv(f,'t').trim();if(!t)return ferr(f,'Escribe qué hay que hacer.');const para=fv(f,'para')||'todos';
  const quien=para==='todos'?'todo el equipo':((EQ().vaqueros||{})[para]||{}).nombre||'';
  put('ajustes','finca',{...S.config,tareas:(S.config.tareas||[]).concat({id:uid('t'),t,f:fv(f,'f')||hoy(),hecho:false,para})});closeSheet();toast(`Tarea para ${quien}`);publicarEstado();};
ACTS.eqTareaBorrar=el=>{put('ajustes','finca',{...S.config,tareas:(S.config.tareas||[]).filter(t=>t.id!==el.dataset.id)});publicarEstado();};
ACTS.eqServidor=()=>{const e=EQ()||{};
  openSheet(shHead('Servidor del equipo',EQT())+formWrap('eqServidor',`${q('Dirección del servidor',`<input class="in" name="s" value="${esc(e.servidor||'')}" placeholder="${esc(CFG.servidorEquipo||'https://…')}" autocomplete="off" inputmode="url" data-no-tr>`,'Déjalo vacío para usar el de Rumentis. Sin servidor, el equipo se pasa los datos por archivo.')}`,foot('Guardar')));};
SAVE.eqServidor=f=>{const s=fv(f,'s').trim();if(s&&!N.urlOk(s))return ferr(f,'Escribe una dirección que empiece con https://');guardarEQ({servidor:s});closeSheet();toast('Guardado');sincronizarPronto(200);};

/* ---------- pantalla ---------- */
const avatar=(n,cls='')=>`<span class="eq-av ${cls}" aria-hidden="true" data-no-tr>${esc(N.iniciales(n))}</span>`;
const hoyDe=vid=>allItems().filter(i=>i.f===hoy()&&i.por&&i.por.v===vid).length;
function abrirVaquero(vid){
  const e=EQ(),v=(e.vaqueros||{})[vid];if(!v)return;
  const regs=allItems().filter(i=>i.por&&i.por.v===vid).slice(-12).reverse();
  const tareas=(S.config.tareas||[]).filter(t=>!t.hecho&&(t.para===vid||t.para==='todos'));
  openSheet(shHead(esc(v.nombre),'Vaquero')+`<div class="sh-body eq-vq">
    <div class="eq-vq-h">${avatar(v.nombre,'xl')}<div><b data-no-tr>${N.fmtLic(v.lic)}</b><span>${v.estado==='activo'?`En el equipo desde el ${ffc(v.alta)}`:`Dado de baja el ${ffc(v.baja||hoy())}`}</span><span>Última conexión: <span>${N.haceCuanto(v.ts)}</span></span></div></div>
    ${v.estado==='activo'?`<p class="rh">Qué puede registrar</p><div class="card rows eq-perm">${PERM.map(([k,t])=>`<div class="row"><div class="tx"><b>${t}</b></div><button type="button" class="tog" role="switch" aria-checked="${!!(v.permisos||{})[k]}" aria-label="${t}" data-act="eqPermiso" data-v="${vid}" data-k="${k}"><span></span></button></div>`).join('')}</div>`:''}
    <p class="rh">Tareas asignadas</p>${tareas.length?`<div class="card rows">${tareas.map(t=>`<div class="row"><div class="tx"><b>${esc(t.t)}</b><span>${t.para==='todos'?'Para todo el equipo':'Solo para '+esc(v.nombre)} · ${ffc(t.f)}</span></div></div>`).join('')}</div>`:`<p class="hint">Sin tareas pendientes.</p>`}
    ${v.estado==='activo'?`<button type="button" class="btn full" data-act="f" data-f="eqTarea" data-id="${vid}">${ico('nuevo')}Asignarle una tarea</button>`:''}
    <p class="rh">Lo último que registró</p>${regs.length?`<div class="card rows">${regs.map(it=>`<div class="row"><div class="tx"><b>${esc(textoItem(v,it).replace(v.nombre+' ',''))}</b><span>${ffc(it.f)}</span></div></div>`).join('')}</div>`:`<p class="hint">Todavía no registra nada.</p>`}
    <p class="hint">Huella de su teléfono: <span data-no-tr>${N.huella(v.firma)}</span>${v.disp?` · ${esc(v.disp)}`:''}</p></div>
    ${v.estado==='activo'?`<div class="sh-foot"><button type="button" class="btn danger" data-act="eqBaja" data-v="${vid}" style="flex:1">Dar de baja</button></div>`:''}`);
}
const ICA={alimento:'alimento',pesaje:'pesaje',sanidad:'sanidad',baja:'alerta',lic:'recibo',alta:'estrella',tarea:'lista',borrar:'nota'};
function filaAct(a){return `<div class="row eq-act"><span class="mas-ic t-${a.ic==='baja'||a.ic==='borrar'?'r':a.ic==='alimento'?'y':a.ic==='lic'||a.ic==='alta'?'v':'a'}">${icono(ICA[a.ic]||'info','i3')}</span><div class="tx"><b>${esc(a.txt)}</b><span>${N.haceCuanto(a.ts)}</span></div></div>`;}
function botonComprar(txt){const p=gratis()?'':precioLic();return `<button type="button" class="btn pri full eq-comprar" data-act="eqComprar">${ico('nuevo')}<span>${txt}</span>${p?`<span data-no-tr> · ${esc(p)}</span>`:''}</button>`;}
PAGES.equipo=()=>{
  const e=EQ()||{},L=Object.values(e.licencias||{}),V=Object.values(e.vaqueros||{}),act=V.filter(v=>v.estado==='activo'),libres=L.filter(l=>l.estado==='libre');
  const sols=Object.values(e.solicitudes||{});const srv=servidor();
  const hd=`<header class="hd">${hdTop('<span class="sync"></span>')}<div class="ttl"><span class="eyebrow">${esc(S.config.finca||'Mi engorde')}</span><h1>${EQT()}</h1><p class="sub">Tus vaqueros registran desde su teléfono y tú lo ves aquí, en tus lotes.</p>
    ${L.length?`<div class="kpis"><div class="kpi"><b>${act.length}</b><span>${act.length===1?'vaquero':'vaqueros'}</span></div><div class="kpi"><b>${libres.length}</b><span>${libres.length===1?'licencia libre':'licencias libres'}</span></div><div class="kpi"><b>${allItems().filter(i=>i.f===hoy()&&i.por).length}</b><span>registros hoy</span></div></div>`:''}</div></header>`;
  const aviso=CFG.prueba?`<div class="eq-prueba">${ico('check',2.2)}<p><b>App de prueba.</b> Las licencias se crean sin cobrar. Para el vaquero usa <b>Rumentis Vaquero Prueba</b>.</p></div>`
    :esDueno()?`<div class="eq-prueba">${ico('check',2.2)}<p><b>Modo dueño.</b> Tus licencias se crean sin cobrar y sirven en Rumentis Vaquero.</p></div>`:'';
  if(!L.length)return hd+`<main class="bd">${aviso}<section class="sec"><div class="card pad eq-intro">
    <div class="eq-ilus" aria-hidden="true">${avatar('Juan Pérez')}${avatar('María López','b')}${avatar('Pedro Díaz','c')}</div>
    <h2>Suma a tus vaqueros</h2>
    <ul class="eq-lista"><li>${ico('check',2.4)}<span>Cada uno usa <b>Rumentis Vaquero</b> en su teléfono: gratis en Google Play.</span></li>
     <li>${ico('check',2.4)}<span>Anotan entregas de alimento, pesajes, sanidad y muertes; tú lo ves al momento en tus lotes, con su nombre.</span></li>
     <li>${ico('check',2.4)}<span>Les asignas tareas y decides qué puede registrar cada uno.</span></li>
     <li>${ico('check',2.4)}<span>Ellos no ven tus finanzas, precios ni a Rumi.</span></li></ul>
    <p class="hint">Cada licencia se paga una sola vez y es para una persona. Compras las que necesites.</p>
    ${botonComprar(gratis()?'Crear mi primera licencia':'Comprar mi primera licencia')}</div></section>
    ${!gratis()?`<p class="hint eq-dueno"><button type="button" class="lnk" data-act="f" data-f="eqDueno">Tengo un código de dueño</button></p>`:''}</main>`;
  const filaV=v=>`<button type="button" class="row eq-v" data-act="eqVaquero" data-v="${v.id}">${avatar(v.nombre)}<div class="tx"><b>${esc(v.nombre)}</b><span>${!v.ts?'Sin conectarse':`Activo <span>${N.haceCuanto(v.ts)}</span>`}${hoyDe(v.id)?` · <span>${pl(hoyDe(v.id),'registro hoy','registros hoy')}</span>`:''}</span></div>${ico('chev')}</button>`;
  const tareas=(S.config.tareas||[]).filter(t=>t.para&&!t.hecho);
  const nombreDe=id=>id==='todos'?'Todo el equipo':((e.vaqueros||{})[id]||{}).nombre||'';
  return hd+`<main class="bd">${aviso}
   ${sols.length?`<section class="sec">${secH('Quieren entrar',sols.length)}<div class="card rows">${sols.map(s=>`<button type="button" class="row eq-v" data-act="eqSolicitud" data-v="${s.vid}">${avatar(s.nombre,'c')}<div class="tx"><b>${esc(s.nombre)}</b><span>Licencia <span data-no-tr>${N.fmtLic(s.lic)}</span></span></div>${ico('chev')}</button>`).join('')}</div></section>`:''}
   <section class="sec">${secH('Vaqueros',act.length)}${act.length?`<div class="card rows">${act.map(filaV).join('')}</div>`:`<p class="hint">Todavía nadie activa su licencia. Compártela y aparecerá aquí.</p>`}</section>
   ${libres.length?`<section class="sec">${secH('Licencias sin usar',libres.length)}<div class="card rows">${libres.map(l=>`<button type="button" class="row eq-l" data-act="eqLicencia" data-c="${l.c}"><span class="mas-ic t-v">${icono('recibo','i3')}</span><div class="tx"><b data-no-tr>${N.fmtLic(l.c)}</b><span>Toca para ver el QR y enviarla</span></div>${ico('chev')}</button>`).join('')}</div></section>`:''}
   <section class="sec">${botonComprar(gratis()?(libres.length?'Crear otra licencia':'Crear una licencia'):(libres.length?'Comprar otra licencia':'Comprar una licencia'))}</section>
   <section class="sec">${secH('Tareas del equipo',tareas.length,lnkB('f','Asignar','data-f="eqTarea"'))}${tareas.length?`<div class="card rows">${tareas.map(t=>`<div class="row"><span class="mas-ic t-a">${icono('lista','i3')}</span><div class="tx"><b>${esc(t.t)}</b><span>${esc(nombreDe(t.para))} · ${ffc(t.f)}</span></div><button type="button" class="del" data-act="eqTareaBorrar" data-id="${t.id}" aria-label="Quitar tarea">${ico('papelera')}</button></div>`).join('')}</div>`:`<p class="hint">Asigna tareas a un vaquero o a todos: les aparecen en su app.</p>`}</section>
   <section class="sec">${secH('Actividad')}${(e.act||[]).length?`<div class="card rows">${(e.act||[]).slice(0,UI.eqAct?60:12).map(filaAct).join('')}</div>${(e.act||[]).length>12&&!UI.eqAct?`<button type="button" class="lnk" data-act="eqActMas">Ver más</button>`:''}`:`<p class="hint">Aquí verás lo que registra tu equipo.</p>`}</section>
   <section class="sec">${secH('Sincronización')}<div class="card pad eq-sinc">
    <div class="eq-sinc-h"><span class="mas-ic ${srv?'t-v':'t-y'}">${icono(srv?'rayo':'datos','i3')}</span><div><b>${srv?'Automática por internet':'Por archivo (WhatsApp)'}</b><span>${srv?(SY.err?'No se pudo conectar: se reintenta sola':SY.ult?`Última vez: <span>${N.haceCuanto(SY.ult)}</span>`:'Todavía no se conecta'):'Sin servidor: manda los datos a tu equipo como archivo.'}</span></div></div>
    <div class="acts">${srv?`<button type="button" class="btn" data-act="eqSinc">Sincronizar ahora</button>`:''}<button type="button" class="btn" data-act="eqEnviarArchivo">Enviar al equipo</button><button type="button" class="btn" data-act="eqRecibirArchivo">Recibir archivo</button></div>
    <p class="hint eq-arch">Los archivos del equipo terminan en .rumentis. En WhatsApp tócalo y elige Rumentis para abrirlo. Cada archivo se abre una sola vez.</p>
    <div class="row eq-tg"><div class="tx"><b>Aceptar vaqueros nuevos solos</b><span>Si lo apagas, te pregunto antes de dejar entrar a alguien.</span></div><button type="button" class="tog" role="switch" aria-checked="${e.auto!==false}" aria-label="Aceptar solos" data-act="eqAuto"><span></span></button></div>
    <button type="button" class="lnk" data-act="eqServidor">Servidor del equipo</button></div></section>
   ${!gratis()?`<p class="hint eq-dueno"><button type="button" class="lnk" data-act="f" data-f="eqDueno">Tengo un código de dueño</button></p>`:''}
  </main>`;
};
ACTS.eqActMas=()=>{UI.eqAct=1;render();};
FORMS.eqDueno=()=>{openSheet(shHead('Código de dueño',EQT())+formWrap('eqDueno',`${q('Código',`<input class="in vq-lic" name="c" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="RD-XXXX-XXXX-XXXX-XXXX-XXXX" data-no-tr>`,'Solo para el dueño de Rumentis: con él creas licencias sin cobrar.')}`,foot('Activar')));};
SAVE.eqDueno=async f=>{const c=String(fv(f,'c')||'').toUpperCase().replace(/[OÓ]/g,'0').replace(/[IÍL]/g,'1').replace(/[^0-9A-Z]/g,'').replace(/^RD/,'');
  if(N.sha('RUMENTIS-DUENO|'+c)!==HUELLA_DUENO)return ferr(f,'Ese código no es válido.');
  await asegurarEquipo();guardarEQ({dueno:true,act:actividad({ic:'alta',txt:'Activaste el modo dueño'})});closeSheet();toast('Modo dueño activado: tus licencias no se cobran.',4000);};

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
  const fila=`<section class="sec">${secH(EQT())}<div class="card rows">${masFila(ico('equipo'),'Equipo de trabajo',n?pl(n,'vaquero activo','vaqueros activos'):'Suma a tus vaqueros con su propia app','href="#equipo"','t-v')}</div></section>`;
  const i=h.indexOf('<section class="sec"><div class="sec-h"><div><h2>Datos y personalización');return i>0?h.slice(0,i)+fila+h.slice(i):h+fila;};

/* ---------- arranque ---------- */
if(N.pagosHay()){try{Pagos.iniciar(CFG.productoLicencia);}catch(e){}}
if(conEquipo()&&servidor())setTimeout(()=>sincronizar(),2500);
window.Equipo={alRecibir,sincronizar,publicarEstado,estadoEquipo,procesar,aplicarOps,crearLicencia,asegurarEquipo,fichaEq,abrirLicencia,darDeBaja,SY:()=>SY};
pestana();if(route().p==='equipo'||route().p==='mas')render();
})();
