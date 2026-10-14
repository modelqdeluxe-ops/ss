// Rumentis Beta: la recolección de datos en la finca (muestreo al azar, sin arete y sin lote). Sin cámara: las
// mediciones se arman con medidas sintéticas y fotos de prueba. Comprueba la sesión con sus datos fijos, la ficha de
// cada animal (número automático, báscula, Repetir este, Guardar y medir otro, Descartar), el avance de la sesión, el
// peso de báscula anotado después, el Excel (todas las hojas, cálculos y ajustes) y el ZIP por partes con las fotos.
// Usa 8111. Escribe recoleccion.xlsx y recoleccion.zip donde se corre; si hay python3 con openpyxl, abre el Excel.
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');const {execFileSync}=require('child_process');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const cfg=CFG0.replace(/window\.RUMENTIS=\{app:'[a-z]+',prueba:(true|false),beta:(true|false),/,"window.RUMENTIS={app:'jefe',prueba:false,beta:true,");
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const errs=[];
 const p=await (await b.newContext({viewport:{width:393,height:852}})).newPage();
 p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>{if(m.type()==='error'&&!/favicon|ERR_|net::|Failed to load/.test(m.text()))errs.push(m.text());});
 await p.addInitScript(i=>{window.__I=i;},process.env.IDIOMA||'es');await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg}));
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__I||'es');});await p.reload();await p.waitForTimeout(800);
 const paso=t=>console.log('·',t);
 // sin lotes: se puede medir igual, y por defecto ganado con 3 tomas con este teléfono
 await p.evaluate(()=>{location.hash='#pesocam';});await p.waitForTimeout(400);
 assert.equal(await p.evaluate(()=>calc().act.length),0,'finca sin lotes');
 assert.ok(await p.$('[data-act="pcVivo"]'),'se puede medir sin lote');assert.ok(!/Primero crea un lote/.test(await p.$eval('#app',e=>e.textContent)),'no pide lote');
 assert.equal(await p.$eval('.pc-tomas [aria-pressed="true"]',e=>e.dataset.v),'3','3 tomas por defecto');
 await p.click('.pc-tomas [data-v="4"]');await p.waitForTimeout(200);assert.equal(await p.evaluate(()=>S.config.pesoCam.tomasG),4);await p.click('.pc-tomas [data-v="3"]');await p.waitForTimeout(200);
 paso('sin lote y con las tomas de un solo teléfono');
 // la sesión con sus datos fijos
 await p.evaluate(()=>{location.hash='#pclab/resumen';});await p.waitForTimeout(400);
 assert.match(await p.$eval('#app',e=>e.textContent),/Cómo recolectar los datos en la finca/);
 await p.click('[data-act="labSesIni"]');await p.waitForTimeout(300);
 await p.click('#sheet .opts[data-name="llenado"] [data-v="ayuno"]');await p.click('#sheet .opts[data-name="suelo"] [data-v="plano"]');
 await p.fill('#sheet [name=raza]','Brahman × Pardo');await p.fill('#sheet [name=horas]','0');await p.click('#sheet button[type=submit]');await p.waitForTimeout(300);
 const SES=await p.evaluate(()=>S.config.pesoCam.labSes);assert.equal(SES.def.llenado,'ayuno');assert.equal(SES.def.raza,'Brahman × Pardo');assert.equal(SES.def.horas,0);
 paso('sesión con datos fijos: '+JSON.stringify(SES.def));
 // mediciones sintéticas (la "cámara"): medidas de un animal y fotos de prueba, con su depuración
 await p.evaluate(()=>{
   window.__k=0;
   window.__med=async(i,ruido=0)=>{const P=PesoCam,WH=112+(i%15)*3.1,q=.47+((i*7)%11)/100,CD=q*WH,pred=P.predDims(WH*(1+ruido),CD*(1+ruido),'cruce','adulto');
     const c=document.createElement('canvas');c.width=96;c.height=72;const g=c.getContext('2d');g.fillStyle='#8a5';g.fillRect(0,0,96,72);const img=c.toDataURL('image/jpeg',.8);
     const r={modo:'ganado',L:pred,kg:pred,pred,err:.12,cv:.02,comb:9,k:1,b:1,n:0,alto:175,tipo:'cruce',etapa:'adulto',lote:null,
       dims:{WH,WHs:WH,WHr:WH*1.01,CD,CDs:CD,L:WH*1.18,FM:CD*1.08,RWH:.34,qOtro:q*1.02},ancho:WH*.36,largo:WH*1.6,altoAnimal:WH*1.04,camaras:['costado','atras','otro'],
       q:{nitidez:1500+i*50,tomas:6,brillo:.45},fuente:'preciso',caps:[{img,nombre:'De costado'},{img,nombre:'Por detrás'},{img,nombre:'Otro costado'}]};
     r.id=P.registrar(r,{fps:11,msPreciso:800,cam:{width:1920,height:1440,lente:'camera2 0, facing back',vw:1920,vh:1440}});
     const blob=await (await fetch(img)).blob();for(let a=0;a<3;a++){await Fotos.guardar(`pch-${r.id}-${a}`,blob);await Fotos.guardar(`pc-${r.id}-${a}`,blob);for(let j=0;j<3;j++)await Fotos.guardar(`pcr-${r.id}-${a}-${j}`,blob);}
     const D={v:1,modo:'ganado',angs:[{rol:'costado',tomas:[0,1,2].map(j=>({W:960,H:720,cmpx:.31,tam:.6,tamRef:.5,luz:{brillo:.4,contraste:.2,nitidez:1800,quemado:0,oscuro:.01},sil:{score:.9,conf:.8,area:9000},kp:[1,1,.9,2,2,.7],M:{WH:WH*(1+j*.01),CD,L:WH*1.18,FM:CD*1.08}})),ori:{elev:2,horiz:1}},
       {rol:'atras',tomas:[{W:960,H:720,cmpx:.3,RWH:.34,WHr:WH*1.01}]},{rol:'otro',tomas:[{W:960,H:720,q:q*1.02}]}],pares:[{a:0,b:0,pred:pred*.99,WH,WHs:WH,WHr:WH*1.01,CD,CDs:CD,q,tope:false},{a:1,b:0,pred:pred*1.01,WH,CD,q}]};
     await Fotos.guardar(`pcd-${r.id}`,new Blob([JSON.stringify(D)],{type:'application/json'}));return r;};
   // "Medir" en la prueba: arma una medición y muestra su resultado (la ficha, con la sesión en curso)
   ACTS.pcVivo=async()=>{closeSheet();const r=await __med(__k++);PesoCam.mostrar(r);};
 });
 const ficha=async()=>{await p.waitForSelector('#sheet form[data-form="labFicha"]',{timeout:5000});await p.waitForTimeout(250);return await p.$eval('#sheet',e=>e.textContent);};
 await p.evaluate(()=>ACTS.pcVivo());let t=await ficha();
 assert.match(t,/Animal\s*1/,'animal 1');assert.ok(!(await p.$('#sheet [name=arete]')),'no pide arete');assert.match(t,/Datos de la sesión/);
 await p.screenshot({path:'rec_ficha.png',fullPage:true});
 await p.fill('#sheet [name=kg]','900');await p.click('#sheet .opts[data-name="sexo"] [data-v="h"]');await p.click('#sheet .opts[data-name="cc"] [data-v="3"]');await p.fill('#sheet [name=edad]','30');
 await p.click('#sheet [data-act="labFichaIr"][data-v="rep"]');
 await p.waitForTimeout(400);t=await ficha();assert.match(t,/Animal\s*1/,'repetir: el mismo animal');assert.match(t,/Repetición del mismo animal/);
 assert.equal(await p.inputValue('#sheet [name=kg]'),'900','repetir copia la báscula');assert.equal(await p.$eval('#sheet .opts[data-name="sexo"] [aria-pressed="true"]',e=>e.dataset.v),'h');
 const R1=await p.evaluate(()=>PesoCam.REG().map(x=>({aid:x.aid,real:x.real,cond:x.cond,ses:x.ses,cam:x.cam})));
 const K=await p.evaluate(()=>[Math.round(toKg(900)*10)/10,Math.round(toKg(850)*10)/10]);assert.equal(R1[0].real,K[0],'báscula en la unidad del usuario');assert.equal(R1[0].cond.llenado,'ayuno','dato fijo de la sesión');assert.equal(R1[0].cond.sexo,'h');assert.equal(R1[0].cond.edad,30);assert.ok(R1[0].cam&&R1[0].cam.width===1920,'datos de la cámara');
 assert.equal(R1[1].aid,R1[0].aid,'mismo número');paso('ficha: animal 1 con báscula, repetido con sus datos');
 await p.click('#sheet .opts[data-name="postura"] [data-v="bien"]');await p.click('#sheet [data-act="labFichaIr"][data-v="sig"]');
 await p.waitForTimeout(400);t=await ficha();assert.match(t,/Animal\s*2/,'el siguiente es el animal 2');assert.equal(await p.inputValue('#sheet [name=kg]'),'');
 await p.click('#sheet [data-act="labFichaIr"][data-v="sig"]');await p.waitForTimeout(400);t=await ficha();assert.match(t,/Animal\s*3/);
 const n0=await p.evaluate(()=>PesoCam.REG().length),idD=await p.evaluate(()=>PesoCam.REG().slice(-1)[0].id);await p.click('#sheet [data-act="labFichaDesc"]');await p.waitForTimeout(250);await p.click('#sheet [data-act="confirmarSi"]');await p.waitForTimeout(500);
 assert.equal(await p.evaluate(()=>PesoCam.REG().length),n0-1,'descartada');assert.equal(await p.evaluate(async id=>!!(await Fotos.url('pch-'+id+'-0')),idD),false,'sus fotos se borran');
 paso('guardar y medir otro: animal 2 y 3; descartar borra la medición');
 // el avance: falta la báscula del animal 2; se anota después
 await p.evaluate(()=>{location.hash='#pclab/resumen';});await p.waitForTimeout(500);
 t=await p.$eval('#app',e=>e.textContent);assert.match(t,/Recolección en la finca/);assert.match(t,/Falta el peso de báscula/);
 await p.screenshot({path:'rec_avance.png',fullPage:true});
 await p.click('[data-act="labRealAnimal"]');await p.waitForTimeout(300);await p.fill('#sheet [name=kg]','850');await p.click('#sheet button[type=submit]');await p.waitForTimeout(300);
 assert.ok(await p.evaluate(k=>PesoCam.REG().filter(x=>x.real===k).length===1,K[1]),'báscula anotada después');paso('avance de la sesión y báscula anotada después');
 // más animales al azar (2 mediciones cada uno): los reales pesan +6 % con otra γ, para que los ajustes tengan qué ver
 await p.evaluate(async()=>{const P=PesoCam,G=P.GANADO;for(let i=3;i<21;i++){for(let k=0;k<2;k++){const r=await __med(i,k?.01:-.01);const x=P.REG().find(z=>z.id===r.id),WH=x.dims.WH,q=x.dims.CD/WH;
   const real=1.06*Math.exp(G.a.cruce+G.beta*Math.log(WH)+.8*Math.log(q/G.r0));P.actualizarReg(r.id,{aid:'n:'+S.config.pesoCam.labSes.ts.toString(36)+':'+(i+1),real:Math.round(real*10)/10,cond:{...x.cond,sexo:i%2?'m':'h',cc:String(2+i%3)}});}}});
 const N=await p.evaluate(()=>PesoCam.REG().length);paso('mediciones en la sesión: '+N);
 // el Excel
 const X=await p.evaluate(async()=>{const R=PesoCam.REG(),u=await PcLab.libro(R,{sesion:'x'});let s='';for(let i=0;i<u.length;i+=0x8000)s+=String.fromCharCode.apply(null,u.subarray(i,i+0x8000));
   const A=PcLab.ajustes(PcLab.porAnimal(R));return {b64:btoa(s),A,calc:PcLab.calcMed(R[5])};});
 fs.writeFileSync('recoleccion.xlsx',Buffer.from(X.b64,'base64'));
 console.log('calculado de una medición:',JSON.stringify(X.calc,(k,v)=>typeof v==='number'?Math.round(v*100)/100:v));
 assert.ok(X.calc.perimetro_est_cm>100&&X.calc.kg_schaeffer>100&&X.calc.kg_cinta_est>100&&X.calc.volumen_tronco_l>50,'cálculos');
 const fo=X.A.formulas.find(f=>f.f==='app');assert.ok(fo.n>=18,'animales con báscula');assert.ok(fo.sesgo<-.03,'ve el sesgo (los reales pesan más)');
 const lib=X.A.libres.find(o=>o.t==='Alzada y fondo de pecho');assert.ok(lib.r&&lib.r.loo<.03,'el ajuste libre encuentra el modelo ('+(lib.r&&lib.r.loo)+')');assert.ok(Math.abs(lib.r.c[2]-.8)<.15,'γ ≈ 0.8: '+lib.r.c[2]);
 console.log('fórmulas:',X.A.formulas.map(f=>`${f.f} n=${f.n} ${f.mape!=null?(f.mape*100).toFixed(1)+'%':''} cal=${f.cal?(f.cal.loo*100).toFixed(1)+'%':'—'}`).join(' · '));
 console.log('ajustes:',X.A.libres.map(o=>`${o.t}: ${o.r?(o.r.loo*100).toFixed(2)+'%':'—'}`).join(' · '));
 try{const out=execFileSync(process.env.PY||'python3',['-c',`import openpyxl,sys
w=openpyxl.load_workbook(sys.argv[1]);print(w.sheetnames)
for n in w.sheetnames:
  s=w[n];print(n,s.max_row,'x',s.max_column)
m=w['Mediciones'];h=[c.value for c in m[1]];assert 'kg_schaeffer' in h and 'perimetro_est_cm' in h and 'kg_bascula' in h and 'camara_width' in h,h
assert w['Tomas'].max_row>50 and w['Pares'].max_row>20
`,'recoleccion.xlsx'],{encoding:'utf8'});console.log(out.trim());paso('Excel abre con openpyxl');}catch(e){if(/No module named/.test(String(e.stderr||e.message)))console.log('(sin openpyxl: no se abrió el Excel)');else throw e;}
 // el ZIP por partes, desde la pantalla
 await p.evaluate(()=>{window.__env=[];Documentos.enviar=async(n,buf,m,ti,tipo)=>{const u=new Uint8Array(buf);let s='';for(let i=0;i<u.length;i+=0x8000)s+=String.fromCharCode.apply(null,u.subarray(i,i+0x8000));window.__env.push({n,tipo,b64:btoa(s)});return true;};});
 await p.evaluate(()=>{location.hash='#pclab/resumen';});await p.waitForTimeout(400);
 await p.click('[data-act="labExpSes"]');await p.waitForSelector('#sheet [data-act="labExpParte"]');await p.screenshot({path:'rec_exportar.png'});
 await p.click('#sheet [data-act="labExpParte"][data-k="0"]');for(let i=0;i<50&&!(await p.evaluate(()=>window.__env.length));i++)await p.waitForTimeout(200);
 const Z=await p.evaluate(()=>window.__env[0]);assert.ok(Z&&/\.zip$/.test(Z.n)&&Z.tipo==='application/zip',Z&&Z.n);fs.writeFileSync('recoleccion.zip',Buffer.from(Z.b64,'base64'));
 const zl=execFileSync('python3',['-c',`import zipfile,sys
z=zipfile.ZipFile(sys.argv[1]);assert z.testzip() is None;n=z.namelist();print(len(n));print('\\n'.join(n[:14]))`,'recoleccion.zip'],{encoding:'utf8'});
 console.log(zl.trim().split('\n').slice(0,6).join(' | '));
 assert.match(zl,/datos\.xlsx/);assert.match(zl,/mediciones\.csv/);assert.match(zl,/001_animal-1_\w+\/costado_hd\.jpg/);assert.match(zl,/costado_t3\.jpg/);assert.match(zl,/depuracion\.json/);
 assert.ok(await p.$('#sheet .ok'),'parte marcada como enviada');paso('ZIP por partes válido: '+Z.n);
 // Excel desde la sesión
 await p.evaluate(()=>{window.__env=[];closeSheet();});await p.click('.lab-acts [data-act="labExcel"]');for(let i=0;i<50&&!(await p.evaluate(()=>window.__env.length));i++)await p.waitForTimeout(200);
 assert.match(await p.evaluate(()=>window.__env[0].n),/\.xlsx$/);
 // en el registro: las sesiones guardadas con Excel y ZIP
 await p.evaluate(()=>{location.hash='#pclab/registro';});await p.waitForTimeout(400);assert.match(await p.$eval('#app',e=>e.textContent),/Sesiones de recolección/);
 // el CSV lleva lo calculado
 const csv=await p.evaluate(()=>PcLab.csv?PcLab.csv():null);
 await p.click('[data-act="labSesFin"]').catch(()=>{});
 if(process.env.IDIOMA==='xx'){const K=await p.evaluate(()=>[...I18N_REC.keys()]);fs.writeFileSync('claves_rec.json',JSON.stringify(K.map(k=>[k,1])));console.log('claves grabadas',K.length);}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');await b.close();if(errs.length)process.exit(1);
})().catch(e=>{console.error('FALLA',e.message);process.exit(1);});
