// Rumentis Beta: peso con cámara, ganado con una sola foto (el modo en vivo se prueba en vivo_t.js). Foto sintética: un novillo de costado con la marca de 16 cm pegada al lado.
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const IDI=process.env.IDIOMA||'es';const cfg=CFG0.replace("prueba:false,beta:false,","prueba:false,beta:true,");
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const errs=[];
 const ctx=await b.newContext({viewport:{width:393,height:852},deviceScaleFactor:1.5});const p=await ctx.newPage();
 p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>{if(m.type()==='error'&&!/favicon|ERR_|net::|Failed to load resource/.test(m.text()))errs.push(m.text());});
 await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg}));
 await p.addInitScript(i=>{window.VISION_BASE='http://127.0.0.1:8112/';window.__I=i;},IDI);
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__I);localStorage.setItem('rumentis-pc-modo','ganado');});await p.reload();await p.waitForTimeout(900);
 await p.evaluate(()=>cargarDemo());await p.waitForTimeout(400);
 assert.ok(await p.$('.pc-hoy'),'tarjeta en Hoy');
 // foto sintética: la vaca (640 px) llevada a 1600 px con la marca pegada; la marca mide 16 cm = 96 px → 6 px/cm
 await p.evaluate(async()=>{
   await new Promise((ok,mal)=>{const s=document.createElement('script');s.src='http://127.0.0.1:8112/cv.js';s.onload=()=>{const t=document.createElement('script');t.src='http://127.0.0.1:8112/aruco.js';t.onload=ok;t.onerror=mal;document.head.appendChild(t);};document.head.appendChild(s);});
   const im=new Image();im.crossOrigin='anonymous';im.src='http://127.0.0.1:8112/lado.jpg';await im.decode();
   const c=document.createElement('canvas');c.width=1600;c.height=Math.round(im.height*1600/im.width);const x=c.getContext('2d');x.drawImage(im,0,0,c.width,c.height);
   const bits=new AR.Dictionary('ARUCO_MIP_36h12').codeList[7].split('').map(Number),u=12,x0=40,y0=40;   // 8u = 96 px
   x.fillStyle='#fff';x.fillRect(x0-u,y0-u,10*u,10*u);x.fillStyle='#000';x.fillRect(x0,y0,8*u,8*u);x.fillStyle='#fff';bits.forEach((b,i)=>{if(b)x.fillRect(x0+u+(i%6)*u,y0+u+Math.floor(i/6)*u,u,u);});
   window.__foto=await new Promise(r=>c.toBlob(r,'image/jpeg',.9));
   Fotos.tomar=async()=>window.__foto;});
 await p.evaluate(()=>{location.hash='#pesocam';});await p.waitForTimeout(600);await p.screenshot({path:'beta_pag.png',fullPage:true});
 const t0=Date.now();await p.click('[data-act="pcFoto"]');
 await p.waitForSelector('#sheet .pc-kg, #sheet .pc-res',{timeout:60000});await p.waitForTimeout(300);console.log('análisis',Date.now()-t0,'ms');
 const txt=await p.$eval('#sheet',e=>e.textContent);console.log(txt.replace(/\s+/g,' ').slice(0,300));
 await p.screenshot({path:'beta_res.png'});
 assert.ok(await p.$('#sheet .pc-kg'),'peso estimado');
 const r=await p.evaluate(async()=>{const x=await PesoCam.analizar(window.__foto);return {pxcm:x.marca&&x.marca.pxcm,med:x.med,kg:x.est&&x.est.kg};});console.log(JSON.stringify(r));
 assert.ok(Math.abs(r.pxcm-6)<0.3,'la marca da 6 px/cm');
 await p.click('#sheet [data-act="pcAgregar"]');await p.waitForTimeout(400);
 await p.click('[data-act="pcFoto"]');await p.waitForSelector('#sheet .pc-kg',{timeout:60000});await p.click('#sheet [data-act="pcAgregar"]');await p.waitForTimeout(400);
 assert.equal(await p.$$eval('.pc-sesion .row',e=>e.length),2);await p.screenshot({path:'beta_sesion.png',fullPage:true});
 const n0=await p.evaluate(()=>allItems().filter(i=>i.metodo==='camara').length);await p.click('[data-act="pcGuardar"]');await p.waitForTimeout(400);
 assert.equal(await p.evaluate(()=>allItems().filter(i=>i.metodo==='camara').length),n0+1,'pesaje guardado');
 // calibración: la báscula dice 520 kg
 for(const kg of [520,515]){await p.click('[data-act="pcFoto"]');await p.waitForSelector('#sheet .pc-kg',{timeout:60000});await p.click('#sheet [data-act="pcCalRes"]');await p.waitForSelector('#sheet form[data-form="pcCal"]',{timeout:60000});await p.fill('#sheet [name=kg]',String(Math.round(kg*2.20462)));await p.click('#sheet button[type=submit]');await p.waitForTimeout(400);}
 const m=await p.evaluate(()=>PesoCam.modelo());console.log('modelo',JSON.stringify(m));
 const e2=await p.evaluate(async()=>{const x=await PesoCam.analizar(window.__foto);return x.est.kg;});console.log('calibrado',e2);assert.ok(Math.abs(e2-517.5)<15,'se ajusta a la báscula');
 await p.screenshot({path:'beta_cal.png',fullPage:true});
 // el formulario de pesaje ofrece la cámara
 await p.evaluate(()=>FORMS.pesaje({}));await p.waitForTimeout(300);assert.ok(await p.$('#sheet [data-act="pcIr"]'),'botón en el pesaje');
 if(IDI==='xx'){const K=[...await p.evaluate(()=>[...I18N_REC.keys()])];fs.writeFileSync('claves_beta.json',JSON.stringify(K.map(k=>[k,1])));console.log('claves',K.size||K.length);}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');await b.close();
})().catch(e=>{console.error('FALLA',e.message);process.exit(1);});
