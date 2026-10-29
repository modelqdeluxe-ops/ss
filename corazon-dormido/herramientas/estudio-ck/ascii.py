import sys
from PIL import Image
im=Image.open(sys.argv[1]).convert('RGBA'); im=im.crop(im.getbbox()); px=im.load()
keys={}; letras='abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
for y in range(im.height):
    row=''
    for x in range(im.width):
        c=px[x,y]
        if c[3]==0: row+='.'; continue
        if c[:3] not in keys: keys[c[:3]]=letras[len(keys)]
        row+=keys[c[:3]]
    print(row)
for c,k in keys.items(): print(k,'#%02x%02x%02x'%c, end='  ')
print()
