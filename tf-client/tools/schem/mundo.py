"""Lee una caja de bloques, sus entidades de bloque y las entidades de un mundo de la 1.18 en adelante (secciones con
paleta, entidades en entities/). El mundo tiene que estar ya en la versión que se quiere (ver README: actualizar.sh)."""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtio  # noqa: E402

AIRE = 'minecraft:air'


def estado(p):
    """Compuesto de la paleta → «minecraft:nombre[prop=valor,...]» (propiedades en orden alfabético)."""
    n = p['Name'][1]
    props = p.get('Properties')
    if props:
        n += '[' + ','.join(f'{k}={v[1]}' for k, v in sorted(props[1].items())) + ']'
    return n


class Mundo:
    def __init__(self, carpeta):
        self.carpeta = carpeta
        self._regiones = {}
        self._chunks = {}

    def _region(self, sub, rx, rz):
        k = (sub, rx, rz)
        if k not in self._regiones:
            f = os.path.join(self.carpeta, sub, f'r.{rx}.{rz}.mca')
            self._regiones[k] = nbtio.Region(f) if os.path.exists(f) and os.path.getsize(f) >= 8192 else None
        return self._regiones[k]

    def chunk(self, cx, cz):
        k = (cx, cz)
        if k not in self._chunks:
            r = self._region('region', cx >> 5, cz >> 5)
            c = r.chunk(cx & 31, cz & 31) if r else None
            self._chunks[k] = c[1] if c else None
        return self._chunks[k]

    def version(self, cx, cz):
        c = self.chunk(cx, cz)
        return c['DataVersion'][1] if c else None

    def caja(self, x0, y0, z0, x1, y1, z1):
        """(bloques[y, z, x] con índices de la paleta, paleta [str], entidades de bloque [compuestos con tipo])."""
        pal = [AIRE]
        idx = {AIRE: 0}
        arr = np.zeros((y1 - y0 + 1, z1 - z0 + 1, x1 - x0 + 1), dtype=np.int32)
        bes = []
        for cx in range(x0 >> 4, (x1 >> 4) + 1):
            for cz in range(z0 >> 4, (z1 >> 4) + 1):
                c = self.chunk(cx, cz)
                if c is None:
                    continue
                for sec in c['sections'][1][1]:
                    by0 = sec['Y'][1] * 16
                    if by0 + 15 < y0 or by0 > y1:
                        continue
                    bs = sec.get('block_states')
                    if not bs:
                        continue
                    plist = bs[1]['palette'][1][1]
                    gid = []
                    for p in plist:
                        s = estado(p)
                        if s not in idx:
                            idx[s] = len(pal)
                            pal.append(s)
                        gid.append(idx[s])
                    gid = np.array(gid, dtype=np.int32)
                    if 'data' in bs[1]:
                        bits = max(4, (len(plist) - 1).bit_length())
                        per = 64 // bits
                        a = np.array(bs[1]['data'][1], dtype=np.int64).view(np.uint64)
                        sh = np.arange(per, dtype=np.uint64) * np.uint64(bits)
                        v = ((a[:, None] >> sh[None, :]) & np.uint64((1 << bits) - 1)).reshape(-1)[:4096].astype(np.int64)
                    else:
                        v = np.zeros(4096, dtype=np.int64)
                    blk = gid[v].reshape(16, 16, 16)  # y, z, x
                    ax0, ax1 = max(x0, cx * 16), min(x1, cx * 16 + 15)
                    az0, az1 = max(z0, cz * 16), min(z1, cz * 16 + 15)
                    ay0, ay1 = max(y0, by0), min(y1, by0 + 15)
                    if ax0 > ax1 or az0 > az1 or ay0 > ay1:
                        continue
                    arr[ay0 - y0:ay1 - y0 + 1, az0 - z0:az1 - z0 + 1, ax0 - x0:ax1 - x0 + 1] = \
                        blk[ay0 - by0:ay1 - by0 + 1, az0 - cz * 16:az1 - cz * 16 + 1, ax0 - cx * 16:ax1 - cx * 16 + 1]
                for be in c.get('block_entities', (9, (10, [])))[1][1]:
                    bx, by, bz = be['x'][1], be['y'][1], be['z'][1]
                    if x0 <= bx <= x1 and y0 <= by <= y1 and z0 <= bz <= z1:
                        bes.append(be)
        return arr, pal, bes

    def entidades(self, x0, y0, z0, x1, y1, z1):
        """Entidades (compuestos con tipo) cuya posición cae en la caja."""
        out = []
        for cx in range(x0 >> 4, (x1 >> 4) + 1):
            for cz in range(z0 >> 4, (z1 >> 4) + 1):
                r = self._region('entities', cx >> 5, cz >> 5)
                c = r.chunk(cx & 31, cz & 31) if r else None
                if not c:
                    continue
                for e in c[1].get('Entities', (9, (10, [])))[1][1]:
                    px, py, pz = e['Pos'][1][1]
                    if x0 <= px < x1 + 1 and y0 <= py < y1 + 1 and z0 <= pz < z1 + 1:
                        out.append(e)
        return out
