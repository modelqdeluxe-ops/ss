"""Qué modelo se pone cada pack en la espalda (BACKPACK) y en la cabeza (HELMET) según su configuración de
HMCCosmetics. Es el modelo «puesto» que pensó el autor; el del inventario puede ser otro (más grande o con otra forma).

worn_models(pack_dir) -> {'BACKPACK': ['ns:ruta', ...], 'HELMET': [...]}
"""
import glob
import json
import os
import re

import yaml


def _setups(pack_dir):
    files = glob.glob(os.path.join(pack_dir, '**', 'HMCCosmetics*', '**', '*.yml'), recursive=True)
    files = [f for f in files if '/menus/' not in f.replace('\\', '/')]
    # Primero el de 1.20.1 (soporte de armadura, como lo calcula el mod)
    files.sort(key=lambda f: (0 if '1.20.1' in f else 1, f))
    return files


def _overrides(pack_dir):
    """custom_model_data → modelo, por material (potion, paper...)."""
    out = {}
    for f in glob.glob(os.path.join(pack_dir, '**', 'assets', 'minecraft', 'models', 'item', '*.json'), recursive=True):
        mat = os.path.basename(f)[:-5]
        try:
            data = json.load(open(f, encoding='utf-8'))
        except Exception:
            continue
        for o in data.get('overrides', []) or []:
            cmd = (o.get('predicate') or {}).get('custom_model_data')
            if cmd is not None and o.get('model'):
                out.setdefault((mat, int(cmd)), o['model'])
    return out


def _itemsadder(pack_dir):
    """id de ItemsAdder → modelo."""
    out = {}
    for f in glob.glob(os.path.join(pack_dir, '**', 'configs', '**', '*.yml'), recursive=True):
        try:
            data = yaml.safe_load(open(f, encoding='utf-8'))
        except Exception:
            continue
        if not isinstance(data, dict):
            continue
        ns = (data.get('info') or {}).get('namespace')
        for key, item in (data.get('items') or {}).items():
            res = (item or {}).get('resource') or {}
            path = res.get('model_path')
            if ns and path:
                out[f'{ns}:{key}'] = f'{ns}:{path}'
    return out


def worn_models(pack_dir):
    result = {'BACKPACK': [], 'HELMET': []}
    files = _setups(pack_dir)
    if not files:
        return result
    overrides = None
    ia = None
    seen = set()
    for f in files:
        try:
            data = yaml.safe_load(open(f, encoding='utf-8'))
        except Exception:
            continue
        if not isinstance(data, dict):
            continue
        for key, entry in data.items():
            if not isinstance(entry, dict) or entry.get('slot') not in result:
                continue
            item = entry.get('item') or {}
            mat = str(item.get('material', ''))
            ref = None
            if mat.startswith('itemsadder:'):
                ia = ia if ia is not None else _itemsadder(pack_dir)
                ref = ia.get(mat[len('itemsadder:'):])
            elif item.get('model-data') is not None:
                overrides = overrides if overrides is not None else _overrides(pack_dir)
                ref = overrides.get((mat.lower(), int(item['model-data'])))
            if ref and (entry['slot'], ref) not in seen:
                seen.add((entry['slot'], ref))
                result[entry['slot']].append(ref)
        if result['BACKPACK'] or result['HELMET']:
            break  # basta con una instalación
    return result


if __name__ == '__main__':
    import sys
    for d in sys.argv[1:]:
        print(d, worn_models(d))
