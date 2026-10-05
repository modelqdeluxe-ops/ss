"""Genera las texturas de los botones del menú de TF.

Diseño: placa con las puntas en ángulo (forma hexagonal alargada), fondo azul noche como el zafiro de la corona del
logo, doble filete dorado metálico y pequeños rombos dorados con líneas finas en los extremos. El botón principal es de
oro con letras oscuras. Al pasar el ratón el marco brilla y aparece un halo dorado.

Cada PNG mide 64x20 píxeles de interfaz (256x80 de textura por estado) y lleva tres estados apilados (normal, ratón
encima, desactivado). Se dibuja "en tres piezas": las puntas (CAP = 16 píxeles de interfaz a cada lado) van a tamaño fijo
y el centro, que es uniforme, se estira al ancho de cada botón; así sirve para botones de cualquier ancho.
Resolución: 4 píxeles de textura por píxel de interfaz, así a 1080p (escala 4) se ve 1:1. Se dibuja a 4x más y se
reduce con Lanczos para que los bordes queden suaves.
Uso: python3 tools/gen_buttons.py
"""
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "tfclient", "textures", "gui")
SS = 4            # supermuestreo
TEX = 4           # texels por píxel de interfaz
GUI_H = 20
U = TEX * SS      # tamaño de un píxel de interfaz en el lienzo de trabajo


def hexagon(x0, y0, x1, y1, tip):
    ym = (y0 + y1) / 2
    return [(x0 + tip, y0), (x1 - tip, y0), (x1, ym), (x1 - tip, y1), (x0 + tip, y1), (x0, ym)]


def mask_of(size, poly):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).polygon(poly, fill=255)
    return m


def gradient(size, stops):
    """Degradado vertical con varias paradas: [(posición 0..1, (r, g, b))]."""
    w, h = size
    col = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
            if p0 <= t <= p1:
                k = (t - p0) / max(1e-6, p1 - p0)
                col.putpixel((0, y), tuple(int(c0[i] + (c1[i] - c0[i]) * k) for i in range(3)))
                break
    return col.resize((w, h)).convert("RGBA")


def paste_masked(base, layer, mask):
    base.paste(layer, (0, 0), mask)


def inset_poly(W, H, inset, tip_ratio):
    """Hexágono con margen `inset` (en unidades del lienzo) y puntas proporcionales a la altura."""
    x0, y0, x1, y1 = inset, inset, W - inset, H - inset
    tip = (y1 - y0) / 2 * tip_ratio
    return hexagon(x0, y0, x1, y1, tip)


GOLD = [(0.0, (255, 236, 170)), (0.35, (232, 186, 82)), (0.55, (176, 120, 32)), (0.8, (226, 172, 70)), (1.0, (140, 92, 22))]
GOLD_HOT = [(0.0, (255, 248, 210)), (0.35, (255, 214, 110)), (0.55, (206, 146, 44)), (0.8, (250, 202, 96)), (1.0, (170, 112, 30))]
NAVY = [(0.0, (31, 40, 72)), (0.5, (18, 24, 46)), (1.0, (9, 12, 25))]
NAVY_HOT = [(0.0, (46, 58, 102)), (0.5, (27, 36, 68)), (1.0, (14, 19, 40))]
GOLD_FILL = [(0.0, (232, 200, 120)), (0.45, (206, 156, 58)), (1.0, (150, 96, 22))]
GOLD_FILL_HOT = [(0.0, (250, 222, 156)), (0.45, (230, 182, 82)), (1.0, (176, 118, 32))]
TIP = 0.62


def diamond(d, cx, cy, r, fill, outline):
    pts = [(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)]
    d.polygon(pts, fill=fill, outline=outline)


def make_state(w_gui, primary, state):
    W, H = w_gui * U, GUI_H * U
    hover, disabled = state == 1, state == 2
    size = (W, H)
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    m = int(U * 1.0)  # margen para el halo y la sombra

    outer = inset_poly(W, H, m, TIP)
    # Sombra suave debajo
    shadow = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).polygon([(x, y + U * 0.6) for x, y in outer], fill=(0, 0, 0, 150))
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(U * 0.6)))
    # Halo dorado al pasar el ratón
    if hover:
        glow = Image.new("RGBA", size, (0, 0, 0, 0))
        ImageDraw.Draw(glow).polygon(outer, fill=(255, 196, 80, 190))
        img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(U * 0.9)))

    # 1) Contorno oscuro
    ImageDraw.Draw(img).polygon(outer, fill=(10, 8, 6, 255))
    # 2) Filete dorado metálico exterior
    paste_masked(img, gradient(size, GOLD_HOT if hover else GOLD), mask_of(size, inset_poly(W, H, m + U * 0.35, TIP)))
    # 3) Línea oscura de separación
    ImageDraw.Draw(img).polygon(inset_poly(W, H, m + U * 1.25, TIP), fill=(26, 18, 8, 255))
    # 4) Relleno
    fill_poly = inset_poly(W, H, m + U * 1.55, TIP)
    fill_mask = mask_of(size, fill_poly)
    if primary:
        fill = gradient(size, GOLD_FILL_HOT if hover else GOLD_FILL)
    else:
        fill = gradient(size, NAVY_HOT if hover else NAVY)
    paste_masked(img, fill, fill_mask)
    # Brillo superior (vidrio) recortado al relleno
    gloss = Image.new("RGBA", size, (0, 0, 0, 0))
    top_poly = inset_poly(W, H, m + U * 1.55, TIP)
    ImageDraw.Draw(gloss).rectangle([0, 0, W, H * 0.48], fill=(255, 255, 255, 26 if not primary else 48))
    gloss_mask = ImageChops.multiply(mask_of(size, top_poly), gloss.split()[3])
    gloss.putalpha(gloss_mask)
    img = Image.alpha_composite(img, gloss.filter(ImageFilter.GaussianBlur(U * 0.15)))
    # 5) Filete dorado interior fino (doble marco)
    d = ImageDraw.Draw(img)
    inner = inset_poly(W, H, m + U * 2.6, TIP)
    line_color = (120, 76, 16, 220) if primary else ((255, 214, 120, 230) if hover else (212, 166, 74, 200))
    d.line(inner + [inner[0]], fill=line_color, width=max(1, int(U * 0.28)), joint="curve")

    # 6) Adornos: rombo dorado en cada punta interior con una línea fina hacia el centro que se desvanece
    cy = H / 2
    tip_px = (H - 2 * (m + U * 2.6)) / 2 * TIP
    for side in (-1, 1):
        cx = (m + U * 2.6 + tip_px + U * 1.6) if side < 0 else (W - m - U * 2.6 - tip_px - U * 1.6)
        orn = (120, 76, 16, 255) if primary else ((255, 224, 140, 255) if hover else (226, 180, 86, 255))
        line = Image.new("RGBA", size, (0, 0, 0, 0))
        ld = ImageDraw.Draw(line)
        length = U * 4  # cabe dentro de la punta (CAP)
        steps = 24
        for i in range(steps):
            a = int(orn[3] * (1 - i / steps) * 0.85)
            x_a = cx - side * (U * 1.2 + length * i / steps)
            x_b = cx - side * (U * 1.2 + length * (i + 1) / steps)
            ld.line([(x_a, cy), (x_b, cy)], fill=orn[:3] + (a,), width=max(1, int(U * 0.22)))
        img = Image.alpha_composite(img, line)
        d = ImageDraw.Draw(img)
        diamond(d, cx, cy, U * 1.05, orn, (40, 26, 8, 255))
        diamond(d, cx, cy, U * 0.45, (255, 246, 210, 255) if not primary else (255, 236, 170, 255), None)

    if disabled:
        r, g, b, a = img.split()
        lum = Image.merge("RGB", (r, g, b)).convert("L").point(lambda v: int(v * 0.55))
        img = Image.merge("RGBA", (lum, lum, lum, a))
    return img.resize((W // SS, H // SS), Image.LANCZOS)


def make(name, w_gui, primary):
    states = [make_state(w_gui, primary, i) for i in range(3)]
    w, h = states[0].size
    sheet = Image.new("RGBA", (w, h * 3), (0, 0, 0, 0))
    for i, st in enumerate(states):
        sheet.paste(st, (0, i * h))
    sheet.save(os.path.join(OUT, name), optimize=True)
    print(name, sheet.size)


make("button_slice_primary.png", 64, True)
make("button_slice.png", 64, False)
