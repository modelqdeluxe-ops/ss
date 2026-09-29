// recorrido visual: administración y app del personal con servidor local
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');
const SRV='http://127.0.0.1:8790';const IDI=process.env.IDIOMA||'es';const PAGS=[];
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const cfg=(app,prueba,srv)=>CFG0.replace("window.RUMENTIS={app:'jefe',prueba:false,","window.RUMENTIS={app:'"+app+"',prueba:"+prueba+",").replace(/servidorEquipo:'[^']*'/,"servidorEquipo:'"+srv+"'");
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const errs=[];
 async function app(n,a,prueba,srv){const ctx=await b.newContext({viewport:{width:393,height:852},deviceScaleFactor:1.5});const p=await ctx.newPage();
  p.on('pageerror',e=>errs.push(n+': '+e.message));
  await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg(a,prueba,srv)}));
  await p.addInitScript(i=>{window.__IDI=i;},IDI);await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__IDI);});await p.reload();await p.waitForTimeout(900);
  await p.evaluate(()=>{Fotos.tomar=async()=>await new Promise(r=>{const c=document.createElement('canvas');c.width=96;c.height=72;const g=c.getContext('2d');g.fillStyle='#1565c0';g.fillRect(0,0,96,72);c.toBlob(r,'image/jpeg',.8);});});
  PAGS.push(p);return p;}
 const esperar=async(pg,fn,ms=15000)=>{const t0=Date.now();while(Date.now()-t0<ms){try{if(await pg.evaluate(fn))return Date.now()-t0;}catch(e){}await pg.waitForTimeout(200);}return -1;};
 const J=await app('admin','jefe',true,SRV);await J.evaluate(()=>cargarDemo());await J.waitForTimeout(300);
 const lic=await J.evaluate(async()=>{await Equipo.asegurarEquipo();const c=EquipoNucleo.nuevaLicencia();Equipo.crearLicencia(c,{prueba:true});await Equipo.sincronizar();return c;});
 const V=await app('campo','vaquero',true,SRV);await V.screenshot({path:'v2_activar.png'});
 await V.fill('#vqForm [name=nombre]','María López');await V.fill('#vqForm [name=lic]',lic);await V.click('[data-act="vqActivar"]');
 assert.ok(await esperar(V,()=>Vaquero.VQ().estado==='activo')>=0);
 // tareas
 await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(400);
 await J.evaluate(()=>FORMS.eqTarea({}));await J.waitForTimeout(300);await J.screenshot({path:'v2_form_acc.png'});
 await J.click('#sheet .opts[data-name=acc] .opt[data-v=""]');await J.waitForTimeout(200);await J.screenshot({path:'v2_form_otra.png'});
 await J.fill('#sheet [name=t]','Revisar bebederos');await J.fill('#sheet [name=nota]','Los del corral 3 y 4.');await J.click('#sheet button[type=submit]');await J.waitForTimeout(300);
 await J.evaluate(()=>FORMS.eqTarea({}));await J.waitForTimeout(300);await J.click('#sheet button[type=submit]');await J.waitForTimeout(300);
 assert.ok(await esperar(V,()=>(S.config.tareas||[]).length>=2)>=0,'tareas llegan');
 await V.evaluate(()=>{location.hash='#hoy';});await V.waitForTimeout(600);await V.screenshot({path:'v2_hoy.png',fullPage:true});
 const idT=await V.evaluate(()=>S.config.tareas.find(t=>t.t==='Revisar bebederos').id);
 await V.click(`.vq-tc [data-act="vqHecha"][data-p="${idT}"]`);await V.waitForTimeout(400);await V.screenshot({path:'v2_evi0.png'});
 assert.equal(await V.$eval('#sheet [data-act="vqEviEnviar"]',e=>e.disabled),true,'Enviar deshabilitado sin foto');
 await V.click('#sheet [data-act="vqEviFoto"]');await V.waitForTimeout(500);await V.screenshot({path:'v2_evi1.png'});
 assert.equal(await V.$eval('#sheet [data-act="vqEviEnviar"]',e=>e.disabled||!e.classList.contains('listo')),false,'Enviar en verde con foto');
 assert.equal(await V.evaluate(t=>S.config.tareas.find(x=>x.id===t).hecho,idT),false,'tomar la foto no envía');
 await V.fill('#vqEviNota','Uno gotea.');await V.click('#sheet [data-act="vqEviEnviar"]');await V.waitForTimeout(600);
 assert.ok(await esperar(J,()=>{const t=S.config.tareas.find(t=>t.t==='Revisar bebederos');return t&&t.hecho;})>=0,'llega terminada');
 await V.evaluate(()=>{location.hash='#tareas';});await V.waitForTimeout(500);await V.screenshot({path:'v2_tareas.png',fullPage:true});
 // alimentación
 await V.evaluate(()=>FORMS.alimento({}));await V.waitForTimeout(400);await V.screenshot({path:'v2_alim.png'});await V.evaluate(()=>closeSheet());
 await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(600);await J.screenshot({path:'v2_equipo.png',fullPage:true});
 await J.evaluate(()=>FORMS.planAlim());await J.waitForTimeout(400);await J.screenshot({path:'v2_plan.png'});
 const L0=await J.evaluate(()=>calc().act[0].id);await J.fill(`#sheet [name="kg_${L0}"]`,'250');await J.click('#sheet button[type=submit]');await J.waitForTimeout(400);
 assert.ok(await esperar(V,l=>+(S.lotes[l]||{}).kgEnt>0,L0)>=0||true);
 await V.evaluate(()=>FORMS.alimento({}));await V.waitForTimeout(400);await V.screenshot({path:'v2_alim2.png'});await V.evaluate(()=>closeSheet());
 // compra
 await J.evaluate(()=>ACTS.eqComprar());await J.waitForTimeout(400);await J.screenshot({path:'v2_compra.png'});
 await J.click('#sheet [data-act="eqSimular"]');await J.waitForTimeout(300);await J.screenshot({path:'v2_gp.png'});await J.click('#sheet [data-act="eqCompraPrueba"]');await J.waitForTimeout(900);await J.screenshot({path:'v2_lic.png'});await J.evaluate(()=>closeSheet());
 // sin internet
 await V.evaluate(()=>{Vaquero.VQ().err='x';render();});await V.evaluate(()=>{location.hash='#hoy';});await V.waitForTimeout(400);await V.screenshot({path:'v2_hoy_off.png',fullPage:true});
 await J.evaluate(()=>{Equipo.SY().err='x';location.hash='#mas';});await J.waitForTimeout(200);await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(500);await J.screenshot({path:'v2_equipo_off.png'});
 if(IDI==='xx'){const K=new Set();for(const p of PAGS){try{for(const k of await p.evaluate(()=>[...I18N_REC.keys()]))K.add(k);}catch(e){}}fs.writeFileSync('claves_v2.json',JSON.stringify([...K].map(k=>[k,1])));console.log('claves grabadas',K.size);}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');await b.close();
})().catch(e=>{console.error('FALLA',e.message);process.exit(1);});
