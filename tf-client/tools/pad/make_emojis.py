"""Emojis de Comunidad (pad): los de Discord (Twemoji) y animados (Noto Animated Emoji), en atlas + un índice JSON.

    python3 tools/pad/make_emojis.py [--src CARPETA] [--offline]

Baja (una vez; se guardan en --src, por defecto tf-client/build/emoji-src, fuera de git):
  * emojibase-data 16 (MIT): la lista de emojis de Unicode con su grupo, nombre en inglés y en español, etiquetas y
    los atajos de GitHub/Discord (:joy:, :thumbsup:...) y los de CLDR en español.
  * Twemoji 15.1 (los emojis que usa Discord; gráficos CC BY 4.0, https://github.com/jdecked/twemoji) a 72x72.
  * Noto Animated Emoji (Google, CC BY 4.0, https://googlefonts.github.io/noto-emoji-animation/) en WebP de 512 px:
    solo los de tools/pad/emoji/animated.json.

Y deja en src/main/resources/assets/tfclient/textures/gui/emoji/:
  * static_<n>.png (PNG de paleta): atlas de los estáticos, celdas de STATIC_CELL px, PAGE_COLS x PAGE_COLS por hoja (el pad los reduce
    una vez al tamaño exacto en píxeles reales con que se pintan: PadEmojis).
  * anim_<n>.png: atlas de los animados, cada uno sus fotogramas seguidos (celdas de ANIM_CELL px).
  * index.json: hojas, categorías (en el orden del selector de Discord) y cada emoji con su id (el atajo de Discord),
    sus atajos (inglés y español), su nombre en español, palabras para buscar, hoja y celda (y, si es animado,
    fotogramas y milisegundos por fotograma). También los nombres viejos de las reacciones (risa, wow, triste, fuego,
    top) para las fotos que ya las tenían.
  * LICENSE-twemoji.txt, LICENSE-noto-animated.txt y CREDITS.txt con la atribución.

Atajos en español a mano: tools/pad/emoji/alias_es.json. Animados: tools/pad/emoji/animated.json.
"""
import argparse
import concurrent.futures
import io
import json
import os
import subprocess
import sys
import tarfile
import unicodedata

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'tfclient', 'textures', 'gui', 'emoji')

EMOJIBASE = 'https://registry.npmjs.org/emojibase-data/-/emojibase-data-16.0.3.tgz'
TWEMOJI_VER = '15.1.0'
TWEMOJI = 'https://cdn.jsdelivr.net/gh/jdecked/twemoji@' + TWEMOJI_VER + '/assets/72x72/{}.png'
TWEMOJI_LICENSE = 'https://cdn.jsdelivr.net/gh/jdecked/twemoji@' + TWEMOJI_VER + '/LICENSE-GRAPHICS'
NOTO = 'https://fonts.gstatic.com/s/e/notoemoji/latest/{}/512.webp'
CC_BY = 'https://creativecommons.org/licenses/by/4.0/legalcode.txt'

STATIC_CELL = 72      # el tamaño de Twemoji: sin reescalar (nítido hasta 6 px por unidad)
ANIM_CELL = 48
PAGE_COLS = 14        # 14 x 72 = 1008 px por hoja
ANIM_COLS = 21        # 21 x 48 = 1008 px por hoja
MAX_FRAMES = 32       # como mucho 32 fotogramas por animado (se reparten a lo largo de la animación)
MIN_FRAME_MS = 80     # ~12 fotogramas por segundo

# grupo de Unicode (emojibase) -> categoría del selector, en el orden de Discord
GROUPS = {0: 'sonrisas', 1: 'personas', 3: 'naturaleza', 4: 'comida', 6: 'actividades', 5: 'viajes', 7: 'objetos',
          8: 'simbolos', 9: 'banderas'}
CATEGORIES = [  # id, nombre, emoji de la pestaña
    ('animados', 'Animados', 'a_sparkles'),
    ('sonrisas', 'Sonrisas y emociones', 'grinning'),
    ('personas', 'Personas', 'wave'),
    ('naturaleza', 'Naturaleza', 'herb'),
    ('comida', 'Comida y bebida', 'hamburger'),
    ('actividades', 'Actividades', 'soccer'),
    ('viajes', 'Viajes y lugares', 'car'),
    ('objetos', 'Objetos', 'bulb'),
    ('simbolos', 'Símbolos', 'heart'),
    ('banderas', 'Banderas', 'checkered_flag'),
]
# las reacciones de antes (1.3.29-1.3.55) -> su emoji nuevo
LEGACY = {'risa': 'joy', 'wow': 'open_mouth', 'triste': 'cry', 'fuego': 'fire', 'top': 'thumbsup'}


def fetch(url, path):
    """curl (usa el proxy y los certificados del sistema). True si quedó bajado."""
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return True
    tmp = path + '.part'
    r = subprocess.run(['curl', '-sSfL', '--retry', '3', '-o', tmp, url], capture_output=True)
    if r.returncode != 0:
        if os.path.exists(tmp):
            os.remove(tmp)
        return False
    os.replace(tmp, path)
    return True


def norm(s):
    """Para buscar y comparar atajos: minúsculas, sin tildes, espacios y guiones como _."""
    s = unicodedata.normalize('NFD', s.lower())
    s = ''.join(c for c in s if unicodedata.category(c) != 'Mn')
    out = []
    for c in s:
        out.append(c if c.isalnum() or c in '+' else '_')
    return '_'.join(p for p in ''.join(out).split('_') if p)


def bare(emoji):
    return emoji.replace('️', '')


def twemoji_name(hexcode):
    parts = [format(int(p, 16), 'x') for p in hexcode.split('-')]
    if '200d' not in parts:
        parts = [p for p in parts if p != 'fe0f']
    return '-'.join(parts)


def load_emojibase(src, offline):
    tgz = os.path.join(src, 'emojibase-data.tgz')
    if not offline and not fetch(EMOJIBASE, tgz):
        sys.exit('No se pudo bajar emojibase-data')
    with tarfile.open(tgz) as t:
        def read(name):
            return json.load(t.extractfile('package/' + name))
        return (read('en/data.json'), read('es/data.json'), read('en/shortcodes/github.json'),
                read('en/shortcodes/emojibase.json'), read('es/shortcodes/cldr.json'))


def as_list(v):
    return [] if v is None else v if isinstance(v, list) else [v]


def square(im, size):
    """A size x size (media por área con el color premultiplicado: sin bordes oscuros). Sin recortar: cada emoji
    conserva su aire (como en Discord) y los fotogramas de un animado no bailan."""
    im = im.convert('RGBA')
    if im.size == (size, size):
        return im
    side = max(im.size)
    sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    sq.alpha_composite(im, ((side - im.width) // 2, (side - im.height) // 2))
    return sq.convert('RGBa').resize((size, size), Image.BOX if side >= size * 2 else Image.LANCZOS).convert('RGBA')


def fit(frames):
    """Los de Noto traen mucho aire alrededor: se recortan todos los fotogramas por la caja que los contiene a todos
    (así no bailan) y se dejan cuadrados, para que midan como los de Twemoji."""
    box = None
    for f in frames:
        b = f.getchannel('A').point(lambda v: 255 if v > 24 else 0).getbbox()
        if b:
            box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
    if box is None:
        return frames
    side = int(max(box[2] - box[0], box[3] - box[1]) * 1.04)
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    x0, y0 = int(round(cx - side / 2)), int(round(cy - side / 2))
    out = []
    for f in frames:
        sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
        sq.alpha_composite(f.crop((max(0, x0), max(0, y0), min(f.width, x0 + side), min(f.height, y0 + side))),
                           (max(0, -x0), max(0, -y0)))
        out.append(sq)
    return out


def flag_code(hexcode):
    cps = [int(p, 16) for p in hexcode.split('-')]
    if len(cps) == 2 and all(0x1F1E6 <= c <= 0x1F1FF for c in cps):
        return ''.join(chr(ord('a') + c - 0x1F1E6) for c in cps)
    return None


def build_static(src, offline):
    data, es_data, github, ebase, es_cldr = load_emojibase(src, offline)
    es_by_hex = {e['hexcode']: e for e in es_data}
    alias_es = {bare(k): v for k, v in json.load(open(os.path.join(HERE, 'emoji', 'alias_es.json'), encoding='utf-8')).items()
                if k != '_'}
    tw = os.path.join(src, 'twemoji')
    os.makedirs(tw, exist_ok=True)
    wanted = [e for e in data if e.get('group') in GROUPS]
    wanted.sort(key=lambda e: (list(GROUPS).index(e['group']), e.get('order', 0)))
    if not offline:
        with concurrent.futures.ThreadPoolExecutor(16) as ex:
            list(ex.map(lambda e: fetch(TWEMOJI.format(twemoji_name(e['hexcode'])),
                                        os.path.join(tw, twemoji_name(e['hexcode']) + '.png')), wanted))
    out, used = [], set()
    for e in wanted:
        path = os.path.join(tw, twemoji_name(e['hexcode']) + '.png')
        if not os.path.exists(path):
            continue  # Twemoji 15.1 aún no lo tiene (los de Unicode 16)
        hx = e['hexcode']
        names = [n for n in as_list(github.get(hx)) + as_list(ebase.get(hx))]
        cc = flag_code(hx) if e['group'] == 9 else None
        if cc:
            names.insert(0, 'flag_' + cc)
        ids = [norm(n) for n in names if norm(n) and norm(n).replace('_', '').isalnum()]
        ident = next((i for i in ids if i not in used), None)
        if ident is None:
            ident = norm(e['label'])
            while ident in used:
                ident += '_'
        used.add(ident)
        es = es_by_hex.get(hx, {})
        es_label = es.get('label', e['label'])
        short = []
        for n in [ident] + names + alias_es.get(bare(e['emoji']), []) + as_list(es_cldr.get(hx)):
            k = norm(n)
            if k and k not in short:
                short.append(k)
        words = []
        for w in [e['label'], es_label] + as_list(e.get('tags')) + as_list(es.get('tags')):
            k = norm(w)
            if k and k not in words and all(k not in s for s in short):
                words.append(k)
        es_code = (alias_es.get(bare(e['emoji'])) or as_list(es_cldr.get(hx)) or [''])[0]
        out.append({'id': ident, 'c': GROUPS[e['group']], 'u': e['emoji'], 'es': es_label, 's': norm(es_code), 'n': short,
                    'k': ' '.join(words[:12]), 'img': path})
    return out


def build_animated(src, offline):
    lst = json.load(open(os.path.join(HERE, 'emoji', 'animated.json'), encoding='utf-8'))['list']
    nd = os.path.join(src, 'noto')
    os.makedirs(nd, exist_ok=True)
    if not offline:
        with concurrent.futures.ThreadPoolExecutor(8) as ex:
            list(ex.map(lambda a: fetch(NOTO.format(a[0]), os.path.join(nd, a[0] + '.webp')), lst))
    out = []
    for code, ident, alias in lst:
        path = os.path.join(nd, code + '.webp')
        if not os.path.exists(path):
            print('  sin animado:', code, ident)
            continue
        im = Image.open(path)
        frames, durs = [], []
        for i in range(getattr(im, 'n_frames', 1)):
            im.seek(i)
            im.load()
            durs.append(max(10, im.info.get('duration') or 30))
            frames.append(im.convert('RGBA').copy())
        total = sum(durs)
        n = max(1, min(MAX_FRAMES, len(frames), total // MIN_FRAME_MS))
        # n fotogramas repartidos por el tiempo (no por el número: respeta las pausas del original)
        starts, t = [], 0
        for d in durs:
            starts.append(t)
            t += d
        picked = []
        for k in range(n):
            at = total * k / n
            j = max(i for i in range(len(starts)) if starts[i] <= at)
            picked.append(frames[j])
        picked = fit(picked)
        uni = ''.join(chr(int(p, 16)) for p in code.split('_'))
        base = ident[2:]
        short = [norm(x) for x in [ident, base + '_anim'] + alias]
        out.append({'id': ident, 'c': 'animados', 'u': uni, 'es': base.replace('_', ' ') + ' (animado)', 's': norm(alias[0]),
                    'n': list(dict.fromkeys(short)), 'k': 'animado animated ' + base.replace('_', ' '),
                    'frames': [square(f, ANIM_CELL) for f in picked], 't': round(total / n)})
    return out


def pack(items, cell, cols, prefix, frames_of):
    """Mete las celdas en hojas de cols x cols (los fotogramas de un emoji, siempre en la misma hoja)."""
    per = cols * cols
    pages, cur = [], []
    for it in items:
        fr = frames_of(it)
        if len(cur) + len(fr) > per:
            pages.append(cur)
            cur = []
        it['p'], it['i'] = len(pages), len(cur)
        cur.extend(fr)
    if cur:
        pages.append(cur)
    meta = []
    for n, cells in enumerate(pages):
        rows = (len(cells) + cols - 1) // cols
        sheet = Image.new('RGBA', (cols * cell, rows * cell), (0, 0, 0, 0))
        for k, im in enumerate(cells):
            sheet.paste(im, ((k % cols) * cell, (k // cols) * cell))
        name = f'{prefix}_{n}'
        # PNG de paleta (256 colores con transparencia): ~4 veces menos que RGBA y casi igual; el pad las reduce con
        # media por área, que se come el tramado
        sheet = sheet.quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.FLOYDSTEINBERG)
        sheet.save(os.path.join(OUT, name + '.png'), optimize=True)
        meta.append({'file': name, 'cell': cell, 'cols': cols, 'rows': rows})
    return meta


def licenses(src, offline):
    tl = os.path.join(src, 'LICENSE-GRAPHICS')
    cc = os.path.join(src, 'cc-by-4.0.txt')
    if not offline:
        fetch(TWEMOJI_LICENSE, tl)
        fetch(CC_BY, cc)
    head_t = ('Twemoji — Copyright 2019 Twitter, Inc and other contributors; 2024 jdecked and contributors.\n'
              'Graphics licensed under CC-BY 4.0: https://creativecommons.org/licenses/by/4.0/\n'
              'Source: https://github.com/jdecked/twemoji (v' + TWEMOJI_VER + ', assets/72x72).\n'
              'Changes: packed into texture atlases (static_*.png); not otherwise modified.\n\n')
    head_n = ('Noto Animated Emoji — Copyright Google LLC.\n'
              'Licensed under CC BY 4.0: https://creativecommons.org/licenses/by/4.0/\n'
              'Source: https://googlefonts.github.io/noto-emoji-animation/ (512 px WebP).\n'
              'Changes: frames resampled to at most ' + str(MAX_FRAMES) + ' per emoji, scaled down to '
              + str(ANIM_CELL) + 'x' + str(ANIM_CELL) + ' px and packed into texture atlases (anim_*.png).\n\n')
    body_t = open(tl, encoding='utf-8').read() if os.path.exists(tl) else ''
    body_n = open(cc, encoding='utf-8').read() if os.path.exists(cc) else body_t
    with open(os.path.join(OUT, 'LICENSE-twemoji.txt'), 'w', encoding='utf-8') as f:
        f.write(head_t + body_t)
    with open(os.path.join(OUT, 'LICENSE-noto-animated.txt'), 'w', encoding='utf-8') as f:
        f.write(head_n + body_n)
    with open(os.path.join(OUT, 'CREDITS.txt'), 'w', encoding='utf-8') as f:
        f.write('Emojis de Comunidad (TF Pad)\n\n'
                '* Emojis estáticos: Twemoji (los de Discord) — https://github.com/jdecked/twemoji — CC BY 4.0, '
                'Copyright Twitter, Inc and other contributors. Ver LICENSE-twemoji.txt.\n'
                '* Emojis animados: Noto Animated Emoji de Google — https://googlefonts.github.io/noto-emoji-animation/ '
                '— CC BY 4.0. Ver LICENSE-noto-animated.txt.\n'
                '* Nombres y atajos: emojibase-data (MIT, Miles Johnson; datos de Unicode CLDR) y atajos en español '
                'propios (tools/pad/emoji/alias_es.json).\n')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', default=os.path.join(ROOT, 'build', 'emoji-src'))
    ap.add_argument('--offline', action='store_true', help='no bajar nada: usar lo que ya hay en --src')
    a = ap.parse_args()
    os.makedirs(a.src, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith('.png') or f == 'index.json':
            os.remove(os.path.join(OUT, f))
    static = build_static(a.src, a.offline)
    anim = build_animated(a.src, a.offline)
    print(f'{len(static)} estáticos, {len(anim)} animados')
    spages = pack(static, STATIC_CELL, PAGE_COLS, 'static', lambda it: [square(Image.open(it['img']), STATIC_CELL)])
    apages = pack(anim, ANIM_CELL, ANIM_COLS, 'anim', lambda it: it['frames'])
    ids = {e['id'] for e in static}
    for k, v in LEGACY.items():
        assert v in ids, v
    for c in CATEGORIES:
        assert c[2] in ids or c[2] in {e['id'] for e in anim}, c
    by_char = {bare(e['u']): e for e in static}
    for e in anim:  # el nombre en español y las palabras de su emoji quieto
        twin = by_char.get(bare(e['u']))
        if twin:
            e['es'] = twin['es'] + ' (animado)'
            e['k'] = 'animado animated ' + twin['k']
    emojis = []
    for e in anim:
        emojis.append({'id': e['id'], 'c': e['c'], 'u': e['u'], 'es': e['es'], 's': e['s'], 'n': e['n'], 'k': e['k'],
                       'a': 1, 'p': e['p'], 'i': e['i'], 'f': len(e['frames']), 't': e['t']})
    for e in static:
        emojis.append({'id': e['id'], 'c': e['c'], 'u': e['u'], 'es': e['es'], 's': e['s'], 'n': e['n'], 'k': e['k'],
                       'p': e['p'], 'i': e['i']})
    index = {'version': 1, 'static': spages, 'anim': apages,
             'categories': [{'id': c[0], 'name': c[1], 'icon': c[2]} for c in CATEGORIES],
             'legacy': LEGACY, 'emojis': emojis}
    with open(os.path.join(OUT, 'index.json'), 'w', encoding='utf-8') as f:
        json.dump(index, f, ensure_ascii=False, separators=(',', ':'))
    licenses(a.src, a.offline)
    total = 0
    for f in sorted(os.listdir(OUT)):
        sz = os.path.getsize(os.path.join(OUT, f))
        total += sz
        print(f'  {f}: {sz / 1024:.0f} KB')
    print(f'total: {total / 1024 / 1024:.2f} MB')


if __name__ == '__main__':
    main()
