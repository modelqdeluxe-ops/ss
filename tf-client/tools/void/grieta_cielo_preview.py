"""Vista previa de la grieta del cielo (shader sky_rift, TF Client 1.3.93) sin abrir el juego.

    xvfb-run -a python3 -I tools/void/grieta_cielo_preview.py <salida> [--size 500x700]

Dibuja el cuadro de la grieta (el shader de verdad) sobre un cielo de día, en los momentos del evento de
SkyRiftClient: las grietas creciendo, el fogonazo, el desgarro abriéndose, abierto, cerrándose y la cicatriz.
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
SH = os.path.join(RES, 'shaders', 'core')

# nombre, tiempo, grietas, abierto, fogonazo, opacidad[, preludio] (= los de SkyRiftClient en esos momentos)
STATES = [('0_preludio', 5.0, 0.0, 0.0, 0.0, 1.0, 0.8), ('0_grietas', 0.6, 0.35, 0.0, 0.0, 1.0), ('1_rompe', 1.4, 1.0, 0.0, 1.0, 1.0), ('2_abre', 2.2, 1.0, 0.35, 0.4, 1.0),
          ('3_abierta', 6.0, 1.0, 1.0, 0.0, 1.0), ('4_abierta_b', 9.3, 1.0, 1.0, 0.0, 1.0), ('5_cierra', 10.0, 0.7, 0.3, 0.0, 1.0),
          ('6_cicatriz', 12.0, 0.3, 0.0, 0.0, 0.75)]


def main():
    import moderngl
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    w, h = 500, 700
    if '--size' in sys.argv:
        w, h = (int(x) for x in sys.argv[sys.argv.index('--size') + 1].split('x'))
    ctx = moderngl.create_standalone_context(require=330)
    prog = ctx.program(vertex_shader=open(os.path.join(SH, 'sky_rift.vsh')).read(),
                       fragment_shader=open(os.path.join(SH, 'sky_rift.fsh')).read())
    neb = Image.open(os.path.join(RES, 'textures', 'environment', 'grieta_cielo.png')).convert('RGB')
    tex = ctx.texture(neb.size, 3, neb.tobytes())
    tex.filter = (moderngl.LINEAR, moderngl.LINEAR)
    tex.repeat_x = True
    tex.use(0)
    # el cuadro: posición (x, y, z) mirando hacia +z, y su uv
    quad = np.array([-1, -1, 4, 0, 0, 1, -1, 4, 1, 0, 1, 1, 4, 1, 1, -1, -1, 4, 0, 0, 1, 1, 4, 1, 1, -1, 1, 4, 0, 1], 'f4')
    vbo = ctx.buffer(quad.tobytes())
    vao = ctx.vertex_array(prog, [(vbo, '3f 2f', 'Position', 'UV0')])
    ident = np.eye(4, dtype='f4')
    # una proyección que pone el cuadro en el 80 % de la pantalla (z = 4): se ve si algo llega a su borde
    proj = np.array([[3.2, 0, 0, 0], [0, 3.2, 0, 0], [0, 0, 1, 0], [0, 0, 1, 0]], 'f4')
    fbo = ctx.simple_framebuffer((w, h), components=4)
    fbo.use()
    sheet = []
    for name, t, crack, opn, flash, alpha, *rest in STATES:
        vals = {'Sampler0': 0, 'STime': t, 'Crack': crack, 'Open': opn, 'Flash': flash, 'Alpha': alpha, 'AtFar': 0.0,
                'Dark': rest[0] if rest else 0.0}
        for k, v in vals.items():
            if k in prog:
                prog[k].value = v
        prog['ModelViewMat'].write(ident.T.tobytes())
        prog['ProjMat'].write(proj.T.tobytes())
        # el cielo de día detrás
        sky = np.zeros((h, w, 3), np.uint8)
        for y in range(h):
            k = y / h
            sky[y] = (int(120 + 60 * k), int(160 + 45 * k), int(235 + 15 * k))
        fbo.clear(0, 0, 0, 0)
        ctx.enable(moderngl.BLEND)
        ctx.blend_func = moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA, moderngl.ONE, moderngl.ONE_MINUS_SRC_ALPHA
        vao.render()
        im = np.frombuffer(fbo.read(components=4), np.uint8).reshape(h, w, 4)[::-1].astype(float)
        # (sobre el cielo: el color ya va multiplicado por su opacidad en lo que se dibujó sobre negro transparente)
        a = im[..., 3:4] / 255.0
        comp = im[..., :3] + sky.astype(float) * (1 - a)
        img = Image.fromarray(comp.clip(0, 255).astype(np.uint8))
        img.save(os.path.join(out, 'cielo_grieta_%s.png' % name))
        sheet.append(img.resize((w // 2, h // 2)))
    s = Image.new('RGB', (len(sheet) * (w // 2), h // 2))
    for i, im in enumerate(sheet):
        s.paste(im, (i * (w // 2), 0))
    s.save(os.path.join(out, 'cielo_grieta_todo.png'))
    print('hecho en', out)


if __name__ == '__main__':
    main()
