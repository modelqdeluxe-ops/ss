"""El Santuario del Alba (1.3.96): la isla del caballero, aparte de la principal, dentro de una burbuja de luz.

    python3 -I tools/void/isla_alba.py [--preview carpeta]

El dueño: «quiero que hagas una isla santuario en donde pondrás al caballero sacro, aparte de la principal pero no
lejos; estará encerrada en una especie de burbuja brillante que contiene la luz y nos mantiene a salvo de esa corrosión;
inventa algo chingón». Se añade a la isla del Void ya hecha (src/main/resources/tfclient-void-isla.bin; también lo
llama isla.py al acabar, así que no hace falta el mapa del dueño), y se puede volver a pasar (rehace su zona):

  - La isla, 60 bloques al norte del santuario viejo (que queda como «el Umbral», la cabeza del puente): flotante,
    redonda (radio ~21) con el suelo a la altura del puente (y 66) y por debajo un cono de calcita, toba y amatista
    que acaba en punta.
  - Arriba, el Jardín del Alba: musgo con flores blancas, tres cerezos, caminos de cuarzo y un bordillo con varas del
    End. En el centro, el templo: estrado de cuarzo con un anillo cincelado, ocho columnas con capitel de oro y un
    anillo de cornisa (sin techo: encima flota el Corazón del Alba, que dibuja el cliente) y el pedestal del caballero.
  - El Puente de Luz: de la puerta norte del santuario viejo a la isla (5 de ancho: cuarzo, cristal y varas del End).
  - Luz invisible (bloques «light») por todo el jardín.
  - Los fragmentos de luz: 28 sitios repartidos por la isla grande (por donde se anda, lejos del borde, de la plaza de
    la grieta y entre sí), con una luz invisible encima. Ahí los pone el servidor (LightShards).

Guarda la versión 3 del formato (VoidIsland): además, el centro y el radio de la burbuja (el Velo del Alba), dónde
flota el Corazón y los sitios de los fragmentos. El caballero va al pedestal del templo y se llega al camino del sur,
mirándole.
"""
import gzip
import math
import os
import struct
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
BIN = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'tfclient-void-isla.bin'))

# La isla nueva (coordenadas del mundo)
CX, CZ = -64, -224
TOP = 66            # la cara de arriba del suelo (se pisa en 67)
R = 21              # radio del jardín
DEPTH = 34          # lo que baja el cono por debajo
BUBBLE = (CX, 72, CZ, 31)        # el Velo del Alba: centro y radio
HEART = (CX, 85, CZ)             # el Corazón del Alba (lo dibuja el cliente)
BRIDGE_Z0, BRIDGE_Z1 = -169, -203  # de la puerta norte del santuario viejo al jardín
FRAGMENTS = 28


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


def write_bin(path, d, bubble, heart, frags):
    g = d['g']
    H, L, W = g.shape
    out = bytearray()
    out += b'TFVI' + struct.pack('>i', 3)
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
        d['g'][y - d['oy'], z - d['oz'], x - d['ox']] = 0 if name is None else self.pal(name)

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


def island(w):
    """La isla: jardín arriba (y TOP) y cono por debajo."""
    w.grow(CX - R - 6, CX + R + 6, CZ - R - 6, CZ + R + 6)
    # (se rehace su zona entera: se puede volver a pasar)
    for x in range(CX - R - 6, CX + R + 7):
        for z in range(CZ - R - 6, CZ + R + 7):
            for y in range(TOP - DEPTH - 6, TOP + 22):
                w.put(x, y, z, None)
    for x in range(CX - R - 4, CX + R + 5):
        for z in range(CZ - R - 4, CZ + R + 5):
            dx, dz = x - CX, z - CZ
            ang = math.atan2(dz, dx)
            # (el borde: ruido suave a dos escalas alrededor, para que no salga un polígono)
            edge = R + 2.4 * (noise2(math.cos(ang) * 1.6 + 7, math.sin(ang) * 1.6 + 3, 1) - 0.5) * 2 \
                + 1.1 * (noise2(math.cos(ang) * 4.5 + 2, math.sin(ang) * 4.5 + 9, 7) - 0.5) * 2
            rr = math.hypot(dx, dz)
            if rr > edge:
                continue
            # el cono: cuanto más al centro, más baja (con ruido y una punta)
            k = 1 - rr / edge
            depth = int(3 + (DEPTH - 3) * k ** 1.6 + 4 * (noise2(x * 0.18, z * 0.18, 2) - 0.5) * k)
            for y in range(TOP - depth, TOP + 1):
                from_top = TOP - y
                n = noise2(x * 0.3 + y * 0.21, z * 0.3 - y * 0.17, 3)
                if from_top == 0:
                    blk = 'moss_block' if rr < edge - 2.5 and n > 0.28 else 'calcite'
                elif from_top <= 2:
                    blk = 'calcite' if n > 0.15 else 'moss_block'
                elif y < TOP - depth + 3 and n > 0.62:
                    blk = 'amethyst_block'
                elif n > 0.72:
                    blk = 'tuff'
                elif n < 0.12:
                    blk = 'smooth_basalt'
                else:
                    blk = 'calcite'
                w.put(x, y, z, blk)
            # bordillo con varas del End
            if edge - 1.6 < rr <= edge - 0.6:
                w.put(x, TOP + 1, z, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
                if hash2(x, z, 9) < 0.07:
                    w.put(x, TOP + 1, z, 'quartz_pillar[axis=y]')
                    w.put(x, TOP + 2, z, 'end_rod[facing=up]')


def flowers(w):
    """Flores blancas sobre el musgo y tres cerezos alrededor del templo."""
    choices = ['lily_of_the_valley', 'white_tulip', 'oxeye_daisy', 'azure_bluet', 'lily_of_the_valley']
    for x in range(CX - R, CX + R + 1):
        for z in range(CZ - R, CZ + R + 1):
            rr = math.hypot(x - CX, z - CZ)
            if rr < 11 or rr > R - 2:
                continue
            if w.get(x, TOP, z) == 'minecraft:moss_block' and w.get(x, TOP + 1, z) == 'minecraft:air' and hash2(x, z, 4) < 0.22:
                w.put(x, TOP + 1, z, choices[int(hash2(x, z, 5) * len(choices))])
    for i, a in enumerate((0.9, 3.0, 5.1)):
        tx, tz = round(CX + math.cos(a) * 15), round(CZ + math.sin(a) * 15)
        cherry(w, tx, tz, i)


def cherry(w, x, z, seed):
    h = 5 + int(hash2(x, z, seed) * 2)
    w.put(x, TOP, z, 'moss_block')
    for y in range(TOP + 1, TOP + 1 + h):
        w.put(x, y, z, 'cherry_log[axis=y]')
    cy = TOP + h
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(-1, 3):
                d = math.sqrt(dx * dx + dz * dz * 1.0 + (dy * 1.6) ** 2)
                if d <= 3.3 and hash2(x + dx, z + dz, dy + 11) > 0.12 and w.get(x + dx, cy + dy, z + dz) == 'minecraft:air':
                    w.put(x + dx, cy + dy, z + dz, 'cherry_leaves[distance=1,persistent=true,waterlogged=false]')


def temple(w):
    """El templo del centro: estrado, columnas con capitel de oro, cornisa y el pedestal del caballero."""
    for x in range(CX - 10, CX + 11):
        for z in range(CZ - 10, CZ + 11):
            rr = math.hypot(x - CX, z - CZ)
            # (sin flores ni nada encima dentro del templo)
            for y in range(TOP + 1, TOP + 12):
                if rr <= 10.4:
                    w.put(x, y, z, None)
            if rr <= 10.4:
                w.put(x, TOP, z, 'smooth_quartz')
            if 6.6 <= rr <= 7.4:
                w.put(x, TOP, z, 'chiseled_quartz_block')
            if rr <= 5.2:
                w.put(x, TOP + 1, z, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
    # el pedestal del caballero (se pisa en TOP + 2)
    for x in range(CX - 1, CX + 2):
        for z in range(CZ - 1, CZ + 2):
            w.put(x, TOP + 1, z, 'smooth_quartz' if (x, z) != (CX, CZ) else 'gold_block')
    # ocho columnas
    for i in range(8):
        a = i * math.pi / 4 + math.pi / 8
        px, pz = round(CX + math.cos(a) * 8.5), round(CZ + math.sin(a) * 8.5)
        for y in range(TOP + 1, TOP + 7):
            w.put(px, y, pz, 'quartz_pillar[axis=y]')
        w.put(px, TOP + 7, pz, 'gold_block')
        w.put(px, TOP + 8, pz, 'end_rod[facing=up]')
    # el anillo de cornisa (encima de los capiteles)
    for x in range(CX - 10, CX + 11):
        for z in range(CZ - 10, CZ + 11):
            rr = math.hypot(x - CX, z - CZ)
            if 7.8 <= rr <= 9.2 and w.get(x, TOP + 7, z) == 'minecraft:air':
                w.put(x, TOP + 7, z, 'smooth_quartz_slab[type=top,waterlogged=false]')


def paths(w):
    """Camino de cuarzo del puente al templo."""
    for z in range(CZ + 10, CZ + R + 4):
        for x in range(CX - 1, CX + 2):
            if w.get(x, TOP, z) != 'minecraft:air':
                w.put(x, TOP, z, 'smooth_quartz' if x == CX else 'quartz_bricks')
                if w.get(x, TOP + 1, z) not in ('minecraft:air',) and 'end_rod' not in w.get(x, TOP + 1, z):
                    w.put(x, TOP + 1, z, None)


def bridge(w):
    """El Puente de Luz: 5 de ancho (cuarzo en medio, cristal a los lados y varas del End en los bordes)."""
    for z in range(BRIDGE_Z1, BRIDGE_Z0 + 1):
        for dx in range(-2, 3):
            x = CX + dx
            # (por donde pasa, nada encima)
            for y in range(TOP + 1, TOP + 6):
                if 'end_rod' not in w.get(x, y, z) or abs(dx) == 2:
                    w.put(x, y, z, None)
            w.put(x, TOP, z, 'smooth_quartz' if dx == 0 else ('quartz_bricks' if abs(dx) == 1 else 'white_stained_glass'))
            # debajo, un nervio de cristal (que se vea desde abajo)
            if dx == 0 and w.get(x, TOP - 1, z) == 'minecraft:air':
                w.put(x, TOP - 1, z, 'white_stained_glass')
        if z % 4 == 0:
            for dx in (-2, 2):
                w.put(CX + dx, TOP + 1, z, 'quartz_pillar[axis=y]')
                w.put(CX + dx, TOP + 2, z, 'end_rod[facing=up]')


def lights(w):
    """Luz invisible por el jardín, el templo y el puente."""
    for x in range(CX - R, CX + R + 1, 4):
        for z in range(CZ - R, CZ + R + 1, 4):
            if math.hypot(x - CX, z - CZ) <= R - 1:
                y = TOP + 3
                if w.get(x, y, z) == 'minecraft:air':
                    w.put(x, y, z, 'light[level=15,waterlogged=false]')
    for z in range(BRIDGE_Z1, BRIDGE_Z0 + 1, 5):
        if w.get(CX, TOP + 2, z) == 'minecraft:air':
            w.put(CX, TOP + 2, z, 'light[level=14,waterlogged=false]')


TERRAIN = ('wool', 'terracotta', 'clay', 'end_stone', 'mycelium', 'stone')
# (la luz que va encima de cada fragmento: su nivel, 11, solo lo usan ellos)
FRAG_LIGHT = 'minecraft:light[level=11,waterlogged=false]'


def fragments(w, rift):
    """Los sitios de los fragmentos: por la isla grande, en el terreno (con aire encima), lejos del borde, de la plaza y
    entre sí (y del santuario viejo); el más alejado posible de los ya elegidos, uno a uno."""
    d = w.d
    g = d['g']
    H, L, W = g.shape
    # (las luces de los fragmentos de una pasada anterior se quitan, y ninguna luz cuenta como suelo: se puede volver a
    # pasar y salen los mismos sitios)
    frag_light = w.pal(FRAG_LIGHT)
    g[g == frag_light] = 0
    is_light = np.array([n.startswith('minecraft:light') for n in d['names']])
    solid = np.zeros((L, W), bool)
    tops = np.full((L, W), -1, int)
    for iz in range(L):
        for ix in range(W):
            col = g[:, iz, ix]
            ys = np.nonzero((col != 0) & ~is_light[col])[0]
            if len(ys):
                tops[iz, ix] = ys[-1]
                name = d['names'][g[ys[-1], iz, ix]]
                solid[iz, ix] = any(t in name for t in TERRAIN)
    # lejos del borde: distancia a la columna vacía más cercana (por capas)
    ground = tops >= 0
    dist = np.where(ground, 99, 0).astype(float)
    for _ in range(12):
        sh = np.minimum.reduce([np.roll(dist, 1, 0), np.roll(dist, -1, 0), np.roll(dist, 1, 1), np.roll(dist, -1, 1)]) + 1
        dist = np.where(ground, np.minimum(dist, sh), 0)
    cand = []
    for iz in range(0, L, 2):
        for ix in range(0, W, 2):
            if not solid[iz, ix] or dist[iz, ix] < 9:
                continue
            x, z = ix + d['ox'], iz + d['oz']
            if math.hypot(x - rift[0], z - rift[2]) < 34 or math.hypot(x - (-64), z - (-160)) < 22:
                continue
            if math.hypot(x - CX, z - CZ) < R + 12:
                continue
            y = tops[iz, ix] + d['oy']
            if any(g[min(H - 1, tops[iz, ix] + k), iz, ix] != 0 and not is_light[g[min(H - 1, tops[iz, ix] + k), iz, ix]] for k in range(1, 5)):
                continue
            cand.append((x, y + 2, z))
    rnd = [hash2(c[0], c[2], 21) for c in cand]
    chosen = [cand[int(np.argmax(rnd))]]
    while len(chosen) < FRAGMENTS and len(chosen) < len(cand):
        best, bd = None, -1
        for c in cand:
            m = min(math.hypot(c[0] - p[0], c[2] - p[2]) for p in chosen)
            if m > bd:
                bd, best = m, c
        chosen.append(best)
    for x, y, z in chosen:
        if w.get(x, y + 1, z) == 'minecraft:air':
            w.put(x, y + 1, z, FRAG_LIGHT)
    return chosen, bd


def apply(d):
    w = World(d)
    island(w)
    temple(w)
    flowers(w)
    paths(w)
    bridge(w)
    lights(w)
    frags, spacing = fragments(w, d['rift'])
    d['knight'] = (CX, TOP + 2, CZ)
    # se llega al camino del sur del templo, mirando al caballero (al norte)
    d['arrive'] = (CX, TOP + 1, CZ + 15)
    d['yaw'] = 180.0
    return frags, spacing


def main(path=BIN):
    d = read_bin(path)
    frags, spacing = apply(d)
    size = write_bin(path, d, BUBBLE, HEART, frags)
    g = d['g']
    print('isla del Alba: caja %dx%dx%d desde (%d, %d, %d), %d estados, %.1f kB; caballero %s, llegada %s; %d fragmentos '
          '(a %.0f bloques como poco)' % (g.shape[2], g.shape[0], g.shape[1], d['ox'], d['oy'], d['oz'], len(d['names']),
                                           size / 1024, d['knight'], d['arrive'], len(frags), spacing))
    if '--preview' in sys.argv:
        preview(d, frags, sys.argv[sys.argv.index('--preview') + 1])


def preview(d, frags, out):
    """Desde arriba (la zona del santuario y la isla entera con los fragmentos) y de lado (corte norte-sur)."""
    from PIL import Image, ImageDraw
    import zlib
    os.makedirs(out, exist_ok=True)
    g, names = d['g'], d['names']

    def color(n):
        n = n.replace('minecraft:', '').split('[')[0]
        fixed = {'moss_block': (90, 130, 60), 'calcite': (225, 225, 220), 'smooth_quartz': (240, 236, 228),
                 'quartz_bricks': (232, 228, 220), 'chiseled_quartz_block': (210, 205, 195), 'gold_block': (240, 200, 60),
                 'cherry_leaves': (240, 170, 200), 'cherry_log': (90, 50, 60), 'white_stained_glass': (200, 220, 240),
                 'quartz_pillar': (235, 230, 220), 'end_rod': (255, 255, 230), 'amethyst_block': (150, 100, 200),
                 'tuff': (110, 110, 100), 'smooth_basalt': (60, 60, 65), 'light': (255, 255, 120)}
        if n in fixed:
            return fixed[n]
        if n in ('lily_of_the_valley', 'white_tulip', 'oxeye_daisy', 'azure_bluet'):
            return (250, 250, 250)
        h = zlib.crc32(n.encode()) & 0xffffff
        return ((h >> 16) & 255) // 2 + 60, ((h >> 8) & 255) // 2 + 60, (h & 255) // 2 + 60

    H, L, W = g.shape
    k = 6
    x0, x1, z0, z1 = CX - 40, CX + 40, CZ - 32, -150
    img = Image.new('RGB', ((x1 - x0) * k, (z1 - z0) * k))
    dr = ImageDraw.Draw(img)
    for z in range(z0, z1):
        for x in range(x0, x1):
            ix, iz = x - d['ox'], z - d['oz']
            if not (0 <= ix < W and 0 <= iz < L):
                continue
            col = g[:, iz, ix]
            ys = [y for y in np.nonzero(col)[0] if not names[col[y]].startswith('minecraft:light')]
            if not ys:
                continue
            c = color(names[col[ys[-1]]])
            shade = 0.6 + 0.4 * min(1, max(0, (ys[-1] + d['oy'] - 40) / 40))
            dr.rectangle([(x - x0) * k, (z - z0) * k, (x - x0 + 1) * k - 1, (z - z0 + 1) * k - 1],
                         fill=tuple(int(v * shade) for v in c))
    bx, by, bz, br = BUBBLE
    dr.ellipse([(bx - br - x0) * k, (bz - br - z0) * k, (bx + br - x0) * k, (bz + br - z0) * k], outline=(255, 230, 140), width=2)
    for p, c in ((d['knight'], (255, 0, 0)), (d['arrive'], (0, 255, 0))):
        px, pz = (p[0] - x0) * k + k // 2, (p[2] - z0) * k + k // 2
        dr.ellipse([px - 5, pz - 5, px + 5, pz + 5], outline=c, width=2)
    img.save(os.path.join(out, 'alba_arriba.png'))
    # de lado: corte por x = CX (norte a la izquierda)
    k2 = 5
    zz0, zz1, yy0, yy1 = CZ - 36, -140, 20, 110
    side = Image.new('RGB', ((zz1 - zz0) * k2, (yy1 - yy0) * k2), (12, 8, 20))
    ds = ImageDraw.Draw(side)
    for z in range(zz0, zz1):
        iz = z - d['oz']
        if not 0 <= iz < L:
            continue
        for y in range(yy0, yy1):
            iy = y - d['oy']
            v = g[iy, iz, CX - d['ox']]
            if v and not names[v].startswith('minecraft:light'):
                ds.rectangle([(z - zz0) * k2, (yy1 - 1 - y) * k2, (z - zz0 + 1) * k2 - 1, (yy1 - y) * k2 - 1], fill=color(names[v]))
    ds.ellipse([(bz - br - zz0) * k2, (yy1 - by - br) * k2, (bz + br - zz0) * k2, (yy1 - by + br) * k2], outline=(255, 230, 140), width=2)
    side.save(os.path.join(out, 'alba_lado.png'))
    # la isla entera con los fragmentos
    k3 = 2
    whole = Image.new('RGB', (W * k3, L * k3))
    dw = ImageDraw.Draw(whole)
    top = np.zeros((L, W), int) - 1
    for iz in range(L):
        for ix in range(W):
            ys = np.nonzero(g[:, iz, ix])[0]
            if len(ys):
                v = int(np.clip((ys[-1] + d['oy'] - 30) / 70, 0, 1) * 180 + 50)
                dw.rectangle([ix * k3, iz * k3, ix * k3 + k3 - 1, iz * k3 + k3 - 1], fill=(v, v, v))
    for f in frags:
        px, pz = (f[0] - d['ox']) * k3, (f[2] - d['oz']) * k3
        dw.ellipse([px - 4, pz - 4, px + 4, pz + 4], outline=(255, 220, 80), width=2)
    rx, rz = (d['rift'][0] - d['ox']) * k3, (d['rift'][2] - d['oz']) * k3
    dw.ellipse([rx - 6, rz - 6, rx + 6, rz + 6], outline=(255, 0, 255), width=2)
    whole.save(os.path.join(out, 'alba_isla.png'))


if __name__ == '__main__':
    main()
