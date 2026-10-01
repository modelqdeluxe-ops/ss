// Rumentis Beta: peso con cámara en vivo, sin marca. La cámara se reemplaza por lienzos que dibujan escenas con fotos
// de COCO: una persona lejos (debe pedir "Acércate"), de frente y de costado; y una vaca de costado y de espaldas con
// una persona al lado como referencia. Comprueba que las fotos se tomen solas, que las medidas en cm salgan cerca de
// las de la escena y que la calibración con báscula ajuste el peso.
// Usa 8112 con los modelos (silueta, silueta_p, seg, cuerpo, cuerpo_v, animal_m), ort.bundle.js, el .wasm y las fotos: frente.jpg
// (COCO 000000223959), lejos.jpg (000000295478), perfil.jpg (000000438907, de perfil), lado.jpg (000000090062) y
// atras.jpg (000000467776). SIN_WORKER=1 prueba sin Web Worker;
// NUCLEOS=8 simula un teléfono con 8 núcleos (dos workers del modelo rápido).
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const IDI=process.env.IDIOMA||'es';const cfg=CFG0.replace("prueba:false,beta:false,","prueba:false,beta:true,");
const DOS=/2 de 2|2 of 2/;
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const errs=[];
 const ctx=await b.newContext({viewport:{width:393,height:852},deviceScaleFactor:1.5});const p=await ctx.newPage();global.__p=p;global.__errs=errs;
 p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>{const t=m.text();if(m.type()==='error'&&!/favicon|ERR_|net::|Failed to load resource/.test(t))errs.push(t);if(m.type()==='warning'&&/Visión/.test(t))console.log('aviso:',t);});
 await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg}));
 await p.addInitScript(([i,sw,nu])=>{window.VISION_BASE='http://127.0.0.1:8112/';window.__I=i;if(sw)window.Worker=undefined;if(nu)Object.defineProperty(navigator,'hardwareConcurrency',{get:()=>nu});},[IDI,!!process.env.SIN_WORKER,+process.env.NUCLEOS||0]);
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__I);});await p.reload();await p.waitForTimeout(900);
 await p.evaluate(()=>cargarDemo());await p.waitForTimeout(400);
 // la "cámara": un lienzo vertical (personas) y uno horizontal (ganado) con la escena que pida la prueba.
 // Cada cosa: {f: foto, x: centro (fracción del ancho), y: pie (fracción del alto), h: alto en px, sx: estirar a lo ancho}
 await p.evaluate(async()=>{
   const carga=async f=>{const im=new Image();im.crossOrigin='anonymous';im.src='http://127.0.0.1:8112/'+f;await im.decode();return im;};
   const F={frente:{im:await carga('frente.jpg'),bb:[142,29,175,590]},lejos:{im:await carga('lejos.jpg'),bb:[152,36,196,579]},
     lado:{im:await carga('lado.jpg'),bb:[60,127,359,269]},perfil:{im:await carga('perfil.jpg'),bb:[49,164,80,327]},atras:{im:await carga('atras.jpg'),bb:[269,81,169,299]}};
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
 const PF={f:'frente',x:.45,y:.85,h:.7*960},PC={f:'perfil',x:.45,y:.85,h:.7*960,sx:.85};
 await esc('v',[{f:'lejos',x:.45,y:.7,h:.38*960}]);
 const t0=Date.now();await p.click('[data-act="pcVivo"]');
 await p.waitForSelector('#sheet form[data-form="pcAlto"]');await p.fill('#sheet [name=alto]','170');
assert.ok(!await p.$('#sheet [name=sexo]')&&!await p.$('#sheet [name=edad]'),'sin datos demográficos');await p.click('#sheet button[type=submit]');
 await p.waitForSelector('dialog.cv');
 await p.waitForFunction(()=>document.querySelector('.cv-chip[data-k="det"]')?.dataset.s==='ok',null,{timeout:120000});
 console.log('primer cuadro',Date.now()-t0,'ms');await p.waitForTimeout(600);
 const m1=await p.$eval('.cv-msg',e=>e.textContent);console.log('lejos:',m1);assert.ok(/Acércate|Come closer|Aproxime/.test(m1),'pide acercarse');
 assert.equal(await p.$eval('.cv-chip[data-k="dist"]',e=>e.dataset.s),'no');
 const pos1=await p.$eval('.cv-pos',e=>({v:!e.hidden,t:e.lastChild.textContent}));console.log('posición lejos',pos1.t);assert.ok(pos1.v&&parseInt(pos1.t)<90,'indicador de posición');assert.ok(!await p.$('.cv-chip[data-k="ref"]'),'sin referencia en personas');
 await p.screenshot({path:'vivo_lejos.png'});
 await esc('v',[PF]);
 await p.waitForFunction(()=>+(document.querySelector('.cv')?.dataset.puntos||0)>=100,null,{timeout:60000});
 console.log('puntos del cuerpo en vivo',await p.$eval('.cv',e=>e.dataset.puntos));await p.screenshot({path:'vivo_frente.png'});
 const t1=Date.now();await paso2();
 console.log('frente capturada',Date.now()-t0,'ms · de verde a foto',Date.now()-t1,'ms');
 await p.waitForFunction(()=>!!document.querySelector('.cv-chip[data-k="ang"]')?.dataset.s,null,{timeout:30000});
 console.log('velocidad:',await p.$eval('.cv-fps',e=>e.textContent),await p.$eval('.cv',e=>JSON.stringify(e.dataset)));
 assert.equal(await p.$eval('.cv-chip[data-k="ang"]',e=>e.dataset.s),'no','de frente no sirve como costado');
 await esc('v',[PC]);
 await p.waitForSelector('#sheet .pc-kg',{timeout:90000});console.log('medición completa',Date.now()-t0,'ms');
 assert.ok(await p.$('#sheet details.pc-exp'),'explica el modelo');assert.ok(await p.$('.cv-firma')===null,'la cámara se cerró');
 let txt=await leer();await p.screenshot({path:'vivo_res.png'});
 assert.ok(!await p.$('dialog.cv'),'la cámara se cierra');assert.equal(await p.$$eval('#sheet .pc-caps img',e=>e.length),2,'dos fotos');
 const U=await p.evaluate(()=>{const r=PesoCam.REG().slice(-1)[0];return {pred:r.pred,L:r.L,dims:r.dims,puntos:r.puntos,topes:r.topes,estimadas:r.estimadas,v:r.v};});
 console.log('modelo v5',JSON.stringify(U));assert.ok(U.puntos,'puntos del cuerpo en las dos fotos');
 assert.ok(U.pred>35&&U.pred<130,'peso del modelo de una persona');assert.ok(U.L>35&&U.L<130,'volumen de una persona');
 assert.ok(U.dims.bid>30&&U.dims.bid<60&&U.dims.th>35&&U.dims.th<80,'medidas de una persona');
 assert.ok(U.dims.cf>20&&U.dims.cf<60&&U.dims.ne>25&&U.dims.ne<60&&U.dims.lt>25&&U.dims.lt<70,'pantorrilla, cuello y muslo bajo');assert.equal(U.v,7);
 assert.ok(!/modelo rápido|fast model|modelo rápido/.test(txt),'medida con el modelo preciso');
 // calibración: "la báscula dice 72 kg" dos veces → el estimado se ajusta
 // (el peso se escribe en la unidad de la app: libras o kilos según el idioma)
 const LB=await p.evaluate(()=>UW()==='lb'?2.20462:1);
 const kgs=[];
 for(let i=0;i<2;i++){await p.click('#sheet [data-act="pcCalRes"]');await p.waitForSelector('#sheet form[data-form="pcCal"]');
   await p.fill('#sheet [name=kg]',String(Math.round(72*LB)));await p.click('#sheet button[type=submit]');await p.waitForTimeout(400);
   kgs.push(await p.evaluate(()=>PesoCam.modeloV('persona')));console.log('modelo',JSON.stringify(kgs[i]));
   if(i===0){await esc('v',[PF]);await p.click('[data-act="pcVivo"]');await paso2();await esc('v',[PC]);await p.waitForSelector('#sheet .pc-kg',{timeout:90000});}}
 assert.equal(kgs[1].n,2);
 const V=await p.evaluate(()=>PesoCam.REG().filter(c=>c.modo==='persona'&&c.real>0).map(c=>c.pred));
 const k2=await p.evaluate(V=>{const m=PesoCam.modeloV('persona');return m.k*Math.pow(V,m.b);},V[1]);console.log('calibrado',k2);assert.ok(Math.abs(k2-72)<4,'se ajusta a la báscula');
 await p.screenshot({path:'vivo_cal.png',fullPage:true});
 // repetibilidad: la misma persona un poco movida y a otra distancia cada vez; el volumen casi no debe cambiar
 const vols=[];
 for(const [dx,k] of [[-.06,.66],[.04,.74],[0,.8]]){const F2={...PF,x:PF.x+dx,h:k*960};await esc('v',[F2]);await p.click('[data-act="pcVivo"]');await paso2();
   await esc('v',[{...PC,x:F2.x,h:F2.h}]);await p.waitForSelector('#sheet .pc-kg',{timeout:90000});await p.waitForTimeout(300);
   const U2=await p.evaluate(()=>{const r=PesoCam.REG().slice(-1)[0];return {pred:r.pred,giro:r.giro,inc:r.incompleta,dims:r.dims,topes:r.topes};});if(process.env.DEP)console.log('toma',JSON.stringify(U2));vols.push(U2.pred);await p.click('#sheet [data-act="cerrar"]');await p.waitForTimeout(300);}
 const vm=vols.reduce((a,b)=>a+b,0)/vols.length,dv=Math.max(...vols.map(x=>Math.abs(x/vm-1)));
 console.log('repetibilidad',vols.map(v=>v.toFixed(1)).join(' / '),'kg · variación máx',(dv*100).toFixed(1),'%');assert.ok(dv<.06,'medición estable');
 // laboratorio: registro, estadísticas, detalle, peso de báscula desde el registro y CSV
 await p.waitForSelector('.pc-lab .pc-tiles');const E=await p.evaluate(()=>{const e=PcLab.estad('persona');return {n:e.n,nReal:e.nReal,loo:e.loo,mape:e.mape,rep:e.rep,cvInt:e.cvInt,fps:e.fps,msP:e.msP};});
 console.log('laboratorio',JSON.stringify(E));assert.ok(E.n>=5&&E.nReal===2,'registro con báscula');assert.ok(E.rep<.05,'repetibilidad en el laboratorio');assert.ok(E.fps>0&&E.msP>0,'rendimiento registrado');
 await p.click('.pc-lab .pc-reg');await p.waitForSelector('#sheet .pc-det');await p.waitForFunction(()=>[...document.querySelectorAll('#sheet .pc-det-fotos img')].some(i=>!i.hidden&&i.src),null,{timeout:5000});await p.screenshot({path:'vivo_detalle.png'});
 await p.click('#sheet [data-act="pcRegReal"]');await p.fill('#sheet [name=kg]',String(Math.round(71*LB)));await p.click('#sheet button[type=submit]');await p.waitForTimeout(400);
 assert.equal(await p.evaluate(()=>PcLab.estad('persona').nReal),3,'peso de báscula desde el laboratorio');
 const csv=await p.evaluate(async()=>{let t='';Documentos.enviar=async(n,b,m,ti,tipo)=>{t=tipo+'|'+n+'|'+new TextDecoder().decode(b);return true;};await ACTS.pcRegCsv();return t;});
 const lineas=csv.split('\n').filter(Boolean);console.log('csv',lineas[0].slice(0,60),'…',lineas.length-1,'filas');assert.ok(csv.startsWith('text/csv|')&&lineas.length-1===E.n,'CSV');
 // análisis: las últimas mediciones con sus fotos originales
 const AN=await p.evaluate(async()=>{const a=await PcLab.analisis('persona');return {n:a.registros.length,f:Object.keys(a.fotos).length,t:(Object.values(a.fotos)[0]||'').slice(0,23)};});
 console.log('análisis',JSON.stringify(AN));assert.ok(AN.n>=5&&AN.f>=8&&AN.t==='data:image/jpeg;base64,','exportar para análisis con fotos originales');
 await p.screenshot({path:'vivo_lab.png',fullPage:true});

 /* ---------- ganado: la vaca mide 200 cm de largo y 150 de alto; la persona de referencia 175 cm ---------- */
 await p.click('[data-act="pcModo"][data-m="ganado"]');await p.waitForTimeout(300);assert.equal(await p.evaluate(()=>localStorage.getItem('rumentis-pc-modo')),'ganado');
 // el tipo de animal: por la raza del lote, o elegido (cambia el punto de partida del modelo v7)
 assert.deepEqual(await p.evaluate(()=>['Brangus','Brahman x','Angus','Holstein','criollo','',null].map(PesoCam.tipoDeRaza)),['cruce','cebu','euro','leche','cebu','',''],'tipo por la raza');
 assert.ok(await p.$('#pcTipo'),'elige el tipo de animal');assert.ok(await p.$('[data-act="pcEtapa"]'),'elige la etapa');
 await p.selectOption('#pcTipo','euro');await p.waitForTimeout(300);
 assert.equal(await p.evaluate(()=>{const t=S.config.pesoCam.tipos||{};const k=Object.keys(t)[0];return k&&t[k].g;}),'euro','tipo guardado en el lote');
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
 await p.waitForFunction(()=>+(document.querySelector('.cv')?.dataset.puntos||0)>=8,null,{timeout:60000});console.log('puntos de la vaca',await p.$eval('.cv',e=>e.dataset.puntos));
 await paso2();await esc('h',[ATR,REF2]);
 await p.waitForSelector('#sheet .pc-kg',{timeout:90000});txt=await leer();await p.screenshot({path:'vivo_vaca_res.png'});
 // (modelo v7: alzada y fondo de pecho con los puntos del animal y el tipo de animal; el largo y el alto de la silueta
 // quedan en el registro)
 const G=await p.evaluate(()=>{const r=PesoCam.REG().slice(-1)[0];return {pred:r.pred,kg:r.kg,dims:r.dims,largo:r.largo,alto:r.altoAnimal,v:r.v,tipo:r.tipo,etapa:r.etapa};});
 console.log('ganado v7',JSON.stringify(G));assert.equal(G.v,7);assert.equal(G.tipo,'euro','usa el tipo elegido');assert.equal(G.etapa,'adulto');
 // el mismo animal como cebú pesa e^(a cebú − a europeo) ≈ 0.62 veces
 const rc=await p.evaluate(()=>{const g=PesoCam.GANADO;return Math.exp(g.a.cebu-g.a.euro);});assert.ok(rc>.55&&rc<.7,'cebú más liviano a igual alzada');
 assert.ok(G.largo>170&&G.largo<230,'largo cerca de 200 cm');assert.ok(G.alto>125&&G.alto<175,'alto cerca de 150 cm');
 assert.ok(G.dims&&G.dims.WH>100&&G.dims.WH<160&&G.dims.CD>40&&G.dims.CD<100,'alzada y fondo de pecho de un bovino');
 assert.ok(G.pred>150&&G.pred<900,'peso del modelo de un bovino');assert.ok(/alzada|withers|cernelha/i.test(txt),'muestra la alzada');
 await p.click('#sheet [data-act="pcAgregar"]');await p.waitForTimeout(300);assert.equal(await p.$$eval('.pc-sesion .row',e=>e.length),1,'agregado al pesaje');
 const n0=await p.evaluate(()=>allItems().filter(i=>i.metodo==='camara').length);await p.click('[data-act="pcGuardar"]');await p.waitForTimeout(300);
 assert.equal(await p.evaluate(()=>allItems().filter(i=>i.metodo==='camara').length),n0+1,'pesaje guardado');
 if(IDI==='xx'){const K=[...await p.evaluate(()=>[...I18N_REC.keys()])];fs.writeFileSync('claves_vivo.json',JSON.stringify(K.map(k=>[k,1])));console.log('claves',K.length);}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');await b.close();if(errs.length)process.exit(1);
})().catch(async e=>{console.error('FALLA',e.message);
 if(global.__errs&&global.__errs.length)console.error('errores:',global.__errs.join('\n'));
 try{const p=global.__p;if(p)console.error('estado:',await p.evaluate(()=>{const c=document.querySelector('.cv');return c?JSON.stringify({...c.dataset,msg:c.querySelector('.cv-msg').textContent,paso:c.querySelector('.cv-paso').textContent,chips:[...c.querySelectorAll('.cv-chip')].map(x=>x.dataset.k+':'+x.dataset.s).join(' ')}):'sin cámara abierta';}));if(p)await p.screenshot({path:'vivo_falla.png'});}catch(e2){}
 process.exit(1);});
