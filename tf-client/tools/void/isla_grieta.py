"""La isla de la grieta (TF Client 1.3.98): con relieve, decorada, sin la «iglesia» y con un Ancla del Alba cerca de la
grieta. Lo llama isla_alba.apply (después de pasar la isla del dueño a roca del Vacío y de hacer la Llegada).

El dueño, de la 1.3.97: «la isla de la grieta es demasiado pobre: es un solo llano pelado, sin relieves, sin decoraciones;
las zonas de luz, pon una al menos más o menos cerca, no tan cerca de la grieta; y elimina la estructura que parece una
iglesia». Y: «no eres preciso en construcción: bloques y objetos quedan volando» (de eso, limpiar() de isla_alba).

  quitar_iglesia  la torre con aguja del mapa del dueño (x 53..76, z 128..154, hasta y 100; en la 1.3.97 hasta tenía un
                  Ancla en el tejado, a 86) fuera, y el suelo rehecho a la altura de alrededor
  relieve         colinas suaves (ruido de ondas largas, de 40-60 bloques: pendientes de menos de medio bloque por
                  bloque, por donde se anda y pelean los jefes), nada en la arena de la grieta (48 bloques), ni junto a
                  los caminos y lo construido, ni cerca del borde ni de la Llegada
  anclas          dos Anclas del Alba: una a ~55 bloques de la grieta (cerca, pero no tanto) y otra lejos de ella y de la
                  Llegada; en suelo llano y natural
  decorar         árboles petrificados (sus ramas, bloque a bloque y sin huecos), rocas, cristales de amatista,
                  colmillos de obsidiana, columnas de basalto y pilares rotos de la Orden con sus velas; lejos de la
                  arena, de los caminos, del borde y de las Anclas
Todo con semilla fija (sale lo mismo cada vez).
"""
import math
import random

import numpy as np

import bioma

SEED = 9898
IGLESIA = (50, 79, 125, 157)      # x0, x1, z0, z1 (con margen)
Z_MIN = -150                      # la isla de la grieta (de la Llegada hacia el sur)
ARENA = 48                        # alrededor de la grieta, llano
NATURAL = {'deepslate', 'tuff', 'basalt', 'blackstone', 'calcite', 'obsidian', 'crying_obsidian', 'amethyst_block',
           'budding_amethyst', 'smooth_basalt', 'cobbled_deepslate'}


def base(name):
    return name.split('[')[0].replace('minecraft:', '')


def smooth01(u):
    u = np.clip(u, 0.0, 1.0)
    return u * u * (3 - 2 * u)


class Grieta:
    def __init__(self, w, rift, landing, portal):
        self.w = w
        self.d = w.d
        self.rift = rift
        self.landing = landing
        self.portal = portal
        self.rnd = random.Random(SEED)
        self.taken = []           # (x, z, r): lo ya ocupado (Anclas, decoración)

    # ---- mapas de la isla
    def maps(self):
        d = self.d
        g = d['g']
        H, L, W = g.shape
        names = d['names']
        is_light = np.array([n.startswith('minecraft:light') for n in names])
        nat_id = np.array([base(n) in NATURAL for n in names])
        solid = (g != 0) & ~is_light[g]
        has = solid.any(0)
        tops = np.where(has, H - 1 - np.argmax(solid[::-1], 0), -1)
        topb = np.take_along_axis(g, np.maximum(tops, 0)[None], 0)[0]
        nat = has & nat_id[topb]
        z0 = max(0, Z_MIN - d['oz'])
        zone = np.zeros_like(has)
        zone[z0:, :] = True
        from scipy import ndimage
        art = has & ~nat & zone
        self.tops, self.nat, self.has, self.zone = tops, nat, has, zone
        self.d_art = ndimage.distance_transform_edt(~art)
        self.d_edge = ndimage.distance_transform_edt(has)
        xs = np.arange(W) + d['ox']
        zs = np.arange(L) + d['oz']
        self.X, self.Z = np.meshgrid(xs, zs)
        self.d_rift = np.hypot(self.X - self.rift[0], self.Z - self.rift[2])
        self.d_land = np.hypot(self.X - self.landing[0], self.Z - self.landing[2])

    def top(self, x, z):
        return self.w.top(x, z)

    def free(self, x, z, r):
        return all(math.hypot(x - a, z - b) > r + c for a, b, c in self.taken)

    # ---- la «iglesia» fuera
    def quitar_iglesia(self):
        w = self.w
        x0, x1, z0, z1 = IGLESIA
        # la altura del suelo alrededor (la mediana del anillo de fuera)
        ring = []
        for x in range(x0 - 4, x1 + 5):
            for z in (z0 - 4, z1 + 4):
                t = w.top(x, z)
                if t is not None:
                    ring.append(t)
        for z in range(z0 - 4, z1 + 5):
            for x in (x0 - 4, x1 + 4):
                t = w.top(x, z)
                if t is not None:
                    ring.append(t)
        lv = int(np.median(ring)) if ring else 66
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if w.top(x, z) is None:
                    continue
                for y in range(lv + 1, 112):
                    w.put(x, y, z, None)
                below = None
                for y in range(lv, 20, -1):
                    if w.get(x, y, z) != 'minecraft:air':
                        below = y
                        break
                if below is None:
                    continue
                for y in range(below + 1, lv):
                    w.put(x, y, z, 'deepslate' if bioma.h01(x, z + y, 3) < 0.6 else 'tuff')
                hh = bioma.h01(x, z, 11)
                w.put(x, lv, z, 'tuff' if hh < 0.55 else ('smooth_basalt' if hh < 0.85 else 'calcite'))
        return lv

    # ---- colinas
    def relieve(self):
        d = self.d
        g = d['g']
        f = bioma.fbm(self.X / 52.0, self.Z / 52.0, 31, 3)
        hill = 9.0 * smooth01((f - 0.42) / 0.33)
        k = (smooth01((self.d_rift - ARENA) / 28.0) * smooth01((self.d_art - 4) / 9.0) * smooth01((self.d_edge - 5) / 12.0)
             * smooth01((self.d_land - 30) / 16.0))
        h = np.round(hill * k).astype(int)
        h[~(self.nat & self.zone)] = 0
        n = 0
        for iz, ix in zip(*np.nonzero(h > 0)):
            x, z = ix + d['ox'], iz + d['oz']
            t = self.tops[iz, ix] + d['oy']
            topname = d['names'][g[self.tops[iz, ix], iz, ix]]
            for y in range(t, t + h[iz, ix]):
                self.w.put(x, y, z, 'deepslate' if bioma.h01(x, z + y, 7) < 0.55 else 'tuff')
            self.w.put(x, t + h[iz, ix], z, topname.replace('minecraft:', ''))
            n += 1
        return n

    # ---- las Anclas del Alba
    def anclas(self, anchor_floor):
        d = self.d
        ok = self.nat & self.zone & (self.d_edge > 11) & (self.d_art > 7) & (self.d_land > 55)
        ok &= np.hypot(self.X - self.portal[0], self.Z - self.portal[2]) > 35
        cand = []
        for iz, ix in zip(*np.nonzero(ok)):
            if iz % 2 or ix % 2:
                continue
            t = self.tops[iz - 4:iz + 5, ix - 4:ix + 5]
            if t.size < 81 or (t < 0).any() or t.max() - t.min() > 2:
                continue
            cand.append((ix + d['ox'], int(np.median(t)) + d['oy'], iz + d['oz'], self.d_rift[iz, ix]))
        out = []
        near = sorted((c for c in cand if 50 <= c[3] <= 66), key=lambda c: (abs(c[3] - 56), bioma.h01(c[0], c[2], 1)))
        if near:
            out.append(near[0])
        far = [c for c in cand if c[3] > 95 and all(math.hypot(c[0] - o[0], c[2] - o[2]) > 70 for o in out)]
        if far:
            far.sort(key=lambda c: -min([c[3], math.hypot(c[0] - self.landing[0], c[2] - self.landing[2])]
                                        + [math.hypot(c[0] - o[0], c[2] - o[2]) for o in out]))
            out.append(far[0])
        res = []
        for x, y, z, _ in out:
            anchor_floor(self.w, x, y, z)
            self.taken.append((x, z, 7))
            res.append((x, y + 2, z))
        return res

    # ---- decoración
    def spots(self, count, spacing, r_min=ARENA + 6):
        d = self.d
        ok = self.nat & self.zone & (self.d_edge > 7) & (self.d_art > 6) & (self.d_rift > r_min) & (self.d_land > 30)
        idx = list(zip(*np.nonzero(ok)))
        self.rnd.shuffle(idx)
        got = []
        for iz, ix in idx:
            x, z = ix + d['ox'], iz + d['oz']
            if math.hypot(x - self.portal[0], z - self.portal[2]) < 20 or not self.free(x, z, spacing):
                continue
            got.append((x, z))
            self.taken.append((x, z, spacing * 0.5))
            if len(got) >= count:
                break
        return got

    def line(self, a, b, block):
        """Bloques de a a b (x, y, z), cada paso tocando al anterior por una cara (sin huecos en diagonal)."""
        x, y, z = a
        self.w.put(x, y, z, block)
        while (x, y, z) != tuple(b):
            dx, dy, dz = b[0] - x, b[1] - y, b[2] - z
            ax = max((abs(dx), 0), (abs(dy), 1), (abs(dz), 2))[1]
            if ax == 0:
                x += 1 if dx > 0 else -1
            elif ax == 1:
                y += 1 if dy > 0 else -1
            else:
                z += 1 if dz > 0 else -1
            self.w.put(x, y, z, block)

    def tree(self, x, z):
        rnd = self.rnd
        top = self.top(x, z)
        hh = rnd.randint(8, 14)
        px, pz = float(x), float(z)
        lean = (rnd.uniform(-0.15, 0.15), rnd.uniform(-0.15, 0.15))
        prev = (x, top, z)
        for k in range(1, hh + 1):
            px += lean[0]
            pz += lean[1]
            cur = (int(round(px)), top + k, int(round(pz)))
            self.line(prev, cur, 'basalt[axis=y]')
            prev = cur
            if k < 3:
                for dx, dz in ((1, 0), (0, 1), (-1, 0)):
                    if rnd.random() < 0.6:
                        self.w.put(cur[0] + dx, top + k, cur[2] + dz, 'basalt[axis=y]')
            if k > hh * 0.45 and rnd.random() < 0.4:
                ang = rnd.uniform(0, 2 * math.pi)
                ln = rnd.randint(2, 5)
                end = (cur[0] + round(math.cos(ang) * ln), cur[1] + ln // 2 + 1, cur[2] + round(math.sin(ang) * ln))
                self.line(cur, end, 'blackstone')
                self.w.put(end[0], end[1] + 1, end[2], 'polished_blackstone_wall')

    def boulder(self, x, z):
        rnd = self.rnd
        top = self.top(x, z)
        r = rnd.uniform(1.6, 3.4)
        mats = ('cobbled_deepslate', 'tuff', 'smooth_basalt', 'blackstone')
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                for dy in range(-2, 4):
                    if (dx / r) ** 2 + (dz / r) ** 2 + (dy / (r * 0.7)) ** 2 <= 1:
                        self.w.put(x + dx, top + dy, z + dz, mats[int(bioma.h01(x + dx, z + dz + dy, 5) * 4) % 4])

    def crystal(self, x, z):
        rnd = self.rnd
        top = self.top(x, z)
        hh = rnd.randint(7, 16)
        lx, lz = rnd.uniform(-0.25, 0.25), rnd.uniform(-0.25, 0.25)
        for k in range(0, hh):
            rr = 1.6 * (1 - k / hh) + 0.4
            cx, cz = x + lx * k, z + lz * k
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    if math.hypot(dx, dz) <= rr:
                        self.w.put(int(round(cx + dx)), top + 1 + k, int(round(cz + dz)), 'amethyst_block' if k % 4 else 'budding_amethyst')
        tx, tz = int(round(x + lx * (hh - 1))), int(round(z + lz * (hh - 1)))
        self.w.put(tx, top + 1 + hh, tz, 'amethyst_cluster[facing=up,waterlogged=false]')
        for dx, dz in ((2, 0), (-2, 1), (1, -2)):
            t = self.top(x + dx, z + dz)
            if t is not None and abs(t - top) <= 1:
                self.w.put(x + dx, t + 1, z + dz, 'amethyst_cluster[facing=up,waterlogged=false]')

    def fang(self, x, z):
        rnd = self.rnd
        top = self.top(x, z)
        hh = rnd.randint(9, 20)
        ang = rnd.uniform(0, 2 * math.pi)
        for k in range(0, hh):
            rr = 1.8 * (1 - k / hh) ** 0.8 + 0.3
            bend = (k / hh) ** 2 * 4
            cx, cz = x + math.cos(ang) * bend, z + math.sin(ang) * bend
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    if math.hypot(dx, dz) <= rr:
                        self.w.put(int(round(cx + dx)), top + 1 + k, int(round(cz + dz)), 'crying_obsidian' if (k + dx) % 5 == 0 else 'obsidian')

    def columns(self, x, z):
        rnd = self.rnd
        top = self.top(x, z)
        for _ in range(rnd.randint(4, 7)):
            cx, cz = x + rnd.randint(-2, 2), z + rnd.randint(-2, 2)
            t = self.top(cx, cz)
            if t is None or abs(t - top) > 2:
                continue
            for y in range(t + 1, t + 1 + rnd.randint(3, 9)):
                self.w.put(cx, y, cz, 'basalt[axis=y]')

    def pillar(self, x, z):
        """Un pilar roto de la Orden: base de ladrillo de pizarra, fuste de cuarzo partido, una vela y trozos al lado."""
        rnd = self.rnd
        top = self.top(x, z)
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                self.w.put(x + dx, top + 1, z + dz, 'deepslate_bricks')
        hh = rnd.randint(3, 7)
        for y in range(top + 2, top + 2 + hh):
            self.w.put(x, y, z, 'quartz_pillar[axis=y]')
        self.w.put(x, top + 2 + hh, z, 'white_candle[candles=2,lit=true,waterlogged=false]')
        # el trozo caído, tumbado en el suelo a su lado
        ang = rnd.choice(((1, 0, 'x'), (-1, 0, 'x'), (0, 1, 'z'), (0, -1, 'z')))
        for s in range(2, 2 + rnd.randint(2, 4)):
            t = self.top(x + ang[0] * s, z + ang[1] * s)
            if t is None or abs(t - top) > 1:
                break
            self.w.put(x + ang[0] * s, t + 1, z + ang[1] * s, 'quartz_pillar[axis=%s]' % ang[2])

    def decorar(self):
        n = 0
        for count, spacing, fn, rmin in ((18, 17, self.tree, ARENA + 6), (18, 15, self.boulder, ARENA + 6), (12, 22, self.crystal, ARENA + 6),
                                         (9, 22, self.fang, ARENA + 6), (9, 18, self.columns, ARENA + 6), (10, 18, self.pillar, ARENA + 2)):
            for x, z in self.spots(count, spacing, r_min=rmin):
                fn(x, z)
                n += 1
        return n


def run(w, rift, landing, portal, anchor_floor):
    """Todo, en orden. Devuelve las Anclas (x, y, z) y la menor distancia entre ellas y la Llegada y la grieta."""
    gr = Grieta(w, rift, landing, portal)
    gr.quitar_iglesia()
    gr.maps()
    gr.relieve()
    gr.maps()
    frags = gr.anclas(anchor_floor)
    gr.decorar()
    pts = [(landing[0], landing[2]), (rift[0], rift[2])] + [(f[0], f[2]) for f in frags]
    sp = min((math.hypot(a[0] - b[0], a[1] - b[1]) for i, a in enumerate(pts) for b in pts[i + 1:]), default=0)
    return frags, sp
