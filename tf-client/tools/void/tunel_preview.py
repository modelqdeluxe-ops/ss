"""Vista previa del viaje por el agujero de gusano (shader wormhole, TF Client 1.3.91; limpio y con la antesala del Void
desde la 1.3.92) sin abrir el juego.

    xvfb-run -a python3 -I tools/void/tunel_preview.py <salida> [--vuelta] [--size 960x540]

Todo es del propio shader (ruido filtrado) menos la antesala del Void, que usa las nebulosas del cielo del Void
(textures/environment/grieta_cielo.png, del mod). Dibuja fotogramas de todo el viaje con los mismos tiempos que TravelFx
(travel, las tres puertas, open, exit).
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
SH = os.path.join(RES, 'shaders', 'core')
# = TravelFx: tiempos (segundos)
OPEN0, OPEN1, GATE1_T, GATE2_T, GATE3_T, TELEPORT = 1.0, 2.2, 3.4, 4.9, 6.3, 7.5


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


def textures():
    return [Image.open(os.path.join(RES, 'textures', 'environment', 'grieta_cielo.png')).convert('RGB')]


def main():
    import moderngl
    out = sys.argv[1]
    back = '--vuelta' in sys.argv
    w, h = (960, 540)
    if '--size' in sys.argv:
        w, h = map(int, sys.argv[sys.argv.index('--size') + 1].split('x'))
    os.makedirs(out, exist_ok=True)
    ctx = moderngl.create_standalone_context(require=330)
    prog = ctx.program(vertex_shader=open(os.path.join(SH, 'wormhole.vsh')).read(),
                       fragment_shader=open(os.path.join(SH, 'wormhole.fsh')).read())
    neb = textures()[0]
    tex = ctx.texture(neb.size, 3, neb.tobytes())
    tex.filter = (moderngl.LINEAR, moderngl.LINEAR)
    tex.repeat_x = tex.repeat_y = True
    tex.use(1)
    # (la primera fila de la imagen en v = 0, como las sube Minecraft)
    vb = ctx.buffer(np.array([-1, -1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, 1, 1, 0, -1, 1, 0], 'f4').tobytes())
    vao = ctx.vertex_array(prog, [(vb, '3f', 'Position')])
    fbo = ctx.framebuffer(color_attachments=[ctx.texture((w, h), 4)])
    fbo.use()
    g1, g2, g3 = travel(GATE1_T), travel(GATE2_T), travel(GATE3_T)
    arrive = TELEPORT + 0.3
    times = [('0_rasgado', 1.6), ('1_tramo1', 2.6), ('2_puerta1', 3.25), ('3_tramo2', 4.1), ('4_puerta2', 4.75),
             ('5_tramo3', 5.5), ('6_puerta3', 6.15), ('7_tramo4', 7.0), ('8_salida', arrive + 0.75), ('9_luz', arrive + 1.4),
             ('10_se_va', arrive + 1.95)]
    sheet = []
    for name, t in times:
        open_k = smooth(OPEN0, OPEN1, t)
        ex = 0.0 if t < arrive + 0.4 else (smooth(arrive + 0.4, arrive + 1.5, t) + smooth(arrive + 1.5, arrive + 2.4, t))
        vals = {'Sampler1': 1, 'WTime': t, 'Travel': travel(t), 'Gates': (g1, g2, g3), 'Open': open_k, 'Exit': ex,
                'Back': 1.0 if back else 0.0, 'ScreenSize': (float(w), float(h))}
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
    print('hecho en', out, 'puertas', round(g1, 2), round(g2, 2), round(g3, 2))


if __name__ == '__main__':
    main()
