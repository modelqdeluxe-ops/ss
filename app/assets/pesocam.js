/* Rumentis Beta: peso por cámara, en vivo y sin nada que imprimir.
   La cámara (camvivo.js) toma dos fotos solas, cuando la silueta está en verde, y las mide en píxeles. La escala en
   centímetros sale de una estatura conocida:
   - Personas (para probar la beta sin estar en la finca): tu estatura, que se escribe una vez. De frente y de costado.
   - Ganado: una persona de estatura conocida parada junto al animal. De costado y por detrás.
   Peso: kg = c · V^b, con V = área principal (cm²) × ancho del otro ángulo (cm) / 1000. Personas: área de frente ×
   profundidad de costado; ganado: área de costado × ancho por detrás. Se ajusta con la báscula (S.config.pesoCam.cal).
   Los animales medidos de un lote se juntan en un pesaje (promedio y, si se indica, el peso de cada arete) que se guarda
   como cualquier otro, marcado como hecho con cámara.
   Solo se activa en Rumentis Beta (config.js: beta:true). */
(function(){
'use strict';
const CFG=window.RUMENTIS||{};if(!CFG.beta||CFG.app!=='jefe'||!window.Vision)return;

/* ---------- los dos modos ---------- */
const MODOS={
  persona:{clase:Vision.PERSONA,c:.58,alto:'estatura',   // 70 kg: unos 5,000 cm² de frente × 24 cm de profundidad
    buscar:'Párate frente a la cámara, de cuerpo completo',
    pasos:[{id:'frente',nombre:'De frente',instr:'De pie y derecho, con los brazos un poco separados del cuerpo.',girar:'Ponte de frente, con los brazos un poco separados',ang:{min:.24},dist:{eje:'alto',min:.55,max:.88}},
      {id:'costado',nombre:'De costado',instr:'De pie y derecho, con los brazos pegados al cuerpo.',girar:'Gírate de costado, con los brazos pegados',ang:{max:.3,rel:.75},dist:{eje:'alto',min:.55,max:.88},banda:[.18,.55]}]},
  ganado:{clase:Vision.VACA,c:.72,alto:'refAlto',ref:true,  // novillo de 450 kg: unos 12,500 cm² de costado × 50 cm de ancho
    buscar:'Apunta al animal, que se vea completo',
    pasos:[{id:'costado',nombre:'De costado',instr:'El animal de costado; la persona de referencia de pie, a la par del animal.',girar:'Que el animal quede de costado',ang:{min:1.15},dist:{eje:'ancho',min:.5,max:.88}},
      {id:'atras',nombre:'Por detrás',instr:'El animal de espaldas a la cámara; la persona de referencia a su lado.',girar:'Que el animal quede de espaldas a la cámara',ang:{max:.9},dist:{eje:'alto',min:.45,max:.85},banda:[.05,.55]}]}
};
const MKEY='rumentis-pc-modo';
UI.pc=UI.pc||{lote:'',items:[]};
if(!UI.pc.modo){let m=null;try{m=localStorage.getItem(MKEY);}catch(e){}UI.pc.modo=MODOS[m]?m:'persona';}
const PC=()=>S.config.pesoCam||{};
const guardarPC=cambios=>put('ajustes','finca',{...S.config,pesoCam:{...PC(),...cambios}});

/* ---------- calibración con la báscula ----------
   Con 1 a 5 mediciones se ajusta c (mediana de kg/V); con 6 o más, también el exponente. */
const calModo=modo=>(PC().cal||[]).filter(c=>c.modo===modo&&c.V>0&&c.kg>0);
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
/* dos capturas (medidas en píxeles) → centímetros con la estatura conocida → peso */
function combinar(modo,caps,alto){
  const [p,s]=caps,cm=m=>alto/(modo==='persona'?m.alto:m.refAlto);   // cm por píxel de cada foto
  const kp=cm(p.med),ks=cm(s.med),A=p.med.area*kp*kp,anc=s.med.banda*ks,V=A*anc/1000,m=modeloV(modo);
  const r={modo,V,kg:m.c*Math.pow(V,m.b),err:m.err,n:m.n,A,prof:anc,alto,score:Math.min(p.med.score,s.med.score),manual:caps.some(c=>c.manual)};
  if(modo==='ganado'){r.largo=p.med.ancho*kp;r.altoAnimal=p.med.alto*kp;r.ancho=anc;
    // de costado y por detrás el animal tiene la misma altura: si no, la persona no estaba a la par
    const a2=s.med.alto*ks;if(Math.abs(r.altoAnimal-a2)/((r.altoAnimal+a2)/2)>.12)r.aviso='Las dos tomas dieron alturas distintas del animal: que la persona de referencia se pare a la par del animal, ni adelante ni atrás.';}
  if(!r.aviso&&caps.some(c=>c.fuente!=='preciso'))r.aviso='Una foto se midió solo con el modelo rápido: el peso puede ser menos exacto.';
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
    ${cal.length?`<div class="rows">${cal.slice(-6).reverse().map(c=>`<div class="row"><div class="tx"><b>${wtxt(c.kg)}</b><span><span>báscula</span> · <span>${ffc(c.f)}</span> · <span>cámara sin calibrar: ${wtxt(MODOS[modo].c*c.V)}</span></span></div></div>`).join('')}</div>
      <button type="button" class="lnk" data-act="pcBorrarCal">Borrar la calibración</button>`:''}</div></section>`;
}
const filaAlto=(per,a)=>`<div class="pc-alto"><span>${per?'Tu estatura':'Estatura de la persona de referencia'}</span><b>${a?cm(a):'—'}</b><button type="button" class="lnk" data-act="pcAlto">${a?'Cambiar':'Escribir'}</button></div>`;
PAGES.pesocam=()=>{
  const modo=UI.pc.modo,per=modo==='persona',x=loteSel(),it=UI.pc.items,a=PC()[MODOS[modo].alto];
  const prom=it.length?it.reduce((s,r)=>s+r.kg,0)/it.length:0;
  return `<header class="hd">${hdBack('#hoy','Hoy')}<div class="ttl" style="margin-top:-6px"><span class="eyebrow pc-beta">Beta</span><h1>Peso con cámara</h1><p class="sub">${per?'Prueba la medición contigo: la cámara te toma de frente y de costado y estima tu peso.':'La cámara toma al animal de costado y por detrás, con una persona de pie a su lado como referencia, y estima su peso.'} Sin nada que imprimir. Todo se calcula aquí, sin internet.</p></div></header>
  <main class="bd">
   <div class="seg pc-modo" role="group" aria-label="Qué medir">${[['persona','Personas'],['ganado','Ganado']].map(([v,t])=>`<button type="button" data-act="pcModo" data-m="${v}" aria-pressed="${modo===v}">${t}</button>`).join('')}</div>
   ${per?`<section class="sec"><div class="card pad pc-vivo">${filaAlto(true,a)}<p>Dos tomas: de frente y de costado. Cuando la silueta se pone verde, la foto se toma sola.</p>
       <button type="button" class="btn pri full pc-foto" data-act="pcVivo">${icono('camara','i3')}Medir con la cámara</button></div></section>`
     :lotesAct().length?`<section class="sec">${secH('Lote')}<div class="card pad pc-vivo"><select class="in" id="pcLote" data-pclote>${lotesAct().map(l=>`<option value="${esc(l.id)}"${l.id===UI.pc.lote?' selected':''}>${esc(l.l.nombre)} · ${pl(l.cab,'cabeza','cabezas')}</option>`).join('')}</select>
       ${filaAlto(false,a)}<button type="button" class="btn pri full pc-foto" data-act="pcVivo">${icono('camara','i3')}Medir con la cámara</button></div></section>`
     :`<div class="card pad"><p class="empty">Primero crea un lote.</p></div>`}
   ${!per&&it.length?`<section class="sec">${secH('Pesaje en curso',it.length)}<div class="card pad pc-sesion"><div class="pc-prom"><b>${wtxt(prom)}</b><span>promedio de ${pl(it.length,'animal','animales')} en ${esc(x?x.l.nombre:'')}</span></div>
     <div class="rows">${it.map((r,i)=>`<div class="row"><img class="pc-th" src="${r.img}" alt=""><div class="tx"><b>${wtxt(r.kg)}</b><span>${r.arete?`<span>Arete</span> <span data-no-tr>${esc(r.arete)}</span>`:'<span>Sin arete</span>'} · <span>±${Math.round(r.err*100)} %</span></span></div><button type="button" class="del" data-act="pcQuitar" data-i="${i}" aria-label="Quitar">${ico('x')}</button></div>`).join('')}</div>
     <div class="pc-acts"><button type="button" class="btn" data-act="pcVivo">${icono('camara','i3')}Otro animal</button><button type="button" class="btn pri" data-act="pcGuardar">Guardar pesaje</button></div></div></section>`:''}
   <section class="sec">${secH('Cómo hacerlo')}<div class="card pad pc-como">
     <ol class="pc-pasos">${per?`<li>Apoya el teléfono a la altura de la cintura, a unos 3 metros (o que alguien lo sostenga), y usa la cámara frontal si estás solo.</li><li>Párate derecho con el cuerpo completo a la vista: primero de frente, con los brazos un poco separados; después de costado, con los brazos pegados.</li><li>Quédate quieto cuando la silueta esté verde: la foto se toma sola.</li>`
       :`<li>Una persona se para derecha a la par del animal, sin taparlo. Su estatura es la medida de referencia.</li><li>Primero con el animal de costado; después por detrás. Aléjate hasta que el animal y la persona se vean completos.</li><li>Sostén el teléfono firme: cuando la silueta esté verde, la foto se toma sola.</li>`}</ol></div></section>
   ${tarjetaCal(modo)}
   <p class="hint pc-nota">${per?'Beta: sirve para probar la medición. El peso de una persona con cámara es un estimado.':'Beta: compara con la báscula antes de decidir ventas o raciones con este peso.'}</p>
  </main>`;
};
document.addEventListener('change',e=>{const t=e.target;if(t&&t.matches&&t.matches('[data-pclote]')){if(UI.pc.items.length&&t.value!==UI.pc.lote){UI.pc.items=[];toast('Pesaje en curso descartado: era de otro lote.',3500);}UI.pc.lote=t.value;render();}});
ACTS.pcModo=el=>{const m=el.dataset.m;if(!MODOS[m]||m===UI.pc.modo)return;UI.pc.modo=m;try{localStorage.setItem(MKEY,m);}catch(e){}render();};

/* ---------- la estatura de referencia ---------- */
let TRAS=null;   // qué hacer después de guardarla (medir o calibrar)
function formAlto(tras){
  const per=UI.pc.modo==='persona',a=PC()[MODOS[UI.pc.modo].alto];TRAS=tras||null;
  openSheet(shHead(per?'Tu estatura':'Persona de referencia','Peso con cámara')+formWrap('pcAlto',`
    <p class="hint">${per?'Con tu estatura la cámara sabe cuánto mide cada parte de tu cuerpo. Escríbela una vez, sin zapatos.':'Con la estatura de quien se para junto al animal, la cámara sabe cuánto mide el animal. Escríbela una vez.'}</p>
    ${q(per?'Estatura':'Estatura de la persona',inp('alto',a?String(Math.round(a)):'',{unit:'cm',xl:true,ph:'170'}))}`,foot('Guardar')));
}
ACTS.pcAlto=()=>formAlto(null);
SAVE.pcAlto=f=>{const v=num(fv(f,'alto'));if(!(v>=100&&v<=230))return ferr(f,'Escribe la estatura en centímetros (por ejemplo, 170).');
  guardarPC({[MODOS[UI.pc.modo].alto]:Math.round(v)});closeSheet();const t=TRAS;TRAS=null;if(t)setTimeout(t,60);else toast('Estatura guardada');};

/* ---------- medir ---------- */
let ULT=null;
async function medirVivo(){
  const modo=UI.pc.modo,M=MODOS[modo],alto=PC()[M.alto];
  if(!window.CamVivo){toast('La cámara en vivo no está disponible.');return null;}
  const caps=await CamVivo.abrir({titulo:'Peso con cámara',clase:M.clase,ref:!!M.ref,buscar:M.buscar,pasos:M.pasos});
  if(!caps||caps.length<M.pasos.length)return null;
  return {...combinar(modo,caps,alto),caps};
}
// antes de medir hace falta la estatura de referencia
function conAlto(fn){const M=MODOS[UI.pc.modo];if(PC()[M.alto])return fn();formAlto(fn);}
const medHTML=r=>r.modo==='persona'
  ?`<div class="pc-med"><div><b>${cm(r.alto)}</b><span>estatura</span></div><div><b>${nf(r.A/1e4,2)} m²</b><span>de frente</span></div><div><b>${cm(r.prof)}</b><span>de fondo</span></div><div><b>${Math.round(r.score*100)} %</b><span>seguridad</span></div></div>`
  :`<div class="pc-med"><div><b>${cm(r.largo)}</b><span>largo</span></div><div><b>${cm(r.altoAnimal)}</b><span>alto</span></div><div><b>${cm(r.ancho)}</b><span>ancho</span></div><div><b>${Math.round(r.score*100)} %</b><span>seguridad</span></div></div>`;
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
  guardarPC({cal:(PC().cal||[]).concat({modo:r.modo,V:Math.round(r.V*10)/10,kg:Math.round(v*10)/10,f:hoy(),ts:Date.now()}).slice(-200)});
  ULT=null;closeSheet();const m=modeloV(r.modo);toast(`Calibración guardada: error típico ±${Math.round(m.err*100)} %.`,4000);};
ACTS.pcBorrarCal=()=>confirmar('¿Borrar la calibración?','El peso vuelve a calcularse con el estimado general.','Borrar',()=>{const modo=UI.pc.modo;
  guardarPC({cal:(PC().cal||[]).filter(c=>c.modo!==modo)});toast('Calibración borrada');});
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
window.PesoCam={modeloV,combinar,MODOS};
if(route().p==='pesocam'||route().p==='hoy')render();
})();
