"""Texturas de los botones del menú de inicio (textures/gui/menu_buttons.png): placas opacas en pixel art, 1 píxel de
textura por píxel de interfaz (nítidas en cualquier escala), 200x20 cada una, una debajo de otra:

  0 normal · 1 ratón encima · 2 pulsado · 3 principal · 4 principal ratón · 5 principal pulsado · 6 Salir ratón ·
  7 desactivado

Cada placa: contorno oscuro con las esquinas recortadas, marco de metal dorado biselado (claro arriba, oscuro abajo) con
remaches en las esquinas, un bisel interior y el relleno con grano de piedra. TFMenuButton la corta en tres (4 px a
cada lado y el centro), sin estirar nada. Uso: python3 tools/gen_menu_buttons.py
"""
import os
import random

from PIL import Image

W, H = 200, 20
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'tfclient',
                   'textures', 'gui', 'menu_buttons.png')


def hexrgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def clamp(c):
    return tuple(max(0, min(255, v)) for v in c)


# (relleno arriba, relleno abajo, grano, bisel claro, bisel oscuro, marco arriba, marco medio, marco abajo, remache)
STYLES = {
    'normal': ('#34304a', '#1d1b29', 7, '#57507a', '#100e17', '#f4d68c', '#c99a46', '#6b4515', '#fff2c4'),
    'hover': ('#463f66', '#2a2640', 7, '#7a71a8', '#15121f', '#fff0b8', '#f0c35c', '#8f5f1d', '#ffffff'),
    'pressed': ('#1b1926', '#24212f', 6, '#0e0c14', '#3d3856', '#d8b468', '#a87a30', '#5a3a10', '#e8d8a8'),
    'primary': ('#f6c95a', '#c88a26', 9, '#fff1c2', '#8e5810', '#a9772a', '#7c5216', '#4a2e08', '#ffe9a6'),
    'primary_hover': ('#ffd86e', '#d99a30', 9, '#fff8de', '#9a6214', '#c08a34', '#8f611c', '#56360a', '#ffffff'),
    'primary_pressed': ('#c8902e', '#d9a43c', 8, '#8e5810', '#f0c862', '#8f6422', '#6a4512', '#40280a', '#f2d696'),
    'danger_hover': ('#4a2a3c', '#2c1824', 7, '#7a4a62', '#150b12', '#ffb8c2', '#e05a70', '#7e2032', '#ffe2e8'),
    'disabled': ('#2a2a30', '#1e1e24', 4, '#3c3c46', '#121216', '#8a8a92', '#66666e', '#3c3c42', '#9a9aa2'),
}
ORDER = ['normal', 'hover', 'pressed', 'primary', 'primary_hover', 'primary_pressed', 'danger_hover', 'disabled']
OUTLINE = hexrgb('#0b0a10')


def plate(style, seed):
    ft, fb, grain, bl, bd, mt, mm, mb, rivet = style
    ft, fb, bl, bd, mt, mm, mb, rivet = (hexrgb(c) for c in (ft, fb, bl, bd, mt, mm, mb, rivet))
    rnd = random.Random(seed)
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    px = im.load()

    def metal(y, inner):
        """Marco de metal en la fila y: claro arriba, oscuro abajo; la línea de dentro, un poco más oscura."""
        c = mt if y <= 2 else mb if y >= H - 3 else mix(mix(mt, mb, (y - 1) / (H - 3)), mm, 0.5)
        return mix(c, mb, 0.35) if inner else c

    for y in range(H):
        for x in range(W):
            edge = min(x, y, W - 1 - x, H - 1 - y)  # 0 contorno, 1-2 marco, 3 bisel, 4+ relleno
            corner = min(x, W - 1 - x) + min(y, H - 1 - y)
            if corner == 0:
                continue  # esquina recortada (redondeada)
            if edge == 0 or corner == 1:
                px[x, y] = OUTLINE + (255,)
                continue
            if edge <= 2:
                if corner in (2, 3, 4) and edge >= 1 and min(x, W - 1 - x) <= 2 and min(y, H - 1 - y) <= 2:
                    c = rivet if corner == 2 or corner == 4 else mix(rivet, mt, 0.4)  # remache de la esquina
                else:
                    c = metal(y, edge == 2)
                px[x, y] = clamp(c) + (255,)
                continue
            if edge == 3:
                # bisel interior: luz arriba y a la izquierda, sombra abajo y a la derecha
                light = y == 3 or (x == 3 and y < H - 4)
                px[x, y] = (bl if light else bd) + (255,)
                continue
            # relleno con degradado y grano de piedra (motas y vetas finas)
            t = (y - 4) / (H - 9)
            c = mix(ft, fb, t)
            n = rnd.randint(-grain, grain)
            if rnd.random() < 0.06:
                n += grain
            if (x + 3 * y) % 23 == 0:
                n -= grain // 2
            px[x, y] = clamp(tuple(v + n for v in c)) + (255,)
    # brillo fino bajo el bisel de arriba
    for x in range(4, W - 4):
        r, g, b, a = px[x, 4]
        px[x, 4] = clamp((r + 10, g + 10, b + 12)) + (a,)
    # adorno en el centro de cada lado: un rombo pequeño de metal que entra en el marco
    cy = H // 2
    for x0, d in ((3, 1), (W - 4, -1)):
        for dy, length in ((-1, 1), (0, 2), (1, 1)):
            for k in range(length):
                px[x0 + d * k, cy + dy - 1] = clamp(rivet if (dy == 0 and k == 0) else mt) + (255,)
    return im


def main():
    sheet = Image.new('RGBA', (W, H * len(ORDER)), (0, 0, 0, 0))
    for i, name in enumerate(ORDER):
        sheet.paste(plate(STYLES[name], 1234 + (i % 3)), (0, i * H))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    sheet.save(OUT)
    print(OUT, sheet.size)


if __name__ == '__main__':
    main()
