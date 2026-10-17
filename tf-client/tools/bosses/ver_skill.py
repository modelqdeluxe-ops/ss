"""Ver una skill o un mob de un jefe ya convertido (o quién la llama), sin abrir el JSON a mano.

    python3 tools/bosses/ver_skill.py <jefe> <skill>              la skill: enfriamiento, condiciones y sus líneas
    python3 tools/bosses/ver_skill.py <jefe> mob:<nombre>         el mob: opciones y sus líneas con disparador
    python3 tools/bosses/ver_skill.py <jefe> <nombre> --callers   quién la llama (skills y mobs)

<jefe> es el id de assets/tfclient/bosses/<jefe>.json (astwood, druida_boreal...). Los nombres, tal cual en el pack.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BOSSES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'bosses'))


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    boss, name = sys.argv[1], sys.argv[2]
    d = json.load(open(os.path.join(BOSSES, boss + '.json'), encoding='utf-8'))
    if '--callers' in sys.argv:
        def walk(where, o):
            if isinstance(o, dict):
                for v in o.values():
                    if isinstance(v, str) and (v == name or name in v.split(',')):
                        print(where, json.dumps(o, ensure_ascii=False)[:300])
                        break
                for v in o.values():
                    walk(where, v)
            elif isinstance(o, list):
                for v in o:
                    walk(where, v)
        for k, v in d['tree'].items():
            walk('skill:' + k, v)
        for k, v in d['mobs'].items():
            walk('mob:' + k, v)
        return
    if name.startswith('mob:'):
        m = d['mobs'][name[4:]]
        print({k: v for k, v in m.items() if k != 'mechs'})
        for x in m['mechs']:
            print('  ', json.dumps(x, ensure_ascii=False)[:300])
        return
    t = d['tree'].get(name)
    if t is None:
        print('no está; parecidas:', [k for k in d['tree'] if name.lower() in k.lower()][:20])
        sys.exit(1)
    print('cd', t['cooldown'], 'cond', t['conditions'][:6], 'tcond', t.get('target_conditions'))
    for x in t['mechs']:
        print('  ', json.dumps(x, ensure_ascii=False)[:300])


if __name__ == '__main__':
    main()
