"""Calco de iconos: toma la ESTRUCTURA de una referencia de 32x32 (silueta, zonas y sombreado) y la vuelve a pintar con
la paleta del pad y su contorno azul marino. Así los iconos nuevos salen con proporciones de objeto de verdad (como
pidió el dueño: «busca modelos ya hechos y adáptalos») y con el mismo estilo que los dibujados a mano.

material(r, g, b) -> nombre de rampa; cada zona se reparte en 5 tonos según su luz (de la referencia).
"""
import colorsys

from PIL import Image

from kit import Icon, OUT


def hls(c):
    r, g, b = c[:3]
    return colorsys.rgb_to_hls(r / 255, g / 255, b / 255)


def calco(path, material, ramps, drop_dark=0.16, crop_square=True, levels=None, post=None):
    """
    path: PNG de referencia (32x32).
    material: función (r, g, b) -> clave de ramps (o None para quitar el píxel).
    ramps: {clave: [5 colores de claro a oscuro]}.
    drop_dark: los píxeles del borde más oscuros que esto se quitan (su contorno; ponemos el nuestro).
    levels: {clave: [4 umbrales de luz]} para fijar dónde cambia cada tono (si no, por cuantiles).
    post: función (ic, mat) para retoques a mano.
    """
    im = Image.open(path).convert('RGBA')
    if crop_square and im.height > im.width:
        im = im.crop((0, 0, im.width, im.width))
    n = im.width
    px = {(x, y): im.getpixel((x, y)) for y in range(n) for x in range(n)}
    solid = {p for p, c in px.items() if c[3] >= 128}

    # 1) fuera su contorno oscuro (solo el del borde de la silueta)
    def border(p):
        x, y = p
        return any((x + dx, y + dy) not in solid for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))

    keep = {p for p in solid if not (border(p) and hls(px[p])[1] < drop_dark)}

    # 2) materiales
    mat = {}
    for p in keep:
        m = material(*px[p][:3])
        if m is not None:
            mat[p] = m

    # 3) tonos por luz dentro de cada material
    ic = Icon(n)
    by = {}
    for p, m in mat.items():
        by.setdefault(m, []).append(hls(px[p])[1])
    cuts = {}
    for m, ls in by.items():
        if levels and m in levels:
            cuts[m] = levels[m]
        else:
            # por luz real dentro de la zona: conserva el contraste de la referencia
            lo, hi = min(ls), max(ls)
            span = max(hi - lo, 1e-6)
            cuts[m] = [lo + span * t for t in (0.84, 0.62, 0.4, 0.2)]
    for (x, y), m in mat.items():
        l = hls(px[(x, y)])[1]
        c = cuts[m]
        i = 0 if l >= c[0] else 1 if l >= c[1] else 2 if l >= c[2] else 3 if l >= c[3] else 4
        ic.px(x, y, ramps[m][i])
    if post:
        post(ic, mat)
    return ic.outline(OUT)
