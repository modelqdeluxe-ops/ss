// Reporte del día por archivo, sin servidor: Rumentis (administración) + Rumentis Campo (colaborador).
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const cfg=(app,prueba)=>CFG0.replace("window.RUMENTIS={app:'jefe',prueba:false,","window.RUMENTIS={app:'"+app+"',prueba:"+prueba+",").replace(/servidorEquipo:'[^']*'/,"servidorEquipo:''");   // sin internet: solo archivos
const IDI=process.env.IDIOMA||'es';const PAGS=[];
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});
 const errs=[];
 async function app(nombre,app,prueba){
  const ctx=await b.newContext({viewport:{width:393,height:852},deviceScaleFactor:1.5});const p=await ctx.newPage();
  p.on('pageerror',e=>errs.push(nombre+': '+e.message));p.on('console',m=>{if(m.type()==='error'&&!/favicon|ERR_|net::|Failed to load resource/.test(m.text()))errs.push(nombre+' consola: '+m.text());});
  await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg(app,prueba)}));
  await p.addInitScript(i=>{window.__IDI=i;},IDI);await p.goto('http://127.0.0.1:8111/index.html#hoy');
  await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__IDI);});await p.reload();await p.waitForTimeout(900);
  // compartir: en vez de abrir WhatsApp, se guarda el archivo para pasarlo a la otra app
  await p.evaluate(()=>{window.__arch=[];EquipoNucleo.compartirArchivo=(n,u)=>{window.__arch.push({n,b:EquipoNucleo.b64(u)});return true;};});
  await p.evaluate(()=>{window.__sinFoto=false;Fotos.tomar=async()=>window.__sinFoto?null:await new Promise(r=>{const c=document.createElement('canvas');c.width=96;c.height=72;const g=c.getContext('2d');g.fillStyle='#2e7d32';g.fillRect(0,0,96,72);g.fillStyle='#fff';g.fillRect(20,20,40,20);c.toBlob(r,'image/jpeg',.8);});});
  PAGS.push(p);return p;}
 const paso=t=>console.log('·',t);
 const terminar=async(p,id,nota)=>{await p.click(`[data-act="vqHecha"][data-p="${id}"]`);await p.waitForTimeout(300);await p.click('#sheet [data-act="vqEviFoto"]');await p.waitForTimeout(400);if(nota)await p.fill('#vqEviNota',nota);await p.click('#sheet [data-act="vqEviEnviar"]');await p.waitForTimeout(500);};
 const am=(a,r,m)=>{if(IDI==='es')assert.match(a,r,m);};
 const vistos=new Map();
 const ultimo=async p=>{const n0=vistos.get(p)||0;for(let i=0;i<60;i++){const n=await p.evaluate(()=>window.__arch.length);if(n>n0){vistos.set(p,n);return p.evaluate(()=>window.__arch[window.__arch.length-1]);}await p.waitForTimeout(100);}return undefined;};
 // abrir un archivo como si se tocara en WhatsApp (puente Android)
 const abrir=async(p,a)=>{await p.evaluate(t=>{let x=t;window.Recibido={tomar:()=>{const r=x;x='';return r;},pasar:()=>false};archivoRecibido();},a.b);await p.waitForTimeout(900);};
 const toast=p=>p.evaluate(()=>document.querySelector('#toast').textContent);

 // ---------- la administración crea una licencia ----------
 const J=await app('admin','jefe',true);
 await J.evaluate(()=>cargarDemo());await J.waitForTimeout(300);
 const lic=await J.evaluate(async()=>{await Equipo.asegurarEquipo();const c=EquipoNucleo.nuevaLicencia();Equipo.crearLicencia(c,{prueba:true});const fi=await Equipo.fichaEq();return {c,url:EquipoNucleo.enlace(fi,c)};});
 // ---------- el colaborador se activa sin internet y envía su solicitud ----------
 const V=await app('campo','vaquero',true);
 await V.fill('#vqForm [name=nombre]','María López');await V.fill('#vqForm [name=lic]',lic.url);await V.click('[data-act="vqActivar"]');await V.waitForTimeout(900);
 assert.equal(await V.evaluate(()=>Vaquero.VQ().estado),'pendiente');
 am(await V.$eval('#app h1',e=>e.textContent),/Esperando confirmación/);
 assert.equal(await V.$eval('.vq-logo span',e=>e.textContent),IDI==='en'?'Team':IDI==='pt'?'Equipe':'Equipo');
 await V.screenshot({path:'rp_solicitud.png'});
 await V.click('[data-act="vqEnviarArchivo"]');const sol=await ultimo(V);am(sol.n,/^solicitud-maria-lopez-\d{4}\.rumentis$/,sol.n);
 await abrir(J,sol);
 assert.deepEqual(await J.evaluate(()=>Object.values(S.config.equipo.vaqueros).map(v=>v.nombre+'|'+v.estado)),['María López|activo']);paso('solicitud por archivo aceptada');
 // ---------- hora del reporte y tareas ----------
 await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(400);
 await J.click('[data-act="eqAjustes"]');await J.waitForTimeout(300);await J.selectOption('#eqHora','16');await J.waitForTimeout(200);assert.equal(await J.evaluate(()=>S.config.equipo.horaReporte),16);
 // primero con revisión a mano (luego se prueba el registro automático)
 await J.click('[data-act="eqAutoReg"]');await J.waitForTimeout(150);assert.equal(await J.evaluate(()=>S.config.equipo.autoReg),false);
 await J.screenshot({path:'rp_ajustes.png'});await J.evaluate(()=>closeSheet());await J.waitForTimeout(200);
 const vid=await J.evaluate(()=>Object.keys(S.config.equipo.vaqueros)[0]);
 const tarea=async(t,para,rep,acc='')=>{await J.evaluate(()=>FORMS.eqTarea({}));await J.waitForTimeout(250);await J.click(`#sheet .opts[data-name=acc] .opt[data-v="${acc}"]`);if(t)await J.fill('#sheet [name=t]',t);
   await J.click(`#sheet .opts[data-name=para] .opt[data-v="${para}"]`);await J.selectOption('#sheet [name=rep]',rep);await J.click('#sheet button[type=submit]');await J.waitForTimeout(250);};
 await tarea('Revisar bebederos','todos','dia');await tarea('Revisar cercos','todos','s2');await tarea('Limpiar comederos del corral 2',vid,'una');
 await tarea('',vid,'una','sanidad');   // al anotar sanidad pide la foto
 assert.equal(await J.evaluate(()=>S.config.tareas.filter(t=>t.para).map(t=>t.rep).join(',')),'dia,s2,una,una');
 assert.equal(await J.evaluate(()=>S.config.tareas.filter(t=>t.para).pop().t),'Aplicar sanidad','el texto se escribe solo');
 assert.equal(await J.evaluate(()=>Agenda.tareas().some(t=>/bebederos|cercos/.test(t.t))),false,'las del equipo no llenan la agenda de la administración');
 await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(400);await J.screenshot({path:'rp_equipo0.png',fullPage:true});
 await J.click('.eq-arch [data-act="eqEnviarArchivo"]');const act1=await ultimo(J);am(act1.n,/^actualizacion-.*\.campo$/,act1.n);
 await abrir(V,act1);
 assert.equal(await V.evaluate(()=>Vaquero.VQ().estado),'activo');assert.equal(await V.evaluate(()=>Vaquero.VQ().horaRep),16);paso('activo, con la hora del reporte (16:00)');
 await V.evaluate(()=>{location.hash='#hoy';});await V.waitForTimeout(400);
 const hoyTxt=await V.$eval('#app',e=>e.textContent);
 am(hoyTxt,/Revisar bebederos/);am(hoyTxt,/Revisar cercos/);am(hoyTxt,/Limpiar comederos/);am(hoyTxt,/16:00/);
 // ---------- el día: registros y tareas ----------
 const L0=await V.evaluate(()=>calc().act[0].id);
 await V.evaluate(()=>{location.hash='#registrar';});await V.waitForTimeout(300);
 await V.click('.tile[data-f="alimento"]');await V.waitForTimeout(400);await V.click('#sheet [data-act="llenarProg"]');await V.waitForTimeout(200);await V.click('#sheet button[type=submit]');await V.waitForTimeout(500);
 await V.evaluate(l=>FORMS.sanidad({lote:l}),L0);await V.waitForTimeout(300);await V.fill('#sheet [name=producto]','Ivermectina 1%');await V.click('#sheet button[type=submit]');await V.waitForTimeout(500);
 await V.waitForTimeout(300);am(await V.$eval('#sheet',e=>e.textContent),/foto de evidencia/);
 assert.equal(await V.evaluate(()=>S.config.tareas.find(t=>t.t==='Aplicar sanidad').hecho),false,'sin foto no se termina');
 // sin foto sigue pendiente
 await V.evaluate(()=>{window.__sinFoto=true;});await V.click('#sheet [data-act="vqEviFoto"]');await V.waitForTimeout(400);assert.equal(await V.$eval('#sheet [data-act="vqEviEnviar"]',e=>e.disabled),true,'sin foto, Enviar deshabilitado');await V.evaluate(()=>closeSheet());await V.waitForTimeout(300);
 assert.equal(await V.evaluate(()=>S.config.tareas.find(t=>t.t==='Aplicar sanidad').hecho),false);
 await V.evaluate(()=>{window.__sinFoto=false;});await V.evaluate(()=>{location.hash='#tareas';});await V.waitForTimeout(300);
 am(await V.$eval('#app',e=>e.textContent),/Falta la foto/);
 const idS=await V.evaluate(()=>S.config.tareas.find(t=>t.t==='Aplicar sanidad').id);await terminar(V,idS);
 assert.equal(await V.evaluate(()=>S.config.tareas.find(t=>t.t==='Aplicar sanidad').hecho),true,'con foto queda terminada');paso('tarea ligada a sanidad: terminada con foto');
 await V.evaluate(()=>{location.hash='#tareas';});await V.waitForTimeout(300);
 const idT=await V.evaluate(()=>Object.fromEntries(S.config.tareas.map(t=>[t.t,t.id])));
 for(const t of ['Revisar bebederos','Revisar cercos','Limpiar comederos del corral 2'])await terminar(V,idT[t],t==='Revisar bebederos'?'Uno gotea.':'');
 const txtT=await V.$$eval('.vq-tc',e=>e.map(x=>x.textContent).join('|'));assert.ok(!/Revisar bebederos|Revisar cercos|Limpiar comederos/.test(txtT),'hechas: ya no salen como pendientes');
 am(await V.$eval('#app',e=>e.textContent),/Terminadas hoy/);
 const cola=await V.evaluate(()=>Vaquero.VQ().cola.map(o=>o.k));paso('cola: '+cola.join(' '));assert.equal(cola.filter(k=>k==='tarea').length,4);
 assert.equal(await V.evaluate(()=>Vaquero.VQ().cola.filter(o=>o.k==='tarea'&&o.foto).length),4,'cada tarea lleva su foto');
 // perfil con foto y cargo
 await V.evaluate(()=>FORMS.vqPerfil());await V.waitForTimeout(300);await V.click('#sheet [data-act="vqFotoPerfil"]');await V.waitForTimeout(400);
 await V.fill('#sheet [name=cargo]','Encargada de corrales');await V.fill('#sheet [name=tel]','+504 9999 1234');await V.click('#sheet button[type=submit]');await V.waitForTimeout(400);
 assert.equal(await V.evaluate(()=>Vaquero.VQ().perfil.cargo),'Encargada de corrales');
 // no puede borrar registros
 await V.evaluate(()=>{location.hash='#registrar';});await V.waitForTimeout(300);assert.equal(await V.$$eval('#app .del',e=>e.length),0,'sin botón de borrar');
 // ---------- el reporte ----------
 assert.equal((await V.evaluate(()=>colaAvisosPropia()))[0].id,'rep:'+await V.evaluate(()=>hoy()),'recordatorio de hoy mientras no lo envía');
 await V.evaluate(()=>{location.hash='#hoy';});await V.waitForTimeout(300);
 await V.evaluate(()=>document.querySelector('.vq-rep [data-f="vqReporte"]').click());await V.waitForTimeout(400);
 const hoja=await V.$eval('#sheet',e=>e.textContent);am(hoja,/Tareas de hoy/);am(hoja,/Revisar cercos/);am(hoja,/WhatsApp o correo/);
 await V.fill('#sheet [name=nota]','El bebedero del corral 3 gotea.');await V.screenshot({path:'rp_hoja.png'});
 await V.click('#sheet button[type=submit]');await V.waitForTimeout(600);
 const rep1=await ultimo(V);am(rep1.n,/^reporte-maria-lopez-\d{4}-\d\d-\d\d-\d{4}\.rumentis$/,rep1.n);
 am(await V.$eval('.vq-rep',e=>e.textContent),/Enviado a las/);
 assert.ok(!(await V.evaluate(()=>colaAvisosPropia().some(a=>a.id==='rep:'+hoy()))),'ya no se recuerda hoy');
 await V.screenshot({path:'rp_enviado.png'});paso('reporte enviado: '+rep1.n);
 // ---------- la administración lo revisa y lo registra ----------
 const antes=await J.evaluate(()=>allItems().length);
 await abrir(J,rep1);
 am(await J.evaluate(()=>location.hash),/^#equipo\/reporte\//);await J.waitForTimeout(300);
 const pag=await J.$eval('#app',e=>e.textContent);
 am(pag,/El bebedero del corral 3 gotea/);am(pag,/1 de 2 esta semana/);am(pag,/Para registrar en tu finca/);
 assert.equal(await J.evaluate(()=>allItems().length),antes,'nada se registra antes de tocar Registrar');
 await J.screenshot({path:'rp_revision.png',fullPage:true});
 // deja fuera una entrega de alimento
 const nOps=await J.$$eval('button.eq-op',e=>e.length);const primera=await J.$eval('button.eq-op',e=>e.dataset.s);
 await J.click(`button.eq-op[data-s="${primera}"]`);await J.waitForTimeout(250);
 assert.equal(await J.$eval(`button.eq-op[data-s="${primera}"]`,e=>e.getAttribute('aria-pressed')),'false');
 await J.click('[data-act="eqRegistrar"]');await J.waitForTimeout(500);
 am(await J.$eval('#sheet',e=>e.textContent),/Reporte registrado/);await J.screenshot({path:'rp_registrado.png'});await J.evaluate(()=>closeSheet());await J.waitForTimeout(300);
 assert.equal(await J.evaluate(()=>Object.keys(S.config.equipo.fotos||{}).length),4,'llegan las 4 fotos');
 assert.equal(await J.$$eval('.eq-rt .eq-th.con',e=>e.length),4,'miniaturas en el reporte');
 assert.equal(await J.evaluate(()=>S.config.equipo.vaqueros[Object.keys(S.config.equipo.vaqueros)[0]].perfil.cargo),'Encargada de corrales');
 assert.ok(!/Actividad/.test(await J.$eval('#app',e=>e.textContent)),'sin registro de actividad');
 await J.screenshot({path:'rp_detalle.png',fullPage:true});
 const r1=await J.evaluate(()=>Equipo.REP()[0]);assert.equal(r1.estado,'registrado');assert.equal(r1.omit,1);assert.equal(r1.reg,nOps-1);
 assert.equal(await J.evaluate(()=>allItems().some(i=>i.producto==='Ivermectina 1%'&&i.por&&i.por.n==='María López')),true);
 const T=await J.evaluate(()=>Object.fromEntries(S.config.tareas.filter(t=>t.para).map(t=>[t.t,t.rep==='una'?!!t.hecho:(t.hechos||[]).length])));
 assert.deepEqual(T,{'Revisar bebederos':1,'Revisar cercos':1,'Limpiar comederos del corral 2':true,'Aplicar sanidad':true});paso(`registrado: ${r1.reg} cambios, 1 dejado fuera`);
 assert.equal(await J.evaluate(async t=>EquipoNucleo.recibirArchivo(EquipoNucleo.deB64(t),Equipo.alRecibir),rep1.b),false,'el mismo reporte no se abre dos veces');
 am(await toast(J),/ya lo abriste/);
 // ---------- la actualización vuelve al colaborador ----------
 // pide repetir "Limpiar comederos"
 const iL=await J.evaluate(()=>(Equipo.REP()[0].res.tareas||[]).findIndex(t=>/Limpiar comederos/.test(t.t)));
 await J.click(`[data-act="eqRepetir"][data-i="${iL}"]`);await J.waitForTimeout(300);await J.click('#sheet [data-act="confirmarSi"]');await J.waitForTimeout(400);
 assert.equal(await J.evaluate(()=>S.config.tareas.find(t=>/Limpiar comederos/.test(t.t)).hecho),false);am(await J.$eval('#app',e=>e.textContent),/Se pidió repetirla/);
 await J.click('.eq-hecho [data-act="eqEnviarArchivo"]');const act2=await ultimo(J);await abrir(V,act2);
 await V.evaluate(()=>{location.hash='#tareas';});await V.waitForTimeout(300);am(await V.$eval('#app',e=>e.textContent),/Repetir con otra foto/);await V.screenshot({path:'rp_repetir.png',fullPage:true});
 assert.deepEqual(await V.evaluate(()=>Vaquero.resumenDia(hoy()).tareas.filter(t=>/Limpiar/.test(t.t)).map(t=>t.ok)),[0],'en el resumen vuelve a estar pendiente');
 const idL=await V.evaluate(()=>S.config.tareas.find(t=>/Limpiar comederos/.test(t.t)).id);await terminar(V,idL);
 assert.deepEqual(await V.evaluate(()=>Vaquero.resumenDia(hoy()).tareas.filter(t=>/Limpiar/.test(t.t)).map(t=>t.ok)),[1]);paso('tarea repetida con foto nueva');
 assert.equal(await V.evaluate(()=>Vaquero.VQ().cola.length),1,'todo confirmado, menos la tarea repetida');
 await V.evaluate(()=>{location.hash='#hoy';});await V.waitForTimeout(300);
 am(await V.$eval('.vq-rep',e=>e.textContent),/registrado por la administración/);await V.screenshot({path:'rp_confirmado.png'});paso('el colaborador ve la confirmación');
 // ---------- dos reportes: el segundo incluye lo que el primero no alcanzó a registrar ----------
 const sanidad=async prod=>{await V.evaluate(l=>FORMS.sanidad({lote:l}),L0);await V.waitForTimeout(300);await V.fill('#sheet [name=producto]',prod);await V.click('#sheet button[type=submit]');await V.waitForTimeout(400);};
 await sanidad('Vitamina AD3');
 am(await V.$eval('.vq-rep',e=>e.textContent),/hay registros nuevos/);
 await V.evaluate(()=>FORMS.vqReporte());await V.waitForTimeout(300);await V.click('#sheet button[type=submit]');await V.waitForTimeout(500);const rep2=await ultimo(V);
 await sanidad('Oxitetraciclina');
 await V.evaluate(()=>FORMS.vqReporte());await V.waitForTimeout(300);await V.click('#sheet button[type=submit]');await V.waitForTimeout(500);const rep3=await ultimo(V);
 // ahora automático: al abrir el reporte queda registrado
 await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(300);await J.click('[data-act="eqAjustes"]');await J.waitForTimeout(250);await J.click('[data-act="eqAutoReg"]');await J.evaluate(()=>closeSheet());
 await abrir(J,rep2);am(await toast(J),/registrado en tu finca/);await abrir(J,rep3);
 const est=await J.evaluate(()=>Object.values(S.config.equipo.reportes).sort((a,b)=>b.ts-a.ts).slice(0,2).map(r=>r.estado+(r.auto?'-auto':'')));assert.equal(await J.evaluate(()=>Equipo.REP().length),1,'el del mismo día reemplaza a los anteriores en la lista');assert.deepEqual(est,['registrado-auto','registrado-auto']);
 am(await J.$eval('#app',e=>e.textContent),/Registrado automáticamente/);await J.screenshot({path:'rp_auto.png',fullPage:true});
 await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(400);
 assert.equal(await J.$$eval('.eq-pend .row',e=>e.length),0,'nada por revisar');
 am(await J.$eval('.eq-colab',e=>e.textContent),/Reportó/);
 await J.screenshot({path:'rp_equipo1.png',fullPage:true});
 assert.deepEqual(await J.evaluate(()=>allItems().filter(i=>i.por&&i.tipo==='sanidad').map(i=>i.producto).sort()),['Ivermectina 1%','Oxitetraciclina','Vitamina AD3']);paso('reporte acumulado sin duplicar');
 // ---------- la hoja del colaborador y la fila en Más ----------
 await J.evaluate(()=>closeSheet());await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(300);
 await J.click('.eq-colab[href^="#equipo/colab/"]');await J.waitForTimeout(500);const pc=await J.$eval('#app',e=>e.textContent);
 am(pc,/días con reporte/);am(pc,/Encargada de corrales/);am(pc,/Fotos de evidencia/);am(pc,/Revocar licencia/);
 assert.equal(await J.$$eval('.eq-galeria .eq-th.con',e=>e.length),5,'las fotos de evidencia se ven (4 + la repetida)');
 assert.equal(await J.$$eval('.eq-pf .eq-av img:not([hidden])',e=>e.length),1,'foto de perfil');
 await J.screenshot({path:'rp_colab.png',fullPage:true});
 await J.click('.eq-galeria .eq-th');await J.waitForTimeout(400);assert.equal(await J.$$eval('#sheet .eq-visor img[src]',e=>e.length),1,'visor');await J.screenshot({path:'rp_visor.png'});await J.evaluate(()=>closeSheet());await J.waitForTimeout(200);
 await J.evaluate(()=>closeSheet());await J.evaluate(()=>{location.hash='#mas';});await J.waitForTimeout(300);
 await V.evaluate(()=>{location.hash='#mas';});await V.waitForTimeout(300);am(await V.$eval('#app',e=>e.textContent),/Reporte diario16:00/);await V.screenshot({path:'rp_mas.png',fullPage:true});
 // sin palabras "jefe", "vaquero" ni "dueño" en pantalla
 for(const [p,n] of [[J,'admin'],[V,'campo']])for(const h of ['#equipo','#hoy','#mas','#tareas','#registrar']){await p.evaluate(h=>{location.hash=h;},h);await p.waitForTimeout(200);
   const t=await p.$eval('#app',e=>e.textContent);const m=IDI!=='es'?null:t.match(/\b(jefe|vaquer[oa]s?|dueño)\b/i);assert.ok(!m,`${n} ${h}: "${m&&m[0]}"`);}
 if(IDI==='xx'){const K=new Set();for(const p of PAGS){try{for(const k of await p.evaluate(()=>[...I18N_REC.keys()]))K.add(k);}catch(e){}}
   fs.writeFileSync('claves_reporte.json',JSON.stringify([...K].map(k=>[k,1])));console.log('claves grabadas',K.size);}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');
 await b.close();
})().catch(e=>{console.error('FALLA',e.message);process.exit(1);});
