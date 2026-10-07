"""Dibuja un modelo de VFX ya convertido (vfx_bb.convert) en una imagen, con la misma pose que el mod.

Sirve para revisar que la conversión está bien (giros, UV, animación) sin abrir el juego y para las miniaturas de la
web. Rasterizador sencillo con numpy: caras traseras fuera (como en Minecraft), texturas con su fotograma, píxeles
con alfa < 0,1 descartados y mezcla de transparencias de atrás hacia delante.
"""
import math
import os

import numpy as np
from PIL import Image

import vfx_bb as B


def load_textures(model, tex_dir):
    out = []
    for t in model['tex']:
        path = os.path.join(tex_dir, t['path'].split('/')[-1])
        out.append(np.asarray(Image.open(path).convert('RGBA'), dtype=np.float32) / 255.0)
    return out


def look_matrix(yaw_deg, pitch_deg):
    """Giro de la cámara: primero yaw (alrededor de Y), luego pitch (alrededor de X)."""
    y, p = math.radians(yaw_deg), math.radians(pitch_deg)
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rx = np.array([[1, 0, 0], [0, math.cos(p), -math.sin(p)], [0, math.sin(p), math.cos(p)]])
    return rx @ ry


def render(model, textures, anim, t, size=512, yaw=-30, pitch=-12, dist=None, center=None, fov=40, tick=0,
           hidden=None, ss=2, skin=None, actor=None):
    """Imagen RGBA del modelo en el instante t (s). La cámara mira la cara delantera (-Z) del modelo.
    actor: skin de 64×64 de un mob que hace la animación en lugar del muñeco (como en el juego)."""
    mats = B.pose(model, anim, t)
    tris = []  # (z, pts3 (3x3), uvs (3x2), tex, normal)
    flag = lambda q: int(q[24]) if len(q) > 24 else 0
    for bi, b in enumerate(model['bones']):
        if b['hidden'] or (hidden and b['id'] in hidden):
            continue
        m = np.array(mats[bi])
        for q in b['quads']:
            ti = q[0]
            if actor is not None:
                # El muñeco gris lo hace el mob (con su skin); las piezas de material se quedan como en el juego
                if ti == -3:
                    if not flag(q) & 4:
                        continue
                elif flag(q) & 2 and (ti < 0 or model['tex'][ti]['body']):
                    continue
            elif ti == -3:
                continue
            if ti < 0 and ti != -3 and (skin is None or ti == -2 or any(qq[0] >= 0 for qq in b['quads'])):
                continue  # cabeza con skin: en las miniaturas va la cabeza del modelo (o una gris si no tiene)
            n = m[:3, :3] @ np.array(q[1:4])
            vs = np.array(q[4:24]).reshape(4, 5)
            pts = (m[:3, :3] @ vs[:, :3].T).T + m[:3, 3]
            tex = model['tex'][ti] if ti >= 0 else {'ft': 1, 'frames': 1, 'e': False}
            fr = (tick // tex['ft']) % tex['frames'] if tex['frames'] > 1 else 0
            uv = vs[:, 3:5].copy()
            uv[:, 1] = (uv[:, 1] + fr) / tex['frames']
            for a, b2, c in ((0, 1, 2), (0, 2, 3)):
                tris.append((pts[[a, b2, c]], uv[[a, b2, c]], ti, n))
    if not tris:
        return Image.new('RGBA', (size, size))
    allp = np.concatenate([tr[0] for tr in tris])
    if center is None:
        center = (allp.min(0) + allp.max(0)) / 2
    rot = look_matrix(yaw, pitch)
    # La cámara está delante del modelo (en -Z) mirando hacia +Z
    if dist is None:
        rad = np.linalg.norm(allp - center, axis=1).max()
        dist = rad / math.tan(math.radians(fov / 2)) * 1.15
    S = size * ss
    f = S / 2 / math.tan(math.radians(fov / 2))
    color = np.zeros((S, S, 4), np.float32)
    zbuf = np.full((S, S), np.inf, np.float32)
    proj = []
    for pts, uv, ti, n in tris:
        cam = (rot @ (pts - center).T).T
        cam[:, 2] = cam[:, 2] + dist  # profundidad: delante = positivo
        # Mirando hacia +Z desde -Z: la derecha de la imagen es -X del modelo (como Blockbench desde el norte)
        nc = rot @ n
        if nc[2] >= 0:  # la cara mira hacia el lado contrario de la cámara
            continue
        if (cam[:, 2] <= 1).any():
            continue
        sx = S / 2 - cam[:, 0] * f / cam[:, 2]
        sy = S / 2 - cam[:, 1] * f / cam[:, 2]
        proj.append((cam[:, 2].mean(), np.stack([sx, sy], 1), cam[:, 2], uv, ti, nc))
    proj.sort(key=lambda p: -p[0])
    light = np.array([0.2, 1.0, -0.7])
    light /= np.linalg.norm(light)
    for _, sp, z, uv, ti, nc in proj:
        x0, y0 = np.floor(sp.min(0)).astype(int)
        x1, y1 = np.ceil(sp.max(0)).astype(int)
        x0, y0 = max(x0, 0), max(y0, 0)
        x1, y1 = min(x1, S - 1), min(y1, S - 1)
        if x0 > x1 or y0 > y1:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        (ax, ay), (bx, by), (cx, cy) = sp
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            continue
        w0 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den
        w1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
        w2 = 1 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not inside.any():
            continue
        iz = w0 / z[0] + w1 / z[1] + w2 / z[2]
        u = (w0 * uv[0, 0] / z[0] + w1 * uv[1, 0] / z[1] + w2 * uv[2, 0] / z[2]) / iz
        v = (w0 * uv[0, 1] / z[0] + w1 * uv[1, 1] / z[1] + w2 * uv[2, 1] / z[2]) / iz
        tex = textures[ti] if ti >= 0 else actor if ti == -3 else skin
        th, tw = tex.shape[:2]
        tu = np.clip((u * tw).astype(int), 0, tw - 1)
        tv = np.clip((v * th).astype(int), 0, th - 1)
        px = tex[tv, tu]
        depth = 1 / iz
        ok = inside & (px[..., 3] >= 0.1) & (depth < zbuf[y0:y1 + 1, x0:x1 + 1])
        if not ok.any():
            continue
        shade = 1.0 if ti >= 0 and model['tex'][ti]['e'] else 0.6 + 0.4 * max(0.0, float(-(nc @ light)))
        src = px[..., :3] * shade
        a = px[..., 3:4]
        dst = color[y0:y1 + 1, x0:x1 + 1]
        okm = ok[..., None]
        out_rgb = src * a + dst[..., :3] * (1 - a)
        out_a = a + dst[..., 3:4] * (1 - a)
        dst[..., :3] = np.where(okm, out_rgb, dst[..., :3])
        dst[..., 3:4] = np.where(okm, out_a, dst[..., 3:4])
        # Solo lo casi opaco tapa lo de detrás (como el depth write con transparencia)
        zb = zbuf[y0:y1 + 1, x0:x1 + 1]
        zbuf[y0:y1 + 1, x0:x1 + 1] = np.where(ok & (px[..., 3] > 0.9), depth, zb)
    img = Image.fromarray((np.clip(color, 0, 1) * 255).astype(np.uint8), 'RGBA')
    return img.resize((size, size), Image.LANCZOS) if ss > 1 else img


def bounds(model, anim, times):
    """Caja que ocupa el modelo a lo largo de varios instantes (para encuadrar igual todos los fotogramas)."""
    lo, hi = np.full(3, np.inf), np.full(3, -np.inf)
    for t in times:
        mats = B.pose(model, anim, t)
        for bi, b in enumerate(model['bones']):
            if b['hidden']:
                continue
            m = np.array(mats[bi])
            for q in b['quads']:
                if q[0] < 0:
                    continue
                vs = np.array(q[4:24]).reshape(4, 5)[:, :3]
                pts = (m[:3, :3] @ vs.T).T + m[:3, 3]
                lo = np.minimum(lo, pts.min(0))
                hi = np.maximum(hi, pts.max(0))
    return lo, hi
