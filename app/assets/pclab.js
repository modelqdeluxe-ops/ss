/* Rumentis Beta: laboratorio del peso con cámara (datos de desarrollador), dentro de la página #pesocam.
   Con el registro de mediciones (pesocam.js, S.config.pesoCam.reg) calcula qué tan bien anda el modelo:
   - error contra la báscula: medio (MAPE y MAE), sesgo, desviación, RMSE y R² (estimado vs. báscula);
   - error esperado del modelo de hoy con validación cruzada dejando una fuera (se ajusta sin esa medición y se predice);
   - error del modelo de fábrica (sin calibrar), para ver cuánto ayuda la calibración;
   - repetibilidad: dispersión (CV) del volumen en mediciones seguidas de la misma persona o animal (menos de 15 min
     entre sí);
   - dispersión entre fotos de cada medición y rendimiento (cuadros por segundo, tiempo del modelo preciso).
   Gráficas: estimado contra báscula y la serie de las últimas mediciones. Exporta todo en CSV. */
(function(){
'use strict';
const CFG=window.RUMENTIS||{};if(!CFG.beta||CFG.app!=='jefe'||!window.PesoCam)return;
const P=window.PesoCam;
const prom=v=>v.length?v.reduce((a,b)=>a+b,0)/v.length:NaN;
const desv=v=>{if(v.length<2)return NaN;const m=prom(v);return Math.sqrt(v.reduce((s,x)=>s+(x-m)**2,0)/(v.length-1));};
const esNum=v=>typeof v==='number'&&isFinite(v);

/* ---------- las estadísticas de un modo ---------- */
function estad(modo){
  const R=P.REG().filter(x=>x.modo===modo),con=R.filter(x=>x.real>0&&x.kg>0);
  const e=con.map(x=>(x.kg-x.real)/x.real);
  // validación cruzada dejando una fuera, con los datos de calibración de hoy
  const C=P.calModo(modo);
  const loo=C.length>=2?C.map((z,i)=>{const a=P.ajustar(C.filter((_,j)=>j!==i),modo);return (a.k*Math.pow(z.L,a.b)-z.kg)/z.kg;}):[];
  const fab=C.map(z=>(P.MODOS[modo].k*z.L-z.kg)/z.kg);
  // R² solo tiene sentido si los pesos de báscula varían (varias personas o animales): si no, sale un número sin sentido
  let r2=NaN;if(con.length>=3){const y=con.map(x=>x.real),m=prom(y),sst=y.reduce((s,v)=>s+(v-m)**2,0);if(desv(y)/m>=.05)r2=1-con.reduce((s,x)=>s+(x.kg-x.real)**2,0)/sst;}
  // repetibilidad: grupos de mediciones seguidas
  const ses=[];let cur=[];for(const x of R){if(cur.length&&x.ts-cur[cur.length-1].ts>15*60e3){ses.push(cur);cur=[];}cur.push(x);}if(cur.length)ses.push(cur);
  // (con el volumen: no cambia al calibrar, así se ve solo cuánto varía la medición)
  const cvs=ses.filter(g=>g.length>=2).map(g=>desv(g.map(x=>x.L))/prom(g.map(x=>x.L))),sds=ses.filter(g=>g.length>=2).map(g=>desv(g.map(x=>x.L)));
  return {R,con,n:R.length,nReal:con.length,nCal:C.length,mape:prom(e.map(Math.abs)),mae:prom(con.map(x=>Math.abs(x.kg-x.real))),sesgo:prom(e),sdErr:desv(e),
    rmse:con.length?Math.sqrt(prom(con.map(x=>(x.kg-x.real)**2))):NaN,r2,loo:prom(loo.map(Math.abs)),looSesgo:prom(loo),fab:prom(fab.map(Math.abs)),
    rep:prom(cvs),repL:prom(sds),nSes:cvs.length,cvInt:prom(R.map(x=>x.cv).filter(esNum)),fps:prom(R.map(x=>x.fps).filter(v=>v>0)),
    msP:prom(R.map(x=>x.msP).filter(v=>v>0)),seg:prom(R.map(x=>x.seg).filter(v=>v>0)),modelo:P.modeloV(modo)};
}

/* ---------- gráficas (SVG, en las unidades de peso de la app) ---------- */
function dispersion(con){
  if(!con.length)return '';
  const Wd=300,Hd=210,m=34,pts=con.map(x=>[W(x.real),W(x.kg)]),all=pts.flat(),lo=Math.min(...all)*.95,hi=Math.max(...all)*1.05;
  const X=v=>m+(v-lo)/(hi-lo)*(Wd-m-10),Y=v=>Hd-m+6-(v-lo)/(hi-lo)*(Hd-m-4);
  const banda=`${X(lo)},${Y(lo*1.05)} ${X(hi)},${Y(hi*1.05)} ${X(hi)},${Y(hi*.95)} ${X(lo)},${Y(lo*.95)}`;
  return `<figure class="pc-graf"><svg viewBox="0 0 ${Wd} ${Hd}" role="img" aria-label="Estimado contra báscula">
    <polygon points="${banda}" class="g-banda"/><line x1="${X(lo)}" y1="${Y(lo)}" x2="${X(hi)}" y2="${Y(hi)}" class="g-id"/>
    <line x1="${m}" y1="${Hd-m+6}" x2="${Wd-8}" y2="${Hd-m+6}" class="g-eje"/><line x1="${m}" y1="4" x2="${m}" y2="${Hd-m+6}" class="g-eje"/>
    <text x="${m}" y="${Hd-10}" class="g-t" data-no-tr>${nf(lo)}</text><text x="${Wd-8}" y="${Hd-10}" class="g-t" text-anchor="end" data-no-tr>${nf(hi)}</text>
    <text x="${m-4}" y="12" class="g-t" text-anchor="end" data-no-tr>${nf(hi)}</text>
    ${pts.map(([a,b])=>`<circle cx="${X(a).toFixed(1)}" cy="${Y(b).toFixed(1)}" r="3.5" class="g-p"/>`).join('')}</svg>
    <figcaption>Estimado (vertical) contra báscula (horizontal), en ${UW()}. La franja es ±5 %.</figcaption></figure>`;
}
function serie(R){
  const v=R.slice(-30);if(v.length<2)return '';
  const Wd=300,Hd=120,m=8,ys=v.flatMap(x=>[x.kg,x.real].filter(z=>z>0)).map(W),lo=Math.min(...ys)*.97,hi=Math.max(...ys)*1.03;
  const X=i=>m+i/(v.length-1)*(Wd-2*m),Y=y=>Hd-m-(W(y)-lo)/(hi-lo||1)*(Hd-2*m);
  return `<figure class="pc-graf"><svg viewBox="0 0 ${Wd} ${Hd}" role="img" aria-label="Últimas mediciones">
    <polyline points="${v.map((x,i)=>`${X(i).toFixed(1)},${Y(x.kg).toFixed(1)}`).join(' ')}" class="g-l"/>
    ${v.map((x,i)=>`<circle cx="${X(i).toFixed(1)}" cy="${Y(x.kg).toFixed(1)}" r="2.5" class="g-p"/>${x.real>0?`<circle cx="${X(i).toFixed(1)}" cy="${Y(x.real).toFixed(1)}" r="3" class="g-r"/>`:''}`).join('')}</svg>
    <figcaption>Últimas ${v.length} mediciones: estimado (línea) y báscula (anillos).</figcaption></figure>`;
}

/* ---------- la sección en la página ---------- */
const pc=v=>esNum(v)?`${nf(v*100,1)} %`:'—',pcs=v=>esNum(v)?`${v>0?'+':''}${nf(v*100,1)} %`:'—',kgs=v=>esNum(v)?wtxt(v,1):'—',n2=(v,d=2)=>esNum(v)?nf(v,d):'—';
const hora=ts=>{const d=new Date(ts);return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;};
function html(modo){
  const E=estad(modo),m=E.modelo,per=modo==='persona';
  const tiles=[
    [E.n,'mediciones'],[E.nReal,'con báscula'],[E.nCal>=2?'±'+pc(E.loo):'—','error esperado (validación cruzada)'],[E.nReal?'±'+pc(E.mape):'—','error medio al medir'],
    [pcs(E.sesgo),'sesgo'],[kgs(E.rmse),'RMSE'],[n2(E.r2),'R²'],[E.nCal?'±'+pc(E.fab):'—','error sin calibrar'],
    [E.nSes?pc(E.rep):'—','repetibilidad (CV)'],[pc(E.cvInt),'dispersión entre fotos'],[esNum(E.fps)?nf(E.fps,1):'—','cuadros/s'],[esNum(E.msP)?`${nf(E.msP)} ms`:'—','modelo preciso']];
  const filas=E.R.slice(-15).reverse().map(x=>{const er=x.real>0?(x.kg-x.real)/x.real:null;
    return `<button type="button" class="row pc-reg" data-act="pcRegVer" data-id="${x.id}"><div class="tx"><b>${wtxt(x.kg,1)}${x.real>0?` <span class="pc-real">· <span>báscula</span> ${wtxt(x.real,1)}</span>`:''}</b>
      <span><span>${ffc(x.f)}</span> <span data-no-tr>${hora(x.ts)}</span> · <span>${nf(x.L,1)} L</span>${er!=null?` · <span class="${Math.abs(er)<=.05?'ok':'mal'}">${pcs(er)}</span>`:''}${esNum(x.cv)?` · <span>±${nf(x.cv*100,1)} %</span>`:''}</span></div>${ico('chev')}</button>`;}).join('');
  return `<section class="sec">${secH('Laboratorio',E.n||'')}<div class="card pad pc-lab">
    <p class="pc-lab-sub">Datos de desarrollador: cada medición queda registrada aquí. Anota el peso de báscula en las que puedas para medir el error del modelo.</p>
    ${E.n?`<div class="pc-tiles">${tiles.map(([v,t])=>`<div><b data-no-tr>${v}</b><span>${t}</span></div>`).join('')}</div>
      ${dispersion(E.con)}${serie(E.R)}
      <p class="pc-lab-mod" data-no-tr>v${P.VERSION_MODELO} · k = ${nf(W(m.k),3)} ${UW()}/L · b = ${nf(m.b,2)}${per?` · f ${nf(P.FORMA.tronco,2)} / ${nf(P.FORMA.cabeza,3)}`:''}</p>
      <div class="rows">${filas}</div>
      <div class="pc-acts"><button type="button" class="btn" data-act="pcRegCsv">Exportar CSV</button><button type="button" class="btn" data-act="pcRegLimpiar">Borrar registro</button></div>`
    :`<p class="empty">Todavía no hay mediciones.</p>`}</div></section>`;
}

/* ---------- una medición ---------- */
const buscar=id=>P.REG().find(x=>x.id===id);
ACTS.pcRegVer=el=>{
  const x=buscar(el.dataset.id);if(!x)return;const er=x.real>0?(x.kg-x.real)/x.real:null,pa=x.partes||{};
  const fila=(t,v)=>v==null||v===''?'':`<div class="pc-kv"><span>${t}</span><b data-no-tr>${v}</b></div>`;
  openSheet(shHead('Medición',`${ffc(x.f)} ${hora(x.ts)}`)+`<div class="sh-body pc-det">
    ${fila('Peso estimado',wtxt(x.kg,1))}${fila('Peso de báscula',x.real>0?wtxt(x.real,1):'—')}${er!=null?fila('Error',pcs(er)):''}${fila('Rango',esNum(x.err)?'±'+pc(x.err):'')}
    ${fila('Volumen',`${n2(x.L,1)} L`)}${x.modo==='persona'?fila('Por partes',`${n2(pa.cabeza,1)} / ${n2(pa.tronco,1)} / ${n2(pa.brazos,1)} / ${n2(pa.piernas,1)} L`):''}
    ${x.modo==='persona'?fila('Estatura',`${x.alto} cm`)+fila('Hombros',`${n2(x.anchoF,1)} cm`)+fila('Pecho de fondo',`${n2(x.prof,1)} cm`)+fila('IMC',n2(x.imc,1))+fila('Ropa',x.ropa&&P.ROPA[x.ropa]?P.ROPA[x.ropa].t:'')
      :fila('Largo',`${n2(x.largo,1)} cm`)+fila('Alto',`${n2(x.altoAnimal,1)} cm`)+fila('Ancho',`${n2(x.ancho,1)} cm`)+fila('Persona de referencia',`${x.alto} cm`)}
    ${fila('Modelo',`v${x.v} · k ${n2(W(x.k),3)} ${UW()}/L · b ${n2(x.b,2)} · ${x.ncal||0} cal.`)}${fila('Dispersión entre fotos',`${pc(x.cv)} · ${x.comb||1} comb.`)}
    ${fila('Seguridad',esNum(x.score)?`${Math.round(x.score*100)} %`:'')}${fila('Fuente',x.fuente==='preciso'?'RF-DETR':'LR-ASPP')}${fila('Toma manual',x.manual?'sí':'no')}
    ${fila('Cuadros/s',n2(x.fps,1))}${fila('Modelo rápido',esNum(x.msR)?`${nf(x.msR)} ms`:'')}${fila('Modelo preciso',esNum(x.msP)?`${nf(x.msP)} ms`:'')}${fila('Duración',esNum(x.seg)?`${nf(x.seg,1)} s`:'')}${fila('Motor',x.motor)}
    </div><div class="sh-foot"><button type="button" class="btn danger" data-act="pcRegBorrar" data-id="${x.id}" style="flex:1">Borrar</button><button type="button" class="btn pri" data-act="pcRegReal" data-id="${x.id}" style="flex:1.6">${x.real>0?'Cambiar peso de báscula':'Anotar peso de báscula'}</button></div>`);
};
let REAL=null;
ACTS.pcRegReal=el=>{const x=buscar(el.dataset.id);if(!x)return;REAL=x.id;
  openSheet(shHead('Peso de báscula','Laboratorio')+formWrap('pcReg',`<p class="hint">La cámara estimó <b>${wtxt(x.kg,1)}</b>. Escribe lo que marcó la báscula ese día; también sirve para calibrar.</p>
    ${q('Peso de báscula',inp('kg',x.real>0?nf(W(x.real),1):'',{unit:UW(),xl:true,ph:nf(W(x.kg))}))}`,foot('Guardar')));};
SAVE.pcReg=f=>{const x=buscar(REAL);if(!x)return closeSheet();const v=toKg(num(fv(f,'kg')));
  const [lo,hi]=x.modo==='persona'?[15,250]:[40,1300];if(!(v>=lo&&v<=hi))return ferr(f,'Escribe el peso de la báscula.');
  P.actualizarReg(x.id,{real:Math.round(v*10)/10});REAL=null;closeSheet();toast('Peso de báscula guardado');};
ACTS.pcRegBorrar=el=>{const id=el.dataset.id;confirmar('¿Borrar esta medición?','Sale del registro y de la calibración.','Borrar',()=>{P.guardarPC({reg:P.REG().filter(x=>x.id!==id)});toast('Medición borrada');});};
ACTS.pcRegLimpiar=()=>{const modo=UI.pc.modo;confirmar('¿Borrar el registro?','Se borran todas las mediciones de este modo, también su peso de báscula (la calibración).','Borrar',()=>{P.guardarPC({reg:P.REG().filter(x=>x.modo!==modo)});toast('Registro borrado');});};
// todo el registro en CSV (una fila por medición; el peso en kg)
const COLS=['id','fecha','hora','modo','version','litros','kg_estimado','kg_bascula','error_pct','rango_pct','cv_fotos_pct','combinaciones','k','b','calibraciones',
  'estatura_ref_cm','ropa','hombros_cm','pecho_fondo_cm','imc','largo_cm','alto_cm','ancho_cm','cabeza_l','tronco_l','brazos_l','piernas_l','seguridad','fuente','manual','fps','ms_rapido','ms_preciso','segundos','motor'];
function csv(){
  const q=v=>v==null||(typeof v==='number'&&!isFinite(v))?'':typeof v==='number'?String(Math.round(v*1000)/1000):/[",\n]/.test(String(v))?`"${String(v).replace(/"/g,'""')}"`:String(v);
  const L=P.REG().map(x=>{const pa=x.partes||{},er=x.real>0?(x.kg-x.real)/x.real*100:null;
    return [x.id,x.f,hora(x.ts),x.modo,x.v,x.L,x.kg,x.real,er,esNum(x.err)?x.err*100:null,esNum(x.cv)?x.cv*100:null,x.comb,x.k,x.b,x.ncal,x.alto,x.ropa,x.anchoF,x.prof,x.imc,
      x.largo,x.altoAnimal,x.ancho,pa.cabeza,pa.tronco,pa.brazos,pa.piernas,x.score,x.fuente,x.manual?1:0,x.fps,x.msR,x.msP,x.seg,x.motor].map(q).join(',');});
  return [COLS.join(',')].concat(L).join('\n')+'\n';
}
ACTS.pcRegCsv=async()=>{const b=new TextEncoder().encode(csv());await Documentos.enviar(`rumentis-labs-mediciones-${hoy()}.csv`,b.buffer,'compartir','Mediciones',  'text/csv');};
window.PcLab={html,estad,csv};
})();
