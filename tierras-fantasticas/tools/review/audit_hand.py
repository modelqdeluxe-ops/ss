"""Auditoría de los objetos de mano del mod: modelo, padres, display de la mano, texturas visibles."""
import json, os, sys, collections
from PIL import Image
A = '/home/user/ss/tf-client/src/main/resources/assets/tfclient'
VAN = {
 'minecraft:item/generated': {'thirdperson_righthand': {'rotation': [0,0,0], 'translation': [0,3,1], 'scale': [0.55]*3}},
 'minecraft:item/handheld': {'thirdperson_righthand': {'rotation': [0,-90,55], 'translation': [0,4,0.5], 'scale': [0.85]*3}},
 'minecraft:item/handheld_rod': {'thirdperson_righthand': {'rotation': [0,90,55], 'translation': [0,4,2.5], 'scale': [0.85]*3}},
}
HAND = {'sword','heavy','axe','pickaxe','shovel','hoe','bow','crossbow','fishing_rod','shield','trident','held','balloon'}
def load(ref, depth=0):
    if ref.startswith('minecraft:') or ':' not in ref and ref.startswith('item/'):
        r = ref if ref.startswith('minecraft:') else 'minecraft:' + ref
        return {'_vanilla': r, 'display': VAN.get(r, {}).get('display', VAN.get(r, {}))}
    ns, p = ref.split(':', 1)
    path = os.path.join(A, 'models', p + '.json')
    if not os.path.exists(path): return None
    m = json.load(open(path))
    m['_path'] = path
    if m.get('parent') and depth < 6:
        base = load(m['parent'], depth + 1)
        if base is None:
            m['_bad_parent'] = m['parent']
        else:
            d = dict(base.get('display', {})); d.update(m.get('display', {})); m['_display'] = d
            if 'elements' not in m and base.get('elements'): m['elements'] = base['elements']
            t = dict(base.get('textures', {})); t.update(m.get('textures', {})); m['textures'] = t
            m['_chain'] = [m['parent']] + base.get('_chain', [])
    m.setdefault('_display', m.get('display', {}))
    return m
def tex_ok(ref):
    if not ref.startswith('tfclient:'): return 'vanilla'
    p = os.path.join(A, 'textures', ref.split(':',1)[1] + '.png')
    if not os.path.exists(p): return 'missing'
    im = Image.open(p).convert('RGBA')
    if im.getextrema()[3][1] < 20: return 'transparent'
    return 'ok'
reg = json.load(open(os.path.join(A, 'tf_sets.json')))
issues = collections.defaultdict(list)
count = 0
for s in reg['sets']:
    for it in s['items']:
        if it['type'] not in HAND: continue
        count += 1
        refs = [f"tfclient:item/{it['id']}"]
        m0 = load(refs[0])
        if not m0: issues['sin modelo'].append(it['id']); continue
        for ov in m0.get('overrides', []):
            refs.append(ov['model'])
        for ref in refs:
            m = load(ref)
            key = it['id'] + ('' if ref == refs[0] else ' > ' + ref.split('/')[-1])
            if not m: issues['override sin modelo'].append(key); continue
            if m.get('_bad_parent'): issues['padre que no existe'].append(key + ' ' + m['_bad_parent'])
            d = m['_display'].get('thirdperson_righthand')
            gen = not m.get('elements')
            if not d:
                issues['sin pose en la mano' + (' (plano)' if gen else ' (3D)')].append(key + ' padre=' + str(m.get('parent')))
            else:
                sc = d.get('scale', [1,1,1])
                if any(abs(v) < 0.05 for v in sc): issues['escala 0 en la mano'].append(key + str(sc))
                if any(abs(v) > 80 for v in d.get('translation', [0,0,0])): issues['traslación fuera de límite'].append(key)
            texs = {k: v for k, v in m.get('textures', {}).items() if k != 'particle'}
            for k, v in texs.items():
                while isinstance(v, str) and v.startswith('#'): v = m['textures'].get(v[1:], '')
                st = tex_ok(v) if v else 'missing'
                if st in ('missing', 'transparent'): issues['textura ' + st].append(key + ' ' + k + '=' + str(v))
            if not gen:
                used = {f.get('texture','').lstrip('#') for e in m['elements'] for f in e.get('faces',{}).values()}
                miss = [u for u in used if u and u not in m.get('textures', {})]
                if miss: issues['cara con textura sin definir'].append(key + ' ' + ','.join(miss))
                if not m['elements']: issues['sin elementos'].append(key)
print(count, 'objetos de mano')
for k, v in issues.items():
    print(f'\n## {k}: {len(v)}')
    for x in v[:40]: print('  ', x)
