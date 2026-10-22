"""Dibujos de las tarjetas de la barra de categorías de la tienda (public/img/tabs/<categoría>.webp).

Cada tarjeta lleva un dibujo de lo que hay dentro, sacado de las imágenes que ya usa la web: se recorta lo vacío, se
encaja en 256x192 (el doble de lo que se ve) y se guarda con transparencia. Las de pixel art se escalan sin suavizar.
Skills no tiene una imagen propia: es un abanico con tres iconos de clases.

    python3 tools/tab_art.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / 'public'
OUT = ROOT / 'img' / 'tabs'
W, H = 256, 192

# categoría: (imagen, pixel art, recorte en fracciones (izq, arriba, der, abajo) o None)
ART = {
    'rangos': ('img/ranks/royal.png', True, None),
    'cosmeticos': ('img/items/springcos/wings.webp', False, None),
    # La crate entera es muy ancha: solo el cofre y las armas del centro
    'crates': ('img/crates/luminite.webp', False, (0.27, 0.0, 0.73, 1.0)),
    'vfx': ('img/vfx/kill_knockout.webp', False, None),
    # (1.3.112) la piedra de 500x500, la más grande
    'protecciones': ('img/protecciones/500x500.webp', False, None),
}
# Skills: tres clases de colores distintos (fuego, rayo, vacío)
SKILL_ICONS = ['guerrero_llama', 'mago_rayo', 'filo_vacio']


def trimmed(path, crop=None):
    im = Image.open(ROOT / path).convert('RGBA')
    if crop:
        im = im.crop((round(crop[0] * im.width), round(crop[1] * im.height), round(crop[2] * im.width), round(crop[3] * im.height)))
    # Sin el halo suave que traen algunas imágenes (en la tarjeta se vería como un recuadro)
    im.putalpha(im.getchannel('A').point(lambda a: 0 if a < 110 else a))
    box = im.getchannel('A').getbbox()
    return im.crop(box) if box else im


def fit(im, w, h, pixel):
    k = min(w / im.width, h / im.height)
    if pixel and k >= 1:
        k = int(k)
    size = (max(1, round(im.width * k)), max(1, round(im.height * k)))
    return im.resize(size, Image.NEAREST if pixel else Image.LANCZOS)


def place(im):
    canvas = Image.new('RGBA', (W, H))
    canvas.alpha_composite(im, ((W - im.width) // 2, (H - im.height) // 2))
    return canvas


def skills_fan():
    import json

    classes = {c['id']: c for c in json.loads((ROOT.parent / 'config' / 'skills.json').read_text())['classes']}
    canvas = Image.new('RGBA', (W, H))
    side = 96
    for i, cid in enumerate(SKILL_ICONS):
        icon = Image.open(ROOT / classes[cid]['icon']).convert('RGBA').resize((side - 8, side - 8), Image.NEAREST)
        card = Image.new('RGBA', (side, side), (0, 0, 0, 0))
        ImageDraw.Draw(card).rounded_rectangle((0, 0, side - 1, side - 1), 12, fill=(12, 13, 18, 255), outline=(255, 255, 255, 235), width=3)
        mask = Image.new('L', (side - 8, side - 8), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, side - 9, side - 9), 8, fill=255)
        card.paste(icon, (4, 4), mask)
        angle = (-16, 0, 16)[i]
        card = card.rotate(-angle, resample=Image.BICUBIC, expand=True)
        x = W // 2 - card.width // 2 + (-62, 0, 62)[i]
        y = H // 2 - card.height // 2 + (10, -6, 10)[i]
        if i == 1:
            continue
        canvas.alpha_composite(card, (x, y))
    # La del centro, encima
    icon = Image.open(ROOT / classes[SKILL_ICONS[1]]['icon']).convert('RGBA').resize((side + 8, side + 8), Image.NEAREST)
    big = side + 16
    card = Image.new('RGBA', (big, big), (0, 0, 0, 0))
    ImageDraw.Draw(card).rounded_rectangle((0, 0, big - 1, big - 1), 14, fill=(12, 13, 18, 255), outline=(255, 255, 255, 255), width=3)
    mask = Image.new('L', (side + 8, side + 8), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, side + 7, side + 7), 9, fill=255)
    card.paste(icon, (4, 4), mask)
    canvas.alpha_composite(card, (W // 2 - big // 2, H // 2 - big // 2 - 6))
    return canvas


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for cat, (path, pixel, crop) in ART.items():
        im = place(fit(trimmed(path, crop), W, H, pixel))
        im.save(OUT / f'{cat}.webp', 'WEBP', quality=88, method=6)
    skills_fan().save(OUT / 'skills.webp', 'WEBP', quality=88, method=6)
    for f in sorted(OUT.glob('*.webp')):
        print(f.relative_to(ROOT), f.stat().st_size)


if __name__ == '__main__':
    main()
