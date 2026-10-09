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
    """El cristal del pad en «unidades de pantalla», como TFPadScreen.init(): ps = píxeles reales por píxel del marco,
    cs = píxeles reales por unidad. Con ps=4 (1080p a escala 2), cs=2: 576x264 unidades."""

    def __init__(self, ps=4):
        self.ps = ps
        self.cs = ps if ps <= 2 else max(2, round(ps / 2))
        self.UW, self.UH = GW * ps // self.cs, GH * ps // self.cs
        self.Y = BAR + 4
        self.X = self.logo_right(self.Y) + 4
        self.W = self.UW - self.X - 4
        self.H = self.UH - self.Y - 4
        self.clip = None
        self.art = Image.new('RGBA', (self.UW, self.UH), (0, 0, 0, 0))
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

    def scissor(self, x, y, w, h):
        self.clip = (x, y, x + w, y + h)

    def no_scissor(self):
        self.clip = None

    # -------------------------------------------------------------------------------------------------------------
    def img(self, name):
        if name not in self.tex:
            self.tex[name] = Image.open(f'{TEX}/{name}.png').convert('RGBA')
        return self.tex[name]

    def paste(self, im, x, y):
        """Pega una imagen respetando el recorte (scissor)."""
        if self.clip:
            x0, y0, x1, y1 = self.clip
            cx0, cy0 = max(0, x0 - x), max(0, y0 - y)
            cx1, cy1 = min(im.width, x1 - x), min(im.height, y1 - y)
            if cx1 <= cx0 or cy1 <= cy0:
                return
            im = im.crop((cx0, cy0, cx1, cy1))
            x, y = x + cx0, y + cy0
        if x < 0 or y < 0:
            im = im.crop((max(0, -x), max(0, -y), im.width, im.height))
            x, y = max(0, x), max(0, y)
        self.art.alpha_composite(im, (x, y))

    def blit(self, name, x, y):
        self.paste(self.img(name), x, y)

    def put(self, x, y, c):
        if not (0 <= x < self.UW and 0 <= y < self.UH):
            return
        if self.clip and not (self.clip[0] <= x < self.clip[2] and self.clip[1] <= y < self.clip[3]):
            return
        if len(c) == 4 and c[3] < 255:
            base = self.art.getpixel((x, y))
            a, ba = c[3] / 255, base[3] / 255
            oa = a + ba * (1 - a)
            c = tuple(int((c[i] * a + base[i] * ba * (1 - a)) / oa) for i in range(3)) + (int(oa * 255),)
        else:
            c = tuple(c[:3]) + (255,)
        self.art.putpixel((x, y), c)

    def fill(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, c)

    def box(self, x, y, w, h, c):
        self.fill(x + 1, y, x + w - 1, y + h, c)
        self.fill(x, y + 1, x + w, y + h - 1, c)

    def panel(self, x, y, w, h):
        self.fill(x + 2, y + h, x + w - 2, y + h + 1, rgb(0x2882D2))
        self.box(x, y, w, h, NAVY)
        self.box(x + 1, y + 1, w - 2, h - 2, rgb(0xE8F8FF))
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
    def status(self, title, back=True, title_color=(255, 255, 255)):
        """TFPadScreen.drawStatus: [◀] junto al logo, título centrado en el cristal, hora, monedas y ajustes."""
        bx, by = self.logo_right(0) + 4, 3
        if back:
            self.box(bx, by, 12, 12, NAVY)
            self.box(bx + 1, by + 1, 10, 10, rgb(0x3496FA))
            self.fill(bx + 3, by + 5, bx + 4, by + 7, (255, 255, 255))
            self.fill(bx + 4, by + 4, bx + 5, by + 8, (255, 255, 255))
            self.fill(bx + 5, by + 3, bx + 6, by + 9, (255, 255, 255))
            self.fill(bx + 6, by + 5, bx + 9, by + 7, (255, 255, 255))
        self.ptext(title, self.UW // 2 - self.pwidth(title) // 2, 4, title_color)
        right = self.UW - 4
        self.blit('gear', right - 10, 4)
        right -= 16
        coins = '1,250'
        w = self.pwidth(coins)
        self.ptext(coins, right - w, 5, (255, 230, 128))
        right -= w + 14
        self.blit('coin', right, 4)
        right -= 10
        t = '18:30'
        w = self.pwidth(t)
        self.ptext(t, right - w, 5)
        right -= w + 14
        self.blit('sun', right, 4)

    def home(self, apps):
        """PadHomePage: fichas de 38 en celdas de 64x62, hasta 8 columnas, centradas."""
        X, Y, W, H = self.X, self.Y, self.W, self.H
        TILE, CW, CH = 38, 64, 62
        cols = max(3, min(8, W // CW))
        x0 = X + (W - cols * CW) // 2
        rows = (len(apps) + cols - 1) // cols
        self.scissor(X, Y, W, H)
        for i, (icon, name) in enumerate(apps):
            tx, ty = x0 + (i % cols) * CW + (CW - TILE) // 2, Y + 4 + (i // cols) * CH
            self.blit('tile', tx, ty)
            self.blit('icon_' + icon, tx + 3, ty + 3)
            self.ptext(name, tx + TILE // 2 - self.pwidth(name) // 2, ty + 43)
        self.no_scissor()
        content = rows * CH + 4
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

    CELL_W, CELL_H, CARD_W, CARD_H, ICON_BOX = 44, 46, 96, 74, 36

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
        for line in header:
            if room <= 0:
                break
            n = self.wrap_ellipsis(line, X + 2, y + 1, W - 4, NAVY, room)
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

    def cell(self, c, x, y):
        icon, label, color, sel = c
        cw, ch = self.CELL_W, self.CELL_H
        self.box(x + 1, y, cw - 2, ch - 1, rgb(0xC27A10 if sel else 0xB8D4EE))
        self.box(x + 2, y + 1, cw - 4, ch - 3, rgb(0xFFE9A8 if sel else 0xF6FBFF))
        if icon:
            self.item(icon, x + 6, y + 3 if label else y + 6, 2)
        if label:
            label = self.pfit(label, cw - 6)
            self.ptext(label, x + cw // 2 - self.pwidth(label) // 2, y + 36, rgb(color), False)

    def card(self, c, x, y):
        """PadViewPage.drawCard: franja de color, objeto al doble, nombre y segunda línea en oro."""
        icon, name, color, sub, sel = c
        w, h = self.CARD_W - 4, self.CARD_H - 4
        self.box(x + 2, y, w, h, rgb(0xC27A10 if sel else 0xB8D4EE))
        self.box(x + 3, y + 1, w - 2, h - 2, rgb(0xFFF3C8 if sel else 0xFFFFFF))
        self.fill(x + 4, y + 2, x + w, y + 4, rgb(color))
        if icon:
            self.item(icon, x + 2 + (w - 32) // 2, y + 6, 2)
        name = self.fitend(name, w - 8)
        self.mtext(name, x + 2 + (w - self.mwidth(name)) // 2, y + 42, NAVY)
        if sub:
            sub = self.fitend(sub, w - 8)
            self.mtext(sub, x + 2 + (w - self.mwidth(sub)) // 2, y + 54, rgb(0xC27A10))

    def lines_h(self, r, tw):
        return sum(min(2, max(1, self.nlines(l, tw))) for l in r[3]) * 10

    def row_h(self, r, w):
        icon, title, color, lines, prog, badge, b1, b2 = r[:8]
        if not icon and not b1 and not b2:
            n = sum(max(1, self.nlines(l, w - 8)) for l in lines)
            return 6 + (11 if title else 0) + n * 10 + 3
        tw = self.text_w(r, w)
        h = 4 + 10 + self.lines_h(r, tw) + (8 if prog >= 0 else 0) + 3
        return max(self.ICON_BOX + 6 if icon else 26, h)

    def text_w(self, r, w):
        icon, b1, b2 = r[0], r[6], r[7]
        bw = sum(self.bw(b[0]) + 4 for b in (b1, b2) if b)
        return w - (self.ICON_BOX + 10 if icon else 8) - bw - 4

    def row(self, r, x, y, w, h):
        icon, title, color, lines, prog, badge, b1, b2 = r[:8]
        sel = len(r) > 8 and r[8]
        if sel:
            self.box(x, y, w, h - 1, rgb(0xFFE9A8))
            self.fill(x, y + 2, x + 2, y + h - 3, rgb(0xF6B628))
        self.fill(x + 2, y + h - 1, x + w - 2, y + h, rgb(0xC8E4F8))
        if not icon and not b1 and not b2:
            ty = y + 4
            if title:
                self.mtext(title, x + 4, ty, rgb(color))
                ty += 11
            for l in lines:
                ty += max(1, self.wrap(l, x + 4, ty, w - 8, NAVY)) * 10
            return
        tx = x + 5
        if icon:
            ib = self.ICON_BOX
            iy = y + (h - 1 - ib) // 2
            self.box(x + 4, iy, ib, ib, rgb(0xB8D4EE))
            self.box(x + 5, iy + 1, ib - 2, ib - 2, (255, 255, 255))
            self.item(icon, x + 6, iy + 2, 2)
            tx = x + ib + 12
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
        """El marco a escala ps y el cristal encima a escala cs, como en el juego."""
        frame = Image.open(f'{PAD}/marco.png').convert('RGBA')
        if self.ps != 4:
            frame = frame.resize((392 * self.ps, 251 * self.ps), Image.NEAREST)
        big = self.art.resize((self.UW * self.cs, self.UH * self.cs), Image.NEAREST)
        frame.alpha_composite(big, (GX * self.ps, GY * self.ps))
        frame.save(path)
