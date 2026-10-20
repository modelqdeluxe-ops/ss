"""El Void entero a partir de la isla del dueño (1.3.96-1.3.97): la isla de la grieta, los Fragmentos y el Santuario.

    python3 -I tools/void/isla_alba.py [--preview carpeta]

Parte de tools/void/isla_base.bin (la isla del dueño ya convertida por isla.py, con su santuario de la 1.3.89, «el
Umbral») y escribe src/main/resources/tfclient-void-isla.bin; sale siempre lo mismo. (1.3.97, el dueño: «el Void no es
el End; nada de lana ni tierra; todo el Void debe ser congruente»: paleta.py pasa su isla a roca del Vacío y comprueba al
acabar que no queda nada de eso fuera del santuario.)

Del sur al norte:
  - La isla de la grieta (la del dueño, en roca del Vacío). En su punta norte, donde llega el Puente de Piedra y donde
    estaba el Umbral (quitado: su sitio, terreno otra vez), la Llegada: una plaza pequeña y, a su lado, el Portal del
    Alba (vuelve al santuario quedándose en su luz: VoidPortal).
  - El Puente de Piedra y los Fragmentos del Alba Rota (bioma.py): cuatro tierras grandes, la Herida y el Sendero con
    sus Anclas del Alba (los Pozos de Luz).
  - El Puente de Luz y el Santuario del Alba: la isla del caballero (el dueño: «hazla más grande, que sea bonita, bien
    decorada»), dentro del Velo del Alba (la burbuja que dibuja el cliente). Radio ~34:
      · el templo del centro (doce columnas: la Orden; suelo de cuarzo con rosetón de oro; once velas por los once que se
        hicieron luz; el pedestal de Caelthar) y encima, flotando, el Corazón del Alba (lo dibuja el cliente);
      · al norte, el Altar del Vacío: un ábside con su arco donde el servidor pone el soporte con la armadura del set Void
        (DawnArmor), la única que el Void no corroe;
      · caminos de cuarzo en cruz y en anillo con farolas, bancos y la Puerta del Alba al sur;
      · jardines de cerezos con flores, pétalos y azaleas; el Estanque del Alba (nenúfares y pepinos de mar que brillan);
        el Jardín de Amatista; los Once (faros con su luz) junto al borde, con barandilla;
      · por debajo un cono de calcita, toba y amatista con amatistas y lianas que brillan colgando, y tres islotes dentro
        del Velo.
  - Los Pozos de Luz de la isla de la grieta (10, los del Páramo los pone bioma.py).

Guarda la versión 4 del formato (VoidIsland): además de lo de la 3 (el Velo, el Corazón y los pozos), el altar, el
portal (y hacia dónde mira) y dónde empieza la fauna del Páramo.
"""
import gzip
import math
import os
import struct
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bioma  # noqa: E402

BIN = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'tfclient-void-isla.bin'))

BASE = os.path.join(HERE, 'isla_base.bin')

# El santuario (coordenadas del mundo)
CX, CZ = -64, -722
TOP = 66            # la cara de arriba del suelo (se pisa en 67)
R = 34              # radio de la isla
DEPTH = 46          # lo que baja el cono por debajo
CLEAR_R = 48        # se rehace todo en este radio
BUBBLE = (CX, 72, CZ, 47)        # el Velo del Alba: centro y radio
HEART = (CX, 93, CZ)             # el Corazón del Alba (lo dibuja el cliente)
ALTAR = (CX, TOP + 2, CZ - 22)   # donde está de pie el soporte con la armadura del Vacío (en el suelo del ábside)
BRIDGE_Z0, BRIDGE_Z1 = bioma.NORTH_DOOR[1] + 2, CZ + R - 2   # el Puente de Luz: del Páramo a la Puerta del Alba
# La Llegada (donde estaba el Umbral, en la punta norte de la isla de la grieta): centro y suelo
LANDING = (-64, 66, -161)
# El Portal del Alba, a un lado de la Llegada: el centro de abajo de su hueco (x, y, z) y hacia dónde mira (1 = el arco a
# lo largo de Z, se cruza de este a oeste: mira a la plaza; quien baja del puente hacia el sur no lo cruza)
PORTAL = (-53, 67, -161, 1)
FRAGMENTS = 2       # Anclas del Alba (pozos de luz) en la isla de la grieta (los de los Fragmentos, bioma.py)
TEMPLE_R = 12.5
RING_R = 21


def read_bin(path):
    data = gzip.open(path, 'rb').read()
    o = [4]

    def rd(fmt):
        v = struct.unpack_from('>' + fmt, data, o[0])
        o[0] += struct.calcsize('>' + fmt)
        return v

    ver, = rd('i')
    sx, sy, sz, ox, oy, oz = rd('6i')
    knight = rd('3i')
    arrive = rd('3i')
    yaw, = rd('f')
    rift = rd('3i') if ver >= 2 else (0, 72, 0)
    if ver >= 3:
        rd('7i')
        nf, = rd('i')
        for _ in range(nf):
            rd('3i')
    if ver >= 4:
        rd('3i')
        rd('4i')
        rd('3i')
    n, = rd('i')
    names = []
    for _ in range(n):
        ln, = rd('H')
        names.append(data[o[0]:o[0] + ln].decode('utf-8'))
        o[0] += ln
    g = np.zeros((sy, sz, sx), np.int32)
    for z in range(sz):
        for x in range(sx):
            k, = rd('H')
            for _ in range(k):
                y, ln, b = rd('3H')
                g[y:y + ln, z, x] = b
    return dict(g=g, names=names, ox=ox, oy=oy, oz=oz, knight=knight, arrive=arrive, yaw=yaw, rift=rift)


def write_bin(path, d, bubble, heart, frags, altar=ALTAR, portal=PORTAL, fauna=(0, 0, 0)):
    g = d['g']
    H, L, W = g.shape
    out = bytearray()
    out += b'TFVI' + struct.pack('>i', 4)
    out += struct.pack('>iii', W, H, L)
    out += struct.pack('>iii', d['ox'], d['oy'], d['oz'])
    out += struct.pack('>iii', *d['knight'])
    out += struct.pack('>iiif', *d['arrive'], d['yaw'])
    out += struct.pack('>iii', *d['rift'])
    out += struct.pack('>iiii', *bubble)
    out += struct.pack('>iii', *heart)
    out += struct.pack('>i', len(frags))
    for f in frags:
        out += struct.pack('>iii', *f)
    out += struct.pack('>iii', *altar)
    out += struct.pack('>iiii', *portal)
    out += struct.pack('>iii', *fauna)
    out += struct.pack('>i', len(d['names']))
    for n in d['names']:
        e = n.encode('utf-8')
        out += struct.pack('>H', len(e)) + e
    for z in range(L):
        for x in range(W):
            col = g[:, z, x]
            nz = np.nonzero(col)[0]
            runs = []
            if len(nz):
                y = int(nz[0])
                top = int(nz[-1]) + 1
                while y < top:
                    v = col[y]
                    if v == 0:
                        y += 1
                        continue
                    s = y
                    while y < top and col[y] == v:
                        y += 1
                    runs.append((s, y - s, int(v)))
            out += struct.pack('>H', len(runs))
            for s, n, v in runs:
                out += struct.pack('>HHH', s, n, v)
    data = gzip.compress(bytes(out), 9, mtime=0)
    with open(path, 'wb') as f:
        f.write(data)
    return len(data)


class World:
    """La isla en coordenadas del mundo (pone y quita bloques por nombre, creciendo la caja si hace falta)."""

    def __init__(self, d):
        self.d = d
        self.index = {n: i for i, n in enumerate(d['names'])}

    def pal(self, name):
        if ':' not in name.split('[')[0]:
            name = 'minecraft:' + name
        if name not in self.index:
            self.index[name] = len(self.d['names'])
            self.d['names'].append(name)
        return self.index[name]

    def grow(self, x0, x1, z0, z1):
        d = self.d
        g = d['g']
        H, L, W = g.shape
        nx0, nz0 = min(d['ox'], x0), min(d['oz'], z0)
        nx1, nz1 = max(d['ox'] + W - 1, x1), max(d['oz'] + L - 1, z1)
        if (nx0, nz0, nx1, nz1) == (d['ox'], d['oz'], d['ox'] + W - 1, d['oz'] + L - 1):
            return
        g2 = np.zeros((H, nz1 - nz0 + 1, nx1 - nx0 + 1), np.int32)
        g2[:, d['oz'] - nz0:d['oz'] - nz0 + L, d['ox'] - nx0:d['ox'] - nx0 + W] = g
        d['g'], d['ox'], d['oz'] = g2, nx0, nz0

    def put(self, x, y, z, name):
        d = self.d
        iy, iz, ix = y - d['oy'], z - d['oz'], x - d['ox']
        g = d['g']
        if not (0 <= iy < g.shape[0] and 0 <= iz < g.shape[1] and 0 <= ix < g.shape[2]):
            return
        g[iy, iz, ix] = 0 if name is None else self.pal(name)

    def get(self, x, y, z):
        d = self.d
        g = d['g']
        iy, iz, ix = y - d['oy'], z - d['oz'], x - d['ox']
        if not (0 <= iy < g.shape[0] and 0 <= iz < g.shape[1] and 0 <= ix < g.shape[2]):
            return 'minecraft:air'
        return d['names'][g[iy, iz, ix]]

    def top(self, x, z):
        d = self.d
        g = d['g']
        iz, ix = z - d['oz'], x - d['ox']
        if not (0 <= iz < g.shape[1] and 0 <= ix < g.shape[2]):
            return None
        ys = np.nonzero(g[:, iz, ix])[0]
        return None if len(ys) == 0 else int(ys[-1]) + d['oy']


def hash2(x, z, s=0):
    h = (x * 374761393 + z * 668265263 + s * 1442695041) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) / 0xFFFFFFFF


def noise2(x, z, s=0):
    """Ruido de valor suave (0..1)."""
    xi, zi = math.floor(x), math.floor(z)
    fx, fz = x - xi, z - zi
    fx, fz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a, b = hash2(xi, zi, s), hash2(xi + 1, zi, s)
    c, e = hash2(xi, zi + 1, s), hash2(xi + 1, zi + 1, s)
    return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + e * fx) * fz


def polar(x, z):
    """Ángulo (0 = este, 90 = sur, 180 = oeste, 270 = norte) y radio desde el centro del santuario."""
    return math.degrees(math.atan2(z - CZ, x - CX)) % 360, math.hypot(x - CX, z - CZ)


def ang_dist(a, b):
    d = abs(a - b) % 360
    return min(d, 360 - d)


# ------------------------------------------------------------------------------------------- el santuario

def edge_r(ang):
    a = math.radians(ang)
    return R + 2.6 * (noise2(math.cos(a) * 1.6 + 7, math.sin(a) * 1.6 + 3, 1) - 0.5) * 2 \
        + 1.2 * (noise2(math.cos(a) * 4.5 + 2, math.sin(a) * 4.5 + 9, 7) - 0.5) * 2


def island(w):
    """La isla: suelo de musgo y calcita (y TOP) y un cono por debajo que acaba en punta."""
    w.grow(CX - CLEAR_R, CX + CLEAR_R, CZ - CLEAR_R, CZ + CLEAR_R)
    for x in range(CX - CLEAR_R, CX + CLEAR_R + 1):
        for z in range(CZ - CLEAR_R, CZ + CLEAR_R + 1):
            if math.hypot(x - CX, z - CZ) > CLEAR_R:
                continue
            for y in range(TOP - DEPTH - 12, TOP + 46):
                w.put(x, y, z, None)
    for x in range(CX - R - 4, CX + R + 5):
        for z in range(CZ - R - 4, CZ + R + 5):
            ang, rr = polar(x, z)
            edge = edge_r(ang)
            if rr > edge:
                continue
            k = 1 - rr / edge
            depth = int(3 + (DEPTH - 3) * k ** 1.5 + 6 * (noise2(x * 0.15, z * 0.15, 2) - 0.5) * k)
            for y in range(TOP - depth, TOP + 1):
                from_top = TOP - y
                n = noise2(x * 0.3 + y * 0.21, z * 0.3 - y * 0.17, 3)
                if from_top == 0:
                    blk = 'moss_block' if rr < edge - 2.5 and n > 0.24 else 'calcite'
                elif from_top <= 2:
                    blk = 'calcite' if n > 0.15 else 'moss_block'
                elif y < TOP - depth + 3 and n > 0.6:
                    blk = 'amethyst_block'
                elif n > 0.74:
                    blk = 'tuff'
                elif n < 0.1:
                    blk = 'smooth_basalt'
                else:
                    blk = 'calcite'
                w.put(x, y, z, blk)
            # por debajo: amatistas y alguna cadena (la Orden sujeta su isla)
            bot = TOP - depth
            hh = hash2(x, z, 31)
            if hh < 0.06:
                w.put(x, bot - 1, z, 'amethyst_cluster[facing=down,waterlogged=false]')
            elif hh < 0.068:
                for k2 in range(2 + int(hh * 1000) % 6):
                    w.put(x, bot - 1 - k2, z, 'chain[axis=y,waterlogged=false]')


def free(w, x, z):
    """¿Columna de jardín libre? (musgo o calcita arriba y aire encima)"""
    return w.get(x, TOP, z) in ('minecraft:moss_block', 'minecraft:calcite') and w.get(x, TOP + 1, z) == 'minecraft:air'


def temple(w):
    """El templo: suelo con rosetón, doce columnas (la Orden), cornisa, once velas y el pedestal de Caelthar."""
    for x in range(CX - 14, CX + 15):
        for z in range(CZ - 14, CZ + 15):
            ang, rr = polar(x, z)
            if rr > TEMPLE_R:
                continue
            for y in range(TOP + 1, TOP + 16):
                w.put(x, y, z, None)
            ray = min(ang_dist(ang, k * 45) for k in range(8)) * math.pi / 180 * rr
            if rr > TEMPLE_R - 1:
                blk = 'quartz_bricks'
            elif 6.3 <= rr <= 7.1:
                blk = 'gold_block'
            elif 2.5 < rr < TEMPLE_R - 1 and ray < 0.6:
                blk = 'chiseled_quartz_block'
            elif rr <= 2.5:
                blk = 'smooth_quartz'
            else:
                blk = 'quartz_bricks' if (x + z) % 2 else 'smooth_quartz'
            w.put(x, TOP, z, blk)
    # el pedestal de Caelthar (se pisa en TOP + 2)
    for x in range(CX - 1, CX + 2):
        for z in range(CZ - 1, CZ + 2):
            w.put(x, TOP + 1, z, 'smooth_quartz' if (x, z) != (CX, CZ) else 'gold_block')
    for dx, dz, f in ((0, 2, 'north'), (0, -2, 'south'), (2, 0, 'west'), (-2, 0, 'east')):
        w.put(CX + dx, TOP + 1, CZ + dz, f'smooth_quartz_stairs[facing={f},half=bottom,shape=straight,waterlogged=false]')
    # doce columnas (la Orden del Alba) con capitel de oro y vara del End
    for i in range(12):
        a = math.radians(i * 30 + 15)
        px, pz = round(CX + math.cos(a) * 11), round(CZ + math.sin(a) * 11)
        w.put(px, TOP + 1, pz, 'chiseled_quartz_block')
        for y in range(TOP + 2, TOP + 9):
            w.put(px, y, pz, 'quartz_pillar[axis=y]')
        w.put(px, TOP + 9, pz, 'gold_block')
        w.put(px, TOP + 10, pz, 'white_candle[candles=1,lit=true,waterlogged=false]')
    # la cornisa (anillo encima de los capiteles)
    for x in range(CX - 13, CX + 14):
        for z in range(CZ - 13, CZ + 14):
            _, rr = polar(x, z)
            if 10.3 <= rr <= 11.7 and w.get(x, TOP + 9, z) == 'minecraft:air':
                w.put(x, TOP + 9, z, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
    # once velas: los once que se hicieron luz
    for i in range(11):
        a = math.radians(i * 360 / 11 + 8)
        px, pz = round(CX + math.cos(a) * 7.5), round(CZ + math.sin(a) * 7.5)
        w.put(px, TOP + 1, pz, 'chiseled_quartz_block')
        w.put(px, TOP + 2, pz, 'white_candle[candles=4,lit=true,waterlogged=false]')


def altar(w):
    """El Altar del Vacío (al norte del templo): ábside de cuarzo con su arco; el soporte con la armadura lo pone el
    servidor de pie en ALTAR."""
    ax, ay, az = ALTAR
    for x in range(ax - 7, ax + 8):
        for z in range(az - 7, az + 8):
            d = math.hypot(x - ax, z - az)
            if d > 5.6 or z > az + 5:
                continue
            for y in range(TOP + 1, TOP + 14):
                w.put(x, y, z, None)
            w.put(x, TOP + 1, z, 'quartz_bricks' if d > 4.6 else ('chiseled_quartz_block' if (x + z) % 2 else 'smooth_quartz'))
    # escalones (de sur a norte)
    for dx in range(-2, 3):
        w.put(ax + dx, TOP + 1, az + 6, f'smooth_quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
    # (1.3.97) sin pedestal: el soporte de pie en el suelo, sobre una losa de oro (su aura la dibuja el cliente)
    w.put(ax, TOP + 1, az, 'gold_block')
    for dx in (-2, 2):
        w.put(ax + dx, TOP + 2, az + 1, 'white_candle[candles=3,lit=true,waterlogged=false]')
    # el arco de detrás
    for dx in (-3, 3):
        for y in range(TOP + 2, TOP + 10):
            w.put(ax + dx, y, az - 3, 'quartz_pillar[axis=y]')
    for dx in range(-3, 4):
        w.put(ax + dx, TOP + 10, az - 3, 'chiseled_quartz_block')
    for dx in (-2, 2):
        w.put(ax + dx, TOP + 9, az - 3, 'smooth_quartz_stairs[facing=%s,half=top,shape=straight,waterlogged=false]'
              % ('east' if dx < 0 else 'west'))
    w.put(ax, TOP + 11, az - 3, 'gold_block')
    w.put(ax, TOP + 12, az - 3, 'amethyst_cluster[facing=up,waterlogged=false]')
    w.put(ax, TOP + 9, az - 3, 'chain[axis=y,waterlogged=false]')
    w.put(ax, TOP + 8, az - 3, 'soul_lantern[hanging=true,waterlogged=false]')
    # farolas a los lados
    for dx in (-5, 5):
        w.put(ax + dx, TOP + 2, az + 2, 'quartz_pillar[axis=y]')
        w.put(ax + dx, TOP + 3, az + 2, 'quartz_pillar[axis=y]')
        w.put(ax + dx, TOP + 4, az + 2, 'lantern[hanging=false,waterlogged=false]')


def paths(w):
    """Caminos de cuarzo: en cruz (del templo al borde) y en anillo, con farolas."""
    for x in range(CX - R - 2, CX + R + 3):
        for z in range(CZ - R - 2, CZ + R + 3):
            ang, rr = polar(x, z)
            if rr <= TEMPLE_R or rr > edge_r(ang) - 1.2:
                continue
            on_cross = (abs(x - CX) <= 1 or abs(z - CZ) <= 1)
            on_ring = abs(rr - RING_R) <= 1.2
            if not (on_cross or on_ring):
                continue
            centre = (x == CX or z == CZ) if on_cross else abs(rr - RING_R) <= 0.45
            w.put(x, TOP, z, 'smooth_quartz' if centre else 'quartz_bricks')
            if w.get(x, TOP + 1, z) != 'minecraft:air':
                w.put(x, TOP + 1, z, None)
    # farolas: a los lados de la cruz cada 5 y por fuera del anillo
    for k in range(int(TEMPLE_R) + 3, R - 2, 5):
        for sx, sz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for side in (-2, 2):
                x = CX + sx * k + (side if sx == 0 else 0)
                z = CZ + sz * k + (side if sz == 0 else 0)
                if w.get(x, TOP + 1, z) == 'minecraft:air' and w.get(x, TOP, z) != 'minecraft:air':
                    w.put(x, TOP + 1, z, 'quartz_pillar[axis=y]')
                    w.put(x, TOP + 2, z, 'lantern[hanging=false,waterlogged=false]')
    for i in range(16):
        a = math.radians(i * 22.5 + 11.25)
        x, z = round(CX + math.cos(a) * (RING_R + 2.6)), round(CZ + math.sin(a) * (RING_R + 2.6))
        if free(w, x, z):
            w.put(x, TOP + 1, z, 'quartz_pillar[axis=y]')
            w.put(x, TOP + 2, z, 'lantern[hanging=false,waterlogged=false]')
    # bancos por dentro del anillo, mirando hacia fuera
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        x, z = round(CX + math.cos(a) * (RING_R - 2.4)), round(CZ + math.sin(a) * (RING_R - 2.4))
        dx, dz = math.cos(a), math.sin(a)
        facing = ('west' if dx > 0 else 'east') if abs(dx) > abs(dz) else ('north' if dz > 0 else 'south')
        if free(w, x, z):
            w.put(x, TOP + 1, z, f'quartz_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]')


def pond(w):
    """El Estanque del Alba (al suroeste): nenúfares arriba y pepinos de mar que brillan abajo, con bordillo."""
    a = math.radians(135)
    px, pz = CX + math.cos(a) * 25.5, CZ + math.sin(a) * 25.5
    for x in range(int(px) - 7, int(px) + 8):
        for z in range(int(pz) - 7, int(pz) + 8):
            d = math.hypot(x - px, z - pz) + 0.8 * (noise2(x * 0.4, z * 0.4, 12) - 0.5)
            if d <= 4.8:
                for y in range(TOP + 1, TOP + 4):
                    w.put(x, y, z, None)
                w.put(x, TOP - 2, z, 'calcite')
                w.put(x, TOP - 1, z, 'water[level=0]')
                w.put(x, TOP, z, 'water[level=0]')
                hh = hash2(x, z, 41)
                if hh < 0.18:
                    w.put(x, TOP - 1, z, 'sea_pickle[pickles=4,waterlogged=true]')
                elif hh > 0.82:
                    w.put(x, TOP + 1, z, 'lily_pad')
            elif d <= 5.9:
                w.put(x, TOP, z, 'calcite')
                for y in range(TOP + 1, TOP + 3):
                    w.put(x, y, z, None)
                w.put(x, TOP + 1, z, 'smooth_quartz_slab[type=bottom,waterlogged=false]')


def crystals(w):
    """El Jardín de Amatista (al noreste): calcita, racimos y tres agujas."""
    a = math.radians(315)
    px, pz = CX + math.cos(a) * 25, CZ + math.sin(a) * 25
    for x in range(int(px) - 7, int(px) + 8):
        for z in range(int(pz) - 7, int(pz) + 8):
            d = math.hypot(x - px, z - pz)
            if d > 6 or w.get(x, TOP, z) not in ('minecraft:moss_block', 'minecraft:calcite'):
                continue
            w.put(x, TOP, z, 'amethyst_block' if hash2(x, z, 51) < 0.3 else 'calcite')
            for y in range(TOP + 1, TOP + 4):
                w.put(x, y, z, None)
            hh = hash2(x, z, 52)
            if hh < 0.16:
                w.put(x, TOP + 1, z, 'amethyst_cluster[facing=up,waterlogged=false]')
            elif hh < 0.28:
                w.put(x, TOP + 1, z, 'large_amethyst_bud[facing=up,waterlogged=false]')
    for k, (dx, dz, hgt) in enumerate(((0, 0, 6), (2, -2, 4), (-3, 1, 3))):
        x, z = round(px) + dx, round(pz) + dz
        for y in range(TOP + 1, TOP + 1 + hgt):
            w.put(x, y, z, 'amethyst_block')
        w.put(x, TOP + 1 + hgt, z, 'amethyst_cluster[facing=up,waterlogged=false]')


def gardens(w):
    """Flores, pétalos de cerezo, azaleas en flor y cerezos por los jardines."""
    flowers = ['lily_of_the_valley', 'white_tulip', 'oxeye_daisy', 'azure_bluet', 'allium', 'pink_tulip', 'lily_of_the_valley']
    for x in range(CX - R, CX + R + 1):
        for z in range(CZ - R, CZ + R + 1):
            ang, rr = polar(x, z)
            if rr <= TEMPLE_R + 1 or rr > edge_r(ang) - 2.2 or not free(w, x, z) or w.get(x, TOP, z) != 'minecraft:moss_block':
                continue
            hh = hash2(x, z, 4)
            petals = noise2(x * 0.18, z * 0.18, 61)
            if petals > 0.62 and hh < 0.55:
                w.put(x, TOP + 1, z, 'pink_petals[facing=%s,flower_amount=%d]' % (('north', 'east', 'south', 'west')[int(hh * 40) % 4],
                                                                                 1 + int(hh * 100) % 4))
            elif hh < 0.2:
                w.put(x, TOP + 1, z, flowers[int(hash2(x, z, 5) * len(flowers))])
            elif hh > 0.985:
                w.put(x, TOP + 1, z, 'flowering_azalea')
    # cerezos: por fuera del anillo y algunos por dentro, sin pisar caminos ni nada
    spots = []
    for ang in (20, 52, 108, 160, 200, 232, 340, 70, 250):
        for rad in (27.5, 16.5):
            if (rad == 16.5) != (ang in (70, 250)):
                continue
            a = math.radians(ang)
            spots.append((round(CX + math.cos(a) * rad), round(CZ + math.sin(a) * rad)))
    for i, (x, z) in enumerate(spots):
        cherry(w, x, z, i)


def cherry(w, x, z, seed):
    if not free(w, x, z):
        return
    h = 5 + int(hash2(x, z, seed) * 3)
    w.put(x, TOP, z, 'moss_block')
    for y in range(TOP + 1, TOP + 1 + h):
        w.put(x, y, z, 'cherry_log[axis=y]')
    # dos ramas que salen torcidas
    for k, (dx, dz) in enumerate(((1, 0), (-1, 1))):
        bx, by, bz = x, TOP + h - 1 - k, z
        for s in range(2):
            bx += dx
            bz += dz
            by += s
            w.put(bx, by, bz, 'cherry_log[axis=%s]' % ('x' if dx else 'z'))
    cy = TOP + h
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            for dy in range(-1, 3):
                d = math.sqrt(dx * dx + dz * dz + (dy * 1.7) ** 2)
                if d <= 4.0 and hash2(x + dx, z + dz, dy + 11) > 0.14 and w.get(x + dx, cy + dy, z + dz) == 'minecraft:air':
                    w.put(x + dx, cy + dy, z + dz, 'cherry_leaves[distance=1,persistent=true,waterlogged=false]')
    # pétalos caídos alrededor
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            if 1.5 < math.hypot(dx, dz) <= 4.5 and free(w, x + dx, z + dz) and hash2(x + dx, z + dz, 77) < 0.35:
                w.put(x + dx, TOP + 1, z + dz, 'pink_petals[facing=north,flower_amount=%d]' % (1 + int(hash2(x + dx, z + dz, 78) * 4)))


def once(w):
    """Los Once: faros junto al borde (cuarzo, oro y un sapoluz perlado), uno por caballero que se hizo luz."""
    for i in range(11):
        ang = (i * 360 / 11 + 16) % 360
        if min(ang_dist(ang, c) for c in (0, 90, 180, 270)) < 7:
            ang += 8
        a = math.radians(ang)
        x, z = round(CX + math.cos(a) * 30), round(CZ + math.sin(a) * 30)
        if w.get(x, TOP, z) == 'minecraft:air':
            continue
        for y in range(TOP + 1, TOP + 8):
            w.put(x, y, z, None)
        w.put(x, TOP + 1, z, 'chiseled_quartz_block')
        for y in range(TOP + 2, TOP + 5):
            w.put(x, y, z, 'quartz_pillar[axis=y]')
        w.put(x, TOP + 5, z, 'gold_block')
        w.put(x, TOP + 6, z, 'beacon')
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if free(w, x + dx, z + dz):
                w.put(x + dx, TOP + 1, z + dz, 'white_candle[candles=%d,lit=true,waterlogged=false]' % (1 + (i + dx) % 3))


def edge(w):
    """Barandilla junto al borde (con huecos en los caminos) y farolillos."""
    for x in range(CX - R - 4, CX + R + 5):
        for z in range(CZ - R - 4, CZ + R + 5):
            ang, rr = polar(x, z)
            e = edge_r(ang)
            if not (e - 1.6 < rr <= e - 0.6) or w.get(x, TOP, z) == 'minecraft:air':
                continue
            if abs(x - CX) <= 2 or abs(z - CZ) <= 2:
                continue
            if w.get(x, TOP + 1, z) != 'minecraft:air':
                continue
            w.put(x, TOP + 1, z, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
            if hash2(x, z, 9) < 0.06:
                w.put(x, TOP + 1, z, 'quartz_pillar[axis=y]')
                w.put(x, TOP + 2, z, 'lantern[hanging=false,waterlogged=false]')


def gate(w):
    """La Puerta del Alba (al sur, donde llega el Puente de Luz)."""
    gz = CZ + R - 3
    for dx in (-3, 3):
        for y in range(TOP + 1, TOP + 9):
            w.put(CX + dx, y, gz, 'quartz_pillar[axis=y]')
        w.put(CX + dx, TOP + 9, gz, 'gold_block')
    for dx in range(-3, 4):
        w.put(CX + dx, TOP + 10, gz, 'chiseled_quartz_block')
    w.put(CX, TOP + 11, gz, 'gold_block')
    w.put(CX, TOP + 12, gz, 'white_candle[candles=4,lit=true,waterlogged=false]')
    for dx in (-2, 2):
        w.put(CX + dx, TOP + 9, gz, 'smooth_quartz_stairs[facing=%s,half=top,shape=straight,waterlogged=false]' % ('east' if dx < 0 else 'west'))


def islets(w):
    """Tres islotes dentro del Velo, con un cerezo pequeño o amatistas."""
    for k, (ang, rad, y) in enumerate(((35, 39, 80), (152, 38, 77), (218, 37, 85))):
        a = math.radians(ang)
        cx, cz = round(CX + math.cos(a) * rad), round(CZ + math.sin(a) * rad)
        r = 3.6
        for dx in range(-5, 6):
            for dz in range(-5, 6):
                d = math.hypot(dx, dz)
                if d > r:
                    continue
                depth = int(2 + (r - d) * 1.6)
                for j in range(depth):
                    w.put(cx + dx, y - j, cz + dz, 'moss_block' if j == 0 else ('calcite' if j < 3 else 'amethyst_block'))
                if d < r - 1 and hash2(cx + dx, cz + dz, 90) < 0.25:
                    w.put(cx + dx, y - depth, cz + dz, 'amethyst_cluster[facing=down,waterlogged=false]')
                if d < r - 0.8 and hash2(cx + dx, cz + dz, 91) < 0.3 and w.get(cx + dx, y + 1, cz + dz) == 'minecraft:air':
                    w.put(cx + dx, y + 1, cz + dz, 'lily_of_the_valley' if hash2(cx + dx, cz + dz, 92) < 0.5 else 'pink_petals[facing=east,flower_amount=3]')
        if k == 1:
            for j in range(1, 4):
                w.put(cx, y + j, cz, 'amethyst_block')
            w.put(cx, y + 4, cz, 'amethyst_cluster[facing=up,waterlogged=false]')
        else:
            for j in range(1, 4):
                w.put(cx, y + j, cz, 'cherry_log[axis=y]')
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    for dy in range(0, 2):
                        if math.hypot(dx, dz) + dy <= 2.6 and w.get(cx + dx, y + 4 + dy, cz + dz) == 'minecraft:air':
                            w.put(cx + dx, y + 4 + dy, cz + dz, 'cherry_leaves[distance=1,persistent=true,waterlogged=false]')
        w.put(cx, y + 2, cz + 2, 'light[level=12,waterlogged=false]')


def bridge(w):
    """El Puente de Luz: del Páramo a la Puerta del Alba (5 de ancho: cuarzo, ladrillo de cuarzo y cristal)."""
    lo, hi = min(BRIDGE_Z0, BRIDGE_Z1), max(BRIDGE_Z0, BRIDGE_Z1)
    for z in range(lo, hi + 1):
        for dx in range(-2, 3):
            x = CX + dx
            for y in range(TOP + 1, TOP + 6):
                if 'lantern' not in w.get(x, y, z) and 'quartz_pillar' not in w.get(x, y, z):
                    w.put(x, y, z, None)
            w.put(x, TOP, z, 'smooth_quartz' if dx == 0 else ('quartz_bricks' if abs(dx) == 1 else 'white_stained_glass'))
            if dx == 0 and w.get(x, TOP - 1, z) == 'minecraft:air':
                w.put(x, TOP - 1, z, 'white_stained_glass')
        if z % 4 == 0:
            for dx in (-2, 2):
                w.put(CX + dx, TOP + 1, z, 'quartz_pillar[axis=y]')
                w.put(CX + dx, TOP + 2, z, 'lantern[hanging=false,waterlogged=false]')


def lights(w):
    """Luz invisible por el santuario y el Puente de Luz."""
    for x in range(CX - R, CX + R + 1, 4):
        for z in range(CZ - R, CZ + R + 1, 4):
            ang, rr = polar(x, z)
            if rr <= edge_r(ang) - 1:
                y = TOP + 3
                if w.get(x, y, z) == 'minecraft:air':
                    w.put(x, y, z, 'light[level=15,waterlogged=false]')
    lo, hi = min(BRIDGE_Z0, BRIDGE_Z1), max(BRIDGE_Z0, BRIDGE_Z1)
    for z in range(lo, hi + 1, 5):
        if w.get(CX, TOP + 2, z) == 'minecraft:air':
            w.put(CX, TOP + 2, z, 'light[level=14,waterlogged=false]')


def portal(w):
    """El Portal del Alba en el borde oeste de la plaza de la grieta, mirando a ella (fuera del camino que baja del
    Páramo): un arco de cuarzo y oro; su velo de luz lo dibuja el cliente y el servidor lleva al santuario a quien se
    queda en él un momento (VoidPortal). El hueco: 5 de ancho y 8 de alto. PORTAL[3]: 0, el arco a lo largo de X (se
    cruza yendo de norte a sur); 1, a lo largo de Z (se cruza yendo de este a oeste)."""
    px, py, pz, facing = PORTAL
    along_x = facing % 2 == 0

    def put(a, y, b, block):
        # a: a lo largo del arco; b: a través de él
        if along_x:
            w.put(px + a, y, pz + b, block)
        else:
            w.put(px + b, y, pz + a, block)

    def stairs(toward_minus_a, half):
        # escalón que mira hacia -a (o +a) a lo largo del arco
        if along_x:
            f = 'west' if toward_minus_a else 'east'
        else:
            f = 'north' if toward_minus_a else 'south'
        return 'smooth_quartz_stairs[facing=%s,half=%s,shape=straight,waterlogged=false]' % (f, half)

    def stairs_across(toward_minus_b):
        if along_x:
            f = 'north' if toward_minus_b else 'south'
        else:
            f = 'west' if toward_minus_b else 'east'
        return 'smooth_quartz_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % f

    for a in range(-4, 5):
        for b in range(-2, 3):
            for y in range(py, py + 12):
                put(a, y, b, None)
            put(a, py - 1, b, 'chiseled_quartz_block' if abs(a) <= 2 and b == 0 else 'quartz_bricks')
    for a in (-3, 3):
        put(a, py, 0, 'chiseled_quartz_block')
        for y in range(py + 1, py + 8):
            put(a, y, 0, 'quartz_pillar[axis=y]')
        put(a, py + 8, 0, 'gold_block')
        put(a, py, -1, stairs_across(False))
        put(a, py, 1, stairs_across(True))
    for a in range(-3, 4):
        put(a, py + 9, 0, 'chiseled_quartz_block')
    for a in (-2, 2):
        put(a, py + 8, 0, stairs(a > 0, 'top'))
    put(0, py + 10, 0, 'gold_block')
    put(0, py + 11, 0, 'white_candle[candles=4,lit=true,waterlogged=false]')
    for a in (-4, 4):
        put(a, py, 0, 'lantern[hanging=false,waterlogged=false]')
    put(0, py + 8, 0, 'light[level=15,waterlogged=false]')


# ------------------------------------------------------------------------------------------- la Llegada (el Umbral, fuera)

def llegada(w):
    """(1.3.97, el dueño: «ese portal debe estar donde conecta el puente del bioma con la isla principal, donde era el
    santuario antes; quita esa estructura, mejora el terreno») Quita el Umbral (el santuario de la 1.3.89, un círculo de
    cuarzo y pizarra en la punta norte) y hace terreno otra vez: hasta la Llegada (una plaza pequeña de pizarra al nivel
    del Puente de Piedra) baja suave al suelo de alrededor. El portal (portal()) va a su lado."""
    lx, ly, lz = LANDING
    rad, outer = 7.5, 15
    # el suelo de alrededor (a 16-18 bloques, en cada dirección): lo que había antes del Umbral
    ring = {}
    for k in range(32):
        a = 2 * math.pi * k / 32
        tops = []
        for rr in (16, 17, 18):
            x, z = round(lx + math.cos(a) * rr), round(lz + math.sin(a) * rr)
            t = w.top(x, z)
            if t is not None and t > 50:
                tops.append(t)
        ring[k] = sorted(tops)[len(tops) // 2] if tops else None
    for x in range(lx - outer, lx + outer + 1):
        for z in range(lz - outer, lz + outer + 1):
            d = math.hypot(x - lx, z - lz)
            if d > outer:
                continue
            col_has = any(w.get(x, y, z) != 'minecraft:air' for y in range(40, 62))
            for y in range(62, 100):
                w.put(x, y, z, None)
            if not col_has and d > rad + 1:
                continue
            k = int(round((math.atan2(z - lz, x - lx) % (2 * math.pi)) / (2 * math.pi) * 32)) % 32
            far = ring.get(k) or ly
            t = ly if d <= rad else round(ly + (far - ly) * smooth01((d - rad) / (outer - rad)))
            for y in range(56, t):
                w.put(x, y, z, 'deepslate' if hash2(x, z, y) < 0.6 else 'tuff')
            if d <= rad:
                if d <= 1.5:
                    blk = 'chiseled_deepslate'
                elif d <= rad - 1.2:
                    blk = 'polished_deepslate' if (x + z) % 2 else 'deepslate_tiles'
                else:
                    blk = 'deepslate_bricks'
            else:
                hh = hash2(x, z, 5)
                blk = 'smooth_basalt' if hh < 0.35 else ('tuff' if hh < 0.97 else 'calcite')
            w.put(x, t, z, blk)
    # farolas de alma alrededor de la plaza (no al norte, por donde se llega, ni al este, donde está el portal)
    for ang in (135, 180, 225, 315, 45):
        a = math.radians(ang)
        x, z = round(lx + math.cos(a) * (rad + 0.6)), round(lz + math.sin(a) * (rad + 0.6))
        if 250 < ang < 290:
            continue
        w.put(x, ly + 1, z, 'deepslate_brick_wall')
        w.put(x, ly + 2, z, 'deepslate_brick_wall')
        w.put(x, ly + 3, z, 'soul_lantern[hanging=false,waterlogged=false]')


def smooth01(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


def anchor_floor(w, x, lv, z):
    """El suelo de un Ancla del Alba (como las de bioma.py) sobre el terreno que haya: cuarzo con oro, velas, cuatro
    estelas y la luz."""
    for dx in range(-5, 6):
        for dz in range(-5, 6):
            d = math.hypot(dx, dz)
            if d > 4.6:
                continue
            t = w.top(x + dx, z + dz)
            if t is None or abs(t - lv) > 4:
                continue
            for y in range(min(t, lv) + 1, lv):
                w.put(x + dx, y, z + dz, 'tuff')
            for y in range(lv + 1, max(t, lv) + 6):
                w.put(x + dx, y, z + dz, None)
            blk = 'gold_block' if d <= 1.1 else ('smooth_quartz' if d <= 2.2 else (
                ('chiseled_quartz_block' if (dx + dz) % 2 == 0 else 'quartz_bricks') if d <= 3.4 else 'calcite'))
            w.put(x + dx, lv, z + dz, blk)
    for dx, dz in ((3, 0), (-3, 0), (0, 3), (0, -3)):
        w.put(x + dx, lv + 1, z + dz, WELL_CANDLE.replace('minecraft:', ''))
    for dx, dz in ((4, 4), (-4, 4), (4, -4), (-4, -4)):
        t = w.top(x + dx, z + dz)
        if t is None or abs(t - lv) > 4:
            continue
        w.put(x + dx, t + 1, z + dz, 'quartz_pillar[axis=y]')
        w.put(x + dx, t + 2, z + dz, 'chiseled_quartz_block')
        w.put(x + dx, t + 3, z + dz, 'white_candle[candles=1,lit=true,waterlogged=false]')
    w.put(x, lv + 3, z, FRAG_LIGHT)


# ------------------------------------------------------------------------------------------- los pozos de la isla grande

TERRAIN = ('deepslate', 'tuff', 'basalt', 'blackstone', 'calcite', 'obsidian', 'amethyst')
# (la luz que va encima de cada pozo: su nivel, 11, solo lo usan ellos; y sus velas, las de color natural)
FRAG_LIGHT = 'minecraft:light[level=11,waterlogged=false]'
WELL_CANDLE = 'minecraft:candle[candles=4,lit=true,waterlogged=false]'


def fragments(w, rift):
    """Los Pozos de Luz de la isla de la grieta: en el terreno (con aire encima), lejos del borde, de la plaza, del
    portal, del Umbral y entre sí; el más alejado posible de los ya elegidos, uno a uno. (Los del Páramo, bioma.py.)"""
    d = w.d
    g = d['g']
    H, L, W = g.shape
    # (las luces y las velas de los pozos de una pasada anterior se quitan, y ninguna luz cuenta como suelo: se puede
    # volver a pasar y salen los mismos sitios)
    for name in (FRAG_LIGHT, WELL_CANDLE):
        g[g == w.pal(name)] = 0
    is_light = np.array([n.startswith('minecraft:light') for n in d['names']])
    z_lo = -165 - d['oz']   # solo la isla de la grieta (del Umbral hacia el sur)
    solid = np.zeros((L, W), bool)
    tops = np.full((L, W), -1, int)
    for iz in range(max(0, z_lo), L):
        for ix in range(W):
            col = g[:, iz, ix]
            ys = np.nonzero((col != 0) & ~is_light[col])[0]
            if len(ys):
                tops[iz, ix] = ys[-1]
                name = d['names'][g[ys[-1], iz, ix]]
                solid[iz, ix] = any(t in name for t in TERRAIN)
    ground = tops >= 0
    dist = np.where(ground, 99, 0).astype(float)
    for _ in range(12):
        sh = np.minimum.reduce([np.roll(dist, 1, 0), np.roll(dist, -1, 0), np.roll(dist, 1, 1), np.roll(dist, -1, 1)]) + 1
        dist = np.where(ground, np.minimum(dist, sh), 0)
    cand = []
    for iz in range(max(0, z_lo), L, 2):
        for ix in range(0, W, 2):
            if not solid[iz, ix] or dist[iz, ix] < 9:
                continue
            x, z = ix + d['ox'], iz + d['oz']
            if math.hypot(x - rift[0], z - rift[2]) < 40 or math.hypot(x - LANDING[0], z - LANDING[2]) < 45:
                continue
            if math.hypot(x - PORTAL[0], z - PORTAL[2]) < 30:
                continue
            y = tops[iz, ix] + d['oy']
            if any(g[min(H - 1, tops[iz, ix] + k), iz, ix] != 0 and not is_light[g[min(H - 1, tops[iz, ix] + k), iz, ix]] for k in range(1, 5)):
                continue
            cand.append((x, y + 2, z))
    # (1.3.97: pocas; lejos de la Llegada, de la grieta y entre sí: repartidas por el camino de una a otra)
    fixed = [(LANDING[0], 0, LANDING[2]), (rift[0], 0, rift[2])]
    chosen = list(fixed)
    bd = 0
    while len(chosen) < FRAGMENTS + len(fixed) and len(chosen) < len(cand):
        best, bd = None, -1
        for c in cand:
            m = min(math.hypot(c[0] - p[0], c[2] - p[2]) for p in chosen)
            if m > bd:
                bd, best = m, c
        chosen.append(best)
    chosen = chosen[len(fixed):]
    for x, y, z in chosen:
        anchor_floor(w, x, y - 2, z)
    return chosen, bd


def main_ground(w):
    """Las columnas de la isla de la grieta (para que el Páramo no se le pegue): con suelo, del Umbral hacia el sur
    (z > -198), sin el Puente de Luz de antes."""
    d = w.d
    g = d['g']
    xs = np.arange(bioma.X0, bioma.X1 + 1)
    zs = np.arange(bioma.Z0, bioma.Z1 + 1)
    X, Z = np.meshgrid(xs, zs)
    out = np.zeros(X.shape, bool)
    for iz in range(X.shape[0]):
        z = zs[iz]
        if z <= -198:
            continue
        for ix in range(X.shape[1]):
            x = xs[ix]
            if abs(x + 64) <= 3:
                continue
            gx, gz = x - d['ox'], z - d['oz']
            if 0 <= gx < g.shape[2] and 0 <= gz < g.shape[1] and g[:, gz, gx].any():
                out[iz, ix] = True
    return out


def apply(d):
    import paleta
    w = World(d)
    # (1.3.97) la isla del dueño, a roca del Vacío; el Umbral fuera y la Llegada en su sitio
    paleta.repaletar(w)
    llegada(w)
    frags, spacing = fragments(w, d['rift'])
    portal(w)
    biome = bioma.Biome(w)
    biome_wells, fauna = biome.run(main_ground(w), FRAG_LIGHT)
    island(w)
    temple(w)
    altar(w)
    paths(w)
    pond(w)
    crystals(w)
    gardens(w)
    once(w)
    edge(w)
    gate(w)
    islets(w)
    bridge(w)
    lights(w)
    d['knight'] = (CX, TOP + 2, CZ)
    # se llega dentro de la Puerta del Alba, mirando al templo y a Caelthar (al norte)
    d['arrive'] = (CX, TOP + 1, CZ + R - 6)
    d['yaw'] = 180.0
    # nada del End, ni lana, ni tierra... fuera del santuario (dentro del Velo vive lo último del Alba)
    malos = paleta.revisar(d, (CX, CZ, BUBBLE[3] + 2))
    if malos:
        raise SystemExit('quedan bloques que no van en el Void: %s' % malos)
    return frags + biome_wells, spacing, fauna, biome


def main(path=BIN, base=BASE):
    d = read_bin(base)
    frags, spacing, fauna, biome = apply(d)
    size = write_bin(path, d, BUBBLE, HEART, frags, ALTAR, PORTAL, fauna)
    g = d['g']
    print('Void: caja %dx%dx%d desde (%d, %d, %d), %d estados, %.1f kB; caballero %s, llegada %s; %d pozos '
          '(%d en el Páramo; en la isla de la grieta a %.0f bloques como poco); fauna desde %s' % (
              g.shape[2], g.shape[0], g.shape[1], d['ox'], d['oy'], d['oz'], len(d['names']), size / 1024, d['knight'],
              d['arrive'], len(frags), len(biome.wells), spacing, fauna))
    if '--preview' in sys.argv:
        preview(d, frags, fauna, sys.argv[sys.argv.index('--preview') + 1])


COLORS = {'moss_block': (90, 130, 60), 'calcite': (222, 224, 220), 'smooth_quartz': (240, 236, 228), 'quartz_bricks': (232, 228, 220),
          'chiseled_quartz_block': (210, 205, 195), 'gold_block': (240, 200, 60), 'cherry_leaves': (240, 170, 200),
          'cherry_log': (90, 50, 60), 'white_stained_glass': (200, 220, 240), 'quartz_pillar': (235, 230, 220),
          'amethyst_block': (145, 98, 200), 'budding_amethyst': (130, 86, 190), 'tuff': (108, 109, 100), 'smooth_basalt': (70, 68, 76),
          'obsidian': (22, 16, 36), 'crying_obsidian': (70, 22, 125), 'blackstone': (44, 38, 46), 'polished_blackstone': (54, 50, 58),
          'basalt': (82, 82, 88), 'deepslate': (78, 78, 82), 'cobbled_deepslate': (82, 82, 86), 'polished_deepslate': (68, 68, 72),
          'deepslate_tiles': (54, 54, 58), 'cracked_deepslate_tiles': (50, 50, 54), 'deepslate_bricks': (66, 66, 68),
          'cracked_deepslate_bricks': (62, 62, 64), 'chiseled_deepslate': (58, 58, 62), 'polished_blackstone_bricks': (55, 50, 60),
          'cracked_polished_blackstone_bricks': (50, 46, 55), 'chiseled_polished_blackstone': (65, 60, 70), 'chain': (70, 72, 82),
          'soul_lantern': (90, 220, 230), 'lantern': (250, 200, 100), 'water': (60, 110, 200), 'lily_pad': (40, 140, 50),
          'pink_petals': (245, 160, 200), 'candle': (230, 210, 160), 'white_candle': (250, 250, 250), 'beacon': (120, 230, 230),
          'amethyst_cluster': (190, 140, 240), 'large_amethyst_bud': (180, 130, 230), 'medium_amethyst_bud': (175, 125, 225),
          'small_amethyst_bud': (170, 120, 220), 'deepslate_brick_wall': (66, 66, 68), 'purple_stained_glass': (120, 50, 170)}


def color(n):
    import zlib
    n = n.replace('minecraft:', '').split('[')[0]
    if n in COLORS:
        return COLORS[n]
    if n in ('lily_of_the_valley', 'white_tulip', 'oxeye_daisy', 'azure_bluet'):
        return (250, 250, 250)
    h = zlib.crc32(n.encode()) & 0xffffff
    return ((h >> 16) & 255) // 2 + 60, ((h >> 8) & 255) // 2 + 60, (h & 255) // 2 + 60


def preview(d, frags, fauna, out):
    """Desde arriba (todo el norte: el Páramo, el santuario y el portal; con sombreado de relieve) y el santuario de cerca."""
    from PIL import Image, ImageDraw
    os.makedirs(out, exist_ok=True)
    g, names = d['g'], d['names']
    H, L, W = g.shape
    is_light = np.array([n.startswith('minecraft:light') for n in names])

    def topmap(x0, x1, z0, z1):
        tops = np.full((z1 - z0, x1 - x0), -1)
        cols = np.zeros((z1 - z0, x1 - x0, 3))
        for z in range(z0, z1):
            for x in range(x0, x1):
                ix, iz = x - d['ox'], z - d['oz']
                if not (0 <= ix < W and 0 <= iz < L):
                    continue
                col = g[:, iz, ix]
                ys = np.nonzero((col != 0) & ~is_light[col])[0]
                if not len(ys):
                    continue
                tops[z - z0, x - x0] = ys[-1]
                cols[z - z0, x - x0] = color(names[col[ys[-1]]])
        # relieve: luz del noroeste
        t = np.where(tops < 0, np.nan, tops.astype(float))
        gz, gx = np.gradient(np.nan_to_num(t, nan=np.nanmin(t) if np.isfinite(np.nanmin(t)) else 0))
        shade = np.clip(1 - 0.12 * (gx + gz), 0.55, 1.35)
        img = cols * shade[..., None] * (0.75 + 0.25 * np.clip((np.nan_to_num(t, nan=40) - 40) / 50, 0, 1))[..., None]
        img[tops < 0] = (8, 4, 16)
        return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))

    x0, x1, z0, z1 = bioma.X0 - 4, bioma.X1 + 4, CZ - CLEAR_R, 40
    img = topmap(x0, x1, z0, z1).resize(((x1 - x0) * 3, (z1 - z0) * 3), Image.NEAREST)
    dr = ImageDraw.Draw(img)
    for f in frags:
        px, pz = (f[0] - x0) * 3, (f[2] - z0) * 3
        dr.ellipse([px - 6, pz - 6, px + 6, pz + 6], outline=(255, 230, 120), width=2)
    bx, by, bz, br = BUBBLE
    dr.ellipse([(bx - br - x0) * 3, (bz - br - z0) * 3, (bx + br - x0) * 3, (bz + br - z0) * 3], outline=(255, 230, 140), width=2)
    for p, c in ((d['knight'], (255, 0, 0)), (d['arrive'], (0, 255, 0)), (ALTAR, (255, 0, 255)), (PORTAL, (0, 255, 255)),
                 (fauna, (255, 120, 0))):
        px, pz = (p[0] - x0) * 3, (p[2] - z0) * 3
        dr.ellipse([px - 7, pz - 7, px + 7, pz + 7], outline=c, width=3)
    img.save(os.path.join(out, 'norte_arriba.png'))
    s = topmap(CX - CLEAR_R, CX + CLEAR_R, CZ - CLEAR_R, CZ + CLEAR_R).resize((CLEAR_R * 2 * 6, CLEAR_R * 2 * 6), Image.NEAREST)
    s.save(os.path.join(out, 'santuario_arriba.png'))


if __name__ == '__main__':
    main()
