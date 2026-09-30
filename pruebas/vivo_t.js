// Rumentis Beta: peso con cámara en vivo, sin marca. La cámara se reemplaza por lienzos que dibujan escenas con fotos
// de COCO: una persona lejos (debe pedir "Acércate"), de frente y de costado; y una vaca de costado y de espaldas con
// una persona al lado como referencia. Comprueba que las fotos se tomen solas, que las medidas en cm salgan cerca de
// las de la escena y que la calibración con báscula ajuste el peso.
// Usa 8112 con silueta.onnx, seg.onnx, ort.bundle.js, el .wasm y las fotos: frente.jpg (COCO 000000223959),
// lejos.jpg (000000295478), lado.jpg (000000090062) y atras.jpg (000000467776). SIN_WORKER=1 prueba sin Web Worker.
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const IDI=process.env.IDIOMA||'es';const cfg=CFG0.replace("prueba:false,beta:false,","prueba:false,beta:true,");
const DOS=/2 de 2|2 of 2/;
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const errs=[];
 const ctx=await b.newContext({viewport:{width:393,height:852},deviceScaleFactor:1.5});const p=await ctx.newPage();global.__p=p;
 p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>{const t=m.text();if(m.type()==='error'&&!/favicon|ERR_|net::|Failed to load resource/.test(t))errs.push(t);if(m.type()==='warning'&&/Visión/.test(t))console.log('aviso:',t);});
 await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg}));
 await p.addInitScript(([i,sw])=>{window.VISION_BASE='http://127.0.0.1:8112/';window.__I=i;if(sw)window.Worker=undefined;},[IDI,!!process.env.SIN_WORKER]);
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__I);});await p.reload();await p.waitForTimeout(900);
 await p.evaluate(()=>cargarDemo());await p.waitForTimeout(400);
 // la "cámara": un lienzo vertical (personas) y uno horizontal (ganado) con la escena que pida la prueba.
 // Cada cosa: {f: foto, x: centro (fracción del ancho), y: pie (fracción del alto), h: alto en px, sx: estirar a lo ancho}
 await p.evaluate(async()=>{
   const carga=async f=>{const im=new Image();im.crossOrigin='anonymous';im.src='http://127.0.0.1:8112/'+f;await im.decode();return im;};
   const F={frente:{im:await carga('frente.jpg'),bb:[142,29,175,590]},lejos:{im:await carga('lejos.jpg'),bb:[152,36,196,579]},
     lado:{im:await carga('lado.jpg'),bb:[60,127,359,269]},atras:{im:await carga('atras.jpg'),bb:[269,81,169,299]}};
   const V=document.createElement('canvas');V.width=720;V.height=960;const Hz=document.createElement('canvas');Hz.width=960;Hz.height=720;
   window.__esc={c:'v',cosas:[]};
   function pintar(){const e=window.__esc,c=e.c==='v'?V:Hz,x=c.getContext('2d');x.fillStyle='#c9c3b8';x.fillRect(0,0,c.width,c.height);
     for(const o of e.cosas){const {im,bb}=F[o.f],k=o.h/bb[3],pad=.05*bb[3],sw=bb[2]+2*pad,sh=bb[3]+2*pad,dw=sw*k*(o.sx||1),dh=sh*k;
       x.drawImage(im,bb[0]-pad,bb[1]-pad,sw,sh,o.x*c.width-dw/2,o.y*c.height-dh+pad*k,dw,dh);}}
   setInterval(pintar,100);pintar();
   // uno nuevo cada vez: al cerrar, la pantalla detiene el video
   Fotos.camara=async()=>(window.__esc.c==='v'?V:Hz).captureStream(10);
 });
 const esc=(c,cosas)=>p.evaluate(([c,cosas])=>{window.__esc={c,cosas};},[c,cosas]);
 const num=(txt,re)=>+((txt.match(re)||[])[1]||'0').replace(/,/g,'');
 const leer=async()=>{await p.waitForTimeout(300);const t=await p.$eval('#sheet',e=>e.textContent.replace(/\s+/g,' '));console.log(t.slice(0,300));return t;};
 const paso2=()=>p.waitForFunction(re=>new RegExp(re).test(document.querySelector('.cv-paso')?.textContent||''),DOS.source,{timeout:60000});
 await p.evaluate(()=>{location.hash='#pesocam';});await p.waitForTimeout(600);
 assert.equal(await p.$eval('.pc-modo [aria-pressed="true"]',e=>e.dataset.m),'persona','Personas por defecto');
 assert.ok(!/marca|marker|marcador/i.test(await p.$eval('#app',e=>e.textContent)),'no pide ninguna marca');
 await p.screenshot({path:'vivo_pag.png',fullPage:true});

 /* ---------- personas: 170 cm; en la escena la persona mide 0.7 del alto ---------- */
 const PF={f:'frente',x:.45,y:.85,h:.7*960},PC={...PF,sx:.55};
 await esc('v',[{f:'lejos',x:.45,y:.7,h:.38*960}]);
 const t0=Date.now();await p.click('[data-act="pcVivo"]');
 await p.waitForSelector('#sheet form[data-form="pcAlto"]');await p.fill('#sheet [name=alto]','170');await p.click('#sheet button[type=submit]');
 await p.waitForSelector('dialog.cv');
 await p.waitForFunction(()=>document.querySelector('.cv-chip[data-k="det"]')?.dataset.s==='ok',null,{timeout:120000});
 console.log('primer cuadro',Date.now()-t0,'ms');await p.waitForTimeout(600);
 const m1=await p.$eval('.cv-msg',e=>e.textContent);console.log('lejos:',m1);assert.ok(/Acércate|Come closer|Aproxime/.test(m1),'pide acercarse');
 assert.equal(await p.$eval('.cv-chip[data-k="dist"]',e=>e.dataset.s),'no');assert.ok(!await p.$('.cv-chip[data-k="ref"]'),'sin referencia en personas');
 await p.screenshot({path:'vivo_lejos.png'});
 await esc('v',[PF]);
 await p.waitForFunction(()=>document.querySelector('.cv')?.dataset.estado==='verde',null,{timeout:60000});await p.screenshot({path:'vivo_frente.png'});
 const t1=Date.now();await paso2();
 console.log('frente capturada',Date.now()-t0,'ms · de verde a foto',Date.now()-t1,'ms');
 await p.waitForFunction(()=>!!document.querySelector('.cv-chip[data-k="ang"]')?.dataset.s,null,{timeout:30000});
 console.log('velocidad:',await p.$eval('.cv-fps',e=>e.textContent),await p.$eval('.cv',e=>JSON.stringify(e.dataset)));
 assert.equal(await p.$eval('.cv-chip[data-k="ang"]',e=>e.dataset.s),'no','de frente no sirve como costado');
 await esc('v',[PC]);
 await p.waitForSelector('#sheet .pc-kg',{timeout:90000});console.log('medición completa',Date.now()-t0,'ms');
 let txt=await leer();await p.screenshot({path:'vivo_res.png'});
 assert.ok(!await p.$('dialog.cv'),'la cámara se cierra');assert.equal(await p.$$eval('#sheet .pc-caps img',e=>e.length),2,'dos fotos');
 const fondo=num(txt,/([\d.,]+) cm\s*(de fondo|depth|profundidade)/);
 console.log('de fondo',fondo);assert.ok(fondo>12&&fondo<45,'profundidad razonable');
 assert.ok(!/modelo rápido|fast model|modelo rápido/.test(txt),'medida con el modelo preciso');
 // calibración: "la báscula dice 72 kg" dos veces → el estimado se ajusta
 const kgs=[];
 for(let i=0;i<2;i++){await p.click('#sheet [data-act="pcCalRes"]');await p.waitForSelector('#sheet form[data-form="pcCal"]');
   await p.fill('#sheet [name=kg]',String(Math.round(72*2.20462)));await p.click('#sheet button[type=submit]');await p.waitForTimeout(400);
   kgs.push(await p.evaluate(()=>PesoCam.modeloV('persona')));console.log('modelo',JSON.stringify(kgs[i]));
   if(i===0){await esc('v',[PF]);await p.click('[data-act="pcVivo"]');await paso2();await esc('v',[PC]);await p.waitForSelector('#sheet .pc-kg',{timeout:90000});}}
 assert.equal(kgs[1].n,2);
 const V=await p.evaluate(()=>S.config.pesoCam.cal.filter(c=>c.modo==='persona').map(c=>c.V));
 const k2=await p.evaluate(V=>{const m=PesoCam.modeloV('persona');return m.c*Math.pow(V,m.b);},V[1]);console.log('calibrado',k2);assert.ok(Math.abs(k2-72)<4,'se ajusta a la báscula');
 await p.screenshot({path:'vivo_cal.png',fullPage:true});

 /* ---------- ganado: la vaca mide 200 cm de largo y 150 de alto; la persona de referencia 175 cm ---------- */
 await p.click('[data-act="pcModo"][data-m="ganado"]');await p.waitForTimeout(300);assert.equal(await p.evaluate(()=>localStorage.getItem('rumentis-pc-modo')),'ganado');
 await p.screenshot({path:'vivo_ganado.png',fullPage:true});
 const pxL=.6*960/359*359/200,LADO={f:'lado',x:.38,y:.9,h:269*(.6*960/359)},REF1={f:'frente',x:.83,y:.93,h:175*pxL};
 const pxA=.6*720/150,ATR={f:'atras',x:.4,y:.85,h:.6*720},REF2={f:'frente',x:.72,y:.9,h:175*pxA};
 await esc('h',[LADO]);
 await p.click('[data-act="pcVivo"]');await p.waitForSelector('#sheet form[data-form="pcAlto"]');await p.fill('#sheet [name=alto]','175');await p.click('#sheet button[type=submit]');
 await p.waitForSelector('dialog.cv .cv-chip[data-k="ref"]');
 await p.waitForFunction(()=>document.querySelector('.cv-chip[data-k="det"]')?.dataset.s==='ok',null,{timeout:60000});await p.waitForTimeout(800);
 const m2=await p.$eval('.cv-msg',e=>e.textContent);console.log('sin referencia:',m2);assert.ok(/referencia|reference|referência/.test(m2),'pide la persona de referencia');
 await esc('h',[LADO,REF1]);
 await p.waitForFunction(()=>document.querySelector('.cv')?.dataset.estado==='verde',null,{timeout:60000});await p.screenshot({path:'vivo_vaca.png'});
 await paso2();await esc('h',[ATR,REF2]);
 await p.waitForSelector('#sheet .pc-kg',{timeout:90000});txt=await leer();await p.screenshot({path:'vivo_vaca_res.png'});
 const largo=num(txt,/([\d.,]+) cm\s*(largo|length|comprimento)/),altoV=num(txt,/([\d.,]+) cm\s*(alto|height|altura)(?=[\d\s]|$)/);
 console.log('largo',largo,'alto',altoV);assert.ok(largo>170&&largo<230,'largo cerca de 200 cm');assert.ok(altoV>125&&altoV<175,'alto cerca de 150 cm');
 await p.click('#sheet [data-act="pcAgregar"]');await p.waitForTimeout(300);assert.equal(await p.$$eval('.pc-sesion .row',e=>e.length),1,'agregado al pesaje');
 const n0=await p.evaluate(()=>allItems().filter(i=>i.metodo==='camara').length);await p.click('[data-act="pcGuardar"]');await p.waitForTimeout(300);
 assert.equal(await p.evaluate(()=>allItems().filter(i=>i.metodo==='camara').length),n0+1,'pesaje guardado');
 if(IDI==='xx'){const K=[...await p.evaluate(()=>[...I18N_REC.keys()])];fs.writeFileSync('claves_vivo.json',JSON.stringify(K.map(k=>[k,1])));console.log('claves',K.length);}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');await b.close();if(errs.length)process.exit(1);
})().catch(async e=>{console.error('FALLA',e.message);
 try{const p=global.__p;if(p)console.error('estado:',await p.evaluate(()=>{const c=document.querySelector('.cv');return c?JSON.stringify({...c.dataset,msg:c.querySelector('.cv-msg').textContent,paso:c.querySelector('.cv-paso').textContent,chips:[...c.querySelectorAll('.cv-chip')].map(x=>x.dataset.k+':'+x.dataset.s).join(' ')}):'sin cámara abierta';}));if(p)await p.screenshot({path:'vivo_falla.png'});}catch(e2){}
 process.exit(1);});
