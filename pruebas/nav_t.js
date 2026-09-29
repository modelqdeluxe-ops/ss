const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});
 for(const w of [360,393,412]){const p=await b.newPage({viewport:{width:w,height:800},deviceScaleFactor:2});
  await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');});await p.reload();await p.waitForTimeout(800);
  await p.evaluate(()=>cargarDemo());await p.waitForTimeout(600);
  const m=await p.evaluate(()=>[...document.querySelectorAll('nav.bottom [data-nav]')].map(a=>{const s=a.querySelector('span:last-child'),i=a.querySelector('svg');return a.dataset.nav+':'+Math.round(a.getBoundingClientRect().width)+'/'+Math.round(s.scrollWidth)+'/'+Math.round(i.getBoundingClientRect().width)+'/'+getComputedStyle(s).fontSize;}).join(' '));
  console.log(w,m);const n=await p.$('nav.bottom');await n.screenshot({path:`nav_${w}.png`});await p.close();}
 await b.close();})();
