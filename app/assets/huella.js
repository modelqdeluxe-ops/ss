/* Rumentis: huella de carbono de cada lote en el corral.
   Método: IPCC 2019 Refinement, Vol. 4, Cap. 10, Nivel 2 (Tier 2), con el alimento que anotas de verdad.
   Alcance: lo que emite el animal y su estiércol mientras está en tu corral (de la puerta de entrada a la de salida).
   No incluye la producción del alimento, la cría del ternero antes de llegar ni el transporte.
   - Metano entérico (Ec. 10.21): CH4 = EB × Ym/100 ÷ 55,65 MJ/kg. EB = MS consumida × 18,45 MJ/kg.
     Ym (Cuadro 10.12): 3,0 % corral con maíz en hojuelas y 0–10 % de forraje; 4,0 % corral con 0–15 % de forraje;
     6,3 % con 15–75 % de forraje; 7,0 % con más de 75 % de forraje.
   - Metano del estiércol en corral de tierra (Cuadro 10.14, ganado no lechero de alta productividad):
     1,2 / 1,8 / 2,4 g CH4 por kg de sólidos volátiles (clima frío / templado / cálido).
     SV (Ec. 10.24) = [EB × (1 − DE/100) + UE × EB] × (1 − ceniza) ÷ 18,45; UE 0,02 con ≥ 85 % de grano, si no 0,04.
     DE (Cuadros 10.2 y 10.12): 78 % corral con poco forraje, 66 % con 15–75 %, 60 % con más forraje. Ceniza 8 %.
   - Óxido nitroso del estiércol: directo con EF3 = 0,02 kg N2O-N/kg N (corral de tierra, Cuadro 10.21);
     indirecto por volatilización (FracGas 0,30, Cuadro 10.22; EF4 0,010, Cuadro 11.3) y por lixiviación
     (FracLeach 0,035, Cuadro 10.22; EF5 0,011, Cuadro 11.3).
     N consumido (Ec. 10.32) = MS × PC/100 ÷ 6,25. N retenido (Ec. 10.33) con la ganancia y la energía neta de
     ganancia de la Ec. 10.6 (C = 1,2 toros, 1,0 castrados, 0,8 hembras).
   - Potencial de calentamiento a 100 años (IPCC AR6): CH4 biogénico 27, N2O 273. */
(function(){
'use strict';
const GWP={ch4:27,n2o:273},N2O_N=44/28;
const CLIMA={US:'templado',CA:'frio',ES:'templado',PT:'templado',FR:'templado',AR:'templado',UY:'templado',CL:'templado',ZA:'templado',NZ:'templado',AU:'templado'};
const EF_EST={frio:1.2,templado:1.8,calido:2.4};   // g CH4 por kg de SV
const clima=()=>CLIMA[S.config.pais]||'calido';
const norm=t=>String(t||'').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g,'');
const FORRAJE=/pasto|zacate|heno|ensilaje|ensilado|silo|silaje|cana|rastrojo|forraje|napier|king|taiwan|camerun|maralfalfa|capim|feno|silagem|hay|silage|grass|alfalfa|straw|paja|cascarilla|hulls?/;
// composición de una ración: materia seca, proteína y forraje (en base seca), a partir de sus ingredientes
function comp(racId){
  const r=racId&&S.raciones[racId];
  if(!r||!(r.ings||[]).some(i=>+i.p>0))return {ms:0.88,pc:13,forr:null,hojuela:false,conocida:false};
  let tot=0,ms=0,pc=0,forr=0,desc=0;let hojuela=false;
  for(const i of r.ings){const p=+i.p||0;if(!(p>0))continue;const n=norm(i.n);
    const g=typeof INGS!=='undefined'?INGS.find(x=>x.al.test(n)):null;
    const esF=g?!!g.forraje:FORRAJE.test(n);
    // sin datos del ingrediente: forraje verde ~25 % MS, lo demás seco ~88 %
    const m=g?g.ms/100:(esF&&!/heno|hay|feno|paja|straw|rastrojo|cascarilla|hulls?/.test(n)?0.25:0.88);
    const c=g?g.pc:(esF?8:12);if(!g)desc+=p;
    if(/hojuela|flak|floculad/.test(n))hojuela=true;
    tot+=p;ms+=p*m;pc+=p*m*c;if(esF)forr+=p*m;}
  if(!(tot>0)||!(ms>0))return {ms:0.88,pc:13,forr:null,hojuela:false,conocida:false};
  return {ms:ms/tot,pc:pc/ms,forr:forr/ms,hojuela,conocida:true,desconocido:desc/tot};
}
// Cuadro 10.12
function ymDe(forr,hojuela){
  if(forr==null)return {ym:6.3,de:66,ue:0.04,txt:'sin ración registrada: supuse una mezcla con 15 a 75 % de forraje'};
  if(forr<=0.10&&hojuela)return {ym:3.0,de:80,ue:0.02,txt:'corral con maíz en hojuelas y hasta 10 % de forraje'};
  if(forr<=0.15)return {ym:4.0,de:78,ue:0.02,txt:'corral con hasta 15 % de forraje'};
  if(forr<=0.75)return {ym:6.3,de:66,ue:0.04,txt:'mezcla con 15 a 75 % de forraje'};
  return {ym:7.0,de:60,ue:0.04,txt:'más de 75 % de forraje'};
}
const C_SEXO={'Machos enteros':1.2,'Novillos castrados':1.0,'Hembras':0.8,'Mixto':1.0};
const PESO_ADULTO=()=>clima()==='calido'?500:600; // peso adulto de la hembra (MW), Ec. 10.6
/* huella de un lote. extra: días de más para proyectar a la meta con el mismo consumo */
function lote(x){
  const cl=clima(),efEst=EF_EST[cl];
  let ms=0,ge=0,vs=0,ch4e=0,nIn=0,forrPond=0,msConForr=0;const sup=new Set();let sinRac=0,desc=0,kgTot=0;
  for(const a of x.g.alimento){const kg=+a.kg||0;if(!(kg>0))continue;kgTot+=kg;
    const c=comp(a.racion||x.racion);const y=ymDe(c.forr,c.hojuela);if(!c.conocida)sinRac+=kg;if(c.desconocido)desc+=kg*c.desconocido;
    const dm=kg*c.ms,g=dm*18.45;ms+=dm;ge+=g;ch4e+=g*y.ym/100/55.65;
    vs+=(g*(1-y.de/100)+y.ue*g)*(1-0.08)/18.45;nIn+=dm*c.pc/100/6.25;
    if(c.forr!=null){forrPond+=dm*c.forr;msConForr+=dm;}sup.add(y.txt);}
  if(!(ms>0))return null;
  // N retenido (Ec. 10.33 con NEg de la Ec. 10.6) día a día con el peso y la ganancia del lote
  // cabezas promedio: las muertes a mitad de camino; las ventas de un lote cerrado son al final
  const dias=Math.max(1,x.dec),cabProm=Math.max(1,x.cab0-x.bajas/2-(x.activo?x.vend/2:0)),g=Math.max(0,x.gdpTot||x.gdpUse||0);
  const Cs=C_SEXO[x.l.tipo]||1.0,MW=PESO_ADULTO();let nRet=0;
  if(g>0){const pasos=Math.min(dias,400);for(let i=0;i<pasos;i++){const bw=x.p0+g*(i+0.5)*dias/pasos;
    const neg=22.02*Math.pow(bw/(Cs*MW),0.75)*Math.pow(g,1.097);const prot=g*(268-7.03*neg/g)/1000;nRet+=Math.max(0,prot/6.25)*dias/pasos;}
    nRet*=cabProm;}
  const nEx=Math.max(0,nIn-nRet);
  const ch4m=vs*efEst/1000;
  const n2oDir=nEx*0.02*N2O_N,n2oVol=nEx*0.30*0.010*N2O_N,n2oLix=nEx*0.035*0.011*N2O_N;
  const co2={ent:ch4e*GWP.ch4,est:ch4m*GWP.ch4,n2o:(n2oDir+n2oVol+n2oLix)*GWP.n2o};co2.total=co2.ent+co2.est+co2.n2o;
  const kgGan=Math.max(0,x.kgGan),cabDias=cabProm*dias;
  if(sinRac/kgTot>0.2)sup.add('parte del alimento sin ración registrada: supuse 88 % de materia seca y 13 % de proteína');
  if(desc/kgTot>0.2)sup.add('algunos ingredientes no están en mi tabla: estimé su materia seca y proteína');
  return {x,clima:cl,dias,ms,ge,ch4Ent:ch4e,ch4Est:ch4m,n2o:n2oDir+n2oVol+n2oLix,nIn,nRet,nEx,co2,
    porCab:co2.total/Math.max(1,x.cab0-x.bajas),porKg:kgGan>0?co2.total/kgGan:null,porCabDia:co2.total/cabDias,
    forr:msConForr?forrPond/msConForr:null,ym:ch4e>0?ch4e*55.65*100/ge:null,msCabDia:ms/cabDias,kgGan,supuestos:[...sup]};
}
function finca(){
  const C=calc(),L=C.act.concat(C.cer.filter(x=>x.l.fechaCierre&&dias(x.l.fechaCierre,C.H)<=365));
  const R=L.map(lote).filter(Boolean);if(!R.length)return null;
  const t=R.reduce((s,r)=>({co2:s.co2+r.co2.total,ent:s.ent+r.co2.ent,est:s.est+r.co2.est,n2o:s.n2o+r.co2.n2o,kg:s.kg+r.kgGan}),{co2:0,ent:0,est:0,n2o:0,kg:0});
  return {R,...t,porKg:t.kg>0?t.co2/t.kg:null};
}
// qué pasa si: menos alimento por kilo ganado, menos forraje o un aditivo
function palancas(r){
  const P=[];const t=r.co2.total;
  P.push({t:'Mejorar la conversión 10 %',d:(r.co2.ent+r.co2.est+r.co2.n2o)*0.10,x:'Comen 10 % menos por cada kilo que suben: menos desperdicio, ración bien balanceada, animales sanos y vender a tiempo.'});
  if(r.forr!=null&&r.forr>0.15&&r.ym>=6)P.push({t:'Bajar el forraje a menos de 15 % en la terminación',d:r.co2.ent*(1-4.0/r.ym),x:'Con poco forraje el rumen produce menos metano (Ym de 6,3 a 4,0 %). Cámbialo de a poco y con tu nutricionista: más grano cuesta y exige adaptar bien.'});
  P.push({t:'Aditivo que reduce el metano (3-NOP)',d:r.co2.ent*0.30,x:'En estudios publicados baja alrededor de 30 % el metano entérico en engorde. Solo donde esté aprobado y con tu veterinario.'});
  return P.map(p=>({...p,pct:t>0?p.d/t:0}));
}
const tco2=kg=>kg>=1000?`${nf(kg/1000,1)} t CO2e`:`${nf(kg)} ${g5('kg CO2e')}`;
function loteHtml(x){
  const r=lote(x);if(!r)return {html:'Todavía no hay alimento anotado en este lote. Con las entregas de alimento calculo su huella de carbono.'};
  const pa=palancas(r);
  return {html:`<div class="rp-head"><div class="rp-big" style="color:var(--anil5)">${r.porKg!=null?nf(r.porKg,1):'–'}</div><div><b>${g5('kg CO2e por kg ganado')}</b><span>${esc(x.l.nombre)}, ${pl(r.dias,'día','días')} en el corral</span></div></div>
   ${tabla(['',g5('kg CO2e'),'Parte'],[['Metano del rumen',nf(r.co2.ent),pctH(r.co2.ent/r.co2.total)],['Metano del estiércol',nf(r.co2.est),pctH(r.co2.est/r.co2.total)],['Óxido nitroso del estiércol',nf(r.co2.n2o),pctH(r.co2.n2o/r.co2.total)],['<b>Total</b>',`<b>${nf(r.co2.total)}</b>`,'100 %']])}
   <p>Por cabeza: <b>${nf(r.porCab)} ${g5('kg CO2e')}</b>. Al día por cabeza: <b>${nf(r.porCabDia,1)} ${g5('kg CO2e')}</b>, comiendo ${nf(r.msCabDia,1)} kg de materia seca.</p>
   <p class="rh">Cómo bajarla</p><ul class="rp-lista">${pa.map(p=>`<li><b>${p.t}</b>: unos ${nf(p.d)} ${g5('kg CO2e')} menos (${pctH(p.pct)}). ${p.x}</li>`).join('')}</ul>
   <p class="rs">Método IPCC 2019 Nivel 2 con el alimento que anotaste: ${esc(r.supuestos.join('; '))}. Incluye solo lo que emiten el animal y su estiércol en el corral; no incluye la producción del alimento ni la cría antes de llegar.</p>`};
}
const pctH=v=>`${nf((v||0)*100,0)} %`;
function fincaHtml(){
  const F=finca();if(!F)return {html:'Todavía no hay alimento anotado. Con las entregas de alimento calculo la huella de carbono de cada lote.'};
  const rows=F.R.map(r=>[`<b>${esc(r.x.l.nombre)}</b>${r.x.activo?'':' (vendido)'}`,nf(r.co2.total/1000,1),r.porKg!=null?nf(r.porKg,1):'–',nf(r.porCabDia,1)]);
  const mejor=F.R.filter(r=>r.porKg!=null).sort((a,b)=>a.porKg-b.porKg);
  return {html:`<b>Huella de carbono de tu engorde</b><br>Tus lotes de los últimos 12 meses emitieron ${tco2(F.co2)} y subieron ${nf(F.kg)} kg: ${F.porKg!=null?nf(F.porKg,1):'–'} ${g5('kg CO2e por kg ganado')}.`+
    tabla(['Lote','t CO2e',g5('por kg ganado'),'por cabeza al día'],rows).replace('class="tb"','class="tb tw"')+`<p class="rs">${g5('Por kg ganado y por cabeza al día: kg CO2e.')}</p>`+
    (mejor.length>=2?`<p>${esc(mejor[0].x.l.nombre)} es el lote con menos emisión por kilo ganado (${nf(mejor[0].porKg,1)}); ${esc(mejor[mejor.length-1].x.l.nombre)} el de más (${nf(mejor[mejor.length-1].porKg,1)}). Engordar más rápido y con mejor conversión baja la huella y el costo a la vez.</p>`:'')+
    `<p class="rs">Método IPCC 2019 Nivel 2 con el alimento que anotaste. Solo lo que emiten el animal y su estiércol en el corral (metano del rumen, metano y óxido nitroso del estiércol), en CO2 equivalente a 100 años (IPCC AR6). No incluye la producción del alimento, la cría antes de llegar ni el transporte.</p>`};
}
window.Huella={lote,finca,palancas,loteHtml,fincaHtml,comp,ymDe,tco2};
})();
