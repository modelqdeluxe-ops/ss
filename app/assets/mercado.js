/* Rumentis: precios del mercado y cuándo vender o comprar.
   - Los precios los junta dos veces al día una GitHub Action (scripts/mercado/actualizar.js) y los publica en la
     Release "mercado" del repositorio; la app los lee de la API de GitHub (sin clave) y los guarda en el teléfono.
     Fuentes: USDA AMS (novillo gordo, promedio ponderado semanal), Cepea/Esalq-USP (boi gordo, bezerro y milho),
     Banco Central do Brasil (PTAX) y open.er-api.com (tipos de cambio).
   - Estados Unidos y Brasil ven su mercado; los demás países ven esas referencias en su moneda y por su unidad,
     y anotan los precios que ven en su zona (subasta, comprador, vecino) para que Rumi siga la tendencia.
   - Señales: tu precio de venta contra el mercado, precio que sube o baja con lotes listos, máximo o mínimo del año,
     reposición cara o barata (relación boi/bezerro o flaco contra gordo) y maíz que sube o baja.
     Entran en las señales de Rumi, en los avisos al teléfono y en la página #mercado. */
(function(){
'use strict';
const URL_M='https://api.github.com/repos/modelqdeluxe-ops/ss/releases/tags/mercado';
const LS='rumentis-mercado',HORAS=6;
const US_CWT=100/FK;            // kilos en un cwt (100 lb)
const ARROBA_PIE=30;            // la arroba del boi gordo (15 kg de canal) equivale a 30 kg en pie (rendimiento de 50 %)
const BOI_ARROBAS=18;           // un boi gordo de unas 18 arrobas (540 kg en pie), para la relación de troca
let M=null,estado='',cargando=null;

/* ---------- datos ---------- */
function valido(d){
  if(!d||d.version!==1||!d.series||typeof d.series!=='object')return null;
  for(const k of Object.keys(d.series)){const s=d.series[k];
    s.serie=(Array.isArray(s.serie)?s.serie:[]).filter(p=>Array.isArray(p)&&/^\d{4}-\d{2}-\d{2}$/.test(p[0])&&isFinite(+p[1])&&+p[1]>0).map(p=>[p[0],+p[1]]).sort((a,b)=>a[0]<b[0]?-1:a[0]>b[0]?1:0);
    if(!s.serie.length)delete d.series[k];}
  return d;
}
function leer(){try{const o=JSON.parse(localStorage.getItem(LS)||'null');if(o&&o.d&&valido(o.d))M=o;}catch(e){M=null;}}
function extraer(body){const m=/<!-- RUMENTIS_MERCADO\s*\n([\s\S]*?)\nRUMENTIS_MERCADO -->/.exec(body||'');if(!m)return null;try{return valido(JSON.parse(m[1]));}catch(e){return null;}}
function actualizar(forzar){
  if(cargando)return cargando;
  if(!forzar&&M&&Date.now()-M.t<HORAS*36e5)return Promise.resolve(M);
  const ctl=typeof AbortController!=='undefined'?new AbortController():null,to=setTimeout(()=>ctl&&ctl.abort(),15000);
  estado='cargando';
  cargando=fetch(URL_M,{headers:{Accept:'application/vnd.github+json'},cache:'no-store',signal:ctl?ctl.signal:undefined})
    .then(r=>{if(!r.ok)throw new Error('http '+r.status);return r.json();})
    .then(j=>{const d=extraer(j&&j.body);if(!d)throw new Error('formato');M={t:Date.now(),d};estado='';try{localStorage.setItem(LS,JSON.stringify(M));}catch(e){}return M;})
    .catch(e=>{estado='error';if(M)M.t=Math.max(M.t,Date.now()-(HORAS-1)*36e5);return M;})   // sin internet: se reintenta en una hora
    .finally(()=>{clearTimeout(to);cargando=null;const r=route();if(r.p==='mercado')render();});
  return cargando;
}
const ser=k=>M&&M.d.series[k]?M.d.series[k].serie:[];
const info=k=>M&&M.d.series[k]||null;
const ult=k=>{const s=ser(k);return s.length?s[s.length-1]:null;};
// valor en una fecha (el último dato hasta esa fecha)
function en(s,f){let v=null;for(const p of s){if(p[0]<=f)v=p;else break;}return v;}
function cambio(s,dd){if(s.length<2)return null;const u=s[s.length-1],a=en(s,addDias(u[0],-dd));return a&&a[0]<u[0]?u[1]/a[1]-1:null;}
function rango(s,dd=365){if(!s.length)return null;const u=s[s.length-1],d0=addDias(u[0],-dd),P=s.filter(p=>p[0]>=d0);if(P.length<5||dias(P[0][0],u[0])<dd*0.5)return null;const V=P.map(p=>p[1]);
  const lo=Math.min(...V),hi=Math.max(...V);return {lo,hi,pos:hi>lo?(u[1]-lo)/(hi-lo):0.5,n:V.length};}

/* ---------- monedas y unidades ---------- */
const cod=()=>typeof monedaCod==='function'?monedaCod():'otra';
const tasa=c=>{if(c==='USD')return 1;const t=M&&M.d.cambio&&M.d.cambio.tasas;if(c==='BRL'){const p=ult('br_dolar_ptax');if(p)return p[1];}return t&&+t[c]>0?+t[c]:null;};
// de una moneda a la del usuario (null si no se puede)
function aMia(v,de){const c=cod();if(c===de)return v;const a=tasa(de),b=tasa(c);return a&&b?v/a*b:null;}
const region=()=>{const p=S.config.pais,c=cod();return p==='US'&&c==='USD'?'us':p==='BR'&&c==='BRL'?'br':'otro';};
// precio por kilo en pie, en la moneda de cada serie
const KG={us_novillo_gordo_pie:v=>v/US_CWT,us_ternero:v=>v/US_CWT,us_novillo_engorde:v=>v/US_CWT,br_boi_gordo:v=>v/ARROBA_PIE};
const MON={us_ternero:'USD',us_novillo_engorde:'USD',us_novillo_gordo_pie:'USD',us_novillo_gordo_canal:'USD',br_boi_gordo:'BRL',br_bezerro:'BRL',br_milho:'BRL',br_dolar_ptax:'BRL'};
const SIM={USD:'US$',BRL:'R$'};
const nat=(v,m,d=2)=>g5(`${SIM[m]||m} ${nf(v,d)}`);
const UNI={us_ternero:'por cwt (100 lb) en pie',us_novillo_engorde:'por cwt (100 lb) en pie',us_novillo_gordo_pie:'por cwt (100 lb) en pie',us_novillo_gordo_canal:'por cwt (100 lb) en canal',br_boi_gordo:'por arroba (15 kg de canal)',br_bezerro:'por cabeza',br_milho:'por saca de 60 kg',br_dolar_ptax:'por dólar'};
const NOM={us_ternero:'Ternero de engorde (500 a 550 lb)',us_novillo_engorde:'Novillo de engorde (750 a 800 lb)',us_novillo_gordo_pie:'Novillo gordo en pie',us_novillo_gordo_canal:'Novillo gordo en canal',br_boi_gordo:'Boi gordo',br_bezerro:'Bezerro (ternero)',br_milho:'Maíz',br_dolar_ptax:'Dólar en Brasil (PTAX)'};
const LUGAR={us_ternero:'Estados Unidos, subasta de Oklahoma City',us_novillo_engorde:'Estados Unidos, subasta de Oklahoma City',us_novillo_gordo_pie:'Estados Unidos, promedio nacional ponderado de la semana',us_novillo_gordo_canal:'Estados Unidos, promedio nacional ponderado de la semana',
  br_boi_gordo:'Brasil, São Paulo, a plazo',br_bezerro:'Brasil, Mato Grosso do Sul',br_milho:'Brasil, Campinas (SP)',br_dolar_ptax:'Banco Central do Brasil'};
const FUENTE={us_ternero:'USDA AMS y ERS',us_novillo_engorde:'USDA AMS y ERS',us_novillo_gordo_pie:'USDA AMS',us_novillo_gordo_canal:'USDA AMS',br_boi_gordo:'Cepea/Esalq-USP',br_bezerro:'Cepea/Esalq-USP',br_milho:'Cepea/Esalq-USP',br_dolar_ptax:'BCB'};
// serie en precio por kilo en pie y en la moneda del usuario
function serieMia(k){const f=KG[k];if(!f)return [];const m=MON[k];const r=aMia(1,m);if(r==null)return [];return ser(k).map(([d,v])=>[d,f(v)*r]);}

/* ---------- tus precios (los que ves en tu zona) ---------- */
const PROPIOS=()=>(S.config.precios||[]).filter(p=>p&&p.f&&+p.v>0).sort((a,b)=>a.f<b.f?-1:a.f>b.f?1:0);
const propios=t=>PROPIOS().filter(p=>p.tipo===t).map(p=>[p.f,+p.v]);
/* el precio del gordo que manda: el mercado de tu país, o lo último que anotaste (hasta 45 días) */
function referencia(){
  const r=region();
  if(r==='us'||r==='br'){const k=r==='us'?'us_novillo_gordo_pie':'br_boi_gordo',u=ult(k);if(u)return {v:KG[k](u[1]),f:u[0],k,s:serieMia(k),fuente:FUENTE[k],txt:NOM[k]};}
  const p=propios('gordo'),u=p[p.length-1];
  if(u&&dias(u[0],hoy())<=45)return {v:u[1],f:u[0],k:'propio',s:p,fuente:'tus precios',txt:'Lo último que anotaste'};
  return null;
}

/* ---------- señales ---------- */
function senales(){
  const L=[],C=calc(),pv=+S.config.precioVentaKg||0,add=(p,t,s,b)=>L.push({p,t,s,b:b||['Ver precios',{t:'go',go:'#mercado'}],ir:'#mercado'});
  const ref=referencia();
  const listos=C.act.filter(x=>x.listo||(x.diasMeta!=null&&x.diasMeta<=21));
  if(ref){
    // tu precio configurado contra el mercado
    if(pv>0){const d=pv/ref.v-1;if(Math.abs(d)>0.6)add(0.55,'Tu precio de venta no se parece al del mercado',`Pusiste ${pk(pv)} y ${ref.k==='propio'?'lo último que anotaste':'el mercado'} está en ${pk(ref.v)}. Revisa en Configuración la moneda y la unidad de precio.`);
      else if(Math.abs(d)>=0.06)add(0.55,`Tu precio de venta está ${nf(Math.abs(d)*100,0)} % ${d<0?'abajo':'arriba'} del mercado`,
      `Pusiste ${pk(pv)} y ${ref.k==='propio'?'lo último que anotaste':'el mercado'} está en ${pk(ref.v)} (${ffc(ref.f)}). Los márgenes y cuándo vender se calculan con tu precio: revísalo.`);}
    // tendencia de 4 semanas con lotes listos
    const c4=cambio(ref.s,28),rg=rango(ref.s);
    if(c4!=null&&listos.length){
      const nom=listos.slice(0,2).map(x=>x.l.nombre).join(' y ');
      if(c4>=0.03)add(0.6,`El precio del gordo subió ${nf(c4*100,0)} % en 4 semanas`,`${nom} ${listos.length===1?'está listo o por salir':'están listos o por salir'}${rg&&rg.pos>=0.85?' y el precio está cerca del máximo del año':''}: buen momento para pedir precio a varios compradores.`);
      else if(c4<=-0.03)add(0.7,`El precio del gordo bajó ${nf(-c4*100,0)} % en 4 semanas`,`Si ${nom} ya ${listos.length===1?'está listo':'están listos'}, no esperes a que baje más: cada semana de más cuesta alimento y sube el costo de cada kilo ganado.`);
      else if(rg&&rg.pos>=0.9)add(0.5,'El precio del gordo está en lo más alto del año',`${nom} ${listos.length===1?'está listo o por salir':'están listos o por salir'}. Vender cerca del máximo protege tu margen.`);
    }
    // precio en lo más bajo del año con lotes que todavía ganan bien
    if(rg&&rg.pos<=0.1&&rg.n>=10){const esperan=C.act.filter(x=>!x.listo&&x.gdpUse>0.9);if(esperan.length&&!listos.length)add(0.35,'El precio del gordo está en lo más bajo del año','Tus lotes todavía no llegan a la meta: si siguen ganando bien, no hay apuro para vender ahora.');}
  }
  // reposición: Brasil con la relación de troca, los demás con lo que anotaste
  const r=region();
  if(r==='us'){const t=relUS();if(t&&t.prom){const d=t.v/t.prom-1;
    if(d<=-0.05)add(0.45,'Buen momento para reponer: el ternero está barato frente al gordo',`La libra del ternero vale ${nf(t.v,2)} veces la del gordo; el promedio de 2 años es ${nf(t.prom,2)}.`,['¿Cuánto pagar?',{t:'go',go:'#analisis/compra'}]);
    else if(d>=0.05)add(0.35,'Reponer está caro: el ternero vale mucho frente al gordo',`La libra del ternero vale ${nf(t.v,2)} veces la del gordo; el promedio de 2 años es ${nf(t.prom,2)}. Compra solo lo necesario y calcula tu precio máximo.`,['¿Cuánto pagar?',{t:'go',go:'#analisis/compra'}]);}}
  if(r==='br'){const t=troca();if(t&&t.prom){const d=t.v/t.prom-1;
    if(d>=0.05)add(0.45,`Buen momento para reponer: un boi gordo paga ${nf(t.v,2)} bezerros`,`El promedio de 2 años es ${nf(t.prom,2)}. Cuando el boi compra más bezerros, la reposición está barata.`);
    else if(d<=-0.05)add(0.35,`Reponer está caro: un boi gordo paga ${nf(t.v,2)} bezerros`,`El promedio de 2 años es ${nf(t.prom,2)}. Compra solo lo necesario y negocia el precio.`);}}
  else{const g=propios('gordo'),fl=propios('flaco'),ug=g[g.length-1],uf=fl[fl.length-1];
    if(ug&&uf&&dias(ug[0],hoy())<=45&&dias(uf[0],hoy())<=45){const q=uf[1]/ug[1];
      if(q>=1.25)add(0.4,`El ganado para engorde está caro: ${nf((q-1)*100,0)} % más por kilo que el gordo`,`Anotaste ${pk(uf[1])} el flaco y ${pk(ug[1])} el gordo. Con esa diferencia el engorde tiene que ganar mucho para dejar dinero: calcula el precio máximo antes de comprar.`,['¿Cuánto pagar?',{t:'go',go:'#analisis/compra'}]);
      else if(q<=1.05)add(0.4,'Buen momento para comprar ganado de engorde',`El flaco (${pk(uf[1])}) cuesta casi lo mismo por kilo que el gordo (${pk(ug[1])}): cada kilo que suban vale más.`,['¿Cuánto pagar?',{t:'go',go:'#analisis/compra'}]);}}
  // maíz (Brasil)
  if(r==='br'){const c=cambio(ser('br_milho'),28);if(c!=null){
    if(c>=0.08)add(0.45,`El maíz subió ${nf(c*100,0)} % en 4 semanas`,'Si tu bodega está baja, compra lo de un mes antes de que suba más y revisa el costo de tu ración.',['Ver la bodega',{t:'go',go:'#bodega'}]);
    else if(c<=-0.08)add(0.4,`El maíz bajó ${nf(-c*100,0)} % en 4 semanas`,'Buen momento para llenar la bodega si tienes dónde guardarlo seco.',['Ver la bodega',{t:'go',go:'#bodega'}]);}}
  return L.sort((a,b)=>b.p-a.p);
}
/* EE. UU.: cuántas veces vale la libra del ternero la del novillo gordo (con el dato del gordo más cercano, hasta 10 días antes) */
function relUS(){const t=ser('us_ternero'),g=ser('us_novillo_gordo_pie');if(!t.length||!g.length)return null;
  const pts=t.map(([f,v])=>{const p=en(g,f);return p&&dias(p[0],f)<=10?[f,v/p[1]]:null;}).filter(Boolean);if(!pts.length)return null;
  const u=pts[pts.length-1];return {v:u[1],f:u[0],pts,prom:pts.length>=12?pts.reduce((s,p)=>s+p[1],0)/pts.length:null};}
/* relação de troca: bezerros que paga un boi gordo de 18 arrobas */
function troca(){const b=ser('br_boi_gordo'),z=ser('br_bezerro');if(!b.length||!z.length)return null;
  const Z=new Map(z),pts=b.filter(p=>Z.has(p[0])).map(p=>[p[0],p[1]*BOI_ARROBAS/Z.get(p[0])]);if(!pts.length)return null;
  const u=pts[pts.length-1];return {v:u[1],f:u[0],pts,prom:pts.length>=60?pts.reduce((s,p)=>s+p[1],0)/pts.length:null};}

/* ---------- página ---------- */
// lo que compras: que baje es bueno
const COMPRA=new Set(['br_milho','br_bezerro','us_ternero','us_novillo_engorde']);
const pctS=v=>v==null?'–':`${v>0?'+':v<0?'−':''}${nf(Math.abs(v)*100,1)} %`;
const pill=(v,inv)=>v==null?'':`<span class="pill p-${Math.abs(v)<0.01?'tierra':(v>0)!==!!inv?'verde':'rojo'}">${pctS(v)}</span>`;
function barraRango(rg,fmt){if(!rg)return '';return `<div class="rp-ref"><div class="rp-ref-h"><span>Rango de 12 meses</span><b>${fmt(rg.lo)} – ${fmt(rg.hi)}</b></div><div class="rp-ref-t" style="background:linear-gradient(90deg,var(--rojo),var(--tierra),var(--verde))"><u style="left:${(Math.max(0,Math.min(1,rg.pos))*100).toFixed(1)}%"></u></div><small>${rg.pos>=0.85?'Cerca del máximo del año.':rg.pos<=0.15?'Cerca del mínimo del año.':'En la mitad del rango del año.'}</small></div>`;}
const doce=s=>{if(!s.length)return s;const d0=addDias(s[s.length-1][0],-365);return s.filter(p=>p[0]>=d0);};
function tarjeta(k,{mia=false}={}){
  const s=ser(k),i=info(k);if(!s.length||!i)return '';const u=s[s.length-1],m=MON[k];
  let big,sub,pts,fmt,base=s;
  if(mia&&KG[k]){const sm=serieMia(k);if(!sm.length)return '';base=sm;const um=sm[sm.length-1];big=pk(um[1]);sub=`${nat(u[1],m)} ${g5(UNI[k])}${cod()!==m?`, con el cambio de hoy`:''}`;pts=doce(sm).map(([f,v])=>[f,PKd(v)]);fmt=v=>pk(v);}
  else{big=`${nat(u[1],m)}`;const en=KG[k]&&!mia?aMia(KG[k](u[1]),m):null;sub=g5(UNI[k])+(en!=null?` · ${pk(en)} en pie`:'');pts=doce(s);fmt=v=>nat(v,m);}
  const c4=cambio(s,28),c52=cambio(s,364),rg=rango(base);
  const fr=/^us_/.test(k)?'semanal':'diario';
  return `<section class="gcard mk-card"><div class="mk-h"><div><h3>${NOM[k]}</h3><p class="rp-sub">${LUGAR[k]}</p></div><span class="mk-f">${ffc(u[0])}</span></div>
    <div class="mk-v"><b class="hf">${big}</b><span>${sub}</span></div>
    <div class="mk-c">${c4!=null?`<span>4 semanas ${pill(c4,COMPRA.has(k))}</span>`:''}${c52!=null?`<span>1 año ${pill(c52,COMPRA.has(k))}</span>`:''}</div>
    ${barraRango(rg,fmt)}
    ${lineChart({series:[{name:NOM[k],color:'var(--s1)',pts,dots:false}],h:150})}
    <p class="rs">Fuente: ${FUENTE[k]}, dato ${fr}.</p></section>`;
}
function margenesHtml(ref){
  const C=calc(),pv=+S.config.precioVentaKg||0;if(!ref||!pv||!C.act.length||!window.RumiPro)return '';
  const fp=ref.v/pv;if(Math.abs(fp-1)<0.005||Math.abs(fp-1)>0.6)return '';
  const R=C.act.filter(x=>x.proy).map(x=>{const a=RumiPro.margenCon(x).m,b=RumiPro.margenCon(x,{fp}).m;return {x,a,b};});if(!R.length)return '';
  const ta=R.reduce((s,o)=>s+o.a,0),tb=R.reduce((s,o)=>s+o.b,0);
  return `<section class="sec">${secH('Tus lotes a precio de mercado')}<div class="gcard"><p class="rs" style="margin-top:0">Margen al llegar a la meta con tu precio (${pk(pv)}) y con el del mercado (${pk(ref.v)}).</p>
    ${tabla(['Lote','Con tu precio','Con el mercado'],R.map(o=>[esc(o.x.l.nombre),money(o.a),`<b style="color:var(--${o.b<0?'rojo':'ink'})">${money(o.b)}</b>`]).concat(R.length>1?[['<b>Total</b>',`<b>${money(ta)}</b>`,`<b>${money(tb)}</b>`]]:[]))}
    <div class="acts"><button type="button" class="btn" data-act="mercadoUsar" data-v="${ref.v}">Usar ${pk(ref.v)} como mi precio de venta</button></div></div></section>`;
}
function propiosHtml(){
  const P=PROPIOS(),g=propios('gordo'),fl=propios('flaco');
  const graf=g.length+fl.length>=2?lineChart({series:[g.length?{name:'Gordo (venta)',color:'var(--s1)',pts:g.map(([f,v])=>[f,PKd(v)])}:null,fl.length?{name:'Flaco (compra)',color:'var(--s2)',pts:fl.map(([f,v])=>[f,PKd(v)])}:null].filter(Boolean),h:160}):'';
  const lista=P.slice(-8).reverse().map(p=>`<div class="row"><div class="tx"><b>${p.tipo==='flaco'?'Flaco':'Gordo'}: ${pk(+p.v)}</b><span>${ffc(p.f)}${p.lugar?', '+esc(p.lugar):''}</span></div><button type="button" class="del" data-act="mercadoBorrar" data-id="${esc(p.id)}" aria-label="Borrar este precio">${ico('papelera')}</button></div>`).join('');
  return `<section class="sec">${secH('Precios que viste en tu zona',P.length||'')}<div class="card pad" style="display:flex;flex-direction:column;gap:12px">
    <p class="hint" style="margin:0">${region()==='otro'?'No hay un precio público del ganado en tu país: anota lo que te ofrecen, lo que viste en la subasta o lo que pagó el vecino. Con eso sigo la tendencia y te aviso.':'Anota lo que te ofrecen en tu zona para compararlo con el mercado.'}</p>
    ${graf}${lista?`<div class="rows">${lista}</div>`:''}
    <button type="button" class="btn pri full" data-act="f" data-f="precioVisto">Anotar un precio que vi</button></div></section>`;
}
PAGES.mercado=()=>{
  if(!M&&!cargando&&estado!=='error')setTimeout(()=>actualizar(true),0);
  else if(M&&Date.now()-M.t>HORAS*36e5&&!cargando)setTimeout(()=>actualizar(false),0);
  const r=region(),ref=referencia();
  const hace=M?Math.round((Date.now()-M.t)/6e4):null;
  const viejo=M&&M.d.generado&&dias(M.d.generado.slice(0,10),hoy())>10;
  const est=cargando||(!M&&estado!=='error')?'<p class="hint" style="margin:0">Buscando los precios de hoy…</p>':
    !M?'<p class="prev warn" style="margin:0">No pude traer los precios. Revisa tu conexión a internet y toca Actualizar.</p>':
    `<p class="hint" style="margin:0">Revisado ${hace<2?'hace un momento':hace<60?`hace ${hace} minutos`:hace<48*60?`hace ${pl(Math.round(hace/60),'hora','horas')}`:`el ${ffc(new Date(M.t).toISOString().slice(0,10))}`}${estado==='error'?'. Sin conexión: muestro lo último que guardé.':'.'}</p>${viejo?'<p class="prev warn" style="margin:0">Los precios tienen más de 10 días: la fuente no se ha actualizado.</p>':''}`;
  const S0=M||PROPIOS().length?senales():[];
  const sen=S0.length?`<section class="sec">${secH('Lo que dice el mercado',S0.length)}<div class="gcard"><div class="rp-sen">${S0.map(s=>`<div class="rp-s ${s.p>=0.65?'r':s.p>=0.45?'a':'g'}"><b>${esc(s.t)}</b><span>${esc(s.s)}</span></div>`).join('')}</div></div></section>`:'';
  let mk='';
  if(M){
    if(r==='us'){const t=relUS();
      mk=`<section class="sec">${secH('Tu mercado','Estados Unidos')}${tarjeta('us_novillo_gordo_pie',{mia:true})}${tarjeta('us_ternero',{mia:true})}${tarjeta('us_novillo_engorde',{mia:true})}
      ${t?`<section class="gcard mk-card"><div class="mk-h"><div><h3>Ternero contra gordo</h3><p class="rp-sub">Cuántas veces vale la libra del ternero la del novillo gordo</p></div><span class="mk-f">${ffc(t.f)}</span></div>
        <div class="mk-v"><b class="hf">${nf(t.v,2)}</b><span>${t.prom?`promedio de 2 años: ${nf(t.prom,2)}. Más bajo es mejor para reponer.`:''}</span></div>${lineChart({series:[{name:'Relación',color:'var(--s1)',pts:doce(t.pts),dots:false}],h:140})}</section>`:''}
      ${tarjeta('us_novillo_gordo_canal')}</section>`;}
    else if(r==='br'){const t=troca();
      mk=`<section class="sec">${secH('Tu mercado','Brasil')}${tarjeta('br_boi_gordo')}${tarjeta('br_bezerro')}
      ${t?`<section class="gcard mk-card"><div class="mk-h"><div><h3>Relación boi gordo y bezerro</h3><p class="rp-sub">Bezerros que paga un boi gordo de ${BOI_ARROBAS} arrobas</p></div><span class="mk-f">${ffc(t.f)}</span></div>
        <div class="mk-v"><b class="hf">${nf(t.v,2)}</b><span>${t.prom?`promedio de 2 años: ${nf(t.prom,2)}. Más alto es mejor para reponer.`:''}</span></div>${lineChart({series:[{name:'Relación',color:'var(--s1)',pts:doce(t.pts),dots:false}],h:140})}</section>`:''}
      ${tarjeta('br_milho')}${tarjeta('br_dolar_ptax')}</section>`;}
    else{const c=cod();
      mk=`<section class="sec">${secH('Referencias internacionales',c!=='otra'?'en tu moneda':'')}
      <p class="hint">No son el precio de tu zona: sirven para ver si el mercado de la carne sube o baja. ${c==='otra'?'Elige tu moneda en Configuración para verlas en tu moneda.':''}</p>
      ${tarjeta('us_novillo_gordo_pie',{mia:c!=='otra'})}${tarjeta('br_boi_gordo',{mia:c!=='otra'})}</section>`;}
  }
  const fx=(()=>{if(!M||!M.d.cambio)return '';const c=cod(),t=tasa(c);if(c==='USD'||!t)return '';
    return `<section class="sec">${secH('Tipo de cambio',ffc(M.d.cambio.fecha||''))}<div class="card pad eco" style="padding-top:6px"><div><span class="sm">1 dólar</span><span class="n">${money2(t)}</span></div>${c!=='BRL'&&tasa('BRL')?`<div><span class="sm">1 real de Brasil</span><span class="n">${money2(t/tasa('BRL'))}</span></div>`:''}</div></section>`;})();
  return `${masHd('Precios del mercado','Cuándo vender y cuándo comprar, con el mercado y los precios de tu zona.')}
  <main class="bd"><section class="sec"><div class="card pad" style="display:flex;flex-direction:column;gap:10px">${est}<button type="button" class="btn" data-act="mercadoAct"${cargando?' disabled':''}>Actualizar</button></div></section>
   ${sen}${margenesHtml(ref)}${mk}${propiosHtml()}${fx}
   <p class="hint">Fuentes: USDA Agricultural Marketing Service (novillo gordo; ternero y novillo de engorde de la subasta de Oklahoma City, con la historia mensual de USDA ERS), Cepea/Esalq-USP (boi gordo, bezerro y milho), Banco Central do Brasil (PTAX) y ExchangeRate-API (tipos de cambio). El boi gordo se pasa a kilo en pie con la arroba de 30 kg (rendimiento de 50 %).</p></main>`;
};

/* ---------- formulario: precio que vi ---------- */
FORMS.precioVisto=()=>{
  openSheet(shHead('Anotar un precio que vi')+formWrap('precioVisto',
    q('¿Qué precio es?',opts('tipo',[{v:'gordo',t:'Ganado gordo',s:'lo que pagan por el terminado'},{v:'flaco',t:'Ganado para engorde',s:'lo que cuesta el que compras'}],'gordo',{lo:true}))+
    q(`Precio ${PUpor()}`,inp('v','',{unit:PUs(),xl:true,req:true,ph:'0.00'}),'En pie.')+fecha()+
    q('Dónde',inp('lugar','',{mode:'text',ph:'Subasta, comprador, vecino… (opcional)'})),
    foot('Guardar precio')));
};
SAVE.precioVisto=f=>{
  const v=num(fv(f,'v')),tipo=fv(f,'tipo')||'gordo',fe=fv(f,'f')||hoy();
  if(!(v>0))return ferr(f,'Escribe el precio.');
  const kg=precioToKg(v),pv=+S.config.precioVentaKg||0;
  if(pv>0&&(kg>pv*4||kg<pv/4))return ferr(f,`Ese precio es muy distinto al tuyo (${pk(pv)}). Revisa la unidad: es ${PUN[PU()].por}.`);
  const L=(S.config.precios||[]).concat([{id:uid('p'),f:fe,tipo,v:Math.round(kg*10000)/10000,lugar:fv(f,'lugar').trim()}]).sort((a,b)=>a.f<b.f?-1:1).slice(-300);
  put('ajustes','finca',{...S.config,precios:L});closeSheet();toast('Precio guardado');
};
Object.assign(ACTS,{
  mercadoAct:()=>{actualizar(true);render();},
  mercadoUsar:el=>{const v=+el.dataset.v;if(!(v>0))return;confirmar('Cambiar tu precio de venta',`Tu precio de venta pasa de <b>${pk(+S.config.precioVentaKg||0)}</b> a <b>${pk(v)}</b>. Todos los márgenes se recalculan con el nuevo precio.`,'Cambiar',()=>{put('ajustes','finca',{...S.config,precioVentaKg:Math.round(v*10000)/10000});toast('Precio de venta actualizado');});},
  mercadoBorrar:el=>{put('ajustes','finca',{...S.config,precios:(S.config.precios||[]).filter(p=>p.id!==el.dataset.id)});toast('Precio borrado');}
});

/* resumen para Rumi y la página de análisis */
function resumenHtml(){
  const ref=referencia(),S0=senales();
  if(!ref&&!M)return {html:'<b>Precios del mercado</b><br>Todavía no tengo precios. Abre la página de precios con internet o anota los que ves en tu zona.',btns:[['Ver precios',{t:'go',go:'#mercado'}]]};
  const c4=ref?cambio(ref.s,28):null;
  return {html:`<b>Precios del mercado</b><br>${ref?`${ref.txt}: <b>${pk(ref.v)}</b> (${ffc(ref.f)}, ${ref.fuente})${c4!=null?`, ${c4>=0?'sube':'baja'} ${nf(Math.abs(c4)*100,1)} % en 4 semanas`:''}.`:'No hay un precio público del ganado en tu país: anota los que ves en tu zona y sigo la tendencia.'}`+
    (S0.length?`<div class="rp-sen">${S0.slice(0,3).map(s=>`<div class="rp-s ${s.p>=0.65?'r':s.p>=0.45?'a':'g'}"><b>${esc(s.t)}</b><span>${esc(s.s)}</span></div>`).join('')}</div>`:'<p class="rs">Nada urgente del mercado por ahora.</p>'),
    btns:[['Ver precios',{t:'go',go:'#mercado'}]]};
}
/* en Rumi (área de análisis) y en la página de análisis: mercado, huella de carbono y documentos */
if(window.RumiMenu){const A=RumiMenu.AREAS.find(a=>a.id==='pro');
  if(A){const o=A.ops;A.ops=()=>{const L=o();L.push({l:'Precios del mercado: ¿vendo o espero?',fn:resumenHtml},
    {l:'Huella de carbono de mi engorde',fn:()=>window.Huella?Huella.fincaHtml():{html:'No disponible.'}},
    {l:'Informe para el banco o certificado de un lote',fn:()=>({html:'<b>Documentos en PDF</b><br>Hago el informe productivo y financiero para el banco y el certificado de cada lote con código QR para el comprador o el matadero.',btns:[['Abrir documentos',{t:'go',go:'#documentos'}]]})});return L;};}}
if(PAGES.analisis){const _pag=PAGES.analisis;
  PAGES.analisis=function(){let h=_pag.apply(this,arguments);if(!calc().act.length)return h;
    const card=(k,t,r)=>{const bt=(r.btns||[]).slice(0,1).map(([l,a])=>{const i='p'+(++RUMI.n);RUMI.acts[i]=a;return `<button type="button" class="btn sm" data-act="rumiDo" data-id="${i}">${l}</button>`;}).join('');
      return `<section class="gcard rp-card" id="rp_${k}"><h3>${t}</h3><div class="rmsg-like">${r.html.replace(/^<b>[^<]*<\/b>(<br>)?/,'')}</div>${bt?`<div class="acts">${bt}</div>`:''}</section>`;};
    let extra=card('mercado','Precios del mercado',resumenHtml());
    if(window.Huella)extra+=card('huella','Huella de carbono',Huella.fincaHtml());
    h=h.replace('</nav>','<a href="#analisis/mercado" data-rp="mercado">Mercado</a>'+(window.Huella?'<a href="#analisis/huella" data-rp="huella">Huella de carbono</a>':'')+'</nav>');
    return h.includes('<p class="hint">Rumi usa solo tus registros')?h.replace('<p class="hint">Rumi usa solo tus registros',extra+'<p class="hint">Rumi usa solo tus registros'):h.replace('</main>',extra+'</main>');};}
leer();
// al abrir la app, si los precios tienen más de 6 horas y hay internet
setTimeout(()=>{if(navigator.onLine!==false)actualizar(false);},4000);
window.Mercado={actualizar,senales,referencia,troca,relUS,resumenHtml,serieMia,cambio,rango,datos:()=>M,region,aMia};
})();
