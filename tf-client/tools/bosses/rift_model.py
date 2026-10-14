"""El modelo de la grieta de los jefes (el cristal que se golpea para abrir la incursión): se genera aquí, sin pack.

    python3 tools/bosses/rift_model.py [--preview carpeta]

Hace un .bbmodel (geometría, texturas pintadas con PIL y animaciones) y lo convierte con vfx_bb.py como los de
ModelEngine: assets/tfclient/bosses/models/grieta.cristal.json y sus texturas en textures/bosses/grieta/me/.

  pedestal   tres losas de basalto (la del medio girada) con runas que brillan en los lados y cuatro piedras flotando
  círculo    dos anillos de runas en el suelo (se tiñen del color del jefe y giran despacio)
  cristal    una gema alargada de puntas escalonadas, translúcida, con un núcleo que brilla dentro y dos cristales a
             los lados; seis esquirlas giran a su alrededor; las grietas (ocultas) aparecen al despertar
Animaciones: idle (flota y gira), activate (tiembla, crece y se agrieta), dormant (se queda pequeñito y apagado:
incursión y recarga) y regrow (vuelve a crecer). Las texturas marcadas «tint» se tiñen del color del jefe.
"""
import base64
import io
import json
import math
import os
import random
import sys
import uuid

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)
import vfx_bb as B  # noqa: E402

RES = os.path.normpath(os.path.join(TOOLS, '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
RNG = random.Random(7)


def uid():
    return str(uuid.UUID(int=RNG.getrandbits(128)))


# ---------------------------------------------------------------------------------------------- texturas

def tex_stone(n=64):
    """Basalto oscuro con vetas y bordes gastados."""
    img = Image.new('RGBA', (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            v = 34 + RNG.randint(-6, 6) + (6 if (x // 8 + y // 8) % 2 else 0)
            if y % 16 == 0 or x % 32 == (y // 16) * 11 % 32:
                v -= 12  # juntas de las losas
            px[x, y] = (v, v + 2, v + 10, 255)
    return img


def tex_runes(n=64):
    """Runas que brillan (blanco con alfa: se tiñen del color del jefe)."""
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i in range(6):
        x0 = 3 + i * 10
        y0 = 22
        # glifos de trazos rectos, como runas talladas
        pts = [(x0, y0), (x0 + 6, y0), (x0 + 3, y0 + 4), (x0 + 6, y0 + 9), (x0, y0 + 9)]
        RNG.shuffle(pts)
        d.line(pts[:3], fill=(255, 255, 255, 235), width=1)
        d.line([(x0 + 3, y0 - 2), (x0 + 3, y0 + 11)], fill=(255, 255, 255, 200), width=1)
    d.line([(0, 18), (n, 18)], fill=(255, 255, 255, 120))
    d.line([(0, 36), (n, 36)], fill=(255, 255, 255, 120))
    return img


def tex_crystal(n=64):
    """Caras de cristal: bandas claras y oscuras en diagonal, translúcido (se tiñe del color del jefe)."""
    img = Image.new('RGBA', (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            band = math.sin((x * 0.9 + y * 0.35) * 0.45) * 0.5 + 0.5
            edge = 1.0 if x < 2 or x > n - 3 else 0.0
            v = int(150 + 80 * band + 25 * edge + RNG.randint(-8, 8))
            v = max(0, min(255, v))
            px[x, y] = (v, v, v, 200 + int(40 * edge))
    # destellos
    d = ImageDraw.Draw(img)
    for _ in range(6):
        x, y = RNG.randint(4, n - 5), RNG.randint(4, n - 5)
        d.line([(x - 2, y), (x + 2, y)], fill=(255, 255, 255, 255))
        d.line([(x, y - 2), (x, y + 2)], fill=(255, 255, 255, 255))
    return img


def tex_core(n=32):
    """Núcleo que brilla: centro blanco que se apaga hacia los bordes."""
    img = Image.new('RGBA', (n, n))
    px = img.load()
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            r = math.hypot(x - c, (y - c) * 0.6) / c
            v = int(255 * max(0.35, 1 - r * 0.7))
            px[x, y] = (v, v, v, 255)
    return img


def tex_circle(n=128):
    """Círculo de runas del suelo, con simetría de 4 (gira sin saltos)."""
    big = n * 4
    img = Image.new('RGBA', (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = big / 2
    w = (255, 255, 255)
    for r, a, wid in ((0.47, 230, 10), (0.40, 170, 6), (0.30, 140, 5)):
        rr = big * r
        d.ellipse([c - rr, c - rr, c + rr, c + rr], outline=w + (a,), width=wid)
    # runas en el anillo de fuera (cuatro cuartos iguales)
    for q in range(4):
        for i in range(7):
            ang = math.pi / 2 * q + (i + 0.5) * (math.pi / 2) / 7
            rr = big * 0.435
            x, y = c + math.cos(ang) * rr, c + math.sin(ang) * rr
            s = big * 0.02
            pts = [(x - s, y - s), (x + s, y - s), (x, y + s)] if i % 2 else [(x - s, y), (x, y - s), (x + s, y), (x, y + s)]
            d.line(pts + [pts[0]], fill=w + (220,), width=5)
    # estrella de cuatro puntas dentro
    for q in range(4):
        ang = math.pi / 2 * q + math.pi / 4
        p1 = (c + math.cos(ang) * big * 0.30, c + math.sin(ang) * big * 0.30)
        p2 = (c + math.cos(ang + math.pi / 2) * big * 0.30, c + math.sin(ang + math.pi / 2) * big * 0.30)
        d.line([p1, p2], fill=w + (150,), width=5)
    img = img.filter(ImageFilter.GaussianBlur(1.2)).resize((n, n), Image.LANCZOS)
    return img


def tex_cracks(n=64):
    """Grietas blancas (aparecen al despertar)."""
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for _ in range(7):
        x, y = RNG.randint(0, n), RNG.randint(0, n)
        pts = [(x, y)]
        for _ in range(5):
            x += RNG.randint(-9, 9)
            y += RNG.randint(-9, 9)
            pts.append((x, y))
        d.line(pts, fill=(255, 255, 255, 255), width=1)
    return img


def data_url(img):
    buf = io.BytesIO()
    img.save(buf, 'PNG')
    return 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()


# ---------------------------------------------------------------------------------------------- geometría

TEX = []  # (nombre, imagen)


def texture(name, img):
    TEX.append((name, img))
    return len(TEX) - 1


def cube(name, frm, to, tex, faces=None, rot=None, origin=None, uv=None):
    """Un cubo con la textura entera en cada cara (o solo en «faces»)."""
    tw, th = TEX[tex][1].size
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
    stone = texture('stone', tex_stone())
    runes = texture('runes_tint_e', tex_runes())
    crystal = texture('crystal_tint_e', tex_crystal())
    core = texture('core_tint_e', tex_core())
    circle = texture('circle_tint_e', tex_circle())
    cracks = texture('cracks_e', tex_cracks())

    elements, outliner, bones = [], [], {}

    def bone(name, origin, parent=None, rotation=None):
        b = {'name': name, 'origin': list(origin), 'uuid': uid(), 'children': [], 'rotation': list(rotation or [0, 0, 0])}
        bones[name] = b
        (bones[parent]['children'] if parent else outliner).append(b)
        return b

    def add(bname, el):
        elements.append(el)
        bones[bname]['children'].append(el['uuid'])

    bone('base', [0, 0, 0])
    add('base', cube('slab_a', [-15, 0, -15], [15, 3, 15], stone))
    add('base', cube('slab_b', [-12, 3, -12], [12, 6, 12], stone, rot=[0, 45, 0], origin=[0, 3, 0]))
    add('base', cube('slab_c', [-8, 6, -8], [8, 9, 8], stone))
    add('base', cube('runes_a', [-15.15, 0.4, -15.15], [15.15, 2.6, 15.15], runes, faces=['north', 'south', 'east', 'west'],
                     uv=[0, 18, 64, 36]))
    add('base', cube('runes_c', [-8.15, 6.4, -8.15], [8.15, 8.6, 8.15], runes, faces=['north', 'south', 'east', 'west'],
                     uv=[0, 18, 64, 36]))
    bone('rocks', [0, 0, 0], 'base')
    for i, (x, z) in enumerate(((19, 19), (-19, 19), (19, -19), (-19, -19))):
        y = 6 + i % 2 * 3
        add('rocks', cube(f'rock_{i}', [x - 2.5, y, z - 2.5], [x + 2.5, y + 5, z + 2.5], stone,
                          rot=[RNG.randint(-25, 25), RNG.randint(0, 90), RNG.randint(-25, 25)], origin=[x, y + 2.5, z]))

    bone('circle', [0, 0, 0])
    add('circle', cube('ring_out', [-40, 0.15, -40], [40, 0.15, 40], circle, faces=['up']))
    bone('circle2', [0, 0, 0])
    add('circle2', cube('ring_in', [-24, 0.3, -24], [24, 0.3, 24], circle, faces=['up']))

    bone('crystal', [0, 44, 0])
    add('crystal', cube('body', [-8, 24, -8], [8, 60, 8], crystal, rot=[0, 45, 0], origin=[0, 44, 0]))
    add('crystal', cube('top_1', [-6, 60, -6], [6, 68, 6], crystal, rot=[0, 45, 0], origin=[0, 44, 0]))
    add('crystal', cube('top_2', [-3.5, 68, -3.5], [3.5, 75, 3.5], crystal, rot=[0, 45, 0], origin=[0, 44, 0]))
    add('crystal', cube('top_3', [-1.5, 75, -1.5], [1.5, 79, 1.5], crystal, rot=[0, 45, 0], origin=[0, 44, 0]))
    add('crystal', cube('bot_1', [-6, 16, -6], [6, 24, 6], crystal, rot=[0, 45, 0], origin=[0, 44, 0]))
    add('crystal', cube('bot_2', [-3.5, 11, -3.5], [3.5, 16, 3.5], crystal, rot=[0, 45, 0], origin=[0, 44, 0]))
    add('crystal', cube('side_a', [6, 28, -3.5], [13, 50, 3.5], crystal, rot=[0, 0, -24], origin=[9, 28, 0]))
    add('crystal', cube('side_a2', [7.5, 50, -2], [11.5, 56, 2], crystal, rot=[0, 0, -24], origin=[9, 28, 0]))
    add('crystal', cube('side_b', [-13, 30, -3.5], [-6, 48, 3.5], crystal, rot=[0, 0, 26], origin=[-9, 30, 0]))
    add('crystal', cube('side_b2', [-11.5, 48, -2], [-7.5, 53, 2], crystal, rot=[0, 0, 26], origin=[-9, 30, 0]))
    bone('core', [0, 44, 0], 'crystal')
    add('core', cube('core', [-3.6, 28, -3.6], [3.6, 58, 3.6], core, rot=[0, 45, 0], origin=[0, 44, 0]))
    bone('cracks', [0, 44, 0], 'crystal')
    add('cracks', cube('cracks', [-8.3, 23.7, -8.3], [8.3, 60.3, 8.3], cracks, rot=[0, 45, 0], origin=[0, 44, 0]))

    bone('shards', [0, 44, 0])
    for i in range(6):
        a = math.radians(i * 60)
        x, z = math.cos(a) * 27, math.sin(a) * 27
        y = 42 + (7 if i % 2 else -5)
        bone(f'shard_{i}', [x, y, z], 'shards', rotation=[RNG.randint(-30, 30), 0, RNG.randint(-30, 30)])
        add(f'shard_{i}', cube(f'shard_{i}', [x - 2, y - 5, z - 2], [x + 2, y + 5, z + 2], crystal, rot=[0, 45, 0],
                               origin=[x, y, z]))

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
    for i in range(6):
        shard_spin[f'shard_{i}'] = [kf('rotation', 0, [0, 0, 0]), kf('rotation', 8, [0, 720 * (1 if i % 2 else -1), 0])]
    idle = anim('idle', 8, 'loop', dict({
        'crystal': [kf('position', 0, [0, 0, 0], 'catmullrom'), kf('position', 2, [0, 2.5, 0], 'catmullrom'),
                    kf('position', 4, [0, 0, 0], 'catmullrom'), kf('position', 6, [0, 2.5, 0], 'catmullrom'),
                    kf('position', 8, [0, 0, 0], 'catmullrom'),
                    kf('rotation', 0, [0, 0, 0]), kf('rotation', 8, [0, 360, 0])],
        'shards': [kf('rotation', 0, [0, 0, 0]), kf('rotation', 8, [0, -360, 0]),
                   kf('position', 0, [0, 0, 0], 'catmullrom'), kf('position', 4, [0, 3, 0], 'catmullrom'),
                   kf('position', 8, [0, 0, 0], 'catmullrom')],
        'circle': [kf('rotation', 0, [0, 0, 0]), kf('rotation', 8, [0, 90, 0])],
        'circle2': [kf('rotation', 0, [0, 0, 0]), kf('rotation', 8, [0, -90, 0])],
        'rocks': [kf('position', 0, [0, 0, 0], 'catmullrom'), kf('position', 4, [0, 1.5, 0], 'catmullrom'),
                  kf('position', 8, [0, 0, 0], 'catmullrom')],
        'cracks': [kf('scale', 0, [0, 0, 0])],
    }, **shard_spin))
    shake = []
    for k in range(36):
        t = round(k * 0.1, 2)
        amp = min(1.0, t / 2.6) * 4
        shake.append(kf('rotation', t, [amp * (1 if k % 2 else -1), 360 * t / 3.5 * 3, amp * (1 if k % 3 else -1)]))
    activate = anim('activate', 3.5, 'hold', {
        'crystal': shake + [kf('position', 0, [0, 0, 0]), kf('position', 3.5, [0, 7, 0], 'catmullrom'),
                            kf('scale', 0, [1, 1, 1]), kf('scale', 2.8, [1.12, 1.12, 1.12], 'catmullrom'),
                            kf('scale', 3.3, [1.3, 1.3, 1.3])],
        'core': [kf('scale', 0, [1, 1, 1]), kf('scale', 3.3, [1.6, 1.25, 1.6], 'catmullrom')],
        'cracks': [kf('scale', 0, [0, 0, 0]), kf('scale', 0.35, [0, 0, 0], 'step'), kf('scale', 0.4, [1, 1, 1])],
        'shards': [kf('rotation', 0, [0, 0, 0]), kf('rotation', 3.5, [0, -1080, 0]),
                   kf('scale', 0, [1, 1, 1]), kf('scale', 3.2, [1.5, 1.5, 1.5], 'catmullrom')],
    }, override=True)
    dormant = anim('dormant', 0.1, 'hold', {
        'crystal': [kf('scale', 0, [0.16, 0.16, 0.16]), kf('position', 0, [0, -26, 0])],
        'shards': [kf('scale', 0, [0, 0, 0])],
        'cracks': [kf('scale', 0, [0, 0, 0])],
    }, override=True)
    regrow = anim('regrow', 2.2, 'once', {
        'crystal': [kf('scale', 0, [0.16, 0.16, 0.16]), kf('scale', 2.0, [1, 1, 1], 'catmullrom'),
                    kf('position', 0, [0, -26, 0]), kf('position', 2.0, [0, 0, 0], 'catmullrom')],
        'shards': [kf('scale', 0, [0, 0, 0]), kf('scale', 1.2, [0, 0, 0]), kf('scale', 2.2, [1, 1, 1], 'catmullrom')],
        'cracks': [kf('scale', 0, [0, 0, 0])],
    }, override=True)

    textures = []
    for name, img in TEX:
        textures.append({'name': name + '.png', 'uuid': uid(), 'source': data_url(img), 'width': img.width,
                         'height': img.height, 'uv_width': img.width, 'uv_height': img.height})
    return {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False}, 'name': 'cristal',
            'resolution': {'width': 64, 'height': 64}, 'elements': elements, 'outliner': outliner,
            'textures': textures, 'animations': [idle, activate, dormant, regrow]}


def main():
    import tempfile
    bb = build()
    path = os.path.join(tempfile.gettempdir(), 'cristal.bbmodel')
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(bb, f)
    store = B.TextureStore(os.path.join(RES, 'textures', 'bosses', 'grieta', 'me'), 'tfclient:textures/bosses/grieta/me/')
    problems = []
    m = B.convert(path, store, problems, boss=True)
    if problems:
        print('\n'.join(problems))
    # Las texturas «tint» se tiñen del color del jefe en el juego
    for t, (name, _img) in zip(m['tex'], TEX):
        if 'tint' in name:
            t['tint'] = True
    out = os.path.join(RES, 'bosses', 'models', 'grieta.cristal.json')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(m, f, separators=(',', ':'))
    print('grieta.cristal:', len(m['bones']), 'huesos,', sum(len(b['quads']) for b in m['bones']), 'caras')
    if '--preview' in sys.argv:
        dest = sys.argv[sys.argv.index('--preview') + 1]
        import vfx_render as R
        tex = R.load_textures(m, os.path.join(RES, 'textures', 'bosses', 'grieta', 'me'))
        for a, t in (('idle', 0), ('idle', 3), ('activate', 1.5), ('activate', 3.3), ('dormant', 0), ('regrow', 1.1)):
            img = R.render(m, tex, a, t, size=360, yaw=-30, pitch=-18)
            bg = Image.new('RGBA', img.size, (24, 26, 32, 255))
            bg.alpha_composite(img)
            bg.convert('RGB').save(os.path.join(dest, f'grieta_{a}_{t}.png'))


if __name__ == '__main__':
    main()
