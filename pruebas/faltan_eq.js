// de las claves grabadas en modo xx, las que faltan en el catálogo en inglés y portugués
const {chromium}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const p=await b.newPage();
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.setItem('rumentis-idioma','es');});await p.reload();await p.waitForTimeout(1000);
 const rec=JSON.parse(fs.readFileSync(process.argv[2]||'claves_equipo.json','utf8')).map(x=>x[0]);
 const r=await p.evaluate(rec=>{const out=[];for(const L of ['en','pt']){const D=I18N_DIC[L];for(const k of rec){if(D[k]!=null)continue;if(!/[A-Za-zÀ-ÿ]{2}/.test(k.replace(/\{\d+\}/g,'')))continue;
   const P=k.split(/(?<=[.:;!?])\s+/);if(P.length>1&&P.every(x=>D[x]!=null||!/[A-Za-zÀ-ÿ]/.test(x.replace(/\{\d+\}/g,''))))continue;out.push(L+'\t'+k);}}return out;},rec);
 const en=r.filter(x=>x.startsWith('en\t')).map(x=>x.slice(3)),pt=r.filter(x=>x.startsWith('pt\t')).map(x=>x.slice(3));
 fs.writeFileSync('faltan_equipo.txt',en.join('\n'));console.log('faltan en',en.length,'pt',pt.length,'solo pt',pt.filter(x=>!en.includes(x)).length);await b.close();})();
