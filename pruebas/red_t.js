// Por internet (servidor local = el mismo código que Cloudflare): registros en vivo, fotos de evidencia, perfil,
// reporte automático, revocar licencia (cierra la sesión) y activar la licencia de reemplazo. Cuenta las solicitudes.
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');
const SRV='http://127.0.0.1:8790';const IDI=process.env.IDIOMA||'es';const PAGS=[];
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const cfg=(app,prueba)=>CFG0.replace("window.RUMENTIS={app:'jefe',prueba:false,","window.RUMENTIS={app:'"+app+"',prueba:"+prueba+",").replace(/servidorEquipo:'[^']*'/,"servidorEquipo:'"+SRV+"'");
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const errs=[];const SOL={};
 async function app(n,app,prueba){const ctx=await b.newContext({viewport:{width:393,height:852},deviceScaleFactor:1.5});const p=await ctx.newPage();
  p.on('pageerror',e=>errs.push(n+': '+e.message));SOL[n]={};
  p.on('request',r=>{if(r.url().startsWith(SRV)){const k=new URL(r.url()).pathname;SOL[n][k]=(SOL[n][k]||0)+1;}});
  await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg(app,prueba)}));
  await p.addInitScript(i=>{window.__IDI=i;},IDI);await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__IDI);});await p.reload();await p.waitForTimeout(900);
  await p.evaluate(()=>{Fotos.tomar=async()=>await new Promise(r=>{const c=document.createElement('canvas');c.width=96;c.height=72;const g=c.getContext('2d');g.fillStyle='#1565c0';g.fillRect(0,0,96,72);c.toBlob(r,'image/jpeg',.8);});});
  PAGS.push(p);return p;}
 const total=n=>Object.values(SOL[n]).reduce((a,b)=>a+b,0);
 const esperar=async(pg,fn,ms=15000,arg)=>{const t0=Date.now();while(Date.now()-t0<ms){try{if(await pg.evaluate(fn,arg))return Date.now()-t0;}catch(e){}await pg.waitForTimeout(200);}return -1;};
 const paso=t=>console.log('·',t);
 const terminar=async(p,id,nota)=>{await p.click(`[data-act="vqHecha"][data-p="${id}"]`);await p.waitForTimeout(300);await p.click('#sheet [data-act="vqEviFoto"]');await p.waitForTimeout(400);if(nota)await p.fill('#vqEviNota',nota);await p.click('#sheet [data-act="vqEviEnviar"]');await p.waitForTimeout(500);};
 const J=await app('admin','jefe',true);await J.evaluate(()=>cargarDemo());await J.waitForTimeout(300);
 const lic=await J.evaluate(async()=>{await Equipo.asegurarEquipo();const c=EquipoNucleo.nuevaLicencia();Equipo.crearLicencia(c,{prueba:true});await Equipo.sincronizar();return c;});
 const V=await app('campo','vaquero',true);await V.fill('#vqForm [name=nombre]','María López');await V.fill('#vqForm [name=lic]',lic);await V.click('[data-act="vqActivar"]');
 assert.ok(await esperar(V,()=>Vaquero.VQ().estado==='activo')>=0,'activa por internet');paso('activada por internet');
 await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(400);
 const txt=await J.$eval('#app',e=>e.textContent);assert.ok(!/Conectado por internet|se actualiza solo|Actividad/.test(txt),'sin textos de relleno ni actividad');
 assert.equal(await J.$$eval('.eq-arch',e=>e.length),0,'con servidor, sin botones de archivo en la pantalla principal');
 // un registro llega solo
 const L0=await V.evaluate(()=>calc().act[0].id);await V.evaluate(l=>FORMS.sanidad({lote:l}),L0);await V.waitForTimeout(300);await V.fill('#sheet [name=producto]','Ivermectina 1%');await V.click('#sheet button[type=submit]');
 const t1=await esperar(J,()=>allItems().some(i=>i.producto==='Ivermectina 1%'&&i.por));assert.ok(t1>=0,'registro en vivo');paso('registro en vivo en '+t1+' ms');
 // la administración asigna una tarea con indicaciones; llega a Campo
 await J.evaluate(()=>FORMS.eqTarea({}));await J.waitForTimeout(250);await J.click('#sheet .opts[data-name=acc] .opt[data-v=""]');await J.fill('#sheet [name=t]','Revisar bebederos');
 await J.fill('#sheet [name=nota]','Los del corral 3 y 4.');await J.click('#sheet button[type=submit]');await J.waitForTimeout(300);
 assert.ok(await esperar(V,()=>(S.config.tareas||[]).some(t=>t.t==='Revisar bebederos'))>=0,'la tarea llega');
 await V.evaluate(()=>{location.hash='#tareas';});await V.waitForTimeout(300);assert.match(await V.$eval('#app',e=>e.textContent),/Los del corral 3 y 4/);
 const idT=await V.evaluate(()=>S.config.tareas.find(t=>t.t==='Revisar bebederos').id);await terminar(V,idT,'Uno gotea.');
 const t3=await esperar(J,()=>{const t=S.config.tareas.find(t=>t.t==='Revisar bebederos');return t&&t.hecho&&t.foto&&(S.config.equipo.fotos||{})[t.foto];});assert.ok(t3>=0,'tarea con foto en la administración');paso('tarea terminada con foto: llega en '+t3+' ms');
 // perfil
 await V.evaluate(()=>FORMS.vqPerfil());await V.waitForTimeout(300);await V.click('#sheet [data-act="vqFotoPerfil"]');await V.waitForTimeout(300);await V.fill('#sheet [name=cargo]','Vaquera');await V.fill('#sheet [name=cargo]','Encargada');await V.click('#sheet button[type=submit]');
 assert.ok(await esperar(J,()=>Object.values(S.config.equipo.vaqueros).some(v=>v.perfil&&v.perfil.cargo==='Encargada'&&v.perfil.foto))>=0,'perfil');paso('perfil con foto');
 // a la hora del reporte se envía solo; si después termina tareas, llega actualizado (uno por día en las listas)
 await V.evaluate(()=>{const r=Vaquero.VQ().ultRep;if(r)r.ts-=11*60e3;Vaquero.VQ().horaRep=0;Vaquero.autoReporte();});
 assert.ok(await esperar(V,()=>!!(Vaquero.VQ().ultRep&&Vaquero.VQ().ultRep.red))>=0,'reporte enviado solo');paso('reporte enviado solo por internet');
 const t2=await esperar(J,()=>Equipo.REP().length===1&&Equipo.REP()[0].estado==='registrado'&&(Equipo.REP()[0].res.tareas||[]).some(t=>t.ok&&t.foto));assert.ok(t2>=0,'llega completo y se registra solo');paso('reporte completo registrado solo en '+t2+' ms');
 await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(500);assert.match(await J.$eval('.eq-colab',e=>e.textContent),/Reportó/);await J.screenshot({path:'red_equipo.png',fullPage:true});
 await J.evaluate(()=>{location.hash='#equipo/reporte/'+encodeURIComponent(Equipo.REP()[0].rid);});await J.waitForTimeout(700);
 assert.ok(await J.$$eval('.eq-rt .eq-th.con',e=>e.length)>=1,'foto en el reporte');await J.screenshot({path:'red_reporte.png',fullPage:true});
 await V.evaluate(()=>{location.hash='#hoy';});await V.waitForTimeout(400);await V.screenshot({path:'red_campo.png'});
 await V.evaluate(()=>Vaquero.autoReporte());await V.waitForTimeout(800);assert.equal(await J.evaluate(()=>Equipo.REP().length),1,'uno solo al día');
 // en reposo casi no hay solicitudes
 const r0={J:total('admin'),V:total('campo')};await J.waitForTimeout(20000);const r1={J:total('admin')-r0.J,V:total('campo')-r0.V};
 paso(`en reposo (20 s): administración ${r1.J}, Campo ${r1.V} solicitudes`);assert.ok(r1.J<=3&&r1.V<=3,'pocas solicitudes en reposo');
 // ---------- revocar: se cierra la sesión y sale una licencia nueva ----------
 const vid=await J.evaluate(()=>Object.keys(S.config.equipo.vaqueros)[0]);
 await J.evaluate(v=>{location.hash='#equipo/colab/'+v;},vid);await J.waitForTimeout(500);await J.screenshot({path:'red_colab.png',fullPage:true});
 await J.click('[data-act="eqBaja"]');await J.waitForTimeout(300);await J.click('#sheet [data-act="confirmarSi"]');await J.waitForTimeout(600);
 const nueva=await J.evaluate(()=>Object.values(S.config.equipo.licencias).find(l=>l.estado==='libre'));assert.ok(nueva&&nueva.reemplaza===lic,'licencia de reemplazo');
 assert.equal(await J.evaluate(c=>S.config.equipo.licencias[c].estado,lic),'baja');
 assert.ok(await esperar(V,()=>Vaquero.VQ().estado==='baja',20000)>=0,'Campo cierra la sesión');await V.waitForTimeout(1500);
 assert.match(await V.$eval('#app',e=>e.textContent),/Sesión cerrada/);
 assert.equal(await V.evaluate(()=>allItems().length),0,'los datos de la finca se borraron');
 assert.equal(await V.evaluate(async()=>(await Fotos.ids()).filter(k=>/^eq-/.test(k)).length),0,'fotos borradas');await V.screenshot({path:'red_baja.png'});paso('revocada: sesión cerrada y datos borrados');
 // la licencia vieja ya no sirve
 await V.click('[data-act="vqOtra"]');await V.waitForTimeout(500);
 await V.fill('#vqForm [name=nombre]','María López');await V.fill('#vqForm [name=lic]',lic);await V.click('[data-act="vqActivar"]');
 assert.ok(await esperar(V,()=>Vaquero.VQ().estado==='rechazada'||/no|revoc|baja/i.test((document.querySelector('.err')||{}).textContent||''),15000)>=0,'la licencia revocada no entra');paso('la licencia revocada no se puede reutilizar');
 if(await V.$('[data-act="vqOtra"]'))await V.click('[data-act="vqOtra"]');await V.waitForTimeout(500);
 // con la licencia nueva entra sola
 await V.fill('#vqForm [name=nombre]','Pedro Díaz');await V.fill('#vqForm [name=lic]',nueva.c);await V.click('[data-act="vqActivar"]');
 const t4=await esperar(V,()=>Vaquero.VQ().estado==='activo',25000);assert.ok(t4>=0,'la licencia nueva se acepta sola');paso('licencia nueva aceptada sola en '+t4+' ms');
 assert.ok(await esperar(V,()=>calc().act.length>0)>=0,'recibe los lotes');
 assert.deepEqual(await J.evaluate(()=>Object.values(S.config.equipo.vaqueros).map(v=>v.nombre+'|'+v.estado).sort()),['María López|baja','Pedro Díaz|activo']);
 if(IDI==='xx'){const K=new Set();for(const p of PAGS){try{for(const k of await p.evaluate(()=>[...I18N_REC.keys()]))K.add(k);}catch(e){}}fs.writeFileSync('claves_red.json',JSON.stringify([...K].map(k=>[k,1])));console.log('claves grabadas',K.size);}
 console.log('solicitudes:',JSON.stringify(SOL));
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');await b.close();
})().catch(e=>{console.error('FALLA',e.message);process.exit(1);});
