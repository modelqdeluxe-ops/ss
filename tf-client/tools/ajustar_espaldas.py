"""(1.3.166) Lo de la espalda (alas, mochilas) de los sets: medirlo con reglas que se ven y dejarlo como se aprobó.

Lo que pidió el dueño, con capturas del probador de la web (de lado, con su skin): «las alas deben colocarse bien en la
espalda, altura correcta», «hay varias con las alas malas en la espalda» (Draco Imperial, Dríada, mochila de Violeta,
Polilla Rúnica) y, al ver una propuesta que bajaba el Dragonstone: «esta estaba bien arriba… tampoco es que las que
están bien las vas a poner en las patas». Así que solo se toca lo que tiene un defecto que se ve:

1. **Cabeza**: nada de lo que se ve dentro de la cabeza (|x| < 0,27, de 0,04 a 0,52 sobre el cuello, z < 0,27). De lado
   se ve como una raya o unas hojas que cruzan la cara (la 1.3.165 lo dejaba pasar: no miraba la cabeza).
2. **Cuerpo**: nada saliendo por el pecho ni dentro de los brazos (`bad`), como mucho PEN_MAX.
3. **Hueco**: lo más cercano de lo que va detrás del torso (|x| < 0,25, sin las correas, que van por dentro del cuerpo
   hasta el pecho) pegado a la espalda (HUECO_MAX). La 1.3.165 contaba las correas como «pegado» y la mochila de Violeta
   flotaba detrás.
4. **Altura**: no se baja lo que ya está bien (un ala que tapa la cabeza vista de lado es normal: también pasa en los
   sets de antes). Solo se baja lo justo para que la cabeza quede libre, o se echan hacia atrás las piezas que se meten.

Lo aprobado se guarda en `tools/espaldas.json` (por objeto: su `display.head` y, si hace falta, piezas movidas) y
`build_mod_items.py` lo aplica al final, así regenerar no lo pierde. Medido y mirado desde 8 ángulos en el probador de
la web con la skin del dueño (HANDOFF, CHECKPOINT de la 1.3.166).

    python3 tools/ajustar_espaldas.py [set ...]             aplica la tabla (lo que hace build_mod_items)
    python3 tools/ajustar_espaldas.py --medir [set ...]     solo mide (cabeza, cuerpo, hueco, alto)
    python3 tools/ajustar_espaldas.py --proponer [set ...]  propone (no escribe) un arreglo para lo que tenga defectos
Después hay que volver a exportar el probador (tierras-fantasticas/tools/wear_export.py).
"""
import json
import os
import sys

import build_mod_items as M
import check_backs as CB

TABLE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'espaldas.json')
BACK = 0.125          # la espalda (bloques desde el centro del cuerpo)
PEN_MAX = 1.0         # % de lo que se ve que se tolera dentro del cuerpo o de los brazos
HEAD_MAX = 0.5        # % dentro de la cabeza
HUECO_MAX = 0.05      # separación de la espalda (bloques)
HEAD_Z = 0.275        # detrás de la cabeza


def bad(p):
    """Se ve atravesando el cuerpo: en los brazos o por delante del pecho."""
    if not (-0.80 < p[1] < 0.05):
        return False
    if abs(p[0]) < 0.25:
        return p[2] < -BACK - 0.01   # correas apoyadas en el pecho (a -0,12) no cuentan
    return abs(p[0]) < 0.55 and p[2] < BACK


def in_head(p):
    return abs(p[0]) < 0.27 and 0.04 < p[1] < 0.52 and p[2] < 0.27


def measure(pts, wts):
    """% en el cuerpo, % en la cabeza, hueco con la espalda, y lo más alto y lo más bajo (cuantiles 98 y 2)."""
    W = sum(wts) or 1.0
    pen = 100 * sum(w for p, w in zip(pts, wts) if bad(p)) / W
    head = 100 * sum(w for p, w in zip(pts, wts) if in_head(p)) / W
    tor = [(p[2], w) for p, w in zip(pts, wts) if abs(p[0]) < 0.25 and -0.75 < p[1] < -0.05 and p[2] > 0.10]
    gap = M._wquantile([z for z, _ in tor], [w for _, w in tor], 0.10) - BACK if len(tor) > 8 else None
    ys = [p[1] for p in pts]
    return {'cuerpo': pen, 'cabeza': head, 'hueco': gap, 'arriba': M._wquantile(ys, wts, 0.98),
            'abajo': M._wquantile(ys, wts, 0.02)}


def fmt(m):
    gap = '   -  ' if m['hueco'] is None else f"{m['hueco']:+.3f}"
    return (f"cabeza {m['cabeza']:4.1f}%  cuerpo {m['cuerpo']:4.1f}%  hueco {gap}  "
            f"alto {m['arriba']:+.2f}..{m['abajo']:+.2f}")


def defects(m, acepta=None):
    """Lo que se ve mal. `acepta` (de espalda.json) sube el límite de un objeto que el dueño dio por bueno así."""
    a = acepta or {}
    out = []
    if m['cabeza'] > a.get('cabeza', HEAD_MAX):
        out.append('se mete en la cabeza')
    if m['cuerpo'] > a.get('cuerpo', PEN_MAX):
        out.append('atraviesa el cuerpo')
    if m['hueco'] is not None and m['hueco'] > a.get('hueco', HUECO_MAX):
        out.append('separado de la espalda')
    return out


def move_element(el, d):
    for k in ('from', 'to'):
        el[k] = [round(v + d[i], 4) for i, v in enumerate(el[k])]
    if el.get('rotation'):
        el['rotation']['origin'] = [round(v + d[i], 4) for i, v in enumerate(el['rotation']['origin'])]


MARK = 'tf_espalda'   # en el modelo: ya tiene la posición de espaldas.json (Minecraft no lee esta clave)


def apply_entry(model, entry):
    """Pone la posición aprobada y mueve las piezas que diga. Deja la marca MARK para no moverlas dos veces si se vuelve
    a pasar (build_mod_items parte siempre del modelo del pack, sin marca). Para cambiar las piezas de una entrada ya
    aplicada, regenera el set con build_mod_items."""
    if 'head' not in entry or model.get(MARK):
        return False
    model.setdefault('display', {})['head'] = json.loads(json.dumps(entry['head']))
    for mv in entry.get('mover', []):
        for i in mv['elements']:
            move_element(model['elements'][i], mv['d'])
    model[MARK] = 1
    return True


def items(wanted):
    sets = json.load(open(os.path.join(M.ASSETS, 'tf_sets.json'), encoding='utf-8'))['sets']
    for s in sets:
        if wanted and s['id'] not in wanted:
            continue
        for item in s['items']:
            if item['type'] != 'back':
                continue
            worn = item.get('worn')
            path = os.path.join(M.ASSETS, 'models', worn.split(':', 1)[1] + '.json') if worn else \
                os.path.join(M.ASSETS, 'models', 'item', item['id'] + '.json')
            yield s['id'] + '/' + item['id'][len(s['id']) + 1:], item, path


def propose(model, alpha_of):
    """Lo mínimo para quitar los defectos: bajar (hasta 0,4) para librar la cabeza y acercar o alejar en z. Solo una
    propuesta: hay que mirarla en el probador (8 ángulos, la skin del dueño) antes de pasarla a espaldas.json."""
    pts, wts = M.back_points(model, alpha_of)
    for k in range(0, 41):
        dy = -0.01 * k
        moved = [(p[0], p[1] + dy, p[2]) for p in pts]
        m = measure(moved, wts)
        dz = -(m['hueco'] - 0.01) if m['hueco'] is not None else 0.0
        for _ in range(80):
            m = measure([(p[0], p[1], p[2] + dz) for p in moved], wts)
            if m['cuerpo'] <= PEN_MAX and m['cabeza'] <= HEAD_MAX:
                break
            dz += 0.005
        if m['cabeza'] <= HEAD_MAX and m['cuerpo'] <= PEN_MAX:
            return dy, dz, m
    return None


def main(args):
    only_measure = '--medir' in args
    proposing = '--proponer' in args
    wanted = [a for a in args if not a.startswith('--')]
    table = json.load(open(TABLE, encoding='utf-8'))
    alpha_of = CB.alpha_of_factory()
    for tag, item, path in items(wanted):
        model = json.load(open(path, encoding='utf-8'))
        before = measure(*M.back_points(model, alpha_of))
        changed = False
        if not only_measure and not proposing and tag in table:
            changed = apply_entry(model, table[tag])
        after = measure(*M.back_points(model, alpha_of))
        bad_now = defects(after, table.get(tag, {}).get('acepta'))
        note = ('aplicado ' if changed else '') + ('MAL: ' + ', '.join(bad_now) if bad_now else 'bien')
        print(f"{tag:34s} {fmt(after)}  {note}" + (f"   (antes: {fmt(before)})" if changed else ''))
        if proposing and bad_now:
            p = propose(model, alpha_of)
            if p:
                dy, dz, m = p
                print(f"{'':34s} propuesta: bajar {dy:+.2f}, z {dz:+.3f} → {fmt(m)} (mírala en el probador)")
        if changed:
            with open(path, 'w', encoding='utf-8') as fh:
                json.dump(model, fh, separators=(',', ':'))


if __name__ == '__main__':
    main(sys.argv[1:])
