// Arma app/assets/rumi_guia_en.js y rumi_guia_pt.js con la guía de Rumi traducida.
// Fuente: modelo/traducciones/guia/*.tsv (id, inglés, portugués), con los mismos ids que salen de rumi_guia.js:
//   F<i>.t título, F<i>.r resumen, F<i>.<j>h encabezado de una parte, F<i>.<j>.<k> punto; G<i>.t y G<i>.x datos rápidos.
// Los "relacionados" se enlazan por el título traducido. Lo que falte queda en español y se avisa.
// guia/origen.es guarda el español que se tradujo (id, texto): si un texto cambió o se movió de lugar, su
// traducción no se usa (queda en español) hasta revisarla. Tras traducir, `--fijar` actualiza origen.es.
const fs=require('fs'),path=require('path');
const RAIZ=path.join(__dirname,'..');
global.window={};require(path.join(RAIZ,'app/assets/rumi_guia.js'));
const F=window.RUMI_FICHAS,G=window.RUMI_GUIA;
const DIR=path.join(__dirname,'traducciones','guia');
const T={en:new Map(),pt:new Map()};
for(const f of fs.readdirSync(DIR).filter(f=>f.endsWith('.tsv')).sort())
  for(const l of fs.readFileSync(path.join(DIR,f),'utf8').split('\n')){if(!l.trim())continue;const p=l.split('\t');if(p.length!==3){console.log('línea mala',f,l.slice(0,60));continue;}T.en.set(p[0],p[1]);T.pt.set(p[0],p[2]);}
// el español de hoy, con los mismos ids
const ES=new Map();
F.forEach((f,i)=>{ES.set('F'+i+'.t',f.t);ES.set('F'+i+'.r',f.r);f.s.forEach((s,j)=>{ES.set('F'+i+'.'+j+'h',s.h);s.p.forEach((p,k)=>ES.set('F'+i+'.'+j+'.'+k,p));});});
G.forEach((g,i)=>{ES.set('G'+i+'.t',g.t);ES.set('G'+i+'.x',g.x);});
const ORIG=path.join(DIR,'origen.es');
if(process.argv.includes('--fijar'))fs.writeFileSync(ORIG,[...ES].map(([k,v])=>k+'\t'+v).join('\n')+'\n');
const O=new Map();if(fs.existsSync(ORIG))for(const l of fs.readFileSync(ORIG,'utf8').split('\n')){const i=l.indexOf('\t');if(i>0)O.set(l.slice(0,i),l.slice(i+1));}
const cambio=[...ES].filter(([k,v])=>O.has(k)&&O.get(k)!==v).map(([k])=>k);
for(const k of cambio){T.en.delete(k);T.pt.delete(k);}
if(cambio.length)console.log('Cambiaron en español (revisar su traducción y correr con --fijar):',cambio.length,cambio.slice(0,20).join(' '));
// Versión regional: el inglés es para EE.UU. y el portugués para Brasil. guia/region/<idioma>/*.json reemplaza lo que
// cambia con la región (instituciones, leyes, épocas, unidades, forrajes, razas, precios, ejemplos):
//   "F12": {t, r, s:[{h, p:[…]}]}   el tema completo, con las partes que necesite
//   "G40": {t, x}                  un dato rápido completo
//   "F91.0.2": "texto"             una sola línea
//   "+clave": {t, a, r, s, rel}    un tema que solo existe en esa región (se agrega al final)
const REG={en:{},pt:{}};
for(const L of ['en','pt']){const d=path.join(DIR,'region',L);if(!fs.existsSync(d))continue;
  for(const f of fs.readdirSync(d).filter(f=>f.endsWith('.json')).sort()){const o=JSON.parse(fs.readFileSync(path.join(d,f),'utf8'));
    for(const [k,v] of Object.entries(o)){if(k in REG[L])console.log('Repetido en región',L,k,f);REG[L][k]=v;}}}
// cada clave regional debe apuntar a algo que exista en la guía en español
const existe=k=>{let m;
  if(m=/^F(\d+)$/.exec(k))return !!F[+m[1]];
  if(m=/^G(\d+)(\.[tx])?$/.exec(k))return !!G[+m[1]];
  if(m=/^F(\d+)\.([tr])$/.exec(k))return !!F[+m[1]];
  if(m=/^F(\d+)\.(\d+)h$/.exec(k))return !!(F[+m[1]]&&F[+m[1]].s[+m[2]]);
  if(m=/^F(\d+)\.(\d+)\.(\d+)$/.exec(k))return !!(F[+m[1]]&&F[+m[1]].s[+m[2]]&&F[+m[1]].s[+m[2]].p[+m[3]]!=null);
  return k[0]==='+';};
for(const L of ['en','pt'])for(const k of Object.keys(REG[L]))if(!existe(k))console.log('Clave regional sin destino',L,k);
const faltan=[];
for(const L of ['en','pt']){
  const R=REG[L];
  const tr=(id,es)=>{if(typeof R[id]==='string')return R[id];const v=T[L].get(id);if(v==null){faltan.push(L+' '+id);return es;}return v;};
  const tit=new Map(F.map((f,i)=>[f.t,R['F'+i]&&R['F'+i].t||tr('F'+i+'.t',f.t)]));
  const fichas=F.map((f,i)=>{const rel=(f.rel||[]).map(r=>tit.get(r)||r);const o=R['F'+i];
    if(o&&typeof o==='object')return {t:o.t,a:f.a,r:o.r,s:o.s,rel:o.rel||rel};
    return {t:tit.get(f.t),a:f.a,r:tr('F'+i+'.r',f.r),s:f.s.map((s,j)=>({h:tr('F'+i+'.'+j+'h',s.h),p:s.p.map((p,k)=>tr('F'+i+'.'+j+'.'+k,p))})),rel};});
  for(const [k,o] of Object.entries(R))if(k[0]==='+')fichas.push({t:o.t,a:o.a,r:o.r,s:o.s,rel:(o.rel||[]).map(r=>tit.get(r)||r)});
  const guia=G.map((g,i)=>{const o=R['G'+i];if(o&&typeof o==='object')return {t:o.t,x:o.x,a:g.a};return {t:tr('G'+i+'.t',g.t),x:tr('G'+i+'.x',g.x),a:g.a};});
  // revisar que cada tema regional esté completo y que sus relacionados existan
  const tt=new Set(fichas.map(f=>f.t));
  for(const f of fichas){if(!f.t||!f.r||!(f.s||[]).length||f.s.some(s=>!s.h||!(s.p||[]).length))console.log('Tema regional incompleto',L,f.t);
    f.rel=(f.rel||[]).filter(r=>tt.has(r));}
  console.log(L,'regional:',Object.keys(R).length,'reemplazos,',Object.keys(R).filter(k=>k[0]==='+').length,'temas propios');
  fs.writeFileSync(path.join(RAIZ,'app/assets/rumi_guia_'+L+'.js'),'/* Guía de Rumi en '+L+'. La arma modelo/armar_guia_idiomas.js. */\nwindow.RUMI_GUIA='+JSON.stringify(guia)+';\nwindow.RUMI_FICHAS='+JSON.stringify(fichas)+';\nwindow.RUMI_FICHAS_ES='+JSON.stringify(F.map(f=>f.t))+';\n');
}
const n=[...new Set(faltan.map(x=>x.split(' ')[1]))].length;
console.log('fichas',F.length,'datos',G.length,'traducidas en',T.en.size,'faltan',n);
if(process.argv.includes('-v'))console.log(faltan.slice(0,40).join('\n'));
