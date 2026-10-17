"""La isla de The Void (la dimensión del vacío, 1.3.88): del mapa del dueño a lo que genera VoidIslandGenerator.

    python3 -I tools/void/isla.py <EndCarageKitMap.schematic> [--preview carpeta]

El mapa (MCEdit «Alpha»: ids numéricos de antes de la 1.13, 297×121×492) está en packs/void.tar.enc (cifrado, como
los packs de los jefes). Aquí:

  1. Se pasan sus bloques a los de la 1.20.1 (LEGACY; si sale uno que no está, se para).
  2. Se quita lo que había en el pozo del centro (el dueño: «quita las casas de esa isla y rellénalas con bloques del
     contraste de la isla»): era un pozo de ~50×50 que atravesaba la isla con puentes en cruz, cuatro casetas al final
     de los brazos y una torre en medio. Se rellena con el terreno de la isla: el fondo y los lados con su mezcla de
     lana gris, terracota cian, arcilla y piedra del End (ruido 3D, en la misma proporción que el resto de la isla) y
     arriba franjas como las de alrededor (lana y terracota moradas y magenta, micelio...).
  3. Encima, el santuario del caballero de luz (donde se llega y desde donde se vuelve): una plataforma redonda de tres
     escalones (piedra negra pulida, cuarzo y un anillo de oro), seis pilares de cuarzo con varas del End y luz
     invisible (bloques «light»), entradas con escaleras a los cuatro lados y un sello de cuarzo en medio.
  4. Se guarda en src/main/resources/tfclient-void-isla.bin (gzip): paleta de estados y, por columna, tramos
     (y, largo, bloque). El centro del santuario queda en x=0, z=0 del mundo.

Los 81 enderman del mapa no se copian (la dimensión no tiene criaturas).
"""
import gzip
import math
import os
import struct
import sys
from collections import deque

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import nbt  # noqa: E402

OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'tfclient-void-isla.bin'))

# Ids antiguos (id, dato) → estado de bloque de la 1.20.1. Escaleras: dato 0-3 = sube hacia este, oeste, sur, norte;
# +4 = boca abajo. Losas: +8 = arriba. Troncos 162: dato & 3 = acacia/roble oscuro, & 12 = eje (0 y, 4 x, 8 z, 12 corteza).
LEGACY = {
    (1, 0): 'stone', (3, 0): 'dirt', (4, 0): 'cobblestone', (9, 0): 'water[level=0]',
    (35, 0): 'white_wool', (35, 2): 'magenta_wool', (35, 7): 'gray_wool', (35, 10): 'purple_wool', (35, 14): 'red_wool',
    (43, 5): 'stone_brick_slab[type=double,waterlogged=false]',
    (44, 3): 'cobblestone_slab[type=bottom,waterlogged=false]', (44, 5): 'stone_brick_slab[type=bottom,waterlogged=false]',
    (44, 11): 'cobblestone_slab[type=top,waterlogged=false]', (44, 13): 'stone_brick_slab[type=top,waterlogged=false]',
    (82, 0): 'clay', (95, 0): 'white_stained_glass', (95, 2): 'magenta_stained_glass',
    (98, 0): 'stone_bricks', (98, 3): 'chiseled_stone_bricks', (110, 0): 'mycelium[snowy=false]', (121, 0): 'end_stone',
    (159, 0): 'white_terracotta', (159, 2): 'magenta_terracotta', (159, 9): 'cyan_terracotta',
    (159, 10): 'purple_terracotta', (159, 11): 'blue_terracotta',
    (162, 0): 'acacia_log[axis=y]', (162, 4): 'acacia_log[axis=x]', (162, 8): 'acacia_log[axis=z]', (162, 12): 'acacia_wood[axis=y]',
}
for d, f in enumerate(('east', 'west', 'south', 'north')):
    LEGACY[(67, d)] = f'cobblestone_stairs[facing={f},half=bottom,shape=straight,waterlogged=false]'
    LEGACY[(67, d + 4)] = f'cobblestone_stairs[facing={f},half=top,shape=straight,waterlogged=false]'
WALL = 139  # cobblestone_wall: sus lados se calculan con los vecinos

# El pozo del centro (en el mapa): su centro y la caja en la que se busca
PIT_CENTER = (136, 271)  # x, z
PIT_BOX = (98, 176, 232, 312)  # x0, x1, z0, z1
# Altura del suelo del santuario (la cara de arriba del terreno, en el mapa) y desplazamiento al mundo
GROUND = 61
WORLD_DY = 10  # y del mundo = y del mapa + 10 (el suelo queda a 71-72)


def full(s):
    return s if ':' in s else 'minecraft:' + s


def load(path):
    _, d = nbt.read(path)
    W, H, L = d['Width'], d['Height'], d['Length']
    b = np.frombuffer(d['Blocks'], dtype=np.uint8).reshape(H, L, W).astype(np.int32)
    m = np.frombuffer(d['Data'], dtype=np.uint8).reshape(H, L, W).astype(np.int32)
    return b, m


def value_noise(shape, cell, seed, octaves=3):
    """Ruido de valor suave (0..1) del tamaño «shape» (2D o 3D), celdas de «cell» bloques."""
    rng = np.random.default_rng(seed)
    out = np.zeros(shape)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        c = max(1.0, cell / (2 ** o))
        grid_shape = tuple(int(math.ceil(n / c)) + 2 for n in shape)
        g = rng.random(grid_shape)
        coords = np.meshgrid(*[np.arange(n) / c for n in shape], indexing='ij')
        idx = [np.floor(cc).astype(int) for cc in coords]
        fr = [cc - i for cc, i in zip(coords, idx)]
        fr = [f * f * (3 - 2 * f) for f in fr]
        acc = np.zeros(shape)
        dims = len(shape)
        for corner in range(2 ** dims):
            w = np.ones(shape)
            sel = []
            for k in range(dims):
                bit = (corner >> k) & 1
                w = w * (fr[k] if bit else 1 - fr[k])
                sel.append(idx[k] + bit)
            acc += w * g[tuple(sel)]
        out += amp * acc
        tot += amp
        amp *= 0.5
    return out / tot


def flood(empty, start):
    L, W = empty.shape
    seen = np.zeros_like(empty, dtype=bool)
    q = deque([start])
    seen[start] = True
    while q:
        a, c = q.popleft()
        for da, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            na, nc = a + da, c + dc
            if 0 <= na < L and 0 <= nc < W and empty[na, nc] and not seen[na, nc]:
                seen[na, nc] = True
                q.append((na, nc))
    return seen


def dilate(mask, n):
    out = mask.copy()
    for _ in range(n):
        o = out.copy()
        o[1:] |= out[:-1]
        o[:-1] |= out[1:]
        o[:, 1:] |= out[:, :-1]
        o[:, :-1] |= out[:, 1:]
        out = o
    return out


class Island:
    def __init__(self, b, m):
        self.H, self.L, self.W = b.shape
        self.names = ['minecraft:air']
        self.index = {'minecraft:air': 0}
        self.grid = np.zeros(b.shape, np.int32)
        missing = set()
        keys = np.unique(b * 16 + m)
        for k in keys:
            i, d = int(k) // 16, int(k) % 16
            if i == 0:
                continue
            if i == WALL:
                st = 'cobblestone_wall'
            elif (i, d) in LEGACY:
                st = LEGACY[(i, d)]
            else:
                missing.add((i, d))
                continue
            self.grid[(b == i) & (m == d)] = self.pal(full(st))
        if missing:
            raise SystemExit('bloques sin traducir: %s' % sorted(missing))
        self.wall = self.index.get('minecraft:cobblestone_wall', -1)

    def pal(self, name):
        name = full(name)
        if name not in self.index:
            self.index[name] = len(self.names)
            self.names.append(name)
        return self.index[name]

    def name(self, idx):
        return self.names[idx]


def fix_walls(isl):
    """Los muros de piedra se unen a sus vecinos sólidos (en el mapa antiguo no se guardaba)."""
    g = isl.grid
    if isl.wall < 0:
        return
    ys, zs, xs = np.nonzero(g == isl.wall)
    for y, z, x in zip(ys, zs, xs):
        sides = {}
        for nm, dz, dx in (('north', -1, 0), ('south', 1, 0), ('west', 0, -1), ('east', 0, 1)):
            zz, xx = z + dz, x + dx
            ok = 0 <= zz < isl.L and 0 <= xx < isl.W and g[y, zz, xx] != 0 and 'glass' not in isl.name(g[y, zz, xx])
            sides[nm] = 'low' if ok else 'none'
        up = 'true' if not ((sides['north'] == 'low' and sides['south'] == 'low' and sides['east'] == 'none' and sides['west'] == 'none')
                            or (sides['east'] == 'low' and sides['west'] == 'low' and sides['north'] == 'none' and sides['south'] == 'none')) else 'false'
        st = 'cobblestone_wall[east=%s,north=%s,south=%s,up=%s,waterlogged=false,west=%s]' % (
            sides['east'], sides['north'], sides['south'], up, sides['west'])
        g[y, z, x] = isl.pal(st)


def fill_pit(isl):
    """Quita casetas, puentes y torre del pozo del centro y lo rellena con el terreno de la isla."""
    g = isl.grid
    H, L, W = g.shape
    x0, x1, z0, z1 = PIT_BOX
    solid = g != 0
    # El pozo: lo vacío unido al centro en cada capa, juntando todas las capas (es un embudo que se abre hacia abajo)
    pit = np.zeros((L, W), bool)
    box = np.zeros((L, W), bool)
    box[z0:z1, x0:x1] = True
    for y in range(10, GROUND):
        e = ~solid[y] & box
        if e[PIT_CENTER[1], PIT_CENTER[0]]:
            f = flood(e, (PIT_CENTER[1], PIT_CENTER[0]))
            # (por debajo de la isla lo vacío llega al borde de la caja: esa capa ya no es el pozo)
            if f[z0, :].any() or f[z1 - 1, :].any() or f[:, x0].any() or f[:, x1 - 1].any():
                continue
            pit |= f
    pit = dilate(pit, 1) & box
    # Lo construido alrededor (casetas al final de los brazos): fuera del pozo pero a menos de 5 bloques, por encima
    # del suelo se quita todo menos los faroles de cristal blanco
    glass = isl.index.get('minecraft:white_stained_glass', -2)
    near = dilate(pit, 5) & box
    for y in range(GROUND + 1, H):
        layer = g[y]
        kill = near & (layer != 0) & (layer != glass)
        layer[kill] = 0
    # Dentro del pozo, todo fuera (también la torre y los faroles que hubiera)
    g[:, pit] = 0
    solid = g != 0
    # Alturas de alrededor (cara de arriba y fondo) del anillo de fuera del pozo
    ring = dilate(pit, 4) & ~pit & box & solid.any(0)
    top = np.where(solid.any(0), H - 1 - np.argmax(solid[::-1], 0), -1)
    bot = np.where(solid.any(0), np.argmax(solid, 0), -1)
    rz, rx = np.nonzero(ring)
    rt = np.minimum(top[rz, rx], GROUND).astype(float)
    rb = bot[rz, rx].astype(float)
    pz, px = np.nonzero(pit)
    # Interpolación por distancia inversa desde el anillo
    d2 = (pz[:, None] - rz[None, :]) ** 2 + (px[:, None] - rx[None, :]) ** 2 + 0.5
    w = 1.0 / d2 ** 1.6
    ws = w.sum(1)
    ptop = (w @ rt) / ws
    pbot = (w @ rb) / ws
    # El fondo baja un poco hacia el centro (como el resto de la isla, que cuelga)
    cz, cx = PIT_CENTER[1], PIT_CENTER[0]
    rr = np.sqrt((pz - cz) ** 2 + (px - cx) ** 2)
    rmax = rr.max() + 1
    sag = 9 * (1 - (rr / rmax) ** 2)
    n2 = value_noise((L, W), 9, 11)
    pbot = pbot - sag - 4 * (n2[pz, px] - 0.5)
    ptop = np.round(np.clip(ptop, GROUND - 1, GROUND)).astype(int)
    pbot = np.round(np.clip(pbot, 12, GROUND - 6)).astype(int)
    # Materiales: los de dentro y los de la superficie, en la proporción de la isla
    body = [isl.pal('gray_wool'), isl.pal('cyan_terracotta'), isl.pal('clay'), isl.pal('end_stone')]
    zb0, zb1, xb0, xb1 = pz.min(), pz.max() + 1, px.min(), px.max() + 1
    shp = (H, zb1 - zb0, xb1 - xb0)
    n_end = value_noise(shp, 7, 21)
    n_clay = value_noise(shp, 9, 22)
    n_cyan = value_noise(shp, 5, 24)
    surf_names = ['purple_wool', 'magenta_wool', 'purple_terracotta', 'blue_terracotta', 'gray_wool', 'mycelium[snowy=false]',
                  'magenta_terracotta']
    surf = [isl.pal(s) for s in surf_names]
    warp = value_noise((L, W), 14, 31)
    band = value_noise((L, W), 22, 32, octaves=2)
    for i in range(len(pz)):
        z, x = pz[i], px[i]
        lz, lx = z - zb0, x - xb0
        for y in range(pbot[i], ptop[i] + 1):
            depth = ptop[i] - y
            if depth <= 1:
                # franjas onduladas como las de alrededor
                v = band[z, x] * 6 + warp[z, x] * 5
                k = int(v * 3) % len(surf)
                blk = surf[k]
                if depth == 1 and blk in (surf[5],):
                    blk = isl.pal('dirt')
            else:
                if n_end[y, lz, lx] > 0.66:
                    blk = body[3]
                elif n_clay[y, lz, lx] > 0.68:
                    blk = body[2]
                elif n_cyan[y, lz, lx] > 0.62:
                    blk = body[1]
                else:
                    blk = body[0]
            g[y, z, x] = blk
    return pit


def shrine(isl):
    """El santuario del caballero de luz, centrado en el pozo relleno. Devuelve (x, y, z) del mapa donde está él."""
    g = isl.grid
    cx, cz = PIT_CENTER
    y0 = GROUND
    P = isl.pal
    blackstone = P('polished_blackstone_bricks')
    chis_black = P('chiseled_polished_blackstone')
    crying = P('crying_obsidian')
    quartz = P('smooth_quartz')
    qbricks = P('quartz_bricks')
    chis_q = P('chiseled_quartz_block')
    gold = P('gold_block')
    pillar = P('quartz_pillar[axis=y]')
    rod = P('end_rod[facing=up]')
    light = P('light[level=15,waterlogged=false]')
    light_soft = P('light[level=11,waterlogged=false]')
    amethyst = P('amethyst_block')
    cluster = P('amethyst_cluster[facing=up,waterlogged=false]')

    def put(x, y, z, blk):
        if 0 <= y < isl.H and 0 <= z < isl.L and 0 <= x < isl.W:
            g[y, z, x] = blk

    R = 11
    for dz in range(-R - 1, R + 2):
        for dx in range(-R - 1, R + 2):
            r = math.hypot(dx, dz)
            x, z = cx + dx, cz + dz
            # quita lo que hubiera encima
            if r <= R + 0.5:
                for y in range(y0 + 1, y0 + 12):
                    put(x, y, z, 0)
            # Escalón 1: piedra negra con un anillo cincelado y vetas de obsidiana llorosa
            if r <= R + 0.3:
                ang = math.atan2(dz, dx)
                if r > R - 0.8:
                    put(x, y0 + 1, z, chis_black if int((ang + math.pi) / (2 * math.pi) * 36) % 3 == 0 else blackstone)
                elif abs(math.sin(ang * 3)) < 0.12 and r > 4:
                    put(x, y0 + 1, z, crying)
                else:
                    put(x, y0 + 1, z, blackstone)
            # Escalón 2: cuarzo con un anillo de oro
            if r <= 7.6:
                put(x, y0 + 2, z, gold if 6.6 < r <= 7.6 else quartz)
            # Escalón 3: ladrillos de cuarzo y el sello
            if r <= 4.4:
                put(x, y0 + 3, z, qbricks)
                if r <= 1.6:
                    put(x, y0 + 3, z, chis_q)
                if 2.4 < r <= 3.2 and (dx == 0 or dz == 0 or abs(dx) == abs(dz)):
                    put(x, y0 + 3, z, gold)
    # Entradas: escaleras de cuarzo en los cuatro lados (tres de ancho)
    for f, (sx, sz) in (('north', (0, -1)), ('south', (0, 1)), ('west', (-1, 0)), ('east', (1, 0))):
        # hacia dónde mira la escalera: el lado alto hacia el centro
        facing = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}[f]
        st = P(f'quartz_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]')
        for w in (-1, 0, 1):
            px_, pz_ = (w, 0) if sx == 0 else (0, w)
            # escalón al nivel 2 (r 8) y al 3 (r 5)
            put(cx + sx * 8 + px_, y0 + 2, cz + sz * 8 + pz_, st)
            put(cx + sx * 5 + px_, y0 + 3, cz + sz * 5 + pz_, st)
            put(cx + sx * 12 + px_, y0 + 1, cz + sz * 12 + pz_, st)
    # Seis pilares de cuarzo con vara del End y luz invisible, y amatista a sus pies
    for k in range(6):
        a = k * math.pi / 3 + math.pi / 6
        x, z = cx + int(round(math.cos(a) * 9)), cz + int(round(math.sin(a) * 9))
        put(x, y0 + 2, z, chis_q)
        for y in range(y0 + 3, y0 + 8):
            put(x, y, z, pillar)
        put(x, y0 + 8, z, chis_q)
        put(x, y0 + 9, z, gold)
        put(x, y0 + 10, z, rod)
        put(x, y0 + 11, z, light)
        for ox, oz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if g[y0 + 2, z + oz, x + ox] == 0 and g[y0 + 1, z + oz, x + ox] != 0:
                put(x + ox, y0 + 2, z + oz, cluster)
    # Luz suave e invisible sobre la plataforma (el vacío no tiene sol)
    for dx, dz in ((0, 0), (5, 5), (-5, 5), (5, -5), (-5, -5)):
        put(cx + dx, y0 + 6, cz + dz, light_soft)
    # Amatista bajo el sello (brilla un poco por las juntas al mirarlo de cerca)
    put(cx, y0 + 2, cz, amethyst)
    return cx, y0 + 4, cz


def encode(isl, ox, oy, oz, npc, arrive):
    g = isl.grid
    H, L, W = g.shape
    out = bytearray()
    out += b'TFVI' + struct.pack('>i', 1)
    out += struct.pack('>iii', W, H, L)
    out += struct.pack('>iii', ox, oy, oz)
    out += struct.pack('>iii', *npc)
    out += struct.pack('>iiif', *arrive)
    out += struct.pack('>i', len(isl.names))
    for n in isl.names:
        e = n.encode('utf-8')
        out += struct.pack('>H', len(e)) + e
    runs_total = 0
    for z in range(L):
        for x in range(W):
            col = g[:, z, x]
            runs = []
            y = 0
            while y < H:
                v = col[y]
                if v == 0:
                    y += 1
                    continue
                s = y
                while y < H and col[y] == v:
                    y += 1
                runs.append((s, y - s, int(v)))
            out += struct.pack('>H', len(runs))
            for s, n, v in runs:
                out += struct.pack('>HHH', s, n, v)
            runs_total += len(runs)
    data = gzip.compress(bytes(out), 9, mtime=0)
    with open(OUT, 'wb') as f:
        f.write(data)
    return runs_total, len(data)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    b, m = load(sys.argv[1])
    isl = Island(b, m)
    fix_walls(isl)
    fill_pit(isl)
    nx, ny, nz = shrine(isl)
    # Centro del santuario en x=0, z=0 del mundo
    ox, oy, oz = -nx, WORLD_DY, -nz
    npc = (0, ny + oy, 0)
    arrive = (0, ny + oy, 6, 180.0)  # se llega delante del caballero, mirándole (al norte)
    runs, size = encode(isl, ox, oy, oz, npc, arrive)
    print('isla: %dx%dx%d, %d estados, %d tramos, %.1f kB; caballero en %s' % (isl.W, isl.H, isl.L, len(isl.names), runs,
                                                                            size / 1024, npc))
    if '--preview' in sys.argv:
        dest = sys.argv[sys.argv.index('--preview') + 1]
        os.makedirs(dest, exist_ok=True)
        np.savez_compressed(os.path.join(dest, 'isla.npz'), g=isl.grid, names=np.array(isl.names))


if __name__ == '__main__':
    main()
