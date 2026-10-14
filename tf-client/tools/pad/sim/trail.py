"""Las estelas de arma (1.3.67) en el simulador: el mismo cálculo que TrailClient.draw y TrailPreview.draw, para ver
la vista previa de Efectos › COMBATE. Los quads se rasterizan con numpy (alfa por vértice, textura que se repite)."""
import math
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
TRAIL_TEX = os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/trail')


def L(type_, frm, to, af, at, fp, tp, br=1.0, wh=0.0, tex=None, rep=1.0, scroll=0.0, per=0, dens=0.0, size=0.0, drift=0.0, cells=0,
      steps=0):
    return dict(type=type_, frm=frm, to=to, af=af, at=at, fp=fp, tp=tp, br=br, wh=wh, tex=tex, rep=rep, scroll=scroll, per=per,
                dens=dens, size=size, drift=drift, cells=cells, steps=steps)


# TrailProfile.ALL
PROFILES = [
    ('Clásico', 1, 1, [L('R', 0, 1, 1, 0, 2, 1)]),
    ('Tajo', 0.55, 0.5, [L('R', 0, 1, 0.85, 0, 3, 2), L('R', 0, 0.14, 1, 0.5, 2.5, 1.5, 1, 0.85)]),
    ('Neón', 1, 1, [L('R', 0.05, 1, 0.6, 0.05, 1.5, 1, 0.45, 0), L('R', 0, 0.06, 1, 1, 1.5, 0, 1.3, 0.3)]),
    ('Brasas', 1, 1, [L('R', 0, 1, 0.5, 0, 2, 1), L('S', 0, 0.8, 1, 0.6, 1.2, 0, 1, 0.45, per=2, dens=0.55, size=0.035, drift=0.6)]),
    ('Píxel', 1, 1, [L('P', 0, 0.9, 1, 0.55, 1, 0, cells=5, steps=4)]),
    ('Humo', 1, 1.15, [L('R', 0, 1, 0.9, 0.55, 1.2, 0.5, tex='smoke', rep=1, scroll=0.35)]),
    ('Destellos', 1, 1, [L('R', 0, 1, 1, 0.5, 1.5, 1, tex='streaks', rep=1.5, scroll=1.2)]),
]

DEFAULTS = dict(enabled=True, length=6, width=60, opacity=85, blend=0, fade=0, minOpacity=0, core=0, profile=0, lowDetail=False,
                reducedFlash=False)

_TEX = {}


def tex(name):
    if name not in _TEX:
        _TEX[name] = np.asarray(Image.open(f'{TRAIL_TEX}/{name}.png').convert('RGBA'), float) / 255
    return _TEX[name]


def blade_of(item_png):
    """TrailClient.readBlade: la punta (arriba a la derecha), la guarda (a un tercio) y el color de la hoja."""
    im = np.asarray(Image.open(item_png).convert('RGBA'))
    h, w = im.shape[:2]
    best, worst = -10 ** 9, 10 ** 9
    bx = by = hx = hy = 0
    for y in range(h):
        for x in range(w):
            if im[y, x, 3] < 40:
                continue
            d = x - y
            if d > best:
                best, bx, by = d, x, y
            if d < worst:
                worst, hx, hy = d, x, y
    tx, ty, ex, ey = (bx + 0.5) / w, 1 - (by + 0.5) / h, (hx + 0.5) / w, 1 - (hy + 0.5) / h
    return tx, ty, ex + (tx - ex) * 0.32, ey + (ty - ey) * 0.32


def _cr(p0, p1, p2, p3, u):
    u2, u3 = u * u, u * u * u
    return 0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3)


class Raster:
    """Pinta quads sobre un trozo de la imagen del simulador (en píxeles reales), con brillo (suma) o translúcido."""

    def __init__(self, arr, ox, oy):
        self.a, self.ox, self.oy = arr, ox, oy

    def tri(self, P, A, U, V, c, additive, texture):
        h, w = self.a.shape[:2]
        xs, ys = [p[0] - self.ox for p in P], [p[1] - self.oy for p in P]
        x0, x1 = max(0, int(math.floor(min(xs)))), min(w - 1, int(math.ceil(max(xs))))
        y0, y1 = max(0, int(math.floor(min(ys)))), min(h - 1, int(math.ceil(max(ys))))
        if x1 < x0 or y1 < y0:
            return
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        (ax, ay), (bx_, by_), (cx, cy) = zip(xs, ys)
        den = (by_ - cy) * (ax - cx) + (cx - bx_) * (ay - cy)
        if abs(den) < 1e-9:
            return
        l0 = ((by_ - cy) * (gx - cx) + (cx - bx_) * (gy - cy)) / den
        l1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
        l2 = 1 - l0 - l1
        inside = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
        if not inside.any():
            return
        alpha = l0 * A[0] + l1 * A[1] + l2 * A[2]
        col = np.ones(alpha.shape + (3,)) * np.array(c)
        if texture is not None:
            u = l0 * U[0] + l1 * U[1] + l2 * U[2]
            v = l0 * V[0] + l1 * V[1] + l2 * V[2]
            th, tw = texture.shape[:2]
            ti = np.clip((v * th).astype(int), 0, th - 1)
            tj = (np.floor(u * tw).astype(int)) % tw
            t = texture[ti, tj]
            col = col * t[..., :3]
            alpha = alpha * t[..., 3]
        region = self.a[y0:y1 + 1, x0:x1 + 1, :3]
        m = inside[..., None]
        if additive:
            region += np.where(m, col * alpha[..., None], 0)
        else:
            region[:] = np.where(m, col * alpha[..., None] + region * (1 - alpha[..., None]), region)

    def quad(self, P, A, U, V, c, additive, texture=None):
        self.tri([P[0], P[1], P[2]], [A[0], A[1], A[2]], [U[0], U[1], U[2]], [V[0], V[1], V[2]], c, additive, texture)
        self.tri([P[0], P[2], P[3]], [A[0], A[2], A[3]], [U[0], U[2], U[3]], [V[0], V[2], V[3]], c, additive, texture)


def _alpha(l, age, glob, st):
    fp = {1: 0.6, 2: 1.0, 3: 3.0}.get(st['fade'], l['fp'])
    k = (1 - age) ** max(0.05, fp)
    a = l['at'] + (l['af'] - l['at']) * k
    a = max(a, st['minOpacity'] / 100 * l['af'])
    a *= min(1.0, (1 - age) / 0.12)
    return max(0.0, min(1.0, a * glob))


def _rgb(l, color, st):
    br = min(1.0, l['br']) if st['reducedFlash'] else l['br']
    wh = l['wh'] * 0.5 if st['reducedFlash'] else l['wh']
    out = []
    for sh in (16, 8, 0):
        v = min(1.0, ((color >> sh) & 255) / 255 * br)
        out.append(v + (1 - v) * wh)
    return out


def _hash(x):
    x &= 0xFFFFFFFF
    x ^= x >> 16
    x = (x * 0x7FEB352D) & 0xFFFFFFFF
    x ^= x >> 15
    x = (x * 0x846CA68B) & 0xFFFFFFFF
    x ^= x >> 16
    return x


def draw_trail(R, samples, now, color, st, to_px, right=(1, 0), up=(0, -1), time_s=0.0):
    """TrailClient.draw para un tramo: samples = [(tx, ty, bx, by, at_ms, seed)], en unidades; to_px pasa a píxeles."""
    name, lscale, wscale, layers = PROFILES[st['profile']]
    life = st['length'] * 50 * lscale
    samples = [s for s in samples if now - s[4] <= life]
    if len(samples) < 2:
        return
    additive = st['blend'] == 0
    glob = st['opacity'] / 100 * (1.7 if st['blend'] == 2 else 1) * (0.7 if additive and st['reducedFlash'] else 1)
    raw = len(samples)
    k = 1 if st['lowDetail'] or raw < 3 else max(1, min(4, 199 // max(1, raw - 1)))
    pts = []
    for i in range(raw - 1):
        p0, p1, p2, p3 = samples[max(0, i - 1)], samples[i], samples[i + 1], samples[min(raw - 1, i + 2)]
        for j in range(k):
            u = j / k
            pts.append([_cr(p0[c], p1[c], p2[c], p3[c], u) for c in range(4)] + [min(1, max(0, (now - (p1[4] + (p2[4] - p1[4]) * u)) / life))])
    last = samples[-1]
    pts.append(list(last[:4]) + [min(1, max(0, (now - last[4]) / life))])
    layers = list(layers)
    if st['core'] > 0:
        kk = st['core'] / 100 * (0.5 if st['reducedFlash'] else 1)
        layers.append(L('R', 0, 0.07, kk, 0, 1.6, 0, 1.2, 0.3 if st['reducedFlash'] else 0.75))
    for li, l in enumerate(layers):
        c = _rgb(l, color, st)
        if l['type'] == 'R':
            texture = tex(l['tex']) if l['tex'] else None
            span = l['to'] - l['frm']
            prev = None
            for p in pts:
                tx, ty, bx, by, age = p
                taper = 1 if l['tp'] <= 0 else (1 - age) ** l['tp']
                f0, f1 = l['frm'], l['frm'] + span * taper
                a0 = to_px(tx + (bx - tx) * f0, ty + (by - ty) * f0)
                a1 = to_px(tx + (bx - tx) * f1, ty + (by - ty) * f1)
                cur = (a0, a1, _alpha(l, age, glob, st), age * l['rep'] - time_s * l['scroll'])
                if prev and (prev[2] > 0.002 or cur[2] > 0.002):
                    R.quad([prev[0], prev[1], cur[1], cur[0]], [prev[2], prev[2], cur[2], cur[2]], [prev[3], prev[3], cur[3], cur[3]],
                           [0, 1, 1, 0], c, additive, texture)
                prev = cur
        elif l['type'] == 'S':
            for s in samples:
                age = min(1, max(0, (now - s[4]) / life))
                a = _alpha(l, age, glob, st)
                if a <= 0.01:
                    continue
                blen = math.hypot(s[2] - s[0], s[3] - s[1])
                scale = max(0.3, blen / 0.55)
                for j in range(l['per']):
                    h = _hash(s[5] * 31 + j * 7919 + li * 104729)
                    if (h & 0xFFFF) / 65535 > l['dens']:
                        continue
                    along = l['frm'] + (l['to'] - l['frm']) * (((h >> 16) & 0xFFFF) / 65535)
                    h2 = _hash(h)
                    rx, ry = ((h2 & 0x3FF) / 1023 - 0.5) * 2, (((h2 >> 10) & 0x3FF) / 1023 - 0.5) * 2
                    drift = l['drift'] * age * scale
                    x = s[0] + (s[2] - s[0]) * along + rx * drift + up[0] * drift * 0.6
                    y = s[1] + (s[3] - s[1]) * along + ry * drift + up[1] * drift * 0.6
                    sz = l['size'] * (1 - 0.6 * age) * scale
                    R.quad([to_px(x - sz, y - sz), to_px(x + sz, y - sz), to_px(x + sz, y + sz), to_px(x - sz, y + sz)], [a] * 4,
                           [0] * 4, [0] * 4, c, additive)
        else:
            cells, steps = max(1, l['cells']), max(1, l['steps'])
            lastp = None
            for s in samples:
                blen = math.hypot(s[2] - s[0], s[3] - s[1])
                cell = blen * (l['to'] - l['frm']) / cells
                if lastp and (s[0] - lastp[0]) ** 2 + (s[1] - lastp[1]) ** 2 < cell * cell * 0.6:
                    continue
                lastp = s
                age = min(1, max(0, (now - s[4]) / life))
                q = math.ceil((1 - age) * steps) / steps
                a = math.ceil(_alpha(l, age, glob, st) * steps) / steps
                if q <= 0 or a <= 0.01:
                    continue
                for kk in range(cells):
                    f = l['frm'] + (l['to'] - l['frm']) * (kk + 0.5) / cells
                    x, y = s[0] + (s[2] - s[0]) * f, s[1] + (s[3] - s[1]) * f
                    hs = cell * 0.5 * q
                    R.quad([to_px(x - hs, y - hs), to_px(x + hs, y - hs), to_px(x + hs, y + hs), to_px(x - hs, y + hs)], [a] * 4,
                           [0] * 4, [0] * 4, c, additive)


def preview(s, x, y, w, h, item_png, color, st, t_ms, period=1350, swing=300):
    """TrailPreview.draw en el instante t_ms del ciclo: el escenario, la estela y el arma girada sobre su empuñadura."""
    s.vgrad(x, y, w, h, (0x14, 0x1C, 0x3C), (0x07, 0x09, 0x1A))
    s.fill(x, y + h - 9, x + w, y + h, (0x0B, 0x10, 0x28))
    s.fill(x, y + h - 9, x + w, y + h - 8, (0x78, 0xA0, 0xFF, 0x33))
    for i in range(7):
        sx, sy = x + 6 + (i * 37) % max(1, w - 12), y + 4 + (i * 23) % max(1, h // 2)
        s.fill(sx, sy, sx + 1, sy + 1, (255, 255, 255, 0x99 if i % 3 == 0 else 0x55))
    a0, a1 = -125.0, 40.0

    def angle_at(t):
        if t < swing:
            u = t / swing
            return a0 + (a1 - a0) * (1 - (1 - u) ** 3)
        if t < period - 520:
            u = (t - swing) / 220
            return a1 + (math.sin(u * math.pi) * 4 if u < 1 else 0)
        u = (t - (period - 520)) / 520
        return a1 + (a0 - a1) * (u * u * (3 - 2 * u))

    k = h * 0.6 / 16
    px, py = x + w * 0.5, y + h * 0.74
    btx, bty, bgx, bgy = blade_of(item_png)
    hx = (bgx - (btx - bgx) * 0.25) * 16
    hy = (1 - (bgy - (bty - bgy) * 0.25)) * 16

    def M(ang, sx_, sy_):
        r = math.radians(ang)
        X, Y = (sx_ - hx) * k, (sy_ - hy) * k
        return px + X * math.cos(r) - Y * math.sin(r), py + X * math.sin(r) + Y * math.cos(r)

    _, _, wscale, _ = PROFILES[st['profile']]
    wd = min(1.25, st['width'] / 100 * wscale)
    samples = []
    tf = 0.0
    while tf <= t_ms and tf < swing:
        ang = angle_at(tf)
        tx, ty = M(ang, btx * 16, (1 - bty) * 16)
        bx_, by_ = btx + (bgx - btx) * wd, bty + (bgy - bty) * wd
        bx, by = M(ang, bx_ * 16, (1 - by_) * 16)
        samples.append((tx, ty, bx, by, tf, int(tf * 977) * 31))
        tf += 1000 / 60
    # a píxeles reales: el trozo de imagen del escenario
    r = s._rect(x, y, x + w, y + h)
    arr = np.asarray(s.art, float)[r[1]:r[3], r[0]:r[2]].copy() / 255
    R = Raster(arr, r[0], r[1])

    def to_px(ux, uy):
        q = s._rect(ux, uy, ux, uy)
        ox, oy = s.o
        return (ox + ux * s.f, oy + uy * s.f)

    if st['enabled']:
        draw_trail(R, samples, t_ms, color, st, to_px, time_s=t_ms / 1000)
    arr = np.clip(arr, 0, 1)
    arr[..., 3] = 1
    s.art.paste(Image.fromarray((arr * 255).round().astype(np.uint8), 'RGBA'), (r[0], r[1]))
    # el arma girada (su textura de 16, a k unidades por píxel), encima
    ang = angle_at(t_ms)
    sprite = Image.open(item_png).convert('RGBA')
    kk = k * s.f
    size = (r[2] - r[0], r[3] - r[1])
    rad = math.radians(ang)
    ca, sa = math.cos(rad), math.sin(rad)
    ppx, ppy = (s.o[0] + px * s.f) - r[0], (s.o[1] + py * s.f) - r[1]
    # salida (X, Y) -> textura: inversa de T(pp)·R·S(kk)·T(-h)
    a_, b_, c_ = ca / kk, sa / kk, 0
    d_, e_ = -sa / kk, ca / kk
    c_ = -(a_ * ppx + b_ * ppy) + hx
    f_ = -(d_ * ppx + e_ * ppy) + hy
    rot = sprite.transform(size, Image.AFFINE, (a_, b_, c_, d_, e_, f_), resample=Image.NEAREST)
    s._paste_px(rot, r[0], r[1])
