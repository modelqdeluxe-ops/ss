"""Pruebas del conversor de modelos (tools/vfx_bb.py, modo jefe) con .bbmodel hechos aquí mismo, sin los packs:
etiquetas de hueso de ModelEngine 4, bezier, Molang, fotogramas con valor antes/después, escalón, IK de Blockbench,
miembros de jugador, cajas b_ y Box UV. Y la IK (el mismo CCD que VfxIk.java) llegando a su objetivo.

    python3 tools/bosses/test_convert.py        (sale con 1 si algo falla)
"""
import base64
import io
import json
import math
import os
import sys
import tempfile

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..'))
sys.path.insert(0, HERE)
import molang  # noqa: E402
import vfx_bb as B  # noqa: E402
from sim_physics import compose, decompose, rot_to  # noqa: E402

FAILS = []


def check(name, ok, detail=''):
    print(f'  {"OK " if ok else "NO "} {name}' + (f'  ({detail})' if detail and not ok else ''))
    if not ok:
        FAILS.append(name)


def texture():
    img = Image.new('RGBA', (16, 16), (200, 50, 50, 255))
    b = io.BytesIO()
    img.save(b, 'PNG')
    return 'data:image/png;base64,' + base64.b64encode(b.getvalue()).decode()


def cube(u, f, t, faces=True):
    c = {'uuid': u, 'name': 'c', 'type': 'cube', 'from': f, 'to': t, 'origin': [0, 0, 0]}
    c['faces'] = {d: ({'uv': [0, 0, 4, 4], 'texture': 0} if faces else {'texture': 0})
                  for d in ('north', 'south', 'east', 'west', 'up', 'down')}
    return c


def kf(ch, t, x, interp='linear', **kw):
    return dict({'channel': ch, 'time': t, 'interpolation': interp, 'data_points': [{'x': x, 'y': 0, 'z': 0}]}, **kw)


def convert(doc, tmp):
    path = os.path.join(tmp, 'm.bbmodel')
    json.dump(doc, open(path, 'w'))
    probs = []
    m = B.convert(path, B.TextureStore(os.path.join(tmp, 'tex'), 'x/'), probs, boss=True)
    return m, probs


def bone(m, bid):
    return next(b for b in m['bones'] if b['id'] == bid)


def track(m, an, bid, key):
    i = next(i for i, b in enumerate(m['bones']) if b['id'] == bid)
    return next(t for t in m['anims'][an]['tracks'] if t['b'] == i)[key]


def main():
    print('molang')
    u = set()
    check('math.sin en grados', abs(molang.evaluate('math.sin(q.anim_time * 360) * 5', 0.25, u) - 5) < 1e-9)
    check('ternario y comparaciones', molang.evaluate('query.anim_time > 1 ? 7 : 3', 2) == 7)
    check('variables', molang.evaluate('v.a = 2; v.a * 4', 0) == 8)
    molang.evaluate('q.ground_speed', 0, u)
    check('avisa de lo que no conoce', 'q.ground_speed' in u)

    tmp = tempfile.mkdtemp()
    src = texture()
    els = [cube('e1', [-2, 0, -2], [2, 4, 2]), cube('e2', [-1, 4, -1], [1, 6, 1]), cube('e3', [-1, 0, -1], [1, 2, 1]),
           cube('e4', [-4, 0, -4], [4, 8, 4]), cube('e5', [-1, 0, -1], [1, 1, 1], faces=False)]
    els[4]['box_uv'] = True
    els[4]['uv_offset'] = [0, 0]
    outl = [{'uuid': 'b1', 'name': 'body', 'origin': [0, 0, 0], 'children': ['e1', 'e5',
             {'uuid': 'b2', 'name': 'h[yaw=60;pitch=30;speed=10]_head', 'origin': [0, 4, 0], 'children': ['e2']},
             {'uuid': 'b3', 'name': 'tl_tail', 'origin': [0, 2, 8], 'children': [
                 {'uuid': 'b4', 'name': 'tail_vis', 'origin': [0, 2, 8], 'children': ['e3']}]},
             {'uuid': 'b5', 'name': 'b_box', 'origin': [0, 0, 0], 'children': ['e4']},
             {'uuid': 'b6', 'name': 'l_rope', 'origin': [0, 0, 0], 'children': []},
             {'uuid': 'b7', 'name': 'jig[stiff=0.3;angle=30,10,5]_ear', 'origin': [0, 4, 0], 'children': []},
             {'uuid': 'b8', 'name': 'prarm_arm', 'origin': [4, 12, 0], 'children': []},
             {'uuid': 'b9', 'name': 'limb[type=left_forearm]_fa', 'origin': [-4, 6, 0], 'children': []},
             {'uuid': 'b10', 'name': 'zz[a=1]_bad', 'origin': [0, 0, 0], 'children': []}]}]
    anims = [{'name': 'test', 'length': 1, 'loop': 'loop', 'animators': {
        'b1': {'type': 'bone', 'keyframes': [kf('rotation', 0, 0, 'bezier'), kf('rotation', 1, 10, 'bezier')]},
        'b2': {'type': 'bone', 'keyframes': [kf('rotation', 0, 'math.sin(q.anim_time*360)*20')]},
        'b4': {'type': 'bone', 'keyframes': [kf('position', 0, 0, 'step'),
                                             {'channel': 'position', 'time': 0.5, 'interpolation': 'linear',
                                              'data_points': [{'x': 1, 'y': 0, 'z': 0}, {'x': 5, 'y': 0, 'z': 0}]},
                                             kf('position', 1, 5)]}}}]
    m, probs = convert({'resolution': {'width': 16, 'height': 16}, 'textures': [{'name': 't', 'source': src}],
                        'elements': els, 'outliner': outl, 'animations': anims}, tmp)
    print('etiquetas de hueso')
    h = bone(m, 'head')
    check('h[...]_: cabeza con límites', h['head'] and h['bp'] == {'yaw': 60.0, 'pitch': 30.0, 'speed': 10.0})
    check('tl_: segmento (conserva el nombre)', bone(m, 'tl_tail').get('beh') == 'tl')
    bx = bone(m, 'box')
    check('b_: caja recta sin dibujarse', bx.get('aabb') and bx['hb'] == [-4.0, 0.0, -4.0, 4.0, 8.0, 4.0] and not bx['quads'])
    check('l_ sin cubos: correa', bone(m, 'rope').get('beh') == 'leash')
    ear = bone(m, 'ear')
    check('jig[...]_: física con sus valores', ear.get('beh') == 'jig' and ear['bp']['angle'] == [30.0, 10.0, 5.0])
    arm = bone(m, 'arm')
    check('prarm_: brazo con la skin (12 caras)', arm.get('beh') == 'limb' and len(arm['quads']) == 12 and arm['quads'][0][0] == -4)
    check('limb[type=left_forearm]', bone(m, 'fa')['bp'] == {'type': 'left_forearm', 'split': 1})
    check('etiqueta desconocida: problema', any('zz[...]' in p for p in probs))
    check('Box UV sin UV por cara: caras sacadas de uv_offset', len(bone(m, 'body')['quads']) == 12)
    print('animaciones')
    r = track(m, 'test', 'body', 'r')
    check('bezier: mitad simétrica', abs(B.sample(r, 0.5)[0] + 5) < 0.05, B.sample(r, 0.5))
    check('bezier: no es lineal', abs(B.sample(r, 0.25)[0] + 2.5) > 0.1, B.sample(r, 0.25))
    hr = track(m, 'test', 'head', 'r')
    check('Molang horneado', abs(B.sample(hr, 0.25)[0] + 20) < 0.05, B.sample(hr, 0.25))
    p = track(m, 'test', 'tail_vis', 'p')
    vals = [round(B.sample(p, t)[0], 3) for t in (0.4, 0.497, 0.5, 0.502, 0.75)]
    check('escalón y valor antes/después', vals == [0.0, 0.0, -1.0, -5.0, -5.0], vals)

    print('IK de Blockbench')
    els2 = [cube('c1', [-1, 0, -1], [1, 8, 1]), cube('c2', [-1, 8, -1], [1, 16, 1]), cube('c3', [-1, 16, -1], [1, 20, 1]),
            {'uuid': 'n1', 'name': 'foot_target', 'type': 'null_object', 'position': [0, 12, 0], 'ik_target': 'k3',
             'ik_source': 'k1'}]
    outl2 = [{'uuid': 'root', 'name': 'root', 'origin': [0, 0, 0], 'children': [
        {'uuid': 'k1', 'name': 'thigh', 'origin': [0, 0, 0], 'children': ['c1', {'uuid': 'k2', 'name': 'shin', 'origin': [0, 8, 0],
                                                                               'children': ['c2', {'uuid': 'k3', 'name': 'foot', 'origin': [0, 16, 0], 'children': ['c3']}]}]},
        'n1']}]
    anims2 = [{'name': 'reach', 'length': 1, 'loop': 'loop', 'animators': {'n1': {'type': 'null_object', 'keyframes': [
        kf('position', 0, 0), {'channel': 'position', 'time': 1, 'interpolation': 'linear', 'data_points': [{'x': 6, 'y': -2, 'z': 0}]}]}}}]
    m2, probs2 = convert({'resolution': {'width': 16, 'height': 16}, 'textures': [{'name': 't', 'source': src}],
                          'elements': els2, 'outliner': outl2, 'animations': anims2}, tmp)
    check('cadena y objetivo', m2.get('ik') == [{'null': 4, 'chain': [1, 2, 3], 'lock': False, 'pole': -1}] and not probs2,
          (m2.get('ik'), probs2))
    worst = 0.0
    for t in (0, 0.25, 0.5, 0.75, 0.99):
        mats = [np.array(x) for x in B.pose(m2, 'reach', t)]
        s, target = ccd(m2, mats, m2['ik'][0])
        worst = max(worst, float(np.linalg.norm(s[3][:3, 3] - target)))
    check('el pie llega al objetivo (CCD, como VfxIk)', worst < 0.25, f'{worst:.3f} px')

    print('cabezas de ModelEngine 4 (VfxPose.build)')
    heads(tmp, src)

    print('jig[repeat], sombra y brazos slim')
    els3 = [cube('r1', [-0.5, -6, -0.5], [0.5, 0, 0.5]), cube('w1', [-1, -8, -1], [1, -6, 1]), cube('s1', [-16, 0, -12], [16, 2, 12])]
    outl3 = [{'uuid': 'q0', 'name': 'body', 'origin': [0, 0, 0], 'children': [
        {'uuid': 'q1', 'name': 'jig[repeat=3;stiff=0.2]_rope', 'origin': [0, 0, 0], 'children': ['r1',
            {'uuid': 'q2', 'name': 'weight', 'origin': [0, -6, 0], 'children': ['w1']}]},
        {'uuid': 'q3', 'name': 'prarm_arm', 'origin': [4, 12, 0], 'children': []}]},
        {'uuid': 'q4', 'name': 'shadow', 'origin': [0, 0, 0], 'children': ['s1']}]
    m3, probs3 = convert({'resolution': {'width': 16, 'height': 16}, 'textures': [{'name': 't', 'source': src}],
                          'elements': els3, 'outliner': outl3, 'animations': []}, tmp)
    ids = [b['id'] for b in m3['bones']]
    chain = [bone(m3, x) for x in ('rope', 'rope_2', 'rope_3')] if 'rope_3' in ids else []
    check('jig[repeat=3]_: tres eslabones en cadena', len(chain) == 3 and chain[1]['parent'] == ids.index('rope')
          and chain[2]['parent'] == ids.index('rope_2') and chain[2]['pivot'] == [0.0, -12.0, 0.0]
          and all(c.get('beh') == 'jig' and c['bp'] == {'stiff': 0.2} for c in chain), (ids, probs3))
    w = bone(m3, 'weight')
    check('... y lo que colgaba, tras el último', w['parent'] == ids.index('rope_3') and w['pivot'] == [0.0, -18.0, 0.0], w)
    check('hueso shadow: diámetro de la sombra', m3.get('shadow') == 2.0, m3.get('shadow'))
    sq = bone(m3, 'arm').get('sq') or []
    xs = [q[4 + k * 5] for q in sq if q[0] == -4 for k in range(4)]
    check('brazo slim: 3 px de ancho', len(sq) == 12 and xs and abs(max(xs) - min(xs) - 3) < 1e-6, (len(sq), xs[:4]))

    mythic()

    print()
    if FAILS:
        print('FALLAN:', ', '.join(FAILS))
        return 1
    print('todo bien')
    return 0


def mythic():
    """Lo de MythicMobs de §11.3 en el compilador y en los simuladores (los mismos cálculos que el Java)."""
    sys.path.insert(0, os.path.join(HERE, '..', 'skills'))
    import compile as C
    import sim as SIM
    import sim_ai as AI

    pm = C.parse_line('switch{condition=entitytype{t=<case>};cases= case SKELETON=[ - message{m="a"} @server - delay 5 ] '
                      'case DEFAULT=[ - message{m="b"} @server ] } @trigger ~onDamaged')
    tree = {}
    C.extract_inline([pm], tree, 'mob_x')
    sk = tree.get('mob_x__0_case_SKELETON', {})
    check('switch: un caso por skill, con <case> en su condición',
          pm['a'].get('cases') == 'SKELETON=mob_x__0_case_SKELETON,DEFAULT=mob_x__0_case_DEFAULT'
          and sk.get('target_conditions') == [{'m': 'entitytype', 'a': {'t': 'SKELETON'}, 'v': 'true'}]
          and len(sk.get('mechs', [])) == 2 and tree['mob_x__0_case_DEFAULT']['target_conditions'] == [], (pm, tree))
    check('switch: sus casos cuentan como skills llamadas', C.refs(pm)[0] == ['mob_x__0_case_SKELETON', 'mob_x__0_case_DEFAULT'])
    check('alias de disparadores (onBowShoot, onSkillHit, onVibration)',
          [C.parse_line(f'skill{{s=a}} ~{t}')['tr'] for t in ('onBowShoot', 'onSkillHit', 'onVibration')]
          == ['onshoot', 'onskilldamage', 'onhear'])
    cm = C.parse_line('chainmissile{b=3;bounceConditions=[ - isplayer true ]} @target')
    check('chainmissile: bounceConditions aparte', cm.get('bc') == [{'m': 'isplayer', 'a': {}, 'v': 'true'}] and 'bounceconditions' not in cm['a'], cm)

    class Caster:
        def pos(self):
            return np.array([0.0, 0.0, 0.0])

        def yaw(self):
            return 0.0

    class Ctx:
        caster, targets, trigger = Caster(), None, None

    ctx, o = Ctx(), np.zeros(3)
    check('@Cone: angle·range·0,1 puntos si no lo dice (144)', len(SIM.shape_points('cone', {}, ctx, o)) == 144)
    cone = SIM.shape_points('cone', {'a': '60', 'r': '8', 'p': '200'}, ctx, o)
    check('@Cone: dentro del ángulo y del alcance, delante', all(np.linalg.norm(p) <= 8 + 1e-9 and p[2] >= -1e-9
          and abs(math.degrees(math.atan2(-p[0], p[2]))) <= 30 + 1e-6 for p in cone), len(cone))
    sph = SIM.shape_points('sphere', {'r': '3', 'p': '50', 'e': 'true'}, ctx, o)
    check('@Sphere{exact}: los puntos en la superficie', len(sph) == 50 and all(abs(np.linalg.norm(p) - 3) < 1e-9 for p in sph))
    counts = [len(SIM.shape_points('rectangle', k, ctx, o)) for k in ({}, {'outline': 'true'}, {'filled': 'true'})]
    check('@Rectangle: caras 488, aristas 104, llena 1000 (10 por arista)', counts == [488, 104, 1000], counts)
    bir = [len(SIM.shape_points('bir', k, ctx, o)) for k in ({}, {'s': 'CUBE'}, {'oa': 'true'})]
    check('@BlocksInRadius r=2 en el suelo de prueba: esfera 10, cubo 50, solo aire 23', bir == [10, 50, 23], bir)
    cases = [('x*2+1', 3, 7), ('(x/5)+1', 10, 3), ('2^3^2', 0, 512), ('max(1,x,3)', 7, 7), ('x>5 & x<10', 7, 1),
             ('!0', 0, 1), ('10%4', 0, 2), ('-2^2', 0, -4), ('sqrt(16)+log(100)', 0, 6), ('round(2.5)+floor(-1.5)', 0, 1)]
    bad = [(e, AI.crunch(e, x)) for e, x, want in cases if AI.crunch(e, x) != want]
    check('variablemath: las cuentas de Crunch (como SkillVars.math)', not bad and AI.crunch('nada', 0) is None, bad)


def heads(tmp, src):
    """hi_ / h_ / h[yaw]_ como ModelEngine 4 (y BetterModel): giro global que no se acumula; los hijos sin etiqueta
    de un h_ van con él pero no giran; hi_ hace cabeza a todos sus hijos; cada cabeza con sus límites."""
    els = [cube('d1', [-2, 8, -2], [2, 12, 2]), cube('d2', [-1, 12, -1], [1, 14, 1]), cube('d3', [3, 10, -1], [5, 14, 1]),
           cube('d4', [-1, 2, 7], [1, 6, 9]), cube('d5', [-1, 2, 11], [1, 4, 13])]
    outl = [{'uuid': 'r', 'name': 'root', 'origin': [0, 0, 0], 'children': [
        {'uuid': 'c', 'name': 'hi[yaw=20]_chest', 'origin': [0, 10, 0], 'children': ['d1',
            {'uuid': 'n', 'name': 'neck', 'origin': [0, 12, 0], 'children': [
                {'uuid': 'h', 'name': 'hi_head', 'origin': [0, 12, 0], 'children': ['d2']},
                {'uuid': 's', 'name': 'h_snout', 'origin': [0, 12, 1], 'children': [
                    {'uuid': 'o', 'name': 'nose', 'origin': [0, 12, 2], 'children': []}]}]},
            {'uuid': 'a', 'name': 'arm', 'origin': [4, 14, 0], 'children': ['d3']}]},
        {'uuid': 't', 'name': 'h_turret', 'origin': [0, 4, 8], 'children': ['d4',
            {'uuid': 'j', 'name': 'jaw', 'origin': [0, 4, 12], 'children': ['d5']}]}]}]
    m, probs = convert({'resolution': {'width': 16, 'height': 16}, 'textures': [{'name': 't', 'source': src}],
                        'elements': els, 'outliner': outl, 'animations': []}, tmp)
    ids = [b['id'] for b in m['bones']]
    check('hi_: todo su subárbol es cabeza (también bajo un h_), los hijos de h_ no',
          all(bone(m, x)['head'] for x in ('chest', 'neck', 'head', 'arm', 'snout', 'nose'))
          and bone(m, 'turret')['head'] and not bone(m, 'jaw')['head'], [(b['id'], b['head']) for b in m['bones']])
    check('hi[...]_: sus hijos sin etiqueta heredan los límites', bone(m, 'arm').get('bp') == {'yaw': 20.0}
          and not bone(m, 'nose').get('bp'), (bone(m, 'arm').get('bp'), bone(m, 'nose').get('bp')))
    mats = [np.array(x) for x in B.pose(m, None, 0, head=(40, 0))]

    def yaw_of(i):
        z = mats[i][:3, :3] @ np.array([0, 0, 1.0])
        return math.degrees(math.atan2(z[0], z[2]))
    yh, yc, ya, yt, yj = (yaw_of(ids.index(x)) for x in ('head', 'chest', 'arm', 'turret', 'jaw'))
    check('cabeza dentro de cabeza: mira 40°, no 80° ni repartido', abs(yh + 40) < 1e-6, f'{yh:.2f}')
    check('hi[yaw=20]_: el pecho solo 20°', abs(yc + 20) < 1e-6, f'{yc:.2f}')
    check('hijo de hi_ (el brazo): con su pecho', abs(ya + 20) < 1e-6, f'{ya:.2f}')
    check('h_ suelto: 40°', abs(yt + 40) < 1e-6, f'{yt:.2f}')
    yn = yaw_of(ids.index('nose'))
    check('bajo un h_ dentro de hi_: 40° (sin sumar)', abs(yn + 40) < 1e-6, f'{yn:.2f}')
    check('hijo sin etiqueta de h_ (la mandíbula): no gira', abs(yj) < 1e-6, f'{yj:.2f}')
    jaw = mats[ids.index('jaw')][:3, 3]
    want = np.array([4 * math.sin(math.radians(-40)), 4, 8 + 4 * math.cos(math.radians(-40))])
    check('... pero va donde la lleva su cabeza', np.linalg.norm(jaw - want) < 1e-6, (jaw, want))
    still = [np.array(x) for x in B.pose(m, None, 0)]
    check('sin mirada, igual que antes', all(np.allclose(a, b) for a, b in zip(still, [np.array(x) for x in B.pose(m, None, 0, head=(0, 0))])))


def ccd(m, mats, c):
    """El mismo CCD que VfxIk.solve (sin límites ni polo)."""
    n = len(m['bones'])
    par = [b['parent'] for b in m['bones']]
    chain, nb = c['chain'], c['null']
    tip = chain[-1]
    s = [x.copy() for x in mats]
    target = mats[nb][:3, 3]

    def turn(j, q):
        old = s[j].copy()
        t, r, sc = decompose(s[j])
        s[j] = compose(t, q @ r, sc)
        d = s[j] @ np.linalg.inv(old)
        moved = [False] * n
        moved[j] = True
        for i in range(j + 1, n):
            if par[i] >= 0 and moved[par[i]]:
                moved[i] = True
                s[i] = d @ s[i]

    for _ in range(16):
        for k in range(len(chain) - 2, -1, -1):
            j = chain[k]
            p = s[j][:3, 3]
            a, b = s[tip][:3, 3] - p, target - p
            la, lb = np.linalg.norm(a), np.linalg.norm(b)
            if la < 1e-6 or lb < 1e-6:
                continue
            if np.linalg.norm(np.cross(a / la, b / lb)) < 1e-4 and np.dot(a, b) > 0 and lb < la - 1e-3:
                ax = decompose(s[j])[1][:, 0]
                ang = 0.15
                ax = ax / np.linalg.norm(ax)
                x, y, z = ax
                cs, sn = math.cos(ang), math.sin(ang)
                q = np.array([[cs + x * x * (1 - cs), x * y * (1 - cs) - z * sn, x * z * (1 - cs) + y * sn],
                              [y * x * (1 - cs) + z * sn, cs + y * y * (1 - cs), y * z * (1 - cs) - x * sn],
                              [z * x * (1 - cs) - y * sn, z * y * (1 - cs) + x * sn, cs + z * z * (1 - cs)]])
                turn(j, q)
                continue
            turn(j, rot_to(a, b))
        if np.linalg.norm(s[tip][:3, 3] - target) < 0.05:
            break
    return s, target


if __name__ == '__main__':
    sys.exit(main())
