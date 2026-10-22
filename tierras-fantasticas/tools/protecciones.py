"""Las imágenes de las piedras de protección de pago para la tienda (sección Protecciones, 1.3.112): cada una en 3D, en
la vista de inventario, con las texturas del TF Client (tf-client/tools/gen_claim_blocks.py: la cara, el lado y la tapa,
con su brillo encima) → public/img/protecciones/<tamaño>.webp.

    python3 tools/protecciones.py
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_items as B  # noqa: E402

TEX = os.path.join(HERE, '..', '..', 'tf-client', 'src', 'main', 'resources', 'assets', 'tfclient', 'textures', 'block', 'claims')
OUT = os.path.join(HERE, '..', 'public', 'img', 'protecciones')
SIZES = ['250x250', '300x300', '500x500']


def capa(size, part, tmp):
    """La textura con su brillo encima (como se ve en el juego: el brillo va a plena luz)."""
    base = Image.open(os.path.join(TEX, f'proteccion_{size}_{part}.png')).convert('RGBA')
    glow = os.path.join(TEX, f'proteccion_{size}_{part}_glow.png')
    if os.path.exists(glow):
        g = Image.open(glow).convert('RGBA').resize(base.size, Image.NEAREST)
        base.alpha_composite(g)
    path = os.path.join(tmp, f'{size}_{part}.png')
    base.save(path)
    return {'path': path, 'rel': path, 'w': base.width, 'h': base.height, 'fw': base.width, 'fh': base.height, 'frames': None,
            'frametime': 1, 'emissive': False}


def main():
    import tempfile
    tmp = tempfile.mkdtemp()
    os.makedirs(OUT, exist_ok=True)
    faces = {'north': 'f', 'south': 's', 'east': 's', 'west': 's', 'up': 't', 'down': 'b'}
    for size in SIZES:
        texs = {'f': capa(size, 'front', tmp), 's': capa(size, 'side', tmp), 't': capa(size, 'top', tmp),
                'b': capa(size, 'bottom', tmp)}
        model = {'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16],
                               'faces': {k: {'texture': '#' + v, 'uv': [0, 0, 16, 16]} for k, v in faces.items()}}],
                 # de lado, con la cara (la de las letras TF) mirando a la izquierda, como un bloque en el inventario
                 'display': {'gui': {'rotation': [30, 45, 0], 'translation': [0, 0, 0], 'scale': [0.625] * 3}}}
        out = os.path.join(OUT, f'{size}.webp')
        B.finish_thumb(B.render(model, texs, B.THUMB), B.THUMB, out)
        print('→', os.path.relpath(out))


if __name__ == '__main__':
    main()
