"""Lee un .schem (Sponge v2 o v3): (raíz sin tipos, bloques[y, z, x], paleta por índice, entidades de bloque)."""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtio  # noqa: E402


def leer(ruta):
    _, raiz = nbtio.read_file(ruta)
    r = nbtio.plain(raiz)
    if isinstance(r.get('Schematic'), dict):  # v3 va dentro de «Schematic»
        r = r['Schematic']
    W, H, L = r['Width'] & 0xFFFF, r['Height'] & 0xFFFF, r['Length'] & 0xFFFF
    if r['Version'] == 3:
        b = r['Blocks']
        paleta, datos, bes = b['Palette'], b['Data'], b.get('BlockEntities', [])
    else:
        paleta, datos, bes = r['Palette'], r['BlockData'], r.get('BlockEntities', [])
    inv = [None] * (max(paleta.values()) + 1)
    for k, v in paleta.items():
        inv[v] = k
    vals = []
    i = 0
    while i < len(datos):  # varints
        v = s = 0
        while True:
            c = datos[i]
            i += 1
            v |= (c & 0x7F) << s
            s += 7
            if c < 0x80:
                break
        vals.append(v)
    if len(vals) != W * H * L:
        raise ValueError(f'{ruta}: {len(vals)} bloques y debería haber {W * H * L}')
    return r, np.array(vals, np.int32).reshape(H, L, W), inv, bes
