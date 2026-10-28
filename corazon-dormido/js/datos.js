'use strict';
// Datos del juego: tipos de pared y suelo, objetos, recetas y criaturas. Para equilibrar, se toca aquí.

const T = 16;              // tamaño de una casilla en píxeles
const N_MUNDO = 256;       // el mundo mide N_MUNDO × N_MUNDO casillas
const CENTRO = N_MUNDO / 2;

const BIOMA = { TIERRA: 1, ARCILLA: 2, PIEDRA: 3, RUINA: 4 };
const NOMBRE_BIOMA = { 1: 'tierra', 2: 'arcilla', 3: 'piedra', 4: 'ruina' };

// Paredes. nivel: el pico mínimo para romperla (99 = no se rompe).
const MURO = { NADA: 0, TIERRA: 1, ARCILLA: 2, PIEDRA: 3, RUINA: 4, ROCA: 5 };
const INFO_MURO = {
  1: { nombre: 'Tierra', arte: 'tierra', vida: 30, nivel: 0, mapa: '#6a4a38' },
  2: { nombre: 'Arcilla', arte: 'arcilla', vida: 55, nivel: 0, mapa: '#8c4834' },
  3: { nombre: 'Piedra', arte: 'piedra', vida: 90, nivel: 1, mapa: '#4f5a74' },
  4: { nombre: 'Muro antiguo', arte: 'ruina', vida: 1, nivel: 99, mapa: '#3c5c66' },
  5: { nombre: 'Roca madre', arte: 'roca', vida: 1, nivel: 99, mapa: '#1a1820' },
};
// Suelos (arte de suelo)
const SUELO_DE = { 1: 'tierra', 2: 'arcilla', 3: 'piedra', 4: 'ruina', 5: 'arena' };
const SUELO_MAPA = { 1: '#2c201b', 2: '#38201d', 3: '#1c1f2a', 4: '#24323a', 5: '#33251d' };

// Menas dentro de las paredes
const MENA = { NADA: 0, COBRE: 1, ESTANO: 2, HIERRO: 3 };
const INFO_MENA = {
  1: { arte: 'cobre', item: 'mineral_cobre', nivel: 0, extra: 1.4, mapa: '#e08a50' },
  2: { arte: 'estano', item: 'mineral_estano', nivel: 1, extra: 1.5, mapa: '#c8c4e4' },
  3: { arte: 'hierro', item: 'mineral_hierro', nivel: 2, extra: 1.6, mapa: '#8cb2d4' },
};

// Objetos del inventario. tipo: material, pico, espada, armadura, comida, colocable, especial.
const ITEMS = {
  madera: { nombre: 'Madera', icono: 'i_madera', tipo: 'material', desc: 'Sale de las raíces gruesas. Para mesas, antorchas y mangos.' },
  baba: { nombre: 'Baba', icono: 'i_baba', tipo: 'material', desc: 'Pegajosa y arde bien. Con madera da más antorchas.' },
  mineral_cobre: { nombre: 'Mineral de cobre', icono: 'i_mineral_cobre', tipo: 'material', desc: 'Se funde en el horno: 2 dan un lingote.' },
  mineral_estano: { nombre: 'Mineral de estaño', icono: 'i_mineral_estano', tipo: 'material', desc: 'Está en la arcilla. Se funde en el horno.' },
  mineral_hierro: { nombre: 'Mineral de hierro', icono: 'i_mineral_hierro', tipo: 'material', desc: 'Está en la piedra. Se funde en el horno.' },
  lingote_cobre: { nombre: 'Lingote de cobre', icono: 'i_lingote_cobre', tipo: 'material', desc: 'Para el yunque y las herramientas de cobre.' },
  lingote_estano: { nombre: 'Lingote de estaño', icono: 'i_lingote_estano', tipo: 'material', desc: 'Para las herramientas de estaño.' },
  lingote_hierro: { nombre: 'Lingote de hierro', icono: 'i_lingote_hierro', tipo: 'material', desc: 'Para las herramientas de hierro.' },
  cristal: { nombre: 'Cristal', icono: 'i_cristal', tipo: 'material', desc: 'Brilla solo. Sirve para lámparas.' },
  fragmento: { nombre: 'Fragmento del Corazón', icono: 'i_fragmento', tipo: 'especial', desc: 'Late. Llévalo a un pedestal junto al Corazón.' },

  pico_viejo: { nombre: 'Pico viejo', icono: 'i_pico_viejo', tipo: 'pico', nivel: 0, minado: 10, dano: 7, vel: 0.34, max: 1, desc: 'Rompe tierra y arcilla. La piedra le queda grande.' },
  pico_cobre: { nombre: 'Pico de cobre', icono: 'i_pico_cobre', tipo: 'pico', nivel: 1, minado: 18, dano: 8, vel: 0.3, max: 1, desc: 'Ya rompe la piedra y saca estaño.' },
  pico_topo: { nombre: 'Garra del Topo', icono: 'i_pico_topo', tipo: 'pico', nivel: 2, minado: 30, dano: 14, vel: 0.24, max: 1, desc: 'La garra del Topo Rey. Cava rapidísimo y saca hierro.' },
  pico_estano: { nombre: 'Pico de estaño', icono: 'i_pico_estano', tipo: 'pico', nivel: 2, minado: 28, dano: 11, vel: 0.28, max: 1, desc: 'Saca hierro de la piedra.' },
  pico_hierro: { nombre: 'Pico de hierro', icono: 'i_pico_hierro', tipo: 'pico', nivel: 3, minado: 42, dano: 15, vel: 0.26, max: 1, desc: 'Atraviesa cualquier pared.' },
  espada_madera: { nombre: 'Espada de madera', icono: 'i_espada_madera', tipo: 'espada', dano: 11, vel: 0.38, max: 1, desc: 'Mejor que nada.' },
  espada_cobre: { nombre: 'Espada de cobre', icono: 'i_espada_cobre', tipo: 'espada', dano: 19, vel: 0.34, max: 1, desc: 'Buena contra babosas y larvas.' },
  espada_estano: { nombre: 'Espada de estaño', icono: 'i_espada_estano', tipo: 'espada', dano: 27, vel: 0.32, max: 1, desc: 'Corta el caparazón de los escarabajos.' },
  espada_hierro: { nombre: 'Espada de hierro', icono: 'i_espada_hierro', tipo: 'espada', dano: 38, vel: 0.3, max: 1, desc: 'La mejor de la demo.' },
  armadura_cobre: { nombre: 'Armadura de cobre', icono: 'i_armadura_cobre', tipo: 'armadura', def: 3, max: 1, desc: 'Defensa 3. Tócala en la mochila para ponértela.' },
  armadura_estano: { nombre: 'Armadura de estaño', icono: 'i_armadura_estano', tipo: 'armadura', def: 6, max: 1, desc: 'Defensa 6.' },
  armadura_hierro: { nombre: 'Armadura de hierro', icono: 'i_armadura_hierro', tipo: 'armadura', def: 10, max: 1, desc: 'Defensa 10.' },

  baya: { nombre: 'Baya de luz', icono: 'i_baya', tipo: 'comida', cura: 8, hambre: 14, desc: 'Cura 8 y quita un poco de hambre.' },
  hongo: { nombre: 'Hongo azul', icono: 'i_hongo', tipo: 'comida', cura: 4, hambre: 22, desc: 'Quita hambre. Asado en el horno, mucho mejor.' },
  hongo_asado: { nombre: 'Hongo asado', icono: 'i_hongo_asado', tipo: 'comida', cura: 18, hambre: 40, desc: 'Cura 18 y quita bastante hambre.' },
  carne_larva: { nombre: 'Carne de larva', icono: 'i_carne', tipo: 'comida', cura: 4, hambre: 25, desc: 'Cruda no sabe a nada. Ásala.' },
  larva_asada: { nombre: 'Larva asada', icono: 'i_carne_asada', tipo: 'comida', cura: 26, hambre: 55, desc: 'Cura 26. Lo mejor para antes de un jefe.' },

  antorcha: { nombre: 'Antorcha', icono: 'i_antorcha', tipo: 'colocable', obj: 'antorcha', desc: 'Ilumina y espanta a las criaturas: no salen donde hay luz.' },
  mesa: { nombre: 'Mesa de trabajo', icono: 'i_mesa', tipo: 'colocable', obj: 'mesa', max: 9, desc: 'Ponla en el suelo para crear más cosas cerca de ella.' },
  horno: { nombre: 'Horno', icono: 'i_horno', tipo: 'colocable', obj: 'horno', max: 9, desc: 'Funde minerales y asa comida. Úsalo de cerca.' },
  yunque: { nombre: 'Yunque', icono: 'i_yunque', tipo: 'colocable', obj: 'yunque', max: 9, desc: 'Forja picos, espadas y armaduras de metal.' },
  lampara: { nombre: 'Lámpara de cristal', icono: 'i_lampara', tipo: 'colocable', obj: 'lampara', desc: 'Luz grande y fría. No se apaga nunca.' },
};

// est: la estación que tiene que estar cerca (null = a mano).
const RECETAS = [
  { item: 'mesa', n: 1, ing: { madera: 8 }, est: null },
  { item: 'antorcha', n: 2, ing: { madera: 1 }, est: null },
  { item: 'antorcha', n: 6, ing: { madera: 1, baba: 1 }, est: 'mesa' },
  { item: 'espada_madera', n: 1, ing: { madera: 7 }, est: 'mesa' },
  { item: 'horno', n: 1, ing: { mineral_cobre: 8, madera: 4 }, est: 'mesa' },
  { item: 'yunque', n: 1, ing: { lingote_cobre: 5, madera: 3 }, est: 'mesa' },
  { item: 'lingote_cobre', n: 1, ing: { mineral_cobre: 2 }, est: 'horno' },
  { item: 'lingote_estano', n: 1, ing: { mineral_estano: 2 }, est: 'horno' },
  { item: 'lingote_hierro', n: 1, ing: { mineral_hierro: 2 }, est: 'horno' },
  { item: 'hongo_asado', n: 1, ing: { hongo: 1 }, est: 'horno' },
  { item: 'larva_asada', n: 1, ing: { carne_larva: 1 }, est: 'horno' },
  { item: 'pico_cobre', n: 1, ing: { lingote_cobre: 6, madera: 2 }, est: 'yunque' },
  { item: 'espada_cobre', n: 1, ing: { lingote_cobre: 7, madera: 2 }, est: 'yunque' },
  { item: 'armadura_cobre', n: 1, ing: { lingote_cobre: 10 }, est: 'yunque' },
  { item: 'pico_estano', n: 1, ing: { lingote_estano: 8, lingote_cobre: 2 }, est: 'yunque' },
  { item: 'espada_estano', n: 1, ing: { lingote_estano: 9, lingote_cobre: 2 }, est: 'yunque' },
  { item: 'armadura_estano', n: 1, ing: { lingote_estano: 12 }, est: 'yunque' },
  { item: 'pico_hierro', n: 1, ing: { lingote_hierro: 10, lingote_estano: 2 }, est: 'yunque' },
  { item: 'espada_hierro', n: 1, ing: { lingote_hierro: 11, lingote_estano: 2 }, est: 'yunque' },
  { item: 'armadura_hierro', n: 1, ing: { lingote_hierro: 14 }, est: 'yunque' },
  { item: 'lampara', n: 2, ing: { cristal: 1, lingote_hierro: 1 }, est: 'yunque' },
];
const NOMBRE_ESTACION = { mesa: 'Mesa de trabajo', horno: 'Horno', yunque: 'Yunque' };

// Objetos puestos en el mundo. bloquea: no se puede pasar. luz: [radio en casillas, r, g, b].
// vida: golpes que aguanta; suelta: lo que da al romperse.
const OBJ = {
  antorcha: { arte: 'antorcha', anim: 3, brillo: true, luz: [7, 1.0, 0.78, 0.52], parpadeo: true, vida: 1, suelta: [['antorcha', 1, 1]], mapa: '#ffd060' },
  mesa: { arte: 'mesa', bloquea: true, vida: 3, suelta: [['mesa', 1, 1]], estacion: 'mesa', mapa: '#c28a52' },
  horno: { arte: 'horno', anim: 2, brillo: true, bloquea: true, luz: [4, 1.0, 0.55, 0.3], parpadeo: true, vida: 3, suelta: [['horno', 1, 1]], estacion: 'horno', mapa: '#ff9a3c' },
  yunque: { arte: 'yunque', bloquea: true, vida: 3, suelta: [['yunque', 1, 1]], estacion: 'yunque', mapa: '#9aa6b8' },
  lampara: { arte: 'lampara', brillo: true, luz: [9, 0.75, 0.95, 1.0], vida: 3, suelta: [['lampara', 1, 1]], mapa: '#b8fbff' },
  raiz: { arte: 'raiz', bloquea: true, vida: 24, suelta: [['madera', 2, 4]], planta: true, mapa: '#8a5a36' },
  arbusto: { arte: 'arbusto', brillo: true, luz: [3, 0.35, 0.7, 1.0], vida: 8, suelta: [['baya', 1, 3]], planta: true, mapa: '#3f7a5a' },
  hongo: { arte: 'hongo', brillo: true, luz: [3.5, 0.3, 0.85, 0.95], vida: 8, suelta: [['hongo', 1, 2]], planta: true, mapa: '#3fd8e8' },
  cristal: { arte: 'cristal', brillo: true, bloquea: true, luz: [4.5, 0.35, 0.9, 1.0], vida: 70, nivel: 2, suelta: [['cristal', 2, 3]], mapa: '#6ff0ff' },
  corazon: { arte: 'corazon', bloquea: true, vida: 0, mapa: '#ff6b8f' },
  pedestal: { arte: 'pedestal', bloquea: true, vida: 0, mapa: '#6b919b' },
};

// Criaturas. bioma: dónde aparecen. botin: [item, min, max, probabilidad].
const CRIATURAS = {
  babosa: { nombre: 'Babosa', vida: 26, dano: 9, vel: 34, radio: 5, bioma: BIOMA.TIERRA, salta: true, botin: [['baba', 1, 2, 0.8], ['baya', 1, 1, 0.12]] },
  larva: { nombre: 'Larva', vida: 44, dano: 13, vel: 30, radio: 6, bioma: BIOMA.ARCILLA, botin: [['carne_larva', 1, 1, 0.65], ['baba', 1, 1, 0.3]] },
  escarabajo: { nombre: 'Escarabajo', vida: 80, dano: 18, def: 3, vel: 42, radio: 6, bioma: BIOMA.PIEDRA, embiste: true, botin: [['cristal', 1, 1, 0.3], ['mineral_hierro', 1, 2, 0.4]] },
};
const JEFE_TOPO = { nombre: 'TOPO REY', vida: 1100, dano: 18, vel: 32, radio: 11 };

// Lo que llevas al empezar
const INICIO = [['pico_viejo', 1], ['antorcha', 6], ['baya', 3]];

// Objetivos de la demo (la guía arriba a la derecha). hecho(J) dice si ya está.
const OBJETIVOS = [
  { txt: 'Rompe raíces gruesas: 8 de madera', hecho: (J) => J.cuenta('madera') >= 8 || J.hito.mesa },
  { txt: 'Crea una mesa de trabajo y ponla', hecho: (J) => J.hito.mesa },
  { txt: 'Pica cobre en las paredes: 8 minerales', hecho: (J) => J.cuenta('mineral_cobre') >= 8 || J.hito.horno },
  { txt: 'Crea un horno junto a la mesa', hecho: (J) => J.hito.horno },
  { txt: 'Funde lingotes y crea un yunque', hecho: (J) => J.hito.yunque },
  { txt: 'Forja una espada de cobre', hecho: (J) => J.hito.espada_cobre },
  { txt: 'Busca y derrota al Topo Rey', hecho: (J) => J.hito.topo },
  { txt: 'Lleva el fragmento al Corazón', hecho: (J) => J.hito.fragmento },
];
