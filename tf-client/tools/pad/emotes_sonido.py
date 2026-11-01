"""La música de los 5 emotes del clan (1.3.160). El dueño: «necesito más de 1 emote, mínimo 5 distintos, ninguno igual ni
música igual». Cada uno con su tono, su tempo, sus instrumentos y su sala, al ritmo de su animación (ClanEmotes):

  estandarte  Do mayor, marcha (112 ppm): redoble, el asta sube (golpe y «shing»), la tela se despliega (acorde de
              metales con timbales) y la marcha heroica con caja y bombo; acorde final con platillo.        5,0 s
  corona      Re mayor, lento: el haz de luz (coro que sube y campanitas que bajan), arrodillarse (coro grave), la corona
              baja (arpa que desciende), se levanta (crescendo) y la corona se posa (campana, gong y coro con órgano). 5,0 s
  trueno      Mi menor: carga (zumbido que sube, chispazos y taikos que se aceleran), el rayo (trueno, boom y golpe de
              metales), el ritmo de taikos con chispas eléctricas y el golpe final con retumbo.             4,6 s
  fiesta      Sol mayor, 128 ppm: confeti (dos «pop» y un matasuegras), bombo a negras, palmas, charles, bajo y la
              melodía de 8 bits; giro (barrido que sube) y el golpe final con platillo.                    5,2 s
  orbita      La menor, místico: un oleaje al revés, arpa en arpegios (Lam–Fa–Do–Sol) con un colchón que brilla, el
              barrido que sube al juntarse las insignias y el destello (campana y coro en La mayor).       5,0 s

Todo sintetizado aquí (numpy, filtros y sala con FFT), mono (suena en el sitio del jugador), pico a −1 dB.
Uso: python3 tools/pad/emotes_sonido.py → assets/tfclient/sounds/clanes/emote_<nombre>.ogg (con ffmpeg)."""
import os
import subprocess
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
DIR = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'sounds', 'clanes'))
SR = 44100


def note(n):
    return 440.0 * 2 ** ((n - 69) / 12)


# ---------------------------------------------------------------------------------------------------------------
# Filtros (con FFT: sin bucles por muestra)
# ---------------------------------------------------------------------------------------------------------------

def lp(x, fc, order=2):
    if len(x) == 0:
        return x
    n = 1 << int(np.ceil(np.log2(len(x) + 1)))
    X = np.fft.rfft(x, n)
    f = np.fft.rfftfreq(n, 1 / SR)
    X *= 1 / (1 + (f / max(fc, 1)) ** 2) ** (order / 2)
    return np.fft.irfft(X, n)[:len(x)]


def hp(x, fc, order=2):
    return x - lp(x, fc, order)


def bp(x, lo, hi):
    return lp(hp(x, lo), hi)


def conv(a, b):
    n = 1 << int(np.ceil(np.log2(len(a) + len(b))))
    return np.fft.irfft(np.fft.rfft(a, n) * np.fft.rfft(b, n), n)[:len(a)]


# ---------------------------------------------------------------------------------------------------------------
# Instrumentos
# ---------------------------------------------------------------------------------------------------------------

class Track:
    def __init__(self, dur, seed):
        self.dur = dur
        self.mix = np.zeros(int(SR * (dur + 1.5)))
        self.rng = np.random.default_rng(seed)

    def add(self, sig, at, gain=1.0):
        i = int(at * SR)
        if i >= len(self.mix):
            return
        j = min(len(self.mix), i + len(sig))
        self.mix[i:j] += sig[:j - i] * gain

    def noise(self, n):
        return self.rng.standard_normal(n)

    def render(self, name, room, wet):
        """La sala (ruido que se apaga en room segundos), el final suave y el pico a −1 dB."""
        ir_n = int(room * SR)
        t = np.arange(ir_n) / SR
        ir = self.noise(ir_n) * np.exp(-6.9 * t / room)
        ir = lp(ir, 6000)
        ir[0] = 1.0
        ir /= np.sqrt(np.sum(ir ** 2))
        out = (1 - wet * 0.4) * self.mix + wet * conv(self.mix, ir)
        end = int((self.dur + 0.2) * SR)
        out = out[:end]
        fade = int(0.3 * SR)
        out[-fade:] *= np.linspace(1, 0, fade) ** 1.5
        out -= np.mean(out)
        out /= np.max(np.abs(out)) / 10 ** (-1 / 20)
        wav = os.path.join(DIR, 'emote_' + name + '.wav')
        ogg = os.path.join(DIR, 'emote_' + name + '.ogg')
        with wave.open(wav, 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((out * 32767).astype('<i2').tobytes())
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', ogg], check=True)
        os.remove(wav)
        print(ogg, round(len(out) / SR, 2), 's')


def env(n, a, d, s, r, hold):
    t = np.arange(n) / SR
    e = np.where(t < a, t / max(a, 1e-4), np.where(t < a + d, 1 - (1 - s) * (t - a) / max(d, 1e-4), s))
    rel = np.clip((t - hold) / max(r, 1e-4), 0, 1)
    return e * (1 - rel)


def saw_ph(ph):
    return 2 * (ph % 1) - 1


def brass(midi, length, gain=0.3, bright=1.0, vib=0.004):
    n = int((length + 0.35) * SR)
    t = np.arange(n) / SR
    f = note(midi) * (1 + vib * np.sin(2 * np.pi * 5.2 * t) * np.clip(t / 0.3, 0, 1))
    ph = np.cumsum(f) / SR
    x = saw_ph(ph) + 0.6 * saw_ph(ph * 1.004) + 0.3 * np.sin(2 * np.pi * ph * 0.5)
    e = env(n, 0.035, 0.15, 0.78, 0.25, length)
    dark, br = lp(x, 900), lp(x, 900 + 3600 * bright)
    w = np.clip(e * 1.2, 0, 1)
    return (dark * (1 - w) + br * w) * e * gain


def strings(midis, length, gain=0.1, cut=2200, attack=0.4):
    n = int((length + 0.6) * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for m in midis:
        for det in (-0.005, 0.0, 0.006):
            f = note(m) * (1 + det) * (1 + 0.003 * np.sin(2 * np.pi * (4.5 + det * 100) * t))
            s += saw_ph(np.cumsum(f) / SR)
    e = env(n, attack, 0.3, 0.85, 0.55, length)
    return lp(s, cut) * e * gain / len(midis)


def choir(midis, length, gain=0.1, attack=0.35, cut=2600):
    n = int((length + 0.6) * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for m in midis:
        for det in (-0.006, 0.0, 0.007):
            f = note(m) * (1 + det)
            ph = 2 * np.pi * f * t + 0.5 * np.sin(2 * np.pi * 5.0 * t + det * 300)
            s += np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.18 * np.sin(3 * ph) + 0.08 * np.sin(5 * ph)
    # «aa»: un formante hacia los 800 y otro a los 1200 Hz
    s = lp(s, cut) + 0.6 * bp(s, 650, 1300)
    e = env(n, attack, 0.25, 0.85, 0.6, length)
    return s * e * gain / len(midis)


def organ(midis, length, gain=0.08):
    n = int((length + 0.4) * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for m in midis:
        f = note(m)
        for h, a in ((1, 1), (2, 0.6), (3, 0.35), (4, 0.3), (6, 0.15), (8, 0.12)):
            s += a * np.sin(2 * np.pi * f * h * t)
    e = env(n, 0.06, 0.1, 0.9, 0.35, length)
    return s * e * gain / len(midis)


def bell(freq, length=1.8, gain=0.15):
    n = int(length * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for m, a, d in ((1, 1, 1.3), (2.76, 0.5, 2.4), (5.4, 0.25, 4.2), (8.93, 0.12, 6.5), (0.5, 0.2, 0.9)):
        s += a * np.sin(2 * np.pi * freq * m * t) * np.exp(-d * t)
    return s * gain * np.clip(t / 0.002, 0, 1)


def harp(midi, length=1.6, gain=0.18):
    n = int(length * SR)
    t = np.arange(n) / SR
    f = note(midi)
    s = np.zeros(n)
    for k in range(1, 9):
        if f * k > 12000:
            break
        s += (1 / k ** 1.25) * np.sin(2 * np.pi * f * k * t + k) * np.exp(-(1.6 + 1.3 * k) * t)
    return s * gain * np.clip(t / 0.0015, 0, 1)


def square(midi, length, gain=0.12, duty=0.5, vib=0.0, glide=0.0):
    n = int((length + 0.08) * SR)
    t = np.arange(n) / SR
    f = note(midi) * (1 + vib * np.sin(2 * np.pi * 6 * t)) * (1 + glide * np.clip(1 - t / 0.06, 0, 1))
    ph = np.cumsum(f) / SR % 1
    x = np.where(ph < duty, 1.0, -1.0)
    e = env(n, 0.004, 0.08, 0.7, 0.06, length)
    return lp(x, 7000) * e * gain


def bass(midi, length, gain=0.35, cut=700):
    n = int((length + 0.1) * SR)
    t = np.arange(n) / SR
    f = note(midi)
    ph = f * t
    x = 0.7 * saw_ph(ph) + 0.8 * np.sin(2 * np.pi * ph)
    e = env(n, 0.005, 0.12, 0.65, 0.08, length)
    return lp(x, cut) * e * gain


def kick(gain=0.9, length=0.45, f0=150, f1=45):
    n = int(length * SR)
    t = np.arange(n) / SR
    f = f1 + (f0 - f1) * np.exp(-28 * t)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-7 * t)
    return s * gain


def timpani(midi=40, length=1.4, gain=0.6):
    n = int(length * SR)
    t = np.arange(n) / SR
    f = note(midi) * (1 + 0.15 * np.exp(-20 * t))
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.4 * np.sin(2 * np.pi * np.cumsum(f * 1.5) / SR)
    return s * np.exp(-2.6 * t) * gain


def snare(tr, gain=0.35, length=0.25):
    n = int(length * SR)
    t = np.arange(n) / SR
    x = hp(tr.noise(n), 1200) * np.exp(-16 * t)
    x += 0.5 * np.sin(2 * np.pi * 185 * t) * np.exp(-25 * t)
    return x * gain


def snare_roll(tr, length, gain=0.2, rate=26):
    n = int(length * SR)
    t = np.arange(n) / SR
    x = hp(tr.noise(n), 1000)
    hits = 0.5 + 0.5 * np.cos(2 * np.pi * rate * t)
    return x * hits * (t / length) ** 1.5 * gain


def clap(tr, gain=0.4):
    n = int(0.22 * SR)
    t = np.arange(n) / SR
    x = bp(tr.noise(n), 900, 3200)
    e = np.zeros(n)
    for off in (0.0, 0.011, 0.022):
        e += np.where(t >= off, np.exp(-90 * (t - off)), 0) * (1 if off < 0.02 else 0)
    e += np.where(t >= 0.03, np.exp(-18 * (t - 0.03)), 0)
    return x * e * gain


def hat(tr, gain=0.12, length=0.07):
    n = int(length * SR)
    t = np.arange(n) / SR
    return hp(tr.noise(n), 7500) * np.exp(-60 * t) * gain


def cymbal(tr, length=2.2, gain=0.22):
    n = int(length * SR)
    t = np.arange(n) / SR
    return hp(tr.noise(n), 5000) * np.exp(-2.0 * t) * gain * np.clip(t / 0.004, 0, 1)


def riser(tr, length, gain=0.25, f0=300, f1=6000):
    n = int(length * SR)
    t = np.arange(n) / SR
    x = tr.noise(n)
    # se reparte en trozos con su filtro (el corte sube)
    out = np.zeros(n)
    k = 12
    for i in range(k):
        a, b = i * n // k, (i + 1) * n // k
        fc = f0 * (f1 / f0) ** ((i + 0.5) / k)
        out[a:b] = lp(x, fc)[a:b]
    return out * (t / length) ** 1.6 * gain


def taiko(tr, gain=0.8, length=0.9, f=70):
    n = int(length * SR)
    t = np.arange(n) / SR
    fr = f * (1 + 0.6 * np.exp(-25 * t))
    s = np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.exp(-4.5 * t)
    s += 0.35 * lp(tr.noise(n), 900) * np.exp(-30 * t)
    return s * gain


def rim(tr, gain=0.25):
    n = int(0.08 * SR)
    t = np.arange(n) / SR
    return (bp(tr.noise(n), 1800, 5000) * 0.6 + np.sin(2 * np.pi * 1700 * t)) * np.exp(-55 * t) * gain


def thunder(tr, length=2.6, gain=0.9):
    n = int(length * SR)
    t = np.arange(n) / SR
    x = tr.noise(n)
    crack = hp(x, 2500) * np.exp(-22 * t)
    rumble = lp(x, 220) * (np.exp(-1.3 * t)) * (1 + 0.6 * np.sin(2 * np.pi * 3.1 * t) ** 2)
    boom = np.sin(2 * np.pi * np.cumsum(38 + 40 * np.exp(-6 * t)) / SR) * np.exp(-2.2 * t)
    return (1.2 * crack + 1.1 * rumble + 0.6 * boom) * gain


def zap(tr, gain=0.18, length=0.16, f0=1400, f1=180):
    n = int(length * SR)
    t = np.arange(n) / SR
    fc = f1 + (f0 - f1) * np.exp(-18 * t)
    mod = np.sin(2 * np.pi * np.cumsum(fc * 2.01) / SR) * 6
    s = np.sin(2 * np.pi * np.cumsum(fc) / SR + mod)
    s += 0.4 * hp(tr.noise(n), 3000)
    return s * np.exp(-14 * t) * gain


def gong(freq=90, length=3.0, gain=0.35):
    n = int(length * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for m, a, d in ((1, 1, 0.9), (1.47, 0.6, 1.2), (2.09, 0.5, 1.5), (2.56, 0.35, 1.9), (3.37, 0.25, 2.5), (4.2, 0.15, 3)):
        s += a * np.sin(2 * np.pi * freq * m * t * (1 + 0.002 * np.sin(2 * np.pi * 0.7 * t))) * np.exp(-d * t)
    return s * gain * np.clip(t / 0.01, 0, 1)


def shing(tr, length=0.9, gain=0.25):
    """Metálico que sube (el asta que sale)."""
    n = int(length * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for m in (1, 1.51, 2.23, 3.1):
        f = 1200 * m * (1 + 0.7 * t / length)
        s += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-3.5 * t)
    s += 0.5 * hp(tr.noise(n), 6000) * np.exp(-8 * t)
    return s * gain / 3


def pop(tr, gain=0.5):
    n = int(0.12 * SR)
    t = np.arange(n) / SR
    s = np.sin(2 * np.pi * np.cumsum(700 * np.exp(-12 * t) + 120) / SR) * np.exp(-30 * t)
    s += bp(tr.noise(n), 800, 4000) * np.exp(-45 * t)
    return s * gain


def party_horn(midi, length=0.55, gain=0.16):
    n = int(length * SR)
    t = np.arange(n) / SR
    f = note(midi) * (1 + 0.35 * np.clip(t / 0.18, 0, 1)) * (1 + 0.01 * np.sin(2 * np.pi * 9 * t))
    ph = np.cumsum(f) / SR
    x = saw_ph(ph) + 0.5 * np.where(ph % 1 < 0.3, 1, -1)
    x = bp(x, 400, 2600)
    return x * env(n, 0.02, 0.1, 0.8, 0.12, length - 0.12) * gain


def glide(m0, m1, length, gain=0.12):
    """Un barrido de tono (seno con armónicos) de m0 a m1."""
    n = int(length * SR)
    t = np.arange(n) / SR
    mm = m0 + (m1 - m0) * (t / length) ** 1.5
    f = 440.0 * 2 ** ((mm - 69) / 12)
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.15 * np.sin(3 * ph)
    return s * env(n, 0.05, 0.1, 0.9, 0.15, length - 0.15) * gain


# ---------------------------------------------------------------------------------------------------------------
# 1. Estandarte (Do mayor, marcha)
# ---------------------------------------------------------------------------------------------------------------

def estandarte():
    tr = Track(5.0, 1601)
    tr.add(snare_roll(tr, 0.5, 0.22), 0.0)
    tr.add(riser(tr, 0.5, 0.18, 400, 5000), 0.0)
    # 0,5: el asta sube
    tr.add(kick(1.0, 0.6, 120, 38), 0.5)
    tr.add(shing(tr, 1.0, 0.35), 0.5)
    tr.add(cymbal(tr, 2.0, 0.16), 0.5)
    for i, at in enumerate((0.62, 0.74, 0.86)):
        tr.add(brass(67, 0.08, 0.2, 1.1), at)
    # 1,0: la tela se despliega
    for m in (48, 60, 64, 67, 72):
        tr.add(brass(m, 0.5, 0.16 if m > 50 else 0.2, 1.2), 1.0)
    for i in range(10):
        tr.add(timpani(36, 0.4, 0.18), 1.0 + i * 0.05)
    tr.add(timpani(36, 1.4, 0.6), 1.5)
    # 1,55 – 4,3: la marcha (112 ppm)
    beat = 60 / 112
    melody = [(72, 1), (76, 1), (79, 2), (81, 1), (79, 1), (76, 2), (77, 1), (81, 1), (84, 2), (83, 1.5), (84, 2.5)]
    at = 1.55
    for m, b in melody:
        tr.add(brass(m, b * beat * 0.92, 0.17, 1.0), at)
        tr.add(brass(m - 12, b * beat * 0.92, 0.07, 0.7), at)
        at += b * beat * 0.5
    chords = [((48, 55, 64), 1.55), ((53, 57, 65), 2.09), ((55, 59, 62), 2.63), ((53, 57, 65), 3.17), ((55, 59, 65), 3.44), ((48, 55, 64), 3.71)]
    for ms, at in chords:
        tr.add(strings(ms, 0.5, 0.12, 2400, 0.05), at)
    t = 1.55
    i = 0
    while t < 4.25:
        tr.add(kick(0.55, 0.3), t) if i % 2 == 0 else tr.add(snare(tr, 0.3), t)
        tr.add(snare(tr, 0.12, 0.12), t + beat * 0.25 * 0.5 + beat * 0.25) if i % 2 == 1 else None
        t += beat * 0.5
        i += 1
    # 4,3: el acorde final
    for m in (36, 48, 60, 64, 67, 72, 76):
        tr.add(brass(m, 0.55, 0.13 if m > 40 else 0.2, 1.2), 4.3)
    tr.add(cymbal(tr, 1.4, 0.2), 4.3)
    tr.add(timpani(36, 1.0, 0.6), 4.3)
    tr.render('estandarte', 1.3, 0.35)


# ---------------------------------------------------------------------------------------------------------------
# 2. Corona (Re mayor, lento, coro y campanas)
# ---------------------------------------------------------------------------------------------------------------

def corona():
    tr = Track(5.0, 1602)
    # 0 – 0,6: el haz de luz
    rev = cymbal(tr, 0.9, 0.2)[::-1]
    tr.add(rev, 0.0)
    tr.add(choir((74, 78, 81), 0.9, 0.09, 0.6, 3500), 0.0)
    for i, m in enumerate((98, 95, 93, 90, 86, 83)):
        tr.add(bell(note(m), 1.2, 0.05), 0.05 + i * 0.09)
    # 0,4 – 1,0: arrodillarse
    tr.add(choir((50, 57), 1.6, 0.12, 0.5, 1800), 0.4)
    # 0,8 – 2,2: la corona baja (arpa que desciende)
    arp = [86, 81, 78, 74, 69, 66, 62, 57, 62, 66, 69, 74]
    for i, m in enumerate(arp):
        tr.add(harp(m, 1.4, 0.14), 0.8 + i * 0.11)
    # 2,0 – 2,6: se levanta
    tr.add(choir((62, 66, 69), 0.7, 0.1, 0.5, 3000), 2.0)
    tr.add(riser(tr, 0.6, 0.08, 800, 7000), 2.0)
    # 2,6: la corona se posa
    tr.add(bell(note(86), 2.4, 0.22), 2.6)
    tr.add(bell(note(93), 2.0, 0.12), 2.6)
    tr.add(gong(73, 2.4, 0.32), 2.6)
    tr.add(choir((50, 62, 66, 69, 74), 1.9, 0.16, 0.08, 3600), 2.6)
    tr.add(organ((38, 50, 57, 62, 66), 1.8, 0.1), 2.6)
    # 2,6 – 4,4: la melodía de campanas
    for m, at in ((81, 2.95), (78, 3.25), (86, 3.55), (85, 3.85), (86, 4.15)):
        tr.add(bell(note(m), 1.6, 0.1), at)
        tr.add(harp(m - 12, 1.2, 0.08), at)
    tr.add(choir((55, 62, 67, 71), 0.7, 0.07, 0.2, 2800), 3.6)
    tr.add(choir((50, 62, 66, 69, 74), 0.9, 0.08, 0.2, 2800), 4.2)
    tr.render('corona', 2.6, 0.55)


# ---------------------------------------------------------------------------------------------------------------
# 3. Trueno (Mi menor, taikos y tormenta)
# ---------------------------------------------------------------------------------------------------------------

def trueno():
    tr = Track(4.6, 1603)
    # 0 – 1,0: la carga
    tr.add(glide(40, 52, 1.15, 0.16), 0.0)
    tr.add(riser(tr, 1.1, 0.16, 200, 4000), 0.0)
    for at in (0.0, 0.42, 0.66, 0.82, 0.92, 0.99, 1.05):
        tr.add(taiko(tr, 0.55, 0.6, 62), at)
    for k in range(7):
        tr.add(zap(tr, 0.08, 0.1, 2000 + k * 200, 300), 0.15 + k * 0.12 + tr.rng.uniform(-0.02, 0.02))
    # 1,15: el rayo
    tr.add(thunder(tr, 3.0, 0.75), 1.15)
    tr.add(kick(0.6, 0.9, 110, 34), 1.15)
    for m in (40, 52, 55, 59, 64):
        tr.add(brass(m, 0.35, 0.2 if m > 45 else 0.25, 1.5, 0.0), 1.17)
    tr.add(cymbal(tr, 1.6, 0.22), 1.15)
    # 1,5 – 3,8: el ritmo (taikos dobles y chispas)
    beat = 0.3
    t = 1.6
    i = 0
    pattern = [1, 0, 1, 1, 0, 1, 0, 1]
    while t < 3.75:
        if pattern[i % len(pattern)]:
            tr.add(taiko(tr, 0.34 if i % 4 == 0 else 0.22, 0.5, 66 if i % 4 == 0 else 84), t)
        else:
            tr.add(rim(tr, 0.2), t)
        if i % 3 == 1:
            tr.add(zap(tr, 0.1, 0.12, 2400, 400), t + 0.07)
        t += beat / 2
        i += 1
    ost = [52, 55, 57, 55, 52, 55, 59, 57]
    for k, m in enumerate(ost):
        tr.add(brass(m, 0.22, 0.16, 1.0, 0.0), 1.6 + k * 0.28)
        tr.add(brass(m - 12, 0.22, 0.1, 0.6, 0.0), 1.6 + k * 0.28)
    tr.add(strings((40, 47, 52, 55), 2.2, 0.08, 1400, 0.3), 1.6)
    # 3,8: el golpe final
    tr.add(taiko(tr, 0.9, 1.2, 55), 3.8)
    tr.add(thunder(tr, 1.4, 0.35), 3.82)
    for m in (40, 52, 55, 59, 64):
        tr.add(brass(m, 0.4, 0.17, 1.3, 0.0), 3.8)
    tr.render('trueno', 1.0, 0.3)


# ---------------------------------------------------------------------------------------------------------------
# 4. Fiesta (Sol mayor, 128 ppm, 8 bits)
# ---------------------------------------------------------------------------------------------------------------

def fiesta():
    tr = Track(5.2, 1604)
    tr.add(pop(tr, 0.6), 0.2)
    tr.add(pop(tr, 0.55), 0.26)
    tr.add(party_horn(67, 0.55, 0.14), 0.24)
    beat = 60 / 128
    start = 0.3
    # bombo, palmas y charles
    for b in range(9):
        at = start + b * beat
        if at > 4.8:
            break
        tr.add(kick(0.42, 0.3, 140, 52), at)
        if b % 2 == 1:
            tr.add(clap(tr, 0.38), at)
        tr.add(hat(tr, 0.1), at + beat / 2)
    # bajo
    bl = [43, 43, 47, 48, 50, 50, 48, 47, 45, 45, 47, 50, 43, 43, 47, 50]
    for k, m in enumerate(bl):
        at = start + k * beat / 2
        if at > 4.7:
            break
        tr.add(bass(m, beat / 2 * 0.85, 0.2, 1100), at)
    # la melodía de 8 bits
    mel = [(79, 0.5), (83, 0.5), (86, 1), (83, 0.5), (81, 0.5), (79, 1), (76, 0.5), (79, 0.5), (81, 1), (86, 0.5), (88, 0.5), (86, 1)]
    at = start
    for m, b in mel:
        tr.add(square(m, b * beat * 0.85, 0.13, 0.25, 0.006, 0.02), at)
        tr.add(square(m + 12, b * beat * 0.6, 0.045, 0.5), at)
        tr.add(square(m - 12, b * beat * 0.7, 0.04, 0.5), at)
        at += b * beat
    # 3,6 – 4,8: el giro
    tr.add(riser(tr, 1.1, 0.14, 500, 9000), 3.7)
    for k in range(8):
        tr.add(square(79 + k * 2, 0.06, 0.05, 0.25), 3.9 + k * 0.1)
    # 4,8: el golpe final
    tr.add(kick(1.0, 0.5, 150, 45), 4.8)
    tr.add(cymbal(tr, 1.2, 0.2), 4.8)
    for m in (55, 67, 71, 74, 79):
        tr.add(square(m, 0.35, 0.05, 0.5), 4.8)
    tr.add(strings((55, 59, 62, 67), 0.4, 0.08, 4000, 0.01), 4.8)
    tr.render('fiesta', 0.6, 0.18)


# ---------------------------------------------------------------------------------------------------------------
# 5. Órbita (La menor, arpa y colchón que brilla)
# ---------------------------------------------------------------------------------------------------------------

def orbita():
    tr = Track(5.0, 1605)
    # 0 – 0,8: oleaje al revés y el colchón
    swell = (bell(note(69), 1.0, 0.3) + cymbal(tr, 1.0, 0.15))[::-1]
    tr.add(swell, 0.0)
    tr.add(strings((45, 57, 60, 64), 3.4, 0.07, 1800, 0.8), 0.2)
    tr.add(choir((69, 72, 76), 3.0, 0.05, 1.0, 4500), 0.5)
    # 0,4 – 3,6: los arpegios (Lam – Fa – Do – Sol)
    chords = [(57, 60, 64, 69), (53, 57, 60, 65), (48, 55, 60, 64), (55, 59, 62, 67)]
    step = 0.1
    t = 0.45
    k = 0
    while t < 3.6:
        ch = chords[int((t - 0.45) / 0.8) % 4]
        order = [0, 1, 2, 3, 2, 1, 3, 2]
        m = ch[order[k % 8]] + 12
        tr.add(harp(m, 1.3, 0.13), t)
        if k % 4 == 0:
            tr.add(harp(ch[0], 1.6, 0.11), t)
        t += step
        k += 1
    for i in range(12):
        tr.add(bell(note(88 + int(tr.rng.integers(0, 5)) * 2), 1.0, 0.035), 0.7 + i * 0.24 + tr.rng.uniform(-0.03, 0.03))
    tr.add(riser(tr, 3.0, 0.05, 1500, 9000), 0.6)
    # 3,6 – 4,2: se juntan (barrido que sube)
    tr.add(glide(57, 81, 0.6, 0.1), 3.6)
    tr.add(riser(tr, 0.6, 0.14, 600, 10000), 3.6)
    # 4,2: el destello
    tr.add(bell(note(81), 2.0, 0.25), 4.2)
    tr.add(bell(note(88), 1.6, 0.14), 4.21)
    tr.add(choir((57, 61, 64, 69, 73), 0.9, 0.14, 0.03, 4000), 4.2)
    tr.add(gong(55, 1.6, 0.18), 4.2)
    tr.render('orbita', 2.2, 0.5)


# ---------------------------------------------------------------------------------------------------------------
# (1.3.160, el dueño: «mete muchos más emotes») los diez de la tienda del clan
# ---------------------------------------------------------------------------------------------------------------

def dist(x, drive=4.0):
    return np.tanh(x * drive) / np.tanh(drive)


def power_chord(root, length, gain=0.16):
    n = int((length + 0.2) * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for m in (root, root + 7, root + 12):
        for det in (-0.004, 0.004):
            s += saw_ph(note(m) * (1 + det) * t)
    s = dist(lp(s, 3200) * 0.6, 5.0)
    return lp(s, 4200) * env(n, 0.005, 0.2, 0.75, 0.15, length) * gain


def wind(tr, length, gain=0.2, f0=400, f1=1500, rate=0.8):
    n = int(length * SR)
    t = np.arange(n) / SR
    x = tr.noise(n)
    a = bp(x, f0, f1)
    b = bp(x, f0 * 1.8, f1 * 2.2)
    m = 0.5 + 0.5 * np.sin(2 * np.pi * rate * t)
    return (a * m + b * (1 - m)) * gain * np.clip(t / 0.3, 0, 1)


def kiss(gain=0.3):
    n = int(0.18 * SR)
    t = np.arange(n) / SR
    f = 900 + 1200 * t / 0.18
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-14 * t) * gain


def fenix():
    tr = Track(5.0, 1606)
    tr.add(strings((38, 45, 50), 0.6, 0.08, 1200, 0.3), 0.0)
    tr.add(riser(tr, 0.5, 0.12, 300, 3000), 0.0)
    # 0,5: se abren las alas (llamarada) y el acorde
    tr.add(riser(tr, 0.35, 0.3, 2000, 9000)[::-1], 0.5)
    tr.add(kick(0.7, 0.6, 110, 40), 0.5)
    for m in (38, 50, 53, 57, 62):
        tr.add(brass(m, 0.6, 0.14, 1.1), 0.52)
    # los aleteos (cada 1,26 s)
    for k in range(4):
        tr.add(wind(tr, 0.55, 0.2, 300, 1800, 2.0) * np.hanning(int(0.55 * SR)), 0.9 + k * 1.26)
    # la melodía que vuela (cuerdas agudas y coro)
    mel = [(74, 0.4), (77, 0.4), (81, 0.8), (84, 0.4), (82, 0.4), (81, 0.8), (86, 1.0)]
    at = 1.0
    for m, ln in mel:
        tr.add(strings((m,), ln, 0.12, 4200, 0.05), at)
        at += ln
    tr.add(choir((50, 57, 62, 65), 3.2, 0.08, 0.6, 3000), 1.0)
    # 4,4: re mayor
    for m in (38, 50, 54, 57, 62, 66):
        tr.add(brass(m, 0.55, 0.12, 1.2), 4.4)
    tr.add(cymbal(tr, 1.2, 0.18), 4.4)
    tr.render('fenix', 1.6, 0.4)


def tornado():
    tr = Track(4.8, 1607)
    tr.add(wind(tr, 4.4, 0.28, 250, 1400, 0.9), 0.1)
    beat = 60 / 140
    pattern = [55, 58, 62, 58, 55, 58, 63, 58]
    t = 0.4
    k = 0
    while t < 3.8:
        up = int((t - 0.4) / 0.9)
        tr.add(strings((pattern[k % 8] + up,), beat / 4 * 0.9, 0.09, 3800, 0.005), t)
        t += beat / 4
        k += 1
    tr.add(strings((43, 50), 3.4, 0.07, 900, 0.5), 0.4)
    tr.add(riser(tr, 3.3, 0.1, 400, 7000), 0.5)
    for at in (0.4, 1.3, 2.2, 3.0, 3.4):
        tr.add(taiko(tr, 0.3, 0.4, 80), at)
    # 3,85: se deshace
    tr.add(cymbal(tr, 1.0, 0.22), 3.85)
    for m in (43, 55, 59, 62, 67):
        tr.add(brass(m, 0.5, 0.13, 1.2), 3.85)
    tr.render('tornado', 1.0, 0.3)


def ventisca():
    tr = Track(5.0, 1608)
    tr.add(wind(tr, 4.8, 0.08, 1500, 5000, 0.4), 0.0)
    tr.add(strings((71, 74, 78), 4.3, 0.05, 6000, 0.8), 0.2)
    # los cristales que crecen (tintineos)
    for i in range(9):
        tr.add(bell(note(95 + (i * 5) % 12), 0.8, 0.05), 0.45 + i * 0.09)
    mel = [(83, 0.5), (86, 0.5), (90, 1.0), (88, 0.5), (86, 0.5), (83, 1.0)]
    at = 0.5
    for m, ln in mel:
        tr.add(bell(note(m), 1.5, 0.12), at)
        tr.add(harp(m - 12, 1.2, 0.06), at)
        at += ln * 0.68
    # 2,5: el marco de hielo
    for m in (71, 75, 78, 83, 85):
        tr.add(bell(note(m), 2.2, 0.1), 2.5)
    tr.add(choir((59, 66, 71, 75), 1.8, 0.06, 0.2, 5000), 2.5)
    # 4,4: el hielo se rompe
    for i in range(10):
        tr.add(hp(tr.noise(int(0.12 * SR)), 5000) * np.exp(-30 * np.arange(int(0.12 * SR)) / SR) * 0.2, 4.4 + i * 0.03)
    for i, m in enumerate((95, 90, 86, 83, 78)):
        tr.add(bell(note(m), 0.9, 0.08), 4.42 + i * 0.06)
    tr.render('ventisca', 2.4, 0.55)


def meteoro():
    tr = Track(5.0, 1609)
    tr.add(strings((36, 43), 2.0, 0.09, 600, 0.5), 0.0)
    for at in (0.0, 0.6, 1.0, 1.3, 1.55, 1.75, 1.9):
        tr.add(timpani(36, 0.6, 0.35), at)
    tr.add(glide(72, 100, 1.4, 0.07), 0.6)
    tr.add(riser(tr, 1.4, 0.18, 150, 4000), 0.6)
    # 2,0: el golpe
    tr.add(kick(1.0, 1.0, 100, 28), 2.0)
    tr.add(thunder(tr, 2.4, 0.7), 2.0)
    for m in (36, 48, 51, 55):
        tr.add(brass(m, 0.4, 0.2, 1.5, 0.0), 2.02)
    tr.add(cymbal(tr, 1.8, 0.2), 2.0)
    # 2,35: la fanfarria (do mayor)
    fan = [(60, 0.18), (64, 0.18), (67, 0.18), (72, 0.7), (71, 0.3), (72, 0.9)]
    at = 2.4
    for m, ln in fan:
        tr.add(brass(m, ln * 0.9, 0.16, 1.1), at)
        tr.add(brass(m - 12, ln * 0.9, 0.08, 0.8), at)
        at += ln
    tr.add(strings((48, 55, 64, 67), 2.2, 0.08, 3000, 0.1), 2.4)
    tr.add(timpani(36, 1.2, 0.5), 4.3)
    tr.render('meteoro', 1.4, 0.35)


def fuegos():
    tr = Track(5.2, 1610)
    launches = [0.5, 0.9, 1.3, 1.7, 2.1, 2.5]
    for i, t0 in enumerate(launches):
        tr.add(glide(84 + i, 100 + i, 0.85, 0.05), t0)
        tr.add(pop(tr, 0.55), t0 + 0.9)
        tr.add(kick(0.45, 0.4, 90, 40), t0 + 0.9)
        n = int(0.9 * SR)
        crackle = (tr.rng.random(n) > 0.995).astype(float) * tr.rng.standard_normal(n)
        tr.add(hp(crackle, 3000) * np.exp(-2.5 * np.arange(n) / SR) * 0.8, t0 + 0.95)
    mel = [(70, 0.25), (74, 0.25), (77, 0.5), (82, 0.5), (81, 0.25), (79, 0.25), (77, 0.5), (74, 0.5), (77, 1.0)]
    at = 0.5
    for m, ln in mel:
        tr.add(bell(note(m + 12), 0.8, 0.07), at)
        tr.add(brass(m, ln * 0.85, 0.08, 0.9), at)
        at += ln
    # 3,3: el gran final
    tr.add(kick(0.9, 0.8, 100, 32), 3.3)
    tr.add(cymbal(tr, 1.6, 0.22), 3.3)
    for m in (46, 58, 62, 65, 70, 74):
        tr.add(brass(m, 0.8, 0.12, 1.2), 3.3)
    n = int(1.5 * SR)
    crackle = (tr.rng.random(n) > 0.99).astype(float) * tr.rng.standard_normal(n)
    tr.add(hp(crackle, 2500) * np.exp(-1.5 * np.arange(n) / SR) * 0.9, 3.35)
    tr.render('fuegos', 1.2, 0.3)


def espadas():
    tr = Track(5.0, 1611)
    beat = 0.6
    for k in range(8):
        tr.add(taiko(tr, 0.45 if k % 2 == 0 else 0.3, 0.7, 55), 0.0 + k * beat)
    tr.add(choir((45, 52, 57), 2.2, 0.12, 0.6, 1600), 0.0)
    tr.add(snare_roll(tr, 0.8, 0.12), 0.4)
    # las espadas que se clavan
    for i in range(8):
        tr.add(shing(tr, 0.6, 0.18), 1.15 + i * 0.12)
        tr.add(kick(0.25, 0.25, 140, 60), 1.15 + i * 0.12)
    # 2,2: el acorde
    tr.add(gong(110, 2.2, 0.25), 2.2)
    for m in (45, 57, 60, 64, 69):
        tr.add(brass(m, 0.8, 0.13, 1.1), 2.2)
    tr.add(choir((57, 60, 64, 69), 2.4, 0.14, 0.2, 2800), 2.2)
    ost = [57, 60, 64, 60, 59, 62, 64, 62]
    for k, m in enumerate(ost):
        tr.add(strings((m - 12,), 0.28, 0.08, 1800, 0.01), 2.5 + k * 0.28)
    tr.add(taiko(tr, 0.7, 1.0, 50), 4.3)
    tr.render('espadas', 2.0, 0.45)


def aura():
    tr = Track(4.8, 1612)
    n = int(4.0 * SR)
    t = np.arange(n) / SR
    f = note(30) * (1 + 0.5 * (t / 4.0) ** 2)
    drone = saw_ph(np.cumsum(f) / SR) + saw_ph(np.cumsum(f * 1.007) / SR)
    cut = 200 + 2500 * (t / 4.0) ** 2
    out = np.zeros(n)
    for i in range(16):
        a, b = i * n // 16, (i + 1) * n // 16
        out[a:b] = lp(drone, cut[(a + b) // 2])[a:b]
    tr.add(out * 0.18 * np.clip(t / 0.6, 0, 1), 0.0)
    # latidos que se aceleran
    at, gap = 0.6, 0.5
    while at < 3.6:
        tr.add(kick(0.45, 0.25, 90, 45), at)
        tr.add(kick(0.3, 0.2, 90, 45), at + 0.14)
        at += gap
        gap = max(0.18, gap * 0.85)
    for k in range(10):
        tr.add(zap(tr, 0.06, 0.1, 2600, 500), 1.6 + k * 0.2 + tr.rng.uniform(-0.03, 0.03))
    # 1,6: el grito de poder
    tr.add(power_chord(42, 0.8, 0.12), 1.6)
    tr.add(riser(tr, 2.2, 0.12, 500, 9000), 1.65)
    # 3,85: la descarga
    tr.add(kick(1.0, 1.0, 120, 26), 3.85)
    tr.add(cymbal(tr, 1.4, 0.24), 3.85)
    tr.add(power_chord(42, 0.9, 0.16), 3.86)
    tr.add(thunder(tr, 1.2, 0.35), 3.86)
    tr.render('aura', 1.0, 0.3)


def corazones():
    tr = Track(4.6, 1613)
    beat = 60 / 110
    chords = [(53, 57, 60, 65), (48, 55, 60, 64), (50, 57, 62, 65), (46, 53, 58, 62)]
    for k in range(8):
        ch = chords[(k // 2) % 4]
        for j, m in enumerate(ch):
            tr.add(harp(m + 12, 0.5, 0.09), 0.1 + k * beat + j * 0.012)
    mel = [(77, 0.5), (79, 0.5), (81, 1.0), (84, 0.5), (81, 0.5), (79, 1.0), (77, 1.5)]
    at = 0.1
    for m, ln in mel:
        tr.add(bell(note(m + 12), 1.0, 0.1), at)
        at += ln * beat
    tr.add(kiss(0.25), 1.4)
    for k in range(5):
        tr.add(kick(0.25, 0.25, 70, 45), 1.85 + k * 0.48)
        tr.add(kick(0.18, 0.2, 70, 45), 1.85 + k * 0.48 + 0.16)
    tr.add(choir((65, 69, 72), 2.2, 0.05, 0.4, 4000), 1.8)
    tr.render('corazones', 1.2, 0.35)


def concierto():
    tr = Track(5.0, 1614)
    beat = 0.5
    riff = [40, 40, 43, 45, 40, 40, 47, 45]
    for k in range(7):
        tr.add(power_chord(riff[k % 8], 0.22, 0.17), 0.3 + k * beat)
        tr.add(power_chord(riff[k % 8], 0.18, 0.12), 0.3 + k * beat + 0.25)
    for k in range(14):
        at = 0.3 + k * beat / 2
        if k % 4 == 0:
            tr.add(kick(0.6, 0.3, 130, 50), at)
        if k % 4 == 2:
            tr.add(snare(tr, 0.35), at)
        tr.add(hat(tr, 0.09), at)
    tr.add(bass(28, 3.2, 0.18, 500), 0.3)
    # 3,65: el acorde final con el salto
    tr.add(power_chord(40, 1.2, 0.2), 3.7)
    tr.add(cymbal(tr, 1.5, 0.24), 3.7)
    tr.add(kick(0.9, 0.6, 120, 40), 3.7)
    tr.render('concierto', 0.7, 0.22)


def portal():
    tr = Track(5.0, 1615)
    sw = (cymbal(tr, 0.9, 0.2) + bell(note(64), 0.9, 0.2))[::-1]
    tr.add(sw, 0.0)
    tr.add(riser(tr, 0.6, 0.15, 300, 6000), 0.35)
    tr.add(kick(0.5, 0.8, 60, 30), 0.95)
    arp = [52, 53, 55, 59, 60, 59, 55, 53]
    for k in range(20):
        m = arp[k % 8] + 12 * (k // 8 % 2)
        tr.add(square(m + 12, 0.12, 0.04, 0.5), 1.0 + k * 0.15)
        tr.add(bell(note(m + 24), 0.6, 0.025), 1.0 + k * 0.15 + 0.075)
    tr.add(strings((40, 47, 52, 53), 3.0, 0.07, 1500, 0.6), 1.0)
    for k in range(6):
        tr.add(kick(0.3, 0.5, 50, 32), 1.0 + k * 0.6)
    # 2,0: la insignia sale (barrido que sube)
    tr.add(glide(64, 88, 0.7, 0.07), 2.0)
    for m in (76, 79, 83, 88):
        tr.add(bell(note(m), 1.6, 0.09), 2.7)
    # 4,25: se cierra
    tr.add(cymbal(tr, 0.4, 0.2)[::-1], 3.85)
    tr.add(kick(0.8, 0.7, 80, 30), 4.25)
    tr.render('portal', 2.0, 0.5)


if __name__ == '__main__':
    estandarte()
    corona()
    trueno()
    fiesta()
    orbita()
    fenix()
    tornado()
    ventisca()
    meteoro()
    fuegos()
    espadas()
    aura()
    corazones()
    concierto()
    portal()
