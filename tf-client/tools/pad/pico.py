"""Pico de diamante 32x32 adaptado de la estructura del pico de Minecraft / Faithful 32x:
cabeza en media luna fina (núcleo de 3 px que se afina a 1 en las puntas), simétrica respecto al eje del mango
(x+y=31); el mango, largo y de 3 px, atraviesa la cabeza y su punta asoma por fuera del centro del arco."""
from kit import *

# Brazo de arriba: columna x -> (fila de arriba, fila de abajo) del núcleo. El de la derecha es su reflejo.
COLS = {10: (6, 6), 11: (5, 6), 12: (5, 7)}
for _x in range(13, 19):
    COLS[_x] = (5, 7)
COLS.update({19: (5, 8), 20: (6, 8), 21: (6, 9), 22: (6, 9), 23: (7, 8), 24: (7, 7)})


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
    upper = {(x, y) for (x, y) in head if x + y < 31}
    lower = {(x, y) for (x, y) in head if x + y > 31}
    # mango: 3 px en diagonal desde abajo a la izquierda hasta asomar por encima de la cabeza
    handle = {(x, y) for y in range(32) for x in range(32) if 30 <= x + y <= 32 and 3 <= x <= 25 and y <= 28}
    handle -= {(3, 27)}
    for x, y in handle:
        s = x + y
        col = (WOOD[1], WOOD[2], WOOD[3])[s - 30]
        ic.px(x, y, col)
    # vetas y brillos de la madera
    for x in (7, 11, 15, 19):
        ic.px(x, 30 - x, WOOD[0])
    for x in (9, 13, 17):
        ic.px(x, 32 - x, WOOD[4])
    # pomo
    ic.px(3, 28, WOOD[3]); ic.px(4, 28, WOOD[4]); ic.px(3, 29, WOOD[4])
    # cabeza (encima del mango): luz arriba-izquierda
    for x, y in head:
        col = DIAM[2]
        if (x, y) in upper or x + y == 31:
            if (x, y - 1) not in head: col = DIAM[0]
            elif (x, y + 1) not in head: col = DIAM[3]
            else: col = DIAM[1]
        else:
            if (x - 1, y) not in head: col = DIAM[1]
            elif (x + 1, y) not in head: col = DIAM[3]
            else: col = DIAM[2]
            if (x, y + 1) not in head and (x + 1, y) not in head: col = DIAM[4]
        ic.px(x, y, col)
    # puntas: el último píxel más claro, como el filo
    ic.px(10, 6, DIAM[1]); ic.px(25, 21, DIAM[3])
    # brillos sobre el filo de arriba y la curva
    for x, y in ((13, 5), (14, 5), (15, 5), (21, 6), (22, 6)):
        if (x, y) in head: ic.px(x, y, WHITE)
    return ic.outline()


if __name__ == '__main__':
    im = pico().image()
    im.save('ic_oficios.png')
    review(im).save('rev_oficios.png')
