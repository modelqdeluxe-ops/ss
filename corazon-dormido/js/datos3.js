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
