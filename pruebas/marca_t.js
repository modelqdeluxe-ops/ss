const {chromium}=require('playwright');const fs=require('fs');
const cfg=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8').replace("prueba:false,beta:false,","prueba:false,beta:true,");
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const p=await b.newPage();const errs=[];p.on('pageerror',e=>errs.push(e.message));
 await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg}));await p.addInitScript(()=>{window.VISION_BASE='http://127.0.0.1:8112/';});
 await p.goto('http://127.0.0.1:8111/index.html#pesocam');await p.evaluate(()=>{localStorage.setItem('rumentis-bienvenida','1');});await p.reload();await p.waitForTimeout(1500);
 const n=await p.evaluate(async()=>{let buf=null;Documentos.enviar=async(n,b)=>{buf=b;return true;};await ACTS.pcMarcaPdf();return buf?buf.byteLength:0;});
 console.log('pdf bytes',n,'marca bits',await p.$$eval('.pc-marca rect',e=>e.length));
 const pdf=await p.evaluate(async()=>{let buf;Documentos.enviar=async(n,b)=>{buf=b;return true;};await ACTS.pcMarcaPdf();return Array.from(new Uint8Array(buf));});fs.writeFileSync('marca.pdf',Buffer.from(pdf));
 console.log(errs.join('\n')||'sin errores');await b.close();})();
