'use strict';
// Datos de la fase 2: la Selva Esmeralda, al otro lado de la Gran Muralla. Metal carmesí, madera de selva,
// criaturas nuevas, dos titanes (Tamar y Hiedra Viva), mercaderes con casa, mascotas, bombas y la piedra de
// regreso. Se añade a las tablas de datos.js.

// ---------------------------------------------------------------- suelos, paredes y menas

Object.assign(SUELO, { HIERBA: 15, MUSGO: 16, TIERRA_SELVA: 17, FLORES: 18, MOHO: 19, TEMPLO: 20, VENENO: 21, TABLAS: 22, ALFOMBRA_VERDE: 23 });
Object.assign(INFO_SUELO, {
  15: { arte: 'hierba', mapa: '#3a6a2a', decor: ['dc_hierba_alta_0', 'dc_hierba_alta_1', 'dc_hierba_alta_0', 'dc_flor_selva_0', 'dc_flor_selva_1', 'dc_helechito', 'guijarro'], densidad: 0.3, planos: ['ad_hojas', 'ad_hierba'], piedra: 1, arable: true },
  16: { arte: 'musgo', mapa: '#2e5a3a', decor: ['dc_helechito', 'dc_seta_1', 'guijarro', 'dc_hierba_alta_1'], densidad: 0.14, planos: ['ad_musgo', 'ad_hojas'], piedra: 3, arable: true },
  17: { arte: 'tierra_selva', mapa: '#3a2c1c', decor: ['guijarro', 'dc_hierba_alta_0', 'dc_seta_0', 'dc_helechito'], densidad: 0.12, planos: ['ad_hojas', 'ad_raices'], piedra: 1, arable: true },
  18: { arte: 'flores', mapa: '#5a7a3a', decor: ['dc_flor_selva_0', 'dc_flor_selva_1', 'dc_flor_selva_2', 'dc_flor_0', 'dc_flor_1', 'dc_hierba_alta_0'], densidad: 0.42, piedra: 1, arable: true },
  19: { arte: 'moho', mapa: '#1e3a3a', decor: ['dc_seta_moho', 'dc_seta_moho', 'guijarro'], densidad: 0.09, planos: ['ad_moho'], piedra: 3, brilla: 0.06 },
  20: { arte: 'templo', mapa: '#6a6a58', decor: ['guijarro', 'dc_hierba_alta_0'], densidad: 0.04, planos: ['ad_musgo'], piedra: 4 },
  21: { arte: 'veneno', mapa: '#3a2a4a', decor: ['dc_seta_moho', 'dc_helechito'], densidad: 0.1, planos: ['ad_moho'], piedra: 3, brilla: 0.04 },
  22: { arte: 'tablas', mapa: '#8a6a3a' },
  23: { arte: 'alfombra_verde', mapa: '#2a5a3a' },
});
Object.assign(MURO, { HIERBA: 10, ESPINO: 11, MUSGOSA: 12, MOHO: 13, SELLO: 14, SELVA_MADERA: 15 });
Object.assign(INFO_MURO, {
  10: { nombre: 'Bloque de hierba', arte: 'hierba', vida: 160, poder: 55, bloque: 'bloque_hierba', mapa: '#4a8a3a' },
  11: { nombre: 'Madera de espino', arte: 'espino', vida: 200, poder: 55, bloque: 'bloque_espino', mapa: '#6a4a2a' },
  12: { nombre: 'Piedra musgosa', arte: 'musgosa', vida: 260, poder: 60, bloque: 'bloque_musgosa', mapa: '#4a6a5a' },
  13: { nombre: 'Muro de moho', arte: 'moho', vida: 1, poder: 999, mapa: '#2a4a4a', brilla: 0.05 },
  14: { nombre: 'Roca dormida', arte: 'sello', vida: 1, poder: 999, alto: 1.3, tapa: 0.5, mapa: '#1e1a2a' },
  15: { nombre: 'Pared de selva', arte: 'selva_madera', vida: 60, poder: 0, bloque: 'pared_selva', mapa: '#7a5a2a' },
});
MENA.CARMESI = 5;
INFO_MENA[5] = { arte: 'carmesi', item: 'mineral_carmesi', poder: 55, extra: 1.7, mapa: '#ff3a4a' };

// ---------------------------------------------------------------- objetos del inventario

// materiales
item('madera_selva', { nombre: 'Madera de selva', tipo: 'material', desc: 'De los árboles de la Selva. Dura y verde.' });
item('mineral_carmesi', { nombre: 'Mineral carmesí', tipo: 'material', rareza: 1, desc: 'Rojo vivo, en la hierba de la Selva. Necesita un pico de hierro.' });
item('lingote_carmesi', { nombre: 'Lingote carmesí', tipo: 'material', rareza: 1, desc: 'El metal de la Selva.' });
item('pluma_antigua', { nombre: 'Pluma antigua', tipo: 'material', rareza: 1, desc: 'Cayó del cielo hace mucho. Tamar las echa de menos.' });
item('hoja_grande', { nombre: 'Hoja gigante', tipo: 'material', desc: 'De los helechos. Para tejer y techar.' });
item('savia', { nombre: 'Savia', tipo: 'material', desc: 'Pegajosa y dulce. Une lo que sea.' });
item('caparazon_musgo', { nombre: 'Caparazón musgoso', tipo: 'material', desc: 'Del escarabajo de musgo. Para armaduras ligeras.' });
item('aguijon', { nombre: 'Aguijón', tipo: 'material', desc: 'De las avispas esmeralda. Para flechas que envenenan.' });
item('polvo_explosivo', { nombre: 'Polvo explosivo', tipo: 'material', desc: 'Del hongo explosivo. No lo acerques al fuego.' });
item('espora_moho', { nombre: 'Espora de moho', tipo: 'material', desc: 'Brilla un poco. Del moho de la mazmorra.' });
item('baba_veneno', { nombre: 'Baba venenosa', tipo: 'material', desc: 'Morada y burbujeante.' });
item('pluma_tamar', { nombre: 'Pluma de Tamar', tipo: 'material', rareza: 3, desc: 'Tibia y ligera como el aire.' });
item('moneda', { nombre: 'Moneda antigua', tipo: 'material', max: 9999, rareza: 1, desc: 'Lo que aceptan los mercaderes.' });
// bloques y suelos
item('bloque_hierba', { nombre: 'Bloque de hierba', tipo: 'bloque', muro: MURO.HIERBA, desc: 'Ponlo para levantar una pared.' });
item('bloque_espino', { nombre: 'Bloque de espino', tipo: 'bloque', muro: MURO.ESPINO, desc: 'Madera con pinchos. También sirve para arcos.' });
item('bloque_musgosa', { nombre: 'Piedra musgosa', tipo: 'bloque', muro: MURO.MUSGOSA, desc: 'Ponla para levantar una pared que aguanta mucho.' });
item('pared_selva', { nombre: 'Pared de selva', tipo: 'bloque', muro: MURO.SELVA_MADERA, desc: 'Madera verde para tu casa.' });
item('suelo_tablas', { nombre: 'Suelo de tablas', tipo: 'suelo', suelo: SUELO.TABLAS, desc: 'Tablas de selva.' });
item('suelo_musgo', { nombre: 'Suelo de musgo', tipo: 'suelo', suelo: SUELO.MUSGO, desc: 'Blando y verde.' });
item('suelo_templo', { nombre: 'Losas del templo', tipo: 'suelo', suelo: SUELO.TEMPLO, rareza: 1, desc: 'Piedra clara labrada.' });
item('alfombra_verde', { nombre: 'Alfombra verde', tipo: 'suelo', suelo: SUELO.ALFOMBRA_VERDE, rareza: 1, desc: 'Tejida con hojas gigantes.' });

// herramientas y armas
item('pico_carmesi', { nombre: 'Pico carmesí', tipo: 'pico', poder: 80, dano: 22, vel: 0.23, max: 1, rareza: 1, desc: 'Rompe todo lo de la Selva en pocos golpes.' });
item('espada_carmesi', { nombre: 'Espada carmesí', tipo: 'espada', dano: 52, vel: 0.29, max: 1, rareza: 1, desc: 'Fina, roja y rapidísima.' });
item('maza_carmesi', { nombre: 'Maza carmesí', tipo: 'maza', dano: 84, vel: 0.58, max: 1, rareza: 1, desc: 'Lenta, aturde y empuja muy lejos.' });
item('lanza_espino', { nombre: 'Lanza de espino', tipo: 'espada', dano: 40, vel: 0.4, alcance: 40, max: 1, desc: 'Llega más lejos que una espada.' });
item('arco_carmesi', { nombre: 'Arco carmesí', tipo: 'arco', dano: 28, vel: 0.36, max: 1, rareza: 1, desc: 'Tensado con espino. Muy rápido.' });
item('arco_tamar', { nombre: 'Arco de Tamar', tipo: 'arco', dano: 34, vel: 0.42, multi: 3, max: 1, rareza: 3, desc: 'Dispara tres flechas en abanico y solo gasta una.' });
item('latigo_hiedra', { nombre: 'Látigo de Hiedra', tipo: 'espada', dano: 46, vel: 0.34, alcance: 42, veneno: 8, max: 1, rareza: 2, desc: 'Largo y venenoso.' });
item('flecha_carmesi', { nombre: 'Flecha carmesí', tipo: 'municion', dano: 15, desc: 'Punta carmesí.' });
item('flecha_aguijon', { nombre: 'Flecha de aguijón', tipo: 'municion', dano: 11, veneno: 5, desc: 'Envenena lo que toca.' });
item('bomba', { nombre: 'Bomba', tipo: 'bomba', dano: 70, max: 30, desc: 'Lánzala: estalla al rato, rompe paredes cercanas y hiere a todo lo que pille.' });
item('cana_carmesi', { nombre: 'Caña carmesí', tipo: 'cana', fuerza: 2.2, max: 1, rareza: 1, desc: 'Para los peces grandes de la Selva.' });
item('regadera_grande', { nombre: 'Regadera grande', tipo: 'regadera', area: 1, max: 1, rareza: 1, desc: 'Riega nueve casillas de una vez.' });
item('azada_carmesi', { nombre: 'Azada carmesí', tipo: 'azada', area: 2, max: 1, rareza: 1, desc: 'Ara cinco casillas de una vez.' });
item('piedra_regreso', { nombre: 'Piedra de regreso', tipo: 'regreso', max: 20, rareza: 1, desc: 'Apriétala: vuelves a tu cama (o al Corazón).' });
item('cebo', { nombre: 'Cebo', tipo: 'material', desc: 'Si lo llevas, los peces pican antes. Se gasta uno por pez.' });

// armaduras y accesorios
for (const [m, n, d, r, extra] of [['carmesi', 'carmesí', 4, 1, 0], ['musgo', 'musgo', 2.5, 1, 0.1]]) {
  item('casco_' + m, { nombre: `Casco de ${n}`, tipo: 'casco', def: Math.round(d * 2), max: 1, rareza: r, desc: `Defensa ${Math.round(d * 2)}.` });
  item('peto_' + m, { nombre: `Peto de ${n}`, tipo: 'peto', def: Math.round(d * 3), max: 1, rareza: r, desc: `Defensa ${Math.round(d * 3)}.` });
  item('botas_' + m, { nombre: `Botas de ${n}`, tipo: 'botas', def: Math.round(d), vel: extra, max: 1, rareza: r, desc: extra ? `Defensa ${Math.round(d)} y +${extra * 100} % de velocidad.` : `Defensa ${Math.round(d)}.` });
}
item('anillo_carmesi', { nombre: 'Anillo carmesí', tipo: 'accesorio', dano: 0.1, max: 1, rareza: 1, desc: '+10 % de daño.' });
item('collar_moho', { nombre: 'Collar de moho', tipo: 'accesorio', vidaMax: 25, luz: 1, max: 1, rareza: 2, desc: '+25 de vida máxima y un poco de luz.' });
item('anzuelo_oro', { nombre: 'Anzuelo de oro', tipo: 'accesorio', pesca: 0.4, suerte: 0.05, max: 1, rareza: 1, desc: 'Los peces luchan menos y sale algún tesoro más.' });
item('alas_tamar', { nombre: 'Plumas de Tamar', tipo: 'accesorio', vel: 0.15, critico: 0.06, max: 1, rareza: 3, desc: '+15 % de velocidad y +6 % de crítico.' });
item('semilla_hiedra', { nombre: 'Corazón de Hiedra', tipo: 'accesorio', regen: 1, max: 1, rareza: 2, desc: 'Regeneras 1 de vida por segundo.' });

// comida, semillas y peces
item('fruta_selva', { nombre: 'Fruta de selva', tipo: 'comida', cura: 10, hambre: 18, ingrediente: { vel: 0.08 }, desc: 'Jugosa. Ingrediente.' });
item('avena', { nombre: 'Avena hinchada', tipo: 'comida', cura: 4, hambre: 30, ingrediente: { vidaMax: 15 }, desc: 'Llena mucho. Ingrediente.' });
item('melon_luna', { nombre: 'Melón de luna', tipo: 'comida', cura: 12, hambre: 20, rareza: 1, ingrediente: { luz: 2, regen: 0.6 }, desc: 'Brilla por dentro. Ingrediente.' });
item('miel', { nombre: 'Miel', tipo: 'comida', cura: 14, hambre: 10, rareza: 1, ingrediente: { regen: 1 }, desc: 'De los panales de avispa. Ingrediente.' });
item('carne_escarabajo', { nombre: 'Carne de escarabajo', tipo: 'comida', cura: 6, hambre: 24, ingrediente: { defensa: 6 }, desc: 'Dura como su caparazón. Ingrediente.' });
for (const [p, n, ing, r] of [['hoja', 'Pez hoja', { vel: 0.1 }, 0], ['espino', 'Pez espino', { dano: 0.12 }, 0], ['anguila', 'Anguila de luz', { luz: 2.5, critico: 0.05 }, 1], ['sol', 'Pez sol', { vidaMax: 30, regen: 1 }, 3]]) {
  item('pez_' + p, { nombre: n, tipo: 'comida', cura: 8, hambre: 24, ingrediente: ing, rareza: r, desc: 'Pescado de la Selva. Ingrediente.' });
}
item('semilla_avena', { nombre: 'Semilla de avena', tipo: 'semilla', cultivo: 'avena', desc: 'Plántala en tierra arada.' });
item('semilla_melon', { nombre: 'Semilla de melón de luna', tipo: 'semilla', cultivo: 'melon', rareza: 1, desc: 'Plántala en tierra arada.' });

// mascotas: el huevo se incuba y sale la mascota, que se pone en su hueco del equipo
for (const [h, n, mas, mn, r, desc] of [
  ['leal', 'Huevo leal', 'topito', 'Topito', 1, 'Muerde y a veces aturde.'],
  ['baboso', 'Huevo baboso', 'babosita', 'Babosita', 1, 'Salta sobre tus enemigos y los frena.'],
  ['luz', 'Huevo de luz', 'luci', 'Luci', 2, 'No pelea: te alumbra y te cura poco a poco.'],
  ['moho', 'Huevo de moho', 'mohito', 'Mohito', 2, 'Escupe esporas venenosas.'],
]) {
  item('huevo_' + h, { nombre: n, tipo: 'material', rareza: r, max: 9, mascota: 'mascota_' + mas, desc: 'Ponlo en una incubadora para que nazca.' });
  item('mascota_' + mas, { nombre: mn, tipo: 'mascota', rareza: r, max: 1, desc: desc + ' Ponla en el hueco de mascota del equipo.' });
}

// colocables
COLOCABLE('mesa_carmesi', 'Mesa carmesí', 'mesa_carmesi', 'Abre las recetas de la Selva.', { max: 9, rareza: 1 });
COLOCABLE('yunque_carmesi', 'Yunque carmesí', 'yunque_carmesi', 'Forja lo carmesí.', { max: 9, rareza: 1 });
COLOCABLE('farol_carmesi', 'Farol carmesí', 'farol_carmesi', 'Luz roja y cálida, enorme.', { rareza: 1 });
COLOCABLE('incubadora', 'Incubadora', 'incubadora', 'Pon un huevo dentro: en unos minutos nace tu mascota.', { max: 9 });
COLOCABLE('idolo_barbas', 'Ídolo de baba', 'idolo_barbas', 'Ponlo en una casa cerrada con cama: llega Barbas, el mercader.', { max: 1 });
COLOCABLE('idolo_pesca', 'Ídolo del cebo', 'idolo_pesca', 'Ponlo en una casa cerrada con cama: llega Marina, la pescadora.', { max: 1 });
COLOCABLE('idolo_misterioso', 'Ídolo misterioso', 'idolo_misterioso', 'Ponlo en una casa cerrada con cama: llega el Encapuchado.', { max: 1, rareza: 2 });
COLOCABLE('silla_selva', 'Silla de selva', 'silla_selva', 'Para tu casa.', { max: 20 });
COLOCABLE('mesa_selva', 'Mesa de comedor', 'mesa_selva', 'Para tu casa.', { max: 20 });
COLOCABLE('estanteria', 'Estantería', 'estanteria', 'Llena de tarros y libros.', { max: 20 });
COLOCABLE('lampara_pie', 'Lámpara de pie', 'lampara_pie', 'Luz suave para dentro de casa.', { max: 20 });
COLOCABLE('valla', 'Valla de selva', 'valla', 'Nadie pasa por encima.', { max: 50 });
COLOCABLE('banderin', 'Banderín', 'banderin', 'Decoración.', { max: 20 });
COLOCABLE('maceta_grande', 'Maceta grande', 'maceta_grande', 'Un helecho en una maceta.', { max: 20 });
COLOCABLE('cofre_selva', 'Cofre de selva', 'cofre_selva', 'Guarda 30 cosas, como el cofre de madera.', { max: 9 });

// especiales
item('ofrenda_cielo', { nombre: 'Ofrenda del cielo', tipo: 'especial', rareza: 2, max: 1, desc: 'Ponla en el altar del templo del cielo: Tamar baja.' });
item('gema_cielo', { nombre: 'Gema del Cielo', tipo: 'especial', rareza: 3, max: 1, desc: 'La gema de Tamar. Ponla en su altar junto al Corazón.' });
item('gema_hiedra', { nombre: 'Gema de la Hiedra', tipo: 'especial', rareza: 3, max: 1, desc: 'La gema de Hiedra Viva. Ponla en su altar junto al Corazón.' });

// ---------------------------------------------------------------- recetas

Object.assign(NIVEL_MESA, { mesa_carmesi: 5 });
Object.assign(NIVEL_YUNQUE, { yunque_carmesi: 4 });
Object.assign(NOMBRE_ESTACION, { mesa_carmesi: 'Mesa carmesí', yunque_carmesi: 'Yunque carmesí', incubadora: 'Incubadora' });
receta('lingote_carmesi', 1, { mineral_carmesi: 2 }, 'horno');
receta('mesa_carmesi', 1, { lingote_carmesi: 8, madera_selva: 8 }, 'mesa_hierro');
receta('pared_selva', 2, { madera_selva: 2 }, 'mesa');
receta('suelo_tablas', 4, { madera_selva: 2 }, 'mesa');
receta('silla_selva', 1, { madera_selva: 4 }, 'mesa');
receta('mesa_selva', 1, { madera_selva: 6 }, 'mesa');
receta('estanteria', 1, { madera_selva: 8, hoja_grande: 2 }, 'mesa');
receta('valla', 4, { madera_selva: 3 }, 'mesa');
receta('banderin', 1, { madera_selva: 2, hoja_grande: 3 }, 'mesa');
receta('maceta_grande', 1, { bloque_arcilla: 3, hoja_grande: 2 }, 'mesa');
receta('cofre_selva', 1, { madera_selva: 10, savia: 1 }, 'mesa');
receta('suelo_musgo', 4, { hoja_grande: 1, bloque_tierra: 2 }, 'mesa_cobre');
receta('alfombra_verde', 4, { hoja_grande: 4, fibra: 4 }, 'mesa_hierro');
receta('lampara_pie', 1, { madera_selva: 4, cristal: 1 }, 'mesa_estano');
receta('incubadora', 1, { lingote_hierro: 4, cristal: 2, hoja_grande: 4 }, 'mesa_hierro');
receta('idolo_barbas', 1, { baba: 10, madera: 6, lingote_oro: 1 }, 'mesa_hierro');
receta('idolo_pesca', 1, { pez_guppy: 3, pez_barro: 2, fibra: 6 }, 'mesa_hierro');
receta('bomba', 4, { polvo_explosivo: 2, lingote_cobre: 1 }, 'mesa_hierro');
receta('piedra_regreso', 2, { cristal: 1, hongo: 2 }, 'mesa_estano');
receta('cebo', 5, { baba: 1, carne_larva: 1 }, 'mesa');
receta('lanza_espino', 1, { bloque_espino: 6, lingote_hierro: 3 }, 'mesa_hierro');
receta('flecha_aguijon', 10, { madera_selva: 2, aguijon: 1 }, 'mesa_hierro');
receta('pico_carmesi', 1, { lingote_carmesi: 10, madera_selva: 3 }, 'mesa_carmesi');
receta('azada_carmesi', 1, { lingote_carmesi: 6, madera_selva: 2 }, 'mesa_carmesi');
receta('regadera_grande', 1, { lingote_carmesi: 6 }, 'mesa_carmesi');
receta('cana_carmesi', 1, { lingote_carmesi: 5, fibra: 6, savia: 1 }, 'mesa_carmesi');
receta('arco_carmesi', 1, { lingote_carmesi: 6, bloque_espino: 4, fibra: 6 }, 'mesa_carmesi');
receta('flecha_carmesi', 10, { madera_selva: 2, lingote_carmesi: 1 }, 'mesa_carmesi');
receta('yunque_carmesi', 1, { lingote_carmesi: 12 }, 'mesa_carmesi');
receta('farol_carmesi', 1, { lingote_carmesi: 2, cristal: 2 }, 'mesa_carmesi');
receta('ofrenda_cielo', 1, { pluma_antigua: 5, lingote_carmesi: 3, gema_antigua: 1 }, 'mesa_carmesi');
receta('espada_carmesi', 1, { lingote_carmesi: 12, lingote_hierro: 2 }, 'yunque_carmesi');
receta('maza_carmesi', 1, { lingote_carmesi: 15, madera_selva: 4 }, 'yunque_carmesi');
receta('casco_carmesi', 1, { lingote_carmesi: 10 }, 'yunque_carmesi');
receta('peto_carmesi', 1, { lingote_carmesi: 16 }, 'yunque_carmesi');
receta('botas_carmesi', 1, { lingote_carmesi: 8 }, 'yunque_carmesi');
receta('anillo_carmesi', 1, { lingote_carmesi: 5, gema_antigua: 1 }, 'yunque_carmesi');
receta('casco_musgo', 1, { caparazon_musgo: 6, fibra: 4 }, 'yunque_hierro');
receta('peto_musgo', 1, { caparazon_musgo: 10, fibra: 6 }, 'yunque_hierro');
receta('botas_musgo', 1, { caparazon_musgo: 5, fibra: 4 }, 'yunque_hierro');
receta('collar_moho', 1, { espora_moho: 12, lingote_oro: 3, cristal: 2 }, 'yunque_hierro');

// ---------------------------------------------------------------- objetos del mundo

Object.assign(OBJ, {
  arbol: { arte: 'arbol', bloquea: true, vida: 60, suelta: [['madera_selva', 3, 5], ['fruta_selva', 1, 2, 0.35], ['savia', 1, 1, 0.3]], planta: true, alto: true, mapa: '#2e6a2a' },
  arbol_luna: { arte: 'arbol_luna', brillo: true, luz: [6, 0.55, 0.8, 1.0], bloquea: true, vida: 60, suelta: [['madera_selva', 2, 4], ['semilla_melon', 1, 2, 0.6]], planta: true, alto: true, mapa: '#7ad0ff' },
  helecho: { arte: 'helecho', vida: 8, suelta: [['hoja_grande', 1, 2], ['fibra', 1, 2, 0.5]], planta: true, mapa: '#3a7a3a' },
  flor_sol: { arte: 'flor_sol', brillo: true, luz: [3, 1.0, 0.85, 0.4], vida: 6, suelta: [['savia', 1, 1], ['semilla_avena', 1, 1, 0.4]], planta: true, mapa: '#ffd040' },
  avena_silvestre: { arte: 'avena_silvestre', vida: 6, suelta: [['avena', 1, 2], ['semilla_avena', 1, 1, 0.6]], planta: true, mapa: '#d8c870' },
  hongo_explosivo: { arte: 'hongo_explosivo', vida: 4, suelta: [['polvo_explosivo', 1, 2]], planta: true, explota: true, mapa: '#e06a3a' },
  panal: { arte: 'panal', bloquea: true, vida: 30, suelta: [['miel', 1, 3], ['aguijon', 1, 2, 0.5]], rompible: true, nido: 'avispa', mapa: '#e0a040' },
  roca_cobre: { arte: 'roca_cobre', bloquea: true, vida: 160, nivel: 20, suelta: [['mineral_cobre', 5, 8]], mapa: '#e08a50' },
  roca_estano: { arte: 'roca_estano', bloquea: true, vida: 180, nivel: 20, suelta: [['mineral_estano', 5, 8]], mapa: '#c8c4e4' },
  roca_hierro: { arte: 'roca_hierro', bloquea: true, vida: 220, nivel: 35, suelta: [['mineral_hierro', 5, 8]], mapa: '#8cb2d4' },
  roca_oro: { arte: 'roca_oro', bloquea: true, vida: 220, nivel: 20, suelta: [['mineral_oro', 4, 7]], mapa: '#ffd24a' },
  roca_carmesi: { arte: 'roca_carmesi', bloquea: true, vida: 300, nivel: 55, suelta: [['mineral_carmesi', 5, 9]], mapa: '#ff3a4a' },
  caja_madera: { arte: 'caja_madera', bloquea: true, vida: 1, suelta: [['madera_selva', 1, 3, 0.5], ['moneda', 3, 9, 0.6], ['cebo', 2, 4, 0.3], ['bomba', 1, 2, 0.15], ['pluma_antigua', 1, 1, 0.08], ['huevo_baboso', 1, 1, 0.02]], rompible: true, mapa: '#a87a4a' },
  vasija_flor: { arte: 'vasija_flor', bloquea: true, vida: 1, suelta: [['moneda', 4, 12, 0.7], ['semilla_melon', 1, 1, 0.2], ['pluma_antigua', 1, 1, 0.12], ['piedra_regreso', 1, 1, 0.1], ['gema_antigua', 1, 1, 0.06]], rompible: true, mapa: '#7aa05a' },
  cofre_moho: { arte: 'cofre_moho', bloquea: true, vida: 0, interactua: 'cofre', mapa: '#5ae0c0' },
  cofre_selva: { arte: 'cofre_selva', bloquea: true, vida: 3, suelta: [['cofre_selva', 1, 1]], interactua: 'cofre', mapa: '#9a7a3a' },
  altar_cielo: { arte: 'altar_cielo', bloquea: true, vida: 0, interactua: 'altar_cielo', mapa: '#e0e0ff' },
  altar_titan: { arte: 'altar_titan', bloquea: true, vida: 0, interactua: 'altar_titan', mapa: '#b0ffd0' },
  tablilla: { arte: 'tablilla', bloquea: true, vida: 0, interactua: 'tablilla', mapa: '#c8c0a0' },
  tienda: { arte: 'tienda', bloquea: true, vida: 0, mapa: '#c87a4a' },
  hoguera: { arte: 'hoguera', brillo: true, luz: [6, 1.0, 0.65, 0.3], fuego: true, vida: 0, mapa: '#ff9a3c' },
  pozo: { arte: 'pozo', bloquea: true, vida: 0, mapa: '#8a9aa8' },
  columna_selva: { arte: 'columna_selva', bloquea: true, vida: 0, mapa: '#8a9a7a' },
  incubadora: { arte: 'incubadora', bloquea: true, vida: 3, suelta: [['incubadora', 1, 1]], interactua: 'incubadora', mapa: '#a0e0ff' },
  idolo_barbas: { arte: 'idolo_barbas', bloquea: true, vida: 3, suelta: [['idolo_barbas', 1, 1]], interactua: 'idolo', mercader: 'barbas', mapa: '#86d64f' },
  idolo_pesca: { arte: 'idolo_pesca', bloquea: true, vida: 3, suelta: [['idolo_pesca', 1, 1]], interactua: 'idolo', mercader: 'pescadora', mapa: '#6ab0ff' },
  idolo_misterioso: { arte: 'idolo_misterioso', bloquea: true, vida: 3, suelta: [['idolo_misterioso', 1, 1]], interactua: 'idolo', mercader: 'encapuchado', mapa: '#c07aff' },
  mesa_carmesi: { arte: 'mesa_carmesi', bloquea: true, vida: 3, suelta: [['mesa_carmesi', 1, 1]], estacion: 'mesa_carmesi', interactua: 'crear', mapa: '#ff5a6a' },
  yunque_carmesi: { arte: 'yunque_carmesi', bloquea: true, vida: 3, suelta: [['yunque_carmesi', 1, 1]], estacion: 'yunque_carmesi', interactua: 'crear', mapa: '#ff5a6a' },
  farol_carmesi: { arte: 'farol_carmesi', brillo: true, luz: [12, 1.0, 0.55, 0.5], vida: 3, suelta: [['farol_carmesi', 1, 1]], mapa: '#ff7a6a' },
  silla_selva: { arte: 'silla_selva', vida: 3, suelta: [['silla_selva', 1, 1]], mapa: '#9a6a3a' },
  mesa_selva: { arte: 'mesa_selva', bloquea: true, vida: 3, suelta: [['mesa_selva', 1, 1]], mapa: '#9a6a3a' },
  estanteria: { arte: 'estanteria', bloquea: true, vida: 3, suelta: [['estanteria', 1, 1]], mapa: '#9a6a3a' },
  lampara_pie: { arte: 'lampara_pie', brillo: true, luz: [7, 1.0, 0.86, 0.6], bloquea: true, vida: 3, suelta: [['lampara_pie', 1, 1]], mapa: '#ffe090' },
  valla: { arte: 'valla', bloquea: true, vida: 3, suelta: [['valla', 1, 1]], mapa: '#8a6a3a' },
  banderin: { arte: 'banderin', vida: 2, suelta: [['banderin', 1, 1]], mapa: '#c84858' },
  maceta_grande: { arte: 'maceta_grande', bloquea: true, vida: 2, suelta: [['maceta_grande', 1, 1]], mapa: '#3a7a3a' },
});

// ---------------------------------------------------------------- criaturas de la Selva
// fija: no se mueve. vuela: va por el aire (no le afecta el agua). veneno: deja charcos venenosos.

Object.assign(CRIATURAS, {
  babosa_veneno: { nombre: 'Pústula', vida: 120, dano: 20, vel: 44, radio: 6, conducta: 'salta', veneno: true, regiones: { 4: 3, 5: 1, 6: 1 }, botin: [['baba_veneno', 1, 2, 0.8], ['moneda', 1, 3, 0.3]], xp: 18, colores: ['#a060e0', '#6a2aa0', '#e8c0ff'] },
  gruto_jardinero: { nombre: 'Sembrador', vida: 150, dano: 22, vel: 30, radio: 6, conducta: 'jardinero', regiones: { 4: 2, 5: 1 }, botin: [['semilla_avena', 1, 2, 0.4], ['savia', 1, 1, 0.3], ['moneda', 2, 5, 0.5], ['huevo_leal', 1, 1, 0.01]], xp: 24, colores: ['#6a8a5a', '#a8c088', '#5a3a1a'] },
  gruto_cazador: { nombre: 'Acechador', vida: 170, dano: 26, vel: 42, radio: 6, conducta: 'cazador', regiones: { 4: 2, 5: 1 }, botin: [['pluma_antigua', 1, 1, 0.12], ['flecha_carmesi', 2, 5, 0.3], ['moneda', 3, 6, 0.5]], xp: 28, colores: ['#6a8a5a', '#a8c088', '#8a3a2a'] },
  planta_trampa: { nombre: 'Fauce', vida: 220, dano: 30, vel: 0, radio: 7, conducta: 'trampa', fija: true, regiones: { 4: 1, 5: 1.5 }, botin: [['savia', 1, 2, 0.6], ['hoja_grande', 1, 2, 0.5]], xp: 22, colores: ['#3a8a3a', '#c83a5a', '#ffd0e0'] },
  avispa: { nombre: 'Aguijonera', vida: 70, dano: 18, vel: 72, radio: 5, conducta: 'avispa', vuela: true, regiones: { 4: 1.5, 5: 3 }, botin: [['aguijon', 1, 1, 0.6], ['miel', 1, 1, 0.12]], xp: 16, colores: ['#3ad06a', '#f2d040', '#1a3a1a'] },
  escarabajo_musgo: { nombre: 'Lápida', vida: 340, dano: 34, def: 8, vel: 34, radio: 9, conducta: 'carga', regiones: { 4: 1.2 }, botin: [['caparazon_musgo', 1, 2, 0.85], ['carne_escarabajo', 1, 1, 0.5]], xp: 40, colores: ['#3a6a2a', '#7aa84e', '#2a1a10'] },
  tentaculo_moho: { nombre: 'Brazo de la Podre', vida: 280, dano: 32, vel: 0, radio: 8, conducta: 'tentaculo', fija: true, regiones: {}, botin: [['espora_moho', 2, 3], ['moneda', 4, 8, 0.6]], xp: 36, colores: ['#2ab0a0', '#5ae0c0', '#0a3a3a'] },
  larva_moho: { nombre: 'Gusano de Podre', vida: 120, dano: 24, vel: 32, radio: 6, conducta: 'repta', esporas: true, regiones: { 6: 4 }, botin: [['espora_moho', 1, 1, 0.7], ['quitina', 1, 1, 0.4]], xp: 20, colores: ['#5ae0c0', '#2a8a7a', '#c0fff0'] },
});

// Los dos titanes de la Selva. invocado: no se despierta solo (hay que hacer la ofrenda en su altar).
Object.assign(JEFES, {
  hiedra: { nombre: 'HIEDRA', titulo: 'la Flor de Sangre', frase: 'Él quiso quemarme. Ahora es mi corazón.', vida: 4400, dano: 30, radio: 16, hw: 14, hh: 8, alto: 26, region: REGION.SELVA, cristal: 'gema_hiedra', despierta: 10, correa: 24, botin: [['latigo_hiedra', 1, 1, 0.6], ['semilla_hiedra', 1, 1, 0.5], ['savia', 6, 10], ['lingote_carmesi', 6, 10], ['moneda', 60, 120], ['huevo_luz', 1, 1, 0.4]] },
  tamar: { nombre: 'TAMAR', titulo: 'el Ojo del Cielo', frase: 'Lo he visto todo desde arriba. Te he visto a ti.', vida: 5200, dano: 34, radio: 18, hw: 16, hh: 8, alto: 30, region: REGION.SELVA, cristal: 'gema_cielo', invocado: true, despierta: 14, correa: 30, botin: [['arco_tamar', 1, 1, 0.5], ['alas_tamar', 1, 1, 0.5], ['pluma_tamar', 3, 5], ['lingote_carmesi', 8, 14], ['moneda', 80, 140]] },
});

// ---------------------------------------------------------------- cultivos, peces y cocina

Object.assign(CULTIVOS, {
  avena: { nombre: 'Avena hinchada', item: 'avena', semilla: 'semilla_avena', etapa: 60 },
  melon: { nombre: 'Melón de luna', item: 'melon_luna', semilla: 'semilla_melon', etapa: 90, brilla: true },
});
Object.assign(PECES, {
  4: [['pez_hoja', 6], ['pez_espino', 3], ['pez_anguila', 1], ['pez_sol', 0.15]],
  5: [['pez_hoja', 5], ['pez_anguila', 0.8], ['pez_sol', 0.2]],
  6: [['pez_anguila', 4], ['pez_espino', 2], ['pez_sol', 0.2]],
});
Object.assign(PLATO_TIPO, { fruta_selva: 'Macedonia', avena: 'Gachas', melon_luna: 'Sorbete', miel: 'Pastel', carne_escarabajo: 'Estofado', pez_hoja: 'Ceviche', pez_espino: 'Parrillada', pez_anguila: 'Anguila asada', pez_sol: 'Festín del sol' });
Object.assign(PLATO_PREFIJO, { fruta_selva: 'tropical', avena: 'saciante', melon_luna: 'de luna', miel: 'meloso', carne_escarabajo: 'acorazado', pez_hoja: 'veloz', pez_espino: 'feroz', pez_anguila: 'eléctrico', pez_sol: 'radiante' });
NOMBRE_EFECTO.critico = 'Crítico';

// ---------------------------------------------------------------- habilidades

HABILIDADES.explosivos = { nombre: 'Explosivos', desc: '+2 % de daño de las bombas por nivel.' };
HABILIDADES.doma = { nombre: 'Doma', desc: '+2 % de daño de tu mascota por nivel.' };

// ---------------------------------------------------------------- mercaderes
// venta: [objeto, precio en monedas, cuántos tiene]. Reponen cada 25–35 minutos de juego.

const MERCADERES = {
  barbas: {
    nombre: 'Barbas', titulo: 'el minero', idolo: 'idolo_barbas', colores: ['#86d64f', '#f6ecda'],
    saludo: '¡Huy, una casa con cama! Me quedo. Vendo de todo lo de bajo tierra.',
    venta: [['antorcha', 2, 40], ['semilla_baya', 4, 10], ['semilla_trigo', 4, 10], ['semilla_bulbo', 6, 10], ['espora_hongo', 5, 10], ['semilla_avena', 10, 8], ['lingote_cobre', 12, 20], ['lingote_estano', 18, 15], ['lingote_hierro', 30, 10], ['bomba', 25, 10], ['cebo', 6, 20], ['fibra', 3, 30]],
  },
  pescadora: {
    nombre: 'Marina', titulo: 'la pescadora', idolo: 'idolo_pesca', colores: ['#6ab0ff', '#f6ecda'],
    saludo: 'Buen sitio. Se oye el agua desde aquí. ¿Pescamos?',
    venta: [['cebo', 4, 40], ['cana_cobre', 60, 1], ['anzuelo_oro', 240, 1], ['pez_guppy', 8, 10], ['pez_barro', 8, 10], ['pez_hoja', 12, 8], ['pez_espino', 14, 6], ['semilla_melon', 30, 3]],
  },
  encapuchado: {
    nombre: 'El Encapuchado', titulo: 'buscador de reliquias', idolo: 'idolo_misterioso', colores: ['#c07aff', '#2a1a3a'],
    saludo: 'Shh… Tengo cosas que nadie más tiene. Monedas por delante.',
    venta: [['huevo_leal', 400, 1], ['huevo_baboso', 300, 1], ['piedra_regreso', 40, 10], ['pluma_antigua', 60, 5], ['gema_antigua', 120, 3], ['collar_cristal', 220, 1], ['anillo_oro', 260, 1], ['bomba', 20, 15], ['incubadora', 180, 1]],
  },
};
// Valor de venta de un objeto a un mercader (en monedas).
function valorVenta(id) {
  const it = asegurarItem(id);
  if (!it || id === 'moneda') return 0;
  if (it.valor) return it.valor;
  if (id.startsWith('lingote_')) return { cobre: 5, estano: 8, hierro: 14, oro: 16, carmesi: 26 }[id.slice(8)] || 6;
  if (id.startsWith('mineral_')) return { cobre: 2, estano: 3, hierro: 6, oro: 7, carmesi: 12 }[id.slice(8)] || 2;
  if (it.plato) return 10 + (it.rareza || 0) * 15;
  if (it.tipo === 'bloque' || it.tipo === 'suelo') return 1;
  const base = [2, 9, 35, 120][it.rareza || 0];
  return it.max === 1 ? base * 4 : base;
}

// ---------------------------------------------------------------- lo que cuentan las tablillas de la Selva

const TABLILLAS = [
  'Antes de la Muralla, el Corazón latía tan fuerte que la luz llegaba hasta la Selva. Las plantas aprendieron a vivir de ese latido.',
  'Tamar vigila el cielo de piedra. Dicen que baja si le ofreces lo que perdió: sus plumas, el metal rojo y una gema antigua.',
  'La Hiedra no siempre fue mala. Era un jardín. Alguien plantó en ella el miedo y creció hacia dentro.',
  'Los grutos de la Selva no son como los de las Ruinas: siembran, cazan y guardan sus cosas en cajas. Pero no les gustan las visitas.',
  'El metal carmesí está en la hierba más vieja. Solo un pico de hierro lo saca sin romperse.',
  'Bajo el moho hay salas que nadie cavó. El moho las construyó para guardar algo. Sus muros no se rompen: entra por la puerta.',
  'Si tienes una casa con cama, alguien vendrá a vivir contigo. Los mercaderes viajan por las cuevas buscando un hogar.',
  'Los huevos se incuban al calor. Lo que nace se queda contigo para siempre.',
  'Más allá de la Selva, el mar duerme y el desierto espera. Cuando los titanes descansen, el Corazón los despertará.',
  'Una piedra de regreso guarda el camino a tu cama. Apriétala si te pierdes.',
  'Las avispas esmeralda defienden sus panales. Su miel cura casi tanto como un buen guiso.',
  'Cuidado con los hongos naranjas: estallan si los golpeas. Los grutos los usan para abrir caminos.',
];

// ---------------------------------------------------------------- objetivos de la fase 2

OBJETIVOS.push(
  { txt: 'Habla con el Corazón despierto', hecho: (J) => J.hito.hablo2 },
  { txt: 'Pon la mano en la Gran Muralla', hecho: (J) => J.murallaAbierta },
  { txt: 'Entra en la Selva Esmeralda', hecho: (J) => J.hito.selva },
  { txt: 'Pica mineral carmesí (pico de hierro)', hecho: (J) => J.hito.tiene_mineral_carmesi || J.hito.mesa_carmesi },
  { txt: 'Funde carmesí y haz una mesa carmesí', hecho: (J) => J.hito.mesa_carmesi },
  { txt: 'Forja un pico carmesí', hecho: (J) => J.hito.pico_carmesi },
  { txt: 'Casa cerrada con cama e ídolo: llega un mercader', hecho: (J) => J.hito.mercader },
  { txt: 'Incuba un huevo y lleva una mascota', hecho: (J) => J.hito.mascota },
  { txt: 'Encuentra el cofre de la Mazmorra del Moho', hecho: (J) => J.hito.cofreMoho },
  { txt: 'Derrota a Hiedra Viva', hecho: (J) => J.jefesMuertos.hiedra },
  { txt: 'Haz la ofrenda del cielo y vence a Tamar', hecho: (J) => J.jefesMuertos.tamar },
  { txt: 'Pon las gemas de los titanes en sus altares', hecho: (J) => J.altares && J.altares.tamar && J.altares.hiedra },
);
