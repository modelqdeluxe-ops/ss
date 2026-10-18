"""Vista previa del viaje por el agujero de gusano (shader wormhole, TF Client 1.3.91) sin abrir el juego.

    xvfb-run -a python3 -I tools/void/tunel_preview.py <carpeta con assets/minecraft/textures> <salida> [--vuelta] [--size 960x540]

Las texturas son las del propio Minecraft (no están en el repo): una carpeta con assets/minecraft/textures/... sacada del
.jar del cliente 1.20.1, y el panorama del menú (assets/minecraft/textures/gui/title/background/panorama_0..3.png; en el
.jar son de 1×1: los de verdad vienen en el índice de recursos). Se juntan igual que en vacio/WormholeTextures (TILES,
la tira del panorama, el portal multiplicado por su transparencia) y se dibujan fotogramas de todo el viaje con los
mismos tiempos (TravelFx.travel, gates, open, exit).
"""
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SH = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'shaders', 'core'))

# = WormholeTextures.TILES (orden del atlas 4×4) y sus tintes
TILES = ['grass_block_top', 'grass_block_side', 'dirt', 'stone', 'oak_leaves', 'oak_log', 'cherry_leaves', 'cherry_log',
         'netherrack', 'nether_bricks', 'glowstone', 'crimson_nylium', 'end_stone', 'purpur_block', 'obsidian', 'crying_obsidian']
TINT = {'grass_block_top': (0x7c, 0xbd, 0x6b), 'oak_leaves': (0x59, 0xae, 0x30)}
# = TravelFx: tiempos (segundos)
OPEN0, OPEN1, GATE1_T, GATE2_T, TELEPORT = 1.0, 2.2, 4.0, 5.9, 7.5


def smooth(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def travel(t):
    """= TravelFx.travel: velocidad 1,4 + 2,6·smoothstep(1; 4,5; t), integrada."""
    a, b = 1.0, 4.5
    if t < a:
        s = 0.0
    elif t <= b:
        y = (t - a) / (b - a)
        s = (b - a) * (y ** 3 - y ** 4 / 2)
    else:
        s = (b - a) / 2 + (t - b)
    return 1.4 * t + 2.6 * s


def textures(root):
    tex = os.path.join(root, 'assets', 'minecraft', 'textures')
    faces = [Image.open(os.path.join(tex, 'gui', 'title', 'background', 'panorama_%d.png' % i)).convert('RGB').resize((512, 512))
             for i in range(4)]
    pano = Image.new('RGB', (2048, 512))
    for i, f in enumerate(faces):
        pano.paste(f, (i * 512, 0))
    atlas = Image.new('RGB', (64, 64))
    for i, name in enumerate(TILES):
        im = Image.open(os.path.join(tex, 'block', name + '.png')).convert('RGBA')
        w = im.width
        im = im.crop((0, 0, w, w)).resize((16, 16), Image.NEAREST)
        a = np.asarray(im).astype(float)
        if name in TINT:
            a[..., :3] *= np.array(TINT[name]) / 255.0
        # (las hojas tienen huecos: detrás, oscuro)
        rgb = a[..., :3] * (a[..., 3:4] / 255.0) + np.array([20, 30, 18]) * (1 - a[..., 3:4] / 255.0)
        atlas.paste(Image.fromarray(rgb.clip(0, 255).astype(np.uint8)), ((i % 4) * 16, (i // 4) * 16))
    lava = Image.open(os.path.join(tex, 'block', 'lava_still.png')).convert('RGB')
    portal = Image.open(os.path.join(tex, 'block', 'nether_portal.png')).convert('RGBA')
    pa = np.asarray(portal).astype(float)
    portal = Image.fromarray((pa[..., :3] * pa[..., 3:4] / 255).astype(np.uint8))
    stars = Image.open(os.path.join(tex, 'entity', 'end_portal.png')).convert('RGB')
    sky = Image.open(os.path.join(tex, 'environment', 'end_sky.png')).convert('RGB')
    return pano, atlas, lava, portal, stars, sky


def main():
    import moderngl
    root, out = sys.argv[1], sys.argv[2]
    back = '--vuelta' in sys.argv
    w, h = (960, 540)
    if '--size' in sys.argv:
        w, h = map(int, sys.argv[sys.argv.index('--size') + 1].split('x'))
    os.makedirs(out, exist_ok=True)
    ctx = moderngl.create_standalone_context(require=330)
    prog = ctx.program(vertex_shader=open(os.path.join(SH, 'wormhole.vsh')).read(),
                       fragment_shader=open(os.path.join(SH, 'wormhole.fsh')).read())
    imgs = textures(root)
    texs = []
    for i, im in enumerate(imgs):
        t = ctx.texture(im.size, 3, im.tobytes())
        t.filter = (moderngl.LINEAR, moderngl.LINEAR) if i == 0 else (moderngl.NEAREST, moderngl.NEAREST)
        t.repeat_x = t.repeat_y = True
        t.use(i)
        texs.append(t)
    # (la primera fila de la imagen en v = 0, como las sube Minecraft)
    vb = ctx.buffer(np.array([-1, -1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, 1, 1, 0, -1, 1, 0], 'f4').tobytes())
    vao = ctx.vertex_array(prog, [(vb, '3f', 'Position')])
    fbo = ctx.framebuffer(color_attachments=[ctx.texture((w, h), 4)])
    fbo.use()
    lava_frames = imgs[2].height / imgs[2].width
    portal_frames = imgs[3].height / imgs[3].width
    g1, g2 = travel(GATE1_T), travel(GATE2_T)
    arrive = TELEPORT + 0.3
    times = [('0_rasgado', 1.6), ('1_tunel', 2.6), ('2_puerta1_cerca', 3.6), ('3_puerta1', 4.0), ('4_mundo2', 4.8),
             ('5_puerta2_cerca', 5.5), ('6_mundo3', 6.6), ('7_salida', arrive + 1.0), ('8_luz', arrive + 1.45), ('9_se_va', arrive + 1.9)]
    sheet = []
    for name, t in times:
        open_k = smooth(OPEN0, OPEN1, t)
        ex = 0.0 if t < arrive + 0.4 else (smooth(arrive + 0.4, arrive + 1.5, t) + smooth(arrive + 1.5, arrive + 2.4, t))
        vals = {'Sampler0': 0, 'Sampler1': 1, 'Sampler2': 2, 'Sampler3': 3, 'Sampler4': 4, 'Sampler5': 5, 'WTime': t,
                'Travel': travel(t), 'Gates': (g1, g2), 'Open': open_k, 'Exit': ex, 'Back': 1.0 if back else 0.0,
                'Frames': (lava_frames, portal_frames), 'ScreenSize': (float(w), float(h))}
        for k, v in vals.items():
            if k in prog:
                prog[k].value = v
        ctx.clear(0.15, 0.2, 0.25, 1.0)
        ctx.enable(moderngl.BLEND)
        ctx.blend_func = moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA
        vao.render()
        im = Image.frombytes('RGBA', (w, h), fbo.read(components=4)).transpose(Image.FLIP_TOP_BOTTOM).convert('RGB')
        im.save(os.path.join(out, 'tunel_%s%s.png' % ('vuelta_' if back else '', name)))
        sheet.append(im.resize((w // 2, h // 2)))
    cols = 2
    rows = (len(sheet) + cols - 1) // cols
    s = Image.new('RGB', (cols * (w // 2), rows * (h // 2)))
    for i, im in enumerate(sheet):
        s.paste(im, ((i % cols) * (w // 2), (i // cols) * (h // 2)))
    s.save(os.path.join(out, 'tunel_%stodo.png' % ('vuelta_' if back else '')))
    print('hecho en', out, 'puertas', round(g1, 2), round(g2, 2))


if __name__ == '__main__':
    main()
