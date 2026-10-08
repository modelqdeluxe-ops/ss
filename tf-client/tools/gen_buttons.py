"""Genera las texturas de los botones del menú de TF, con el mismo diseño que los botones de la web.

Diseño (como `.btn` de la web): bloque redondeado con un «labio» abajo (la sombra sólida que le da volumen), filo de
luz arriba y un borde fino. El principal es de oro con letras oscuras (como «Comprar» en la web); el resto, pizarra azul
con filo cian (los secundarios de la web) que al pasar el ratón se llena de cian → azul; el de Discord, en su azul.

Cada PNG mide 64x20 píxeles de interfaz (256x80 de textura por estado) y lleva tres estados apilados (normal, ratón
encima, desactivado). Se dibuja "en tres piezas": las puntas (CAP = 16 píxeles de interfaz a cada lado) van a tamaño fijo
y el centro, que es uniforme, se estira al ancho de cada botón; así sirve para botones de cualquier ancho.
Resolución: 4 píxeles de textura por píxel de interfaz. Se dibuja a 4x más y se reduce con Lanczos (bordes suaves).
Uso: python3 tools/gen_buttons.py
"""
import os

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "tfclient", "textures", "gui")
SS = 4            # supermuestreo
TEX = 4           # texels por píxel de interfaz
GUI_H = 20
U = TEX * SS      # tamaño de un píxel de interfaz en el lienzo de trabajo

# Colores por estilo y estado: (arriba, abajo) del cuerpo, borde (rgba), labio, brillo de arriba (alfa)
STYLES = {
    "primary": {
        0: ((255, 236, 150), (255, 160, 48), (255, 246, 200, 150), (138, 71, 5), 150),
        1: ((255, 246, 190), (255, 184, 70), (255, 252, 225, 220), (150, 80, 8), 190),
    },
    "secondary": {
        0: ((44, 56, 118), (27, 35, 80), (69, 233, 255, 140), (10, 15, 44), 40),
        1: ((40, 120, 170), (40, 82, 200), (140, 245, 255, 245), (8, 22, 60), 70),
    },
    "discord": {
        0: ((125, 135, 255), (88, 101, 242), (200, 205, 255, 150), (52, 61, 176), 110),
        1: ((150, 160, 255), (110, 122, 255), (225, 228, 255, 220), (60, 70, 190), 140),
    },
}


def vgrad(w, h, top, bottom):
    col = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        col.putpixel((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return col.resize((w, h)).convert("RGBA")


def rounded_mask(size, box, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle(box, radius=radius, fill=255)
    return m


def make_state(w_gui, style, state):
    W, H = w_gui * U, GUI_H * U
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    disabled = state == 2
    top, bottom, border, lip, shine = STYLES[style][1 if state == 1 else 0]
    radius = int(4.5 * U)
    lip_h = 2 * U
    body = (0, 0, W - 1, H - 1 - lip_h)
    # Labio: el mismo bloque, oscuro, un poco más abajo
    lip_layer = Image.new("RGBA", (W, H), lip + (255,))
    img.paste(lip_layer, (0, 0), rounded_mask((W, H), (0, lip_h, W - 1, H - 1), radius))
    # Cuerpo con su degradado
    img.paste(vgrad(W, H, top, bottom), (0, 0), rounded_mask((W, H), body, radius))
    d = ImageDraw.Draw(img, "RGBA")
    # Borde fino y filo de luz arriba
    d.rounded_rectangle(body, radius=radius, outline=border, width=max(1, U // 4 * 1))
    d.line([(radius, int(0.5 * U)), (W - radius, int(0.5 * U))], fill=(255, 255, 255, shine), width=max(1, U // 4))
    if disabled:
        r, g, b, a = img.split()
        lum = Image.merge("RGB", (r, g, b)).convert("L").point(lambda v: int(v * 0.55))
        img = Image.merge("RGBA", (lum, lum, lum, a))
    return img.resize((W // SS, H // SS), Image.LANCZOS)


def make(name, w_gui, style):
    states = [make_state(w_gui, style, i) for i in range(3)]
    w, h = states[0].size
    sheet = Image.new("RGBA", (w, h * 3), (0, 0, 0, 0))
    for i, st in enumerate(states):
        sheet.paste(st, (0, i * h))
    sheet.save(os.path.join(OUT, name), optimize=True)
    print(name, sheet.size)


make("button_slice_primary.png", 64, "primary")
make("button_slice.png", 64, "secondary")
make("button_slice_discord.png", 64, "discord")
