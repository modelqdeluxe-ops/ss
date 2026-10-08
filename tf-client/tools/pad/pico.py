"""Pico de diamante 32x32, estudiado sobre los picos de Minecraft, Terraria y Stardew:
brazos finos (4 px) que se afinan en punta y caen un poco, esquina redondeada arriba a la derecha,
casquillo de oro donde entra el mango, mango largo de 3 px. La cabeza es simétrica respecto al eje del
mango (x+y=31): se dibuja el brazo de arriba y se refleja."""
from kit import *

# brazo de arriba: columna x -> (fila de arriba, fila de abajo)
COLS = {5: (8, 9), 6: (7, 9), 7: (6, 9), 8: (6, 9), 9: (5, 9), 10: (5, 9), 11: (5, 9), 12: (4, 9), 13: (4, 9)}
for _x in range(14, 23):
    COLS[_x] = (4, 10) if _x >= 18 else (4, 9)
COLS.update({23: (4, 8), 24: (4, 7), 25: (5, 6), 26: (5, 5)})


def mirror(x, y):
    return 31 - y, 31 - x


def pico():
    ic = Icon()
    head = set()
    for x, (a, b) in COLS.items():
        for y in range(a, b + 1):
            if x + y <= 31:
                head.add((x, y))
                head.add(mirror(x, y))
    # mango
    handle = {(x, y) for y in range(32) for x in range(32)
              if 30 <= x + y <= 33 and 3 <= x <= 21 and y <= 28 and (x, y) not in head}
    for x, y in handle:
        ic.px(x, y, (WOOD[1], WOOD[2], WOOD[2], WOOD[3])[x + y - 30])
    for x in (9, 12, 15):
        ic.px(x, 30 - x, WOOD[0])
    # empuñadura: vueltas de cuero
    LEATHER = [(206, 132, 92), (160, 92, 64), (110, 60, 46)]
    for x, y in handle:
        if 4 <= x <= 8:
            k = x + y - 30
            k = min(2, k)
            ic.px(x, y, LEATHER[k] if (x - y) % 4 in (0, 1) else LEATHER[min(2, k + 1)])
    # pomo de oro
    for x, y, c in ((2, 28, GOLD[1]), (3, 28, GOLD[2]), (2, 29, GOLD[2]), (3, 29, GOLD[3]), (4, 29, GOLD[3]), (3, 30, GOLD[4]), (2, 30, GOLD[3])):
        ic.px(x, y, c)
    # cabeza
    for x, y in head:
        up = (x, y - 1) not in head
        dn = (x, y + 1) not in head
        lf = (x - 1, y) not in head
        rt = (x + 1, y) not in head
        if x + y <= 31:
            if up: col = DIAM[0]
            elif (x, y - 2) not in head: col = DIAM[1]
            elif dn: col = DIAM[3]
            else: col = DIAM[2]
            if lf and not up: col = DIAM[1]
        else:
            if rt and dn: col = DIAM[4]
            elif rt: col = DIAM[3]
            elif lf: col = DIAM[1]
            elif dn: col = DIAM[3]
            else: col = DIAM[2]
        ic.px(x, y, col)
    # brillos
    for x, y in ((10, 5), (11, 5), (12, 5), (25, 6)):
        ic.px(x, y, WHITE)
    # casquillo de oro con gema donde entra el mango
    cx, cy = 21, 10
    sock = {(x, y) for y in range(32) for x in range(32) if abs(x - cx) + abs(y - cy) <= 3}
    for x, y in sock:
        dx, dy = x - cx, y - cy
        col = GOLD[2]
        if dx + dy <= -2 or (dx < 0 and dy <= 0 and abs(dx) + abs(dy) == 3): col = GOLD[1]
        if dx + dy >= 2: col = GOLD[3]
        if abs(dx) + abs(dy) == 3 and dx + dy >= 1: col = GOLD[4]
        ic.px(x, y, col)
    ic.px(cx - 2, cy - 1, GOLD[0]); ic.px(cx - 1, cy - 2, GOLD[0])
    gem = {(cx, cy - 1), (cx - 1, cy), (cx, cy), (cx + 1, cy), (cx, cy + 1)}
    for x, y in gem:
        ic.px(x, y, RED[2])
    ic.px(cx, cy - 1, RED[1]); ic.px(cx - 1, cy, RED[1]); ic.px(cx, cy, WHITE)
    ic.px(cx + 1, cy, RED[3]); ic.px(cx, cy + 1, RED[3])
    # contorno del casquillo sobre la cabeza y el mango
    for x, y in {(x, y) for y in range(32) for x in range(32) if abs(x - cx) + abs(y - cy) == 4}:
        if (x, y) in head or (x, y) in handle:
            ic.px(x, y, OUT)
    return ic.outline()


if __name__ == '__main__':
    im = pico().image()
    im.save('ic_oficios.png')
    review(im).save('rev_oficios.png')
