// Rumentis Beta con ARCore por debajo de la cámara en vivo, sin teléfono: el puente nativo "Ar" se reemplaza por uno
// que hace lo mismo que ArPuente.java (manda cuadros chicos con su meta: suelo, inclinación, intrínsecos, profundidad;
// y fotos completas cuando la página las pide) con escenas de fotos de COCO. Comprueba:
// - la prueba con persona: la cámara en vivo con la silueta, espera a que ARCore tenga el suelo, toma sola las fotos de
//   ARCore y mide la estatura (175 cm reales; con la profundidad y con el suelo de ARCore: error < 4 %);
// - si ARCore no arranca, la misma prueba con la cámara normal y el sensor de movimiento;
// - ganado: costado, por detrás y otro costado con ARCore; escala de ARCore, medidas y peso; el Excel;
// - el orden de los métodos y la corrección del sesgo con las pruebas.
// Usa 8111 y 8112 (los modelos y las fotos de vivo_t.js).
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');const {execFileSync}=require('child_process');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const IDI=process.env.IDIOMA||'es';const cfg=CFG0.replace("prueba:false,beta:false,","prueba:false,beta:true,");
/* la escena con perspectiva exacta: un cuerpo de pie de Hr cm, con la caja de yT a yP (filas de la foto), visto con el
   teléfono a altoM m del suelo. Busca la inclinación (grados, negativa = hacia abajo) con la que ese cuerpo mide Hr y da
   la distancia horizontal D (cm) y la profundidad (mm, a lo largo del eje de la cámara) de su frente en las filas del
   cuerpo (mc: medio cuerpo, del frente al plano del contorno). Con D fija (dada) busca la inclinación que pone las patas
   en yP. */
const tg=(p,y,cy,fy)=>Math.tan(p+Math.atan((y-cy)/fy));
function escena({Hr,D:D0,altoM,yT,yP,cy,fy,mc}){const h=altoM*100;let mejor=null;
  for(let e=-45;e<=15;e+=.002){const p=-e*Math.PI/180,aP=p+Math.atan((yP-cy)/fy);if(aP<=.02)continue;const D=h/Math.tan(aP),H=D*(tg(p,yP,cy,fy)-tg(p,yT,cy,fy));
    const err=D0?Math.abs(D-D0):Math.abs(H-Hr);if(!mejor||err<mejor.err)mejor={err,e,p,D,H};}
  const ym=yT+.375*(yP-yT),b=Math.atan((ym-cy)/fy),Z=mejor.D*Math.cos(b)/Math.cos(mejor.p+b)-mc;
  return {elev:mejor.e,D:mejor.D,H:mejor.H,mm:Math.round(Z*10)};}
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const errs=[];
 const p=await (await b.newContext({viewport:{width:393,height:852},deviceScaleFactor:1.5})).newPage();
 p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>{const t=m.text();if(m.type()==='error'&&!/favicon|ERR_|net::|Failed to load resource/.test(t))errs.push(t);});
 await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg}));
 await p.addInitScript(i=>{window.VISION_BASE='http://127.0.0.1:8112/';window.__I=i;},IDI);
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__I);});await p.reload();await p.waitForTimeout(900);
 const paso=t=>console.log('·',t);
 await p.evaluate(()=>cargarDemo());await p.waitForTimeout(400);
 // las escenas y el ARCore de prueba. __esc: {f, x, y (pie), hf (alto en fracción del cuadro), plan:{W,H (foto completa), fx, mm, alto, elev, ar (ancho/alto de la caja)}}
 await p.evaluate(async()=>{
   const carga=async f=>{const im=new Image();im.crossOrigin='anonymous';im.src='http://127.0.0.1:8112/'+f;await im.decode();return im;};
   const F={frente:{im:await carga('frente.jpg'),bb:[142,29,175,590]},lado:{im:await carga('lado.jpg'),bb:[60,127,359,269]},atras:{im:await carga('atras.jpg'),bb:[269,81,169,299]}};
   window.__pintar=(W,H,o)=>{const c=document.createElement('canvas');c.width=W;c.height=H;const x=c.getContext('2d');x.fillStyle='#c9c3b8';x.fillRect(0,0,W,H);
     const {im,bb}=F[o.f],h=o.hf*H,k=h/bb[3],pad=.05*bb[3],sw=bb[2]+2*pad,sh=bb[3]+2*pad,dw=sw*k,dh=sh*k;x.drawImage(im,bb[0]-pad,bb[1]-pad,sw,sh,o.x*W-dw/2,o.y*H-dh+pad*k,dw,dh);return c;};
   const b64=u=>{let s='';const v=new Uint8Array(u.buffer);for(let i=0;i<v.length;i+=0x8000)s+=String.fromCharCode.apply(null,v.subarray(i,i+0x8000));return btoa(s);};
   // lo que sabe ARCore de una imagen de w×h (escala k respecto de la foto completa)
   /* como ArPuente: los cuadros en vivo sin profundidad (pd: ya la hay) y girados (el JPEG acostado, rot 90); la foto con
      la profundidad suavizada (aquí mala a propósito: el fondo se come a la persona) y la cruda con solo los puntos de
      confianza (bien, en el cuerpo; cero en lo demás) */
   const meta=(e,w,h,GW)=>{const k=w/e.plan.W,P=e.plan,o={W:w,H:h,fx:P.fx*k,fy:P.fx*k,cx:w/2,cy:h/2,elev:P.elev,ladeo:0,sigue:true,alto:P.alto,rot:0,pd:true};if(!GW)return o;
     const GH=Math.round(GW*h/w),d=new Uint16Array(GW*GH),r=new Uint16Array(GW*GH),hb=e.hf,wb=hb*P.ar*h/w;
     const bx=[e.x-wb/2,e.y-hb,e.x+wb/2,e.y];for(let y=0;y<GH;y++)for(let x=0;x<GW;x++){const X=(x+.5)/GW,Y=(y+.5)/GH,en=X>=bx[0]&&X<=bx[2]&&Y>=bx[1]&&Y<=bx[3];
       d[y*GW+x]=en&&!P.malo?P.mm:en?Math.round(P.mm*2.6):9000;r[y*GW+x]=en&&(x+y)%3?P.mm+((x*7+y*3)%9-4)*4:(x*y)%5?0:9000;}
     o.prof={w:GW,h:GH,mm:b64(d)};if(P.cruda!==false)o.praw={w:GW,h:GH,mm:b64(r)};return o;};
   const acostar=c=>{const r=document.createElement('canvas');r.width=c.height;r.height=c.width;const g=r.getContext('2d');g.setTransform(0,-1,1,0,0,c.width);g.drawImage(c,0,0);return r;};
   window.__ar={iniciar:0,cuadros:0,fotos:0,falla:false};
   window.Ar={estado:()=>'listo',
     iniciar:()=>{__ar.iniciar++;setTimeout(()=>window.arIniciado(JSON.stringify(__ar.falla?{error:'no-compatible: prueba'}:{ok:true,profundidad:true})),30);},
     otro:()=>setTimeout(()=>{const e=window.__esc;if(!e||!window.arCuadro)return;const w=e.plan.W/2,h=e.plan.H/2,c=acostar(__pintar(w,h,e));__ar.cuadros++;
       window.arCuadro(c.toDataURL('image/jpeg',.8).split(',')[1],{...meta(e,w,h,0),rot:90,iw:c.width,ih:c.height});},40),
     foto:()=>setTimeout(async()=>{const e=window.__esc,c=__pintar(e.plan.W,e.plan.H,e),bl=await new Promise(r=>c.toBlob(r,'image/jpeg',.92)),id='ar-prueba-'+(++__ar.fotos);
       await Fotos.guardar(id,bl);window.arFoto(JSON.stringify({...meta(e,e.plan.W,e.plan.H,160),ts:Date.now(),foto:id}));},60),
     detener:()=>{}};
 });
 const esc=o=>p.evaluate(o=>{window.__esc=o;},o);
 // ---------- prueba con persona: ARCore por debajo de la cámara en vivo ----------
 // foto completa 960×1280, f = 1013 px; la persona de 175 cm ocupa 0.625 del alto (800 px), el teléfono a 1.4 m
 const PW=960,PH=1280,fx=1013,hf=.625,EP=escena({Hr:175,altoM:1.4,yT:(.93-hf)*PH,yP:.93*PH,cy:PH/2,fy:fx,mc:10});
 console.log('escena persona',JSON.stringify(EP));
 await esc({f:'frente',x:.5,y:.93,hf,plan:{W:PW,H:PH,fx,mm:EP.mm,alto:1.4,elev:EP.elev,ar:.3,malo:true}});
 await p.evaluate(()=>{location.hash='#pclab/resumen';});await p.waitForTimeout(600);
 assert.match(await p.$eval('#app',e=>e.textContent),/Probar la medida con una persona/);
 assert.equal(await p.$$eval('[data-act="labPrueba"]',e=>e.length),1,'un solo botón');
 await p.click('[data-act="labPrueba"]');await p.waitForTimeout(300);await p.fill('#sheet [name=real]','175');await p.click('#sheet button[type=submit]');
 await p.waitForSelector('dialog.cv',{timeout:20000});
 await p.waitForFunction(()=>+(document.querySelector('.cv')?.dataset.dist||0)>0,null,{timeout:60000});
 const dV=await p.$eval('.cv',e=>+e.dataset.dist);console.log('distancia en vivo (ARCore)',dV,'m');await p.screenshot({path:'ar_vivo.png'});
 assert.ok(await p.evaluate(()=>document.querySelector('.cv-gira').hidden),'con ARCore no se cambia de cámara');
 await p.waitForFunction(()=>((S.config.pesoCam||{}).pruebas||[]).length===1,null,{timeout:120000});
 const P1=await p.evaluate(()=>S.config.pesoCam.pruebas[0]);console.log('prueba ARCore',JSON.stringify(P1));
 assert.equal(P1.via,'ar');assert.ok(Math.abs(P1.medido.prof/175-1)<.04,'estatura con la profundidad (la cruda; la suavizada estaba mala): '+P1.medido.prof);assert.equal(P1.g,2);
 assert.ok(Math.abs(P1.medido.suelo/175-1)<.04,'estatura con el suelo de ARCore: '+P1.medido.suelo);
 const A1=await p.evaluate(()=>({...window.__ar}));assert.ok(A1.cuadros>5&&A1.fotos>=1,'cuadros en vivo y fotos completas de ARCore '+JSON.stringify(A1));
 assert.match(await p.$eval('#sheet',e=>e.textContent),/Resultado de la prueba/);await p.screenshot({path:'ar_prueba.png'});await p.click('#sheet [data-act="cerrar"]');
 paso(`persona con ARCore en la cámara en vivo: profundidad ${P1.medido.prof} cm, suelo ${P1.medido.suelo} cm (real 175) · ${A1.cuadros} cuadros, ${A1.fotos} fotos`);
 // ---------- si ARCore no arranca: la cámara normal y el sensor de movimiento ----------
 await p.evaluate(()=>{window.__ar.falla=true;const V=document.createElement('canvas');V.width=720;V.height=960;const E=__pintar(720,960,{f:'frente',x:.5,y:.93,hf:.625});
   window.__pv=setInterval(()=>V.getContext('2d').drawImage(E,0,0),100);V.getContext('2d').drawImage(E,0,0);Fotos.camara=async()=>V.captureStream(10);
   const z=9.81*Math.sin(5*Math.PI/180),y=9.81*Math.cos(5*Math.PI/180);window.__gv=setInterval(()=>window.dispatchEvent(new DeviceMotionEvent('devicemotion',{accelerationIncludingGravity:{x:0,y,z},interval:50})),50);});
 await p.evaluate(()=>{location.hash='#pclab/resumen';});await p.waitForTimeout(400);
 await p.click('[data-act="labPrueba"]');await p.waitForTimeout(300);await p.fill('#sheet [name=real]','175');await p.click('#sheet button[type=submit]');
 await p.waitForFunction(()=>((S.config.pesoCam||{}).pruebas||[]).length===2,null,{timeout:120000});
 const P2=await p.evaluate(()=>S.config.pesoCam.pruebas[1]);console.log('prueba sin ARCore',JSON.stringify(P2));
 assert.equal(P2.via,'sensor','sin ARCore: el sensor');assert.ok(P2.medido.sensor>100&&P2.medido.sensor<260,'estatura con el sensor');
 await p.click('#sheet [data-act="cerrar"]');await p.evaluate(()=>{clearInterval(window.__gv);clearInterval(window.__pv);window.__ar.falla=false;});
 paso(`sin ARCore, con el sensor de movimiento: ${P2.medido.sensor} cm`);
 // ---------- ganado con ARCore ----------
 // foto completa 1280×960, f = 1013; la vaca a 3 m en horizontal, el teléfono a 1.4 m
 const GWd=1280,GHd=960;
 const pl=(y)=>{const E=escena({D:300,altoM:1.4,yT:(y-.6)*GHd,yP:y*GHd,cy:GHd/2,fy:fx,mc:15});return {W:GWd,H:GHd,fx,mm:E.mm,alto:1.4,elev:E.elev,ar:1.33};};
 await esc({f:'lado',x:.45,y:.9,hf:.6,plan:pl(.9)});
 await p.evaluate(()=>{location.hash='#pesocam';});await p.waitForTimeout(500);
 const pag=await p.$eval('#app',e=>e.textContent);assert.ok(!/Tipo de animal|Etapa|Distancia al animal con/.test(pag),'sin preguntas de tipo, etapa ni método');
 assert.ok(!(await p.$('[data-act="pcAlto"]')),'con ARCore no pide la altura del teléfono');
 const n0=await p.evaluate(()=>PesoCam.REG().length);
 await p.click('[data-act="pcVivo"]');await p.waitForSelector('dialog.cv',{timeout:20000});
 await p.waitForFunction(()=>/(Paso|Step|Passo) 2/.test(document.querySelector('.cv-paso')?.textContent||''),null,{timeout:90000});
 await esc({f:'atras',x:.45,y:.88,hf:.6,plan:{...pl(.88),ar:.57}});
 await p.waitForFunction(()=>/(Paso|Step|Passo) 3/.test(document.querySelector('.cv-paso')?.textContent||''),null,{timeout:90000});
 await esc({f:'lado',x:.45,y:.9,hf:.6,plan:pl(.9)});
 await p.waitForSelector('#sheet .pc-kg',{timeout:120000});await p.screenshot({path:'ar_vaca.png'});
 const G=await p.evaluate(()=>{const r=PesoCam.REG().slice(-1)[0];return {geo:r.geo,dims:r.dims,kg:r.kg,camaras:r.camaras,motor:r.motor,n:PesoCam.REG().length};});
 await p.waitForTimeout(800);const Z0=await p.evaluate(async()=>{const id=PesoCam.REG().slice(-1)[0].id,u=await Fotos.url('pcz-'+id+'-0-0');if(!u)return null;const j=JSON.parse(await (await fetch(u)).text());return {praw:!!j.praw,prof:!!j.prof,fx:j.fx};});
 assert.ok(Z0&&Z0.praw&&Z0.prof&&Z0.fx>0,'se guarda lo de ARCore de cada foto (profundidad cruda y suavizada) '+JSON.stringify(Z0));
 console.log('ganado ARCore',JSON.stringify(G).slice(0,700));
 assert.equal(G.n,n0+1,'registrada');assert.ok(/^ar-/.test(G.geo.metodo),'escala de ARCore: '+G.geo.metodo);assert.ok(Math.abs(G.geo.ar.costado.suelo/300-1)<.03,'distancia por el suelo: '+G.geo.ar.costado.suelo);
 assert.ok(Math.abs(G.geo.ar.costado.prof/G.geo.ar.costado.suelo-1)<.04,'suelo y profundidad de acuerdo');assert.equal(G.geo.ar.costado.profTipo,'cruda');
 assert.ok(G.dims&&G.dims.WH>80&&G.dims.WH<200&&G.dims.CD>30,'alzada y fondo');assert.ok(G.kg>100&&G.kg<1500,'peso');assert.deepEqual(G.camaras,['costado','atras','otro']);assert.match(G.motor,/arcore/);
 paso(`ganado con ARCore: alzada ${Math.round(G.dims.WH)} cm, fondo ${Math.round(G.dims.CD)} cm, ${Math.round(G.kg)} kg (${G.geo.metodo})`);
 // ---------- el orden de los métodos y la corrección del sesgo con las pruebas (como las primeras reales) ----------
 const AJ=await p.evaluate(()=>PcLab.ajusteAR([174,168,173,160,170].map((m,i)=>({via:'ar',g:2,real:180,medido:{suelo:m,prof:[228,374,159,260,107][i],centro:[156,95,217,117,92][i]}}))));
 assert.equal(AJ.arPref[0],'suelo','el más estable primero');assert.ok(AJ.arCorr.suelo>1.03&&AJ.arCorr.suelo<1.1,'corrige el sesgo del suelo');
 await p.evaluate(a=>PesoCam.guardarPC(a),AJ);
 const k2=await p.evaluate(()=>{const c={fx:760,fy:760,cx:480,cy:360,W:960,H:720,alto:1.4,elev:-12};const t={W:960,H:720,sil:{n:[.2,.3,.7,.9]},med:{alto:400}};return PesoCam.escalaAR(c,t,'ganado');});
 assert.equal(k2.metodo,'ar-suelo');assert.ok(Math.abs(k2.cmpx/k2.ksuelo-AJ.arCorr.suelo)<1e-6,'aplica la corrección');
 // las pruebas de antes de la corrección de la perspectiva (sin g) no cuentan
 assert.deepEqual(await p.evaluate(()=>PcLab.ajusteAR([1,2,3].map(()=>({via:'ar',real:180,medido:{suelo:170}}))).arPref),null,'pruebas viejas fuera');paso('método y corrección: '+AJ.arPref.join(' > ')+' · ×'+AJ.arCorr.suelo);
 // el Excel con la escala de ARCore y las pruebas
 const X=await p.evaluate(async()=>{const u=await PcLab.libro(PesoCam.REG().filter(x=>x.modo==='ganado'),{});let s='';for(let i=0;i<u.length;i+=0x8000)s+=String.fromCharCode.apply(null,u.subarray(i,i+0x8000));return btoa(s);});
 fs.writeFileSync('ar.xlsx',Buffer.from(X,'base64'));
 try{console.log(execFileSync(process.env.PY||'python3',['-c',`import openpyxl,sys
w=openpyxl.load_workbook(sys.argv[1]);assert 'Pruebas persona' in w.sheetnames,w.sheetnames
h=[c.value for c in w['Mediciones'][1]];assert 'escala_metodo' in h and 'distancia_costado_m' in h,h
r=[c.value for c in w['Mediciones'][w['Mediciones'].max_row]];print('escala',r[h.index('escala_metodo')],'distancia',r[h.index('distancia_costado_m')])`,'ar.xlsx'],{encoding:'utf8'}).trim());}catch(e){if(/No module named/.test(String(e.stderr||e.message)))console.log('(sin openpyxl)');else throw e;}
 if(IDI==='xx'){const K=await p.evaluate(()=>[...I18N_REC.keys()]);fs.writeFileSync('claves_ar.json',JSON.stringify(K.map(k=>[k,1])));console.log('claves',K.length);}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');await b.close();if(errs.length)process.exit(1);
})().catch(e=>{console.error('FALLA',e.message);process.exit(1);});
