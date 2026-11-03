# ck_hoja.py <salida> <zoom> <png...>: hoja de contacto con el nombre y el tamaño de cada dibujo
import sys, os
from PIL import Image, ImageDraw, ImageFont
F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 12)
sal, z, fs = sys.argv[1], int(sys.argv[2]), sys.argv[3:]
ims = [(os.path.basename(f)[:-4], Image.open(f).convert('RGBA')) for f in fs]
W = 1800; x = y = 8; fila = 0; pos = []
for n, im in ims:
    w, h = im.size[0] * z, im.size[1] * z
    if x + w > W - 8: x = 8; y += fila + 24; fila = 0
    pos.append((x, y, n, im)); x += max(w, 120) + 12; fila = max(fila, h)
out = Image.new('RGBA', (W, y + fila + 30), (44, 43, 52, 255)); d = ImageDraw.Draw(out)
for x, y, n, im in pos:
    d.text((x, y), f'{n} {im.size[0]}x{im.size[1]}', fill=(220, 220, 200), font=F)
    zz = im.resize((im.size[0] * z, im.size[1] * z), Image.NEAREST); out.paste(zz, (x, y + 16), zz)
out.save(sal); print(out.size)
