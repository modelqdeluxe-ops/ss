"""Simulador del menú principal de TF Client (mismas medidas y la misma lógica de dibujo que el código Java).

Reproduce TitleScreenMixin (posiciones), TFLogoRenderer (emblema y halo), TFMenuButton + TFMenuPaint (botones dibujados
en píxeles reales: relleno, borde, halo, iconos) y la letra Cinzel tal como la pinta Minecraft (glifos a 4x, muestreo
sin suavizar). Uso: python3 tools/menu_sim.py [carpeta_salida] [filtro]  (capturas a 1280x720..2560x1440, escalas 1-6)
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'tfclient')
GUI = ROOT + '/textures/gui'
FONT = ROOT + '/font/titanone.ttf'  # la letra de los botones del menú principal (TFMenuButton.MENU_FONT_AT_SCALE)
ASC, DESC, UPEM = 970, -175, 1000


# ------------------------------------------------------------------------------------------------ lienzo (framebuffer)
class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = np.zeros((h, w, 3), np.float32)
        self.scissor = None

    def _clip(self, x0, y0, x1, y1):
        cx0, cy0, cx1, cy1 = 0, 0, self.w, self.h
        if self.scissor:
            cx0, cy0, cx1, cy1 = self.scissor
        return max(x0, cx0), max(y0, cy0), min(x1, cx1), min(y1, cy1)

    def fill(self, x0, y0, x1, y1, argb):
        """GuiGraphics.fill: color ARGB (int) con mezcla normal."""
        a = ((argb >> 24) & 255) / 255.0
        if a <= 0:
            return
        c = np.array([(argb >> 16) & 255, (argb >> 8) & 255, argb & 255], np.float32) / 255.0
        x0, y0, x1, y1 = self._clip(int(x0), int(y0), int(x1), int(y1))
        if x1 <= x0 or y1 <= y0:
            return
        reg = self.px[y0:y1, x0:x1]
        reg[:] = reg * (1 - a) + c * a

    def fill_gradient(self, x0, y0, x1, y1, top, bot):
        """GuiGraphics.fillGradient: interpolación vertical por centro de píxel (como la GPU)."""
        if y1 <= y0 or x1 <= x0:
            return
        h = y1 - y0
        for y in range(y0, y1):
            t = (y + 0.5 - y0) / h
            self.fill(x0, y, x1, y + 1, lerp_argb(top, bot, t))

    def composite(self, rgba, x, y, tint=(1, 1, 1, 1), additive=False):
        """Pega una imagen RGBA (float 0..1, h x w x 4) en (x, y) multiplicada por tint."""
        h, w = rgba.shape[:2]
        x0, y0, x1, y1 = self._clip(x, y, x + w, y + h)
        if x1 <= x0 or y1 <= y0:
            return
        src = rgba[y0 - y:y1 - y, x0 - x:x1 - x]
        col = src[..., :3] * np.array(tint[:3], np.float32)
        a = src[..., 3:4] * tint[3]
        reg = self.px[y0:y1, x0:x1]
        if additive:
            reg[:] = np.minimum(1, reg + col * a)
        else:
            reg[:] = reg * (1 - a) + col * a

    def image(self):
        return Image.fromarray((np.clip(self.px, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGB')


def lerp_argb(c0, c1, t):
    t = min(1.0, max(0.0, t))
    out = 0
    for sh in (24, 16, 8, 0):
        a, b = (c0 >> sh) & 255, (c1 >> sh) & 255
        out |= int(round(a + (b - a) * t)) << sh
    return out


def argb(a, rgb):
    return (max(0, min(255, int(round(a * 255)))) << 24) | (rgb & 0xFFFFFF)


def with_alpha(c, k):
    """Multiplica la opacidad de un color ARGB."""
    return argb(((c >> 24) & 255) / 255.0 * k, c)


def to_float_img(im):
    return np.asarray(im.convert('RGBA'), np.float32) / 255.0


# ------------------------------------------------------------------------------------ TFMenuPaint (mismo algoritmo)
def sdf(px, py, w, h, r):
    """Distancia con signo al rectángulo redondeado (w x h, radio r) desde el punto (px, py); negativa dentro."""
    qx = abs(px - w / 2.0) - (w / 2.0 - r)
    qy = abs(py - h / 2.0) - (h / 2.0 - r)
    ox, oy = max(qx, 0.0), max(qy, 0.0)
    return math.sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0.0) - r


def cov(d):
    return min(1.0, max(0.0, 0.5 - d))


class Runs:
    """Junta píxeles seguidos del mismo color de una fila en un solo rectángulo (como TFMenuPaint.Runs)."""

    def __init__(self, g):
        self.g, self.x0, self.x1, self.y, self.c = g, 0, 0, 0, 0

    def put(self, x, y, c):
        if self.c != 0 and y == self.y and x == self.x1 and c == self.c:
            self.x1 += 1
            return
        self.flush()
        self.x0, self.x1, self.y, self.c = x, x + 1, y, c

    def flush(self):
        if self.c != 0 and ((self.c >> 24) & 255) > 0:
            self.g.fill(self.x0, self.y, self.x1, self.y + 1, self.c)
        self.c = 0


def round_fill(g, x, y, w, h, r, inset, ya, yb, top, bot):
    """Relleno del rectángulo redondeado metido `inset` px (dentro del borde), solo en las filas [ya, yb) y con
    degradado vertical top→bot repartido en esas filas."""
    ya, yb = max(ya, inset), min(yb, h - inset)
    if yb <= ya:
        return
    span = yb - ya

    def color_at(row):
        return lerp_argb(top, bot, (row + 0.5 - ya) / span)

    def edge(row):  # color en el borde de una fila (para fillGradient en bloque)
        return lerp_argb(top, bot, (row - ya) / span)

    # Filas rectas (todas las columnas dentro del borde)
    a, b = max(ya, r), min(yb, h - r)
    if b > a:
        g.fill_gradient(x + inset, y + a, x + w - inset, y + b, edge(a), edge(b))
    # Filas de las esquinas: el centro en bloque, las esquinas píxel a píxel
    runs = Runs(g)
    for rows in ((ya, min(yb, r)), (max(ya, h - r), yb)):
        if rows[1] <= rows[0]:
            continue
        g.fill_gradient(x + r, y + rows[0], x + w - r, y + rows[1], edge(rows[0]), edge(rows[1]))
        for row in range(rows[0], rows[1]):
            c = color_at(row)
            for col in list(range(0, r)) + list(range(w - r, w)):
                k = cov(sdf(col + 0.5, row + 0.5, w, h, r) + inset)
                if k > 0:
                    runs.put(x + col, y + row, with_alpha(c, k))
            runs.flush()


def round_ring(g, x, y, w, h, r, t, top, bot):
    """Borde de grosor t con degradado vertical (top arriba, bot abajo) y esquinas suavizadas."""
    def edge(row):
        return lerp_argb(top, bot, row / h)

    def color_at(row):
        return lerp_argb(top, bot, (row + 0.5) / h)

    g.fill_gradient(x, y + r, x + t, y + h - r, edge(r), edge(h - r))
    g.fill_gradient(x + w - t, y + r, x + w, y + h - r, edge(r), edge(h - r))
    g.fill_gradient(x + r, y, x + w - r, y + t, edge(0), edge(t))
    g.fill_gradient(x + r, y + h - t, x + w - r, y + h, edge(h - t), edge(h))
    runs = Runs(g)
    for row in list(range(0, r)) + list(range(h - r, h)):
        c = color_at(row)
        for col in list(range(0, r)) + list(range(w - r, w)):
            d = sdf(col + 0.5, row + 0.5, w, h, r)
            k = cov(d) - cov(d + t)
            if k > 0:
                runs.put(x + col, y + row, with_alpha(c, k))
        runs.flush()


def halo(g, x, y, w, h, r, size, color):
    """Halo suave fuera de la forma (sombra o brillo): opacidad (1 - d/size)^2 según la distancia d al borde."""
    if size <= 0 or ((color >> 24) & 255) == 0:
        return

    def k_of(d):
        f = 1.0 - min(1.0, max(0.0, d / size))
        return f * f

    for k in range(size):
        c = with_alpha(color, k_of(k + 0.5))
        g.fill(x + r, y - 1 - k, x + w - r, y - k, c)
        g.fill(x + r, y + h + k, x + w - r, y + h + k + 1, c)
        g.fill(x - 1 - k, y + r, x - k, y + h - r, c)
        g.fill(x + w + k, y + r, x + w + k + 1, y + h - r, c)
    runs = Runs(g)
    for row in list(range(-size, r)) + list(range(h - r, h + size)):
        for col in list(range(-size, r)) + list(range(w - r, w + size)):
            d = sdf(col + 0.5, row + 0.5, w, h, r)
            if d <= -0.5 or d >= size:
                continue
            k = (1.0 - cov(d)) * k_of(d)
            if k > 0:
                runs.put(x + col, y + row, with_alpha(color, k))
        runs.flush()


# ------------------------------------------------------------------------------------ iconos (TFMenuIcons, mismo dibujo)
ICON = 9          # tamaño del icono en píxeles de interfaz
ICON_SS = 4       # supermuestreo por lado


def _seg(px, py, ax, ay, bx, by):
    vx, vy, wx, wy = bx - ax, by - ay, px - ax, py - ay
    t = max(0.0, min(1.0, (wx * vx + wy * vy) / (vx * vx + vy * vy)))
    dx, dy = wx - vx * t, wy - vy * t
    return math.sqrt(dx * dx + dy * dy)


def _box(px, py, cx, cy, hx, hy, rr=0.0):
    qx, qy = abs(px - cx) - hx + rr, abs(py - cy) - hy + rr
    return math.sqrt(max(qx, 0) ** 2 + max(qy, 0) ** 2) + min(max(qx, qy), 0) - rr


def _circ(px, py, cx, cy, r):
    return math.hypot(px - cx, py - cy) - r


def _tri_right(px, py):
    # Triángulo de "jugar" con las puntas algo redondeadas (distancia a un triángulo menos 0,8)
    a, b, c = (4.6, 3.2), (12.6, 8.0), (4.6, 12.8)
    inside = True
    for (x0, y0), (x1, y1) in ((a, b), (b, c), (c, a)):
        if (x1 - x0) * (py - y0) - (y1 - y0) * (px - x0) < 0:
            inside = False
    d = min(_seg(px, py, *a, *b), _seg(px, py, *b, *c), _seg(px, py, *c, *a))
    return (-d if inside else d) - 0.9


def icon_alpha(name, px, py):
    """Opacidad 0..1 del icono en el punto (px, py) de un lienzo de 16x16 unidades."""
    if name == 'play':
        return 1.0 if _tri_right(px, py) <= 0 else 0.0
    if name == 'world':  # bloque en perspectiva: tapa clara, caras con dos tonos
        cx = 8.0
        top = abs(px - cx) / 6.2 + abs(py - 5.0) / 3.1 <= 1.0
        if top:
            return 1.0
        if 2.0 <= px <= 14.0 and 5.0 <= py:
            # bajo la tapa: lado izquierdo o derecho, cortado abajo en V
            if py <= 11.0 + (6.0 - abs(px - cx)) * 0.5 and py >= 5.0:
                if abs(px - cx) <= 6.0:
                    edge = abs(px - cx) < 0.55
                    if edge:
                        return 0.0
                    return 0.62 if px < cx else 0.36
        return 0.0
    if name == 'mods':  # pieza de puzle
        body = _box(px, py, 7.0, 9.0, 4.6, 4.6, 1.0)
        knob_top = _circ(px, py, 7.0, 3.6, 2.0)
        knob_right = _circ(px, py, 12.9, 9.0, 2.0)
        hole_left = _circ(px, py, 2.4, 9.0, 1.7)
        d = min(body, knob_top, knob_right)
        d = max(d, -hole_left)
        return 1.0 if d <= 0 else 0.0
    if name == 'options':  # engranaje
        r = math.hypot(px - 8.0, py - 8.0)
        ang = math.atan2(py - 8.0, px - 8.0)
        teeth = 8
        a = (ang / (2 * math.pi) * teeth) % 1.0
        tooth = abs(a - 0.5) < 0.22
        outer = 6.9 if tooth else 5.2
        return 1.0 if 2.1 <= r <= outer else 0.0
    if name == 'quit':  # botón de apagar
        r = math.hypot(px - 8.0, py - 8.6)
        ang = math.degrees(math.atan2(px - 8.0, -(py - 8.6)))  # 0 = arriba
        ring = abs(r - 5.4) <= 0.95 and abs(ang) >= 38
        bar = _seg(px, py, 8.0, 1.6, 8.0, 7.6) <= 0.95
        return 1.0 if ring or bar else 0.0
    if name == 'web':  # globo
        r = math.hypot(px - 8.0, py - 8.0)
        ring = abs(r - 6.0) <= 0.8
        meridian = abs(math.hypot((px - 8.0) / 2.6, (py - 8.0) / 6.0) - 1.0) * 2.6 <= 0.75
        eq = abs(py - 8.0) <= 0.7 and r <= 6.0
        lat = (abs(py - 5.0) <= 0.6 or abs(py - 11.0) <= 0.6) and r <= 5.6
        return 1.0 if ring or meridian or eq else (0.0 if not lat else 1.0)
    if name == 'whatsapp':  # bocadillo con colita y teléfono (TFMenuIcons.WHATSAPP)
        ring = abs(math.hypot(px - 8.4, py - 7.6) - 6.0) <= 0.95
        tail = _seg(px, py, 2.4, 13.8, 4.0, 11.0) <= 1.0 or _seg(px, py, 2.4, 13.8, 5.4, 12.9) <= 1.0
        ang = math.degrees(math.atan2(py - 5.8, px - 10.3))
        band = abs(math.hypot(px - 10.3, py - 5.8) - 4.3) <= 0.7 and 92 <= ang <= 178
        ends = _circ(px, py, 5.9, 5.7, 1.15) <= 0 or _circ(px, py, 10.2, 10.1, 1.15) <= 0
        return 1.0 if ring or tail or band or ends else 0.0
    if name == 'discord':  # mando con dos ojos (sin marca)
        body = _box(px, py, 8.0, 8.4, 6.6, 4.4, 3.2)
        ear_l = _circ(px, py, 4.0, 4.6, 1.8)
        ear_r = _circ(px, py, 12.0, 4.6, 1.8)
        d = min(body, ear_l, ear_r)
        eye = min(_box(px, py, 5.6, 8.4, 1.15, 1.45, 1.1), _box(px, py, 10.4, 8.4, 1.15, 1.45, 1.1))
        d = max(d, -eye)
        return 1.0 if d <= 0 else 0.0
    return 0.0


_icon_cache = {}


def icon_image(name, n):
    """Icono de n x n píxeles reales (blanco con opacidad), supermuestreado ICON_SS x ICON_SS."""
    key = (name, n)
    if key in _icon_cache:
        return _icon_cache[key]
    out = np.zeros((n, n, 4), np.float32)
    out[..., :3] = 1.0
    for j in range(n):
        for i in range(n):
            acc = 0.0
            for sj in range(ICON_SS):
                for si in range(ICON_SS):
                    px = (i + (si + 0.5) / ICON_SS) * 16.0 / n
                    py = (j + (sj + 0.5) / ICON_SS) * 16.0 / n
                    acc += icon_alpha(name, px, py)
            out[j, i, 3] = acc / (ICON_SS * ICON_SS)
    _icon_cache[key] = out
    return out


# ------------------------------------------------------------------------------------------------ texto (Cinzel en MC)
_fonts = {}


def oversample(S):
    """TFMenuButton.fontFor: cinzel_1..cinzel_6 (4 = cinzel.json), glifos a la escala de la interfaz."""
    return max(1, min(6, S))


def font_os(os_):
    if os_ not in _fonts:
        em = UPEM * 10.0 * os_ / (ASC - DESC)  # stbtt_ScaleForPixelHeight(10 x oversample)
        _fonts[os_] = ImageFont.truetype(FONT, size=em)
    return _fonts[os_]


def text_width(s, S=4):
    """Font.width en píxeles de interfaz (avances en coma flotante; Minecraft redondea hacia arriba)."""
    return math.ceil(font_os(64).getlength(s) / 64)


def draw_text(g, s, gx, gy, S, rgb, alpha=1.0, dy_real=0, scale=1.0, os_=None):
    """drawString(font, s, gx, gy) con la letra TTF: glifos rasterizados a `oversample` px por px de interfaz (sin
    hinting, como stb_truetype: se dibuja a 8x y se reduce por promedio) y muestreados sin suavizar."""
    os_ = os_ or oversample(S)
    big = 8
    f = font_os(os_ * big)
    base4 = 10.0 * ASC / (ASC - DESC) + 1 - 3  # línea base respecto a y (en píxeles de interfaz)
    B = 10 * os_
    w = int(f.getlength(s) / big) + 16
    w += (-w) % 1
    im = Image.new('L', (w * big, 16 * os_ * big), 0)
    ImageDraw.Draw(im).text((0, B * big), s, font=f, fill=255, anchor='ls')
    im = im.resize((w, 16 * os_), Image.BOX)
    k = S * scale / os_
    tw, th = max(1, int(round(im.width * k))), max(1, int(round(im.height * k)))
    small = im.resize((tw, th), Image.NEAREST)
    arr = np.zeros((th, tw, 4), np.float32)
    arr[..., 0] = rgb[0] / 255.0
    arr[..., 1] = rgb[1] / 255.0
    arr[..., 2] = rgb[2] / 255.0
    arr[..., 3] = np.asarray(small, np.float32) / 255.0
    x = int(round(gx * S))
    y = int(round((gy + base4 * scale) * S - B * k)) + dy_real
    g.composite(arr, x, y, (1, 1, 1, alpha))


# ------------------------------------------------------------------------------------------------ botón (TFMenuButton)
import json

PRIMARY, SECONDARY, DANGER = 'primary', 'secondary', 'danger'
GAP = 3          # entre icono y texto (interfaz)
TEXT_UP = 4.6    # el texto se dibuja en el centro - TEXT_UP (centrado a la vista de Titan One)
INK, INK_HOT, OUTLINE = 0xE7E1D5, 0xFFFBF2, 0x140A04
MAIN = ['tf', 'blue1', 'blue2', 'gray', 'red', 'green', 'purple', 'blueb']
ROWS = [[0], [1], [2], [3, 4], [5, 6, 7]]
SPAN = 1.7  # cada fila mide lo que Tierras Fantásticas en la lámina por SPAN (una columna limpia)
K_MAX, LOGO_FRAC, BOTTOM_MARGIN = 0.16, 0.44, 10  # el emblema: al menos el 44 % del alto
ROW_GAP = 50  # separación entre filas (en la lámina eran 70-100: más juntas, placas más grandes)
CAP_HEIGHT, CAP_FILL, TF_CAP_FILL, INTERIOR = 6.2, 0.5, 0.52, 0.7
LABELS = ['Tierras Fantásticas', 'Mundo local', 'Mods', 'Opciones', 'Salir', 'WhatsApp', 'Web', 'Discord']
_menu = {}


def ease(t):
    return t * t * (3 - 2 * t)


def mix_rgb(a, b, t):
    return lerp_argb(a | 0xFF000000, b | 0xFF000000, t) & 0xFFFFFF


def rgb3(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def menu_data():
    if not _menu:
        _menu['json'] = json.load(open(GUI + '/menu_main.json'))
        _menu['atlas'] = Image.open(GUI + '/menu_main.png').convert('RGBA')
        _menu['glow'] = Image.open(GUI + '/menu_glow.png').convert('RGBA')
        _menu['nav'] = Image.open(GUI + '/button_slice.png').convert('RGBA')
    return _menu


def snapS(v, S):
    return round(v * S) / S


class Placed:
    """TFMenuPlates.Placed: escala por la altura del cuerpo y zonas lisas estiradas a lo ancho."""

    def __init__(self, p, bx, by, bw, bh, S):
        self.p = p
        body = p['body']
        self.k = k = bh / (body[3] - body[1])
        # cada lado de la gema del centro se estira lo suyo: la gema cae en el centro del botón (TFMenuPlates.Placed)
        mid = (body[0] + body[2]) / 2
        near = [g[0] for g in p['sparks'] if abs(g[0] - mid) < (body[2] - body[0]) * 0.1]
        anchor = sum(near) / len(near) if near else mid
        fill_l = sum(f[1] - f[0] for f in p['fills'] if f[1] <= anchor)
        fill_r = sum(f[1] - f[0] for f in p['fills'] if f[1] > anchor)
        fixed_l, fixed_r = anchor - body[0] - fill_l, body[2] - anchor - fill_r
        if fill_l > 0 and fill_r > 0:
            kl = max(k * 0.25, (bw / 2 - fixed_l * k) / fill_l)
            kr = max(k * 0.25, (bw / 2 - fixed_r * k) / fill_r)
        else:
            tot, extra = fill_l + fill_r, bw - (body[2] - body[0]) * k
            kl = kr = max(k * 0.25, k + extra / tot) if tot > 0 else k
        src, scale = [0.0], []
        for f in p['fills']:
            src += [f[0], f[1]]
            scale += [k, kl if f[1] <= anchor else kr]
        src.append(p['uv'][2])
        scale.append(k)
        rel = [0.0]
        for j in range(1, len(src)):
            rel.append(rel[-1] + (src[j] - src[j - 1]) * scale[j - 1])
        self.src = src
        body_rel = self._at(rel, src, body[0])
        self.left = bx - body_rel
        self.top = by - body[1] * k
        self.gui = [snapS(self.left + r, S) for r in rel]

    @staticmethod
    def _at(out, inn, sx):
        for j in range(1, len(inn)):
            if sx <= inn[j] or j == len(inn) - 1:
                t = (sx - inn[j - 1]) / max(1e-3, inn[j] - inn[j - 1])
                return out[j - 1] + (out[j] - out[j - 1]) * t
        return out[0]

    def x(self, sx):
        return self._at(self.gui, self.src, sx)

    def y(self, sy):
        return self.top + sy * self.k


def draw_plate(g, S, at, tint=(1, 1, 1, 1), additive=False, dy=0.0):
    """TFMenuPlates.draw: pieza a pieza (con mipmaps y filtro lineal en el juego: aquí LANCZOS)."""
    m = menu_data()
    p = at.p
    u, v, w, h = p['uv']
    hh = h * at.k
    for j in range(1, len(at.src)):
        sx0, sx1 = at.src[j - 1], at.src[j]
        gx0, gx1 = at.gui[j - 1], at.gui[j]
        if sx1 - sx0 < 0.5 or gx1 - gx0 <= 0:
            continue
        u0 = round(sx0)
        uw = max(1, round(sx1) - u0)
        piece = m['atlas'].crop((u + u0, v, u + u0 + uw, v + h))
        rw, rh = round((gx1 - gx0) * S), round(hh * S)
        if rw <= 0 or rh <= 0:
            continue
        piece = piece.convert('RGBa').resize((rw, rh), Image.LANCZOS).convert('RGBA')
        g.composite(to_float_img(piece), round(gx0 * S), round((at.top + dy) * S), tint, additive)


def draw_glow(g, S, img, x, y, r, tint):
    n = max(1, round(2 * r * S))
    im = img.resize((n, n), Image.BILINEAR)
    g.composite(to_float_img(im), round((x - r) * S), round((y - r) * S), tint, additive=True)


def draw_nav(g, S, x, y, w, h, hover, active=True):
    """TFButtonTheme.drawPlate: puntas a la altura del botón y el centro cortado; estado ratón fundido encima."""
    tex = menu_data()['nav']
    PH, PAD, CL, CR = 135, 16, 115, 114
    fill = tex.width - CL - CR

    def state(i, a):
        k = h / PH
        cl, cr = CL * k, CR * k
        if cl + cr > w:
            f = w / (cl + cr)
            cl, cr = cl * f, cr * f
        xl, xr = snapS(x + cl, S), snapS(x + w - cr, S)
        v = PAD + i * (PH + PAD)
        parts = [(x, xl - x, 0, CL)]
        at = xl
        while at < xr - 0.01:
            seg = min(fill * k, xr - at)
            parts.append((at, seg, CL, max(1, min(fill, round(seg / k)))))
            at += seg
        parts.append((xr, x + w - xr, tex.width - CR, CR))
        for px, pw, u, uw in parts:
            rw = round((px + pw) * S) - round(px * S)
            if rw <= 0:
                continue
            piece = tex.crop((u, v, u + uw, v + PH)).convert('RGBa').resize((rw, round(h * S)), Image.LANCZOS)
            g.composite(to_float_img(piece.convert('RGBA')), round(px * S), round(y * S), (1, 1, 1, a))

    if not active:
        state(2, 1)
    else:
        state(0, 1)
        if hover > 0.01:
            state(1, hover)


def paint_content(g, S, x0, x1, cy, label, icon, kind, size, e, active=True, shine=None, tint='806040'):
    """TFMenuButton.paintContent: icono y texto centrados, contorno oscuro de max(1, 0.5 S) píxeles reales; Tierras
    Fantásticas en oro (degradado, luz detrás y brillo que cruza en `shine`, 0..1)."""
    ink = 0x9A9AA6 if not active else mix_rgb(INK, INK_HOT, e)
    n = max(1, min(12, round(S * size)))
    if n > 1 and n / S > size * 1.1:
        n -= 1
    scale = n / S
    tw = tracked_width(label)
    maxw = x1 - x0
    icon_w = (ICON + GAP) * scale if icon else 0
    group = tw * scale + icon_w  # el icono crece con la letra
    if group > maxw and group > 0:
        scale *= max(0.6, (maxw - icon_w) / (tw * scale))
        group = tw * scale + icon_w
    gx = snapS((x0 + x1) / 2 - group / 2, S)
    # contorno grueso (un 8,5 % de la letra) en 16 direcciones + 8 a media distancia, y sombra debajo
    gold_text = kind == PRIMARY and active
    ro = max(1, round(10 * scale * S * 0.05))
    ring = ring_offsets(ro)
    edge = tuple(round(c * (GOLD_OUTLINE_SHADE if gold_text else OUTLINE_SHADE)) for c in rgb3(int(tint, 16)))
    if icon:
        sz = round(ICON * scale * S)
        img = menu_icon(icon, sz)
        ix, iy = round(gx * S), round(cy * S - sz / 2)
        for dx, dy in ring:
            g.composite(img, ix + dx, iy + dy, tuple(c / 255 for c in edge) + (1,))
        g.composite(img, ix, iy, tuple(c / 255 for c in rgb3(ink)) + (1,))
        gx += icon_w
    ty = snapS(cy - TEXT_UP * scale, S)
    if gold_text:  # TFMenuButton.goldGlow
        glow = menu_data()['glow']
        w = tw * scale
        gw, gh = w + 24, 13 * scale
        im = glow.resize((max(1, round(gw * S)), max(1, round(gh * S))), Image.BILINEAR)
        g.composite(to_float_img(im), round((gx + w / 2 - gw / 2) * S), round((cy - gh / 2) * S),
                    (1, 0.72, 0.25, 0.27 + 0.18 * e), additive=True)
    for dx, dy in ring:
        draw_tracked(g, label, gx + dx / S, ty + dy / S, S, edge, 1.0, 0, scale, n)
    # letras en degradado (franjas de 2 px reales): oro en Tierras Fantásticas, blanco a gris azulado en las demás
    x0r, x1r = math.floor(gx * S) - 4, math.ceil((gx + tw * scale) * S) + 4
    y0r, y1r = math.floor((ty + GOLD_TOP * scale) * S), math.ceil((ty + GOLD_BOTTOM * scale) * S)
    draw_tracked(g, label, gx, ty, S, rgb3(GOLD[2] if gold_text else WHITE[1]), 1.0, 0, scale, n)
    bands = max(2, (y1r - y0r) // 2)
    for i in range(bands):
        b0 = y0r + (y1r - y0r) * i // bands
        b1 = y0r + (y1r - y0r) * (i + 1) // bands
        g.scissor = (x0r, b0, x1r, b1)
        t = (i + 0.5) / bands
        c = gold(t, e) if gold_text else (mix_rgb(ramp(WHITE, t), 0xF6F1E6, 0.45 * e) if active else 0x9A9AA6)
        c = texture(c, t, i)
        draw_tracked(g, label, gx, ty, S, rgb3(c), 1.0, 0, scale, n)
    if shine is not None:
        k = math.sin(shine * math.pi)
        band = max(6, (x1r - x0r) // 5)
        bx = round(x0r - band + shine * (x1r - x0r + band))
        for j in range(3):
            inset = j * band // 6
            g.scissor = (bx + inset, y0r, bx + band - inset, y1r)
            draw_tracked(g, label, gx, ty, S, (255, 255, 246), 0.35 * k, 0, scale, n)
    g.scissor = None


TRACKING = 0.45  # letras del menú un poco más juntas (TFMenuButton.TRACKING)


def tracked_width(label):
    """TFMenuButton.Tracked.width: la suma de cada letra menos TRACKING entre cada dos."""
    w = sum(font_os(64).getlength(c) / 64 - (0 if c == ' ' else TRACKING) for c in label)
    return max(0.0, w + (TRACKING if label and label[-1] != ' ' else 0))


def draw_tracked(g, label, gx, gy, S, rgb, alpha=1.0, dy_real=0, scale=1.0, os_=None):
    """TFMenuButton.Tracked.draw: letra a letra."""
    x = 0.0
    for c in label:
        draw_text(g, c, gx + x * scale, gy, S, rgb, alpha, dy_real, scale, os_)
        x += font_os(64).getlength(c) / 64 - (0 if c == ' ' else TRACKING)


def texture(rgb, t, i):
    """TFMenuButton.texture: brillo de bisel arriba, sombra abajo y grano fino."""
    k = 1.0 + 0.16 * max(0.0, 1 - abs(t - 0.22) / 0.1) - 0.12 * min(1.0, max(0.0, (t - 0.72) / 0.28))
    k += 0.025 if i % 2 == 0 else -0.025
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    return (min(255, max(0, round(r * k))) << 16) | (min(255, max(0, round(g * k))) << 8) | min(255, max(0, round(b * k)))


def ring_offsets(ro):
    """TFMenuButton.ring: 16 direcciones a ro píxeles reales y 8 a la mitad (sin repetir)."""
    out = []
    for i in range(16):
        a = i * math.pi / 8
        out.append((round(math.cos(a) * ro), round(math.sin(a) * ro)))
    if ro >= 2:
        for i in range(8):
            a = i * math.pi / 4
            out.append((round(math.cos(a) * ro / 2), round(math.sin(a) * ro / 2)))
    seen, res = set(), []
    for o in out:
        if o not in seen and o != (0, 0):
            seen.add(o)
            res.append(o)
    return res


def ramp(cols, t):
    p = t * (len(cols) - 1)
    i = min(len(cols) - 2, int(p))
    return lerp_argb(0xFF000000 | cols[i], 0xFF000000 | cols[i + 1], p - i) & 0xFFFFFF


_icons = {}


def menu_icon(name, n):
    """menu_icons.png (whatsapp, discord, web; 256 cada uno) a n x n, blanco con opacidad."""
    if 'atlas' not in _icons:
        _icons['atlas'] = Image.open(GUI + '/menu_icons.png').convert('RGBA')
    i = ['whatsapp', 'discord', 'web'].index(name)
    im = _icons['atlas'].crop((16 + i * 272, 16, 16 + i * 272 + 256, 272)).resize((n, n), Image.LANCZOS)
    return to_float_img(im)


GOLD = [0xF9EBC4, 0xF4DA96, 0xE8C06A, 0xD8A955, 0xCB9C4E]
GOLD_OUTLINE = 0x2A1204
GOLD_TOP, GOLD_BOTTOM = 0.0, 6.7
WHITE = [0xE6E0D3, 0xD9D2C3, 0xBDB5A6]
OUTLINE_SHADE, GOLD_OUTLINE_SHADE = 0.55, 0.72  # contorno de las letras blancas: el color de su placa (tint) oscurecido  # el degradado de las demás letras (arriba a abajo)


def gold(t, e):
    p = t * (len(GOLD) - 1)
    i = min(len(GOLD) - 2, int(p))
    c = lerp_argb(0xFF000000 | GOLD[i], 0xFF000000 | GOLD[i + 1], p - i)
    return lerp_argb(c, 0xFFFBEFCF, 0.35 * e) & 0xFFFFFF


def paint_button(g, S, b, hover=0.0, pressed=False, active=True, sweep=None, size=1.0):
    """TFMenuButton.renderWidget con placa (sin el zoom de 2 %: aquí solo sube 0,8 al pasar el ratón)."""
    label, icon, kind, plate, x, y, w, h = b
    m = menu_data()
    p = m['json']['plates'][plate]
    e = ease(hover) if active else 0.0
    lift = 0.5 if pressed else -0.6 * e
    at = Placed(p, x, y + lift, w, h, S)
    shade = 0.45 if not active else 0.8 if pressed else 1.0
    draw_plate(g, S, at, (shade, shade, shade, 1))
    if e > 0.01 and not pressed:
        draw_plate(g, S, at, (1, 1, 1, 0.16 * e), additive=True)
    for i, l in enumerate(p['lights']):
        flick = 0.5 + 0.18 * math.sin(i * 2.1) + 0.1 * math.sin(i * 4.3)
        draw_glow(g, S, m['glow'], at.x(l[0]), at.y(l[1]), l[2] * at.k * (3.0 + 0.25 * flick),
                  (1, 0.6, 0.22, min(1, flick * (0.55 + 0.25 * e))))
    if e > 0.01:
        for gem in p['sparks']:
            draw_glow(g, S, m['glow'], at.x(gem[0]), at.y(gem[1]), gem[2] * at.k * 2.2, (0.35, 0.75, 1, 0.5 * e))
    bh = p['body'][3] - p['body'][1]
    paint_content(g, S, at.x(p['text'][0]), at.x(p['text'][2]), at.y((p['body'][1] + p['body'][3]) / 2), label, icon,
                  kind, size, e, active, shine=None, tint=p.get('tint', '806040'))


_shine = None


def paint_sweep(g, S, gx, gy, gw, gh, t, strength, lift):
    """TFMenuPaint.sweep: línea luminosa (button_shine.png) que cruza el botón; t de 0 a 1. Recortada dentro del
    borde (tijera en unidades de interfaz, como enableScissor)."""
    global _shine
    if _shine is None:
        _shine = Image.open(GUI + '/button_shine.png').convert('RGBA')
    eased = t * t * (3 - 2 * t)
    band_w = gh * 2.4
    band_x = gx - band_w + eased * (gw + band_w)
    k = strength * math.sin(t * math.pi)
    inset = 3
    g.scissor = ((gx + inset) * S, (gy + 1) * S, (gx + gw - inset) * S, (gy + gh - 1) * S)
    im = _shine.resize((max(1, int(band_w * S)), gh * S), Image.BILINEAR)
    g.composite(to_float_img(im), int(band_x * S), gy * S - lift, (1, 1, 1, k), additive=True)
    g.scissor = None


# ------------------------------------------------------------------------------------------------ pantalla de título
def gui_size(fbw, fbh, S):
    return -(-fbw // S), -(-fbh // S)


def auto_scale(fbw, fbh, limit=0):
    i = 1
    while (limit == 0 or i < limit) and fbw / (i + 1) >= 320 and fbh / (i + 1) >= 240:
        i += 1
    return i


VIS_GAP = 30  # hueco entre lo que se ve de cada fila (gemas incluidas), en píxeles de la lámina
PAD = 0.4  # aire a cada lado del texto, en altos de la placa
GROUP_GAP = 14  # entre los botones de una misma fila (interfaz)
BLUE_MIN = 0.62  # Mundo local y Mods, como poco este tanto del ancho de Tierras Fantásticas
GROUPS = [[0], [1, 2], [3, 4], [5, 7], [6]]  # los de un grupo, del mismo ancho (simetría en espejo)
ROW_OF = [0, 1, 2, 3, 3, 4, 4, 4]


def crisp(size, S):
    """El tamaño nítido que usa paintContent: n/S con n entero, como mucho un 10 % más que size."""
    n = max(1, min(12, round(S * size)))
    if n > 1 and n / S > size * 1.1:
        n -= 1
    return n / S


def layout(w, h, S=4, fbw=None):
    """TFMenuButton.mainLayout + TitleScreenMixin: (texto, icono, tipo, placa, x, y, ancho, alto) y los tamaños de
    letra (botones, Tierras Fantásticas). Alto: la lámina con filas separadas lo justo; ancho: cada botón a su texto
    (los de una fila, iguales), todo centrado."""
    d = menu_data()['json']
    comp = d['composition']
    ctop, cbot = comp['top'], comp['bottom']
    P = [d['plates'][n] for n in MAIN]
    L = [p['layout'] for p in P]
    rows = [[0], [1], [2], [3, 4], [5, 6, 7]]
    # mismo hueco entre lo que se ve de cada fila (gemas incluidas): TFMenuButton.VIS_GAP
    row_y = {}
    ny, last_bottom = 0.0, 0.0
    for r, row in enumerate(rows):
        above = max(P[i]['body'][1] - P[i]['vis'][0] for i in row)
        below = max(P[i]['vis'][1] - P[i]['body'][1] for i in row)
        body_top = above if r == 0 else ny + above
        for i in row:
            row_y[i] = body_top
        last_bottom = body_top + below
        ny = last_bottom + VIS_GAP
    comp_h = last_bottom
    ctop = 0
    k = min(K_MAX, (h - BOTTOM_MARGIN - round(h * LOGO_FRAC) - 6) / comp_h)
    free = h - BOTTOM_MARGIN - comp_h * k
    top = free - max(0.0, (free - 186) / 2)
    hs = [max(1, round((P[i]['body'][3] - P[i]['body'][1]) * k)) for i in range(8)]
    # filas apiladas con la escala real de cada placa (TFMenuButton.mainLayout: shiftY)
    ys = [0.0] * 8
    cursor, gap = top, round(VIS_GAP * k * S) / S
    for row in rows:
        kk = {i: hs[i] / (P[i]['body'][3] - P[i]['body'][1]) for i in row}
        above = max((P[i]['body'][1] - P[i]['vis'][0]) * kk[i] for i in row)
        below = max((P[i]['vis'][1] - P[i]['body'][1]) * kk[i] for i in row)
        body_top = round((cursor + above) * S) / S
        for i in row:
            ys[i] = body_top
        cursor = round((body_top + below) * S) / S + gap
    size = max(1.0, min(CAP_FILL * INTERIOR * hs[i] / CAP_HEIGHT for i in range(1, 8)))
    tf = max(size, TF_CAP_FILL * INTERIOR * hs[0] / CAP_HEIGHT)
    labels = [('Tierras Fantásticas', None, PRIMARY), ('Mundo local', None, SECONDARY), ('Mods', None, SECONDARY),
              ('Opciones', None, SECONDARY), ('Salir', None, DANGER), ('WhatsApp', 'whatsapp', SECONDARY),
              ('Web', 'web', SECONDARY), ('Discord', 'discord', SECONDARY)]
    for _ in range(4):  # si una fila no cabe a lo ancho, letra un poco más pequeña
        eff, efft = crisp(size, S), crisp(tf, S)
        need = []
        for i, (label, icon, kind) in enumerate(labels):
            sc = efft if i == 0 else eff
            body, text = P[i]['body'], P[i]['text']
            side = (text[0] - body[0]) * k
            fills = sum(f[1] - f[0] for f in P[i]['fills'])
            min_w = ((body[2] - body[0]) - fills * 0.75) * k
            need.append(max(min_w, (tracked_width(label) + (ICON + GAP if icon else 0)) * sc + 2 * (side + PAD * hs[i])))
        widths = [0.0] * 8
        for grp in GROUPS:
            for i in grp:
                widths[i] = max(need[j] for j in grp)
        for i in (1, 2):
            widths[i] = max(widths[i], BLUE_MIN * widths[0])
        widest = max(sum(widths[i] for i in row) + GROUP_GAP * (len(row) - 1) for row in rows)
        if widest <= w - 16 or size <= 0.6:
            break
        size, tf = size * 0.9, tf * 0.9
    cx = w / 2 if fbw is None else fbw / (2 * S)  # el centro de verdad de la ventana (TFMenuButton.mainLayout)
    rects = [None] * 8
    for row in rows:
        even = lambda v: 2 * round(v / 2)  # anchos pares: todas las filas con el mismo centro exacto
        tot = sum(even(widths[i]) for i in row) + GROUP_GAP * (len(row) - 1)
        x = round(cx - tot / 2)
        shift = cx - (x + tot / 2)  # TFMenuButton.mainShiftX: medio píxel si el ancho es impar
        for i in row:
            y0 = ys[i]
            rects[i] = (x + shift, y0, even(widths[i]), hs[i])
            x += even(widths[i]) + GROUP_GAP
    out = [lab + (n,) + r for lab, n, r in zip(labels, MAIN, rects)]
    return round(top), out, (eff, efft)


_bg = None
_logo = None
_glow = None


def render(fbw, fbh, S=None, hover=None, pressed=None, sweep=None, out=None, hover_sweep=None):
    global _bg, _logo, _glow
    S = S or auto_scale(fbw, fbh)
    w, h = gui_size(fbw, fbh, S)
    g = Canvas(fbw, fbh)
    if _bg is None:
        _bg = Image.open(GUI + '/menu_background.png').convert('RGB')
        _logo = Image.open(GUI + '/logo.png').convert('RGBA')
        _glow = Image.open(GUI + '/logo_glow.png').convert('RGBA')
    # Fondo (TFDraw.cover en interfaz)
    sc = max(w / _bg.width, h / _bg.height)
    dw, dh = math.ceil(_bg.width * sc), math.ceil(_bg.height * sc)
    bg = _bg.resize((dw * S, dh * S), Image.LANCZOS)
    g.composite(to_float_img(bg), (w - dw) // 2 * S, (h - dh) // 2 * S)
    top, buttons, (eff, efft) = layout(w, h, S, fbw)
    # Emblema
    lh = min(170, top - 4 - 8)
    if lh > 16:
        lw = lh * _logo.width / _logo.height
        cx, cy = w / 2.0, 4 + lh / 2.0
        gw_, gh_ = lw * 1.5, lh * 1.5
        gl = _glow.resize((int(gw_ * S), int(gh_ * S)), Image.LANCZOS)
        g.composite(to_float_img(gl), int((cx - gw_ / 2) * S), int((cy - gh_ / 2) * S), (1, 1, 1, 0.72))
        lg = _logo.resize((int(lw * S), int(lh * S)), Image.LANCZOS)
        g.composite(to_float_img(lg), int((cx - lw / 2) * S), int((cy - lh / 2) * S))
    for i, b in enumerate(buttons):
        paint_button(g, S, b, size=efft if i == 0 else eff, hover=1.0 if hover == i else 0.0, pressed=pressed == i,
                     sweep=(sweep if b[2] == PRIMARY else (hover_sweep if hover == i else None)))
    im = g.image()
    if out:
        im.save(out)
    return im, S, (w, h), buttons


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(__import__('tempfile').gettempdir(), 'tf_menu_sim')
    os.makedirs(out, exist_ok=True)
    scenes = [
        ('1920x1080_escala4', 1920, 1080, 4, None, None, None),
        ('1920x1080_escala3_raton_mundo', 1920, 1080, 3, 1, None, None),
        ('1920x1080_escala3_raton_whatsapp', 1920, 1080, 3, 5, None, None),
        ('1920x1080_escala2_raton_discord', 1920, 1080, 2, 7, None, None),
        ('1920x1080_escala3_raton_web', 1920, 1080, 3, 6, None, None),
        ('1280x720_escala3_raton_servidor', 1280, 720, 3, 0, None, None),
        ('1280x720_escala2', 1280, 720, 2, None, None, None),
        ('2560x1440_escala4_raton_salir', 2560, 1440, 4, 4, None, None),
        ('2560x1440_escala3_pulsado_opciones', 2560, 1440, 3, 3, 3, None),
        ('1920x1080_escala4_destello', 1920, 1080, 4, None, None, 0.45),
        ('2560x1440_escala6', 2560, 1440, 6, None, None, None),
    ]
    only = sys.argv[2] if len(sys.argv) > 2 else None
    for name, fw, fh, S, hv, pr, sw in scenes:
        if only and only not in name:
            continue
        im, S, gsz, buttons = render(fw, fh, S, hv, pr, sw, f'{out}/{name}.png')
        # Recorte de los botones a tamaño real
        top = min(b[5] for b in buttons) - 12
        bot = max(b[5] + b[7] for b in buttons) + 8
        left = min(b[4] for b in buttons) - 14
        right = max(b[4] + b[6] for b in buttons) + 14
        im.crop((left * S, top * S, right * S, bot * S)).save(f'{out}/{name}_botones.png')
        print(name, 'escala', S, 'interfaz', gsz)
