"""Inventario de los packs de skills (clases de MMOCore / MythicLib / MythicMobs / ModelEngine / MMOItems...).

    python3 tools/skills/inventario.py <carpeta con un pack descomprimido por subcarpeta> [salida.json]

Para cada pack saca: las clases (MMOCore), las skills con su nombre, descripción, cooldown y maná, cómo se lanzaban
(clic del arma de MMOItems o tecla de la clase), la skill de MythicMobs que ejecutan, las armas y armaduras con
habilidades, y cuántos modelos (.bbmodel), sonidos (.ogg) e iconos trae. Los packs son comprados: este script solo
los lee, no copia nada al repo.
"""
import json
import os
import re
import sys

import yaml


def load(path):
    try:
        with open(path, encoding='utf-8', errors='ignore') as f:
            text = f.read()
        # tabuladores sueltos rompen PyYAML
        return yaml.safe_load(text.replace('\t', '  ')) or {}
    except Exception as e:  # noqa: BLE001
        return {'__error__': str(e)}


def norm(name):
    """Id de skill como lo usan MMOCore/MMOItems: mayúsculas, guiones y espacios a «_»."""
    return re.sub(r'[\s\-]+', '_', os.path.splitext(name)[0]).upper()


def leveled(v, level=100):
    """Valor de MMOCore a un nivel: base + por nivel, dentro de [min, max]. Sin límites puestos se queda la base
    (si no, con 100 niveles saldrían valores sin sentido)."""
    try:
        base = float(v.get('base') or 0)
        per = float(v.get('per-level') or 0)
        lo = float(v['min']) if v.get('min') is not None else 0.0
        hi = float(v['max']) if v.get('max') is not None else 0.0
        if per == 0 or (lo == 0 and hi == 0):
            return round(base, 3)
        val = base + per * (level - 1)
        return round(max(min(lo, hi), min(max(lo, hi), val)), 3)
    except (TypeError, ValueError):
        return v.get('base')


def strip_colors(s):
    s = str(s or '')
    s = re.sub(r'<[^>]+>', '', s)
    s = re.sub(r'[&§][0-9a-fk-orx]', '', s, flags=re.I)
    return s.strip()


def files(root, ext):
    for d, _, fs in os.walk(root):
        for f in fs:
            if f.lower().endswith(ext):
                yield os.path.join(d, f)


def scan(pack_dir):
    out = {'pack': os.path.basename(pack_dir), 'classes': [], 'skills': {}, 'weapons': [], 'armor': [],
           'mm_skill_files': [], 'bbmodels': 0, 'sounds': 0, 'icons': 0, 'errors': []}
    for p in files(pack_dir, '.bbmodel'):
        out['bbmodels'] += 1
    for p in files(pack_dir, '.ogg'):
        out['sounds'] += 1
    for p in files(pack_dir, '.png'):
        if 'icon' in os.path.basename(p).lower():
            out['icons'] += 1
    for p in files(pack_dir, '.json'):
        if re.search(r'icon', os.path.basename(p), re.I):
            out['icons'] += 1
    ymls = list(files(pack_dir, '.yml')) + list(files(pack_dir, '.yaml'))
    for p in ymls:
        lp = '/' + os.path.relpath(p, pack_dir).lower()  # solo lo de dentro del pack
        data = load(p)
        if isinstance(data, dict) and '__error__' in data:
            out['errors'].append(f'{os.path.relpath(p, pack_dir)}: {data["__error__"][:120]}')
            continue
        if not isinstance(data, dict):
            continue
        if '/classes/' in lp and 'mmocore' in lp:
            disp = data.get('display', {}) or {}
            out['classes'].append({'file': os.path.basename(p), 'name': strip_colors(disp.get('name')),
                                   'lore': [strip_colors(x) for x in (disp.get('lore') or [])],
                                   'skills': list((data.get('skills') or {}).keys()),
                                   # ranuras de pasiva (formula: <PASSIVE>): solo esas llevan pasivas
                                   'passive_slots': sum(1 for v in (data.get('skill-slots') or {}).values()
                                                        if isinstance(v, dict) and '<PASSIVE>' in str(v.get('formula', ''))),
                                   'skill_conf': {norm(k): v for k, v in (data.get('skills') or {}).items()
                                                  if isinstance(v, dict)}})
        elif '/skills/' in lp and 'mmocore' in lp:
            sid = norm(os.path.basename(p))
            s = out['skills'].setdefault(sid, {})
            s['name'] = strip_colors(data.get('name'))
            s['lore'] = [strip_colors(x) for x in (data.get('lore') or []) if strip_colors(x)]
            # Valores con nivel (daño, duración, cooldown...): la clase se compra entera, así que van al nivel máximo
            mods = {}
            for k, v in data.items():
                if isinstance(v, dict) and 'base' in v:
                    mods[str(k).lower()] = leveled(v)
            s['mods'] = mods
            cd = data.get('cooldown') or {}
            s['cooldown'] = mods.get('cooldown') if isinstance(cd, dict) else cd
            mana = data.get('mana') or {}
            s['mana'] = mods.get('mana') if isinstance(mana, dict) else mana
            s['icon_item'] = data.get('material')
            if data.get('passive-type'):
                # skill pasiva de MMOCore: se dispara sola (TIMER cada «timer» s, o al atacar, recibir daño...)
                timer = data.get('timer') or {}
                prev = s.get('passive') or {}
                t = timer.get('base') if isinstance(timer, dict) else timer
                s['passive'] = {'type': str(data['passive-type']).upper(), 'timer': t or prev.get('timer')}
        elif 'mythiclib' in lp and '/skill' in lp:
            # un archivo puede tener una skill (formato plano) o varias (clave = id)
            entries = {norm(os.path.basename(p)): data} if 'mythicmobs-skill-id' in data or 'mythic-skill' in data \
                else {k.upper(): v for k, v in data.items() if isinstance(v, dict)}
            for sid, v in entries.items():
                s = out['skills'].setdefault(sid, {})
                src = str(v.get('source') or '')
                s['mm'] = v.get('mythicmobs-skill-id') or v.get('mythic-skill') or \
                    (src.split(':', 1)[1] if src.lower().startswith('mythicmobs:') else None) or s.get('mm')
                s.setdefault('name', strip_colors(v.get('name')))
                # MythicLib nuevo: parameters: {damage: {player: {base, per-level, min, max}}}, trigger, icon, lore
                params = v.get('parameters')
                if isinstance(params, dict):
                    mods = dict(s.get('mods') or {})
                    for k, pv in params.items():
                        if not isinstance(pv, dict):
                            continue
                        lv = pv.get('player') if isinstance(pv.get('player'), dict) else pv
                        if 'base' in lv:
                            mods.setdefault(str(k).lower(), leveled(lv))
                        elif pv.get('item') is not None:
                            mods.setdefault(str(k).lower(), pv.get('item'))
                    s['mods'] = mods
                    if s.get('cooldown') is None and 'cooldown' in mods:
                        s['cooldown'] = mods['cooldown']
                    if s.get('mana') is None and 'mana' in mods:
                        s['mana'] = mods['mana']
                if not s.get('lore') and v.get('lore'):
                    s['lore'] = [strip_colors(x) for x in v['lore'] if strip_colors(x)]
                if not s.get('icon_item') and v.get('icon'):
                    s['icon_item'] = v.get('icon')
                trig = str(v.get('trigger') or '').upper()
                if trig in ('TIMER', 'DAMAGED', 'ATTACK', 'KILL_ENTITY', 'SHOOT_BOW', 'DEATH', 'SNEAK', 'LOGIN') \
                        and not s.get('passive'):
                    s['passive'] = {'type': trig, 'timer': (s.get('mods') or {}).get('timer')}
                if v.get('cooldown') is not None:
                    s.setdefault('cooldown', v.get('cooldown'))
                if v.get('passive-type'):
                    if not s.get('passive'):
                        s['passive'] = {'type': str(v['passive-type']).upper(), 'timer': v.get('timer')}
                    elif not s['passive'].get('timer') and v.get('timer'):
                        s['passive']['timer'] = v.get('timer')
                p = s.get('passive')
                if p and p.get('type') == 'TIMER' and not p.get('timer'):
                    t = (s.get('mods') or {}).get('timer')
                    if t:
                        p['timer'] = t
                    else:
                        # «passive-type: TIMER» con el temporizador a 0: en la clase va en una ranura normal y se
                        # lanza con su tecla (el Mando del Nigromante, la Andanada del Piromante)
                        s.pop('passive')
        elif 'mmoitems' in lp and '/item/' in lp:
            for iid, it in data.items():
                if not isinstance(it, dict):
                    continue
                base = it.get('base') or it
                if not isinstance(base, dict):
                    continue
                entry = {'id': iid, 'file': os.path.basename(p), 'material': base.get('material'),
                         'cmd': base.get('custom-model-data'), 'name': strip_colors(base.get('name')),
                         'abilities': []}
                for ab in (base.get('ability') or {}).values():
                    if isinstance(ab, dict):
                        vals = {}
                        for k, v in ab.items():
                            if k in ('type', 'mode'):
                                continue
                            try:
                                vals[str(k).lower()] = float(v)
                            except (TypeError, ValueError):
                                pass
                        entry['abilities'].append({'skill': str(ab.get('type', '')).upper(), 'mode': ab.get('mode'),
                                                   'cooldown': ab.get('cooldown'), 'values': vals})
                mat = str(base.get('material', '')).upper()
                if any(k in mat for k in ('HELMET', 'CHESTPLATE', 'LEGGINGS', 'BOOTS')) or 'armor' in os.path.basename(p).lower():
                    out['armor'].append(entry)
                else:
                    out['weapons'].append(entry)
        elif 'mmoitems' in lp and '/skill/' in lp:
            sid = norm(os.path.basename(p))
            s = out['skills'].setdefault(sid, {})
            s.setdefault('name', strip_colors(data.get('name')))
        elif 'mythicmobs' in lp and '/skills/' in lp.replace('/skill/', '/skills/'):
            out['mm_skill_files'].append(os.path.relpath(p, pack_dir))
    # cómo se lanzaba cada skill (arma)
    for w in out['weapons']:
        for ab in w['abilities']:
            s = out['skills'].setdefault(ab['skill'], {})
            s.setdefault('modes', []).append(ab['mode'])
            if s.get('cooldown') in (None, 0) and ab.get('cooldown'):
                s['cooldown'] = ab['cooldown']
            # los valores de la habilidad del arma (daño...) cuando la skill no trae los suyos
            mods = s.setdefault('mods', {})
            for k, v in (ab.get('values') or {}).items():
                if not mods.get(k):
                    mods[k] = v
    return out


def main():
    root = sys.argv[1]
    dest = sys.argv[2] if len(sys.argv) > 2 else None
    packs = []
    for name in sorted(os.listdir(root)):
        d = os.path.join(root, name)
        if os.path.isdir(d):
            packs.append(scan(d))
    for p in packs:
        cls = ', '.join(c['name'] or c['file'] for c in p['classes']) or '—'
        weap = len(p['weapons'])
        sk = [s for s in p['skills'].values() if s.get('name') or s.get('mm')]
        print(f"{p['pack'][:42]:42s} clases: {cls[:30]:30s} skills: {len(sk):2d}  armas: {weap:2d}  armadura: {len(p['armor']):2d}"
              f"  bb: {p['bbmodels']:3d}  ogg: {p['sounds']:3d}  errores: {len(p['errors'])}")
    if dest:
        with open(dest, 'w', encoding='utf-8') as f:
            json.dump(packs, f, ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main()
