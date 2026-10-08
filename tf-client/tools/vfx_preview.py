"""Vista previa animada de un efecto (kill o skill) para la web: simula la línea de tiempo de fx.json como el mod
(VfxClient: modelos con sus animaciones, cambios de pieza, visibilidad, estados, tintes, proyectiles, temporizadores y
partículas aproximadas) y la dibuja con vfx_render en un WebP animado.

En los kills la víctima es un zombi que hace la animación (como en el juego); en las skills, un jugador (Steve) en el
centro, con el efecto alrededor. Las partículas son cuadraditos de color (sin las texturas de Minecraft).
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

import vfx_bb as B
import vfx_render as R

# Color de cada partícula de Minecraft (las que no salen aquí, blancas)
PARTICLE_COLORS = {
    'flame': '#ffa63a', 'soul_fire_flame': '#59e8ff', 'smoke': '#5a5a5a', 'large_smoke': '#4a4a4a',
    'campfire_cosy_smoke': '#8a8a8a', 'campfire_signal_smoke': '#8a8a8a', 'cloud': '#eeeeee', 'end_rod': '#fff6dc',
    'electric_spark': '#bfe9ff', 'crit': '#fff2a8', 'enchanted_hit': '#9ad7ff', 'witch': '#b34dff', 'enchant': '#c9b6ff',
    'portal': '#a14dff', 'reverse_portal': '#c86bff', 'soul': '#59c8ff', 'snowflake': '#ffffff', 'falling_water': '#4aa3ff',
    'dripping_water': '#4aa3ff', 'splash': '#6ab8ff', 'bubble': '#8fd0ff', 'squid_ink': '#151515',
    'glow_squid_ink': '#3fffd0', 'sculk_soul': '#45e0ff', 'totem_of_undying': '#ffd34d', 'dragon_breath': '#d24dff',
    'heart': '#ff4d6d', 'lava': '#ff8a2a', 'flash': '#ffffff', 'sweep_attack': '#dddddd', 'sonic_boom': '#33ffff',
    'glow': '#9fffd8', 'block': '#8a7a6a', 'falling_dust': '#b8a88a', 'damage_indicator': '#7a0000', 'poof': '#dddddd',
    'wax_on': '#ffb43a', 'scrape': '#99ffdd', 'effect': '#e8e8ff', 'instant_effect': '#ffffff', 'note': '#7cff6b',
    'firework': '#ffffff', 'cherry_leaves': '#ffb7d5', 'spore_blossom_air': '#c47bd6', 'ash': '#777777',
    'white_ash': '#dddddd', 'dust_plume': '#c8b48a', 'happy_villager': '#59ff70', 'angry_villager': '#555555',
}


def hex_rgb(h, default=(1.0, 1.0, 1.0)):
    try:
        c = int(str(h).replace('#', ''), 16)
        return ((c >> 16) & 255) / 255, ((c >> 8) & 255) / 255, (c & 255) / 255
    except ValueError:
        return default


def rot_y(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], float)


def offset(ev, yaw):
    fwd, side, up = ev.get('fwd', 0), ev.get('side', 0), ev.get('up', 0)
    rad = math.radians(yaw)
    fx, fz = -math.sin(rad), math.cos(rad)
    rx, rz = -math.cos(rad), -math.sin(rad)
    return np.array([fx * fwd + rx * side, up, fz * fwd + rz * side])


class Assets:
    """Modelos y texturas ya convertidos (assets/tfclient/vfx/models y textures/vfx)."""

    def __init__(self, models_dir, tex_dir):
        self.models_dir, self.tex_dir = models_dir, tex_dir
        self.cache = {}

    def get(self, name):
        name = name.lower()
        if name not in self.cache:
            path = os.path.join(self.models_dir, name + '.json')
            if not os.path.exists(path):
                self.cache[name] = None
            else:
                m = json.load(open(path))
                self.cache[name] = (m, R.load_textures(m, self.tex_dir))
        return self.cache[name]


class Instance:
    def __init__(self, sim, model, tex, ev):
        self.sim, self.model, self.tex, self.ev = sim, model, tex, ev
        self.key = ev.get('key', ev['model'])
        self.life = ev.get('life', 40)
        self.age = 0
        self.alive = True
        self.scale = ev.get('scale', 1.0) * (sim.actor_scale if ev.get('actor') else 1.0)
        self.extra_yaw = ev.get('yaw', 0)
        self.yaw = sim.yaw + self.extra_yaw
        self.hop = ev.get('hop', 0)
        self.visible = [not b['hidden'] for b in model['bones']]
        self.sources = {}
        self.tint = None
        self.vel = ev.get('vel', 0)
        self.grav = ev.get('grav', 0)
        self.fall = 0.0
        self.travel = np.zeros(3)
        self.anim, self.speed, self.anim_start = None, 1.0, 0
        self.set_anim(ev.get('anim'), ev.get('speed', 1.0))
        self.origin = self.place()

    def set_anim(self, name, speed):
        self.anim = name if name in self.model['anims'] else None
        self.speed = speed
        self.anim_start = self.age

    def place(self):
        p = self.sim.base + offset(self.ev, self.sim.yaw) + self.travel
        if self.hop > 0:
            s = min(1.0, self.age / 10.0)
            rad = math.radians(self.sim.yaw)
            p = p + np.array([-math.sin(rad) * self.hop * s, 4 * 0.35 * s * (1 - s), math.cos(rad) * self.hop * s])
        return p

    def set_visible(self, b, show, children):
        self.visible[b] = show
        if not children:
            return
        bones = self.model['bones']
        for i in range(len(bones)):
            p = bones[i]['parent']
            while p >= 0:
                if p == b:
                    self.visible[i] = show
                    break
                p = bones[p]['parent']

    def bone(self, name):
        n = name.lower()
        for i, b in enumerate(self.model['bones']):
            if b['id'].lower() == n:
                return i
        return -1

    def tick(self):
        if not self.alive:
            return
        self.age += 1
        if self.age > self.life:
            self.alive = False
            return
        for s in self.ev.get('swap', []):
            if s['t'] == self.age:
                b = self.bone(s['bone'])
                other = self.sim.assets.get(s['model'])
                if b >= 0 and other:
                    self.sources[b] = other
        for v in self.ev.get('vis', []):
            if v['t'] == self.age:
                b = self.bone(v['bone'])
                if b >= 0:
                    self.set_visible(b, bool(v.get('show')), v.get('children', True))
        for st in self.ev.get('states', []):
            if st['t'] == self.age:
                self.set_anim(st.get('anim'), st.get('speed', 1.0))
        for tn in self.ev.get('tints', []):
            if tn['t'] == self.age:
                self.tint = hex_rgb(tn.get('c', '#ffffff'))
        for vl in self.ev.get('vels', []):
            if vl['t'] == self.age:
                self.vel = vl.get('vel', 0)
        if self.vel or self.grav or self.ev.get('vels'):
            rad = math.radians(self.sim.yaw)
            self.fall += self.grav
            self.travel = self.travel + np.array([-math.sin(rad) * self.vel, -self.fall, math.cos(rad) * self.vel])
        self.origin = self.place()
        for tm in self.ev.get('timers', []):
            if self.age % max(1, tm.get('every', 1)) == 0:
                self.sim.start(tm['ev'], self)

    def seconds(self):
        return (self.age - self.anim_start) / 20.0 * self.speed

    def world(self):
        m = np.eye(4)
        m[:3, 3] = self.origin
        return m @ rot_y(180 - self.yaw) @ np.diag([self.scale / 16] * 3 + [1])

    def mats(self):
        return B.pose(self.model, self.anim, self.seconds())

    def bone_world(self, b):
        m = self.world() @ np.array(self.mats()[b])
        return m[:3, 3]


class Particle:
    __slots__ = ('pos', 'vel', 'life', 'age', 'c1', 'c2', 'size')


class Sim:
    """Una línea de tiempo de fx.json en marcha, con quien lanza mirando hacia la cámara."""

    def __init__(self, assets, fx, actor_skin=None, actor_scale=1.0, seed=7):
        self.assets, self.fx = assets, fx
        self.yaw = 180.0  # mirando a -Z: la cámara de vfx_render ve el frente
        self.base = np.zeros(3)
        self.actor_skin, self.actor_scale = actor_skin, actor_scale
        self.instances, self.repeaters, self.particles = [], [], []
        self.age = 0
        self.random = random.Random(seed)

    def start(self, ev, owner=None):
        if 'model' in ev:
            got = self.assets.get(ev['model'])
            if got:
                self.instances.append(Instance(self, got[0], got[1], ev))
        elif 'part' in ev:
            if 'orbit' in ev:
                ticks = max(1, ev.get('ticks', 20))
                self.repeaters.append([ev, ticks, 1, self.age + 1, owner, 0])
                self.spawn(ev, owner, 0)
            else:
                self.spawn(ev, owner, 0)
                rep = ev.get('rep', 0)
                if rep > 0:
                    every = max(1, ev.get('every', 1))
                    self.repeaters.append([ev, rep, every, self.age + every, owner, 0])
        elif 'state' in ev:
            for inst in self.instances:
                if ev.get('inst') is None or ev.get('inst') == inst.key:
                    inst.set_anim(ev['state'], ev.get('speed', 1.0))

    def bone_position(self, key, bone):
        for inst in reversed(self.instances):
            if not inst.alive or (key is not None and key != inst.key):
                continue
            b = inst.bone(bone)
            if b >= 0:
                return inst.bone_world(b)
        return None

    def spawn(self, ev, owner, step):
        kind = str(ev['part']).split(':')[-1]
        if kind == 'dust':
            c1 = c2 = hex_rgb(ev.get('color', '#ffffff'))
        elif kind == 'dust_color_transition':
            c1, c2 = hex_rgb(ev.get('color', '#ffffff')), hex_rgb(ev.get('color2', '#ffffff'))
        else:
            c1 = c2 = hex_rgb(PARTICLE_COLORS.get(kind, '#ffffff'))
        size = 0.05 * ev.get('size', 1.0) if kind.startswith('dust') else 0.06
        rnd = self.random
        if 'orbit' in ev:
            c = self.base + offset(ev, self.yaw)
            period = max(1, ev.get('period', 20))
            a = math.pi * 2 * (ev.get('start', 0) + step) / period
            r = ev.get('orbit', 1)
            at = c + np.array([math.cos(a) * r, ev.get('oy', 0), math.sin(a) * r])
            self.burst(at, max(1, int(ev.get('n', 1))), ev.get('hs', 0), ev.get('vs', 0), ev.get('sp', 0), c1, c2, size)
            return
        bone = ev.get('bone')
        at = None
        if bone:
            if owner is not None:
                b = owner.bone(bone)
                if b < 0 or not owner.alive:
                    return
                at = owner.bone_world(b)
            else:
                at = self.bone_position(ev.get('inst'), bone)
            if at is None:
                return
        elif owner is not None:
            at = owner.origin
        else:
            at = self.base + offset(ev, self.yaw)
        at = at + np.array([0, ev.get('y', 0), 0])
        n = max(0, int(ev.get('n', 10)))
        hs, vs, sp = ev.get('hs', 0), ev.get('vs', 0), ev.get('sp', 0)
        sphere = ev.get('sphere', 0)
        if sphere > 0:
            for _ in range(max(1, int(ev.get('n', 10)))):
                u, a = rnd.random() * 2 - 1, rnd.random() * math.pi * 2
                rr = math.sqrt(1 - u * u)
                self.add(at + np.array([rr * math.cos(a) * sphere, u * sphere, rr * math.sin(a) * sphere]), np.zeros(3), c1, c2, size)
            return
        ring, points = ev.get('ring', 0), int(ev.get('points', 0))
        if ring > 0 and points > 0:
            for p in range(points):
                a = math.pi * 2 * p / points
                self.burst(at + np.array([math.cos(a) * ring, 0, math.sin(a) * ring]), max(1, n), hs, vs, sp, c1, c2, size)
            return
        if n == 0:
            self.add(at, np.array([hs * sp, vs * sp, hs * sp]), c1, c2, size)
            return
        self.burst(at, n, hs, vs, sp, c1, c2, size)

    def burst(self, at, n, hs, vs, sp, c1, c2, size):
        g = self.random.gauss
        for _ in range(min(n, 40)):
            pos = at + np.array([g(0, 1) * hs, g(0, 1) * vs, g(0, 1) * hs])
            self.add(pos, np.array([g(0, 1) * sp, g(0, 1) * sp, g(0, 1) * sp]), c1, c2, size)

    def add(self, pos, vel, c1, c2, size):
        if len(self.particles) > 1500:
            return
        p = Particle()
        p.pos, p.vel, p.c1, p.c2, p.size = np.array(pos, float), np.array(vel, float), c1, c2, size
        p.age, p.life = 0, self.random.randint(10, 22)
        self.particles.append(p)

    def tick(self):
        if self.age <= self.fx['dur']:
            for ev in self.fx['ev']:
                if ev.get('t', 0) == self.age:
                    self.start(ev)
        for inst in list(self.instances):
            inst.tick()
        for rp in list(self.repeaters):
            ev, left, every, next_age, owner, count = rp
            if self.age >= next_age and left > 0:
                rp[5] += 1
                self.spawn(ev, owner, rp[5])
                rp[1] -= 1
                rp[3] = self.age + every
            if rp[1] <= 0:
                self.repeaters.remove(rp)
        for p in list(self.particles):
            p.age += 1
            if p.age > p.life:
                self.particles.remove(p)
                continue
            p.pos = p.pos + p.vel
            p.vel = p.vel * 0.9 + np.array([0, 0.004, 0])
        self.instances = [i for i in self.instances if i.alive]
        self.age += 1

    def scene(self, steve=None):
        """Triángulos y partículas de este instante (en bloques)."""
        tris = []
        for inst in self.instances:
            tris += R.model_tris(inst.model, inst.tex, inst.mats(), world=inst.world(), tick=inst.age,
                                 actor=self.actor_skin if inst.ev.get('actor') else None, visible=inst.visible,
                                 sources=inst.sources, tint=inst.tint)
        if steve is not None:
            tris += steve
        pts = []
        for p in self.particles:
            k = p.age / max(1, p.life)
            c = [p.c1[i] * (1 - k) + p.c2[i] * k for i in range(3)]
            pts.append((p.pos, (*c, 0.9 * (1 - k * k)), p.size))
        return tris, pts


def player_model(skin_path, tex_dir, scratch):
    """Un jugador de pie (Steve) hecho con las mismas piezas que el muñeco, para las skills."""
    elements, outliner = [], []
    cubes = {'head': ([-4, 24, -4], [4, 32, 4]), 'body': ([-4, 12, -2], [4, 24, 2]),
             'left_arm': ([-8, 12, -2], [-4, 24, 2]), 'right_arm': ([4, 12, -2], [8, 24, 2]),
             'left_leg': ([-4, 0, -2], [0, 12, 2]), 'right_leg': ([0, 0, -2], [4, 12, 2])}
    for i, (name, (f, t)) in enumerate(cubes.items()):
        uid = f'00000000-0000-0000-0000-00000000000{i}'
        faces = {d: {'uv': [0, 0, 1, 1], 'texture': 0} for d in ('north', 'south', 'east', 'west', 'up', 'down')}
        elements.append({'name': name, 'from': f, 'to': t, 'origin': [0, 0, 0], 'faces': faces, 'uuid': uid, 'type': 'cube'})
        outliner.append({'name': name, 'origin': [0, 0, 0], 'uuid': f'10000000-0000-0000-0000-00000000000{i}', 'children': [uid]})
    bb = {'resolution': {'width': 64, 'height': 64}, 'elements': elements, 'outliner': outliner, 'animations': [],
          'textures': [{'name': 'body', 'path': skin_path, 'uv_width': 64, 'uv_height': 64}]}
    path = os.path.join(scratch, 'tf_preview_player.bbmodel')
    with open(path, 'w') as fh:
        json.dump(bb, fh)
    problems = []
    store = B.TextureStore(scratch, 'x:')
    model = B.convert(path, store, problems)
    return model


def frame_box(frames, keep=(4, 96)):
    """Caja que encuadra lo que pasa: percentiles de todos los puntos (un trozo que sale volando lejos no achica el
    resto)."""
    pts = []
    for tris, parts in frames:
        for tr in tris[::3]:
            pts.append(tr[0].mean(0))
        for p in parts[::2]:
            pts.append(p[0])
    if not pts:
        return np.zeros(3) - 1, np.zeros(3) + 1
    a = np.array(pts)
    return np.percentile(a, keep[0], axis=0), np.percentile(a, keep[1], axis=0)


def render_preview(assets, fx_list, out_path, size=320, actor_skin=None, steve=None, step=2, yaw=-28, pitch=-14,
                   fps_ms=100, pad_end=4):
    """WebP animado de una o varias líneas de tiempo seguidas (los tres cortes de un combo, por ejemplo)."""
    frames = []
    total = sum(fx['dur'] + 2 for fx in fx_list)
    step = max(step, math.ceil(total / 64))  # como mucho unos 64 fotogramas (lo largo va más a saltos)
    fps_ms = 50 * step
    for fx in fx_list:
        sim = Sim(assets, fx, actor_skin=actor_skin)
        for _ in range(fx['dur'] + 2):
            sim.tick()
            if sim.age % step == 0:
                frames.append(sim.scene(steve))
    lo, hi = frame_box(frames)
    # Quien lanza (o la víctima) siempre dentro del encuadre
    lo = np.minimum(lo, [-0.5, 0, -0.5])
    hi = np.maximum(hi, [0.5, 2, 0.5])
    center = (lo + hi) / 2
    rad = max(np.linalg.norm(hi - lo) / 2, 1.3)
    dist = rad / math.tan(math.radians(20)) * 1.0
    imgs = [R.raster(t, size=size, yaw=yaw, pitch=pitch, dist=dist, center=center, fov=40, ss=1, points=p)
            for t, p in frames]
    imgs += [imgs[-1]] * pad_end
    imgs[0].save(out_path, 'WEBP', save_all=True, append_images=imgs[1:], duration=fps_ms, loop=0, quality=70,
                 method=4, minimize_size=True)
    return len(imgs)
