"""Genera las texturas de los botones del menú: placa de piedra de templo con marco dorado redondeado,
medallones con gema en los extremos y unas ramitas de hojas discretas en las esquinas.

Cada PNG lleva tres estados apilados (normal, ratón encima, desactivado). Resolución: 4 píxeles de textura por
píxel de interfaz de Minecraft (botón de 200x20 -> 800x80), así a 1080p se ve 1:1 y nítido.
Se dibuja a 4x más y se reduce con Lanczos para que los bordes queden suaves.
Uso: python3 tools/gen_buttons.py
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "tfclient", "textures", "gui")
SS = 4          # supermuestreo
SCALE = 4       # texels por píxel de interfaz
GUI_H = 20


def rounded(draw, box, r, fill):
    draw.rounded_rectangle(box, radius=r, fill=fill)


def vertical_gradient(w, h, top, bottom):
    g = Image.new("RGBA", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        g.putpixel((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
    return g.resize((w, h))


def stone_texture(w, h, seed, warm):
    rng = random.Random(seed)
    tex = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(tex)
    # Bloques tallados sutiles y motas
    block_w = h * 1.6
    row_h = h / 2
    for row in range(2):
        offset = 0 if row == 0 else block_w / 2
        x = -offset
        while x < w:
            y0, y1 = row * row_h, (row + 1) * row_h
            d.line([(x, y0), (x, y1)], fill=(20, 16, 10, 70), width=max(1, SS * 2))
            x += block_w
        d.line([(0, row_h), (w, row_h)], fill=(20, 16, 10, 60), width=max(1, SS * 2))
    for _ in range(w * h // (SS * SS * 40)):
        x, y = rng.randrange(w), rng.randrange(h)
        r = rng.randint(SS, SS * 3)
        c = rng.choice([(255, 240, 210, 18), (0, 0, 0, 26), (255, 230, 190, 12)])
        d.ellipse([x - r, y - r, x + r, y + r], fill=c)
    return tex.filter(ImageFilter.GaussianBlur(SS * 0.6))


def leaf(d, x, y, angle, size, color, vein):
    pts = []
    for t in range(0, 21):
        a = t / 20 * math.pi
        rx = size * math.sin(a) * 0.42
        ry = size * (t / 20)
        pts.append((rx, ry))
    pts += [(-px, py) for px, py in reversed(pts)]
    ca, sa = math.cos(angle), math.sin(angle)
    poly = [(x + px * ca - py * sa, y + px * sa + py * ca) for px, py in pts]
    d.polygon(poly, fill=color)
    d.line([(x, y), (x - size * sa * 0.9, y + size * ca * 0.9)], fill=vein, width=max(1, SS))


def sprig(d, x, y, direction, rng, scale):
    """Ramita de hojas que sale de una esquina hacia el centro, apagada para no dominar."""
    stem = (58, 74, 40, 255)
    leaf_colors = [(78, 104, 52, 255), (96, 122, 62, 255), (66, 90, 46, 255)]
    pts = [(x, y)]
    cx, cy = x, y
    for i in range(6):
        cx += direction * scale * 9
        cy += math.sin(i * 0.9) * scale * 3 + scale * 1.2
        pts.append((cx, cy))
    d.line(pts, fill=stem, width=int(scale * 1.6), joint="curve")
    for i, (px, py) in enumerate(pts[1:], 1):
        side = 1 if i % 2 else -1
        ang = (0.9 if direction > 0 else -0.9) + side * 0.8
        leaf(d, px, py, ang, scale * (11 - i), rng.choice(leaf_colors), (48, 64, 34, 255))


def medallion(img, cx, cy, r, gem, hover):
    d = ImageDraw.Draw(img)
    d.ellipse([cx - r - SS * 3, cy - r - SS * 3, cx + r + SS * 3, cy + r + SS * 3], fill=(22, 16, 8, 255))
    ring = vertical_gradient(2 * r + 2 * SS * 2, 2 * r + 2 * SS * 2, (255, 226, 140), (150, 98, 26))
    mask = Image.new("L", ring.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, ring.size[0] - 1, ring.size[1] - 1], fill=255)
    img.paste(ring, (cx - r - SS * 2, cy - r - SS * 2), mask)
    # Gema con brillo
    gr = int(r * 0.66)
    gem_img = vertical_gradient(2 * gr, 2 * gr, gem[2] if hover else gem[1], gem[0])
    gmask = Image.new("L", gem_img.size, 0)
    ImageDraw.Draw(gmask).ellipse([0, 0, 2 * gr - 1, 2 * gr - 1], fill=255)
    img.paste(gem_img, (cx - gr, cy - gr), gmask)
    d.ellipse([cx - gr * 0.55, cy - gr * 0.7, cx - gr * 0.05, cy - gr * 0.25], fill=(255, 255, 255, 150 if hover else 110))


def make_state(w_gui, primary, state, seed):
    W, H = w_gui * SCALE * SS, GUI_H * SCALE * SS
    hover, disabled = state == 1, state == 2
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    pad = SS * 6
    r_out = (H - 2 * pad) // 2 - SS * 6
    # Brillo exterior al pasar el ratón
    if hover:
        glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        rounded(ImageDraw.Draw(glow), [pad - SS * 3, pad - SS * 3, W - pad + SS * 3, H - pad + SS * 3], r_out + SS * 3,
                (255, 200, 90, 150))
        img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(SS * 4)))
    d = ImageDraw.Draw(img)
    # Contorno oscuro + marco dorado degradado + filo claro interior
    rounded(d, [pad, pad, W - pad, H - pad], r_out, (24, 17, 8, 255))
    frame_top, frame_bottom = ((255, 232, 150), (176, 118, 30)) if hover else ((236, 196, 104), (132, 86, 22))
    frame = vertical_gradient(W, H, frame_top, frame_bottom)
    fmask = Image.new("L", (W, H), 0)
    rounded(ImageDraw.Draw(fmask), [pad + SS * 3, pad + SS * 3, W - pad - SS * 3, H - pad - SS * 3], r_out - SS * 3, 255)
    img.paste(frame, (0, 0), fmask)
    # Placa de piedra interior (tono cálido, poco verde)
    inset = pad + SS * 9
    if hover:
        stone_top, stone_bottom = (120, 108, 86), (78, 68, 52)
    else:
        stone_top, stone_bottom = (100, 90, 72), (62, 54, 41)
    if primary:
        stone_top, stone_bottom = (tuple(min(255, c + 10) for c in stone_top), stone_bottom)
    stone = vertical_gradient(W, H, stone_top, stone_bottom)
    stone = Image.alpha_composite(stone, stone_texture(W, H, seed, True))
    smask = Image.new("L", (W, H), 0)
    rounded(ImageDraw.Draw(smask), [inset, inset, W - inset, H - inset], r_out - SS * 9, 255)
    img.paste(stone, (0, 0), smask)
    d = ImageDraw.Draw(img)
    # Filo interior: luz arriba, sombra abajo
    d.rounded_rectangle([inset, inset, W - inset, H - inset], radius=r_out - SS * 9, outline=(30, 22, 12, 255), width=SS * 2)
    d.line([(inset + r_out, inset + SS * 3), (W - inset - r_out, inset + SS * 3)], fill=(255, 240, 200, 60), width=SS * 2)
    # Línea grabada dorada fina a lo largo
    if primary:
        y1, y2 = inset + SS * 8, H - inset - SS * 8
        for y in (y1, y2):
            d.line([(inset + r_out * 1.6, y), (W - inset - r_out * 1.6, y)], fill=(214, 168, 72, 150), width=SS * 2)
    # Medallones con gema en los extremos
    gem = ((150, 82, 8), (232, 150, 30), (255, 214, 110)) if primary else ((18, 52, 140), (52, 104, 214), (130, 176, 255))
    mr = int((H - 2 * pad) * 0.30)
    medallion(img, pad + r_out, H // 2, mr, gem, hover)
    medallion(img, W - pad - r_out, H // 2, mr, gem, hover)
    # Ramitas discretas en las esquinas superiores
    rng = random.Random(seed + state)
    d = ImageDraw.Draw(img)
    s = SS * 1.1
    sprig(d, pad + r_out + mr, pad + SS * 2, 1, rng, s)
    sprig(d, W - pad - r_out - mr, pad + SS * 2, -1, rng, s)
    if disabled:
        gray = img.convert("LA").convert("RGBA")
        r, g, b, a = gray.split()
        dim = Image.eval(r, lambda v: int(v * 0.6))
        img = Image.merge("RGBA", (dim, dim, dim, img.split()[3]))
    return img.resize((W // SS, H // SS), Image.LANCZOS)


def make(name, w_gui, primary, seed):
    states = [make_state(w_gui, primary, i, seed) for i in range(3)]
    w, h = states[0].size
    sheet = Image.new("RGBA", (w, h * 3), (0, 0, 0, 0))
    for i, st in enumerate(states):
        sheet.paste(st, (0, i * h))
    sheet.save(os.path.join(OUT, name), optimize=True)
    print(name, sheet.size)


make("button_primary.png", 200, True, 11)
make("button_wide.png", 200, False, 7)
make("button_half.png", 98, False, 23)
