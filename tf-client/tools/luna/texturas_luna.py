"""Las texturas de la Luna de Sangre (TF Client 1.3.175), hechas aquí (nada copiado de Minecraft).

    python3 tools/luna/texturas_luna.py

Escribe en assets/tfclient/textures/:
  entity/luna_ojos/*.png      las capas de ojos rojos que brillan (RenderType.eyes: solo suman luz): los píxeles de los
                              ojos de cada modelo en rojo vivo y un halo tenue alrededor, en la cara de delante de la
                              cabeza. Dónde están los ojos se midió en las texturas de Minecraft 1.20.1 (los píxeles
                              oscuros o blancos de la cara): zombi, husk, ahogado, esqueleto, stray y esqueleto wither en
                              la fila 12 (u 9-10 y 13-14); creeper, 2x2 en las filas 10-11; aldeano zombi, saqueador,
                              vindicador, invocador y bruja en la fila 14.
  environment/luna_sangre_vacia.png   la luna de Minecraft vacía esa noche (la dibuja BloodMoonClient: la llena, roja)
  environment/luna_sangre_halo.png    su resplandor (aditivo)
  gui/luna_vineta.png                 la viñeta roja de la pantalla
"""
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'textures'))

CORE = (255, 46, 30)      # el ojo
HOT = (255, 120, 80)      # el centro del ojo (un poco más claro)
GLOW = (110, 6, 4)        # el halo en la cara (suma poquito)

# (archivo, tamaño, cara de delante de la cabeza (u0, v0, u1, v1), píxeles de los ojos)
ROW12 = [(9, 12), (10, 12), (13, 12), (14, 12)]
ROW14 = [(9, 14), (10, 14), (13, 14), (14, 14)]
MASKS = [
    ('humanoide', (64, 64), (8, 8, 16, 16), ROW12),         # zombi, husk, ahogado
    ('esqueleto', (64, 32), (8, 8, 16, 16), ROW12),         # esqueleto, stray, esqueleto wither
    ('creeper', (64, 32), (8, 8, 16, 16), [(9, 10), (10, 10), (9, 11), (10, 11), (13, 10), (14, 10), (13, 11), (14, 11)]),
    ('aldeano', (64, 64), (8, 10, 16, 20), ROW14),          # aldeano zombi, saqueador, vindicador, invocador
    ('bruja', (64, 128), (8, 10, 16, 20), ROW14),
]


def eye_mask(size, face, eyes):
    """(1.3.177) Solo los píxeles de los ojos (el dueño: «esos ojos están horribles, parecen lentes»: el halo de alrededor
    de cada ojo se juntaba en una franja de 3x8 de lado a lado de la cara). El de dentro de cada ojo, un poco más claro."""
    img = np.zeros((size[1], size[0], 4), np.uint8)
    for (u, v) in eyes:
        inner = (u == 10 or u == 13)
        img[v, u] = (*(HOT if inner else CORE), 255)
    return Image.fromarray(img, 'RGBA')


def moon_cell():
    """(1.3.177) La luna llena roja en una celda de 32 como la de Minecraft (su disco ocupa 14 px, de 9 a 22: medido en
    moon_phases.png): así sale del mismo tamaño que la de siempre (el dueño: «no tan grande»; antes llenaba la celda y
    salía el doble). Pixel art: degradado de claro (arriba a la izquierda) a oscuro, tres manchas oscuras y el borde."""
    img = np.zeros((32, 32, 4), np.uint8)
    light, base, mid, dark = (244, 104, 72), (214, 50, 36), (178, 32, 26), (138, 18, 18)
    spots = [(13.5, 13.5, 1.6), (18.5, 17.0, 1.9), (13.0, 19.0, 1.0)]
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - 16, y + 0.5 - 16
            d = (dx * dx + dy * dy) ** 0.5
            if d > 7.0:
                continue
            k = (dx + dy) / 14.0                   # -1 arriba a la izquierda, +1 abajo a la derecha
            c = light if k < -0.5 else base if k < 0.25 else mid
            if any(((x + 0.5 - sx) ** 2 + (y + 0.5 - sy) ** 2) ** 0.5 < r for sx, sy, r in spots):
                c = mid if c != mid else dark
            if d > 6.3 and k > -0.2:
                c = dark
            img[y, x] = (*c, 255)
    return Image.fromarray(img, 'RGBA')


def halo(px):
    y, x = np.mgrid[0:px, 0:px] + 0.5
    d = np.hypot(x - px / 2, y - px / 2) / (px / 2)
    edge = np.clip((1 - d) / 0.3, 0, 1)
    a = np.clip(np.exp(-((d / 0.38) ** 2)) * 0.85 + np.exp(-((d / 0.7) ** 2)) * 0.3, 0, 1) * (edge * edge * (3 - 2 * edge))
    img = np.zeros((px, px, 4), np.uint8)
    img[..., 0], img[..., 1], img[..., 2] = 255, 52, 30
    img[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(img, 'RGBA')


def vignette(px):
    y, x = np.mgrid[0:px, 0:px] + 0.5
    d = np.hypot((x - px / 2) / (px / 2), (y - px / 2) / (px / 2)) / np.sqrt(2)
    t = np.clip((d - 0.42) / 0.58, 0, 1)
    a = t * t * (3 - 2 * t) * 0.92
    img = np.zeros((px, px, 4), np.uint8)
    img[..., 0], img[..., 1], img[..., 2] = 92, 0, 4
    img[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(img, 'RGBA')


FONT = os.path.join(HERE, 'fuentes', 'Cinzel.ttf')   # Cinzel (SIL Open Font License 1.1, fuentes/OFL.txt)


def text_image(text, size, color, bottom=None, weight='Regular', glow=(150, 0, 10), glow_alpha=0.55, outline=2):
    """Un texto con Cinzel: degradado de color de arriba abajo, contorno oscuro fino y resplandor suave detrás."""
    from PIL import ImageDraw, ImageFilter, ImageFont
    font = ImageFont.truetype(FONT, size)
    try:
        font.set_variation_by_name(weight)
    except Exception:
        pass
    x0, y0, x1, y1 = font.getbbox(text)
    pad = size // 2
    w, h = x1 - x0 + 2 * pad, y1 - y0 + 2 * pad
    mask = Image.new('L', (w, h), 0)
    ImageDraw.Draw(mask).text((pad - x0, pad - y0), text, font=font, fill=255)
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    # resplandor
    g = mask.filter(ImageFilter.GaussianBlur(size * 0.18))
    out.paste(Image.new('RGBA', (w, h), (*glow, 255)), (0, 0), g.point(lambda v: int(v * glow_alpha)))
    # contorno
    if outline:
        o = mask.filter(ImageFilter.MaxFilter(outline * 2 + 1))
        out.paste(Image.new('RGBA', (w, h), (18, 0, 2, 255)), (0, 0), o.point(lambda v: int(v * 0.85)))
    # el texto con su degradado
    top = np.array(color, float)
    bot = np.array(bottom or color, float)
    grad = np.zeros((h, w, 4), np.uint8)
    for yy in range(h):
        t = min(1.0, max(0.0, (yy - pad) / max(1, (y1 - y0))))
        grad[yy, :, :3] = (top * (1 - t) + bot * t).astype(np.uint8)
    grad[..., 3] = 255
    out.paste(Image.fromarray(grad, 'RGBA'), (0, 0), mask)
    return out


def titles():
    save(text_image('La Luna de Sangre', 112, (236, 58, 52), (150, 6, 14), weight='Bold'), 'gui/luna/titulo.png')
    save(text_image('Esta noche, los monstruos despiertan', 46, (236, 214, 210), (196, 168, 164), glow=(60, 0, 4), glow_alpha=0.5,
                    outline=2), 'gui/luna/subtitulo.png')
    lines = ['Los monstruos son más agresivos y peligrosos', 'Los más fuertes atraviesan tu armadura',
             'Esta noche nadie duerme · Sobrevive hasta el amanecer']
    for i, t in enumerate(lines):
        top = (255, 112, 100) if i == 0 else (236, 220, 216)
        save(text_image(t, 42, top, None, glow=(70, 0, 6), glow_alpha=0.5, outline=2), f'gui/luna/linea{i + 1}.png')


def save(img, rel):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    print(rel, img.size)


def main():
    for name, size, face, eyes in MASKS:
        save(eye_mask(size, face, eyes), f'entity/luna_ojos/{name}.png')
    # (1.3.178) la luna es la de Minecraft teñida de rojo al dibujarla (BloodMoonClient): ya no hay textura propia
    # durante la escena, la luna de Minecraft no se ve (va la nuestra, que nace): sus fases, vacías
    save(Image.new('RGBA', (128, 64), (0, 0, 0, 0)), 'environment/luna_sangre_vacia.png')
    titles()
    save(halo(128), 'environment/luna_sangre_halo.png')
    save(vignette(256), 'gui/luna_vineta.png')


if __name__ == '__main__':
    main()
