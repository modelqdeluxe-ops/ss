/* Rumentis Beta: peso por cámara.
   1. La foto de costado del animal, junto a la marca de medida (un cuadro impreso de 16 cm, ArUco MIP 36h12 id 7).
   2. vision.js recorta la silueta del animal (RF-DETR Seg) y aruco.js encuentra la marca: con ella se sabe cuántos
      píxeles son un centímetro.
   3. Con la silueta en centímetros (área, largo y alto) se estima el peso: kg = a · área^b. De fábrica a y b son un
      punto de partida razonable; con cada foto calibrada con la báscula la fórmula se ajusta a los animales de la finca.
   4. Las fotos de un lote se juntan en un pesaje (promedio, y el peso de cada arete si se indica) que se guarda como
      cualquier otro, marcado como hecho con cámara.
   Solo se activa en Rumentis Beta (config.js: beta:true). */
(function(){
'use strict';
const CFG=window.RUMENTIS||{};if(!CFG.beta||CFG.app!=='jefe'||!window.Vision)return;
const MARCA_CM=16,MARCA_ID=7;
const tr=t=>window.I18N&&I18N.txt?I18N.txt(t):t;
// punto de partida (sin calibrar): un novillo de 450 kg de costado mide unos 12,500 cm² (con patas y cabeza)
const DEF={a:450/Math.pow(12500,1.5),b:1.5,err:.15};

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
function modelo(){
  const C=CAL().filter(c=>c.A>0&&c.kg>0);if(C.length<2)return {...DEF,n:C.length};
  const X=C.map(c=>Math.log(c.A)),Y=C.map(c=>Math.log(c.kg)),n=C.length;let a,b;
  if(n<6){b=DEF.b;a=Math.exp(Y.reduce((s,y,i)=>s+y-b*X[i],0)/n);}
  else{const mx=X.reduce((s,v)=>s+v,0)/n,my=Y.reduce((s,v)=>s+v,0)/n;let sxy=0,sxx=0;for(let i=0;i<n;i++){sxy+=(X[i]-mx)*(Y[i]-my);sxx+=(X[i]-mx)**2;}
    b=sxx>0?Math.min(1.9,Math.max(1.1,sxy/sxx)):DEF.b;a=Math.exp(my-b*mx);}
  const res=C.map((c,i)=>Y[i]-Math.log(a)-b*X[i]),sd=Math.sqrt(res.reduce((s,r)=>s+r*r,0)/Math.max(1,n-1));
  return {a,b,n,err:Math.max(.04,Math.min(.25,n<6?Math.max(sd,.08):sd))};
}
const estimar=A=>{const m=modelo();return {kg:m.a*Math.pow(A,m.b),err:m.err,n:m.n};};

/* ---------- analizar una foto ---------- */
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
UI.pc=UI.pc||{lote:'',items:[]};
const lotesAct=()=>calc().act;
const loteSel=()=>{const L=lotesAct();if(!L.find(x=>x.id===UI.pc.lote))UI.pc.lote=L.length?L[0].id:'';return calc().L[UI.pc.lote];};
const marcaSVG=()=>`<svg viewBox="0 0 10 10" class="pc-marca" aria-hidden="true"><rect width="10" height="10" fill="#fff"/><rect x="1" y="1" width="8" height="8" fill="#111"/>${MARCA_BITS().map((b,i)=>b?`<rect x="${2+i%6}" y="${2+Math.floor(i/6)}" width="1" height="1" fill="#fff"/>`:'').join('')}</svg>`;
// los 36 bits de la marca (se sacan del diccionario de aruco.js; mientras no carga, un patrón fijo del mismo id)
let BITS=null;function MARCA_BITS(){if(BITS)return BITS;if(window.AR){const d=new AR.Dictionary('ARUCO_MIP_36h12');BITS=d.codeList[MARCA_ID].split('').map(Number);return BITS;}return Array(36).fill(0);}
PAGES.pesocam=()=>{
  const x=loteSel(),m=modelo(),it=UI.pc.items,cal=CAL();
  const prom=it.length?it.reduce((s,r)=>s+r.kg,0)/it.length:0;
  return `<header class="hd">${hdBack('#hoy','Hoy')}<div class="ttl" style="margin-top:-6px"><span class="eyebrow pc-beta">Beta</span><h1>Peso con cámara</h1><p class="sub">Una foto de costado junto a la marca de medida y el teléfono estima el peso. Todo se calcula aquí, sin internet.</p></div></header>
  <main class="bd">
   ${lotesAct().length?`<section class="sec">${secH('Lote')}<div class="card pad"><select class="in" id="pcLote" data-pclote>${lotesAct().map(l=>`<option value="${esc(l.id)}"${l.id===UI.pc.lote?' selected':''}>${esc(l.l.nombre)} · ${pl(l.cab,'cabeza','cabezas')}</option>`).join('')}</select>
     <button type="button" class="btn pri full pc-foto" data-act="pcFoto">${icono('camara','i3')}Tomar foto de un animal</button></div></section>`
     :`<div class="card pad"><p class="empty">Primero crea un lote.</p></div>`}
   ${it.length?`<section class="sec">${secH('Pesaje en curso',it.length)}<div class="card pad pc-sesion"><div class="pc-prom"><b>${wtxt(prom)}</b><span>promedio de ${pl(it.length,'animal','animales')} en ${esc(x?x.l.nombre:'')}</span></div>
     <div class="rows">${it.map((r,i)=>`<div class="row"><img class="pc-th" src="${r.img}" alt=""><div class="tx"><b>${wtxt(r.kg)}</b><span>${r.arete?`<span>Arete</span> <span data-no-tr>${esc(r.arete)}</span>`:'<span>Sin arete</span>'} · <span>±${Math.round(r.err*100)} %</span></span></div><button type="button" class="del" data-act="pcQuitar" data-i="${i}" aria-label="Quitar">${ico('x')}</button></div>`).join('')}</div>
     <div class="pc-acts"><button type="button" class="btn" data-act="pcFoto">${icono('camara','i3')}Otra foto</button><button type="button" class="btn pri" data-act="pcGuardar">Guardar pesaje</button></div></div></section>`:''}
   <section class="sec">${secH('Cómo tomar la foto')}<div class="card pad pc-como">
     <div class="pc-marca-w">${marcaSVG()}<div><b>Marca de medida</b><span>Un cuadro negro de 16 cm. Imprímela en tamaño carta o A4 al 100 %.</span><button type="button" class="btn sm" data-act="pcMarcaPdf">Descargar para imprimir</button></div></div>
     <ol class="pc-pasos"><li>Pega la marca en la manga o en la cerca, a la altura de las costillas del animal.</li><li>Pon al animal de costado, pegado a la marca, y aléjate hasta que quepa completo en la foto.</li><li>Toma la foto de frente al costado, con el teléfono acostado. Que se vean el animal entero y la marca.</li></ol></div></section>
   <section class="sec">${secH('Calibración con tu báscula',cal.length||'')}<div class="card pad pc-cal">
     <p>${cal.length<2?'Sin calibrar: el peso es un estimado general. Toma fotos de animales recién pesados en la báscula y escribe su peso real: la app aprende de tus animales.':`Calibrada con ${pl(cal.length,'foto','fotos')}. Error típico: ±${Math.round(m.err*100)} %.`}</p>
     <button type="button" class="btn full" data-act="pcCalibrar">${icono('pesaje','i3')}Calibrar con un animal pesado</button>
     ${cal.length?`<div class="rows">${cal.slice(-6).reverse().map(c=>`<div class="row"><div class="tx"><b>${wtxt(c.kg)}</b><span><span>báscula</span> · <span>${ffc(c.f)}</span> · <span>foto: ${wtxt(DEF.a*Math.pow(c.A,DEF.b))} sin calibrar</span></span></div></div>`).join('')}</div>
       <button type="button" class="lnk" data-act="pcBorrarCal">Borrar la calibración</button>`:''}</div></section>
   <p class="hint pc-nota">Beta: compara con la báscula antes de decidir ventas o raciones con este peso.</p>
  </main>`;
};
// el dibujo de la marca sale del diccionario de aruco.js: se carga al abrir la página
document.addEventListener('rumentis-pintado',()=>{if(route().p==='pesocam'&&!BITS&&!window.AR)aruco().then(()=>{if(route().p==='pesocam')render();}).catch(()=>{});});
document.addEventListener('change',e=>{const t=e.target;if(t&&t.matches&&t.matches('[data-pclote]')){if(UI.pc.items.length&&t.value!==UI.pc.lote){UI.pc.items=[];toast('Pesaje en curso descartado: era de otro lote.',3500);}UI.pc.lote=t.value;render();}});

let ULT=null;
async function tomarYAnalizar(titulo){
  const b=await Fotos.tomar(titulo,{max:1920});if(!b)return null;
  openSheet(shHead('Analizando la foto','Peso con cámara')+`<div class="sh-body pc-anal"><div class="pc-spin" aria-hidden="true"></div><p>${Vision.listo()?'Buscando al animal y la marca…':'Cargando el modelo de visión la primera vez…'}</p></div>`);
  try{const r=await analizar(b);ULT=r;return r;}catch(e){closeSheet();toast('No se pudo analizar la foto en este teléfono.',4000);return null;}
}
const FALLO={'sin-animal':'No encontré al animal en la foto. Tómala de costado y que se vea completo.','sin-marca':'No encontré la marca de medida. Que se vea completa, sin reflejos ni sombras encima, y más cerca si está pequeña.'};
ACTS.pcFoto=async()=>{
  const x=loteSel();if(!x){toast('Primero crea un lote.');return;}
  const r=await tomarYAnalizar('Foto de costado, junto a la marca');if(!r)return;
  if(!r.ok){openSheet(shHead('No se pudo medir','Peso con cámara')+`<div class="sh-body"><img class="pc-res" src="${vista(r)}" alt=""><p>${FALLO[r.motivo]}</p></div><div class="sh-foot"><button type="button" class="btn" data-act="cerrar" style="flex:1">Cerrar</button><button type="button" class="btn pri" data-act="pcFoto" style="flex:1.4">Tomar otra</button></div>`);return;}
  const e=r.est,an=(x.animAct||[]);r.img=vista(r);
  openSheet(shHead('Peso estimado',esc(x.l.nombre))+`<div class="sh-body pc-resw"><img class="pc-res" src="${r.img}" alt="">
    <div class="pc-kg"><b>${wtxt(e.kg)}</b><span>entre ${wtxt(e.kg*(1-e.err))} y ${wtxt(e.kg*(1+e.err))}</span></div>
    ${r.aviso?`<p class="pc-aviso">${ico('aviso',2)}<span>${r.aviso}</span></p>`:''}
    <div class="pc-med"><div><b>${nf(r.med.L)} cm</b><span>largo</span></div><div><b>${nf(r.med.Al)} cm</b><span>alto</span></div><div><b>${nf(r.med.A/1e4,2)} m²</b><span>silueta</span></div><div><b>${Math.round(r.seg.score*100)} %</b><span>seguridad</span></div></div>
    ${an.length?q('Arete (opcional)',`<select class="in" id="pcArete"><option value="">Sin arete</option>${an.map(a=>`<option value="${esc(a.id)}">${esc(a.arete)}</option>`).join('')}</select>`):''}
    <p class="hint">${e.n<2?'Sin calibrar: estimado general.':`Calibrado con ${pl(e.n,'foto','fotos')} de tu báscula.`}</p></div>
    <div class="sh-foot"><button type="button" class="btn" data-act="cerrar" style="flex:1">Descartar</button><button type="button" class="btn pri" data-act="pcAgregar" style="flex:1.6">Agregar al pesaje</button></div>`);
};
ACTS.pcAgregar=()=>{const r=ULT;if(!r||!r.ok)return;const x=loteSel();const sel=$('#pcArete'),aid=sel?sel.value:'';const a=aid&&(x.animAct||[]).find(z=>z.id===aid);
  UI.pc.items=UI.pc.items.filter(z=>!aid||z.aid!==aid).concat({kg:r.est.kg,err:r.est.err,A:r.med.A,aid:aid||'',arete:a?a.arete:'',img:r.img});ULT=null;closeSheet();render();
  toast(`${wtxt(r.est.kg)} agregado. Toma otra foto o guarda el pesaje.`,3000);};
ACTS.pcQuitar=el=>{UI.pc.items.splice(+el.dataset.i,1);render();};
ACTS.pcGuardar=()=>{const x=loteSel(),it=UI.pc.items;if(!x||!it.length)return;
  const prom=it.reduce((s,r)=>s+r.kg,0)/it.length,pesos={};for(const r of it)if(r.aid)pesos[r.aid]=Math.round(r.kg*10)/10;
  addItem({tipo:'pesaje',f:hoy(),lote:x.id,prom:Math.round(prom*10)/10,cab:it.length,...(Object.keys(pesos).length?{pesos}:{}),parcial:true,metodo:'camara'});
  UI.pc.items=[];render();toast(`${x.l.nombre}: ${wtxt(prom)} de promedio con cámara (${pl(it.length,'animal','animales')})`,4000);};
// calibrar: la foto de un animal recién pesado y su peso de báscula
ACTS.pcCalibrar=async()=>{
  const r=await tomarYAnalizar('Animal recién pesado, junto a la marca');if(!r)return;
  if(!r.ok){openSheet(shHead('No se pudo medir','Calibración')+`<div class="sh-body"><img class="pc-res" src="${vista(r)}" alt=""><p>${FALLO[r.motivo]}</p></div><div class="sh-foot"><button type="button" class="btn pri" data-act="pcCalibrar" style="flex:1">Tomar otra</button></div>`);return;}
  r.img=vista(r);
  openSheet(shHead('Calibrar con la báscula','Peso con cámara')+formWrap('pcCal',`<img class="pc-res" src="${r.img}" alt="">
    <p class="hint">La foto estima <b>${wtxt(r.est.kg)}</b>. Escribe lo que marcó la báscula para este animal.</p>
    ${q('Peso de báscula',inp('kg','',{unit:UW(),xl:true,ph:nf(W(r.est.kg))}))}`,foot('Guardar calibración')));};
SAVE.pcCal=f=>{const r=ULT;if(!r||!r.ok)return;const v=toKg(num(fv(f,'kg')));if(!(v>=40&&v<=1300))return ferr(f,'Escribe el peso de la báscula.');
  const pc=S.config.pesoCam||{};put('ajustes','finca',{...S.config,pesoCam:{...pc,cal:(pc.cal||[]).concat({A:Math.round(r.med.A),L:Math.round(r.med.L),Al:Math.round(r.med.Al),kg:Math.round(v*10)/10,f:hoy(),ts:Date.now()}).slice(-200)}});
  ULT=null;closeSheet();const m=modelo();toast(m.n<2?'Calibración guardada. Con una más empieza a ajustarse.':`Calibración guardada: error típico ±${Math.round(m.err*100)} %.`,4000);};
ACTS.pcBorrarCal=()=>confirmar('¿Borrar la calibración?','El peso vuelve a calcularse con el estimado general.','Borrar',()=>{put('ajustes','finca',{...S.config,pesoCam:{...(S.config.pesoCam||{}),cal:[]}});toast('Calibración borrada');});
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

/* ---------- entradas: Hoy, Más y el formulario de pesaje ---------- */
const _hoy=PAGES.hoy;
PAGES.hoy=(...a)=>{const h=_hoy(...a),i=h.indexOf('<main class="bd">');if(i<0)return h;const k=i+'<main class="bd">'.length;
  return h.slice(0,k)+`<a class="card pad pc-hoy" href="#pesocam"><span class="mas-ic t-v">${icono('camara','i3')}</span><div><b>Peso con cámara <span class="pc-beta">Beta</span></b><span>Estima el peso con una foto de costado.</span></div>${ico('chev')}</a>`+h.slice(k);};
const _pes=FORMS.pesaje;
if(_pes)FORMS.pesaje=(...a)=>{_pes(...a);const b=$('#sheet .sh-body');if(b&&!b.querySelector('.pc-f'))b.insertAdjacentHTML('afterbegin',`<button type="button" class="btn full pc-f" data-act="pcIr">${icono('camara','i3')}Estimar con cámara (beta)</button>`);};
ACTS.pcIr=()=>{closeSheet();location.hash='#pesocam';};
window.PesoCam={analizar,estimar,modelo,medidas,buscarMarca,DEF,MARCA_CM};
if(route().p==='pesocam'||route().p==='hoy')render();
})();
