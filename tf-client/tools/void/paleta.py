"""La paleta del Void (TF Client 1.3.97): que todo sea roca del Vacío (el dueño: «no pongas bloques del End, el Void no
es el End; no pongas bloques de lana, tierra, nada de eso; hay hoyos con lana; todo el Void debe ser congruente»).

  repaletar(d)  la isla del dueño (su mapa del End, hecho de lana, terracota, piedra del End, arcilla y tierra) pasa a
                piedras del Vacío, cada color a la suya (se conservan sus franjas y su forma) y con vetas de ruido 3D:
                  lana gris (el cuerpo)            → pizarra profunda, toba y piedra negra
                  piedra del End                   → basalto liso, basalto y piedra negra
                  terracota cian (lo de arriba)    → toba y basalto liso
                  arcilla                          → calcita y toba (ceniza clara)
                  terracota azul                   → pizarra pulida y pizarra profunda
                  terracota morada                 → piedra negra y obsidiana
                  lana morada                      → obsidiana y obsidiana que llora
                  lana y terracota magenta         → amatista y amatista que brota
                  micelio, tierra                  → basalto liso, pizarra profunda
                  madera de acacia                 → basalto (troncos petrificados)
                  ladrillos y adoquín de piedra    → ladrillos de piedra negra pulida / pizarra (escaleras, losas y muros
                                                     con sus mismas propiedades)
                  vara del End                     → racimo de amatista
  revisar(d, ...)  cuenta lo que no debe haber (fuera del santuario, donde el Velo guarda lo último vivo): piedra del End,
                coro, purpur, varas del End, lana, terracota, tierra, hierba, micelio, nilio, arcilla, sculk, plantas del
                mundo de arriba, sapoluz. isla_alba.py falla si queda algo.
"""
import numpy as np

# base (sin «minecraft:» ni propiedades) → lo nuevo: un bloque, o [(bloque, peso), ...] mezclados con ruido 3D
MAPA = {
    'gray_wool': [('deepslate', 0.6), ('tuff', 0.24), ('blackstone', 0.16)],
    'end_stone': [('smooth_basalt', 0.5), ('blackstone', 0.32), ('basalt[axis=y]', 0.18)],
    'cyan_terracotta': [('tuff', 0.58), ('smooth_basalt', 0.3), ('deepslate', 0.12)],
    'clay': [('calcite', 0.55), ('tuff', 0.45)],
    'blue_terracotta': [('polished_deepslate', 0.6), ('deepslate', 0.4)],
    'purple_terracotta': [('blackstone', 0.7), ('obsidian', 0.3)],
    'purple_wool': [('obsidian', 0.76), ('crying_obsidian', 0.24)],
    'magenta_wool': [('amethyst_block', 0.8), ('budding_amethyst', 0.2)],
    'magenta_terracotta': 'amethyst_block',
    'white_terracotta': 'calcite',
    'white_wool': 'calcite',
    'mycelium': 'smooth_basalt',
    'dirt': 'deepslate',
    'stone_bricks': [('polished_blackstone_bricks', 0.88), ('cracked_polished_blackstone_bricks', 0.12)],
    'chiseled_stone_bricks': 'chiseled_polished_blackstone',
    'cobblestone': 'cobbled_deepslate',
    'magenta_stained_glass': 'purple_stained_glass',
}
# con sus propiedades tal cual
MAPA_PROPS = {
    'acacia_wood': 'basalt',
    'acacia_log': 'basalt',
    'stone_brick_slab': 'polished_blackstone_brick_slab',
    'cobblestone_stairs': 'cobbled_deepslate_stairs',
    'cobblestone_wall': 'cobbled_deepslate_wall',
}
# del todo distinto
MAPA_ESTADO = {
    'end_rod': 'amethyst_cluster[facing=up,waterlogged=false]',
}

# lo que no debe haber fuera del santuario (por el principio del nombre o el nombre entero)
PROHIBIDOS = ('end_stone', 'end_rod', 'purpur', 'chorus', 'dragon', '_wool', 'terracotta', 'dirt', 'grass_block', 'podzol',
              'mycelium', 'nylium', 'clay', 'sculk', 'cave_vines', 'warped_', 'crimson_', 'nether_sprouts', 'dead_bush',
              'froglight', 'moss_block', 'azalea', 'cherry', 'pink_petals', 'sand', 'gravel', 'pointed_dripstone',
              'soul_soil', 'soul_fire', 'shulker')


def base(name):
    return name.split('[')[0].replace('minecraft:', '')


def props(name):
    return name[name.index('['):] if '[' in name else ''


def _noise3(x, y, z, scale, seed):
    """Ruido de valor 3D suave (0..1) en esos puntos."""
    def h(ix, iy, iz):
        v = (ix * 374761393 + iy * 1103515245 + iz * 668265263 + seed * 1442695041) & 0xFFFFFFFF
        v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
        return ((v ^ (v >> 16)) & 0xFFFFFFFF) / 0xFFFFFFFF
    fx, fy, fz = x / scale, y / scale, z / scale
    ix, iy, iz = np.floor(fx).astype(np.int64), np.floor(fy).astype(np.int64), np.floor(fz).astype(np.int64)
    tx, ty, tz = fx - ix, fy - iy, fz - iz
    tx, ty, tz = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty), tz * tz * (3 - 2 * tz)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                wgt = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                out = out + wgt * h(ix + dx, iy + dy, iz + dz)
    return out


def repaletar(w):
    """La isla que hay ahora (la del dueño) a la paleta del Void. w: isla_alba.World."""
    d = w.d
    g = d['g']
    names = list(d['names'])
    cambios = 0
    for i, n in enumerate(names):
        if i == 0:
            continue
        b = base(n)
        if b in MAPA_ESTADO:
            new = MAPA_ESTADO[b]
        elif b in MAPA_PROPS:
            new = MAPA_PROPS[b] + props(n)
        elif b in MAPA:
            new = MAPA[b]
        else:
            continue
        sel = np.nonzero(g == i)
        if len(sel[0]) == 0:
            continue
        cambios += len(sel[0])
        if isinstance(new, str):
            g[sel] = w.pal(new)
            continue
        # mezcla con ruido 3D (vetas que cruzan los bloques, como estratos)
        y, z, x = sel
        n1 = _noise3(x.astype(float), y.astype(float) * 1.6, z.astype(float), 9.0, 7 + i)
        n2 = _noise3(x.astype(float), y.astype(float), z.astype(float), 3.0, 91 + i)
        v = 0.72 * n1 + 0.28 * n2
        # el valor por cuantiles: así cada uno sale más o menos con su peso
        order = np.argsort(v)
        acc = 0.0
        out = np.empty(len(v), np.int64)
        lo = 0
        for blk, wgt in new:
            acc += wgt
            hi = len(v) if blk == new[-1][0] else int(round(acc * len(v)))
            out[order[lo:hi]] = w.pal(blk)
            lo = hi
        g[sel] = out
    return cambios


def revisar(d, santuario):
    """{bloque: cuántos} de lo prohibido fuera del santuario (cx, cz, radio)."""
    g = d['g']
    cnt = np.bincount(g.ravel(), minlength=len(d['names']))
    malos = [i for i, n in enumerate(d['names']) if i and cnt[i] and any(p in base(n) for p in PROHIBIDOS)]
    out = {}
    if not malos:
        return out
    cx, cz, r = santuario
    for i in malos:
        y, z, x = np.nonzero(g == i)
        far = np.hypot(x + d['ox'] - cx, z + d['oz'] - cz) > r
        if far.any():
            out[d['names'][i]] = int(far.sum())
    return out
