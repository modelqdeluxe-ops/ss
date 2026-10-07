# python3 headsheet.py <carpeta de heads.cjs> heads.json <prefijo> : hojas con cada casco de frente y de perfil
import sys, json, os
from PIL import Image, ImageDraw, ImageChops
outdir, lst, prefix = sys.argv[1], json.load(open(sys.argv[2])), sys.argv[3]
BG = (20, 26, 38); W = H = 200; COLS = 4; ROWS = 4
def crop(p):
    im = Image.open(p).convert('RGB')
    # solo la mitad de arriba (cabeza y torso)
    im = im.crop((150, 60, 550, 460))
    return im.resize((W, H))
per = COLS * ROWS
for page in range(0, len(lst), per):
    chunk = lst[page:page + per]
    sheet = Image.new('RGB', (W * 2 * COLS, H * ROWS), BG); d = ImageDraw.Draw(sheet)
    for i, (s, k, *_rest) in enumerate(chunk):
        x, y = (i % COLS) * W * 2, (i // COLS) * H
        for j, t in enumerate('fs'):
            p = f'{outdir}/{s}__{k}__{t}.png'
            if os.path.exists(p): sheet.paste(crop(p), (x + j * W, y))
        d.text((x + 3, y + 2), f'{page + i} {s}/{k}'[:40], fill=(255, 255, 0))
    sheet.save(f'{prefix}_{page // per:02d}.png')
