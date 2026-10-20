"""El santuario de las crates (TF Client 1.3.99): la escena en vivo donde se abre la crate.

    python3 -I tools/crates/santuario.py

El dueño: «una escena tipo de un santuario en la naturaleza con fuente de agua, tipo ruinas, con un haz del sol sobre
el lugar y eso, una escena completa en vivo, y ahí puesta la crate correctamente, la apertura, etc.»

Escribe assets/fscrates/escena/santuario.bin, que lee crates/client/scene/SanctuaryScene: los bloques (una paleta de
estados de bloque de Minecraft y la lista de bloques), los puntos de la escena (la crate, el estanque, los caños, los
cerezos, el sol...) y la ruta de la cámara de la cinemática (tick, ojo, punto al que mira y campo de visión). La vista
previa (santuario_preview.py) usa lo mismo.

Un claro del bosque con la luz dorada de la mañana: en medio, una fuente redonda en ruinas y, en el centro del agua,
el pedestal de la crate (calcita y piedra cincelada sobre una base con escalones), con pasaderas de piedra desde el
camino; cuatro caños en las diagonales echan el agua en arco hacia el pedestal (el agua, los chorros y las ondas los
dibuja el mod). Alrededor, una plaza de ladrillo de piedra rota y con musgo, un arco caído detrás (con lianas y
faroles), columnas a los lados (enteras y rotas, una tumbada), muros bajos derrumbados, cerezos en flor, robles,
abedules y azaleas, flores, helechos y hierba; el terreno sube en colinas por detrás y a los lados y cierra el
horizonte. El sol entra alto, por la izquierda y desde atrás, y cae sobre la crate.

Coordenadas: x al este, z al sur (la cámara mira al norte, desde el sur), y arriba; el suelo de la plaza está a y = 0
(los bloques del suelo, en y = -1). La crate se apoya en y = 2, centrada en (0.5, 0.5) (el bloque (0, 1, 0)).
"""
import math
import os
import struct

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'fscrates', 'escena',
                                    'santuario.bin'))

# el volumen de la escena (en bloques)
X0, X1 = -34, 34
Y0, Y1 = -6, 24
Z0, Z1 = -40, 30
NX, NY, NZ = X1 - X0 + 1, Y1 - Y0 + 1, Z1 - Z0 + 1

CRATE = (0.5, 2.0, 0.5)            # base de la crate (centro de su huella)
POOL_R = 5.6                       # radio del agua del estanque (centrado en la crate)
RIM_R = 6.7                        # hasta aquí, el borde de piedra
SPOUT_R = 7.4                      # los cuatro caños, en diagonal, fuera del borde
SPOUT_H = 3                        # la cabeza del caño (bloque), de donde sale el agua en arco hacia el pedestal
ARCH_Z = -11
PLAZA_C = (0.0, -1.0)
SUN = np.array([-0.46, 0.80, -0.38])
SUN /= np.linalg.norm(SUN)

# Flags de la paleta (los lee SanctuaryScene)
F_SWAY = 1      # se mece con el viento (hierba, flores, helechos): se dibuja en cada fotograma
F_WATER = 2     # agua: la dibuja el mod (la superficie con la textura del agua, sin el renderizador de fluidos)

SWAY = {'minecraft:grass', 'minecraft:tall_grass', 'minecraft:fern', 'minecraft:large_fern', 'minecraft:poppy',
        'minecraft:dandelion', 'minecraft:oxeye_daisy', 'minecraft:azure_bluet', 'minecraft:cornflower',
        'minecraft:allium', 'minecraft:lily_of_the_valley', 'minecraft:blue_orchid', 'minecraft:pink_tulip',
        'minecraft:white_tulip', 'minecraft:orange_tulip', 'minecraft:red_tulip', 'minecraft:peony',
        'minecraft:rose_bush', 'minecraft:lilac', 'minecraft:sunflower'}


class Scene:
    def __init__(self, seed=7):
        self.pal = ['minecraft:air']
        self.flags = [0]
        self.ids = {'minecraft:air': 0}
        self.a = np.zeros((NX, NY, NZ), np.uint16)
        self.rng = np.random.default_rng(seed)
        self.height = {}

    def pid(self, state):
        i = self.ids.get(state)
        if i is None:
            i = len(self.pal)
            self.pal.append(state)
            flags = 0
            name = state.split('[')[0]
            if name in SWAY:
                flags |= F_SWAY
            if name == 'minecraft:water':
                flags |= F_WATER
            self.flags.append(flags)
            self.ids[state] = i
        return i

    def inside(self, x, y, z):
        return X0 <= x <= X1 and Y0 <= y <= Y1 and Z0 <= z <= Z1

    def set(self, x, y, z, state, only_air=False):
        if not self.inside(x, y, z):
            return
        if only_air and self.a[x - X0, y - Y0, z - Z0] != 0:
            return
        self.a[x - X0, y - Y0, z - Z0] = self.pid(state)

    def get(self, x, y, z):
        if not self.inside(x, y, z):
            return 'minecraft:air'
        return self.pal[self.a[x - X0, y - Y0, z - Z0]]

    def air(self, x, y, z):
        return self.get(x, y, z) == 'minecraft:air'

    def name(self, x, y, z):
        return self.get(x, y, z).split('[')[0]

    def r(self):
        return self.rng.random()

    def pick(self, opts):
        """opts: [(peso, valor), ...]"""
        tot = sum(w for w, _ in opts)
        v = self.r() * tot
        for w, o in opts:
            v -= w
            if v <= 0:
                return o
        return opts[-1][1]


# ----------------------------------------------------------------------------------------------- ruido y utilidades
def vhash(ix, iz, seed):
    h = (ix * 374761393 + iz * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, z, seed):
    ix, iz = math.floor(x), math.floor(z)
    fx, fz = x - ix, z - iz
    sx, sz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a, b = vhash(ix, iz, seed), vhash(ix + 1, iz, seed)
    c, d = vhash(ix, iz + 1, seed), vhash(ix + 1, iz + 1, seed)
    return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz


def fbm(x, z, seed, octaves=3):
    v, amp, f, tot = 0.0, 1.0, 1.0, 0.0
    for o in range(octaves):
        v += vnoise(x * f, z * f, seed + o * 31) * amp
        tot += amp
        amp *= 0.5
        f *= 2.0
    return v / tot


def smooth(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def stairs(name, facing, half='bottom', shape='straight'):
    return f'minecraft:{name}[facing={facing},half={half},shape={shape},waterlogged=false]'


def slab(name, kind='bottom'):
    return f'minecraft:{name}[type={kind},waterlogged=false]'


def leaves(name):
    return f'minecraft:{name}[distance=1,persistent=true,waterlogged=false]'


def log(name, axis='y'):
    return f'minecraft:{name}[axis={axis}]'


def vine(n=False, s=False, e=False, w=False, up=False):
    b = lambda v: 'true' if v else 'false'  # noqa: E731
    return f'minecraft:vine[east={b(e)},north={b(n)},south={b(s)},up={b(up)},west={b(w)}]'


def tall(name, x, y, z, sc):
    sc.set(x, y, z, f'minecraft:{name}[half=lower]')
    sc.set(x, y + 1, z, f'minecraft:{name}[half=upper]')


def ground(sc, x, z):
    """La altura donde se apoya algo en (x, z): encima del bloque más alto del terreno."""
    return sc.height.get((x, z), -1) + 1


def pool_d(x, z):
    return math.hypot(x + 0.5 - CRATE[0], z + 0.5 - CRATE[2])


def in_plaza(x, z, margin=0.0):
    """La plaza: una elipse irregular alrededor de la fuente, y el camino que baja hacia la cámara."""
    dx, dz = (x + 0.5 - PLAZA_C[0]) / 12.0, (z + 0.5 - PLAZA_C[1]) / 12.5
    edge = 1.0 + (fbm(x * 0.21, z * 0.21, 5) - 0.5) * 0.28 - margin
    if dx * dx + dz * dz < edge * edge:
        return True
    # el camino (3 de ancho, con los bordes comidos)
    if 8 <= z <= 26 and abs(x) <= 1 + (1 if fbm(x * 0.4, z * 0.4, 9) > 0.62 else 0) - (margin > 0):
        return True
    return False


# ------------------------------------------------------------------------------------------------------- el terreno
def terrain(sc):
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            # lejos de la plaza sube en colinas (más por detrás y a los lados; poco por delante, donde está la cámara)
            dx, dz = x - PLAZA_C[0], (z - PLAZA_C[1]) * (0.9 if z < PLAZA_C[1] else 1.35)
            r = math.hypot(dx, dz)
            rise = smooth(15, 31, r) * (6.5 if z < 7 else 3.0)
            h = rise + (fbm(x * 0.09, z * 0.09, 1) - 0.5) * 2.6 * smooth(10, 20, r)
            if in_plaza(x, z) or r < 13:
                h = 0
            top = int(round(h)) - 1
            sc.height[(x, z)] = top
            for y in range(max(Y0, top - 2), top + 1):
                if y == top:
                    n = fbm(x * 0.17, z * 0.17, 3)
                    if n > 0.70:
                        st = 'minecraft:moss_block'
                    elif n < 0.25:
                        st = sc.pick([(3, 'minecraft:coarse_dirt'), (2, 'minecraft:podzol[snowy=false]'),
                                      (2, 'minecraft:rooted_dirt')])
                    else:
                        st = 'minecraft:grass_block[snowy=false]'
                else:
                    st = 'minecraft:dirt'
                sc.set(x, y, z, st)


# ----------------------------------------------------------------------------------------------------------- la plaza
def plaza(sc):
    for x in range(-18, 19):
        for z in range(-20, 28):
            if not in_plaza(x, z) or pool_d(x, z) < RIM_R + 0.2:
                continue
            # baldosas que faltan: hierba o musgo
            if fbm(x * 0.5, z * 0.5, 11) > 0.74:
                continue
            edge = not in_plaza(x, z, margin=0.12)
            st = sc.pick([(45, 'minecraft:stone_bricks'), (30, 'minecraft:mossy_stone_bricks'),
                          (15, 'minecraft:cracked_stone_bricks'), (6, 'minecraft:mossy_cobblestone'),
                          (4, 'minecraft:cobblestone')] if not edge else
                         [(30, 'minecraft:mossy_stone_bricks'), (25, 'minecraft:mossy_cobblestone'),
                          (20, 'minecraft:stone_bricks'), (25, 'minecraft:moss_block')])
            sc.set(x, -1, z, st)
            if edge and sc.r() < 0.35:
                sc.set(x, 0, z, 'minecraft:moss_carpet')


# ------------------------------------------------------------------------------------------ la fuente y el pedestal
def pool(sc):
    """El estanque redondo con el pedestal de la crate en medio, pasaderas desde el camino y cuatro caños."""
    for x in range(-8, 9):
        for z in range(-8, 9):
            d = pool_d(x, z)
            if d < POOL_R:
                sc.set(x, -2, z, 'minecraft:stone')
                sc.set(x, -1, z, sc.pick([(3, 'minecraft:mossy_cobblestone'), (2, 'minecraft:gravel'),
                                          (2, 'minecraft:mossy_stone_bricks'), (1, 'minecraft:clay')]))
                sc.set(x, 0, z, 'minecraft:water[level=0]')
            elif d < RIM_R:
                ang = math.atan2(z + 0.5 - CRATE[2], x + 0.5 - CRATE[0])
                chis = abs(math.sin(ang * 4)) < 0.16
                sc.set(x, -1, z, 'minecraft:stone_bricks')
                sc.set(x, 0, z, 'minecraft:chiseled_stone_bricks' if chis else
                       sc.pick([(5, 'minecraft:stone_bricks'), (4, 'minecraft:mossy_stone_bricks'),
                                (1, 'minecraft:cracked_stone_bricks')]))
                if not (abs(x) <= 1 and z > 0):      # el borde se abre donde llegan las pasaderas
                    sc.set(x, 1, z, slab('mossy_stone_brick_slab' if sc.r() < 0.4 else 'stone_brick_slab'))
    # el pedestal: una base en cruz que sale del agua (con escalones) y encima 3 × 3 de calcita y piedra cincelada
    for x in range(-2, 3):
        for z in range(-2, 3):
            if abs(x) == 2 and abs(z) == 2:
                continue
            sname = 'mossy_stone_brick_stairs' if sc.r() < 0.35 else 'stone_brick_stairs'
            if abs(x) <= 1 and abs(z) <= 1:
                st = 'minecraft:stone_bricks'
            elif z == 2:
                st = stairs(sname, 'north')
            elif z == -2:
                st = stairs(sname, 'south')
            elif x == -2:
                st = stairs(sname, 'east')
            else:
                st = stairs(sname, 'west')
            sc.set(x, 0, z, st)
            sc.set(x, -1, z, 'minecraft:stone_bricks')
    for x in range(-1, 2):
        for z in range(-1, 2):
            sc.set(x, 1, z, 'minecraft:chiseled_stone_bricks' if abs(x) == 1 and abs(z) == 1 else 'minecraft:calcite')
    # las pasaderas (losas altas, a ras del agua) desde el camino hasta el pedestal
    for z in (3, 4, 5):
        sc.set(0, 0, z, slab('mossy_stone_brick_slab' if z == 4 else 'stone_brick_slab', 'top'))
    sc.set(0, 0, 6, 'minecraft:chiseled_stone_bricks')
    # nenúfares
    for (x, z) in ((-3, 2), (3, -2), (-4, -2), (2, 4), (4, 2), (-2, -4)):
        if sc.name(x, 0, z) == 'minecraft:water':
            sc.set(x, 1, z, 'minecraft:lily_pad')
    # los caños: un pilar en cada diagonal, fuera del borde, con la cabeza cincelada de donde sale el agua
    for k, (x, z) in enumerate(spout_blocks()):
        sc.set(x, -1, z, 'minecraft:stone_bricks')
        sc.set(x, 0, z, 'minecraft:chiseled_stone_bricks')
        for y in range(1, SPOUT_H):
            sc.set(x, y, z, 'minecraft:mossy_stone_bricks' if (y + k) % 2 else 'minecraft:stone_bricks')
        sc.set(x, SPOUT_H, z, 'minecraft:chiseled_stone_bricks')
        sc.set(x, SPOUT_H + 1, z, slab('stone_brick_slab'))
        if k % 2 == 0:
            side_z = z + (1 if z > 0 else -1)          # la cara de fuera
            for y in range(1, SPOUT_H):
                sc.set(x, y, side_z, vine(n=side_z > z, s=side_z < z), only_air=True)
    # flores de agua alrededor del borde
    for x in range(-9, 10):
        for z in range(-9, 10):
            d = pool_d(x, z)
            if RIM_R <= d < RIM_R + 1.2 and sc.air(x, 0, z) and sc.r() < 0.22 and sc.name(x, -1, z) in (
                    'minecraft:grass_block', 'minecraft:moss_block'):
                sc.set(x, 0, z, sc.pick([(2, 'minecraft:blue_orchid'), (2, 'minecraft:fern'), (1, 'minecraft:grass')]))


def spout_blocks():
    out = []
    for k in range(4):
        ang = math.radians(45 + 90 * k)
        out.append((int(math.floor(CRATE[0] + math.cos(ang) * SPOUT_R)), int(math.floor(CRATE[2] + math.sin(ang) * SPOUT_R))))
    return out


def spouts():
    """Dónde empieza cada chorro: el lado de la cabeza del caño que mira al pedestal."""
    out = []
    for (bx, bz) in spout_blocks():
        x, z = bx + 0.5, bz + 0.5
        dx, dz = CRATE[0] - x, CRATE[2] - z
        L = math.hypot(dx, dz)
        out.append((x + dx / L * 0.62, SPOUT_H + 0.55, z + dz / L * 0.62))
    return out


# ------------------------------------------------------------------------------------------------- ruinas y columnas
def column(sc, x, z, h, full, mossy_p=0.4):
    """Una columna maciza: basa cincelada, fuste de ladrillo (con musgo) y capitel con ménsulas; las rotas acaban en
    un trozo agrietado con musgo encima."""
    base = ground(sc, x, z)
    sc.set(x, base - 1, z, 'minecraft:stone_bricks')
    sc.set(x, base, z, 'minecraft:chiseled_stone_bricks')
    for y in range(base + 1, base + h):
        if full or y < base + h - 1:
            sc.set(x, y, z, 'minecraft:mossy_stone_bricks' if sc.r() < mossy_p else 'minecraft:stone_bricks')
        else:
            sc.set(x, y, z, 'minecraft:cracked_stone_bricks')
            sc.set(x, y + 1, z, 'minecraft:moss_carpet' if sc.r() < 0.7 else 'minecraft:fern')
    top = base + h
    if full:
        sc.set(x, top, z, 'minecraft:chiseled_stone_bricks')
        for (dx, dz, face) in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'), (0, -1, 'south')):
            sc.set(x + dx, top, z + dz, stairs('stone_brick_stairs', face, 'top'))
        sc.set(x, top + 1, z, slab('mossy_stone_brick_slab'))
    # basa ancha (losas alrededor) y lianas por las caras
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        sc.set(x + dx, base, z + dz, slab('mossy_stone_brick_slab' if sc.r() < 0.5 else 'stone_brick_slab'),
               only_air=True)
    for (dx, dz, side) in ((0, 1, 'n'), (1, 0, 'w'), (-1, 0, 'e'), (0, -1, 's')):
        if sc.r() < 0.45:
            ytop = top - 1 if full else top - 2
            length = int(2 + sc.r() * max(1, ytop - base - 1))
            for k in range(length):
                y = ytop - k
                if y <= base:
                    break
                sc.set(x + dx, y, z + dz, vine(**{side: True}), only_air=True)


def arch(sc):
    z = ARCH_Z
    for x in (-4, 4):
        column(sc, x, z, 7, True, 0.5)
    # el dintel, roto a la derecha
    for x in range(-5, 6):
        if 1 <= x <= 2:
            continue
        st = 'minecraft:mossy_stone_bricks' if sc.r() < 0.45 else 'minecraft:stone_bricks'
        if x == -5:
            st = stairs('stone_brick_stairs', 'east', 'top')
        elif x == 5:
            st = stairs('stone_brick_stairs', 'west', 'top')
        elif x in (0, 3):
            st = 'minecraft:cracked_stone_bricks'
        sc.set(x, 8, z, st)
        if -4 < x < 4 and x not in (1, 2, 3) and sc.r() < 0.6:
            sc.set(x, 9, z, slab('mossy_stone_brick_slab') if sc.r() < 0.5 else 'minecraft:moss_carpet')
    sc.set(0, 9, z, 'minecraft:chiseled_stone_bricks')
    # lo que cayó del dintel
    sc.set(2, 0, z - 2, 'minecraft:cracked_stone_bricks')
    sc.set(3, 0, z - 1, slab('stone_brick_slab'))
    sc.set(1, 0, z - 3, 'minecraft:moss_carpet', only_air=True)
    # lianas (de bayas luminosas) colgando del dintel, y faroles
    for x in (-3, -1, 3):
        n = 2 + int(sc.r() * 3)
        for k in range(n):
            y = 7 - k
            berries = 'true' if sc.r() < 0.3 else 'false'
            if k == n - 1:
                sc.set(x, y, z, f'minecraft:cave_vines[age=0,berries={berries}]', only_air=True)
            else:
                sc.set(x, y, z, f'minecraft:cave_vines_plant[berries={berries}]', only_air=True)
    sc.set(-2, 7, z, 'minecraft:chain[axis=y,waterlogged=false]')
    sc.set(-2, 6, z, 'minecraft:lantern[hanging=true,waterlogged=false]')
    sc.set(4, 7, z + 1, 'minecraft:lantern[hanging=true,waterlogged=false]')


def colonnade(sc):
    left = [(-10, 8, True), (-4, 3, False), (2, 7, True), (8, 2, False)]
    right = [(-10, 4, False), (-4, 8, True), (2, 2, False), (8, 6, True)]
    for z, h, full in left:
        column(sc, -10, z, h, full)
    for z, h, full in right:
        column(sc, 11, z, h, full)
    # una columna caída delante a la derecha (tumbada en la hierba)
    for x in range(6, 10):
        sc.set(x, 0, 12, 'minecraft:mossy_stone_bricks' if x % 2 else 'minecraft:stone_bricks')
    sc.set(5, 0, 12, 'minecraft:chiseled_stone_bricks')
    sc.set(7, 1, 12, 'minecraft:moss_carpet')


def low_walls(sc):
    """Muros bajos derrumbados en el borde de la plaza (nunca delante de la cámara)."""
    segs = [((-14, -12), (-9, -16), 3), ((8, -16), (13, -12), 2), ((-15, -2), (-15, 4), 2), ((15, -6), (15, 0), 3),
            ((-7, -17), (-2, -18), 2)]
    for (ax, az), (bx, bz), hmax in segs:
        n = max(abs(bx - ax), abs(bz - az))
        for i in range(n + 1):
            x = round(ax + (bx - ax) * i / n)
            z = round(az + (bz - az) * i / n)
            base = ground(sc, x, z)
            h = max(1, int(round(hmax * (0.45 + 0.55 * math.sin(math.pi * i / max(1, n))) + (sc.r() - 0.5) * 1.4)))
            for y in range(base, base + h):
                sc.set(x, y, z, sc.pick([(5, 'minecraft:mossy_stone_bricks'), (4, 'minecraft:stone_bricks'),
                                         (2, 'minecraft:cracked_stone_bricks'), (2, 'minecraft:mossy_cobblestone')]))
            top = base + h
            sc.set(x, top, z, sc.pick([(3, 'minecraft:moss_carpet'), (2, slab('mossy_stone_brick_slab')),
                                       (1, 'minecraft:fern'), (2, 'minecraft:air')]))


# ------------------------------------------------------------------------------------------------------- los árboles
def canopy(sc, cx, cy, cz, rx, ry, rz, leaf, density=1.0, seed=0):
    for x in range(int(cx - rx - 1), int(cx + rx + 2)):
        for y in range(int(cy - ry - 1), int(cy + ry + 2)):
            for z in range(int(cz - rz - 1), int(cz + rz + 2)):
                d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 + ((z + 0.5 - cz) / rz) ** 2
                edge = 1.0 + (vhash(x * 7 + y, z * 3 - y, seed) - 0.5) * 0.5
                if d < edge * density:
                    sc.set(x, y, z, leaf, only_air=True)


def trunk_line(sc, a, b, st):
    n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2]))) + 1
    for i in range(n + 1):
        t = i / n
        x, y, z = (round(a[k] + (b[k] - a[k]) * t) for k in range(3))
        sc.set(x, y, z, st)


def cherry(sc, x, z, h, r, seed):
    y0 = ground(sc, x, z)
    lg, lf = log('cherry_log'), leaves('cherry_leaves')
    for y in range(y0, y0 + h):
        sc.set(x, y, z, lg)
    top = (x, y0 + h - 1, z)
    # ramas en diagonal (como los cerezos de verdad) y una copa en cada rama
    for k, (dx, dz) in enumerate(((1, 1), (-1, 1), (1, -1), (-1, -1))):
        if vhash(k, seed, 3) < 0.25:
            continue
        L = 2 + int(vhash(k, seed, 5) * 2)
        end = (x + dx * L, top[1] + 1 + int(vhash(k, seed, 7) * 2), z + dz * L)
        trunk_line(sc, top, end, log('cherry_log', 'x' if abs(dx) else 'z'))
        canopy(sc, end[0] + 0.5, end[1] + 1.3, end[2] + 0.5, r * 0.75, r * 0.45, r * 0.75, lf, seed=seed + k)
    canopy(sc, x + 0.5, top[1] + 2.2, z + 0.5, r, r * 0.55, r, lf, seed=seed)
    # cortinas de hojas colgando por debajo
    for i in range(int(r * 5)):
        ang = vhash(i, seed, 11) * math.tau
        rr = r * (0.6 + 0.4 * vhash(i, seed, 13))
        px, pz = round(x + math.cos(ang) * rr), round(z + math.sin(ang) * rr)
        for yy in range(top[1] + 1, top[1] - 2, -1):
            if not sc.air(px, yy + 1, pz):
                sc.set(px, yy, pz, lf, only_air=True)
    # pétalos rosas por el suelo, bajo la copa
    for px in range(int(x - r - 1), int(x + r + 2)):
        for pz in range(int(z - r - 1), int(z + r + 2)):
            if math.hypot(px - x, pz - z) < r + 0.5 and vhash(px, pz, seed + 17) < 0.55:
                gy = ground(sc, px, pz)
                if sc.air(px, gy, pz) and sc.name(px, gy - 1, pz) in ('minecraft:grass_block', 'minecraft:moss_block',
                                                                      'minecraft:podzol'):
                    face = ('north', 'east', 'south', 'west')[int(vhash(px, pz, 19) * 4) % 4]
                    amount = 1 + int(vhash(px, pz, 23) * 4) % 4
                    sc.set(px, gy, pz, f'minecraft:pink_petals[facing={face},flower_amount={amount}]')


def oak(sc, x, z, h, r, seed, kind='oak', big=False):
    y0 = ground(sc, x, z)
    lg, lf = log(f'{kind}_log'), leaves(f'{kind}_leaves')
    xs = (x, x + 1) if big else (x,)
    zs = (z, z + 1) if big else (z,)
    for y in range(y0 - 1, y0 + h):
        for tx in xs:
            for tz in zs:
                sc.set(tx, y, tz, lg)
    cx, cz = x + (1.0 if big else 0.5), z + (1.0 if big else 0.5)
    # ramas gruesas
    for k in range(3 if big else 2):
        ang = vhash(k, seed, 29) * math.tau
        L = 2.5 + vhash(k, seed, 31) * 1.5
        a = (x, y0 + h - 3 + k, z)
        b = (round(x + math.cos(ang) * L), y0 + h - 1 + k, round(z + math.sin(ang) * L))
        trunk_line(sc, a, b, log(f'{kind}_log', 'x'))
        canopy(sc, b[0] + 0.5, b[1] + 1.0, b[2] + 0.5, r * 0.6, r * 0.42, r * 0.6, lf, seed=seed + 40 + k)
    canopy(sc, cx, y0 + h + 0.8, cz, r, r * 0.62, r, lf, seed=seed)


def birch(sc, x, z, h, seed):
    y0 = ground(sc, x, z)
    for y in range(y0, y0 + h):
        sc.set(x, y, z, log('birch_log'))
    canopy(sc, x + 0.5, y0 + h - 0.5, z + 0.5, 2.3, 2.6, 2.3, leaves('birch_leaves'), seed=seed)


def azalea_bush(sc, x, z, seed):
    y0 = ground(sc, x, z)
    for dx in (0, 1):
        for dz in (0, 1):
            if vhash(x + dx, z + dz, seed) < 0.85:
                sc.set(x + dx, y0, z + dz, leaves('flowering_azalea_leaves' if vhash(dx, dz, seed + 1) < 0.5
                                                  else 'azalea_leaves'), only_air=True)
                if vhash(x + dx, z + dz, seed + 2) < 0.4:
                    sc.set(x + dx, y0 + 1, z + dz, leaves('azalea_leaves'), only_air=True)


def trees(sc):
    cherry(sc, -14, -3, 6, 4.4, 101)
    cherry(sc, 12, -15, 7, 4.6, 102)
    cherry(sc, 13, 10, 4, 3.0, 103)
    cherry(sc, -15, -19, 5, 3.6, 104)
    for (x, z, h, r, s, kind, big) in ((-23, -17, 9, 6.0, 201, 'dark_oak', True), (-8, -27, 8, 5.5, 202, 'oak', False),
                                        (6, -28, 10, 6.5, 203, 'dark_oak', True), (21, -21, 9, 6.0, 204, 'oak', False),
                                        (-26, -2, 8, 5.5, 205, 'oak', False), (25, -5, 9, 6.0, 206, 'dark_oak', True),
                                        (-22, 14, 7, 5.0, 207, 'oak', False), (22, 15, 7, 5.0, 208, 'oak', False),
                                        (-3, -33, 9, 6.0, 209, 'oak', False), (15, -32, 8, 5.5, 210, 'oak', False),
                                        (-17, -32, 8, 5.5, 211, 'dark_oak', True), (29, 4, 8, 5.0, 212, 'oak', False),
                                        (-30, 6, 8, 5.0, 213, 'oak', False)):
        oak(sc, x, z, h, r, s, kind, big)
    for (x, z, h, s) in ((-18, 4, 8, 301), (18, 6, 7, 302), (16, -9, 8, 303), (-19, -9, 7, 304), (-17, 20, 6, 305),
                         (15, 22, 7, 306), (-10, -20, 7, 307), (9, -21, 8, 308), (21, -12, 7, 309), (-22, -11, 8, 310)):
        birch(sc, x, z, h, s)
    # el bosque de las colinas (detrás y a los lados), para que el fondo sea bosque y no una ladera
    k = 0
    for (x, z) in ((-12, -24), (0, -23), (12, -25), (-20, -24), (20, -26), (-27, -12), (27, -14), (-28, 0), (28, -2),
                   (-4, -29), (8, -32), (-14, -30), (24, 9), (-25, 8)):
        k += 1
        oak(sc, x, z, 5 + int(vhash(x, z, 5) * 3), 3.4 + vhash(x, z, 7) * 1.4, 500 + k,
            'oak' if vhash(x, z, 9) < 0.7 else 'dark_oak', False)
    for (x, z, s) in ((-13, 3, 401), (13, -3, 402), (-7, -15, 403), (6, -16, 404), (-13, -10, 405), (13, -9, 406),
                      (-5, 13, 407), (5, 14, 408)):
        azalea_bush(sc, x, z, s)


# --------------------------------------------------------------------------------------------- hierba, flores, setas
FLOWER_ZONES = [('minecraft:poppy', 'minecraft:oxeye_daisy'), ('minecraft:cornflower', 'minecraft:azure_bluet'),
                ('minecraft:allium', 'minecraft:pink_tulip'), ('minecraft:dandelion', 'minecraft:white_tulip'),
                ('minecraft:lily_of_the_valley', 'minecraft:oxeye_daisy')]


def flora(sc):
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            gy = ground(sc, x, z)
            below = sc.name(x, gy - 1, z)
            if not sc.air(x, gy, z):
                continue
            if below not in ('minecraft:grass_block', 'minecraft:moss_block', 'minecraft:podzol'):
                if below in ('minecraft:stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:mossy_cobblestone',
                             'minecraft:cracked_stone_bricks') and sc.r() < 0.05 and pool_d(x, z) > RIM_R + 1:
                    sc.set(x, gy, z, 'minecraft:grass')
                continue
            v = sc.r()
            zone = fbm(x * 0.13, z * 0.13, 21)
            flowers = fbm(x * 0.3, z * 0.3, 23)
            far = math.hypot(x - PLAZA_C[0], z - PLAZA_C[1]) > 19
            if flowers > 0.62 and v < (0.18 if far else 0.55):
                pair = FLOWER_ZONES[int(fbm(x * 0.07, z * 0.07, 25) * 10) % len(FLOWER_ZONES)]
                sc.set(x, gy, z, pair[0] if sc.r() < 0.6 else pair[1])
            elif v < 0.30:
                sc.set(x, gy, z, 'minecraft:grass')
            elif v < 0.37 and sc.air(x, gy + 1, z):
                tall(sc.pick([(4, 'tall_grass'), (1, 'large_fern')]), x, gy, z, sc)
            elif v < 0.43:
                sc.set(x, gy, z, 'minecraft:fern')
            elif v < 0.435 and sc.air(x, gy + 1, z):
                tall(sc.pick([(1, 'peony'), (1, 'rose_bush'), (1, 'lilac')]), x, gy, z, sc)
            elif v < 0.445 and zone < 0.4:
                sc.set(x, gy, z, sc.pick([(1, 'minecraft:brown_mushroom'), (1, 'minecraft:red_mushroom')]))
            elif v < 0.46:
                sc.set(x, gy, z, sc.pick([(1, 'minecraft:azalea'), (1, 'minecraft:flowering_azalea')]))


def clear_view(sc):
    """Nada de hojas ni troncos entre la ruta de la cámara y la crate."""
    for t in range(0, 401, 4):
        eye, at, _ = camera_at(t)
        d = np.array(at) - np.array(eye)
        L = float(np.linalg.norm(d))
        for s in np.arange(0.0, L, 0.4):
            p = np.array(eye) + d / L * s
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        x, y, z = int(math.floor(p[0])) + dx, int(math.floor(p[1])) + dy, int(math.floor(p[2])) + dz
                        n = sc.name(x, y, z)
                        if 'leaves' in n or n.endswith('_log'):
                            sc.set(x, y, z, 'minecraft:air')


# ------------------------------------------------------------------------------------------- la cámara y los puntos
# (tick, ojo, mira, campo de visión): la cinemática (CrateCinematicScreen) la recorre con Catmull-Rom
CAMERA = [
    (0, (-12.0, 13.0, 25.0), (0.5, 2.5, -2.0), 62.0),      # llegada: desde lo alto, sobre el camino
    (34, (-6.5, 7.5, 17.0), (0.5, 2.6, -1.0), 58.0),
    (62, (-3.2, 4.4, 10.5), (0.5, 2.6, 0.0), 52.0),        # la crate en el haz de luz, en medio del agua
    (96, (-1.3, 3.2, 4.4), (0.6, 2.5, 1.0), 46.0),         # la llave llega a la cerradura
    (116, (-0.9, 3.0, 3.7), (0.55, 2.5, 1.0), 44.0),       # gira
    (138, (-2.0, 3.8, 6.2), (0.5, 2.9, 0.5), 52.0),        # ¡se abre! (retrocede)
    (166, (-0.5, 4.6, 7.2), (0.5, 3.7, 0.5), 54.0),        # la ruleta (el anillo, justo encima de la crate)
    (230, (1.3, 4.5, 6.8), (0.5, 3.75, 0.5), 52.0),
    (288, (0.6, 4.3, 5.9), (0.5, 3.85, 0.9), 48.0),
    (300, (0.55, 4.35, 4.8), (0.5, 3.95, 1.5), 46.0),      # el premio, delante, en el centro
    (400, (-1.0, 4.6, 5.4), (0.5, 4.0, 1.4), 48.0),
]


def catmull(p0, p1, p2, p3, t):
    t2, t3 = t * t, t * t * t
    return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)


def camera_at(tick):
    """Igual que CrateCinematicScreen.camera: Catmull-Rom entre las claves, con el tiempo algo suavizado."""
    keys = CAMERA
    i = 0
    while i < len(keys) - 2 and keys[i + 1][0] <= tick:
        i += 1
    k1, k2 = keys[i], keys[i + 1]
    k0, k3 = keys[max(0, i - 1)], keys[min(len(keys) - 1, i + 2)]
    t = min(1.0, max(0.0, (tick - k1[0]) / max(1e-6, k2[0] - k1[0])))
    t = t * t * (3 - 2 * t) * 0.35 + t * 0.65
    eye = catmull(*(np.array(k[1], float) for k in (k0, k1, k2, k3)), t)
    at = catmull(*(np.array(k[2], float) for k in (k0, k1, k2, k3)), t)
    fov = k1[3] + (k2[3] - k1[3]) * t
    return eye, at, fov


def anchors():
    """Puntos de la escena: nombre → (x, y, z, w)."""
    pts = {
        'crate': CRATE,
        'pool': (CRATE[0], 0.875, CRATE[2], POOL_R),        # superficie del agua del estanque y su radio
        'sun': tuple(float(v) for v in SUN),                # dirección hacia el sol
        'cherry0': (-13.5, 8.5, -2.5, 4.4), 'cherry1': (12.5, 10.0, -14.5, 4.6), 'cherry2': (13.5, 6.0, 10.5, 3.0),
        'cherry3': (-14.5, 7.5, -18.5, 3.6),
        'flowers0': (-7.0, 0.3, 8.0, 3.0), 'flowers1': (7.5, 0.3, 6.0, 3.0), 'flowers2': (-6.0, 0.3, -9.0, 3.0),
    }
    for i, p in enumerate(spouts()):
        pts[f'spout{i}'] = p
    return pts


def build():
    sc = Scene()
    terrain(sc)
    plaza(sc)
    pool(sc)
    arch(sc)
    colonnade(sc)
    low_walls(sc)
    trees(sc)
    flora(sc)
    clear_view(sc)
    return sc


def write(sc, path):
    blocks = np.argwhere(sc.a != 0)
    out = bytearray()
    out += b'TFSC'
    out += struct.pack('>i', 1)
    out += struct.pack('>iii', X0, Y0, Z0)
    out += struct.pack('>i', len(sc.pal))
    for s, f in zip(sc.pal, sc.flags):
        b = s.encode('utf-8')
        out += struct.pack('>H', len(b)) + b + struct.pack('>B', f)
    out += struct.pack('>i', len(blocks))
    for (i, j, k) in blocks:
        out += struct.pack('>BBBH', int(i), int(j), int(k), int(sc.a[i, j, k]))
    pts = anchors()
    out += struct.pack('>i', len(pts))
    for name, v in pts.items():
        b = name.encode('utf-8')
        v = tuple(float(c) for c in v) + (0.0,) * (4 - len(v))
        out += struct.pack('>H', len(b)) + b + struct.pack('>ffff', *v)
    out += struct.pack('>i', len(CAMERA))
    for t, eye, at, fov in CAMERA:
        out += struct.pack('>f', t) + struct.pack('>fff', *eye) + struct.pack('>fff', *at) + struct.pack('>f', fov)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(bytes(out))
    return len(blocks), len(out)


def main():
    sc = build()
    n, size = write(sc, OUT)
    print(f'{n} bloques, {len(sc.pal)} estados, {size / 1024:.0f} KB → {os.path.relpath(OUT)}')


if __name__ == '__main__':
    main()
