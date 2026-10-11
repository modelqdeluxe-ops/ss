"""Catálogo de la sección «Skills» de la tienda: las clases de skills del mod TF Client.

Lee las clases del mod (tf-client/src/main/resources/assets/tfclient/skills/classes/<id>.json, con los textos en español
que mete build_skills.py), escribe config/skills.json y copia a public/img/skills/<clase>/ el icono de cada skill y su
vista previa animada.

    python3 tools/skills.py                          catálogo + iconos (las vistas previas que ya hay se quedan)
    python3 tools/skills.py --vistas <carpeta>       además, convierte las vistas previas de sim.py de esa carpeta
    python3 tools/skills.py --vistas <carpeta> --rehacer   las vuelve a convertir todas (si cambias los ajustes de abajo)
    python3 tools/skills.py --mod <ruta a tf-client> lee las clases de otra copia del mod

Las vistas previas salen del simulador del mod (Steve + 3 zombis), de 2 en 2 clases como mucho (comparten caché):

    cd tf-client && python3 tools/skills/sim.py <clase> --webp <carpeta>      (crea <carpeta>/<clase>/<skill>.webp)

Aquí se recortan al contenido (encuadre centrado), se quitan los fotogramas quietos del final y se comprimen para la web.

PRECIOS: los de todas las clases están aquí abajo (PRICE_CENTS y COIN_PRICE). Si una clase tiene que costar distinto,
ponla en PRICE_OVERRIDES. Después de cambiarlos: python3 tools/skills.py (no hace falta --vistas).
"""
import json
import os
import shutil
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
WEB = os.path.normpath(os.path.join(HERE, '..'))
OUT_JSON = os.path.join(WEB, 'config', 'skills.json')
OUT_IMG = os.path.join(WEB, 'public', 'img', 'skills')

# --- Precio de cada clase (el mismo para todas mientras el dueño no diga otra cosa) ---
PRICE_CENTS = 599  # con dinero (Stripe), en céntimos de USD: 5,99 USD
COIN_PRICE = 1  # PRUEBA temporal (antes 7500: ~2 semanas jugando 2 h al día, ver HANDOFF, economía)
PRICE_OVERRIDES = {}  # 'glacia': {'price': 799, 'coinPrice': 40000}

# Teclas por defecto de la barra de skills del mod (SkillHud.DEFAULT_KEYS): las skills con tecla, por orden.
KEYS = ['5', '6', '7', '8', '9', '0']  # las teclas de las skills en el mod (SkillHud: eligen la skill)
BOW_CLASSES = {'arquero', 'arquero_despertado', 'null'}  # SkillDefs.BOW_CLASSES: la skill sale al disparar con el arco

# Grupos del filtro de la web, por la primera palabra del rol de la clase
GROUPS = [
    ('cuerpo', 'Cuerpo a cuerpo', '#ff7a4a'),
    ('magia', 'Magia', '#b77dff'),
    ('distancia', 'A distancia', '#9fd86b'),
    ('sigilo', 'Sigilo', '#8a9bff'),
    ('invocacion', 'Invocación', '#45e9c8'),
    ('apoyo', 'Apoyo', '#ffd166'),
]
ROLE_GROUP = {
    'tirador': 'distancia', 'arquero': 'distancia',
    'asesino': 'sigilo',
    'hechicera': 'magia', 'hechicero': 'magia', 'maga': 'magia', 'mago': 'magia',
    'invocador': 'invocacion',
    'soporte': 'apoyo', 'sanador': 'apoyo',
}

# Icono de la clase en la rejilla: por defecto el de su primera skill con tecla; aquí se puede elegir otra.
CLASS_ICON = {'filo_vacio': 'void_edge_dimension_benediction'}  # el icono de la clase (si no, el de su primera skill)

# Vistas previas (de tf-client/tools/skills/previews.py: ya encuadradas a 480 px, 20 por segundo, sin pérdida): lado,
# calidad WebP, máximo de fotogramas y el fondo, que va pintado en
# la imagen (con transparencia pesan el doble). La web pone ese mismo color detrás (--sk-bg en styles.css).
PREVIEW_SIZE = 480
PREVIEW_QUALITY = 72
PREVIEW_MAX_FRAMES = 130
PREVIEW_HOLD = 8
PREVIEW_MIN_FRAMES = 36
PREVIEW_BG = (12, 15, 31)
FRAME_MS = 50


def mod_dir(args):
    if '--mod' in args:
        return os.path.abspath(args[args.index('--mod') + 1])
    return os.path.normpath(os.path.join(WEB, '..', 'tf-client'))


def num(v):
    try:
        return round(float(v), 1)
    except (TypeError, ValueError):
        return 0.0


def group_of(role):
    first = role.split('·')[0].strip().split(' ')[0].lower()
    return ROLE_GROUP.get(first, 'cuerpo')


def texture_path(assets, ref):
    """«tfclient:textures/gui/skills/x/y.png» → ruta del archivo."""
    ns, _, rel = (ref or '').partition(':')
    return os.path.join(assets, ns, rel) if rel else None


def copy_icon(src, dst):
    """Copia el icono (PNG de 32 o 64 px) solo si cambió."""
    if not src or not os.path.exists(src):
        return False
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if not os.path.exists(dst) or open(src, 'rb').read() != open(dst, 'rb').read():
        shutil.copyfile(src, dst)
    return True


def convert_preview(src, dst):
    """WebP de sim.py → WebP para la web: encuadrado al contenido, como mucho PREVIEW_MAX_FRAMES fotogramas, con el
    fondo pintado y más comprimido."""
    im = Image.open(src)
    frames = []
    for i in range(im.n_frames):
        im.seek(i)
        frames.append(np.asarray(im.convert('RGBA')))
    if not frames:
        return False
    stack = np.stack(frames)
    # Se corta cuando ya no pasa nada (sin contar unos fotogramas quietos al final) y nunca pasa del máximo
    diff = np.abs(stack[1:].astype(np.int16) - stack[:-1].astype(np.int16)).mean(axis=(1, 2, 3))
    moving = np.nonzero(diff > 0.25)[0]
    end = (int(moving[-1]) + 2 if len(moving) else 1) + PREVIEW_HOLD
    stack = stack[:max(1, min(end, PREVIEW_MAX_FRAMES, len(stack)))]
    # Skills que no se ven (mejoras, marcas, pasivas sin efecto visible): sin vista previa; la web enseña su icono
    if len(stack) < 2 or not len(moving):
        if os.path.exists(dst):
            os.remove(dst)
        return False
    # Las muy cortas (un tajo): una pausa al final para que no se repitan sin parar
    if len(stack) < PREVIEW_MIN_FRAMES:
        stack = np.concatenate([stack, np.repeat(stack[-1:], PREVIEW_MIN_FRAMES - len(stack), axis=0)])
    # Encuadre: lo que se ve en algún fotograma, en un cuadrado centrado con margen
    alpha = stack[..., 3].max(axis=0) > 8
    ys, xs = np.nonzero(alpha)
    h, w = alpha.shape
    if (w, h) == (PREVIEW_SIZE, PREVIEW_SIZE):
        box = (0, 0, w, h)  # previews.py ya las da encuadradas y al tamaño final: ni recorte ni reescalado
    elif len(xs):
        x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
        side = int(min(max(h, w) * 1.1, max(max(x1 - x0, y1 - y0) * 1.12, 120)))
        bx = int(round((x0 + x1) / 2 - side / 2))
        by = int(round((y0 + y1) / 2 - side / 2))
        box = (bx, by, bx + side, by + side)
    else:
        box = (0, 0, w, h)
    out = []
    for fr in stack:
        img = Image.fromarray(fr, 'RGBA').crop(box)
        if img.size != (PREVIEW_SIZE, PREVIEW_SIZE):
            img = img.resize((PREVIEW_SIZE, PREVIEW_SIZE), Image.LANCZOS)
        bg = Image.new('RGBA', img.size, PREVIEW_BG + (255,))
        bg.alpha_composite(img)
        out.append(bg.convert('RGB'))
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    out[0].save(dst, 'WEBP', save_all=True, append_images=out[1:], duration=FRAME_MS, loop=0,
                quality=PREVIEW_QUALITY, method=6)
    return True


def main():
    args = sys.argv[1:]
    mod = mod_dir(args)
    vistas = os.path.abspath(args[args.index('--vistas') + 1]) if '--vistas' in args else None
    redo = '--rehacer' in args
    assets = os.path.join(mod, 'src', 'main', 'resources', 'assets')
    classes_dir = os.path.join(assets, 'tfclient', 'skills', 'classes')
    if not os.path.isdir(classes_dir):
        sys.exit(f'No encuentro las clases del mod en {classes_dir}')

    classes = []
    converted = 0
    for fname in sorted(os.listdir(classes_dir)):
        if not fname.endswith('.json'):
            continue
        data = json.load(open(os.path.join(classes_dir, fname), encoding='utf-8'))
        cid = data['id']
        info = data.get('class') or {}
        icons = data.get('icons') or {}
        name = info.get('name_es') or info.get('name') or cid
        role = info.get('role_es') or ''
        skills = []
        slot = 0
        for s in data.get('skills') or []:
            if s.get('hidden'):
                continue
            passive = s.get('passive')
            sid = s['id']
            icon_rel = f'img/skills/{cid}/{sid}.png'
            has_icon = copy_icon(texture_path(assets, icons.get(sid)), os.path.join(WEB, 'public', icon_rel))
            prev_rel = f'img/skills/{cid}/prev_{sid}.webp'
            prev_abs = os.path.join(WEB, 'public', prev_rel)
            if vistas:
                src = os.path.join(vistas, cid, sid + '.webp')
                # Solo las que cambiaron desde la última vez (o todas con --rehacer)
                fresh = os.path.exists(prev_abs) and os.path.getmtime(prev_abs) >= os.path.getmtime(src) if os.path.exists(src) else True
                if os.path.exists(src) and (redo or not fresh) and convert_preview(src, prev_abs):
                    converted += 1
            entry = {
                'id': sid,
                'name': s.get('name_es') or s.get('name') or sid,
                'desc': s.get('desc_es') or ' '.join(x for x in (s.get('lore') or [])[:1] if '{' not in x),
                'cooldown': num(s.get('cooldown')),
            }
            if passive:
                # TIMER: se lanza sola todo el rato; DAMAGED: al recibir daño
                entry['passive'] = 'dano' if str(passive.get('type', '')).upper() == 'DAMAGED' else 'siempre'
            else:
                # Como el mod: nunca menos de 4 ticks entre dos usos (SkillServer.MIN_COOLDOWN)
                entry['cooldown'] = max(entry['cooldown'], 0.2)
                entry['slot'] = slot
                entry['key'] = KEYS[slot] if slot < len(KEYS) else None
                if sid == data.get('basic'):
                    entry['basic'] = True
                slot += 1
            entry['icon'] = icon_rel if has_icon else None
            entry['preview'] = prev_rel if os.path.exists(prev_abs) else None
            skills.append(entry)
        actives = [x for x in skills if 'slot' in x]
        # La skill que se enseña primero en la ficha: la de la vista previa con más movimiento (la que más pesa)
        shown = [x for x in skills if x['preview']]
        showcase = max(shown, key=lambda x: os.path.getsize(os.path.join(WEB, 'public', x['preview'])))['id'] if shown else None
        pick = CLASS_ICON.get(cid)
        lead = next((x for x in skills if x['id'] == pick), None) or (actives or skills)[0]
        prices = PRICE_OVERRIDES.get(cid, {})
        classes.append({
            'id': cid,
            'name': name,
            'role': role,
            'group': group_of(role),
            'desc': info.get('desc_es') or ' '.join(info.get('lore') or []),
            'color': info.get('color') or '#45e9ff',
            'bow': cid in BOW_CLASSES,
            'icon': lead['icon'],
            'showcase': showcase,
            'price': int(prices.get('price', PRICE_CENTS)),
            'coinPrice': int(prices.get('coinPrice', COIN_PRICE)),
            'skills': skills,
        })

    classes.sort(key=lambda c: c['name'].lower())
    # Iconos y vistas previas de clases o skills que ya no existen: fuera
    keep = {f"{c['id']}/{x}" for c in classes for s in c['skills'] for x in (f"{s['id']}.png", f"prev_{s['id']}.webp")}
    if os.path.isdir(OUT_IMG):
        for root, _, files in os.walk(OUT_IMG):
            for f in files:
                rel = os.path.relpath(os.path.join(root, f), OUT_IMG).replace(os.sep, '/')
                if rel not in keep:
                    os.remove(os.path.join(root, f))
    out = {
        '_leeme': 'Generado por tools/skills.py (no lo edites a mano: cambia el script y vuelve a ejecutarlo).',
        'price': PRICE_CENTS,
        'coinPrice': COIN_PRICE,
        'keys': KEYS,
        'groups': [{'id': g, 'name': n, 'color': c} for g, n, c in GROUPS if any(x['group'] == g for x in classes)],
        'classes': classes,
    }
    with open(OUT_JSON, 'w', encoding='utf-8') as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1)
        fh.write('\n')
    n_sk = sum(len(c['skills']) for c in classes)
    n_prev = sum(1 for c in classes for s in c['skills'] if s['preview'])
    n_icon = sum(1 for c in classes for s in c['skills'] if s['icon'])
    size = sum(os.path.getsize(os.path.join(r, f)) for r, _, fs in os.walk(OUT_IMG) for f in fs) if os.path.isdir(OUT_IMG) else 0
    print(f'{len(classes)} clases, {n_sk} skills: {n_icon} iconos, {n_prev} vistas previas'
          f'{f" ({converted} convertidas)" if vistas else ""}; img/skills pesa {size / 1e6:.1f} MB')


if __name__ == '__main__':
    main()
