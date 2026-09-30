/* Rumentis Beta: peso por cámara, en vivo y sin nada que imprimir.
   La cámara (camvivo.js) toma dos fotos solas, cuando la silueta está en verde, y las mide en píxeles. La escala en
   centímetros sale de una estatura conocida:
   - Personas (para probar la beta sin estar en la finca): tu estatura, que se escribe una vez. De frente y de costado.
   - Ganado: una persona de estatura conocida parada junto al animal. De costado y por detrás.
   Peso por volumen: el cuerpo se corta en rebanadas y cada corte se toma como una elipse (personas: ancho de frente ×
   profundidad de costado a la misma altura; ganado: a lo largo del animal de costado, con la forma que se ve por
   detrás). kg = k · litros^b; de fábrica k es la densidad del cuerpo y b = 1; se ajusta con la báscula
   (S.config.pesoCam.cal).
   Los animales medidos de un lote se juntan en un pesaje (promedio y, si se indica, el peso de cada arete) que se guarda
   como cualquier otro, marcado como hecho con cámara.
   Solo se activa en Rumentis Beta (config.js: beta:true). */
(function(){
'use strict';
const CFG=window.RUMENTIS||{};if(!CFG.beta||CFG.app!=='jefe'||!window.Vision)return;

/* ---------- los dos modos ----------
   k: kilos por litro del volumen medido. En personas es la densidad del cuerpo (~1.0 kg/L: el volumen ya descuenta la
   ropa); en ganado algo más que 1 porque las patas no entran en el volumen. Se ajusta con la báscula. */
const MODOS={
  persona:{clase:Vision.PERSONA,k:1.0,err:.03,alto:'estatura',
    buscar:'Párate frente a la cámara, de cuerpo completo',
    pasos:[{id:'frente',nombre:'De frente',instr:'De pie y derecho, con los brazos relajados y las manos un poco separadas de las piernas.',girar:'Ponte de frente, con las manos un poco separadas de las piernas',ang:{min:.24},dist:{eje:'alto',min:.55,max:.88}},
      {id:'costado',nombre:'De costado',instr:'De perfil completo, con los brazos relajados a los lados.',girar:'Gírate de costado, con los brazos a los lados',girarMas:'Gírate un poco más: de perfil completo',ang:{max:.3,rel:.75,perfil:.65,hombros:.3},dist:{eje:'alto',min:.55,max:.88},banda:[.18,.55]}]},
  ganado:{clase:Vision.VACA,k:1.1,err:.15,alto:'refAlto',ref:true,
    buscar:'Apunta al animal, que se vea completo',
    pasos:[{id:'costado',nombre:'De costado',instr:'El animal de costado; la persona de referencia de pie, a la par del animal.',girar:'Que el animal quede de costado',ang:{min:1.15},dist:{eje:'ancho',min:.5,max:.88}},
      {id:'atras',nombre:'Por detrás',instr:'El animal de espaldas a la cámara; la persona de referencia a su lado.',girar:'Que el animal quede de espaldas a la cámara',ang:{max:.9},dist:{eje:'alto',min:.45,max:.85},banda:[.05,.55]}]}
};
// la silueta mide un poco más que la estatura escrita (sin zapatos): suela y pelo
const EXTRA_CM=2.5;
// ropa: centímetros que se descuentan por lado al ancho y al fondo de cada rebanada
const ROPA={ajustada:{cm:.4,t:'Ajustada'},normal:{cm:.8,t:'Normal'},holgada:{cm:1.5,t:'Holgada'}};
/* forma de cada corte: área = f · ancho · fondo. Una elipse da π/4 ≈ 0.785; el tronco es más cuadrado (superelipse
   de exponente ~2.2: 0.81). Cabeza, brazos y piernas se toman elípticos. */
const FORMA={cabeza:Math.PI/4,tronco:.81,brazos:Math.PI/4,piernas:Math.PI/4};
const MKEY='rumentis-pc-modo',VERSION_MODELO=4;
UI.pc=UI.pc||{lote:'',items:[]};
if(!UI.pc.modo){let m=null;try{m=localStorage.getItem(MKEY);}catch(e){}UI.pc.modo=MODOS[m]?m:'persona';}
const PC=()=>S.config.pesoCam||{};
const guardarPC=cambios=>put('ajustes','finca',{...S.config,pesoCam:{...PC(),...cambios}});
const ropaSel=()=>ROPA[PC().ropa]?PC().ropa:'normal';

/* ---------- registro de mediciones (laboratorio) ----------
   Cada medición se guarda en S.config.pesoCam.reg (las últimas 300): volumen, peso estimado, medidas, calidad y
   rendimiento. Si se anota el peso de báscula (`real`), sirve para calibrar y para calcular el error. */
const REG=()=>PC().reg||[];
function registrar(r,stats){
  const id='m'+Date.now().toString(36)+Math.random().toString(36).slice(2,5),q=v=>v==null||!isFinite(v)?null:Math.round(v*100)/100;
  const rec={id,ts:Date.now(),f:hoy(),modo:r.modo,v:VERSION_MODELO,L:q(r.L),kg:q(r.kg),err:q(r.err),cv:q(r.cv),comb:r.comb,k:q(r.k),b:q(r.b),ncal:r.n,
    alto:r.alto,ropa:r.modo==='persona'?r.ropa:null,pred:q(r.pred),dims:r.dims?Object.fromEntries(Object.entries(r.dims).map(([a,b])=>[a,q(b)])):null,
    puntos:r.modo==='persona'?!!r.puntos:null,limpia:q(r.limpia),giro:q(r.giro),incompleta:r.modo==='persona'?!!r.incompleta:null,estimadas:r.estimadas||null,sexo:r.sexo||null,edad:r.edad||null,topes:r.topes||null,prof:q(r.prof),anchoF:q(r.anchoF),imc:q(r.imc),fondoAncho:q(r.fondoAncho),brazosPegados:!!r.brazosPegados,topadas:q(r.topadas),largo:q(r.largo),altoAnimal:q(r.altoAnimal),ancho:q(r.ancho),
    partes:r.partes?Object.fromEntries(Object.entries(r.partes).map(([a,b])=>[a,q(b)])):null,fuente:r.fuente,manual:r.manual,score:q(r.score),
    fps:q(stats&&stats.fps),msR:q(stats&&stats.msRapido),msP:q(stats&&stats.msPreciso),msPose:q(stats&&stats.msPose),seg:q(stats&&stats.seg),motor:stats&&stats.motor||'',real:null};
  guardarPC({reg:REG().concat(rec).slice(-300)});return id;
}
const actualizarReg=(id,cambios)=>guardarPC({reg:REG().map(x=>x.id===id?{...x,...cambios}:x)});

/* ---------- calibración con la báscula ----------
   kg = k · litros^b, ajuste robusto (una medición mala no arrastra el resultado):
   - 1 a 5 mediciones, o volúmenes muy parecidos (una sola persona): b = 1 y k = mediana de kg/L;
   - 6 o más con volúmenes variados (≥ 15 % de rango): Theil–Sen en logaritmos: b = mediana de las pendientes entre
     cada par (0.85–1.15) y ln k = mediana de ln kg − b · ln L.
   Los datos: mediciones del registro con peso de báscula (y las calibraciones de antes del registro, si las hay). */
const med=v=>{const s=v.slice().sort((a,b)=>a-b),n=s.length;return n?(n%2?s[(n-1)/2]:(s[n/2-1]+s[n/2])/2):0;};
// (personas: la base es el peso que da el modelo de ANSUR II, `pred` (v4); las mediciones de modelos anteriores no cuentan.
// Ganado: el volumen. En los dos, `L` es esa base.)
const baseDe=x=>x.modo==='persona'?(x.v>=4&&x.pred>0?x.pred:0):x.L;
const calModo=modo=>REG().filter(x=>x.modo===modo&&x.real>0&&baseDe(x)>0).map(x=>({L:baseDe(x),kg:x.real,id:x.id}))
  .concat(modo==='persona'?[]:(PC().cal||[]).filter(c=>c.modo===modo&&c.L>0&&c.kg>0));
function ajustar(C,modo){
  const n=C.length,M=MODOS[modo];if(!n)return {k:M.k,b:1,n:0};
  const X=C.map(z=>Math.log(z.L)),Y=C.map(z=>Math.log(z.kg)),rango=Math.max(...X)-Math.min(...X);let b=1;
  if(n>=6&&rango>=Math.log(1.15)){const P=[];for(let i=0;i<n;i++)for(let j=i+1;j<n;j++){const dx=X[j]-X[i];if(Math.abs(dx)>.02)P.push((Y[j]-Y[i])/dx);}
    if(P.length)b=Math.min(1.15,Math.max(.85,med(P)));}
  return {k:Math.exp(med(Y.map((y,i)=>y-b*X[i]))),b,n};
}
function modeloV(modo){
  const C=calModo(modo),a=ajustar(C,modo),n=C.length;
  if(!n)return {...a,err:MODOS[modo].err};
  const res=C.map(z=>Math.log(z.kg)-Math.log(a.k)-a.b*Math.log(z.L)),sd=n>1?Math.sqrt(res.reduce((s,r)=>s+r*r,0)/(n-1)):.08;
  return {...a,err:Math.max(.03,Math.min(.2,n<6?Math.max(sd,.05):sd))};
}

/* ---------- el volumen ----------
   La silueta (máscara G×G sobre la zona Z de la foto) en tramos por fila, en centímetros. */
function tramos(sil,cmpx){
  const G=sil.G,[x0,y0,x1,y1]=sil.caja,filas=[];
  for(let y=y0;y<y1;y++){const t=[];let a=-1;for(let x=x0;x<=x1;x++){const on=x<x1&&sil.mask[y*G+x]===1;if(on&&a<0)a=x;else if(!on&&a>=0){t.push([a-x0,x-x0]);a=-1;}}filas.push(t);}
  return {filas,cw:sil.cx*cmpx,ch:sil.cy*cmpx,cols:x1-x0};
}
const suma=t=>t.reduce((s,[a,b])=>s+b-a,0);
/* Persona: cada fila del cuerpo de frente es una rebanada; su corte tiene el ancho de frente y el fondo de costado a la
   misma altura (relativa a la estatura; mediana de 5 filas, así una fila mal recortada no pesa). Se descuenta la ropa
   (`ropa` cm por lado) y el área del corte es f · ancho · fondo según la parte del cuerpo (FORMA). Los tramos fuera del
   tronco y las piernas (los brazos) se toman redondos: su fondo es su propio ancho. Devuelve litros por parte. */
/* brazos pegados: si de frente casi no se ven separados del tronco (menos de 15 % de las filas entre hombros y
   caderas con brazo aparte), cada brazo se estima con la anatomía: ancho ≈ 5.2 % de la estatura; en esas filas se
   resta del tramo central y se cuenta redondo, como brazo. Si no, contarían como tronco (con su fondo) y el volumen
   saldría alto. */
const BRAZO=.052,H_HOMBRO=.18,H_CADERA=.52;
// topes del fondo de costado: veces el ancho de frente (piernas: por pierna) y fracción de la estatura. Son holgados
// (una persona muy robusta cabe): solo recortan lo que ningún cuerpo tiene.
const TOPE={cabeza:1.4,tronco:1.05,piernas:1.5},TOPE_ALTO={cabeza:.14,tronco:.24,piernas:.13};
function volPersona(F,S,ropa=0){
  const nF=F.filas.length,nS=S.filas.length,alto=nF*F.ch;let tx0=1e9,tx1=-1e9;
  for(let i=Math.floor(nF*.28);i<Math.floor(nF*.45);i++){const t=F.filas[i].reduce((m,z)=>!m||z[1]-z[0]>m[1]-m[0]?z:m,null);if(t){tx0=Math.min(tx0,t[0]);tx1=Math.max(tx1,t[1]);}}
  const hol=(tx1-tx0)*.12,P={cabeza:0,tronco:0,brazos:0,piernas:0},menos=v=>Math.max(0,v-2*ropa);
  // ¿se ven los brazos aparte? (filas entre hombros y caderas con algún tramo fuera del tronco)
  let sep=0,tot=0;for(let i=Math.floor(nF*H_HOMBRO);i<Math.floor(nF*H_CADERA);i++){tot++;if(F.filas[i].some(([a,b])=>!(b>tx0-hol&&a<tx1+hol)))sep++;}
  const pegados=tot>0&&sep/tot<.15,bw=BRAZO*alto;
  let filasT=0,topadas=0;
  for(let i=0;i<nF;i++){const h=(i+.5)/nF,js=Math.min(nS-1,Math.floor(h*nS)),ds=[],pierna=h>=H_CADERA;
    // de costado: en el tronco cuenta todo (el brazo pegado queda encima); en las piernas, solo el tramo más grande
    // (si una pierna va un poco adelante, de costado se ven dos y no hay que sumarlas)
    for(let j=Math.max(0,js-2);j<=Math.min(nS-1,js+2);j++)ds.push(pierna?Math.max(0,...S.filas[j].map(([a,b])=>b-a)):suma(S.filas[j]));
    const d=menos(med(ds)*S.cw);
    for(const [a,b] of F.filas[i]){let wd=menos((b-a)*F.cw);const central=b>tx0-hol&&a<tx1+hol;
      const parte=!central?'brazos':h<.13?'cabeza':h<H_CADERA?'tronco':'piernas';
      if(central&&pegados&&h>=H_HOMBRO&&h<H_CADERA){const quita=Math.min(2*bw,wd*.4);wd-=quita;P.brazos+=2*FORMA.brazos*(quita/2)**2*F.ch;}
      // tope anatómico: el fondo no pasa de TOPE[parte] veces el ancho de frente ni de una fracción de la estatura;
      // si pasa, la silueta de costado se pegó a algo (cama, suelo, mueble) y se recorta
      let dd=central?d:wd;
      if(central){filasT++;const t=Math.min(TOPE[parte]*wd,TOPE_ALTO[parte]*alto);if(dd>t){dd=t;topadas++;}}
      P[parte]+=FORMA[parte]*wd*dd*F.ch;}}
  for(const k in P)P[k]/=1000;P.pegados=pegados;P.topadas=filasT?topadas/filasT:0;return P;
}
/* Ganado: de costado, cada columna del cuerpo (sin las patas) es una rebanada a lo largo del animal; su corte tiene
   la forma que se ve por detrás, agrandada o achicada según el grueso del cuerpo en esa columna. */
// el cuerpo sin las patas, fila por fila: debajo de la fila más ancha las patas empiezan donde la silueta se parte en
// dos o más tramos (y sigue partida); de ahí para abajo solo cuentan los tramos anchos (la panza), no las patas
function cuerpo(T){
  const c=T.filas.map(suma),n=c.length,m=Math.max(...c),iw=c.indexOf(m);let fin=n;
  for(let i=iw;i<n;i++){let ok=true;for(let j=i;j<Math.min(n,i+4);j++)if(T.filas[j].length<2){ok=false;break;}if(ok){fin=i;break;}}
  return T.filas.map((t,i)=>i<fin?t:t.filter(([a,b])=>b-a>=.25*m));
}
function volGanado(L,A){
  let SA=0,TA=0;cuerpo(A).forEach(t=>{const v=suma(t);if(v){SA+=v*A.cw*A.ch;TA+=A.ch;}});
  const col=new Float32Array(L.cols+1);
  cuerpo(L).forEach(t=>{for(const [a,b] of t)for(let x=a;x<b;x++)col[x]+=L.ch;});
  let V=0;for(let x=0;x<col.length;x++)if(col[x]>0)V+=SA*(col[x]/TA)**2*L.cw;
  return {cuerpo:V/1000};
}
/* ---------- personas: modelo v4, entrenado con ANSUR II ----------
   ANSUR II (encuesta antropométrica del ejército de EE. UU., 2012; dominio público): 6,068 personas pesadas en báscula
   y medidas a mano. Con las medidas que se pueden sacar de las dos siluetas, la estatura, el sexo y la edad se ajustó
   una ley de potencias (modelo/peso/ajustar_peso.py):
     ln peso = c0 + c1 ln S + c2 ln hombros + c3 ln ancho pecho + c4 ln fondo pecho + c5 ln ancho cintura
               + c6 ln fondo cintura + c7 ln ancho cadera + c8 ln fondo glúteos + c9 ln contorno muslo
               + c10 ln contorno pantorrilla + c11 ln contorno cuello + c12 ln contorno muslo sobre la rodilla
               + c13 sexo + c14 edad/50                                                            (medidas en mm)
   Validación cruzada (10 partes): error medio 1.6 % con medidas exactas y 2.2 % con 3 % de ruido en cada medida (el de
   una foto); sin sesgo (el modelo v3, sin pantorrilla, cuello, muslo bajo, sexo ni edad: 2.5 y 3.1 %). Si una de las
   tres medidas nuevas no se puede tomar (o sale fuera de lo humano), se estima con las demás (`imputar`, también de
   ANSUR II): sin ninguna de las tres, 2.9 %. Sin edad se usa la mediana de ANSUR II (28 años); sin sexo, 0.5.
   Cada medida se toma a su altura en ANSUR II (fracción de la estatura desde la coronilla) y los puntos del cuerpo
   dicen dónde están los brazos y las manos para no contarlos como tronco. */
const ANSUR={coef:[-14.11171, 0.94984, 0.23983, 0.09156, 0.22046, 0.08211, 0.13294, 0.08079, 0.13794, 0.28047, 0.23176, 0.2196, 0.20926, 0.02983, -0.00288],claves:['S','bid','cb','cd','wb','wd','hb','bd','th','cf','ne','lt','sexo','edad'],
  imputar:{cf:[0.50562, 0.08034, 0.09561, 0.10028, 0.07365, -0.07895, -0.00491, 0.08448, 0.03307, 0.47929, 0.02335, -0.01213],ne:[2.0137, 0.02839, 0.31269, 0.10744, 0.11736, -0.03637, 0.08766, -0.09171, 0.08116, 0.03746, 0.12058, 0.01711],lt:[0.03264, 0.11367, 0.02169, 0.04515, 0.0207, -0.10182, 0.04103, 0.15727, 0.04804, 0.5911, 0.00363, -0.00147]},
  niveles:{hombro:.1798,pecho:.269,cintura:.3983,cadera:.4857,entrepierna:.5193},
  limites:{bid:[0.2107,0.3836],cb:[0.1216,0.2228],cd:[0.0949,0.2239],wb:[0.1236,0.2749],wd:[0.0825,0.2301],hb:[0.1455,0.2926],bd:[0.0924,0.2104],th:[0.2399,0.5357],cf:[0.1601,0.3126],ne:[0.1589,0.3104],lt:[0.1668,0.353]},
  n:6068,mape:0.022,edad:28};
const BASE_IMP=['S','bid','cb','cd','wb','wd','hb','bd','th'];
const lnPeso=x=>ANSUR.coef[0]+ANSUR.claves.reduce((t,k,i)=>t+ANSUR.coef[i+1]*(k==='sexo'?x.sexo:k==='edad'?x.edad/50:Math.log(x[k]*10)),0);
function imputar(k,x){const c=ANSUR.imputar[k];let v=c[0];BASE_IMP.forEach((b,i)=>{v+=c[i+1]*Math.log(x[b]*10);});return Math.exp(v+c[10]*x.sexo+c[11]*x.edad/50)/10;}
// los puntos del cuerpo de una toma en celdas de sus tramos (x desde el borde izquierdo de la caja, y desde arriba)
function kpT(t){const s=t.sil,P=t.kp;if(!P||!s||!s.Z||!s.caja)return null;const [x0,y0]=s.caja,o=[];
  for(let i=0;i<P.length;i+=3)o.push([(P[i]-s.Z.x)/s.cx-x0,(P[i+1]-s.Z.y)/s.cy-y0,P[i+2]]);return o;}
const kOk=(K,i)=>!!(K&&K[i]&&K[i][2]>=.3);
// x (celdas) donde el tramo a–b del esqueleto cruza la altura y
function xEn(K,a,b,y){if(!kOk(K,a)||!kOk(K,b))return null;const A=K[a],B=K[b],lo=Math.min(A[1],B[1]),hi=Math.max(A[1],B[1]);
  if(y<lo||y>hi||hi-lo<1e-6)return null;return A[0]+(y-A[1])/(B[1]-A[1])*(B[0]-A[0]);}
const medio=(K,i,j)=>kOk(K,i)&&kOk(K,j)?[(K[i][0]+K[j][0])/2,(K[i][1]+K[j][1])/2]:kOk(K,i)?K[i]:kOk(K,j)?K[j]:null;
// el centro del cuerpo a la altura y: del medio de los hombros al de las caderas
function centroEn(K,y){const h=medio(K,5,6),c=medio(K,11,12);if(!h||!c)return null;if(y<=h[1])return h[0];if(y>=c[1])return c[0];return h[0]+(y-h[1])/(c[1]-h[1])*(c[0]-h[0]);}
// brazos y manos: tramos del esqueleto (hombro–codo, codo–muñeca, muñeca–punta del dedo medio) y su medio ancho
// (fracción de la estatura)
const BRAZOS=[[5,7,.029],[7,9,.025],[9,103,.02],[6,8,.029],[8,10,.025],[10,124,.02]],MANOS=BRAZOS.filter(z=>z[2]===.02);
// el tramo de la fila que tiene el centro del cuerpo (o el más cercano; sin puntos, el más ancho)
function tramoCentro(fila,c){if(!fila.length)return null;if(c==null)return fila.reduce((m,z)=>!m||z[1]-z[0]>m[1]-m[0]?z:m,null);
  let m=null,md=1e9;for(const z of fila){if(z[0]<=c&&c<=z[1])return z;const d=Math.min(Math.abs(z[0]-c),Math.abs(z[1]-c));if(d<md){md=d;m=z;}}return m;}
/* quita de [a,b] (cm) un brazo o una mano pegados al borde del tramo z. Solo si el centro del brazo cae dentro del
   tramo y cerca del borde (a menos de 1.6 medios anchos: el brazo es parte del tramo); si junto al brazo hay otro
   tramo, el brazo va separado y no se corta; si va por delante del cuerpo (lejos del borde), tampoco. */
function cortar(a,b,fila,z,K,y,cw,alto,segs){
  if(!K)return [a,b];
  for(const [i,j,f] of segs){const x=xEn(K,i,j,y);if(x==null)continue;const xc=x*cw,hw=f*alto;if(xc<=a||xc>=b)continue;
    if(fila.some(t=>t!==z&&Math.abs((t[0]+t[1])/2*cw-xc)<2.5*hw))continue;
    if(xc-a<1.6*hw)a=Math.max(a,xc+hw);else if(b-xc<1.6*hw)b=Math.min(b,xc-hw);}
  return [a,b];}
/* dónde está la estatura en la silueta: la coronilla (celda `top`) y la estatura en celdas (H). Sin puntos, la silueta
   entera (menos suela y pelo). Con puntos, si de la oreja a los pies el cuerpo es bastante más largo que la silueta (la silueta
   no llegó a los pies o a la cabeza), manda el de los puntos y la escala se corrige (`k`): así una silueta incompleta
   no agranda todas las medidas. ANSUR II: la oreja está a 7.6 % de la estatura bajo la coronilla; el tobillo, a 4.1 %
   del suelo; el talón y los dedos, a ~0.5 %. */
function marco(T,K,alto,ref){
  const n=T.filas.length,Hs=n*alto/ref,base={top:0,H:Hs,k:1,incompleta:false,K};if(!K)return base;
  const orejas=[3,4].filter(i=>kOk(K,i)).map(i=>K[i][1]);if(!orejas.length)return base;
  const yo=orejas.reduce((a,b)=>a+b,0)/orejas.length;
  const pies=[[19,.005],[22,.005],[17,.005],[20,.005],[15,.0406],[16,.0406]].filter(([i])=>kOk(K,i)).map(([i,f])=>(K[i][1]-yo)/(1-.0756-f));
  if(!pies.length)return base;const Hk=Math.max(...pies);
  // (los puntos suelen dar un poco menos que la estatura real: 95 % de las veces menos de 1.03 veces; por eso solo si
  // pasan de 1.08 veces la silueta, que entonces sí está incompleta)
  if(Hk>Hs*1.08)return {top:yo-.0756*Hk,H:Hk,k:Hs/Hk,incompleta:true,K};
  return base;}
/* la fila (celda) de una altura del cuerpo (fracción de la estatura desde la coronilla). Por la silueta: coronilla +
   h · estatura. Por los puntos: entre los hombros (a 22 % en personas de pie, COCO), las caderas (51.5 %) y las
   rodillas (71.4 %). Si las dos dicen casi lo mismo (menos de 4 % de la estatura), el promedio; si no, la silueta tiene
   algo pegado arriba o abajo (o la persona no está derecha) y mandan los puntos. */
const ANCLAS=[[.22,5,6],[.515,11,12],[.714,13,14]];
function nivel(M,h){
  const ys=M.top+h*M.H,K=M.K;if(!K)return ys;
  const A=ANCLAS.map(([f,i,j])=>{const m=medio(K,i,j);return m&&kOk(K,i)&&kOk(K,j)?[f,m[1]]:null;}).filter(Boolean);
  if(A.length<2||A[0][0]!==.22||A[1][0]!==.515)return ys;
  let a=A[0],b=A[1];if(h>b[0]&&A[2]){a=A[1];b=A[2];}
  const yk=a[1]+(h-a[0])/(b[0]-a[0])*(b[1]-a[1]);if(!(b[1]>a[1]))return ys;
  return Math.abs(yk-ys)>.04*M.H?yk:(yk+ys)/2;}
// valores por fila entre dos alturas (fracciones de la estatura, desde la coronilla)
function banda(T,M,h0,h1,fn){const n=T.filas.length,v=[],y0=nivel(M,h0),y1=nivel(M,h1);
  for(let i=Math.max(0,Math.floor(y0));i<=Math.min(n-1,Math.ceil(y1));i++){const r=fn(T.filas[i],i+.5);if(r>0)v.push(r);}return v;}
const pctl=(v,p)=>{const s=v.slice().sort((a,b)=>a-b);return s.length?s[Math.min(s.length-1,Math.floor(p*(s.length-1)+.5))]:0;};
// piernas de frente: el tramo de cada pierna (el tramo del esqueleto de cada una: cadera–rodilla en el muslo,
// rodilla–tobillo en la pantorrilla), sin la mano; si las piernas están juntas, se parte a la mitad entre las dos.
// Devuelve [ancho de una pierna, distancia entre los centros de las dos]
function piernas(fila,y,K,cw,alto,sL=[11,13],sR=[12,14]){
  const l=K?xEn(K,sL[0],sL[1],y):null,r=K?xEn(K,sR[0],sR[1],y):null;
  if(l==null||r==null){const t=fila.map(([a,b])=>(b-a)*cw).sort((a,b)=>b-a);return [t.length>=2?(t[0]+t[1])/2:t.length?t[0]/2:0,0];}
  const ws=[];for(const [c,o] of [[l,r],[r,l]]){const z=tramoCentro(fila,c);if(!z)continue;let a=z[0]*cw,b=z[1]*cw;const cc=c*cw,oc=o*cw;
    if(z[0]<=o&&o<=z[1]){const m=(cc+oc)/2;if(cc<oc)b=Math.min(b,m);else a=Math.max(a,m);}
    [a,b]=cortar(a,b,fila,z,K,y,cw,alto,MANOS);ws.push(b-a);}
  return [ws.length?ws.reduce((x,y)=>x+y,0)/ws.length:0,Math.abs(l-r)*cw];}
// fondo de una pierna de perfil: el tramo donde va el esqueleto de la pierna (sin la mano)
function fondoPierna(fila,y,K,cw,alto,sL,sR){const l=K?xEn(K,sL[0],sL[1],y)??xEn(K,sR[0],sR[1],y):null,z=tramoCentro(fila,l);if(!z)return 0;
  const [a,b]=cortar(z[0]*cw,z[1]*cw,fila,z,K,y,cw,alto,MANOS);return b-a;}
// contorno de una elipse con ejes a y b (Ramanujan)
const elipse=(a,b)=>{a/=2;b/=2;return Math.PI*(3*(a+b)-Math.sqrt((3*a+b)*(a+3*b)));};
/* giro de la toma de perfil (seno del ángulo): la separación de los hombros y de las caderas de perfil contra la de
   frente. De perfil exacto es 0; girado θ, se ve sen θ de la separación. null sin puntos. */
function giroPerfil(KF,KS,cwF,cwS){
  if(!KF||!KS)return null;const r=[];
  for(const [i,j] of [[5,6],[11,12]])if(kOk(KF,i)&&kOk(KF,j)&&kOk(KS,i)&&kOk(KS,j)){const f=Math.abs(KF[i][0]-KF[j][0])*cwF,s=Math.abs(KS[i][0]-KS[j][0])*cwS;if(f>5)r.push(s/f);}
  return r.length?Math.min(.6,med(r)):null;}
/* las medidas en cm de un par de tomas (frente F y perfil S, con sus puntos KF y KS), sin la ropa, y el peso del
   modelo. `alto`: la estatura (cm), `ref`: estatura + suela y pelo (el alto de la silueta).
   - Brazos y manos: se quitan solo si van pegados al borde del tronco (con los puntos).
   - Perfil girado: un corte ovalado de ancho w y fondo d, girado θ, se ve de sqrt(d² cos² θ + w² sen² θ); con el ancho
     de frente y θ (de los puntos) se recupera el fondo. Las piernas de perfil se ven separadas por sen θ veces la
     distancia entre ellas: se descuenta, y el fondo del muslo no pasa de 1.3 veces su ancho. */
function medidasPersona(F,S,KF,KS,alto,ref,ropa,per={}){
  const N=ANSUR.niveles,med5=v=>v.length?med(v):0,menos=v=>Math.max(0,v-2*ropa);
  const MF=marco(F,KF,alto,ref),MS=marco(S,KS,alto,ref),cwF=F.cw*MF.k,cwS=S.cw*MS.k;
  const cF=y=>KF?centroEn(KF,y):null,cS=y=>KS?centroEn(KS,y):null;
  const anchoT=(fila,y)=>{const c=cF(y),z=tramoCentro(fila,c);if(!z)return 0;const [a,b]=cortar(z[0]*cwF,z[1]*cwF,fila,z,KF,y,cwF,alto,BRAZOS);return b-a;};
  const fondoT=(fila,y)=>{const c=cS(y),z=tramoCentro(fila,c);if(!z)return 0;const [a,b]=cortar(z[0]*cwS,z[1]*cwS,fila,z,KS,y,cwS,alto,BRAZOS);return b-a;};
  const B=(T,M,h0,h1,fn)=>banda(T,M,h0,h1,fn);
  const d={
    bid:pctl(B(F,MF,N.hombro+.01,N.hombro+.08,(fila,y)=>{const z=tramoCentro(fila,cF(y));return z?(z[1]-z[0])*cwF:0;}),.9),
    cb:med5(B(F,MF,N.pecho-.012,N.pecho+.012,anchoT)),cd:med5(B(S,MS,N.pecho-.012,N.pecho+.012,fondoT)),
    wb:med5(B(F,MF,N.cintura-.012,N.cintura+.012,anchoT)),wd:med5(B(S,MS,N.cintura-.012,N.cintura+.012,fondoT)),
    hb:pctl(B(F,MF,N.cadera-.03,N.cadera+.03,anchoT),.9),bd:pctl(B(S,MS,N.cadera-.03,N.cadera+.03,fondoT),.9)};
  // (los hombros de frente no pueden ser más angostos que la distancia entre las articulaciones de los hombros, con
  // los deltoides: si la silueta da menos, la banda cayó fuera de los hombros y se toma 1.4 veces esa distancia)
  if(KF&&kOk(KF,5)&&kOk(KF,6)){const sd=Math.abs(KF[5][0]-KF[6][0])*cwF;if(sd>10&&d.bid<1.1*sd)d.bid=1.4*sd;}
  // el giro del perfil: se recuperan los fondos
  const sg=giroPerfil(KF,KS,cwF,cwS),giro=sg==null?0:sg,cg=Math.sqrt(1-giro*giro);
  const fondo=(D,w)=>giro>.05&&w>0?Math.sqrt(Math.max(D*D-w*w*giro*giro,(.5*D)**2))/cg:D;
  d.cd=fondo(d.cd,d.cb);d.wd=fondo(d.wd,d.wb);d.bd=fondo(d.bd,d.hb);
  for(const k in d)d[k]=menos(d[k]);
  /* piernas: ancho de una pierna de frente y fondo de perfil entre dos alturas (p: percentil de las filas, 0 = mediana),
     sin la ropa; el fondo, corregido por el giro (las piernas se ven separadas) y a lo más `tope` veces el ancho */
  const pierna=(h0,h1,sL,sR,p,tope)=>{const U=[];B(F,MF,h0,h1,(fila,y)=>{const m=piernas(fila,y,KF,cwF,alto,sL,sR);if(m[0]>0)U.push(m);return 0;});
    const w=p?pctl(U.map(m=>m[0]),p):med5(U.map(m=>m[0])),sep=med5(U.map(m=>m[1]));
    const V=B(S,MS,h0,h1,(fila,y)=>fondoPierna(fila,y,KS,cwS,alto,sL,sR));let D=p?pctl(V,p):med5(V);
    if(giro>.05)D=Math.max(.5*D,D-sep*giro);if(w>0)D=Math.min(D,tope*w);return w>0&&D>0?elipse(menos(w),menos(D)):0;};
  d.th=pierna(N.entrepierna+.015,N.entrepierna+.04,[11,13],[12,14],0,1.3);
  // muslo sobre la rodilla, pantorrilla (lo más ancho bajo la rodilla) y cuello (lo más angosto entre la barbilla y
  // el esternón, de frente y de perfil; sin descontar ropa)
  const d2={lt:pierna(.655,.69,[11,13],[12,14],0,1.35),cf:pierna(.745,.81,[13,15],[14,16],.9,1.5)};
  const nw=pctl(B(F,MF,.135,.18,(fila,y)=>{const z=tramoCentro(fila,cF(y));return z?(z[1]-z[0])*cwF:0;}),.1);
  const nd=pctl(B(S,MS,.135,.18,(fila,y)=>{const z=tramoCentro(fila,cS(y));return z?(z[1]-z[0])*cwS:0;}),.1);
  d2.ne=nw>0&&nd>0?elipse(nw,nd):0;
  // fuera de lo que tiene una persona (percentiles 0.1 y 99.9 de ANSUR II, con holgura): se lleva al límite y se avisa
  const topes=[];for(const k in d){const [lo,hi]=ANSUR.limites[k];const v=d[k]/alto;if(!(v>=lo)){d[k]=lo*alto;topes.push(k);}else if(v>hi){d[k]=hi*alto;topes.push(k);}}
  const x={S:alto,...d,sexo:per.sexo==='h'?1:per.sexo==='m'?0:.5,edad:per.edad>=15&&per.edad<=90?per.edad:ANSUR.edad};
  // las tres nuevas: si no se pudieron tomar o salen fuera de lo humano, se estiman con las demás (ANSUR II)
  const estimadas=[];for(const k of ['cf','ne','lt']){const [lo,hi]=ANSUR.limites[k],v=d2[k]/alto;if(v>=lo&&v<=hi)x[k]=d[k]=d2[k];else{x[k]=d[k]=imputar(k,x);estimadas.push(k);}}
  return {d,pred:Math.exp(lnPeso(x)),topes,estimadas,giro:Math.asin(giro)*180/Math.PI,incompleta:MF.incompleta||MS.incompleta};
}

/* dos ángulos (cada uno con 1 o 2 fotos) → centímetros con la estatura conocida → volumen → peso.
   Se calcula el volumen con cada par de fotos (frente × costado) y se promedia; su dispersión (cv) dice qué tan de
   acuerdo estuvieron las fotos entre sí. */
function combinar(modo,caps,alto,ropaK,per={}){
  const [p,s]=caps,m=modeloV(modo),ref=alto+EXTRA_CM,ropa=modo==='persona'?ROPA[ropaK||'normal'].cm:0;
  const tomas=c=>c.tomas&&c.tomas.length?c.tomas:[{med:c.med,sil:c.sil}];
  // cm por píxel de cada foto: la persona medida (personas) o la de referencia (ganado)
  const cmpx=t=>ref/(modo==='persona'?t.med.alto:t.med.refAlto);
  const conK=t=>({t,k:cmpx(t),T:tramos(t.sil,cmpx(t)),K:modo==='persona'?kpT(t):null});
  const TP=tomas(p).map(conK),TS=tomas(s).map(conK);
  const pares=[];
  let pegados=false;const topes=[];
  for(const a of TP)for(const b of TS){const P=modo==='persona'?volPersona(a.T,b.T,ropa):volGanado(a.T,b.T);if(P.pegados)pegados=true;delete P.pegados;topes.push(P.topadas||0);delete P.topadas;
    const x={P,L:Object.values(P).reduce((x,y)=>x+y,0),a,b};if(modo==='persona')x.M=medidasPersona(a.T,b.T,a.K,b.K,alto,ref,ropa,per);pares.push(x);}
  const Ls=pares.map(x=>x.L),L=Ls.reduce((x,y)=>x+y,0)/Ls.length;
  // personas: la base del peso es el modelo de ANSUR II; ganado: el volumen. Su dispersión entre pares de fotos (cv)
  const B=modo==='persona'?pares.map(x=>x.M.pred):Ls,base=B.reduce((x,y)=>x+y,0)/B.length,sd=B.length>1?Math.sqrt(B.reduce((x,y)=>x+(y-base)**2,0)/(B.length-1)):0,cv=base>0?sd/base:0;
  const partes={};for(const x of pares)for(const k in x.P)partes[k]=(partes[k]||0)+x.P[k]/pares.length;
  // el rango: el error del modelo (calibración) y el desacuerdo entre fotos, sumados en cuadratura
  const err=Math.min(.3,Math.sqrt(m.err**2+cv**2));
  const r={modo,L,partes,cv,comb:pares.length,brazosPegados:pegados,topadas:topes.length?topes.reduce((x,y)=>x+y,0)/topes.length:0,kg:m.k*Math.pow(base,m.b),k:m.k,b:m.b,err,n:m.n,alto,ropa:modo==='persona'?(ropaK||'normal'):null,
    score:Math.min(p.med.score,s.med.score),manual:caps.some(c=>c.manual),fuente:caps.every(c=>c.fuente==='preciso')?'preciso':'rapido'};
  const prom=f=>{const v=pares.map(f).filter(isFinite);return v.length?v.reduce((x,y)=>x+y,0)/v.length:NaN;};
  if(modo==='persona'){
    // las medidas del modelo (promedio de los pares), el ancho de hombros y el fondo del pecho para la pantalla
    r.pred=base;r.dims={};for(const k of Object.keys(pares[0].M.d))r.dims[k]=prom(x=>x.M.d[k]);
    r.topes=[...new Set(pares.flatMap(x=>x.M.topes))];r.puntos=pares.every(x=>x.a.K&&x.b.K);
    r.giro=prom(x=>x.M.giro);r.incompleta=pares.some(x=>x.M.incompleta);r.estimadas=[...new Set(pares.flatMap(x=>x.M.estimadas))];
    r.sexo=per.sexo||null;r.edad=per.edad||null;
    r.limpia=prom(x=>Math.max(x.a.t.sil.limpia||0,x.b.t.sil.limpia||0));
    r.prof=r.dims.cd;r.anchoF=r.dims.bid;r.imc=r.kg/((alto/100)**2);
    // fondo del pecho contra su ancho: en una persona va de ~0.55 a ~0.95 (ANSUR II); si pasa de 1.1, la toma de
    // costado no era de perfil
    r.fondoAncho=r.dims.cb>0?r.dims.cd/r.dims.cb:NaN;
    if(!r.puntos)r.aviso='No se vieron bien los puntos del cuerpo en una foto: el peso puede ser menos exacto. Repite con buena luz y el cuerpo completo a la vista.';
    else if(r.topes.length)r.aviso='Una medida salió fuera de lo que tiene una persona y se ajustó: revisa que las dos tomas sean de frente y de perfil completo, con el cuerpo entero a la vista y sin nada pegado.';
    else if(r.fondoAncho>1.1)r.aviso='La toma de costado no parece de perfil completo (el pecho se ve muy ancho de lado): el peso puede salir alto. Repite girando bien de costado.';
    else if(r.imc>40||r.imc<15)r.aviso='El resultado sale fuera de lo normal para tu estatura: revisa que las dos tomas sean de frente y de perfil completo, con el cuerpo entero a la vista.';
  }else{r.largo=prom(x=>x.a.t.med.ancho*x.a.k);r.altoAnimal=prom(x=>x.a.t.med.alto*x.a.k);r.ancho=prom(x=>x.b.t.med.banda*x.b.k);
    // de costado y por detrás el animal tiene la misma altura: si no, la persona no estaba a la par
    const a2=prom(x=>x.b.t.med.alto*x.b.k);if(Math.abs(r.altoAnimal-a2)/((r.altoAnimal+a2)/2)>.12)r.aviso='Las dos tomas dieron alturas distintas del animal: que la persona de referencia se pare a la par del animal, ni adelante ni atrás.';}
  if(!r.aviso&&cv>.06)r.aviso='Las fotos de cada ángulo no coincidieron del todo: quédate más quieto o mejora la luz.';
  if(!r.aviso&&r.fuente!=='preciso')r.aviso='Una foto se midió solo con el modelo rápido: el peso puede ser menos exacto.';
  if(!r.aviso&&r.manual)r.aviso='Una foto se tomó a mano: revisa que la silueta cubra bien el cuerpo.';
  return r;
}

/* ---------- la pantalla ---------- */
const lotesAct=()=>calc().act;
const loteSel=()=>{const L=lotesAct();if(!L.find(x=>x.id===UI.pc.lote))UI.pc.lote=L.length?L[0].id:'';return calc().L[UI.pc.lote];};
const cm=v=>`${nf(v)} cm`;
function tarjetaCal(modo){
  const cal=calModo(modo),m=modeloV(modo),per=modo==='persona';
  return `<section class="sec">${secH(per?'Calibración con tu báscula':'Calibración con la báscula',cal.length||'')}<div class="card pad pc-cal">
    <p>${!cal.length?(per?'Sin calibrar: el peso es un estimado general. Pésate en una báscula, mídete con la cámara y escribe tu peso: la app aprende.':'Sin calibrar: el peso es un estimado general. Mide animales recién pesados en la báscula y escribe su peso real: la app aprende de tus animales.')
      :`Calibrada con ${pl(cal.length,'medición','mediciones')}. Error típico: ±${Math.round(m.err*100)} %.`}</p>
    <button type="button" class="btn full" data-act="pcCalibrar">${icono('pesaje','i3')}${per?'Calibrar con mi peso':'Calibrar con un animal pesado'}</button>
    ${cal.length?`<p class="hint" style="margin:0">k = ${per?nf(m.k,3):`${nf(W(m.k),3)} ${UW()}/L`} · b = ${nf(m.b,2)}. Cada medición con peso de báscula está en el laboratorio, abajo.</p>
      <button type="button" class="lnk" data-act="pcBorrarCal">Borrar la calibración</button>`:''}</div></section>`;
}
const SEXOS={h:'Hombre',m:'Mujer'};
const filaAlto=(per,a)=>{const c=PC(),extra=per&&a?[SEXOS[c.sexo]?`<span>${SEXOS[c.sexo]}</span>`:'',c.edad?`<span>${c.edad} años</span>`:''].filter(Boolean).join(' · '):'';
  return `<div class="pc-alto"><span>${per?'Tu estatura':'Estatura de la persona de referencia'}</span><b>${a?cm(a):'—'}${extra?` <small class="pc-alto-x">· ${extra}</small>`:''}</b><button type="button" class="lnk" data-act="pcAlto">${a?'Cambiar':'Escribir'}</button></div>`;};
PAGES.pesocam=()=>{
  const modo=UI.pc.modo,per=modo==='persona',x=loteSel(),it=UI.pc.items,a=PC()[MODOS[modo].alto];
  const prom=it.length?it.reduce((s,r)=>s+r.kg,0)/it.length:0;
  return `<header class="hd">${hdBack('#hoy','Hoy')}<div class="ttl" style="margin-top:-6px"><span class="eyebrow pc-beta">Beta</span><h1>Peso con cámara</h1><p class="sub">${per?'Prueba la medición contigo: la cámara te toma de frente y de costado y estima tu peso.':'La cámara toma al animal de costado y por detrás, con una persona de pie a su lado como referencia, y estima su peso.'} Sin nada que imprimir. Todo se calcula aquí, sin internet.</p></div></header>
  <main class="bd">
   <div class="seg pc-modo" role="group" aria-label="Qué medir">${[['persona','Personas'],['ganado','Ganado']].map(([v,t])=>`<button type="button" data-act="pcModo" data-m="${v}" aria-pressed="${modo===v}">${t}</button>`).join('')}</div>
   ${per?`<section class="sec"><div class="card pad pc-vivo">${filaAlto(true,a)}
       <div class="pc-ropa"><span>Ropa</span><div class="seg" role="group" aria-label="Ropa">${Object.entries(ROPA).map(([v,o])=>`<button type="button" data-act="pcRopa" data-v="${v}" aria-pressed="${ropaSel()===v}">${o.t}</button>`).join('')}</div></div><p>Dos tomas: de frente y de costado. Cuando la silueta se pone verde, la foto se toma sola.</p>
       <button type="button" class="btn pri full pc-foto" data-act="pcVivo">${icono('camara','i3')}Medir con la cámara</button></div></section>`
     :lotesAct().length?`<section class="sec">${secH('Lote')}<div class="card pad pc-vivo"><select class="in" id="pcLote" data-pclote>${lotesAct().map(l=>`<option value="${esc(l.id)}"${l.id===UI.pc.lote?' selected':''}>${esc(l.l.nombre)} · ${pl(l.cab,'cabeza','cabezas')}</option>`).join('')}</select>
       ${filaAlto(false,a)}<button type="button" class="btn pri full pc-foto" data-act="pcVivo">${icono('camara','i3')}Medir con la cámara</button></div></section>`
     :`<div class="card pad"><p class="empty">Primero crea un lote.</p></div>`}
   ${!per&&it.length?`<section class="sec">${secH('Pesaje en curso',it.length)}<div class="card pad pc-sesion"><div class="pc-prom"><b>${wtxt(prom)}</b><span>promedio de ${pl(it.length,'animal','animales')} en ${esc(x?x.l.nombre:'')}</span></div>
     <div class="rows">${it.map((r,i)=>`<div class="row"><img class="pc-th" src="${r.img}" alt=""><div class="tx"><b>${wtxt(r.kg)}</b><span>${r.arete?`<span>Arete</span> <span data-no-tr>${esc(r.arete)}</span>`:'<span>Sin arete</span>'} · <span>±${Math.round(r.err*100)} %</span></span></div><button type="button" class="del" data-act="pcQuitar" data-i="${i}" aria-label="Quitar">${ico('x')}</button></div>`).join('')}</div>
     <div class="pc-acts"><button type="button" class="btn" data-act="pcVivo">${icono('camara','i3')}Otro animal</button><button type="button" class="btn pri" data-act="pcGuardar">Guardar pesaje</button></div></div></section>`:''}
   <section class="sec">${secH('Cómo hacerlo')}<div class="card pad pc-como">
     <ol class="pc-pasos">${per?`<li>Apoya el teléfono a la altura de la cintura, a unos 3 metros (o que alguien lo sostenga), y usa la cámara frontal si estás solo.</li><li>Detrás de ti, mejor una pared lisa: sin cama, sillón ni muebles pegados a tu cuerpo.</li><li>Párate derecho con el cuerpo completo a la vista: primero de frente, con los brazos relajados y las manos un poco separadas de las piernas; después de perfil completo (hombro hacia la cámara), con los brazos a los lados.</li><li>Ropa ajustada y sin zapatos ni gorra, si puedes: la silueta mide mejor tu cuerpo.</li><li>Quédate quieto cuando la silueta esté verde: la foto se toma sola.</li>`
       :`<li>Una persona se para derecha a la par del animal, sin taparlo. Su estatura es la medida de referencia.</li><li>Primero con el animal de costado; después por detrás. Aléjate hasta que el animal y la persona se vean completos.</li><li>Sostén el teléfono firme: cuando la silueta esté verde, la foto se toma sola.</li>`}</ol></div></section>
   ${tarjetaCal(modo)}
   ${window.PcLab?PcLab.html(modo):''}
   <p class="hint pc-nota">${per?'Beta: sirve para probar la medición. El peso de una persona con cámara es un estimado.':'Beta: compara con la báscula antes de decidir ventas o raciones con este peso.'}</p>
  </main>`;
};
document.addEventListener('change',e=>{const t=e.target;if(t&&t.matches&&t.matches('[data-pclote]')){if(UI.pc.items.length&&t.value!==UI.pc.lote){UI.pc.items=[];toast('Pesaje en curso descartado: era de otro lote.',3500);}UI.pc.lote=t.value;render();}});
ACTS.pcRopa=el=>{if(ROPA[el.dataset.v]){guardarPC({ropa:el.dataset.v});}};
ACTS.pcModo=el=>{const m=el.dataset.m;if(!MODOS[m]||m===UI.pc.modo)return;UI.pc.modo=m;try{localStorage.setItem(MKEY,m);}catch(e){}render();};

/* ---------- la estatura de referencia ---------- */
let TRAS=null;   // qué hacer después de guardarla (medir o calibrar)
function formAlto(tras){
  const per=UI.pc.modo==='persona',a=PC()[MODOS[UI.pc.modo].alto];TRAS=tras||null;
  openSheet(shHead(per?'Tu estatura':'Persona de referencia','Peso con cámara')+formWrap('pcAlto',`
    <p class="hint">${per?'Con tu estatura la cámara sabe cuánto mide cada parte de tu cuerpo. Escríbela una vez, sin zapatos. El sexo y la edad afinan el cálculo del peso.':'Con la estatura de quien se para junto al animal, la cámara sabe cuánto mide el animal. Escríbela una vez.'}</p>
    ${q(per?'Estatura':'Estatura de la persona',inp('alto',a?String(Math.round(a)):'',{unit:'cm',xl:true,ph:'170'}))}
    ${per?q('Sexo',`<select class="in" name="sexo"><option value="">Elige</option>${Object.entries(SEXOS).map(([v,t])=>`<option value="${v}"${PC().sexo===v?' selected':''}>${t}</option>`).join('')}</select>`)
      +q('Edad (opcional)',inp('edad',PC().edad?String(PC().edad):'',{unit:'años',mode:'numeric',ph:'30'})):''}`,foot('Guardar')));
}
ACTS.pcAlto=()=>formAlto(null);
SAVE.pcAlto=f=>{const v=num(fv(f,'alto'));if(!(v>=100&&v<=230))return ferr(f,'Escribe la estatura en centímetros (por ejemplo, 170).');
  const cambios={[MODOS[UI.pc.modo].alto]:Math.round(v)};
  if(UI.pc.modo==='persona'){const sx=fv(f,'sexo'),ed=fv(f,'edad').trim(),e=num(ed);if(!SEXOS[sx])return ferr(f,'Elige el sexo: el cálculo del peso lo usa.');
    if(ed&&!(e>=15&&e<=90))return ferr(f,'Escribe la edad en años (15 a 90) o déjala vacía.');cambios.sexo=sx;cambios.edad=ed?Math.round(e):null;}
  guardarPC(cambios);closeSheet();const t=TRAS;TRAS=null;if(t)setTimeout(t,60);else toast('Estatura guardada');};

/* las fotos de cada medición (con su silueta) quedan en el teléfono para el laboratorio: las de las últimas 40. Y las
   fotos originales de cada toma (sin dibujos, para el análisis que se exporta desde el laboratorio): las de las
   últimas 10 (pcr-<id>-<ángulo>-<toma>) */
const FOTOS_MAX=40,ORIG_MAX=10;
const aBlob=c=>new Promise(ok=>c.toBlob(ok,'image/jpeg',.92));
async function guardarFotos(id,caps){
  if(!window.Fotos)return;
  try{for(let i=0;i<caps.length;i++){const b=await (await fetch(caps[i].img)).blob();await Fotos.guardar(`pc-${id}-${i}`,b);
      const T=caps[i].tomas||[];for(let j=0;j<T.length;j++)if(T[j].fc){const o=await aBlob(T[j].fc);if(o)await Fotos.guardar(`pcr-${id}-${i}-${j}`,o);}}
    const R=REG();for(const x of R.slice(0,-FOTOS_MAX))for(let i=0;i<2;i++)Fotos.borrar(`pc-${x.id}-${i}`);
    for(const x of R.slice(0,-ORIG_MAX))for(let i=0;i<2;i++)for(let j=0;j<3;j++)Fotos.borrar(`pcr-${x.id}-${i}-${j}`);}catch(e){console.warn(e);}
}
/* ---------- medir ---------- */
let ULT=null;
async function medirVivo(){
  const modo=UI.pc.modo,M=MODOS[modo],alto=PC()[M.alto];
  if(!window.CamVivo){toast('La cámara en vivo no está disponible.');return null;}
  const caps=await CamVivo.abrir({titulo:'Peso con cámara',clase:M.clase,ref:!!M.ref,buscar:M.buscar,pasos:M.pasos});
  if(!caps||caps.length<M.pasos.length)return null;
  const r={...combinar(modo,caps,alto,ropaSel(),{sexo:PC().sexo,edad:PC().edad}),caps,stats:caps.stats||{}};r.id=registrar(r,r.stats);guardarFotos(r.id,caps);return r;
}
// antes de medir hace falta la estatura de referencia
function conAlto(fn){const M=MODOS[UI.pc.modo];if(PC()[M.alto]&&(UI.pc.modo!=='persona'||SEXOS[PC().sexo]))return fn();formAlto(fn);}
const medHTML=r=>r.modo==='persona'&&r.dims
  ?`<div class="pc-med"><div><b>${cm(r.dims.bid)}</b><span>hombros</span></div><div><b>${cm(r.dims.wb)}</b><span>cintura</span></div><div><b>${cm(r.dims.th)}</b><span>muslo</span></div><div><b>${nf(r.imc,1)}</b><span>IMC</span></div></div>`
  :r.modo==='persona'?`<div class="pc-med"><div><b>${nf(r.L)} <span data-no-tr>L</span></b><span>volumen</span></div><div><b>${cm(r.anchoF)}</b><span>hombros</span></div><div><b>${cm(r.prof)}</b><span>pecho de fondo</span></div><div><b>${nf(r.imc,1)}</b><span>IMC</span></div></div>`
  :`<div class="pc-med"><div><b>${cm(r.largo)}</b><span>largo</span></div><div><b>${cm(r.altoAnimal)}</b><span>alto</span></div><div><b>${cm(r.ancho)}</b><span>ancho</span></div><div><b>${nf(r.L)} <span data-no-tr>L</span></b><span>volumen</span></div></div>`;
/* cómo se calculó este peso, con sus números */
function explicar(r){
  const per=r.modo==='persona',P=r.partes||{},pct=v=>`±${nf(v*100,1)} %`,ml=r.modo==='persona'?MODOS.persona:MODOS.ganado,mm=modeloV(r.modo);
  if(per&&r.dims)return explicarV3(r,mm,pct);
  const pasos=per?[
    ['Escala','Tu estatura más 2.5 cm de suela y pelo, dividida entre el alto de tu silueta en cada foto: así cada píxel tiene su medida en centímetros.'],
    ['Rebanadas',`La silueta de frente da el ancho a cada altura del cuerpo y la de costado el fondo. Cada rebanada es un corte ovalado: área = f × ancho × fondo (f = ${nf(FORMA.tronco,2)} en el tronco y ${nf(FORMA.cabeza,3)} en cabeza, brazos y piernas). Se descuentan ${nf(ROPA[r.ropa||'normal'].cm,1)} cm de ropa por lado.`],
    ['Volumen',`La suma de las rebanadas: ${nf(r.L,1)} L. Cabeza ${nf(P.cabeza,1)} L · tronco ${nf(P.tronco,1)} L · brazos ${nf(P.brazos,1)} L · piernas ${nf(P.piernas,1)} L.`]]
   :[['Escala','La estatura de la persona de referencia más 2.5 cm, dividida entre su alto en cada foto: así cada píxel tiene su medida en centímetros.'],
    ['Rebanadas','De costado, cada columna del cuerpo (sin las patas) es una rebanada a lo largo del animal; su corte tiene la forma que se ve por detrás, agrandada o achicada según el grueso del cuerpo en esa columna.'],
    ['Volumen',`La suma de las rebanadas: ${nf(r.L,1)} L.`]];
  pasos.push(['Peso',`k × volumen^b = ${nf(W(r.k),3)} × ${nf(r.L,1)}^${nf(r.b,2)} = ${wtxt(r.kg,1)}. ${per?'k es la densidad del cuerpo: peso por litro.':'k es mayor que la densidad del cuerpo porque las patas no entran en el volumen.'} ${mm.n?`Ajustado con ${pl(mm.n,'medición','mediciones')} de báscula.`:`De fábrica k = ${nf(W(ml.k),2)} ${UW()}/L; con la báscula se ajusta.`}`]);
  pasos.push(['Rango',`${pct(r.err)}: el error del modelo (${pct(mm.err)}) y el desacuerdo entre las ${r.comb} combinaciones de fotos (${pct(r.cv)}).`]);
  return `<details class="pc-exp"><summary>Cómo se calcula</summary><p class="pc-exp-f">peso = k · V<sup>b</sup></p><ol>${pasos.map(([t,x])=>`<li><b>${t}</b><span>${x}</span></li>`).join('')}</ol></details>`;
}
// personas (v4): las medidas, la fórmula de ANSUR II y la calibración
const NOM_EST={cf:'pantorrilla',ne:'cuello',lt:'muslo sobre la rodilla'};
function explicarV3(r,mm,pct){
  const D=r.dims,c=ANSUR.coef,ex=(v,d=2)=>`<sup data-no-tr>${nf(v,d)}</sup>`,est=(r.estimadas||[]).filter(k=>NOM_EST[k]);
  const pasos=[
    ['Escala','Tu estatura más 2.5 cm de suela y pelo, dividida entre el alto de tu silueta en cada foto: así cada píxel tiene su medida en centímetros.'],
    ['Puntos del cuerpo',`Un modelo de pose ubica 133 puntos (cuerpo, pies, manos y dedos). Con ellos se quitan los brazos y las manos pegados al medir el tronco, se corrige el giro de la toma de perfil (${nf(r.giro||0,1)}°) y se revisa que la silueta llegue de la cabeza a los pies.`],
    ['Medidas',`A la altura de cada una según ANSUR II, sin ${nf(ROPA[r.ropa||'normal'].cm,1)} cm de ropa por lado: hombros ${cm(D.bid)} · pecho ${cm(D.cb)} de ancho y ${cm(D.cd)} de fondo · cintura ${cm(D.wb)} y ${cm(D.wd)} · cadera ${cm(D.hb)} · glúteos ${cm(D.bd)} de fondo · contorno del muslo ${cm(D.th)}.`]];
  if(D.cf)pasos.push(['Piernas y cuello',`Contorno de la pantorrilla ${cm(D.cf)} · del muslo sobre la rodilla ${cm(D.lt)} · del cuello ${cm(D.ne)}.${est.length?`<br><span>Estimado con las demás medidas (no se pudo tomar bien en la foto):</span> ${est.map(k=>`<span>${NOM_EST[k]}</span>`).join(', ')}`:''}`]);
  pasos.push(['Modelo',`Ajustado con ${nf(ANSUR.n,0)} personas pesadas en báscula y medidas a mano (ANSUR II), con tu sexo${r.edad?' y tu edad':''}. Con tus medidas da ${wtxt(r.pred,1)}. Error medio de validación: ±${nf(ANSUR.mape*100,1)} % con medidas de foto.`],
    ['Peso',`k × P^b = ${nf(r.k,3)} × ${nf(r.pred,1)}^${nf(r.b,2)} = ${wtxt(r.kg,1)}. ${mm.n?`k y b ajustados con ${pl(mm.n,'medición','mediciones')} de tu báscula: corrigen lo propio de tu cuerpo y tu cámara.`:'De fábrica k = 1 y b = 1; con tu báscula se ajustan a ti.'}`],
    ['Rango',`${pct(r.err)}: el error del modelo (${pct(mm.err)}) y el desacuerdo entre las ${r.comb} combinaciones de fotos (${pct(r.cv)}).`],
    ['Volumen',`Como comprobación, la silueta en rebanadas da ${nf(r.L,1)} L (${nf(r.kg/r.L,2)} kg/L).`]);
  const f=D.cf?`P = e${ex(c[0],2)} · S${ex(c[1])} · A${ex(c[2])} · B${ex(c[3])} · C${ex(c[4])} · D${ex(c[5])} · E${ex(c[6])} · F${ex(c[7])} · G${ex(c[8])} · M${ex(c[9])} · H${ex(c[10])} · N${ex(c[11])} · K${ex(c[12])} · e${ex(c[13])}<sup>·x</sup> · e${ex(c[14],3)}<sup>·t/50</sup>`
    :`P = e${ex(c[0],2)} · …`;
  return `<details class="pc-exp"><summary>Cómo se calcula</summary><p class="pc-exp-f" data-no-tr>${f}</p>
    <p class="hint pc-exp-l">S: estatura. A: hombros. B y C: ancho y fondo del pecho. D y E: ancho y fondo de la cintura. F: ancho de la cadera. G: fondo de los glúteos. M: contorno del muslo. H: contorno de la pantorrilla. N: contorno del cuello. K: contorno del muslo sobre la rodilla. Todo en milímetros.</p><p class="hint pc-exp-l">En la fórmula, x vale uno en hombres y cero en mujeres, y t es la edad en años.</p><p class="pc-exp-f" data-no-tr>peso = k · P<sup>b</sup></p><ol>${pasos.map(([t,x])=>`<li><b>${t}</b><span>${x}</span></li>`).join('')}</ol></details>`;
}
const capsHTML=r=>`<div class="pc-caps">${r.caps.map(c=>`<figure><img src="${c.img}" alt=""><figcaption>${c.nombre}</figcaption></figure>`).join('')}</div>`;
function resultado(r){
  const x=loteSel(),an=r.modo==='ganado'&&x?(x.animAct||[]):[];ULT=r;
  openSheet(shHead(r.modo==='persona'?'Tu medición':'Peso estimado',r.modo==='persona'?'Peso con cámara':esc(x?x.l.nombre:''))+`<div class="sh-body pc-resw">${capsHTML(r)}
    <div class="pc-kg"><b>${wtxt(r.kg)}</b><span>entre ${wtxt(r.kg*(1-r.err))} y ${wtxt(r.kg*(1+r.err))}</span></div>
    ${r.aviso?`<p class="pc-aviso">${ico('aviso',2)}<span>${r.aviso}</span></p>`:''}
    ${medHTML(r)}
    ${an.length?q('Arete (opcional)',`<select class="in" id="pcArete"><option value="">Sin arete</option>${an.map(a=>`<option value="${esc(a.id)}">${esc(a.arete)}</option>`).join('')}</select>`):''}
    <p class="hint">${r.n?`Calibrado con ${pl(r.n,'medición','mediciones')} de tu báscula.`:'Sin calibrar: estimado general.'}</p>
    ${explicar(r)}
    <button type="button" class="lnk" data-act="pcCalRes">${r.modo==='persona'?'¿Sabes tu peso? Calibra con tu báscula':'¿Lo pesaste en báscula? Calibra con su peso'}</button></div>
    <div class="sh-foot">${r.modo==='persona'?`<button type="button" class="btn" data-act="cerrar" style="flex:1">Cerrar</button><button type="button" class="btn pri" data-act="pcVivo" style="flex:1.4">Medir otra vez</button>`
      :`<button type="button" class="btn" data-act="cerrar" style="flex:1">Descartar</button><button type="button" class="btn pri" data-act="pcAgregar" style="flex:1.6">Agregar al pesaje</button>`}</div>`);
}
ACTS.pcVivo=()=>{
  if(UI.pc.modo==='ganado'&&!loteSel()){toast('Primero crea un lote.');return;}
  closeSheet();conAlto(async()=>{const r=await medirVivo();if(r)resultado(r);});
};
// el peso de báscula de la última medición
function formCal(r){
  openSheet(shHead(r.modo==='persona'?'Calibrar con tu báscula':'Calibrar con la báscula','Peso con cámara')+formWrap('pcCal',`${capsHTML(r)}
    <p class="hint">La cámara estima <b>${wtxt(r.kg)}</b>. Escribe lo que marcó la báscula.</p>
    ${q('Peso de báscula',inp('kg','',{unit:UW(),xl:true,ph:nf(W(r.kg))}))}`,foot('Guardar calibración')));
}
ACTS.pcCalRes=()=>{if(ULT)formCal(ULT);};
ACTS.pcCalibrar=()=>{if(UI.pc.modo==='ganado'&&!loteSel()){toast('Primero crea un lote.');return;}closeSheet();conAlto(async()=>{const r=await medirVivo();if(r){ULT=r;formCal(r);}});};
SAVE.pcCal=f=>{const r=ULT;if(!r)return;const v=toKg(num(fv(f,'kg')));
  const [lo,hi]=r.modo==='persona'?[15,250]:[40,1300];if(!(v>=lo&&v<=hi))return ferr(f,'Escribe el peso de la báscula.');
  if(r.id)actualizarReg(r.id,{real:Math.round(v*10)/10});
  else guardarPC({cal:(PC().cal||[]).concat({modo:r.modo,L:Math.round(r.L*10)/10,kg:Math.round(v*10)/10,f:hoy(),ts:Date.now()}).slice(-200)});
  ULT=null;closeSheet();const m=modeloV(r.modo);toast(`Calibración guardada: error típico ±${Math.round(m.err*100)} %.`,4000);};
ACTS.pcBorrarCal=()=>confirmar('¿Borrar la calibración?','El peso vuelve a calcularse con el estimado general.','Borrar',()=>{const modo=UI.pc.modo;
  guardarPC({cal:(PC().cal||[]).filter(c=>c.modo!==modo),reg:REG().map(x=>x.modo===modo?{...x,real:null}:x)});toast('Calibración borrada');});
ACTS.pcAgregar=()=>{const r=ULT;if(!r)return;const x=loteSel();if(!x)return;const sel=$('#pcArete'),aid=sel?sel.value:'';const a=aid&&(x.animAct||[]).find(z=>z.id===aid);
  UI.pc.items=UI.pc.items.filter(z=>!aid||z.aid!==aid).concat({kg:r.kg,err:r.err,aid:aid||'',arete:a?a.arete:'',img:r.caps[0].img});ULT=null;closeSheet();render();
  toast(`${wtxt(r.kg)} agregado. Mide otro animal o guarda el pesaje.`,3000);};
ACTS.pcQuitar=el=>{UI.pc.items.splice(+el.dataset.i,1);render();};
ACTS.pcGuardar=()=>{const x=loteSel(),it=UI.pc.items;if(!x||!it.length)return;
  const prom=it.reduce((s,r)=>s+r.kg,0)/it.length,pesos={};for(const r of it)if(r.aid)pesos[r.aid]=Math.round(r.kg*10)/10;
  addItem({tipo:'pesaje',f:hoy(),lote:x.id,prom:Math.round(prom*10)/10,cab:it.length,...(Object.keys(pesos).length?{pesos}:{}),parcial:true,metodo:'camara'});
  UI.pc.items=[];render();toast(`${x.l.nombre}: ${wtxt(prom)} de promedio con cámara (${pl(it.length,'animal','animales')})`,4000);};

/* ---------- entradas: Hoy y el formulario de pesaje ---------- */
const _hoy=PAGES.hoy;
PAGES.hoy=(...a)=>{const h=_hoy(...a),i=h.indexOf('<main class="bd">');if(i<0)return h;const k=i+'<main class="bd">'.length;
  return h.slice(0,k)+`<a class="card pad pc-hoy" href="#pesocam"><span class="mas-ic t-v">${icono('camara','i3')}</span><div><b>Peso con cámara <span class="pc-beta">Beta</span></b><span>Estima el peso con la cámara del teléfono.</span></div>${ico('chev')}</a>`+h.slice(k);};
const _pes=FORMS.pesaje;
if(_pes)FORMS.pesaje=(...a)=>{_pes(...a);const b=$('#sheet .sh-body');if(b&&!b.querySelector('.pc-f'))b.insertAdjacentHTML('afterbegin',`<button type="button" class="btn full pc-f" data-act="pcIr">${icono('camara','i3')}Estimar con cámara (beta)</button>`);};
ACTS.pcIr=()=>{closeSheet();UI.pc.modo='ganado';try{localStorage.setItem(MKEY,'ganado');}catch(e){}location.hash='#pesocam';};
window.PesoCam={modeloV,ajustar,calModo,combinar,volPersona,volGanado,tramos,medidasPersona,kpT,registrar,actualizarReg,REG,guardarPC,MODOS,ROPA,FORMA,ANSUR,EXTRA_CM,VERSION_MODELO};
if(route().p==='pesocam'||route().p==='hoy')render();
})();
