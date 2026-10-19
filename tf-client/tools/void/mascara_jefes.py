"""Por dónde andan los jefes en la isla de The Void (TF Client 1.3.92), sin abrir el juego.

    python3 -I tools/void/mascara_jefes.py src/main/resources/tfclient-void-isla.bin salida.png

Lee la isla (tfclient-void-isla.bin) y hace lo mismo que VoidIsland.edgeField: la distancia de cada columna al borde
(chaflán de 8 vecinos), lo que está a VoidIsland.EDGE (6) bloques o más (y fuera del Velo del Alba, 1.3.96) y, de
eso, solo la parte de la grieta (el islote del este y el santuario se unen por puentes estrechos). En la imagen: verde, por donde andan; azul, andable pero en otra parte; morado,
la franja de 6 bloques; amarillo, el borde; rojo, la grieta. Dice también dónde espera el jugador en la persecución de
la prueba en el juego (BossTest, caso 1200). (Qué bloques son suelo: aquí, por nombre; en el juego, los que tienen
forma de choque.)
"""
import gzip, struct, sys
import numpy as np
from PIL import Image
path, out = sys.argv[1], sys.argv[2]
EDGE = 6.0
f = gzip.open(path, 'rb')
def i32(): return struct.unpack('>i', f.read(4))[0]
def u16(): return struct.unpack('>H', f.read(2))[0]
assert f.read(4) == b'TFVI'
ver = i32()
sx, sy, sz, ox, oy, oz = [i32() for _ in range(6)]
knight = [i32() for _ in range(3)]
arrive = [i32() for _ in range(3)]; ayaw = struct.unpack('>f', f.read(4))[0]
rift = [i32() for _ in range(3)] if ver >= 2 else [0, 72, 0]
bubble, bubble_r = None, 0
if ver >= 3:
    # (1.3.96) el Velo del Alba, el Corazón y los fragmentos
    bubble = [i32() for _ in range(3)]; bubble_r = i32()
    heart = [i32() for _ in range(3)]
    for _ in range(i32()): [i32() for _ in range(3)]
n = i32()
pal = []
for _ in range(n):
    l = u16(); pal.append(f.read(l).decode('utf-8'))
NONSOLID = ('air', 'grass', 'fern', 'flower', 'torch', 'light', 'vine', 'rose', 'tulip', 'dandelion', 'poppy', 'orchid', 'allium',
            'azure_bluet', 'oxeye', 'cornflower', 'lily_of', 'carpet_none', 'sapling', 'button', 'pressure_plate', 'sign', 'banner',
            'cobweb', 'redstone_wire', 'snow[layers=1', 'mushroom', 'seagrass', 'kelp', 'sugar_cane', 'dead_bush', 'bush', 'petals')
def solid(s):
    b = s.split('[')[0].split(':')[-1]
    if b in ('air', 'cave_air', 'void_air', 'light', 'water', 'lava', 'structure_void'): return False
    if b in ('grass', 'tall_grass', 'fern', 'large_fern', 'dead_bush', 'vine', 'torch', 'wall_torch', 'soul_torch', 'soul_wall_torch', 'cobweb',
             'redstone_wire', 'sugar_cane', 'seagrass', 'tall_seagrass', 'kelp', 'kelp_plant', 'pink_petals', 'glow_lichen', 'hanging_roots',
             'moss_carpet' if False else '__'): return False
    if b.endswith(('_sapling', '_button', '_sign', '_banner', '_tulip', 'pressure_plate')) or b in (
        'dandelion', 'poppy', 'blue_orchid', 'allium', 'azure_bluet', 'oxeye_daisy', 'cornflower', 'lily_of_the_valley', 'wither_rose',
        'sunflower', 'lilac', 'rose_bush', 'peony', 'brown_mushroom', 'red_mushroom', 'crimson_fungus', 'warped_fungus', 'crimson_roots',
        'warped_roots', 'nether_sprouts', 'twisting_vines', 'twisting_vines_plant', 'weeping_vines', 'weeping_vines_plant', 'chorus_flower' if False else '__',
        'spore_blossom', 'small_dripleaf', 'torchflower', 'pitcher_plant', 'end_rod' if False else '__'):
        return False
    return True
sol = np.array([solid(s) for s in pal])
cols = np.zeros((sz, sx), bool)
for c in range(sx * sz):
    k = u16()
    g = False
    for _ in range(k):
        y0, ln, b = u16(), u16(), u16()
        if b < n and sol[b]: g = True
    cols[c // sx, c % sx] = g
big = 1e6
d = np.where(cols, big, 0.0).astype(np.float64)
D = 2 ** 0.5
for z in range(sz):
    for x in range(sx):
        if d[z, x] == 0: continue
        v = d[z, x]
        v = min(v, (d[z, x - 1] if x > 0 else 0) + 1)
        v = min(v, (d[z - 1, x] if z > 0 else 0) + 1)
        v = min(v, (d[z - 1, x - 1] if x > 0 and z > 0 else 0) + D)
        v = min(v, (d[z - 1, x + 1] if x < sx - 1 and z > 0 else 0) + D)
        d[z, x] = v
for z in range(sz - 1, -1, -1):
    for x in range(sx - 1, -1, -1):
        if d[z, x] == 0: continue
        v = d[z, x]
        v = min(v, (d[z, x + 1] if x < sx - 1 else 0) + 1)
        v = min(v, (d[z + 1, x] if z < sz - 1 else 0) + 1)
        v = min(v, (d[z + 1, x + 1] if x < sx - 1 and z < sz - 1 else 0) + D)
        v = min(v, (d[z + 1, x - 1] if x > 0 and z < sz - 1 else 0) + D)
        d[z, x] = v
walk0 = d >= EDGE
if bubble_r > 0:
    # = VoidIsland.veilColumn: dentro del Velo del Alba (y 2 bloques más) no entra ningún jefe
    zz, xx = np.mgrid[0:sz, 0:sx]
    walk0 &= (xx + ox + 0.5 - (bubble[0] + 0.5)) ** 2 + (zz + oz + 0.5 - (bubble[2] + 0.5)) ** 2 >= (bubble_r + 2) ** 2
# = VoidIsland.edgeField: solo la parte de la grieta (el islote del este se une por un puente estrecho)
from collections import deque
walk = np.zeros_like(walk0)
rx0, rz0 = rift[0] - ox, rift[2] - oz
if walk0[rz0, rx0]:
    q = deque([(rz0, rx0)]); walk[rz0, rx0] = True
    while q:
        z, x = q.popleft()
        for dz, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            a, b = z + dz, x + dx
            if 0 <= a < sz and 0 <= b < sx and walk0[a, b] and not walk[a, b]:
                walk[a, b] = True; q.append((a, b))
else:
    walk = walk0
print('isla', sx, sz, 'origen', ox, oz, 'columnas con suelo', cols.sum(), 'andables', walk.sum(), 'máx. distancia al borde', d.max())
def at(x, z):
    ix, iz = x - ox, z - oz
    return d[iz, ix] if 0 <= ix < sx and 0 <= iz < sz else 0
print('grieta', rift, 'dist', at(rift[0], rift[2]), '| llegada', arrive, at(arrive[0], arrive[2]), '| caballero', knight, at(knight[0], knight[2]))
img = np.zeros((sz, sx, 3), np.uint8)
img[cols] = (90, 80, 120)
img[walk0] = (40, 110, 140)
img[walk] = (60, 170, 90)
img[(d > 0) & (d < 1.5)] = (230, 210, 120)
rx, rz = rift[0] - ox, rift[2] - oz
img[max(0, rz - 2):rz + 3, max(0, rx - 2):rx + 3] = (255, 60, 60)
Image.fromarray(img).resize((sx * 2, sz * 2), Image.NEAREST).save(out)

# = BossTest caso 1200: desde la grieta hacia el este por z = 0 hasta que el siguiente esté a 2,5 o menos del vacío
x = 0
while x < 400 and at(x + 1, 0) > 2.5: x += 1
print('sitio de la persecución: x', x, 'distancia al borde', at(x, 0), '| la columna andable más cercana está a', end=' ')
iz = 0 - oz
best = min(abs(xx - (x - ox)) for xx in range(sx) if walk[iz, xx])
print(best, 'bloques')
