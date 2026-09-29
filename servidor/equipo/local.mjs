// Servidor del equipo en una computadora de la finca: npm install && npm run local
// Los teléfonos del jefe y de los vaqueros se conectan por el mismo Wi-Fi (o el punto de acceso del teléfono del jefe):
// en la app del jefe, Equipo > Servidor del equipo, se escribe la dirección que se muestra abajo.
// Los datos se guardan en la carpeta datos/ (no se pierden al apagar). Con PUERTO=... se cambia el puerto.
import {Miniflare} from 'miniflare';
import {readFileSync} from 'node:fs';
import {networkInterfaces} from 'node:os';
const port=+process.env.PUERTO||8790;
// solo esta computadora (pruebas): LOCAL=1
const host=process.env.LOCAL?'127.0.0.1':'0.0.0.0';
const mf=new Miniflare({modules:true,scriptPath:'worker.js',d1Databases:['DB'],durableObjects:{TIMBRE:{className:'Timbre',useSQLite:true}},
  compatibilityDate:'2025-07-01',host,port,d1Persist:process.env.SIN_GUARDAR?false:'datos/d1',durableObjectsPersist:process.env.SIN_GUARDAR?false:'datos/timbre'});
const db=await mf.getD1Database('DB');
for(const s of readFileSync('schema.sql','utf8').replace(/--.*$/gm,'').split(';').map(x=>x.trim()).filter(Boolean))await db.prepare(s).run();
await mf.ready;
const ips=Object.values(networkInterfaces()).flat().filter(i=>i&&i.family==='IPv4'&&!i.internal).map(i=>i.address);
console.log('\nServidor del equipo de Rumentis encendido.');
if(host==='127.0.0.1'||!ips.length)console.log(`  Dirección (solo esta computadora): http://127.0.0.1:${port}`);
else{console.log('  Escribe esta dirección en la app del jefe (Equipo > Servidor del equipo):');for(const ip of ips)console.log(`    ${ip}:${port}`);}
console.log('  Déjalo abierto mientras trabajan. Para apagarlo: Ctrl+C.\n');
