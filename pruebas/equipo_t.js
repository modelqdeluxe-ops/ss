// Prueba de punta a punta del equipo: app del jefe (prueba) + app del vaquero, con el servidor local (8790) y por archivo.
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const cfg=(app,prueba,srv)=>CFG0.replace("window.RUMENTIS={app:'jefe',prueba:false,","window.RUMENTIS={app:'"+app+"',prueba:"+prueba+",").replace(/servidorEquipo:'[^']*'/,"servidorEquipo:'"+srv+"'");
const SRV='http://127.0.0.1:8790';const IDI=process.env.IDIOMA||'es';const PAGS=[];
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});
 const errs=[];
 async function app(nombre,app,prueba,srv){
  const ctx=await b.newContext({viewport:{width:393,height:852},deviceScaleFactor:1.5});const p=await ctx.newPage();
  p.on('pageerror',e=>errs.push(nombre+': '+e.message));p.on('console',m=>{if(m.type()==='error'&&!/favicon|ERR_|net::|Failed to load resource/.test(m.text()))errs.push(nombre+' consola: '+m.text());});
  await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg(app,prueba,srv)}));
  await p.addInitScript(i=>{window.__IDI=i;},IDI);await p.goto('http://127.0.0.1:8111/index.html#hoy');
  await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__IDI);});await p.reload();await p.waitForTimeout(900);
  await p.evaluate(()=>{Fotos.tomar=async()=>await new Promise(r=>{const c=document.createElement('canvas');c.width=80;c.height=60;const g=c.getContext('2d');g.fillStyle='#8d6e63';g.fillRect(0,0,80,60);c.toBlob(r,'image/jpeg',.8);});});
  PAGS.push(p);return p;}
 const paso=t=>console.log('·',t);
 const terminar=async(p,id,nota)=>{await p.click(`[data-act="vqHecha"][data-p="${id}"]`);await p.waitForTimeout(300);await p.click('#sheet [data-act="vqEviFoto"]');await p.waitForTimeout(400);if(nota)await p.fill('#vqEviNota',nota);await p.click('#sheet [data-act="vqEviEnviar"]');await p.waitForTimeout(500);};

 // ---------- jefe ----------
 const J=await app('jefe','jefe',true,SRV);
 await J.evaluate(()=>{cargarDemo();});await J.waitForTimeout(300);
 await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(400);
 assert.ok(await J.$('nav.bottom [data-nav="equipo"]'),'la app de prueba muestra la pestaña Equipo');
 assert.ok(await J.$('.eq-intro'),'sin licencias: presentación');
 await J.click('.eq-comprar');await J.waitForTimeout(300);await J.screenshot({path:'eq_compra.png'});await J.click('[data-act="eqSimular"]');await J.waitForTimeout(300);await J.click('[data-act="eqCompraPrueba"]');await J.waitForTimeout(900);
 const cod=await J.$eval('.eq-cod b',e=>e.textContent);assert.match(cod,/^RV-[0-9A-Z]{4}(-[0-9A-Z]{4}){4}$/);
 assert.ok(await J.$('.eq-qr svg'),'QR de la licencia');paso('licencia '+cod);
 await J.screenshot({path:'eq_licencia.png'});
 const lic=cod.replace(/-/g,'').slice(2);
 assert.equal(await J.evaluate(c=>EquipoNucleo.licValida(c),lic),true);
 assert.equal(await J.evaluate(c=>EquipoNucleo.licValida(c.slice(0,18)+(c[18]==='A'?'B':'A')+c[19]),lic),false,'la letra de control atrapa errores');
 await J.evaluate(()=>closeSheet());await J.evaluate(()=>Equipo.sincronizar());
 assert.equal(await J.evaluate(()=>Equipo.SY().err||''),'','el jefe se registra en el servidor');

 // ---------- vaquero por servidor ----------
 const V=await app('vaquero','vaquero',true,SRV);
 assert.ok(await V.$('#vqForm'),'pantalla de activación');assert.equal(await V.$eval('nav.bottom',e=>getComputedStyle(e).display),'none');
 await V.screenshot({path:'vq_activar.png'});
 await V.fill('#vqForm [name=nombre]','Juan');await V.fill('#vqForm [name=lic]',cod);await V.click('[data-act="vqActivar"]');await V.waitForTimeout(400);
 assert.match(await V.$eval('#vqForm .err',e=>e.textContent),/nombre y apellido/);
 await V.fill('#vqForm [name=nombre]','Juan Pérez');await V.fill('#vqForm [name=lic]',cod.toLowerCase().replace(/-/g,' '));await V.click('[data-act="vqActivar"]');await V.waitForTimeout(1500);
 {const e0=await V.evaluate(()=>Vaquero.VQ().estado);assert.ok(['pendiente','activo'].includes(e0),e0);if(e0==='pendiente')assert.ok(await V.$('.vq-espera'));paso('vaquero '+e0+(e0==='activo'?' (en vivo: el jefe lo aceptó al instante)':''));}
 await V.screenshot({path:'vq_espera.png'});
 await J.evaluate(()=>Equipo.sincronizar());await J.waitForTimeout(300);
 const vs=await J.evaluate(()=>Object.values(S.config.equipo.vaqueros).map(v=>v.nombre+'|'+v.estado));assert.deepEqual(vs,['Juan Pérez|activo']);paso('el jefe lo aceptó');
 await V.evaluate(()=>Vaquero.sincronizar());await V.waitForTimeout(300);
 assert.equal(await V.evaluate(()=>Vaquero.VQ().estado),'activo');
 await J.evaluate(()=>Equipo.sincronizar());await V.evaluate(()=>Vaquero.sincronizar());await V.waitForTimeout(500);
 const nl=await V.evaluate(()=>calc().act.length);const nlJ=await J.evaluate(()=>calc().act.length);assert.equal(nl,nlJ,'el vaquero ve los lotes del jefe');
 assert.equal(await V.evaluate(()=>Object.values(S.raciones).every(r=>!r.costoKg)),true,'sin precios de raciones');
 assert.equal(await V.evaluate(()=>allItems().some(i=>i.tipo==='venta'||i.tipo==='gasto')),false,'sin ventas ni gastos');
 assert.equal(await V.evaluate(()=>Object.values(S.lotes).every(l=>!l.precioCompraKg&&!l.flete)),true,'lotes sin precios');
 await V.evaluate(()=>{location.hash='#hoy';});await V.waitForTimeout(500);
 assert.match(await V.$eval('#app h1',e=>e.textContent),/Hola, Juan/);assert.equal(await V.$eval('#rumiFab',e=>getComputedStyle(e).display),'none','sin Rumi');
 assert.deepEqual(await V.$$eval('nav.bottom [data-nav]',a=>a.map(x=>x.dataset.nav)),['hoy','lotes','registrar','tareas','mas']);
 await V.screenshot({path:'vq_hoy.png'});
 for(const h of ['#finanzas','#graficos','#analisis','#documentos','#mercado']){await V.evaluate(h=>{location.hash=h;},h);await V.waitForTimeout(200);assert.match(await V.$eval('#app h1',e=>e.textContent),/Hola/,h+' no se abre en la app del vaquero');}
 // registrar: pesaje y entrega por la pantalla
 const L0=await V.evaluate(()=>calc().act[0].id);
 await V.evaluate(()=>{location.hash='#registrar';});await V.waitForTimeout(300);
 await V.click('.tile[data-f="alimento"]');await V.waitForTimeout(400);
 assert.equal(await V.$$eval('#sheet [name="costoKg"],#sheet .horas-mini',e=>e.length),0,'la entrega del vaquero no pide precios ni horarios');
 await V.click('#sheet [data-act="llenarProg"]');await V.waitForTimeout(200);await V.click('#sheet button[type=submit]');await V.waitForTimeout(500);
 await V.evaluate(l=>FORMS.sanidad({lote:l}),L0);await V.waitForTimeout(300);
 assert.equal(await V.$eval('#sheet [name="costo"]',e=>e.closest('.q').offsetParent===null),true,'sin costo en sanidad');
 await V.fill('#sheet [name=producto]','Ivermectina 1%');await V.click('#sheet button[type=submit]');await V.waitForTimeout(400);
 const cola=await V.evaluate(()=>Vaquero.VQ().cola.map(o=>o.k+':'+(o.it?o.it.tipo:'')));paso('cola '+cola.join(' '));
 assert.ok(cola.some(c=>c==='item+:alimento')&&cola.some(c=>c==='item+:sanidad'));
 assert.match(await V.evaluate(()=>syncTxt()),/Enviando|por enviar|registros/);
 await V.evaluate(()=>Vaquero.sincronizar());await J.evaluate(()=>Equipo.sincronizar());await J.waitForTimeout(400);
 const deV=await J.evaluate(()=>allItems().filter(i=>i.por&&i.por.n==='Juan Pérez').map(i=>i.tipo+':'+(i.tipo==='alimento'?(i.costoKg>0?'con costo':'sin costo'):i.producto)));paso('le llegó al jefe: '+deV.join(', '));
 assert.ok(deV.some(x=>x==='alimento:con costo'),'el jefe pone el costo con sus precios');assert.ok(deV.includes('sanidad:Ivermectina 1%'));
 const act=await J.evaluate(()=>S.config.equipo.act.slice(0,3).map(a=>a.txt));assert.ok(act.some(t=>/Juan Pérez aplicó Ivermectina/.test(t)),act.join(' / '));
 await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(500);await J.screenshot({path:'eq_pagina.png',fullPage:true});
 // el jefe confirma: la cola del vaquero se vacía y no se duplica nada
 await J.evaluate(()=>Equipo.sincronizar());await V.evaluate(()=>Vaquero.sincronizar());await V.waitForTimeout(300);
 assert.equal(await V.evaluate(()=>Vaquero.VQ().cola.length),0,'cola vacía tras la confirmación');
 // ---------- en vivo: sin tocar "sincronizar", lo del vaquero llega solo al jefe y lo del jefe al vaquero ----------
 const esperar=async(pg,fn,arg,ms=9000)=>{const t0=Date.now();while(Date.now()-t0<ms){if(await pg.evaluate(fn,arg))return Date.now()-t0;await pg.waitForTimeout(150);}return -1;};
 assert.ok(await esperar(J,()=>Equipo.vivo())>=0,'el jefe está en vivo');assert.ok(await esperar(V,()=>Vaquero.vivo())>=0,'el vaquero está en vivo');
 assert.match(await V.evaluate(()=>syncTxt()),/Sincronizado/);
 await V.evaluate(l=>FORMS.sanidad({lote:l}),L0);await V.waitForTimeout(300);await V.fill('#sheet [name=producto]','Oxitetraciclina en vivo');await V.click('#sheet button[type=submit]');
 const tJ=await esperar(J,()=>allItems().some(i=>i.producto==='Oxitetraciclina en vivo'));assert.ok(tJ>=0,'le llega al jefe solo');paso('en vivo: vaquero → jefe en '+tJ+' ms');
 await J.evaluate(()=>FORMS.eqTarea({}));await J.waitForTimeout(300);await J.click('#sheet .opts[data-name=acc] .opt[data-v=""]');await J.fill('#sheet [name=t]','Revisar bebederos en vivo');await J.click('#sheet button[type=submit]');
 const tV=await esperar(V,()=>JSON.stringify(S).includes('Revisar bebederos en vivo'),null,12000);assert.ok(tV>=0,'la tarea le llega al vaquero sola');paso('en vivo: jefe → vaquero en '+tV+' ms');
 await V.evaluate(()=>Vaquero.sincronizar());await J.evaluate(()=>Equipo.sincronizar());
 assert.deepEqual(await J.evaluate(()=>allItems().filter(i=>i.por&&i.por.n==='Juan Pérez'&&i.tipo==='sanidad').map(i=>i.producto).sort()),['Ivermectina 1%','Oxitetraciclina en vivo'],'sin duplicados');
 // tarea asignada y hecha
 const vid=await V.evaluate(()=>Vaquero.VQ().vid);
 await J.evaluate(v=>{FORMS.eqTarea({id:v});},vid);await J.waitForTimeout(300);await J.click('#sheet .opts[data-name=acc] .opt[data-v=""]');await J.fill('#sheet [name=t]','Revisar bebederos del corral 3');await J.click('#sheet button[type=submit]');await J.waitForTimeout(300);
 await J.evaluate(()=>Equipo.sincronizar());await V.evaluate(()=>Vaquero.sincronizar());await V.evaluate(()=>{location.hash='#tareas';});await V.waitForTimeout(400);
 const tv=await V.$$eval('.vq-tc b',e=>e.map(x=>x.textContent));assert.ok(tv.some(x=>x.startsWith('Revisar bebederos del corral 3')),tv.join('|'));
 const idB=await V.evaluate(()=>S.config.tareas.find(t=>/bebederos del corral 3/.test(t.t)).id);await terminar(V,idB);
 await V.evaluate(()=>Vaquero.sincronizar());await J.evaluate(()=>Equipo.sincronizar());
 await esperar(J,()=>(S.config.tareas.find(t=>/corral 3/.test(t.t))||{}).hechoPor,null,8000);assert.equal(await J.evaluate(()=>(S.config.tareas.find(t=>/corral 3/.test(t.t))||{}).hechoPor),'Juan Pérez','la tarea hecha le llega al jefe');paso('tarea hecha');
 // permisos: sin sanidad
 await J.evaluate(v=>{const e=S.config.equipo;put('ajustes','finca',{...S.config,equipo:{...e,vaqueros:{...e.vaqueros,[v]:{...e.vaqueros[v],permisos:{...e.vaqueros[v].permisos,sanidad:false}}}}});return Equipo.publicarEstado(true);},vid);
 await J.evaluate(()=>Equipo.sincronizar());await V.evaluate(()=>Vaquero.sincronizar());await V.evaluate(()=>{location.hash='#registrar';});await V.waitForTimeout(400);
 assert.equal(await V.$$eval('.tile[data-f="sanidad"]',e=>e.length),0,'sin permiso de sanidad no aparece');
 // y si igual manda una sanidad (app vieja), el jefe no la aplica
 await V.evaluate(l=>{addItem({tipo:'sanidad',f:hoy(),lote:l,clase:'Vacuna',producto:'Sin permiso',cab:1,costo:0,retiro:0,nota:''});},L0);
 await V.evaluate(()=>Vaquero.sincronizar());await J.evaluate(()=>Equipo.sincronizar());
 assert.equal(await J.evaluate(()=>allItems().some(i=>i.producto==='Sin permiso')),false,'el jefe revisa los permisos');paso('permisos ok');

 // ---------- por archivo, sin servidor ----------
 const J2cod=await J.evaluate(async()=>{const c=EquipoNucleo.nuevaLicencia();Equipo.crearLicencia(c,{prueba:true});const fi=await Equipo.fichaEq();return {c,url:EquipoNucleo.enlace(fi,c)};});
 const V2=await app('vaquero2','vaquero',true,'');await V2.route(SRV+'/**',r=>r.abort());   // este teléfono no tiene internet
 await V2.fill('#vqForm [name=nombre]','María López');await V2.fill('#vqForm [name=lic]',J2cod.url);await V2.click('[data-act="vqActivar"]');await V2.waitForTimeout(800);
 assert.equal(await V2.evaluate(()=>Vaquero.VQ().estado),'pendiente','se activa pegando el enlace, sin internet');
 const altaTxt=await V2.evaluate(async()=>(async(...a)=>EquipoNucleo.b64(await EquipoNucleo.armarArchivo(...a)))([Vaquero.VQ().alta],'v'));
 const n1=await J.evaluate(async t=>{let n=0;for(const s of (await EquipoNucleo.leerArchivo(EquipoNucleo.deB64(t))).sobres)n+=await Equipo.procesar(s);return n;},altaTxt);assert.equal(n1,1);
 const archJ=await J.evaluate(async()=>{const est=await Equipo.publicarEstado(true);return (async(...a)=>EquipoNucleo.b64(await EquipoNucleo.armarArchivo(...a)))(Equipo.SY().archivo.concat(est?[est]:[]),'jefe');});
 await V2.evaluate(async t=>Vaquero.procesarVarios((await EquipoNucleo.leerArchivo(EquipoNucleo.deB64(t))).sobres),archJ);await V2.waitForTimeout(300);
 assert.equal(await V2.evaluate(()=>Vaquero.VQ().estado),'activo');assert.equal(await V2.evaluate(()=>calc().act.length),nlJ,'por archivo también llegan los lotes');paso('María entró por archivo');
 // María anota un pesaje y lo manda por archivo
 await V2.evaluate(l=>{addItem({tipo:'pesaje',f:hoy(),lote:l,prom:333.3,cab:10});},L0);
 const opsTxt=await V2.evaluate(async()=>(async(...a)=>EquipoNucleo.b64(await EquipoNucleo.armarArchivo(...a)))([await Vaquero.sobreOps()],'v'));
 await J.evaluate(async t=>{for(const s of (await EquipoNucleo.leerArchivo(EquipoNucleo.deB64(t))).sobres)await Equipo.procesar(s);},opsTxt);
 assert.equal(await J.evaluate(()=>allItems().some(i=>i.prom===333.3&&i.por&&i.por.n==='María López')),true,'pesaje de María por archivo');
 // el mismo archivo otra vez no duplica
 await J.evaluate(async t=>{for(const s of (await EquipoNucleo.leerArchivo(EquipoNucleo.deB64(t))).sobres)await Equipo.procesar(s);},opsTxt);
 assert.equal(await J.evaluate(()=>allItems().filter(i=>i.prom===333.3).length),1);
 // ---------- archivo .rumentis: cifrado, único y abierto desde WhatsApp (puente Android simulado) ----------
 const fJ=await J.evaluate(async()=>{const est=await Equipo.publicarEstado(true);return EquipoNucleo.b64(await EquipoNucleo.armarArchivo(Equipo.SY().archivo.concat([est]),'jefe',{ficha:await Equipo.fichaEq()}));});
 const crudo=Buffer.from(fJ,'base64');assert.equal(crudo.subarray(0,8).toString(),'RUMENTIS','empieza con la marca');
 assert.ok(!/sobres|ficha|equipo|María/.test(crudo.toString('latin1')),'por dentro no se lee nada');
 assert.equal(await J.evaluate(async t=>EquipoNucleo.recibirArchivo(EquipoNucleo.deB64(t),Equipo.alRecibir),fJ),false,'el jefe no abre su propio archivo');
 assert.match(await J.evaluate(()=>document.querySelector('#toast').textContent),/para la app Rumentis Equipo/,'el archivo de la administración es para Rumentis Equipo');
 assert.match(await J.evaluate(()=>EquipoNucleo.nombreArchivo('equipo-x')),/\.campo$/,'la administración hace .campo');
 assert.match(await V2.evaluate(()=>EquipoNucleo.nombreArchivo('maria')),/\.rumentis$/,'el vaquero hace .rumentis');
 // si llega a la app equivocada, se lo pasa a la otra (puente Android simulado)
 assert.equal(await J.evaluate(async t=>{let dado=null;window.Recibido={tomar:()=>'',pasar:x=>{dado=x;return true;}};const r=await EquipoNucleo.recibirArchivo(EquipoNucleo.deB64(t),Equipo.alRecibir);delete window.Recibido;return !r&&dado===t;},fJ),true,'se lo pasa a Rumentis Campo');
 assert.match(await J.evaluate(()=>document.querySelector('#toast').textContent),/se abre ahí/);
 {const fV=await V2.evaluate(async()=>EquipoNucleo.b64(await EquipoNucleo.armarArchivo([],Vaquero.VQ().vid)));
  assert.equal(await V2.evaluate(async t=>EquipoNucleo.recibirArchivo(EquipoNucleo.deB64(t),Vaquero.alRecibir),fV),false,'el vaquero no abre lo que es para el jefe');
  assert.match(await V2.evaluate(()=>document.querySelector('#toast').textContent),/Rumentis, la app de la administración/);}
 assert.equal(await V2.evaluate(async t=>EquipoNucleo.recibirArchivo(EquipoNucleo.deB64(t),Vaquero.alRecibir),fJ),true,'la primera vez se abre');
 assert.equal(await V2.evaluate(async t=>EquipoNucleo.recibirArchivo(EquipoNucleo.deB64(t),Vaquero.alRecibir),fJ),false,'la segunda vez no');
 assert.match(await V2.evaluate(()=>document.querySelector('.toast,#toast')?.textContent||''),/ya lo abriste/,'avisa que ya se abrió');
 assert.equal(await V2.evaluate(async()=>EquipoNucleo.recibirArchivo(new TextEncoder().encode('hola, no soy de Rumentis'),Vaquero.alRecibir)),false,'basura: no');
 const alterado=Buffer.from(crudo);alterado[alterado.length-5]^=1;
 assert.equal(await V2.evaluate(async t=>EquipoNucleo.recibirArchivo(EquipoNucleo.deB64(t),Vaquero.alRecibir),alterado.toString('base64')),false,'alterado: no');
 // desde WhatsApp: Android deja el archivo en Recibido.tomar() y llama a archivoRecibido()
 const fJ2=await J.evaluate(async()=>{const est=await Equipo.publicarEstado(true);return EquipoNucleo.b64(await EquipoNucleo.armarArchivo([est],'jefe',{ficha:await Equipo.fichaEq()}));});
 await V2.evaluate(t=>{let x=t;window.Recibido={tomar:()=>{const r=x;x='';return r;}};archivoRecibido();},fJ2);await V2.waitForTimeout(800);
 assert.match(await V2.evaluate(()=>document.querySelector('.toast,#toast')?.textContent||''),/Listo|Nada nuevo/,'se abrió desde el puente');
 assert.equal(await V2.evaluate(async t=>EquipoNucleo.recibirArchivo(EquipoNucleo.deB64(t),Vaquero.alRecibir),fJ2),false,'y ya no se abre otra vez');
 // el archivo viejo (.json) todavía se lee, y tampoco dos veces
 const viejo=Buffer.from(JSON.stringify({rumentis:'equipo',v:1,de:'v000000000001',ts:1,sobres:[]})).toString('base64');
 assert.equal(await J.evaluate(async t=>EquipoNucleo.recibirArchivo(EquipoNucleo.deB64(t),Equipo.alRecibir),viejo),true);
 assert.equal(await J.evaluate(async t=>EquipoNucleo.recibirArchivo(EquipoNucleo.deB64(t),Equipo.alRecibir),viejo),false);
 paso('archivo .rumentis cifrado, único y abierto desde WhatsApp');

 // ---------- una licencia no sirve dos veces ----------
 const V3=await app('vaquero3','vaquero',true,SRV);
 await V3.fill('#vqForm [name=nombre]','Pedro Díaz');await V3.fill('#vqForm [name=lic]',cod);await V3.click('[data-act="vqActivar"]');await V3.waitForTimeout(1200);
 await J.evaluate(()=>Equipo.sincronizar());await V3.evaluate(()=>Vaquero.sincronizar());await V3.waitForTimeout(300);
 assert.equal(await V3.evaluate(()=>Vaquero.VQ().estado),'rechazada');assert.match(await V3.evaluate(()=>Vaquero.VQ().motivo),/otra persona/);paso('licencia usada: rechazada');

 // ---------- baja ----------
 await J.evaluate(v=>Equipo.darDeBaja(v),vid);await J.evaluate(()=>Equipo.sincronizar());await V.evaluate(()=>Vaquero.sincronizar()).catch(()=>{});await V.waitForTimeout(2000);
 assert.equal(await V.evaluate(()=>Vaquero.VQ().estado),'baja');assert.match(await V.$eval('#app h1',e=>e.textContent),/Sesión cerrada/);
 assert.equal(await V.evaluate(()=>allItems().length),0,'se borran los datos de la finca');
 const lr=await J.evaluate(()=>Object.values(S.config.equipo.licencias).filter(l=>l.reemplaza).length);assert.equal(lr,1,'licencia de reemplazo');
 assert.equal(await J.evaluate(()=>S.config.equipo.gen),2,'la clave del equipo cambió');
 // María (que sigue) recibe la clave nueva por archivo y puede leer el estado nuevo
 const arch2=await J.evaluate(async()=>{const est=await Equipo.publicarEstado(true);return (async(...a)=>EquipoNucleo.b64(await EquipoNucleo.armarArchivo(...a)))(Equipo.SY().archivo.concat([est]),'jefe');});
 await V2.evaluate(async t=>Vaquero.procesarVarios((await EquipoNucleo.leerArchivo(EquipoNucleo.deB64(t))).sobres),arch2);
 assert.equal(await V2.evaluate(()=>Vaquero.VQ().gen),2);assert.equal(await V2.evaluate(()=>Vaquero.VQ().estado),'activo');paso('baja y clave nueva');

 // ---------- licencia de prueba para siempre ----------
 const V4=await app('vaquero4','vaquero',true,'');
 await V4.fill('#vqForm [name=nombre]','Ana Prueba');await V4.fill('#vqForm [name=lic]','RV-PRUEBA-2026');await V4.click('[data-act="vqActivar"]');await V4.waitForTimeout(600);
 assert.equal(await V4.evaluate(()=>Vaquero.VQ().demo),true);assert.ok(await V4.evaluate(()=>calc().act.length>0),'finca de muestra');
 // la app de Google Play no acepta ni la licencia de prueba ni licencias de la app de prueba
 const V5=await app('vaquero5','vaquero',false,'');
 await V5.fill('#vqForm [name=nombre]','Ana Prueba');await V5.fill('#vqForm [name=lic]','RV-PRUEBA-2026');await V5.click('[data-act="vqActivar"]');await V5.waitForTimeout(500);
 assert.notEqual(await V5.evaluate(()=>Vaquero.VQ().estado),'activo');
 await V5.fill('#vqForm [name=lic]',J2cod.url);await V5.click('[data-act="vqActivar"]');await V5.waitForTimeout(600);
 assert.match(await V5.$eval('#vqForm .err',e=>e.textContent),/app de prueba/);paso('la app real rechaza licencias de prueba');
 // ---------- app real del dueño: pestaña Equipo siempre, código de dueño y licencias reales sin cobrar ----------
 const J2=await app('jefe2','jefe',false,'');await J2.evaluate(()=>cargarDemo());await J2.evaluate(()=>{location.hash='#equipo';});await J2.waitForTimeout(400);
 assert.equal(await J2.$$eval('nav.bottom [data-nav="equipo"]',e=>e.length),1,'la pestaña Equipo siempre se ve');
 assert.match(await J2.$eval('.eq-comprar',e=>e.textContent),/1\.99/,'precio de respaldo');
 await J2.click('.eq-comprar');await J2.waitForTimeout(300);assert.match(await J2.$eval('#toast',e=>e.textContent),/Google Play/);
 await J2.click('[data-f="eqDueno"]');await J2.waitForTimeout(300);await J2.fill('#sheet [name=c]','RD-AAAA-BBBB-CCCC-DDDD-EEEE');await J2.click('#sheet button[type=submit]');await J2.waitForTimeout(300);
 assert.match(await J2.$eval('#sheet .err',e=>e.textContent),/no es válido/);
 await J2.fill('#sheet [name=c]',process.env.DUENO.toLowerCase().replace(/-/g,' '));await J2.click('#sheet button[type=submit]');await J2.waitForTimeout(600);
 assert.equal(await J2.evaluate(()=>S.config.equipo.dueno),true,'código de dueño');
 await J2.screenshot({path:'eq_dueno.png'});
 await J2.click('.eq-comprar');await J2.waitForTimeout(300);await J2.click('[data-act="eqCompraPrueba"]');await J2.waitForTimeout(900);
 const codR=await J2.$eval('.eq-cod b',e=>e.textContent);await J2.evaluate(()=>closeSheet());
 assert.equal(await J2.evaluate(()=>Object.values(S.config.equipo.licencias)[0].compra.dueno),true);
 // el vaquero de la app real escribe solo la licencia, sin servidor: el alta va por archivo
 const V7=await app('vaquero7','vaquero',false,'');
 await V7.fill('#vqForm [name=nombre]','Rosa Mejía');await V7.fill('#vqForm [name=lic]',codR);await V7.click('[data-act="vqActivar"]');await V7.waitForTimeout(800);
 assert.equal(await V7.evaluate(()=>Vaquero.VQ().estado),'pendiente','se activa escribiendo solo la licencia');
 assert.ok(await V7.$('[data-act="vqEnviarArchivo"]'));await V7.screenshot({path:'vq_offline.png'});
 const alta7=await V7.evaluate(()=>(async(...a)=>EquipoNucleo.b64(await EquipoNucleo.armarArchivo(...a)))([Vaquero.VQ().alta],'v'));
 const n7=await J2.evaluate(async t=>{let n=0;for(const s of (await EquipoNucleo.leerArchivo(EquipoNucleo.deB64(t))).sobres)n+=await Equipo.procesar(s);return n;},alta7);assert.equal(n7,1);
 assert.deepEqual(await J2.evaluate(()=>Object.values(S.config.equipo.vaqueros).map(v=>v.nombre)),['Rosa Mejía']);
 // un archivo de otro jefe no se adopta
 const otro=await J.evaluate(async()=>(async(...a)=>EquipoNucleo.b64(await EquipoNucleo.armarArchivo(...a)))(Equipo.SY().archivo,'jefe',{ficha:await Equipo.fichaEq()}));
 assert.equal(await V7.evaluate(async t=>Vaquero.adoptarFicha((await EquipoNucleo.leerArchivo(EquipoNucleo.deB64(t)))),otro),false,'archivo de otro jefe');
 const arch7=await J2.evaluate(async()=>{const est=await Equipo.publicarEstado(true);return (async(...a)=>EquipoNucleo.b64(await EquipoNucleo.armarArchivo(...a)))(Equipo.SY().archivo.concat([est]),'jefe',{ficha:await Equipo.fichaEq()});});
 const ok7=await V7.evaluate(async t=>{const o=(await EquipoNucleo.leerArchivo(EquipoNucleo.deB64(t)));if(!(await Vaquero.adoptarFicha(o)))return 'no adopta';await Vaquero.procesarVarios(o.sobres);return Vaquero.VQ().estado;},arch7);
 assert.equal(ok7,'activo','la licencia del dueño sirve en la app real del vaquero');
 assert.equal(await V7.evaluate(()=>calc().act.length),await J2.evaluate(()=>calc().act.length));paso('código de dueño y alta escribiendo solo la licencia');
 // ---------- recorrido por las demás pantallas ----------
 await V2.evaluate(()=>{location.hash='#lotes';});await V2.waitForTimeout(250);await V2.click('.vq-lote');await V2.waitForTimeout(300);
 for(const h of ['#mas','#mas/idioma','#mas/acerca','#mas/apariencia','#tareas','#registrar','#hoy']){await V2.evaluate(h=>{location.hash=h;},h);await V2.waitForTimeout(250);}
 await V2.evaluate(()=>{FORMS.alimento({});});await V2.waitForTimeout(250);await V2.evaluate(()=>closeSheet());
 await V2.evaluate(()=>{const c=Vaquero.VQ();});
 await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(300);
 const v2=await V2.evaluate(()=>Vaquero.VQ().vid);await J.evaluate(v=>{ACTS.eqVaquero({dataset:{v}});},v2);await J.waitForTimeout(400);
 assert.match(await J.$eval('#app',e=>e.textContent),/Qué puede registrar/);await J.screenshot({path:'eq_colab.png',fullPage:true});
 for(const a of ['eqAjustes','eqLicencias','eqAnteriores']){await J.evaluate(a=>ACTS[a](),a);await J.waitForTimeout(250);await J.screenshot({path:'eq_'+a+'.png'});await J.evaluate(()=>closeSheet());await J.waitForTimeout(150);}
 await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(300);
 if(await J.$('[data-act="eqTareaVer"]')){await J.click('[data-act="eqTareaVer"]');await J.waitForTimeout(300);await J.screenshot({path:'eq_tarea.png'});await J.evaluate(()=>closeSheet());}
 await J.evaluate(()=>{ACTS.eqServidor();});await J.waitForTimeout(200);await J.evaluate(()=>closeSheet());
 await J.evaluate(()=>{FORMS.eqTarea({});});await J.waitForTimeout(200);await J.evaluate(()=>closeSheet());
 await J.evaluate(()=>{ACTS.eqBaja({dataset:{v:Object.keys(S.config.equipo.vaqueros).find(k=>S.config.equipo.vaqueros[k].estado==='activo')}});});await J.waitForTimeout(200);await J.evaluate(()=>closeSheet());
 await J.evaluate(()=>{ACTS.eqHistMas();});await J.waitForTimeout(200);
 // auto apagado: una alta queda como solicitud
 await J.evaluate(()=>{put('ajustes','finca',{...S.config,equipo:{...S.config.equipo,auto:false}});});
 const c3=await J.evaluate(()=>{const c=EquipoNucleo.nuevaLicencia();Equipo.crearLicencia(c,{prueba:true});return c;});
 await J.evaluate(()=>Equipo.sincronizar());
 const V6=await app('vaquero6','vaquero',true,SRV);await V6.fill('#vqForm [name=nombre]','Luis Solís');await V6.fill('#vqForm [name=lic]',c3);await V6.click('[data-act="vqActivar"]');await V6.waitForTimeout(1200);
 await J.evaluate(()=>Equipo.sincronizar());await J.evaluate(()=>{location.hash='#mas';});await J.waitForTimeout(200);await J.evaluate(()=>{location.hash='#equipo';});await J.waitForTimeout(300);
 assert.ok(await J.$('[data-act="eqSolicitud"]'),'con aceptar solos apagado, la alta queda como solicitud');
 await J.click('[data-act="eqSolicitud"]');await J.waitForTimeout(300);await J.click('#sheet [data-act="eqAceptar"]');await J.waitForTimeout(500);
 await J.evaluate(()=>Equipo.sincronizar());await V6.evaluate(()=>Vaquero.sincronizar());assert.equal(await V6.evaluate(()=>Vaquero.VQ().estado),'activo');paso('solicitud aceptada');
 assert.equal(await V2.evaluate(()=>typeof ACTS.vqBorrar),'undefined','Campo no puede borrar datos');
 if(IDI==='xx'){const K=new Set();for(const p of PAGS){try{for(const k of await p.evaluate(()=>[...I18N_REC.keys()]))K.add(k);}catch(e){}}
   fs.writeFileSync('claves_equipo.json',JSON.stringify([...K].map(k=>[k,1])));console.log('claves grabadas',K.size);}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');
 await b.close();
})().catch(e=>{console.error('FALLA',e.message);process.exit(1);});
