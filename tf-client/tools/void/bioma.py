"""El Páramo del Alba Rota (1.3.96): el bioma del Void entre la isla de la grieta y el santuario.

    (lo llama isla_alba.py, que también se puede pasar solo: python3 -I tools/void/isla_alba.py [--preview carpeta])

El dueño: «hazte un bioma completo: estará el santuario, y en medio del santuario y la isla principal estará un bioma
completo con estas criaturas hostiles; genéralo bien, decoración del Void; quiero un bioma completo grandecito, y
después ya viene la isla principal de la grieta; pozos de luz en el bioma». Se viene del santuario (al norte) por el
Puente de Luz, se cruza el Páramo por el Sendero de las Esquirlas y se llega a la isla de la grieta (al sur) por el
Puente de Piedra. Lo que hay:

  - La tierra: una masa flotante de ~240 × 230 bloques (varias lomas unidas, borde con ruido, colinas suaves) con la
    cara de arriba a ~66-72 y por debajo un cono irregular de piedra del End, piedra negra, toba y obsidiana que acaba
    en estalactitas; de su cara de abajo cuelgan lianas con bayas que brillan, amatistas y espeleotemas.
  - Zonas (con bordes de ruido): el Bosque de Coros (noroeste: piedra del End y árboles de coro), los Campos de
    Amatista (noreste: calcita, agujas y racimos de amatista), el Páramo de Obsidiana (sureste: piedra negra, basalto,
    obsidiana que llora, agujas de obsidiana y braseros de fuego de alma), las Ruinas de la Orden (suroeste: un templo
    de cuarzo roto, columnas caídas, sculk que lo cubre) y, por todo lo demás, el páramo (piedra del End con sculk y
    nilio distorsionado con raíces, brotes y enredaderas).
  - La Herida: en el centro, un agujero que atraviesa la isla entera (se ve el vacío y el agujero negro), con el borde
    de obsidiana que llora, amatistas y columnas de purpur rotas. Y cuatro grietas largas y estrechas que también la
    atraviesan.
  - El Sendero de las Esquirlas: de la Puerta del Páramo (sur) al Puente de Luz (norte), serpenteando; ladrillo de
    piedra del End con borde de piedra negra pulida, farolas de alma y braseros.
  - Los Pozos de Luz (donde cayeron las esquirlas de los once caballeros): 16, a lo largo del sendero y por las zonas;
    suelo llano de cuarzo y calcita, cuatro velas, la luz invisible encima. El cliente dibuja el círculo de luz y su
    columna; el servidor, la esquirla que flota (LightShards). Quedarse en su luz quita la corrosión del Void.
  - Islotes flotando por encima (piedra del End, obsidiana, amatista y lianas).
  - El Puente de Piedra a la isla de la grieta (5 de ancho: la fauna no lo cruza, VoidIsland.faunaWalkable) con la
    Puerta del Páramo (columnas de piedra negra con un dintel roto y farolas).

Todo sale de una semilla fija: pasarlo otra vez da lo mismo (antes se borra la zona entera).
"""
import math
import random

import numpy as np

# La caja del bioma (coordenadas del mundo; norte = z más pequeña)
X0, X1 = -195, 60
Z0, Z1 = -432, -196
SEED = 7319
SOUTH_DOOR = (-64, -206)       # donde llega el Puente de Piedra
NORTH_DOOR = (-64, -419)       # de donde sale el Puente de Luz al santuario
STONE_BRIDGE_Z = (-206, -169)  # el Puente de Piedra: del Páramo a la puerta norte del Umbral (el santuario viejo)
HERIDA = (-64, -312, 11.5)
WELLS = 16


# ------------------------------------------------------------------------------------------- ruido (numpy)

def vhash(ix, iz, seed):
    h = (ix.astype(np.int64) * 374761393 + iz.astype(np.int64) * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFFFF) / 0xFFFFFFFF


def vnoise(x, z, seed):
    xi, zi = np.floor(x), np.floor(z)
    fx, fz = x - xi, z - zi
    fx, fz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    xi, zi = xi.astype(np.int64), zi.astype(np.int64)
    a, b = vhash(xi, zi, seed), vhash(xi + 1, zi, seed)
    c, e = vhash(xi, zi + 1, seed), vhash(xi + 1, zi + 1, seed)
    return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + e * fx) * fz


def fbm(x, z, seed, octaves=4):
    tot, amp, norm = 0.0, 1.0, 0.0
    for i in range(octaves):
        tot = tot + amp * vnoise(x * (2 ** i), z * (2 ** i), seed + 17 * i)
        norm += amp
        amp *= 0.5
    return tot / norm


def smoothstep(a, b, x):
    u = np.clip((x - a) / (b - a), 0, 1)
    return u * u * (3 - 2 * u)


# ------------------------------------------------------------------------------------------- el bioma

class Biome:
    def __init__(self, w):
        self.w = w
        self.rnd = random.Random(SEED)
        xs = np.arange(X0, X1 + 1)
        zs = np.arange(Z0, Z1 + 1)
        self.X, self.Z = np.meshgrid(xs, zs)
        self.NZ, self.NX = self.X.shape
        self.wells = []
        self.trail = []
        self.occupied = np.zeros(self.X.shape, bool)   # sitios ya usados por estructuras

    # ---- utilidades
    def inside(self, x, z):
        return X0 <= x <= X1 and Z0 <= z <= Z1 and self.mask[z - Z0, x - X0]

    def h(self, x, z):
        return int(self.H[z - Z0, x - X0])

    def put(self, x, y, z, name):
        self.w.put(x, y, z, name)

    def get(self, x, y, z):
        return self.w.get(x, y, z)

    def air(self, x, y, z):
        return self.get(x, y, z) == 'minecraft:air'

    def dist_trail(self, x, z):
        return min((math.hypot(x - px, z - pz) for px, pz in self.trail), default=1e9)

    # ---- la forma
    def shape(self, main_ground):
        from scipy import ndimage
        X, Z = self.X.astype(float), self.Z.astype(float)
        blobs = [(-64, -314, 112, 102), (-142, -262, 50, 46), (16, -362, 48, 54), (-130, -392, 48, 38),
                 (8, -250, 42, 36), (-64, -404, 44, 26), (-64, -226, 30, 26)]
        f = np.full(X.shape, -9.0)
        for cx, cz, rx, rz in blobs:
            f = np.maximum(f, 1 - ((X - cx) / rx) ** 2 - ((Z - cz) / rz) ** 2)
        f = f + 0.42 * (fbm(X / 38, Z / 38, SEED + 1) - 0.5) + 0.14 * (fbm(X / 9, Z / 9, SEED + 2) - 0.5)
        mask = f > 0
        # lejos de la isla de la grieta (menos donde llega el puente)
        if main_ground is not None:
            far = ndimage.distance_transform_edt(~main_ground)
            mask &= far >= 12
        # la tierra del sur llega hasta la puerta del puente
        sx, sz = SOUTH_DOOR
        mask |= (np.abs(X - sx) <= 7) & (Z >= sz - 14) & (Z <= sz) & (main_ground is None or True)
        # la mayor pieza
        lab, n = ndimage.label(mask)
        if n > 1:
            sizes = ndimage.sum(mask, lab, range(1, n + 1))
            mask = lab == (1 + int(np.argmax(sizes)))
        self.mask = mask
        self.dedge = ndimage.distance_transform_edt(mask)

    def make_trail(self):
        pts = [SOUTH_DOOR, (-52, -232), (-58, -258), (-84, -282), (-98, -306), (-96, -334), (-80, -356), (-56, -374),
               (-48, -394), (-60, -410), NORTH_DOOR]
        out = []
        for (ax, az), (bx, bz) in zip(pts, pts[1:]):
            n = int(math.hypot(bx - ax, bz - az))
            for i in range(n):
                t = i / n
                # (suave: Catmull-Rom sin más; con este paso basta una línea con un poco de vaivén)
                x = ax + (bx - ax) * t + 2.2 * math.sin(t * math.pi * 2 + ax * 0.1)
                z = az + (bz - az) * t
                out.append((x, z))
        out.append(NORTH_DOOR)
        self.trail = out

    def heights(self):
        from scipy import ndimage
        X, Z = self.X.astype(float), self.Z.astype(float)
        north = np.clip((Z1 - Z) / (Z1 - Z0), 0, 1)
        base = 66 + 3.5 * north
        hills = 7.5 * (fbm(X / 52, Z / 52, SEED + 3) - 0.5) * 2 + 2.5 * (fbm(X / 17, Z / 17, SEED + 4) - 0.5) * 2
        hills *= smoothstep(0, 16, self.dedge)
        H = base + hills - 1.5 * (1 - smoothstep(0, 6, self.dedge))
        # el sendero, llano y suave; las puertas, a su altura
        tmask = np.zeros(X.shape, bool)
        for px, pz in self.trail[::3]:
            ix, iz = int(round(px)) - X0, int(round(pz)) - Z0
            tmask[max(0, iz - 6):iz + 7, max(0, ix - 6):ix + 7] = True
        dt = ndimage.distance_transform_edt(~tmask)
        Hs = ndimage.gaussian_filter(H, 7)
        k = 1 - smoothstep(0, 9, dt)
        H = H * (1 - k) + Hs * k
        for (dx, dz), hh in ((SOUTH_DOOR, 66.0), (NORTH_DOOR, 69.0)):
            d = np.hypot(X - dx, Z - dz)
            k = 1 - smoothstep(6, 26, d)
            H = H * (1 - k) + hh * k
        self.H = np.round(H).astype(int)
        self.H[~self.mask] = -1

    def flatten(self, cx, cz, r, level=None):
        d = np.hypot(self.X - cx, self.Z - cz)
        sel = (d <= r) & self.mask
        if not sel.any():
            return None
        lv = int(round(np.mean(self.H[sel]))) if level is None else level
        ring = (d <= r + 4) & self.mask & ~sel
        k = 1 - smoothstep(r, r + 4, d[ring])
        self.H[ring] = np.round(self.H[ring] * (1 - k) + lv * k).astype(int)
        self.H[sel] = lv
        return lv

    def zones(self):
        X, Z = self.X.astype(float), self.Z.astype(float)
        centers = [('coros', -134, -362, 60), ('amatista', 14, -352, 52), ('obsidiana', 4, -252, 52), ('ruinas', -130, -262, 34)]
        best = np.full(X.shape, 9.0)
        zone = np.full(X.shape, 'paramo', dtype=object)
        for k, (name, cx, cz, r) in enumerate(centers):
            sc = np.hypot(X - cx, Z - cz) / r + 0.4 * (fbm(X / 26, Z / 26, SEED + 40 + k) - 0.5)
            sel = (sc < 1) & (sc < best)
            zone[sel] = name
            best = np.where(sel, sc, best)
        self.zone = zone

    # ---- el volumen
    def fill(self):
        w = self.w
        g = w.d['g']
        oy = w.d['oy']
        X, Z = self.X.astype(float), self.Z.astype(float)
        thick = 5 + 34 * np.clip(self.dedge / 46, 0, 1) ** 0.85 + 6 * (fbm(X / 21, Z / 21, SEED + 5) - 0.5) * 2
        # estalactitas: conos colgando
        rnd = self.rnd
        cones = np.zeros(X.shape)
        ys, xs = np.nonzero(self.dedge > 9)
        for _ in range(150):
            i = rnd.randrange(len(xs))
            cx, cz = X[ys[i], xs[i]], Z[ys[i], xs[i]]
            rad, dep = rnd.uniform(3, 7), rnd.uniform(8, 24)
            d = np.hypot(X - cx, Z - cz)
            cones = np.maximum(cones, dep * np.clip(1 - d / rad, 0, 1) ** 1.3)
        bottom = (self.H - thick - cones).astype(int)
        self.bottom = bottom
        P = w.pal
        n_top = fbm(X / 7, Z / 7, SEED + 6)
        n_patch = fbm(X / 13, Z / 13, SEED + 7)
        n_strata = fbm(X / 30, Z / 30, SEED + 8)
        ids = {}

        def I(name):
            if name not in ids:
                ids[name] = P(name)
            return ids[name]

        for iz in range(self.NZ):
            for ix in range(self.NX):
                if not self.mask[iz, ix]:
                    continue
                top, bot = int(self.H[iz, ix]), int(bottom[iz, ix])
                zn = self.zone[iz, ix]
                a, b, c = n_top[iz, ix], n_patch[iz, ix], n_strata[iz, ix]
                tb, sb = self.surface(zn, a, b)
                gx, gz = ix + X0 - w.d['ox'], iz + Z0 - w.d['oz']
                col = g[:, gz, gx]
                y0 = max(bot, oy) - oy
                mid = (bot + top) // 2 - oy
                # cara de abajo, dos capas
                col[y0:y0 + 2] = I('crying_obsidian') if (a * 7.3 + b) % 1 < 0.18 else I('obsidian')
                # estratos: la mitad de abajo y la de arriba
                low = 'blackstone' if c > 0.55 else ('tuff' if c < 0.3 else 'end_stone')
                high = 'end_stone' if c > 0.42 or zn != 'obsidiana' else 'blackstone'
                if zn == 'amatista':
                    high = 'calcite'
                col[y0 + 2:mid] = I(low)
                col[max(mid, y0 + 2):top - 3 - oy] = I(high)
                col[top - 3 - oy:top - oy] = I(sb)
                col[top - oy] = I(tb)

    def surface(self, zn, a, b):
        """La cara de arriba y lo de debajo según la zona y el ruido."""
        if zn == 'coros':
            if b > 0.7:
                return 'end_stone_bricks', 'end_stone'
            return ('mycelium' if a < 0.42 else 'end_stone'), 'end_stone'
        if zn == 'amatista':
            if b > 0.7:
                return 'amethyst_block', 'calcite'
            return ('smooth_basalt' if a < 0.25 else 'calcite'), 'calcite'
        if zn == 'obsidiana':
            if a > 0.78:
                return 'crying_obsidian', 'obsidian'
            if a > 0.62:
                return 'obsidian', 'blackstone'
            if b > 0.6:
                return 'basalt[axis=y]', 'blackstone'
            return ('smooth_basalt' if a < 0.3 else 'blackstone'), 'blackstone'
        if zn == 'ruinas':
            if b > 0.66:
                return 'sculk', 'end_stone'
            return ('mycelium' if a < 0.5 else 'end_stone'), 'end_stone'
        # el páramo: micelio gris violáceo (suelta esporas) con manchas de piedra del End, sculk y nilio distorsionado
        if b > 0.7:
            return 'warped_nylium', 'end_stone'
        if b < 0.26:
            return 'sculk', 'end_stone'
        if a > 0.78:
            return 'tuff', 'end_stone'
        if a > 0.6:
            return 'end_stone', 'end_stone'
        return 'mycelium', 'end_stone'

    # ---- huecos que atraviesan la isla
    def herida(self):
        cx, cz, r = HERIDA
        w = self.w
        for x in range(int(cx - r - 6), int(cx + r + 7)):
            for z in range(int(cz - r - 6), int(cz + r + 7)):
                if not self.inside(x, z):
                    continue
                ang = math.atan2(z - cz, x - cx)
                rad = r + 1.6 * math.sin(ang * 3 + 1) + 1.1 * math.sin(ang * 7)
                d = math.hypot(x - cx, z - cz)
                if d < rad:
                    for y in range(self.bottom[z - Z0, x - X0] - 1, self.h(x, z) + 2):
                        w.put(x, y, z, None)
                    self.mask[z - Z0, x - X0] = False
                    self.H[z - Z0, x - X0] = -1
                elif d < rad + 2.6:
                    top = self.h(x, z)
                    for y in range(self.bottom[z - Z0, x - X0], top + 1):
                        w.put(x, y, z, 'crying_obsidian' if (x * 7 + y * 3 + z * 5) % 9 == 0 else 'obsidian')
                    if (x * 13 + z * 7) % 5 == 0:
                        w.put(x, top + 1, z, 'amethyst_cluster[facing=up,waterlogged=false]')
                self.occupied[z - Z0, x - X0] |= d < rad + 8
        # columnas de purpur rotas alrededor
        for i in range(9):
            a = i * 2 * math.pi / 9 + 0.3
            x, z = round(cx + math.cos(a) * (r + 6)), round(cz + math.sin(a) * (r + 6))
            if not self.inside(x, z):
                continue
            top = self.h(x, z)
            hh = 2 + (i * 5) % 5
            for y in range(top + 1, top + 1 + hh):
                w.put(x, y, z, 'purpur_pillar[axis=y]')
            if hh >= 5:
                w.put(x, top + 1 + hh, z, 'purpur_slab[type=bottom,waterlogged=false]')

    def chasms(self):
        rnd = self.rnd
        w = self.w
        made = 0
        tries = 0
        while made < 4 and tries < 60:
            tries += 1
            ys, xs = np.nonzero((self.dedge > 22) & self.mask)
            i = rnd.randrange(len(xs))
            x, z = float(X0 + xs[i]), float(Z0 + ys[i])
            ang = rnd.uniform(0, math.pi)
            length = rnd.randint(26, 40)
            pts = []
            ok = True
            for s in range(length):
                ang += rnd.uniform(-0.25, 0.25)
                x += math.cos(ang)
                z += math.sin(ang)
                ix, iz = int(round(x)), int(round(z))
                if (not self.inside(ix, iz) or self.dist_trail(ix, iz) < 9 or self.occupied[iz - Z0, ix - X0]
                        or self.dedge[iz - Z0, ix - X0] < 10):
                    ok = False
                    break
                pts.append((ix, iz, 1.2 + 1.3 * math.sin(math.pi * s / length)))
            if not ok:
                continue
            made += 1
            for ix, iz, wd in pts:
                for dx in range(-3, 4):
                    for dz in range(-3, 4):
                        x2, z2 = ix + dx, iz + dz
                        if not self.inside(x2, z2):
                            continue
                        d = math.hypot(dx, dz)
                        if d <= wd:
                            for y in range(self.bottom[z2 - Z0, x2 - X0] - 1, self.h(x2, z2) + 2):
                                w.put(x2, y, z2, None)
                            self.mask[z2 - Z0, x2 - X0] = False
                            self.H[z2 - Z0, x2 - X0] = -1
                        elif d <= wd + 1.2:
                            top = self.h(x2, z2)
                            w.put(x2, top, z2, 'crying_obsidian' if (x2 + z2) % 3 == 0 else 'obsidian')
                        self.occupied[z2 - Z0, x2 - X0] = True

    # ---- el sendero y lo que hay en él
    def path(self):
        w = self.w
        done = set()
        for i, (px, pz) in enumerate(self.trail):
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    x, z = int(round(px)) + dx, int(round(pz)) + dz
                    if (x, z) in done or not self.inside(x, z):
                        continue
                    d = math.hypot(x - px, z - pz)
                    if d > 2.2:
                        continue
                    done.add((x, z))
                    top = self.h(x, z)
                    edge = d > 1.3
                    hsh = (x * 31 + z * 17) % 11
                    if edge:
                        blk = 'cracked_polished_blackstone_bricks' if hsh == 0 else 'polished_blackstone_bricks'
                    else:
                        blk = 'end_stone_bricks' if hsh else 'chiseled_polished_blackstone'
                    w.put(x, top, z, blk)
                    for y in range(top + 1, top + 4):
                        if not self.air(x, y, z):
                            w.put(x, y, z, None)
                    self.occupied[z - Z0, x - X0] = True
        # farolas de alma a los lados, cada ~16 bloques
        for i in range(8, len(self.trail) - 8, 16):
            (px, pz), (qx, qz) = self.trail[i], self.trail[i + 1]
            nx, nz = -(qz - pz), qx - px
            ln = math.hypot(nx, nz) or 1
            side = 1 if (i // 16) % 2 == 0 else -1
            x, z = int(round(px + side * nx / ln * 3.5)), int(round(pz + side * nz / ln * 3.5))
            if not self.inside(x, z):
                continue
            top = self.h(x, z)
            w.put(x, top + 1, z, 'polished_blackstone_wall')
            w.put(x, top + 2, z, 'polished_blackstone_wall')
            w.put(x, top + 3, z, 'soul_lantern[hanging=false,waterlogged=false]')
            self.occupied[z - Z0, x - X0] = True

    def brazier(self, x, z):
        top = self.h(x, z)
        self.put(x, top + 1, z, 'polished_blackstone')
        self.put(x, top + 2, z, 'soul_soil')
        self.put(x, top + 3, z, 'soul_fire')
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            self.put(x + dx, top + 1, z + dz, 'polished_blackstone_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]'
                     % {(1, 0): 'west', (-1, 0): 'east', (0, 1): 'north', (0, -1): 'south'}[(dx, dz)])
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                if self.inside(x + dx, z + dz):
                    self.occupied[z + dz - Z0, x + dx - X0] = True

    # ---- los pozos de luz
    def place_wells(self):
        rnd = self.rnd
        chosen = []
        # a lo largo del sendero (a un lado), cada ~30 bloques
        for i in range(18, len(self.trail) - 10, 30):
            (px, pz), (qx, qz) = self.trail[i], self.trail[i + 1]
            nx, nz = -(qz - pz), qx - px
            ln = math.hypot(nx, nz) or 1
            side = -1 if (i // 30) % 2 else 1
            x, z = int(round(px + side * nx / ln * 6)), int(round(pz + side * nz / ln * 6))
            if self.inside(x, z) and self.dedge[z - Z0, x - X0] > 7:
                chosen.append((x, z))
        # y por las zonas: lo más lejos posible de los demás
        ys, xs = np.nonzero(self.mask & (self.dedge > 10) & ~self.occupied)
        cand = list(zip(xs + X0, ys + Z0))[::7]
        while len(chosen) < WELLS and cand:
            best, bd = None, -1
            for c in cand:
                m = min((math.hypot(c[0] - p[0], c[1] - p[1]) for p in chosen), default=1e9)
                m = min(m, math.hypot(c[0] - HERIDA[0], c[1] - HERIDA[1]) - HERIDA[2] - 8)
                if m > bd:
                    bd, best = m, c
            if best is None or bd < 22:
                break
            chosen.append(best)
        rnd.shuffle(chosen)
        out = []
        for x, z in chosen:
            lv = self.flatten(x, z, 3.6)
            if lv is None:
                continue
            out.append((x, lv, z))
            for dx in range(-5, 6):
                for dz in range(-5, 6):
                    if self.inside(x + dx, z + dz) and math.hypot(dx, dz) <= 5:
                        self.occupied[z + dz - Z0, x + dx - X0] = True
        self.wells = out

    def build_wells(self, frag_light):
        """Los pozos ya con el terreno hecho: suelo de luz, velas y la luz invisible (la esquirla la pone el servidor)."""
        out = []
        for x, lv, z in self.wells:
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    d = math.hypot(dx, dz)
                    if d > 3.6 or not self.inside(x + dx, z + dz):
                        continue
                    blk = 'smooth_quartz' if d <= 1.6 else ('quartz_bricks' if d <= 2.4 else 'calcite')
                    self.put(x + dx, lv, z + dz, blk)
                    for y in range(lv + 1, lv + 5):
                        self.put(x + dx, y, z + dz, None)
            for dx, dz in ((3, 0), (-3, 0), (0, 3), (0, -3)):
                self.put(x + dx, lv + 1, z + dz, 'candle[candles=4,lit=true,waterlogged=false]')
            self.put(x, lv + 3, z, frag_light)
            # el sitio de la esquirla: 2 por encima del suelo (como en la isla grande)
            out.append((x, lv + 2, z))
        return out

    # ---- vegetación y estructuras por zonas
    def plants(self):
        w = self.w
        for iz in range(self.NZ):
            for ix in range(self.NX):
                if not self.mask[iz, ix] or self.occupied[iz, ix]:
                    continue
                x, z = ix + X0, iz + Z0
                top = int(self.H[iz, ix])
                tb = self.get(x, top, z)
                r = ((x * 73856093) ^ (z * 19349663) ^ SEED) % 1000 / 1000.0
                if not self.air(x, top + 1, z):
                    continue
                if tb.startswith('minecraft:warped_nylium'):
                    if r < 0.4:
                        w.put(x, top + 1, z, 'warped_roots')
                    elif r < 0.62:
                        w.put(x, top + 1, z, 'nether_sprouts')
                    elif r < 0.67:
                        w.put(x, top + 1, z, 'warped_fungus')
                    elif r < 0.72:
                        hh = 1 + int(r * 1000) % 4
                        for k in range(hh - 1):
                            w.put(x, top + 1 + k, z, 'twisting_vines_plant')
                        w.put(x, top + hh, z, 'twisting_vines[age=25]')
                elif tb == 'minecraft:calcite' or tb == 'minecraft:amethyst_block':
                    if r < 0.07:
                        w.put(x, top + 1, z, 'amethyst_cluster[facing=up,waterlogged=false]')
                    elif r < 0.12:
                        w.put(x, top + 1, z, 'large_amethyst_bud[facing=up,waterlogged=false]')
                    elif r < 0.16:
                        w.put(x, top + 1, z, 'medium_amethyst_bud[facing=up,waterlogged=false]')
                elif tb == 'minecraft:end_stone' and self.zone[iz, ix] == 'paramo' and r < 0.012:
                    w.put(x, top + 1, z, 'chorus_flower[age=5]')
                elif tb == 'minecraft:mycelium':
                    if r < 0.025:
                        w.put(x, top + 1, z, 'crimson_roots' if r < 0.004 else 'warped_roots')
                    elif r < 0.04:
                        w.put(x, top + 1, z, 'dead_bush')
                elif tb == 'minecraft:blackstone' and r < 0.02:
                    w.put(x, top + 1, z, 'polished_blackstone_button[face=floor,facing=north,powered=false]')

    def spots(self, zone, count, spacing, margin=8):
        """Sitios de la zona para estructuras, separados entre sí y lejos de lo ocupado."""
        ys, xs = np.nonzero((self.zone == zone) & self.mask & ~self.occupied & (self.dedge > margin))
        if len(xs) == 0:
            return []
        order = list(range(len(xs)))
        self.rnd.shuffle(order)
        out = []
        for i in order:
            x, z = int(xs[i] + X0), int(ys[i] + Z0)
            if all(math.hypot(x - a, z - b) >= spacing for a, b in out) and self.dist_trail(x, z) > 7:
                out.append((x, z))
                if len(out) >= count:
                    break
        return out

    def chorus_tree(self, x, z):
        """Un árbol de coro como los del End (ChorusFlowerBlock.generatePlant, a lo simple) con flores secas en las puntas."""
        rnd = self.rnd
        top = self.h(x, z)
        if self.get(x, top, z) not in ('minecraft:end_stone', 'minecraft:end_stone_bricks'):
            return
        cells, tips = set(), []

        def grow(px, py, pz, depth, length):
            for i in range(length):
                py += 1
                cells.add((px, py, pz))
            if depth >= 4 or py > top + 16:
                tips.append((px, py + 1, pz))
                return
            branches = rnd.randint(1, 3 if depth else 4)
            for _ in range(branches):
                dx, dz = rnd.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
                nx, nz = px + dx, pz + dz
                if (nx, py, nz) in cells:
                    continue
                cells.add((nx, py, nz))
                grow(nx, py, nz, depth + 1, rnd.randint(1, 3))
            if rnd.random() < 0.4:
                tips.append((px, py + 1, pz))

        grow(x, top, z, 0, rnd.randint(2, 5))
        tipset = set(tips)
        for (px, py, pz) in cells:
            if not self.air(px, py, pz):
                return
        for (px, py, pz) in cells:
            props = {}
            for nm, (dx, dy, dz) in (('north', (0, 0, -1)), ('south', (0, 0, 1)), ('east', (1, 0, 0)), ('west', (-1, 0, 0)),
                                     ('up', (0, 1, 0)), ('down', (0, -1, 0))):
                q = (px + dx, py + dy, pz + dz)
                props[nm] = q in cells or q in tipset or (nm == 'down' and py == top + 1)
            st = ','.join(f'{k}={"true" if v else "false"}' for k, v in sorted(props.items()))
            self.put(px, py, pz, f'chorus_plant[{st}]')
        for (px, py, pz) in tipset:
            if (px, py, pz) not in cells and self.air(px, py, pz):
                self.put(px, py, pz, 'chorus_flower[age=5]')
        self.occupied[z - Z0, x - X0] = True

    def amethyst_spire(self, x, z):
        rnd = self.rnd
        top = self.h(x, z)
        hh = rnd.randint(6, 15)
        lean = (rnd.uniform(-0.25, 0.25), rnd.uniform(-0.25, 0.25))
        r0 = rnd.uniform(1.4, 2.4)
        for k in range(hh + 1):
            r = r0 * (1 - k / (hh + 1)) + 0.4
            cx, cz = x + lean[0] * k, z + lean[1] * k
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    if math.hypot(cx - (x + dx), cz - (z + dz)) <= r:
                        self.put(x + dx, top + 1 + k, z + dz, 'amethyst_block')
        tx, tz = round(x + lean[0] * (hh + 1)), round(z + lean[1] * (hh + 1))
        self.put(tx, top + hh + 2, tz, 'amethyst_cluster[facing=up,waterlogged=false]')
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if self.inside(x + dx, z + dz):
                    self.occupied[z + dz - Z0, x + dx - X0] = True

    def needle(self, x, z):
        """Una aguja de obsidiana con vetas de obsidiana que llora."""
        rnd = self.rnd
        top = self.h(x, z)
        hh = rnd.randint(16, 30)
        r0 = rnd.uniform(2.4, 3.6)
        for k in range(hh + 1):
            r = r0 * (1 - k / (hh + 1)) ** 1.2 + 0.3
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    if math.hypot(dx, dz) <= r:
                        cry = ((x + dx) * 5 + k * 3 + (z + dz) * 7) % 11 == 0
                        self.put(x + dx, top + 1 + k, z + dz, 'crying_obsidian' if cry else 'obsidian')
        self.put(x, top + hh + 2, z, 'end_rod[facing=up]')
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                if self.inside(x + dx, z + dz):
                    self.occupied[z + dz - Z0, x + dx - X0] = True

    def basalt_columns(self, x, z):
        rnd = self.rnd
        for _ in range(rnd.randint(4, 8)):
            dx, dz = rnd.randint(-3, 3), rnd.randint(-3, 3)
            if not self.inside(x + dx, z + dz):
                continue
            top = self.h(x + dx, z + dz)
            for k in range(rnd.randint(1, 6)):
                self.put(x + dx, top + 1 + k, z + dz, 'basalt[axis=y]')

    def ruins(self):
        """Las Ruinas de la Orden: un templo de cuarzo roto, cubierto de sculk."""
        rnd = self.rnd
        cx, cz = -132, -262
        lv = self.flatten(cx, cz, 15)
        if lv is None:
            return
        for x in range(cx - 16, cx + 17):
            for z in range(cz - 16, cz + 17):
                d = math.hypot(x - cx, z - cz)
                if d > 15 or not self.inside(x, z):
                    continue
                r = ((x * 92821) ^ (z * 68917)) % 100
                if r < 12:
                    blk = 'sculk'
                elif r < 16:
                    blk = 'end_stone'
                elif d <= 4.5:
                    blk = 'chiseled_quartz_block' if (x + z) % 2 else 'smooth_quartz'
                elif 9.6 <= d <= 10.6:
                    blk = 'chiseled_quartz_block'
                else:
                    blk = 'quartz_bricks'
                self.put(x, lv, z, blk)
                for y in range(lv + 1, lv + 10):
                    self.put(x, y, z, None)
                self.occupied[z - Z0, x - X0] = True
        # doce columnas: unas enteras, otras rotas o caídas
        for i in range(12):
            a = i * math.pi / 6 + 0.26
            x, z = round(cx + math.cos(a) * 12.5), round(cz + math.sin(a) * 12.5)
            kind = rnd.random()
            if kind < 0.25:
                for y in range(lv + 1, lv + 8):
                    self.put(x, y, z, 'quartz_pillar[axis=y]')
                self.put(x, lv + 8, z, 'gold_block')
                self.put(x, lv + 9, z, 'end_rod[facing=up]')
            elif kind < 0.7:
                for y in range(lv + 1, lv + 1 + rnd.randint(1, 5)):
                    self.put(x, y, z, 'quartz_pillar[axis=y]')
                self.put(x, lv + 1, z + 1, 'sculk')
            else:
                # caída: tumbada hacia fuera
                self.put(x, lv + 1, z, 'quartz_pillar[axis=y]')
                ax = 'x' if abs(math.cos(a)) > abs(math.sin(a)) else 'z'
                for k in range(1, rnd.randint(4, 7)):
                    fx, fz = (x + k * (1 if math.cos(a) > 0 else -1), z) if ax == 'x' else (x, z + k * (1 if math.sin(a) > 0 else -1))
                    if self.inside(fx, fz):
                        self.put(fx, self.h(fx, fz) + 1, fz, f'quartz_pillar[axis={ax}]')
        # el pedestal roto del centro: velas y una farola de alma
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                self.put(cx + dx, lv + 1, cz + dz, 'chiseled_quartz_block')
        self.put(cx, lv + 2, cz, 'quartz_pillar[axis=y]')
        self.put(cx, lv + 3, cz, 'soul_lantern[hanging=false,waterlogged=false]')
        for dx, dz in ((1, 1), (-1, -1), (1, -1), (-1, 1)):
            self.put(cx + dx, lv + 2, cz + dz, 'white_candle[candles=3,lit=true,waterlogged=false]')
        # el arco de la entrada (al este, hacia el sendero), roto
        for side in (-2, 2):
            for y in range(lv + 1, lv + 8 - (side > 0) * 3):
                self.put(cx + 16, y, cz + side, 'quartz_pillar[axis=y]')
        for dz in range(-2, 1):
            self.put(cx + 16, lv + 8, cz + dz, 'smooth_quartz_slab[type=bottom,waterlogged=false]')

    def islets(self):
        rnd = self.rnd
        n = 0
        tries = 0
        placed = []
        while n < 14 and tries < 200:
            tries += 1
            ys, xs = np.nonzero(self.mask & (self.dedge > 6))
            i = rnd.randrange(len(xs))
            x, z = int(xs[i] + X0), int(ys[i] + Z0)
            if any(math.hypot(x - a, z - b) < 28 for a, b in placed):
                continue
            cy = self.h(x, z) + rnd.randint(18, 40)
            r = rnd.uniform(3, 6.5)
            placed.append((x, z))
            n += 1
            for dx in range(-7, 8):
                for dz in range(-7, 8):
                    d = math.hypot(dx, dz)
                    if d > r:
                        continue
                    depth = int(2 + (r - d) * 1.4)
                    for k in range(depth):
                        blk = 'end_stone' if k < 2 else ('obsidian' if (dx + dz + k) % 4 == 0 else 'end_stone')
                        self.put(x + dx, cy - k, z + dz, blk)
                    if d < r - 1 and (dx * 3 + dz * 5) % 7 == 0:
                        self.put(x + dx, cy - depth, z + dz, 'amethyst_cluster[facing=down,waterlogged=false]')
                    if (dx * 5 + dz * 11) % 13 == 0 and d < r - 0.5:
                        # lianas con bayas que brillan
                        ln = 2 + (dx * dz) % 4
                        for k in range(ln):
                            self.put(x + dx, cy - depth - k, z + dz, 'cave_vines_plant[berries=true]' if k < ln - 1
                                     else 'cave_vines[age=25,berries=true]')
            if rnd.random() < 0.5:
                self.amethyst_islet_top(x, cy, z)
            else:
                self.put(x, cy + 1, z, 'chorus_flower[age=5]' if rnd.random() < 0.5 else 'amethyst_cluster[facing=up,waterlogged=false]')

    def amethyst_islet_top(self, x, y, z):
        for k in range(1, 4):
            self.put(x, y + k, z, 'amethyst_block')
        self.put(x, y + 4, z, 'amethyst_cluster[facing=up,waterlogged=false]')

    def underside(self):
        """Lo que cuelga por debajo: lianas con bayas que brillan, amatistas y espeleotemas."""
        w = self.w
        for iz in range(self.NZ):
            for ix in range(self.NX):
                if not self.mask[iz, ix]:
                    continue
                x, z = ix + X0, iz + Z0
                bot = int(self.bottom[iz, ix])
                r = ((x * 2654435761) ^ (z * 40503) ^ (SEED * 7)) % 1000 / 1000.0
                if not self.air(x, bot - 1, z):
                    continue
                if r < 0.035:
                    ln = 2 + int(r * 10000) % 7
                    for k in range(ln):
                        w.put(x, bot - 1 - k, z, 'cave_vines_plant[berries=true]' if k < ln - 1 else 'cave_vines[age=25,berries=true]')
                elif r < 0.06:
                    w.put(x, bot - 1, z, 'amethyst_cluster[facing=down,waterlogged=false]')
                elif r < 0.075:
                    w.put(x, bot - 1, z, 'pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]')

    def stone_bridge(self):
        """El Puente de Piedra a la isla de la grieta, con la Puerta del Páramo en este lado."""
        w = self.w
        z0, z1 = STONE_BRIDGE_Z[1], STONE_BRIDGE_Z[0]   # de -168 (sur) a -206 (norte)
        cx = SOUTH_DOOR[0]
        n = abs(z1 - z0)
        for i, z in enumerate(range(z0, z1 - 1, -1)):
            t = i / n
            sag = 4.5 * math.sin(math.pi * t)
            for dx in range(-3, 4):
                x = cx + dx
                # (encima del tablero, nada; por debajo solo donde ya no hay isla grande: la del Umbral llega a z -176)
                for y in range(60 if z <= -177 else 66, 72):
                    w.put(x, y, z, None)
                if abs(dx) == 3:
                    continue
                w.put(x, 66, z, 'end_stone_bricks' if abs(dx) <= 1 else 'polished_blackstone_bricks')
                # por debajo, un arco de roca (más fino en medio)
                depth = int(2 + (1 - math.sin(math.pi * t)) * 6 + (abs(dx) == 2) * -1)
                for k in range(1, depth + 1):
                    w.put(x, 66 - k, z, 'blackstone' if (x + z + k) % 3 else 'end_stone')
            if i % 6 == 3:
                for dx in (-2, 2):
                    w.put(cx + dx, 67, z, 'polished_blackstone_wall')
                    w.put(cx + dx, 68, z, 'soul_lantern[hanging=false,waterlogged=false]')
        # la Puerta del Páramo (en el lado del Páramo)
        gz = SOUTH_DOOR[1] - 2
        top = 66
        for dx in (-4, 4):
            for y in range(top + 1, top + 10):
                w.put(cx + dx, y, gz, 'polished_blackstone_bricks' if y % 3 else 'chiseled_polished_blackstone')
            w.put(cx + dx, top + 10, gz, 'soul_lantern[hanging=false,waterlogged=false]')
        for dx in range(-4, 2):
            w.put(cx + dx, top + 9, gz, 'polished_blackstone_brick_slab[type=bottom,waterlogged=false]')
        w.put(cx - 1, top + 8, gz, 'chain[axis=y,waterlogged=false]')
        w.put(cx - 1, top + 7, gz, 'soul_lantern[hanging=true,waterlogged=false]')
        for dz in range(-3, 4):
            for dx in range(-3, 4):
                if self.inside(cx + dx, gz + dz):
                    self.occupied[gz + dz - Z0, cx + dx - X0] = True

    # ---- todo
    def run(self, main_ground, frag_light):
        w = self.w
        w.grow(X0, X1, Z0, Z1)
        self.shape(main_ground)
        self.make_trail()
        self.zones()
        self.heights()
        self.place_wells()
        self.flatten(-132, -262, 15)
        # (se borra la zona entera: se puede volver a pasar; también el santuario de antes, el de la primera 1.3.96, que
        # estaba aquí, en -64 -224, con su puente)
        g = w.d['g']
        X, Z = self.X, self.Z
        clear = self.mask | (np.hypot(X + 64, Z + 224) <= 30) | ((np.abs(X + 64) <= 3) & (Z <= -177))
        for iz, ix in zip(*np.nonzero(clear)):
            g[:, Z[iz, ix] - w.d['oz'], X[iz, ix] - w.d['ox']] = 0
        self.fill()
        self.herida()
        self.chasms()
        self.stone_bridge()
        self.path()
        well_sites = self.build_wells(frag_light)
        self.ruins()
        for x, z in self.spots('coros', 46, 7):
            self.chorus_tree(x, z)
        for x, z in self.spots('amatista', 16, 13):
            self.amethyst_spire(x, z)
        for x, z in self.spots('obsidiana', 9, 18):
            self.needle(x, z)
        for x, z in self.spots('obsidiana', 8, 12):
            self.basalt_columns(x, z)
        for x, z in self.spots('paramo', 6, 30) + self.spots('obsidiana', 3, 20):
            self.brazier(x, z)
        # braseros junto al sendero, cada ~45 bloques
        for i in range(30, len(self.trail) - 20, 45):
            (px, pz), (qx, qz) = self.trail[i], self.trail[i + 1]
            nx, nz = -(qz - pz), qx - px
            ln = math.hypot(nx, nz) or 1
            x, z = int(round(px - nx / ln * 4.5)), int(round(pz - nz / ln * 4.5))
            if self.inside(x, z) and not self.occupied[z - Z0, x - X0]:
                self.brazier(x, z)
        self.plants()
        self.islets()
        self.underside()
        # dónde empieza la fauna (VoidIsland: por donde anda, desde aquí): lo más lejos del borde y de los huecos (la
        # Herida y las grietas ya hechas)
        from scipy import ndimage
        d = ndimage.distance_transform_edt(self.mask)
        iz, ix = np.unravel_index(int(np.argmax(d)), d.shape)
        seed = (int(ix + X0), int(self.H[iz, ix]) + 1, int(iz + Z0))
        return well_sites, seed
