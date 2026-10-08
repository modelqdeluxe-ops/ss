"""Sonidos del TF Pad, sintetizados desde cero (originales, sin muestras de nadie).

    python3 tools/pad/build_sounds.py            # escribe src/main/resources/assets/tfclient/sounds/pad/*.ogg
    python3 tools/pad/build_sounds.py --wav DIR  # además, los .wav para escucharlos

Timbre: campanita de cristal (parciales de marimba + un toque de FM) con una cola de reverb corta y cálida, en
la escala de mi mayor (mi, sol#, si): suena a «aparato mágico» y combina con el resto del servidor.
Necesita numpy y ffmpeg (libvorbis).
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient',
                                        'sounds', 'pad'))
SR = 44100


def note(name):
    """'E5' → Hz."""
    names = {'C': -9, 'C#': -8, 'D': -7, 'D#': -6, 'E': -5, 'F': -4, 'F#': -3, 'G': -2, 'G#': -1, 'A': 0, 'A#': 1, 'B': 2}
    n, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((names[n] + 12 * (octave - 4)) / 12)


def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def env(t, attack, decay):
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-np.maximum(t - attack, 0) / decay)


def bell(f, dur, decay=0.35, bright=1.0):
    """Campanita: fundamental + parciales de marimba (4x, 10x) que se apagan antes + un poco de FM."""
    t = t_axis(dur)
    mod = 0.6 * bright * np.exp(-t / 0.05) * np.sin(2 * np.pi * f * 3.5 * t)
    s = 1.00 * np.sin(2 * np.pi * f * t + mod) * env(t, 0.003, decay)
    s += 0.32 * bright * np.sin(2 * np.pi * f * 4.0 * t) * env(t, 0.002, decay * 0.35)
    s += 0.10 * bright * np.sin(2 * np.pi * f * 10.0 * t) * env(t, 0.001, decay * 0.12)
    # coro: una copia un poco desafinada da cuerpo
    s += 0.35 * np.sin(2 * np.pi * f * 1.004 * t) * env(t, 0.004, decay * 0.9)
    return s


def blip(f0, f1, dur, decay):
    """Burbuja: un seno que baja de tono muy rápido (toque de pantalla)."""
    t = t_axis(dur)
    f = f1 + (f0 - f1) * np.exp(-t / 0.012)
    phase = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(phase) * env(t, 0.0015, decay)
    s += 0.25 * np.sin(2 * phase) * env(t, 0.001, decay * 0.5)
    return s


def whoosh(dur, f_from, f_to, level=0.25, seed=1):
    """Soplo suave: ruido filtrado con un paso banda que se mueve."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    noise = rng.standard_normal(n)
    t = t_axis(dur)
    fc = f_from * (f_to / f_from) ** (t / dur)
    out = np.zeros(n)
    lp = bp = 0.0
    for i in range(n):  # filtro de estado variable
        g = 2 * np.sin(np.pi * fc[i] / SR)
        hp = noise[i] - lp - 0.35 * bp
        bp += g * hp
        lp += g * bp
        out[i] = bp
    # segunda pasada: paso bajo suave para que no sisee
    lp2 = 0.0
    for i in range(n):
        lp2 += 0.18 * (out[i] - lp2)
        out[i] = lp2
    shape = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 2
    return level * out / (np.max(np.abs(out)) + 1e-9) * shape


def fade_end(x, ms=40):
    """Ninguna nota se corta de golpe: el final se apaga en ms milisegundos (si no, chasquea)."""
    n = min(len(x), int(SR * ms / 1000))
    x = x.copy()
    x[-n:] *= np.linspace(1, 0, n) ** 2
    return x


def place(dst, src, at):
    """Suma src en dst desde el segundo at; si no cabe, se recorta y LUEGO se funde (nunca un corte seco)."""
    i = int(at * SR)
    n = min(len(src), len(dst) - i)
    dst[i:i + n] += fade_end(src[:n])


def reverb(x, mix=0.18, size=1.0):
    """Reverb pequeña de Schroeder (4 peines + 2 pasa-todo), cálida y corta."""
    combs = [int(SR * d * size) for d in (0.0297, 0.0371, 0.0411, 0.0437)]
    out = np.zeros(len(x) + int(SR * 0.5))
    xin = np.concatenate([x, np.zeros(int(SR * 0.5))])
    for d, g in zip(combs, (0.78, 0.76, 0.74, 0.72)):
        buf = np.zeros(len(xin))
        damp = 0.0
        for i in range(len(xin)):
            fb = buf[i - d] if i >= d else 0.0
            damp = 0.6 * fb + 0.4 * damp  # pierde agudos en cada vuelta
            buf[i] = xin[i] + g * damp
        out += buf
    for d in (int(SR * 0.005), int(SR * 0.0017)):
        y = np.zeros(len(out))
        for i in range(len(out)):
            xd = out[i - d] if i >= d else 0.0
            yd = y[i - d] if i >= d else 0.0
            y[i] = -0.7 * out[i] + xd + 0.7 * yd
        out = y
    wet = out / (np.max(np.abs(out)) + 1e-9)
    dry = np.concatenate([x, np.zeros(int(SR * 0.5))])
    return (1 - mix) * dry / (np.max(np.abs(dry)) + 1e-9) + mix * wet


def finish(x, peak, tail=0.02):
    # quita el final en silencio y suaviza el corte
    nz = np.nonzero(np.abs(x) > 1e-3 * np.max(np.abs(x)))[0]
    x = x[: (nz[-1] + 1 if len(nz) else len(x))]
    fade = min(len(x), int(tail * SR))
    x[-fade:] *= np.linspace(1, 0, fade)
    x = x - np.mean(x)
    return peak * x / (np.max(np.abs(x)) + 1e-9)


# ---------------------------------------------------------------------------------------------------------------------

def s_open():
    """Encender: arpegio que sube (mi-sol#-si-mi) con un brillo arriba y un «fum» grave que arranca."""
    x = np.zeros(int(SR * 1.6))
    for i, (n, v) in enumerate((('E5', 0.75), ('G#5', 0.7), ('B5', 0.7), ('E6', 0.85))):
        place(x, v * bell(note(n), 1.1, decay=0.22 + 0.04 * i), 0.055 * i)
    place(x, 0.18 * bell(note('B6'), 0.7, decay=0.35, bright=0.6), 0.24)
    t = t_axis(0.25)
    sub = 0.35 * np.sin(2 * np.pi * (90 + 160 * t / 0.25) * t) * env(t, 0.01, 0.08)
    place(x, sub, 0.0)
    place(x, whoosh(0.32, 700, 3600, level=0.035, seed=3), 0.0)
    return finish(reverb(x, 0.22), 0.80)


def s_close():
    """Apagar: dos notas que bajan (si → mi) y un soplo que se va."""
    x = np.zeros(int(SR * 0.8))
    place(x, 0.7 * bell(note('B5'), 0.6, decay=0.18), 0.0)
    place(x, 0.75 * bell(note('E5'), 0.7, decay=0.25), 0.07)
    place(x, whoosh(0.26, 3000, 600, level=0.03, seed=5), 0.0)
    return finish(reverb(x, 0.18), 0.65)


def s_select():
    """Tocar una app: burbuja clara con una chispa de campanita."""
    x = np.zeros(int(SR * 0.4))
    place(x, blip(1900, 880, 0.12, 0.035), 0.0)
    place(x, 0.35 * bell(note('B6'), 0.3, decay=0.07, bright=0.5), 0.012)
    return finish(reverb(x, 0.12, 0.7), 0.62)


def s_back():
    """Volver: la misma burbuja, más grave y corta."""
    x = np.zeros(int(SR * 0.3))
    place(x, blip(1200, 520, 0.1, 0.03), 0.0)
    place(x, 0.25 * bell(note('E6'), 0.25, decay=0.05, bright=0.4), 0.01)
    return finish(reverb(x, 0.10, 0.7), 0.55)


def s_page():
    """Abrir una página: soplo corto que sube y dos notas (sol# → si)."""
    x = np.zeros(int(SR * 0.7))
    place(x, whoosh(0.18, 900, 3400, level=0.035, seed=7), 0.0)
    place(x, 0.6 * bell(note('G#5'), 0.5, decay=0.16), 0.05)
    place(x, 0.65 * bell(note('B5'), 0.6, decay=0.22), 0.11)
    return finish(reverb(x, 0.18), 0.6)


def s_hover():
    """Pasar por encima: un tic muy suave."""
    x = np.zeros(int(SR * 0.12))
    place(x, blip(3200, 2400, 0.05, 0.012), 0.0)
    return finish(x, 0.30, 0.01)


def click_burst(dur, seed, bright=1.0):
    """Chasquido mecánico: ruido muy corto con un pico agudo (el obturador)."""
    rng = np.random.default_rng(seed)
    t = t_axis(dur)
    n = rng.standard_normal(len(t))
    # paso alto sencillo: diferencia de muestras
    hp = np.concatenate([[0], np.diff(n)])
    return bright * hp * env(t, 0.0005, dur / 4)


def s_foto():
    """Foto: clic-clac del obturador, un golpecito grave y un brillo de cristal."""
    x = np.zeros(int(SR * 0.7))
    place(x, 0.55 * click_burst(0.025, 11), 0.0)
    place(x, 0.45 * click_burst(0.03, 12, 0.8), 0.075)
    t = t_axis(0.08)
    place(x, 0.35 * np.sin(2 * np.pi * 140 * t) * env(t, 0.002, 0.02), 0.0)
    place(x, 0.22 * bell(note('B6'), 0.45, decay=0.12, bright=0.6), 0.09)
    place(x, 0.16 * bell(note('E7'), 0.4, decay=0.1, bright=0.5), 0.13)
    return finish(reverb(x, 0.14, 0.8), 0.7)


def s_like():
    """Like: burbuja que sube y una campanita (mi → si)."""
    x = np.zeros(int(SR * 0.6))
    place(x, blip(700, 1500, 0.09, 0.03), 0.0)
    place(x, 0.5 * bell(note('E6'), 0.4, decay=0.1), 0.03)
    place(x, 0.45 * bell(note('B6'), 0.45, decay=0.14), 0.09)
    return finish(reverb(x, 0.16), 0.6)


SOUNDS = {'open': s_open, 'close': s_close, 'select': s_select, 'back': s_back, 'page': s_page, 'hover': s_hover,
          'foto': s_foto, 'like': s_like}


def write_wav(path, x):
    data = (np.clip(x, -1, 1) * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    wav_dir = sys.argv[sys.argv.index('--wav') + 1] if '--wav' in sys.argv else None
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in SOUNDS.items():
            x = fn()
            wav = os.path.join(tmp, name + '.wav')
            write_wav(wav, x)
            if wav_dir:
                os.makedirs(wav_dir, exist_ok=True)
                write_wav(os.path.join(wav_dir, name + '.wav'), x)
            ogg = os.path.join(OUT_DIR, name + '.ogg')
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '6', ogg], check=True)
            print(f'{name}: {len(x) / SR:.2f} s → {os.path.relpath(ogg, HERE)}')


if __name__ == '__main__':
    main()
