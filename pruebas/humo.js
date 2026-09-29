const {chromium}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});
 for(const L of ['es','en','pt']){const p=await b.newPage({viewport:{width:400,height:860}});const errs=[];
  p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>{if(m.type()==='error'&&!/favicon|ERR_CERT|net::/.test(m.text()))errs.push(m.text());});
  await p.route('https://api.github.com/**',r=>r.fulfill({status:200,contentType:'application/json',headers:{'access-control-allow-origin':'*'},body:fs.readFileSync('rel_mercado.json','utf8')}));
  await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(l=>{localStorage.clear();localStorage.setItem('rumentis-idioma',l);localStorage.setItem('rumentis-bienvenida','1');},L);await p.reload();await p.waitForTimeout(1200);
  await p.evaluate(()=>{cargarDemo();const s=document.querySelector('#splash');if(s)s.remove();});await p.waitForTimeout(500);
  const rutas=['#hoy','#lotes','#registrar','#graficos','#finanzas','#inventario','#mas','#mas/config','#mas/avanzado','#agenda','#bodega','#analisis','#documentos','#mercado'];
  for(const r of rutas){await p.evaluate(h=>{closeSheet();location.hash=h;},r);await p.waitForTimeout(400);}
  const C=await p.evaluate(()=>({act:calc().act.map(x=>x.id),cer:calc().cer.map(x=>x.id)}));
  for(const id of C.act.concat(C.cer))for(const t of ['resumen','animales','numeros','historial']){await p.evaluate(([id,t])=>{if(location.hash!=='#lote/'+id)location.hash='#lote/'+id;UI.loteTab=t;render();},[id,t]);await p.waitForTimeout(120);}
  const forms=await p.evaluate(()=>Object.keys(FORMS));let nf=0;
  for(const k of forms){await p.evaluate(([k,l])=>{try{FORMS[k]({lote:l,id:l});}catch(e){console.error('FORM '+k+': '+e.message);}},[k,C.act[0]]);await p.waitForTimeout(60);nf++;}
  // el menú de Rumi: área de análisis
  await p.evaluate(async()=>{closeSheet();const A=RumiMenu.AREAS.find(a=>a.id==='pro');for(const o of A.ops()){if(o&&o.fn){try{const r=o.fn();if(r&&r.then)await r;}catch(e){console.error('RUMI '+o.l+': '+e.message);}}}});
  // un clic real en Compartir del certificado (en el navegador descarga)
  await p.evaluate(id=>FORMS.docCert({id}),C.cer[0]);await p.waitForTimeout(300);
  const [dl]=await Promise.all([p.waitForEvent('download',{timeout:15000}).catch(()=>null),p.click('[data-act="docHacer"][data-modo="guardar"]')]);
  let firma='-';if(dl){const pth=await dl.path();const buf=require('fs').readFileSync(pth);firma=buf.slice(-400).toString().includes('%RUMENTIS-FIRMA')?'firmado':'SIN FIRMA';const v=await p.evaluate(b=>{const u=Uint8Array.from(atob(b),c=>c.charCodeAt(0));return Documentos.comprobarPDF(u.buffer).est;},buf.toString('base64'));firma+=' '+v;}
  console.log(L,'formularios',nf,'descarga',dl?dl.suggestedFilename()+' '+firma:'ninguna','errores',errs.length?errs:'ninguno');await p.close();}
 await b.close();})();
