"""Compara lo que pegó WorldEdit (el mundo ya guardado) con el .schem: bloque a bloque, entidades de bloque y entidades.

    python3 -I comparar.py <mundo> <archivo.schem> <x> <y> <z> [<archivo.schem> <x> <y> <z> ...]

x y z: dónde estaba el jugador al hacer //paste (los pies). Hierba ↔ tierra no cuenta como fallo: es el juego (la hierba
bajo un bloque pasa a tierra y la de al lado se extiende a la tierra con luz, igual que en el mapa original). Termina con
«FALLOS n».
"""
import os
import sys
from collections import Counter

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtio  # noqa: E402
from mundo import Mundo  # noqa: E402
from schem import leer  # noqa: E402

w = Mundo(sys.argv[1])
fallos = 0
args = sys.argv[2:]
for i in range(0, len(args), 4):
    ruta, pies = args[i], tuple(int(v) for v in args[i + 1:i + 4])
    r, arr, inv, bes = leer(ruta)
    m = r.get('Metadata', {})
    mx, my, mz = pies[0] + m.get('WEOffsetX', 0), pies[1] + m.get('WEOffsetY', 0), pies[2] + m.get('WEOffsetZ', 0)
    H, L, W = arr.shape
    got, gpal, gbes = w.caja(mx, my, mz, mx + W - 1, my + H - 1, mz + L - 1)
    a = np.array(inv, dtype=object)[arr]
    g = np.array(gpal, dtype=object)[got]
    ne = a != g
    HIERBA, TIERRA = 'minecraft:grass_block[snowy=false]', 'minecraft:dirt'
    hierba = ne & (((a == HIERBA) & (g == TIERRA)) | ((a == TIERRA) & (g == HIERBA)))
    ne &= ~hierba
    print(f'{os.path.basename(ruta)} pegado con el mínimo en {(mx, my, mz)}: {int(ne.sum())} bloques distintos de {ne.size} '
          f'({int((arr != 0).sum())} sin aire; hierba ↔ tierra por el juego: {int(hierba.sum())})')
    for (x, y), n in Counter(zip(a[ne].tolist(), g[ne].tolist())).most_common(8):
        print(f'    {n} × {x} → {y}')
    fallos += int(ne.sum())
    enmundo = {(b['x'][1] - mx, b['y'][1] - my, b['z'][1] - mz): nbtio.plain((10, b)) for b in gbes}
    mal = 0
    for b in bes:
        o = enmundo.get(tuple(b['Pos']))
        quiero = {k: v for k, v in b.items() if k not in ('Pos', 'Id')}
        tengo = None if o is None else {k: v for k, v in o.items() if k not in ('x', 'y', 'z', 'id', 'keepPacked')}
        if quiero != tengo:
            mal += 1
            if mal <= 3:
                print('    entidad de bloque distinta', b['Id'], b['Pos'], str(tengo)[:200])
    print(f'    entidades de bloque: {len(bes)} en el schem, {len(enmundo)} en el mundo, {mal} distintas o que faltan')
    fallos += mal + abs(len(bes) - len(enmundo))

    def clave(e, off=(0, 0, 0)):
        return (e['Id'], e.get('variant'), e.get('facing'), tuple(round(v - o, 3) for v, o in zip(e['Pos'], off)))
    quiero = sorted(clave(e) for e in r.get('Entities', []))
    tengo = sorted(clave({**nbtio.plain((10, e)), 'Id': nbtio.plain(e['id'])}, (mx, my, mz))
                   for e in w.entidades(mx, my, mz, mx + W - 1, my + H - 1, mz + L - 1))
    print(f'    entidades: {len(quiero)} en el schem, {len(tengo)} en el mundo, '
          + ('iguales' if quiero == tengo else f'DISTINTAS {tengo}'))
    fallos += quiero != tengo
print('FALLOS', fallos)
sys.exit(1 if fallos else 0)
