import sys
from PIL import Image
from collections import Counter
def escala(im):
    # el tamaño de bloque: el menor tramo de píxeles iguales en filas y columnas opacas
    px = im.load(); w, h = im.size; best = 99
    for y in range(h):
        run = 1
        for x in range(1, w):
            if px[x, y] == px[x-1, y]: run += 1
            else:
                if px[x-1, y][3] > 0 and 0 < run < best: best = run
                run = 1
    return best
for n in sys.argv[1:]:
    im = Image.open('img/' + n).convert('RGBA'); e = escala(im)
    s = im.resize((im.width // e, im.height // e), Image.NEAREST)
    op = [c for c in s.getdata() if c[3] > 0]
    cols = Counter(op)
    bb = s.getbbox()
    # contorno: color de los píxeles opacos que tocan transparencia
    px = s.load(); borde = Counter()
    for y in range(s.height):
        for x in range(s.width):
            if px[x, y][3] == 0: continue
            if any(not (0 <= x+dx < s.width and 0 <= y+dy < s.height) or px[x+dx, y+dy][3] == 0 for dx, dy in ((1,0),(-1,0),(0,1),(0,-1))): borde[px[x, y][:3]] += 1
    print(f"{n}: escala x{e}, nativo {s.width}x{s.height}, figura {bb[2]-bb[0]}x{bb[3]-bb[1]}, {len(cols)} colores, contorno {['#%02x%02x%02x' % c for c, _ in borde.most_common(3)]}")
    s.save('nat_' + n)
