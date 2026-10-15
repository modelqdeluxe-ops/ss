/* Rumentis Beta: cámara en vivo para el peso con cámara.
   Pantalla completa con el video. El modelo rápido (vision.js) busca la silueta en cada cuadro y revisa si está todo
   listo para medir: se ve completo, a buena distancia, en el ángulo que toca, quieto y (en ganado) con la persona de
   referencia al lado. Con todo en verde unos cuadros seguidos, la foto se toma sola y pide el siguiente ángulo;
   mientras tanto el modelo preciso mide esa foto en segundo plano, sobre un recorte alrededor del sujeto.
   Fluidez: los cuadros se recortan y reducen con createImageBitmap y van directo al worker; hay tantos cuadros en vuelo
   como workers del modelo rápido; y la silueta se dibuja a 60 cuadros por segundo (contorno fino, la caja se desliza
   entre un resultado y el siguiente).
   CamVivo.abrir({titulo, clase, ref, buscar, pasos:[{id, nombre, instr, girar, ang:{min,max,rel}, dist:{eje,min,max}, banda}]})
   devuelve una captura por paso ({id, nombre, med, sil, ref, W, H, img, manual, fuente}) o null si se cierra antes.
   Las medidas van en píxeles del cuadro (W×H); la escala en centímetros la pone pesocam.js con una estatura conocida.
   (ang.rel: la proporción ancho/alto debe bajar a esa fracción de la de la primera toma; ang.perfil: lo mismo con el
   ancho del tronco a la altura del pecho, que no se deja engañar por los pies ni los brazos; p. ej. de frente a costado) */
(function(){
'use strict';
const CFG=window.RUMENTIS||{};if(!CFG.beta||!window.Vision)return;

/* reglas generales (las de distancia y ángulo vienen en cada paso) */
// tomas: fotos por ángulo para el modelo preciso (se promedian: menos ruido); separadas `entre` ms
const REGLAS={score:.5,margen:.02,quieto:.86,seguidos:3,lienzo:960,ref:{alto:.25,forma:1.6},tomas:3,entre:140};
const MARCA='Rumentis Labs';
const CHIPS=[['det','Detectado'],['comp','Completo'],['dist','Distancia'],['ang','Ángulo'],['ref','Referencia'],['quieto','Quieto']];
// colores de estado (esmeralda, ámbar y coral, un poco más suaves que los primarios: se ven más finos sobre el video)
const COLOR={verde:'52,211,153',ambar:'251,191,36',rojo:'248,113,113'};

const iou=(a,b)=>{if(!a||!b)return 0;const x0=Math.max(a[0],b[0]),y0=Math.max(a[1],b[1]),x1=Math.min(a[2],b[2]),y1=Math.min(a[3],b[3]);
  const i=Math.max(0,x1-x0)*Math.max(0,y1-y0),u=(a[2]-a[0])*(a[3]-a[1])+(b[2]-b[0])*(b[3]-b[1])-i;return u>0?i/u:0;};
const mediana=v=>{const s=v.filter(x=>isFinite(x)).sort((a,b)=>a-b),n=s.length;return n?(n%2?s[(n-1)/2]:(s[n/2-1]+s[n/2])/2):NaN;};
const pct=(v,p)=>{const s=v.filter(x=>x>0).sort((a,b)=>a-b);return s.length?s[Math.min(s.length-1,Math.floor(p*(s.length-1)+.5))]:0;};
const dentro=(c,mg)=>c[0]>mg&&c[1]>mg&&c[2]<1-mg&&c[3]<1-mg;

/* la rejilla G×G de una silueta cubre una zona Z del cuadro W×H (todo el cuadro o un recorte): se anota su caja en
   fracciones del cuadro (n), cuántos píxeles mide cada celda (cx, cy) y si toca un borde de la zona que no es borde
   del cuadro (entonces la zona la cortó) */
// una caja en fracciones del cuadro (W×H) → en fracciones de la zona Z (la pista para el modelo)
const enZona=(n,Z,W,H)=>n?[(n[0]*W-Z.x)/Z.w,(n[1]*H-Z.y)/Z.h,(n[2]*W-Z.x)/Z.w,(n[3]*H-Z.y)/Z.h]:null;
const enZonaPt=(q,Z,W,H)=>q?[(q[0]*W-Z.x)/Z.w,(q[1]*H-Z.y)/Z.h]:null;
function aCuadro(s,G,Z,W,H){
  if(!s||!s.ok)return s;const [x0,y0,x1,y1]=s.caja;s.G=G;s.Z=Z;s.cx=Z.w/G;s.cy=Z.h/G;
  s.n=[(Z.x+x0*s.cx)/W,(Z.y+y0*s.cy)/H,(Z.x+x1*s.cx)/W,(Z.y+y1*s.cy)/H];
  s.cortada=(x0<=0&&Z.x>1)||(y0<=0&&Z.y>1)||(x1>=G&&Z.x+Z.w<W-1)||(y1>=G&&Z.y+Z.h<H-1);return s;
}
/* medidas de una silueta en píxeles del cuadro */
// ancho del tronco (mediana de las filas entre 25 % y 45 % de la altura) sobre la altura de la silueta: de frente es
// ~2 veces el de perfil; a 45° queda en medio. Sirve para saber si la toma de costado es de perfil de verdad.
function torsoN(s){const [,y0,,y1]=s.caja,h=y1-y0,fs=[];for(let y=Math.floor(y0+.25*h);y<Math.ceil(y0+.45*h);y++)fs.push(s.filas[y]);
  return pct(fs,.5)*s.cx/(h*s.cy);}
function medir(s,W,H,banda){
  const [x0,y0,x1,y1]=s.caja,cx=s.cx,cy=s.cy,h=y1-y0,b=banda||[.2,.6],fs=[];
  for(let y=Math.floor(y0+b[0]*h);y<Math.ceil(y0+b[1]*h);y++)fs.push(s.filas[y]);
  return {W,H,alto:h*cy,ancho:(x1-x0)*cx,area:s.area*cx*cy,banda:pct(fs,.9)*cx,ratio:((x1-x0)*cx)/(h*cy),torsoN:torsoN(s),score:s.score};
}
/* una zona alrededor de las cajas (fracciones del cuadro): con margen, de proporción entre `amin` y `amax` y de al
   menos `min` del cuadro. Para seguir al sujeto con el modelo rápido y para recortar la foto del preciso. */
function zonaDe(cajas,W,H,{margen=.25,min=.5,amin=.6,amax=1.6}={}){
  const c=cajas.filter(Boolean);if(!c.length)return null;
  const x0=Math.min(...c.map(z=>z[0]))*W,y0=Math.min(...c.map(z=>z[1]))*H,x1=Math.max(...c.map(z=>z[2]))*W,y1=Math.max(...c.map(z=>z[3]))*H;
  let w=Math.max((x1-x0)*(1+2*margen),W*min),h=Math.max((y1-y0)*(1+2*margen),H*min);
  if(w/h<amin)w=h*amin;else if(w/h>amax)h=w/amax;
  w=Math.min(w,W);h=Math.min(h,H);if(w>W*.92&&h>H*.92)return null;
  const x=Math.min(W-w,Math.max(0,(x0+x1)/2-w/2)),y=Math.min(H-h,Math.max(0,(y0+y1)/2-h/2));
  return {x,y,w,h};
}
/* la zona que se sigue: se queda quieta mientras las cajas estén cómodas dentro (así el modelo ve siempre el mismo
   recorte y la silueta no tiembla); se mueve cuando el sujeto se acerca a un borde o se ve muy chico en ella */
function zonaSig(cajas,W,H,act){
  const nueva=zonaDe(cajas,W,H);if(!act||!nueva)return nueva;const c=cajas.filter(Boolean),m=.06;
  const comoda=c.every(z=>{const x0=z[0]*W,y0=z[1]*H,x1=z[2]*W,y1=z[3]*H;
    return (x0>=act.x+m*act.w||act.x<1)&&(y0>=act.y+m*act.h||act.y<1)&&(x1<=act.x+act.w*(1-m)||act.x+act.w>W-1)&&(y1<=act.y+act.h*(1-m)||act.y+act.h>H-1)&&(y1-y0)>=.35*act.h;});
  return comoda?act:nueva;
}
// la persona de referencia: completa, de pie y de buen tamaño en el cuadro
/* la distancia al animal sin referencia, con el teléfono en la mano: a una altura conocida h (cm, del suelo al
   teléfono) y con la inclinación del acelerómetro (elev: grados, negativo = hacia abajo). El punto donde las patas tocan
   el suelo (yPie, en píxeles del cuadro W×H) está a un ángulo a bajo el horizonte y la distancia por el suelo es
   h / tan(a). Con ella, la profundidad sobre el eje de la cámara (Z) y los cm por píxel en el plano del animal (Z / f).
   fovL: el campo de visión del lado largo del cuadro (grados; la cámara principal en 1x ronda 65–70°). Supone suelo
   plano entre el teléfono y el animal. Devuelve {D (m), Z (cm), cmpx, a (grados)} o null si no se puede. */
/* la escala (cm por píxel) de un cuerpo de pie a D cm (en horizontal) del teléfono, inclinado p rad hacia abajo: el
   alto real de la caja (de las patas, yPie, a lo más alto, yTop; filas de la imagen) entre su alto en píxeles, con la
   perspectiva exacta. Con la profundidad de un solo punto (Z/f) la escala sale grande cuando el teléfono mira hacia
   abajo: +5 % a 10°, +8 a 10 % a 15° (lo de arriba del cuerpo queda más cerca de la cámara que las patas). */
function escPlano(D,p,yTop,yPie,cy,fy){
  if(!(D>0)||!(fy>0)||!isFinite(p)||!isFinite(yTop)||!isFinite(yPie)||!(yPie-yTop>4))return null;
  const t=y=>Math.tan(p+Math.atan((y-cy)/fy)),h=D*(t(yPie)-t(yTop));return h>0?h/(yPie-yTop):null;
}
// el campo de visión (grados, lado largo) con zoom z: la distancia focal se multiplica por z
const fovZ=(fov,z)=>z>1.01?2*Math.atan(Math.tan(fov*Math.PI/360)/z)*180/Math.PI:fov;
function suelo(yPie,W,H,elev,h,fovL,yTop,extra){
  if(!(h>0)||!(fovL>20)||elev==null||!isFinite(elev)||!(W>0)||!(H>0)||!isFinite(yPie))return null;
  const f=(Math.max(W,H)/2)/Math.tan(fovL*Math.PI/360),p=-elev*Math.PI/180,a=p+Math.atan((yPie-H/2)/f);
  if(!(a>2*Math.PI/180&&a<80*Math.PI/180))return null;
  // extra: cuánto más lejos que lo que toca el suelo está el plano del cuerpo (cm)
  const Z=h/Math.sin(a)*Math.cos(a-p),D=h/Math.tan(a)+(extra>0?extra:0),k=isFinite(yTop)?escPlano(D,p,yTop,yPie,H/2,f):null;
  return {D:D/100,Z,cmpx:k||Z/f,a:a*180/Math.PI,f};
}
function refOk(p,W,H){if(!p||!p.ok||p.score<REGLAS.score||p.cortada)return false;const c=p.n;
  return dentro(c,REGLAS.margen)&&c[3]-c[1]>=REGLAS.ref.alto&&((c[3]-c[1])*H)/((c[2]-c[0])*W)>=REGLAS.ref.forma;}
/* hombros de perfil (con los puntos del cuerpo): separación de los dos hombros entre el largo del tronco (hombros a
   caderas). De frente ~0.8; a 45° ~0.5; de perfil casi 0. null si los puntos no son confiables. */
function hombrosN(kp,W,H){if(!kp)return null;const P=kp.p,c=i=>P[3*i+2]>=.3;if(!(c(5)&&c(6)&&(c(11)||c(12))))return null;
  const ys=(P[16]+P[19])/2,yh=c(11)&&c(12)?(P[34]+P[37])/2:c(11)?P[34]:P[37],t=Math.abs(yh-ys)*H;
  return t>1?Math.abs(P[15]-P[18])*W/t:null;}
function revisar(s,p,W,H,paso,prev,ref,conRef,kp){
  const E={det:false,comp:null,dist:null,ang:null,ref:conRef?false:undefined,quieto:null};
  E.det=!!(s&&s.ok&&s.score>=REGLAS.score);
  if(conRef)E.ref=refOk(p,W,H);
  if(!E.det)return {E,msg:paso.buscar};
  const c=s.n,d=paso.dist,a=paso.ang;
  E.comp=dentro(c,REGLAS.margen)&&!s.cortada;
  const v=d.eje==='alto'?c[3]-c[1]:d.eje==='max'?Math.max(c[3]-c[1],c[2]-c[0]):c[2]-c[0];E.dist=v>=d.min&&v<=d.max;
  const ratio=((c[2]-c[0])*W)/((c[3]-c[1])*H);
  // (ref: las medidas de la primera toma; perfil: el tronco debe verse a lo más esa fracción de ancho que de frente)
  const hn=a.hombros?hombrosN(kp,W,H):null;
  // (con los puntos del cuerpo, los hombros dicen directo si está de perfil; las reglas de la silueta contra la toma de
  // frente (proporción y tronco) solo cuentan sin puntos: con el cuerpo de perfil de verdad a veces fallaban por poco)
  const conKP=hn!=null,tn=!conKP&&a.perfil&&ref&&ref.torsoN?torsoN(s):0,perfil=conKP?hn<=a.hombros:(!a.perfil||!ref||!ref.torsoN||tn<=a.perfil*ref.torsoN);
  E.ang=(a.min==null||ratio>=a.min)&&(a.max==null||ratio<=a.max)&&(conKP||!a.rel||!ref||ratio<=a.rel*ref.ratio)&&perfil;
  const q=iou(c,prev),qMin=paso.quieto!=null?paso.quieto:REGLAS.quieto;E.quieto=q>=qMin;
  /* qué tan cerca está de la posición ideal (0 a 1): cada regla da 1 si se cumple y baja según lo lejos que esté */
  const cerca=x=>Math.max(0,Math.min(1,x));
  const fuera=Math.max(0,REGLAS.margen-c[0],REGLAS.margen-c[1],c[2]-(1-REGLAS.margen),c[3]-(1-REGLAS.margen));
  const kComp=E.comp?1:cerca(1-fuera/.1-(s.cortada?.3:0)),kDist=E.dist?1:cerca(1-(v<d.min?d.min-v:v-d.max)/.3);
  const viol=[a.min!=null&&ratio<a.min?(a.min-ratio)/a.min:0,a.max!=null&&ratio>a.max?(ratio-a.max)/a.max:0,
    !conKP&&a.rel&&ref&&ratio>a.rel*ref.ratio?(ratio-a.rel*ref.ratio)/(a.rel*ref.ratio):0,!perfil&&tn?Math.max(0,(tn-a.perfil*ref.torsoN)/(a.perfil*ref.torsoN)):0,
    hn!=null&&hn>a.hombros?(hn-a.hombros)/.6:0];
  const kAng=E.ang?1:cerca(1-Math.max(...viol)/.5),kRef=conRef?(E.ref?1:.4):1,kQ=cerca((q-qMin*.7)/(qMin*.3));
  const pos=kComp*kDist*kAng*kRef*(.8+.2*kQ);
  let msg='';
  if(!E.comp)msg=v>d.max*.95?'Aléjate un poco':'Que se vea el cuerpo completo';
  else if(!E.dist)msg=v<d.min?'Haz zoom o acércate':'Aléjate o quita zoom';
  else if(!E.ang)msg=!perfil&&paso.girarMas?paso.girarMas:paso.girar;
  else if(conRef&&!E.ref)msg=p&&p.ok?'Que la persona de referencia se vea completa y derecha':'Falta la persona de referencia junto al animal';
  else if(!E.quieto)msg='Quieto…';
  return {E,msg,caja:c,pos,dbg:{ratio:+ratio.toFixed(3),hn:hn==null?null:+hn.toFixed(3),tn:+(tn||0).toFixed(3),ref:ref&&ref.ratio?+ref.ratio.toFixed(3):null}};
}
/* la silueta del video (modelo rápido), limpia con los puntos del cuerpo: se quita lo que queda lejos del esqueleto
   (suelo, cama, muebles pegados al cuerpo). Cada celda debe quedar cerca de algún tramo del esqueleto, con holgura
   (1.5 veces): del tronco (el cuadrilátero de hombros y caderas) a 24 % del alto de la silueta, de la cabeza a 13 %,
   de brazos y piernas a 10 %, de manos (hasta la punta de cada dedo) y pies a 5–6 %. Medido contra siluetas a mano
   (COCO): con el modelo rápido baja el error de área (casa 19 → 16 %) sin perder IoU; más ajustado cortaba cabeza y
   pies. Solo en el video: la foto que se mide queda como la da el modelo preciso. P: x, y en fracciones del cuadro. */
const TRAMOS_LIMPIA=[[5,7,.065],[7,9,.065],[6,8,.065],[8,10,.065],[11,13,.065],[13,15,.065],[12,14,.065],[14,16,.065],[0,0,.09],[3,4,.08],[0,3,.08],[0,4,.08],[3,3,.08],[4,4,.08],
  [15,19,.04],[15,17,.04],[16,22,.04],[16,20,.04],[9,91,.04],[10,112,.04],
  [91,95,.035],[91,99,.035],[91,103,.035],[91,107,.035],[91,111,.035],[112,116,.035],[112,120,.035],[112,124,.035],[112,128,.035],[112,132,.035]];
function limpiarSil(s,P,W,H){
  if(!s||!s.ok||!P||P.length<23*3)return s;const c=i=>P[3*i+2]>=.3;
  if(![5,6,11,12].every(c))return s;
  const G=s.G,Z=s.Z,gx=i=>(P[3*i]*W-Z.x)/s.cx,gy=i=>(P[3*i+1]*H-Z.y)/s.cy,escY=s.cy,HOLGURA=1.5;
  // (el alto de la silueta en celdas; los puntos suelen dar una estatura menor que la real y cortarían de más)
  const alto=(s.caja[3]-s.caja[1])*HOLGURA;if(!(alto>G*.1))return s;
  const tr=TRAMOS_LIMPIA.filter(([a,b])=>c(a)&&c(b)).map(([a,b,m])=>[gx(a),gy(a),gx(b),gy(b),m*alto*escY]);
  const quad=[5,6,12,11].map(i=>[gx(i),gy(i)]),mT=.16*alto*escY;
  const dSeg=(x,y,[ax,ay,bx,by])=>{const dx=bx-ax,dy=by-ay,l=dx*dx+dy*dy,t=l?Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/l)):0;return Math.hypot((x-ax-t*dx)*s.cx,(y-ay-t*dy)*escY);};
  const enQuad=(x,y)=>{let d=0;for(let i=0;i<4;i++){const [ax,ay]=quad[i],[bx,by]=quad[(i+1)%4],cr=(bx-ax)*(y-ay)-(by-ay)*(x-ax);if(cr!==0){if(d&&Math.sign(cr)!==d)return false;d=Math.sign(cr);}}return true;};
  const bordes=[0,1,2,3].map(i=>[...quad[i],...quad[(i+1)%4]]);
  const M=new Float32Array(G*G);let quita=0;
  for(let y=0;y<G;y++)for(let x=0;x<G;x++){const i=y*G+x;if(!s.mask[i]){M[i]=-1;continue;}const X=x+.5,Y=y+.5;
    let ok=enQuad(X,Y);if(!ok)for(const b of bordes)if(dSeg(X,Y,b)<=mT){ok=true;break;}
    if(!ok)for(const t of tr)if(dSeg(X,Y,t)<=t[4]){ok=true;break;}
    M[i]=ok?1:-1;if(!ok)quita++;}
  if(!quita)return s;
  const n=Vision.silueta(M,G,G,G,s.caja.map(z=>z/G));if(!n.ok)return s;
  n.score=s.score;n.conf=s.conf;n.limpia=quita/s.area;return aCuadro(n,G,Z,W,H);
}
/* la caja del cuerpo según los puntos (fracciones del cuadro): de la cabeza (sobre los ojos, con la coronilla) a los
   pies, de lado a lado con manos; null si faltan hombros o caderas */
function cajaPuntos(P){const ok=i=>P[3*i+2]>=.4;if(![5,6,11,12].every(ok))return null;let x0=1,y0=1,x1=0,y1=0;
  for(let i=0;i<P.length/3;i++)if(ok(i)&&(i<23||i>=91)){x0=Math.min(x0,P[3*i]);x1=Math.max(x1,P[3*i]);y0=Math.min(y0,P[3*i+1]);y1=Math.max(y1,P[3*i+1]);}
  const h=y1-y0;return [x0-.04*h,y0-.1*h,x1+.04*h,y1+.03*h];}
// recorte para el modelo de pose: la caja n (fracciones del cuadro W×H) con 25 % de margen y la proporción m.W:m.H
function recortePose(n,W,H,m){let w=(n[2]-n[0])*W*1.25,h=(n[3]-n[1])*H*1.25;const a=m.W/m.H;if(w/h>a)h=w/a;else w=h*a;
  return {x:(n[0]+n[2])/2*W-w/2,y:(n[1]+n[3])/2*H-h/2,w,h};}
const NUCLEOS=()=>navigator.hardwareConcurrency||4;
function beep(){try{const A=window.AudioContext||window.webkitAudioContext;if(!A)return;const a=beep.a||(beep.a=new A()),o=a.createOscillator(),g=a.createGain();
  o.frequency.value=1046;g.gain.setValueAtTime(.18,a.currentTime);g.gain.exponentialRampToValueAtTime(.001,a.currentTime+.18);o.connect(g).connect(a.destination);o.start();o.stop(a.currentTime+.2);}catch(e){}}
// una foto recibida de otro teléfono (JPEG) a un lienzo
async function deFoto(blob){try{const b=await createImageBitmap(blob),c=document.createElement('canvas');c.width=b.width;c.height=b.height;c.getContext('2d').drawImage(b,0,0);try{b.close();}catch(e){}return c;}catch(e){return null;}}
/* el contorno de una silueta como camino en un lienzo que muestra el cuadro W×H en (X,Y,Wd,Hd). `a` (opcional) es la
   caja [x0,y0,x1,y1] donde se quiere ver: el contorno se lleva de su caja real a esa (así se desliza suave) */
function camino(x,s,X,Y,Wd,Hd,W,H,a){
  const c=s.cont;if(!c||c.length<6)return false;const Z=s.Z,n=s.n,b=a||n;
  const sx=(b[2]-b[0])/Math.max(1e-6,n[2]-n[0]),sy=(b[3]-b[1])/Math.max(1e-6,n[3]-n[1]);
  const P=[];for(let i=0;i<c.length;i+=2){const fx=(Z.x+c[i]*s.cx)/W,fy=(Z.y+c[i+1]*s.cy)/H;P.push(X+(b[0]+(fx-n[0])*sx)*Wd,Y+(b[1]+(fy-n[1])*sy)*Hd);}
  // el contorno con curvas (por los puntos medios de cada tramo): sin escalones
  const m=P.length/2,mx=i=>(P[2*(i%m)]+P[2*((i+1)%m)])/2,my=i=>(P[2*(i%m)+1]+P[2*((i+1)%m)+1])/2;
  x.beginPath();x.moveTo(mx(0),my(0));for(let i=1;i<=m;i++)x.quadraticCurveTo(P[2*(i%m)],P[2*(i%m)+1],mx(i),my(i));
  x.closePath();return true;
}
/* los puntos del cuerpo, como un traje de captura de movimiento: puntos blancos que brillan y líneas finas entre
   ellos. P: x, y (fracciones del cuadro), confianza; se dibujan en el lienzo que muestra el cuadro en (X,Y,Wd,Hd). */
// tamaño de cada punto (personas de 133 puntos: la cara y los dedos más chicos, para que no tapen)
const tamPunto=(i,n)=>n<=17?1:i<17?1:i<23?.75:i<91?.38:i===91||i===112?.7:.5;
/* confianza mínima para dibujar un punto: 0.4 en el cuerpo y 0.5 en pies, cara y manos. Medido en COCO (DWPose-s): los
   puntos muy fuera de lugar (a más de 10 % de la estatura) bajan de ~3.7 % a 1–2 %; los dudosos no se muestran */
const verPunto=(P,i,n)=>P[3*i+2]>=(n<=17||i<17?.4:.5);
/* ¿los puntos describen un cuerpo? Si la cámara ve solo una parte (una mano de cerca, la cara), el modelo inventa un
   cuerpo entero con puntos sin sentido: se exige hombros y caderas confiables y al menos 10 de los 17 puntos del cuerpo
   (en animales, 8 de 17) */
function poseBuena(P){const n=P.length/3,c=i=>P[3*i+2]>=.4;if(n<=17)return [...Array(17).keys()].filter(c).length>=8;
  return [5,6,11,12].every(c)&&[...Array(17).keys()].filter(c).length>=10;}
/* una mano (raíz r) se dibuja solo si su muñeca es confiable y su tamaño tiene sentido para el cuerpo (a lo más 1/4
   del largo del tronco): así no aparecen dedos sueltos */
function manoBuena(P,r,mu){const c=i=>P[3*i+2]>=.4;if(!c(mu))return false;const t=Math.hypot(P[15]-P[33],P[16]-P[34])||1;
  let x0=1e9,y0=1e9,x1=-1e9,y1=-1e9;for(let i=r;i<r+21;i++)if(P[3*i+2]>=.5){x0=Math.min(x0,P[3*i]);x1=Math.max(x1,P[3*i]);y0=Math.min(y0,P[3*i+1]);y1=Math.max(y1,P[3*i+1]);}
  return x1<x0||Math.hypot(x1-x0,y1-y0)<=.6*t;}
function puntos(x,P,esq,X,Y,Wd,Hd,r=3){
  if(!P||!poseBuena(P))return;const n=P.length/3,mL=n>91&&manoBuena(P,91,9),mR=n>91&&manoBuena(P,112,10);
  const ok=i=>verPunto(P,i,n)&&(i<91||(i<112?mL:mR)),px=i=>X+P[3*i]*Wd,py=i=>Y+P[3*i+1]*Hd;
  x.save();x.lineCap='round';x.lineJoin='round';
  for(const grueso of [1,0]){x.strokeStyle=grueso?'rgba(255,255,255,.6)':'rgba(255,255,255,.5)';x.lineWidth=Math.max(.7,r*(grueso?.4:.22));x.beginPath();
    for(const [a,b] of esq)if((a<91)===!!grueso&&ok(a)&&ok(b)){x.moveTo(px(a),py(a));x.lineTo(px(b),py(b));}x.stroke();}
  x.shadowColor='rgba(255,255,255,.95)';x.fillStyle='#fff';
  for(let i=0;i<n;i++)if(ok(i)){const ri=r*tamPunto(i,n);x.shadowBlur=ri*3;x.beginPath();x.arc(px(i),py(i),ri,0,2*Math.PI);x.fill();}
  x.restore();}
/* la silueta: un halo suave del color de estado alrededor del cuerpo, una línea limpia encima y un relleno apenas
   visible. Sin escalones (el contorno va en curvas) */
function trazar(x,s,col,X,Y,Wd,Hd,W,H,a,{relleno=.2,linea=2}={}){
  if(!s||!s.ok||!camino(x,s,X,Y,Wd,Hd,W,H,a))return;
  x.save();x.lineJoin='round';x.lineCap='round';
  x.fillStyle=`rgba(${col},${relleno*.45})`;x.fill();
  x.strokeStyle=`rgba(${col},.22)`;x.lineWidth=linea*4.5;x.shadowColor=`rgba(${col},.55)`;x.shadowBlur=linea*8;x.stroke();
  x.shadowBlur=0;x.strokeStyle=`rgba(${col},.98)`;x.lineWidth=linea;x.stroke();x.restore();
}
/* el recuadro: solo cuatro esquinas redondeadas, finas y translúcidas (blancas; del color de estado cuando está listo) */
function recuadro(x,X0,Y0,X1,Y1,col,listo){
  const w=X1-X0,h=Y1-Y0,L=Math.max(12,Math.min(22,w*.2,h*.1)),r=Math.min(12,L*.7);
  x.save();x.lineCap='round';x.lineJoin='round';x.strokeStyle=listo?`rgba(${col},.95)`:'rgba(255,255,255,.7)';x.lineWidth=1.75;x.beginPath();
  for(const [px,py,sx,sy] of [[X0,Y0,1,1],[X1,Y0,-1,1],[X0,Y1,1,-1],[X1,Y1,-1,-1]]){x.moveTo(px,py+sy*L);x.arcTo(px,py,px+sx*L,py,r);x.lineTo(px+sx*L,py);}
  x.stroke();x.restore();
}

function abrir(o){
  return new Promise(async fin0=>{
    // cuadros buenos seguidos para tomar la foto (ganado en movimiento: 2)
    const SEG=o.seguidos||REGLAS.seguidos;
    const pasos=o.pasos.slice(),conRef0=!!o.ref,clases=conRef0?[o.clase,Vision.PERSONA]:[o.clase],caps=[];
    const refDe=p=>conRef0&&!!p&&p.ref!==false;let conRef=refDe(pasos[0]);
    /* personal: cámara enlazada de otro teléfono (no toma sola: espera el disparo de la administración); enlace: la
       administración con cámaras enlazadas (dispara a todas a la vez) */
    const PER=o.personal||null,ENL=o.enlace&&window.Enlace?o.enlace:null,enl=()=>ENL&&ip===0&&Enlace.conectados().length>0;
    let ip=0,activo=true,stream=null,frontal=false,wl=null,tick=0;
    /* la inclinación del teléfono en cada toma (para el laboratorio), con el acelerómetro: elev, hacia dónde apunta la
       cámara (0 = horizontal; negativo = hacia abajo) y horiz, cuánto se ladea la imagen respecto del horizonte (grados) */
    let GRAV=null;const onMot=e=>{const a=e.accelerationIncludingGravity;if(a&&a.x!=null)GRAV=GRAV?GRAV.map((g,i)=>.8*g+.2*[a.x,a.y,a.z][i]):[a.x,a.y,a.z];};try{window.addEventListener('devicemotion',onMot);}catch(e){}
    const telOri=()=>{if(arUsa&&arMeta)return {elev:Math.round(arMeta.elev*10)/10,horiz:Math.round(arMeta.ladeo*10)/10};if(!GRAV)return null;const [x,y,z]=GRAV,n=Math.hypot(x,y,z);if(!(n>3))return null;
      const th=((screen.orientation&&screen.orientation.angle)||+window.orientation||0)*Math.PI/180,up=x*Math.sin(th)+y*Math.cos(th),der=x*Math.cos(th)-y*Math.sin(th);
      const el=Math.asin(Math.max(-1,Math.min(1,-z/n)))*180/Math.PI;return {elev:Math.round((frontal?-el:el)*10)/10,horiz:Math.round(Math.atan2(der,up)*1800/Math.PI)/10};};
    const w=document.createElement('dialog');w.className='cam cv';w.setAttribute('aria-label',o.titulo||'Peso con cámara');
    w.innerHTML=`<div class="cam-top"><div class="cv-tit"><b class="cv-paso"></b><span class="cv-sub"></span></div><button type="button" class="cam-x" aria-label="Cerrar">${ico('x',2.4)}</button></div>
      <div class="cv-chips">${CHIPS.filter(([k])=>k!=='ref'||conRef0).map(([k,t])=>`<span class="cv-chip" data-k="${k}"><i></i>${t}</span>`).join('')}</div>
      <div class="cam-v cv-v"><video playsinline autoplay muted></video><canvas class="cv-lz"></canvas>
        <div class="cv-ind"><span class="cv-pos" hidden><em>Posición</em><span class="cv-pos-b"><i></i></span><b data-no-tr>0 %</b></span><b class="cv-msg">Abriendo la cámara…</b><span class="cv-prog" hidden><i></i></span></div>
        <span class="cv-tag" hidden><i></i><b data-c="rojo">Ajusta</b><b data-c="ambar">Casi</b><b data-c="verde">Listo</b></span><img class="cv-th" alt="" hidden><span class="cv-firma" data-no-tr>Powered by <b>Rumentis Labs</b></span><span class="cv-dist" hidden><em>Distancia</em><b data-no-tr></b></span><span class="cv-toca">Toca el animal para elegirlo · pellizca para acercar</span><div class="cv-zoom" hidden>${[1,2,3,4].map(z=>`<button type="button" data-z="${z}" data-no-tr>${z}×</button>`).join('')}</div></div>
      <div class="cam-bar cv-bar"><span class="cv-fps"></span><button type="button" class="cam-disp cv-man" aria-label="Tomar la foto ahora" disabled><i></i></button><button type="button" class="cam-lente cv-gira">Girar cámara</button></div>`;
    document.body.appendChild(w);try{w.showModal();}catch(e){w.setAttribute('open','');}
    const $w=s=>w.querySelector(s),distEl=w.querySelector('.cv-dist'),tag=w.querySelector('.cv-tag'),posEl=w.querySelector('.cv-pos'),v=$w('video'),lz=$w('.cv-lz'),msg=$w('.cv-msg'),sub=$w('.cv-sub'),prog=$w('.cv-prog'),fps=$w('.cv-fps'),man=$w('.cv-man'),th=$w('.cv-th');
    const parar=()=>{if(stream)stream.getTracks().forEach(t=>t.stop());stream=null;if(arUsa){arUsa=false;try{Ar.detener();}catch(e){}}};
    const fin=r=>{if(!activo)return;activo=false;clearInterval(tick);try{window.removeEventListener('devicemotion',onMot);}catch(e){}parar();try{wl&&wl.release();}catch(e){}try{w.close();}catch(e){}w.remove();fin0(r);};
    w.addEventListener('cancel',e=>{e.preventDefault();fin(null);});
    $w('.cam-x').onclick=()=>fin(null);
    const pintarPaso=()=>{const p=pasos[ip];conRef=refDe(p);{const c=w.querySelector('.cv-chip[data-k="ref"]');if(c)c.hidden=!conRef;}$w('.cv-paso').textContent=pasos.length>1?`Paso ${ip+1} de ${pasos.length} · ${p.nombre}`:p.nombre;sub.textContent=p.instr;};
    pintarPaso();
    /* ---------- ARCore por debajo (o.ar y el puente "Ar"): la sesión nativa tiene la cámara y manda cuadros chicos
       con lo que sabe de cada uno (suelo, inclinación, profundidad); se dibujan en un lienzo y el video muestra ese
       lienzo, así todo lo demás (silueta en vivo, indicadores, foto sola) sigue igual. Las fotos para medir las toma
       ARCore a resolución completa. Si ARCore no arranca, la cámara normal y el sensor de movimiento. ---------- */
    const arPide=!!(o.ar&&window.Ar&&Ar.iniciar);let arUsa=false,arFallo=null,arMeta=null;const arEsp=[];
    window.arFoto=j=>{let x=null;try{x=JSON.parse(j);}catch(e){}const f=arEsp.shift();if(f)f(x);};
    const fotoAR=()=>new Promise(ok=>{let hecho=false;const fin_=x=>{if(!hecho){hecho=true;ok(x);}};arEsp.push(fin_);try{Ar.foto();}catch(e){fin_(null);}setTimeout(()=>fin_(null),9000);});
    // la foto de ARCore viene sin girar: se gira rot grados al abrirla (como el cuadro en vivo)
    const cargarFoto=async(id,rot)=>{try{const u=await Fotos.url(id);if(!u)return null;const im=new Image();im.src=u;await im.decode();rot=((rot|0)%360+360)%360;
      const w0=im.naturalWidth,h0=im.naturalHeight,c=document.createElement('canvas');c.width=rot%180?h0:w0;c.height=rot%180?w0:h0;const g=c.getContext('2d');
      g.setTransform(...(rot===90?[0,1,-1,0,c.width,0]:rot===180?[-1,0,0,-1,c.width,c.height]:rot===270?[0,-1,1,0,0,c.height]:[1,0,0,1,0,0]));g.drawImage(im,0,0);return c;}catch(e){return null;}};
    // listo para medir: ARCore ubicado y con el suelo encontrado (o con profundidad)
    const arListo=()=>!!(arMeta&&arMeta.sigue&&(arMeta.alto>0||arMeta.pd||arMeta.prof));
    // la distancia (m) por el suelo de ARCore, con la caja de la silueta (fracciones del cuadro)
    const distAR=(n,m)=>{if(!m||!(m.alto>0)||!n)return null;const p=-m.elev*Math.PI/180,a=p+Math.atan((n[3]*m.H-m.cy)/m.fy);return a>.035&&a<1.4?m.alto/Math.tan(a):null;};
    async function abrirAR(){
      const c=document.createElement('canvas');c.width=640;c.height=480;const g=c.getContext('2d');g.fillStyle='#000';g.fillRect(0,0,640,480);arMeta=null;
      /* cada cuadro llega como JPEG sin girar: se pide el siguiente enseguida (el teléfono lo arma mientras este se
         decodifica), se decodifica fuera del hilo principal y se dibuja girado meta.rot grados. Si llega uno nuevo
         mientras se decodifica otro, el viejo se salta. */
      let ultPide=0,deco=0;
      const pedir=()=>{if(!activo)return;const t=performance.now(),d=Math.max(0,33-(t-ultPide));setTimeout(()=>{if(!activo)return;ultPide=performance.now();try{Ar.otro();}catch(e){}},d);};
      let ultLlega=performance.now();
      // si un cuadro se pierde en el camino, se vuelve a pedir (la cámara no se queda congelada)
      const vig=setInterval(()=>{if(!activo||arFallo){clearInterval(vig);return;}if(performance.now()-ultLlega>1500){ultLlega=performance.now();try{Ar.otro();}catch(e){}}},500);
      window.arCuadro=async(b64,meta)=>{if(!activo)return;ultLlega=performance.now();pedir();const mi=++deco;
        try{const s=atob(b64),u=new Uint8Array(s.length);for(let i=0;i<s.length;i++)u[i]=s.charCodeAt(i);
          const bm=await createImageBitmap(new Blob([u],{type:'image/jpeg'}));if(mi!==deco||!activo){try{bm.close();}catch(e){}return;}
          const rot=((meta&&meta.rot)|0)%360,Wc=rot%180?bm.height:bm.width,Hc=rot%180?bm.width:bm.height;
          if(c.width!==Wc||c.height!==Hc){c.width=Wc;c.height=Hc;}
          g.setTransform(...(rot===90?[0,1,-1,0,Wc,0]:rot===180?[-1,0,0,-1,Wc,Hc]:rot===270?[0,-1,1,0,0,Hc]:[1,0,0,1,0,0]));g.drawImage(bm,0,0);g.setTransform(1,0,0,1,0,0);
          try{bm.close();}catch(e){}arMeta=meta;}catch(e){}};
      const r=await new Promise(ok=>{let hecho=false;const fin_=x=>{if(!hecho){hecho=true;ok(x);}};window.arIniciado=j=>{let x={};try{x=JSON.parse(j);}catch(e){}fin_(x);};
        try{Ar.iniciar(JSON.stringify({lado:960}));}catch(e){fin_({error:'puente'});}setTimeout(()=>fin_({error:'tiempo'}),15000);});
      if(!r.ok)throw new Error(r.instalar?'instalar':r.error||'ar');
      try{Ar.otro();}catch(e){}const t0=performance.now();while(!arMeta&&activo&&performance.now()-t0<8000)await new Promise(z=>setTimeout(z,100));
      if(!arMeta){try{Ar.detener();}catch(e){}throw new Error('sin-cuadros');}
      stream=c.captureStream(30);v.srcObject=stream;try{await v.play();}catch(e){}arUsa=true;w.classList.remove('espejo');$w('.cv-gira').hidden=true;
    }
    async function abrirCam(){parar();
      if(arPide&&!arFallo){try{await abrirAR();iniZoom();return;}catch(e){arFallo=String(e&&e.message||e);arUsa=false;
        toast(arFallo==='instalar'?'Instala "Servicios de Google Play para RA" y vuelve a medir. Mientras, se mide con el sensor de movimiento.':'ARCore no arrancó: se mide con el sensor de movimiento.',5000);}}
      const s=await Fotos.camara(frontal);
      if(!activo){s.getTracks().forEach(t=>t.stop());return;}   // se cerró mientras abría
      stream=s;v.srcObject=s;try{await v.play();}catch(e){}w.classList.toggle('espejo',frontal);zona=null;iniZoom();}
    let zona=null;
    // la cámara con que se midió (resolución, zoom, lente): queda en el registro del laboratorio
    const camInfo=()=>{try{const t=stream&&stream.getVideoTracks()[0];if(!t)return null;const g=t.getSettings?t.getSettings():{},o={lente:String(t.label||'').slice(0,60)||null,frontal};
      for(const k of ['width','height','frameRate','zoom','focusDistance','focusMode','exposureMode','whiteBalanceMode','aspectRatio'])if(g[k]!=null)o[k]=typeof g[k]==='number'?Math.round(g[k]*100)/100:g[k];
      o.vw=v.videoWidth;o.vh=v.videoHeight;return o;}catch(e){return null;}};
    /* zoom: con ARCore, recorte del centro en el teléfono (Ar.zoom); sin ARCore, el zoom de la cámara si lo tiene. Al
       cambiar, el sujeto se vuelve a buscar (la imagen cambió de escala). Botones 1× a 4× y pellizco con dos dedos. */
    let zoomAct=1,zoomMax=1,zoomCap=null;const zEl=$w('.cv-zoom');
    const pintarZoom=()=>{zEl.hidden=zoomMax<1.5;for(const b of zEl.querySelectorAll('button')){const z=+b.dataset.z;b.hidden=z>zoomMax+.01;b.setAttribute('aria-pressed',String(Math.abs(z-zoomAct)<.25));}};
    const ponerZoom=(z,guardar=true)=>{z=Math.round(Math.max(1,Math.min(zoomMax,z))*20)/20;if(Math.abs(z-zoomAct)<.02)return;zoomAct=z;
      if(arUsa){try{Ar.zoom(z);}catch(e){}}
      else if(zoomCap){const t=stream&&stream.getVideoTracks()[0];try{t&&t.applyConstraints({advanced:[{zoom:Math.min(zoomCap.max,zoomCap.min*z)}]}).catch(()=>{});}catch(e){}}
      pista=null;zona=null;hist=[];prev=null;buenos=[];caja=null;pintarZoom();
      if(guardar)try{localStorage.setItem('rumentis-pc-zoom',String(z));}catch(e){}};
    const iniZoom=()=>{zoomAct=1;zoomCap=null;
      if(arUsa)zoomMax=typeof Ar.zoom==='function'?4:1;
      else{let c=null;try{const t=stream&&stream.getVideoTracks()[0];c=t&&t.getCapabilities?t.getCapabilities().zoom:null;}catch(e){}
        zoomCap=c&&c.max>c.min?{min:c.min||1,max:c.max}:null;zoomMax=zoomCap?Math.min(4,zoomCap.max/zoomCap.min):1;}
      if(arUsa&&zoomMax>1)try{Ar.zoom(1);}catch(e){}
      let z0=1;try{z0=+localStorage.getItem('rumentis-pc-zoom')||1;}catch(e){}pintarZoom();if(z0>1)ponerZoom(z0,false);};
    zEl.addEventListener('click',e=>{const b=e.target.closest('button[data-z]');if(b){e.stopPropagation();ponerZoom(+b.dataset.z);}});
    $w('.cv-gira').onclick=async()=>{frontal=!frontal;try{await abrirCam();}catch(e){frontal=!frontal;toast('No se pudo abrir esa cámara.');try{await abrirCam();}catch(e2){}}};
    try{await abrirCam();}catch(e){msg.textContent='No se pudo abrir la cámara. Da permiso de cámara a la app en los ajustes del teléfono.';sub.textContent='';w.classList.add('sin');return;}
    if(!activo)return;
    try{if(navigator.wakeLock)wl=await navigator.wakeLock.request('screen');}catch(e){}
    msg.textContent='Cargando el modelo de visión…';w.classList.add('carga');
    let rap=null;try{rap=await Vision.motor(o.clase===Vision.PERSONA?'rapidoP':'rapido');}catch(e){if(activo){msg.textContent='No se pudo cargar el modelo de visión en este teléfono.';w.classList.remove('carga');}return;}
    // el preciso se carga mientras tanto: lo necesita la primera foto; después, los de pose (video y fotos)
    const persona=o.clase===Vision.PERSONA,esq=Vision.ESQUELETO[persona?'persona':'animal'];
    const preP=PER?Promise.reject(new Error('personal')):Vision.motor('preciso');preP.catch(()=>{});
    // pose: en personas el rápido en el video y el preciso en las fotos (se carga después del rápido); en ganado el mismo
    let poseV=null;const poseVP=Vision.motor(persona?'cuerpoV':'animal');poseVP.then(m=>{poseV=m;}).catch(()=>{});
    const poseFP=persona?poseVP.catch(()=>{}).then(()=>Vision.motor('cuerpo')):poseVP;poseFP.catch(()=>{});
    const espejo=persona?Vision.ESPEJO:Vision.ESPEJO_ANIMAL;
    if(!activo)return;w.classList.remove('carga');
    const R=rap.R,cr=document.createElement('canvas');cr.width=cr.height=R;const xr=cr.getContext('2d',{willReadFrequently:true});
    let W=0,H=0,hist=[],prev=null,pista=null,toque=null,fijo=null,tRef=0,refAnd=false,buenos=[],malos=0,ult=null,tiempos=[],pausa=0,fallas=0,vuelo=0,seq=0,hecho=0,bmpOk=typeof createImageBitmap==='function'?3:0;
    // el tamaño de trabajo del cuadro (hasta 960 px de lado)
    const medidas=()=>{const vw=v.videoWidth,vh=v.videoHeight,k=Math.min(1,REGLAS.lienzo/Math.max(vw,vh)),w2=Math.round(vw*k),h2=Math.round(vh*k);if(w2!==W||h2!==H){W=w2;H=h2;zona=null;}};

    /* ---------- dibujo: 60 cuadros por segundo ---------- */
    const vis={s:null,p:null,color:'rojo',kp:null};let caja=null,kpV=null;
    const rect=()=>{const bw=v.clientWidth,bh=v.clientHeight,vw=v.videoWidth,vh=v.videoHeight;if(!vw||!bw)return null;const k=Math.min(bw/vw,bh/vh);
      return {x:(bw-vw*k)/2,y:(bh-vh*k)/2,w:vw*k,h:vh*k};};
    function dibujar(){
      if(!activo)return;requestAnimationFrame(dibujar);
      const bw=v.clientWidth,bh=v.clientHeight,dpr=Math.min(2,window.devicePixelRatio||1);
      if(lz.width!==Math.round(bw*dpr)||lz.height!==Math.round(bh*dpr)){lz.width=Math.round(bw*dpr);lz.height=Math.round(bh*dpr);}
      const x=lz.getContext('2d');x.setTransform(dpr,0,0,dpr,0,0);x.clearRect(0,0,bw,bh);const Rr=rect();if(!Rr||!W)return;
      const s=vis.s,p=vis.p,col=COLOR[vis.color];
      if(p&&p.ok)trazar(x,p,'255,255,255',Rr.x,Rr.y,Rr.w,Rr.h,W,H,null,{relleno:.08,linea:1.5});
      if(!s||!s.ok){caja=null;tag.hidden=true;return;}
      // la caja que se ve se acerca a la del último resultado: el movimiento se ve continuo
      if(!caja)caja=s.n.slice();else for(let i=0;i<4;i++)caja[i]+=(s.n[i]-caja[i])*.35;
      trazar(x,s,col,Rr.x,Rr.y,Rr.w,Rr.h,W,H,caja,{relleno:vis.color==='verde'?.2:.14,linea:1.75});
      // los puntos del cuerpo se deslizan hacia el último resultado (y se quitan si ya es viejo)
      // (se deslizan hacia el último resultado; ya no siguen la caja de la silueta: si la silueta se pegaba a algo,
      // los arrastraba fuera del cuerpo)
      const kp=vis.kp&&performance.now()-vis.kp.t<1500?vis.kp.p:null;
      if(kp){if(!kpV||kpV.length!==kp.length)kpV=kp.slice();else for(let i=0;i<kp.length;i++)kpV[i]+=(kp[i]-kpV[i])*(i%3===2?1:.7);
        puntos(x,kpV,esq,Rr.x,Rr.y,Rr.w,Rr.h,Math.max(2.5,Math.min(4,Rr.w/110)));}else kpV=null;
      // el recuadro (con 8 px de aire alrededor del cuerpo) y la etiqueta de estado encima
      const g=12,X0=Rr.x+caja[0]*Rr.w-g,Y0=Rr.y+caja[1]*Rr.h-g,X1=Rr.x+caja[2]*Rr.w+g,Y1=Rr.y+caja[3]*Rr.h+g;
      recuadro(x,X0,Y0,X1,Y1,col,vis.color==='verde');
      tag.hidden=false;tag.dataset.c=vis.color;// (con la cámara frontal el lienzo va en espejo y la etiqueta no: se pone sobre la esquina que se ve a la izquierda)
      const tx=w.classList.contains('espejo')?bw-X1:X0;tag.style.transform=`translate(${Math.round(Math.max(4,tx))}px,${Math.round(Math.max(4,Y0-26))}px)`;
    }
    requestAnimationFrame(dibujar);
    function chips(E){for(const el of w.querySelectorAll('.cv-chip')){const s=E[el.dataset.k];el.dataset.s=s==null?'':s?'ok':'no';}}
    // la foto que se guarda: el cuadro con el contorno de la silueta
    // (recortada alrededor del sujeto, para que se vea grande)
    function foto(fc,s,ref,kp){const Z=(s&&s.ok&&zonaDe([s.n,ref&&ref.ok?ref.n:null],fc.width,fc.height,{margen:.12,min:.3,amin:.6,amax:1.6}))||{x:0,y:0,w:fc.width,h:fc.height};
      const c=document.createElement('canvas'),k=Math.min(1,720/Math.max(Z.w,Z.h));c.width=Math.round(Z.w*k);c.height=Math.round(Z.h*k);
      const x=c.getContext('2d');x.drawImage(fc,Z.x,Z.y,Z.w,Z.h,0,0,c.width,c.height);const X=-Z.x*k,Y=-Z.y*k,Wd=fc.width*k,Hd=fc.height*k,lw=Math.max(2,c.width/260);
      if(ref&&ref.ok)trazar(x,ref,'255,255,255',X,Y,Wd,Hd,fc.width,fc.height,null,{relleno:.08,linea:lw*.75});
      trazar(x,s,COLOR.verde,X,Y,Wd,Hd,fc.width,fc.height,null,{relleno:.22,linea:lw});
      if(kp){const P=kp.slice();for(let i=0;i<P.length;i+=3){P[i]/=fc.width;P[i+1]/=fc.height;}puntos(x,P,esq,X,Y,Wd,Hd,lw*1.4);}
      const fs=Math.max(10,Math.round(c.width/34));x.font=`700 ${fs}px sans-serif`;x.textAlign='right';x.textBaseline='bottom';x.shadowColor='rgba(0,0,0,.6)';x.shadowBlur=4;x.fillStyle='rgba(255,255,255,.85)';x.fillText(MARCA,c.width-fs*.6,c.height-fs*.5);
      return c.toDataURL('image/jpeg',.85);}
    /* medida precisa de una foto capturada (en segundo plano), sobre un recorte alrededor del sujeto (y de la persona
       de referencia): el modelo ve el cuerpo con más detalle. Si el recorte lo corta, se mide la foto completa. Si no
       sale, se queda la del modelo rápido. */
    // tres fotos por ángulo (se promedian las 9 combinaciones: menos ruido en cada medida); dos en teléfonos muy lentos
    // (el modelo preciso tardó más de 4 s la vez pasada)
    let nTomas=REGLAS.tomas;try{if(+localStorage.getItem('rumentis-pc-msp')>4000)nTomas=2;}catch(e){}
    const tPre=[];
    async function precisa(fc,cajas,cR=conRef0,req=conRef){
      // cR: también busca a la persona de referencia (si está, se mide para comparar); req: sin ella la foto no sirve
      try{const pre=await preP,t0=performance.now(),c=document.createElement('canvas');c.width=c.height=pre.R;const x=c.getContext('2d',{willReadFrequently:true});
        // con la pista del sujeto (y de la persona de referencia) que se venía siguiendo en el video
        // (y con el punto del animal que se tocó: entre varios, el que lo contiene)
        const cl=Z=>{const a=enZona(cajas[0],Z,fc.width,fc.height),q=enZonaPt(cajas.pt,Z,fc.width,fc.height),b=cR?enZona(cajas[1],Z,fc.width,fc.height):null,sj=a?{c:o.clase,p:a,pt:q}:o.clase;return cR?[sj,b?{c:Vision.PERSONA,p:b}:Vision.PERSONA]:[sj];};
        const una=async Z=>{x.drawImage(fc,Z.x,Z.y,Z.w,Z.h,0,0,pre.R,pre.R);const r=await pre.correr({px:x.getImageData(0,0,pre.R,pre.R).data},cl(Z));
          return {s:aCuadro(r.suj[o.clase],r.G,Z,fc.width,fc.height),p:cR?aCuadro(r.suj[Vision.PERSONA],r.G,Z,fc.width,fc.height):null};};
        const Zr=zonaDe(cajas,fc.width,fc.height,{margen:.1,min:.3,amin:.5,amax:2}),todo={x:0,y:0,w:fc.width,h:fc.height};
        let r=await una(Zr||todo);
        if(Zr&&((r.s&&r.s.cortada)||(r.p&&r.p.cortada)||!(r.s&&r.s.ok)))r=await una(todo);
        tPre.push(performance.now()-t0);
        if(!r.s||!r.s.ok||(req&&!refOk(r.p,fc.width,fc.height)))return null;
        // la silueta de la foto tiene que ser el mismo sujeto que estaba verde en el video: si el modelo preciso se quedó
        // con otra cosa (otro animal, una persona, algo del fondo), esa foto no se usa
        if(cajas[0]&&iou(r.s.n,cajas[0])<.4){r.s.otro=true;return null;}
        if(r.p&&!refOk(r.p,fc.width,fc.height))r.p=null;
        // (la silueta de la foto no se limpia con los puntos: medido contra siluetas a mano, el modelo preciso sale
        // mejor sin tocar, también en escenas de casa; limpiarla le quitaba cabeza, pies o brazos)
        r.kp=await posePx(fc,r.s.n);
        return r;}catch(e){console.warn(e);return null;}}
    /* los puntos del cuerpo en una foto (con el modelo de pose de fotos): recorte alrededor de la caja n (fracciones),
       con 25 % de margen y la proporción del modelo; devuelve x, y en píxeles de la foto y la confianza */
    const tPose=[];
    async function posePx(fc,n){
      try{const m=await poseFP,t0=performance.now(),Z=recortePose(n,fc.width,fc.height,m),c=document.createElement('canvas');c.width=m.W;c.height=m.H;
        const x=c.getContext('2d',{willReadFrequently:true});x.fillStyle='#000';x.fillRect(0,0,m.W,m.H);x.drawImage(fc,Z.x,Z.y,Z.w,Z.h,0,0,m.W,m.H);
        const P=(await m.correr({px:x.getImageData(0,0,m.W,m.H).data},[])).kp;
        // la misma foto en espejo: cada punto se promedia con su par del otro lado (izquierda ↔ derecha)
        x.setTransform(-1,0,0,1,m.W,0);x.fillRect(0,0,m.W,m.H);x.drawImage(fc,Z.x,Z.y,Z.w,Z.h,0,0,m.W,m.H);x.setTransform(1,0,0,1,0,0);
        const Q=(await m.correr({px:x.getImageData(0,0,m.W,m.H).data},[])).kp;
        if(Q&&Q.length===P.length)for(let i=0;i<P.length/3;i++){const j=espejo[i]??i;P[3*i]=(P[3*i]+1-Q[3*j])/2;P[3*i+1]=(P[3*i+1]+Q[3*j+1])/2;P[3*i+2]=(P[3*i+2]+Q[3*j+2])/2;}
        for(let i=0;i<P.length;i+=3){P[i]=Z.x+P[i]*Z.w;P[i+1]=Z.y+P[i+1]*Z.h;}
        tPose.push(performance.now()-t0);return P;}catch(e){console.warn(e);return null;}}
    // las fotos de un ángulo se toman todas antes de avisar (el pitido hace que la persona se mueva)
    let capturando=false;
    async function capturar(lista,manual){
      if(capturando)return;capturando=true;pausa=Infinity;
      const p=pasos[ip],u=lista[lista.length-1],cajas=[u.s.n,u.p&&u.p.ok?u.p.n:null];cajas.pt=ptSel();
      // con cámaras enlazadas, todas toman sus fotos en este mismo instante
      const disp=enl()?ENL.disparar():null;
      const cuadro=()=>{const fc=document.createElement('canvas');fc.width=W;fc.height=H;fc.getContext('2d').drawImage(v,0,0,W,H);return fc;};
      // la foto a la resolución completa de la cámara (solo la primera toma): para entrenar el modelo con los datos de la finca
      const cuadroHD=()=>{if(!v.videoWidth)return null;try{const c=document.createElement('canvas');c.width=v.videoWidth;c.height=v.videoHeight;c.getContext('2d').drawImage(v,0,0);return c;}catch(e){return null;}};
      const med={};for(const k of ['alto','ancho','area','banda','ratio','torsoN','score','refAlto'])med[k]=mediana(lista.map(z=>z.med[k]));med.W=W;med.H=H;
      let cap;
      if(arUsa){
        // con ARCore: las tomas son fotos completas de la sesión, cada una con su profundidad, suelo e intrínsecos
        // las fotos se piden todas a la vez (cuadros seguidos de la cámara); el teléfono las arma una tras otra
        const J=await Promise.all(Array.from({length:nTomas},()=>fotoAR()));if(!activo)return;
        const F=[];for(const j of J){if(!j||!j.foto)continue;const fc=await cargarFoto(j.foto,j.rot);try{Fotos.borrar(j.foto);}catch(e){}if(fc)F.push({fc,arc:j});}
        if(!F.length){capturando=false;pausa=performance.now()+800;msg.textContent='No se pudo tomar la foto. Otra vez…';return;}
        cap={id:p.id,nombre:p.nombre,manual:!!manual,zoom:zoomAct,ori:{elev:F[0].arc.elev,horiz:F[0].arc.ladeo},ts:Date.now(),rapido:med,fc:F[0].fc,hd:F[0].fc,s:u.s,pr:u.p,banda:p.banda,ar:true,
          tomas:F.map(f=>({fc:f.fc,arc:f.arc,pend:precisa(f.fc,cajas)}))};
      }else{
        const fc=cuadro();cap={id:p.id,nombre:p.nombre,manual:!!manual,zoom:zoomAct,ori:telOri(),ts:Date.now(),rapido:med,fc,hd:cuadroHD(),s:u.s,pr:u.p,banda:p.banda,tomas:[{fc,pend:precisa(fc,cajas)}]};
        // las demás tomas, unos milisegundos después (todavía quieto): el modelo preciso las mide todas y se promedian
        for(let t=1;t<nTomas;t++){await new Promise(z=>setTimeout(z,REGLAS.entre));if(!activo)return;const f2=cuadro();cap.tomas.push({fc:f2,pend:precisa(f2,cajas)});}
      }
      const fc=cap.fc;
      caps.push(cap);capturando=false;
      try{navigator.vibrate&&navigator.vibrate(120);}catch(e){}beep();
      w.classList.add('flash');setTimeout(()=>w.classList.remove('flash'),260);
      const kv=!arUsa&&vis.kp&&performance.now()-vis.kp.t<800?vis.kp.p.slice():null;if(kv)for(let i=0;i<kv.length;i+=3){kv[i]*=W;kv[i+1]*=H;}
      th.src=foto(fc,u.s,u.p,kv);th.hidden=false;buenos=[];prev=null;ult=null;tiempos=[];prog.hidden=true;zona=null;vis.s=vis.p=vis.kp=null;
      if(disp){
        msg.textContent='Recibiendo las fotos de los otros teléfonos…';w.dataset.estado='';chips({});
        const r=await disp.fotos;if(!activo)return;const hechos=new Set();
        for(const [rol,L] of Object.entries(r.fotos)){const R=Enlace.ROLES[rol],fs=(L||[]).filter(Boolean);if(!R||!fs.length)continue;
          const tomas=[];for(const f of fs){const fc2=await deFoto(f.blob);if(fc2)tomas.push({fc:fc2,pend:precisa(fc2,[],R.ref,R.ref)});}
          if(tomas.length){caps.push({id:rol,nombre:R.nombre,remoto:true,banda:R.banda,tomas,fc:tomas[0].fc});hechos.add(rol);}}
        // lo que ya tomó otro teléfono no se pide aquí
        for(let j=pasos.length-1;j>ip;j--)if(hechos.has(pasos[j].id))pasos.splice(j,1);
        const faltan=[...r.faltan,...r.nada].map(x=>Enlace.ROLES[x]&&Enlace.ROLES[x].nombre).filter(Boolean);
        if(faltan.length)toast(`No llegaron las fotos de: ${faltan.join(', ')}`,3500);
      }
      if(ip+1>=pasos.length){terminar();return;}
      ip++;pintarPaso();msg.textContent=pasos[ip].girar;chips({});hist=[];pista=null;toque=null;fijo=null;posEl.hidden=true;w.dataset.estado='';pausa=performance.now()+1500;
    }
    async function terminar(){
      pausa=Infinity;man.disabled=true;chips({});w.dataset.estado='';sub.textContent='';w.classList.add('carga');
      const todas=caps.flatMap(c=>c.tomas);let listas=0;
      const avance=()=>{msg.textContent=`Midiendo… ${listas}/${todas.length}`;};avance();
      todas.forEach(t=>t.pend.then(()=>{listas++;if(activo&&listas<todas.length)avance();}));
      const out=[];
      for(const c of caps){const tomas=[];let img=null;
        for(const t of c.tomas){const r=await t.pend;if(!activo)return;if(!r)continue;
          const med=medir(r.s,W,H,c.banda);if(r.p)med.refAlto=medir(r.p,W,H).alto;tomas.push({arc:t.arc||null,med,sil:r.s,ref:r.p,kp:r.kp||null,W:t.fc.width,H:t.fc.height,fc:t.fc});
          if(!img)img=foto(t.fc,r.s,r.p,r.kp);}
        const fuente=tomas.length?'preciso':'rapido';
        if(!tomas.length){if(c.remoto)continue;tomas.push({med:c.rapido,sil:c.s,ref:c.pr||null,W:c.fc.width,H:c.fc.height});}
        out.push({id:c.id,nombre:c.nombre,manual:c.manual,remoto:!!c.remoto,zoom:c.zoom||1,ori:c.ori||null,ts:c.ts||null,tomas,med:tomas[0].med,sil:tomas[0].sil,ref:tomas[0].ref,W:tomas[0].W,H:tomas[0].H,fuente,hd:c.hd||null,img:img||foto(c.fc,c.s,c.pr)});}
      try{if(tPre.length)localStorage.setItem('rumentis-pc-msp',String(Math.round(mediana(tPre))));}catch(e){}
      // rendimiento de esta medición (para el laboratorio)
      out.stats={fps:fpsL.length?fpsL.reduce((a,b)=>a+b,0)/fpsL.length:0,cuadros:procesados,msRapido:msR.length?msR.reduce((a,b)=>a+b,0)/msR.length:0,
        msPreciso:tPre.length?tPre.reduce((a,b)=>a+b,0)/tPre.length:0,msPose:tPose.length?tPose.reduce((a,b)=>a+b,0)/tPose.length:0,seg:(performance.now()-tIni)/1000,motor:rap.modo+'×'+rap.n+(arUsa?'+arcore':''),arcore:arUsa?{profundidad:!!(arMeta&&arMeta.prof)}:arPide?{fallo:arFallo}:null,tomas:todas.length,cam:camInfo()};
      fin(out);
    }
    man.onclick=()=>{if(!ult)return toast('Que se vea completo para tomar la foto.');const l=buenos.length?buenos:[ult];capturar(l.slice(-SEG),true);};
    if(PER){
      man.hidden=true;$w('.cv-paso').innerHTML=`<span>${pasos[0].nombre}</span> · <span>${o.titulo||''}</span>`;
      sub.innerHTML=PER.admin?`<span>Enlazada con</span> <span data-no-tr>${esc(PER.admin)}</span>. <span>${pasos[0].instr}</span>`:`<span>${pasos[0].instr}</span>`;
      PER.alDisparo(async(id,res)=>{
        if(!id){if(res){PER.msg=res;setTimeout(()=>{PER.msg='';},4000);}return;}
        if(!activo||!v.videoWidth){PER.enviar(id,[]);return;}
        const fotos=[];medidas();
        for(let t=0;t<nTomas;t++){if(t)await new Promise(z=>setTimeout(z,REGLAS.entre));const fc=document.createElement('canvas');fc.width=W;fc.height=H;fc.getContext('2d').drawImage(v,0,0,W,H);
          const blob=await new Promise(z=>fc.toBlob(z,'image/jpeg',.9));if(blob)fotos.push({blob,w:W,h:H});if(t===0){th.src=fc.toDataURL('image/jpeg',.7);th.hidden=false;}}
        try{navigator.vibrate&&navigator.vibrate(120);}catch(e){}beep();w.classList.add('flash');setTimeout(()=>w.classList.remove('flash'),260);
        await PER.enviar(id,fotos);});
      // si se cae la conexión con la administración, la cámara se cierra
      const vig=setInterval(()=>{if(!activo||arFallo){clearInterval(vig);return;}if(!PER.vivo()){clearInterval(vig);msg.textContent='Se perdió la conexión con la administración.';setTimeout(()=>fin(null),2500);}},1000);
    }

    /* ---------- un resultado del modelo rápido ---------- */
    const tIni=performance.now(),msR=[],fpsL=[];let procesados=0,fpsAct=0;
    function procesar(r,Z){
      procesados++;if(msR.length<400)msR.push(r.ms);
      const p=pasos[ip];let s=aCuadro(r.suj[o.clase],r.G,Z,W,H);const pr=conRef0?aCuadro(r.suj[Vision.PERSONA],r.G,Z,W,H):null;
      seguir(s);
      const now=performance.now();
      if(persona&&s&&s.ok&&vis.kp&&now-vis.kp.t<600&&iou(vis.kp.n,s.n)>=.85&&poseBuena(vis.kp.p))s=limpiarSil(s,vis.kp.p,W,H);
      tiempos.push(now);tiempos=tiempos.filter(t=>now-t<2000);
      // (sobre al menos 0.8 s: con varios workers los resultados llegan en ráfagas y una ventana corta exagera)
      if(tiempos.length>2&&now-tiempos[0]>=800){const f=(tiempos.length-1)/((now-tiempos[0])/1000);fpsAct=f;fps.textContent=`${f.toFixed(1)} cuadros/s`;if(fpsL.length<400)fpsL.push(f);}
      // (ms y motor quedan en el diálogo para las pruebas)
      w.dataset.ms=Math.round(r.ms);w.dataset.motor=rap.modo+'×'+rap.n;
      // el cuadro siguiente mira solo la zona del sujeto; si la zona lo cortó (o en ganado falta la persona), completo
      zona=(s&&s.cortada)||(pr&&pr.cortada)||(conRef&&!(pr&&pr.ok))?null:zonaSig([s&&s.ok?s.n:null,pr&&pr.ok?pr.n:null],W,H,zona);
      const ev=revisar(s,pr,W,H,{...p,buscar:o.buscar},prev,caps.length?caps[0].rapido:null,conRef,vis.kp&&now-vis.kp.t<700?vis.kp:null);
      // "quieto" se compara con la mediana de las últimas cajas: un cuadro raro no reinicia la cuenta
      if(ev.caja){hist.push(ev.caja);if(hist.length>3)hist.shift();prev=[0,1,2,3].map(i=>mediana(hist.map(z=>z[i])));}else{hist=[];prev=null;}
      if(ev.dbg)w.dataset.ang=JSON.stringify(ev.dbg);
      posEl.hidden=ev.pos==null;const pp=Math.round((ev.pos||0)*100);posEl.style.setProperty('--p',(ev.pos||0));posEl.lastChild.textContent=`${pp} %`;
      if(arUsa&&ev.E.det&&!arListo())ev.msg=arMeta&&!arMeta.sigue?'Mueve el teléfono despacio para que ARCore reconozca el lugar':'Apunta un momento al suelo para que ARCore lo encuentre';
      const E=ev.E,listo0=E.det&&E.comp&&E.dist&&E.ang&&E.quieto&&(!conRef||E.ref)&&(!arUsa||arListo());
      // la administración espera a que las cámaras enlazadas también estén listas
      const faltan=listo0&&enl()&&!Enlace.listas()?Enlace.esperando():[],listo=listo0&&!faltan.length&&!PER;
      if(faltan.length)ev.msg=`Esperando a: ${faltan.join(', ')}`;
      const color=!E.det?'rojo':listo||(listo0&&(PER||faltan.length))?'verde':(E.comp&&E.dist?'ambar':'rojo');
      if(PER){const t=performance.now();if(color!==envio.c||t-envio.t>300){envio={c:color,t};PER.estado({c:color,m:ev.msg,p:+(ev.pos||0).toFixed(2)});}}
      const med=E.det?medir(s,W,H,p.banda):null;if(med&&(conRef?E.ref:refOk(pr,W,H)))med.refAlto=medir(pr,W,H).alto;
      // la distancia al animal (con la altura del teléfono y su inclinación), en vivo
      if(arUsa){const D=E.det&&s&&s.n?distAR(s.n,arMeta):null;distEl.hidden=!D;if(D)distEl.lastChild.textContent=`${nf(D,1)} m`;w.dataset.dist=D?D.toFixed(2):'';}
      else if(o.geo){const ori=telOri(),d=E.det&&s&&s.n&&ori?suelo(s.n[3]*H,W,H,ori.elev,o.geo.h,fovZ(o.geo.fov,zoomAct)):null;distEl.hidden=!d;if(d)distEl.lastChild.textContent=`${nf(d.D,1)} m`;w.dataset.dist=d?d.D.toFixed(2):'';}
      ult=E.det&&E.comp&&(!conRef||E.ref)?{s,p:pr,med}:null;man.disabled=!ult;
      vis.s=E.det?s:null;vis.p=pr;vis.color=color;chips(E);w.dataset.estado=color;w.dataset.zona=zona?'si':'no';
      /* un cuadro que falla no reinicia la cuenta si el anterior estaba bien y lo que falló es poco (un temblor de la
         silueta o del ángulo): solo dos seguidos la reinician. Así no cuesta tanto que se tome la foto */
      const casi=!listo&&E.det&&E.comp&&E.dist&&(!conRef||E.ref)&&(ev.pos||0)>=.85;
      if(listo){malos=0;buenos.push({s,p:pr,med});prog.hidden=false;prog.style.setProperty('--p',buenos.length/SEG);msg.textContent='Quieto…';
        if(buenos.length>=SEG)capturar(buenos,false);}
      else if(buenos.length&&casi&&malos<1){malos++;}
      else{buenos=[];malos=0;prog.hidden=true;msg.textContent=PER&&listo0?(PER.msg||'Lista: esperando la señal de la administración'):ev.msg;}
    }
    let envio={c:'',t:0};
    /* ---------- cuadros en vuelo: uno por worker del modelo rápido ---------- */
    async function tomar(Z){
      const k=v.videoWidth/W;
      // (si createImageBitmap falla varias veces seguidas, se usa un lienzo en la página)
      if(bmpOk>0)try{const b=await createImageBitmap(v,Z.x*k,Z.y*k,Z.w*k,Z.h*k,{resizeWidth:R,resizeHeight:R,resizeQuality:'low'});bmpOk=3;return {bmp:b};}catch(e){bmpOk--;}
      xr.drawImage(v,Z.x*k,Z.y*k,Z.w*k,Z.h*k,0,0,R,R);return {px:xr.getImageData(0,0,R,R).data};
    }
    /* ---------- el animal que se toca en la pantalla ----------
       Un toque elige la silueta que está debajo del dedo (en un grupo, la de ese animal) y la deja fija: se sigue a ese
       animal (la caja seguida recorta la mancha del grupo y, cada 1.5 s, el modelo preciso la vuelve a ajustar al animal
       que contiene el punto tocado). Al cambiar de paso se suelta. */
    const ptSel=()=>fijo&&pista?[pista.n[0]+fijo.rel[0]*(pista.n[2]-pista.n[0]),pista.n[1]+fijo.rel[1]*(pista.n[3]-pista.n[1])]:null;
    function tocar(cx,cy){const R=rect(),bb=v.getBoundingClientRect();if(!R||!W)return;let x=(cx-bb.left-R.x)/R.w,y=(cy-bb.top-R.y)/R.h;
      if(w.classList.contains('espejo'))x=1-x;if(!(x>=0&&y>=0&&x<=1&&y<=1))return;
      toque={x,y,t:performance.now()};fijo=null;pista=null;zona=null;hist=[];prev=null;buenos=[];caja=null;tRef=0;
      const h=$w('.cv-toca');if(h)h.hidden=true;try{navigator.vibrate&&navigator.vibrate(15);}catch(e){}}
    function seguir(s){const ah=performance.now();
      if(!(s&&s.ok&&s.n)){if(toque&&ah-toque.t>2500){toque=null;toast('No se encontró un animal ahí: toca encima del animal.');}return;}
      if(toque){const n=s.n,ww=Math.max(1e-3,n[2]-n[0]),hh=Math.max(1e-3,n[3]-n[1]);
        fijo={rel:[Math.min(1,Math.max(0,(toque.x-n[0])/ww)),Math.min(1,Math.max(0,(toque.y-n[1])/hh))]};toque=null;pista={n:n.slice(),t:ah};tRef=0;return;}
      let n=s.n;
      // dentro de un grupo (la mancha se salía de la caja): la caja guarda su tamaño y su centro sigue a la silueta recortada, 10 % por cuadro como mucho
      // (si la mancha entera no es mucho más grande que la caja, es el mismo animal más grande: se acercó o se hizo zoom)
      if(fijo&&s.recortada&&s.exceso>2.2&&pista){const P=pista.n,pw=P[2]-P[0],ph=P[3]-P[1],c0=[(P[0]+P[2])/2,(P[1]+P[3])/2],c1=[(n[0]+n[2])/2,(n[1]+n[3])/2],
        cx=c0[0]+Math.max(-.1*pw,Math.min(.1*pw,c1[0]-c0[0])),cy=c0[1]+Math.max(-.1*ph,Math.min(.1*ph,c1[1]-c0[1]));n=[cx-pw/2,cy-ph/2,cx+pw/2,cy+ph/2];}
      pista={n,t:ah};w.dataset.fijo=fijo?'1':'';if(fijo)refrescar();}
    // el modelo preciso vuelve a encontrar al animal elegido (el que contiene el punto tocado) y ajusta la caja seguida
    async function refrescar(){if(!fijo||!pista||refAnd||capturando||performance.now()-tRef<1500)return;refAnd=true;tRef=performance.now();
      try{const pre=await preP,fc=document.createElement('canvas');fc.width=W;fc.height=H;fc.getContext('2d').drawImage(v,0,0,W,H);
        const q=ptSel(),Z=zonaDe([pista.n],W,H,{margen:.3,min:.3,amin:.5,amax:2})||{x:0,y:0,w:W,h:H},c=document.createElement('canvas');c.width=c.height=pre.R;
        const x=c.getContext('2d',{willReadFrequently:true});x.drawImage(fc,Z.x,Z.y,Z.w,Z.h,0,0,pre.R,pre.R);
        const r=await pre.correr({px:x.getImageData(0,0,pre.R,pre.R).data},[{c:o.clase,p:enZona(pista.n,Z,W,H),pt:enZonaPt(q,Z,W,H)}]);
        let s2=aCuadro(r.suj[o.clase],r.G,Z,W,H);
        // si el recorte lo corta, en el cuadro completo
        if(s2&&s2.cortada){const T={x:0,y:0,w:W,h:H};x.drawImage(fc,0,0,W,H,0,0,pre.R,pre.R);const r2=await pre.correr({px:x.getImageData(0,0,pre.R,pre.R).data},[{c:o.clase,p:pista.n,pt:q}]);s2=aCuadro(r2.suj[o.clase],r2.G,T,W,H);}
        if(fijo&&q&&s2&&s2.ok&&!s2.cortada&&q[0]>=s2.n[0]&&q[0]<=s2.n[2]&&q[1]>=s2.n[1]&&q[1]<=s2.n[3]){const n=s2.n;pista={n:n.slice(),t:performance.now()};
          fijo.rel=[(q[0]-n[0])/Math.max(1e-3,n[2]-n[0]),(q[1]-n[1])/Math.max(1e-3,n[3]-n[1])];}}catch(e){console.warn(e);}
      refAnd=false;}
    // toques y pellizco sobre el video
    {const vv=$w('.cv-v');let pin=null,tq=null;
      vv.addEventListener('touchstart',e=>{if(e.target.closest('.cv-zoom'))return;if(e.touches.length===2){const [a,b]=e.touches;pin={d:Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY)||1,z:zoomAct};tq=null;}
        else if(e.touches.length===1&&!pin){const t=e.touches[0];tq={x:t.clientX,y:t.clientY,t:performance.now()};}},{passive:true});
      vv.addEventListener('touchmove',e=>{if(pin&&e.touches.length===2){const [a,b]=e.touches;ponerZoom(pin.z*Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY)/pin.d);}
        else if(tq&&e.touches[0]&&Math.hypot(e.touches[0].clientX-tq.x,e.touches[0].clientY-tq.y)>14)tq=null;},{passive:true});
      vv.addEventListener('touchend',e=>{if(pin&&e.touches.length<2){if(!e.touches.length)pin=null;tq=null;return;}if(tq&&!e.touches.length&&performance.now()-tq.t<450)tocar(tq.x,tq.y);tq=null;});
      vv.addEventListener('click',e=>{if(e.target.closest('.cv-zoom')||e.sourceCapabilities&&e.sourceCapabilities.firesTouchEvents)return;if(!('ontouchstart' in window))tocar(e.clientX,e.clientY);});}
    async function lanzar(){
      vuelo++;const mi=++seq,paso=ip;medidas();const Z=zona||{x:0,y:0,w:W,h:H};let r=null;
      // la pista: la caja del sujeto que se venía siguiendo (así no salta a otro animal, a otra persona o a algo del fondo)
      const ah=performance.now(),pi=pista&&ah-pista.t<(fijo?4000:1200)?enZona(pista.n,Z,W,H):null;
      const ent=toque?{c:o.clase,pt:enZonaPt([toque.x,toque.y],Z,W,H)}:pi?{c:o.clase,p:pi,fijo:!!fijo}:o.clase,clP=clases.map(c=>c===o.clase?ent:c);
      try{r=await rap.correr(await tomar(Z),clP);fallas=0;}catch(e){console.warn(e);fallas++;}
      vuelo--;
      if(!activo)return;
      if(fallas>=3){msg.textContent='No se pudo cargar el modelo de visión en este teléfono.';w.dataset.estado='';man.disabled=true;clearInterval(tick);return;}
      // un resultado viejo (llegó después de uno más nuevo, o de antes de cambiar de paso) no se usa
      if(r&&mi>hecho&&paso===ip&&performance.now()>=pausa){hecho=mi;procesar(r,Z);}
      // sin worker el modelo corre en la página: se le da un respiro para que la pantalla responda
      if(rap.modo!=='worker')await new Promise(z=>setTimeout(z,Math.max(20,Math.min(200,(r&&r.ms||60)*.3))));
      bombear();
    }
    function bombear(){
      if(!activo||fallas>=3)return;
      while(vuelo<rap.n&&v.videoWidth&&v.readyState>=2&&performance.now()>=pausa)lanzar();
    }
    /* ---------- los puntos del cuerpo en el video ----------
       Un cuadro a la vez, recortado alrededor de la última silueta. En teléfonos con pocos núcleos (o con el modelo de
       animales, más pesado) se deja un respiro entre uno y otro para no quitarle cuadros a la silueta. */
    // (con dos workers, los cuadros se reparten parejo: uno cada medio tiempo del modelo)
    let poseVuelo=0,poseT=0,poseSeq=0,poseHecho=0,poseMs=0;const poseRespiro=persona?(NUCLEOS()>=6?0:60):250;
    /* suavizado de los puntos al llegar (como un filtro One Euro): si un punto casi no se movió (menos de 1.2 % del alto
       del cuerpo) se mezcla con el anterior (55 % lo nuevo) y deja de temblar; si se movió más, lo sigue de inmediato */
    function suavizar(P,A,alto){if(!A||A.length!==P.length)return P;
      for(let i=0;i<P.length;i+=3){if(A[i+2]<.3||P[i+2]<.3)continue;const dd=Math.hypot((P[i]-A[i])*W,(P[i+1]-A[i+1])*H)/Math.max(1e-6,alto*H),a=Math.min(1,.55+dd/.012*.45);
        P[i]=A[i]+(P[i]-A[i])*a;P[i+1]=A[i+1]+(P[i+1]-A[i+1])*a;}return P;}
    /* la silueta manda: si su video baja de 15 cuadros/s (el teléfono no da para todo), los puntos esperan más entre uno
       y otro (hasta 150 ms); el dibujo los desliza igual, así que se siguen viendo fluidos */
    const esperaPose=()=>fpsAct>0&&fpsAct<15?Math.min(150,(15-fpsAct)*25):0;
    async function bombearPose(){
      if(!activo||!poseV||poseVuelo>=poseV.n||!vis.s||!vis.s.ok||!v.videoWidth||performance.now()<pausa||performance.now()-poseT<Math.max(poseRespiro,poseMs/poseV.n,esperaPose()))return;
      poseVuelo++;poseT=performance.now();const paso=ip,mi=++poseSeq;
      // el recorte sale de los puntos del cuadro anterior (seguimiento, como en los trajes de captura); si no hay o son
      // viejos, de la silueta. Así, si la silueta se pega a algo, los puntos no se van con ella.
      const kb=vis.kp&&performance.now()-vis.kp.t<400&&poseBuena(vis.kp.p)?cajaPuntos(vis.kp.p):null,n=kb&&iou(kb,vis.s.n)>.3?kb:vis.s.n.slice();
      try{const Z=recortePose(n,W,H,poseV),k=v.videoWidth/W;let e;
        try{e={bmp:await createImageBitmap(v,Z.x*k,Z.y*k,Z.w*k,Z.h*k,{resizeWidth:poseV.W,resizeHeight:poseV.H,resizeQuality:'medium'})};}
        catch(er){const c=document.createElement('canvas');c.width=poseV.W;c.height=poseV.H;const x=c.getContext('2d',{willReadFrequently:true});x.fillStyle='#000';x.fillRect(0,0,c.width,c.height);
          x.drawImage(v,Z.x*k,Z.y*k,Z.w*k,Z.h*k,0,0,c.width,c.height);e={px:x.getImageData(0,0,c.width,c.height).data};}
        const r=await poseV.correr(e,[]),P=r.kp;poseMs=poseMs?poseMs*.8+r.ms*.2:r.ms;w.dataset.msPose=Math.round(poseMs);
        for(let i=0;i<P.length;i+=3){P[i]=(Z.x+P[i]*Z.w)/W;P[i+1]=(Z.y+P[i+1]*Z.h)/H;}
        if(activo&&paso===ip&&performance.now()>=pausa&&mi>poseHecho){poseHecho=mi;suavizar(P,vis.kp&&performance.now()-vis.kp.t<500&&poseBuena(vis.kp.p)?vis.kp.p:null,n[3]-n[1]);vis.kp={p:P,t:performance.now(),n};let c=0;for(let i=2;i<P.length;i+=3)if(P[i]>=.3)c++;w.dataset.puntos=c;}
      }catch(e){console.warn(e);}
      poseVuelo--;if(poseRespiro)setTimeout(bombearPose,poseRespiro);else bombearPose();
    }
    tick=setInterval(()=>{bombear();bombearPose();},60);bombear();
  });
}
/* una foto sin video (la de ARCore): se mide como una toma del video (modelo preciso, la zona del sujeto y los puntos
   del cuerpo, en espejo). fc: lienzo; o.clase (Vision.VACA o Vision.PERSONA); o.banda. Devuelve la toma o null. */
async function medirFoto(fc,o){
  const persona=o.clase===Vision.PERSONA,pre=await Vision.motor('preciso'),W=fc.width,H=fc.height;
  const c=document.createElement('canvas');c.width=c.height=pre.R;const x=c.getContext('2d',{willReadFrequently:true});
  const una=async Z=>{x.drawImage(fc,Z.x,Z.y,Z.w,Z.h,0,0,pre.R,pre.R);const r=await pre.correr({px:x.getImageData(0,0,pre.R,pre.R).data},[o.clase]);return aCuadro(r.suj[o.clase],r.G,Z,W,H);};
  let s=await una({x:0,y:0,w:W,h:H});if(!s||!s.ok)return null;
  // otra pasada en la zona del sujeto, con más detalle
  const Z=zonaDe([s.n],W,H,{margen:.1,min:.3,amin:.5,amax:2});if(Z){const s2=await una(Z);if(s2&&s2.ok&&!s2.cortada)s=s2;}
  let kp=null;
  try{const m=await Vision.motor(persona?'cuerpo':'animal'),esp=persona?Vision.ESPEJO:Vision.ESPEJO_ANIMAL,Zp=recortePose(s.n,W,H,m),d=document.createElement('canvas');d.width=m.W;d.height=m.H;
    const y=d.getContext('2d',{willReadFrequently:true});y.fillStyle='#000';y.fillRect(0,0,m.W,m.H);y.drawImage(fc,Zp.x,Zp.y,Zp.w,Zp.h,0,0,m.W,m.H);
    const P=(await m.correr({px:y.getImageData(0,0,m.W,m.H).data},[])).kp;
    y.setTransform(-1,0,0,1,m.W,0);y.fillRect(0,0,m.W,m.H);y.drawImage(fc,Zp.x,Zp.y,Zp.w,Zp.h,0,0,m.W,m.H);y.setTransform(1,0,0,1,0,0);
    const Q=(await m.correr({px:y.getImageData(0,0,m.W,m.H).data},[])).kp;
    if(P){if(Q&&Q.length===P.length)for(let i=0;i<P.length/3;i++){const j=esp[i]??i;P[3*i]=(P[3*i]+1-Q[3*j])/2;P[3*i+1]=(P[3*i+1]+Q[3*j+1])/2;P[3*i+2]=(P[3*i+2]+Q[3*j+2])/2;}
      for(let i=0;i<P.length;i+=3){P[i]=Zp.x+P[i]*Zp.w;P[i+1]=Zp.y+P[i+1]*Zp.h;}kp=P;}}catch(e){console.warn(e);}
  // la vista con la silueta y los puntos (como la de las tomas del video)
  let img=null;try{const Zv=zonaDe([s.n],W,H,{margen:.12,min:.3,amin:.6,amax:1.6})||{x:0,y:0,w:W,h:H},c2=document.createElement('canvas'),k=Math.min(1,720/Math.max(Zv.w,Zv.h));
    c2.width=Math.round(Zv.w*k);c2.height=Math.round(Zv.h*k);const g=c2.getContext('2d');g.drawImage(fc,Zv.x,Zv.y,Zv.w,Zv.h,0,0,c2.width,c2.height);
    const X=-Zv.x*k,Y=-Zv.y*k,Wd=W*k,Hd=H*k,lw=Math.max(2,c2.width/260);trazar(g,s,COLOR.verde,X,Y,Wd,Hd,W,H,null,{relleno:.22,linea:lw});
    if(kp){const P=kp.slice();for(let i=0;i<P.length;i+=3){P[i]/=W;P[i+1]/=H;}puntos(g,P,Vision.ESQUELETO[persona?'persona':'animal'],X,Y,Wd,Hd,lw*1.4);}
    img=c2.toDataURL('image/jpeg',.85);}catch(e){img=fc.toDataURL('image/jpeg',.8);}
  return {med:medir(s,W,H,o.banda),sil:s,ref:null,kp,W,H,fc,img};
}
window.CamVivo={medirFoto,suelo,escPlano,fovZ,poseBuena,manoBuena,abrir,REGLAS,medir,revisar,zonaSig,zonaDe,torsoN,hombrosN,recortePose,limpiarSil,aCuadro,cajaPuntos};
})();
