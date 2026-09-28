/* Rumentis: más análisis de Rumi con tus propios registros.
   - ¿Estoy dando de más o de menos? (consumo, tendencia y lectura de comedero)
   - ¿De dónde sale mi ganancia? (compra y venta contra engorde)
   - Comparar mis lotes (lado a lado, con el mejor y el peor)
   - Sanidad y muertes: qué me dicen
   - ¿Cuándo se me acaba el alimento? (días de bodega)
   - Lo que te está costando dinero (ordenado por plata a la semana)
   Todo se agrega al área de análisis del menú de Rumi y a la página #analisis. */
(function(){
if(!window.RumiMenu||!window.RumiPro)return;
const pct=(v,d=0)=>`${nf(v*100,d)} %`;
const pv=()=>+S.config.precioVentaKg||0;
const des=()=>Math.max(0,Math.min(15,+S.config.desbaste||0))/100;
const pill=(c,t)=>`<span class="pill p-${c}">${t}</span>`;
const nota=t=>`<p class="rs">${t}</p>`;
const lotesConDatos=()=>calc().act.filter(x=>x.g.alimento.length);

/* ---------- 1. ¿Estoy dando de más o de menos? ---------- */
function comedero(x){
  const H=calc().H;
  // consumo por cabeza: últimos 7 días contra los 7 anteriores
  const prom=(a,b)=>{let s=0,n=0;for(let i=a;i<=b;i++){const v=x.consDia[addDias(H,-i)];if(v!=null){s+=v;n++;}}return n?s/n:null;};
  const c1=prom(1,7),c0=prom(8,14),tend=c1&&c0?c1/c0-1:null;
  const pesoPct=c1&&x.pesoHoy?c1/x.pesoHoy:null;
  const lect=x.g.alimento.filter(a=>a.lect!=null&&a.lect!=='').slice(-6).map(a=>+a.lect);
  const vac=lect.filter(v=>v===0).length,sob=lect.filter(v=>v===3).length;
  let hacer,c;
  if(lect.length>=3&&vac>=Math.max(2,lect.length/2)){hacer='Sube la ración 3 a 5 %: se la terminan y esperan.';c='tierra';}
  else if(lect.length>=3&&sob>=Math.max(2,lect.length/2)){hacer='Baja la ración 3 a 5 %: está sobrando y se desperdicia.';c='tierra';}
  else if(tend!=null&&tend<-0.1){hacer='Comen menos que la semana pasada: revisa agua, calor, salud y la mezcla.';c='rojo';}
  else if(pesoPct!=null&&pesoPct<0.018){hacer='Comen poco para su peso: revisa agua, sombra y enfermos.';c='rojo';}
  else if(pesoPct!=null&&pesoPct>0.036){hacer='Consumo muy alto para su peso: revisa desperdicio en el comedero y la báscula del alimento.';c='tierra';}
  else {hacer='Va bien: sigue igual.';c='verde';}
  return {c1,tend,pesoPct,lect,vac,sob,hacer,c,costoDia:c1!=null?c1*(x.costoKgR||0):null};
}
function comederoHtml(){
  const L=lotesConDatos();if(!L.length)return {html:'Todavía no hay entregas de alimento anotadas. Cuando anotes unos días, te digo si estás dando de más o de menos.'};
  const rows=L.map(x=>{const r=comedero(x);return [`<b>${esc(x.l.nombre)}</b>`,r.c1!=null?`${nf(r.c1,1)} kg`:'–',r.pesoPct!=null?pct(r.pesoPct,1):'–',
    r.tend!=null?`${r.tend>=0?'+':''}${nf(r.tend*100,0)} %`:'–',r.lect.length?r.lect.map(v=>LECT[v][0]).join(' '):'–',pill(r.c,r.c==='verde'?'Bien':'Ajustar')];});
  const R=L.map(x=>({x,r:comedero(x)})),aj=R.filter(o=>o.r.c!=='verde');
  const dia=R.reduce((s,o)=>s+(o.r.c1||0)*o.x.cab,0),cost=R.reduce((s,o)=>s+(o.r.costoDia||0)*o.x.cab,0);
  return {html:`<b>¿Estoy dando de más o de menos?</b><br>Tus lotes comen unos <b>${nf(dia)} kg</b> al día, que cuestan <b>${money(cost)}</b> al día.`+
    tabla(['Lote','Por cabeza','% del peso','Contra la semana pasada','Comedero','Estado'],rows)+
    (aj.length?`<p class="rh">Qué hacer</p>`+aj.map(o=>`<b>${esc(o.x.l.nombre)}</b>: ${o.r.hacer}`).join('<br>'):nota('Todos tus lotes comen bien. Sigue leyendo el comedero antes de cada entrega.'))+
    nota('Comedero: V vacío, L limpio, P poco, S sobra. Lo normal en engorde es que coman entre 2.2 y 3 % de su peso cada día (tal como se sirve, un poco más con ensilaje). Cambia la ración de a poco, 3 a 5 % por vez.'),
    btns:[['Anotar alimento',{t:'form',k:'alimento',p:{}}]]};
}

/* ---------- 2. ¿De dónde sale mi ganancia? ---------- */
function origen(x){
  const p=x.activo?pv():(x.g.venta.length?x.g.venta.reduce((s,v)=>s+(+v.kg||0)*(+v.precioKg||0),0)/Math.max(1,x.g.venta.reduce((s,v)=>s+(+v.kg||0),0)):pv());
  const valorInicial=x.p0*x.cab0*p;
  const spread=valorInicial-x.compra;
  const venta=x.activo?(x.proy?x.proy.venta+x.ingresos:0):x.ingresos;
  const costoOp=x.costoOp+(x.activo&&x.proy?x.proy.alimRest:0);
  const engorde=venta-valorInicial-costoOp;
  const kgG=x.activo?Math.max(0,(x.meta-x.p0))*x.cab+Math.max(0,x.kgGan-(x.pesoHoy-x.p0)*x.cab):x.kgGan;
  const costoKg=kgG>0?costoOp/kgG:null;
  return {p,spread,engorde,total:spread+engorde,costoKg,cab:x.cab0-x.bajas||x.cab0};
}
function origenHtml(){
  const C=calc();const L=C.act.concat(C.cer.slice(-4));if(!L.length)return {html:'Aún no tienes lotes.'};
  const rows=L.map(x=>{const o=origen(x);return [`<b>${esc(x.l.nombre)}</b>${x.activo?'':' (vendido)'}`,`<span style="color:var(--${o.spread>=0?'verde':'rojo'})">${money(o.spread)}</span>`,`<span style="color:var(--${o.engorde>=0?'verde':'rojo'})">${money(o.engorde)}</span>`,`<b>${money(o.total)}</b>`];});
  const T=L.map(origen),sp=T.reduce((s,o)=>s+o.spread,0),en=T.reduce((s,o)=>s+o.engorde,0);
  let msg;
  if(sp<0&&en>0)msg=`Ganas <b>engordando</b> (${money(en)}) pero pierdes <b>comprando</b> (${money(sp)}): pagas cada kilo más caro de lo que lo vendes. Negociar la compra o comprar un poco más pesado es donde más puedes mejorar.`;
  else if(sp>=0&&en<0)msg=`Ganas <b>comprando bien</b> (${money(sp)}) pero el <b>engorde</b> te cuesta más de lo que vale lo que suben (${money(en)}). Revisa el costo de la ración, la conversión y los días de más en el corral.`;
  else if(sp<0&&en<0)msg=`Pierdes en la compra (${money(sp)}) y en el engorde (${money(en)}). Antes del próximo lote calcula el precio máximo de compra y revisa la ración.`;
  else msg=`Ganas en las dos partes: en la compra (${money(sp)}) y en el engorde (${money(en)}). Bien.`;
  return {html:`<b>¿De dónde sale mi ganancia?</b><br>Tu margen tiene dos partes: la <b>compra y venta</b> (lo que valen al precio de venta los kilos que compraste, menos lo que pagaste con flete) y el <b>engorde</b> (lo que valen los kilos que suben, menos todo lo que gastaste para que suban).`+
    tabla(['Lote','Compra y venta','Engorde','Margen'],rows)+`<p class="rh">Lo que dice</p>${msg}`+
    nota('En lotes en engorde es la proyección al vender en su meta con tu precio de venta; en los vendidos, lo real.'),btns:[['¿Cuánto pagar por el próximo lote?',{t:'go',go:'#analisis/compra'}]]};
}

/* ---------- 3. Comparar mis lotes ---------- */
function compararHtml(){
  const C=calc();const L=C.act.concat(C.cer.slice(-6));if(L.length<2)return {html:'Necesito al menos dos lotes para compararlos.'};
  const val=x=>{const d=Math.max(1,x.activo?x.dec+x.diasMeta:x.dec),cabB=Math.max(1,x.cab0-x.bajas);
    const m=x.activo?(x.proy?x.proy.margen:null):x.margenReal;
    return {g:x.gdpTot||x.gdp,conv:x.conv,ck:x.costoKgGan,mort:x.mort,md:m!=null?m/cabB/d:null};};
  const V=L.map(x=>({x,v:val(x)}));
  const cols=[['g',1,v=>`${nf(v,2)} kg`],['conv',-1,v=>`${nf(v,1)} a 1`],['ck',-1,v=>pk(v)],['mort',-1,v=>pct(v,1)],['md',1,v=>money2(v)]];
  const best={},worst={};
  for(const [k,s] of cols){const xs=V.filter(o=>o.v[k]!=null&&isFinite(o.v[k]));if(xs.length<2)continue;xs.sort((a,b)=>(a.v[k]-b.v[k])*s);worst[k]=xs[0].x.id;best[k]=xs[xs.length-1].x.id;}
  const rows=V.map(({x,v})=>[`<b>${esc(x.l.nombre)}</b>${x.activo?'':' (vendido)'}`].concat(cols.map(([k,,f])=>v[k]==null||!isFinite(v[k])?'–':`<span style="${best[k]===x.id?'color:var(--verde);font-weight:800':worst[k]===x.id?'color:var(--rojo)':''}">${f(v[k])}${best[k]===x.id?' ★':''}</span>`)));
  const top=V.filter(o=>o.v.md!=null).sort((a,b)=>b.v.md-a.v.md);
  let msg='';if(top.length>=2){const a=top[0],z=top[top.length-1];
    const por=[];if(a.v.g&&z.v.g&&a.v.g>z.v.g*1.05)por.push(`gana ${nf(a.v.g-z.v.g,2)} kg más por día`);if(a.v.conv&&z.v.conv&&a.v.conv<z.v.conv*0.95)por.push(`convierte mejor (${nf(a.v.conv,1)} contra ${nf(z.v.conv,1)})`);
    if(a.v.mort<z.v.mort)por.push(`se le mueren menos (${pct(a.v.mort,1)} contra ${pct(z.v.mort,1)})`);
    const pa=+a.x.l.precioCompraKg||0,pz=+z.x.l.precioCompraKg||0;if(pa&&pz&&pa<pz*0.97)por.push(`se compró más barato (${pk(pa)} contra ${pk(pz)})`);
    msg=`<p class="rh">Lo que dice</p><b>${esc(a.x.l.nombre)}</b> deja <b>${money2(a.v.md-z.v.md)}</b> más por cabeza cada día que <b>${esc(z.x.l.nombre)}</b>${por.length?': '+por.join(', ')+'.':'.'} Repite lo que hiciste con el mejor: mismo proveedor, raza, ración y manejo.`;}
  return {html:`<b>Comparar mis lotes</b><br>Lado a lado, con ★ el mejor de cada columna y en rojo el más flojo.`+tabla(['Lote','Ganancia diaria','Conversión',`Costo por ${UW()} ganado`,'Mortalidad','Margen por cabeza al día'],rows)+msg+
    nota('El margen por cabeza al día pone en la misma balanza lotes cortos y largos. En los lotes en engorde es proyectado a su meta.')};
}

/* ---------- 4. Sanidad y muertes ---------- */
function sanidadHtml(){
  const C=calc();const L=C.act.concat(C.cer.slice(-6));if(!L.length)return {html:'Aún no tienes lotes.'};
  let trat=0,trat21=0,cab0=0,costo=0,bajas=0,bajas21=0;const causas={};
  const rows=L.map(x=>{const t=x.g.sanidad.filter(s=>s.clase==='Tratamiento');const tc=t.reduce((s,i)=>s+(+i.cab||0),0);
    const t21=t.filter(s=>dias(x.l.fechaIngreso,s.f)<=21).reduce((s,i)=>s+(+i.cab||0),0);
    const b21=x.g.baja.filter(b=>dias(x.l.fechaIngreso,b.f)<=21).reduce((s,i)=>s+(+i.cab||0),0);
    for(const b of x.g.baja){const k=b.causa||'Sin causa';causas[k]=(causas[k]||0)+(+b.cab||0);}
    trat+=tc;trat21+=t21;cab0+=x.cab0;costo+=x.san;bajas+=x.bajas;bajas21+=b21;
    const tr=x.cab0?tc/x.cab0:0;
    return [`<b>${esc(x.l.nombre)}</b>`,pct(tr,0),pct(x.mort,1),x.cab0?money2(x.san/x.cab0):'–',pill(x.mort>0.02||tr>0.25?'rojo':x.mort>0.01||tr>0.15?'tierra':'verde',x.mort>0.02||tr>0.25?'Alto':x.mort>0.01||tr>0.15?'Vigilar':'Bien')];});
  const ins=[];
  const tr=cab0?trat/cab0:0,mo=cab0?bajas/cab0:0;
  if(trat&&trat21/trat>=0.5)ins.push(`El <b>${pct(trat21/trat)}</b> de tus tratamientos fue en las <b>primeras 3 semanas</b>. El problema está en la llegada: agua y heno al llegar, descanso, vacuna respiratoria y compras de pocos orígenes.`);
  if(bajas&&bajas21/bajas>=0.5)ins.push(`La mitad o más de tus muertes (${nf(bajas21)} de ${nf(bajas)}) pasan en las primeras 3 semanas: refuerza la recepción y separa rápido a los decaídos.`);
  if(mo>0.015)ins.push(`Tu mortalidad (${pct(mo,1)}) está arriba de lo aceptable en engorde (1 a 1.5 %).`);
  if(tr>0.2)ins.push(`Tratas a ${pct(tr)} de los animales que entran: es mucho. Revisa origen, transporte y el plan de ingreso con tu veterinario.`);
  const top=Object.entries(causas).sort((a,b)=>b[1]-a[1]);
  if(top.length)ins.push(`Causa de muerte más común: <b>${esc(top[0][0])}</b> (${nf(top[0][1])} de ${nf(bajas)}).${/neumon|respir/i.test(top[0][0])?' Casi siempre empieza por estrés de viaje, polvo y mezcla de orígenes.':/acidosis|timpan/i.test(top[0][0])?' Tiene que ver con la ración: adaptación lenta al grano, horarios fijos y comederos limpios.':''}`);
  if(!ins.length)ins.push('Tus números de sanidad se ven bien. Sigue con el plan de ingreso y el calendario de vacunas.');
  return {html:`<b>Sanidad y muertes: qué me dicen</b><br>Gastas <b>${cab0?money2(costo/cab0):'–'}</b> por cabeza en sanidad; tratas a <b>${pct(tr)}</b> de los animales y se muere el <b>${pct(mo,1)}</b>.`+
    tabla(['Lote','Tratados','Mortalidad','Sanidad por cabeza','Estado'],rows)+`<p class="rh">Lo que dice</p>`+ins.join('<br>')+nota('Referencia de engorde en corral: menos de 15 % de tratados y menos de 1.5 % de muertes. Para medicinas, consulta a tu veterinario.')};
}

/* ---------- 5. ¿Cuándo se me acaba el alimento? ---------- */
function bodegaHtml(){
  if(!window.Bodega)return {html:'La bodega no está disponible.'};
  const inv=Bodega.inventario().filter(o=>!o.e.sin);
  if(!inv.length)return {html:'Aún no anotas compras en la bodega. Cuando anotes lo que compras, te digo cuántos días te alcanza cada ingrediente y cuándo comprar.',btns:[['Abrir la bodega',{t:'go',go:'#bodega'}]]};
  const H=calc().H;
  const rows=inv.sort((a,b)=>(a.e.dias??1e9)-(b.e.dias??1e9)).map(o=>{const d=o.e.dias;const c=d==null?'verde':d<7?'rojo':d<14?'tierra':'verde';
    return [`<b>${esc(o.it.n)}</b>`,`${nf(Math.max(0,o.stock))} kg`,o.e.dia>0?`${nf(o.e.dia)} kg`:'–',d==null?'–':`${nf(Math.max(0,d))} días`,d==null?'–':ffc(addDias(H,Math.max(0,Math.floor(d)))),pill(c,d!=null&&d<7?'Comprar ya':d!=null&&d<14?'Pronto':'Bien')];});
  const urg=inv.filter(o=>o.e.dias!=null&&o.e.dias<14);
  return {html:`<b>¿Cuándo se me acaba el alimento?</b><br>Con lo que comen tus lotes esta semana:`+tabla(['Ingrediente','En bodega','Usan al día','Alcanza','Se acaba','Estado'],rows)+
    (urg.length?`<p class="rh">Qué hacer</p>Compra ${urg.map(o=>`<b>${esc(o.it.n)}</b> (unos ${nf(Math.ceil(o.e.dia*21/50)*50)} kg para 3 semanas)`).join(', ')}. Pedir con una semana de margen evita cambios bruscos de ración.`:nota('Tienes alimento para más de dos semanas.'))+
    nota('Cuenta la bodega de vez en cuando: con el conteo real el cálculo se corrige solo.'),btns:[['Abrir la bodega',{t:'go',go:'#bodega'}]]};
}

/* ---------- 6. Lo que te está costando dinero ---------- */
function fugasHtml(){
  const C=calc();if(!C.act.length)return {html:'Todavía no tienes lotes en engorde.'};
  const F=[];const p=pv(),d=des();
  for(const x of C.act){
    const cons=x.consumo||x.pesoHoy*0.026,costoDia=cons*x.cab*(x.costoKgR||0);
    // pasados de su meta: cada día cuesta más de lo que suben
    if(x.listo){const valor=x.gdpUse*x.cab*p*(1-d);const neto=(valor-costoDia)*7;
      F.push({t:`${esc(x.l.nombre)} ya llegó a su meta`,s:neto<0?`Cada semana en el corral te cuesta unos <b>${money(-neto)}</b> más de lo que suben.`:`Todavía sube más de lo que come (${money(neto)} a la semana), pero el riesgo y el costo crecen. Ofrécelo.`,go:'#analisis/vender',prio:neto<0?-neto:neto*0.2});}
    // comedero con sobras: desperdicio estimado 4 %
    const r=comedero(x);
    if(r.sob>=2)F.push({t:`Sobra comida en ${esc(x.l.nombre)}`,s:`Si baja 4 % la ración, ahorras unos <b>${money(costoDia*0.04*7)}</b> a la semana.`,prio:costoDia*0.04*7,go:'#analisis/comedero'});
    if(r.vac>=2)F.push({t:`${esc(x.l.nombre)} se queda con el comedero vacío`,s:`Pierde ganancia: si le falta 5 % de comida, deja de subir unos ${nf(x.gdpUse*0.05*x.cab*7)} kg a la semana (${money(x.gdpUse*0.05*x.cab*7*p)}).`,prio:x.gdpUse*0.05*x.cab*7*p,go:'#analisis/comedero'});
    if(x.bajaCons)F.push({t:`${esc(x.l.nombre)} comió menos ayer`,s:'Una baja de consumo suele avisar de calor, agua o enfermedad dos o tres días antes. Revísalo hoy.',prio:costoDia*0.5,go:'#lote/'+encodeURIComponent(x.id)});
    // costo de ganancia arriba del precio
    if(x.costoKgGan&&p&&x.costoKgGan>p*0.95&&x.kgGan>0){const gSem=x.gdpUse*x.cab*7;F.push({t:`En ${esc(x.l.nombre)} el kilo ganado cuesta casi lo que vale`,s:`Producir un kilo te cuesta ${pk(x.costoKgGan)} y lo vendes a ${pk(p)}. Revisa la ración y la conversión: a la semana son ${nf(gSem)} kg.`,prio:Math.max(0,(x.costoKgGan-p*0.8))*gSem,go:'#analisis/costos'});}
    // sin pesar: decides a ciegas
    const dsp=dias(x.ult.f,C.H);if(dsp>(+S.config.diasSinPesar||21)+7)F.push({t:`${esc(x.l.nombre)} lleva ${dsp} días sin pesar`,s:'Sin pesajes no sé si la ración funciona ni cuándo vender. Pésalo esta semana.',prio:costoDia*0.3,go:'#lote/'+encodeURIComponent(x.id)});
  }
  if(!F.length)return {html:'<b>Lo que te está costando dinero</b><br>No encontré fugas de dinero esta semana. Sigue así: anota cada entrega y pesa cada 3 a 4 semanas.'};
  F.sort((a,b)=>b.prio-a.prio);
  return {html:`<b>Lo que te está costando dinero</b><br>Ordenado por lo que más plata te puede ahorrar esta semana:`+
    `<ol class="rp-fugas">${F.slice(0,7).map(f=>`<li><b>${f.t}</b><br>${f.s}</li>`).join('')}</ol>`+nota('Son estimaciones con tus registros, tu precio de venta y el costo de tu ración.'),
    btns:F.slice(0,2).filter(f=>f.go).map(f=>[f.t.replace(/<[^>]+>/g,'').slice(0,40),{t:'go',go:f.go}])};
}

/* ---------- menú de Rumi ---------- */
const NUEVOS=[
  {l:'Lo que me está costando dinero',ic3:'cartera',fn:fugasHtml},
  {l:'¿Estoy dando de más o de menos?',fn:comederoHtml},
  {l:'¿De dónde sale mi ganancia?',fn:origenHtml},
  {l:'Comparar mis lotes',fn:compararHtml},
  {l:'Sanidad y muertes: qué me dicen',fn:sanidadHtml},
  {l:'¿Cuándo se me acaba el alimento?',fn:bodegaHtml}];
const A=RumiMenu.AREAS.find(a=>a.id==='pro');
if(A){const o=A.ops;A.ops=()=>{const L=o();const i=Math.max(0,L.findIndex(x=>x&&x.l&&x.l.startsWith('Señales de alerta'))+1);L.splice(i,0,...NUEVOS);return L;};
  A.s='Puntaje, fugas de dinero, comedero, lotes, sanidad, ventas y riesgo';}

/* ---------- página de análisis ---------- */
const _pag=PAGES.analisis;
PAGES.analisis=function(id){
  let h=_pag.apply(this,arguments);const C=calc();if(!C.act.length)return h;
  const card=(k,t,r,sub='')=>{const bt=(r.btns||[]).filter(b=>b&&b[1]).slice(0,2).map(([l,a])=>{const i='p'+(++RUMI.n);RUMI.acts[i]=a;return `<button type="button" class="btn sm" data-act="rumiDo" data-id="${i}">${l}</button>`;}).join('');
    return `<section class="gcard rp-card" id="rp_${k}"><h3>${t}</h3>${sub?`<p class="rp-sub">${sub}</p>`:''}<div class="rmsg-like">${r.html.replace(/^<b>[^<]*<\/b>(<br>)?/,'')}</div>${bt?`<div class="acts">${bt}</div>`:''}</section>`;};
  const idx=[['fugas','Fugas de dinero'],['comedero','Comedero'],['origen','Origen de la ganancia'],['comparar','Comparar lotes'],['sanidad2','Sanidad'],['bodega2','Alimento en bodega']];
  h=h.replace('</nav>',idx.map(([k,t])=>`<a href="#analisis/${k}" data-rp="${k}">${t}</a>`).join('')+'</nav>');
  const extra=card('fugas','Lo que te está costando dinero',fugasHtml())+
    card('comedero','¿Estoy dando de más o de menos?',comederoHtml())+card('origen','¿De dónde sale tu ganancia?',origenHtml())+
    card('comparar','Comparar tus lotes',compararHtml())+card('sanidad2','Sanidad y muertes',sanidadHtml())+card('bodega2','¿Cuándo se acaba el alimento?',bodegaHtml());
  // las fugas de dinero van arriba, después del chequeo; lo demás antes de la nota final
  h=h.replace(/(<section class="gcard rp-card" id="rp_senales")/,card('fugas','Lo que te está costando dinero',fugasHtml())+'$1');
  h=h.replace('<p class="hint">Rumi usa solo tus registros',extra.replace(/<section class="gcard rp-card" id="rp_fugas">[\s\S]*?<\/section>/,'')+'<p class="hint">Rumi usa solo tus registros');
  return h;
};
window.RumiAnalisis2={comederoHtml,origenHtml,compararHtml,sanidadHtml,bodegaHtml,fugasHtml,comedero,origen};
})();
