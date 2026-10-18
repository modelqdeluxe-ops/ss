"""Vista previa del cielo de The Void sin abrir el juego: ejecuta el shader de verdad (void_sky.vsh/.fsh) con Mesa.

    xvfb-run -a python3 tools/void/cielo_preview.py carpeta [--t segundos] [--q calidad] [--size 960x540]

Necesita `pip install moderngl` y una pantalla virtual (xvfb-run). Dibuja el cubo del cielo como VoidSky (posiciones
±100 alrededor de la cámara, la matriz de la vista y la proyección de Minecraft con 70° de campo) mirando a varios
sitios: hacia el agujero (como al llegar al santuario), de lado, arriba y abajo. Los uniformes salen de VoidSky.java
(HOLE, normal del disco): si los cambias allí, cámbialos aquí.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
SH = os.path.join(RES, 'shaders', 'core')


def norm(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def hole_and_disk():
    """Las mismas cuentas que VoidSky.HOLE y VoidSky.diskNormal()."""
    f = norm([0.16, 0.42, 1.0])
    up = np.array([0.0, 1.0, 0.0])
    n0 = norm(up - f * up.dot(f))
    tilt = math.radians(7)
    n1 = norm(n0 * math.cos(tilt) - f * math.sin(tilt))
    roll = math.radians(-16)
    # girar n1 alrededor de f (Rodrigues)
    n = n1 * math.cos(roll) + np.cross(f, n1) * math.sin(roll) + f * f.dot(n1) * (1 - math.cos(roll))
    return f, norm(n)


def perspective(fov, aspect, near, far):
    t = 1 / math.tan(math.radians(fov) / 2)
    m = np.zeros((4, 4))
    m[0, 0] = t / aspect
    m[1, 1] = t
    m[2, 2] = (far + near) / (near - far)
    m[2, 3] = 2 * far * near / (near - far)
    m[3, 2] = -1
    return m


def view(yaw, pitch):
    """Como Minecraft: rotación X por pitch y luego Y por yaw + 180."""
    def rx(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]])

    def ry(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]])
    return rx(math.radians(pitch)) @ ry(math.radians(yaw + 180))


def main():
    import moderngl
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    t = float(sys.argv[sys.argv.index('--t') + 1]) if '--t' in sys.argv else 30.0
    q = float(sys.argv[sys.argv.index('--q') + 1]) if '--q' in sys.argv else 1.0
    w, h = (960, 540)
    if '--size' in sys.argv:
        w, h = (int(x) for x in sys.argv[sys.argv.index('--size') + 1].split('x'))
    ctx = moderngl.create_standalone_context(require=330)
    vs = open(os.path.join(SH, 'void_sky.vsh')).read()
    fs = open(os.path.join(SH, 'void_sky.fsh')).read()
    prog = ctx.program(vertex_shader=vs, fragment_shader=fs)
    S = 100.0
    c = [(-S, -S, -S), (S, -S, -S), (S, S, -S), (-S, S, -S), (-S, -S, S), (S, -S, S), (S, S, S), (-S, S, S)]
    faces = [(0, 1, 2, 3), (5, 4, 7, 6), (4, 0, 3, 7), (1, 5, 6, 2), (3, 2, 6, 7), (4, 5, 1, 0)]
    tri = []
    for a, b, cc, d in faces:
        for k in (a, b, cc, a, cc, d):
            tri.append(c[k])
    vbo = ctx.buffer(np.array(tri, 'f4').tobytes())
    vao = ctx.vertex_array(prog, [(vbo, '3f', 'Position')])
    img = Image.open(os.path.join(RES, 'textures', 'environment', 'grieta_cielo.png')).convert('RGB')
    tex = ctx.texture(img.size, 3, img.tobytes())
    tex.filter = (moderngl.LINEAR, moderngl.LINEAR)
    tex.repeat_x = True
    tex.repeat_y = False
    tex.use(0)
    f, n = hole_and_disk()
    for name, val in (('Sampler0', 0), ('VoidTime', t), ('HoleDir', tuple(f)), ('DiskNormal', tuple(n)), ('Quality', q)):
        if name in prog:
            prog[name].value = val
    fbo = ctx.simple_framebuffer((w, h))
    fbo.use()
    proj = perspective(70, w / h, 0.05, 1000)
    hole_yaw = math.degrees(math.atan2(-f[0], f[2]))
    hole_pitch = -math.degrees(math.asin(f[1]))
    views = [('agujero', hole_yaw, hole_pitch + 8), ('llegada', -9, -8), ('lado', hole_yaw + 70, -5),
             ('arriba', hole_yaw, -70), ('abajo', hole_yaw + 180, 50), ('espalda', hole_yaw + 180, -15)]
    for vname, yaw, pitch in views:
        mv = view(yaw, pitch)
        prog['ModelViewMat'].write(mv.T.astype('f4').tobytes())
        prog['ProjMat'].write(proj.T.astype('f4').tobytes())
        ctx.clear(0, 0, 0)
        vao.render()
        data = fbo.read(components=3)
        im = Image.frombytes('RGB', (w, h), data).transpose(Image.FLIP_TOP_BOTTOM)
        im.save(os.path.join(out, f'cielo_{vname}.png'))
    print('hecho en', out, 'agujero hacia', np.round(f, 3), 'normal', np.round(n, 3))


if __name__ == '__main__':
    main()
