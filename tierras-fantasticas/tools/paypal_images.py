#!/usr/bin/env python3
"""(2026-10-25, el dueño: «en PayPal sale sin ninguna descripción del producto, imagen o nada») PayPal solo acepta imágenes
JPG, PNG o GIF en los artículos del pedido (image_url), y las de la tienda son WebP. Este script hace una copia JPG
pequeña (320 px) de la imagen de cada producto de config/products.json en public/img/pp/… y apunta cuáles hay en
src/paypal-images.json (la web solo manda a PayPal las que existen).

    python3 tools/paypal_images.py
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUBLIC = os.path.join(ROOT, 'public')


def jpg_path(webp):
    rel = webp.lstrip('/')
    assert rel.startswith('img/')
    return 'img/pp/' + os.path.splitext(rel[4:])[0] + '.jpg'


def main():
    with open(os.path.join(ROOT, 'config', 'products.json'), encoding='utf-8') as f:
        data = json.load(f)
    products = data['products'] if isinstance(data, dict) else data
    done = {}
    for p in products:
        img = p.get('image')
        if not img or img in done:
            continue
        src = os.path.join(PUBLIC, img.lstrip('/'))
        if not os.path.isfile(src):
            continue
        out = jpg_path(img)
        dst = os.path.join(PUBLIC, out)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        im = Image.open(src).convert('RGBA')
        im.thumbnail((320, 320))
        bg = Image.new('RGB', im.size, (255, 255, 255))
        bg.paste(im, mask=im.split()[3])
        bg.save(dst, 'JPEG', quality=85, optimize=True)
        done[img] = out
    with open(os.path.join(ROOT, 'src', 'paypal-images.json'), 'w', encoding='utf-8') as f:
        json.dump(done, f, indent=1, ensure_ascii=False)
        f.write('\n')
    size = sum(os.path.getsize(os.path.join(PUBLIC, v)) for v in done.values())
    print(f'{len(done)} imágenes JPG para PayPal ({size // 1024} KB)')


if __name__ == '__main__':
    main()
