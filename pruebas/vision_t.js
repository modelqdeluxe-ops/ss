const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const p=await b.newPage({viewport:{width:760,height:1200}});const errs=[];p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>{if(m.type()==='error')errs.push(m.text());});
 await p.goto('http://127.0.0.1:8112/prueba.html');const t0=Date.now();await p.evaluate(()=>Vision.cargar());console.log('carga',Date.now()-t0,'ms');
 for(const f of ['v1.jpg','v2.jpg'])console.log(f,JSON.stringify(await p.evaluate(f=>probar(f),f)));
 await p.screenshot({path:'vision.png',fullPage:true});console.log(errs.join('\n')||'sin errores');await b.close();})();
