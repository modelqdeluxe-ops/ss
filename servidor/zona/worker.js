/* Rumentis: servidor de la comparación anónima con engordes de la zona (Cloudflare Workers + D1).
   POST   /v1/aporte   la app manda los indicadores de la finca del mes (sin nombres ni ubicación exacta)
   GET    /v1/zona     medianas y cuartiles de las fincas del mismo cuadro de ~110 km, de los cuadros vecinos o del país
   DELETE /v1/aporte   la finca sale y se borran todos sus datos
   GET    /v1/salud    para comprobar que está en línea
   Privacidad: la finca es un número al azar del teléfono (aquí solo su hash); no se guardan IP, nombres, lotes ni
   montos totales. Un grupo se muestra solo con MINIMO fincas distintas o más (5 por omisión) y solo sus cuartiles. */

const RANGOS={gdp:[0.1,3],conv:[2.5,25],mort:[0,40],costo:[0,1e7],costo_usd:[0.05,20],dias:[20,400]};
const METR=['gdp','conv','mort','costo','dias'];
const MAX_DIA=30;   // envíos por IP y día

const CORS={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Methods':'GET, POST, DELETE, OPTIONS','Access-Control-Allow-Headers':'Content-Type','Access-Control-Max-Age':'86400'};
const json=(o,st=200)=>new Response(JSON.stringify(o),{status:st,headers:{...CORS,'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'}});
const error=(msg,st=400)=>json({ok:false,error:msg},st);

async function sha(s){const b=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s));return [...new Uint8Array(b)].map(x=>x.toString(16).padStart(2,'0')).join('');}
const hoy=()=>new Date().toISOString().slice(0,10);
const mesMenos=(mes,n)=>{let [y,m]=mes.split('-').map(Number);m-=n;while(m<=0){m+=12;y--;}return `${y}-${String(m).padStart(2,'0')}`;};
const num=(v,[a,b])=>{v=Number(v);return Number.isFinite(v)&&v>=a&&v<=b?v:null;};
const PAIS=/^[A-Z]{2}$/,CELDA=/^-?\d{1,2}_-?\d{1,3}$/,MON=/^[A-Z]{3}$/,MES=/^\d{4}-(0[1-9]|1[0-2])$/,ID=/^[0-9a-f]{32}$/;

async function leerJSON(req){
  const t=await req.text();if(t.length>4000)throw new Error('grande');
  return JSON.parse(t);
}
async function limite(env,req){
  const ip=req.headers.get('CF-Connecting-IP')||'local',d=hoy(),k=await sha(ip+'|'+d+'|'+(env.SAL||''));
  const r=await env.DB.prepare('SELECT n FROM limites WHERE k=?').bind(k).first();
  if(r&&r.n>=MAX_DIA)return false;
  await env.DB.prepare('INSERT INTO limites (k,dia,n) VALUES (?,?,1) ON CONFLICT(k) DO UPDATE SET n=n+1').bind(k,d).run();
  return true;
}

async function aporte(req,env){
  let b;try{b=await leerJSON(req);}catch(e){return error('json');}
  if(!b||b.v!==1||!ID.test(b.id||'')||!PAIS.test(b.pais||'')||!MES.test(b.mes||''))return error('datos');
  const ahora=new Date().toISOString().slice(0,7);if(b.mes>ahora||b.mes<mesMenos(ahora,1))return error('mes');
  const celda=b.celda&&CELDA.test(b.celda)?b.celda:null,moneda=b.moneda&&MON.test(b.moneda)?b.moneda:null;
  const m=b.m||{},v={};for(const k of Object.keys(RANGOS))v[k]=num(m[k],RANGOS[k]);
  if(v.gdp==null&&v.conv==null)return error('vacio');
  const tam=[1,2,3,4].includes(Number(m.tam))?Number(m.tam):null;
  if(!(await limite(env,req)))return error('limite',429);
  const fid=await sha(b.id+'|'+(env.SAL||''));
  await env.DB.prepare(`INSERT INTO aportes (fid,mes,pais,celda,moneda,gdp,conv,mort,costo,costo_usd,dias,tam,creado) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
    ON CONFLICT(fid,mes) DO UPDATE SET pais=excluded.pais,celda=excluded.celda,moneda=excluded.moneda,gdp=excluded.gdp,conv=excluded.conv,mort=excluded.mort,
    costo=excluded.costo,costo_usd=excluded.costo_usd,dias=excluded.dias,tam=excluded.tam,creado=excluded.creado`)
    .bind(fid,b.mes,b.pais,celda,moneda,v.gdp,v.conv,v.mort,v.costo,v.costo_usd,v.dias,tam,new Date().toISOString()).run();
  // limpieza de límites viejos (de vez en cuando)
  if(Math.random()<0.05)await env.DB.prepare('DELETE FROM limites WHERE dia<?').bind(new Date(Date.now()-2*864e5).toISOString().slice(0,10)).run();
  return json({ok:true});
}

async function borrar(req,env,url){
  const id=url.searchParams.get('id')||'';if(!ID.test(id))return error('datos');
  if(!(await limite(env,req)))return error('limite',429);
  const fid=await sha(id+'|'+(env.SAL||''));
  const r=await env.DB.prepare('DELETE FROM aportes WHERE fid=?').bind(fid).run();
  return json({ok:true,borrados:(r.meta&&r.meta.changes)||0});
}

/* cuartiles: interpolación lineal */
function q(v,p){const i=(v.length-1)*p,a=Math.floor(i),b=Math.ceil(i);return v[a]+(v[b]-v[a])*(i-a);}
const red=(x,d)=>Math.round(x*10**d)/10**d;
const DEC={gdp:2,conv:2,mort:2,costo:4,dias:0};
function resumen(filas,min,moneda){
  const r={};
  for(const k of METR){
    let v,d=DEC[k],mon=null;
    if(k==='costo'){
      // en la moneda del que pregunta si hay suficientes fincas con ella; si no, en dólares
      const loc=filas.filter(f=>f.moneda===moneda&&f.costo!=null).map(f=>f.costo);
      if(moneda&&loc.length>=min){v=loc;mon=moneda;}
      else{v=filas.filter(f=>f.costo_usd!=null).map(f=>f.costo_usd);mon='USD';}
    }else v=filas.map(f=>f[k]).filter(x=>x!=null);
    if(v.length<min)continue;
    v.sort((a,b)=>a-b);
    r[k]={p25:red(q(v,0.25),d),p50:red(q(v,0.5),d),p75:red(q(v,0.75),d),n:v.length,...(mon?{moneda:mon}:{})};
  }
  return r;
}
async function zona(env,url){
  const pais=(url.searchParams.get('pais')||'').toUpperCase(),celda=url.searchParams.get('celda')||'',moneda=(url.searchParams.get('moneda')||'').toUpperCase();
  if(!PAIS.test(pais))return error('pais');
  const min=Math.max(3,Number(env.MINIMO)||5),desde=mesMenos(new Date().toISOString().slice(0,7),11);
  // lo último de cada finca en los últimos 12 meses
  const {results}=await env.DB.prepare(`SELECT a.* FROM aportes a JOIN (SELECT fid,MAX(mes) m FROM aportes WHERE pais=? AND mes>=? GROUP BY fid) u ON a.fid=u.fid AND a.mes=u.m`).bind(pais,desde).all();
  const filas=results||[];
  const grupos=[];
  if(CELDA.test(celda)){
    const [la,lo]=celda.split('_').map(Number),cerca=new Set();for(let i=-1;i<=1;i++)for(let j=-1;j<=1;j++)cerca.add(`${la+i}_${lo+j}`);
    grupos.push(['celda',filas.filter(f=>f.celda===celda)],['vecinas',filas.filter(f=>cerca.has(f.celda))]);
  }
  grupos.push(['pais',filas]);
  for(const [nivel,F] of grupos){
    if(F.length<min)continue;
    const metr=resumen(F,min,MON.test(moneda)?moneda:null);
    if(!Object.keys(metr).length)continue;
    return json({ok:true,nivel,fincas:F.length,minimo:min,metr,desde,act:new Date().toISOString()});
  }
  return json({ok:true,nivel:null,fincas:Math.max(0,...grupos.map(g=>g[1].length)),minimo:min,metr:{},desde});
}

export default {
  async fetch(req,env){
    const url=new URL(req.url);
    if(req.method==='OPTIONS')return new Response(null,{status:204,headers:CORS});
    try{
      if(url.pathname==='/v1/salud')return json({ok:true});
      if(url.pathname==='/v1/aporte'&&req.method==='POST')return await aporte(req,env);
      if(url.pathname==='/v1/aporte'&&req.method==='DELETE')return await borrar(req,env,url);
      if(url.pathname==='/v1/zona'&&req.method==='GET')return await zona(env,url);
      return error('ruta',404);
    }catch(e){return error('interno',500);}
  }
};
