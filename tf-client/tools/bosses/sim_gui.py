"""Comprueba la ventana /tf boss (BossScreen.java) sin abrir el juego: hace las mismas cuentas que BossScreen.layout,
chipRect, stepRect y riftButtons a varios tamaños de interfaz y mira que nada se monte encima de nada, que todo quepa
en el panel y que los textos tengan sitio. Con --png <carpeta> dibuja cada tamaño (a x3) para mirarlo.

    python3 tools/bosses/sim_gui.py [--png carpeta]

Si se cambia la disposición en BossScreen.java, se cambia aquí igual.
"""
import sys

# Anchos de la fuente de Minecraft (avance con su espacio); lo demás, 6
NARROW = {' ': 4, '!': 2, "'": 2, ',': 2, '.': 2, ':': 2, ';': 2, '|': 2, 'i': 2, 'í': 2, '·': 2, 'l': 3, '`': 3,
          't': 4, 'I': 4, 'Í': 4, '[': 4, ']': 4, '(': 4, ')': 4, '{': 4, '}': 4, '"': 4, '*': 4, 'f': 5, 'k': 5,
          '<': 5, '>': 5, '@': 7, '~': 7, '…': 8, '✕': 9, '✎': 9, '−': 6}


def width(s):
    return sum(NARROW.get(c, 6) for c in s)


GAP, PAD, FOOT, BTN, CHIP, TAB, ROWS, CARD_H, RIFT_H = 8, 8, 14, 16, 14, 16, 9, 32, 32
PRESETS = ['Fácil', 'Normal', 'Difícil', 'Pesadilla', 'Demencial']
LABELS = ['Vida', 'Daño', 'Velocidad', 'Ritmo de ataque', 'Inteligencia', 'Armadura', 'Vida por jugador',
          'Recarga de la grieta', 'Monedas por jugador']
# Los valores más anchos que puede enseñar cada fila
VALUES = ['24.000', '180', '×1,25', '×1,5', '100 %', '18', '+100 %', '1 h 45 min', '1.000.000']
BOSSES = ['Gárgola', 'Hidra', 'Abominación Parasitaria', 'Esqueleto de la Torre', 'Astwood']


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


class L:
    pass


def layout(w, h, banner):
    o = L()
    o.headH = 28
    o.pw = min(w - 12, 620)
    o.ph = min(h - 12, 340)
    o.px = (w - o.pw) // 2
    o.py = (h - o.ph) // 2
    o.bx = o.px + PAD
    o.by = o.py + o.headH + 4
    o.bw = o.pw - 2 * PAD
    o.bh = o.ph - o.headH - 4 - FOOT - PAD
    o.footY = o.py + o.ph - FOOT - 2
    o.listW = clamp(int(o.bw * 0.22), 100, 140)
    o.midW = clamp(int(o.bw * 0.32), 120, 210)
    o.rightW = o.bw - o.listW - o.midW - 2 * GAP
    if o.rightW < 170:
        o.midW = max(110, o.midW - (170 - o.rightW))
        o.rightW = o.bw - o.listW - o.midW - 2 * GAP
    o.listX = o.bx
    o.midX = o.listX + o.listW + GAP
    o.rightX = o.midX + o.midW + GAP
    buttons = 3 * BTN + 2 * 3
    o.btnY = o.by + o.bh - buttons
    o.nameY = o.btnY - 26
    o.stageY = o.by
    o.stageH = o.nameY - 4 - o.stageY
    o.tabsY = o.by
    y = o.tabsY + TAB + 5
    o.bannerY = None
    if banner:
        o.bannerY = y
        y += 13
    o.chipsY = y
    o.chipsPerRow = 5 if o.rightW >= 5 * 56 + 4 * 3 else 3
    o.chipRows = 1 if o.chipsPerRow == 5 else 2
    y += o.chipRows * CHIP + (o.chipRows - 1) * 3 + 6
    o.rowsY = y
    o.rowH = max(11, min(20, (o.by + o.bh - y) // ROWS))
    return o


def chip_rect(o, i):
    per = o.chipsPerRow
    row, col = i // per, i % per
    in_row = len(PRESETS) - row * per if row == o.chipRows - 1 else per
    cw = (o.rightW - (in_row - 1) * 3) // in_row
    x = o.rightX + col * (cw + 3)
    w = o.rightX + o.rightW - x if col == in_row - 1 else cw
    return (x, o.chipsY + row * (CHIP + 3), w, CHIP)


def step_rect(o, i, plus):
    s = min(12, o.rowH - 2)
    y = o.rowsY + i * o.rowH + (o.rowH - 1 - s) // 2
    x = o.rightX + o.rightW - (s + 2 if plus else 2 * s + 5)
    return (x, y, s, s)


def rift_buttons(o, i):
    top = o.tabsY + TAB + 5 + BTN + 5 + RIFT_H  # (debajo de la tarjeta de la grieta del Void)
    y = top + i * RIFT_H + 7
    x3 = o.rightX + o.rightW - 16
    return [(x3 - 2 * 17 - 4, y, 20, 14), (x3 - 17, y, 14, 14), (x3, y, 14, 14)]


def void_card_buttons(o):
    y = o.tabsY + TAB + 5 + BTN + 5 + 7
    x3 = o.rightX + o.rightW - 16
    return [(x3 - 24, y, 20, 14), (x3, y, 14, 14)]


VOID_ROW = 14


def void_layout(o):
    """= BossScreen.voidLayout (el panel de la grieta del Void, 1.3.89; su secuencia, 1.3.96)."""
    v = L()
    x, w = o.rightX, o.rightW
    y = o.tabsY + TAB + 5
    v.back = (x, y, 16, BTN)
    v.title = (x + 22, y + 4, w - 24, 9)
    y += BTN + 3
    cw = (w - 3) // 2
    v.chipSeq = (x, y, cw, CHIP)
    v.chipRand = (x + cw + 3, y, w - cw - 3, CHIP)
    y += CHIP + 3
    v.status = (x + 2, y, w - 4, 9)
    y += 12
    v.end = (x, y, cw, BTN)
    v.reset = (x + cw + 3, y, w - cw - 3, BTN)
    y += BTN + 3
    aw = 54
    v.add = (x, y, w - aw - 3, BTN)
    v.all = (x + w - aw, y, aw, BTN)
    v.listY = y + BTN + 4
    return v


def void_row_buttons(o, v, i):
    """= BossScreen.voidRowButtons: ▲ ▼ ✎ ✕ de la fila i."""
    y = v.listY + i * VOID_ROW + 1
    x = o.rightX + o.rightW - 13
    return [(x - 39, y, 12, 12), (x - 26, y, 12, 12), (x - 13, y, 12, 12), (x, y, 12, 12)]


def inter(a, b):
    return a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]


def inside(a, b):
    return a[0] >= b[0] and a[1] >= b[1] and a[0] + a[2] <= b[0] + b[2] and a[1] + a[3] <= b[1] + b[3]


def pieces(w, h, tab, banner, rifts=6):
    """Todo lo que se dibuja: (nombre, rect, grupo). Dos del mismo grupo de nivel superior no se pueden tocar."""
    o = layout(w, h, banner and tab == 0)
    out = []
    out.append(('cabecera', (o.px, o.py, o.pw, o.headH), 'top'))
    out.append(('cerrar', (o.px + o.pw - 20, o.py + 7, 14, 14), 'head'))
    tw = round(width('JEFES') * 1.4)
    sub_w = o.pw - tw - 50
    out.append(('título', (o.px + 10, o.py + 8, tw, 12), 'head'))
    out.append(('subtítulo', (o.px + 16 + tw, o.py + 11, min(sub_w, width('Incursiones: elige el jefe, su dificultad y pon su grieta')), 9), 'head'))
    # Lista
    visible = o.bh // CARD_H
    for i in range(min(len(BOSSES), visible)):
        out.append((f'jefe {i}', (o.listX, o.by + i * CARD_H, o.listW, CARD_H - 3), 'body'))
    out.append(('escenario', (o.midX, o.stageY, o.midW, o.stageH), 'body'))
    out.append(('nombre', (o.midX + 2, o.nameY + 2, o.midW - 4, 22), 'body'))
    for k in range(3):
        out.append((f'botón {k}', (o.midX, o.btnY + k * (BTN + 3), o.midW, BTN), 'body'))
    out.append(('pestaña 0', (o.rightX, o.tabsY, (o.rightW - 3) // 2, TAB), 'body'))
    out.append(('pestaña 1', (o.rightX + (o.rightW - 3) // 2 + 3, o.tabsY, o.rightW - (o.rightW - 3) // 2 - 3, TAB), 'body'))
    if tab == 0:
        if o.bannerY is not None:
            out.append(('aviso edición', (o.rightX, o.bannerY, o.rightW, 11), 'body'))
        for i in range(len(PRESETS)):
            out.append((f'preset {PRESETS[i]}', chip_rect(o, i), 'body'))
        for i in range(ROWS):
            out.append((f'fila {i}', (o.rightX, o.rowsY + i * o.rowH, o.rightW, o.rowH - 1), 'body'))
        bottom = o.by + o.bh
        iy = o.rowsY + ROWS * o.rowH + 6
        if bottom - iy >= 9:
            out.append(('con 4 jugadores', (o.rightX + 2, iy, o.rightW - 4, 9), 'body'))
    elif tab == 2:
        v = void_layout(o)
        for n in ('back', 'title', 'chipSeq', 'chipRand', 'status', 'end', 'reset', 'add', 'all'):
            out.append((f'void {n}', getattr(v, n), 'body'))
        vis = max(1, (o.by + o.bh - v.listY) // VOID_ROW)
        for i in range(min(17, vis)):
            out.append((f'void fila {i}', (o.rightX, v.listY + i * VOID_ROW, o.rightW, VOID_ROW - 1), 'body'))
    else:
        top = o.tabsY + TAB + 5
        out.append(('quitar pruebas', (o.rightX, top, o.rightW, BTN), 'body'))
        y0 = top + BTN + 5
        out.append(('grieta del Void', (o.rightX, y0, o.rightW, RIFT_H - 3), 'body'))
        y0 += RIFT_H
        vis = max(1, (o.by + o.bh - y0) // RIFT_H)
        for i in range(min(rifts, vis)):
            out.append((f'grieta {i}', (o.rightX, y0 + i * RIFT_H, o.rightW, RIFT_H - 3), 'body'))
    out.append(('pie', (o.px + 10, o.footY + 3, o.pw - 20, 9), 'foot'))
    return o, out


def check(w, h):
    errs = []
    for tab in (0, 1, 2):
        for banner in (False, True):
            o, ps = pieces(w, h, tab, banner)
            panel = (o.px, o.py, o.pw, o.ph)
            screen = (0, 0, w, h)
            if not inside(panel, screen):
                errs.append('el panel se sale de la pantalla')
            for n, r, g in ps:
                if not inside(r, panel):
                    errs.append(f'{n} se sale del panel {r}')
            body = [(n, r) for n, r, g in ps if g == 'body']
            for i in range(len(body)):
                for j in range(i + 1, len(body)):
                    if inter(body[i][1], body[j][1]):
                        errs.append(f'{body[i][0]} pisa {body[j][0]}')
            head = [(n, r) for n, r, g in ps if g == 'head']
            for i in range(len(head)):
                for j in range(i + 1, len(head)):
                    if inter(head[i][1], head[j][1]):
                        errs.append(f'{head[i][0]} pisa {head[j][0]}')
            for n, r, g in ps:
                if g == 'body' and r[1] + r[3] > o.footY:
                    errs.append(f'{n} baja hasta el pie')
                if g == 'body' and r[1] < o.py + o.headH:
                    errs.append(f'{n} sube a la cabecera')
            if o.stageH < 60:
                errs.append(f'escenario muy bajo ({o.stageH})')
            if tab == 0:
                for i in range(len(PRESETS)):
                    x, y, cw, ch = chip_rect(o, i)
                    if width(PRESETS[i]) > cw - 4:
                        errs.append(f'«{PRESETS[i]}» no cabe en su botón ({cw})')
                for i in range(ROWS):
                    mi, pl = step_rect(o, i, False), step_rect(o, i, True)
                    row = (o.rightX, o.rowsY + i * o.rowH, o.rightW, o.rowH - 1)
                    if not inside(mi, row) or not inside(pl, row) or inter(mi, pl):
                        errs.append(f'fila {i}: − y + mal puestos')
                    if mi[2] < 9:
                        errs.append(f'fila {i}: botones de {mi[2]} px')
                    value_right = mi[0] - 4
                    label_w = value_right - width(VALUES[i]) - o.rightX - 8
                    if label_w < width(LABELS[i]):
                        # Se recorta con «…»: se avisa si queda muy poco
                        if label_w < 60:
                            errs.append(f'fila {i}: «{LABELS[i]}» con solo {label_w} px')
            elif tab == 2:
                v = void_layout(o)
                if (o.by + o.bh - v.listY) // VOID_ROW < 3:
                    errs.append('grieta del Void: caben menos de 3 criaturas en la lista')
                for lab, r in (('EN ORDEN', v.chipSeq), ('AL AZAR', v.chipRand), ('FIN: REPETIR', v.end), ('FIN: EL FINAL', v.end),
                               ('REINICIAR', v.reset), ('A TODOS', v.all), ('GRIETA DEL VOID · 17 jefes', v.title)):
                    if width(lab) > r[2] - 4:
                        errs.append(f'«{lab}» no cabe ({r[2]})')
                vis = max(1, (o.by + o.bh - v.listY) // VOID_ROW)
                for i in range(min(17, vis)):
                    row = (o.rightX, v.listY + i * VOID_ROW, o.rightW, VOID_ROW - 1)
                    bs = void_row_buttons(o, v, i)
                    for b in bs:
                        if not inside(b, row):
                            errs.append(f'void fila {i}: botón fuera de la fila')
                    for a in range(4):
                        for b in range(a + 1, 4):
                            if inter(bs[a], bs[b]):
                                errs.append(f'void fila {i}: botones pisados')
                    if bs[0][0] - o.rightX - 4 - width('17. ▶ ') < 60:
                        errs.append(f'void fila {i}: poco sitio para el nombre')
            else:
                top = o.tabsY + TAB + 5
                y0 = top + BTN + 5
                card = (o.rightX, y0, o.rightW, RIFT_H - 3)
                for b in void_card_buttons(o):
                    if not inside(b, card):
                        errs.append('grieta del Void: botón fuera de la tarjeta')
                if void_card_buttons(o)[0][0] - o.rightX - 10 < width('✦ GRIETA DEL VOID ✦'):
                    errs.append('grieta del Void: no cabe el título')
                y0 += RIFT_H
                vis = max(1, (o.by + o.bh - y0) // RIFT_H)
                for i in range(min(6, vis)):
                    card = (o.rightX, y0 + i * RIFT_H, o.rightW, RIFT_H - 3)
                    bs = rift_buttons(o, i)
                    for b in bs:
                        if not inside(b, card):
                            errs.append(f'grieta {i}: botón fuera de la tarjeta')
                    for a in range(3):
                        for b in range(a + 1, 3):
                            if inter(bs[a], bs[b]):
                                errs.append(f'grieta {i}: botones pisados')
                    if bs[0][0] - o.rightX - 10 < 80:
                        errs.append(f'grieta {i}: poco sitio para el texto')
    return sorted(set(errs))


def draw(w, h, path, tab, banner):
    from PIL import Image, ImageDraw
    k = 3
    o, ps = pieces(w, h, tab, banner)
    img = Image.new('RGB', (w * k, h * k), (20, 22, 28))
    d = ImageDraw.Draw(img)
    colors = {'top': (31, 35, 44), 'head': (90, 96, 110), 'body': (50, 56, 70), 'foot': (120, 100, 40)}
    d.rectangle([o.px * k, o.py * k, (o.px + o.pw) * k - 1, (o.py + o.ph) * k - 1], fill=(25, 28, 35), outline=(44, 49, 61))
    for n, r, g in ps:
        x, y, rw, rh = r
        d.rectangle([x * k, y * k, (x + rw) * k - 1, (y + rh) * k - 1], fill=colors[g], outline=(140, 150, 170))
        d.text((x * k + 3, y * k + 2), n, fill=(235, 238, 244))
    if tab == 2:
        pass
    elif tab == 0:
        for i in range(ROWS):
            for plus in (False, True):
                x, y, s, _ = step_rect(o, i, plus)
                d.rectangle([x * k, y * k, (x + s) * k - 1, (y + s) * k - 1], fill=(52, 57, 70), outline=(200, 200, 200))
    else:
        top = o.tabsY + TAB + 5
        y0 = top + BTN + 5
        vis = max(1, (o.by + o.bh - y0) // RIFT_H)
        for i in range(min(6, vis)):
            for x, y, bw, bh in rift_buttons(o, i):
                d.rectangle([x * k, y * k, (x + bw) * k - 1, (y + bh) * k - 1], fill=(52, 57, 70), outline=(200, 200, 200))
    img.save(path)


SIZES = [(427, 240), (455, 256), (480, 270), (512, 288), (640, 360), (683, 384), (960, 540), (1280, 720)]


def main():
    png = sys.argv[sys.argv.index('--png') + 1] if '--png' in sys.argv else None
    bad = 0
    for w, h in SIZES:
        errs = check(w, h)
        o = layout(w, h, False)
        print(f'{w}x{h}: panel {o.pw}x{o.ph}, columnas {o.listW}/{o.midW}/{o.rightW}, escenario {o.midW}x{o.stageH}, '
              f'filas de {o.rowH} px, presets {o.chipsPerRow} por fila -> {"OK" if not errs else str(len(errs)) + " fallos"}')
        for e in errs:
            print('   -', e)
        bad += len(errs)
        if png:
            import os
            os.makedirs(png, exist_ok=True)
            draw(w, h, os.path.join(png, f'gui_{w}x{h}_dificultad.png'), 0, True)
            draw(w, h, os.path.join(png, f'gui_{w}x{h}_grietas.png'), 1, False)
            draw(w, h, os.path.join(png, f'gui_{w}x{h}_void.png'), 2, False)
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
