// Junta la guía de Rumi: el saber que ya trae la app (SABER, CONCEPTOS, LUGARES, TOUR)
// más modelo/saber_extra.json y modelo/saber/*.json. Escribe app/assets/rumi_saber.json.
const fs=require('fs'),path=require('path');
const RAIZ=path.join(__dirname,'..');
const html=fs.readFileSync(path.join(RAIZ,'app/assets/index.html'),'utf8').split('\n');

function arreglo(nombre){
  const i=html.findIndex(l=>l.startsWith('const '+nombre+'=['));
  if(i<0)throw new Error('No encontré '+nombre);
  let j=i;while(!/^\];?\s*$/.test(html[j]))j++;
  return eval('('+html.slice(i,j+1).join('\n').replace('const '+nombre+'=','').replace(/;\s*$/,'')+')');
}
const src=o=>o instanceof RegExp?o.source:undefined;
const limpiar=s=>String(s||'').replace(/<[^>]+>/g,'').replace(/\s+/g,' ').trim();

const saber=[];
for(const o of arreglo('SABER'))saber.push({t:o.t,x:limpiar(o.x),re:src(o.re)});
for(const o of arreglo('CONCEPTOS'))saber.push({t:(s=>s[0].toUpperCase()+s.slice(1))(o.t.replace(/^(La|El|Los|Las|Un|Una) /,'')),x:limpiar(o.x),re:src(o.re)});
for(const o of arreglo('LUGARES'))saber.push({t:'Dónde está '+o.t,x:limpiar(o.how.join(' ')),re:src(o.re)});
for(const o of arreglo('TOUR'))saber.push({t:'Pantalla '+o.t,x:limpiar(o.x)});
for(const o of JSON.parse(fs.readFileSync(path.join(__dirname,'saber_extra.json'),'utf8')))saber.push(o);
const DIR=path.join(__dirname,'saber');
for(const f of fs.readdirSync(DIR).filter(f=>f.endsWith('.json')).sort())
  for(const o of JSON.parse(fs.readFileSync(path.join(DIR,f),'utf8')))saber.push({...o,_f:f.replace('.json','')});
// Área de cada tema (para el menú de Rumi)
const POR_ARCHIVO={nutricion:'nutricion',nutricion_2:'nutricion',sanidad:'sanidad',sanidad_2:'sanidad',manejo:'manejo',manejo_2:'manejo',economia_normas:'negocio',negocio_2:'negocio',app:'app'};
const AREAS=JSON.parse(fs.readFileSync(path.join(__dirname,'areas.json'),'utf8'));
const areaDe=new Map();for(const [a,ts] of Object.entries(AREAS))if(!a.startsWith('_'))for(const t of ts)areaDe.set(t,a);
const quitar=new Set(AREAS._quitar||[]);
for(let i=saber.length-1;i>=0;i--)if(quitar.has(saber[i].t))saber.splice(i,1);
for(const e of saber){if(e.a){delete e._f;continue;}if(areaDe.has(e.t))e.a=areaDe.get(e.t);else if(e._f&&POR_ARCHIVO[e._f])e.a=POR_ARCHIVO[e._f];delete e._f;}
const sinArea=saber.filter(e=>!e.a).map(e=>e.t);if(sinArea.length)throw new Error('Temas sin área: '+sinArea.join(' | '));
const vistos=new Set();for(const e of saber){if(vistos.has(e.t))throw new Error('Tema repetido: '+e.t);vistos.add(e.t);}

const limpio=saber.map(({t,x,a})=>({t,x,a}));
fs.writeFileSync(path.join(RAIZ,'app/assets/rumi_saber.json'),JSON.stringify(limpio));
// La misma guía como script, para cargarla con <script> desde file:// (fetch no lee file://)
fs.writeFileSync(path.join(RAIZ,'app/assets/rumi_guia.js'),'window.RUMI_GUIA='+JSON.stringify(limpio)+';\n');
const cuenta={};for(const e of limpio)cuenta[e.a]=(cuenta[e.a]||0)+1;console.log(cuenta);
console.log('Guía de Rumi:',saber.length,'temas');
