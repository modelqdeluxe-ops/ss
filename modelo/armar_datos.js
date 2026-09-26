// Arma los datos de entrenamiento con el mismo contexto (Guía) que Rumi ve en el teléfono.
// Salida: modelo/datos/train.jsonl y modelo/datos/eval.jsonl
const fs=require('fs'),path=require('path');
const R=require('./rag.js');
const D=path.join(__dirname,'datos');
const saber=JSON.parse(fs.readFileSync(path.join(__dirname,'../app/assets/rumi_saber.json'),'utf8'));
const ix=R.indexar(saber);const porT=new Map(saber.map(e=>[e.t,e]));
let semilla=7;const azar=()=>(semilla=(semilla*1103515245+12345)%2147483648)/2147483648;

const ejemplos=[];
function agregar(q,a,t){
  let g=R.buscar(ix,q,2);
  if(t){const oro=porT.get(t);if(!oro)throw new Error('Tema sin guía: '+t);
    if(!g.includes(oro)){g=g.length?[g[0],oro]:[oro];if(azar()<0.5)g.reverse();}}
  ejemplos.push({t:t||'',messages:[...R.mensajes(g,q),{role:'assistant',content:a}]});
}
for(const f of ['preguntas_a.json','preguntas_b.json','preguntas_c.json']){
  const P=JSON.parse(fs.readFileSync(path.join(D,f),'utf8'));
  for(const [t,qs] of Object.entries(P)){const e=porT.get(t);if(!e)throw new Error('Tema sin guía: '+t);for(const q of qs)agregar(q,R.limpiar(e.x),t);}
}
for(const f of ['libres.json','libres_b.json'])for(const o of JSON.parse(fs.readFileSync(path.join(D,f),'utf8')))agregar(o.q,o.a,o.t);

// Separa 1 de cada 12 para evaluar (no se entrena con ellos).
const train=[],ev=[];ejemplos.forEach((e,i)=>(i%12===5?ev:train).push(e));
fs.writeFileSync(path.join(D,'train.jsonl'),train.map(e=>JSON.stringify(e)).join('\n')+'\n');
fs.writeFileSync(path.join(D,'eval.jsonl'),ev.map(e=>JSON.stringify(e)).join('\n')+'\n');
const sinOro=ejemplos.filter(e=>e.t).length;
console.log('ejemplos',ejemplos.length,'train',train.length,'eval',ev.length,'con tema',sinOro);
