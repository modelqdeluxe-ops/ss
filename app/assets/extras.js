/* Extras de Rumentis 3.6: Agenda de tareas, Bodega de alimento e Informe de lote para compartir.
   Todo se guarda en S.config (va en el respaldo). */
(function(){
'use strict';
const cfg=()=>S.config;
const guardarCfg=(k,v)=>put('ajustes','finca',{...S.config,[k]:v});
const IC3={
 agenda:'<rect x="3.5" y="5" width="17" height="15" rx="2.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
 bodega:'<path d="M3 10l9-6 9 6v10H3z"/><path d="M8 20v-6h8v6M8 17h8"/>',
 check:'<path d="M5 12.5l4.5 4.5L19 7.5"/>',compartir:'<circle cx="18" cy="5.5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="18.5" r="2.5"/><path d="M8.2 10.8l7.6-4M8.2 13.2l7.6 4"/>',
 alimento:'<path d="M4 9h16l-1.5 10a2 2 0 0 1-2 1.7h-9a2 2 0 0 1-2-1.7L4 9z"/><path d="M8 9V6a4 4 0 0 1 8 0v3"/>',
 pesaje:'<path d="M4 20h16M6 20l2-9h8l2 9M12 11V5M8 5h8"/>',sanidad:'<path d="M18 3l3 3M16 5l3 3M5 19l-2 2M14 7l3 3-8 8H6v-3l8-8z"/>',
 venta:'<path d="M3 7h13l4 5v5h-3M3 7v10h2M9 17h5"/><circle cx="7" cy="17.5" r="2"/><circle cx="16" cy="17.5" r="2"/>',
 ojo:'<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
 datos:'<path d="M12 3v12M7 10l5 5 5-5M4 19h16"/>',nota:'<path d="M5 4h14v16H5z"/><path d="M8 9h8M8 13h8M8 17h5"/>',info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5h.01"/>'
};
const i3=k=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${IC3[k]||''}</svg>`;

/* ================= BODEGA ================= */
const B=()=>{const b=cfg().bodega||{};return {items:b.items||[],movs:b.movs||[]};};
const guardarB=b=>guardarCfg('bodega',b);
let _cache={v:-1,M:null,F:null};
function mapa(){if(_cache.v===ver&&_cache.M)return _cache;const M={};
  for(const i of calc().items)if(i.tipo==='alimento'){const d=M[i.f]||(M[i.f]={});d[i.racion||'']=(d[i.racion||'']||0)+(+i.kg||0);}
  _cache={v:ver,M,F:Object.keys(M).sort()};return _cache;}
function consumoDia(it,f){
  const d=mapa().M[f];if(!d)return 0;let kg=0;
  if(it.tipo==='racion')return d[it.ref]||0;
  if(it.tipo==='ing'){const n=String(it.ref).trim().toLowerCase();
    for(const [rid,k] of Object.entries(d)){const r=S.raciones[rid];if(!r||!(r.ings||[]).length)continue;const tot=r.ings.reduce((s,g)=>s+(+g.p||0),0);if(!tot)continue;
      for(const g of r.ings)if(String(g.n).trim().toLowerCase()===n)kg+=k*(+g.p||0)/tot;}}
  return kg;
}
function estado(it){
  const b=B(),H=calc().H,mv=b.movs.filter(m=>m.item===it.id).sort((a,b)=>a.f<b.f?-1:1);
  if(!mv.length)return {sin:true};
  const conteos=mv.filter(m=>m.tipo==='conteo');const c=conteos[conteos.length-1];
  const fc=c?c.f:addDias(mv[0].f,-1);let st=c?+c.kg:0;
  for(const m of mv)if(m.tipo==='compra'&&m.f>fc)st+=+m.kg;
  // consumo desde el conteo (o desde el día anterior a la primera compra)
  let cons=0;const fechas=mapa().F.filter(f=>f>fc&&f<=H);
  for(const f of fechas)cons+=consumoDia(it,f);
  st-=cons;
  let s7=0;for(let k=0;k<7;k++)s7+=consumoDia(it,addDias(H,-k));const dia=s7/7;
  const dias=dia>0?st/dia:null;const ult=[...mv].reverse().find(m=>m.tipo==='compra');
  return {stock:st,dia,dias,ult,sin:false};
}
function resumen(){const b=B();if(!b.items.length)return null;const E=b.items.map(it=>({it,e:estado(it)})).filter(o=>!o.e.sin);
  if(!E.length)return {txt:`${pl(b.items.length,'producto','productos')}, sin inventario anotado`};
  const bajo=E.filter(o=>o.e.dias!=null&&o.e.dias<(+o.it.min||7));
  return {txt:bajo.length?`${pl(bajo.length,'producto','productos')} por acabarse`:`${pl(E.length,'producto','productos')} con inventario al día`,bajo};}
PAGES.bodega=()=>{
  const b=B();
  const card=it=>{const e=estado(it);const min=+it.min||7;
    const pct=e.dias==null?0:Math.max(3,Math.min(100,e.dias/30*100));const col=e.dias==null?'var(--anil5)':e.dias<min?'var(--rojo)':e.dias<min*2?'var(--arete-e)':'var(--verde)';
    return `<div class="card pad bod">
      <button type="button" class="bod-t" data-act="f" data-f="bodegaItem" data-id="${esc(it.id)}"><b>${esc(it.n)}</b><span>${it.tipo==='racion'?'Ración':it.tipo==='ing'?'Ingrediente':'Otro'}${e.ult?` · última compra ${cuando(e.ult.f)}`:''}</span></button>
      ${e.sin?`<p class="hint" style="margin:0">Anota una compra o cuenta lo que hay en bodega para empezar.</p>`:`<div class="bod-n"><div><span class="hf">${nf(Math.max(0,e.stock))} kg</span><small>en bodega</small></div><div style="text-align:right"><span class="hf" style="color:${col}">${e.dias==null?'–':e.dias<1?'hoy se acaba':pl(Math.floor(e.dias),'día','días')}</span><small>${e.dia?`alcanza · usas ${nf(e.dia)} kg al día`:'sin consumo anotado'}</small></div></div>
      <div class="bod-bar"><i style="width:${pct}%;background:${col}"></i></div>`}
      <div class="acts"><button type="button" class="btn" data-act="f" data-f="bodegaCompra" data-id="${esc(it.id)}">${i3('alimento')}Anotar compra</button><button type="button" class="btn" data-act="f" data-f="bodegaConteo" data-id="${esc(it.id)}">Contar bodega</button></div></div>`;};
  const vacio=`<div class="card pad" style="display:flex;flex-direction:column;gap:12px"><p style="margin:0;line-height:1.5">Anota lo que compras de alimento y la app te dice cuánto te queda y para cuántos días alcanza, con lo que tus lotes comen de verdad. Te avisa en la Agenda antes de que se acabe.</p>
    ${Object.keys(S.raciones).length?`<button type="button" class="btn pri" data-act="bodegaAuto">Agregar mis raciones e ingredientes</button>`:''}<button type="button" class="btn" data-act="f" data-f="bodegaItem">Agregar un producto</button></div>`;
  return `<header class="hd">${hdBack('#mas','Más','<span class="sync"></span>')}<div class="ttl" style="margin-top:-6px"><h1>Bodega de alimento</h1><p class="sub">Cuánto tienes y para cuántos días te alcanza.</p></div></header>
   <main class="bd">${b.items.length?b.items.map(card).join('')+`<button type="button" class="btn full" data-act="f" data-f="bodegaItem">Agregar un producto</button><p class="hint">El consumo sale de las entregas de alimento que anotas. Si una ración tiene ingredientes con porcentajes, cada ingrediente se descuenta en su proporción.</p>`:vacio}</main>`;
};
Object.assign(FORMS,{
  bodegaItem:({id}={})=>{
    const b=B(),it=id?b.items.find(x=>x.id===id):null;
    const ings=[...new Set(Object.values(S.raciones).flatMap(r=>(r.ings||[]).filter(g=>+g.p>0).map(g=>String(g.n).trim())).filter(Boolean))];
    const R=Object.entries(S.raciones);
    const refs=[{v:'',t:'Ninguno (otro producto)'}].concat(R.map(([k,r])=>({v:'r:'+k,t:'Ración: '+r.nombre})),ings.map(n=>({v:'i:'+n,t:'Ingrediente: '+n})));
    const cur=it?(it.tipo==='racion'?'r:'+it.ref:it.tipo==='ing'?'i:'+it.ref:''):'';
    const body=`${q('Nombre',`<input class="in" name="n" value="${esc(it?it.n:'')}" placeholder="Maíz molido" required autocomplete="off">`)}
      ${q('¿Qué es en tus raciones?',`<select class="in" name="ref">${refs.map(o=>`<option value="${esc(o.v)}"${o.v===cur?' selected':''}>${esc(o.t)}</option>`).join('')}</select>`,'Así la app descuenta lo que comen tus lotes.')}
      ${q('Avisarme cuando alcance para menos de',inp('min',it?it.min||7:7,{mode:'numeric',unit:'días'}))}
      ${it?`<button type="button" class="btn danger" data-act="bodegaBorrar" data-id="${esc(it.id)}">Quitar de la bodega</button>`:''}`;
    openSheet(shHead(it?'Editar producto':'Agregar producto','Bodega')+formWrap('bodegaItem',body,foot('Guardar')),()=>{const f=$('#sheet form');if(it)f.dataset.id=it.id;});
  },
  bodegaCompra:({id}={})=>{const it=B().items.find(x=>x.id===id);if(!it)return;
    const body=`<div class="two">${q('Fecha',`<input class="in" type="date" name="f" value="${hoy()}" max="${hoy()}">`)}${q('Cantidad',inp('kg','',{unit:'kg',ph:'0',xl:true}))}</div>
      ${q('Costo total',inp('costo','',{unit:S.config.moneda||'L',ph:'Opcional'}),'Solo para tu control; el costo del alimento ya se calcula con la ración.')}${q('Proveedor',`<input class="in" name="prov" placeholder="Opcional" autocomplete="off">`)}`;
    openSheet(shHead('Compra de '+esc(it.n),'Bodega')+formWrap('bodegaCompra',body,foot('Guardar compra')),()=>{$('#sheet form').dataset.id=id;});},
  bodegaConteo:({id}={})=>{const it=B().items.find(x=>x.id===id);if(!it)return;
    const body=`<p class="hint" style="margin:0">Pesa o cuenta lo que hay en bodega (por ejemplo sacos × peso del saco). La app corrige el inventario desde esta fecha.</p><div class="two">${q('Fecha',`<input class="in" type="date" name="f" value="${hoy()}" max="${hoy()}">`)}${q('Hay en bodega',inp('kg','',{unit:'kg',ph:'0',xl:true}))}</div>`;
    openSheet(shHead('Contar '+esc(it.n),'Bodega')+formWrap('bodegaConteo',body,foot('Guardar conteo')),()=>{$('#sheet form').dataset.id=id;});}
});
Object.assign(SAVE,{
  bodegaItem:f=>{const b=B();const n=fv(f,'n').trim();if(!n)return ferr(f,'Escribe el nombre.');const r=fv(f,'ref');
    const d={n,tipo:r.startsWith('r:')?'racion':r.startsWith('i:')?'ing':'otro',ref:r.slice(2),min:Math.max(1,num(fv(f,'min'))||7)};
    if(f.dataset.id)b.items=b.items.map(x=>x.id===f.dataset.id?{...x,...d}:x);else b.items=b.items.concat({id:uid('b'),...d});
    guardarB(b);closeSheet();toast('Producto guardado');},
  bodegaCompra:f=>{const kg=toKg(num(fv(f,'kg')));if(!(kg>0))return ferr(f,'Escribe la cantidad.');const b=B();
    b.movs=b.movs.concat({id:uid('m'),item:f.dataset.id,tipo:'compra',f:fv(f,'f')||hoy(),kg:Math.round(kg*10)/10,costo:num(fv(f,'costo'))||0,prov:fv(f,'prov').trim()});guardarB(b);closeSheet();toast('Compra anotada');},
  bodegaConteo:f=>{const raw=fv(f,'kg').trim();if(raw==='')return ferr(f,'Escribe cuánto hay.');const kg=toKg(num(raw));const b=B();
    b.movs=b.movs.concat({id:uid('m'),item:f.dataset.id,tipo:'conteo',f:fv(f,'f')||hoy(),kg:Math.round(kg*10)/10});guardarB(b);closeSheet();toast('Inventario corregido');}
});
Object.assign(ACTS,{
  bodegaAuto:()=>{const b=B();const ya=new Set(b.items.map(x=>x.tipo+':'+String(x.ref).toLowerCase()));const nuevos=[];
    for(const [k,r] of Object.entries(S.raciones)){const ings=(r.ings||[]).filter(g=>+g.p>0&&String(g.n).trim());
      if(ings.length){for(const g of ings){const key='ing:'+String(g.n).trim().toLowerCase();if(!ya.has(key)){ya.add(key);nuevos.push({id:uid('b'),n:String(g.n).trim(),tipo:'ing',ref:String(g.n).trim(),min:7});}}}
      else if(!ya.has('racion:'+k.toLowerCase())){ya.add('racion:'+k.toLowerCase());nuevos.push({id:uid('b'),n:r.nombre,tipo:'racion',ref:k,min:7});}}
    b.items=b.items.concat(nuevos);guardarB(b);toast(nuevos.length?`Agregué ${pl(nuevos.length,'producto','productos')}. Ahora anota cuánto hay de cada uno.`:'Ya estaban todos.');},
  bodegaBorrar:el=>{const b=B();b.items=b.items.filter(x=>x.id!==el.dataset.id);b.movs=b.movs.filter(m=>m.item!==el.dataset.id);guardarB(b);closeSheet();toast('Producto quitado');}
});

/* ================= AGENDA ================= */
const hechas=()=>cfg().hechas||{};
function tareas(){
  const C=calc(),H=C.H,T=[],d=+S.config.diasSinPesar||21,hh=hechas();
  const add=(key,f,t,s,ic,act,tono)=>{if(hh[key]===H||(hh[key]&&hh[key]>=f&&!key.startsWith('dia:')))return;T.push({key,f,t,s,ic,act,tono});};
  if(C.act.length&&C.sigEnt)add('ent:'+H+':'+C.sigEnt,H,`Entrega de alimento de las ${horaDe(C.sigEnt)}`,`Llevas ${nf(C.kgHoy)} kg de unos ${nf(C.prog)} kg`,'alimento',{t:'form',k:'alimento',n:C.sigEnt},'y');
  for(const x of C.act){
    const nm=x.l.nombre;
    if(x.dec<21)add('dia:rev:'+x.id+':'+H,H,`Revisar recién llegados de ${nm}`,'Fiebre, tos, moco, que coman y tomen agua (lleva el termómetro)','ojo',null,'r');
    const fp=addDias(x.ult.f,d);add('pes:'+x.id+':'+x.ult.f,fp<H?H:fp,`Pesar ${nm}`,`Último pesaje ${cuando(x.ult.f)}`,'pesaje',{t:'form',k:'pesaje',p:{lote:x.id}},'a');
    const san=x.g.sanidad||[],ing=x.l.fechaIngreso;
    const vac=san.filter(s=>/vacun/i.test(s.clase||''));
    if(vac.some(s=>s.f<=addDias(ing,7))&&!vac.some(s=>s.f>=addDias(ing,14)&&s.f<=addDias(ing,50))&&H<=addDias(ing,50))add('ref:'+x.id,addDias(ing,21),`Refuerzo de vacuna de ${nm}`,'La segunda dosis protege de verdad (día 21 a 30)','sanidad',{t:'form',k:'sanidad',p:{lote:x.id}},'v');
    if(x.retiroHasta&&x.retiroHasta>=H)add('ret:'+x.id+':'+x.retiroHasta,x.retiroHasta,`${nm} sale de retiro`,`${x.retiroProd||'Medicamento'}: desde este día se puede vender a matadero`,'info',null,'a');
    if(x.listo)add('ven:'+x.id,H,`Vender ${nm}`,`Ya llegó a su meta de ${nf(x.meta)} kg${x.retiroHasta?` (espera el retiro)`:''}`,'venta',{t:'form',k:'venta',p:{lote:x.id}},'v');
    else if(x.fechaMeta&&x.fechaMeta<=addDias(H,28))add('pre:'+x.id+':'+x.fechaMeta,addDias(x.fechaMeta,-7)<H?H:addDias(x.fechaMeta,-7),`Preparar la venta de ${nm}`,`Llega a la meta cerca del ${ffc(x.fechaMeta)}: busca comprador y revisa retiros`,'venta',null,'v');
  }
  const b=B();for(const it of b.items){const e=estado(it);if(!e.sin&&e.dias!=null&&e.dias<(+it.min||7))add('bod:'+it.id+':'+H,H,`Comprar ${it.n}`,e.dias<1?'Se acaba hoy':`Alcanza para ${pl(Math.floor(e.dias),'día','días')}`,'bodega',{t:'go',go:'#bodega'},'r');}
  if(window.tareasFin)for(const t of window.tareasFin())add(t.key,t.f,t.t,t.s,t.ic,t.act,t.tono);
  let ult=null;try{ult=localStorage.getItem('rumentis-ult-respaldo');}catch(e){}
  if(Object.keys(S.lotes).length&&(!ult||ult<addDias(H,-7)))add('resp:'+H,H,'Descargar respaldo',ult?`El último fue ${cuando(ult)}`:'Aún no has descargado ninguno','datos',{t:'go',go:'#mas/datos'},'a');
  for(const t of (cfg().tareas||[]))if(!t.hecho)T.push({key:'propia:'+t.id,f:t.f,t:t.t,s:'Recordatorio tuyo',ic:'nota',propia:t.id,tono:'a'});
  return T.sort((a,b)=>a.f<b.f?-1:a.f>b.f?1:0);
}
window.Agenda={tareas};
const accion=a=>{if(!a)return '';RUMI.acts=RUMI.acts||{};const id='ag'+(++RUMI.n);RUMI.acts[id]=a;return id;};
function filaT(t,H){
  const aid=accion(t.act);
  return `<div class="row ag-row"><span class="mas-ic t-${t.tono||'a'}">${i3(t.ic)}</span><div class="tx"><b>${esc(t.t)}</b><span>${esc(t.s)}${t.f>H?` · ${ffc(t.f)}`:t.f<H?' · atrasada':''}</span></div>
    <div class="ag-acts">${aid?`<button type="button" class="btn sm" data-act="rumiDo" data-id="${aid}">Hacer</button>`:''}<button type="button" class="ag-ok" data-act="agHecha" data-k="${esc(t.key)}" data-p="${esc(t.propia||'')}" aria-label="Marcar como hecha">${i3('check')}</button></div></div>`;
}
PAGES.agenda=()=>{
  const H=calc().H,T=tareas();
  const hoyT=T.filter(t=>t.f<=H),sem=T.filter(t=>t.f>H&&t.f<=addDias(H,7)),mas=T.filter(t=>t.f>addDias(H,7));
  const sec=(t,L,vacio)=>`<section class="sec">${secH(t,L.length)}${L.length?`<div class="card rows">${L.map(x=>filaT(x,H)).join('')}</div>`:`<p class="hint">${vacio}</p>`}</section>`;
  return `<header class="hd">${hdBack('#hoy','Hoy','<span class="sync"></span>')}<div class="ttl" style="margin-top:-6px"><h1>Agenda</h1><p class="sub">Lo que toca hacer, armado con tus datos: entregas, pesajes, refuerzos, retiros, ventas y bodega.</p></div></header>
   <main class="bd">${sec('Hoy',hoyT,'Nada pendiente para hoy. ¡Bien!')}${sec('Próximos 7 días',sem,'Nada en los próximos días.')}${mas.length?sec('Más adelante',mas,''):''}
    <button type="button" class="btn full" data-act="f" data-f="tarea">${ico('nuevo')}Agregar un recordatorio</button></main>`;
};
FORMS.tarea=()=>{const body=`${q('¿Qué hay que hacer?',`<input class="in" name="t" placeholder="Comprar vacunas, llamar al veterinario…" required autocomplete="off">`)}${q('¿Cuándo?',`<input class="in" type="date" name="f" value="${hoy()}" min="${hoy()}">`)}`;
  openSheet(shHead('Nuevo recordatorio','Agenda')+formWrap('tarea',body,foot('Guardar')));};
SAVE.tarea=f=>{const t=fv(f,'t').trim();if(!t)return ferr(f,'Escribe qué hay que hacer.');guardarCfg('tareas',(cfg().tareas||[]).concat({id:uid('t'),t,f:fv(f,'f')||hoy(),hecho:false}));closeSheet();toast('Recordatorio guardado');};
ACTS.agHecha=el=>{const H=calc().H;if(el.dataset.p){guardarCfg('tareas',(cfg().tareas||[]).map(t=>t.id===el.dataset.p?{...t,hecho:true}:t));}
  else{const h={...hechas()};for(const k of Object.keys(h))if(h[k]<addDias(H,-60))delete h[k];h[el.dataset.k]=H;guardarCfg('hechas',h);}toast('Hecho');};
// recordar la fecha del último respaldo
const _resp=ACTS.respaldo;ACTS.respaldo=(...a)=>{try{localStorage.setItem('rumentis-ult-respaldo',hoy());}catch(e){}return _resp(...a);};
// tarjeta de agenda en Hoy
const _hoyA=PAGES.hoy;
PAGES.hoy=(...a)=>{const html=_hoyA(...a);if(!Object.keys(S.lotes).length)return html;const H=calc().H,T=tareas().filter(t=>t.f<=H);
  const card=`<a class="card pad ag-card" href="#agenda"><div class="ag-h"><span class="mas-ic t-y">${i3('agenda')}</span><div><b>Agenda de hoy</b><span>${T.length?pl(T.length,'tarea pendiente','tareas pendientes'):'Todo al día'}</span></div>${ico('chev')}</div>${T.slice(0,3).map(t=>`<div class="ag-mini"><i class="t-${t.tono||'a'}"></i>${esc(t.t)}</div>`).join('')}</a>`;
  return html.replace('<main class="bd">','<main class="bd">'+card);};

/* ================= INFORME DEL LOTE ================= */
function informe(id){
  const C=calc(),x=C.L[id];if(!x)return '';const l=x.l,u=t=>aUnidad(t).replace(/<[^>]+>/g,'');
  const L=[`*${l.nombre}* · ${S.config.finca||'Mi engorde'}`,`Informe del ${ffl(C.H)}`,''];
  L.push(`Cabezas: ${x.activo?x.cab:x.cab0}${x.bajas?` (murieron ${x.bajas})`:''}`,`Entró el ${ffc(l.fechaIngreso)} con ${nf(x.p0)} kg por cabeza`,`Peso ${x.activo?'de hoy':'de venta'}: ${nf(x.pesoHoy)} kg${x.estimado?' (estimado)':''}`,`Ganancia diaria: ${nf(x.activo?x.gdpUse:x.gdpTot,2)} kg por día`);
  if(x.conv)L.push(`Conversión: ${nf(x.conv,1)} a 1`);
  if(x.consumo)L.push(`Consumo: ${nf(x.consumo,1)} kg por cabeza al día`);
  if(x.activo)L.push(x.listo?`Ya llegó a la meta de ${nf(x.meta)} kg`:`Meta ${nf(x.meta)} kg: faltan ${nf(x.falta)} kg, sale cerca del ${ffc(x.fechaMeta)}`);
  L.push('',`Invertido: ${money(x.costoTot)} (compra ${money(x.compra)}, alimento ${money(x.costoAlim)}, sanidad ${money(x.san)})`);
  if(x.costoKgGan)L.push(`Costo por kilo ganado: ${pk(x.costoKgGan)}`);
  const cb=x.activo?x.proy.cabBase:(x.cab0-x.bajas)||1,m=x.activo?x.proy.margen:x.margenReal;
  L.push(`${x.activo?'Margen estimado':'Margen'}: ${money(m)} (${money(m/cb)} por cabeza)`);
  if(x.retiroHasta)L.push(`En retiro hasta el ${ffc(x.retiroHasta)} (${x.retiroProd})`);
  L.push('','Hecho con Rumentis');
  return u(L.join('\n'));
}
async function compartir(texto,titulo){
  if(window.I18N)texto=I18N.txt(texto);
  if(window.Android&&Android.compartir){Android.compartir(texto);return;}
  if(navigator.share){try{await navigator.share({title:titulo,text:texto});return;}catch(e){if(e&&e.name==='AbortError')return;}}
  try{await navigator.clipboard.writeText(texto);toast('Informe copiado. Pégalo en WhatsApp.');}catch(e){descargar(`informe-${titulo}.txt`,texto);}
}
ACTS.informeLote=el=>{const t=informe(el.dataset.id);if(t)compartir(t,S.lotes[el.dataset.id].nombre);};
window.Extras={informe,compartir,bodega:{resumen,estado}};
/* valor de lo que hay en bodega: costo de la última compra con precio; si no, el precio del ingrediente o de la ración */
function valorKg(it){const b=B(),c=[...b.movs].reverse().find(m=>m.item===it.id&&m.tipo==='compra'&&+m.costo>0&&+m.kg>0);if(c)return c.costo/c.kg;
  if(it.tipo==='racion'&&S.raciones[it.ref])return +S.raciones[it.ref].costoKg||0;
  if(it.tipo==='ing'&&typeof INGS!=='undefined'){const n=String(it.ref).trim().toLowerCase();const g=INGS.find(i=>String(i.n).toLowerCase()===n);if(g)return ingPrecio(g.id);}
  return 0;}
function inventario(){return B().items.map(it=>{const e=estado(it),st=e.sin?0:Math.max(0,e.stock),vk=valorKg(it);return {it,e,stock:st,valorKg:vk,valor:st*vk};});}
window.Bodega={resumen,inventario};
})();
