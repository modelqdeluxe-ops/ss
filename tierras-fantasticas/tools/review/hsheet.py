import sys, json, os
from PIL import Image, ImageDraw, ImageChops
outdir, lst, prefix, cols, rows = sys.argv[1], json.load(open(sys.argv[2])), sys.argv[3], int(sys.argv[4]), int(sys.argv[5])
BG = (20, 26, 38); W = H = 220
def fit(im):
    diff = ImageChops.difference(im.convert('RGB'), Image.new('RGB', im.size, BG)).convert('L').point(lambda v: 255 if v > 12 else 0)
    bb = diff.getbbox()
    if bb: im = im.crop((max(0, bb[0]-8), max(0, bb[1]-8), min(im.width, bb[2]+8), min(im.height, bb[3]+8)))
    k = min(W / im.width, (H-14) / im.height)
    im = im.resize((max(1, int(im.width*k)), max(1, int(im.height*k))))
    cell = Image.new('RGB', (W, H), BG); cell.paste(im, ((W-im.width)//2, 14 + (H-14-im.height)//2)); return cell
per = cols * rows
for page in range(0, len(lst), per):
    chunk = lst[page:page+per]
    sheet = Image.new('RGB', (W*cols, H*rows), BG); d = ImageDraw.Draw(sheet)
    for i, it in enumerate(chunk):
        s, k = it[0], it[1]
        p = f'{outdir}/{s}__{k}.png'
        x, y = (i % cols) * W, (i // cols) * H
        if os.path.exists(p): sheet.paste(fit(Image.open(p)), (x, y))
        d.text((x+3, y+2), f'{page+i} {s}/{k}'[:34], fill=(255, 255, 0))
    sheet.save(f'{prefix}_{page//per:02d}.png')
