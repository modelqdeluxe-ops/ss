"""Los muebles de los NPC de escenas (1.3.106; el dueño: «algunos modelos tienen animación de estar sentados en algo pero
no está su modelo; debes meter su modelo de silla, mesa, por ejemplo en la taberna; debes meter completo con sus cosas»).

1. Los de la taberna: el pack trae sus muebles para Nexo (la silla —un taburete—, la mesa larga y la jarra: modelos de
   objeto con display «fixed» a escala 0,5). Se convierten al formato de los NPC y se colocan desde el panel (Scenes
   Tavern → Chair, Table, Mug) como un NPC más: se mueven, se giran, se duplican y se borran igual.
2. Los que no trae ningún pack, hechos con la misma madera (la textura de la silla y la mesa de la taberna): silla con
   respaldo, taburete alto con reposapiés, sofá, banco y escritorio. También en el panel (Scenes Tavern).
3. A cada NPC que en su escena se sienta, se tumba o apoya los pies en algo que no está, el suyo, hecho a su medida y
   dibujado con él (npcs/props.json; el mod: SkillClient.renderProps): medido con su animación de escena y las mismas
   cuentas que el mod (pegado.pose, con la piel del panel): el asiento justo debajo de los muslos y del culo (sin que
   ninguna pierna lo atraviese en toda la animación), el respaldo detrás de la espalda e inclinado como ella, el
   reposapiés bajo los pies, la mesa bajo los pies o el papel. Los que ya traen su banco o su taburete en el modelo (Townsfolks
   Vol 1, el mercado) y los que se sientan o se tumban en el suelo no llevan nada.

Uso (lo llama build_npcs.convert al final; solo, sobre lo que ya hay en el jar):
    python3 -I tools/npcs/muebles.py <carpeta con los packs descomprimidos> [--res <assets/tfclient>]
    python3 -I tools/npcs/muebles.py --hoja <png> [mob…] [--res …]     # cada NPC con sus muebles (de lado y en 3/4)
"""
import base64
import io
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)
sys.path.insert(0, HERE)
import vfx_bb as B  # noqa: E402
import pegado as P  # noqa: E402

TAVERN = 'npc_tavern'
TAVERN_KEY = 'scenes_tavern'
NEXO = os.path.join('Furnitures ( Nexo ) Drag & drop', 'plugins', 'Nexo')
# Los muebles de Nexo del pack: id del objeto → (nombre en el panel, id del mob y del modelo). Su tamaño: el del
# display «fixed» del modelo por la escala 0,5 del mueble (scenes_tavern.yml)
NEXO_ITEMS = {
    'scenes_tavern_chair': ('Chair', 'mueble_silla_taberna'),
    'scenes_tavern_table': ('Table', 'mueble_mesa_taberna'),
    'scenes_mug': ('Mug', 'mueble_jarra'),
}
# Los hechos aquí (con la madera de la taberna), para el panel: id → nombre
OWN_ITEMS = {
    'mueble_silla': 'Chair with backrest',
    'mueble_taburete_alto': 'Bar stool',
    'mueble_sofa': 'Couch',
    'mueble_banco': 'Bench',
    'mueble_escritorio': 'Desk',
    'mueble_barra': 'Bar counter',
}
PREFIX = 'mueble_'

# Qué lleva cada NPC (pack, mob) → muebles. Medido con su escena (las vistas de lado: tools/npcs/muebles.py --hoja):
#  - silla: Sit Chair 1-3, los lectores sentados (libro 2, periódico) y el escritor (con su escritorio bajo el papel);
#  - Sit Chair 4 se echa atrás con los pies en alto (a 18 px): la silla (taburete) de la taberna y su mesa bajo los pies;
#  - sofá: Sit Couch y el lector del libro 1 (las piernas recogidas encima del asiento);
#  - taburete alto: los dos de la barra (los pies a 1,7 px del suelo: el reposapiés);
#  - banco: los dos tumbados en alto (boca arriba con la cabeza fuera, boca abajo);
#  - barra: el camarero de la taberna (1.3.107).
NEEDS = {
    ('npc_townsfolks_vol3', 'scene_townsfolk_sit_chair_1'): ['chair'],
    ('npc_townsfolks_vol3', 'scene_townsfolk_sit_chair_2'): ['chair'],
    ('npc_townsfolks_vol3', 'scene_townsfolk_sit_chair_3'): ['chair'],
    ('npc_townsfolks_vol3', 'scene_townsfolk_readers_book_2'): ['chair'],
    ('npc_townsfolks_vol3', 'scene_townsfolk_readers_newspaper'): ['chair'],
    ('npc_townsfolks_vol3', 'scene_townsfolk_writer'): ['chair', 'desk'],
    ('npc_townsfolks_vol3', 'scene_townsfolk_sit_chair_4'): ['stool', 'feet_table'],
    ('npc_townsfolks_vol3', 'scene_townsfolk_sit_couch_1'): ['couch'],
    ('npc_townsfolks_vol3', 'scene_townsfolk_readers_book_1'): ['couch'],
    ('npc_townsfolks_vol3', 'scene_townsfolk_sit_bar_1'): ['bar_stool'],
    ('npc_townsfolks_vol3', 'scene_townsfolk_sit_bar_2'): ['bar_stool'],
    ('npc_townsfolks_vol3', 'scene_townsfolk_lay_back_1'): ['bench'],
    ('npc_townsfolks_vol3', 'scene_townsfolk_lay_front_2'): ['bench'],
    # (1.3.107) el camarero limpia la jarra con las manos siempre delante: detrás de su barra
    ('npc_tavern', 'scene_tavern_bartender'): ['bar'],
}
SAMPLES = 120          # fotogramas de la escena que se miran
CLEAR = 0.15           # lo que queda el mueble por debajo de lo que se apoya en él (px): ni se hunde ni flota
SEAT_PCT = 5           # el asiento, bajo lo más bajo del 95 % de la escena (el paso de una postura a otra puede rozarlo)
SKIN = 'npc_wanderers_vol1.wanderer_5'


# ------------------------------------------------------------------------------------------- textura de madera

def wood_atlas(tavern_tex):
    """La textura de los muebles hechos aquí (64×64): de la de la taberna, los tablones con clavos (arriba a la
    izquierda), la madera oscura de los cantos y la veta de las patas; y la tela del sofá (roja, con su trama)."""
    src = tavern_tex.convert('RGBA')
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    out.paste(src.crop((0, 0, 37, 28)), (0, 0))       # P: tablones
    out.paste(src.crop((37, 16, 72, 26)), (0, 28))    # D: cantos (madera oscura, a lo largo)
    out.paste(src.crop((0, 40, 55, 56)), (0, 38))     # G: veta de las patas (a lo alto)
    cloth = Image.new('RGBA', (24, 24))
    px = cloth.load()
    for y in range(24):
        for x in range(24):
            base = (142, 38, 42) if (x // 2 + y // 2) % 2 else (128, 32, 37)
            if x in (0, 23) or y in (0, 23):
                base = (98, 24, 28)
            elif (x + y) % 6 == 0:
                base = tuple(min(255, c + 14) for c in base)
            px[x, y] = base + (255,)
    out.paste(cloth, (38, 0))                          # C: tela
    return out


REGIONS = {'P': (0, 0, 37, 28), 'D': (0, 28, 35, 10), 'G': (0, 38, 55, 16), 'C': (38, 0, 24, 24)}


def face_uv(kind, w, h, seed):
    """El trozo de la textura para una cara de w×h px (1 píxel de textura por píxel de modelo, como los de la taberna;
    si la cara es más grande que el trozo, se estira)."""
    rx, ry, rw, rh = REGIONS[kind]
    w, h = max(w, 0.01), max(h, 0.01)
    uw, vh = min(w, rw), min(h, rh)
    ox = (seed * 7) % max(1, int(rw - uw) + 1)
    oy = (seed * 3) % max(1, int(rh - vh) + 1)
    return [rx + ox, ry + oy, rx + ox + uw, ry + oy + vh]


def box(name, lo, hi, kind, seed=0):
    """Una caja: kind «slab» (tablón: arriba y abajo tablones, los lados madera oscura), «post» (pata: veta), «cloth»
    (tela) o una letra de REGIONS para todas sus caras."""
    dims = [hi[i] - lo[i] for i in range(3)]
    faces = {}
    for k, (a, b) in B.FACE_AXES.items():
        if kind == 'slab':
            reg = 'P' if k in ('up', 'down') else 'D'
        elif kind == 'post':
            reg = 'G'
        elif kind == 'cloth':
            reg = 'C'
        else:
            reg = kind
        w, h = dims[a], dims[b]
        if reg == 'G' and k in ('up', 'down'):
            w, h = min(w, 2), min(h, 2)
        if reg in ('D',) and w < h:
            w, h = h, w
        faces[k] = {'uv': face_uv(reg, w, h, seed + len(faces)), 'texture': 0}
    return {'name': name, 'from': list(lo), 'to': list(hi), 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'faces': faces}


# ------------------------------------------------------------------------------------------- modelos

class Builder:
    """Un modelo de Blockbench (bbmodel) hecho en memoria, para convertirlo con vfx_bb.convert como los del pack."""

    def __init__(self, name, tex_img, tex_res=64):
        self.name = name
        self.tex = tex_img
        self.res = tex_res
        self.groups = []
        self.elements = []
        self.n = 0

    def _uuid(self):
        self.n += 1
        return f'{self.n:08x}-0000-4000-8000-{self.n:012x}'

    def group(self, name, origin, rotation=(0, 0, 0), elements=()):
        g = {'name': name, 'uuid': self._uuid(), 'origin': list(origin), 'rotation': list(rotation), 'children': []}
        for el in elements:
            el = dict(el, uuid=self._uuid())
            self.elements.append(el)
            g['children'].append(el['uuid'])
        self.groups.append(g)
        return g

    def hitbox(self, w, h):
        el = {'name': 'hb', 'uuid': self._uuid(), 'from': [-w * 8, 0, -w * 8], 'to': [w * 8, h * 16, w * 8],
              'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'faces': {}}
        self.elements.append(el)
        self.groups.append({'name': 'hitbox', 'uuid': self._uuid(), 'origin': [0, 0, 0], 'children': [el['uuid']]})

    def convert(self, store, problems):
        buf = io.BytesIO()
        self.tex.save(buf, 'PNG')
        d = {'meta': {'box_uv': False}, 'resolution': {'width': self.res, 'height': self.res},
             'textures': [{'name': 'madera.png', 'uv_width': self.res, 'uv_height': self.res,
                           'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}],
             'elements': self.elements, 'outliner': self.groups, 'animations': []}
        tmp = os.path.join(os.environ.get('TMPDIR', '/tmp'), f'tf_mueble_{os.getpid()}_{self.name}.bbmodel')
        with open(tmp, 'w', encoding='utf-8') as f:
            json.dump(d, f)
        try:
            out = B.convert(tmp, store, problems, boss=True)
        finally:
            os.remove(tmp)
        out['name'] = self.name
        return out


def chair(name, tex, seat, zf, bz, tilt=0.0, hb=True):
    """Silla con respaldo: asiento de 11 de ancho con su tablón arriba a «seat» px, de z = zf hasta detrás de los postes
    del respaldo (su cara de delante en z = bz), cuatro patas y el respaldo (dos postes y dos travesaños), inclinado «tilt»
    grados hacia atrás desde el asiento."""
    b = Builder(name, tex)
    s, zb = seat, bz + 2
    zc = (zf + zb) / 2
    b.group('seat', [0, s, zc], elements=[box('seat', (-5.5, s - 2, zf), (5.5, s, zb), 'slab', 1)])
    legs = []
    for i, (x, z) in enumerate(((-5.5, zf), (3.5, zf), (-5.5, bz), (3.5, bz))):
        legs.append(box(f'leg{i}', (x, 0, z), (x + 2, s - 2, z + 2), 'post', i))
    b.group('legs', [0, 0, zc], elements=legs)
    b.group('back', [0, s, bz], rotation=(-tilt, 0, 0), elements=[
        box('post_l', (-5.5, s, bz), (-3.5, s + 13, bz + 2), 'post', 5),
        box('post_r', (3.5, s, bz), (5.5, s + 13, bz + 2), 'post', 6),
        box('rail_1', (-3.5, s + 5, bz + 0.25), (3.5, s + 7, bz + 1.75), 'slab', 7),
        box('rail_2', (-3.5, s + 10, bz + 0.25), (3.5, s + 12.5, bz + 1.75), 'slab', 8)])
    if hb:
        b.hitbox(0.7, (s + 13) / 16)
    return b


def bar_stool(name, tex, seat, foot_y, foot_z):
    """Taburete alto: asiento de 10×10 a «seat» px, patas de 1,5 y el reposapiés (un travesaño delante, bajo los pies, en
    foot_z, y dos a los lados hasta las patas de delante) con su cara de arriba a foot_y."""
    b = Builder(name, tex)
    s = seat
    b.group('seat', [0, s, 0], elements=[box('seat', (-5, s - 2, -5), (5, s, 5), 'slab', 2)])
    legs = [box(f'leg{i}', (x, 0, z), (x + 1.5, s - 2, z + 1.5), 'post', i)
            for i, (x, z) in enumerate(((-4.5, -4.5), (3, -4.5), (-4.5, 3), (3, 3)))]
    fz = min(foot_z, -4.5)
    ft = [box('foot_front', (-4.5, foot_y - 1, fz - 0.75), (4.5, foot_y, fz + 0.75), 'post', 4),
          box('foot_back', (-4.5, foot_y - 1, 3), (4.5, foot_y, 4.5), 'post', 5)]
    if fz < -4.5:
        ft += [box('foot_l', (-4.5, foot_y - 1, fz + 0.75), (-3, foot_y, -4.5), 'post', 6),
               box('foot_r', (3, foot_y - 1, fz + 0.75), (4.5, foot_y, -4.5), 'post', 7)]
    b.group('legs', [0, 0, 0], elements=legs + ft)
    b.hitbox(0.65, s / 16)
    return b


def couch(name, tex, seat, zf, bz, tilt=0.0, width=17.0):
    """Sofá (de uno): armazón de madera, cojín de tela con su cara de arriba a «seat» px desde z = zf, respaldo con cojín
    (la cara de delante del cojín en z = bz, inclinado «tilt» grados) y brazos a los lados."""
    b = Builder(name, tex)
    s, hw = seat, width / 2
    zb = bz + 4
    b.group('frame', [0, 0, 0], elements=[
        box('base', (-hw, 1, zf), (hw, s - 3, zb), 'slab', 1),
        box('foot0', (-hw, 0, zf), (-hw + 2, 1, zf + 2), 'post', 2),
        box('foot1', (hw - 2, 0, zf), (hw, 1, zf + 2), 'post', 3),
        box('foot2', (-hw, 0, zb - 2), (-hw + 2, 1, zb), 'post', 4),
        box('foot3', (hw - 2, 0, zb - 2), (hw, 1, zb), 'post', 5),
        box('cushion', (-hw + 2, s - 3, zf), (hw - 2, s, bz), 'cloth', 6),
        box('arm_l', (-hw, s - 3, zf), (-hw + 2, s + 4, bz), 'slab', 7),
        box('arm_r', (hw - 2, s - 3, zf), (hw, s + 4, bz), 'slab', 8)])
    b.group('back', [0, s - 3, bz], rotation=(-tilt, 0, 0), elements=[
        box('back', (-hw, s - 3, bz + 1.5), (hw, s + 12, zb), 'slab', 9),
        box('back_cushion', (-hw + 2, s, bz), (hw - 2, s + 10, bz + 1.5), 'cloth', 10)])
    b.hitbox(1.0, (s + 12) / 16)
    return b


def bench(name, tex, top, z0, z1, width=10.0):
    """Banco: tablón de «width» de ancho con su cara de arriba a «top» px, de z0 a z1, sobre dos tablas."""
    b = Builder(name, tex)
    hw = width / 2
    b.group('top', [0, top, 0], elements=[box('top', (-hw, top - 2, z0), (hw, top, z1), 'slab', 1)])
    b.group('legs', [0, 0, 0], elements=[
        box('leg0', (-hw + 0.5, 0, z0 + 1.5), (hw - 0.5, top - 2, z0 + 3.5), 'D', 2),
        box('leg1', (-hw + 0.5, 0, z1 - 3.5), (hw - 0.5, top - 2, z1 - 1.5), 'D', 3),
        box('rail', (-0.75, 2, z0 + 3.5), (0.75, 3.5, z1 - 3.5), 'post', 4)])
    b.hitbox(min(1.0, (z1 - z0) / 16), top / 16)
    return b


def bar(name, tex, back_z, top=17.0, width=32.0, depth=12.0):
    """Barra de taberna: tablero de tablones con su cara de arriba a «top» px y canto; delante, tablas verticales de
    madera oscura con dos fajas; su cara de detrás (la del camarero) en z = back_z y el resto hacia delante (−z)."""
    b = Builder(name, tex)
    hw = width / 2
    z1, z0 = back_z, back_z - depth
    els = [box('top', (-hw - 1, top - 2, z0 - 1), (hw + 1, top, z1 + 0.5), 'slab', 1),
           box('body', (-hw, 0, z0 + 1), (hw, top - 2, z1), 'D', 2),
           box('rail_low', (-hw - 0.5, 2, z0 + 0.5), (hw + 0.5, 4, z0 + 1.5), 'slab', 3),
           box('rail_high', (-hw - 0.5, top - 5, z0 + 0.5), (hw + 0.5, top - 3, z0 + 1.5), 'slab', 4)]
    for i in range(int(width // 8)):
        x = -hw + 2 + i * 8
        els.append(box(f'plank{i}', (x, 0, z0 + 0.5), (x + 2, top - 2, z0 + 1), 'post', 5 + i))
    b.group('bar', [0, 0, (z0 + z1) / 2], elements=els)
    b.hitbox(1.0, top / 16)
    return b


def desk(name, tex, top, zc, width=18.0, depth=11.0):
    """Escritorio: tablero de width×depth con su cara de arriba a «top» px, centrado en z = zc, sobre cuatro patas."""
    b = Builder(name, tex)
    hw, hd = width / 2, depth / 2
    z0, z1 = zc - hd, zc + hd
    b.group('top', [0, top, zc], elements=[box('top', (-hw, top - 2, z0), (hw, top, z1), 'slab', 1)])
    b.group('legs', [0, 0, zc], elements=[
        box(f'leg{i}', (x, 0, z), (x + 2, top - 2, z + 2), 'post', i)
        for i, (x, z) in enumerate(((-hw, z0), (hw - 2, z0), (-hw, z1 - 2), (hw - 2, z1 - 2)))]
        + [box('rail', (-hw + 2, top - 4, z1 - 1.75), (hw - 2, top - 2, z1 - 0.25), 'slab', 5)])
    b.hitbox(1.0, top / 16)
    return b


# ------------------------------------------------------------------------------------------- muebles de Nexo

def nexo_model(path, tex_img, scale, name):
    """Un modelo de objeto de Minecraft (los muebles de Nexo) como bbmodel: centrado en x/z, con lo más bajo en el suelo y
    a la escala con la que se ve el mueble (la del display «fixed» por la del mueble)."""
    d = json.load(open(path, encoding='utf-8'))
    els = d['elements']
    lo_y = min(min(e['from'][1], e['to'][1]) for e in els)
    b = Builder(name, tex_img, 16)

    def tr(p):
        return [(p[0] - 8) * scale, (p[1] - lo_y) * scale, (p[2] - 8) * scale]

    out = []
    for e in els:
        r = e.get('rotation') or {}
        rot = [0.0, 0.0, 0.0]
        if r.get('angle'):
            rot['xyz'.index(r.get('axis', 'y'))] = float(r['angle'])
        faces = {}
        for k, fc in (e.get('faces') or {}).items():
            f2 = {'uv': fc['uv'], 'texture': 0}
            if fc.get('rotation'):
                f2['rotation'] = fc['rotation']
            faces[k] = f2
        out.append({'name': e.get('name', 'cube'), 'from': tr(e['from']), 'to': tr(e['to']),
                    'origin': tr(r.get('origin', [8, 8, 8])), 'rotation': rot, 'faces': faces})
    b.group('mueble', [0, 0, 0], elements=out)
    xs = [v for e in out for v in (e['from'][0], e['to'][0])]
    zs = [v for e in out for v in (e['from'][2], e['to'][2])]
    ys = [v for e in out for v in (e['from'][1], e['to'][1])]
    b.hitbox(min(2.0, max(max(xs) - min(xs), max(zs) - min(zs)) / 16), max(ys) / 16)
    return b, d


def nexo_scale(model_json, item_conf):
    fixed = (model_json.get('display') or {}).get('fixed') or {}
    s = (fixed.get('scale') or [1, 1, 1])[1]
    m = (((item_conf.get('Mechanics') or {}).get('furniture') or {}).get('properties') or {}).get('scale') or {}
    return float(s) * float(m.get('y', 1))


# ------------------------------------------------------------------------------------------- medir las poses

class Pose:
    """La escena de un NPC muestreada: los vértices de cada pieza (px, espacio del modelo) en cada fotograma."""

    def __init__(self, res, pack, mob, entry):
        prog = json.load(open(os.path.join(res, 'npcs', pack + '.json'), encoding='utf-8'))
        m0 = json.load(open(os.path.join(res, 'npcs', 'models', entry['model'] + '.json'), encoding='utf-8'))
        skin = json.load(open(os.path.join(res, 'npcs', 'models', SKIN + '.json'), encoding='utf-8'))
        self.m = P.with_skin(m0, skin) if entry.get('autoskin') else m0
        self.ids = [b['id'] for b in self.m['bones']]
        layers = P.scene_layers(prog['mobs'][mob], self.m)
        ln = max([self.m['anims'][a]['len'] for a, _, _ in layers] + [0])
        self.frames = []
        for k in range(SAMPLES):
            t = ln * k / SAMPLES
            self.frames.append(P.pose(self.m, P.extra_of(self.m, [(a, t, w, ov) for a, w, ov in layers])))

    def verts(self, names, local=None):
        """Por fotograma, los vértices de esas piezas (con local(q) → máscara, solo los que cumplen en su hueso)."""
        out = []
        for mats in self.frames:
            vs = []
            for nm in names:
                if nm not in self.ids:
                    continue
                i = self.ids.index(nm)
                q = P.quads_of(self.m['bones'][i]).reshape(-1, 3)
                if local is not None and len(q):
                    q = q[local(q)]
                if len(q):
                    vs.append((mats[i][:3, :3] @ q.T).T + mats[i][:3, 3])
            out.append(np.concatenate(vs) if vs else np.zeros((0, 3)))
        return out

    def dense(self, names, local=None):
        """Por fotograma, puntos de las caras de esas piezas (5×5 en cada una: para ver lo que atraviesa un tablón; con
        local(p) → máscara, solo los que cumplen en su hueso)."""
        uv = [(u, v) for u in np.linspace(0, 1, 5) for v in np.linspace(0, 1, 5)]
        w = np.array([[(1 - u) * (1 - v), u * (1 - v), u * v, (1 - u) * v] for u, v in uv])
        out = []
        for mats in self.frames:
            vs = []
            for nm in names:
                if nm not in self.ids:
                    continue
                i = self.ids.index(nm)
                q = P.quads_of(self.m['bones'][i])
                if not len(q):
                    continue
                pts = np.einsum('kc,ncd->nkd', w, q).reshape(-1, 3)
                if local is not None:
                    pts = pts[local(pts)]
                vs.append((mats[i][:3, :3] @ pts.T).T + mats[i][:3, 3])
            out.append(np.concatenate(vs) if vs else np.zeros((0, 3)))
        return out

    def body_tilt(self):
        """Cuánto se echa hacia atrás la espalda (grados, mediana; 0 = recta)."""
        i = self.ids.index('body')
        a = []
        for mats in self.frames:
            up = mats[i][:3, :3] @ np.array([0.0, 1.0, 0.0])
            a.append(math.degrees(math.atan2(up[2], up[1])))
        return float(np.median(a))


LEGS = ['left_leg', 'right_leg', 'left_pant', 'right_pant']
THIGH = ['left_leg', 'right_leg']
BODY = ['body']


def all_v(frames):
    return np.concatenate([f for f in frames if len(f)]) if any(len(f) for f in frames) else np.zeros((0, 3))


def under(frames, x0, x1, z0, z1, pct=0.0):
    """Lo más bajo de esos vértices dentro de la planta (x0..x1, z0..z1), en todos los fotogramas (o None). Con pct, el
    percentil pct de lo más bajo de cada fotograma: un instante de paso (al cambiar de postura) no baja el asiento."""
    lows = []
    for v in frames:
        if not len(v):
            continue
        sel = v[(v[:, 0] > x0) & (v[:, 0] < x1) & (v[:, 2] > z0) & (v[:, 2] < z1)]
        if len(sel):
            lows.append(sel[:, 1].min())
    if not lows:
        return None
    return float(np.percentile(lows, pct)) if pct else min(lows)


def through(frames, top, thick, x0, x1, z0, z1):
    """Lo más hondo que algo se mete en el tablón (de top − thick a top, en esa planta), en todos los fotogramas (px)."""
    worst = 0.0
    for v in frames:
        if not len(v):
            continue
        sel = v[(v[:, 0] > x0) & (v[:, 0] < x1) & (v[:, 2] > z0) & (v[:, 2] < z1) & (v[:, 1] < top) & (v[:, 1] > top - thick)]
        if len(sel):
            worst = max(worst, float(top - sel[:, 1].min()))
    return worst


def seat_for(pose, half_w, depth, back=True, sink=1.0, thick=2.0):
    """Dónde va un asiento: su cara de arriba (lo más bajo del culo —la cara de abajo del cuerpo— sobre él en toda la
    escena, menos CLEAR), su borde de delante (zf: hacia delante mientras las piernas, que bajan desde la cadera, no se
    metan más de «sink» px en el tablón) y el de detrás (zb: con respaldo, la espalda + 0,3; si no, 4 px por detrás de la
    cadera). Devuelve (arriba, zf, zb, la espalda)."""
    body = pose.verts(BODY)
    bz = all_v(body)
    back_z = float(np.percentile(bz[:, 2], 99))
    hz = all_v(pose.verts(THIGH, lambda q: q[:, 1] > -2.5))
    hip_z = float(np.median(hz[:, 2]))
    zb = back_z + 0.3 if back else hip_z + 4.0
    # lo que se apoya: el culo y la parte de arriba de los muslos (de lado, lo que se ve encima del asiento es la cara de
    # abajo del muslo: el cuerpo queda entre las piernas)
    thighs = pose.dense(THIGH, lambda q: q[:, 1] > -4.5)
    top = under([np.concatenate([a, b]) for a, b in zip(body, thighs)], -half_w, half_w, zb - depth, zb, SEAT_PCT) - CLEAR
    dense = [np.concatenate([a, b]) for a, b in zip(pose.dense(LEGS), pose.dense(BODY))]
    zf = zb - depth
    while zf < hip_z - 1.0 and through(dense, top, thick, -half_w, half_w, zf, zb) > sink:
        zf += 0.5
    return top, zf, zb, back_z


def sink_of(pose, top, x0, x1, z0, z1, thick=2.0):
    """Lo más hondo que las piernas o el cuerpo se meten en el tablón del asiento en toda la escena (px; 0 = nada)."""
    dense = [np.concatenate([a, b]) for a, b in zip(pose.dense(LEGS), pose.dense(BODY))]
    return through(dense, top, thick, x0, x1, z0, z1)


def props_for(pose, needs, tex, store, problems, pack, mob, report):
    """Los muebles de un NPC: [(modelo convertido, {m, p, r, s})]."""
    out = []
    tag = mob.replace('scene_', '').replace('townsfolk_', '')
    for need in needs:
        mid = f'{pack}.{PREFIX}{tag}_{need}'
        if need == 'chair':
            top, zf, zb, back_z = seat_for(pose, 5.5, 11.0)
            tilt = max(0.0, min(20.0, pose.body_tilt()))
            mdl = chair(mid.split('.', 1)[1], tex, top, zf, zb, tilt, hb=False).convert(store, problems)
            report.append(f'{mob}: silla, asiento a {top:.1f} px de {zf:.1f} a {zb:.1f}, respaldo {tilt:.0f}°, '
                          f'se hunde {sink_of(pose, top, -5.5, 5.5, zf, zb):.2f} px')
        elif need == 'couch':
            top, zf, zb, back_z = seat_for(pose, 6.5, 14.0)
            tilt = max(0.0, min(25.0, pose.body_tilt()))
            mdl = couch(mid.split('.', 1)[1], tex, top, zf, zb, tilt).convert(store, problems)
            report.append(f'{mob}: sofá, asiento a {top:.1f} px de {zf:.1f} a {zb:.1f}, respaldo {tilt:.0f}°, '
                          f'se hunde {sink_of(pose, top, -6.5, 6.5, zf, zb):.2f} px')
        elif need == 'bar_stool':
            top, zf, zb, back_z = seat_for(pose, 5.0, 10.0, back=False)
            feet = all_v(pose.verts(LEGS, lambda q: q[:, 1] < -9.0))
            fy = float(feet[:, 1].min()) - CLEAR if len(feet) else 2.0
            fz = float(np.median(feet[:, 2])) if len(feet) else -6.0
            zc = (zf + zb) / 2
            mdl = bar_stool(mid.split('.', 1)[1], tex, top, fy, fz - zc).convert(store, problems)
            report.append(f'{mob}: taburete alto, asiento a {top:.1f} px en z {zc:.1f}, reposapiés a {fy:.1f} px en z {fz:.1f}, '
                          f'se hunde {sink_of(pose, top, -5, 5, zc - 5, zc + 5):.2f} px')
            # (el taburete va centrado bajo el asiento medido)
            out.append((mdl, {'m': mid, 'p': [0.0, 0.0, round(zc, 3)], 'r': 0.0, 's': [1, 1, 1]}))
            continue
        elif need == 'bench':
            body = pose.verts(BODY)
            legs = pose.verts(LEGS)
            bz = all_v(body)
            z0, z1 = float(bz[:, 2].min()), float(bz[:, 2].max())
            lz = all_v(legs)
            top = under(body, -5, 5, z0, z1)
            # hacia las piernas mientras sigan por encima
            step = 0.5
            zlo = z0
            while lz[:, 2].min() < zlo - step:
                low = under(legs, -5, 5, zlo - step, zlo)
                if low is not None and low < top - 0.05:
                    break
                zlo -= step
            zhi = z1
            while lz[:, 2].max() > zhi + step:
                low = under(legs, -5, 5, zhi, zhi + step)
                if low is not None and low < top - 0.05:
                    break
                zhi += step
            top = min(top, under(legs, -5, 5, zlo, zhi) or top) - CLEAR
            mdl = bench(mid.split('.', 1)[1], tex, top, zlo, zhi).convert(store, problems)
            report.append(f'{mob}: banco, a {top:.1f} px de z {zlo:.1f} a {zhi:.1f}, se hunde '
                          f'{sink_of(pose, top, -5, 5, zlo, zhi):.2f} px')
        elif need == 'bar':
            # el camarero, detrás de su barra: la barra, por delante de lo más adelantado de sus manos
            hands = all_v(pose.dense(['left_arm', 'right_arm', 'left_item', 'right_item', 'mug']))
            front = float(np.percentile(hands[:, 2], 1)) if len(hands) else -9.0
            back_z = front - 0.5
            mdl = bar(mid.split('.', 1)[1], tex, back_z).convert(store, problems)
            report.append(f'{mob}: barra, de z {back_z - 12:.1f} a {back_z:.1f} (sus manos llegan hasta {front:.1f})')
        elif need == 'desk':
            paper = all_v(pose.verts(['paint']))
            if not len(paper):
                problems.append(f'{mob}: sin papel para su escritorio')
                continue
            top = float(paper[:, 1].min()) - CLEAR
            zc = float(np.median(paper[:, 2]))
            # que las rodillas no atraviesen el tablero
            knees = through(pose.dense(LEGS), top, 2.0, -9, 9, zc - 5.5, zc + 5.5)
            if knees > 0.05:
                problems.append(f'{mob}: las rodillas se meten {knees:.2f} px en el escritorio (a {top:.1f} px)')
            mdl = desk(mid.split('.', 1)[1], tex, top, zc).convert(store, problems)
            report.append(f'{mob}: escritorio, a {top:.1f} px en z {zc:.1f}')
        elif need == 'stool':
            top, zf, zb, back_z = seat_for(pose, 5.3, 10.7, back=False)
            report.append(f'{mob}: silla de la taberna, asiento a {top:.1f} px en z {(zf + zb) / 2:.1f}, se hunde '
                          f'{sink_of(pose, top, -5.3, 5.3, zf, zb):.2f} px')
            out.append((None, {'m': f'{TAVERN}.mueble_silla_taberna', 'p': [0.0, 0.0, round((zf + zb) / 2, 3)], 'r': 0.0,
                               's': [1.0, round(top / STOOL_TOP, 4), 1.0]}))
            continue
        elif need == 'feet_table':
            # los pies en alto (los fotogramas con los pies por encima de la cadera): la mesa larga de la taberna debajo,
            # con su borde de cerca bajo los talones (las piernas, rectas desde la cadera, no la atraviesan)
            feet = pose.verts(LEGS, lambda q: q[:, 1] < -8.0)
            hi = [k for k, f in enumerate(feet) if len(f) and f[:, 1].min() > 14.0]
            if not hi:
                problems.append(f'{mob}: no sube los pies')
                continue
            fv = np.concatenate([feet[k] for k in hi])
            top = float(np.percentile([feet[k][:, 1].min() for k in hi], SEAT_PCT)) - CLEAR
            edge = float(fv[:, 2].max()) - 0.3
            half = 9.16                       # media mesa de fondo (16 px × 1,1455)
            zc = edge - half
            legs = pose.dense(LEGS)
            pen = through(legs, top, 2.0 * top / TABLE_TOP, -21, 21, zc - half, zc + half)
            out.append((None, {'m': f'{TAVERN}.mueble_mesa_taberna', 'p': [0.0, 0.0, round(zc, 3)], 'r': 0.0,
                               's': [1.0, round(top / TABLE_TOP, 4), 1.0]}))
            report.append(f'{mob}: la mesa de la taberna bajo los pies, a {top:.1f} px, de z {zc - half:.1f} a {edge:.1f}; '
                          f'las piernas se meten {pen:.2f} px')
            continue
        else:
            continue
        out.append((mdl, {'m': mid, 'p': [0.0, 0.0, 0.0], 'r': 0.0, 's': [1, 1, 1]}))
    return out


STOOL_TOP = 8.0 * (2.13476 * 0.5)       # la cara de arriba de la silla de la taberna (px, en su tamaño de mueble)
TABLE_TOP = 14.0 * (2.29101 * 0.5)      # y la de su mesa (de 2 a 16 en el modelo: lo más bajo va al suelo)


# ------------------------------------------------------------------------------------------- todo

FURNITURE_MOB = {
    'type': 'ARMOR_STAND',
    'options': {'Despawn': 'persistent', 'KnockbackResistance': 10, 'Collidable': False, 'MovementSpeed': 0,
                'Invincible': True, 'Silent': True, 'PreventLeashing': True, 'AlwaysShowName': False},
    'display': '', 'health': 5, 'ai_goals': ['clear'], 'damage': 0, 'kind': 'entity', 'speed': 0.0,
}


def furniture_mob(mid):
    m = json.loads(json.dumps(FURNITURE_MOB))
    m['model'] = mid
    m['mechs'] = [
        {'m': 'model', 'a': {'mid': mid, 'save': 'true'}, 't': 'self', 'tr': 'onspawn'},
        {'m': 'model', 'a': {'mid': mid, 'save': 'true', 'delay': '5'}, 't': 'self', 'tr': 'onload'},
        {'m': 'cancelevent', 'a': {'sync': 'true'}, 't': 'self', 'tr': 'ondamaged'},
        {'m': 'cullconfig', 'a': {'vc': 'false', 'bkc': 'false', 'blc': 'false'}, 't': 'self', 'tr': 'onload'},
        {'m': 'cullconfig', 'a': {'vc': 'false', 'bkc': 'false', 'blc': 'false'}, 't': 'self', 'tr': 'onspawn'},
    ]
    return m


def write_model(res, mid, mdl):
    with open(os.path.join(res, 'npcs', 'models', mid + '.json'), 'w', encoding='utf-8') as f:
        json.dump(mdl, f, ensure_ascii=False, separators=(',', ':'))


def main(root, res):
    problems = []
    models_dir = os.path.join(res, 'npcs', 'models')
    # lo de antes (se rehace todo: volver a pasarlo no cambia nada)
    for f in os.listdir(models_dir):
        if f.split('.', 1)[-1].startswith(PREFIX):
            os.remove(os.path.join(models_dir, f))
    store = B.TextureStore(os.path.join(res, 'textures', 'npcs', TAVERN, 'me'), f'tfclient:textures/npcs/{TAVERN}/me/')
    tav = next((d for d in sorted(os.listdir(root)) if TAVERN_KEY in d.lower()), None)
    if not tav:
        print(f'muebles: falta el pack de la taberna en {root}')
        return 1
    nexo = os.path.join(root, tav, NEXO)
    import yaml
    conf = {}
    for d, _ds, fs in os.walk(os.path.join(nexo, 'items')):
        for f in fs:
            if f.endswith('.yml'):
                conf.update(yaml.safe_load(open(os.path.join(d, f), encoding='utf-8')) or {})
    furniture = []   # (mob, nombre, modelo)
    tavern_tex = None
    for item, (label, mob) in NEXO_ITEMS.items():
        c = conf.get(item) or {}
        model_ref = ((c.get('Pack') or {}).get('model') or '')
        ns, path = model_ref.split(':', 1) if ':' in model_ref else ('scenes', model_ref)
        mpath = os.path.join(nexo, 'pack', 'assets', ns, 'models', path + '.json')
        mj = json.load(open(mpath, encoding='utf-8'))
        tref = list((mj.get('textures') or {}).values())[0]
        tns, tpath = tref.split(':', 1) if ':' in tref else ('minecraft', tref)
        img = Image.open(os.path.join(nexo, 'pack', 'assets', tns, 'textures', tpath + '.png')).convert('RGBA')
        if item == 'scenes_tavern_chair':
            tavern_tex = img
        scale = nexo_scale(mj, c)
        b, _ = nexo_model(mpath, img, scale, mob)
        mid = f'{TAVERN}.{mob}'
        write_model(res, mid, b.convert(store, problems))
        furniture.append((mob, label, mid))
    tex = wood_atlas(tavern_tex)
    own = {
        'mueble_silla': chair('mueble_silla', tex, 9.0, -5.5, 3.5),
        'mueble_taburete_alto': bar_stool('mueble_taburete_alto', tex, 12.0, 2.0, -6.0),
        'mueble_sofa': couch('mueble_sofa', tex, 6.5, -8.0, 4.0, 8.0),
        'mueble_banco': bench('mueble_banco', tex, 8.0, -12.0, 12.0),
        'mueble_escritorio': desk('mueble_escritorio', tex, 13.0, 0.0),
        'mueble_barra': bar('mueble_barra', tex, 6.0),
    }
    for mob, b in own.items():
        mid = f'{TAVERN}.{mob}'
        write_model(res, mid, b.convert(store, problems))
        furniture.append((mob, OWN_ITEMS[mob], mid))

    # los de cada NPC
    cat_path = os.path.join(res, 'npcs', 'catalog.json')
    catalog = json.load(open(cat_path, encoding='utf-8'))
    props = {}
    report = []
    for (pack, mob), needs in NEEDS.items():
        c = next((x for x in catalog if x['id'] == pack), None)
        entry = c and next((n for n in c['npcs'] if n['mob'] == mob), None)
        if not entry:
            problems.append(f'falta el NPC {pack} {mob}')
            continue
        pose = Pose(res, pack, mob, entry)
        lst = []
        for mdl, ref in props_for(pose, needs, tex, store, problems, pack, mob, report):
            if mdl is not None:
                write_model(res, ref['m'], mdl)
            lst.append(ref)
        props[f'{pack}|{mob}'] = lst
    with open(os.path.join(res, 'npcs', 'props.json'), 'w', encoding='utf-8') as f:
        json.dump(props, f, ensure_ascii=False, indent=1, sort_keys=True)

    # los muebles en el panel (Scenes Tavern) y sus mobs en el pack
    tpath = os.path.join(res, 'npcs', TAVERN + '.json')
    prog = json.load(open(tpath, encoding='utf-8'))
    prog['mobs'] = {k: v for k, v in prog['mobs'].items() if not k.startswith(PREFIX)}
    prog['models'] = {k: v for k, v in prog['models'].items() if not k.startswith(PREFIX)}
    tc = next(x for x in catalog if x['id'] == TAVERN)
    tc['npcs'] = [n for n in tc['npcs'] if not n['mob'].startswith(PREFIX)]
    at = next((i for i, n in enumerate(tc['npcs']) if n.get('marker')), len(tc['npcs']))
    for mob, label, mid in furniture:
        prog['mobs'][mob] = furniture_mob(mid)
        prog['models'][mob] = mid
        tc['npcs'].insert(at, {'mob': mob, 'name': label, 'model': mid, 'state': None, 'walks': False, 'hostile': False,
                               'options': {}, 'furniture': True})
        at += 1
    with open(tpath, 'w', encoding='utf-8') as f:
        json.dump(prog, f, ensure_ascii=False, separators=(',', ':'))
    with open(cat_path, 'w', encoding='utf-8') as f:
        json.dump(catalog, f, ensure_ascii=False, indent=1)
    print(f'muebles: {len(furniture)} en el panel (Scenes Tavern), {sum(len(v) for v in props.values())} con '
          f'{len(props)} NPC; problemas: {len(problems)}')
    for r in report:
        print('   ·', r)
    for p in problems:
        print('   -', p)
    return len(problems)


def prop_world(ref):
    """La matriz del mueble en el espacio del modelo del NPC: la misma que el mod (SkillClient.renderProps): moverlo a p,
    girarlo r grados en Y y escalarlo s."""
    c, sn = math.cos(math.radians(ref['r'])), math.sin(math.radians(ref['r']))
    rot = np.array([[c, 0, sn, 0], [0, 1, 0, 0], [-sn, 0, c, 0], [0, 0, 0, 1]])
    t = np.eye(4)
    t[:3, 3] = ref['p']
    return t @ rot @ np.diag(list(ref['s']) + [1])


def sheet(out_png, res, only=()):
    """Cada NPC con sus muebles, de lado y en tres cuartos, en tres momentos de su escena (comprobarlo a ojo)."""
    import vfx_render as R
    import hoja_npcs as H
    from PIL import ImageDraw, ImageFont
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 11)
    props = json.load(open(os.path.join(res, 'npcs', 'props.json'), encoding='utf-8'))
    catalog = json.load(open(os.path.join(res, 'npcs', 'catalog.json'), encoding='utf-8'))
    skin = json.load(open(os.path.join(res, 'npcs', 'models', SKIN + '.json'), encoding='utf-8'))
    stex = R.load_textures(skin, os.path.join(res, 'textures', 'npcs', SKIN.split('.')[0], 'me'))
    rows = []
    for key, lst in sorted(props.items()):
        pack, mob = key.split('|')
        if only and not any(o in mob for o in only):
            continue
        entry = next(n for c in catalog if c['id'] == pack for n in c['npcs'] if n['mob'] == mob)
        prog = json.load(open(os.path.join(res, 'npcs', pack + '.json'), encoding='utf-8'))
        m = json.load(open(os.path.join(res, 'npcs', 'models', entry['model'] + '.json'), encoding='utf-8'))
        tex = R.load_textures(m, os.path.join(res, 'textures', 'npcs', pack, 'me'))
        if entry.get('autoskin'):
            m, tex = H.with_skin(m, tex, skin, stex)
        layers = P.scene_layers(prog['mobs'][mob], m)
        ln = max([m['anims'][a]['len'] for a, _, _ in layers] + [0])
        furn = []
        for ref in lst:
            pm = json.load(open(os.path.join(res, 'npcs', 'models', ref['m'] + '.json'), encoding='utf-8'))
            ptex = R.load_textures(pm, os.path.join(res, os.path.dirname(pm['tex'][0]['path'].split(':', 1)[1])))
            furn.append((pm, ptex, P.pose(pm, {}), prop_world(ref)))
        cells = []
        for k, (yaw, pitch) in enumerate(((90, 0), (90, 0), (90, 0), (-35, -18), (-35, -18), (150, -15))):
            t = ln * (k % 3) / 3
            mats = P.pose(m, P.extra_of(m, [(a, t, w, ov) for a, w, ov in layers]))
            tris = R.model_tris(m, tex, mats)
            for pm, ptex, pmats, w in furn:
                tris += R.model_tris(pm, ptex, pmats, world=w)
            img = R.raster(tris, 220, yaw, pitch, 85, np.array([0.0, 12.0, -3.0]), 40)
            bg = Image.new('RGBA', img.size, (150, 190, 230, 255))
            ImageDraw.Draw(bg).line([(0, 0), (0, 0)])
            bg.alpha_composite(img)
            cells.append((f'{mob.replace("scene_townsfolk_", "")} t={t:.1f}', bg.convert('RGB')))
        rows.append(cells)
    W = 220
    img = Image.new('RGB', (6 * W, len(rows) * (W + 16)), (255, 255, 255))
    d = ImageDraw.Draw(img)
    for r, cells in enumerate(rows):
        for c, (lab, im) in enumerate(cells):
            img.paste(im, (c * W, r * (W + 16) + 16))
            d.text((c * W + 3, r * (W + 16) + 2), lab, fill=(0, 0, 0), font=font)
    img.save(out_png)
    print(out_png, len(rows), 'NPC')


if __name__ == '__main__':
    args = sys.argv[1:]
    res = P.RES
    if '--res' in args:
        i = args.index('--res')
        res = args[i + 1]
        del args[i:i + 2]
    if args and args[0] == '--hoja':
        sheet(args[1], res, args[2:])
        sys.exit(0)
    sys.exit(1 if main(args[0], res) else 0)
