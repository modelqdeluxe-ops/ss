/* Conocimiento de referencia de Rumi (Rumentis 4.0): tablas que se calculan o se consultan.
   - Requerimientos por peso, ganancia, raza y clima (NASEM 2016, con ajustes de raza y de calor).
   - Razas para engorde: ganancia en corral, rendimiento, calor, carne y temperamento.
   - Precios de referencia por país (rangos orientativos) y comparación con tu precio.
   - Calendario sanitario según tu región y su clima, con las épocas del año de tu país.
   Todo sin internet. */
(function(){
'use strict';
const tablaW=(h,r)=>tabla(h,r).replace('class="tb"','class="tb tw"');
if(!window.RumiMenu)return;
const cl=(v,a,b)=>Math.max(a,Math.min(b,v));

/* ================= requerimientos por raza y clima ================= */
/* mantenimiento relativo al europeo (NASEM 2016: cebú −10 %; lecheros +20 %) y ganancia típica en corral */
const RAZAS_G=[
 {k:'cebu',n:'Cebú (Brahman, Nelore, Gyr, Indubrasil)',m:0.90,g:[1.0,1.4],dmi:0.97},
 {k:'cruce',n:'Cruce cebú por europeo (F1, Brangus, Simbrah)',m:0.95,g:[1.3,1.7],dmi:1.0},
 {k:'euro',n:'Europeo de carne (Angus, Charolais, Hereford)',m:1.0,g:[1.4,1.9],dmi:1.02},
 {k:'leche',n:'Macho lechero (Holstein, Pardo Suizo)',m:1.2,g:[1.2,1.5],dmi:1.04}
];
/* clima: ajuste de consumo (NASEM: sobre 35 °C baja 10 a 35 %), del mantenimiento por jadeo y del agua */
const CLIMAS=[
 {k:'fresco',n:'Fresco (menos de 20 °C)',t:16,dmi:1.03,m:1.0,agua:4.5},
 {k:'templado',n:'Templado (20 a 27 °C)',t:24,dmi:1.0,m:1.0,agua:5.5},
 {k:'calido',n:'Cálido (27 a 32 °C)',t:30,dmi:0.93,m:1.07,agua:7.0},
 {k:'muycalido',n:'Muy caliente (más de 32 °C)',t:35,dmi:0.85,m:1.14,agua:8.5}
];
function reqRC(bw,raza,clima,gdp){
  const R=requer(bw,gdp),r=RAZAS_G.find(z=>z.k===raza)||RAZAS_G[1],c=CLIMAS.find(z=>z.k===clima)||CLIMAS[2];
  const dmi=R.dmi*r.dmi*c.dmi,nem=R.nem*r.m*c.m;
  return {dmi,cms:dmi/bw*100,nem,neg:R.neg,pc:R.pc,ca:R.ca,p:R.p,agua:dmi*c.agua,
    // energía neta de ganancia que debe traer cada kg de ración para esa ganancia (tras cubrir mantenimiento)
    dens:R.neg/Math.max(0.5,dmi-nem/2.0)};
}
function tablaReq(raza,clima){
  const r=RAZAS_G.find(z=>z.k===raza),c=CLIMAS.find(z=>z.k===clima),gdp=(r.g[0]+r.g[1])/2*(clima==='muycalido'?0.85:clima==='calido'?0.93:1);
  const pesos=U()==='lb'?[450,550,650,750,850,950,1050,1150].map(v=>v/FK):[200,250,300,350,400,450,500,550];
  const F=pesos.map(bw=>({bw,...reqRC(bw,raza,clima,gdp)}));
  return {html:`<b>${esc(r.n)}</b><br><span class="rs">${esc(c.n)} · ganancia de ${nf(gdp,2)} kg por día</span>
    ${tablaW(['Peso','Come','Prot.','Agua','Mcal'],F.map(o=>[g5(`${nf(Math.round(W(o.bw)))} ${UW()}`),g5(`${nf(W(o.dmi),1)} ${UW()}`),`${nf(o.pc,1)} %`,`${nf(o.agua)} L`,`${nf(o.nem+o.neg,1)}`]))}
    <p class="rs"><b>Come</b>: kilos de materia seca al día; tal como se sirve es más si la ración trae agua (ensilaje, pasto verde). <b>Prot.</b>: proteína cruda de la ración en base seca. <b>Agua</b>: litros al día con ese clima. <b>Mcal</b>: megacalorías de energía neta al día para mantenerse y ganar (NASEM 2016). Cebú necesita 10 % menos para mantenerse; un macho lechero, 20 % más. Con calor comen menos y gastan más en jadear: por eso bajan la ganancia.</p>
    <p class="rs">Calcio de ${nf(F[0].ca,2)} a ${nf(F[F.length-1].ca,2)} % y fósforo de ${nf(F[0].p,2)} a ${nf(F[F.length-1].p,2)} % de la ración. Para una dieta con tus ingredientes usa Formular dieta.</p>`,
    btns:[['Formular una dieta',{t:'go',go:'#formular'}]]};
}

/* ================= razas para engorde ================= */
/* ganancia en corral en el trópico (kg/día), rendimiento en canal (%), calor, carne (terneza y marmoleo), temperamento (5 = manso) */
const RAZAS=[
 ['Brahman','cebú',[1.1,1.4],[55,58],5,2,2,'La base del trópico: aguanta calor, garrapata y pasto pobre. Carne menos tierna; más nervioso.'],
 ['Nelore','cebú',[1.0,1.3],[55,57],5,2,2,'Muy rústico y de buena canal magra. Es la raza de Brasil.'],
 ['Gyr y Guzerat','cebú',[0.9,1.2],[53,55],5,2,3,'Más lecheros; engordan más lento que el Brahman.'],
 ['Indubrasil','cebú',[1.0,1.3],[54,56],5,2,3,'Rústico, de huesos largos; rinde algo menos.'],
 ['Brangus','compuesta',[1.3,1.6],[57,59],4,3,3,'3/8 cebú y 5/8 Angus: buena ganancia y carne con más marmoleo, aguanta calor.'],
 ['Simbrah','compuesta',[1.3,1.6],[57,60],4,3,3,'Simmental por Brahman: grande, gana rápido, buena canal.'],
 ['Beefmaster','compuesta',[1.3,1.5],[57,59],4,3,3,'Muy fértil y rústica; buena para cruces.'],
 ['Santa Gertrudis','compuesta',[1.2,1.5],[56,58],4,3,3,'Shorthorn por Brahman, rojo, adaptado al calor.'],
 ['Senepol','adaptada',[1.2,1.5],[57,59],5,4,4,'Europea adaptada al trópico, pelo corto (slick), mansa y de carne tierna.'],
 ['Romosinuano y criollos','adaptada',[1.0,1.3],[55,57],5,4,4,'Criollos del trópico: mansos, rústicos y de carne tierna; ideales para cruzar con cebú.'],
 ['Angus','europea',[1.4,1.8],[58,62],2,5,4,'La de mejor marmoleo. En el trópico sufre calor: mejor en cruce.'],
 ['Hereford','europea',[1.3,1.6],[57,60],2,4,4,'Mansa y precoz; sensible a ojo rosado y cáncer de ojo con mucho sol.'],
 ['Charolais','europea',[1.5,1.9],[60,63],2,3,3,'Mucho músculo y ganancia; necesita buena comida y sombra.'],
 ['Simmental','europea',[1.4,1.8],[59,62],3,3,4,'Grande y de rápido crecimiento; base del Simbrah.'],
 ['Limousin','europea',[1.3,1.6],[61,64],2,3,2,'El mayor rendimiento en canal; carne magra.'],
 ['F1 cebú por europeo','cruce',[1.3,1.7],[57,60],4,3,3,'El vigor híbrido da 10 a 20 % más ganancia que sus padres en el trópico.'],
 ['Holstein y Pardo Suizo machos','lechera',[1.2,1.5],[52,56],2,3,4,'Baratos al comprar; rinden menos en canal y necesitan más comida para mantenerse.']
];
const estrellas=n=>'●'.repeat(n)+'○'.repeat(5-n);
function razasHtml(){
  return {html:`<b>Razas para engorde</b>${tablaW(['Raza','Gana por día','Rinde','Calor'],RAZAS.map(r=>[`<b>${esc(r[0])}</b><br><small>${esc(r[1])}</small>`,`${nf(r[2][0],1)} a ${nf(r[2][1],1)} kg`,`${r[3][0]} a ${r[3][1]} %`,`<span style="letter-spacing:1px">${estrellas(r[4])}</span>`]))}
    <p class="rs">Ganancia en corral con buena ración en el trópico; en pastoreo es la mitad o menos. <b>Rinde</b>: rendimiento en canal. <b>Calor</b>: tolerancia al calor (5 puntos es la mejor).</p>
    <p class="rh">Carne y manejo</p>${tablaW(['Raza','Carne','Mansedumbre'],RAZAS.map(r=>[esc(r[0]),`<span style="letter-spacing:1px">${estrellas(r[5])}</span>`,`<span style="letter-spacing:1px">${estrellas(r[6])}</span>`]))}
    <p class="rs">${RAZAS.map(r=>`<b>${esc(r[0])}</b>: ${esc(r[7])}`).join('<br>')}</p>
    <p class="rs">Son rangos típicos de la literatura y de corrales de la región; tu manejo, la edad al comprar y la ración pesan más que la raza. Compara con tus propios lotes en Análisis de Rumi, «¿Qué raza me rinde más?».</p>`,
    btns:[['¿Qué raza me rinde más a mí?',{t:'fn',fn:'razaMia'}]]};
}

/* ================= precios de referencia por país ================= */
/* rangos orientativos de 2025 a mediados de 2026 (subastas, plantas y reportes públicos). Cambian cada semana. */
const PRECIOS={
 HN:{m:'L',u:'lb',gordo:[32,42],flaco:[36,46],maiz:[8,12],fuente:'Subastas de Olancho, Choluteca y plantas empacadoras'},
 GT:{m:'Q',u:'lb',gordo:[9,12],flaco:[10,13],maiz:[2.4,3.4],fuente:'Subastas y rastros de la costa sur y Petén'},
 SV:{m:'$',u:'lb',gordo:[1.0,1.3],flaco:[1.1,1.45],maiz:[0.3,0.42],fuente:'Rastros y ferias de ganado'},
 NI:{m:'C$',u:'lb',gordo:[36,46],flaco:[40,52],maiz:[10,14],fuente:'Plantas exportadoras y subastas'},
 CR:{m:'₡',u:'kg',gordo:[1250,1600],flaco:[1350,1800],maiz:[190,260],fuente:'Subastas ganaderas (novillo gordo en pie)'},
 PA:{m:'B/.',u:'lb',gordo:[0.9,1.2],flaco:[1.0,1.35],maiz:[0.3,0.4],fuente:'Subastas de Chiriquí, Veraguas y Los Santos'},
 DO:{m:'RD$',u:'lb',gordo:[95,125],flaco:[100,135],maiz:[20,28],fuente:'Ferias y mataderos'},
 MX:{m:'$',u:'kg',gordo:[45,62],flaco:[55,75],maiz:[5,7],fuente:'SNIIM, subastas del sureste y norte'},
 US:{m:'$',u:'lb',gordo:[1.9,2.4],flaco:[2.9,3.6],maiz:[0.07,0.09],fuente:'USDA AMS: novillo gordo (fed steer) y novillo de 750 lb (feeder)'},
 CO:{m:'$',u:'kg',gordo:[8000,10500],flaco:[9000,12000],maiz:[1300,1700],fuente:'Subastas de Córdoba, Meta y Antioquia'},
 EC:{m:'$',u:'lb',gordo:[0.95,1.25],flaco:[1.0,1.35],maiz:[0.18,0.25],fuente:'Ferias de la costa y la Amazonía'},
 PE:{m:'S/',u:'kg',gordo:[8,11],flaco:[9,12],maiz:[1.2,1.6],fuente:'Camales y ferias'},
 BO:{m:'Bs',u:'kg',gordo:[14,19],flaco:[16,21],maiz:[1.5,2.2],fuente:'Frigoríficos de Santa Cruz y Beni'},
 BR:{m:'R$',u:'arrb',gordo:[280,340],flaco:[300,380],maiz:[1.0,1.4],fuente:'Indicador del boi gordo (arroba de 15 kg de canal) y bezerro',nota:'En Brasil la arroba del boi gordo es de canal (carcaça), no en pie.'},
 PY:{m:'US$',u:'kg',gordo:[1.7,2.1],flaco:[1.9,2.4],maiz:[0.15,0.2],fuente:'Frigoríficos (novillo al gancho llevado a pie)'},
 UY:{m:'US$',u:'kg',gordo:[2.2,2.8],flaco:[2.6,3.3],maiz:[0.2,0.26],fuente:'INAC y ferias (novillo gordo, en pie)'},
 AR:{m:'US$',u:'kg',gordo:[1.9,2.6],flaco:[2.2,3.0],maiz:[0.15,0.2],fuente:'Mercado Agroganadero de Cañuelas (novillo en pie, llevado a dólares)',nota:'En Argentina el precio en pesos cambia muy rápido con la inflación; compara en dólares.'},
 CL:{m:'$',u:'kg',gordo:[1900,2400],flaco:[2100,2700],maiz:[240,320],fuente:'Ferias de la zona sur'},
 AU:{m:'A$',u:'kg',gordo:[3.0,4.2],flaco:[3.4,4.8],maiz:[0.3,0.4],fuente:'MLA (precio en pie)'},
 ZA:{m:'R',u:'kg',gordo:[34,44],flaco:[38,50],maiz:[3.5,5],fuente:'RMIS y subastas'}
};
const UN={lb:'libra',kg:'kilo',arrb:'arroba de canal'};
function preciosHtml(){
  const pais=S.config.pais,mi=PRECIOS[pais];
  let comp='';
  if(mi){const pv=+S.config.precioVentaKg||0,conv=mi.u==='lb'?1/FK:mi.u==='arrb'?15:1;const mio=pv*conv;
    const [a,b]=mi.gordo;const mismaMon=(S.config.moneda||'')===mi.m||(mi.m==='$'&&/\$/.test(S.config.moneda||''));
    if(pv&&mismaMon&&mi.u!=='arrb')comp=`<p class="rs">Tu precio de venta es <b>${g5(`${esc(mi.m)} ${nf(mio,mio<20?2:0)} por ${UN[mi.u]}`)}</b>: ${mio<a*0.97?'<b style="color:var(--rojo)">está por debajo</b> de la referencia. Pregunta en otras plantas o subastas antes de vender.':mio>b*1.03?'<b style="color:var(--verde)">está por encima</b> de la referencia. Buen precio: asegúralo con tu comprador.':'está <b>dentro</b> de la referencia.'}</p>`;}
  const r2=([a,b],m)=>{const d=a%1||b%1?2:0;return `${esc(m)} ${nf(a,d)} a ${nf(b,d)}`;};
  const fila=(k,o)=>[`<b>${esc((PAISES.find(p=>p[0]===k)||[k,k])[1])}</b><br><small>${g5('por '+UN[o.u])}</small>`,g5(r2(o.gordo,o.m)),g5(r2(o.flaco,o.m))];
  const orden=Object.keys(PRECIOS).sort((a,b)=>(b===pais)-(a===pais));
  return {html:`<b>Precios de referencia del ganado</b>${mi?`<p class="rs" style="margin-top:2px">Tu país primero. Fuente: ${esc(mi.fuente)}.${mi.nota?' '+esc(mi.nota):''}</p>`:''}
    ${tablaW(['País','Gordo','Para engordar'],orden.map(k=>fila(k,PRECIOS[k])))}${comp}
    ${mi?`<p class="rs">Maíz amarillo en tu país: ${g5(`${esc(mi.m)} ${nf(mi.maiz[0],2)} a ${nf(mi.maiz[1],2)} por ${UN[mi.u==='arrb'?'kg':mi.u]}`)}.</p>`:''}
    <p class="rs"><b>Son rangos orientativos</b> de 2025 a mediados de 2026, tomados de subastas, plantas y reportes públicos. El precio cambia cada semana y según peso, raza, sexo y región. Úsalos para saber si una oferta está muy fuera de lo normal, no para cerrar un negocio: confirma siempre en tu mercado.</p>`,
    btns:[['Cambiar mi precio de venta',{t:'go',go:'#mas/config'}]]};
}

/* ================= calendario sanitario por región ================= */
/* región y épocas de lluvia de cada país (meses 1 a 12) */
const REG_PAIS={HN:['th',[5,10]],GT:['th',[5,10]],SV:['ts',[5,10]],NI:['th',[5,11]],CR:['th',[5,11]],PA:['th',[5,12]],BZ:['th',[6,12]],DO:['th',[5,11]],
 MX:['sub',[6,10]],US:['tem',null],CA:['tem',null],CO:['th',[4,11]],VE:['th',[5,11]],EC:['th',[1,5]],PE:['th',[12,4]],BO:['sub',[11,4]],BR:['th',[10,4]],PY:['sub',[10,4]],
 UY:['tem',null],AR:['tem',null],CL:['tem',null],ES:['tem',null],PT:['tem',null],FR:['tem',null],AU:['ari',[11,3]],NZ:['tem',null],ZA:['sub',[10,3]],PH:['th',[6,11]],IN:['ts',[6,9]]};
const SUR=new Set(['AR','UY','CL','AU','NZ','ZA','PY','BR','BO']);
const REGIONES={
 th:{n:'Trópico húmedo',x:'Calor y humedad casi todo el año, lluvias largas. Los problemas grandes son garrapatas y enfermedades que transmiten, tórsalo, parásitos, cojeras en lodo y estrés por calor.'},
 ts:{n:'Trópico seco',x:'Una época seca larga y dura. Los problemas son la falta de agua y de forraje, el polvo con neumonías, la falta de vitamina A y el calor fuerte.'},
 sub:{n:'Subtrópico',x:'Veranos calientes y lluviosos, inviernos suaves. Garrapata en verano, neumonías con los cambios de temperatura.'},
 tem:{n:'Templado',x:'Cuatro estaciones. El complejo respiratorio en otoño e invierno es el problema número uno; en verano, moscas y ojo rosado; en invierno, estrés por frío y barro.'},
 ari:{n:'Árido y semiárido',x:'Poca lluvia, mucho calor y agua con sales. Cuida la calidad del agua, el polvo y el calor.'}
};
/* plan base de ingreso (todas las regiones) */
const INGRESO=[
 ['Día 0 (llegada)','Agua limpia, heno y descanso. Identificar y pesar después de 12 a 24 horas.'],
 ['Día 1','Vacuna clostridial polivalente (7 u 8 vías). Vacuna respiratoria viral (IBR, DVB, PI3 y VRSB) y contra Mannheimia si vienen de subasta o viaje largo.'],
 ['Día 1','Desparasitante interno de amplio espectro y control de parásitos externos. Vitaminas A, D y E.'],
 ['Día 21 a 30','Refuerzo de la clostridial y de la respiratoria (si la etiqueta lo pide). Revisar a los que no ganan.'],
 ['Cada día','Revisar a los animales mañana y tarde: orejas caídas, tos, moco, no come. Los primeros 30 días son los de más riesgo.'],
 ['Antes de vender','Revisar que ningún tratamiento esté en retiro. La app te avisa.']
];
const REG_PLAN={
 th:{lluvias:['Garrapatas: baño o producto cada 21 a 28 días; alterna el grupo químico cada año para evitar resistencia.','Tórsalo y mosca del cuerno: control en cuanto veas los primeros.','Vacuna contra rabia paralítica una vez al año si hay murciélagos vampiros en tu zona (casi toda Latinoamérica tropical).','Leptospirosis: vacuna si hay agua estancada o roedores.','Pisos: lodo trae cojeras y pietín; rellena y drena los corrales.','Anaplasma y babesia (ranilla): vigila fiebre, anemia y orina oscura.'],
      seca:['Vitaminas A, D y E al inicio de la seca si comen pasto seco.','Agua: revisa caudal y limpieza; beben más con calor.','Desparasitación estratégica al inicio de la seca.','Sombra: al menos 3 a 4 m² por animal.'],
      todo:['Ántrax y carbón sintomático: vacuna anual en zonas con casos.','Gusano barrenador: revisa y cura heridas, ombligos y castraciones cada día; es de reporte obligatorio.']},
 ts:{lluvias:['Garrapatas y moscas al inicio de las lluvias.','Timpanismo con pastos tiernos: cambia la dieta poco a poco.','Rabia paralítica: vacuna anual si hay murciélagos vampiros.'],
      seca:['Vitaminas A, D y E: la falta de vitamina A es común con pasto seco.','Polvo: riega los corrales; baja las neumonías.','Agua: calidad y cantidad, con sales bajas.','Proteína: suplementa con urea y melaza o bancos de proteína si hay potrero.','Plantas tóxicas: en la seca comen lo que queda verde.'],
      todo:['Ántrax y carbón sintomático: vacuna anual en zonas con casos.','Estrés por calor: sombra, agua fresca y trabajar al amanecer.']},
 sub:{verano:['Garrapata: tratamientos estratégicos al inicio del calor.','Mosca del cuerno y de los establos.','Estrés por calor: sombra y agua.'],
      invierno:['Neumonías con los cambios bruscos de temperatura: reparo del viento.','Barro en los corrales: drenaje.'],
      todo:['Fiebre aftosa: sigue el programa oficial de tu país.','Rabia paralítica donde haya murciélagos vampiros.','Clostridiales y carbón: vacuna anual.']},
 tem:{'otoño':['Complejo respiratorio: vacuna antes del destete o al ingreso y metafilaxia en animales de alto riesgo con tu veterinario.','Recepción con reparo del viento y cama seca.'],
      invierno:['Estrés por frío: necesitan más energía (1 % más por cada grado bajo el confort).','Barro: cada 10 cm de barro baja la ganancia; drena y raspa.','Agua sin congelarse.'],
      primavera:['Pietín y problemas de patas con humedad.','Revisa la condición al cambiar de dieta.'],
      verano:['Moscas y ojo rosado (queratoconjuntivitis): control de moscas y sombra.','Estrés por calor en días de más de 30 °C: sombra, agua y aspersores.'],
      todo:['Clostridiales: vacuna al ingreso con refuerzo.','Fiebre aftosa y otros programas oficiales según el país.']},
 ari:{lluvias:['Garrapatas y moscas con las primeras lluvias.','Timpanismo con rebrote.'],
      seca:['Agua: análisis de sales y sulfatos (arriba de 1,000 ppm de sulfato causa polioencefalomalacia).','Vitamina A en seca.','Polvo: riego de corrales.','Calor: sombra y agua fresca.'],
      todo:['Clostridiales y carbón: vacuna anual.']}
};
const MESES_N=['enero','febrero','marzo','abril','mayo','junio','julio','agosto','septiembre','octubre','noviembre','diciembre'];
const rango=([a,b])=>`${MESES_N[a-1]} a ${MESES_N[b-1]}`;
function epocas(pais){
  const [reg,ll]=REG_PAIS[pais]||['th',[5,10]];
  if(reg==='tem'){const sur=SUR.has(pais);return [['otoño',sur?[3,5]:[9,11]],['invierno',sur?[6,8]:[12,2]],['primavera',sur?[9,11]:[3,5]],['verano',sur?[12,2]:[6,8]]];}
  if(reg==='sub'){const sur=SUR.has(pais);return [['verano',sur?[11,3]:[5,9]],['invierno',sur?[5,8]:[11,2]]];}
  const fin=ll[1],ini=ll[0];return [['lluvias',ll],['seca',[fin%12+1,(ini+10)%12+1]]];
}
function calendarioRegion(pais){
  pais=pais||S.config.pais||'HN';const [reg]=REG_PAIS[pais]||['th'];const R=REGIONES[reg],P=REG_PLAN[reg],E=epocas(pais);
  const nomP=(PAISES.find(p=>p[0]===pais)||[pais,'tu país'])[1];
  const mes=new Date().getMonth()+1,en=([a,b])=>a<=b?mes>=a&&mes<=b:mes>=a||mes<=b;
  const ahora=E.find(e=>en(e[1]));
  return {html:`<b>Calendario sanitario: ${esc(nomP)}</b><p class="rs" style="margin-top:2px"><b>${R.n}.</b> ${R.x}</p>
    <p class="rh">Al ingreso de cada lote</p>${tablaW(['Cuándo','Qué hacer'],INGRESO.map(([a,b])=>[`<b>${a}</b>`,b]))}
    ${E.map(([ep,m])=>P[ep]?`<p class="rh">${ep[0].toUpperCase()+ep.slice(1)} (${rango(m)})${ahora&&ahora[0]===ep?' · <span class="pill p-verde">ahora</span>':''}</p><ul class="rl">${P[ep].map(t=>`<li>${t}</li>`).join('')}</ul>`:'').join('')}
    ${P.todo?`<p class="rh">Todo el año</p><ul class="rl">${P.todo.map(t=>`<li>${t}</li>`).join('')}</ul>`:''}
    <p class="rs">Es una guía general por clima. Las vacunas obligatorias, las enfermedades de tu zona y las dosis las decide tu veterinario y el servicio oficial de sanidad de tu país.</p>`,
    btns:[['Calendario de un lote',{t:'rumi',q:'calendario de sanidad'}]]};
}

/* ================= Rumi: nueva parte en la guía ================= */
const opReq={l:'Requerimientos por raza y clima',ic3:'nutricion',sub:()=>({t:'Requerimientos',intro:'¿Qué tipo de animales engordas?',
  ops:RAZAS_G.map(r=>({l:r.n,sub:()=>({t:r.n,intro:'¿Cómo es el clima en tu finca?',ops:CLIMAS.map(c=>({l:c.n,fn:()=>tablaReq(r.k,c.k)}))})}))})};
const opPais={l:'Calendario sanitario de otra región',sub:()=>({t:'Otras regiones',intro:'¿De qué país?',ops:PAISES.filter(p=>REG_PAIS[p[0]]).map(p=>({l:p[1],small:REGIONES[REG_PAIS[p[0]][0]].n,fn:()=>calendarioRegion(p[0])}))})};
const REF_OPS=()=>[{l:'Calendario sanitario de mi región',ic3:'sanidad',fn:()=>calendarioRegion()},opReq,{l:'Razas para engorde (comparación)',ic3:'genetica',fn:razasHtml},{l:'Precios de referencia por país',ic3:'negocio',fn:preciosHtml},opPais];
const G=RumiMenu.AREAS.find(a=>a.id==='guia');
if(G){const o=G.ops;G.ops=()=>[{l:'Tablas de referencia',ic3:'conceptos',n:5,sub:()=>({t:'Tablas de referencia',intro:'Tablas que calculo para ti: requerimientos por raza y clima, calendario sanitario de tu región, razas y precios de referencia.',ops:REF_OPS()})}].concat(o());}
const S2=RumiMenu.AREAS.find(a=>a.id==='salud');
if(S2){const o=S2.ops;S2.ops=()=>{const L=o();L.splice(1,0,{l:'Calendario sanitario de mi región',ic3:'sanidad',fn:()=>calendarioRegion()});return L;};}
const AL=RumiMenu.AREAS.find(a=>a.id==='alimento');
if(AL){const o=AL.ops;AL.ops=()=>{const L=o();L.splice(1,0,opReq);return L;};}
const DI=RumiMenu.AREAS.find(a=>a.id==='dinero');
if(DI){const o=DI.ops;DI.ops=()=>{const L=o();L.splice(1,0,{l:'Precios de referencia por país',ic3:'negocio',fn:preciosHtml});return L;};}
window.RumiSaber2={tablaReq,reqRC,razasHtml,preciosHtml,calendarioRegion,PRECIOS,REG_PAIS,RAZAS,RAZAS_G,CLIMAS,epocas};
if(window.RumiMas)RumiMas.FN.razaMia=()=>RumiPro.rankingHtml('raza');
})();
