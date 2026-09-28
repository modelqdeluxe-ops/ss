// Prueba del servidor con miniflare (Workers + D1 en local): npm install && npm test
import {Miniflare} from 'miniflare';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';

const mf=new Miniflare({modules:true,scriptPath:'worker.js',d1Databases:['DB'],bindings:{SAL:'prueba',MINIMO:'5'},compatibilityDate:'2025-07-01'});
const db=await mf.getD1Database('DB');
for(const s of readFileSync('schema.sql','utf8').replace(/--.*$/gm,'').split(';').map(x=>x.trim()).filter(Boolean))await db.prepare(s).run();

const B='http://zona.local';
const mes=new Date().toISOString().slice(0,7);
const id=i=>i.toString(16).padStart(32,'0');
const post=(b,ip='1.1.1.1')=>mf.dispatchFetch(B+'/v1/aporte',{method:'POST',headers:{'Content-Type':'text/plain','CF-Connecting-IP':ip},body:JSON.stringify(b)});
const zona=q=>mf.dispatchFetch(B+'/v1/zona?'+new URLSearchParams(q)).then(r=>r.json());
const finca=(i,celda,o={})=>({v:1,id:id(i),pais:'HN',celda,mes,moneda:'HNL',m:{gdp:1.1+i*0.05,conv:8-i*0.1,mort:1+i*0.2,costo:40+i,costo_usd:1.5+i*0.04,dias:110+i,tam:2,...o}});

let r=await mf.dispatchFetch(B+'/v1/salud');assert.equal((await r.json()).ok,true);
r=await mf.dispatchFetch(B+'/v1/aporte',{method:'OPTIONS'});assert.equal(r.status,204);assert.equal(r.headers.get('access-control-allow-origin'),'*');
// datos malos
for(const b of [{},{v:1,id:'x',pais:'HN',mes},{...finca(1,'14_-87'),pais:'Honduras'},{...finca(1,'14_-87'),mes:'2019-01'},{...finca(1,'14_-87'),m:{gdp:99,conv:99}}]){
  r=await post(b);assert.equal(r.status,400,JSON.stringify(b));}
// con 4 fincas en la celda todavía no se muestra nada
for(let i=1;i<=4;i++)assert.equal((await post(finca(i,'14_-87'),'10.0.0.'+i)).status,200);
let z=await zona({pais:'HN',celda:'14_-87',moneda:'HNL'});
assert.equal(z.nivel,null);assert.equal(z.fincas,4);assert.deepEqual(z.metr,{});
// la quinta abre el grupo de la celda
await post(finca(5,'14_-87'),'10.0.0.5');
z=await zona({pais:'HN',celda:'14_-87',moneda:'HNL'});
assert.equal(z.nivel,'celda');assert.equal(z.fincas,5);assert.equal(z.metr.gdp.p50,1.25);assert.equal(z.metr.costo.moneda,'HNL');assert.equal(z.metr.costo.p50,43);
// el mismo mes se reemplaza, no se suma
await post(finca(5,'14_-87',{gdp:2}),'10.0.0.5');
z=await zona({pais:'HN',celda:'14_-87',moneda:'HNL'});assert.equal(z.fincas,5);assert.equal(z.metr.gdp.p75,1.3);
// una celda vecina con pocas fincas usa las vecinas; otra lejana usa el país
z=await zona({pais:'HN',celda:'15_-87',moneda:'HNL'});assert.equal(z.nivel,'vecinas');
z=await zona({pais:'HN',celda:'10_-80',moneda:'USD'});assert.equal(z.nivel,'pais');assert.equal(z.metr.costo.moneda,'USD');
z=await zona({pais:'HN'});assert.equal(z.nivel,'pais');
// no se guarda el número de la finca, solo su hash
const f=await db.prepare('SELECT fid FROM aportes').all();assert.ok(f.results.every(x=>x.fid.length===64&&!x.fid.includes(id(1))));
// salir: se borran sus datos
r=await mf.dispatchFetch(B+'/v1/aporte?id='+id(5),{method:'DELETE'});assert.equal((await r.json()).borrados,1);
z=await zona({pais:'HN',celda:'14_-87',moneda:'HNL'});assert.equal(z.nivel,null);
// límite por IP y día
let ult;for(let i=0;i<31;i++)ult=await post({...finca(1,'1_1'),id:id(100+i)},'9.9.9.9');assert.equal(ult.status,429);
// ruta que no existe
assert.equal((await mf.dispatchFetch(B+'/nada')).status,404);
await mf.dispose();
console.log('servidor de la zona: todas las pruebas pasaron');
