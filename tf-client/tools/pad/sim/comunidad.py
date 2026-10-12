"""Vistas previas de Comunidad con los emojis de Discord: python3 tools/pad/sim/comunidad.py <carpeta de salida>.

Dibuja PadCommunityPage (tarjetas con el texto con emojis y las etiquetas de reacción) y PadEmojiPicker (buscador,
pestañas, rejilla y pie) con las mismas medidas que el Java, y los emojis sacados de los atlas de verdad
(textures/gui/emoji, los hace tools/pad/make_emojis.py). Si cambias posiciones en esas clases, cámbialas aquí también.
pages.py lo llama para la hoja de todas las apps.
"""
import json
import os
import sys
import unicodedata

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import ROOT, Sim, rgb  # noqa: E402

EMO = os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/gui/emoji')
IDX = json.load(open(os.path.join(EMO, 'index.json'), encoding='utf-8'))
EMOJIS = IDX['emojis']
BY_ID = {e['id']: e for e in EMOJIS}
BY_NAME = {}
for _e in EMOJIS:
    for _n in _e['n']:
        BY_NAME.setdefault(_n, _e)
for _e in EMOJIS:
    BY_NAME[_e['id']] = _e
BY_CHAR = {}
for _e in EMOJIS:
    if not _e.get('a'):
        BY_CHAR.setdefault(_e['u'].replace('️', ''), _e)
LONGEST = max(len(k) for k in BY_CHAR)
_PAGES = {}
T = [0]  # milisegundos «de reloj» para los animados (PadEmojiDraw.frame)

INK = (24, 38, 92)
TEXT, MUTED, GOLD_TEXT = rgb(0x18265C), rgb(0x4A6694), rgb(0xC27A10)
GOLD, GOLD_LO = rgb(0xF6B628), rgb(0xC07818)
B, R = 0, 2

# PadCommunityClient.DEFAULT_FREQUENT
DEFAULT_FREQUENT = ['joy', 'thumbsup', 'heart', 'fire', 'sob', 'open_mouth', 'a_fire', 'a_joy', 'tada', 'clap', '100',
                    'eyes', 'skull', 'pray', 'sparkles', 'a_heart', 'smiling_face_with_three_hearts', 'rofl', 'cry',
                    'pout', 'sunglasses', 'thinking', 'a_thumbsup', 'a_tada']


def norm(s):
    s = unicodedata.normalize('NFD', s.lower())
    s = ''.join(c for c in s if unicodedata.category(c) != 'Mn')
    out, sep = [], False
    for c in s:
        if c.isalnum() or c == '+':
            if sep and out:
                out.append('_')
            out.append(c)
            sep = False
        else:
            sep = True
    return ''.join(out)


def legacy(i):
    return IDX['legacy'].get(i, i)


def search(q, mx):
    """PadEmojis.search."""
    q = norm(q)
    tiers = [[], [], [], [], []]
    for e in EMOJIS:
        t = 9
        for n in e['n']:
            if n == q:
                t = min(t, 0)
            elif n.startswith(q):
                t = min(t, 1)
            elif ('_' + q) in n:
                t = min(t, 2)
            elif q in n:
                t = min(t, 3)
        if t == 9 and q in (e['k'] + ' ' + norm(e['es'])):
            t = 4
        if t < 5:
            tiers[t].append(e)
    return [e for tier in tiers for e in tier][:mx]


def page_img(e):
    key = ('anim' if e.get('a') else 'static', e['p'])
    if key not in _PAGES:
        meta = IDX[key[0]][key[1]]
        _PAGES[key] = (Image.open(os.path.join(EMO, meta['file'] + '.png')).convert('RGBA'), meta)
    return _PAGES[key]


def edraw(s, e, x, y, u):
    """PadEmojiDraw.draw: la celda reducida al tamaño exacto en píxeles reales (media por área)."""
    if e is None:
        return
    img, meta = page_img(e)
    cell = e['i'] + ((T[0] // max(16, e['t'])) % e['f'] if e.get('a') else 0)
    c = meta['cell']
    sx, sy = cell % meta['cols'] * c, cell // meta['cols'] * c
    r = s._rect(x, y, x + u, y + u)
    px = r[2] - r[0]
    im = img.crop((sx, sy, sx + c, sy + c))
    im = im.convert('RGBa').resize((px, px), Image.BOX if px < c else Image.BILINEAR).convert('RGBA')
    s._paste_px(im, r[0], r[1])


class Local:
    """Como pages.Local: la página de la app «a escala» (X, Y, W, H en unidades de vista desde 0,0)."""

    def __init__(self, s):
        self.s = s

    def __enter__(self):
        s = self.s
        self.saved = (s.X, s.Y, s.W, s.H, s.bs, s.big)
        self.ctx = s.big_at(s.X, s.Y, px=s.big)
        self.ctx.__enter__()
        s.X, s.Y, s.W, s.H = 0, 0, int(self.saved[2] / s.bs), int(self.saved[3] / s.bs)
        s.bs, s.big = 1, s.f
        return s

    def __exit__(self, *a):
        s = self.s
        self.ctx.__exit__(*a)
        s.X, s.Y, s.W, s.H, s.bs, s.big = self.saved


# ---------------------------------------------------------------------------------------------------------------------
# Texto con emojis (PadEmojiDraw.wrap)
# ---------------------------------------------------------------------------------------------------------------------
INLINE = 9


def parse(text):
    out, buf, i = [], '', 0
    while i < len(text):
        c = text[i]
        if c == ':':
            j = text.find(':', i + 1)
            if j > i + 1 and j - i - 1 <= 48 and ' ' not in text[i + 1:j]:
                e = BY_NAME.get(norm(text[i + 1:j])) or BY_ID.get(legacy(text[i + 1:j]))
                if e:
                    if buf:
                        out.append(buf)
                    buf = ''
                    out.append(e)
                    i = j + 1
                    continue
        elif ord(c) > 127:
            for n in range(min(len(text) - i, LONGEST + 4), 0, -1):
                k = text[i:i + n].replace('️', '')
                if k and k in BY_CHAR:
                    if buf:
                        out.append(buf)
                    buf = ''
                    out.append(BY_CHAR[k])
                    i += n
                    break
            else:
                buf += c
                i += 1
            continue
        buf += c
        i += 1
    if buf:
        out.append(buf)
    return out


def segw(s, g):
    return INLINE + 1 if isinstance(g, dict) else s.mwidth(g)


def rich_wrap(s, text, x, y, w, color, maxl):
    words, cur = [], []
    for g in parse(text):
        if isinstance(g, dict):
            cur.append(g)
            continue
        parts = g.split(' ')
        for k, p in enumerate(parts):
            if p:
                cur.append(p)
            if k < len(parts) - 1 and cur:
                words.append(cur)
                cur = []
    if cur:
        words.append(cur)
    space = s.mwidth(' ')
    lines, line, lw = [], [], 0
    for wd in words:
        ww = sum(segw(s, g) for g in wd)
        if line and lw + space + ww > w:
            lines.append(line)
            line, lw = [], 0
        if line:
            line.append(' ')
            lw += space
        line += wd
        lw += ww
    if line:
        lines.append(line)
    n = min(len(lines), maxl)
    for i in range(n):
        ln = lines[i]
        if i == n - 1 and len(lines) > maxl:
            flat = [c for g in ln for c in ([g] if isinstance(g, dict) else list(g))]
            while flat and sum(segw(s, g) for g in flat) + s.mwidth('…') > w:
                flat.pop()
            ln = flat + ['…']
        cx = x
        for g in ln:
            if isinstance(g, dict):
                edraw(s, g, cx, y + i * 10 - 1, INLINE)
            else:
                s.mtext(g, cx, y + i * 10, color)
            cx += segw(s, g)
    return n


# ---------------------------------------------------------------------------------------------------------------------
# PadEmojiPicker
# ---------------------------------------------------------------------------------------------------------------------
CELL, EMOJI, HEAD, TAB_H, TAB_EMOJI, FOOT = 17, 13, 12, 18, 12, 24
PPAD, PBAR = 5, 6


class Picker:
    def __init__(self, query='', scroll=0, mine=(), section=None):
        self.query, self.scroll, self.mine, self.section = query, scroll, set(mine), section

    def place(self, ax, ay, aw, ah, anchor):
        self.cols = max(6, min(13, (aw - 12 - PPAD * 2 - PBAR) // CELL))
        self.w = self.cols * CELL + PPAD * 2 + PBAR
        self.h = max(110, min(190, ah - 6))
        self.x = max(ax + 4, min(anchor - self.w // 2, ax + aw - self.w - 4))
        self.y = ay + (ah - self.h) // 2
        self.gy = self.y + PPAD + 16 + 3 + TAB_H + 3
        self.gh = self.y + self.h - FOOT - self.gy
        self.layout()
        if self.section:
            for sid, _, _, top in self.sections:
                if sid == self.section:
                    self.scroll = top
        self.scroll = max(0, min(self.scroll, max(0, self.content - self.gh)))

    def rows(self, n):
        return max(0 if n == 0 else 1, (n + self.cols - 1) // self.cols)

    def layout(self):
        self.sections, top = [], 0
        if self.query.strip():
            found = search(self.query, 26 * 13)
            self.sections.append(('resultados', 'SIN RESULTADOS' if not found else 'RESULTADOS', found, 0))
            top = HEAD + self.rows(len(found)) * CELL
        else:
            freq = [BY_ID[i] for i in DEFAULT_FREQUENT if i in BY_ID][:self.cols * 2]
            self.sections.append(('frecuentes', 'FRECUENTES', freq, top))
            top += HEAD + self.rows(len(freq)) * CELL
            for c in IDX['categories']:
                lst = [e for e in EMOJIS if e['c'] == c['id']]
                self.sections.append((c['id'], c['name'].upper(), lst, top))
                top += HEAD + self.rows(len(lst)) * CELL
        self.content = top + 2

    def current(self):
        cur = self.sections[0][0]
        for sid, _, _, top in self.sections:
            if top <= self.scroll + 2:
                cur = sid
        return cur

    def render(self, s, mx, my, hits=None):
        x, y, w, h = self.x, self.y, self.w, self.h
        s.fill(x + 3, y + 3, x + w + 3, y + h + 3, (24, 38, 92, 85))
        s.box(x, y, w, h, INK)
        s.box(x + 1, y + 1, w - 2, h - 2, GOLD_LO)
        s.vgrad(x + 2, y + 2, w - 4, h - 4, (255, 255, 255), rgb(0xF8FCFF))
        # buscador
        fx, fy, fw = x + PPAD, y + PPAD, w - PPAD * 2
        s.box(fx, fy, fw, 16, INK)
        s.fill(fx + 1, fy + 1, fx + fw - 1, fy + 15, (255, 255, 255))
        s.fill(fx + 1, fy + 1, fx + fw - 1, fy + 2, rgb(0xD6E6F6))
        lx, ly = fx + fw - 13, fy + 4
        c = rgb(0x96AACC) if not self.query else INK
        s.fill(lx + 1, ly, lx + 5, ly + 1, c)
        s.fill(lx + 1, ly + 5, lx + 5, ly + 6, c)
        s.fill(lx, ly + 1, lx + 1, ly + 5, c)
        s.fill(lx + 5, ly + 1, lx + 6, ly + 5, c)
        for i in range(3):
            s.fill(lx + 5 + i, ly + 5 + i, lx + 7 + i, ly + 6 + i, c)
        shown = s.fitend('Buscar: fuego, risa, corazón, :joy:...', fw - 24) if not self.query else self.query
        s.mtext(shown, fx + 5, fy + 4, rgb(0x96AACC) if not self.query else TEXT)
        cx = fx + 5 + (0 if not self.query else s.mwidth(shown))
        s.fill(cx, fy + 3, cx + 1, fy + 13, TEXT)
        if hits is not None:
            hits.append((fx, fy, fw, 16))
        # pestañas
        tabs = [('frecuentes', 'Frecuentes', 'clock3')] + [(c['id'], c['name'], c['icon']) for c in IDX['categories']]
        n = len(tabs)
        tw = min(20, (w - PPAD * 2) // n)
        tx, ty = x + (w - tw * n) // 2, y + PPAD + 16 + 3
        cur = self.current() if not self.query.strip() else ''
        hov_tab = None
        for i, (tid, tname, icon) in enumerate(tabs):
            bx = tx + i * tw
            sel = tid == cur
            hov = bx <= mx < bx + tw and ty <= my < ty + TAB_H
            if sel:
                s.fill(bx + 1, ty, bx + tw - 1, ty + TAB_H, rgb(0xDCEBFA))
                s.fill(bx + 2, ty + TAB_H - 2, bx + tw - 2, ty + TAB_H, GOLD)
            elif hov:
                s.fill(bx + 1, ty, bx + tw - 1, ty + TAB_H, rgb(0xEEF5FD))
            o = (tw - TAB_EMOJI) // 2
            edraw(s, BY_NAME.get(icon), bx + o, ty + 2 - (1 if hov and not sel else 0), TAB_EMOJI)
            if hov:
                hov_tab = tname
            if hits is not None:
                hits.append((bx, ty, tw, TAB_H))
        # rejilla
        s.fill(x + 2, self.gy - 2, x + w - 2, self.gy - 1, rgb(0xD6E6F6))
        s.scissor(x + 2, self.gy, w - 4, self.gh)
        hovered = None
        for sid, title, lst, top in self.sections:
            sy = self.gy + top - self.scroll
            sh = HEAD + self.rows(len(lst)) * CELL
            if sy + sh < self.gy or sy > self.gy + self.gh:
                continue
            s.btext(title, x + PPAD + 1, sy + 2, MUTED)
            if sid == 'resultados' and not lst:
                s.mtext(s.fitend('Nada con «' + self.query + '». Prueba en inglés o en español.', w - PPAD * 2 - 4), x + PPAD + 1,
                        sy + HEAD + 4, MUTED)
            for i, e in enumerate(lst):
                cx, cy = x + PPAD + i % self.cols * CELL, sy + HEAD + i // self.cols * CELL
                if cy + CELL < self.gy or cy > self.gy + self.gh:
                    continue
                hov = cx <= mx < cx + CELL and cy <= my < cy + CELL and self.gy <= my < self.gy + self.gh
                sel = e['id'] in self.mine
                if sel or hov:
                    s.box(cx, cy, CELL, CELL, rgb(0xE0A030 if sel else 0x9DBCE0))
                    s.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, rgb(0xFFF3C8 if sel else 0xEAF4FD))
                o = (CELL - EMOJI) // 2
                edraw(s, e, cx + o, cy + o - (1 if hov else 0), EMOJI)
                if hov:
                    hovered = e
                if hits is not None and self.gy <= cy and cy + CELL <= self.gy + self.gh:
                    hits.append((cx, cy, CELL, CELL))
        s.no_scissor()
        s.scrollbar(x + w - PPAD - 4, self.gy + 1, self.gh - 2, self.gh, self.content, self.scroll)
        # pie
        fy = y + h - FOOT
        s.fill(x + 2, fy, x + w - 2, fy + 1, rgb(0xD6E6F6))
        s.fill(x + 2, fy + 1, x + w - 2, y + h - 2, rgb(0xEFF6FD))
        tx2, tw2 = x + PPAD + 22, w - PPAD * 2 - 24
        if hovered:
            edraw(s, hovered, x + PPAD + 1, fy + 3, 18)
            s.btext(s.fitend(':' + hovered['id'] + ':', tw2 - 8), tx2, fy + 3, TEXT)
            es = hovered['es']
            sub = es if not hovered.get('s') or hovered['s'] == hovered['id'] else ':' + hovered['s'] + ': · ' + es
            s.mtext(s.fitend(sub, tw2), tx2, fy + 13, MUTED)
        elif hov_tab:
            s.btext(hov_tab, x + PPAD + 2, fy + 8, TEXT)
        else:
            s.mtext(s.fitend(f'{len(EMOJIS):,}'.replace(',', '.') + ' emojis · escribe para buscar', w - PPAD * 2 - 4),
                    x + PPAD + 2, fy + 8, MUTED)


# ---------------------------------------------------------------------------------------------------------------------
# PadCommunityPage
# ---------------------------------------------------------------------------------------------------------------------
CHIP_EMOJI, CHIP_H, CHIP_ROW = 9, 13, 15


def chip_w(s, n):
    return CHIP_EMOJI + 6 + s.mwidth(str(n))


def chips(s, reacts, x_full, x_last, end, max_rows):
    """PadCommunityPage.chips."""
    known = len(reacts)
    best, rows = [], 1
    while rows <= max_rows:
        best, row, rx = [], 0, x_last if rows == 1 else x_full
        for k, (eid, n, mine) in enumerate(reacts):
            bw = chip_w(s, n)
            if rx + bw > end:
                if row + 1 >= rows:
                    break
                row += 1
                rx = x_last if row == rows - 1 else x_full
                if rx + bw > end:
                    break
            best.append([rx, row, bw, k])
            rx += bw + 3
        if len(best) == known or rows == max_rows:
            break
        rows += 1
    if len(best) < known:
        last = rows - 1
        while True:
            more = known - len(best)
            mw = s.mwidth('+' + str(more)) + 8
            prev = best[-1] if best else None
            sx = prev[0] + prev[2] + 3 if prev and prev[1] == last else x_last
            if sx + mw <= end or prev is None:
                best.append([sx, last, mw, -1 - more])
                break
            best.pop()
    return best, rows


def like_button(s, x, y, w, label, liked, hover):
    if liked:
        top, bot = (0xFFF2F8, 0xFFD0E4) if hover else (0xFFEAF3, 0xFFC2DC)
    else:
        top, bot = (0xFFF4F8, 0xFFD2E4) if hover else (0xFFFFFF, 0xDCEAF8)
    s.box(x, y, w, 16, INK)
    s.vgrad(x + 1, y + 1, w - 2, 14, rgb(top), rgb(bot))
    s.fill(x + 2, y + 1, x + w - 2, y + 2, (255, 255, 255))
    s.blit('heart' if liked else 'heart_off', x + 5, y + 3)
    s.mtext(label, x + 20, y + 4, rgb(0xC8327A) if liked else INK)


PHOTO = Image.open(os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/gui/menu_background.png')).convert('RGBA')
POSTS = [  # (nombre, cuándo, texto, likes, le diste like, reacciones [(emoji, cuántos, tuya)], es tuya)
    ('Pewez777', 'hace 12 min', 'Atardecer desde la torre del spawn :fuego: 😍 ¡gg! :a_tada:', 18, True,
     [('joy', 3, False), ('a_fire', 5, True), ('heart_eyes', 2, False), ('thumbsup', 4, True), ('a_100', 1, False),
      ('flag_es', 2, False), ('skull', 1, False), ('a_clap', 3, False), ('pizza', 1, False)], True),
    ('Steve', 'hace 2 h', 'Mi granja de :ear_of_rice: lista 👀 :a_eyes:', 1, False, [('eyes', 1, False)], False),
]


def comunidad(out, names, picker=None, hover=None, fbw=1920, fbh=1080, mouse=None, hits_out=None, t=0):
    """picker: un Picker abierto en la primera foto. hover: 'chip' (globo de la etiqueta). mouse: (x, y) del ratón
    dentro del selector (unidades de la página)."""
    T[0] = t
    s = Sim(fbw=fbw, fbh=fbh)
    s.status('COMUNIDAD')
    loc = Local(s)
    loc.__enter__()
    X, Y, W, H = s.X, s.Y, s.W, s.H
    hits = []
    top, bottom = Y + 14, Y + H
    s.panel(X, top, W, bottom - top)
    x, chosen = X + 4, None
    for key, label in (('recientes', 'RECIENTES'), ('populares', 'POPULARES'), ('mias', 'MÍAS')):
        w = s.pwidth(label) + 14
        if key == 'recientes':
            chosen = (x, w, label)
        else:
            s.tab(x, Y, w, label, 0)
        hits.append((x, Y, w, 15))
        x += w + 2
    s.tab(chosen[0], Y, chosen[1], chosen[2], 2)
    fw = s.bw('+ FOTO')
    s.button(X + W - fw - 4, Y - 3, fw, '+ FOTO', 1)
    pw = max(128, min(256, (W - 20) // 2)) // 16 * 16
    ph = pw * 9 // 16
    ch = ph + 14
    s.scissor(X + 2, top + 4, W - 4, bottom - top - 8)
    cy = top + 5
    anchor = None
    tip = None
    for i, (name, ago, cap, likes, liked, reacts, mine) in enumerate(POSTS):
        x, y, w, h = X + 5, cy, W - 16, ch - 4
        s.ucard(x, y, w, h, 2 if mine else 0)
        px, py = x + 5, y + 5
        s.box(px - 2, py - 2, pw + 4, ph + 4, INK)
        s.box(px - 1, py - 1, pw + 2, ph + 2, GOLD_LO)
        s.paste(PHOTO.resize((pw, ph)), px, py)
        cx = px + pw + 10
        cw = x + w - 6 - cx
        s.mtext(name, cx, py + 1, GOLD_TEXT)
        s.mtext(ago, cx, py + 12, MUTED)
        ey = py + ph - 32
        rbw = s.bw('REACCIONAR')
        s.button(cx, ey - 1, rbw, 'REACCIONAR', B, hover=picker is not None and i == 0)
        hits.append((cx, ey - 1, rbw, 16))
        if i == 0:
            anchor = cx + rbw // 2
        cap_top = py + 26
        max_rows = 1 + max(0, (ey - 2 - (10 if cap else 0) - cap_top) // CHIP_ROW)
        placed, rows = chips(s, reacts, cx, cx + rbw + 4, cx + cw, max_rows)
        for c in placed:
            ex, bw = c[0], c[2]
            cyy = ey + 1 - (rows - 1 - c[1]) * CHIP_ROW
            hits.append((ex, cyy, bw, CHIP_H))
            if c[3] < 0:
                s.box(ex, cyy, bw, CHIP_H, rgb(0x9DBCE0))
                s.fill(ex + 1, cyy + 1, ex + bw - 1, cyy + CHIP_H - 1, rgb(0xF2F8FE))
                s.mtext('+' + str(-1 - c[3]), ex + 4, cyy + 3, MUTED)
                continue
            eid, n, mk = reacts[c[3]]
            hv = hover == 'chip' and i == 0 and c[3] == 1
            s.box(ex, cyy, bw, CHIP_H, rgb(0xE0A030 if mk else 0x6A9AD0 if hv else 0x9DBCE0))
            s.fill(ex + 1, cyy + 1, ex + bw - 1, cyy + CHIP_H - 1,
                   rgb((0xFFEBB0 if hv else 0xFFF3C8) if mk else 0xE4F0FC if hv else 0xF2F8FE))
            edraw(s, BY_ID[eid], ex + 2, cyy + 2, CHIP_EMOJI)
            s.mtext(str(n), ex + CHIP_EMOJI + 4, cyy + 3, GOLD_TEXT if mk else TEXT)
            if hv:
                tip = (':' + eid + ':', ex + bw // 2, cyy)
        if cap:
            chips_top = ey + 1 - (rows - 1) * CHIP_ROW
            maxl = max(1, min((ph - 60) // 10, (chips_top - 2 - cap_top) // 10))
            rich_wrap(s, '«' + cap + '»', cx, cap_top, cw, TEXT, maxl)
        ly = py + ph - 15
        label = f'{likes} like' + ('' if likes == 1 else 's')
        lw = 20 + s.mwidth(label) + 6
        like_button(s, cx, ly - 1, lw, label, liked, False)
        hits.append((cx, ly - 1, lw, 16))
        blabel = 'BORRAR' if mine else 'DENUNCIAR'
        bw = s.bw(blabel)
        s.button(x + w - 6 - bw, ly - 1, bw, blabel, R if mine else B)
        cy += ch
    s.no_scissor()
    if tip:
        t_, tx, ty = tip
        tw = s.mwidth(t_) + 8
        x0, y0 = max(X + 2, min(tx - tw // 2, X + W - tw - 2)), ty - 14
        s.box(x0, y0, tw, 12, INK)
        s.fill(x0 + 1, y0 + 1, x0 + tw - 1, y0 + 11, rgb(0x2A3A70))
        s.mtext(t_, x0 + 4, y0 + 2, (255, 255, 255))
    if picker is not None:
        hits = []
        s.fill(X, Y - 4, X + W, Y + H, (24, 38, 92, 85))
        picker.mine = {e for e, n, m in POSTS[0][5] if m}
        picker.place(X, Y, W, H, anchor)
        mx, my = mouse if mouse else (-1000, -1000)
        picker.render(s, mx, my, hits)
    if hits_out:
        for hx, hy, hw, hh in hits:
            r = s._rect(hx, hy, hx + hw, hy + hh)
            red = Image.new('RGBA', (r[2] - r[0], r[3] - r[1]), (255, 0, 0, 255))
            red.paste((0, 0, 0, 0), (1, 1, red.width - 1, red.height - 1))
            s._paste_px(red, r[0], r[1])
    loc.__exit__(None, None, None)
    name = hits_out or out
    s.save(os.path.join(OUTDIR[0], name + '.png'))
    names.append(name)
    return s


OUTDIR = ['']


def run(outdir, names):
    OUTDIR[0] = outdir
    comunidad('p_comunidad', names, hover='chip')
    p = Picker()
    comunidad('p_comunidad_reaccionar', names, picker=p, mouse=(0, 0))
    # el ratón sobre un emoji de la rejilla (el 4º de Frecuentes)
    p = Picker()
    comunidad('p_comunidad_reaccionar_hover', names, picker=Picker(), mouse=_cell(p, 0, 3))
    comunidad('p_comunidad_animados', names, picker=Picker(section='animados'), mouse=_cell(Picker(section='animados'), 0, 1, 'animados'))
    comunidad('p_comunidad_buscar', names, picker=Picker(query='fuego'), mouse=_cell(Picker(query='fuego'), 0, 0, 'resultados'))
    comunidad('p_comunidad_banderas', names, picker=Picker(section='banderas'))
    comunidad('p_comunidad_zonas', names, picker=Picker(), hits_out='p_comunidad_zonas')
    comunidad('p_comunidad_768', names, picker=Picker(section='sonrisas'), fbw=1366, fbh=768)
    comunidad('p_comunidad_1440', names, picker=Picker(section='sonrisas'), fbw=2560, fbh=1440)
    comunidad('p_comunidad_4k', names, fbw=3840, fbh=2160, hover='chip')


def _cell(p, row, col, section='frecuentes', fbw=1920, fbh=1080):
    """Centro de una celda de la rejilla (en unidades de la página), para poner el ratón encima."""
    s = Sim(fbw=fbw, fbh=fbh)
    with Local(s):
        X, Y, W, H = s.X, s.Y, s.W, s.H
        pw = max(128, min(256, (W - 20) // 2)) // 16 * 16
        anchor = X + 5 + 5 + pw + 10 + s.bw('REACCIONAR') // 2
        p.place(X, Y, W, H, anchor)
        top = [t for sid, _, _, t in p.sections if sid == section][0]
        return (p.x + PPAD + col * CELL + CELL // 2, p.gy + top - p.scroll + HEAD + row * CELL + CELL // 2)


def gif(outdir, frames=12, step=90):
    """El selector en la sección de animados, fotograma a fotograma (p_comunidad_animados.gif)."""
    ims = []
    for k in range(frames):
        tmp = []
        OUTDIR[0] = outdir
        comunidad('_gif', tmp, picker=Picker(section='animados'), t=k * step)
        ims.append(Image.open(os.path.join(outdir, '_gif.png')).convert('RGB'))
    os.remove(os.path.join(outdir, '_gif.png'))
    ims[0].save(os.path.join(outdir, 'p_comunidad_animados.gif'), save_all=True, append_images=ims[1:], duration=step, loop=0)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit('Uso: python3 tools/pad/sim/comunidad.py <carpeta de salida>')
    d = sys.argv[1]
    os.makedirs(d, exist_ok=True)
    got = []
    run(d, got)
    gif(d)
    print('\n'.join(got + ['p_comunidad_animados.gif']))
