"""Los sonidos de las grietas y de la muerte de los jefes (1.3.86): sintetizados aquí, sin muestras de nadie.

    python3 tools/bosses/sonidos_grieta.py [--wav carpeta]

Saca OGG Vorbis mono (para que suenen en su sitio) en assets/tfclient/sounds/grieta/ y sounds/jefes/ (fuera de
sounds/bosses/, que el conversor borra). Necesita numpy, scipy y ffmpeg con libvorbis.

  grieta/ambiente     zumbido grave del vacío, en bucle (cerca de la grieta)
  grieta/golpe        al golpear el cristal: impacto grave y metal que resuena en una caverna
  grieta/despertar    los 3,5 s del despertar: un rugido que sube, cada vez más rápido
  grieta/estallido    sale el jefe: absorción al revés, boom grave, desgarro y cola que brilla
  grieta/lista        la grieta vuelve a estar lista: un gong grave
  jefes/muerte_crepita_1..3   los chasquidos de energía de la muerte épica (antes, explosiones de TNT)
  jefes/muerte_estallido      el estallido final (implosión, boom y cristales que se rompen)
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
from scipy import signal

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'sounds'))


def rng(seed):
    return np.random.default_rng(seed)


def t_of(dur):
    return np.arange(int(dur * SR)) / SR


def env_adsr(n, a, d, s, r, sustain=0.7):
    """Envolvente (en segundos) del largo n."""
    e = np.zeros(n)
    ia, idd, ir = int(a * SR), int(d * SR), int(r * SR)
    i = 0
    seg = min(ia, n)
    e[:seg] = np.linspace(0, 1, seg, endpoint=False) if seg else []
    i = seg
    seg = min(idd, n - i)
    e[i:i + seg] = np.linspace(1, sustain, seg, endpoint=False)
    i += seg
    rest = n - i - ir
    if rest > 0:
        e[i:i + rest] = sustain
        i += rest
    seg = n - i
    if seg > 0:
        e[i:] = np.linspace(e[i - 1] if i else sustain, 0, seg)
    return e


def exp_env(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def sweep(f0, f1, n, curve=1.0):
    """Frecuencia instantánea de f0 a f1 (curva >1: tarda en moverse) → fase."""
    k = np.linspace(0, 1, n) ** curve
    f = f0 * (f1 / f0) ** k
    return 2 * np.pi * np.cumsum(f) / SR


def saw(phase, harmonics=24):
    out = np.zeros_like(phase)
    for h in range(1, harmonics + 1):
        out += np.sin(phase * h) / h
    return out * 0.6


def lp(x, fc, order=4):
    b, a = signal.butter(order, min(fc, SR / 2 - 100) / (SR / 2), 'low')
    return signal.lfilter(b, a, x)


def hp(x, fc, order=2):
    b, a = signal.butter(order, fc / (SR / 2), 'high')
    return signal.lfilter(b, a, x)


def bp(x, f0, f1, order=2):
    b, a = signal.butter(order, [f0 / (SR / 2), min(f1, SR / 2 - 100) / (SR / 2)], 'band')
    return signal.lfilter(b, a, x)


def lp_sweep(x, f0, f1, blocks=64):
    """Paso bajo que se abre de f0 a f1 (por bloques, con estado)."""
    out = np.zeros_like(x)
    n = len(x)
    edges = np.linspace(0, n, blocks + 1).astype(int)
    zi = None
    for i in range(blocks):
        fc = f0 * (f1 / f0) ** (i / max(1, blocks - 1))
        b, a = signal.butter(2, min(fc, SR / 2 - 200) / (SR / 2), 'low')
        if zi is None:
            zi = signal.lfilter_zi(b, a) * 0
        seg = x[edges[i]:edges[i + 1]]
        out[edges[i]:edges[i + 1]], zi = signal.lfilter(b, a, seg, zi=zi)
    return out


def reverb(x, seconds=3.0, damp=3000, wet=0.5, seed=1, predelay=0.02):
    """Reverb de caverna: convolución con ruido que se apaga (y se oscurece) con el tiempo."""
    n = int(seconds * SR)
    r = rng(seed)
    ir = r.standard_normal(n) * np.exp(-np.arange(n) / (seconds / 6.9 * SR))
    # se oscurece: mezcla del ruido con su versión filtrada según avanza
    dark = lp(ir, damp * 0.35)
    k = np.linspace(0, 1, n) ** 0.6
    ir = ir * (1 - k) + dark * k * 2.0
    ir = np.concatenate([np.zeros(int(predelay * SR)), ir])
    ir /= np.sqrt(np.sum(ir ** 2))
    y = signal.fftconvolve(x, ir)[: len(x) + len(ir) - 1]
    dry = np.concatenate([x, np.zeros(len(y) - len(x))])
    return dry * (1 - wet) + y * wet * 1.6


def metal(n, base, partials, tau, seed=3, detune=0.004):
    """Parciales inarmónicos que se apagan (campana, gong, cristal)."""
    r = rng(seed)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for i, (ratio, amp) in enumerate(partials):
        f = base * ratio * (1 + r.uniform(-detune, detune))
        out += amp * np.sin(2 * np.pi * f * t + r.uniform(0, 6.28)) * np.exp(-t / (tau / (1 + i * 0.25)))
    return out


def norm(x, peak=0.89):
    m = np.max(np.abs(x)) or 1
    return x / m * peak


def fade(x, fin=0.005, fout=0.05):
    n = len(x)
    a, b = int(fin * SR), int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def soft(x, drive=1.5):
    return np.tanh(x * drive) / np.tanh(drive)


# ---------------------------------------------------------------------------------------------- sonidos

def ambiente():
    """Zumbido del vacío en bucle (12 s, sin costura: todo es periódico en 12 s)."""
    dur = 12.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    # Frecuencias que caben un número entero de veces en 12 s (el bucle no salta)
    def fq(f):
        return round(f * dur) / dur
    sub = 0.55 * np.sin(2 * np.pi * fq(41.0) * t) + 0.35 * np.sin(2 * np.pi * fq(41.5) * t)
    pad = np.zeros(n)
    for f, a in ((55, 0.5), (55.25, 0.5), (82.5, 0.32), (110.5, 0.18), (65.33, 0.22)):
        pad += a * saw(2 * np.pi * fq(f) * t, 16)
    lfo = 0.5 + 0.5 * np.sin(2 * np.pi * t / dur * 2)
    pad = lp(np.concatenate([pad, pad]), 700)[n:]  # filtrado ya «caliente» (sin el arranque del filtro)
    pad *= 0.55 + 0.45 * lfo
    r = rng(5)
    air = r.standard_normal(n * 2)
    air = bp(air, 300, 1400)[n:]
    air *= 0.12 * (0.5 + 0.5 * np.sin(2 * np.pi * t / dur * 3 + 1))
    # brillo lejano: unos parciales altos que suben y bajan
    shimmer = np.zeros(n)
    for f, ph in ((fq(660), 0), (fq(987.5), 2), (fq(1318), 4)):
        shimmer += np.sin(2 * np.pi * f * t) * (0.5 + 0.5 * np.sin(2 * np.pi * t / dur * 1 + ph)) ** 3
    # armónicos del grave (que se oiga también en altavoces pequeños)
    growl = hp(np.concatenate([soft(sub * 3, 3)] * 2), 90)[n:] * 0.35
    x = sub + growl + pad * 1.3 + air * 1.5 + shimmer * 0.06
    # reverb «en bucle»: se convoluciona tres vueltas seguidas y se queda con la del medio
    y = reverb(np.concatenate([x, x, x]), 3.5, 2000, 0.35, seed=9)[n:2 * n]
    return norm(y, 0.8)


def golpe():
    dur = 4.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    thump = np.sin(sweep(95, 34, n, 0.35)) * exp_env(n, 0.45)
    r = rng(11)
    crack = hp(r.standard_normal(n), 1800) * exp_env(n, 0.018) * 0.7
    body = lp(r.standard_normal(n), 1500) * exp_env(n, 0.12) * 0.8
    bell = metal(n, 98, [(1, 1), (2.76, 0.6), (5.40, 0.4), (8.93, 0.25), (13.3, 0.15), (1.5, 0.35)], 2.2, seed=12)
    x = thump * 1.1 + crack + body + bell * 0.85
    return fade(norm(reverb(soft(x, 1.3), 3.8, 2400, 0.55, seed=13)), 0.001, 0.4)


def despertar():
    """3,5 s que suben (de 40 a 160 Hz), con trémolo cada vez más rápido y un coro que se abre."""
    dur = 3.6
    n = int(dur * SR)
    t = np.arange(n) / SR
    k = t / dur
    x = np.zeros(n)
    for det in (1.0, 1.007, 0.993, 2.0):
        x += saw(sweep(40 * det, 165 * det, n, 1.6), 20) * (0.6 if det != 2.0 else 0.25)
    x = lp_sweep(x, 180, 3200)
    trem_rate = 4 + 18 * k ** 2
    trem = 0.6 + 0.4 * np.sin(2 * np.pi * np.cumsum(trem_rate) / SR)
    r = rng(21)
    noise = lp_sweep(r.standard_normal(n), 300, 7000) * 0.35 * k ** 1.5
    # «coro»: vocales graves (formantes) que se abren
    choir = np.zeros(n)
    for f, a in ((110, 1), (110.6, 1), (165, 0.6), (220.4, 0.4)):
        choir += saw(sweep(f, f * 1.5, n, 1.4), 14) * a
    choir = bp(choir, 400, 1200) * 0.5 * k
    sub = np.sin(sweep(30, 55, n, 1.2)) * 0.8
    x = (x * trem + noise + choir + sub) * np.clip(k * 1.2, 0.05, 1) ** 1.1
    y = reverb(soft(x, 1.4), 2.5, 3000, 0.35, seed=22)[: int(4.0 * SR)]
    return fade(norm(y), 0.05, 0.25)


def estallido():
    dur = 6.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    r = rng(31)
    # 0,45 s de absorción al revés (ruido que sube y se cierra de golpe)
    pre = int(0.45 * SR)
    suck = hp(r.standard_normal(pre), 500) * np.linspace(0, 1, pre) ** 3
    suck = lp_sweep(suck, 1200, 9000, 16) * 0.8
    m = n - pre
    boom = np.sin(sweep(70, 24, m, 0.3)) * exp_env(m, 0.9) * 1.3
    crack = hp(r.standard_normal(m), 1500) * exp_env(m, 0.04)
    roar = soft(lp(r.standard_normal(m), 1600) * exp_env(m, 0.7) * 2.5, 2.0) * 0.6
    growl = lp(soft(saw(sweep(58, 31, m, 0.4), 30) * 2.5, 2.5), 2500) * exp_env(m, 0.8) * 0.55
    tear = bp(r.standard_normal(m), 900, 4000) * (exp_env(m, 0.25) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * t[:m]))) * 0.5
    shards = metal(m, 523, [(1, 1), (1.47, 0.8), (2.31, 0.7), (3.17, 0.5), (4.62, 0.4), (6.08, 0.3)], 2.6, seed=33,
                   detune=0.02) * 0.18
    after = np.concatenate([np.zeros(int(0.15 * SR)), shards])[:m]
    x = np.concatenate([suck, boom + crack + roar + growl + tear + after])
    y = reverb(soft(x, 1.6), 5.0, 2200, 0.5, seed=34)[: int(8.0 * SR)]
    return fade(norm(y), 0.01, 0.8)


def lista():
    dur = 5.0
    n = int(dur * SR)
    gong = metal(n, 73.4, [(1, 1), (1.52, 0.55), (2.18, 0.5), (2.94, 0.3), (4.07, 0.22), (5.3, 0.12)], 3.4, seed=41)
    swell = env_adsr(n, 0.08, 0.3, 0.0, 0.1, 1.0)
    shimmer = metal(n, 880, [(1, 1), (1.5, 0.7), (2.01, 0.5)], 1.6, seed=42) * 0.1
    gong2 = metal(n, 146.8, [(1, 1), (1.52, 0.5), (2.18, 0.4)], 2.6, seed=44) * 0.5
    x = (gong + gong2) * swell + shimmer
    return fade(norm(reverb(x, 4.0, 2600, 0.45, seed=43)), 0.01, 0.6)


def crepita(seed):
    """Chasquido de energía: un golpe seco, un zumbido violeta que cae y crepitar; corto y sin TNT."""
    dur = 1.6
    n = int(dur * SR)
    t = np.arange(n) / SR
    r = rng(seed)
    thump = np.sin(sweep(r.uniform(85, 110), 40, n, 0.3)) * exp_env(n, 0.12)
    zap = np.sin(sweep(r.uniform(1300, 1800), r.uniform(180, 260), n, 0.5)) * exp_env(n, 0.09) * 0.7
    zap += saw(sweep(r.uniform(650, 900), 120, n, 0.5), 8) * exp_env(n, 0.07) * 0.45
    grains = np.zeros(n)
    for _ in range(int(r.uniform(18, 30))):
        at = int(r.exponential(0.12) * SR)
        if at >= n - 600:
            continue
        g = r.standard_normal(500) * np.exp(-np.arange(500) / 60)
        grains[at:at + 500] += g * r.uniform(0.3, 1.0)
    grains = bp(grains, 1800, 9000) * 0.8
    x = thump * 1.1 + zap + grains
    return fade(norm(reverb(soft(x, 1.5), 1.6, 3500, 0.3, seed=seed + 1)[: int(2.2 * SR)]), 0.001, 0.3)


def muerte_estallido():
    dur = 7.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    r = rng(61)
    pre = int(0.9 * SR)
    # implosión: todo sube y se aspira hacia dentro
    rise = saw(sweep(60, 420, pre, 2.0), 18) * np.linspace(0, 1, pre) ** 2.5 * 0.5
    suck = hp(r.standard_normal(pre), 800) * np.linspace(0, 1, pre) ** 4 * 0.7
    implode = lp_sweep(rise + suck, 400, 12000, 24)
    m = n - pre
    boom = np.sin(sweep(80, 22, m, 0.28)) * exp_env(m, 1.1) * 1.4
    boom2 = np.sin(sweep(46, 19, m, 0.3)) * exp_env(m, 1.6) * 0.8
    crack = hp(r.standard_normal(m), 1200) * exp_env(m, 0.05) * 1.1
    roar = soft(lp(r.standard_normal(m), 1800) * exp_env(m, 0.9) * 3, 2.0) * 0.55
    growl = lp(soft(saw(sweep(52, 27, m, 0.35), 30) * 2.5, 2.5), 2600) * exp_env(m, 1.0) * 0.6
    shatter = metal(m, 740, [(1, 1), (1.34, 0.9), (1.93, 0.8), (2.71, 0.7), (3.55, 0.5), (4.9, 0.4), (6.6, 0.3)], 3.0,
                    seed=63, detune=0.03) * 0.22
    sparkle = bp(r.standard_normal(m), 5000, 14000) * exp_env(m, 1.4) * 0.18 * (0.5 + 0.5 * np.sin(2 * np.pi * 7 * t[:m]))
    x = np.concatenate([implode, boom + boom2 + crack + roar + growl + shatter + sparkle])
    y = reverb(soft(x, 1.7), 6.0, 2000, 0.55, seed=64)[: int(10.0 * SR)]
    return fade(norm(y), 0.01, 1.0)


SOUNDS = {
    'grieta/ambiente': ambiente,
    'grieta/golpe': golpe,
    'grieta/despertar': despertar,
    'grieta/estallido': estallido,
    'grieta/lista': lista,
    'jefes/muerte_crepita_1': lambda: crepita(51),
    'jefes/muerte_crepita_2': lambda: crepita(52),
    'jefes/muerte_crepita_3': lambda: crepita(53),
    'jefes/muerte_estallido': muerte_estallido,
}


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    wav_dir = sys.argv[sys.argv.index('--wav') + 1] if '--wav' in sys.argv else None
    tmp = tempfile.mkdtemp()
    for name, fn in SOUNDS.items():
        x = fn()
        # sin el sub-grave que nadie oye (<25 Hz) y sin la cola muda del final (por debajo de -60 dB)
        x = norm(hp(x, 25, 2), 0.89)
        loud = np.nonzero(np.abs(x) > 0.89 * 10 ** (-60 / 20))[0]
        if name != 'grieta/ambiente' and len(loud):
            x = fade(x[: min(len(x), loud[-1] + int(0.05 * SR))].copy(), 0, 0.15)
        wav = os.path.join(wav_dir or tmp, name.replace('/', '_') + '.wav')
        os.makedirs(os.path.dirname(wav), exist_ok=True)
        write_wav(wav, x)
        out = os.path.join(RES, name + '.ogg')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', '-ac', '1', out],
                       check=True)
        print(f'{name}: {len(x) / SR:.1f} s, {os.path.getsize(out) // 1024} KB')


if __name__ == '__main__':
    main()
