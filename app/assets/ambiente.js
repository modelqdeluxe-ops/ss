/* Ambiente de Rumentis: fotos de novillos en bucle, colores según la hora y la época del año,
   clima real de tu zona (Open-Meteo, sin clave) con efectos de lluvia, nubes, sol y estrellas,
   y la bienvenida animada de la marca. */
(function(){
'use strict';
const FOTOS=Array.from({length:25},(_,i)=>`g${String(i+1).padStart(2,'0')}.webp`);
const r=document.documentElement;

/* ---------- momento del día y época del año ---------- */
function momento(d=new Date()){
  const h=d.getHours()+d.getMinutes()/60;
  if(h<5)return 'madrugada';if(h<7)return 'amanecer';if(h<11)return 'manana';if(h<15)return 'mediodia';
  if(h<17.5)return 'tarde';if(h<19)return 'atardecer';return 'noche';
}
// Centroamérica: época seca de diciembre a abril, lluvias de mayo a noviembre (canícula en julio y agosto)
function epoca(d=new Date()){const m=d.getMonth()+1;if(m===12||m<=4)return 'seca';if(m===7||m===8)return 'canicula';return 'lluvias';}
const SALUDO={madrugada:'Buena madrugada',amanecer:'Buenos días',manana:'Buenos días',mediodia:'Buenas tardes',tarde:'Buenas tardes',atardecer:'Buenas tardes',noche:'Buenas noches'};
const EPOCA_TXT={seca:'época seca',canicula:'canícula',lluvias:'época de lluvias'};
function aplicarMomento(){r.dataset.momento=momento();r.dataset.epoca=epoca();}
aplicarMomento();setInterval(aplicarMomento,60000);

/* ---------- clima (Open-Meteo) ---------- */
const WMO=c=>c===0?['despejado','sun']:c<=2?['parcialmente nublado','sun_behind_small_cloud']:c===3?['nublado','cloud']:c<=48?['neblina','fog']:
  c<=57?['llovizna','sun_behind_rain_cloud']:c<=67?['lluvia','cloud_with_rain']:c<=77?['frío','cloud']:c<=82?['aguaceros','cloud_with_rain']:['tormenta','cloud_with_lightning_and_rain'];
const tipoClima=c=>c==null?'':c>=95?'tormenta':(c>=51&&c<=67)||(c>=80&&c<=82)?'lluvia':c===3||c===45||c===48?'nublado':c<=2&&c>0?'parcial':'despejado';
const KEY='rumentis-clima',KEYL='rumentis-ubic';
let CLIMA=null;try{CLIMA=JSON.parse(localStorage.getItem(KEY)||'null');}catch(e){}
const iconoDia=()=>{const m=momento();return m==='amanecer'?'sunrise':m==='atardecer'?'sunset':(m==='noche'||m==='madrugada')?'crescent_moon':'sun';};
function iconoClima(){if(!CLIMA)return iconoDia();const [,ic]=WMO(CLIMA.code);if(!CLIMA.dia&&(ic==='sun'||ic==='sun_behind_small_cloud'))return ic==='sun'?'crescent_moon':'cloud';return ic;}
function textoClima(){if(!CLIMA)return '';return `${Math.round(CLIMA.t)} °C, ${WMO(CLIMA.code)[0]}`;}
const getJ=async u=>{const c=new AbortController();const t=setTimeout(()=>c.abort(),9000);try{const r=await fetch(u,{signal:c.signal});if(!r.ok)throw 0;return await r.json();}finally{clearTimeout(t);}};
function ubicGPS(){return new Promise(ok=>{if(!navigator.geolocation)return ok(null);let hecho=false;const fin=v=>{if(!hecho){hecho=true;ok(v);}};
  setTimeout(()=>fin(null),10000);
  try{navigator.geolocation.getCurrentPosition(p=>fin({lat:p.coords.latitude,lon:p.coords.longitude,n:''}),()=>fin(null),{maximumAge:3600e3,timeout:9000,enableHighAccuracy:false});}catch(e){fin(null);}});}
async function ubicacion(){
  let u=null;try{u=JSON.parse(localStorage.getItem(KEYL)||'null');}catch(e){}
  const nombre=(S.config.ubicacion||'').trim();
  if(u&&u.origen==='nombre'&&u.q!==nombre)u=null;
  if(u&&Date.now()-u.ts<7*864e5)return u;
  const g=await ubicGPS();
  if(g){u={...g,origen:'gps',ts:Date.now()};}
  else if(nombre){
    // prueba cada parte ("aldea, municipio, depto"): la primera que el mapa conozca
    const CA=['HN','GT','SV','NI','CR','PA','MX','BZ'];
    for(const q of nombre.split(',').map(x=>x.trim()).filter(Boolean)){
      const j=await getJ(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(q)}&count=10&language=es&format=json`);
      const res=(j.results||[]).sort((a,b)=>(CA.includes(b.country_code)?1:0)-(CA.includes(a.country_code)?1:0))[0];
      if(res){u={lat:res.latitude,lon:res.longitude,n:res.name,origen:'nombre',q:nombre,ts:Date.now()};break;}
    }}
  if(u)try{localStorage.setItem(KEYL,JSON.stringify(u));}catch(e){}
  return u;
}
async function actualizarClima(forzar){
  const cambioUbic=CLIMA&&CLIMA.origen==='nombre'&&CLIMA.q!==(S.config.ubicacion||'').trim();
  if(!forzar&&!cambioUbic&&CLIMA&&Date.now()-CLIMA.ts<30*60e3){pintarClima();return;}
  try{
    const u=await ubicacion();if(!u){pintarClima();return;}
    const j=await getJ(`https://api.open-meteo.com/v1/forecast?latitude=${u.lat}&longitude=${u.lon}&current=temperature_2m,relative_humidity_2m,weather_code,is_day,precipitation,wind_speed_10m&daily=temperature_2m_max,precipitation_probability_max&timezone=auto&forecast_days=2`);
    const c=j.current||{};
    CLIMA={t:c.temperature_2m,h:c.relative_humidity_2m,code:c.weather_code,dia:!!c.is_day,lluvia:c.precipitation,viento:c.wind_speed_10m,max:(j.daily||{}).temperature_2m_max,probLluvia:(j.daily||{}).precipitation_probability_max,lugar:u.n||'',origen:u.origen,q:u.q||'',ts:Date.now()};
    try{localStorage.setItem(KEY,JSON.stringify(CLIMA));}catch(e){}
  }catch(e){}
  pintarClima();
}
function pintarClima(){
  r.dataset.clima=CLIMA&&Date.now()-CLIMA.ts<6*3600e3?tipoClima(CLIMA.code):'';
  const m=document.querySelector('body[data-pg="hoy"] .hd .top .meta');
  if(m&&!m.querySelector('.clima')){m.insertAdjacentHTML('beforeend',`<span class="clima"></span>`);}
  document.querySelectorAll('.hd .clima').forEach(e=>{
    e.innerHTML=`<img src="ic3d/${iconoClima()}.webp" alt="">${CLIMA?`<b>${esc(textoClima())}</b>${CLIMA.lugar?`<i>${esc(CLIMA.lugar)}</i>`:''}`:`<i>${EPOCA_TXT[epoca()]}</i>`}`;
    e.title=CLIMA?'Clima de tu zona':'Pon tu ubicación en Más, Configuración, para ver el clima';e.setAttribute('role','button');e.dataset.act='climaVer';
  });
}
ACTS.climaVer=()=>{if(typeof abrirRumi==='function'&&window.RumiMenu&&RumiMenu.clima){abrirRumi();setTimeout(()=>RumiMenu.clima(),250);}else actualizarClima(true);};
window.Clima={actual:()=>CLIMA,actualizar:actualizarClima,momento,epoca,texto:textoClima,icono:iconoClima,tipo:()=>CLIMA?tipoClima(CLIMA.code):''};

/* ---------- fotos de novillos en bucle en cada encabezado ---------- */
const OFF={hoy:0,lotes:5,registrar:10,graficos:15,mas:20,lote:3,animal:8,formular:13,agenda:18,bodega:23,metodologia:7};
let idx=Math.floor(Math.random()*FOTOS.length),ultimaPg='';
const url=i=>`fondos/${FOTOS[((i%FOTOS.length)+FOTOS.length)%FOTOS.length]}`;
function montar(){
  const hd=document.querySelector('#app .hd');if(!hd||hd.querySelector('.hd-fx')||!FOTOS.length)return;
  const pg=document.body.dataset.pg||'hoy';
  if(pg!==ultimaPg){idx=(OFF[pg]||0)+Math.floor(Math.random()*3);ultimaPg=pg;}
  hd.insertAdjacentHTML('afterbegin',`<div class="hd-fx" aria-hidden="true"><i class="ft on" style="background-image:url('${url(idx)}')"></i><i class="ft"></i><b class="tinte"></b><b class="fx-cielo"></b><b class="fx-lluvia"></b><b class="fx-nubes"></b></div>`);
  pintarClima();
}
function siguiente(){
  const fx=document.querySelector('#app .hd .hd-fx');if(!fx||document.hidden)return;
  idx++;const [a,b]=fx.querySelectorAll('.ft');const on=a.classList.contains('on')?a:b,off=on===a?b:a;
  const img=new Image();img.onload=()=>{off.style.backgroundImage=`url('${url(idx)}')`;off.classList.remove('kb');void off.offsetWidth;off.classList.add('on','kb');on.classList.remove('on');};img.src=url(idx);
}
setInterval(siguiente,8000);
new MutationObserver(()=>montar()).observe(document.getElementById('app'),{childList:true});
montar();
document.addEventListener('visibilitychange',()=>{if(!document.hidden){aplicarMomento();actualizarClima();}});

/* ---------- bienvenida animada ---------- */
function bienvenida(){
  const primera=(()=>{try{return !localStorage.getItem('rumentis-bienvenida');}catch(e){return false;}})();
  const m=momento(),finca=(S.config.finca&&S.config.finca!=='Mi engorde')?S.config.finca:'';
  const w=document.createElement('div');w.id='splash';w.setAttribute('role','presentation');
  w.innerHTML=`<i class="sp-foto" style="background-image:url('${url(Math.floor(Math.random()*FOTOS.length))}')"></i><b class="sp-tinte"></b>
   <div class="sp-c"><div class="sp-logo">${typeof FIRMA!=='undefined'?FIRMA:''}</div>
   <p class="sp-hola">${SALUDO[m]}${finca?`, <b>${esc(finca)}</b>`:''}</p>
   <p class="sp-lema">Tu engorde, con números claros y hecho con cariño</p>
   <div class="sp-clima">${CLIMA&&Date.now()-CLIMA.ts<6*3600e3?`<img src="ic3d/${iconoClima()}.webp" alt=""><span>${esc(textoClima())}${CLIMA.lugar?` · ${esc(CLIMA.lugar)}`:''}</span>`:`<img src="ic3d/${iconoDia()}.webp" alt=""><span>${EPOCA_TXT[epoca()].replace(/^./,c=>c.toUpperCase())}</span>`}</div></div>
   <div class="sp-barra"><i></i></div>`;
  document.body.appendChild(w);
  const cerrar=()=>{if(w.classList.contains('fin'))return;w.classList.add('fin');setTimeout(()=>{w.remove();if(primera)saludoRumi();},600);};
  w.addEventListener('click',cerrar);setTimeout(cerrar,primera?3600:2600);
}
function saludoRumi(){
  try{localStorage.setItem('rumentis-bienvenida','1');}catch(e){}
  const w=document.createElement('div');w.id='hola-rumi';
  w.innerHTML=`<div class="hr-c"><div class="hr-rumi"><img src="ic3d/rumi.webp" alt=""><img class="hr-mano" src="ic3d/waving_hand.webp" alt=""></div>
   <div class="hr-globo"><b>¡Hola! Soy Rumi</b><p>Te voy a ayudar con tu engorde: tus lotes, la comida, la salud del ganado y tus ganancias.</p><p>Te enseño la app en pocos pasos. Solo toca <b>Siguiente</b>.</p></div>
   <button type="button" class="btn pri full hr-si">Enséñame la app</button><button type="button" class="btn full hr-no">Ahora no</button></div>`;
  document.body.appendChild(w);requestAnimationFrame(()=>w.classList.add('on'));
  const listo=()=>document.dispatchEvent(new Event('rumentis-listo'));
  const fin=t=>{w.classList.remove('on');setTimeout(()=>{w.remove();if(t){document.addEventListener('tourfin',listo,{once:true});iniciarTour();}else listo();},350);};
  w.querySelector('.hr-si').onclick=()=>fin(true);w.querySelector('.hr-no').onclick=()=>fin(false);
}
window.Bienvenida={mostrar:bienvenida,rumi:saludoRumi};
const conSplash=!!(window.CON_BIENVENIDA||(!window.SIN_BIENVENIDA&&!navigator.webdriver));
const primeraVez=(()=>{try{return !localStorage.getItem('rumentis-bienvenida');}catch(e){return false;}})();
if(conSplash)bienvenida();
// la primera vez el permiso de ubicación espera a que termine el saludo y el recorrido
if(conSplash&&primeraVez){pintarClima();document.addEventListener('rumentis-listo',()=>setTimeout(()=>actualizarClima(),700),{once:true});}
else setTimeout(()=>actualizarClima(),1200);
})();
