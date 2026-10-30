"""Archivo legal: cada versión de los Términos y del Aviso de privacidad, tal como se publicó.

(30 oct 2026, el dueño: «espero que tengas una copia de los términos anteriores, para que podamos citarlos en cualquier
disputa de los pagos anteriores») Lee el historial de git de origin/main (público en GitHub: la fecha de cada versión la
prueba su commit) y escribe:

    public/archivo-legal/index.html                       la lista de versiones, con su vigencia, commit y SHA-256
    public/archivo-legal/terminos-<versión>[-b].html      copia exacta de /terminos de esa versión, con un aviso arriba
    public/archivo-legal/privacidad-<versión>[-b].html    copia exacta de /privacidad de esa versión
    config/legal-archive.json                             lo mismo para el Worker: el recibo y el expediente de una
                                                          disputa citan la versión que aceptó cada pedido y copian su
                                                          política de reembolsos tal cual

Una «versión» es lo que dice config/legal.json; si el texto cambió sin cambiar de versión, cada texto distinto es una
variante (-b, -c...) con sus fechas. Lo de antes de config/legal.json (6 oct 2026) va como «sin número».

    git fetch --unshallow origin main   (si el clon es superficial: hace falta todo el historial)
    python3 tools/legal_archive.py

Ejecútalo cada vez que se publique una versión nueva de los Términos (después de fusionarla en main, para que su commit
sea el de main).
"""
import hashlib
import html
import json
import os
import re
import subprocess
from datetime import datetime, timezone, timedelta

HERE = os.path.dirname(os.path.abspath(__file__))
WEB = os.path.normpath(os.path.join(HERE, '..'))
ROOT = os.path.normpath(os.path.join(WEB, '..'))
OUT_DIR = os.path.join(WEB, 'public', 'archivo-legal')
OUT_JSON = os.path.join(WEB, 'config', 'legal-archive.json')
REPO = 'https://github.com/modelqdeluxe-ops/ss'
SITE = 'https://tierrasfantásticas.store'
PATHS = {
    'terminos': 'tierras-fantasticas/public/terminos.html',
    'privacidad': 'tierras-fantasticas/public/privacidad.html',
    'legal': 'tierras-fantasticas/config/legal.json',
}
MX = timezone(timedelta(hours=-6))  # hora de Ciudad de México (sin horario de verano desde 2022)
MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre',
          'diciembre']


def git(*args):
    return subprocess.run(['git', *args], cwd=ROOT, capture_output=True, text=True, check=True).stdout


def show(commit, path):
    try:
        return subprocess.run(['git', 'show', f'{commit}:{path}'], cwd=ROOT, capture_output=True, check=True).stdout
    except subprocess.CalledProcessError:
        return None


def main_body(page):
    m = re.search(rb'<main[^>]*>(.*?)</main>', page, re.S)
    return m.group(1) if m else page


def when(iso):
    d = datetime.fromisoformat(iso).astimezone(MX)
    return f'{d.day} de {MONTHS[d.month - 1]} de {d.year}, {d:%H:%M}'


def refunds_text(page):
    """El texto de «Cancelaciones y reembolsos» de esa versión, tal cual (sin etiquetas)."""
    m = re.search(rb'<section[^>]*id="reembolsos"[^>]*>(.*?)</section>', page, re.S)
    if not m:
        return ''
    s = re.sub(r'\s+', ' ', m.group(1).decode('utf-8'))
    s = re.sub(r'<h2[^>]*>.*?</h2>', '', s, flags=re.S)
    s = re.sub(r'<li[^>]*>', '\n• ', s)
    s = re.sub(r'</p>', '\n', s)
    s = re.sub(r'<[^>]+>', '', s)
    s = html.unescape(s)
    lines = [re.sub(r'\s+', ' ', x).strip() for x in s.split('\n')]
    return '\n'.join(x for x in lines if x)


def banner(kind, v, page_sha):
    title = 'Términos y condiciones' if kind == 'terminos' else 'Aviso de privacidad'
    until = f"hasta el {when(v['to'])}" if v['to'] else 'hasta hoy (es la vigente)'
    current = '/terminos' if kind == 'terminos' else '/privacidad'
    return (
        '<div style="background:#fff7d6;border-bottom:2px solid #e0b100;color:#2a1d14;font:15px/1.5 Arial,sans-serif;'
        'padding:12px 16px;position:relative;z-index:9999">'
        f'<b>Versión archivada de los {title}</b> · versión <b>{html.escape(v["label"])}</b>, vigente desde el '
        f'{when(v["from"])} {until} (hora de Ciudad de México). Es una copia exacta de la página publicada entonces; las '
        f'compras de esas fechas se rigen por ella. La versión vigente está en <a href="{current}">{current}</a>; todas las '
        f'versiones, en <a href="/archivo-legal/">el archivo legal</a>. Original publicado en GitHub: commit '
        f'<a href="{REPO}/blob/{v["commit"]}/{PATHS[kind]}">{v["commit"][:10]}</a> · SHA-256 del original: '
        f'<code style="font-size:12px;word-break:break-all">{page_sha}</code></div>'
    )


def archived(page, kind, v, sha):
    s = page.decode('utf-8')
    s = s.replace('<head>', '<head>\n  <meta name="robots" content="noindex">', 1)
    s = re.sub(r'(<body[^>]*>)', lambda m: m.group(1) + '\n' + banner(kind, v, sha), s, count=1)
    return s


def collect():
    rows = git('log', '--reverse', '--format=%H %cI', 'origin/main', '--', *PATHS.values()).split('\n')
    variants = []
    for line in filter(None, rows):
        commit, at = line.split()
        terms, privacy, legal = (show(commit, PATHS[k]) for k in ('terminos', 'privacidad', 'legal'))
        if not terms or not privacy:
            continue
        version = json.loads(legal)['version'] if legal else None
        key = (version, hashlib.sha256(main_body(terms)).hexdigest(), hashlib.sha256(main_body(privacy)).hexdigest())
        if variants and variants[-1]['key'] == key:
            continue
        variants.append({'key': key, 'version': version, 'commit': commit, 'from': at, 'terms': terms, 'privacy': privacy})
    for i, v in enumerate(variants):
        v['to'] = variants[i + 1]['from'] if i + 1 < len(variants) else None
    # nombre de cada variante: la versión (o «sin-numero-<día>») y -b, -c... si el texto cambió dentro de la misma versión
    seen = {}
    for v in variants:
        base = v['version'] or 'sin-numero'
        n = seen.get(base, 0)
        seen[base] = n + 1
        v['slug'] = base + ('' if n == 0 else '-' + chr(ord('a') + n))
        v['label'] = (v['version'] or 'sin número') + ('' if n == 0 else f' ({chr(ord("a") + n)})')
    return variants


def write(variants):
    os.makedirs(OUT_DIR, exist_ok=True)
    keep = {'index.html'}
    manifest = []
    for v in variants:
        entry = {
            'version': v['version'],
            'label': v['label'],
            'from': v['from'],
            'to': v['to'],
            'commit': v['commit'],
        }
        for kind, page in (('terminos', v['terms']), ('privacidad', v['privacy'])):
            name = f'{kind}-{v["slug"]}.html'
            sha = hashlib.sha256(page).hexdigest()
            with open(os.path.join(OUT_DIR, name), 'w', encoding='utf-8') as f:
                f.write(archived(page, kind, v, sha))
            keep.add(name)
            # sin «.html»: Cloudflare (html_handling) redirige la URL con extensión a esta
            entry['terms' if kind == 'terminos' else 'privacy'] = f'/archivo-legal/{name[:-5]}'
            entry['termsSha256' if kind == 'terminos' else 'privacySha256'] = sha
        entry['refunds'] = refunds_text(v['terms'])
        manifest.append(entry)
    for f in os.listdir(OUT_DIR):
        if f not in keep:
            os.remove(os.path.join(OUT_DIR, f))
    with open(OUT_JSON, 'w', encoding='utf-8') as f:
        json.dump({'_leeme': 'Generado por tools/legal_archive.py (no lo edites a mano).', 'versions': manifest}, f,
                  ensure_ascii=False, indent=1)
        f.write('\n')
    rows = []
    for e in reversed(manifest):
        until = when(e['to']) if e['to'] else '<b>vigente</b>'
        rows.append(
            f'<tr><td><b>{html.escape(e["label"])}</b></td><td>{when(e["from"])}</td><td>{until}</td>'
            f'<td><a href="{e["terms"]}">Términos</a> · <a href="{e["privacy"]}">Privacidad</a></td>'
            f'<td><a href="{REPO}/commit/{e["commit"]}"><code>{e["commit"][:10]}</code></a></td>'
            f'<td><code style="font-size:11px;word-break:break-all">{e["termsSha256"]}</code></td></tr>'
        )
    index = f'''<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex">
  <title>Archivo legal · Tierras Fantásticas</title>
  <style>
    :root {{ color-scheme: light; }}
    body {{ margin: 0; background: #fffaf0; color: #2a1d14; font: 15px/1.55 Arial, sans-serif; }}
    main {{ max-width: 1100px; margin: 0 auto; padding: 24px 16px 48px; }}
    h1 {{ font-size: 26px; margin: 0 0 8px; }}
    p {{ max-width: 820px; }}
    .scroll {{ overflow-x: auto; }}
    table {{ border-collapse: collapse; width: 100%; min-width: 760px; background: #fff; }}
    th, td {{ border: 1px solid #e6d8c3; padding: 8px 10px; text-align: left; vertical-align: top; }}
    th {{ background: #f6ead6; }}
    a {{ color: #7a3e00; }}
  </style>
</head>
<body>
<main>
  <h1>Archivo legal de Tierras Fantásticas</h1>
  <p>Todas las versiones de los <a href="/terminos">Términos y condiciones</a> y del <a href="/privacidad">Aviso de
  privacidad</a>, tal como se publicaron, con las fechas en que estuvieron vigentes (hora de Ciudad de México). Cada
  compra se rige por la versión que se aceptó al pagarla; su número sale en el recibo. La fecha de cada versión se puede
  comprobar en el historial público del repositorio de la web en GitHub (commit) y cada copia lleva la huella SHA-256
  del archivo original.</p>
  <div class="scroll"><table>
    <thead><tr><th>Versión</th><th>Vigente desde</th><th>Hasta</th><th>Textos</th><th>Commit en GitHub</th><th>SHA-256 de los Términos</th></tr></thead>
    <tbody>
      {chr(10).join("      " + r for r in rows).strip()}
    </tbody>
  </table></div>
</main>
</body>
</html>
'''
    with open(os.path.join(OUT_DIR, 'index.html'), 'w', encoding='utf-8') as f:
        f.write(index)
    return manifest


if __name__ == '__main__':
    if git('rev-parse', '--is-shallow-repository').strip() == 'true':
        raise SystemExit('El clon es superficial: git fetch --unshallow origin main')
    m = write(collect())
    for e in m:
        print(f"{e['label']:<22} {e['from'][:16]} → {(e['to'] or 'vigente')[:16]}  {e['commit'][:10]}  reembolsos: {len(e['refunds'])} car.")
