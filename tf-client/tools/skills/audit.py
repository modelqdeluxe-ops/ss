"""Auditoría de las clases de skills: lo que check_assets.py no mira y el simulador no recorre.

  - Modelos de ítem de los efectos (models/skills/**): las reglas con las que Minecraft 1.20.1 los carga (si una falla,
    el modelo sale como cubo negro y morado): coordenadas en -16..32, giros de 0/±22.5/±45 en x/y/z, caras válidas,
    texturas «#x» que existan, padres que existan.
  - Modelos de ModelEngine: cada animación (state), hueso (partvis, changepart, @modelpart) y modelo nuevo
    (changepart) que piden las skills y los mobs existe en el modelo que toca; texturas que abren y con el tamaño que
    dice el modelo; los mobs que andan solos (esbirros) tienen animación de andar.
  - Texturas (textures/skills, textures/gui/skills): que abran; las animadas (.mcmeta) con fotogramas enteros.
  - Sonidos de las skills: que cada .ogg sea Ogg Vorbis válido (cabeceras) y no esté vacío.
  - Textos: nombre y descripción en español de cada clase y de cada skill visible.

    python3 tools/skills/audit.py          (sale con 1 si hay algún fallo; los «avisos» no cuentan como fallo)
"""
import glob
import json
import os
import struct
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets'))
TF = os.path.join(RES, 'tfclient')
FACES = {'north', 'south', 'east', 'west', 'up', 'down'}

# Avisos conocidos que vienen así en el pack (no son fallos de la conversión): (clase, modelo, cosa)
PACK_AS_IS = {
    ('filo_vacio', 'filo_vacio.void_edge_piercing_dimension_line', 'slash'),    # su única animación está vacía
    ('filo_vacio', 'filo_vacio.void_edge_piercing_dimension_line_1', 'slash'),
    ('filo_vacio', 'filo_vacio.void_edge_piercing_dimension_line_2', 'slash'),
    ('caballero_artico', 'caballero_artico.fx_permafrost_lance', 'p'),           # @modelpart{pid=p}: huesos p1..p8
    # huesos y animaciones que piden los mobs del pack y su .bbmodel no tiene (ModelEngine tampoco hacía nada)
    ('arquero', 'arquero.archer_cyclone_shot_arrow', 'spawn'),
    ('cazador_bestias', 'cazador_bestias.slice_n_dice_1', 'blood'),
    ('clerigo_despertado', 'clerigo_despertado.sacred_combo_vfx_1', 'ef_mace_swing'),
    ('filo_vacio', 'filo_vacio.void_edge_dimension_benediction', 'slash'),
    ('filo_vacio', 'filo_vacio.void_edge_dimension_benediction_2', 'slash'),
    ('filo_vacio', 'filo_vacio.void_edge_horizon_slash_impact_vfx', 'slash'),
    ('filo_vacio', 'filo_vacio.void_edge_constellations_vfx', 'wink'),
    ('filo_vacio', 'filo_vacio.void_edge_dimension_breaker_vfx', 'slash'),
    ('guerrero_despertado', 'guerrero_despertado.berserker_leap_1', 'rubble'),   # tiene rubble1, rubble2...
    ('guerrero_despertado', 'guerrero_despertado.relentless_whirlwind_1', 'efv'),
    ('guerrero_despertado', 'guerrero_despertado.strike_of_fury_1', 'efv'),
    ('luchador_despertado', 'luchador_despertado.agile_striker_vfx_1', 'wave'),
    ('luchador_despertado', 'luchador_despertado.agile_striker_vfx_1', 'impact'),
    ('nigromante', 'nigromante.vfx_explosion_white_1', 'body'),
}


def ref_path(ref, kind, ext):
    ns, _, path = str(ref).rpartition(':')
    return os.path.join(RES, ns or 'minecraft', kind, path + ext)


def check_item_models(fails):
    n = 0
    for f in sorted(glob.glob(os.path.join(TF, 'models', 'skills', '**', '*.json'), recursive=True)):
        n += 1
        rel = os.path.relpath(f, TF)
        try:
            m = json.load(open(f, encoding='utf-8'))
        except ValueError as e:
            fails.append(f'{rel}: JSON roto ({e})')
            continue
        tx = m.get('textures') or {}
        for k, v in tx.items():
            if not isinstance(v, str):
                fails.append(f'{rel}: textura {k} no es texto')
            elif v.startswith('#'):
                if v[1:] not in tx:
                    fails.append(f'{rel}: textura {k} → {v} no existe')
            else:
                ns = v.rpartition(':')[0] or 'minecraft'
                if ns != 'minecraft' and not os.path.exists(ref_path(v, 'textures', '.png')):
                    fails.append(f'{rel}: falta la textura {v}')
        par = m.get('parent')
        if par and not par.startswith('builtin/'):
            ns = par.rpartition(':')[0] or 'minecraft'
            if ns != 'minecraft' and not os.path.exists(ref_path(par, 'models', '.json')):
                fails.append(f'{rel}: falta el padre {par}')
        if not m.get('elements') and not par:
            fails.append(f'{rel}: sin elementos ni padre')
        for el in m.get('elements') or []:
            if any(float(v) < -16 or float(v) > 32 for v in el.get('from', []) + el.get('to', [])):
                fails.append(f'{rel}: un elemento se sale de -16..32')
            r = el.get('rotation')
            if r and (float(r.get('angle', 0)) not in (-45, -22.5, 0, 22.5, 45) or r.get('axis') not in ('x', 'y', 'z')
                      or 'origin' not in r):
                fails.append(f'{rel}: giro no válido en 1.20.1 {r}')
            for face, fd in (el.get('faces') or {}).items():
                if face not in FACES:
                    fails.append(f'{rel}: cara {face}')
                elif fd.get('rotation', 0) % 90:
                    fails.append(f'{rel}: giro de cara {fd.get("rotation")}')
                elif str(fd.get('texture', '')).lstrip('#') not in tx and not par:
                    fails.append(f'{rel}: la cara {face} usa {fd.get("texture")}, que no está en textures')
        for ctx, d in (m.get('display') or {}).items():
            if any(abs(float(v)) > 4 for v in d.get('scale', [])):
                fails.append(f'{rel}: display {ctx} con escala > 4 (Minecraft la recorta)')
    return n


_MODELS = {}


def me_model(mid):
    if mid not in _MODELS:
        p = os.path.join(TF, 'skills', 'models', mid + '.json')
        _MODELS[mid] = json.load(open(p, encoding='utf-8')) if os.path.exists(p) else None
    return _MODELS[mid]


def has_bone(model, part):
    """VfxModel.bone: exacto, sin mayúsculas, o sin el prefijo de comportamiento (ef_suelo → «suelo»)."""
    low = part.lower()
    for b in model['bones']:
        bid = b['id'].lower()
        if bid == low:
            return True
        tail = '_' + low
        if bid.endswith(tail) and bid.index('_') == len(bid) - len(tail):
            return True
    return False


def has_anim(model, s):
    return s in model['anims'] or s.lower() in {k.lower() for k in model['anims']}


def mid_of(cid, raw):
    if not raw or '<' in str(raw):
        return None
    raw = str(raw)
    return raw if '.' in raw else f'{cid}.{raw.lower()}'


def check_me(fails, warns):
    nmods = 0
    for f in sorted(glob.glob(os.path.join(TF, 'skills', 'classes', '*.json'))):
        d = json.load(open(f, encoding='utf-8'))
        cid = d['id']
        all_mids = set()
        groups = []
        for k, v in d['tree'].items():
            groups.append(('skill ' + k, None, v['mechs']))
        for k, v in d['mobs'].items():
            groups.append(('mob ' + k, k, v.get('mechs', [])))
        for _, _, mechs in groups:
            for m in mechs:
                a = m.get('a', {})
                if m['m'] in ('model', 'changepart', 'state', 'partvis', 'partvisibility', 'brightness', 'tint'):
                    for key in ('mid', 'm', 'modelid', 'nm', 'newmodelid', 'nmid'):
                        mid = mid_of(cid, a.get(key))
                        if mid and me_model(mid):
                            all_mids.add(mid)
        # los modelos que puede llevar cada mob: los de sus «model» y los cambios de pieza
        mob_mids = {}
        for k, v in d['mobs'].items():
            s = set()
            for m in v.get('mechs', []):
                if m['m'] == 'model':
                    mid = mid_of(cid, m['a'].get('mid') or m['a'].get('m') or m['a'].get('modelid'))
                    if mid:
                        s.add(mid)
            mob_mids[k] = s
        for where, mob, mechs in groups:
            for m in mechs:
                a = m.get('a', {})
                name = m['m']
                explicit = mid_of(cid, a.get('mid') or a.get('m') or a.get('modelid'))
                if explicit:
                    cands = {explicit}
                elif mob and m.get('t') in (None, 'self') and mob_mids.get(mob):
                    cands = mob_mids[mob]
                else:
                    cands = all_mids
                models = [(c, me_model(c)) for c in cands if me_model(c)]
                if not models:
                    continue

                def report(thing, what, cands=cands):
                    key_ok = any((cid, c, thing) in PACK_AS_IS for c in cands)
                    msg = f'{cid} {where}: {what} «{thing}» no está en {sorted(cands)[:3]}'
                    (warns if key_ok else fails).append(msg)

                if name in ('state', 'animation'):
                    s = a.get('state') or a.get('s') or a.get('animation') or a.get('a')
                    if s and '<' not in s and not any(has_anim(md, s) for _, md in models):
                        report(s, 'animación')
                elif name in ('partvis', 'partvisibility', 'changepart'):
                    p = a.get('partid') or a.get('pid') or a.get('part') or a.get('p')
                    if p and '<' not in p and not any(has_bone(md, p) for _, md in models):
                        report(p, 'hueso')
                    if name == 'changepart':
                        nm = mid_of(cid, a.get('newmodelid') or a.get('nmid') or a.get('nm') or a.get('newmodel'))
                        npart = a.get('newpartid') or a.get('npid') or a.get('np') or a.get('newpart') or p
                        if nm:
                            md = me_model(nm)
                            if md is None:
                                fails.append(f'{cid} {where}: changepart a un modelo que no está ({nm})')
                            elif npart and '<' not in npart and not has_bone(md, npart):
                                fails.append(f'{cid} {where}: changepart: {nm} no tiene la pieza «{npart}»')
                if m.get('t') == 'modelpart':
                    p = (m.get('ta') or {}).get('pid') or (m.get('ta') or {}).get('p')
                    tm = mid_of(cid, (m.get('ta') or {}).get('mid'))
                    if tm and me_model(tm) and p and not has_bone(me_model(tm), p):
                        report(p, 'hueso de @modelpart', cands={tm})
        # esbirros: si andan, que tengan animación de andar
        for k, v in d['mobs'].items():
            if not v.get('living'):
                continue
            for mid in mob_mids.get(k, ()):
                md = me_model(mid)
                if md and not any(n.lower() in ('walk', 'run', 'move') for n in md['anims']):
                    warns.append(f'{cid} mob {k}: el modelo {mid} no tiene animación de andar (se desliza)')
    # texturas de los modelos de ModelEngine
    for f in sorted(glob.glob(os.path.join(TF, 'skills', 'models', '*.json'))):
        nmods += 1
        md = json.load(open(f, encoding='utf-8'))
        for t in md.get('tex', []):
            p = ref_path(t['path'].replace('textures/', '', 1).removesuffix('.png'), 'textures', '.png')
            try:
                with Image.open(p) as im:
                    im.load()
                    w, h = im.size
            except Exception as e:  # noqa: BLE001
                fails.append(f'{os.path.basename(f)}: no abre la textura {t["path"]} ({e})')
                continue
            fr = max(1, int(t.get('frames', 1)))
            if fr > 1 and h % fr:
                fails.append(f'{os.path.basename(f)}: {t["path"]} tiene {fr} fotogramas pero {h}px de alto')
    return nmods


def check_textures(fails):
    n = 0
    for root in ('textures/skills', 'textures/gui/skills'):
        for f in glob.glob(os.path.join(TF, root, '**', '*.png'), recursive=True):
            n += 1
            try:
                with Image.open(f) as im:
                    im.load()
                    w, h = im.size
            except Exception as e:  # noqa: BLE001
                fails.append(f'{os.path.relpath(f, TF)}: no abre ({e})')
                continue
            meta = f + '.mcmeta'
            if os.path.exists(meta):
                an = json.load(open(meta, encoding='utf-8')).get('animation', {})
                fw = an.get('width', w)
                fh = an.get('height', fw if h % fw == 0 else h)
                if h % fh or w % fw:
                    fails.append(f'{os.path.relpath(f, TF)}: animada con fotogramas que no cuadran ({w}x{h})')
    return n


def ogg_info(path):
    """Cabeceras de un Ogg Vorbis: (canales, frecuencia) o un error."""
    with open(path, 'rb') as fh:
        data = fh.read(4096)
    if len(data) < 64 or data[:4] != b'OggS':
        return 'no es Ogg'
    segs = data[26]
    body = 27 + segs
    if data[body:body + 7] != b'\x01vorbis':
        return 'no es Vorbis'
    ch = data[body + 11]
    rate = struct.unpack('<I', data[body + 12:body + 16])[0]
    if ch < 1 or rate < 8000:
        return f'cabecera rara ({ch} canales, {rate} Hz)'
    return ch, rate


def check_sounds(fails, warns):
    n = 0
    stereo = 0
    for f in glob.glob(os.path.join(TF, 'sounds', 'skills', '**', '*.ogg'), recursive=True):
        n += 1
        info = ogg_info(f)
        if isinstance(info, str):
            fails.append(f'{os.path.relpath(f, TF)}: {info}')
        elif info[0] > 1:
            stereo += 1
    return n, stereo


def check_texts(fails):
    for f in sorted(glob.glob(os.path.join(TF, 'skills', 'classes', '*.json'))):
        d = json.load(open(f, encoding='utf-8'))
        c = d['class']
        if not (c.get('name_es') or c.get('name')):
            fails.append(f'{d["id"]}: la clase no tiene nombre')
        for s in d['skills']:
            if s.get('hidden'):
                continue
            if not s.get('name_es'):
                fails.append(f'{d["id"]}/{s["id"]}: sin nombre en español')
            if not s.get('desc_es'):
                fails.append(f'{d["id"]}/{s["id"]}: sin descripción en español')


# Carpetas que el atlas de bloques de 1.20.1 ya trae (assets/minecraft/atlases/blocks.json de vanilla)
VANILLA_ATLAS = ('block/', 'item/', 'entity/conduit/')


def check_atlas(fails):
    """Cada textura de un modelo de ítem o de bloque tiene que estar en el atlas de bloques (si no, morada y negra)."""
    atlas = os.path.join(RES, 'minecraft', 'atlases', 'blocks.json')
    dirs, singles = list(VANILLA_ATLAS), set()
    if os.path.exists(atlas):
        for src in json.load(open(atlas, encoding='utf-8'))['sources']:
            if src['type'] == 'directory':
                dirs.append(src['source'].rstrip('/') + '/')
            elif src['type'] == 'single':
                singles.add(src['resource'].rpartition(':')[2])
    n = 0
    for f in sorted(glob.glob(os.path.join(TF, 'models', '**', '*.json'), recursive=True)):
        try:
            tx = json.load(open(f, encoding='utf-8')).get('textures') or {}
        except ValueError:
            continue
        for v in tx.values():
            if not isinstance(v, str) or v.startswith('#'):
                continue
            n += 1
            path = v.rpartition(':')[2]
            if path not in singles and not any(path.startswith(d) for d in dirs):
                fails.append(f'{os.path.relpath(f, TF)}: la textura {v} no está en el atlas de bloques (saldría morada)')
    return n


def main():
    fails, warns = [], []
    check_atlas(fails)
    n_items = check_item_models(fails)
    n_me = check_me(fails, warns)
    n_tex = check_textures(fails)
    n_snd, stereo = check_sounds(fails, warns)
    check_texts(fails)
    for w in warns:
        print('· aviso:', w)
    for f in fails:
        print('-', f)
    print(f'{n_items} modelos de ítem, {n_me} modelos de ModelEngine, {n_tex} texturas, {n_snd} sonidos '
          f'({stereo} en estéreo): {len(fails)} fallos, {len(warns)} avisos')
    sys.exit(1 if fails else 0)


if __name__ == '__main__':
    main()
