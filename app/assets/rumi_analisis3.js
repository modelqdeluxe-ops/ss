/* Rumentis: más análisis de Rumi.
   - ¿Estoy mejorando lote a lote? Ganancia, conversión, mortalidad, costo del kilo ganado y margen de cada lote vendido,
     en orden, con la tendencia de los primeros contra los últimos.
   - ¿Con qué ración te sale más barato engordar? Entre pesaje y pesaje, lo que subió el lote y lo que comió de cada ración:
     conversión y costo del kilo ganado por ración (solo tramos donde una ración fue al menos 70 % de lo que comieron).
   - ¿Qué mejorar primero? Cuánto dinero vale cada palanca en tus lotes en engorde: ganancia diaria, costo del alimento,
     precio de venta y una muerte menos, ordenadas.
   - ¿Vendo ahora o espero, con el mercado? El mejor día de venta con tu precio fijo y con la tendencia del mercado
     de las últimas 4 semanas (a la mitad, para no exagerarla). */
(function(){
'use strict';
if(!window.RumiMenu||!window.RumiPro)return;
const des=()=>Math.max(0,Math.min(15,+S.config.desbaste||0))/100;
const pvKg=()=>+S.config.precioVentaKg||0;
const kvs=rows=>`<div class="rp-kv">${rows.map(([a,b])=>`<div><span>${a}</span><em>${b}</em></div>`).join('')}</div>`;
const tablaW=(h,r)=>tabla(h,r).replace('class="tb"','class="tb tw"');
const pill=(c,t)=>`<span class="pill p-${c}">${t}</span>`;
const nota=t=>`<p class="rs">${t}</p>`;

/* ---------- 1. ¿Estoy mejorando lote a lote? ---------- */
function progreso(){
  const C=calc(),L=C.cer.filter(x=>x.l.fechaCierre).sort((a,b)=>a.l.fechaIngreso<b.l.fechaIngreso?-1:1);
  if(L.length<2)return {html:'<b>¿Estoy mejorando lote a lote?</b><br>Necesito al menos dos lotes vendidos para ver si vas mejorando. Cuando cierres el segundo, te lo muestro.'};
  const fila=x=>{const cb=Math.max(1,x.cab0-x.bajas);return {x,gdp:x.gdpTot,conv:x.conv||null,mort:x.mort*100,costo:x.costoKgGan||null,mg:x.margenReal/cb};};
  const F=L.map(fila);
  const mitad=Math.max(1,Math.floor(F.length/2)),A=F.slice(0,mitad),B=F.slice(-mitad);
  const prom=(a,k)=>{const v=a.map(o=>o[k]).filter(z=>z!=null&&isFinite(z));return v.length?v.reduce((s,z)=>s+z,0)/v.length:null;};
  const M=[{k:'gdp',t:'Ganancia diaria',alto:true,f:v=>`${nf(v,2)} kg`},{k:'conv',t:'Conversión',alto:false,f:v=>`${nf(v,1)} a 1`},{k:'mort',t:'Mortalidad',alto:false,f:v=>`${nf(v,1)} %`},
    {k:'costo',t:'Costo por kg ganado',alto:false,f:v=>pk(v)},{k:'mg',t:'Margen por cabeza',alto:true,f:v=>money(v)}];
  const cambios=M.map(m=>{const a=prom(A,m.k),b=prom(B,m.k);if(a==null||b==null)return null;const d=b-a,rel=a?d/Math.abs(a):0,mejor=m.alto?d>0:d<0;
    return {m,a,b,d,rel,mejor,igual:Math.abs(rel)<0.02};}).filter(Boolean);
  const mej=cambios.filter(c=>c.mejor&&!c.igual),peor=cambios.filter(c=>!c.mejor&&!c.igual);
  const rows=F.map(o=>[`<b>${esc(o.x.l.nombre)}</b><br><small>${ffc(o.x.l.fechaIngreso)}</small>`,`${nf(o.gdp,2)} kg`,o.conv?`${nf(o.conv,1)}`:'–',`${nf(o.mort,1)} %`,o.costo?pk(o.costo):'–',`<b style="color:var(--${o.mg>=0?'verde':'rojo'})">${money(o.mg)}</b>`]);
  const txt=mej.length&&!peor.length?`Vas mejorando en todo: ${mej.map(c=>c.m.t.toLowerCase()).join(', ')}. Sigue haciendo lo mismo.`:
    mej.length?`Mejoraste en ${mej.map(c=>c.m.t.toLowerCase()).join(', ')}, pero empeoraste en ${peor.map(c=>c.m.t.toLowerCase()).join(', ')}. Revisa qué cambió en esos lotes: proveedor, ración, época o manejo.`:
    peor.length?`Tus últimos lotes van peor que los primeros en ${peor.map(c=>c.m.t.toLowerCase()).join(', ')}. Compara proveedor, ración y época con los mejores.`:'Tus lotes van parejos: sin cambios grandes de uno a otro.';
  return {html:`<b>¿Estoy mejorando lote a lote?</b><br>Tus ${pl(F.length,'lote vendido','lotes vendidos')}, del más viejo al más nuevo. Comparo ${mitad===1?'el primero con el último':`los ${mitad} primeros con los ${mitad} últimos`}.`+
    tablaW(['Lote','Ganancia','Conv.','Muertes','Costo kg ganado','Margen por cabeza'],rows)+
    kvs(cambios.map(c=>[c.m.t,`${c.m.f(c.a)} → <b style="color:var(--${c.igual?'ink2':c.mejor?'verde':'rojo'})">${c.m.f(c.b)}</b>`]))+`<p>${txt}</p>`+
    nota('Con pocos lotes la diferencia puede ser suerte o la época del año; compara al menos tres o cuatro lotes.')};
}

/* ---------- 2. ¿Con qué ración te sale más barato engordar? ---------- */
function porRacion(){
  const C=calc(),L=C.act.concat(C.cer),R={};
  for(const x of L){
    const pes=[{f:x.l.fechaIngreso,v:x.p0}].concat(x.g.pesaje.map(p=>({f:p.f,v:+p.prom,cab:+p.cab})).sort((a,b)=>a.f<b.f?-1:1));
    for(let i=1;i<pes.length;i++){const a=pes[i-1],b=pes[i];if(!(b.v>0)||b.f<=a.f)continue;
      const al=x.g.alimento.filter(z=>z.f>=a.f&&z.f<b.f);if(!al.length)continue;
      const kg={},costo={};let tot=0;
      for(const z of al){const r=z.racion||x.racion||'_';const k=+z.kg||0;kg[r]=(kg[r]||0)+k;tot+=k;
        const ck=+z.costoKg||(S.raciones[r]?+S.raciones[r].costoKg||0:x.costoKgR||0);costo[r]=(costo[r]||0)+k*ck;}
      if(!(tot>0))continue;
      const dom=Object.entries(kg).sort((p,q)=>q[1]-p[1])[0];if(dom[1]/tot<0.7)continue;   // tramo con una ración clara
      const cab=b.cab||x.cab0-x.bajas||x.cab0,gan=(b.v-a.v)*cab;if(!(gan>0))continue;
      const o=R[dom[0]]||(R[dom[0]]={kg:0,costo:0,gan:0,tramos:0,lotes:new Set(),dias:0});
      o.kg+=tot;o.costo+=Object.values(costo).reduce((s,v)=>s+v,0);o.gan+=gan;o.tramos++;o.lotes.add(x.l.nombre);o.dias+=dias(a.f,b.f);}
  }
  const E=Object.entries(R).filter(([id,o])=>o.gan>0).map(([id,o])=>({id,n:S.raciones[id]?S.raciones[id].nombre:id==='_'?'Sin ración anotada':'Ración borrada',...o,conv:o.kg/o.gan,ck:o.costo/o.gan}));
  if(!E.length)return {html:'<b>¿Con qué ración te sale más barato engordar?</b><br>Necesito entregas de alimento con su ración y al menos dos pesajes del mismo lote para comparar raciones.'};
  E.sort((a,b)=>a.ck-b.ck);
  const rows=E.map((e,i)=>[`<b>${esc(e.n)}</b><br><small>${pl(e.tramos,'tramo','tramos')}, ${esc([...e.lotes].slice(0,3).join(', '))}</small>`,`${nf(e.conv,1)} a 1`,e.costo>0?pk(e.ck):'–',i===0&&E.length>1?pill('verde','La más barata'):'']);
  let txt='';
  if(E.length>1&&E[0].costo>0){const a=E[0],b=E[E.length-1],dif=b.ck-a.ck;
    const gan=C.act.reduce((s,x)=>s+x.gdpUse*x.cab*30,0);
    txt=`<p><b>${esc(a.n)}</b> te deja el kilo ganado a ${pk(a.ck)}; <b>${esc(b.n)}</b> a ${pk(b.ck)}. `+(dif>0&&gan>0?`Con lo que suben tus lotes en un mes (${nf(gan)} kg), la diferencia es de unos ${money(dif*gan)}.</p>`:'</p>')+
      `<p class="rs">Ojo: las raciones de inicio se usan con animales livianos, que convierten mejor; compara raciones de la misma etapa antes de decidir.</p>`;}
  return {html:`<b>¿Con qué ración te sale más barato engordar?</b><br>Entre pesaje y pesaje veo cuánto subió cada lote y qué comió. Cuento solo los tramos donde una ración fue al menos 70 % de lo que comieron.`+
    tablaW(['Ración','Conversión','Costo por kg ganado',''],rows)+txt};
}

/* ---------- 3. ¿Qué mejorar primero? ---------- */
function palancas(){
  const C=calc(),A=C.act.filter(x=>x.proy);if(!A.length)return {html:'<b>¿Qué mejorar primero?</b><br>No tienes lotes en engorde.'};
  const base=A.reduce((s,x)=>s+RumiPro.margenCon(x).m,0);
  const con=o=>A.reduce((s,x)=>s+RumiPro.margenCon(x,o).m,0)-base;
  const muerte=A.reduce((s,x)=>s+(RumiPro.margenCon(x).m-RumiPro.margenCon(x,{muertes:1}).m),0);   // una muerte menos en cada lote
  const P=[
    {t:'Ganancia diaria 5 % más alta',v:con({fg:1.05}),x:'Ración bien balanceada, comederos limpios, agua fresca, sombra y animales sanos.'},
    {t:'Alimento 5 % más barato',v:con({fa:0.95}),x:'Compra los granos en cosecha, cambia un ingrediente caro por uno equivalente y evita el desperdicio.'},
    {t:'Vender 2 % más caro',v:con({fp:1.02}),x:'Pide precio a varios compradores, vende en lote parejo y con buen acabado.'},
    {t:'Una muerte menos en cada lote',v:muerte,x:'Recepción con agua y heno, vacunas al llegar y separar rápido a los decaídos.'}].filter(p=>isFinite(p.v)).sort((a,b)=>b.v-a.v);
  return {html:`<b>¿Qué mejorar primero?</b><br>Cuánto dinero te dejaría cada mejora en tus ${pl(A.length,'lote','lotes')} en engorde, hasta que lleguen a su meta:`+
    `<ol class="rp-fugas">${P.map(p=>`<li><b>${p.t}: ${money(p.v)}</b><br>${p.x}</li>`).join('')}</ol>`+
    nota(`Calculado con tus lotes, tu precio de venta (${pk(pvKg())}) y el costo de tu ración. La que más vale va primero.`)};
}

/* ---------- 4. ¿Vendo ahora o espero, con el mercado? ---------- */
function curvaCon(x,tend,dMax=90){
  const C=calc(),pv=pvKg(),g0=Math.max(0.2,x.gdpUse||0.2),cons0=x.consumo||x.pesoHoy*0.026,cKg=x.costoKgR||0,gen=C.genDia||0;
  const base=x.costoTot-x.ingresos,vivos=x.cab;let w=x.pesoHoy,acum=0;const P=[];
  for(let d=0;d<=dMax;d++){
    if(d>0){const g=g0*Math.max(0.45,1-0.0028*d);const cons=cons0*(w/x.pesoHoy);acum+=(cons*cKg+gen)*vivos;w+=g;}
    const f=Math.max(0.85,Math.min(1.15,1+tend*d/28));   // la tendencia sigue, con tope de ±15 %
    P.push({d,w,m:w*(1-des())*pv*f*vivos-(base+acum)});
  }
  let best=P[0];for(const p of P)if(p.m>best.m)best=p;return {P,best,hoy:P[0]};
}
function venderMercado(){
  const C=calc(),L=C.act.filter(x=>x.listo||(x.diasMeta!=null&&x.diasMeta<=30));
  if(!C.act.length)return {html:'<b>¿Vendo ahora o espero, con el mercado?</b><br>No tienes lotes en engorde.'};
  const ref=window.Mercado&&Mercado.referencia();let c4=ref?Mercado.cambio(ref.s,28):null;
  if(!L.length)return {html:`<b>¿Vendo ahora o espero, con el mercado?</b><br>Ningún lote está listo ni a menos de 30 días de su meta. Te aviso cuando alguno se acerque.`};
  if(c4==null)return {html:`<b>¿Vendo ahora o espero, con el mercado?</b><br>Todavía no tengo cómo va el precio del gordo en tu zona. Abre Precios del mercado con internet, o anota ahí los precios que ves, y te digo si conviene esperar.`,btns:[['Precios del mercado',{t:'go',go:'#mercado'}]]};
  const tend=c4*0.5;
  const rows=[],ideas=[];
  for(const x of L){const fijo=curvaCon(x,0),mer=curvaCon(x,tend);
    const cuando=d=>d===0?'ya':`en ${pl(d,'día','días')}`;
    const celda=c=>`Vender ${cuando(c.best.d)}<br><b style="color:var(--${c.best.m>=0?'verde':'rojo'})">${money(c.best.m)}</b>`;
    rows.push([`<b>${esc(x.l.nombre)}</b>`,celda(fijo),celda(mer)]);
    if(mer.best.d<fijo.best.d)ideas.push(`${esc(x.l.nombre)}: con el precio bajando, conviene adelantar la venta ${pl(fijo.best.d-mer.best.d,'día','días')}.`);
    else if(mer.best.d>fijo.best.d)ideas.push(`${esc(x.l.nombre)}: con el precio subiendo, puedes esperar ${pl(mer.best.d-fijo.best.d,'día','días')} más si siguen ganando bien.`);}
  return {html:`<b>¿Vendo ahora o espero, con el mercado?</b><br>El precio del gordo ${c4>=0?'subió':'bajó'} ${nf(Math.abs(c4)*100,1)} % en 4 semanas (${esc(ref.fuente)}). Supongo que sigue a la mitad de ese ritmo, con un tope de 15 %.`+
    tablaW(['Lote','Con tu precio de hoy','Con la tendencia del mercado'],rows)+
    (ideas.length?`<ul class="rp-lista">${ideas.map(t=>`<li>${t}</li>`).join('')}</ul>`:'<p>La tendencia del mercado no cambia el mejor día de venta: sigue el plan.</p>')+
    nota('El mercado puede cambiar de dirección: úsalo como guía y pide precio a varios compradores.'),btns:[['Precios del mercado',{t:'go',go:'#mercado'}]]};
}

/* ---------- menú de Rumi y página de análisis ---------- */
const NUEVOS=[{l:'¿Estoy mejorando lote a lote?',fn:progreso},{l:'¿Con qué ración me sale más barato engordar?',fn:porRacion},{l:'¿Qué mejorar primero?',fn:palancas},{l:'¿Vendo ahora o espero, con el mercado?',fn:venderMercado}];
const A=RumiMenu.AREAS.find(a=>a.id==='pro');
if(A){const o=A.ops;A.ops=()=>{const L=o();const i=L.findIndex(x=>x&&x.l&&/^Comparar mis lotes/.test(x.l));L.splice(i>=0?i+1:L.length,0,...NUEVOS);return L;};}
if(PAGES.analisis){const _pag=PAGES.analisis;
  PAGES.analisis=function(){let h=_pag.apply(this,arguments);if(!calc().act.length)return h;
    const card=(k,t,r)=>{const bt=(r.btns||[]).slice(0,1).map(([l,a])=>{const i='p'+(++RUMI.n);RUMI.acts[i]=a;return `<button type="button" class="btn sm" data-act="rumiDo" data-id="${i}">${l}</button>`;}).join('');
      return `<section class="gcard rp-card" id="rp_${k}"><h3>${t}</h3><div class="rmsg-like">${r.html.replace(/^<b>[^<]*<\/b>(<br>)?/,'')}</div>${bt?`<div class="acts">${bt}</div>`:''}</section>`;};
    const cards=card('palancas','¿Qué mejorar primero?',palancas())+card('vendermer','¿Vendo ahora o espero, con el mercado?',venderMercado())+
      card('progreso','¿Estoy mejorando lote a lote?',progreso())+card('raciones2','¿Con qué ración te sale más barato engordar?',porRacion());
    h=h.replace('</nav>','<a href="#analisis/palancas" data-rp="palancas">Qué mejorar</a><a href="#analisis/vendermer" data-rp="vendermer">Vender con el mercado</a><a href="#analisis/progreso" data-rp="progreso">¿Mejoro?</a><a href="#analisis/raciones2" data-rp="raciones2">Raciones</a></nav>');
    const ancla='<section class="gcard rp-card" id="rp_comparar">';
    return h.includes(ancla)?h.replace(ancla,cards+ancla):h.replace('</main>',cards+'</main>');};}
window.RumiAnalisis3={progreso,porRacion,palancas,venderMercado,curvaCon};
})();
