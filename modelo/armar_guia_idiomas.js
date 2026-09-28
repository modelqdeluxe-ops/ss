// Arma app/assets/rumi_guia_en.js y rumi_guia_pt.js con la guía de Rumi traducida.
// Fuente: modelo/traducciones/guia/*.tsv (id, inglés, portugués), con los mismos ids que salen de rumi_guia.js:
//   F<i>.t título, F<i>.r resumen, F<i>.<j>h encabezado de una parte, F<i>.<j>.<k> punto; G<i>.t y G<i>.x datos rápidos.
// Los "relacionados" se enlazan por el título traducido. Lo que falte queda en español y se avisa.
const fs=require('fs'),path=require('path');
const RAIZ=path.join(__dirname,'..');
global.window={};require(path.join(RAIZ,'app/assets/rumi_guia.js'));
const F=window.RUMI_FICHAS,G=window.RUMI_GUIA;
const DIR=path.join(__dirname,'traducciones','guia');
const T={en:new Map(),pt:new Map()};
for(const f of fs.readdirSync(DIR).filter(f=>f.endsWith('.tsv')).sort())
  for(const l of fs.readFileSync(path.join(DIR,f),'utf8').split('\n')){if(!l.trim())continue;const p=l.split('\t');if(p.length!==3){console.log('línea mala',f,l.slice(0,60));continue;}T.en.set(p[0],p[1]);T.pt.set(p[0],p[2]);}
const faltan=[];
for(const L of ['en','pt']){
  const tr=(id,es)=>{const v=T[L].get(id);if(v==null){faltan.push(L+' '+id);return es;}return v;};
  const tit=new Map(F.map((f,i)=>[f.t,tr('F'+i+'.t',f.t)]));
  const fichas=F.map((f,i)=>({t:tit.get(f.t),a:f.a,r:tr('F'+i+'.r',f.r),s:f.s.map((s,j)=>({h:tr('F'+i+'.'+j+'h',s.h),p:s.p.map((p,k)=>tr('F'+i+'.'+j+'.'+k,p))})),rel:(f.rel||[]).map(r=>tit.get(r)||r)}));
  const guia=G.map((g,i)=>({t:tr('G'+i+'.t',g.t),x:tr('G'+i+'.x',g.x),a:g.a}));
  fs.writeFileSync(path.join(RAIZ,'app/assets/rumi_guia_'+L+'.js'),'/* Guía de Rumi en '+L+'. La arma modelo/armar_guia_idiomas.js. */\nwindow.RUMI_GUIA='+JSON.stringify(guia)+';\nwindow.RUMI_FICHAS='+JSON.stringify(fichas)+';\nwindow.RUMI_FICHAS_ES='+JSON.stringify(F.map(f=>f.t))+';\n');
}
const n=[...new Set(faltan.map(x=>x.split(' ')[1]))].length;
console.log('fichas',F.length,'datos',G.length,'traducidas en',T.en.size,'faltan',n);
if(process.argv[2]==='-v')console.log(faltan.slice(0,40).join('\n'));
