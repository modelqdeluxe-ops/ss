-- Rumentis: comparación anónima con engordes de la zona (Cloudflare D1)
-- Una fila por finca y mes. La finca es un número al azar que genera la app (se guarda su hash, nunca el número).
CREATE TABLE IF NOT EXISTS aportes (
  fid    TEXT NOT NULL,          -- SHA-256 del número al azar de la finca + SAL
  mes    TEXT NOT NULL,          -- AAAA-MM
  pais   TEXT NOT NULL,          -- código ISO de 2 letras
  celda  TEXT,                   -- cuadro de 1 grado (unos 110 km): "lat_lon" redondeado hacia abajo, o NULL
  moneda TEXT,                   -- código ISO de la moneda del costo
  gdp    REAL,                   -- ganancia diaria, kg por cabeza
  conv   REAL,                   -- kg de alimento por kg ganado
  mort   REAL,                   -- mortalidad, %
  costo  REAL,                   -- costo por kg ganado, en su moneda
  costo_usd REAL,                -- el mismo costo en dólares
  dias   REAL,                   -- días de engorde de los lotes vendidos
  tam    INTEGER,                -- tamaño: 1 (< 50 cabezas), 2 (50-199), 3 (200-999), 4 (1000 o más)
  creado TEXT NOT NULL,
  PRIMARY KEY (fid, mes)
);
CREATE INDEX IF NOT EXISTS aportes_pais_mes ON aportes (pais, mes);
CREATE INDEX IF NOT EXISTS aportes_celda_mes ON aportes (celda, mes);
-- límite de envíos por IP y día (la IP se guarda como hash y se borra a los 2 días)
CREATE TABLE IF NOT EXISTS limites (
  k   TEXT PRIMARY KEY,          -- SHA-256 de IP + día + SAL
  dia TEXT NOT NULL,
  n   INTEGER NOT NULL
);
