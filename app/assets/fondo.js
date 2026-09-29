/* Rumentis: fondo vivo, sin partículas, que no le quita fuerza al teléfono.
   - Curvas de nivel que respiran: cada 3 segundos se calcula un relieve nuevo (ruido que cambia lento, marching
     squares) en un rato libre del teléfono, nunca durante el scroll ni al cambiar de página, y se funde con el
     anterior con una transición de opacidad. Así el mapa se deforma suave como agua o tierra viva.
   - Un degradado de color con dos luces suaves y una textura fina, sin dibujos de cerros: las curvas de nivel son
     el motivo y cubren toda la pantalla.
   - Sombras de nubes que cruzan el campo de día (capas que se deslizan, en CSS).
   Todo lo que no se mueve (degradado, luces y textura) se hornea en UNA sola imagen:
   así la tarjeta gráfica del teléfono pinta una capa en vez de ocho con máscaras y mezclas, y le sobra fuerza
   para ir a la tasa de refresco de la pantalla (60, 90 o 120 Hz).
   Todo el movimiento es transform u opacity: el procesador no trabaja cuadro a cuadro. Se detiene en segundo plano,
   mientras tocas o haces scroll, y queda quieto si el teléfono pide menos movimiento. */
(function(){
'use strict';
const R=document.documentElement,reducido=matchMedia('(prefers-reduced-motion: reduce)').matches;
const fondo=document.getElementById('fondo');if(!fondo||!document.createElement('canvas').getContext)return;

/* ---------- ruido simplex 3D (Stefan Gustavson, dominio público) ---------- */
const G3=[[1,1,0],[-1,1,0],[1,-1,0],[-1,-1,0],[1,0,1],[-1,0,1],[1,0,-1],[-1,0,-1],[0,1,1],[0,-1,1],[0,1,-1],[0,-1,-1]];
const P=new Uint8Array(512);{const p=[...Array(256).keys()];let s=20260928;for(let i=255;i>0;i--){s=(s*1103515245+12345)>>>0;const j=s%(i+1);[p[i],p[j]]=[p[j],p[i]];}for(let i=0;i<512;i++)P[i]=p[i&255];}
function ruido(x,y,z){
  const F=1/3,Gc=1/6,s=(x+y+z)*F,i=Math.floor(x+s),j=Math.floor(y+s),k=Math.floor(z+s),t=(i+j+k)*Gc;
  const x0=x-i+t,y0=y-j+t,z0=z-k+t;let i1,j1,k1,i2,j2,k2;
  if(x0>=y0){if(y0>=z0){i1=1;j1=0;k1=0;i2=1;j2=1;k2=0;}else if(x0>=z0){i1=1;j1=0;k1=0;i2=1;j2=0;k2=1;}else{i1=0;j1=0;k1=1;i2=1;j2=0;k2=1;}}
  else{if(y0<z0){i1=0;j1=0;k1=1;i2=0;j2=1;k2=1;}else if(x0<z0){i1=0;j1=1;k1=0;i2=0;j2=1;k2=1;}else{i1=0;j1=1;k1=0;i2=1;j2=1;k2=0;}}
  const x1=x0-i1+Gc,y1=y0-j1+Gc,z1=z0-k1+Gc,x2=x0-i2+2*Gc,y2=y0-j2+2*Gc,z2=z0-k2+2*Gc,x3=x0-1+.5,y3=y0-1+.5,z3=z0-1+.5;
  const ii=i&255,jj=j&255,kk=k&255;let n=0;
  const c=(xx,yy,zz,g)=>{let t=.6-xx*xx-yy*yy-zz*zz;if(t<0)return 0;t*=t;const q=G3[g%12];return t*t*(q[0]*xx+q[1]*yy+q[2]*zz);};
  n+=c(x0,y0,z0,P[ii+P[jj+P[kk]]]);n+=c(x1,y1,z1,P[ii+i1+P[jj+j1+P[kk+k1]]]);n+=c(x2,y2,z2,P[ii+i2+P[jj+j2+P[kk+k2]]]);n+=c(x3,y3,z3,P[ii+1+P[jj+1+P[kk+1]]]);
  return 32*n;
}

/* ---------- colores de la paleta ---------- */
const sonda=document.createElement('i');sonda.style.cssText='position:absolute;width:0;height:0;visibility:hidden';fondo.appendChild(sonda);
// [r,g,b,a] del color CSS (acepta var() y color-mix)
function rgb(css){sonda.style.color='';sonda.style.color=css;const v=getComputedStyle(sonda).color;
  let m=/rgba?\(([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:[,\s/]+([\d.]+))?/.exec(v);if(m)return [+m[1],+m[2],+m[3],m[4]!=null?+m[4]:1];
  m=/color\(srgb ([\d.e-]+) ([\d.e-]+) ([\d.e-]+)(?: \/ ([\d.]+))?/.exec(v);if(m)return [m[1]*255,m[2]*255,m[3]*255].map(Math.round).concat(m[4]!=null?+m[4]:1);return [120,130,125,1];}
let COL=null;
function leerColores(){
  const noche=/noche|madrugada/.test(R.dataset.momento||'')||R.dataset.modo==='dark';
  COL={ink:rgb('var(--ink)'),bg:rgb('var(--bg)'),cielo:rgb('var(--cielo)'),amb1:rgb('var(--amb1)'),amb2:rgb('var(--amb2)'),
    luzHd:rgb('color-mix(in srgb,var(--head) 14%,transparent)'),suelo:rgb('color-mix(in srgb,var(--cerro) 7%,var(--bg))'),
    c3a:rgb('color-mix(in srgb,var(--cerro) 13%,var(--bg))'),c3b:rgb('color-mix(in srgb,var(--cerro) 8%,var(--bg))'),
    c2a:rgb('color-mix(in srgb,var(--cerro) 20%,var(--bg))'),c2b:rgb('color-mix(in srgb,var(--cerro) 12%,var(--bg))'),cerroA:rgb('color-mix(in srgb,var(--cerro) 30%,var(--bg))'),cerroB:rgb('color-mix(in srgb,var(--cerro) 20%,var(--bg))'),
    pasto:rgb('color-mix(in srgb,var(--cerro) 55%,var(--bg))'),pasto2:rgb('color-mix(in srgb,var(--cerro) 40%,var(--bg))'),luz:rgb('var(--arete)'),noche,
    linea:rgb('color-mix(in srgb,var(--cerro) 55%,var(--ink))'),bajo:rgb('color-mix(in srgb,var(--cerro) 26%,var(--bg))'),medio:rgb('color-mix(in srgb,var(--arete) 9%,var(--bg))'),
    dia:!noche&&!/lluvia|tormenta|niebla/.test(R.dataset.clima||'')};
}
const rgba=(c,a)=>`rgba(${c[0]|0},${c[1]|0},${c[2]|0},${(a==null?(c[3]==null?1:c[3]):a).toFixed(3)})`;

/* ---------- capas ----------
   Se dibuja en lienzos fuera de pantalla y se muestra como imagen (blob): las imágenes no le cuestan al teléfono
   en cada cambio de página como sí los lienzos en pantalla. */
const mk=(tag,cls)=>{const e=document.createElement(tag);e.className=cls;return e;};
// orden: imagen horneada, curvas de nivel y sombras de nubes
const base=mk('i','f-base'),capaT=mk('i','f-vivo'),imA=mk('b','on'),imB=mk('b','');capaT.append(imA,imB);
const sombras=mk('i','f-sombras');sombras.innerHTML='<b></b><b></b>';
fondo.append(base,capaT,sombras);
const lienzo=(w,h)=>{const c=document.createElement('canvas');c.width=Math.max(1,Math.round(w*dpr));c.height=Math.max(1,Math.round(h*dpr));const x=c.getContext('2d');x.setTransform(dpr,0,0,dpr,0,0);return {c,x};};
const URLS=new Map();
function aImagen(el,cv,listo){
  const poner=u=>{const vieja=URLS.get(el);URLS.set(el,u);el.style.backgroundImage=`url("${u}")`;if(vieja&&vieja.startsWith('blob:'))setTimeout(()=>URL.revokeObjectURL(vieja),4000);if(listo)listo();};
  if(cv.toBlob)cv.toBlob(b=>{if(b)poner(URL.createObjectURL(b));else poner(cv.toDataURL());},'image/png');else poner(cv.toDataURL());
}
let W=0,H=0,dpr=1;

/* ---------- curvas de nivel que respiran ---------- */
const PASO=13,NIV=[-.72,-.54,-.36,-.18,0,.18,.36,.54,.72],CADA=3000;
let campo=null,cols=0,filas=0,frente=imA,t0=performance.now();
function relieve(t){
  cols=Math.ceil(W/PASO)+2;filas=Math.ceil(H/PASO)+2;if(!campo||campo.length!==cols*filas)campo=new Float32Array(cols*filas);
  const z=t*0.000028,fx=t*0.0000045,esc=1/210;
  for(let j=0;j<filas;j++)for(let i=0;i<cols;i++){const x=i*PASO*esc+fx,y=j*PASO*esc;
    campo[j*cols+i]=ruido(x,y,z)*.78+ruido(x*2.1+7.3,y*2.1+1.7,z*1.6)*.22;}
}
function curvas(cx){
  const w=Math.min(1,W/420),lin=COL.noche?.12:.15;
  NIV.forEach((nv,n)=>{
    cx.beginPath();
    for(let j=0;j<filas-1;j++)for(let i=0;i<cols-1;i++){
      const a=campo[j*cols+i],b=campo[j*cols+i+1],c=campo[(j+1)*cols+i+1],d=campo[(j+1)*cols+i];
      const k=(a>nv?8:0)|(b>nv?4:0)|(c>nv?2:0)|(d>nv?1:0);if(k===0||k===15)continue;
      const x=i*PASO,y=j*PASO,e=(p,q)=>(nv-p)/(q-p);
      const T=[x+PASO*e(a,b),y],Rr=[x+PASO,y+PASO*e(b,c)],B=[x+PASO*e(d,c),y+PASO],L=[x,y+PASO*e(a,d)];
      const seg=(p,q)=>{cx.moveTo(p[0],p[1]);cx.lineTo(q[0],q[1]);};
      switch(k){case 1:case 14:seg(L,B);break;case 2:case 13:seg(B,Rr);break;case 3:case 12:seg(L,Rr);break;case 4:case 11:seg(T,Rr);break;
        case 5:seg(L,T);seg(B,Rr);break;case 6:case 9:seg(T,B);break;case 7:case 8:seg(L,T);break;case 10:seg(T,Rr);seg(L,B);break;}
    }
    const maestra=n===4;
    cx.strokeStyle=rgba(COL.linea,maestra?lin*1.55:lin);cx.lineWidth=maestra?1.5*w+.25:w+.2;cx.stroke();
  });
  // se desvanece arriba y abajo: horneado aquí en vez de una máscara CSS, que le cuesta a la tarjeta gráfica en cada cuadro
  cx.globalCompositeOperation='destination-in';const g=cx.createLinearGradient(0,0,0,H);
  g.addColorStop(.06,'rgba(0,0,0,0)');g.addColorStop(.3,'#000');g.addColorStop(1,'#000');
  cx.fillStyle=g;cx.fillRect(0,0,W,H);cx.globalCompositeOperation='source-over';
}
/* dibuja el relieve siguiente en el lienzo de atrás y lo funde con el de adelante */
let ocupadoHasta=0,espera=0;
function siguiente(){
  if(document.hidden||PAUSA||performance.now()<ocupadoHasta){programar(600);return;}
  const atras=frente===imA?imB:imA,{c,x}=lienzo(W,H);
  relieve(performance.now()-t0+CADA);curvas(x);
  aImagen(atras,c,()=>{atras.classList.add('on');frente.classList.remove('on');frente=atras;programar(CADA);});
}
function programar(ms){clearTimeout(espera);if(reducido)return;
  espera=setTimeout(()=>{if(window.requestIdleCallback)requestIdleCallback(siguiente,{timeout:1200});else siguiente();},ms);}
// el scroll y el cambio de página tienen prioridad
const ocupar=ms=>{ocupadoHasta=Math.max(ocupadoHasta,performance.now()+ms);window.OCUPADO_HASTA=ocupadoHasta;};
addEventListener('scroll',()=>ocupar(450),{passive:true,capture:true});
addEventListener('touchmove',()=>ocupar(450),{passive:true});
addEventListener('hashchange',()=>ocupar(900));
// mientras tocas o haces scroll, el fondo se queda quieto: toda la tarjeta gráfica para lo que estás haciendo
let tq=0;const quieto=()=>{if(!fondo.classList.contains('toque'))fondo.classList.add('toque');clearTimeout(tq);tq=setTimeout(()=>fondo.classList.remove('toque'),900);};
addEventListener('pointerdown',quieto,{passive:true,capture:true});addEventListener('scroll',quieto,{passive:true,capture:true});addEventListener('touchmove',quieto,{passive:true});

/* ---------- lo que no se mueve, horneado en una imagen ---------- */
let GRANO=null;
function grano(){
  // textura fina: puntitos al azar en un mosaico chico que se repite
  const oscuro=R.dataset.modo==='dark',k=oscuro?'o':'c';if(GRANO&&GRANO.k===k)return GRANO.p;
  const n=128,t=document.createElement('canvas');t.width=t.height=n;const x=t.getContext('2d'),d=x.createImageData(n,n);let s=99;
  for(let i=0;i<n*n;i++){s=(s*16807)%2147483647;const v=s/2147483647;const o=i*4;
    if(oscuro){d.data[o]=d.data[o+1]=255;d.data[o+2]=240;d.data[o+3]=Math.round(v*v*14);}else{d.data[o]=70;d.data[o+1]=62;d.data[o+2]=45;d.data[o+3]=Math.round(v*v*22);}}
  x.putImageData(d,0,0);GRANO={k,p:t};return t;
}
function hornear(){
  const {c,x}=lienzo(W,H);
  // degradado en diagonal: el tono del cielo arriba, el fondo en medio y un verde de campo abajo
  let g=x.createLinearGradient(0,0,W*.45,H);g.addColorStop(0,rgba(COL.cielo,1));g.addColorStop(.38,rgba(COL.bg,1));g.addColorStop(.7,rgba(COL.medio,1));g.addColorStop(1,rgba(COL.bajo,1));
  x.fillStyle=g;x.fillRect(0,0,W,H);
  // luces suaves (las dos "orbes") y la luz del encabezado que baja
  const orbe=(cx,cy,r,col)=>{const q=x.createRadialGradient(cx,cy,0,cx,cy,r);q.addColorStop(0,rgba(col,(col[3]==null?1:col[3])*.9));q.addColorStop(.62,rgba(col,0));x.fillStyle=q;x.fillRect(cx-r,cy-r,r*2,r*2);};
  orbe(W*.95,W*.3,W*.6,COL.amb1);orbe(0,H*.55+W*.4,W*.7,COL.amb2);
  x.save();x.translate(W/2,0);x.scale(W*1.2,H*.42);g=x.createRadialGradient(0,0,0,0,0,1);g.addColorStop(0,rgba(COL.luzHd));g.addColorStop(.7,rgba(COL.luzHd,0));x.fillStyle=g;x.fillRect(-1,0,2,1);x.restore();
  x.save();x.setTransform(1,0,0,1,0,0);x.fillStyle=x.createPattern(grano(),'repeat');x.fillRect(0,0,c.width,c.height);x.restore();
  // se cambia cuando la imagen ya está lista para pintarse: sin un cuadro vacío
  const tmp=mk('i','');aImagen(tmp,c,()=>{const u=URLS.get(tmp);URLS.delete(tmp);const im=new Image();im.src=u;
    const poner=()=>{const vieja=base.dataset.u;base.dataset.u=u;base.style.backgroundImage=`url("${u}")`;fondo.classList.add('horneado');
      if(vieja&&vieja.startsWith('blob:'))setTimeout(()=>URL.revokeObjectURL(vieja),4000);};
    (im.decode?im.decode():Promise.resolve()).then(poner,poner);});
}
let VIENTO=.25;
function viento(){try{const c=window.Clima&&Clima.actual();if(c&&c.viento!=null){VIENTO=Math.max(.12,Math.min(1,((+c.viento||0)+(+c.rachas||0)*.5)/45));}}catch(e){}}

/* ---------- armar ---------- */
function medir(){dpr=Math.min(1.5,devicePixelRatio||1);W=innerWidth;H=innerHeight;}
function todo(){medir();leerColores();const {c,x}=lienzo(W,H);relieve(performance.now()-t0);curvas(x);aImagen(frente,c);hornear();programar(CADA);}
let PAUSA=false;
new MutationObserver(()=>{clearTimeout(todo._t);todo._t=setTimeout(()=>{const antes=JSON.stringify(COL);leerColores();if(JSON.stringify(COL)!==antes){hornear();}},120);}).observe(R,{attributes:true,attributeFilter:['data-modo','data-paleta','data-momento','data-epoca','data-clima','data-tema']});
addEventListener('resize',()=>{clearTimeout(medir._t);medir._t=setTimeout(todo,200);});
document.addEventListener('visibilitychange',()=>{R.classList.toggle('fondo-quieto',document.hidden);});
setInterval(viento,60000);
function iniciar(){todo();viento();}
if(document.readyState==='loading')addEventListener('DOMContentLoaded',iniciar);else iniciar();
window.FondoVivo={pausa:v=>{PAUSA=!!v;R.classList.toggle('fondo-quieto',!!v);},viento:v=>{VIENTO=Math.max(.12,Math.min(1,v));},estado:()=>({W,H,VIENTO})};
})();
