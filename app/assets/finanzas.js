/* Finanzas e Inventario de Rumentis 3.9.
   Finanzas: balance con el ganado como activo biológico (valor de mercado menos desbaste, como pide la NIC 41),
   estado de resultados del período, flujo de caja por mes, créditos y rentabilidad de cada lote vendido.
   Inventario: ganado, alimento (de la Bodega), medicinas e insumos, y equipo e instalaciones.
   Todo se guarda en S.config.fin (va en el respaldo). */
(function(){
'use strict';
const I=k=>icono(k,'i3');
const F=()=>{const f=S.config.fin||{};return {caja:f.caja||null,creditos:f.creditos||[],equipos:f.equipos||[],insumos:f.insumos||[],imovs:f.imovs||[]};};
const guardarF=d=>put('ajustes','finca',{...S.config,fin:{...(S.config.fin||{}),...d}});
const nomMes=m=>{const [y,mm]=m.split('-');return MESES[+mm-1]+(y===hoy().slice(0,4)?'':' '+y.slice(2));};
const pct=(v,d=0)=>v==null||!isFinite(v)?'–':nf(v*100,d)+' %';
const addMeses=(f,n)=>{const [y,m,d]=f.split('-').map(Number);const t=new Date(Date.UTC(y,m-1+n,1));const ult=new Date(Date.UTC(t.getUTCFullYear(),t.getUTCMonth()+1,0)).getUTCDate();t.setUTCDate(Math.min(d,ult));return t.toISOString().slice(0,10);};
const u5=t=>g5(esc(t));   // unidades de insumos: no se pasan a libras

/* ================= cálculos ================= */
function ganado(){
  const C=calc(),pv=+S.config.precioVentaKg||0,des=(+S.config.desbaste||0)/100;
  return C.act.map(x=>{const kg=x.pesoHoy*x.cab,vivas=Math.max(1,x.cab0-x.bajas);
    return {x,kg,mercado:kg*(1-des)*pv,costo:x.costoTot*x.cab/vivas};});
}
function insumo(it,movs){
  const mv=movs.filter(m=>m.item===it.id).sort((a,b)=>a.f<b.f?-1:a.f>b.f?1:0);
  let st=0,cu=+it.costoU||0,venc=null,ultC=null,uso30=0;const H=hoy();
  for(const m of mv){
    if(m.tipo==='conteo')st=+m.cant||0;
    else if(m.tipo==='compra'){st+=+m.cant||0;if(+m.costo>0&&+m.cant>0)cu=m.costo/m.cant;if(m.venc)venc=m.venc;ultC=m;}
    else if(m.tipo==='uso'){st-=+m.cant||0;if(dias(m.f,H)<=30)uso30+=+m.cant||0;}
  }
  st=Math.max(0,st);
  const diasAl=uso30>0?st/(uso30/30):null;
  const bajo=st<=(+it.min||0)||(diasAl!=null&&diasAl<14);
  const vence=venc&&st>0?dias(H,venc):null;
  return {stock:st,costoU:cu,valor:st*cu,venc,vence,ultC,diasAl,bajo,sin:!mv.length};
}
function equipo(e){
  const H=hoy(),años=Math.max(0,dias(e.f||H,H)/365.25),vida=Math.max(1,+e.vida||10),res=(+e.costo||0)*(+e.res||0)/100;
  const dep=((+e.costo||0)-res)/vida,acum=Math.min((+e.costo||0)-res,dep*años);
  return {libro:(+e.costo||0)-acum,dep,acum,años};
}
/* crédito con interés simple sobre saldo: cada abono paga primero el interés que se debe */
function credito(c,hasta){
  const H=hasta||hoy(),r=(+c.tasa||0)/100;let bal=+c.monto||0,int=0,last=c.f,pagInt=0,pagCap=0;const intPor=[];
  for(const a of (c.abonos||[]).filter(a=>a.f<=H).sort((a,b)=>a.f<b.f?-1:1)){
    const i=bal*r*Math.max(0,dias(last,a.f))/365;int+=i;intPor.push({f:a.f,i});let p=+a.monto||0;
    const pi=Math.min(p,int);int-=pi;p-=pi;pagInt+=pi;const pc=Math.min(p,bal);bal-=pc;pagCap+=pc;last=a.f;}
  const i=bal*r*Math.max(0,dias(last,H))/365;int+=i;intPor.push({f:H,i});
  const n=+c.plazo||0,rm=r/12,cuota=n?(rm?(+c.monto||0)*rm/(1-Math.pow(1+rm,-n)):(+c.monto||0)/n):null;
  return {saldo:bal+int,capital:bal,intPend:int,pagInt,pagCap,vence:n?addMeses(c.f,n):null,cuota,intPor};
}
/* intereses que corren entre dos fechas (para el estado de resultados) */
function interesEntre(c,d0,d1){const a=credito(c,d0>c.f?d0:c.f),b=credito(c,d1);return Math.max(0,(b.pagInt+b.intPend)-(d0>c.f?a.pagInt+a.intPend:0));}

function periodo(k){const H=hoy();
  if(k==='mes')return [H.slice(0,7)+'-01',H,'este mes'];
  if(k==='anio')return [H.slice(0,4)+'-01-01',H,'este año'];
  if(k==='todo'){const C=calc();const f0=Object.values(C.L).map(x=>x.l.fechaIngreso).concat(C.items.map(i=>i.f)).sort()[0]||H;return [f0,H,'desde el inicio'];}
  return [addDias(H,-365),H,'últimos 12 meses'];}

/* movimientos de dinero con fecha: lo que entra y lo que sale, a partir de tus registros */
function movimientos(){
  const C=calc(),fin=F(),M=[];
  for(const x of Object.values(C.L)){
    M.push({f:x.l.fechaIngreso,v:-x.compra,k:'compra',t:'Compra de '+x.l.nombre});
    for(const a of x.g.alimento)M.push({f:a.f,v:-(+a.kg||0)*(+a.costoKg||0),k:'alimento'});
    for(const s of x.g.sanidad)M.push({f:s.f,v:-(+s.costo||0),k:'sanidad'});
    for(const g of x.g.gasto)M.push({f:g.f,v:-(+g.monto||0),k:'gastos'});
    for(const v of x.g.venta)M.push({f:v.f,v:(+v.kg||0)*(+v.precioKg||0),k:'ventas',t:'Venta de '+x.l.nombre});
  }
  for(const g of C.generales)M.push({f:g.f,v:-(+g.monto||0),k:'generales'});
  for(const c of fin.creditos){M.push({f:c.f,v:+c.monto||0,k:'creditos',t:'Crédito '+c.n});for(const a of (c.abonos||[]))M.push({f:a.f,v:-(+a.monto||0),k:'abonos'});}
  for(const e of fin.equipos)if(e.f&&+e.costo>0)M.push({f:e.f,v:-(+e.costo),k:'equipo',t:e.n});
  return M.filter(m=>m.v);
}
function resultados(d0,d1){
  const C=calc(),fin=F(),en=f=>f>=d0&&f<=d1;
  let ventas=0,costoV=0,cabV=0;
  for(const x of Object.values(C.L)){const porCab=(x.costoTot-(x.gasGen||0))/Math.max(1,x.cab0-x.bajas);  // los gastos generales van aparte
    for(const v of x.g.venta)if(en(v.f)){ventas+=(+v.kg||0)*(+v.precioKg||0);costoV+=porCab*(+v.cab||0);cabV+=+v.cab||0;}}
  const generales=C.generales.filter(g=>en(g.f)).reduce((s,g)=>s+(+g.monto||0),0);
  const intereses=fin.creditos.reduce((s,c)=>s+interesEntre(c,d0,d1),0);
  const dep=fin.equipos.reduce((s,e)=>{const q=equipo(e);const a=Math.max(d0,e.f||d0),b=d1;return s+(b>a?q.dep*dias(a,b)/365.25:0);},0);
  const bruta=ventas-costoV,neta=bruta-generales-intereses-dep;
  const G=ganado(),noReal=G.reduce((s,o)=>s+o.mercado-o.costo,0);
  return {ventas,costoV,cabV,bruta,generales,intereses,dep,neta,noReal};
}
function balance(){
  const fin=F(),G=ganado(),bod=window.Bodega?Bodega.inventario():[];
  const ganadoM=G.reduce((s,o)=>s+o.mercado,0),ganadoC=G.reduce((s,o)=>s+o.costo,0);
  const alimento=bod.reduce((s,o)=>s+o.valor,0);
  const insumos=fin.insumos.reduce((s,it)=>s+insumo(it,fin.imovs).valor,0);
  const equipos=fin.equipos.reduce((s,e)=>s+equipo(e).libro,0);
  const caja=fin.caja?+fin.caja.efectivo||0:0,cxc=fin.caja?+fin.caja.cxc||0:0,cxp=fin.caja?+fin.caja.cxp||0:0;
  const creditos=fin.creditos.reduce((s,c)=>s+credito(c).saldo,0);
  const activos=caja+cxc+ganadoM+alimento+insumos+equipos,pasivos=creditos+cxp;
  return {caja,cxc,cxp,ganadoM,ganadoC,alimento,insumos,equipos,creditos,activos,pasivos,patrimonio:activos-pasivos};
}

/* ================= gráficos propios ================= */
/* barras de lo que entra (arriba) y lo que sale (abajo) cada mes, con la línea de lo que queda */
function flujoChart(meses){
  const W=340,h=210,pl=46,pr=8,pt=12,pb=26;
  const mx=Math.max(1,...meses.map(m=>Math.max(m.ent,m.sal)));
  const tk=niceTicks(-mx,mx,4),lo=tk[0],hi=tk[tk.length-1];
  const bw=(W-pl-pr)/meses.length,Y=v=>pt+(h-pt-pb)*(1-(v-lo)/(hi-lo||1));
  let g=tk.map(v=>`<line x1="${pl}" x2="${W-pr}" y1="${Y(v).toFixed(1)}" y2="${Y(v).toFixed(1)}" style="stroke:var(--line)" stroke-width="${v===0?1.6:1}"/><text x="${pl-6}" y="${(Y(v)+4).toFixed(1)}" text-anchor="end" font-size="11.5">${fmtCorto(v)}</text>`).join('');
  meses.forEach((m,i)=>{const x=pl+i*bw+bw*0.18,w=Math.max(2,bw*0.64),z=Y(0);
    if(m.ent)g+=`<rect class="cb" x="${x.toFixed(1)}" y="${Y(m.ent).toFixed(1)}" width="${w.toFixed(1)}" height="${(z-Y(m.ent)).toFixed(1)}" rx="2.5" style="fill:var(--verde)"/>`;
    if(m.sal)g+=`<rect class="cb" x="${x.toFixed(1)}" y="${z.toFixed(1)}" width="${w.toFixed(1)}" height="${(Y(-m.sal)-z).toFixed(1)}" rx="2.5" style="fill:var(--rojo);opacity:.8"/>`;
    if(i%2===(meses.length-1)%2)g+=`<text x="${(pl+i*bw+bw/2).toFixed(1)}" y="${h-7}" text-anchor="middle" font-size="11.5">${nomMes(m.m)}</text>`;});
  const pts=meses.map((m,i)=>`${(pl+i*bw+bw/2).toFixed(1)} ${Y(Math.max(lo,Math.min(hi,m.ent-m.sal))).toFixed(1)}`);
  g+=`<path d="M${pts.join(' L')}" fill="none" style="stroke:var(--ink)" stroke-width="2" stroke-linejoin="round"/>`+meses.map((m,i)=>`<circle cx="${(pl+i*bw+bw/2).toFixed(1)}" cy="${Y(Math.max(lo,Math.min(hi,m.ent-m.sal))).toFixed(1)}" r="3" style="fill:var(--ink);stroke:var(--card)" stroke-width="1.5"/>`).join('');
  return `<svg viewBox="0 0 ${W} ${h}" role="img" aria-label="Entradas y salidas de dinero por mes">${g}</svg><div class="legend"><span><i style="background:var(--verde)"></i>entra</span><span><i style="background:var(--rojo)"></i>sale</span><span><i style="background:var(--ink);height:3px;border-radius:2px"></i>queda cada mes</span></div>`;
}
const fmtCorto=v=>{const a=Math.abs(v);return (v<0?'−':'')+(a>=1e6?nf(a/1e6,1)+'M':a>=1e3?nf(a/1e3,0)+'k':nf(a));};
/* una barra partida en colores (composición) con su leyenda en filas */
function composicion(partes){
  const t=partes.reduce((s,p)=>s+Math.max(0,p.v),0);if(!(t>0))return `<p class="empty">Todavía no hay datos.</p>`;
  return `<div class="fz-comp">${partes.filter(p=>p.v>0).map(p=>`<i style="width:${(p.v/t*100).toFixed(2)}%;background:${p.c}" title="${esc(p.t)}"></i>`).join('')}</div>
   <div class="fz-leg">${partes.filter(p=>p.v>0).map(p=>`<div><i style="background:${p.c}"></i><span>${p.t}</span><b>${money(p.v)}</b><em>${nf(p.v/t*100,0)} %</em></div>`).join('')}</div>`;
}
const linea=(t,v,{b=false,neg=false,sub='',cls=''}={})=>{const d=neg?-v:v;return `<div class="fz-l${b?' b':''}${cls?' '+cls:''}"><span>${t}${sub?`<small>${sub}</small>`:''}</span><em${d<0&&!neg?' style="color:var(--rojo)"':''}>${d<0?'− ':''}${money(Math.abs(d))}</em></div>`;};

/* ================= FINANZAS ================= */
PAGES.finanzas=()=>{
  const C=calc(),fin=F(),k=UI.finPer||'12m',[d0,d1,pn]=periodo(k);
  const Bl=balance(),Rs=resultados(d0,d1);
  const chips=[['mes','Este mes'],['anio','Este año'],['12m','12 meses'],['todo','Todo']].map(([v,t])=>`<button type="button" class="chip" data-act="ui" data-k="finPer" data-v="${v}" aria-pressed="${k===v}">${t}</button>`).join('');
  // flujo por mes
  const mv=movimientos(),H=C.H,n=k==='mes'?6:12,meses=[];
  for(let i=n-1;i>=0;i--){const m=addMeses(H.slice(0,7)+'-01',-i).slice(0,7);const e=mv.filter(x=>x.f.slice(0,7)===m);meses.push({m,ent:e.filter(x=>x.v>0).reduce((s,x)=>s+x.v,0),sal:-e.filter(x=>x.v<0).reduce((s,x)=>s+x.v,0)});}
  const enP=mv.filter(x=>x.f>=d0&&x.f<=d1),entP=enP.filter(x=>x.v>0).reduce((s,x)=>s+x.v,0),salP=-enP.filter(x=>x.v<0).reduce((s,x)=>s+x.v,0);
  const salidas=['compra','alimento','sanidad','gastos','generales','abonos','equipo'].map((q,i)=>({t:{compra:'Compra de ganado',alimento:'Alimento',sanidad:'Sanidad',gastos:'Gastos de lotes',generales:'Gastos generales',abonos:'Pagos de créditos',equipo:'Equipo'}[q],v:-enP.filter(x=>x.k===q).reduce((s,x)=>s+x.v,0),c:['var(--s1)','var(--s4)','var(--s3)','var(--s2)','var(--s6)','var(--s5)','var(--ink2)'][i]}));
  // rentabilidad de lotes vendidos
  const cer=C.cer.filter(x=>x.costoTot>0).map(x=>{const roi=x.margenReal/x.costoTot,an=x.dec>0?Math.pow(1+roi,365/x.dec)-1:null;return {x,roi,an};});
  const act=C.act,margenProy=act.reduce((s,x)=>s+(x.proy?x.proy.margen:0),0);
  const alertas=[];
  if(Bl.patrimonio<0)alertas.push(['w','Debes más de lo que tienes: revisa créditos y precios de venta.']);
  if(act.some(x=>x.proy&&x.proy.margen<0))alertas.push(['w',`${act.filter(x=>x.proy&&x.proy.margen<0).map(x=>esc(x.l.nombre)).join(', ')} perdería dinero con el precio de hoy. Mira el precio mínimo en el lote.`]);
  for(const c of fin.creditos){const q=credito(c);if(q.vence&&q.saldo>1&&dias(H,q.vence)<=45)alertas.push(['w',`El crédito ${esc(c.n)} vence el ${ffl(q.vence)} y debes ${money(q.saldo)}.`]);}
  const hero=`<div class="kpis"><div class="kpi"><b>${money(Bl.patrimonio)}</b><span>patrimonio</span></div><div class="kpi"><b style="${Rs.neta<0?'color:#ffb4a8':''}">${money(Rs.neta)}</b><span>ganancia ${pn}</span></div><div class="kpi"><b>${money(Bl.ganadoM)}</b><span>ganado en pie</span></div></div>`;
  const bal=`<section class="gcard"><h3>Balance a hoy</h3>
    <p class="gque"><b>Qué estás viendo:</b> lo que tienes (activos), lo que debes (pasivos) y lo que es tuyo de verdad (patrimonio). El ganado en engorde vale lo que te pagarían hoy: peso en pie, menos desbaste, por tu precio de venta.</p>
    <div class="fz-t">Lo que tienes</div>
    ${linea('Efectivo y bancos',Bl.caja,{sub:fin.caja?`al ${ffc(fin.caja.f)}`:'toca Actualizar'})}
    ${Bl.cxc?linea('Te deben (por cobrar)',Bl.cxc):''}
    ${linea('Ganado en engorde',Bl.ganadoM,{sub:`${pl(C.cabT,'cabeza','cabezas')}, ${nf(C.kgPie)} kg en pie · al costo ${money(Bl.ganadoC)}`})}
    ${linea('Alimento en bodega',Bl.alimento,{sub:window.Bodega&&Bodega.inventario().length?'':'anótalo en Bodega'})}
    ${linea('Medicinas e insumos',Bl.insumos)}
    ${linea('Equipo e instalaciones',Bl.equipos,{sub:fin.equipos.length?'menos depreciación':'agrégalos en Inventario'})}
    ${linea('Total de activos',Bl.activos,{b:true})}
    <div class="fz-t">Lo que debes</div>
    ${linea('Créditos',Bl.creditos,{sub:fin.creditos.length?pl(fin.creditos.length,'crédito','créditos')+' con intereses a hoy':''})}
    ${Bl.cxp?linea('Cuentas por pagar',Bl.cxp):''}
    ${linea('Total de pasivos',Bl.pasivos,{b:true})}
    ${linea('Patrimonio',Bl.patrimonio,{b:true,cls:'tot'})}
    ${composicion([{t:'Ganado',v:Bl.ganadoM,c:'var(--s1)'},{t:'Alimento',v:Bl.alimento,c:'var(--s4)'},{t:'Insumos',v:Bl.insumos,c:'var(--s3)'},{t:'Equipo',v:Bl.equipos,c:'var(--s2)'},{t:'Efectivo',v:Bl.caja+Bl.cxc,c:'var(--verde)'}])}
    <div class="acts"><button type="button" class="btn" data-act="f" data-f="finCaja">${I('cartera')}Actualizar efectivo</button><a class="btn" href="#inventario">${I('inventario')}Inventario</a></div></section>`;
  const res=`<section class="gcard"><h3>Estado de resultados</h3><div class="chips">${chips}</div>
    <p class="gque"><b>Qué estás viendo:</b> lo que ganaste ${pn} con lo que ya vendiste. El costo de lo vendido es lo que llevaba invertido cada animal vendido: compra, alimento, sanidad y gastos de su lote.</p>
    ${linea('Ventas de ganado',Rs.ventas,{sub:Rs.cabV?pl(Rs.cabV,'cabeza vendida','cabezas vendidas'):'sin ventas en este período'})}
    ${linea('Costo de lo vendido',Rs.costoV,{neg:true})}
    ${linea('Ganancia bruta',Rs.bruta,{b:true})}
    ${linea('Gastos generales',Rs.generales,{neg:true})}
    ${Rs.intereses?linea('Intereses de créditos',Rs.intereses,{neg:true}):''}
    ${Rs.dep?linea('Depreciación del equipo',Rs.dep,{neg:true}):''}
    ${linea('Ganancia neta',Rs.neta,{b:true,cls:'tot'})}
    <div class="glee"><b>Lo que dicen tus números</b><ul>
      <li class="sin"><span>El ganado en engorde vale hoy ${money(Math.abs(Rs.noReal))} ${Rs.noReal>=0?'más':'menos'} de lo que te ha costado. Esa ganancia todavía no es dinero: se hace real al vender.</span></li>
      ${act.length?`<li class="sin"><span>Si vendes todos los lotes en engorde al llegar a su meta con el precio de hoy, dejarían <b>${money(margenProy)}</b>.</span></li>`:''}
      ${Rs.ventas?`<li class="sin"><span>Margen sobre ventas: ${pct(Rs.bruta/Rs.ventas,1)}.</span></li>`:''}
      ${alertas.map(([c,t])=>`<li class="sin"><span>${t}</span><em class="st w">Revisar</em></li>`).join('')}</ul></div></section>`;
  const flujo=`<section class="gcard"><h3>Flujo de dinero</h3>
    <div class="gv"><b style="${entP-salP<0?'color:var(--rojo)':''}">${money(entP-salP)}</b><span class="sm">queda ${pn}: entró ${money(entP)} y salió ${money(salP)}</span></div>
    <p class="gque"><b>Qué estás viendo:</b> cada mes, el dinero que entró (ventas y créditos) arriba y el que salió (compras, alimento, sanidad, gastos y pagos) abajo. La línea es lo que quedó ese mes.</p>
    ${flujoChart(meses)}
    <div class="fz-t">A dónde se fue el dinero ${pn}</div>${composicion(salidas.map(o=>({...o})))}</section>`;
  const rent=cer.length?`<section class="gcard"><h3>Rentabilidad de lotes vendidos</h3>
    <p class="gque"><b>Qué estás viendo:</b> cuánto rindió cada peso invertido en el lote. Anualizado es lo que rendiría en un año al mismo ritmo; compáralo con la tasa de un banco.</p>
    ${hBars(cer.map(o=>({label:`${o.x.l.nombre}: ${money(o.x.margenReal)} en ${o.x.dec} días`,v:o.roi*100,color:o.roi>=0?'var(--verde)':'var(--rojo)'})),{fmt:v=>nf(v,1)+' %',signed:true})}
    <div class="glee"><ul>${cer.slice(0,6).map(o=>`<li class="sin"><span><b>${esc(o.x.l.nombre)}</b>: ${pct(o.roi,1)} en ${o.x.dec} días${o.an!=null?`, ${pct(o.an,0)} al año`:''}.</span></li>`).join('')}</ul></div></section>`:'';
  const cred=`<section class="sec">${secH('Créditos',fin.creditos.length||'',lnkB('f','Agregar','data-f="finCredito"'))}${fin.creditos.length?`<div class="card rows">${fin.creditos.map(c=>{const q=credito(c);return `<button type="button" class="row" data-act="f" data-f="finCredito" data-id="${esc(c.id)}"><span class="mas-ic t-t">${I('banco')}</span><div class="tx"><b>${esc(c.n)}: debes ${money(q.saldo)}</b><span>${money(+c.monto)} al ${nf(+c.tasa,1)} % anual${q.vence?`, vence el ${ffc(q.vence)}`:''}${q.cuota?` · cuota ${money(q.cuota)}`:''}</span></div>${ico('chev')}</button>`;}).join('')}</div>`:`<div class="card"><p class="empty">Sin créditos. Si financias el engorde, agrégalo: sus intereses entran en tus costos.</p></div>`}</section>`;
  return `<header class="hd">${hdTop('<span class="sync"></span>')}<div class="ttl"><h1>Finanzas</h1><p class="sub">Tu balance, lo que ganas y a dónde va tu dinero.</p></div>${hero}</header>
   <main class="bd" style="gap:18px">${bal}${res}${flujo}${rent}${cred}<p class="hint">Todo sale de tus registros de compra, alimento, sanidad, gastos y ventas. <a class="lnk" href="#metodologia" style="padding:0">Cómo calculo</a></p></main>`;
};

/* ================= INVENTARIO ================= */
PAGES.inventario=()=>{
  const C=calc(),fin=F(),G=ganado(),bod=window.Bodega?Bodega.inventario():[];
  const ins=fin.insumos.map(it=>({it,q:insumo(it,fin.imovs)}));
  const vG=G.reduce((s,o)=>s+o.mercado,0),vA=bod.reduce((s,o)=>s+o.valor,0),vI=ins.reduce((s,o)=>s+o.q.valor,0),vE=fin.equipos.reduce((s,e)=>s+equipo(e).libro,0);
  const avisos=ins.filter(o=>o.q.bajo||(o.q.vence!=null&&o.q.vence<=30)).length+bod.filter(o=>!o.e.sin&&o.e.dias!=null&&o.e.dias<(+o.it.min||7)).length;
  const hero=`<div class="kpis"><div class="kpi"><b>${money(vG+vA+vI+vE)}</b><span>valor del inventario</span></div><div class="kpi"><b>${nf(C.cabT)}</b><span>cabezas</span></div><div class="kpi"><b>${avisos||'0'}</b><span>${avisos===1?'aviso':'avisos'}</span></div></div>`;
  const gan=`<section class="sec">${secH('Ganado',pl(C.cabT,'cabeza','cabezas'))}${G.length?`<div class="card rows">${G.map(o=>`<a class="row" href="#lote/${encodeURIComponent(o.x.id)}"><span class="mas-ic t-a">${I('vaca')}</span><div class="tx"><b>${esc(o.x.l.nombre)}: ${pl(o.x.cab,'cabeza','cabezas')}</b><span>${nf(o.kg)} kg en pie, ${nf(o.x.pesoHoy)} kg por cabeza · ${o.x.l.raza?esc(o.x.l.raza):'sin raza'}</span></div><div class="fz-v"><b>${money(o.mercado)}</b><small>costó ${money(o.costo)}</small></div></a>`).join('')}</div>`:`<div class="card"><p class="empty">No hay ganado en engorde.</p></div>`}</section>`;
  const ali=`<section class="sec">${secH('Alimento',bod.length?money(vA):'',lnk('#bodega','Bodega'))}${bod.length?`<div class="card rows">${bod.map(o=>{const d=o.e.dias,c=d==null?'':d<(+o.it.min||7)?'p-rojo':d<(+o.it.min||7)*2?'p-tierra':'p-verde';return `<a class="row" href="#bodega"><span class="mas-ic t-y">${I('bodegaG')}</span><div class="tx"><b>${esc(o.it.n)}</b><span>${o.e.sin?'sin inventario anotado':`${nf(o.stock)} kg${o.valor?' · '+money(o.valor):''}`}</span></div>${d!=null?`<span class="pill ${c}">${d<1?'se acaba hoy':pl(Math.floor(d),'día','días')}</span>`:ico('chev')}</a>`;}).join('')}</div>`:`<div class="card pad"><p class="hint" style="margin:0 0 10px">Anota tu alimento en la Bodega y aquí verás cuánto vale y para cuántos días te alcanza.</p><a class="btn full" href="#bodega">Abrir la bodega</a></div>`}</section>`;
  const med=`<section class="sec">${secH('Medicinas e insumos',ins.length?money(vI):'',lnkB('f','Agregar','data-f="insItem"'))}${ins.length?`<div class="card rows">${ins.map(({it,q})=>{
      const st=q.vence!=null&&q.vence<0?['p-rojo','vencido']:q.vence!=null&&q.vence<=30?['p-tierra',`vence en ${pl(q.vence,'día','días')}`]:q.bajo?['p-rojo','por acabarse']:q.sin?['p-anil','sin datos']:['p-verde','bien'];
      return `<div class="row fz-ins"><button type="button" class="fz-ib" data-act="f" data-f="insItem" data-id="${esc(it.id)}"><span class="mas-ic t-v">${I(/vacun/i.test(it.cat||'')?'jeringa':'medicina')}</span><div class="tx"><b>${esc(it.n)}</b><span>${nf(q.stock,q.stock%1?1:0)} ${u5(it.u||'unidades')}${q.valor?' · '+money(q.valor):''}${q.venc?` · vence ${ffc(q.venc)}`:''}</span></div><span class="pill ${st[0]}">${st[1]}</span></button>
        <div class="fz-ia"><button type="button" class="btn sm" data-act="f" data-f="insMov" data-id="${esc(it.id)}" data-t="compra">Compra</button><button type="button" class="btn sm" data-act="f" data-f="insMov" data-id="${esc(it.id)}" data-t="uso">Uso</button><button type="button" class="btn sm" data-act="f" data-f="insMov" data-id="${esc(it.id)}" data-t="conteo">Contar</button></div></div>`;}).join('')}</div>`:`<div class="card pad"><p class="hint" style="margin:0 0 10px">Lleva tus vacunas, desparasitantes, antibióticos, vitaminas y otros insumos: cuánto hay, cuánto valen y cuándo vencen. Te aviso en la Agenda antes de que se acaben o se venzan.</p><button type="button" class="btn full" data-act="f" data-f="insItem">Agregar un producto</button></div>`}</section>`;
  const eq=`<section class="sec">${secH('Equipo e instalaciones',fin.equipos.length?money(vE):'',lnkB('f','Agregar','data-f="finEquipo"'))}${fin.equipos.length?`<div class="card rows">${fin.equipos.map(e=>{const q=equipo(e);return `<button type="button" class="row" data-act="f" data-f="finEquipo" data-id="${esc(e.id)}"><span class="mas-ic t-t">${I(/tractor|carro|camión|vehículo/i.test(e.n+' '+(e.cat||''))?'tractor':'equipo')}</span><div class="tx"><b>${esc(e.n)}</b><span>${esc(e.cat||'Equipo')} · costó ${money(+e.costo)}${e.f?' en '+ffc(e.f):''} · pierde ${money(q.dep)} al año</span></div><div class="fz-v"><b>${money(q.libro)}</b><small>vale hoy</small></div></button>`;}).join('')}</div>`:`<div class="card"><p class="empty">Báscula, corrales, comederos, picadora, tractor… Agrégalos para que tu balance esté completo.</p></div>`}</section>`;
  return `<header class="hd">${hdTop('<span class="sync"></span>')}<div class="ttl"><h1>Inventario</h1><p class="sub">Todo lo que tienes en la finca y cuánto vale.</p></div>${hero}</header>
   <main class="bd">${gan}${ali}${med}${eq}<p class="hint">El ganado se valora con su peso en pie de hoy, menos desbaste, a tu precio de venta. El equipo pierde valor en línea recta durante su vida útil.</p></main>`;
};

/* ================= formularios ================= */
const CATS_INS=['Vacuna','Desparasitante','Antibiótico','Vitamina o mineral','Antiinflamatorio','Implante','Insumo','Otro'];
const UNI_INS=['ml','dosis','frascos','litros','kg','g','unidades','sacos','cajas'];
Object.assign(FORMS,{
  insItem:({id}={})=>{const it=id?F().insumos.find(x=>x.id===id):null;
    const body=`${q('Nombre',`<input class="in" name="n" value="${esc(it?it.n:'')}" placeholder="Ivermectina 1 %" required autocomplete="off">`)}
      <div class="two">${q('Tipo',`<select class="in" name="cat">${CATS_INS.map(c=>`<option${it&&it.cat===c?' selected':''}>${c}</option>`).join('')}</select>`)}${q('Se cuenta en',`<select class="in" name="u">${UNI_INS.map(c=>`<option${(it?it.u:'ml')===c?' selected':''}>${u5(c)}</option>`).join('')}</select>`)}</div>
      ${q('Avisarme cuando queden menos de',inp('min',it?it.min||'':'',{ph:'0'}))}
      ${it?`<button type="button" class="btn danger" data-act="insBorrar" data-id="${esc(it.id)}">Quitar del inventario</button>`:''}`;
    openSheet(shHead(it?'Editar producto':'Agregar producto','Medicinas e insumos')+formWrap('insItem',body,foot('Guardar'),it?`data-id="${esc(it.id)}"`:''));},
  insMov:({id,t}={})=>{const it=F().insumos.find(x=>x.id===id);if(!it)return;const u=u5(it.u||'unidades');
    const L=calc().act;
    const tt={compra:'Compra de ',uso:'Uso de ',conteo:'Contar '}[t]+esc(it.n);
    const body=`<div class="two">${fecha()}${q(t==='conteo'?'Hay ahora':'Cantidad',inp('cant','',{unit:u,ph:'0',xl:true}))}</div>
      ${t==='compra'?`<div class="two">${q('Costo total',inp('costo','',{unit:S.config.moneda||'L',ph:'0'}))}${q('Vence',`<input class="in" type="date" name="venc">`)}</div>${q('Proveedor',`<input class="in" name="prov" placeholder="Opcional" autocomplete="off">`)}`:''}
      ${t==='uso'&&L.length?q('En qué lote',`<select class="in" name="lote"><option value="">Varios o ninguno</option>${L.map(x=>`<option value="${esc(x.id)}">${esc(x.l.nombre)}</option>`).join('')}</select>`):''}`;
    openSheet(shHead(tt,'Inventario')+formWrap('insMov',body,foot('Guardar'),`data-id="${esc(id)}" data-t="${t}"`));},
  finEquipo:({id}={})=>{const e=id?F().equipos.find(x=>x.id===id):null;
    const body=`${q('Nombre',`<input class="in" name="n" value="${esc(e?e.n:'')}" placeholder="Báscula ganadera" required autocomplete="off">`)}
      <div class="two">${q('Tipo',`<select class="in" name="cat">${['Instalación','Equipo','Maquinaria','Vehículo','Herramienta','Otro'].map(c=>`<option${e&&e.cat===c?' selected':''}>${c}</option>`).join('')}</select>`)}${q('Costo',inp('costo',e?e.costo:'',{unit:S.config.moneda||'L',ph:'0'}))}</div>
      <div class="two">${fecha(e?e.f:hoy())}${q('Vida útil',inp('vida',e?e.vida:10,{unit:'años',mode:'numeric'}))}</div>
      ${q('Valor al final de su vida',inp('res',e?e.res||0:10,{unit:'%',mode:'numeric'}),'Lo que valdría al venderlo usado. Muchas veces 10 %.')}
      ${e?`<button type="button" class="btn danger" data-act="finBorrar" data-k="equipos" data-id="${esc(e.id)}">Quitar</button>`:''}`;
    openSheet(shHead(e?'Editar equipo':'Agregar equipo o instalación','Inventario')+formWrap('finEquipo',body,foot('Guardar'),e?`data-id="${esc(e.id)}"`:''));},
  finCredito:({id}={})=>{const c=id?F().creditos.find(x=>x.id===id):null,qd=c?credito(c):null;
    const ab=c&&(c.abonos||[]).length?`<div class="q"><span class="lb">Pagos hechos</span><div class="card rows">${c.abonos.slice().sort((a,b)=>a.f<b.f?1:-1).map(a=>`<div class="row"><div class="tx"><b>${money(+a.monto)}</b><span>${ffl(a.f)}</span></div><button type="button" class="lnk" data-act="finAbonoQuitar" data-id="${esc(c.id)}" data-a="${esc(a.id)}">Quitar</button></div>`).join('')}</div></div>`:'';
    const body=`${c?`<div class="card pad fz-cr"><div class="kv"><span>Debes hoy</span><b>${money(qd.saldo)}</b></div><div class="kv"><span>Capital</span><b>${money(qd.capital)}</b></div><div class="kv"><span>Intereses por pagar</span><b>${money(qd.intPend)}</b></div><div class="kv"><span>Intereses pagados</span><b>${money(qd.pagInt)}</b></div>${qd.cuota?`<div class="kv"><span>Cuota mensual estimada</span><b>${money(qd.cuota)}</b></div>`:''}</div>
        <button type="button" class="btn pri" data-act="f" data-f="finAbono" data-id="${esc(c.id)}">Anotar un pago</button>`:''}
      ${q('Nombre',`<input class="in" name="n" value="${esc(c?c.n:'')}" placeholder="Banco, cooperativa…" required autocomplete="off">`)}
      <div class="two">${q('Monto',inp('monto',c?c.monto:'',{unit:S.config.moneda||'L',ph:'0'}))}${q('Tasa anual',inp('tasa',c?c.tasa:'',{unit:'%',ph:'0'}))}</div>
      <div class="two">${fecha(c?c.f:hoy())}${q('Plazo',inp('plazo',c?c.plazo||'':'',{unit:'meses',mode:'numeric',ph:'12'}))}</div>${ab}
      ${c?`<button type="button" class="btn danger" data-act="finBorrar" data-k="creditos" data-id="${esc(c.id)}">Eliminar crédito</button>`:''}`;
    openSheet(shHead(c?esc(c.n):'Agregar crédito','Finanzas')+formWrap('finCredito',body,foot('Guardar'),c?`data-id="${esc(c.id)}"`:''));},
  finAbono:({id}={})=>{const c=F().creditos.find(x=>x.id===id);if(!c)return;const qd=credito(c);
    openSheet(shHead('Pago a '+esc(c.n),'Crédito')+formWrap('finAbono',`<div class="two">${fecha()}${q('Monto pagado',inp('monto','',{unit:S.config.moneda||'L',ph:'0',xl:true}))}</div><p class="hint" style="margin:0">Hoy debes ${money(qd.saldo)}. El pago cubre primero los intereses y lo demás baja el capital.</p>`,foot('Guardar pago')),()=>{$('#sheet form').dataset.id=id;});},
  finCaja:()=>{const c=F().caja||{};
    const body=`${q('Efectivo y bancos hoy',inp('efectivo',c.efectivo||'',{unit:S.config.moneda||'L',ph:'0',xl:true}),'Lo que tienes en caja y en tus cuentas.')}
      <div class="two">${q('Te deben',inp('cxc',c.cxc||'',{unit:S.config.moneda||'L',ph:'0'}),'Ventas por cobrar')}${q('Debes a proveedores',inp('cxp',c.cxp||'',{unit:S.config.moneda||'L',ph:'0'}),'Sin contar créditos')}</div>`;
    openSheet(shHead('Efectivo y cuentas','Finanzas')+formWrap('finCaja',body,foot('Guardar')));}
});
const upd=(k,list)=>guardarF({[k]:list});
Object.assign(SAVE,{
  insItem:f=>{const n=fv(f,'n').trim();if(!n)return ferr(f,'Escribe el nombre.');const d={n,cat:fv(f,'cat'),u:fv(f,'u'),min:num(fv(f,'min'))||0};const L=F().insumos;
    upd('insumos',f.dataset.id?L.map(x=>x.id===f.dataset.id?{...x,...d}:x):L.concat({id:uid('in'),...d}));closeSheet();toast('Producto guardado');},
  insMov:f=>{const raw=fv(f,'cant').trim();const c=num(raw);const t=f.dataset.t;if(raw===''||!(c>=0)||(t!=='conteo'&&!(c>0)))return ferr(f,'Escribe la cantidad.');
    const m={id:uid('im'),item:f.dataset.id,tipo:t,f:fv(f,'f')||hoy(),cant:c};if(t==='compra'){m.costo=num(fv(f,'costo'))||0;m.venc=fv(f,'venc')||null;m.prov=fv(f,'prov').trim();}if(t==='uso')m.lote=fv(f,'lote')||null;
    upd('imovs',F().imovs.concat(m));closeSheet();toast({compra:'Compra anotada',uso:'Uso anotado',conteo:'Inventario corregido'}[t]);},
  finEquipo:f=>{const n=fv(f,'n').trim();if(!n)return ferr(f,'Escribe el nombre.');const costo=num(fv(f,'costo'));if(!(costo>0))return ferr(f,'Escribe el costo.');
    const d={n,cat:fv(f,'cat'),costo,f:fv(f,'f')||hoy(),vida:Math.max(1,num(fv(f,'vida'))||10),res:Math.min(90,Math.max(0,num(fv(f,'res'))||0))};const L=F().equipos;
    upd('equipos',f.dataset.id?L.map(x=>x.id===f.dataset.id?{...x,...d}:x):L.concat({id:uid('eq'),...d}));closeSheet();toast('Equipo guardado');},
  finCredito:f=>{const n=fv(f,'n').trim();if(!n)return ferr(f,'Escribe el nombre.');const monto=num(fv(f,'monto'));if(!(monto>0))return ferr(f,'Escribe el monto.');
    const d={n,monto,tasa:num(fv(f,'tasa'))||0,f:fv(f,'f')||hoy(),plazo:Math.round(num(fv(f,'plazo'))||0)};const L=F().creditos;
    upd('creditos',f.dataset.id?L.map(x=>x.id===f.dataset.id?{...x,...d}:x):L.concat({id:uid('cr'),abonos:[],...d}));closeSheet();toast('Crédito guardado');},
  finAbono:f=>{const m=num(fv(f,'monto'));if(!(m>0))return ferr(f,'Escribe el monto.');
    upd('creditos',F().creditos.map(c=>c.id===f.dataset.id?{...c,abonos:(c.abonos||[]).concat({id:uid('ab'),f:fv(f,'f')||hoy(),monto:m})}:c));closeSheet();toast('Pago anotado');},
  finCaja:f=>{guardarF({caja:{efectivo:num(fv(f,'efectivo'))||0,cxc:num(fv(f,'cxc'))||0,cxp:num(fv(f,'cxp'))||0,f:hoy()}});closeSheet();toast('Efectivo actualizado');}
});
Object.assign(ACTS,{
  insBorrar:el=>confirmar('Quitar producto','Se quita el producto y sus movimientos.','Quitar',()=>{const id=el.dataset.id;guardarF({insumos:F().insumos.filter(x=>x.id!==id),imovs:F().imovs.filter(m=>m.item!==id)});toast('Producto quitado');}),
  finBorrar:el=>confirmar('Eliminar','Se elimina de tus finanzas. Esto no se puede deshacer.','Eliminar',()=>{const k=el.dataset.k;upd(k,F()[k].filter(x=>x.id!==el.dataset.id));toast('Eliminado');}),
  finAbonoQuitar:el=>{upd('creditos',F().creditos.map(c=>c.id===el.dataset.id?{...c,abonos:(c.abonos||[]).filter(a=>a.id!==el.dataset.a)}:c));FORMS.finCredito({id:el.dataset.id});}
});

/* avisos para la Agenda */
window.tareasFin=()=>{
  const fin=F(),H=hoy(),T=[];
  for(const it of fin.insumos){const q=insumo(it,fin.imovs);if(q.sin)continue;
    if(q.vence!=null&&q.vence<=30)T.push({key:'insv:'+it.id+':'+q.venc,f:H,t:q.vence<0?`${it.n} está vencido`:`${it.n} vence pronto`,s:q.vence<0?`Venció el ${ffc(q.venc)}: no lo uses`:`Vence el ${ffc(q.venc)}; úsalo primero`,ic:'info',act:{t:'go',go:'#inventario'},tono:'r'});
    else if(q.bajo)T.push({key:'insb:'+it.id+':'+H,f:H,t:`Comprar ${it.n}`,s:`Quedan ${nf(q.stock,q.stock%1?1:0)} ${it.u||'unidades'}`,ic:'bodega',act:{t:'go',go:'#inventario'},tono:'y'});}
  for(const c of fin.creditos){const q=credito(c);if(q.vence&&q.saldo>1&&dias(H,q.vence)<=30)T.push({key:'crv:'+c.id+':'+q.vence,f:addDias(q.vence,-30)<H?H:addDias(q.vence,-30),t:`Pagar crédito ${c.n}`,s:`Vence el ${ffc(q.vence)}; debes ${money(q.saldo)}`,ic:'info',act:{t:'go',go:'#finanzas'},tono:'r'});}
  return T;
};
/* para Rumi y otras pantallas */
window.Fin={balance,resultados,periodo,ganado,insumo,credito,equipo,movimientos,F};
})();

/* el engorde de ejemplo trae también finanzas e inventario, para verlos funcionando */
(function(){
  if(typeof cargarDemo!=='function')return;
  const base=cargarDemo;
  cargarDemo=function(){
    base.apply(this,arguments);
    const D=n=>addDias(hoy(),n),id=p=>uid(p),R=typeof demoReg==='function'?demoReg():{a:1,h:1,e:1,agro:'Agroservicio El Campo',banco:'Banco agrícola',eq:['Corrales y comederos','Báscula ganadera','Picadora de forraje','Tractor'],ins:['Ivermectina 1 %','Vacuna clostridial','Oxitetraciclina LA','Vitamina AD3E','Aretes numerados']},$e=v=>Math.round(v*R.e),$h=v=>Math.round(v*R.h);
    const t0=toast;toast=()=>{};try{ACTS.bodegaAuto&&ACTS.bodegaAuto();}catch(e){}finally{toast=t0;}
    const b=S.config.bodega||{items:[],movs:[]},pr={'silage':2.2,'silagem':2.2,'maíz':6.2,'maiz':6.2,'heno':3.1,'pulpa':4.4,'harina':9.8,'melaza':5.6,'urea':14,'sal':8.5,'minerales':22,'corn':6.2,'milho':6.2,'distillers':4.4,'polpa':4.4,'hay':3.1,'farelo':9.8,'supplement':22,'núcleo':22};
    const movs=[];b.items.forEach((it,i)=>{const k=Object.keys(pr).find(z=>it.n.toLowerCase().includes(z));const kg=[4200,2600,1800,950,700,400][i%6];
      movs.push({id:id('m'),item:it.id,tipo:'compra',f:D(-12),kg,costo:Math.round(kg*(k?pr[k]:6)*R.a),prov:R.agro});
      movs.push({id:id('m'),item:it.id,tipo:'conteo',f:D(-3),kg:Math.round(kg*0.55)});});
    const ins=[{n:R.ins[0],cat:'Desparasitante',u:'ml',min:100},{n:R.ins[1],cat:'Vacuna',u:'dosis',min:40},{n:R.ins[2],cat:'Antibiótico',u:'ml',min:150},{n:R.ins[3],cat:'Vitamina o mineral',u:'ml',min:100},{n:R.ins[4],cat:'Insumo',u:'unidades',min:30}].map(x=>({id:id('in'),...x}));
    const im=[];const mv=(i,t,f,cant,extra={})=>im.push({id:id('im'),item:ins[i].id,tipo:t,f,cant,...extra});
    mv(0,'compra',D(-60),1000,{costo:$h(4200),venc:D(420)});mv(0,'uso',D(-45),470);mv(0,'uso',D(-15),260);
    mv(1,'compra',D(-50),100,{costo:$h(5400),venc:D(22)});mv(1,'uso',D(-40),48);mv(1,'uso',D(-14),30);
    mv(2,'compra',D(-30),500,{costo:$h(6250),venc:D(300)});mv(2,'uso',D(-10),120);
    mv(3,'compra',D(-40),250,{costo:$h(2100),venc:D(200)});mv(3,'uso',D(-38),180);
    mv(4,'compra',D(-70),200,{costo:$h(3000)});mv(4,'uso',D(-16),52);
    const fin={caja:{efectivo:$e(385000),cxc:0,cxp:$e(42000),f:D(-2)},
      creditos:[{id:id('cr'),n:R.banco,monto:$e(1500000),tasa:12,f:D(-200),plazo:18,abonos:[{id:id('ab'),f:D(-110),monto:$e(150000)},{id:id('ab'),f:D(-20),monto:$e(150000)}]}],
      equipos:[{id:id('eq'),n:R.eq[0],cat:'Instalación',costo:$e(620000),f:D(-1500),vida:20,res:10},{id:id('eq'),n:R.eq[1],cat:'Equipo',costo:$e(185000),f:D(-900),vida:15,res:10},
        {id:id('eq'),n:R.eq[2],cat:'Maquinaria',costo:$e(95000),f:D(-600),vida:8,res:10},{id:id('eq'),n:R.eq[3],cat:'Vehículo',costo:$e(1250000),f:D(-2000),vida:12,res:15}],
      insumos:ins,imovs:im};
    put('ajustes','finca',{...S.config,bodega:{items:b.items,movs:b.movs.concat(movs)},fin});
    // el conteo de ayer se ajusta a lo que comen de verdad: alcanza para 2 a 5 semanas
    if(window.Bodega){const inv=Bodega.inventario(),c2=inv.filter(o=>o.e.dia>0).map((o,i)=>({id:id('m'),item:o.it.id,tipo:'conteo',f:D(-1),kg:Math.round(o.e.dia*(16+i*6))}));
      if(c2.length)put('ajustes','finca',{...S.config,bodega:{...S.config.bodega,movs:S.config.bodega.movs.concat(c2)}});}
  };
})();
