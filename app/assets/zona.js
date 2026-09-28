/* Rumentis: comparación anónima con otros engordes de la zona.
   - Es voluntaria: nada sale del teléfono hasta que el usuario toca Participar, y puede salir y borrar sus datos.
   - Lo que se manda (una vez por semana como máximo): país, cuadro de ~110 km (latitud y longitud sin decimales),
     moneda y los indicadores de los lotes de los últimos 12 meses: ganancia diaria, conversión, mortalidad, costo por
     kilo ganado, días de engorde y un tamaño aproximado. Con un número al azar del teléfono, no con el nombre.
   - El servidor (servidor/zona, Cloudflare Workers + D1) devuelve los cuartiles de las fincas del mismo cuadro, de los
     cuadros vecinos o del país, y solo si hay al menos 5 fincas distintas.
   - SERVIDOR queda vacío hasta que el dueño de la app publique el servidor; mientras tanto se compara con la referencia. */
(function(){
'use strict';
const SERVIDOR='';                       // p. ej. 'https://rumentis-zona.tu-cuenta.workers.dev'
const LSZ='rumentis-zona',CACHE_H=24,CADA_D=7;
const Z=()=>S.config.zona||{};
const guardarZ=o=>put('ajustes','finca',{...S.config,zona:{...Z(),...o}});
const url=()=>{const u=(Z().url||SERVIDOR||'').trim().replace(/\/+$/,'');return /^https:\/\/[^\s/]+|^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?/.test(u)?u:'';};
const nuevoId=()=>{const b=new Uint8Array(16);try{crypto.getRandomValues(b);}catch(e){for(let i=0;i<16;i++)b[i]=Math.floor(Math.random()*256);}return [...b].map(x=>x.toString(16).padStart(2,'0')).join('');};
const celda=()=>{try{const u=JSON.parse(localStorage.getItem('rumentis-ubic2')||'null');if(u&&isFinite(u.lat)&&isFinite(u.lon))return `${Math.floor(u.lat)}_${Math.floor(u.lon)}`;}catch(e){}return '';};
const monCod=()=>{const c=typeof monedaCod==='function'?monedaCod():'otra';return /^[A-Z]{3}$/.test(c)?c:'';};

/* ---------- tus indicadores (lotes en engorde y vendidos en 12 meses) ---------- */
function mios(){
  const C=calc(),H=C.H,L=C.act.concat(C.cer.filter(x=>x.l.fechaCierre&&dias(x.l.fechaCierre,H)<=365));
  if(!L.length)return null;
  const w=x=>x.activo?x.cab:x.cab0;
  const pon=f=>{let s=0,t=0;for(const x of L){const v=f(x);if(v!=null&&isFinite(v)){s+=v*w(x);t+=w(x);}}return t?s/t:null;};
  const gdp=pon(x=>x.activo?(x.gdp!=null?x.gdpUse:null):(x.gdpTot>0?x.gdpTot:null));
  if(gdp==null)return null;   // sin pesajes no hay nada que comparar
  const cer=L.filter(x=>!x.activo),cab=L.reduce((s,x)=>s+w(x),0);
  const costo=pon(x=>x.costoKgGan>0?x.costoKgGan:null);
  let costoUsd=null;try{const r=window.Mercado&&Mercado.aMia(1,'USD');if(costo!=null&&r)costoUsd=costo/r;}catch(e){}
  return {gdp,conv:pon(x=>x.conv||null),mort:pon(x=>x.mort*100),costo,costoUsd,
    dias:cer.length?cer.reduce((s,x)=>s+x.dec*x.cab0,0)/Math.max(1,cer.reduce((s,x)=>s+x.cab0,0)):null,
    tam:cab<50?1:cab<200?2:cab<1000?3:4,lotes:L.length};
}
function paquete(id=Z().id){
  const m=mios();if(!m||!S.config.pais)return null;
  const r=(v,d)=>v==null?null:Math.round(v*10**d)/10**d;
  return {v:1,id,pais:S.config.pais,celda:celda()||null,mes:hoy().slice(0,7),moneda:monCod()||null,
    m:{gdp:r(m.gdp,3),conv:r(m.conv,2),mort:r(m.mort,2),costo:r(m.costo,4),costo_usd:r(m.costoUsd,4),dias:r(m.dias,0),tam:m.tam}};
}

/* ---------- servidor ---------- */
async function pedir(ruta,op={}){
  const c=typeof AbortController!=='undefined'?new AbortController():null,t=setTimeout(()=>c&&c.abort(),12000);
  try{const r=await fetch(url()+ruta,{...op,cache:'no-store',signal:c?c.signal:undefined});const j=await r.json().catch(()=>({}));if(!r.ok||!j.ok)throw new Error(j.error||('http '+r.status));return j;}
  finally{clearTimeout(t);}
}
let cache=null;try{cache=JSON.parse(localStorage.getItem(LSZ)||'null');}catch(e){}
let ocupado=null,error='';
async function sincronizar(forzar){
  if(ocupado)return ocupado;
  const z=Z();if(!z.on||!z.id||!url())return null;
  ocupado=(async()=>{
    try{
      const p=paquete();
      if(p&&(forzar||!z.ult||dias(z.ult,hoy())>=CADA_D||z.ult.slice(0,7)!==hoy().slice(0,7))){
        // text/plain: sin pedido previo de permiso (CORS) desde la app
        await pedir('/v1/aporte',{method:'POST',headers:{'Content-Type':'text/plain'},body:JSON.stringify(p)});guardarZ({ult:hoy()});}
      if(forzar||!cache||Date.now()-cache.t>CACHE_H*36e5||cache.pais!==S.config.pais){
        const q=new URLSearchParams({pais:S.config.pais||''});const c=celda(),m=monCod();if(c)q.set('celda',c);if(m)q.set('moneda',m);
        const j=await pedir('/v1/zona?'+q.toString());cache={t:Date.now(),pais:S.config.pais,d:j};try{localStorage.setItem(LSZ,JSON.stringify(cache));}catch(e){}}
      error='';
    }catch(e){error='red';}
    finally{ocupado=null;const r=route();if(r.p==='analisis')render();}
    return cache;
  })();
  return ocupado;
}
async function salir(){
  const z=Z();let ok=true;
  if(z.id&&url()){try{await pedir('/v1/aporte?id='+z.id,{method:'DELETE'});}catch(e){ok=false;}}
  if(!ok)return false;
  cache=null;try{localStorage.removeItem(LSZ);}catch(e){}
  put('ajustes','finca',{...S.config,zona:{url:z.url||''}});return true;
}

/* ---------- comparación ---------- */
const MET=[
  {k:'gdp',t:'Ganancia diaria',alto:true,f:v=>`${nf(v,2)} kg`},
  {k:'conv',t:'Conversión',alto:false,f:v=>`${nf(v,1)} a 1`},
  {k:'mort',t:'Mortalidad',alto:false,f:v=>`${nf(v,1)} %`},
  {k:'costo',t:'Costo por kg ganado',alto:false,f:null},
  {k:'dias',t:'Días de engorde',alto:null,f:v=>nf(v)}];
function puesto(v,q,alto){
  if(v==null||!q)return null;const [a,b,c]=alto?[q.p75,q.p50,q.p25]:[q.p25,q.p50,q.p75],mejor=alto?(x,y)=>x>=y:(x,y)=>x<=y;
  return mejor(v,a)?{t:'Entre los mejores',c:'verde',s:3}:mejor(v,b)?{t:'Mejor que la mitad',c:'verde',s:2}:mejor(v,c)?{t:'Abajo de la mitad',c:'tierra',s:1}:{t:'Entre los más bajos',c:'rojo',s:0};
}
const NIVEL={celda:'fincas de tu zona (unos 100 km)',vecinas:'fincas de tu zona y las vecinas',pais:'fincas de tu país'};
function tablaZona(d,m){
  const out=[];let peor=null,mejor=null;
  for(const o of MET){const q=d.metr[o.k];if(!q)continue;let mv=m[o.k],fmt=o.f;
    if(o.k==='costo'){if(q.moneda&&q.moneda!==monCod()){mv=m.costoUsd;fmt=v=>`US$ ${nf(v,2)}`;}else fmt=v=>pk(v);}
    if(mv==null)continue;
    const p=o.alto==null?null:puesto(mv,q,o.alto);
    // escala: la mitad del medio de las fincas, tu valor y un margen
    const pad=Math.max((q.p75-q.p25)*0.6,Math.abs(q.p50)*0.05,1e-6),lo=Math.min(q.p25,mv)-pad,hi=Math.max(q.p75,mv)+pad,X=v=>((v-lo)/(hi-lo)*100).toFixed(1);
    out.push(`<div class="zn-m"><div class="zn-h"><span>${o.t}</span>${p?`<span class="pill p-${p.c}">${p.t}</span>`:''}</div>
      <div class="zn-t"><i style="left:${X(q.p25)}%;width:${(X(q.p75)-X(q.p25)).toFixed(1)}%"></i><em style="left:${X(q.p50)}%"></em><u style="left:${X(mv)}%"></u></div>
      <div class="zn-l"><span>Tú: <b>${fmt(mv)}</b></span><span>Zona: <b>${fmt(q.p50)}</b>, la mitad entre ${fmt(q.p25)} y ${fmt(q.p75)}</span></div></div>`);
    if(p){if(!peor||p.s<peor.p.s)peor={o,p};if(!mejor||p.s>mejor.p.s)mejor={o,p};}}
  if(!out.length)return '';
  let txt='';
  if(mejor&&mejor.p.s>=2)txt+=`<p>Tu punto fuerte: <b>${mejor.o.t.toLowerCase()}</b>.</p>`;
  if(peor&&peor.p.s<=1&&peor!==mejor)txt+=`<p>Donde más puedes mejorar: <b>${peor.o.t.toLowerCase()}</b>. Mira qué hacen distinto las fincas de arriba: ración, recepción, compras y días en el corral.</p>`;
  return `<div class="zn">${out.join('')}</div><div class="legend"><span><i style="background:var(--anil5);opacity:.35"></i>la mitad de las fincas</span><span><i style="background:var(--ink2);width:3px"></i>el medio</span><span><i style="background:var(--arete-e);border-radius:50%"></i>tú</span></div>`+txt;
}
function html(){
  const m=mios(),z=Z(),hay=!!url();
  const ref=()=>{try{return RumiMas.refHtml().html.replace(/^<b>[^<]*<\/b>/,'');}catch(e){return '';}};
  const adv=`<details class="zn-adv"><summary>Servidor de la comparación</summary><div class="zn-adv-b"><p class="rs">Solo para quien administra la app: la dirección del servidor de Rumentis (servidor/zona).</p>
    <div class="unit"><input class="in" id="znUrl" value="${esc(z.url||'')}" placeholder="${esc(SERVIDOR||'https://…workers.dev')}" autocomplete="off" inputmode="url"></div><button type="button" class="btn sm" data-act="zonaUrl">Guardar</button></div></details>`;
  if(!hay)return {html:`<b>Tú contra tu zona</b><br>Pronto podrás compararte, sin dar tu nombre, con otros engordes de tu zona: ganancia, conversión, mortalidad y costo por kilo. Mientras tanto te comparo con la referencia.${ref()}${adv}`};
  if(!z.on)return {html:`<b>Tú contra tu zona</b><br>Compara tu ganancia diaria, conversión, mortalidad y costo por kilo con otros engordes de tu zona, sin que nadie sepa quién eres. Solo se ve el grupo cuando hay al menos 5 fincas.`+
    `<div class="acts"><button type="button" class="btn pri" data-act="f" data-f="zonaUnirse">Ver qué se comparte y participar</button></div>${ref()}${adv}`};
  const d=cache&&cache.d;
  const est=ocupado?'<p class="hint">Buscando los datos de tu zona…</p>':error&&!d?'<p class="prev warn">No pude conectarme. Lo intento otra vez cuando tengas internet.</p>':'';
  if(!m)return {html:`<b>Tú contra tu zona</b><br>Necesito al menos un lote con pesajes para comparar.${est}`};
  if(!d)return {html:`<b>Tú contra tu zona</b>${est||'<p class="hint">Todavía no tengo los datos de tu zona.</p>'}`};
  if(!d.nivel)return {html:`<b>Tú contra tu zona</b><br>Todavía somos pocos: ${d.fincas?`hay ${pl(d.fincas,'finca','fincas')} de tu país`:'aún no hay fincas de tu país'} y necesito al menos ${d.minimo} para comparar sin que nadie pueda saber de quién es cada dato. Invita a otros ganaderos a usar Rumentis. Mientras tanto, la referencia:${ref()}`+
    `<div class="acts"><button type="button" class="btn sm" data-act="f" data-f="zonaUnirse">Mi participación</button></div>`};
  return {html:`<b>Tú contra tu zona</b><br>Tus lotes de los últimos 12 meses contra ${pl(d.fincas,'finca','fincas')}: ${NIVEL[d.nivel]||''}.`+tablaZona(d,m)+
    `<p class="rs">Datos anónimos, actualizados el ${ffc(new Date(cache.t).toISOString().slice(0,10))}.</p>${est}`+
    `<div class="acts"><button type="button" class="btn sm" data-act="f" data-f="zonaUnirse">Mi participación</button></div>`};
}

/* ---------- consentimiento ---------- */
FORMS.zonaUnirse=()=>{
  const z=Z(),p=paquete(z.id||''),m=p&&p.m;
  const fila=(t,v)=>`<li><b>${t}:</b> ${v}</li>`;
  const lista=p?`<ul class="rp-lista">${fila('País',esc(p.pais))}${fila('Zona',p.celda?'un cuadro de unos 110 km, sin tu ubicación exacta':'solo el país (no tengo tu ubicación)')}${fila('Moneda',esc(p.moneda||'–'))}
    ${m.gdp!=null?fila('Ganancia diaria',`${nf(m.gdp,2)} kg`):''}${m.conv!=null?fila('Conversión',`${nf(m.conv,1)} a 1`):''}${m.mort!=null?fila('Mortalidad',`${nf(m.mort,1)} %`):''}
    ${m.costo!=null?fila('Costo por kg ganado',pk(m.costo)):''}${m.dias!=null?fila('Días de engorde',nf(m.dias)):''}${fila('Tamaño',['','menos de 50 cabezas','50 a 199 cabezas','200 a 999 cabezas','1,000 cabezas o más'][m.tam])}${fila('Quién',"un número al azar de este teléfono, no tu nombre")}</ul>`:
    `<p class="prev warn">${S.config.pais?'Necesito al menos un lote con pesajes para participar.':'Elige tu país en Configuración para participar.'}</p>`;
  openSheet(shHead('Compararte con tu zona')+`<div class="sh-body">
    <p style="margin-top:0">Esto es lo que se comparte, una vez por semana como máximo:</p>${lista}
    <p><b>No se comparte:</b> tu nombre ni el de la finca, tu ubicación exacta, tus lotes, animales, proveedores, compradores ni cuánto dinero ganas o debes.</p>
    <p class="hint">El grupo solo se muestra cuando hay al menos 5 fincas, y solo sus rangos: nadie puede ver los datos de una finca. Puedes salir cuando quieras y se borra todo lo que mandaste.</p>
    <p class="err"></p></div><div class="sh-foot" style="gap:8px">${z.on?`<button type="button" class="btn danger" data-act="zonaSalir" style="flex:1">Salir y borrar mis datos</button><button type="button" class="btn" data-act="cerrar" style="flex:1">Seguir participando</button>`:
    `<button type="button" class="btn" data-act="cerrar" style="flex:1">Ahora no</button><button type="button" class="btn pri" data-act="zonaSi" style="flex:1"${p?'':' disabled'}>Participar</button>`}</div>`);
};
Object.assign(ACTS,{
  zonaSi:async el=>{el.disabled=true;guardarZ({on:true,id:Z().id||nuevoId(),ult:''});closeSheet();toast('Gracias. Busco los datos de tu zona…');await sincronizar(true);
    toast(error?'Sin conexión: lo intento cuando tengas internet':'Listo: ya te comparo con tu zona');},
  zonaSalir:async el=>{el.disabled=true;const ok=await salir();el.disabled=false;
    if(ok){closeSheet();toast('Saliste de la comparación y se borraron tus datos');}else{const e=document.querySelector('#sheet .err');if(e)e.textContent=window.I18N?I18N.t('No pude borrar tus datos: revisa tu conexión e inténtalo otra vez.'):'';}},
  zonaUrl:()=>{const i=document.getElementById('znUrl');if(!i)return;const v=i.value.trim();guardarZ({url:v});cache=null;try{localStorage.removeItem(LSZ);}catch(e){}
    toast(v&&!url()?'Esa dirección no sirve: debe empezar con https://':'Servidor guardado');if(Z().on)sincronizar(true);}
});

/* ---------- en Rumi y en Análisis ---------- */
if(window.RumiMenu){const A=RumiMenu.AREAS.find(a=>a.id==='pro');
  if(A){const o=A.ops;A.ops=()=>{const L=o();const i=L.findIndex(x=>x&&x.l&&/^Precios del mercado/.test(x.l));L.splice(i>=0?i:L.length,0,{l:'¿Cómo voy contra los engordes de mi zona?',fn:html});return L;};}}
if(PAGES.analisis){const _pag=PAGES.analisis;
  PAGES.analisis=function(){let h=_pag.apply(this,arguments);if(!calc().act.length)return h;
    if(Z().on&&url()&&!ocupado&&(!cache||Date.now()-cache.t>CACHE_H*36e5))setTimeout(()=>sincronizar(false),0);
    const card=`<section class="gcard rp-card" id="rp_zona"><h3>Tú contra tu zona</h3><div class="rmsg-like">${html().html.replace(/^<b>[^<]*<\/b>(<br>)?/,'')}</div></section>`;
    h=h.replace('<a href="#analisis/mercado"','<a href="#analisis/zona" data-rp="zona">Tu zona</a><a href="#analisis/mercado"');
    return h.replace('<section class="gcard rp-card" id="rp_mercado">',card+'<section class="gcard rp-card" id="rp_mercado">');};}
// una vez por semana, al abrir la app
setTimeout(()=>{if(navigator.onLine!==false)sincronizar(false);},7000);
window.Zona={mios,paquete,sincronizar,salir,html,url,SERVIDOR};
})();
