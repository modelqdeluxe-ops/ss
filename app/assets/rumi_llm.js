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
const L={estado:'apagado',error:null,ocupado:false,cola:Promise.resolve()};
window.RumiLLM=L;

function ramMB(){try{return window.Android&&Android.ramMB?Number(Android.ramMB()):0;}catch(e){return 0;}}

// El motor corre en un iframe con origen https://rumentis.local (ver rumi_motor.html):
// desde file:// el navegador no deja arrancar el worker de wllama.
let marco=null,sig=0;const pend=new Map();
addEventListener('message',e=>{
  const m=e.data||{};if(m.rumiMotor!==1||!marco||e.source!==marco.contentWindow)return;
  if(m.listo){L.modelo=m.modelo;L.estado='listo';return;}
  if(m.id==null){if(m.error){L.estado='error';L.error=m.error;console.error('Rumi avanzado:',m.error);}return;}
  const p=pend.get(m.id);if(!p)return;
  if(m.parcial!=null&&p.alToken)p.alToken(m.parcial);
  if(m.fin){pend.delete(m.id);m.error?p.mal(new Error(m.error)):p.ok(m);}
});

L.iniciar=function(){
  if(L.estado!=='apagado')return;
  if(!window.RUMI_LLM_FORZAR&&ramMB()<RAM_MIN_MB){L.estado='sin_ram';return;}
  L.estado='cargando';
  const s=document.createElement('script');s.src=BASE+'rumi_rag.js';
  s.onload=()=>fetch(BASE+'rumi_saber.json').then(r=>r.json()).then(g=>{IX=RumiRAG.indexar(g);}).catch(()=>{});
  document.head.appendChild(s);
  marco=document.createElement('iframe');
  marco.src=BASE+'rumi_motor.html';marco.title='Rumi';marco.setAttribute('aria-hidden','true');marco.tabIndex=-1;
  marco.style.cssText='position:fixed;width:1px;height:1px;border:0;opacity:0;pointer-events:none;left:-9px;top:-9px';
  document.body.appendChild(marco);
};

L.listo=()=>L.estado==='listo';

const texto=h=>String(h||'').replace(/<[^>]+>/g,' ').replace(/\s+/g,' ').trim();
// Cuándo contesta el modelo en vez de las reglas.
let IX=null;   // la Guía también en esta página, para decidir rápido
L.usar=function(r,txt){
  if(!L.listo()||L.ocupado)return false;
  if(r&&(r.run||r.btns&&r.btns.length))return false;
  const f=typeof firmaRespuesta==='function'?firmaRespuesta(r):'otra';
  const h=texto(r&&r.html);
  // Solo temas de ganado de carne: si la Guía no tiene nada parecido, el modelo no contesta
  // y queda la respuesta de las reglas ("no te entendí" o "eso se sale de lo mío").
  const top=IX?RumiRAG.puntuar(IX,txt)[0]:null;
  if(IX&&(!top||top.s<8)&&!L.esDosis(txt))return false;
  if(f==='debil'||/^Eso se sale de lo mío/.test(h))return true;
  // Las reglas contestaron un tema de su saber, pero la Guía encuentra otro tema mucho más
  // cercano a la pregunta (p. ej. "orina color café" → ranilla, no neumonía).
  const m=/^<b>([^<]+)<\/b><br>/.exec((r&&r.html)||'');
  if(m&&IX){const p=RumiRAG.puntuar(IX,txt);const top=p[0],suyo=p.find(x=>x.e.t===m[1]);
    if(top&&top.e.t!==m[1]&&top.s>=12&&(!suyo||suyo.s<top.s*0.6))return true;}
  return false;
};

// Dosis de medicinas: nunca las contesta el modelo. Las que la app sabe calcular las
// contestan las reglas; para las demás, esta respuesta fija.
const PIDE_DOSIS=/(\bdosis\b|cuant[oa]s? (ml|cc|mililitros|centimetros|mg)\b|\b(inyect|aplic)\w* .{0,25}(cuant|que cantidad)|(cuant[oa]s?|que cantidad) .{0,25}\b(inyect|aplic)\w*)/;
const MEDICINA=/(penicilin|antibiotic|\w+cilina|\w+micina|\w+floxacin|dexameta|meloxicam|flunixin|vitamina|complejo b|calcio|suero|medicina|medicamento|remedio|desparasitante)/;
const CUANTO_PONGO=/(cuant[oa]s?|que cantidad) .{0,30}\b(le |les )?(pongo|doy|echo)\b/;
const SABE_DOSIS=/(ivermectina|doramectina|albendazol|levamisol|oxitetra|terramicina|\boxi\b|closantel)/;
const RESP_DOSIS='No tengo la dosis de ese producto y no la voy a inventar. Sigue la etiqueta y lo que te indique tu veterinario, y anótalo en Sanidad con sus días de retiro. Las dosis que sí calculo son de ivermectina, doramectina, albendazol, levamisol, oxitetraciclina y closantel: escríbeme, por ejemplo, <b>"dosis de ivermectina para 350 kilos"</b>.';
L.esDosis=txt=>{const t=texto(txt).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'');return (PIDE_DOSIS.test(t)||CUANTO_PONGO.test(t)&&MEDICINA.test(t))&&!SABE_DOSIS.test(t);};

const DOSIS_RE=/\b\d+([.,]\d+)?\s?(ml|cc|mg|mililitros?|miligramos?)\b/i;
L.generar=function(pregunta,alToken){
  return new Promise((ok,mal)=>{const id=++sig;pend.set(id,{ok,mal,alToken});
    marco.contentWindow.postMessage({rumiApp:1,tipo:'generar',id,pregunta},'*');}).then(m=>{
    let out=m.texto||'';
    if(DOSIS_RE.test(out)&&!/veterinari/i.test(out))out+=' Confirma cualquier dosis con tu veterinario.';
    return {texto:out,tema:m.tema||null,guia:m.guia||[]};
  });
};

// Responde en el chat de Rumi con el modelo, escribiendo la respuesta mientras se genera.
L.decir=function(txt,r){
  if(L.esDosis(txt)){botSay({html:RESP_DOSIS,sug:(r&&r.sug)||sugs(),nou:true});return;}
  L.ocupado=true;
  const entrada={html:'',nou:true,llm:true,q:txt,sug:(r&&r.sug)||sugs()};
  let ultimo=0,pend=null;
  const pintar=(t,fin)=>{entrada.html=(entrada.tema?'<b>'+esc(entrada.tema)+'</b><br>':'')+esc(t)+(fin?'':'<span class="llm-cursor"></span>');
    const now=Date.now();if(!fin&&now-ultimo<120){clearTimeout(pend);pend=setTimeout(()=>pintar(entrada.t||t),130);return;}
    ultimo=now;pintarLog();};
  RUMI.log.push(entrada);
  L.cola=L.cola.then(async()=>{
    try{
      const res=await L.generar(txt,t=>{entrada.t=t;pintar(t);});
      entrada.t=res.texto||'No te entendí bien. ¿Me lo dices de otra forma?';entrada.tema=res.tema;
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
