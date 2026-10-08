"""Sonidos del TF Pad, sintetizados desde cero (originales, sin muestras de nadie).

    python3 tools/pad/build_sounds.py            # escribe src/main/resources/assets/tfclient/sounds/pad/*.ogg
    python3 tools/pad/build_sounds.py --wav DIR  # además, los .wav para escucharlos

Timbre (1.3.23): suave y redondo, sin campanas ni brillos metálicos. «Pops» de burbuja con el tono que se desliza,
pulsos de seno cálidos filtrados (como una tecla de piano eléctrico muy apagada), soplos de aire muy bajitos para
los cambios de pantalla y un tic de madera para las pestañas. Todo bajo de volumen y corto: acompaña sin molestar.
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

def lowpass(x, fc):
    """Paso bajo de un polo: quita lo agudo que pica."""
    a = 1 - np.exp(-2 * np.pi * fc / SR)
    y = np.zeros(len(x))
    acc = 0.0
    for i in range(len(x)):
        acc += a * (x[i] - acc)
        y[i] = acc
    return y


def soft(f, dur, decay, attack=0.006, warmth=0.18):
    """Pulso cálido: seno con un poco de segundo armónico (como un piano eléctrico muy apagado). Nada metálico."""
    t = t_axis(dur)
    s = np.sin(2 * np.pi * f * t) + warmth * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t / (decay * 0.4))
    return s * env(t, attack, decay)


def pop(f0, f1, dur, decay, attack=0.002):
    """Burbuja: un seno cuyo tono se desliza de f0 a f1 (sube o baja) y se apaga rápido."""
    t = t_axis(dur)
    f = f1 + (f0 - f1) * np.exp(-t / 0.018)
    phase = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(phase) * env(t, attack, decay)


def wood(f, dur=0.05, seed=21):
    """Tic de madera: un golpecito de ruido filtrado con una nota muy corta debajo."""
    rng = np.random.default_rng(seed)
    t = t_axis(dur)
    n = lowpass(rng.standard_normal(len(t)), 2600) * env(t, 0.0005, 0.004)
    return 0.6 * n / (np.max(np.abs(n)) + 1e-9) + np.sin(2 * np.pi * f * t) * env(t, 0.001, 0.012)


def s_open():
    """Encender: un acorde suave que crece (mi + si), un soplo de aire que sube y una burbuja al final."""
    x = np.zeros(int(SR * 0.9))
    place(x, 0.55 * soft(note('E4'), 0.7, 0.22, attack=0.035), 0.0)
    place(x, 0.45 * soft(note('B4'), 0.65, 0.2, attack=0.035), 0.03)
    place(x, 0.30 * soft(note('E5'), 0.55, 0.16, attack=0.03), 0.07)
    place(x, whoosh(0.35, 500, 2200, level=0.05, seed=3), 0.0)
    place(x, 0.35 * pop(620, 980, 0.12, 0.035), 0.16)
    return finish(lowpass(reverb(x, 0.16, 0.8), 5200), 0.42)


def s_close():
    """Apagar: soplo que baja y una burbuja grave que se va."""
    x = np.zeros(int(SR * 0.55))
    place(x, whoosh(0.28, 2000, 450, level=0.05, seed=5), 0.0)
    place(x, 0.5 * pop(700, 380, 0.16, 0.05), 0.03)
    place(x, 0.3 * soft(note('E4'), 0.4, 0.12, attack=0.01), 0.05)
    return finish(lowpass(reverb(x, 0.12, 0.7), 4200), 0.36)


def s_select():
    """Tocar un botón: burbuja que sube un poco, redonda y corta."""
    x = np.zeros(int(SR * 0.22))
    place(x, pop(540, 820, 0.12, 0.03), 0.0)
    place(x, 0.18 * soft(note('B5'), 0.12, 0.03, attack=0.004, warmth=0.0), 0.008)
    return finish(lowpass(x, 4200), 0.34)


def s_back():
    """Volver: la misma burbuja, pero bajando."""
    x = np.zeros(int(SR * 0.2))
    place(x, pop(620, 400, 0.12, 0.03), 0.0)
    return finish(lowpass(x, 3600), 0.3)


def s_page():
    """Cambiar de pantalla: un soplo de aire muy bajito y un pulso cálido (sin campanas)."""
    x = np.zeros(int(SR * 0.4))
    place(x, whoosh(0.2, 700, 2400, level=0.06, seed=7), 0.0)
    place(x, 0.4 * soft(note('G#4'), 0.3, 0.07, attack=0.008), 0.035)
    place(x, 0.3 * pop(480, 720, 0.1, 0.025), 0.05)
    return finish(lowpass(reverb(x, 0.1, 0.6), 4000), 0.3)


def s_tab():
    """Cambiar de pestaña: un tic de madera suave."""
    x = np.zeros(int(SR * 0.09))
    place(x, wood(1150), 0.0)
    return finish(lowpass(x, 5000), 0.26, 0.008)


def s_hover():
    """Pasar por encima: un tic muy suave."""
    x = np.zeros(int(SR * 0.06))
    place(x, wood(1700, 0.03, seed=9), 0.0)
    return finish(lowpass(x, 5200), 0.12, 0.006)


def click_burst(dur, seed, bright=1.0):
    """Chasquido mecánico: ruido muy corto con un pico agudo (el obturador)."""
    rng = np.random.default_rng(seed)
    t = t_axis(dur)
    n = rng.standard_normal(len(t))
    hp = np.concatenate([[0], np.diff(n)])
    return bright * hp * env(t, 0.0005, dur / 4)


def s_foto():
    """Foto: clic-clac del obturador y un golpecito grave (sin brillos)."""
    x = np.zeros(int(SR * 0.4))
    place(x, 0.55 * click_burst(0.025, 11), 0.0)
    place(x, 0.45 * click_burst(0.03, 12, 0.8), 0.075)
    t = t_axis(0.08)
    place(x, 0.4 * np.sin(2 * np.pi * 140 * t) * env(t, 0.002, 0.02), 0.0)
    return finish(lowpass(lowpass(reverb(x, 0.1, 0.6), 3800), 3800), 0.45)


def s_like():
    """Like: dos burbujas que suben, la segunda más aguda."""
    x = np.zeros(int(SR * 0.35))
    place(x, pop(520, 780, 0.12, 0.03), 0.0)
    place(x, 0.8 * pop(700, 1040, 0.14, 0.035), 0.07)
    return finish(lowpass(reverb(x, 0.1, 0.6), 4600), 0.36)


SOUNDS = {'open': s_open, 'close': s_close, 'select': s_select, 'back': s_back, 'page': s_page, 'tab': s_tab,
          'hover': s_hover, 'foto': s_foto, 'like': s_like}


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
