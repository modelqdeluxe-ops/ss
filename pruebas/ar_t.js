// Rumentis Beta con ARCore, sin teléfono: el puente nativo "Ar" se reemplaza por uno que entrega lo mismo que
// ArActivity (foto, profundidad en mm, intrínsecos, distancia a la mira, altura sobre el suelo e inclinación) con escenas
// de fotos de COCO. Comprueba que la página mida la foto con los modelos y saque la escala de la profundidad:
// - prueba con persona por ARCore: la estatura medida contra la real (la profundidad se elige para que la escena mida
//   175 cm): error < 4 %; también con el suelo de ARCore (altura e inclinación coherentes con la escena);
// - prueba con persona con el sensor de movimiento (acelerómetro simulado): da una estatura;
// - ganado con ARCore: 3 tomas, escala "ar-prof", medidas y peso de un bovino; queda en el registro y el Excel.
// Usa 8111 y 8112 (los modelos y las fotos de vivo_t.js).
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');const {execFileSync}=require('child_process');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const IDI=process.env.IDIOMA||'es';const cfg=CFG0.replace("prueba:false,beta:false,","prueba:false,beta:true,");
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const errs=[];
 const p=await (await b.newContext({viewport:{width:393,height:852},deviceScaleFactor:1.5})).newPage();
 p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>{const t=m.text();if(m.type()==='error'&&!/favicon|ERR_|net::|Failed to load resource/.test(t))errs.push(t);});
 await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg}));
 await p.addInitScript(i=>{window.VISION_BASE='http://127.0.0.1:8112/';window.__I=i;},IDI);
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__I);});await p.reload();await p.waitForTimeout(900);
 const paso=t=>console.log('·',t);
 // (la finca de muestra al principio: cargarla después borraría las pruebas)
 await p.evaluate(()=>cargarDemo());await p.waitForTimeout(400);
 // las escenas y el ARCore de prueba
 await p.evaluate(async()=>{
   const carga=async f=>{const im=new Image();im.crossOrigin='anonymous';im.src='http://127.0.0.1:8112/'+f;await im.decode();return im;};
   const F={frente:{im:await carga('frente.jpg'),bb:[142,29,175,590]},lado:{im:await carga('lado.jpg'),bb:[60,127,359,269]},atras:{im:await carga('atras.jpg'),bb:[269,81,169,299]}};
   // dibuja una cosa con su pie en (x, y) (fracciones) y h px de alto; devuelve el lienzo
   window.__escena=(W,H,o)=>{const c=document.createElement('canvas');c.width=W;c.height=H;const x=c.getContext('2d');x.fillStyle='#c9c3b8';x.fillRect(0,0,W,H);
     const {im,bb}=F[o.f],k=o.h/bb[3],pad=.05*bb[3],sw=bb[2]+2*pad,sh=bb[3]+2*pad,dw=sw*k,dh=sh*k;x.drawImage(im,bb[0]-pad,bb[1]-pad,sw,sh,o.x*W-dw/2,o.y*H-dh+pad*k,dw,dh);return c;};
   const b64=u=>{let s='';const v=new Uint8Array(u.buffer);for(let i=0;i<v.length;i+=0x8000)s+=String.fromCharCode.apply(null,v.subarray(i,i+0x8000));return btoa(s);};
   window.__arLlamadas=[];window.__arPlan=null;
   window.Ar={estado:()=>'listo',abrir:o=>{const op=JSON.parse(o);window.__arLlamadas.push(op);
     setTimeout(async()=>{const cap=[];for(let i=0;i<op.pasos.length;i++){const ps=op.pasos[i],e=window.__arPlan(ps.id,op.orient);
       const c=__escena(e.W,e.H,e.cosa),bl=await new Promise(r=>c.toBlob(r,'image/jpeg',.92)),id='ar-prueba-'+Date.now()+'-'+i;await Fotos.guardar(id,bl);
       const dw=160,dh=Math.round(160*e.H/e.W),d=new Uint16Array(dw*dh).fill(e.fondoMm);
       // la profundidad: el sujeto (su caja) a e.mm, el fondo lejos
       const bx=[(e.cosa.x*e.W-e.cosa.h*e.ar/2)/e.W,(e.cosa.y*e.H-e.cosa.h)/e.H,(e.cosa.x*e.W+e.cosa.h*e.ar/2)/e.W,e.cosa.y];
       for(let y=0;y<dh;y++)for(let x=0;x<dw;x++){const X=(x+.5)/dw,Y=(y+.5)/dh;if(X>=bx[0]&&X<=bx[2]&&Y>=bx[1]&&Y<=bx[3])d[y*dw+x]=e.mm;}
       cap.push({paso:ps.id,ts:Date.now(),sigue:true,centro:e.mm/1000,alto:e.alto,elev:e.elev,ladeo:0,rot:0,fx:e.fx,fy:e.fx,cx:e.W/2,cy:e.H/2,W:e.W,H:e.H,foto:id,prof:{w:dw,h:dh,mm:b64(d)}});}
       window.arResultado(JSON.stringify({capturas:cap,profundidad:true}));},50);}};
 });
 // ---------- prueba con persona por ARCore ----------
 // la persona mide 600 px en una foto de 720×960 con f = 760 px; 175 cm a esa escala están a 175·760/600 = 221.7 cm de
 // profundidad (el plano de su contorno): la piel que ve la cámara, 10 cm antes
 const fx=760,hpx=600,Zp=175*fx/hpx;
 await p.evaluate(([fx,hpx,Zp])=>{window.__arPlan=(id,orient)=>({W:720,H:960,fx,cosa:{f:'frente',x:.5,y:.93,h:hpx},ar:.3,mm:Math.round((Zp-10)*10),fondoMm:9000,
   // el suelo: el teléfono a 1.40 m y la inclinación que pone los pies (y = .93·960) donde caen a esa distancia
   alto:1.40,elev:-(Math.atan(140/Zp)-Math.atan((.93*960-480)/fx))*180/Math.PI});},[fx,hpx,Zp]);
 await p.evaluate(()=>{location.hash='#pclab/resumen';});await p.waitForTimeout(600);
 assert.match(await p.$eval('#app',e=>e.textContent),/Probar la medida con una persona/);assert.ok(await p.$('[data-act="labPrueba"][data-v="ar"]'),'botón de ARCore');
 await p.click('[data-act="labPrueba"][data-v="ar"]');await p.waitForTimeout(300);await p.fill('#sheet [name=real]','175');await p.click('#sheet button[type=submit]');
 await p.waitForFunction(()=>((S.config.pesoCam||{}).pruebas||[]).length===1,null,{timeout:90000});
 const P1=await p.evaluate(()=>S.config.pesoCam.pruebas[0]);console.log('prueba ARCore',JSON.stringify(P1));
 const op=await p.evaluate(()=>window.__arLlamadas[0]);assert.equal(op.orient,'vertical');assert.ok(op.textos&&op.textos.tomar,'textos para la pantalla nativa');
 assert.ok(Math.abs(P1.medido.prof/175-1)<.04,'estatura con la profundidad: '+P1.medido.prof);
 assert.ok(P1.medido.suelo>0&&Math.abs(P1.medido.suelo/175-1)<.06,'estatura con el suelo de ARCore: '+P1.medido.suelo);
 assert.ok(P1.medido.centro>0,'con la mira');await p.screenshot({path:'ar_prueba.png'});
 assert.match(await p.$eval('#sheet',e=>e.textContent),/Resultado de la prueba/);await p.click('#sheet [data-act="cerrar"]');
 paso(`persona con ARCore: profundidad ${P1.medido.prof} cm, suelo ${P1.medido.suelo} cm, mira ${P1.medido.centro} cm (real 175)`);
 // ---------- prueba con persona con el sensor de movimiento ----------
 await p.evaluate(async()=>{const V=document.createElement('canvas');V.width=720;V.height=960;const E=__escena(720,960,{f:'frente',x:.5,y:.93,h:600});
   // (un lienzo que no cambia no da cuadros de video: se vuelve a pintar)
   window.__pv=setInterval(()=>V.getContext('2d').drawImage(E,0,0),100);V.getContext('2d').drawImage(E,0,0);Fotos.camara=async()=>V.captureStream(10);
   const z=9.81*Math.sin(5*Math.PI/180),y=9.81*Math.cos(5*Math.PI/180);window.__gv=setInterval(()=>window.dispatchEvent(new DeviceMotionEvent('devicemotion',{accelerationIncludingGravity:{x:0,y,z},interval:50})),50);});
 await p.evaluate(()=>{location.hash='#pclab/resumen';});await p.waitForTimeout(400);
 await p.click('[data-act="labPrueba"][data-v="sensor"]');await p.waitForTimeout(300);await p.fill('#sheet [name=real]','175');await p.fill('#sheet [name=alt]','140');await p.click('#sheet button[type=submit]');
 await p.waitForFunction(()=>((S.config.pesoCam||{}).pruebas||[]).length===2,null,{timeout:90000});
 const P2=await p.evaluate(()=>S.config.pesoCam.pruebas[1]);console.log('prueba sensor',JSON.stringify(P2));assert.ok(P2.medido.sensor>100&&P2.medido.sensor<260,'estatura con el sensor');
 await p.click('#sheet [data-act="cerrar"]');await p.evaluate(()=>{clearInterval(window.__gv);clearInterval(window.__pv);});
 paso(`persona con el sensor de movimiento: ${P2.medido.sensor} cm`);
 // ---------- ganado con ARCore ----------
 // la vaca: 1.0 de alto en la escena = 450 px; a 3 m de la cámara (más 15 cm al plano del contorno), con f = 760
 await p.evaluate(()=>{window.__arPlan=(id)=>({W:960,H:720,fx:760,cosa:id==='atras'?{f:'atras',x:.45,y:.88,h:430}:{f:'lado',x:.45,y:.9,h:430},ar:id==='atras'?.57:1.33,mm:3000,fondoMm:12000,alto:1.4,elev:-12});});
 await p.evaluate(()=>{location.hash='#pesocam';});await p.waitForTimeout(500);
 assert.ok(await p.$('[data-act="pcUsarAR"][data-v="1"][aria-pressed="true"]'),'ARCore elegido para la distancia');
 assert.ok(!(await p.$('[data-act="pcAlto"]')),'con ARCore no pide la altura del teléfono');
 const n0=await p.evaluate(()=>PesoCam.REG().length);
 await p.click('[data-act="pcVivo"]');
 await p.waitForSelector('#sheet .pc-kg',{timeout:120000});await p.screenshot({path:'ar_vaca.png'});
 const G=await p.evaluate(()=>{const r=PesoCam.REG().slice(-1)[0];return {geo:r.geo,dims:r.dims,mx:r.mx,kg:r.kg,camaras:r.camaras,motor:r.motor,n:PesoCam.REG().length};});
 console.log('ganado ARCore',JSON.stringify(G).slice(0,900));
 assert.equal(G.n,n0+1,'registrada');assert.equal(G.geo.metodo,'ar-prof','escala con la profundidad');assert.ok(Math.abs(G.geo.ar.costado.prof-315)<5,'profundidad del cuerpo 300 cm + 15 al plano del contorno');assert.ok(G.geo.ar.costado.elev===-12,'inclinación');
 assert.ok(G.dims&&G.dims.WH>80&&G.dims.WH<200&&G.dims.CD>30,'alzada y fondo');assert.ok(G.kg>100&&G.kg<1500,'peso');assert.deepEqual(G.camaras,['costado','atras','otro']);assert.equal(G.motor,'arcore');
 paso(`ganado con ARCore: alzada ${Math.round(G.dims.WH)} cm, fondo ${Math.round(G.dims.CD)} cm, ${Math.round(G.kg)} kg`);
 // el Excel con las columnas de ARCore y la hoja de pruebas
 const X=await p.evaluate(async()=>{const u=await PcLab.libro(PesoCam.REG().filter(x=>x.modo==='ganado'),{});let s='';for(let i=0;i<u.length;i+=0x8000)s+=String.fromCharCode.apply(null,u.subarray(i,i+0x8000));return btoa(s);});
 fs.writeFileSync('ar.xlsx',Buffer.from(X,'base64'));
 try{console.log(execFileSync(process.env.PY||'python3',['-c',`import openpyxl,sys
w=openpyxl.load_workbook(sys.argv[1]);assert 'Pruebas persona' in w.sheetnames,w.sheetnames
h=[c.value for c in w['Mediciones'][1]];assert 'escala_metodo' in h and 'distancia_costado_m' in h,h
r=[c.value for c in w['Mediciones'][2]];print('escala',r[h.index('escala_metodo')],'distancia',r[h.index('distancia_costado_m')])`,'ar.xlsx'],{encoding:'utf8'}).trim());}catch(e){if(/No module named/.test(String(e.stderr||e.message)))console.log('(sin openpyxl)');else throw e;}
 if(IDI==='xx'){const K=await p.evaluate(()=>[...I18N_REC.keys()]);fs.writeFileSync('claves_ar.json',JSON.stringify(K.map(k=>[k,1])));console.log('claves',K.length);}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');await b.close();if(errs.length)process.exit(1);
})().catch(e=>{console.error('FALLA',e.message);process.exit(1);});
