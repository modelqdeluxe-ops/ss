// Rumentis Beta: el peso con cámara aprende solo (ganado). Sin cámara: se arman mediciones en el registro sobre la
// finca de muestra y se comprueba que la app tome como pesos reales el peso de entrada de cada animal, los pesajes
// anotados a mano (del animal y del lote) y las ventas; que con eso ajuste el modelo (k), el factor propio de cada
// animal y su peso con sus mediciones anteriores; y el modelo con cinta. Usa 8111 (app/assets).
const {chromium}=require('playwright');const fs=require('fs');const assert=require('assert');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8');
const cfg=CFG0.replace(/window\.RUMENTIS=\{app:'[a-z]+',prueba:(true|false),beta:(true|false),/,"window.RUMENTIS={app:'jefe',prueba:false,beta:true,");
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});const errs=[];
 const p=await (await b.newContext({viewport:{width:393,height:852}})).newPage();
 p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>{if(m.type()==='error'&&!/favicon|ERR_|net::|Failed to load/.test(m.text()))errs.push(m.text());});
 await p.addInitScript(i=>{window.__I=i;},process.env.IDIOMA||'es');await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:cfg}));
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(()=>{localStorage.clear();localStorage.setItem('rumentis-bienvenida','1');localStorage.setItem('rumentis-idioma',window.__I||'es');});await p.reload();await p.waitForTimeout(800);
 await p.evaluate(()=>cargarDemo());await p.waitForTimeout(400);
 const R=await p.evaluate(()=>{
   const P=PesoCam,C=calc(),x=C.act.find(l=>(l.l.animales||[]).filter(a=>+a.p0>0).length>=8),an=x.l.animales.filter(a=>+a.p0>0).slice(0,8);
   const fi=x.l.fechaIngreso,mas=(f,d)=>new Date(Date.parse(f)+d*864e5).toISOString().slice(0,10);
   // el modelo se equivoca +15 % parejo y cada animal tiene su propio error (−6 % a +6 %)
   const prop=an.map((a,i)=>1.15*(1+(i%5-2)*.03));let n=0;
   const rec=(a,i,f,kgReal)=>({id:'t'+(n++),ts:Date.parse(f)+n,f,modo:'ganado',v:7,pred:Math.round(kgReal*prop[i]*100)/100,kg:Math.round(kgReal*prop[i]*100)/100,lote:x.id,aid:a.id,dims:{WH:130},real:null});
   const g=x.gdpUse>0&&x.gdpUse<3?x.gdpUse:1.1;
   // al ingreso (peso de entrada), a los 30 días (sin peso) y a los 60 (pesaje a mano de cada animal)
   const regs=[...an.map((a,i)=>rec(a,i,fi,+a.p0)),...an.map((a,i)=>rec(a,i,mas(fi,30),+a.p0+30*g)),...an.slice(0,4).map((a,i)=>rec(a,i,mas(fi,60),+a.p0+60*g))];
   P.guardarPC({reg:regs});
   const E=P.etiquetas('ganado','foto'),src={};for(const z of E)src[z.src]=(src[z.src]||0)+1;
   const m=P.modeloV('ganado'),fa=an.map(a=>P.factorAnimal(a.id).f),h=P.historialAnimal(an[0].id,x.id);
   // el peso del animal hoy con su factor y su historial, contra el real (entrada + ganancia)
   const real0=+an[0].p0+g*Math.max(0,(Date.parse(hoy())-Date.parse(fi))/864e5);
   // un pesaje a mano de cada uno a los 60 días (llega como pesaje del animal)
   addItem({tipo:'pesaje',f:mas(fi,60),lote:x.id,prom:0,cab:4,pesos:Object.fromEntries(an.slice(0,4).map(a=>[a.id,Math.round(+a.p0+60*g)])),parcial:true});
   const E2=P.etiquetas('ganado','foto'),src2={};for(const z of E2)src2[z.src]=(src2[z.src]||0)+1;
   // venta de los 8 a los 90 días: el comprador pesa el grupo; mediciones de cámara de esa semana
   P.guardarPC({reg:P.REG().concat(an.map((a,i)=>({...rec(a,i,mas(fi,88),+a.p0+88*g),id:'v'+i,aid:null})))});
   addItem({tipo:'venta',f:mas(fi,90),lote:x.id,cab:8,kg:Math.round(an.reduce((s,a)=>s+(+a.p0+90*g),0)),precioKg:1,comprador:'Prueba'});
   const E3=P.etiquetas('ganado','foto'),src3={};for(const z of E3)src3[z.src]=(src3[z.src]||0)+1;
   const cinta=P.pesoCinta(130,180,{g:'euro',etapa:'adulto'});
   const errG=(mm,ff)=>{const L=P.etiquetas('ganado','foto').filter(z=>!z.grupo);return L.reduce((s,z)=>s+Math.abs(mm.k*Math.pow(z.L,mm.b)*(ff?P.factorAnimal(z.aid).f:1)/z.kg-1),0)/L.length;};
   return {src,k:m.k,b:m.b,n:m.n,err:m.err,fa,h,real0,src2,src3,k3:P.modeloV('ganado').k,b3:P.modeloV('ganado').b,cinta,e0:errG({k:1,b:1}),e1:errG(m),e2:errG(m,true)};
 });
 console.log(JSON.stringify(R));
 assert.equal(R.src.entrada,8,'aprende del peso de entrada de cada animal');
 console.log(`error: sin aprender ${(R.e0*100).toFixed(1)} %, con el modelo ajustado ${(R.e1*100).toFixed(1)} %, con el factor de cada animal ${(R.e2*100).toFixed(1)} %`);
 assert.ok(R.e0>.12&&R.e1<.05&&R.e2<.02,'aprende: el error baja de 15 % a menos de 2 % por animal');assert.ok(R.b>=.85&&R.b<=1.15,'b por el crecimiento de cada animal: '+R.b);
 
 assert.ok(R.h&&R.h.n===3&&Math.abs(R.h.kg/R.real0-1)<.03,'el peso del animal con su historial queda a menos de 3 % del real: '+JSON.stringify(R.h)+' real '+R.real0);
 assert.equal(R.src2.pesaje,4,'aprende de los pesajes a mano del animal');
 assert.equal(R.src3.venta,1,'aprende de la venta (en grupo)');
 assert.ok(R.cinta>420&&R.cinta<560,'modelo con cinta: '+R.cinta);
 // la tarjeta lo dice
 await p.evaluate(()=>{location.hash='#pesocam';});await p.waitForTimeout(400);await p.click('[data-act="pcModo"][data-m="ganado"]');await p.waitForTimeout(400);
 const t=await p.$eval('.pc-cal',e=>e.textContent.replace(/\s+/g,' '));console.log(t);assert.ok(/peso de entrada|entry weight|peso de entrada/.test(t)&&/ventas|sales|vendas/.test(t),'la tarjeta dice de dónde aprendió');
 await p.screenshot({path:'aprende.png',fullPage:true});
 if(process.env.IDIOMA==='xx'){const K=await p.evaluate(()=>[...I18N_REC.keys()]);fs.writeFileSync('claves_aprende.json',JSON.stringify(K.map(k=>[k,1])));}
 console.log(errs.length?'ERRORES:\n'+errs.join('\n'):'errores ninguno');await b.close();if(errs.length)process.exit(1);
})().catch(e=>{console.error('FALLA',e.message);process.exit(1);});
