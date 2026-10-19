"""La isla de The Void (la dimensión del vacío, 1.3.88; santuario y plaza nuevos en la 1.3.89): del mapa del dueño a lo
que genera VoidIslandGenerator.

    python3 -I tools/void/isla.py <EndCarageKitMap.schematic> [--preview carpeta]

El mapa (MCEdit «Alpha»: ids numéricos de antes de la 1.13, 297×121×492) está en packs/void.tar.enc (cifrado, como
los packs de los jefes). Aquí:

  1. Se pasan sus bloques a los de la 1.20.1 (LEGACY; si sale uno que no está, se para).
  2. Se quita lo que había en el pozo del centro (el dueño: «quita las casas de esa isla y rellénalas con bloques del
     contraste de la isla»): era un pozo de ~50×50 que atravesaba la isla con puentes en cruz, cuatro casetas al final
     de los brazos y una torre en medio. Se rellena con el terreno de la isla: el fondo y los lados con su mezcla de
     lana gris, terracota cian, arcilla y piedra del End (ruido 3D, en la misma proporción que el resto de la isla) y
     arriba las franjas de alrededor, que se alargan hacia dentro (cada columna toma la de la orilla más cercana por un
     camino con ruido: siguen las bandas en vez de manchas).
  3. 1.3.89 (el dueño: «el centro debe tener la grieta; el santuario hazlo en una esquina, en esa construcción; ese
     modelo es horrible y está 1 bloque volando del suelo; quita las demás estructuras cerca del centro»):
     - En el centro, la plaza de la grieta del Void (plaza): suelo plano en un radio de 24 (la grieta madre llega con
       su círculo de runas a ~20), oscuro de piedra negra y obsidiana con vetas de obsidiana llorosa que salen del centro
       y se funde con las franjas de la isla hacia fuera. Sin nada encima a menos de 45 bloques (farolillos, caminos).
     - El santuario (santuario), en la punta norte, donde estaba el templete de cúpula del mapa: estrado redondo de dos
       escalones (piedra negra y cuarzo con un anillo cincelado), ocho columnas de cuarzo con capitel, un entablamento
       con cornisa, cúpula de cristal blanco con nervios de cuarzo y remate de oro con vara del End, farolillos colgando
       en las entradas, balaustrada hacia el barranco y luz invisible dentro. Los cimientos bajan hasta el terreno en
       cada columna (el de la 1.3.88 tenía huecos debajo: «volando»). El caballero en el pedestal del centro mirando al
       sur; se llega a la puerta del este, mirando hacia la grieta (y el agujero negro detrás).
  4. 1.3.90 (el dueño, con captura desde arriba: «la grieta no está en el centro de la isla; quiero que hagas esa isla
     más grande»): la isla se agranda ×1,5 en planta y ×1,35 en altura (×1,5 la sacaría por debajo del mundo) alrededor
     de su centro de verdad, y la plaza y la grieta van ahí (agrandar). El terreno se remuestrea suave (ocupación
     trilineal, sin escalones de 1-2 bloques) y las franjas de la superficie se copian de su columna; los caminos van con
     el terreno; lo construido (el templete del sureste y los farolillos) se pone a su tamaño (1:1) en su sitio nuevo,
     sobre el terreno nuevo, con cimientos; el templete del norte lo sustituye el santuario.
  5. Se guarda en src/main/resources/tfclient-void-isla.bin (gzip): paleta de estados, posiciones (caballero, llegada,
     grieta) y, por columna, tramos (y, largo, bloque). El centro de la plaza (la grieta) queda en x=0, z=0 del mundo.

Los 81 enderman del mapa no se copian (la dimensión no tiene criaturas).
"""
import gzip
import heapq
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
# Altura del suelo de la plaza (la cara de arriba del terreno, en el mapa) y desplazamiento al mundo
GROUND = 61
WORLD_DY = 10  # y del mundo = y del mapa + 10 (el suelo de la plaza queda a 71: se pisa en 72)
# El santuario: el templete de la punta norte del mapa (centro y altura de su terraza)
SHRINE = (115, 135)
SHRINE_TOP = 56
PLAZA_R = 28
CLEAR_R = 55
# 1.3.90: la isla más grande (en planta y en altura) y en el mundo con y = la del mapa grande (oy 0)
SCALE = 1.5
SCALE_Y = 1.35
WORLD_GROUND = GROUND + WORLD_DY  # la cara de arriba de la plaza en el mundo: 71 (la grieta sigue en 0 72 0)
H_WORLD = 128
TERRAIN = {'gray_wool', 'end_stone', 'cyan_terracotta', 'clay', 'purple_wool', 'magenta_wool', 'purple_terracotta',
           'blue_terracotta', 'magenta_terracotta', 'white_terracotta', 'mycelium', 'dirt', 'white_wool', 'red_wool',
           'acacia_wood', 'acacia_log'}


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
    labels = inpaint_surface(isl, pit, ring)
    for i in range(len(pz)):
        z, x = pz[i], px[i]
        lz, lx = z - zb0, x - xb0
        top_blk, below_blk = labels.get((z, x), (body[0], body[0]))
        for y in range(pbot[i], ptop[i] + 1):
            depth = ptop[i] - y
            if depth == 0:
                blk = top_blk
            elif depth == 1:
                blk = below_blk
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


def is_terrain(isl, idx):
    return idx != 0 and isl.name(idx).split(':')[1].split('[')[0] in TERRAIN


def inpaint_surface(isl, pit, ring):
    """La superficie del pozo relleno: cada columna toma la de la orilla (ring) más cercana por un camino con ruido
    (Dijkstra con coste 1 + ruido): las franjas de alrededor se alargan hacia dentro en vez de quedar manchas."""
    g = isl.grid
    H, L, W = g.shape
    solid = g != 0
    top = np.where(solid.any(0), H - 1 - np.argmax(solid[::-1], 0), -1)
    noise = value_noise((L, W), 6, 41)
    dist = {}
    label = {}
    heap = []
    rz, rx = np.nonzero(ring)
    for z, x in zip(rz, rx):
        t = top[z, x]
        if t < 1 or not is_terrain(isl, g[t, z, x]):
            continue
        below = g[t - 1, z, x]
        if not is_terrain(isl, below):
            below = g[t, z, x]
        dist[(z, x)] = 0.0
        label[(z, x)] = (int(g[t, z, x]), int(below))
        heapq.heappush(heap, (0.0, int(z), int(x)))
    allowed = pit | ring
    while heap:
        d, z, x = heapq.heappop(heap)
        if d > dist.get((z, x), 1e18):
            continue
        for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nz, nx = z + dz, x + dx
            if not (0 <= nz < L and 0 <= nx < W) or not allowed[nz, nx]:
                continue
            nd = d + 1.0 + 3.0 * noise[nz, nx]
            if nd < dist.get((nz, nx), 1e18):
                dist[(nz, nx)] = nd
                label[(nz, nx)] = label[(z, x)]
                heapq.heappush(heap, (nd, nz, nx))
    return {k: v for k, v in label.items() if pit[k]}


def clear_near_center(isl):
    """Nada construido a menos de CLEAR_R bloques de la grieta (farolillos, trozos de camino): por encima del terreno,
    todo lo que no sea terreno fuera."""
    g = isl.grid
    H, L, W = g.shape
    cx, cz = PIT_CENTER
    terr = np.array([is_terrain(isl, i) for i in range(len(isl.names))])
    T = terr[g]
    ttop = np.where(T.any(0), H - 1 - np.argmax(T[::-1], 0), -1)
    n = 0
    for z in range(max(0, cz - CLEAR_R), min(L, cz + CLEAR_R + 1)):
        for x in range(max(0, cx - CLEAR_R), min(W, cx + CLEAR_R + 1)):
            if math.hypot(x - cx, z - cz) > CLEAR_R:
                continue
            col = g[:, z, x]
            # (lo que está a ras del suelo de alrededor, como los caminos metidos en el terreno, se rellena con el
            # terreno de la columna: sin surcos)
            around = ttop[max(0, z - 1):z + 2, max(0, x - 1):x + 2]
            flush = int(np.median(around[around >= 0])) if (around >= 0).any() else -1
            fill = col[ttop[z, x]] if ttop[z, x] >= 0 else 0
            for y in range(max(0, ttop[z, x] + 1), H):
                if col[y] != 0 and not terr[col[y]]:
                    col[y] = fill if y <= flush else 0
                    n += 1
    return n


def plaza(isl):
    """La plaza de la grieta del Void: llana (cara de arriba en GROUND) en un radio PLAZA_R, oscura en el centro (piedra
    negra, negra pulida y obsidiana a manchas, con siete vetas de obsidiana llorosa que salen del centro) y fundiéndose
    con las franjas de la isla hacia fuera (a puntos, sin un borde redondo). Las 4 siguientes columnas bajan o suben
    poco a poco hasta el terreno de alrededor."""
    g = isl.grid
    H, L, W = g.shape
    cx, cz = PIT_CENTER
    P = isl.pal
    blackstone, polished, obsidian, crying = P('blackstone'), P('polished_blackstone'), P('obsidian'), P('crying_obsidian')
    body = P('gray_wool')
    n1 = value_noise((L, W), 5, 51)
    n2 = value_noise((L, W), 4, 52)
    nd = value_noise((L, W), 3, 53)
    rng = np.random.default_rng(54)
    veins = [k * math.tau / 7 + rng.uniform(-0.2, 0.2) for k in range(7)]
    R2 = PLAZA_R + 4
    for z in range(cz - R2, cz + R2 + 1):
        for x in range(cx - R2, cx + R2 + 1):
            r = math.hypot(x - cx, z - cz)
            if r > R2:
                continue
            col = g[:, z, x]
            solid = np.nonzero(col)[0]
            if len(solid) == 0:
                continue
            ytop = int(solid.max())
            top_blk = col[ytop] if is_terrain(isl, col[ytop]) else body
            below_blk = col[ytop - 1] if ytop > 0 and is_terrain(isl, col[ytop - 1]) else top_blk
            if r <= PLAZA_R:
                target = GROUND
            else:
                k = (r - PLAZA_R) / 4.0
                target = int(round(GROUND + (ytop - GROUND) * k))
            # subir o bajar la columna hasta target, con sus dos bloques de arriba encima
            col[target + 1:] = np.where(col[target + 1:] != 0, 0, col[target + 1:])
            for y in range(min(ytop, target) - 1, target + 1):
                if y < 0:
                    continue
                if y == target:
                    col[y] = top_blk
                elif y == target - 1:
                    col[y] = below_blk
                elif col[y] == 0:
                    col[y] = body
            if r > PLAZA_R:
                continue
            # lo oscuro: seguro hasta 18, a puntos hasta PLAZA_R
            dark = smooth(PLAZA_R, 18.0, r)
            if nd[z, x] < dark:
                v = n1[z, x]
                blk = obsidian if n2[z, x] > 0.68 else polished if v > 0.6 else blackstone
                ang = math.atan2(z - cz, x - cx)
                for a0 in veins:
                    a = a0 + 0.22 * math.sin(r * 0.35 + a0 * 3)
                    da = (ang - a + math.pi) % math.tau - math.pi
                    if 5.5 < r < 24 and abs(da) * r < 0.8 - 0.02 * r:
                        blk = crying
                col[GROUND] = blk
                col[GROUND - 1] = blackstone


def smooth(e0, e1, x):
    t = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def connect_walls(isl, cells):
    """Los muros se unen a los vecinos que sean muro o bloque lleno (como en el juego al ponerlos a mano)."""
    g = isl.grid
    H, L, W = g.shape

    def joins(idx):
        if idx == 0:
            return False
        n = isl.name(idx)
        return ('wall' in n or not any(w in n for w in ('glass', 'stairs', 'slab', 'lantern', 'light', 'rod', 'cluster')))

    for (x, y, z, base) in cells:
        sides = {}
        for nm, dz, dx in (('north', -1, 0), ('south', 1, 0), ('west', 0, -1), ('east', 0, 1)):
            zz, xx = z + dz, x + dx
            sides[nm] = 'low' if 0 <= zz < L and 0 <= xx < W and joins(g[y, zz, xx]) else 'none'
        straight = (sides['north'] == sides['south'] == 'low' and sides['east'] == sides['west'] == 'none') or \
                   (sides['east'] == sides['west'] == 'low' and sides['north'] == sides['south'] == 'none')
        g[y, z, x] = isl.pal('%s[east=%s,north=%s,south=%s,up=%s,waterlogged=false,west=%s]' % (
            base, sides['east'], sides['north'], sides['south'], 'false' if straight else 'true', sides['west']))


def santuario(isl):
    """El santuario del caballero, en la terraza de la punta norte (donde el mapa tenía un templete con cúpula).
    Devuelve (caballero, llegada) en coordenadas del mapa: el caballero en su pedestal del centro y la llegada en el
    borde sur del estrado."""
    g = isl.grid
    H, L, W = g.shape
    cx, cz = SHRINE
    T = SHRINE_TOP
    P = isl.pal
    terr = np.array([is_terrain(isl, i) for i in range(len(isl.names))])

    def put(x, y, z, blk):
        if 0 <= y < H and 0 <= z < L and 0 <= x < W:
            g[y, z, x] = blk

    def rr(x, z):
        return math.hypot(x - cx, z - cz)

    def angle_near(x, z, angs, tol):
        a = math.atan2(z - cz, x - cx)
        return any(abs((a - b + math.pi) % math.tau - math.pi) < tol for b in angs)

    # 1. Fuera el templete del mapa (columnas de piedra y cúpula de terracota): todo lo de encima de la terraza al aire
    # (hasta r 10,5; más allá, solo lo que no es terreno) y lo que no es terreno por debajo, terreno
    body = P('gray_wool')
    for z in range(cz - 12, cz + 13):
        for x in range(cx - 12, cx + 13):
            r = rr(x, z)
            if r > 11.5:
                continue
            for y in range(T - 4, H):
                b = g[y, z, x]
                if b == 0:
                    continue
                if y > T and r <= 10.5:
                    g[y, z, x] = 0
                elif not terr[b]:
                    g[y, z, x] = body if y <= T else 0
    # 2. Cimientos: en cada columna del estrado, de lo más alto que haya hasta la terraza (sin huecos debajo)
    tiles = P('deepslate_tiles')
    R1 = 8.6
    for z in range(cz - 9, cz + 10):
        for x in range(cx - 9, cx + 10):
            if rr(x, z) > R1:
                continue
            col = g[:, z, x]
            below = np.nonzero(col[:T + 1])[0]
            y0 = int(below.max()) + 1 if len(below) else T - 6
            for y in range(min(y0, T), T + 1):
                col[y] = tiles
    # 3. Primer escalón (y T+1): piedra negra pulida con un borde de ladrillos y ocho piedras cinceladas
    bricks, chis_black, polished = P('polished_blackstone_bricks'), P('chiseled_polished_blackstone'), P('polished_blackstone')
    eight = [k * math.pi / 4 for k in range(8)]
    for z in range(cz - 9, cz + 10):
        for x in range(cx - 9, cx + 10):
            r = rr(x, z)
            if r > R1:
                continue
            if r > 7.6:
                put(x, T + 1, z, chis_black if angle_near(x, z, eight, 0.07) else bricks)
            else:
                put(x, T + 1, z, polished)
    # 4. Segundo escalón (y T+2, el suelo): cuarzo liso, borde de ladrillos de cuarzo, un anillo cincelado con oro en
    # los cuatro puntos y el pedestal del caballero en medio (con amatista debajo)
    quartz, qbricks, chis_q, gold = P('smooth_quartz'), P('quartz_bricks'), P('chiseled_quartz_block'), P('gold_block')
    four = [k * math.pi / 2 for k in range(4)]
    for z in range(cz - 8, cz + 9):
        for x in range(cx - 8, cx + 9):
            r = rr(x, z)
            if r > 7.6:
                continue
            blk = qbricks if r > 6.9 else quartz
            if 4.3 < r <= 5.1:
                blk = gold if angle_near(x, z, four, 0.12) else chis_q
            put(x, T + 2, z, blk)
    put(cx, T + 2, cz, P('amethyst_block'))
    for z in range(cz - 1, cz + 2):
        for x in range(cx - 1, cx + 2):
            if rr(x, z) <= 1.5:
                put(x, T + 3, z, gold if (x, z) == (cx, cz) else chis_q)
    # 5. Ocho columnas de cuarzo (entre las entradas) con basa y capitel
    pillar = P('quartz_pillar[axis=y]')
    pillar_angs = [math.pi / 8 + k * math.pi / 4 for k in range(8)]
    pillars = []
    for a in pillar_angs:
        x, z = cx + int(round(math.cos(a) * 6.4)), cz + int(round(math.sin(a) * 6.4))
        pillars.append((x, z))
        put(x, T + 3, z, chis_q)
        for y in range(T + 4, T + 10):
            put(x, y, z, pillar)
        put(x, T + 10, z, chis_q)
    # 6. Cúpula: cristal blanco con nervios de cuarzo sobre las columnas; remate de oro y vara del End
    glass, rib = P('white_stained_glass'), quartz
    cy = T + 11
    Rd = 6.6
    for y in range(cy + 1, cy + 8):
        for z in range(cz - 7, cz + 8):
            for x in range(cx - 7, cx + 8):
                d = math.sqrt((x - cx) ** 2 + (z - cz) ** 2 + (y - cy) ** 2)
                if Rd - 1.1 < d <= Rd:
                    rxy = max(0.5, rr(x, z))
                    put(x, y, z, rib if angle_near(x, z, pillar_angs, 0.55 / rxy) else glass)
    for z in range(cz - 1, cz + 2):
        for x in range(cx - 1, cx + 2):
            if rr(x, z) <= 1.5:
                put(x, cy + 6, z, chis_q)
                put(x, cy + 7, z, 0)
    put(cx, cy + 7, cz, gold)
    put(cx, cy + 8, cz, P('end_rod[facing=up]'))
    put(cx, cy + 9, cz, P('light[level=15,waterlogged=false]'))
    # 7. Entablamento sobre las columnas (oro encima de cada una) y cornisa que vuela
    slab = P('smooth_quartz_slab[type=bottom,waterlogged=false]')
    for z in range(cz - 8, cz + 9):
        for x in range(cx - 8, cx + 9):
            r = rr(x, z)
            if 5.6 < r <= 7.4:
                put(x, cy, z, quartz)
            if 6.0 < r <= 7.9:
                put(x, cy + 1, z, slab)
    for x, z in pillars:
        put(x, cy, z, gold)
    # 8. Farolillos colgando en las cuatro entradas y luz invisible dentro (el Void no tiene sol)
    lantern = P('lantern[hanging=true,waterlogged=false]')
    for a in four:
        put(cx + int(round(math.cos(a) * 6.4)), cy - 1, cz + int(round(math.sin(a) * 6.4)), lantern)
    put(cx, T + 8, cz, P('light[level=15,waterlogged=false]'))
    for a in four:
        put(cx + int(round(math.cos(a + math.pi / 4) * 4)), T + 5, cz + int(round(math.sin(a + math.pi / 4) * 4)),
            P('light[level=11,waterlogged=false]'))
    # 9. Entrada al este (por donde sigue el camino): escaleras de cuarzo; en lo demás, balaustrada (al sur, el barranco)
    for w in (-1, 0, 1):
        put(cx + 9, T + 1, cz + w, P('quartz_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]'))
        put(cx + 8, T + 2, cz + w, P('quartz_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]'))
    walls = []
    for z in range(cz - 9, cz + 10):
        for x in range(cx - 9, cx + 10):
            r = rr(x, z)
            if 7.6 < r <= R1 and not angle_near(x, z, [0.0], 0.42) and g[T + 2, z, x] == 0:
                walls.append((x, T + 2, z, 'polished_blackstone_brick_wall'))
                put(x, T + 2, z, P('polished_blackstone_brick_wall'))
    connect_walls(isl, walls)
    # amatista al pie de las columnas, por fuera
    cluster = P('amethyst_cluster[facing=up,waterlogged=false]')
    for x, z in pillars:
        ox, oz = x - cx, z - cz
        n = math.hypot(ox, oz)
        px_, pz_ = x + int(round(ox / n)), z + int(round(oz / n))
        if g[T + 3, pz_, px_] == 0 and g[T + 2, pz_, px_] not in (0,) and 'wall' not in isl.name(g[T + 2, pz_, px_]):
            put(px_, T + 3, pz_, cluster)
    knight = (cx, T + 4, cz)
    # se llega a la escalera del este (por fuera de las columnas, con la cúpula a la derecha): desde ahí la línea hasta la
    # grieta del centro queda libre (desde el borde sur la tapaba la loma que baja de la aguja del oeste, a y 72-77, y
    # desde la puerta, la columna de su lado sur)
    arrive = (cx + 8, T + 3, cz)
    return knight, arrive


def agrandar(isl):
    """La isla ×SCALE en planta y ×SCALE_Y en altura alrededor de su centro (el de las columnas con algo de terreno),
    en una rejilla nueva con y = la del mundo. Cambia isl (rejilla) y PIT_CENTER, SHRINE, SHRINE_TOP, GROUND y
    WORLD_DY para lo que viene después (la plaza va al centro nuevo). Devuelve (ox, oz): x, z del mundo del (0, 0) de la
    rejilla."""
    global PIT_CENTER, SHRINE, SHRINE_TOP, GROUND, WORLD_DY
    g = isl.grid
    H, L, W = g.shape
    terr = np.array([is_terrain(isl, i) for i in range(len(isl.names))])
    glassy = np.array(['glass' in n for n in isl.names])
    T = terr[g]
    solid = g != 0
    # El centro: el del cuerpo de la isla, sin las puntas (columnas de 6 bloques o más, abiertas 12: fuera lo que no
    # cabe en un círculo de 12): el punto que queda a la misma distancia de los bordes oeste y este y de los bordes norte
    # y sur (desde la grieta, la isla se ve igual de grande a cada lado)
    body = T.sum(0) >= 6
    dist = np.zeros(body.shape, int)
    cur = body.copy()
    k = 0
    while cur.any():
        k += 1
        dist[cur] = k
        e = cur.copy()
        e[1:] &= cur[:-1]
        e[:-1] &= cur[1:]
        e[:, 1:] &= cur[:, :-1]
        e[:, :-1] &= cur[:, 1:]
        e[1:, 1:] &= cur[:-1, :-1]
        e[:-1, :-1] &= cur[1:, 1:]
        e[1:, :-1] &= cur[:-1, 1:]
        e[:-1, 1:] &= cur[1:, :-1]
        cur = e
    core = dilate(dist >= 12, 12) & body
    bz, bx = np.nonzero(core)
    cxs, czs = int(round(bx.mean())), int(round(bz.mean()))

    def span(line, c):
        lo = hi = c
        while lo > 0 and line[lo - 1]:
            lo -= 1
        while hi < len(line) - 1 and line[hi + 1]:
            hi += 1
        return lo, hi

    for _ in range(8):
        xl, xh = span(core[czs], cxs)
        zl, zh = span(core[:, cxs], czs)
        cxs, czs = int(round((xl + xh) / 2)), int(round((zl + zh) / 2))
    # Lo que no es terreno: el templete del norte (r ≤ 14) se quita (lo sustituye el santuario); el cristal (farolillos)
    # y el templete del sureste se ponen a su tamaño; lo demás (caminos de ladrillo, metidos en el suelo) va con el
    # terreno
    sx, sz = SHRINE
    yy, zz, xx = np.nonzero(solid & ~T)
    drop = np.hypot(xx - sx, zz - sz) <= 14
    se = (xx >= 186) & (xx <= 216) & (zz >= 319) & (zz <= 352)
    keep = glassy[g[yy, zz, xx]] | se
    struct = [(int(y), int(z), int(x)) for y, z, x, d, k in zip(yy, zz, xx, drop, keep) if k and not d]
    like = T.copy()
    for y, z, x, d, k in zip(yy, zz, xx, drop, keep):
        if not d and not k:
            like[y, z, x] = True
    occ = like.astype(np.float32)
    mat = np.where(like, g, 0)
    # La rejilla nueva: lo que ocupa el terreno escalado, con margen
    xs, zs = np.nonzero(like.any(0).T)[0], np.nonzero(like.any(0))[0]
    wx0 = int(math.floor((xs.min() - cxs) * SCALE)) - 4
    wx1 = int(math.ceil((xs.max() - cxs) * SCALE)) + 4
    wz0 = int(math.floor((zs.min() - czs) * SCALE)) - 4
    wz1 = int(math.ceil((zs.max() - czs) * SCALE)) + 4
    W2, L2, H2 = wx1 - wx0 + 1, wz1 - wz0 + 1, H_WORLD
    X, Z = np.meshgrid(np.arange(W2), np.arange(L2))
    xsrc = cxs + (X + wx0) / SCALE
    zsrc = czs + (Z + wz0) / SCALE
    x0 = np.floor(xsrc).astype(int)
    z0 = np.floor(zsrc).astype(int)
    fx = (xsrc - x0).astype(np.float32)
    fz = (zsrc - z0).astype(np.float32)
    g2 = np.zeros((H2, L2, W2), np.int32)
    for Y in range(H2):
        ysrc = GROUND + (Y - WORLD_GROUND) / SCALE_Y
        y0 = int(math.floor(ysrc))
        fy = ysrc - y0
        if y0 + 1 < 0 or y0 >= H:
            continue
        acc = np.zeros((L2, W2), np.float32)
        best_w = np.full((L2, W2), -1.0, np.float32)
        best_m = np.zeros((L2, W2), np.int32)
        for dy in (0, 1):
            yi = y0 + dy
            if yi < 0 or yi >= H:
                continue
            wy = fy if dy else 1 - fy
            for dz in (0, 1):
                zi = z0 + dz
                wz = fz if dz else 1 - fz
                for dx in (0, 1):
                    xi = x0 + dx
                    wxx = fx if dx else 1 - fx
                    ok = (zi >= 0) & (zi < L) & (xi >= 0) & (xi < W)
                    zc, xc = np.clip(zi, 0, L - 1), np.clip(xi, 0, W - 1)
                    o = np.where(ok, occ[yi, zc, xc], 0.0)
                    w = wy * wz * wxx
                    acc += w * o
                    cand = np.where(o > 0, w, -1.0)
                    better = cand > best_w
                    best_w = np.where(better, cand, best_w)
                    best_m = np.where(better, mat[yi, zc, xc], best_m)
        g2[Y] = np.where(acc >= 0.5, best_m, 0)
    # Las franjas de la superficie: el bloque de arriba y el de debajo, los de su columna en el mapa
    s2 = g2 != 0
    top2 = np.where(s2.any(0), H2 - 1 - np.argmax(s2[::-1], 0), -1)
    ltop = np.where(like.any(0), H - 1 - np.argmax(like[::-1], 0), -1)
    xn = np.clip(np.round(xsrc).astype(int), 0, W - 1)
    zn = np.clip(np.round(zsrc).astype(int), 0, L - 1)
    for Z_, X_ in zip(*np.nonzero(top2 >= 0)):
        sz_, sx_ = zn[Z_, X_], xn[Z_, X_]
        t = ltop[sz_, sx_]
        if t < 1:
            continue
        Yt = top2[Z_, X_]
        g2[Yt, Z_, X_] = mat[t, sz_, sx_]
        if Yt > 0 and g2[Yt - 1, Z_, X_] != 0 and mat[t - 1, sz_, sx_] != 0:
            g2[Yt - 1, Z_, X_] = mat[t - 1, sz_, sx_]
    s2 = g2 != 0
    top2 = np.where(s2.any(0), H2 - 1 - np.argmax(s2[::-1], 0), -1)
    # Lo construido, a su tamaño: cada pieza (conexa) en su sitio nuevo, a la misma altura sobre el suelo, con cimientos
    pts = set(struct)
    seen = set()
    placed = 0
    found = isl.pal('stone_bricks')
    for p0 in struct:
        if p0 in seen:
            continue
        comp, st = [], [p0]
        seen.add(p0)
        while st:
            q = st.pop()
            comp.append(q)
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        r = (q[0] + dy, q[1] + dz, q[2] + dx)
                        if r in pts and r not in seen:
                            seen.add(r)
                            st.append(r)
        a = np.array(comp)
        ymin = int(a[:, 0].min())
        az, ax = int(round(a[:, 1].mean())), int(round(a[:, 2].mean()))
        lift = ymin - int(ltop[az, ax])
        NX = int(round((ax - cxs) * SCALE)) - wx0
        NZ = int(round((az - czs) * SCALE)) - wz0
        base = int(top2[NZ, NX]) + lift
        cols = {}
        for y, z, x in comp:
            Y, Z_, X_ = base + y - ymin, NZ + z - az, NX + x - ax
            if 0 <= Y < H2 and 0 <= Z_ < L2 and 0 <= X_ < W2:
                g2[Y, Z_, X_] = g[y, z, x]
                cols[(Z_, X_)] = min(cols.get((Z_, X_), 999), Y)
                placed += 1
        # sin terreno dentro de la pieza (por encima de su base) ni huecos debajo
        for (Z_, X_), ylo in cols.items():
            col = g2[:, Z_, X_]
            for Y in range(base + 1, H2):
                if col[Y] != 0 and terr[col[Y]] and Y > ylo:
                    col[Y] = 0
            below = np.nonzero(col[:ylo])[0]
            if len(below) and len(comp) > 20:
                for Y in range(int(below.max()) + 1, ylo):
                    col[Y] = found
    isl.grid = g2
    isl.H, isl.L, isl.W = H2, L2, W2
    PIT_CENTER = (-wx0, -wz0)
    SHRINE = (int(round((sx - cxs) * SCALE)) - wx0, int(round((sz - czs) * SCALE)) - wz0)
    SHRINE_TOP = int(round(WORLD_GROUND + (SHRINE_TOP - GROUND) * SCALE_Y))
    GROUND = WORLD_GROUND
    WORLD_DY = 0
    print('agrandada: centro del mapa (%.1f, %.1f), %dx%dx%d, %d bloques construidos puestos; santuario en %s, terraza %d'
          % (cxs, czs, W2, H2, L2, placed, SHRINE, SHRINE_TOP))
    return wx0, wz0


def encode(isl, ox, oy, oz, npc, arrive, rift):
    g = isl.grid
    H, L, W = g.shape
    out = bytearray()
    out += b'TFVI' + struct.pack('>i', 2)
    out += struct.pack('>iii', W, H, L)
    out += struct.pack('>iii', ox, oy, oz)
    out += struct.pack('>iii', *npc)
    out += struct.pack('>iiif', *arrive)
    out += struct.pack('>iii', *rift)
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
    agrandar(isl)
    cleared = clear_near_center(isl)
    plaza(isl)
    (kx, ky, kz), (ax, ay, az) = santuario(isl)
    # El centro de la plaza (la grieta) en x=0, z=0 del mundo
    ox, oy, oz = -PIT_CENTER[0], WORLD_DY, -PIT_CENTER[1]
    npc = (kx + ox, ky + oy, kz + oz)
    rift = (0, GROUND + 1 + oy, 0)
    # se llega al borde sur del santuario mirando hacia la grieta del centro
    yaw = -math.degrees(math.atan2(rift[0] - (ax + ox), rift[2] - (az + oz)))
    arrive = (ax + ox, ay + oy, az + oz, round(yaw, 1))
    runs, size = encode(isl, ox, oy, oz, npc, arrive, rift)
    print('isla: %dx%dx%d, %d estados, %d tramos, %.1f kB; caballero en %s, llegada %s, grieta %s; quitados %d bloques '
          'cerca del centro' % (isl.W, isl.H, isl.L, len(isl.names), runs, size / 1024, npc, arrive, rift, cleared))
    if '--preview' in sys.argv:
        dest = sys.argv[sys.argv.index('--preview') + 1]
        os.makedirs(dest, exist_ok=True)
        np.savez_compressed(os.path.join(dest, 'isla.npz'), g=isl.grid, names=np.array(isl.names))
    # (1.3.96) el Santuario del Alba (la isla del caballero con su burbuja), el Puente de Luz y los fragmentos de luz
    import isla_alba
    isla_alba.main(OUT)


if __name__ == '__main__':
    main()
