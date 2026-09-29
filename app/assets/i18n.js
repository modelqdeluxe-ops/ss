/* Idiomas de Rumentis.
   La app escribe sus textos en español; esta capa traduce lo que aparece en pantalla (textos y atributos)
   justo antes de pintarse, con catálogos por idioma (i18n_en.js, i18n_pt.js…).
   Los números, fechas y lo que escribe el usuario (nombres de lotes, razas, proveedores…) se guardan
   como {0}, {1}… para que una sola frase del catálogo sirva con cualquier dato. */
(function(){
'use strict';
const LS='rumentis-idioma';
const IDIOMAS=[['es','Español'],['en','English'],['pt','Português']];
const leerLS=()=>{try{return localStorage.getItem(LS)||'';}catch(e){return '';}};
const auto=()=>{const n=String(navigator.language||'es').slice(0,2).toLowerCase();return IDIOMAS.some(i=>i[0]===n)?n:'es';};
let lang=leerLS()||auto();
const DIC=window.I18N_DIC=window.I18N_DIC||{};
const MES=['enero','febrero','marzo','abril','mayo','junio','julio','agosto','septiembre','octubre','noviembre','diciembre'];
const MESC=['ene','feb','mar','abr','may','jun','jul','ago','sep','oct','nov','dic'];
const DIA=['domingo','lunes','martes','miércoles','jueves','viernes','sábado'];
const TMES={en:['January','February','March','April','May','June','July','August','September','October','November','December'],pt:['janeiro','fevereiro','março','abril','maio','junho','julho','agosto','setembro','outubro','novembro','dezembro']};
const TMESC={en:['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'],pt:['jan','fev','mar','abr','mai','jun','jul','ago','set','out','nov','dez']};
const TDIA={en:['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'],pt:['domingo','segunda-feira','terça-feira','quarta-feira','quinta-feira','sexta-feira','sábado']};

/* ---------- lo que escribió el usuario: no se traduce ---------- */
let _dv=-1,_dre=null;
function datos(){
  const v=typeof ver!=='undefined'?ver:0;if(_dv===v&&_dre!==undefined)return _dre;
  const D=new Set();const add=x=>{x=String(x==null?'':x).trim();if(x.length>=2&&!/^\d+([.,]\d+)?$/.test(x))D.add(x);};
  try{
    const c=S.config||{};add(c.finca);add(c.ubicacion);
    for(const l of Object.values(S.lotes||{})){add(l.nombre);add(l.raza);add(l.proveedor);for(const a of (l.animales||[])){add(a.raza);add(a.color);add(a.nota);}}
    for(const r of Object.values(S.raciones||{}))add(r.nombre);
    for(const it of (typeof allItems==='function'?allItems():[])){add(it.comprador);add(it.producto);add(it.desc);add(it.nota);}
    const b=c.bodega||{};for(const it of (b.items||[]))add(it.n);
    const f=c.fin||{};for(const k of ['insumos','equipos','creditos'])for(const it of (f[k]||[]))add(it.n);
    for(const t of (c.tareas||[]))add(t.t);
    // equipo: nombres de los vaqueros (y su nombre de pila, para "Hola, Juan"), la finca y las licencias
    const lic=x=>{x=String(x||'').replace(/[^0-9A-Z]/g,'');if(x.length===20)add('RV-'+x.match(/.{1,4}/g).join('-'));};
    const persona=n=>{add(n);add(String(n||'').trim().split(/\s+/)[0]);};
    const e=c.equipo||{};for(const v of Object.values(e.vaqueros||{}))persona(v.nombre);for(const x of Object.values(e.solicitudes||{})){persona(x.nombre);lic(x.lic);}
    for(const k of Object.keys(e.licencias||{}))lic(k);
    // reportes por registrar: las novedades y los productos que todavía no están en la finca
    for(const r of Object.values(e.reportes||{})){add(r.nota);for(const o of (r.ops||[]))if(o&&o.it){add(o.it.producto);add(o.it.desc);add(o.it.nota);}}
    const vq=window.Vaquero&&Vaquero.VQ?Vaquero.VQ():null;if(vq){persona(vq.nombre);add(vq.finca);lic(vq.lic);for(const n of (vq.nombres||[]))persona(n);}
    add('RV-PRUEBA-2026');
  }catch(e){}
  const L=[...D].sort((a,b)=>b.length-a.length).map(x=>x.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'));
  _dre=L.length?new RegExp('(^|[^A-Za-zÀ-ÿ])('+L.join('|')+')(?=$|[^A-Za-zÀ-ÿ])','g'):null;_dv=v;return _dre;
}
/* ---------- normalizar: texto → clave con {n} y la lista de valores ---------- */
const RE_NUM=/[−-]?\d+(?:[.,]\d+)*/g;
function norm(t){
  const vals=[];const ph=v=>{vals.push(v);return String.fromCharCode(0xE000+vals.length-1);};  // marcas sin dígitos
  const re=datos();
  if(re)t=t.replace(re,(m,a,b)=>a+ph({d:b}));
  // fechas: "22 de noviembre", "28 sep", "lunes 28 de septiembre"
  t=t.replace(/\b(\d{1,2}) de (enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)(?: de (\d{4}))?/g,(m,d,mes,y)=>ph({f:'l',d:+d,m:MES.indexOf(mes),y}));
  t=t.replace(/\b(\d{1,2}) (ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic)\b(?: (\d{4}))?/g,(m,d,mes,y)=>ph({f:'c',d:+d,m:MESC.indexOf(mes),y}));
  t=t.replace(/\b(domingo|lunes|martes|miércoles|jueves|viernes|sábado)\b/g,m=>ph({w:DIA.indexOf(m)}));
  t=t.replace(RE_NUM,m=>ph({n:m}));
  // abreviaturas de unidades: iguales en todos los idiomas
  t=t.replace(/(^|[\s(\/])(lb|kg|qq|cwt|t|ml|g|km|cm|m²|°C|%)(?=$|[\s),.;:\/])/g,(m,a,u)=>a+ph({d:u}));
  const key=t.replace(/[\uE000-\uE0FF]/g,c=>'{'+(c.charCodeAt(0)-0xE000)+'}');
  return {key,vals};
}
function valor(v){
  if(v.d!=null&&v.f==null&&v.m==null)return v.d;
  if(v.n!=null)return v.n;   // los números se ven igual en toda la app
  if(v.w!=null)return (TDIA[lang]||DIA)[v.w];
  if(v.f==='l'){const M=(TMES[lang]||MES)[v.m];return lang==='en'?`${M} ${v.d}${v.y?', '+v.y:''}`:`${v.d} de ${M}${v.y?' de '+v.y:''}`;}
  if(v.f==='c'){const M=(TMESC[lang]||MESC)[v.m];return lang==='en'?`${M} ${v.d}${v.y?' '+v.y:''}`:`${v.d} ${M}${v.y?' '+v.y:''}`;}
  return '';
}
const REC=window.I18N_REC=new Map();
function traducir(t){
  if(lang==='es'||!t)return t;
  const m=/^(\s*)([\s\S]*?)(\s*)$/.exec(t);const core=m[2];if(!core||!/[A-Za-zÀ-ÿ]/.test(core))return t;
  const D=DIC[lang]||{};
  // la moneda del usuario se escribe como "L" en el catálogo y se devuelve la suya al final
  const mon=(typeof S!=='undefined'&&S.config&&S.config.moneda)||'L';
  const esc=mon.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
  // también "en miles de $." al final de la frase
  const aL=x=>mon==='L'?x:x.replace(new RegExp('(^|[^A-Za-zÀ-ÿ])'+esc+'(?=\\s?[−-]?\\d|/|[.)]?\\s*$)','g'),'$1L');
  const deL=x=>mon==='L'||x==null?x:x.replace(/(^|[^A-Za-zÀ-ÿ])L(?=\s?[−-]?\d|\/|[.)]?\s*$)/g,(m,a)=>a+mon);
  const uno=s0=>{const s=aL(s0);const {key,vals}=norm(s);
    if(lang==='xx'){if(/[A-Za-zÀ-ÿ]/.test(key.replace(/\{\d+\}/g,'')))REC.set(key,(REC.get(key)||0)+1);return null;}
    // solo fechas, números o datos: se arman igual, con los meses y días en el idioma
    const tr=D[key]!=null?D[key]:(/[A-Za-zÀ-ÿ]/.test(key.replace(/\{\d+\}/g,''))?null:key);if(tr==null)return null;return deL(tr.replace(/\{(\d+)\}/g,(x,i)=>vals[+i]!=null?valor(vals[+i]):x));};
  let r=uno(core);
  if(r==null&&lang!=='xx'){
    // por partes: frases separadas por punto, dos puntos o punto y coma
    const P=core.split(/(?<=[.:;!?])\s+/);
    if(P.length>1){const T=P.map(p=>uno(p));if(T.some(x=>x!=null))r=T.map((x,i)=>x??P[i]).join(' ');}
  }
  return r==null?t:m[1]+r+m[3];
}
/* ---------- aplicar al DOM ---------- */
const ATR=['placeholder','aria-label','title','alt'];
const HECHO=new WeakMap();
function nodo(n){
  if(n.nodeType===3){const p=n.parentNode;if(!p||p.nodeName==='SCRIPT'||p.nodeName==='STYLE'||(p.closest&&p.closest('[data-no-tr]')))return;
    const v=n.data;if(HECHO.get(n)===v)return;const t=traducir(v);if(t!==v)n.data=t;HECHO.set(n,n.data);return;}
  if(n.nodeType!==1||n.nodeName==='SCRIPT'||n.nodeName==='STYLE'||n.hasAttribute('data-no-tr'))return;
  for(const a of ATR)if(n.hasAttribute(a)){const v=n.getAttribute(a),t=traducir(v);if(t!==v)n.setAttribute(a,t);}
  if((n.nodeName==='INPUT'&&(n.type==='button'||n.type==='submit')))n.value=traducir(n.value);
  const w=document.createTreeWalker(n,NodeFilter.SHOW_TEXT|NodeFilter.SHOW_ELEMENT);let x;
  while((x=w.nextNode())){if(x.nodeType===3)nodo(x);else if(x.nodeType===1){if(x.nodeName==='SCRIPT'||x.nodeName==='STYLE')continue;for(const a of ATR)if(x.hasAttribute(a)){const v=x.getAttribute(a),t=traducir(v);if(t!==v)x.setAttribute(a,t);}}}
}
let obs=null;
function observar(){
  if(obs)obs.disconnect();if(lang==='es')return;
  obs=new MutationObserver(ms=>{obs.disconnect();try{for(const m of ms){if(m.type==='characterData')nodo(m.target);else if(m.type==='attributes')nodo(m.target);else m.addedNodes.forEach(nodo);}}finally{obs.observe(document.documentElement,OPT);}});
  obs.observe(document.documentElement,OPT);
  if(document.body)nodo(document.body);document.title=traducir(document.title);
}
const OPT={childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:ATR};
function cambiar(l){lang=l;try{localStorage.setItem(LS,l);}catch(e){}document.documentElement.lang=l==='xx'?'es':l;location.reload();}
window.I18N={t:traducir,norm,lang:()=>lang,IDIOMAS,cambiar,observar,
  // para dibujar sin rehacer la pantalla: traducir la página nueva antes de compararla, y marcar un texto ya traducido
  pre:n=>{if(lang!=='es')nodo(n);},marcar:n=>{if(n.nodeType===3)HECHO.set(n,n.data);},
  // para textos que la app arma para otros (WhatsApp, respaldo): traducir sin tocar el DOM
  txt:s=>String(s).split('\n').map(traducir).join('\n')};
document.documentElement.lang=lang==='xx'?'es':lang;
observar();
})();
