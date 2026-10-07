import sys, json, os
from PIL import Image, ImageDraw, ImageChops
outdir, lst, prefix, per = sys.argv[1], json.load(open(sys.argv[2])), sys.argv[3], int(sys.argv[4])
BG = (20, 26, 38)
def fit(im, W, H):
    diff = ImageChops.difference(im.convert('RGB'), Image.new('RGB', im.size, BG)).convert('L').point(lambda v: 255 if v > 12 else 0)
    bb = diff.getbbox()
    if bb:
        pad = 12
        im = im.crop((max(0, bb[0]-pad), max(0, bb[1]-pad), min(im.width, bb[2]+pad), min(im.height, bb[3]+pad)))
    k = min(W / im.width, H / im.height)
    im = im.resize((max(1, int(im.width*k)), max(1, int(im.height*k))))
    cell = Image.new('RGB', (W, H), BG); cell.paste(im, ((W-im.width)//2, (H-im.height)//2)); return cell
for page in range(0, len(lst), per):
    chunk = lst[page:page+per]
    W, H = 340, 340
    sheet = Image.new('RGB', (W*3, H*len(chunk)), BG)
    d = ImageDraw.Draw(sheet)
    for r, (s, k) in enumerate(chunk):
        for c in range(3):
            p = f'{outdir}/{s}__{k}__{c}.png'
            if os.path.exists(p):
                sheet.paste(fit(Image.open(p), W, H), (c*W, r*H))
        d.text((6, r*H+4), f'{s}/{k}', fill=(255, 255, 0))
    sheet.save(f'{prefix}_{page//per}.png')
