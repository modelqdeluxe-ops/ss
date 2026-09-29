/* Rumentis Beta: peso por cámara.
   Dos formas de medir, con la marca de medida (un cuadro impreso de 16 cm, ArUco MIP 36h12 id 7) para saber cuántos
   píxeles son un centímetro:
   - En vivo (camvivo.js): el video marca la silueta en rojo, ámbar o verde y toma sola una foto por ángulo cuando todo
     está bien. Con dos ángulos se estima el volumen: kg = c · (área principal en cm² × ancho del otro ángulo en cm / 1000).
       Personas (para probar la beta sin estar en la finca): de frente y de costado; da la estatura y el peso.
       Ganado: de costado y por detrás; da el peso.
   - Una sola foto de costado (ganado): kg = a · área^b.
   Cada fórmula se ajusta con la báscula (S.config.pesoCam.cal): con cada medición calibrada la app aprende.
   Las fotos de un lote se juntan en un pesaje (promedio, y el peso de cada arete si se indica) que se guarda como
   cualquier otro, marcado como hecho con cámara.
   Solo se activa en Rumentis Beta (config.js: beta:true). */
(function(){
'use strict';
const CFG=window.RUMENTIS||{};if(!CFG.beta||CFG.app!=='jefe'||!window.Vision)return;
const MARCA_CM=16,MARCA_ID=7;
const tr=t=>window.I18N&&I18N.txt?I18N.txt(t):t;
// una sola foto (sin calibrar): un novillo de 450 kg de costado mide unos 12,500 cm² (con patas y cabeza)
const DEF={a:450/Math.pow(12500,1.5),b:1.5,err:.15};

/* ---------- los dos modos en vivo ---------- */
const MODOS={
  persona:{clase:Vision.PERSONA,c:.58,   // 70 kg: unos 5,000 cm² de frente × 24 cm de profundidad
    buscar:'Párate frente a la cámara, de cuerpo completo',
    pasos:[{id:'frente',nombre:'De frente',instr:'Junto a la marca, con los brazos un poco separados del cuerpo.',girar:'Ponte de frente, con los brazos un poco separados',ang:{min:.24},dist:{eje:'alto',min:.55,max:.85}},
      {id:'costado',nombre:'De costado',instr:'Junto a la marca, con los brazos pegados al cuerpo.',girar:'Gírate de costado, con los brazos pegados',ang:{max:.3,rel:.75},dist:{eje:'alto',min:.55,max:.85},banda:[.18,.55]}]},
  ganado:{clase:Vision.VACA,c:.72,       // novillo de 450 kg: unos 12,500 cm² de costado × 50 cm de ancho
    buscar:'Apunta al animal, que se vea completo',
    pasos:[{id:'costado',nombre:'De costado',instr:'El animal de costado, pegado a la marca.',girar:'Que el animal quede de costado',ang:{min:1.15},dist:{eje:'ancho',min:.55,max:.88}},
      {id:'atras',nombre:'Por detrás',instr:'El animal de espaldas a la cámara, con la marca a su lado.',girar:'Que el animal quede de espaldas a la cámara',ang:{max:.9},dist:{eje:'alto',min:.55,max:.85},banda:[.05,.55]}]}
};
const MKEY='rumentis-pc-modo';
UI.pc=UI.pc||{lote:'',items:[]};
if(!UI.pc.modo){let m=null;try{m=localStorage.getItem(MKEY);}catch(e){}UI.pc.modo=MODOS[m]?m:'persona';}

/* ---------- la marca de medida ---------- */
let arP=null;
function script(src){return new Promise((ok,mal)=>{const s=document.createElement('script');s.src=src;s.onload=()=>ok();s.onerror=()=>mal(new Error('script'));document.head.appendChild(s);});}
function aruco(){if(!arP)arP=(async()=>{const b=new URL(Vision.BASE,location.href).href;if(!window.CV)await script(b+'cv.js');if(!window.AR)await script(b+'aruco.js');})();arP.catch(()=>{arP=null;});return arP;}
const dist=(p,q)=>Math.hypot(p.x-q.x,p.y-q.y);
// busca la marca en la foto (a más resolución que el modelo, para que salga aunque esté lejos)
async function buscarMarca(img){
  await aruco();const w0=img.naturalWidth||img.width,h0=img.naturalHeight||img.height,k=Math.min(1,1600/Math.max(w0,h0));
  const c=document.createElement('canvas');c.width=Math.round(w0*k);c.height=Math.round(h0*k);const x=c.getContext('2d');x.drawImage(img,0,0,c.width,c.height);
  const det=new AR.Detector({dictionaryName:'ARUCO_MIP_36h12'});const ms=det.detect(x.getImageData(0,0,c.width,c.height));
  const m=ms.find(z=>z.id===MARCA_ID);if(!m)return null;
  const P=m.corners.map(p=>({x:p.x/k,y:p.y/k})),L=[0,1,2,3].map(i=>dist(P[i],P[(i+1)%4]));
  const lado=L.reduce((a,b)=>a+b,0)/4,sesgo=Math.max(...L)/Math.min(...L);
  return {esquinas:P,lado,pxcm:lado/MARCA_CM,sesgo};   // px de la foto original por cm
}

/* ---------- calibración con la báscula ---------- */
const CAL=()=>((S.config.pesoCam||{}).cal)||[];
// una sola foto: las calibraciones de antes (sin modo) y las de ganado en vivo (traen el área de costado)
function modelo(){
  const C=CAL().filter(c=>(!c.modo||c.modo==='ganado')&&c.A>0&&c.kg>0);if(C.length<2)return {...DEF,n:C.length};
  const X=C.map(c=>Math.log(c.A)),Y=C.map(c=>Math.log(c.kg)),n=C.length;let a,b;
  if(n<6){b=DEF.b;a=Math.exp(Y.reduce((s,y,i)=>s+y-b*X[i],0)/n);}
  else{const mx=X.reduce((s,v)=>s+v,0)/n,my=Y.reduce((s,v)=>s+v,0)/n;let sxy=0,sxx=0;for(let i=0;i<n;i++){sxy+=(X[i]-mx)*(Y[i]-my);sxx+=(X[i]-mx)**2;}
    b=sxx>0?Math.min(1.9,Math.max(1.1,sxy/sxx)):DEF.b;a=Math.exp(my-b*mx);}
  const res=C.map((c,i)=>Y[i]-Math.log(a)-b*X[i]),sd=Math.sqrt(res.reduce((s,r)=>s+r*r,0)/Math.max(1,n-1));
  return {a,b,n,err:Math.max(.04,Math.min(.25,n<6?Math.max(sd,.08):sd))};
}
const estimar=A=>{const m=modelo();return {kg:m.a*Math.pow(A,m.b),err:m.err,n:m.n};};
/* en vivo, por modo: V = área principal (cm²) × ancho del otro ángulo (cm) / 1000; kg = c · V^b.
   Con 1 a 5 calibraciones se ajusta c (mediana de kg/V); con 6 o más, también el exponente. */
const calModo=modo=>CAL().filter(c=>c.modo===modo&&c.V>0&&c.kg>0);
function modeloV(modo){
  const C=calModo(modo),n=C.length,c0=MODOS[modo].c;
  if(!n)return {c:c0,b:1,n:0,err:.15};
  const X=C.map(z=>Math.log(z.V)),Y=C.map(z=>Math.log(z.kg));let c,b=1;
  if(n<6){const r=C.map(z=>z.kg/z.V).sort((a,b)=>a-b);c=n%2?r[(n-1)/2]:(r[n/2-1]+r[n/2])/2;}
  else{const mx=X.reduce((s,v)=>s+v,0)/n,my=Y.reduce((s,v)=>s+v,0)/n;let sxy=0,sxx=0;for(let i=0;i<n;i++){sxy+=(X[i]-mx)*(Y[i]-my);sxx+=(X[i]-mx)**2;}
    b=sxx>0?Math.min(1.3,Math.max(.7,sxy/sxx)):1;c=Math.exp(my-b*mx);}
  const res=C.map((z,i)=>Y[i]-Math.log(c)-b*X[i]),sd=n>1?Math.sqrt(res.reduce((s,r)=>s+r*r,0)/(n-1)):.1;
  return {c,b,n,err:Math.max(.04,Math.min(.25,n<6?Math.max(sd,.06):sd))};
}
/* dos capturas → medidas del cuerpo y peso */
function combinar(modo,caps){
  const [p,s]=caps,V=p.med.A*s.med.banda/1000,m=modeloV(modo);
  const r={modo,V,kg:m.c*Math.pow(V,m.b),err:m.err,n:m.n,A:p.med.A,prof:s.med.banda,score:Math.min(p.med.score,s.med.score),manual:caps.some(c=>c.manual)};
  if(modo==='persona'){r.estatura=(p.med.alto+s.med.alto)/2;
    // las dos tomas deberían dar casi la misma estatura: si no, alguna se midió mal
    if(Math.abs(p.med.alto-s.med.alto)/r.estatura>.06)r.aviso='Las dos tomas dieron estaturas distintas: repite con la marca a la altura del pecho y pegado a la pared.';}
  else{r.largo=p.med.ancho;r.alto=p.med.alto;r.ancho=s.med.banda;}
  if(!r.aviso&&r.manual)r.aviso='Una foto se tomó a mano: revisa que la silueta cubra bien el cuerpo.';
  return r;
}

/* ---------- analizar una foto (ganado, una sola foto) ---------- */
function medidas(seg,pxcm){
  // pxcm es de la foto original; la silueta está en el lienzo del modelo (más chico)
  const W=seg.W,H=seg.H,k=seg.lienzo.width/(seg.fotoW||W),p=pxcm*k;let x0=W,x1=-1,y0=H,y1=-1,borde=0;
  for(let y=0;y<H;y++)for(let x=0;x<W;x++)if(seg.mask[y*W+x]){if(x<x0)x0=x;if(x>x1)x1=x;if(y<y0)y0=y;if(y>y1)y1=y;if(x===0||y===0||x===W-1||y===H-1)borde++;}
  return {A:seg.area/(p*p),L:(x1-x0+1)/p,Al:(y1-y0+1)/p,cortado:borde>Math.max(W,H)*.08};
}
async function analizar(blob){
  const url=URL.createObjectURL(blob),img=new Image();img.src=url;await img.decode();
  const [seg,marca]=await Promise.all([Vision.segmentar(img),buscarMarca(img).catch(()=>null)]);
  seg.fotoW=img.naturalWidth;
  const r={url,seg,marca,ok:false};
  if(!seg.ok){r.motivo='sin-animal';return r;}
  if(!marca){r.motivo='sin-marca';return r;}
  r.med=medidas(seg,marca.pxcm);r.est=estimar(r.med.A);r.ok=true;
  if(r.med.cortado)r.aviso='El animal no se ve completo en la foto: el peso puede salir bajo.';
  else if(marca.sesgo>1.25)r.aviso='La marca se ve torcida: toma la foto más de frente al costado del animal.';
  return r;
}
// la foto con la silueta pintada y la marca encuadrada
function vista(r){
  const s=r.seg,c=document.createElement('canvas');c.width=s.W;c.height=s.H;const x=c.getContext('2d');x.drawImage(s.lienzo,0,0);
  if(s.mask){const d=x.getImageData(0,0,s.W,s.H);for(let i=0;i<s.W*s.H;i++)if(s.mask[i]){const o=i*4;d.data[o]=d.data[o]*.45+46*.55;d.data[o+1]=d.data[o+1]*.45+204*.55;d.data[o+2]=d.data[o+2]*.45+113*.55;}x.putImageData(d,0,0);}
  if(r.marca){const k=s.W/s.fotoW;x.strokeStyle='#F0BF33';x.lineWidth=3;x.beginPath();r.marca.esquinas.forEach((p,i)=>i?x.lineTo(p.x*k,p.y*k):x.moveTo(p.x*k,p.y*k));x.closePath();x.stroke();}
  return c.toDataURL('image/jpeg',.82);
}

/* ---------- la pantalla ---------- */
const lotesAct=()=>calc().act;
const loteSel=()=>{const L=lotesAct();if(!L.find(x=>x.id===UI.pc.lote))UI.pc.lote=L.length?L[0].id:'';return calc().L[UI.pc.lote];};
const marcaSVG=()=>`<svg viewBox="0 0 10 10" class="pc-marca" aria-hidden="true"><rect width="10" height="10" fill="#fff"/><rect x="1" y="1" width="8" height="8" fill="#111"/>${MARCA_BITS().map((b,i)=>b?`<rect x="${2+i%6}" y="${2+Math.floor(i/6)}" width="1" height="1" fill="#fff"/>`:'').join('')}</svg>`;
// los 36 bits de la marca (se sacan del diccionario de aruco.js; mientras no carga, un patrón fijo del mismo id)
let BITS=null;function MARCA_BITS(){if(BITS)return BITS;if(window.AR){const d=new AR.Dictionary('ARUCO_MIP_36h12');BITS=d.codeList[MARCA_ID].split('').map(Number);return BITS;}return Array(36).fill(0);}
const cm=v=>`${nf(v)} cm`;
function tarjetaMarca(){return `<div class="pc-marca-w">${marcaSVG()}<div><b>Marca de medida</b><span>Un cuadro negro de 16 cm. Imprímela en tamaño carta o A4 al 100 %.</span><button type="button" class="btn sm" data-act="pcMarcaPdf">Descargar para imprimir</button></div></div>`;}
function tarjetaCal(modo){
  const cal=calModo(modo),m=modeloV(modo),per=modo==='persona';
  return `<section class="sec">${secH(per?'Calibración con tu báscula':'Calibración con la báscula',cal.length||'')}<div class="card pad pc-cal">
    <p>${!cal.length?(per?'Sin calibrar: el peso es un estimado general. Pésate en una báscula, mídete con la cámara y escribe tu peso: la app aprende.':'Sin calibrar: el peso es un estimado general. Mide animales recién pesados en la báscula y escribe su peso real: la app aprende de tus animales.')
      :`Calibrada con ${pl(cal.length,'medición','mediciones')}. Error típico: ±${Math.round(m.err*100)} %.`}</p>
    <button type="button" class="btn full" data-act="pcCalibrar">${icono('pesaje','i3')}${per?'Calibrar con mi peso':'Calibrar con un animal pesado'}</button>
    ${cal.length?`<div class="rows">${cal.slice(-6).reverse().map(c=>`<div class="row"><div class="tx"><b>${wtxt(c.kg)}</b><span><span>báscula</span> · <span>${ffc(c.f)}</span> · <span>cámara sin calibrar: ${wtxt(MODOS[modo].c*c.V)}</span></span></div></div>`).join('')}</div>
      <button type="button" class="lnk" data-act="pcBorrarCal">Borrar la calibración</button>`:''}</div></section>`;
}
PAGES.pesocam=()=>{
  const modo=UI.pc.modo,per=modo==='persona',x=loteSel(),it=UI.pc.items;
  const prom=it.length?it.reduce((s,r)=>s+r.kg,0)/it.length:0;
  return `<header class="hd">${hdBack('#hoy','Hoy')}<div class="ttl" style="margin-top:-6px"><span class="eyebrow pc-beta">Beta</span><h1>Peso con cámara</h1><p class="sub">${per?'Prueba la medición contigo: la cámara te mide de frente y de costado junto a la marca, y estima tu estatura y tu peso.':'La cámara mide al animal de costado y por detrás junto a la marca, y estima su peso.'} Todo se calcula aquí, sin internet.</p></div></header>
  <main class="bd">
   <div class="seg pc-modo" role="group" aria-label="Qué medir">${[['persona','Personas'],['ganado','Ganado']].map(([v,t])=>`<button type="button" data-act="pcModo" data-m="${v}" aria-pressed="${modo===v}">${t}</button>`).join('')}</div>
   ${per?`<section class="sec"><div class="card pad pc-vivo"><p>Dos tomas: de frente y de costado. Cuando la silueta se pone verde, la foto se toma sola.</p>
       <button type="button" class="btn pri full pc-foto" data-act="pcVivo">${icono('camara','i3')}Medir con la cámara</button></div></section>`
     :lotesAct().length?`<section class="sec">${secH('Lote')}<div class="card pad"><select class="in" id="pcLote" data-pclote>${lotesAct().map(l=>`<option value="${esc(l.id)}"${l.id===UI.pc.lote?' selected':''}>${esc(l.l.nombre)} · ${pl(l.cab,'cabeza','cabezas')}</option>`).join('')}</select>
       <button type="button" class="btn pri full pc-foto" data-act="pcVivo">${icono('camara','i3')}Medir con la cámara</button>
       <button type="button" class="btn full pc-una" data-act="pcFoto">Una sola foto de costado</button></div></section>`
     :`<div class="card pad"><p class="empty">Primero crea un lote.</p></div>`}
   ${!per&&it.length?`<section class="sec">${secH('Pesaje en curso',it.length)}<div class="card pad pc-sesion"><div class="pc-prom"><b>${wtxt(prom)}</b><span>promedio de ${pl(it.length,'animal','animales')} en ${esc(x?x.l.nombre:'')}</span></div>
     <div class="rows">${it.map((r,i)=>`<div class="row"><img class="pc-th" src="${r.img}" alt=""><div class="tx"><b>${wtxt(r.kg)}</b><span>${r.arete?`<span>Arete</span> <span data-no-tr>${esc(r.arete)}</span>`:'<span>Sin arete</span>'} · <span>±${Math.round(r.err*100)} %</span></span></div><button type="button" class="del" data-act="pcQuitar" data-i="${i}" aria-label="Quitar">${ico('x')}</button></div>`).join('')}</div>
     <div class="pc-acts"><button type="button" class="btn" data-act="pcVivo">${icono('camara','i3')}Otro animal</button><button type="button" class="btn pri" data-act="pcGuardar">Guardar pesaje</button></div></div></section>`:''}
   <section class="sec">${secH('Cómo hacerlo')}<div class="card pad pc-como">${tarjetaMarca()}
     <ol class="pc-pasos">${per?`<li>Pega la marca en la pared a la altura del pecho.</li><li>Párate pegado a la pared, junto a la marca. Quien sostiene el teléfono se aleja hasta que quepas de pies a cabeza (o apoya el teléfono y usa la cámara frontal).</li><li>Primero de frente, con los brazos un poco separados; después de costado, con los brazos pegados. Quédate quieto cuando la silueta esté verde.</li>`
       :`<li>Pega la marca en la manga o en la cerca, a la altura de las costillas del animal.</li><li>Con el animal de costado, pegado a la marca, aléjate hasta que quepa completo. Después, por detrás, con la marca a su lado.</li><li>Sostén el teléfono firme. Cuando la silueta esté verde, la foto se toma sola.</li>`}</ol></div></section>
   ${tarjetaCal(modo)}
   <p class="hint pc-nota">${per?'Beta: sirve para probar la medición. El peso de una persona con cámara es un estimado.':'Beta: compara con la báscula antes de decidir ventas o raciones con este peso.'}</p>
  </main>`;
};
// el dibujo de la marca sale del diccionario de aruco.js: se carga al abrir la página
document.addEventListener('rumentis-pintado',()=>{if(route().p==='pesocam'&&!BITS&&!window.AR)aruco().then(()=>{if(route().p==='pesocam')render();}).catch(()=>{});});
document.addEventListener('change',e=>{const t=e.target;if(t&&t.matches&&t.matches('[data-pclote]')){if(UI.pc.items.length&&t.value!==UI.pc.lote){UI.pc.items=[];toast('Pesaje en curso descartado: era de otro lote.',3500);}UI.pc.lote=t.value;render();}});
ACTS.pcModo=el=>{const m=el.dataset.m;if(!MODOS[m]||m===UI.pc.modo)return;UI.pc.modo=m;try{localStorage.setItem(MKEY,m);}catch(e){}render();};

/* ---------- medir en vivo ---------- */
let ULT=null;
async function medirVivo(){
  const modo=UI.pc.modo,M=MODOS[modo];
  if(!window.CamVivo){toast('La cámara en vivo no está disponible.');return null;}
  const caps=await CamVivo.abrir({titulo:'Peso con cámara',clase:M.clase,buscar:M.buscar,pasos:M.pasos});
  if(!caps||caps.length<M.pasos.length)return null;
  return {...combinar(modo,caps),caps};
}
const medHTML=r=>r.modo==='persona'
  ?`<div class="pc-med"><div><b>${cm(r.estatura)}</b><span>estatura</span></div><div><b>${nf(r.A/1e4,2)} m²</b><span>de frente</span></div><div><b>${cm(r.prof)}</b><span>de fondo</span></div><div><b>${Math.round(r.score*100)} %</b><span>seguridad</span></div></div>`
  :`<div class="pc-med"><div><b>${cm(r.largo)}</b><span>largo</span></div><div><b>${cm(r.alto)}</b><span>alto</span></div><div><b>${cm(r.ancho)}</b><span>ancho</span></div><div><b>${Math.round(r.score*100)} %</b><span>seguridad</span></div></div>`;
const capsHTML=r=>`<div class="pc-caps">${r.caps.map(c=>`<figure><img src="${c.img}" alt=""><figcaption>${c.nombre}</figcaption></figure>`).join('')}</div>`;
function resultado(r){
  const x=loteSel(),an=r.modo==='ganado'&&x?(x.animAct||[]):[];ULT=r;
  openSheet(shHead(r.modo==='persona'?'Tu medición':'Peso estimado',r.modo==='persona'?'Peso con cámara':esc(x?x.l.nombre:''))+`<div class="sh-body pc-resw">${capsHTML(r)}
    <div class="pc-kg"><b>${wtxt(r.kg)}</b><span>entre ${wtxt(r.kg*(1-r.err))} y ${wtxt(r.kg*(1+r.err))}</span></div>
    ${r.aviso?`<p class="pc-aviso">${ico('aviso',2)}<span>${r.aviso}</span></p>`:''}
    ${medHTML(r)}
    ${an.length?q('Arete (opcional)',`<select class="in" id="pcArete"><option value="">Sin arete</option>${an.map(a=>`<option value="${esc(a.id)}">${esc(a.arete)}</option>`).join('')}</select>`):''}
    <p class="hint">${r.n?`Calibrado con ${pl(r.n,'medición','mediciones')} de tu báscula.`:'Sin calibrar: estimado general.'}</p>
    <button type="button" class="lnk" data-act="pcCalRes">${r.modo==='persona'?'¿Sabes tu peso? Calibra con tu báscula':'¿Lo pesaste en báscula? Calibra con su peso'}</button></div>
    <div class="sh-foot">${r.modo==='persona'?`<button type="button" class="btn" data-act="cerrar" style="flex:1">Cerrar</button><button type="button" class="btn pri" data-act="pcVivo" style="flex:1.4">Medir otra vez</button>`
      :`<button type="button" class="btn" data-act="cerrar" style="flex:1">Descartar</button><button type="button" class="btn pri" data-act="pcAgregar" style="flex:1.6">Agregar al pesaje</button>`}</div>`);
}
ACTS.pcVivo=async()=>{
  if(UI.pc.modo==='ganado'&&!loteSel()){toast('Primero crea un lote.');return;}
  closeSheet();const r=await medirVivo();if(r)resultado(r);
};
// el peso de báscula de la última medición
function formCal(r){
  openSheet(shHead(r.modo==='persona'?'Calibrar con tu báscula':'Calibrar con la báscula','Peso con cámara')+formWrap('pcCal',`${r.caps?capsHTML(r):`<img class="pc-res" src="${r.img}" alt="">`}
    <p class="hint">La cámara estima <b>${wtxt(r.kg)}</b>. Escribe lo que marcó la báscula${r.modo==='persona'?'':' para este animal'}.</p>
    ${q('Peso de báscula',inp('kg','',{unit:UW(),xl:true,ph:nf(W(r.kg))}))}`,foot('Guardar calibración')));
}
ACTS.pcCalRes=()=>{if(ULT)formCal(ULT);};
ACTS.pcCalibrar=async()=>{closeSheet();const r=await medirVivo();if(r)formCal(r);};
SAVE.pcCal=f=>{const r=ULT;if(!r)return;const v=toKg(num(fv(f,'kg')));
  const [lo,hi]=r.modo==='persona'?[15,250]:[40,1300];if(!(v>=lo&&v<=hi))return ferr(f,'Escribe el peso de la báscula.');
  const pc=S.config.pesoCam||{},hoyF=hoy(),ts=Date.now();
  // en vivo se guarda el volumen (y el área de costado del ganado, que también ajusta la fórmula de una sola foto)
  const e=r.caps?{modo:r.modo,V:Math.round(r.V*10)/10,...(r.modo==='ganado'?{A:Math.round(r.A),L:Math.round(r.largo),Al:Math.round(r.alto)}:{})}
    :{A:Math.round(r.med.A),L:Math.round(r.med.L),Al:Math.round(r.med.Al)};
  put('ajustes','finca',{...S.config,pesoCam:{...pc,cal:(pc.cal||[]).concat({...e,kg:Math.round(v*10)/10,f:hoyF,ts}).slice(-200)}});
  ULT=null;closeSheet();
  if(r.caps){const m=modeloV(r.modo);toast(`Calibración guardada: error típico ±${Math.round(m.err*100)} %.`,4000);}
  else{const m=modelo();toast(m.n<2?'Calibración guardada. Con una más empieza a ajustarse.':`Calibración guardada: error típico ±${Math.round(m.err*100)} %.`,4000);}};
ACTS.pcBorrarCal=()=>confirmar('¿Borrar la calibración?','El peso vuelve a calcularse con el estimado general.','Borrar',()=>{const modo=UI.pc.modo,pc=S.config.pesoCam||{};
  put('ajustes','finca',{...S.config,pesoCam:{...pc,cal:(pc.cal||[]).filter(c=>modo==='persona'?c.modo!=='persona':c.modo==='persona')}});toast('Calibración borrada');});

/* ---------- ganado: una sola foto ---------- */
async function tomarYAnalizar(titulo){
  const b=await Fotos.tomar(titulo,{max:1920});if(!b)return null;
  openSheet(shHead('Analizando la foto','Peso con cámara')+`<div class="sh-body pc-anal"><div class="pc-spin" aria-hidden="true"></div><p>${Vision.listo()?'Buscando al animal y la marca…':'Cargando el modelo de visión la primera vez…'}</p></div>`);
  try{const r=await analizar(b);return r;}catch(e){closeSheet();toast('No se pudo analizar la foto en este teléfono.',4000);return null;}
}
const FALLO={'sin-animal':'No encontré al animal en la foto. Tómala de costado y que se vea completo.','sin-marca':'No encontré la marca de medida. Que se vea completa, sin reflejos ni sombras encima, y más cerca si está pequeña.'};
ACTS.pcFoto=async()=>{
  const x=loteSel();if(!x){toast('Primero crea un lote.');return;}
  const r=await tomarYAnalizar('Foto de costado, junto a la marca');if(!r)return;
  if(!r.ok){openSheet(shHead('No se pudo medir','Peso con cámara')+`<div class="sh-body"><img class="pc-res" src="${vista(r)}" alt=""><p>${FALLO[r.motivo]}</p></div><div class="sh-foot"><button type="button" class="btn" data-act="cerrar" style="flex:1">Cerrar</button><button type="button" class="btn pri" data-act="pcFoto" style="flex:1.4">Tomar otra</button></div>`);return;}
  const e=r.est,an=(x.animAct||[]);r.img=vista(r);r.kg=e.kg;r.err=e.err;ULT=r;
  openSheet(shHead('Peso estimado',esc(x.l.nombre))+`<div class="sh-body pc-resw"><img class="pc-res" src="${r.img}" alt="">
    <div class="pc-kg"><b>${wtxt(e.kg)}</b><span>entre ${wtxt(e.kg*(1-e.err))} y ${wtxt(e.kg*(1+e.err))}</span></div>
    ${r.aviso?`<p class="pc-aviso">${ico('aviso',2)}<span>${r.aviso}</span></p>`:''}
    <div class="pc-med"><div><b>${nf(r.med.L)} cm</b><span>largo</span></div><div><b>${nf(r.med.Al)} cm</b><span>alto</span></div><div><b>${nf(r.med.A/1e4,2)} m²</b><span>silueta</span></div><div><b>${Math.round(r.seg.score*100)} %</b><span>seguridad</span></div></div>
    ${an.length?q('Arete (opcional)',`<select class="in" id="pcArete"><option value="">Sin arete</option>${an.map(a=>`<option value="${esc(a.id)}">${esc(a.arete)}</option>`).join('')}</select>`):''}
    <p class="hint">${e.n<2?'Sin calibrar: estimado general.':`Calibrado con ${pl(e.n,'foto','fotos')} de tu báscula.`}</p>
    <button type="button" class="lnk" data-act="pcCalRes">¿Lo pesaste en báscula? Calibra con su peso</button></div>
    <div class="sh-foot"><button type="button" class="btn" data-act="cerrar" style="flex:1">Descartar</button><button type="button" class="btn pri" data-act="pcAgregar" style="flex:1.6">Agregar al pesaje</button></div>`);
};
ACTS.pcAgregar=()=>{const r=ULT;if(!r||!(r.caps||r.ok))return;const x=loteSel();if(!x)return;const sel=$('#pcArete'),aid=sel?sel.value:'';const a=aid&&(x.animAct||[]).find(z=>z.id===aid);
  UI.pc.items=UI.pc.items.filter(z=>!aid||z.aid!==aid).concat({kg:r.kg,err:r.err,aid:aid||'',arete:a?a.arete:'',img:r.caps?r.caps[0].img:r.img});ULT=null;closeSheet();render();
  toast(`${wtxt(r.kg)} agregado. Mide otro animal o guarda el pesaje.`,3000);};
ACTS.pcQuitar=el=>{UI.pc.items.splice(+el.dataset.i,1);render();};
ACTS.pcGuardar=()=>{const x=loteSel(),it=UI.pc.items;if(!x||!it.length)return;
  const prom=it.reduce((s,r)=>s+r.kg,0)/it.length,pesos={};for(const r of it)if(r.aid)pesos[r.aid]=Math.round(r.kg*10)/10;
  addItem({tipo:'pesaje',f:hoy(),lote:x.id,prom:Math.round(prom*10)/10,cab:it.length,...(Object.keys(pesos).length?{pesos}:{}),parcial:true,metodo:'camara'});
  UI.pc.items=[];render();toast(`${x.l.nombre}: ${wtxt(prom)} de promedio con cámara (${pl(it.length,'animal','animales')})`,4000);};
// la marca para imprimir: PDF carta/A4 con el cuadro de 16 cm y una regla de 10 cm para revisar la escala
ACTS.pcMarcaPdf=async()=>{
  try{await aruco();await Documentos.libs();}catch(e){toast('No se pudo preparar la marca.');return;}
  const {jsPDF}=window.jspdf,d=new jsPDF({unit:'mm',format:'letter'}),u=20,x0=(215.9-10*u)/2+u,y0=30,bits=MARCA_BITS();
  d.setFillColor(0,0,0);d.rect(x0,y0,8*u,8*u,'F');d.setFillColor(255,255,255);
  bits.forEach((b,i)=>{if(b)d.rect(x0+u+(i%6)*u,y0+u+Math.floor(i/6)*u,u,u,'F');});
  d.setFontSize(13);d.text(tr('Rumentis · Marca de medida (16 cm)'),107.95,y0+8*u+14,{align:'center'});
  d.setDrawColor(0,0,0);d.setLineWidth(.6);const ry=y0+8*u+26;d.line(57.95,ry,157.95,ry);d.line(57.95,ry-3,57.95,ry+3);d.line(157.95,ry-3,157.95,ry+3);
  d.setFontSize(10);d.text(tr('Esta línea debe medir 10 cm. Si no, imprime al 100 % (sin ajustar a la página).'),107.95,ry+8,{align:'center'});
  await Documentos.enviar('marca-rumentis-16cm.pdf',d.output('arraybuffer'),'compartir','Marca de medida');
};

/* ---------- entradas: Hoy y el formulario de pesaje ---------- */
const _hoy=PAGES.hoy;
PAGES.hoy=(...a)=>{const h=_hoy(...a),i=h.indexOf('<main class="bd">');if(i<0)return h;const k=i+'<main class="bd">'.length;
  return h.slice(0,k)+`<a class="card pad pc-hoy" href="#pesocam"><span class="mas-ic t-v">${icono('camara','i3')}</span><div><b>Peso con cámara <span class="pc-beta">Beta</span></b><span>Estima el peso con la cámara del teléfono.</span></div>${ico('chev')}</a>`+h.slice(k);};
const _pes=FORMS.pesaje;
if(_pes)FORMS.pesaje=(...a)=>{_pes(...a);const b=$('#sheet .sh-body');if(b&&!b.querySelector('.pc-f'))b.insertAdjacentHTML('afterbegin',`<button type="button" class="btn full pc-f" data-act="pcIr">${icono('camara','i3')}Estimar con cámara (beta)</button>`);};
ACTS.pcIr=()=>{closeSheet();UI.pc.modo='ganado';try{localStorage.setItem(MKEY,'ganado');}catch(e){}location.hash='#pesocam';};
window.PesoCam={analizar,estimar,modelo,modeloV,combinar,medidas,buscarMarca,aruco,MODOS,DEF,MARCA_CM,MARCA_ID};
if(route().p==='pesocam'||route().p==='hoy')render();
})();
