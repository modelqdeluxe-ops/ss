const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});
 for(const tema of ['light','dark']){const p=await b.newPage({viewport:{width:400,height:860}});const errs=[];p.on('pageerror',e=>errs.push(e.message));
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(t=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-tema',t);},tema);await p.reload();await p.waitForTimeout(1500);
 await p.evaluate(()=>{const s=document.querySelector('#splash');if(s)s.remove();document.querySelector('#app').style.visibility='hidden';FondoVivo.viento(.8);});
 for(const k of [0,1]){await p.waitForTimeout(k?2500:500);await p.screenshot({path:`sc_fondo_${tema}_${k}.png`});}
 const perf=await p.evaluate(async()=>{let n=0;const t0=performance.now();await new Promise(r=>{function f(){n++;if(performance.now()-t0<2000)requestAnimationFrame(f);else r();}requestAnimationFrame(f);});return {fps:n/2,estado:FondoVivo.estado()};});
 console.log(tema,JSON.stringify(perf),'errores',errs);await p.close();}
 await b.close();})();
