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
FONT = ROOT + '/font/cinzel.ttf'
ASC, DESC, UPEM = 976, -372, 1000


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


def draw_text(g, s, gx, gy, S, rgb, alpha=1.0, dy_real=0, scale=1.0):
    """drawString(font, s, gx, gy) con la letra TTF: glifos rasterizados a `oversample` px por px de interfaz (sin
    hinting, como stb_truetype: se dibuja a 8x y se reduce por promedio) y muestreados sin suavizar."""
    os_ = oversample(S)
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
PRIMARY, SECONDARY, DANGER = 'primary', 'secondary', 'danger'
GAP = 4         # entre icono y texto (interfaz)
TEXT_UP = 3.0   # el texto se dibuja en y + h/2 - TEXT_UP (centrado óptico de Cinzel)


def ease(t):
    return t * t * (3 - 2 * t)


def mix_rgb(a, b, t):
    return lerp_argb(a | 0xFF000000, b | 0xFF000000, t) & 0xFFFFFF


def mix_argb(a, b, t):
    return lerp_argb(a, b, t)


WHATSAPP, DISCORD = 'whatsapp', 'discord_kind'
GOLD, GOLD_TEXT, RED, RED_TEXT = 0xE9C46A, 0xFFE3A0, 0xE2556B, 0xFFC9D2
BRAND = {WHATSAPP: 0x25D366, DISCORD: 0x5865F2}


def paint_button(g, S, gx, gy, gw, gh, kind, label, icon, hover=0.0, pressed=False, active=True, alpha=1.0, sweep=None):
    """TFMenuButton: dorado liso (principal) o panel oscuro con borde fino (los demás); iconos solo WhatsApp/Discord."""
    R = 2 * S
    T = 1 if S < 4 else 2
    e = ease(hover) if active else 0.0
    X, Y, W, H = gx * S, gy * S, gw * S, gh * S
    accent = RED if kind == DANGER else BRAND.get(kind, GOLD)

    if kind == PRIMARY:
        top = mix_argb(0xFFF3C553, 0xFFFFD46A, e)
        bot = mix_argb(0xFFDDA437, 0xFFEBB445, e)
        if pressed:
            top, bot = mix_argb(top, 0xFFB98322, 0.5), mix_argb(bot, 0xFFB98322, 0.5)
        round_fill(g, X, Y, W, H, R, T, 0, H, with_alpha(top, alpha), with_alpha(bot, alpha))
        g.fill(X + R, Y + T, X + W - R, Y + T + max(1, S // 2), argb(0.55 * alpha, 0xFFF3C8))
        ring = argb(0.95 * alpha, 0x7A4E0E)
        round_ring(g, X, Y, W, H, R, T, ring, ring)
        ink = 0x2A1A04
    else:
        fill = mix_argb(argb(0.74, 0x0A0C14), argb(0.84, 0x171B28), e)
        if pressed:
            fill = argb(0.85, 0x05060A)
        ring = mix_argb(argb(0.22, 0xFFFFFF), argb(0.95, accent), e)
        round_fill(g, X, Y, W, H, R, T, 0, H, with_alpha(fill, alpha), with_alpha(fill, alpha))
        round_ring(g, X, Y, W, H, R, T, with_alpha(ring, alpha), with_alpha(ring, alpha))
        hot = RED_TEXT if kind == DANGER else (0xFFFFFF if kind in BRAND else GOLD_TEXT)
        ink = mix_rgb(0xE9ECF5, hot, e)
    if not active:
        ink = 0x7C8299

    tw = text_width(label, S)
    maxw = gw - 2 * min(10, gw // 6)
    with_icon = icon is not None and tw + ICON + GAP <= maxw
    group = tw + (ICON + GAP if with_icon else 0)
    scale = max(0.6, maxw / group) if group > maxw else 1.0
    snap = lambda v: round(v * S) / S
    gx0 = snap(gx + (gw - group * scale) / 2.0)
    off = max(1, S // 2)
    if with_icon:
        n = ICON * S
        iconimg = icon_image(icon, n)
        ix = int(round(gx0 * S))
        iy = Y + (H - n) // 2
        g.composite(iconimg, ix, iy, tuple(c / 255.0 for c in rgb3(accent)) + (alpha,))
        gx0 += ICON + GAP
    ty = snap(gy + gh / 2.0 - TEXT_UP * scale)
    if kind != PRIMARY:
        draw_text(g, label, gx0, ty, S, (0, 0, 0), 0.5 * alpha, off, scale)
    draw_text(g, label, gx0, ty, S, rgb3(ink), alpha, 0, scale)


def rgb3(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


_shine = None


def paint_sweep(g, S, gx, gy, gw, gh, t, strength, lift):
    """Línea luminosa (button_shine.png) que cruza el botón; t de 0 a 1. Recortada dentro del borde (tijera en
    unidades de interfaz, como enableScissor)."""
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


def layout(w, h):
    """TitleScreenMixin.tfclient$replaceButtons: (texto, icono, tipo, x, y, ancho)."""
    l = h // 4 + 48
    shift = max(0, min(12, h - (l + 132)))
    x = w // 2 - 100
    top = l + shift
    return top, [
        ('Tierras Fantásticas', None, PRIMARY, x, top, 200),
        ('Mundo local', None, SECONDARY, x, top + 24, 200),
        ('Mods', None, SECONDARY, x, top + 48, 98),
        ('Web', None, SECONDARY, x + 102, top + 48, 98),
        ('Opciones', None, SECONDARY, x, top + 72, 98),
        ('Salir', None, DANGER, x + 102, top + 72, 98),
        ('WhatsApp', 'whatsapp', WHATSAPP, x, top + 96, 98),
        ('Discord', 'discord', DISCORD, x + 102, top + 96, 98),
    ]


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
    top, buttons = layout(w, h)
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
    for i, (label, icon, kind, bx, by, bw) in enumerate(buttons):
        paint_button(g, S, bx, by, bw, 20, kind, label, icon,
                     hover=1.0 if hover == i else 0.0, pressed=pressed == i,
                     sweep=(sweep if kind == PRIMARY else (hover_sweep if hover == i else None)))
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
        ('1920x1080_escala3_raton_whatsapp', 1920, 1080, 3, 6, None, None),
        ('1920x1080_escala2_raton_discord', 1920, 1080, 2, 7, None, None),
        ('1280x720_escala3_raton_servidor', 1280, 720, 3, 0, None, None),
        ('1280x720_escala2', 1280, 720, 2, None, None, None),
        ('2560x1440_escala4_raton_salir', 2560, 1440, 4, 5, None, None),
        ('2560x1440_escala3_pulsado_opciones', 2560, 1440, 3, 4, 4, None),
    ]
    only = sys.argv[2] if len(sys.argv) > 2 else None
    for name, fw, fh, S, hv, pr, sw in scenes:
        if only and only not in name:
            continue
        im, S, gsz, buttons = render(fw, fh, S, hv, pr, sw, f'{out}/{name}.png')
        # Recorte de los botones a tamaño real
        top = min(b[4] for b in buttons) - 8
        bot = max(b[4] for b in buttons) + 28
        left = buttons[0][3] - 14
        im.crop((left * S, top * S, (left + 228) * S, bot * S)).save(f'{out}/{name}_botones.png')
        print(name, 'escala', S, 'interfaz', gsz)
