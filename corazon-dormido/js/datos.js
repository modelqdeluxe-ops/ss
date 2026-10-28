'use strict';
// Datos del juego (fase 1: el anillo interior). Para equilibrar o añadir contenido, se toca aquí.
// Diseño completo en DISEÑO.md.

const T = 16;               // tamaño de una casilla en píxeles del arte
const N_MUNDO = 320;        // el mundo mide N_MUNDO × N_MUNDO casillas
const CENTRO = N_MUNDO / 2;
const R_RAICES = 48;        // radio de Las Raíces
const R_MURALLA = 142;      // radio de la Gran Muralla

// ---------------------------------------------------------------- regiones

const REGION = { SANTUARIO: 0, RAICES: 1, ARCILLA: 2, RUINAS: 3 };
const NOMBRE_REGION = ['Santuario del Corazón', 'Las Raíces', 'Cuevas de Arcilla', 'Ruinas Olvidadas'];
// Tono de la oscuridad en cada región (sombras frías, ambiente propio)
const TONO_REGION = [[0.9, 0.95, 1.1], [0.95, 0.94, 1.05], [1.02, 0.92, 0.95], [0.9, 0.96, 1.08]];

// ---------------------------------------------------------------- suelos
// arte: textura s_<arte>_<0..3>. decor: adornos de pie (guijarro = el del bioma). planos: adornos tumbados.
const SUELO = { TIERRA: 1, CESPED: 2, ARCILLA: 3, COLMENA: 4, PIEDRA: 5, RUINA: 6, LOSA: 7, ALFOMBRA: 8, ARENA: 9, GRAVA: 10, AGUA: 11, ARADA: 12, MADERA: 13, BARRO: 14 };
const INFO_SUELO = {
  1: { arte: 'tierra', mapa: '#3a2a22', decor: ['guijarro', 'guijarro', 'dc_hierba_0', 'dc_seta_0', 'dc_seta_1'], densidad: 0.09, planos: ['ad_piedras', 'ad_raices', 'ad_terron'], piedra: 1, arable: true },
  2: { arte: 'cesped', mapa: '#2c4a26', decor: ['dc_hierba_0', 'dc_hierba_1', 'dc_hierba_1', 'dc_flor_0', 'dc_flor_1', 'guijarro'], densidad: 0.22, planos: ['ad_hierba'], piedra: 1, arable: true },
  3: { arte: 'arcilla', mapa: '#4a2620', decor: ['guijarro', 'guijarro', 'dc_huesos', 'dc_seta_1', 'dc_fibra'], densidad: 0.09, planos: ['ad_piedras', 'ad_huesos'], piedra: 2, arable: true },
  4: { arte: 'colmena', mapa: '#4a3a1c', decor: ['dc_huevo', 'dc_huevo', 'guijarro'], densidad: 0.1, planos: ['ad_baba'], piedra: 2 },
  5: { arte: 'piedra', mapa: '#262a36', decor: ['guijarro', 'guijarro', 'dc_estalagmita_0', 'dc_estalagmita_1', 'dc_cristalito'], densidad: 0.08, piedra: 3 },
  6: { arte: 'ruina', mapa: '#2c3c46', decor: ['guijarro', 'dc_hierba_0'], densidad: 0.04, piedra: 4 },
  7: { arte: 'losa', mapa: '#38404c', decor: ['guijarro', 'dc_vasija_rota'], densidad: 0.05, planos: ['ad_musgo'], piedra: 3 },
  8: { arte: 'alfombra', mapa: '#5a1e2e' },
  9: { arte: 'arena', mapa: '#3c2a20', decor: ['dc_huesos', 'guijarro', 'guijarro'], densidad: 0.08, planos: ['ad_garras', 'ad_huesos'], piedra: 1 },
  10: { arte: 'grava', mapa: '#2a2622', decor: ['guijarro', 'guijarro', 'guijarro'], densidad: 0.12, piedra: 3 },
  11: { arte: 'agua', mapa: '#1b4d76', liquido: true },
  12: { arte: 'arada', mapa: '#4a2e1e' },
  13: { arte: 'madera', mapa: '#7a5232' },
  14: { arte: 'barro', mapa: '#3e3618', decor: ['guijarro', 'dc_seta_1'], densidad: 0.06, piedra: 2, arable: true },
};

// ---------------------------------------------------------------- paredes
// poder: pico mínimo para romperla. bloque: lo que suelta. alto: veces la altura normal. tapa: oscuridad de la tapa.
const MURO = { NADA: 0, TIERRA: 1, ARCILLA: 2, COLMENA: 3, PIEDRA: 4, LADRILLO: 5, SANTUARIO: 6, ROCA: 7, MURALLA: 8, MADERA: 9 };
const INFO_MURO = {
  1: { nombre: 'Tierra', arte: 'tierra', vida: 30, poder: 0, bloque: 'bloque_tierra', mapa: '#6a4a38' },
  2: { nombre: 'Arcilla', arte: 'arcilla', vida: 50, poder: 10, bloque: 'bloque_arcilla', mapa: '#8c4834' },
  3: { nombre: 'Colmena', arte: 'colmena', vida: 70, poder: 15, bloque: 'bloque_colmena', mapa: '#8a6a2c' },
  4: { nombre: 'Piedra', arte: 'piedra', vida: 80, poder: 20, bloque: 'bloque_piedra', mapa: '#4f5a74' },
  5: { nombre: 'Muro de las ruinas', arte: 'ladrillo', vida: 140, poder: 35, bloque: 'bloque_ladrillo', mapa: '#5a6878' },
  6: { nombre: 'Muro antiguo', arte: 'ruina', vida: 1, poder: 999, mapa: '#3c5c66' },
  7: { nombre: 'Roca madre', arte: 'roca', vida: 1, poder: 999, mapa: '#1a1820' },
  8: { nombre: 'Gran Muralla', arte: 'muralla', vida: 1, poder: 999, alto: 1.7, tapa: 0.5, mapa: '#3a6a4a' },
  9: { nombre: 'Pared de madera', arte: 'madera', vida: 40, poder: 0, bloque: 'pared_madera', mapa: '#8a5a36' },
};
const MENA = { NADA: 0, COBRE: 1, ESTANO: 2, HIERRO: 3, ORO: 4 };
const INFO_MENA = {
  1: { arte: 'cobre', item: 'mineral_cobre', poder: 0, extra: 1.4, mapa: '#e08a50' },
  2: { arte: 'estano', item: 'mineral_estano', poder: 20, extra: 1.5, mapa: '#c8c4e4' },
  3: { arte: 'hierro', item: 'mineral_hierro', poder: 35, extra: 1.6, mapa: '#8cb2d4' },
  4: { arte: 'oro', item: 'mineral_oro', poder: 20, extra: 1.8, mapa: '#ffd24a' },
};

// ---------------------------------------------------------------- objetos del inventario
// tipo: material, bloque, pico, espada, maza, arco, municion, azada, regadera, cana, casco, peto, botas, accesorio,
// comida, semilla, colocable, especial. rareza: 0 común, 1 raro, 2 épico, 3 legendario.
const RAREZA = [{ nombre: 'Común', color: '#f6ecda' }, { nombre: 'Raro', color: '#6ab0ff' }, { nombre: 'Épico', color: '#c07aff' }, { nombre: 'Legendario', color: '#ffa640' }];
const ITEMS = {};
function item(id, d) { ITEMS[id] = { icono: 'i_' + id, max: 99, rareza: 0, ...d }; }

// materiales
item('madera', { nombre: 'Madera', tipo: 'material', desc: 'De las raíces gruesas. Para mesas, antorchas y mangos.' });
item('fibra', { nombre: 'Fibra', tipo: 'material', desc: 'Hebras de las plantas de la arcilla. Para cuerdas, arcos y camas.' });
item('baba', { nombre: 'Baba', tipo: 'material', desc: 'Pegajosa y arde bien.' });
item('baba_roja', { nombre: 'Baba roja', tipo: 'material', rareza: 1, desc: 'Baba espesa de las babosas rojas. Arde aún mejor.' });
item('quitina', { nombre: 'Quitina', tipo: 'material', desc: 'Caparazón de larva. Duro y ligero.' });
item('hueso', { nombre: 'Hueso', tipo: 'material', desc: 'De las criaturas de las ruinas.' });
item('cristal', { nombre: 'Cristal', tipo: 'material', rareza: 1, desc: 'Brilla solo. Para lámparas y joyas.' });
item('gema_antigua', { nombre: 'Gema antigua', tipo: 'material', rareza: 2, desc: 'Una piedra que late. La guardaban las ruinas.' });
for (const [m, n] of [['cobre', 'cobre'], ['estano', 'estaño'], ['hierro', 'hierro'], ['oro', 'oro']]) {
  item('mineral_' + m, { nombre: 'Mineral de ' + n, tipo: 'material', desc: 'Se funde en el horno: 2 dan un lingote.' });
  item('lingote_' + m, { nombre: 'Lingote de ' + n, tipo: 'material', desc: 'Para herramientas, armas y mesas.' });
}
// bloques que se pueden poner
item('bloque_tierra', { nombre: 'Bloque de tierra', tipo: 'bloque', muro: MURO.TIERRA, desc: 'Ponlo para levantar una pared.' });
item('bloque_arcilla', { nombre: 'Bloque de arcilla', tipo: 'bloque', muro: MURO.ARCILLA, desc: 'Ponlo para levantar una pared.' });
item('bloque_colmena', { nombre: 'Bloque de colmena', tipo: 'bloque', muro: MURO.COLMENA, desc: 'Ponlo para levantar una pared.' });
item('bloque_piedra', { nombre: 'Bloque de piedra', tipo: 'bloque', muro: MURO.PIEDRA, desc: 'Ponlo para levantar una pared.' });
item('bloque_ladrillo', { nombre: 'Bloque de ruinas', tipo: 'bloque', muro: MURO.LADRILLO, desc: 'Ponlo para levantar una pared que aguanta mucho.' });
item('pared_madera', { nombre: 'Pared de madera', tipo: 'bloque', muro: MURO.MADERA, desc: 'Para tu casa.' });
item('suelo_madera', { nombre: 'Suelo de madera', tipo: 'suelo', suelo: SUELO.MADERA, desc: 'Ponlo en el suelo de tu casa.' });
item('suelo_losa', { nombre: 'Suelo de losas', tipo: 'suelo', suelo: SUELO.LOSA, desc: 'Piedra labrada para el suelo.' });
item('alfombra', { nombre: 'Alfombra', tipo: 'suelo', suelo: SUELO.ALFOMBRA, rareza: 1, desc: 'Roja y mullida.' });

// herramientas (poder: poder de minado)
item('pico_viejo', { nombre: 'Pico viejo', tipo: 'pico', poder: 10, dano: 7, vel: 0.34, max: 1, desc: 'Rompe la tierra. Para la arcilla necesitas uno de cobre.' });
item('pico_cobre', { nombre: 'Pico de cobre', tipo: 'pico', poder: 20, dano: 9, vel: 0.3, max: 1, desc: 'Rompe arcilla, colmena y piedra.' });
item('pico_topo', { nombre: 'Garra del Topo', tipo: 'pico', poder: 30, dano: 14, vel: 0.24, max: 1, rareza: 2, desc: 'La garra del Topo Rey. Cava rapidísimo.' });
item('pico_estano', { nombre: 'Pico de estaño', tipo: 'pico', poder: 35, dano: 12, vel: 0.28, max: 1, desc: 'Rompe los muros de las ruinas y saca hierro.' });
item('pico_hierro', { nombre: 'Pico de hierro', tipo: 'pico', poder: 55, dano: 16, vel: 0.25, max: 1, rareza: 1, desc: 'Atraviesa todo lo que hay a este lado de la Muralla.' });
item('azada_madera', { nombre: 'Azada de madera', tipo: 'azada', max: 1, desc: 'Ara la tierra para plantar.' });
item('azada_cobre', { nombre: 'Azada de cobre', tipo: 'azada', max: 1, area: 1, desc: 'Ara tres casillas de una vez.' });
item('regadera', { nombre: 'Regadera', tipo: 'regadera', max: 1, desc: 'Riega: lo regado crece el doble de rápido.' });
item('cana_madera', { nombre: 'Caña de madera', tipo: 'cana', fuerza: 1, max: 1, desc: 'Lánzala al agua y espera a que pique.' });
item('cana_cobre', { nombre: 'Caña de cobre', tipo: 'cana', fuerza: 1.5, max: 1, rareza: 1, desc: 'El sedal aguanta más tirones.' });

// armas
item('espada_madera', { nombre: 'Espada de madera', tipo: 'espada', dano: 11, vel: 0.36, max: 1, desc: 'Mejor que nada.' });
item('espada_cobre', { nombre: 'Espada de cobre', tipo: 'espada', dano: 19, vel: 0.34, max: 1, desc: 'Buena contra babosas y larvas.' });
item('espada_estano', { nombre: 'Espada de estaño', tipo: 'espada', dano: 27, vel: 0.32, max: 1, desc: 'Corta la quitina.' });
item('espada_hierro', { nombre: 'Espada de hierro', tipo: 'espada', dano: 38, vel: 0.3, max: 1, rareza: 1, desc: 'Equilibrada y mortal.' });
item('maza_cobre', { nombre: 'Maza de cobre', tipo: 'maza', dano: 30, vel: 0.62, max: 1, desc: 'Lenta, pero empuja lejos y aturde.' });
item('maza_hierro', { nombre: 'Maza de hierro', tipo: 'maza', dano: 58, vel: 0.6, max: 1, rareza: 1, desc: 'Rompe escudos y cráneos.' });
item('garra_reina', { nombre: 'Aguijón de la Reina', tipo: 'espada', dano: 34, vel: 0.22, max: 1, rareza: 2, veneno: 4, desc: 'Rápido y venenoso.' });
item('baculo_ozmar', { nombre: 'Báculo de Ozmar', tipo: 'arco', dano: 30, vel: 0.5, max: 1, rareza: 3, magico: true, desc: 'Lanza orbes de sombra que buscan. No gasta flechas.' });
item('arco_madera', { nombre: 'Arco de madera', tipo: 'arco', dano: 9, vel: 0.5, max: 1, desc: 'Dispara flechas. Apunta solo a lo que tengas delante.' });
item('arco_estano', { nombre: 'Arco de estaño', tipo: 'arco', dano: 18, vel: 0.42, max: 1, desc: 'Más tenso, más lejos.' });
item('flecha', { nombre: 'Flecha', tipo: 'municion', dano: 4, desc: 'Para el arco.' });
item('flecha_cobre', { nombre: 'Flecha de cobre', tipo: 'municion', dano: 9, desc: 'Punta de cobre.' });

// armadura y accesorios
for (const [m, n, d, r] of [['cobre', 'cobre', 1, 0], ['estano', 'estaño', 2, 0], ['hierro', 'hierro', 3, 1], ['larva', 'larva', 2, 1]]) {
  item('casco_' + m, { nombre: `Casco de ${n}`, tipo: 'casco', def: d * 2, max: 1, rareza: r, desc: `Defensa ${d * 2}.` });
  item('peto_' + m, { nombre: `Peto de ${n}`, tipo: 'peto', def: d * 3, max: 1, rareza: r, desc: `Defensa ${d * 3}.` });
  item('botas_' + m, { nombre: `Botas de ${n}`, tipo: 'botas', def: d, vel: m === 'larva' ? 0.08 : 0, max: 1, rareza: r, desc: m === 'larva' ? 'Defensa 2 y más velocidad.' : `Defensa ${d}.` });
}
item('anillo_cobre', { nombre: 'Anillo de cobre', tipo: 'accesorio', vidaMax: 10, max: 1, desc: '+10 de vida máxima.' });
item('collar_cristal', { nombre: 'Collar de cristal', tipo: 'accesorio', luz: 2, max: 1, rareza: 1, desc: 'Tu luz llega más lejos.' });
item('amuleto_topo', { nombre: 'Amuleto del Topo', tipo: 'accesorio', minado: 0.2, max: 1, rareza: 2, desc: '+20 % de daño a las paredes.' });
item('broche_larva', { nombre: 'Broche de la Reina', tipo: 'accesorio', vel: 0.12, max: 1, rareza: 2, desc: '+12 % de velocidad.' });
item('anillo_oro', { nombre: 'Anillo de oro', tipo: 'accesorio', critico: 0.08, max: 1, rareza: 1, desc: '+8 % de golpe crítico.' });
item('corona_ozmar', { nombre: 'Corona de Ozmar', tipo: 'accesorio', dano: 0.2, vidaMax: 20, max: 1, rareza: 3, desc: '+20 % de daño y +20 de vida.' });

// comida cruda y semillas
item('baya', { nombre: 'Baya de luz', tipo: 'comida', cura: 8, hambre: 12, ingrediente: { vida: 6 }, desc: 'Cura un poco. Ingrediente.' });
item('hongo', { nombre: 'Hongo azul', tipo: 'comida', cura: 4, hambre: 20, ingrediente: { minado: 0.1 }, desc: 'Quita hambre. Ingrediente.' });
item('bulbo', { nombre: 'Bulbo de cueva', tipo: 'comida', cura: 6, hambre: 26, ingrediente: { vidaMax: 10 }, desc: 'Un tubérculo dulce. Ingrediente.' });
item('trigo', { nombre: 'Trigo de cueva', tipo: 'comida', cura: 2, hambre: 16, ingrediente: { regen: 0.5 }, desc: 'Ingrediente.' });
item('carne_larva', { nombre: 'Carne de larva', tipo: 'comida', cura: 4, hambre: 25, ingrediente: { dano: 0.08 }, desc: 'Ingrediente.' });
item('carne_seton', { nombre: 'Carne de setón', tipo: 'comida', cura: 5, hambre: 18, ingrediente: { defensa: 3 }, desc: 'Ingrediente.' });
for (const [p, n, ing] of [['guppy', 'Pez guijarro', { vel: 0.06 }], ['barro', 'Pez de barro', { defensa: 4 }], ['cristal', 'Pez de cristal', { luz: 1.5 }], ['dorado', 'Pez dorado', { suerte: 0.15 }]]) {
  item('pez_' + p, { nombre: n, tipo: 'comida', cura: 6, hambre: 22, ingrediente: ing, rareza: p === 'dorado' ? 2 : p === 'cristal' ? 1 : 0, desc: 'Pescado. Ingrediente.' });
}
item('semilla_baya', { nombre: 'Semilla de baya', tipo: 'semilla', cultivo: 'baya', desc: 'Plántala en tierra arada.' });
item('espora_hongo', { nombre: 'Esporas de hongo', tipo: 'semilla', cultivo: 'hongo', desc: 'Plántalas en tierra arada.' });
item('semilla_bulbo', { nombre: 'Semilla de bulbo', tipo: 'semilla', cultivo: 'bulbo', desc: 'Plántala en tierra arada.' });
item('semilla_trigo', { nombre: 'Semilla de trigo', tipo: 'semilla', cultivo: 'trigo', desc: 'Plántala en tierra arada.' });

// colocables
const COLOCABLE = (id, nombre, obj, desc, extra = {}) => item(id, { nombre, tipo: 'colocable', obj, desc, ...extra });
COLOCABLE('antorcha', 'Antorcha', 'antorcha', 'Ilumina y espanta: las criaturas no salen donde hay luz.');
COLOCABLE('lampara', 'Lámpara de cristal', 'lampara', 'Luz grande y fría.', { rareza: 1 });
COLOCABLE('farol_oro', 'Farol de oro', 'farol', 'Luz cálida enorme.', { rareza: 1 });
COLOCABLE('mesa', 'Mesa de trabajo', 'mesa', 'Para crear más cosas cerca de ella.', { max: 9 });
COLOCABLE('mesa_cobre', 'Mesa de cobre', 'mesa_cobre', 'Mesa mejorada: picos, azadas y lo de la mesa básica.', { max: 9 });
COLOCABLE('mesa_estano', 'Mesa de estaño', 'mesa_estano', 'Abre las recetas de estaño.', { max: 9 });
COLOCABLE('mesa_hierro', 'Mesa de hierro', 'mesa_hierro', 'Abre las recetas de hierro.', { max: 9 });
COLOCABLE('horno', 'Horno', 'horno', 'Funde minerales en lingotes.', { max: 9 });
COLOCABLE('yunque', 'Yunque de cobre', 'yunque', 'Forja armas y armaduras de cobre.', { max: 9 });
COLOCABLE('yunque_estano', 'Yunque de estaño', 'yunque_estano', 'Forja lo de estaño.', { max: 9 });
COLOCABLE('yunque_hierro', 'Yunque de hierro', 'yunque_hierro', 'Forja lo de hierro.', { max: 9 });
COLOCABLE('olla', 'Olla', 'olla', 'Junta dos ingredientes en un plato con mejoras.', { max: 9 });
COLOCABLE('cofre', 'Cofre', 'cofre', 'Guarda 30 cosas. Las mesas crean con lo que hay en los cofres cercanos.', { max: 9 });
COLOCABLE('cama', 'Cama', 'cama', 'Duerme aquí: reapareces en tu cama.', { max: 9 });
COLOCABLE('puerta', 'Puerta de madera', 'puerta', 'Tú pasas; las criaturas no.', { max: 20 });
COLOCABLE('maceta', 'Maceta con flor', 'maceta', 'Decoración.', { max: 20 });

// especiales
item('cristal_topo', { nombre: 'Cristal del Topo', tipo: 'especial', rareza: 2, max: 1, desc: 'Ponlo en la estatua del Topo junto al Corazón.' });
item('cristal_reina', { nombre: 'Cristal de la Reina', tipo: 'especial', rareza: 2, max: 1, desc: 'Ponlo en la estatua de la Reina junto al Corazón.' });
item('cristal_ozmar', { nombre: 'Cristal de Ozmar', tipo: 'especial', rareza: 2, max: 1, desc: 'Ponlo en la estatua de Ozmar junto al Corazón.' });

// ---------------------------------------------------------------- recetas
// est: estación que tiene que estar cerca (null = a mano). Las mesas de nivel alto valen para lo de nivel bajo.
const NIVEL_MESA = { mesa: 1, mesa_cobre: 2, mesa_estano: 3, mesa_hierro: 4 };
const NOMBRE_ESTACION = { mesa: 'Mesa de trabajo', mesa_cobre: 'Mesa de cobre', mesa_estano: 'Mesa de estaño', mesa_hierro: 'Mesa de hierro', horno: 'Horno', yunque: 'Yunque de cobre', yunque_estano: 'Yunque de estaño', yunque_hierro: 'Yunque de hierro' };
const RECETAS = [];
function receta(item, n, ing, est = null) { RECETAS.push({ item, n, ing, est }); }
receta('mesa', 1, { madera: 8 });
receta('antorcha', 2, { madera: 1 });
receta('azada_madera', 1, { madera: 6 });
receta('espada_madera', 1, { madera: 7 }, 'mesa');
receta('antorcha', 6, { madera: 1, baba: 1 }, 'mesa');
receta('cofre', 1, { madera: 10 }, 'mesa');
receta('cana_madera', 1, { madera: 6, fibra: 3 }, 'mesa');
receta('arco_madera', 1, { madera: 6, fibra: 4 }, 'mesa');
receta('flecha', 10, { madera: 2, bloque_piedra: 1 }, 'mesa');
receta('pared_madera', 2, { madera: 2 }, 'mesa');
receta('suelo_madera', 4, { madera: 2 }, 'mesa');
receta('puerta', 1, { madera: 6 }, 'mesa');
receta('cama', 1, { madera: 10, fibra: 6 }, 'mesa');
receta('olla', 1, { mineral_cobre: 6, madera: 4 }, 'mesa');
receta('horno', 1, { mineral_cobre: 8, madera: 4 }, 'mesa');
receta('mesa_cobre', 1, { lingote_cobre: 6, madera: 6 }, 'mesa');
receta('maceta', 1, { bloque_arcilla: 2, semilla_baya: 1 }, 'mesa');
receta('pico_cobre', 1, { lingote_cobre: 6, madera: 2 }, 'mesa_cobre');
receta('azada_cobre', 1, { lingote_cobre: 5, madera: 2 }, 'mesa_cobre');
receta('regadera', 1, { lingote_cobre: 4 }, 'mesa_cobre');
receta('yunque', 1, { lingote_cobre: 8 }, 'mesa_cobre');
receta('cana_cobre', 1, { lingote_cobre: 4, fibra: 4 }, 'mesa_cobre');
receta('flecha_cobre', 10, { madera: 2, lingote_cobre: 1 }, 'mesa_cobre');
receta('suelo_losa', 4, { bloque_piedra: 2 }, 'mesa_cobre');
receta('mesa_estano', 1, { lingote_estano: 8, lingote_cobre: 4 }, 'mesa_cobre');
receta('pico_estano', 1, { lingote_estano: 8, lingote_cobre: 2 }, 'mesa_estano');
receta('arco_estano', 1, { lingote_estano: 6, fibra: 6 }, 'mesa_estano');
receta('yunque_estano', 1, { lingote_estano: 10 }, 'mesa_estano');
receta('lampara', 2, { cristal: 1, lingote_estano: 1 }, 'mesa_estano');
receta('farol_oro', 1, { lingote_oro: 2, cristal: 1 }, 'mesa_estano');
receta('mesa_hierro', 1, { lingote_hierro: 10, lingote_estano: 4 }, 'mesa_estano');
receta('pico_hierro', 1, { lingote_hierro: 10, lingote_estano: 2 }, 'mesa_hierro');
receta('yunque_hierro', 1, { lingote_hierro: 12 }, 'mesa_hierro');
receta('alfombra', 4, { fibra: 6, baba_roja: 1 }, 'mesa_hierro');
for (const m of ['cobre', 'estano', 'hierro', 'oro']) receta('lingote_' + m, 1, { ['mineral_' + m]: 2 }, 'horno');
receta('espada_cobre', 1, { lingote_cobre: 7, madera: 2 }, 'yunque');
receta('maza_cobre', 1, { lingote_cobre: 9, madera: 3 }, 'yunque');
receta('casco_cobre', 1, { lingote_cobre: 6 }, 'yunque');
receta('peto_cobre', 1, { lingote_cobre: 10 }, 'yunque');
receta('botas_cobre', 1, { lingote_cobre: 5 }, 'yunque');
receta('anillo_cobre', 1, { lingote_cobre: 4, cristal: 1 }, 'yunque');
receta('espada_estano', 1, { lingote_estano: 9, lingote_cobre: 2 }, 'yunque_estano');
receta('casco_estano', 1, { lingote_estano: 8 }, 'yunque_estano');
receta('peto_estano', 1, { lingote_estano: 12 }, 'yunque_estano');
receta('botas_estano', 1, { lingote_estano: 6 }, 'yunque_estano');
receta('casco_larva', 1, { quitina: 10, fibra: 4 }, 'yunque_estano');
receta('peto_larva', 1, { quitina: 14, fibra: 6 }, 'yunque_estano');
receta('botas_larva', 1, { quitina: 8, fibra: 4 }, 'yunque_estano');
receta('collar_cristal', 1, { cristal: 3, lingote_oro: 2 }, 'yunque_estano');
receta('anillo_oro', 1, { lingote_oro: 4, gema_antigua: 1 }, 'yunque_estano');
receta('espada_hierro', 1, { lingote_hierro: 11, lingote_estano: 2 }, 'yunque_hierro');
receta('maza_hierro', 1, { lingote_hierro: 14, madera: 4 }, 'yunque_hierro');
receta('casco_hierro', 1, { lingote_hierro: 10 }, 'yunque_hierro');
receta('peto_hierro', 1, { lingote_hierro: 15 }, 'yunque_hierro');
receta('botas_hierro', 1, { lingote_hierro: 8 }, 'yunque_hierro');

// ---------------------------------------------------------------- objetos puestos en el mundo
// bloquea: no se pasa. luz: [radio, r, g, b]. vida: golpes. suelta: lo que da al romperse. estacion: para crear.
// interactua: abre algo al tocar «Usar». nivel: poder de minado necesario.
const OBJ = {
  antorcha: { arte: 'antorcha', brillo: true, luz: [7, 1.0, 0.78, 0.52], fuego: true, vida: 1, suelta: [['antorcha', 1, 1]], mapa: '#ffd060' },
  lampara: { arte: 'lampara', brillo: true, luz: [9, 0.75, 0.95, 1.0], vida: 3, suelta: [['lampara', 1, 1]], mapa: '#b8fbff' },
  farol: { arte: 'farol', brillo: true, luz: [11, 1.0, 0.86, 0.55], vida: 3, suelta: [['farol_oro', 1, 1]], mapa: '#ffe090' },
  mesa: { arte: 'mesa', bloquea: true, vida: 3, suelta: [['mesa', 1, 1]], estacion: 'mesa', interactua: 'crear', mapa: '#c28a52' },
  mesa_cobre: { arte: 'mesa_cobre', bloquea: true, vida: 3, suelta: [['mesa_cobre', 1, 1]], estacion: 'mesa_cobre', interactua: 'crear', mapa: '#e08a50' },
  mesa_estano: { arte: 'mesa_estano', bloquea: true, vida: 3, suelta: [['mesa_estano', 1, 1]], estacion: 'mesa_estano', interactua: 'crear', mapa: '#c8c4e4' },
  mesa_hierro: { arte: 'mesa_hierro', bloquea: true, vida: 3, suelta: [['mesa_hierro', 1, 1]], estacion: 'mesa_hierro', interactua: 'crear', mapa: '#8cb2d4' },
  horno: { arte: 'horno', brillo: true, bloquea: true, luz: [4, 1.0, 0.55, 0.3], fuego: true, vida: 3, suelta: [['horno', 1, 1]], estacion: 'horno', interactua: 'crear', mapa: '#ff9a3c' },
  yunque: { arte: 'yunque', bloquea: true, vida: 3, suelta: [['yunque', 1, 1]], estacion: 'yunque', interactua: 'crear', mapa: '#9aa6b8' },
  yunque_estano: { arte: 'yunque_estano', bloquea: true, vida: 3, suelta: [['yunque_estano', 1, 1]], estacion: 'yunque_estano', interactua: 'crear', mapa: '#c8c4e4' },
  yunque_hierro: { arte: 'yunque_hierro', bloquea: true, vida: 3, suelta: [['yunque_hierro', 1, 1]], estacion: 'yunque_hierro', interactua: 'crear', mapa: '#8cb2d4' },
  olla: { arte: 'olla', bloquea: true, brillo: true, luz: [3, 1.0, 0.6, 0.35], vida: 3, suelta: [['olla', 1, 1]], interactua: 'olla', mapa: '#7a5a4a' },
  cofre: { arte: 'cofre', bloquea: true, vida: 3, suelta: [['cofre', 1, 1]], interactua: 'cofre', mapa: '#c89048' },
  cofre_ruinas: { arte: 'cofre_ruinas', bloquea: true, vida: 0, interactua: 'cofre', mapa: '#e0b050' },
  cama: { arte: 'cama', bloquea: true, vida: 3, suelta: [['cama', 1, 1]], interactua: 'cama', mapa: '#c84858' },
  puerta: { arte: 'puerta', bloqueaCriaturas: true, vida: 3, suelta: [['puerta', 1, 1]], mapa: '#8a5a36' },
  maceta: { arte: 'maceta', bloquea: true, vida: 1, suelta: [['maceta', 1, 1]], mapa: '#d07a5a' },
  cultivo: { arte: 'cultivo', vida: 1, planta: true, mapa: '#5aa06a' },
  raiz: { arte: 'raiz', bloquea: true, vida: 24, suelta: [['madera', 2, 4]], planta: true, mapa: '#8a5a36' },
  arbusto: { arte: 'arbusto', brillo: true, luz: [3, 0.35, 0.7, 1.0], vida: 8, suelta: [['baya', 1, 3], ['semilla_baya', 1, 1, 0.5]], planta: true, mapa: '#3f7a5a' },
  hongo: { arte: 'hongo', brillo: true, luz: [3.5, 0.3, 0.85, 0.95], vida: 8, suelta: [['hongo', 1, 2], ['espora_hongo', 1, 1, 0.5]], planta: true, mapa: '#3fd8e8' },
  planta_fibra: { arte: 'planta_fibra', vida: 6, suelta: [['fibra', 1, 3], ['semilla_trigo', 1, 1, 0.3]], planta: true, mapa: '#8aa04a' },
  bulbo_silvestre: { arte: 'bulbo_silvestre', vida: 6, suelta: [['bulbo', 1, 2], ['semilla_bulbo', 1, 1, 0.6]], planta: true, mapa: '#c87a5a' },
  cristal: { arte: 'cristal', brillo: true, bloquea: true, luz: [4.5, 0.35, 0.9, 1.0], vida: 70, nivel: 20, suelta: [['cristal', 2, 3]], mapa: '#6ff0ff' },
  vasija: { arte: 'vasija', bloquea: true, vida: 1, suelta: [['madera', 1, 2, 0.3], ['flecha', 3, 6, 0.4], ['lingote_cobre', 1, 2, 0.25], ['gema_antigua', 1, 1, 0.05], ['baya', 1, 2, 0.3]], rompible: true, mapa: '#a87a5a' },
  huevo_larva: { arte: 'huevo_larva', brillo: true, luz: [2.5, 0.5, 0.95, 0.3], bloquea: true, vida: 10, suelta: [['quitina', 1, 2], ['baba', 1, 1, 0.5]], rompible: true, mapa: '#e0d090' },
  columna: { arte: 'columna', bloquea: true, vida: 0, mapa: '#6a7888' },
  trono: { arte: 'trono', bloquea: true, vida: 0, mapa: '#8a2a4a' },
  corazon: { arte: 'corazon', bloquea: true, vida: 0, interactua: 'corazon', mapa: '#ff6b8f' },
  estatua: { arte: 'estatua', bloquea: true, vida: 0, interactua: 'estatua', mapa: '#6b919b' },
};

// ---------------------------------------------------------------- criaturas
// conducta: salta, repta, embiste, lancea, chaman, bruto, seton. region: dónde aparecen (y su peso).
const CRIATURAS = {
  babosa: { nombre: 'Babosa', vida: 26, dano: 9, vel: 34, radio: 5, conducta: 'salta', regiones: { 1: 5 }, botin: [['baba', 1, 2, 0.8], ['baya', 1, 1, 0.1]], xp: 4, colores: ['#86d64f', '#56a83b', '#effed8'] },
  babosa_roja: { nombre: 'Babosa roja', vida: 60, dano: 15, vel: 40, radio: 6, conducta: 'salta', regiones: { 1: 1, 2: 2 }, botin: [['baba_roja', 1, 2, 0.7], ['baba', 1, 2, 0.5]], xp: 9, colores: ['#e8504a', '#a82a3a', '#ffd0c0'], divide: 'babosa' },
  seton: { nombre: 'Setón', vida: 70, dano: 12, vel: 22, radio: 6, conducta: 'seton', regiones: { 1: 2 }, botin: [['carne_seton', 1, 1, 0.7], ['espora_hongo', 1, 2, 0.6]], xp: 8, colores: ['#c86a3a', '#e8c8a0', '#8a4a2a'] },
  larva: { nombre: 'Larva', vida: 44, dano: 13, vel: 30, radio: 6, conducta: 'repta', regiones: { 2: 5 }, botin: [['carne_larva', 1, 1, 0.65], ['quitina', 1, 1, 0.4]], xp: 7, colores: ['#efe2bf', '#c9a878', '#a5513a'] },
  larva_grande: { nombre: 'Larva grande', vida: 140, dano: 20, vel: 24, radio: 9, conducta: 'escupe', regiones: { 2: 1 }, botin: [['quitina', 2, 4, 0.9], ['carne_larva', 1, 2, 0.7]], xp: 18, colores: ['#efe2bf', '#c9a878', '#7ac83a'] },
  gruto_lancero: { nombre: 'Gruto lancero', vida: 80, dano: 18, vel: 38, radio: 6, conducta: 'lancea', regiones: { 2: 2, 3: 3 }, botin: [['hueso', 1, 2, 0.5], ['flecha', 2, 5, 0.3], ['lingote_estano', 1, 1, 0.08]], xp: 14, colores: ['#6a8a5a', '#a8c088', '#3a4a2a'] },
  gruto_chaman: { nombre: 'Gruto chamán', vida: 70, dano: 16, vel: 30, radio: 6, conducta: 'chaman', regiones: { 3: 2 }, botin: [['hueso', 1, 1, 0.5], ['cristal', 1, 1, 0.25], ['gema_antigua', 1, 1, 0.04]], xp: 18, colores: ['#7a5a9a', '#c0a0e8', '#3a2a4a'] },
  gruto_bruto: { nombre: 'Gruto bruto', vida: 220, dano: 30, def: 4, vel: 26, radio: 9, conducta: 'bruto', regiones: { 3: 1 }, botin: [['hueso', 2, 3, 0.8], ['lingote_hierro', 1, 2, 0.3]], xp: 30, colores: ['#5a6a4a', '#8a9a6a', '#2a3a1a'] },
};

// Los tres guardianes del anillo interior
const JEFES = {
  topo: { nombre: 'TOPO REY', vida: 1100, dano: 18, radio: 12, region: REGION.RAICES, cristal: 'cristal_topo', botin: [['pico_topo', 1, 1], ['amuleto_topo', 1, 1, 0.5], ['lingote_cobre', 6, 10], ['semilla_bulbo', 2, 4]] },
  reina: { nombre: 'REINA LARVA', vida: 1700, dano: 22, radio: 16, region: REGION.ARCILLA, cristal: 'cristal_reina', botin: [['garra_reina', 1, 1, 0.6], ['broche_larva', 1, 1, 0.5], ['quitina', 10, 16], ['lingote_estano', 6, 10]] },
  ozmar: { nombre: 'OZMAR EL CORROMPIDO', vida: 2100, dano: 26, radio: 10, region: REGION.RUINAS, cristal: 'cristal_ozmar', botin: [['baculo_ozmar', 1, 1, 0.5], ['corona_ozmar', 1, 1, 0.35], ['gema_antigua', 2, 4], ['lingote_hierro', 8, 12]] },
};

// ---------------------------------------------------------------- cultivos, peces y cocina

// etapas: segundos por etapa (3 etapas). Regado: el doble de rápido.
const CULTIVOS = {
  baya: { nombre: 'Baya de luz', item: 'baya', semilla: 'semilla_baya', etapa: 50, brilla: true },
  hongo: { nombre: 'Hongo azul', item: 'hongo', semilla: 'espora_hongo', etapa: 40, brilla: true },
  bulbo: { nombre: 'Bulbo', item: 'bulbo', semilla: 'semilla_bulbo', etapa: 70 },
  trigo: { nombre: 'Trigo', item: 'trigo', semilla: 'semilla_trigo', etapa: 55 },
};
// peces por región: [item, peso]
const PECES = { 0: [['pez_guppy', 1]], 1: [['pez_guppy', 6], ['pez_dorado', 0.3]], 2: [['pez_barro', 6], ['pez_guppy', 1], ['pez_dorado', 0.3]], 3: [['pez_cristal', 5], ['pez_barro', 1], ['pez_dorado', 0.4]] };
// Tipo de plato según el ingrediente principal, y prefijo según el segundo.
const PLATO_TIPO = { baya: 'Mermelada', hongo: 'Guiso', bulbo: 'Puré', trigo: 'Pan', carne_larva: 'Brocheta', carne_seton: 'Asado', pez_guppy: 'Sopa', pez_barro: 'Caldo', pez_cristal: 'Sashimi', pez_dorado: 'Banquete' };
const PLATO_PREFIJO = { baya: 'brillante', hongo: 'del minero', bulbo: 'abundante', trigo: 'reconfortante', carne_larva: 'picante', carne_seton: 'robusto', pez_guppy: 'ligero', pez_barro: 'blindado', pez_cristal: 'luminoso', pez_dorado: 'dorado' };
const NOMBRE_EFECTO = { vida: 'Cura', vidaMax: 'Vida máx.', regen: 'Regeneración', minado: 'Minado', dano: 'Daño', vel: 'Velocidad', defensa: 'Defensa', luz: 'Luz', suerte: 'Suerte' };

// ---------------------------------------------------------------- habilidades

const HABILIDADES = {
  mineria: { nombre: 'Minería', desc: '+1 % de daño a paredes por nivel.' },
  combate: { nombre: 'Cuerpo a cuerpo', desc: '+1 % de daño cuerpo a cuerpo por nivel.' },
  distancia: { nombre: 'A distancia', desc: '+1 % de daño a distancia por nivel.' },
  vitalidad: { nombre: 'Vitalidad', desc: '+1 de vida máxima por nivel.' },
  carrera: { nombre: 'Carrera', desc: '+0,5 % de velocidad por nivel.' },
  artesania: { nombre: 'Artesanía', desc: '+1 % de crear el doble por nivel.' },
  jardineria: { nombre: 'Jardinería', desc: '+1 % de cosecha doble por nivel.' },
  pesca: { nombre: 'Pesca', desc: 'Los peces luchan menos por nivel.' },
  cocina: { nombre: 'Cocina', desc: '+2 % de fuerza de los platos por nivel.' },
};
const NIVEL_MAX = 50;
const xpNivel = (n) => 30 + n * 14;

// Lo que llevas al empezar
const INICIO = [['pico_viejo', 1], ['antorcha', 8], ['baya', 4]];

// Objetivos (guía arriba a la derecha). hecho(J) dice si ya está.
const OBJETIVOS = [
  { txt: 'Rompe raíces gruesas: 8 de madera', hecho: (J) => J.cuenta('madera') >= 8 || J.hito.mesa },
  { txt: 'Crea una mesa de trabajo y ponla', hecho: (J) => J.hito.mesa },
  { txt: 'Pica cobre: 8 minerales', hecho: (J) => J.cuenta('mineral_cobre') >= 8 || J.hito.horno },
  { txt: 'Crea un horno y funde lingotes', hecho: (J) => J.hito.horno && J.cuenta('lingote_cobre') >= 1 || J.hito.mesa_cobre },
  { txt: 'Haz una mesa de cobre y un pico de cobre', hecho: (J) => J.hito.pico_cobre },
  { txt: 'Forja una espada y armadura de cobre', hecho: (J) => J.hito.espada_cobre },
  { txt: 'Derrota al Topo Rey', hecho: (J) => J.jefesMuertos.topo },
  { txt: 'Pon el cristal en su estatua', hecho: (J) => J.estatuas.topo },
  { txt: 'Busca estaño en la arcilla y mejora tu equipo', hecho: (J) => J.hito.pico_estano },
  { txt: 'Derrota a la Reina Larva en su colmena', hecho: (J) => J.jefesMuertos.reina },
  { txt: 'Pon su cristal en la estatua', hecho: (J) => J.estatuas.reina },
  { txt: 'Encuentra la sala del trono de Ozmar', hecho: (J) => J.jefesMuertos.ozmar || J.hito.vioOzmar },
  { txt: 'Derrota a Ozmar el Corrompido', hecho: (J) => J.jefesMuertos.ozmar },
  { txt: 'Despierta el Corazón', hecho: (J) => J.estatuas.ozmar },
];
