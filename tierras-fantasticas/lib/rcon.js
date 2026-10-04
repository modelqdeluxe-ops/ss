// Cliente RCON mínimo (protocolo Source RCON, usado por Minecraft).
// Paquete: int32 longitud | int32 id | int32 tipo | cuerpo ASCII \0 | \0
const net = require('net');

const TYPE_AUTH = 3;
const TYPE_COMMAND = 2;

function encode(id, type, body) {
  const payload = Buffer.from(body, 'utf8');
  const buf = Buffer.alloc(14 + payload.length);
  buf.writeInt32LE(10 + payload.length, 0);
  buf.writeInt32LE(id, 4);
  buf.writeInt32LE(type, 8);
  payload.copy(buf, 12);
  return buf;
}

class Rcon {
  constructor({ host, port = 25575, password, timeout = 5000 }) {
    this.host = host;
    this.port = port;
    this.password = password;
    this.timeout = timeout;
    this.nextId = 1;
    this.pending = new Map();
    this.buffer = Buffer.alloc(0);
  }

  connect() {
    return new Promise((resolve, reject) => {
      this.socket = net.createConnection({ host: this.host, port: this.port });
      this.socket.setTimeout(this.timeout);
      this.socket.once('error', reject);
      this.socket.once('timeout', () => {
        this.socket.destroy();
        reject(new Error('RCON: tiempo de conexión agotado'));
      });
      this.socket.on('data', (chunk) => this.onData(chunk));
      this.socket.once('connect', async () => {
        try {
          const res = await this.send(TYPE_AUTH, this.password);
          if (res.id === -1) throw new Error('RCON: contraseña incorrecta');
          resolve(this);
        } catch (err) {
          this.close();
          reject(err);
        }
      });
    });
  }

  onData(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (this.buffer.length >= 4) {
      const len = this.buffer.readInt32LE(0);
      if (this.buffer.length < 4 + len) break;
      const id = this.buffer.readInt32LE(4);
      const type = this.buffer.readInt32LE(8);
      const body = this.buffer.toString('utf8', 12, 4 + len - 2);
      this.buffer = this.buffer.subarray(4 + len);
      // Si la autenticación falla, el servidor responde con id -1.
      const key = id === -1 ? [...this.pending.keys()][0] : id;
      const waiter = this.pending.get(key);
      if (!waiter) continue;
      // Algunos servidores envían un paquete vacío (tipo 0) antes de la respuesta de auth.
      if (waiter.type === TYPE_AUTH && type === 0) continue;
      this.pending.delete(key);
      waiter.resolve({ id, type, body });
    }
  }

  send(type, body) {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error('RCON: sin respuesta del servidor'));
      }, this.timeout);
      this.pending.set(id, {
        type,
        resolve: (res) => {
          clearTimeout(timer);
          resolve(res);
        },
      });
      this.socket.write(encode(id, type, body));
    });
  }

  async command(cmd) {
    const res = await this.send(TYPE_COMMAND, cmd);
    return res.body;
  }

  close() {
    if (this.socket) this.socket.end();
  }
}

// Ejecuta una lista de comandos en una única conexión.
async function runCommands(options, commands) {
  const rcon = await new Rcon(options).connect();
  const results = [];
  try {
    for (const cmd of commands) {
      results.push({ cmd, output: await rcon.command(cmd) });
    }
  } finally {
    rcon.close();
  }
  return results;
}

module.exports = { Rcon, runCommands, encode };
