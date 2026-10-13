"""Retratos de los oficios para el pad (Oficios): el muñeco de cada oficio recortado de los paneles del pack
«Medieval Jobs» (ItemsAdder: resourcepack/assets/medival_jobs/textures/<oficio>.png, 256x256; el muñeco está en la
ventana de arriba). Sale un cuadro de 39x39 con su fondo por oficio en textures/gui/pad/jobs/<oficio>.png (el nombre
es el «fondo» del oficio en la config: farmer, miner, wood_cutter...). El pack no se sube al repo: solo lo generado.

    python3 tools/pad/job_images.py <carpeta textures del pack>
"""
import os
import sys

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets',
                   'tfclient', 'textures', 'gui', 'pad', 'jobs')
JOBS = ['farmer', 'miner', 'wood_cutter', 'digger', 'fisherman', 'hunter', 'alchemist', 'blacksmith', 'enchanter',
        'builder']
BOX = (108, 52, 147, 91)  # el muñeco, centrado en la ventana (sin el marco ni la cinta del nombre)


def main():
    src = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    for j in JOBS:
        im = Image.open(os.path.join(src, j + '.png')).convert('RGBA').crop(BOX)
        im.save(os.path.join(OUT, j + '.png'), optimize=True)
        print(j, im.size)


if __name__ == '__main__':
    main()
