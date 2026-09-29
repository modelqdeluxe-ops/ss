// compara la página armada por partes (morph) con la misma página armada desde cero
const {chromium}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});let malos=0,total=0;
 for(const L of ['es','en','pt']){const p=await b.newPage({viewport:{width:400,height:860}});const errs=[];p.on('pageerror',e=>errs.push(e.message));
  await p.route('https://api.github.com/**',r=>r.fulfill({status:200,contentType:'application/json',headers:{'access-control-allow-origin':'*'},body:fs.readFileSync('rel_mercado.json','utf8')}));
  await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.evaluate(l=>{localStorage.clear();localStorage.setItem('rumentis-idioma',l);localStorage.setItem('rumentis-bienvenida','1');},L);await p.reload();await p.waitForTimeout(1200);
  await p.evaluate(()=>{cargarDemo();const s=document.querySelector('#splash');if(s)s.remove();});await p.waitForTimeout(500);
  const C=await p.evaluate(()=>({act:calc().act.map(x=>x.id),cer:calc().cer.map(x=>x.id)}));
  let rutas=['#hoy','#lotes','#registrar','#graficos','#finanzas','#inventario','#mas','#mas/config','#mas/avanzado','#agenda','#bodega','#analisis','#documentos','#mercado','#formular','#metodologia'];
  for(const id of C.act.concat(C.cer))rutas.push('#lote/'+encodeURIComponent(id));
  // orden mezclado, dos vueltas: así cada página se arma sobre otras distintas
  let s=7;const rnd=()=>{s=(s*16807)%2147483647;return s/2147483647;};const seq=[...rutas,...rutas.slice().sort(()=>rnd()-.5),...rutas.slice().sort(()=>rnd()-.5)];
  const limpiar=()=>p.evaluate(()=>{const c=document.getElementById('app').cloneNode(true);c.querySelectorAll('[data-fijo],.rp').forEach(x=>x.remove());
    const ser=n=>n.nodeType===3?n.data:n.nodeType!==1?'':'<'+n.nodeName+[...n.attributes].map(a=>' '+a.name+'="'+a.value.replace(/^(ag|p|r)\d+$/,'$1#')+'"').sort().join('')+(n.nodeName==='INPUT'||n.nodeName==='TEXTAREA'||n.nodeName==='SELECT'?' v='+n.value+' c='+n.checked:'')+'>'+[...n.childNodes].map(ser).join('')+'</>';
    return [...c.childNodes].map(ser).join('');});
  const cmp=async()=>{await p.waitForTimeout(250);const a=await limpiar();
    await p.evaluate(()=>{const app=document.getElementById('app');[...app.childNodes].forEach(n=>{if(!(n.nodeType===1&&n.hasAttribute('data-fijo')))n.remove();});render();});
    await p.waitForTimeout(250);const b2=await limpiar();if(a===b2)return null;
    let i=0;while(a[i]===b2[i])i++;return {r:await p.evaluate(()=>location.hash),en:i,a:a.slice(Math.max(0,i-100),i+150),b:b2.slice(Math.max(0,i-100),i+150)};};
  for(const r of seq){await p.evaluate(h=>{closeSheet();location.hash=h;},r);await p.waitForTimeout(120);total++;const d=await cmp();if(d){malos++;if(malos<6)console.log(L,JSON.stringify(d));}
    if(r.startsWith('#lote/'))for(const tb of ['animales','numeros','historial','resumen']){await p.evaluate(tb=>{const x=document.querySelector(`.tabs [data-v="${tb}"]`);if(x)x.click();},tb);await p.waitForTimeout(80);total++;const d=await cmp();if(d){malos++;if(malos<6)console.log(L,tb,JSON.stringify(d));}}}
  console.log(L,'errores',errs.length?errs:'ninguno');await p.close();}
 console.log('comparaciones',total,'distintas',malos);await b.close();})();
