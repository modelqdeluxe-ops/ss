const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome',args:['--use-fake-ui-for-media-stream','--use-fake-device-for-media-stream']});const errs=[];
 for(const [w,h,n] of [[393,852,'v'],[852,393,'h']]){const ctx=await b.newContext({viewport:{width:w,height:h},deviceScaleFactor:1.5,permissions:['camera']});const p=await ctx.newPage();p.on('pageerror',e=>errs.push(e.message));
  await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma','es');});await p.reload();await p.waitForTimeout(800);
  p.evaluate(()=>Fotos.tomar('Evidencia · Revisar bebederos',{soloCamara:true}));await p.waitForTimeout(2500);await p.screenshot({path:'cam_'+n+'.png'});
  const r=await p.evaluate(()=>{const d=document.querySelector('.cam-disp').getBoundingClientRect();return [d.x+d.width/2,d.y+d.height/2,innerWidth,innerHeight];});console.log(n,r);}
 console.log(errs.join('\n')||'sin errores');await b.close();})();
