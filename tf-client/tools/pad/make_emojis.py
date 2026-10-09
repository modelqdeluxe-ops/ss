"""Reacciones de Comunidad (1.3.31): los emojis de Noto Color Emoji (Google, licencia OFL 1.1 / Apache 2.0) reducidos a
44x44 (el pad los pinta suavizados a 11 unidades en la tarjeta y a 14 en el selector de REACCIONAR). Deja los PNG
en tools/pad/emoji/, que build_pad.py copia como emo_<nombre>.png. Solo hace falta volver a correrlo para cambiar un emoji:

    python3 tools/pad/make_emojis.py [ruta a NotoColorEmoji.ttf]
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONT = sys.argv[1] if len(sys.argv) > 1 else '/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf'
SIZE = 44
EMOJIS = {'risa': '\U0001F602', 'wow': '\U0001F62E', 'triste': '\U0001F622', 'fuego': '\U0001F525', 'top': '\U0001F44D'}


def main():
    font = ImageFont.truetype(FONT, 109)  # el único tamaño que trae la fuente de color
    out = os.path.join(HERE, 'emoji')
    os.makedirs(out, exist_ok=True)
    for name, ch in EMOJIS.items():
        im = Image.new('RGBA', (160, 160), (0, 0, 0, 0))
        ImageDraw.Draw(im).text((0, 0), ch, font=font, embedded_color=True)
        im = im.crop(im.getbbox())
        side = max(im.size)
        sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
        sq.alpha_composite(im, ((side - im.width) // 2, (side - im.height) // 2))
        sq.resize((SIZE, SIZE), Image.LANCZOS).save(os.path.join(out, name + '.png'))
        print(name)


if __name__ == '__main__':
    main()
