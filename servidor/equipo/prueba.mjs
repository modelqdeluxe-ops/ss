// Prueba del servidor de relevo con miniflare (Workers + D1 en local): npm install && npm test
// Firma con la misma tweetnacl de la app y el mismo JSON canónico.
import {Miniflare} from 'miniflare';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url);
const nacl=require('../../app/assets/lib/nacl-fast.min.js');

const mf=new Miniflare({modules:true,scriptPath:'worker.js',d1Databases:['DB'],durableObjects:{TIMBRE:{className:'Timbre',useSQLite:true}},compatibilityDate:'2025-07-01'});
const db=await mf.getD1Database('DB');
for(const s of readFileSync('schema.sql','utf8').replace(/--.*$/gm,'').split(';').map(x=>x.trim()).filter(Boolean))await db.prepare(s).run();

const B='http://equipo.local';
const b64=u=>Buffer.from(u).toString('base64');
const b64u=u=>b64(u).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
const canon=v=>v==null?'null':Array.isArray(v)?'['+v.map(canon).join(',')+']':typeof v==='object'?'{'+Object.keys(v).sort().filter(k=>v[k]!==undefined).map(k=>JSON.stringify(k)+':'+canon(v[k])).join(',')+'}':JSON.stringify(v);
const sha=s=>createHash('sha256').update(s).digest('hex');
const llave=()=>{const k=nacl.sign.keyPair();return {pub:b64(k.publicKey),sec:k.secretKey};};
const firmar=(o,k)=>({...o,f:b64u(nacl.sign.detached(Buffer.from(canon(o)),k.sec))});
const idDe=pub=>'v'+sha('RUMENTIS-VAQ|'+pub).slice(0,12);
const post=(ruta,b)=>mf.dispatchFetch(B+ruta,{method:'POST',headers:{'Content-Type':'text/plain'},body:JSON.stringify(b)});
const pedir=async(ruta,cuerpo,k)=>{const b={...cuerpo,ts:Date.now()};const r=await post(ruta,k?firmar(b,k):b);return {st:r.status,j:await r.json()};};
const sobre=(t,de,para,k,extra={})=>firmar({...extra,v:1,t,e:E,de,para,ts:Date.now(),id:b64u(nacl.randomBytes(9)),z:0,x:'sb',g:1,n:'x',c:'cifrado'},k);

let r=await mf.dispatchFetch(B+'/v1/salud');assert.equal((await r.json()).ok,true);
r=await mf.dispatchFetch(B+'/v1/enviar',{method:'OPTIONS'});assert.equal(r.status,204);assert.equal(r.headers.get('access-control-allow-origin'),'*');

// el jefe registra su equipo; otra llave no puede quedárselo
const jefe=llave(),otro=llave(),E='e'+b64u(nacl.randomBytes(12));
assert.equal((await pedir('/v1/equipo',{e:E,firma:jefe.pub},jefe)).st,200);
assert.equal((await pedir('/v1/equipo',{e:E,firma:jefe.pub},jefe)).st,200,'registrar dos veces no falla');
assert.equal((await pedir('/v1/equipo',{e:E,firma:otro.pub},otro)).st,409);
// firma falsa o pedido viejo
assert.equal((await pedir('/v1/equipo',{e:'e'+b64u(nacl.randomBytes(12)),firma:jefe.pub},otro)).st,400);
{const b=firmar({e:E,quien:'jefe',desde:0,ts:Date.now()-20*60e3},jefe);assert.equal((await post('/v1/recibir',b)).status,403,'pedido de hace 20 minutos');}

// licencias: solo el jefe las sube; se busca la ficha por el SHA-256
const lic='7K3MQ9XT2HWP8C4NZD6A',h=sha('RUMENTIS-LIC-1|'+lic),ficha={v:1,e:E,j:jefe.pub,k:'caja',fn:'Finca La Prueba',s:'',p:0,f:'x'};
assert.equal((await pedir('/v1/licencias',{e:E,quien:'jefe',lics:[{h,ficha}]},otro)).st,403);
assert.equal((await pedir('/v1/licencias',{e:E,quien:'jefe',lics:[{h,ficha}]},jefe)).st,200);
let x=await pedir('/v1/ficha',{h});assert.equal(x.st,200);assert.equal(x.j.ficha.fn,'Finca La Prueba');
assert.equal((await pedir('/v1/ficha',{h:sha('otra')})).st,404);
const g=await db.prepare('SELECT * FROM licencias').all();assert.ok(g.results.every(z=>!JSON.stringify(z).includes(lic)),'el código de la licencia no se guarda');

// alta del vaquero: va al buzón del jefe
const vaq=llave(),V=idDe(vaq.pub);
const alta=sobre('alta',V,'jefe',vaq,{k:'cajaV',j:vaq.pub});alta.x='box';const altaF=firmar({...alta,f:undefined},vaq);
assert.equal((await pedir('/v1/alta',{h,sobre:altaF})).st,200);
assert.equal((await pedir('/v1/alta',{h:sha('nada'),sobre:altaF})).st,404);
assert.equal((await pedir('/v1/alta',{h,sobre:{...altaF,de:'v000000000000'}})).st,400,'el número del vaquero sale de su llave');
x=await pedir('/v1/recibir',{e:E,quien:'jefe',desde:0},jefe);assert.equal(x.st,200);assert.equal(x.j.sobres.length,1);assert.equal(x.j.sobres[0].t,'alta');const c1=x.j.hasta;
x=await pedir('/v1/recibir',{e:E,quien:'jefe',desde:c1},jefe);assert.equal(x.j.sobres.length,0,'el cursor avanza');

// antes de ser aceptado: el vaquero no puede mandar, pero sí leer lo suyo (con su propia llave)
assert.equal((await pedir('/v1/enviar',{e:E,quien:V,sobres:[sobre('ops',V,'jefe',vaq)]},vaq)).st,403);
assert.equal((await pedir('/v1/recibir',{e:E,quien:V,firma:vaq.pub,desde:0},vaq)).st,200);
assert.equal((await pedir('/v1/recibir',{e:E,quien:V,firma:otro.pub,desde:0},otro)).st,403,'otra llave no lee lo del vaquero');
// el jefe le da la bienvenida (para él) y publica el estado (para todos)
assert.equal((await pedir('/v1/enviar',{e:E,quien:'jefe',sobres:[sobre('bienvenida','jefe',V,jefe)]},jefe)).st,200);
x=await pedir('/v1/recibir',{e:E,quien:V,firma:vaq.pub,desde:0},vaq);assert.equal(x.j.sobres.length,1);assert.equal(x.j.sobres[0].t,'bienvenida');
assert.equal((await pedir('/v1/enviar',{e:E,quien:'jefe',sobres:[sobre('estado','jefe','todos',jefe,{r:'estado'})]},jefe)).st,200);
x=await pedir('/v1/recibir',{e:E,quien:V,firma:vaq.pub,desde:0},vaq);assert.equal(x.j.sobres.length,1,'sin ser miembro no recibe lo de todos');
// miembro
assert.equal((await pedir('/v1/miembros',{e:E,quien:'jefe',altas:[{vid:V,firma:vaq.pub}]},vaq)).st,403);
assert.equal((await pedir('/v1/miembros',{e:E,quien:'jefe',altas:[{vid:V,firma:vaq.pub},{vid:'v111111111111',firma:otro.pub}]},jefe)).st,200);
assert.equal((await db.prepare('SELECT COUNT(*) n FROM miembros').first()).n,1,'un número que no sale de la llave no entra');
x=await pedir('/v1/recibir',{e:E,quien:V,desde:0},vaq);assert.equal(x.j.sobres.length,2,'ya es miembro: recibe su bienvenida y el estado');
// el estado nuevo reemplaza al anterior
await pedir('/v1/enviar',{e:E,quien:'jefe',sobres:[sobre('estado','jefe','todos',jefe,{r:'estado'})]},jefe);
assert.equal((await db.prepare("SELECT COUNT(*) n FROM buzon WHERE r='estado'").first()).n,1);
// el vaquero manda sus registros al jefe, y solo al jefe
assert.equal((await pedir('/v1/enviar',{e:E,quien:V,sobres:[sobre('ops',V,'jefe',vaq)]},vaq)).st,200);
assert.equal((await pedir('/v1/enviar',{e:E,quien:V,sobres:[sobre('ops',V,'todos',vaq)]},vaq)).st,403);
assert.equal((await pedir('/v1/enviar',{e:E,quien:V,sobres:[sobre('ops','jefe','jefe',vaq)]},vaq)).st,400,'no se hace pasar por el jefe');
x=await pedir('/v1/recibir',{e:E,quien:'jefe',desde:c1},jefe);assert.deepEqual(x.j.sobres.map(s=>s.t),['ops']);
// tiempo real: el timbre avisa al jefe cuando el vaquero manda algo, y al vaquero cuando el jefe publica
const timbre=async(cuerpo,k)=>{const b=firmar({...cuerpo,ts:Date.now()},k);
  const r=await mf.dispatchFetch(B+'/v1/timbre?b='+b64u(Buffer.from(JSON.stringify(b))),{headers:{Upgrade:'websocket'}});
  if(r.status!==101)return {st:r.status};const ws=r.webSocket;ws.accept();const msgs=[];ws.addEventListener('message',ev=>msgs.push(ev.data));return {st:101,ws,msgs};};
const espera=ms=>new Promise(r=>setTimeout(r,ms));
assert.equal((await mf.dispatchFetch(B+'/v1/timbre')).status,426,'sin WebSocket no');
assert.equal((await timbre({e:E,quien:'jefe'},otro)).st,403,'firma de otro: no');
const tj=await timbre({e:E,quien:'jefe'},jefe),tv=await timbre({e:E,quien:V,firma:vaq.pub},vaq);assert.equal(tj.st,101);assert.equal(tv.st,101);
await pedir('/v1/enviar',{e:E,quien:V,sobres:[sobre('ops',V,'jefe',vaq)]},vaq);await espera(150);
assert.deepEqual(tj.msgs,['hay'],'el jefe se entera al instante');assert.deepEqual(tv.msgs,[],'al vaquero no le toca');
await pedir('/v1/enviar',{e:E,quien:'jefe',sobres:[sobre('estado','jefe','todos',jefe,{r:'estado'})]},jefe);await espera(150);
assert.deepEqual(tv.msgs,['hay'],'el vaquero se entera del estado nuevo');assert.equal(tj.msgs.length,1);
tv.ws.send('ping');await espera(100);assert.equal(tv.msgs.at(-1),'pong','ping y pong');
tj.ws.close();tv.ws.close();
{const r=await mf.dispatchFetch(B+'/v1/salud');assert.equal((await r.json()).timbre,true);}
// baja: la licencia deja de encontrarse y el vaquero ya no puede mandar
await pedir('/v1/licencias',{e:E,quien:'jefe',bajas:[h]},jefe);await pedir('/v1/miembros',{e:E,quien:'jefe',bajas:[V]},jefe);
assert.equal((await pedir('/v1/ficha',{h})).st,404);
assert.equal((await pedir('/v1/enviar',{e:E,quien:V,sobres:[sobre('ops',V,'jefe',vaq)]},vaq)).st,403);
assert.equal((await pedir('/v1/alta',{h,sobre:altaF})).st,404);
// una licencia dada de baja no se puede volver a subir
await pedir('/v1/licencias',{e:E,quien:'jefe',lics:[{h,ficha}]},jefe);assert.equal((await pedir('/v1/ficha',{h})).st,404);
// límites: JSON roto, ruta que no existe, método
assert.equal((await post('/v1/recibir','{')).status,400);
assert.equal((await mf.dispatchFetch(B+'/v1/nada',{method:'POST',body:'{}'})).status,404);
assert.equal((await mf.dispatchFetch(B+'/v1/recibir')).status,405);
await mf.dispose();
console.log('servidor del equipo: todo bien');
