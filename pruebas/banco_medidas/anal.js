// medidas de frente sobre fotos reales: mediana (fracción de la estatura) contra ANSUR II y % fuera de los límites
const fs=require('fs');const {cargar,D}=require('./h.js');
const P=cargar(process.argv[2]);const B=JSON.parse(fs.readFileSync(process.argv[3]||'banco.json'));const AP=JSON.parse(fs.readFileSync('ansur_pct.json'));
const lim=JSON.parse(JSON.stringify(P.ANSUR.limites));for(const k in P.ANSUR.limites)P.ANSUR.limites[k]=[0,9];
const alto=170,ref=alto+P.EXTRA_CM,S=D[1][0];const TS=P.tramos(S.sil,ref/S.med.alto),KS=P.kpT(S);
const V={};let n=0,algun=0;const filas=[];
for(const t of B){if(t.tipo!=='F')continue;const T=P.tramos(t.sil,ref/t.med.alto),K=P.kpT(t);
  const M=P.medidasPersona(T,TS,K,KS,alto,ref,0);n++;let fuera=[];
  for(const k of ['bid','cb','wb','hb','th','lt','cf']){const r=M.d[k]/alto;(V[k]=V[k]||[]).push(r);if(r<lim[k][0]||r>lim[k][1])fuera.push(k+(r<lim[k][0]?'↓':'↑'));}
  if(fuera.length)algun++;filas.push([t.id,fuera.join(' ')]);}
const pc=(v,p)=>{const s=v.slice().sort((a,b)=>a-b);return s[Math.floor(p*(s.length-1))];};
console.log('fotos de frente',n,'con alguna medida fuera',(100*algun/n).toFixed(1)+'%');
for(const k in V){const v=V[k];const lo=v.filter(r=>r<lim[k][0]).length,hi=v.filter(r=>r>lim[k][1]).length;
  console.log(k.padEnd(4),'mediana',pc(v,.5).toFixed(3),'ANSUR',AP[k][2].toFixed(3),'p5–p95',pc(v,.05).toFixed(3)+'–'+pc(v,.95).toFixed(3),'ANSUR',AP[k][1]+'–'+AP[k][3],'bajo',(100*lo/v.length).toFixed(1)+'%','alto',(100*hi/v.length).toFixed(1)+'%');}
if(process.env.DET)console.log(filas.filter(f=>f[1]).map(f=>f.join(':')).join('\n'));
