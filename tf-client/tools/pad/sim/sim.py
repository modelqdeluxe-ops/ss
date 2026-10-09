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


class Sim:
    """El cristal del pad, dibujado en píxeles reales como en el juego (TFPadScreen.init): ps = píxeles reales por
    píxel del marco, cs = píxeles reales por unidad de contenido, big = píxeles reales por píxel de textura en la escala
    grande (portada, barra de arriba, iconos de la app Música). Con ps=4 (1080p a escala 2): cs=2, big=3 (bs=1,5),
    576x264 unidades. Las coordenadas de todos los métodos son unidades; dentro de `with s.big_at(x, y)` son píxeles
    de textura a la escala grande con la esquina en (x, y)."""

    def __init__(self, ps=4):
        self.ps = ps
        self.cs = ps if ps <= 2 else max(2, round(ps / 2))
        self.big = max(self.cs, int(ps * 0.75 + 0.5))
        self.bs = self.big / self.cs
        self.bar = round(12 * self.bs) + 6
        self.UW, self.UH = GW * ps // self.cs, GH * ps // self.cs
        self.Y = self.bar + 4
        self.X = self.logo_right(self.Y) + 4
        self.W = self.UW - self.X - 4
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

    def panel(self, x, y, w, h):
        self.fill(x + 2, y + h, x + w - 2, y + h + 1, rgb(0x2882D2))
        self.box(x, y, w, h, NAVY)
        self.box(x + 1, y + 1, w - 2, h - 2, rgb(0xE8F8FF))
        self.vgrad(x + 2, y + 2, w - 4, h - 4, rgb(0xF4FBFF), rgb(0xDCEEFC))
        self.fill(x + 2, y + 1, x + w - 2, y + 2, (255, 255, 255))
        self.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, rgb(0xAAD8F8))

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
        ch = {'·': '.', '«': '"', '»': '"', '…': '.'}.get(ch, ch)
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
            for yy in range(8):
                for xx in range(self.mcw[code]):
                    if self.mcfont.getpixel((gx + xx, gy + yy))[3]:
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

    BTN = [(0x3496FA, 0x96D6FF, 0x1854BE), (0xF6B628, 0xFFEC96, 0xBA7014), (0xE83446, 0xFF9C9C, 0x9A1A30),
           (0x40C850, 0xA0F078, 0x1E7C2C), (0xAABAD2, 0xD6E2F0, 0x7E8CA8)]

    def bw(self, label):
        return max(36, self.pwidth(label) + 14)

    def button(self, x, y, w, label, style, enabled=True, hover=False):
        c = self.BTN[style if enabled else 4]
        if hover:
            c = self.BTN[1]
        self.fill(x + 2, y + 15, x + w - 2, y + 16, rgb(0x2882D2))
        self.box(x, y, w, 15, NAVY)
        self.box(x + 1, y + 1, w - 2, 13, rgb(c[0]))
        self.fill(x + 2, y + 1, x + w - 2, y + 2, rgb(c[1]))
        self.fill(x + 2, y + 12, x + w - 2, y + 14, rgb(c[2]))
        self.ptext(label, x + w // 2 - self.pwidth(label) // 2, y + 2)

    def progress(self, x, y, w, f):
        self.box(x, y, w, 6, NAVY)
        self.fill(x + 1, y + 1, x + w - 1, y + 5, rgb(0xC8DCF0))
        fw = round((w - 2) * min(1, f))
        if fw > 0:
            full = f >= 1
            self.fill(x + 1, y + 1, x + 1 + fw, y + 5, rgb(0xF6B628 if full else 0x40C850))
            self.fill(x + 1, y + 1, x + 1 + fw, y + 2, rgb(0xFFEC96 if full else 0xA0F078))

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

    # -------------------------------------------------------------------------------------------------------------
    def status(self, title, back=True, title_color=(255, 255, 255), music=False):
        """TFPadScreen.drawStatus (1.3.25): todo a la escala grande."""
        bsz = self.bigu(12)
        bx, by = self.logo_right(0) + 4, (self.bar - bsz) // 2
        if back:
            with self.big_at(bx, by):
                self.box(0, 0, 12, 12, NAVY)
                self.box(1, 1, 10, 10, rgb(0x3496FA))
                self.fill(2, 2, 10, 3, rgb(0x96D6FF))
                self.fill(3, 5, 4, 7, (255, 255, 255))
                self.fill(4, 4, 5, 8, (255, 255, 255))
                self.fill(5, 3, 6, 9, (255, 255, 255))
                self.fill(6, 5, 9, 7, (255, 255, 255))
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
        """PadHomePage (1.3.25): fichas de 40 a la escala grande, rótulos grandes, halo y partículas en la de hover."""
        import math
        import random
        X, Y, W, H = self.X, self.Y, self.W, self.H
        t = self.bigu(40)
        cols = max(3, min(8, W // (t + self.bigu(10))))
        cw = W // cols
        ch = t + self.bigu(10) + 1
        top = Y
        x0 = X + (W - cols * cw) // 2
        rows = (len(apps) + cols - 1) // cols
        self.scissor(X, Y, W, H)
        rnd = random.Random(3)
        for i, (icon, name) in enumerate(apps):
            tx, ty = x0 + (i % cols) * cw + (cw - t) // 2, top + (i // cols) * ch
            hv = icon == hover
            if hv:
                for k in (3, 2, 1):
                    p = self.bigu(k * 2)
                    a = int(0.9 * (70 - k * 18))
                    self.box(tx - p, ty - p, t + p * 2, t + p * 2, (255, 242, 160, a))
            lift = -self.bigu(1) if hv else 0
            with self.big_at(tx, ty + lift):
                self.blit('tile_h' if hv else 'tile', 0, 0)
                self.blit('icon_' + icon, 4, 4)
                if glint == icon:
                    for row in range(4, 36):
                        a0 = max(4, 14 - int(row * 0.8)); b0 = min(36, a0 + 5)
                        if b0 > a0:
                            self.fill(a0, row, b0, row + 1, (255, 255, 255, 85))
            with self.big_at(tx + t / 2, ty + t - 1):
                self.ptext(name, -self.pwidth(name) // 2, 0, (255, 230, 128) if hv else (255, 255, 255))
            if hv:
                for _ in range(14):
                    px = tx + (4 + rnd.random() * 30) * self.bs
                    py = ty + (rnd.random() * 26) * self.bs
                    s = self.bigu(1 + rnd.randint(0, 1))
                    col = (255, 216, 74, 255 - rnd.randint(0, 120))
                    if rnd.random() < 0.4:
                        self.fill(int(px) - s, int(py), int(px) + s + 1, int(py) + 1, col)
                        self.fill(int(px), int(py) - s, int(px) + 1, int(py) + s + 1, col)
                    else:
                        self.fill(int(px), int(py), int(px) + s, int(py) + s, col)
        self.no_scissor()
        content = rows * ch
        if content > H:
            bx, bh = X + W - 3, H - 4
            self.fill(bx, Y + 2, bx + 2, Y + 2 + bh, (24, 38, 92, 0x55))
            th = max(10, bh * H // content)
            self.fill(bx, Y + 2, bx + 2, Y + 2 + th, rgb(0xF6B628))

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
        self.box(x, y, w, 15, NAVY)
        self.box(x + 1, y + 1, w - 2, 13, (255, 255, 255))
        self.fill(x + 2, y + 1, x + w - 2, y + 2, rgb(0xD6E6F6))
        shown = self.fitend(text or hint, w - 10)
        self.mtext(shown, x + 5, y + 4, NAVY if text else rgb(0x96AACC))

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

    CELL_W, CELL_H, CARD_W, CARD_H, ICON_BOX, ROW_GAP = 30, 32, 84, 66, 26, 2

    def view(self, tabs, sel, header, rows, footer=None, input_=None, empty='', cells=None, cards=None):
        """Igual que PadViewPage.render (1.3.24): pestañas, cabecera, rejilla de casillas o tarjetas y filas."""
        X, Y, W, H = self.X, self.Y, self.W, self.H
        cards_mode = cards is not None
        cells = cards if cards_mode else (cells or [])
        y = Y
        if tabs:
            gap, pad = 3, 10
            total = sum(self.pwidth(l) + pad for _, l in tabs) + gap * (len(tabs) - 1)
            if total > W:
                gap, pad = 2, 6
                total = sum(self.pwidth(l) + pad for _, l in tabs) + gap * (len(tabs) - 1)
            maxl = max(12, (W - gap * (len(tabs) - 1)) // len(tabs) - pad) if total > W else 10 ** 6
            x = X
            for key, label in tabs:
                label = self.pfit(label, maxl)
                w = self.pwidth(label) + pad
                s = key == sel
                self.box(x, y, w, 13, NAVY)
                self.box(x + 1, y + 1, w - 2, 11, rgb(0xF6B628 if s else 0xE8F8FF))
                if s:
                    self.fill(x + 2, y + 1, x + w - 2, y + 2, rgb(0xFFEC96))
                self.ptext(label, x + w // 2 - self.pwidth(label) // 2, y + 1, (255, 255, 255) if s else NAVY, s)
                x += w + gap
            y += 16
        room = 1 if tabs else 2
        for k, line in enumerate(header):
            if room <= 0:
                break
            if k == 0:
                self.diamond(X + 3, y + 4)
            n = self.wrap_ellipsis(line, X + 10, y + 1, W - 12, NAVY, room)
            room -= n
            y += 10 * n
        if header:
            y += 2
        bottom = bool(footer) or input_ is not None
        top, bot = y, Y + H - (19 if bottom else 0)
        self.panel(X, top, W, bot - top)
        ix, iw = X + 3, W - 10
        cw, ch = (self.CARD_W, self.CARD_H) if cards_mode else (self.CELL_W, self.CELL_H)
        cols = max(1, iw // cw)
        grows = (len(cells) + cols - 1) // cols
        gridh = grows * ch + (2 if grows else 0)
        content = gridh + sum(self.row_h(r, iw) for r in rows)
        visible = bot - top - 4
        self.scissor(X + 1, top + 2, W - 2, visible)
        ry = top + 2
        if grows:
            gx = X + (W - 4 - cols * cw) // 2
            for i, c in enumerate(cells):
                cx, cy = gx + (i % cols) * cw, ry + 1 + (i // cols) * ch
                if cards_mode:
                    self.card(c, cx, cy)
                else:
                    self.cell(c, cx, cy)
            ry += gridh
        for r in rows:
            h = self.row_h(r, iw)
            self.row(r, ix, ry, iw, h)
            ry += h
        self.no_scissor()
        if not rows and not cells:
            n = self.nlines(empty, W - 30)
            self.wrap(empty, X + 15, (top + bot) // 2 - n * 5, W - 30, rgb(0x4A6694))
        if content > visible:
            bx, by, bh = X + W - 5, top + 3, visible - 2
            self.fill(bx, by, bx + 2, by + bh, rgb(0xC8DCF0))
            th = max(8, bh * visible // content)
            self.fill(bx, by, bx + 2, by + th, rgb(0x3496FA))
        fy = Y + H - 16
        footer = footer or []
        total = sum(self.bw(l) + 4 for l, s in footer)
        x = X + W - total + 4
        for l, s in footer:
            self.button(x, fy, self.bw(l), l, s)
            x += self.bw(l) + 4
        if input_:
            hint, label, typed = input_
            bw = self.bw(label)
            right = X + W - total
            fw = right - X - bw - 4
            self.field(X, fy, fw, typed, hint)
            self.button(right - bw, fy, bw, label, 3, bool(typed))

    def diamond(self, cx, cy):
        self.fill(cx - 2, cy, cx + 3, cy + 1, rgb(0xC27A10))
        self.fill(cx - 1, cy - 1, cx + 2, cy + 2, rgb(0xF6B628))
        self.fill(cx, cy - 2, cx + 1, cy + 3, rgb(0xC27A10))
        self.fill(cx, cy - 1, cx + 1, cy, rgb(0xFFEC96))

    def cell(self, c, x, y):
        icon, label, color, sel = c
        cw, ch = self.CELL_W, self.CELL_H
        self.box(x + 1, y, cw - 2, ch - 1, rgb(0xC27A10 if sel else 0xB8D4EE))
        self.box(x + 2, y + 1, cw - 4, ch - 3, rgb(0xFFE9A8 if sel else 0xF6FBFF))
        self.fill(x + 3, y + 1, x + cw - 3, y + 2, (255, 255, 255))
        if icon:
            self.item(icon, x + (cw - 16) // 2, y + 3 if label else y + (ch - 1 - 16) // 2)
        if label:
            label = self.pfit(label, cw - 4)
            self.ptext(label, x + cw // 2 - self.pwidth(label) // 2, y + 20, rgb(color), False)

    def card(self, c, x, y):
        """PadViewPage.drawCard (1.3.25): degradado, franja de color, objeto al doble, nombre y línea en oro."""
        icon, name, color, sub, sel = c
        w, h = self.CARD_W - 4, self.CARD_H - 4
        self.fill(x + 4, y + h, x + w, y + h + 1, (32, 64, 112, 51))
        self.box(x + 2, y, w, h, rgb(0xC27A10 if sel else 0xB8D4EE))
        self.vgrad(x + 3, y + 1, w - 2, h - 2, rgb(0xFFF7DA if sel else 0xFFFFFF), rgb(0xFFE9A8 if sel else 0xEAF4FD))
        self.fill(x + 4, y + 2, x + w, y + 4, rgb(color))
        if icon:
            self.item(icon, x + 2 + (w - 32) // 2, y + 6, 2)
        name = self.fitend(name, w - 8)
        self.mtext(name, x + 2 + (w - self.mwidth(name)) // 2, y + 41, NAVY)
        if sub:
            sub = self.fitend(sub, w - 8)
            self.mtext(sub, x + 2 + (w - self.mwidth(sub)) // 2, y + 51, rgb(0xC27A10))

    def lines_h(self, r, tw):
        return sum(min(2, max(1, self.nlines(l, tw))) for l in r[3]) * 10

    def row_h(self, r, w):
        icon, title, color, lines, prog, badge, b1, b2 = r[:8]
        text_only = not icon and not b1 and not b2
        if text_only and not lines and title:
            return 16
        if text_only:
            n = sum(max(1, self.nlines(l, w - 12)) for l in lines)
            return 6 + (11 if title else 0) + n * 10 + 3 + self.ROW_GAP
        tw = self.text_w(r, w)
        h = 4 + 10 + self.lines_h(r, tw) + (8 if prog >= 0 else 0) + 3
        return max(self.ICON_BOX + 6 if icon else 24, h) + self.ROW_GAP

    def text_w(self, r, w):
        icon, b1, b2 = r[0], r[6], r[7]
        bw = sum(self.bw(b[0]) + 4 for b in (b1, b2) if b)
        return w - (self.ICON_BOX + 10 if icon else 10) - bw - 6

    def row(self, r, x, y, w, h):
        icon, title, color, lines, prog, badge, b1, b2 = r[:8]
        sel = len(r) > 8 and r[8]
        h -= self.ROW_GAP
        text_only = not icon and not b1 and not b2
        if text_only and not lines and title:
            self.mtext(title, x + 4, y + 5, rgb(color))
            lx = x + 8 + self.mwidth(title)
            self.fill(lx, y + 9, x + w - 4, y + 10, (194, 122, 16, 102))
            self.diamond(x + w - 6, y + 8)
            return
        self.box(x, y, w, h, rgb(0xE0A030 if sel else 0xC9DDF2))
        self.vgrad(x + 1, y + 1, w - 2, h - 2, rgb(0xFFF7DA if sel else 0xFFFFFF), rgb(0xFFE9A8 if sel else 0xF1F7FD))
        self.fill(x + 2, y + 1, x + w - 2, y + 2, (255, 255, 255, 170))
        if sel:
            self.fill(x + 1, y + 2, x + 3, y + h - 2, rgb(0xF6B628))
        if text_only:
            ty = y + 4
            if title:
                self.mtext(title, x + 6, ty, rgb(color))
                ty += 11
            for l in lines:
                ty += max(1, self.wrap(l, x + 6, ty, w - 12, NAVY)) * 10
            return
        tx = x + 6
        if icon:
            ib = self.ICON_BOX
            iy = y + (h - ib) // 2
            self.box(x + 4, iy, ib, ib, rgb(0xE0A030 if sel else 0xB8D4EE))
            self.vgrad(x + 5, iy + 1, ib - 2, ib - 2, (255, 255, 255), rgb(0xE4F1FC))
            self.item(icon, x + 4 + (ib - 16) // 2, iy + (ib - 16) // 2)
            tx = x + ib + 10
        bw = sum(self.bw(b[0]) + 4 for b in (b1, b2) if b)
        tw = self.text_w(r, w)
        bdw = self.mwidth(badge) + 6 if badge else 0
        th = 10 + self.lines_h(r, tw) + (8 if prog >= 0 else 0)
        top = y + 4 if not icon else y + max(4, (h - 1 - th) // 2)
        self.mtext(self.fitend(title, tw - bdw), tx, top, rgb(color))
        if badge:
            self.mtext(badge, tx + tw - bdw + 6, top, rgb(0xC27A10))
        ly = top + 10
        for l in lines:
            ly += max(1, self.wrap_ellipsis(l, tx, ly, tw, rgb(0x4A6694), 2)) * 10
        if prog >= 0:
            self.progress(tx, ly + 1, min(tw, 120), prog)
        bx, by = x + w - bw, y + (h - 16) // 2
        for b in (b1, b2):
            if b:
                self.button(bx, by, self.bw(b[0]), b[0], b[1], b[2] if len(b) > 2 else True)
                bx += self.bw(b[0]) + 4

    def save(self, path):
        """El marco a escala ps y el cristal encima, como en el juego."""
        frame = Image.open(f'{PAD}/marco.png').convert('RGBA')
        if self.ps != 4:
            frame = frame.resize((392 * self.ps, 251 * self.ps), Image.NEAREST)
        frame.alpha_composite(self.art, (GX * self.ps, GY * self.ps))
        frame.save(path)
