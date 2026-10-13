import os
"""El diseño 1.4 de las apps del pad (PadSkin.java y PadViewPage.java), para el simulador: mismas medidas, colores y
animaciones (dibujadas en el instante T). Sim hereda de Skin; aquí solo se usan sus métodos de dibujo básicos (fill,
vgrad, box, mtext, ptext, img, paste…), así que este archivo no importa sim.py."""
import math

import numpy as np
from PIL import Image, ImageDraw

WHITE = (255, 255, 255)
INK = (24, 38, 92)
GOLD, GOLD_HI, GOLD_LO = (246, 182, 40), (255, 229, 138), (192, 120, 24)
TEXT, MUTED = (24, 38, 92), (74, 102, 148)


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def mix(a, b, k):
    """a mezclado con b: k = 0 a, k = 1 b (PadSkin.mix)."""
    return tuple(int(round(a[i] + (b[i] - a[i]) * k)) for i in range(3))


def darken(c, k):
    return tuple(int(round(v * k)) for v in c[:3])


def lighten(c, n):
    return tuple(min(255, v + n) for v in c[:3])


def tint(c, k):
    return tuple(int(round(255 + (v - 255) * k)) for v in c[:3])


def deep(c):
    """PadSkin.deep: los colores muy claros se oscurecen un poco (para fondos con texto blanco)."""
    lum = (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255
    return mix(c, INK, min(0.45, (lum - 0.62) * 1.6 + 0.12)) if lum > 0.62 else tuple(c[:3])


def ink(c):
    """PadSkin.ink: los colores muy claros se oscurecen hacia el azul noche (texto sobre blanco)."""
    lum = (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255
    return mix(c, INK, min(0.7, (lum - 0.55) * 1.5 + 0.3)) if lum > 0.55 else tuple(c[:3])


def with_a(c, a):
    return tuple(c[:3]) + (max(0, min(255, int(a))),)


# PadSkin.THEME: el color y la frase de cada app (las del admin usan las de su app)
THEMES = [('Cielo', 0x54D0FC, 0x60D5FC, 0xFFFFFF), ('Rosa', 0xFFB9DC, 0xFF8EC6, 0xFFFFFF),
          ('Lavanda', 0xCDB9FF, 0xA88CF5, 0xFFFFFF), ('Menta', 0xA2F2D5, 0x5ED8AB, 0xFFFFFF),
          ('Atardecer', 0xFFCC92, 0xFF9670, 0xFFFFFF), ('Oro', 0xFFE69A, 0xF4C24F, 0xFFFFFF),
          ('Fresa', 0xFFA6B2, 0xF0647C, 0xFFFFFF), ('Noche', 0x3B4E9E, 0x1D2860, 0xA9B8FF)]

THEME = {
    'oficios': (0xE0892E, 'Trabaja, sube de nivel y cobra cada pago.'),
    'misiones': (0x22A884, 'Retos diarios y semanales con premio.'),
    'cazas': (0xC8553D, 'Caza criaturas y gana premios.'),
    'recompensas': (0xE2466A, 'Tu premio de cada día: no rompas la racha.'),
    'tienda': (0xE6A019, 'Compra y vende al momento.'),
    'gts': (0x8B5CF0, 'El mercado entre jugadores.'),
    'monedero': (0xD49A12, 'Tus monedas y tus movimientos.'),
    'viajes': (0x1AA3C8, 'Viaja por el mundo en un momento.'),
    'explorar': (0x1AA3C8, 'Descubre sitios nuevos al azar.'),
    'hogares': (0xDB6440, 'Tus casas, a un clic.'),
    'kits': (0xB4783A, 'Equipo listo para reclamar.'),
    'protecciones': (0x3B7DDB, 'Protege tus terrenos.'),
    'clanes': (0xD43A50, 'Tu clan, sus miembros y aliados.'),
    'jugadores': (0x2E9BE6, 'Quién está conectado ahora.'),
    'ranking': (0xE3A52A, 'Los mejores del servidor.'),
    'armario': (0x9E5AE0, 'Tus skins y tus sets, a tu estilo.'),
    'efectos': (0xE0489E, 'Efectos al matar y clases de skills.'),
    'rango': (0xD9A21E, 'Tu rango y sus ventajas.'),
    'gachapon': (0xE0489E, 'Gira y gana premios.'),
    'pase': (0x2EAF5C, 'Sube de nivel y cobra premios.'),
    'camara': (0x3B9BE0, 'Tus fotos para la comunidad.'),
    'ajustes': (0x5B74A6, 'Tu pad, a tu manera.'),
    'admin': (0x5B74A6, 'Configura el servidor.'),
}
DEFAULT = (0x3496FA, '')


def accent(app):
    base = app[2:] if app.startswith('a_') else app
    if app in ('a_apps', 'a_ajustes'):
        base = 'admin'
    return rgb(THEME.get(base, DEFAULT)[0])


def tagline(app):
    base = app[2:] if app.startswith('a_') else app
    if app in ('a_apps', 'a_ajustes'):
        base = 'admin'
    return THEME.get(base, DEFAULT)[1]


def icon_of(app):
    base = app[2:] if app.startswith('a_') else app
    if app in ('a_apps', 'a_ajustes', 'ajustes'):
        return 'admin'
    return 'viajes' if base == 'explorar' else base


class Skin:
    T = 1.35  # segundos desde que se abrió el pad (el instante que se dibuja)

    # ---------------------------------------------------------------------------------------------------------------
    # Formas suaves (en el Java: vértices con color, PadSkin.hgrad / glow / disc)
    def _arr_paste(self, arr, rx, ry):
        self._paste_px(Image.fromarray(np.clip(arr, 0, 255).round().astype(np.uint8), 'RGBA'), rx, ry)

    def hgrad(self, x, y, w, h, cl, cr):
        r = self._rect(x, y, x + w, y + h)
        W, H = r[2] - r[0], r[3] - r[1]
        if W <= 0 or H <= 0:
            return
        cl = np.array(cl if len(cl) == 4 else tuple(cl) + (255,), float)
        cr = np.array(cr if len(cr) == 4 else tuple(cr) + (255,), float)
        t = (np.arange(W) + 0.5) / W
        row = np.outer(1 - t, cl) + np.outer(t, cr)
        self._arr_paste(np.repeat(row[None], H, axis=0), r[0], r[1])

    def glow(self, cx, cy, r, color, a):
        """Luz redonda: a en el centro, 40 % a 0,45 r y nada en el borde (PadSkin.glow, dos anillos de 24 lados)."""
        if r <= 0 or a <= 0:
            return
        rr = self._rect(cx - r, cy - r, cx + r, cy + r)
        W, H = rr[2] - rr[0], rr[3] - rr[1]
        yy, xx = np.mgrid[0:H, 0:W]
        d = np.hypot(xx + 0.5 - W / 2, yy + 0.5 - H / 2) / (W / 2)
        al = np.where(d < 0.45, 1 - d / 0.45 * 0.6, np.clip(0.4 * (1 - (d - 0.45) / 0.55), 0, 1)) * a
        arr = np.zeros((H, W, 4))
        arr[..., :3] = color[:3]
        arr[..., 3] = al
        self._arr_paste(arr, rr[0], rr[1])

    def disc(self, cx, cy, r, color):
        rr = self._rect(cx - r, cy - r, cx + r, cy + r)
        W, H = rr[2] - rr[0], rr[3] - rr[1]
        if W <= 0:
            return
        im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        pts = [(W / 2 + math.cos(i / 24 * 2 * math.pi) * W / 2, H / 2 + math.sin(i / 24 * 2 * math.pi) * H / 2) for i in range(24)]
        ImageDraw.Draw(im).polygon(pts, fill=tuple(color[:3]) + ((color[3],) if len(color) > 3 else (255,)))
        self._paste_px(im, rr[0], rr[1])

    def tex_alpha(self, name, x, y, w, h, alpha, tint_c=WHITE, smooth=True):
        """Una textura del pad estirada a w x h unidades, teñida y con alfa (RenderSystem.setShaderColor)."""
        im = self.img(name) if isinstance(name, str) else name
        r = self._rect(x, y, x + w, y + h)
        if r[2] <= r[0] or r[3] <= r[1]:
            return
        im = im.resize((r[2] - r[0], r[3] - r[1]), Image.BILINEAR if smooth else Image.NEAREST)
        a = np.asarray(im, float).copy()
        a[..., :3] *= np.array(tint_c[:3], float) / 255
        a[..., 3] *= alpha
        self._arr_paste(a, r[0], r[1])

    def tiled(self, name, x, y, w, h, alpha, off=0, tint_c=WHITE):
        """Textura en mosaico (16x16) desplazada off unidades a la derecha, con alfa: las rayas (PadSkin.stripes)."""
        im = self.img(name)
        r = self._rect(x, y, x + w, y + h)
        W, H = r[2] - r[0], r[3] - r[1]
        if W <= 0 or H <= 0:
            return
        f = self.f
        big = im.resize((16 * int(f), 16 * int(f)), Image.NEAREST) if f != 1 else im
        n = big.width
        sx = int(round(-off * f)) % n
        reps = (W + sx) // n + 2, H // n + 2
        canvas = Image.new('RGBA', (reps[0] * n, reps[1] * n))
        for i in range(reps[0]):
            for j in range(reps[1]):
                canvas.paste(big, (i * n, j * n))
        a = np.asarray(canvas.crop((sx, 0, sx + W, H)), float).copy()
        a[..., :3] *= np.array(tint_c[:3], float) / 255
        a[..., 3] *= alpha
        self._arr_paste(a, r[0], r[1])

    def rays(self, cx, cy, r, alpha, angle):
        im = self.img('rays').rotate(-angle, resample=Image.BILINEAR)
        self.tex_alpha(im, cx - r, cy - r, 2 * r, 2 * r, alpha)

    def sparkle(self, cx, cy, s, a, color=WHITE):
        """Destello de 4 puntas (PadSkin.sparkle): centro blanco y brazos que se apagan."""
        if a <= 0.02:
            return
        self.fill(cx, cy, cx + 1, cy + 1, with_a(WHITE, 255 * a))
        for i in range(1, s + 1):
            k = a * (1 - (i - 1) / s) * 0.9
            c = with_a(color, 255 * k)
            for dx, dy in ((i, 0), (-i, 0), (0, i), (0, -i)):
                self.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, c)

    def twinkle(self, seed, speed=1.0):
        """0 a 1: cuánto brilla un destello (PadSkin.twinkle)."""
        v = math.sin(self.T * 2.1 * speed + seed * 2.39)
        return max(0.0, v) ** 2

    def sheen(self, x, y, w, h, period=5.0, alpha=0.35):
        """El brillo que cruza en diagonal (PadSkin.sheen): dos bandas, una ancha y otra fina, cada period segundos."""
        self.sheen_at(x, y, w, h, (self.T % period) / 0.9, alpha)

    def sheen_at(self, x, y, w, h, k, alpha):
        """PadSkin.sheenAt: el brillo en el punto k de su paso."""
        if k < 0 or k >= 1:
            return
        pos = -h - 12 + k * (w + h + 24)
        for j in range(h):
            for off, bw, a in ((0, 6, alpha), (9, 2, alpha * 0.8)):
                x0 = max(x, int(x + pos + off - j))
                x1 = min(x + w, int(x + pos + off + bw - j))
                if x1 > x0:
                    self.fill(x0, y + j, x1, y + j + 1, with_a(WHITE, 255 * a))

    def icon_crisp(self, name, cx, cy, size):
        """Un icono de app (32x32) a un número entero de píxeles reales por píxel, el mayor que quepa en size unidades,
        centrado en (cx, cy) (PadSkin.iconCrisp)."""
        upx = self.f
        n = max(1, int(size * upx // 32))
        k = n / upx
        if name in getattr(self, 'ITEM_ICONS', {}):  # PadSkin.icon32 con un objeto: su luz detrás
            self.glow(cx, cy, 17 * k, WHITE, 90)
        im = self.img('icon_' + name).resize((32 * n, 32 * n), Image.NEAREST)
        r = self._rect(cx - 16 * k, cy - 16 * k, cx, cy)
        self._paste_px(im, r[0], r[1])

    def icon_alpha(self, name, x, y, size, alpha):
        item = getattr(self, 'ITEM_ICONS', {}).get(name)
        im = self.item_icon(item, outline=False) if item else self.img('icon_' + name)
        self.tex_alpha(im, x, y, size, size, alpha, smooth=False)

    def image(self, path, x, y, size, smooth=True, max_w=None):
        """Imagen de una fila o tarjeta (PadView: image), sin deformar (PadSkin.image): cabe en max_w x size centrada en
        el cuadrado (x, y, size); los retratos de los oficios (64x39) salen más anchos que altos."""
        im = Image.open(path).convert('RGBA')
        mw = max_w or size
        fit = min(mw / im.width, size / im.height)
        w, h = im.width * fit, im.height * fit
        cx, cy = x + size / 2, y + size / 2
        self.tex_alpha(im, cx - w / 2, cy - h / 2, w, h, 1.0, smooth=smooth)

    def stext(self, s, x, y, color, shadow):
        """Texto con sombra de color (PadSkin.shadowText)."""
        self.mtext(s, x + 1, y + 1, shadow)
        self.mtext(s, x, y, color)

    # ---------------------------------------------------------------------------------------------------------------
    # Piezas
    def sk_window(self, x, y, w, h, acc):
        """PadSkin.window: la ventana de la app (borde azul noche, degradado al color de la app abajo, textura, oro)."""
        self.fill(x + 2, y + h, x + w - 2, y + h + 1, (24, 38, 92, 85))
        self.fill(x + 4, y + h + 1, x + w - 4, y + h + 2, (24, 38, 92, 35))
        self.box(x, y, w, h, INK)
        self.vgrad(x + 1, y + 1, w - 2, h - 2, (250, 253, 255), mix((220, 236, 251), tint(acc, 0.3), 0.35))
        self.texture(x + 1, y + 1, w - 2, h - 2)
        self.fill(x + 2, y + 1, x + w - 2, y + 2, WHITE)
        self.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, mix((198, 218, 238), acc, 0.15))
        self.corners(x, y, w, h)

    def banner_h(self, lines):
        return max(24, len(lines) * 10 + 10)

    def sk_banner(self, x, y, w, h, acc, icon, lines):
        """PadSkin.banner: la cabecera de la app. Degradado de su color con rayas que se mueven, una luz a la derecha
        con el icono grande en marca de agua, el icono en su medallón y el texto en blanco; un brillo la cruza."""
        top, bot = mix(acc, WHITE, 0.14), darken(acc, 0.78)
        self.vgrad(x, y, w, h, top, bot)
        self.tiled('stripes', x, y, w, h, 0.08, off=self.T * 3)
        self.glow(x + w - 34, y + h / 2, 64, WHITE, 70)
        # el icono en grande, de marca de agua, a la derecha (recortado por la cabecera)
        clip = self.clip
        self.scissor(x, y, w, h)
        wm = 44
        self.icon_alpha(icon, x + w - wm - 10, y + h / 2 - wm / 2 + 3, wm, 0.22)
        self.clip = clip
        self.fill(x, y, x + w, y + 1, lighten(acc, 70))
        self.fill(x, y + h - 1, x + w, y + h, darken(acc, 0.55))
        self.fill(x, y + h, x + w, y + h + 1, (24, 38, 92, 50))
        # medallón
        ms = 20
        mx, my = x + 5, y + (h - ms) // 2
        self.box(mx, my, ms, ms, darken(acc, 0.45))
        self.vgrad(mx + 1, my + 1, ms - 2, ms - 2, mix(acc, WHITE, 0.62), mix(acc, WHITE, 0.22))
        self.fill(mx + 2, my + 1, mx + ms - 2, my + 2, WHITE)
        self.glow(mx + ms / 2, my + ms / 2, ms * 0.55, WHITE, 120)
        self.icon_crisp(icon, mx + ms / 2, my + ms / 2, ms - 3)
        # texto
        tx = mx + ms + 7
        sh = darken(acc, 0.42)
        ty = y + (h - len(lines) * 10) // 2 + 1
        for i, l in enumerate(lines):
            self.stext(l, tx, ty + i * 10, WHITE, sh)
        # destellos y brillo
        for i, (sx, sy) in enumerate(((w - 18, 5), (w - 58, h - 6), (w - 90, 6))):
            self.sparkle(x + sx, y + sy, 2, self.twinkle(i * 1.7 + 0.4) * 0.9)
        self.sheen(x, y, w, h - 1, period=6.0, alpha=0.18)

    TAB_H, TAB_GAP, TAB_PAD, TAB_PAD_MIN, TAB_SPACE, TAB_MIN = 17, 3, 9, 6, 3, 34
    TABS = TAB_H + TAB_GAP

    def sk_tab_compact(self, x, y, w, label, state, acc):
        """PadSkin.tabCompact: igual que la pestaña normal (17, letra a 4 de arriba) pero desde y - 4 (Comunidad, Música)."""
        c = deep(acc)
        h = self.TAB_H
        y -= 4
        if state == 2:
            edge, low = darken(c, 0.5), darken(c, 0.78)
            self.box(x, y, w, h, edge)
            self.vgrad(x + 1, y + 1, w - 2, h - 2, mix(c, WHITE, 0.30), darken(c, 0.9))
            self.fill(x + 2, y + 1, x + w - 2, y + 2, mix(c, WHITE, 0.62))
            self.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, low)
            cx = x + w // 2
            self.fill(cx - 1, y + h, cx + 2, y + h + 1, edge)
            if label:
                self.ptext_shadow(label, cx - self.pwidth(label) // 2, y + 4, WHITE, darken(c, 0.42))
            return
        hover = state == 1
        yy = y - 1 if hover else y
        self.box(x, yy, w, h, mix(c, INK, 0.25) if hover else rgb(0x7F9CC8))
        self.vgrad(x + 1, yy + 1, w - 2, h - 2, WHITE, mix(rgb(0xE4EEF9), tint(c, 0.35), 0.55) if hover else rgb(0xD3E3F4))
        self.fill(x + 1, yy + h - 2, x + w - 1, yy + h - 1, mix(c, WHITE, 0.25) if hover else rgb(0xBACFE6))
        if label:
            self.ptext(label, x + w // 2 - self.pwidth(label) // 2, yy + 4, ink(c) if hover else rgb(0x3A5888), False)

    def sk_tab(self, x, y, w, label, state, acc):
        """PadSkin.tab (1.4.1): pastilla de 17 con el texto centrado y aire; la elegida, del color de la app con su
        flechita hacia la ventana; las demás de cristal claro (al pasar el ratón suben 1 y se marcan con su color)."""
        c = deep(acc)
        H = self.TAB_H
        if state == 2:
            edge, low = darken(c, 0.5), darken(c, 0.78)
            self.box(x, y, w, H, edge)
            self.vgrad(x + 1, y + 1, w - 2, H - 2, mix(c, WHITE, 0.30), darken(c, 0.9))
            self.fill(x + 2, y + 1, x + w - 2, y + 2, mix(c, WHITE, 0.62))
            self.fill(x + 1, y + H - 2, x + w - 1, y + H - 1, low)
            cx = x + w // 2
            self.fill(cx - 1, y + H - 1, cx + 2, y + H, low)
            for i in range(self.TAB_GAP):
                hw = 2 - i
                self.fill(cx - hw, y + H + i, cx + hw + 1, y + H + i + 1, edge)
                if hw > 0:
                    self.fill(cx - hw + 1, y + H + i, cx + hw, y + H + i + 1, low)
            self.ptext_shadow(label, x + w // 2 - self.pwidth(label) // 2, y + 4, WHITE, darken(c, 0.42))
            return
        hover = state == 1
        yy = y - 1 if hover else y
        self.fill(x + 1, y + H, x + w - 1, y + H + 1, (24, 38, 92, 0x3C if hover else 0x24))
        self.box(x, yy, w, H, mix(c, INK, 0.25) if hover else rgb(0x7F9CC8))
        self.vgrad(x + 1, yy + 1, w - 2, H - 2, WHITE, mix(rgb(0xE4EEF9), tint(c, 0.35), 0.55) if hover else rgb(0xD3E3F4))
        self.fill(x + 1, yy + H - 2, x + w - 1, yy + H - 1, mix(c, WHITE, 0.25) if hover else rgb(0xBACFE6))
        self.ptext(label, x + w // 2 - self.pwidth(label) // 2, yy + 4, ink(c) if hover else rgb(0x3A5888), False)

    def ptext_shadow(self, s, x, y, color, shadow):
        """PadFont.drawShadowCentered: sombra 1 abajo (y abajo a la derecha) y el texto encima."""
        self.ptext(s, x, y + 1, shadow, False)
        self.ptext(s, x + 1, y + 1, shadow, False)
        self.ptext(s, x, y, color, False)

    def sk_tabs(self, x, y, room, labels, sel, acc, hover=-1):
        """PadSkin.tabRow: las pestañas en fila (con menos margen y luego recortadas si no caben); la elegida, la
        última. Devuelve [(x, ancho)]."""
        n = len(labels)

        def total_w(p2):
            return sum(max(self.TAB_MIN, self.pwidth(l) + p2) for l in labels) + self.TAB_SPACE * max(0, n - 1)
        p2 = self.TAB_PAD * 2
        total = total_w(p2)
        if total > room:
            p2 = self.TAB_PAD_MIN * 2
            total = total_w(p2)
        maxl = max(12, (room - self.TAB_SPACE * (n - 1)) // max(1, n) - p2) if total > room else 10 ** 6
        out, shown, tx = [], [], x
        for l in labels:
            l = self.pfit(l, maxl)
            w = max(0 if total > room else self.TAB_MIN, self.pwidth(l) + p2)
            out.append((tx, w))
            shown.append(l)
            tx += w + self.TAB_SPACE
        for i in range(n):
            if i != sel:
                self.sk_tab(out[i][0], y, out[i][1], shown[i], 1 if i == hover else 0, acc)
        if 0 <= sel < n:
            self.sk_tab(out[sel][0], y, out[sel][1], shown[sel], 2, acc)
        return out

    BTN2 = [(0x5AB4FF, 0x2C74E4, 0xB4E0FF, 0x1A4AA8), (0xFFD650, 0xE8961A, 0xFFF2B0, 0xA05E0E),
            (0xFF6274, 0xC82038, 0xFFB4BC, 0x861428), (0x68E886, 0x22A84C, 0xC4FFD0, 0x147034),
            (0xE6EDF6, 0xC7D3E3, 0xF7FAFD, 0xAEBCD0), (0x97A3BF, 0x66728F, 0xC9D0E2, 0x434D69)]

    def sk_button(self, x, y, w, label, style, enabled=True, hover=False):
        """PadSkin.button: caramelo de 16 de alto. Mitad de arriba más clara (brillo), sombra abajo; al pasar el ratón
        sube 1 y se aclara."""
        c = self.BTN2[4 if not enabled else 5 if style == 4 else min(style, 3)]
        top, bot, hi, lo = rgb(c[0]), rgb(c[1]), rgb(c[2]), rgb(c[3])
        if hover and enabled:
            top, bot = lighten(top, 26), lighten(bot, 26)
        lift = 1 if hover and enabled else 0
        yy = y - lift
        self.fill(x + 1, y + 16, x + w - 1, y + 17, (24, 38, 92, 70 if lift else 45))
        self.box(x, yy, w, 16, INK)
        self.vgrad(x + 1, yy + 1, w - 2, 7, mix(top, WHITE, 0.22), top)
        self.vgrad(x + 1, yy + 8, w - 2, 5, mix(top, bot, 0.55), bot)
        self.fill(x + 2, yy + 1, x + w - 2, yy + 2, hi)
        self.fill(x + 1, yy + 13, x + w - 1, yy + 15, lo)
        col = self.button_text(style, enabled)
        shade = self.button_shade(style, enabled)
        if self.is_back(label):
            ax, ay = x + 6, yy + 7
            for k, cc in ((1, shade), (0, col)):
                for i in range(4):
                    self.fill(ax + i, ay - i + k, ax + i + 1, ay + i + 1 + k, cc)
                self.fill(ax + 1, ay + k, ax + 7, ay + 1 + k, cc)
            self.ptext_shadow(label, x + w // 2 + 4 - self.pwidth(label) // 2, yy + 2, col, shade)
        elif label:
            self.ptext_shadow(label, x + w // 2 - self.pwidth(label) // 2, yy + 2, col, shade)

    def button_shade(self, style, enabled=True):
        """PadSkin.buttonShade (1.4.1): la sombra del texto, del color oscuro del botón."""
        if enabled and style == 1:  # oro: letra oscura con luz debajo
            return rgb(0xFFF0B0)
        c = self.BTN2[4 if not enabled else 5 if style == 4 else min(style, 3)]
        return darken(rgb(c[3]), 0.78) if enabled else rgb(0x1C2240)

    @staticmethod
    def button_text(style, enabled=True):
        """PadSkin.buttonText: blanco; en el de oro, marrón oscuro."""
        return rgb(0x8696B4) if not enabled else rgb(0x5A3304) if style == 1 else (255, 255, 255)

    def sk_chip(self, x, y, text, style, h=12):
        """PadUi.chip (1.4): etiqueta con brillo arriba y una sombra debajo."""
        pal = {'gold': (0xD69A1E, 0xFFF7D8, 0xFFE2A0, 0x7A4C00), 'green': (0x3DAA5C, 0xEAFBEF, 0xC2F0CF, 0x136B30),
               'gray': (0xB4C6DC, 0xF7FAFD, 0xE4ECF5, 0x5A7398), 'blue': (0x6FA8E8, 0xF0F7FF, 0xD4E7FC, 0x1E4E9C)}[style]
        w = self.chip_w(text)
        self.fill(x + 1, y + h, x + w - 1, y + h + 1, with_a(rgb(pal[0]), 70))
        self.box(x, y, w, h, rgb(pal[0]))
        self.vgrad(x + 1, y + 1, w - 2, h - 2, rgb(pal[1]), rgb(pal[2]))
        self.fill(x + 2, y + 1, x + w - 2, y + 2, WHITE)
        plus, coin, rest = self.money_parts(text)
        tx, ty = x + 4, y + (h - 8) // 2 + 1
        if plus:
            self.mtext('+', tx, ty, rgb(pal[3]))
            tx += self.mwidth('+')
        if coin:
            self.blit('coin_s', tx, y + (h - 8) // 2)
            tx += 9
        self.mtext(rest, tx, ty, rgb(pal[3]))
        return w

    def sk_progress(self, x, y, w, f, acc=None):
        """PadSkin.progress: carril hundido, relleno verde (oro si está completa) con brillo y un reflejo que corre."""
        f = max(0.0, min(1.0, f))
        self.box(x, y, w, 6, rgb(0x8FAACB))
        self.fill(x + 1, y + 1, x + w - 1, y + 5, rgb(0xDCE7F3))
        self.fill(x + 1, y + 1, x + w - 1, y + 2, rgb(0xBFD1E6))
        fw = round((w - 2) * f)
        if fw > 0:
            full = f >= 1
            self.vgrad(x + 1, y + 1, fw, 4, rgb(0xFFE47A if full else 0x86F0A4), rgb(0xE08E14 if full else 0x1E9E46))
            self.fill(x + 1, y + 1, x + 1 + fw, y + 2, rgb(0xFFF6C8 if full else 0xDAFFE4))
            # el reflejo que recorre la barra
            p = (self.T % 2.6) / 2.6
            sx = x + 1 + int(-12 + p * (fw + 24))
            for x0, x1, a0, a1 in ((sx, sx + 6, 0, 110), (sx + 6, sx + 12, 110, 0)):
                c0, c1 = max(x + 1, x0), min(x + 1 + fw, x1)
                if c1 > c0:
                    k0, k1 = (c0 - x0) / 6, (c1 - x0) / 6
                    self.hgrad(c0, y + 2, c1 - c0, 3, (255, 255, 255, a0 + (a1 - a0) * k0), (255, 255, 255, a0 + (a1 - a0) * k1))

    def sk_slot(self, x, y, s, color=None, glow=True):
        """PadSkin.medallion: el hueco de un icono, del color de su fila (o azul hielo), con su luz."""
        if color:
            edge, top, bot = tint(color, 0.62), tint(color, 0.10), tint(color, 0.30)
        else:
            edge, top, bot = rgb(0x9DBCE0), rgb(0xF4F9FE), rgb(0xD6E6F6)
        self.box(x, y, s, s, edge)
        self.vgrad(x + 1, y + 1, s - 2, s - 2, top, bot)
        self.fill(x + 2, y + 1, x + s - 2, y + 2, WHITE)
        if glow:
            self.glow(x + s / 2, y + s / 2, s * 0.55, WHITE, 150)

    def sk_card(self, x, y, w, h, state, acc, edge=None):
        """PadSkin.card: tarjeta blanca en relieve con sombra. state: 0 normal, 1 ratón encima (sube 1 y se ilumina
        del color de la app), 2 marcada (oro y cálida)."""
        lift = 1 if state == 1 else 0
        yy = y - lift
        self.fill(x + 1, y + h, x + w - 1, y + h + 1, (24, 38, 92, 75 if state == 1 else 48))
        if state == 1:
            self.fill(x + 3, y + h + 1, x + w - 3, y + h + 2, (24, 38, 92, 28))
        e = rgb(0xE0A030) if state == 2 else mix(acc, INK, 0.15) if state == 1 else (edge or rgb(0xC2D6EC))
        top = rgb(0xFFFCEE) if state == 2 else (255, 255, 255)
        bot = rgb(0xFFEDBE) if state == 2 else mix(rgb(0xEFF5FC), tint(acc, 0.25), 0.25 if state == 1 else 0)
        self.box(x, yy, w, h, e)
        self.vgrad(x + 1, yy + 1, w - 2, h - 2, top, bot)
        self.fill(x + 2, yy + 1, x + w - 2, yy + 2, WHITE)
        return yy

    def sk_section(self, x, y, w, title, color):
        """PadSkin.section: título de sección: barrita del color, texto en negrita y una línea que se apaga."""
        self.fill(x, y + 1, x + 3, y + 10, color)
        self.fill(x, y + 1, x + 3, y + 2, lighten(color, 80))
        self.btext(title, x + 6, y + 2, darken(color, 0.8))
        lx = x + 6 + self.bwidth(title) + 5
        if lx < x + w - 8:
            self.hgrad(lx, y + 6, x + w - lx - 4, 1, with_a(tint(color, 0.6), 255), with_a(tint(color, 0.6), 0))
            self.hgrad(lx, y + 7, x + w - lx - 4, 1, (255, 255, 255, 255), (255, 255, 255, 0))

    def sk_dock(self, x, y, w, h, acc):
        self.fill(x, y - 2, x + w, y - 1, mix(rgb(0xC6DAEE), acc, 0.25))
        self.fill(x, y - 1, x + w, y, WHITE)
        self.vgrad(x, y, w, h, rgb(0xF1F7FD), mix(rgb(0xDCE9F6), tint(acc, 0.3), 0.3))

    def sk_scrollbar(self, x, y, h, visible, content, scroll, acc):
        if content <= visible or h <= 4:
            return
        self.box(x, y, 4, h, rgb(0xD6E4F2))
        self.fill(x + 1, y + 1, x + 3, y + h - 1, rgb(0xE8F0F8))
        th = max(12, h * visible // content)
        ty = y + (h - th) * scroll // max(1, content - visible)
        self.box(x, ty, 4, th, darken(acc, 0.7))
        self.vgrad(x + 1, ty + 1, 2, th - 2, mix(acc, WHITE, 0.45), acc)

    def toggle(self, x, y, k, hover=False):
        """PadSkin.toggle: interruptor de 28x14 (k: 0 apagado, 1 encendido)."""
        top, bot = mix(rgb(0xC9D5E6), rgb(0x7CEC98), k), mix(rgb(0xA8B7CC), rgb(0x22A84C), k)
        if hover:
            top, bot = lighten(top, 18), lighten(bot, 18)
        self.fill(x + 1, y + 14, x + 27, y + 15, (24, 38, 92, 50))
        self.box(x, y, 28, 14, mix(rgb(0x6E80A0), rgb(0x147034), k))
        self.vgrad(x + 1, y + 1, 26, 12, bot, top)
        self.fill(x + 2, y + 1, x + 26, y + 2, (0, 0, 0, 48))
        if k > 0.5:
            self.ptext_shadow('SÍ', x + 4, y + 2, WHITE, rgb(0x0F5A26))
        else:
            self.ptext_shadow('NO', x + 27 - 4 - self.pwidth('NO'), y + 2, WHITE, rgb(0x56637E))
        kx = x + 1 + round(k * 14)
        self.fill(kx + 1, y + 13, kx + 12, y + 14, (24, 38, 92, 64))
        self.box(kx, y + 1, 13, 12, mix(rgb(0x8090B0), rgb(0x1E7C3A), k))
        self.vgrad(kx + 1, y + 2, 11, 10, WHITE, rgb(0xDCE6F2))
        self.fill(kx + 2, y + 2, kx + 11, y + 3, WHITE)

    def slider(self, x, y, w, f, acc, hover=False):
        """PadSkin.slider: deslizador de 14 de alto con el tirador."""
        f = max(0.0, min(1.0, f))
        ty = y + 4
        self.box(x, ty, w, 6, rgb(0x8FAACB))
        self.fill(x + 1, ty + 1, x + w - 1, ty + 5, rgb(0xDCE7F3))
        self.fill(x + 1, ty + 1, x + w - 1, ty + 2, rgb(0xBFD1E6))
        fw = round((w - 2) * f)
        if fw > 0:
            self.vgrad(x + 1, ty + 1, fw, 4, mix(acc, WHITE, 0.45), acc)
            self.fill(x + 1, ty + 1, x + 1 + fw, ty + 2, mix(acc, WHITE, 0.7))
        for i in range(1, 5):
            self.fill(x + (w - 1) * i // 5, ty + 6, x + (w - 1) * i // 5 + 1, ty + 8, (24, 38, 92, 80))
        kx = x + round((w - 9) * f)
        if hover:
            self.glow(kx + 4.5, y + 7, 10, mix(acc, WHITE, 0.4), 140)
        self.fill(kx + 1, y + 14, kx + 8, y + 15, (24, 38, 92, 64))
        self.box(kx, y, 9, 14, darken(acc, 0.6))
        self.vgrad(kx + 1, y + 1, 7, 12, WHITE, mix(acc, WHITE, 0.7))
        self.fill(kx + 3, y + 4, kx + 4, y + 10, mix(acc, WHITE, 0.3))
        self.fill(kx + 5, y + 4, kx + 6, y + 10, mix(acc, WHITE, 0.3))

    def sk_tile(self, x, y, s, color, hover=False):
        """PadSkin.tile: la pastilla del icono de una fila, del color de la fila, con las esquinas recortadas."""
        c = deep(color)
        edge = darken(c, 0.62)
        self.fill(x + 1, y + s, x + s - 1, y + s + 1, (24, 38, 92, 48))
        self.fill(x + 1, y, x + s - 1, y + s, edge)
        self.fill(x, y + 1, x + s, y + s - 1, edge)
        self.vgrad(x + 1, y + 1, s - 2, s - 2, mix(c, WHITE, 0.7 if hover else 0.62), mix(c, WHITE, 0.28 if hover else 0.18))
        self.fill(x + 2, y + 1, x + s - 2, y + 2, (255, 255, 255, 170))
        self.fill(x + 1, y + s - 3, x + s - 1, y + s - 1, tuple(darken(c, 0.8)) + (90,))
        self.glow(x + s / 2, y + s / 2, s * 0.5, WHITE, 200 if hover else 160)

    def sk_field(self, x, y, w, text, hint, acc):
        """PadSkin.field (1.4.2): marco del color de la app, cristal con sombra arriba, lápiz en su pastilla."""
        c = deep(acc)
        self.fill(x + 1, y + 16, x + w - 1, y + 17, (24, 38, 92, 36))
        self.box(x, y, w, 16, mix(c, INK, 0.35))
        self.vgrad(x + 1, y + 1, w - 2, 14, mix(rgb(0xE9F1FA), tint(c, 0.25), 0.4), WHITE)
        self.fill(x + 1, y + 1, x + w - 1, y + 2, mix(rgb(0xC9D8EA), c, 0.18))
        px, py = x + 2, y + 2
        self.vgrad(px, py, 12, 12, mix(c, WHITE, 0.35), c)
        self.fill(px + 12, py, px + 13, py + 12, mix(c, INK, 0.3))
        for i in range(6):
            self.fill(px + 3 + i, py + 8 - i, px + 5 + i, py + 9 - i, WHITE)
        self.fill(px + 3, py + 9, px + 4, py + 10, mix(c, WHITE, 0.8))
        tx, tw = x + 18, w - 22
        shown = self.fitend(hint, tw) if not text else text
        self.mtext(shown, tx, y + 4, mix(rgb(0x96AACC), c, 0.25) if not text else rgb(0x18265C))
        cx = tx + (0 if not text else self.mwidth(shown))
        self.fill(cx, y + 3, cx + 1, y + 13, c)

    def sk_list_footer(self, cx, cy, room, acc, icon):
        """PadSkin.listFooterArt: bajo una lista corta, el icono de la app grande y suave con su luz y destellos."""
        if room < 46 or not icon:
            return
        s = min(56, room - 12)
        self.glow(cx, cy, s * 1.2, acc, 46)
        try:
            self.tex_alpha('icon_' + icon, cx - s / 2, cy - s / 2, s, s, 0.16, smooth=False)
        except Exception:
            pass
        for i in range(4):
            tw = max(0.0, math.sin(self.T * 1.9 + i * 1.7))
            sx = cx + int((-1 if i % 2 == 0 else 1) * (s * 0.55 + i * 4))
            sy = cy + (-s // 4 if i < 2 else s // 5)
            self.sparkle(sx, sy, i % 2 + 1, tw * tw * 0.7, mix(acc, WHITE, 0.5))

    def sk_empty(self, cx, mid, w, text, acc, icon, room):
        """PadSkin.emptyState: el icono de la app flotando en su luz, con destellos, y el texto debajo."""
        nl = len(self.split(text, w))
        if icon and room > 60:
            big = room > 110
            r = 27 if big else 19
            iy = mid - (26 if big else 18) - nl * 5
            bob = round(math.sin(self.T * 2.2) * 1.5)
            self.glow(cx, iy, r * 1.9, tint(acc, 0.55), 160)
            self.disc(cx, iy, r, with_a(tint(acc, 0.22), 255))
            self.disc(cx, iy, r - 2, with_a(mix(acc, WHITE, 0.86), 255))
            self.glow(cx, iy - 3, r * 0.8, WHITE, 200)
            self.icon_crisp(icon, cx, iy + bob, 44 if big else 30)
            k = r / 19
            for i, (dx, dy) in enumerate(((-26, -10), (24, -14), (20, 12), (-22, 13))):
                self.sparkle(int(cx + dx * k), int(iy + dy * k), 2, self.twinkle(i * 1.3), tint(acc, 0.6))
            mid += 22 if big else 14
        for i, l in enumerate(self.split(text, w)):
            self.mtext(l, cx - self.mwidth(l) // 2, mid - nl * 5 + i * 10, MUTED)

    # ---------------------------------------------------------------------------------------------------------------
    # El fondo del cristal (TFPadScreen.drawBackdrop)
    BOKEH = [  # x, y (0-1 del cristal), radio (unidades), velocidad (unidades/s), vaivén (amplitud, periodo), color, alfa
        (0.08, 0.30, 46, 3.0, 14, 9.0, (255, 255, 255), 0.20), (0.22, 0.85, 30, 4.5, 10, 7.0, (220, 246, 255), 0.26),
        (0.37, 0.55, 64, 2.2, 18, 11.0, (255, 255, 255), 0.13), (0.52, 0.15, 26, 5.0, 8, 6.5, (255, 244, 200), 0.24),
        (0.63, 0.70, 52, 2.8, 16, 10.0, (255, 255, 255), 0.16), (0.78, 0.35, 36, 3.8, 12, 8.0, (200, 238, 255), 0.22),
        (0.90, 0.80, 58, 2.5, 20, 12.0, (255, 255, 255), 0.14), (0.95, 0.12, 24, 4.2, 9, 7.5, (255, 236, 170), 0.22),
        (0.45, 0.95, 40, 3.4, 13, 9.5, (230, 250, 255), 0.18),
    ]
    STARS = [(0.12, 0.62), (0.27, 0.18), (0.33, 0.82), (0.48, 0.40), (0.58, 0.88), (0.66, 0.22), (0.74, 0.58),
             (0.86, 0.30), (0.92, 0.66), (0.18, 0.92), (0.55, 0.62), (0.81, 0.90), (0.40, 0.12), (0.70, 0.78)]

    def backdrop(self, t=None, home=None, fade=1.0):
        """El fondo del cristal (1.4): sin quitar su azul, encima van luces suaves desenfocadas que suben despacio
        meciéndose, un haz de luz en diagonal que lo cruza de vez en cuando, destellos que parpadean y el rombo tallado
        muy suave. Se recorta para no pisar el logo (arriba a la izquierda). Sin animaciones (Ajustes), quieto."""
        t = self.T if t is None else t
        W, H = self.UW, self.UH
        self.scissor(0, 0, W, H)
        # el tema (PadSettings.THEMES; THEME=n en el entorno) y los monogramas TF (TFPadScreen.monograms)
        th = THEMES[int(os.environ.get('THEME', '0'))]
        self.vgrad(0, 0, W, H, rgb(th[1]), rgb(th[2]))
        sx, sy = 46, 30
        ox, oy = (t * 3) % sx, (t * 2) % (sy * 2)
        row = -2
        while row * sy + oy < H + sy:
            y = row * sy + oy
            shift = 0 if row % 2 == 0 else sx / 2
            x = -sx + ox + shift
            while x < W + sx:
                self.mtext('TF', round(x) + 1, round(y) + 1, (0, 0, 0, 12))
                self.mtext('TF', round(x), round(y), rgb(th[3]) + (28,))
                x += sx
            row += 1
        # el rombo tallado, muy suave, y una sombra leve abajo: el cristal gana relieve sin perder su azul
        self.texture(0, 0, W, H)
        self.vgrad_a(0, H * 2 // 3, W, H - H * 2 // 3, (20, 70, 150, 0), (20, 70, 150, 22))
        # el haz de luz en diagonal (cada 13 s)
        k = (t % 13.0) / 4.5
        if k < 1:
            self.beam(-120 + k * (W + 240), 70, 0.16)
        for (bx, by, r, v, amp, per, col, a) in self.BOKEH:
            y = (by * (H + 2 * r) - v * t) % (H + 2 * r) - r
            x = bx * W + amp * math.sin(t * 2 * math.pi / per + bx * 9)
            self.tex_alpha('bokeh', x - r, y - r, 2 * r, 2 * r, a, col)
        for i, (sx, sy) in enumerate(self.STARS):
            tw = max(0.0, math.sin(t * 1.7 + i * 2.17)) ** 3
            self.sparkle(int(sx * W), int(sy * H), 2 if i % 3 == 0 else 1, tw * 0.85)
        if home:  # en la portada, su cielo animado (PadHomePage.ambient)
            self.home_ambient(home, t, fade)
        self.no_scissor()
        # el logo, otra vez encima (las luces pasan por detrás)
        logo = self.img('logo_over')
        k = self.ps / 4
        self._paste_px(logo.resize((round(logo.width * k), round(logo.height * k)), Image.NEAREST), 0, 0)

    def vgrad_a(self, x, y, w, h, ct, cb):
        """Degradado vertical con alfa (fillGradient con colores ARGB)."""
        r = self._rect(x, y, x + w, y + h)
        Wp, Hp = r[2] - r[0], r[3] - r[1]
        if Wp <= 0 or Hp <= 0:
            return
        t = (np.arange(Hp) + 0.5) / Hp
        col = np.outer(1 - t, np.array(ct, float)) + np.outer(t, np.array(cb, float))
        self._arr_paste(np.repeat(col[:, None, :], Wp, axis=1), r[0], r[1])

    def beam(self, cx, half, a):
        """Haz de luz inclinado (sube hacia la derecha): blanco con alfa a en el centro que se apaga a los lados (dos
        cuadriláteros con degradado en el Java). cx: dónde corta la mitad de la altura del cristal."""
        W, H = self.UW, self.UH
        r = self._rect(0, 0, W, H)
        Wp, Hp = r[2] - r[0], r[3] - r[1]
        yy, xx = np.mgrid[0:Hp, 0:Wp]
        f = self.f
        u = ((xx + 0.5) / f - cx + ((yy + 0.5) / f - H / 2) * 0.6) / half
        arr = np.zeros((Hp, Wp, 4))
        arr[..., :3] = 255
        arr[..., 3] = np.clip(1 - np.abs(u), 0, 1) * a * 255
        self._arr_paste(arr, r[0], r[1])
