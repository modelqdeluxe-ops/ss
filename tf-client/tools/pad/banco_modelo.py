"""El cofre del Banco del clan (tfclient:banco_clan), 1.3.158: el modelo 3D de una crate de madera de TF Crates (base y
tapa cerrada, de assets/fscrates/models/block), encogido para que quepa en un bloque y apoyado en el suelo. El dueño:
«los cofres siguen siendo una mierda, saca un modelo de las crates, uno de madera, de los modelos 3D de las crates del
mod, no de la web». Usa la textura de la crate tal cual (fscrates:block/…). Las rotaciones de cada pieza se quedan igual
(escalar no cambia los ángulos: siguen siendo los que admite Minecraft, ±22,5 y ±45).
Uso: python3 tools/pad/banco_modelo.py [crate] [--lado N] [--ver salida.png]
  crate: crate_lvl1 (la de fábrica), crates1_lvl1, toro_minotaur, toro_xi, cine_legendary… (con su _lid si lo tiene).
  --lado: lo que mide de ancho como mucho, en píxeles de bloque (15)."""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets'))
CRATES = os.path.join(RES, 'fscrates', 'models', 'block')
OUT = os.path.join(RES, 'tfclient', 'models', 'block', 'clan', 'banco_clan.json')
# la cerradura de cada crate mira al norte (el frente del bloque con facing=north) salvo en estas, que va al sur
FRONT_SOUTH = {'cine_legendary', 'crates1_lvl1'}


def main():
    args = sys.argv[1:]
    crate, side, ver = 'crate_lvl1', 15.0, None
    i = 0
    while i < len(args):
        if args[i] == '--lado':
            side = float(args[i + 1])
            i += 2
        elif args[i] == '--ver':
            ver = args[i + 1]
            i += 2
        else:
            crate = args[i]
            i += 1
    parts = [crate] + ([crate + '_lid'] if os.path.exists(os.path.join(CRATES, crate + '_lid.json')) else [])
    elements, textures = [], {}
    for p in parts:
        m = json.load(open(os.path.join(CRATES, p + '.json'), encoding='utf-8'))
        textures.update({k: v for k, v in m.get('textures', {}).items()})
        elements += m['elements']
    lo = [min(e['from'][k] for e in elements) for k in range(3)]
    hi = [max(e['to'][k] for e in elements) for k in range(3)]
    s = side / max(hi[0] - lo[0], hi[2] - lo[2])
    cx, cz = (lo[0] + hi[0]) / 2, (lo[2] + hi[2]) / 2

    def tr(p):
        return [round(8 + (p[0] - cx) * s, 4), round((p[1] - lo[1]) * s, 4), round(8 + (p[2] - cz) * s, 4)]

    out = []
    for e in elements:
        e = json.loads(json.dumps(e))
        e['from'], e['to'] = tr(e['from']), tr(e['to'])
        r = e.get('rotation')
        if r:
            r['origin'] = tr(r.get('origin', [8, 8, 8]))
            if not r.get('angle'):
                del e['rotation']
        e.pop('name', None)
        out.append(e)
    tex = {k: v for k, v in textures.items() if k != 'particle'}
    tex['particle'] = textures.get('particle') or next(iter(tex.values()))
    model = {
        '_comentario': 'Hecho con tools/pad/banco_modelo.py ' + crate + ': el modelo de la crate «' + crate
                       + '» de TF Crates (base y tapa), a escala ' + str(round(s, 4)) + '. No editar a mano.',
        'parent': 'minecraft:block/block',
        'render_type': 'minecraft:cutout',
        'textures': tex,
        'elements': out,
    }
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(model, f, ensure_ascii=False, indent=1)
        f.write('\n')
    height = (hi[1] - lo[1]) * s
    half = (max(hi[0] - lo[0], hi[2] - lo[2]) * s) / 2
    print(f'{OUT}: «{crate}», {len(out)} piezas, escala {s:.4f}, alto {height:.2f}, de {8 - half:.2f} a {8 + half:.2f}')
    print(f'  frente al {"sur (blockstate girado 180)" if crate in FRONT_SOUTH else "norte"}; caja del bloque: '
          f'Block.box({max(0, round(8 - half))}, 0, {max(0, round(8 - half))}, {min(16, round(8 + half))}, {min(16, round(height))}, {min(16, round(8 + half))})')
    bs = os.path.join(RES, 'tfclient', 'blockstates', 'banco_clan.json')
    extra = 180 if crate in FRONT_SOUTH else 0
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        v = {'model': 'tfclient:block/clan/banco_clan'}
        if (y + extra) % 360:
            v['y'] = (y + extra) % 360
        variants['facing=' + facing] = v
    with open(bs, 'w', encoding='utf-8') as f:
        json.dump({'variants': variants}, f, indent=2)
    if ver:
        sys.path.insert(0, HERE)
        import ver_modelo as V
        from PIL import Image
        a = V.render(['tfclient:block/clan/banco_clan'], 360, 30, -24)
        b = V.render(['tfclient:block/clan/banco_clan'], 360, 150, -24)
        im = Image.new('RGB', (720, 360), (96, 118, 104))
        im.paste(a, (0, 0), a)
        im.paste(b, (360, 0), b)
        im.save(ver)
        print(ver)


if __name__ == '__main__':
    main()
