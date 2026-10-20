"""Instrumentos sintetizados para los sonidos y la música del santuario del Void (TF Client 1.3.97), sin muestras.

El dueño, de los sonidos de la 1.3.96 (acordes de senos que florecían): «los pozos de luz tienen un sonido feo y el del
santuario también, hazlo mejor». Aquí, instrumentos con más cuerpo:

  arpa(...)    cuerda pulsada: suma de armónicos, cada uno con su caída (los agudos se apagan antes), un poco de
               inarmonía y el golpecito del dedo (ruido muy corto filtrado); afinación exacta
  campana(...) campana suave por FM (portadora y moduladora 1:3,5, el índice se apaga: el brillo se va primero)
  flauta(...)  seno con 2.º y 3.er armónicos débiles, vibrato que llega tarde y el aire del soplo
  celesta(...) caja de música: fundamental, octava y un parcial alto que se apagan antes (1.3.98)
  coro(...)    «aah» de sierras desafinadas por las formantes de la a (sonidos_cielo.choir) con aliento
  sala(...)    reverberación estéreo de sala (reflexiones tempranas + cola difusa que se oscurece)

Lo usan sonidos_viaje.py (luz/*) y musica_santuario.py.
"""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'bosses'))
from sonidos_cielo import choir, smooth  # noqa: E402
from sonidos_grieta import SR, bp, lp, reverb, rng  # noqa: E402

A4 = 440.0
NOTE = {'C': -9, 'C#': -8, 'Db': -8, 'D': -7, 'D#': -6, 'Eb': -6, 'E': -5, 'F': -4, 'F#': -3, 'Gb': -3, 'G': -2,
        'G#': -1, 'Ab': -1, 'A': 0, 'A#': 1, 'Bb': 1, 'B': 2}


def hz(name):
    """'D4', 'F#3', 'Bb5' → frecuencia."""
    n = name[:-1]
    octave = int(name[-1])
    return A4 * 2 ** ((NOTE[n] + 12 * (octave - 4)) / 12)


def place(out, x, at, gain=1.0):
    """Suma x (mono o estéreo) en out a partir de «at» segundos (recorta lo que se sale)."""
    i = int(round(at * SR))
    if i >= len(out):
        return
    n = min(len(x), len(out) - i)
    if x.ndim == 1 and out.ndim == 2:
        out[i:i + n] += x[:n, None] * gain
    else:
        out[i:i + n] += x[:n] * gain


def pan2(mono, p):
    a = (p + 1) * np.pi / 4
    return np.stack([mono * np.cos(a), mono * np.sin(a)], 1)


def arpa(f, dur=4.0, vel=1.0, seed=1, bright=1.0):
    """Una nota de arpa (mono)."""
    r = rng(seed)
    t = np.arange(int(dur * SR)) / SR
    x = np.zeros(len(t))
    pos = 0.13 + 0.04 * r.random()      # dónde se pulsa la cuerda (da el color)
    nyq = SR / 2 - 500
    tau0 = 2.6 * (220.0 / max(f, 60)) ** 0.35
    for k in range(1, 16):
        fk = f * k * (1 + 0.0004 * k * k)  # un pelo de inarmonía
        if fk > nyq:
            break
        amp = abs(np.sin(np.pi * k * pos)) / k ** (1.25 - 0.25 * bright)
        tau = tau0 / (1 + 0.32 * k ** 1.45)
        x += amp * np.sin(2 * np.pi * fk * t + r.uniform(0, 6.28)) * np.exp(-t / tau)
    # el ataque: sube en 2 ms (sin clic) y el roce del dedo
    x *= smooth(0, 0.002, t)
    pluck = r.standard_normal(int(0.012 * SR)) * np.exp(-np.arange(int(0.012 * SR)) / (0.002 * SR))
    pluck = bp(np.concatenate([pluck, np.zeros(len(t) - len(pluck))]), f * 1.5, min(f * 8, nyq))
    x = x + pluck * 0.08
    return x * vel / 3.0


def campana(f, dur=5.0, vel=1.0, seed=3):
    """Campana suave (mono): FM 1:3,5 con el índice que se apaga, y un parcial grave que se queda."""
    r = rng(seed)
    t = np.arange(int(dur * SR)) / SR
    idx = 2.4 * np.exp(-t / 0.35) + 0.25
    mod = np.sin(2 * np.pi * f * 3.5 * t + r.uniform(0, 6.28))
    x = np.sin(2 * np.pi * f * t + idx * mod) * np.exp(-t / 1.8)
    x += 0.35 * np.sin(2 * np.pi * f * 0.5 * t) * np.exp(-t / 2.6)
    x *= smooth(0, 0.004, t)
    return x * vel * 0.5


def celesta(f, dur=3.0, vel=1.0, seed=9):
    """(1.3.98) Celesta / caja de música (mono): un golpecito de martillo sobre una lámina; la fundamental, una octava y
    un parcial alto que se apagan antes (brilla al tocarla y queda redonda), sin nada de ruido."""
    r = rng(seed)
    t = np.arange(int(dur * SR)) / SR
    ph = r.uniform(0, 6.28)
    x = (np.sin(2 * np.pi * f * t + ph) * np.exp(-t / 1.4)
         + 0.32 * np.sin(2 * np.pi * f * 2.0 * t + ph) * np.exp(-t / 0.55)
         + 0.12 * np.sin(2 * np.pi * f * 4.07 * t) * np.exp(-t / 0.18))
    x *= smooth(0, 0.003, t)
    return x * vel * 0.42


def flauta(f, dur, vel=1.0, seed=5, attack=0.12, release=0.35):
    """Una nota de flauta (mono), con su vibrato y su aire."""
    r = rng(seed)
    n = int((dur + release) * SR)
    t = np.arange(n) / SR
    vib = 1 + 0.0045 * smooth(0.35, 0.9, t) * np.sin(2 * np.pi * 5.2 * t + r.uniform(0, 6))
    ph = 2 * np.pi * np.cumsum(f * vib) / SR
    x = np.sin(ph) + 0.18 * np.sin(2 * ph) + 0.06 * np.sin(3 * ph)
    env = smooth(0, attack, t) * (1 - smooth(dur, dur + release, t))
    air = bp(r.standard_normal(n), f * 0.8, min(f * 3.2, SR / 2 - 500)) * 0.10
    air = air * (0.4 + 0.6 * np.exp(-t / 0.25))
    return (x + air) * env * vel * 0.45


def coro(t, freqs, seed=7, breath=0.08):
    """«Aah» (estéreo) con un poco de aliento."""
    x = choir(t, freqs, seed=seed, vib=0.0035)
    r = rng(seed + 1)
    for c in range(2):
        x[:, c] += bp(r.standard_normal(len(t)), 900, 3200) * breath * 0.2
    return x


def sala(x, seconds=4.2, wet=0.38, damp=4200, seed=11):
    """Reverberación de sala estéreo: reflexiones tempranas (unos ecos cortos) y la cola de sonidos_grieta.reverb."""
    if x.ndim == 1:
        x = np.stack([x, x], 1)
    out = []
    for c in range(2):
        y = x[:, c].copy()
        r = rng(seed + c)
        early = np.zeros(int(0.09 * SR))
        for _ in range(9):
            early[r.integers(int(0.008 * SR), len(early))] += r.uniform(0.15, 0.45) * r.choice((-1, 1))
        er = np.convolve(y, early)[: len(y)]
        tail = reverb(y + 0.3 * er, seconds, damp, wet, seed=seed + 7 * c, predelay=0.035)
        out.append(tail)
    m = max(len(o) for o in out)
    return np.stack([np.pad(o, (0, m - len(o))) for o in out], 1)


def espectro(x, name):
    """Medidas para revisar sin oírlo: pico, nivel, centroide del espectro y si hay chasquidos (saltos bruscos)."""
    mono = x if x.ndim == 1 else x.mean(1)
    peak = np.max(np.abs(x))
    rms = np.sqrt(np.mean(mono ** 2))
    spec = np.abs(np.fft.rfft(mono * np.hanning(len(mono))))
    fr = np.fft.rfftfreq(len(mono), 1 / SR)
    cent = np.sum(spec * fr) / max(np.sum(spec), 1e-9)
    d = np.abs(np.diff(mono))
    clicks = int(np.sum(d > 0.25))
    print(f'   {name}: {len(mono) / SR:.1f} s, pico {peak:.2f}, rms {20 * np.log10(rms + 1e-12):.1f} dB, centroide {cent:.0f} Hz, '
          f'saltos > 0,25: {clicks}')
