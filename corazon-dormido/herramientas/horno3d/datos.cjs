// node datos.cjs <extraído.json> <salida.js> <NOMBRE> <clip1,clip2,...>
// Pasa el movimiento extraído (extraer.cjs) a un archivo del juego: solo los huesos que usa el esqueleto 2D, en
// milésimas (enteros) para que pese poco. ANIM_<NOMBRE> = { huesos, clips: { nombre: { fps, c: [[x,y,z,...]...], s } } }
// s: el «arriba» de las dos manos (handslot) en cada cuadro, para girar lo que llevan.
const fs = require('fs');
const [entrada, salida, nombre, lista] = process.argv.slice(2);
const d = JSON.parse(fs.readFileSync(entrada));
const HUESOS = ['hips', 'spine', 'chest', 'head', 'upperarml', 'lowerarml', 'handl', 'handslotl', 'upperarmr', 'lowerarmr', 'handr', 'handslotr', 'upperlegl', 'lowerlegl', 'footl', 'toesl', 'upperlegr', 'lowerlegr', 'footr', 'toesr'];
const ix = HUESOS.map((h) => d.huesos.indexOf(h));
if (ix.some((i) => i < 0)) throw new Error('faltan huesos: ' + HUESOS.filter((h, k) => ix[k] < 0));
const clips = {};
for (const c of lista.split(',')) {
  const cl = d.clips[c];
  if (!cl) throw new Error('no hay clip ' + c);
  clips[c] = {
    fps: cl.fps,
    c: cl.cuadros.map((f) => ix.flatMap((i) => f[i].slice(0, 3).map((v) => Math.round(v * 1000)))),
    s: cl.cuadros.map((f) => [d.huesos.indexOf('handslotl'), d.huesos.indexOf('handslotr')].flatMap((i) => f[i].slice(3, 6).map((v) => Math.round(v * 1000)))),
  };
}
fs.writeFileSync(salida, `'use strict';\n// Movimiento de animaciones CC0 (KayKit, Kay Lousberg: www.kaylousberg.com). Solo el movimiento de los huesos;\n// los dibujos son nuestros (esqueleto.js). Generado por herramientas/horno3d/datos.cjs: no se edita a mano.\nconst ANIM_${nombre} = ${JSON.stringify({ huesos: HUESOS, clips })};\n`);
console.log(salida, (fs.statSync(salida).size / 1024).toFixed(1) + ' KB');
