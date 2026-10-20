"""Vista previa del santuario de las crates, sin abrir el juego (bloques de colores, sin texturas).

    xvfb-run -a python3 -I tools/crates/santuario_preview.py <carpeta de salida> [ticks...]

Dibuja la escena de santuario.py desde la cámara de la cinemática (la misma ruta, con Catmull-Rom, que
CrateCinematicScreen) para ver el encuadre: qué tapa qué, si se ve el borde de la escena, dónde cae el sol.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import santuario as S  # noqa: E402

COLORS = {
    'grass_block': (0.42, 0.66, 0.30), 'dirt': (0.52, 0.37, 0.25), 'coarse_dirt': (0.47, 0.34, 0.24),
    'podzol': (0.45, 0.32, 0.18), 'rooted_dirt': (0.55, 0.40, 0.30), 'moss_block': (0.36, 0.55, 0.20),
    'moss_carpet': (0.36, 0.55, 0.20), 'stone_bricks': (0.55, 0.55, 0.55), 'mossy_stone_bricks': (0.45, 0.52, 0.40),
    'cracked_stone_bricks': (0.50, 0.50, 0.50), 'chiseled_stone_bricks': (0.60, 0.60, 0.60),
    'mossy_cobblestone': (0.42, 0.48, 0.38), 'cobblestone': (0.48, 0.48, 0.48), 'polished_andesite': (0.55, 0.57, 0.56),
    'calcite': (0.88, 0.88, 0.86), 'clay': (0.62, 0.64, 0.70), 'gravel': (0.52, 0.49, 0.47), 'stone': (0.47, 0.47, 0.47),
    'stone_brick_stairs': (0.55, 0.55, 0.55), 'mossy_stone_brick_stairs': (0.45, 0.52, 0.40),
    'stone_brick_slab': (0.56, 0.56, 0.56), 'mossy_stone_brick_slab': (0.45, 0.52, 0.40),
    'stone_brick_wall': (0.55, 0.55, 0.55), 'mossy_stone_brick_wall': (0.45, 0.52, 0.40),
    'cherry_log': (0.30, 0.18, 0.20), 'cherry_leaves': (0.95, 0.67, 0.80), 'oak_log': (0.42, 0.33, 0.20),
    'oak_leaves': (0.30, 0.52, 0.16), 'dark_oak_log': (0.25, 0.19, 0.11), 'dark_oak_leaves': (0.24, 0.42, 0.13),
    'birch_log': (0.85, 0.84, 0.80), 'birch_leaves': (0.45, 0.62, 0.30), 'azalea_leaves': (0.38, 0.55, 0.20),
    'flowering_azalea_leaves': (0.55, 0.50, 0.55), 'water': (0.25, 0.55, 0.90), 'lantern': (0.95, 0.75, 0.35),
    'chain': (0.25, 0.25, 0.30), 'vine': (0.25, 0.48, 0.12), 'cave_vines': (0.30, 0.50, 0.15),
    'cave_vines_plant': (0.30, 0.50, 0.15), 'lily_pad': (0.20, 0.50, 0.15), 'pink_petals': (0.97, 0.70, 0.82),
    'grass': (0.45, 0.70, 0.30), 'tall_grass': (0.45, 0.70, 0.30), 'fern': (0.38, 0.62, 0.28),
    'large_fern': (0.38, 0.62, 0.28), 'poppy': (0.85, 0.15, 0.10), 'dandelion': (0.95, 0.85, 0.15),
    'oxeye_daisy': (0.95, 0.95, 0.85), 'azure_bluet': (0.85, 0.90, 0.95), 'cornflower': (0.35, 0.45, 0.90),
    'allium': (0.70, 0.45, 0.90), 'lily_of_the_valley': (0.95, 0.95, 0.95), 'blue_orchid': (0.20, 0.65, 0.95),
    'pink_tulip': (0.95, 0.65, 0.80), 'white_tulip': (0.95, 0.95, 0.95), 'peony': (0.90, 0.70, 0.90),
    'rose_bush': (0.80, 0.15, 0.20), 'lilac': (0.80, 0.60, 0.90), 'brown_mushroom': (0.60, 0.45, 0.35),
    'red_mushroom': (0.85, 0.20, 0.15), 'azalea': (0.40, 0.58, 0.22), 'flowering_azalea': (0.60, 0.50, 0.60),
}
FLAT = {'moss_carpet': 0.06, 'pink_petals': 0.06, 'lily_pad': 0.03}
CROSS = {'grass', 'tall_grass', 'fern', 'large_fern', 'poppy', 'dandelion', 'oxeye_daisy', 'azure_bluet', 'cornflower',
         'allium', 'lily_of_the_valley', 'blue_orchid', 'pink_tulip', 'white_tulip', 'peony', 'rose_bush', 'lilac',
         'brown_mushroom', 'red_mushroom', 'cave_vines', 'cave_vines_plant', 'chain'}
SKY_TOP, SKY_HOR = np.array([0.42, 0.60, 0.86]), np.array([1.0, 0.86, 0.64])
FOG = np.array([0.93, 0.85, 0.68])


def box(x0, y0, z0, x1, y1, z1):
    """Las 6 caras (triángulos) de una caja, con su normal."""
    v = []
    faces = [((1, 0, 0), [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)]),
             ((-1, 0, 0), [(x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (x0, y0, z0)]),
             ((0, 1, 0), [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)]),
             ((0, -1, 0), [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)]),
             ((0, 0, 1), [(x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (x0, y0, z1)]),
             ((0, 0, -1), [(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)])]
    for n, q in faces:
        for k in (0, 1, 2, 0, 2, 3):
            v.append((q[k], n))
    return v


def mesh(sc):
    tris = []
    a = sc.a
    full = np.zeros(len(sc.pal), bool)
    for i, s in enumerate(sc.pal):
        n = s.split('[')[0].split(':')[1]
        full[i] = i != 0 and n in COLORS and n not in FLAT and n not in CROSS and not any(
            k in n for k in ('slab', 'stairs', 'wall', 'lantern', 'vine', 'water', 'leaves', 'azalea'))
    for (i, j, k) in np.argwhere(a != 0):
        st = sc.pal[a[i, j, k]]
        n = st.split('[')[0].split(':')[1]
        col = COLORS.get(n, (1.0, 0.0, 1.0))
        x, y, z = i + S.X0, j + S.Y0, k + S.Z0
        if n == 'water':
            tris += [(p, nn, col, 0.75) for p, nn in box(x, y + 0.86, z, x + 1, y + 0.875, z + 1)[12:18]]
            continue
        if n in FLAT:
            h = FLAT[n]
            tris += [(p, nn, col, 1.0) for p, nn in box(x, y, z, x + 1, y + h, z + 1)]
            continue
        if n in CROSS:
            h = 0.8 if n not in ('tall_grass', 'large_fern', 'peony', 'rose_bush', 'lilac') else 1.0
            for (ax, az, bx, bz) in ((0.15, 0.15, 0.85, 0.85), (0.85, 0.15, 0.15, 0.85)):
                q = [(x + ax, y, z + az), (x + ax, y + h, z + az), (x + bx, y + h, z + bz), (x + bx, y, z + bz)]
                for kk in (0, 1, 2, 0, 2, 3, 0, 2, 1, 0, 3, 2):
                    tris.append((q[kk], (0, 1, 0), col, 1.0))
            continue
        if 'slab' in n:
            kind = 'top' if 'type=top' in st else ('double' if 'type=double' in st else 'bottom')
            y0, y1 = (0.5, 1.0) if kind == 'top' else ((0, 1) if kind == 'double' else (0, 0.5))
            tris += [(p, nn, col, 1.0) for p, nn in box(x, y + y0, z, x + 1, y + y1, z + 1)]
            continue
        if 'wall' in n:
            tris += [(p, nn, col, 1.0) for p, nn in box(x + 0.25, y, z + 0.25, x + 0.75, y + 1, z + 0.75)]
            continue
        if 'lantern' in n:
            tris += [(p, nn, col, 1.0) for p, nn in box(x + 0.3, y, z + 0.3, x + 0.7, y + 0.55, z + 0.7)]
            continue
        if n == 'vine':
            side = [s for s in ('north', 'south', 'east', 'west') if f'{s}=true' in st]
            for s in side:
                b = {'north': (0, 0, 1, 0.05), 'south': (0, 0.95, 1, 1), 'west': (0, 0, 0.05, 1), 'east': (0.95, 0, 1, 1)}[s]
                tris += [(p, nn, col, 1.0) for p, nn in box(x + b[0], y, z + b[1], x + b[2], y + 1, z + b[3])]
            continue
        # cubos: solo las caras que se ven
        for f, (dx, dy, dz) in enumerate(((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            ii, jj, kk = i + dx, j + dy, k + dz
            if 0 <= ii < S.NX and 0 <= jj < S.NY and 0 <= kk < S.NZ and full[a[ii, jj, kk]]:
                continue
            tris += [(p, nn, col, 1.0) for p, nn in box(x, y, z, x + 1, y + 1, z + 1)[f * 6:f * 6 + 6]]
    # la crate (un cubo dorado) y la ruleta (un anillo de cubitos), para ver dónde quedan
    cx, cy, cz = S.CRATE
    tris += [(p, nn, (0.85, 0.62, 0.2), 1.0) for p, nn in box(cx - 0.5, cy, cz - 0.45, cx + 0.5, cy + 1.0, cz + 0.45)]
    for k in range(12):
        a = k / 12 * math.tau
        x, z, y = cx + math.cos(a) * 1.6, cz + math.sin(a) * 1.6, cy + 1.85
        tris += [(p, nn, (0.9, 0.2, 0.9), 1.0) for p, nn in box(x - 0.2, y - 0.2, z - 0.2, x + 0.2, y + 0.2, z + 0.2)]
    return tris


camera_at = S.camera_at


def look_at(eye, at):
    f = at - eye
    f /= np.linalg.norm(f)
    r = np.cross(f, [0, 1, 0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    m = np.eye(4)
    m[0, :3], m[1, :3], m[2, :3] = r, u, -f
    m[:3, 3] = -m[:3, :3] @ eye
    return m


def perspective(fov, aspect, near, far):
    f = 1 / math.tan(math.radians(fov) / 2)
    return np.array([[f / aspect, 0, 0, 0], [0, f, 0, 0], [0, 0, (far + near) / (near - far), 2 * far * near / (near - far)],
                     [0, 0, -1, 0]], 'f4')


VS = """
#version 330
uniform mat4 mvp; uniform mat4 mv;
in vec3 pos; in vec3 nrm; in vec4 col;
out vec3 n; out vec4 c; out float dist; out vec3 wp;
void main(){ gl_Position = mvp*vec4(pos,1.0); n = nrm; c = col; dist = length((mv*vec4(pos,1.0)).xyz); wp = pos; }
"""
FS = """
#version 330
uniform vec3 sun; uniform vec3 fog; uniform vec3 crate;
in vec3 n; in vec4 c; in float dist; in vec3 wp; out vec4 o;
void main(){
  float l = 0.55 + 0.45*max(dot(normalize(n), sun), 0.0);
  // el haz de sol sobre el altar: más luz cerca de su eje
  vec3 d = wp - crate; float along = dot(d, sun); float off = length(d - sun*along);
  float beam = exp(-off*off/5.0);
  vec3 col = c.rgb * l * (0.72 + 0.4*beam);
  float f = clamp((dist-18.0)/(52.0-18.0), 0.0, 1.0);
  o = vec4(mix(col, fog, f*0.85), c.a);
}
"""


def main():
    out = sys.argv[1]
    ticks = [int(t) for t in sys.argv[2:]] or [0, 30, 62, 96, 116, 138, 166, 230, 300, 400]
    os.makedirs(out, exist_ok=True)
    import moderngl
    sc = S.build()
    tris = mesh(sc)
    data = np.array([(*p, *n, *c, al) for p, n, c, al in tris], 'f4')
    W, H = 960, 540
    ctx = moderngl.create_standalone_context()
    prog = ctx.program(vertex_shader=VS, fragment_shader=FS)
    vbo = ctx.buffer(data.tobytes())
    vao = ctx.vertex_array(prog, [(vbo, '3f 3f 4f', 'pos', 'nrm', 'col')])
    fbo = ctx.framebuffer(color_attachments=[ctx.texture((W, H), 4)], depth_attachment=ctx.depth_texture((W, H)))
    prog['sun'].value = tuple(S.SUN)
    prog['fog'].value = tuple(FOG)
    prog['crate'].value = S.CRATE
    for tick in ticks:
        eye, at, fov = camera_at(tick)
        mv = look_at(eye, at)
        p = perspective(fov, W / H, 0.05, 300)
        fbo.use()
        ctx.enable(moderngl.DEPTH_TEST | moderngl.BLEND)
        ctx.clear(0, 0, 0, 0)
        prog['mvp'].write((p @ mv).astype('f4').T.tobytes())
        prog['mv'].write(mv.astype('f4').T.tobytes())
        vao.render()
        img = np.frombuffer(fbo.color_attachments[0].read(), np.uint8).reshape(H, W, 4)[::-1].astype(float) / 255
        # el cielo detrás
        yy = np.linspace(0, 1, H)[:, None, None]
        sky = SKY_TOP * (1 - yy) + SKY_HOR * yy
        rgb = img[..., :3] * img[..., 3:] + sky * (1 - img[..., 3:])
        # la crate (un cubo dorado) para ver dónde queda
        Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8)).save(os.path.join(out, f'cam_{tick:03d}.png'))
        print('cam', tick, np.round(eye, 2), np.round(at, 2), fov)


if __name__ == '__main__':
    main()
