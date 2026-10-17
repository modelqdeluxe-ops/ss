"""El modelo de la grieta de los jefes (el cristal que se golpea para abrir la incursión): se genera aquí, sin pack.

    python3 tools/bosses/rift_model.py [--preview carpeta]

Hace un .bbmodel (geometría, texturas pintadas con numpy/PIL y animaciones) y lo convierte con vfx_bb.py como los de
ModelEngine: assets/tfclient/bosses/models/grieta.cristal.json y sus texturas en textures/bosses/grieta/me/.

Desde la 1.3.86 (lo pidió el dueño: «una textura chingona e imponente, tipo oscuridad, morado intenso oscuro, un solo
color para todas»): ya no se tiñe del color del jefe; todo es de un mismo morado del vacío.

  base       plataforma escalonada de obsidiana casi negra con vetas moradas que brillan
  agujas     cuatro agujas de obsidiana alrededor, inclinadas, con vetas que brillan
  círculo    dos anillos de runas violetas en el suelo que giran despacio
  desgarro   la grieta en sí: una rasgadura vertical de ~7 bloques (dos planos cruzados) con el vacío dentro, animado
             (remolino morado con estrellas, 16 fotogramas) y el borde magenta que brilla
  cristal    obsidiana translúcida con vetas, un núcleo violeta que late dentro, dos cristales a los lados; ocho esquirlas
             giran alrededor; las grietas (ocultas) aparecen al despertar
Animaciones: idle (flota, gira, el desgarro respira), activate (tiembla, se agrieta y el desgarro se abre), dormant
(incursión y recarga: el cristal pequeño y apagado, el desgarro una rendija) y regrow (vuelve a crecer).
"""
import base64
import io
import json
import math
import os
import random
import sys
import uuid

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)
import vfx_bb as B  # noqa: E402

RES = os.path.normpath(os.path.join(TOOLS, '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
RNG = random.Random(7)
NP = np.random.default_rng(7)

# El morado del vacío (de lo más oscuro a lo que más brilla)
VOID = np.array([6, 2, 12], float)
DEEP = np.array([30, 7, 58], float)
PURPLE = np.array([82, 18, 158], float)
VIOLET = np.array([140, 48, 240], float)
GLOW = np.array([196, 104, 255], float)
HOT = np.array([246, 214, 255], float)


def uid():
    return str(uuid.UUID(int=RNG.getrandbits(128)))


# ---------------------------------------------------------------------------------------------- ruido

def noise2(w, h, scale, octaves=4, seed=0, tile=True):
    """Ruido de valor fractal (0..1), que se repite sin costuras si tile."""
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        cells = max(1, int(scale * 2 ** o))
        cw, ch = cells, max(1, int(cells * h / w))
        grid = rng.random((ch + 1, cw + 1))
        if tile:
            grid[-1, :] = grid[0, :]
            grid[:, -1] = grid[:, 0]
        ys = np.linspace(0, ch, h, endpoint=False)
        xs = np.linspace(0, cw, w, endpoint=False)
        y0 = ys.astype(int)
        x0 = xs.astype(int)
        fy = ys - y0
        fx = xs - x0
        fy = fy * fy * (3 - 2 * fy)
        fx = fx * fx * (3 - 2 * fx)
        a = grid[y0][:, x0]
        b = grid[y0][:, x0 + 1]
        c = grid[y0 + 1][:, x0]
        d = grid[y0 + 1][:, x0 + 1]
        top = a + (b - a) * fx[None, :]
        bot = c + (d - c) * fx[None, :]
        out += amp * (top + (bot - top) * fy[:, None])
        tot += amp
        amp *= 0.5
    return out / tot


def ramp(t, stops):
    """Colores a lo largo de t (0..1) entre las paradas [(t, color)]."""
    t = np.clip(t, 0, 1)
    out = np.zeros(t.shape + (3,))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        m = (t >= t0) & (t <= t1)
        k = ((t - t0) / max(1e-6, t1 - t0))[m][:, None]
        out[m] = c0 + (c1 - c0) * k
    return out


def to_img(rgb, alpha):
    a = np.clip(alpha, 0, 255)
    arr = np.dstack([np.clip(rgb, 0, 255), a]).astype(np.uint8)
    return Image.fromarray(arr, 'RGBA')


def veins_mask(n, seed, count=9, width=1):
    """Grietas ramificadas (una máscara 0..255) para las vetas que brillan."""
    img = Image.new('L', (n, n), 0)
    d = ImageDraw.Draw(img)
    r = random.Random(seed)
    for _ in range(count):
        x, y = r.uniform(0, n), r.uniform(0, n)
        ang = r.uniform(0, math.tau)
        pts = [(x, y)]
        for _ in range(r.randint(4, 8)):
            ang += r.uniform(-0.9, 0.9)
            step = r.uniform(3, 8)
            x, y = x + math.cos(ang) * step, y + math.sin(ang) * step
            pts.append((x, y))
            if r.random() < 0.3:
                bx, by, ba = x, y, ang + r.choice((-1, 1)) * r.uniform(0.6, 1.2)
                bpts = [(bx, by)]
                for _ in range(r.randint(2, 4)):
                    bx, by = bx + math.cos(ba) * r.uniform(2, 5), by + math.sin(ba) * r.uniform(2, 5)
                    bpts.append((bx, by))
                d.line(bpts, fill=200, width=width)
        # se repite al otro lado (sin costuras)
        for ox in (-n, 0, n):
            for oy in (-n, 0, n):
                d.line([(px + ox, py + oy) for px, py in pts], fill=255, width=width)
    return img


# ---------------------------------------------------------------------------------------------- texturas

def tex_obsidian(n=64, seed=1):
    """Obsidiana casi negra con un brillo morado oscuro y motas (sin luz propia)."""
    nz = noise2(n, n, 3, 4, seed)
    fine = noise2(n, n, 16, 2, seed + 1)
    t = nz * 0.75 + fine * 0.25
    rgb = ramp(t, [(0, VOID * 1.4), (0.5, DEEP * 0.55), (0.75, DEEP * 0.9), (1, PURPLE * 0.65)])
    speck = NP.random((n, n)) > 0.985
    rgb[speck] = PURPLE * 0.9
    # facetas: bandas diagonales un poco más claras (brillo del cristal volcánico)
    yy, xx = np.mgrid[0:n, 0:n]
    band = (np.sin((xx * 0.7 + yy * 0.3) * 0.35) > 0.93)
    rgb[band] = rgb[band] * 1.6 + 8
    return to_img(rgb, np.full((n, n), 255))


def tex_veins(n=64, seed=2, count=9):
    """Vetas que brillan (con luz propia): violeta con el centro casi blanco y un halo que se apaga."""
    core = np.array(veins_mask(n, seed, count, 1), float) / 255
    halo = np.array(veins_mask(n, seed, count, 1).filter(ImageFilter.GaussianBlur(1.6)), float) / 255
    halo = np.clip(halo * 2.2, 0, 1)
    rgb = ramp(np.clip(core * 0.85 + halo * 0.45, 0, 1), [(0, PURPLE), (0.45, VIOLET), (0.8, GLOW), (1, HOT)])
    alpha = np.clip(core * 255 + halo * 170, 0, 255)
    return to_img(rgb, alpha)


def tex_crystal(n=64, seed=3):
    """Cristal de obsidiana: oscuro y translúcido (se ve el núcleo dentro), facetas y aristas violetas."""
    yy, xx = np.mgrid[0:n, 0:n]
    band = np.sin((xx * 0.9 + yy * 0.35) * 0.42) * 0.5 + 0.5
    nz = noise2(n, n, 4, 3, seed)
    t = band * 0.45 + nz * 0.55
    rgb = ramp(t, [(0, VOID * 2), (0.55, DEEP), (0.85, PURPLE * 0.9), (1, VIOLET * 0.8)])
    alpha = 165 + 45 * t
    edge = (xx < 2) | (xx > n - 3)
    rgb[edge] = VIOLET * 1.05
    alpha[edge] = 245
    img = to_img(rgb, alpha)
    d = ImageDraw.Draw(img)
    for _ in range(5):
        x, y = RNG.randint(4, n - 5), RNG.randint(4, n - 5)
        d.line([(x - 2, y), (x + 2, y)], fill=(236, 200, 255, 255))
        d.line([(x, y - 2), (x, y + 2)], fill=(236, 200, 255, 255))
    return img


def tex_core(n=32):
    """Núcleo (con luz propia): casi blanco en el centro, violeta intenso hacia fuera."""
    yy, xx = np.mgrid[0:n, 0:n]
    c = (n - 1) / 2
    r = np.hypot(xx - c, (yy - c) * 0.55) / c
    t = np.clip(1 - r * 0.85, 0, 1)
    rgb = ramp(t, [(0, PURPLE), (0.4, VIOLET), (0.75, GLOW), (1, HOT)])
    return to_img(rgb, np.full((n, n), 255))


def tex_circle(n=256):
    """Los anillos de runas del suelo (luz propia), con simetría de 4 (giran sin saltos)."""
    big = n * 4
    img = Image.new('L', (big, big), 0)
    d = ImageDraw.Draw(img)
    c = big / 2
    for r, a, wid in ((0.475, 255, 12), (0.455, 150, 4), (0.39, 210, 7), (0.30, 170, 6), (0.18, 120, 4)):
        rr = big * r
        d.ellipse([c - rr, c - rr, c + rr, c + rr], outline=a, width=wid)
    for q in range(4):
        for i in range(9):
            ang = math.pi / 2 * q + (i + 0.5) * (math.pi / 2) / 9
            rr = big * 0.425
            x, y = c + math.cos(ang) * rr, c + math.sin(ang) * rr
            s = big * 0.018
            k = i % 3
            if k == 0:
                pts = [(x - s, y - s), (x + s, y - s), (x, y + s), (x - s, y - s)]
            elif k == 1:
                pts = [(x - s, y), (x, y - s), (x + s, y), (x, y + s), (x - s, y)]
            else:
                pts = [(x, y - s), (x, y + s), (x - s * 0.8, y), (x + s * 0.8, y)]
            d.line(pts, fill=235, width=6)
    # estrella de ocho puntas dentro
    for q in range(8):
        ang = math.pi / 4 * q
        p1 = (c + math.cos(ang) * big * 0.30, c + math.sin(ang) * big * 0.30)
        p2 = (c + math.cos(ang + math.pi * 3 / 4) * big * 0.30, c + math.sin(ang + math.pi * 3 / 4) * big * 0.30)
        d.line([p1, p2], fill=150, width=5)
    sharp = np.array(img.resize((n, n), Image.LANCZOS), float) / 255
    glow = np.array(img.filter(ImageFilter.GaussianBlur(14)).resize((n, n), Image.LANCZOS), float) / 255
    glow = np.clip(glow * 2.0, 0, 1)
    t = np.clip(sharp * 0.9 + glow * 0.35, 0, 1)
    rgb = ramp(t, [(0, PURPLE), (0.35, VIOLET), (0.75, GLOW), (1, HOT)])
    alpha = np.clip(sharp * 255 + glow * 150, 0, 255)
    return to_img(rgb, alpha)


def tex_cracks(n=64):
    """Grietas casi blancas que aparecen al despertar (luz propia)."""
    core = np.array(veins_mask(n, 11, 8, 1), float) / 255
    halo = np.clip(np.array(veins_mask(n, 11, 8, 2).filter(ImageFilter.GaussianBlur(1.2)), float) / 255 * 1.8, 0, 1)
    rgb = ramp(np.clip(core + halo * 0.4, 0, 1), [(0, VIOLET), (0.6, GLOW), (1, HOT)])
    return to_img(rgb, np.clip(core * 255 + halo * 150, 0, 255))


TEAR_W, TEAR_H, TEAR_FRAMES = 48, 144, 16


def tex_tear():
    """El desgarro (luz propia, animado): una rasgadura vertical con el borde magenta y el vacío dentro, un remolino
    morado que gira y estrellas que titilan. Una tira de TEAR_FRAMES fotogramas de TEAR_W × TEAR_H."""
    w, h = TEAR_W, TEAR_H
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    v = (yy + 0.5) / h  # 0 arriba, 1 abajo
    u = (xx + 0.5) / w - 0.5  # -0.5..0.5
    # Perfil de la rasgadura: ancho que crece hacia el medio, con unos pocos dientes grandes que no se mueven (con
    # dientes fila a fila el borde salía «peludo»), un poco torcida
    rows = np.linspace(0, 1, h)
    jag = np.interp(rows, np.linspace(0, 1, 9), [0.5, 0.9, 0.35, 1.0, 0.55, 0.95, 0.3, 0.8, 0.5])[:, None]
    teeth = np.zeros(h)
    for tv, tw_, ta in ((0.22, 0.035, 0.22), (0.41, 0.03, -0.18), (0.63, 0.04, 0.25), (0.8, 0.03, -0.2)):
        teeth += ta * np.clip(1 - np.abs(rows - tv) / tw_, 0, 1)
    half = 0.44 * np.sin(np.pi * v) ** 0.65 * (0.72 + 0.32 * jag + teeth[:, None]) + 0.02
    center = np.interp(rows, np.linspace(0, 1, 6), [0, 0.05, -0.04, 0.06, -0.03, 0])[:, None] * np.sin(np.pi * v)
    d = np.abs(u - center) / np.maximum(half, 1e-3)  # <1 dentro
    stars = NP.random((h, w))
    frames = []
    for f in range(TEAR_FRAMES):
        ph = f / TEAR_FRAMES * math.tau
        # remolino: el ruido se mueve en espiral alrededor del centro (vuelve al empezar: bucle sin saltos)
        swirl = (np.sin((v * 7.0 - ph) + np.sin(u * 9 + ph) * 1.2 + d * 3.0) * 0.5 + 0.5)
        swirl2 = (np.sin((v * 13.0 + ph * 2) - u * 6 + np.cos(v * 5 - ph) * 1.5) * 0.5 + 0.5)
        inner = np.clip(swirl * 0.6 + swirl2 * 0.4, 0, 1) ** 1.6
        rgb = ramp(inner * (1 - d * 0.55), [(0, VOID), (0.35, DEEP), (0.7, PURPLE), (1, VIOLET)])
        # estrellas que titilan
        tw = (np.sin(stars * 50 + ph * 2) * 0.5 + 0.5)
        st = (stars > 0.965) & (d < 0.85)
        rgb[st] = rgb[st] * 0.3 + HOT * (0.5 + 0.5 * tw[st])[:, None]
        # el borde: violeta que brilla con el centro casi blanco, y un halo fuera
        rim = np.exp(-((d - 1.0) / 0.11) ** 2)
        flick = 0.85 + 0.15 * np.sin(v * 9 + ph)
        rgb = rgb * (1 - rim[..., None]) + ramp(rim * flick, [(0, PURPLE), (0.5, GLOW), (1, HOT)]) * rim[..., None]
        outside = np.clip(1 - (d - 1.0) / 1.1, 0, 1) ** 2.2 * (d > 1)
        alpha = np.where(d <= 1, 235 + 20 * rim, 0) + outside * 170
        rgb = np.where((d > 1)[..., None], ramp(outside, [(0, PURPLE), (1, GLOW)]), rgb)
        frames.append(np.dstack([np.clip(rgb, 0, 255), np.clip(alpha, 0, 255)]))
    arr = np.concatenate(frames, axis=0).astype(np.uint8)
    return Image.fromarray(arr, 'RGBA')


def data_url(img):
    buf = io.BytesIO()
    img.save(buf, 'PNG')
    return 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()


# ---------------------------------------------------------------------------------------------- geometría

TEX = []  # (nombre, imagen, ancho y alto de un fotograma en uv, tiempo por fotograma)


def texture(name, img, uv=None, frame_time=1):
    TEX.append((name, img, uv or img.size, frame_time))
    return len(TEX) - 1


def cube(name, frm, to, tex, faces=None, rot=None, origin=None, uv=None):
    """Un cubo con la textura entera (un fotograma) en cada cara (o solo en «faces»)."""
    tw, th = TEX[tex][2]
    all_faces = faces or ['north', 'south', 'east', 'west', 'up', 'down']
    fdict = {}
    for f in ['north', 'south', 'east', 'west', 'up', 'down']:
        if f in all_faces:
            fdict[f] = {'uv': list(uv or [0, 0, tw, th]), 'texture': tex}
    el = {'name': name, 'type': 'cube', 'uuid': uid(), 'from': list(frm), 'to': list(to), 'faces': fdict,
          'origin': list(origin or [(frm[i] + to[i]) / 2 for i in range(3)])}
    if rot:
        el['rotation'] = list(rot)
    return el


def build():
    obsidian = texture('obsidian', tex_obsidian())
    veins = texture('veins_e', tex_veins())
    veins2 = texture('veins_dense_e', tex_veins(64, 5, 14))
    crystal = texture('crystal', tex_crystal())
    core = texture('core_e', tex_core())
    circle = texture('circle_e', tex_circle())
    cracks = texture('cracks_e', tex_cracks())
    tear = texture('tear_e', tex_tear(), uv=(TEAR_W, TEAR_H), frame_time=2)

    elements, outliner, bones = [], [], {}

    def bone(name, origin, parent=None, rotation=None):
        b = {'name': name, 'origin': list(origin), 'uuid': uid(), 'children': [], 'rotation': list(rotation or [0, 0, 0])}
        bones[name] = b
        (bones[parent]['children'] if parent else outliner).append(b)
        return b

    def add(bname, el):
        elements.append(el)
        bones[bname]['children'].append(el['uuid'])

    def veined(bname, name, frm, to, rot=None, origin=None, vt=veins, top=False):
        """Un bloque de obsidiana y, encima, sus vetas que brillan (una piel un poco más grande)."""
        add(bname, cube(name, frm, to, obsidian, rot=rot, origin=origin))
        e = 0.12
        faces = ['north', 'south', 'east', 'west'] + (['up'] if top else [])
        add(bname, cube(name + '_v', [frm[0] - e, frm[1] + e, frm[2] - e], [to[0] + e, to[1] + (e if top else -e), to[2] + e], vt,
                        faces=faces, rot=rot, origin=origin))

    # La base: tres escalones de obsidiana con vetas
    bone('base', [0, 0, 0])
    veined('base', 'slab_a', [-20, 0, -20], [20, 4, 20])
    veined('base', 'slab_b', [-15, 4, -15], [15, 8, 15], rot=[0, 45, 0], origin=[0, 4, 0], vt=veins2)
    veined('base', 'slab_c', [-10, 8, -10], [10, 11, 10], vt=veins2, top=True)

    # Las agujas de obsidiana (cuatro, inclinadas hacia fuera)
    bone('spires', [0, 0, 0], 'base')
    for i, (x, z) in enumerate(((27, 27), (-27, 27), (27, -27), (-27, -27))):
        hgt = (70, 82, 62, 76)[i]
        rot = [8 * (1 if z > 0 else -1), RNG.randint(0, 40), -8 * (1 if x > 0 else -1)]
        org = [x, 0, z]
        steps = ((4.5, 0, 0.45), (3.4, 0.45, 0.72), (2.2, 0.72, 0.9), (1.0, 0.9, 1.0))
        for k, (r, y0, y1) in enumerate(steps):
            veined('spires', f'spire_{i}_{k}', [x - r, hgt * y0, z - r], [x + r, hgt * y1, z + r], rot=rot, origin=org,
                   vt=veins if k % 2 else veins2)

    # Los anillos de runas del suelo
    bone('circle', [0, 0, 0])
    add('circle', cube('ring_out', [-60, 0.15, -60], [60, 0.15, 60], circle, faces=['up']))
    bone('circle2', [0, 0, 0])
    add('circle2', cube('ring_in', [-30, 0.3, -30], [30, 0.3, 30], circle, faces=['up']))

    # El desgarro: dos planos cruzados con el vacío animado
    bone('tear', [0, 74, 0])
    add('tear', cube('tear_a', [-22, 14, 0], [22, 134, 0], tear, faces=['north', 'south']))
    add('tear', cube('tear_b', [-22, 14, 0], [22, 134, 0], tear, faces=['north', 'south'], rot=[0, 90, 0], origin=[0, 74, 0]))

    # El cristal
    bone('crystal', [0, 62, 0])
    c45 = dict(rot=[0, 45, 0], origin=[0, 62, 0])
    for name, frm, to in (('body', [-9, 36, -9], [9, 82, 9]), ('top_1', [-6.5, 82, -6.5], [6.5, 92, 6.5]),
                          ('top_2', [-4, 92, -4], [4, 100, 4]), ('top_3', [-1.5, 100, -1.5], [1.5, 107, 1.5]),
                          ('bot_1', [-6.5, 28, -6.5], [6.5, 36, 6.5]), ('bot_2', [-4, 21, -4], [4, 28, 4]),
                          ('bot_3', [-1.5, 16, -1.5], [1.5, 21, 1.5])):
        add('crystal', cube(name, frm, to, crystal, **c45))
    add('crystal', cube('body_v', [-9.15, 36.15, -9.15], [9.15, 81.85, 9.15], veins, faces=['north', 'south', 'east', 'west'], **c45))
    add('crystal', cube('side_a', [7, 40, -3.5], [14, 64, 3.5], crystal, rot=[0, 0, -24], origin=[10, 40, 0]))
    add('crystal', cube('side_a2', [8.5, 64, -2], [12.5, 71, 2], crystal, rot=[0, 0, -24], origin=[10, 40, 0]))
    add('crystal', cube('side_b', [-14, 42, -3.5], [-7, 62, 3.5], crystal, rot=[0, 0, 26], origin=[-10, 42, 0]))
    add('crystal', cube('side_b2', [-12.5, 62, -2], [-8.5, 68, 2], crystal, rot=[0, 0, 26], origin=[-10, 42, 0]))
    bone('core', [0, 62, 0], 'crystal')
    add('core', cube('core', [-4, 40, -4], [4, 80, 4], core, **c45))
    bone('cracks', [0, 62, 0], 'crystal')
    add('cracks', cube('cracks', [-9.3, 35.7, -9.3], [9.3, 82.3, 9.3], cracks, **c45))

    # Las esquirlas que orbitan
    bone('shards', [0, 62, 0])
    for i in range(8):
        a = math.radians(i * 45)
        x, z = math.cos(a) * 34, math.sin(a) * 34
        y = 60 + (9 if i % 2 else -7)
        bone(f'shard_{i}', [x, y, z], 'shards', rotation=[RNG.randint(-35, 35), 0, RNG.randint(-35, 35)])
        add(f'shard_{i}', cube(f'shard_{i}', [x - 2.2, y - 6, z - 2.2], [x + 2.2, y + 6, z + 2.2], obsidian, rot=[0, 45, 0],
                               origin=[x, y, z]))
        add(f'shard_{i}', cube(f'shard_{i}_v', [x - 2.32, y - 5.9, z - 2.32], [x + 2.32, y + 5.9, z + 2.32], veins2,
                               faces=['north', 'south', 'east', 'west'], rot=[0, 45, 0], origin=[x, y, z]))

    # ------------------------------------------------------------------ animaciones
    def kf(channel, time, xyz, interp='linear'):
        return {'channel': channel, 'data_points': [{'x': xyz[0], 'y': xyz[1], 'z': xyz[2]}], 'uuid': uid(), 'time': time,
                'interpolation': interp}

    def anim(name, length, loop, tracks, override=False):
        animators = {}
        for bname, kfs in tracks.items():
            animators[bones[bname]['uuid']] = {'name': bname, 'type': 'bone', 'keyframes': kfs}
        return {'uuid': uid(), 'name': name, 'loop': loop, 'override': override, 'length': length, 'animators': animators}

    shard_spin = {}
    for i in range(8):
        shard_spin[f'shard_{i}'] = [kf('rotation', 0, [0, 0, 0]), kf('rotation', 8, [0, 720 * (1 if i % 2 else -1), 0])]
    idle = anim('idle', 8, 'loop', dict({
        'crystal': [kf('position', 0, [0, 0, 0], 'catmullrom'), kf('position', 2, [0, 3, 0], 'catmullrom'),
                    kf('position', 4, [0, 0, 0], 'catmullrom'), kf('position', 6, [0, 3, 0], 'catmullrom'),
                    kf('position', 8, [0, 0, 0], 'catmullrom'),
                    kf('rotation', 0, [0, 0, 0]), kf('rotation', 8, [0, 360, 0])],
        'core': [kf('scale', 0, [1, 1, 1], 'catmullrom'), kf('scale', 1, [1.25, 1.05, 1.25], 'catmullrom'),
                 kf('scale', 2, [1, 1, 1], 'catmullrom'), kf('scale', 3, [1.25, 1.05, 1.25], 'catmullrom'),
                 kf('scale', 4, [1, 1, 1], 'catmullrom'), kf('scale', 5, [1.25, 1.05, 1.25], 'catmullrom'),
                 kf('scale', 6, [1, 1, 1], 'catmullrom'), kf('scale', 7, [1.25, 1.05, 1.25], 'catmullrom'),
                 kf('scale', 8, [1, 1, 1], 'catmullrom')],
        'tear': [kf('scale', 0, [1, 1, 1], 'catmullrom'), kf('scale', 2, [1.12, 1.03, 1.12], 'catmullrom'),
                 kf('scale', 4, [0.94, 1, 0.94], 'catmullrom'), kf('scale', 6, [1.1, 1.02, 1.1], 'catmullrom'),
                 kf('scale', 8, [1, 1, 1], 'catmullrom'),
                 kf('rotation', 0, [0, 0, 0]), kf('rotation', 8, [0, 45, 0])],
        'shards': [kf('rotation', 0, [0, 0, 0]), kf('rotation', 8, [0, -360, 0]),
                   kf('position', 0, [0, 0, 0], 'catmullrom'), kf('position', 4, [0, 4, 0], 'catmullrom'),
                   kf('position', 8, [0, 0, 0], 'catmullrom')],
        'circle': [kf('rotation', 0, [0, 0, 0]), kf('rotation', 8, [0, 90, 0])],
        'circle2': [kf('rotation', 0, [0, 0, 0]), kf('rotation', 8, [0, -90, 0])],
        'cracks': [kf('scale', 0, [0, 0, 0])],
    }, **shard_spin))
    shake = []
    for k in range(36):
        t = round(k * 0.1, 2)
        amp = min(1.0, t / 2.6) * 4
        shake.append(kf('rotation', t, [amp * (1 if k % 2 else -1), 360 * t / 3.5 * 3, amp * (1 if k % 3 else -1)]))
    activate = anim('activate', 3.5, 'hold', {
        'crystal': shake + [kf('position', 0, [0, 0, 0]), kf('position', 3.5, [0, 8, 0], 'catmullrom'),
                            kf('scale', 0, [1, 1, 1]), kf('scale', 2.8, [1.12, 1.12, 1.12], 'catmullrom'),
                            kf('scale', 3.3, [1.3, 1.3, 1.3])],
        'core': [kf('scale', 0, [1, 1, 1]), kf('scale', 3.3, [1.7, 1.25, 1.7], 'catmullrom')],
        'cracks': [kf('scale', 0, [0, 0, 0]), kf('scale', 0.35, [0, 0, 0], 'step'), kf('scale', 0.4, [1, 1, 1])],
        'tear': [kf('scale', 0, [1, 1, 1]), kf('scale', 2.6, [1.5, 1.08, 1.5], 'catmullrom'),
                 kf('scale', 3.3, [2.1, 1.18, 2.1], 'catmullrom'),
                 kf('rotation', 0, [0, 0, 0]), kf('rotation', 3.5, [0, 180, 0])],
        'shards': [kf('rotation', 0, [0, 0, 0]), kf('rotation', 3.5, [0, -1080, 0]),
                   kf('scale', 0, [1, 1, 1]), kf('scale', 3.2, [1.5, 1.5, 1.5], 'catmullrom')],
    }, override=True)
    dormant = anim('dormant', 0.1, 'hold', {
        'crystal': [kf('scale', 0, [0.16, 0.16, 0.16]), kf('position', 0, [0, -30, 0])],
        'shards': [kf('scale', 0, [0, 0, 0])],
        'cracks': [kf('scale', 0, [0, 0, 0])],
        'tear': [kf('scale', 0, [0.1, 0.9, 0.1])],
    }, override=True)
    regrow = anim('regrow', 2.2, 'once', {
        'crystal': [kf('scale', 0, [0.16, 0.16, 0.16]), kf('scale', 2.0, [1, 1, 1], 'catmullrom'),
                    kf('position', 0, [0, -30, 0]), kf('position', 2.0, [0, 0, 0], 'catmullrom')],
        'shards': [kf('scale', 0, [0, 0, 0]), kf('scale', 1.2, [0, 0, 0]), kf('scale', 2.2, [1, 1, 1], 'catmullrom')],
        'cracks': [kf('scale', 0, [0, 0, 0])],
        'tear': [kf('scale', 0, [0.1, 0.9, 0.1]), kf('scale', 1.8, [1, 1, 1], 'catmullrom')],
    }, override=True)

    textures = []
    for name, img, (uw, uh), ft in TEX:
        t = {'name': name + '.png', 'uuid': uid(), 'source': data_url(img), 'width': img.width,
             'height': img.height, 'uv_width': uw, 'uv_height': uh}
        if ft != 1:
            t['frame_time'] = ft
        textures.append(t)
    return {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False}, 'name': 'cristal',
            'resolution': {'width': 64, 'height': 64}, 'elements': elements, 'outliner': outliner,
            'textures': textures, 'animations': [idle, activate, dormant, regrow]}


def main():
    import tempfile
    bb = build()
    path = os.path.join(tempfile.gettempdir(), 'cristal.bbmodel')
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(bb, f)
    tex_dir = os.path.join(RES, 'textures', 'bosses', 'grieta', 'me')
    # Las texturas de antes se van (cada una se guarda con el nombre de su huella)
    if os.path.isdir(tex_dir):
        for fn in os.listdir(tex_dir):
            if fn.endswith('.png'):
                os.remove(os.path.join(tex_dir, fn))
    store = B.TextureStore(tex_dir, 'tfclient:textures/bosses/grieta/me/')
    problems = []
    m = B.convert(path, store, problems, boss=True)
    if problems:
        print('\n'.join(problems))
    out = os.path.join(RES, 'bosses', 'models', 'grieta.cristal.json')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(m, f, separators=(',', ':'))
    print('grieta.cristal:', len(m['bones']), 'huesos,', sum(len(b['quads']) for b in m['bones']), 'caras,',
          sum(1 for t in m['tex'] if t['e']), 'texturas con luz propia,', [t['frames'] for t in m['tex']])
    if '--preview' in sys.argv:
        dest = sys.argv[sys.argv.index('--preview') + 1]
        os.makedirs(dest, exist_ok=True)
        import vfx_render as R
        tex = R.load_textures(m, tex_dir)
        for a, t in (('idle', 0), ('idle', 3), ('activate', 1.5), ('activate', 3.3), ('dormant', 0), ('regrow', 1.1)):
            img = R.render(m, tex, a, t, size=420, yaw=-30, pitch=-14)
            bg = Image.new('RGBA', img.size, (24, 26, 32, 255))
            bg.alpha_composite(img)
            bg.convert('RGB').save(os.path.join(dest, f'grieta_{a}_{t}.png'))


if __name__ == '__main__':
    main()
