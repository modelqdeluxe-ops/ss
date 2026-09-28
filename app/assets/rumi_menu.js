/* Rumi por menús.
   Rumi no recibe texto ni voz: saluda, muestra sus áreas y, dentro de cada área,
   las preguntas, cálculos y acciones que resuelve. Cada opción llama a una respuesta fija de la app,
   a una calculadora, a un formulario o a un tema de la guía (fichas con sus partes). */
(function(){
'use strict';
const GUIA=window.RUMI_GUIA||[],FICHAS=window.RUMI_FICHAS||[];
const FIDX=new Map(FICHAS.map((f,i)=>[f.t,i]));
// con la guía traducida, los temas que el código nombra en español también se encuentran
(window.RUMI_FICHAS_ES||[]).forEach((t,i)=>{if(!FIDX.has(t))FIDX.set(t,i);});
const uPal=()=>U()==='lb'?'libras':'kilos';
const IC2={
 hoy:'<path d="M12 3v2M12 19v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M3 12h2M19 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4"/><circle cx="12" cy="12" r="4"/>',
 lotes:'<path d="M3 20V8M21 20V8M3 11h18M3 16h18M9 11v5M15 11v5"/>',
 anotar:'<path d="M9 4h6l1 2h3v15H5V6h3z"/><path d="M9 12h6M9 16h4"/>',
 alimento:'<path d="M4 9h16l-1.5 10a2 2 0 0 1-2 1.7h-9a2 2 0 0 1-2-1.7L4 9z"/><path d="M8 9V6a4 4 0 0 1 8 0v3"/>',
 salud:'<path d="M12 21s-7-4.5-9-9.5C1.7 8 4 4.5 7.5 4.5c2 0 3.5 1 4.5 2.5 1-1.5 2.5-2.5 4.5-2.5C20 4.5 22.3 8 21 11.5 19 16.5 12 21 12 21z"/><path d="M12 9v6M9 12h6"/>',
 sintomas:'<circle cx="11" cy="11" r="6.5"/><path d="M16 16l5 5M11 8v3.5M11 14h.01"/>',
 calc:'<rect x="5" y="3" width="14" height="18" rx="2.5"/><path d="M8.5 7h7M8.5 11h.01M12 11h.01M15.5 11h.01M8.5 14.5h.01M12 14.5h.01M15.5 14.5h.01M8.5 18h.01M12 18h3.5"/>',
 dinero:'<path d="M3 7h18v10H3z"/><circle cx="12" cy="12" r="2.6"/><path d="M6 10v4M18 10v4"/>',
 guia:'<path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5z"/><path d="M4 20.5A2.5 2.5 0 0 0 6.5 23H20v-5"/><path d="M9 8h7M9 11.5h5"/>',
 app:'<rect x="6" y="2.5" width="12" height="19" rx="2.5"/><path d="M10.5 18.5h3"/>',
 menu:'<rect x="3.5" y="3.5" width="7" height="7" rx="2"/><rect x="13.5" y="3.5" width="7" height="7" rx="2"/><rect x="3.5" y="13.5" width="7" height="7" rx="2"/><rect x="13.5" y="13.5" width="7" height="7" rx="2"/>',
 back:'<path d="M15 5l-7 7 7 7"/>',chev:'<path d="M9 5l7 7-7 7"/>',
 libro:'<path d="M6 4h11a1 1 0 0 1 1 1v15H7a2 2 0 0 1-2-2V5a1 1 0 0 1 1-1z"/><path d="M9 8h6"/>',
 parte:'<path d="M5 6h14M5 12h14M5 18h9"/>',todo:'<path d="M4 5h16v14H4z"/><path d="M8 9h8M8 13h8M8 17h5"/>',link:'<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
 vaca:'<path d="M5 8c0-2 1.5-3 3-3h8c1.5 0 3 1 3 3v6a5 5 0 0 1-5 5h-4a5 5 0 0 1-5-5z"/><path d="M5 8L2 6M19 8l3-2M9.5 12h.01M14.5 12h.01M10 16h4"/>'
};
const ic=(k,sw=2)=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${IC2[k]||''}</svg>`;
const i3=k=>icono(k);
const lista=p=>`<ul class="rl">${p.map(x=>`<li>${esc(x)}</li>`).join('')}</ul>`;

/* ---------- guía: fichas con partes ---------- */
const SUB={sanidad:'Sanidad',nutricion:'Nutrición',manejo:'Manejo del ganado',forrajes:'Pastos y forrajes',instalaciones:'Corrales e instalaciones',negocio:'Negocio y precios',normas:'Normas y buenas prácticas',conceptos:'Palabras y cálculos del engorde',fisiologia:'Ciencia y fisiología del bovino',genetica:'Genética y razas',carne:'Ciencia de la carne',actualidad:'Actualidad y tendencias 2026',app:'Cómo usar la app'};
const fichasDe=a=>FICHAS.map((f,i)=>({f,i})).filter(o=>o.f.a===a);
const cortosDe=a=>GUIA.map((g,i)=>({g,i})).filter(o=>o.g.a===a);
const nPuntos=f=>f.s.reduce((n,x)=>n+x.p.length,0);
function fichaNivel(i){
  const f=FICHAS[i];
  const ops=[{l:'Ver todo el tema',ic:'todo',fn:()=>({html:`<b>${esc(f.t)}</b><p class="rres">${esc(f.r)}</p>`+f.s.map(s=>`<p class="rh">${esc(s.h)}</p>${lista(s.p)}`).join('')})}]
    .concat(f.s.map(s=>({l:`${s.h}`,ic:'parte',n:s.p.length,fn:()=>({html:`<b>${esc(f.t)}</b> · ${esc(s.h)}${lista(s.p)}`})})))
    .concat((f.rel||[]).filter(r=>FIDX.has(r)).map(r=>({l:r,ic:'link',pre:'Relacionado',sub:()=>fichaNivel(FIDX.get(r))})));
  return {t:f.t,intro:`<b>${esc(f.t)}</b><p class="rres">${esc(f.r)}</p><span class="rs">${f.s.length} partes y ${nPuntos(f)} puntos. Elige una parte o toca Ver todo el tema.</span>`,ops};
}
const opFichas=a=>{const F=fichasDe(a),C=cortosDe(a);return {l:SUB[a],ic3:a,n:F.length,sub:()=>({t:SUB[a],intro:`Estos son mis temas de <b>${SUB[a].toLowerCase()}</b>: ${F.length} temas completos${C.length?` y ${C.length} datos rápidos`:''}. Elige uno.`,
  ops:F.map(o=>({l:o.f.t,n:nPuntos(o.f),sub:()=>fichaNivel(o.i)})).concat(C.length?[{l:'Datos rápidos',ic:'libro',n:C.length,sub:()=>({t:'Datos rápidos: '+SUB[a],intro:'Datos cortos para consultar rápido.',ops:C.map(o=>({l:o.g.t,guia:o.i}))})}]:[])})};};
const opTema=t=>FIDX.has(t)?{l:FICHAS[FIDX.get(t)].t,ic:'libro',sub:()=>fichaNivel(FIDX.get(t))}:null;

/* ---------- utilidades ---------- */
const q=(l,frase)=>({l,q:frase});
const f=(l,k,p)=>({l,form:k,p:p||{}});
const C_=()=>calc();
const lotesAct=()=>C_().act;
const kgU=v=>W(v),pU=v=>PKd(v);
const opLotes=(t,fn,todos)=>()=>{const C=C_();const L=todos?Object.values(C.L).sort((a,b)=>(b.activo-a.activo)||String(a.l.nombre).localeCompare(String(b.l.nombre),'es',{numeric:true})):C.act;
  if(!L.length)return {t,intro:'Todavía no tienes lotes en engorde. Crea el primero y aquí verás sus números.',ops:[f('Crear un lote','lote')]};
  return {t,intro:'¿De qué lote?',ops:L.map(x=>({l:`${x.l.nombre}${x.activo?'':' (vendido)'}`,small:x.activo?`${pl(x.cab,'cabeza','cabezas')}, ${nf(kgU(x.pesoHoy))} ${UW()}`:'',sub:()=>fn(x)}))};};

/* ---------- mis lotes: todo sobre un lote ---------- */
const MET=[['¿Cómo va?',null],['Peso de hoy','peso'],['¿Cuándo sale?','salida'],['Ganancia diaria','ganancia'],['¿Cuánto come?','consumo'],['Conversión','conversion'],['Costo por kilo ganado','costo'],['Margen esperado','margen'],['Dinero invertido','inversion'],['Días en engorde','dias'],['Cabezas','cabezas'],['Muertes','muertes'],['Retiro de medicinas','retiro'],['Ración que come','racion']];
function animalHtml(x,a){
  const est=a.estado==='muerto'?`Murió el ${ffc(a.fin)}.`:a.estado==='vendido'?`Se vendió el ${ffc(a.fin)}.`:'En engorde.';
  const d=[['Peso',`${nf(a.pesoHoy)} kg`],['Ganancia',a.gdp!=null?`${nf(a.gdp,2)} kg por día`:'sin pesajes aún'],['Entró con',`${nf(a.p0)} kg`],['Raza',a.raza||x.l.raza||'–'],['Color',a.color||'–'],['Costo de compra',a.costo?money(a.costo):'–'],['Tratamientos',String(a.san.length)]];
  return `<div class="ranim"><span class="foto grande"><img data-foto="${esc(a.id)}" alt="" hidden>${ic('vaca',1.8)}</span><div><b>Arete ${esc(a.arete)}</b><span>${esc(x.l.nombre)}. ${est}</span></div></div>`+tabla(null,d.map(([k,v])=>[k,`<b>${esc(v)}</b>`]));
}
function opsAnimales(x){
  const A=(x.anim||[]).filter(a=>a.estado==='activo');
  if(!tieneAnimales(x.l))return [{l:'Este lote no tiene aretes',fn:()=>({html:'Este lote se anotó solo con el peso promedio. Agrega sus animales para seguir a cada uno.',btns:[['Agregar animales',{t:'form',k:'animales',p:{}}]]})}];
  const prom=x.gdpUse;const con=A.filter(a=>a.gdp!=null);
  const unAnimal=a=>({l:`Arete ${a.arete}`,small:`${nf(kgU(a.pesoHoy))} ${UW()}${a.gdp!=null?`, ${nf(kgU(a.gdp),2)} ${UW()}/día`:''}`,sub:()=>({t:'Arete '+a.arete,intro:animalHtml(x,a),ops:[
    {l:'Abrir su ficha',go:`#animal/${encodeURIComponent(x.id)}/${encodeURIComponent(a.id)}`},
    {l:'Pesarlo',run:()=>FORMS.pesoAnimal({lote:x.id,id:a.id})},
    {l:'Tratarlo (sanidad)',run:()=>FORMS.sanidad({lote:x.id,id:a.id})},
    {l:'Tomarle foto',run:async()=>{const b=await Fotos.tomar('Arete '+a.arete);if(b){await Fotos.guardar(a.id,b);toast('Foto guardada');}},keep:true}]})});
  return [
    {l:'Los 5 que más ganan',fn:()=>{const B=[...con].sort((u,v)=>v.gdp-u.gdp).slice(0,5);return {html:B.length?`Los que más ganan en <b>${esc(x.l.nombre)}</b>:`+tabla(['Arete','Peso','Por día'],B.map(a=>[esc(a.arete),nf(a.pesoHoy)+' kg',nf(a.gdp,2)+' kg'])):'Aún no hay pesajes por animal en este lote.'};}},
    {l:'Animales atrasados',fn:()=>{const B=con.filter(a=>a.gdp<prom*0.7).sort((u,v)=>u.gdp-v.gdp);return {html:B.length?`Ganan menos del 70 % del promedio (${nf(prom,2)} kg):`+tabla(['Arete','Peso','Por día'],B.map(a=>[esc(a.arete),nf(a.pesoHoy)+' kg',nf(a.gdp,2)+' kg']))+'<p class="rs">Revísalos: dientes, patas, parásitos o si no los dejan comer.</p>':'Ningún animal va muy atrasado. ¡Bien!'};}},
    {l:'Sin pesar desde la entrada',fn:()=>{const B=A.filter(a=>!a.pesado);return {html:B.length?`${pl(B.length,'animal','animales')} sin pesar desde la entrada: ${B.slice(0,30).map(a=>esc(a.arete)).join(', ')}${B.length>30?'…':''}.`:'Todos los animales tienen al menos un pesaje.'};}},
    {l:'Razas y colores del lote',fn:()=>{const cnt=k=>{const c={};for(const a of A){const v=a[k]||'Sin anotar';c[v]=(c[v]||0)+1;}return Object.entries(c).sort((a,b)=>b[1]-a[1]);};const r=cnt('raza'),c=cnt('color');
      const gz=r.map(([k])=>{const s=con.filter(a=>(a.raza||'Sin anotar')===k);return s.length?nf(promedio(s.map(a=>a.gdp)),2)+' kg':'–';});
      return {html:`<b>Razas</b>`+tabla(['Raza','Cabezas','Ganancia'],r.map(([k,n],i)=>[esc(k),n,gz[i]]))+`<b>Colores</b>`+tabla(['Color','Cabezas'],c.map(([k,n])=>[esc(k),n]))};}},
    {l:'Ver un animal',n:A.length,sub:()=>({t:'Animales de '+x.l.nombre,intro:'¿Cuál animal? Te muestro su foto y sus números.',ops:A.slice().sort((u,v)=>(parseInt(u.arete)||0)-(parseInt(v.arete)||0)).map(unAnimal)})}
  ];
}
function calendario(x){
  const l=x.l,ing=l.fechaIngreso,H=C_().H,san=x.g.sanidad||[],pes=x.g.pesaje||[];
  const hecho=(desde,hasta,arr)=>arr.some(i=>i.f>=addDias(ing,desde)&&i.f<=addDias(ing,hasta));
  const filas=[
    ['Día 0 a 2',addDias(ing,0),'Vacuna clostridial, desparasitante y vitaminas',hecho(0,3,san)],
    ['Día 1 a 7',addDias(ing,1),'Pesaje de entrada y aretes',tieneAnimales(l)||hecho(0,7,pes)],
    ['Día 21 a 30',addDias(ing,21),'Refuerzo de vacuna clostridial (y respiratoria si aplica)',hecho(18,35,san)],
    ['Día 28',addDias(ing,28),'Primer pesaje de control',hecho(20,40,pes)],
    ['Día 56',addDias(ing,56),'Segundo pesaje',hecho(45,70,pes)],
    ['Día 84',addDias(ing,84),'Tercer pesaje',hecho(75,100,pes)]];
  if(x.activo&&x.fechaMeta)filas.push(['Meta',x.fechaMeta,`Llega a ${nf(x.meta)} kg: preparar venta`,false]);
  return {html:`Calendario de <b>${esc(l.nombre)}</b> (entró el ${ffc(ing)}):`+tabla(['Cuándo','Fecha','Qué hacer',''],filas.map(([c,fe,t,ok])=>[c,ffc(fe),esc(t),ok?'✓':(fe<H?'<b style="color:var(--rojo)">pendiente</b>':'')]))+'<p class="rs">Guía general; ajústala con tu veterinario.</p>'};
}
function loteNivel(x){
  const ops=MET.map(([l,m])=>({l,fn:()=>m?metricaLote(x,m):loteMsg(x)}));
  if(x.activo){
    ops.push({l:'Proyectar su peso',sub:()=>({t:'Proyectar '+x.l.nombre,intro:`¿A cuántos días proyecto <b>${esc(x.l.nombre)}</b>?`,ops:[15,30,45,60,90,120].map(d=>({l:`En ${d} días`,fn:()=>proyeccion(nrm(`cuanto pesara en ${d} dias`),{lote:x.id})}))})});
    ops.push({l:'Calendario de sanidad y pesajes',fn:()=>calendario(x)});
  }
  ops.push({l:'Sus animales',ic:'vaca',sub:()=>({t:'Animales de '+x.l.nombre,intro:'¿Qué quieres ver de los animales?',ops:opsAnimales(x)})});
  if(x.activo)ops.push(f('Anotar pesaje de este lote','pesaje',{lote:x.id}),f('Anotar alimento','alimento',{}),f('Anotar sanidad','sanidad',{lote:x.id}),f('Anotar venta','venta',{lote:x.id}),f('Anotar muerte','baja',{lote:x.id}));
  ops.push({l:'Compartir informe (WhatsApp)',run:()=>Extras.compartir(Extras.informe(x.id),x.l.nombre),keep:true});
  ops.push({l:'Abrir el lote',go:'#lote/'+encodeURIComponent(x.id)});
  return {t:x.l.nombre,intro:loteMsg(x).html,ops};
}

/* ---------- síntomas ---------- */
const SINT=[
 ['Tose, tiene moco o respira rápido',['Neumonía o complejo respiratorio (lo más común las primeras 4 semanas).','Parásitos del pulmón si tose sin fiebre.','Polvo en época seca.','Calor fuerte si todo el lote jadea en horas calientes.'],['Sepáralo y tómale la temperatura: más de 39.5 °C es fiebre.','Si tiene fiebre, llama al veterinario hoy: tratar el primer día cambia todo.','Anota el tratamiento y el retiro en la app.'],['Neumonía o complejo respiratorio','Signos vitales y cómo tomarlos','Estrés por calor']],
 ['No come o está decaído',['Neumonía (con fiebre).','Acidosis o indigestión (sin fiebre, heces raras).','Anaplasmosis o babesiosis (mucosas pálidas o amarillas).','Parásitos o dientes malos (flaco, pelo feo).','Agua escasa o calor.'],['Tómale la temperatura y mira mucosas y heces.','Revisa el bebedero y la ración del lote.','Sepáralo con agua, sombra y heno, y consulta al veterinario.'],['Revisión diaria de los animales','Acidosis ruminal','Anaplasmosis','Consumo bajo']],
 ['Panza inflada del lado izquierdo',['Timpanismo: urgencia.','Acidosis por exceso de grano.'],['Llama al veterinario ya.','Haz caminar al animal despacio.','Revisa la ración: cambio brusco, grano muy fino, leguminosa tierna.'],['Timpanismo o panza inflada','Acidosis ruminal','Adaptación al concentrado']],
 ['Diarrea',['Cambio brusco de ración o exceso de grano (espumosa, agria).','Coccidiosis (con sangre o moco, pujo).','Alimento con moho o agua sucia.','Parásitos.','Infecciones (con fiebre).'],['Revisa la ración, la bodega y el agua.','Tómale la temperatura.','Dale agua y sueros; consulta si hay sangre, fiebre o varios casos.'],['Diarrea en el engorde','Coccidiosis','Acidosis ruminal']],
 ['Cojea',['Gabarro (infección entre las pezuñas, mal olor).','Piedras, clavos o espinas.','Laminitis por acidosis (varias patas).','Golpes.'],['Levanta la pata en la manga y límpiala.','Saca lo que tenga clavado y desinfecta.','Pásalo a un corral seco; consulta si hay hinchazón o mal olor.'],['Cojeras y problemas de pezuña','Pisos y drenaje','Acidosis ruminal']],
 ['Orina roja o café',['Babesiosis o ranilla roja (con fiebre).','Leptospirosis.','Hemoglobinuria bacilar (clostridial).','Helecho u otras plantas.'],['No lo corras; sepáralo con sombra y agua.','Llama al veterinario hoy.','Revisa garrapatas y plantas del potrero.'],['Orina roja u oscura','Babesiosis o ranilla roja','Leptospirosis']],
 ['Jadea con la boca abierta',['Golpe de calor (varios animales, horas calientes).','Neumonía grave (un animal, con fiebre).','Timpanismo.'],['Moja al animal y el piso, dale agua y sombra.','No lo muevas ni lo trabajes.','Si está caído o con fiebre, llama al veterinario.'],['Golpe de calor','Estrés por calor','Neumonía o complejo respiratorio']],
 ['Ojo lloroso o mancha blanca',['Ojo rosado (queratoconjuntivitis).','Espina, polvo o golpe.'],['Sepáralo a la sombra.','Consulta el tratamiento; un parche lo protege.','Controla moscas y polvo.'],['Queratoconjuntivitis u ojo rosado','Moscas del cuerno y del establo']],
 ['Herida con gusanos o mal olor',['Gusano barrenador (miasis).','Herida infectada o absceso.'],['Limpia, saca las larvas y aplica larvicida y repelente.','Revisa cada día.','Reporta el gusano barrenador a SENASA.'],['Gusano barrenador o gusanera','Heridas y curaciones']],
 ['Tiembla, se cae o está ciego',['Intoxicación por urea (temblores, babeo, poco después de comer).','Polioencefalomalacia (ciego, cabeza contra la pared).','Babesiosis con signos nerviosos.','Rabia (babea, no traga, parálisis).','Plantas tóxicas.'],['Llama al veterinario de inmediato.','No metas la mano en la boca si babea.','Retira el alimento sospechoso.'],['Intoxicación por urea','Polioencefalomalacia','Rabia paralítica bovina','Intoxicación por plantas y hongos']],
 ['Hinchazón en el cuerpo',['Absceso en sitio de inyección.','Pierna negra o edema maligno (crece rápido, cruje).','Hernia (bulto blando en ombligo o escroto).','Mordedura de serpiente.'],['Si crece rápido o cruje, es urgencia veterinaria.','Si es blando y en el sitio de una inyección, puede ser absceso: consulta.'],['Enfermedades clostridiales','Heridas y curaciones','Hernias y prolapsos']],
 ['Flaco, pelo feo o no engorda',['Parásitos internos o fasciola.','Falta de minerales.','Dientes malos o animal viejo.','Enfermedad pasada (neumonía).','No lo dejan comer (dominado).'],['Pésalo y compara con el lote en la app.','Revisa dientes, mucosas y heces.','Desparasita según el veterinario y revisa minerales.','Si sigue atrasado, véndelo sin retiros.'],['Animales atrasados','Parásitos internos','Deficiencias de minerales']],
 ['Se rasca o pierde pelo',['Tiña (manchas redondas con costra).','Piojos o sarna.','Garrapatas.','Fotosensibilización (piel blanca quemada).'],['Revisa la piel de cerca.','Trata según el tipo de parásito o problema.','Usa guantes: la tiña se pega.'],['Problemas de piel','Garrapatas']],
 ['Puja para orinar y no sale',['Cálculos urinarios (machos castrados, raciones con poco calcio o poca agua).'],['Es urgencia: llama al veterinario.','Revisa el agua y el calcio de la ración.'],['Cálculos urinarios','Agua de bebida']],
 ['Murió de repente',['Clostridiales (pierna negra, enterotoxemia).','Ántrax (sangre oscura por nariz, boca o ano).','Timpanismo o acidosis aguda.','Intoxicación.','Rayo.'],['Si sangra por los orificios, no lo abras y llama al veterinario y a SENASA.','Aparta al resto del lote.','Pide necropsia y anota la muerte en la app.'],['Muerte de un animal','Ántrax o carbón bacteridiano','Enfermedades clostridiales']]
];
const opSintoma=([t,causas,ahora,fichas])=>({l:t,sub:()=>({t,intro:`<b>${esc(t)}</b><p class="rh">Puede ser</p>${lista(causas)}<p class="rh">Qué hacer ahora</p>${lista(ahora)}<span class="rs">Esto orienta, no reemplaza al veterinario. Abre un tema para ver más.</span>`,ops:fichas.map(opTema).filter(Boolean).concat([f('Anotar un tratamiento','sanidad')])})});

/* ---------- calculadoras ---------- */
// calcOp con fn local: v → {html}. Los campos usan la unidad de la app cuando dicen u:'peso'.
const cc=(l,campos,fn,intro)=>({l,calc:{campos,fn,intro}});
const cPeso=(k='p',lab='Peso del animal')=>({k,l:lab,peso:true,min:10,max:3000});
const cN=(k,l,u,min,max,def)=>({k,l,u,min,max,def});
const aKg=v=>toKg(v);
const R=(t,filas,nota)=>({html:`<b>${esc(t)}</b>`+tabla(null,filas.map(([a,b])=>[esc(a),`<b>${b}</b>`]))+(nota?`<p class="rs">${nota}</p>`:''),nou:true});
const kgTxt=(kg,d=1)=>U()==='lb'?`${nf(kg*FK,d)} lb`:`${nf(kg,d)} kg`;
const precioU=()=>PUpor();
const mU=()=>PUs();
const CALCS=()=>[
 {l:'Índice de calor',calc:{campos:[cN('t','Temperatura','°C',10,50),cN('h','Humedad','%',5,100)],frase:v=>`indice de calor con ${v.t} grados y ${v.h} % de humedad`,intro:'Dime la temperatura y la humedad y te digo el riesgo para tus animales.'}},
 cc('Peso por cinta (sin báscula)',[cN('pt','Perímetro del pecho','cm',60,300),cN('lc','Largo del cuerpo','cm',60,300)],v=>{const kg=v.pt*v.pt*v.lc/10840;return R('Peso estimado',[['Peso aproximado',kgTxt(kg)],['Rango probable',`${kgTxt(kg*0.92)} a ${kgTxt(kg*1.08)}`]],'Mide el pecho justo detrás de las patas delanteras y el largo de la punta del hombro a la punta de la nalga. Para vender, usa báscula.');},'Mide con cinta métrica en centímetros.'),
 cc('Ganancia diaria',[cPeso('a','Peso inicial'),cPeso('b','Peso final'),cN('d','Días entre pesajes','días',1,1000)],v=>{const g=(aKg(v.b)-aKg(v.a))/v.d;return R('Ganancia diaria',[['Ganó en total',kgTxt(aKg(v.b)-aKg(v.a))],['Por día',kgTxt(g,2)]],g>=1.4?'Excelente para corral.':g>=1?'Buena ganancia.':g>=0.6?'Normal para pastoreo con suplemento.':'Baja: revisa ración, salud y agua.');},'Pon los dos pesos y los días entre ellos.'),
 cc('Días para llegar a un peso',[cPeso('a','Peso actual'),cPeso('b','Peso meta'),cN('g','Ganancia diaria',()=>UW()+'/día',0.05,5)],v=>{const d=Math.ceil((aKg(v.b)-aKg(v.a))/aKg(v.g));return R('Días a la meta',[['Días',d>0?nf(d):'ya llegó'],['Fecha estimada',d>0?ffl(addDias(hoy(),d)):'hoy']]);},'Pon el peso de hoy, la meta y cuánto sube por día.'),
 cc('Conversión alimenticia',[cN('al','Alimento consumido',()=>UW(),1,1e7),cN('g','Kilos ganados',()=>UW(),0.1,1e6)],v=>{const c=v.al/v.g;return R('Conversión',[['Conversión',`${nf(c,1)} a 1`],['Eficiencia',nf(1/c,3)]],c<=6.5?'Excelente.':c<=8?'Está bien para finalización.':'Alta: revisa desperdicio, ración y salud.');},'Pon el alimento que comieron y los kilos que ganaron en el mismo período.'),
 cc('Costo por kilo ganado',[cN('m','Costo de la mezcla',()=>PUAs(),0.01,1e5),cN('c','Conversión (kg de alimento por kg ganado)','a 1',1,30),cN('o','Otros costos por kilo ganado',()=>mU(),0,1e5,0)],v=>{const al=v.m/PWA()*PW()*v.c,t=al+(v.o||0);return R('Costo por kilo ganado',[['Alimento',money2(al)+' '+PUpor()],['Total',money2(t)+' '+PUpor()],['Precio de venta actual',money2(pU(+S.config.precioVentaKg))+' '+PUpor()]],t<pU(+S.config.precioVentaKg)?'Producir cada kilo te cuesta menos de lo que te pagan: bien.':'Cada kilo te cuesta más de lo que te pagan: revisa la ración y la conversión.');},'El costo del kilo de mezcla está en tus raciones.'),
 cc('Consumo según el peso',[cPeso('p','Peso del animal'),cN('pc','Consumo en materia seca','% del peso',1,4,2.5),cN('ms','Materia seca de la ración','%',10,100,85)],v=>{const kg=aKg(v.p)*v.pc/100;return R('Consumo diario',[['Materia seca',kgTxt(kg)],['Tal como se sirve',kgTxt(kg/(v.ms/100))]],'Engorde: 2.2 a 2.8 % del peso en materia seca. Menos al inicio y con calor.');},'Pon el peso; los porcentajes ya traen valores comunes.'),
 cc('Agua que necesitan',[cPeso('p','Peso del animal'),cN('t','Temperatura del día','°C',10,45,30),cN('n','Cabezas','cab.',1,1e5,1)],v=>{const kg=aKg(v.p);const l=kg*0.024*litrosPorKgMS(v.t);return R('Agua al día',[['Por animal',`${nf(l)} litros`],['Todo el grupo',`${nf(l*v.n)} litros`],['Reserva para 3 días',`${nf(l*v.n*3)} litros`]],'Se calcula con lo que comen (2.4 % del peso en materia seca): de 3.5 litros por kilo comido con clima fresco a 7 con calor fuerte. Nunca debe faltar.');},'Pon el peso, la temperatura máxima del día y cuántos animales.'),
 {l:'Dosis de un medicamento',goArea:'salud'},
 cc('Dosis por peso (según la etiqueta)',[cPeso('p','Peso del animal'),cN('ml','ml de la etiqueta','ml',0.1,100,1),cN('cada','por cada',()=>UW(),1,500,U()==='lb'?110:50)],v=>{const d=v.ml*v.p/v.cada;return R('Dosis',[['Dosis para este animal',`${nf(d,1)} ml`]],'Usa siempre la dosis y la vía de la etiqueta de tu producto, y consulta a tu veterinario. Respeta el retiro.');},'Copia de la etiqueta cuántos ml van por cada cuánto peso.'),
 {l:'Espacio de corral y comedero',calc:{campos:[cN('n','Cabezas','cab.',1,5000)],frase:v=>`espacio de comedero para ${v.n} cabezas`,intro:'¿Para cuántas cabezas calculo el corral, el comedero, la sombra y el bebedero?'}},
 cc('Carga de un potrero',[cN('a','Área del potrero','manzanas',0.1,1e4),cN('fv','Pasto verde medido','kg por m²',0.1,20,1.5),cPeso('p','Peso promedio'),cN('d','Días de ocupación','días',1,120,3)],v=>{const disp=v.a*7000*v.fv*0.55;const cons=aKg(v.p)*0.11;const cab=Math.floor(disp/(cons*v.d));return R('Carga del potrero',[['Forraje aprovechable',kgTxt(disp)],['Consumo por animal al día',kgTxt(cons)],['Animales por '+v.d+' días',nf(cab)]],'Se usa 55 % del pasto para no sobrepastorear. Mide el pasto con un marco de 1 m² en varios puntos.');},'Mide el pasto con un marco de un metro cuadrado.'),
 cc('Ensilaje que necesito',[cN('n','Cabezas','cab.',1,1e5),cN('kg','Ensilaje por animal al día',()=>UW(),0.5,60,8),cN('d','Días','días',1,400,120)],v=>{const t=v.n*v.kg*v.d;return R('Ensilaje necesario',[['Consumo',`${nf(t)} ${UW()}`],['Con 20 % de pérdidas',`${nf(t*1.2)} ${UW()}`],['En toneladas',nf(aKg(t*1.2)/1000,1)+' t']]);},'Para planear el verano.'),
 {l:'Rendimiento en canal',calc:{campos:[cPeso('p','Peso vivo')],frase:v=>`rendimiento en canal de ${v.p} ${uPal()}`,intro:'¿Cuánto pesa el animal vivo? Te digo cuánto da en canal.'}},
 cc('Comparar pie y canal',[cN('pc','Precio de la canal',()=>mU(),0.01,1e5),cN('r','Rendimiento esperado','%',40,70,56),cN('pp','Oferta en pie',()=>mU(),0.01,1e5)],v=>{const eq=v.pc*v.r/100;return R('Pie o canal',[['Precio en canal equivale en pie a',money2(eq)],['Oferta en pie',money2(v.pp)],['Conviene',eq>v.pp?'vender en canal':'vender en pie']],'Revisa también el desbaste y quién paga el flete.');},'Compara las dos ofertas.'),
 cc('Desbaste al vender',[cPeso('p','Peso lleno'),cN('d','Desbaste','%',0,15,+S.config.desbaste||4),cN('pr','Precio',()=>mU(),0,1e5,0)],v=>{const pag=v.p*(1-v.d/100);return R('Peso que te pagan',[['Peso pagado',`${nf(pag,1)} ${UW()}`],['Descuento',`${nf(v.p-pag,1)} ${UW()}`]].concat(v.pr?[['Valor',money(pag*v.pr/PW())]]:[]));},'Pon el peso en finca y el porcentaje de desbaste.'),
 cc('Margen de un negocio',[cPeso('pc','Peso de compra'),cN('prc','Precio de compra',()=>mU(),0.01,1e5),cPeso('pv','Peso de venta en la finca'),cN('d','Desbaste','%',0,15,+S.config.desbaste||4),cN('prv','Precio de venta',()=>mU(),0.01,1e5,r2u(pU(+S.config.precioVentaKg))),cN('ck','Costo por kilo ganado',()=>mU(),0,1e5),cN('o','Otros costos por cabeza',()=>S.config.moneda||'L',0,1e6,0)],v=>{const comp=v.pc*v.prc/PW(),vent=v.pv*(1-(v.d||0)/100)*v.prv/PW(),eng=(v.pv-v.pc)*v.ck/PW();const m=vent-comp-eng-(v.o||0);return R('Margen por cabeza',[['Venta (con desbaste)',money(vent)],['Compra',money(comp)],['Engorde',money(eng)],['Otros',money(v.o||0)],['Margen',money(m)]],m>0?'El negocio deja ganancia.':'Con estos números pierdes: baja el precio de compra o el costo.');},'Pon los precios en la unidad de la app.'),
 {l:'Precio máximo de compra',calc:{campos:[cPeso('p','Peso de compra')],frase:v=>`a cuanto puedo comprar novillos de ${v.p} ${uPal()}`,intro:'¿De qué peso son los animales que piensas comprar?'}},
 cc('Precio por cabeza a precio por peso',[cN('pr','Precio por cabeza',()=>S.config.moneda||'L',1,1e7),cPeso('p','Peso del animal')],v=>R('Precio por peso',[[precioU(),money2(v.pr/v.p*PW())]],'Compara siempre las ofertas por peso.'),'Para comparar ofertas.'),
 cc('Interés de un crédito',[cN('m','Monto',()=>S.config.moneda||'L',1,1e9),cN('t','Tasa anual','%',0,100),cN('d','Días','días',1,3650,120)],v=>{const i=v.m*v.t/100*v.d/365;return R('Costo del crédito',[['Interés',money(i)],['Total a pagar',money(v.m+i)]]);},'Para sumar el costo del dinero al engorde.'),
 cc('Libras a kilos',[cN('v','Libras','lb',0.01,1e9)],v=>R('Conversión',[['Kilos',nf(v.v/2.2046,2)+' kg']]),''),
 cc('Kilos a libras',[cN('v','Kilos','kg',0.01,1e9)],v=>R('Conversión',[['Libras',nf(v.v*2.2046,2)+' lb']]),''),
 cc('Quintales y arrobas',[cN('v','Quintales','qq',0,1e7,0),cN('a','Arrobas','@',0,1e7,0)],v=>{const lb=v.v*100+v.a*25;return R('Conversión',[['Libras',nf(lb)+' lb'],['Kilos',nf(lb/2.2046,1)+' kg']]);},'Un quintal = 100 libras; una arroba = 25 libras.'),
 cc('Precio por libra y por kilo',[cN('lb','Precio por libra',()=>S.config.moneda||'L',0,1e6,0),cN('kg','Precio por kilo',()=>S.config.moneda||'L',0,1e6,0)],v=>R('Conversión de precio',[].concat(v.lb?[['Por kilo',money2(v.lb*2.2046)]]:[]).concat(v.kg?[['Por libra',money2(v.kg/2.2046)]]:[])),'Llena uno de los dos.'),
 cc('Manzanas y hectáreas',[cN('mz','Manzanas','mz',0,1e7,0),cN('ha','Hectáreas','ha',0,1e7,0)],v=>R('Conversión de área',[].concat(v.mz?[['Hectáreas',nf(v.mz*0.6987,2)+' ha']]:[]).concat(v.ha?[['Manzanas',nf(v.ha/0.6987,2)+' mz']]:[])),'Llena uno de los dos.'),
 {l:'Edad por los dientes',sub:()=>({t:'Edad por los dientes',intro:'¿Cuántos dientes permanentes (anchos) tiene abajo?',ops:[['Ninguno, todos de leche','menos de 18 a 20 meses'],['2 permanentes','unos 2 años'],['4 permanentes','unos 2.5 a 3 años'],['6 permanentes','unos 3 a 3.5 años'],['8 permanentes, boca llena','4 años o más'],['Dientes gastados o faltan','animal viejo: convierte peor']].map(([l,e])=>({l,fn:()=>({html:`Con ${esc(l.toLowerCase())}: <b>${e}</b>. Varía con la raza y la alimentación.`,nou:true})}))})},
 {l:'Mezcla de una ración por tandas',sub:()=>{const R0=Object.entries(S.raciones).filter(([k,r])=>(r.ings||[]).some(i=>+i.p>0));return {t:'Mezcla por tandas',intro:R0.length?'¿Qué ración vas a mezclar?':'Tus raciones aún no tienen ingredientes con porcentajes. Crea una en Formular dieta.',ops:R0.map(([k,r])=>cc(r.nombre,[cN('t','Cantidad a mezclar',()=>UW(),1,1e7,1000)],v=>{const tot=r.ings.reduce((s,i)=>s+(+i.p||0),0);return {html:`<b>${esc(r.nombre)}</b> para ${nf(v.t)} ${UW()}:`+tabla(['Ingrediente','Cantidad'],r.ings.filter(i=>+i.p>0).map(i=>[esc(i.n),`${nf(v.t*i.p/tot,1)} ${UW()}`]))+'<p class="rs">Los ingredientes pequeños (minerales, urea) mézclalos antes con un poco del alimento.</p>',nou:true};},'¿Cuánto vas a mezclar en total?'))};}}
];

/* ---------- áreas ---------- */

/* ---------- clima de hoy y estrés por calor (ITH) ---------- */
function ith(t,h){return (1.8*t+32)-(0.55-0.0055*h)*(1.8*t-26);}
const ITH_NIV=[[75,'Normal','verde','Sin estrés por calor. Mantén agua limpia y sombra.'],
 [79,'Alerta','tierra','Empiezan a jadear y comen menos. Revisa que el agua alcance y no los muevas en horas de sol.'],
 [84,'Peligro','rojo','Comen menos y bajan la ganancia. Da la comida temprano y al atardecer, más agua y sombra, y no pesar ni vacunar a mediodía.'],
 [999,'Emergencia','rojo','Riesgo de muerte por calor. Moja el piso o los animales, sombra total, agua de sobra y nada de manejo hasta que refresque.']];
const nivelITH=v=>ITH_NIV.find(z=>v<z[0]);
function climaMsg(){
  const c=window.Clima&&Clima.actual();
  if(!c||c.t==null){if(window.Clima)Clima.actualizar(true);
    return {html:`Aún no tengo el clima de tu zona. Necesito internet y saber dónde estás: da permiso de ubicación al teléfono o escribe tu pueblo en <b>Más, Configuración, Ubicación</b>.<br><br>Mientras tanto, estamos en <b>${{seca:'época seca',canicula:'canícula',lluvias:'época de lluvias'}[window.Clima?Clima.epoca():'lluvias']}</b>.`,btns:[['Abrir Configuración',{t:'go',go:'#mas/config'}]]};}
  const v=ith(c.t,c.h??70),n=nivelITH(v);
  const hace=Math.max(0,Math.round((Date.now()-c.ts)/60000));
  const H=(c.horas||[]).slice(0,12).filter(x=>x.t!=null);
  const pico=H.reduce((m,x)=>{const i=ith(x.t,x.hr??c.h??70);return !m||i>m.i?{...x,i}:m;},null);
  const lluviaProx=H.find(x=>(x.p||0)>=60||(x.mm||0)>=0.5);
  const mx=c.max&&c.max[0]!=null?c.max[0]:null,mn=c.min&&c.min[0]!=null?c.min[0]:null,pl0=c.probLluvia&&c.probLluvia[0]!=null?c.probLluvia[0]:null;
  const man=c.max&&c.max[1]!=null?`Mañana: entre ${Math.round(c.min[1])} y ${Math.round(c.max[1])} °C${c.probLluvia&&c.probLluvia[1]!=null?`, ${c.probLluvia[1]} % de probabilidad de lluvia`:''}.`:'';
  const tabla0=H.length?tabla(['Hora','Temp.','Lluvia','Calor'],H.filter((x,i)=>i%2===0).map(x=>{const i=ith(x.t,x.hr??70),nv=nivelITH(i);return [x.h,`${Math.round(x.t)} °C`,`${x.p??0} %`,`<span class="pill p-${nv[2]}">${nv[1]}</span>`];})):'';
  return {html:`<b>${esc(Clima.texto())}</b>${c.lugar?` en ${esc(c.lugar)}`:''}<br>Se siente como ${Math.round(c.sens??c.t)} °C · humedad ${Math.round(c.h??0)} %${c.viento!=null?` · viento ${Math.round(c.viento)} km/h`:''}.
   ${mx!=null?`<br>Hoy: mínima ${Math.round(mn)} °C, máxima <b>${Math.round(mx)} °C</b>${pl0!=null?`, lluvia ${pl0} %`:''}.`:''}
   <p class="rh">Calor en el ganado ahora</p>Índice de temperatura y humedad (ITH): <b>${nf(Math.round(v))}</b> <span class="pill p-${n[2]}">${n[1]}</span><br>${n[3]}
   ${pico&&nivelITH(pico.i)!==n?`<p class="rh">Lo más fuerte de las próximas horas</p>A las <b>${pico.h}</b> el ITH llega a <b>${nf(Math.round(pico.i))}</b> (${nivelITH(pico.i)[1]}). ${nivelITH(pico.i)[3]}`:''}
   ${lluviaProx?`<p class="rh">Lluvia</p>Probable lluvia cerca de las <b>${lluviaProx.h}</b> (${lluviaProx.p??0} %). Protege el alimento y revisa los drenajes.`:''}
   ${tabla0?`<p class="rh">Próximas horas</p>${tabla0}`:''}${man?`<br>${man}`:''}
   <br><small>El Brahman y sus cruces aguantan más calor que las razas europeas, pero igual bajan su ganancia.</small><br><small>Dato de ${hace<2?'hace un momento':'hace '+pl(hace,'minuto','minutos')}${c.origen==='gps'?', con la ubicación del teléfono':c.origen==='nombre'?`, para ${esc(c.q||'tu ubicación')}`:''}. Clima: Open-Meteo.</small>`,btns:[['Actualizar el clima',{t:'clima'}]]};
}
const AREAS=[
 {id:'hoy',ic:'hoy',t:'Mi engorde',s:'Pendientes y números de hoy',intro:'Aquí te digo cómo va tu engorde con tus propios datos. ¿Qué quieres saber?',ops:()=>[
   q('¿Qué hago hoy?','que hago hoy'),
   {l:'Clima de hoy y calor en el ganado',ic3:'sun',fn:climaMsg},
   {l:'Mi agenda (tareas de hoy y próximas)',fn:()=>{const H=C_().H,T=Agenda.tareas();const h=T.filter(t=>t.f<=H),p=T.filter(t=>t.f>H&&t.f<=addDias(H,7));
     return {html:(h.length?'<b>Hoy</b>'+lista(h.map(t=>t.t+': '+t.s)):'<b>Hoy no tienes tareas pendientes.</b>')+(p.length?'<p class="rh">Próximos 7 días</p>'+lista(p.map(t=>`${ffc(t.f)} · ${t.t}`)):''),btns:[['Abrir la agenda',{t:'go',go:'#agenda'}]]};}},
   q('¿Qué lote está listo para vender?','que lote esta listo'),
   {l:'Lotes que toca pesar',fn:()=>{const C=C_(),d=+S.config.diasSinPesar||21;const P=C.act.filter(x=>x.diasSinPesar>d);return {html:P.length?`Toca pesar:`+tabla(['Lote','Último pesaje'],P.map(x=>[esc(x.l.nombre),cuando(x.ult.f)])):`Todos tus lotes están pesados en los últimos ${d} días.`,btns:P[0]?[['Anotar pesaje',{t:'form',k:'pesaje',p:{lote:P[0].id}}]]:[]};}},
   {l:'Lotes en retiro de medicinas',fn:()=>{const R0=C_().act.filter(x=>x.retiroHasta);return {html:R0.length?'En retiro (no vender a matadero antes):'+tabla(['Lote','Producto','Hasta'],R0.map(x=>[esc(x.l.nombre),esc(x.retiroProd||''),ffc(x.retiroHasta)])):'Ningún lote está en retiro.'};}},
   q('¿Cuántas cabezas tengo?','cuantas cabezas tengo'),q('¿Cuál es mi mejor lote?','cual es mi mejor lote'),
   q('¿Cuánto suben por día?','cuanto suben por dia'),q('¿Cuál es mi conversión?','cual es mi conversion'),
   q('¿Cuánto comen?','cuanto comen'),q('¿Cuántos se han muerto?','cuantos se han muerto'),q('Resumen del mes','resumen del mes'),
   q('¿Cuántas vueltas al año da mi corral?','cuantas vueltas al ano')]},
 {id:'lotes',ic:'lotes',t:'Mis lotes',s:'Todo sobre cada lote y sus animales',intro:'Elige un lote y te digo todo: peso, salida, costos, margen, animales, calendario y más.',ops:()=>opLotes('Mis lotes',loteNivel,true)().ops},
 {id:'anotar',ic:'anotar',t:'Anotar',s:'Te abro el formulario',intro:'¿Qué quieres anotar? Te abro el formulario listo para llenar.',ops:()=>[
   f('Entrega de alimento','alimento'),f('Pesaje','pesaje'),f('Nuevo lote de animales','lote'),f('Sanidad: vacuna o tratamiento','sanidad'),
   f('Venta','venta'),f('Muerte o baja','baja'),f('Gasto','gasto'),f('Nueva ración','racion'),
   q('Me equivoqué en un registro','me equivoque en un registro')]},
 {id:'alimento',ic:'alimento',t:'Alimentación',s:'Dietas, consumo, agua y forrajes',intro:'Hablemos de la comida de tus animales. ¿Qué necesitas?',ops:()=>[
   q('Formular una dieta barata','formula una dieta'),{l:'Abrir el formulador de dietas',go:'#formular'},
   q('¿Cuánto alimento necesito para el mes?','cuanto alimento para el mes'),
   {l:'¿Para cuántos días me alcanza el alimento?',fn:()=>{const it=(S.config.bodega||{}).items||[];const E=it.map(i=>({i,e:Extras.bodega.estado(i)})).filter(o=>!o.e.sin);
     return E.length?{html:'En tu bodega:'+tabla(['Producto','Hay','Alcanza'],E.map(o=>[esc(o.i.n),`${nf(Math.max(0,o.e.stock))} kg`,o.e.dias==null?'–':pl(Math.floor(o.e.dias),'día','días')])),btns:[['Abrir la bodega',{t:'go',go:'#bodega'}]]}:{html:'Aún no llevas la bodega. Anota tus compras de alimento y te digo para cuántos días alcanza.',btns:[['Abrir la bodega',{t:'go',go:'#bodega'}]]};}},
   q('¿Cuánto comen mis lotes?','cuanto comen'),
   q('Programa de adaptación al concentrado','como adapto al concentrado'),q('¿Cuánta agua necesitan mis lotes?','cuanta agua necesitan'),
   q('¿Cuánto cuesta el kilo ganado?','cuanto cuesta el kilo ganado'),
   CALCS().find(c=>c.l==='Consumo según el peso'),CALCS().find(c=>c.l==='Mezcla de una ración por tandas'),CALCS().find(c=>c.l==='Ensilaje que necesito'),
   opFichas('nutricion'),opFichas('forrajes')]},
 {id:'salud',ic:'salud',t:'Salud',s:'Sanidad, dosis y prevención',intro:'Vamos con la salud del ganado. Para medicinas y casos graves, confirma siempre con tu veterinario.',ops:()=>[
   {l:'¿Qué tiene mi animal? (síntomas)',ic:'sintomas',goArea:'sintomas'},
   {l:'Clima de hoy y riesgo de calor',ic3:'thermometer',fn:climaMsg},
   q('Plan sanitario de ingreso','plan sanitario de ingreso'),
   {l:'Calendario de sanidad de un lote',sub:opLotes('Calendario de sanidad',x=>({fn:()=>calendario(x)}))},
   {l:'Dosis de un medicamento',sub:()=>({t:'Dosis de un medicamento',intro:'¿Qué producto vas a aplicar? Si no está, usa Dosis por peso con los datos de tu etiqueta.',ops:DOSIS.map(d=>({l:d.n,calc:{campos:[cPeso()],frase:v=>`dosis de ${d.re.source.split('|')[0].replace(/\\b/g,'')} para ${v.p} ${uPal()}`,intro:`¿Cuánto pesa el animal? Te calculo la dosis de <b>${esc(d.n)}</b>.`}})).concat([CALCS().find(c=>c.l==='Dosis por peso (según la etiqueta)')])})},
   {l:'Lotes en retiro de medicinas',fn:()=>{const R0=C_().act.filter(x=>x.retiroHasta);return {html:R0.length?'En retiro:'+tabla(['Lote','Producto','Hasta'],R0.map(x=>[esc(x.l.nombre),esc(x.retiroProd||''),ffc(x.retiroHasta)])):'Ningún lote está en retiro.'};}},
   q('¿Cuántos se han muerto?','cuantos se han muerto'),f('Anotar sanidad','sanidad'),
   opFichas('sanidad')]},
 {id:'sintomas',ic:'sintomas',t:'¿Qué tiene mi animal?',s:'Elige lo que ves y te oriento',intro:'¿Qué le ves al animal? Te digo qué puede ser, qué hacer ahora y los temas para saber más.',ops:()=>SINT.map(opSintoma)},
 {id:'calc',ic:'calc',t:'Calculadoras',s:`${CALCS().length} cálculos del engorde`,intro:'Elige qué calculamos. Solo pon los números que te pido.',ops:()=>CALCS()},
 {id:'dinero',ic:'dinero',t:'Dinero y ventas',s:'Márgenes, precios y gastos',intro:'Veamos los números del negocio. ¿Qué quieres revisar?',ops:()=>[
   q('¿Cuánto voy a ganar?','cuanto voy a ganar'),q('Precio mínimo para no perder','precio minimo para no perder'),
   CALCS().find(c=>c.l==='Precio máximo de compra'),CALCS().find(c=>c.l==='Margen de un negocio'),
   {l:'¿Y si sube el maíz?',calc:{campos:[cN('p','Cuánto sube','%',1,200)],frase:v=>`que pasa si el maiz sube ${v.p} %`,intro:'¿Cuánto sube el maíz? Te digo cómo cambia el margen de cada lote.'}},
   q('¿Cuánto cuesta el kilo ganado?','cuanto cuesta el kilo ganado'),q('¿Cuánto he gastado este mes?','cuanto he gastado este mes'),
   q('¿Cuánto he vendido?','cuanto he vendido'),q('¿Cómo mejoro el margen?','como mejorar el margen'),
   CALCS().find(c=>c.l==='Comparar pie y canal'),CALCS().find(c=>c.l==='Desbaste al vender'),CALCS().find(c=>c.l==='Interés de un crédito'),
   f('Anotar una venta','venta'),f('Anotar un gasto','gasto'),
   opFichas('negocio'),opFichas('normas'),opFichas('conceptos')]},
 {id:'guia',ic:'guia',t:'Guía de engorde',s:`${FICHAS.length} temas a fondo`,intro:`Mi guía tiene <b>${FICHAS.length} temas completos</b> con ${nf(FICHAS.reduce((n,f)=>n+nPuntos(f),0))} puntos explicados, más ${GUIA.length} datos rápidos. Cada tema se abre por partes: qué es, señales, qué hacer, prevención, números y errores comunes.`,ops:()=>['actualidad','sanidad','nutricion','manejo','forrajes','instalaciones','negocio','fisiologia','genetica','carne','normas','conceptos','app'].map(opFichas)},
 {id:'app',ic:'app',t:'Usar la app',s:'Recorrido, respaldo y ajustes',intro:'Te enseño a usar Rumentis. ¿Qué necesitas?',ops:()=>[
   q('Hazme el recorrido','hazme el recorrido'),q('¿Por dónde empiezo?','por donde empiezo'),
   q('¿Dónde está el respaldo?','donde esta el respaldo'),q('¿Cómo paso mis datos a Excel?','como exporto a excel'),
   q('¿Cómo cambio el precio de venta?','como cambio el precio de venta'),q('Me equivoqué en un registro','me equivoque en un registro'),
   {l:'Cambiar colores y tema',go:'#mas/apariencia'},{l:'Configuración de la finca',go:'#mas/config'},
   opFichas('app')]}
];
const areaDe=id=>AREAS.find(a=>a.id===id);

/* ---------- estado del menú ---------- */
const M={pila:[]};   // [] = menú principal; cada nivel: {t,ops,area}
const nivel=()=>M.pila[M.pila.length-1]||null;
function abrirArea(a){M.calc=null;M.min=false;M.q='';M.pila=[{t:a.t,area:a.id,ops:a.ops().filter(Boolean)}];botSay({html:a.intro});pintarPanel();}
function entrar(sub,label){
  M.min=false;M.q='';
  const n=sub();if(n.sub){entrar(n.sub,label);return;}
  if(n.fn){responderCon(label,n.fn);return;}
  M.pila.push({t:n.t,area:(nivel()||{}).area,ops:(n.ops||[]).filter(Boolean)});
  if(label)RUMI.log.push({u:label});
  botSay({html:n.intro});pintarPanel();
}
function menu(saludar){M.pila=[];M.calc=null;if(saludar!==false)botSay(saludoMenu(true));pintarPanel();}
function atras(){if(M.calc)M.calc=null;else if(M.pila.length>1)M.pila.pop();else M.pila=[];pintarPanel();}

function pensando(){const m=$('#rumiMsgs');if(m)m.insertAdjacentHTML('beforeend','<div class="rrow">'+rumiSVG('av think')+'<div class="rmsg b typing" aria-label="Rumi está pensando"><i></i><i></i><i></i></div></div>');const b=$('#rumiBody');if(b)b.scrollTop=b.scrollHeight;}
function responderCon(label,fn){
  RUMI.log.push({u:label});pintarLog();pensando();
  setTimeout(()=>{let r;try{r=fn();}catch(e){console.error(e);r=null;}if(!r||!r.html)r={html:'No pude calcular eso con tus datos. Revisa que tengas lotes y registros.'};delete r.sug;botSay(r);M.min=esLargo();pintarPanel();},300);
}
const porFrase=(label,frase)=>responderCon(label,()=>{RUMI.last=nrm(frase);return responder(frase);});

function elegir(op){
  if(!op)return;
  M.calc=null;
  if(op.q)return porFrase(op.l,op.q);
  if(op.fn)return responderCon(op.l,op.fn);
  if(op.form){ejecutar({t:'form',k:op.form,p:op.p});return;}
  if(op.run){if(!op.keep)closeSheet();setTimeout(()=>op.run(),op.keep?0:40);return;}
  if(op.go){closeSheet();location.hash=op.go;if(op.sel)setTimeout(()=>{const e=$(op.sel);if(e)e.scrollIntoView({behavior:'smooth',block:'start'});},400);return;}
  if(op.goArea)return abrirArea(areaDe(op.goArea));
  if(op.guia!=null){const g=GUIA[op.guia];return responderCon(op.l,()=>({html:`<b>${esc(g.t)}</b><br>${esc(g.x)}`}));}
  if(op.sub)return entrar(op.sub,op.l);
  if(op.calc){M.calc=op;M.min=false;RUMI.log.push({u:op.l});botSay({html:op.calc.intro||'Pon los números.',nou:true});pintarPanel();setTimeout(()=>{const i=$('#rmCalc input');if(i)i.focus();},60);}
}

/* ---------- panel inferior ---------- */
const uDe=k=>k.peso?UW():typeof k.u==='function'?k.u():(k.u||'');
function pintarPanel(){
  const p=$('#rumiPanel');if(!p)return;
  const n=nivel();
  const buscar=`<div class="rm-buscar">${i3('buscar')}<input id="rmQ" type="search" placeholder="Buscar tema, cálculo, síntoma o lote…" autocomplete="off" enterkeyhint="search" value="${esc(M.q||'')}"></div><div id="rmRes" class="rm-ops"></div>`;
  if(M.calc){
    const c=M.calc.calc;
    p.innerHTML=locTxt(`<div class="rm-bar">${barBtns()}<b>${esc(M.calc.l)}</b>${togBtn()}</div>
     <form id="rmCalc" class="rm-calc" autocomplete="off">${c.campos.map(k=>`<div class="q"><label for="rmc_${k.k}">${esc(k.l)}</label><div class="unit"><input class="in" id="rmc_${k.k}" name="${k.k}" inputmode="decimal" enterkeyhint="next" value="${k.def!=null?esc(String(k.def)):''}"><em>${esc(uDe(k))}</em></div></div>`).join('')}
     <button class="btn pri full">Calcular</button></form>`);
  }else if(!n){
    p.innerHTML=locTxt(`<div class="rm-bar"><b>¿En qué te ayudo?</b>${togBtn()}</div>${buscar}
     <div class="rm-areas">${AREAS.map(a=>`<button type="button" class="rm-area" data-act="rmArea" data-id="${a.id}" data-c="${a.id}"><i class="a3">${i3(a.id)}</i><b>${a.t}</b><span>${a.s}</span></button>`).join('')}</div>`);
  }else{
    p.innerHTML=locTxt(`<div class="rm-bar">${barBtns()}<b>${esc(n.t)}</b>${togBtn()}</div>${n.ops.length>8?buscar:''}
     <div class="rm-ops rm-lista">${n.ops.map((o,i)=>opHtml(o,'rmOp',i)).join('')}</div>`);
  }
  p.dataset.area=(M.pila[0]||{}).area||'';
  p.classList.toggle('min',!!M.min);
  p.scrollTop=0;
  if(M.q)filtrar(M.q);
  bajar();
}
const opHtml=(o,act,i)=>`<button type="button" class="rm-op${o.guia!=null?' g':''}${o.form?' f':''}" data-act="${act}" data-i="${i}">${o.ic3?`<i class="o3" data-c="${o.ic3}">${i3(o.ic3)}</i>`:o.ic?`<i>${ic(o.ic,2)}</i>`:''}<span>${o.pre?`<small class="pre">${esc(o.pre)}</small>`:''}${esc(o.l)}${o.small?`<small>${esc(o.small)}</small>`:''}</span>${o.n?`<em class="rm-n">${o.n}</em>`:''}${ic('chev',2.2)}</button>`;
const togBtn=()=>`<button type="button" class="rm-tog" data-act="rmTog" aria-label="${M.min?'Ver opciones':'Ocultar opciones'}">${M.min?'<span>Ver opciones</span>':''}<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${M.min?'M6 15l6-6 6 6':'M6 9l6 6 6-6'}"/></svg></button>`;
const esLargo=()=>{const m=$('#rumiMsgs'),b=$('#rumiBody');if(!m||!b)return false;const r=m.querySelectorAll('.rrow');const l=r[r.length-1];return !!l&&l.offsetHeight>innerHeight*0.38;};
/* ---------- buscador ---------- */
const nq=t=>String(t||'').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9ñ ]+/g,' ').replace(/\s+/g,' ').trim();
let IDX=null;
function indice(){
  if(IDX)return IDX;const L=[];
  for(const a of AREAS){if(a.id==='guia'||a.id==='lotes')continue;let ops=[];try{ops=a.ops();}catch(e){}
    for(const o of ops.filter(Boolean))if(!o.n||!o.sub)L.push({o,t:o.l,x:'',area:a.t,peso:3});}
  for(const c of CALCS())if(!L.some(e=>e.o.l===c.l))L.push({o:c,t:c.l,x:'calculadora calcular',area:'Calculadoras',peso:3});
  for(const sn of SINT)L.push({o:opSintoma(sn),t:sn[0],x:sn[1].join(' ')+' '+sn[2].join(' '),area:'¿Qué tiene mi animal?',peso:3});
  FICHAS.forEach((f,i)=>L.push({o:{l:f.t,ic3:f.a,sub:()=>fichaNivel(i)},t:f.t,x:f.r+' '+f.s.map(z=>z.h+' '+z.p.join(' ')).join(' '),area:SUB[f.a]||'Guía',peso:2}));
  GUIA.forEach((g,i)=>L.push({o:{l:g.t,guia:i},t:g.t,x:g.x,area:'Dato rápido · '+(SUB[g.a]||''),peso:1}));
  for(const e of L){e.nt=nq(e.t);e.nx=nq(e.x);}
  return IDX=L;
}
function filtrar(qq){
  const box=$('#rmRes'),p=$('#rumiPanel');if(!box||!p)return;
  const q=nq(qq);M.q=qq;p.classList.toggle('buscando',q.length>=2);
  if(q.length<2){box.innerHTML='';return;}
  const pal=q.split(' ').filter(w=>w.length>1);
  const lotes=C_().act.filter(x=>nq(x.l.nombre).includes(q)).map(x=>({o:{l:x.l.nombre,small:'Mis lotes',ic3:'lotes',sub:()=>loteNivel(x)},sc:100}));
  const R=indice().map(e=>{let sc=0;for(const w of pal){if(e.nt.includes(w))sc+=10*e.peso;else if(e.nx.includes(w))sc+=2*e.peso;else return null;}if(e.nt.startsWith(pal[0]))sc+=15;return {o:{...e.o,small:e.o.small||e.area},sc};}).filter(Boolean).sort((a,b)=>b.sc-a.sc);
  M.res=lotes.concat(R).slice(0,40).map(r=>r.o);
  box.innerHTML=M.res.length?`<p class="rm-rc">${M.res.length>=40?'Los 40 mejores resultados':pl(M.res.length,'resultado','resultados')}</p>`+M.res.map((o,i)=>opHtml(o,'rmRes',i)).join(''):'<p class="rm-rc">No encontré nada con esas palabras. Prueba con otra, por ejemplo: acidosis, dosis, margen, pesaje.</p>';
}
document.addEventListener('input',e=>{if(e.target.id==='rmQ')filtrar(e.target.value);});
document.addEventListener('keydown',e=>{if(e.target.id==='rmQ'&&e.key==='Enter'){e.preventDefault();e.target.blur();}});
/* siempre mostrar el mensaje más reciente de Rumi: si es corto, al fondo; si es largo, desde su inicio */
function bajar(){
  const go=()=>{const b=$('#rumiBody'),m=$('#rumiMsgs');if(!b||!m)return;const rows=m.querySelectorAll('.rrow');const last=rows[rows.length-1];
    if(!last){b.scrollTop=b.scrollHeight;return;}
    const top=last.getBoundingClientRect().top-b.getBoundingClientRect().top+b.scrollTop-10;
    const alto=last.offsetHeight>b.clientHeight*0.75;
    b.scrollTo({top:alto?top:b.scrollHeight,behavior:'smooth'});};
  requestAnimationFrame(()=>requestAnimationFrame(go));setTimeout(go,260);
}
const barBtns=()=>`<button type="button" class="rm-menu" data-act="rmMenu">${ic('menu',2)}<span>Menú</span></button>${M.pila.length>1||M.calc?`<button type="button" class="rm-atras" data-act="rmAtras" aria-label="Atrás">${ic('back',2.4)}</button>`:''}`;

document.addEventListener('submit',e=>{
  if(e.target.id!=='rmCalc')return;e.preventDefault();
  const op=M.calc;if(!op)return;const v={};
  for(const k of op.calc.campos){const raw=e.target.elements[k.k].value.trim();const x=raw===''&&k.def!=null?+k.def:num(raw);
    if(!(x>=k.min&&x<=k.max)){toast(`Escribe ${k.l.toLowerCase()} entre ${nf(k.min)} y ${nf(k.max)}.`);e.target.elements[k.k].focus();return;}v[k.k]=x;}
  const label=op.calc.campos.map(k=>`${k.l}: ${nf(v[k.k],2)} ${uDe(k)}`).join(', ');
  M.calc=null;
  if(op.calc.fn)responderCon(label,()=>op.calc.fn(v));else porFrase(label,op.calc.frase(v));
},true);
document.addEventListener('keydown',e=>{if(e.key!=='Enter'||!e.target.closest||!e.target.closest('#rmCalc'))return;const ins=$$('#rmCalc input');const i=ins.indexOf(e.target);if(i<ins.length-1){e.preventDefault();ins[i+1].focus();}});

/* ---------- saludo ---------- */
function saludoMenu(corto){
  const h=new Date().getHours(),sal=h<12?'Buenos días':h<19?'Buenas tardes':'Buenas noches';
  const nuevo=!Object.keys(S.lotes).length;
  if(corto)return {html:'Volvimos al menú. Elige un área.',nou:true};
  return {html:`${sal}, soy <b>Rumi</b>, tu ayudante de engorde. Elige un área y te muestro todo lo que puedo hacer ahí: tus números, cada lote y sus animales, síntomas, ${CALCS().length} calculadoras y una guía con ${FICHAS.length} temas a fondo. Todo sin internet.${nuevo?' Si vas empezando, entra a <b>Usar la app</b>.':''}`,nou:true};
}

/* ---------- reemplazos del Rumi de texto ---------- */
window.abrirRumi=abrirRumi=function(pre){
  const head=`<div class="sh-head"><div class="r"><span>Tu ayudante de engorde, sin internet</span><button type="button" class="x" data-act="cerrar" aria-label="Cerrar">${ico('x',2.2)}</button></div><h2 id="shTitle" tabindex="-1" autofocus style="outline:none;display:flex;align-items:center;gap:10px"><span class="rumi-hd">${rumiSVG('bob')}</span>Rumi</h2></div>`;
  openSheet(head+`<div class="sh-body" id="rumiBody"><div id="rumiMsgs" class="rumi-msgs" aria-live="polite"></div></div><div class="sh-foot rumi-panel" id="rumiPanel"></div>`);
  $('#sheetBody').style.height=innerWidth>=760?'min(92vh,760px)':'100%';
  if(!RUMI.log.length){M.pila=[];M.calc=null;botSay(saludoMenu());}else pintarLog();
  pintarPanel();
  if(pre){const op=typeof pre==='string'?{l:pre.replace(/^./,c=>c.toUpperCase()),q:pre}:pre;elegir(op);}
};
window.pintarSug=pintarSug=function(){};
window.saludo=saludo=()=>saludoMenu();
window.capacidades=capacidades=()=>({html:'Todo lo que hago está en mi menú: '+AREAS.map(a=>`<b>${a.t}</b>`).join(', ')+'.',nou:true});
window.escuchar=escuchar=function(){abrirRumi();};
window.rumiVoz=function(){abrirRumi();};
Object.assign(ACTS,{
  rumi:()=>abrirRumi(),
  rumiAsk:el=>elegir({l:el.dataset.q,q:el.dataset.q}),
  rumiAskOpen:el=>abrirRumi(el.dataset.q),
  rumiVoz:()=>{},
  rmArea:el=>abrirArea(areaDe(el.dataset.id)),
  rmOp:el=>{const n=nivel();if(n)elegir(n.ops[+el.dataset.i]);},
  rmMenu:()=>{M.q='';menu();},
  rmTog:()=>{M.min=!M.min;pintarPanel();},
  rmRes:el=>{const o=(M.res||[])[+el.dataset.i];M.q='';M.min=false;if(o)elegir(o);},
  rmAtras:()=>atras()
});
window.RumiMenu={AREAS,CALCS,SINT,FICHAS,elegir,menu,abrirArea,M,clima:()=>responderCon('¿Cómo está el clima?',climaMsg)};
})();
