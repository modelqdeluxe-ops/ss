// Servidor de relevo en tu computadora (para probar las apps): npm run local  → http://127.0.0.1:8790
import {Miniflare} from 'miniflare';
import {readFileSync} from 'node:fs';
const port=+process.env.PUERTO||8790;
const mf=new Miniflare({modules:true,scriptPath:'worker.js',d1Databases:['DB'],compatibilityDate:'2025-07-01',host:'127.0.0.1',port});
const db=await mf.getD1Database('DB');
for(const s of readFileSync('schema.sql','utf8').replace(/--.*$/gm,'').split(';').map(x=>x.trim()).filter(Boolean))await db.prepare(s).run();
console.log('Servidor del equipo en '+(await mf.ready));
