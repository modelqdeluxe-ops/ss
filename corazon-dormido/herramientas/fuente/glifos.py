# python3 -I herramientas/fuente/glifos.py <PixelOperator8.ttf> <salida.js>
# Pasa la fuente Pixel Operator 8 (Jayvee Enaguas, CC0: https://www.dafont.com/pixel-operator.font) a la tabla de
# glifos del juego: cada letra, una rejilla de 11 filas («#» tinta, «.» nada, filas separadas por «/»): 2 filas para
# las tildes, 7 de mayúscula y 2 para lo que baja (g, p, q, y). Se dibuja sin suavizar, píxel a píxel.
import sys
from PIL import Image, ImageDraw, ImageFont
ttf, salida = sys.argv[1], sys.argv[2]
K = 4  # se dibuja a 32 px (cada píxel de la letra es un bloque de 4×4) y se toma el centro de cada bloque
f = ImageFont.truetype(ttf, 8 * K)
CHARS = ''.join(chr(c) for c in range(32, 127)) + '¡¿«»…—·×ÁÉÍÓÚÑÜáéíóúñüºª°'
# la fila de arriba de la mayúscula (H) en un lienzo dibujado en y=10
def caja(ch):
    im = Image.new('L', (40 * K, 40 * K), 0); ImageDraw.Draw(im).text((10 * K, 10 * K), ch, font=f, fill=255)
    pe = Image.new('L', (40, 40), 0)
    for y in range(40):
        for x in range(40):
            if im.getpixel((x * K + K // 2, y * K + K // 2)) > 127: pe.putpixel((x, y), 255)
    return pe
top = caja('H').getbbox()[1]  # primera fila de la mayúscula
out = {}
for ch in CHARS:
    im = caja(ch); bb = im.getbbox()
    adv = int(round(f.getlength(ch) / K))
    if not bb:
        out[ch] = (max(2, adv - 1), ['.' * max(1, adv - 1)] * 11)
        continue
    x0, x1 = 10 + 0, 10 + max(adv, bb[2] - 10)  # desde el origen hasta el avance (o lo dibujado, si es más)
    x0 = min(x0, bb[0])
    w = max(1, bb[2] - x0)
    filas = []
    for r in range(11):
        y = top - 2 + r
        filas.append(''.join('#' if im.getpixel((x, y)) else '.' for x in range(x0, x0 + w)))
    out[ch] = (w, filas)
esc = lambda c: c.replace('\\', '\\\\').replace("'", "\\'")
with open(salida, 'w') as fo:
    fo.write("'use strict';\n// Glifos de Pixel Operator 8 (Jayvee Enaguas, CC0). Generado por herramientas/fuente/glifos.py: no se edita a mano.\n")
    fo.write('// 11 filas por letra: 2 para las tildes, 7 de mayúscula, 2 de lo que baja. «#» tinta, filas separadas por «/».\n')
    fo.write('const GLIFOS8 = {\n')
    for ch, (w, filas) in out.items():
        fo.write(f"  '{esc(ch)}': '{'/'.join(filas)}',\n")
    fo.write('};\n')
print(len(out), 'glifos', 'fila de arriba', top)
