"""Compara la imagen del dueño con la cinemática: la vista previa (santuario_render.py) o una captura del juego (la prueba
[prueba-jefes=crates], rama pruebas-jefes: 00_crates_crate2_00_llegada.jpg, tick 12). Lo mismo que se hizo en la 1.3.99.

    python3 -I tools/crates/comparar.py <imagen> [--ref docs/crates/imagen-del-dueno.webp] [--lado salida.png]

Imprime, foto → imagen:
  - los colores por tipo de cosa: agua (turquesa), hojas, piedra, faroles y sol, rosa, sombras: qué parte de la imagen
    ocupa cada uno, su color medio y su brillo del 90 %;
  - saturación media (y en lo claro), luz en los percentiles 5/50/95, contraste global, contraste local (8 px) y nitidez;
  - por zonas fijas del encuadre (agua de cerca, cascadas, altar, paredes...), el color medio.
Con --lado escribe las dos una encima de otra (arriba la foto) para mirarlas.

La vista previa lleva franjas de cine finas (3,5 %); la captura del juego, también: se recortan las dos igual.
"""
import argparse
import colorsys
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REF = os.path.normpath(os.path.join(HERE, '..', '..', 'docs', 'crates', 'imagen-del-dueno.webp'))
BARS = 0.035

ZONES = {'agua de cerca': (100, 600, 400, 700), 'agua derecha': (880, 580, 1150, 690), 'cascada alta': (540, 120, 690, 380),
         'cascada izquierda': (345, 300, 415, 500), 'sol arriba': (600, 28, 680, 100), 'altar': (600, 550, 715, 690),
         'pared izquierda': (0, 120, 50, 230), 'musgo izquierda': (150, 300, 245, 430), 'fondo derecha': (1000, 190, 1070, 300),
         'imagen entera': (0, 0, 1280, 721)}


def load(path, bars):
    im = Image.open(path).convert('RGB')
    if bars:
        w, h = im.size
        b = int(h * BARS)
        im = im.crop((0, b, w, h - b))
    return im


def kinds(a):
    px = a.reshape(-1, 3)[::3]
    hsv = np.array([colorsys.rgb_to_hsv(*p) for p in px])
    h, s, v = hsv[:, 0] * 360, hsv[:, 1], hsv[:, 2]
    groups = {
        'agua (turquesa)': (h > 170) & (h < 200) & (s > 0.45) & (v > 0.35),
        'hojas (verde)': (h > 70) & (h < 160) & (s > 0.3) & (v > 0.12),
        'piedra (gris)': (s < 0.3) & (v > 0.15) & (v < 0.7),
        'faroles y sol': (h > 20) & (h < 60) & (s > 0.35) & (v > 0.5),
        'rosa (flores)': ((h > 290) | (h < 10)) & (s > 0.35) & (v > 0.35),
        'sombras': v < 0.15,
    }
    out = {}
    for k, m in groups.items():
        out[k] = (m.mean() * 100, tuple(int(c) for c in px[m].mean(0) * 255) if m.any() else None,
                  int(np.percentile(v[m], 90) * 255) if m.any() else None)
    return out


def stats(a):
    mx, mn = a.max(2), a.min(2)
    s = (mx - mn) / np.maximum(mx, 1e-6)
    L = a @ np.array([0.2126, 0.7152, 0.0722])
    h, w = L.shape
    k = 8
    blocks = L[:h // k * k, :w // k * k].reshape(h // k, k, w // k, k).transpose(0, 2, 1, 3).reshape(-1, k * k)
    return {'saturación media': s.mean(), 'saturación en lo claro': s[mx > 0.5].mean(),
            'luz p5': np.percentile(L, 5), 'luz p50': np.percentile(L, 50), 'luz p95': np.percentile(L, 95),
            'contraste global': L.std(), 'contraste local (8 px)': blocks.std(1).mean(),
            'nitidez': np.abs(np.diff(L, axis=1)).mean() + np.abs(np.diff(L, axis=0)).mean()}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('imagen')
    ap.add_argument('--ref', default=REF)
    ap.add_argument('--lado')
    o = ap.parse_args()
    ref = load(o.ref, False)
    img = load(o.imagen, True)
    small = [np.asarray(x.resize((640, 360), Image.LANCZOS)).astype(float) / 255 for x in (ref, img)]
    print(f"{'':24s} {'FOTO':>36s}    {'IMAGEN':>36s}")
    a, b = kinds(small[0]), kinds(small[1])
    for k in a:
        (pa, ca, va), (pb, cb, vb) = a[k], b[k]
        print(f'{k:24s} {pa:5.1f}% {str(ca):>16s} brillo90 {va}    {pb:5.1f}% {str(cb):>16s} brillo90 {vb}')
    print()
    sa, sb = stats(small[0]), stats(small[1])
    for k in sa:
        print(f'{k:24s} foto {sa[k]:.3f}   imagen {sb[k]:.3f}   ({(sb[k] / sa[k] - 1) * 100:+.0f} %)')
    print()
    big = [x.resize((1280, 721), Image.LANCZOS) for x in (ref, img)]
    for k, box in ZONES.items():
        ca = tuple(int(c) for c in np.asarray(big[0].crop(box)).reshape(-1, 3).mean(0))
        cb = tuple(int(c) for c in np.asarray(big[1].crop(box)).reshape(-1, 3).mean(0))
        print(f'{k:24s} foto {ca}   imagen {cb}')
    if o.lado:
        W = Image.new('RGB', (1280, 1442))
        W.paste(big[0], (0, 0))
        W.paste(big[1], (0, 721))
        W.resize((960, 1082)).save(o.lado)
        print('→', o.lado)


if __name__ == '__main__':
    main()
