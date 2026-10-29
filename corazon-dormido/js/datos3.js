'use strict';
// Datos del mundo grande de la fase 2 (0.7). El sector de la Selva llega ahora hasta el radio 620 en tres bandas:
//   - la Selva Esmeralda (147–330), la de siempre pero más grande y con más cosas;
//   - la Ciénaga de la Podre (330–470): por aquí subió la Podre. Agua negra, turba, árboles muertos y las costillas
//     de Ásor saliendo del fango;
//   - la Ciudad Velada (470–620): la ciudad de los Veladores de antes de la Muralla. Calles, casas, la biblioteca,
//     el templo y la cripta, todo vacío y velado.
// Aquí van los suelos, las paredes, las menas, los materiales y los objetos del mundo. Se añade a datos.js y datos2.js.

// ---------------------------------------------------------------- suelos

Object.assign(SUELO, { LODO: 24, AGUA_NEGRA: 25, PODRE: 26, TURBERA: 27, CALZADA: 28, MOSAICO: 29, CENIZA: 30, CRIPTA: 31, PLACA: 32 });
Object.assign(INFO_SUELO, {
  24: { arte: 'lodo', mapa: '#2a261e', decor: ['guijarro', 'dc_junco_0', 'dc_junco_1', 'dc_huesos', 'dc_junco_0'], densidad: 0.1, planos: ['ad_charco', 'ad_huesos'], piedra: 2 },
  25: { arte: 'agua_negra', mapa: '#101a18', liquido: true },
  26: { arte: 'podre', mapa: '#2a0e26', decor: ['dc_poro_podre', 'dc_huesos', 'dc_poro_podre'], densidad: 0.05, piedra: 3, brilla: 0.02, podre: true },
  27: { arte: 'turbera', mapa: '#22180e', decor: ['dc_junco_0', 'dc_helecho_muerto', 'guijarro', 'dc_helecho_muerto'], densidad: 0.1, planos: ['ad_raices', 'ad_charco'], piedra: 1, arable: true },
  28: { arte: 'calzada', mapa: '#454a56', decor: ['dc_hierba_seca', 'guijarro'], densidad: 0.03, planos: ['ad_musgo', 'ad_hojas_secas'], piedra: 4 },
  29: { arte: 'mosaico', mapa: '#2e3060' },
  30: { arte: 'ceniza', mapa: '#3a3632', decor: ['guijarro', 'dc_hierba_seca', 'dc_huesos', 'dc_hierba_seca'], densidad: 0.08, planos: ['ad_huesos', 'ad_hojas_secas'], piedra: 3 },
  31: { arte: 'cripta', mapa: '#1e1c26', decor: ['dc_huesos', 'dc_vela', 'dc_craneo'], densidad: 0.05, piedra: 4 },
  32: { arte: 'placa', mapa: '#8a8a70' }, // placa de presión de los puzles: se hunde si hay alguien o un bloque encima
});
// los suelos nuevos con borde orgánico (los de piedra labrada no se mezclan)
Object.assign(PRIORIDAD_SUELO, { turbera: 4, ceniza: 3, lodo: 5, podre: 10 });

// ---------------------------------------------------------------- paredes y menas

Object.assign(MURO, { TURBA: 16, RAIZ_MUERTA: 17, COSTRA: 18, HUESO_ASOR: 19, VELADA: 20, VELADA_VIEJA: 21, CRIPTA: 22, PIEDRA_GRIS: 23 });
Object.assign(INFO_MURO, {
  16: { nombre: 'Turba', arte: 'turba', vida: 300, poder: 75, bloque: 'bloque_turba', mapa: '#3a2c22' },
  17: { nombre: 'Raíz muerta', arte: 'raiz_muerta', vida: 280, poder: 75, bloque: 'bloque_raiz_muerta', mapa: '#4e4036' },
  18: { nombre: 'Costra de la Podre', arte: 'costra', vida: 420, poder: 100, mapa: '#4a1a40', brilla: 0.06 },
  19: { nombre: 'Hueso de Ásor', arte: 'hueso_asor', vida: 1, poder: 999, alto: 1.7, tapa: 0.6, mapa: '#c8b898' },
  20: { nombre: 'Piedra velada', arte: 'velada', vida: 420, poder: 100, bloque: 'bloque_velada', mapa: '#5e6474' },
  21: { nombre: 'Muro de los Veladores', arte: 'velada_vieja', vida: 1, poder: 999, alto: 1.4, tapa: 0.5, mapa: '#4a5060' },
  22: { nombre: 'Muro de la cripta', arte: 'cripta', vida: 1, poder: 999, alto: 1.25, tapa: 0.55, mapa: '#2a2632' },
  23: { nombre: 'Piedra gris', arte: 'piedra_gris', vida: 380, poder: 100, bloque: 'bloque_piedra_gris', mapa: '#40424c' },
});
Object.assign(MENA, { MEDULA: 6, PLATA: 7 });
INFO_MENA[6] = { arte: 'medula', item: 'mineral_medula', poder: 75, extra: 1.7, mapa: '#f0e0c0' };
INFO_MENA[7] = { arte: 'plata', item: 'mineral_plata', poder: 100, extra: 1.8, mapa: '#d0e0ff' };

// ---------------------------------------------------------------- materiales, bloques y suelos

item('madera_muerta', { nombre: 'Madera muerta', tipo: 'material', desc: 'Gris y dura como el hueso. De los árboles de la Ciénaga.' });
item('savia_negra', { nombre: 'Savia negra', tipo: 'material', rareza: 1, desc: 'Espesa y fría. Huele a hierro.' });
item('espora_podre', { nombre: 'Espora de la Podre', tipo: 'material', desc: 'Late en la mano, muy despacio. No te la acerques a la boca.' });
item('mineral_medula', { nombre: 'Médula de Ásor', tipo: 'material', rareza: 1, valor: 14, desc: 'Hueso del dios, con el tuétano rojo todavía. En la turba de la Ciénaga. Necesita un pico carmesí.' });
item('lingote_medula', { nombre: 'Barra de médula', tipo: 'material', rareza: 1, valor: 32, desc: 'Médula fundida y prensada. Más dura que cualquier metal.' });
item('mineral_plata', { nombre: 'Plata velada', tipo: 'material', rareza: 1, valor: 18, desc: 'Plata fría de las vetas de la Ciudad Velada. Necesita un pico de médula.' });
item('lingote_plata', { nombre: 'Lingote de plata velada', tipo: 'material', rareza: 1, valor: 40, desc: 'No se empaña nunca. Los Veladores la usaban para encerrar cosas.' });
item('pagina_velada', { nombre: 'Página velada', tipo: 'material', rareza: 1, max: 999, valor: 20, desc: 'Una hoja de un libro de los Veladores. Alguien en la Ciudad las busca.' });
item('bloque_turba', { nombre: 'Bloque de turba', tipo: 'bloque', muro: MURO.TURBA, desc: 'Tierra negra de la Ciénaga, prensada.' });
item('bloque_raiz_muerta', { nombre: 'Bloque de raíz muerta', tipo: 'bloque', muro: MURO.RAIZ_MUERTA, desc: 'Raíces grises entrelazadas.' });
item('bloque_velada', { nombre: 'Sillar velado', tipo: 'bloque', muro: MURO.VELADA, desc: 'Piedra cortada por los Veladores.' });
item('bloque_piedra_gris', { nombre: 'Piedra gris', tipo: 'bloque', muro: MURO.PIEDRA_GRIS, desc: 'Roca fría de la Ciudad Velada.' });
item('suelo_calzada', { nombre: 'Calzada velada', tipo: 'suelo', suelo: SUELO.CALZADA, desc: 'Losas gastadas por mil años de pasos.' });
item('suelo_mosaico', { nombre: 'Mosaico velado', tipo: 'suelo', suelo: SUELO.MOSAICO, rareza: 1, desc: 'Teselas azules y doradas.' });
item('suelo_turbera', { nombre: 'Suelo de turba', tipo: 'suelo', suelo: SUELO.TURBERA, desc: 'Blando y negro. Se puede arar.' });

receta('lingote_medula', 1, { mineral_medula: 2 }, 'horno');
receta('lingote_plata', 1, { mineral_plata: 2 }, 'horno');
receta('suelo_turbera', 4, { bloque_turba: 2 }, 'mesa');
receta('suelo_calzada', 4, { bloque_piedra_gris: 2 }, 'mesa_carmesi');
receta('suelo_mosaico', 4, { bloque_velada: 2, lingote_oro: 1 }, 'mesa_carmesi');

// ---------------------------------------------------------------- objetos del mundo
// restos: cuerpos de Veladores que se pueden registrar una vez (dan una línea de historia y algo de botín).
// encendido: la luz solo sale si el objeto está encendido (o.extra = 1).

Object.assign(OBJ, {
  // la Ciénaga
  arbol_muerto: { arte: 'arbol_muerto', variantes: 2, bloquea: true, vida: 70, suelta: [['madera_muerta', 2, 4], ['savia_negra', 1, 1, 0.2]], planta: true, alto: true, mapa: '#4a3e34' },
  junco: { arte: 'junco', vida: 4, suelta: [['fibra', 1, 3]], planta: true, mapa: '#5a6a3a' },
  seta_podre: { arte: 'seta_podre', brillo: true, luz: [2.5, 0.7, 0.2, 0.55], vida: 6, suelta: [['espora_podre', 1, 1]], planta: true, mapa: '#c04a9a' },
  bulto_podre: { arte: 'bulto_podre', brillo: true, luz: [5, 1.0, 0.24, 0.7], bloquea: true, vida: 90, rompible: true, nido: 'larva_moho', suelta: [['espora_podre', 2, 4], ['moneda', 2, 6, 0.3]], mapa: '#ff3cb4' },
  capullo_podre: { arte: 'capullo_podre', bloquea: true, vida: 40, rompible: true, suelta: [['espora_podre', 1, 2], ['hueso', 1, 3], ['moneda', 5, 15, 0.6], ['piedra_regreso', 1, 1, 0.1], ['gema_antigua', 1, 1, 0.05], ['pagina_velada', 1, 1, 0.15]], mapa: '#8a3a6a' },
  idolo_podre: { arte: 'idolo_podre', brillo: true, luz: [4, 1.0, 0.24, 0.7], bloquea: true, vida: 0, mapa: '#6a2a5a' },
  farol_pantano: { arte: 'farol_pantano', brillo: true, luz: [7, 0.55, 1.0, 0.6], bloquea: true, vida: 0, mapa: '#a0ff90' },
  roca_medula: { arte: 'roca_medula', bloquea: true, vida: 340, nivel: 75, suelta: [['mineral_medula', 5, 8]], mapa: '#e8d8b8' },
  restos_velador: { arte: 'restos_velador', vida: 0, interactua: 'restos', mapa: '#b0a890' },
  // la Ciudad Velada
  estatua_velador: { arte: 'estatua_velador', bloquea: true, vida: 0, mapa: '#8a90a0' },
  estatua_rota: { arte: 'estatua_rota', bloquea: true, vida: 0, mapa: '#6a7080' },
  fuente_seca: { arte: 'fuente_seca', bloquea: true, vida: 0, mapa: '#7a8090' },
  banco_piedra: { arte: 'banco_piedra', bloquea: true, vida: 0, mapa: '#6a6e78' },
  farola_velada: { arte: 'farola_velada', brillo: true, luz: [8, 0.55, 0.72, 1.0], bloquea: true, vida: 0, mapa: '#a0c0ff' },
  columna_rota: { arte: 'columna_rota', bloquea: true, vida: 0, mapa: '#6a7080' },
  sarcofago: { arte: 'sarcofago', bloquea: true, vida: 0, interactua: 'cofre', mapa: '#6a6a7a' },
  atril: { arte: 'atril', bloquea: true, vida: 0, interactua: 'tablilla', mapa: '#c8b080' },
  estante_roto: { arte: 'estante_roto', bloquea: true, vida: 1, rompible: true, suelta: [['pagina_velada', 1, 1, 0.45], ['moneda', 2, 8, 0.5], ['madera', 1, 2, 0.4]], mapa: '#6a5034' },
  urna_velada: { arte: 'urna_velada', bloquea: true, vida: 1, rompible: true, suelta: [['moneda', 6, 16, 0.7], ['mineral_plata', 1, 3, 0.3], ['pagina_velada', 1, 1, 0.12], ['piedra_regreso', 1, 1, 0.1], ['gema_antigua', 1, 1, 0.08]], mapa: '#9aa0b8' },
  roca_plata: { arte: 'roca_plata', bloquea: true, vida: 380, nivel: 100, suelta: [['mineral_plata', 5, 8]], mapa: '#d8e0f0' },
  cofre_velado: { arte: 'cofre_velado', bloquea: true, vida: 0, interactua: 'cofre', mapa: '#c0d0ff' },
  // puzles y viaje
  brasero_runa: { arte: 'brasero_runa', nombreDe: (o) => `brasero_runa_${(o.datos && o.datos.runa) || 0}${o.extra ? '_on' : ''}`, bloquea: true, vida: 0, interactua: 'brasero', encendido: [6, 0.72, 0.45, 1.0], fuego: true, mapa: '#c478ff' },
  palanca: { arte: 'palanca', bloquea: true, vida: 0, interactua: 'palanca', mapa: '#a08060' },
  bloque_empuje: { arte: 'bloque_empuje', bloquea: true, vida: 0, interactua: 'empujar', mapa: '#8a8a90' },
  puerta_sellada: { arte: 'puerta_sellada', bloquea: true, vida: 0, interactua: 'sellada', brillo: true, luz: [4, 0.6, 0.45, 1.0], mapa: '#5a4a8a' },
  piedra_paso: { arte: 'piedra_paso', bloquea: true, vida: 0, interactua: 'paso', encendido: [6, 0.5, 0.9, 1.0], mapa: '#80e0ff' },
});

// ---------------------------------------------------------------- criaturas y peces de las bandas nuevas
// (hasta que lleguen las suyas propias, las de la Selva se adentran también en la Ciénaga y la Ciudad)

Object.assign(CRIATURAS.babosa_veneno.regiones, { 8: 3 });
Object.assign(CRIATURAS.larva_moho.regiones, { 8: 2 });
Object.assign(CRIATURAS.gruto_cazador.regiones, { 8: 1 });
Object.assign(CRIATURAS.escarabajo_musgo.regiones, { 9: 1 });
Object.assign(CRIATURAS.gruto_bruto.regiones, { 9: 1 });
Object.assign(CRIATURAS.gruto_chaman.regiones, { 9: 1, 10: 1 });
Object.assign(PECES, {
  8: [['pez_anguila', 4], ['pez_espino', 2], ['pez_barro', 2], ['pez_sol', 0.1]],
  9: [['pez_cristal', 4], ['pez_anguila', 1], ['pez_dorado', 0.5]],
  10: [['pez_cristal', 3], ['pez_dorado', 0.6]],
});

// ---------------------------------------------------------------- médula (la Ciénaga) y plata velada (la Ciudad)
// La mesa de médula se hace con el corazón de Grul y la forja de plata con la llama del Centinela: cada banda se abre
// venciendo a su jefe.

item('carne_sanguijuela', { nombre: 'Carne de sanguijuela', tipo: 'comida', cura: 8, hambre: 22, ingrediente: { regen: 0.8 }, desc: 'Negra y elástica. Ingrediente.' });
item('cebo_luz', { nombre: 'Cebo del farolero', tipo: 'material', rareza: 1, valor: 18, desc: 'Sigue brillando aunque esté muerto. Para faroles y collares.' });
item('esencia_velada', { nombre: 'Esencia velada', tipo: 'material', rareza: 1, valor: 24, desc: 'Fría como el agua de un pozo. Lo que queda de un Velado.' });
item('corazon_grul', { nombre: 'Corazón de Grul', tipo: 'especial', rareza: 3, max: 1, desc: 'Aún late, despacio. Con él se hace la mesa de médula.' });
item('llama_centinela', { nombre: 'Llama del Centinela', tipo: 'especial', rareza: 3, max: 1, desc: 'Una luz fría que no quema. Con ella se hace la forja de plata.' });
// herramientas y armas
item('pico_medula', { nombre: 'Pico de médula', tipo: 'pico', poder: 105, dano: 26, vel: 0.22, max: 1, rareza: 1, desc: 'Hueso del dios con filo. Rompe la piedra de la Ciudad y saca la plata.' });
item('espada_medula', { nombre: 'Espada de médula', tipo: 'espada', dano: 64, vel: 0.29, max: 1, rareza: 1, desc: 'Una costilla afilada. Pesa poco y corta mucho.' });
item('maza_medula', { nombre: 'Maza de médula', tipo: 'maza', dano: 102, vel: 0.58, max: 1, rareza: 1, desc: 'Una vértebra con mango. Aturde y empuja.' });
item('arco_medula', { nombre: 'Arco de médula', tipo: 'arco', dano: 34, vel: 0.36, max: 1, rareza: 1, desc: 'Hueso y tendón.' });
item('flecha_medula', { nombre: 'Flecha de médula', tipo: 'municion', dano: 19, desc: 'Punta de hueso del dios.' });
item('pico_plata', { nombre: 'Pico de plata velada', tipo: 'pico', poder: 130, dano: 30, vel: 0.2, max: 1, rareza: 2, desc: 'El mejor pico que hicieron los Veladores.' });
item('espada_plata', { nombre: 'Espada velada', tipo: 'espada', dano: 78, vel: 0.28, max: 1, rareza: 2, desc: 'Plata que no se empaña. Los Velados le tienen miedo.' });
item('lanza_plata', { nombre: 'Lanza velada', tipo: 'espada', dano: 66, vel: 0.36, alcance: 42, max: 1, rareza: 2, desc: 'Llega más lejos que una espada.' });
item('arco_plata', { nombre: 'Arco velado', tipo: 'arco', dano: 42, vel: 0.34, max: 1, rareza: 2, desc: 'Tensado con el cabello de un Velado.' });
item('flecha_plata', { nombre: 'Flecha de plata', tipo: 'municion', dano: 24, desc: 'Atraviesa el velo.' });
item('lengua_grul', { nombre: 'Lengua de Grul', tipo: 'espada', dano: 70, vel: 0.36, alcance: 46, veneno: 10, max: 1, rareza: 3, desc: 'Un látigo de carne que envenena. Llega muy lejos.' });
item('espada_centinela', { nombre: 'Espada del Centinela', tipo: 'espada', dano: 98, vel: 0.33, max: 1, rareza: 3, desc: 'La hoja de piedra que guardaba la puerta. Pesa como un juramento.' });
// armaduras y accesorios
for (const [m, n, d, r, vel] of [['medula', 'médula', 5, 1, 0], ['plata', 'plata velada', 6, 2, 0.05]]) {
  item('casco_' + m, { nombre: `Casco de ${n}`, tipo: 'casco', def: d * 2, max: 1, rareza: r, desc: `Defensa ${d * 2}.` });
  item('peto_' + m, { nombre: `Peto de ${n}`, tipo: 'peto', def: d * 3, max: 1, rareza: r, desc: `Defensa ${d * 3}.` });
  item('botas_' + m, { nombre: `Botas de ${n}`, tipo: 'botas', def: d, vel, max: 1, rareza: r, desc: vel ? `Defensa ${d} y +${vel * 100} % de velocidad.` : `Defensa ${d}.` });
}
item('anillo_medula', { nombre: 'Anillo de médula', tipo: 'accesorio', dano: 0.12, vidaMax: 10, max: 1, rareza: 1, desc: '+12 % de daño y +10 de vida máxima.' });
item('collar_farolero', { nombre: 'Collar del farolero', tipo: 'accesorio', luz: 3, suerte: 0.04, max: 1, rareza: 1, desc: 'Mucha luz y un poco de suerte.' });
item('amuleto_velado', { nombre: 'Amuleto velado', tipo: 'accesorio', regen: 1.5, vidaMax: 20, max: 1, rareza: 2, desc: 'Regeneras 1,5 de vida por segundo y +20 de vida máxima.' });
item('velo_centinela', { nombre: 'Velo del Centinela', tipo: 'accesorio', vidaMax: 40, luz: 2, critico: 0.05, max: 1, rareza: 3, desc: '+40 de vida máxima, luz y +5 % de crítico.' });
// estaciones y muebles
COLOCABLE('mesa_medula', 'Mesa de médula', 'mesa_medula', 'Abre las recetas de la Ciénaga.', { max: 9, rareza: 1 });
COLOCABLE('yunque_medula', 'Yunque de médula', 'yunque_medula', 'Forja lo de médula.', { max: 9, rareza: 1 });
COLOCABLE('mesa_plata', 'Forja velada', 'mesa_plata', 'Abre las recetas de la Ciudad Velada.', { max: 9, rareza: 2 });
COLOCABLE('yunque_plata', 'Yunque de plata', 'yunque_plata', 'Forja lo de plata velada.', { max: 9, rareza: 2 });
COLOCABLE('farol_cebo', 'Farol del pantano', 'farol_cebo', 'Un cebo de farolero en una jaula: luz verde y enferma.', { max: 20 });
COLOCABLE('farola', 'Farola velada', 'farola', 'Luz fría de alma, enorme.', { max: 20, rareza: 1 });
COLOCABLE('banco', 'Banco de piedra', 'banco', 'Para sentarse a esperar.', { max: 20 });
COLOCABLE('estatua_decor', 'Estatua de un Velador', 'estatua_decor', 'Te mira sin cara.', { max: 9, rareza: 1 });

Object.assign(NIVEL_MESA, { mesa_medula: 6, mesa_plata: 7 });
Object.assign(NIVEL_YUNQUE, { yunque_medula: 5, yunque_plata: 6 });
Object.assign(NOMBRE_ESTACION, { mesa_medula: 'Mesa de médula', yunque_medula: 'Yunque de médula', mesa_plata: 'Forja velada', yunque_plata: 'Yunque de plata' });
receta('mesa_medula', 1, { lingote_medula: 10, madera_muerta: 8, corazon_grul: 1 }, 'mesa_carmesi');
receta('yunque_medula', 1, { lingote_medula: 12 }, 'mesa_medula');
receta('pico_medula', 1, { lingote_medula: 12, madera_muerta: 3 }, 'mesa_medula');
receta('arco_medula', 1, { lingote_medula: 6, madera_muerta: 4, fibra: 6 }, 'mesa_medula');
receta('flecha_medula', 10, { madera_muerta: 2, lingote_medula: 1 }, 'mesa_medula');
receta('farol_cebo', 1, { cebo_luz: 1, madera_muerta: 2 }, 'mesa_medula');
receta('banco', 1, { bloque_piedra_gris: 6 }, 'mesa_medula');
receta('suelo_turbera', 4, { bloque_turba: 2 }, 'mesa_medula');
receta('espada_medula', 1, { lingote_medula: 14, savia_negra: 1 }, 'yunque_medula');
receta('maza_medula', 1, { lingote_medula: 16, madera_muerta: 4 }, 'yunque_medula');
receta('casco_medula', 1, { lingote_medula: 11 }, 'yunque_medula');
receta('peto_medula', 1, { lingote_medula: 17 }, 'yunque_medula');
receta('botas_medula', 1, { lingote_medula: 9 }, 'yunque_medula');
receta('anillo_medula', 1, { lingote_medula: 6, gema_antigua: 1 }, 'yunque_medula');
receta('collar_farolero', 1, { cebo_luz: 4, lingote_medula: 2 }, 'yunque_medula');
receta('mesa_plata', 1, { lingote_plata: 10, bloque_velada: 8, llama_centinela: 1 }, 'mesa_medula');
receta('yunque_plata', 1, { lingote_plata: 12 }, 'mesa_plata');
receta('pico_plata', 1, { lingote_plata: 14, lingote_medula: 2 }, 'mesa_plata');
receta('arco_plata', 1, { lingote_plata: 8, esencia_velada: 2, fibra: 6 }, 'mesa_plata');
receta('flecha_plata', 10, { madera_muerta: 2, lingote_plata: 1 }, 'mesa_plata');
receta('farola', 1, { lingote_plata: 2, esencia_velada: 1 }, 'mesa_plata');
receta('estatua_decor', 1, { bloque_velada: 12 }, 'mesa_plata');
receta('espada_plata', 1, { lingote_plata: 16, esencia_velada: 2 }, 'yunque_plata');
receta('lanza_plata', 1, { lingote_plata: 12, madera_muerta: 4 }, 'yunque_plata');
receta('casco_plata', 1, { lingote_plata: 12, esencia_velada: 1 }, 'yunque_plata');
receta('peto_plata', 1, { lingote_plata: 18, esencia_velada: 1 }, 'yunque_plata');
receta('botas_plata', 1, { lingote_plata: 10, esencia_velada: 1 }, 'yunque_plata');
receta('amuleto_velado', 1, { esencia_velada: 6, lingote_plata: 3, gema_antigua: 1 }, 'yunque_plata');

Object.assign(OBJ, {
  mesa_medula: { arte: 'mesa_medula', bloquea: true, vida: 3, suelta: [['mesa_medula', 1, 1]], estacion: 'mesa_medula', interactua: 'crear', mapa: '#e8d8b8' },
  yunque_medula: { arte: 'yunque_medula', bloquea: true, vida: 3, suelta: [['yunque_medula', 1, 1]], estacion: 'yunque_medula', interactua: 'crear', mapa: '#e8d8b8' },
  mesa_plata: { arte: 'mesa_plata', brillo: true, luz: [4, 0.55, 0.72, 1.0], bloquea: true, vida: 3, suelta: [['mesa_plata', 1, 1]], estacion: 'mesa_plata', interactua: 'crear', mapa: '#d0e0ff' },
  yunque_plata: { arte: 'yunque_plata', bloquea: true, vida: 3, suelta: [['yunque_plata', 1, 1]], estacion: 'yunque_plata', interactua: 'crear', mapa: '#d0e0ff' },
  farol_cebo: { arte: 'farol_pantano', brillo: true, luz: [8, 0.55, 1.0, 0.6], bloquea: true, vida: 3, suelta: [['farol_cebo', 1, 1]], mapa: '#a0ff90' },
  farola: { arte: 'farola_velada', brillo: true, luz: [11, 0.55, 0.72, 1.0], bloquea: true, vida: 3, suelta: [['farola', 1, 1]], mapa: '#a0c0ff' },
  banco: { arte: 'banco_piedra', bloquea: true, vida: 3, suelta: [['banco', 1, 1]], mapa: '#6a6e78' },
  estatua_decor: { arte: 'estatua_velador', bloquea: true, vida: 3, suelta: [['estatua_decor', 1, 1]], mapa: '#8a90a0' },
});

// ---------------------------------------------------------------- criaturas de la Ciénaga, la Ciudad y la Cripta
// (las de la Selva que se metían en las bandas nuevas se quedan solo de visita)
Object.assign(CRIATURAS.babosa_veneno.regiones, { 8: 1 });
Object.assign(CRIATURAS.larva_moho.regiones, { 8: 1 });
Object.assign(CRIATURAS.gruto_cazador.regiones, { 8: 0.5 });
Object.assign(CRIATURAS.escarabajo_musgo.regiones, { 9: 0.3 });
Object.assign(CRIATURAS.gruto_bruto.regiones, { 9: 0.6 });
Object.assign(CRIATURAS.gruto_chaman.regiones, { 9: 0.6, 10: 0.5 });
Object.assign(CRIATURAS, {
  sanguijuela: { nombre: 'Sanguijuela de turba', vida: 210, dano: 32, vel: 38, radio: 6, conducta: 'lancea', regiones: { 8: 4 }, botin: [['carne_sanguijuela', 1, 1, 0.6], ['savia_negra', 1, 1, 0.12], ['moneda', 2, 6, 0.4]], xp: 30, colores: ['#2a342c', '#4e5a44', '#c8302a'] },
  farolero: { nombre: 'Farolero', vida: 260, dano: 40, vel: 30, radio: 7, conducta: 'farol', regiones: { 8: 1.6 }, botin: [['cebo_luz', 1, 1, 0.7], ['hueso', 1, 2, 0.4], ['moneda', 4, 10, 0.5]], xp: 40, colores: ['#16161e', '#a8d82a', '#e0ff6a'] },
  sapo_hueco: { nombre: 'Sapo hueco', vida: 230, dano: 34, vel: 46, radio: 7, conducta: 'salta', veneno: true, regiones: { 8: 2.4 }, botin: [['espora_podre', 1, 2, 0.6], ['baba_veneno', 1, 2, 0.5], ['moneda', 2, 6, 0.3]], xp: 32, colores: ['#3e4830', '#525c3e', '#ff3cb4'] },
  moscarda: { nombre: 'Moscarda', vida: 110, dano: 26, vel: 80, radio: 5, conducta: 'avispa', vuela: true, regiones: { 8: 2, 9: 0.6 }, botin: [['espora_podre', 1, 1, 0.4], ['moneda', 1, 4, 0.3]], xp: 22, colores: ['#24242c', '#c8301e', '#ff3cb4'] },
  gruto_fango: { nombre: 'Gruto del fango', vida: 260, dano: 36, vel: 40, radio: 6, conducta: 'lancea', regiones: { 8: 1.4 }, botin: [['hueso', 1, 2, 0.5], ['cebo', 2, 4, 0.3], ['moneda', 4, 9, 0.5], ['lingote_medula', 1, 1, 0.05]], xp: 40, colores: ['#3e2e1c', '#684e30', '#ffa424'] },
  necrofago: { nombre: 'Necrófago', vida: 320, dano: 42, vel: 52, radio: 7, conducta: 'lancea', regiones: { 9: 3, 10: 1.5 }, botin: [['hueso', 2, 3, 0.6], ['pagina_velada', 1, 1, 0.15], ['moneda', 5, 12, 0.5]], xp: 48, colores: ['#3e3638', '#685c5c', '#d8fff6'] },
  velado: { nombre: 'Velado', vida: 280, dano: 44, vel: 34, radio: 7, conducta: 'fantasma', vuela: true, translucido: true, regiones: { 9: 2, 10: 3 }, botin: [['esencia_velada', 1, 1, 0.6], ['pagina_velada', 1, 1, 0.2], ['moneda', 6, 14, 0.5]], xp: 55, colores: ['#3e64a0', '#86b0e6', '#e8f6ff'] },
  guardian_piedra: { nombre: 'Guardián de piedra', vida: 520, dano: 50, def: 10, vel: 26, radio: 9, conducta: 'estatua', regiones: { 9: 0.8 }, botin: [['bloque_velada', 2, 4, 0.8], ['mineral_plata', 1, 3, 0.4], ['gema_antigua', 1, 1, 0.05]], xp: 70, colores: ['#3c424e', '#646c7a', '#b4dcff'] },
  velador_caido: { nombre: 'Velador caído', vida: 380, dano: 46, vel: 40, radio: 6, conducta: 'lancea', regiones: { 10: 3 }, botin: [['hueso', 2, 4, 0.7], ['pagina_velada', 1, 1, 0.25], ['lingote_plata', 1, 1, 0.08], ['moneda', 6, 14, 0.5]], xp: 60, colores: ['#ae9e74', '#303848', '#b4dcff'] },
});
// los jefes de las bandas: Grul en la Caja Torácica de Ásor y el Centinela al fondo de la Cripta
Object.assign(JEFES, {
  grul: { nombre: 'GRUL', titulo: 'el Sapo Hueco', frase: 'Me tragué a cien Veladores. Ninguno me llenó.', vida: 6400, dano: 38, radio: 22, hw: 22, hh: 10, alto: 34, region: REGION.CIENAGA, cristal: 'corazon_grul', despierta: 11, correa: 32, botin: [['lengua_grul', 1, 1, 0.5], ['lingote_medula', 8, 14], ['savia_negra', 2, 4], ['moneda', 100, 180], ['huevo_moho', 1, 1, 0.35]] },
  centinela: { nombre: 'EL CENTINELA', titulo: 'el Que No Duerme', frase: 'Guardé la puerta antes de que hubiera Muralla. No pasarás.', vida: 7000, dano: 44, radio: 18, hw: 14, hh: 8, alto: 44, region: REGION.CRIPTA, cristal: 'llama_centinela', despierta: 6, correa: 15, botin: [['espada_centinela', 1, 1, 0.5], ['velo_centinela', 1, 1, 0.5], ['lingote_plata', 8, 14], ['esencia_velada', 3, 6], ['moneda', 140, 220], ['gema_antigua', 2, 4]] },
});
Object.assign(PECES, { 8: [['pez_anguila', 4], ['pez_espino', 2], ['pez_barro', 2], ['pez_sol', 0.1]] });
Object.assign(PLATO_TIPO, { carne_sanguijuela: 'Morcilla' });
Object.assign(PLATO_PREFIJO, { carne_sanguijuela: 'de pantano' });
