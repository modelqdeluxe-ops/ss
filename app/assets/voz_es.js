/* Texto a fonemas en español latinoamericano para la voz neuronal de Rumi.
   Escrito para Rumentis (sin espeak): reglas de la ortografía del español, sílabas, acento,
   diptongos, alófonos de b, d, g (β, ð, ɣ), asimilación de la n, la r fuerte y el seseo.
   Produce los mismos símbolos IPA con los que se entrenaron las voces Piper en español. */
(function(g){
'use strict';
const SIN_ACENTO=new Set('a al con de del des e el en la las le les lo los me mi mis nos o os por que quien se si sin su sus te tras tu tus u y'.split(' '));
const SECUNDARIO=new Set('ante aunque bajo como cuando cuanto desde donde entre hacia hasta mientras para pero porque sobre'.split(' '));
const EXCEP={'ser':'sˈer','adn':'ˌaðˌeˈɛne','méxico':'mˈɛxiko','mexicano':'mexikˈano','texas':'tˈexas','oaxaca':'waxˈaka','whisky':'wˈiski','ok':'okˈeɪ'};
const FUERTE='aeoáéóíú',ACENT='áéíóú',QUITA={'á':'a','é':'e','í':'i','ó':'o','ú':'u','ü':'u'};
const esV=c=>'aeiouáéíóúü'.includes(c);

/* ---------- números en palabras ---------- */
const U0=['cero','uno','dos','tres','cuatro','cinco','seis','siete','ocho','nueve','diez','once','doce','trece','catorce','quince','dieciséis','diecisiete','dieciocho','diecinueve','veinte','veintiuno','veintidós','veintitrés','veinticuatro','veinticinco','veintiséis','veintisiete','veintiocho','veintinueve'];
const DEC=['','','','treinta','cuarenta','cincuenta','sesenta','setenta','ochenta','noventa'];
const CEN=['','ciento','doscientos','trescientos','cuatrocientos','quinientos','seiscientos','setecientos','ochocientos','novecientos'];
function menosDeMil(n){
  if(n<30)return U0[n];if(n<100)return DEC[Math.floor(n/10)]+(n%10?' y '+U0[n%10]:'');
  if(n===100)return 'cien';return CEN[Math.floor(n/100)]+(n%100?' '+menosDeMil(n%100):'');
}
function numero(n){
  n=Math.floor(Math.abs(n));if(n<1000)return menosDeMil(n);
  if(n<1e6){const m=Math.floor(n/1000),r=n%1000;return (m===1?'mil':menosDeMil(m).replace(/uno$/,'ún')+' mil')+(r?' '+menosDeMil(r):'');}
  const M=Math.floor(n/1e6),r=n%1e6;return (M===1?'un millón':numero(M).replace(/uno$/,'ún')+' millones')+(r?' '+numero(r):'');
}
function numeros(t){
  t=t.replace(/(\d+),(\d{2})\s+(lempiras?|d[oó]lares|pesos|quetzales|colones|c[oó]rdobas|reales|soles|euros|bolivianos|guaran[ií]es)/g,(m,a,c,mon)=>numero(+a)+' '+mon+' con '+numero(+c));
  return t.replace(/(\d+)[,.](\d+)/g,(m,a,b)=>numero(+a)+' coma '+(b.length<=2&&b[0]!=='0'?numero(+b):b.split('').map(d=>U0[+d]).join(' ')))
          .replace(/\d+/g,m=>m.length>9?m.split('').map(d=>U0[+d]).join(' '):numero(+m));
}

/* ---------- una palabra: grafemas, sílabas y acento ---------- */
// cada unidad: {c:consonante} o {v:vocal, ac:acentuada, deb:débil}
function unidades(w){
  const u=[];let i=0;const s=w;
  const V=(ch,extra={})=>u.push({v:QUITA[ch]||ch,ac:ACENT.includes(ch),deb:(ch==='i'||ch==='u'||ch==='ü'),...extra});
  while(i<s.length){
    const c=s[i],n=s[i+1],n2=s[i+2];
    if(esV(c)){
      // hi/hu + vocal: la h no suena y la vocal es semivocal
      V(c);i++;continue;}
    if(c==='h'){if(i>0&&esV(s[i-1])&&n&&esV(n)){i++;V(n,{hiato:true});i++;continue;}i++;continue;}
    if(c==='c'&&n==='h'){u.push({c:'tʃ'});i+=2;continue;}
    if(c==='l'&&n==='l'){u.push({c:'LL'});i+=2;continue;}
    if(c==='r'&&n==='r'){u.push({c:'r'});i+=2;continue;}
    if(c==='q'&&n==='u'){u.push({c:'k'});i+=2;continue;}
    if(c==='g'&&n==='u'&&(n2==='e'||n2==='i'||n2==='é'||n2==='í')){u.push({c:'G'});i+=2;continue;}
    if(c==='g'&&n==='ü'){u.push({c:'G'});V('ü',{hiato:true});i+=2;continue;}
    if(c==='g'&&(n==='e'||n==='i'||n==='é'||n==='í')){u.push({c:'x'});i++;continue;}
    if(c==='c'&&(n==='e'||n==='i'||n==='é'||n==='í')){u.push({c:'s'});i++;continue;}
    if(c==='c'&&n==='c'){u.push({c:'k'});i++;continue;}
    if(c==='x'){u.push({c:'k'});u.push({c:'s'});if(n==='c'&&'eiéí'.includes(n2||''))i++;i++;continue;}
    if(c==='y'){
      if(n&&esV(n)&&n!=='y'){u.push({c:'Y'});i++;continue;}
      // y final o entre consonantes: vocal i
      u.push({v:'i',ac:false,deb:true,yfin:true});i++;continue;}
    const M={b:'B',v:'B',d:'D',g:'G',j:'x',z:'s',c:'k',k:'k',q:'k',ñ:'ɲ',w:'w',f:'f',l:'l',m:'m',n:'n',p:'p',r:'R1',s:'s',t:'t'};
    u.push({c:M[c]||''});i++;
  }
  return u.filter(x=>x.v||x.c);
}
const INSEP=new Set(['pr','br','tr','dr','kr','Gr','fr','pl','Bl','kl','Gl','fl','BR1','DR1','GR1','pR1','tR1','kR1','fR1','Bl','Dl']);
function silabas(u){
  // núcleos: grupos de vocales que forman diptongo o triptongo
  const S=[];let cur={on:[],nuc:[],co:[]};const out=[];
  // primero marcamos núcleos
  const idx=[];for(let i=0;i<u.length;i++)if(u[i].v)idx.push(i);
  if(!idx.length)return [{on:u,nuc:[],co:[]}];
  // agrupar vocales contiguas en núcleos
  const grupos=[];let i=0;
  while(i<u.length){
    if(!u[i].v){i++;continue;}
    let j=i;const g=[u[i]];
    while(j+1<u.length&&u[j+1].v){
      const a=u[j],b=u[j+1];
      const fuerteA=!a.deb||a.ac,fuerteB=!b.deb||b.ac;
      if(b.hiato||(fuerteA&&fuerteB)){break;}           // hiato
      // i o u después de lu, de r inicial o de un grupo con líquida (pr, bl…) suena completa: lu-e-go, ri-es-go, pru-e-ba
      if(j===i&&a.deb&&!a.ac&&fuerteB){const c1=u[i-1],c2=u[i-2];
        if(c1&&c1.c&&((c1.c==='l'&&a.v==='u'&&!(c2&&c2.c))||(c1.c==='R1'&&(i===1||(c2&&c2.c)))||(c1.c==='l'&&c2&&c2.c)||(c1.c==='r'&&i===1)))a.lleno=true;}
      g.push(b);j++;
    }
    grupos.push({ini:i,fin:j,g});i=j+1;
  }
  // repartir consonantes entre núcleos
  const sil=grupos.map(x=>({on:[],nuc:x.g,co:[]}));
  sil[0].on=u.slice(0,grupos[0].ini);
  for(let k=0;k<grupos.length-1;k++){
    const cs=u.slice(grupos[k].fin+1,grupos[k+1].ini);
    let corte;
    if(cs.length<=1)corte=0;
    else{const par=cs.slice(-2).map(x=>x.c).join('');corte=INSEP.has(par)?cs.length-2:cs.length-1;}
    sil[k].co=cs.slice(0,corte);sil[k+1].on=cs.slice(corte);
  }
  sil[sil.length-1].co=u.slice(grupos[grupos.length-1].fin+1);
  return sil;
}
function acento(sil,w){
  for(let k=0;k<sil.length;k++)if(sil[k].nuc.some(v=>v.ac))return k;
  const ult=w[w.length-1];
  if(sil.length===1)return 0;
  const llana='aeiouns'.includes(ult)&&!(ult==='y');
  return llana?sil.length-2:sil.length-1;
}

/* ---------- de palabra a fonemas, con el contexto de la frase ---------- */
const NASAL=new Set(['m','n','ŋ','ɲ']);
function palabra(w,prev){
  if(EXCEP[w])return {ph:EXCEP[w],ult:EXCEP[w].slice(-1)};
  // adverbios en -mente: dos acentos, el del adjetivo y el de -mente
  if(w.length>7&&w.endsWith('mente')){const a=palabra(w.slice(0,-5),prev),b=palabra('mente',a.ult);return {ph:a.ph.replace(/ˌ/g,'')+b.ph,ult:b.ult};}
  const u=unidades(w),sil=silabas(u);
  const nuc=sil.filter(s=>s.nuc.length).length;
  const tono=SIN_ACENTO.has(w)?-1:acento(sil,w);
  const secund=SECUNDARIO.has(w);
  let out='';let p=prev; // p: último fonema emitido (o '#' en pausa)
  const emit=x=>{out+=x;p=x.slice(-1);};
  const flat=[];sil.forEach((s,k)=>{s.on.forEach(c=>flat.push({...c,k}));s.nuc.forEach(v=>flat.push({...v,k,nuc:true,o:v}));s.co.forEach(c=>flat.push({...c,k,coda:true}));});
  // secundario en palabras largas: dos o más sílabas antes del acento
  const secs=new Set();if(!secund&&tono>=2)for(let k=0;k<=tono-2;k+=2)secs.add(k);
  for(let i=0;i<flat.length;i++){
    const x=flat[i],sig=flat[i+1],ini=i===0;
    if(x.v){
      const s=sil[x.k],pos=s.nuc.indexOf(x.o),fuerte=s.nuc.reduce((b,v,j)=>b<0&&(!v.deb||v.ac)?j:b,-1);
      const cab=fuerte>=0?fuerte:(s.nuc.length>1?(s.nuc[s.nuc.length-1].yfin&&s.nuc.length===2?0:s.nuc.length-1):0);
      if(pos===cab){
        if(x.k===tono)emit(secund?'ˌ':'ˈ');else if(secs.has(x.k)&&nuc>=3)emit('ˌ');
        let v=x.v;
        // e abierta: sílaba tónica cerrada por n seguida de consonante, o antes de j
        if(v==='e'&&x.k===tono&&!secund){const c1=s.co[0],nx=flat[i+1];if(c1&&c1.c==='n'&&flat[i+2]&&flat[i+2].c&&flat[i+1]===flat.find(z=>z===flat[i+1]))v='ɛ';}
        emit(v);
      }else if(pos<cab){if(x.lleno){if(x.k===tono||secs.has(x.k)){}emit(x.v);}else emit(x.v==='i'?'j':'w');}
      else{emit(x.yfin&&s.nuc[cab].v==='u'?'j':x.v==='i'?'ɪ':'ʊ');}
      continue;
    }
    let c=x.c;const tras=p;
    const nasalAntes=NASAL.has(tras),pausa=tras==='#';
    if(c==='B')c=pausa||nasalAntes||(x.coda&&sig&&sig.c==='p')?'b':'β';
    else if(c==='p'&&sig&&sig.c==='t')c='pː';
    else if(c==='D')c=pausa||nasalAntes?'d':(i===flat.length-1&&!/[aeiou]dad$/.test(w)?'d':'ð');
    else if(c==='G')c=pausa||nasalAntes?'ɡ':'ɣ';
    else if(c==='R1')c=(ini||tras==='n'||tras==='l'||tras==='s')?'r':'ɾ';
    else if(c==='Y')c=(sig&&sig.v)?(tras==='n'||tras==='ɲ'?'':'ʝ'):'i';
    else if(c==='LL')c=ini||pausa?'ʝ':(esV(tras)||'ɪʊjwˈˌ'.includes(tras)?'jj':'ʝ');
    else if(c==='n'){
      // la n se asimila a la consonante que sigue (también en la palabra siguiente, ver abajo)
      const nx=sig&&sig.c;
      if(nx==='B'||nx==='p')c='m';else if(nx==='G')c='ŋ';else if(nx==='Y')c='ɲ';
    }
    if(c)emit(c);
  }
  return {ph:out,ult:p};
}

function fonemas(texto){
  let t=' '+numeros(String(texto||'').toLowerCase())+' ';
  t=t.replace(/[“”"«»()\[\]]/g,',').replace(/[¿¡]/g,' ').replace(/[—–]/g,', ').replace(/\s+/g,' ');
  const frases=[];let cur='',prev='#';
  const toks=t.match(/[a-záéíóúüñ]+|[.?!]|[,;:]/g)||[];
  for(let i=0;i<toks.length;i++){
    const tk=toks[i];
    if(/^[.?!]$/.test(tk)){cur=cur.trimEnd()+tk;if(cur.replace(/[.?!\s]/g,''))frases.push(cur);cur='';prev='#';continue;}
    if(/^[,;:]$/.test(tk)){cur=cur.trimEnd()+tk+' ';prev='#';continue;}
    let r=palabra(tk,prev);
    // asimilación de la n final a la palabra siguiente
    const nx=toks[i+1];
    // la n final se une a la palabra siguiente: m ante p, b, v; ŋ ante g fuerte
    if(r.ph.endsWith('n')&&nx&&/^[a-z]/.test(nx)){if(/^[bpv]/.test(nx))r.ph=r.ph.slice(0,-1)+'m';else if(/^g[aouülr]/.test(nx))r.ph=r.ph.slice(0,-1)+'ŋ';}
    // d final de -dad: suave si sigue otra palabra, firme antes de una pausa
    if(/[aeiou]dad$/.test(tk)&&r.ph.endsWith('ð')&&!(nx&&/^[a-záéíóúüñ]/.test(nx)))r.ph=r.ph.slice(0,-1)+'d';
    cur+=r.ph+' ';prev=r.ult;
  }
  if(cur.trim())frases.push(cur.trim());
  return frases.map(f=>f.replace(/\s+([,;:.?!])/g,'$1').replace(/\s+/g,' ').trim());
}
g.VozEs={fonemas,numeros,numero};
})(typeof window!=='undefined'?window:globalThis);
