"""Motor de síntesis de la banda sonora de Corazón Dormido (sin muestras: todo sale de aquí).

Instrumentos:
- substractivos (cuerdas, coro con formantes, metales, violonchelo y bajo): sierras con polyBLEP y filtros;
- aditivos (piano, campanas, celesta, marimba): parciales con su caída;
- arpa por Karplus-Strong;
- percusión sintetizada (taiko, timbal, caja de marco, platillo, latido, gotas y viento).

Mezcla en estéreo con un bus seco y otro de reverberación por convolución (la reverb de una cueva grande).
`Pieza.render()` devuelve la pieza en bucle perfecto: la cola de la reverb se suma al principio.
"""
import math

import numpy as np
from scipy import signal

SR = 44100
_rng = np.random.default_rng(1)

# ---------------------------------------------------------------- notas

_NOMBRES = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}


def midi(n):
    """'D4', 'F#3', 'Bb2' o un número MIDI."""
    if isinstance(n, (int, float)):
        return n
    base = _NOMBRES[n[0].upper()]
    k = 1
    while k < len(n) and n[k] in '#b':
        base += 1 if n[k] == '#' else -1
        k += 1
    return base + 12 * (int(n[k:]) + 1)


def hz(n):
    return 440.0 * 2 ** ((midi(n) - 69) / 12)


# ---------------------------------------------------------------- utilidades

def env_adsr(n, a, d, s, r, sr=SR):
    """Envolvente de n muestras: ataque a, caída d hasta s, y suelta r al final (dentro de n)."""
    a, d, r = int(a * sr), int(d * sr), int(r * sr)
    e = np.full(n, s, dtype=np.float64)
    a = min(a, n)
    if a:
        e[:a] = np.linspace(0, 1, a, endpoint=False) ** 1.5
    d = min(d, n - a)
    if d > 0:
        e[a:a + d] = 1 + (s - 1) * (1 - np.exp(-5 * np.linspace(0, 1, d)))
    r = min(r, n)
    if r:
        e[n - r:] *= np.linspace(1, 0, r) ** 2
    return e


def fase(f, n, vib=0.0, vibf=5.0, vibret=0.4, deriva=0.0, sem=0):
    """Fase acumulada (en ciclos) con vibrato que entra poco a poco y una deriva lenta de afinación."""
    t = np.arange(n) / SR
    finst = np.full(n, f, dtype=np.float64)
    if vib:
        entra = np.clip(t / max(vibret, 1e-3), 0, 1)
        finst *= 1 + vib * entra * np.sin(2 * np.pi * vibf * t + sem)
    if deriva:
        finst *= 1 + deriva * np.sin(2 * np.pi * 0.23 * t + sem * 1.7)
    return np.cumsum(finst) / SR, finst


def sierra(ph, finst):
    """Sierra limitada en banda (polyBLEP)."""
    p = ph % 1.0
    dt = finst / SR
    s = 2 * p - 1
    m1 = p < dt
    t1 = p[m1] / dt[m1]
    s[m1] -= t1 + t1 - t1 * t1 - 1
    m2 = p > 1 - dt
    t2 = (p[m2] - 1) / dt[m2]
    s[m2] -= t2 * t2 + t2 + t2 + 1
    return s


def pulso(ph, finst, ancho=0.5):
    return sierra(ph, finst) - sierra(ph + ancho, finst)


def lp(x, fc, orden=2):
    fc = min(fc, SR * 0.45)
    b, a = signal.butter(orden, fc / (SR / 2), 'low')
    return signal.lfilter(b, a, x)


def hp(x, fc, orden=2):
    b, a = signal.butter(orden, fc / (SR / 2), 'high')
    return signal.lfilter(b, a, x)


def bp(x, fc, q):
    b, a = signal.iirpeak(min(fc, SR * 0.45) / (SR / 2), q)
    return signal.lfilter(b, a, x)


def estante(x, fc, db, tipo='bajo'):
    """Filtro de estante (RBJ): sube o baja todo lo que está por debajo (bajo) o por encima (alto) de fc."""
    A = 10 ** (db / 40)
    w = 2 * math.pi * fc / SR
    cw, sw = math.cos(w), math.sin(w)
    al = sw / 2 * math.sqrt(2)
    s2 = 2 * math.sqrt(A) * al
    if tipo == 'bajo':
        b = [A * ((A + 1) - (A - 1) * cw + s2), 2 * A * ((A - 1) - (A + 1) * cw), A * ((A + 1) - (A - 1) * cw - s2)]
        a = [(A + 1) + (A - 1) * cw + s2, -2 * ((A - 1) + (A + 1) * cw), (A + 1) + (A - 1) * cw - s2]
    else:
        b = [A * ((A + 1) + (A - 1) * cw + s2), -2 * A * ((A - 1) + (A + 1) * cw), A * ((A + 1) + (A - 1) * cw - s2)]
        a = [(A + 1) - (A - 1) * cw + s2, 2 * ((A - 1) - (A + 1) * cw), (A + 1) - (A - 1) * cw - s2]
    return signal.lfilter(np.array(b) / a[0], np.array(a) / a[0], x)


def ruido(n, sem=None):
    g = np.random.default_rng(sem) if sem is not None else _rng
    return g.standard_normal(n)


# ---------------------------------------------------------------- instrumentos (devuelven mono, sin normalizar)

def cuerdas(f, dur, vel=0.7, ataque=0.5, suelta=1.2, brillo=1.0, voces=5, sem=0):
    """Sección de cuerdas: sierras desafinadas un poco, vibrato y filtro que se abre con la intensidad."""
    n = int((dur + suelta) * SR)
    out = np.zeros(n)
    for v in range(voces):
        det = 1 + (v - (voces - 1) / 2) * 0.0035
        ph, fi = fase(f * det, n, vib=0.0045, vibf=4.6 + v * 0.31, vibret=0.6, deriva=0.0012, sem=sem * 7 + v)
        out += sierra(ph + _rng.random(), fi)
    out = lp(out, (1600 + 4200 * vel) * brillo, 2)
    out = hp(out, 90)
    e = env_adsr(n, ataque, 0.3, 0.85, suelta)
    e[: int(dur * SR)] *= np.linspace(0.92, 1.0, int(dur * SR))
    return out * e * vel / voces


def chelo(f, dur, vel=0.7, ataque=0.25, suelta=0.6, sem=0):
    """Violonchelo solista: una sierra con vibrato amplio, cuerpo de madera (resonancias) y ruido del arco."""
    n = int((dur + suelta) * SR)
    ph, fi = fase(f, n, vib=0.007, vibf=5.2, vibret=0.35, deriva=0.001, sem=sem)
    x = sierra(ph, fi)
    x = 0.6 * lp(x, 2400) + 0.5 * bp(x, 260, 2.5) + 0.35 * bp(x, 620, 3) + 0.15 * bp(x, 1500, 4)
    x += 0.04 * bp(ruido(n), 2500, 1.5) * env_adsr(n, 0.05, 0.3, 0.4, suelta)
    return x * env_adsr(n, ataque, 0.25, 0.8, suelta) * vel


_FORMANTES = {
    'a': [(700, 1.0, 80), (1220, 0.5, 90), (2600, 0.25, 120)],
    'o': [(450, 1.0, 70), (800, 0.45, 80), (2830, 0.15, 100)],
    'u': [(325, 1.0, 50), (700, 0.3, 60), (2530, 0.1, 100)],
    'e': [(400, 1.0, 60), (2000, 0.45, 100), (2550, 0.3, 120)],
}


def coro(f, dur, vel=0.6, vocal='a', ataque=0.7, suelta=1.5, voces=6, sem=0):
    """Coro: pulsos desafinados por formantes de una vocal, con vibrato lento y aliento."""
    n = int((dur + suelta) * SR)
    src = np.zeros(n)
    for v in range(voces):
        det = 1 + (v - (voces - 1) / 2) * 0.004
        ph, fi = fase(f * det, n, vib=0.006, vibf=4.8 + v * 0.27, vibret=0.8, deriva=0.0015, sem=sem * 5 + v)
        src += pulso(ph + _rng.random(), fi, 0.35 + 0.05 * v / voces)
    src /= voces
    out = np.zeros(n)
    for fc, g, bw in _FORMANTES[vocal]:
        out += g * bp(src, fc, fc / bw)
    out += 0.22 * bp(src, 2900, 3.5)  # el formante del cantante: lo que hace que un coro se oiga claro
    out += 0.05 * bp(ruido(n), _FORMANTES[vocal][1][0], 2)  # aliento
    out = hp(out, 90)
    return out * env_adsr(n, ataque, 0.4, 0.9, suelta) * vel * 2.2


def metal(f, dur, vel=0.8, suelta=0.4, sem=0):
    """Metales (trompas y trombones): sierras con un filtro que se abre al soplar y se cierra después."""
    n = int((dur + suelta) * SR)
    x = np.zeros(n)
    for v in range(3):
        ph, fi = fase(f * (1 + (v - 1) * 0.003), n, vib=0.003, vibf=5, vibret=0.5, sem=sem + v)
        x += sierra(ph, fi)
    t = np.arange(n) / SR
    corte = 500 + 3200 * vel * (np.exp(-t * 3) * 0.7 + 0.3)
    # filtro variable: por bloques
    out = np.zeros(n)
    blq = 512
    zi = None
    for i in range(0, n, blq):
        b, a = signal.butter(2, min(corte[i], SR * 0.45) / (SR / 2), 'low')
        if zi is None:
            zi = signal.lfilter_zi(b, a) * 0
        out[i:i + blq], zi = signal.lfilter(b, a, x[i:i + blq], zi=zi)
    return out * env_adsr(n, 0.04, 0.25, 0.75, suelta) * vel / 3


def bajo(f, dur, vel=0.8, suelta=0.3):
    """Bajo de cuerda grave (pizzicato largo / contrabajo con arco suave)."""
    n = int((dur + suelta) * SR)
    ph, fi = fase(f, n, vib=0.002, vibf=4.5)
    x = 0.7 * np.sin(2 * np.pi * ph) + 0.35 * lp(sierra(ph, fi), 500)
    return x * env_adsr(n, 0.06, 0.4, 0.7, suelta) * vel


def dron(f, dur, vel=0.5, suelta=2.0, sem=0):
    """Zumbido grave que respira: dos sierras y un seno con el filtro moviéndose despacio."""
    n = int((dur + suelta) * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for v, det in enumerate([0.997, 1.003]):
        ph, fi = fase(f * det, n, deriva=0.002, sem=sem + v)
        x += sierra(ph, fi)
    ph, _ = fase(f / 2, n)
    x = lp(x, 260 + 180 * (1 + np.sin(2 * np.pi * 0.07 * t + sem)).mean()) * 0.5 + np.sin(2 * np.pi * ph) * 0.6
    mod = 0.75 + 0.25 * np.sin(2 * np.pi * 0.11 * t + sem)
    return x * mod * env_adsr(n, 2.0, 0.5, 1.0, suelta) * vel


def piano(f, dur, vel=0.7, suelta=0.8, sem=0):
    """Piano: parciales algo inarmónicos, tres cuerdas que baten, caída en dos tiempos y golpe del macillo."""
    n = int((dur + suelta) * SR)
    t = np.arange(n) / SR
    B = 0.00025
    out = np.zeros(n)
    kmax = int(min(24, 9000 / f))
    for k in range(1, kmax + 1):
        fk = k * f * math.sqrt(1 + B * k * k)
        amp = (vel ** (0.6 + k * 0.08)) / k ** 1.1
        caida = np.exp(-t * (0.9 + 0.35 * k) * (f / 260) ** 0.4) * 0.7 + np.exp(-t * (0.25 + 0.05 * k)) * 0.3
        for c, det in enumerate((-0.6, 0.0, 0.7)):
            out += amp * caida * np.sin(2 * np.pi * (fk + det * (k ** 0.5) * 0.25) * t + c + k) / 3
    golpe = lp(ruido(n), 2000 + 2000 * vel) * np.exp(-t * 90) * 0.15 * vel
    out += golpe
    e = np.ones(n)
    r = int(suelta * SR)
    e[n - r:] = np.linspace(1, 0, r) ** 2
    return out * e * 0.5


def campana(f, dur, vel=0.6, sem=0):
    """Campana de bronce: parciales inarmónicos (hum, prima, tercera menor, quinta, nominal…) que se apagan."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for r, a, d in [(0.5, 0.6, 0.35), (1.0, 1.0, 0.5), (1.183, 0.7, 0.7), (1.506, 0.5, 0.9), (2.0, 0.6, 1.1),
                    (2.514, 0.35, 1.6), (2.662, 0.3, 1.8), (3.011, 0.25, 2.2), (4.166, 0.2, 3.0), (5.433, 0.12, 4.0)]:
        out += a * np.exp(-t * d * (f / 400) ** 0.3) * np.sin(2 * np.pi * f * r * t + r * 3 + sem)
    out += 0.3 * lp(ruido(n), 4000) * np.exp(-t * 60)
    return out * vel * 0.3


def celesta(f, dur, vel=0.6, sem=0):
    """Celesta / caja de música: brillo de metal pequeño, se apaga deprisa."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = (np.sin(2 * np.pi * f * t) * np.exp(-t * 2.2) + 0.5 * np.sin(2 * np.pi * f * 4.0 * t) * np.exp(-t * 7)
           + 0.25 * np.sin(2 * np.pi * f * 2.0 * t) * np.exp(-t * 4) + 0.12 * np.sin(2 * np.pi * f * 6.3 * t) * np.exp(-t * 12))
    ataque = np.clip(t / 0.003, 0, 1)
    return out * ataque * vel * 0.5


def marimba(f, dur, vel=0.7, sem=0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.sin(2 * np.pi * f * t) * np.exp(-t * 5) + 0.35 * np.sin(2 * np.pi * f * 3.93 * t) * np.exp(-t * 14) + 0.12 * np.sin(2 * np.pi * f * 9.2 * t) * np.exp(-t * 30)
    out += 0.08 * lp(ruido(n), 3000) * np.exp(-t * 200)
    return out * np.clip(t / 0.002, 0, 1) * vel * 0.6


def arpa(f, dur, vel=0.7, brillo=0.5, sem=None):
    """Arpa / cuerda pulsada (Karplus-Strong con lfilter)."""
    n = int(dur * SR)
    N = max(2, int(round(SR / f)))
    exc = np.zeros(n)
    k = min(N, n)
    pulso_ = ruido(k, sem) * np.hanning(k)
    exc[:k] = lp(pulso_, 1500 + 6000 * brillo) * vel
    g = 0.996 - 0.0006 * (f / 440)
    a = np.zeros(N + 2)
    a[0] = 1
    a[N] = -g * 0.5
    a[N + 1] = -g * 0.5
    y = signal.lfilter([1.0], a, exc)
    t = np.arange(n) / SR
    y *= np.exp(-t * 0.6)
    return hp(y, 60) * 0.8


def flauta(f, dur, vel=0.6, ataque=0.12, suelta=0.35, sem=0):
    """Flauta de hueso: seno con un poco de armónicos, vibrato y mucho aire."""
    n = int((dur + suelta) * SR)
    ph, fi = fase(f, n, vib=0.009, vibf=5.4, vibret=0.3, deriva=0.001, sem=sem)
    x = np.sin(2 * np.pi * ph) + 0.18 * np.sin(4 * np.pi * ph) + 0.06 * np.sin(6 * np.pi * ph)
    aire = bp(ruido(n), f * 2, 1.2) * 0.22 + hp(ruido(n), 3000) * 0.03
    e = env_adsr(n, ataque, 0.2, 0.85, suelta)
    return (x + aire) * e * vel * 0.6


# ---------------------------------------------------------------- percusión

def taiko(vel=1.0, tono=1.0, dur=1.6):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = 52 * tono + 70 * tono * np.exp(-t * 18)
    cuerpo = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 3.2)
    piel = lp(ruido(n), 900) * np.exp(-t * 25) * 0.5
    golpe = hp(ruido(n), 1500) * np.exp(-t * 120) * 0.25
    return np.tanh((cuerpo + piel + golpe) * 1.6) * vel * 0.9


def timbal(vel=1.0, f=80, dur=1.5):
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = sum(a * np.sin(2 * np.pi * f * r * t) * np.exp(-t * d) for r, a, d in [(1, 1, 2.2), (1.5, 0.5, 3), (1.98, 0.3, 4), (2.44, 0.2, 5)])
    x += lp(ruido(n), 1200) * np.exp(-t * 30) * 0.3
    return x * vel * 0.6


def tambor_marco(vel=0.7, dur=0.5):
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.sin(2 * np.pi * (180 + 90 * np.exp(-t * 30)) * t) * np.exp(-t * 14) * 0.6 + bp(ruido(n), 1800, 1) * np.exp(-t * 35) * 0.6
    return x * vel


def caja(vel=0.7, dur=0.4):
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.sin(2 * np.pi * 190 * t) * np.exp(-t * 25) * 0.5 + hp(ruido(n), 1200) * np.exp(-t * 16) * 0.7
    return x * vel * 0.8


def sonaja(vel=0.4, dur=0.12):
    n = int(dur * SR)
    t = np.arange(n) / SR
    return hp(ruido(n), 5000) * np.exp(-t * 40) * vel * 0.5


def platillo(vel=0.6, dur=3.0, crece=0.0):
    """Platillo suspendido; con crece > 0 sube como un redoble (se hace un swell)."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = hp(ruido(n), 3500) * 0.5 + bp(ruido(n), 7000, 0.7) * 0.3
    e = (t / dur) ** 2.5 if crece else np.exp(-t * 1.4)
    return x * e * vel


def latido(vel=1.0):
    """El latido del Corazón: dos golpes graves (lub-dub)."""
    n = int(1.0 * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for t0, a in ((0.0, 1.0), (0.24, 0.75)):
        tt = np.clip(t - t0, 0, None)
        on = t >= t0
        out += on * a * np.sin(2 * np.pi * (48 + 30 * np.exp(-tt * 25)) * tt) * np.exp(-tt * 9)
    out += lp(ruido(n), 300) * np.exp(-t * 20) * 0.2
    return np.tanh(out * 1.4) * vel


def gota(f=None, vel=0.4):
    f = f or (900 + _rng.random() * 900)
    n = int(0.35 * SR)
    t = np.arange(n) / SR
    fi = f * (1 + 1.6 * (1 - np.exp(-t * 45)))
    return np.sin(2 * np.pi * np.cumsum(fi) / SR) * np.exp(-t * 22) * vel


def viento(dur, vel=0.3, centro=500, sem=3):
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = ruido(n, sem)
    out = np.zeros(n)
    blq = 2048
    zi = None
    for i in range(0, n, blq):
        fc = centro * (1 + 0.6 * math.sin(2 * math.pi * 0.05 * (i / SR) + sem) + 0.25 * math.sin(2 * math.pi * 0.17 * (i / SR)))
        b, a = signal.iirpeak(fc / (SR / 2), 1.6)
        if zi is None:
            zi = np.zeros(max(len(a), len(b)) - 1)
        out[i:i + blq], zi = signal.lfilter(b, a, x[i:i + blq], zi=zi)
    return out * vel * (0.7 + 0.3 * np.sin(2 * np.pi * 0.031 * t + sem))


# ---------------------------------------------------------------- reverb y mezcla

def respuesta(rt60=3.0, oscuro=5000, sem=11, pre=0.02):
    """Respuesta al impulso estéreo de una sala de piedra: primeras reflexiones y una cola que se oscurece."""
    n = int((rt60 * 1.1 + pre) * SR)
    g = np.random.default_rng(sem)
    t = np.arange(n) / SR
    ir = np.zeros((n, 2))
    caida = np.exp(-6.9 * t / rt60)
    for c in range(2):
        cola = g.standard_normal(n) * caida
        cola = lp(cola, oscuro)
        cola[: int(pre * SR)] = 0
        for k in range(8):
            d = int((pre + 0.008 + g.random() * 0.06) * SR)
            cola[d] += (g.random() * 0.8 + 0.2) * (1 if g.random() < 0.5 else -1) * 0.6
        ir[:, c] = cola
    ir /= np.sqrt((ir ** 2).sum(axis=0, keepdims=True))
    return ir


class Pieza:
    """Una pieza: se le ponen notas en segundos y se mezcla al final con su reverb."""

    def __init__(self, segundos, bpm=60, rt60=3.2, oscuro=7000, humedo=0.32, graves=-4.0, presencia=6.0):
        self.graves, self.presencia = graves, presencia
        self.L = int(segundos * SR)
        self.cola = int((rt60 + 1.5) * SR)
        n = self.L + self.cola
        self.seco = np.zeros((n, 2))
        self.envio = np.zeros((n, 2))
        self.bpm = bpm
        self.ir = respuesta(rt60, oscuro)
        self.humedo = humedo

    def tiempo(self, compas, pulso=0.0, por_compas=4):
        return (compas * por_compas + pulso) * 60.0 / self.bpm

    def poner(self, x, t, pan=0.0, gan=1.0, rev=0.35):
        """x mono en el segundo t; pan de -1 (izquierda) a 1 (derecha); rev: cuánto va a la reverb."""
        i = int(t * SR)
        if i >= self.seco.shape[0] or i < 0:
            return
        x = x[: self.seco.shape[0] - i] * gan
        izq, der = math.cos((pan + 1) * math.pi / 4), math.sin((pan + 1) * math.pi / 4)
        self.seco[i:i + len(x), 0] += x * izq
        self.seco[i:i + len(x), 1] += x * der
        if rev:
            self.envio[i:i + len(x), 0] += x * izq * rev
            self.envio[i:i + len(x), 1] += x * der * rev

    def render(self, volumen=-16.0):
        """Mezcla, reverb, bucle perfecto (la cola vuelve al principio), compresión suave y nivel en dBFS RMS."""
        hum = np.zeros_like(self.envio)
        for c in range(2):
            hum[:, c] = signal.fftconvolve(self.envio[:, c], self.ir[:, c])[: self.envio.shape[0]]
        mez = self.seco + hum * self.humedo * 3.0
        # masterizado (antes de cerrar el bucle, para que los filtros no arranquen en frío en la unión):
        # menos barro abajo, más presencia y aire arriba
        mez = hp(mez.T, 32).T
        mez = estante(mez.T, 140, self.graves).T
        mez = estante(mez.T, 2800, self.presencia, 'alto').T
        out = mez[: self.L].copy()
        cola = mez[self.L:]
        out[: len(cola)] += cola
        rms = np.sqrt((out ** 2).mean())
        out *= 10 ** (volumen / 20) / max(rms, 1e-9)
        out = np.tanh(out * 1.1) / 1.1  # limitador suave
        pico = np.abs(out).max()
        if pico > 0.98:
            out *= 0.98 / pico
        return out.astype(np.float32)


def guardar_ogg(ruta, x, calidad=0.45):
    # por bloques: libsndfile se cae si se le da todo de golpe
    import soundfile as sf
    with sf.SoundFile(ruta, 'w', SR, x.shape[1], format='OGG', subtype='VORBIS', compression_level=1 - calidad) as f:
        for i in range(0, len(x), 32768):
            f.write(x[i:i + 32768])
