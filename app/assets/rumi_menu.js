/* Rumi por menús.
   Rumi ya no recibe texto ni voz: saluda, muestra sus áreas y, dentro de cada área,
   las preguntas y acciones que resuelve. Cada opción llama a una respuesta fija de la app
   (responder con una frase conocida, una función directa, un formulario o un tema de la guía). */
(function(){
'use strict';
const GUIA=window.RUMI_GUIA||[];
const uPal=()=>U()==='lb'?'libras':'kilos';
const IC2={
 hoy:'<path d="M12 3v2M12 19v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M3 12h2M19 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4"/><circle cx="12" cy="12" r="4"/>',
 anotar:'<path d="M9 4h6l1 2h3v15H5V6h3z"/><path d="M9 12h6M9 16h4"/>',
 alimento:'<path d="M4 9h16l-1.5 10a2 2 0 0 1-2 1.7h-9a2 2 0 0 1-2-1.7L4 9z"/><path d="M8 9V6a4 4 0 0 1 8 0v3"/>',
 salud:'<path d="M12 21s-7-4.5-9-9.5C1.7 8 4 4.5 7.5 4.5c2 0 3.5 1 4.5 2.5 1-1.5 2.5-2.5 4.5-2.5C20 4.5 22.3 8 21 11.5 19 16.5 12 21 12 21z"/><path d="M12 9v6M9 12h6"/>',
 calc:'<rect x="5" y="3" width="14" height="18" rx="2.5"/><path d="M8.5 7h7M8.5 11h.01M12 11h.01M15.5 11h.01M8.5 14.5h.01M12 14.5h.01M15.5 14.5h.01M8.5 18h.01M12 18h3.5"/>',
 dinero:'<path d="M3 7h18v10H3z"/><circle cx="12" cy="12" r="2.6"/><path d="M6 10v4M18 10v4"/>',
 guia:'<path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5z"/><path d="M4 20.5A2.5 2.5 0 0 0 6.5 23H20v-5"/><path d="M9 8h7M9 11.5h5"/>',
 app:'<rect x="6" y="2.5" width="12" height="19" rx="2.5"/><path d="M10.5 18.5h3"/>',
 menu:'<rect x="3.5" y="3.5" width="7" height="7" rx="2"/><rect x="13.5" y="3.5" width="7" height="7" rx="2"/><rect x="3.5" y="13.5" width="7" height="7" rx="2"/><rect x="13.5" y="13.5" width="7" height="7" rx="2"/>',
 back:'<path d="M15 5l-7 7 7 7"/>',chev:'<path d="M9 5l7 7-7 7"/>',libro:'<path d="M6 4h11a1 1 0 0 1 1 1v15H7a2 2 0 0 1-2-2V5a1 1 0 0 1 1-1z"/><path d="M9 8h6"/>'
};
const ic=(k,sw=2)=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${IC2[k]||''}</svg>`;

/* ---------- temas de la guía ---------- */
const SUB={sanidad:'Sanidad',nutricion:'Nutrición',manejo:'Manejo del ganado',forrajes:'Pastos y forrajes',instalaciones:'Corrales e instalaciones',negocio:'Negocio y precios',normas:'Normas y buenas prácticas',conceptos:'Palabras del engorde',app:'Temas de la app'};
const temas=a=>GUIA.map((g,i)=>({g,i})).filter(o=>o.g.a===a);
const opGuia=a=>({l:`${SUB[a]} (${temas(a).length} temas)`,ic:'libro',sub:()=>({t:SUB[a],intro:`Estos son mis temas de <b>${SUB[a].toLowerCase()}</b>. Elige uno y te lo explico.`,ops:temas(a).map(o=>({l:o.g.t,guia:o.i}))})});

/* ---------- respuestas ---------- */
const q=(l,frase)=>({l,q:frase});
const f=(l,k,p)=>({l,form:k,p:p||{}});
const lotesAct=()=>calc().act;
const sinLotes=()=>({html:'Todavía no tienes lotes en engorde. Crea el primero y aquí verás sus números.',btns:[['Crear lote',{t:'form',k:'lote',p:{}}]]});
const opLotes=(t,fn)=>()=>{const L=lotesAct();if(!L.length)return {t,intro:'Todavía no tienes lotes en engorde.',ops:[f('Crear un lote','lote')]};return {t,intro:'¿De qué lote?',ops:L.map(x=>({l:`${x.l.nombre} · ${pl(x.cab,'cabeza','cabezas')}`,sub:()=>fn(x)}))};};
const opProy=opLotes('Proyectar un lote',x=>({t:x.l.nombre,intro:`¿A cuántos días proyecto <b>${esc(x.l.nombre)}</b>?`,ops:[15,30,45,60,90].map(d=>({l:`En ${d} días`,fn:()=>proyeccion(nrm(`cuanto pesara en ${d} dias`),{lote:x.id})}))}));

/* formulario pequeño para calculadoras: campos numéricos y una frase que entiende responder */
const calcOp=(l,campos,frase,intro)=>({l,calc:{campos,frase,intro}});
const cPeso=(k='p',lab='Peso del animal')=>({k,l:lab,u:()=>UW(),min:50,max:2000});

const AREAS=[
 {id:'hoy',ic:'hoy',t:'Mi engorde',s:'Pendientes, lotes y números',intro:'Aquí te digo cómo va tu engorde con tus propios datos. ¿Qué quieres saber?',ops:()=>[
   q('¿Qué hago hoy?','que hago hoy'),q('¿Qué lote está listo para vender?','que lote esta listo'),
   {l:'¿Cómo va un lote?',sub:opLotes('¿Cómo va un lote?',x=>({fn:()=>loteMsg(x)}))},
   {l:'Proyectar un lote',sub:opProy},
   q('¿Cuántas cabezas tengo?','cuantas cabezas tengo'),q('¿Cuál es mi mejor lote?','cual es mi mejor lote'),
   q('¿Cuánto suben por día?','cuanto suben por dia'),q('¿Cuál es mi conversión?','cual es mi conversion'),
   q('¿Cuántos se han muerto?','cuantos se han muerto'),q('Resumen del mes','resumen del mes'),
   q('¿Cada cuánto debo pesar?','cada cuanto peso'),q('¿Cuántas vueltas al año da mi corral?','cuantas vueltas al ano')]},
 {id:'anotar',ic:'anotar',t:'Anotar',s:'Te abro el formulario',intro:'¿Qué quieres anotar? Te abro el formulario listo para llenar.',ops:()=>[
   f('Entrega de alimento','alimento'),f('Pesaje','pesaje'),f('Nuevo lote de animales','lote'),f('Sanidad: vacuna o tratamiento','sanidad'),
   f('Venta','venta'),f('Muerte o baja','baja'),f('Gasto','gasto'),f('Nueva ración','racion'),
   {l:'Me equivoqué en un registro',q:'me equivoque en un registro'}]},
 {id:'alimento',ic:'alimento',t:'Alimentación',s:'Dietas, consumo y agua',intro:'Hablemos de la comida de tus animales. ¿Qué necesitas?',ops:()=>[
   q('Formular una dieta barata','formula una dieta'),{l:'Abrir el formulador de dietas',go:'#formular'},
   q('¿Cuánto alimento necesito para el mes?','cuanto alimento para el mes'),q('¿Cuánto comen mis lotes?','cuanto comen'),
   q('¿Cómo los adapto al concentrado?','como adapto al concentrado'),q('¿Cuánta agua necesitan?','cuanta agua necesitan'),
   q('¿Cuánto cuesta el kilo ganado?','cuanto cuesta el kilo ganado'),
   opGuia('nutricion'),opGuia('forrajes')]},
 {id:'salud',ic:'salud',t:'Salud',s:'Sanidad, dosis y enfermos',intro:'Vamos con la salud del ganado. Para medicinas y casos graves, confirma siempre con tu veterinario.',ops:()=>[
   q('Plan sanitario de ingreso','plan sanitario de ingreso'),
   {l:'Dosis de un medicamento',sub:()=>({t:'Dosis de un medicamento',intro:'¿Qué producto vas a aplicar?',ops:DOSIS.map(d=>calcOp(d.n,[cPeso()],v=>`dosis de ${d.re.source.split('|')[0].replace(/\\b/g,'')} para ${v.p} ${uPal()}`,`¿Cuánto pesa el animal? Te calculo la dosis de <b>${esc(d.n)}</b>.`))})},
   q('Tengo un animal con tos','tengo un novillo con tos'),q('Un animal no come','un animal no come'),
   q('¿Cuántos se han muerto?','cuantos se han muerto'),f('Anotar sanidad','sanidad'),
   opGuia('sanidad')]},
 {id:'calc',ic:'calc',t:'Calculadoras',s:'Calor, espacio, pesos y más',intro:'Elige qué calculamos. Solo pon los números que te pido.',ops:()=>[
   calcOp('Índice de calor',[{k:'t',l:'Temperatura',u:()=>'°C',min:10,max:50},{k:'h',l:'Humedad',u:()=>'%',min:5,max:100}],v=>`indice de calor con ${v.t} grados y ${v.h} % de humedad`,'Dime la temperatura y la humedad y te digo el riesgo para tus animales.'),
   calcOp('Espacio de corral y comedero',[{k:'n',l:'Cabezas',u:()=>'cab.',min:1,max:5000}],v=>`espacio de comedero para ${v.n} cabezas`,'¿Para cuántas cabezas calculo el corral, el comedero, la sombra y el bebedero?'),
   calcOp('Convertir libras a kilos',[{k:'v',l:'Libras',u:()=>'lb',min:0.1,max:1e6}],v=>`${v.v} libras a kilos`,'¿Cuántas libras convierto a kilos?'),
   calcOp('Convertir kilos a libras',[{k:'v',l:'Kilos',u:()=>'kg',min:0.1,max:1e6}],v=>`${v.v} kilos a libras`,'¿Cuántos kilos convierto a libras?'),
   calcOp('Rendimiento en canal',[cPeso('p','Peso vivo')],v=>`rendimiento en canal de ${v.p} ${uPal()}`,'¿Cuánto pesa el animal vivo? Te digo cuánto da en canal.'),
   {l:'Dosis de un medicamento',goArea:'salud'},
   q('¿Cuánto alimento necesito para el mes?','cuanto alimento para el mes'),q('¿Cuánta agua necesitan?','cuanta agua necesitan')]},
 {id:'dinero',ic:'dinero',t:'Dinero y ventas',s:'Márgenes, precios y gastos',intro:'Veamos los números del negocio. ¿Qué quieres revisar?',ops:()=>[
   q('¿Cuánto voy a ganar?','cuanto voy a ganar'),q('Precio mínimo para no perder','precio minimo para no perder'),
   calcOp('Precio máximo de compra',[cPeso('p','Peso de compra')],v=>`a cuanto puedo comprar novillos de ${v.p} ${uPal()}`,'¿De qué peso son los animales que piensas comprar? Te digo cuánto puedes pagar como máximo.'),
   calcOp('¿Y si sube el maíz?',[{k:'p',l:'Cuánto sube',u:()=>'%',min:1,max:200}],v=>`que pasa si el maiz sube ${v.p} %`,'¿Cuánto sube el maíz? Te digo cómo cambia el margen de cada lote.'),
   q('¿Cuánto cuesta el kilo ganado?','cuanto cuesta el kilo ganado'),q('¿Cuánto he gastado este mes?','cuanto he gastado este mes'),
   q('¿Cuánto he vendido?','cuanto he vendido'),q('¿Cómo mejoro el margen?','como mejorar el margen'),
   f('Anotar una venta','venta'),f('Anotar un gasto','gasto'),
   opGuia('negocio'),opGuia('normas')]},
 {id:'guia',ic:'guia',t:'Guía de engorde',s:`${GUIA.length} temas explicados`,intro:`Mi guía de engorde tiene <b>${GUIA.length} temas</b> explicados en palabras sencillas. Elige un área.`,ops:()=>['sanidad','nutricion','manejo','forrajes','instalaciones','negocio','normas','conceptos'].map(opGuia)},
 {id:'app',ic:'app',t:'Usar la app',s:'Recorrido, respaldo y ajustes',intro:'Te enseño a usar Rumentis. ¿Qué necesitas?',ops:()=>[
   q('Hazme el recorrido','hazme el recorrido'),q('¿Por dónde empiezo?','por donde empiezo'),
   q('¿Dónde está el respaldo?','donde esta el respaldo'),q('¿Cómo paso mis datos a Excel?','como exporto a excel'),
   q('¿Cómo cambio el precio de venta?','como cambio el precio de venta'),q('Me equivoqué en un registro','me equivoque en un registro'),
   {l:'Cambiar colores y tema',go:'#mas',sel:'#apariencia'},
   opGuia('app')]}
];
const areaDe=id=>AREAS.find(a=>a.id===id);

/* ---------- estado del menú ---------- */
const M={pila:[]};   // [] = menú principal; cada nivel: {t,intro,ops,ic}
const nivel=()=>M.pila[M.pila.length-1]||null;
function abrirArea(a){M.pila=[{t:a.t,ic:a.ic,ops:a.ops()}];botSay({html:a.intro,nou:true});pintarPanel();}
function entrar(sub,label){
  const n=sub();if(n.sub){entrar(n.sub,label);return;}
  if(n.fn){responderCon(label,n.fn);return;}
  M.pila.push({t:n.t,ic:(nivel()||{}).ic,ops:n.ops});
  if(label)RUMI.log.push({u:label});
  botSay({html:n.intro,nou:true});pintarPanel();
}
function menu(saludar){M.pila=[];M.calc=null;if(saludar!==false)botSay(saludoMenu(true));pintarPanel();}
function atras(){M.calc=null;if(M.pila.length>1){M.pila.pop();}else M.pila=[];pintarPanel();}

function pensando(){const m=$('#rumiMsgs');if(m)m.insertAdjacentHTML('beforeend','<div class="rrow">'+rumiSVG('av think')+'<div class="rmsg b typing" aria-label="Rumi está pensando"><i></i><i></i><i></i></div></div>');const b=$('#rumiBody');if(b)b.scrollTop=b.scrollHeight;}
function responderCon(label,fn){
  RUMI.log.push({u:label});pintarLog();pensando();
  setTimeout(()=>{let r;try{r=fn();}catch(e){r=null;}if(!r||!r.html)r={html:'No pude calcular eso con tus datos. Revisa que tengas lotes y registros.'};delete r.sug;botSay(r);pintarPanel();},320);
}
const porFrase=(label,frase)=>responderCon(label,()=>{RUMI.last=nrm(frase);return responder(frase);});

function elegir(op){
  if(!op)return;
  M.calc=null;
  if(op.q)return porFrase(op.l,op.q);
  if(op.fn)return responderCon(op.l,op.fn);
  if(op.form){ejecutar({t:'form',k:op.form,p:op.p});return;}
  if(op.go){closeSheet();location.hash=op.go;if(op.sel)setTimeout(()=>{const e=$(op.sel);if(e)e.scrollIntoView({behavior:'smooth',block:'start'});},350);return;}
  if(op.goArea)return abrirArea(areaDe(op.goArea));
  if(op.guia!=null){const g=GUIA[op.guia];return responderCon(op.l,()=>({html:`<b>${esc(g.t)}</b><br>${esc(g.x)}`}));}
  if(op.sub)return entrar(op.sub,op.l);
  if(op.calc){M.calc=op;RUMI.log.push({u:op.l});botSay({html:op.calc.intro,nou:true});pintarPanel();setTimeout(()=>{const i=$('#rmCalc input');if(i)i.focus();},60);}
}

/* ---------- panel inferior ---------- */
function pintarPanel(){
  const p=$('#rumiPanel');if(!p)return;
  const n=nivel();
  if(!n){
    p.innerHTML=`<div class="rm-bar"><b>¿En qué te ayudo?</b><span>Elige un área</span></div>
     <div class="rm-areas">${AREAS.map(a=>`<button type="button" class="rm-area" data-act="rmArea" data-id="${a.id}" data-c="${a.id}"><i>${ic(a.ic,2)}</i><b>${a.t}</b><span>${a.s}</span></button>`).join('')}</div>`;
  }else if(M.calc){
    const c=M.calc.calc;
    p.innerHTML=`<div class="rm-bar">${barBtns()}<b>${esc(M.calc.l)}</b></div>
     <form id="rmCalc" class="rm-calc" autocomplete="off">${c.campos.map(k=>`<div class="q"><label for="rmc_${k.k}">${esc(k.l)}</label><div class="unit"><input class="in" id="rmc_${k.k}" name="${k.k}" inputmode="decimal" enterkeyhint="done" required><em>${esc(k.u())}</em></div></div>`).join('')}
     <button class="btn pri full">Calcular</button></form>`;
  }else{
    p.innerHTML=`<div class="rm-bar">${barBtns()}<b>${esc(n.t)}</b></div>
     <div class="rm-ops">${n.ops.map((o,i)=>`<button type="button" class="rm-op${o.guia!=null?' g':''}${o.form?' f':''}" data-act="rmOp" data-i="${i}">${o.ic?`<i>${ic(o.ic,2)}</i>`:''}<span>${esc(o.l)}</span>${ic('chev',2.2)}</button>`).join('')}</div>`;
  }
  p.dataset.area=(M.pila[0]&&AREAS.find(a=>a.t===M.pila[0].t)||{}).id||'';
  p.scrollTop=0;const ops=$('.rm-ops,.rm-areas',p);if(ops)ops.scrollTop=0;
}
const barBtns=()=>`<button type="button" class="rm-menu" data-act="rmMenu">${ic('menu',2)}<span>Menú</span></button>${M.pila.length>1||M.calc?`<button type="button" class="rm-atras" data-act="rmAtras" aria-label="Atrás">${ic('back',2.4)}</button>`:''}`;

document.addEventListener('submit',e=>{
  if(e.target.id!=='rmCalc')return;e.preventDefault();
  const op=M.calc;if(!op)return;const v={};
  for(const k of op.calc.campos){const x=num(e.target.elements[k.k].value);if(!(x>=k.min&&x<=k.max)){toast(`Escribe ${k.l.toLowerCase()} entre ${nf(k.min)} y ${nf(k.max)}.`);e.target.elements[k.k].focus();return;}v[k.k]=x;}
  const frase=op.calc.frase(v);
  const label=op.calc.campos.map(k=>`${k.l}: ${nf(v[k.k],2)} ${k.u()}`).join(', ');
  M.calc=null;porFrase(label,frase);
},true);

/* ---------- saludo ---------- */
function saludoMenu(corto){
  const h=new Date().getHours(),sal=h<12?'Buenos días':h<19?'Buenas tardes':'Buenas noches';
  const nuevo=!Object.keys(S.lotes).length;
  if(corto)return {html:'Volvimos al menú. Elige un área.',nou:true};
  return {html:`${sal}, soy <b>Rumi</b>, tu ayudante de engorde. Trabajo con este menú: eliges un área y te muestro todo lo que puedo hacer ahí, con tus propios datos y sin internet.${nuevo?' Si vas empezando, entra a <b>Usar la app</b>.':''}`,nou:true};
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
  rmMenu:()=>menu(),
  rmAtras:()=>atras()
});
window.RumiMenu={AREAS,elegir,menu,abrirArea,M};
})();
