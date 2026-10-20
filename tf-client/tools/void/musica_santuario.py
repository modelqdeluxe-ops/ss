"""La música del santuario del Void (TF Client 1.3.97; rehecha en la 1.3.98: el dueño, «la música del santuario es muy
alta, bájale; y busca una música o mejórala, más bonita»).

    python3 tools/void/musica_santuario.py [--wav carpeta]

Escribe assets/tfclient/sounds/santuario/musica.ogg: «Nana del Alba», ~107 s en bucle sin costura (la cola de la
reverberación y de las notas del final se suma al principio). Re mayor a 72 por minuto, sobre el bajo del Canon
(Re, La, Si m, Fa# m, Sol, Re, Sol, La; un compás cada uno): cuerdas suaves que respiran (sin el coro de la 1.3.97, que
con sus formantes sonaba áspero), el arpa en arpegios de corcheas, un bajo redondo y la melodía, una nana sencilla:
primero la celesta (caja de música), luego la flauta con otra frase y al final las dos, con el arpa más llena. Todo de
instrumentos.py (sin muestras). Suena dentro del Velo del Alba en vez de la música del Void (RiftSky, con fundido y más
baja que la del Void).

    intro (4 compases: cuerdas y arpa) · A (8: celesta) · B (8: flauta) · A' (8: celesta y flauta) · coda (4: cuerdas)
"""
import os
import subprocess
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'bosses'))
from instrumentos import arpa, celesta, espectro, flauta, hz, pan2, place, sala  # noqa: E402
from sonidos_cielo import pad, smooth  # noqa: E402
from sonidos_grieta import SR, hp, lp, rng, write_wav  # noqa: E402

RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'sounds'))
BEAT = 60.0 / 72
BAR = 4 * BEAT
# (bajo, voces de las cuerdas, notas del arpa de grave a agudo)
CHORDS = {
    'D': ('D2', ('F#3', 'A3', 'D4'), ('D3', 'A3', 'D4', 'F#4', 'A4', 'D5')),
    'A': ('A1', ('E3', 'A3', 'C#4'), ('A2', 'E3', 'A3', 'C#4', 'E4', 'A4')),
    'Bm': ('B1', ('F#3', 'B3', 'D4'), ('B2', 'F#3', 'B3', 'D4', 'F#4', 'B4')),
    'F#m': ('F#2', ('F#3', 'A3', 'C#4'), ('F#2', 'C#3', 'F#3', 'A3', 'C#4', 'F#4')),
    'G': ('G1', ('G3', 'B3', 'D4'), ('G2', 'D3', 'G3', 'B3', 'D4', 'G4')),
}
CANON = ['D', 'A', 'Bm', 'F#m', 'G', 'D', 'G', 'A']
PROG = ['D', 'G', 'D', 'A'] + CANON + CANON + CANON + ['G', 'D', 'G', 'A']
# las melodías: (nota, en tiempos desde el principio de la frase, dura en tiempos)
MEL_A = [('F#5', 0, 2), ('E5', 2, 1), ('D5', 3, 1), ('C#5', 4, 2), ('D5', 6, 1), ('E5', 7, 1),
         ('D5', 8, 1.5), ('C#5', 9.5, 0.5), ('B4', 10, 2), ('A4', 12, 2), ('C#5', 14, 2),
         ('B4', 16, 1), ('D5', 17, 1), ('G5', 18, 2), ('F#5', 20, 1.5), ('E5', 21.5, 0.5), ('D5', 22, 2),
         ('B4', 24, 1), ('D5', 25, 1), ('E5', 26, 1), ('G5', 27, 1), ('F#5', 28, 2), ('E5', 30, 2)]
MEL_B = [('A5', 0, 2), ('F#5', 2, 2), ('E5', 4, 1), ('F#5', 5, 1), ('E5', 6, 1), ('C#5', 7, 1),
         ('D5', 8, 2), ('F#5', 10, 2), ('E5', 12, 1), ('C#5', 13, 1), ('A4', 14, 2),
         ('B4', 16, 1), ('C#5', 17, 1), ('D5', 18, 1), ('G5', 19, 1), ('F#5', 20, 2), ('A5', 22, 2),
         ('G5', 24, 1), ('F#5', 25, 1), ('E5', 26, 1), ('D5', 27, 1), ('E5', 28, 3)]
TAIL = 9.0


def strings(t, voices, seed):
    """Cuerdas suaves: sierras desafinadas muy filtradas, con un vaivén lento de volumen (respiran)."""
    x = pad(t, [hz(v) for v in voices], 700, 1100, seed=seed, spread=0.0035, voices=3)
    for c in range(2):
        x[:, c] = lp(x[:, c], 1500, 2)
    return x * (0.85 + 0.15 * np.sin(2 * np.pi * 0.23 * t + seed))[:, None]


def compose():
    total = len(PROG) * BAR
    n = int((total + TAIL) * SR)
    out = np.zeros((n, 2))
    r = rng(1398)
    for i, name in enumerate(PROG):
        bass, voices, harp = CHORDS[name]
        t0 = i * BAR
        coda = i >= len(PROG) - 4
        span = BAR + 2.4
        t = np.arange(int(span * SR)) / SR
        env = smooth(0, 0.9, t) * (1 - smooth(BAR + 0.3, span, t))
        place(out, strings(t, voices, 100 + i) * env[:, None], max(0, t0 - 0.4), 0.42)
        # el bajo: un seno redondo con su octava, en blancas
        for k in (0, 2):
            tb = np.arange(int(2.4 * BEAT * SR)) / SR
            fb = hz(bass)
            xb = (np.sin(2 * np.pi * fb * tb) + 0.3 * np.sin(4 * np.pi * fb * tb)) * smooth(0, 0.05, tb) * np.exp(-tb / 1.6)
            place(out, xb, t0 + k * BEAT, 0.22)
        # el arpa: corcheas que suben y bajan (en la intro y en la coda, más tranquila: negras)
        step = BEAT if (i < 4 or coda) else BEAT / 2
        order = [0, 1, 2, 3, 4, 5, 4, 3] if i % 2 == 0 else [0, 2, 1, 3, 2, 4, 3, 5]
        for k in range(int(round(BAR / step))):
            j = order[k % len(order)]
            at = max(0.0, t0 + k * step + r.uniform(-0.01, 0.01))
            vel = (0.5 + 0.2 * (k == 0) + 0.1 * r.random()) * (0.8 if k % 2 else 1.0)
            place(out, pan2(arpa(hz(harp[j]), 3.0, vel, seed=300 + i * 17 + k), -0.4 + 0.8 * j / 5), at, 0.55)
    # las melodías
    a0, b0, c0 = 4 * BAR, 12 * BAR, 20 * BAR
    for k, (note, at, dur) in enumerate(MEL_A):
        place(out, pan2(celesta(hz(note), 3.0, 0.9, seed=500 + k), 0.15), a0 + at * BEAT, 0.6)
    for k, (note, at, dur) in enumerate(MEL_B):
        place(out, pan2(flauta(hz(note), dur * BEAT * 0.95, 0.85, seed=600 + k), -0.1), b0 + at * BEAT, 0.55)
    for k, (note, at, dur) in enumerate(MEL_A):
        place(out, pan2(celesta(hz(note), 3.0, 0.75, seed=700 + k), 0.2), c0 + at * BEAT, 0.5)
        place(out, pan2(flauta(hz(note), dur * BEAT * 0.95, 0.6, seed=800 + k), -0.15), c0 + at * BEAT, 0.4)
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
