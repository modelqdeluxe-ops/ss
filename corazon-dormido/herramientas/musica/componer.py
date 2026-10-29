#!/usr/bin/env python3
"""Compone la banda sonora de Corazón Dormido y la guarda en OGG (Vorbis).

  python3 corazon-dormido/herramientas/musica/componer.py <carpeta-de-salida> [tema …]

Sin temas, los compone todos (temas.TEMAS). Necesita numpy, scipy y soundfile. Todo es determinista: la misma
partitura da el mismo archivo. Lo usa el workflow de Android para meter la música en la app.
"""
import pathlib
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from motor import SR, guardar_ogg  # noqa: E402
from temas import TEMAS  # noqa: E402


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    salida = pathlib.Path(sys.argv[1])
    salida.mkdir(parents=True, exist_ok=True)
    for nombre in sys.argv[2:] or list(TEMAS):
        t0 = time.time()
        x = TEMAS[nombre]()
        ruta = salida / f'{nombre}.ogg'
        guardar_ogg(ruta, x)
        print(f'{nombre}: {len(x) / SR:.0f} s, {ruta.stat().st_size / 1e6:.2f} MB, {time.time() - t0:.0f} s de cálculo', flush=True)


if __name__ == '__main__':
    main()
