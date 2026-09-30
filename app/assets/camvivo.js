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
const CFG=window.RUMENTIS||{};if(!CFG.beta||CFG.app!=='jefe'||!window.Vision)return;

/* reglas generales (las de distancia y ángulo vienen en cada paso) */
// tomas: fotos por ángulo para el modelo preciso (se promedian: menos ruido); separadas `entre` ms
const REGLAS={score:.5,margen:.02,quieto:.9,seguidos:3,lienzo:960,ref:{alto:.25,forma:1.6},tomas:3,entre:140};
const MARCA='Rumentis Labs';
const CHIPS=[['det','Detectado'],['comp','Completo'],['dist','Distancia'],['ang','Ángulo'],['ref','Referencia'],['quieto','Quieto']];
const COLOR={verde:'46,204,113',ambar:'240,191,51',rojo:'231,76,60'};

const iou=(a,b)=>{if(!a||!b)return 0;const x0=Math.max(a[0],b[0]),y0=Math.max(a[1],b[1]),x1=Math.min(a[2],b[2]),y1=Math.min(a[3],b[3]);
  const i=Math.max(0,x1-x0)*Math.max(0,y1-y0),u=(a[2]-a[0])*(a[3]-a[1])+(b[2]-b[0])*(b[3]-b[1])-i;return u>0?i/u:0;};
const mediana=v=>{const s=v.filter(x=>isFinite(x)).sort((a,b)=>a-b),n=s.length;return n?(n%2?s[(n-1)/2]:(s[n/2-1]+s[n/2])/2):NaN;};
const pct=(v,p)=>{const s=v.filter(x=>x>0).sort((a,b)=>a-b);return s.length?s[Math.min(s.length-1,Math.floor(p*(s.length-1)+.5))]:0;};
const dentro=(c,mg)=>c[0]>mg&&c[1]>mg&&c[2]<1-mg&&c[3]<1-mg;

/* la rejilla G×G de una silueta cubre una zona Z del cuadro W×H (todo el cuadro o un recorte): se anota su caja en
   fracciones del cuadro (n), cuántos píxeles mide cada celda (cx, cy) y si toca un borde de la zona que no es borde
   del cuadro (entonces la zona la cortó) */
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
  const v=d.eje==='alto'?c[3]-c[1]:c[2]-c[0];E.dist=v>=d.min&&v<=d.max;
  const ratio=((c[2]-c[0])*W)/((c[3]-c[1])*H);
  // (ref: las medidas de la primera toma; perfil: el tronco debe verse a lo más esa fracción de ancho que de frente)
  const hn=a.hombros?hombrosN(kp,W,H):null;
  const tn=a.perfil&&ref&&ref.torsoN?torsoN(s):0,perfil=(!a.perfil||!ref||!ref.torsoN||tn<=a.perfil*ref.torsoN)&&(hn==null||hn<=a.hombros);
  E.ang=(a.min==null||ratio>=a.min)&&(a.max==null||ratio<=a.max)&&(!a.rel||!ref||ratio<=a.rel*ref.ratio)&&perfil;
  const q=iou(c,prev);E.quieto=q>=REGLAS.quieto;
  /* qué tan cerca está de la posición ideal (0 a 1): cada regla da 1 si se cumple y baja según lo lejos que esté */
  const cerca=x=>Math.max(0,Math.min(1,x));
  const fuera=Math.max(0,REGLAS.margen-c[0],REGLAS.margen-c[1],c[2]-(1-REGLAS.margen),c[3]-(1-REGLAS.margen));
  const kComp=E.comp?1:cerca(1-fuera/.1-(s.cortada?.3:0)),kDist=E.dist?1:cerca(1-(v<d.min?d.min-v:v-d.max)/.3);
  const viol=[a.min!=null&&ratio<a.min?(a.min-ratio)/a.min:0,a.max!=null&&ratio>a.max?(ratio-a.max)/a.max:0,
    a.rel&&ref&&ratio>a.rel*ref.ratio?(ratio-a.rel*ref.ratio)/(a.rel*ref.ratio):0,!perfil&&tn?Math.max(0,(tn-a.perfil*ref.torsoN)/(a.perfil*ref.torsoN)):0,
    hn!=null&&hn>a.hombros?(hn-a.hombros)/.6:0];
  const kAng=E.ang?1:cerca(1-Math.max(...viol)/.5),kRef=conRef?(E.ref?1:.4):1,kQ=cerca((q-.6)/(REGLAS.quieto-.6));
  const pos=kComp*kDist*kAng*kRef*(.8+.2*kQ);
  let msg='';
  if(!E.comp)msg=v>d.max*.95?'Aléjate un poco':'Que se vea el cuerpo completo';
  else if(!E.dist)msg=v<d.min?'Acércate':'Aléjate';
  else if(!E.ang)msg=!perfil&&paso.girarMas?paso.girarMas:paso.girar;
  else if(conRef&&!E.ref)msg=p&&p.ok?'Que la persona de referencia se vea completa y derecha':'Falta la persona de referencia junto al animal';
  else if(!E.quieto)msg='Quieto…';
  return {E,msg,caja:c,pos};
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
  const n=Vision.silueta(M,G,G,G);if(!n.ok)return s;
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
/* el contorno de una silueta como camino en un lienzo que muestra el cuadro W×H en (X,Y,Wd,Hd). `a` (opcional) es la
   caja [x0,y0,x1,y1] donde se quiere ver: el contorno se lleva de su caja real a esa (así se desliza suave) */
function camino(x,s,X,Y,Wd,Hd,W,H,a){
  const c=s.cont;if(!c||c.length<6)return false;const Z=s.Z,n=s.n,b=a||n;
  const sx=(b[2]-b[0])/Math.max(1e-6,n[2]-n[0]),sy=(b[3]-b[1])/Math.max(1e-6,n[3]-n[1]);
  x.beginPath();
  for(let i=0;i<c.length;i+=2){const fx=(Z.x+c[i]*s.cx)/W,fy=(Z.y+c[i+1]*s.cy)/H,px=X+(b[0]+(fx-n[0])*sx)*Wd,py=Y+(b[1]+(fy-n[1])*sy)*Hd;i?x.lineTo(px,py):x.moveTo(px,py);}
  x.closePath();return true;
}
/* los puntos del cuerpo, como un traje de captura de movimiento: puntos blancos que brillan y líneas finas entre
   ellos. P: x, y (fracciones del cuadro), confianza; se dibujan en el lienzo que muestra el cuadro en (X,Y,Wd,Hd). */
// tamaño de cada punto (personas de 133 puntos: la cara y los dedos más chicos, para que no tapen)
const tamPunto=(i,n)=>n<=17?1:i<17?1:i<23?.75:i<91?.38:i===91||i===112?.7:.5;
/* confianza mínima para dibujar un punto: 0.4 en el cuerpo y 0.5 en pies, cara y manos. Medido en COCO (DWPose-s): los
   puntos muy fuera de lugar (a más de 10 % de la estatura) bajan de ~3.7 % a 1–2 %; los dudosos no se muestran */
const verPunto=(P,i,n)=>P[3*i+2]>=(n<=17||i<17?.4:.5);
function puntos(x,P,esq,X,Y,Wd,Hd,r=3){
  if(!P)return;const n=P.length/3,ok=i=>verPunto(P,i,n),px=i=>X+P[3*i]*Wd,py=i=>Y+P[3*i+1]*Hd;
  x.save();x.lineCap='round';x.lineJoin='round';
  for(const grueso of [1,0]){x.strokeStyle=grueso?'rgba(255,255,255,.6)':'rgba(255,255,255,.5)';x.lineWidth=Math.max(.7,r*(grueso?.4:.22));x.beginPath();
    for(const [a,b] of esq)if((a<91)===!!grueso&&ok(a)&&ok(b)){x.moveTo(px(a),py(a));x.lineTo(px(b),py(b));}x.stroke();}
  x.shadowColor='rgba(255,255,255,.95)';x.fillStyle='#fff';
  for(let i=0;i<n;i++)if(ok(i)){const ri=r*tamPunto(i,n);x.shadowBlur=ri*3;x.beginPath();x.arc(px(i),py(i),ri,0,2*Math.PI);x.fill();}
  x.restore();}
function trazar(x,s,col,X,Y,Wd,Hd,W,H,a,{relleno=.2,linea=2}={}){
  if(!s||!s.ok||!camino(x,s,X,Y,Wd,Hd,W,H,a))return;
  x.fillStyle=`rgba(${col},${relleno})`;x.fill();x.strokeStyle=`rgb(${col})`;x.lineWidth=linea;x.lineJoin='round';x.stroke();
}

function abrir(o){
  return new Promise(async fin0=>{
    const pasos=o.pasos,conRef=!!o.ref,clases=conRef?[o.clase,Vision.PERSONA]:[o.clase],caps=[];
    let ip=0,activo=true,stream=null,frontal=false,wl=null,tick=0;
    const w=document.createElement('dialog');w.className='cam cv';w.setAttribute('aria-label',o.titulo||'Peso con cámara');
    w.innerHTML=`<div class="cam-top"><div class="cv-tit"><b class="cv-paso"></b><span class="cv-sub"></span></div><button type="button" class="cam-x" aria-label="Cerrar">${ico('x',2.4)}</button></div>
      <div class="cv-chips">${CHIPS.filter(([k])=>k!=='ref'||conRef).map(([k,t])=>`<span class="cv-chip" data-k="${k}"><i></i>${t}</span>`).join('')}</div>
      <div class="cam-v cv-v"><video playsinline autoplay muted></video><canvas class="cv-lz"></canvas>
        <div class="cv-ind"><span class="cv-pos" hidden><em>Posición</em><span class="cv-pos-b"><i></i></span><b data-no-tr>0 %</b></span><b class="cv-msg">Abriendo la cámara…</b><span class="cv-prog" hidden><i></i></span></div>
        <img class="cv-th" alt="" hidden><span class="cv-firma" data-no-tr>Powered by <b>Rumentis Labs</b></span></div>
      <div class="cam-bar cv-bar"><span class="cv-fps"></span><button type="button" class="cam-disp cv-man" aria-label="Tomar la foto ahora" disabled><i></i></button><button type="button" class="cam-lente cv-gira">Girar cámara</button></div>`;
    document.body.appendChild(w);try{w.showModal();}catch(e){w.setAttribute('open','');}
    const $w=s=>w.querySelector(s),posEl=w.querySelector('.cv-pos'),v=$w('video'),lz=$w('.cv-lz'),msg=$w('.cv-msg'),sub=$w('.cv-sub'),prog=$w('.cv-prog'),fps=$w('.cv-fps'),man=$w('.cv-man'),th=$w('.cv-th');
    const parar=()=>{if(stream)stream.getTracks().forEach(t=>t.stop());stream=null;};
    const fin=r=>{if(!activo)return;activo=false;clearInterval(tick);parar();try{wl&&wl.release();}catch(e){}try{w.close();}catch(e){}w.remove();fin0(r);};
    w.addEventListener('cancel',e=>{e.preventDefault();fin(null);});
    $w('.cam-x').onclick=()=>fin(null);
    const pintarPaso=()=>{const p=pasos[ip];$w('.cv-paso').textContent=pasos.length>1?`Paso ${ip+1} de ${pasos.length} · ${p.nombre}`:p.nombre;sub.textContent=p.instr;};
    pintarPaso();
    async function abrirCam(){parar();const s=await Fotos.camara(frontal);
      if(!activo){s.getTracks().forEach(t=>t.stop());return;}   // se cerró mientras abría
      stream=s;v.srcObject=s;try{await v.play();}catch(e){}w.classList.toggle('espejo',frontal);zona=null;}
    let zona=null;
    $w('.cv-gira').onclick=async()=>{frontal=!frontal;try{await abrirCam();}catch(e){frontal=!frontal;toast('No se pudo abrir esa cámara.');try{await abrirCam();}catch(e2){}}};
    try{await abrirCam();}catch(e){msg.textContent='No se pudo abrir la cámara. Da permiso de cámara a la app en los ajustes del teléfono.';sub.textContent='';w.classList.add('sin');return;}
    if(!activo)return;
    try{if(navigator.wakeLock)wl=await navigator.wakeLock.request('screen');}catch(e){}
    msg.textContent='Cargando el modelo de visión…';w.classList.add('carga');
    let rap=null;try{rap=await Vision.motor(o.clase===Vision.PERSONA?'rapidoP':'rapido');}catch(e){if(activo){msg.textContent='No se pudo cargar el modelo de visión en este teléfono.';w.classList.remove('carga');}return;}
    // el preciso se carga mientras tanto: lo necesita la primera foto; después, los de pose (video y fotos)
    const persona=o.clase===Vision.PERSONA,esq=Vision.ESQUELETO[persona?'persona':'animal'];
    const preP=Vision.motor('preciso');preP.catch(()=>{});
    // pose: en personas el rápido en el video y el preciso en las fotos (se carga después del rápido); en ganado el mismo
    let poseV=null;const poseVP=Vision.motor(persona?'cuerpoV':'animal');poseVP.then(m=>{poseV=m;}).catch(()=>{});
    const poseFP=persona?poseVP.catch(()=>{}).then(()=>Vision.motor('cuerpo')):poseVP;poseFP.catch(()=>{});
    const espejo=persona?Vision.ESPEJO:Vision.ESPEJO_ANIMAL;
    if(!activo)return;w.classList.remove('carga');
    const R=rap.R,cr=document.createElement('canvas');cr.width=cr.height=R;const xr=cr.getContext('2d',{willReadFrequently:true});
    let W=0,H=0,hist=[],prev=null,buenos=[],ult=null,tiempos=[],pausa=0,fallas=0,vuelo=0,seq=0,hecho=0,bmpOk=typeof createImageBitmap==='function'?3:0;
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
      if(!s||!s.ok){caja=null;return;}
      // la caja que se ve se acerca a la del último resultado: el movimiento se ve continuo
      if(!caja)caja=s.n.slice();else for(let i=0;i<4;i++)caja[i]+=(s.n[i]-caja[i])*.35;
      trazar(x,s,col,Rr.x,Rr.y,Rr.w,Rr.h,W,H,caja,{relleno:vis.color==='verde'?.24:.16,linea:2});
      // los puntos del cuerpo se deslizan hacia el último resultado (y se quitan si ya es viejo)
      // (se deslizan hacia el último resultado; ya no siguen la caja de la silueta: si la silueta se pegaba a algo,
      // los arrastraba fuera del cuerpo)
      const kp=vis.kp&&performance.now()-vis.kp.t<1500?vis.kp.p:null;
      if(kp){if(!kpV||kpV.length!==kp.length)kpV=kp.slice();else for(let i=0;i<kp.length;i++)kpV[i]+=(kp[i]-kpV[i])*(i%3===2?1:.5);
        puntos(x,kpV,esq,Rr.x,Rr.y,Rr.w,Rr.h,Math.max(2.5,Math.min(4,Rr.w/110)));}else kpV=null;
      // esquinas finas
      const X0=Rr.x+caja[0]*Rr.w,Y0=Rr.y+caja[1]*Rr.h,X1=Rr.x+caja[2]*Rr.w,Y1=Rr.y+caja[3]*Rr.h,L=Math.min(16,(X1-X0)/5,(Y1-Y0)/5),g=6;
      x.strokeStyle=`rgba(${col},.95)`;x.lineWidth=1.5;x.lineCap='round';x.beginPath();
      for(const [px,py,sx,sy] of [[X0-g,Y0-g,1,1],[X1+g,Y0-g,-1,1],[X0-g,Y1+g,1,-1],[X1+g,Y1+g,-1,-1]]){x.moveTo(px,py+sy*L);x.lineTo(px,py);x.lineTo(px+sx*L,py);}x.stroke();
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
    async function precisa(fc,cajas){
      try{const pre=await preP,t0=performance.now(),c=document.createElement('canvas');c.width=c.height=pre.R;const x=c.getContext('2d',{willReadFrequently:true});
        const una=async Z=>{x.drawImage(fc,Z.x,Z.y,Z.w,Z.h,0,0,pre.R,pre.R);const r=await pre.correr({px:x.getImageData(0,0,pre.R,pre.R).data},clases);
          return {s:aCuadro(r.suj[o.clase],r.G,Z,fc.width,fc.height),p:conRef?aCuadro(r.suj[Vision.PERSONA],r.G,Z,fc.width,fc.height):null};};
        const Zr=zonaDe(cajas,fc.width,fc.height,{margen:.1,min:.3,amin:.5,amax:2}),todo={x:0,y:0,w:fc.width,h:fc.height};
        let r=await una(Zr||todo);
        if(Zr&&((r.s&&r.s.cortada)||(r.p&&r.p.cortada)||!(r.s&&r.s.ok)))r=await una(todo);
        tPre.push(performance.now()-t0);
        if(!r.s||!r.s.ok||(conRef&&!refOk(r.p,fc.width,fc.height)))return null;
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
      const p=pasos[ip],u=lista[lista.length-1],cajas=[u.s.n,u.p&&u.p.ok?u.p.n:null];
      const cuadro=()=>{const fc=document.createElement('canvas');fc.width=W;fc.height=H;fc.getContext('2d').drawImage(v,0,0,W,H);return fc;};
      const med={};for(const k of ['alto','ancho','area','banda','ratio','torsoN','score','refAlto'])med[k]=mediana(lista.map(z=>z.med[k]));med.W=W;med.H=H;
      const fc=cuadro(),cap={id:p.id,nombre:p.nombre,manual:!!manual,rapido:med,fc,s:u.s,pr:u.p,banda:p.banda,tomas:[{fc,pend:precisa(fc,cajas)}]};
      // las demás tomas, unos milisegundos después (todavía quieto): el modelo preciso las mide todas y se promedian
      for(let t=1;t<nTomas;t++){await new Promise(z=>setTimeout(z,REGLAS.entre));if(!activo)return;const f2=cuadro();cap.tomas.push({fc:f2,pend:precisa(f2,cajas)});}
      caps.push(cap);capturando=false;
      try{navigator.vibrate&&navigator.vibrate(120);}catch(e){}beep();
      w.classList.add('flash');setTimeout(()=>w.classList.remove('flash'),260);
      const kv=vis.kp&&performance.now()-vis.kp.t<800?vis.kp.p.slice():null;if(kv)for(let i=0;i<kv.length;i+=3){kv[i]*=W;kv[i+1]*=H;}
      th.src=foto(fc,u.s,u.p,kv);th.hidden=false;buenos=[];prev=null;ult=null;tiempos=[];prog.hidden=true;zona=null;vis.s=vis.p=vis.kp=null;
      if(ip+1>=pasos.length){terminar();return;}
      ip++;pintarPaso();msg.textContent=pasos[ip].girar;chips({});hist=[];posEl.hidden=true;w.dataset.estado='';pausa=performance.now()+1500;
    }
    async function terminar(){
      pausa=Infinity;man.disabled=true;chips({});w.dataset.estado='';sub.textContent='';w.classList.add('carga');
      const todas=caps.flatMap(c=>c.tomas);let listas=0;
      const avance=()=>{msg.textContent=`Midiendo… ${listas}/${todas.length}`;};avance();
      todas.forEach(t=>t.pend.then(()=>{listas++;if(activo&&listas<todas.length)avance();}));
      const out=[];
      for(const c of caps){const tomas=[];let img=null;
        for(const t of c.tomas){const r=await t.pend;if(!activo)return;if(!r)continue;
          const med=medir(r.s,W,H,c.banda);if(r.p)med.refAlto=medir(r.p,W,H).alto;tomas.push({med,sil:r.s,ref:r.p,kp:r.kp||null,W:t.fc.width,H:t.fc.height,fc:t.fc});
          if(!img)img=foto(t.fc,r.s,r.p,r.kp);}
        const fuente=tomas.length?'preciso':'rapido';
        if(!tomas.length)tomas.push({med:c.rapido,sil:c.s,ref:c.pr||null,W:c.fc.width,H:c.fc.height});
        out.push({id:c.id,nombre:c.nombre,manual:c.manual,tomas,med:tomas[0].med,sil:tomas[0].sil,ref:tomas[0].ref,W:tomas[0].W,H:tomas[0].H,fuente,img:img||foto(c.fc,c.s,c.pr)});}
      try{if(tPre.length)localStorage.setItem('rumentis-pc-msp',String(Math.round(mediana(tPre))));}catch(e){}
      // rendimiento de esta medición (para el laboratorio)
      out.stats={fps:fpsL.length?fpsL.reduce((a,b)=>a+b,0)/fpsL.length:0,cuadros:procesados,msRapido:msR.length?msR.reduce((a,b)=>a+b,0)/msR.length:0,
        msPreciso:tPre.length?tPre.reduce((a,b)=>a+b,0)/tPre.length:0,msPose:tPose.length?tPose.reduce((a,b)=>a+b,0)/tPose.length:0,seg:(performance.now()-tIni)/1000,motor:rap.modo+'×'+rap.n,tomas:todas.length};
      fin(out);
    }
    man.onclick=()=>{if(!ult)return toast('Que se vea completo para tomar la foto.');const l=buenos.length?buenos:[ult];capturar(l.slice(-REGLAS.seguidos),true);};

    /* ---------- un resultado del modelo rápido ---------- */
    const tIni=performance.now(),msR=[],fpsL=[];let procesados=0;
    function procesar(r,Z){
      procesados++;if(msR.length<400)msR.push(r.ms);
      const p=pasos[ip];let s=aCuadro(r.suj[o.clase],r.G,Z,W,H);const pr=conRef?aCuadro(r.suj[Vision.PERSONA],r.G,Z,W,H):null;
      const now=performance.now();
      if(persona&&s&&s.ok&&vis.kp&&now-vis.kp.t<600&&iou(vis.kp.n,s.n)>=.85)s=limpiarSil(s,vis.kp.p,W,H);
      tiempos.push(now);tiempos=tiempos.filter(t=>now-t<2000);
      // (sobre al menos 0.8 s: con varios workers los resultados llegan en ráfagas y una ventana corta exagera)
      if(tiempos.length>2&&now-tiempos[0]>=800){const f=(tiempos.length-1)/((now-tiempos[0])/1000);fps.textContent=`${f.toFixed(1)} cuadros/s`;if(fpsL.length<400)fpsL.push(f);}
      // (ms y motor quedan en el diálogo para las pruebas)
      w.dataset.ms=Math.round(r.ms);w.dataset.motor=rap.modo+'×'+rap.n;
      // el cuadro siguiente mira solo la zona del sujeto; si la zona lo cortó (o en ganado falta la persona), completo
      zona=(s&&s.cortada)||(pr&&pr.cortada)||(conRef&&!(pr&&pr.ok))?null:zonaSig([s&&s.ok?s.n:null,pr&&pr.ok?pr.n:null],W,H,zona);
      const ev=revisar(s,pr,W,H,{...p,buscar:o.buscar},prev,caps.length?caps[0].rapido:null,conRef,vis.kp&&now-vis.kp.t<700?vis.kp:null);
      // "quieto" se compara con la mediana de las últimas cajas: un cuadro raro no reinicia la cuenta
      if(ev.caja){hist.push(ev.caja);if(hist.length>3)hist.shift();prev=[0,1,2,3].map(i=>mediana(hist.map(z=>z[i])));}else{hist=[];prev=null;}
      posEl.hidden=ev.pos==null;const pp=Math.round((ev.pos||0)*100);posEl.style.setProperty('--p',(ev.pos||0));posEl.lastChild.textContent=`${pp} %`;
      const E=ev.E,listo=E.det&&E.comp&&E.dist&&E.ang&&E.quieto&&(!conRef||E.ref);
      const color=!E.det?'rojo':listo?'verde':(E.comp&&E.dist?'ambar':'rojo');
      const med=E.det?medir(s,W,H,p.banda):null;if(med&&conRef&&E.ref)med.refAlto=medir(pr,W,H).alto;
      ult=E.det&&E.comp&&(!conRef||E.ref)?{s,p:pr,med}:null;man.disabled=!ult;
      vis.s=E.det?s:null;vis.p=pr;vis.color=color;chips(E);w.dataset.estado=color;w.dataset.zona=zona?'si':'no';
      if(listo){buenos.push({s,p:pr,med});prog.hidden=false;prog.style.setProperty('--p',buenos.length/REGLAS.seguidos);msg.textContent='Quieto…';
        if(buenos.length>=REGLAS.seguidos)capturar(buenos,false);}
      else{buenos=[];prog.hidden=true;msg.textContent=ev.msg;}
    }
    /* ---------- cuadros en vuelo: uno por worker del modelo rápido ---------- */
    async function tomar(Z){
      const k=v.videoWidth/W;
      // (si createImageBitmap falla varias veces seguidas, se usa un lienzo en la página)
      if(bmpOk>0)try{const b=await createImageBitmap(v,Z.x*k,Z.y*k,Z.w*k,Z.h*k,{resizeWidth:R,resizeHeight:R,resizeQuality:'low'});bmpOk=3;return {bmp:b};}catch(e){bmpOk--;}
      xr.drawImage(v,Z.x*k,Z.y*k,Z.w*k,Z.h*k,0,0,R,R);return {px:xr.getImageData(0,0,R,R).data};
    }
    async function lanzar(){
      vuelo++;const mi=++seq,paso=ip;medidas();const Z=zona||{x:0,y:0,w:W,h:H};let r=null;
      try{r=await rap.correr(await tomar(Z),clases);fallas=0;}catch(e){console.warn(e);fallas++;}
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
    let poseVuelo=0,poseT=0,poseSeq=0,poseHecho=0,poseMs=0;const poseRespiro=persona?(NUCLEOS()>=6?0:120):250;
    /* suavizado de los puntos al llegar (como un filtro One Euro): si un punto casi no se movió (menos de 1.5 % del alto
       del cuerpo) se promedia con el anterior y deja de temblar; si se movió más, lo sigue de inmediato */
    function suavizar(P,A,alto){if(!A||A.length!==P.length)return P;
      for(let i=0;i<P.length;i+=3){if(A[i+2]<.3||P[i+2]<.3)continue;const dd=Math.hypot((P[i]-A[i])*W,(P[i+1]-A[i+1])*H)/Math.max(1e-6,alto*H),a=Math.min(1,.35+dd/.015*.65);
        P[i]=A[i]+(P[i]-A[i])*a;P[i+1]=A[i+1]+(P[i+1]-A[i+1])*a;}return P;}
    async function bombearPose(){
      if(!activo||!poseV||poseVuelo>=poseV.n||!vis.s||!vis.s.ok||!v.videoWidth||performance.now()<pausa||performance.now()-poseT<Math.max(poseRespiro,poseMs/poseV.n))return;
      poseVuelo++;poseT=performance.now();const paso=ip,mi=++poseSeq;
      // el recorte sale de los puntos del cuadro anterior (seguimiento, como en los trajes de captura); si no hay o son
      // viejos, de la silueta. Así, si la silueta se pega a algo, los puntos no se van con ella.
      const kb=vis.kp&&performance.now()-vis.kp.t<400?cajaPuntos(vis.kp.p):null,n=kb&&iou(kb,vis.s.n)>.3?kb:vis.s.n.slice();
      try{const Z=recortePose(n,W,H,poseV),k=v.videoWidth/W;let e;
        try{e={bmp:await createImageBitmap(v,Z.x*k,Z.y*k,Z.w*k,Z.h*k,{resizeWidth:poseV.W,resizeHeight:poseV.H,resizeQuality:'medium'})};}
        catch(er){const c=document.createElement('canvas');c.width=poseV.W;c.height=poseV.H;const x=c.getContext('2d',{willReadFrequently:true});x.fillStyle='#000';x.fillRect(0,0,c.width,c.height);
          x.drawImage(v,Z.x*k,Z.y*k,Z.w*k,Z.h*k,0,0,c.width,c.height);e={px:x.getImageData(0,0,c.width,c.height).data};}
        const r=await poseV.correr(e,[]),P=r.kp;poseMs=poseMs?poseMs*.8+r.ms*.2:r.ms;w.dataset.msPose=Math.round(poseMs);
        for(let i=0;i<P.length;i+=3){P[i]=(Z.x+P[i]*Z.w)/W;P[i+1]=(Z.y+P[i+1]*Z.h)/H;}
        if(activo&&paso===ip&&performance.now()>=pausa&&mi>poseHecho){poseHecho=mi;suavizar(P,vis.kp&&performance.now()-vis.kp.t<500?vis.kp.p:null,n[3]-n[1]);vis.kp={p:P,t:performance.now(),n};let c=0;for(let i=2;i<P.length;i+=3)if(P[i]>=.3)c++;w.dataset.puntos=c;}
      }catch(e){console.warn(e);}
      poseVuelo--;if(poseRespiro)setTimeout(bombearPose,poseRespiro);else bombearPose();
    }
    tick=setInterval(()=>{bombear();bombearPose();},60);bombear();
  });
}
window.CamVivo={abrir,REGLAS,medir,revisar,zonaSig,zonaDe,torsoN,hombrosN,recortePose,limpiarSil,aCuadro,cajaPuntos};
})();
