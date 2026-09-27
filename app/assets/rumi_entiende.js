/* Rumi entiende: decide a qué tema se refiere la pregunta y contesta SOLO con textos revisados
   de la Guía de Rumi. No genera texto, así que no puede inventar.

   1. Las reglas de Rumi contestan primero (cálculos, dosis que sabe calcular, formularios, tus lotes).
   2. Si las reglas no entienden, o la pregunta es de otro tema, entra el modelo de comprensión
      (rumi_motor.html) o, si el teléfono no lo puede cargar, la búsqueda por palabras.
   3. Tema claro → texto de la Guía. Dudoso → "¿Te refieres a…?" con opciones.
      De otro tema → lo dice y sugiere preguntas de la app. Dosis de medicinas → respuesta fija. */
(function(){
const BASE='https://rumentis.local/';
const RAM_MIN_MB=2800;              // el modelo de comprensión usa unos 400 MB
// Umbrales de parecido (coseno) calibrados con preguntas de prueba; ver modelo/embeddings/calibrar.py
let U=window.RUMI_UMBRALES||{tema:0.86,duda:0.80,fuera:0.84,margen:0.015};
const E={estado:'apagado',error:null,ocupado:false,modelo:null};
window.RumiEntiende=E;

let GUIA=null,IX=null;
const texto=h=>String(h||'').replace(/<[^>]+>/g,' ').replace(/\s+/g,' ').trim();
const norm=s=>texto(s).toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g,'');
function ramMB(){try{return window.Android&&Android.ramMB?Number(Android.ramMB()):0;}catch(e){return 0;}}
function cargarScript(src){return new Promise((ok,mal)=>{const s=document.createElement('script');s.src=src;s.onload=ok;s.onerror=mal;document.head.appendChild(s);});}

// ---------- motor (iframe) ----------
let marco=null,sig=0;const pend=new Map();
addEventListener('message',e=>{
  const m=e.data||{};if(m.rumiMotor!==1||!marco||e.source!==marco.contentWindow)return;
  if(m.listo){E.modelo=m.modelo;if(m.umbrales)U=m.umbrales;E.estado='listo';return;}
  if(m.id==null){if(m.error){E.estado='error';E.error=m.error;console.error('Rumi entiende:',m.error);}return;}
  const p=pend.get(m.id);if(!p)return;pend.delete(m.id);m.error?p.mal(new Error(m.error)):p.ok(m.res);
});
function motor(txt){
  return new Promise((ok,mal)=>{const id=++sig;pend.set(id,{ok,mal});
    marco.contentWindow.postMessage({rumiApp:1,tipo:'entender',id,texto:txt},'*');
    setTimeout(()=>{if(pend.has(id)){pend.delete(id);mal(new Error('tiempo'));}},15000);});
}

E.iniciar=async function(){
  if(E.estado!=='apagado')return;
  E.estado='cargando';
  try{
    await cargarScript(BASE+'rumi_rag.js');
    GUIA=await (await fetch(BASE+'rumi_saber.json')).json();
    IX=RumiRAG.indexar(GUIA);
  }catch(e){E.estado='error';E.error='No cargó la guía';return;}
  if(!window.RUMI_FORZAR&&ramMB()<RAM_MIN_MB){E.estado='palabras';return;}
  marco=document.createElement('iframe');
  marco.src=BASE+'rumi_motor.html';marco.title='Rumi';marco.setAttribute('aria-hidden','true');marco.tabIndex=-1;
  marco.style.cssText='position:fixed;width:1px;height:1px;border:0;opacity:0;pointer-events:none;left:-9px;top:-9px';
  document.body.appendChild(marco);
  setTimeout(()=>{if(E.estado==='cargando'){E.estado='palabras';E.error='El modelo tardó demasiado';}},120000);
};

// ---------- entender ----------
// Devuelve [{c: tema | 'FUERA' | 'APP', s: parecido 0..1}], de mayor a menor.
async function entender(txt){
  if(E.estado==='listo'){try{return {res:await motor(txt),via:'modelo'};}catch(e){}}
  if(!IX)return {res:[],via:'nada'};
  // Respaldo por palabras: convierte el puntaje a una escala parecida a la del modelo.
  const p=RumiRAG.puntuar(IX,txt).slice(0,4);
  return {via:'palabras',res:p.filter(x=>x.s>0).map(x=>({c:x.e.t,s:Math.min(.99,.6+x.s/60)}))};
}
const tema=t=>GUIA&&GUIA.find(e=>e.t===t);

// ---------- dosis ----------
const PIDE_DOSIS=/(\bdosis\b|cuant[oa]s? (ml|cc|mililitros|centimetros|mg)\b|\b(inyect|aplic)\w* .{0,25}(cuant|que cantidad)|(cuant[oa]s?|que cantidad) .{0,25}\b(inyect|aplic)\w*)/;
const MEDICINA=/(penicilin|antibiotic|\w+cilina|\w+micina|\w+floxacin|dexameta|meloxicam|flunixin|vitamina|complejo b|calcio|suero|medicina|medicamento|remedio|desparasitante)/;
const CUANTO_PONGO=/(cuant[oa]s?|que cantidad) .{0,30}\b(le |les )?(pongo|doy|echo)\b/;
const SABE_DOSIS=/(ivermectina|doramectina|albendazol|levamisol|oxitetra|terramicina|\boxi\b|closantel)/;
const RESP_DOSIS='No tengo la dosis de ese producto y no la voy a inventar. Sigue la etiqueta y lo que te indique tu veterinario, y anótalo en Sanidad con sus días de retiro. Las dosis que sí calculo son de ivermectina, doramectina, albendazol, levamisol, oxitetraciclina y closantel: escríbeme, por ejemplo, <b>"dosis de ivermectina para 350 kilos"</b>.';
E.esDosis=txt=>{const t=norm(txt);return (PIDE_DOSIS.test(t)||CUANTO_PONGO.test(t)&&MEDICINA.test(t))&&!SABE_DOSIS.test(t);};

// ---------- decidir ----------
const FUERA_HTML='Eso no es de mi tema. Soy Rumi y solo sé de <b>engorde de ganado</b> y de cómo usar <b>Rumentis</b>: alimentación, sanidad, manejo, costos, ventas y tus lotes. Pregúntame algo de eso, por ejemplo:';
function respTema(t,otros){
  const e=tema(t);if(!e)return null;
  const rel=otros.filter(o=>o.c!==t&&tema(o.c)).slice(0,3).map(o=>o.c);
  return {html:`<b>${esc(e.t)}</b><br>${esc(texto(e.x))}`,sug:rel.length?rel:sugs(),nou:true,guia:e.t};
}
function respDuda(temas){
  return {html:'No estoy seguro de qué me preguntas. ¿Te refieres a alguno de estos temas?',sug:temas,nou:true};
}

// ¿Debe Rumi entender esta pregunta con la Guía, en vez de dejar la respuesta de las reglas?
E.maneja=function(txt,r){
  if(E.estado==='apagado'||E.estado==='error')return false;
  if(E.esDosis(txt))return true;
  if(r&&(r.run||r.btns&&r.btns.length))return false;
  const f=typeof firmaRespuesta==='function'?firmaRespuesta(r):'otra';
  return f==='debil'||f==='saber'||f==='definicion'||/^Eso (se sale de lo mío|no lo sé)/.test(texto(r&&r.html));
};

E.responder=async function(txt,r){
  if(E.esDosis(txt)){botSay({html:RESP_DOSIS,sug:sugs(),nou:true});return;}
  E.ocupado=true;
  let out=null;
  try{
    const {res,via}=await entender(txt);
    const f=typeof firmaRespuesta==='function'?firmaRespuesta(r):'otra';
    const top=res[0],seg=res[1];
    const temas=res.filter(x=>x.c!=='FUERA'&&x.c!=='APP'&&tema(x.c));
    const reglaTema=(/^<b>([^<]+)<\/b><br>/.exec((r&&r.html)||'')||[])[1];
    if(top){
      if(top.c==='FUERA'&&top.s>=U.fuera){out={html:FUERA_HTML,sug:sugs(),nou:true};}
      else if(top.c==='APP'){out=null;}                       // orden de la app: que respondan las reglas
      else if(f==='saber'||f==='definicion'){
        // Las reglas ya dieron un tema: solo se cambia si el modelo está seguro de otro tema.
        const suyo=res.find(x=>x.c===reglaTema);
        if(tema(top.c)&&top.c!==reglaTema&&top.s>=U.tema&&(!suyo||suyo.s<top.s-0.03))out=respTema(top.c,temas);
      }
      else if(tema(top.c)&&top.s>=U.tema&&(!seg||seg.c===top.c||top.s-seg.s>=U.margen||!tema(seg.c)))out=respTema(top.c,temas);
      else if(temas.length&&temas[0].s>=U.duda)out=respDuda(temas.slice(0,3).map(x=>x.c));
      else if(top.c==='FUERA'&&top.s>=U.duda)out={html:FUERA_HTML,sug:sugs(),nou:true};
    }
    if(out)out.via=via;
  }catch(e){out=null;}
  E.ocupado=false;
  botSay(out||r);
};

E.iniciar();
})();
