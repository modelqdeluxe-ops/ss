"""Vistas de un .schem (isométricas desde los 4 lados y planta) con el color medio de las texturas de la 1.20.1.

    python3 -I vista.py <archivo.schem> <client.jar de la 1.20.1> <prefijo de salida> [--escala 3] [--sin-terreno]

Saca <prefijo>_iso0..3.png (desde el sureste, suroeste, noroeste y noreste) y <prefijo>_planta.png. El client.jar se
baja de Mojang (README). Sirve para mirar que el schematic está entero antes de dárselo al dueño.
"""
import argparse
import io
import os
import sys
import zipfile

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from schem import leer  # noqa: E402

INVISIBLES = ('air', 'cave_air', 'void_air', 'barrier', 'light', 'structure_void')
TINTE = {'grass_block': (110, 160, 70), 'grass': (100, 150, 60), 'tall_grass': (100, 150, 60), 'fern': (90, 140, 60),
         'large_fern': (90, 140, 60), 'vine': (70, 120, 40), 'water': (50, 80, 200), 'water_cauldron': (50, 80, 200),
         'lava': (220, 100, 20), 'oak_leaves': (70, 120, 40), 'spruce_leaves': (60, 95, 60),
         'birch_leaves': (110, 140, 70), 'jungle_leaves': (60, 140, 30), 'acacia_leaves': (90, 130, 40),
         'dark_oak_leaves': (60, 110, 30), 'mangrove_leaves': (80, 130, 40)}
SUFIJOS = ('_stairs', '_slab', '_wall', '_fence_gate', '_fence', '_pressure_plate', '_button', '_wall_sign', '_sign',
           '_wall_hanging_sign', '_hanging_sign', '_carpet', '_pane', '_wall_banner', '_banner', '_trapdoor', '_door')


class Colores:
    def __init__(self, jar):
        self.z = zipfile.ZipFile(jar)
        self.hay = set(self.z.namelist())
        self.cache = {}

    def _tex(self, n):
        p = f'assets/minecraft/textures/block/{n}.png'
        if p not in self.hay:
            return None
        a = np.asarray(Image.open(io.BytesIO(self.z.read(p))).convert('RGBA'))[:16, :16].reshape(-1, 4).astype(float)
        m = a[:, 3] > 20
        return a[m, :3].mean(0) if m.any() else None

    def __call__(self, estado):
        n = estado.split('[')[0].replace('minecraft:', '')
        if n in self.cache:
            return self.cache[n]
        if n in TINTE:
            self.cache[n] = np.array(TINTE[n], float)
            return self.cache[n]
        base = next((n[:-len(s)] for s in SUFIJOS if n.endswith(s)), n)
        c = []
        if n.startswith('potted_'):
            c.append('flower_pot')
        if n.endswith('_wood'):
            c.append(n.replace('_wood', '_log'))
        if n.endswith('_carpet') or n.endswith('_banner'):
            c += [base + '_wool', base + '_block']
        if base.endswith('_brick') or base.endswith('_tile'):
            c.append(base + 's')
        if 'sign' in n or n.endswith('_fence') or n.endswith('_fence_gate'):
            c.append(base + '_planks')
        if base.startswith('smooth_'):
            c += [base[7:] + '_top', base[7:], base[7:] + '_block_top', base[7:] + '_block_bottom', 'smooth_stone']
        for b in (n, base):
            c += [b + '_top', b, b + '_planks', b + '_block', b + 's', b + '_front', b + '_side', b + '_0',
                  b + '_stage3']
        if 'chest' in n:
            c.append('oak_planks')
        if 'campfire' in n:
            c.append('campfire_log')
        if 'skull' in n or 'head' in n:
            c.append('bone_block_side')
        if 'cauldron' in n:
            c.append('cauldron_side')
        col = None
        for t in c:
            col = self._tex(t)
            if col is not None:
                if 'leaves' in t:
                    col = col * np.array([0.5, 0.8, 0.4])
                break
        self.cache[n] = col
        return col


def iso(arr, rgb, vis, s=3, giro=0):
    """Isométrica: cubos con la cara de arriba y dos laterales, de atrás hacia delante."""
    a = np.rot90(arr, giro, axes=(1, 2))
    sy, sz, sx = a.shape
    W, H = (sx + sz) * 2 * s + 4 * s, (sx + sz) * s + sy * 2 * s + 4 * s
    img = np.zeros((H, W, 3), np.uint8)
    img[:] = (24, 25, 34)
    v = vis[a]
    p = np.pad(v, 1)
    expuesto = (~p[2:, 1:-1, 1:-1]) | (~p[1:-1, 2:, 1:-1]) | (~p[1:-1, 1:-1, 2:])
    ys, zs, xs = np.nonzero(v & expuesto)
    o = np.lexsort((ys, xs + zs))
    ys, zs, xs = ys[o], zs[o], xs[o]
    cols = rgb[a[ys, zs, xs]]
    tw = th = 4 * s
    arriba = np.zeros((th, tw), bool)
    izq = np.zeros((th, tw), bool)
    der = np.zeros((th, tw), bool)
    for yy in range(th):
        for xx in range(tw):
            if abs(xx - 2 * s + 0.5) / 2 + abs(yy - s + 0.5) <= s:
                arriba[yy, xx] = True
            elif yy >= s and xx < 2 * s and yy - s - xx * 0.5 >= 0 and yy - xx * 0.5 <= 3 * s:
                izq[yy, xx] = True
            elif yy >= s and xx >= 2 * s and yy - s - (tw - 1 - xx) * 0.5 >= 0 and yy - (tw - 1 - xx) * 0.5 <= 3 * s:
                der[yy, xx] = True
    ox, oy = sz * 2 * s + 2 * s, sy * 2 * s + 2 * s
    for y, z, x, c in zip(ys, zs, xs, cols):
        px, py = ox + (x - z) * 2 * s - 2 * s, oy + (x + z) * s - y * 2 * s - s
        t = img[py:py + th, px:px + tw]
        if t.shape[:2] != (th, tw):
            continue
        t[arriba] = np.clip(c, 0, 255)
        t[izq] = np.clip(c * 0.75, 0, 255)
        t[der] = np.clip(c * 0.6, 0, 255)
    im = Image.fromarray(img)
    caja = Image.fromarray(((img != (24, 25, 34)).any(2) * 255).astype(np.uint8)).getbbox()
    return im.crop((max(0, caja[0] - 8), max(0, caja[1] - 8), min(W, caja[2] + 8), min(H, caja[3] + 8))) if caja else im


def planta(arr, rgb, vis, s=6):
    v = vis[arr]
    top = arr.shape[0] - 1 - np.argmax(v[::-1], 0)
    img = rgb[arr[top, np.arange(arr.shape[1])[:, None], np.arange(arr.shape[2])[None, :]]]
    img = img * (0.55 + 0.45 * (top - top.min()) / max(1, top.max() - top.min()))[..., None]
    img[~v.any(0)] = (24, 25, 34)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    return im.resize((arr.shape[2] * s, arr.shape[1] * s), Image.NEAREST)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('schem')
    ap.add_argument('jar')
    ap.add_argument('prefijo')
    ap.add_argument('--escala', type=int, default=3)
    a = ap.parse_args()
    r, arr, inv, _ = leer(a.schem)
    col = Colores(a.jar)
    rgb = np.zeros((len(inv), 3))
    vis = np.zeros(len(inv), bool)
    sin = set()
    for i, e in enumerate(inv):
        n = e.split('[')[0].replace('minecraft:', '')
        if n in INVISIBLES:
            continue
        c = col(e)
        vis[i] = True
        if c is None:
            sin.add(n)
            c = (255, 0, 255)
        rgb[i] = c
    if sin:
        print('sin color (salen en magenta):', sorted(sin))
    for g in range(4):
        iso(arr, rgb, vis, a.escala, g).save(f'{a.prefijo}_iso{g}.png')
    planta(arr, rgb, vis).save(f'{a.prefijo}_planta.png')
    print(f'{a.prefijo}_iso0..3.png y {a.prefijo}_planta.png ({r["Width"]}x{r["Height"]}x{r["Length"]})')


if __name__ == '__main__':
    main()
