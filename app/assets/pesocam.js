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
  persona:{clase:Vision.PERSONA,k:1.0,err:.10,alto:'estatura',
    buscar:'Párate frente a la cámara, de cuerpo completo',
    pasos:[{id:'frente',nombre:'De frente',instr:'De pie y derecho, con los brazos un poco separados del cuerpo.',girar:'Ponte de frente, con los brazos un poco separados',ang:{min:.24},dist:{eje:'alto',min:.55,max:.88}},
      {id:'costado',nombre:'De costado',instr:'De pie y derecho, con los brazos pegados al cuerpo.',girar:'Gírate de costado, con los brazos pegados',ang:{max:.3,rel:.75},dist:{eje:'alto',min:.55,max:.88},banda:[.18,.55]}]},
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
const MKEY='rumentis-pc-modo',VERSION_MODELO=2;
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
    alto:r.alto,ropa:r.modo==='persona'?r.ropa:null,prof:q(r.prof),anchoF:q(r.anchoF),imc:q(r.imc),largo:q(r.largo),altoAnimal:q(r.altoAnimal),ancho:q(r.ancho),
    partes:r.partes?Object.fromEntries(Object.entries(r.partes).map(([a,b])=>[a,q(b)])):null,fuente:r.fuente,manual:r.manual,score:q(r.score),
    fps:q(stats&&stats.fps),msR:q(stats&&stats.msRapido),msP:q(stats&&stats.msPreciso),seg:q(stats&&stats.seg),motor:stats&&stats.motor||'',real:null};
  guardarPC({reg:REG().concat(rec).slice(-300)});return id;
}
const actualizarReg=(id,cambios)=>guardarPC({reg:REG().map(x=>x.id===id?{...x,...cambios}:x)});

/* ---------- calibración con la báscula ----------
   kg = k · litros^b. Con 1 a 5 mediciones se ajusta k (mediana de kg/L); con 6 o más, también el exponente.
   Los datos: mediciones del registro con peso de báscula (y las calibraciones de antes del registro, si las hay). */
const calModo=modo=>REG().filter(x=>x.modo===modo&&x.real>0&&x.L>0).map(x=>({L:x.L,kg:x.real,id:x.id}))
  .concat((PC().cal||[]).filter(c=>c.modo===modo&&c.L>0&&c.kg>0));
function ajustar(C,modo){
  const n=C.length,M=MODOS[modo];if(!n)return {k:M.k,b:1,n:0};
  const X=C.map(z=>Math.log(z.L)),Y=C.map(z=>Math.log(z.kg));let k,b=1;
  if(n<6){const r=C.map(z=>z.kg/z.L).sort((a,b)=>a-b);k=n%2?r[(n-1)/2]:(r[n/2-1]+r[n/2])/2;}
  else{const mx=X.reduce((s,v)=>s+v,0)/n,my=Y.reduce((s,v)=>s+v,0)/n;let sxy=0,sxx=0;for(let i=0;i<n;i++){sxy+=(X[i]-mx)*(Y[i]-my);sxx+=(X[i]-mx)**2;}
    b=sxx>0?Math.min(1.15,Math.max(.85,sxy/sxx)):1;k=Math.exp(my-b*mx);}
  return {k,b,n};
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
const med=v=>{const s=v.slice().sort((a,b)=>a-b),n=s.length;return n?(n%2?s[(n-1)/2]:(s[n/2-1]+s[n/2])/2):0;};
/* Persona: cada fila del cuerpo de frente es una rebanada; su corte tiene el ancho de frente y el fondo de costado a la
   misma altura (relativa a la estatura; mediana de 5 filas, así una fila mal recortada no pesa). Se descuenta la ropa
   (`ropa` cm por lado) y el área del corte es f · ancho · fondo según la parte del cuerpo (FORMA). Los tramos fuera del
   tronco y las piernas (los brazos) se toman redondos: su fondo es su propio ancho. Devuelve litros por parte. */
function volPersona(F,S,ropa=0){
  const nF=F.filas.length,nS=S.filas.length;let tx0=1e9,tx1=-1e9;
  for(let i=Math.floor(nF*.28);i<Math.floor(nF*.45);i++){const t=F.filas[i].reduce((m,z)=>!m||z[1]-z[0]>m[1]-m[0]?z:m,null);if(t){tx0=Math.min(tx0,t[0]);tx1=Math.max(tx1,t[1]);}}
  const hol=(tx1-tx0)*.12,P={cabeza:0,tronco:0,brazos:0,piernas:0},menos=v=>Math.max(0,v-2*ropa);
  for(let i=0;i<nF;i++){const h=(i+.5)/nF,js=Math.min(nS-1,Math.floor(h*nS)),ds=[];
    for(let j=Math.max(0,js-2);j<=Math.min(nS-1,js+2);j++)ds.push(suma(S.filas[j]));
    const d=menos(med(ds)*S.cw);
    for(const [a,b] of F.filas[i]){const wd=menos((b-a)*F.cw),central=b>tx0-hol&&a<tx1+hol;
      const parte=!central?'brazos':h<.13?'cabeza':h<.52?'tronco':'piernas';
      P[parte]+=FORMA[parte]*wd*(central?d:wd)*F.ch;}}
  for(const k in P)P[k]/=1000;return P;
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
/* dos ángulos (cada uno con 1 o 2 fotos) → centímetros con la estatura conocida → volumen → peso.
   Se calcula el volumen con cada par de fotos (frente × costado) y se promedia; su dispersión (cv) dice qué tan de
   acuerdo estuvieron las fotos entre sí. */
function combinar(modo,caps,alto,ropaK){
  const [p,s]=caps,m=modeloV(modo),ref=alto+EXTRA_CM,ropa=modo==='persona'?ROPA[ropaK||'normal'].cm:0;
  const tomas=c=>c.tomas&&c.tomas.length?c.tomas:[{med:c.med,sil:c.sil}];
  // cm por píxel de cada foto: la persona medida (personas) o la de referencia (ganado)
  const cmpx=t=>ref/(modo==='persona'?t.med.alto:t.med.refAlto);
  const TP=tomas(p).map(t=>({t,k:cmpx(t),T:tramos(t.sil,cmpx(t))})),TS=tomas(s).map(t=>({t,k:cmpx(t),T:tramos(t.sil,cmpx(t))}));
  const pares=[];
  for(const a of TP)for(const b of TS){const P=modo==='persona'?volPersona(a.T,b.T,ropa):volGanado(a.T,b.T);pares.push({P,L:Object.values(P).reduce((x,y)=>x+y,0),a,b});}
  const Ls=pares.map(x=>x.L),L=Ls.reduce((x,y)=>x+y,0)/Ls.length,sd=Ls.length>1?Math.sqrt(Ls.reduce((x,y)=>x+(y-L)**2,0)/(Ls.length-1)):0,cv=L>0?sd/L:0;
  const partes={};for(const x of pares)for(const k in x.P)partes[k]=(partes[k]||0)+x.P[k]/pares.length;
  // el rango: el error del modelo (calibración) y el desacuerdo entre fotos, sumados en cuadratura
  const err=Math.min(.3,Math.sqrt(m.err**2+cv**2));
  const r={modo,L,partes,cv,comb:pares.length,kg:m.k*Math.pow(L,m.b),k:m.k,b:m.b,err,n:m.n,alto,ropa:modo==='persona'?(ropaK||'normal'):null,
    score:Math.min(p.med.score,s.med.score),manual:caps.some(c=>c.manual),fuente:caps.every(c=>c.fuente==='preciso')?'preciso':'rapido'};
  const prom=f=>{const v=pares.map(f).filter(isFinite);return v.length?v.reduce((x,y)=>x+y,0)/v.length:NaN;};
  if(modo==='persona'){
    // fondo del pecho (a un 30 % de la estatura desde arriba) y ancho de hombros (a un 22 %)
    const fila=(T,h)=>T.filas[Math.min(T.filas.length-1,Math.floor(h*T.filas.length))]||[];
    r.prof=prom(x=>suma(fila(x.b.T,.3))*x.b.T.cw);r.anchoF=prom(x=>Math.max(0,...fila(x.a.T,.22).map(([a,b])=>b-a))*x.a.T.cw);r.imc=r.kg/((alto/100)**2);
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
    ${cal.length?`<p class="hint" style="margin:0">k = ${nf(W(m.k),3)} ${UW()}/L · b = ${nf(m.b,2)}. Cada medición con peso de báscula está en el laboratorio, abajo.</p>
      <button type="button" class="lnk" data-act="pcBorrarCal">Borrar la calibración</button>`:''}</div></section>`;
}
const filaAlto=(per,a)=>`<div class="pc-alto"><span>${per?'Tu estatura':'Estatura de la persona de referencia'}</span><b>${a?cm(a):'—'}</b><button type="button" class="lnk" data-act="pcAlto">${a?'Cambiar':'Escribir'}</button></div>`;
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
     <ol class="pc-pasos">${per?`<li>Apoya el teléfono a la altura de la cintura, a unos 3 metros (o que alguien lo sostenga), y usa la cámara frontal si estás solo.</li><li>Párate derecho con el cuerpo completo a la vista: primero de frente, con los brazos un poco separados; después de costado, con los brazos pegados.</li><li>Quédate quieto cuando la silueta esté verde: la foto se toma sola.</li>`
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
  const r={...combinar(modo,caps,alto,ropaSel()),caps,stats:caps.stats||{}};r.id=registrar(r,r.stats);return r;
}
// antes de medir hace falta la estatura de referencia
function conAlto(fn){const M=MODOS[UI.pc.modo];if(PC()[M.alto])return fn();formAlto(fn);}
const medHTML=r=>r.modo==='persona'
  ?`<div class="pc-med"><div><b>${nf(r.L)} <span data-no-tr>L</span></b><span>volumen</span></div><div><b>${cm(r.anchoF)}</b><span>hombros</span></div><div><b>${cm(r.prof)}</b><span>pecho de fondo</span></div><div><b>${nf(r.imc,1)}</b><span>IMC</span></div></div>`
  :`<div class="pc-med"><div><b>${cm(r.largo)}</b><span>largo</span></div><div><b>${cm(r.altoAnimal)}</b><span>alto</span></div><div><b>${cm(r.ancho)}</b><span>ancho</span></div><div><b>${nf(r.L)} <span data-no-tr>L</span></b><span>volumen</span></div></div>`;
/* cómo se calculó este peso, con sus números */
function explicar(r){
  const per=r.modo==='persona',P=r.partes||{},pct=v=>`±${nf(v*100,1)} %`,ml=r.modo==='persona'?MODOS.persona:MODOS.ganado,mm=modeloV(r.modo);
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
window.PesoCam={modeloV,ajustar,calModo,combinar,volPersona,volGanado,tramos,registrar,actualizarReg,REG,guardarPC,MODOS,ROPA,FORMA,EXTRA_CM,VERSION_MODELO};
if(route().p==='pesocam'||route().p==='hoy')render();
})();
