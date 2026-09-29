/* Rumentis: servidor de relevo del equipo (Cloudflare Workers + D1).
   Guarda y entrega los sobres que se mandan el jefe y sus vaqueros. Los sobres van cifrados de punta a punta
   (tweetnacl en las apps): aquí no se pueden leer. Lo que sí se comprueba:
   - Cada pedido va firmado (Ed25519) por quien lo hace y con la hora (se rechaza si tiene más de 15 minutos).
   - El jefe se registra con su llave; solo él sube licencias, da de alta o de baja a vaqueros y publica el estado.
   - Una licencia se guarda por su SHA-256 (el código no). Con ella, un teléfono nuevo busca la ficha del equipo y
     manda su alta al jefe. Solo los vaqueros que el jefe aceptó pueden mandarle registros.
   - Cada quien lee solo lo suyo: el jefe lo que va para 'jefe'; un vaquero lo que va para él (su número sale de su
     llave pública) y, si es miembro, lo que va para 'todos'.
   Rutas (POST con JSON en text/plain): /v1/equipo, /v1/licencias, /v1/ficha, /v1/miembros, /v1/alta, /v1/enviar,
   /v1/recibir. GET /v1/salud. */
const CORS={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Methods':'GET, POST, OPTIONS','Access-Control-Allow-Headers':'Content-Type','Access-Control-Max-Age':'86400'};
const json=(o,st=200)=>new Response(JSON.stringify(o),{status:st,headers:{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store',...CORS}});
const mal=(m,st=400)=>json({ok:false,error:m},st);
const MAX_PEDIDO=2_000_000,MAX_SOBRE=1_200_000,VENTANA=15*60e3,RETENER=60*864e5,POR_VEZ=100,ALTAS_MAX=20;
const enc=new TextEncoder();
const canon=v=>v==null?'null':Array.isArray(v)?'['+v.map(canon).join(',')+']':typeof v==='object'?'{'+Object.keys(v).sort().filter(k=>v[k]!==undefined).map(k=>JSON.stringify(k)+':'+canon(v[k])).join(',')+'}':JSON.stringify(v);
const deB64=t=>{const s=atob(t);const u=new Uint8Array(s.length);for(let i=0;i<s.length;i++)u[i]=s.charCodeAt(i);return u;};
const deB64u=t=>deB64(String(t).replace(/-/g,'+').replace(/_/g,'/')+'==='.slice((String(t).length+3)%4));
async function sha(s){const d=await crypto.subtle.digest('SHA-256',enc.encode(s));return [...new Uint8Array(d)].map(b=>b.toString(16).padStart(2,'0')).join('');}
const idDe=async firma=>'v'+(await sha('RUMENTIS-VAQ|'+firma)).slice(0,12);
async function verificar(obj,firmaPub){
  try{
    if(!obj||typeof obj.f!=='string'||typeof firmaPub!=='string')return false;
    const o={...obj};delete o.f;
    const k=await crypto.subtle.importKey('raw',deB64(firmaPub),{name:'Ed25519'},false,['verify']);
    return await crypto.subtle.verify({name:'Ed25519'},k,deB64u(obj.f),enc.encode(canon(o)));
  }catch(e){return false;}
}
const EQ_RE=/^e[A-Za-z0-9_-]{10,40}$/,V_RE=/^v[0-9a-f]{12}$/,H_RE=/^[0-9a-f]{64}$/,B64_RE=/^[A-Za-z0-9+/]{43}=$/;
const reciente=ts=>typeof ts==='number'&&Math.abs(Date.now()-ts)<VENTANA;

async function firmaJefe(env,e){const r=await env.DB.prepare('SELECT firma FROM equipos WHERE id=?').bind(e).first();return r?r.firma:null;}
async function firmaMiembro(env,e,vid){const r=await env.DB.prepare('SELECT firma FROM miembros WHERE equipo=? AND vid=?').bind(e,vid).first();return r?r.firma:null;}
// quién hace el pedido: el jefe, un miembro o (solo para leer lo suyo) un vaquero todavía sin aceptar
async function quien(env,b,{nuevoOk=false}={}){
  if(!EQ_RE.test(b.e||'')||!reciente(b.ts))return null;
  if(b.quien==='jefe'){const f=await firmaJefe(env,b.e);return f&&await verificar(b,f)?{jefe:true}:null;}
  if(!V_RE.test(b.quien||''))return null;
  const fm=await firmaMiembro(env,b.e,b.quien);
  if(fm)return await verificar(b,fm)?{vid:b.quien,miembro:true}:null;
  if(nuevoOk&&typeof b.firma==='string'&&B64_RE.test(b.firma)&&await idDe(b.firma)===b.quien&&await verificar(b,b.firma))return {vid:b.quien,miembro:false};
  return null;
}
function sobreOk(s){return s&&typeof s==='object'&&s.v===1&&typeof s.t==='string'&&typeof s.para==='string'&&typeof s.de==='string'&&typeof s.f==='string'&&typeof s.c==='string'&&JSON.stringify(s).length<=MAX_SOBRE;}

const RUTAS={
  async '/v1/equipo'(b,env){
    if(!EQ_RE.test(b.e||'')||!B64_RE.test(b.firma||'')||!reciente(b.ts)||!(await verificar(b,b.firma)))return mal('pedido no válido');
    const ya=await firmaJefe(env,b.e);
    if(ya&&ya!==b.firma)return mal('ese equipo es de otra llave',409);
    if(!ya)await env.DB.prepare('INSERT INTO equipos(id,firma,creado) VALUES(?,?,?)').bind(b.e,b.firma,Date.now()).run();
    return json({ok:true});
  },
  async '/v1/licencias'(b,env){
    const q=await quien(env,b);if(!q||!q.jefe)return mal('sin permiso',403);
    const lics=Array.isArray(b.lics)?b.lics.slice(0,200):[],bajas=Array.isArray(b.bajas)?b.bajas.slice(0,200):[];const st=[];
    for(const l of lics){if(!H_RE.test(l.h||'')||!l.ficha||l.ficha.e!==b.e)continue;const fi=JSON.stringify(l.ficha);if(fi.length>3000)continue;
      st.push(env.DB.prepare('INSERT INTO licencias(h,equipo,ficha,creado) VALUES(?,?,?,?) ON CONFLICT(h) DO UPDATE SET ficha=excluded.ficha WHERE licencias.equipo=excluded.equipo AND licencias.baja IS NULL').bind(l.h,b.e,fi,Date.now()));}
    for(const h of bajas)if(H_RE.test(h||''))st.push(env.DB.prepare('UPDATE licencias SET baja=? WHERE h=? AND equipo=?').bind(Date.now(),h,b.e));
    if(st.length)await env.DB.batch(st);
    return json({ok:true,guardadas:lics.length,bajas:bajas.length});
  },
  async '/v1/ficha'(b,env){
    if(!H_RE.test(b.h||''))return mal('licencia no válida');
    const r=await env.DB.prepare('SELECT ficha FROM licencias WHERE h=? AND baja IS NULL').bind(b.h).first();
    if(!r)return mal('no existe',404);
    return json({ok:true,ficha:JSON.parse(r.ficha)});
  },
  async '/v1/miembros'(b,env){
    const q=await quien(env,b);if(!q||!q.jefe)return mal('sin permiso',403);const st=[];
    for(const a of (Array.isArray(b.altas)?b.altas:[]).slice(0,200)){if(!V_RE.test(a.vid||'')||!B64_RE.test(a.firma||'')||await idDe(a.firma)!==a.vid)continue;
      st.push(env.DB.prepare('INSERT INTO miembros(equipo,vid,firma) VALUES(?,?,?) ON CONFLICT(equipo,vid) DO UPDATE SET firma=excluded.firma').bind(b.e,a.vid,a.firma));}
    for(const v of (Array.isArray(b.bajas)?b.bajas:[]).slice(0,200))if(V_RE.test(v||''))st.push(env.DB.prepare('DELETE FROM miembros WHERE equipo=? AND vid=?').bind(b.e,v));
    if(st.length)await env.DB.batch(st);
    return json({ok:true});
  },
  async '/v1/alta'(b,env){
    if(!H_RE.test(b.h||'')||!sobreOk(b.sobre))return mal('pedido no válido');
    const s=b.sobre;const L=await env.DB.prepare('SELECT equipo,baja,altas FROM licencias WHERE h=?').bind(b.h).first();
    if(!L||L.baja)return mal('no existe',404);
    if(L.altas>=ALTAS_MAX)return mal('demasiados intentos con esa licencia',429);
    if(s.t!=='alta'||s.para!=='jefe'||s.e!==L.equipo||!B64_RE.test(s.j||'')||await idDe(s.j)!==s.de||!(await verificar(s,s.j)))return mal('sobre no válido');
    await env.DB.batch([
      env.DB.prepare('UPDATE licencias SET altas=altas+1 WHERE h=?').bind(b.h),
      env.DB.prepare('INSERT INTO buzon(equipo,para,de,r,cuerpo,creado) VALUES(?,?,?,?,?,?)').bind(L.equipo,'jefe',s.de,null,JSON.stringify(s),Date.now())]);
    return json({ok:true});
  },
  async '/v1/enviar'(b,env){
    const q=await quien(env,b);if(!q||(!q.jefe&&!q.miembro))return mal('sin permiso',403);
    const sobres=Array.isArray(b.sobres)?b.sobres.slice(0,100):[];const st=[];const yo=q.jefe?'jefe':q.vid;
    for(const s of sobres){
      if(!sobreOk(s)||s.e!==b.e||s.de!==yo)return mal('sobre no válido');
      if(!q.jefe&&s.para!=='jefe')return mal('un vaquero solo le escribe al jefe',403);
      if(q.jefe&&s.para!=='todos'&&!V_RE.test(s.para))return mal('destino no válido');
      const r=q.jefe&&s.r==='estado'?'estado':null;
      if(r)st.push(env.DB.prepare('DELETE FROM buzon WHERE equipo=? AND para=? AND r=?').bind(b.e,s.para,r));
      st.push(env.DB.prepare('INSERT INTO buzon(equipo,para,de,r,cuerpo,creado) VALUES(?,?,?,?,?,?)').bind(b.e,s.para,yo,r,JSON.stringify(s),Date.now()));
    }
    if(st.length)await env.DB.batch(st);
    return json({ok:true,guardados:sobres.length});
  },
  async '/v1/recibir'(b,env){
    const q=await quien(env,b,{nuevoOk:true});if(!q)return mal('sin permiso',403);
    const desde=Math.max(0,Math.floor(+b.desde||0));let r;
    if(q.jefe)r=await env.DB.prepare('SELECT id,cuerpo FROM buzon WHERE equipo=? AND para=? AND id>? ORDER BY id LIMIT ?').bind(b.e,'jefe',desde,POR_VEZ).all();
    else if(q.miembro)r=await env.DB.prepare('SELECT id,cuerpo FROM buzon WHERE equipo=? AND para IN (?,?) AND id>? ORDER BY id LIMIT ?').bind(b.e,q.vid,'todos',desde,POR_VEZ).all();
    else r=await env.DB.prepare('SELECT id,cuerpo FROM buzon WHERE equipo=? AND para=? AND id>? ORDER BY id LIMIT ?').bind(b.e,q.vid,desde,POR_VEZ).all();
    const rows=r.results||[];
    // lo que ya pasó por aquí hace más de 60 días se borra (de vez en cuando, sin frenar el pedido)
    if(Math.random()<0.02)await env.DB.prepare('DELETE FROM buzon WHERE creado<?').bind(Date.now()-RETENER).run();
    return json({ok:true,sobres:rows.map(x=>JSON.parse(x.cuerpo)),hasta:rows.length?rows[rows.length-1].id:desde,mas:rows.length===POR_VEZ});
  }
};

export default {
  async fetch(req,env){
    const u=new URL(req.url);
    if(req.method==='OPTIONS')return new Response(null,{status:204,headers:CORS});
    if(req.method==='GET'&&u.pathname==='/v1/salud')return json({ok:true,servicio:'rumentis-equipo'});
    const f=RUTAS[u.pathname];if(!f)return mal('no existe',404);
    if(req.method!=='POST')return mal('usa POST',405);
    const t=await req.text();if(t.length>MAX_PEDIDO)return mal('muy grande',413);
    let b;try{b=JSON.parse(t);}catch(e){return mal('JSON no válido');}
    if(!b||typeof b!=='object')return mal('JSON no válido');
    try{return await f(b,env);}catch(e){return mal('error del servidor',500);}
  }
};
