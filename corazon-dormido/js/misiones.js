'use strict';
// Historia y encargos (0.7): las tablillas cuentan la historia de LORE.md (las de dentro de la Muralla mienten sin
// querer; las de fuera dicen la verdad), el bestiario guarda una ficha de cada criatura que matas, el diario lleva la
// cuenta de los encargos y dos personajes hablan contigo: el Archivero, el último Velador (Ciudad Velada), y Muda,
// la gruta que dejó de rezar (Ciénaga de la Podre). Los consejos de juego de antes salen en la pantalla de carga.

// ---------------------------------------------------------------- tablillas
const HISTORIA = {
  interior: [
    'La Muralla protege al Corazón de lo que hay fuera. Que nadie la toque nunca.',
    'Ozmar es el más sabio de nosotros. Si él dice que el Corazón se curará, se curará.',
    'El Topo abre los caminos del latido. Que nadie le haga daño.',
    'La Madre se come lo que muere para que el Corazón no enferme. Dadle de comer.',
    'Fuera de la Muralla no queda nada. Solo roca y oscuridad.',
    'Los que bajaron a la oscuridad volverán cuando el Corazón despierte. Esperadlos con la lámpara encendida.',
    'La luz del Corazón no se acaba nunca. Por eso no hace falta guardarla.',
    'Los Veladores no tenemos nada que esconder. El velo es por respeto, no por vergüenza.',
  ],
  selva: [
    'Ásor cayó aquí antes que nada. Donde su sangre empapó la tierra, todo creció demasiado deprisa.',
    'Hiedra era un jardín. Un Velador quiso quemarla con su lámpara. Ahora está dentro de ella.',
    'Tamar no es un ave. Es el ojo de Ásor, que salió volando al caer. Lleva mil años buscando la cara de la que salió.',
    'Los grutos de la Selva siembran lo que les manda la Podre. Lo que plantan se pone morado a la semana.',
    'El metal carmesí es sangre del dios que se hizo piedra. Por eso está caliente.',
    'Levantamos la Muralla en cuarenta años. Nos dijeron que era para proteger el Corazón. Mentían.',
    'Más allá de la Selva el suelo se hunde. Allí el agua es negra y no refleja nada.',
    'Si oyes zumbar un panal como si rezara, aléjate. Las aguijoneras también rezan a la Podre.',
    'Bajo el moho hay salas que nadie cavó. Algo las hizo para guardar lo que pone.',
    'Las lápidas que caminan eran tumbas nuestras. Lo de debajo se cansó de estar quieto.',
    'La luz del cielo de la Selva no es sol. Es el latido del Corazón, que llega hasta aquí atravesando la roca.',
    'Contamos a los que entraron en la Selva y a los que salieron. Las cuentas no salen.',
  ],
  cienaga: [
    'Aquí subió la Podre por primera vez. Del Pozo salió como humo, y el humo tenía hambre.',
    'Ozmar vino a la Ciénaga a estudiar el Pozo. Volvió con una idea: darle al Corazón un poco de Podre, como quien vacuna a un niño.',
    'Las costillas que salen del fango son de Ásor. Debajo está su pecho. Debajo del pecho, lo que le queda de corazón.',
    'Grul era un sapo de los charcos. Se tragó algo que salió del Pozo y ya no ha parado de crecer.',
    'Los grutos del fango viven sobre el agua porque la Podre no sube por el agua negra. De momento.',
    'No sigas una luz en la Ciénaga. Las luces de aquí tienen dientes detrás.',
    'Esta casa era nuestra. El agua subió en una noche y nadie despertó.',
    'La médula del dios es más dura que el metal. La tallábamos para hacer sellos que no se rompieran.',
    'Los capullos de la Podre no se comen a nadie. Los guardan. Para después.',
    'Las sanguijuelas beben del agua negra y engordan de algo que no es sangre.',
    'La Podre no come carne. Come luz. Por eso lo que toca se queda frío y vacío por dentro.',
    'Dicen que en el fondo del Pozo algo late al revés que el Corazón.',
  ],
  ciudad: [
    'Esta era la Ciudad Velada. Aquí vivíamos antes de la Muralla, cuando todavía no teníamos miedo.',
    'Llevamos velo porque Ásor nos vio la cara al caer. Desde entonces, lo que nos mira nos encuentra.',
    'La Muralla no se hizo para guardar el Corazón. Se hizo para encerrar a los hijos de su sangre: los Titanes Menores.',
    'El Centinela juró guardar la cripta hasta que volviera el último Velador. Nadie le dijo que no iba a volver nadie.',
    'En la cripta enterramos a los que se pudrieron. No los enterramos lo bastante hondo.',
    'El Archivero no quiso irse. Dijo que alguien tenía que acordarse de todo cuando el Corazón despertara.',
    'Las farolas de la ciudad arden con lo que queda de nosotros. Cada luz es un Velador que no se fue.',
    'Al otro lado de la Ciudad duerme el Mar Hundido. Allí se ahogó el aliento de Ásor, y todavía respira.',
    'Y más allá, el Desierto del Origen: la piel del dios al sol. Lo que vive allí tiene sed.',
    'Si despiertas al Corazón del todo, el latido llegará al cuerpo de Ásor. Nadie sabe si se levantará para salvarnos o para irse.',
    'Las estatuas de las plazas no son estatuas. Las tallamos alrededor de los que no querían dormirse.',
    'Escribimos esto en plata porque la plata no se pudre. La Podre no la puede leer.',
    'Ozmar fue el último en salir de la Ciudad. Cerró la Muralla desde dentro y tiró la llave al Corazón.',
    'Los Velados no buscan comer. Buscan calor. Tú tienes mucho.',
    'Nuestros niños aprendían a leer con una sola frase: «Lo que duerme, que duerma».',
    'Cuando la luz de la última farola se apague, la Ciudad será de la Podre.',
  ],
};
const ZONA_TABLILLA = (reg) => (reg === REGION.CIENAGA ? 'cienaga' : reg === REGION.CIUDAD || reg === REGION.CRIPTA ? 'ciudad' : reg >= REGION.SELVA && reg !== REGION.SELLADO ? 'selva' : 'interior');
const TITULO_TABLILLA = { interior: 'TABLILLA', selva: 'TABLILLA DE FUERA', cienaga: 'TABLILLA HUNDIDA', ciudad: 'LIBRO VELADOR' };
const leerTablillaPuzles = INTERACCIONES.tablilla;
INTERACCIONES.tablilla = (o) => {
  const d = o.datos || {};
  if (d.orden || d.clave || d.texto) { leerTablillaPuzles(o); J.tituloTablilla = 'TABLILLA'; return; }
  const zona = ZONA_TABLILLA(M.region[idx(o.x, o.y)]), lista = HISTORIA[zona], k = (d.n || 0) % lista.length;
  J.tablilla = `«${lista[k]}»`; J.tituloTablilla = o.tipo === 'atril' ? 'LIBRO VELADOR' : TITULO_TABLILLA[zona];
  J.hito[`leida_${zona}_${k}`] = true;
  J.panel = 'tablilla'; Sonido.sfx.menu();
};
PANELES.tablilla = (ctx) => panelMensaje(ctx, J.tituloTablilla || 'TABLILLA', J.tablilla || '', 'CERRAR', () => { J.panel = null; }, '#e8d8a0');
const leidas = () => Object.keys(J.hito).filter((k) => k.startsWith('leida_')).length;
const TOTAL_TABLILLAS = Object.values(HISTORIA).reduce((a, l) => a + l.length, 0);

// Los consejos de juego (las tablillas de antes) salen mientras se genera el mundo.
const CONSEJOS = [
  'Si tienes una casa cerrada con cama y un ídolo, un mercader vendrá a vivir contigo.',
  'Los huevos se incuban al calor de la incubadora. Lo que nace se queda contigo.',
  'Una piedra de regreso te devuelve a tu cama (o al Corazón). Apriétala si te pierdes.',
  'Los hongos naranjas estallan si los golpeas. Los grutos los usan para abrir caminos.',
  'La miel de los panales cura casi tanto como un buen guiso.',
  'Las piedras de paso se despiertan al tocarlas. Después puedes viajar entre todas las que hayas despertado.',
  'Los braseros de runas se encienden en el orden que dice su tablilla. Si te equivocas, se apagan todos.',
  'Los bloques del templo se empujan con USAR. Si se atascan, la palanca de la entrada los devuelve a su sitio.',
  'La médula de Ásor está en la turba de la Ciénaga. Hace falta un pico carmesí para sacarla.',
  'Grul guarda el corazón con el que se hace la mesa de médula. El Centinela, la llama de la forja velada.',
  'Las criaturas no salen donde hay luz. Una antorcha a tiempo vale más que una espada.',
  'Cada criatura que matas desbloquea su ficha en el bestiario de la mochila.',
];

// ---------------------------------------------------------------- bestiario
const FICHAS = {
  babosa: 'Se arrastra sobre los muertos y los disuelve despacio. Lo que asoma de ella es lo último que se comió.',
  babosa_roja: 'Cuando se parte, cada mitad sigue teniendo hambre.',
  babosa_veneno: 'La Podre la llenó hasta reventar. Literalmente.',
  seton: 'Ya no es un minero. Es la maceta de algo.',
  larva: 'No tiene ojos. No los necesita.',
  larva_grande: 'Lo que escupe ya ha disuelto a otros antes que a ti.',
  larva_moho: 'La Podre también se come a los gusanos. Luego los usa.',
  gruto_lancero: 'Llevan la cara de sus abuelos para que la luz no los reconozca.',
  gruto_chaman: 'Reza a lo que se comió a su pueblo. Y lo que se lo comió le contesta.',
  gruto_bruto: 'Lo engordan con lo que cazan los demás. Solo sirve para una cosa.',
  gruto_jardinero: 'Allí donde siembra, la tierra se pone morada a la semana.',
  gruto_cazador: 'Si lo ves, es que ya ha disparado.',
  planta_trampa: 'Las flores de la Selva aprendieron de Hiedra a comer carne.',
  tentaculo_moho: 'No es un animal. Es un dedo de algo mucho más grande.',
  avispa: 'Su nido zumba como una voz que reza.',
  escarabajo_musgo: 'Los Veladores enterraban a sus muertos bajo estas piedras. Las piedras se levantaron.',
  sanguijuela: 'Bebe del agua negra y engorda de algo que no es sangre. Cuando te muerde, se lleva un poco de tu luz.',
  farolero: 'Lo único que ves de él es la luz que cuelga delante de su boca. Lo demás lo ves cuando ya es tarde.',
  sapo_hueco: 'La Podre le vació las tripas y le llenó la espalda. Salta para que no le pese.',
  moscarda: 'Pone los huevos en lo que todavía se mueve.',
  gruto_fango: 'Los grutos del pantano pescan con arpón. Lo que no pescan, se lo llevan a sus ídolos.',
  necrofago: 'Era un Velador. La Podre se le comió la luz de dentro y le dejó el hambre.',
  velado: 'No se fue con los demás. Ahora busca algo caliente a lo que abrazarse.',
  guardian_piedra: 'Lo tallaron alrededor de alguien que no quería dormirse. Sigue sin querer.',
  velador_caido: 'Bajó a la cripta a enterrar a los suyos y se quedó haciendo guardia. Ya no recuerda por qué.',
};
const BESTIARIO_JEFES = ['topo', 'reina', 'ozmar', 'hiedra', 'tamar', 'grul', 'centinela'];

// ---------------------------------------------------------------- personajes y encargos
OBJ.archivero = { arte: 'archivero', brillo: true, luz: [6, 0.55, 0.72, 1.0], bloquea: true, vida: 0, interactua: 'hablar', mapa: '#b4dcff' };
OBJ.muda = { arte: 'muda', brillo: true, luz: [5, 1.0, 0.7, 0.35], bloquea: true, vida: 0, interactua: 'hablar', mapa: '#ffa424' };
item('ojo_velador', { nombre: 'Ojo del Velador', tipo: 'accesorio', luz: 4, suerte: 0.08, critico: 0.04, max: 1, rareza: 3, desc: 'Un ojo de plata que no parpadea. Ves más, encuentras más, aciertas más.' });

const PERSONAJES = {
  archivero: {
    nombre: 'EL ARCHIVERO', titulo: 'el último Velador', color: '#b4dcff',
    intro: [
      'Tienes calor. Hace mil años que nadie con calor sube a esta torre.',
      'Soy el Archivero. Cuando los demás se fueron, yo me quedé con los libros. Los libros no saben irse.',
      'Tú eres lo que hizo el Corazón con su último latido. Lo sé porque lo escribí antes de que pasara.',
      'La Muralla no era para proteger al Corazón. Era para encerrar lo que nació de la sangre de Ásor. Y tú la abriste.',
      'Si vas a seguir abriendo puertas, ábrelas sabiendo. Tráeme lo que encuentres ahí fuera y te diré qué era.',
    ],
    saludos: ['Sigues caliente. Bien.', 'Los libros me han hablado de ti mientras no estabas.', '¿Qué has encontrado ahí fuera?', 'Cuidado con las farolas que parpadean. Alguien se está yendo.'],
  },
  muda: {
    nombre: 'MUDA', titulo: 'la que dejó de rezar', color: '#ffa424',
    intro: [
      'No grites. No llevo máscara: no soy de ellos. Ya no.',
      'Me llaman Muda porque dejé de rezar. Para los míos, quien no reza a la Podre no tiene voz.',
      'Mi pueblo se puso las caras de los abuelos para que la luz no los reconociera. Yo quiero que me reconozca.',
      'Tú brillas como cuentan las tablillas viejas. Ayúdame a quemar lo que se comió a los míos.',
    ],
    saludos: ['El agua negra está quieta hoy. Eso nunca es bueno.', 'Los míos rezan más fuerte desde que llegaste.', 'Sigue quemando, Despierto.'],
  },
};
const contar = (pref) => Object.keys(J.hito).filter((k) => k.startsWith(pref)).length;
const braserosAbiertos = () => M.puzles.filter((pz, k) => pz.tipo === 'braseros' && J.hito['puzle_' + k]).length;
// pide: lo que hay que llevar (se quita al entregar). progreso(): [hecho, total]. premio: lo que da. linea: lo que dice.
const ENCARGOS = {
  paginas: { de: 'archivero', nombre: 'Las páginas sueltas', desc: 'Tráeme diez páginas veladas. Están en las estanterías rotas, en las urnas y en los bolsillos de los muertos.', pide: [['pagina_velada', 10]], premio: [['moneda', 200], ['piedra_regreso', 5]], linea: 'Esta habla de la Muralla. La escribió alguien que tenía miedo de lo que estaba encerrando. Sigue buscando.' },
  nombres: { de: 'archivero', nombre: 'Los que no volvieron', desc: 'Por toda la Selva hay Veladores que no llegaron a casa. Registra a doce. Quiero sus nombres para el libro.', progreso: () => [Math.min(12, J.hito.restos || 0), 12], premio: [['lingote_plata', 6], ['esencia_velada', 3]], linea: 'Doce nombres. Los apunto al lado de los suyos: así no se quedan solos en el libro.' },
  runas: { de: 'archivero', nombre: 'Las cámaras de las runas', desc: 'Guardamos lo que más valía tras puertas de runas. Abre tres cámaras de braseros y cuéntame qué había.', progreso: () => [Math.min(3, braserosAbiertos()), 3], premio: [['gema_antigua', 3], ['idolo_misterioso', 1]], linea: 'Entonces las llamas aún recuerdan el orden. Bien. Las cosas que recuerdan son las que siguen vivas.' },
  centinela: { de: 'archivero', nombre: 'El que no duerme', desc: 'El Centinela guarda la cripta desde antes de la Muralla. Ya no sabe por qué. Dale descanso.', requiere: 'runas', progreso: () => [J.jefesMuertos.centinela ? 1 : 0, 1], premio: [['moneda', 300], ['mapa:ciudad', 1]], linea: 'Descansa. Por fin. Toma: el plano de la Ciudad. Ya no hay nadie a quien esconderle las calles.' },
  ozmar: { de: 'archivero', nombre: 'El libro de Ozmar', desc: 'Ozmar escribía cada noche. Arrancó sus páginas antes de irse. Tráeme treinta y te leeré lo que hizo.', requiere: 'paginas', pide: [['pagina_velada', 30]], premio: [['ojo_velador', 1]], linea: '«Le daré un poco de Podre, solo un poco, como quien vacuna a un niño. Si duerme, que duerma. Si muere, que muera conmigo.» Ozmar sabía lo que podía pasar. Lo hizo igual.' },
  esporas: { de: 'muda', nombre: 'Quemar la Podre', desc: 'Tráeme quince esporas de la Podre. Las quemo en el farol: es lo único que les da miedo.', pide: [['espora_podre', 15]], premio: [['lingote_medula', 5], ['moneda', 80]], linea: 'Mira cómo chillan al arder. Todavía tienen luz dentro: es la que nos robaron.' },
  sapos: { de: 'muda', nombre: 'Las bocas del pantano', desc: 'Los sapos huecos se comen a los nuestros cuando van a pescar. Mata a ocho.', progreso: () => [Math.min(8, (J.bestiario && J.bestiario.sapo_hueco) || 0), 8], premio: [['savia_negra', 3], ['huevo_moho', 1]], linea: 'Ocho bocas menos. Esta noche alguien podrá pescar sin mirar atrás.' },
  bultos: { de: 'muda', nombre: 'Los ídolos de mi pueblo', desc: 'Rompe diez bultos de la Podre. Cada uno que revientas es una oración que no llega.', progreso: () => [Math.min(10, J.hito.roto_bulto_podre || 0), 10], premio: [['cebo_luz', 3], ['collar_farolero', 1]], linea: 'Ya no los oigo rezar desde aquí. Por primera vez en años, el pantano está en silencio.' },
  grul: { de: 'muda', nombre: 'El que se comió a mi pueblo', desc: 'Grul vive en las costillas del dios. Dentro de él están mi madre y mi hermano. Sácalos.', requiere: 'sapos', progreso: () => [J.jefesMuertos.grul ? 1 : 0, 1], premio: [['huevo_luz', 1], ['lingote_medula', 8]], linea: 'No han salido. Ya lo sabía. Pero ahora nadie más va a entrar. Gracias, Despierto.' },
};
function progresoEncargo(id) {
  const e = ENCARGOS[id];
  if (e.pide) { const [it, n] = e.pide[0]; return [Math.min(n, J.cuenta(it)), n]; }
  return e.progreso();
}
const encargoListo = (id) => { const [a, b] = progresoEncargo(id); return a >= b; };
function darPremio(lista) {
  for (const [id, n] of lista) {
    if (id === 'mapa:ciudad') { revelarRegion(REGION.CIUDAD); aviso('El plano de la Ciudad Velada está en tu mapa', null, '#b4dcff'); continue; }
    asegurarItem(id); const resto = agregar(id, n); if (resto) soltar(J.jug.x, J.jug.y, id, resto);
    aviso(`+${n} ${ITEMS[id].nombre}`, null, '#9fffa8');
  }
}
function revelarRegion(reg) {
  for (let i = 0; i < M.region.length; i++) if (M.region[i] === reg && !M.visto[i]) { M.visto[i] = 1; tocarTrozoI(i); marcarMapa(i); }
}

// ---------------------------------------------------------------- hablar
INTERACCIONES.hablar = (o) => {
  const quien = o.tipo, P = PERSONAJES[quien];
  if (!J.encargos) J.encargos = {};
  if (!J.hito['conoce_' + quien]) { J.dialogo = { quien, lineas: P.intro.slice(), k: 0, intro: true }; }
  else J.dialogo = { quien, lineas: [P.saludos[Math.floor(Math.random() * P.saludos.length)]], k: 0 };
  J.panel = 'dialogo'; Sonido.sfx.menu();
};
PANELES.dialogo = (ctx) => {
  const D = J.dialogo; if (!D) { J.panel = null; return; }
  const P = PERSONAJES[D.quien];
  fondoPanel(ctx, false);
  const w = Math.min(LW - 16, 240), x = Math.round(LW / 2 - w / 2), lineas = partirTexto(D.lineas[D.k] || '', w - 70);
  const ops = [];
  if (D.k < D.lineas.length - 1) ops.push(['SIGUE', () => { D.k++; Sonido.sfx.menu(); }]);
  else {
    if (D.intro) J.hito['conoce_' + D.quien] = true;
    for (const [id, e] of Object.entries(ENCARGOS)) {
      if (e.de !== D.quien || J.encargos[id] === 'hecho') continue;
      if (e.requiere && J.encargos[e.requiere] !== 'hecho') continue;
      if (J.encargos[id] === 'activo') {
        if (encargoListo(id)) ops.push(['ENTREGAR: ' + e.nombre, () => {
          if (e.pide) for (const [it, n] of e.pide) quitar(it, n);
          J.encargos[id] = 'hecho'; darPremio(e.premio); Sonido.sfx.victoria();
          J.dialogo = { quien: D.quien, lineas: [e.linea], k: 0 };
        }]);
      } else ops.push(['ENCARGO: ' + e.nombre, () => { J.encargos[id] = 'activo'; Sonido.sfx.nivel(); aviso('Nuevo encargo: ' + e.nombre, null, P.color); J.dialogo = { quien: D.quien, lineas: [e.desc], k: 0 }; }]);
    }
    ops.push(['ADIÓS', () => { J.panel = null; J.dialogo = null; }]);
  }
  const h = Math.max(70, 30 + lineas.length * 8 + 4 + ops.length * 15), y = Math.max(4, Math.round(LH / 2 - h / 2));
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => {}, { mudo: true });
  sprGrande(ctx, D.quien, x + 30, y + 58, 1);
  texto(ctx, P.nombre, x + 62, y + 6, P.color, { sombra: COL.negro });
  texto(ctx, P.titulo, x + 62, y + 14, COL.suave);
  lineas.forEach((l, k) => texto(ctx, l, x + 62, y + 26 + k * 8, COL.texto));
  ops.forEach(([txt, fn], k) => botonTexto(ctx, x + 62, y + h - 4 - (ops.length - k) * 15, w - 68, 13, txt, fn, { color: txt === 'ADIÓS' ? COL.botonOff : '#2a4a5a' }));
};

// ---------------------------------------------------------------- diario y bestiario (pestañas de la mochila)
function panelDiario(ctx, x, y, w, h) {
  let fy = y + 22;
  const linea = (txt, color = COL.texto, sangria = 0) => { for (const l of partirTexto(txt, w - 14 - sangria)) { if (fy > y + h - 8) return; texto(ctx, l, x + 7 + sangria, fy, color); fy += 7; } };
  texto(ctx, 'LO QUE HAY QUE HACER', x + 7, fy, COL.oro); fy += 9;
  linea(J.objetivo < OBJETIVOS.length ? OBJETIVOS[J.objetivo].txt : 'El Corazón late. Por ahora no hay nada más.', '#ffd8a0', 4);
  fy += 3;
  texto(ctx, 'ENCARGOS', x + 7, fy, COL.oro); fy += 9;
  const activos = Object.entries(J.encargos || {}).filter(([, v]) => v === 'activo');
  if (!activos.length) linea('Nadie te ha pedido nada. Busca a quien todavía hable: en la Ciénaga y en la Ciudad Velada.', COL.suave, 4);
  for (const [id] of activos) { const e = ENCARGOS[id], [a, b] = progresoEncargo(id); linea(`${e.nombre} (${PERSONAJES[e.de].nombre.toLowerCase()}): ${a}/${b}${a >= b ? ' · ¡listo para entregar!' : ''}`, a >= b ? '#9fffa8' : COL.texto, 4); }
  const hechos = Object.values(J.encargos || {}).filter((v) => v === 'hecho').length;
  fy += 3;
  texto(ctx, 'LO QUE SABES', x + 7, fy, COL.oro); fy += 9;
  linea(`Tablillas leídas: ${leidas()}/${TOTAL_TABLILLAS}. Veladores registrados: ${J.hito.restos || 0}. Puertas de runas abiertas: ${contar('puzle_')}/${M.puzles.length}. Encargos cumplidos: ${hechos}/${Object.keys(ENCARGOS).length}.`, COL.suave, 4);
}
function panelBestiario(ctx, x, y, w, h) {
  const tipos = Object.keys(FICHAS), cols = Math.floor((w - 12) / 19), B = J.bestiario || {};
  if (UI.selBestia === undefined) UI.selBestia = -1;
  tipos.forEach((t, k) => {
    const cx = x + 6 + (k % cols) * 19, cy = y + 20 + Math.floor(k / cols) * 19, visto = B[t] > 0;
    ctx.fillStyle = UI.selBestia === k ? '#5a3a2a' : '#1a1218'; ctx.fillRect(cx, cy, 18, 18);
    if (visto) { const n = `${t}_0`, r = SPR[n]; if (r) { const k2 = Math.min(1, 16 / Math.max(r.w, r.h)); ctx.drawImage(ATLAS, r.x, r.y, r.w, r.h, Math.round(cx + 9 - (r.w * k2) / 2), Math.round(cy + 17 - r.h * k2), r.w * k2, r.h * k2); } }
    else texto(ctx, '?', cx + 7, cy + 6, COL.suave);
    boton(cx, cy, 18, 18, () => { UI.selBestia = k; Sonido.sfx.menu(); }, { mudo: true });
  });
  // los jefes, en su propia fila: se desvelan al vencerlos
  const jy = y + 22 + Math.ceil(tipos.length / cols) * 19, JM = J.jefesMuertos || {};
  texto(ctx, 'JEFES', x + 7, jy, COL.oro);
  BESTIARIO_JEFES.forEach((id, k) => {
    const cx = x + 6 + k * 19, cy = jy + 8, visto = !!JM[id];
    ctx.fillStyle = UI.selBestia === 'j' + k ? '#5a3a2a' : '#22101a'; ctx.fillRect(cx, cy, 18, 18);
    const r = visto && SPR[`${id}_0`];
    if (r) { const k2 = Math.min(1, 16 / Math.max(r.w, r.h)); ctx.drawImage(ATLAS, r.x, r.y, r.w, r.h, Math.round(cx + 9 - (r.w * k2) / 2), Math.round(cy + 17 - r.h * k2), r.w * k2, r.h * k2); }
    else texto(ctx, '?', cx + 7, cy + 6, '#a05050');
    boton(cx, cy, 18, 18, () => { UI.selBestia = 'j' + k; Sonido.sfx.menu(); }, { mudo: true });
  });
  const fy = jy + 30;
  if (typeof UI.selBestia === 'string') {
    const id = BESTIARIO_JEFES[+UI.selBestia.slice(1)], d = JEFES[id];
    if (!JM[id]) { texto(ctx, 'Todavía vive. Lo sabrás cuando no.', x + 7, fy, COL.suave); return; }
    texto(ctx, `${d.nombre}, ${d.titulo}`, x + 7, fy, '#ff9a7a');
    partirTexto(`«${d.frase}»`, w - 14).forEach((l, k) => texto(ctx, l, x + 7, fy + 9 + k * 7, COL.texto));
    return;
  }
  const t = tipos[UI.selBestia];
  if (!t) { partirTexto(`Criaturas conocidas: ${tipos.filter((q) => B[q] > 0).length}/${tipos.length}. Jefes vencidos: ${BESTIARIO_JEFES.filter((q) => JM[q]).length}/${BESTIARIO_JEFES.length}. Toca una para leer su ficha.`, w - 14).forEach((l, k) => texto(ctx, l, x + 7, fy + k * 7, COL.suave)); return; }
  if (!(B[t] > 0)) { texto(ctx, 'Todavía no la conoces. Mata una.', x + 7, fy, COL.suave); return; }
  texto(ctx, `${CRIATURAS[t].nombre} · vencidas: ${B[t]}`, x + 7, fy, COL.oro);
  partirTexto(`«${FICHAS[t]}»`, w - 14).forEach((l, k) => texto(ctx, l, x + 7, fy + 9 + k * 7, COL.texto));
}

// ---------------------------------------------------------------- objetivos de las bandas (siguen a los de la Selva)
OBJETIVOS.push(
  { txt: 'Baja a la Ciénaga de la Podre, detrás de la Selva', hecho: (J) => J.hito['region_' + REGION.CIENAGA] },
  { txt: 'Busca a Muda en los palafitos de la Ciénaga', hecho: (J) => J.hito.conoce_muda },
  { txt: 'Pica médula de Ásor en la turba (pico carmesí)', hecho: (J) => J.hito.tiene_mineral_medula || J.hito.mesa_medula },
  { txt: 'Vence a Grul en la Caja Torácica de Ásor', hecho: (J) => J.jefesMuertos.grul },
  { txt: 'Haz la mesa de médula con el corazón de Grul', hecho: (J) => J.hito.mesa_medula },
  { txt: 'Entra en la Ciudad Velada', hecho: (J) => J.hito['region_' + REGION.CIUDAD] },
  { txt: 'Sube a la torre del Archivero', hecho: (J) => J.hito.conoce_archivero },
  { txt: 'Abre la Cripta de los Veladores (las palancas)', hecho: (J) => J.hito.cripta },
  { txt: 'Vence al Centinela, el Que No Duerme', hecho: (J) => J.jefesMuertos.centinela },
  { txt: 'Haz la forja velada con la llama del Centinela', hecho: (J) => J.hito.mesa_plata },
);
