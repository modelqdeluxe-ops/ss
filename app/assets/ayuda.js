/* Ayuda de Rumi dentro de la app.
   - Cada formulario trae arriba "¿Qué anoto aquí?" con la explicación de Rumi (abierta las primeras veces)
     y una nota corta debajo de cada campo.
   - Cada página tiene un botoncito de Rumi que hace un tutorial corto de esa página. Al terminarlo
     desaparece; se puede repetir desde Rumi, Usar la app, Muéstrame la app. */
(function(){
'use strict';
const LS=(k,v)=>{try{if(v===undefined)return JSON.parse(localStorage.getItem(k)||'null');localStorage.setItem(k,JSON.stringify(v));}catch(e){return null;}};

/* ================= formularios ================= */
const LECT_AYUDA='Debajo de cada lote marca cómo estaba el comedero <b>antes</b> de servir: <b>Vacío</b>, se lo acabaron y esperaron (dales un poco más); <b>Limpio</b>, comieron justo lo que necesitan; <b>Poco</b>, sobró un poquito (está bien); <b>Sobra</b>, sobró mucho (dales menos). Es opcional, pero con esto te digo si estás dando de más o de menos.';
const F={
 alimento:{i:'Anota la comida que serviste en <b>una</b> de tus entregas del día, por ejemplo la de las 6:00. Con esto sé cuánto comen tus lotes, cuánto te cuesta y cuánto te queda en la bodega.',
  p:['Escribe el <b>total</b> que serviste a cada lote en esta entrega, no lo de un animal.','¿Das siempre lo mismo? Toca <b>Llenar con lo de siempre</b> y pongo el promedio de los últimos días. Solo corrige lo que cambió.','Si un lote no comió en esta entrega, deja su casilla vacía.',LECT_AYUDA],
  c:{f:'El día en que serviste la comida. Casi siempre es hoy.',n:'Cuál de tus horarios de comida es. Los horarios se cambian en Más, Configuración.',racion:'La mezcla que serviste. «La de cada lote» usa la ración que tiene asignada cada lote; de ahí sale el costo.',costoKg:'Lo que te cuesta el alimento. Lo uso para sacar el costo de cada lote.'}},
 pesaje:{i:'Anota cuánto pesaron tus animales. Con dos pesajes calculo cuánto ganan por día, cuándo llegan a la meta y cuánto te cuesta cada kilo ganado.',
  p:['Si pesaste a todos juntos o a un grupo, escribe el peso y cuántos animales se pesaron; yo saco el promedio.','Pesa siempre a la misma hora, de preferencia temprano y antes de comer, para comparar bien.'],
  c:{lote:'El lote que pesaste.',modo:'Si escribes el peso promedio por animal o el total de todos los que subieron a la báscula.',val:'El número que marcó la báscula.',cab:'Cuántos animales se pesaron.',f:'El día del pesaje.'}},
 pesajeInd:{i:'Anota el peso de cada animal del lote. Así encuentro a los que se atrasan y te digo cuáles vender primero.',
  p:['Escribe el arete en la <b>lupa</b> y presiona Enter: salto directo a su peso. Al presionar Enter en el peso vuelves a la lupa.','Toca <b>Faltan</b> para ver solo los que no has pesado.','Los que dejes vacíos no cambian; puedes terminar otro día.','Si un peso es muy distinto al anterior, te aviso para que lo revises.'],
  c:{f:'El día del pesaje.'}},
 pesoAnimal:{i:'Anota el peso de este animal. Lo comparo con su pesaje anterior para saber cuánto gana por día.',c:{f:'El día del pesaje.'}},
 sanidad:{i:'Anota vacunas, desparasitantes, vitaminas o tratamientos. Así llevo su costo, te recuerdo los refuerzos y te aviso de los días de retiro antes de vender.',
  p:['Si solo trataste a unos animales, elige <b>Algunos</b> y toca sus aretes.','Los días de retiro vienen en la etiqueta. Mientras dure el retiro no te dejo vender a matadero sin avisarte.'],
  c:{lote:'El lote de los animales tratados.',clase:'Qué tipo de producto: vacuna, desparasitante, vitamina, antibiótico u otro.',producto:'El nombre como sale en la etiqueta.',aplica:'Si se aplicó a todo el lote o solo a algunos animales.',animales:'Toca los aretes de los animales que trataste.',cab:'Cuántos animales recibieron el producto.',costo:'Lo que costó en total: producto y aplicación.',retiro:'Días que deben pasar antes de vender a matadero. Viene en la etiqueta.',f:'El día en que lo aplicaste.',nota:'Opcional: dosis, quién lo aplicó, cómo reaccionaron.'}},
 baja:{i:'Anota los animales que murieron. Ajusto las cabezas del lote, su costo y la mortalidad, y te aviso si se repite una causa.',
  c:{lote:'El lote del animal que murió.',cab:'Cuántos animales murieron.',causa:'Lo que crees que pasó. Si no sabes, elige «No se sabe».',f:'El día en que murió.',nota:'Opcional: síntomas, lo que le diste, lo que dijo el veterinario.'}},
 bajaAn:{i:'Anota un animal que murió. Ajusto las cabezas del lote, su costo y la mortalidad, y te aviso si se repite una causa.',
  c:{animales:'Toca el arete del animal que murió.',causa:'Lo que crees que pasó. Si no sabes, elige «No se sabe».',f:'El día en que murió.',nota:'Opcional: síntomas, lo que le diste, lo que dijo el veterinario.'}},
 venta:{i:'Anota los animales que vendiste. Calculo lo que ganaste por cabeza y, cuando vendes todo el lote, lo cierro con su resultado final.',
  p:['Escribe el peso y el precio tal como te los pagaron. Si el comprador pesó en tu finca, descuento el desbaste de Configuración.'],
  c:{lote:'El lote de los animales vendidos.',cab:'Cuántos animales vendiste.',modo:'Dónde y cómo los pesaron: en tu finca (peso lleno) o en la báscula del comprador.',val:'El peso que te pagaron.',precio:'Lo que te pagaron por cada unidad de peso.',f:'El día de la venta.',comprador:'Opcional. Me sirve para comparar compradores.',nota:'Opcional.'}},
 ventaAn:{i:'Anota los animales que vendiste. Calculo lo que ganaste por cabeza y, cuando vendes todo el lote, lo cierro con su resultado final.',
  p:['Toca los aretes vendidos, o «Todos» si salió el lote completo.','Escribe el peso y el precio tal como te los pagaron.'],
  c:{animales:'Toca los aretes de los animales vendidos.',modo:'Dónde y cómo los pesaron: en tu finca (peso lleno) o en la báscula del comprador.',val:'El peso que te pagaron, de todos los animales vendidos juntos.',precio:'Lo que te pagaron por cada unidad de peso.',f:'El día de la venta.',comprador:'Opcional. Me sirve para comparar compradores.'}},
 gasto:{i:'Anota gastos que no son comida ni medicinas: sueldos, luz, combustible, reparaciones, fletes. Si es de un lote se suma a su costo; si es general lo reparto entre todos los lotes.',
  c:{monto:'Cuánto pagaste.',cat:'De qué es el gasto.',lote:'El lote al que pertenece, o «General» si es de toda la finca.',f:'El día del gasto.',desc:'Opcional: una nota para acordarte.'}},
 lote:{i:'Un lote es un grupo de animales que entraron juntos. Con él sigo su peso, su comida y sus costos, y te digo cuándo venderlos y cuánto vas a ganar.',
  p:['Si tus animales tienen arete, agrégalos uno por uno con su peso de entrada. Si no, escribe solo cuántos son y su peso promedio.','El precio de compra y el flete son importantes: con ellos calculo tu ganancia real.'],
  c:{nombre:'Un nombre fácil de reconocer, como «Lote 14» o «Novillos de marzo».',f:'El día en que llegaron a la finca.',meta:'El peso por animal al que piensas venderlos.',tipo:'Qué animales son: novillos, toretes, vaquillas…',proveedor:'A quién se los compraste. Así comparo qué proveedor te rinde más.',racion:'La mezcla que van a comer.',precio:'Lo que pagaste por los animales.',flete:'Transporte, comisión y otros gastos de la compra, del lote completo.'}},
 animales:{i:'Agrega animales al lote con su arete y su peso de entrada. Así puedo seguir a cada uno.',p:['Si tienes muchos, escribe el arete, pasa al peso y sigue con el siguiente sin tocar nada más.']},
 animal:{i:'Los datos de este animal. Puedes corregir su arete, su peso de entrada o anotar su raza y color.',c:{arete:'El número o código del arete.',p0:'Lo que pesó al llegar a la finca.',raza:'Opcional. Me sirve para comparar razas.',color:'Opcional. Ayuda a reconocerlo en el corral.',nota:'Opcional.'}},
 racion:{i:'Una ración es la mezcla que les das de comer. Con su costo calculo cuánto te cuesta alimentar cada lote y cada kilo ganado.',
  p:['Si escribes los ingredientes con su porcentaje, la bodega descuenta cada uno según lo que sirves.'],
  c:{nombre:'Un nombre para reconocerla, como «Inicio» o «Finalización».',costo:'Lo que te cuesta cada unidad de la mezcla ya hecha.',ing_n:'El nombre del ingrediente.',ing_p:'Qué parte de la mezcla es, en porcentaje.'}},
 bodegaItem:{i:'Agrega un producto de tu bodega de alimento: maíz, concentrado, heno, harina… Llevo cuánto te queda y te aviso antes de que se acabe.',
  c:{n:'El nombre del producto.',ref:'Si es una ración o un ingrediente de tus raciones, lo descuento solo cuando anotas alimento.',min:'Cuántos días antes de que se acabe quieres que te avise.'}},
 bodegaCompra:{i:'Anota lo que entró a la bodega. Lo sumo a lo que tienes.',c:{f:'El día en que llegó.',kg:'Cuánto compraste.',costo:'Opcional, para tu control.',prov:'Opcional. A quién se lo compraste.'}},
 bodegaConteo:{i:'Cuenta lo que hay de verdad en la bodega. Corrijo mi cálculo con lo que cuentes; hazlo cada una o dos semanas.',c:{f:'El día del conteo.',kg:'Lo que hay ahora en la bodega.'}},
 tarea:{i:'Un recordatorio para tu agenda. Te lo muestro en Hoy el día que toca.',c:{t:'Qué hay que hacer.',f:'El día en que toca hacerlo.'}},
 insItem:{i:'Agrega una medicina o insumo: vacunas, desparasitantes, jeringas, sal mineral… Llevo cuánto te queda, su valor y cuándo vence.',
  c:{n:'El nombre como sale en la etiqueta.',cat:'Qué tipo de producto es.',u:'Cómo lo cuentas: ml, dosis, frascos, bolsas…',min:'Te aviso cuando quede menos que esto.'}},
 insMov:{i:'Anota una compra, un uso o un conteo de este producto. Así el inventario siempre está al día.',
  c:{f:'El día del movimiento.',cant:'Cuánto compraste, usaste o hay ahora, según el caso.',costo:'Lo que pagaste en total.',venc:'La fecha de vencimiento de la etiqueta. Te aviso antes.',prov:'Opcional.',lote:'Si lo usaste en un lote, elígelo.'}},
 finEquipo:{i:'Agrega equipo o instalaciones: báscula, corrales, tractor, bebederos… Calculo cuánto se desgasta cada año (depreciación) para tu balance.',
  c:{n:'El nombre del equipo o instalación.',cat:'Qué tipo es.',costo:'Lo que te costó.',f:'Cuándo lo compraste o construiste.',vida:'Cuántos años crees que va a servir.',res:'Lo que valdría al final, al venderlo usado.'}},
 finCredito:{i:'Anota un préstamo. Llevo cuánto debes, cuánto vas pagando de intereses y cuánto baja el capital.',
  c:{n:'Quién te prestó: banco, cooperativa, persona.',monto:'Cuánto te prestaron.',tasa:'El interés que cobran al año.',f:'Cuándo te dieron el dinero.',plazo:'En cuántos meses lo debes pagar.'}},
 finAbono:{i:'Anota un pago que hiciste al préstamo. Primero cubre los intereses y lo demás baja lo que debes.',c:{f:'El día del pago.',monto:'Cuánto pagaste.'}},
 finCaja:{i:'Cuánto dinero tienes hoy y lo que te deben o debes. Lo uso en tu balance.',c:{efectivo:'Lo que tienes en caja y en tus cuentas de banco.',cxc:'Ventas que todavía no te pagan.',cxp:'Lo que debes a proveedores, sin contar préstamos.'}}
};
const visto=k=>{const m=LS('rumentis-ayuda-f')||{};return m[k]||0;};
const contar=k=>{const m=LS('rumentis-ayuda-f')||{};m[k]=(m[k]||0)+1;LS('rumentis-ayuda-f',m);};
function ayudaForm(f){
  const k=f.dataset.form,A=F[k];if(!A||f.dataset.ayuda)return;f.dataset.ayuda='1';
  const body=f.querySelector('.sh-body');if(!body)return;
  const abierta=visto(k)<2;contar(k);
  const d=document.createElement('details');d.className='f-ayuda';if(abierta)d.open=true;
  d.innerHTML=aUnidad(`<summary><span class="rumi-av sm">${rumiSVG('mini')}</span><b>¿Qué anoto aquí?</b>${ico('chev',2.4)}</summary><div class="f-ay-b"><p>${A.i}</p>${A.p&&A.p.length?`<ul>${A.p.map(x=>`<li>${x}</li>`).join('')}</ul>`:''}</div>`);
  body.prepend(d);
  for(const [n,t] of Object.entries(A.c||{})){
    const el=f.querySelector(`[name="${n}"]`);if(!el)continue;const qd=el.closest('.q');if(!qd||qd.querySelector(':scope>small'))continue;
    const s=document.createElement('small');s.className='f-tip';s.textContent=aUnidad(t);qd.appendChild(s);}
}
const _open=openSheet;
openSheet=window.openSheet=function(html,after){_open(html,after);const f=document.querySelector('#sheet form[data-form]');if(f)ayudaForm(f);};

/* ================= tutoriales por página ================= */
const txt=s=>window.I18N&&I18N.txt?I18N.txt(s):s;
const nrm=s=>String(s||'').replace(/\s+/g,' ').trim().toLowerCase();
/* busca la sección cuyo título empieza con h (en español o traducido) */
function porTitulo(h){
  const A=[nrm(h),nrm(txt(h))];
  const C=document.querySelectorAll('main.bd section, main.bd .card, main.bd .gcard, main.bd > *');
  for(const c of C){const t=c.querySelector('h2,h3,.sec-h b,.gc-h b,b');if(t&&A.some(a=>nrm(t.innerText).startsWith(a)))return c;}
  return null;
}
const T={
 hoy:{t:'Hoy',s:[
  {q:'header.hd',t:'Tu engorde de un vistazo',x:'Arriba ves cuántos animales tienes, cuánto pesan en total y tres números clave: ganancia diaria, conversión y costo por kilo ganado. Toca el clima para ver el riesgo de calor para tu ganado.'},
  {q:'.rp-hoy',t:'Análisis de Rumi',x:'Tu puntaje de 0 a 100 y la probabilidad de ganar dinero con lo que tienes en engorde. Tócalo y te digo qué haría yo primero.'},
  {q:'.ag-card',t:'Agenda de hoy',x:'Las tareas del día, armadas con tus datos: entregas de comida, pesajes, refuerzos de vacuna y retiros.'},
  {q:'.paso-w',t:'Siguiente paso',x:'Lo más importante según tus números. Tócalo para hacerlo; si ya lo hiciste, quítalo con la X.'},
  {q:'.alim-c',t:'Comida de hoy',x:'Cuánto has servido hoy contra lo que comen normalmente. El botón abre la siguiente entrega.'},
  {h:'Atención',t:'Avisos',x:'Problemas de tus lotes: muertes, animales que comen menos, pesajes atrasados. Toca uno para ver el detalle.'},
  {h:'Lotes en engorde',t:'Tus lotes',x:'Cada lote con su peso y lo que gana por día. Toca uno para ver todo sobre él.'},
  {q:'#rumiFab',t:'Pregúntame',x:'Cuando tengas una duda, tócame. Tengo tus números, calculadoras, síntomas y una guía completa de engorde.'}]},
 lotes:{t:'Lotes',s:[
  {q:'header.hd',t:'Tus lotes',x:'Un lote es un grupo de animales que entraron juntos. Aquí están todos, con cabezas y peso total.'},
  {q:'main.bd nav.tabs, main.bd .seg',t:'En engorde y vendidos',x:'Cambia entre los lotes que tienes ahora y los que ya vendiste, con su resultado final.'},
  {q:'main.bd .lote',t:'Cada lote',x:'Cabezas, días en engorde, peso y ganancia por día. «Listo» quiere decir que ya llegó a su peso meta. Tócalo para abrirlo.'},
  {q:'nav.bottom [data-nav="registrar"]',t:'Crear un lote',x:'Para un lote nuevo toca Registrar y luego Nuevo lote.'}]},
 lote:{t:'Un lote',s:[
  {q:'header.hd',t:'El lote',x:'Nombre, cabezas, qué animales son y cuándo entraron. Abajo están sus números principales.'},
  {q:'.paso-w',t:'Qué toca',x:'Lo que más necesita este lote ahora. Si ya lo hiciste, quítalo con la X.'},
  {q:'main.bd .qa',t:'Anotar rápido',x:'Pesaje, alimento, sanidad y venta de este lote con un toque.'},
  {q:'main.bd nav.tabs',t:'Pestañas',x:'Resumen, sus animales uno por uno, sus números de dinero y todo lo que has anotado.'},
  {h:'Rendimiento',t:'Rendimiento',x:'Días en engorde, ganancia diaria, conversión y cuándo llega a la meta.'},
  {h:'Peso del lote',t:'Su peso',x:'Cada punto es un pesaje. La línea punteada es hacia dónde va si sigue ganando igual.'}]},
 registrar:{t:'Registrar',s:[
  {h:'Ahora toca',t:'Ahora toca',x:'Lo que te toca anotar ahora mismo según tus horarios y tus lotes.'},
  {h:'Anotar',t:'Todo lo que anotas',x:'Alimento, pesajes, sanidad, ventas, muertes, gastos, lotes nuevos y raciones. Cada formulario te explica qué llenar.'},
  {h:'Lo último que anotaste',t:'Lo último',x:'Tus últimos registros. Si te equivocaste, tócalo para corregirlo o borrarlo.'}]},
 finanzas:{t:'Finanzas',s:[
  {q:'header.hd',t:'Tu dinero',x:'Arriba: tu patrimonio (lo que es tuyo de verdad), la ganancia de los últimos 12 meses y cuánto vale hoy tu ganado en pie.'},
  {h:'Balance a hoy',t:'Balance',x:'Lo que tienes (efectivo, ganado, alimento, medicinas, equipo) menos lo que debes. El ganado vale lo que te pagarían hoy.'},
  {h:'Estado de resultados',t:'Lo que ganaste',x:'Ventas menos el costo de lo vendido y los gastos. Cambia el período arriba: mes, año o 12 meses.'},
  {h:'Flujo de dinero',t:'Entradas y salidas',x:'Cuánto dinero entró y salió cada mes. Te ayuda a ver cuándo vas a necesitar efectivo.'},
  {h:'Rentabilidad de lotes vendidos',t:'Rentabilidad',x:'Cuánto rindió cada lote vendido por cada unidad de dinero invertida, y al año.'},
  {h:'Créditos',t:'Préstamos',x:'Tus préstamos, cuánto debes hoy y cuánto pagas de intereses. Toca uno para anotar un pago.'}]},
 inventario:{t:'Inventario',s:[
  {q:'header.hd',t:'Todo lo que tienes',x:'El valor de todo lo que hay en la finca: ganado, alimento, medicinas y equipo.'},
  {h:'Ganado',t:'Ganado',x:'Tus animales por lote, con su peso y lo que valen hoy.'},
  {h:'Alimento',t:'Alimento',x:'Lo que hay en bodega y para cuántos días alcanza. Te aviso antes de que se acabe.'},
  {h:'Medicinas e insumos',t:'Medicinas',x:'Cuánto te queda de cada producto y cuándo vence. Anota compras, usos o conteos.'},
  {h:'Equipo e instalaciones',t:'Equipo',x:'Báscula, corrales, maquinaria… con cuánto valen hoy después del desgaste.'}]},
 graficos:{t:'Gráficos',s:[
  {q:'main.bd > div:first-child',t:'Elige qué ver',x:'Todos los lotes juntos o uno solo, incluidos los vendidos.'},
  {h:'Peso promedio',t:'Peso',x:'Cada línea es un lote. La línea punteada es su proyección, los anillos marcan cuándo llega a la meta y la línea vertical es hoy.'},
  {h:'Ganancia diaria',t:'Ganancia diaria',x:'Cuánto sube cada animal por día. Es el número que más dice si tu engorde va bien.'},
  {h:'Conversión alimenticia',t:'Conversión',x:'Cuánta comida necesitas para un kilo de carne. Menos es mejor.'},
  {h:'Margen por cabeza',t:'Margen',x:'Lo que deja cada animal. Cada gráfico trae «Qué estás viendo» y mi explicación de tus números.'}]},
 mas:{t:'Más',s:[
  {h:'Rumi',t:'Análisis de Rumi',x:'Mi análisis completo: puntaje, riesgo, simulador, proveedores y qué hacer primero.'},
  {h:'Tu finca',t:'Tu finca',x:'Configuración, raciones, gastos generales, agenda y bodega.'},
  {h:'Datos y personalización',t:'Tus datos',x:'Respaldo, descargas para Excel, colores y avisos al teléfono.'},
  {h:'Ayuda',t:'Ayuda',x:'El recorrido por la app, cómo calculo cada número y el menú de Rumi.'}]},
 config:{t:'Configuración',s:[
  {h:'Idioma, moneda y unidades',t:'Lo primero',x:'Tu idioma, tu país, si pesas en libras o kilos, tu moneda y cómo se cotiza el ganado y el alimento. Cambia al momento y tus datos no cambian.'},
  {h:'Tu finca',t:'Tu finca',x:'El nombre y la ubicación. Con la ubicación te doy el clima y el riesgo de calor.'},
  {h:'Precio de venta',t:'Precio de venta',x:'Lo que te pagan hoy. Actualízalo cuando cambie el mercado: con él calculo márgenes y el valor del ganado.'},
  {h:'Metas del engorde',t:'Metas',x:'El peso al que vendes, la ganancia esperada y el desbaste.'},
  {h:'Alimentación y avisos',t:'Horarios',x:'Tus horarios de comida y cada cuánto quieres pesar.'}]},
 analisis:{t:'Análisis de Rumi',s:[
  {h:'Chequeo de tu engorde',t:'Chequeo',x:'Un puntaje de 0 a 100 con cinco partes: ganancia, conversión, rentabilidad, salud y registros. Abajo, lo que haría primero.'},
  {h:'Riesgo del negocio',t:'Riesgo',x:'Pruebo miles de escenarios con precios, ganancias y costos que cambian, y te digo la probabilidad de ganar.'},
  {h:'Simulador',t:'¿Qué pasa si…?',x:'Mueve el precio, el costo del alimento o la ganancia diaria y mira al momento cómo cambia tu resultado.'},
  {h:'Tu semana',t:'Tu semana',x:'El resumen de los últimos 7 días para compartir por WhatsApp.'},
  {h:'Proveedores',t:'Proveedores',x:'Qué proveedor y qué raza te dejan más dinero por cabeza.'}]},
 agenda:{t:'Agenda',s:[
  {h:'Hoy',t:'Hoy',x:'Lo que toca hoy o está atrasado. Toca «Hacer» y te abro el formulario.'},
  {h:'Próximos 7 días',t:'Esta semana',x:'Lo que viene en los próximos días.'},
  {q:'main.bd > .btn.full',t:'Tus recordatorios',x:'Agrega tus propias tareas; te las muestro el día que tocan.'}]},
 bodega:{t:'Bodega',s:[
  {q:'main.bd .bod',t:'Cada producto',x:'Cuánto hay, para cuántos días alcanza y cuándo lo compraste. Anota compras y conteos aquí.'},
  {q:'main.bd > .btn.full',t:'Agregar',x:'Agrega maíz, concentrado, heno o lo que tengas en la bodega.'}]},
 raciones:{t:'Raciones',s:[
  {h:'Formular una dieta',t:'Formular',x:'Calculo la mezcla más barata que cubre lo que tus animales necesitan, con tus ingredientes y tus precios.'},
  {h:'Tus raciones',t:'Tus raciones',x:'Tus mezclas con su costo. Las eliges al anotar la comida.'}]},
 formular:{t:'Formular dieta',s:[
  {h:'Para qué animales',t:'Para quién',x:'Elige el lote o escribe el peso promedio y la ganancia que buscas.'},
  {h:'Ingredientes que tienes',t:'Ingredientes',x:'Marca los que tienes y pon su precio de hoy.'},
  {q:'main.bd > .btn.ar',t:'Formular',x:'Toca y te doy la mezcla más barata, su costo y cuánto dar por animal.'}]},
 gastos:{t:'Gastos generales',s:[
  {q:'main.bd .sec:first-child',t:'Este mes',x:'Lo que llevas gastado este mes en sueldos, luz, combustible y otros.'},
  {h:'Últimos gastos',t:'Tus gastos',x:'Cada gasto general. Se reparte entre tus lotes para el costo real.'}]},
 datos:{t:'Tus datos',s:[
  {h:'Respaldo',t:'Respaldo',x:'Descarga una copia cada semana y guárdala en Drive o mándatela por WhatsApp. Si pierdes el teléfono, la restauras.'},
  {h:'Descargar para Excel',t:'Excel',x:'Tus lotes, alimento, pesajes, sanidad, ventas y gastos en archivos para Excel.'}]}
};
const clave=()=>{const r=route();if(r.p==='mas'&&r.id)return {config:'config',raciones:'raciones',gastos:'gastos',datos:'datos'}[r.id]||null;return T[r.p]?r.p:null;};
const vistos=()=>LS('rumentis-tut')||{};
let TUT=null;
function tutorial(k){
  const D=T[k];if(!D)return;
  const pasos=D.s.map((s,i)=>{let el=null;if(s.q)el=document.querySelector(s.q);else if(s.h)el=porTitulo(s.h);if(!el)return null;el.setAttribute('data-tut',k+i);return {sel:`[data-tut="${k+i}"]`,t:s.t,x:s.x};}).filter(Boolean);
  if(!pasos.length){toast('Esta página todavía no tiene nada que mostrar.');return;}
  pasos.push({t:'¡Listo!',x:'Si quieres repetir este tutorial, pídemelo en Rumi, Usar la app, <b>Muéstrame la app</b>.'});
  TUT=k;spot(pasos);
}
function abrirTut(k){
  const go={hoy:'#hoy',lotes:'#lotes',registrar:'#registrar',finanzas:'#finanzas',inventario:'#inventario',graficos:'#graficos',mas:'#mas',config:'#mas/config',analisis:'#analisis',agenda:'#agenda',bodega:'#bodega',raciones:'#mas/raciones',formular:'#formular',gastos:'#mas/gastos',datos:'#mas/datos'}[k];
  if(k==='lote'){const x=calc().act[0];if(!x){toast('Primero crea un lote.');return;}location.hash='#lote/'+x.id;}
  else if(go&&location.hash!==go)location.hash=go;
  closeSheet();setTimeout(()=>tutorial(k),650);
}
document.addEventListener('tourfin',()=>{if(!TUT)return;const m=vistos();m[TUT]=1;LS('rumentis-tut',m);TUT=null;const c=document.querySelector('.tut-chip');if(c){c.classList.add('fuera');setTimeout(()=>c.remove(),300);}});
function chip(){
  const k=clave();if(!k||vistos()[k]||document.querySelector('.tut-chip'))return;
  if(k!=='hoy'&&!Object.keys(S.lotes).length)return;
  const hd=document.querySelector('#app header.hd .ttl');if(!hd)return;
  const b=document.createElement('button');b.type='button';b.className='tut-chip';b.dataset.act='tutPagina';b.dataset.k=k;
  b.innerHTML=`<span class="rumi-av sm">${rumiSVG('mini')}</span><span>${txt('¿Cómo funciona esta página?')}</span>`;
  hd.appendChild(b);
}
const _render=render;
render=window.render=function(){_render.apply(this,arguments);try{chip();}catch(e){}};
Object.assign(ACTS,{
  tutPagina:el=>tutorial(el.dataset.k),
  tutAbrir:el=>abrirTut(el.dataset.k)
});

/* en Rumi: Usar la app → Muéstrame la app, con una parte por página */
if(window.RumiMenu){
  const A=RumiMenu.AREAS.find(a=>a.id==='app');
  if(A){const o=A.ops;A.ops=()=>[{l:'Muéstrame la app',ic:'app',n:Object.keys(T).length+1,sub:()=>({t:'Muéstrame la app',intro:'¿Qué página te explico? Te llevo ahí y te muestro cada parte, paso a paso.',
    ops:[{l:'Recorrido completo por la app',run:()=>{iniciarTour();}}].concat(Object.entries(T).map(([k,d])=>({l:d.t,small:pl(d.s.length,'paso','pasos'),run:()=>abrirTut(k)})))})}].concat(o());}
}
window.Ayuda={tutorial,abrirTut,F,T};
})();
