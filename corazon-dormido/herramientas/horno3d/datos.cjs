// node datos.cjs <extraído.json> <salida.js> <NOMBRE> <clip1,clip2,...> [hueso1,hueso2,...] [escala] [autor]
// Pasa el movimiento extraído (extraer.cjs) a un archivo del juego: solo los huesos que se usan, en milésimas
// (enteros) para que pese poco. ANIM_<NOMBRE> = { huesos, clips: { nombre: { fps, c: [[x,y,z,...]...], s } } }
// s (solo si hay handslot): el «arriba» de las dos manos en cada cuadro, para girar lo que llevan.
// Sin lista de huesos se usan los 20 del esqueleto humano de KayKit. escala: multiplica las posiciones (los FBX de
// Quaternius vienen en centímetros: 0.01). Los clips se guardan sin el prefijo «Armature|».
const fs = require('fs');
const [entrada, salida, nombre, lista, huesos, escala = '1', autor = 'KayKit, Kay Lousberg: www.kaylousberg.com'] = process.argv.slice(2);
const d = JSON.parse(fs.readFileSync(entrada));
const HUMANO = ['hips', 'spine', 'chest', 'head', 'upperarml', 'lowerarml', 'handl', 'handslotl', 'upperarmr', 'lowerarmr', 'handr', 'handslotr', 'upperlegl', 'lowerlegl', 'footl', 'toesl', 'upperlegr', 'lowerlegr', 'footr', 'toesr'];
const HUESOS = huesos && huesos !== '-' ? huesos.split(',') : HUMANO;
const ix = HUESOS.map((h) => d.huesos.indexOf(h));
if (ix.some((i) => i < 0)) throw new Error('faltan huesos: ' + HUESOS.filter((h, k) => ix[k] < 0));
const manos = [d.huesos.indexOf('handslotl'), d.huesos.indexOf('handslotr')], k = Number(escala);
const clips = {};
for (const c of lista.split(',')) {
  const cl = d.clips[c] || d.clips[Object.keys(d.clips).find((n) => n.split('|').pop() === c)];
  if (!cl) throw new Error('no hay clip ' + c);
  clips[c.split('|').pop()] = {
    fps: cl.fps,
    c: cl.cuadros.map((f) => ix.flatMap((i) => f[i].slice(0, 3).map((v) => Math.round(v * k * 1000)))),
    ...(manos[0] >= 0 && HUESOS.includes('handslotl') ? { s: cl.cuadros.map((f) => manos.flatMap((i) => f[i].slice(3, 6).map((v) => Math.round(v * 1000)))) } : {}),
  };
}
fs.writeFileSync(salida, `'use strict';\n// Movimiento de animaciones CC0 (${autor}). Solo el movimiento de los huesos;\n// los dibujos son nuestros. Generado por herramientas/horno3d/datos.cjs: no se edita a mano.\nconst ANIM_${nombre} = ${JSON.stringify({ huesos: HUESOS, clips })};\n`);
console.log(salida, (fs.statSync(salida).size / 1024).toFixed(1) + ' KB');
