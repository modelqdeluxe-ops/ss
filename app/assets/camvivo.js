/* Rumentis Beta: cámara en vivo para el peso con cámara.
   Pantalla completa con el video; en cada cuadro que analiza el modelo se pinta la silueta (roja, ámbar o verde) y se
   revisa si está todo listo para medir: se ve completo, a buena distancia, con la marca de medida, en el ángulo que
   toca y quieto. Cuando todo está en verde varios cuadros seguidos, la foto se toma sola y pide el siguiente ángulo.
   CamVivo.abrir({titulo, clase, buscar, pasos:[{id, nombre, instr, girar, ang:{min,max,rel}, dist:{eje,min,max}, banda}]})
   (ang.rel: la proporción ancho/alto debe bajar a esa fracción de la de la primera toma; p. ej. de frente a costado)
   devuelve una captura por paso ({id, med, img, manual}) o null si se cierra antes. */
(function(){
'use strict';
const CFG=window.RUMENTIS||{};if(!CFG.beta||CFG.app!=='jefe'||!window.Vision)return;

/* reglas generales (las de distancia y ángulo vienen en cada paso) */
const REGLAS={score:.5,margen:.02,marcaMin:36,quieto:.93,seguidos:3,det:960};
const MARCA_CM=16;
const CHIPS=[['det','Detectado'],['comp','Completo'],['dist','Distancia'],['marca','Marca'],['ang','Ángulo'],['quieto','Quieto']];
const COLOR={verde:[46,204,113],ambar:[240,191,51],rojo:[231,76,60]};

const iou=(a,b)=>{if(!a||!b)return 0;const x0=Math.max(a[0],b[0]),y0=Math.max(a[1],b[1]),x1=Math.min(a[2],b[2]),y1=Math.min(a[3],b[3]);
  const i=Math.max(0,x1-x0)*Math.max(0,y1-y0),u=(a[2]-a[0])*(a[3]-a[1])+(b[2]-b[0])*(b[3]-b[1])-i;return u>0?i/u:0;};
const mediana=v=>{const s=v.filter(x=>isFinite(x)).sort((a,b)=>a-b),n=s.length;return n?(n%2?s[(n-1)/2]:(s[n/2-1]+s[n/2])/2):NaN;};
const pct=(v,p)=>{const s=v.filter(x=>x>0).sort((a,b)=>a-b);return s.length?s[Math.min(s.length-1,Math.floor(p*(s.length-1)+.5))]:0;};

/* medidas de un cuadro: la silueta viene en una rejilla G×G que cubre todo el cuadro (estirado); la marca, en px del
   lienzo de detección (Wd×Hd). Todo se pasa a centímetros con la marca. */
function medir(r,m,Wd,Hd,paso){
  const G=r.G,[x0,y0,x1,y1]=r.caja,cx=Wd/G,cy=Hd/G;   // px del lienzo por celda
  const med={score:r.score,caja:[x0/G,y0/G,x1/G,y1/G],ratio:((x1-x0)*cx)/((y1-y0)*cy)};
  if(m){const k=MARCA_CM/m.lado;   // cm por px
    med.pxcm=m.lado/MARCA_CM;med.ancho=(x1-x0)*cx*k;med.alto=(y1-y0)*cy*k;med.A=r.area*cx*cy*k*k;
    // ancho en una franja de alturas (profundidad del cuerpo de costado, ancho del animal por detrás)
    const b=paso.banda||[.2,.6],h=y1-y0,fs=[];for(let y=Math.floor(y0+b[0]*h);y<Math.ceil(y0+b[1]*h);y++)fs.push(r.filas[y]);
    med.banda=pct(fs,.9)*cx*k;}
  return med;
}
function revisar(r,m,paso,prev,ref){
  const E={det:false,comp:null,dist:null,marca:null,ang:null,quieto:null};let msg='',dir=0;
  E.det=!!(r&&r.ok&&r.score>=REGLAS.score);
  E.marca=!!(m&&m.lado>=REGLAS.marcaMin);
  if(!E.det)return {E,msg:paso.buscar};
  const G=r.G,c=r.caja.map(v=>v/G),mg=REGLAS.margen,d=paso.dist;
  E.comp=c[0]>mg&&c[1]>mg&&c[2]<1-mg&&c[3]<1-mg;
  const v=d.eje==='alto'?c[3]-c[1]:c[2]-c[0];E.dist=v>=d.min&&v<=d.max;dir=v<d.min?1:v>d.max?-1:0;
  const ratio=((c[2]-c[0])*r.W)/((c[3]-c[1])*r.H);const a=paso.ang;E.ang=(a.min==null||ratio>=a.min)&&(a.max==null||ratio<=a.max)&&(!a.rel||!ref||ratio<=a.rel*ref);
  E.quieto=iou(c,prev)>=REGLAS.quieto;
  if(!E.comp)msg=dir<0||v>d.max*.95?'Aléjate un poco':'Que se vea el cuerpo completo';
  else if(!E.dist)msg=dir>0?'Acércate':'Aléjate';
  else if(!E.ang)msg=paso.girar;
  else if(!E.marca)msg=m?'Acércate: la marca se ve pequeña':'Falta la marca de medida';
  else if(!E.quieto)msg='Quieto…';
  return {E,msg,caja:c};
}
// opacidad de la silueta según el logit de la máscara: borde nítido pero suave (la máscara de 78×78 se agranda)
const alfa=(v,max)=>{const t=Math.min(1,Math.max(0,(v+.75)/1.5));return Math.round(t*t*(3-2*t)*max);};
function beep(){try{const A=window.AudioContext||window.webkitAudioContext;if(!A)return;const a=beep.a||(beep.a=new A()),o=a.createOscillator(),g=a.createGain();
  o.frequency.value=1046;g.gain.setValueAtTime(.18,a.currentTime);g.gain.exponentialRampToValueAtTime(.001,a.currentTime+.18);o.connect(g).connect(a.destination);o.start();o.stop(a.currentTime+.2);}catch(e){}}

function abrir(o){
  return new Promise(async fin0=>{
    const pasos=o.pasos,caps=[];let ip=0,activo=true,stream=null,frontal=false,wl=null;
    const w=document.createElement('dialog');w.className='cam cv';w.setAttribute('aria-label',o.titulo||'Peso con cámara');
    w.innerHTML=`<div class="cam-top"><div class="cv-tit"><b class="cv-paso"></b><span class="cv-sub"></span></div><button type="button" class="cam-x" aria-label="Cerrar">${ico('x',2.4)}</button></div>
      <div class="cv-chips">${CHIPS.map(([k,t])=>`<span class="cv-chip" data-k="${k}"><i></i>${t}</span>`).join('')}</div>
      <div class="cam-v cv-v"><video playsinline autoplay muted></video><canvas class="cv-lz"></canvas>
        <div class="cv-ind"><b class="cv-msg">Abriendo la cámara…</b><span class="cv-prog" hidden><i></i></span></div>
        <img class="cv-th" alt="" hidden></div>
      <div class="cam-bar cv-bar"><span class="cv-fps"></span><button type="button" class="cam-disp cv-man" aria-label="Tomar la foto ahora" disabled><i></i></button><button type="button" class="cam-lente cv-gira">Girar cámara</button></div>`;
    document.body.appendChild(w);try{w.showModal();}catch(e){w.setAttribute('open','');}
    const $w=s=>w.querySelector(s),v=$w('video'),lz=$w('.cv-lz'),msg=$w('.cv-msg'),sub=$w('.cv-sub'),prog=$w('.cv-prog'),fps=$w('.cv-fps'),man=$w('.cv-man'),th=$w('.cv-th');
    const parar=()=>{if(stream)stream.getTracks().forEach(t=>t.stop());stream=null;};
    const fin=r=>{if(!activo)return;activo=false;parar();try{wl&&wl.release();}catch(e){}try{w.close();}catch(e){}w.remove();fin0(r);};
    w.addEventListener('cancel',e=>{e.preventDefault();fin(null);});
    $w('.cam-x').onclick=()=>fin(null);
    const pintarPaso=()=>{const p=pasos[ip];$w('.cv-paso').textContent=pasos.length>1?`Paso ${ip+1} de ${pasos.length} · ${p.nombre}`:p.nombre;sub.textContent=p.instr;};
    pintarPaso();
    async function abrirCam(){parar();const s=await Fotos.camara(frontal);
      if(!activo){s.getTracks().forEach(t=>t.stop());return;}   // se cerró mientras abría
      stream=s;v.srcObject=s;try{await v.play();}catch(e){}w.classList.toggle('espejo',frontal);}
    $w('.cv-gira').onclick=async()=>{frontal=!frontal;try{await abrirCam();}catch(e){frontal=!frontal;toast('No se pudo abrir esa cámara.');try{await abrirCam();}catch(e2){}}};
    try{await abrirCam();}catch(e){msg.textContent='No se pudo abrir la cámara. Da permiso de cámara a la app en los ajustes del teléfono.';sub.textContent='';w.classList.add('sin');return;}
    if(!activo)return;
    try{if(navigator.wakeLock)wl=await navigator.wakeLock.request('screen');}catch(e){}
    msg.textContent=Vision.listo()?'Preparando…':'Cargando el modelo de visión…';w.classList.add('carga');
    let mot=null;try{mot=await Vision.motor();await PesoCam.aruco();}catch(e){if(activo){msg.textContent='No se pudo cargar el modelo de visión en este teléfono.';w.classList.remove('carga');}return;}
    if(!activo)return;w.classList.remove('carga');
    const det=new AR.Detector({dictionaryName:'ARUCO_MIP_36h12'});
    const c312=document.createElement('canvas');c312.width=c312.height=Vision.RES;const x312=c312.getContext('2d',{willReadFrequently:true});
    const cd=document.createElement('canvas'),xd=cd.getContext('2d',{willReadFrequently:true});
    const cm=document.createElement('canvas');cm.width=cm.height=Vision.MASK;const xm=cm.getContext('2d');
    let prev=null,buenos=[],ult=null,tiempos=[],pausa=0,fallas=0;
    // dónde se ve el video en pantalla (object-fit: contain)
    const rect=()=>{const bw=v.clientWidth,bh=v.clientHeight,vw=v.videoWidth,vh=v.videoHeight;if(!vw||!bw)return null;const k=Math.min(bw/vw,bh/vh);
      return {x:(bw-vw*k)/2,y:(bh-vh*k)/2,w:vw*k,h:vh*k};};
    function dibujar(r,m,color){
      const bw=v.clientWidth,bh=v.clientHeight,dpr=Math.min(2,window.devicePixelRatio||1);if(lz.width!==Math.round(bw*dpr)||lz.height!==Math.round(bh*dpr)){lz.width=Math.round(bw*dpr);lz.height=Math.round(bh*dpr);}
      const x=lz.getContext('2d');x.setTransform(dpr,0,0,dpr,0,0);x.clearRect(0,0,bw,bh);const R=rect();if(!R)return;
      if(r&&r.ok&&r.mask){const M=Vision.MASK,d=xm.createImageData(M,M),[cr,cg,cb]=COLOR[color];
        for(let i=0;i<M*M;i++){d.data[i*4]=cr;d.data[i*4+1]=cg;d.data[i*4+2]=cb;d.data[i*4+3]=alfa(r.mask[i],125);}
        xm.putImageData(d,0,0);x.imageSmoothingEnabled=true;x.drawImage(cm,R.x,R.y,R.w,R.h);
        // esquinas de la caja
        const G=r.G,[a0,b0,a1,b1]=r.caja,X0=R.x+a0/G*R.w,Y0=R.y+b0/G*R.h,X1=R.x+a1/G*R.w,Y1=R.y+b1/G*R.h,L=Math.min(28,(X1-X0)/4,(Y1-Y0)/4);
        x.strokeStyle=`rgb(${cr},${cg},${cb})`;x.lineWidth=4;x.lineCap='round';x.beginPath();
        for(const [px,py,sx,sy] of [[X0,Y0,1,1],[X1,Y0,-1,1],[X0,Y1,1,-1],[X1,Y1,-1,-1]]){x.moveTo(px,py+sy*L);x.lineTo(px,py);x.lineTo(px+sx*L,py);}x.stroke();}
      if(m){const k=R.w/cd.width;x.strokeStyle=m.lado>=REGLAS.marcaMin?'#2ECC71':'#F0BF33';x.lineWidth=3;x.beginPath();
        m.esquinas.forEach((p,i)=>i?x.lineTo(R.x+p.x*k,R.y+p.y*k):x.moveTo(R.x+p.x*k,R.y+p.y*k));x.closePath();x.stroke();}
    }
    function chips(E){for(const [k] of CHIPS){const el=w.querySelector(`.cv-chip[data-k="${k}"]`),s=E[k];el.dataset.s=s==null?'':s?'ok':'no';}}
    // la foto que se guarda: el cuadro con la silueta pintada
    function foto(r,color){const c=document.createElement('canvas');c.width=cd.width;c.height=cd.height;const x=c.getContext('2d');x.drawImage(cd,0,0);
      if(r&&r.mask){const M=Vision.MASK,d=xm.createImageData(M,M),[cr,cg,cb]=COLOR[color];for(let i=0;i<M*M;i++){d.data[i*4]=cr;d.data[i*4+1]=cg;d.data[i*4+2]=cb;d.data[i*4+3]=alfa(r.mask[i],110);}
        xm.putImageData(d,0,0);x.imageSmoothingEnabled=true;x.drawImage(cm,0,0,c.width,c.height);}
      const k=Math.min(1,720/Math.max(c.width,c.height));if(k<1){const c2=document.createElement('canvas');c2.width=Math.round(c.width*k);c2.height=Math.round(c.height*k);c2.getContext('2d').drawImage(c,0,0,c2.width,c2.height);return c2.toDataURL('image/jpeg',.82);}
      return c.toDataURL('image/jpeg',.82);}
    function capturar(lista,manual){
      const p=pasos[ip],med={};for(const k of ['A','ancho','alto','banda','pxcm','score','ratio'])med[k]=mediana(lista.map(z=>z.med[k]));
      const u=lista[lista.length-1];caps.push({id:p.id,nombre:p.nombre,med,img:foto(u.r,'verde'),manual:!!manual});
      try{navigator.vibrate&&navigator.vibrate(120);}catch(e){}beep();
      w.classList.add('flash');setTimeout(()=>w.classList.remove('flash'),260);
      th.src=caps[caps.length-1].img;th.hidden=false;buenos=[];prev=null;ult=null;tiempos=[];
      if(ip+1>=pasos.length){msg.textContent='Listo';sub.textContent='';setTimeout(()=>fin(caps),700);return;}
      ip++;pintarPaso();msg.textContent=pasos[ip].girar;chips({});dibujar(null,null,'rojo');pausa=performance.now()+2200;
    }
    man.onclick=()=>{if(!ult||!ult.ok)return toast('Que se vea completo y con la marca para tomar la foto.');const l=buenos.length?buenos:[ult];capturar(l.slice(-REGLAS.seguidos),true);};
    // el ciclo: un cuadro a la vez, lo más rápido que dé el teléfono
    while(activo){
      if(!v.videoWidth||v.readyState<2||performance.now()<pausa){await new Promise(r=>setTimeout(r,120));continue;}
      const p=pasos[ip],vw=v.videoWidth,vh=v.videoHeight,kd=Math.min(1,REGLAS.det/Math.max(vw,vh));
      cd.width=Math.round(vw*kd);cd.height=Math.round(vh*kd);xd.drawImage(v,0,0,cd.width,cd.height);
      x312.drawImage(cd,0,0,Vision.RES,Vision.RES);const px=x312.getImageData(0,0,Vision.RES,Vision.RES).data;
      const t0=performance.now();const pr=mot.correr(px,{clase:o.clase,umbral:.3});
      // la marca se busca en la página mientras el modelo corre en el worker
      let m=null;try{const ms=det.detect(xd.getImageData(0,0,cd.width,cd.height)).filter(z=>z.id===PesoCam.MARCA_ID);
        if(ms.length){const P=ms[0].corners,L=[0,1,2,3].map(i=>Math.hypot(P[i].x-P[(i+1)%4].x,P[i].y-P[(i+1)%4].y));m={esquinas:P,lado:L.reduce((a,b)=>a+b,0)/4};}}catch(e){}
      let r;try{r=await pr;fallas=0;}catch(e){console.warn(e);
        if(++fallas>=3){msg.textContent='No se pudo cargar el modelo de visión en este teléfono.';w.dataset.estado='';man.disabled=true;break;}
        await new Promise(z=>setTimeout(z,400));continue;}
      if(!activo)break;
      // (ms, total y motor quedan en el diálogo para las pruebas)
      r.W=cd.width;r.H=cd.height;w.dataset.ms=Math.round(r.ms);w.dataset.total=Math.round(performance.now()-t0);w.dataset.motor=mot.modo;
      const now=performance.now();tiempos.push(now);tiempos=tiempos.filter(t=>now-t<4000);
      if(tiempos.length>1)fps.textContent=`${((tiempos.length-1)/((now-tiempos[0])/1000)).toFixed(1)} cuadros/s`;
      if(now<pausa)continue;
      const ev=revisar(r,m,{...p,buscar:o.buscar},prev,caps.length?caps[0].med.ratio:null);prev=ev.caja||null;
      const E=ev.E,listo=E.det&&E.comp&&E.dist&&E.marca&&E.ang&&E.quieto;
      const color=!E.det?'rojo':listo?'verde':(E.comp&&E.dist?'ambar':'rojo');
      const med=E.det?medir(r,m,cd.width,cd.height,p):null;
      ult=E.det&&E.marca&&E.comp?{ok:true,r,med}:null;man.disabled=!ult;
      dibujar(E.det?r:null,m,color);chips(E);w.dataset.estado=color;
      if(listo){buenos.push({r,med});prog.hidden=false;prog.style.setProperty('--p',buenos.length/REGLAS.seguidos);
        msg.textContent=buenos.length<REGLAS.seguidos?'Quieto…':'Listo';
        if(buenos.length>=REGLAS.seguidos){capturar(buenos,false);prog.hidden=true;}}
      else{buenos=[];prog.hidden=true;msg.textContent=ev.msg;}
      if(mot.modo!=='worker')await new Promise(z=>setTimeout(z,Math.min(300,(performance.now()-t0)*.3)));
    }
  });
}
window.CamVivo={abrir,REGLAS,medir,revisar};
})();
