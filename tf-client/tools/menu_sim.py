"""Simulador del menú principal de TF Client (mismas medidas y la misma lógica de dibujo que el código Java).

Reproduce TitleScreenMixin (posiciones), TFLogoRenderer (emblema y halo), TFMenuButton + TFPill + TFMenuPaint (1.3.120:
pastillas de cristal oscuro con hilo de oro dibujadas en píxeles reales: sombra, halo, relleno, cristal, hilo, destello,
hilos de oro e iconos) y la letra Titan One tal como la pinta Minecraft (glifos a n píxeles por píxel de interfaz,
muestreo sin suavizar). Uso: python3 tools/menu_sim.py [carpeta_salida] [filtro]  (capturas a 1280x720..2560x1440, escalas 1-6)
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


# ------------------------------------------------------------------------------------------------ botón (TFPill)
GAP = 3                     # entre icono y texto (interfaz)
TRACKING = 0.45             # letras del menú un poco más juntas (TFMenuButton.TRACKING)
CAP_HEIGHT = 6.2            # alto de las mayúsculas de Titan One a escala 1
GOLD_FILL, GOLD_HOT, WHITE_FILL, WHITE_HOT = 0xF0CF7E, 0xFBE7B4, 0xEAE4D8, 0xFFE9A8


def ease(t):
    return t * t * (3 - 2 * t)


def mix_rgb(a, b, t):
    return lerp_argb(a | 0xFF000000, b | 0xFF000000, t) & 0xFFFFFF


def rgb3(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def snapS(v, S):
    return round(v * S) / S


def fill(gold_text, e, breath=0.5):
    """TFMenuButton.fill: color liso de las letras (el oro respira; breath de 0 a 1)."""
    if not gold_text:
        return mix_rgb(WHITE_FILL, WHITE_HOT, e)
    return mix_rgb(GOLD_FILL, GOLD_HOT, min(1.0, 0.25 * breath + e))


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


_icons = {}


def menu_icon(name, n):
    """menu_icons.png (whatsapp, discord, web; 256 cada uno) a n x n, blanco con opacidad."""
    if 'atlas' not in _icons:
        _icons['atlas'] = Image.open(GUI + '/menu_icons.png').convert('RGBA')
    i = ['whatsapp', 'discord', 'web'].index(name)
    im = _icons['atlas'].crop((16 + i * 272, 16, 16 + i * 272 + 256, 272)).resize((n, n), Image.LANCZOS)
    return to_float_img(im)


def gui_size(fbw, fbh, S):
    return -(-fbw // S), -(-fbh // S)


def auto_scale(fbw, fbh, limit=0):
    i = 1
    while (limit == 0 or i < limit) and fbw / (i + 1) >= 320 and fbh / (i + 1) >= 240:
        i += 1
    return i


def crisp(size, S):
    """El tamaño nítido que usa paintContent: n/S con n entero, como mucho un 10 % más que size."""
    n = max(1, min(12, round(S * size)))
    if n > 1 and n / S > size * 1.1:
        n -= 1
    return n / S


# TFPill.Style: (relleno arriba, abajo; relleno con ratón arriba, abajo; hilo arriba, abajo; hilo con ratón arriba, abajo; halo)
STYLES = {
    'gold':    (0x2C1A4A, 0x120922, 0x4E2E82, 0x24123E, 0xE9C46E, 0x7A5A22, 0xFFEDB0, 0xE0B456, 0xFFC860),
    'violet':  (0x2A1846, 0x110920, 0x4A2C7E, 0x22123C, 0xD9B465, 0x6E5020, 0xFFE9A8, 0xD8AC50, 0xC9A0FF),
    'red':     (0x2A1846, 0x110920, 0x6A1E34, 0x2E0A16, 0xD9B465, 0x6E5020, 0xFFC0B0, 0xD8604A, 0xFF6A50),
    'green':   (0x2A1846, 0x110920, 0x1D5E44, 0x0B2A1E, 0xD9B465, 0x6E5020, 0xC0F6D6, 0x48C080, 0x5CE89A),
    'blurple': (0x2A1846, 0x110920, 0x3E44A8, 0x1A1C52, 0xD9B465, 0x6E5020, 0xD6DAFF, 0x7C84F4, 0x8C96FF),
}


def lerp(a, b, t):
    return lerp_argb(0xFF000000 | a, 0xFF000000 | b, t) & 0xFFFFFF


def shine(g, X, Y, W, H, r, t, strength):
    """Destello: una banda inclinada que cruza la placa (recortada a su forma, fila a fila)."""
    if t <= 0 or t >= 1:
        return
    e = t * t * (3 - 2 * t)
    bw = H * 0.9
    slant = 0.45
    cx0 = X - bw - H * slant + e * (W + 2 * bw + H * slant)
    k = strength * math.sin(t * math.pi)
    for j in range(H):
        y = j + 0.5
        dy = max(0.0, r - y, y - (H - r))
        xl = r - math.sqrt(max(0.0, r * r - dy * dy)) if dy > 0 else 0.0
        lo, hi = X + math.ceil(xl), X + W - math.ceil(xl)
        c = cx0 + (H - y) * slant
        for a0, a1, f in ((-1.0, -0.5, 0.35), (-0.5, -0.17, 0.7), (-0.17, 0.17, 1.0), (0.17, 0.5, 0.7), (0.5, 1.0, 0.35)):
            x0, x1 = max(lo, round(c + a0 * bw)), min(hi, round(c + a1 * bw))
            if x1 > x0:
                g.fill(x0, Y + j, x1, Y + j + 1, argb(0.20 * k * f, 0xFFFFFF))


def pill(g, S, x, y, w, h, style, e, pressed=False, active=True, primary=False, shine_t=None):
    """TFPill.paint: la pastilla (sombra, halo, relleno, cristal, hilo; doble hilo en el principal; destello)."""
    st = STYLES[style]
    lift = 0 if pressed else -round(e * S)
    X, Y, W, H = round(x * S), round(y * S) + lift + (max(1, round(0.5 * S)) if pressed else 0), round(w * S), round(h * S)
    r = H // 2
    t = max(1, round(S * 0.6))
    # sombra y halo
    halo(g, X, Y + max(1, S // 2), W, H, r, round(4 * S), argb(0.55, 0x000000))
    glow = e if not pressed else 0.5 * e
    if primary:
        breath = 0.5
        glow = max(glow, 0.22 + 0.1 * breath)
    if glow > 0.01:
        halo(g, X, Y, W, H, r, round(7 * S), argb(0.55 * glow, st[8]))
    k = e if active else 0.0
    if pressed:
        k *= 0.6
    top = lerp(st[0], st[2], k)
    bot = lerp(st[1], st[3], k)
    if not active:
        top, bot = 0x26222E, 0x15131A
    round_fill(g, X, Y, W, H, r, 0, 0, H, argb(0.93, top), argb(0.95, bot))
    # vidrio: luz arriba y sombra abajo, dentro del hilo
    round_fill(g, X, Y, W, H, r, t, t, H // 2, argb(0.13 + 0.07 * k, 0xFFFFFF), argb(0.0, 0xFFFFFF))
    round_fill(g, X, Y, W, H, r, t, H * 3 // 4, H - t, argb(0.0, 0x000000), argb(0.22, 0x000000))
    # hilo
    rt, rb = lerp(st[4], st[6], k), lerp(st[5], st[7], k)
    if not active:
        rt, rb = 0x6A6470, 0x3A3640
    round_ring(g, X, Y, W, H, r, t, argb(0.95, rt), argb(0.95, rb))
    if primary:
        ins = t + max(1, round(S * 1.2))
        round_ring(g, X + ins, Y + ins, W - 2 * ins, H - 2 * ins, r - ins, 1, argb(0.45 + 0.35 * k, rt), argb(0.25 + 0.3 * k, rb))
    if shine_t is not None:
        shine(g, X, Y, W, H, r, shine_t, 1.0)
    return X, Y, W, H


LABELS = [('Tierras Fantásticas', None, 'gold', True), ('Mundo local', None, 'violet', False), ('Mods', None, 'violet', False),
          ('Opciones', None, 'violet', False), ('Salir', None, 'red', False), ('WhatsApp', 'whatsapp', 'green', False),
          ('Web', 'web', 'violet', False), ('Discord', 'discord', 'blurple', False)]
ROW_H = [28, 22, 22, 22, 20]
ROWS = [[0], [1], [2], [3, 4], [5, 6, 7]]
GAP_Y, GAP_X = 7, 8
TEXT_UP = 3.05


def layout(w, h, S):
    """TFMenuButton.mainLayout: (arriba, [(índice, x, y, ancho, alto, tamaño de letra)])."""
    total = sum(ROW_H) + GAP_Y * (len(ROW_H) - 1) + 3
    avail = h - 10 - max(84, h * 0.40)
    k = max(0.62, min(1.0, avail / total))
    hs = [max(14, round(v * k)) for v in ROW_H]
    gy = max(3, round(GAP_Y * k))
    comp = sum(hs) + gy * (len(hs) - 1) + round(3 * k)
    free = h - 10 - comp
    top = free - max(0.0, (free - 186) / 2)
    sizes = [0.44 * hs[0] / 6.2] + [0.42 * hs[i] / 6.2 for i in range(1, 4)] + [0.36 * hs[4] / 6.2]
    # ancho de la columna: lo que pide el texto más ancho de cada fila
    col = max(210, min(280, w * 0.36))
    for r, row in enumerate(ROWS):
        need = 0
        for i in row:
            lab, icon, _, prim = LABELS[i]
            sc = crisp(sizes[r], S)
            tw = tracked_width(lab) * sc + ((ICON + 3) * sc if icon else 0)
            need = max(need, tw + hs[r] * (1.9 if icon is None else 1.0))
        col = max(col, need * len(row) + GAP_X * (len(row) - 1))
    col = min(col, w - 16)
    col = 2 * round(col / 2)
    out = []
    y = top
    for r, row in enumerate(ROWS):
        n = len(row)
        bw = (col - GAP_X * (n - 1)) / n
        x = (w - col) / 2
        for j, i in enumerate(row):
            out.append((i, round(x + j * (bw + GAP_X)), round(y), round(bw), hs[r], crisp(sizes[r], S)))
        y += hs[r] + gy + (round(3 * k) if r == 0 else 0)
    return round(top), out


def threads(g, S, X, Y, W, H, r, tx0, tx1, amount, k):
    """Hilos de oro a los dos lados del texto (como el nombre de los caballeros): salen del texto hacia las puntas
    (amount de 0 a 1), se apagan hacia fuera y acaban en un rombo pequeño."""
    if amount <= 0.01:
        return
    th = max(1, round(S * 0.5))
    cy = Y + H // 2 - th // 2
    gap = round(5 * S)
    d = max(1, round(S * 0.8))
    col = lerp(0xE9C46E, 0xFFF0C0, k)
    for side in (-1, 1):
        start = tx0 - gap if side < 0 else tx1 + gap
        end_full = X + r if side < 0 else X + W - r
        length = abs(end_full - start)
        if length < 4 * S:
            continue
        L = length * amount
        steps = 12
        for i in range(steps):
            a0, a1 = i / steps, (i + 1) / steps
            alpha = 0.95 * (1 - a0) ** 1.3 * min(1.0, amount * 1.5)
            if side < 0:
                x1, x0 = round(start - a0 * L), round(start - a1 * L)
            else:
                x0, x1 = round(start + a0 * L), round(start + a1 * L)
            if x1 > x0:
                g.fill(x0, cy, x1, cy + th, argb(alpha, col))
        tip = round(start - L) if side < 0 else round(start + L)
        ty = cy + th // 2
        a = min(1.0, amount * 1.5)
        for j in range(-d, d + 1):
            hw = d - abs(j)
            g.fill(tip - hw, ty + j, tip + hw + 1, ty + j + 1, argb(a, col))


def text(g, S, lab, icon, x, y, w, h, size, e, primary, active=True, thread=0.0):
    """TFMenuButton.paintContent (menú principal): hilos, icono y letras con contorno, centradas a la vista."""
    n = max(1, min(12, round(S * size)))
    if n > 1 and n / S > size * 1.1:
        n -= 1
    sc = n / S
    tw = tracked_width(lab) * sc
    iw = (ICON + 3) * sc if icon else 0
    gx = snapS(x + w / 2 - (tw + iw) / 2, S)
    cy = y + h / 2
    ro = max(1, round(10 * sc * S * 0.05))
    ring = ring_offsets(ro)
    edge = (0x3A, 0x23, 0x06) if primary else (0x1A, 0x0E, 0x2E)
    ink = fill(primary, e)  # TFMenuButton.fill: oro que respira o crema que pasa a oro claro
    if not active:
        ink = 0x8A8494
    if thread > 0:
        X, Y, W, H = round(x * S), round(y * S), round(w * S), round(h * S)
        threads(g, S, X, Y, W, H, H // 2, round(gx * S), round((gx + tw + iw) * S), thread, e)
    if icon:
        sz = round(ICON * sc * S)
        img = menu_icon(icon, sz)
        ix, iy = round(gx * S), round(cy * S - sz / 2)
        for dx, dy in ring:
            g.composite(img, ix + dx, iy + dy, tuple(c / 255 for c in edge) + (1,))
        g.composite(img, ix, iy, tuple(c / 255 for c in rgb3(ink)) + (1,))
        gx += iw
    ty = snapS(cy - TEXT_UP * sc, S)
    for dx, dy in ring:
        draw_tracked(g, lab, gx + dx / S, ty + dy / S, S, edge, 1.0, 0, sc, n)
    draw_tracked(g, lab, gx, ty, S, rgb3(ink), 1.0, 0, sc, n)


def render(fbw, fbh, S, hover=None, pressed=None, shine_t=None, out=None):
    w, h = gui_size(fbw, fbh, S)
    g = Canvas(fbw, fbh)
    bg = Image.open(GUI + '/menu_background.png').convert('RGB')
    logo = Image.open(GUI + '/logo.png').convert('RGBA')
    glow = Image.open(GUI + '/logo_glow.png').convert('RGBA')
    sc = max(w / bg.width, h / bg.height)
    dw, dh = math.ceil(bg.width * sc), math.ceil(bg.height * sc)
    g.composite(to_float_img(bg.resize((dw * S, dh * S), Image.LANCZOS)), (w - dw) // 2 * S, (h - dh) // 2 * S)
    top, rects = layout(w, h, S)
    lh = min(170, top - 4 - 8)
    if lh > 16:  # TitleScreenMixin: justo encima de los botones
        lw = lh * logo.width / logo.height
        cx, cy = w / 2.0, max(4, top - 8 - lh) + lh / 2.0
        gw_, gh_ = lw * 1.5, lh * 1.5
        g.composite(to_float_img(glow.resize((int(gw_ * S), int(gh_ * S)), Image.LANCZOS)), int((cx - gw_ / 2) * S), int((cy - gh_ / 2) * S), (1, 1, 1, 0.72))
        g.composite(to_float_img(logo.resize((int(lw * S), int(lh * S)), Image.LANCZOS)), int((cx - lw / 2) * S), int((cy - lh / 2) * S))
    for i, x, y, bw, bh, size in rects:
        lab, icon, style, prim = LABELS[i]
        e = 1.0 if hover == i else 0.0
        pr = pressed == i
        X, Y, W, H = pill(g, S, x, y, bw, bh, style, e, pr, True, prim, shine_t=shine_t if (hover == i or prim) else None)
        text(g, S, lab, icon, X / S, Y / S, W / S, H / S, size, e, prim, thread=1.0 if prim else (e if icon is None and bw >= 90 else 0.0))
    im = g.image()
    if out:
        im.save(out)
        ys = [r[2] for r in rects]
        y0 = min(ys) - 14
        y1 = max(r[2] + r[4] for r in rects) + 12
        x0 = min(r[1] for r in rects) - 16
        x1 = max(r[1] + r[3] for r in rects) + 16
        im.crop((x0 * S, y0 * S, x1 * S, y1 * S)).save(out.replace('.png', '_botones.png'))
    return im



if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(__import__('tempfile').gettempdir(), 'tf_menu_sim')
    os.makedirs(out, exist_ok=True)
    scenes = [
        ('1920x1080_escala3_raton_mundo', 1920, 1080, 3, 1, None, None),
        ('1920x1080_escala4_raton_whatsapp', 1920, 1080, 4, 5, None, None),
        ('1920x1080_escala2_raton_discord', 1920, 1080, 2, 7, None, None),
        ('1280x720_escala2_raton_salir', 1280, 720, 2, 4, None, None),
        ('1920x1080_escala3_destello_servidor', 1920, 1080, 3, 0, None, 0.45),
        ('2560x1440_escala4_pulsado_opciones', 2560, 1440, 4, 3, 3, None),
        ('2560x1440_escala6', 2560, 1440, 6, None, None, None),
    ]
    only = sys.argv[2] if len(sys.argv) > 2 else None
    for name, fw, fh, S, hv, pr, sw in scenes:
        if only and only not in name:
            continue
        render(fw, fh, S, hover=hv, pressed=pr, shine_t=sw, out=f'{out}/{name}.png')
        print(name, 'escala', S, 'interfaz', gui_size(fw, fh, S))
