"""Simulador de las páginas del TF Pad (mismas posiciones y colores que el código Java) para ver cómo quedan."""
import json
import math
import os
import sys
import unicodedata

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from skin import (GOLD_HI, Skin, WHITE, accent, darken, deep, icon_of, ink, lighten as _lighten, mix, tagline, with_a)  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
PAD = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(PAD))  # tf-client/
TEX = os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/gui/pad')


def _mc_textures():
    """Texturas de Minecraft (fuente ascii e iconos de objetos) sacadas del client-extra.jar de ForgeGradle."""
    import zipfile
    jar = os.environ.get('TF_MC_JAR') or os.path.expanduser('~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar')
    out = os.path.expanduser('~/.cache/tfclient-sim')
    base = os.path.join(out, 'assets/minecraft/textures')
    if not os.path.exists(os.path.join(base, 'font/ascii.png')):
        if not os.path.exists(jar):
            sys.exit('No encuentro client-extra.jar: compila el mod una vez (./gradlew build) o pon TF_MC_JAR=<ruta>.')
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.startswith('assets/minecraft/textures/font/') or n.startswith('assets/minecraft/textures/item/'):
                    z.extract(n, out)
    if not os.path.isdir(os.path.join(base, 'block')) and os.path.exists(jar):
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.startswith('assets/minecraft/textures/block/') and n.endswith('.png'):
                    z.extract(n, out)
    if not os.path.isdir(os.path.join(base, 'entity/player')) and os.path.exists(jar):
        with zipfile.ZipFile(jar) as z:  # las caras de Steve y Alex para las cabezas de jugador
            for n in z.namelist():
                if n.startswith('assets/minecraft/textures/entity/player/wide/') and n.endswith('.png'):
                    z.extract(n, out)
    return base


MC = _mc_textures()
S = 4
NAVY = (24, 38, 92)


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


GX, GY, GW, GH = 52, 64, 288, 132
BAR = 18
# TFPadScreen.STRETCH_U/D y MAX_EXTRA: filas de frame.png que se repiten para hacer el pad más alto
STRETCH_U, STRETCH_D, MAX_EXTRA = 430, 596, 48
# 1.3.38: también a lo ancho (columnas repetidas a cada lado de la gema) y el tamaño se calcula con lo que se ve
STRETCH_L, STRETCH_R, MAX_EXTRA_X = 540, 1028, 24
VIS_L, VIS_R, VIS_T, VIS_B = 10, 382, 2, 235
INK = (24, 38, 92)
GOLD, GOLD_HI, GOLD_LO = (246, 182, 40), (255, 229, 138), (192, 120, 24)
PANEL_TOP, PANEL_BOT, PANEL_HI = (248, 252, 255), (220, 238, 252), (255, 255, 255)
SLOT, SLOT_EDGE, SLOT_SHADE = (226, 238, 250), (157, 188, 224), (194, 216, 238)
TEXT, MUTED, GOLD_TEXT = (24, 38, 92), (74, 102, 148), (194, 122, 16)
GREEN_TEXT, RED_TEXT = (30, 158, 70), (200, 50, 60)


def on_dark(c):
    """PadUi.onDark (1.3.26, en blanco): los colores del servidor se usan tal cual."""
    return rgb(c)


def lighten(c, n):
    return tuple(min(255, v + n) for v in c)


def tint(c, k):
    """PadUi.tint: el color c mezclado con blanco (k=1 el color tal cual, k=0 blanco)."""
    return tuple(int(round(255 + (v - 255) * k)) for v in c[:3])


class Sim(Skin):
    """El cristal del pad, dibujado en píxeles reales como en el juego (TFPadScreen.init): ps = píxeles reales por
    píxel del marco, cs = píxeles reales por unidad de contenido, big = píxeles reales por píxel de textura en la escala
    grande (portada, barra de arriba, iconos de la app Música). Con ps=4 (1080p a escala 2): cs=2, big=3 (bs=1,5),
    576x264 unidades. Las coordenadas de todos los métodos son unidades; dentro de `with s.big_at(x, y)` son píxeles
    de textura a la escala grande con la esquina en (x, y)."""

    def __init__(self, ps=None, fbh=1080, fbw=1920, backdrop=True):
        vw, vh = VIS_R - VIS_L, VIS_B - VIS_T
        self.fbw, self.fbh = fbw, fbh
        self.ps = ps = ps or max(1, int(min(fbw * 0.97 / vw, fbh * 0.975 / vh)))
        self.cs = ps if ps <= 2 else max(2, int(ps / 2 + 0.5))
        self.big = max(self.cs, int(ps * 0.75 + 0.5))
        self.bs = self.big / self.cs
        self.bar = round(12 * self.bs) + 6
        self.extra = max(0, min(MAX_EXTRA, int((fbh * 0.985 / ps - vh) // 2)))
        self.extra_x = max(0, min(MAX_EXTRA_X, int((fbw * 0.97 / ps - vw) // 2)))
        self.UW, self.UH = (GW + self.extra_x * 2) * ps // self.cs, (GH + self.extra * 2) * ps // self.cs
        self.Y = self.bar + 4
        self.X = self.logo_right(self.Y) + 4
        self.W = self.UW - self.X * 2
        self.H = self.UH - self.Y - 4
        self.clip = None
        self.o, self.f = (0, 0), self.cs
        self.art = Image.new('RGBA', (self.UW * self.cs, self.UH * self.cs), (0, 0, 0, 0))
        self.tex = {}
        meta = json.load(open(f'{TEX}/font.json', encoding='utf-8'))
        self.fchars, self.fw, self.fcols = meta['chars'], meta['widths'], meta['cols']
        self.fimg = Image.open(f'{TEX}/font.png').convert('RGBA')
        self.mcfont = Image.open(f'{MC}/font/ascii.png').convert('RGBA')
        self.mcw = {}
        for code in range(256):
            cx, cy = (code % 16) * 8, (code // 16) * 8
            w = 0
            for x in range(8):
                if any(self.mcfont.getpixel((cx + x, cy + y))[3] for y in range(8)):
                    w = x + 1
            self.mcw[code] = w
        if backdrop:
            self.backdrop()

    def units(self, pad_px):
        return -(-pad_px * self.ps // self.cs)

    def logo_right(self, y):
        pad_y = GY + y * self.cs / self.ps
        pad_x = 81 if pad_y < 66 else 79 if pad_y < 70 else 71 if pad_y < 80 else 61 if pad_y < 86 else GX
        return self.units(pad_x - GX)

    def bigu(self, n):
        """Tamaño en unidades de n píxeles de textura a la escala grande (TFPadScreen.big)."""
        return int(n * self.bs + 0.5)

    class _Scaled:
        def __init__(self, sim, ux, uy, px):
            self.sim, self.args = sim, (ux, uy, px)

        def __enter__(self):
            s = self.sim
            self.saved = (s.o, s.f)
            ux, uy, px = self.args
            s.o = (round(s.o[0] + ux * s.f), round(s.o[1] + uy * s.f))
            s.f = px
            return s

        def __exit__(self, *a):
            self.sim.o, self.sim.f = self.saved

    def big_at(self, ux, uy, px=None):
        return Sim._Scaled(self, ux, uy, px or self.big)

    def scissor(self, x, y, w, h):
        self.clip = self._rect(x, y, x + w, y + h)

    def no_scissor(self):
        self.clip = None

    # -------------------------------------------------------------------------------------------------------------
    def _rect(self, x0, y0, x1, y1):
        ox, oy = self.o
        f = self.f
        return (int(round(ox + x0 * f)), int(round(oy + y0 * f)), int(round(ox + x1 * f)), int(round(oy + y1 * f)))

    # PadSkin.ITEM_ICONS (1.4.1): iconos que son el objeto de Minecraft de verdad (al doble y con contorno azul noche)
    ITEM_ICONS = {'oficios': 'diamond_pickaxe'}

    def img(self, name):
        if name not in self.tex:
            item = self.ITEM_ICONS.get(name[5:]) if name.startswith('icon_') else None
            self.tex[name] = self.item_icon(item) if item else Image.open(f'{TEX}/{name}.png').convert('RGBA')
        return self.tex[name]

    def icon32(self, name, x, y):
        """PadSkin.icon32: el icono de 32 con la esquina en (x, y); si es un objeto, con una luz suave detrás."""
        if name in self.ITEM_ICONS:
            self.glow(x + 16, y + 16, 17, WHITE, 90)
        self.blit('icon_' + name, x, y)

    @staticmethod
    def item_icon(item, outline=True):
        """PadSkin.icon32 con un objeto: la textura de 16 al doble (32x32) y, debajo, la misma teñida de azul noche
        (multiplicada, como setColor) un píxel hacia cada lado: el contorno."""
        tex = Image.open(f'{MC}/item/{item}.png').convert('RGBA').resize((32, 32), Image.NEAREST)
        if not outline:
            return tex
        a = np.asarray(tex, float)
        sh = a.copy()
        sh[..., :3] *= np.array(NAVY, float) / 255
        shadow = Image.fromarray(sh.round().astype(np.uint8), 'RGBA')
        out = Image.new('RGBA', (34, 34), (0, 0, 0, 0))
        for dx, dy in ((0, 1), (2, 1), (1, 0), (1, 2)):
            out.alpha_composite(shadow, (dx, dy))
        out.alpha_composite(tex, (1, 1))
        return out.crop((1, 1, 33, 33))

    def _paste_px(self, im, rx, ry):
        """Pega una imagen ya en píxeles reales, respetando el recorte."""
        W, H = self.art.size
        x0, y0, x1, y1 = self.clip if self.clip else (0, 0, W, H)
        x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, W), min(y1, H)
        cx0, cy0 = max(0, x0 - rx), max(0, y0 - ry)
        cx1, cy1 = min(im.width, x1 - rx), min(im.height, y1 - ry)
        if cx1 <= cx0 or cy1 <= cy0:
            return
        self.art.alpha_composite(im.crop((cx0, cy0, cx1, cy1)), (rx + cx0, ry + cy0))

    def paste(self, im, x, y, px=None):
        """Pega una textura (1 píxel de textura = f píxeles reales, o px) con la esquina en (x, y)."""
        k = px or self.f
        if k != 1:
            im = im.resize((int(im.width * k), int(im.height * k)), Image.NEAREST)
        rx, ry = self._rect(x, y, x, y)[:2]
        self._paste_px(im, rx, ry)

    def paste_fit(self, im, x, y, w, h):
        """Una imagen (foto, portada) ajustada a w x h unidades, suavizada."""
        r = self._rect(x, y, x + w, y + h)
        self._paste_px(im.resize((r[2] - r[0], r[3] - r[1]), Image.LANCZOS).convert('RGBA'), r[0], r[1])

    def blit(self, name, x, y):
        self.paste(self.img(name), x, y)

    def fill(self, x0, y0, x1, y1, c):
        if x1 <= x0 or y1 <= y0:
            return
        r = self._rect(x0, y0, x1, y1)
        if r[2] <= r[0] or r[3] <= r[1]:
            return
        col = tuple(c) + (255,) if len(c) == 3 else tuple(c)
        self._paste_px(Image.new('RGBA', (r[2] - r[0], r[3] - r[1]), col), r[0], r[1])

    def put(self, x, y, c):
        self.fill(x, y, x + 1, y + 1, c)

    def box(self, x, y, w, h, c):
        self.fill(x + 1, y, x + w - 1, y + h, c)
        self.fill(x, y + 1, x + w, y + h - 1, c)

    def vgrad(self, x, y, w, h, top, bot):
        for i in range(max(0, h)):
            t = i / max(1, h - 1)
            self.fill(x, y + i, x + w, y + i + 1, tuple(int(top[k] * (1 - t) + bot[k] * t) for k in range(3)))

    def stud(self, cx, cy):
        self.fill(cx - 1, cy - 1, cx + 2, cy + 2, GOLD_LO)
        self.fill(cx, cy - 1, cx + 1, cy + 2, GOLD)
        self.fill(cx - 1, cy, cx + 2, cy + 1, GOLD)
        self.fill(cx, cy - 1, cx + 1, cy + 1, GOLD_HI)

    def texture(self, x, y, w, h):
        """PadUi.texture: el dibujo de rombos (pattern.png, 16x16) en mosaico."""
        im = self.img('pattern')
        for ty in range(y, y + h, 16):
            for tx in range(x, x + w, 16):
                self.paste(im.crop((0, 0, min(16, x + w - tx), min(16, y + h - ty))), tx, ty)

    def stretch(self, name, x, y, w, h):
        """Una textura estirada a w x h unidades (la luz y la sombra de las vitrinas)."""
        im = self.img(name)
        r = self._rect(x, y, x + w, y + h)
        self._paste_px(im.resize((max(1, r[2] - r[0]), max(1, r[3] - r[1])), Image.BILINEAR), r[0], r[1])

    def panel(self, x, y, w, h):
        """PadUi.panel (1.3.28): la ventana de la app. Borde azul noche, blanco con bisel, textura de cristal tallado y
        esquinas de oro."""
        self.fill(x + 2, y + h, x + w - 2, y + h + 1, (40, 110, 200, 90))
        self.box(x, y, w, h, INK)
        self.vgrad(x + 1, y + 1, w - 2, h - 2, PANEL_TOP, PANEL_BOT)
        self.texture(x + 1, y + 1, w - 2, h - 2)
        self.fill(x + 2, y + 1, x + w - 2, y + 2, (255, 255, 255))
        self.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, rgb(0xC6DAEE))
        self.corners(x, y, w, h)

    def corners(self, x, y, w, h):
        for cx, cy, dx, dy in ((x + 2, y + 2, 1, 1), (x + w - 3, y + 2, -1, 1), (x + 2, y + h - 3, 1, -1), (x + w - 3, y + h - 3, -1, -1)):
            for i in range(4):
                self.put(cx + dx * i, cy, GOLD)
                self.put(cx, cy + dy * i, GOLD)
            self.put(cx, cy, GOLD_HI)

    def ucard(self, x, y, w, h, state=0):
        """PadUi.card (1.3.28): 0 normal, 1 ratón encima, 2 marcada."""
        edge = rgb(0xE0A030) if state == 2 else rgb(0x3496FA) if state == 1 else rgb(0xBBD3EC)
        top = rgb(0xFFFBEA) if state == 2 else rgb(0xF2F9FF) if state == 1 else (255, 255, 255)
        bot = rgb(0xFFEFC0) if state == 2 else rgb(0xDCEEFF) if state == 1 else rgb(0xF1F7FD)
        self.fill(x + 1, y + h, x + w - 1, y + h + 1, (52, 150, 250, 90) if state == 1 else (40, 90, 160, 45))
        self.box(x, y, w, h, edge)
        self.vgrad(x + 1, y + 1, w - 2, h - 2, top, bot)
        self.fill(x + 2, y + 1, x + w - 2, y + 2, (255, 255, 255))

    def slot(self, x, y, w, h, color=None):
        """PadUi.slot (1.3.28): medallón para un objeto, del color de su fila (o azul hielo)."""
        edge = tint(color, 0.45) if color else SLOT_EDGE
        top = tint(color, 0.08) if color else rgb(0xF0F6FD)
        bot = tint(color, 0.20) if color else SLOT
        self.box(x, y, w, h, edge)
        self.vgrad(x + 1, y + 1, w - 2, h - 2, top, bot)
        self.fill(x + 2, y + 1, x + w - 2, y + 2, (255, 255, 255))

    def tab(self, x, y, w, label, state):
        """PadUi.tab (1.3.28): pestaña de carpeta de 15 de alto; la elegida baja y se une a la ventana (su borde de
        arriba está en y + 14), con su filo de oro."""
        if state == 2:
            self.box(x, y, w, 15, INK)
            self.vgrad(x + 1, y + 1, w - 2, 15, (255, 255, 255), PANEL_TOP)
            self.fill(x + 2, y + 1, x + w - 2, y + 3, GOLD)
            self.fill(x + 2, y + 1, x + w - 2, y + 2, GOLD_HI)
            self.ptext(label, x + w // 2 - self.pwidth(label) // 2, y + 4, INK, False)
        else:
            top, bot = (rgb(0xF2F9FF), rgb(0xCFE3F6)) if state == 1 else (rgb(0xE2EFFB), rgb(0xC0D7EE))
            self.box(x, y + 2, w, 13, rgb(0x6A88B8))
            self.vgrad(x + 1, y + 3, w - 2, 11, top, bot)
            self.ptext(label, x + w // 2 - self.pwidth(label) // 2, y + 4, rgb(0x34507E), False)

    def divider(self, x, y, w, title, color):
        self.btext(title, x, y + 1, color)
        lx = x + self.bwidth(title) + 5
        if lx < x + w - 8:
            self.fill(lx, y + 5, x + w - 6, y + 6, tint(GOLD_LO, 0.6))
            self.fill(lx, y + 6, x + w - 6, y + 7, (255, 255, 255))
        cx = x + w - 4
        self.fill(cx - 2, y + 5, cx + 3, y + 6, GOLD_LO)
        self.fill(cx - 1, y + 4, cx + 2, y + 7, GOLD)
        self.fill(cx, y + 3, cx + 1, y + 8, GOLD)
        self.fill(cx, y + 4, cx + 1, y + 5, GOLD_HI)

    def scrollbar(self, x, y, h, visible, content, scroll=0):
        if content <= visible or h <= 4:
            return
        self.box(x, y, 4, h, rgb(0xD2E2F2))
        th = max(10, h * visible // content)
        ty = y + (h - th) * scroll // max(1, content - visible)
        self.box(x, ty, 4, th, GOLD_LO)
        self.vgrad(x + 1, ty + 1, 2, th - 2, GOLD_HI, GOLD)

    def infobar(self, x, y, w, header, room):
        """La barra de información arriba de la ventana: un rombo de oro y el texto (lo que antes iba sobre el
        cristal). Devuelve su alto."""
        lines = []
        for line in header:
            for l in self.split(line, w - 30):
                lines.append(l)
        if len(lines) > room:
            lines = lines[:room]
            lines[-1] = self.fitend(lines[-1] + "...", w - 30)
        h = len(lines) * 10 + 4
        self.vgrad(x, y, w, h, rgb(0xF0F7FE), rgb(0xE0EDFA))
        self.fill(x, y + h, x + w, y + h + 1, rgb(0xC9DCF0))
        self.fill(x, y + h + 1, x + w, y + h + 2, (255, 255, 255))
        self.diamond(x + 7, y + 6)
        for i, l in enumerate(lines):
            self.mtext(l, x + 13, y + 3 + i * 10, TEXT)
        return h + 2

    def split(self, s, w):
        words, line, out = s.split(' '), '', []
        for wd in words:
            t = (line + ' ' + wd).strip()
            if self.mwidth(t) > w and line:
                out.append(line)
                line = wd
            else:
                line = t
        if line:
            out.append(line)
        return out

    def dock(self, x, y, w, h):
        """La barra de acciones abajo de la ventana."""
        self.fill(x, y - 2, x + w, y - 1, rgb(0xC6DAEE))
        self.fill(x, y - 1, x + w, y, (255, 255, 255))
        self.vgrad(x, y, w, h, rgb(0xEAF3FC), rgb(0xD8E7F6))

    @staticmethod
    def tone(text, on=True, tone=0):
        """El color de una etiqueta (PadUi.tone): el que pida el servidor (1 oro, 2 verde, 3 gris, 4 azul) o, si no,
        gris si no se puede pulsar, oro si es un precio («¤…»), verde si es lo que ganas («+…»), azul si es un estado."""
        if tone:
            return {1: 'gold', 2: 'green', 3: 'gray', 4: 'blue'}[tone]
        if not on:
            return 'gray'
        return 'gold' if text.startswith('¤') else 'green' if text.startswith('+') else 'blue'

    @staticmethod
    def money_parts(text):
        plus = text.startswith('+¤')
        coin = plus or text.startswith('¤')
        return plus, coin, text[2:] if plus else text[1:] if coin else text

    def chip_w(self, text):
        plus, coin, rest = self.money_parts(text)
        return (self.mwidth('+') if plus else 0) + (9 if coin else 0) + self.mwidth(rest) + 8

    def chip(self, x, y, text, style, h=12):
        """PadUi.chip: etiqueta de estado o de precio. style: gold, green, gray, blue. «¤1.250» lleva la moneda delante
        («+¤48»: más la moneda)."""
        pal = {'gold': (0xD69A1E, 0xFFF7D8, 0xFFE2A0, 0x7A4C00), 'green': (0x3DAA5C, 0xEAFBEF, 0xC2F0CF, 0x136B30),
               'gray': (0xB4C6DC, 0xF7FAFD, 0xE4ECF5, 0x5A7398), 'blue': (0x6FA8E8, 0xF0F7FF, 0xD4E7FC, 0x1E4E9C)}[style]
        w = self.chip_w(text)
        self.box(x, y, w, h, rgb(pal[0]))
        self.vgrad(x + 1, y + 1, w - 2, h - 2, rgb(pal[1]), rgb(pal[2]))
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

    def btext(self, s, x, y, color):
        """Texto en negrita (como el de Minecraft: dos veces, a 1 de distancia)."""
        self.mtext(s, x, y, color)
        self.mtext(s, x + 1, y, color)

    def bwidth(self, s):
        return self.mwidth(s) + sum(1 for ch in s if ch != ' ')

    def title(self, s, x, y, w, color):
        """PadUi.title: en negrita; si así no cabe pero normal sí, normal; si no, recortado en negrita."""
        if self.bwidth(s) <= w:
            self.btext(s, x, y, color)
        elif self.mwidth(s) <= w:
            self.mtext(s, x, y, color)
        else:
            self.btext(self.bfit(s, w), x, y, color)

    def title_w(self, s, w):
        b = self.bwidth(s)
        return b if b <= w else self.mwidth(s) if self.mwidth(s) <= w else w

    def bfit(self, s, w):
        if self.bwidth(s) <= w:
            return s
        while s and self.bwidth(s + '...') > w:
            s = s[:-1]
        return s + '...'

    # pixel font
    def pwidth(self, s):
        w = 0
        for ch in s.upper():
            i = self.fchars.find(ch)
            w += (self.fw[i] if i >= 0 else 2) + 1
        return max(0, w - 1)

    def ptext(self, s, x, y, color=(255, 255, 255), outline=True):
        pts = []
        cx = x
        for ch in s.upper():
            i = self.fchars.find(ch)
            if i < 0:
                cx += 3
                continue
            w = self.fw[i]
            ox, oy = (i % self.fcols) * 6, (i // self.fcols) * 10
            for gy in range(10):
                for gx in range(w):
                    if self.fimg.getpixel((ox + gx, oy + gy))[3]:
                        pts.append((cx + gx, y + gy))
            cx += w + 1
        st = set(pts)
        if outline:
            oc = outline if isinstance(outline, tuple) else NAVY  # PadFont.draw(…, outline): contorno de otro color
            for px, py in pts:
                for dx, dy in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (1, 1), (-1, 2), (0, 2), (1, 2), (0, 1)):
                    if (px + dx, py + dy) not in st:
                        self.put(px + dx, py + dy, oc)
        for p in pts:
            self.put(p[0], p[1], color)

    # Minecraft font
    def mcbase(self, ch):
        ch = {'·': '.', '«': '"', '»': '"', '…': '.', '¿': '?', '¡': '!', 'º': 'o', 'ª': 'a'}.get(ch, ch)
        if ord(ch) < 256 and ord(ch) >= 32 and ch.isascii():
            return ch
        d = unicodedata.normalize('NFD', ch)[0]
        return d if d.isascii() else '?'

    def mwidth(self, s):
        w = 0
        for ch in s:
            c = self.mcbase(ch)
            w += (4 if c == ' ' else self.mcw[ord(c)] + 1)
        return w

    def mtext(self, s, x, y, color):
        cx = x
        for ch in s:
            c = self.mcbase(ch)
            code = ord(c)
            if c == ' ':
                cx += 4
                continue
            gx, gy = (code % 16) * 8, (code // 16) * 8
            flip = ch in '¿¡'  # como en Minecraft: el ? y el ! dados la vuelta
            for yy in range(8):
                for xx in range(self.mcw[code]):
                    if self.mcfont.getpixel((gx + xx, gy + yy))[3]:
                        if flip:
                            self.put(cx + self.mcw[code] - 1 - xx, y + 7 - yy, color)
                        else:
                            self.put(cx + xx, y + yy, color)
            cx += self.mcw[code] + 1

    def wrap(self, s, x, y, w, color, maxl=99):
        words, line, n = s.split(' '), '', 0
        lines = []
        for wd in words:
            t = (line + ' ' + wd).strip()
            if self.mwidth(t) > w and line:
                lines.append(line)
                line = wd
            else:
                line = t
        if line:
            lines.append(line)
        for i, l in enumerate(lines[:maxl]):
            self.mtext(l, x, y + i * 10, color)
        return min(len(lines), maxl)

    def nlines(self, s, w):
        words, line, n = s.split(' '), '', 1
        for wd in words:
            t = (line + ' ' + wd).strip()
            if self.mwidth(t) > w and line:
                n += 1
                line = wd
            else:
                line = t
        return n

    # PadUi.BTN: cara arriba, cara abajo, luz, sombra
    BTN = [(0x5AB4FF, 0x2C74E4, 0xB4E0FF, 0x1A4AA8), (0xFFD650, 0xE8961A, 0xFFF2B0, 0xA05E0E),
           (0xFF6274, 0xC82038, 0xFFB4BC, 0x861428), (0x68E886, 0x22A84C, 0xC4FFD0, 0x147034),
           (0x4A5884, 0x36426C, 0x6E7CA8, 0x252E50), (0x97A3BF, 0x66728F, 0xC9D0E2, 0x434D69)]

    @staticmethod
    def is_back(label):
        return label.startswith('ATR') or label == 'VOLVER'

    def bw(self, label):
        return max(36, self.pwidth(label) + 14 + (8 if self.is_back(label) else 0))

    def button(self, x, y, w, label, style, enabled=True, hover=False):
        c = self.BTN[4 if not enabled else 5 if style == 4 else min(style, 3)]
        top, bot = rgb(c[0]), rgb(c[1])
        if hover and enabled:
            top, bot = lighten(top, 30), lighten(bot, 30)
        self.box(x, y, w, 16, INK)
        self.vgrad(x + 1, y + 1, w - 2, 13, top, bot)
        self.fill(x + 2, y + 1, x + w - 2, y + 2, rgb(c[2]))
        self.fill(x + 1, y + 13, x + w - 1, y + 15, rgb(c[3]))
        col = (255, 255, 255) if enabled else rgb(0x8C9AC4)
        if self.is_back(label):
            ax, ay = x + 6, y + 7
            for i in range(4):
                self.fill(ax + i, ay - i, ax + i + 1, ay + i + 1, col)
            self.fill(ax + 1, ay, ax + 7, ay + 1, col)
            self.ptext(label, x + w // 2 + 4 - self.pwidth(label) // 2, y + 2, col)
        else:
            self.ptext(label, x + w // 2 - self.pwidth(label) // 2, y + 2, col)

    def progress(self, x, y, w, f):
        """PadUi.progress (1.3.28): barra redondeada de 6 con su hueco hundido."""
        self.box(x, y, w, 6, rgb(0x9DB8D8))
        self.fill(x + 1, y + 1, x + w - 1, y + 5, rgb(0xDCE8F4))
        self.fill(x + 1, y + 1, x + w - 1, y + 2, rgb(0xC6D8EC))
        fw = round((w - 2) * min(1, f))
        if fw > 0:
            full = f >= 1
            self.vgrad(x + 1, y + 1, fw, 4, rgb(0xFFE070 if full else 0x8CF0A8), rgb(0xE08E14 if full else 0x1E9E46))
            self.fill(x + 1, y + 1, x + 1 + fw, y + 2, rgb(0xFFF6C0 if full else 0xD8FFE2))

    def item(self, name, x, y, scale=1):
        if name in ('player_head', 'player_head_alex'):
            # la cabeza de un jugador (en el juego, en 3D): su cara con la capa del pelo, en plano
            skin = Image.open(f"{MC}/entity/player/wide/{'alex' if name.endswith('alex') else 'steve'}.png").convert('RGBA')
            face = skin.crop((8, 8, 16, 16))
            face.alpha_composite(skin.crop((40, 8, 48, 16)))
            im = Image.new('RGBA', (16, 16))
            im.paste(face.resize((14, 14), Image.NEAREST), (1, 1))
            if scale != 1:
                im = im.resize((16 * scale, 16 * scale), Image.NEAREST)
            self.paste(im, x, y)
            return
        if name.startswith('tfb:'):
            p = os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/block', name[4:] + '.png')
            im = Image.open(p).convert('RGBA').resize((16, 16), Image.NEAREST)
        else:
            p = None
            if name.startswith('tf:'):
                p = os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/item', name[3:] + '.png')
                if not os.path.exists(p):
                    p = None
            if p is None:
                p = f'{MC}/item/{name}.png'
                if not os.path.exists(p):
                    p = f'{MC}/block/{name}.png'
                if not os.path.exists(p):
                    p = f'{MC}/item/paper.png'
            im = Image.open(p).convert('RGBA')
            im = im.crop((0, 0, im.width, im.width)).resize((16, 16), Image.NEAREST)
        if scale != 1:
            im = im.resize((16 * scale, 16 * scale), Image.NEAREST)
        self.paste(im, x, y)

    @staticmethod
    def count_label(n, big):
        """PadView.countLabel: la cantidad de verdad; exacta (con puntos de miles) hasta 9.999 al doble y 999 a tamaño
        normal; más, corta como PadView.shortNumber (1,8K, 35K, 1,5M; hacia abajo)."""
        if n < (10_000 if big else 1000):
            return f'{n:,}'.replace(',', '.')
        def dec(d):
            t = f'{d:.1f}'
            return (t[:-2] if t.endswith('.0') else t).replace('.', ',')
        if n < 10_000:
            return dec(math.floor(n / 100) / 10) + 'K'
        if n < 1_000_000:
            return f'{n // 1000}K'
        return dec(math.floor(n / 100_000) / 10) + 'M'

    def count(self, n, x, y, k=1):
        """PadUi.item (1.3.28): la cantidad en blanco sobre una etiqueta azul noche, sin sombra. Es la de verdad
        (la tienda manda 128 o 1.024 aunque el objeto se apile de 64): PadView.countLabel."""
        t = self.count_label(n, k > 1)
        w = self.mwidth(t) + 2
        rx, ry = x + 16 * k + 1 - w, y + 16 * k - 8
        self.box(rx, ry, w + 1, 9, rgb(0x22346E))
        self.mtext(t, rx + 1, ry + 1, (255, 255, 255))

    # -------------------------------------------------------------------------------------------------------------
    def status(self, title, back=True, title_color=(255, 255, 255), music=False, greens=True):
        """TFPadScreen.drawStatus (1.3.25): todo a la escala grande."""
        bsz = self.bigu(12)
        bx, by = self.logo_right(0) + 4, (self.bar - bsz) // 2
        if back:
            with self.big_at(bx, by):
                self.box(0, 0, 12, 12, INK)
                self.vgrad(1, 1, 10, 9, rgb(0x5AB4FF), rgb(0x2C74E4))
                self.fill(2, 1, 10, 2, rgb(0xB4E0FF))
                self.fill(1, 9, 11, 11, rgb(0x1A4AA8))
                self.fill(3, 5, 4, 6, (255, 255, 255))
                self.fill(4, 4, 5, 7, (255, 255, 255))
                self.fill(5, 3, 6, 8, (255, 255, 255))
                self.fill(6, 5, 9, 6, (255, 255, 255))
        ty = self.bar / 2 - 5.5 * self.bs
        with self.big_at(self.UW / 2, ty):
            self.ptext(title, -self.pwidth(title) // 2, 0, title_color)
        right = self.UW - 4 - bsz
        with self.big_at(right + bsz / 2 - 5 * self.bs, self.bar / 2 - 5 * self.bs):
            self.blit('gear', 0, 0)
        right -= self.bigu(8)
        for text, icon, color in (('1.250', 'coin', (255, 230, 128)), ('7', 'coin_green', (140, 240, 180)), ('18:30', 'sun', (255, 255, 255))):
            if icon == 'coin_green' and not greens:
                continue
            w = self.bigu(self.pwidth(text))
            right -= w
            with self.big_at(right, ty + self.bs):
                self.ptext(text, 0, 0, color)
            right -= self.bigu(14)
            with self.big_at(right, (self.bar - self.bigu(11)) / 2):
                self.blit(icon, 0, 0)
            right -= self.bigu(8)
        if music:
            right -= self.bigu(11)
            for i, lv in enumerate((0.7, 1.0, 0.5, 0.8)):
                h = max(self.bigu(2), round(self.bigu(10) * lv))
                x = right + i * self.bigu(3)
                y1 = self.bar // 2 + self.bigu(5)
                self.fill(x - 1, y1 - h - 1, x + self.bigu(2) + 1, y1 + 1, NAVY)
                self.fill(x, y1 - h, x + self.bigu(2), y1, rgb(0x7CF0B0))

    def home_layout(self, apps):
        """PadHomePage.layout (1.3.38): rejilla simétrica. Fichas iguales a la escala grande (o a una escala entera
        menor si así no caben las filas), el mismo hueco a lo ancho y a lo alto, filas repartidas (23 → 8+8+7),
        centrada en el cristal. Devuelve (ts, tile, cols, x0, top, pitchX, pitchY, gap, contentH)."""
        Y, H, SW = self.Y, self.H, self.UW
        LABEL_H, MIN_GAP = 11, 4
        n = len(apps)
        max_label = max(self.pwidth(name) for _, name in apps)
        room = SW - 8
        grid = None
        for k in range(self.big, max(1, self.cs) - 1, -1):
            ts = k / self.cs
            t = round(40 * ts)
            min_gap = max(round(8 * ts), max_label - t + 6)
            max_cols = max(1, min(9, (room + min_gap) // (t + min_gap)))
            rows = max(1, -(-n // max_cols))
            cols = max(1, -(-n // rows))
            cell_h = t + LABEL_H
            gap_v = (H - rows * cell_h) // (rows + 1)
            gap_h = (room - cols * t) // (cols + 1)
            fits = gap_v >= MIN_GAP
            gap_x = max(min_gap, min(gap_h, round(26 * ts)))
            gap_y = min(gap_x, gap_v) if fits else MIN_GAP
            gap_x = max(min_gap, min(gap_x, gap_y))
            grid_w = cols * t + (cols - 1) * gap_x
            content_h = rows * cell_h + (rows - 1) * gap_y
            x0 = (SW - grid_w) // 2
            top = Y + (H - content_h) // 2 if fits else Y + 2
            while self.logo_right(top) + 2 > x0 and top + content_h < Y + H:
                top += 1
            grid = (ts, t, cols, x0, top, t + gap_x, cell_h + gap_y, gap_x, content_h + 4)
            if fits:
                break
        return grid

    @staticmethod
    def _wave(i, cols):
        return (i % cols) * 0.07 + (i // cols) * 0.11

    # PadHomePage.CLOUDS y MOTES
    CLOUDS = [(0.20, 230, 62, 3.2, 0.10, 0.20), (0.58, 260, 70, 2.4, 0.55, 0.17), (0.88, 210, 56, 3.8, 0.30, 0.19),
              (0.38, 330, 92, -5.5, 0.80, 0.14), (0.74, 300, 84, -6.5, 0.15, 0.13)]
    MOTES = [(0.05, 9, 6, 0.1, 1), (0.13, 13, 4, 0.7, 0), (0.21, 8, 7, 0.4, 2), (0.29, 11, 5, 0.9, 0), (0.36, 7, 6, 0.2, 1),
             (0.44, 12, 4, 0.6, 0), (0.52, 9, 8, 0.3, 2), (0.60, 14, 5, 0.8, 0), (0.67, 8, 6, 0.5, 1), (0.75, 11, 7, 0.05, 0),
             (0.83, 10, 4, 0.65, 2), (0.90, 13, 6, 0.35, 0), (0.96, 8, 5, 0.85, 1), (0.10, 10, 5, 0.55, 0), (0.48, 15, 6, 0.15, 0),
             (0.71, 9, 7, 0.95, 0)]

    def home_ambient(self, apps, t, fade=1.0):
        """PadHomePage.ambient (1.4.1): rayos de sol desde arriba, la luz que respira detrás de la rejilla, nubes en dos
        capas (lejos a la derecha, cerca a la izquierda) y motas de luz que suben. Va dentro del fondo, antes del logo."""
        w, h = self.UW, self.UH
        self.rays(w / 2, -h * 0.18, h * 1.25, 0.10 * fade, t * 2.2)
        ts, tile, cols, x0, top, px, py, gap, content_h = self.home_layout(apps)
        gw, gh = cols * tile + (cols - 1) * gap, content_h
        pulse = 0.5 + 0.5 * math.sin(t * 1.25)
        cy = top + gh / 2
        self.tex_alpha('bokeh', w / 2 - gw * 0.68, cy - gh * 0.78, gw * 1.36, gh * 1.56, (0.22 + 0.12 * pulse) * fade, rgb(0xDDF4FF))
        for (cyf, cw, ch, v, ph, a) in self.CLOUDS:
            span = w + cw
            x = ((ph * span + v * t) % span) - cw
            y = cyf * h - ch / 2 + 3 * math.sin(t * 0.35 + ph * 6)
            self.tex_alpha('bokeh', x, y, cw, ch, a * fade)
            self.tex_alpha('bokeh', x + cw * 0.28, y - ch * 0.22, cw * 0.5, ch * 0.8, a * 0.8 * fade)
        for i, (mx, v, amp, ph, sz) in enumerate(self.MOTES):
            span = h + 20
            y = h + 10 - ((ph * span + v * t) % span)
            x = mx * w + amp * math.sin(t * 0.9 + i * 1.7)
            life = max(0, min(1, (h + 10 - y) / 40)) * max(0, min(1, y / 50))
            a = life * (0.45 + 0.55 * max(0.0, math.sin(t * 2.1 + i * 0.9 * 2.39)) ** 2) * fade
            if sz:
                self.sparkle(round(x), round(y), int(sz), a * 0.9, rgb(0xFFF6D8))
            else:
                self.put(round(x), round(y), (255, 255, 255, round(200 * a)))

    def home(self, apps, hover=None, glint=None, since=10.0, hover_t=0.3):
        """PadHomePage (1.4.1): la rejilla, con las fichas entrando en cascada (since: segundos desde que se vio la
        portada), el brillo que las cruza en ola cada 9 s y la del ratón elevada con su halo."""
        Y, H, SW = self.Y, self.H, self.UW
        n = len(apps)
        ts, t, cols, x0, top, px, py, gap, content_h = self.home_layout(apps)
        self.home_grid = (ts, t, cols, x0, top, px, py)
        self.scissor(2, Y, SW - 4, H)
        for i, (icon, name) in enumerate(apps):
            row, col = divmod(i, cols)
            in_row = min(cols, n - row * cols)
            tx = x0 + (cols - in_row) * px // 2 + col * px
            ty = top + row * py
            wv = self._wave(i, cols)
            k_in = max(0.0, min(1.0, (since - 0.06 - wv * 0.55) / 0.34))
            if k_in <= 0:
                continue
            hv = icon == hover
            lift = -round(ts * 2) if hv else 0
            if k_in < 1:  # la entrada: crece con rebote desde su centro (en el sim, se dibuja aparte y se escala)
                c1, kk = 1.5, k_in - 1
                sc = 1 + (c1 + 1) * kk ** 3 + c1 * kk ** 2
                self._tile_scaled(icon, name, tx, ty + (1 - k_in) * 6 * ts, t, ts, sc)
                continue
            if hv:  # 1.4: halo dorado detrás de la ficha del ratón
                self.glow(tx + t / 2, ty + lift + t / 2, t * 0.95, (255, 230, 140), 150)
            if lift:
                self.fill(tx + round(3 * ts), ty + t, tx + t - round(3 * ts), ty + t + max(1, round(ts)), (24, 38, 92, 0x55))
            with self.big_at(tx, ty + lift, px=ts * self.cs):
                self.blit('tile_h' if hv else 'tile', 0, 0)
                self.icon32(icon, 4, 4)
            face = round(4 * ts)
            k = (hover_t % 2.4) / 0.7 if hv else ((self.T - wv + 90) % 9) / 0.8
            if 0 <= k < 1:
                self.sheen_at(tx + face, ty + lift + face, t - 2 * face, t - 2 * face, k, 0.32 if hv else 0.22)
            self.ptext(name, tx + t // 2 - self.pwidth(name) // 2, ty + t + 2, (255, 230, 128) if hv else (255, 255, 255))
        self.no_scissor()

    def _tile_scaled(self, icon, name, tx, ty, t, ts, sc):
        """Una ficha (con su nombre) dibujada en un lienzo aparte y escalada sc desde el centro de la ficha."""
        if sc <= 0.02:
            return
        f = self.f
        cw, chh = int((t + 60) * f), int((t + 14) * f)
        sub = Sim.__new__(Sim)
        sub.__dict__.update(self.__dict__)
        sub.art = Image.new('RGBA', (cw, chh), (0, 0, 0, 0))
        sub.o, sub.clip = (0, 0), None
        ox = 30
        with sub.big_at(ox, 0, px=ts * self.cs):
            sub.blit('tile', 0, 0)
            sub.icon32(icon, 4, 4)
        sub.ptext(name, ox + t // 2 - self.pwidth(name) // 2, t + 2, (255, 255, 255))
        im = sub.art.resize((max(1, round(cw * sc)), max(1, round(chh * sc))), Image.NEAREST)
        # el centro de la ficha queda en su sitio
        cx, cy = (tx + t / 2), (ty + t / 2)
        rx, ry = self._rect(cx, cy, cx, cy)[:2]
        self._paste_px(im, round(rx - (ox + t / 2) * f * sc), round(ry - (t / 2) * f * sc))

    def fitend(self, s, w):
        if self.mwidth(s) <= w:
            return s
        while s and self.mwidth(s + '...') > w:
            s = s[:-1]
        return s + '...'

    def pfit(self, s, w):
        if self.pwidth(s) <= w:
            return s
        while s and self.pwidth(s + '.') > w:
            s = s[:-1]
        return s + '.'

    def field(self, x, y, w, text, hint):
        self.box(x, y, w, 16, INK)
        self.fill(x + 1, y + 1, x + w - 1, y + 15, (255, 255, 255))
        self.fill(x + 1, y + 1, x + w - 1, y + 2, rgb(0xD6E6F6))
        self.fill(x + 1, y + 2, x + 2, y + 15, rgb(0xE6EFF8))
        shown = self.fitend(text or hint, w - 10)
        self.mtext(shown, x + 5, y + 4, TEXT if text else rgb(0x96AACC))
        cx = x + 5 + (self.mwidth(shown) if text else 0)
        self.fill(cx, y + 3, cx + 1, y + 13, TEXT)

    def wrap_ellipsis(self, s, x, y, w, color, maxl):
        words = s.split(' ')
        lines, line = [], ''
        for wd in words:
            t = (line + ' ' + wd).strip()
            if self.mwidth(t) > w and line:
                lines.append(line)
                line = wd
            else:
                line = t
        if line:
            lines.append(line)
        if len(lines) > maxl:
            rest = ' '.join(lines[maxl - 1:])
            lines = lines[:maxl - 1] + [self.fitend(rest + ' ', w) if self.mwidth(rest) > w else rest]
        for i, l in enumerate(lines):
            self.mtext(l, x, y + i * 10, color)
        return len(lines)

    # PadViewPage (1.4): medidas
    CELL_W, CELL_H, CARD_MIN, CARD_MAX, CARD_H, CARD_BAR, ICON_BOX, ROW_GAP, HERO_MIN, HERO_SLOT, SHOW, DOCK = (
        30, 32, 76, 116, 78, 8, 26, 4, 52, 40, 40, 22)
    # tarjetas grandes (galería: las imágenes de los efectos)
    GAL_MIN, GAL_MAX, GAL_H, GAL_SHOW, GAL_IMG = 88, 124, 100, 60, 50

    @staticmethod
    def _ex(t):
        """Lo opcional de una fila o tarjeta: el diccionario del final (img, smooth, prog, badge, tone, off, sel)."""
        return t[-1] if t and isinstance(t[-1], dict) else {}

    def view(self, tabs, sel, header, rows, footer=None, input_=None, empty='', cells=None, cards=None, hover=None, hero=None,
             app='', gallery=None, hover_row=None, waiting=False, tab_hover=None):
        """PadViewPage.render (1.4): pestañas de carpeta encima (la elegida, del color de la app, se une a la cabecera);
        dentro, la cabecera de la app (su color, su icono y el texto del servidor o su frase), la ficha grande, la lista
        (casillas, tarjetas o galería y filas) y abajo la barra de acciones."""
        X, Y, W, H = self.X, self.Y, self.W, self.H
        acc = accent(app)
        mode = 'gallery' if gallery is not None else 'cards' if cards is not None else 'cells'
        cells = gallery if gallery is not None else cards if cards is not None else (cells or [])
        top = Y + (self.TABS if tabs else 0)
        self.sk_window(X, top, W, Y + H - top, acc)
        y = top + 1
        show_banner = bool(header) or not hero
        if show_banner:
            room = 1 if tabs else 2
            lines = []
            for l in (header or [tagline(app)]):
                lines += self.split(l, W - 2 - 46)
            if len(lines) > room:
                lines = lines[:room]
                lines[-1] = self.fitend(lines[-1] + '...', W - 2 - 46)
            bh = self.banner_h(lines)
            self.sk_banner(X + 1, y, W - 2, bh, acc, icon_of(app), lines)
            y += bh + 1
        elif tabs:
            self.vgrad(X + 1, y, W - 2, 4, mix(acc, WHITE, 0.14), darken(acc, 0.78))
            y += 4
        if tabs:
            keys = [k for k, _ in tabs]
            self.sk_tabs(X + 4, Y, W - 8, [l for _, l in tabs], keys.index(sel) if sel in keys else -1, acc,
                         keys.index(tab_hover) if tab_hover in keys else -1)
        if waiting:
            self.spinner_w(X + W - 10, top + 3)
        y += 3
        bottom = bool(footer) or input_ is not None
        dockh = self.DOCK if bottom else 0
        dock_y = Y + H - 1 - dockh
        bot = dock_y - (3 if bottom else 2)
        alone = hero and not (cells or rows or empty)
        if hero:
            hh = self.hero_h(hero, W - 10)
            hy = y + max(0, (bot - y - hh) // 2) if alone else y
            self.hero(hero, X + 5, hy, W - 10, hh, acc)
            y += hh + 5
        visible = bot - y
        if not alone:
            ix, iw = X + 5, W - 15
            n = len(cells)
            if mode != 'cells':
                cmin, cmax = (self.GAL_MIN, self.GAL_MAX) if mode == 'gallery' else (self.CARD_MIN, self.CARD_MAX)
                cols = max(1, iw // cmin)
                if 0 < n < cols:
                    cols = n
                    cw = min(cmax, iw // cols)
                else:
                    cw = iw // cols
                bar = any(self._ex(c).get('prog', -1) >= 0 for c in cells)
                ch = (self.GAL_H if mode == 'gallery' else self.CARD_H) + (self.CARD_BAR if bar else 0)
                gx = ix + (iw - cols * cw) // 2
            else:
                cw, ch = self.CELL_W, self.CELL_H
                cols = max(1, iw // cw)
                gx = ix + (iw - max(1, min(cols, n)) * cw) // 2
            grows = (n + cols - 1) // cols
            gridh = grows * ch + (3 if grows else 0)
            content = gridh + sum(self.row_h(r, iw) for r in rows)
            self.scissor(X + 1, y, W - 2, visible)
            ry = y + 2
            if mode != 'cells' and not rows and content < visible:
                ry += (visible - content) // 2
            for i, c in enumerate(cells):
                cx, cy = gx + (i % cols) * cw, ry + 1 + (i // cols) * ch
                if mode != 'cells':
                    self.card(c, cx, cy, cw, ch, hover == i, acc, mode == 'gallery')
                else:
                    self.cell(c, cx, cy, hover == i, acc)
            ry += gridh
            for i, r in enumerate(rows):
                h = self.row_h(r, iw)
                self.row(r, ix, ry, iw, h, acc, hover_row == i)
                ry += h
            self.no_scissor()
            if not rows and not cells:
                self.sk_empty(X + W // 2, (y + bot) // 2, W - 40, empty, acc, icon_of(app), bot - y)
            self.sk_scrollbar(X + W - 8, y + 1, visible - 2, visible, content, 0, acc)
        if bottom:
            self.sk_dock(X + 1, dock_y, W - 2, dockh, acc)
            fy = dock_y + (dockh - 16) // 2
            footer = footer or []
            total = sum(self.bw(l) + 4 for l, *_ in footer)
            x = X + W - 5 - total + 4
            for b in footer:
                self.sk_button(x, fy, self.bw(b[0]), b[0], b[1], b[2] if len(b) > 2 else True)
                x += self.bw(b[0]) + 4
            if input_:
                hint, label, typed = input_
                bw = self.bw(label)
                right = X + W - 5 - total
                fw = right - (X + 5) - bw - 4
                self.field(X + 5, fy, fw, typed, hint)
                self.sk_button(right - bw, fy, bw, label, 3, bool(typed))

    def spinner_w(self, cx, y):
        """PadUi.spinner en blanco (va sobre la cabecera de color)."""
        for i, (dx, dy) in enumerate(((0, -3), (3, 0), (0, 3), (-3, 0))):
            c = (255, 255, 255) if i == 1 else (255, 255, 255, 110)
            self.fill(cx + dx - 1, y + 4 + dy - 1, cx + dx + 1, y + 4 + dy + 1, c)

    def diamond(self, cx, cy):
        self.fill(cx - 2, cy, cx + 3, cy + 1, rgb(0xC27A10))
        self.fill(cx - 1, cy - 1, cx + 2, cy + 2, rgb(0xF6B628))
        self.fill(cx, cy - 2, cx + 1, cy + 3, rgb(0xC27A10))
        self.fill(cx, cy - 1, cx + 1, cy, rgb(0xFFEC96))

    def pill(self, x, y, text, style, h):
        return self.chip(x, y, text, {3: 'green', 4: 'gray'}.get(style, 'gold'), h)

    def icon_at(self, icon, ex, x, y, size, n=1):
        """El icono de una fila, tarjeta o ficha: la imagen (PadView image) si la hay; si no, el objeto (a 1x o 2x)."""
        if ex.get('img'):
            self.image(ex['img'], x, y, size, ex.get('smooth', True))
        elif icon:
            k = 2 if size >= 32 else 1
            off = (size - 16 * k) // 2
            self.item(icon, x + off, y + off, k)
            if n > 1:
                self.count(n, x + off, y + off, k)

    def cell(self, c, x, y, hover=False, acc=(52, 150, 250)):
        """Casilla pequeña (1.4): tarjetita con el objeto y su cantidad; al pasar el ratón sube y se ilumina."""
        icon, label, color, sel = c[:4]
        n = c[4] if len(c) > 4 and isinstance(c[4], int) else 1
        cw, ch = self.CELL_W, self.CELL_H
        yy = self.sk_card(x + 1, y, cw - 2, ch - 1, 2 if sel else 1 if hover else 0, acc)
        if icon:
            iy = yy + 4 if label else yy + (ch - 1 - 16) // 2
            self.item(icon, x + (cw - 16) // 2, iy)
            if n > 1:
                self.count(n, x + (cw - 16) // 2, iy)
        if label:
            label = self.pfit(label, cw - 4)
            self.ptext(label, x + cw // 2 - self.pwidth(label) // 2, yy + 20, on_dark(color), False)

    def card(self, c, x, y, cw, ch, hover=False, acc=(52, 150, 250), gallery=False):
        """PadViewPage.drawCard (1.4): tarjeta con vitrina del color de la tarjeta (rayas, luz, franja de color abajo),
        el objeto o la imagen grande, el nombre en negrita y su etiqueta; la marcada, con borde de oro, rayos que giran
        y una cinta con la marca; al pasar el ratón sube, se ilumina y la cruza un brillo."""
        icon, name, color, sub, sel = c[:5]
        n = c[5] if len(c) > 5 and isinstance(c[5], int) else 1
        ex = self._ex(c)
        on = not ex.get('off')
        col = on_dark(color)
        w, h = cw - 4, ch - 4
        x0 = x + 2
        state = 2 if sel else 1 if hover else 0
        if sel:  # aura de oro que late
            a = 0.55 + 0.45 * math.sin(self.T * 3.2)
            self.box(x0 - 1, y - 1, w + 2, h + 2, with_a(GOLD_HI, 90 + 90 * a))
        yy = self.sk_card(x0, y, w, h, state, acc, edge=tint(col, 0.45))
        sh = self.GAL_SHOW if gallery else self.SHOW
        cx = x0 + w // 2
        self.vgrad(x0 + 1, yy + 1, w - 2, sh, tint(col, 0.62), tint(col, 0.22))
        self.tiled('stripes', x0 + 1, yy + 1, w - 2, sh, 0.10, off=self.T * 3)
        clip = self.clip
        self.scissor(x0 + 1, yy + 1, w - 2, sh)
        if sel or hover:
            self.rays(cx, yy + 1 + sh / 2, sh * 0.95, 0.55 if sel else 0.35, self.T * 25)
        self.glow(cx, yy + 1 + sh / 2, sh * 0.62, WHITE, 165)
        self.clip = clip
        self.stretch('floor', cx - 14, yy + sh - 6, 28, 5)
        self.fill(x0 + 1, yy + 1 + sh, x0 + w - 1, yy + 2 + sh, col)
        self.fill(x0 + 1, yy + 2 + sh, x0 + w - 1, yy + 3 + sh, tint(col, 0.35))
        self.fill(x0 + 2, yy + 1, x0 + w - 2, yy + 2, (255, 255, 255, 150))
        bob = round(math.sin(self.T * 4.4)) if hover or sel else 0
        size = self.GAL_IMG if gallery else 32
        if ex.get('img'):
            self.icon_at(icon, ex, cx - size // 2, yy + 1 + (sh - size) // 2 + bob, size)
        else:
            self.icon_at(icon, ex, cx - 16, yy + 4 + bob + (sh - 40) // 2, 32, n)
        if hover:
            self.sheen(x0 + 1, yy + 1, w - 2, sh, period=1.6, alpha=0.3)
        self.title(name, cx - self.title_w(name, w - 6) // 2, yy + sh + 7, w - 6, TEXT)
        if sub:
            style = self.tone(sub, on or sel, ex.get('tone', 0))
            if self.chip_w(sub) > w - 6:
                plus, coin, rest = self.money_parts(sub)
                head = '+¤' if plus else '¤' if coin else ''
                room = w - 6 - (self.chip_w(head + 'x') - self.mwidth('x'))
                sub = head + self.fitend(rest, max(6, room))
            self.sk_chip(cx - self.chip_w(sub) // 2, yy + sh + 18, sub, style)
        if ex.get('prog', -1) >= 0:
            pct = f"{round(min(1, ex['prog']) * 100)}%"
            pw = self.mwidth(pct)
            bw = w - 12 - pw - 4
            self.sk_progress(x0 + 6, yy + sh + 34, bw, ex['prog'])
            self.mtext(pct, x0 + 6 + bw + 4, yy + sh + 33, MUTED)
        if ex.get('badge'):
            bw = self.chip_w(ex['badge'])
            self.sk_chip(x0 + w - bw - 3, yy + 4, ex['badge'], 'green', 11)
        if sel:  # la cinta de oro con la marca, arriba a la izquierda
            self.ribbon(x0 + 1, yy + 1)

    def ribbon(self, x, y):
        """PadSkin.ribbon: esquina de oro (triángulo de 11) con una marca blanca."""
        for i in range(11):
            self.fill(x, y + i, x + 11 - i, y + i + 1, GOLD if i < 10 else GOLD_LO)
        self.fill(x, y, x + 11, y + 1, GOLD_HI)
        for px, py in ((5, 1), (4, 2), (5, 2), (1, 3), (3, 3), (4, 3), (1, 4), (2, 4), (3, 4), (2, 5)):
            self.fill(x + px, y + py, x + px + 1, y + py + 1, (255, 255, 255))

    def hero_right(self, r):
        badge, b1, b2 = r[5], r[6], r[7]
        w = self.chip_w(badge) + 6 if badge else 0
        return w + sum(self.bw(b[0]) + 4 for b in (b1, b2) if b)

    def hero_tw(self, r, w):
        return w - (self.HERO_SLOT + 20) - self.hero_right(r) - 6

    def hero_h(self, r, w):
        h = 8 + 11 + self.lines_h(r, self.hero_tw(r, w)) + (10 if r[4] >= 0 else 0) + 6
        return max(self.HERO_MIN, h)

    def hero(self, r, x, y, w, h, acc=(52, 150, 250)):
        """PadViewPage.drawHero (1.4): la ficha grande. Banner del color de la ficha con rayas, rayos de luz que giran
        detrás del medallón, el objeto o la imagen grande, el título, las líneas, la barra, la etiqueta y los botones;
        un brillo la cruza de vez en cuando."""
        icon, title, color, lines, prog, badge, b1, b2 = r[:8]
        ex = self._ex(r)
        col = deep(on_dark(color))
        self.fill(x + 1, y + h, x + w - 1, y + h + 1, (24, 38, 92, 80))
        self.fill(x + 3, y + h + 1, x + w - 3, y + h + 2, (24, 38, 92, 30))
        self.box(x, y, w, h, darken(col, 0.45))
        self.vgrad(x + 1, y + 1, w - 2, h - 2, mix(col, WHITE, 0.16), darken(col, 0.74))
        self.tiled('stripes', x + 1, y + 1, w - 2, h - 2, 0.08, off=self.T * 3)
        ms = self.HERO_SLOT
        mx, my = x + 7, y + (h - ms) // 2
        clip = self.clip
        self.scissor(x + 1, y + 1, w - 2, h - 2)
        self.rays(mx + ms / 2, my + ms / 2, h * 1.15, 0.5, self.T * 18)
        self.glow(mx + ms / 2, my + ms / 2, h * 0.8, WHITE, 90)
        self.glow(x + w - 30, y + h / 2, 70, WHITE, 55)
        self.clip = clip
        self.fill(x + 1, y + 1, x + w - 1, y + 2, _lighten(col, 70))
        self.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, darken(col, 0.55))
        self.sk_slot(mx, my, ms, col)
        self.stretch('floor', mx + 8, my + ms - 7, ms - 16, 4)
        n = r[8] if len(r) > 8 and isinstance(r[8], int) else 1
        if ex.get('img'):
            self.icon_at(icon, ex, mx + 3, my + 3, ms - 6)
        else:
            self.icon_at(icon, ex, mx + (ms - 32) // 2, my + (ms - 32) // 2 - 1, 32, n)
        tx, tw = x + ms + 15, self.hero_tw(r, w)
        th = 11 + self.lines_h(r, tw) + (10 if prog >= 0 else 0)
        top = y + max(6, (h - th) // 2 + 1)
        shade = darken(col, 0.38)
        self.btext(self.bfit(title, tw), tx + 1, top + 1, shade)
        self.btext(self.bfit(title, tw), tx, top, WHITE)
        ly = top + 12
        soft = mix(WHITE, col, 0.10)
        for l in lines:
            n2 = self.wrap_ellipsis(l, tx + 1, ly + 1, tw, shade, 2)
            self.wrap_ellipsis(l, tx, ly, tw, soft, 2)
            ly += max(1, n2) * 10
        if prog >= 0:
            pct = f"{round(min(1, prog) * 100)}%"
            bw = min(tw - self.mwidth(pct) - 4, 160)
            self.sk_progress(tx, ly + 1, bw, prog)
            self.stext(pct, tx + bw + 4, ly, WHITE, shade)
        bx = x + w - self.hero_right(r) - 3
        if badge:
            self.sk_chip(bx, y + (h - 14) // 2, badge, self.tone(badge), 14)
            bx += self.chip_w(badge) + 6
        by = y + (h - 16) // 2
        for b in (b1, b2):
            if b:
                self.sk_button(bx, by, self.bw(b[0]), b[0], b[1], b[2] if len(b) > 2 else True)
                bx += self.bw(b[0]) + 4
        for i, (sx, sy) in enumerate(((w - 14, 6), (w - 50, h - 7), (ms + 10, 5))):
            self.sparkle(x + sx, y + sy, 2, self.twinkle(i * 1.9 + 1.1) * 0.9)
        self.sheen(x + 1, y + 1, w - 2, h - 2, period=7.0, alpha=0.2)

    def lines_h(self, r, tw):
        return sum(min(2, max(1, self.nlines(l, tw))) for l in r[3]) * 10

    def row_h(self, r, w):
        icon, title, color, lines, prog, badge, b1, b2 = r[:8]
        ex = self._ex(r)
        text_only = not icon and not ex.get('img') and not b1 and not b2
        if text_only and not lines and title:
            return 17
        if text_only:
            n = sum(max(1, self.nlines(l, w - 18)) for l in lines)
            return 8 + (12 if title else 0) + n * 10 + 3 + self.ROW_GAP
        tw = self.text_w(r, w)
        h = 4 + 11 + self.lines_h(r, tw) + (8 if prog >= 0 else 0) + 3
        return max(self.ICON_BOX + 10 if (icon or ex.get('img')) else 26, h + 2) + self.ROW_GAP

    def right_w(self, r):
        badge, b1, b2 = r[5], r[6], r[7]
        return (self.chip_w(badge) + 6 if badge else 0) + sum(self.bw(b[0]) + 4 for b in (b1, b2) if b)

    def text_w(self, r, w):
        has_icon = r[0] or self._ex(r).get('img')
        return w - (self.ICON_BOX + 17 if has_icon else 14) - self.right_w(r) - 6

    def row(self, r, x, y, w, h, acc=(52, 150, 250), hover=False):
        """PadViewPage.drawRow (1.4): tarjeta con una franja del color de la fila (o de la app) a la izquierda, el icono
        en su medallón con luz, el título en negrita, las líneas, la barra y a la derecha la etiqueta y los botones."""
        icon, title, color, lines, prog, badge, b1, b2 = r[:8]
        ex = self._ex(r)
        sel = (len(r) > 8 and r[8] is True) or ex.get('sel')
        h -= self.ROW_GAP
        has_icon = bool(icon or ex.get('img'))
        text_only = not has_icon and not b1 and not b2
        plain = color in (0x18265C, 0x7E8CA8)
        tcol = on_dark(color)
        if text_only and not lines and title:
            self.sk_section(x + 2, y + 4, w - 4, title, acc if plain else tcol)
            return
        state = 2 if sel else 1 if hover else 0
        yy = self.sk_card(x, y, w, h, state, acc)
        spine = GOLD if sel else acc if plain else tcol
        if color == 0x7E8CA8:
            spine = rgb(0xAAB8CC)
        self.vgrad(x + 1, yy + 1, 3, h - 2, lighten(spine, 40), spine)
        if text_only:
            ty = yy + 5
            if title:
                self.title(title, x + 9, ty, w - 16, darken(acc, 0.75) if plain else ink(tcol))
                ty += 12
            for l in lines:
                ty += max(1, self.wrap(l, x + 9, ty, w - 16, MUTED)) * 10
            return
        tx = x + 10
        if has_icon:
            ib = self.ICON_BOX
            iy = yy + (h - ib) // 2
            self.sk_slot(x + 7, iy, ib, None if plain else tcol)
            bob = round(math.sin(self.T * 4.4)) if hover else 0
            if ex.get('img'):
                self.icon_at(icon, ex, x + 9, iy + 2 + bob, ib - 4)
            else:
                self.icon_at(icon, ex, x + 7 + (ib - 16) // 2, iy + (ib - 16) // 2 + bob, 16)
            tx = x + ib + 14
        tw = self.text_w(r, w)
        th = 11 + self.lines_h(r, tw) + (8 if prog >= 0 else 0)
        top = yy + 5 if not has_icon else yy + max(5, (h - th) // 2 + 1)
        self.title(title, tx, top, tw, ink(tcol))
        ly = top + 11
        for l in lines:
            ly += max(1, self.wrap_ellipsis(l, tx, ly, tw, MUTED, 2)) * 10
        if prog >= 0:
            self.sk_progress(tx, ly + 1, min(tw, 140), prog)
        bx = x + w - self.right_w(r) - 2
        if badge:
            self.sk_chip(bx + 2, yy + (h - 12) // 2, badge, self.tone(badge))
            bx += self.chip_w(badge) + 6
        by = yy + (h - 16) // 2
        for b in (b1, b2):
            if b:
                self.sk_button(bx, by, self.bw(b[0]), b[0], b[1], b[2] if len(b) > 2 else True)
                bx += self.bw(b[0]) + 4

    TOAST_COLOR = [(0x4F, 0xA8, 0xFF), (0x3C, 0xCB, 0x6A), (0xF0, 0x50, 0x5A)]

    def toast(self, text, kind=1, since=1.0, left=0.7, hover=False):
        """TFPadScreen.drawNotice (1.4.1): el aviso abajo en el centro, a la escala grande. since: segundos desde que
        salió (entra en 0,22 s); left: lo que le queda (la línea de abajo)."""
        k_in = min(1.0, since / 0.22)
        k_in = 1 - (1 - k_in) ** 3
        a = min(1.0, since / (0.22 * 0.6))
        A = round(a * 255)
        bs = self.bs
        with self.big_at(0, 0):
            vw, vh = int(self.UW / bs), int(self.UH / bs)
            lines = self.split(text, min(vw - 70, 250))[:3]
            tw = max(self.mwidth(l) for l in lines)
            w, h = tw + 34, max(22, len(lines) * 10 + 12)
            x = (vw - w) // 2
            y = round(vh - 9 - h + (1 - k_in) * 12)
            acc = self.TOAST_COLOR[kind]
            self.fill(x + 2, y + h, x + w - 2, y + h + 1, (24, 38, 92, round(A * 0.38)))
            self.fill(x + 4, y + h + 1, x + w - 4, y + h + 2, (24, 38, 92, round(A * 0.16)))
            self.box(x, y, w, h, (10, 16, 48, round(A * 0.94)))
            self.vgrad_a(x + 1, y + 1, w - 2, h - 2, (0x34, 0x48, 0x8E, round(A * 0.95)) if hover else (0x2B, 0x3D, 0x80, round(A * 0.95)),
                         (0x17, 0x22, 0x56, round(A * 0.95)))
            self.fill(x + 2, y + 1, x + w - 2, y + 2, (255, 255, 255, round(A * 0.22)))
            self.vgrad(x + 1, y + 2, 2, h - 4, _lighten(acc, 50), acc)
            cx, cy = x + 13, y + h // 2
            self.glow(cx + 0.5, cy + 0.5, 11, acc, round(A * 0.55))
            self.disc(cx + 0.5, cy + 0.5, 6, darken(acc, 0.62) + (A,))
            self.disc(cx + 0.5, cy + 0.5, 5, tuple(acc) + (A,))
            gx, gy = cx - 3, cy - 3
            if kind == 1:
                pts = [(0, 3), (1, 4), (2, 5), (3, 4), (4, 3), (5, 2), (6, 1), (1, 3), (2, 4), (3, 3), (4, 2), (5, 1)]
            elif kind == 2:
                pts = [(3, 0), (3, 1), (3, 2), (3, 3), (3, 5)]
            else:
                pts = [(3, 0), (3, 2), (3, 3), (3, 4), (3, 5), (2, 2), (2, 6), (3, 6), (4, 6)]
            for px, py in pts:
                self.put(gx + px, gy + py, (255, 255, 255, A))
            ty = y + (h - len(lines) * 10) // 2 + 1
            for l in lines:
                self.mtext(l, x + 24, ty + 1, (10, 16, 48, round(A * 0.7)))
                self.mtext(l, x + 23, ty, (244, 247, 255))
                ty += 10
            lw = round((w - 8) * left)
            if lw > 0:
                self.fill(x + 4, y + h - 2, x + 4 + lw, y + h - 1, tuple(acc) + (round(A * 0.7),))

    def frame(self):
        """frame.png alargado como TFPadScreen.drawFrame (filas y columnas lisas repetidas), a escala ps."""
        import numpy as np
        a = np.asarray(Image.open(f'{TEX}/frame.png').convert('RGBA'))
        e, ex = self.extra * 4, self.extra_x * 4
        a = np.concatenate([a[:STRETCH_U], np.repeat(a[STRETCH_U:STRETCH_U + 1], e, axis=0), a[STRETCH_U:STRETCH_D],
                            np.repeat(a[STRETCH_D:STRETCH_D + 1], e, axis=0), a[STRETCH_D:]], axis=0)
        a = np.concatenate([a[:, :STRETCH_L], np.repeat(a[:, STRETCH_L:STRETCH_L + 1], ex, axis=1), a[:, STRETCH_L:STRETCH_R],
                            np.repeat(a[:, STRETCH_R:STRETCH_R + 1], ex, axis=1), a[:, STRETCH_R:]], axis=1)
        frame = Image.fromarray(a)
        if self.ps != 4:
            frame = frame.resize(((392 + self.extra_x * 2) * self.ps, (251 + self.extra * 2) * self.ps), Image.NEAREST)
        return frame

    def save(self, path, screen=False):
        """El marco a escala ps y el cristal encima, como en el juego. screen=True: dentro de la pantalla entera (fbw x
        fbh, con el fondo oscurecido), centrado por lo que se ve del marco, como TFPadScreen.init."""
        frame = self.frame()
        frame.alpha_composite(self.art, (GX * self.ps, GY * self.ps))
        if screen:
            vw, vh = VIS_R - VIS_L, VIS_B - VIS_T
            fx = (self.fbw - (vw + self.extra_x * 2) * self.ps) // 2 - VIS_L * self.ps
            fy = (self.fbh - (vh + self.extra * 2) * self.ps) // 2 - VIS_T * self.ps
            out = Image.new('RGBA', (self.fbw, self.fbh), (16, 16, 24, 255))
            out.alpha_composite(frame, (fx, fy))
            frame = out
        frame.save(path)
