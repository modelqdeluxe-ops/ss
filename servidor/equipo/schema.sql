-- Servidor de relevo del equipo de Rumentis (Cloudflare D1).
-- Solo guarda sobres cifrados: no puede leer lo que se mandan el jefe y sus vaqueros.
CREATE TABLE IF NOT EXISTS equipos(
  id TEXT PRIMARY KEY,          -- número al azar del equipo (lo crea la app del jefe)
  firma TEXT NOT NULL,          -- llave pública Ed25519 del jefe (base64)
  creado INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS licencias(
  h TEXT PRIMARY KEY,           -- SHA-256 de la licencia: el código mismo no se guarda
  equipo TEXT NOT NULL,
  ficha TEXT NOT NULL,          -- ficha del equipo firmada por el jefe (llaves públicas, nombre de la finca)
  baja INTEGER,                 -- fecha en que el jefe la dio de baja
  altas INTEGER NOT NULL DEFAULT 0,
  creado INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS miembros(
  equipo TEXT NOT NULL,
  vid TEXT NOT NULL,            -- número del vaquero (sale de su llave pública)
  firma TEXT NOT NULL,
  PRIMARY KEY(equipo,vid)
);
CREATE TABLE IF NOT EXISTS buzon(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  equipo TEXT NOT NULL,
  para TEXT NOT NULL,           -- 'jefe', un vaquero o 'todos'
  de TEXT NOT NULL,
  r TEXT,                       -- 'estado': el nuevo reemplaza al anterior
  cuerpo TEXT NOT NULL,         -- el sobre cifrado y firmado, tal cual
  creado INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS buzon_para ON buzon(equipo,para,id);
CREATE INDEX IF NOT EXISTS buzon_creado ON buzon(creado);
