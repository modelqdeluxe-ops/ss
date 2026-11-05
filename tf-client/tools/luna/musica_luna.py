"""La música de la Luna de Sangre (TF Client 1.3.175). El dueño: «quiero una musiquita baja tenebrosa durante el evento,
una melodía tenebrosa bonita tenue» y, al empezar, «una escena para todos con el nacimiento de la luna, una musiquita y un
título».

    python3 tools/luna/musica_luna.py [--wav carpeta]

Escribe en assets/tfclient/sounds/luna_sangre/:
  musica.ogg   «Nana de la Luna Roja», ~103 s en bucle sin costura: re menor a 56 por minuto. Cuerdas oscuras que
               respiran y un bordón grave; la caja de música (celesta) con una nana triste; en la parte B, un coro muy
               bajo y la campana grave cada dos compases. La cola de la reverberación se suma al principio (sin corte).
                 intro (4: cuerdas, bordón, campana) · A (8: caja de música) · B (8: caja más aguda y coro) · coda (4)
  escena.ogg   16 s, la cinemática (los tiempos, en BloodMoonScene): golpe grave y bordón al empezar, coro que sube
               mientras nace la luna, campana al salir el título (3 s), golpe a los 7,5 s (cambia la escena) y un
               fogonazo a los 8,6 / 9,6 / 10,6 / 11,6 s (se encienden los ojos rojos), y la nana que se apaga.
Todo de instrumentos.py (sin muestras).
"""
import os
import subprocess
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'void'))
sys.path.insert(0, os.path.join(HERE, '..', 'bosses'))
from instrumentos import campana, celesta, coro, espectro, hz, pan2, place, sala  # noqa: E402
from sonidos_cielo import pad, smooth  # noqa: E402
from sonidos_grieta import SR, bp, hp, lp, rng, write_wav  # noqa: E402

RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'sounds', 'luna_sangre'))
BEAT = 60.0 / 56
BAR = 4 * BEAT
# (bajo, voces de las cuerdas)
CHORDS = {
    'Dm': ('D2', ('A3', 'D4', 'F4')),
    'Gm': ('G1', ('G3', 'Bb3', 'D4')),
    'Bb': ('Bb1', ('F3', 'Bb3', 'D4')),
    'A': ('A1', ('E3', 'A3', 'C#4')),
    'F': ('F2', ('F3', 'A3', 'C4')),
    'Eb': ('Eb2', ('G3', 'Bb3', 'Eb4')),
}
PROG = (['Dm', 'Dm', 'Bb', 'A']
        + ['Dm', 'Gm', 'Bb', 'A', 'Dm', 'Bb', 'Gm', 'A']
        + ['Bb', 'F', 'Gm', 'A', 'Dm', 'Eb', 'Gm', 'A']
        + ['Dm', 'Bb', 'Gm', 'A'])
# (nota, tiempo desde el principio de la frase, duración en tiempos)
MEL_A = [('A5', 0, 2), ('F5', 2, 1), ('E5', 3, 1), ('D5', 4, 2), ('Bb5', 6, 2),
         ('A5', 8, 1.5), ('G5', 9.5, 0.5), ('F5', 10, 2), ('E5', 12, 3), ('C#5', 15, 1),
         ('D5', 16, 2), ('F5', 18, 1), ('A5', 19, 1), ('Bb5', 20, 2), ('A5', 22, 1), ('G5', 23, 1),
         ('G5', 24, 1.5), ('F5', 25.5, 0.5), ('E5', 26, 2), ('E5', 28, 2), ('D5', 30, 2)]
MEL_B = [('D6', 0, 2), ('C6', 2, 1), ('Bb5', 3, 1), ('A5', 4, 4),
         ('G5', 8, 1), ('A5', 9, 1), ('Bb5', 10, 2), ('A5', 12, 4),
         ('F5', 16, 2), ('G5', 18, 1), ('A5', 19, 1), ('Bb5', 20, 2), ('G5', 22, 2),
         ('D5', 24, 2), ('Eb5', 26, 1), ('F5', 27, 1), ('E5', 28, 2), ('C#5', 30, 2)]
TAIL = 10.0


def strings(t, voices, seed, cutoff=900):
    """Cuerdas oscuras: sierras desafinadas muy filtradas que respiran despacio."""
    x = pad(t, [hz(v) for v in voices], 500, cutoff, seed=seed, spread=0.004, voices=3)
    for c in range(2):
        x[:, c] = lp(x[:, c], cutoff + 300, 2)
    return x * (0.82 + 0.18 * np.sin(2 * np.pi * 0.17 * t + seed))[:, None]


def drone(t, f, seed):
    """Bordón grave: seno con su quinta y un pelo de batido, muy suave."""
    r = rng(seed)
    x = (np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * f * 1.5 * t + r.uniform(0, 6))
         + 0.25 * np.sin(2 * np.pi * (f * 1.003) * t))
    return pan2(x * 0.5, 0.0)


def wind(n, seed, lo=180, hi=900):
    """Viento lejano: ruido en banda que sube y baja (estéreo)."""
    r = rng(seed)
    t = np.arange(n) / SR
    out = np.zeros((n, 2))
    for c in range(2):
        w = bp(r.standard_normal(n), lo, hi)
        out[:, c] = w * (0.5 + 0.5 * np.sin(2 * np.pi * 0.07 * t + c * 1.7 + seed))
    return out * 0.08


def boom(dur=4.0, f=42.0, seed=1):
    """Golpe grave: seno que cae de tono con un poco de ruido sordo."""
    r = rng(seed)
    t = np.arange(int(dur * SR)) / SR
    ff = f * (1 + 0.6 * np.exp(-t / 0.08))
    x = np.sin(2 * np.pi * np.cumsum(ff) / SR) * np.exp(-t / 1.1)
    x += lp(r.standard_normal(len(t)), 300, 2) * np.exp(-t / 0.15) * 0.4
    return x * smooth(0, 0.004, t)


def flare(seed):
    """Un fogonazo (los ojos que se encienden): aire que sube en 0,25 s, golpe sordo y un timbre metálico grave."""
    r = rng(seed)
    pre = 0.25
    n = int(2.6 * SR)
    t = np.arange(n) / SR
    rise = bp(r.standard_normal(n), 900, 5200) * smooth(0, pre, t) * (1 - smooth(pre, pre + 0.03, t)) * 0.5
    x = rise + np.concatenate([np.zeros(int(pre * SR)), boom(2.6 - pre, 55 + 6 * r.random(), seed + 1)])[:n] * 0.7
    bell = np.concatenate([np.zeros(int(pre * SR)), campana(hz('D4') * (1 + 0.01 * r.uniform(-1, 1)), 2.6 - pre, 0.6, seed + 2)])[:n]
    x += bell * 0.55
    return x


def compose_music():
    total = len(PROG) * BAR
    n = int((total + TAIL) * SR)
    out = np.zeros((n, 2))
    for i, name in enumerate(PROG):
        bass, voices = CHORDS[name]
        t0 = i * BAR
        span = BAR + 2.8
        t = np.arange(int(span * SR)) / SR
        env = smooth(0, 1.4, t) * (1 - smooth(BAR + 0.4, span, t))
        place(out, strings(t, voices, 300 + i) * env[:, None], max(0, t0 - 0.6), 0.36)
        place(out, drone(t, hz(bass) / 2 if hz(bass) > 70 else hz(bass), 40 + i) * (env * 0.9)[:, None], max(0, t0 - 0.6), 0.16)
        # la campana grave, cada dos compases (fuera de la parte A, para no tapar la caja)
        if i % 2 == 0 and not (4 <= i < 12):
            place(out, pan2(campana(hz(bass) * 4, 6.0, 0.7, seed=500 + i), -0.2), t0 + 0.02, 0.22)
        # el coro en la parte B, muy bajo
        if 12 <= i < 20:
            tc = np.arange(int((BAR + 2.0) * SR)) / SR
            ce = smooth(0, 1.8, tc) * (1 - smooth(BAR, BAR + 2.0, tc))
            place(out, coro(tc, [hz(v) for v in voices[:2]], seed=700 + i) * ce[:, None], t0, 0.09)
    a0, b0 = 4 * BAR, 12 * BAR
    for k, (note, at, dur) in enumerate(MEL_A):
        place(out, pan2(celesta(hz(note), 3.6, 0.8, seed=900 + k), 0.18), a0 + at * BEAT, 0.5)
    for k, (note, at, dur) in enumerate(MEL_B):
        place(out, pan2(celesta(hz(note), 3.6, 0.7, seed=1100 + k), -0.12), b0 + at * BEAT, 0.45)
        # un eco una octava abajo, más bajo (la nana se oye «doble», como en la distancia)
        place(out, pan2(celesta(hz(note) / 2, 3.0, 0.5, seed=1200 + k), 0.3), b0 + at * BEAT + BEAT / 2, 0.18)
    out += wind(n, 77)
    return out, total


def compose_scene():
    dur = 16.0
    n = int((dur + 6) * SR)
    out = np.zeros((n, 2))
    t = np.arange(int(dur * SR)) / SR
    # golpe grave y bordón que sube
    place(out, boom(5.0, 38, seed=3), 0.0, 0.9)
    place(out, drone(t, hz('D2') / 2, 9) * (smooth(0, 3.0, t) * (1 - smooth(13.5, 16.0, t)))[:, None], 0.0, 0.35)
    # el coro que crece mientras nace la luna (re menor, cada vez más arriba)
    tc = np.arange(int(8.0 * SR)) / SR
    ce = smooth(0.2, 6.5, tc) ** 1.5 * (1 - smooth(7.2, 8.0, tc))
    place(out, coro(tc, [hz('D3'), hz('A3'), hz('D4'), hz('F4')], seed=21) * ce[:, None], 0.3, 0.42)
    place(out, strings(tc, ('A4', 'D5', 'F5'), 22, cutoff=2400) * (smooth(2.0, 7.0, tc) * (1 - smooth(7.2, 8.0, tc)))[:, None], 0.3, 0.16)
    # el título: campana
    place(out, pan2(campana(hz('D4'), 6.0, 0.9, seed=31), 0.0), 3.0, 0.5)
    place(out, pan2(campana(hz('A3'), 6.0, 0.7, seed=32), -0.3), 3.02, 0.35)
    # cambia la escena: golpe y el acorde cae a si bemol
    place(out, boom(4.0, 34, seed=41), 7.5, 1.0)
    tb = np.arange(int(8.5 * SR)) / SR
    place(out, strings(tb, ('F3', 'Bb3', 'D4'), 42, cutoff=700) * (smooth(0, 0.6, tb) * (1 - smooth(6.0, 8.5, tb)))[:, None], 7.5, 0.45)
    # los ojos: cuatro fogonazos
    for k, at in enumerate((8.6, 9.6, 10.6, 11.6)):
        place(out, pan2(flare(60 + k), -0.45 + 0.3 * k), at - 0.25, 0.75)
    # el final: la nana en la caja de música sobre la mayor que se queda abierta
    for k, (note, at) in enumerate((('A5', 12.6), ('F5', 13.2), ('E5', 13.8), ('D5', 14.4))):
        place(out, pan2(celesta(hz(note), 3.6, 0.8, seed=80 + k), 0.15), at, 0.5)
    tf = np.arange(int(4.5 * SR)) / SR
    place(out, strings(tf, ('E3', 'A3', 'C#4'), 90) * (smooth(0, 0.8, tf) * (1 - smooth(2.6, 4.5, tf)))[:, None], 12.4, 0.3)
    return out, dur


def encode(x, name, wav_dir):
    tmp = tempfile.mkdtemp()
    wav = os.path.join(wav_dir or tmp, f'luna_{name}.wav')
    os.makedirs(os.path.dirname(wav), exist_ok=True)
    write_wav(wav, x)
    os.makedirs(RES, exist_ok=True)
    out = os.path.join(RES, name + '.ogg')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '3', '-ac', '2', out], check=True)
    print(f'luna_sangre/{name}: {len(x) / SR:.1f} s, {os.path.getsize(out) // 1024} KB')


def main():
    wav_dir = sys.argv[sys.argv.index('--wav') + 1] if '--wav' in sys.argv else None
    dry, total = compose_music()
    wet = sala(dry, 5.2, 0.42, 3800, seed=1175)
    wet = np.stack([hp(wet[:, c], 30, 2) for c in range(2)], 1)
    n = int(total * SR)
    loop = wet[:n].copy()
    tail = wet[n:]
    m = min(len(tail), n)
    loop[:m] += tail[:m]
    loop = loop / np.max(np.abs(loop)) * 0.8
    espectro(loop, 'luna_sangre/musica')
    print('   empalme del bucle: salto %.4f (el mayor entre dos muestras: %.4f)' % (
        np.abs(loop[-1] - loop[0]).max(), np.abs(np.diff(loop, axis=0)).max()))
    encode(loop, 'musica', wav_dir)

    dry, dur = compose_scene()
    wet = sala(dry, 4.0, 0.34, 4200, seed=1176)
    wet = np.stack([hp(wet[:, c], 28, 2) for c in range(2)], 1)
    x = wet[:int(dur * SR)]
    fade_out = 1 - smooth(dur - 1.2, dur, np.arange(len(x)) / SR)
    x = x * fade_out[:, None]
    x = x / np.max(np.abs(x)) * 0.85
    espectro(x, 'luna_sangre/escena')
    encode(x, 'escena', wav_dir)


if __name__ == '__main__':
    main()
