"""Marcos para las fotos de la Cámara del TF Pad (1.3.155).

(El dueño: «agrega en el modo foto más marcos para publicar la foto, una extensa de marcos, me lo pidieron») Al
publicar una foto se elige un marco; se pega encima de la foto antes de subirla (PadCommunityClient), así todos lo ven en
Comunidad. Todos dibujados aquí con PIL (sin imágenes de nadie), 1600x900 con el centro transparente, y su miniatura
(256x144, sobre un paisaje de muestra) para el selector y una mediana (640x360, transparente) para verlo encima de la
foto antes de publicar.

    python3 tools/pad/marcos.py            escribe los PNG y marcos.json
    python3 tools/pad/marcos.py --ver dir  además, una hoja con todas las miniaturas

Salida: assets/tfclient/textures/gui/pad/marcos/<id>.png, marcos/mini/<id>.png y assets/tfclient/pad/marcos.json
({id, nombre, grupo} en el orden del selector).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
OUT = os.path.join(RES, 'textures', 'gui', 'pad', 'marcos')
MINI = os.path.join(OUT, 'mini')
MEDIO = os.path.join(OUT, 'medio')  # 640x360 con transparencia: la vista previa encima de la foto en la Cámara
INDEX = os.path.join(RES, 'pad', 'marcos.json')
FONT_TITAN = os.path.join(RES, 'font', 'titanone.ttf')
FONT_CINZEL = os.path.join(RES, 'font', 'cinzel.ttf')
W, H = 1600, 900


def rgba(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def new():
    return Image.new('RGBA', (W, H), (0, 0, 0, 0))


def ring_mask(t, tb=None, radius=0):
    """Máscara del borde: t a los lados y arriba, tb abajo."""
    tb = t if tb is None else tb
    m = Image.new('L', (W, H), 255)
    ImageDraw.Draw(m).rounded_rectangle([t, t, W - 1 - t, H - 1 - tb], radius=radius, fill=0)
    return m


def paint_ring(img, mask, fill):
    """Pinta el relleno (imagen RGBA o color) donde dice la máscara."""
    layer = fill if isinstance(fill, Image.Image) else Image.new('RGBA', (W, H), fill)
    img.paste(layer, (0, 0), mask)
    return img


def metal(c_hi, c_mid, c_lo, t):
    """Un borde de metal con bisel: claro fuera, oscuro dentro, en bandas suaves."""
    arr = np.zeros((H, W, 4), np.float32)
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.minimum.reduce([xx, yy, W - 1 - xx, H - 1 - yy]).astype(np.float32)
    f = np.clip(d / t, 0, 1)
    hi, mid, lo = (np.array(rgba(c)[:3], np.float32) for c in (c_hi, c_mid, c_lo))
    k = np.where(f < 0.5, f * 2, (f - 0.5) * 2)[..., None]
    col = np.where((f < 0.5)[..., None], hi * (1 - k) + mid * k, mid * (1 - k) + lo * k)
    shine = (0.12 * np.sin(xx / 35.0 + yy / 60.0))[..., None] * 255
    arr[..., :3] = np.clip(col + shine, 0, 255)
    arr[..., 3] = 255
    return Image.fromarray(arr.astype(np.uint8), 'RGBA')


def lines(img, insets, color, width=3):
    d = ImageDraw.Draw(img)
    for i in insets:
        d.rectangle([i, i, W - 1 - i, H - 1 - i], outline=color, width=width)


def corner_ornaments(img, color, size=70, inset=18):
    d = ImageDraw.Draw(img)
    for cx, cy, sx, sy in [(inset, inset, 1, 1), (W - 1 - inset, inset, -1, 1), (inset, H - 1 - inset, 1, -1),
                           (W - 1 - inset, H - 1 - inset, -1, -1)]:
        d.polygon([(cx, cy), (cx + sx * size, cy), (cx + sx * size * 0.35, cy + sy * size * 0.35), (cx, cy + sy * size)], fill=color)
        r = size * 0.18
        d.ellipse([cx + sx * size * 0.55 - r, cy + sy * size * 0.55 - r, cx + sx * size * 0.55 + r, cy + sy * size * 0.55 + r], fill=color)


def scatter(img, n, seed, drawer, band=120, avoid_center=True):
    """Pone n adornos por el borde (a menos de band px del canto)."""
    rng = np.random.default_rng(seed)
    d = ImageDraw.Draw(img)
    placed = 0
    while placed < n:
        x, y = rng.uniform(0, W), rng.uniform(0, H)
        edge = min(x, y, W - x, H - y)
        if avoid_center and edge > band:
            continue
        drawer(d, x, y, rng)
        placed += 1


def star(d, x, y, r, color, points=5):
    pts = []
    for i in range(points * 2):
        a = math.pi / points * i - math.pi / 2
        rr = r if i % 2 == 0 else r * 0.45
        pts.append((x + math.cos(a) * rr, y + math.sin(a) * rr))
    d.polygon(pts, fill=color)


def heart(d, x, y, r, color):
    d.ellipse([x - r, y - r * 0.6, x, y + r * 0.4], fill=color)
    d.ellipse([x, y - r * 0.6, x + r, y + r * 0.4], fill=color)
    d.polygon([(x - r * 0.98, y), (x + r * 0.98, y), (x, y + r * 1.15)], fill=color)


def flower(d, x, y, r, petal, center):
    for k in range(5):
        a = k * 2 * math.pi / 5
        px, py = x + math.cos(a) * r * 0.6, y + math.sin(a) * r * 0.6
        d.ellipse([px - r * 0.5, py - r * 0.5, px + r * 0.5, py + r * 0.5], fill=petal)
    d.ellipse([x - r * 0.3, y - r * 0.3, x + r * 0.3, y + r * 0.3], fill=center)


def leaf(d, x, y, r, color, ang):
    pts = []
    for i in range(20):
        t = i / 19 * math.pi
        lx, ly = math.cos(t) * r, math.sin(t) * r * 0.45
        pts.append((lx, ly))
    pts += [(lx, -ly) for lx, ly in reversed(pts)]
    ca, sa = math.cos(ang), math.sin(ang)
    d.polygon([(x + px * ca - py * sa, y + px * sa + py * ca) for px, py in pts], fill=color)


def snowflake(d, x, y, r, color):
    for k in range(6):
        a = k * math.pi / 3
        d.line([(x, y), (x + math.cos(a) * r, y + math.sin(a) * r)], fill=color, width=max(2, int(r / 6)))
        for f in (0.55,):
            bx, by = x + math.cos(a) * r * f, y + math.sin(a) * r * f
            for s in (-1, 1):
                b = a + s * math.pi / 4
                d.line([(bx, by), (bx + math.cos(b) * r * 0.3, by + math.sin(b) * r * 0.3)], fill=color, width=max(2, int(r / 8)))


def glow_lines(img, inset, color, width=6, blur=14, radius=30):
    layer = new()
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle([inset, inset, W - 1 - inset, H - 1 - inset], radius=radius, outline=color, width=width * 3)
    halo = layer.filter(ImageFilter.GaussianBlur(blur))
    core = new()
    ImageDraw.Draw(core).rounded_rectangle([inset, inset, W - 1 - inset, H - 1 - inset], radius=radius, outline=(255, 255, 255, 235),
                                           width=max(2, width // 2))
    out = Image.alpha_composite(img, halo)
    out = Image.alpha_composite(out, halo)
    return Image.alpha_composite(out, core)


def text(img, s, xy, size, color, font=FONT_TITAN, anchor='mm', stroke=0, stroke_color=(0, 0, 0, 255)):
    f = ImageFont.truetype(font, size)
    ImageDraw.Draw(img).text(xy, s, font=f, fill=color, anchor=anchor, stroke_width=stroke, stroke_fill=stroke_color)


# --- texturas de bloque pixeladas (dibujo propio, no las de Minecraft) ---
PX = 6  # tamaño de un «píxel»
BLOCK = 16 * PX


def block_tile(seed, palette, kind):
    rng = np.random.default_rng(seed)
    g = rng.integers(0, len(palette), (16, 16))
    if kind == 'planks':
        g = np.zeros((16, 16), int)
        for r in range(16):
            g[r, :] = 1 if r % 4 == 3 else (0 if rng.random() < 0.8 else 2)
        for r in range(0, 16, 4):
            c = rng.integers(2, 14)
            g[r:r + 3, c] = 1
    elif kind == 'bricks':
        g = np.zeros((16, 16), int)
        for r in range(16):
            if r % 4 == 3:
                g[r, :] = 1
            else:
                off = 0 if (r // 4) % 2 == 0 else 4
                for c in range(16):
                    g[r, c] = 1 if (c + off) % 8 == 7 else (0 if rng.random() < 0.85 else 2)
    elif kind == 'grass':
        g = rng.integers(2, len(palette), (16, 16))
        for c in range(16):
            depth = rng.integers(3, 6)
            g[:depth, c] = rng.integers(0, 2, depth)
    elif kind == 'gem':
        base = rng.integers(0, 2, (16, 16))
        for _ in range(5):
            x, y = rng.integers(2, 13, 2)
            base[y:y + 2, x:x + 2] = 2 + rng.integers(0, len(palette) - 2)
        g = base
    tile = Image.new('RGBA', (16, 16))
    tile.putdata([rgba(palette[v]) for v in g.flatten()])
    return tile.resize((BLOCK, BLOCK), Image.NEAREST)


def block_frame(palette, kind, seed, edge_dark=True):
    img = new()
    n = 0
    for x in range(0, W, BLOCK):
        for y in range(0, H, BLOCK):
            if x >= BLOCK and y >= BLOCK and x + BLOCK < W - BLOCK + BLOCK // 2 and y + BLOCK < H - BLOCK + BLOCK // 2:
                if x + BLOCK <= W - BLOCK and y + BLOCK <= H - BLOCK:
                    continue
            img.paste(block_tile(seed + n, palette, kind), (x, y))
            n += 1
    # recorte limpio: el borde tiene un bloque de grueso en los cuatro lados
    mask = ring_mask(BLOCK)
    clean = new()
    clean.paste(img, (0, 0), mask)
    if edge_dark:
        lines(clean, [BLOCK - 3], (0, 0, 0, 160), width=3)
    return clean


# ---------------------------------------------------------------------------------------------------------------------
# Los marcos
# ---------------------------------------------------------------------------------------------------------------------

def m_oro():
    img = paint_ring(new(), ring_mask(46), metal('#fff3b0', '#e2a72e', '#8a5a12', 46))
    lines(img, [6, 40], (110, 70, 10, 255), 3)
    corner_ornaments(img, rgba('#fff0a0'), 80, 10)
    return img


def m_plata():
    img = paint_ring(new(), ring_mask(46), metal('#ffffff', '#b9c3cf', '#5d6a78', 46))
    lines(img, [6, 40], (60, 70, 85, 255), 3)
    corner_ornaments(img, rgba('#ffffff'), 80, 10)
    return img


def m_bronce():
    img = paint_ring(new(), ring_mask(46), metal('#ffd2a0', '#c27a3a', '#6a3a14', 46))
    lines(img, [6, 40], (80, 40, 10, 255), 3)
    return img


def m_madera():
    rng = np.random.default_rng(7)
    arr = np.zeros((H, W, 4), np.uint8)
    yy, xx = np.mgrid[0:H, 0:W]
    grain = (np.sin(xx / 7.0 + np.sin(yy / 23.0) * 3 + rng.random() * 6) * 0.5 + 0.5)
    grain_v = (np.sin(yy / 7.0 + np.sin(xx / 23.0) * 3) * 0.5 + 0.5)
    vert = (np.minimum(xx, W - 1 - xx) < 60)
    g = np.where(vert, grain_v, grain)
    base = np.array([150, 96, 52])
    arr[..., :3] = np.clip(base[None, None, :] * (0.75 + 0.35 * g[..., None]), 0, 255)
    arr[..., 3] = 255
    img = paint_ring(new(), ring_mask(60), Image.fromarray(arr, 'RGBA'))
    lines(img, [2, 58], (60, 34, 14, 255), 4)
    return img


def m_marmol():
    from fondos import fbm  # mismo ruido que los fondos
    n = fbm(W, H, 9, octaves=6, base=4)
    veins = np.abs(np.sin(n * 18)) ** 0.15
    arr = np.zeros((H, W, 4), np.uint8)
    arr[..., :3] = np.clip(np.stack([235 * veins + 10, 232 * veins + 12, 228 * veins + 18], -1), 0, 255)
    arr[..., 3] = 255
    img = paint_ring(new(), ring_mask(52), Image.fromarray(arr, 'RGBA'))
    lines(img, [2, 50], (150, 140, 120, 255), 3)
    return img


def m_negro():
    img = paint_ring(new(), ring_mask(54), (18, 18, 22, 255))
    lines(img, [44], (214, 170, 72, 255), 3)
    return img


def m_galeria():
    img = paint_ring(new(), ring_mask(70), (250, 248, 242, 255))
    shadow = new()
    ImageDraw.Draw(shadow).rectangle([70, 70, W - 71, H - 71], outline=(0, 0, 0, 90), width=6)
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(6)))
    lines(img, [0], (200, 196, 186, 255), 4)
    return img


def m_polaroid():
    img = paint_ring(new(), ring_mask(40, 150), (252, 252, 250, 255))
    text(img, 'Tierras Fantásticas', (W // 2, H - 76), 46, (70, 70, 80, 255), font=FONT_CINZEL)
    return img


def m_pelicula():
    img = new()
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, 70], fill=(16, 16, 18, 255))
    d.rectangle([0, H - 70, W, H], fill=(16, 16, 18, 255))
    for x in range(20, W, 62):
        for y in (20, H - 50):
            d.rounded_rectangle([x, y, x + 34, y + 30], radius=6, fill=(0, 0, 0, 0))
    return img


def m_postal():
    img = paint_ring(new(), ring_mask(50), (250, 246, 236, 255))
    d = ImageDraw.Draw(img)
    for x in range(0, W, 30):  # dientes del sello
        for y in (0, H):
            d.ellipse([x - 9, y - 9, x + 9, y + 9], fill=(0, 0, 0, 0))
    for y in range(0, H, 30):
        for x in (0, W):
            d.ellipse([x - 9, y - 9, x + 9, y + 9], fill=(0, 0, 0, 0))
    lines(img, [44], (190, 60, 60, 255), 3)
    # matasellos
    d.ellipse([W - 260, H - 230, W - 70, H - 40], outline=(60, 60, 140, 200), width=5)
    text(img, 'TF', (W - 165, H - 135), 60, (60, 60, 140, 200), font=FONT_CINZEL)
    for i in range(4):
        d.arc([W - 420 - i * 30, H - 200, W - 240 - i * 30, H - 80], 200, 340, fill=(60, 60, 140, 160), width=4)
    return img


def m_vintage():
    yy, xx = np.mgrid[0:H, 0:W]
    dx, dy = (xx - W / 2) / (W / 2), (yy - H / 2) / (H / 2)
    r = np.sqrt(dx * dx + dy * dy)
    a = np.clip((r - 0.55) / 0.6, 0, 1) ** 1.6
    arr = np.zeros((H, W, 4), np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2] = 92, 60, 30
    arr[..., 3] = (a * 220).astype(np.uint8)
    img = Image.fromarray(arr, 'RGBA')
    m = Image.new('L', (W, H), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, W - 1, H - 1], radius=70, fill=255)
    corners = new()
    paint_ring(corners, Image.eval(m, lambda v: 255 - v), (245, 235, 215, 255))
    return Image.alpha_composite(img, corners)


def m_vineta():
    yy, xx = np.mgrid[0:H, 0:W]
    dx, dy = (xx - W / 2) / (W / 2), (yy - H / 2) / (H / 2)
    r = np.sqrt(dx * dx * 0.9 + dy * dy)
    a = np.clip((r - 0.6) / 0.7, 0, 1) ** 1.4
    arr = np.zeros((H, W, 4), np.uint8)
    arr[..., 3] = (a * 235).astype(np.uint8)
    return Image.fromarray(arr, 'RGBA')


def m_hierba():
    return block_frame(['#5fae3c', '#4c9a2e', '#8a5a34', '#76492a', '#9b6a40', '#6b3f22'], 'grass', 100)


def m_piedra():
    return block_frame(['#8a8a8a', '#7a7a7a', '#9a9a9a', '#6c6c6c', '#a4a4a4'], 'noise', 200)


def m_tablones():
    return block_frame(['#b07d48', '#7c5530', '#c08b52'], 'planks', 300)


def m_diamante():
    return block_frame(['#8f8f8f', '#7d7d7d', '#5ce6e0', '#2fc0c8', '#b8fff8'], 'gem', 400)


def m_esmeralda():
    return block_frame(['#8f8f8f', '#7d7d7d', '#3ddc6a', '#18a84a', '#a8ffc4'], 'gem', 500)


def m_oro_pixel():
    return block_frame(['#f6cf3a', '#e0ac22', '#ffe98a', '#c08814'], 'noise', 600)


def m_ladrillo():
    return block_frame(['#a9503c', '#d8cfc4', '#8e3f2f'], 'bricks', 700)


def m_hielo():
    img = block_frame(['#a8d8ff', '#8cc6f8', '#cdeaff', '#78b4ec'], 'noise', 800)
    a = np.asarray(img).copy()
    a[..., 3] = (a[..., 3] * 0.85).astype(np.uint8)
    return Image.fromarray(a, 'RGBA')


def m_enredadera():
    img = new()
    d = ImageDraw.Draw(img)
    rng = np.random.default_rng(31)
    for side in range(4):
        pts = []
        steps = 60
        for i in range(steps + 1):
            t = i / steps
            wob = math.sin(t * 25 + side) * 14
            if side == 0:
                pts.append((t * W, 30 + wob))
            elif side == 1:
                pts.append((t * W, H - 30 + wob))
            elif side == 2:
                pts.append((30 + wob, t * H))
            else:
                pts.append((W - 30 + wob, t * H))
        d.line(pts, fill=(70, 120, 50, 255), width=7)
        for x, y in pts[::2]:
            leaf(d, x + rng.uniform(-12, 12), y + rng.uniform(-12, 12), rng.uniform(16, 26), (rgba('#4fae4a') if rng.random() < 0.5 else rgba('#2f8f3a')),
                 rng.uniform(0, math.pi))
    scatter(img, 26, 32, lambda dd, x, y, r: flower(dd, x, y, r.uniform(10, 16), rgba('#ffffff'), rgba('#ffd84a')), band=55)
    return img


def m_flores():
    img = new()
    rng = np.random.default_rng(41)
    d = ImageDraw.Draw(img)
    cols = ['#ff7eb6', '#ffd24a', '#9e7bff', '#ff8a5c', '#5ad1ff']
    for cx, cy in [(0, 0), (W, 0), (0, H), (W, H)]:
        for _ in range(26):
            x, y = cx + rng.normal(0, 90) * (1 if cx == 0 else -1) * np.sign(rng.normal()) ** 2, cy + rng.normal(0, 70) * (1 if cy == 0 else -1)
            x, y = abs(x - cx) * (1 if cx == 0 else -1) + cx, abs(y - cy) * (1 if cy == 0 else -1) + cy
            leaf(d, x, y, rng.uniform(18, 30), rgba('#4caf50'), rng.uniform(0, math.pi))
        for _ in range(18):
            x, y = cx + abs(rng.normal(0, 85)) * (1 if cx == 0 else -1), cy + abs(rng.normal(0, 65)) * (1 if cy == 0 else -1)
            flower(d, x, y, rng.uniform(16, 30), rgba(cols[rng.integers(0, len(cols))]), rgba('#fff3a0'))
    return img


def m_hadas():
    img = new()
    layer = new()
    d = ImageDraw.Draw(layer)
    rng = np.random.default_rng(51)
    for _ in range(140):
        x, y = rng.uniform(0, W), rng.uniform(0, H)
        if min(x, y, W - x, H - y) > 110:
            continue
        r = rng.uniform(3, 9)
        c = rgba(['#ffb3e6', '#fff3a0', '#b3ecff', '#e0b3ff'][rng.integers(0, 4)])
        d.ellipse([x - r, y - r, x + r, y + r], fill=c)
    glowl = layer.filter(ImageFilter.GaussianBlur(10))
    img = Image.alpha_composite(img, glowl)
    img = Image.alpha_composite(img, glowl)
    img = Image.alpha_composite(img, layer)
    scatter(img, 30, 52, lambda dd, x, y, r: star(dd, x, y, r.uniform(8, 16), (255, 255, 255, 240), 4), band=90)
    return img


def m_runas():
    img = glow_lines(new(), 34, (120, 90, 255, 255), width=4, blur=16, radius=10)
    rng = np.random.default_rng(61)
    glyphs = 'ᚠᚢᚦᚨᚱᚲᚷᚹᚺᚾᛁᛃᛇᛈᛉᛊᛏᛒᛖᛗᛚᛜᛞᛟ'
    try:
        f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 34)
    except OSError:
        f = ImageFont.load_default()
    layer = new()
    d = ImageDraw.Draw(layer)
    for x in range(60, W - 40, 70):
        for y in (12, H - 52):
            d.text((x, y), glyphs[rng.integers(0, len(glyphs))], font=f, fill=(190, 170, 255, 255))
    for y in range(80, H - 60, 70):
        for x in (6, W - 40):
            d.text((x, y), glyphs[rng.integers(0, len(glyphs))], font=f, fill=(190, 170, 255, 255))
    img = Image.alpha_composite(img, layer.filter(ImageFilter.GaussianBlur(6)))
    return Image.alpha_composite(img, layer)


def m_cristales():
    img = new()
    d = ImageDraw.Draw(img)
    rng = np.random.default_rng(71)
    for cx, cy, sx, sy in [(0, 0, 1, 1), (W, 0, -1, 1), (0, H, 1, -1), (W, H, -1, -1)]:
        for _ in range(9):
            ang = rng.uniform(0.15, 1.4)
            length = rng.uniform(90, 220)
            wid = rng.uniform(18, 34)
            bx, by = cx + sx * rng.uniform(0, 70), cy + sy * rng.uniform(0, 70)
            dx, dy = math.cos(ang) * sx, math.sin(ang) * sy
            px, py = -dy, dx
            tip = (bx + dx * length, by + dy * length)
            col = rgba(['#9ae8ff', '#c79bff', '#7affd8'][rng.integers(0, 3)], 235)
            d.polygon([(bx + px * wid, by + py * wid), (bx - px * wid, by - py * wid), (tip[0] - px * wid * 0.4 - dx * 20, tip[1] - py * wid * 0.4 - dy * 20),
                       tip, (tip[0] + px * wid * 0.4 - dx * 20, tip[1] + py * wid * 0.4 - dy * 20)], fill=col)
            d.line([(bx, by), tip], fill=(255, 255, 255, 200), width=3)
    return img


def m_estrellas():
    img = new()
    scatter(img, 70, 81, lambda d, x, y, r: star(d, x, y, r.uniform(10, 26), rgba(['#ffd84a', '#ffffff', '#ffb84a'][r.integers(0, 3)])), band=80)
    return img


def m_corazones():
    img = new()
    scatter(img, 60, 91, lambda d, x, y, r: heart(d, x, y, r.uniform(14, 28), rgba(['#ff4d6d', '#ff8fab', '#ffb3c6'][r.integers(0, 3)])), band=80)
    return img


def m_neon(color):
    def f():
        return glow_lines(new(), 36, rgba(color), width=6, blur=16, radius=36)
    return f


def m_arcoiris():
    img = new()
    cols = ['#ff4d4d', '#ff9f43', '#ffd93d', '#5ad46e', '#4da6ff', '#9b6bff']
    for i, c in enumerate(cols):
        m = ring_mask(14 * (i + 1))
        inner = ring_mask(14 * i) if i else Image.new('L', (W, H), 0)
        band = Image.fromarray(np.clip(np.asarray(m, np.int16) - np.asarray(inner, np.int16) * 0, 0, 255).astype(np.uint8))
        layer = Image.new('RGBA', (W, H), rgba(c))
        mm = Image.fromarray(((np.asarray(m) > 0) & ~(np.asarray(ring_mask(14 * i)) > 0) if i else (np.asarray(m) > 0)).astype(np.uint8) * 255)
        img.paste(layer, (0, 0), mm)
    return img


def m_confeti():
    img = new()
    cols = ['#ff4d6d', '#ffd84a', '#4dd2ff', '#7cff6b', '#c47bff', '#ff9f43']

    def piece(d, x, y, r):
        c = rgba(cols[r.integers(0, len(cols))])
        if r.random() < 0.5:
            a = r.uniform(0, math.pi)
            l, w = r.uniform(10, 22), r.uniform(4, 8)
            pts = [(-l, -w), (l, -w), (l, w), (-l, w)]
            d.polygon([(x + px * math.cos(a) - py * math.sin(a), y + px * math.sin(a) + py * math.cos(a)) for px, py in pts], fill=c)
        else:
            s = r.uniform(5, 10)
            d.ellipse([x - s, y - s, x + s, y + s], fill=c)
    scatter(img, 260, 101, piece, band=120)
    text(img, '¡FELICIDADES!', (W // 2, 60), 64, rgba('#ffffff'), stroke=6, stroke_color=rgba('#ff4d6d'))
    return img


def m_fuegos():
    img = new()
    layer = new()
    d = ImageDraw.Draw(layer)
    rng = np.random.default_rng(111)
    for cx, cy in [(140, 120), (W - 160, 110), (180, H - 150), (W - 200, H - 140), (W // 2, 70)]:
        col = rgba(['#ff5e7e', '#ffd84a', '#5ee0ff', '#b07bff', '#7cff8a'][rng.integers(0, 5)])
        for k in range(28):
            a = k * 2 * math.pi / 28
            r1, r2 = 20, rng.uniform(70, 110)
            d.line([(cx + math.cos(a) * r1, cy + math.sin(a) * r1), (cx + math.cos(a) * r2, cy + math.sin(a) * r2)], fill=col, width=4)
            d.ellipse([cx + math.cos(a) * r2 - 5, cy + math.sin(a) * r2 - 5, cx + math.cos(a) * r2 + 5, cy + math.sin(a) * r2 + 5], fill=col)
    img = Image.alpha_composite(img, layer.filter(ImageFilter.GaussianBlur(8)))
    return Image.alpha_composite(img, layer)


def m_halloween():
    img = paint_ring(new(), ring_mask(34), (40, 20, 60, 230))
    d = ImageDraw.Draw(img)
    rng = np.random.default_rng(121)
    for x, y in [(80, H - 80), (W - 90, H - 85), (W // 2 - 300, H - 60), (W // 2 + 320, H - 64)]:
        r = rng.uniform(46, 62)
        for k in (-0.55, 0, 0.55):
            d.ellipse([x + k * r - r * 0.6, y - r * 0.8, x + k * r + r * 0.6, y + r * 0.8], fill=(255, 140, 26, 255))
        d.rectangle([x - 6, y - r * 0.95, x + 6, y - r * 0.7], fill=(60, 120, 40, 255))
        d.polygon([(x - r * 0.45, y - r * 0.2), (x - r * 0.2, y - r * 0.45), (x - r * 0.05, y - r * 0.2)], fill=(40, 20, 10, 255))
        d.polygon([(x + r * 0.45, y - r * 0.2), (x + r * 0.2, y - r * 0.45), (x + r * 0.05, y - r * 0.2)], fill=(40, 20, 10, 255))
        d.chord([x - r * 0.5, y - r * 0.2, x + r * 0.5, y + r * 0.5], 0, 180, fill=(40, 20, 10, 255))

    def bat(dd, x, y, r):
        s = r.uniform(16, 30)
        dd.polygon([(x, y), (x - s, y - s * 0.5), (x - s * 0.6, y), (x - s * 1.2, y + s * 0.1), (x - s * 0.5, y + s * 0.4), (x, y + s * 0.2),
                    (x + s * 0.5, y + s * 0.4), (x + s * 1.2, y + s * 0.1), (x + s * 0.6, y), (x + s, y - s * 0.5)], fill=(25, 10, 35, 255))
    scatter(img, 14, 122, bat, band=150)
    return img


def m_navidad():
    img = paint_ring(new(), ring_mask(30), (180, 30, 40, 240))
    lines(img, [10, 22], (255, 255, 255, 255), 4)
    d = ImageDraw.Draw(img)
    for cx, cy in [(70, 70), (W - 70, 70), (70, H - 70), (W - 70, H - 70)]:
        for k in range(3):
            a = k * 2 * math.pi / 3 + 0.5
            leaf(d, cx + math.cos(a) * 36, cy + math.sin(a) * 36, 40, rgba('#1f8a3a'), a)
        for k in range(3):
            d.ellipse([cx - 12 + k * 10 - 10, cy - 10, cx - 12 + k * 10 + 8, cy + 8], fill=(220, 20, 40, 255))
    scatter(img, 40, 131, lambda dd, x, y, r: snowflake(dd, x, y, r.uniform(10, 18), (255, 255, 255, 230)), band=100)
    return img


def m_nieve():
    img = new()
    scatter(img, 90, 141, lambda d, x, y, r: snowflake(d, x, y, r.uniform(8, 22), (255, 255, 255, 235)), band=120)
    d = ImageDraw.Draw(img)
    pts = [(0, H)] + [(x, H - 40 - 20 * math.sin(x / 90.0) - 10 * math.sin(x / 33.0)) for x in range(0, W + 1, 20)] + [(W, H)]
    d.polygon(pts, fill=(250, 252, 255, 255))
    return img


def m_primavera():
    img = new()
    scatter(img, 120, 151, lambda d, x, y, r: (d.ellipse([x - 7, y - 4, x + 7, y + 4], fill=rgba(['#ffb6d0', '#ffd1e0', '#ff9ec0'][r.integers(0, 3)]))), band=110)
    scatter(img, 24, 152, lambda d, x, y, r: flower(d, x, y, r.uniform(12, 20), rgba('#ffc1d8'), rgba('#ffe27a')), band=60)
    return img


def m_otono():
    img = new()
    cols = ['#e8692c', '#f2a33a', '#c94a2a', '#a8642a', '#f0c24a']
    scatter(img, 110, 161, lambda d, x, y, r: leaf(d, x, y, r.uniform(14, 26), rgba(cols[r.integers(0, len(cols))]), r.uniform(0, math.pi)), band=110)
    return img


def m_verano():
    img = new()
    d = ImageDraw.Draw(img)
    layer = new()
    ImageDraw.Draw(layer).ellipse([W - 260, -80, W + 60, 240], fill=(255, 210, 60, 255))
    img = Image.alpha_composite(img, layer.filter(ImageFilter.GaussianBlur(30)))
    img = Image.alpha_composite(img, layer)
    d = ImageDraw.Draw(img)
    for k, col in enumerate(['#4dc9ff', '#1fa2e6', '#0b7fc0']):
        y0 = H - 70 + k * 22
        pts = [(0, H)] + [(x, y0 + 14 * math.sin(x / 50.0 + k)) for x in range(0, W + 1, 10)] + [(W, H)]
        d.polygon(pts, fill=rgba(col, 240))
    return img


def m_rec():
    img = new()
    d = ImageDraw.Draw(img)
    c = (255, 255, 255, 235)
    l, m = 90, 40
    for x, y, sx, sy in [(m, m, 1, 1), (W - m, m, -1, 1), (m, H - m, 1, -1), (W - m, H - m, -1, -1)]:
        d.line([(x, y), (x + sx * l, y)], fill=c, width=8)
        d.line([(x, y), (x, y + sy * l)], fill=c, width=8)
    d.ellipse([70, 70, 104, 104], fill=(235, 40, 40, 255))
    text(img, 'REC', (120, 87), 40, c, anchor='lm')
    d.rectangle([W - 190, 74, W - 100, 104], outline=c, width=4)
    d.rectangle([W - 100, 82, W - 92, 96], fill=c)
    d.rectangle([W - 184, 80, W - 120, 98], fill=c)
    text(img, '00:00:21', (W - 90, H - 80), 36, c, anchor='rm')
    d.line([(W // 2 - 26, H // 2), (W // 2 + 26, H // 2)], fill=(255, 255, 255, 160), width=3)
    d.line([(W // 2, H // 2 - 26), (W // 2, H // 2 + 26)], fill=(255, 255, 255, 160), width=3)
    return img


def m_comic():
    img = paint_ring(new(), ring_mask(24), (10, 10, 10, 255))
    d = ImageDraw.Draw(img)
    for cx, cy, sx, sy in [(24, 24, 1, 1), (W - 24, H - 24, -1, -1)]:
        for i in range(12):
            for j in range(12 - i):
                r = max(2, 12 - (i + j))
                x, y = cx + sx * (i * 26 + 14), cy + sy * (j * 26 + 14)
                d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 70, 70, 255))
    burst = [(W - 260 + math.cos(k * math.pi / 10) * (120 if k % 2 == 0 else 75), 150 + math.sin(k * math.pi / 10) * (95 if k % 2 == 0 else 60))
             for k in range(20)]
    d.polygon(burst, fill=(255, 220, 40, 255), outline=(10, 10, 10, 255))
    text(img, '¡WOW!', (W - 260, 150), 56, (230, 30, 30, 255), stroke=4, stroke_color=(10, 10, 10, 255))
    return img


def m_tf():
    img = m_oro()
    d = ImageDraw.Draw(img)
    # cinta abajo con el nombre
    y = H - 100
    d.polygon([(W // 2 - 420, y), (W // 2 + 420, y), (W // 2 + 380, y + 40), (W // 2 + 420, y + 80), (W // 2 - 420, y + 80), (W // 2 - 380, y + 40)],
              fill=(120, 30, 40, 255))
    d.rectangle([W // 2 - 360, y + 6, W // 2 + 360, y + 74], fill=(160, 40, 52, 255))
    text(img, 'TIERRAS FANTÁSTICAS', (W // 2, y + 40), 48, (255, 232, 160, 255), font=FONT_CINZEL)
    return img


def m_tf_noche():
    img = paint_ring(new(), ring_mask(40), (14, 18, 52, 255))
    lines(img, [8, 34], (214, 170, 72, 255), 3)
    scatter(img, 60, 171, lambda d, x, y, r: star(d, x, y, r.uniform(4, 9), (255, 240, 190, 255), 4), band=36)
    text(img, 'TIERRAS FANTÁSTICAS', (W // 2, H - 20), 30, (255, 232, 160, 255), font=FONT_CINZEL)
    return img


MARCOS = [
    ('oro', 'Oro', 'Clásicos', m_oro),
    ('plata', 'Plata', 'Clásicos', m_plata),
    ('bronce', 'Bronce', 'Clásicos', m_bronce),
    ('madera', 'Madera', 'Clásicos', m_madera),
    ('marmol', 'Mármol', 'Clásicos', m_marmol),
    ('negro', 'Negro y oro', 'Clásicos', m_negro),
    ('galeria', 'Galería', 'Clásicos', m_galeria),
    ('polaroid', 'Instantánea', 'Clásicos', m_polaroid),
    ('pelicula', 'Película', 'Clásicos', m_pelicula),
    ('postal', 'Postal', 'Clásicos', m_postal),
    ('vintage', 'Vintage', 'Clásicos', m_vintage),
    ('vineta', 'Viñeta', 'Clásicos', m_vineta),
    ('hierba', 'Hierba', 'Bloques', m_hierba),
    ('piedra', 'Piedra', 'Bloques', m_piedra),
    ('tablones', 'Tablones', 'Bloques', m_tablones),
    ('diamante', 'Diamante', 'Bloques', m_diamante),
    ('esmeralda', 'Esmeralda', 'Bloques', m_esmeralda),
    ('oro_pixel', 'Bloque de oro', 'Bloques', m_oro_pixel),
    ('ladrillo', 'Ladrillo', 'Bloques', m_ladrillo),
    ('hielo', 'Hielo', 'Bloques', m_hielo),
    ('enredadera', 'Enredadera', 'Fantasía', m_enredadera),
    ('flores', 'Flores', 'Fantasía', m_flores),
    ('hadas', 'Hadas', 'Fantasía', m_hadas),
    ('runas', 'Runas', 'Fantasía', m_runas),
    ('cristales', 'Cristales', 'Fantasía', m_cristales),
    ('estrellas', 'Estrellas', 'Fantasía', m_estrellas),
    ('corazones', 'Corazones', 'Fantasía', m_corazones),
    ('neon_rosa', 'Neón rosa', 'Neón', m_neon('#ff4fd8')),
    ('neon_azul', 'Neón azul', 'Neón', m_neon('#3fb6ff')),
    ('neon_verde', 'Neón verde', 'Neón', m_neon('#46ff8a')),
    ('neon_oro', 'Neón dorado', 'Neón', m_neon('#ffc83f')),
    ('arcoiris', 'Arcoíris', 'Neón', m_arcoiris),
    ('confeti', 'Cumpleaños', 'Fiestas', m_confeti),
    ('fuegos', 'Fuegos artificiales', 'Fiestas', m_fuegos),
    ('halloween', 'Halloween', 'Fiestas', m_halloween),
    ('navidad', 'Navidad', 'Fiestas', m_navidad),
    ('nieve', 'Nieve', 'Temporadas', m_nieve),
    ('primavera', 'Primavera', 'Temporadas', m_primavera),
    ('otono', 'Otoño', 'Temporadas', m_otono),
    ('verano', 'Verano', 'Temporadas', m_verano),
    ('rec', 'Cámara REC', 'Otros', m_rec),
    ('comic', 'Cómic', 'Otros', m_comic),
    ('tf', 'Tierras Fantásticas', 'Otros', m_tf),
    ('tf_noche', 'Noche de Tierras', 'Otros', m_tf_noche),
]


def sample_scene(w, h):
    """El paisaje de muestra para las miniaturas."""
    sys.path.insert(0, HERE)
    import fondos
    im = Image.open(os.path.join(os.path.dirname(OUT), 'fondos', 'bosque.png')).convert('RGB')
    return im.resize((w, h), Image.LANCZOS)


def main():
    sys.path.insert(0, HERE)
    os.makedirs(MINI, exist_ok=True)
    os.makedirs(MEDIO, exist_ok=True)
    bg = sample_scene(256, 144)
    keep = set()
    for mid, _, _, fn in MARCOS:
        im = fn()
        im.save(os.path.join(OUT, f'{mid}.png'), optimize=True)
        im.resize((640, 360), Image.LANCZOS).save(os.path.join(MEDIO, f'{mid}.png'), optimize=True)
        thumb = bg.copy().convert('RGBA')
        thumb.alpha_composite(im.resize((256, 144), Image.LANCZOS))
        thumb.convert('RGB').save(os.path.join(MINI, f'{mid}.png'), optimize=True)
        keep.add(mid)
    os.makedirs(os.path.dirname(INDEX), exist_ok=True)
    with open(INDEX, 'w', encoding='utf-8') as f:
        json.dump([{'id': i, 'nombre': n, 'grupo': g} for i, n, g, _ in MARCOS], f, ensure_ascii=False, indent=1)
        f.write('\n')
    if '--ver' in sys.argv:
        dest = sys.argv[sys.argv.index('--ver') + 1]
        os.makedirs(dest, exist_ok=True)
        cols = 6
        sheet = Image.new('RGB', (cols * 260, math.ceil(len(MARCOS) / cols) * 148), 'white')
        for i, (mid, _, _, _) in enumerate(MARCOS):
            sheet.paste(Image.open(os.path.join(MINI, f'{mid}.png')), ((i % cols) * 260 + 2, (i // cols) * 148 + 2))
        sheet.save(os.path.join(dest, 'marcos.png'))
    total = sum(os.path.getsize(os.path.join(d, f'{m}.png')) for m in keep for d in (OUT, MINI, MEDIO))
    print(f'{len(MARCOS)} marcos, {total // 1024} KB')


if __name__ == '__main__':
    main()
