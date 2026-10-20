"""La música del santuario del Void (TF Client 1.3.97; el dueño: «que suene otra melodía dentro del santuario»).

    python3 tools/void/musica_santuario.py [--wav carpeta]

Escribe assets/tfclient/sounds/santuario/musica.ogg: ~96 s en bucle sin costura (la cola de la reverberación y de las
notas del final se suma al principio). Re mayor, tranquila y luminosa, «lo último del Alba»: un coro que respira, un bajo
suave, el arpa en arpegios (la Orden), campanas lejanas en los cambios de acorde y, en la segunda mitad, una flauta con
una melodía sencilla. Todo de instrumentos.py (sin muestras). Suena dentro del Velo del Alba en vez de la música del
Void (RiftSky, con fundido).
"""
import os
import subprocess
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'bosses'))
from instrumentos import arpa, campana, coro, espectro, flauta, hz, pan2, place, sala  # noqa: E402
from sonidos_cielo import pad, smooth  # noqa: E402
from sonidos_grieta import SR, hp, rng, write_wav  # noqa: E402

RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'sounds'))
CHORD = 6.0   # segundos por acorde
# (raíz para el bajo, voces del coro, notas del arpa)
CHORDS = {
    'D': ('D2', ('A3', 'D4', 'F#4'), ('D3', 'A3', 'D4', 'F#4', 'A4', 'D5')),
    'Bm': ('B1', ('B3', 'D4', 'F#4'), ('B2', 'F#3', 'B3', 'D4', 'F#4', 'B4')),
    'G': ('G1', ('B3', 'D4', 'G4'), ('G2', 'D3', 'G3', 'B3', 'D4', 'G4')),
    'A': ('A1', ('A3', 'C#4', 'E4'), ('A2', 'E3', 'A3', 'C#4', 'E4', 'A4')),
    'Asus': ('A1', ('A3', 'D4', 'E4'), ('A2', 'E3', 'A3', 'D4', 'E4', 'A4')),
    'Em': ('E2', ('B3', 'E4', 'G4'), ('E2', 'B2', 'E3', 'G3', 'B3', 'E4')),
    'F#m': ('F#2', ('A3', 'C#4', 'F#4'), ('F#2', 'C#3', 'F#3', 'A3', 'C#4', 'F#4')),
}
PROG = ['D', 'Bm', 'G', 'A', 'D', 'F#m', 'G', 'Asus',      # A: el santuario que respira
        'Bm', 'G', 'D', 'A', 'Bm', 'G', 'Em', 'A']         # B: con la flauta
# la melodía de la flauta (nota, empieza en s desde la parte B, dura s)
MELODY = [('F#5', 0.0, 2.4), ('E5', 2.5, 1.0), ('D5', 3.6, 2.2),
          ('B4', 6.0, 2.8), ('D5', 9.0, 2.8),
          ('A4', 12.0, 1.8), ('D5', 14.0, 1.0), ('F#5', 15.1, 2.7),
          ('E5', 18.0, 3.6), ('C#5', 21.8, 2.0),
          ('D5', 24.0, 1.8), ('F#5', 26.0, 1.8), ('E5', 28.0, 1.9),
          ('D5', 30.0, 2.8), ('B4', 33.0, 2.8),
          ('G4', 36.0, 1.8), ('B4', 38.0, 1.8), ('E5', 40.0, 1.9),
          ('C#5', 42.0, 2.8), ('A4', 45.0, 2.7)]
TAIL = 9.0


def compose():
    total = len(PROG) * CHORD
    n = int((total + TAIL) * SR)
    out = np.zeros((n, 2))
    r = rng(1397)
    t_all = np.arange(n) / SR
    for i, name in enumerate(PROG):
        bass, voices, harp = CHORDS[name]
        t0 = i * CHORD
        # el coro: entra en 1,6 s y se queda hasta que el siguiente ya ha entrado (se solapan)
        span = CHORD + 3.2
        t = np.arange(int(span * SR)) / SR
        env = smooth(0, 1.6, t) * (1 - smooth(CHORD + 0.4, span, t))
        ch = coro(t, [hz(v) for v in voices], seed=100 + i) * env[:, None]
        place(out, ch, max(0, t0 - 0.8), 0.55)
        # el bajo: sierras graves muy filtradas
        b = pad(t, [hz(bass), hz(bass) * 2], 380, 520, seed=200 + i, spread=0.003, voices=2) * env[:, None]
        place(out, b, max(0, t0 - 0.8), 0.32)
        # el arpa: arpegio que sube y baja (una nota cada 0,5 s), con algo de vida
        order = [0, 1, 2, 3, 4, 5, 4, 3, 2, 3, 4, 5] if i % 2 == 0 else [0, 2, 1, 3, 2, 4, 3, 5, 4, 3, 2, 1]
        for k, j in enumerate(order):
            at = t0 + k * 0.5 + r.uniform(-0.012, 0.012)
            vel = (0.55 + 0.25 * (k == 0) + 0.12 * r.random()) * (0.85 if k % 2 else 1.0)
            note = arpa(hz(harp[j]), 3.5, vel, seed=300 + i * 13 + k)
            place(out, pan2(note, -0.35 + 0.7 * j / 5), at, 0.75)
        # una campana lejana en cada cambio de acorde (cada dos)
        if i % 2 == 0:
            top = hz(voices[-1]) * 2
            place(out, pan2(campana(top, 5.0, 0.5, seed=400 + i), 0.25 if i % 4 else -0.25), t0 + 0.05, 0.35)
    # la flauta (parte B)
    b0 = 8 * CHORD
    for k, (note, at, dur) in enumerate(MELODY):
        x = flauta(hz(note), dur, 0.9, seed=500 + k)
        place(out, pan2(x, 0.1), b0 + at, 0.5)
    _ = t_all
    return out, total


def main():
    wav_dir = sys.argv[sys.argv.index('--wav') + 1] if '--wav' in sys.argv else None
    dry, total = compose()
    wet = sala(dry, 4.6, 0.36, 4500, seed=1398)
    # (el filtro de graves antes de cerrar el bucle: después, al empezar sin memoria, dejaba un escalón en el empalme)
    wet = np.stack([hp(wet[:, c], 35, 2) for c in range(2)], 1)
    n = int(total * SR)
    # el bucle: lo que pasa de «total» (cola de notas y reverberación) se suma al principio
    loop = wet[:n].copy()
    tail = wet[n:]
    m = min(len(tail), n)
    loop[:m] += tail[:m]
    loop = loop / np.max(np.abs(loop)) * 0.8
    espectro(loop, 'santuario/musica')
    # (que el empalme del bucle no salte: el último y el primer valor, casi iguales)
    print('   empalme del bucle: salto %.4f (el mayor entre dos muestras: %.4f)' % (
        np.abs(loop[-1] - loop[0]).max(), np.abs(np.diff(loop, axis=0)).max()))
    tmp = tempfile.mkdtemp()
    wav = os.path.join(wav_dir or tmp, 'santuario_musica.wav')
    os.makedirs(os.path.dirname(wav), exist_ok=True)
    write_wav(wav, loop)
    out = os.path.join(RES, 'santuario', 'musica.ogg')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '3', '-ac', '2', out], check=True)
    print(f'santuario/musica: {len(loop) / SR:.1f} s, {os.path.getsize(out) // 1024} KB')


if __name__ == '__main__':
    main()
