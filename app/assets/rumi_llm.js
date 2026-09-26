/* Rumi avanzado: modelo de lenguaje (Qwen2.5-0.5B ajustado para Rumi, 494M de parámetros)
   que corre dentro del teléfono con wllama (llama.cpp en WebAssembly). Sin internet.

   - Solo se activa en teléfonos con suficiente RAM y si el modelo viene en la app.
   - Las cuentas, dosis y formularios los siguen haciendo las reglas de Rumi; el modelo
     entra cuando las reglas no entienden la pregunta o está fuera de su alcance.
   - Cada respuesta del modelo se puede reportar (política de IA generativa de Google Play). */
(function(){
const BASE='https://rumentis.local/';
const RAM_MIN_MB=4600;           // teléfonos de 5 GB o más (Android reporta un poco menos de lo anunciado)
const REPORTE_CORREO='';         // correo que recibe los reportes de respuestas
const L={estado:'apagado',error:null,w:null,ix:null,ocupado:false,cola:Promise.resolve()};
window.RumiLLM=L;

function ramMB(){try{return window.Android&&Android.ramMB?Number(Android.ramMB()):0;}catch(e){return 0;}}
function cargarScript(src){return new Promise((ok,mal)=>{const s=document.createElement('script');s.src=src;s.onload=ok;s.onerror=mal;document.head.appendChild(s);});}
async function blobDe(ruta,tipo){const r=await fetch(BASE+ruta);if(!r.ok)throw new Error('No encontré '+ruta);const b=await r.blob();return tipo?new Blob([b],{type:tipo}):b;}

L.iniciar=async function(){
  if(L.estado!=='apagado')return;
  if(!window.RUMI_LLM_FORZAR&&ramMB()<RAM_MIN_MB){L.estado='sin_ram';return;}
  L.estado='cargando';
  try{
    const man=await (await fetch(BASE+'rumi/modelo.json')).json();
    await cargarScript(BASE+'rumi_rag.js');
    L.ix=RumiRAG.indexar(await (await fetch(BASE+'rumi_saber.json')).json());
    const {Wllama}=await import(BASE+'wllama/index.js');
    const url=async(r,t)=>URL.createObjectURL(await blobDe(r,t));
    const wasm=await url('wllama/wllama.wasm','application/wasm');
    const w=new Wllama({default:wasm,'wllama.wasm':wasm},{suppressNativeLog:true,allowOffline:true,logger:{debug(){},log(){},info(){},warn(){},error:console.error}});
    // Para WebView sin JSPI o memoria de 64 bits: versión compatible, también local.
    w.setCompat({worker:BASE+'wllama/compat/wllama.js',wasm:await url('wllama/compat/wllama.wasm','application/wasm')});
    const partes=[];for(const f of man.archivos)partes.push(await blobDe('rumi/'+f));
    const cfg={n_ctx:1024,n_batch:256,n_threads:1};
    try{await w.loadModel(partes,cfg);}
    catch(e){await w.exit().catch(()=>{});await w.loadModel(partes,{...cfg,n_gpu_layers:0});}
    L.w=w;L.modelo=man;L.estado='listo';
  }catch(e){L.estado='error';L.error=String(e&&e.message||e);console.error('Rumi avanzado:',e);}
};

L.listo=()=>L.estado==='listo';

const texto=h=>String(h||'').replace(/<[^>]+>/g,' ').replace(/\s+/g,' ').trim();
// Cuándo contesta el modelo en vez de las reglas.
L.usar=function(r,txt){
  if(!L.listo()||L.ocupado)return false;
  if(r&&(r.run||r.btns&&r.btns.length))return false;
  const f=typeof firmaRespuesta==='function'?firmaRespuesta(r):'otra';
  const h=texto(r&&r.html);
  return f==='debil'||/^Eso se sale de lo mío/.test(h);
};

const DOSIS_RE=/\b\d+([.,]\d+)?\s?(ml|cc|mg|mililitros?|miligramos?)\b/i;
L.generar=async function(pregunta,alToken){
  const guia=RumiRAG.buscar(L.ix,pregunta,2);
  const msgs=RumiRAG.mensajes(guia,pregunta);
  let out='';
  const st=await L.w.createChatCompletion({messages:msgs,max_tokens:180,temperature:0.3,top_p:0.9,stream:true});
  for await(const c of st){const d=c.choices&&c.choices[0]&&c.choices[0].delta&&c.choices[0].delta.content;if(d){out+=d;alToken&&alToken(out);}}
  out=out.trim();
  if(DOSIS_RE.test(out)&&!/veterinari/i.test(out))out+=' Confirma cualquier dosis con tu veterinario.';
  return {texto:out,guia:guia.map(e=>e.t)};
};

// Responde en el chat de Rumi con el modelo, escribiendo la respuesta mientras se genera.
L.decir=function(txt,r){
  L.ocupado=true;
  const entrada={html:'',nou:true,llm:true,q:txt,sug:(r&&r.sug)||sugs()};
  let ultimo=0,pend=null;
  const pintar=(t,fin)=>{entrada.html=esc(t)+(fin?'':'<span class="llm-cursor"></span>');
    const now=Date.now();if(!fin&&now-ultimo<120){clearTimeout(pend);pend=setTimeout(()=>pintar(entrada.t||t),130);return;}
    ultimo=now;pintarLog();};
  RUMI.log.push(entrada);
  L.cola=L.cola.then(async()=>{
    try{
      const res=await L.generar(txt,t=>{entrada.t=t;pintar(t);});
      entrada.t=res.texto||'No te entendí bien. ¿Me lo dices de otra forma?';
    }catch(e){entrada.t='Algo falló al pensar la respuesta. Prueba decirlo de otra forma.';}
    clearTimeout(pend);entrada.fin=true;pintar(entrada.t,true);pintarSug(entrada.sug);L.ocupado=false;
    const hd=$('.rumi-hd .rm');if(hd){hd.classList.remove('talk');void hd.offsetWidth;hd.classList.add('talk');}
  });
};

// Botón para reportar respuestas del modelo.
const _msgHtml=msgHtml;
msgHtml=function(x){
  let h=_msgHtml(x);
  if(x.llm&&x.fin){const i=RUMI.log.indexOf(x);
    const b=x.rep?'<span class="llm-rep ok">Reportada. Gracias.</span>':`<button type="button" class="llm-rep" data-act="rumiRep" data-i="${i}">Reportar respuesta</button>`;
    h=h.replace(/<\/div><\/div>$/,`<div class="llm-pie"><small>Respuesta generada por IA, puede tener errores.</small>${b}</div></div></div>`);}
  return h;
};
Object.assign(ACTS,{rumiRep:el=>{
  const x=RUMI.log[+el.dataset.i];if(!x)return;x.rep=true;pintarLog();
  const rep={fecha:new Date().toISOString(),pregunta:x.q,respuesta:x.t,modelo:L.modelo&&L.modelo.version};
  try{const k='rumi_reportes',a=JSON.parse(localStorage.getItem(k)||'[]');a.push(rep);localStorage.setItem(k,JSON.stringify(a.slice(-200)));}catch(e){}
  if(REPORTE_CORREO)location.href='mailto:'+REPORTE_CORREO+'?subject='+encodeURIComponent('Reporte de respuesta de Rumi')+'&body='+encodeURIComponent('Pregunta: '+rep.pregunta+'\n\nRespuesta: '+rep.respuesta+'\n\nModelo: '+rep.modelo+'\nFecha: '+rep.fecha);
  else toast('Gracias. Guardamos tu reporte.');
}});

const css=document.createElement('style');
css.textContent='.llm-cursor{display:inline-block;width:8px;height:15px;margin-left:2px;vertical-align:-2px;background:var(--ink2);animation:tp 1s infinite}'+
  '.llm-pie{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:6px 10px;margin-top:8px}.llm-pie small{color:var(--ink2);font-size:12px}'+
  '.llm-rep{border:0;background:none;color:var(--ink2);font:inherit;font-size:12.5px;text-decoration:underline;padding:6px 0;min-height:32px}.llm-rep.ok{text-decoration:none}';
document.head.appendChild(css);

L.iniciar();
})();
