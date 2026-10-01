const fs=require('fs');
function cargar(file){const L=fs.readFileSync(file||require('path').join(__dirname,'../../app/assets/pesocam.js'),'utf8').split('\n');
  const i0=L.findIndex(l=>l.startsWith('const EXTRA_CM')),i1=L.findIndex(l=>l.startsWith('const FORMA')),a=L.findIndex(l=>l.startsWith('const med=')),b=L.findIndex(l=>l.startsWith('function combinar'));
  const src=[L[i0],L[i1],...L.slice(a,b)].join('\n')+'\nreturn {tramos,kpT,medidasPersona,EXTRA_CM,ANSUR,marco,nivel,banda,pctl,tramoCentro,cortar,giroPerfil};';
  return new Function(src)();}
const D=fs.existsSync('caps.json')?JSON.parse(fs.readFileSync('caps.json')):[];for(const a of D)for(const t of a)if(t.kp&&!Array.isArray(t.kp))t.kp=Object.keys(t.kp).sort((x,y)=>x-y).map(k=>t.kp[k]);
module.exports={cargar,D};
if(require.main===module){const P=cargar(process.argv[2]);const alto=170,ref=alto+P.EXTRA_CM;
  const conK=t=>{const T=P.tramos(t.sil,ref/t.med.alto);return {T,K:P.kpT(t)};};
  for(const a of D[0])for(const b of D[1]){const A=conK(a),B=conK(b);const M=P.medidasPersona(A.T,B.T,A.K,B.K,alto,ref,0);console.log(JSON.stringify(Object.fromEntries(Object.entries(M.d).map(([k,v])=>[k,+v.toFixed(1)]))),M.topes.join(','),M.pred.toFixed(1));}}
