// Rumentis Beta: el laboratorio completo del peso con cámara (#pclab), sin cámara. Sobre la finca de muestra arma
// mediciones de ganado con medidas, calidad de la toma y datos del animal; unas con báscula y otras con arete y peso de
// entrada. Los animales "reales" pesan como el modelo con otra γ (0.9 en vez de 0.54) y +8 %, y las fotos poco nítidas
// tienen más error: el laboratorio debe verlo (diagnóstico, error por tercio, ajuste sugerido con γ alta). Recorre todas
// las pestañas, el detalle de una medición, los datos del animal, la sesión de prueba y los exportes. Usa 8111.
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const cfg=CFG0.replace(/window\.RUMENTIS=\{app:'[a-z]+',prueba:(true|false),beta:(true|false),/,"window.RUMENTIS={app:'jefe',prueba:false,beta:true,");
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const errs=[];
 const p=await (await b.newContext({viewport:{width:393,height:852}})).newPage();
 p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>{if(m.type()==='error'&&!/favicon|ERR_|net::|Failed to load/.test(m.text()))errs.push(m.text());});
 await p.addInitScript(i=>{window.__I=i;},process.env.IDIOMA||'es');await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg}));
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__I||'es');localStorage.setItem('rumentis-pc-modo','ganado');});await p.reload();await p.waitForTimeout(800);
 await p.evaluate(()=>cargarDemo());await p.waitForTimeout(400);
 const N=await p.evaluate(()=>{
   const P=PesoCam,C=calc(),x=C.act.find(l=>(l.l.animales||[]).filter(a=>+a.p0>0).length>=8),an=x.l.animales.slice(0,24);
   let s=7;const rnd=()=>{s=(s*16807)%2147483647;return s/2147483647;};
   const regs=[];const t0=Date.now()-3*864e5;
   an.forEach((a,i)=>{
     const WH=120+rnd()*25,q=.46+rnd()*.16,CD=q*WH,pred=P.predDims(WH,CD,'cruce','adulto'),G=P.GANADO;
     // el peso "real": γ = 0.9 y +8 %; con fotos poco nítidas (un tercio) la medición se equivoca más
     const real=1.08*Math.exp(G.a.cruce+G.beta*Math.log(WH)+.9*Math.log(q/G.r0)),borrosa=i%3===0,ruido=borrosa?(rnd()<.5?-1:1)*(.07+rnd()*.05):(rnd()-.5)*.02;
     const base={modo:'ganado',v:7,f:hoy(),lote:x.id,tipo:'cruce',etapa:'adulto',alto:175,k:1,b:1,ncal:0,err:.15,cv:.01+rnd()*.03,comb:9,fuente:'preciso',score:.9,fps:12,msR:40,msP:900,msPose:150,seg:20,motor:'worker×1',puntos:true,topes:q<.48?['cd']:[]};
     // (el error de la toma va en la alzada medida: el peso del modelo sale de las medidas, como en la app)
     for(let k=0;k<(i<3?3:1);k++){const Wm=WH*Math.pow((1+ruido)*(1+(k?(rnd()-.5)*.03:0)),1/(G.beta-G.gamma)),pr=P.predDims(Wm,CD,'cruce','adulto');
       regs.push({...base,id:'L'+i+'_'+k,ts:t0+i*60e3+k*20e3,pred:Math.round(pr*100)/100,kg:Math.round(pr*100)/100,L:pr,aid:a.id,real:i<14&&!k?Math.round(real*10)/10:null,
         dims:{WH:Wm,WHs:Wm,WHr:Wm*(1+(rnd()-.5)*.06),CD,CDs:CD,L:WH*.8,FM:CD*1.1,RWH:.6,qOtro:null},
         dq:{brillo:.4+rnd()*.2,contraste:.25,nitidez:borrosa?120+rnd()*100:2000+rnd()*4000,quemado:0,oscuro:.02,tam:.5,tamRef:.4+rnd()*.3,kp:.8+rnd()*.3,kpMin:.5,cvWH:.01,cvCD:.02,cvPred:.02,elev:-3+rnd()*6,horiz:1,tomas:6,LWH:.8},
         cond:i%2?{sexo:i%4===1?'m':'h',cc:String(2+i%3),llenado:i%3?'lleno':'ayuno',notas:'prueba'}:null});}
   });
   // los 10 sin báscula: su peso de entrada (al ingreso del lote hace 3 días o menos)
   P.guardarPC({reg:regs,tipos:{[x.id]:{g:'cruce',etapa:'adulto'}}});
   for(const r of regs.filter(r=>!r.real&&r.id.endsWith('_0')).slice(0,6)){const a=an.find(z=>z.id===r.aid);const i=+r.id.slice(1).split('_')[0];
     const WH=r.dims.WH,q=r.dims.CD/WH,G=P.GANADO;a.p0=Math.round(1.08*Math.exp(G.a.cruce+G.beta*Math.log(WH)+.9*Math.log(q/G.r0)));a.fIng=hoy();}
   put('lotes',x.id,{...x.l});
   return {n:regs.length,lote:x.id};
 });
 await p.waitForTimeout(300);
 const E=await p.evaluate(()=>{const L=PcLab.etiquetasLab('ganado');return {n:L.F.length,src:[...new Set(L.F.map(f=>f.src))],e:L.F.reduce((s,f)=>s+Math.abs(f.e),0)/L.F.length,fab:L.F.reduce((s,f)=>s+f.fab,0)/L.F.length};});
 console.log('pesos reales',JSON.stringify(E));assert.ok(E.n>=18&&E.src.includes('bascula')&&E.src.includes('entrada'),'une báscula y peso de entrada');assert.ok(E.fab<-.03,'sin calibrar subestima (el real es +8 %)');
 // la tarjeta en #pesocam lleva al laboratorio completo
 await p.evaluate(()=>{location.hash='#pesocam';});await p.waitForSelector('.pc-lab .lab-abrir');await p.click('.pc-lab .lab-abrir');await p.waitForSelector('.lab-tabs');
 const tabs=['resumen','errores','medidas','calidad','animales','modelo','registro'],T={};
 for(const t of tabs){await p.evaluate(t=>{location.hash='#pclab/'+t;},t);await p.waitForTimeout(350);
   T[t]=await p.evaluate(()=>({txt:document.querySelector('main.lab').textContent.replace(/\s+/g,' ').length,svg:document.querySelectorAll('main.lab svg').length,tab:document.querySelectorAll('main.lab .lab-t').length,act:document.querySelector('.lab-tabs [aria-pressed="true"]').getAttribute('href')}));
   await p.screenshot({path:`lab_${t}.png`,fullPage:true});assert.equal(T[t].act,'#pclab/'+t);}
 console.log('pestañas',JSON.stringify(T));
 assert.ok(T.errores.svg>=3&&T.errores.tab>=4,'errores: curva, contra el peso, por grupos');assert.ok(T.medidas.svg>=4,'medidas: histogramas');assert.ok(T.calidad.tab>=2,'calidad');
 // diagnóstico: el error de las fotos poco nítidas
 await p.evaluate(()=>{location.hash='#pclab/resumen';});await p.waitForTimeout(300);
 const D=await p.$$eval('.lab-diag li',e=>e.map(x=>x.textContent.replace(/\s+/g,' ')));console.log('diagnóstico:\n  '+D.join('\n  '));
 assert.ok(D.some(t=>/Nitidez|Sharpness|Nitidez/.test(t)),'ve que la nitidez importa');assert.ok(D.some(t=>/subestima|underestimates|subestima/.test(t)),'ve el sesgo de fábrica');
 // ajuste sugerido: γ de la finca alta (el real tiene 0.9)
 await p.evaluate(()=>{location.hash='#pclab/modelo';});await p.waitForTimeout(300);
 const S=await p.$$eval('.lab-t',ts=>{const t=ts.find(x=>/β/.test(x.textContent));return t?[...t.querySelectorAll('tbody tr')].map(r=>[...r.cells].map(c=>c.textContent)):null;});console.log('ajuste sugerido',JSON.stringify(S));
 assert.ok(S&&S.length===3,'ajuste con los datos de la finca');assert.ok(parseFloat(S[1][3].replace(',','.'))<parseFloat(S[0][3].replace(',','.')),'con γ de la finca baja el error');const g2=+S[1][2].replace(',','.');assert.ok(g2>.7&&g2<1.1,'γ de la finca cerca de 0.9: '+g2);
 // calculadora
 await p.fill('[data-labcalc] [name=wh]','140');await p.fill('[data-labcalc] [name=cd]','74');await p.waitForTimeout(200);assert.ok(/\d/.test(await p.$eval('[data-calcr]',e=>e.textContent)),'calculadora');
 // animales: la trayectoria de uno con 3 mediciones
 await p.evaluate(()=>{location.hash='#pclab/animales';});await p.waitForTimeout(300);await p.click('[data-act="labAnimal"]');await p.waitForSelector('#sheet .pc-graf');
 // registro, detalle y datos del animal
 await p.evaluate(()=>{location.hash='#pclab/registro';});await p.waitForTimeout(300);
 await p.click('[data-act="labFiltro"][data-f="datos"]');await p.waitForTimeout(200);const nD=await p.$$eval('main.lab .pc-reg',e=>e.length);assert.equal(nD,await p.evaluate(()=>PesoCam.REG().filter(x=>x.cond).length),'filtro con datos');
 await p.click('[data-act="labFiltro"][data-f="todas"]');await p.waitForTimeout(200);
 await p.click('main.lab .pc-reg');await p.waitForSelector('#sheet .pc-det');assert.ok(/Sensibilidad|Sensitivity|Sensibilidade/.test(await p.$eval('#sheet',e=>e.textContent)),'sensibilidad');
 await p.click('#sheet [data-act="labCond"]');await p.waitForSelector('#sheet form[data-form="labCond"]');
 await p.click('#sheet .opts[data-name="sexo"] [data-v="c"]');await p.click('#sheet .opts[data-name="cc"] [data-v="5"]');await p.click('#sheet .opts[data-name="postura"] [data-v="movio"]');
 await p.fill('#sheet [name=cinta]','190');await p.fill('#sheet [name=notas]','se movió en la segunda');await p.click('#sheet button[type=submit]');await p.waitForTimeout(300);
 const C=await p.evaluate(()=>PesoCam.REG().slice(-1)[0].cond);console.log('datos del animal',JSON.stringify(C));assert.ok(C&&C.sexo==='c'&&C.cc==='5'&&C.postura==='movio'&&C.cinta===190,'datos guardados');
 // sesión de prueba y "mismo animal"
 await p.evaluate(()=>{location.hash='#pclab/resumen';});await p.waitForTimeout(300);await p.click('[data-act="labSesIni"]');await p.click('#sheet button[type=submit]');await p.waitForTimeout(300);
 assert.ok(await p.evaluate(()=>!!S.config.pesoCam.labSes),'sesión en curso');await p.click('[data-act="labMismo"]');await p.waitForTimeout(200);assert.ok(await p.evaluate(()=>S.config.pesoCam.labMismo===true),'mismo animal');
 assert.ok(await p.$('.lab-ses-on'),'muestra la sesión');await p.click('[data-act="labSesFin"]');await p.waitForTimeout(200);assert.ok(await p.evaluate(()=>!S.config.pesoCam.labSes),'sesión terminada');
 // exportes
 const X=await p.evaluate(async()=>{let o={};Documentos.enviar=async(n,b,m,ti,tipo)=>{o[n.replace(/-\d{4}-\d{2}-\d{2}.*$/,'')]=new TextDecoder().decode(b);return true;};await ACTS.pcRegCsv();await ACTS.labExportar();return o;});
 const csv=X['rumentis-labs-mediciones'],cab=csv.split('\n')[0].split(','),fila=csv.split('\n')[N.n].split(',');
 assert.ok(cab.includes('toma_nitidez')&&cab.includes('animal_cc')&&cab.includes('kg_real')&&cab.includes('arete'),'CSV con las columnas nuevas');
 assert.equal(fila[cab.indexOf('animal_cc')],'5','CSV con los datos del animal');
 const J=JSON.parse(X['rumentis-labs-laboratorio-ganado']);console.log('laboratorio completo',J.registros.length,'registros,',J.pesosReales.length,'pesos reales,',J.finca&&J.finca.lotes.length,'lote');
 assert.ok(J.registros.length===N.n&&J.pesosReales.length>=18&&J.finca&&J.finca.lotes[0].animales.length>0&&J.constantes.beta===2,'exporta el laboratorio completo');
 // personas: sin datos, sin errores
 await p.click('[data-act="labModo"][data-m="persona"]');await p.waitForTimeout(300);for(const t of ['resumen','errores','medidas','calidad','modelo','registro']){await p.evaluate(t=>{location.hash='#pclab/'+t;},t);await p.waitForTimeout(200);}
 if(process.env.IDIOMA==='xx'){const K=await p.evaluate(()=>[...I18N_REC.keys()]);fs.writeFileSync('claves_lab.json',JSON.stringify(K.map(k=>[k,1])));}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');await b.close();if(errs.length)process.exit(1);
})().catch(e=>{console.error('FALLA',e.message);process.exit(1);});
