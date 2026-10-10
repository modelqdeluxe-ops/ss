"""Vistas previas de las skills para la web: nítidas y encuadradas.

    python3 tools/skills/previews.py <carpeta> [clase ...]      (sin clases: todas)

Crea <carpeta>/<clase>/<skill>.webp (WebP animado SIN pérdida, fondo transparente, 480×480, 20 fotogramas/s) que luego
tierras-fantasticas/tools/skills.py pasa a la web (pinta el fondo y comprime una sola vez). Diferencias con
«sim.py --webp»: dibuja directamente al tamaño final con suavizado (antes se dibujaba a 320, se recortaba y se volvía a
agrandar), con la cámara ya encuadrada a lo que pasa (una pasada rápida a 160 px busca dónde está) y, en los combos y
golpes básicos, la skill varias veces seguidas para que se vea la cadena entera y no solo el primer golpe.

De 2 en 2 clases como mucho a la vez (las texturas de Steve y los zombis se cachean en build/tf_preview).
"""
import math
import os
import re
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
import sim as S  # noqa: E402
import vfx_preview as VP  # noqa: E402
import vfx_render as R  # noqa: E402

SIZE = 480
SS = 2
STEP = 1          # un fotograma por tick: 20 por segundo
FOV = 40
FILL = 0.84       # cuánto del cuadro ocupa lo que pasa
MAX_ZOOM = 2.6    # para que una skill muy pequeña no se vea pixelada de cerca
TICKS = 200
COMBO_CASTS = 4
COMBO_EVERY = 14
MAX_FRAMES = 130  # 6,5 s


def staged(cls, skill):
    """¿Combo o golpe con etapas (la misma skill varias veces hace golpes distintos)?"""
    if 'combo' in skill['id']:
        return True
    e = (skill.get('entry') or '').lower()
    return any(k.lower().startswith(e) and re.search(r'_(st_?)?[2-5]$', k.lower()) for k in cls['tree'])


def best_hand(cls, skill, casts):
    """Como sim.main: en las clases que cambian de forma, la variante con más cosas."""
    best = None
    for hand in [None] + sorted((cls.get('hand_items') or {}).keys(), key=len)[1:]:
        hand = hand.lower() if hand else None
        sim, ok, _ = S.run_skill(cls, skill, TICKS, frames=False, hand=hand, casts=casts, every=COMBO_EVERY)
        score = sum(sim.counts.values()) + len(sim.sounds)
        if best is None or score > best[0]:
            best = (score, hand)
    return best[1]


def wpercentile(values, weights, q):
    order = np.argsort(values)
    v, w = values[order], weights[order]
    c = np.cumsum(w)
    return float(v[min(len(v) - 1, np.searchsorted(c, q / 100 * c[-1]))])


def framing(frames):
    """Centro y distancia de la cámara para que lo que pasa llene el cuadro, y hasta qué fotograma se mueve algo."""
    lo, hi = VP.frame_box(frames, keep=(3, 97))
    lo = np.maximum(np.minimum(lo, [-1, 0, -1]), [-9, -1, -4])
    hi = np.minimum(np.maximum(hi, [1, 2, 1]), [9, 9, 12])
    center = (lo + hi) / 2
    dist = max(np.linalg.norm(hi - lo) / 2, 1.5) / math.tan(math.radians(FOV / 2))
    # Pasada rápida a 160 px: cuánto tiempo se ve algo en cada píxel (lo que pasa de largo pesa poco)
    small = 160
    seen = np.zeros((small, small), np.float64)
    last = None
    end = len(frames)
    for i, (tris, pts) in enumerate(frames):
        a = np.asarray(R.raster(tris, size=small, yaw=S.CAM[0], pitch=S.CAM[1], dist=dist, center=center, fov=FOV,
                                ss=1, points=pts))
        seen += a[..., 3] > 16
        if last is not None and np.abs(a.astype(np.int16) - last.astype(np.int16)).mean() > 0.15:
            end = i + 1
        last = a
    ys, xs = np.nonzero(seen > 0)
    if not len(xs):
        return center, dist, end
    w = seen[ys, xs]
    x0, x1 = wpercentile(xs, w, 2), wpercentile(xs, w, 98) + 1
    y0, y1 = wpercentile(ys, w, 2), wpercentile(ys, w, 98) + 1
    f = small / 2 / math.tan(math.radians(FOV / 2))
    ox, oy = (x0 + x1) / 2 - small / 2, (y0 + y1) / 2 - small / 2
    rot = R.look_matrix(*S.CAM)
    # sx = S/2 - camx·f/z: para llevar ese punto al centro, la cámara se mueve -ox·z/f en su eje x (igual en y)
    center = center + rot.T @ np.array([-ox * dist / f, -oy * dist / f, 0.0])
    half = max(x1 - x0, y1 - y0) / 2 / (small / 2)
    zoom = min(MAX_ZOOM, FILL / max(half, 1e-3))
    return center, dist / zoom, end


def render(cls, skill, out_path):
    casts = COMBO_CASTS if staged(cls, skill) else 1
    hand = best_hand(cls, skill, casts)
    sim, ok, frames = S.run_skill(cls, skill, TICKS, frames=True, step=STEP, hand=hand, casts=casts, every=COMBO_EVERY)
    if not frames:
        return 0
    center, dist, end = framing(frames)
    frames = frames[:max(2, min(end + 8, MAX_FRAMES))]
    imgs = [R.raster(t, size=SIZE, yaw=S.CAM[0], pitch=S.CAM[1], dist=dist, center=center, fov=FOV, ss=SS, points=p)
            for t, p in frames]
    imgs += [imgs[-1]] * 6
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    imgs[0].save(out_path, 'WEBP', save_all=True, append_images=imgs[1:], duration=50, loop=0, lossless=True,
                 method=3)
    return len(imgs)


def main():
    args = sys.argv[1:]
    out = os.path.abspath(args[0])
    ids = args[1:] or sorted(f[:-5] for f in os.listdir(S.CLASSES) if f.endswith('.json'))
    for cid in ids:
        cls = S.load_class(cid)
        for sk in cls['skills']:
            if sk.get('hidden'):
                continue
            n = render(cls, sk, os.path.join(out, cid, sk['id'] + '.webp'))
            print(f'{cid}/{sk["id"]}: {n} fotogramas', flush=True)


if __name__ == '__main__':
    main()
