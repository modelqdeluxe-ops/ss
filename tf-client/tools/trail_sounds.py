"""Sonidos del tajo de las estelas de arma (Efectos › COMBATE, 1.3.70), sintetizados desde cero (sin muestras de nadie).

    python3 tools/trail_sounds.py            # escribe src/main/resources/assets/tfclient/sounds/combate/*.ogg
    python3 tools/trail_sounds.py --wav DIR  # además, los .wav para escucharlos

Un «zas» de aire: ruido con un paso banda que baja deprisa de agudo a grave (el filo cortando el aire), un cuerpo grave
(el peso del arma) y una cola muy corta. tajo1-3 son el golpe normal (el juego elige uno al azar) y tajo_fuerte el
crítico: más largo, más grave y con más cuerpo. Usa las piezas de tools/pad/build_sounds.py (numpy y ffmpeg).
"""
import os
import subprocess
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'pad'))
import build_sounds as bs  # noqa: E402

SR = bs.SR
OUT_DIR = os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'sounds', 'combate'))


def svf_sweep(noise, fc, q=0.55):
    """Paso banda de estado variable con la frecuencia fc[i] que se mueve en cada muestra."""
    out = np.zeros(len(noise))
    lp = bp = 0.0
    for i in range(len(noise)):
        g = 2 * np.sin(np.pi * min(fc[i], SR / 6) / SR)
        hp = noise[i] - lp - q * bp
        bp += g * hp
        lp += g * bp
        out[i] = bp
    return out


def swish(dur, f_hi, f_lo, seed, peak=0.32, body=0.45, tail=0.35):
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    # la frecuencia baja deprisa al principio y se para abajo (curva exponencial)
    k = np.clip(t / (dur * 0.85), 0, 1)
    fc = f_hi * (f_lo / f_hi) ** (k ** 0.7)
    air = svf_sweep(rng.standard_normal(n), fc)
    air /= np.max(np.abs(air)) + 1e-9
    # cuerpo: ruido grave, como el peso del arma
    low = bs.lowpass(bs.lowpass(rng.standard_normal(n), 420), 420)
    low /= np.max(np.abs(low)) + 1e-9
    # envolvente: sube hasta el pico (cuando el filo pasa delante) y cae
    pk = peak * dur
    rise = np.clip(t / pk, 0, 1) ** 1.6
    fall = np.exp(-np.maximum(t - pk, 0) / (dur * tail))
    shape = rise * fall
    x = (air + body * low) * shape
    x = bs.lowpass(bs.lowpass(x, 3200), 3200)  # sin siseo: el aire es grave
    return bs.finish(bs.fade_end(x, 25), 0.6, tail=0.015)


def s_tajo1():
    return swish(0.21, 1900, 520, seed=3)


def s_tajo2():
    return swish(0.23, 1700, 470, seed=7, peak=0.36)


def s_tajo3():
    return swish(0.19, 2100, 600, seed=11, peak=0.28)


def s_tajo_fuerte():
    x = swish(0.32, 1500, 330, seed=5, peak=0.40, body=0.8, tail=0.45)
    y = bs.reverb(x, mix=0.10, size=0.6)[: int(0.46 * SR)]
    return bs.finish(bs.fade_end(y, 60), 0.7, tail=0.03)


SOUNDS = {'tajo1': s_tajo1, 'tajo2': s_tajo2, 'tajo3': s_tajo3, 'tajo_fuerte': s_tajo_fuerte}


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    wav_dir = sys.argv[sys.argv.index('--wav') + 1] if '--wav' in sys.argv else None
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in SOUNDS.items():
            x = fn()
            wav = os.path.join(tmp, name + '.wav')
            bs.write_wav(wav, x)
            if wav_dir:
                os.makedirs(wav_dir, exist_ok=True)
                bs.write_wav(os.path.join(wav_dir, name + '.wav'), x)
            ogg = os.path.join(OUT_DIR, name + '.ogg')
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '6', ogg], check=True)
            print(f'{name}: {len(x) / SR:.2f} s → {os.path.relpath(ogg, HERE)}')


if __name__ == '__main__':
    main()
