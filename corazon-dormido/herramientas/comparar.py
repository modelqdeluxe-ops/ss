# comparar.py a.png b.png salida.png [alto]: a arriba, b abajo (recortadas al contenido)
import sys
from PIL import Image
a, b = Image.open(sys.argv[1]).convert('RGB'), Image.open(sys.argv[2]).convert('RGB')
def alto(im):
    px = im.load(); w, h = im.size
    for y in range(h - 1, 0, -1):
        if px[w // 2, y] != (43, 42, 51) and px[5, y] != px[5, h - 1]: return y + 4
    return h
h = int(sys.argv[4]) if len(sys.argv) > 4 else max(alto(a), alto(b))
out = Image.new('RGB', (a.width, h * 2 + 6), (255, 255, 255))
out.paste(a.crop((0, 0, a.width, h)), (0, 0)); out.paste(b.crop((0, 0, b.width, h)), (0, h + 6))
out.save(sys.argv[3])
