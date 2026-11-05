"""Comprueba un .schem contra los bloques de la 1.20.1: cada estado existe con todas sus propiedades y valores válidos,
cada bloque con entidad de bloque la tiene (y ninguna sobra) y los carteles van en el formato de la 1.20.

    python3 -I validar.py <blocks.json> <archivo.schem>...

blocks.json sale del servidor oficial: java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports
(queda en generated/reports/blocks.json). Termina con «PROBLEMAS 0».
"""
import json
import os
import re
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from schem import leer  # noqa: E402

CON_ENTIDAD = re.compile(
    r'(sign|banner|chest|furnace|smoker|hopper|jukebox|skull|head|campfire|beacon|conduit|barrel|lectern|_bed$|spawner|'
    r'shulker_box|dispenser|dropper|brewing_stand|enchanting_table|beehive|bee_nest|bell|end_gateway|command_block|'
    r'structure_block|comparator|daylight_detector|decorated_pot|chiseled_bookshelf|sculk_sensor|sculk_catalyst|'
    r'sculk_shrieker|moving_piston|end_portal$|jigsaw|suspicious_)')

bloques = json.load(open(sys.argv[1]))
mal = 0
for ruta in sys.argv[2:]:
    r, arr, inv, bes = leer(ruta)
    if r['DataVersion'] != 3465:
        print(ruta, 'DataVersion', r['DataVersion'], '(1.20.1 es 3465)')
        mal += 1
    for s in inv:
        n, _, props = s.partition('[')
        if n not in bloques:
            print(ruta, 'NO EXISTE', s)
            mal += 1
            continue
        spec = bloques[n].get('properties', {})
        got = dict(kv.split('=') for kv in props.rstrip(']').split(',')) if props else {}
        if set(got) != set(spec):
            print(ruta, 'PROPIEDADES', s, 'tiene que tener', sorted(spec))
            mal += 1
        for k, v in got.items():
            if k in spec and v not in spec[k]:
                print(ruta, 'VALOR', s, k, spec[k])
                mal += 1
    pos = {tuple(b['Pos']) for b in bes}
    for i, s in enumerate(inv):
        if CON_ENTIDAD.search(s.split('[')[0]):
            for y, z, x in zip(*np.nonzero(arr == i)):
                if (int(x), int(y), int(z)) not in pos:
                    print(ruta, 'SIN ENTIDAD DE BLOQUE', s, (x, y, z))
                    mal += 1
    for b in bes:
        x, y, z = b['Pos']
        if not CON_ENTIDAD.search(inv[arr[y, z, x]].split('[')[0]):
            print(ruta, 'ENTIDAD DE BLOQUE SOBRE', inv[arr[y, z, x]], b['Id'])
            mal += 1
        if 'sign' in b['Id'] and ('Text1' in b or 'front_text' not in b):
            print(ruta, 'CARTEL EN FORMATO VIEJO', b['Pos'])
            mal += 1
    print(f'{os.path.basename(ruta)}: {len(inv)} estados, {len(bes)} entidades de bloque, '
          f'{len(r.get("Entities", []))} entidades')
print('PROBLEMAS', mal)
sys.exit(1 if mal else 0)
