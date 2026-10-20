"""Vista previa de la grieta del cielo (shader sky_rift) sin el juego: fotogramas de su animación.

    xvfb-run -a python3 -I tools/void/grieta_cielo_preview.py carpeta [--size 960x540] [--gif]

Dibuja como SkyRiftClient: un cuadro enorme a DRAW bloques en la dirección de la grieta, con una cámara de 70° que la
mira (y otra desde el horizonte, mirando al norte), sobre un cielo de día que se apaga como en el juego (el velo de
SkyRiftClient). Los momentos (STATES) son los valores que da SkyRiftClient.look() en esos segundos. Con --gif, además,
un GIF de la aparición entera (preludio, se rompe, se abre) para verla en movimiento.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
SH = os.path.join(RES, 'shaders', 'core')

YAW, PITCH = 180.0, -50.0     # = SkyRift.YAW, PITCH
DRAW = 25.0                    # = SkyRiftClient.DRAW
ANG = 1.21                     # medio ancho del cuadro (radianes) = SkyRiftClient.ANG (más de 1,2: el shader se apaga antes del borde)


def spin_rate(phase, t):
    """(1.3.96) Lo rápido que gira la tormenta (rad/s), como SkyRiftClient.spinRate()."""
    sm = lambda a, b, x: (lambda u: u * u * (3 - 2 * u))(min(1.0, max(0.0, (x - a) / (b - a))))
    base = 0.06
    if phase == 'preludio':
        return base + 0.55 * sm(9.0, 12.0, t)
    if phase == 'rompe':
        return base + 0.55 * (1 - sm(1.8, 5.5, t))
    if phase == 'reabre':
        return base + 0.5 * (sm(0.0, 1.0, t) if t < 1.2 else 1 - sm(1.2, 4.0, t))
    if phase == 'cierra':
        return base + 0.7 * sm(0.0, 1.8, t) * (1 - sm(2.3, 3.5, t))
    if phase == 'final':
        return base + 0.8 * sm(0.0, 2.2, t) * (1 - sm(2.5, 3.5, t))
    return base


def spin(phase, t):
    """Lo girado: la base de siempre (como si llevara mucho rato) más lo que ha corrido en esta fase."""
    n = max(1, int(t / 0.02))
    extra = sum(spin_rate(phase, (i + 0.5) * t / n) - 0.06 for i in range(n)) * t / n
    return 60.0 + 0.06 * t + extra


def look(phase, t):
    """Lo mismo que SkyRiftClient.look() (y skyDim): crack, open, flash, alpha, dark, storm, shatter, dim."""
    sm = lambda a, b, x: (lambda u: u * u * (3 - 2 * u))(min(1.0, max(0.0, (x - a) / (b - a))))
    bump = lambda x, at, w: max(0.0, 1.0 - abs(x - at) / w)
    flicks = [3.6, 6.4, 8.7, 10.3, 11.4]
    if phase == 'preludio':
        fl = max(bump(t, f, 0.18) for f in flicks)
        cr = max([0.0] + [bump(t, f, 0.25) * (0.12 + 0.04 * i) for i, f in enumerate(flicks)])
        cr = max(cr, 0.18 * sm(7.0, 12.0, t))
        return dict(Crack=cr, Open=0.0, Flash=0.45 * fl, Alpha=1.0, Dark=0.95 * sm(0.5, 10, t), Storm=sm(0.0, 11.5, t),
                    Shatter=-1.0, dim=0.6 * sm(0, 9, t) - 0.15 * fl)
    if phase == 'rompe':
        steps_t, steps_v = [0.0, 0.35, 0.75, 1.15, 1.5], [0.3, 0.45, 0.62, 0.8, 1.0]
        cr = 0.18
        for i, (a, v) in enumerate(zip(steps_t, steps_v)):
            cr += (v - (0.18 if i == 0 else steps_v[i - 1])) * sm(a, a + 0.14, t)
        BREAK = 1.8
        flash = max([0.35 * bump(t, a + 0.05, 0.2) for a in steps_t[1:]] + [0.0])
        flash = max(flash, 1.6 * math.exp(-(t - BREAK) * 1.6) if t >= BREAK else 0.3 * sm(1.5, BREAK, t))
        return dict(Crack=cr, Open=sm(BREAK, 5.5, t), Flash=flash, Alpha=1.0, Dark=0.95 * (1 - sm(BREAK, 4.0, t)), Storm=1.0,
                    Shatter=(t - BREAK) if t >= BREAK else -1.0, dim=0.6 - 0.45 * sm(5.5, 16, t))
    if phase == 'abierta':
        return dict(Crack=1.0, Open=1.0, Flash=0.0, Alpha=1.0, Dark=0.0, Storm=1.0, Shatter=-1.0, dim=0.15)
    if phase == 'cierra':
        return dict(Crack=1.0 - 0.65 * sm(0.5, 2.5, t), Open=1.0 - sm(0.0, 2.2, t), Flash=0.8 * math.exp(-abs(t - 2.3) * 6),
                    Alpha=1.0 - 0.2 * sm(2.3, 3.5, t), Dark=0.0, Storm=1.0 - 0.75 * sm(0.5, 3.0, t), Shatter=-1.0, dim=0.15 * (1 - sm(0.5, 4, t)))
    if phase == 'cicatriz':
        return dict(Crack=0.35, Open=0.0, Flash=0.0, Alpha=0.8, Dark=0.0, Storm=0.25, Shatter=-1.0, dim=0.0)
    if phase == 'reabre':
        # (desde la cicatriz: la tormenta vuelve, las grietas crecen y estalla a los 1,2 s)
        BREAK = 1.2
        flash = 1.3 * math.exp(-(t - BREAK) * 1.8) if t >= BREAK else 0.3 * sm(0.9, BREAK, t)
        return dict(Crack=0.35 + 0.65 * sm(0.0, BREAK, t), Open=sm(BREAK, 4.0, t), Flash=flash, Alpha=0.8 + 0.2 * sm(0, 0.6, t),
                    Dark=0.6 * (1 - sm(BREAK, 3.0, t)), Storm=0.25 + 0.75 * sm(0.0, 1.5, t),
                    Shatter=(t - BREAK) if t >= BREAK else -1.0, dim=0.15 + 0.3 * sm(0, 1.2, t) * (1 - sm(2, 7, t)))
    if phase == 'final':
        # (se cierra para siempre: gran fogonazo a los 2,5 s y desaparece todo)
        return dict(Crack=1.0 - sm(0.5, 3.0, t), Open=1.0 - sm(0.0, 2.5, t), Flash=1.6 * math.exp(-abs(t - 2.5) * 3.5),
                    Alpha=1.0 - sm(2.6, 4.5, t), Dark=0.0, Storm=1.0 - sm(0.5, 3.5, t), Shatter=-1.0, dim=0.15 * (1 - sm(0.5, 4, t)))
    raise ValueError(phase)


STATES = [('preludio', 3.0), ('preludio', 7.5), ('preludio', 8.7), ('preludio', 11.6), ('rompe', 0.5), ('rompe', 1.3),
          ('rompe', 1.85), ('rompe', 2.4), ('rompe', 3.5), ('rompe', 5.5), ('abierta', 20.0), ('abierta', 27.5),
          ('cierra', 1.5), ('cierra', 2.1), ('cicatriz', 40.0), ('reabre', 1.4), ('reabre', 3.0), ('final', 2.5)]


def direction(yaw, pitch):
    y, p = math.radians(yaw), math.radians(pitch)
    return np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])


def basis():
    f = direction(YAW, PITCH)
    f /= np.linalg.norm(f)
    right = np.cross(f, [0.0, 1.0, 0.0])
    right /= np.linalg.norm(right)
    up = np.cross(right, f)
    return f, right, up


def look_at(f):
    # vista: la cámara en el origen mirando a f (en OpenGL mira hacia −z)
    zf = -f / np.linalg.norm(f)
    x = np.cross([0.0, 1.0, 0.0], zf)
    if np.linalg.norm(x) < 1e-6:
        x = np.array([1.0, 0.0, 0.0])
    x /= np.linalg.norm(x)
    y = np.cross(zf, x)
    m = np.eye(4)
    m[0, :3], m[1, :3], m[2, :3] = x, y, zf
    return m


def perspective(fov, aspect, near, far):
    f = 1 / math.tan(math.radians(fov) / 2)
    return np.array([[f / aspect, 0, 0, 0], [0, f, 0, 0], [0, 0, (far + near) / (near - far), 2 * far * near / (near - far)],
                     [0, 0, -1, 0]], 'f4')


def main():
    import moderngl
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    w, h = 960, 540
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
    f, right, up = basis()
    hw = DRAW * math.tan(ANG)
    c = f * DRAW
    corners = [c - right * hw - up * hw, c + right * hw - up * hw, c + right * hw + up * hw, c - right * hw + up * hw]
    uvs = [(0, 0), (1, 0), (1, 1), (0, 1)]
    tri = []
    for i in (0, 1, 2, 0, 2, 3):
        tri += list(corners[i]) + list(uvs[i])
    vbo = ctx.buffer(np.array(tri, 'f4').tobytes())
    vao = ctx.vertex_array(prog, [(vbo, '3f 2f', 'Position', 'UV0')])
    proj = perspective(70, w / h, 0.05, 256)
    cams = {'cara': f, 'horizonte': direction(180.0, -12.0)}
    fbo = ctx.simple_framebuffer((w, h), components=4)
    fbo.use()

    def frame(state, cam):
        phase, t = state
        vals = look(phase, t)
        for k, v in vals.items():
            if k in prog:
                prog[k].value = v
        for k, v in (('Sampler0', 0), ('STime', 1000.0 + t), ('AtFar', 0.0), ('RiftF', tuple(f)), ('RiftR', tuple(right)),
                     ('RiftU', tuple(up)), ('Spin', spin(phase, t))):
            if k in prog:
                prog[k].value = v
        prog['ModelViewMat'].write(look_at(cams[cam]).T.astype('f4').tobytes())
        prog['ProjMat'].write(proj.T.tobytes())
        fbo.clear(0, 0, 0, 0)
        ctx.enable(moderngl.BLEND)
        ctx.blend_func = moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA, moderngl.ONE, moderngl.ONE_MINUS_SRC_ALPHA
        vao.render()
        im = np.frombuffer(fbo.read(components=4), np.uint8).reshape(h, w, 4)[::-1].astype(float)
        # el cielo de día, apagado como lo apaga SkyRiftClient (velo oscuro violeta)
        ys = np.linspace(0, 1, h)[:, None, None]
        sky = np.concatenate([120 + 60 * ys, 160 + 45 * ys, 235 + 15 * ys], axis=2) * np.ones((1, w, 1))
        dim = min(0.85, max(0.0, vals['dim'] * 0.78))
        sky = sky * (1 - dim) + np.array([6, 1, 15]) * dim
        a = im[..., 3:4] / 255.0
        comp = im[..., :3] + sky * (1 - a)
        img = Image.fromarray(comp.clip(0, 255).astype(np.uint8))
        ImageDraw.Draw(img).text((8, 8), '%s %.1f s' % (phase, t), fill=(255, 255, 0))
        return img

    sheet = []
    for st in STATES:
        img = frame(st, 'cara')
        img.save(os.path.join(out, 'grieta_%s_%04.1f.png' % st))
        sheet.append(img)
    sheet.append(frame(('abierta', 20.0), 'horizonte'))
    cols = 3
    sw, shh = w // 2, h // 2
    rows = (len(sheet) + cols - 1) // cols
    big = Image.new('RGB', (sw * cols, shh * rows))
    for i, im in enumerate(sheet):
        big.paste(im.resize((sw, shh)), ((i % cols) * sw, (i // cols) * shh))
    big.save(os.path.join(out, 'grieta_todo.png'))
    if '--gif' in sys.argv:
        # (pequeño: 320 × 180, a 4 fotogramas por segundo, con paleta común; ~2 MB)
        frames = []
        for phase, t0, t1 in (('preludio', 0.0, 12.0), ('rompe', 0.0, 8.0), ('abierta', 20.0, 23.0)):
            for t in np.arange(t0, t1, 0.25):
                frames.append(frame((phase, float(t)), 'cara').resize((320, 180), Image.LANCZOS).convert('P', palette=Image.ADAPTIVE, colors=128))
        frames[0].save(os.path.join(out, 'grieta.gif'), save_all=True, append_images=frames[1:], duration=250, loop=0, optimize=True)
    print('hecho en', out)


if __name__ == '__main__':
    main()
