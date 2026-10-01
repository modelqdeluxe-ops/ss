// Rumentis Beta: tres teléfonos enlazados para el peso con cámara (WebRTC directo, enlace por código).
// Tres páginas en el mismo navegador: la administración (Rumentis Beta, de costado con la persona de referencia), una
// cámara por detrás (otro teléfono con Rumentis Beta) y una del otro costado (Rumentis Equipo Beta). Las cámaras son
// lienzos con escenas de COCO (como vivo_t.js). Comprueba: el enlace con los códigos, que la administración espere a
// que las otras cámaras estén listas, que un solo disparo tome las tres vistas a la vez (sin pedir el paso "por detrás"
// en este teléfono), que las fotos lleguen y se midan, que el peso use el otro costado y que el resultado llegue a las
// cámaras enlazadas.
// Servidores: 8111 (app/assets) y 8112 (visión y fotos, ver LEEME.md).
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const IDI=process.env.IDIOMA||'es';
const cfgDe=app=>CFG0.replace(/window\.RUMENTIS=\{app:'[a-z]+',prueba:(true|false),beta:(true|false),/,`window.RUMENTIS={app:'${app}',prueba:false,beta:true,`);
(async()=>{
 const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
   args:['--disable-features=WebRtcHideLocalIpsWithMdns','--use-fake-ui-for-media-stream','--use-fake-device-for-media-stream']});
 const errs=[];global.__errs=errs;
 async function telefono(nombre,app){
   const ctx=await b.newContext({viewport:{width:393,height:852},deviceScaleFactor:1.5,permissions:['camera']});const p=await ctx.newPage();
   p.on('pageerror',e=>errs.push(nombre+': '+e.message));
   p.on('console',m=>{const t=m.text();if(m.type()==='error'&&!/favicon|ERR_|net::|Failed to load resource/.test(t))errs.push(nombre+': '+t);});
   await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfgDe(app)}));
   await p.addInitScript(i=>{window.VISION_BASE='http://127.0.0.1:8112/';window.__I=i;},IDI);
   await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__I);});
   await p.reload();await p.waitForTimeout(900);
   if(app==='jefe'){await p.evaluate(()=>cargarDemo());await p.waitForTimeout(400);}
   // la "cámara": un lienzo horizontal con la escena que pida la prueba (fx: en espejo)
   await p.evaluate(async()=>{
     const carga=async f=>{const im=new Image();im.crossOrigin='anonymous';im.src='http://127.0.0.1:8112/'+f;await im.decode();return im;};
     const F={frente:{im:await carga('frente.jpg'),bb:[142,29,175,590]},lado:{im:await carga('lado.jpg'),bb:[60,127,359,269]},atras:{im:await carga('atras.jpg'),bb:[269,81,169,299]}};
     const Hz=document.createElement('canvas');Hz.width=960;Hz.height=720;window.__esc={cosas:[]};
     function pintar(){const x=Hz.getContext('2d');x.setTransform(1,0,0,1,0,0);x.fillStyle='#c9c3b8';x.fillRect(0,0,960,720);
       for(const o of window.__esc.cosas){const {im,bb}=F[o.f],k=o.h/bb[3],pad=.05*bb[3],sw=bb[2]+2*pad,sh=bb[3]+2*pad,dw=sw*k,dh=sh*k,cx=o.x*960;
         x.save();if(o.fx){x.translate(cx,0);x.scale(-1,1);x.translate(-cx,0);}x.drawImage(im,bb[0]-pad,bb[1]-pad,sw,sh,cx-dw/2,o.y*720-dh+pad*k,dw,dh);x.restore();}}
     setInterval(pintar,100);pintar();Fotos.camara=async()=>Hz.captureStream(10);
   });
   return p;
 }
 const esc=(p,cosas)=>p.evaluate(c=>{window.__esc={cosas:c};},cosas);
 const A=await telefono('admin','jefe'),B=await telefono('atrás','jefe'),C=await telefono('otro','vaquero');global.__p=A;
 // escenas: la vaca mide 200 cm de largo y 150 de alto; la persona de referencia 175 cm
 const pxL=.6*960/359*359/200,LADO={f:'lado',x:.38,y:.9,h:269*(.6*960/359)},REF1={f:'frente',x:.83,y:.93,h:175*pxL};
 const pxA=.6*720/150,ATR={f:'atras',x:.4,y:.85,h:.6*720},REF2={f:'frente',x:.72,y:.9,h:175*pxA};
 await esc(A,[LADO,REF1]);await esc(B,[ATR,REF2]);await esc(C,[{...LADO,x:.6,fx:true}]);

 /* ---------- la página de ganado muestra los teléfonos enlazados ---------- */
 await A.evaluate(()=>{location.hash='#pesocam';});await A.waitForTimeout(500);
 await A.click('[data-act="pcModo"][data-m="ganado"]');await A.waitForTimeout(400);
 assert.ok(await A.$('.en-card'),'tarjeta de teléfonos enlazados');
 assert.equal(await A.$$eval('.en-roles [data-act="enEnlazar"]',e=>e.length),3,'tres roles para enlazar');
 await A.screenshot({path:'enlace_pag.png',fullPage:true});
 // la hoja de enlace muestra el código QR del rol
 await A.click('.en-roles [data-act="enEnlazar"][data-r="arriba"]');await A.waitForSelector('#sheet .en-qr svg',{timeout:20000});
 await A.screenshot({path:'enlace_hoja.png'});await A.click('#sheet [data-act="cerrar"]');await A.waitForTimeout(300);await A.evaluate(()=>Enlace.soltar('arriba'));

 /* ---------- enlace con los códigos: A ofrece, B y C responden ---------- */
 const enlazar=async(G,rol)=>{
   const t=await A.evaluate(r=>Enlace.ofertar(r),rol);assert.ok(/^RCAM1\.[zj]/.test(t),'código del enlace');console.log(rol,'código',t.length,'caracteres');
   await G.evaluate(t=>{window.__u=Enlace.unirseCon(t);},t);
   await G.waitForFunction(()=>Enlace.INV.p&&Enlace.INV.p.respuesta,null,{timeout:20000});
   const r=await G.evaluate(()=>Enlace.INV.p.respuesta);
   assert.ok(await G.$('#sheet .en-qr svg'),'muestra su código para la administración');
   const ok=await A.evaluate(r=>Enlace.responder(r),r);assert.ok(ok.ok,'la administración acepta la respuesta: '+JSON.stringify(ok));
   await A.waitForFunction(rol=>Enlace.conectados().some(p=>p.rol===rol),rol,{timeout:30000});
   // el otro teléfono abre su cámara enlazada sola
   await G.waitForSelector('dialog.cv',{timeout:30000});
 };
 // un código que no es de respuesta no enlaza
 assert.equal((await A.evaluate(()=>Enlace.responder('hola'))).ok,false);
 await enlazar(B,'atras');await enlazar(C,'otro');
 assert.deepEqual((await A.evaluate(()=>Enlace.conectados().map(p=>p.rol))).sort(),['atras','otro']);
 // las cámaras enlazadas siguen al animal y le dicen a la administración cuando están listas
 await A.waitForFunction(()=>Enlace.listas(),null,{timeout:120000});
 const sub=await B.$eval('.cv-sub',e=>e.textContent);console.log('cámara por detrás:',sub.slice(0,80));
 await B.screenshot({path:'enlace_atras.png'});await C.screenshot({path:'enlace_otro.png'});

 /* ---------- un disparo, tres vistas ---------- */
 await A.evaluate(()=>{location.hash='#pesocam';});await A.waitForTimeout(300);
 await A.click('[data-act="pcVivo"]');await A.waitForSelector('#sheet form[data-form="pcAlto"]');await A.fill('#sheet [name=alto]','175');await A.click('#sheet button[type=submit]');
 await A.waitForSelector('dialog.cv');
 // la administración no pasa al paso "por detrás": las fotos de los otros teléfonos llegan solas
 let vioPaso2=false;const vig=setInterval(async()=>{try{const t=await A.$eval('.cv-paso',e=>e.textContent);if(/2 de 2|2 of 2|2 de 2/.test(t)&&!/Recib|Receiv/.test(await A.$eval('.cv-msg',e=>e.textContent)))vioPaso2=true;}catch(e){}},300);
 await A.waitForSelector('#sheet .pc-kg',{timeout:180000});clearInterval(vig);
 const txt=await A.$eval('#sheet',e=>e.textContent.replace(/\s+/g,' '));console.log(txt.slice(0,260));await A.screenshot({path:'enlace_res.png'});
 const G=await A.evaluate(()=>{const r=PesoCam.REG().slice(-1)[0];return {pred:r.pred,kg:r.kg,dims:r.dims,camaras:r.camaras,remotas:r.remotas,tipo:r.tipo,etapa:r.etapa,v:r.v};});
 console.log('enlazado',JSON.stringify(G));
 assert.ok(!vioPaso2,'no pidió la toma por detrás en este teléfono');
 assert.deepEqual(G.camaras,['costado','atras','otro'],'usó las tres cámaras');assert.deepEqual([...G.remotas].sort(),['atras','otro']);
 assert.ok(G.dims.WH>100&&G.dims.WH<170,'alzada de un bovino');assert.ok(isFinite(G.dims.WHr)&&G.dims.WHr>100,'alzada por detrás (de la cámara enlazada)');
 assert.ok(G.dims.qOtro>.42&&G.dims.qOtro<.65,'fondo de pecho del otro costado');assert.ok(G.pred>150&&G.pred<900,'peso de un bovino');
 assert.ok(/Cámaras|Cameras|Câmeras/.test(txt)||await A.$('#sheet details.pc-exp'),'explica las cámaras');
 // el resultado llega a las cámaras enlazadas
 await B.waitForFunction(()=>/lb|kg/.test(document.querySelector('.cv-msg')?.textContent||''),null,{timeout:15000});
 console.log('en la cámara por detrás:',await B.$eval('.cv-msg',e=>e.textContent));

 /* ---------- si se cae un teléfono, la administración sigue sola ---------- */
 await C.close();await A.waitForFunction(()=>!Enlace.conectados().some(p=>p.rol==='otro'),null,{timeout:40000});
 await A.click('#sheet [data-act="cerrar"]');await A.waitForTimeout(300);
 assert.deepEqual(await A.evaluate(()=>Enlace.conectados().map(p=>p.rol)),['atras']);
 await A.evaluate(()=>Enlace.soltarTodo());await A.waitForTimeout(500);
 await B.waitForFunction(()=>!document.querySelector('dialog.cv'),null,{timeout:15000});
 if(IDI==='xx'){const K=[];for(const p of [A,B])K.push(...await p.evaluate(()=>[...I18N_REC.keys()]));fs.writeFileSync('claves_enlace.json',JSON.stringify([...new Set(K)].map(k=>[k,1])));console.log('claves',K.length);}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');await b.close();if(errs.length)process.exit(1);
})().catch(async e=>{console.error('FALLA',e.message);if(global.__errs&&global.__errs.length)console.error('errores:',global.__errs.join('\n'));
 try{await global.__p.screenshot({path:'enlace_falla.png'});}catch(e2){}process.exit(1);});
