"""Textura del objeto «pad de administrador» (16x16): una tableta con marco azul marino, esquinas de oro y en la
pantalla un engranaje dorado. Píxel a píxel, con el contorno del pad.

    python3 tools/pad/item_pad_admin.py   # escribe src/main/resources/assets/tfclient/textures/item/pad_admin.png
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'textures', 'item',
                                    'pad_admin.png'))
ROWS = [
    "................",
    ".OOOOOOOOOOOOOO.",
    "OGGNNNNNNNNNNGGO",
    "OGnNNNNNNNNNNNgO",
    "ONNsssssssssSNNO",
    "ONNsssyysyysSNNO",
    "ONNssyYYYYysSNNO",
    "ONNsyYYbbYYySNNO",
    "ONNssYbBBbYsSNNO",
    "ONNsyYYbbYYySNNO",
    "ONNssyYYYYysSNNO",
    "ONNsssyysyysSNNO",
    "ONNSSSSSSSSSSNNO",
    "OgnNNNNNNNNNNNgO",
    "OGGNNNNNNNNNNGGO",
    ".OOOOOOOOOOOOOO.",
]
PAL = {
    'O': (20, 18, 42), 'N': (36, 52, 104), 'n': (60, 84, 150), 'G': (246, 182, 40), 'g': (190, 120, 24),
    's': (120, 212, 252), 'S': (70, 160, 220), 'y': (255, 226, 96), 'Y': (234, 160, 32), 'b': (52, 150, 250),
    'B': (220, 246, 255),
}


def main():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ROWS):
        for x, ch in enumerate(row):
            if ch in PAL:
                im.putpixel((x, y), PAL[ch] + (255,))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im.save(OUT)
    print('escrito', OUT)


if __name__ == '__main__':
    main()
