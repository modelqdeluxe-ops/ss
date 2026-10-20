"""El Bosque de las Hadas: la escena en vivo de la apertura de las crates (TF Client 1.3.99).

    python3 -I tools/crates/santuario.py

El dueño: «una escena tipo de un santuario en la naturaleza con fuente de agua, tipo ruinas, con un haz del sol sobre
el lugar, una escena completa en vivo»; luego: «que aparezca ya cerca de la crate; un shader bonito, el resplandor más
bello, las fuentes de agua mejor diseñadas, la estructura mejor diseñada, el fondo… está como en una pradera cualquiera»,
«esas ruinas más bonitas, mejor trabajadas, todo», «algo bello, como un bosque de hadas».

Escribe assets/fscrates/escena/santuario.bin (lo lee crates/client/scene/SanctuaryScene): la paleta de estados de
bloque y los bloques, los puntos de la escena, la ruta de la cámara y las luces mágicas (dónde brilla algo, de qué color:
el mod les pone su halo y alumbra lo de alrededor). La vista previa (santuario_preview.py) usa lo mismo.

Un claro encantado al atardecer, dentro de un bosque de árboles gigantes y viejos cuyas copas (azalea en flor, cerezo)
forman una cúpula con un hueco por donde entra el sol. En medio, un estanque turquesa que brilla desde el fondo (linternas
de mar, pepinos de mar) con la crate en un pedestal de mármol; cuatro fuentes en forma de copa sobre el borde echan el
agua en arco hacia ella. Detrás, un arco élfico de mármol cubierto de azalea en flor y flores de esporas, con faroles; y
detrás del arco, un risco de musgo con su cascada, que cae en un remanso y entra en el estanque por un canal. Alrededor,
una columnata en ruinas (columnas enteras con su cornisa, otras rotas, tambores caídos), piedras con liquen que brilla y
amatistas, setas gigantes y setas lámpara, corros de hadas, flores por todas partes, raíces enormes, bayas luminosas.

Coordenadas: x al este, z al sur (la cámara mira al norte, desde el sur), y arriba; el suelo del claro está a y = 0 (los
bloques del suelo, en y = -1). La crate se apoya en y = 2, centrada en (0.5, 0.5) (el bloque (0, 1, 0)).
"""
import math
import os
import struct

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'fscrates', 'escena',
                                    'santuario.bin'))

X0, X1 = -44, 44
Y0, Y1 = -6, 40
Z0, Z1 = -48, 34
NX, NY, NZ = X1 - X0 + 1, Y1 - Y0 + 1, Z1 - Z0 + 1

CRATE = (0.5, 2.0, 0.5)
POOL_R = 5.6                       # el agua del estanque
RIM_R = 6.7                        # el borde de mármol
FOUNT_R = 6.15                     # las fuentes-copa, en el borde (en las diagonales)
BOWL_Y = 4                         # la copa (con su agua)
ARCH_Z = -8                        # el arco élfico
CLIFF_Z = -17                      # el montículo de la cascada (su cara, en el centro)
FALL_TOP = 10                      # de donde cae la cascada
FALL_W = 2                         # la cascada, de x = -2 a x = 2
GLADE_R = 13.0                     # el claro
# el haz de luz: entra casi desde arriba por el hueco de las copas (un poco desde atrás a la izquierda) y cae sobre la
# crate (el resto del pantano, en penumbra)
SUN = np.array([-0.16, 0.97, -0.20])
SUN /= np.linalg.norm(SUN)

F_SWAY, F_WATER, F_GLOW = 1, 2, 4

SWAY = {'minecraft:grass', 'minecraft:tall_grass', 'minecraft:fern', 'minecraft:large_fern', 'minecraft:poppy',
        'minecraft:dandelion', 'minecraft:oxeye_daisy', 'minecraft:azure_bluet', 'minecraft:cornflower',
        'minecraft:allium', 'minecraft:lily_of_the_valley', 'minecraft:blue_orchid', 'minecraft:pink_tulip',
        'minecraft:white_tulip', 'minecraft:orange_tulip', 'minecraft:red_tulip', 'minecraft:peony',
        'minecraft:rose_bush', 'minecraft:lilac', 'minecraft:torchflower', 'minecraft:pitcher_plant'}
# lo que brilla: no le llega la sombra y lleva halo (r, g, b, tamaño)
GLOW = {'minecraft:lantern': (1.0, 0.72, 0.38, 1.0), 'minecraft:shroomlight': (1.0, 0.62, 0.30, 1.2),
        'minecraft:sea_lantern': (0.55, 0.95, 1.0, 1.1), 'minecraft:glow_lichen': (0.55, 1.0, 0.85, 0.35),
        'minecraft:cave_vines': (1.0, 0.75, 0.35, 0.45), 'minecraft:cave_vines_plant': (1.0, 0.75, 0.35, 0.45),
        'minecraft:pearlescent_froglight': (1.0, 0.75, 0.95, 1.2), 'minecraft:ochre_froglight': (1.0, 0.86, 0.45, 1.2),
        'minecraft:verdant_froglight': (0.7, 1.0, 0.6, 1.2), 'minecraft:amethyst_cluster': (0.85, 0.55, 1.0, 0.7),
        'minecraft:large_amethyst_bud': (0.85, 0.55, 1.0, 0.5), 'minecraft:end_rod': (1.0, 0.95, 0.9, 0.9),
        'minecraft:sea_pickle': (0.6, 1.0, 0.7, 0.5), 'minecraft:soul_lantern': (0.45, 0.9, 1.0, 1.0)}
STONE = ('minecraft:tuff', 'minecraft:mossy_cobblestone', 'minecraft:cobbled_deepslate', 'minecraft:andesite', 'minecraft:moss_block')


class Scene:
    def __init__(self, seed=11):
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
            name = state.split('[')[0]
            f = 0
            if name in SWAY:
                f |= F_SWAY
            if name == 'minecraft:water':
                f |= F_WATER
            if name in GLOW and not (name.startswith('minecraft:cave_vines') and 'berries=false' in state):
                f |= F_GLOW
            self.flags.append(f)
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


def lichen(face):
    """Liquen luminoso pegado a una cara (face: el lado donde está el bloque que lo sostiene)."""
    f = {k: 'false' for k in ('down', 'east', 'north', 'south', 'up', 'west')}
    f[face] = 'true'
    return 'minecraft:glow_lichen[' + ','.join(f'{k}={v}' for k, v in sorted(f.items())) + ',waterlogged=false]'


def tall(name, x, y, z, sc):
    sc.set(x, y, z, f'minecraft:{name}[half=lower]')
    sc.set(x, y + 1, z, f'minecraft:{name}[half=upper]')


def ground(sc, x, z):
    return sc.height.get((x, z), -1) + 1


def pool_d(x, z):
    return math.hypot(x + 0.5 - CRATE[0], z + 0.5 - CRATE[2])


def glade_d(x, z):
    return math.hypot(x + 0.5 - CRATE[0], (z + 0.5 - CRATE[2]) * (0.85 if z < 0 else 1.0))


# ------------------------------------------------------------------------------------------------------- el suelo
def terrain(sc):
    """El suelo: el claro llano y, fuera, el suelo del bosque (musgo, podsol, hierba) que sube en colinas por todos
    lados (detrás de los árboles no se ve ningún horizonte: solo más bosque)."""
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            r = glade_d(x, z)
            h = 0.0
            if r > GLADE_R:
                h = (fbm(x * 0.08, z * 0.08, 1) - 0.4) * 3.0 * smooth(GLADE_R, GLADE_R + 8, r)
                h += smooth(15, 38, r) * (8.0 + 4.0 * fbm(x * 0.05, z * 0.05, 9))
            top = int(round(h)) - 1
            sc.height[(x, z)] = top
            for y in range(max(Y0, top - 2), top + 1):
                if y == top:
                    n = fbm(x * 0.15, z * 0.15, 3)
                    if r > GLADE_R + 1:
                        st = sc.pick([(4, 'minecraft:moss_block'), (3, 'minecraft:podzol[snowy=false]'),
                                      (2, 'minecraft:grass_block[snowy=false]'), (1, 'minecraft:rooted_dirt')]) if n < 0.6 else \
                            'minecraft:grass_block[snowy=false]'
                    elif n > 0.66:
                        st = 'minecraft:moss_block'
                    else:
                        st = 'minecraft:grass_block[snowy=false]'
                else:
                    st = 'minecraft:dirt'
                sc.set(x, y, z, st)


# ---------------------------------------------------------------------------------- el estanque y el pedestal
def pool(sc):
    """El estanque de las hadas: fondo que brilla (prismarina, arena, alguna linterna de mar y pepinos de mar), borde
    de piedra vieja con musgo (nada de mármol blanco), nenúfares; en medio, el pedestal de piedra cincelada."""
    cx, cz = CRATE[0], CRATE[2]
    for x in range(-8, 9):
        for z in range(-8, 9):
            d = pool_d(x, z)
            if d < POOL_R:
                sc.set(x, -2, z, 'minecraft:stone')
                v = vhash(x, z, 77)
                st = ('minecraft:sea_lantern' if v < 0.07 and d > 2.6 else
                      'minecraft:prismarine_bricks' if v < 0.40 else
                      'minecraft:dark_prismarine' if v < 0.62 else 'minecraft:sand')
                sc.set(x, -1, z, st)
                sc.set(x, 0, z, 'minecraft:water[level=0]')
            elif d < RIM_R:
                ang = math.atan2(z + 0.5 - cz, x + 0.5 - cx)
                sc.set(x, -1, z, 'minecraft:stone_bricks')
                sc.set(x, 0, z, 'minecraft:chiseled_stone_bricks' if abs(math.sin(ang * 4)) < 0.12 else
                       sc.pick([(5, 'minecraft:mossy_stone_bricks'), (3, 'minecraft:stone_bricks'), (2, 'minecraft:cracked_stone_bricks'),
                                (2, 'minecraft:moss_block')]))
                if abs(x) > 1 and sc.r() < 0.8:      # el borde se abre hacia el camino y hacia el canal de la cascada
                    sc.set(x, 1, z, sc.pick([(5, slab('mossy_stone_brick_slab')), (2, slab('stone_brick_slab')),
                                             (3, 'minecraft:moss_carpet')]))
    # el pedestal: base en cruz de piedra con musgo que sale del agua con escalones; arriba, piedra cincelada con musgo,
    # pétalos rosas, dos amatistas detrás de la crate y liquen que brilla en los costados
    for x in range(-2, 3):
        for z in range(-2, 3):
            if abs(x) == 2 and abs(z) == 2:
                continue
            if abs(x) <= 1 and abs(z) <= 1:
                st = 'minecraft:mossy_stone_bricks'
            elif z == 2:
                st = stairs('mossy_stone_brick_stairs', 'north')
            elif z == -2:
                st = stairs('mossy_stone_brick_stairs', 'south')
            elif x == -2:
                st = stairs('mossy_stone_brick_stairs', 'east')
            else:
                st = stairs('mossy_stone_brick_stairs', 'west')
            sc.set(x, 0, z, st)
            sc.set(x, -1, z, 'minecraft:stone_bricks')
    for x in range(-1, 2):
        for z in range(-1, 2):
            corner = abs(x) == 1 and abs(z) == 1
            sc.set(x, 1, z, 'minecraft:chiseled_stone_bricks' if corner or (x == 0 and z == 0) else 'minecraft:mossy_stone_bricks')
    sc.set(-1, 2, 0, 'minecraft:moss_carpet')
    sc.set(0, 2, -1, 'minecraft:moss_carpet')
    sc.set(1, 2, 0, 'minecraft:pink_petals[facing=west,flower_amount=4]')
    sc.set(0, 2, 1, 'minecraft:pink_petals[facing=north,flower_amount=2]')
    sc.set(-1, 2, 1, 'minecraft:pink_petals[facing=east,flower_amount=3]')
    sc.set(1, 2, 1, 'minecraft:moss_carpet')
    sc.set(-1, 2, -1, 'minecraft:amethyst_cluster[facing=up,waterlogged=false]')
    sc.set(1, 2, -1, 'minecraft:amethyst_cluster[facing=up,waterlogged=false]')
    for (x, z, face) in ((-2, -1, 'east'), (-2, 0, 'east'), (2, 0, 'west'), (2, 1, 'west'), (0, -2, 'south'), (-1, 2, 'north'),
                         (1, 2, 'north')):
        sc.set(x, 1, z, lichen(face), only_air=True)
    # pasaderas de piedra desde el camino
    for z in (3, 4, 5):
        sc.set(0, 0, z, slab('mossy_stone_brick_slab', 'top'))
    sc.set(0, 0, 6, 'minecraft:chiseled_stone_bricks')
    # nenúfares y pepinos de mar que brillan en el fondo
    for (x, z) in ((-3, 2), (3, -2), (-4, -2), (2, 4), (4, 2), (-2, -4), (3, 3), (-3, -3)):
        if sc.name(x, 0, z) == 'minecraft:water':
            sc.set(x, 1, z, 'minecraft:lily_pad')
    for (x, z) in ((-3, 1), (2, -3), (4, 0), (-1, 4), (1, -4)):
        if sc.name(x, 0, z) == 'minecraft:water':
            n = 1 + int(vhash(x, z, 5) * 4) % 4
            sc.set(x, 0, z, f'minecraft:sea_pickle[pickles={n},waterlogged=true]')
            sc.set(x, -1, z, 'minecraft:sand')


FALLING = 'minecraft:water[level=8]'      # agua que cae (el juego la dibuja como una cascada de verdad)


SPRINGS = ((-5, -4, 1601), (5, -4, 1602))


def spring_points():
    """Dónde cae el chorro de cada peña (x, y del agua, z, 1): para la espuma y las ondas del mod."""
    return [(bx + 2 * (1 if bx < 0 else -1) + 0.5, 0.875, bz + 0.5, 1.0) for (bx, bz, _) in SPRINGS]


def springs(sc):
    """Las fuentes del estanque: dos peñas de musgo en el borde de atrás, a los lados, de las que mana un chorro de agua
    de verdad (agua que cae, como en el juego) al estanque; liquen, helechos y bayas luminosas."""
    for (bx, bz, seed) in SPRINGS:
        hts = {}
        for i in range(-1, 2):
            for j in range(-2, 1):
                h = 4 + (1 if j == -2 else 0) - (1 if abs(i) == 1 and j == 0 else 0) + int(vhash(i, j, seed) * 1.6)
                hts[(i, j)] = h
                for y in range(-1, h):
                    n = vhash(bx * 3 + i + y, bz * 5 + j - y, seed)
                    st = STONE[0 if n < 0.25 else 1 if n < 0.55 else 3 if n < 0.7 else 4]
                    sc.set(bx + i, y, bz + j, st)
                sc.set(bx + i, h, bz + j, sc.pick([(3, 'minecraft:moss_carpet'), (1, 'minecraft:fern'), (1, 'minecraft:air')]))
        # la boca: una muesca arriba, en el lado que da al estanque, con agua; el chorro cae por ese lado hasta el agua
        side = 1 if bx < 0 else -1
        top = hts[(side, 0)] - 1
        sc.set(bx + side, top, bz, 'minecraft:water[level=0]')
        sc.set(bx + side, top + 1, bz, 'minecraft:air')
        # la muesca, cerrada por delante con una piedra con musgo (el agua sale de la roca, no de un cubo suelto)
        for i in (0, side):
            for y in range(-1, top + (1 if i == side else 0)):
                sc.set(bx + i, y, bz + 1, STONE[1] if vhash(i, y, seed + 5) < 0.6 else STONE[4])
            sc.set(bx + i, top + (1 if i == side else 0), bz + 1, 'minecraft:moss_carpet', only_air=True)
        for y in range(1, top + 1):
            sc.set(bx + 2 * side, y, bz, FALLING)
        for (dz, face) in ((-1, 'south'), (1, 'north')):
            for y in range(1, top):
                if vhash(y, bz + dz, seed + 3) < 0.4:
                    sc.set(bx + 2 * side, y, bz + dz, lichen(face), only_air=True)
        sc.set(bx + (2 if bx > 0 else -2), 2, bz - 1, 'minecraft:cave_vines[age=0,berries=true]', only_air=True)


# ------------------------------------------------------------------------------------- el risco y la cascada
def cliff(sc):
    """Un risco de musgo y piedra en semicírculo detrás del arco, con la cascada en medio: cae a un remanso al pie y un
    canal la lleva al estanque. Liquen que brilla, enredaderas, amatistas; helechos, flores y azaleas arriba."""
    for x in range(-11, 12):
        face = CLIFF_Z + 0.05 * x * x + (fbm(x * 0.3, 7, 41) - 0.5) * 1.6
        for z in range(-28, -10):
            if z > face:
                continue
            depth = face - z
            hmax = FALL_TOP - 1 - 0.075 * x * x + (fbm(x * 0.22, z * 0.22, 43) - 0.5) * 2.5 + min(2.0, depth * 0.3)
            hmax = int(round(hmax))
            base = ground(sc, x, z)
            if hmax <= base:
                continue
            for y in range(base - 1, hmax):
                n = vhash(x * 3 + y, z * 5 - y, 47)
                st = STONE[0 if n < 0.3 else 1 if n < 0.55 else 2 if n < 0.68 else 3 if n < 0.78 else 4]
                sc.set(x, y, z, st)
            sc.set(x, hmax, z, 'minecraft:grass_block[snowy=false]')
            sc.height[(x, z)] = hmax
    # el arroyo de arriba (agua) que llega a la boca de la cascada, y el hueco por donde cae
    for x in range(-FALL_W, FALL_W + 1):
        for z in range(-28, CLIFF_Z + 3):
            top = sc.height.get((x, z), FALL_TOP)
            if z <= CLIFF_Z - 1:
                sc.set(x, FALL_TOP - 1, z, 'minecraft:mossy_cobblestone')
                sc.set(x, FALL_TOP, z, 'minecraft:water[level=0]')
                for y in range(FALL_TOP + 1, top + 2):
                    sc.set(x, y, z, 'minecraft:air')
            else:
                for y in range(0, top + 2):
                    sc.set(x, y, z, 'minecraft:air')
    # la cascada: agua que cae de verdad (el juego la dibuja como en el mundo), del arroyo de arriba al remanso
    for x in range(-FALL_W, FALL_W + 1):
        for y in range(1, FALL_TOP + 1):
            sc.set(x, y, CLIFF_Z, FALLING)
    # el remanso al pie y el canal hasta el estanque
    for x in range(-4, 5):
        for z in range(CLIFF_Z - 1, -10):
            if math.hypot(x * 0.8, (z - (CLIFF_Z + 2)) * 1.2) < 3.3:
                sc.set(x, -2, z, 'minecraft:stone')
                sc.set(x, -1, z, sc.pick([(3, 'minecraft:prismarine_bricks'), (1, 'minecraft:sea_lantern'), (2, 'minecraft:sand')]))
                sc.set(x, 0, z, 'minecraft:water[level=0]')
                for y in range(1, 3):
                    if 'stone' in sc.name(x, y, z) or sc.name(x, y, z) in STONE:
                        sc.set(x, y, z, 'minecraft:air')
    for z in range(-13, -4):
        for x in (-1, 0, 1):
            if pool_d(x, z) >= POOL_R:
                sc.set(x, -1, z, 'minecraft:prismarine_bricks')
                sc.set(x, 0, z, 'minecraft:water[level=0]')
                sc.set(x, 1, z, 'minecraft:air')
        for x in (-2, 2):
            if pool_d(x, z) >= RIM_R - 0.3 and sc.name(x, 0, z) != 'minecraft:water':
                sc.set(x, 0, z, 'minecraft:mossy_stone_bricks')
                sc.set(x, 1, z, slab('mossy_stone_brick_slab'))
    # vida en la cara del risco: liquen que brilla, enredaderas y amatistas
    for x in range(-12, 13):
        if abs(x) <= FALL_W:
            continue
        for y in range(1, 14):
            for z in range(-28, -9):
                if sc.air(x, y, z) and sc.name(x, y, z - 1) in STONE:
                    v = vhash(x * 7 + y, z, 53)
                    if v < 0.10:
                        sc.set(x, y, z, lichen('north'))
                    elif v < 0.22:
                        sc.set(x, y, z, vine(n=True))
                    elif v < 0.235:
                        sc.set(x, y, z, 'minecraft:amethyst_cluster[facing=south,waterlogged=false]')
    # arriba: hierba, flores, helechos y azaleas
    for (x, z) in list(sc.height):
        if -12 <= x <= 12 and -28 <= z <= -10 and sc.height[(x, z)] >= 2 and abs(x) > FALL_W:
            gy = sc.height[(x, z)] + 1
            if sc.air(x, gy, z) and sc.name(x, gy - 1, z) == 'minecraft:grass_block':
                v = vhash(x, z, 59)
                if v < 0.25:
                    sc.set(x, gy, z, 'minecraft:grass')
                elif v < 0.34:
                    sc.set(x, gy, z, sc.pick([(1, 'minecraft:allium'), (1, 'minecraft:pink_tulip'), (1, 'minecraft:azure_bluet')]))
                elif v < 0.39:
                    sc.set(x, gy, z, 'minecraft:fern')
                elif v < 0.42:
                    azalea_bush(sc, x, z, 600 + x * 31 + z)


def waterfall():
    """La cascada (para el mod: su espuma y su bruma): x de un borde, x del otro, y de arriba, y de abajo; y la z de su
    cara de delante."""
    return (-FALL_W + 0.0, FALL_W + 1.0, FALL_TOP + 0.9, 0.85, CLIFF_Z + 1.0)


# ------------------------------------------------------------------------------------------------ el arco élfico
def elven_arch(sc):
    """El arco en ruinas delante de la cascada: dos columnas de piedra vieja con musgo (basa y capitel cincelados), un
    arco escalonado con su clave y una amatista encima; cubierto de azalea, flores de esporas, lianas y faroles."""
    z = ARCH_Z
    for x in (-4, 4):
        sc.set(x, -1, z, 'minecraft:stone_bricks')
        sc.set(x, 0, z, 'minecraft:chiseled_stone_bricks')
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if sc.name(x + dx, 0, z + dz) != 'minecraft:water':
                sc.set(x + dx, 0, z + dz, slab('mossy_stone_brick_slab'), only_air=True)
        for y in range(1, 7):
            sc.set(x, y, z, 'minecraft:mossy_stone_bricks' if vhash(x, y, 111) < 0.55 else
                   'minecraft:cracked_stone_bricks' if vhash(x, y, 113) < 0.3 else 'minecraft:stone_bricks')
        sc.set(x, 7, z, 'minecraft:chiseled_stone_bricks')
    for (x, y) in ((-4, 8), (4, 8), (-3, 8), (3, 8), (-3, 9), (3, 9), (-2, 9), (2, 9), (-2, 10), (2, 10), (-1, 10), (1, 10),
                   (0, 10)):
        sc.set(x, y, z, 'minecraft:mossy_stone_bricks' if vhash(x, y, 115) < 0.6 else 'minecraft:stone_bricks')
    for (x, face) in ((-3, 'east'), (3, 'west')):
        sc.set(x, 7, z, stairs('mossy_stone_brick_stairs', face, 'top'))
    for (x, face) in ((-2, 'east'), (2, 'west')):
        sc.set(x, 8, z, stairs('mossy_stone_brick_stairs', face, 'top'))
    for (x, face) in ((-1, 'east'), (1, 'west')):
        sc.set(x, 9, z, stairs('stone_brick_stairs', face, 'top'))
    sc.set(0, 9, z, slab('mossy_stone_brick_slab', 'top'))
    sc.set(0, 10, z, 'minecraft:chiseled_stone_bricks')
    sc.set(0, 11, z, 'minecraft:amethyst_cluster[facing=up,waterlogged=false]')
    for (x, y, dz) in ((-4, 10, 0), (-5, 8, 0), (-3, 11, 0), (-2, 11, 0), (-1, 11, 1), (1, 11, -1), (2, 11, 0), (3, 11, 0),
                       (4, 10, 0), (5, 8, 0), (5, 7, 0), (-5, 7, 1), (4, 9, 1), (-2, 12, 0), (3, 12, -1), (-5, 9, 0)):
        sc.set(x, y, z + dz, leaves('flowering_azalea_leaves' if (x + y) % 3 == 0 else 'azalea_leaves'), only_air=True)
    for (x, y) in ((-1, 8), (1, 8), (-2, 7), (2, 7)):
        sc.set(x, y, z, 'minecraft:spore_blossom', only_air=True)
    for x in (-3, 3):
        sc.set(x, 6, z, 'minecraft:chain[axis=y,waterlogged=false]', only_air=True)
        sc.set(x, 5, z, 'minecraft:lantern[hanging=true,waterlogged=false]', only_air=True)
    for (x, n) in ((-4, 4), (4, 5), (-2, 3), (2, 2)):
        for k in range(n):
            sc.set(x, (6 if abs(x) == 4 else 8) - k, z + 1, vine(n=True), only_air=True)


# ------------------------------------------------------------------------------------------------- las ruinas
def ruins(sc):
    """Restos de un santuario viejo entre el pantano: columnas rotas de piedra con musgo y lianas, tambores caídos."""
    for (x, z, h, seed) in ((-9, -5, 5, 1701), (9, -6, 3, 1702), (-10, 3, 2, 1703), (10, 2, 4, 1704), (-7, -10, 3, 1705),
                            (8, -11, 6, 1706)):
        g = ground(sc, x, z)
        sc.set(x, g - 1, z, 'minecraft:stone_bricks')
        sc.set(x, g, z, 'minecraft:chiseled_stone_bricks')
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if vhash(x + dx, z + dz, seed) < 0.7:
                sc.set(x + dx, g, z + dz, slab('mossy_stone_brick_slab'), only_air=True)
        for y in range(g + 1, g + h):
            sc.set(x, y, z, 'minecraft:mossy_stone_bricks' if vhash(y, seed, 121) < 0.6 else 'minecraft:cracked_stone_bricks')
        sc.set(x, g + h, z, 'minecraft:moss_carpet' if vhash(x, z, 123) < 0.5 else 'minecraft:pink_petals[facing=south,flower_amount=4]')
        for (dx, dz, side, face) in ((0, 1, 'n', 'north'), (1, 0, 'w', 'west'), (-1, 0, 'e', 'east'), (0, -1, 's', 'south')):
            v = vhash(x * 3 + dx, z * 5 + dz, seed + 1)
            for y in range(g + 1, g + h):
                if v < 0.45:
                    sc.set(x + dx, y, z + dz, vine(**{side: True}), only_air=True)
                elif v < 0.6 and vhash(y, seed, 125) < 0.5:
                    sc.set(x + dx, y, z + dz, lichen(face), only_air=True)
    for (x, z, axis, k) in ((6, 8, 'x', 3), (-9, 7, 'z', 3), (11, -3, 'z', 2)):
        g = ground(sc, x, z)
        for i in range(k):
            px, pz = (x + i, z) if axis == 'x' else (x, z + i)
            sc.set(px, g, pz, 'minecraft:mossy_stone_bricks')
        sc.set(x, g + 1, z, 'minecraft:moss_carpet', only_air=True)


# ------------------------------------------------------------------------------------- los árboles del pantano
def canopy(sc, cx, cy, cz, rx, ry, rz, mix, seed=0):
    """Una copa (solo la cáscara: lo de dentro no se ve y pesaría)."""
    for x in range(int(cx - rx - 1), int(cx + rx + 2)):
        for y in range(int(cy - ry - 1), int(cy + ry + 2)):
            for z in range(int(cz - rz - 1), int(cz + rz + 2)):
                d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 + ((z + 0.5 - cz) / rz) ** 2
                edge = 1.0 + (vhash(x * 7 + y, z * 3 - y, seed) - 0.5) * 0.5
                if 0.32 * edge < d < edge:
                    v = vhash(x * 13 - y, z * 7 + y, seed + 1)
                    acc = 0
                    for w, name in mix:
                        acc += w
                        if v < acc:
                            sc.set(x, y, z, leaves(name), only_air=True)
                            break


def line(sc, a, b, st, only_air=False):
    n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2]))) + 1
    for i in range(n + 1):
        t = i / n
        x, y, z = (round(a[k] + (b[k] - a[k]) * t) for k in range(3))
        sc.set(x, y, z, st, only_air=only_air)


SWAMP_LEAVES = [(0.38, 'mangrove_leaves'), (0.30, 'dark_oak_leaves'), (0.22, 'azalea_leaves'), (0.10, 'flowering_azalea_leaves')]


def swamp_tree(sc, x, z, h, r, seed, kind, reach=None):
    """Un árbol viejo del pantano: tronco de 2 × 2 (mangle con raíces que se meten en el barro y el agua, o roble oscuro
    con raíces de madera), ramas bajas que se abren (hacia el claro si reach), copa baja y ancha; lianas largas, bayas
    luminosas, flores de esporas y alguna luz de rana o de seta colgando; liquen y lianas en la corteza."""
    g = ground(sc, x, z)
    mangrove = kind == 'mangrove'
    bark = 'minecraft:mangrove_wood[axis=y]' if mangrove else 'minecraft:dark_oak_wood[axis=y]'
    side = 'minecraft:mangrove_wood[axis=x]' if mangrove else 'minecraft:dark_oak_wood[axis=x]'
    top = g + h
    for y in range(g - 1, top):
        for dx in (0, 1):
            for dz in (0, 1):
                sc.set(x + dx, y, z + dz, bark)
    cx, cz = x + 1.0, z + 1.0
    # raíces
    for k in range(7):
        ang = (k + vhash(k, seed, 81)) / 7 * math.tau
        L = 3 + vhash(k, seed, 83) * 3
        a = (cx - 0.5 + math.cos(ang) * 1.0, g + (3.5 if mangrove else 1.5), cz - 0.5 + math.sin(ang) * 1.0)
        mid = (cx - 0.5 + math.cos(ang) * L * 0.6, g + (2.2 if mangrove else 0.4), cz - 0.5 + math.sin(ang) * L * 0.6)
        b = (cx - 0.5 + math.cos(ang) * L, g - 1, cz - 0.5 + math.sin(ang) * L)
        st = 'minecraft:mangrove_roots[waterlogged=false]' if mangrove else bark
        line(sc, a, mid, st, only_air=True)
        line(sc, mid, b, st, only_air=True)
        bx, bz = round(b[0]), round(b[2])
        if vhash(k, seed, 85) < 0.5:
            sc.set(bx, ground(sc, bx, bz), bz, 'minecraft:moss_carpet', only_air=True)
    # ramas y copas
    nb = 5
    for k in range(nb):
        ang = (k + vhash(k, seed, 87)) / nb * math.tau
        if reach is not None and k < 2:
            ang = reach + (k - 0.5) * 0.7
        L = r * (0.8 + 0.4 * vhash(k, seed, 89)) * (1.5 if reach is not None and k < 2 else 1.0)
        y0 = g + h * (0.55 + 0.1 * k / nb)
        a = (cx - 0.5, y0, cz - 0.5)
        b = (cx - 0.5 + math.cos(ang) * L, y0 + 1.5 + vhash(k, seed, 91) * 2.5, cz - 0.5 + math.sin(ang) * L)
        line(sc, a, b, side)
        canopy(sc, b[0] + 0.5, b[1] + 1.0, b[2] + 0.5, r * 0.6, r * 0.28, r * 0.6, SWAMP_LEAVES, seed + 10 + k)
    canopy(sc, cx, top + 1.5, cz, r, r * 0.36, r, SWAMP_LEAVES, seed)
    # lo que cuelga de las copas
    R = r * 1.9
    for i in range(int(r * 16)):
        px = round(cx + (vhash(i, seed, 93) - 0.5) * 2 * R)
        pz = round(cz + (vhash(i, seed, 95) - 0.5) * 2 * R)
        over_glade = pool_d(px, pz) < 9.5 or (-2 < pz < 15 and abs(px) < 8)
        for y in range(top + 6, g + 2, -1):
            if 'leaves' in sc.name(px, y + 1, pz) and sc.air(px, y, pz):
                v = vhash(i, seed, 97)
                if over_glade:
                    # encima del claro y del pasillo de la cámara, solo cosas cortas que no tapen (bayas, esporas)
                    if v < 0.5 and y > 6:
                        for k in range(2):
                            berry = 'true' if vhash(i + k, seed, 101) < 0.7 else 'false'
                            sc.set(px, y - k, pz, f'minecraft:cave_vines[age=0,berries={berry}]' if k == 1 else
                                   f'minecraft:cave_vines_plant[berries={berry}]', only_air=True)
                    elif v < 0.8:
                        sc.set(px, y, pz, 'minecraft:spore_blossom', only_air=True)
                    break
                if v < 0.45:
                    n = 3 + int(vhash(i, seed, 99) * 7)
                    sd = ('n', 's', 'e', 'w')[i % 4]
                    for k in range(n):
                        if not sc.air(px, y - k, pz) or y - k <= ground(sc, px, pz):
                            break
                        sc.set(px, y - k, pz, vine(**{sd: True}))
                elif v < 0.72:
                    n = 2 + int(vhash(i, seed, 99) * 5)
                    for k in range(n):
                        if not sc.air(px, y - k, pz):
                            break
                        berry = 'true' if vhash(i + k, seed, 101) < 0.55 else 'false'
                        st = f'minecraft:cave_vines[age=0,berries={berry}]' if k == n - 1 else \
                            f'minecraft:cave_vines_plant[berries={berry}]'
                        sc.set(px, y - k, pz, st)
                elif v < 0.84:
                    sc.set(px, y, pz, 'minecraft:spore_blossom', only_air=True)
                elif v < 0.90:
                    sc.set(px, y, pz, 'minecraft:chain[axis=y,waterlogged=false]', only_air=True)
                    sc.set(px, y - 1, pz, ('minecraft:pearlescent_froglight', 'minecraft:verdant_froglight',
                                           'minecraft:ochre_froglight')[i % 3], only_air=True)
                elif v < 0.95:
                    sc.set(px, y + 1, pz, 'minecraft:shroomlight')
                break
    for y in range(g + 1, top - 1):
        for (dx, dz, face, sd) in ((2, 0, 'west', 'w'), (-1, 0, 'east', 'e'), (0, 2, 'north', 'n'), (0, -1, 'south', 's'),
                                   (2, 1, 'west', 'w'), (-1, 1, 'east', 'e'), (1, 2, 'north', 'n'), (1, -1, 'south', 's')):
            v = vhash(y * 11 + dx, seed + dz * 3, 103)
            if v < 0.12:
                sc.set(x + dx, y, z + dz, lichen(face), only_air=True)
            elif v < 0.30:
                sc.set(x + dx, y, z + dz, vine(**{sd: True}), only_air=True)


STEM = 'minecraft:mushroom_stem[down=false,east=true,north=true,south=true,up=false,west=true]'


def mushroom(sc, x, z, h, kind):
    """Seta gigante: pie y sombrero (roja, en cúpula; marrón, plano y ancho) y una luz de seta debajo."""
    g = ground(sc, x, z)
    for y in range(g, g + h):
        sc.set(x, y, z, STEM)
    cap = f'minecraft:{kind}_mushroom_block[down=false,east=true,north=true,south=true,up=true,west=true]'
    rr = 2 if kind == 'red' else 3
    for dx in range(-rr, rr + 1):
        for dz in range(-rr, rr + 1):
            d = math.hypot(dx, dz)
            if d > rr + 0.3:
                continue
            if kind == 'red':
                sc.set(x + dx, g + h + (1 if d < rr - 0.8 else 0), z + dz, cap)
                if d > rr - 0.8:
                    sc.set(x + dx, g + h - 1, z + dz, cap)
            else:
                sc.set(x + dx, g + h, z + dz, cap)
    sc.set(x + 1, g + h - 1, z, 'minecraft:shroomlight', only_air=True)


def lamp_mushroom(sc, x, z, h):
    """Un farol de piedra con musgo (un poste y su farol encima) entre las flores del pantano."""
    g = ground(sc, x, z)
    h = max(1, h - 1)
    for y in range(g, g + h):
        sc.set(x, y, z, 'minecraft:mossy_stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
    sc.set(x, g + h, z, 'minecraft:lantern[hanging=false,waterlogged=false]')


def standing_stone(sc, x, z, h, seed):
    g = ground(sc, x, z)
    for y in range(g, g + h):
        sc.set(x, y, z, 'minecraft:mossy_cobblestone' if vhash(y, seed, 105) < 0.5 else 'minecraft:mossy_stone_bricks')
    sc.set(x, g + h, z, 'minecraft:amethyst_cluster[facing=up,waterlogged=false]')
    for (dx, dz, face) in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'), (0, -1, 'south')):
        for y in range(g, g + h):
            if vhash(y * 3 + dx, seed + dz, 107) < 0.35:
                sc.set(x + dx, y, z + dz, lichen(face), only_air=True)


def azalea_bush(sc, x, z, seed):
    y0 = ground(sc, x, z)
    for dx in (0, 1):
        for dz in (0, 1):
            if vhash(x + dx, z + dz, seed) < 0.85:
                sc.set(x + dx, y0, z + dz, leaves('flowering_azalea_leaves' if vhash(dx, dz, seed + 1) < 0.6
                                                  else 'azalea_leaves'), only_air=True)
                if vhash(x + dx, z + dz, seed + 2) < 0.35:
                    sc.set(x + dx, y0 + 1, z + dz, leaves('flowering_azalea_leaves'), only_air=True)


def tree_spots():
    """Dónde va cada árbol: un anillo de gigantes pegados al claro (sus ramas bajas lo cubren) y el bosque detrás, cada
    vez más denso, sin dejar ver ningún horizonte; nada en el pasillo de la cámara ni delante de la cascada."""
    rng = np.random.default_rng(1999)
    pts = []

    def free(x, z, mind):
        return all((x - px) ** 2 + (z - pz) ** 2 >= mind * mind for px, pz, *_ in pts)

    def blocked(x, z):
        if pool_d(x, z) < 9.0:
            return True
        if -2 < z < 15 and abs(x) < 7:            # el pasillo de la cámara
            return True
        if -26 < z < -6 and abs(x) < 8:           # el risco, el remanso, el arco
            return True
        return not (X0 + 6 <= x <= X1 - 6 and Z0 + 6 <= z <= Z1 - 6)

    for (r0, r1, mind, tries, size) in ((9.5, 13.5, 6.0, 900, 0), (13.5, 24.0, 6.5, 2500, 1), (24.0, 40.0, 7.0, 3000, 2)):
        for _ in range(tries):
            ang = rng.random() * math.tau
            rad = r0 + rng.random() * (r1 - r0)
            x = int(round(CRATE[0] + math.cos(ang) * rad))
            z = int(round(CRATE[2] + math.sin(ang) * rad))
            if blocked(x, z) or not free(x, z, mind):
                continue
            pts.append((x, z, size, ang))
    return pts


def forest(sc):
    for i, (x, z, size, ang) in enumerate(tree_spots()):
        seed = 1000 + i * 7
        kind = 'mangrove' if vhash(x, z, 131) < 0.5 else 'dark'
        if size == 0:
            # los del claro: ramas bajas que se abren hacia la crate (una cúpula con un hueco por donde entra el haz)
            swamp_tree(sc, x, z, 9 + int(vhash(x, z, 133) * 3), 5.5 + vhash(x, z, 135) * 1.0, seed, kind, reach=ang + math.pi)
        elif size == 1:
            swamp_tree(sc, x, z, 10 + int(vhash(x, z, 133) * 4), 5.0 + vhash(x, z, 135) * 1.5, seed, kind)
        else:
            swamp_tree(sc, x, z, 11 + int(vhash(x, z, 133) * 5), 5.0 + vhash(x, z, 135) * 1.5, seed, kind)
    for (x, z, s) in ((-12, 7, 1301), (12, 2, 1302), (-7, -12, 1303), (7, -12, 1304), (-13, -3, 1305), (14, -9, 1306),
                      (-8, 12, 1307), (9, 13, 1308)):
        azalea_bush(sc, x, z, s)
    for (x, z, h, kind) in ((-15, -9, 5, 'red'), (16, 6, 6, 'brown'), (-17, 10, 4, 'red'), (11, -16, 5, 'red'),
                            (-12, -17, 6, 'brown')):
        mushroom(sc, x, z, h, kind)
    for (x, z, h) in ((-10, 3, 2), (9, -2, 3), (-6, 10, 2), (8, 11, 2), (-12, -13, 3), (12, -13, 2), (-3, 12, 1), (14, 9, 3),
                      (-14, 2, 2), (15, -4, 2)):
        lamp_mushroom(sc, x, z, h)
    for (x, z, h, s) in ((-9, -2, 3, 1501), (10, -9, 2, 1502), (-11, 9, 2, 1503), (13, 5, 3, 1504)):
        standing_stone(sc, x, z, h, s)


def swamp(sc):
    """El pantano alrededor del claro: charcas de agua (con barro) entre el musgo, nenúfares, pepinos de mar que brillan
    en el agua y hojas grandes de goteo."""
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            r = glade_d(x, z)
            if r < RIM_R + 1.5 or r > 30:
                continue
            if -26 < z < -6 and abs(x) < 9:
                continue
            if -2 < z < 15 and abs(x) < 5:
                continue
            top = sc.height.get((x, z), -1)
            if top > 0:
                continue
            n = fbm(x * 0.13, z * 0.13, 141)
            if n < 0.56 or not sc.air(x, top + 1, z):
                continue
            sc.set(x, top - 1, z, 'minecraft:mud')
            sc.set(x, top, z, 'minecraft:water[level=0]')
            v = vhash(x, z, 143)
            if v < 0.16:
                sc.set(x, top + 1, z, 'minecraft:lily_pad')
            elif v < 0.20:
                sc.set(x, top, z, f'minecraft:sea_pickle[pickles={1 + int(vhash(x, z, 145) * 4) % 4},waterlogged=true]')
            elif v < 0.24 and n < 0.62:
                face = ('north', 'east', 'south', 'west')[int(vhash(x, z, 147) * 4) % 4]
                sc.set(x, top + 1, z, f'minecraft:big_dripleaf[facing={face},tilt=none,waterlogged=false]')


# --------------------------------------------------------------------------------------------- flores y hierba
FLOWERS = [('minecraft:blue_orchid', 'minecraft:allium'), ('minecraft:lily_of_the_valley', 'minecraft:blue_orchid'),
           ('minecraft:allium', 'minecraft:pink_tulip'), ('minecraft:azure_bluet', 'minecraft:blue_orchid')]


def flora(sc):
    """Lo que crece en el suelo: en el claro, flores del pantano (orquídeas azules, alliums, lirios) y hierba; en el
    bosque, helechos, helechos grandes, hierba alta, musgo y setas."""
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            gy = ground(sc, x, z)
            below = sc.name(x, gy - 1, z)
            if not sc.air(x, gy, z) or below not in ('minecraft:grass_block', 'minecraft:moss_block', 'minecraft:podzol',
                                                     'minecraft:rooted_dirt'):
                continue
            if pool_d(x, z) < RIM_R + 0.3:
                continue
            v = sc.r()
            r = glade_d(x, z)
            if r < GLADE_R + 1:
                flowers = fbm(x * 0.28, z * 0.28, 23)
                if flowers > 0.52 and v < 0.6:
                    pair = FLOWERS[int(fbm(x * 0.07, z * 0.07, 25) * 12) % len(FLOWERS)]
                    sc.set(x, gy, z, pair[0] if sc.r() < 0.6 else pair[1])
                elif v < 0.30:
                    sc.set(x, gy, z, 'minecraft:grass')
                elif v < 0.40:
                    sc.set(x, gy, z, 'minecraft:fern')
                elif v < 0.46 and sc.air(x, gy + 1, z):
                    tall('large_fern', x, gy, z, sc)
                elif v < 0.50:
                    face = ('north', 'east', 'south', 'west')[int(vhash(x, z, 19) * 4) % 4]
                    sc.set(x, gy, z, f'minecraft:pink_petals[facing={face},flower_amount={1 + int(vhash(x, z, 23) * 4) % 4}]')
                elif v < 0.56:
                    sc.set(x, gy, z, 'minecraft:moss_carpet')
            else:
                if v < 0.22:
                    sc.set(x, gy, z, 'minecraft:fern')
                elif v < 0.34 and sc.air(x, gy + 1, z):
                    tall(sc.pick([(3, 'large_fern'), (2, 'tall_grass')]), x, gy, z, sc)
                elif v < 0.46:
                    sc.set(x, gy, z, 'minecraft:grass')
                elif v < 0.56:
                    sc.set(x, gy, z, 'minecraft:moss_carpet')
                elif v < 0.58:
                    sc.set(x, gy, z, sc.pick([(1, 'minecraft:red_mushroom'), (1, 'minecraft:brown_mushroom')]))
                elif v < 0.60:
                    sc.set(x, gy, z, 'minecraft:blue_orchid')
    for (cx, cz, rr) in ((-7, 6, 1.6), (8, 6, 1.4), (-10, -6, 1.5)):
        for k in range(10):
            a = k / 10 * math.tau
            x, z = round(cx + math.cos(a) * rr), round(cz + math.sin(a) * rr)
            gy = ground(sc, x, z)
            if sc.name(x, gy - 1, z) in ('minecraft:grass_block', 'minecraft:moss_block', 'minecraft:podzol'):
                sc.set(x, gy, z, 'minecraft:red_mushroom' if k % 3 else 'minecraft:brown_mushroom')
    for z in range(8, 24, 2):
        x = int(round(math.sin(z * 0.35) * 0.8))
        gy = ground(sc, x, z) - 1
        sc.set(x, gy, z, 'minecraft:mossy_stone_bricks')
        sc.set(x, gy + 1, z, 'minecraft:air')


def clear_view(sc):
    """Nada de hojas ni troncos entre la cámara y la crate, ni en el camino del sol hasta ella (el hueco de la cúpula)."""
    sun = np.array(SUN)
    for t in range(0, 361, 4):
        eye, at, _ = camera_at(t)
        d = np.array(at) - np.array(eye)
        L = float(np.linalg.norm(d))
        for s in np.arange(0.0, L, 0.4):
            clear_ball(sc, np.array(eye) + d / L * s, 2)
    # y nada colgando entre la cámara y la crate (ni en la ruleta, encima)
    for x in range(-6, 7):
        for z in range(-3, 12):
            for y in range(2, 12):
                n = sc.name(x, y, z)
                if n in ('minecraft:vine', 'minecraft:cave_vines', 'minecraft:cave_vines_plant', 'minecraft:chain') or \
                        n.endswith('froglight'):
                    if abs(x) < 4 or z > 2:
                        sc.set(x, y, z, 'minecraft:air')
    for s in np.arange(4.0, 46.0, 0.6):
        clear_ball(sc, np.array(CRATE) + sun * s, 2 if s < 20 else 3, only_leaves=True)


def clear_ball(sc, p, r, only_leaves=False):
    for dx in range(-r, r + 1):
        for dy in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if dx * dx + dy * dy + dz * dz > r * r + 1:
                    continue
                x, y, z = int(math.floor(p[0])) + dx, int(math.floor(p[1])) + dy, int(math.floor(p[2])) + dz
                n = sc.name(x, y, z)
                if 'leaves' in n or (not only_leaves and (n.endswith('_log') or n.endswith('_wood'))) or n in (
                        'minecraft:cave_vines', 'minecraft:cave_vines_plant', 'minecraft:spore_blossom', 'minecraft:vine',
                        'minecraft:chain') or n.endswith('froglight'):
                    sc.set(x, y, z, 'minecraft:air')


def lights(sc):
    """Las luces mágicas: cada bloque que brilla (con su color y su tamaño), para el halo y la luz de alrededor."""
    out = []
    for (i, j, k) in np.argwhere(sc.a != 0):
        idx = sc.a[i, j, k]
        st = sc.pal[idx]
        name = st.split('[')[0]
        if name in GLOW and (sc.flags[idx] & F_GLOW):
            r, g, b, s = GLOW[name]
            if name == 'minecraft:sea_pickle':
                s *= 0.5 + 0.2 * int(st.split('pickles=')[1][0])
            out.append((i + X0 + 0.5, j + Y0 + 0.5, k + Z0 + 0.5, r, g, b, s))
    return out


# ------------------------------------------------------------------------------------------- la cámara y los puntos
# (tick, ojo, mira, campo de visión): ya cerca de la crate desde el principio (el dueño: «que aparezca directamente
# cerca de la crate»); la cinemática (CrateCinematicScreen) la recorre con Catmull-Rom
CAMERA = [
    (0, (-2.8, 4.0, 8.8), (0.5, 3.0, -0.5), 54.0),          # llega: la crate en el estanque, el arco y la cascada detrás
    (20, (-2.2, 3.7, 6.9), (0.55, 2.8, 0.3), 51.0),
    (56, (-1.3, 3.2, 4.4), (0.6, 2.5, 1.0), 46.0),           # la llave llega a la cerradura
    (80, (-0.9, 3.0, 3.7), (0.55, 2.5, 1.0), 44.0),          # gira
    (102, (-2.0, 3.8, 6.2), (0.5, 2.9, 0.5), 52.0),          # ¡se abre! (retrocede)
    (130, (-0.5, 4.6, 7.2), (0.5, 3.7, 0.5), 54.0),          # la ruleta, encima de la crate
    (194, (1.3, 4.5, 6.8), (0.5, 3.75, 0.5), 52.0),
    (252, (0.6, 4.3, 5.9), (0.5, 3.85, 0.9), 48.0),
    (264, (0.55, 4.3, 5.2), (0.5, 3.9, 1.5), 48.0),          # el premio, delante, en el centro
    (360, (-0.9, 4.5, 5.8), (0.5, 3.95, 1.4), 50.0),
]


def catmull(p0, p1, p2, p3, t):
    t2, t3 = t * t, t * t * t
    return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)


def camera_at(tick):
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
    wf = waterfall()
    return {
        'crate': CRATE,
        'pool': (CRATE[0], 0.875, CRATE[2], POOL_R),
        'sun': tuple(float(v) for v in SUN),
        'fall': wf[:4], 'fall_z': (wf[4], 0.0, 0.0, 0.0),
        'pond': (0.5, 0.875, CLIFF_Z + 2.0, 3.0),
        'flowers0': (-7.0, 0.3, 7.0, 3.0), 'flowers1': (7.5, 0.3, 5.0, 3.0), 'flowers2': (-5.0, 0.3, -6.0, 2.5),
        **{f'spring{i}': p for i, p in enumerate(spring_points())},
    }


def build():
    sc = Scene()
    terrain(sc)
    cliff(sc)
    pool(sc)
    springs(sc)
    elven_arch(sc)
    ruins(sc)
    swamp(sc)
    forest(sc)
    flora(sc)
    clear_view(sc)
    return sc


def write(sc, path):
    blocks = np.argwhere(sc.a != 0)
    out = bytearray()
    out += b'TFSC'
    out += struct.pack('>i', 2)
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
    ls = lights(sc)
    out += struct.pack('>i', len(ls))
    for l in ls:
        out += struct.pack('>fffffff', *l)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(bytes(out))
    return len(blocks), len(out), len(ls)


def main():
    sc = build()
    n, size, nl = write(sc, OUT)
    print(f'{n} bloques, {len(sc.pal)} estados, {nl} luces, {size / 1024:.0f} KB → {os.path.relpath(OUT)}')


if __name__ == '__main__':
    main()
