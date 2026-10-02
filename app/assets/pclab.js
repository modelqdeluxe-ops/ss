/* Rumentis Beta: laboratorio del peso con cámara (la app de depuración del modelo). Dos lugares:
   - en la página #pesocam, una tarjeta con lo principal (error, sesgo, repetibilidad…) y las últimas mediciones;
   - la página #pclab, el laboratorio completo, en pestañas:
     Resumen   el estado del modelo y un diagnóstico automático (qué está fallando y qué revisar), la sesión de prueba;
     Errores   el error contra cada peso real (báscula, entrada, pesajes, ventas), con validación cruzada (dejando una
               fuera), con el factor de cada animal y aprendiendo en orden (la curva de aprendizaje); por fuente, tipo,
               etapa, cámaras, condiciones del animal y rango de peso; foto contra cinta contra báscula;
     Medidas   cómo se reparten las medidas (alzada, fondo de pecho, su razón con los límites del modelo, largo), si las
               cámaras coinciden entre sí, y el error contra cada medida (si sube con una, el coeficiente está mal);
     Calidad   las condiciones de cada toma (nitidez, luz, tamaño del animal y de la persona en la foto, confianza de los
               puntos, inclinación del teléfono, desacuerdo entre fotos) y el error en cada tercio de cada una;
               el rendimiento (cuadros por segundo, tiempos de los modelos);
     Animales  cada animal medido por arete: su trayectoria (estimados y pesos reales), su factor y su repetibilidad;
     Modelo    las constantes del modelo, la calibración, cada peso real usado, un ajuste sugerido con los datos de la
               finca (β, γ) y una calculadora;
     Registro  todas las mediciones (filtros) y los exportes.
   Cada medición se abre con su detalle: las fotos con la silueta, la persona de referencia, los puntos y dónde se
   midió; cada foto y cada par de fotos con sus números; la sensibilidad (cuánto cambia el peso con 1 cm de error); el
   peso con el modelo de hoy; las condiciones del animal (sexo, condición corporal, llenado, preñez, pelo, postura,
   suelo, fondo, edad, distancia, cinta, notas). */
(function(){
'use strict';
const CFG=window.RUMENTIS||{};if(!CFG.beta||CFG.app!=='jefe'||!window.PesoCam)return;
const P=window.PesoCam;
const prom=v=>v.length?v.reduce((a,b)=>a+b,0)/v.length:NaN;
const desv=v=>{if(v.length<2)return NaN;const m=prom(v);return Math.sqrt(v.reduce((s,x)=>s+(x-m)**2,0)/(v.length-1));};
const esNum=v=>typeof v==='number'&&isFinite(v);
const medn=v=>{const s=v.filter(esNum).sort((a,b)=>a-b),n=s.length;return n?(n%2?s[(n-1)/2]:(s[n/2-1]+s[n/2])/2):NaN;};
const pctl=(v,p)=>{const s=v.filter(esNum).sort((a,b)=>a-b);return s.length?s[Math.min(s.length-1,Math.max(0,Math.round(p*(s.length-1))))]:NaN;};
const LAB=()=>{UI.lab=UI.lab||{f:'todas'};return UI.lab;};
const PCc=()=>S.config.pesoCam||{};

/* ---------- los pesos reales (etiquetas), unidos a su medición ----------
   Para cada peso real de un animal: el error del modelo de fábrica, el del modelo de hoy (con ese peso adentro), el de
   validación cruzada (ajustado sin ese peso: el honesto), el del animal conocido (con su factor, sin ese peso) y el de
   aprender en orden (ajustado solo con los pesos anteriores). */
const buscar=id=>P.REG().find(x=>x.id===id);
const MEMO=new Map();
function etiquetasLab(modo){
  const R=P.REG(),k=`${modo}|${typeof ver!=='undefined'?ver:0}|${R.length}|${R.length?R[R.length-1].id:''}`;if(MEMO.has(k))return MEMO.get(k);
  const r=etiquetasLab0(modo);if(MEMO.size>8)MEMO.clear();MEMO.set(k,r);return r;
}
function etiquetasLab0(modo){
  const out=[],grupos=[];
  for(const canal of modo==='ganado'?['foto','cinta']:['foto']){
    const C=P.calModo(modo,canal);if(!C.length)continue;const m=P.modeloV(modo,canal),k0=P.MODOS[modo].k;
    const ts=z=>{const x=z.id&&buscar(z.id);return x?x.ts:0;},orden=C.map((z,i)=>i).sort((a,b)=>ts(C[a])-ts(C[b]));
    C.forEach((z,i)=>{
      const fab=k0*z.L/z.kg-1,hoyE=m.k*Math.pow(z.L,m.b)/z.kg-1;
      const resto=C.filter((_,j)=>j!==i),a=P.ajustar(resto,modo),p=a.k*Math.pow(z.L,a.b),loo=p/z.kg-1;
      // el animal conocido: su factor con sus otros pesos (encogido como en la app)
      let ani=NaN;if(z.aid&&!z.grupo){const O=resto.filter(o=>!o.grupo&&o.aid===z.aid);if(O.length){const r=prom(O.map(o=>Math.log(o.kg)-Math.log(a.k)-a.b*Math.log(o.L))),n=O.length;ani=p*Math.exp(r*n/(n+.2))/z.kg-1;}}
      // en orden: solo con los pesos de antes
      const pos=orden.indexOf(i),antes=orden.slice(0,pos).map(j=>C[j]),b=P.ajustar(antes,modo),seq=b.k*Math.pow(z.L,b.b)/z.kg-1;
      const f={...z,canal,x:z.id?buscar(z.id):null,fab,hoy:hoyE,e:loo,ani,seq,pos};
      (z.grupo?grupos:out).push(f);});
  }
  return {F:out,G:grupos};
}
const NOM_SRC={bascula:'Báscula',entrada:'Peso de entrada',pesaje:'Pesaje del animal','pesaje del lote':'Pesaje del lote',venta:'Venta','entrada del lote':'Entrada del lote'};

/* ---------- las estadísticas de un modo (la tarjeta de #pesocam y las pruebas) ---------- */
function estad(modo){
  const R=P.REG().filter(x=>x.modo===modo),con=R.filter(x=>x.real>0&&x.kg>0);
  const e=con.map(x=>(x.kg-x.real)/x.real);
  const C=P.calModo(modo);
  const loo=C.length>=2?C.map((z,i)=>{const a=P.ajustar(C.filter((_,j)=>j!==i),modo);return (a.k*Math.pow(z.L,a.b)-z.kg)/z.kg;}):[];
  const fab=C.map(z=>(P.MODOS[modo].k*z.L-z.kg)/z.kg);
  // R² solo tiene sentido si los pesos de báscula varían (varias personas o animales): si no, sale un número sin sentido
  let r2=NaN;if(con.length>=3){const y=con.map(x=>x.real),m=prom(y),sst=y.reduce((s,v)=>s+(v-m)**2,0);if(desv(y)/m>=.05)r2=1-con.reduce((s,x)=>s+(x.kg-x.real)**2,0)/sst;}
  // repetibilidad: grupos de mediciones seguidas (menos de 15 min entre sí), con la base del peso antes de calibrar
  const ses=sesiones(R),bs=baseRep;
  const cvs=ses.map(g=>desv(g.map(bs))/prom(g.map(bs))),sds=ses.map(g=>desv(g.map(bs)));
  return {R,con,n:R.length,nReal:con.length,nCal:C.length,mape:prom(e.map(Math.abs)),mae:prom(con.map(x=>Math.abs(x.kg-x.real))),sesgo:prom(e),sdErr:desv(e),
    rmse:con.length?Math.sqrt(prom(con.map(x=>(x.kg-x.real)**2))):NaN,r2,loo:prom(loo.map(Math.abs)),looSesgo:prom(loo),fab:prom(fab.map(Math.abs)),fabSesgo:prom(fab),
    rep:prom(cvs),repL:prom(sds),nSes:cvs.length,cvInt:prom(R.map(x=>x.cv).filter(esNum)),fps:prom(R.map(x=>x.fps).filter(v=>v>0)),
    msP:prom(R.map(x=>x.msP).filter(v=>v>0)),seg:prom(R.map(x=>x.seg).filter(v=>v>0)),modelo:P.modeloV(modo)};
}
// la base del peso antes de calibrar (personas: el modelo de ANSUR II; ganado: el modelo de medidas, o el volumen)
const baseRep=x=>x.pred>0?x.pred:x.L;
// mediciones repetidas: seguidas (menos de 15 min) y, en ganado, del mismo animal si tienen arete
function sesiones(R){
  const ses=[];let cur=[];
  for(const x of R){const u=cur[cur.length-1];if(u&&(x.ts-u.ts>15*60e3||(x.aid||u.aid)&&x.aid!==u.aid)){ses.push(cur);cur=[];}cur.push(x);}
  if(cur.length)ses.push(cur);return ses.filter(g=>g.length>=2);
}

/* ---------- gráficas (SVG) ---------- */
const GW=300;
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
  const Wd=300,Hd=120,m=8,ys=v.flatMap(x=>[x.kg,x.real].filter(z=>z>0)).map(W);if(!ys.length||v.some(x=>!(x.kg>0)))return '';const lo=Math.min(...ys)*.97,hi=Math.max(...ys)*1.03;
  const X=i=>m+i/(v.length-1)*(Wd-2*m),Y=y=>Hd-m-(W(y)-lo)/(hi-lo||1)*(Hd-2*m);
  return `<figure class="pc-graf"><svg viewBox="0 0 ${Wd} ${Hd}" role="img" aria-label="Últimas mediciones">
    <polyline points="${v.map((x,i)=>`${X(i).toFixed(1)},${Y(x.kg).toFixed(1)}`).join(' ')}" class="g-l"/>
    ${v.map((x,i)=>`<circle cx="${X(i).toFixed(1)}" cy="${Y(x.kg).toFixed(1)}" r="2.5" class="g-p"/>${x.real>0?`<circle cx="${X(i).toFixed(1)}" cy="${Y(x.real).toFixed(1)}" r="3" class="g-r"/>`:''}`).join('')}</svg>
    <figcaption>Últimas ${v.length} mediciones: estimado (línea) y báscula (anillos).</figcaption></figure>`;
}
// histograma: marcas (líneas) en los valores que importan (límites del modelo, ±5 %)
function hist(v,{lo,hi,n=14,marcas=[],fmt=x=>nf(x,1),cap=''}={}){
  v=v.filter(esNum);if(v.length<2)return '';lo=lo??Math.min(...v);hi=hi??Math.max(...v);if(!(hi>lo)){lo-=1;hi+=1;}
  const b=new Array(n).fill(0);for(const x of v)b[Math.min(n-1,Math.max(0,Math.floor((x-lo)/(hi-lo)*n)))]++;
  const H=96,m=14,bw=(GW-2*m)/n,mx=Math.max(...b),X=z=>m+(z-lo)/(hi-lo)*(GW-2*m);
  return `<figure class="pc-graf"><svg viewBox="0 0 ${GW} ${H+16}" role="img">${b.map((c,i)=>c?`<rect x="${(m+i*bw+1).toFixed(1)}" y="${(H-c/mx*(H-6)).toFixed(1)}" width="${Math.max(1,bw-2).toFixed(1)}" height="${(c/mx*(H-6)).toFixed(1)}" rx="2" class="g-bar"/>`:'').join('')}
    <line x1="${m}" y1="${H}" x2="${GW-m}" y2="${H}" class="g-eje"/>${marcas.filter(z=>z>=lo&&z<=hi).map(z=>`<line x1="${X(z).toFixed(1)}" y1="2" x2="${X(z).toFixed(1)}" y2="${H}" class="g-mk"/>`).join('')}
    <text x="${m}" y="${H+13}" class="g-t" data-no-tr>${fmt(lo)}</text><text x="${GW-m}" y="${H+13}" class="g-t" text-anchor="end" data-no-tr>${fmt(hi)}</text></svg>${cap?`<figcaption>${cap}</figcaption>`:''}</figure>`;
}
// nube de puntos; cero: línea en y = 0; diag: la identidad; banda: ±banda alrededor de la identidad o de 0
function nube(pts,{cap='',diag=false,cero=false,banda=0,fx=x=>nf(x,1),fy=y=>nf(y,1),cls}={}){
  pts=pts.filter(p=>esNum(p[0])&&esNum(p[1]));if(pts.length<2)return '';
  const H=170,m=30,xs=pts.map(p=>p[0]),ys=pts.map(p=>p[1]);let x0=Math.min(...xs),x1=Math.max(...xs),y0=Math.min(...ys,cero?-banda:Infinity),y1=Math.max(...ys,cero?banda:-Infinity);
  if(diag){x0=y0=Math.min(x0,y0);x1=y1=Math.max(x1,y1);}const px=(x1-x0)*.06||1,py=(y1-y0)*.08||1;x0-=px;x1+=px;y0-=py;y1+=py;
  const X=x=>m+(x-x0)/(x1-x0)*(GW-m-8),Y=y=>H-14-(y-y0)/(y1-y0)*(H-22);
  const bd=cero&&banda?`<rect x="${m}" y="${Y(banda).toFixed(1)}" width="${GW-m-8}" height="${(Y(-banda)-Y(banda)).toFixed(1)}" class="g-banda"/>`:'';
  return `<figure class="pc-graf"><svg viewBox="0 0 ${GW} ${H}" role="img">${bd}${cero?`<line x1="${m}" y1="${Y(0).toFixed(1)}" x2="${GW-8}" y2="${Y(0).toFixed(1)}" class="g-id"/>`:''}${diag?`<line x1="${X(x0)}" y1="${Y(x0)}" x2="${X(x1)}" y2="${Y(x1)}" class="g-id"/>`:''}
    <line x1="${m}" y1="${H-14}" x2="${GW-8}" y2="${H-14}" class="g-eje"/><line x1="${m}" y1="4" x2="${m}" y2="${H-14}" class="g-eje"/>
    ${pts.map(p=>`<circle cx="${X(p[0]).toFixed(1)}" cy="${Y(p[1]).toFixed(1)}" r="3" class="${p[2]||cls||'g-p'}"/>`).join('')}
    <text x="${m}" y="${H-2}" class="g-t" data-no-tr>${fx(x0+px)}</text><text x="${GW-8}" y="${H-2}" class="g-t" text-anchor="end" data-no-tr>${fx(x1-px)}</text>
    <text x="${m-3}" y="12" class="g-t" text-anchor="end" data-no-tr>${fy(y1-py)}</text><text x="${m-3}" y="${H-16}" class="g-t" text-anchor="end" data-no-tr>${fy(y0+py)}</text></svg>${cap?`<figcaption>${cap}</figcaption>`:''}</figure>`;
}
// líneas: [{pts:[[x,y]…], cls}] y una línea horizontal de meta
function lineas(S0,{cap='',meta=null,fy=y=>nf(y,1),fx=null}={}){
  const S=S0.filter(s=>s.pts.filter(p=>esNum(p[1])).length>=2);if(!S.length)return '';
  const all=S.flatMap(s=>s.pts).filter(p=>esNum(p[1])),H=150,m=30;let x0=Math.min(...all.map(p=>p[0])),x1=Math.max(...all.map(p=>p[0])),y0=Math.min(...all.map(p=>p[1]),meta??Infinity),y1=Math.max(...all.map(p=>p[1]),meta??-Infinity);
  if(!(x1>x0))x1=x0+1;const py=(y1-y0)*.08||1;y0-=py;y1+=py;
  const X=x=>m+(x-x0)/(x1-x0)*(GW-m-8),Y=y=>H-14-(y-y0)/(y1-y0)*(H-22);
  return `<figure class="pc-graf"><svg viewBox="0 0 ${GW} ${H}" role="img">${meta!=null?`<line x1="${m}" y1="${Y(meta).toFixed(1)}" x2="${GW-8}" y2="${Y(meta).toFixed(1)}" class="g-mk"/>`:''}
    <line x1="${m}" y1="${H-14}" x2="${GW-8}" y2="${H-14}" class="g-eje"/><line x1="${m}" y1="4" x2="${m}" y2="${H-14}" class="g-eje"/>
    ${S.map(s=>s.puntos?s.pts.filter(p=>esNum(p[1])).map(p=>`<circle cx="${X(p[0]).toFixed(1)}" cy="${Y(p[1]).toFixed(1)}" r="3" class="${s.cls}"/>`).join(''):`<polyline points="${s.pts.filter(p=>esNum(p[1])).map(p=>`${X(p[0]).toFixed(1)},${Y(p[1]).toFixed(1)}`).join(' ')}" class="${s.cls}"/>`).join('')}
    <text x="${m-3}" y="12" class="g-t" text-anchor="end" data-no-tr>${fy(y1-py)}</text><text x="${m-3}" y="${H-16}" class="g-t" text-anchor="end" data-no-tr>${fy(y0+py)}</text>
    ${fx?`<text x="${m}" y="${H-2}" class="g-t" data-no-tr>${fx(x0)}</text><text x="${GW-8}" y="${H-2}" class="g-t" text-anchor="end" data-no-tr>${fx(x1)}</text>`:''}</svg>${cap?`<figcaption>${cap}</figcaption>`:''}</figure>`;
}

/* ---------- formatos ---------- */
const pc=v=>esNum(v)?`${nf(v*100,1)} %`:'—',pcs=v=>esNum(v)?`${v>0?'+':''}${nf(v*100,1)} %`:'—',kgs=v=>esNum(v)?wtxt(v,1):'—',n2=(v,d=2)=>esNum(v)?nf(v,d):'—';
const hora=ts=>{const d=new Date(ts);return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;};
const tabla=(cab,filas,cls='')=>filas.length?`<div class="lab-tw"><table class="lab-t ${cls}"><thead><tr>${cab.map(c=>`<th>${c}</th>`).join('')}</tr></thead><tbody>${filas.map(f=>`<tr>${f.map((c,i)=>i?`<td data-no-tr>${c}</td>`:`<td>${c}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`:'';
const tiles=T=>`<div class="pc-tiles">${T.map(([v,t])=>`<div><b data-no-tr>${v}</b><span>${t}</span></div>`).join('')}</div>`;
const caja=(t,c,body)=>`<section class="sec">${secH(t,c)}<div class="card pad lab-c">${body}</div></section>`;
const vacio=t=>`<p class="empty">${t}</p>`;
const areteDe=x=>{if(!x||!x.aid)return '';try{const L=calc().L[x.lote];const a=L&&(L.l.animales||[]).find(z=>z.id===x.aid);return a?a.arete:'';}catch(e){return '';}};

/* ---------- la tarjeta en #pesocam ---------- */
function html(modo){
  const E=estad(modo),m=E.modelo,per=modo==='persona';
  const T=[
    [E.n,'mediciones'],[E.nReal,'con báscula'],[E.nCal>=2?'±'+pc(E.loo):'—','error esperado (validación cruzada)'],[E.nReal?'±'+pc(E.mape):'—','error medio al medir'],
    [pcs(E.sesgo),'sesgo'],[kgs(E.rmse),'RMSE'],[n2(E.r2),'R²'],[E.nCal?'±'+pc(E.fab):'—','error sin calibrar'],
    [E.nSes?pc(E.rep):'—','repetibilidad (CV)'],[pc(E.cvInt),'dispersión entre fotos'],[esNum(E.fps)?nf(E.fps,1):'—','cuadros/s'],[esNum(E.msP)?`${nf(E.msP)} ms`:'—','modelo preciso']];
  const ses=PCc().labSes;
  return `<section class="sec">${secH('Laboratorio',E.n||'')}<div class="card pad pc-lab">
    <p class="pc-lab-sub">Datos de desarrollador: cada medición queda registrada aquí con todo lo que hizo el modelo. Anota el peso de báscula en las que puedas para medir el error del modelo.</p>
    ${ses?`<p class="lab-ses-on">${ico('aviso',2)}<span><span>Sesión de prueba:</span> <b data-no-tr>${esc(ses.n)}</b></span></p>`:''}
    <a class="btn pri full lab-abrir" href="#pclab">Abrir el laboratorio completo</a>
    ${E.n?`${tiles(T)}
      ${dispersion(E.con)}${serie(E.R)}
      <p class="pc-lab-mod" data-no-tr>v${P.VERSION_MODELO} · k = ${nf(m.k,3)} · b = ${nf(m.b,2)}${per?` · ANSUR II n = ${P.ANSUR.n}`:''}</p>
      <div class="rows">${E.R.slice(-15).reverse().map(filaReg).join('')}</div>
      <div class="pc-acts"><button type="button" class="btn" data-act="pcRegCsv">Exportar CSV</button><button type="button" class="btn" data-act="pcRegAnalisis">Exportar para análisis</button><button type="button" class="btn" data-act="pcRegLimpiar">Borrar registro</button></div>`
    :`<p class="empty">Todavía no hay mediciones.</p>`}</div></section>`;
}
function filaReg(x){const er=x.real>0?(x.kg-x.real)/x.real:null,ar=areteDe(x);
  return `<button type="button" class="row pc-reg" data-act="pcRegVer" data-id="${x.id}"><div class="tx"><b>${wtxt(x.kg,1)}${x.real>0?` <span class="pc-real">· <span>báscula</span> ${wtxt(x.real,1)}</span>`:''}</b>
    <span><span>${ffc(x.f)}</span> <span data-no-tr>${hora(x.ts)}</span>${ar?` · <span>Arete</span> <span data-no-tr>${esc(ar)}</span>`:''}${x.HG>0?` · <span>cinta</span>`:''}${er!=null?` · <span class="${Math.abs(er)<=.05?'ok':'mal'}">${pcs(er)}</span>`:''}${esNum(x.cv)?` · <span>±${nf(x.cv*100,1)} %</span>`:''}${x.cond?` · <span>con datos</span>`:''}</span></div>${ico('chev')}</button>`;}

/* ======================= la página #pclab ======================= */
const TABS=[['resumen','Resumen'],['errores','Errores'],['medidas','Medidas'],['calidad','Calidad'],['animales','Animales'],['modelo','Modelo'],['registro','Registro']];
PAGES.pclab=()=>{
  const r=route(),tab=TABS.some(t=>t[0]===r.id)?r.id:'resumen',modo=UI.pc&&UI.pc.modo||'ganado';
  let cuerpo='';try{cuerpo=({resumen:tResumen,errores:tErrores,medidas:tMedidas,calidad:tCalidad,animales:tAnimales,modelo:tModelo,registro:tRegistro})[tab](modo);}catch(e){console.error(e);cuerpo=vacio('No se pudo calcular esta parte.');}
  return `<header class="hd">${hdBack('#pesocam','Peso con cámara')}<div class="ttl" style="margin-top:-6px"><span class="eyebrow pc-beta">Beta</span><h1>Laboratorio</h1><p class="sub">Todo lo que mide y calcula el modelo de peso con cámara, para revisarlo y ajustarlo.</p></div></header>
  <main class="bd lab">
   <div class="seg pc-modo" role="group" aria-label="Qué medir">${[['persona','Personas'],['ganado','Ganado']].map(([v,t])=>`<button type="button" data-act="labModo" data-m="${v}" aria-pressed="${modo===v}">${t}</button>`).join('')}</div>
   <nav class="chips lab-tabs" aria-label="Partes del laboratorio">${TABS.filter(t=>modo==='ganado'||t[0]!=='animales').map(([k,t])=>`<a class="chip" href="#pclab/${k}" aria-pressed="${k===tab}">${t}</a>`).join('')}</nav>
   ${cuerpo}
  </main>`;
};
ACTS.labModo=el=>{const m=el.dataset.m;if(!P.MODOS[m])return;UI.pc.modo=m;try{localStorage.setItem('rumentis-pc-modo',m);}catch(e){}render();};

/* ---------- Resumen ---------- */
function tResumen(modo){
  const E=estad(modo),{F,G}=etiquetasLab(modo),R=E.R,ses=PCc().labSes;
  const sesion=`<section class="sec">${secH('Sesión de prueba')}<div class="card pad lab-c">
    ${ses?`<p class="lab-ses-on">${ico('aviso',2)}<span><span>En curso:</span> <b data-no-tr>${esc(ses.n)}</b> · <span>${pl(R.filter(x=>x.ses===ses.n).length,'medición','mediciones')}</span></span></p>
      <button type="button" class="btn full" data-act="labSesFin">Terminar la sesión</button>`
      :`<p class="hint" style="margin:0">Una sesión agrupa las mediciones de una prueba (por ejemplo, el día que pesas en la finca) para analizarlas y exportarlas juntas.</p><button type="button" class="btn pri full" data-act="labSesIni">Empezar una sesión de prueba</button>`}
    ${modo==='ganado'?`<div class="lab-tog"><div><b>Repetir el mismo animal</b><span>Las mediciones nuevas toman el arete de la anterior (30 minutos). Para medir la repetibilidad: mide 3 a 5 veces cada animal.</span></div><button type="button" class="tog" data-act="labMismo" role="switch" aria-checked="${!!PCc().labMismo}" aria-label="Repetir el mismo animal"><span></span></button></div>`:''}</div></section>`;
  if(!R.length)return sesion+caja('Estado del modelo','',vacio('Todavía no hay mediciones. Mide con la cámara y vuelve aquí.'));
  const eF=F.map(f=>f.e),T=[[R.length,'mediciones'],[F.length+G.length,'pesos reales'],[F.length?'±'+pc(prom(eF.map(Math.abs))):'—','error esperado (validación cruzada)'],
    [F.length?pcs(prom(eF)):'—','sesgo'],[F.some(f=>esNum(f.ani))?'±'+pc(prom(F.map(f=>f.ani).filter(esNum).map(Math.abs))):'—','animal conocido'],[F.length?'±'+pc(prom(F.map(f=>Math.abs(f.fab)))):'—','error sin calibrar'],
    [E.nSes?pc(E.rep):'—','repetibilidad (CV)'],[pc(E.cvInt),'dispersión entre fotos'],[F.length?pc(F.filter(f=>Math.abs(f.e)<=.05).length/F.length):'—','dentro de ±5 %']];
  return sesion+caja('Estado del modelo',`v${P.VERSION_MODELO}`,`${tiles(T)}${hist(eF.map(e=>e*100),{lo:Math.min(-15,...eF.map(e=>e*100)),hi:Math.max(15,...eF.map(e=>e*100)),marcas:[-5,0,5],fmt:v=>`${nf(v)} %`,cap:'Error con validación cruzada de cada peso real. Líneas: −5 %, 0 y +5 %.'})}`)
    +caja('Diagnóstico','',diagnostico(modo,E,F,G));
}
// lo que el laboratorio ve en los datos: cada hallazgo con lo que hay que revisar
function diagnostico(modo,E,F,G){
  const H=[],add=(n,t)=>H.push([n,t]),R=E.R,gan=modo==='ganado';
  const nInd=F.length,mape=prom(F.map(f=>Math.abs(f.e))),ses=prom(F.map(f=>f.e)),fabS=prom(F.map(f=>f.fab));
  if(nInd<10)add(1,`<span>Hay</span> <b data-no-tr>${nInd}</b> <span>pesos reales de animales con arete. Con menos de 10 los números todavía no son confiables: la meta es 20 o más, de animales distintos.</span>`);
  if(nInd>=3&&Math.abs(fabS)>.03)add(2,`<span>El modelo de fábrica ${fabS>0?'sobreestima':'subestima'} en promedio</span> <b data-no-tr>${pc(Math.abs(fabS))}</b><span>. La calibración lo corrige; si es más de 10 %, revisar el tipo de animal, la etapa o la estatura de referencia.</span>`);
  if(nInd>=5)add(mape>.05?3:0,mape>.05?`<span>Error esperado</span> <b data-no-tr>${pc(mape)}</b><span>: más que la meta de 5 %.</span>`:`<span>Error esperado</span> <b data-no-tr>${pc(mape)}</b><span>: dentro de la meta de 5 %.</span>`);
  if(nInd>=5&&Math.abs(ses)>.02)add(2,`<span>Queda un sesgo de</span> <b data-no-tr>${pcs(ses)}</b> <span>con validación cruzada: los pesos reales tienen fechas o fuentes que no coinciden, o el modelo se tuerce con el tamaño (ver Medidas).</span>`);
  if(E.nSes&&E.rep>.03)add(2,`<span>La misma medición repetida varía</span> <b data-no-tr>${pc(E.rep)}</b><span>: la toma misma aporta buena parte del error. Ver Calidad.</span>`);
  else if(E.nSes)add(0,`<span>Repetibilidad</span> <b data-no-tr>${pc(E.rep)}</b><span>: la toma es estable.</span>`);
  else add(1,'Sin mediciones repetidas todavía: mide el mismo animal 3 a 5 veces seguidas para saber cuánto varía la toma.');
  if(gan){
    const D=R.filter(x=>x.dims&&x.dims.WH>0);
    const tope=D.filter(x=>x.topes&&x.topes.length).length;if(D.length>=5&&tope/D.length>.2)add(2,`<span>En</span> <b data-no-tr>${pc(tope/D.length)}</b> <span>de las mediciones el fondo de pecho salió del rango del modelo y se ajustó: revisar que la toma de costado sea de perfil y sin nada que tape el pecho.</span>`);
    const dW=D.filter(x=>esNum(x.dims.WHr)&&x.dims.WHs>0).map(x=>Math.abs(x.dims.WHr/x.dims.WHs-1));if(dW.length>=5&&medn(dW)>.06)add(2,`<span>La alzada de costado y la de atrás difieren</span> <b data-no-tr>${pc(medn(dW))}</b> <span>(mediana): la persona de referencia no está a la par del animal en alguna de las dos tomas.</span>`);
    const sinP=R.filter(x=>x.puntos===false).length;if(sinP)add(2,`<span>Mediciones sin los puntos del animal:</span> <b data-no-tr>${sinP}</b><span>. Se calcularon con el volumen (±30 %): repetirlas con el animal entero a la vista.</span>`);
    const C=F.filter(f=>f.x&&(f.x.HG>0||(f.x.cond&&f.x.cond.cinta>0))&&f.x.dims&&f.x.dims.WH>0);
    if(C.length>=3){const ec=prom(C.map(f=>Math.abs(P.pesoCinta(f.x.dims.WH,f.x.HG>0?f.x.HG:f.x.cond.cinta,{g:f.x.tipo,etapa:f.x.etapa})/f.kg-1))),ef=prom(C.map(f=>Math.abs((f.x.predFoto||f.x.pred)/f.kg-1)));
      add(0,`<span>Sin calibrar, con cinta el error es</span> <b data-no-tr>${pc(ec)}</b> <span>y solo con foto</span> <b data-no-tr>${pc(ef)}</b><span>.</span>`);}
  }
  const q=R.map(x=>x.dq).filter(Boolean);
  if(q.length>=3){
    const el=medn(q.map(d=>esNum(d.elev)?Math.abs(d.elev):NaN));if(el>10)add(2,`<span>El teléfono apunta</span> <b data-no-tr>${nf(el)}°</b> <span>hacia arriba o hacia abajo (mediana): sostenerlo derecho, a la altura del pecho del animal. La inclinación cambia la alzada.</span>`);
    const tr=medn(q.map(d=>d.tamRef));if(gan&&tr<.3)add(2,`<span>La persona de referencia ocupa</span> <b data-no-tr>${pc(tr)}</b> <span>del alto de la foto: acercarse. Un error de 1 cm en su alto cambia el peso cerca de 1.2 %.</span>`);
    const ni=medn(q.map(d=>d.nitidez));if(ni<300)add(2,'Fotos poco nítidas (movidas o desenfocadas): más luz o quedarse quieto antes del disparo.');
  }
  for(const [nom,fn] of FACT_ERR){const t=tercios(F,fn);if(!t)continue;const [a,,c]=t;const peor=a.mape>c.mape?a:c,mejor=a.mape>c.mape?c:a;
    if(peor.mape>1.6*mejor.mape&&peor.mape-mejor.mape>.02)add(2,`<span>${nom}:</span> <span>el error sube a</span> <b data-no-tr>${pc(peor.mape)}</b> <span>${peor===a?'en el tercio bajo':'en el tercio alto'}</span> <span>(contra</span> <b data-no-tr>${pc(mejor.mape)}</b><span>).</span>`);}
  if(!H.length)return vacio('Nada que revisar por ahora.');
  return `<ul class="lab-diag">${H.sort((a,b)=>b[0]-a[0]).map(([n,t])=>`<li class="n${n}">${t}</li>`).join('')}</ul>`;
}

/* ---------- Errores ---------- */
// error medio por grupo (con validación cruzada) y sesgo
function porGrupo(F,clave){const g=new Map();for(const f of F){const k=clave(f);if(k==null||k==='')continue;if(!g.has(k))g.set(k,[]);g.get(k).push(f);}
  return [...g.entries()].map(([k,v])=>[k,v.length,'±'+pc(prom(v.map(f=>Math.abs(f.e)))),pcs(prom(v.map(f=>f.e))),esNum(prom(v.map(f=>f.ani).filter(esNum)))?'±'+pc(prom(v.map(f=>f.ani).filter(esNum).map(Math.abs))):'—']);}
const CAB_G=['','n','error','sesgo','animal conocido'];
function tercios(F,fn){const v=F.map(f=>[fn(f),f]).filter(z=>esNum(z[0])).sort((a,b)=>a[0]-b[0]);if(v.length<9)return null;const n=v.length,c=[v.slice(0,Math.floor(n/3)),v.slice(Math.floor(n/3),Math.floor(2*n/3)),v.slice(Math.floor(2*n/3))];
  return c.map(g=>({lo:g[0][0],hi:g[g.length-1][0],n:g.length,mape:prom(g.map(z=>Math.abs(z[1].e)))}));}
const COND_G=[
  ['sexo','Sexo',[['h','Hembra'],['m','Macho'],['c','Castrado']]],
  ['cc','Condición corporal (1 a 5)',[['1','1'],['2','2'],['3','3'],['4','4'],['5','5']]],
  ['llenado','Llenado',[['ayuno','En ayuno (más de 12 h)'],['medio','De 4 a 12 h sin comer'],['lleno','Comió hace menos de 4 h']]],
  ['prenez','Preñez',[['no','No'],['1','1 a 3 meses'],['2','4 a 6 meses'],['3','7 a 9 meses']]],
  ['pelo','Pelo',[['corto','Corto'],['largo','Largo'],['mojado','Mojado']]],
  ['postura','Postura',[['bien','Bien parado'],['patas','Patas abiertas o juntas'],['cabeza','Cabeza baja o girada'],['movio','Se movió']]],
  ['suelo','Suelo',[['plano','Plano'],['inclinado','Inclinado'],['tapa','Barro o pasto alto (tapa las patas)']]],
  ['fondo','Fondo',[['limpio','Limpio'],['animales','Otros animales'],['cerca','Cerca o corral']]]];
const COND_P=[['sexo','Sexo',[['h','Mujer'],['m','Hombre']]],['llenado','Llenado',[['ayuno','En ayuno (más de 12 h)'],['medio','De 4 a 12 h sin comer'],['lleno','Comió hace menos de 4 h']]]];
const condDe=modo=>modo==='ganado'?COND_G:COND_P;
const txtOpc=(L,v)=>{const o=L.find(z=>z[0]===String(v));return o?o[1]:null;};
function tErrores(modo){
  const {F,G}=etiquetasLab(modo);
  if(!F.length&&!G.length)return caja('Errores','',vacio(modo==='ganado'?'Todavía no hay pesos reales. Anota el peso de báscula en una medición, elige el arete de cada animal medido o registra los pesajes, entradas y ventas: la app los une solos.':'Todavía no hay pesos de báscula: anota tu peso en una medición.'));
  const tot=[[F.length,'pesos reales de un animal'],[G.length,'de grupo'],['±'+pc(prom(F.map(f=>Math.abs(f.fab)))),'sin calibrar'],['±'+pc(prom(F.map(f=>Math.abs(f.hoy)))),'modelo de hoy (con ese peso)'],['±'+pc(prom(F.map(f=>Math.abs(f.e)))),'validación cruzada'],
    [F.some(f=>esNum(f.ani))?'±'+pc(prom(F.map(f=>f.ani).filter(esNum).map(Math.abs))):'—','animal conocido']];
  // la curva de aprendizaje: el error de cada peso real con lo aprendido antes de él (promedio móvil de 5)
  const sq=F.filter(f=>f.canal==='foto').sort((a,b)=>a.pos-b.pos),mov=sq.map((f,i)=>{const w=sq.slice(Math.max(0,i-4),i+1);return [i+1,prom(w.map(z=>Math.abs(z.seq)))*100];});
  const curva=lineas([{pts:mov,cls:'g-l'},{pts:sq.map((f,i)=>[i+1,Math.abs(f.seq)*100]),cls:'g-p',puntos:true}],{meta:5,fy:y=>`${nf(y)} %`,fx:x=>nf(x),cap:'Curva de aprendizaje: el error de cada peso real con lo que la app había aprendido antes de él (puntos) y su promedio de 5 (línea). Línea roja: la meta de 5 %.'});
  const res=nube(F.map(f=>[W(f.kg),f.e*100]),{cero:true,banda:5,fy:y=>`${nf(y)} %`,fx:x=>nf(x),cap:`Error con validación cruzada contra el peso real (${UW()}). Si sube o baja con el peso, la potencia b no cuadra con tu ganado.`});
  const C=modo==='ganado'?condDe(modo):COND_P;
  const tablas=[['Por fuente del peso real',f=>NOM_SRC[f.src]||f.src],['Por canal',f=>f.canal==='cinta'?'Con cinta':'Solo foto'],
    ...(modo==='ganado'?[['Por tipo de animal',f=>f.x&&f.x.tipo?(P.TIPOS_G.find(t=>t[0]===f.x.tipo)||[])[1]:null],['Por etapa',f=>f.x?({ternero:'Ternero',crec:'En crecimiento',adulto:'Adulto o terminado'})[P.etapaDe(f.x)]:null],
      ['Por cámaras',f=>f.x?(f.x.camaras&&f.x.camaras.length>2?`${f.x.camaras.length} cámaras`:'2 tomas, 1 teléfono'):null],['Por sesión de prueba',f=>f.x&&f.x.ses?f.x.ses:null]]:[]),
    ['Por modelo de la silueta',f=>f.x?(f.x.fuente==='preciso'?'Preciso':'Rápido'):null],
    ...C.map(([k,t,L])=>[t,f=>f.x&&f.x.cond&&f.x.cond[k]!=null&&f.x.cond[k]!==''?txtOpc(L,f.x.cond[k]):null])];
  const pesoT=tercios(F,f=>f.kg);
  let h=caja('Errores',`${F.length}`,`${tiles(tot)}<p class="hint" style="margin:0">Validación cruzada: el modelo se ajusta sin ese peso y luego lo predice (el error que tendrás con un animal nuevo). Animal conocido: además con el factor propio del animal, de sus otros pesos.</p>`)
    +caja('Curva de aprendizaje','',curva||vacio('Hacen falta al menos 2 pesos reales.'))
    +caja('Error contra el peso','',res+(pesoT?tabla(['','n','error'],pesoT.map((t,i)=>[['Livianos','Medianos','Pesados'][i],`${t.n} · ${nf(W(t.lo))}–${nf(W(t.hi))}`,'±'+pc(t.mape)])):''));
  h+=caja('Por grupos','',tablas.map(([t,fn])=>{const g=porGrupo(F,fn);return g.length?`<h3 class="lab-h3">${t}</h3>`+tabla(CAB_G,g):'';}).join('')||vacio('Sin grupos todavía.'));
  if(modo==='ganado'){
    const Cn=F.filter(f=>f.x&&f.x.dims&&f.x.dims.WH>0&&(f.x.HG>0||(f.x.cond&&f.x.cond.cinta>0)));
    if(Cn.length)h+=caja('Foto, cinta y báscula',Cn.length,tabla(['','n','error medio','sesgo'],[['Solo foto (sin calibrar)',Cn.length,...ems(Cn.map(f=>(f.x.predFoto||f.x.pred)/f.kg-1))],
      ['Cinta (sin calibrar)',Cn.length,...ems(Cn.map(f=>P.pesoCinta(f.x.dims.WH,f.x.HG>0?f.x.HG:f.x.cond.cinta,{g:f.x.tipo,etapa:f.x.etapa})/f.kg-1))]])
      +nube(Cn.map(f=>[W(f.kg),W(P.pesoCinta(f.x.dims.WH,f.x.HG>0?f.x.HG:f.x.cond.cinta,{g:f.x.tipo,etapa:f.x.etapa})),'g-r']).concat(Cn.map(f=>[W(f.kg),W(f.x.predFoto||f.x.pred)])),{diag:true,fx:x=>nf(x),fy:y=>nf(y),cap:`Estimado sin calibrar contra el peso real (${UW()}): foto (puntos) y cinta (anillos).`}));
  }
  if(G.length)h+=caja('Pesos de grupo',G.length,tabla(['','n','real','cámara','error'],G.map(g=>[NOM_SRC[g.src]||g.src,g.n,kgs(g.kg),kgs(g.kg*(1+g.e)),pcs(g.e)])));
  h+=caja('Cada peso real',F.length,`<div class="rows">${F.slice().sort((a,b)=>Math.abs(b.e)-Math.abs(a.e)).slice(0,40).map(f=>`<button type="button" class="row pc-reg" data-act="pcRegVer" data-id="${f.id}"><div class="tx"><b>${kgs(f.kg)} <span class="pc-real">· <span>${NOM_SRC[f.src]||f.src}</span></span></b><span><span>${f.x?ffc(f.x.f):''}</span>${f.x&&areteDe(f.x)?` · <span>Arete</span> <span data-no-tr>${esc(areteDe(f.x))}</span>`:''} · <span>validación</span> <span class="${Math.abs(f.e)<=.05?'ok':'mal'}">${pcs(f.e)}</span> · <span>sin calibrar</span> <span data-no-tr>${pcs(f.fab)}</span></span></div>${ico('chev')}</button>`).join('')}</div><p class="hint" style="margin:0">Ordenados del peor al mejor.</p>`);
  return h;
}
const ems=v=>['±'+pc(prom(v.map(Math.abs))),pcs(prom(v))];

/* ---------- Medidas ---------- */
function tMedidas(modo){
  const R=P.REG().filter(x=>x.modo===modo);if(!R.length)return caja('Medidas','',vacio('Todavía no hay mediciones.'));
  const {F}=etiquetasLab(modo);let h='';
  if(modo==='ganado'){
    const D=R.filter(x=>x.dims&&x.dims.WH>0),G=P.GANADO;if(!D.length)return caja('Medidas','',vacio('Ninguna medición tiene alzada y fondo de pecho (faltaron los puntos del animal).'));
    const q=D.map(x=>x.dims.CD/x.dims.WH);
    h+=caja('Alzada y fondo de pecho',D.length,tiles([[`${nf(medn(D.map(x=>x.dims.WH)),1)} cm`,'alzada (mediana)'],[`${nf(medn(D.map(x=>x.dims.CD)),1)} cm`,'fondo de pecho'],[nf(medn(q),3),'fondo / alzada'],
        [`${nf(medn(D.map(x=>x.dims.L)),1)} cm`,'largo'],[nf(medn(D.map(x=>x.dims.L/x.dims.WH)),2),'largo / alzada'],[pc(D.filter(x=>x.topes&&x.topes.length).length/D.length),'ajustadas al límite']])
      +hist(D.map(x=>x.dims.WH),{fmt:v=>`${nf(v)} cm`,cap:'Alzada a la cruz.'})
      +hist(q,{lo:Math.min(.38,...q),hi:Math.max(.7,...q),marcas:[G.cdwh[0],G.r0,G.cdwh[1]],fmt:v=>nf(v,2),cap:`Fondo de pecho entre alzada. Líneas: los límites del modelo (${nf(G.cdwh[0],2)} y ${nf(G.cdwh[1],2)}) y el valor medio (${nf(G.r0,2)}). Fuera de los límites se ajusta: si muchas caen ahí, la toma de costado no es de perfil.`})
      +hist(D.map(x=>x.dims.L/x.dims.WH),{fmt:v=>nf(v,2),cap:'Largo (del hombro a la base de la cola) entre alzada. Si una medición queda muy por debajo de las demás, el animal estaba girado hacia la cámara.'}));
    const dW=D.filter(x=>esNum(x.dims.WHr)&&x.dims.WHs>0).map(x=>(x.dims.WHr/x.dims.WHs-1)*100),dQ=D.filter(x=>esNum(x.dims.qOtro)).map(x=>(x.dims.qOtro/(x.dims.CD/x.dims.WH)-1)*100);
    h+=caja('¿Coinciden las cámaras?','',(hist(dW,{lo:Math.min(-20,...dW),hi:Math.max(20,...dW),marcas:[-12,0,12],fmt:v=>`${nf(v)} %`,cap:'Alzada de atrás contra la de costado. Dentro de ±12 % se promedian; fuera, se usa solo la de costado.'})||vacio('Sin alzadas de atrás todavía.'))
      +(dQ.length?hist(dQ,{lo:Math.min(-30,...dQ),hi:Math.max(30,...dQ),marcas:[-25,0,25],fmt:v=>`${nf(v)} %`,cap:'Fondo / alzada del otro costado (cámara enlazada) contra el de este costado.'}):'')
      +tabla(['','mediana','de 10 a 90 %'],[['Variación de la alzada entre fotos',pc(medn(D.map(x=>x.dq&&x.dq.cvWH))),`${pc(pctl(D.map(x=>x.dq&&x.dq.cvWH),.1))} – ${pc(pctl(D.map(x=>x.dq&&x.dq.cvWH),.9))}`],
        ['Variación del fondo de pecho entre fotos',pc(medn(D.map(x=>x.dq&&x.dq.cvCD))),`${pc(pctl(D.map(x=>x.dq&&x.dq.cvCD),.1))} – ${pc(pctl(D.map(x=>x.dq&&x.dq.cvCD),.9))}`]]));
    const FD=F.filter(f=>f.x&&f.x.dims&&f.x.dims.WH>0);
    if(FD.length>=3)h+=caja('Error contra cada medida',FD.length,nube(FD.map(f=>[f.x.dims.WH,f.e*100]),{cero:true,banda:5,fx:x=>`${nf(x)} cm`,fy:y=>`${nf(y)} %`,cap:'Contra la alzada. Si el error sube con la alzada, β es alta; si baja, es baja.'})
      +nube(FD.map(f=>[f.x.dims.CD/f.x.dims.WH,f.e*100]),{cero:true,banda:5,fx:x=>nf(x,2),fy:y=>`${nf(y)} %`,cap:'Contra el fondo / alzada. Si el error sube con la razón, γ es alta; si baja, es baja.'})
      +(FD.some(f=>esNum(f.x.dims.AWL))?nube(FD.filter(f=>esNum(f.x.dims.AWL)).map(f=>[f.x.dims.AWL,f.e*100]),{cero:true,banda:5,fx:x=>nf(x,2),fy:y=>`${nf(y)} %`,cap:'Contra el ancho / largo desde arriba (no entra en el modelo): si hay tendencia, el ancho ayudaría.'}):''));
  }else{
    const D=R.filter(x=>x.dims&&x.dims.bid>0);if(!D.length)return caja('Medidas','',vacio('Sin medidas del cuerpo todavía.'));
    const M=[['bid','Hombros'],['cb','Ancho del pecho'],['cd','Fondo del pecho'],['wb','Ancho de la cintura'],['wd','Fondo de la cintura'],['hb','Ancho de la cadera'],['bd','Fondo de los glúteos'],['th','Contorno del muslo'],['cf','Contorno de la pantorrilla'],['ne','Contorno del cuello']];
    h+=caja('Medidas del cuerpo',D.length,tabla(['','mediana','de 10 a 90 %'],M.map(([k,t])=>[t,`${nf(medn(D.map(x=>x.dims[k])),1)} cm`,`${nf(pctl(D.map(x=>x.dims[k]),.1),1)} – ${nf(pctl(D.map(x=>x.dims[k]),.9),1)}`]))
      +hist(D.map(x=>x.giro),{fmt:v=>`${nf(v)}°`,cap:'Giro de la toma de perfil.'})
      +tabla(['',''],[['Silueta incompleta',pc(D.filter(x=>x.incompleta).length/D.length)],['Alguna medida estimada',pc(D.filter(x=>x.estimadas&&x.estimadas.length).length/D.length)],['Medidas ajustadas al límite',pc(D.filter(x=>x.topes&&x.topes.length).length/D.length)]]));
  }
  return h;
}

/* ---------- Calidad ---------- */
const FACT=[
  ['nitidez','Nitidez',x=>x.dq&&x.dq.nitidez,v=>nf(v)],['brillo','Brillo',x=>x.dq&&x.dq.brillo,pc],['quemado','Zonas quemadas',x=>x.dq&&x.dq.quemado,pc],['oscuro','Zonas negras',x=>x.dq&&x.dq.oscuro,pc],
  ['tam','Alto del sujeto en la foto',x=>x.dq&&x.dq.tam,pc],['tamRef','Alto de la persona de referencia en la foto',x=>x.dq&&x.dq.tamRef,pc],
  ['kp','Confianza de los puntos',x=>x.dq&&x.dq.kp,v=>nf(v,2)],['kpMin','Punto menos confiable',x=>x.dq&&x.dq.kpMin,v=>nf(v,2)],['score','Seguridad de la silueta',x=>x.score,pc],
  ['cvPred','Desacuerdo entre fotos',x=>x.dq&&esNum(x.dq.cvPred)?x.dq.cvPred:x.cv,pc],['elev','Inclinación del teléfono',x=>x.dq&&esNum(x.dq.elev)?Math.abs(x.dq.elev):NaN,v=>`${nf(v,1)}°`],
  ['horiz','Giro de la imagen',x=>x.dq&&esNum(x.dq.horiz)?Math.abs(x.dq.horiz):NaN,v=>`${nf(v,1)}°`],['LWH','Largo / alzada',x=>x.dq&&x.dq.LWH,v=>nf(v,2)]];
const FACT_ERR=FACT.filter(f=>['nitidez','brillo','tam','tamRef','kp','cvPred','elev'].includes(f[0])).map(([,t,fn])=>[t,f=>f.x?fn(f.x):NaN]);
function tCalidad(modo){
  const R=P.REG().filter(x=>x.modo===modo);if(!R.length)return caja('Calidad de las tomas','',vacio('Todavía no hay mediciones.'));
  const {F}=etiquetasLab(modo),Q=R.filter(x=>x.dq);
  const filas=[],ter=[];for(const [k,t,fn,fmt] of FACT){const v=R.map(fn).filter(esNum);if(!v.length)continue;filas.push([t,fmt(medn(v)),`${fmt(pctl(v,.1))} – ${fmt(pctl(v,.9))}`]);
    const T=tercios(F,f=>f.x?fn(f.x):NaN);if(T)ter.push([t,...T.map(z=>'±'+pc(z.mape))]);}
  let h=caja('Calidad de las tomas',Q.length,(Q.length?'':`<p class="hint" style="margin:0">Las mediciones de antes de esta versión no tienen los datos de cada toma.</p>`)
    +tabla(['','mediana','de 10 a 90 %'],filas))
    +caja('Error por tercio','',(ter.length?tabla(['','tercio bajo','tercio medio','tercio alto'],ter):'')
    +`<p class="hint" style="margin:0">Los pesos reales se ordenan por cada dato y se parten en tres; si un tercio tiene mucho más error, ese dato importa. Hacen falta 9 pesos reales o más.</p>`);
  const ni=Q.map(x=>x.dq.nitidez).filter(esNum),el=Q.map(x=>x.dq.elev).filter(esNum);
  h+=caja('Luz, nitidez y teléfono','',(hist(ni,{lo:0,fmt:v=>nf(v),marcas:[300],cap:'Nitidez del sujeto (varianza del laplaciano). Una foto movida o desenfocada queda por debajo de 300.'})||'')
    +(hist(Q.map(x=>x.dq.brillo*100),{lo:0,hi:100,marcas:[25,75],fmt:v=>`${nf(v)} %`,cap:'Brillo del sujeto. Entre 25 y 75 % está bien.'})||'')
    +(el.length>=2?hist(el,{lo:Math.min(-20,...el),hi:Math.max(20,...el),marcas:[-10,0,10],fmt:v=>`${nf(v)}°`,cap:'Hacia dónde apunta la cámara: 0 es horizontal, negativo es hacia abajo. Entre −10° y 10° está bien.'}):`<p class="hint" style="margin:0">Sin datos de inclinación: este teléfono no da el acelerómetro a la app.</p>`)
    +(hist(Q.map(x=>x.dq.tamRef*100),{lo:0,hi:100,marcas:[30],fmt:v=>`${nf(v)} %`,cap:'Alto de la persona de referencia en la foto. Menos de 30 %: muy lejos.'})||''));
  // la repetibilidad: grupos de mediciones del mismo animal (o seguidas)
  const S=sesiones(R);
  h+=caja('Repetibilidad',S.length,S.length?tabla(['','n','base','variación'],S.slice(-15).reverse().map(g=>[`${ffc(g[0].f)} ${hora(g[0].ts)}${g[0].aid&&areteDe(g[0])?' · '+esc(areteDe(g[0])):''}`,g.length,kgs(prom(g.map(baseRep))),pc(desv(g.map(baseRep))/prom(g.map(baseRep)))]))
    +`<p class="hint" style="margin:0">Mediciones seguidas (menos de 15 minutos) del mismo animal: cuánto varía el peso del modelo antes de calibrar. Es el piso del error que da la toma misma.</p>`:vacio('Mide el mismo animal 3 a 5 veces seguidas (con el mismo arete) para ver cuánto varía la toma.'));
  const motores={};for(const x of R)if(x.motor)motores[x.motor]=(motores[x.motor]||0)+1;
  h+=caja('Rendimiento','',tabla(['','mediana','de 10 a 90 %'],[['Cuadros por segundo',...mr(R.map(x=>x.fps),1)],['Modelo rápido (ms)',...mr(R.map(x=>x.msR),0)],['Modelo preciso (ms)',...mr(R.map(x=>x.msP),0)],['Modelo de puntos (ms)',...mr(R.map(x=>x.msPose),0)],['Duración (s)',...mr(R.map(x=>x.seg),1)]])
    +tabla(['Motor','mediciones'],Object.entries(motores).map(([k,n])=>[`<span data-no-tr>${esc(k)}</span>`,n])));
  return h;
}
const mr=(v,d)=>{v=v.filter(z=>esNum(z)&&z>0);return v.length?[nf(medn(v),d),`${nf(pctl(v,.1),d)} – ${nf(pctl(v,.9),d)}`]:['—','—'];};

/* ---------- Animales ---------- */
function tAnimales(modo){
  const R=P.REG().filter(x=>x.modo==='ganado'&&x.aid);if(!R.length)return caja('Animales','',vacio('Ninguna medición tiene arete. En el resultado de cada medición elige el arete del animal: así la app sigue a cada uno y aprende de él.'));
  const por=new Map();for(const x of R){if(!por.has(x.aid))por.set(x.aid,[]);por.get(x.aid).push(x);}
  const {F}=etiquetasLab('ganado');
  return caja('Animales',por.size,`<div class="rows">${[...por.entries()].sort((a,b)=>b[1].length-a[1].length).map(([aid,v])=>{const u=v[v.length-1],h=P.historialAnimal(aid,u.lote),fa=P.factorAnimal(aid),nr=F.filter(f=>f.aid===aid).length;
    return `<button type="button" class="row pc-reg" data-act="labAnimal" data-aid="${esc(aid)}"><div class="tx"><b><span>Arete</span> <span data-no-tr>${esc(areteDe(u)||'—')}</span>${h?` · ${wtxt(h.kg)}`:''}</b><span><span>${pl(v.length,'medición','mediciones')}</span> · <span>${pl(nr,'peso real','pesos reales')}</span>${fa.n?` · <span>factor</span> <span data-no-tr>${nf(fa.f,3)}</span>`:''}</span></div>${ico('chev')}</button>`;}).join('')}</div>`);
}
ACTS.labAnimal=el=>{
  const aid=el.dataset.aid,R=P.REG().filter(x=>x.modo==='ganado'&&x.aid===aid).sort((a,b)=>a.ts-b.ts);if(!R.length)return;const u=R[R.length-1];
  const {F}=etiquetasLab('ganado'),E=F.filter(f=>f.aid===aid),h=P.historialAnimal(aid,u.lote),fa=P.factorAnimal(aid),d0=Date.parse(R[0].f),dia=f=>(Date.parse(f)-d0)/864e5;
  const pts=[{pts:R.map(x=>[dia(x.f),W(x.kg)]),cls:'g-p',puntos:true},{pts:E.filter(f=>f.x).map(f=>[dia(f.x.f),W(f.kg)]),cls:'g-r',puntos:true}];
  const S=sesiones(R);
  openSheet(shHead(`<span>Arete</span> <span data-no-tr>${esc(areteDe(u)||'—')}</span>`,'Laboratorio')+`<div class="sh-body pc-det">
    ${lineas(pts,{fy:y=>nf(y),fx:x=>`${nf(x)} d`,cap:`Estimados (puntos) y pesos reales (anillos), en ${UW()}, por día desde la primera medición.`})}
    ${h?`<div class="pc-kv"><span>Peso hoy con su historial</span><b data-no-tr>${wtxt(h.kg)} ±${nf(h.err*100,1)} %</b></div>`:''}
    <div class="pc-kv"><span>Factor propio</span><b data-no-tr>${nf(fa.f,3)} (${fa.n})</b></div>
    ${S.length?`<div class="pc-kv"><span>Repetibilidad</span><b data-no-tr>${pc(prom(S.map(g=>desv(g.map(baseRep))/prom(g.map(baseRep)))))}</b></div>`:''}
    ${E.length?`<div class="pc-kv"><span>Error con validación cruzada</span><b data-no-tr>±${pc(prom(E.map(f=>Math.abs(f.e))))}</b></div>`:''}
    ${R.length>=2&&Date.parse(u.f)>Date.parse(R[0].f)?`<div class="pc-kv"><span>Ganancia según la cámara</span><b data-no-tr>${wtxt((u.kg-R[0].kg)/dia(u.f),2)}/d</b></div>`:''}
    <div class="rows">${R.slice().reverse().map(filaReg).join('')}</div></div>`);
};

/* ---------- Modelo ---------- */
function tModelo(modo){
  const G=P.GANADO;let h='';
  for(const canal of modo==='ganado'?['foto','cinta']:['foto']){const m=P.modeloV(modo,canal);
    h+=caja(canal==='cinta'?'Calibración con cinta':'Calibración','',tiles([[nf(m.k,3),'k'],[nf(m.b,3),'b'],[m.n,'pesos reales'],[m.ni??0,'de un animal'],[m.ng??0,'de grupo'],['±'+pc(m.err),'error típico']]));}
  if(modo==='ganado'){
    h+=caja('Constantes del modelo',`v${P.VERSION_MODELO}`,`<p class="pc-exp-f" data-no-tr>ln P = a + e + ${nf(G.beta,2)} ln A + ${nf(G.gamma,2)} ln(F / (${nf(G.r0,2)} A))</p>
      ${tabla(['Tipo','a'],P.TIPOS_G.map(([k,t])=>[t,nf(G.a[k],3)]))}${tabla(['Etapa','e'],[['Ternero',nf(G.etapas.ternero,2)],['En crecimiento',nf(G.etapas.crec,2)],['Adulto o terminado',nf(G.etapas.adulto,2)]])}
      ${tabla(['',''],[['Fondo / alzada (límites)',`${nf(G.cdwh[0],2)} – ${nf(G.cdwh[1],2)}`],['Alzada de atrás (grupa / cruz)',nf(G.grupa,2)],['Animales del ajuste',nf(G.n)],['Error de validación',pc(G.mape)]])}
      <p class="pc-exp-f" data-no-tr>ln P = ${nf(G.cinta.a,4)} + t + e + ${nf(G.cinta.wh,4)} ln A + ${nf(G.cinta.hg,4)} ln C</p><p class="hint" style="margin:0">Con cinta. C: perímetro del pecho.</p>`);
    h+=caja('Ajuste con tus datos','',sugerido());
    h+=caja('Calculadora','',`<form class="lab-calc" data-labcalc onsubmit="return false">
      <div class="lab-calc-f">${q('Alzada',inp('wh','130',{unit:'cm'}))}${q('Fondo de pecho',inp('cd','68',{unit:'cm'}))}${q('Perímetro con cinta',inp('hg','',{unit:'cm',ph:'—'}))}</div>
      <div class="lab-calc-f">${q('Tipo',`<select class="in" name="tipo">${P.TIPOS_G.map(([k,t])=>`<option value="${k}">${t}</option>`).join('')}</select>`)}${q('Etapa',`<select class="in" name="etapa"><option value="adulto">Adulto o terminado</option><option value="crec">En crecimiento</option><option value="ternero">Ternero</option></select>`)}</div>
      <div class="lab-calc-r" data-calcr></div></form>`);
  }else{
    h+=caja('Constantes del modelo',`v${P.VERSION_MODELO}`,`<p class="hint" style="margin:0"><span>Modelo de ANSUR II con</span> <span data-no-tr>${nf(P.ANSUR.n)}</span> <span>personas; error de validación</span> <span data-no-tr>${pc(P.ANSUR.mape)}</span>.</p>`);
  }
  const {F}=etiquetasLab(modo);
  if(F.length)h+=caja('Pesos reales usados',F.length,tabla(['','fecha','base','real','error'],F.slice(-60).reverse().map(f=>[NOM_SRC[f.src]||f.src,f.x?ffc(f.x.f):'',nf(W(f.L),1),nf(W(f.kg),1),pcs(f.hoy)])));
  return h;
}
// β y γ con los datos de la finca (no se aplican: es para ajustar el modelo con el resultado). Mínimos cuadrados en
// logaritmos con el tipo y la etapa de cada medición; dos variantes: β y γ libres, y β fijo (más estable con pocos datos)
function sugerido(){
  const {F}=etiquetasLab('ganado'),G=P.GANADO,D=F.filter(f=>f.canal==='foto'&&f.x&&f.x.dims&&f.x.dims.WH>0&&f.x.dims.CD>0);
  if(D.length<12)return `<p class="hint" style="margin:0"><span>Con 12 pesos reales de animales medidos (hay</span> <span data-no-tr>${D.length}</span><span>) se calcula aquí la alzada (β) y el fondo de pecho (γ) que mejor van con tu ganado, con su error.</span></p>`;
  const fila=f=>{const x=f.x,qq=Math.min(G.cdwh[1],Math.max(G.cdwh[0],x.dims.CD/x.dims.WH));return {y:Math.log(f.kg)-G.a[x.tipo||'cruce']-G.etapas[P.etapaDe(x)],a:Math.log(x.dims.WH),b:Math.log(qq/G.r0)};};
  const Z=D.map(fila);
  const mco=(Z,libre)=>{if(libre){let S=[[0,0,0],[0,0,0],[0,0,0]],t=[0,0,0];for(const z of Z){const v=[1,z.a,z.b];for(let i=0;i<3;i++){t[i]+=v[i]*z.y;for(let j=0;j<3;j++)S[i][j]+=v[i]*v[j];}}return resolver(S,t);}
    let S=[[0,0],[0,0]],t=[0,0];for(const z of Z){const v=[1,z.b],y=z.y-G.beta*z.a;for(let i=0;i<2;i++){t[i]+=v[i]*y;for(let j=0;j<2;j++)S[i][j]+=v[i]*v[j];}}const c=resolver(S,t);return c?[c[0],G.beta,c[1]]:null;};
  const looE=libre=>prom(Z.map((z,i)=>{const c=mco(Z.filter((_,j)=>j!==i),libre);return c?Math.abs(Math.exp(c[0]+c[1]*z.a+c[2]*z.b-z.y)-1):NaN;}).filter(esNum));
  const c1=mco(Z,true),c2=mco(Z,false),hoy=prom(D.map(f=>Math.abs(f.e)));
  return tabla(['','β','γ','error (validación cruzada)'],[['Modelo de hoy (calibrado)',nf(G.beta,2),nf(G.gamma,2),pc(hoy)],...(c2?[['β fijo, γ de tu finca',nf(c2[1],2),nf(c2[2],2),pc(looE(false))]]:[]),...(c1?[['β y γ de tu finca',nf(c1[1],2),nf(c1[2],2),pc(looE(true))]]:[])])
    +`<p class="hint" style="margin:0">No se aplica solo: exporta el laboratorio y envíalo para ajustar el modelo con estos datos.</p>`;
}
function resolver(A,b){const n=b.length,M=A.map((r,i)=>[...r,b[i]]);for(let c=0;c<n;c++){let p=c;for(let r=c+1;r<n;r++)if(Math.abs(M[r][c])>Math.abs(M[p][c]))p=r;if(Math.abs(M[p][c])<1e-12)return null;[M[c],M[p]]=[M[p],M[c]];
  for(let r=0;r<n;r++)if(r!==c){const f=M[r][c]/M[c][c];for(let k=c;k<=n;k++)M[r][k]-=f*M[c][k];}}return M.map((r,i)=>r[n]/r[i]);}
function calcular(f){
  const o=f.querySelector('[data-calcr]');if(!o)return;const wh=num(fv(f,'wh')),cd=num(fv(f,'cd')),hg=num(fv(f,'hg')),t=fv(f,'tipo'),e=fv(f,'etapa');
  const p=P.predDims(wh,cd,t,e);if(!esNum(p)){o.innerHTML='';return;}const m=P.modeloV('ganado'),kg=m.k*Math.pow(p,m.b),s=P.sensibilidad({modo:'ganado',kg,dims:{WH:wh,CD:cd},alto:PCc().refAlto||170});
  const pc2=hg>0?P.pesoCinta(wh,hg,{g:t,etapa:e}):NaN,mc=P.modeloV('ganado','cinta');
  o.innerHTML=`<div class="pc-kv"><span>Peso del modelo</span><b data-no-tr>${wtxt(p,1)}</b></div><div class="pc-kv"><span>Calibrado</span><b data-no-tr>${wtxt(kg,1)}</b></div>
    ${esNum(pc2)?`<div class="pc-kv"><span>Con cinta (calibrado)</span><b data-no-tr>${wtxt(mc.k*Math.pow(pc2,mc.b),1)}</b></div>`:''}
    ${s?`<div class="pc-kv"><span>1 cm más de alzada</span><b data-no-tr>+${wtxt(s.WH,1)}</b></div><div class="pc-kv"><span>1 cm más de fondo de pecho</span><b data-no-tr>+${wtxt(s.CD,1)}</b></div>`:''}`;
}
document.addEventListener('input',e=>{const f=e.target&&e.target.closest&&e.target.closest('[data-labcalc]');if(f)calcular(f);});
document.addEventListener('change',e=>{const f=e.target&&e.target.closest&&e.target.closest('[data-labcalc]');if(f)calcular(f);});

/* ---------- Registro ---------- */
const FILTROS=[['todas','Todas'],['real','Con peso real'],['arete','Con arete'],['datos','Con datos del animal'],['sesion','Esta sesión']];
function tRegistro(modo){
  const L=LAB(),R=P.REG().filter(x=>x.modo===modo),ses=PCc().labSes,ids=new Set(etiquetasLab(modo).F.map(f=>f.id));
  const fl={todas:()=>true,real:x=>x.real>0||ids.has(x.id),arete:x=>!!x.aid,datos:x=>!!x.cond,sesion:x=>ses&&x.ses===ses.n}[L.f]||(()=>true),V=R.filter(fl);
  return `<section class="sec">${secH('Registro',V.length)}<div class="card pad lab-c">
    <div class="chips wrap">${FILTROS.filter(f=>f[0]!=='sesion'||ses).map(([k,t])=>`<button type="button" class="chip" data-act="labFiltro" data-f="${k}" aria-pressed="${L.f===k}">${t}</button>`).join('')}</div>
    ${V.length?`<div class="rows">${V.slice().reverse().slice(0,150).map(filaReg).join('')}</div>`:vacio('Ninguna medición con este filtro.')}
    </div></section>
    <section class="sec">${secH('Exportar')}<div class="card pad lab-c">
    <p class="hint" style="margin:0">CSV: una fila por medición, con las medidas, la calidad de las tomas, las condiciones del animal y el peso real. Laboratorio completo: todo el registro, lo de cada foto (siluetas, puntos, medidas), los pesos reales con su fuente y el estado del modelo, para ajustarlo fuera del teléfono. Para análisis: además las fotos originales de las últimas 10.</p>
    <div class="pc-acts lab-acts"><button type="button" class="btn" data-act="pcRegCsv">Exportar CSV</button><button type="button" class="btn pri" data-act="labExportar">Exportar el laboratorio completo</button><button type="button" class="btn" data-act="pcRegAnalisis">Exportar para análisis</button><button type="button" class="btn danger" data-act="pcRegLimpiar">Borrar registro</button></div></div></section>`;
}
ACTS.labFiltro=el=>{LAB().f=el.dataset.f;render();};
ACTS.labSesIni=()=>{const n=`Prueba ${ffc(hoy())}`;openSheet(shHead('Sesión de prueba','Laboratorio')+formWrap('labSes',`<p class="hint">Las mediciones que hagas quedan marcadas con este nombre hasta que termines la sesión.</p>${q('Nombre',inp('n',n,{mode:'text'}))}`,foot('Empezar')));};
SAVE.labSes=f=>{const n=String(fv(f,'n')||'').trim().slice(0,40);if(!n)return ferr(f,'Escribe un nombre.');P.guardarPC({labSes:{n,ts:Date.now()}});closeSheet();toast('Sesión de prueba en curso');};
ACTS.labSesFin=()=>{P.guardarPC({labSes:null});toast('Sesión terminada');};
ACTS.labMismo=()=>{P.guardarPC({labMismo:!PCc().labMismo});};

/* ======================= una medición ======================= */
const fotoN=x=>Math.max(2,x.camaras?x.camaras.length:2);
async function leerDbg(id){if(!window.Fotos)return null;try{const u=await Fotos.url(`pcd-${id}`);if(!u)return null;return await (await fetch(u)).json();}catch(e){return null;}}
ACTS.pcRegVer=el=>{
  const x=buscar(el.dataset.id);if(!x)return;const er=x.real>0?(x.kg-x.real)/x.real:null,pa=x.partes||{};
  const fila=(t,v)=>v==null||v===''?'':`<div class="pc-kv"><span>${t}</span><b data-no-tr>${v}</b></div>`;
  // (con palabras que sí se traducen)
  const filaT=(t,v)=>v==null||v===''?'':`<div class="pc-kv"><span>${t}</span><b>${v}</b></div>`;
  const lab=labEn(x),s=P.sensibilidad(x),ahora=ahoraDe(x),C=condDe(x.modo),cd=x.cond||{};
  openSheet(shHead('Medición',`${ffc(x.f)} ${hora(x.ts)}`)+`<div class="sh-body pc-det">
    <div class="pc-caps pc-det-fotos">${Array.from({length:fotoN(x)},(_,i)=>`<figure><img data-foto="pc-${x.id}-${i}" alt="" hidden></figure>`).join('')}</div>
    <div class="lab-dbg" data-dbg="${x.id}"></div>
    ${fila('Peso estimado',wtxt(x.kg,1))}${fila('Peso de báscula',x.real>0?wtxt(x.real,1):'—')}${er!=null?fila('Error',pcs(er)):''}${lab&&!(x.real>0)?filaT('Peso real',`<span>${NOM_SRC[lab.src]||lab.src}</span> <span data-no-tr>${wtxt(lab.kg,1)} (${pcs(x.kg/lab.kg-1)})</span>`):''}${fila('Rango',esNum(x.err)?'±'+pc(x.err):'')}
    ${ahora&&Math.abs(ahora/x.kg-1)>.001?fila('Con el modelo de hoy',wtxt(ahora,1)):''}
    ${areteDe(x)?fila('Arete',esc(areteDe(x))):''}${x.ses?fila('Sesión de prueba',esc(x.ses)):''}${x.HG>0?fila('Perímetro con cinta',`${nf(x.HG)} cm`)+fila('Peso solo con foto (sin calibrar)',kgs(x.predFoto)):''}
    ${s?`<h3 class="lab-h3">Sensibilidad (1 cm de error)</h3>${fila('Alzada',`±${wtxt(s.WH,1)}`)}${fila('Fondo de pecho',s.CD?`±${wtxt(s.CD,1)}`:'0')}${fila('Estatura de referencia',`±${wtxt(s.ref,1)}`)}`:''}
    <h3 class="lab-h3">Datos del animal</h3>
    ${Object.keys(cd).length?C.map(([k,t,L])=>cd[k]!=null&&cd[k]!==''?filaT(t,txtOpc(L,cd[k])):'').join('')+(cd.edad>0?fila(x.modo==='persona'?'Edad (años)':'Edad (meses)',nf(cd.edad)):'')+(cd.dist>0?fila('Distancia al animal',`${nf(cd.dist,1)} m`):'')+(cd.cinta>0?fila('Perímetro con cinta (referencia)',`${nf(cd.cinta)} cm`):'')+(cd.notas?`<p class="lab-notas" data-no-tr>${esc(cd.notas)}</p>`:'')
      :'<p class="hint" style="margin:0">Sin datos todavía.</p>'}
    <button type="button" class="btn full" data-act="labCond" data-id="${x.id}">${Object.keys(cd).length?'Cambiar los datos del animal':'Anotar los datos del animal'}</button>
    <h3 class="lab-h3">Modelo</h3>
    ${fila('Volumen',`${n2(x.L,1)} L`)}${x.modo==='persona'?fila('Por partes',`${n2(pa.cabeza,1)} / ${n2(pa.tronco,1)} / ${n2(pa.brazos,1)} / ${n2(pa.piernas,1)} L`):''}
    ${x.modo==='persona'&&x.dims?fila('Estatura',`${x.alto} cm`)+fila('Peso del modelo (sin calibrar)',wtxt(x.pred,1))+fila('Hombros',`${n2(x.dims.bid,1)} cm`)+fila('Pecho (ancho × fondo)',`${n2(x.dims.cb,1)} × ${n2(x.dims.cd,1)} cm`)+fila('Cintura (ancho × fondo)',`${n2(x.dims.wb,1)} × ${n2(x.dims.wd,1)} cm`)+fila('Cadera · glúteos',`${n2(x.dims.hb,1)} · ${n2(x.dims.bd,1)} cm`)+fila('Contorno del muslo',`${n2(x.dims.th,1)} cm`)+(x.dims.cf?fila('Pantorrilla · muslo sobre la rodilla · cuello',`${n2(x.dims.cf,1)} · ${n2(x.dims.lt,1)} · ${n2(x.dims.ne,1)} cm`)+filaT('Estimadas con las demás medidas',x.estimadas&&x.estimadas.length?x.estimadas.map(k=>`<span>${({cf:'pantorrilla',ne:'cuello',lt:'muslo sobre la rodilla'})[k]||k}</span>`).join(', '):'ninguna'):'')+fila('Puntos del cuerpo',x.puntos?'sí':'no')+fila('Giro de la toma de perfil',esNum(x.giro)?`${nf(x.giro,1)}°`:'')+fila('Silueta incompleta (escala con los puntos)',x.incompleta==null?'':x.incompleta?'sí':'no')+fila('Medidas ajustadas al límite',x.topes&&x.topes.length?x.topes.join(', '):'ninguna')+fila('IMC',n2(x.imc,1))+fila('Ropa',x.ropa&&P.ROPA[x.ropa]?P.ROPA[x.ropa].t:'')
      :x.modo==='persona'?fila('Estatura',`${x.alto} cm`)+fila('Hombros',`${n2(x.anchoF,1)} cm`)+fila('Pecho de fondo',`${n2(x.prof,1)} cm`)+fila('Fondo / ancho del pecho',n2(x.fondoAncho,2))+fila('Brazos',x.brazosPegados?'pegados (estimados)':'separados')+fila('Filas recortadas (tope anatómico)',esNum(x.topadas)?pc(x.topadas):'')+fila('IMC',n2(x.imc,1))+fila('Ropa',x.ropa&&P.ROPA[x.ropa]?P.ROPA[x.ropa].t:'')
      :(x.dims&&x.dims.WH?fila('Peso del modelo (sin calibrar)',wtxt(x.pred,1))+(x.tipo&&P.tipoTxt?filaT('Tipo de animal',P.tipoTxt(x)):'')+fila('Alzada (de costado · por detrás)',`${n2(x.dims.WH,1)} cm (${n2(x.dims.WHs,1)} · ${n2(x.dims.WHr,1)})`)+fila('Fondo de pecho',`${n2(x.dims.CD,1)} cm`)+fila('Fondo / alzada',n2(x.dims.CD/x.dims.WH,3))+fila('Largo (hombro a cola)',`${n2(x.dims.L,1)} cm`)+fila('Fondo medio del tronco',`${n2(x.dims.FM,1)} cm`)+fila('Ancho / alto por detrás',n2(x.dims.RWH,3))+(x.camaras&&x.camaras.length>2?fila('Cámaras',x.camaras.length)+fila('Fondo / alzada del otro costado',n2(x.dims.qOtro,3))+fila('Ancho / largo desde arriba',n2(x.dims.AWL,3)):'')+fila('Medidas ajustadas al límite',x.topes&&x.topes.length?x.topes.join(', '):'ninguna'):'')+fila('Largo',`${n2(x.largo,1)} cm`)+fila('Alto',`${n2(x.altoAnimal,1)} cm`)+fila('Ancho',`${n2(x.ancho,1)} cm`)+fila('Persona de referencia',`${x.alto} cm`)}
    ${fila('Modelo',`v${x.v} · k ${(x.modo==='persona'&&x.v>=3)||x.v>=6?n2(x.k,3):`${n2(W(x.k),3)} ${UW()}/L`} · b ${n2(x.b,2)} · ${x.ncal||0} cal.`)}${fila('Dispersión entre fotos',`${pc(x.cv)} · ${x.comb||1} comb.`)}
    ${fila('Seguridad',esNum(x.score)?`${Math.round(x.score*100)} %`:'')}${fila('Fuente',x.fuente==='preciso'?'RF-DETR':'LR-ASPP')}${fila('Toma manual',x.manual?'sí':'no')}
    ${x.dq?`<h3 class="lab-h3">Calidad de la toma</h3>`+FACT.map(([k,t,fn,fmt])=>{const v=fn(x);return esNum(v)?fila(t,fmt(v)):'';}).join('')+fila('Fotos medidas',x.dq.tomas):''}
    <h3 class="lab-h3">Rendimiento</h3>
    ${fila('Cuadros/s',n2(x.fps,1))}${fila('Modelo rápido',esNum(x.msR)?`${nf(x.msR)} ms`:'')}${fila('Modelo preciso',esNum(x.msP)?`${nf(x.msP)} ms`:'')}${fila('Modelo de pose',esNum(x.msPose)?`${nf(x.msPose)} ms`:'')}${fila('Duración',esNum(x.seg)?`${nf(x.seg,1)} s`:'')}${fila('Motor',x.motor)}
    <button type="button" class="lnk" data-act="labUna" data-id="${x.id}">Exportar esta medición</button>
    </div><div class="sh-foot"><button type="button" class="btn danger" data-act="pcRegBorrar" data-id="${x.id}" style="flex:1">Borrar</button><button type="button" class="btn pri" data-act="pcRegReal" data-id="${x.id}" style="flex:1.6">${x.real>0?'Cambiar peso de báscula':'Anotar peso de báscula'}</button></div>`);
  pintarDbg(x);
};
// el peso real de una medición (de cualquier fuente) y su peso con el modelo de hoy
function labEn(x){try{return etiquetasLab(x.modo).F.find(f=>f.id===x.id)||null;}catch(e){return null;}}
function ahoraDe(x){const base=P.baseDe(x);if(!(base>0))return null;let b=base;
  if(x.modo==='ganado'&&x.dims&&x.dims.WH>0&&!(x.HG>0)){const p=P.predDims(x.dims.WH,x.dims.CD,x.tipo,P.etapaDe(x));if(esNum(p))b=p;}
  const m=P.modeloV(x.modo,P.canalDe(x)),fa=x.aid?P.factorAnimal(x.aid,P.canalDe(x)):{f:1};return m.k*Math.pow(b,m.b)*fa.f;}

/* lo que hizo el modelo con cada foto: dibujado sobre la foto original (silueta, persona de referencia, puntos con su
   confianza y dónde se midió), y los números de cada foto y de cada par */
const LAST={x:null,D:null,a:0,t:0};
async function pintarDbg(x){
  const el=$('#sheet .lab-dbg');if(!el)return;const D=await leerDbg(x.id);if(!$('#sheet .lab-dbg'))return;
  if(!D){el.innerHTML=`<p class="hint" style="margin:0">Sin los datos de cada foto: se guardan para las últimas 60 mediciones, desde esta versión.</p>`;return;}
  Object.assign(LAST,{x,D,a:0,t:0});
  const fila=(t,v)=>v==null||v===''?'':`<div class="pc-kv"><span>${t}</span><b data-no-tr>${v}</b></div>`;
  const rolTxt=r=>({costado:'De costado',atras:'Por detrás',otro:'Otro costado',arriba:'Desde arriba',frente:'De frente'})[r]||r;
  const tomas=D.angs.flatMap((A,ia)=>A.tomas.map((T,it)=>({A,T,ia,it})));
  const kpU=P.KP_USA[D.modo]||[];
  el.innerHTML=`<div class="lab-ov"><canvas class="lab-cv"></canvas></div>
    <div class="chips wrap lab-tomas">${tomas.map(z=>`<button type="button" class="chip" data-act="labToma" data-a="${z.ia}" data-t="${z.it}" aria-pressed="${!z.ia&&!z.it}"><span>${rolTxt(z.A.rol)}</span> <span data-no-tr>${z.it+1}</span></button>`).join('')}</div>
    <p class="hint lab-ley"><i class="k-s"></i><span>silueta</span> <i class="k-r"></i><span>persona de referencia</span> <i class="k-m"></i><span>medidas</span> <i class="k-p"></i><span>puntos (verde: seguro; rojo: dudoso)</span></p>
    <h3 class="lab-h3">Cada foto</h3>
    ${tabla(['','cm/px','alto','puntos','nitidez','brillo','medida'],tomas.map(({A,T,it})=>[`${rolTxt(A.rol)} ${it+1}`,T.cmpx?nf(T.cmpx,3):'—',pc(T.tam),T.kp?nf(prom(kpU.map(i=>T.kp[3*i+2]).filter(esNum)),2):'—',T.luz?nf(T.luz.nitidez):'—',T.luz?pc(T.luz.brillo):'—',
      T.M?`A ${nf(T.M.WH,1)} · F ${nf(T.M.CD,1)} · L ${nf(T.M.L,1)}`:esNum(T.WHr)?`A ${nf(T.WHr,1)} · ${nf(T.RWH,2)}`:esNum(T.q)?`F/A ${nf(T.q,3)}`:esNum(T.AWL)?`${nf(T.AWL,3)}`:'']))}
    ${D.pares.length?`<h3 class="lab-h3">Cada par de fotos</h3>`+tabla(D.modo==='ganado'?['','alzada','fondo','F/A','peso']:['','volumen','giro','peso'],D.pares.map(p=>D.modo==='ganado'?[`${p.a+1} × ${p.b+1}`,`${nf(p.WH,1)} (${nf(p.WHs,1)} · ${esNum(p.WHr)?nf(p.WHr,1):'—'})`,nf(p.CD,1),`${nf(p.q,3)}${p.tope?' *':''}`,kgs(p.pred)]:[`${p.a+1} × ${p.b+1}`,`${nf(p.L,1)} L`,esNum(p.giro)?`${nf(p.giro,1)}°`:'—',kgs(p.pred)]))+(D.pares.some(p=>p.tope)?'<p class="hint" style="margin:0">* fuera de los límites del modelo: se ajustó.</p>':''):''}
    <h3 class="lab-h3">Inclinación del teléfono</h3>${D.angs.map(A=>A.ori?fila(rolTxt(A.rol),`${nf(A.ori.elev,1)}° · ${nf(A.ori.horiz,1)}°`):'').join('')||'<p class="hint" style="margin:0">Sin datos del acelerómetro.</p>'}
    ${D.modo==='ganado'?`<details class="lab-kp"><summary>Puntos del animal (de costado)</summary><div data-kp></div></details>`:''}`;
  dibujarToma(0,0);
}
ACTS.labToma=el=>{$$('#sheet .lab-tomas .chip').forEach(b=>b.setAttribute('aria-pressed',String(b===el)));dibujarToma(+el.dataset.a,+el.dataset.t);};
async function dibujarToma(ia,it){
  const {x,D}=LAST,cv=$('#sheet .lab-cv');if(!x||!D||!cv)return;const A=D.angs[ia],T=A&&A.tomas[it];if(!T)return;
  const u=(await Fotos.url(`pcr-${x.id}-${ia}-${it}`))||(await Fotos.url(`pc-${x.id}-${ia}`));
  const Wd=T.W||640,Hd=T.H||480;cv.width=Wd;cv.height=Hd;const g=cv.getContext('2d');g.fillStyle='#222';g.fillRect(0,0,Wd,Hd);
  if(u){try{const im=new Image();im.src=u;await im.decode();g.drawImage(im,0,0,Wd,Hd);}catch(e){}}
  const lw=Math.max(2,Wd/320);g.lineWidth=lw;
  const px=(s,cx,cy)=>[s.Z.x+cx*s.cx,s.Z.y+cy*s.cy];
  const contorno=(s,col)=>{if(!s||!s.cont||!s.Z)return;g.strokeStyle=col;g.beginPath();for(let i=0;i<s.cont.length;i+=2){const [a,b]=px(s,s.cont[i],s.cont[i+1]);i?g.lineTo(a,b):g.moveTo(a,b);}g.closePath();g.stroke();};
  contorno(T.sil,'#2ED3E6');contorno(T.ref,'#F5C518');
  if(T.M&&T.M.geo&&T.sil&&T.sil.Z){const s=T.sil,G=T.M.geo;g.strokeStyle='#FF3EA5';g.lineWidth=lw*1.6;const ln=(a,b)=>{g.beginPath();g.moveTo(...a);g.lineTo(...b);g.stroke();};
    ln(px(s,G.cruz[0],G.cruz[1]),px(s,G.cruz[0],G.suelo));ln(px(s,G.pecho[0],G.pecho[1]),px(s,G.pecho[0],G.pecho[2]));ln(px(s,G.hombro[0],G.hombro[1]),px(s,G.cola[0],G.hombro[1]));g.lineWidth=lw;}
  if(T.kp){for(let i=0;i<T.kp.length;i+=3){const c=T.kp[i+2];if(!(c>.05))continue;g.fillStyle=c>=.6?'#2BD46B':c>=.3?'#F5A623':'#E5484D';g.beginPath();g.arc(T.kp[i],T.kp[i+1],lw*2.2,0,7);g.fill();}}
  const k=$('#sheet [data-kp]');if(k&&A.rol==='costado'&&T.kp)k.innerHTML=P.KP_ANIMAL.map((n,i)=>`<div class="pc-kv"><span>${n}</span><b data-no-tr class="${T.kp[3*i+2]>=.6?'ok':T.kp[3*i+2]>=.3?'':'mal'}">${nf(T.kp[3*i+2],2)}</b></div>`).join('');
}

/* ---------- los datos del animal (condiciones de la medición) ---------- */
let COND=null,VOLVER=false;
function formCond(x,volver){COND=x.id;VOLVER=!!volver;const c=x.cond||{},C=condDe(x.modo),gan=x.modo==='ganado';
  openSheet(shHead('Datos del animal','Laboratorio')+formWrap('labCond',`<p class="hint">Todo es opcional. Con estos datos el laboratorio ve qué condiciones cambian el error del modelo.</p>
    ${q('Peso de báscula',inp('kg',x.real>0?nf(W(x.real),1):'',{unit:UW(),ph:nf(W(x.kg))}),'El de la báscula, ese mismo día.')}
    ${C.map(([k,t,L])=>q(t,opts(k,L.map(([v,tt])=>({v,t:tt})),c[k]??'',{allowNone:true}))).join('')}
    ${q(gan?'Edad (meses)':'Edad (años)',inp('edad',c.edad>0?String(c.edad):'',{ph:'—'}))}
    ${gan?q('Perímetro con cinta (referencia)',inp('cinta',c.cinta>0?String(c.cinta):'',{unit:'cm',ph:'—'}),'Solo para comparar en el laboratorio: no cambia este peso.')+q('Distancia del teléfono al animal',inp('dist',c.dist>0?nf(c.dist,1):'',{unit:'m',ph:'—'})):''}
    ${q('Notas',`<textarea class="in" name="notas" rows="3" maxlength="400">${esc(c.notas||'')}</textarea>`)}`,foot('Guardar')));}
ACTS.labCond=el=>{const x=buscar(el.dataset.id);if(x)formCond(x,false);};
SAVE.labCond=f=>{const x=buscar(COND);if(!x)return closeSheet();const C=condDe(x.modo),c={};
  for(const [k] of C){const v=fv(f,k);if(v!=='')c[k]=v;}
  const n=(k,lo,hi)=>{const v=num(fv(f,k));if(v>=lo&&v<=hi)c[k]=Math.round(v*10)/10;};n('edad',0,600);n('cinta',60,320);n('dist',.5,30);
  const notas=String(fv(f,'notas')||'').trim().slice(0,400);if(notas)c.notas=notas;
  const cambios={cond:Object.keys(c).length?c:null};const kv=fv(f,'kg');
  if(kv!==''){const v=toKg(num(kv)),[lo,hi]=x.modo==='persona'?[15,250]:[40,1300];if(!(v>=lo&&v<=hi))return ferr(f,'Escribe el peso de la báscula.');cambios.real=Math.round(v*10)/10;}
  P.actualizarReg(x.id,cambios);COND=null;closeSheet();toast('Datos guardados');if(VOLVER&&P.verResultado)setTimeout(P.verResultado,80);};
// desde el resultado de una medición
ACTS.pcLabDatos=()=>{const r=P.ultimo&&P.ultimo();if(!r||!r.id)return;const x=buscar(r.id);if(x)formCond(x,true);};

/* ---------- peso de báscula, borrar ---------- */
let REAL=null;
ACTS.pcRegReal=el=>{const x=buscar(el.dataset.id);if(!x)return;REAL=x.id;
  openSheet(shHead('Peso de báscula','Laboratorio')+formWrap('pcReg',`<p class="hint">La cámara estimó <b>${wtxt(x.kg,1)}</b>. Escribe lo que marcó la báscula ese día; también sirve para calibrar.</p>
    ${q('Peso de báscula',inp('kg',x.real>0?nf(W(x.real),1):'',{unit:UW(),xl:true,ph:nf(W(x.kg))}))}`,foot('Guardar')));};
SAVE.pcReg=f=>{const x=buscar(REAL);if(!x)return closeSheet();const v=toKg(num(fv(f,'kg')));
  const [lo,hi]=x.modo==='persona'?[15,250]:[40,1300];if(!(v>=lo&&v<=hi))return ferr(f,'Escribe el peso de la báscula.');
  P.actualizarReg(x.id,{real:Math.round(v*10)/10});REAL=null;closeSheet();toast('Peso de báscula guardado');};
const borrarFotos=id=>{if(!window.Fotos)return;Fotos.borrar(`pcd-${id}`);for(let i=0;i<4;i++){Fotos.borrar(`pc-${id}-${i}`);for(let j=0;j<3;j++)Fotos.borrar(`pcr-${id}-${i}-${j}`);}};
ACTS.pcRegBorrar=el=>{const id=el.dataset.id;confirmar('¿Borrar esta medición?','Sale del registro y de la calibración.','Borrar',()=>{P.guardarPC({reg:P.REG().filter(x=>x.id!==id)});borrarFotos(id);toast('Medición borrada');});};
ACTS.pcRegLimpiar=()=>{const modo=UI.pc.modo;confirmar('¿Borrar el registro?','Se borran todas las mediciones de este modo, también su peso de báscula (la calibración).','Borrar',()=>{const fu=P.REG().filter(x=>x.modo===modo);P.guardarPC({reg:P.REG().filter(x=>x.modo!==modo)});fu.forEach(x=>borrarFotos(x.id));toast('Registro borrado');});};

/* ---------- exportar ---------- */
// todo el registro en CSV (una fila por medición; el peso en kg)
const DQ=['brillo','contraste','nitidez','quemado','oscuro','tam','tamRef','kp','kpMin','cvWH','cvCD','cvPred','elev','horiz','LWH','tomas'];
const CK=['sexo','cc','llenado','prenez','pelo','postura','suelo','fondo','edad','dist','cinta','notas'];
const COLS=['id','fecha','hora','modo','version','litros','kg_estimado','kg_bascula','error_pct','rango_pct','cv_fotos_pct','combinaciones','k','b','calibraciones',
  'estatura_ref_cm','ropa','kg_modelo','hombros_cm','pecho_ancho_cm','pecho_fondo_cm','cintura_ancho_cm','cintura_fondo_cm','cadera_ancho_cm','gluteos_fondo_cm','muslo_contorno_cm','pantorrilla_cm','muslo_bajo_cm','cuello_cm','estimadas',
  'puntos','silueta_limpiada_pct','giro_perfil_grados','silueta_incompleta','medidas_al_limite','fondo_ancho','brazos_pegados','filas_recortadas_pct','imc','largo_cm','alto_cm','ancho_cm','alzada_cm','alzada_costado_cm','alzada_atras_cm','fondo_pecho_cm','largo_tronco_cm','fondo_medio_cm','ancho_alto_atras','tipo_animal','etapa','camaras','fondo_alzada_otro_costado','ancho_largo_arriba','ancho_arriba_cm','cabeza_l','tronco_l','brazos_l','piernas_l','seguridad','fuente','manual','fps','ms_rapido','ms_preciso','segundos','motor',
  'lote','animal','arete','sesion','perimetro_cinta_cm','kg_modelo_foto','kg_real','fuente_real','kg_modelo_hoy',...DQ.map(k=>'toma_'+k),...CK.map(k=>'animal_'+k),'ms_pose'];
function csv(){
  const q=v=>v==null||(typeof v==='number'&&!isFinite(v))?'':typeof v==='number'?String(Math.round(v*1000)/1000):/[",\n]/.test(String(v))?`"${String(v).replace(/"/g,'""')}"`:String(v);
  const lab={};for(const m of ['persona','ganado'])try{for(const f of etiquetasLab(m).F)lab[f.id]=f;}catch(e){}
  const L=P.REG().map(x=>{const pa=x.partes||{},er=x.real>0?(x.kg-x.real)/x.real*100:null,d=x.dims||{},dq=x.dq||{},c=x.cond||{},l=lab[x.id];
    return [x.id,x.f,hora(x.ts),x.modo,x.v,x.L,x.kg,x.real,er,esNum(x.err)?x.err*100:null,esNum(x.cv)?x.cv*100:null,x.comb,x.k,x.b,x.ncal,x.alto,x.ropa,x.pred,
      x.dims?d.bid:x.anchoF,d.cb,x.dims?d.cd:x.prof,d.wb,d.wd,d.hb,d.bd,d.th,d.cf,d.lt,d.ne,x.estimadas?x.estimadas.join(' '):null,x.puntos==null?null:x.puntos?1:0,esNum(x.limpia)?x.limpia*100:null,x.giro,x.incompleta==null?null:x.incompleta?1:0,x.topes?x.topes.join(' '):null,x.fondoAncho,x.modo==='persona'?(x.brazosPegados?1:0):null,esNum(x.topadas)?x.topadas*100:null,x.imc,
      x.largo,x.altoAnimal,x.ancho,d.WH,d.WHs,d.WHr,d.CD,d.L,d.FM,d.RWH,x.tipo,x.etapa||(x.joven?'ternero':null),x.camaras?x.camaras.join(' '):null,d.qOtro,d.AWL,d.AW,pa.cabeza,pa.tronco,pa.brazos,pa.piernas,x.score,x.fuente,x.manual?1:0,x.fps,x.msR,x.msP,x.seg,x.motor,
      x.lote,x.aid,areteDe(x)||null,x.ses,x.HG,x.predFoto,l?l.kg:null,l?l.src:null,ahoraDe(x),...DQ.map(k=>dq[k]),...CK.map(k=>c[k]),x.msPose].map(q).join(',');});
  return [COLS.join(',')].concat(L).join('\n')+'\n';
}
ACTS.pcRegCsv=async()=>{const b=new TextEncoder().encode(csv());await Documentos.enviar(`rumentis-labs-mediciones-${hoy()}.csv`,b.buffer,'compartir','Mediciones',  'text/csv');};
const aURL=b=>new Promise(ok=>{const r=new FileReader();r.onload=()=>ok(r.result);r.onerror=()=>ok(null);r.readAsDataURL(b);});
async function fotosDe(x,fotos){if(!window.Fotos)return;for(let i=0;i<4;i++)for(let j=0;j<3;j++){const k=`pcr-${x.id}-${i}-${j}`;try{const u=await Fotos.url(k);if(!u)continue;const b=await (await fetch(u)).blob();const d=await aURL(b);if(d)fotos[k]=d;}catch(e){}}}
/* para el análisis de Rumentis Labs: las últimas mediciones con sus fotos originales (sin dibujos) y lo de cada foto,
   en un solo archivo JSON. Con las fotos se puede repetir la medición paso a paso fuera del teléfono. */
async function analisis(modo){
  const R=P.REG().filter(x=>x.modo===modo).slice(-10),fotos={},dbg={};
  for(const x of R){await fotosDe(x,fotos);const D=await leerDbg(x.id);if(D)dbg[x.id]=D;}
  return {tipo:'rumentis-labs-analisis',v:2,version:P.VERSION_MODELO,fecha:hoy(),modo,registros:R,depuracion:dbg,fotos};
}
ACTS.pcRegAnalisis=async()=>{const modo=UI.pc.modo;toast('Preparando el archivo…');const a=await analisis(modo);
  if(!Object.keys(a.fotos).length)toast('Todavía no hay fotos originales: se guardan desde esta versión.',3500);
  const b=new TextEncoder().encode(JSON.stringify(a));await Documentos.enviar(`rumentis-labs-analisis-${hoy()}.json`,b.buffer,'compartir','Análisis','application/json');};
/* el laboratorio completo: todo el registro con lo de cada foto (las últimas 60), los pesos reales con su fuente, el
   estado del modelo y los animales y movimientos que dan pesos reales (entradas, pesajes, ventas) */
async function completo(modo){
  const R=P.REG().filter(x=>x.modo===modo),dbg={};for(const x of R){const D=await leerDbg(x.id);if(D)dbg[x.id]=D;}
  const {F,G}=etiquetasLab(modo),strip=f=>{const {x,...o}=f;return o;};
  let fin=null;if(modo==='ganado')try{const C=calc(),ids=new Set(R.map(x=>x.lote).filter(Boolean));
    fin={lotes:[...ids].map(id=>{const L=C.L[id];return L?{id,nombre:L.l.nombre,raza:L.l.raza||null,fechaIngreso:L.l.fechaIngreso||null,pesoIngreso:L.l.pesoIngreso||null,gdp:L.gdpUse||null,
      animales:(L.l.animales||[]).map(a=>({id:a.id,arete:a.arete,p0:a.p0||null,fIng:a.fIng||null,sexo:a.sexo||null,raza:a.raza||null}))}:null;}).filter(Boolean),
      movimientos:(C.items||[]).filter(i=>ids.has(i.lote)&&(i.tipo==='pesaje'||i.tipo==='venta')).map(i=>({tipo:i.tipo,f:i.f,lote:i.lote,prom:i.prom||null,cab:i.cab||null,kg:i.kg||null,pesos:i.pesos||null,metodo:i.metodo||null,animales:i.animales||null}))};}catch(e){}
  const modelos={};for(const c of modo==='ganado'?['foto','cinta']:['foto'])modelos[c]=P.modeloV(modo,c);
  return {tipo:'rumentis-labs-laboratorio',v:1,version:P.VERSION_MODELO,app:(document.querySelector('meta[name=version]')||{}).content||null,fecha:hoy(),modo,
    constantes:modo==='ganado'?P.GANADO:{ANSUR:P.ANSUR},modelos,referencia:PCc()[P.MODOS[modo].alto]||null,tipos:PCc().tipos||null,
    registros:R,depuracion:dbg,pesosReales:F.map(strip),pesosGrupo:G.map(strip),finca:fin};
}
ACTS.labExportar=async()=>{const modo=UI.pc.modo;toast('Preparando el archivo…');const a=await completo(modo);
  const b=new TextEncoder().encode(JSON.stringify(a));await Documentos.enviar(`rumentis-labs-laboratorio-${modo}-${hoy()}.json`,b.buffer,'compartir','Laboratorio','application/json');};
ACTS.labUna=async el=>{const x=buscar(el.dataset.id);if(!x)return;const fotos={};await fotosDe(x,fotos);const a={tipo:'rumentis-labs-medicion',v:1,version:P.VERSION_MODELO,fecha:hoy(),registro:x,depuracion:await leerDbg(x.id),fotos};
  const b=new TextEncoder().encode(JSON.stringify(a));await Documentos.enviar(`rumentis-labs-medicion-${x.id}.json`,b.buffer,'compartir','Medición','application/json');};
window.PcLab={html,estad,csv,analisis,completo,etiquetasLab,leerDbg,diagnostico};
})();
