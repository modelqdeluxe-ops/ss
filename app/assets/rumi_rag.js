// Búsqueda en la guía de Rumi. El mismo código corre en la app (se copia a
// app/assets/rumi_llm.js) y en Node para armar los datos de entrenamiento,
// así el modelo se entrena con el mismo contexto que va a ver en el teléfono.
(function(raiz){
const VACIAS=new Set(('a al algo algun alguna algunos ante asi aun aunque bien cada como con contra cual cuales cuando de del desde donde dos el ella ellas ellos en entre era es esa ese eso esta estan este esto estos fue ha hay hace hacer la las le les lo los mas me mi mis muy nada ni no nos o otra otro para pero poco por porque puedo que quien se sea ser si sin sobre solo son su sus tambien tan te tengo tiene tienen tu tus un una uno unos y ya yo vos usted rumi hola favor dime oye mire').split(' '));
function norm(s){return String(s||'').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/<[^>]+>/g,' ').replace(/[^a-z0-9ñ ]+/g,' ').replace(/\s+/g,' ').trim();}
function raices(s){const r=[];for(const w of norm(s).split(' '))if(w.length>=3&&!VACIAS.has(w))r.push(w.slice(0,5));return r;}
function limpiar(s){return String(s||'').replace(/<[^>]+>/g,'').replace(/\s+/g,' ').trim();}
function indexar(entradas){
  const docs=entradas.map(e=>({e,nom:new Set(raices(e.t)),tit:new Set(raices(e.t+' '+(e.kw||''))),cuerpo:new Set(raices(e.x)),re:e.re?new RegExp(e.re):null}));
  const df=new Map();for(const d of docs)for(const r of new Set([...d.tit,...d.cuerpo]))df.set(r,(df.get(r)||0)+1);
  const N=docs.length,idf=r=>Math.log(1+N/(df.get(r)||N));
  return {docs,idf};
}
function puntuar(ix,pregunta){
  const q=[...new Set(raices(pregunta))],qn=norm(pregunta);
  return ix.docs.map(d=>{let s=0;for(const r of q){if(d.tit.has(r))s+=2.5*ix.idf(r);else if(d.cuerpo.has(r))s+=ix.idf(r);}
    if(d.re&&d.re.test(qn))s+=6;
    // desempate: cuánto del nombre del tema aparece en la pregunta
    if(s>0&&d.nom.size){let c=0;for(const r of d.nom)if(q.includes(r))c++;s+=3*c/d.nom.size;}
    return {e:d.e,s};}).sort((a,b)=>b.s-a.s);
}
function buscar(ix,pregunta,k=2){
  const res=puntuar(ix,pregunta).filter(x=>x.s>=3);
  return res.slice(0,k).filter((x,i)=>i===0||x.s>=res[0].s*0.5).map(x=>x.e);
}
const SISTEMA='Eres Rumi, el ayudante de la app Rumentis para engorde de ganado en Honduras. Responde en español sencillo, de tú, en 1 a 4 oraciones. Usa la Guía cuando sirva. No inventes dosis de medicinas, precios ni datos de los lotes del usuario.';
function mensajes(guia,pregunta){
  const g=guia.length?guia.map(e=>'• '+e.t+': '+limpiar(e.x)).join('\n'):'(nada)';
  return [{role:'system',content:SISTEMA},{role:'user',content:'Guía:\n'+g+'\n\nPregunta: '+pregunta}];
}
const API={norm,raices,limpiar,indexar,puntuar,buscar,mensajes,SISTEMA};
if(typeof module!=='undefined'&&module.exports)module.exports=API;else raiz.RumiRAG=API;
})(this);
