// Junta la guía de Rumi: el saber que ya trae la app (SABER, CONCEPTOS, LUGARES, TOUR)
// más modelo/saber_extra.json. Escribe app/assets/rumi_saber.json.
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

fs.writeFileSync(path.join(RAIZ,'app/assets/rumi_saber.json'),JSON.stringify(saber));
console.log('Guía de Rumi:',saber.length,'temas');
