"""Vista previa del Velo del Alba y el Corazón del Alba (SanctuaryFx, 1.3.96), sin abrir el juego.

    xvfb-run -a python3 -I tools/void/santuario_preview.py <carpeta de salida>

Dibuja con los shaders del mod (velo_alba, corazon_alba) la burbuja (la misma malla que SanctuaryFx: 96 × 48), el
Corazón y su columna sobre un Void oscuro con la isla del santuario como un disco gris (para ver que la tapa), desde el
puente, desde lejos (la isla principal), desde dentro y al cruzarla (la onda).
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SH = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'shaders', 'core'))
sys.path.insert(0, HERE)
import isla_alba  # noqa: E402

CENTER = np.array(isla_alba.BUBBLE[:3], float) + 0.5
RADIUS = float(isla_alba.BUBBLE[3])
HEART = np.array(isla_alba.HEART, float) + 0.5
SEG_LON, SEG_LAT = 96, 48
HEART_HALF = 5.0
BEAM_W, BEAM_H = 2.4, 160.0
GROUND = (isla_alba.TOP + 1 - CENTER[1]) / RADIUS


def sphere():
    """Triángulos de la esfera de radio 1: igual que SanctuaryFx.sphere (el shader la pone en su sitio)."""
    out = []
    for j in range(SEG_LAT):
        t0, t1 = math.pi * j / SEG_LAT, math.pi * (j + 1) / SEG_LAT
        for i in range(SEG_LON):
            p0, p1 = 2 * math.pi * i / SEG_LON, 2 * math.pi * (i + 1) / SEG_LON
            q = [(t0, p0), (t0, p1), (t1, p1), (t1, p0)]
            v = [np.array([math.sin(t) * math.cos(p), math.cos(t), math.sin(t) * math.sin(p)]) for t, p in q]
            for k in (0, 1, 2, 0, 2, 3):
                out += list(v[k])
    return np.array(out, 'f4')


def look_at(fwd):
    zf = -fwd / np.linalg.norm(fwd)
    x = np.cross([0, 1, 0], zf)
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
    ctx = moderngl.create_standalone_context(require=330)
    veil = ctx.program(vertex_shader=open(os.path.join(SH, 'velo_alba.vsh')).read(),
                       fragment_shader=open(os.path.join(SH, 'velo_alba.fsh')).read())
    heart = ctx.program(vertex_shader=open(os.path.join(SH, 'corazon_alba.vsh')).read(),
                        fragment_shader=open(os.path.join(SH, 'corazon_alba.fsh')).read())
    ground = ctx.program(vertex_shader='''#version 330
in vec3 Position; uniform mat4 ModelViewMat; uniform mat4 ProjMat; out float yy;
void main(){ gl_Position = ProjMat * ModelViewMat * vec4(Position,1.0); yy = Position.y; }''',
                         fragment_shader='''#version 330
in float yy; out vec4 c; void main(){ c = vec4(vec3(0.16,0.15,0.2), 1.0); }''')
    proj = perspective(70, w / h, 0.05, 512)
    SPHERE = sphere()
    fbo = ctx.framebuffer(color_attachments=[ctx.texture((w, h), 4)], depth_attachment=ctx.depth_texture((w, h)))
    fbo.use()

    def draw(cam, fwd, t, ripple=(0, 1, 0, -1.0)):
        view = look_at(np.array(fwd, float))
        mv = view.T.astype('f4').tobytes()
        fbo.clear(0.02, 0.0, 0.05, 1.0)
        ctx.enable(moderngl.DEPTH_TEST)
        ctx.disable(moderngl.BLEND)
        ctx.disable(moderngl.CULL_FACE)
        # la isla (un cono: arriba un disco a TOP + 1) para la profundidad
        rel = lambda p: np.array(p, float) - cam  # noqa: E731
        tri = []
        top = isla_alba.TOP + 1
        cx, cz = isla_alba.CX + 0.5, isla_alba.CZ + 0.5
        for i in range(48):
            a0, a1 = 2 * math.pi * i / 48, 2 * math.pi * (i + 1) / 48
            p0 = rel((cx + isla_alba.R * math.cos(a0), top, cz + isla_alba.R * math.sin(a0)))
            p1 = rel((cx + isla_alba.R * math.cos(a1), top, cz + isla_alba.R * math.sin(a1)))
            tri += list(rel((cx, top, cz))) + list(p0) + list(p1)
            tri += list(p0) + list(p1) + list(rel((cx, top - isla_alba.DEPTH, cz)))
        gvbo = ctx.buffer(np.array(tri, 'f4').tobytes())
        gvao = ctx.vertex_array(ground, [(gvbo, '3f', 'Position')])
        ground['ModelViewMat'].write(mv)
        ground['ProjMat'].write(proj.T.tobytes())
        gvao.render()
        ctx.enable(moderngl.BLEND)
        ctx.blend_func = moderngl.SRC_ALPHA, moderngl.ONE
        # (en moderngl la máscara de profundidad va en el framebuffer y vale al usarlo)
        fbo.depth_mask = False
        fbo.use()
        inside = np.linalg.norm(cam - CENTER) < RADIUS
        vbo = ctx.buffer(SPHERE.tobytes())
        vao = ctx.vertex_array(veil, [(vbo, '3f', 'Position')])
        for k, v in (('STime', 1000.0 + t), ('Center', tuple(CENTER - cam)), ('Radius', RADIUS), ('Ripple', ripple),
                     ('Inside', 1.0 if inside else 0.0), ('Ground', GROUND), ('Alpha', 1.0)):
            if k in veil:
                veil[k].value = v
        veil['ModelViewMat'].write(mv)
        veil['ProjMat'].write(proj.T.tobytes())
        vao.render()
        # el corazón (mira a la cámara) y su columna (gira solo en vertical)
        inv = np.linalg.inv(view)
        right, up = inv[:3, 0], inv[:3, 1]
        hc = HEART - cam
        quads = []
        for (c, r, u, mode) in ((hc, right * HEART_HALF, up * HEART_HALF, 0.0),):
            quads.append((c, r, u, mode))
        hz = hc.copy()
        hz[1] = 0
        side = np.cross([0, 1, 0], hz / (np.linalg.norm(hz) + 1e-6))
        quads.append((hc + np.array([0, BEAM_H / 2, 0]), side * BEAM_W, np.array([0, BEAM_H / 2, 0]), 1.0))
        heart['ModelViewMat'].write(mv)
        heart['ProjMat'].write(proj.T.tobytes())
        for c, r, u, mode in quads:
            pts = [c - r - u, c + r - u, c + r + u, c - r + u]
            uvs = [(0, 0), (1, 0), (1, 1), (0, 1)]
            tri = []
            for i in (0, 1, 2, 0, 2, 3):
                tri += list(pts[i]) + list(uvs[i])
            hvbo = ctx.buffer(np.array(tri, 'f4').tobytes())
            hvao = ctx.vertex_array(heart, [(hvbo, '3f 2f', 'Position', 'UV0')])
            for k, v in (('STime', 1000.0 + t), ('Mode', mode), ('Alpha', 1.0)):
                if k in heart:
                    heart[k].value = v
            hvao.render()
        fbo.depth_mask = True
        fbo.use()
        im = np.frombuffer(fbo.read(components=3), np.uint8).reshape(h, w, 3)[::-1]
        return Image.fromarray(im.copy())

    views = [
        ('puente', CENTER + np.array([0, -3, 50]), np.array([0, 0.1, -1])),
        ('isla_principal', CENTER + np.array([30, 6, 130]), np.array([-0.2, -0.02, -1])),
        ('dentro', np.array([CENTER[0] + 4, isla_alba.TOP + 2.6, CENTER[2] + 12]), np.array([0, 0.25, -1])),
        ('dentro_arriba', np.array([CENTER[0] + 4, isla_alba.TOP + 2.6, CENTER[2] + 12]), np.array([0, 1.2, -0.6])),
    ]
    sheet = []
    for name, cam, fwd in views:
        for t in (0.0, 0.3):
            img = draw(cam, fwd, t)
            ImageDraw.Draw(img).text((8, 8), '%s t=%.1f' % (name, t), fill=(255, 255, 0))
            img.save(os.path.join(out, 'velo_%s_%.1f.png' % (name, t)))
            sheet.append(img)
    # la onda al cruzar: desde el puente, justo al entrar
    cam = CENTER + np.array([0, -3, 36])
    d = (cam - CENTER) / np.linalg.norm(cam - CENTER)
    for s in (0.1, 0.5, 1.0):
        img = draw(cam, np.array([0, 0.2, -1]), 0.0, (d[0], d[1], d[2], s))
        ImageDraw.Draw(img).text((8, 8), 'onda %.1f s' % s, fill=(255, 255, 0))
        img.save(os.path.join(out, 'velo_onda_%.1f.png' % s))
        sheet.append(img)
    cols = 3
    sw, shh = w // 2, h // 2
    rows = (len(sheet) + cols - 1) // cols
    big = Image.new('RGB', (sw * cols, shh * rows))
    for i, im in enumerate(sheet):
        big.paste(im.resize((sw, shh)), ((i % cols) * sw, (i // cols) * shh))
    big.save(os.path.join(out, 'velo_todo.png'))
    print('hecho en', out)


if __name__ == '__main__':
    main()
