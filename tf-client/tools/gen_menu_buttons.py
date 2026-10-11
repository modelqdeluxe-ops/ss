"""Botones de los menús de TF a partir de las dos láminas del dueño (11 oct 2026):

  1) menú principal: Tierras Fantásticas (madera, gemas y faroles), dos azules (Mundo local, Mods), gris y rojo
     (Opciones, Salir) y verde, morado y azul (WhatsApp, Web, Discord), ya colocados como van en pantalla;
  2) ajustes y navegación: placas de piedra con marco plateado y gemas ámbar.

Las láminas traen el fondo de cuadritos pintado (no es transparencia): se quita rellenando desde los bordes todo lo
claro y gris que toque el borde, y el contorno oscuro de cada botón hace de muro. Los píxeles del borde se suavizan y
los transparentes toman el color del vecino opaco (sin halos al filtrar ni en los mipmaps). Las láminas no van en el
repo, solo lo convertido. Uso:

  python3 tools/gen_menu_buttons.py lamina_menu.webp lamina_ajustes.webp

Sale:
  textures/gui/menu_main.png   las 8 placas del menú principal a su tamaño original (con mipmaps en el juego)
  textures/gui/menu_main.json  dónde está cada una, su cuerpo (lo que se pulsa), las zonas lisas que se pueden estirar
                               a lo ancho, sus gemas y faroles (para animarlos), su sitio en la composición y su color
                               (tint: el contorno de las letras)
  textures/gui/menu_glow.png   un punto de luz redondo y suave (luz de los faroles y de las gemas)
  textures/gui/button_slice.png la placa de piedra para los demás menús (TFButtonTheme): normal, ratón y desactivada
"""
import json
import os
import sys
from collections import deque

import numpy as np
from PIL import Image
from scipy import ndimage

GUI = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'tfclient',
                   'textures', 'gui')
PAD = 16  # separación entre placas en la textura (4 niveles de mipmap sin mezclar vecinas)

# Lámina 1: recorte de cada placa (x0, y0, x1, y1) y zonas lisas que se estiran (x relativas a la placa).
MENU = {
    'tf':     ((18, 12, 1290, 323), [(250, 480), (790, 1030)]),
    'blue1':  ((183, 325, 1128, 540), [(200, 390), (555, 760)]),
    'blue2':  ((183, 540, 1128, 757), [(200, 390), (555, 760)]),
    'gray':   ((29, 751, 656, 967), [(200, 470)]),
    'red':    ((671, 749, 1282, 967), [(180, 440)]),
    'green':  ((12, 969, 451, 1175), [(120, 300)]),
    'purple': ((470, 974, 852, 1176), [(140, 250)]),
    'blueb':  ((874, 971, 1297, 1178), [(135, 290)]),
}
# Ancho del hueco para el texto a cada lado del cuerpo (gemas y marco), en altos del cuerpo.
TEXT_SIDE = {'tf': 0.9, 'blue1': 1.25, 'blue2': 1.25}
# Lámina 2: la placa mediana de piedra; puntas fijas y el centro (liso) repetido en espejo hasta FILL_W.
NAV_BOX, NAV_CAPS, FILL_W = (132, 601, 791, 736), (115, 545), 1600


def background(rgb):
    """Fondo de cuadritos: claro, gris y unido al borde de la imagen."""
    a = rgb.astype(np.int32)
    lum, sat = a.mean(-1), a.max(-1) - a.min(-1)
    cand = (lum > 196) & (sat < 28)
    h, w = cand.shape
    bg = np.zeros_like(cand)
    q = deque()
    for y in range(h):
        for x in (0, w - 1):
            if cand[y, x] and not bg[y, x]:
                bg[y, x] = True
                q.append((y, x))
    for x in range(w):
        for y in (0, h - 1):
            if cand[y, x] and not bg[y, x]:
                bg[y, x] = True
                q.append((y, x))
    while q:
        y, x = q.popleft()
        for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
            if 0 <= ny < h and 0 <= nx < w and cand[ny, nx] and not bg[ny, nx]:
                bg[ny, nx] = True
                q.append((ny, nx))
    return bg


def cutout(path):
    """La lámina en RGBA sin el fondo, con el borde suavizado y sin halos."""
    rgb = np.asarray(Image.open(path).convert('RGB'))
    bg = background(rgb)
    out = np.dstack([rgb, np.where(bg, 0, 255)]).astype(np.float32)
    # Borde: los píxeles claros pegados al fondo son mezcla de contorno y cuadritos; se vuelven semitransparentes
    near = ndimage.binary_dilation(bg, iterations=2) & ~bg
    lum = rgb.astype(np.float32).mean(-1)
    sat = rgb.max(-1).astype(np.float32) - rgb.min(-1)
    soft = near & (lum > 110) & (sat < 60)
    k = np.clip((235.0 - lum) / 175.0, 0.0, 1.0)
    out[..., 3] = np.where(soft, 255.0 * k, out[..., 3])
    dark = np.array([22, 16, 12], np.float32)
    out[..., :3] = np.where(soft[..., None], dark + (out[..., :3] - dark) * 0.25, out[..., :3])
    # Lo claro y gris que quede a 2 px o menos de lo transparente es fondo: fuera del todo
    dist = ndimage.distance_transform_edt(out[..., 3] >= 20)
    o = out.astype(np.int32)
    grey_light = (o[..., :3].mean(-1) > 150) & (o[..., :3].max(-1) - o[..., :3].min(-1) < 50)
    out[..., 3] = np.where(grey_light & (dist <= 2.5), 0, out[..., 3])
    return bleed(out)


def bleed(a):
    """Color del vecino opaco en los píxeles transparentes (el filtrado lineal y los mipmaps no meten blanco)."""
    solid = a[..., 3] > 127
    idx = ndimage.distance_transform_edt(~solid, return_distances=False, return_indices=True)
    a[..., :3] = np.where(solid[..., None], a[..., :3], a[idx[0], idx[1], :3])
    return a


def body_of(sprite):
    """Cuerpo de la placa: el rectángulo del marco dorado (filas y columnas con mucho oro)."""
    r, g, b, al = (sprite[..., i] for i in range(4))
    gold = (r > 190) & (g > 120) & (g < 215) & (b < 110) & (al > 128)
    h, w = gold.shape
    rows = np.where(gold.sum(1) > 0.4 * w)[0]
    cols = np.where(gold.sum(0) > 0.3 * h)[0]
    return [int(cols.min()), int(rows.min()), int(cols.max()) + 1, int(rows.max()) + 1]


def blobs(mask, min_area):
    lab, n = ndimage.label(mask)
    out = []
    for i, sl in enumerate(ndimage.find_objects(lab)):
        area = int((lab[sl] == i + 1).sum())
        if area < min_area:
            continue
        cy, cx = ndimage.center_of_mass(lab == i + 1)
        r = max(sl[0].stop - sl[0].start, sl[1].stop - sl[1].start) / 2
        out.append([round(float(cx), 1), round(float(cy), 1), round(float(r), 1)])
    return out


def gems_of(sprite):
    """Gemas azules (para la chispa) y luz de los faroles (para que titile)."""
    r, g, b, al = (sprite[..., i].astype(np.int32) for i in range(4))
    cyan = (b > 200) & (g > 140) & (r < 140) & (al > 128)
    flame = (r > 245) & (g > 215) & (b > 120) & (al > 128)  # el centro blanco y cálido de la llama
    return blobs(cyan, 400), blobs(flame, 60)


def pack(sprites):
    """Filas de placas en una textura de 2048 de ancho; posiciones múltiplos de 16."""
    order = sorted(sprites, key=lambda k: -sprites[k].shape[0])
    x = y = PAD
    row_h = 0
    pos = {}
    for k in order:
        h, w = sprites[k].shape[:2]
        if x + w + PAD > 2048:
            x, y = PAD, y + row_h + PAD
            row_h = 0
        pos[k] = (x, y)
        x += (w + PAD + 15) // 16 * 16
        row_h = max(row_h, (h + 15) // 16 * 16)
    H = (y + row_h + PAD + 15) // 16 * 16
    atlas = np.zeros((H, 2048, 4), np.float32)
    for k, (px, py) in pos.items():
        h, w = sprites[k].shape[:2]
        atlas[py:py + h, px:px + w] = sprites[k]
    return atlas, pos


def menu(path):
    sheet = cutout(path)
    sprites, info = {}, {}
    for name, ((x0, y0, x1, y1), fills) in MENU.items():
        s = sheet[y0:y1, x0:x1].copy()
        if name == 'blue1':  # las dos azules se tocan por las gemas: cada una se queda con su mitad
            s[540 - y0:, :, 3] = 0
        if name == 'blue2':
            s[:0, :, 3] = 0
        sprites[name] = s
        body = body_of(s)
        bh = body[3] - body[1]
        side = TEXT_SIDE.get(name, 0.2) * bh
        # el color de la placa (mediana del hueco del texto): las letras blancas llevan el contorno de ese color, oscuro
        side_px = TEXT_SIDE.get(name, 0.2) * bh
        hole = s[int(body[1] + bh * 0.3):int(body[3] - bh * 0.3), int(body[0] + side_px):int(body[2] - side_px), :3]
        tint = '%06X' % int(''.join('%02X' % int(c) for c in np.median(hole.reshape(-1, 3), axis=0)), 16)
        sparks, lights = gems_of(s)
        # Faroles: solo los de Tierras Fantásticas, colgados fuera del marco (lo demás que brilla es oro)
        lights = [l for l in lights if name == 'tf' and (l[0] < body[0] - 30 or l[0] > body[2] + 30)]
        info[name] = {
            'body': body,
            'text': [round(body[0] + side), body[1], round(body[2] - side), body[3]],
            'fills': [list(f) for f in fills],
            'sparks': sparks,
            'lights': lights,
            'layout': [body[0] + x0, body[1] + y0, body[2] + x0, body[3] + y0],
            'tint': tint,
        }
    atlas, pos = pack(sprites)
    for name, (px, py) in pos.items():
        h, w = sprites[name].shape[:2]
        info[name]['uv'] = [px, py, w, h]
    Image.fromarray(np.clip(bleed(atlas), 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(GUI, 'menu_main.png'),
                                                                                  optimize=True)
    tops = [v['layout'][1] - v['body'][1] for v in info.values()]
    bottoms = [v['layout'][1] - v['body'][1] + v['uv'][3] for v in info.values()]
    data = {'atlas': [atlas.shape[1], atlas.shape[0]],
            'composition': {'top': min(tops), 'bottom': max(bottoms),
                            'center': round((info['tf']['layout'][0] + info['tf']['layout'][2]) / 2)},
            'plates': {k: {f: info[k][f] for f in ('uv', 'body', 'text', 'fills', 'sparks', 'lights', 'layout', 'tint')}
                       for k in MENU}}
    with open(os.path.join(GUI, 'menu_main.json'), 'w') as f:
        json.dump(data, f, indent=1)
    print('menu_main.png', atlas.shape[1], 'x', atlas.shape[0])
    for k in MENU:
        print(' ', k, info[k]['uv'], 'cuerpo', info[k]['body'], 'gemas', len(info[k]['sparks']), 'faroles',
              len(info[k]['lights']))


def nav(path):
    """Placa de piedra: puntas tal cual y el centro repetido en espejo; tres estados con PAD entre ellos."""
    sheet = cutout(path)
    x0, y0, x1, y1 = NAV_BOX
    s = sheet[y0:y1, x0:x1]
    h = s.shape[0]
    c0, c1 = NAV_CAPS
    left, mid, right = s[:, :c0], s[:, c0:c1], s[:, c1:]
    parts, done, flip = [], 0, False
    while done < FILL_W:
        part = mid[:, ::-1] if flip else mid
        part = part[:, :FILL_W - done]
        parts.append(part)
        done += part.shape[1]
        flip = not flip
    plate = np.concatenate([left] + parts + [right], axis=1)
    a = plate.copy()
    rgb = a[..., :3]
    amber = (rgb[..., 0] > 170) & (rgb[..., 0] - rgb[..., 2] > 90) & (a[..., 3] > 128)
    # Ratón encima: piedra y marco más claros y las gemas encendidas con un brillo alrededor
    hover = a.copy()
    hover[..., :3] = np.clip(hover[..., :3] * 1.18 + 6, 0, 255)
    glow = ndimage.gaussian_filter(amber.astype(np.float32), 6) * 1.6
    hover[..., 0] = np.clip(hover[..., 0] + glow * 120, 0, 255)
    hover[..., 1] = np.clip(hover[..., 1] + glow * 70, 0, 255)
    hover[..., 2] = np.clip(hover[..., 2] + glow * 10, 0, 255)
    # Desactivada: gris y apagada
    off = a.copy()
    gray = off[..., :3].mean(-1, keepdims=True)
    off[..., :3] = (gray * 0.8 + off[..., :3] * 0.2) * 0.62
    W = plate.shape[1]
    out = np.zeros((3 * h + 4 * PAD, W, 4), np.float32)
    for i, st in enumerate((a, hover, off)):
        y = PAD + i * (h + PAD)
        out[y:y + h] = st
    Image.fromarray(np.clip(bleed(out), 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(GUI, 'button_slice.png'),
                                                                                optimize=True)
    print('button_slice.png', W, 'x', out.shape[0], 'placa', h, 'puntas', c0, W - (c0 + FILL_W), 'pad', PAD)


def glow():
    n = 64
    y, x = np.mgrid[0:n, 0:n] + 0.5
    d = np.hypot(x - n / 2, y - n / 2) / (n / 2)
    a = np.clip(np.exp(-d * d * 3.2) - 0.04, 0, 1) / 0.96
    out = np.dstack([np.full((n, n), 255.0)] * 3 + [a * 255])
    Image.fromarray(out.astype(np.uint8), 'RGBA').save(os.path.join(GUI, 'menu_glow.png'), optimize=True)


if __name__ == '__main__':
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    menu(sys.argv[1])
    nav(sys.argv[2])
    glow()
