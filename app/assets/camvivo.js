/* Rumentis Beta: cámara en vivo para el peso con cámara.
   Pantalla completa con el video. El modelo rápido (vision.js) marca la silueta en cada cuadro, roja, ámbar o verde,
   y revisa si está todo listo para medir: se ve completo, a buena distancia, en el ángulo que toca, quieto y (en
   ganado) con la persona de referencia al lado. Con todo en verde unos cuadros seguidos, la foto se toma sola y pide
   el siguiente ángulo; mientras tanto el modelo preciso mide esa foto en segundo plano.
   CamVivo.abrir({titulo, clase, ref, buscar, pasos:[{id, nombre, instr, girar, ang:{min,max,rel}, dist:{eje,min,max}, banda}]})
   devuelve una captura por paso ({id, nombre, med, img, manual, fuente}) o null si se cierra antes. Las medidas van en
   píxeles del cuadro: la escala en centímetros la pone pesocam.js con una estatura conocida.
   (ang.rel: la proporción ancho/alto debe bajar a esa fracción de la de la primera toma; p. ej. de frente a costado) */
(function(){
'use strict';
const CFG=window.RUMENTIS||{};if(!CFG.beta||CFG.app!=='jefe'||!window.Vision)return;

/* reglas generales (las de distancia y ángulo vienen en cada paso) */
const REGLAS={score:.5,margen:.02,quieto:.93,seguidos:3,lienzo:960,ref:{alto:.25,forma:1.6}};
const CHIPS=[['det','Detectado'],['comp','Completo'],['dist','Distancia'],['ang','Ángulo'],['ref','Referencia'],['quieto','Quieto']];
const COLOR={verde:[46,204,113],ambar:[240,191,51],rojo:[231,76,60]};

const iou=(a,b)=>{if(!a||!b)return 0;const x0=Math.max(a[0],b[0]),y0=Math.max(a[1],b[1]),x1=Math.min(a[2],b[2]),y1=Math.min(a[3],b[3]);
  const i=Math.max(0,x1-x0)*Math.max(0,y1-y0),u=(a[2]-a[0])*(a[3]-a[1])+(b[2]-b[0])*(b[3]-b[1])-i;return u>0?i/u:0;};
const mediana=v=>{const s=v.filter(x=>isFinite(x)).sort((a,b)=>a-b),n=s.length;return n?(n%2?s[(n-1)/2]:(s[n/2-1]+s[n/2])/2):NaN;};
const pct=(v,p)=>{const s=v.filter(x=>x>0).sort((a,b)=>a-b);return s.length?s[Math.min(s.length-1,Math.floor(p*(s.length-1)+.5))]:0;};
const dentro=(c,mg)=>c[0]>mg&&c[1]>mg&&c[2]<1-mg&&c[3]<1-mg;

/* la rejilla G×G de una silueta cubre una zona Z del cuadro W×H (todo el cuadro o la zona que se sigue): se anota
   su caja en fracciones del cuadro (n), cuántos píxeles mide cada celda (cx, cy) y si toca un borde de la zona que no
   es borde del cuadro (entonces la zona la cortó) */
function aCuadro(s,G,Z,W,H){
  if(!s||!s.ok)return s;const [x0,y0,x1,y1]=s.caja;s.G=G;s.Z=Z;s.cx=Z.w/G;s.cy=Z.h/G;
  s.n=[(Z.x+x0*s.cx)/W,(Z.y+y0*s.cy)/H,(Z.x+x1*s.cx)/W,(Z.y+y1*s.cy)/H];
  s.cortada=(x0<=0&&Z.x>1)||(y0<=0&&Z.y>1)||(x1>=G&&Z.x+Z.w<W-1)||(y1>=G&&Z.y+Z.h<H-1);return s;
}
/* medidas de una silueta en píxeles del cuadro */
function medir(s,W,H,banda){
  const [x0,y0,x1,y1]=s.caja,cx=s.cx,cy=s.cy,h=y1-y0,b=banda||[.2,.6],fs=[];
  for(let y=Math.floor(y0+b[0]*h);y<Math.ceil(y0+b[1]*h);y++)fs.push(s.filas[y]);
  return {W,H,alto:h*cy,ancho:(x1-x0)*cx,area:s.area*cx*cy,banda:pct(fs,.9)*cx,ratio:((x1-x0)*cx)/(h*cy),score:s.score};
}
/* la zona que se sigue en el cuadro siguiente: las cajas encontradas con un margen, de proporción parecida a la del
   entrenamiento (0.6–1.6) y de al menos la mitad del cuadro; así el modelo rápido ve al sujeto con más detalle */
function zonaSig(cajas,W,H){
  const c=cajas.filter(Boolean);if(!c.length)return null;
  let x0=Math.min(...c.map(z=>z[0]))*W,y0=Math.min(...c.map(z=>z[1]))*H,x1=Math.max(...c.map(z=>z[2]))*W,y1=Math.max(...c.map(z=>z[3]))*H;
  let w=Math.max((x1-x0)*1.5,W*.5),h=Math.max((y1-y0)*1.5,H*.5);
  if(w/h<.6)w=h*.6;else if(w/h>1.6)h=w/1.6;
  w=Math.min(w,W);h=Math.min(h,H);if(w>W*.92&&h>H*.92)return null;
  const x=Math.min(W-w,Math.max(0,(x0+x1)/2-w/2)),y=Math.min(H-h,Math.max(0,(y0+y1)/2-h/2));
  return {x,y,w,h};
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
  E.ang=(a.min==null||ratio>=a.min)&&(a.max==null||ratio<=a.max)&&(!a.rel||!ref||ratio<=a.rel*ref);
  E.quieto=iou(c,prev)>=REGLAS.quieto;
  let msg='';
  if(!E.comp)msg=v>d.max*.95?'Aléjate un poco':'Que se vea el cuerpo completo';
  else if(!E.dist)msg=v<d.min?'Acércate':'Aléjate';
  else if(!E.ang)msg=paso.girar;
  else if(conRef&&!E.ref)msg=p&&p.ok?'Que la persona de referencia se vea completa y derecha':'Falta la persona de referencia junto al animal';
  else if(!E.quieto)msg='Quieto…';
  return {E,msg,caja:c};
}
function beep(){try{const A=window.AudioContext||window.webkitAudioContext;if(!A)return;const a=beep.a||(beep.a=new A()),o=a.createOscillator(),g=a.createGain();
  o.frequency.value=1046;g.gain.setValueAtTime(.18,a.currentTime);g.gain.exponentialRampToValueAtTime(.001,a.currentTime+.18);o.connect(g).connect(a.destination);o.start();o.stop(a.currentTime+.2);}catch(e){}}
// la silueta (máscara G×G) pintada sobre un lienzo que muestra el cuadro W×H en (X,Y,Wd,Hd), agrandada con suavizado
function pintarMask(x,s,color,alfa,X,Y,Wd,Hd,W,H){
  const G=s.G,Z=s.Z,kx=Wd/W,ky=Hd/H;X+=Z.x*kx;Y+=Z.y*ky;Wd=Z.w*kx;Hd=Z.h*ky;
  const m=document.createElement('canvas');m.width=m.height=G;const xm=m.getContext('2d'),d=xm.createImageData(G,G),[r,g,b]=COLOR[color];
  for(let i=0;i<G*G;i++)if(s.mask[i]){d.data[i*4]=r;d.data[i*4+1]=g;d.data[i*4+2]=b;d.data[i*4+3]=alfa;}
  xm.putImageData(d,0,0);x.imageSmoothingEnabled=true;x.drawImage(m,X,Y,Wd,Hd);
}

function abrir(o){
  return new Promise(async fin0=>{
    const pasos=o.pasos,conRef=!!o.ref,clases=conRef?[o.clase,Vision.PERSONA]:[o.clase],caps=[];
    let ip=0,activo=true,stream=null,frontal=false,wl=null;
    const w=document.createElement('dialog');w.className='cam cv';w.setAttribute('aria-label',o.titulo||'Peso con cámara');
    w.innerHTML=`<div class="cam-top"><div class="cv-tit"><b class="cv-paso"></b><span class="cv-sub"></span></div><button type="button" class="cam-x" aria-label="Cerrar">${ico('x',2.4)}</button></div>
      <div class="cv-chips">${CHIPS.filter(([k])=>k!=='ref'||conRef).map(([k,t])=>`<span class="cv-chip" data-k="${k}"><i></i>${t}</span>`).join('')}</div>
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
    msg.textContent='Cargando el modelo de visión…';w.classList.add('carga');
    let rap=null;try{rap=await Vision.motor('rapido');}catch(e){if(activo){msg.textContent='No se pudo cargar el modelo de visión en este teléfono.';w.classList.remove('carga');}return;}
    // el preciso se carga mientras tanto: lo necesita la primera foto
    const preP=Vision.motor('preciso');preP.catch(()=>{});
    if(!activo)return;w.classList.remove('carga');
    const cd=document.createElement('canvas'),xd=cd.getContext('2d',{willReadFrequently:true});
    const cr=document.createElement('canvas');cr.width=cr.height=rap.R;const xr=cr.getContext('2d',{willReadFrequently:true});
    let prev=null,buenos=[],ult=null,tiempos=[],pausa=0,fallas=0,zona=null;
    // dónde se ve el video en pantalla (object-fit: contain)
    const rect=()=>{const bw=v.clientWidth,bh=v.clientHeight,vw=v.videoWidth,vh=v.videoHeight;if(!vw||!bw)return null;const k=Math.min(bw/vw,bh/vh);
      return {x:(bw-vw*k)/2,y:(bh-vh*k)/2,w:vw*k,h:vh*k};};
    function dibujar(s,p,color){
      const bw=v.clientWidth,bh=v.clientHeight,dpr=Math.min(2,window.devicePixelRatio||1);if(lz.width!==Math.round(bw*dpr)||lz.height!==Math.round(bh*dpr)){lz.width=Math.round(bw*dpr);lz.height=Math.round(bh*dpr);}
      const x=lz.getContext('2d');x.setTransform(dpr,0,0,dpr,0,0);x.clearRect(0,0,bw,bh);const R=rect();if(!R)return;
      if(p&&p.ok)pintarMask(x,p,'verde',60,R.x,R.y,R.w,R.h,cd.width,cd.height);
      if(s&&s.ok){pintarMask(x,s,color,125,R.x,R.y,R.w,R.h,cd.width,cd.height);
        // esquinas de la caja
        const [a0,b0,a1,b1]=s.n,X0=R.x+a0*R.w,Y0=R.y+b0*R.h,X1=R.x+a1*R.w,Y1=R.y+b1*R.h,L=Math.min(28,(X1-X0)/4,(Y1-Y0)/4),[cr_,cg,cb]=COLOR[color];
        x.strokeStyle=`rgb(${cr_},${cg},${cb})`;x.lineWidth=4;x.lineCap='round';x.beginPath();
        for(const [px,py,sx,sy] of [[X0,Y0,1,1],[X1,Y0,-1,1],[X0,Y1,1,-1],[X1,Y1,-1,-1]]){x.moveTo(px,py+sy*L);x.lineTo(px,py);x.lineTo(px+sx*L,py);}x.stroke();}
    }
    function chips(E){for(const el of w.querySelectorAll('.cv-chip')){const s=E[el.dataset.k];el.dataset.s=s==null?'':s?'ok':'no';}}
    // la foto que se guarda: el cuadro con la silueta pintada
    function foto(fc,s){const c=document.createElement('canvas'),k=Math.min(1,720/Math.max(fc.width,fc.height));c.width=Math.round(fc.width*k);c.height=Math.round(fc.height*k);
      const x=c.getContext('2d');x.drawImage(fc,0,0,c.width,c.height);if(s&&s.ok)pintarMask(x,s,'verde',105,0,0,c.width,c.height,fc.width,fc.height);return c.toDataURL('image/jpeg',.82);}
    // medida precisa de una foto capturada (en segundo plano); si no sale, se queda la del modelo rápido
    async function precisa(fc){
      try{const pre=await preP,c=document.createElement('canvas');c.width=c.height=pre.R;const x=c.getContext('2d',{willReadFrequently:true});x.drawImage(fc,0,0,pre.R,pre.R);
        const r=await pre.correr(x.getImageData(0,0,pre.R,pre.R).data,clases),Z={x:0,y:0,w:fc.width,h:fc.height};
        const s=aCuadro(r.suj[o.clase],r.G,Z,fc.width,fc.height),p=aCuadro(r.suj[Vision.PERSONA],r.G,Z,fc.width,fc.height);
        if(!s||!s.ok||(conRef&&!refOk(p,fc.width,fc.height)))return null;
        return {s,p:conRef?p:null};}catch(e){console.warn(e);return null;}}
    function capturar(lista,manual){
      const p=pasos[ip],u=lista[lista.length-1],fc=document.createElement('canvas');fc.width=cd.width;fc.height=cd.height;fc.getContext('2d').drawImage(cd,0,0);
      const med={};for(const k of ['alto','ancho','area','banda','ratio','score','refAlto'])med[k]=mediana(lista.map(z=>z.med[k]));med.W=cd.width;med.H=cd.height;
      caps.push({id:p.id,nombre:p.nombre,manual:!!manual,rapido:med,fc,s:u.s,banda:p.banda,pend:precisa(fc)});
      try{navigator.vibrate&&navigator.vibrate(120);}catch(e){}beep();
      w.classList.add('flash');setTimeout(()=>w.classList.remove('flash'),260);
      th.src=foto(fc,u.s);th.hidden=false;buenos=[];prev=null;ult=null;tiempos=[];prog.hidden=true;zona=null;
      if(ip+1>=pasos.length){terminar();return;}
      ip++;pintarPaso();msg.textContent=pasos[ip].girar;chips({});dibujar(null,null,'rojo');w.dataset.estado='';pausa=performance.now()+1500;
    }
    async function terminar(){
      pausa=Infinity;man.disabled=true;chips({});dibujar(null,null,'rojo');w.dataset.estado='';msg.textContent='Midiendo…';sub.textContent='';w.classList.add('carga');
      const out=[];
      for(const c of caps){const r=await c.pend;if(!activo)return;
        let med=c.rapido,fuente='rapido',img=null;
        if(r){med=medir(r.s,c.fc.width,c.fc.height,c.banda);if(r.p)med.refAlto=medir(r.p,c.fc.width,c.fc.height).alto;fuente='preciso';img=foto(c.fc,r.s);}
        out.push({id:c.id,nombre:c.nombre,manual:c.manual,med,fuente,img:img||foto(c.fc,c.s)});}
      fin(out);
    }
    man.onclick=()=>{if(!ult)return toast('Que se vea completo para tomar la foto.');const l=buenos.length?buenos:[ult];capturar(l.slice(-REGLAS.seguidos),true);};
    // el ciclo: un cuadro a la vez, lo más rápido que dé el teléfono
    while(activo){
      if(!v.videoWidth||v.readyState<2||performance.now()<pausa){await new Promise(r=>setTimeout(r,80));continue;}
      const p=pasos[ip],vw=v.videoWidth,vh=v.videoHeight,kd=Math.min(1,REGLAS.lienzo/Math.max(vw,vh));
      if(cd.width!==Math.round(vw*kd)||cd.height!==Math.round(vh*kd)){cd.width=Math.round(vw*kd);cd.height=Math.round(vh*kd);}
      xd.drawImage(v,0,0,cd.width,cd.height);const Z=zona||{x:0,y:0,w:cd.width,h:cd.height};xr.drawImage(cd,Z.x,Z.y,Z.w,Z.h,0,0,rap.R,rap.R);
      const t0=performance.now();let r;
      try{r=await rap.correr(xr.getImageData(0,0,rap.R,rap.R).data,clases);fallas=0;}catch(e){console.warn(e);
        if(++fallas>=3){msg.textContent='No se pudo cargar el modelo de visión en este teléfono.';w.dataset.estado='';man.disabled=true;break;}
        await new Promise(z=>setTimeout(z,400));continue;}
      if(!activo)break;
      // (ms y motor quedan en el diálogo para las pruebas)
      w.dataset.ms=Math.round(r.ms);w.dataset.motor=rap.modo;
      const now=performance.now();tiempos.push(now);tiempos=tiempos.filter(t=>now-t<3000);
      if(tiempos.length>2)fps.textContent=`${((tiempos.length-1)/((now-tiempos[0])/1000)).toFixed(1)} cuadros/s`;
      if(now<pausa)continue;
      const W=cd.width,H=cd.height,s=aCuadro(r.suj[o.clase],r.G,Z,W,H),pr=conRef?aCuadro(r.suj[Vision.PERSONA],r.G,Z,W,H):null;
      // si la zona cortó al sujeto, el cuadro siguiente se mira completo
      // (en ganado, mientras no aparezca la persona de referencia, también se mira completo)
      zona=(s&&s.cortada)||(pr&&pr.cortada)||(conRef&&!(pr&&pr.ok))?null:zonaSig([s&&s.ok?s.n:null,pr&&pr.ok?pr.n:null],W,H);
      const ev=revisar(s,pr,W,H,{...p,buscar:o.buscar},prev,caps.length?caps[0].rapido.ratio:null,conRef);prev=ev.caja||null;
      const E=ev.E,listo=E.det&&E.comp&&E.dist&&E.ang&&E.quieto&&(!conRef||E.ref);
      const color=!E.det?'rojo':listo?'verde':(E.comp&&E.dist?'ambar':'rojo');
      const med=E.det?medir(s,W,H,p.banda):null;if(med&&conRef&&E.ref)med.refAlto=medir(pr,W,H).alto;
      ult=E.det&&E.comp&&(!conRef||E.ref)?{s,med}:null;man.disabled=!ult;
      dibujar(E.det?s:null,pr,color);chips(E);w.dataset.estado=color;w.dataset.zona=zona?'si':'no';
      if(listo){buenos.push({s,med});prog.hidden=false;prog.style.setProperty('--p',buenos.length/REGLAS.seguidos);msg.textContent='Quieto…';
        if(buenos.length>=REGLAS.seguidos)capturar(buenos,false);}
      else{buenos=[];prog.hidden=true;msg.textContent=ev.msg;}
      if(rap.modo!=='worker')await new Promise(z=>setTimeout(z,Math.min(200,(performance.now()-t0)*.3)));
    }
  });
}
window.CamVivo={abrir,REGLAS,medir,revisar,zonaSig};
})();
