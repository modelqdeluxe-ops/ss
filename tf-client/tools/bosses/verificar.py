"""La red de seguridad de los jefes: que nada de lo que se toque (el motor, el conversor, un pack nuevo) cambie a los
jefes que ya están publicados sin que se sepa. Lo lanza GitHub Actions en cada compilación (tf-client.yml) y debe
pasarse antes de publicar:

    python3 tools/bosses/verificar.py                       # todo; sale con 1 si algo falla
    python3 tools/bosses/verificar.py --actualizar [jefe]   # rehace las referencias de esos jefes (o de todos)

Mira, en este orden:
  1. el auditor (`audit.py`): 0 problemas en todos los jefes del catálogo;
  2. su autoprueba (`audit.py --prueba`): ve todos los errores metidos a propósito;
  3. las pruebas del conversor y de los simuladores (`test_convert.py`): «todo bien»;
  4. por jefe, contra sus referencias guardadas en `tools/bosses/referencias/`:
     - `<jefe>.sim.txt`: el simulador de la pelea (`sim_ai.py`) con varias semillas y dificultades;
     - `<jefe>.ficha.txt`: lo que el simulador no ve: la caja de golpe y los ojos de cada mob (como
       BossEntity.getDimensions), su tipo, velocidad, vida, daño y modificadores; y cada modelo (huesos de cada tipo,
       cabezas con sus límites, cadenas, animaciones con su duración, una huella del archivo) y lo más que se doblan
       sus colas al girar (`sim_physics.py`).

Si algo cambia en un jefe que ya existía, sale el cambio y falla. Si el cambio es a propósito (un arreglo, un pack
reconvertido), se acepta con `--actualizar <jefe>` y se explica en el commit y en HANDOFF.md. Un jefe nuevo necesita
sus referencias en el mismo cambio (`--actualizar <jefe>`).
"""
import difflib
import hashlib
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'tfclient')
BOSSES = os.path.join(RES, 'bosses')
REFS = os.path.join(HERE, 'referencias')
PY = sys.executable

# Las pasadas del simulador por jefe: (semilla, dificultad de /tf boss o None = el pack tal cual)
RUNS = [(3, None), (3, 'normal'), (3, 'demencial'), (11, 'normal'), (11, 'dificil')]

# Tamaño (ancho, alto) de los tipos de Minecraft (EntityType.getDimensions), para model{hitbox=false}
VANILLA = {
    'IRON_GOLEM': (1.4, 2.7), 'ZOMBIE': (0.6, 1.95), 'HUSK': (0.6, 1.95), 'DROWNED': (0.6, 1.95),
    'ZOMBIE_VILLAGER': (0.6, 1.95), 'ZOMBIFIED_PIGLIN': (0.6, 1.95), 'SKELETON': (0.6, 1.99), 'STRAY': (0.6, 1.99),
    'WITHER_SKELETON': (0.7, 2.4), 'VINDICATOR': (0.6, 1.95), 'PILLAGER': (0.6, 1.95), 'EVOKER': (0.6, 1.95),
    'ILLUSIONER': (0.6, 1.95), 'WITCH': (0.6, 1.95), 'VILLAGER': (0.6, 1.95), 'WANDERING_TRADER': (0.6, 1.95),
    'PIGLIN': (0.6, 1.95), 'PIGLIN_BRUTE': (0.6, 1.95), 'BLAZE': (0.6, 1.8), 'ENDERMAN': (0.6, 2.9),
    'RAVAGER': (1.95, 2.2), 'SPIDER': (1.4, 0.9), 'CAVE_SPIDER': (0.7, 0.5), 'WOLF': (0.6, 0.85),
    'HORSE': (1.3964844, 1.6), 'SKELETON_HORSE': (1.3964844, 1.6), 'ZOMBIE_HORSE': (1.3964844, 1.6),
    'GHAST': (4.0, 4.0), 'PHANTOM': (0.9, 0.5), 'WARDEN': (0.9, 2.9), 'CREEPER': (0.6, 1.7),
    'ARMOR_STAND': (0.5, 1.975), 'GIANT': (3.6, 12.0), 'HOGLIN': (1.3964844, 1.4), 'ZOGLIN': (1.3964844, 1.4),
    'POLAR_BEAR': (1.4, 1.4), 'BAT': (0.5, 0.9), 'VEX': (0.4, 0.8), 'ALLAY': (0.35, 0.6), 'SNOW_GOLEM': (0.7, 1.9),
    'STRIDER': (0.9, 1.7), 'SHULKER': (1.0, 1.0), 'GUARDIAN': (0.85, 0.85), 'ELDER_GUARDIAN': (1.9975, 1.9975),
    'WITHER': (0.9, 3.5), 'SLIME': (0.52, 0.52), 'MAGMA_CUBE': (0.52, 0.52), 'SILVERFISH': (0.4, 0.3),
    'ENDERMITE': (0.4, 0.3), 'RABBIT': (0.4, 0.5), 'PIG': (0.9, 0.9), 'COW': (0.9, 1.4), 'SHEEP': (0.9, 1.3),
    'CHICKEN': (0.4, 0.7), 'GOAT': (0.9, 1.3), 'FOX': (0.6, 0.7), 'CAT': (0.6, 0.7), 'OCELOT': (0.6, 0.7),
    'PANDA': (1.3, 1.25), 'LLAMA': (0.9, 1.87), 'CAMEL': (1.7, 2.375), 'SNIFFER': (1.9, 1.75), 'FROG': (0.5, 0.5),
    'AXOLOTL': (0.75, 0.42), 'DOLPHIN': (0.9, 0.6), 'SQUID': (0.8, 0.8), 'GLOW_SQUID': (0.8, 0.8),
    'TURTLE': (1.2, 0.4), 'PARROT': (0.5, 0.9), 'BEE': (0.7, 0.6), 'ENDER_DRAGON': (16.0, 8.0),
}
# Las de nuestras entidades si no hay otra (TFBosses: jefe y esbirro)
DEFAULT_DIMS = {'boss': (1.4, 2.7), 'minion': (0.8, 1.9)}


def catalog():
    return [c['id'] for c in json.load(open(os.path.join(BOSSES, 'catalog.json'), encoding='utf-8'))]


def num(v, d):
    try:
        return float(str(v).strip().strip('"'))
    except (TypeError, ValueError):
        return d


def model_line(mob):
    """La línea model{} con la que sale el mob (la de su ~onSpawn/~onCombat sobre sí mismo, como build_bosses)."""
    for pm in mob.get('mechs', []):
        if pm['m'] == 'model' and pm.get('tr') in (None, 'onspawn', 'oncombat', 'onspawnorload', 'onready') \
                and pm.get('t') in (None, 'self', 'caster'):
            return pm.get('a', {})
    return {}


def dims(prog, name, mob):
    """Su caja (ancho, alto) y altura de los ojos, como BossEntity.getDimensions (y la de Minecraft: 0,85 del alto)."""
    a = model_line(mob)
    use_model = str(a.get('hitbox', a.get('h', 'true'))).lower() != 'false'
    hbs = a.get('hitboxscale', a.get('hbs'))
    scale = num(hbs if hbs is not None else a.get('scale', a.get('size', '1')), 1.0)
    w = h = None
    mid = mob.get('model')
    if use_model and mid:
        p = os.path.join(BOSSES, 'models', mid + '.json')
        if os.path.exists(p):
            hb = json.load(open(p, encoding='utf-8')).get('hitbox')
            if hb and hb[0] > 0 and hb[1] > 0:
                w, h = hb[0], hb[1]
    if w is None and not use_model:
        w, h = VANILLA.get(str(mob.get('type', '')).upper(), (None, None))
    if w is None:
        main = str(prog['boss'].get('mob', '')).lower() == name.lower()
        w, h = DEFAULT_DIMS['boss' if main else 'minion']
    return round(w * scale, 4), round(h * scale, 4), round(h * scale * 0.85, 4), scale, use_model


def chain_kind(b):
    beh = b.get('beh')
    if beh:
        return beh
    low = b['id'].lower()
    for t in ('segf_', 'seg_', 'tlf_', 'tl_', 'jig'):
        if low.startswith(t) and not b.get('quads'):
            return t.rstrip('_')
    return None


def model_lines(mid):
    p = os.path.join(BOSSES, 'models', mid + '.json')
    if not os.path.exists(p):
        return [f'  modelo {mid}: NO EXISTE']
    raw = open(p, 'rb').read()
    m = json.loads(raw)
    bones = m['bones']
    kinds = {}
    for b in bones:
        k = chain_kind(b) or ('cabeza' if b.get('head') else None) or ('caja' if b.get('hb') else None)
        if k:
            kinds[k] = kinds.get(k, 0) + 1
    out = [f'  modelo {mid}: {len(bones)} huesos, huella {hashlib.sha256(raw).hexdigest()[:16]}, '
           f'caja {m.get("hitbox")}, sombra {m.get("shadow")}, ' + ', '.join(f'{k} {v}' for k, v in sorted(kinds.items()))]
    heads = [b["id"] + (json.dumps(b["bp"], sort_keys=True) if b.get("bp") else "") for b in bones if b.get("head")]
    if heads:
        out.append('    cabezas: ' + ' '.join(heads))
    anims = m.get('anims', {})
    out.append('    animaciones: ' + ' '.join(f'{n}:{a.get("len", a.get("length", 0))}' for n, a in sorted(anims.items())))
    return out


def ficha(bid):
    """Lo que el simulador no ve, en texto (para comparar con la referencia)."""
    prog = json.load(open(os.path.join(BOSSES, bid + '.json'), encoding='utf-8'))
    bi = prog['boss']
    lines = [f'jefe {bid}: mob {bi.get("mob")}, vida {bi.get("health")} (pack {bi.get("pack_health")}), daño '
             f'{bi.get("damage")}, modelo {bi.get("model")}, caja {bi.get("hitbox")}, ataques {len(bi.get("attacks") or [])}']
    models = set()
    for name in sorted(prog['mobs']):
        mob = prog['mobs'][name]
        if mob.get('model'):
            models.add(mob['model'])
        if mob.get('kind') != 'entity':
            lines.append(f'mob {name}: efecto {mob.get("type")}, modelo {mob.get("model")}, {len(mob.get("mechs", []))} líneas')
            continue
        w, h, eye, sc, use = dims(prog, name, mob)
        opts = mob.get('options') or {}
        lines.append(f'mob {name}: {mob.get("type")}, caja {w}×{h} (ojos {eye}, escala {sc}, '
                     f'{"del modelo" if use else "de su tipo"}), velocidad {opts.get("MovementSpeed", mob.get("speed"))}, '
                     f'vida {mob.get("health")}, daño {mob.get("damage")}, IA {mob.get("ai_goals")}, '
                     f'modificadores {mob.get("damage_modifiers")}, {len(mob.get("mechs", []))} líneas')
    for mid in sorted(models | {m for m in (bi.get('model'),) if m}):
        lines += model_lines(mid)
    # Las colas y la física: lo más que se apartan al girar 120° en 10 ticks andando
    sys.path.insert(0, HERE)
    import sim_physics
    for mid in sorted(models):
        p = os.path.join(BOSSES, 'models', mid + '.json')
        if not os.path.exists(p) or not sim_physics.has_chains(json.load(open(p, encoding='utf-8'))):
            continue
        worst, _f, _m = sim_physics.simulate(mid, 'idle', 120.0, 0.3, quiet=True)
        lines.append(f'  colas de {mid}: lo más que se aparta un eslabón al girar {worst:.1f}°')
    return '\n'.join(lines) + '\n'


def sim(bid):
    """El simulador de la pelea con cada pasada de RUNS."""
    out = []
    for seed, dif in RUNS:
        cmd = [PY, os.path.join(HERE, 'sim_ai.py'), bid, '--seed', str(seed), '--min', '8']
        if dif:
            cmd += ['--dif', dif]
        r = subprocess.run(cmd, capture_output=True, text=True)
        out.append(f'## semilla {seed}, {dif or "el pack tal cual"}\n{r.stdout}{r.stderr}')
    return '\n'.join(out)


def run_ok(name, cmd, want_tail=None):
    r = subprocess.run(cmd, capture_output=True, text=True)
    text = r.stdout + r.stderr
    ok = r.returncode == 0 and (want_tail is None or want_tail in text.strip().splitlines()[-1:][0] if text.strip() else False)
    print(f'{"OK " if ok else "NO "} {name}')
    if not ok:
        print('\n'.join('    ' + x for x in text.strip().splitlines()[-25:]))
    return ok


def compare(path, now, label):
    if not os.path.exists(path):
        print(f'NO  {label}: sin referencia (jefe nuevo: python3 tools/bosses/verificar.py --actualizar <jefe>)')
        return False
    old = open(path, encoding='utf-8').read()
    if old == now:
        print(f'OK  {label}')
        return True
    print(f'NO  {label}: ha cambiado (si es a propósito: --actualizar y explícalo en el commit y en HANDOFF.md)')
    diff = list(difflib.unified_diff(old.splitlines(), now.splitlines(), 'referencia', 'ahora', lineterm='', n=1))
    print('\n'.join('    ' + x for x in diff[:60]) + ('\n    ...' if len(diff) > 60 else ''))
    return False


def main():
    args = sys.argv[1:]
    ids = catalog()
    if '--actualizar' in args:
        which = [a for a in args if not a.startswith('--')] or ids
        os.makedirs(REFS, exist_ok=True)
        for bid in which:
            if bid not in ids:
                print(f'{bid}: no está en el catálogo')
                return 1
            open(os.path.join(REFS, bid + '.sim.txt'), 'w', encoding='utf-8').write(sim(bid))
            open(os.path.join(REFS, bid + '.ficha.txt'), 'w', encoding='utf-8').write(ficha(bid))
            print(f'referencias de {bid} rehechas')
        return 0
    ok = True
    ok &= run_ok('auditor: 0 problemas en todos los jefes', [PY, os.path.join(HERE, 'audit.py'), '--sin-avisos'])
    ok &= run_ok('autoprueba del auditor', [PY, os.path.join(HERE, 'audit.py'), '--prueba'])
    ok &= run_ok('pruebas del conversor y de los simuladores', [PY, os.path.join(HERE, 'test_convert.py')], 'todo bien')
    for bid in ids:
        ok &= compare(os.path.join(REFS, bid + '.ficha.txt'), ficha(bid), f'{bid}: ficha (cajas, modelos, colas)')
        ok &= compare(os.path.join(REFS, bid + '.sim.txt'), sim(bid), f'{bid}: simulador de la pelea')
    stale = sorted({f.split('.')[0] for f in os.listdir(REFS)} - set(ids)) if os.path.isdir(REFS) else []
    for s in stale:
        print(f'·   referencias de {s}, que ya no está en el catálogo (bórralas si se quitó a propósito)')
    print('\ntodo bien' if ok else '\nHAY CAMBIOS O FALLOS (arriba)')
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
