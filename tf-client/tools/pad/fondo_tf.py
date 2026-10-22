"""El fondo del TF Pad (1.3.107; el dueño: «quita ese TF del pad, se ve horrible; cambia ese fondo; dibuja un TF oscuro de
fondo como el logo que ves arriba a la izquierda»): el logo de Tierras Fantásticas (textures/gui/logo.png, el del marco)
en azul noche, con sus relieves en dos tonos de azul oscuro, para ir grande detrás de las apps (TFPadScreen.glassBase).

    python3 -I tools/pad/fondo_tf.py      → textures/gui/pad/fondo_tf.png
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
GUI = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'textures', 'gui'))
DARK = (10, 22, 58)       # lo más oscuro (las sombras del logo)
LIGHT = (44, 74, 138)     # lo más claro (las caras blancas de las letras)
HEIGHT = 640


def main():
    src = Image.open(os.path.join(GUI, 'logo.png')).convert('RGBA')
    w = round(src.width * HEIGHT / src.height)
    src = src.resize((w, HEIGHT), Image.LANCZOS)
    out = Image.new('RGBA', src.size)
    sp, op = src.load(), out.load()
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = sp[x, y]
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            k = lum ** 1.3
            op[x, y] = tuple(round(DARK[i] + (LIGHT[i] - DARK[i]) * k) for i in range(3)) + (a,)
    dest = os.path.join(GUI, 'pad', 'fondo_tf.png')
    out.save(dest, optimize=True)
    print(dest, out.size)


if __name__ == '__main__':
    main()
