"""El fondo del TF Pad (1.3.107; el dueño: «quita ese fondo de aldeas del pad y deja un logo dibujado como el que
tenemos: solo las TF encerradas en un círculo o algo, en azul oscuro»): un emblema de pixel art, dibujado aquí (no es
el logo pegado): un aro grueso con su brillo arriba a la izquierda y su sombra abajo a la derecha, y dentro las letras
«TF» macizas con su relieve hacia abajo a la derecha, como las del logo del marco. Todo en azules oscuros, para ir
grande detrás de las apps (TFPadScreen.emblem).

    python3 -I tools/pad/fondo_tf.py      → textures/gui/pad/fondo_tf.png
"""
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
GUI = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'textures', 'gui'))
N = 80            # la rejilla de pixel art
SCALE = 8         # cada píxel del dibujo, 8×8 en la textura (nítido, como el resto del pad)

OUTLINE = (6, 12, 34)
RING = (22, 44, 100)
RING_HI = (40, 72, 140)
RING_LO = (12, 26, 64)
FACE = (32, 60, 124)
FACE_HI = (48, 84, 158)
DEPTH = (10, 22, 56)


def letters():
    """Las celdas de la T y la F (x, y) en la rejilla: trazo de 8, 32 de alto, la F un poco más estrecha."""
    cells = set()
    top, h, s = 24, 32, 8
    tx, tw = 18, 22                    # la T: barra de 22, palo de 8 en el centro
    for y in range(top, top + s):
        for x in range(tx, tx + tw):
            cells.add((x, y))
    sx = tx + (tw - s) // 2
    for y in range(top, top + h):
        for x in range(sx, sx + s):
            cells.add((x, y))
    fx = tx + tw + 3                   # la F: palo de 8, barra de arriba de 19, la del medio de 14
    for y in range(top, top + h):
        for x in range(fx, fx + s):
            cells.add((x, y))
    for y in range(top, top + s):
        for x in range(fx, fx + 19):
            cells.add((x, y))
    for y in range(top + 13, top + 13 + 7):
        for x in range(fx, fx + 14):
            cells.add((x, y))
    return cells


def main():
    img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    px = img.load()
    c = (N - 1) / 2
    r_out, r_in = 38.5, 31.5
    # el aro, con su contorno
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - c, y - c)
            if r_in - 1 <= d <= r_out + 1:
                px[x, y] = OUTLINE + (255,)
            if r_in <= d <= r_out:
                # luz de arriba a la izquierda: más claro en esa mitad y en el filo de fuera
                k = ((c - x) + (c - y)) / (2 * r_out)
                col = RING_HI if k > 0.45 and d > r_out - 3 else RING_LO if k < -0.45 and d < r_in + 3 else RING
                px[x, y] = col + (255,)
    # dentro del aro, un disco muy oscuro y translúcido (el fondo del emblema)
    for y in range(N):
        for x in range(N):
            if math.hypot(x - c, y - c) < r_in - 1:
                px[x, y] = (12, 24, 62, 170)
    # las letras: relieve (3 px abajo a la derecha), contorno y cara con su brillo arriba
    cells = letters()
    depth = {(x + dx, y + dy) for (x, y) in cells for dx, dy in ((1, 1), (2, 2), (3, 3), (1, 2), (2, 1), (2, 3), (3, 2))}
    for (x, y) in depth | cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if 0 <= x + dx < N and 0 <= y + dy < N:
                    px[x + dx, y + dy] = OUTLINE + (255,)
    for (x, y) in depth:
        px[x, y] = DEPTH + (255,)
    for (x, y) in cells:
        hi = (x - 1, y) not in cells or (x, y - 1) not in cells
        px[x, y] = (FACE_HI if hi else FACE) + (255,)
    out = img.resize((N * SCALE, N * SCALE), Image.NEAREST)
    dest = os.path.join(GUI, 'pad', 'fondo_tf.png')
    out.save(dest, optimize=True)
    print(dest, out.size)


if __name__ == '__main__':
    main()
