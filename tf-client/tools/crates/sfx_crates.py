"""Los efectos de sonido de las crates (TF Client 1.3.113).

El dueño: «el sonido en general de las crates, el de la llave, la ruleta, son horribles pero horribles; no te estás
tomando el tiempo; sonidos por rarezas, al abrir la crate, la llave, todo eso no me gusta». Hasta la 1.3.112 eran
sonidos del juego (la mesa de encantamientos, la brújula imantada, el faro, el botón de madera, el de subir de nivel…).

Ahora son propios, hechos aquí con cuidado: lo mecánico, con grabaciones de verdad (la llave, el cerrojo, la madera
del cofre: `muestras/`, de los paquetes de Kenney, dominio público CC0) y lo mágico, sintetizado (campanas de cristal,
celesta, arpa de cuerda pulsada, coro, metales, golpes graves, soplos), todo en Re mayor, con su sala (reverberación
por convolución, estéreo) y el volumen medido (sonoridad parecida entre ellos y picos a −1 dB).

    python3 -I tools/crates/sfx_crates.py [--wav carpeta] [nombre ...]

La cinemática (estéreo, sin distancia; los pide CrateCinematicScreen como tfclient:crates.<nombre>):

  llave_aparece  la llave sale de un remolino de luz: soplo que sube y celesta que sube
  llave_lanza    vuela dando vueltas (1,3 s): el aire que corta, girando, y una estela de brillos
  llave_entra    golpea el cofre y entra: la madera, el roce del metal y el clac de la cerradura
  llave_gira     trinquete, el golpe del cierre y una campana de cristal
  runas          el anillo de runas se enciende: zumbido que sube y cristales
  tiembla        la crate tiembla (0,9 s, hasta que se abre): la madera traquetea y todo sube
  apertura       ¡se abre!: golpe grave, la tapa, el acorde que florece y una lluvia de destellos
  tic            cada premio que pasa por la ruleta (el mod le sube el tono al frenar)
  parada         la ruleta se para: un toque de madera y una campana
  premio1..5     el premio, uno por rareza: común, rara, épica, legendaria y mítica (cada uno más grande)

La crate del mundo (mono: suena en su sitio, para los de alrededor; CrateSfx): mundo_llave, mundo_carga, mundo_sube,
mundo_pico, mundo_abre, mundo_tic, mundo_premio1..5 y mundo_cierra.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.signal import butter, fftconvolve, istft, lfilter, sosfilt, stft

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'sounds', 'crates'))
MUESTRAS = os.path.join(HERE, 'muestras')
SR = 44100
RNG = np.random.default_rng(1131)

# Re mayor
D2, A2, D3, A3, D4, E4, Fs4, A4, B4 = 73.42, 110.0, 146.83, 220.0, 293.66, 329.63, 369.99, 440.0, 493.88
D5, E5, Fs5, G5, A5, B5 = 587.33, 659.26, 739.99, 783.99, 880.0, 987.77
D6, E6, Fs6, G6, A6, B6, D7, E7, Fs7, A7 = 1174.66, 1318.51, 1479.98, 1567.98, 1760.0, 1975.53, 2349.32, 2637.02, 2959.96, 3520.0
PENTA_HI = [D6, E6, Fs6, A6, B6, D7, E7, Fs7, A7]


# ------------------------------------------------------------------------------------------------ utilidades
def tt(dur):
    return np.arange(int(dur * SR)) / SR


def silence(dur):
    return np.zeros((int(dur * SR), 2))


def st(x, pan=0.0):
    """Mono → estéreo con panorama de potencia constante (pan de −1 a 1)."""
    a = (pan + 1) * np.pi / 4
    return np.stack([x * np.cos(a), x * np.sin(a)], axis=1)


def tailfade(x, frac=0.3, min_sec=0.01):
    """El final, apagándose en coseno (sin cortar de golpe: cada corte es un chasquido)."""
    n = len(x)
    k = max(1, min(n, max(int(min_sec * SR), int(n * frac))))
    e = np.ones(n)
    e[n - k:] = 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, k))
    return x * (e[:, None] if x.ndim == 2 else e)


def place(dst, src, at, gain=1.0):
    i = int(at * SR)
    if i >= len(dst):
        return dst
    n = min(len(src), len(dst) - i)
    piece = src[:n]
    if n < len(src):
        piece = tailfade(piece, 0.0, 0.03)
    dst[i:i + n] += piece * gain
    return dst


def sos(kind, f, order=2):
    nyq = SR / 2
    if kind == 'bp':
        return butter(order, [max(10, f[0]) / nyq, min(f[1], nyq * 0.98) / nyq], btype='band', output='sos')
    return butter(order, min(f, nyq * 0.98) / nyq, btype=kind, output='sos')


def lp(x, f, order=2):
    return sosfilt(sos('low', f, order), x, axis=0)


def hp(x, f, order=2):
    return sosfilt(sos('high', f, order), x, axis=0)


def bp(x, lo, hi, order=2):
    return sosfilt(sos('bp', (lo, hi), order), x, axis=0)


def env_ad(n, attack, decay_tau, sr=SR):
    t = np.arange(n) / sr
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * a * (3 - 2 * a) * np.exp(-np.maximum(0, t - attack) / decay_tau)


def fade(x, fin=0.0, fout=0.05):
    n = len(x)
    e = np.ones(n)
    if fin > 0:
        k = min(n, int(fin * SR))
        e[:k] = np.linspace(0, 1, k) ** 2
    if fout > 0:
        k = min(n, int(fout * SR))
        e[n - k:] *= np.linspace(1, 0, k) ** 2
    return x * (e[:, None] if x.ndim == 2 else e)


def load(name, ratio=1.0):
    """Una muestra (mono, a SR), a otra velocidad (ratio > 1: más aguda y corta)."""
    path = os.path.join(MUESTRAS, name)
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', path, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'],
                         check=True, capture_output=True).stdout
    x = np.frombuffer(raw, dtype=np.float32).astype(np.float64)
    x = x - np.mean(x[:200]) if len(x) > 200 else x
    # sin el silencio del principio
    k = np.argmax(np.abs(x) > 0.02 * np.max(np.abs(x)))
    x = x[max(0, k - 20):]
    if ratio != 1.0:
        idx = np.arange(0, len(x) - 1, ratio)
        x = np.interp(idx, np.arange(len(x)), x)
    return tailfade(x / max(1e-9, np.max(np.abs(x))), 0.05)


# ------------------------------------------------------------------------------------------------ la sala
def hall_ir(rt=1.8, pre=0.014, bright=0.5, seed=7, dur=None):
    """Respuesta de una sala de piedra (el santuario): reflexiones tempranas y una cola de ruido que se apaga en `rt`
    segundos (los agudos antes que los graves), distinta en cada oído."""
    rnd = np.random.default_rng(seed)
    dur = dur or rt * 1.3
    n = int(dur * SR)
    t = np.arange(n) / SR
    ir = np.zeros((n, 2))
    for ch in range(2):
        noise = rnd.standard_normal(n)
        lo_part = lp(noise, 1800)
        hi_part = noise - lo_part
        dec = np.exp(-6.9 * t / rt)
        dec_hi = np.exp(-6.9 * t / (rt * (0.35 + 0.4 * bright)))
        tail = lo_part * dec + hi_part * dec_hi * (0.5 + bright * 0.5)
        tail *= np.clip((t - pre) / 0.03, 0, 1)
        er = np.zeros(n)
        for _ in range(14):
            d = pre + rnd.uniform(0.003, 0.07)
            er[int(d * SR)] += rnd.uniform(-1, 1) * np.exp(-d * 18)
        ir[:, ch] = tail * 0.22 + lp(er, 6000) * 0.9
    return ir / np.sqrt(np.sum(ir ** 2) / 2)


def room(x, wet=0.28, rt=1.8, bright=0.5, seed=7, pre=0.014):
    """La señal en la sala (estéreo): lo directo y la reverberación por convolución."""
    if x.ndim == 1:
        x = st(x)
    ir = hall_ir(rt, pre, bright, seed)
    out = np.zeros((len(x) + len(ir) - 1, 2))
    for ch in range(2):
        out[:, ch] = fftconvolve(x[:, 0] * 0.5 + x[:, 1] * 0.5, ir[:, ch])
    dry = np.zeros_like(out)
    dry[:len(x)] = x
    wet_sig = hp(out, 120) * wet * 0.35
    return dry * (1 - wet * 0.4) + wet_sig


# ------------------------------------------------------------------------------------------------ instrumentos
def mallet(n, bright=1.0):
    """El golpe de la baqueta: un clic de ruido muy corto."""
    k = min(n, int(0.004 * SR))
    c = np.zeros(n)
    c[:k] = RNG.standard_normal(k) * np.linspace(1, 0, k) ** 2
    return hp(c, 2500 + 2000 * bright)


def celesta(f, dur=1.6, vel=1.0, bright=1.0):
    """Celesta / glockenspiel: parciales de una lámina (1; 2,76; 5,40; 8,93), los agudos se apagan antes."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for r, a, d in ((1.0, 1.0, 0.9), (2.0, 0.18, 0.5), (2.76, 0.28 * bright, 0.32), (5.40, 0.10 * bright, 0.14),
                    (8.93, 0.04 * bright, 0.07)):
        fr = f * r
        if fr > SR * 0.45:
            continue
        out += a * np.sin(2 * np.pi * fr * t + RNG.uniform(0, 6.28)) * np.exp(-t / (d * dur / 1.6))
    out *= np.clip(t / 0.002, 0, 1)
    out += mallet(n, bright) * 0.25 * bright
    return tailfade(out * vel, 0.3)


def crystal(f, dur=3.0, vel=1.0):
    """Campana de cristal (como una copa): parciales 1; 2,32; 4,25; 6,63, cada uno en dos que laten despacio."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for r, a, d in ((1.0, 1.0, 1.0), (2.32, 0.42, 0.55), (4.25, 0.18, 0.3), (6.63, 0.07, 0.16)):
        fr = f * r
        if fr > SR * 0.45:
            continue
        beat = 0.6 + 1.4 * r
        out += a * (np.sin(2 * np.pi * fr * t) + 0.6 * np.sin(2 * np.pi * (fr + beat) * t + 1.0)) * np.exp(-t / (d * dur / 2.2))
    out *= np.clip(t / 0.003, 0, 1)
    return tailfade(out * vel / 1.6, 0.3)


def harp(f, dur=1.8, vel=1.0, bright=0.6):
    """Arpa (cuerda pulsada, Karplus-Strong con lfilter)."""
    n = int(dur * SR)
    p = SR / f
    N = int(p)
    frac = p - N
    burst = lp(RNG.standard_normal(N + 2), 1500 + 6000 * bright)
    x = np.zeros(n)
    x[:len(burst)] = burst
    g = 0.996 ** (440 / max(f, 60))
    # (la media de dos muestras: el filtro que apaga los agudos de la cuerda; el tono, con el retraso entero)
    a = np.zeros(N + 2)
    a[0] = 1
    a[N] = -g * 0.5
    a[N + 1] = -g * 0.5
    del frac
    y = lfilter([1.0], a, x)
    y = y / max(1e-9, np.max(np.abs(y)))
    return tailfade(y * vel * np.exp(-np.arange(n) / SR / (dur * 0.6)), 0.3)


def saw_additive(freq_t, n_harm=40, tilt=1.0, bright_t=None):
    """Diente de sierra limitado en banda (suma de senos) con frecuencia que cambia en el tiempo (vibrato) y un brillo que
    puede cambiar (bright_t: cuántos armónicos pasan, de 1 a n_harm)."""
    phase = 2 * np.pi * np.cumsum(freq_t) / SR
    out = np.zeros(len(freq_t))
    f0 = float(np.mean(freq_t))
    for k in range(1, n_harm + 1):
        if k * f0 > SR * 0.42:
            break
        amp = 1.0 / k ** tilt
        if bright_t is not None:
            amp = amp * np.exp(-np.maximum(0, k - 1) / np.maximum(0.3, bright_t))
        out += amp * np.sin(k * phase)
    return out


def pad(freqs, dur, attack=0.4, release=0.8, cutoff=2400, voices=3, detune=0.006, vib=0.0, gain=1.0, seed=3):
    """Colchón de cuerdas (sierras desafinadas un poco, con filtro), estéreo."""
    rnd = np.random.default_rng(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros((n, 2))
    for f in freqs:
        for v in range(voices):
            dt = 1 + detune * (v - (voices - 1) / 2)
            ft = f * dt * (1 + vib * np.sin(2 * np.pi * rnd.uniform(4.5, 5.5) * t + rnd.uniform(0, 6)))
            s = saw_additive(ft, n_harm=30, tilt=1.1)
            out += st(s, pan=rnd.uniform(-0.6, 0.6))
    out = lp(out, cutoff, 2)
    e = np.clip(t / attack, 0, 1) ** 1.5 * np.clip((dur - t) / release, 0, 1)
    return out * e[:, None] * gain / (len(freqs) * voices)


def choir(freqs, dur, attack=0.5, release=1.0, vowel='a', gain=1.0, seed=5):
    """Coro («aah»): sierras con vibrato por tres formantes."""
    form = {'a': ((800, 1.0, 80), (1150, 0.5, 90), (2900, 0.12, 120)), 'o': ((450, 1.0, 70), (800, 0.4, 80), (2830, 0.08, 100))}[vowel]
    rnd = np.random.default_rng(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros((n, 2))
    for f in freqs:
        for v in range(4):
            ft = f * (1 + 0.004 * (v - 1.5)) * (1 + 0.006 * np.sin(2 * np.pi * rnd.uniform(4.8, 5.8) * t + rnd.uniform(0, 6)))
            s = saw_additive(ft, n_harm=40, tilt=1.0)
            y = np.zeros(n)
            for fc, a, bw in form:
                y += a * bp(s, fc - bw * 2, fc + bw * 2, 2)
            out += st(y, pan=rnd.uniform(-0.7, 0.7))
    e = np.clip(t / attack, 0, 1) ** 2 * np.clip((dur - t) / release, 0, 1)
    out = out * e[:, None]
    return out / max(1e-9, np.max(np.abs(out))) * gain


def brass(freqs, dur, swell=0.08, hold=0.5, release=0.9, gain=1.0, seed=9):
    """Metales (trompas y trompetas): el brillo se abre con el soplido y se calma."""
    rnd = np.random.default_rng(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    bright = 2 + 16 * (np.clip(t / swell, 0, 1) * np.exp(-np.maximum(0, t - swell) / 0.35)) + 3
    out = np.zeros((n, 2))
    for f in freqs:
        for v in range(2):
            ft = f * (1 + 0.003 * (v - 0.5)) * (1 + 0.003 * np.sin(2 * np.pi * 5.2 * t + rnd.uniform(0, 6)))
            s = saw_additive(ft, n_harm=36, tilt=0.9, bright_t=bright)
            out += st(s, pan=rnd.uniform(-0.4, 0.4))
    e = np.clip(t / (swell * 0.7), 0, 1) * np.where(t < hold, 1.0, np.exp(-(t - hold) / release))
    out = tailfade(out * e[:, None], 0.25)
    return out / max(1e-9, np.max(np.abs(out))) * gain


def boom(f0=72, f1=38, dur=1.8, gain=1.0, punch=1.0):
    """Golpe grave (como un timbal muy hondo, sin tambor de batería): seno que cae de tono y un golpe de ruido."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f1 + (f0 - f1) * np.exp(-t / 0.12)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR)
    s = np.tanh(s * 1.6) / np.tanh(1.6)
    e = np.exp(-t / (dur * 0.35)) * np.clip(t / 0.004, 0, 1)
    thump = lp(RNG.standard_normal(n), 260) * np.exp(-t / 0.05) * 1.5 * punch
    return tailfade((s * e + thump) * gain, 0.3)


def whoosh(dur, f0, f1, width=0.6, gain=1.0, shape='swell', seed=11):
    """Soplo: ruido con una banda que va de f0 a f1 (en el espectro, con STFT)."""
    rnd = np.random.default_rng(seed)
    n = int(dur * SR)
    x = rnd.standard_normal(n)
    f, tseg, Z = stft(x, SR, nperseg=1024)
    k = np.linspace(0, 1, Z.shape[1])
    fc = f0 * (f1 / f0) ** k
    lf = np.log(np.maximum(f, 1))[:, None]
    mask = np.exp(-((lf - np.log(fc)[None, :]) ** 2) / (2 * width ** 2))
    _, y = istft(Z * mask, SR, nperseg=1024)
    y = y[:n]
    t = np.arange(len(y)) / SR
    if shape == 'swell':
        e = (t / dur) ** 2 * np.clip((dur - t) / 0.06, 0, 1)
    elif shape == 'burst':
        e = np.clip(t / 0.01, 0, 1) * np.exp(-t / (dur * 0.3))
    else:  # arco
        e = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.5
    y = tailfade(y * e, 0.1)
    return y / max(1e-9, np.max(np.abs(y))) * gain


def sparkles(dur, count, seed, lo=None, hi=None, start=0.0, spread=1.0, gain=1.0, fall=False):
    """Destellos: notas muy agudas y cortas de celesta, por todo el estéreo (fall: cada vez más graves y más flojos)."""
    rnd = np.random.default_rng(seed)
    out = silence(dur)
    notes = [f for f in PENTA_HI + [f * 2 for f in PENTA_HI[:4]] if (lo is None or f >= lo) and (hi is None or f <= hi)]
    for i in range(count):
        at = start + rnd.uniform(0, spread) if not fall else start + spread * (i / count) ** 0.8 + rnd.uniform(0, 0.03)
        f = notes[rnd.integers(len(notes))] if not fall else notes[max(0, len(notes) - 1 - int(i * len(notes) / count))]
        v = rnd.uniform(0.25, 0.6) * (1 - 0.6 * i / count if fall else 1)
        s = celesta(f, dur=rnd.uniform(0.3, 0.7), vel=v, bright=0.7)
        place(out, st(s, pan=rnd.uniform(-0.85, 0.85)), at)
    return out * gain


def metal_ring(dur=0.5, base=2300, gain=1.0):
    """El canto del metal de la llave: parciales inarmónicos cortos."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for r, a, d in ((1.0, 1.0, 0.18), (1.62, 0.6, 0.12), (2.57, 0.35, 0.08), (3.9, 0.2, 0.05)):
        out += a * np.sin(2 * np.pi * base * r * t) * np.exp(-t / d)
    return tailfade(out * gain * np.clip(t / 0.001, 0, 1), 0.2)


# ------------------------------------------------------------------------------------------------ medida
def loudness(x):
    """Sonoridad aproximada (dB, RMS de lo que suena con el filtro K simplificado: sin graves por debajo de 100 Hz)."""
    m = x.mean(axis=1) if x.ndim == 2 else x
    y = hp(m, 100)
    y = y + 0.4 * hp(y, 2000)
    win = int(0.4 * SR)
    if len(y) < win:
        return 20 * np.log10(max(1e-9, np.sqrt(np.mean(y ** 2))))
    blocks = [np.mean(y[i:i + win] ** 2) for i in range(0, len(y) - win, win // 4)]
    blocks = np.array(blocks)
    gate = blocks[blocks > np.max(blocks) * 0.01]
    return 10 * np.log10(max(1e-12, np.mean(gate)))


def master(x, target_db, peak=0.89, tail=0.08):
    """Al volumen pedido (sin pasar del pico), sin continua y con el final suave (mono o estéreo, como venga)."""
    x = x - np.mean(x, axis=0)
    x = hp(x, 35)
    g = 10 ** ((target_db - loudness(x)) / 20)
    x = x * g
    pk = np.max(np.abs(x))
    if pk > peak:
        # limitador suave: comprime solo lo que pasa del 70 % del pico
        th = 0.7 * peak
        over = np.abs(x) > th
        x[over] = np.sign(x[over]) * (th + (peak - th) * np.tanh((np.abs(x[over]) - th) / (peak - th)))
    # recorta el silencio del final
    m = np.max(np.abs(x), axis=1) if x.ndim == 2 else np.abs(x)
    last = len(m) - np.argmax(m[::-1] > 1e-3)
    x = x[:max(last, int(0.05 * SR))]
    return fade(x, 0, tail)


def mono(x):
    return x.mean(axis=1) if x.ndim == 2 else x


# ------------------------------------------------------------------------------------------------ la cinemática
def llave_aparece():
    out = silence(2.2)
    place(out, st(whoosh(1.1, 500, 5200, width=0.5, gain=0.35, shape='swell', seed=21)), 0.0)
    for i, (f, at) in enumerate(((A5, 0.30), (D6, 0.44), (Fs6, 0.58), (A6, 0.72), (D7, 0.86))):
        place(out, st(celesta(f, 1.4, vel=0.55 + 0.1 * i, bright=0.8), pan=-0.5 + 0.25 * i), at)
    t = tt(1.6)
    shim = (np.sin(2 * np.pi * D6 * t) + 0.5 * np.sin(2 * np.pi * A6 * t)) * (0.5 + 0.5 * np.sin(2 * np.pi * 7 * t))
    shim *= np.sin(np.pi * np.clip(t / 1.6, 0, 1)) ** 2
    place(out, st(shim * 0.08), 0.2)
    place(out, sparkles(2.0, 10, 22, lo=D7, start=0.6, spread=0.9, gain=0.5), 0)
    return master(room(out, wet=0.42, rt=2.0, bright=0.7, seed=31), -21)


def llave_lanza():
    out = silence(1.5)
    w = whoosh(1.3, 900, 2600, width=0.55, gain=1.0, shape='arc', seed=41)
    t = np.arange(len(w)) / SR
    spin = 0.55 + 0.45 * np.sin(2 * np.pi * (7 + 5 * t / 1.3) * t) ** 2   # la llave girando corta el aire a golpes
    w = w * spin
    pan = np.sin(np.pi * t / 1.3 - np.pi / 2) * 0.7                         # de un lado a otro, como vuela
    a = (pan + 1) * np.pi / 4
    place(out, np.stack([w * np.cos(a), w * np.sin(a)], 1) * 0.6, 0)
    place(out, sparkles(1.5, 9, 42, lo=A6, start=0.15, spread=1.0, gain=0.35), 0)
    return master(room(out, wet=0.3, rt=1.6, seed=32), -24)


def llave_entra():
    out = silence(1.2)
    wood = load('impactWood_heavy_000.ogg', 0.85)
    place(out, st(lp(wood, 3000) * 0.9), 0.0)
    place(out, st(boom(110, 60, 0.4, gain=0.45, punch=0.6)), 0.0)
    scrape = load('drawKnife1.ogg', 1.35)[:int(0.16 * SR)]
    scrape = bp(fade(scrape, 0.01, 0.05), 1200, 9000)
    place(out, st(scrape * 0.45, 0.15), 0.07)
    place(out, st(load('metalLatch.ogg', 1.05) * 0.85, 0.1), 0.2)
    place(out, st(metal_ring(0.5, 2600, 0.18), 0.1), 0.22)
    return master(room(out, wet=0.22, rt=1.3, seed=33), -20)


def llave_gira():
    out = silence(3.0)
    for i, (at, r) in enumerate(((0.0, 1.12), (0.075, 1.05), (0.15, 0.98))):
        c = load('metalClick.ogg', r)
        place(out, st(c * (0.55 + 0.1 * i), 0.1), at)
    clunk = lp(load('doorClose_1.ogg', 0.82), 2400)
    place(out, st(clunk * 0.8), 0.24)
    place(out, st(boom(95, 50, 0.6, gain=0.55, punch=0.7)), 0.24)
    place(out, st(crystal(D6, 2.6, 0.32), -0.2), 0.3)
    place(out, st(crystal(A6, 2.2, 0.18), 0.25), 0.34)
    return master(room(out, wet=0.32, rt=2.2, seed=34), -20)


def runas():
    out = silence(2.4)
    t = tt(1.9)
    hum = np.zeros(len(t))
    for f, a in ((D4, 1.0), (A4, 0.6), (D5, 0.35)):
        ft = f * (1 + 0.02 * np.clip(t / 1.2, 0, 1)) * (1 + 0.003 * np.sin(2 * np.pi * 5 * t))
        hum += a * np.sin(2 * np.pi * np.cumsum(ft) / SR)
    hum *= np.clip(t / 0.7, 0, 1) ** 2 * np.clip((1.9 - t) / 0.6, 0, 1) * (0.8 + 0.2 * np.sin(2 * np.pi * 9 * t))
    place(out, st(lp(hum, 1800) * 0.3), 0)
    place(out, st(whoosh(1.4, 300, 3000, width=0.6, gain=0.22, seed=51)), 0.1)
    for i, f in enumerate((A6, D7, Fs7, A7)):
        place(out, st(crystal(f, 1.2, 0.16), -0.6 + 0.4 * i), 0.55 + 0.1 * i)
    return master(room(out, wet=0.4, rt=2.0, bright=0.6, seed=35), -23)


def tiembla():
    """De TREMBLE0 a OPEN (0,9 s): la madera traquetea cada vez más y todo sube hasta el golpe de la apertura."""
    out = silence(1.1)
    rnd = np.random.default_rng(61)
    woods = ['impactWood_light_000.ogg', 'impactWood_light_001.ogg', 'impactWood_light_002.ogg', 'impactWood_light_003.ogg',
             'impactPlank_medium_000.ogg', 'impactPlank_medium_001.ogg']
    at = 0.0
    while at < 0.86:
        w = load(woods[rnd.integers(len(woods))], rnd.uniform(0.85, 1.15))
        place(out, st(lp(w, 4500) * rnd.uniform(0.25, 0.5) * (0.6 + 0.6 * at), rnd.uniform(-0.3, 0.3)), at)
        at += rnd.uniform(0.05, 0.12) * (1.2 - 0.6 * at)
    t = tt(0.92)
    rumble = lp(rnd.standard_normal(len(t)), 90) * (t / 0.92) ** 1.5 * 2.0
    place(out, st(rumble * 0.5), 0)
    rise = np.zeros(len(t))
    for f in (D4, A4, D5):
        ft = f * 2 ** (np.clip(t / 0.92, 0, 1) * 7 / 12)
        rise += np.sin(2 * np.pi * np.cumsum(ft) / SR)
    place(out, st(rise * (t / 0.92) ** 3 * 0.12), 0)
    place(out, st(whoosh(0.9, 400, 6000, width=0.5, gain=0.35, shape='swell', seed=62)), 0.02)
    return master(room(out, wet=0.25, rt=1.4, seed=36), -21, tail=0.03)


def apertura():
    out = silence(3.6)
    place(out, st(boom(80, 40, 2.2, gain=1.0, punch=1.0)), 0)
    place(out, st(lp(load('doorOpen_1.ogg', 1.2), 3500) * 0.45, -0.1), 0.0)
    place(out, st(whoosh(0.9, 6000, 1500, width=0.7, gain=0.5, shape='burst', seed=71)), 0.0)
    place(out, brass([D4, A4, D5, Fs5], 2.6, swell=0.06, hold=0.35, release=0.9, gain=0.45), 0.02)
    place(out, pad([D4, A4, D5, Fs5, A5, E6], 3.2, attack=0.05, release=2.0, cutoff=5000, gain=1.6), 0.0)
    place(out, sparkles(3.4, 34, 72, start=0.06, spread=1.9, gain=0.9, fall=True), 0)
    for i, f in enumerate((D6, Fs6, A6, D7)):
        place(out, st(celesta(f, 1.6, 0.5, 1.0), -0.45 + 0.3 * i), 0.04 + 0.05 * i)
    return master(room(out, wet=0.36, rt=2.4, bright=0.6, seed=37), -17)


def tic():
    """Un toque corto, de madera y cristal: limpio aunque suene treinta veces seguidas."""
    n = int(0.16 * SR)
    t = np.arange(n) / SR
    body = np.sin(2 * np.pi * 1320 * t) * np.exp(-t / 0.018) + 0.35 * np.sin(2 * np.pi * 3640 * t) * np.exp(-t / 0.008)
    knock = bp(RNG.standard_normal(n), 1800, 5200) * np.exp(-t / 0.004) * 0.6
    glass = 0.18 * np.sin(2 * np.pi * A6 * 2 * t) * np.exp(-t / 0.05)
    x = (body + knock + glass) * np.clip(t / 0.0008, 0, 1)
    return master(room(st(x), wet=0.12, rt=0.6, seed=38), -25, tail=0.03)


def parada():
    out = silence(2.6)
    knock = lp(load('impactWood_light_002.ogg', 0.75), 2500)
    place(out, st(knock * 0.6), 0)
    place(out, st(crystal(A5, 2.4, 0.5), -0.15), 0.02)
    place(out, st(crystal(D6, 2.2, 0.35), 0.2), 0.05)
    place(out, sparkles(2.0, 6, 81, lo=D7, start=0.08, spread=0.5, gain=0.3), 0)
    return master(room(out, wet=0.34, rt=2.0, seed=39), -22)


def premio(level):
    """El premio. 1 común: dos notas; 2 rara: arpegio y brillos; 3 épica: arpegio, acorde y golpe suave; 4 legendaria:
    metales, coro, timbal y cascada de brillos; 5 mítica: todo más grande, con el coro que sube y una campana al final."""
    dur = (1.8, 2.3, 3.0, 3.8, 4.6)[level - 1]
    out = silence(dur)
    if level == 1:
        place(out, st(celesta(D6, 1.4, 0.7), -0.2), 0.0)
        place(out, st(celesta(A6, 1.4, 0.6), 0.2), 0.12)
        place(out, sparkles(1.5, 5, 91, lo=D7, start=0.2, spread=0.5, gain=0.3), 0)
        return master(room(out, wet=0.32, rt=1.6, seed=40), -22)
    if level == 2:
        for i, f in enumerate((D6, Fs6, A6, D7)):
            place(out, st(celesta(f, 1.6, 0.6 + 0.08 * i), -0.4 + 0.27 * i), 0.09 * i)
        place(out, st(harp(D5, 1.8, 0.35)), 0.0)
        place(out, pad([D5, A5, Fs5], 2.0, attack=0.15, release=1.2, cutoff=3000, gain=0.7), 0.05)
        place(out, sparkles(2.2, 12, 92, lo=A6, start=0.25, spread=0.9, gain=0.45), 0)
        return master(room(out, wet=0.36, rt=1.9, seed=41), -20)
    if level == 3:
        for i, f in enumerate((A5, D6, Fs6, A6, D7)):
            place(out, st(celesta(f, 1.8, 0.6 + 0.08 * i), -0.5 + 0.25 * i), 0.07 * i)
        for i, f in enumerate((D4, A4, D5, Fs5)):
            place(out, st(harp(f, 2.2, 0.4)), 0.02 * i)
        place(out, pad([D4, A4, D5, Fs5, A5, E6], 2.8, attack=0.12, release=1.6, cutoff=4200, gain=1.2), 0.0)
        place(out, st(boom(70, 42, 1.4, gain=0.5, punch=0.4)), 0.0)
        place(out, sparkles(2.9, 22, 93, start=0.3, spread=1.5, gain=0.6, fall=True), 0)
        return master(room(out, wet=0.38, rt=2.2, seed=42), -18)
    if level == 4:
        place(out, st(boom(76, 40, 2.4, gain=0.9)), 0.0)
        place(out, brass([D3, A3, D4, Fs4, A4], 3.2, swell=0.09, hold=0.9, release=1.2, gain=0.55), 0.0)
        place(out, choir([D4, A4, D5, Fs5], 3.6, attack=0.4, release=1.5, gain=0.4), 0.1)
        for i, f in enumerate((D6, Fs6, A6, D7, Fs7)):
            place(out, st(celesta(f, 1.8, 0.55 + 0.06 * i), -0.6 + 0.3 * i), 0.15 + 0.07 * i)
        place(out, sparkles(3.6, 36, 94, start=0.2, spread=2.2, gain=0.75, fall=True), 0)
        return master(room(out, wet=0.38, rt=2.6, bright=0.6, seed=43), -16)
    # mítica
    place(out, st(boom(70, 34, 2.8, gain=1.0)), 0.0)
    place(out, st(boom(90, 45, 1.2, gain=0.5)), 0.45)
    place(out, brass([D3, A3, D4, Fs4, A4, D5], 3.8, swell=0.1, hold=1.2, release=1.4, gain=0.6), 0.0)
    place(out, choir([D4, A4, D5, Fs5, A5], 4.2, attack=0.6, release=1.6, gain=0.5, seed=8), 0.05)
    place(out, st(whoosh(1.2, 400, 7000, width=0.5, gain=0.3, shape='swell', seed=95)), 0.0)
    for i, f in enumerate((A5, D6, Fs6, A6, D7, Fs7, A7)):
        place(out, st(celesta(f, 1.8, 0.5 + 0.05 * i), -0.7 + 0.23 * i), 0.12 + 0.06 * i)
    place(out, sparkles(4.4, 48, 96, start=0.15, spread=2.8, gain=0.8, fall=True), 0)
    place(out, st(crystal(D7, 2.4, 0.35)), 1.4)
    return master(room(out, wet=0.42, rt=3.0, bright=0.7, seed=44), -15)


# ------------------------------------------------------------------------------------------------ la crate del mundo
def cierra():
    """La crate del mundo se cierra: la tapa de madera y dos notas que bajan."""
    out = silence(1.8)
    place(out, st(lp(load('doorClose_1.ogg', 1.05), 3000) * 0.7), 0)
    place(out, st(celesta(A5, 1.2, 0.35)), 0.12)
    place(out, st(celesta(D5, 1.3, 0.3)), 0.26)
    return room(out, wet=0.25, rt=1.3, seed=45)


def mundo(x, db):
    """Mono (para que suene en su sitio) y un poco más seco que en la escena."""
    return master(mono(x), db)


SOUNDS = {
    'llave_aparece': llave_aparece, 'llave_lanza': llave_lanza, 'llave_entra': llave_entra, 'llave_gira': llave_gira,
    'runas': runas, 'tiembla': tiembla, 'apertura': apertura, 'tic': tic, 'parada': parada,
    'premio1': lambda: premio(1), 'premio2': lambda: premio(2), 'premio3': lambda: premio(3),
    'premio4': lambda: premio(4), 'premio5': lambda: premio(5),
    'mundo_llave': lambda: mundo(np.concatenate([llave_entra()[:int(0.3 * SR)], llave_gira()]), -20),
    'mundo_carga': lambda: mundo(runas(), -22),
    'mundo_sube': lambda: mundo(room(st(celesta(D6, 0.6, 0.6) + 0.5 * celesta(A6, 0.6, 0.4)), wet=0.25, rt=1.0), -25),
    'mundo_pico': lambda: mundo(tiembla(), -21),
    'mundo_abre': lambda: mundo(apertura(), -18),
    'mundo_tic': lambda: mundo(tic(), -26),
    'mundo_premio1': lambda: mundo(premio(1), -21), 'mundo_premio2': lambda: mundo(premio(2), -20),
    'mundo_premio3': lambda: mundo(premio(3), -19), 'mundo_premio4': lambda: mundo(premio(4), -18),
    'mundo_premio5': lambda: mundo(premio(5), -17),
    'mundo_cierra': lambda: mundo(cierra(), -23),
}


def write_wav(path, x):
    import wave
    x = np.clip(x, -1, 1)
    data = (x * 32767).astype('<i2')
    ch = 1 if data.ndim == 1 else data.shape[1]
    with wave.open(path, 'wb') as w:
        w.setnchannels(ch)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def main():
    wav_dir = sys.argv[sys.argv.index('--wav') + 1] if '--wav' in sys.argv else None
    only = [a for a in sys.argv[1:] if a in SOUNDS]
    tmp = tempfile.mkdtemp()
    os.makedirs(RES, exist_ok=True)
    for name, fn in SOUNDS.items():
        if only and name not in only:
            continue
        x = fn()
        wav = os.path.join(wav_dir or tmp, f'crates_{name}.wav')
        os.makedirs(os.path.dirname(wav), exist_ok=True)
        write_wav(wav, x)
        out = os.path.join(RES, f'{name}.ogg')
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', out], check=True)
        pk = 20 * np.log10(max(1e-9, np.max(np.abs(x))))
        print(f'   {name:14s} {len(x) / SR:4.1f} s  {"mono" if x.ndim == 1 else "estéreo"}  sonoridad {loudness(x):6.1f} dB'
              f'  pico {pk:5.1f} dB  → {os.path.getsize(out) // 1024} KB')


if __name__ == '__main__':
    main()
