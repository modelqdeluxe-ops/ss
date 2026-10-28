#!/usr/bin/env python3
"""Empaqueta una versión de Corazón Dormido para su Release de GitHub.

  python3 corazon-dormido/herramientas/empaquetar.py <carpeta-del-juego> <versión> <carpeta-de-salida>

Saca dos archivos:
  corazon-dormido-<versión>.html  todo el juego en un solo archivo (se abre con doble clic, sin servidor)
  corazon-dormido-<versión>.zip   la carpeta del juego (index.html, js/ y el README)
"""
import pathlib
import re
import sys
import zipfile


def un_archivo(carpeta: pathlib.Path) -> str:
    html = (carpeta / 'index.html').read_text(encoding='utf-8')

    def meter(m):
        js = (carpeta / m.group(1)).read_text(encoding='utf-8').replace('</script', '<\\/script')
        return f'<script>/* {m.group(1)} */\n{js}\n</script>'

    html, n = re.subn(r'<script src="([^"]+)"></script>', meter, html)
    if n == 0:
        raise SystemExit('index.html no tiene <script src="…">: ¿es la carpeta del juego?')
    return html


def main():
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    carpeta, ver, salida = pathlib.Path(sys.argv[1]), sys.argv[2], pathlib.Path(sys.argv[3])
    salida.mkdir(parents=True, exist_ok=True)
    nombre = f'corazon-dormido-{ver}'
    (salida / f'{nombre}.html').write_text(un_archivo(carpeta), encoding='utf-8')
    with zipfile.ZipFile(salida / f'{nombre}.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        for f in sorted([carpeta / 'index.html', carpeta / 'README.md', *(carpeta / 'js').glob('*.js')]):
            if f.exists():
                z.write(f, f'{nombre}/{f.relative_to(carpeta).as_posix()}')
    print(f'{nombre}: html {(salida / f"{nombre}.html").stat().st_size} B, zip {(salida / f"{nombre}.zip").stat().st_size} B')


if __name__ == '__main__':
    main()
