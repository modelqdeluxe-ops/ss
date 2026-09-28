/* Rumi, análisis avanzados (Rumentis 4.0). Todo con tus datos y en el teléfono:
   - Señales de alerta: lo que cambió y merece tu atención (ganancia que baja, consumo que cae,
     alimento que se acaba, costos que suben, retiros que chocan con la venta…).
   - Simulador "¿qué pasa si…?" con controles deslizables y resultado al momento.
   - ¿Cuándo vender?: la curva de margen de cada lote día por día y el día que más deja.
   - Punto de equilibrio: precio y peso mínimos para no perder, y tu margen de seguridad.
   - En qué se va el dinero: estructura de costos por lote y de la finca.
   - Flujo de caja de los próximos 90 días: cuándo entra y sale dinero y si te va a faltar.
   - Tú contra la referencia: ganancia, conversión, mortalidad, costo y consumo contra rangos buenos.
   - ¿Cuánto pagar por el próximo lote?: precio máximo de compra según el peso.
   - Calendario de ventas y cuánto rinde tu dinero al año contra el banco. */
(function(){
'use strict';
const tablaW=(h,r)=>tabla(h,r).replace('class="tb"','class="tb tw"');
if(!window.RumiMenu||!window.RumiPro)return;
const cl=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
const des=()=>Math.max(0,Math.min(15,+S.config.desbaste||0))/100;
const pvKg=()=>+S.config.precioVentaKg||0;
const I=k=>icono(k,'i3');
const kvs=rows=>`<div class="rp-kv">${rows.map(([a,b])=>`<div><span>${a}</span><em>${b}</em></div>`).join('')}</div>`;
const fmtK=v=>{const a=Math.abs(v),s=S.config.moneda||'L';return (v<0?'−':'')+s+' '+(a>=1e6?nf(a/1e6,2)+' M':a>=1e4?nf(a/1e3,0)+' mil':nf(a));};
const col=v=>v>=0?'verde':'rojo';
const conProy=()=>calc().act.filter(x=>x.proy);

/* ================= curva de venta: margen si vendes en d días ================= */
/* la ganancia diaria baja poco a poco al acercarse al peso final y el consumo sube con el peso */
function curva(x,dMax=150){
  const C=calc(),pv=pvKg(),g0=Math.max(0.2,x.gdpUse||0.2),cons0=x.consumo||x.pesoHoy*0.026,cKg=x.costoKgR||0,gen=C.genDia||0;
  const base=x.costoTot-x.ingresos,vivos=x.cab;let w=x.pesoHoy,acum=0;const P=[];
  for(let d=0;d<=dMax;d++){
    if(d>0){const g=g0*Math.max(0.45,1-0.0028*d);const cons=cons0*(w/x.pesoHoy);acum+=(cons*cKg+gen)*vivos;w+=g;}
    P.push({d,w,m:w*(1-des())*pv*vivos-(base+acum),dia:d?(0):0});
  }
  // margen de cada día más: lo que suben contra lo que cuestan
  for(let d=1;d<P.length;d++)P[d].dia=P[d].m-P[d-1].m;
  let best=P[0];for(const p of P)if(p.m>best.m)best=p;
  return {P,best,hoy:P[0]};
}
function cuandoVender(x){
  if(!x||!x.activo)return {html:'Elige un lote en engorde.'};
  const {P,best,hoy}=curva(x),H=calc().H,dm=x.diasMeta||0;
  const pts=P.filter((p,i)=>i%3===0||p===best).map(p=>[addDias(H,p.d),p.m/1000]);
  const meta=P[Math.min(P.length-1,dm)];
  const ch=lineChart({series:[{name:'Margen del lote (miles)',color:'var(--s1)',pts,dots:false}],refs:[{v:0,color:'var(--rojo)',label:'sin ganancia'}],marks:[{f:addDias(H,best.d),v:best.m/1000,color:'var(--verde)'}].concat(dm>0&&dm<P.length?[{f:addDias(H,dm),v:meta.m/1000,color:'var(--arete)'}]:[]),today:true,h:200,
    extra:[{k:'ring',color:'var(--verde)',t:'el día que más deja'}].concat(dm>0&&dm<P.length?[{k:'ring',color:'var(--arete)',t:'llega a la meta'}]:[])});
  const mas=best.m-hoy.m;const nota=`<p class="rs" style="margin-top:4px">Margen del lote si lo vendes ese día, en miles de ${esc(S.config.moneda||'L')}.</p>`;
  let txt;
  if(best.d===0)txt=`<b>Te conviene vender ya.</b> Cada día más cuesta más de lo que suben: hoy el lote deja ${money(hoy.m)} y cada día que pase deja menos.`;
  else txt=`Lo que más te deja es vender alrededor del <b>${ffl(addDias(H,best.d))}</b> (en ${pl(best.d,'día','días')}), con unos <b>${nf(best.w)} kg</b>: ${money(best.m)} de margen, <b>${money(mas)}</b> más que vender hoy. Después, cada día cuesta más de lo que suben.`;
  const d30=P[Math.min(30,P.length-1)];
  return {html:`<b>¿Cuándo vender ${esc(x.l.nombre)}?</b>${ch}${nota}<p class="rs">${txt}</p>
    ${kvs([['Si vendes hoy',`<b style="color:var(--${col(hoy.m)})">${money(hoy.m)}</b>`],['En el mejor día',`<b style="color:var(--${col(best.m)})">${money(best.m)}</b>`],['Un día más hoy deja',`${money(P[1]?P[1].dia:0)}`],['Un día más en 30 días deja',`${money(d30.dia)}`]])}
    <p class="rs">Supongo tu precio de hoy (${pk(pvKg())} el kilo) y que la ganancia diaria baja poco a poco al acercarse al peso final. Si el precio sube o baja, cambia el mejor día: pruébalo en el simulador.</p>`,
    btns:[['Abrir el simulador',{t:'fn',fn:'simulador'}],['Anotar la venta',{t:'form',k:'venta',p:{lote:x.id}}]]};
}

/* ================= punto de equilibrio ================= */
function equilibrio(x){
  const p=x.proy;if(!p)return null;const pf=Math.max(x.meta,x.pesoHoy),kgV=pf*(1-des())*p.cabBase;
  const pmin=kgV>0?(p.costoFinal-x.ingresos)/kgV:null;
  const wmin=p.cabBase>0&&pvKg()>0?(p.costoFinal-x.ingresos)/(p.cabBase*(1-des())*pvKg()):null;
  const seg=pmin!=null&&pvKg()>0?(pvKg()-pmin)/pvKg():null;
  return {pmin,wmin,seg,pf};
}
function equilibrioHtml(){
  const A=conProy();if(!A.length)return {html:'No tienes lotes en engorde.'};
  const R=A.map(x=>({x,e:equilibrio(x)}));
  const peor=R.slice().sort((a,b)=>(a.e.seg??1)-(b.e.seg??1))[0];
  return {html:`<b>Punto de equilibrio</b>${tablaW(['Lote','Precio mínimo','Peso mínimo','Seguridad'],R.map(({x,e})=>[esc(x.l.nombre),e.pmin!=null?`<b>${pk(e.pmin)}</b>`:'–',e.wmin!=null?nf(e.wmin)+' kg':'–',e.seg!=null?`<span class="pill p-${e.seg>=0.1?'verde':e.seg>=0?'tierra':'rojo'}">${nf(e.seg*100,0)} %</span>`:'–']))}
    <p class="rs">El <b>precio mínimo</b> es lo menos que te deben pagar por kilo para no perder, con el peso al que planeas vender. El <b>peso mínimo</b> es lo que debe pesar cada animal para no perder con tu precio de hoy (${pk(pvKg())}). La <b>seguridad</b> es cuánto puede bajar el precio antes de perder. ${peor&&peor.e.seg!=null&&peor.e.seg<0.08?`Cuida <b>${esc(peor.x.l.nombre)}</b>: con una baja de ${nf(Math.max(0,peor.e.seg*100),0)} % en el precio ya no gana. Negocia el precio antes de la salida.`:'Tus lotes aguantan una baja de precio razonable.'}</p>`};
}

/* ================= en qué se va el dinero ================= */
const PARTES=[['compra','Compra y flete','var(--s1)'],['alim','Alimento','var(--s2)'],['san','Sanidad','var(--s3)'],['gas','Gastos del lote','var(--s4)'],['gen','Gastos generales','var(--s5)']];
function costos(L){
  const t={compra:0,alim:0,san:0,gas:0,gen:0};
  for(const x of L){t.compra+=x.compra||0;t.alim+=(x.costoAlim||0)+(x.proy?x.proy.alimRest||0:0);t.san+=x.san||0;t.gas+=x.gas||0;t.gen+=(x.gasGen||0)+(x.proy?x.proy.genRest||0:0);}
  return t;
}
function barraCostos(t){
  const tot=Object.values(t).reduce((s,v)=>s+Math.max(0,v),0);if(!(tot>0))return '<p class="empty">Todavía no hay costos anotados.</p>';
  return `<div class="fz-comp">${PARTES.filter(([k])=>t[k]>0).map(([k,n,c])=>`<i style="width:${(t[k]/tot*100).toFixed(2)}%;background:${c}"></i>`).join('')}</div>
   <div class="fz-leg">${PARTES.filter(([k])=>t[k]>0).map(([k,n,c])=>`<div><i style="background:${c}"></i><span>${n}</span><b>${money(t[k])}</b><em>${nf(t[k]/tot*100,0)} %</em></div>`).join('')}</div>`;
}
function costosHtml(){
  const C=calc(),A=C.act;if(!A.length)return {html:'No tienes lotes en engorde.'};
  const t=costos(A),sinCompra=t.alim+t.san+t.gas+t.gen,pa=sinCompra?t.alim/sinCompra:0;
  const porKg=A.map(x=>({x,v:x.costoKgGan})).filter(o=>o.v>0).sort((a,b)=>b.v-a.v);
  return {html:`<b>En qué se va tu dinero</b><p class="rs" style="margin-top:2px">Lotes en engorde, con lo que falta hasta la meta.</p>${barraCostos(t)}
    <p class="rs">Sin contar la compra, el alimento es el <b>${nf(pa*100,0)} %</b> de tus costos. ${pa>0.8?'Es alto: una mejor dieta o mejores precios de maíz y soya son tu mayor palanca.':pa<0.55?'Es bajo para un engorde en corral: revisa si los gastos generales o la sanidad se están comiendo el margen.':'Está en lo normal para un engorde en corral (55 a 80 %).'}</p>
    ${porKg.length>1?`<p class="rh">Costo por kilo ganado</p>${hBars(porKg.map(o=>({label:o.x.l.nombre,v:o.v})),{fmt:v=>pk(v)})}<p class="rs">Compáralo con tu precio de venta (${pk(pvKg())}): si el kilo ganado cuesta más que lo que te pagan por kilo, ese lote gana solo por la diferencia entre el precio de compra y el de venta.</p>`:''}`};
}

/* ================= flujo de caja de 90 días ================= */
function flujo90(){
  const C=calc(),H=C.H,fin=(S.config.fin||{}),caja=+((fin.caja||{}).efectivo)||0;
  const sem=[];for(let i=0;i<13;i++)sem.push({f:addDias(H,i*7),ent:0,sal:0,ev:[]});
  for(const x of C.act){
    const dFin=x.proy?Math.max(0,x.diasMeta||0):0,cons=x.consumo||x.pesoHoy*0.026,dia=(cons*(x.costoKgR||0)+(C.genDia||0))*x.cab;
    for(let d=0;d<Math.min(91,dFin||91);d++){const w=Math.floor(d/7);if(sem[w])sem[w].sal+=dia;}
    if(x.proy&&dFin<91){const w=Math.floor(dFin/7);if(sem[w]){sem[w].ent+=x.proy.venta;sem[w].ev.push(`venta de ${x.l.nombre}`);}}
  }
  for(const c of (fin.creditos||[])){try{const q=Fin.credito(c);if(q&&q.cuota&&q.saldo>0)for(let m=1;m<=3;m++){const w=Math.floor(m*30/7);if(sem[w]){sem[w].sal+=q.cuota;sem[w].ev.push(`cuota de ${c.n}`);}}}catch(e){}}
  let acc=caja;const pts=[[H,acc/1000]];let min={v:acc,f:H};
  for(const s of sem){acc+=s.ent-s.sal;s.acc=acc;pts.push([addDias(s.f,6),acc/1000]);if(acc<min.v)min={v:acc,f:addDias(s.f,6)};}
  return {sem,pts,caja,min,fin:acc};
}
function flujoHtml(){
  const C=calc();if(!C.act.length)return {html:'No tienes lotes en engorde.'};
  const F=flujo90(),ent=F.sem.reduce((s,x)=>s+x.ent,0),sal=F.sem.reduce((s,x)=>s+x.sal,0);
  const ev=F.sem.filter(s=>s.ev.length).slice(0,6);
  const ch=lineChart({series:[{name:'Dinero disponible (miles)',color:'var(--s2)',pts:F.pts}],refs:[{v:0,color:'var(--rojo)',label:'sin dinero'}],h:190})+`<p class="rs" style="margin-top:4px">Dinero disponible cada semana, en miles de ${esc(S.config.moneda||'L')}.</p>`;
  return {html:`<b>Tu dinero en los próximos 90 días</b>${ch}
    ${kvs([['Tienes hoy',money(F.caja)],['Va a entrar',`<b style="color:var(--verde)">${money(ent)}</b>`],['Va a salir',`<b style="color:var(--rojo)">${money(sal)}</b>`],['En 90 días tendrías',`<b style="color:var(--${col(F.fin)})">${money(F.fin)}</b>`]])}
    ${F.min.v<0?`<p class="rs"><b style="color:var(--rojo)">Ojo:</b> alrededor del <b>${ffl(F.min.f)}</b> te faltarían <b>${money(-F.min.v)}</b>. Adelanta una venta, negocia el pago del alimento o consigue capital antes de esa fecha.</p>`:`<p class="rs">No te falta dinero en los próximos 90 días con lo que sé hoy.</p>`}
    ${ev.length?`<p class="rh">Lo que mueve tu dinero</p>${tablaW(['Semana del','Qué pasa'],ev.map(s=>[ffc(s.f),esc(s.ev.join(', '))]))}`:''}
    <p class="rs">Salidas: alimento y gastos generales de cada lote hasta su meta, y cuotas de créditos. Entradas: la venta de cada lote al llegar a la meta. Anota tu efectivo en Finanzas para que empiece de lo que tienes.</p>`,
    btns:[['Anotar mi efectivo',{t:'form',k:'finCaja'}],['Ver Finanzas',{t:'go',go:'#finanzas'}]]};
}

/* ================= tú contra la referencia ================= */
/* rangos de engorde en corral en el trópico (NASEM 2016, experiencia de corrales en Centroamérica) */
const REF=[
 {k:'gdp',t:'Ganancia diaria',u:'kg/día',malo:0.8,bueno:1.4,max:2,dec:2,alto:true,txt:'1.2 a 1.6 kg por día es muy bueno en corral con cruces cebú.'},
 {k:'conv',t:'Conversión',u:'a 1',malo:9.5,bueno:7.5,max:12,min:4,dec:1,alto:false,txt:g5('De 6 a 8 de alimento (tal como se sirve) por cada 1 de peso ganado es lo normal; menos es mejor.')},
 {k:'mort',t:'Mortalidad',u:'%',malo:3,bueno:1,max:5,min:0,dec:1,alto:false,txt:'Menos de 1 % es excelente; más de 2 % indica problemas de recepción o sanidad.'},
 {k:'costo',t:'Costo del kilo ganado',u:'% del precio',malo:95,bueno:75,max:130,min:30,dec:0,alto:false,txt:'Si el kilo ganado cuesta menos del 75 % de lo que te pagan por kilo, el engorde deja dinero por sí solo.'},
 {k:'cms',t:'Consumo',u:'% del peso',malo:1.8,bueno:2.5,max:3.5,min:1,dec:1,alto:true,txt:'Comen de 2.2 a 3 % de su peso en materia seca al día; poco consumo frena la ganancia.'}
];
function valoresFinca(){
  const C=calc(),A=C.act,w=A.reduce((s,x)=>s+x.cab,0)||1,pond=(f)=>{const v=A.filter(x=>f(x)!=null&&isFinite(f(x)));const ww=v.reduce((s,x)=>s+x.cab,0);return ww?v.reduce((s,x)=>s+f(x)*x.cab,0)/ww:null;};
  return {gdp:pond(x=>x.gdp!=null?x.gdpUse:null),conv:pond(x=>x.conv||null),mort:pond(x=>x.mort*100),costo:pvKg()?pond(x=>x.costoKgGan>0?x.costoKgGan/pvKg()*100:null):null,cms:pond(x=>x.consumo&&x.pesoHoy?x.consumo*0.85/x.pesoHoy*100:null)};
}
function refHtml(){
  const C=calc();if(!C.act.length)return {html:'No tienes lotes en engorde.'};
  const V=valoresFinca();
  const fila=r=>{const v=V[r.k];if(v==null)return '';const lo=r.min??0,hi=r.max,pos=cl((v-lo)/(hi-lo))*100;
    const a=cl((Math.min(r.malo,r.bueno)-lo)/(hi-lo))*100,b=cl((Math.max(r.malo,r.bueno)-lo)/(hi-lo))*100;
    const ok=r.alto?v>=r.bueno:v<=r.bueno,mal=r.alto?v<r.malo:v>r.malo,c=ok?'verde':mal?'rojo':'tierra';
    const bg=r.alto?`linear-gradient(90deg,var(--rojo) 0 ${a}%,var(--tierra) ${a}% ${b}%,var(--verde) ${b}%)`:`linear-gradient(90deg,var(--verde) 0 ${a}%,var(--tierra) ${a}% ${b}%,var(--rojo) ${b}%)`;
    return `<div class="rp-ref"><div class="rp-ref-h"><span>${r.t}</span><b style="color:var(--${c})">${nf(v,r.dec)} ${r.k==='gdp'?'kg':r.u}</b></div><div class="rp-ref-t" style="background:${bg}"><u style="left:${pos.toFixed(1)}%"></u></div><small>${r.txt}</small></div>`;};
  const pos=r=>{const v=V[r.k];if(v==null)return 1;const t=r.alto?(v-r.malo)/(r.bueno-r.malo):(r.malo-v)/(r.malo-r.bueno);return t;};
  const malos=REF.filter(r=>V[r.k]!=null&&pos(r)<1).sort((a,b)=>pos(a)-pos(b));
  return {html:`<b>Tú contra la referencia</b><p class="rs" style="margin-top:2px">Tu promedio de lotes en engorde, pesado por cabezas. Verde es bueno; rojo, a mejorar.</p>${REF.map(fila).join('')}
    <p class="rs">${malos.length?`Tu mayor oportunidad está en <b>${malos[0].t.toLowerCase()}</b>${malos.length>1?`, y luego en ${malos.slice(1,3).map(r=>r.t.toLowerCase()).join(' y ')}`:''}.`:'Estás en buenos rangos en todo. Cuida la constancia.'}</p>`};
}

/* ================= señales de alerta ================= */
function senales(){
  const C=calc(),H=C.H,S0=[];
  const add=(p,t,s,b)=>S0.push({p,t,s,b});
  for(const x of C.act){
    const n=x.l.nombre;
    // ganancia reciente contra la de todo el engorde
    if(x.gdpRec!=null&&x.gdpTot>0&&x.gdpRec<x.gdpTot*0.85)add(0.8,`${n} bajó su ganancia ${nf((1-x.gdpRec/x.gdpTot)*100,0)} %`,`Ganaba ${nf(x.gdpTot,2)} kg por día en promedio y en el último pesaje ${nf(x.gdpRec,2)}. Revisa ración, agua, calor y salud.`,['Ver el lote',{t:'go',go:'#lote/'+x.id}]);
    // consumo de los últimos 3 días contra las 2 semanas anteriores
    const c3=[1,2,3].map(i=>x.consDia[addDias(H,-i)]).filter(v=>v!=null),c14=[...Array(14).keys()].map(i=>x.consDia[addDias(H,-4-i)]).filter(v=>v!=null);
    if(c3.length>=2&&c14.length>=5){const a=c3.reduce((s,v)=>s+v,0)/c3.length,b=c14.reduce((s,v)=>s+v,0)/c14.length;
      if(a<b*0.9)add(0.85,`${n} está comiendo ${nf((1-a/b)*100,0)} % menos`,`${nf(a,1)} kg por cabeza en los últimos días contra ${nf(b,1)} antes. Es la primera señal de enfermedad o calor.`,['Ver el lote',{t:'go',go:'#lote/'+x.id}]);}
    // animales que pierden peso
    const pierden=(x.animAct||[]).filter(a=>a.gdp!=null&&a.gdp<0);
    if(pierden.length)add(0.9,`${pl(pierden.length,'animal pierde','animales pierden')} peso en ${n}`,`Aretes ${pierden.slice(0,5).map(a=>a.arete).join(', ')}. Revísalos hoy.`,['Semáforo del lote',{t:'fn',fn:'semaforo',lote:x.id}]);
    // retiro que choca con la venta
    if(x.retiroHasta&&x.fechaMeta&&x.retiroHasta>x.fechaMeta)add(0.75,`${n} llega a la meta antes de terminar su retiro`,`Meta el ${ffc(x.fechaMeta)}, retiro de ${x.retiroProd} hasta el ${ffc(x.retiroHasta)}. No lo vendas a matadero antes.`,null);
    // costo del kilo ganado alto
    if(x.costoKgGan>0&&pvKg()&&x.costoKgGan>pvKg()*0.9)add(0.6,`En ${n} el kilo ganado cuesta casi lo que te pagan`,`Te cuesta ${pk(x.costoKgGan)} y te pagan ${pk(pvKg())}. Revisa la dieta y el desperdicio.`,['Formular dieta',{t:'go',go:'#formular'}]);
    // comederos: sobra mucho o se vacían
    const L=x.g.alimento.filter(a=>a.lect!=null&&dias(a.f,H)<=5).map(a=>+a.lect);
    if(L.length>=3){const sob=L.filter(v=>v===3).length/L.length,vac=L.filter(v=>v===0).length/L.length;
      if(sob>=0.5)add(0.5,`En ${n} sobra comida seguido`,'Más de la mitad de las veces sobra alimento: baja un poco la ración y ahorras sin perder ganancia.',null);
      if(vac>=0.5)add(0.55,`En ${n} el comedero se vacía seguido`,'Se quedan sin comida: sube un poco la ración o agrega una entrega.',null);}
    if(x.mort>0.02)add(0.7,`${n}: mortalidad de ${nf(x.mort*100,1)} %`,'Arriba del 2 % es alto. Repasa recepción, vacunas y agua.',['Ver el lote',{t:'go',go:'#lote/'+x.id}]);
  }
  // alimento que se acaba
  try{for(const i of ((S.config.bodega||{}).items||[])){const e=Extras.bodega.estado(i);if(e&&e.dias!=null&&e.dias<(+i.min||7))add(e.dias<3?0.95:0.7,`${i.n} alcanza para ${pl(Math.max(0,Math.floor(e.dias)),'día','días')}`,'Compra antes de que se acabe: un cambio brusco de dieta baja la ganancia.',['Ver la bodega',{t:'go',go:'#bodega'}]);}}catch(e){}
  // medicinas que vencen o se acaban
  try{const fn=S.config.fin||{};for(const it of (fn.insumos||[])){const q=Fin.insumo(it,fn.imovs||[]);if(q.sin)continue;if(it.min&&q.stock<=+it.min)add(0.45,`Quedan pocas unidades de ${it.n}`,`Tienes ${nf(q.stock)} y pediste aviso con ${nf(+it.min)}.`,['Ver inventario',{t:'go',go:'#inventario'}]);if(q.vence!=null&&q.vence<=30&&q.vence>=0)add(0.4,`${it.n} vence el ${ffc(q.venc)}`,'Úsalo primero o cámbialo.',null);}}catch(e){}
  // dinero
  try{const F=flujo90();if(F.min.v<0)add(0.65,`Te faltaría dinero cerca del ${ffc(F.min.f)}`,`Unos ${money(-F.min.v)} según tus gastos y ventas esperadas.`,['Ver el flujo',{t:'fn',fn:'flujo'}]);}catch(e){}
  return S0.sort((a,b)=>b.p-a.p);
}
function senalesHtml(){
  const L=senales();
  if(!L.length)return {html:'<b>Sin señales de alerta.</b><p class="rs">Revisé ganancia, consumo, pesos, retiros, costos, comederos, bodega, medicinas y dinero. Todo está en orden.</p>'};
  return {html:`<b>${pl(L.length,'señal','señales')} que merece${L.length===1?'':'n'} tu atención</b><div class="rp-sen">${L.slice(0,8).map(s=>`<div class="rp-s ${s.p>=0.8?'r':s.p>=0.6?'a':'g'}"><b>${esc(s.t)}</b><span>${esc(s.s)}</span></div>`).join('')}</div>`,
    btns:L.filter(s=>s.b).slice(0,3).map(s=>s.b)};
}

/* ================= precio máximo de compra ================= */
function compraMax(pesoC,margen=0.1){
  const C=calc(),A=C.act.concat(C.cer),pv=pvKg();if(!pv)return null;
  const gdp=(()=>{const v=A.filter(x=>(x.activo?x.gdpUse:x.gdpTot)>0);return v.length?v.reduce((s,x)=>s+(x.activo?x.gdpUse:x.gdpTot),0)/v.length:+S.config.gdpEsperada||1.3;})();
  const meta=+S.config.metaKg||480,cKg=(()=>{const v=C.act.filter(x=>x.costoKgR>0);return v.length?v.reduce((s,x)=>s+x.costoKgR,0)/v.length:0;})();
  const flete=(()=>{const v=A.filter(x=>x.cab0>0&&x.l.flete);return v.length?v.reduce((s,x)=>s+(+x.l.flete||0)/x.cab0,0)/v.length:0;})();
  const d=Math.max(0,Math.ceil((meta-pesoC)/gdp));
  let alim=0;for(let i=0;i<d;i++){const w=pesoC+gdp*i;alim+=w*0.026*cKg;}
  const otros=(C.genDia||0)*d+15*d/30*0+ (C.act.length?C.act.reduce((s,x)=>s+(x.san||0)/Math.max(1,x.cab0),0)/C.act.length:0);
  const venta=meta*(1-des())*pv,costoSinCompra=alim+otros+flete;
  // ganar 'margen' sobre lo invertido: venta = (compra + costoSinCompra)*(1+margen)
  const pMax0=(venta-costoSinCompra)/pesoC,pMaxM=(venta/(1+margen)-costoSinCompra)/pesoC;
  return {pesoC,d,gdp,alim,otros,flete,venta,pMax0,pMaxM,meta};
}
function compraHtml(){
  const C=calc();if(!pvKg())return {html:'Pon tu precio de venta en Configuración para calcular esto.'};
  const base=C.act.length?C.act.reduce((s,x)=>s+x.p0,0)/C.act.length:250,paso=U()==='lb'?25:10;
  const pesos=[...new Set([0.7,0.85,1,1.15,1.3].map(f=>Math.round(W(base*f)/paso)*paso))].map(v=>toKg(v)).filter(p=>p>80&&p<(+S.config.metaKg||480)-20);
  const R=pesos.map(p=>compraMax(p)).filter(Boolean);if(!R.length)return {html:'Necesito tu peso meta y tu precio de venta.'};
  const actual=C.act.filter(x=>x.l.precioCompraKg).map(x=>+x.l.precioCompraKg);const pAct=actual.length?actual.reduce((s,v)=>s+v,0)/actual.length:null;
  return {html:`<b>¿Cuánto pagar por el próximo lote?</b>${tablaW(['Peso','Días','Sin perder','Ganando 10 %'],R.map(r=>[nf(r.pesoC)+' kg',nf(r.d),pk(r.pMax0),`<b>${pk(r.pMaxM)}</b>`]))}
    <p class="rs">Es el precio máximo por kilo en pie que puedes pagar con tu ganancia diaria (${nf(R[0].gdp,2)} kg), tu ración, tus gastos y tu precio de venta de hoy (${pk(pvKg())}), vendiendo a ${nf(R[0].meta)} kg. ${pAct?`En tus lotes actuales pagaste en promedio ${pk(pAct)}.`:''} Los animales más livianos aguantan un precio más alto por kilo porque ganan más kilos en tu finca.</p>`};
}

/* ================= calendario de ventas y rendimiento del dinero ================= */
function calendarioHtml(){
  const C=calc(),A=conProy().slice().sort((a,b)=>(a.fechaMeta||'9')<(b.fechaMeta||'9')?-1:1);if(!A.length)return {html:'No tienes lotes en engorde.'};
  const tasa=(()=>{const c=((S.config.fin||{}).creditos||[]).filter(c=>+c.tasa>0);return c.length?c.reduce((s,c)=>s+(+c.tasa),0)/c.length:12;})();
  const rows=A.map(x=>{const dTot=Math.max(1,(x.dec||0)+(x.diasMeta||0)),roi=x.proy.costoFinal?x.proy.margen/x.proy.costoFinal:0,anual=roi*365/dTot;return {x,roi,anual};});
  return {html:`<b>Calendario de ventas</b>${tablaW(['Lote','Sale','Cabezas','Venta'],rows.map(({x})=>[esc(x.l.nombre),x.listo?'<b>Listo</b>':ffc(x.fechaMeta),nf(x.proy.cabBase),fmtK(x.proy.venta)]))}
    <p class="rh">¿Cuánto rinde tu dinero al año?</p>${hBars(rows.map(r=>({label:r.x.l.nombre,v:r.anual*100})),{fmt:v=>nf(v,1)+' %',signed:true})}
    <p class="rs">Es la ganancia sobre lo invertido llevada a un año. Compárala con la tasa de tu banco (${nf(tasa,1)} % anual): ${rows.filter(r=>r.anual*100<tasa).length?`<b>${rows.filter(r=>r.anual*100<tasa).map(r=>esc(r.x.l.nombre)).join(', ')}</b> rinde${rows.filter(r=>r.anual*100<tasa).length>1?'n':''} menos que el banco; revisa sus costos o su precio.`:'todos tus lotes rinden más que el banco.'}</p>`};
}

/* ================= escenarios típicos (respuesta de Rumi) ================= */
function escenariosTipicos(){
  const A=conProy();if(!A.length)return {html:'No tienes lotes en engorde para simular.'};
  const tot=o=>A.reduce((s,x)=>s+RumiPro.margenCon(x,o).m,0),b=tot({});
  const E=[['El precio baja 10 %',{fp:0.9}],['El precio sube 10 %',{fp:1.1}],['El maíz y la soya suben 20 %',{fa:1.2}],['Ola de calor: ganan 15 % menos',{fg:0.85}],['Mejoras la dieta: ganan 10 % más',{fg:1.1}],['Todo en contra junto',{fp:0.9,fa:1.2,fg:0.85}]];
  const R=E.map(([t,o])=>{const v=tot(o);return [t,v];});
  return {html:`<b>¿Qué pasa si…?</b><p class="rs" style="margin-top:2px">Margen de todos tus lotes en engorde al llegar a su meta. Hoy: <b>${money(b)}</b>.</p>${tablaW(['Si…','Tu margen','Cambio'],R.map(([t,v])=>[t,`<b style="color:var(--${col(v)})">${fmtK(v)}</b>`,`<span style="color:var(--${v>=b?'verde':'rojo'})">${v>=b?'+':'−'}${fmtK(Math.abs(v-b)).replace(/^−/,'')}</span>`]))}
    <p class="rs">${R[5][1]<0?'Si todo sale mal a la vez, perderías dinero: asegura el precio de venta o compra el alimento con tiempo.':'Aun con todo en contra, tu engorde gana. Buen colchón.'} Mueve cada cosa a tu gusto en el simulador.</p>`,
    btns:[['Abrir el simulador',{t:'fn',fn:'simulador'}],['Riesgo del negocio',{t:'fn',fn:'riesgoFinca'}]]};
}

/* ================= simulador con controles ================= */
const SIM={p:0,a:0,g:0,m:0};
const SLID=[['p','Precio de venta',-30,30],['a','Costo del alimento',-30,50],['g','Ganancia diaria',-30,30],['m','Muertes extra',0,5]];
function simRes(){
  const A=conProy();if(!A.length)return '<p class="empty">No tienes lotes en engorde.</p>';
  const o={fp:1+SIM.p/100,fa:1+SIM.a/100,fg:1+SIM.g/100};
  const R=A.map(x=>{const b=RumiPro.margenCon(x).m;const e=RumiPro.margenCon(x,{...o,muertes:Math.round(x.cab*SIM.m/100)});return {x,b,e:e.m,d:e.d};});
  const tb=R.reduce((s,r)=>s+r.b,0),te=R.reduce((s,r)=>s+r.e,0),dif=te-tb;
  const mx=Math.max(1,...R.map(r=>Math.max(Math.abs(r.b),Math.abs(r.e))));
  return `<div class="rp-simtot"><div><span>Hoy</span><b>${fmtK(tb)}</b></div><div><span>Con tu escenario</span><b style="color:var(--${col(te)})">${fmtK(te)}</b></div><div><span>Diferencia</span><b style="color:var(--${dif>=0?'verde':'rojo'})">${dif>=0?'+':'−'}${fmtK(Math.abs(dif)).replace(/^−/,'')}</b></div></div>
   <div class="rp-simL">${R.map(r=>`<div><span>${esc(r.x.l.nombre)}</span><div class="rp-simb"><i class="h" style="width:${(Math.abs(r.b)/mx*100).toFixed(1)}%;background:var(--card2)"></i><i style="width:${(Math.abs(r.e)/mx*100).toFixed(1)}%;background:var(--${col(r.e)})"></i></div><b style="color:var(--${col(r.e)})">${fmtK(r.e)}</b></div>`).join('')}</div>
   <p class="rs">${te<0?'<b>Con este escenario pierdes dinero.</b> ':''}${SIM.g?`Con otra ganancia diaria cambian los días a la meta y el alimento que falta. `:''}La barra gris es el margen de hoy; la de color, con tu escenario.</p>`;
}
function simuladorHtml(){
  const pre=[['Precio −10 %',{p:-10,a:0,g:0,m:0}],['Maíz +20 %',{p:0,a:20,g:0,m:0}],['Calor fuerte',{p:0,a:0,g:-15,m:1}],['Todo en contra',{p:-10,a:20,g:-15,m:1}],['Todo a favor',{p:8,a:-10,g:10,m:0}],['Como hoy',{p:0,a:0,g:0,m:0}]];
  return `<div class="rp-pre">${pre.map(([t,v])=>`<button type="button" class="chip" data-act="simPre" data-v='${JSON.stringify(v)}'>${t}</button>`).join('')}</div>
   <div class="rp-sl">${SLID.map(([k,t,a,b])=>`<label><span>${t}</span><b id="simv_${k}">${SIM[k]>0&&k!=='m'?'+':''}${SIM[k]} %</b><input type="range" min="${a}" max="${b}" step="1" value="${SIM[k]}" data-sim="${k}" aria-label="${t}"></label>`).join('')}</div>
   <div id="simRes">${simRes()}</div>`;
}
document.addEventListener('input',e=>{const k=e.target.dataset&&e.target.dataset.sim;if(!k)return;SIM[k]=+e.target.value;const v=document.getElementById('simv_'+k);if(v)v.textContent=`${SIM[k]>0&&k!=='m'?'+':''}${SIM[k]} %`;
  cancelAnimationFrame(simRes.t);simRes.t=requestAnimationFrame(()=>{const r=document.getElementById('simRes');if(r)r.innerHTML=aUnidad(simRes());});});

/* ================= Rumi: nuevas opciones en su área de análisis ================= */
const porLote=(t,fn)=>({l:t,sub:()=>{const L=calc().act;if(!L.length)return {t,intro:'Todavía no tienes lotes en engorde.',ops:[{l:'Crear un lote',form:'lote',p:{}}]};
  return {t,intro:'¿De qué lote?',ops:L.map(x=>({l:x.l.nombre,small:pl(x.cab,'cabeza','cabezas'),fn:()=>fn(x)}))};}});
const FN={flujo:flujoHtml,senales:senalesHtml,riesgoFinca:()=>RumiPro.riesgoFinca(),vender:a=>cuandoVender(calc().L[(a||{}).lote])};
const A=RumiMenu.AREAS.find(a=>a.id==='pro');
if(A){const o=A.ops;A.ops=()=>{const L=o();const i=Math.max(0,L.findIndex(x=>x&&x.l&&x.l.startsWith('Riesgo del negocio')));
  L.splice(i,0,{l:'Señales de alerta',ic3:'chispa',fn:senalesHtml},porLote('¿Cuándo me conviene vender?',cuandoVender),{l:'Punto de equilibrio',fn:equilibrioHtml},{l:'¿En qué se va mi dinero?',ic3:'cartera',fn:costosHtml},{l:'Mi dinero en los próximos 90 días',ic3:'banco',fn:flujoHtml},{l:'Yo contra la referencia',fn:refHtml},{l:'¿Cuánto pagar por el próximo lote?',fn:compraHtml},{l:'Calendario de ventas y rendimiento',fn:calendarioHtml});
  return L;};
  A.s='Puntaje, alertas, simulador, ventas, dinero y riesgo';}

/* ================= página de Análisis: todo junto, con índice ================= */
const _pag=PAGES.analisis;
PAGES.analisis=function(id){
  const C=calc();if(!C.act.length)return _pag.apply(this,arguments);
  const card=(k,t,r,sub='')=>{const bt=(r.btns||[]).filter(b=>b&&b[1]).slice(0,3).map(([l,a])=>{const i='p'+(++RUMI.n);RUMI.acts[i]=a;return `<button type="button" class="btn sm" data-act="rumiDo" data-id="${i}">${l}</button>`;}).join('');
    const html=/^(flujo|ref|compra|calendario|costos|equilibrio)$/.test(k)?r.html.replace(/^<b>[^<]*<\/b>/,''):r.html;
    return `<section class="gcard rp-card" id="rp_${k}"><h3>${t}</h3>${sub?`<p class="rp-sub">${sub}</p>`:''}<div class="rmsg-like">${html}</div>${bt?`<div class="acts">${bt}</div>`:''}</section>`;};
  const lid=UI.rpLote&&C.L[UI.rpLote]&&C.L[UI.rpLote].activo?UI.rpLote:C.act[0].id;
  const chipsL=C.act.map(x=>`<button type="button" class="chip" data-act="ui" data-k="rpLote" data-v="${esc(x.id)}" aria-pressed="${x.id===lid}">${esc(x.l.nombre)}</button>`).join('');
  const IDX=[['chequeo','Chequeo'],['senales','Alertas'],['sim','Simulador'],['vender','Cuándo vender'],['equilibrio','Equilibrio'],['costos','Costos'],['flujo','Flujo 90 días'],['ref','Referencia'],['compra','Compra'],['calendario','Calendario'],['riesgo','Riesgo'],['semana','Semana'],['prov','Proveedores']];
  const h=`<header class="hd">${hdBack('#mas','Más','<span class="sync"></span>')}<div class="ttl" style="margin-top:-6px"><span class="eyebrow">${I('chispa')} Rumi piensa con tus datos</span><h1>Análisis de Rumi</h1><p class="sub">Puntaje, alertas, simulador, cuándo vender, tu dinero y el riesgo.</p></div></header>
   <main class="bd" style="gap:18px"><nav class="rp-idx" aria-label="Partes del análisis">${IDX.map(([k,t])=>`<a href="#analisis/${k}" data-rp="${k}">${t}</a>`).join('')}</nav>
   ${card('chequeo','Chequeo de tu engorde',RumiPro.chequeoFinca())}
   ${card('senales','Señales de alerta',senalesHtml(),'Lo que cambió y merece tu atención.')}
   <section class="gcard rp-card" id="rp_sim"><h3>Simulador: ¿qué pasa si…?</h3><p class="rp-sub">Mueve cada control y mira al momento cómo queda tu margen al vender cada lote en su meta.</p>${simuladorHtml()}</section>
   <section class="gcard rp-card" id="rp_vender"><h3>¿Cuándo me conviene vender?</h3><div class="chips rp-chl">${chipsL}</div><div class="rmsg-like">${cuandoVender(C.L[lid]).html.replace(/^<b>[^<]*<\/b>/,'')}</div></section>
   ${card('equilibrio','Punto de equilibrio',equilibrioHtml())}
   ${card('costos','¿En qué se va tu dinero?',costosHtml())}
   ${card('flujo','Tu dinero en 90 días',flujoHtml())}
   ${card('ref','Tú contra la referencia',refHtml())}
   ${card('compra','¿Cuánto pagar por el próximo lote?',compraHtml())}
   ${card('calendario','Calendario de ventas y rendimiento',calendarioHtml())}
   ${card('riesgo','Riesgo del negocio',RumiPro.riesgoFinca())}
   ${card('semana','Tu semana',RumiPro.semana())}
   ${card('prov','Proveedores y razas',{html:RumiPro.rankingHtml('prov').html+RumiPro.rankingHtml('raza').html})}
   <p class="hint">Rumi usa solo tus registros y la experiencia del engorde en corral. Son estimaciones para decidir mejor; para medicinas y casos graves, consulta a tu veterinario.</p></main>`;
  if(id)setTimeout(()=>{const e=document.getElementById('rp_'+id);if(e)e.scrollIntoView({block:'start',behavior:'smooth'});},120);
  return h;
};
Object.assign(ACTS,{
  simPre:el=>{let v;try{v=JSON.parse(el.dataset.v);}catch(e){return;}Object.assign(SIM,v);for(const [k] of SLID){const i=document.querySelector(`[data-sim="${k}"]`);if(i)i.value=SIM[k];const b=document.getElementById('simv_'+k);if(b)b.textContent=`${SIM[k]>0&&k!=='m'?'+':''}${SIM[k]} %`;}
    const r=document.getElementById('simRes');if(r)r.innerHTML=aUnidad(simRes());}
});
/* el índice de la página no cambia de ruta: solo baja a la parte */
document.addEventListener('click',e=>{const a=e.target.closest&&e.target.closest('.rp-idx a');if(!a)return;e.preventDefault();const s=document.getElementById('rp_'+a.dataset.rp);if(s)s.scrollIntoView({block:'start',behavior:'smooth'});},true);

window.RumiMas={FN,senales,senalesHtml,cuandoVender,curva,equilibrio,equilibrioHtml,costosHtml,flujo90,flujoHtml,refHtml,compraHtml,calendarioHtml,escenariosTipicos,simuladorHtml,valoresFinca};
})();
