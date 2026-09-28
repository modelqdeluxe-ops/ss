/* Voz natural de Rumi: una voz neuronal (Piper, español de México) que corre dentro del teléfono con
   ONNX Runtime Web, sin internet. El texto pasa a fonemas con voz_es.js y el sonido sale por Web Audio.
   Si algo falla (teléfono muy viejo, poca memoria), Rumi vuelve a la voz del teléfono. */
(function(){
'use strict';
const BASE=location.protocol==='file:'?'https://rumentis.local/voz/':new URL('voz/',location.href).href;
const LS='rumentis-voz-natural';
let ses=null,cfg=null,ctx=null,turno=0,fuentes=[],prom=null,estado='nada';
const soporta=()=>typeof WebAssembly==='object'&&!!(window.AudioContext||window.webkitAudioContext)&&!!window.VozEs;
const activa=()=>{try{return localStorage.getItem(LS)!=='no';}catch(e){return true;}};
const cargarJs=src=>new Promise((ok,mal)=>{const s=document.createElement('script');s.src=src;s.onload=ok;s.onerror=mal;document.head.appendChild(s);});

function preparar(){
  if(ses)return Promise.resolve(true);if(prom)return prom;
  prom=(async()=>{
    try{
      estado='cargando';
      if(!window.ort)await cargarJs(BASE+'ort.wasm.min.js');
      ort.env.wasm.wasmPaths=BASE;ort.env.wasm.numThreads=1;ort.env.wasm.proxy=false;ort.env.logLevel='error';
      cfg=await (await fetch(BASE+'rumi_es.json')).json();
      const buf=await (await fetch(BASE+'rumi_es.onnx')).arrayBuffer();
      ses=await ort.InferenceSession.create(buf,{executionProviders:['wasm'],graphOptimizationLevel:'all'});
      estado='lista';return true;
    }catch(e){console.error('voz natural',e);estado='error';prom=null;ses=null;return false;}
  })();
  return prom;
}

/* trozos cortos para que empiece a hablar pronto: frases y, si son largas, partes separadas por comas */
function trozos(texto){
  const F=VozEs.fonemas(texto),out=[];
  for(const f of F){
    for(const p of f.split(/(?<=[;:])\s+/)){
      if(p.length<=200){out.push(p);continue;}
      let cur='';for(const q of p.split(/(?<=,)\s+/)){if(cur&&(cur+' '+q).length>200){out.push(cur);cur=q;}else cur=cur?cur+' '+q:q;}
      if(cur)out.push(cur);
    }
  }
  return out.filter(x=>x.replace(/[\s.,;:?!]/g,''));
}
async function sintetizar(fr,vel=1){
  const M=cfg.phoneme_id_map,ids=[...M['^'],...M['_']];
  for(const ch of fr){const x=M[ch];if(x)ids.push(...x,...M['_']);}
  ids.push(...M['$']);
  const I=cfg.inference||{};
  const out=await ses.run({
    input:new ort.Tensor('int64',BigInt64Array.from(ids,BigInt),[1,ids.length]),
    input_lengths:new ort.Tensor('int64',BigInt64Array.from([BigInt(ids.length)]),[1]),
    scales:new ort.Tensor('float32',Float32Array.from([I.noise_scale??0.667,(I.length_scale??1)/vel,I.noise_w??0.8]),[3])});
  const a=Float32Array.from(out[Object.keys(out)[0]].data);
  // volumen parejo entre frases, sin saturar
  let pk=0;for(let i=0;i<a.length;i++){const v=Math.abs(a[i]);if(v>pk)pk=v;}
  const g=pk>0?Math.min(3,0.9/pk):1;if(g!==1)for(let i=0;i<a.length;i++)a[i]*=g;
  return a;
}
function callar(){turno++;for(const s of fuentes){try{s.onended=null;s.stop();}catch(e){}}fuentes=[];}
async function hablar(texto,{vel=1,alTerminar}={}){
  callar();const mi=turno;
  if(!ses){if(estado!=='cargando'&&typeof toast==='function')toast('Preparando la voz de Rumi…',2200);}
  if(!await preparar())return false;
  if(mi!==turno)return true;
  try{
    ctx=ctx||new (window.AudioContext||window.webkitAudioContext)();
    if(ctx.state==='suspended')await ctx.resume();
  }catch(e){return false;}
  let t=ctx.currentTime+0.06,ultima=null;
  for(const f of trozos(texto)){
    let a;try{a=await sintetizar(f,vel);}catch(e){console.error(e);return fuentes.length>0;}
    if(mi!==turno)return true;
    const b=ctx.createBuffer(1,a.length,cfg.sample_rate);b.getChannelData(0).set(a);
    const s=ctx.createBufferSource();s.buffer=b;s.connect(ctx.destination);
    t=Math.max(t,ctx.currentTime+0.02);s.start(t);t+=b.duration+(/[.?!]$/.test(f)?0.22:0.1);fuentes.push(s);ultima=s;
  }
  if(ultima&&alTerminar)ultima.onended=()=>{if(mi===turno)alTerminar();};
  return true;
}
/* para pruebas: sintetiza sin reproducir y dice cuánto audio salió y cuánto tardó */
async function medir(texto){if(!await preparar())return null;const t0=performance.now();let n=0;const T=trozos(texto);for(const f of T)n+=(await sintetizar(f)).length;return {seg:n/cfg.sample_rate,ms:Math.round(performance.now()-t0),trozos:T};}
window.VozRumi={hablar,callar,preparar,soporta,activa,medir,estado:()=>estado,
  activar:v=>{try{localStorage.setItem(LS,v?'si':'no');}catch(e){}}};

/* Rumi habla con la voz natural; si no se puede, con la del teléfono */
if(typeof leer==='function'){
  const _leer=leer,_callar=callarVoz;
  leer=function(t){
    // en otros idiomas lee la voz del teléfono en ese idioma (la voz natural es de español)
    const L=window.I18N?I18N.lang():'es';
    if(L!=='es'&&L!=='xx'){const txt=I18N.txt(String(t||'').replace(/<[^>]+>/g,' ')).replace(/\s+/g,' ').trim();if(!txt)return;
      try{if(window.Android&&Android.vozIdioma){Android.vozIdioma(L);Android.hablarCon(txt,'');return;}}catch(e){}
      try{const u=new SpeechSynthesisUtterance(txt);u.lang=L==='pt'?'pt-BR':'en-US';speechSynthesis.cancel();speechSynthesis.speak(u);}catch(e){}return;}
    if(activa()&&soporta()){const txt=paraVoz(t);if(!txt)return;try{if(window.Android&&Android.callar)Android.callar();}catch(e){}
      hablar(txt).then(ok=>{if(!ok)_leer(t);});return;}
    return _leer(t);
  };
  callarVoz=function(){callar();_callar();};
}
/* en Configuración: primero la voz natural, luego las del teléfono */
if(typeof vozCfgHtml==='function'){
  const _cfg=vozCfgHtml;
  vozCfgHtml=function(){
    const L=window.I18N?I18N.lang():'es';
    if(L!=='es'&&L!=='xx')return `<p class="hint" style="margin:0">Rumi lee con la voz del teléfono en tu idioma. La voz natural de Rumi está en español.</p><button type="button" class="btn full" data-act="vozProbar">Escuchar a Rumi</button>${window.Android&&Android.vozAjustes?'<button type="button" class="btn full" data-act="vozInstalar">Instalar voces más naturales</button>':''}`;
    if(!soporta())return _cfg();
    const on=activa(),st=estado==='error'?'<small class="hint" style="margin:0;color:var(--rojo)">Este teléfono no pudo cargar la voz natural; se usa la del teléfono.</small>':'';
    return `<div class="voz-nat${on?' on':''}"><div class="tx"><b>Voz natural de Rumi</b><span>Voz neuronal que corre en tu teléfono, sin internet. Suena como una persona.</span></div>
      <div class="seg" role="group" aria-label="Voz natural"><button type="button" data-act="vozNat" data-v="si" aria-pressed="${on}">Sí</button><button type="button" data-act="vozNat" data-v="no" aria-pressed="${!on}">No</button></div></div>${st}
      <button type="button" class="btn full" data-act="vozProbar">Escuchar a Rumi</button>
      ${on?'':_cfg()}`;
  };
  Object.assign(ACTS,{
    vozNat:el=>{VozRumi.activar(el.dataset.v==='si');const e=$('#vozCfg');if(e)e.innerHTML=vozCfgHtml();if(el.dataset.v==='si')leer(FRASE_PRUEBA);},
    vozProbar:()=>leer(FRASE_PRUEBA)
  });
}
})();
