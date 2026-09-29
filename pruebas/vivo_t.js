// Rumentis Beta: cámara en vivo con personas. La cámara se reemplaza por un lienzo que dibuja escenas: una persona
// lejos (debe pedir "Acércate"), de frente junto a la marca y de costado. Comprueba que las dos fotos se tomen solas,
// que la estatura salga cerca de la real (la escena se arma a 170 cm) y que la calibración con báscula ajuste el peso.
// Usa 8112 con cv.js, aruco.js, seg.onnx, ort.bundle.js, el .wasm, frente.jpg (COCO 000000223959) y lejos.jpg
// (COCO 000000295478). SIN_WORKER=1 prueba el camino sin Web Worker.
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const IDI=process.env.IDIOMA||'es';const cfg=CFG0.replace("prueba:false,beta:false,","prueba:false,beta:true,");
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const errs=[];
 const ctx=await b.newContext({viewport:{width:393,height:852},deviceScaleFactor:1.5});const p=await ctx.newPage();
 p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>{const t=m.text();if(m.type()==='error'&&!/favicon|ERR_|net::|Failed to load resource/.test(t))errs.push(t);if(m.type()==='warning'&&/Visión/.test(t))console.log('aviso:',t);});
 await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg}));
 await p.addInitScript(([i,sw])=>{window.VISION_BASE='http://127.0.0.1:8112/';window.__I=i;if(sw)window.Worker=undefined;},[IDI,!!process.env.SIN_WORKER]);
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__I);});await p.reload();await p.waitForTimeout(900);
 await p.evaluate(()=>cargarDemo());await p.waitForTimeout(400);
 // la "cámara": un lienzo de 720×960 con la escena que pida la prueba
 await p.evaluate(async()=>{
   await new Promise((ok,mal)=>{const s=document.createElement('script');s.src='http://127.0.0.1:8112/cv.js';s.onload=()=>{const t=document.createElement('script');t.src='http://127.0.0.1:8112/aruco.js';t.onload=ok;t.onerror=mal;document.head.appendChild(t);};document.head.appendChild(s);});
   const carga=async f=>{const im=new Image();im.crossOrigin='anonymous';im.src='http://127.0.0.1:8112/'+f;await im.decode();return im;};
   const F={frente:{im:await carga('frente.jpg'),bb:[142,29,175,590]},lejos:{im:await carga('lejos.jpg'),bb:[152,36,196,579]}};
   const bits=new AR.Dictionary('ARUCO_MIP_36h12').codeList[7].split('').map(Number);
   const c=document.createElement('canvas');c.width=720;c.height=960;const x=c.getContext('2d');
   window.__esc={f:'frente',alto:.7,ancho:1,marca:true};
   function pintar(){const e=window.__esc,{im,bb}=F[e.f],H=e.alto*c.height,k=H/bb[3],pad=.06*bb[3];
     x.fillStyle='#c9c3b8';x.fillRect(0,0,c.width,c.height);
     const sx=bb[0]-pad,sy=bb[1]-pad,sw=bb[2]+2*pad,sh=bb[3]+2*pad,dw=sw*k*e.ancho,dh=sh*k,dx=c.width*.4-dw/2,dy=(c.height-dh)/2;
     x.drawImage(im,sx,sy,sw,sh,dx,dy,dw,dh);
     if(e.marca){const pxcm=H/170,u=16*pxcm/8,mx=dx+dw+14,my=dy+pad*k+.22*H;   // 16 cm a la altura del pecho
       x.fillStyle='#fff';x.fillRect(mx-u,my-u,10*u,10*u);x.fillStyle='#000';x.fillRect(mx,my,8*u,8*u);x.fillStyle='#fff';bits.forEach((b,i)=>{if(b)x.fillRect(mx+u+(i%6)*u,my+u+Math.floor(i/6)*u,u,u);});}
   }
   setInterval(pintar,100);pintar();
   Fotos.camara=async()=>c.captureStream(10);   // uno nuevo cada vez: al cerrar, la pantalla detiene el videowindow.__lienzo=c;
 });
 await p.evaluate(()=>{location.hash='#pesocam';});await p.waitForTimeout(600);
 assert.equal(await p.$eval('.pc-modo [aria-pressed="true"]',e=>e.dataset.m),'persona','Personas por defecto');
 await p.screenshot({path:'vivo_pag.png',fullPage:true});
 // 1) lejos: debe pedir que se acerque
 await p.evaluate(()=>{window.__esc={f:'lejos',alto:.38,ancho:1,marca:true};});
 const t0=Date.now();await p.click('[data-act="pcVivo"]');
 await p.waitForSelector('dialog.cv');
 await p.waitForFunction(()=>document.querySelector('.cv-chip[data-k="det"]')?.dataset.s==='ok',null,{timeout:120000});
 console.log('primer cuadro',Date.now()-t0,'ms');
 await p.waitForTimeout(300);
 const m1=await p.$eval('.cv-msg',e=>e.textContent);console.log('lejos:',m1);assert.ok(/Acércate|Come closer|Aproxime/.test(m1),'pide acercarse');
 assert.equal(await p.$eval('.cv-chip[data-k="dist"]',e=>e.dataset.s),'no');
 await p.screenshot({path:'vivo_lejos.png'});
 // 2) de frente, bien: se toma sola
 await p.evaluate(()=>{window.__esc={f:'frente',alto:.7,ancho:1,marca:true};});
 await p.waitForFunction(()=>document.querySelector('.cv-chip[data-k="marca"]')?.dataset.s==='ok'&&document.querySelector('.cv')?.dataset.estado==='verde',null,{timeout:60000});
 await p.screenshot({path:'vivo_frente.png'});
 await p.waitForFunction(()=>/2 de 2|2 of 2/.test(document.querySelector('.cv-paso')?.textContent||''),null,{timeout:60000});
 console.log('frente capturada',Date.now()-t0,'ms');
 // en el segundo paso, de frente otra vez: el ángulo no sirve
 await p.waitForFunction(()=>!!document.querySelector('.cv-chip[data-k="ang"]')?.dataset.s,null,{timeout:30000});
 console.log('velocidad:',await p.$eval('.cv-fps',e=>e.textContent),await p.$eval('.cv',e=>JSON.stringify(e.dataset)));
 const E2=await p.evaluate(()=>({ang:document.querySelector('.cv-chip[data-k="ang"]').dataset.s,msg:document.querySelector('.cv-msg').textContent}));console.log('paso 2 de frente:',JSON.stringify(E2));
 assert.equal(E2.ang,'no','de frente no sirve como costado');
 // 3) de costado (la misma persona más angosta)
 await p.evaluate(()=>{window.__esc={f:'frente',alto:.7,ancho:.55,marca:true};});
 await p.waitForSelector('#sheet .pc-kg',{timeout:90000});console.log('medición completa',Date.now()-t0,'ms');
 await p.waitForTimeout(400);
 const txt=await p.$eval('#sheet',e=>e.textContent.replace(/\s+/g,' '));console.log(txt.slice(0,260));
 await p.screenshot({path:'vivo_res.png'});
 assert.ok(!await p.$('dialog.cv'),'la cámara se cierra');
 assert.equal(await p.$$eval('#sheet .pc-caps img',e=>e.length),2,'dos fotos');
 const est=+((txt.match(/([\d.,]+) cm\s*(estatura|height|altura)/)||[])[1]||'0').replace(',','');
 console.log('estatura',est);assert.ok(est>150&&est<190,'estatura cerca de 170 cm');
 // 4) calibración: "la báscula dice 72 kg" dos veces → el estimado se ajusta
 const kgs=[];
 for(const kg of [72,72]){await p.click('#sheet [data-act="pcCalRes"]');await p.waitForSelector('#sheet form[data-form="pcCal"]');
   await p.fill('#sheet [name=kg]',String(Math.round(kg*2.20462)));await p.click('#sheet button[type=submit]');await p.waitForTimeout(400);
   const m=await p.evaluate(()=>PesoCam.modeloV('persona'));kgs.push(m);console.log('modelo',JSON.stringify(m));
   if(kgs.length<2){await p.evaluate(()=>{window.__esc={f:'frente',alto:.7,ancho:1,marca:true};});await p.click('[data-act="pcVivo"]');
     await p.waitForFunction(()=>/2 de 2|2 of 2/.test(document.querySelector('.cv-paso')?.textContent||''),null,{timeout:60000});
     await p.evaluate(()=>{window.__esc={f:'frente',alto:.7,ancho:.55,marca:true};});await p.waitForSelector('#sheet .pc-kg',{timeout:90000});}}
 assert.equal(kgs[1].n,2);
 const V=await p.evaluate(()=>S.config.pesoCam.cal.filter(c=>c.modo==='persona').map(c=>c.V));
 const k2=await p.evaluate(V=>{const m=PesoCam.modeloV('persona');return m.c*Math.pow(V,m.b);},V[1]);console.log('calibrado',k2);assert.ok(Math.abs(k2-72)<4,'se ajusta a la báscula');
 await p.screenshot({path:'vivo_cal.png',fullPage:true});
 // 5) modo ganado: se cambia y se recuerda
 await p.click('[data-act="pcModo"][data-m="ganado"]');await p.waitForTimeout(300);
 assert.ok(await p.$('[data-act="pcFoto"]'),'una sola foto en ganado');assert.equal(await p.evaluate(()=>localStorage.getItem('rumentis-pc-modo')),'ganado');
 await p.screenshot({path:'vivo_ganado.png',fullPage:true});
 if(IDI==='xx'){const K=[...await p.evaluate(()=>[...I18N_REC.keys()])];fs.writeFileSync('claves_vivo.json',JSON.stringify(K.map(k=>[k,1])));console.log('claves',K.length);}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');await b.close();if(errs.length)process.exit(1);
})().catch(e=>{console.error('FALLA',e.message);process.exit(1);});
