#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
La textura del disco de una canción compartida (1.3.134, tfclient:disco_compartido): 16x16 como los discos de
Minecraft (vinilo negro con surcos y brillo), con la etiqueta morada y el agujero dorado de Tierras Fantásticas.
Uso: python3 tools/musica/disco_compartido.py [--ver carpeta]  (escribe la textura en assets/tfclient/textures/item)
"""
import os
import sys
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
SALIDA = os.path.join(AQUI, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'textures', 'item',
                      'disco_compartido.png')

# . nada · k borde · d vinilo · s surco · h brillo · p etiqueta · P etiqueta clara · o oro · O oro claro
DIBUJO = [
    '................',
    '.....kkkkkk.....',
    '...kkddddddkk...',
    '..kdddsshdddkk..',
    '..kdsddddhhddk..',
    '.kddsdpppPdsddk.',
    '.kdsdpPPPppdddk.',
    '.kdsdpPoOppdsdk.',
    '.kdsdpPOoppdsdk.',
    '.kdddppppppdsdk.',
    '.kdsddppppddddk.',
    '..kddsdddddsdk..',
    '..kkddddsdddkk..',
    '...kkddddddkk...',
    '.....kkkkkk.....',
    '................',
]
COLORES = {
    'k': (18, 16, 22), 'd': (40, 38, 48), 's': (62, 60, 74), 'h': (120, 118, 140),
    'p': (139, 79, 232), 'P': (176, 132, 255), 'o': (214, 160, 40), 'O': (252, 214, 96),
}


def main():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, fila in enumerate(DIBUJO):
        assert len(fila) == 16, (y, fila)
        for x, c in enumerate(fila):
            if c != '.':
                img.putpixel((x, y), COLORES[c] + (255,))
    os.makedirs(os.path.dirname(SALIDA), exist_ok=True)
    img.save(SALIDA)
    if '--ver' in sys.argv:
        carpeta = sys.argv[sys.argv.index('--ver') + 1]
        os.makedirs(carpeta, exist_ok=True)
        img.resize((256, 256), Image.NEAREST).save(os.path.join(carpeta, 'disco_compartido_x16.png'))
    print('escrito', os.path.normpath(SALIDA))


if __name__ == '__main__':
    main()
