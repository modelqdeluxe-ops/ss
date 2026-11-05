// Jugador de prueba para un servidor Forge/Mohist 1.20.1 (tf-client/tools/mohist/README.md): hace el apretón de manos de
// Forge (FML3: contesta a fml:loginwrapper con la lista de mods y canales del servidor y acusa recibo de lo demás), entra
// sin cuenta (online-mode=false), escribe en el chat lo que se añada a <carpeta>/<nombre>.cmd (una línea por mensaje o
// comando; «quit» para salir) y apunta todo lo que le llega del chat (sistema y jugadores) en su salida. Líneas especiales:
// «!usar x y z [cara cx cy cz]» (clic derecho con lo de la mano sobre la cara de arriba de ese bloque, o la cara y el
// punto que se digan), «!mirar yaw pitch», «!romper x y z», «!hueco n» (hueco de la barra) y
// «!pad app|pestaña|acción|texto» (pulsar en el pad, como el TF Client). Apunta los avisos del pad y cuándo se le abre.
// Confirma los /tp como el juego.
//   node bot.js <nombre> [puerto] [carpeta de los .cmd]
const mc = require('minecraft-protocol');
const fs = require('fs');
const path = require('path');

const name = process.argv[2] || 'Bot';
const port = Number(process.argv[3] || 25565);
const cmdFile = path.join(process.argv[4] || __dirname, `${name}.cmd`);
fs.writeFileSync(cmdFile, '');

const log = (...a) => console.log(new Date().toISOString().slice(11, 23), `[${name}]`, ...a);

// ---- búfer de Minecraft (VarInt, cadenas) ----
function readVarInt(buf, o) {
  let v = 0, s = 0, b;
  do { b = buf[o.i++]; v |= (b & 0x7f) << s; s += 7; } while (b & 0x80);
  return v;
}
function readStr(buf, o) {
  const n = readVarInt(buf, o);
  const s = buf.toString('utf8', o.i, o.i + n);
  o.i += n;
  return s;
}
function varInt(v) {
  const out = [];
  do { let b = v & 0x7f; v >>>= 7; if (v) b |= 0x80; out.push(b); } while (v);
  return Buffer.from(out);
}
function str(s) { const b = Buffer.from(s, 'utf8'); return Buffer.concat([varInt(b.length), b]); }
function wrap(channel, inner) { return Buffer.concat([str(channel), varInt(inner.length), inner]); }

const client = mc.createClient({ host: '127.0.0.1', port, username: name, version: '1.20.1', auth: 'offline', hideErrors: true });
client.tagHost = '\0FML3\0';
client.removeAllListeners('login_plugin_request');
client.on('login_plugin_request', (p) => {
  const reply = (data) => client.write('login_plugin_response', { messageId: p.messageId, data });
  if (p.channel !== 'fml:loginwrapper' || !p.data) { log('login plugin desconocido', p.channel); return reply(undefined); }
  const o = { i: 0 };
  const inner = readStr(p.data, o);
  const len = readVarInt(p.data, o);
  const body = p.data.subarray(o.i, o.i + len);
  if (inner !== 'fml:handshake') { log('loginwrapper de', inner, '(se contesta vacío)'); return reply(wrap(inner, Buffer.alloc(0))); }
  if (body[0] === 1) {
    // S2CModList: mods, canales (nombre → versión), registros y registros de datapacks
    const b = { i: 1 };
    const mods = []; for (let n = readVarInt(body, b); n > 0; n--) mods.push(readStr(body, b));
    const channels = []; for (let n = readVarInt(body, b); n > 0; n--) channels.push([readStr(body, b), readStr(body, b)]);
    log(`lista de mods del servidor: ${mods.length} mods, ${channels.length} canales`);
    // C2SModListReply (2): los mismos mods y canales con su versión, sin registros
    const parts = [Buffer.from([2]), varInt(mods.length), ...mods.map(str), varInt(channels.length)];
    for (const [c, v] of channels) parts.push(str(c), str(v));
    parts.push(varInt(0));
    return reply(wrap('fml:handshake', Buffer.concat(parts)));
  }
  // registros, config, datos de los mods…: C2SAcknowledge (99)
  return reply(wrap('fml:handshake', Buffer.from([99])));
});

client.on('login', () => log('dentro del mundo'));
// los teletransportes (/tp): confirmarlos como el juego (si no, el servidor no deja usar ni poner nada)
let pos = { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 };
client.on('position', (p) => {
  pos = {
    x: p.flags & 1 ? pos.x + p.x : p.x, y: p.flags & 2 ? pos.y + p.y : p.y, z: p.flags & 4 ? pos.z + p.z : p.z,
    yaw: p.flags & 8 ? pos.yaw + p.yaw : p.yaw, pitch: p.flags & 16 ? pos.pitch + p.pitch : p.pitch,
  };
  client.write('teleport_confirm', { teleportId: p.teleportId });
  client.write('position_look', { ...pos, onGround: true });
});
let sequence = 1;
client.on('kick_disconnect', (p) => log('ECHADO:', JSON.stringify(p.reason)));
client.on('end', (r) => { log('fin de la conexión', r || ''); process.exit(0); });
client.on('error', (e) => log('error', e.message));

function plain(json) {
  try {
    const j = typeof json === 'string' ? JSON.parse(json) : json;
    const walk = (c) => (typeof c === 'string' ? c : (c.text || '') + (c.translate ? `{${c.translate}${c.with ? ':' + c.with.map(walk).join('|') : ''}}` : '') + (c.extra || []).map(walk).join(''));
    return walk(j);
  } catch { return String(json); }
}
client.on('systemChat', (m) => log('CHAT sistema:', plain(m.formattedMessage)));
client.on('set_title_text', (p) => log('TÍTULO:', plain(p.text)));
// el pad del TF Client (canal tfclient:pad): los avisos (3) y cuando el servidor lo abre en una app (7)
client.on('custom_payload', (p) => {
  if (p.channel !== 'tfclient:pad' || !p.data || !p.data.length) return;
  const o = { i: 1 };
  if (p.data[0] === 3) log('PAD aviso:', readStr(p.data, o));
  else if (p.data[0] === 7) log('PAD abre:', readStr(p.data, o), readStr(p.data, o));
});
client.on('set_title_subtitle', (p) => log('SUBTÍTULO:', plain(p.text)));
client.on('playerChat', (m) => log('CHAT jugador:', plain(m.senderName), '→', m.plainMessage ?? plain(m.formattedMessage), m.unsignedContent ? '| ' + plain(m.unsignedContent) : ''));

let done = 0;
setInterval(() => {
  const lines = fs.readFileSync(cmdFile, 'utf8').split('\n').filter(Boolean);
  for (; done < lines.length; done++) {
    const line = lines[done];
    if (line === 'quit') { client.end('fin'); return; }
    if (line.startsWith('!usar ')) {
      // clic derecho con lo que lleva en la mano sobre la cara de arriba del bloque x y z; opcional: la cara
      // (0 abajo, 1 arriba, 2 norte, 3 sur, 4 oeste, 5 este) y el punto de la cara (0..1 en x y z)
      const [x, y, z, cara = 1, cx = 0.5, cy = 1, cz = 0.5] = line.slice(6).trim().split(/\s+/).map(Number);
      log('usa lo de la mano sobre', x, y, z, 'cara', cara, 'en', cx, cy, cz);
      client.write('block_place', { hand: 0, location: { x, y, z }, direction: cara, cursorX: cx, cursorY: cy, cursorZ: cz, insideBlock: false, sequence: sequence++ });
      continue;
    }
    if (line.startsWith('!mirar ')) {
      // girar la cabeza: «!mirar yaw pitch» (grados, como /tp)
      const [yaw, pitch] = line.slice(7).trim().split(/\s+/).map(Number);
      pos.yaw = yaw; pos.pitch = pitch;
      client.write('look', { yaw, pitch, onGround: true });
      continue;
    }
    if (line.startsWith('!romper ')) {
      // empezar a romper el bloque x y z (en creativo se rompe ya; en supervivencia, además, «terminar»)
      const [x, y, z, cara = 1] = line.slice(8).trim().split(/\s+/).map(Number);
      log('rompe', x, y, z);
      client.write('block_dig', { status: 0, location: { x, y, z }, face: cara, sequence: sequence++ });
      client.write('block_dig', { status: 2, location: { x, y, z }, face: cara, sequence: sequence++ });
      continue;
    }
    if (line.startsWith('!pad ')) {
      // pulsar en el pad: «!pad app|pestaña|acción|texto» (TFPadNet.Action, índice 4)
      const [app, tab = '', action = '', text = ''] = line.slice(5).split('|');
      log('pad:', app, tab, action, text);
      client.write('custom_payload', { channel: 'tfclient:pad', data: Buffer.concat([Buffer.from([4]), str(app), str(tab), str(action), str(text)]) });
      continue;
    }
    if (line.startsWith('!hueco ')) {
      client.write('held_item_slot', { slotId: Number(line.slice(7)) });
      continue;
    }
    log('escribe:', line);
    client.chat(line);
  }
}, 200);
