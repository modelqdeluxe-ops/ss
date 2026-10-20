"""Los Fragmentos del Alba Rota (1.3.97): la tierra del Void entre la isla de la grieta y el santuario.

    (lo llama isla_alba.py, que también se puede pasar solo: python3 -I tools/void/isla_alba.py [--preview carpeta])

El dueño (1.3.97): «no pongas bloques del End, el Void no es el End; no pongas lana, tierra, nada de eso; los biomas no
me gustan, hazlos muchísimo más grandes; todo el Void debe ser congruente». Y el lore: lo que queda de esta dimensión
después de que el agujero negro se la tragara; los caballeros del Alba la sostienen con su magia para que no se
derrumbe. Así que es roca del Vacío (pizarra profunda, piedra negra, basalto, toba, calcita, obsidiana, amatista) y, de
lo de los caballeros, cuarzo, oro, velas y faroles de alma. Ni piedra del End, ni coro, ni purpur, ni varas del End, ni
lana, ni tierra, ni micelio, ni plantas del mundo de arriba (paleta.py lo comprueba al acabar).

Una masa de ~600 × 460 bloques (unas cuatro veces la del Páramo de la 1.3.96), de sur a norte:

  - Los Llanos de Ceniza (sur, junto a la isla de la grieta): toba, basalto liso y calcita como ceniza, terrazas suaves,
    árboles petrificados (troncos de basalto con ramas de piedra negra), rocas sueltas y grietas largas que atraviesan
    la tierra hasta el vacío.
  - El Bosque de Cristal (este): calcita y amatista, cristales gigantes de amatista inclinados (de 8 a 26 de alto),
    rocas de amatista que brota y racimos.
  - Las Fauces de Obsidiana (oeste): piedra negra, obsidiana y vetas de obsidiana que llora; colmillos de obsidiana
    curvados de hasta 40 de alto, columnas de basalto y crestas escarpadas.
  - Las Ruinas de la Orden (norte, ante el santuario): suelo de baldosas de pizarra y toba, capillas rotas de cuarzo y
    oro, columnas caídas, estelas de los caballeros con sus velas y una muralla derruida.
  - La Herida (en medio): un cráter que atraviesa la tierra (se ve el vacío y el agujero negro), con escombros que flotan
    dentro, sostenidos por cadenas: la magia de la Orden.
  - El Sendero del Alba: de la Puerta (sur) al Puente de Luz (norte), losas de pizarra con farolas de alma; a lo largo,
    las Anclas del Alba: los Pozos de Luz (pocos: la corrosión es lenta), donde un caballero se hizo luz para sostener el
    fragmento.
  - Fragmentos que flotan por encima, algunos colgados de cadenas.
  - El Puente de Piedra a la isla de la grieta (5 de ancho: la fauna no lo cruza).

Todo sale de una semilla fija: pasarlo otra vez da lo mismo (antes se borra la zona entera).
"""
import math
import random

import numpy as np

# La caja (coordenadas del mundo; norte = z más pequeña)
X0, X1 = -372, 244
Z0, Z1 = -660, -196
SEED = 7397
SOUTH_DOOR = (-64, -206)       # donde llega el Puente de Piedra
NORTH_DOOR = (-64, -652)       # de donde sale el Puente de Luz al santuario
STONE_BRIDGE_Z = (-206, -169)  # el Puente de Piedra: del sur de los Fragmentos a la llegada de la isla de la grieta
HERIDA = (-14, -430, 27)
WELLS = 4                      # Anclas del Alba a lo largo del sendero
# (nombre, centro x, centro z, radio): las cuatro tierras
REGIONS = [('ceniza', -64, -300, 175), ('cristal', 150, -440, 150), ('obsidiana', -280, -440, 150), ('ruinas', -64, -592, 112)]


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


def h01(x, z, s):
    """Un número fijo (0..1) para esa columna."""
    return (((x * 73856093) ^ (z * 19349663) ^ (s * 83492791)) & 0xFFFFFF) / float(0xFFFFFF)


# ------------------------------------------------------------------------------------------- la tierra

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
        self.occupied = np.zeros(self.X.shape, bool)

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

    def occupy(self, x, z, r):
        r = int(math.ceil(r))
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if X0 <= x + dx <= X1 and Z0 <= z + dz <= Z1 and dx * dx + dz * dz <= r * r:
                    self.occupied[z + dz - Z0, x + dx - X0] = True

    def dist_trail(self, x, z):
        return min((math.hypot(x - px, z - pz) for px, pz in self.trail[::2]), default=1e9)

    # ---- la forma
    def shape(self, main_ground):
        from scipy import ndimage
        X, Z = self.X.astype(float), self.Z.astype(float)
        blobs = [(-64, -430, 300, 205), (-64, -300, 150, 95), (-270, -470, 100, 120), (150, -470, 100, 120),
                 (-64, -600, 140, 60), (-170, -330, 80, 70), (60, -330, 80, 70)]
        f = np.full(X.shape, -9.0)
        for cx, cz, rx, rz in blobs:
            f = np.maximum(f, 1 - ((X - cx) / rx) ** 2 - ((Z - cz) / rz) ** 2)
        f = f + 0.85 * (fbm(X / 95, Z / 95, SEED + 1) - 0.5) + 0.3 * (fbm(X / 26, Z / 26, SEED + 2) - 0.5) \
            + 0.12 * (fbm(X / 8, Z / 8, SEED + 12) - 0.5)
        mask = f > 0
        # dentro de la caja con margen
        mask &= (X > X0 + 6) & (X < X1 - 6) & (Z > Z0 + 6) & (Z < Z1 - 3)
        # lejos de la isla de la grieta
        if main_ground is not None:
            far = ndimage.distance_transform_edt(~main_ground)
            mask &= far >= 14
        # la tierra del sur llega hasta la puerta del puente y la del norte hasta el Puente de Luz
        sx, sz = SOUTH_DOOR
        mask |= (np.abs(X - sx) <= 8) & (Z >= sz - 18) & (Z <= sz)
        nx, nz = NORTH_DOOR
        mask |= (np.abs(X - nx) <= 9) & (Z >= nz) & (Z <= nz + 20)
        lab, n = ndimage.label(mask)
        if n > 1:
            sizes = ndimage.sum(mask, lab, range(1, n + 1))
            mask = lab == (1 + int(np.argmax(sizes)))
        self.mask = mask
        self.dedge = ndimage.distance_transform_edt(mask)

    def make_trail(self):
        pts = [SOUTH_DOOR, (-44, -246), (-78, -292), (-112, -338), (-118, -388), (-96, -440), (-104, -488), (-70, -530),
               (-44, -566), (-58, -608), NORTH_DOOR]
        out = []
        for i, ((ax, az), (bx, bz)) in enumerate(zip(pts, pts[1:])):
            n = int(math.hypot(bx - ax, bz - az))
            for k in range(n):
                t = k / n
                # Catmull-Rom entre los puntos (curvas suaves)
                p0 = pts[max(0, i - 1)]
                p3 = pts[min(len(pts) - 1, i + 2)]
                t2, t3 = t * t, t * t * t
                x = 0.5 * ((2 * ax) + (-p0[0] + bx) * t + (2 * p0[0] - 5 * ax + 4 * bx - p3[0]) * t2 + (-p0[0] + 3 * ax - 3 * bx + p3[0]) * t3)
                z = 0.5 * ((2 * az) + (-p0[1] + bz) * t + (2 * p0[1] - 5 * az + 4 * bz - p3[1]) * t2 + (-p0[1] + 3 * az - 3 * bz + p3[1]) * t3)
                out.append((x, z))
        out.append(NORTH_DOOR)
        self.trail = out

    def regions(self):
        """Peso de cada tierra (suave, con ruido en los bordes) y la que manda en cada columna."""
        X, Z = self.X.astype(float), self.Z.astype(float)
        # (las coordenadas deformadas: así los bordes entre tierras serpentean en vez de ir rectos)
        Xw = X + 150 * (fbm(X / 140, Z / 140, SEED + 60) - 0.5) + 40 * (fbm(X / 38, Z / 38, SEED + 61) - 0.5)
        Zw = Z + 150 * (fbm(X / 140, Z / 140, SEED + 62) - 0.5) + 40 * (fbm(X / 38, Z / 38, SEED + 63) - 0.5)
        ws = []
        for k, (name, cx, cz, r) in enumerate(REGIONS):
            d = np.hypot(Xw - cx, Zw - cz) / r + 0.2 * (fbm(X / 24, Z / 24, SEED + 50 + k) - 0.5)
            ws.append(np.exp(-3.0 * d * d))
        W = np.stack(ws)
        W = W / np.maximum(W.sum(0), 1e-9)
        self.weights = W
        self.zone = np.array([r[0] for r in REGIONS], dtype=object)[np.argmax(W, 0)]

    def heights(self):
        from scipy import ndimage
        X, Z = self.X.astype(float), self.Z.astype(float)
        wc, wk, wo, wr = self.weights
        n1 = fbm(X / 62, Z / 62, SEED + 3)
        n2 = fbm(X / 18, Z / 18, SEED + 4)
        ceniza = 3.0 * (n1 - 0.5) * 2 + 1.0 * (n2 - 0.5)
        # (terrazas suaves en los llanos)
        ceniza = ceniza + 0.8 * np.sin(ceniza * math.pi / 2.2)
        cristal = 7.5 * (fbm(X / 48, Z / 48, SEED + 5) - 0.5) * 2 + 2.0 * (n2 - 0.5)
        ridge = 1 - np.abs(2 * fbm(X / 54, Z / 54, SEED + 6) - 1)
        obsid = 13.0 * ridge - 5.0 + 3.5 * (n2 - 0.5) * 2
        ruinas = 2.5 + 2.0 * (n1 - 0.5)
        H = 68 + wc * ceniza + wk * cristal + wo * obsid + wr * ruinas
        # hacia el borde baja un poco
        H = H - 2.0 * (1 - smoothstep(0, 10, self.dedge))
        # el sendero, llano y suave
        tmask = np.zeros(X.shape, bool)
        for px, pz in self.trail[::3]:
            ix, iz = int(round(px)) - X0, int(round(pz)) - Z0
            tmask[max(0, iz - 6):iz + 7, max(0, ix - 6):ix + 7] = True
        dt = ndimage.distance_transform_edt(~tmask)
        Hs = ndimage.gaussian_filter(H, 8)
        k = 1 - smoothstep(0, 10, dt)
        H = H * (1 - k) + Hs * k
        for (dx, dz), hh in ((SOUTH_DOOR, 66.0), (NORTH_DOOR, 69.0)):
            d = np.hypot(X - dx, Z - dz)
            k = 1 - smoothstep(6, 30, d)
            H = H * (1 - k) + hh * k
        self.H = np.round(H).astype(int)
        self.H[~self.mask] = -1

    def flatten(self, cx, cz, r, level=None):
        d = np.hypot(self.X - cx, self.Z - cz)
        sel = (d <= r) & self.mask
        if not sel.any():
            return None
        lv = int(round(np.mean(self.H[sel]))) if level is None else level
        ring = (d <= r + 5) & self.mask & ~sel
        k = 1 - smoothstep(r, r + 5, d[ring])
        self.H[ring] = np.round(self.H[ring] * (1 - k) + lv * k).astype(int)
        self.H[sel] = lv
        return lv

    # ---- el volumen
    def fill(self):
        w = self.w
        g = w.d['g']
        oy = w.d['oy']
        X, Z = self.X.astype(float), self.Z.astype(float)
        thick = 6 + 40 * np.clip(self.dedge / 60, 0, 1) ** 0.8 + 7 * (fbm(X / 23, Z / 23, SEED + 5) - 0.5) * 2
        rnd = self.rnd
        cones = np.zeros(X.shape)
        ys, xs = np.nonzero(self.dedge > 10)
        for _ in range(420):
            i = rnd.randrange(len(xs))
            cx, cz = X[ys[i], xs[i]], Z[ys[i], xs[i]]
            rad, dep = rnd.uniform(3, 9), rnd.uniform(8, 30)
            x0, x1 = int(cx - rad) - X0, int(cx + rad) - X0 + 2
            z0, z1 = int(cz - rad) - Z0, int(cz + rad) - Z0 + 2
            sub = (slice(max(0, z0), z1), slice(max(0, x0), x1))
            d = np.hypot(X[sub] - cx, Z[sub] - cz)
            cones[sub] = np.maximum(cones[sub], dep * np.clip(1 - d / rad, 0, 1) ** 1.3)
        bottom = (self.H - thick - cones).astype(int)
        self.bottom = bottom
        P = w.pal
        n_top = fbm(X / 7, Z / 7, SEED + 6)
        n_patch = fbm(X / 15, Z / 15, SEED + 7)
        n_strata = fbm(X / 34, Z / 34, SEED + 8)
        n_vein = np.abs(2 * fbm(X / 26, Z / 26, SEED + 9) - 1)
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
                a, b, c, v = n_top[iz, ix], n_patch[iz, ix], n_strata[iz, ix], n_vein[iz, ix]
                tb, sb = self.surface(zn, a, b, v)
                gx, gz = ix + X0 - w.d['ox'], iz + Z0 - w.d['oz']
                col = g[:, gz, gx]
                y0 = max(bot, oy) - oy
                mid = (bot + top) // 2 - oy
                # cara de abajo: obsidiana con brillos
                col[y0:y0 + 2] = I('crying_obsidian') if (a * 7.3 + b) % 1 < 0.14 else I('obsidian')
                low = 'blackstone' if c > 0.56 else ('basalt[axis=y]' if c < 0.3 else 'deepslate')
                high = 'tuff' if c > 0.6 else 'deepslate'
                if zn == 'cristal':
                    high = 'calcite' if c > 0.4 else 'tuff'
                elif zn == 'obsidiana':
                    high = 'blackstone'
                col[y0 + 2:mid] = I(low)
                col[max(mid, y0 + 2):top - 3 - oy] = I(high)
                col[top - 3 - oy:top - oy] = I(sb)
                col[top - oy] = I(tb)

    def surface(self, zn, a, b, v):
        """La cara de arriba y lo de debajo según la tierra y el ruido."""
        if zn == 'ceniza':
            if v < 0.018:
                return 'crying_obsidian', 'obsidian'
            if b > 0.7:
                return 'calcite', 'tuff'
            if a > 0.72:
                return 'smooth_basalt', 'tuff'
            if a < 0.22:
                return 'basalt[axis=y]', 'tuff'
            return 'tuff', 'tuff'
        if zn == 'cristal':
            if b > 0.72:
                return 'amethyst_block', 'calcite'
            if v < 0.02:
                return 'budding_amethyst', 'amethyst_block'
            if a < 0.25:
                return 'smooth_basalt', 'calcite'
            return 'calcite', 'calcite'
        if zn == 'obsidiana':
            if v < 0.03:
                return 'crying_obsidian', 'obsidian'
            if a > 0.7:
                return 'obsidian', 'blackstone'
            if b > 0.62:
                return 'basalt[axis=y]', 'blackstone'
            return ('polished_blackstone' if a < 0.2 else 'blackstone'), 'blackstone'
        # ruinas
        if b > 0.66:
            return ('cracked_deepslate_tiles' if a > 0.5 else 'deepslate_tiles'), 'deepslate'
        if a > 0.75:
            return 'polished_deepslate', 'deepslate'
        return 'tuff', 'deepslate'

    # ---- huecos que atraviesan la tierra
    def hole(self, x, z):
        """Quita la columna entera (se ve el vacío)."""
        for y in range(self.bottom[z - Z0, x - X0] - 1, self.h(x, z) + 3):
            self.put(x, y, z, None)
        self.mask[z - Z0, x - X0] = False
        self.H[z - Z0, x - X0] = -1

    def herida(self):
        """La Herida: un cráter que atraviesa la tierra, con escombros que flotan dentro, colgados de cadenas."""
        cx, cz, r = HERIDA
        rnd = self.rnd
        for x in range(int(cx - r - 9), int(cx + r + 10)):
            for z in range(int(cz - r - 9), int(cz + r + 10)):
                if not self.inside(x, z):
                    continue
                ang = math.atan2(z - cz, x - cx)
                rad = r + 2.6 * math.sin(ang * 3 + 1) + 1.6 * math.sin(ang * 7 + 2)
                d = math.hypot(x - cx, z - cz)
                if d < rad:
                    self.hole(x, z)
                elif d < rad + 4:
                    top = self.h(x, z)
                    for y in range(self.bottom[z - Z0, x - X0], top + 1):
                        self.put(x, y, z, 'crying_obsidian' if (x * 7 + y * 3 + z * 5) % 7 == 0 else 'obsidian')
                    if h01(x, z, 3) < 0.15:
                        self.put(x, top + 1, z, 'amethyst_cluster[facing=up,waterlogged=false]')
                if X0 <= x <= X1 and Z0 <= z <= Z1 and d < rad + 10:
                    self.occupied[z - Z0, x - X0] = True
        # escombros que flotan dentro, cada uno colgado de una cadena del borde de arriba
        for i in range(9):
            a = rnd.uniform(0, 2 * math.pi)
            rr = rnd.uniform(4, r - 6)
            bx, bz = round(cx + math.cos(a) * rr), round(cz + math.sin(a) * rr)
            by = rnd.randint(40, 64)
            size = rnd.uniform(1.6, 3.4)
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    for dy in range(-4, 3):
                        e = (dx / size) ** 2 + (dz / size) ** 2 + (dy / (size * 0.8)) ** 2
                        if e <= 1:
                            self.put(bx + dx, by + dy, bz + dz, 'crying_obsidian' if (dx + dy + dz) % 4 == 0 else 'obsidian')
            for y in range(by + 3, by + rnd.randint(14, 24)):
                self.put(bx, y, bz, 'chain[axis=y,waterlogged=false]')

    def fissures(self):
        """Grietas largas y estrechas que atraviesan los Llanos hasta el vacío."""
        rnd = self.rnd
        made, tries = 0, 0
        while made < 7 and tries < 160:
            tries += 1
            ys, xs = np.nonzero((self.zone == 'ceniza') & (self.dedge > 24) & self.mask)
            if len(xs) == 0:
                return
            i = rnd.randrange(len(xs))
            x, z = float(X0 + xs[i]), float(Z0 + ys[i])
            ang = rnd.uniform(0, math.pi)
            length = rnd.randint(34, 64)
            pts, ok = [], True
            for s in range(length):
                ang += rnd.uniform(-0.2, 0.2)
                x += math.cos(ang)
                z += math.sin(ang)
                ix, iz = int(round(x)), int(round(z))
                if (not self.inside(ix, iz) or self.dist_trail(ix, iz) < 12 or self.occupied[iz - Z0, ix - X0]
                        or self.dedge[iz - Z0, ix - X0] < 12):
                    ok = False
                    break
                pts.append((ix, iz, 1.0 + 1.8 * math.sin(math.pi * s / length)))
            if not ok:
                continue
            made += 1
            for ix, iz, wd in pts:
                for dx in range(-4, 5):
                    for dz in range(-4, 5):
                        x2, z2 = ix + dx, iz + dz
                        if not self.inside(x2, z2):
                            continue
                        d = math.hypot(dx, dz)
                        if d <= wd:
                            self.hole(x2, z2)
                        elif d <= wd + 1.4:
                            top = self.h(x2, z2)
                            self.put(x2, top, z2, 'crying_obsidian' if (x2 + z2) % 3 == 0 else 'obsidian')
                        if X0 <= x2 <= X1 and Z0 <= z2 <= Z1:
                            self.occupied[z2 - Z0, x2 - X0] = True

    # ---- el sendero
    def path(self):
        done = set()
        for px, pz in self.trail:
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    x, z = int(round(px)) + dx, int(round(pz)) + dz
                    if (x, z) in done or not self.inside(x, z):
                        continue
                    d = math.hypot(x - px, z - pz)
                    if d > 2.4:
                        continue
                    done.add((x, z))
                    top = self.h(x, z)
                    hsh = h01(x, z, 11)
                    if d > 1.5:
                        blk = 'cracked_deepslate_tiles' if hsh < 0.12 else 'deepslate_tiles'
                    else:
                        blk = 'chiseled_deepslate' if hsh < 0.04 else 'polished_deepslate'
                    self.put(x, top, z, blk)
                    for y in range(top + 1, top + 5):
                        if not self.air(x, y, z):
                            self.put(x, y, z, None)
                    self.occupied[z - Z0, x - X0] = True
        # farolas de alma a los lados, cada ~18 bloques
        for i in range(10, len(self.trail) - 10, 18):
            (px, pz), (qx, qz) = self.trail[i], self.trail[i + 1]
            nx, nz = -(qz - pz), qx - px
            ln = math.hypot(nx, nz) or 1
            side = 1 if (i // 18) % 2 == 0 else -1
            x, z = int(round(px + side * nx / ln * 3.8)), int(round(pz + side * nz / ln * 3.8))
            if not self.inside(x, z):
                continue
            top = self.h(x, z)
            self.put(x, top + 1, z, 'deepslate_brick_wall')
            self.put(x, top + 2, z, 'deepslate_brick_wall')
            self.put(x, top + 3, z, 'soul_lantern[hanging=false,waterlogged=false]')
            self.occupied[z - Z0, x - X0] = True

    # ---- las Anclas del Alba (Pozos de Luz)
    def place_wells(self):
        """A lo largo del sendero, repartidas (a un lado)."""
        n = len(self.trail)
        out = []
        for k in range(WELLS):
            i = int(n * (k + 1) / (WELLS + 1))
            (px, pz), (qx, qz) = self.trail[i], self.trail[min(n - 1, i + 1)]
            nx, nz = -(qz - pz), qx - px
            ln = math.hypot(nx, nz) or 1
            side = -1 if k % 2 else 1
            x, z = int(round(px + side * nx / ln * 8)), int(round(pz + side * nz / ln * 8))
            if not self.inside(x, z):
                x, z = int(round(px)), int(round(pz))
            lv = self.flatten(x, z, 5.0)
            if lv is None:
                continue
            out.append((x, lv, z))
            self.occupy(x, z, 7)
        self.wells = out

    def build_wells(self, frag_light):
        """El suelo del Ancla (cuarzo con oro), cuatro velas, cuatro estelas y la luz; la burbuja la dibuja el cliente."""
        out = []
        for x, lv, z in self.wells:
            for dx in range(-5, 6):
                for dz in range(-5, 6):
                    d = math.hypot(dx, dz)
                    if d > 4.6 or not self.inside(x + dx, z + dz):
                        continue
                    if d <= 1.1:
                        blk = 'gold_block'
                    elif d <= 2.2:
                        blk = 'smooth_quartz'
                    elif d <= 3.4:
                        blk = 'chiseled_quartz_block' if (dx + dz) % 2 == 0 else 'quartz_bricks'
                    else:
                        blk = 'calcite'
                    self.put(x + dx, lv, z + dz, blk)
                    for y in range(lv + 1, lv + 6):
                        self.put(x + dx, y, z + dz, None)
            for dx, dz in ((3, 0), (-3, 0), (0, 3), (0, -3)):
                self.put(x + dx, lv + 1, z + dz, 'candle[candles=4,lit=true,waterlogged=false]')
            # cuatro estelas de cuarzo con su vela (en diagonal, fuera de la burbuja)
            for dx, dz in ((4, 4), (-4, 4), (4, -4), (-4, -4)):
                xx, zz = x + dx, z + dz
                if not self.inside(xx, zz):
                    continue
                t = self.h(xx, zz)
                self.put(xx, t + 1, zz, 'quartz_pillar[axis=y]')
                self.put(xx, t + 2, zz, 'chiseled_quartz_block')
                self.put(xx, t + 3, zz, 'white_candle[candles=1,lit=true,waterlogged=false]')
            self.put(x, lv + 3, z, frag_light)
            out.append((x, lv + 2, z))
        return out

    # ---- lo que hay en cada tierra
    def spots(self, zone, count, spacing, margin=10, trail=8):
        ys, xs = np.nonzero((self.zone == zone) & self.mask & ~self.occupied & (self.dedge > margin))
        if len(xs) == 0:
            return []
        order = list(range(len(xs)))
        self.rnd.shuffle(order)
        out = []
        for i in order:
            x, z = int(xs[i] + X0), int(ys[i] + Z0)
            if all(math.hypot(x - a, z - b) >= spacing for a, b in out) and self.dist_trail(x, z) > trail:
                out.append((x, z))
                if len(out) >= count:
                    break
        return out

    def petrified_tree(self, x, z):
        """Un árbol petrificado: tronco de basalto que se tuerce y ramas de piedra negra sin hojas."""
        rnd = self.rnd
        top = self.h(x, z)
        hh = rnd.randint(7, 15)
        px, pz = float(x), float(z)
        lean = (rnd.uniform(-0.18, 0.18), rnd.uniform(-0.18, 0.18))
        prev = (x, top, z)
        for k in range(1, hh + 1):
            px += lean[0]
            pz += lean[1]
            bx, bz = int(round(px)), int(round(pz))
            # (1.3.98: el tronco y las ramas, cada bloque tocando al anterior por una cara; antes, al torcerse o en
            # diagonal, quedaban trozos al aire: «bloques que quedan volando»)
            self.line(prev, (bx, top + k, bz), 'basalt[axis=y]')
            prev = (bx, top + k, bz)
            if k < 3 and hh > 10:
                for dx, dz in ((1, 0), (0, 1)):
                    self.put(bx + dx, top + k, bz + dz, 'basalt[axis=y]')
            if k > hh * 0.45 and rnd.random() < 0.35:
                ang = rnd.uniform(0, 2 * math.pi)
                ln = rnd.randint(2, 5)
                end = (bx + round(math.cos(ang) * ln), top + k + ln // 2, bz + round(math.sin(ang) * ln))
                self.line((bx, top + k, bz), end, 'blackstone')
                self.put(end[0], end[1] + 1, end[2], 'polished_blackstone_wall')
        self.occupy(x, z, 3)

    def line(self, a, b, block):
        """Bloques de a a b (x, y, z), cada paso tocando al anterior por una cara."""
        x, y, z = a
        self.put(x, y, z, block)
        while (x, y, z) != tuple(b):
            dx, dy, dz = b[0] - x, b[1] - y, b[2] - z
            ax = max((abs(dx), 0), (abs(dy), 1), (abs(dz), 2))[1]
            if ax == 0:
                x += 1 if dx > 0 else -1
            elif ax == 1:
                y += 1 if dy > 0 else -1
            else:
                z += 1 if dz > 0 else -1
            self.put(x, y, z, block)

    def boulder(self, x, z, kind):
        rnd = self.rnd
        top = self.h(x, z)
        r = rnd.uniform(1.4, 3.2)
        mats = {'ceniza': ('cobbled_deepslate', 'tuff', 'smooth_basalt'), 'obsidiana': ('blackstone', 'obsidian', 'basalt[axis=y]'),
                'ruinas': ('deepslate_bricks', 'cracked_deepslate_bricks', 'tuff'), 'cristal': ('budding_amethyst', 'amethyst_block', 'calcite')}[kind]
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                for dy in range(-1, 4):
                    if (dx / r) ** 2 + (dz / r) ** 2 + (dy / (r * 0.75)) ** 2 <= 1:
                        self.put(x + dx, top + dy, z + dz, mats[int(h01(x + dx, z + dz + dy, 5) * 3) % 3])
        if kind == 'cristal':
            self.put(x, top + int(r * 0.75) + 1, z, 'amethyst_cluster[facing=up,waterlogged=false]')
        self.occupy(x, z, r + 1)

    def crystal(self, x, z):
        """Un cristal gigante de amatista: un prisma inclinado que se afila, con racimos en la punta y alrededor."""
        rnd = self.rnd
        top = self.h(x, z)
        hh = rnd.randint(8, 26)
        lean = (rnd.uniform(-0.35, 0.35), rnd.uniform(-0.35, 0.35))
        r0 = rnd.uniform(1.6, 3.4)
        for k in range(hh + 1):
            r = r0 * (1 - (k / (hh + 1)) ** 1.6) + 0.35
            cx, cz = x + lean[0] * k, z + lean[1] * k
            for dx in range(-5, 6):
                for dz in range(-5, 6):
                    # (sección de seis lados, como un cristal)
                    px, pz = x + dx - cx, z + dz - cz
                    hexd = max(abs(px), abs(px) * 0.5 + abs(pz) * 0.866)
                    if hexd <= r:
                        self.put(x + dx, top + 1 + k, z + dz, 'amethyst_block' if k < hh * 0.85 else 'budding_amethyst')
        tx, tz = round(x + lean[0] * (hh + 1)), round(z + lean[1] * (hh + 1))
        self.put(tx, top + hh + 2, tz, 'amethyst_cluster[facing=up,waterlogged=false]')
        for _ in range(6):
            dx, dz = rnd.randint(-4, 4), rnd.randint(-4, 4)
            if self.inside(x + dx, z + dz) and self.air(x + dx, self.h(x + dx, z + dz) + 1, z + dz):
                self.put(x + dx, self.h(x + dx, z + dz) + 1, z + dz,
                         rnd.choice(('amethyst_cluster', 'large_amethyst_bud', 'medium_amethyst_bud')) + '[facing=up,waterlogged=false]')
        self.occupy(x, z, 5)

    def fang(self, x, z):
        """Un colmillo de obsidiana que se curva, con vetas de obsidiana que llora."""
        rnd = self.rnd
        top = self.h(x, z)
        hh = rnd.randint(16, 40)
        r0 = rnd.uniform(2.6, 4.6)
        ang = rnd.uniform(0, 2 * math.pi)
        bend = rnd.uniform(4, 10)
        for k in range(hh + 1):
            t = k / (hh + 1)
            r = r0 * (1 - t) ** 1.25 + 0.3
            off = bend * t * t
            cx, cz = x + math.cos(ang) * off, z + math.sin(ang) * off
            for dx in range(-6, 7):
                for dz in range(-6, 7):
                    if math.hypot(x + dx - cx, z + dz - cz) <= r:
                        cry = h01(x + dx, z + dz, k) < 0.09
                        self.put(x + dx, top + 1 + k, z + dz, 'crying_obsidian' if cry else 'obsidian')
        self.occupy(x, z, 6)

    def basalt_columns(self, x, z):
        rnd = self.rnd
        for _ in range(rnd.randint(5, 10)):
            dx, dz = rnd.randint(-4, 4), rnd.randint(-4, 4)
            if not self.inside(x + dx, z + dz):
                continue
            top = self.h(x + dx, z + dz)
            for k in range(rnd.randint(2, 9)):
                self.put(x + dx, top + 1 + k, z + dz, 'basalt[axis=y]')
        self.occupy(x, z, 5)

    def stele(self, x, z):
        """La estela de un caballero que cayó aquí: pizarra pulida con una vela."""
        top = self.h(x, z)
        self.put(x, top + 1, z, 'polished_deepslate')
        self.put(x, top + 2, z, 'chiseled_deepslate')
        self.put(x, top + 3, z, 'polished_deepslate_slab[type=bottom,waterlogged=false]')
        self.put(x, top + 4, z, 'white_candle[candles=2,lit=true,waterlogged=false]')
        self.occupy(x, z, 2)

    def chapel(self, cx, cz, rot):
        """Una capilla de la Orden, rota: planta de 15 × 11, muros de cuarzo con su zócalo de pizarra, columnas, un
        altar con oro y velas; partes caídas."""
        rnd = self.rnd
        lv = self.flatten(cx, cz, 10)
        if lv is None:
            return
        hw, hl = 7, 5

        def at(u, v):
            # u a lo largo, v a lo ancho, girado
            return (cx + u, cz + v) if rot == 0 else (cx + v, cz + u)

        for u in range(-hw - 1, hw + 2):
            for v in range(-hl - 1, hl + 2):
                x, z = at(u, v)
                if not self.inside(x, z):
                    continue
                self.put(x, lv, z, 'quartz_bricks' if abs(v) == hl + 1 or abs(u) == hw + 1 else
                         ('polished_deepslate' if (u + v) % 2 else 'deepslate_tiles'))
                for y in range(lv + 1, lv + 12):
                    self.put(x, y, z, None)
                wall = abs(u) == hw or abs(v) == hl
                if wall:
                    hmax = 2 + int(h01(x, z, 7) * 6)
                    if u == -hw and abs(v) <= 1:
                        hmax = 0  # la puerta
                    for y in range(lv + 1, lv + 1 + hmax):
                        self.put(x, y, z, 'polished_deepslate' if y == lv + 1 else ('quartz_bricks' if (y + u) % 4 else 'chiseled_quartz_block'))
        # columnas dentro
        for u in (-4, 0, 4):
            for v in (-3, 3):
                x, z = at(u, v)
                hh = 2 + int(h01(x, z, 9) * 7)
                for y in range(lv + 1, lv + 1 + hh):
                    self.put(x, y, z, 'quartz_pillar[axis=y]')
                if hh >= 7:
                    self.put(x, lv + 1 + hh, z, 'gold_block')
        # el altar
        x, z = at(hw - 2, 0)
        self.put(x, lv + 1, z, 'chiseled_quartz_block')
        self.put(x, lv + 2, z, 'gold_block')
        for dv in (-1, 1):
            xx, zz = at(hw - 2, dv)
            self.put(xx, lv + 1, zz, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
            self.put(xx, lv + 2, zz, 'white_candle[candles=3,lit=true,waterlogged=false]')
        # bloques caídos alrededor
        for _ in range(10):
            u, v = rnd.randint(-hw - 4, hw + 4), rnd.randint(-hl - 4, hl + 4)
            x, z = at(u, v)
            if self.inside(x, z) and (abs(u) > hw or abs(v) > hl):
                self.put(x, self.h(x, z) + 1, z, rnd.choice(('quartz_bricks', 'chiseled_quartz_block', 'polished_deepslate')))
        self.occupy(cx, cz, 13)

    def wall_ruin(self, x0, z0, x1, z1):
        """Un trozo de la muralla que rodeaba las tierras de la Orden: de pizarra, rota."""
        n = int(math.hypot(x1 - x0, z1 - z0))
        for i in range(n):
            t = i / n
            x, z = int(round(x0 + (x1 - x0) * t)), int(round(z0 + (z1 - z0) * t))
            if not self.inside(x, z) or self.dist_trail(x, z) < 5:
                continue
            hmax = int(2 + 6 * (0.5 + 0.5 * math.sin(i * 0.31)) * h01(x, z, 13))
            top = self.h(x, z)
            for y in range(top + 1, top + 1 + hmax):
                self.put(x, y, z, 'deepslate_bricks' if (y + i) % 5 else 'cracked_deepslate_bricks')
            if hmax >= 6:
                self.put(x, top + 1 + hmax, z, 'deepslate_brick_slab[type=bottom,waterlogged=false]')
            self.occupied[z - Z0, x - X0] = True

    def islets(self):
        """Fragmentos que flotan encima; algunos, colgados de cadenas (la magia de la Orden los sujeta)."""
        rnd = self.rnd
        placed = []
        tries = 0
        while len(placed) < 26 and tries < 400:
            tries += 1
            ys, xs = np.nonzero(self.mask & (self.dedge > 8))
            i = rnd.randrange(len(xs))
            x, z = int(xs[i] + X0), int(ys[i] + Z0)
            if any(math.hypot(x - a, z - b) < 40 for a, b in placed):
                continue
            ground = self.h(x, z)
            cy = ground + rnd.randint(22, 52)
            r = rnd.uniform(3.5, 8.5)
            placed.append((x, z))
            zone = self.zone[z - Z0, x - X0]
            topb = {'cristal': 'calcite', 'obsidiana': 'blackstone', 'ruinas': 'deepslate_tiles'}.get(zone, 'tuff')
            for dx in range(-9, 10):
                for dz in range(-9, 10):
                    d = math.hypot(dx, dz)
                    if d > r:
                        continue
                    depth = int(2 + (r - d) * 1.6)
                    for k in range(depth):
                        blk = topb if k == 0 else ('obsidian' if (dx + dz + k) % 5 == 0 else 'deepslate')
                        self.put(x + dx, cy - k, z + dz, blk)
                    if d < r - 1 and h01(x + dx, z + dz, 17) < 0.12:
                        self.put(x + dx, cy - depth, z + dz, 'amethyst_cluster[facing=down,waterlogged=false]')
            if zone == 'cristal':
                for k in range(1, 5):
                    self.put(x, cy + k, z, 'amethyst_block')
                self.put(x, cy + 5, z, 'amethyst_cluster[facing=up,waterlogged=false]')
            # colgado de cadenas: dos o tres hasta el suelo (o hasta donde lleguen)
            if rnd.random() < 0.55 and ground > 0:
                for _ in range(rnd.randint(2, 3)):
                    dx, dz = rnd.randint(-2, 2), rnd.randint(-2, 2)
                    depth = int(2 + (r - math.hypot(dx, dz)) * 1.6)
                    for y in range(ground + 1, cy - depth):
                        if self.inside(x + dx, z + dz) and self.air(x + dx, y, z + dz):
                            self.put(x + dx, y, z + dz, 'chain[axis=y,waterlogged=false]')

    def underside(self):
        """Lo que cuelga por debajo: amatistas y alguna cadena rota."""
        for iz in range(self.NZ):
            for ix in range(self.NX):
                if not self.mask[iz, ix]:
                    continue
                x, z = ix + X0, iz + Z0
                bot = int(self.bottom[iz, ix])
                r = h01(x, z, SEED)
                if not self.air(x, bot - 1, z):
                    continue
                if r < 0.03:
                    self.put(x, bot - 1, z, 'amethyst_cluster[facing=down,waterlogged=false]')
                elif r < 0.034:
                    for k in range(1, 3 + int(r * 10000) % 6):
                        self.put(x, bot - k, z, 'chain[axis=y,waterlogged=false]')

    def details(self):
        """Lo pequeño del suelo: brotes de amatista en el cristal, botones de piedra negra en las Fauces."""
        for iz in range(self.NZ):
            for ix in range(self.NX):
                if not self.mask[iz, ix] or self.occupied[iz, ix]:
                    continue
                x, z = ix + X0, iz + Z0
                top = int(self.H[iz, ix])
                if not self.air(x, top + 1, z):
                    continue
                r = h01(x, z, 29)
                zn = self.zone[iz, ix]
                tb = self.get(x, top, z)
                if zn == 'cristal' and tb in ('minecraft:calcite', 'minecraft:amethyst_block', 'minecraft:budding_amethyst'):
                    if r < 0.05:
                        self.put(x, top + 1, z, 'small_amethyst_bud[facing=up,waterlogged=false]')
                    elif r < 0.075:
                        self.put(x, top + 1, z, 'medium_amethyst_bud[facing=up,waterlogged=false]')
                elif zn == 'obsidiana' and r < 0.012:
                    self.put(x, top + 1, z, 'polished_blackstone_button[face=floor,facing=north,powered=false]')
                elif zn == 'ceniza' and r < 0.006:
                    self.put(x, top + 1, z, 'cobbled_deepslate_slab[type=bottom,waterlogged=false]')

    def stone_bridge(self):
        """El Puente de Piedra a la isla de la grieta, con la Puerta de los Fragmentos en este lado."""
        w = self.w
        z0, z1 = STONE_BRIDGE_Z[1], STONE_BRIDGE_Z[0]   # de -169 (sur) a -206 (norte)
        cx = SOUTH_DOOR[0]
        n = abs(z1 - z0)
        for i, z in enumerate(range(z0, z1 - 1, -1)):
            t = i / n
            for dx in range(-3, 4):
                x = cx + dx
                for y in range(60 if z <= -177 else 66, 72):
                    w.put(x, y, z, None)
                if abs(dx) == 3:
                    continue
                w.put(x, 66, z, 'polished_deepslate' if abs(dx) <= 1 else 'deepslate_tiles')
                depth = int(2 + (1 - math.sin(math.pi * t)) * 6 + (abs(dx) == 2) * -1)
                for k in range(1, depth + 1):
                    w.put(x, 66 - k, z, 'blackstone' if (x + z + k) % 3 else 'deepslate')
            if i % 6 == 3:
                for dx in (-2, 2):
                    w.put(cx + dx, 67, z, 'deepslate_brick_wall')
                    w.put(cx + dx, 68, z, 'soul_lantern[hanging=false,waterlogged=false]')
        # la Puerta de los Fragmentos (en este lado)
        gz = SOUTH_DOOR[1] - 2
        top = 66
        for dx in (-4, 4):
            for y in range(top + 1, top + 10):
                w.put(cx + dx, y, gz, 'deepslate_bricks' if y % 3 else 'chiseled_deepslate')
            w.put(cx + dx, top + 10, gz, 'soul_lantern[hanging=false,waterlogged=false]')
        # (1.3.98: el dintel entero; antes iba de −4 a 1 y quedaba cojo)
        for dx in range(-4, 5):
            w.put(cx + dx, top + 10 if abs(dx) == 4 else top + 9, gz, 'deepslate_brick_slab[type=bottom,waterlogged=false]'
                  if abs(dx) < 4 else 'soul_lantern[hanging=false,waterlogged=false]')
        w.put(cx - 1, top + 8, gz, 'chain[axis=y,waterlogged=false]')
        w.put(cx - 1, top + 7, gz, 'soul_lantern[hanging=true,waterlogged=false]')
        self.occupy(cx, gz, 4)

    # ---- todo
    def run(self, main_ground, frag_light):
        w = self.w
        w.grow(X0, X1, Z0, Z1)
        self.shape(main_ground)
        self.make_trail()
        self.regions()
        self.heights()
        self.place_wells()
        # (se borra la zona entera: se puede volver a pasar)
        g = w.d['g']
        X, Z = self.X, self.Z
        clear = self.mask | ((np.abs(X + 64) <= 3) & (Z <= -177))
        for iz, ix in zip(*np.nonzero(clear)):
            g[:, Z[iz, ix] - w.d['oz'], X[iz, ix] - w.d['ox']] = 0
        # las capillas antes de rellenar (allanan su sitio)
        chapels = [(-112, -585, 0), (-6, -612, 1), (-150, -628, 0), (24, -560, 1)]
        for cx, cz, rot in chapels:
            if self.inside(cx, cz):
                self.flatten(cx, cz, 10)
        self.fill()
        self.herida()
        self.fissures()
        self.stone_bridge()
        self.path()
        well_sites = self.build_wells(frag_light)
        for cx, cz, rot in chapels:
            if self.inside(cx, cz) and self.dist_trail(cx, cz) > 14:
                self.chapel(cx, cz, rot)
        self.wall_ruin(-190, -640, -120, -520)
        self.wall_ruin(30, -520, 90, -640)
        for x, z in self.spots('ruinas', 22, 14):
            self.stele(x, z)
        for x, z in self.spots('ruinas', 10, 22):
            self.boulder(x, z, 'ruinas')
        for x, z in self.spots('ceniza', 70, 13):
            self.petrified_tree(x, z)
        for x, z in self.spots('ceniza', 40, 18):
            self.boulder(x, z, 'ceniza')
        for x, z in self.spots('cristal', 46, 16):
            self.crystal(x, z)
        for x, z in self.spots('cristal', 18, 20):
            self.boulder(x, z, 'cristal')
        for x, z in self.spots('obsidiana', 26, 24):
            self.fang(x, z)
        for x, z in self.spots('obsidiana', 22, 16):
            self.basalt_columns(x, z)
        for x, z in self.spots('obsidiana', 14, 20):
            self.boulder(x, z, 'obsidiana')
        self.details()
        self.islets()
        self.underside()
        # dónde empieza la fauna (VoidIsland: por donde anda, desde aquí): lo más lejos del borde y de los huecos
        from scipy import ndimage
        d = ndimage.distance_transform_edt(self.mask)
        iz, ix = np.unravel_index(int(np.argmax(d)), d.shape)
        seed = (int(ix + X0), int(self.H[iz, ix]) + 1, int(iz + Z0))
        return well_sites, seed
