"""Los sonidos de la grieta del cielo (1.3.97): sintetizados aquí, sin muestras de nadie, SIN GOLPES, sin cristal y sin
explosiones.

    python3 tools/void/sonidos_cielo.py [--wav carpeta] [--medir] [nombres...]

El dueño, de la 1.3.96: «el sonido de la grieta no es épico, no suena el quebranto del cielo; no pongas sonidos
secuenciados, tampoco de explosiones o cristales». Antes era un acorde de senos que florecía, un desgarro de ruido y un
brillo agudo (sonaba a cristal). Ahora el cielo SE QUIEBRA como en el cine, todo continuo:

  - el cielo cruje (groan): un roce lento y áspero, como una estructura enorme bajo tensión (pulsos irregulares de 20 a
    70 Hz que se funden en un gruñido, por resonancias graves que suben con la tensión);
  - un tono de Shepard que sube sin acabar nunca (la tensión), y un coro grave de hombres («oh»);
  - el «braam»: metales graves enormes (sierras desafinadas por un filtro que se abre como un metal al soplar, con
    gruñido y saturación) que entra en 0,2 s, sin golpe, y se hunde despacio: es el cielo que se parte;
  - el retumbo que rueda (como un trueno sin golpes: ruido grave con un vaivén lento) y el desgarro grave del cielo que
    se abre en estéreo, como las puntas;
  - el coro grande («aah») y, abierta, el zumbido del Void.
Estéreo (se oyen sin posición, como sonidos de interfaz). Salen en assets/tfclient/sounds/cielo/ (OGG Vorbis):

  cielo/preludio   12 s de aparición: el cielo se apaga y se forma la tormenta; el cielo empieza a crujir y cruje cada
                   vez más fuerte, el grave y el coro de hombres crecen, el retumbo rueda y el tono de Shepard sube sin
                   fin; lleva sin corte a «rompe» (se solapa 1 s con ella)
  cielo/rompe      se rompe (la telaraña crece 0-1,8 s, estalla a los 1,8 s, se abre hasta los 5,5 s): el crujido llega a
                   lo más tenso y, a los 1,8 s, el braam en re menor, el grave que se hunde, el desgarro que se abre a los
                   lados, el retumbo y el coro grande; queda el zumbido del Void
  cielo/cierra     se sella (fogonazo a los 2,3 s): el braam al revés (se aspira hasta el fogonazo), el desgarro que se
                   cose de los lados al centro y un grave suave con un coro que se apaga
  cielo/reabre     se vuelve a abrir (estalla a los 1,2 s): más oscuro (braam con el trítono y un gruñido de coro)
  cielo/final      el final (fogonazo a los 2,5 s): el braam al revés que se abre en re mayor, luminoso, y el coro en paz
  cielo/aliento    abierta, cada 40-70 s: el Void respira (el cielo cruje lejos, muy bajo)

--medir: dice, por pista, el pico, el volumen y el «golpe» más fuerte (lo que más sube el volumen en 20 ms respecto a
los 60 ms de antes, en dB, pasados los primeros 0,3 s): por encima de ~10 dB se oiría como un golpe. Sin nombres, las hace todas.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'bosses'))
from sonidos_grieta import SR, bp, hp, lp, lp_sweep, norm, reverb, rng, saw, soft, sweep, write_wav  # noqa: E402

RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'sounds'))


def tt(dur):
    return np.arange(int(dur * SR)) / SR


def smooth(a, b, x):
    u = np.clip((x - a) / (b - a), 0, 1)
    return u * u * (3 - 2 * u)


def swell(t, at, attack, decay):
    """Envolvente sin golpe: sube en «attack» s con forma de S y cae con una exponencial suave."""
    up = smooth(at - attack, at, t)
    down = np.where(t > at, np.exp(-np.maximum(t - at, 0) / decay), 1.0)
    return up * down


def pad(t, freqs, cutoff0, cutoff1, seed=1, spread=0.004, voices=3):
    """Acorde de sierras desafinadas, filtrado que se abre; estéreo (cada lado con su desafinación)."""
    r = rng(seed)
    out = np.zeros((len(t), 2))
    for f in freqs:
        for c in range(2):
            for v in range(voices):
                det = 1 + r.uniform(-spread, spread)
                out[:, c] += saw(2 * np.pi * f * det * t + r.uniform(0, 6.28), 18)
    n = len(t)
    for c in range(2):
        out[:, c] = lp_sweep(out[:, c], cutoff0, cutoff1, 48)
    return out / (len(freqs) * voices)


def choir(t, freqs, seed=2, vib=0.004):
    """Un coro («aah»): sierras con vibrato por las formantes de la vocal a."""
    r = rng(seed)
    out = np.zeros((len(t), 2))
    for f in freqs:
        for c in range(2):
            for v in range(3):
                rate = r.uniform(4.5, 6.0)
                ph = 2 * np.pi * np.cumsum(f * (1 + r.uniform(-0.003, 0.003)) * (1 + vib * np.sin(2 * np.pi * rate * t + r.uniform(0, 6)))) / SR
                out[:, c] += saw(ph, 30)
    for c in range(2):
        x = out[:, c]
        out[:, c] = bp(x, 600, 850) * 1.0 + bp(x, 1050, 1300) * 0.6 + bp(x, 2400, 2800) * 0.15
    return out / (len(freqs) * 3)


def noise2(n, seed):
    r = rng(seed)
    return np.stack([r.standard_normal(n), r.standard_normal(n)], 1)


def band_sweep(x, f0, f1, width=0.6, blocks=64):
    """Paso banda que se mueve de f0 a f1 (por bloques, con estado), en cada canal."""
    from scipy import signal
    out = np.zeros_like(x)
    n = len(x)
    edges = np.linspace(0, n, blocks + 1).astype(int)
    for c in range(x.shape[1]):
        zi = None
        for i in range(blocks):
            fc = f0 * (f1 / f0) ** (i / max(1, blocks - 1))
            lo, hi = fc * (1 - width / 2), min(fc * (1 + width / 2), SR / 2 - 200)
            b, a = signal.butter(2, [lo / (SR / 2), hi / (SR / 2)], 'band')
            if zi is None:
                zi = signal.lfilter_zi(b, a) * 0
            seg = x[edges[i]:edges[i + 1], c]
            out[edges[i]:edges[i + 1], c], zi = signal.lfilter(b, a, seg, zi=zi)
    return out


def pan(mono, p):
    """Mono → estéreo con el panorama p (−1 izquierda, 1 derecha; puede variar en el tiempo), a igual potencia."""
    a = (np.asarray(p) + 1) * np.pi / 4
    return np.stack([mono * np.cos(a), mono * np.sin(a)], 1)


def stereo_reverb(x, seconds, damp, wet, seed):
    # (lo seco acaba en 0,4 s suaves: si acabara de golpe, la reverb lo convertiría en un chasquido)
    x = x.copy()
    fo = int(0.4 * SR)
    x[-fo:] *= np.linspace(1, 0, fo)[:, None]
    return np.stack([reverb(x[:, 0], seconds, damp, wet, seed=seed), reverb(x[:, 1], seconds, damp, wet, seed=seed + 101)], 1)


def sub(t, f0, f1, curve=1.0):
    s = np.sin(sweep(f0, f1, len(t), curve))
    # armónicos (que se oiga también en altavoces pequeños)
    return s + 0.25 * soft(s * 2.5, 2.5)


def bloom(t, at, freqs, attack=0.35, decay=3.5, seed=7, bright=3500):
    """Un acorde que florece (sin ataque seco): senos y sierras suaves con coro, que se abre y se apaga despacio."""
    r = rng(seed)
    out = np.zeros((len(t), 2))
    env = swell(t, at, attack, decay)
    for f in freqs:
        for c in range(2):
            for v in range(3):
                det = 1 + r.uniform(-0.004, 0.004)
                ph = 2 * np.pi * f * det * t + r.uniform(0, 6.28)
                out[:, c] += 0.6 * np.sin(ph) + 0.4 * saw(ph, 10)
    for c in range(2):
        out[:, c] = lp(out[:, c], bright)
    return out * env[:, None] / (len(freqs) * 3)


def shimmer(t, freqs, seed=9):
    """Brillo alto que tiembla (parciales con trémolo lento, cada uno a su ritmo)."""
    r = rng(seed)
    out = np.zeros((len(t), 2))
    for f in freqs:
        for c in range(2):
            rate = r.uniform(0.6, 2.2)
            out[:, c] += np.sin(2 * np.pi * f * (1 + r.uniform(-0.002, 0.002)) * t + r.uniform(0, 6)) * \
                (0.5 + 0.5 * np.sin(2 * np.pi * rate * t + r.uniform(0, 6))) ** 2
    return out / len(freqs)


def tear(t, t0, t1, seed=17, f0=280, f1=2600, spread=True):
    """El desgarro: ruido que se rasga (aspereza a 25-45 Hz) por una banda que sube; en estéreo se abre como las puntas."""
    n = len(t)
    x = noise2(n, seed)
    x = band_sweep(x, f0, f1, 0.9)
    rough = 0.6 + 0.4 * np.sin(2 * np.pi * np.cumsum(25 + 20 * smooth(t0, t1, t)) / SR)
    env = smooth(t0, t0 + 0.6, t) * (1 - smooth(t0 + (t1 - t0) * 0.55, t1 + 0.8, t))
    mono = (x[:, 0] + x[:, 1]) * 0.5 * rough * env
    if not spread:
        return pan(mono, 0.0)
    # dos «puntas» que se separan del centro a los lados
    p = 0.85 * smooth(t0, t1, t)
    return 0.6 * pan(x[:, 0] * rough * env, -p) + 0.6 * pan(x[:, 1] * rough * env, p)


def whoosh(t, at, attack, decay, f0, f1, seed=21):
    """Un soplo de aire: ruido con el filtro de f0 a f1, ataque suave."""
    n = len(t)
    x = noise2(n, seed)
    for c in range(2):
        x[:, c] = lp_sweep(x[:, c], f0, f1, 48)
    return x * swell(t, at, attack, decay)[:, None]


def riser(t, t0, t1, f0, f1, seed=25):
    """Subida (tono que sube y ruido agudo que crece), sin corte: llega arriba en t1."""
    k = smooth(t0, t1, t) ** 1.5
    tone = np.sin(sweep(f0, f1, len(t), 1.0)) * k
    tone2 = np.sin(sweep(f0 * 1.5, f1 * 1.5, len(t), 1.0)) * k * 0.5
    nz = noise2(len(t), seed)
    for c in range(2):
        nz[:, c] = hp(lp_sweep(nz[:, c], 1500, 12000, 32), 900)
    return pan(tone + tone2, 0.0) * 0.6 + nz * (k ** 2)[:, None] * 0.35


def rumble(t, at, amp, seed):
    """Retumbo lejano (relámpago): graves con ataque lento, que ruedan."""
    n = len(t)
    x = noise2(n, seed)
    for c in range(2):
        x[:, c] = lp(x[:, c], 160)
    return x * (swell(t, at + 0.25, 0.35, 1.6) * amp)[:, None] * 2.5


# ------------------------------------------------------------------------------------- instrumentos 1.3.97


def saw_blep(freq, seed=0):
    """Sierra limpia (polyBLEP, sin aliasing apreciable) con la frecuencia instantánea freq (Hz, array)."""
    dt = np.asarray(freq, float) / SR
    ph = (np.cumsum(dt) + rng(seed).random()) % 1.0
    y = 2 * ph - 1
    m = ph < dt
    u = ph[m] / dt[m]
    y[m] -= u + u - u * u - 1
    m = ph > 1 - dt
    u = (ph[m] - 1) / dt[m]
    y[m] -= u * u + u + u + 1
    return y


def biquad(x, fc, q=0.707, kind='low', block=128):
    """Filtro de 2.º orden (RBJ) con la frecuencia fc que cambia con el tiempo (array o número), por bloques con estado."""
    from scipy import signal
    fc = np.broadcast_to(np.asarray(fc, float), x.shape)
    out = np.empty_like(x)
    zi = np.zeros(2)
    for i in range(0, len(x), block):
        f = min(max(float(fc[i]), 20.0), SR / 2 - 500)
        w = 2 * np.pi * f / SR
        al, cw = np.sin(w) / (2 * q), np.cos(w)
        b = [(1 - cw) / 2, 1 - cw, (1 - cw) / 2] if kind == 'low' else [al, 0.0, -al]
        a = [1 + al, -2 * cw, 1 - al]
        out[i:i + block], zi = signal.lfilter(np.array(b) / a[0], np.array(a) / a[0], x[i:i + block], zi=zi)
    return out


def slow_noise(n, rate, seed):
    """Un vaivén irregular y lento (ruido filtrado a «rate» Hz), de 0 a 1."""
    m = lp(rng(seed).standard_normal(n + SR), rate, 2)[SR:]
    m /= np.std(m) + 1e-9
    return 0.5 + 0.5 * np.tanh(m)


def braam(t, at, notes, attack=0.2, hold=1.4, release=3.2, seed=50, bright=3000, drop=0.03, growl=0.3, drive=2.8):
    """El «braam» del cine: metales graves enormes. Sierras desafinadas (5 por nota y lado) por un paso bajo que se abre
    al soplar (de 110 Hz a «bright» en el ataque) y se cierra despacio; la nota entra un pelo baja (el labio) y se hunde
    «drop» al apagarse; gruñido (vaivén áspero a ~40 Hz) y saturación. Empieza en «at» y llega arriba en at + attack (sin
    golpe). Estéreo."""
    r = rng(seed)
    n = len(t)
    top = at + attack
    amp = smooth(at, top, t) * np.where(t > top + hold, np.exp(-np.maximum(t - top - hold, 0) / (release / 2.6)), 1.0)
    fc = 110 + (bright - 110) * smooth(at - 0.02, top + 0.05, t) * (0.4 + 0.6 * np.exp(-np.maximum(t - top, 0) / 1.3))
    bend = (1 - 0.018 * (1 - smooth(at, top, t))) * (1 - drop * smooth(top, top + hold + release, t))
    rough = 1 + growl * (lp(r.standard_normal(n), 45, 2) / 0.12).clip(-1.5, 1.5) * 0.5
    out = np.zeros((n, 2))
    # (lo que pesa cada nota: el grave da el peso con poco, los del medio son el metal que se oye)
    gain = (0.5, 0.95, 1.0, 0.9, 0.8, 0.7)
    for k, f in enumerate(notes):
        for c in range(2):
            x = np.zeros(n)
            for v in range(5):
                det = 1 + (v - 2) * 0.0026 + r.uniform(-0.0012, 0.0012)
                x += saw_blep(f * det * bend, seed=seed * 31 + k * 7 + v * 3 + c)
            x /= 5
            x = biquad(x, fc * (1.0 + 0.04 * c), q=1.1)
            out[:, c] += x * gain[min(k, len(gain) - 1)]
    # el peso: un seno en la fundamental más grave
    out += (np.sin(2 * np.pi * np.cumsum(notes[0] * bend) / SR) * 0.3)[:, None]
    out *= rough[:, None]
    out = soft(out * drive, 1.0)
    for c in range(2):
        out[:, c] = lp(out[:, c], bright * 1.6, 2)
    return out * amp[:, None]


def groan(t, rate, res, amount, seed=70, jitter=0.35, q=9.0):
    """El cielo que cruje: un roce (pulsos irregulares a «rate» Hz, de fuerza al azar, que se funden en un gruñido
    áspero) por las resonancias «res» (Hz; cada una número o array), como una estructura enorme bajo tensión. Mono."""
    r = rng(seed)
    n = len(t)
    jit = lp(r.standard_normal(n), 25, 2)
    jit /= np.std(jit) + 1e-9
    ph = np.cumsum(np.asarray(rate, float) * (1 + jitter * np.tanh(jit)) / SR)
    k = np.floor(ph)
    imp = (np.diff(k, prepend=k[0]) > 0) * (0.45 + 0.55 * r.random(n))
    # (cada pulso, 2 ms suaves: sin chasquido de banda ancha)
    w = np.hanning(int(0.002 * SR) + 2)
    imp = np.convolve(imp, w / w.sum(), 'same')
    out = np.zeros(n)
    for i, f in enumerate(res):
        out += biquad(imp, f, q=q * (1 + 0.3 * i), kind='band') / (1 + 0.5 * i)
    out = lp(out, 1800, 2)
    out /= np.max(np.abs(out)) + 1e-9
    return out * amount


def roll(t, amount, seed=90, cutoff=300, rate=5.0):
    """Retumbo que rueda sin golpes (como un trueno largo): ruido grave con un vaivén lento e irregular, en estéreo."""
    n = len(t)
    x = noise2(n, seed)
    out = np.zeros((n, 2))
    for c in range(2):
        m = slow_noise(n, rate, seed + 10 + c) ** 1.6
        out[:, c] = lp(x[:, c], cutoff, 4) * m * 3.0
    return out * np.asarray(amount)[..., None] if np.ndim(amount) else out * amount


def shepard(t, t0, base=36.71, octaves=6, speed=0.2, seed=95):
    """Tono de Shepard que sube sin acabar nunca (la tensión): parciales a octavas que suben «speed» octavas/s, con una
    campana de volumen en la escala logarítmica (los de arriba se apagan y entran otros por abajo). Estéreo."""
    r = rng(seed)
    out = np.zeros((len(t), 2))
    pos = speed * np.maximum(t - t0, 0)
    for k in range(octaves):
        o = (k + pos) % octaves
        f = base * 2 ** o
        amp = np.exp(-((o - octaves * 0.45) / (octaves / 5.0)) ** 2)
        for c in range(2):
            ph = 2 * np.pi * np.cumsum(f * (1 + (c - 0.5) * 0.003)) / SR + r.uniform(0, 6.28)
            out[:, c] += amp * (np.sin(ph) + 0.25 * np.sin(2 * ph) + 0.1 * np.sin(3 * ph))
    return out / octaves * 2.2


def choir_o(t, freqs, seed=12, vib=0.004):
    """Coro grave de hombres («oh»): sierras con vibrato por las formantes de la o."""
    r = rng(seed)
    out = np.zeros((len(t), 2))
    for f in freqs:
        for c in range(2):
            for v in range(3):
                rate = r.uniform(4.2, 5.6)
                fr = f * (1 + r.uniform(-0.003, 0.003)) * (1 + vib * np.sin(2 * np.pi * rate * t + r.uniform(0, 6)))
                out[:, c] += saw_blep(fr, seed=seed * 13 + v + 5 * c)
    for c in range(2):
        x = out[:, c]
        out[:, c] = bp(x, 380, 560) * 1.0 + bp(x, 720, 920) * 0.55 + bp(x, 2300, 2700) * 0.08
    return out / (len(freqs) * 3)


def rasgar(t, t0, t1, seed=17, f0=150, f1=700, spread=True, inward=False):
    """El desgarro grave del cielo: ruido que se rasga (aspereza irregular, no periódica) por una banda que sube de f0 a
    f1; en estéreo se abre del centro a los lados (o, «inward», se cierra de los lados al centro)."""
    n = len(t)
    x = noise2(n, seed)
    x = band_sweep(x, f0, f1, 1.1)
    env = smooth(t0, t0 + 0.5, t) * (1 - smooth(t0 + (t1 - t0) * 0.6, t1 + 0.6, t))
    rough = np.stack([slow_noise(n, 38, seed + 3) ** 2.2, slow_noise(n, 38, seed + 4) ** 2.2], 1) * 2.4
    y = x * rough * env[:, None]
    if not spread:
        return pan(y.mean(1), 0.0)
    p = 0.85 * (1 - smooth(t0, t1, t) if inward else smooth(t0, t1, t))
    return 0.7 * pan(y[:, 0], -p) + 0.7 * pan(y[:, 1], p)


def cruje(t, amount, rate, sc, seed, pan_rate=0.07):
    """Dos crujidos del cielo (uno a cada lado, que se mueven despacio), cada uno con su vaivén: «rate» (Hz) y la escala
    de sus resonancias «sc» (1 = 95-655 Hz; más = más tenso), números o arrays. amount: array."""
    a = groan(t, rate, [95 * sc, 180 * sc, 305 * sc, 510 * sc], amount * (0.45 + 0.55 * slow_noise(len(t), 0.35, seed + 1)), seed=seed)
    b = groan(t, rate * 1.13, [122 * sc, 236 * sc, 390 * sc, 655 * sc], amount * (0.45 + 0.55 * slow_noise(len(t), 0.3, seed + 2)),
              seed=seed + 5)
    p = 0.6 * np.sin(2 * np.pi * pan_rate * t + seed)
    return pan(a, -0.35 + 0.25 * p) + pan(b, 0.35 + 0.25 * p)


def glue(x, below_db=-14.0, ratio=3.0, rate=5.0):
    """Compresión lenta (la envolvente, a «rate» Hz: sin bombeo ni chasquidos): lo que pasa de «below_db» por debajo del
    máximo se aprieta «ratio» a 1. Así suena grande y lleno sin subir el pico."""
    env = np.sqrt(np.maximum(lp(np.max(x ** 2, 1), rate, 2), 1e-12))
    thr = env.max() * 10 ** (below_db / 20)
    g = np.where(env > thr, (env / thr) ** (1 / ratio - 1), 1.0)
    return x * g[:, None]


def hall(x, seconds=4.5, wet=0.42, damp=2400, seed=11):
    return stereo_reverb(x, seconds, damp, wet, seed)


# ---------------------------------------------------------------------------------------------- pistas

DMIN = (36.71, 73.42, 110.0, 146.83, 174.61)        # re menor, grave: re1 re2 la2 re3 fa3
DTRI = (36.71, 73.42, 103.83, 146.83, 174.61)       # con el trítono (sol#2): más oscuro
DMAJ = (36.71, 73.42, 110.0, 146.83, 185.0)         # re mayor
DOPEN = (36.71, 73.42, 110.0, 146.83)               # quinta vacía


def preludio():
    dur = 13.2
    t = tt(dur)
    a = (0.1 + 0.9 * (np.minimum(t, 12.0) / 12.0) ** 1.4) * (1 - smooth(12.2, 13.1, t))
    s = sub(t, 36.71, 41.2, 1.3) * a
    dark = pad(t, (36.71, 55.0, 73.42, 87.31), 120, 900, seed=3) * a[:, None] * 1.2
    men = choir_o(t, (73.42, 110.0, 146.83, 174.61), seed=5) * (smooth(3.0, 10.5, t) * a)[:, None] * 2.4
    # el cielo empieza a crujir y cruje cada vez más (más deprisa y más agudo: más tensión)
    k = smooth(0, 12.5, t) ** 1.4
    cr = cruje(t, smooth(1.0, 11.0, t) ** 1.2 * (1 - smooth(12.4, 13.1, t)), 27 + 25 * k, 0.9 + 0.4 * k, seed=31)
    rum = roll(t, (0.25 + 0.75 * smooth(0, 12, t)) * a, seed=40)
    sh = shepard(t, 0.0, speed=0.16) * (smooth(4.5, 11.5, t) * (1 - smooth(12.3, 13.0, t)))[:, None]
    wind = noise2(len(t), 6)
    for c in range(2):
        wind[:, c] = bp(lp_sweep(wind[:, c], 300, 2200, 48), 150, 3200)
    pos = np.sin(2 * np.pi * np.cumsum(0.08 + 0.22 * smooth(0, 12, t)) / SR)
    wind = wind * np.stack([0.5 - 0.35 * pos, 0.5 + 0.35 * pos], 1) * (a * 0.35)[:, None]
    x = pan(s, 0.0) * 0.45 + dark * 0.5 + men * 1.3 + cr * 1.0 + rum * 0.45 + sh * 0.85 + wind
    return hall(x, 4.0, 0.4, 2200, 11)[: len(t) + int(1.0 * SR)]


def _rompe(dur, brk, opened, notes, choir_notes, seed, dark=False):
    t = tt(dur)
    pre = 1 - smooth(brk, brk + 0.35, t)
    # lo más tenso del crujido (sigue el del preludio) y el Shepard que sigue subiendo, hasta que se parte
    k = smooth(0, brk, t)
    cr = cruje(t, ((0.75 + 0.25 * k) if not dark else smooth(0, 0.7, t)) * pre, 52 + 18 * k, 1.3 + 0.25 * k, seed=seed)
    sh = shepard(t, 0.0, speed=0.16) * (smooth(-1, 0.4, t) * pre)[:, None]
    # se parte: el braam, el grave que se hunde, el desgarro, el retumbo y el coro grande
    br = braam(t, brk - 0.1, notes, attack=0.3, hold=1.5 if not dark else 1.2, release=3.6, seed=seed + 1,
               bright=2600 if not dark else 2000, drop=0.035 if not dark else 0.05)
    s = sub(t, 58, 27, 0.6) * swell(t, brk + 0.12, 0.22, 2.8)
    rip = rasgar(t, brk + 0.05, opened, seed=seed + 2, f0=170, f1=1100)
    rum = roll(t, swell(t, brk + 0.25, 0.5, 3.2) + 0.25 * (1 - pre) * smooth(brk, opened, t), seed=seed + 3, cutoff=340)
    big = choir(t, choir_notes, seed=seed + 4) * swell(t, brk + 0.15, 0.6, 4.5)[:, None]
    hum = pad(t, (36.71, 55.0, 73.42), 160, 500, seed=seed + 6) * (smooth(brk + 1.5, opened, t) * np.exp(-np.maximum(t - opened, 0) / 2.4))[:, None]
    growl = 0
    if dark:
        # (al volver a abrirse) un gruñido de coro grave al principio
        growl = choir_o(t, (73.42, 77.78, 110.0), seed=seed + 7, vib=0.012) * (smooth(0, 0.8, t) * pre)[:, None] * 3.0
    x = cr * 0.9 + sh * 0.8 + br * 1.3 + pan(s, 0.0) * 0.55 + rip * 0.85 + rum * 0.55 + big * 2.6 + hum * 0.8 + growl
    return hall(x, 5.0, 0.45, 2400, seed + 8)


def rompe():
    return _rompe(10.0, 1.8, 5.5, DMIN, (146.83, 174.61, 220.0, 293.66, 440.0), 40)


def reabre():
    return _rompe(8.0, 1.2, 4.0, DTRI, (146.83, 174.61, 207.65, 293.66), 60, dark=True)


def _cierra(dur, flash, notes, seed, peace=False):
    t = tt(dur)
    # el braam al revés: se aspira hasta el fogonazo (sube y se abre), y ahí se va
    k = (smooth(0.0, flash, t) ** 2.2) * (1 - smooth(flash, flash + 0.3, t))
    rb = braam(t, 0.0, notes, attack=flash, hold=0.0, release=0.4, seed=seed, bright=1800, drop=-0.02, growl=0.2)
    rb = rb * (k / np.maximum(smooth(0.0, flash, t), 1e-3) * (t < flash + 0.3))[:, None]
    suck = noise2(len(t), seed + 1)
    for c in range(2):
        suck[:, c] = lp_sweep(suck[:, c], 300, 4500, 40)
    # el desgarro que se cose: de los lados al centro
    zip_ = rasgar(t, 0.0, flash, seed=seed + 2, f0=700, f1=180, inward=True)
    thoom = sub(t, 52, 33, 0.6) * swell(t, flash + 0.05, 0.14, 1.4)
    x = rb * 1.4 + suck * (k * 0.3)[:, None] + zip_ * 0.7 + pan(thoom, 0.0) * 0.55
    if peace:
        # se abre en re mayor, luminoso, con el coro en paz y unos metales cálidos
        x = x + braam(t, flash - 0.05, DMAJ, attack=0.35, hold=1.0, release=4.0, seed=seed + 3, bright=3200, drop=0.0,
                      growl=0.1, drive=1.6) * 0.8
        x = x + choir(t, (293.66, 369.99, 440.0, 587.33), seed=seed + 4, vib=0.0035) * swell(t, flash + 0.5, 0.9, 4.0)[:, None] * 2.2
        x = x + pad(t, (146.83, 220.0, 293.66, 369.99), 900, 2600, seed=seed + 5) * swell(t, flash + 0.3, 0.8, 3.5)[:, None] * 1.4
    else:
        x = x + choir(t, (146.83, 220.0, 293.66), seed=seed + 4) * swell(t, flash + 0.08, 0.3, 1.6)[:, None] * 2.4
    return hall(x, 4.5 if not peace else 5.5, 0.45, 2400 if not peace else 3200, seed + 6)


def cierra():
    return _cierra(5.0, 2.3, DOPEN, 80)


def final():
    return _cierra(8.0, 2.5, DMAJ, 100, peace=True)


def aliento():
    """El Void respira (abierta, cada 40-70 s): el cielo cruje lejos, con un retumbo grave, muy bajo."""
    dur = 6.5
    t = tt(dur)
    env = np.sin(np.pi * np.clip(t / 6.0, 0, 1)) ** 2
    cr = cruje(t, env, 26 + 6 * env, 0.75 + 0.1 * env, seed=120)
    s = sub(t, 38, 33) * env * 0.6
    rum = roll(t, env * 0.6, seed=121, cutoff=220)
    x = cr * 1.2 + pan(s, 0) + rum
    return hall(x, 3.0, 0.4, 1500, 122) * 0.45


SOUNDS = {
    'cielo/preludio': preludio,
    'cielo/rompe': rompe,
    'cielo/cierra': cierra,
    'cielo/reabre': reabre,
    'cielo/final': final,
    'cielo/aliento': aliento,
}
# (el aliento es más bajo que los demás: no se normaliza hasta arriba)
PEAK = {'cielo/aliento': 0.45}


def medir(name, x):
    """Pico, volumen (RMS) y el «golpe» más fuerte: cuánto sube el volumen en 20 ms respecto a los 60 ms de antes (dB)."""
    m = x.mean(1)
    w = int(0.02 * SR)
    blocks = len(m) // w
    rms = np.sqrt(np.mean(m[: blocks * w].reshape(blocks, w) ** 2, 1) + 1e-12)
    db = 20 * np.log10(rms)
    # (con el momento de verdad de cada uno: antes se sacaba de la lista ya filtrada y salía un tiempo que no era)
    # (sin los primeros 0,3 s: lo que arranca desde el silencio, con su subida de 0,15 s, siempre «sube» mucho)
    jumps = [(db[i] - 20 * np.log10(np.sqrt(np.mean(rms[i - 3:i] ** 2)) + 1e-12), i * 0.02) for i in range(15, blocks) if db[i] > -40]
    j, at = max(jumps) if jumps else (0, 0)
    print(f'  {name}: pico {np.max(np.abs(x)):.2f}, volumen {20 * np.log10(np.sqrt(np.mean(m ** 2)) + 1e-12):.1f} dB, '
          f'golpe más fuerte {j:.1f} dB (a los {at:.2f} s)')
    return j


def main():
    wav_dir = sys.argv[sys.argv.index('--wav') + 1] if '--wav' in sys.argv else None
    tmp = tempfile.mkdtemp()
    skip = {sys.argv[sys.argv.index('--wav') + 1]} if '--wav' in sys.argv else set()
    want = [a for a in sys.argv[1:] if not a.startswith('--') and a not in skip]
    for name, fn in SOUNDS.items():
        if want and name not in want and name.split('/')[-1] not in want:
            continue
        x = fn()
        x = np.stack([hp(x[:, c], 28, 2) for c in range(2)], 1)
        x = norm(glue(x), PEAK.get(name, 0.85))
        # (empieza en 0,15 s suaves: lo que arranca desde el silencio no es un golpe)
        fi = int(0.15 * SR)
        x[:fi] *= smooth(0, 1, np.linspace(0, 1, fi))[:, None]
        # sin la cola muda del final (por debajo de -60 dB)
        loud = np.nonzero(np.abs(x).max(1) > 0.85 * 10 ** (-60 / 20))[0]
        if len(loud):
            x = x[: min(len(x), loud[-1] + int(0.05 * SR))].copy()
            fo = int(0.3 * SR)
            x[-fo:] *= np.linspace(1, 0, fo)[:, None]
        if '--medir' in sys.argv:
            medir(name, x)
        wav = os.path.join(wav_dir or tmp, name.replace('/', '_') + '.wav')
        os.makedirs(os.path.dirname(wav), exist_ok=True)
        write_wav(wav, x)
        out = os.path.join(RES, name + '.ogg')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', '-ac', '2', out], check=True)
        print(f'{name}: {len(x) / SR:.1f} s, {os.path.getsize(out) // 1024} KB')


if __name__ == '__main__':
    main()
