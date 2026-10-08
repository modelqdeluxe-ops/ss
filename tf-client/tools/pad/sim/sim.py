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


class Sim:
    X, Y, W, H = 60, 84, 272, 110

    def __init__(self):
        self.art = Image.new('RGBA', (392, 251), (0, 0, 0, 0))
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

    # -------------------------------------------------------------------------------------------------------------
    def img(self, name):
        if name not in self.tex:
            self.tex[name] = Image.open(f'{TEX}/{name}.png').convert('RGBA')
        return self.tex[name]

    def blit(self, name, x, y):
        self.art.alpha_composite(self.img(name), (x, y))

    def put(self, x, y, c):
        if 0 <= x < 392 and 0 <= y < 251:
            if len(c) == 4:
                base = self.art.getpixel((x, y))
                a = c[3] / 255
                c = tuple(int(base[i] * (1 - a) + c[i] * a) for i in range(3)) + (255,)
            else:
                c = tuple(c) + (255,)
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

    def item(self, name, x, y):
        if name.startswith('tfb:'):
            p = os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/block', name[4:] + '.png')
            im = Image.open(p).convert('RGBA').resize((16, 16), Image.NEAREST)
            self.art.alpha_composite(im, (x, y))
            return
        if name.startswith('tf:'):
            p = os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/item', name[3:] + '.png')
            if os.path.exists(p):
                im = Image.open(p).convert('RGBA').resize((16, 16), Image.NEAREST)
                self.art.alpha_composite(im, (x, y))
                return
        p = f'{MC}/item/{name}.png'
        if not os.path.exists(p):
            p = f'{MC}/block/{name}.png'
        if not os.path.exists(p):
            p = f'{MC}/item/paper.png'
        im = Image.open(p).convert('RGBA').crop((0, 0, 16, 16))
        self.art.alpha_composite(im, (x, y))

    # -------------------------------------------------------------------------------------------------------------
    def status(self, title, back=True):
        x = 96
        if back:
            self.box(86, 66, 12, 12, NAVY)
            self.box(87, 67, 10, 10, rgb(0x3496FA))
            self.fill(89, 71, 90, 73, (255, 255, 255))
            self.fill(90, 70, 91, 74, (255, 255, 255))
            self.fill(91, 69, 92, 75, (255, 255, 255))
            self.fill(92, 71, 95, 73, (255, 255, 255))
            x = 102
        self.ptext(title, x, 67)
        right = 330
        self.blit('gear', right - 10, 67)
        right -= 16
        coins = '1.250'
        w = self.pwidth(coins)
        self.ptext(coins, right - w, 68, (255, 230, 128))
        right -= w + 14
        self.blit('coin', right, 67)
        right -= 10
        t = '18:30'
        w = self.pwidth(t)
        self.ptext(t, right - w, 68)
        right -= w + 14
        self.blit('sun', right, 67)

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

    def view(self, tabs, sel, header, rows, footer=None, input_=None, empty='', cells=None):
        """Igual que PadViewPage.render (1.3.23): pestañas que siempre caben, cabecera de 2 líneas, rejilla y filas."""
        X, Y, W, H = self.X, self.Y, self.W, self.H
        cells = cells or []
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
            if room == 1 and self.nlines(line, W - 4) > 1:
                line = self.fitend(line, W - 4)
            n = self.wrap(line, X + 2, y + 1, W - 4, NAVY, room)
            room -= n
            y += 10 * n
        if header:
            y += 2
        bottom = bool(footer) or input_ is not None
        top, bot = y, Y + H - (19 if bottom else 0)
        self.panel(X, top, W, bot - top)
        ix, iw = X + 3, W - 10
        cols = max(1, iw // 32)
        grows = (len(cells) + cols - 1) // cols
        gridh = grows * 31 + (2 if grows else 0)
        content = gridh + sum(self.row_h(r, iw) for r in rows)
        visible = bot - top - 4
        ry = top + 2
        if grows:
            gx = X + (W - 4 - cols * 32) // 2
            for i, c in enumerate(cells):
                cx, cy = gx + (i % cols) * 32, ry + 1 + (i // cols) * 31
                if cy + 31 > bot:
                    break
                self.cell(c, cx, cy)
            ry += gridh
        for r in rows:
            h = self.row_h(r, iw)
            if ry + h > bot:
                break
            self.row(r, ix, ry, iw, h)
            ry += h
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
        self.box(x + 1, y, 30, 30, rgb(0xC27A10 if sel else 0xB8D4EE))
        self.box(x + 2, y + 1, 28, 28, rgb(0xFFE9A8 if sel else 0xF6FBFF))
        if icon:
            self.item(icon, x + 8, y + 3)
        if label:
            label = self.pfit(label, 28)
            self.ptext(label, x + 16 - self.pwidth(label) // 2, y + 19, rgb(color), False)

    def row_h(self, r, w):
        icon, title, color, lines, prog, badge, b1, b2 = r[:8]
        if not icon and not b1 and not b2:
            n = sum(max(1, self.nlines(l, w - 8)) for l in lines)
            return 6 + (11 if title else 0) + n * 10 + 3
        tw = self.text_w(r, w)
        n = sum(min(2, max(1, self.nlines(l, tw))) for l in lines)
        return max(26, 4 + 10 + n * 10 + (8 if prog >= 0 else 0) + 3)

    def text_w(self, r, w):
        icon, b1, b2 = r[0], r[6], r[7]
        bw = sum(self.bw(b[0]) + 4 for b in (b1, b2) if b)
        return w - (26 if icon else 8) - bw - 4

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
            iy = y + (h - 17) // 2
            self.box(x + 3, iy - 1, 20, 18, rgb(0xB8D4EE))
            self.box(x + 4, iy, 18, 16, (255, 255, 255))
            self.item(icon, x + 5, iy)
            tx = x + 27
        bw = sum(self.bw(b[0]) + 4 for b in (b1, b2) if b)
        tw = self.text_w(r, w)
        bdw = self.mwidth(badge) + 6 if badge else 0
        self.mtext(self.fitend(title, tw - bdw), tx, y + 4, rgb(color))
        if badge:
            self.mtext(badge, tx + tw - bdw + 6, y + 4, rgb(0xC27A10))
        ly = y + 14
        for l in lines:
            ly += max(1, self.wrap(l, tx, ly, tw, rgb(0x4A6694), 2)) * 10
        if prog >= 0:
            self.progress(tx, ly + 1, min(tw, 120), prog)
        bx, by = x + w - bw, y + (h - 16) // 2
        for b in (b1, b2):
            if b:
                self.button(bx, by, self.bw(b[0]), b[0], b[1], b[2] if len(b) > 2 else True)
                bx += self.bw(b[0]) + 4

    def save(self, path):
        frame = Image.open(f'{PAD}/marco.png').convert('RGBA')
        big = self.art.resize((392 * S, 251 * S), Image.NEAREST).crop((0, 0, frame.width, frame.height))
        frame.alpha_composite(big)
        frame.save(path)
