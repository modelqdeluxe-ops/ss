// recorre la app en modo de registro (idioma "xx") y guarda todas las claves de texto que aparecen
const {chromium}=require('playwright');const fs=require('fs');
(async()=>{
 const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});
 const p=await b.newPage({viewport:{width:400,height:860}});
 const errs=[];p.on('pageerror',e=>errs.push(e.message));
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.setItem('rumentis-idioma','xx');localStorage.setItem('rumentis-voz-natural','no');});
 await p.reload();await p.waitForTimeout(1500);
 await p.evaluate(u=>{cargarDemo();put('ajustes','finca',{...S.config,unidad:u});document.querySelector('#splash')&&document.querySelector('#splash').remove();},process.argv[2]||'lb');
 await p.waitForTimeout(800);
 const W=ms=>p.waitForTimeout(ms);
 const go=async h=>{await p.evaluate(h=>{closeSheet&&closeSheet();location.hash=h;},h);await W(350);};
 const C=await p.evaluate(()=>({act:calc().act.map(x=>x.id),cer:calc().cer.map(x=>x.id),an:calc().act[0].animAct.slice(0,3).map(a=>a.id)}));
 const rutas=['#hoy','#lotes','#registrar','#graficos','#mas','#mas/config','#mas/raciones','#mas/gastos','#mas/datos','#mas/apariencia','#mas/ayuda','#mas/avanzado','#metodologia','#agenda','#bodega','#finanzas','#inventario','#analisis','#formular'];
 for(const r of rutas)await go(r);
 await p.evaluate(()=>{UI.lotesTab='cerrados';render();});await W(200);
 for(const id of C.act.concat(C.cer)){await go('#lote/'+id);
   const tabs=await p.$$eval('[data-act="ui"]',els=>els.map(e=>[e.dataset.k,e.dataset.v]));
   for(const [k,v] of tabs){if(!/tab|Tab/.test(k))continue;await p.evaluate(([k,v])=>{UI[k]=v;render();},[k,v]);await W(200);}}
 for(const a of C.an)await go('#animal/'+C.act[0]+'/'+a);
 await go('#graficos');for(const id of ['todos',...C.act,...C.cer])for(const per of ['30','90','todo']){await p.evaluate(([id,per])=>{UI.gLote=id;UI.gPer=per;render();},[id,per]);await W(120);}
 await go('#finanzas');for(const k of ['mes','anio','12m','todo']){await p.evaluate(k=>{UI.finPer=k;render();},k);await W(150);}
 // formularios
 const forms=await p.evaluate(()=>Object.keys(FORMS));
 for(const k of forms){try{await p.evaluate(([k,l,a])=>{const B=Bodega.inventario()[0],I=(S.config.fin||{}).insumos||[],E=(S.config.fin||{}).equipos||[],CR=(S.config.fin||{}).creditos||[];
   const args={lote:l,id:k==='animal'||k==='pesoAnimal'||k==='baja'?a:k==='bodegaItem'||k==='bodegaCompra'||k==='bodegaConteo'?(B&&B.it.id):k==='insItem'||k==='insMov'?(I[0]&&I[0].id):k==='finEquipo'?(E[0]&&E[0].id):k==='finCredito'||k==='finAbono'?(CR[0]&&CR[0].id):k==='racion'?Object.keys(S.raciones)[0]:undefined,t:'compra',n:1};
   FORMS[k](args);},[k,C.act[0],C.an[0]]);await W(250);
   if(k==='insMov'){for(const t of ['uso','conteo']){await p.evaluate(t=>{FORMS.insMov({id:S.config.fin.insumos[0].id,t});},t);await W(200);}}
   if(k==='pesaje'){await p.evaluate(l=>FORMS.pesaje({lote:l,modo:'grupo'}),C.act[0]);await W(200);await p.evaluate(()=>FORMS.pesaje({}));await W(200);}
   if(k==='lote'||k==='racion'||k==='finEquipo'||k==='finCredito'||k==='insItem'){await p.evaluate(k=>FORMS[k]({}),k);await W(200);}
 }catch(e){console.log('form',k,e.message.slice(0,100));}}
 await p.evaluate(()=>closeSheet());
 // Rumi: todas las áreas, sus opciones y un nivel de submenús
 await p.evaluate(()=>abrirRumi());await W(600);
 const areas=await p.evaluate(()=>RumiMenu.AREAS.map(a=>a.id));
 for(const id of areas){
   await p.evaluate(id=>{RumiMenu.M.pila=[];RumiMenu.abrirArea(RumiMenu.AREAS.find(a=>a.id===id));},id);await W(350);
   const n=await p.evaluate(()=>{const N=RumiMenu.M.pila[RumiMenu.M.pila.length-1];return N?N.ops.length:0;});
   for(let i=0;i<n;i++){
     const tipo=await p.evaluate(([id,i])=>{RumiMenu.M.pila=[];RumiMenu.M.calc=null;RumiMenu.abrirArea(RumiMenu.AREAS.find(a=>a.id===id));const op=RumiMenu.M.pila[RumiMenu.M.pila.length-1].ops[i];if(!op)return '';RumiMenu.elegir(op);return op.sub?'sub':op.calc?'calc':'x';},[id,i]);
     await W(tipo==='x'?500:350);
     if(tipo==='sub'&&id!=='guia'){const m=await p.evaluate(()=>{const N=RumiMenu.M.pila[RumiMenu.M.pila.length-1];return N?Math.min(N.ops.length,12):0;});
       for(let j=0;j<m;j++){await p.evaluate(([id,i,j])=>{RumiMenu.M.pila=[];RumiMenu.M.calc=null;RumiMenu.abrirArea(RumiMenu.AREAS.find(a=>a.id===id));RumiMenu.elegir(RumiMenu.M.pila[RumiMenu.M.pila.length-1].ops[i]);const N=RumiMenu.M.pila[RumiMenu.M.pila.length-1];const op=N&&N.ops[j];if(op&&!op.go&&!op.form)RumiMenu.elegir(op);},[id,i,j]);await W(420);
         const hs=await p.evaluate(()=>{const N=RumiMenu.M.pila[RumiMenu.M.pila.length-1];return N?Math.min(N.ops.length,8):0;});
         if(id==='lotes'||id==='pro'){for(let k=0;k<hs;k++){await p.evaluate(([id,i,j,k])=>{RumiMenu.M.pila=[];RumiMenu.abrirArea(RumiMenu.AREAS.find(a=>a.id===id));RumiMenu.elegir(RumiMenu.M.pila[RumiMenu.M.pila.length-1].ops[i]);const N=RumiMenu.M.pila[RumiMenu.M.pila.length-1];RumiMenu.elegir(N.ops[j]);const N2=RumiMenu.M.pila[RumiMenu.M.pila.length-1];const op=N2&&N2.ops[k];if(op&&!op.go&&!op.form&&!op.sub)RumiMenu.elegir(op);},[id,i,j,k]);await W(380);}}
       }}
     await p.evaluate(()=>{if(!document.querySelector('#rumiMsgs'))abrirRumi();RUMI.log=RUMI.log.slice(-4);});
     if(!(await p.$('#rumiMsgs')))await p.evaluate(()=>abrirRumi());
   }
 }
 // partes nuevas: análisis de Rumi, tutoriales, tablas de referencia, avisos y configuración
 await p.evaluate(()=>{try{closeSheet();}catch(e){}const c=document.querySelector('#rumiClose,[data-act="rumiClose"]');if(c)c.click();});
 await go('#analisis');const partes=await p.$$eval('.rp-idx a',as=>as.map(a=>a.getAttribute('href')));for(const h of partes)await go(h);
 await go('#mas/config');await p.evaluate(()=>{document.querySelectorAll('details').forEach(d=>d.open=true);});await W(300);
 const pinta=async fn=>{await p.evaluate(fn);await W(250);};
 await pinta(()=>{const d=document.createElement('div');d.id='recX';document.body.appendChild(d);});
 const pon=h=>`document.getElementById('recX').insertAdjacentHTML('beforeend','<div>'+(${h})+'</div>')`;
 // tutoriales: cada paso como se pinta en el globo
 await p.evaluate(()=>{const X=document.getElementById('recX');for(const [k,d] of Object.entries(Ayuda.T)){X.insertAdjacentHTML('beforeend',`<div><b>${d.t}</b></div>`);for(const s of d.s)X.insertAdjacentHTML('beforeend',`<div><b>${s.t}</b><p>${s.x}</p></div>`);}
   X.insertAdjacentHTML('beforeend','<div><b>¡Listo!</b><p>Si quieres repetir este tutorial, pídemelo en Rumi, Usar la app, <b>Muéstrame la app</b>.</p></div>');
   for(const [k,f] of Object.entries(Ayuda.F)){X.insertAdjacentHTML('beforeend',`<div>${f.i||''}</div>`);for(const t of (f.p||[]))X.insertAdjacentHTML('beforeend',`<div>${t}</div>`);for(const t of Object.values(f.c||{}))X.insertAdjacentHTML('beforeend',`<div>${t}</div>`);}});
 await W(400);
 // tablas de referencia: todas las razas × climas, todos los países
 await p.evaluate(()=>{const X=document.getElementById('recX'),R=RumiSaber2;const add=o=>{try{const r=typeof o==='function'?o():o;X.insertAdjacentHTML('beforeend','<div>'+(r&&r.html||r||'')+'</div>');}catch(e){X.insertAdjacentHTML('beforeend','<div>'+e.message+'</div>');}};
   for(const r of R.RAZAS_G)for(const c of R.CLIMAS)add(()=>R.tablaReq(r.k,c.k));add(R.razasHtml);add(R.preciosHtml);
   for(const pa of Object.keys(R.REG_PAIS))add(()=>R.calendarioRegion(pa));
   for(const [k,f] of Object.entries(RumiMas.FN))add(f);
   for(const a of Avisos.cola())X.insertAdjacentHTML('beforeend',`<div>${a.t}</div><div>${a.s}</div>`);});
 await W(800);
 for(const pa of await p.evaluate(()=>Object.keys(RumiSaber2.PRECIOS))){await p.evaluate(pa=>{S.config.pais=pa;const X=document.getElementById('recX');const r=RumiSaber2.preciosHtml();X.insertAdjacentHTML('beforeend','<div>'+(r.html||r)+'</div>');},pa);await W(80);}
 await p.evaluate(()=>{try{Avisos.datosWidget();}catch(e){}});
 const keys=await p.evaluate(()=>[...I18N_REC.entries()]);
 fs.writeFileSync('claves_'+(process.argv[2]||'lb')+'.json',JSON.stringify(keys,null,0));
 console.log('claves',keys.length,'errores',errs.slice(0,5));
 await b.close();
})();
