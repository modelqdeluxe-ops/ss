"""Una construcción de un mundo de Minecraft → .schem (Sponge v2: WorldEdit 7.2 y FAWE, Forge/Bukkit/Mohist 1.20.1).

    python3 -I casa_a_schem.py <mundo 1.20.1> --caja x0 y0 z0 x1 y1 z1 --semilla x y z --nombre Nombre --salida carpeta
                               [--margen 14] [--pies x y z]

El mundo tiene que estar ya en la 1.20.1 (DataVersion 3465): si es de otra versión, antes `actualizar.sh` (README).
Saca dos archivos:

  <Nombre>.schem          la construcción sola: la pieza de bloques «hechos» que contiene la semilla (26 vecinos), con
                          el agua, las plantas y el jardín que lleva encima, y una base de 3 bloques del terreno bajo su
                          planta (para que las piscinas y el suelo no queden al aire). Sin el paisaje de alrededor.
  <Nombre>_terreno.schem  lo mismo con su trozo de paisaje: un rectángulo con --margen bloques alrededor, desde 6 bajo el
                          terreno más bajo; los árboles decorados que cruzan el borde van enteros si su tronco está dentro
                          y fuera si no. Con --margen 0 no se hace.

Al pegar (//paste), el jugador queda donde estaban los pies al copiar: por defecto, al final del lado sur de la
construcción (el camino de entrada), de pie en él, mirando al norte. --pies cambia ese punto.

Agua: el agua «colgada» que dejan los constructores (puesta con WorldEdit sin física, con aire debajo) se cae en cuanto
algo la actualiza. Aquí se rellena el aire de debajo con agua cayendo (level=8) hasta tocar algo, que es justo lo que
hace el juego (comprobado en Mohist: mismos bloques), así que pegada se queda quieta.
"""
import argparse
import os
import re
import sys
from collections import Counter

import numpy as np
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtio  # noqa: E402
from mundo import Mundo  # noqa: E402

DATA_VERSION = 3465  # 1.20.1
AIRES = ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air')
# Terreno y plantas naturales: lo que no cuenta como «hecho» (la casa es lo demás: bloques puestos, agua, árboles
# decorados con vallas, etc.)
TERRENO = re.compile(
    r'^minecraft:(stone|dirt|grass_block|coarse_dirt|podzol|mycelium|gravel|sand|red_sand|sandstone|andesite|diorite|'
    r'granite|tuff|.*_ore|snow|snow_block|powder_snow|ice|packed_ice|rooted_dirt|mud|clay|calcite|deepslate|moss_block|'
    r'mossy_cobblestone|cobblestone|grass|fern|tall_grass|large_fern|moss_carpet|dandelion|poppy|azure_bluet|'
    r'oxeye_daisy|cornflower|lily_of_the_valley|allium|blue_orchid|.*_tulip|sweet_berry_bush|bedrock|lava|'
    r'pointed_dripstone|dripstone_block|smooth_basalt|amethyst_block|budding_amethyst|.*amethyst_bud|amethyst_cluster|'
    r'glow_lichen|cave_vines.*|hanging_roots|infested_.*|spore_blossom|big_dripleaf.*|small_dripleaf|azalea|'
    r'flowering_azalea|magma_block|obsidian|bubble_column|seagrass|tall_seagrass|kelp|kelp_plant|lily_pad|'
    r'dead_bush|brown_mushroom|red_mushroom|sugar_cane)$')
N26 = np.ones((3, 3, 3), bool)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('mundo')
    ap.add_argument('--caja', nargs=6, type=int, required=True, metavar=('X0', 'Y0', 'Z0', 'X1', 'Y1', 'Z1'),
                    help='caja del mundo donde buscar (la construcción y su alrededor)')
    ap.add_argument('--semilla', nargs=3, type=int, required=True, metavar=('X', 'Y', 'Z'),
                    help='un bloque de la construcción (una pared, por ejemplo)')
    ap.add_argument('--nombre', required=True)
    ap.add_argument('--salida', required=True)
    ap.add_argument('--margen', type=int, default=14, help='bloques de paisaje alrededor en <Nombre>_terreno (0: no)')
    ap.add_argument('--pies', nargs=3, type=int, metavar=('X', 'Y', 'Z'), help='dónde queda el jugador al pegar')
    a = ap.parse_args()
    os.makedirs(a.salida, exist_ok=True)

    w = Mundo(a.mundo)
    X0, Y0, Z0, X1, Y1, Z1 = a.caja
    vers = Counter(w.version(cx, cz) for cx in range(X0 >> 4, (X1 >> 4) + 1) for cz in range(Z0 >> 4, (Z1 >> 4) + 1))
    if set(vers) != {DATA_VERSION}:
        sys.exit(f'El mundo no está en la 1.20.1 (DataVersion de los chunks: {dict(vers)}). Antes: actualizar.sh')
    arr, pal, bes = w.caja(X0, Y0, Z0, X1, Y1, Z1)
    ents = w.entidades(X0, Y0, Z0, X1, Y1, Z1)
    nombres = [p.split('[')[0] for p in pal]
    aire = np.array([n in AIRES for n in nombres])[arr]
    ter = np.array([bool(TERRENO.match(n)) for n in nombres])[arr]
    hecho = ~aire & ~ter

    # 1) La construcción: la pieza de «hecho» (26 vecinos) que contiene la semilla (o la más grande a 3 bloques de ella)
    lab, n = ndimage.label(hecho, structure=N26)
    sx, sy, sz = a.semilla[0] - X0, a.semilla[1] - Y0, a.semilla[2] - Z0
    cerca = lab[max(0, sy - 3):sy + 4, max(0, sz - 3):sz + 4, max(0, sx - 3):sx + 4]
    tam = np.bincount(lab.ravel())
    cand = [i for i in np.unique(cerca) if i]
    if not cand:
        sys.exit('No hay bloques de construcción cerca de la semilla')
    casa_id = max(cand, key=lambda i: tam[i])
    casa = lab == casa_id
    ys, zs, xs = np.nonzero(casa)
    print(f'construcción: {casa.sum()} bloques, x {xs.min() + X0}..{xs.max() + X0}, y {ys.min() + Y0}..{ys.max() + Y0}, '
          f'z {zs.min() + Z0}..{zs.max() + Z0}')
    if xs.min() == 0 or zs.min() == 0 or xs.max() == arr.shape[2] - 1 or zs.max() == arr.shape[1] - 1:
        print('AVISO: la construcción toca el borde de --caja; agrándala')

    # 2) Planta (+1 alrededor, huecos rellenos) y 3) el terreno en ella desde 3 bajo el primer bloque de la casa
    H = arr.shape[0]
    planta = ndimage.binary_fill_holes(ndimage.binary_dilation(casa.any(0), iterations=1))
    bajo = np.where(casa.any(0), np.argmax(casa, 0), H)
    bajo = ndimage.grey_erosion(bajo, size=(3, 3))  # el borde de la planta toma el de dentro
    yy = np.arange(H)[:, None, None]
    sola = casa | (ter & planta[None] & (yy >= np.clip(bajo - 3, 0, H)[None]))
    lab2, _ = ndimage.label(sola, structure=N26)  # sin trozos sueltos de terreno que no tocan la casa
    t2 = np.bincount(lab2.ravel())
    t2[0] = 0
    sola = lab2 == np.argmax(t2)

    pies = a.pies
    if pies is None:
        zsur = zs.max()
        xx = np.nonzero(casa[:, zsur, :].any(0))[0]
        xm = int(xx[len(xx) // 2])
        pies = (xm + X0, int(np.nonzero(casa[:, zsur, xm])[0].max()) + Y0 + 1, zsur + Z0)
    print('pies al pegar (mundo):', tuple(int(v) for v in pies))

    escribir = Escritor(arr, pal, bes, ents, (X0, Y0, Z0))
    escribir(os.path.join(a.salida, f'{a.nombre}.schem'), sola, pies, a.nombre)

    if a.margen > 0:
        bx0, bz0 = np.nonzero(sola.any(0))[1].min(), np.nonzero(sola.any(0))[0].min()
        bx1, bz1 = np.nonzero(sola.any(0))[1].max(), np.nonzero(sola.any(0))[0].max()
        rx0, rz0, rx1, rz1 = bx0 - a.margen, bz0 - a.margen, bx1 + a.margen, bz1 + a.margen
        if rx0 < 0 or rz0 < 0 or rx1 >= arr.shape[2] or rz1 >= arr.shape[1]:
            sys.exit('--margen se sale de --caja; agranda la caja')
        rect = np.zeros(planta.shape, bool)
        rect[rz0:rz1 + 1, rx0:rx1 + 1] = True
        arriba = np.where(ter.any(0), H - 1 - np.argmax(ter[::-1], 0), 0)
        ybot = int(arriba[rect].min()) - 6
        con = np.zeros_like(sola)
        con[ybot:, rect] = ~aire[ybot:, rect]
        for i in range(1, n + 1):  # árboles y demás piezas: enteras si su punto más bajo está dentro, si no, fuera
            if i == casa_id:
                continue
            m = lab == i
            ys_, zs_, xs_ = np.nonzero(m)
            if not len(ys_):
                continue
            k = np.argmin(ys_)
            con = (con | m) if rect[zs_[k], xs_[k]] else (con & ~m)
        con |= sola
        escribir(os.path.join(a.salida, f'{a.nombre}_terreno.schem'), con, pies, f'{a.nombre} (con terreno)')


class Escritor:
    def __init__(self, arr, pal, bes, ents, origen):
        self.arr, self.pal, self.bes, self.ents = arr, list(pal), bes, ents
        self.X0, self.Y0, self.Z0 = origen

    def __call__(self, ruta, mask, pies, nombre):
        arr, pal, X0, Y0, Z0 = self.arr, self.pal, self.X0, self.Y0, self.Z0
        ys, zs, xs = np.nonzero(mask)
        by0, bz0, bx0 = ys.min(), zs.min(), xs.min()
        sl = (slice(by0, ys.max() + 1), slice(bz0, zs.max() + 1), slice(bx0, xs.max() + 1))
        sub = np.where(mask[sl], arr[sl], 0)
        # agua colgada → el aire de debajo, agua cayendo hasta tocar algo (lo que hace el juego al actualizarla)
        esagua = np.array([p.startswith('minecraft:water[') for p in pal])
        if 'minecraft:water[level=8]' not in pal:
            pal.append('minecraft:water[level=8]')
            esagua = np.append(esagua, True)
        cae = pal.index('minecraft:water[level=8]')
        relleno = 0
        for y in range(sub.shape[0] - 2, -1, -1):
            m = (sub[y] == 0) & esagua[sub[y + 1]]
            sub[y][m] = cae
            relleno += int(m.sum())
        Hh, L, W = sub.shape
        usados = np.unique(sub)
        if usados[0] != 0:
            usados = np.concatenate([[0], usados])
        remap = np.zeros(len(pal), np.int64)
        remap[usados] = np.arange(len(usados))
        datos = bytearray()
        for v in remap[sub].reshape(-1).tolist():  # orden x + z*W + y*W*L
            while v >= 0x80:
                datos.append((v & 0x7F) | 0x80)
                v >>= 7
            datos.append(v)
        wx0, wy0, wz0 = int(bx0 + X0), int(by0 + Y0), int(bz0 + Z0)
        bes = []
        for be in self.bes:
            x, y, z = be['x'][1], be['y'][1], be['z'][1]
            if not mask[y - Y0, z - Z0, x - X0]:
                continue
            d = {k: v for k, v in be.items() if k not in ('x', 'y', 'z', 'id', 'keepPacked')}
            d['Pos'] = (11, [x - wx0, y - wy0, z - wz0])
            d['Id'] = (8, be['id'][1])
            bes.append(d)
        ents = []
        for e in self.ents:  # cuadros, marcos, soportes…: van si su bloque (o uno pegado) está en la máscara
            px, py, pz = e['Pos'][1][1]
            colgado = 'TileX' in e
            if colgado:
                bx, by, bz = e['TileX'][1] - X0, e['TileY'][1] - Y0, e['TileZ'][1] - Z0
            else:
                bx, by, bz = int(np.floor(px)) - X0, int(np.floor(py)) - Y0, int(np.floor(pz)) - Z0
            if not (0 <= by < mask.shape[0] and 0 <= bz < mask.shape[1] and 0 <= bx < mask.shape[2]):
                continue
            if not mask[max(0, by - 1):by + 2, max(0, bz - 1):bz + 2, max(0, bx - 1):bx + 2].any():
                continue
            d = {k: v for k, v in e.items() if k not in ('UUID', 'id', 'Pos')}  # sin UUID: el juego le da uno nuevo
            d['Id'] = (8, e['id'][1])
            # Offset = 0: Pos (y TileX/Y/Z) relativos al mínimo = coordenadas del portapapeles de WorldEdit
            d['Pos'] = (9, (6, [px - wx0, py - wy0, pz - wz0]))
            if colgado:
                d['TileX'] = (3, e['TileX'][1] - wx0)
                d['TileY'] = (3, e['TileY'][1] - wy0)
                d['TileZ'] = (3, e['TileZ'][1] - wz0)
            ents.append(d)
        fx, fy, fz = (int(v) for v in pies)
        raiz = {
            'Version': (3, 2),
            'DataVersion': (3, DATA_VERSION),
            'Metadata': (10, {'WEOffsetX': (3, wx0 - fx), 'WEOffsetY': (3, wy0 - fy), 'WEOffsetZ': (3, wz0 - fz),
                              'Name': (8, nombre)}),
            'Width': (2, W), 'Height': (2, Hh), 'Length': (2, L),
            'Offset': (11, [0, 0, 0]),
            'PaletteMax': (3, len(usados)),
            'Palette': (10, {pal[g]: (3, i) for i, g in enumerate(usados.tolist())}),
            'BlockData': (7, bytes(datos)),
            'BlockEntities': (9, (10, bes)),
            'Entities': (9, (10, ents)),
        }
        nbtio.write_file(ruta, 'Schematic', raiz)
        print(f'{os.path.basename(ruta)}: {W}x{Hh}x{L} (x·y·z), {int((sub != 0).sum())} bloques, paleta {len(usados)}, '
              f'{len(bes)} entidades de bloque, {len(ents)} entidades, agua colgada rellenada {relleno}, '
              f'mínimo {(wx0, wy0, wz0)}, WEOffset {(wx0 - fx, wy0 - fy, wz0 - fz)}, {os.path.getsize(ruta) / 1024:.0f} KB')


if __name__ == '__main__':
    main()
