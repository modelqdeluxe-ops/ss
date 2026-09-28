/* Rumentis: documentos en PDF para otros.
   - Informe para el banco: la finca, balance, resultados de 12 meses, créditos, indicadores, lotes, flujo de 90 días,
     riesgo y huella de carbono, con espacio para firmar.
   - Certificado de lote: origen, sanidad con sus retiros, pesajes, alimentación, muertes, ventas, huella y aretes,
     con un código QR que se lee con cualquier teléfono (texto, sin internet) y un código de verificación.
   Cada documento lleva un código (SHA-256 de su contenido). Los emitidos se guardan en S.config.docs y se comprueban
   en Más, Documentos, Verificar un código.
   jsPDF 2.5.2 y qrcode-generator 1.4.4 (MIT, lib/) se cargan solo al hacer el primer documento.
   En Android el PDF sale por el puente (Archivos: abrir, compartir o guardar); en un navegador, por compartir o descarga. */
(function(){
'use strict';

/* ---------- librerías ---------- */
const CARGA={};
function cargar(src){return CARGA[src]||(CARGA[src]=new Promise((ok,mal)=>{const s=document.createElement('script');s.src=src;
  s.onload=()=>ok();s.onerror=()=>{delete CARGA[src];s.remove();mal(new Error('lib'));};document.head.appendChild(s);}));}
async function libs(){
  if(!(window.jspdf&&window.jspdf.jsPDF))await cargar('lib/jspdf.umd.min.js');
  if(!window.qrcode)await cargar('lib/qrcode.js');
  if(window.qrcode&&qrcode.stringToBytesFuncs)qrcode.stringToBytes=qrcode.stringToBytesFuncs['UTF-8'];
}

/* ---------- SHA-256 (en JS: crypto.subtle no existe en file://) ---------- */
const K256=[0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
 0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
 0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
 0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2];
function utf8(s){s=String(s);if(typeof TextEncoder!=='undefined')return new TextEncoder().encode(s);
  const u=unescape(encodeURIComponent(s)),b=new Uint8Array(u.length);for(let i=0;i<u.length;i++)b[i]=u.charCodeAt(i);return b;}
function sha256(s){
  const m=utf8(s),l=m.length,n=((l+9+63)>>6)<<6,b=new Uint8Array(n);b.set(m);b[l]=0x80;
  const dv=new DataView(b.buffer);dv.setUint32(n-8,Math.floor(l/0x20000000));dv.setUint32(n-4,(l<<3)>>>0);
  const H=[0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19],w=new Uint32Array(64);
  const r=(x,k)=>(x>>>k)|(x<<(32-k));
  for(let o=0;o<n;o+=64){
    for(let i=0;i<16;i++)w[i]=dv.getUint32(o+i*4);
    for(let i=16;i<64;i++){const a=w[i-15],c=w[i-2];w[i]=(w[i-16]+(r(a,7)^r(a,18)^(a>>>3))+w[i-7]+(r(c,17)^r(c,19)^(c>>>10)))>>>0;}
    let [A,B,C,D,E,F,G,Hh]=H;
    for(let i=0;i<64;i++){const t1=(Hh+(r(E,6)^r(E,11)^r(E,25))+((E&F)^(~E&G))+K256[i]+w[i])>>>0,t2=((r(A,2)^r(A,13)^r(A,22))+((A&B)^(A&C)^(B&C)))>>>0;
      Hh=G;G=F;F=E;E=(D+t1)>>>0;D=C;C=B;B=A;A=(t1+t2)>>>0;}
    H[0]=(H[0]+A)>>>0;H[1]=(H[1]+B)>>>0;H[2]=(H[2]+C)>>>0;H[3]=(H[3]+D)>>>0;H[4]=(H[4]+E)>>>0;H[5]=(H[5]+F)>>>0;H[6]=(H[6]+G)>>>0;H[7]=(H[7]+Hh)>>>0;}
  return H.map(x=>x.toString(16).padStart(8,'0')).join('');
}
/* JSON con las llaves en orden: el mismo contenido da siempre el mismo código */
const canon=v=>v==null?'null':Array.isArray(v)?'['+v.map(canon).join(',')+']':typeof v==='object'?'{'+Object.keys(v).sort().filter(k=>v[k]!==undefined).map(k=>JSON.stringify(k)+':'+canon(v[k])).join(',')+'}':JSON.stringify(v);
const codigo=(pre,obj)=>{const h=sha256(canon(obj)).toUpperCase();return {cod:`${pre}-${h.slice(0,4)}-${h.slice(4,8)}-${h.slice(8,12)}`,h};};
const normCod=s=>String(s||'').toUpperCase().replace(/[^A-Z0-9]/g,'');

/* ---------- textos ---------- */
const desEsc=s=>s.replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/&amp;/g,'&');
// como en pantalla: kilos o libras, precio por unidad, sin etiquetas, y traducido
const plano=s=>desEsc(aUnidad(esc(String(s??''))).replace(/<br\s*\/?>/g,'\n').replace(/<[^>]+>/g,''));
const tr=s=>window.I18N?String(s).split('\n').map(I18N.t).join('\n'):s;
const REPL={'\u2013':'-','\u2014':'-','\u2212':'-','\u2010':'-','\u2011':'-','\u2018':"'",'\u2019':"'",'\u201A':"'",'\u201C':'"','\u201D':'"','\u201E':'"','\u2026':'...','\u2022':'·',
  '\u2192':'->','\u2190':'<-','\u2191':'^','\u2193':'v','\u2265':'>=','\u2264':'<=','\u2248':'~','\u2260':'<>','\u2030':' por mil','\u2605':'*','\u2713':'v','\u2714':'v','\u2717':'x',
  '\u20AC':'EUR','\u20A1':'CRC','\u20B2':'PYG','\u20A6':'NGN','\u20B1':'PHP','\u20B9':'INR','\u2082':'2','\u2083':'3','\u2084':'4','\u2080':'0','\u2081':'1',
  '\u00A0':' ','\u202F':' ','\u2009':' ','\u2007':' ','\u200A':' ','\u2002':' ','\u2003':' '};
// las fuentes estándar del PDF solo tienen Latín-1: lo demás se cambia por su equivalente o se quita
const lat=s=>String(s).replace(/[\u0000-\u0009\u000B-\u001F\u2063\u2064\u200B-\u200D\uFEFF]/g,'')
  .replace(/[^\n\x20-\x7E\xA0-\xFF]/g,c=>REPL[c]!=null?REPL[c]:c.normalize('NFD').replace(/[\u0300-\u036F]/g,'').replace(/[^\x20-\x7E\xA0-\xFF]/g,''));
// la moneda no se separa de su número al partir renglones
const pegar=s=>{const m=(S.config.moneda||'L').replace(/[.*+?^${}()|[\]\\]/g,'\\$&');return s.replace(new RegExp('(^|[^A-Za-z])('+m+') (?=[-\u2212]?\\d)','g'),'$1$2\u00A0');};
const T=s=>lat(pegar(tr(plano(s))));  // para el PDF
const TQ=s=>tr(plano(s));            // para el QR (UTF-8)
const fISO=f=>f||'';

/* ---------- el PDF ---------- */
const MM=0.3528,COL={pri:[31,122,99],pri2:[21,86,70],ink:[27,38,33],gris:[98,108,104],linea:[214,224,219],suave:[238,245,241],
  rojo:[178,58,40],ambar:[176,112,20],verde:[31,122,99],blanco:[255,255,255]};
function pdf(meta){
  const {jsPDF}=window.jspdf;const d=new jsPDF({unit:'mm',format:'a4',compress:true});
  const PW=210,PH=297,M=16,AN=PW-2*M,ABAJO=PH-17;
  d.setProperties({title:lat(meta.titulo),subject:lat(meta.sub||''),author:lat(meta.autor||''),creator:'Rumentis',keywords:meta.cod||''});
  d.setLineHeightFactor(1.25);
  const P={d,M,AN,PW,PH,y:M};
  const fuente=(t,b)=>{d.setFontSize(t);d.setFont('helvetica',b===true?'bold':b||'normal');};
  const color=c=>d.setTextColor(c[0],c[1],c[2]);
  const lh=t=>t*MM*1.3;
  P.lh=lh;P.fuente=fuente;P.color=color;
  P.pagina=()=>{d.addPage();P.y=M;
    // encabezado corto en las páginas que siguen
    fuente(7.5,'bold');color(COL.pri);d.text(T(meta.titulo).toUpperCase(),M,P.y,{baseline:'top'});
    fuente(7.5);color(COL.gris);d.text(T(meta.finca||''),PW-M,P.y,{baseline:'top',align:'right'});
    d.setDrawColor(...COL.linea);d.setLineWidth(0.2);d.line(M,P.y+4.5,PW-M,P.y+4.5);P.y+=9;};
  P.espacio=h=>{if(P.y+h>ABAJO)P.pagina();};
  P.encabezado=()=>{
    d.setFillColor(...COL.pri);d.rect(0,0,PW,36,'F');d.setFillColor(...COL.pri2);d.rect(0,36,PW,1.2,'F');
    fuente(7.5,'bold');color([190,228,214]);d.text('RUMENTIS',M,9,{baseline:'top',charSpace:0.8});
    fuente(18,'bold');color(COL.blanco);const tl=d.splitTextToSize(T(meta.titulo),AN-58);d.text(tl[0],M,14,{baseline:'top'});
    fuente(9.5);color([222,240,233]);d.text(d.splitTextToSize(T(meta.sub||''),AN-58).slice(0,2),M,23.5,{baseline:'top'});
    fuente(9,'bold');color(COL.blanco);d.text(d.splitTextToSize(T(meta.finca||''),56).slice(0,2),PW-M,10,{baseline:'top',align:'right'});
    fuente(8);color([222,240,233]);d.text(d.splitTextToSize(T(meta.derecha||''),56).slice(0,3),PW-M,19,{baseline:'top',align:'right'});
    P.y=44;};
  P.seccion=(t,sub)=>{P.espacio(22);P.y+=3;fuente(12,'bold');color(COL.pri2);d.text(T(t),M,P.y,{baseline:'top'});
    if(sub){fuente(8);color(COL.gris);d.text(T(sub),PW-M,P.y+1.2,{baseline:'top',align:'right'});}
    P.y+=lh(12)+0.6;d.setDrawColor(...COL.pri);d.setLineWidth(0.5);d.line(M,P.y,M+14,P.y);d.setDrawColor(...COL.linea);d.setLineWidth(0.2);d.line(M+14,P.y,PW-M,P.y);P.y+=3.4;};
  // párrafo con salto de página por renglón
  P.texto=(t,{t:tam=9.5,b=false,c=COL.ink,x=M,an=AN,esp=2.2,al='left',crudo=false}={})=>{
    fuente(tam,b);color(c);const L=d.splitTextToSize(crudo?lat(t):T(t),an),h=lh(tam);
    for(const ln of L){P.espacio(h);d.text(ln,al==='right'?x+an:x,P.y,{baseline:'top',align:al});P.y+=h;}P.y+=esp;};
  P.nota=(t,o={})=>P.texto(t,{t:7.8,c:COL.gris,...o});
  // cifras grandes en cajas
  P.cifras=(L,{cols=3}={})=>{
    const g=3,w=(AN-g*(cols-1))/cols;
    for(let i=0;i<L.length;i+=cols){const fila=L.slice(i,i+cols);
      fuente(7.8);const hs=fila.map(([v,e])=>d.splitTextToSize(T(e),w-6).length);const h=8+lh(13)+Math.max(...hs)*lh(7.8);
      P.espacio(h+g);
      fila.forEach(([v,e,c],j)=>{const x=M+j*(w+g);d.setFillColor(...COL.suave);d.roundedRect(x,P.y,w,h,1.6,1.6,'F');
        fuente(13,'bold');color(c||COL.ink);d.text(d.splitTextToSize(T(v),w-6)[0],x+3,P.y+3,{baseline:'top'});
        fuente(7.8);color(COL.gris);d.text(d.splitTextToSize(T(e),w-6),x+3,P.y+4+lh(13),{baseline:'top'});});
      P.y+=h+g;}
    P.y+=1;};
  // pares de dato y valor en columnas
  P.pares=(L,{cols=2,an=AN,x=M}={})=>{
    const g=4,w=(an-g*(cols-1))/cols;
    for(let i=0;i<L.length;i+=cols){const fila=L.slice(i,i+cols);
      fuente(9.5,'bold');const hv=fila.map(([a,v])=>d.splitTextToSize(T(v==null||v===''?'–':v),w).length);const h=lh(7.5)+Math.max(...hv)*lh(9.5)+2.2;
      P.espacio(h);
      fila.forEach(([a,v],j)=>{const xx=x+j*(w+g);fuente(7.5);color(COL.gris);d.text(T(a),xx,P.y,{baseline:'top'});
        fuente(9.5,'bold');color(COL.ink);d.text(d.splitTextToSize(T(v==null||v===''?'–':v),w),xx,P.y+lh(7.5),{baseline:'top'});});
      P.y+=h;}
    P.y+=1.5;};
  // tabla con encabezado que se repite en cada página. Celda: texto o {t, b (negrita), c (color)}
  P.tabla=(head,rows,{w,al,t:tam=8.4,total=false}={})=>{
    const n=head.length,ws=(w||head.map(()=>1)),sw=ws.reduce((a,b)=>a+b,0),cw=ws.map(v=>v/sw*AN),pad=1.6,h1=lh(tam);
    const A=al||head.map((x,i)=>i===0?'l':'r');
    const cab=()=>{fuente(tam-0.6,'bold');const L=head.map((h,i)=>d.splitTextToSize(T(h),cw[i]-2*pad));const hh=Math.max(...L.map(l=>l.length))*lh(tam-0.6)+2*pad;
      d.setFillColor(...COL.pri);d.rect(M,P.y,AN,hh,'F');color(COL.blanco);let x=M;
      L.forEach((l,i)=>{d.text(l,A[i]==='r'?x+cw[i]-pad:x+pad,P.y+pad,{baseline:'top',align:A[i]==='r'?'right':'left'});x+=cw[i];});P.y+=hh;};
    const celdas=r=>r.map((c,i)=>{const o=c&&typeof c==='object'?c:{t:c};fuente(tam,o.b?'bold':'normal');return {...o,L:d.splitTextToSize(T(o.t==null?'–':o.t),cw[i]-2*pad)};});
    P.espacio(lh(tam)*3+8);cab();
    rows.forEach((r,k)=>{const C=celdas(r),hh=Math.max(...C.map(c=>c.L.length))*h1+2*pad;const ult=total&&k===rows.length-1;
      if(P.y+hh>ABAJO){P.pagina();cab();}
      if(ult){d.setFillColor(...COL.suave);d.rect(M,P.y,AN,hh,'F');d.setDrawColor(...COL.pri);d.setLineWidth(0.35);d.line(M,P.y,PW-M,P.y);}
      else if(k%2===1){d.setFillColor(248,250,249);d.rect(M,P.y,AN,hh,'F');}
      let x=M;C.forEach((c,i)=>{fuente(tam,c.b||ult?'bold':'normal');color(c.c||COL.ink);d.text(c.L,A[i]==='r'?x+cw[i]-pad:x+pad,P.y+pad,{baseline:'top',align:A[i]==='r'?'right':'left'});x+=cw[i];});
      P.y+=hh;d.setDrawColor(...COL.linea);d.setLineWidth(0.15);d.line(M,P.y,PW-M,P.y);});
    P.y+=3.5;};
  // línea de un valor en el tiempo (con el cero marcado)
  P.linea=(pts,{alto=42,fmt=v=>nf(v),etq=[]}={})=>{
    P.espacio(alto+8);const x0=M+18,x1=PW-M-2,y0=P.y+2,y1=P.y+alto;
    const vs=pts.map(p=>p[1]);let lo=Math.min(0,...vs),hi=Math.max(0,...vs);if(hi===lo)hi=lo+1;const pd=(hi-lo)*0.08;lo-=lo<0?pd:0;hi+=pd;
    const X=i=>x0+(x1-x0)*(pts.length>1?i/(pts.length-1):0),Y=v=>y1-(y1-y0)*(v-lo)/(hi-lo);
    d.setDrawColor(...COL.linea);d.setLineWidth(0.15);fuente(7);color(COL.gris);
    for(const v of [hi,(hi+lo)/2,lo]){d.line(x0,Y(v),x1,Y(v));d.text(lat(fmt(v)),x0-2,Y(v),{baseline:'middle',align:'right'});}
    if(lo<0&&hi>0){d.setDrawColor(...COL.rojo);d.setLineWidth(0.3);d.line(x0,Y(0),x1,Y(0));}
    d.setDrawColor(...COL.pri);d.setLineWidth(0.7);for(let i=1;i<pts.length;i++)d.line(X(i-1),Y(pts[i-1][1]),X(i),Y(pts[i][1]));
    d.setFillColor(...COL.pri);pts.forEach((p,i)=>d.circle(X(i),Y(p[1]),0.7,'F'));
    fuente(7);color(COL.gris);etq.forEach(([i,t])=>d.text(T(t),X(i),y1+1.5,{baseline:'top',align:i===0?'left':i===pts.length-1?'right':'center'}));
    P.y=y1+7;};
  // código QR dibujado con cuadros (vectorial, nítido al imprimir)
  P.qr=(texto,x,y,lado)=>{
    const q=qrcode(0,'M');q.addData(texto,'Byte');q.make();const n=q.getModuleCount(),mz=4,s=lado/(n+2*mz);
    d.setFillColor(255,255,255);d.rect(x,y,lado,lado,'F');d.setFillColor(0,0,0);
    for(let r=0;r<n;r++){let c=0;while(c<n){if(!q.isDark(r,c)){c++;continue;}let e=c;while(e+1<n&&q.isDark(r,e+1))e++;
      d.rect(x+(mz+c)*s,y+(mz+r)*s,(e-c+1)*s+0.01,s+0.01,'F');c=e+1;}}
    return n;};
  P.firmas=L=>{P.espacio(34);P.y+=16;const g=12,w=(AN-g*(L.length-1))/L.length;
    L.forEach((t,i)=>{const x=M+i*(w+g);d.setDrawColor(...COL.ink);d.setLineWidth(0.3);d.line(x,P.y,x+w,P.y);fuente(8);color(COL.gris);d.text(d.splitTextToSize(T(t),w),x,P.y+1.8,{baseline:'top'});});
    P.y+=12;};
  P.pie=()=>{const N=d.getNumberOfPages();
    for(let i=1;i<=N;i++){d.setPage(i);d.setDrawColor(...COL.linea);d.setLineWidth(0.2);d.line(M,PH-12.5,PW-M,PH-12.5);fuente(7.2);color(COL.gris);
      d.text(lat(`${T('Hecho con Rumentis')} · ${T(meta.finca||'')}${meta.cod?' · '+T('Código')+' '+meta.cod:''}`),M,PH-11,{baseline:'top'});
      d.text(T(`Página ${i} de ${N}`),PW-M,PH-11,{baseline:'top',align:'right'});}};
  P.fin=()=>{P.pie();return d.output('arraybuffer');};
  return P;
}

/* ---------- mandar el archivo ---------- */
function b64(buf){const u=new Uint8Array(buf);let s='';for(let i=0;i<u.length;i+=0x8000)s+=String.fromCharCode.apply(null,u.subarray(i,i+0x8000));return btoa(s);}
function bajar(blob,nombre){const u=URL.createObjectURL(blob),a=document.createElement('a');a.href=u;a.download=nombre;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(u),60000);}
/* modo: abrir, compartir o guardar. Devuelve false si no se pudo (el usuario ya vio el aviso) */
async function enviar(nombre,buf,modo,titulo){
  const A=window.Android,mime='application/pdf';
  if(A&&A.compartirArchivo){const x=b64(buf);let ok=false;
    try{ok=modo==='abrir'?A.abrirArchivo(nombre,x,mime):modo==='guardar'?A.guardarArchivo(nombre,x,mime):A.compartirArchivo(nombre,x,mime,T(titulo||nombre));}catch(e){ok=false;}
    if(!ok)toast('No pude preparar el archivo. Revisa el espacio del teléfono.');return !!ok;}
  const blob=new Blob([buf],{type:mime});
  if(modo==='compartir'&&navigator.canShare&&typeof File!=='undefined'){const f=new File([blob],nombre,{type:mime});
    if(navigator.canShare({files:[f]})){try{await navigator.share({files:[f],title:tr(titulo||nombre)});return true;}catch(e){if(e&&e.name==='AbortError')return false;}}}
  if(modo==='abrir'){const u=URL.createObjectURL(blob);const w=window.open(u,'_blank');if(w){setTimeout(()=>URL.revokeObjectURL(u),120000);return true;}URL.revokeObjectURL(u);}
  bajar(blob,nombre);return true;
}
const archivo=t=>String(t).normalize('NFD').replace(/[\u0300-\u036F]/g,'').replace(/[^A-Za-z0-9._-]+/g,'-').replace(/-+/g,'-').replace(/^-|-$/g,'').slice(0,60)||'documento';

/* ---------- los documentos emitidos ---------- */
const DOCS=()=>(S.config.docs||[]);
function registrar(doc){const L=DOCS().filter(o=>o.cod!==doc.cod);L.unshift(doc);put('ajustes','finca',{...S.config,docs:L.slice(0,150)});}
const titular=()=>S.config.titular||{};
const paisN=()=>{const p=S.config.pais;if(!p)return '';try{return new Intl.DisplayNames([window.I18N?I18N.lang():'es'],{type:'region'}).of(p);}catch(e){return p;}};
const lugar=()=>[S.config.ubicacion,paisN()].filter(Boolean).join(', ');
const hoyL=()=>conA(hoy());
const pct=(v,d=0)=>{const r=Math.round(v*100*10**d)/10**d;return `${nf(r===0?0:r,d)} %`;};
// fechas con año (documentos formales)
const conA=f=>{if(!f)return '';const s=ffl(f);return s.includes(f.slice(0,4))?s:s+' de '+f.slice(0,4);};
const conAc=f=>{if(!f)return '';const s=ffc(f);return s.includes(f.slice(0,4))?s:s+' '+f.slice(0,4);};

/* ================= INFORME PARA EL BANCO ================= */
function datosBanco(){
  const C=calc(),b=Fin.balance(),per=Fin.periodo('12m'),r=Fin.resultados(per[0],per[1]),fin=Fin.F();
  const cer=C.cer.filter(x=>x.l.fechaCierre&&x.l.fechaCierre>=per[0]);
  return {C,b,per,r,fin,cer};
}
async function informeBanco(o){
  await libs();
  const {C,b,per,r,fin,cer}=datosBanco(),cfg=S.config,tt=titular();
  const resumen={t:'banco',finca:cfg.finca||'',f:hoy(),cab:C.cabT,act:C.act.length,pat:Math.round(b.patrimonio),act_:Math.round(b.activos),pas:Math.round(b.pasivos),ven:Math.round(r.ventas),net:Math.round(r.neta),tit:tt.nombre||'',id:tt.id||''};
  const {cod}=codigo('RMB',resumen);
  const P=pdf({titulo:'Informe productivo y financiero',sub:o.para?`${tr('Preparado para')} ${o.para}`:'Engorde de ganado en corral',finca:cfg.finca||'Mi engorde',
    derecha:[`Al ${hoyL()}`,lugar()].filter(Boolean).join('\n'),autor:tt.nombre||cfg.finca||'',cod});
  P.encabezado();
  // productor
  P.seccion('Productor');
  P.pares([['Nombre',tt.nombre||'–'],['Identificación',tt.id||'–'],['Finca',cfg.finca||'–'],['Ubicación',lugar()||'–'],['Teléfono',tt.tel||'–'],['Actividad','Engorde de ganado de carne en corral']],{cols:3});
  // resumen
  P.seccion('Resumen','valores en '+(cfg.moneda||'L'));
  P.cifras([[nf(C.cabT),`cabezas en engorde en ${pl(C.act.length,'lote','lotes')}`],[money(b.ganadoM),'valor del ganado a precio de venta'],[money(b.patrimonio),'patrimonio (activos menos deudas)',b.patrimonio<0?COL.rojo:null],
    [money(r.ventas),'ventas de ganado, últimos 12 meses'],[money(r.neta),'utilidad neta, últimos 12 meses',r.neta<0?COL.rojo:COL.verde],[money(b.pasivos),'deudas (créditos y cuentas por pagar)']]);
  const kgPie=C.act.reduce((s,x)=>s+x.pesoHoy*x.cab,0);
  P.texto(`La finca tiene ${pl(C.act.length,'lote','lotes')} en engorde con ${pl(C.cabT,'cabeza','cabezas')} y ${nf(kgPie)} kg en pie. En los últimos 12 meses vendió ${pl(r.cabV,'cabeza','cabezas')} por ${money(r.ventas)}${cer.length?` y cerró ${pl(cer.length,'lote','lotes')}`:''}.`+
    (b.pasivos>0?` Sus deudas son el ${pct(b.activos>0?b.pasivos/b.activos:0)} de sus activos.`:' No tiene deudas registradas.'));
  // balance
  P.seccion('Balance general',`al ${hoyL()}`);
  const B=[['Efectivo y bancos',b.caja],['Cuentas por cobrar',b.cxc],['Ganado en engorde (precio de venta)',b.ganadoM],['Alimento en bodega',b.alimento],['Medicinas e insumos',b.insumos],['Equipo (valor en libros)',b.equipos]];
  P.tabla(['Activos','Monto','% del total'],B.map(([t,v])=>[t,money(v),b.activos>0?pct(v/b.activos):'–']).concat([[{t:'Total activos',b:true},{t:money(b.activos),b:true},'100 %']]),{w:[3,1.4,1],total:true});
  P.tabla(['Deudas y patrimonio','Monto','% de los activos'],[['Créditos (saldo con intereses)',money(b.creditos),b.activos>0?pct(b.creditos/b.activos):'–'],['Cuentas por pagar',money(b.cxp),b.activos>0?pct(b.cxp/b.activos):'–'],[{t:'Total deudas',b:true},{t:money(b.pasivos),b:true},b.activos>0?pct(b.pasivos/b.activos):'–'],
    [{t:'Patrimonio',b:true},{t:money(b.patrimonio),b:true,c:b.patrimonio<0?COL.rojo:COL.ink},b.activos>0?pct(b.patrimonio/b.activos):'–']],{w:[3,1.4,1],total:true});
  P.nota(`El ganado se valora al precio de venta de ${pk(+cfg.precioVentaKg||0)} con ${nf(+cfg.desbaste||0)} % de desbaste. Lo que costó hasta hoy: ${money(b.ganadoC)}.`);
  // resultados
  P.seccion('Estado de resultados',`${conAc(per[0])} a ${conAc(per[1])}`);
  const RR=[['Ventas de ganado',r.ventas],['Costo del ganado vendido',-r.costoV],[{t:'Utilidad bruta',b:true},r.bruta],['Gastos generales',-r.generales],['Intereses',-r.intereses],['Depreciación del equipo',-r.dep],[{t:'Utilidad neta',b:true},r.neta]];
  P.tabla(['Concepto','Monto','% de las ventas'],RR.map(([t,v])=>[t,{t:money(v),b:typeof t==='object',c:v<0?COL.rojo:COL.ink},r.ventas>0?pct(v/r.ventas):'–']),{w:[3,1.4,1],total:true});
  if(C.act.length)P.nota(r.noReal>=0?`Además, el ganado en engorde vale ${money(r.noReal)} más de lo que costó hasta hoy (ganancia no realizada, no incluida arriba).`:`Además, el ganado en engorde vale ${money(-r.noReal)} menos de lo que costó hasta hoy (pérdida no realizada, no incluida arriba).`);
  // créditos
  P.seccion('Créditos');
  const cr=(fin.creditos||[]).map(c=>({c,q:Fin.credito(c)}));
  if(cr.length)P.tabla(['Crédito','Desde','Monto','Tasa anual','Plazo','Cuota al mes','Saldo','Vence'],
    cr.map(({c,q})=>[c.n||'Crédito',ffc(c.f),money(+c.monto||0),`${nf(+c.tasa||0,1)} %`,+c.plazo?`${nf(+c.plazo)} meses`:'–',q.cuota?money(q.cuota):'–',money(q.saldo),q.vence?ffc(q.vence):'–']).concat(
      cr.length>1?[[{t:'Total',b:true},'','','','',money(cr.reduce((s,o)=>s+(o.q.cuota||0),0)),money(cr.reduce((s,o)=>s+o.q.saldo,0)),'']]:[]),{w:[2.2,1.2,1.4,1,1,1.4,1.4,1.2],t:7.8,total:cr.length>1});
  else P.texto('No hay créditos registrados.');
  // indicadores
  P.seccion('Indicadores productivos');
  const V=RumiMas.valoresFinca(),REF=RumiMas.REF||[];
  const cerI=(()=>{if(!cer.length)return null;const w=cer.reduce((s,x)=>s+x.cab0,0)||1,pon=f=>{const L=cer.filter(x=>f(x)!=null&&isFinite(f(x)));const ww=L.reduce((s,x)=>s+x.cab0,0);return ww?L.reduce((s,x)=>s+f(x)*x.cab0,0)/ww:null;};
    return {gdp:pon(x=>x.gdpTot),conv:pon(x=>x.conv||null),mort:pon(x=>x.mort*100),dias:pon(x=>x.dec),mg:cer.reduce((s,x)=>s+x.margenReal,0)/Math.max(1,cer.reduce((s,x)=>s+x.cab0-x.bajas,0))};})();
  const est=(rf,v)=>{if(v==null)return '–';const ok=rf.alto?v>=rf.bueno:v<=rf.bueno,mal=rf.alto?v<rf.malo:v>rf.malo;return ok?{t:'Bueno',c:COL.verde,b:true}:mal?{t:'A mejorar',c:COL.rojo,b:true}:{t:'Aceptable',c:COL.ambar,b:true};};
  const fmtI=(rf,v)=>v==null?'–':rf.k==='gdp'?`${nf(v,2)} kg`:rf.k==='conv'?`${nf(v,1)} a 1`:`${nf(v,rf.dec)} %`;
  const refT=rf=>rf.k==='gdp'?`${nf(rf.bueno,1)} kg o más`:rf.k==='conv'?`${nf(rf.bueno,1)} a 1 o menos`:rf.alto?`${nf(rf.bueno,1)} % o más`:`${nf(rf.bueno,rf.dec)} % o menos`;
  const nomI={gdp:'Ganancia diaria por cabeza',conv:'Conversión (alimento por kg ganado)',mort:'Mortalidad',costo:'Costo del kg ganado (% del precio de venta)',cms:'Consumo de materia seca (% del peso)'};
  const cerV={gdp:cerI&&cerI.gdp,conv:cerI&&cerI.conv,mort:cerI&&cerI.mort};
  P.tabla(['Indicador','Lotes en engorde','Lotes vendidos, 12 meses','Referencia','Estado'],REF.map(rf=>[nomI[rf.k]||rf.t,fmtI(rf,V[rf.k]),fmtI(rf,cerV[rf.k]),refT(rf),est(rf,V[rf.k]!=null?V[rf.k]:cerV[rf.k])]),{w:[2.6,1.3,1.3,1.4,1],al:['l','r','r','r','l']});
  if(cerI)P.nota(`Los lotes vendidos en 12 meses estuvieron en promedio ${nf(cerI.dias)} días en el corral y dejaron ${money(cerI.mg)} de margen por cabeza.`);
  // lotes en engorde
  if(C.act.length){P.seccion('Lotes en engorde',pl(C.cabT,'cabeza','cabezas'));
    P.tabla(['Lote','Entró','Cabezas','Peso hoy','Ganancia diaria','Sale','Margen estimado'],C.act.map(x=>[x.l.nombre,ffc(x.l.fechaIngreso),nf(x.cab),`${nf(x.pesoHoy)} kg`,`${nf(x.gdpUse,2)} kg`,x.listo?'Listo':ffc(x.fechaMeta),{t:money(x.proy.margen),c:x.proy.margen<0?COL.rojo:COL.ink}]).concat(
      C.act.length>1?[[{t:'Total',b:true},'',nf(C.cabT),'','','',money(C.act.reduce((s,x)=>s+x.proy.margen,0))]]:[]),{w:[2,1.1,1,1.2,1.2,1,1.5],total:C.act.length>1});}
  // lotes vendidos
  P.seccion('Lotes vendidos en los últimos 12 meses');
  if(cer.length){const tc=cer.reduce((s,x)=>s+x.cab0-x.bajas,0),tm=cer.reduce((s,x)=>s+x.margenReal,0);
    P.tabla(['Lote','Vendido','Cabezas','Días','Ganancia diaria','Conversión','Margen','Por cabeza'],cer.map(x=>{const cb=x.cab0-x.bajas;return [x.l.nombre,ffc(x.l.fechaCierre),nf(cb),nf(x.dec),`${nf(x.gdpTot,2)} kg`,x.conv?`${nf(x.conv,1)} a 1`:'–',{t:money(x.margenReal),c:x.margenReal<0?COL.rojo:COL.ink},money(cb?x.margenReal/cb:0)];}).concat(
      cer.length>1?[[{t:'Total',b:true},'',nf(tc),'','','',money(tm),money(tc?tm/tc:0)]]:[]),{w:[2,1.1,1,0.8,1.2,1.1,1.4,1.2],t:8,total:cer.length>1});}
  else P.texto('Ningún lote se cerró en los últimos 12 meses.');
  // flujo
  if(C.act.length){const F=RumiMas.flujo90();P.seccion('Dinero en los próximos 90 días','proyección');
    const pts=[[hoy(),F.caja]].concat(F.sem.map(s=>[addDias(s.f,6),s.acc]));
    P.linea(pts,{fmt:v=>money(v),etq:[[0,'hoy'],[Math.round(pts.length/2),ffc(pts[Math.round(pts.length/2)][0])],[pts.length-1,ffc(pts[pts.length-1][0])]]});
    P.texto(`Con el efectivo de hoy (${money(F.caja)}), el alimento de los lotes, los gastos generales y las cuotas de los créditos, `+(F.min.f===hoy()?`el dinero no baja de lo que hay hoy; a los 90 días quedaría ${money(F.fin)}.`:`el punto más bajo sería ${money(F.min.v)} el ${conA(F.min.f)}; a los 90 días quedaría ${money(F.fin)}.`)+(F.min.v<0?' Ese faltante habría que cubrirlo con crédito o ventas antes de esa fecha.':''));
    const ev=F.sem.filter(s=>s.ent>0);if(ev.length)P.tabla(['Semana','Entradas','Salidas','Queda en caja','Qué pasa'],ev.map(s=>[ffc(s.f),money(s.ent),money(s.sal),{t:money(s.acc),c:s.acc<0?COL.rojo:COL.ink},s.ev.join(', ')]),{w:[1,1.2,1.2,1.2,2.6],al:['l','r','r','r','l'],t:8});
    P.nota('Supone que cada lote se vende al llegar a su meta al precio de venta configurado y que el consumo y los gastos siguen como hasta hoy.');}
  // riesgo
  if(C.act.some(x=>x.proy)&&window.RumiPro&&RumiPro.simFinca){const sf=RumiPro.simFinca(),q=p=>sf.T[Math.min(sf.N-1,Math.floor(p*sf.N))];
    P.seccion('Riesgo del engorde en curso');
    P.cifras([[pct(sf.pr),'probabilidad de que el engorde completo deje ganancia',sf.pr>=0.9?COL.verde:sf.pr>=0.7?COL.ambar:COL.rojo],[money(q(0.1)),'margen en un mal escenario (1 de cada 10 queda por debajo)',q(0.1)<0?COL.rojo:null],[money(q(0.5)),'margen más probable (mediana)']]);
    P.nota(`Simulación de Montecarlo con ${nf(sf.N)} escenarios: varían el precio de venta, la ganancia diaria, el costo del alimento y las muertes hasta que cada lote llega a su meta.`);}
  // huella
  if(window.Huella){const H=Huella.finca();if(H){P.seccion('Huella de carbono','IPCC 2019, nivel 2');
    P.tabla(['Lote','t CO2e',g5('kg CO2e por kg ganado'),g5('kg CO2e por cabeza al día')],H.R.map(r=>[r.x.l.nombre,nf(r.co2.total/1000,1),r.porKg!=null?nf(r.porKg,1):'–',nf(r.porCabDia,1)]).concat(
      H.R.length>1?[[{t:'Total',b:true},nf(H.co2/1000,1),H.porKg!=null?nf(H.porKg,1):'–','']]:[]),{w:[2.4,1,1.4,1.4],total:H.R.length>1});
    P.nota('Metano del rumen y del estiércol y óxido nitroso del estiércol mientras los animales están en el corral, calculados con el alimento registrado. No incluye la producción del alimento ni la cría.');}}
  // declaración
  P.seccion('Declaración');
  P.texto('Los datos de este informe salen de los registros que el productor anota en Rumentis: compras, alimento, pesajes, sanidad, ventas, gastos, créditos e inventario. No son estados financieros auditados. El valor del ganado y las proyecciones son estimaciones con los precios configurados por el productor.',{t:8.6});
  P.texto(`${tr('Código del informe:')} ${cod}. ${tr(`Emitido el ${hoyL()}.`)}`,{t:8.6,b:true});
  P.firmas(['Firma del productor',tt.nombre?`${tr('Nombre e identificación')}: ${tt.nombre}${tt.id?', '+tt.id:''}`:'Nombre e identificación']);
  const buf=P.fin();
  registrar({t:'banco',cod,f:hoy(),hh:new Date().toTimeString().slice(0,5),n:'Informe productivo y financiero',para:o.para||'',snap:resumen});
  return {buf,cod,nombre:`${archivo(tr('Informe'))}-${archivo(cfg.finca||'finca')}-${hoy()}.pdf`,titulo:'Informe productivo y financiero'};
}

/* ================= CERTIFICADO DE LOTE ================= */
function retiroDe(i){const d=+i.retiro||0;return d>0?addDias(i.f,d):null;}
function datosCert(x){
  const l=x.l,H=hoy(),san=x.g.sanidad.slice().sort((a,b)=>a.f<b.f?-1:1);
  const vig=san.map(i=>({i,h:retiroDe(i)})).filter(o=>o.h&&o.h>H).sort((a,b)=>a.h<b.h?1:-1);
  const racs=[...new Set(x.g.alimento.map(a=>a.racion||x.racion).filter(Boolean))].map(id=>S.raciones[id]).filter(Boolean);
  const hu=window.Huella?Huella.lote(x):null;
  // lo que certifica (y con lo que se calcula el código)
  const snap={t:'cert',lote:l.nombre,id:x.id,finca:S.config.finca||'',f:H,ing:l.fechaIngreso,prov:l.proveedor||'',tipo:l.tipo||'',raza:l.raza||'',
    cab0:x.cab0,cab:x.activo?x.cab:0,bajas:x.bajas,vend:x.vend,p0:Math.round(x.p0*10)/10,peso:Math.round(x.pesoHoy*10)/10,
    san:san.map(i=>[i.f,i.clase||'',i.producto||'',+i.cab||0,+i.retiro||0]),ret:vig.length?[vig[0].h,vig[0].i.producto||vig[0].i.clase||'']:null,
    pes:x.g.pesaje.map(p=>[p.f,+p.prom||0]),aret:(l.animales||[]).map(a=>a.arete||'').filter(Boolean),hu:hu&&hu.porKg!=null?Math.round(hu.porKg*100)/100:null};
  return {x,l,san,vig,racs,hu,snap};
}
const hashSan=x=>sha256(canon(x.g.sanidad.map(i=>[i.f,i.clase||'',i.producto||'',+i.cab||0,+i.retiro||0]).sort()));
function textoQR(D,cod){
  const s=D.snap,L=[
    TQ('RUMENTIS · Certificado de lote'),`${TQ('Código')}: ${cod}`,`${TQ('Lote')}: ${s.lote}`,`${TQ('Finca')}: ${[s.finca,lugar()].filter(Boolean).join(', ')}`,
    `${TQ('Emitido')}: ${s.f}`,`${TQ('Entró')}: ${s.ing}${s.prov?' · '+TQ('proveedor')+' '+s.prov:''}`,
    TQ(`Cabezas: ${s.cab0} al entrar, ${s.cab} en el corral, ${s.vend} vendidas, ${s.bajas} muertas`),
    TQ(`Sanidad: ${pl(s.san.length,'registro','registros')}`)+'; '+(s.ret?TQ(`en retiro hasta el ${s.ret[0]}`)+` (${s.ret[1]})`:TQ(`sin retiros vigentes al ${s.f}`)),
  ];
  if(s.hu!=null)L.push(TQ(`Huella: ${nf(s.hu,1)} ${g5('kg CO2e por kg ganado')}`));
  if(s.aret.length)L.push(TQ(`Aretes: ${nf(s.aret.length)}, en el certificado`));
  return L.join('\n');
}
async function certificadoLote(id,o={}){
  await libs();
  const C=calc(),x=C.L[id];if(!x)throw new Error('lote');
  const D=datosCert(x),{l,san,vig,racs,hu,snap}=D,cfg=S.config,tt=titular();
  const {cod}=codigo('RMC',snap);
  const P=pdf({titulo:'Certificado de lote',sub:`${l.nombre} · ${pl(x.cab0,'cabeza','cabezas')}${l.tipo?', '+l.tipo.toLowerCase():''}`,finca:cfg.finca||'Mi engorde',
    derecha:[`Emitido el ${hoyL()}`,lugar()].filter(Boolean).join('\n'),autor:tt.nombre||cfg.finca||'',cod});
  P.encabezado();
  // identificación con el QR a la derecha
  const y0=P.y,lado=46,xq=P.PW-P.M-lado;
  P.seccion('Identificación');
  const yI=P.y;P.qr(textoQR(D,cod),xq,yI,lado);
  P.fuente(7);P.color(COL.gris);P.d.text(P.d.splitTextToSize(T('Lee el código con la cámara del teléfono: trae el resumen de este certificado, sin internet.'),lado),xq,yI+lado+1.5,{baseline:'top'});
  P.pares([['Lote',l.nombre],['Código',cod],['Finca',cfg.finca||'–'],['Ubicación',lugar()||'–'],['Productor',[tt.nombre,tt.id].filter(Boolean).join(', ')||'–'],['Tipo',[l.tipo,l.raza].filter(Boolean).join(', ')||'–'],
    ['Entró',conA(l.fechaIngreso)],[x.activo?'Días en el corral':'Vendido',x.activo?nf(x.dec):`${conA(l.fechaCierre)}, ${pl(x.dec,'día','días')} en el corral`]],{cols:2,an:P.AN-lado-6});
  P.y=Math.max(P.y,yI+lado+10);
  // origen y estado
  P.seccion('Origen y estado');
  P.pares([['Proveedor',l.proveedor||'No anotado'],['Peso de entrada',`${nf(x.p0)} kg por cabeza`],['Cabezas al entrar',nf(x.cab0)],
    [x.activo?'Peso estimado hoy':'Peso de venta',`${nf(x.pesoHoy)} kg por cabeza${x.estimado?' (sin pesajes)':''}`],['En el corral hoy',nf(x.activo?x.cab:0)],['Vendidas',nf(x.vend)],['Muertes',`${nf(x.bajas)} (${nf(x.mort*100,1)} %)`],
    ['Ganancia diaria',`${nf(x.activo?x.gdpUse:x.gdpTot,2)} kg`],['Destino',o.destino||'–']],{cols:3});
  // sanidad
  P.seccion('Sanidad',pl(san.length,'registro','registros'));
  const H=hoy();
  if(vig.length){const v=vig[0];P.cifras([[`En retiro hasta el ${conAc(v.h)}`,`por ${v.i.producto||v.i.clase}. No debe ir a matadero antes de esa fecha.`,COL.rojo]],{cols:1});}
  else P.cifras([[`Sin retiros vigentes al ${conAc(H)}`,san.length?'Todos los tiempos de retiro de los productos aplicados ya se cumplieron.':'No hay tratamientos registrados en este lote.',COL.verde]],{cols:1});
  if(san.length)P.tabla(['Fecha','Tipo','Producto','Cabezas','Retiro','Libre desde'],san.map(i=>{const h=retiroDe(i);return [conAc(i.f),i.clase||'–',i.producto||'–',nf(+i.cab||0),+i.retiro?`${nf(+i.retiro)} días`:'Sin retiro',h?{t:conAc(h),c:h>H?COL.rojo:COL.ink,b:h>H}:'–'];}),{w:[1,1.3,2.2,0.9,1,1.1],al:['l','l','l','r','r','r'],t:8});
  // pesajes
  if(x.g.pesaje.length){P.seccion('Pesajes',pl(x.g.pesaje.length,'pesaje','pesajes'));
    let prev={f:l.fechaIngreso,v:x.p0};
    P.tabla(['Fecha','Peso promedio','Cabezas pesadas','Ganancia diaria desde el anterior'],[['Entrada: '+conAc(l.fechaIngreso),`${nf(x.p0)} kg`,nf(x.cab0),'–']].concat(x.g.pesaje.slice().sort((a,b)=>a.f<b.f?-1:1).map(p=>{const dd=dias(prev.f,p.f),g=dd>0?(+p.prom-prev.v)/dd:null;prev={f:p.f,v:+p.prom};return [conAc(p.f),`${nf(+p.prom)} kg`,nf(+p.cab||x.cab),g!=null?`${nf(g,2)} kg`:'–'];})),{w:[1.2,1.2,1.2,1.8],t:8});}
  // alimentación
  P.seccion('Alimentación');
  const kgT=x.g.alimento.reduce((s,a)=>s+(+a.kg||0),0);
  P.texto(x.g.alimento.length?`${nf(kgT)} kg de alimento en ${pl(new Set(x.g.alimento.map(a=>a.f)).size,'día','días')} con entregas anotadas${x.conv?`; conversión de ${nf(x.conv,1)} kg de alimento por kg ganado`:''}${x.consumo?`; consumo reciente de ${nf(x.consumo,1)} kg por cabeza al día`:''}.`:'No hay entregas de alimento anotadas.');
  if(racs.length)P.tabla(['Ración','Ingredientes'],racs.map(r=>[r.nombre,(r.ings||[]).filter(i=>+i.p>0).map(i=>`${i.n} ${nf(+i.p,0)} %`).join(', ')||'Sin ingredientes anotados']),{w:[1.2,3.8],al:['l','l'],t:8});
  // muertes
  if(x.g.baja.length){P.seccion('Muertes',pl(x.bajas,'cabeza','cabezas'));
    P.tabla(['Fecha','Cabezas','Causa'],x.g.baja.map(b=>[conAc(b.f),nf(+b.cab||0),b.causa||'No anotada']),{w:[1,0.8,4],al:['l','r','l'],t:8});}
  // ventas (sin precios)
  if(x.g.venta.length){P.seccion('Ventas',pl(x.vend,'cabeza','cabezas'));
    P.tabla(['Fecha','Cabezas','Peso promedio','Comprador'],x.g.venta.map(v=>[conAc(v.f),nf(+v.cab||0),+v.cab?`${nf((+v.kg||0)/(+v.cab))} kg`:'–',v.comprador||'–']),{w:[1,0.8,1.2,3],al:['l','r','r','l'],t:8});}
  // huella
  if(hu){P.seccion('Huella de carbono','IPCC 2019, nivel 2');
    P.cifras([[hu.porKg!=null?nf(hu.porKg,1):'–',g5('kg CO2e por kg ganado')],[nf(hu.co2.total/1000,2),'t CO2e del lote en el corral'],[nf(hu.porCabDia,1),g5('kg CO2e por cabeza al día')]]);
    P.nota(`Metano del rumen ${nf(hu.co2.ent)}, metano del estiércol ${nf(hu.co2.est)} y óxido nitroso ${nf(hu.co2.n2o)} ${g5('kg CO2e')}, con el alimento registrado. No incluye la producción del alimento ni la cría.`);}
  // animales
  const an=(l.animales||[]).filter(a=>a.arete);
  if(an.length){P.seccion('Animales',pl(an.length,'arete','aretes'));
    const est=a=>{const e=(x.anim||[]).find(z=>z.id===a.id||z.arete===a.arete);return e&&e.estado?({activo:'En el corral',vendido:'Vendido',muerto:'Murió'})[e.estado]||e.estado:'';};
    P.tabla(['Arete','Raza','Color','Peso de entrada','Estado'],an.map(a=>[a.arete,a.raza||l.raza||'–',a.color||'–',+a.p0?`${nf(+a.p0)} kg`:'–',est(a)||'–']),{w:[1.2,1.5,1.2,1.2,1.2],al:['l','l','l','r','l'],t:8});}
  // declaración
  P.seccion('Declaración');
  P.texto('El productor declara que estos datos son los que registró en Rumentis para este lote. Este documento no reemplaza los certificados oficiales de sanidad, las guías de movilización ni la inspección veterinaria que pida la ley de su país.',{t:8.6});
  P.texto(`${tr('Código de verificación:')} ${cod}. ${tr('El productor lo comprueba en su app: Más, Documentos, Verificar un código.')}`,{t:8.6,b:true});
  P.firmas(['Firma del productor','Firma de quien recibe']);
  const buf=P.fin();
  registrar({t:'cert',cod,f:hoy(),hh:new Date().toTimeString().slice(0,5),lote:x.id,n:l.nombre,dest:o.destino||'',snap,hs:hashSan(x)});
  return {buf,cod,nombre:`${archivo(tr('Certificado'))}-${archivo(l.nombre)}-${hoy()}.pdf`,titulo:`Certificado de ${l.nombre}`};
}

/* ---------- verificar un código ---------- */
function verificar(txt){
  const c=normCod(txt);if(c.length<8)return {est:'corto'};
  const d=DOCS().find(o=>normCod(o.cod)===c||normCod(o.cod).endsWith(c));
  if(!d)return {est:'no'};
  // el contenido guardado debe dar el mismo código (que nadie editó el respaldo)
  const pre=d.cod.slice(0,3),ok=codigo(pre,d.snap).cod===d.cod;
  let cambio=null;
  if(d.t==='cert'&&d.lote){const x=calc().L[d.lote];if(!x)cambio='borrado';else if(d.hs&&hashSan(x)!==d.hs)cambio='sanidad';}
  return {est:ok?'ok':'alterado',d,cambio};
}
function verificarHtml(r){
  if(r.est==='corto')return `<p class="prev warn">Escribe el código completo, por ejemplo RMC-1A2B-3C4D-5E6F.</p>`;
  if(r.est==='no')return `<p class="prev warn">Este código no lo emitió este teléfono. Solo se pueden comprobar los documentos hechos aquí (también vienen en tu respaldo).</p>`;
  if(r.est==='alterado')return `<p class="prev warn">El código existe, pero lo guardado no coincide con él: el respaldo pudo ser editado. No confíes en este documento.</p>`;
  const d=r.d,s=d.snap;
  let h=`<p class="prev ok"><b>Código válido.</b> Lo emitió esta app el ${ffl(d.f)}${d.hh?' a las '+esc(d.hh):''}.</p>`;
  const lista=L=>`<ul class="rp-lista">${L.map(([k,v])=>`<li><b>${k}:</b> ${v}</li>`).join('')}</ul>`;
  if(d.t==='cert')h+=lista([['Documento','Certificado de lote'],['Lote',esc(s.lote)],['Cabezas',`${nf(s.cab0)} al entrar, ${nf(s.cab)} en el corral, ${nf(s.vend)} vendidas, ${nf(s.bajas)} muertas`],
    ['Sanidad',`${pl(s.san.length,'registro','registros')}; ${s.ret?`en retiro hasta el ${ffc(s.ret[0])}`:'sin retiros vigentes'}`],['Entró',ffl(s.ing)+(s.prov?', '+esc(s.prov):'')]].concat(d.dest?[['Destino',esc(d.dest)]]:[]));
  else h+=lista([['Documento','Informe productivo y financiero'],['Finca',esc(s.finca)],['Cabezas',nf(s.cab)],['Patrimonio',money(s.pat)],['Utilidad neta, 12 meses',money(s.net)]]);
  if(r.cambio==='sanidad')h+=`<p class="prev warn">Ojo: después de emitirlo se cambiaron registros de sanidad de este lote. Emite un certificado nuevo si el comprador lo necesita al día.</p>`;
  if(r.cambio==='borrado')h+=`<p class="prev warn">El lote de este certificado ya no está en la app.</p>`;
  return h;
}

/* ================= pantallas ================= */
const btnDoc=(modo,t,extra='')=>`<button type="button" class="btn${modo==='compartir'?' pri':''}" data-act="docHacer" data-modo="${modo}" ${extra} style="flex:1">${t}</button>`;
const pieDoc=extra=>`<div class="sh-foot" style="gap:8px">${btnDoc('abrir','Ver',extra)}${btnDoc('guardar','Guardar',extra)}${btnDoc('compartir','Compartir',extra)}</div>`;
FORMS.docBanco=()=>{
  const C=calc(),tt=titular(),b=Fin.balance(),per=Fin.periodo('12m'),r=Fin.resultados(per[0],per[1]);
  openSheet(shHead('Informe para el banco','PDF')+`<form data-form="docBanco" novalidate><div class="sh-body">
    <p class="hint" style="margin-top:0">Un PDF con tu finca, balance, resultados de 12 meses, créditos, indicadores, lotes, flujo de 90 días, riesgo y huella de carbono. Sirve para pedir un crédito o mostrar cómo va tu engorde.</p>
    ${q('Tu nombre',inp('nombre',tt.nombre||'',{mode:'text',ph:'Como aparece en tu identificación'}))}
    ${q('Identificación',inp('id',tt.id||'',{mode:'text',ph:'Número de identidad o RTN'}))}
    ${q('Teléfono',inp('tel',tt.tel||'',{mode:'tel'}))}
    ${q('Para',inp('para','',{mode:'text',ph:'Nombre del banco o cooperativa (opcional)'}))}
    ${tabla(null,[['Cabezas en engorde',nf(C.cabT)],['Patrimonio',money(b.patrimonio)],['Ventas, 12 meses',money(r.ventas)],['Utilidad neta, 12 meses',money(r.neta)]])}
    <p class="hint">Revisa antes tus datos en Finanzas: efectivo, créditos, equipo e insumos. Lo que no anotes no aparece en el informe.</p>
    <p class="err"></p></div>${pieDoc('data-doc="banco"')}</form>`);
};
FORMS.docCert=({id})=>{
  const C=calc(),x=C.L[id];if(!x)return;const D=datosCert(x);
  const est=D.vig.length?`<p class="prev warn">En retiro hasta el <b>${ffl(D.vig[0].h)}</b> por ${esc(D.vig[0].i.producto||D.vig[0].i.clase)}. El certificado lo dirá.</p>`:`<p class="prev ok">Sin retiros vigentes: todos los tiempos de retiro ya se cumplieron.</p>`;
  openSheet(shHead('Certificado del lote',esc(x.l.nombre))+`<form data-form="docCert" novalidate><div class="sh-body">
    <p class="hint" style="margin-top:0">Un PDF para el comprador o el matadero: origen, sanidad con sus retiros, pesajes, alimentación, muertes, huella de carbono y aretes, con un código QR y un código para verificarlo.</p>
    ${est}
    ${tabla(null,[['Proveedor',esc(x.l.proveedor||'No anotado')],['Tratamientos',nf(D.san.length)],['Pesajes',nf(x.g.pesaje.length)],['Aretes',nf((x.l.animales||[]).filter(a=>a.arete).length)]])}
    ${x.l.proveedor?'':`<p class="hint">Anota el proveedor en Editar lote: el comprador querrá saber de dónde vienen.</p>`}
    ${q('Destino',inp('destino','',{mode:'text',ph:'Comprador o matadero (opcional)'}))}
    <p class="err"></p></div>${pieDoc(`data-doc="cert" data-id="${esc(id)}"`)}</form>`);
};
FORMS.docVerificar=()=>{
  openSheet(shHead('Verificar un código')+`<div class="sh-body">
    <p class="hint" style="margin-top:0">Escribe el código que viene en el informe o el certificado (RMB-… o RMC-…). Te digo si lo emitió esta app y qué decía.</p>
    ${q('Código',`<input class="in" name="cod" id="docCod" autocomplete="off" autocapitalize="characters" placeholder="RMC-1A2B-3C4D-5E6F" style="text-transform:uppercase">`,'','docCod')}
    <div id="docVer"></div></div><div class="sh-foot"><button type="button" class="btn pri" data-act="docVerificar" style="flex:1">Verificar</button></div>`,
    el=>{const i=el.querySelector('#docCod');if(i)i.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();ACTS.docVerificar();}});});
};
ACTS.docVerificar=()=>{const i=document.querySelector('#docCod'),o=document.querySelector('#docVer');if(!i||!o)return;o.innerHTML=aUnidad(verificarHtml(verificar(i.value)));};
let ocupado=false;
ACTS.docHacer=async el=>{
  if(ocupado)return;const f=el.closest('form'),modo=el.dataset.modo,tipo=el.dataset.doc;const err=f&&f.querySelector('.err');if(err)err.textContent='';
  const B=f?[...f.querySelectorAll('[data-act="docHacer"]')]:[el],txt=el.textContent;
  ocupado=true;B.forEach(b=>b.disabled=true);el.textContent=tr('Preparando…');
  try{
    let r;
    if(tipo==='banco'){const v=n=>(f.elements[n]&&f.elements[n].value||'').trim();
      const tt={nombre:v('nombre'),id:v('id'),tel:v('tel')};
      if(JSON.stringify(tt)!==JSON.stringify({nombre:'',id:'',tel:'',...titular()}))put('ajustes','finca',{...S.config,titular:tt});
      r=await informeBanco({para:v('para')});}
    else r=await certificadoLote(el.dataset.id,{destino:(f.elements.destino&&f.elements.destino.value||'').trim()});
    const ok=await enviar(r.nombre,r.buf,modo,r.titulo);
    if(ok){if(modo==='guardar'&&!window.Android)toast('PDF descargado');else if(modo!=='guardar')toast(`${tr('Código')} ${r.cod}`,3200);}
  }catch(e){console.error(e);const m=e&&e.message==='lib'?'No pude cargar el generador de PDF. Cierra y abre la app.':'No pude hacer el PDF. Revisa los datos e inténtalo otra vez.';if(err)err.textContent=tr(m);else toast(m);}
  finally{ocupado=false;B.forEach(b=>b.disabled=false);el.textContent=txt;}
};

/* página Documentos (Más) */
PAGES.documentos=()=>{
  const C=calc(),L=C.act.concat(C.cer.slice(0,8)),D=DOCS();
  const fila=(ic,t,s,attr,tono)=>masFila(mico(ic),t,s,attr,tono);
  return `${masHd('Documentos','Informes y certificados en PDF para el banco, el comprador o el matadero.')}
  <main class="bd">
   <section class="sec">${secH('Para el banco')}<div class="card rows">${fila('gasto','Informe productivo y financiero','Balance, resultados, créditos, indicadores, flujo y riesgo','data-act="f" data-f="docBanco"','t-t')}</div></section>
   <section class="sec">${secH('Certificado de lote','con código QR')}<div class="card rows">${L.length?L.map(x=>fila('doc',esc(x.l.nombre),`${pl(x.activo?x.cab:x.cab0,'cabeza','cabezas')}${x.activo?'':', vendido'}${x.retiroHasta&&x.retiroHasta>C.H?` · en retiro hasta el ${ffc(x.retiroHasta)}`:''}`,`data-act="f" data-f="docCert" data-id="${esc(x.id)}"`,'t-v')).join(''):`<p class="empty">Aún no tienes lotes.</p>`}</div></section>
   <section class="sec">${secH('Verificar')}<div class="card rows">${fila('info','Verificar un código','¿Este documento lo hice yo? ¿Qué decía?','data-act="f" data-f="docVerificar"','t-a')}</div></section>
   ${D.length?`<section class="sec">${secH('Emitidos',D.length)}<div class="card rows">${D.slice(0,12).map(d=>`<div class="row"><div class="tx"><b>${d.t==='cert'?'Certificado de lote':'Informe productivo y financiero'}</b><span>${ffc(d.f)} · <span data-no-tr>${esc([d.t==='cert'?d.n:d.para,d.cod].filter(Boolean).join(' · '))}</span></span></div></div>`).join('')}</div></section>`:''}
   <p class="hint">Los documentos salen de tus registros. Cada uno lleva un código que solo este teléfono (o tu respaldo) puede comprobar.</p>
  </main>`;
};
window.Documentos={informeBanco,certificadoLote,verificar,sha256,canon,codigo,lat,T,enviar,datosCert,textoQR,libs};
})();
