# hoja.py salida.png ancho nombres...: hoja de contacto a escala nativa x2 (las imágenes de la wiki están a x4)
import sys, math
from PIL import Image, ImageDraw
sal, ancho, nombres = sys.argv[1], int(sys.argv[2]), sys.argv[3:]
ims = []
for n in nombres:
    try: im = Image.open('img/' + n).convert('RGBA')
    except Exception: continue
    ims.append((n, im))
x = y = 0; fila = 0; pos = []
for n, im in ims:
    w, h = im.size
    if x + w > ancho: x = 0; y += fila + 14; fila = 0
    pos.append((n, im, x, y)); x += w + 8; fila = max(fila, h)
H = y + fila + 14
out = Image.new('RGBA', (ancho, H), (40, 38, 48, 255)); d = ImageDraw.Draw(out)
for n, im, x, y in pos:
    out.paste(im, (x, y + 12), im); d.text((x, y), n[:18], fill=(200, 200, 200, 255))
out.save(sal)
