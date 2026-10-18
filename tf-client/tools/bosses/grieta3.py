"""La grieta 3.0 (TF Client 1.3.88) y la grieta madre (1.3.89): geometría facetada y su vista previa con los shaders.

    python3 tools/bosses/grieta3.py              escribe assets/tfclient/bosses/grieta3.json y grieta3_madre.json
    xvfb-run -a python3 tools/bosses/grieta3.py --preview carpeta [--t segundos] [--estado lista|despierta|dormida]
                                                [--madre]

La grieta madre (el dueño: «haz dos grietas, la normal y la grieta madre, una más imponente que la que tenemos, para
poner criaturas más poderosas»; va en el centro de la isla del Void si el staff la elige en /tf boss → VOID): la misma
idea en grande y en carmesí: estrado de 18 lados (7,4 de radio) con 24 piedras alrededor, 11 garras de 9 a 27 bloques,
cristal ×1,5, 24 esquirlas, desgarro de 30 bloques de alto y círculo de runas de 19,5. El color lo ponen los shaders
(material +4 en rift_obsidian y modo 0,25/1 en rift_fx) y RiftRenderer (la luz de cada cara).

El dueño (con capturas): «la textura de la grieta está mal trabajada, se ve horrible, falta pintura, bordes dentados,
caras planas, mal hecha; quiero algo más imponente que esto, más grande». La 3.0 ya no lleva texturas:

  base      un estrado de obsidiana roto: una losa de 14 lados con el borde biselado y un anillo de 16 piedras
            irregulares inclinadas alrededor (prismas de 5-7 lados).
  agujas    7 garras de obsidiana facetadas (prismas de 6 lados que se afinan y se curvan hacia fuera y luego hacia
            dentro, giradas), de 9 a 17 bloques, alrededor del desgarro.
  cristal   el cristal que se golpea: una bipirámide de 8 lados de 4,4 bloques que flota y gira.
  esquirlas 14 cristalitos que orbitan (cada uno con su radio, altura, velocidad, fase e inclinación: los anima Java).
  desgarro  no es geometría: un cuadro que mira a la cámara (RiftRenderer) dibujado por shaders/core/rift_fx.fsh: la
            rasgadura (borde dentado pero suave, sin píxeles), el vacío dentro (remolino, estrellas, el ojo del vórtice)
            y el halo; y el círculo de runas del suelo (el mismo shader, modo «suelo»).

Las caras son planas a propósito (facetas): la luz se calcula en Java por vértice (luz de arriba, la luz morada del
desgarro, que da en las caras que lo miran, y un brillo de borde según la cámara); shaders/core/rift_obsidian.fsh pone
encima las vetas que laten (ruido por píxel) y el brillo de las puntas. Así no hay texturas que se estiren.

Formato: {"parts": {nombre: {"pivot": [x,y,z], "v": [x,y,z, nx,ny,nz, u,v, mat, vena, ...] (12 floats... ver VERT)}},
"shards": [[radio, altura, velocidad, fase, inclinación, tamaño], ...], "tear": {...}}. mat: 0 roca, 1 cristal,
2 esquirla, 3 punta. Triángulos de 3 vértices seguidos.
"""
import json
import math
import os
import random
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
OUT = os.path.join(RES, 'bosses', 'grieta3.json')
VERT = 10  # x y z nx ny nz u v mat vena
RNG = random.Random(2026)

# El desgarro (lo dibuja el shader): alto, ancho máximo, centro y el cuadro del halo
TEAR = {'h': 20.0, 'w': 7.5, 'cy': 11.5, 'quad_w': 17.0, 'quad_h': 27.0}
RING = {'r': 13.5, 'y': 0.04}
OUT_MADRE = os.path.join(RES, 'bosses', 'grieta3_madre.json')

# Las dos grietas: la normal (1.3.88) y la madre (1.3.89). El desgarro de la madre es el mismo cuadro escalado ×1,5
# (el shader trabaja en coordenadas del cuadro).
VARIANTS = {
    'normal': {'base_sides': 14, 'base_r': 5.6, 'stones': 16, 'stone_r': (7.0, 9.6), 'stone_sz': (0.7, 1.4), 'stone_h': (0.5, 1.6),
               'spires': 7, 'spire_r': (6.8, 8.2), 'spire_tall': (12.0, 18.0), 'spire_short': (6.0, 10.0), 'spire_r0': (1.2, 1.9),
               'spire_lean': (2.0, 3.4), 'crystal_scale': 1.0, 'crystal_y': 6.2, 'shards': 14, 'shard_r': (2.6, 5.2),
               'shard_h': (2.0, 13.0), 'tear': TEAR, 'ring': RING},
    'madre': {'base_sides': 18, 'base_r': 7.4, 'stones': 24, 'stone_r': (9.2, 13.0), 'stone_sz': (0.9, 1.9), 'stone_h': (0.7, 2.4),
              'spires': 11, 'spire_r': (9.0, 10.6), 'spire_tall': (19.0, 27.0), 'spire_short': (9.0, 14.0), 'spire_r0': (1.6, 2.6),
              'spire_lean': (2.6, 4.6), 'crystal_scale': 1.5, 'crystal_y': 8.6, 'shards': 24, 'shard_r': (3.6, 7.6),
              'shard_h': (3.0, 19.0),
              'tear': {'h': 30.0, 'w': 11.25, 'cy': 16.5, 'quad_w': 25.5, 'quad_h': 40.5}, 'ring': {'r': 19.5, 'y': 0.04}},
}


class Mesh:
    def __init__(self):
        self.v = []

    def tri(self, a, b, c, mat, vein, uv=None):
        a, b, c = (np.asarray(p, float) for p in (a, b, c))
        n = np.cross(b - a, c - a)
        ln = np.linalg.norm(n)
        if ln < 1e-9:
            return
        n = n / ln
        if uv is None:
            # coordenadas de superficie: proyección sobre el plano de la cara (para el ruido de las vetas)
            t = np.cross(n, [0, 1, 0]) if abs(n[1]) < 0.95 else np.array([1.0, 0, 0])
            t = t / np.linalg.norm(t)
            bt = np.cross(n, t)
            uv = [(p @ t, p @ bt) for p in (a, b, c)]
        for p, (u, w) in zip((a, b, c), uv):
            self.v += [round(float(p[0]), 4), round(float(p[1]), 4), round(float(p[2]), 4),
                       round(float(n[0]), 4), round(float(n[1]), 4), round(float(n[2]), 4),
                       round(float(u), 4), round(float(w), 4), mat, round(vein, 3)]

    def quad(self, a, b, c, d, mat, vein):
        self.tri(a, b, c, mat, vein)
        self.tri(a, c, d, mat, vein)

    def ntris(self):
        return len(self.v) // (VERT * 3)


def ring_pts(cx, cy, cz, r, sides, jitter, rot=0.0, rng=RNG):
    pts = []
    for i in range(sides):
        a = rot + i * math.tau / sides + rng.uniform(-jitter, jitter) * 0.5 / sides * math.tau
        rr = r * (1 + rng.uniform(-jitter, jitter))
        pts.append((cx + math.cos(a) * rr, cy, cz + math.sin(a) * rr))
    return pts


def prism(m, bottom, top, mat, vein, cap_top=True, cap_bottom=False):
    n = len(bottom)
    for i in range(n):
        j = (i + 1) % n
        m.quad(bottom[i], bottom[j], top[j], top[i], mat, vein)
    if cap_top:
        c = np.mean(top, axis=0)
        for i in range(n):
            m.tri(c, top[(i + 1) % n], top[i], mat, vein * 0.6)
    if cap_bottom:
        c = np.mean(bottom, axis=0)
        for i in range(n):
            m.tri(c, bottom[i], bottom[(i + 1) % n], mat, vein * 0.3)


def base(m, V):
    """El estrado: losa biselada de 14 lados (18 la madre) y anillo de piedras."""
    sides = V['base_sides']
    rng = random.Random(11)
    rad = [V['base_r'] * (1 + rng.uniform(-0.07, 0.07)) for _ in range(sides)]
    angs = [i * math.tau / sides + rng.uniform(-0.08, 0.08) for i in range(sides)]
    ground = [(math.cos(a) * r, 0.0, math.sin(a) * r) for a, r in zip(angs, rad)]
    mid = [(math.cos(a) * r, 0.45, math.sin(a) * r) for a, r in zip(angs, rad)]
    top = [(math.cos(a) * (r - 0.35), 0.72, math.sin(a) * (r - 0.35)) for a, r in zip(angs, rad)]
    prism(m, ground, mid, 0, 0.5, cap_top=False)
    prism(m, mid, top, 0, 0.7, cap_top=False)
    # la cara de arriba, con un hundimiento en el centro (donde cae la luz del desgarro)
    inner = [(math.cos(a) * 2.0 * V['base_r'] / 5.6, 0.6, math.sin(a) * 2.0 * V['base_r'] / 5.6) for a in angs]
    for i in range(sides):
        j = (i + 1) % sides
        m.quad(top[i], inner[i], inner[j], top[j], 0, 0.9)
    c = (0, 0.55, 0)
    for i in range(sides):
        m.tri(c, inner[(i + 1) % sides], inner[i], 0, 1.0)
    # piedras del anillo
    ns = V['stones']
    for k in range(ns):
        a = k * math.tau / ns + rng.uniform(-0.12, 0.12)
        r = rng.uniform(*V['stone_r'])
        cx, cz = math.cos(a) * r, math.sin(a) * r
        sz = rng.uniform(*V['stone_sz'])
        h = rng.uniform(*V['stone_h'])
        n = rng.choice((5, 6, 7))
        bot = ring_pts(cx, -0.2, cz, sz, n, 0.25, rot=rng.uniform(0, 6.28), rng=rng)
        lean = (rng.uniform(-0.35, 0.35), rng.uniform(-0.35, 0.35))
        tp = [(x + lean[0] * h + (cx - x) * 0.35, h, z + lean[1] * h + (cz - z) * 0.35) for x, _, z in bot]
        prism(m, bot, tp, 0, 0.45)


def spire(m, x, z, height, r0, lean_out, twist, seg, rng):
    """Una garra: prisma de 6 lados que se afina y se curva (hacia fuera y luego hacia dentro)."""
    sides = 6
    out = np.array([x, 0, z]) / max(1e-6, math.hypot(x, z))
    rings = []
    for s in range(seg + 1):
        t = s / seg
        # curva: sale hacia fuera y la punta vuelve hacia el desgarro (garra)
        off = lean_out * (1.6 * t - 0.75 * t * t * t)
        cx, cz = x + out[0] * off, z + out[2] * off
        y = height * t
        r = r0 * (1 - t) ** 0.85 + 0.02
        rot = twist * t
        rings.append([(cx + math.cos(rot + i * math.tau / sides) * r * (1 + 0.12 * math.sin(i * 2.1 + s)), y - 0.25 * (s == 0),
                       cz + math.sin(rot + i * math.tau / sides) * r * (1 + 0.12 * math.cos(i * 1.7 + s))) for i in range(sides)])
    for s in range(seg):
        vein = 0.55 + 0.45 * (s / seg)
        mat = 3 if s >= seg - 1 else 0
        prism(m, rings[s], rings[s + 1], mat, vein, cap_top=(s == seg - 1))


def spires(m, V):
    rng = random.Random(23)
    n = V['spires']
    for k in range(n):
        a = k * math.tau / n + rng.uniform(-0.15, 0.15) + 0.3
        r = rng.uniform(*V['spire_r'])
        h = rng.uniform(*V['spire_tall']) if k % 2 == 0 else rng.uniform(*V['spire_short'])
        spire(m, math.cos(a) * r, math.sin(a) * r, h, rng.uniform(*V['spire_r0']), rng.uniform(*V['spire_lean']),
              rng.uniform(-0.9, 0.9), 8 if h < 20 else 10, rng)


def bipyramid(m, c, radius, up, down, sides, mat, vein, rot=0.0):
    cx, cy, cz = c
    eq = [(cx + math.cos(rot + i * math.tau / sides) * radius, cy, cz + math.sin(rot + i * math.tau / sides) * radius)
          for i in range(sides)]
    top = (cx, cy + up, cz)
    bot = (cx, cy - down, cz)
    for i in range(sides):
        j = (i + 1) % sides
        m.tri(top, eq[j], eq[i], mat, vein)
        m.tri(bot, eq[i], eq[j], mat, vein)


def crystal(m, V):
    """El cristal que se golpea (alrededor de su pivote, que anima Java)."""
    # cuerpo: bipirámide alargada con un anillo intermedio (más facetas)
    k = V['crystal_scale']
    sides = 8
    r = 1.7 * k
    eq1 = [(math.cos(i * math.tau / sides) * r, 0.5 * k, math.sin(i * math.tau / sides) * r) for i in range(sides)]
    eq2 = [(math.cos(i * math.tau / sides + 0.3) * r * 0.92, -0.5 * k, math.sin(i * math.tau / sides + 0.3) * r * 0.92) for i in range(sides)]
    top, bot = (0, 3.4 * k, 0), (0, -2.4 * k, 0)
    for i in range(sides):
        j = (i + 1) % sides
        m.tri(top, eq1[j], eq1[i], 1, 0.8)
        m.quad(eq1[i], eq1[j], eq2[j], eq2[i], 1, 0.9)
        m.tri(bot, eq2[i], eq2[j], 1, 0.8)
    # dos cristales pequeños a los lados
    bipyramid(m, (1.7 * k, -0.8 * k, 0.4 * k), 0.6 * k, 1.6 * k, 0.8 * k, 6, 1, 0.7, rot=0.4)
    bipyramid(m, (-1.5 * k, -0.5 * k, -0.5 * k), 0.5 * k, 1.3 * k, 0.7 * k, 6, 1, 0.7, rot=1.1)


def shard_mesh(m, V):
    bipyramid(m, (0, 0, 0), 0.32, 0.75, 0.45, 5, 2, 0.6)


def build(variant='normal'):
    V = VARIANTS[variant]
    parts = {}
    for name, fn, pivot in (('base', base, [0, 0, 0]), ('spires', spires, [0, 0, 0]), ('crystal', crystal, [0, V['crystal_y'], 0]),
                            ('shard', shard_mesh, [0, 0, 0])):
        m = Mesh()
        fn(m, V)
        parts[name] = {'pivot': pivot, 'v': m.v}
    rng = random.Random(41)
    shards = []
    for i in range(V['shards']):
        shards.append([round(rng.uniform(*V['shard_r']), 3), round(rng.uniform(*V['shard_h']), 3),
                       round(rng.uniform(0.25, 0.7) * rng.choice((-1, 1)), 3),
                       round(rng.uniform(0, math.tau), 3), round(rng.uniform(-0.4, 0.4), 3), round(rng.uniform(0.7, 1.5), 3)])
    return {'parts': parts, 'shards': shards, 'tear': V['tear'], 'ring': V['ring']}


# ------------------------------------------------------------------------------------- luz por vértice (= Java)

LIGHT = np.array([0.35, 0.85, 0.25]) / np.linalg.norm([0.35, 0.85, 0.25])
# colores (= RiftRenderer): normal (morado) y madre (carmesí)
COLORS = {
    'normal': {'rock': np.array([0.075, 0.042, 0.11]), 'crystal': np.array([0.22, 0.07, 0.42]), 'shard': np.array([0.30, 0.10, 0.55]),
               'tear': np.array([0.85, 0.28, 1.0]), 'rim': np.array([0.55, 0.30, 0.95])},
    'madre': {'rock': np.array([0.085, 0.030, 0.060]), 'crystal': np.array([0.42, 0.05, 0.20]), 'shard': np.array([0.55, 0.08, 0.28]),
              'tear': np.array([1.0, 0.22, 0.45]), 'rim': np.array([0.95, 0.28, 0.50])},
}


def light(pos, n, mat, cam, glow, variant='normal'):
    """La misma cuenta que RiftRenderer.light (Java): base × (ambiente + difusa) + luz del desgarro + borde."""
    C = COLORS[variant]
    base_c = C['rock'] if mat in (0, 3) else C['crystal'] if mat == 1 else C['shard']
    dif = max(0.0, float(n @ LIGHT))
    c = base_c * (0.5 + 0.9 * dif)
    tear = np.array([0.0, VARIANTS[variant]['tear']['cy'], 0.0])
    to = tear - pos
    d2 = float(to @ to)
    to = to / math.sqrt(d2 + 1e-9)
    k = max(0.0, float(n @ to)) * glow * 80.0 / (80.0 + d2)
    c = c + C['tear'] * k * (0.55 if mat in (0, 3) else 0.9)
    view = cam - pos
    view = view / (np.linalg.norm(view) + 1e-9)
    fr = (1 - abs(float(n @ view))) ** 3
    c = c + C['rim'] * fr * (0.35 if mat in (0, 3) else 0.7)
    return np.clip(c, 0, 1)


# ------------------------------------------------------------------------------------- vista previa (moderngl)

def preview(dest, t, state, variant='normal'):
    import moderngl
    from PIL import Image
    sys.path.insert(0, os.path.join(HERE, '..', 'void'))
    import cielo_preview as CP
    data = build(variant)
    V = VARIANTS[variant]
    madre = variant == 'madre'
    sc = 1.5 if madre else 1.0
    ctx = moderngl.create_standalone_context(require=330)
    w, h = 960, 540
    sh = os.path.join(RES, 'shaders', 'core')
    rock = ctx.program(vertex_shader=open(os.path.join(sh, 'rift_obsidian.vsh')).read(),
                       fragment_shader=open(os.path.join(sh, 'rift_obsidian.fsh')).read())
    fx = ctx.program(vertex_shader=open(os.path.join(sh, 'rift_fx.vsh')).read(),
                     fragment_shader=open(os.path.join(sh, 'rift_fx.fsh')).read())
    sky = ctx.program(vertex_shader=open(os.path.join(sh, 'void_sky.vsh')).read(),
                      fragment_shader=open(os.path.join(sh, 'void_sky.fsh')).read())
    # lightmap blanco (luz llena) en Sampler2
    lm = ctx.texture((16, 16), 3, bytes([255] * 16 * 16 * 3))
    lm.use(2)
    img = Image.open(os.path.join(RES, 'textures', 'environment', 'grieta_cielo.png')).convert('RGB')
    pano = ctx.texture(img.size, 3, img.tobytes())
    pano.filter = (moderngl.LINEAR, moderngl.LINEAR)
    pano.use(0)
    fbo = ctx.framebuffer(color_attachments=[ctx.texture((w, h), 4)], depth_attachment=ctx.depth_texture((w, h)))
    fbo.use()
    proj = CP.perspective(70, w / h, 0.05, 1000)
    glow = {'lista': 1.0, 'despierta': 1.7, 'dormida': 0.3}[state]
    open_k = {'lista': 1.0, 'despierta': 1.35, 'dormida': 0.06}[state]
    gt = (t * 20 % 24000) / 24000.0
    f, n = CP.hole_and_disk()
    for vname, cam, yaw_deg, pitch in (('frente', (0.5, 2.0, -26.0), 0.0, -14.0), ('lado', (22.0, 6.0, -12.0), 60.0, -6.0),
                                       ('cerca', (0.0, 1.8, -12.0), 0.0, -32.0), ('lejos', (12.0, 9.0, -46.0), 14.0, -6.0),
                                       ('pegado', (2.5, 7.0, -5.5), 20.0, -28.0)):
        cam = np.array(cam) * sc
        mv = CP.view(yaw_deg, pitch)
        ctx.clear(0, 0, 0, 1)
        ctx.enable(moderngl.DEPTH_TEST)
        # cielo (el del vacío) sin profundidad
        S = 100.0
        c = [(-S, -S, -S), (S, -S, -S), (S, S, -S), (-S, S, -S), (-S, -S, S), (S, -S, S), (S, S, S), (-S, S, S)]
        faces = [(0, 1, 2, 3), (5, 4, 7, 6), (4, 0, 3, 7), (1, 5, 6, 2), (3, 2, 6, 7), (4, 5, 1, 0)]
        tri = [c[k] for a, b, cc, d in faces for k in (a, b, cc, a, cc, d)]
        sv = ctx.buffer(np.array(tri, 'f4').tobytes())
        for nm, val in (('Sampler0', 0), ('VoidTime', t), ('HoleDir', tuple(f)), ('DiskNormal', tuple(n)), ('Quality', 0.6)):
            if nm in sky:
                sky[nm].value = val
        sky['ModelViewMat'].write(mv.T.astype('f4').tobytes())
        sky['ProjMat'].write(proj.T.astype('f4').tobytes())
        ctx.depth_mask = False
        ctx.vertex_array(sky, [(sv, '3f', 'Position')]).render()
        ctx.depth_mask = True
        # suelo (losa gris oscura) para ver la sombra de la escena
        # la grieta: posiciones relativas a la cámara (como hace Minecraft), luz por vértice en CPU
        verts = []
        def add_part(name, xf):
            v = np.array(data['parts'][name]['v'], float).reshape(-1, VERT)
            piv = np.array(data['parts'][name]['pivot'])
            for row in v:
                p = xf(row[0:3]) if xf else row[0:3] + piv
                nn = row[3:6]
                if xf:
                    nn = xf(row[3:6], True)
                col = light(p, nn, int(row[8]), cam, glow, variant)
                rel = p - cam
                verts.append((rel[0], rel[1], rel[2], col[0], col[1], col[2], 1.0, row[6], row[7], int(row[8]) + (4 if madre else 0),
                              int(min(255, row[9] * glow * 100)), 240, 240))
        add_part('base', None)
        add_part('spires', None)
        ang = t * 0.6
        def crys(p, normal=False):
            ca, sa = math.cos(ang), math.sin(ang)
            q = np.array([ca * p[0] - sa * p[2], p[1], sa * p[0] + ca * p[2]])
            if normal:
                return q
            s = 0.25 if state == 'dormida' else 1.0
            return q * s + np.array([0, V['crystal_y'] + 0.3 * math.sin(t * 1.3), 0])
        add_part('crystal', crys)
        if state != 'dormida':
            for sd in data['shards']:
                r, hh, sp, ph, tilt, sz = sd
                a = ph + t * sp
                cpos = np.array([math.cos(a) * r, hh + math.sin(a * 2 + ph) * tilt, math.sin(a) * r])
                def sh_xf(p, normal=False, a=a, cpos=cpos, sz=sz):
                    ca, sa = math.cos(a * 3), math.sin(a * 3)
                    q = np.array([ca * p[0] - sa * p[2], p[1], sa * p[0] + ca * p[2]])
                    return q if normal else q * sz + cpos
                add_part('shard', sh_xf)
        dt = np.dtype([('p', 'f4', 3), ('c', 'f4', 4), ('uv', 'f4', 2), ('m', 'i4', 2), ('l', 'i4', 2)])
        arr = np.array([(v[0:3], v[3:7], v[7:9], v[9:11], v[11:13]) for v in verts], dtype=dt)
        vb = ctx.buffer(arr.tobytes())
        rock['ModelViewMat'].write(mv.T.astype('f4').tobytes())
        rock['ProjMat'].write(proj.T.astype('f4').tobytes())
        if 'GameTime' in rock:
            rock['GameTime'].value = gt
        if 'Sampler2' in rock:
            rock['Sampler2'].value = 2
        vao = ctx.vertex_array(rock, [(vb, '3f 4f 2f 2i 2i', 'Position', 'Color', 'UV0', 'UV1', 'UV2')])
        ctx.disable(moderngl.CULL_FACE)
        vao.render()
        # desgarro (cuadro que mira a la cámara, cilíndrico) y el círculo del suelo
        ctx.enable(moderngl.BLEND)
        ctx.blend_func = moderngl.ONE, moderngl.ONE_MINUS_SRC_ALPHA
        ctx.depth_mask = False
        qv = []
        fwd = np.array([cam[0], 0, cam[2]])
        fwd = fwd / (np.linalg.norm(fwd) + 1e-9)
        side = np.array([fwd[2], 0, -fwd[0]])
        qw, qh, cy = V['tear']['quad_w'], V['tear']['quad_h'], V['tear']['cy']
        for (u, vv) in ((0, 0), (1, 0), (1, 1), (0, 0), (1, 1), (0, 1)):
            p = side * (u - 0.5) * qw + np.array([0, cy + (vv - 0.5) * qh, 0])
            rel = p - cam
            qv.append([rel[0], rel[1], rel[2], 0.37, 0.25 if madre else 0.0, min(1.0, open_k / 1.4), glow / 2, u, vv])
        R = V['ring']['r']
        for (u, vv) in ((0, 0), (1, 0), (1, 1), (0, 0), (1, 1), (0, 1)):
            p = np.array([(u - 0.5) * 2 * R, V['ring']['y'], (vv - 0.5) * 2 * R])
            rel = p - cam
            qv.append([rel[0], rel[1], rel[2], 0.37, 1.0 if madre else 0.75, 1.0, glow / 2, u, vv])
        qb = ctx.buffer(np.array(qv, 'f4').tobytes())
        fx['ModelViewMat'].write(mv.T.astype('f4').tobytes())
        fx['ProjMat'].write(proj.T.astype('f4').tobytes())
        if 'GameTime' in fx:
            fx['GameTime'].value = gt
        ctx.vertex_array(fx, [(qb, '3f 4f 2f', 'Position', 'Color', 'UV0')]).render()
        ctx.disable(moderngl.BLEND)
        ctx.depth_mask = True
        data_px = fbo.read(components=3)
        im = Image.frombytes('RGB', (w, h), data_px).transpose(Image.FLIP_TOP_BOTTOM)
        im.save(os.path.join(dest, f'grieta3_{variant}_{state}_{vname}.png'))


def main():
    for variant, out in (('normal', OUT), ('madre', OUT_MADRE)):
        data = build(variant)
        with open(out, 'w', encoding='utf-8') as fh:
            json.dump(data, fh, separators=(',', ':'))
        tris = {k: len(p['v']) // (VERT * 3) for k, p in data['parts'].items()}
        print(os.path.basename(out) + ':', tris, 'triángulos;', os.path.getsize(out) // 1024, 'KB')
    if '--preview' in sys.argv:
        dest = sys.argv[sys.argv.index('--preview') + 1]
        os.makedirs(dest, exist_ok=True)
        t = float(sys.argv[sys.argv.index('--t') + 1]) if '--t' in sys.argv else 12.0
        st = sys.argv[sys.argv.index('--estado') + 1] if '--estado' in sys.argv else 'lista'
        preview(dest, t, st, 'madre' if '--madre' in sys.argv else 'normal')


if __name__ == '__main__':
    main()
