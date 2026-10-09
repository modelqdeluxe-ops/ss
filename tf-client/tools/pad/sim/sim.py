"""Simulador de las páginas del TF Pad (mismas posiciones y colores que el código Java) para ver cómo quedan."""
import json
import os
import sys
import unicodedata

from PIL import Image

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
    return base


MC = _mc_textures()
S = 4
NAVY = (24, 38, 92)


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


GX, GY, GW, GH = 52, 64, 288, 132
BAR = 18
# TFPadScreen.STRETCH_U/D y MAX_EXTRA: filas de frame.png que se repiten para hacer el pad más alto
STRETCH_U, STRETCH_D, MAX_EXTRA = 430, 596, 40
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


class Sim:
    """El cristal del pad, dibujado en píxeles reales como en el juego (TFPadScreen.init): ps = píxeles reales por
    píxel del marco, cs = píxeles reales por unidad de contenido, big = píxeles reales por píxel de textura en la escala
    grande (portada, barra de arriba, iconos de la app Música). Con ps=4 (1080p a escala 2): cs=2, big=3 (bs=1,5),
    576x264 unidades. Las coordenadas de todos los métodos son unidades; dentro de `with s.big_at(x, y)` son píxeles
    de textura a la escala grande con la esquina en (x, y)."""

    def __init__(self, ps=4, fbh=1080):
        self.ps = ps
        self.cs = ps if ps <= 2 else max(2, round(ps / 2))
        self.big = max(self.cs, int(ps * 0.75 + 0.5))
        self.bs = self.big / self.cs
        self.bar = round(12 * self.bs) + 6
        self.extra = max(0, min(MAX_EXTRA, int((fbh * 0.985 / ps - 251) // 2)))
        self.UW, self.UH = GW * ps // self.cs, (GH + self.extra * 2) * ps // self.cs
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

    def img(self, name):
        if name not in self.tex:
            self.tex[name] = Image.open(f'{TEX}/{name}.png').convert('RGBA')
        return self.tex[name]

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
            for l in self.split(line, w - 18):
                lines.append(l)
        if len(lines) > room:
            lines = lines[:room]
            lines[-1] = self.fitend(lines[-1] + '...', w - 18)
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
            for px, py in pts:
                for dx, dy in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (1, 1), (-1, 2), (0, 2), (1, 2), (0, 1)):
                    if (px + dx, py + dy) not in st:
                        self.put(px + dx, py + dy, NAVY)
        for p in pts:
            self.put(p[0], p[1], color)

    # Minecraft font
    def mcbase(self, ch):
        ch = {'·': '.', '«': '"', '»': '"', '…': '.', '¿': '?', '¡': '!'}.get(ch, ch)
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

    def count(self, n, x, y, k=1):
        """PadUi.item (1.3.28): la cantidad en blanco sobre una etiqueta azul noche, sin sombra."""
        t = str(n)
        w = self.mwidth(t) + 2
        rx, ry = x + 16 * k + 1 - w, y + 16 * k - 8
        self.box(rx, ry, w + 1, 9, rgb(0x22346E))
        self.mtext(t, rx + 1, ry + 1, (255, 255, 255))

    # -------------------------------------------------------------------------------------------------------------
    def status(self, title, back=True, title_color=(255, 255, 255), music=False):
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
        for text, icon, color in (('1,250', 'coin', (255, 230, 128)), ('18:30', 'sun', (255, 255, 255))):
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

    def home(self, apps, hover=None, glint=None):
        """PadHomePage (1.3.26): fichas de 40 a la escala grande, nombres a escala normal, 7 columnas y el aire
        repartido; la del ratón, elevada con su sombra. Sin halo ni partículas."""
        X, Y, W, H = self.X, self.Y, self.W, self.H
        LABEL_H, MIN_GAP = 11, 4
        t = self.bigu(40)
        gw = self.UW - 8
        cols = max(3, min(7, gw // (t + self.bigu(8))))
        cw = min(gw // cols, t + self.bigu(10))
        rows = (len(apps) + cols - 1) // cols
        base = t + LABEL_H + MIN_GAP
        ch = base if rows * base >= H else base + (H - rows * base) // (rows + 1)
        top = Y + (H - rows * ch) // 2 + (ch - t - LABEL_H) // 2 if rows * ch <= H else Y + 2
        x0 = (self.UW - cols * cw) // 2
        x0 = max(x0, self.logo_right(top) + 2 - (cw - t) // 2)
        x0 = min(x0, self.UW - 4 - cols * cw)
        self.scissor(2, Y, self.UW - 4, H)
        for i, (icon, name) in enumerate(apps):
            tx, ty = x0 + (i % cols) * cw + (cw - t) // 2, top + (i // cols) * ch
            hv = icon == hover
            lift = -round(self.bs * 2) if hv else 0
            if lift:
                self.fill(tx + self.bigu(3), ty + t, tx + t - self.bigu(3), ty + t + self.bigu(1), (24, 38, 92, 0x55))
            with self.big_at(tx, ty + lift):
                self.blit('tile_h' if hv else 'tile', 0, 0)
                self.blit('icon_' + icon, 4, 4)
            self.ptext(name, tx + t // 2 - self.pwidth(name) // 2, ty + t + 2, (255, 230, 128) if hv else (255, 255, 255))
        self.no_scissor()

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

    CELL_W, CELL_H, CARD_MIN, CARD_MAX, CARD_H, CARD_BAR, ICON_BOX, ROW_GAP, HERO_MIN, HERO_SLOT, SHOW, DOCK = (
        30, 32, 76, 116, 76, 8, 26, 3, 50, 40, 40, 20)

    def view(self, tabs, sel, header, rows, footer=None, input_=None, empty='', cells=None, cards=None, hover=None, hero=None):
        """PadViewPage.render (1.3.28): una sola ventana. Pestañas de carpeta encima (la elegida se une a ella); dentro,
        la barra de información, la cabecera grande, la lista (casillas, tarjetas, filas) y abajo la barra de acciones."""
        X, Y, W, H = self.X, self.Y, self.W, self.H
        cards_mode = cards is not None
        cells = cards if cards_mode else (cells or [])
        top = Y + (14 if tabs else 0)
        self.panel(X, top, W, Y + H - top)
        if tabs:
            gap, pad = 2, 14
            total = sum(self.pwidth(l) + pad for _, l in tabs) + gap * (len(tabs) - 1)
            room = W - 8
            if total > room:
                pad = 8
                total = sum(self.pwidth(l) + pad for _, l in tabs) + gap * (len(tabs) - 1)
            maxl = max(12, (room - gap * (len(tabs) - 1)) // len(tabs) - pad) if total > room else 10 ** 6
            x, chosen = X + 4, None
            for key, label in tabs:
                label = self.pfit(label, maxl)
                w = self.pwidth(label) + pad
                if key == sel:
                    chosen = (x, w, label)
                else:
                    self.tab(x, Y, w, label, 0)
                x += w + gap
            if chosen:
                self.tab(chosen[0], Y, chosen[1], chosen[2], 2)
        y = top + 1
        if header:
            y += self.infobar(X + 1, y, W - 2, header, 1 if tabs else 2)
        y += 3
        bottom = bool(footer) or input_ is not None
        dockh = self.DOCK if bottom else 0
        dock_y = Y + H - 1 - dockh
        bot = dock_y - (3 if bottom else 2)
        alone = hero and not (cells or rows or empty)
        if hero:
            hh = self.hero_h(hero, W - 10)
            hy = y + max(0, (bot - y - hh) // 2) if alone else y
            self.hero(hero, X + 5, hy, W - 10, hh)
            y += hh + 4
        visible = bot - y
        if not alone:
            ix, iw = X + 5, W - 15
            n = len(cells)
            if cards_mode:
                cols = max(1, iw // self.CARD_MIN)
                if 0 < n < cols:
                    cols = n
                    cw = min(self.CARD_MAX, iw // cols)
                else:
                    cw = iw // cols
                bar = any(len(c) > 6 and c[6].get('prog', -1) >= 0 for c in cells)
                ch = self.CARD_H + (self.CARD_BAR if bar else 0)
                gx = ix + (iw - cols * cw) // 2
            else:
                cw, ch = self.CELL_W, self.CELL_H
                cols = max(1, iw // cw)
                gx = ix + (iw - max(1, min(cols, n)) * cw) // 2
            grows = (n + cols - 1) // cols
            gridh = grows * ch + (2 if grows else 0)
            content = gridh + sum(self.row_h(r, iw) for r in rows)
            self.scissor(X + 1, y, W - 2, visible)
            ry = y + 1
            if cards_mode and not rows and content < visible:
                ry += (visible - content) // 2
            for i, c in enumerate(cells):
                cx, cy = gx + (i % cols) * cw, ry + 1 + (i // cols) * ch
                if cards_mode:
                    self.card(c, cx, cy, cw, ch, hover == i)
                else:
                    self.cell(c, cx, cy)
            ry += gridh
            for r in rows:
                h = self.row_h(r, iw)
                self.row(r, ix, ry, iw, h)
                ry += h
            self.no_scissor()
            if not rows and not cells:
                nl = self.nlines(empty, W - 40)
                mid = (y + bot) // 2
                for i, l in enumerate(self.split(empty, W - 40)):
                    self.mtext(l, X + W // 2 - self.mwidth(l) // 2, mid - nl * 5 + i * 10, MUTED)
            self.scrollbar(X + W - 8, y + 1, visible - 2, visible, content)
        if bottom:
            self.dock(X + 1, dock_y, W - 2, dockh)
            fy = dock_y + (dockh - 16) // 2
            footer = footer or []
            total = sum(self.bw(l) + 4 for l, s_ in footer)
            x = X + W - 5 - total + 4
            for l, st in footer:
                self.button(x, fy, self.bw(l), l, st)
                x += self.bw(l) + 4
            if input_:
                hint, label, typed = input_
                bw = self.bw(label)
                right = X + W - 5 - total
                fw = right - (X + 5) - bw - 4
                self.field(X + 5, fy, fw, typed, hint)
                self.button(right - bw, fy, bw, label, 3, bool(typed))

    def diamond(self, cx, cy):
        self.fill(cx - 2, cy, cx + 3, cy + 1, rgb(0xC27A10))
        self.fill(cx - 1, cy - 1, cx + 2, cy + 2, rgb(0xF6B628))
        self.fill(cx, cy - 2, cx + 1, cy + 3, rgb(0xC27A10))
        self.fill(cx, cy - 1, cx + 1, cy, rgb(0xFFEC96))

    def cell(self, c, x, y):
        """Casilla pequeña (1.3.28): medallón azul hielo con el objeto y su cantidad."""
        icon, label, color, sel = c[:4]
        n = c[4] if len(c) > 4 else 1
        cw, ch = self.CELL_W, self.CELL_H
        self.ucard(x + 1, y, cw - 2, ch - 1, 2 if sel else 0)
        if icon:
            iy = y + 4 if label else y + (ch - 1 - 16) // 2
            self.item(icon, x + (cw - 16) // 2, iy)
            if n > 1:
                self.count(n, x + (cw - 16) // 2, iy)
        if label:
            label = self.pfit(label, cw - 4)
            self.ptext(label, x + cw // 2 - self.pwidth(label) // 2, y + 20, on_dark(color), False)

    def pill(self, x, y, text, style, h):
        return self.chip(x, y, text, {3: 'green', 4: 'gray'}.get(style, 'gold'), h)

    def card(self, c, x, y, cw, ch, hover=False):
        """PadViewPage.drawCard (1.3.28): arriba la vitrina tintada del color de la tarjeta (con la textura, una luz
        detrás del objeto al doble y su sombra en el suelo); abajo el nombre en negrita y una etiqueta (precio o estado:
        oro; «+…» verde; gris si no se puede pulsar); la barra de progreso si tiene y la burbuja de aviso arriba.
        c = (icono, nombre, color, segunda línea, marcada, cantidad, {prog, badge, off})."""
        icon, name, color, sub, sel = c[:5]
        n = c[5] if len(c) > 5 else 1
        ex = c[6] if len(c) > 6 else {}
        on = not ex.get('off')
        col = on_dark(color)
        w, h = cw - 4, ch - 4
        x0, yy = x + 2, (y - 1 if hover else y)
        self.ucard(x0, yy, w, h, 2 if sel else 1 if hover else 0)
        sh = self.SHOW
        self.vgrad(x0 + 1, yy + 1, w - 2, sh, tint(col, 0.30), tint(col, 0.10))
        self.texture(x0 + 1, yy + 1, w - 2, sh)
        self.fill(x0 + 1, yy + 1 + sh, x0 + w - 1, yy + 2 + sh, tint(col, 0.55))
        self.fill(x0 + 1, yy + 2 + sh, x0 + w - 1, yy + 3 + sh, (255, 255, 255))
        cx = x0 + w // 2
        self.stretch('glow', cx - 20, yy + 2, 40, sh - 2)
        self.stretch('floor', cx - 13, yy + sh - 6, 26, 5)
        if icon:
            self.item(icon, cx - 16, yy + 4, 2)
            if n > 1:
                self.count(n, cx - 16, yy + 4, 2)
        self.title(name, cx - self.title_w(name, w - 6) // 2, yy + sh + 6, w - 6, TEXT)
        if sub:
            style = self.tone(sub, on or sel, ex.get('tone', 0))
            if self.chip_w(sub) > w - 6:
                sub = self.fitend(sub, w - 14)
            self.chip(cx - self.chip_w(sub) // 2, yy + sh + 17, sub, style)
        if ex.get('prog', -1) >= 0:
            pct = f"{round(min(1, ex['prog']) * 100)}%"
            pw = self.mwidth(pct)
            bw = w - 12 - pw - 4
            self.progress(x0 + 6, yy + sh + 33, bw, ex['prog'])
            self.mtext(pct, x0 + 6 + bw + 4, yy + sh + 32, MUTED)
        if ex.get('badge'):
            bw = self.chip_w(ex['badge'])
            self.chip(x0 + w - bw - 3, yy + 4, ex['badge'], 'green', 11)

    def hero_right(self, r):
        badge, b1, b2 = r[5], r[6], r[7]
        w = self.chip_w(badge) + 6 if badge else 0
        return w + sum(self.bw(b[0]) + 4 for b in (b1, b2) if b)

    def hero_tw(self, r, w):
        return w - (self.HERO_SLOT + 18) - self.hero_right(r) - 6

    def hero_h(self, r, w):
        h = 8 + 11 + self.lines_h(r, self.hero_tw(r, w)) + (10 if r[4] >= 0 else 0) + 6
        return max(self.HERO_MIN, h)

    def hero(self, r, x, y, w, h):
        """PadViewPage.drawHero (1.3.28): banner tintado del color de la ficha, con textura; el objeto al doble en su
        medallón con luz; título en negrita, líneas, barra y la etiqueta grande del precio."""
        icon, title, color, lines, prog, badge, b1, b2 = r[:8]
        col = on_dark(color)
        self.fill(x + 1, y + h, x + w - 1, y + h + 1, (40, 90, 160, 60))
        self.box(x, y, w, h, tint(col, 0.65))
        self.vgrad(x + 1, y + 1, w - 2, h - 2, tint(col, 0.24), tint(col, 0.07))
        self.texture(x + 1, y + 1, w - 2, h - 2)
        self.fill(x + 2, y + 1, x + w - 2, y + 2, (255, 255, 255))
        ms = self.HERO_SLOT
        mx, my = x + 6, y + (h - ms) // 2
        self.slot(mx, my, ms, ms, col)
        self.stretch('glow', mx + 1, my + 1, ms - 2, ms - 2)
        self.stretch('floor', mx + 8, my + ms - 7, ms - 16, 4)
        if icon:
            self.item(icon, mx + (ms - 32) // 2, my + (ms - 32) // 2 - 1, 2)
            n = r[8] if len(r) > 8 and isinstance(r[8], int) else 1
            if n > 1:
                self.count(n, mx + (ms - 32) // 2, my + (ms - 32) // 2 - 1, 2)
        tx, tw = x + ms + 14, self.hero_tw(r, w)
        th = 11 + self.lines_h(r, tw) + (10 if prog >= 0 else 0)
        top = y + max(6, (h - th) // 2 + 1)
        self.title(title, tx, top, tw, TEXT)
        ly = top + 12
        for l in lines:
            ly += max(1, self.wrap_ellipsis(l, tx, ly, tw, MUTED, 2)) * 10
        if prog >= 0:
            pct = f"{round(min(1, prog) * 100)}%"
            bw = min(tw - self.mwidth(pct) - 4, 160)
            self.progress(tx, ly + 1, bw, prog)
            self.mtext(pct, tx + bw + 4, ly, MUTED)
        bx = x + w - self.hero_right(r) - 2
        if badge:
            self.chip(bx, y + (h - 14) // 2, badge, self.tone(badge), 14)
            bx += self.chip_w(badge) + 6
        by = y + (h - 16) // 2
        for b in (b1, b2):
            if b:
                self.button(bx, by, self.bw(b[0]), b[0], b[1], b[2] if len(b) > 2 else True)
                bx += self.bw(b[0]) + 4

    def lines_h(self, r, tw):
        return sum(min(2, max(1, self.nlines(l, tw))) for l in r[3]) * 10

    def row_h(self, r, w):
        icon, title, color, lines, prog, badge, b1, b2 = r[:8]
        text_only = not icon and not b1 and not b2
        if text_only and not lines and title:
            return 16
        if text_only:
            n = sum(max(1, self.nlines(l, w - 14)) for l in lines)
            return 8 + (12 if title else 0) + n * 10 + 3 + self.ROW_GAP
        tw = self.text_w(r, w)
        h = 4 + 11 + self.lines_h(r, tw) + (8 if prog >= 0 else 0) + 3
        return max(self.ICON_BOX + 10 if icon else 26, h + 2) + self.ROW_GAP

    def right_w(self, r):
        badge, b1, b2 = r[5], r[6], r[7]
        return (self.chip_w(badge) + 6 if badge else 0) + sum(self.bw(b[0]) + 4 for b in (b1, b2) if b)

    def text_w(self, r, w):
        return w - (self.ICON_BOX + 15 if r[0] else 12) - self.right_w(r) - 6

    def row(self, r, x, y, w, h):
        """PadViewPage.drawRow (1.3.28): tarjeta con el objeto en su medallón (del color de la fila), el título en
        negrita, las líneas, la barra y a la derecha la etiqueta (precio, premio, estado) y los botones."""
        icon, title, color, lines, prog, badge, b1, b2 = r[:8]
        sel = len(r) > 8 and r[8] is True
        h -= self.ROW_GAP
        text_only = not icon and not b1 and not b2
        tcol = on_dark(color)
        if text_only and not lines and title:
            self.divider(x + 2, y + 4, w - 4, title, GOLD_TEXT if color == 0x18265C else tcol)
            return
        self.ucard(x, y, w, h, 2 if sel else 0)
        if text_only:
            ty = y + 5
            if title:
                self.btext(title, x + 7, ty, tcol)
                ty += 12
            for l in lines:
                ty += max(1, self.wrap(l, x + 7, ty, w - 14, MUTED)) * 10
            return
        tx = x + 8
        if icon:
            ib = self.ICON_BOX
            iy = y + (h - ib) // 2
            plain = color in (0x18265C, 0x7E8CA8)
            self.slot(x + 5, iy, ib, ib, None if plain else tcol)
            self.item(icon, x + 5 + (ib - 16) // 2, iy + (ib - 16) // 2)
            tx = x + ib + 12
        tw = self.text_w(r, w)
        th = 11 + self.lines_h(r, tw) + (8 if prog >= 0 else 0)
        top = y + 5 if not icon else y + max(5, (h - th) // 2 + 1)
        self.title(title, tx, top, tw, tcol)
        ly = top + 11
        for l in lines:
            ly += max(1, self.wrap_ellipsis(l, tx, ly, tw, MUTED, 2)) * 10
        if prog >= 0:
            self.progress(tx, ly + 1, min(tw, 140), prog)
        bx = x + w - self.right_w(r) - 2
        if badge:
            self.chip(bx + 2, y + (h - 12) // 2, badge, self.tone(badge))
            bx += self.chip_w(badge) + 6
        by = y + (h - 16) // 2
        for b in (b1, b2):
            if b:
                self.button(bx, by, self.bw(b[0]), b[0], b[1], b[2] if len(b) > 2 else True)
                bx += self.bw(b[0]) + 4

    def save(self, path):
        """El marco a escala ps y el cristal encima, como en el juego."""
        import numpy as np
        a = np.asarray(Image.open(f'{TEX}/frame.png').convert('RGBA'))
        e = self.extra * 4
        a = np.concatenate([a[:STRETCH_U], np.repeat(a[STRETCH_U:STRETCH_U + 1], e, axis=0), a[STRETCH_U:STRETCH_D],
                            np.repeat(a[STRETCH_D:STRETCH_D + 1], e, axis=0), a[STRETCH_D:]], axis=0)
        frame = Image.fromarray(a)
        if self.ps != 4:
            frame = frame.resize((392 * self.ps, (251 + self.extra * 2) * self.ps), Image.NEAREST)
        frame.alpha_composite(self.art, (GX * self.ps, GY * self.ps))
        frame.save(path)
