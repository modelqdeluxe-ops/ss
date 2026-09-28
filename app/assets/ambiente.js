/* Ambiente de Rumentis: colores según la hora real del sol y la época del año, clima de tu zona
   (Open-Meteo, sin clave) con efectos de lluvia, nubes, sol, estrellas y relámpagos dibujados en canvas,
   fotos de novillos en bucle en cada encabezado y la bienvenida de la marca. */
(function(){
'use strict';
const FOTOS=Array.from({length:25},(_,i)=>`g${String(i+1).padStart(2,'0')}.webp`);
const r=document.documentElement;
const ls={get:k=>{try{return localStorage.getItem(k);}catch(e){return null;}},set:(k,v)=>{try{localStorage.setItem(k,v);}catch(e){}}};
const KEY='rumentis-clima2',KEYL='rumentis-ubic2';
let CLIMA=null;try{CLIMA=JSON.parse(ls.get(KEY)||'null');}catch(e){}

/* ---------- momento del día (con la salida y puesta del sol reales si las tenemos) ---------- */
const hm=t=>{const d=new Date(t);return d.getHours()+d.getMinutes()/60;};
function momento(d=new Date()){
  const h=d.getHours()+d.getMinutes()/60;
  let sa=5.6,pu=17.8; // Centroamérica: el sol sale cerca de las 5:30 y se pone cerca de las 17:50
  if(CLIMA&&CLIMA.sol&&CLIMA.sol[0]){sa=hm(CLIMA.sol[0]);pu=hm(CLIMA.sol[1]);}
  if(h<sa-1)return 'madrugada';if(h<sa+1)return 'amanecer';if(h<11)return 'manana';if(h<14.5)return 'mediodia';
  if(h<pu-1)return 'tarde';if(h<pu+.6)return 'atardecer';return 'noche';
}
// Centroamérica: época seca de diciembre a abril, lluvias de mayo a noviembre (canícula en julio y agosto)
function epoca(d=new Date()){const m=d.getMonth()+1;if(m===12||m<=4)return 'seca';if(m===7||m===8)return 'canicula';return 'lluvias';}
const SALUDO={madrugada:'Buena madrugada',amanecer:'Buenos días',manana:'Buenos días',mediodia:'Buenas tardes',tarde:'Buenas tardes',atardecer:'Buenas tardes',noche:'Buenas noches'};
const EPOCA_TXT={seca:'Época seca',canicula:'Canícula',lluvias:'Época de lluvias'};
function aplicarMomento(){r.dataset.momento=momento();r.dataset.epoca=epoca();}
aplicarMomento();setInterval(aplicarMomento,60000);

/* ---------- clima (Open-Meteo) ---------- */
const WMO=c=>c===0?'despejado':c===1?'casi despejado':c===2?'parcialmente nublado':c===3?'nublado':c<=48?'neblina':c<=55?'llovizna':c<=57?'llovizna helada':
  c<=63?'lluvia':c===65?'lluvia fuerte':c<=67?'lluvia helada':c<=77?'nieve':c<=81?'aguaceros':c===82?'aguaceros fuertes':c<=86?'nieve':'tormenta eléctrica';
function tipoClima(c=CLIMA){
  if(!c||c.code==null)return '';
  const moja=(c.lluvia||0)>=0.1;
  if(c.code>=95)return 'tormenta';
  if(moja||(c.code>=51&&c.code<=67)||(c.code>=80&&c.code<=82))return 'lluvia';
  if(c.code===45||c.code===48)return 'niebla';
  if(c.code===3||(c.nubes||0)>=85)return 'nublado';
  if(c.code===2||c.code===1||(c.nubes||0)>=30)return 'parcial';
  return 'despejado';
}
function iconoClima(){
  const m=momento(),noche=m==='noche'||m==='madrugada';
  if(!vigente())return m==='amanecer'||m==='atardecer'?'amanecer':noche?'luna-estrellas':'sol';
  const t=tipoClima(),dia=CLIMA.dia!=null?CLIMA.dia:!noche;
  return t==='tormenta'?'tormenta':t==='lluvia'?'lluvia':t==='niebla'?'niebla':t==='nublado'?'nube':t==='parcial'?(dia?'sol-nube':'luna-nube'):(dia?'sol':'luna-estrellas');
}
const vigente=()=>!!(CLIMA&&CLIMA.t!=null&&Date.now()-CLIMA.ts<3*3600e3);
function textoClima(){if(!vigente())return '';const t=tipoClima();const d=(CLIMA.lluvia||0)>=0.1&&CLIMA.code<51?'lloviendo':WMO(CLIMA.code);return `${Math.round(CLIMA.t)} °C, ${t==='lluvia'&&d==='nublado'?'lloviendo':d}`;}
const getJ=async(u,ms=10000)=>{const c=new AbortController();const t=setTimeout(()=>c.abort(),ms);try{const x=await fetch(u,{signal:c.signal,cache:'no-store'});if(!x.ok)throw new Error(x.status);return await x.json();}finally{clearTimeout(t);}};
// ubicación del teléfono: se pide fresca en cada apertura (máximo 15 minutos de antigüedad)
function ubicGPS(){return new Promise(ok=>{if(!navigator.geolocation)return ok(null);let hecho=false;const fin=v=>{if(!hecho){hecho=true;ok(v);}};
  setTimeout(()=>fin(null),15000);
  try{navigator.geolocation.getCurrentPosition(p=>fin({lat:+p.coords.latitude.toFixed(4),lon:+p.coords.longitude.toFixed(4),prec:Math.round(p.coords.accuracy||0)}),()=>fin(null),{maximumAge:15*60e3,timeout:14000,enableHighAccuracy:false});}catch(e){fin(null);}});}
const dist=(a,b)=>{const R=6371,dLa=(b.lat-a.lat)*Math.PI/180,dLo=(b.lon-a.lon)*Math.PI/180;const x=Math.sin(dLa/2)**2+Math.cos(a.lat*Math.PI/180)*Math.cos(b.lat*Math.PI/180)*Math.sin(dLo/2)**2;return 2*R*Math.asin(Math.sqrt(x));};
async function nombreDe(u){
  // nombre del lugar (pueblo o municipio) para que sepas de dónde es el clima
  try{const j=await getJ(`https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${u.lat}&longitude=${u.lon}&localityLanguage=es`,8000);
    return j.locality||j.city||(j.localityInfo&&j.localityInfo.administrative&&(j.localityInfo.administrative.slice(-1)[0]||{}).name)||j.principalSubdivision||'';}catch(e){return '';}
}
async function ubicacion(){
  let prev=null;try{prev=JSON.parse(ls.get(KEYL)||'null');}catch(e){}
  const nombre=(S.config.ubicacion||'').trim();
  const g=await ubicGPS();
  if(g){
    if(prev&&prev.origen==='gps'&&prev.n&&dist(prev,g)<3)return {...prev,...g,ts:Date.now()};
    const u={...g,n:await nombreDe(g),origen:'gps',ts:Date.now()};ls.set(KEYL,JSON.stringify(u));return u;
  }
  if(prev&&prev.origen==='gps'&&Date.now()-prev.ts<6*3600e3)return prev;
  if(nombre){
    if(prev&&prev.origen==='nombre'&&prev.q===nombre)return prev;
    // prueba cada parte ("aldea, municipio, depto"): la primera que el mapa conozca
    const CA=['HN','GT','SV','NI','CR','PA','MX','BZ'];
    for(const q of nombre.split(',').map(x=>x.trim()).filter(Boolean)){
      try{const j=await getJ(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(q)}&count=10&language=es&format=json`);
        const res=(j.results||[]).sort((a,b)=>(CA.includes(b.country_code)?1:0)-(CA.includes(a.country_code)?1:0))[0];
        if(res){const u={lat:res.latitude,lon:res.longitude,n:res.name,origen:'nombre',q:nombre,ts:Date.now()};ls.set(KEYL,JSON.stringify(u));return u;}}catch(e){}
    }
  }
  return prev;
}
let pidiendo=null;
function actualizarClima(forzar){
  if(pidiendo)return pidiendo;
  if(!forzar&&CLIMA&&Date.now()-CLIMA.ts<15*60e3){pintarClima();return Promise.resolve();}
  pidiendo=(async()=>{try{
    const u=await ubicacion();if(!u){pintarClima();return;}
    const j=await getJ(`https://api.open-meteo.com/v1/forecast?latitude=${u.lat}&longitude=${u.lon}`+
      `&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,showers,weather_code,cloud_cover,wind_speed_10m,wind_gusts_10m`+
      `&hourly=temperature_2m,relative_humidity_2m,precipitation_probability,precipitation,weather_code`+
      `&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,sunrise,sunset,uv_index_max`+
      `&timezone=auto&forecast_days=3&models=best_match`);
    const c=j.current||{},d=j.daily||{},h=j.hourly||{};
    const ahora=c.time||'',i0=Math.max(0,(h.time||[]).findIndex(t=>t>=ahora.slice(0,13)));
    const horas=(h.time||[]).slice(i0,i0+24).map((t,k)=>({h:t.slice(11,16),t:h.temperature_2m[i0+k],hr:h.relative_humidity_2m[i0+k],p:h.precipitation_probability[i0+k],mm:h.precipitation[i0+k],code:h.weather_code[i0+k]}));
    CLIMA={t:c.temperature_2m,sens:c.apparent_temperature,h:c.relative_humidity_2m,code:c.weather_code,dia:!!c.is_day,lluvia:(c.precipitation||0),nubes:c.cloud_cover,
      viento:c.wind_speed_10m,rachas:c.wind_gusts_10m,max:d.temperature_2m_max,min:d.temperature_2m_min,probLluvia:d.precipitation_probability_max,mmDia:d.precipitation_sum,uv:d.uv_index_max,
      sol:[(d.sunrise||[])[0],(d.sunset||[])[0]],horas,hora:ahora.slice(11,16),lugar:u.n||'',origen:u.origen,q:u.q||'',prec:u.prec||0,ts:Date.now()};
    ls.set(KEY,JSON.stringify(CLIMA));aplicarMomento();
  }catch(e){}finally{pidiendo=null;}
  pintarClima();})();
  return pidiendo;
}
function pintarClima(){
  r.dataset.clima=vigente()?tipoClima():'';
  if(vigente())FX.viento(Math.min(1,(CLIMA.viento||0)/40));
  const m=document.querySelector('body[data-pg="hoy"] .hd .top .meta');
  if(m&&!m.querySelector('.clima'))m.insertAdjacentHTML('beforeend',`<span class="clima"></span>`);
  document.querySelectorAll('.hd .clima').forEach(e=>{
    e.innerHTML=vigente()?`${icono(iconoClima(),'ci')}<b class="ct">${Math.round(CLIMA.t)}°</b><span><em>${esc(textoClima().replace(/^[^,]+, /,''))}</em>${CLIMA.lugar?`<i>${esc(CLIMA.lugar)}</i>`:''}</span>`:`${icono(iconoClima(),'ci')}<span><em>${EPOCA_TXT[epoca()]}</em><i>Toca para ver el clima</i></span>`;
    e.title=vigente()?'Clima de tu zona':'Da permiso de ubicación o pon tu lugar en Más, Configuración';
    e.setAttribute('role','button');e.dataset.act='climaVer';
  });
  FX.tipo(vigente()?tipoClima():'');
}
ACTS.climaVer=()=>{if(typeof abrirRumi==='function'&&window.RumiMenu&&RumiMenu.clima){abrirRumi();setTimeout(()=>RumiMenu.clima(),250);}else actualizarClima(true);};
window.Clima={actual:()=>vigente()?CLIMA:null,actualizar:actualizarClima,momento,epoca,texto:textoClima,icono:iconoClima,tipo:()=>vigente()?tipoClima():'',nombre:WMO};

/* ---------- efectos del cielo en el encabezado (canvas) ---------- */
const FX=(()=>{
  let VI=.15,cv=null,cx=null,W=0,H=0,dpr=1,tipo='',gotas=[],nubes=[],estrellas=[],salpic=[],rayo=0,sigRayo=0,ult=0,raf=0,t0=performance.now();
  const reducido=matchMedia('(prefers-reduced-motion: reduce)').matches;
  const rnd=(a,b)=>a+Math.random()*(b-a);
  function medir(){if(!cv)return;const b=cv.getBoundingClientRect();const d=Math.min(2,devicePixelRatio||1);if(cv.width===Math.round(b.width*d)&&cv.height===Math.round(b.height*d)&&W===b.width)return;dpr=d;W=b.width;H=b.height;cv.width=Math.round(W*dpr);cv.height=Math.round(H*dpr);cx.setTransform(dpr,0,0,dpr,0,0);sembrar();}
  function sembrar(){
    const llueve=tipo==='lluvia'||tipo==='tormenta',n=llueve?Math.round(W*H/(tipo==='tormenta'?750:1000)):0;
    gotas=Array.from({length:n},()=>nuevaGota(true));
    const nn=tipo==='nublado'||llueve?7:tipo==='parcial'?4:tipo==='niebla'?0:0;
    nubes=Array.from({length:nn},(_,i)=>nuevaNube(i,nn));
    estrellas=Array.from({length:Math.round(W*H/2600)},()=>({x:rnd(0,W),y:rnd(0,H*.8),r:rnd(.4,1.3),f:rnd(0,6.28),v:rnd(.6,1.8)}));
  }
  function nuevaGota(ini){const z=Math.random();return {x:rnd(-40,W+40),y:ini?rnd(-H,H):rnd(-60,-10),z,l:6+z*16,v:(420+z*520),a:.18+z*.42,w:.6+z*.9};}
  function nuevaNube(i,n){const oscura=tipo==='lluvia'||tipo==='tormenta';return {x:(i/n)*W*1.3-W*.15+rnd(-20,20),y:rnd(-30,H*.22),s:rnd(.9,1.6),v:rnd(4,11),o:oscura?rnd(.22,.38):rnd(.08,.18),osc:oscura,partes:Array.from({length:6},(_,k)=>({dx:(k-2.5)*rnd(18,26),dy:rnd(-10,8)-Math.abs(k-2.5)*-2,r:rnd(20,34)}))};}
  function nube(n){for(const p of n.partes){const x=n.x+p.dx*n.s,y=n.y+p.dy*n.s,rad=p.r*n.s*1.6;const g=cx.createRadialGradient(x,y,0,x,y,rad);
      const c=n.osc?'38,50,66':'250,252,255';g.addColorStop(0,`rgba(${c},${n.o})`);g.addColorStop(.55,`rgba(${c},${n.o*.55})`);g.addColorStop(1,`rgba(${c},0)`);cx.fillStyle=g;cx.beginPath();cx.arc(x,y,rad,0,6.283);cx.fill();}}
  function sol(t){const m=r.dataset.momento;if(tipo==='lluvia'||tipo==='tormenta'||tipo==='nublado'||tipo==='niebla')return;if(m==='noche'||m==='madrugada')return luna(t);
    const bajo=m==='amanecer'||m==='atardecer';const x=W*.94,y=bajo?H*.95:-H*.02,rad=bajo?44:32;
    const g=cx.createRadialGradient(x,y,0,x,y,rad*5);g.addColorStop(0,bajo?'rgba(255,190,120,.42)':'rgba(255,244,200,.38)');g.addColorStop(.25,bajo?'rgba(255,150,90,.16)':'rgba(255,230,150,.13)');g.addColorStop(1,'rgba(255,220,150,0)');
    cx.fillStyle=g;cx.beginPath();cx.arc(x,y,rad*5,0,6.283);cx.fill();
    cx.save();cx.translate(x,y);cx.rotate(t*.00004);for(let k=0;k<12;k++){cx.rotate(Math.PI/6);const lg=cx.createLinearGradient(0,0,rad*4.2,0);lg.addColorStop(0,'rgba(255,240,200,.09)');lg.addColorStop(1,'rgba(255,240,200,0)');cx.fillStyle=lg;cx.beginPath();cx.moveTo(0,-3);cx.lineTo(rad*4.2,-12);cx.lineTo(rad*4.2,12);cx.lineTo(0,3);cx.fill();}cx.restore();
    const d=cx.createRadialGradient(x,y,0,x,y,rad*.55);d.addColorStop(0,'rgba(255,255,245,.95)');d.addColorStop(1,'rgba(255,240,200,.0)');cx.fillStyle=d;cx.beginPath();cx.arc(x,y,rad*.55,0,6.283);cx.fill();}
  function luna(t){for(const e of estrellas){const a=.35+.45*Math.sin(t*.001*e.v+e.f);cx.fillStyle=`rgba(255,255,240,${a.toFixed(3)})`;cx.beginPath();cx.arc(e.x,e.y,e.r,0,6.283);cx.fill();}
    if(tipo==='nublado'||tipo==='lluvia'||tipo==='tormenta')return;const x=W*.56,y=H*.14,rad=15;
    const g=cx.createRadialGradient(x,y,0,x,y,rad*4);g.addColorStop(0,'rgba(230,236,255,.35)');g.addColorStop(1,'rgba(230,236,255,0)');cx.fillStyle=g;cx.beginPath();cx.arc(x,y,rad*4,0,6.283);cx.fill();
    cx.fillStyle='rgba(248,246,236,.95)';cx.beginPath();cx.arc(x,y,rad,0,6.283);cx.fill();cx.globalCompositeOperation='destination-out';cx.beginPath();cx.arc(x+8,y-5,rad*.95,0,6.283);cx.fill();cx.globalCompositeOperation='source-over';}
  function lluvia(dt){const vi=VI;const ang=.12+vi*.35;
    cx.lineCap='round';
    for(const g of gotas){g.y+=g.v*dt;g.x+=g.v*dt*ang;
      if(g.y>H*(0.78+g.z*.22)){if(g.z>.55&&Math.random()<.5)salpic.push({x:g.x,y:H*(0.78+g.z*.22),t:0,z:g.z});Object.assign(g,nuevaGota(false));continue;}
      const lg=cx.createLinearGradient(g.x,g.y,g.x-g.l*ang,g.y-g.l);lg.addColorStop(0,`rgba(215,228,245,${g.a})`);lg.addColorStop(1,'rgba(215,228,245,0)');
      cx.strokeStyle=lg;cx.lineWidth=g.w;cx.beginPath();cx.moveTo(g.x,g.y);cx.lineTo(g.x-g.l*ang,g.y-g.l);cx.stroke();}
    for(let i=salpic.length-1;i>=0;i--){const s=salpic[i];s.t+=dt;if(s.t>.28){salpic.splice(i,1);continue;}const k=s.t/.28;
      cx.strokeStyle=`rgba(220,232,248,${(.45*(1-k)*s.z).toFixed(3)})`;cx.lineWidth=.8;cx.beginPath();cx.ellipse(s.x,s.y,2+k*7*s.z,.8+k*2,0,0,6.283);cx.stroke();}}
  function niebla(t){for(let k=0;k<3;k++){const y=H*(.35+k*.2),off=((t*.006*(k+1))%(W*2))-W;const g=cx.createLinearGradient(0,y-30,0,y+30);g.addColorStop(0,'rgba(235,238,242,0)');g.addColorStop(.5,'rgba(235,238,242,.18)');g.addColorStop(1,'rgba(235,238,242,0)');cx.fillStyle=g;cx.fillRect(off,y-30,W*2,60);cx.fillRect(off-W*2,y-30,W*2,60);}}
  function relampago(t,dt){if(tipo!=='tormenta')return;if(!sigRayo)sigRayo=t+rnd(3000,9000);
    if(t>sigRayo){rayo=1;sigRayo=t+rnd(4000,11000);cv._camino=(()=>{let x=rnd(W*.2,W*.8),y=0;const p=[[x,y]];while(y<H*.75){y+=rnd(10,22);x+=rnd(-16,16);p.push([x,y]);}return p;})();}
    if(rayo>0){cx.fillStyle=`rgba(235,240,255,${(rayo*.35).toFixed(3)})`;cx.fillRect(0,0,W,H);
      if(rayo>.55&&cv._camino){cx.strokeStyle=`rgba(255,255,255,${rayo.toFixed(2)})`;cx.lineWidth=1.8;cx.shadowColor='rgba(190,210,255,.9)';cx.shadowBlur=12;cx.beginPath();cv._camino.forEach(([x,y],i)=>i?cx.lineTo(x,y):cx.moveTo(x,y));cx.stroke();cx.shadowBlur=0;}
      rayo=Math.max(0,rayo-dt*2.4);}}
  function cuadro(t){raf=requestAnimationFrame(cuadro);if(!cv||!cv.isConnected){cancelAnimationFrame(raf);raf=0;return;}if(document.hidden)return;
    if(t-ult<33)return;const dt=Math.min(.05,(t-ult)/1000);ult=t;
    cx.clearRect(0,0,W,H);sol(t);
    for(const n of nubes){n.x+=n.v*dt;if(n.x-150*n.s>W)n.x=-150*n.s;nube(n);}
    if(tipo==='niebla')niebla(t);
    if(tipo==='lluvia'||tipo==='tormenta')lluvia(dt);
    relampago(t,dt);}
  function montar(canvas){cv=canvas;cx=cv.getContext('2d');medir();if(reducido){ult=0;cuadro(performance.now());cancelAnimationFrame(raf);raf=0;return;}if(!raf){ult=0;raf=requestAnimationFrame(cuadro);}}
  addEventListener('resize',()=>{if(cv)medir();});
  return {montar,viento:v=>{VI=Math.max(.08,v);},tipo:t=>{if(t===tipo)return;tipo=t;if(cv)sembrar();}};
})();

/* ---------- fotos de novillos en bucle en cada encabezado ---------- */
const OFF={hoy:0,lotes:5,registrar:10,graficos:15,mas:20,lote:3,animal:8,formular:13,agenda:18,bodega:23,metodologia:7};
let idx=Math.floor(Math.random()*FOTOS.length),ultimaPg='';
const url=i=>`fondos/${FOTOS[((i%FOTOS.length)+FOTOS.length)%FOTOS.length]}`;
/* El encabezado se vuelve a dibujar con cada cambio de datos. Para que la foto no parpadee, la capa de fotos
   es siempre la misma: se mueve al encabezado nuevo y su acercamiento lento sigue donde iba. */
let FXN=null,t0=0,cambiando=0,pendiente=null;
const dec=u=>{const im=new Image();im.src=u;return (im.decode?im.decode():new Promise(r=>{im.onload=r;})).catch(()=>{});};
function capas(){const [a,b]=FXN.querySelectorAll('.ft');const on=a.classList.contains('on')&&!a.classList.contains('arriba')?a:b.classList.contains('on')?b:a;return {on,off:on===a?b:a};}
function montar(){
  const hd=document.querySelector('#app .hd');if(!hd||!FOTOS.length)return;
  if(hd.querySelector('.hd-fx'))return;
  const pg=document.body.dataset.pg||'hoy',nueva=!!FXN&&pg!==ultimaPg;
  if(!FXN){
    idx=(OFF[pg]||0)+Math.floor(Math.random()*3);ultimaPg=pg;
    FXN=document.createElement('div');FXN.className='hd-fx';FXN.setAttribute('aria-hidden','true');
    FXN.innerHTML=`<i class="ft on kb" style="background-image:url('${url(idx)}')"></i><i class="ft"></i><b class="tinte"></b><canvas class="fx-cv"></canvas>`;
    t0=performance.now();
  }
  hd.insertBefore(FXN,hd.firstChild);
  // al moverla, el navegador reinicia la animación: la retomamos en el mismo punto
  const el=performance.now()-t0;FXN.querySelectorAll('.ft.kb').forEach(f=>f.style.animationDelay=`-${Math.round(el)}ms`);
  FX.montar(FXN.querySelector('.fx-cv'));
  pintarClima();
  if(nueva){ultimaPg=pg;cambiar((OFF[pg]||0)+Math.floor(Math.random()*3));}
}
function cambiar(n){
  if(!FXN)return;if(cambiando){pendiente=n;return;}cambiando=1;idx=n;const u=url(idx);
  dec(u).then(()=>{
    const {on,off}=capas();
    off.style.backgroundImage=`url('${u}')`;off.style.animationDelay='0ms';off.classList.remove('kb','on','in');void off.offsetWidth;
    t0=performance.now();off.classList.add('arriba','in','on','kb');
    setTimeout(()=>{on.classList.remove('on','in','kb','arriba');off.classList.remove('arriba','in');cambiando=0;if(pendiente!=null){const p=pendiente;pendiente=null;cambiar(p);}},1700);
  });
}
function siguiente(){if(!FXN||document.hidden||!FXN.isConnected)return;cambiar(idx+1);}
setInterval(siguiente,8000);
new MutationObserver(()=>montar()).observe(document.getElementById('app'),{childList:true});
montar();
document.addEventListener('visibilitychange',()=>{if(!document.hidden){aplicarMomento();actualizarClima();}});
setInterval(()=>{if(!document.hidden)actualizarClima();},15*60e3);

/* ---------- bienvenida (la pantalla ya está en el HTML desde el primer instante) ---------- */
const primeraVez=!ls.get('rumentis-bienvenida');
function bienvenida(){
  const w=document.getElementById('splash');if(!w)return;
  const finca=(S.config.finca&&S.config.finca!=='Mi engorde')?S.config.finca:'';
  if(finca)ls.set('rumentis-finca',finca);
  const h=w.querySelector('.sp-hola');if(h)h.innerHTML=`${SALUDO[momento()]}${finca?`,<br><b>${esc(finca)}</b>`:''}`;
  const cl=w.querySelector('.sp-clima');
  if(cl)cl.innerHTML=`${icono(iconoClima(),'ci')}<span>${vigente()?`${esc(textoClima())}${CLIMA.lugar?` · ${esc(CLIMA.lugar)}`:''}`:EPOCA_TXT[epoca()]}</span>`;
  const dur=primeraVez?6200:5200,t0=window.SPLASH_T0||performance.now();
  const cerrar=()=>{if(w.classList.contains('fin'))return;w.classList.add('fin');setTimeout(()=>{w.remove();if(primeraVez)saludoRumi();},800);};
  w.addEventListener('click',()=>{if(performance.now()-t0>1500)cerrar();});
  setTimeout(cerrar,Math.max(600,dur-(performance.now()-t0)));
}
function saludoRumi(){
  ls.set('rumentis-bienvenida','1');
  const w=document.createElement('div');w.id='hola-rumi';
  w.innerHTML=`<div class="hr-c"><div class="hr-rumi"><img src="fondos/rumi.webp" alt="Rumi"></div>
   <p class="hr-sm">Tu ayudante de engorde</p><h2 class="hr-t">¡Hola! Soy Rumi</h2>
   <p class="hr-x">Te ayudo con tus lotes, la comida, la salud del ganado y tus ganancias. Te enseño la app en pocos pasos.</p>
   <button type="button" class="btn pri full hr-si">Enséñame la app</button><button type="button" class="btn full hr-no">Ahora no</button></div>`;
  document.body.appendChild(w);requestAnimationFrame(()=>requestAnimationFrame(()=>w.classList.add('on')));
  const listo=()=>document.dispatchEvent(new Event('rumentis-listo'));
  const fin=t=>{w.classList.remove('on');setTimeout(()=>{w.remove();if(t){document.addEventListener('tourfin',listo,{once:true});iniciarTour();}else listo();},400);};
  w.querySelector('.hr-si').onclick=()=>fin(true);w.querySelector('.hr-no').onclick=()=>fin(false);
}
window.Bienvenida={rumi:saludoRumi};
const conSplash=!!document.getElementById('splash');
if(conSplash)bienvenida();
// la primera vez el permiso de ubicación espera a que termine el saludo y el recorrido
if(conSplash&&primeraVez){pintarClima();document.addEventListener('rumentis-listo',()=>setTimeout(()=>actualizarClima(true),700),{once:true});}
else setTimeout(()=>actualizarClima(),900);
})();
