// Prueba Rumi avanzado en Chromium, simulando el WebView de la app:
// la página se abre desde file:// y https://rumentis.local/ apunta a un servidor local
// que sirve app/assets, igual que MainActivity$1.shouldInterceptRequest.
//
// Uso: NODE_PATH=$(npm root -g) node scripts/probar_rumi.js [preguntas.json]
const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),os=require('os'),https=require('https');
const {execSync}=require('child_process');
const ASSETS=path.join(__dirname,'..','app','assets');
const mime=p=>p.endsWith('.js')?'text/javascript':p.endsWith('.wasm')?'application/wasm':p.endsWith('.json')?'application/json':p.endsWith('.html')?'text/html':'application/octet-stream';

// Preguntas que NO están en los datos de entrenamiento.
const PREGUNTAS=process.argv[2]?JSON.parse(fs.readFileSync(process.argv[2],'utf8')):[
  'mis novillos tienen la panza inflada del lado izquierdo',
  'un animal amanecio con la orina oscura y los ojos amarillos',
  'le salieron gusanos a un ternero en el ombligo',
  'se puede dar cascara de cacao al ganado',
  'quien es el presidente de honduras',
  'por que el lote esta comiendo menos que la semana pasada',
  'es bueno el sorgo o mejor compro maiz',
  'como hago para que el ganado aguante el calor de abril',
  'como se si un torete tiene calentura',
];

(async()=>{
  // servidor HTTPS local que hace de rumentis.local (certificado temporal)
  const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'rumi-'));
  execSync(`openssl req -x509 -newkey rsa:2048 -nodes -keyout ${tmp}/k.pem -out ${tmp}/c.pem -days 1 -subj /CN=rumentis.local 2>/dev/null`);
  const srv=https.createServer({key:fs.readFileSync(tmp+'/k.pem'),cert:fs.readFileSync(tmp+'/c.pem')},(req,res)=>{
    const p=decodeURIComponent(new URL(req.url,'https://x').pathname.slice(1)),f=path.join(ASSETS,p);
    if(!f.startsWith(ASSETS)||!fs.existsSync(f)){res.writeHead(404);return res.end();}
    res.writeHead(200,{'Content-Type':mime(p),'Access-Control-Allow-Origin':'*','Content-Length':fs.statSync(f).size});
    fs.createReadStream(f).pipe(res);
  }).listen(0);
  const puerto=srv.address().port;
  const b=await chromium.launch({env:{...process.env,HTTPS_PROXY:'',HTTP_PROXY:'',https_proxy:'',http_proxy:'',ALL_PROXY:''},args:[`--host-resolver-rules=MAP rumentis.local 127.0.0.1:${puerto}`,'--ignore-certificate-errors','--no-proxy-server']});
  const ctx=await b.newContext({ignoreHTTPSErrors:true});
  const page=await ctx.newPage();
  page.on('console',m=>{if(m.type()==='error')console.log('[consola]',m.text().slice(0,300));});
  page.on('pageerror',e=>console.log('[error]',e.message));
  await page.addInitScript(()=>{window.Android={ramMB:()=>6000,barras(){},escuchar(){},guardar(){}};});
  await page.goto('file://'+path.join(ASSETS,'index.html'));
  const t0=Date.now();
  await page.waitForFunction(()=>window.RumiLLM,null,{timeout:60000});
  await page.waitForFunction(()=>window.RumiLLM&&['listo','error','sin_ram'].includes(RumiLLM.estado),null,{timeout:600000,polling:500});
  const est=await page.evaluate(()=>({estado:RumiLLM.estado,error:RumiLLM.error,modelo:RumiLLM.modelo}));
  console.log('Modelo:',JSON.stringify(est),`cargado en ${((Date.now()-t0)/1000).toFixed(1)} s`);
  if(est.estado!=='listo'){await b.close();srv.close();process.exit(1);}

  // 1) directo al modelo
  for(const q of PREGUNTAS){
    const r=await page.evaluate(async q=>{const t=performance.now();let n=0;const res=await RumiLLM.generar(q,()=>n++);return {...res,ms:performance.now()-t,n};},q);
    console.log(`\nP: ${q}\nGuía: ${r.guia.join(' | ')||'(nada)'}\nR: ${r.texto}\n(${(r.ms/1000).toFixed(1)} s, ${(r.n/(r.ms/1000)).toFixed(1)} trozos/s)`);
  }
  // 2) por el chat, como el usuario: las reglas contestan lo que saben y el modelo lo demás
  await page.evaluate(()=>abrirRumi());
  for(const q of ['¿qué hago hoy?','un toro tiene la orina color cafe y esta debil','cuanta amoxicilina le pongo a un torete de 250 kilos']){
    const n=await page.evaluate(()=>RUMI.log.length);
    await page.evaluate(q=>preguntar(q),q);
    await page.waitForFunction(n=>RUMI.log.length>n+1&&!RumiLLM.ocupado&&(!RUMI.log[RUMI.log.length-1].llm||RUMI.log[RUMI.log.length-1].fin),n,{timeout:300000});
    const u=await page.evaluate(()=>{const x=RUMI.log[RUMI.log.length-1];return {llm:!!x.llm,txt:(x.t||x.html).replace(/<[^>]+>/g,' ').replace(/\s+/g,' ').slice(0,300)};});
    console.log(`\nChat P: ${q}\n${u.llm?'Modelo':'Reglas'}: ${u.txt}`);
  }
  await page.screenshot({path:path.join(__dirname,'..','dist','rumi_chat.png')});
  await b.close();srv.close();
})();
