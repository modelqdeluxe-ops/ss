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
const REGLAS={score:.5,margen:.02,quieto:.9,seguidos:3,lienzo:960,ref:{alto:.25,forma:1.6},tomas:2,entre:160};
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
function revisar(s,p,W,H,paso,prev,ref,conRef){
  const E={det:false,comp:null,dist:null,ang:null,ref:conRef?false:undefined,quieto:null};
  E.det=!!(s&&s.ok&&s.score>=REGLAS.score);
  if(conRef)E.ref=refOk(p,W,H);
  if(!E.det)return {E,msg:paso.buscar};
  const c=s.n,d=paso.dist,a=paso.ang;
  E.comp=dentro(c,REGLAS.margen)&&!s.cortada;
  const v=d.eje==='alto'?c[3]-c[1]:c[2]-c[0];E.dist=v>=d.min&&v<=d.max;
  const ratio=((c[2]-c[0])*W)/((c[3]-c[1])*H);
  // (ref: las medidas de la primera toma; perfil: el tronco debe verse a lo más esa fracción de ancho que de frente)
  const perfil=!a.perfil||!ref||!ref.torsoN||torsoN(s)<=a.perfil*ref.torsoN;
  E.ang=(a.min==null||ratio>=a.min)&&(a.max==null||ratio<=a.max)&&(!a.rel||!ref||ratio<=a.rel*ref.ratio)&&perfil;
  E.quieto=iou(c,prev)>=REGLAS.quieto;
  let msg='';
  if(!E.comp)msg=v>d.max*.95?'Aléjate un poco':'Que se vea el cuerpo completo';
  else if(!E.dist)msg=v<d.min?'Acércate':'Aléjate';
  else if(!E.ang)msg=!perfil&&paso.girarMas?paso.girarMas:paso.girar;
  else if(conRef&&!E.ref)msg=p&&p.ok?'Que la persona de referencia se vea completa y derecha':'Falta la persona de referencia junto al animal';
  else if(!E.quieto)msg='Quieto…';
  return {E,msg,caja:c};
}
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
        <div class="cv-ind"><b class="cv-msg">Abriendo la cámara…</b><span class="cv-prog" hidden><i></i></span></div>
        <img class="cv-th" alt="" hidden><span class="cv-firma" data-no-tr>Powered by <b>Rumentis Labs</b></span></div>
      <div class="cam-bar cv-bar"><span class="cv-fps"></span><button type="button" class="cam-disp cv-man" aria-label="Tomar la foto ahora" disabled><i></i></button><button type="button" class="cam-lente cv-gira">Girar cámara</button></div>`;
    document.body.appendChild(w);try{w.showModal();}catch(e){w.setAttribute('open','');}
    const $w=s=>w.querySelector(s),v=$w('video'),lz=$w('.cv-lz'),msg=$w('.cv-msg'),sub=$w('.cv-sub'),prog=$w('.cv-prog'),fps=$w('.cv-fps'),man=$w('.cv-man'),th=$w('.cv-th');
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
    // el preciso se carga mientras tanto: lo necesita la primera foto
    const preP=Vision.motor('preciso');preP.catch(()=>{});
    if(!activo)return;w.classList.remove('carga');
    const R=rap.R,cr=document.createElement('canvas');cr.width=cr.height=R;const xr=cr.getContext('2d',{willReadFrequently:true});
    let W=0,H=0,prev=null,buenos=[],ult=null,tiempos=[],pausa=0,fallas=0,vuelo=0,seq=0,hecho=0,bmpOk=typeof createImageBitmap==='function'?3:0;
    // el tamaño de trabajo del cuadro (hasta 960 px de lado)
    const medidas=()=>{const vw=v.videoWidth,vh=v.videoHeight,k=Math.min(1,REGLAS.lienzo/Math.max(vw,vh)),w2=Math.round(vw*k),h2=Math.round(vh*k);if(w2!==W||h2!==H){W=w2;H=h2;zona=null;}};

    /* ---------- dibujo: 60 cuadros por segundo ---------- */
    const vis={s:null,p:null,color:'rojo'};let caja=null;
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
      // esquinas finas
      const X0=Rr.x+caja[0]*Rr.w,Y0=Rr.y+caja[1]*Rr.h,X1=Rr.x+caja[2]*Rr.w,Y1=Rr.y+caja[3]*Rr.h,L=Math.min(16,(X1-X0)/5,(Y1-Y0)/5),g=6;
      x.strokeStyle=`rgba(${col},.95)`;x.lineWidth=1.5;x.lineCap='round';x.beginPath();
      for(const [px,py,sx,sy] of [[X0-g,Y0-g,1,1],[X1+g,Y0-g,-1,1],[X0-g,Y1+g,1,-1],[X1+g,Y1+g,-1,-1]]){x.moveTo(px,py+sy*L);x.lineTo(px,py);x.lineTo(px+sx*L,py);}x.stroke();
    }
    requestAnimationFrame(dibujar);
    function chips(E){for(const el of w.querySelectorAll('.cv-chip')){const s=E[el.dataset.k];el.dataset.s=s==null?'':s?'ok':'no';}}
    // la foto que se guarda: el cuadro con el contorno de la silueta
    // (recortada alrededor del sujeto, para que se vea grande)
    function foto(fc,s,ref){const Z=(s&&s.ok&&zonaDe([s.n,ref&&ref.ok?ref.n:null],fc.width,fc.height,{margen:.12,min:.3,amin:.6,amax:1.6}))||{x:0,y:0,w:fc.width,h:fc.height};
      const c=document.createElement('canvas'),k=Math.min(1,720/Math.max(Z.w,Z.h));c.width=Math.round(Z.w*k);c.height=Math.round(Z.h*k);
      const x=c.getContext('2d');x.drawImage(fc,Z.x,Z.y,Z.w,Z.h,0,0,c.width,c.height);const X=-Z.x*k,Y=-Z.y*k,Wd=fc.width*k,Hd=fc.height*k,lw=Math.max(2,c.width/260);
      if(ref&&ref.ok)trazar(x,ref,'255,255,255',X,Y,Wd,Hd,fc.width,fc.height,null,{relleno:.08,linea:lw*.75});
      trazar(x,s,COLOR.verde,X,Y,Wd,Hd,fc.width,fc.height,null,{relleno:.22,linea:lw});
      const fs=Math.max(10,Math.round(c.width/34));x.font=`700 ${fs}px sans-serif`;x.textAlign='right';x.textBaseline='bottom';x.shadowColor='rgba(0,0,0,.6)';x.shadowBlur=4;x.fillStyle='rgba(255,255,255,.85)';x.fillText(MARCA,c.width-fs*.6,c.height-fs*.5);
      return c.toDataURL('image/jpeg',.85);}
    /* medida precisa de una foto capturada (en segundo plano), sobre un recorte alrededor del sujeto (y de la persona
       de referencia): el modelo ve el cuerpo con más detalle. Si el recorte lo corta, se mide la foto completa. Si no
       sale, se queda la del modelo rápido. */
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
        return r;}catch(e){console.warn(e);return null;}}
    // las fotos de un ángulo se toman todas antes de avisar (el pitido hace que la persona se mueva)
    let capturando=false;
    async function capturar(lista,manual){
      if(capturando)return;capturando=true;pausa=Infinity;
      const p=pasos[ip],u=lista[lista.length-1],cajas=[u.s.n,u.p&&u.p.ok?u.p.n:null];
      const cuadro=()=>{const fc=document.createElement('canvas');fc.width=W;fc.height=H;fc.getContext('2d').drawImage(v,0,0,W,H);return fc;};
      const med={};for(const k of ['alto','ancho','area','banda','ratio','torsoN','score','refAlto'])med[k]=mediana(lista.map(z=>z.med[k]));med.W=W;med.H=H;
      const fc=cuadro(),cap={id:p.id,nombre:p.nombre,manual:!!manual,rapido:med,fc,s:u.s,pr:u.p,banda:p.banda,tomas:[{fc,pend:precisa(fc,cajas)}]};
      // las demás tomas, unos milisegundos después (todavía quieto): el modelo preciso las mide todas y se promedian
      for(let t=1;t<REGLAS.tomas;t++){await new Promise(z=>setTimeout(z,REGLAS.entre));if(!activo)return;const f2=cuadro();cap.tomas.push({fc:f2,pend:precisa(f2,cajas)});}
      caps.push(cap);capturando=false;
      try{navigator.vibrate&&navigator.vibrate(120);}catch(e){}beep();
      w.classList.add('flash');setTimeout(()=>w.classList.remove('flash'),260);
      th.src=foto(fc,u.s,u.p);th.hidden=false;buenos=[];prev=null;ult=null;tiempos=[];prog.hidden=true;zona=null;vis.s=vis.p=null;
      if(ip+1>=pasos.length){terminar();return;}
      ip++;pintarPaso();msg.textContent=pasos[ip].girar;chips({});w.dataset.estado='';pausa=performance.now()+1500;
    }
    async function terminar(){
      pausa=Infinity;man.disabled=true;chips({});w.dataset.estado='';sub.textContent='';w.classList.add('carga');
      const todas=caps.flatMap(c=>c.tomas);let listas=0;
      const avance=()=>{msg.textContent=`Midiendo… ${listas}/${todas.length}`;};avance();
      todas.forEach(t=>t.pend.then(()=>{listas++;if(activo&&listas<todas.length)avance();}));
      const out=[];
      for(const c of caps){const tomas=[];let img=null;
        for(const t of c.tomas){const r=await t.pend;if(!activo)return;if(!r)continue;
          const med=medir(r.s,W,H,c.banda);if(r.p)med.refAlto=medir(r.p,W,H).alto;tomas.push({med,sil:r.s,ref:r.p,W:t.fc.width,H:t.fc.height});
          if(!img)img=foto(t.fc,r.s,r.p);}
        const fuente=tomas.length?'preciso':'rapido';
        if(!tomas.length)tomas.push({med:c.rapido,sil:c.s,ref:c.pr||null,W:c.fc.width,H:c.fc.height});
        out.push({id:c.id,nombre:c.nombre,manual:c.manual,tomas,med:tomas[0].med,sil:tomas[0].sil,ref:tomas[0].ref,W:tomas[0].W,H:tomas[0].H,fuente,img:img||foto(c.fc,c.s,c.pr)});}
      // rendimiento de esta medición (para el laboratorio)
      out.stats={fps:fpsL.length?fpsL.reduce((a,b)=>a+b,0)/fpsL.length:0,cuadros:procesados,msRapido:msR.length?msR.reduce((a,b)=>a+b,0)/msR.length:0,
        msPreciso:tPre.length?tPre.reduce((a,b)=>a+b,0)/tPre.length:0,seg:(performance.now()-tIni)/1000,motor:rap.modo+'×'+rap.n,tomas:todas.length};
      fin(out);
    }
    man.onclick=()=>{if(!ult)return toast('Que se vea completo para tomar la foto.');const l=buenos.length?buenos:[ult];capturar(l.slice(-REGLAS.seguidos),true);};

    /* ---------- un resultado del modelo rápido ---------- */
    const tIni=performance.now(),msR=[],fpsL=[];let procesados=0;
    function procesar(r,Z){
      procesados++;if(msR.length<400)msR.push(r.ms);
      const p=pasos[ip],s=aCuadro(r.suj[o.clase],r.G,Z,W,H),pr=conRef?aCuadro(r.suj[Vision.PERSONA],r.G,Z,W,H):null;
      const now=performance.now();tiempos.push(now);tiempos=tiempos.filter(t=>now-t<2000);
      if(tiempos.length>2){const f=(tiempos.length-1)/((now-tiempos[0])/1000);fps.textContent=`${f.toFixed(1)} cuadros/s`;if(fpsL.length<400)fpsL.push(f);}
      // (ms y motor quedan en el diálogo para las pruebas)
      w.dataset.ms=Math.round(r.ms);w.dataset.motor=rap.modo+'×'+rap.n;
      // el cuadro siguiente mira solo la zona del sujeto; si la zona lo cortó (o en ganado falta la persona), completo
      zona=(s&&s.cortada)||(pr&&pr.cortada)||(conRef&&!(pr&&pr.ok))?null:zonaSig([s&&s.ok?s.n:null,pr&&pr.ok?pr.n:null],W,H,zona);
      const ev=revisar(s,pr,W,H,{...p,buscar:o.buscar},prev,caps.length?caps[0].rapido:null,conRef);prev=ev.caja||null;
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
    tick=setInterval(bombear,60);bombear();
  });
}
window.CamVivo={abrir,REGLAS,medir,revisar,zonaSig,zonaDe,torsoN};
})();
