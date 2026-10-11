"""Iconos de la 1.3.38 (todos a 32x32, con las reglas del pad: contorno azul noche de 1 px, luz arriba a la izquierda,
rampas de 5 tonos y nada pegado al borde del lienzo).

- Oficios: el pico de Minecraft de verdad. Estructura del pico de diamante de vanilla (y de Faithful 32x): la cabeza es
  un arco de circunferencia con centro en la empuñadura (punta arriba a la izquierda, punta abajo a la derecha, más
  gruesa en el medio), el mango de madera en diagonal desde abajo a la izquierda y su punta asomando por fuera del arco.
- TF Pass: un pase (billete) dorado con muescas a los lados, la franja verde de las monedas del gacha y una estrella.
- Gachapón: la máquina de cápsulas (cúpula de cristal llena de cápsulas de colores, cuerpo rojo, ranura de la moneda,
  manivela y la boca por donde sale la cápsula).
- Viajes: mapa plegado en tres con su camino de puntos rojos, la X del tesoro y la brújula.
- Efectos: destello mágico grande con su halo y dos chispas (más lleno y legible que el de antes).
- Cazas: ballesta cargada con la flecha, calcada de la estructura de la de vanilla.
- Misiones: pergamino enrollado con su lista y las marcas verdes.

Para revisar: python3 tools/pad/iconos4.py <carpeta> (los guarda ampliados con rejilla, y una hoja con todos).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kit import DIAM, GOLD, GREEN, OUT, RED, WOOD, Icon, review  # noqa: E402

WHITE = (255, 255, 255)
PAPER = [(255, 252, 236), (250, 238, 200), (232, 214, 162), (198, 170, 112), (150, 120, 76)]
EMER = [(214, 255, 226), (110, 236, 150), (40, 196, 104), (20, 140, 84), (14, 92, 64)]
REDB = [(255, 206, 206), (255, 112, 112), (226, 48, 64), (164, 26, 50), (108, 18, 42)]
GLASS = [(240, 252, 255), (200, 236, 252), (150, 206, 240), (104, 160, 214), (70, 112, 176)]
STEEL = [(255, 255, 255), (222, 230, 242), (176, 188, 208), (124, 136, 164), (84, 92, 124)]
PURPLE = [(246, 220, 255), (214, 150, 255), (170, 90, 240), (120, 54, 196), (82, 36, 150)]
PINK_C = [(255, 230, 246), (255, 160, 220), (246, 96, 190), (196, 56, 150), (140, 36, 120)]
SKY = [(220, 246, 255), (130, 214, 255), (60, 160, 246), (36, 104, 214), (30, 64, 160)]


def shade(ic, m, ramp, light=(-1, -1)):
    """Relleno con volumen: la cara del lado de la luz (arriba a la izquierda) clara, la de enfrente oscura, borde de
    luz de 1 px y sombra de 1 px. light: hacia dónde está la luz."""
    lx, ly = light
    for x, y in m:
        lit = (x + lx, y + ly) not in m          # borde que mira a la luz
        dark = (x - lx, y - ly) not in m         # borde de sombra
        side_l = (x + lx, y) not in m or (x, y + ly) not in m
        side_d = (x - lx, y) not in m or (x, y - ly) not in m
        if lit and not dark:
            c = ramp[0] if side_l and ((x + lx, y) not in m and (x, y + ly) not in m) else ramp[1]
        elif dark and not lit:
            c = ramp[4] if side_d and ((x - lx, y) not in m and (x, y - ly) not in m) else ramp[3]
        elif side_l and not side_d:
            c = ramp[1]
        elif side_d and not side_l:
            c = ramp[3]
        else:
            c = ramp[2]
        ic.px(x, y, c)


def centered(ic):
    """Centra el dibujo en el lienzo (por su caja), para que todos los iconos queden igual de centrados."""
    pts = [(x, y) for y in range(ic.n) for x in range(ic.n) if ic.c[y][x]]
    if not pts:
        return ic
    x0, x1 = min(p[0] for p in pts), max(p[0] for p in pts)
    y0, y1 = min(p[1] for p in pts), max(p[1] for p in pts)
    dx = (ic.n - 1 - x1 - x0) // 2
    dy = (ic.n - 1 - y1 - y0) // 2
    if dx == 0 and dy == 0:
        return ic
    out = Icon(ic.n)
    for x, y in pts:
        out.px(x + dx, y + dy, ic.c[y][x])
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Oficios: el pico
# ---------------------------------------------------------------------------------------------------------------------

def oficios():
    """Pico de diamante como el de Minecraft (medidas del de vanilla al doble): la cabeza es un arco con centro en la
    empuñadura C (abajo a la izquierda), de -71° a -19° (simétrico respecto a -45°, el eje del mango); en el centro
    está a 27,5 de C y es gruesa (6 px) y hacia las puntas se afina hasta 1 px y se curva hacia dentro (hacia el
    mango), como las puntas de un pico de verdad. El mango (3 px) va de C hasta asomar por fuera del arco."""
    ic = Icon()
    cx, cy = 3.5, 28.5
    a0, a1 = math.radians(-73), math.radians(-17)
    amid, half = (a0 + a1) / 2, (a1 - a0) / 2

    def geo(x, y):
        """(t, r, centro del grosor, grosor) o None. t: 0 en el centro del arco, 1 en las puntas."""
        dx, dy = x - cx, y - cy
        r, a = math.hypot(dx, dy), math.atan2(dy, dx)
        if not a0 <= a <= a1:
            return None
        t = abs(a - amid) / half
        mid = 27.2 - 4.6 * t ** 2
        th = 5.6 * (1 - t ** 1.7) + 0.9
        return t, r, mid, th

    def in_head(x, y):
        g = geo(x, y)
        return g is not None and abs(g[1] - g[2]) <= g[3] / 2

    head = ic.mask(in_head)
    ux, uy = math.cos(math.radians(-45)), math.sin(math.radians(-45))

    def along_across(x, y):
        dx, dy = x - cx, y - cy
        return dx * ux + dy * uy, -dx * uy + dy * ux

    handle = ic.mask(lambda x, y: 0.4 <= along_across(x, y)[0] <= 30.6 and abs(along_across(x, y)[1]) <= 1.6)
    for x, y in handle:
        along, across = along_across(x + 0.5, y + 0.5)
        c = WOOD[1] if across < -0.55 else WOOD[3] if across > 0.55 else WOOD[2]
        if int(along) % 7 == 4 and abs(across) <= 0.55:
            c = WOOD[3]  # veta
        ic.px(x, y, c)
    for x, y in head:
        t, r, mid, th = geo(x + 0.5, y + 0.5) or (1, 0, 0, 1)
        f = (r - (mid - th / 2)) / max(0.6, th)  # 0 = filo de dentro (hacia el mango), 1 = lomo de fuera
        c = DIAM[1] if f > 0.74 else DIAM[2] if f > 0.42 else DIAM[3] if f > 0.16 else DIAM[4]
        if t > 0.82:
            c = DIAM[2] if f > 0.5 else DIAM[3]  # las puntas, más lisas
        ic.px(x, y, c)
    # brillos del lomo a los dos lados del centro
    for k in (-0.55, -0.32, 0.34, 0.56):
        a = amid + k * half
        g = geo(cx + 30 * math.cos(a), cy + 30 * math.sin(a))
        r = 27.2 - 4.6 * (abs(k)) ** 2 + (5.6 * (1 - abs(k) ** 1.7) + 0.9) / 2 - 0.7
        px, py = int(cx + r * math.cos(a)), int(cy + r * math.sin(a))
        if (px, py) in head:
            ic.px(px, py, DIAM[0])
    # la punta del mango asoma por fuera del lomo (madera encima de la cabeza)
    for x, y in handle:
        along, across = along_across(x + 0.5, y + 0.5)
        if along > 29.4:
            ic.px(x, y, WOOD[1] if across < 0 else WOOD[2])
    return centered(ic.outline(OUT))


# ---------------------------------------------------------------------------------------------------------------------
# TF Pass
# ---------------------------------------------------------------------------------------------------------------------

def pase():
    """Pase de temporada: billete dorado (muescas redondas a los lados, troquel de puntos) con la franja verde de las
    monedas del gacha, una estrella grande de oro en el centro y una moneda verde en la parte del troquel."""
    ic = Icon()
    x0, y0, x1, y1 = 2, 7, 29, 24
    card = ic.rect(x0, y0, x1, y1)
    for x, y in list(card):
        if x in (x0, x1) and y in (y0, y1):
            card.discard((x, y))
    my = (y0 + y1 + 1) / 2
    notch = ic.disc(x0 - 0.5, my, 3.2) | ic.disc(x1 + 1.5, my, 3.2)
    card -= notch
    shade(ic, card, GOLD)
    band = ic.rect(x0 + 3, y0 + 3, x1 - 3, y1 - 3) & card
    shade(ic, band, EMER)
    # troquel: línea de puntos
    tx = x1 - 8
    for y in range(y0 + 1, y1, 2):
        ic.px(tx, y, GOLD[4] if (tx, y) not in band else EMER[4])
    # estrella de 5 puntas, de oro con su contorno oscuro
    scx, scy, ro, ri = 13.0, 16.0, 7.2, 3.0
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = ro if i % 2 == 0 else ri
        pts.append((scx + r * math.cos(a), scy + r * math.sin(a)))
    star = ic.poly(pts)
    rim = {(x + dx, y + dy) for x, y in star for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))} - star
    for x, y in rim:
        ic.px(x, y, GOLD[4])
    for x, y in star:
        ic.px(x, y, GOLD[1] if x + 0.5 < scx else GOLD[2])
    for x, y in star:
        if (x, y + 1) not in star or ((x + 1, y) not in star and x + 0.5 > scx):
            ic.px(x, y, GOLD[3])
    ic.px(int(scx) - 1, int(scy) - 2, GOLD[0])
    ic.px(int(scx), int(scy) - 4, GOLD[0])
    # moneda verde en el troquel
    coin = ic.disc(x1 - 3.5, my, 2.6)
    for x, y in coin:
        ic.px(x, y, EMER[1] if (x + 0.5) + (y + 0.5) < (x1 - 3.5) + my else EMER[3])
    for x, y in {(x + dx, y + dy) for x, y in coin for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))} - coin:
        ic.px(x, y, EMER[4])
    return centered(ic.outline(OUT))


# ---------------------------------------------------------------------------------------------------------------------
# Gachapón
# ---------------------------------------------------------------------------------------------------------------------

def gachapon():
    """Máquina de cápsulas: cúpula de cristal con seis cápsulas (mitad de color, mitad blanca, con su junta), tapa
    roja, cuerpo rojo con la ranura de la moneda, la manivela plateada y la boca de salida con una cápsula."""
    ic = Icon()
    dcx, dcy, dr = 16, 12, 9.1
    dome = {(x, y) for x, y in ic.disc(dcx, dcy, dr) if y <= 18}
    cap = ic.rect(13, 2, 18, 3)
    ring = ic.rect(6, 18, 25, 19)
    body = ic.rect(7, 20, 24, 27)
    base = ic.rect(5, 28, 26, 29)
    for x, y in dome:
        d = math.hypot(x + 0.5 - 12.5, y + 0.5 - 8) / 13
        ic.px(x, y, GLASS[0] if d < 0.22 else GLASS[1] if d < 0.55 else GLASS[2] if d < 0.85 else GLASS[3])
    caps = [(11, 15, RED), (16, 16, SKY), (21, 15, GOLD), (13, 10, PURPLE), (19, 10, GREEN), (16, 13, PINK_C)]
    for ccx, ccy, ramp in caps:
        m = ic.disc(ccx + 0.5, ccy + 0.5, 2.3) & dome
        for x, y in m:
            if y < ccy:
                c = ramp[1] if x < ccx else ramp[2]
            elif y == ccy:
                c = ramp[3]  # la junta
            else:
                c = (246, 248, 252) if x <= ccx else (204, 212, 228)
            ic.px(x, y, c)
        if (ccx - 1, ccy - 2) in m:
            ic.px(ccx - 1, ccy - 2, WHITE)
    for x, y in ((9, 6), (9, 7), (10, 5), (11, 4), (8, 8)):
        if (x, y) in dome:
            ic.px(x, y, WHITE)
    for x, y in dome:
        if any((x + dx, y + dy) not in dome for dx, dy in ((1, 0), (-1, 0), (0, -1))):
            ic.px(x, y, GLASS[3] if x >= dcx else GLASS[2])
    shade(ic, cap, REDB)
    shade(ic, ring, REDB)
    shade(ic, body, REDB)
    shade(ic, base, STEEL)
    # manivela grande en el centro (disco plateado con su brazo en diagonal)
    knob = ic.disc(16.5, 23, 3.4)
    shade(ic, knob, STEEL)
    for i in range(-2, 3):
        ic.px(16 + i, 23 - i, STEEL[4])
    ic.px(15, 21, WHITE)
    # ranura de la moneda arriba a la derecha (placa con la ranura)
    for y in range(21, 24):
        ic.px(21, y, (40, 44, 70))
    for y in range(20, 25):
        ic.px(22, y, STEEL[2])
    # boca de salida abajo a la izquierda, con una cápsula asomando
    mouth = ic.rect(8, 25, 11, 27)
    for x, y in mouth:
        ic.px(x, y, (70, 22, 44))
    for x, y in ((9, 26), (10, 26), (9, 27), (10, 27)):
        ic.px(x, y, GOLD[1] if y == 26 else (236, 240, 248))
    return centered(ic.outline(OUT))


# ---------------------------------------------------------------------------------------------------------------------
# Viajes: mapa
# ---------------------------------------------------------------------------------------------------------------------

def viajes():
    """Mapa del tesoro plegado en tres paneles (el del medio un poco más alto, como un mapa abierto de verdad), con
    tierra, agua, un camino de puntos rojos y la X; una brújula pequeña encima en la esquina."""
    ic = Icon()
    panels = [ic.poly([(2, 7), (11, 5), (11, 27), (2, 29)]), ic.poly([(11, 5), (21, 7), (21, 29), (11, 27)]),
              ic.poly([(21, 7), (30, 5), (30, 27), (21, 29)])]
    tones = [PAPER[1], PAPER[2], PAPER[1]]
    allm = set()
    for m, base in zip(panels, tones):
        allm |= m
        for x, y in m:
            ic.px(x, y, base)
    # sombra de los pliegues
    for y in range(5, 30):
        for x in (11, 21):
            if (x, y) in allm:
                ic.px(x, y, PAPER[3])
    # tierra y agua
    water = ic.poly([(2, 18), (8, 16), (13, 19), (17, 18), (20, 21), (20, 29), (2, 29)]) & allm
    for x, y in water:
        ic.px(x, y, SKY[2] if (x + y) % 7 else SKY[1])
    land = ic.poly([(22, 9), (29, 8), (29, 18), (24, 17), (22, 13)]) & allm
    for x, y in land:
        ic.px(x, y, GREEN[2] if (x * 3 + y) % 5 else GREEN[1])
    # camino de puntos rojos y la X
    path = [(6, 11), (8, 10), (10, 11), (12, 13), (14, 14), (16, 13), (18, 12)]
    for x, y in path:
        ic.px(x, y, RED[2])
    for i in range(-2, 3):
        ic.px(24 + i, 22 + i, RED[3])
        ic.px(24 + i, 22 - i, RED[3])
    ic.px(24, 22, RED[1])
    # bordes del papel un poco oscuros abajo
    for x, y in allm:
        if (x, y + 1) not in allm:
            ic.px(x, y, PAPER[4])
    return centered(ic.outline(OUT))


# ---------------------------------------------------------------------------------------------------------------------
# Efectos: destello mágico
# ---------------------------------------------------------------------------------------------------------------------

def efectos():
    """Destello de 4 puntas grande (rosa y violeta, con el centro blanco), su halo en rombo y dos chispas."""
    ic = Icon()

    def star4(cx, cy, r, w):
        return ic.mask(lambda x, y: (abs(x - cx) / r) ** 0.7 + (abs(y - cy) / r) ** 0.7 <= 1 and
                       (abs(x - cx) <= w or abs(y - cy) <= w or (abs(x - cx) / r) ** 0.7 + (abs(y - cy) / r) ** 0.7 <= 0.8))

    big = star4(14.5, 16.5, 12.5, 1.2)
    halo = ic.mask(lambda x, y: abs(x - 14.5) + abs(y - 16.5) <= 7.5) - big
    for x, y in halo:
        ic.px(x, y, (250, 214, 255))
    pink = [(255, 255, 255), (255, 196, 238), (246, 110, 206), (190, 64, 186), (130, 44, 160)]
    for x, y in big:
        d = abs(x + 0.5 - 14.5) + abs(y + 0.5 - 16.5)
        ic.px(x, y, pink[0] if d < 2.5 else pink[1] if d < 5 else pink[2] if d < 9 else pink[3])
    for cx, cy, r in ((26, 6, 4.5), (25.5, 25.5, 3.5)):
        m = star4(cx, cy, r, 0.6)
        for x, y in m:
            d = abs(x + 0.5 - cx) + abs(y + 0.5 - cy)
            ic.px(x, y, (255, 255, 230) if d < 1.5 else GOLD[1] if d < 3 else GOLD[2])
    return centered(ic.outline(OUT))


# ---------------------------------------------------------------------------------------------------------------------
# Cazas: ballesta
# ---------------------------------------------------------------------------------------------------------------------

def cazas():
    """Ballesta cargada, en diagonal como la de Minecraft: culata de madera de abajo a la derecha hacia arriba a la
    izquierda, el arco de hierro cruzado y la flecha lista con su punta."""
    ic = Icon()
    # culata (stock): de (26,27) a (9,10), 3 px
    stock = set()
    for x, y in [(x, y) for y in range(32) for x in range(32)]:
        t = ((x + 0.5 - 9) + (y + 0.5 - 10)) / 2
        d = ((x + 0.5 - 9) - (y + 0.5 - 10)) / math.sqrt(2)
        if 0 <= t <= 17 and abs(d) <= 1.7:
            stock.add((x, y))
    for x, y in stock:
        d = ((x + 0.5 - 9) - (y + 0.5 - 10))
        ic.px(x, y, WOOD[1] if d < -0.8 else WOOD[3] if d > 0.8 else WOOD[2])
    # arco: curva perpendicular a la culata, cerca de la parte de delante
    bow = set()
    for i in range(-60, 61):
        s = i / 60
        bx = 12 + s * 10.5 - 3.2 * (1 - s * s) * 0.7
        by = 13 - s * 10.5 - 3.2 * (1 - s * s) * 0.7
        for ox in (0, 1):
            bow.add((int(round(bx)) + ox, int(round(by))))
    for x, y in bow:
        ic.px(x, y, (188, 196, 214) if x + y < 25 else (124, 136, 164))
    # cuerda tensa, de punta a punta del arco pasando por la nuez
    for i in range(0, 15):
        x = 2 + i * (19 - 2) / 14
        y = 24 - i * (24 - 2) / 14
        ic.px(int(round(x)), int(round(y)) + 0, (226, 230, 240))
    # flecha: astil y punta de hierro hacia arriba a la izquierda
    for i in range(0, 14):
        ic.px(8 + i, 9 + i, (232, 208, 160) if i % 3 else (200, 170, 120))
    for x, y in ((5, 6), (6, 6), (5, 7), (6, 7), (7, 7), (6, 8), (7, 8)):
        ic.px(x, y, (230, 236, 248))
    ic.px(4, 5, (255, 255, 255))
    # plumas
    for x, y in ((21, 23), (22, 22), (20, 22)):
        ic.px(x, y, RED[2])
    # gatillo y nuez de metal
    for x, y in ((18, 18), (19, 19), (20, 22), (21, 23)):
        pass
    ic.px(19, 21, (124, 136, 164))
    ic.px(20, 21, (124, 136, 164))
    return centered(ic.outline(OUT))


# ---------------------------------------------------------------------------------------------------------------------
# Misiones: pergamino
# ---------------------------------------------------------------------------------------------------------------------

def misiones():
    """Pergamino desenrollado con sus dos rollos de madera, tres tareas (las dos primeras con marca verde) y un sello
    rojo de lacre abajo a la derecha."""
    ic = Icon()
    sheet = ic.rect(6, 6, 25, 26)
    shade(ic, sheet, PAPER)
    for x, y in sheet:
        if 2 <= ((x - 6) + (y * 3)) % 9 <= 2:
            pass
    rolls = [ic.rect(4, 3, 27, 6), ic.rect(4, 26, 27, 29)]
    for m in rolls:
        shade(ic, m, WOOD)
        for x, y in m:
            if x in (4, 27):
                ic.px(x, y, WOOD[3] if y not in (3, 26) else WOOD[2])
    # tareas: casilla + raya
    for i, y in enumerate((10, 15, 20)):
        box = ic.rect(9, y, 11, y + 2)
        for x, yy in box:
            ic.px(x, yy, (255, 255, 255))
        for x, yy in ((9, y), (10, y), (11, y), (9, y + 1), (9, y + 2)):
            ic.px(x, yy, PAPER[4])
        for x in range(14, 23 - (3 if i == 2 else 0)):
            ic.px(x, y + 1, PAPER[4] if i < 2 else PAPER[3])
        if i < 2:  # marca verde
            for x, yy in ((9, y + 1), (10, y + 2), (11, y + 1), (12, y), (13, y - 1)):
                ic.px(x, yy, GREEN[3])
            ic.px(10, y + 1, GREEN[1])
    # sello de lacre
    seal = ic.disc(22, 22.5, 2.8)
    shade(ic, seal, REDB)
    ic.px(21, 21, REDB[0])
    return centered(ic.outline(OUT))


ICONOS = {'oficios': oficios, 'pase': pase, 'gachapon': gachapon, 'viajes': viajes, 'efectos': efectos, 'misiones': misiones}

if __name__ == '__main__':
    from PIL import Image
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    ims = []
    for name, fn in ICONOS.items():
        im = fn().image()
        review(im).save(os.path.join(out, f'rev4_{name}.png'))
        ims.append(review(im, 8))
    sheet = Image.new('RGBA', (len(ims) * 266, 256), (40, 40, 60, 255))
    for i, im in enumerate(ims):
        sheet.alpha_composite(im, (i * 266, 0))
    sheet.save(os.path.join(out, 'rev4_hoja.png'))
    print('ok')
