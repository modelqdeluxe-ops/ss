"""El Santuario de las Hadas: la escena en vivo de la apertura de las crates (TF Client 1.3.99).

    python3 -I tools/crates/santuario.py

El dueño mandó la imagen que quiere («quiero exactamente esto»): un santuario en ruinas cerrado, de piedra con musgo y
enredaderas, con el techo de roble oscuro a los lados (flores de esporas rosas y faroles con cadena) y abierto en el
medio, por donde entra un haz de sol grande; al fondo, una cascada alta que baja por una escalera de agua; a los lados,
dos columnas de piedra con su cascada; delante, un estanque turquesa que brilla, con nenúfares; en medio, el altar de
piedra con musgo, flores rosas y dos amatistas, y encima la crate.

Escribe assets/fscrates/escena/santuario.bin (lo lee crates/client/scene/SanctuaryScene): la paleta de estados de
bloque y los bloques, los puntos de la escena, la ruta de la cámara y las luces mágicas (dónde brilla algo, de qué color:
el mod les pone su halo y alumbra lo de alrededor). Las vistas previas (santuario_render.py, santuario_preview.py) usan
lo mismo.

Medidas sacadas de la imagen del dueño (como una cámara: un nenúfar mide un bloque): la cámara a 3,7 de alto y 10,5
bloques delante del altar, mirando recto, con unos 35° de campo vertical; las cascadas de los lados, unos 8 bloques
detrás del altar y a 4,5 de su centro; la escalera de agua empieza a 13 bloques detrás y sube 4; la cascada alta, a 17.

Coordenadas: x al este, z al sur (la cámara mira al norte, desde el sur), y arriba; el agua del estanque está en y = 0
(su superficie, en 0,875). La crate se apoya en y = 2, centrada en (0.5, 0.5) (el bloque (0, 1, 0)). La escena es
simétrica: el bloque x tiene su pareja en -x.
"""
import math
import os
import struct

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'fscrates', 'escena',
                                    'santuario.bin'))

X0, X1 = -20, 20
Y0, Y1 = -3, 26
Z0, Z1 = -24, 20
NX, NY, NZ = X1 - X0 + 1, Y1 - Y0 + 1, Z1 - Z0 + 1

CRATE = (0.5, 2.0, 0.5)
POOL_X = 6                         # el estanque, de x = -6 a 6
POOL_Z0, POOL_Z1 = -12, 13         # y de z = -12 (la escalera) a 13 (detrás de la cámara)
POOL_R = 6.0                       # para las runas y las ondas, alrededor del altar
STAIR_W = 2                        # la escalera de agua y la cascada alta, de x = -2 a 2
STAIR_Z = -13                      # el primer escalón
STEPS = 4                          # escalones (sube uno cada uno)
LAND_Z = STAIR_Z - STEPS           # el rellano de arriba (z = -17, -18)
FALL_Z = LAND_Z - 1                # la cascada alta (z = -18), delante de la pared del fondo
FALL_TOP = 19                      # de donde cae
SIDE_X = (4, 5)                    # las cascadas de los lados (y su pareja en -x)
SIDE_Z = -8
SIDE_TOP = 4                       # de donde cae el agua de los lados (bloque de agua de arriba)
WALL_X = 11                        # las paredes de los lados (x = ±11 y más)
BACK_Z = -19                       # la pared del fondo (z = -19 y más atrás)
AISLE_Y = 7                        # el techo de los lados (vigas de roble oscuro)
AISLE_IN = 4                       # su borde de dentro (x = ±4)
CANOPY_Y = 10                      # las copas encima de la nave
OPEN_Z = (-17, -5)                 # el hueco de las copas, por donde entra el sol
# el haz de sol: entra por el hueco, en diagonal desde atrás y arriba, y cae sobre el altar
SUN = np.array([0.0, 0.78, -0.63])
SUN /= np.linalg.norm(SUN)

F_SWAY, F_WATER, F_GLOW = 1, 2, 4

SWAY = {'minecraft:grass', 'minecraft:tall_grass', 'minecraft:fern', 'minecraft:large_fern', 'minecraft:poppy',
        'minecraft:dandelion', 'minecraft:oxeye_daisy', 'minecraft:azure_bluet', 'minecraft:cornflower',
        'minecraft:allium', 'minecraft:lily_of_the_valley', 'minecraft:blue_orchid', 'minecraft:pink_tulip',
        'minecraft:white_tulip', 'minecraft:orange_tulip', 'minecraft:red_tulip', 'minecraft:peony',
        'minecraft:rose_bush', 'minecraft:lilac', 'minecraft:torchflower', 'minecraft:pitcher_plant'}
# lo que brilla: no le llega la sombra y lleva halo (r, g, b, tamaño)
GLOW = {'minecraft:lantern': (1.0, 0.62, 0.26, 1.5), 'minecraft:shroomlight': (1.0, 0.62, 0.30, 1.2),
        'minecraft:sea_lantern': (0.45, 1.0, 0.95, 1.0), 'minecraft:glow_lichen': (0.55, 1.0, 0.85, 0.35),
        'minecraft:cave_vines': (1.0, 0.75, 0.35, 0.45), 'minecraft:cave_vines_plant': (1.0, 0.75, 0.35, 0.45),
        'minecraft:amethyst_cluster': (0.85, 0.55, 1.0, 0.9), 'minecraft:large_amethyst_bud': (0.85, 0.55, 1.0, 0.5),
        'minecraft:sea_pickle': (0.5, 1.0, 0.8, 0.5)}
STONE = ('minecraft:stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks',
         'minecraft:mossy_cobblestone', 'minecraft:moss_block')


class Scene:
    def __init__(self, seed=11):
        self.pal = ['minecraft:air']
        self.flags = [0]
        self.ids = {'minecraft:air': 0}
        self.a = np.zeros((NX, NY, NZ), np.uint16)
        self.rng = np.random.default_rng(seed)

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

    def sym(self, x, y, z, state, only_air=False):
        """El bloque y su pareja al otro lado (x → -x), con lo de este/oeste cambiado."""
        self.set(x, y, z, state, only_air)
        self.set(-x, y, z, mirror(state), only_air)

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


def mirror(state):
    """El mismo bloque visto en el espejo (este ↔ oeste, izquierda ↔ derecha)."""
    if '[' not in state:
        return state
    s = state.replace('east', '\0').replace('west', 'east').replace('\0', 'west')
    return s.replace('left', '\0').replace('right', 'left').replace('\0', 'right')


# ----------------------------------------------------------------------------------------------- ruido y utilidades
def vhash(ix, iz, seed):
    h = (ix * 374761393 + iz * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


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


def stone(x, y, z, seed, moss=0.45):
    """Una piedra del santuario: ladrillo de piedra, con musgo, agrietado o adoquín con musgo."""
    v = vhash(x * 7 + y * 3, z * 5 - y, seed)
    if v < moss:
        return 'minecraft:mossy_stone_bricks'
    if v < moss + 0.12:
        return 'minecraft:cracked_stone_bricks'
    if v < moss + 0.18:
        return 'minecraft:mossy_cobblestone'
    return 'minecraft:stone_bricks'


FALLING = 'minecraft:water[level=8]'      # agua que cae (el juego la dibuja como una cascada de verdad)
STILL = 'minecraft:water[level=0]'
FLOWERS = ('minecraft:cornflower', 'minecraft:blue_orchid', 'minecraft:allium', 'minecraft:pink_tulip',
           'minecraft:azure_bluet', 'minecraft:lily_of_the_valley')


def flower_bed(sc, x, y, z, seed):
    """Flores encima de un bloque: azules y rosas como en la imagen (acianos, orquídeas, alliums, pétalos rosas)."""
    if not sc.air(x, y, z):
        return
    v = vhash(x, z * 3 + y, seed)
    if v < 0.42:
        sc.set(x, y, z, FLOWERS[int(vhash(x, z, seed + 1) * len(FLOWERS)) % len(FLOWERS)])
    elif v < 0.62:
        face = ('north', 'east', 'south', 'west')[int(vhash(x, z, seed + 2) * 4) % 4]
        sc.set(x, y, z, f'minecraft:pink_petals[facing={face},flower_amount={2 + int(vhash(x, z, seed + 3) * 3) % 3}]')
    elif v < 0.78:
        sc.set(x, y, z, 'minecraft:moss_carpet')
    elif v < 0.88:
        sc.set(x, y, z, 'minecraft:fern')


def ivy(sc, x0, x1, y0, y1, z0, z1, seed, dens=0.7):
    """Enredaderas en todas las caras de piedra que dan al aire dentro de la caja (como en la imagen: todo cubierto)."""
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                if not sc.air(x, y, z):
                    continue
                sides = {}
                for (dx, dz, k) in ((0, -1, 'n'), (0, 1, 's'), (1, 0, 'e'), (-1, 0, 'w')):
                    n = sc.name(x + dx, y, z + dz)
                    if n in STONE or n.endswith('stone_bricks') or n.endswith('_planks') or n.endswith('_log'):
                        sides[k] = True
                if not sides:
                    continue
                # en columnas que cuelgan: más arriba, más tupido
                v = vhash(x * 13 + y * 7, z * 11 - y, seed)
                if v < dens * (0.55 + 0.45 * min(1.0, (y - y0) / 6.0)):
                    sc.set(x, y, z, vine(**sides))


# ---------------------------------------------------------------------------------------------- el suelo y el agua
def floor_and_pool(sc):
    """El estanque: de pared a pared a lo ancho y desde la escalera hasta detrás de la cámara. El fondo brilla
    (prismarina y alguna linterna de mar); fuera, el suelo de piedra del santuario."""
    for x in range(-WALL_X, WALL_X + 1):
        for z in range(BACK_Z, Z1 - 2):
            sc.set(x, -3, z, 'minecraft:stone')
            sc.set(x, -2, z, 'minecraft:stone')
            if abs(x) <= POOL_X and POOL_Z0 <= z <= POOL_Z1:
                v = vhash(x, z, 77)
                # baldosas claras (se ven a través del agua, como en la imagen) y alguna linterna de mar
                st = ('minecraft:sea_lantern' if v < 0.05 and not (abs(x) <= 2 and -2 <= z <= 2) else
                      'minecraft:prismarine_bricks' if v < 0.8 else 'minecraft:dark_prismarine')
                sc.set(x, -1, z, st)
                sc.set(x, 0, z, STILL)
            else:
                sc.set(x, -1, z, stone(x, -1, z, 3))
                sc.set(x, 0, z, stone(x, 0, z, 5, 0.5))


def altar(sc):
    """El altar en medio del estanque (como en la imagen): la columna de piedra del centro, a los lados bloques
    cubiertos de musgo y azalea en flor, delante dos más bajos; arriba, dos amatistas a los lados de la crate y pétalos
    rosas; liquen que brilla en los costados."""
    for x in range(-1, 2):
        for z in range(-1, 2):
            sc.set(x, -1, z, 'minecraft:stone_bricks')
            if x == 0:
                sc.set(x, 0, z, 'minecraft:stone_bricks' if z != 1 else 'minecraft:chiseled_stone_bricks')
                sc.set(x, 1, z, 'minecraft:stone_bricks' if z != 1 else 'minecraft:mossy_stone_bricks')
            else:
                sc.set(x, 0, z, 'minecraft:moss_block' if z < 1 else leaves('flowering_azalea_leaves'))
                sc.set(x, 1, z, leaves('flowering_azalea_leaves') if (x + z) % 2 == 0 else 'minecraft:moss_block')
    for (x, z) in ((-2, 0), (-2, 1), (-1, 2), (-2, -1)):
        sc.sym(x, -1, z, 'minecraft:stone_bricks')
        sc.sym(x, 0, z, 'minecraft:moss_block' if (x + z) % 2 else leaves('flowering_azalea_leaves'))
    sc.sym(-2, 1, 1, 'minecraft:pink_petals[facing=east,flower_amount=4]')
    sc.sym(-2, 1, 0, 'minecraft:pink_petals[facing=south,flower_amount=3]')
    sc.sym(-1, 1, 2, 'minecraft:pink_petals[facing=north,flower_amount=2]')
    sc.sym(-2, 1, -1, 'minecraft:moss_carpet')
    sc.set(0, 0, 2, slab('mossy_stone_brick_slab', 'top'))
    # arriba: las amatistas a los lados de la crate, pétalos y musgo
    sc.sym(-1, 2, 0, 'minecraft:amethyst_cluster[facing=up,waterlogged=false]')
    sc.sym(-1, 2, 1, 'minecraft:pink_petals[facing=east,flower_amount=4]')
    sc.sym(-1, 2, -1, 'minecraft:pink_petals[facing=south,flower_amount=3]')
    sc.set(0, 2, -1, 'minecraft:moss_carpet')
    sc.set(0, 2, 1, 'minecraft:pink_petals[facing=north,flower_amount=2]')
    for (x, z, face) in ((-2, 0, 'east'), (2, 0, 'west'), (-2, 1, 'east'), (2, -1, 'west')):
        sc.set(x, 1, z, lichen(face), only_air=True)
    # nenúfares por el estanque (como en la imagen: junto a las cascadas de los lados y delante)
    for (x, z) in ((-5, -4), (-6, -6), (4, -5), (6, -3), (-3, -9), (3, -10), (-7, 2), (7, 4), (-6, 8), (5, 9), (-2, 6),
                   (3, 5), (-7, -10), (7, -9)):
        if sc.name(x, 0, z) == 'minecraft:water':
            sc.set(x, 1, z, 'minecraft:lily_pad')
    for (x, z) in ((-3, 3), (4, -2), (-4, -1), (2, 8), (-1, -6), (6, 0)):
        if sc.name(x, 0, z) == 'minecraft:water':
            sc.set(x, 0, z, f'minecraft:sea_pickle[pickles={1 + int(vhash(x, z, 5) * 4) % 4},waterlogged=true]')


def stair_falls(sc):
    """Al fondo, la escalera de agua: cuatro escalones de piedra con el agua que corre por encima y cae de uno a otro;
    arriba, el rellano y la cascada alta que baja de lo alto de la pared del fondo, bajo el hueco de las copas."""
    w = STAIR_W
    for k in range(STEPS):
        z = STAIR_Z - k
        for x in range(-w, w + 1):
            for y in range(-1, k + 1):
                sc.set(x, y, z, stone(x, y, z, 11, 0.5))
            sc.set(x, k + 1, z, STILL)
    for z in (LAND_Z, LAND_Z - 1):
        for x in range(-w, w + 1):
            for y in range(-1, STEPS + 1):
                sc.set(x, y, z, stone(x, y, z, 13, 0.5))
            sc.set(x, STEPS + 1, z, STILL)
    for x in range(-w, w + 1):
        for y in range(STEPS + 2, FALL_TOP + 1):
            sc.set(x, y, FALL_Z, FALLING)
    # los muros que encajan la escalera, con su moldura y su musgo, y las columnas altas de los lados
    for z in range(LAND_Z - 1, STAIR_Z + 1):
        k = min(STEPS, STAIR_Z - z)
        top = k + 3
        for y in range(-1, top + 1):
            sc.sym(-w - 1, y, z, stone(-w - 1, y, z, 17, 0.55))
        sc.sym(-w - 1, top + 1, z, slab('mossy_stone_brick_slab'))
    for z in (STAIR_Z, LAND_Z - 1):
        for y in range(-1, 14):
            sc.sym(-w - 1, y, z, stone(-w - 1, y, z, 19, 0.5) if y % 5 else 'minecraft:chiseled_stone_bricks')
            sc.sym(-w - 2, y, z, stone(-w - 2, y, z, 21, 0.5))
    # flores en las molduras
    for z in range(LAND_Z - 1, STAIR_Z + 1):
        k = min(STEPS, STAIR_Z - z)
        flower_bed(sc, -w - 2, k + 4, z, 23)
        flower_bed(sc, w + 2, k + 4, z, 29)


def fall_box():
    """La cascada alta (para el mod: su espuma y su bruma): x de un borde, x del otro, y de arriba, y de abajo."""
    return (-STAIR_W + 0.0, STAIR_W + 1.0, FALL_TOP + 0.9, STEPS + 1.875)


def side_towers(sc):
    """Las dos columnas de piedra de los lados, cada una con su cascada que cae al estanque por delante (el agua sale de
    una muesca de arriba); encima, la columna sigue con musgo, flores y enredaderas hasta el techo."""
    x0, x1 = SIDE_X
    for x in range(x0 - 1, x1 + 3):
        for z in range(SIDE_Z - 5, SIDE_Z):
            hmax = SIDE_TOP + 1 + (2 if x >= x1 + 1 else 0) + (1 if z <= SIDE_Z - 3 else 0)
            for y in range(-1, hmax + 1):
                sc.sym(x, y, z, stone(x, y, z, 31, 0.5))
    # la muesca de arriba (con agua) y el chorro que cae por delante
    for x in (x0, x1):
        sc.sym(x, SIDE_TOP, SIDE_Z - 1, STILL)
        sc.sym(x, SIDE_TOP + 1, SIDE_Z - 1, 'minecraft:air')
        for y in range(1, SIDE_TOP + 1):
            sc.sym(x, y, SIDE_Z, FALLING)
    sc.sym(x0 - 1, SIDE_TOP + 1, SIDE_Z - 1, slab('mossy_stone_brick_slab'))
    for x in (x0, x1):
        sc.sym(x, SIDE_TOP + 1, SIDE_Z - 2, 'minecraft:moss_block')
    # flores y matas encima de la columna
    for x in range(x0 - 1, x1 + 3):
        for z in range(SIDE_Z - 5, SIDE_Z - 1):
            for sgn in (1, -1):
                xx = x * sgn
                y = 0
                while not sc.air(xx, y, z) and y < 14:
                    y += 1
                if y < 14 and sc.name(xx, y - 1, z) != 'minecraft:water':
                    flower_bed(sc, xx, y, z, 37 + sgn)
    # la columna alta de detrás, hasta el techo
    for x in (x1 + 1, x1 + 2):
        for z in (SIDE_Z - 5, SIDE_Z - 4):
            for y in range(-1, AISLE_Y + 1):
                sc.sym(x, y, z, stone(x, y, z, 41, 0.5) if y % 6 else 'minecraft:chiseled_stone_bricks')


def side_halls(sc):
    """Los lados del santuario (como en la imagen): junto al agua un borde de piedra con losas, y detrás masas de piedra
    con musgo en terrazas que suben hasta el techo, con columnas, flores azules y rosas y faroles en su poste; la pared
    alta, toda cubierta de enredaderas."""
    for x in range(POOL_X + 1, WALL_X + 4):
        for z in range(BACK_Z, Z1 - 2):
            if x >= WALL_X:
                top = 15
            elif x == POOL_X + 1:
                top = 0
            else:
                # terrazas: más altas cuanto más cerca de la pared y hacia el fondo (hasta el techo de los lados)
                top = 1 + (x - POOL_X - 2) * 2 + (1 if z < -2 else 0) + (2 if z < -9 else 0) + (1 if vhash(x, z // 3, 51) < 0.3 else 0)
                top = min(top, AISLE_Y - 1)
            for y in range(-1, top + 1):
                sc.sym(x, y, z, stone(x, y, z, 53 + (x >= WALL_X), 0.55))
            if x == POOL_X + 1 and z % 2 == 0:
                sc.sym(x, 1, z, slab('mossy_stone_brick_slab'))
            elif x < WALL_X:
                flower_bed(sc, x, top + 1, z, 59)
                flower_bed(sc, -x, top + 1, z, 61)
    # columnas de piedra hasta el techo (con su capitel cincelado)
    for z in (6, 0, -6, -12):
        for x in (POOL_X + 2,):
            for y in range(-1, AISLE_Y):
                sc.sym(x, y, z, 'minecraft:chiseled_stone_bricks' if y in (0, AISLE_Y - 1) else stone(x, y, z, 57, 0.6))
    for (x, z) in ((POOL_X + 2, 3), (POOL_X + 2, -3), (POOL_X + 2, 9)):
        for sgn in (1, -1):
            xx = x * sgn
            y = 0
            while not sc.air(xx, y, z) and y < 14:
                y += 1
            sc.set(xx, y, z, 'minecraft:mossy_stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
            sc.set(xx, y + 1, z, 'minecraft:lantern[hanging=false,waterlogged=false]')
    # la pared del fondo, detrás de la cascada alta y de las columnas
    for x in range(-WALL_X - 3, WALL_X + 4):
        for z in range(BACK_Z - 3, BACK_Z + 1):
            for y in range(-1, FALL_TOP + 3):
                sc.set(x, y, z, stone(x, y, z, 67, 0.5))


def roofs(sc):
    """El techo: a los lados, vigas de roble oscuro (a la altura de AISLE_Y) con tablas, hojas y flores de esporas que
    cuelgan, faroles con su cadena; en medio, copas de hojas encima de la nave con el hueco por donde entra el sol."""
    for x in range(AISLE_IN, WALL_X + 1):
        for z in range(BACK_Z, Z1 - 2):
            beam = (z % 5 == 0) or x == AISLE_IN
            st = log('dark_oak_log', 'z' if x == AISLE_IN else 'x') if beam else 'minecraft:dark_oak_planks'
            sc.sym(x, AISLE_Y, z, st)
            sc.sym(x, AISLE_Y + 1, z, leaves('oak_leaves') if vhash(x, z, 71) < 0.5 else leaves('azalea_leaves'))
    # las copas encima de la nave (la parte de delante y la de detrás), con el hueco en medio
    for x in range(-AISLE_IN - 1, AISLE_IN + 2):
        for z in range(BACK_Z, Z1 - 2):
            if OPEN_Z[0] <= z <= OPEN_Z[1]:
                continue
            y = CANOPY_Y + (1 if vhash(x, z, 73) < 0.4 else 0)
            sc.set(x, y, z, leaves(('oak_leaves', 'azalea_leaves', 'flowering_azalea_leaves')[int(vhash(x, z, 75) * 3) % 3]))
            sc.set(x, y + 1, z, leaves('oak_leaves'))
            if vhash(x, z, 77) < 0.35:
                sc.set(x, y - 1, z, leaves('azalea_leaves'))
    for z in range(BACK_Z, Z1 - 2, 5):
        for x in range(-AISLE_IN, AISLE_IN + 1):
            if not OPEN_Z[0] <= z <= OPEN_Z[1]:
                sc.set(x, CANOPY_Y - 1, z, log('dark_oak_log', 'x'))
    # el borde del hueco: hojas que cuelgan (las ilumina el sol)
    for x in range(-AISLE_IN - 1, AISLE_IN + 2):
        for z in (OPEN_Z[0] - 1, OPEN_Z[1] + 1):
            for k in range(1 + int(vhash(x, z, 79) * 3)):
                sc.set(x, CANOPY_Y - 1 - k, z, leaves('azalea_leaves' if k else 'oak_leaves'), only_air=True)
    # lo que cuelga del techo de los lados: flores de esporas, hojas, enredaderas con bayas, faroles
    for x in range(AISLE_IN, WALL_X):
        for z in range(BACK_Z + 1, Z1 - 3):
            v = vhash(x * 3, z * 7, 81)
            for sgn in (1, -1):
                xx = x * sgn
                if not sc.air(xx, AISLE_Y - 1, z):
                    continue
                vv = vhash(xx, z, 83)
                if v < 0.14 and z < -1:
                    sc.set(xx, AISLE_Y - 1, z, 'minecraft:spore_blossom')
                elif z > -3:
                    continue
                elif v < 0.45 and x >= AISLE_IN + 3:
                    sc.set(xx, AISLE_Y - 1, z, leaves('azalea_leaves' if vv < 0.5 else 'flowering_azalea_leaves'))
                elif v < 0.62 and x >= AISLE_IN + 1:
                    hang(sc, xx, AISLE_Y - 1, z, 2 + int(vv * 3), vv)
    # flores de esporas también en el borde de las copas de la nave (las rosas de arriba de la imagen)
    for x in range(-AISLE_IN, AISLE_IN + 1):
        for z in range(OPEN_Z[1] + 2, Z1 - 3, 2):
            y = CANOPY_Y - 1
            while sc.air(x, y, z) and y > 6:
                y -= 1
            top = CANOPY_Y - 1
            while not sc.air(x, top, z) and top > 6:
                top -= 1
            if vhash(x, z, 87) < 0.35 and sc.air(x, top, z):
                sc.set(x, top, z, 'minecraft:spore_blossom')


def hang(sc, x, y, z, n, v):
    """Una cortina de enredadera que cuelga del techo (n bloques)."""
    side = ('n', 's', 'e', 'w')[int(v * 4) % 4]
    for k in range(n):
        if not sc.air(x, y - k, z) or y - k <= 1:
            break
        sc.set(x, y - k, z, vine(**{side: True}))


def top_greenery(sc):
    """Arriba, alrededor del hueco por donde entra el sol: ramas de hojas que cuelgan de las copas y de las vigas,
    con enredaderas largas y flores de esporas (lo que en la imagen brilla verde y rosa arriba)."""
    for x in range(-AISLE_IN - 2, AISLE_IN + 3):
        for z in range(OPEN_Z[0] - 1, OPEN_Z[1] + 6):
            v = vhash(x * 5, z * 3, 97)
            inner = OPEN_Z[0] + 1 <= z <= OPEN_Z[1] - 1 and abs(x) <= 2
            if inner:
                continue
            if v < 0.30:
                y0 = CANOPY_Y - int(vhash(x, z, 99) * 1.6)
                for y in range(y0, CANOPY_Y + 1):
                    sc.set(x, y, z, leaves(('azalea_leaves', 'flowering_azalea_leaves', 'oak_leaves')[int(vhash(x, z + y, 101) * 3) % 3]),
                           only_air=True)
                if v < 0.16:
                    hang(sc, x, y0 - 1, z, 1 + int(vhash(x, z, 103) * 3), vhash(x, z, 105))
                elif v < 0.24:
                    sc.set(x, y0 - 1, z, 'minecraft:spore_blossom', only_air=True)


# (x, y del farol, z): los de la imagen del dueño, medidos con su cámara, y alguno más a los lados
LANTERNS = ((-6, 5, -2), (-7, 3, -1), (-4, 6, -11), (-3, 4, -16), (2, 6, -7), (4, 6, -4), (6, 5, -1), (7, 3, -1),
            (-9, 4, -7), (9, 4, -8), (-8, 4, 5), (8, 4, 4), (3, 4, -16))


def lanterns(sc):
    """Los faroles que cuelgan del techo con su cadena (a distintas alturas, como en la imagen)."""
    for (x, ly, z) in LANTERNS:
        ceil = AISLE_Y if abs(x) >= AISLE_IN else CANOPY_Y
        y = ly + 1
        while y < ceil and sc.air(x, y, z):
            sc.set(x, y, z, 'minecraft:chain[axis=y,waterlogged=false]')
            y += 1
        if not sc.air(x, ly, z):
            continue
        sc.set(x, ly, z, 'minecraft:lantern[hanging=true,waterlogged=false]')


def overgrowth(sc):
    """Enredaderas por todas partes (pilares, paredes, muros de la escalera, columnas de los lados) y liquen."""
    ivy(sc, -WALL_X, WALL_X, 1, AISLE_Y - 1, BACK_Z + 1, Z1 - 3, 91, 0.8)
    ivy(sc, -AISLE_IN - 2, AISLE_IN + 2, 5, CANOPY_Y - 1, BACK_Z + 1, STAIR_Z, 93, 0.6)
    for x in range(-WALL_X + 1, WALL_X):
        for y in range(1, AISLE_Y):
            for z in range(BACK_Z + 1, Z1 - 3):
                if sc.air(x, y, z) and vhash(x * 5 + y, z * 3, 95) < 0.025:
                    for (dx, dz, face) in ((1, 0, 'east'), (-1, 0, 'west'), (0, -1, 'north'), (0, 1, 'south')):
                        if sc.name(x + dx, y, z + dz) in STONE:
                            sc.set(x, y, z, lichen(face))
                            break


def clear_view(sc):
    """Nada entre la cámara y la crate (ni encima, donde gira la ruleta), ni en el camino del haz de sol."""
    for t in range(0, 361, 4):
        eye, at, _ = camera_at(t)
        d = np.array(at) - np.array(eye)
        L = float(np.linalg.norm(d))
        for s in np.arange(0.0, L, 0.4):
            clear_ball(sc, np.array(eye) + d / L * s, 1)
    for x in range(-3, 4):
        for z in range(-3, 12):
            for y in range(3, 12):
                n = sc.name(x, y, z)
                if n in ('minecraft:vine', 'minecraft:cave_vines', 'minecraft:cave_vines_plant', 'minecraft:chain',
                         'minecraft:lantern'):
                    sc.set(x, y, z, 'minecraft:air')
    for s in np.arange(3.0, 30.0, 0.6):
        clear_ball(sc, np.array(CRATE) + SUN * s, 2, only_leaves=True)


def clear_ball(sc, p, r, only_leaves=False):
    for dx in range(-r, r + 1):
        for dy in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if dx * dx + dy * dy + dz * dz > r * r + 1:
                    continue
                x, y, z = int(math.floor(p[0])) + dx, int(math.floor(p[1])) + dy, int(math.floor(p[2])) + dz
                n = sc.name(x, y, z)
                if 'leaves' in n and not (abs(x) <= 2 and -2 <= z <= 2 and y <= 2) or n in (
                        'minecraft:cave_vines', 'minecraft:cave_vines_plant', 'minecraft:spore_blossom', 'minecraft:vine',
                        'minecraft:chain') or (not only_leaves and n == 'minecraft:lantern'):
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
# (tick, ojo, mira, campo de visión vertical): empieza con el encuadre de la imagen del dueño (de frente, centrado, el
# altar delante y la cascada alta al fondo) y se acerca a la crate para la llave; la cinemática (CrateCinematicScreen)
# la recorre con Catmull-Rom
CAMERA = [
    (0, (0.5, 3.7, 12.0), (0.5, 3.55, -10.0), 36.0),        # la imagen del dueño
    (24, (0.5, 3.6, 10.2), (0.5, 3.3, -6.0), 36.0),
    (56, (-0.4, 3.1, 4.6), (0.55, 2.5, 1.0), 40.0),          # la llave llega a la cerradura
    (80, (-0.2, 3.0, 3.9), (0.55, 2.5, 1.0), 40.0),          # gira
    (102, (0.4, 3.7, 7.6), (0.5, 3.0, -1.0), 44.0),          # ¡se abre! (retrocede)
    (130, (0.0, 4.4, 8.2), (0.5, 3.8, 0.0), 46.0),           # la ruleta, encima de la crate
    (194, (1.0, 4.3, 7.8), (0.5, 3.8, 0.0), 45.0),
    (252, (0.6, 4.2, 6.6), (0.5, 3.85, 0.6), 44.0),
    (264, (0.55, 4.2, 5.8), (0.5, 3.9, 1.3), 44.0),          # el premio, delante, en el centro
    (360, (0.3, 4.3, 6.6), (0.5, 3.9, 0.6), 46.0),
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


def spring_points():
    """Dónde cae el agua de cada columna de los lados (x, y del agua, z, 1): para la espuma y las ondas del mod."""
    cx = (SIDE_X[0] + SIDE_X[1] + 1) / 2.0
    return [(1.0 - cx, 0.875, SIDE_Z + 0.5, 1.0), (cx, 0.875, SIDE_Z + 0.5, 1.0)]


def anchors():
    fb = fall_box()
    return {
        'crate': CRATE,
        'pool': (CRATE[0], 0.875, CRATE[2], POOL_R),
        'sun': tuple(float(v) for v in SUN),
        'fall': fb, 'fall_z': (FALL_Z + 1.0, 0.0, 0.0, 0.0),
        'pond': (0.5, STEPS + 1.875, LAND_Z + 0.5, 2.5),
        'stairs': (STAIR_Z + 1.0, LAND_Z + 0.0, float(STEPS), float(STAIR_W)),
        'poolbox': (float(POOL_X), float(POOL_Z0), float(POOL_Z1), 0.0),
        'sunspot': (0.5, CANOPY_Y + 0.3, OPEN_Z[1] - 2.5, 1.0),
        'opening': (0.5, CANOPY_Y + 0.5, (OPEN_Z[0] + OPEN_Z[1] + 1) / 2.0, (OPEN_Z[1] - OPEN_Z[0] + 1) / 2.0),
        'flowers0': (-10.0, 2.5, 2.0, 3.0), 'flowers1': (11.0, 2.5, 2.0, 3.0), 'flowers2': (0.5, 6.5, -14.0, 2.5),
        **{f'spring{i}': p for i, p in enumerate(spring_points())},
    }


def build():
    sc = Scene()
    floor_and_pool(sc)
    side_halls(sc)
    stair_falls(sc)
    side_towers(sc)
    altar(sc)
    roofs(sc)
    top_greenery(sc)
    lanterns(sc)
    overgrowth(sc)
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
