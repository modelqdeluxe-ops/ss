/* Análisis de Rumi (Rumentis 3.9): lo que Rumi decide con tus datos.
   - Chequeo con puntaje (0 a 100) de cada lote y de la finca, con lo que Rumi haría primero.
   - Riesgo del negocio: 2,000 escenarios con precio, ganancia, alimento y muertes que cambian;
     probabilidad de ganar, rango de margen y qué pesa más (como @Risk, dentro del teléfono).
   - Proveedores y razas: quién te vende los animales que más rinden.
   - Semáforo de animales y venta escalonada: quién se atrasa y a quién ya conviene vender.
   - Escenarios "¿qué pasa si…?" y el informe de la semana para compartir.
   Todo corre en el teléfono, sin internet. */
(function(){
'use strict';
if(!window.RumiMenu)return;
ICONOS.pro=ICONOS.chispa;
const I=k=>icono(k,'i3');
const cl=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
const n01=(v,malo,bueno)=>cl((v-malo)/(bueno-malo));
const kvs=rows=>`<div class="rp-kv">${rows.map(([a,b])=>`<div><span>${a}</span><em>${b}</em></div>`).join('')}</div>`;
const uniq=bs=>{const v=new Set();return bs.filter(b=>b&&!v.has(b[0])&&v.add(b[0]));};
const des=()=>Math.max(0,Math.min(15,+S.config.desbaste||0))/100;
const pvKg=()=>+S.config.precioVentaKg||0;

/* ================= chequeo con puntaje ================= */
const DIM=[['gan','Ganancia de peso',25],['conv','Conversión',20],['rent','Rentabilidad',25],['salud','Salud',20],['manejo','Registros y manejo',10]];
function chequeo(x){
  const C=calc(),H=C.H,d={};
  const g=x.gdp!=null?x.gdpUse:null;
  d.gan=g==null?null:n01(g,0.8,1.5);
  d.conv=x.conv?n01(x.conv,10,6):null;
  const roi=x.proy&&x.proy.costoFinal?x.proy.margen/x.proy.costoFinal:null;
  d.rent=roi==null?null:n01(roi,-0.05,0.15);
  const atr=x.atrasados&&x.animAct&&x.animAct.length?x.atrasados.length/x.animAct.length:0;
  d.salud=cl(n01(x.mort,0.03,0)-atr*0.6-(x.bajaCons?0.15:0));
  const entregas=[1,2,3].filter(i=>x.kgDia[addDias(H,-i)]!=null).length/3;
  d.manejo=cl(n01(x.diasSinPesar,45,21)*0.6+entregas*0.4);
  let s=0,w=0;for(const [k,,p] of DIM)if(d[k]!=null){s+=d[k]*p;w+=p;}
  const puntaje=w?Math.round(s/w*100):null;
  // lo que Rumi haría primero: las dimensiones más flojas, con su acción
  const rec=[];
  if(d.gan!=null&&d.gan<0.6)rec.push({p:1-d.gan,t:`Sube poco: ${nf(x.gdpUse,2)} kg por día. Revisa la ración (energía y proteína), agua limpia a libre acceso, sombra y parásitos.`,b:['Formular su dieta',{t:'go',go:'#formular'}]});
  if(d.conv!=null&&d.conv<0.6)rec.push({p:1-d.conv,t:`Convierte ${nf(x.conv,1)} a 1: gasta mucho alimento por kilo. Revisa desperdicio en comederos, tamaño de partícula y animales enfermos.`,b:['Ver su consumo',{t:'go',go:'#graficos'}]});
  if(d.rent!=null&&d.rent<0.5)rec.push({p:1-d.rent,t:roi<0?`Con el precio de hoy perdería ${money(-x.proy.margen)}. Mira el precio mínimo y negocia antes de vender.`:`Deja poco: ${nf(roi*100,1)} % sobre lo invertido. Baja el costo del kilo ganado o busca mejor precio.`,b:['Precio mínimo para no perder',{t:'rumi',q:'precio minimo para no perder'}]});
  if(atr>0)rec.push({p:0.4+atr,t:`${pl(x.atrasados.length,'animal gana','animales ganan')} menos del 70 % del promedio. Revísalos: pueden estar enfermos o no alcanzar comedero.`,b:['Ver el semáforo de animales',{t:'fn',fn:'semaforo',lote:x.id}]});
  if(x.mort>0.015)rec.push({p:0.5+x.mort*10,t:`Mortalidad de ${nf(x.mort*100,1)} %: arriba de lo aceptable. Repasa recepción, vacunas y el calendario de sanidad.`,b:['Calendario de sanidad',{t:'go',go:'#lote/'+encodeURIComponent(x.id)}]});
  if(x.diasSinPesar>(+S.config.diasSinPesar||21))rec.push({p:0.45,t:`No se pesa desde hace ${x.diasSinPesar} días: sin pesajes, los números son estimados.`,b:['Anotar pesaje',{t:'form',k:'pesaje',p:{lote:x.id}}]});
  if(entregas<1)rec.push({p:0.3,t:'Faltan entregas de alimento anotadas en los últimos 3 días: el costo y la conversión pierden exactitud.',b:['Anotar alimento',{t:'form',k:'alimento'}]});
  rec.sort((a,b)=>b.p-a.p);
  return {x,d,puntaje,roi,rec};
}
const nivel=p=>p==null?['Sin datos','anil']:p>=80?['Excelente','verde']:p>=65?['Bien','verde']:p>=45?['Regular','tierra']:['Atención','rojo'];
function anillo(p,tam=86){
  const r=tam/2-7,c=2*Math.PI*r,v=p==null?0:p/100,[,col]=nivel(p);
  return `<svg class="rp-ring" viewBox="0 0 ${tam} ${tam}" width="${tam}" height="${tam}" aria-label="Puntaje ${p==null?'sin datos':p+' de 100'}"><circle cx="${tam/2}" cy="${tam/2}" r="${r}" fill="none" style="stroke:var(--card2)" stroke-width="8"/><circle cx="${tam/2}" cy="${tam/2}" r="${r}" fill="none" style="stroke:var(--${col})" stroke-width="8" stroke-linecap="round" stroke-dasharray="${(c*v).toFixed(1)} ${c.toFixed(1)}" transform="rotate(-90 ${tam/2} ${tam/2})"/><text x="50%" y="50%" text-anchor="middle" dominant-baseline="central" font-size="${tam*0.3}" font-weight="800" style="fill:var(--ink);font-family:'Bricolage Grotesque',sans-serif">${p==null?'–':p}</text></svg>`;
}
const barras=d=>`<div class="rp-dims">${DIM.map(([k,t])=>{const v=d[k];const [,col]=nivel(v==null?null:v*100);return `<div class="rp-dim"><span>${t}</span><div class="rp-b"><i style="width:${v==null?0:Math.max(3,v*100).toFixed(0)}%;background:var(--${col})"></i></div><b>${v==null?'–':Math.round(v*100)}</b></div>`;}).join('')}</div>`;
function chequeoLoteHtml(x){
  const q=chequeo(x),[nv]=nivel(q.puntaje);
  return {html:`<div class="rp-head">${anillo(q.puntaje)}<div><b>${esc(x.l.nombre)}: ${nv}</b><span>Puntaje de Rumi sobre 100, con ganancia, conversión, rentabilidad, salud y registros.</span></div></div>${barras(q.d)}
    ${q.rec.length?`<p class="rh">Lo que haría primero</p><ol class="rp-rec">${q.rec.slice(0,3).map(r=>`<li>${r.t}</li>`).join('')}</ol>`:'<p class="rh">Va muy bien. Sigue pesando cada 3 semanas y anotando el alimento.</p>'}`,
    btns:uniq(q.rec.slice(0,3).map(r=>r.b))};
}
function chequeoFinca(){
  const C=calc();if(!C.act.length)return {html:'No tienes lotes en engorde para revisar.'};
  const Q=C.act.map(chequeo),cab=C.act.reduce((s,x)=>s+x.cab,0)||1;
  const val=Q.filter(q=>q.puntaje!=null),p=val.length?Math.round(val.reduce((s,q)=>s+q.puntaje*q.x.cab,0)/Math.max(1,val.reduce((s,q)=>s+q.x.cab,0))):null;
  const d={};for(const [k] of DIM){const v=Q.filter(q=>q.d[k]!=null);d[k]=v.length?v.reduce((s,q)=>s+q.d[k]*q.x.cab,0)/v.reduce((s,q)=>s+q.x.cab,0):null;}
  const rec=Q.flatMap(q=>q.rec.map(r=>({...r,t:`<b>${esc(q.x.l.nombre)}</b>: ${r.t}`}))).sort((a,b)=>b.p-a.p).slice(0,3);
  const [nv]=nivel(p);
  return {html:`<div class="rp-head">${anillo(p)}<div><b>Tu engorde: ${nv}</b><span>${pl(cab,'cabeza','cabezas')} en ${pl(C.act.length,'lote','lotes')}. Puntaje ponderado por cabezas.</span></div></div>${barras(d)}
    <p class="rh">Tus lotes</p>${tabla(['Lote','Puntaje',''],Q.slice().sort((a,b)=>(b.puntaje??-1)-(a.puntaje??-1)).map(q=>{const [t,c]=nivel(q.puntaje);return [esc(q.x.l.nombre),`<b>${q.puntaje??'–'}</b>`,`<span class="pill p-${c}">${t}</span>`];}))}
    ${rec.length?`<p class="rh">Lo que haría primero</p><ol class="rp-rec">${rec.map(r=>`<li>${r.t}</li>`).join('')}</ol>`:''}`,
    btns:uniq(rec.map(r=>r.b)).slice(0,3)};
}

/* ================= riesgo: simulación de Montecarlo ================= */
// generador con semilla: el mismo lote da el mismo resultado cada vez que lo abres
function rng(seed){let s=0;for(const ch of String(seed))s=(s*31+ch.charCodeAt(0))>>>0;s=s||1;return ()=>{s^=s<<13;s>>>=0;s^=s>>17;s^=s<<5;s>>>=0;return s/4294967296;};}
const normal=r=>{let u=0,v=0;while(!u)u=r();while(!v)v=r();return Math.sqrt(-2*Math.log(u))*Math.cos(2*Math.PI*v);};
const VOL={precio:0.08,gdp:0.12,alim:0.10,muerte:0.012};
/* margen de un lote con precio, ganancia, costo del alimento y muertes dados (factores sobre lo de hoy) */
function margenCon(x,{fp=1,fg=1,fa=1,muertes=0}={}){
  const C=calc(),pv=pvKg()*fp,gdp=Math.max(0.2,x.gdpUse*fg),pf=Math.max(x.meta,x.pesoHoy);
  const d=x.pesoHoy>=x.meta?0:Math.ceil((x.meta-x.pesoHoy)/gdp);
  const alim=(x.proy?x.proy.alimRest:0)*(x.diasMeta?d/x.diasMeta:0)*fa;
  const gen=(C.genDia||0)*x.cab*d;
  const vivos=Math.max(0,x.cab-muertes);
  const venta=pf*(1-des())*vivos*pv;
  return {m:venta+x.ingresos-(x.costoTot+alim+gen),d,venta,costo:x.costoTot+alim+gen,vivos,pf};
}
function simular(x,N=2000,vol=VOL){
  const r=rng(x.id+':'+x.cab+':'+Math.round(x.pesoHoy)),M=[],Preq=[];
  const pm=vol.muerte*Math.max(0.3,(x.diasMeta||30)/100);
  for(let i=0;i<N;i++){
    const fp=Math.exp(normal(r)*vol.precio-vol.precio*vol.precio/2),fg=cl(1+normal(r)*vol.gdp,0.4,1.8),fa=Math.max(0.5,1+normal(r)*vol.alim);
    let muertes=0;for(let k=0;k<x.cab;k++)if(r()<pm)muertes++;
    const o=margenCon(x,{fp,fg,fa,muertes});M.push(o.m);
    // precio que haría falta en este escenario para no perder
    const kgV=o.pf*(1-des())*o.vivos;Preq.push(kgV>0?(o.costo-x.ingresos)/kgV:Infinity);
  }
  M.sort((a,b)=>a-b);Preq.sort((a,b)=>a-b);const q=p=>M[Math.min(N-1,Math.floor(p*N))];
  const media=M.reduce((s,v)=>s+v,0)/N,prob=M.filter(v=>v>0).length/N;
  // sensibilidad: cada variable en su 10 % bajo y alto, las demás como hoy
  const z=1.2816,base=margenCon(x).m;
  const sens=[['Precio de venta',a=>margenCon(x,{fp:Math.exp(a*z*vol.precio)})],['Ganancia diaria',a=>margenCon(x,{fg:1+a*z*vol.gdp})],['Costo del alimento',a=>margenCon(x,{fa:1-a*z*vol.alim})],['Muertes',a=>margenCon(x,{muertes:a<0?Math.round(x.cab*pm*2.5):0})]]
    .map(([t,f])=>{const lo=f(-1).m,hi=f(1).m;return {t,lo:Math.min(lo,hi),hi:Math.max(lo,hi)};}).sort((a,b)=>(b.hi-b.lo)-(a.hi-a.lo));
  return {M,N,media,prob,p10:q(0.1),p50:q(0.5),p90:q(0.9),base,sens,precio90:Preq[Math.min(N-1,Math.floor(0.9*N))]};
}
function histo(S0){
  const W=340,h=150,pl=8,pr=8,pt=18,pb=26,M=S0.M,lo=M[Math.floor(M.length*0.005)],hi=M[Math.floor(M.length*0.995)],nb=26;
  if(!(hi>lo))return '';
  const bw=(hi-lo)/nb,cnt=Array(nb).fill(0);for(const v of M){if(v<lo||v>hi)continue;cnt[Math.min(nb-1,Math.floor((v-lo)/bw))]++;}
  const mx=Math.max(...cnt),X=v=>pl+(W-pl-pr)*(v-lo)/(hi-lo),bwp=(W-pl-pr)/nb;
  let g=cnt.map((c,i)=>{const v=lo+(i+0.5)*bw,hh=(h-pt-pb)*c/mx;return `<rect x="${(pl+i*bwp+0.8).toFixed(1)}" y="${(h-pb-hh).toFixed(1)}" width="${(bwp-1.6).toFixed(1)}" height="${hh.toFixed(1)}" rx="2" style="fill:var(--${v<0?'rojo':'verde'});opacity:${v<0?.75:.85}"/>`;}).join('');
  const mk=(v,t,an)=>v>=lo&&v<=hi?`<line x1="${X(v).toFixed(1)}" x2="${X(v).toFixed(1)}" y1="${pt-4}" y2="${h-pb}" style="stroke:var(--ink)" stroke-width="1.5" stroke-dasharray="3 3"/><text x="${X(v).toFixed(1)}" y="${pt-7}" text-anchor="${an}" font-size="11" font-weight="700" style="fill:var(--ink)">${t}</text>`:'';
  g+=mk(S0.p10,'10 %','middle')+mk(S0.p90,'90 %','middle');
  if(0>lo&&0<hi)g+=`<line x1="${X(0).toFixed(1)}" x2="${X(0).toFixed(1)}" y1="${pt}" y2="${h-pb}" style="stroke:var(--rojo)" stroke-width="2"/>`;
  g+=`<text x="${pl}" y="${h-8}" font-size="11.5" style="fill:var(--ink2)">${esc(money(lo))}</text><text x="${W-pr}" y="${h-8}" text-anchor="end" font-size="11.5" style="fill:var(--ink2)">${esc(money(hi))}</text>`;
  return `<svg class="rp-svg" viewBox="0 0 ${W} ${h}" role="img" aria-label="Distribución del margen en 2,000 escenarios">${g}</svg>`;
}
function tornado(S0){
  const all=S0.sens.flatMap(s=>[s.lo,s.hi]).concat(S0.base),lo=Math.min(...all),hi=Math.max(...all),span=hi-lo||1;
  return `<div class="rp-tor">${S0.sens.map(s=>`<div><span>${s.t}</span><div class="rp-tb"><i style="left:${((s.lo-lo)/span*100).toFixed(1)}%;width:${Math.max(1.5,(s.hi-s.lo)/span*100).toFixed(1)}%"></i><u style="left:${((S0.base-lo)/span*100).toFixed(1)}%"></u></div><small>${esc(money(s.lo))} a ${esc(money(s.hi))}</small></div>`).join('')}</div>`;
}
function riesgoLote(x){
  if(!x.activo||!x.proy)return {html:`${esc(x.l.nombre)} ya se vendió: su resultado fue ${money(x.margenReal)}.`};
  const S0=simular(x),pr=S0.prob,top=S0.sens[0];
  const semaf=pr>=0.9?['verde','Riesgo bajo']:pr>=0.7?['tierra','Riesgo moderado']:['rojo','Riesgo alto'];
  return {html:`<div class="rp-head"><div class="rp-big" style="color:var(--${semaf[0]})">${nf(pr*100,0)} %</div><div><b>${esc(x.l.nombre)}: probabilidad de ganar</b><span><span class="pill p-${semaf[0]}">${semaf[1]}</span> en ${nf(S0.N)} escenarios</span></div></div>
    ${histo(S0)}
    ${kvs([['Margen esperado',`<b>${money(S0.media)}</b>`],['Rango probable (80 %)',`${money(S0.p10)} a ${money(S0.p90)}`],['Por cabeza, lo más probable',money(S0.p50/Math.max(1,x.proy.cabBase))],['Precio para ganar 9 de cada 10 veces',`<b>${pk(S0.precio90)} el kilo</b>`]])}
    <p class="rh">Lo que más mueve tu resultado</p>${tornado(S0)}
    <p class="rs">Cada escenario cambia el precio de venta (±${nf(VOL.precio*100)} %), la ganancia diaria (±${nf(VOL.gdp*100)} %), el costo del alimento (±${nf(VOL.alim*100)} %) y las muertes, como en un análisis de @Risk. ${top?`Lo que más pesa es <b>${top.t.toLowerCase()}</b>: ${top.t==='Precio de venta'?'asegura precio con tu comprador antes de la salida.':top.t==='Ganancia diaria'?'cuida la ración y la salud: cada día extra cuesta.':top.t==='Costo del alimento'?'compra el maíz y la soya con tiempo o forma tu dieta con lo más barato.':'refuerza la sanidad de recepción.'}`:''}</p>`,
    btns:[['Escenarios: ¿qué pasa si…?',{t:'fn',fn:'escenarios'}],['Precio mínimo para no perder',{t:'rumi',q:'precio minimo para no perder'}]]};
}
/* suma de escenarios de todos los lotes: el precio se mueve igual para todos, así que se simula junto */
let _sf={v:-1,r:null};
function simFinca(){
  if(_sf.v===ver)return _sf.r;
  const A=calc().act.filter(x=>x.proy),N=1500,r=rng('finca'+A.length),T=[];
  for(let i=0;i<N;i++){const fp=Math.exp(normal(r)*VOL.precio-VOL.precio*VOL.precio/2);let t=0;
    for(const x of A){const fg=cl(1+normal(r)*VOL.gdp,0.4,1.8),fa=Math.max(0.5,1+normal(r)*VOL.alim);let mu=0;const pm=VOL.muerte*Math.max(0.3,(x.diasMeta||30)/100);for(let k=0;k<x.cab;k++)if(r()<pm)mu++;t+=margenCon(x,{fp,fg,fa,muertes:mu}).m;}
    T.push(t);}
  T.sort((a,b)=>a-b);_sf={v:ver,r:{T,N,pr:T.filter(v=>v>0).length/N}};return _sf.r;
}
function riesgoFinca(){
  const C=calc(),A=C.act.filter(x=>x.proy);if(!A.length)return {html:'No tienes lotes en engorde para simular.'};
  const R=A.map(x=>({x,s:simular(x,1500)}));
  const {T,N,pr}=simFinca(),q=p=>T[Math.floor(p*N)];
  const Sx={M:T,p10:q(0.1),p90:q(0.9)};
  return {html:`<div class="rp-head"><div class="rp-big" style="color:var(--${pr>=0.9?'verde':pr>=0.7?'tierra':'rojo'})">${nf(pr*100,0)} %</div><div><b>Probabilidad de que el engorde completo gane</b><span>${pl(A.length,'lote','lotes')} en engorde, ${nf(N)} escenarios</span></div></div>${histo(Sx)}
    ${tabla(['Lote','Gana','Rango probable'],R.map(o=>[esc(o.x.l.nombre),`<b>${nf(o.s.prob*100,0)} %</b>`,`${fmtK(o.s.p10)} a ${fmtK(o.s.p90)}`]))}
    <p class="rs">Todos los lotes comparten el precio del mercado, por eso el riesgo del total no es la suma de riesgos separados. Toca un lote para ver qué lo mueve.</p>`,
    btns:A.slice(0,4).map(x=>[`Riesgo de ${x.l.nombre}`,{t:'fn',fn:'riesgo',lote:x.id}])};
}
const fmtK=v=>{const a=Math.abs(v),s=S.config.moneda||'L';return (v<0?'−':'')+s+' '+(a>=1e6?nf(a/1e6,2)+' M':a>=1e3?nf(a/1e3,0)+' mil':nf(a));};

/* ================= escenarios ================= */
function escenarios(v){
  const C=calc(),A=C.act.filter(x=>x.proy);if(!A.length)return {html:'No tienes lotes en engorde.'};
  const o={fp:1+(v.p||0)/100,fa:1+(v.a||0)/100,fg:1+(v.g||0)/100};
  const rows=A.map(x=>{const b=margenCon(x).m,e=margenCon(x,o);return {x,b,e:e.m,d:e.d};});
  const tb=rows.reduce((s,r)=>s+r.b,0),te=rows.reduce((s,r)=>s+r.e,0);
  const txt=[v.p?`precio ${v.p>0?'+':''}${nf(v.p)} %`:'',v.a?`alimento ${v.a>0?'+':''}${nf(v.a)} %`:'',v.g?`ganancia ${v.g>0?'+':''}${nf(v.g)} %`:''].filter(Boolean).join(', ')||'sin cambios';
  return {html:`<b>Escenario: ${txt}</b>${tabla(['Lote','Hoy','Escenario'],rows.map(r=>[esc(r.x.l.nombre),fmtK(r.b),`<b style="color:var(--${r.e>=0?'verde':'rojo'})">${fmtK(r.e)}</b>`]).concat([['<b>Total</b>',`<b>${fmtK(tb)}</b>`,`<b style="color:var(--${te>=0?'verde':'rojo'})">${fmtK(te)}</b>`]]))}
    <p class="rs">${te>=tb?`Ganarías ${money(te-tb)} más.`:`Ganarías ${money(tb-te)} menos.`} ${v.g?'Con otra ganancia diaria también cambian los días a la meta y el alimento que falta.':''}</p>`};
}

/* ================= proveedores y razas ================= */
function grupos(clave){
  const C=calc(),G={};
  for(const x of Object.values(C.L)){const k=String((clave==='prov'?x.l.proveedor:x.l.raza)||'').trim()||'Sin anotar';const g=G[k]||(G[k]={k,L:[],cab:0});g.L.push(x);g.cab+=x.cab0;}
  return Object.values(G).map(g=>{
    const w=g.L.reduce((s,x)=>s+x.cab0,0)||1;
    const conG=g.L.filter(x=>(x.activo?x.gdp:x.gdpTot)!=null),gdp=conG.length?conG.reduce((s,x)=>s+(x.activo?x.gdpUse:x.gdpTot)*x.cab0,0)/conG.reduce((s,x)=>s+x.cab0,0):null;
    const mort=g.L.reduce((s,x)=>s+x.bajas,0)/w;
    const conC=g.L.filter(x=>x.conv),conv=conC.length?conC.reduce((s,x)=>s+x.conv*x.cab0,0)/conC.reduce((s,x)=>s+x.cab0,0):null;
    const mc=g.L.map(x=>x.activo?(x.proy?{m:x.proy.margen,c:x.proy.cabBase}:null):{m:x.margenReal,c:x.cab0-x.bajas}).filter(Boolean);
    const porCab=mc.length?mc.reduce((s,o)=>s+o.m,0)/Math.max(1,mc.reduce((s,o)=>s+o.c,0)):null;
    const real=g.L.every(x=>!x.activo);
    return {...g,gdp,mort,conv,porCab,real};
  }).sort((a,b)=>(b.porCab??-1e12)-(a.porCab??-1e12));
}
function rankingHtml(clave){
  const G=grupos(clave),nom=clave==='prov'?'proveedor':'raza';
  if(G.length<2)return {html:`Necesito lotes de al menos dos ${clave==='prov'?'proveedores':'razas'} distintos para compararlos. Anota el ${nom} al crear cada lote.`};
  const b=G[0],w=G[G.length-1];
  const dif=b.porCab!=null&&w.porCab!=null?b.porCab-w.porCab:null;
  return {html:`<b>${clave==='prov'?'¿Dónde compro los animales que más rinden?':'¿Qué raza me rinde más?'}</b>
    ${tabla([clave==='prov'?'Proveedor':'Raza','Cab.','Por día','Muertes','Por cabeza'],G.map(g=>[esc(g.k),nf(g.cab),g.gdp!=null?nf(g.gdp,2)+' kg':'–',nf(g.mort*100,1)+' %',`<b style="color:var(--${(g.porCab||0)>=0?'verde':'rojo'})">${g.porCab!=null?fmtK(g.porCab):'–'}</b>`]))}
    <p class="rs">${dif!=null&&dif>0?`Cada animal de <b>${esc(b.k)}</b> deja ${money(dif)} más que uno de <b>${esc(w.k)}</b>${b.gdp&&w.gdp?`: gana ${nf(Math.abs(b.gdp-w.gdp),2)} kg por día ${b.gdp>=w.gdp?'más':'menos'}`:''}${b.mort<w.mort?` y se mueren menos (${nf(b.mort*100,1)} % contra ${nf(w.mort*100,1)} %)`:''}.`:''} En lotes en engorde el margen es estimado; en los vendidos, real. ${G.some(g=>g.L.length<2)?'Con un solo lote la diferencia puede ser suerte: compara al menos 2 o 3 lotes de cada uno.':''}</p>`};
}

/* ================= semáforo de animales y venta escalonada ================= */
function semaforo(x){
  if(!x.animAct||!x.animAct.length)return {html:`${esc(x.l.nombre)} no tiene aretes anotados. Agrega sus animales para ver a cada uno.`};
  const A=x.animAct,con=A.filter(a=>a.gdp!=null),med=con.length?con.map(a=>a.gdp).sort((a,b)=>a-b)[Math.floor(con.length/2)]:null;
  const cls=a=>a.gdp==null||med==null?'gris':a.gdp>=med*0.9?'verde':a.gdp>=med*0.7?'amarillo':'rojo';
  const cnt={verde:0,amarillo:0,rojo:0,gris:0};for(const a of A)cnt[cls(a)]++;
  const C=calc(),cd=(x.consumo||x.pesoHoy*0.026)*(x.costoKgR||0)+(C.genDia||0),pv=pvKg();
  // listos: ya pesan la meta; conviene venderlos si cada día más cuesta más de lo que suben
  const listos=A.filter(a=>a.pesoHoy>=x.meta).map(a=>{const vd=(a.gdp||x.gdpUse)*(1-des())*pv;return {a,vd,conv:cd>vd};}).sort((u,v)=>v.a.pesoHoy-u.a.pesoHoy);
  const vend=listos.filter(o=>o.conv),ah=x.fechaMeta&&vend.length?vend.reduce((s,o)=>s+(cd-o.vd)*Math.max(0,x.diasMeta),0):0;
  const rojos=A.filter(a=>cls(a)==='rojo').sort((u,v)=>u.gdp-v.gdp);
  const tot=A.length,seg=['verde','amarillo','rojo','gris'].map(k=>cnt[k]?`<i class="s-${k}" style="width:${(cnt[k]/tot*100).toFixed(1)}%"></i>`:'').join('');
  return {html:`<b>Semáforo de ${esc(x.l.nombre)}</b><div class="rp-sem">${seg}</div>
    <div class="rp-semL"><span><i class="s-verde"></i>${cnt.verde} bien</span><span><i class="s-amarillo"></i>${cnt.amarillo} lentos</span><span><i class="s-rojo"></i>${cnt.rojo} atrasados</span>${cnt.gris?`<span><i class="s-gris"></i>${cnt.gris} sin pesar</span>`:''}</div>
    <p class="rs">Comparo cada animal con la mitad del lote (${med!=null?nf(med,2)+' kg por día':'sin pesajes'}): bien es 90 % o más; atrasado, menos de 70 %.</p>
    ${rojos.length?`<p class="rh">Revisa primero</p>${tabla(['Arete','Por día','Peso'],rojos.slice(0,8).map(a=>[`<b>${esc(a.arete)}</b>`,nf(a.gdp,2)+' kg',nf(a.pesoHoy)+' kg']))}<p class="rs">Revisa temperatura, patas, dientes y si alcanza comedero. Si está sano y sigue lento, véndelo antes: come lo mismo y gana menos.</p>`:''}
    ${listos.length?`<p class="rh">Ya llegaron a la meta: ${pl(listos.length,'animal','animales')}</p>${tabla(['Arete','Peso','Cada día más'],listos.slice(0,8).map(o=>[`<b>${esc(o.a.arete)}</b>`,nf(o.a.pesoHoy)+' kg',o.conv?`<span style="color:var(--rojo)">pierde ${money2(cd-o.vd)}</span>`:`<span style="color:var(--verde)">gana ${money2(o.vd-cd)}</span>`]))}
      <p class="rs">${vend.length?`Vender ya ${pl(vend.length,'animal','animales')} (venta escalonada) te ahorra cerca de <b>${money(ah)}</b> mientras el resto llega a la meta: cada día comen ${money2(cd)} y suben menos que eso.`:'Todavía les conviene seguir: cada día suben más de lo que cuestan.'}</p>`:''}`,
    btns:[['Ver los animales',{t:'go',go:'#lote/'+encodeURIComponent(x.id)}]].concat(vend.length?[['Anotar la venta',{t:'form',k:'venta',p:{lote:x.id}}]]:[])};
}

/* ================= informe de la semana ================= */
function semana(){
  const C=calc(),H=C.H,d0=addDias(H,-7),d1=addDias(H,-14),en=(f,a,b)=>f>a&&f<=b;
  const kgPer=(a,b)=>C.act.reduce((s,x)=>s+(x.gdpUse||0)*x.cab*dias(a,b),0);
  const costo=(a,b)=>C.items.filter(i=>en(i.f,a,b)).reduce((s,i)=>s+(i.tipo==='alimento'?(+i.kg||0)*(+i.costoKg||0):i.tipo==='sanidad'?+i.costo||0:i.tipo==='gasto'?+i.monto||0:0),0);
  const kg=kgPer(d0,H),c=costo(d0,H),c0=costo(d1,d0),val=kg*(1-des())*pvKg();
  const muertes=C.items.filter(i=>i.tipo==='baja'&&en(i.f,d0,H)).reduce((s,i)=>s+(+i.cab||0),0);
  const ventas=C.items.filter(i=>i.tipo==='venta'&&en(i.f,d0,H));
  const top=chequeoFinca();
  const q=C.act.map(chequeo).flatMap(o=>o.rec.slice(0,1).map(r=>`${o.x.l.nombre}: ${r.t.replace(/<[^>]+>/g,'')}`)).slice(0,3);
  const txt=`*${S.config.finca||'Mi engorde'}: semana al ${ffl(H)}*\n`+
    `• ${pl(C.cabT,'cabeza','cabezas')} en ${pl(C.act.length,'lote','lotes')}\n`+
    `• Peso producido: ${nf(W(kg))} ${UW()} (${nf(W(C.gdpP||0),2)} ${UW()} por día por cabeza)\n`+
    `• Costo de la semana: ${money(c)}${c0?` (${c>=c0?'+':''}${nf((c/c0-1)*100,0)} % contra la anterior)`:''}\n`+
    `• Valor de lo que ganaron: ${money(val)}\n`+
    (muertes?`• Muertes: ${muertes}\n`:'')+(ventas.length?`• Ventas: ${ventas.map(v=>`${v.cab} cab. por ${money((+v.kg||0)*(+v.precioKg||0))}`).join(', ')}\n`:'')+
    (q.length?`\nPrioridades de Rumi:\n${q.map((t,i)=>`${i+1}. ${t}`).join('\n')}`:'');
  window._rpInforme=txt;
  return {html:`<b>Tu semana</b>${kvs([['Peso producido',`<b>${nf(kg)} kg</b>`],['Costo de la semana',`${money(c)}${c0?` <small>(${c>=c0?'+':''}${nf((c/c0-1)*100,0)} %)</small>`:''}`],['Valor de lo que ganaron',`<b style="color:var(--${val>=c?'verde':'rojo'})">${money(val)}</b>`],['Resultado de la semana',`<b style="color:var(--${val-c>=0?'verde':'rojo'})">${money(val-c)}</b>`],['Muertes',nf(muertes)]])}
    ${q.length?`<p class="rh">Prioridades</p><ol class="rp-rec">${q.map(t=>`<li>${esc(t)}</li>`).join('')}</ol>`:''}
    <p class="rs">El valor es lo que subieron de peso por tu precio de venta, menos desbaste. Si supera el costo, la semana dejó dinero.</p>`,
    btns:[['Compartir por WhatsApp',{t:'fn',fn:'compartirSemana'}]]};
}

/* ================= acciones que Rumi puede ejecutar ================= */
const FN={
  semaforo:a=>semaforo(calc().L[a.lote]),riesgo:a=>riesgoLote(calc().L[a.lote]),escenarios:()=>null,
  compartirSemana:()=>{if(window._rpInforme&&window.Extras)Extras.compartir(window._rpInforme,'Informe de la semana');return null;}
};
const _ej=ejecutar;
/* los botones de las respuestas funcionan igual desde Rumi o desde la página de Análisis:
   si Rumi está cerrado, se abre y ahí responde */
const rumiAbierto=()=>!!$('#rumiMsgs');
ejecutar=function(a){
  if(a&&a.t==='rumi'){const op={l:a.q.replace(/^./,c=>c.toUpperCase()),q:a.q};if(rumiAbierto())RumiMenu.elegir(op);else abrirRumi(op);return;}
  if(a&&a.t==='ask'&&!rumiAbierto()){abrirRumi();setTimeout(()=>preguntar(a.q),60);return;}
  if(a&&a.t==='fn'){
    if(a.fn==='escenarios'){const op=OPS().find(o=>o.l.startsWith('Escenarios'));if(rumiAbierto())RumiMenu.elegir(op);else abrirRumi(op);return;}
    if(a.fn==='compartirSemana'){FN.compartirSemana();return;}
    if(a.fn==='simulador'){closeSheet();location.hash='#analisis/sim';return;}
    if(!rumiAbierto())abrirRumi();const f=FN[a.fn]||(window.RumiMas&&RumiMas.FN[a.fn]);const r=f&&f(a);if(r){botSay(r);}return;}
  return _ej(a);
};

/* ================= el área de Rumi ================= */
const porLote=(t,fn,todos)=>({l:t,sub:()=>{const C=calc(),L=todos?Object.values(C.L):C.act;
  if(!L.length)return {t,intro:'Todavía no tienes lotes en engorde.',ops:[{l:'Crear un lote',form:'lote',p:{}}]};
  return {t,intro:'¿De qué lote?',ops:L.map(x=>({l:x.l.nombre,small:`${pl(x.cab,'cabeza','cabezas')}`,fn:()=>fn(x)}))};}});
const OPS=()=>[
  {l:'Chequeo de mi engorde (puntaje)',ic3:'meta',fn:chequeoFinca},
  porLote('Chequeo de un lote',chequeoLoteHtml),
  {l:'Riesgo del negocio (probabilidad de ganar)',ic3:'dado',fn:riesgoFinca},
  porLote('Riesgo de un lote',riesgoLote),
  {l:'Escenarios: ¿qué pasa si…?',ic3:'dado',fn:()=>window.RumiMas?RumiMas.escenariosTipicos():escenarios({p:-10})},
  {l:'Probar mis propios números',calc:{campos:[{k:'p',l:'Precio de venta cambia',u:'%',min:-60,max:100,def:0},{k:'a',l:'Costo del alimento cambia',u:'%',min:-60,max:200,def:0},{k:'g',l:'Ganancia diaria cambia',u:'%',min:-60,max:60,def:0}],fn:escenarios,intro:'Pon cuánto cambia cada cosa (por ejemplo −10 si el precio baja 10 %). Te digo cómo queda el margen de cada lote.'}},
  {l:'¿Dónde compro los que más rinden?',ic3:'trofeo',fn:()=>rankingHtml('prov')},
  {l:'¿Qué raza me rinde más?',fn:()=>rankingHtml('raza')},
  porLote('Semáforo de animales y venta escalonada',semaforo),
  {l:'Informe de la semana',ic3:'recibo',fn:semana},
  {l:'Ver mis finanzas',go:'#finanzas'},{l:'Ver mi inventario',go:'#inventario'},{l:'Abrir el análisis completo',go:'#analisis'}
];
RumiMenu.AREAS.splice(1,0,{id:'pro',ic:'pro',t:'Análisis de Rumi',s:'Puntaje, riesgo y decisiones con tus datos',intro:'Aquí pienso con tus números: te doy un puntaje, calculo el riesgo, comparo proveedores y te digo qué haría primero. ¿Qué analizamos?',ops:OPS});

/* ================= página completa ================= */
PAGES.analisis=()=>{
  const C=calc();
  const card=(t,r,extra='')=>{const bt=(r.btns||[]).filter(b=>b&&b[1]).slice(0,3).map(([l,a])=>{const id='p'+(++RUMI.n);RUMI.acts[id]=a;return `<button type="button" class="btn sm" data-act="rumiDo" data-id="${id}">${l}</button>`;}).join('');
    return `<section class="gcard rp-card"><h3>${t}</h3><div class="rmsg-like">${r.html}</div>${bt?`<div class="acts">${bt}</div>`:''}${extra}</section>`;};
  if(!C.act.length&&!C.cer.length)return `<header class="hd">${hdBack('#mas','Más')}<div class="ttl" style="margin-top:-6px"><h1>Análisis de Rumi</h1></div></header><main class="bd"><div class="card"><p class="empty">Cuando tengas lotes, aquí Rumi te da su puntaje, el riesgo y qué hacer primero.</p></div></main>`;
  const ch=chequeoFinca(),rs=riesgoFinca(),pv=rankingHtml('prov'),sm=semana();
  return `<header class="hd">${hdBack('#mas','Más','<span class="sync"></span>')}<div class="ttl" style="margin-top:-6px"><span class="eyebrow">${I('chispa')} Rumi piensa con tus datos</span><h1>Análisis de Rumi</h1><p class="sub">Puntaje, riesgo, proveedores y lo que haría primero.</p></div></header>
   <main class="bd" style="gap:18px">${card('Chequeo de tu engorde',ch)}${card('Riesgo del negocio',rs)}${card('Tu semana',sm)}${card('Proveedores',pv)}
   <p class="hint">Rumi usa solo tus registros y la experiencia del engorde en corral. Para medicinas y casos graves, consulta a tu veterinario.</p></main>`;
};
/* tarjeta en Hoy: el puntaje y la probabilidad de ganar, de un vistazo */
let _hc={v:-1,h:''};
function tarjetaHoy(){
  if(_hc.v===ver)return _hc.h;
  const C=calc();let h='';
  if(C.act.length){const ch=C.act.map(chequeo).filter(q=>q.puntaje!=null);
    const p=ch.length?Math.round(ch.reduce((s,q)=>s+q.puntaje*q.x.cab,0)/Math.max(1,ch.reduce((s,q)=>s+q.x.cab,0))):null;
    // la simulación tarda: si no está lista, se muestra la última y se calcula después sin trabar la pantalla
    let pr=null;if(C.act.some(x=>x.proy)){if(_sf.v===ver)pr=_sf.r.pr;else{pr=_sf.r?_sf.r.pr:null;setTimeout(()=>{simFinca();const e=document.querySelector('.rp-hoy b[data-pr]');if(e&&_sf.r)e.textContent=`${e.dataset.nv} · ${nf(_sf.r.pr*100,0)} % de probabilidad de ganar`;},120);}}
    const [nv]=nivel(p);
    h=`<a class="card rp-hoy" href="#analisis">${anillo(p,58)}<div class="tx"><span class="eyebrow">${I('chispa')} Análisis de Rumi</span><b data-pr data-nv="${nv}">${nv}${pr!=null?` · ${nf(pr*100,0)} % de probabilidad de ganar`:''}</b><span>Toca para ver qué haría primero</span></div>${ico('chev')}</a>`;}
  _hc={v:_sf.v===ver?ver:-1,h};return h;
}
const _hoy=PAGES.hoy;
PAGES.hoy=function(){const h=_hoy.apply(this,arguments);const t=tarjetaHoy();return t?h.replace('<main class="bd">','<main class="bd">'+t):h;};
window.RumiPro={chequeo,chequeoFinca,simular,simFinca,riesgoLote,riesgoFinca,rankingHtml,semaforo,semana,escenarios,margenCon};
})();
