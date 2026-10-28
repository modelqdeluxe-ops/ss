#!/usr/bin/env python3
"""Mete el juego en la app de Android (android/app/src/main/assets/juego).

  python3 corazon-dormido/herramientas/android.py [carpeta-del-juego]

Pone el juego en un solo archivo (index.html, como el .html de las Releases: así el arte horneado se guarda en
el móvil y la segunda vez arranca enseguida) y copia la música (musica/*.ogg). Después se compila con Gradle en
android/ (lo hace el workflow corazon-dormido-android.yml).
"""
import pathlib
import shutil
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from empaquetar import un_archivo  # noqa: E402


def main():
    carpeta = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parent.parent)
    destino = carpeta / 'android' / 'app' / 'src' / 'main' / 'assets' / 'juego'
    if destino.exists():
        shutil.rmtree(destino)
    destino.mkdir(parents=True)
    (destino / 'index.html').write_text(un_archivo(carpeta), encoding='utf-8')
    total = (destino / 'index.html').stat().st_size
    musica = carpeta / 'musica'
    if musica.is_dir():
        (destino / 'musica').mkdir()
        for f in sorted(musica.glob('*.ogg')):
            shutil.copy2(f, destino / 'musica' / f.name)
            total += f.stat().st_size
    print(f'juego en {destino}: {total / 1e6:.1f} MB')


if __name__ == '__main__':
    main()
