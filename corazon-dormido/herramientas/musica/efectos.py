#!/usr/bin/env python3
"""Los efectos de sonido de Corazón Dormido, sintetizados con el mismo motor que la música.

  python3 corazon-dormido/herramientas/musica/efectos.py <carpeta-de-salida>

Cada efecto es un .ogg corto (mono); los que tienen variantes se llaman nombre_0, nombre_1… El juego (js/sonido.js)
los carga si están y, si no, sigue con los sonidos sintetizados al momento.
"""
import pathlib
import zlib
import sys

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from motor import (SR, arpa, bp, campana, celesta, coro, hp, hz, latido, lp, metal, respuesta, ruido)  # noqa: E402
from scipy import signal  # noqa: E402


def t_(dur):
    return np.arange(int(dur * SR)) / SR


def env(dur, ataque=0.002, caida=10.0):
    t = t_(dur)
    return np.clip(t / ataque, 0, 1) * np.exp(-t * caida)


def sala(x, rt=0.6, mezcla=0.25, oscuro=5000):
    """Un poco de sala (la cueva), corta, para que los golpes no suenen secos."""
    ir = respuesta(rt, oscuro, pre=0.005)[:, 0]
    h = signal.fftconvolve(x, ir)[: len(x) + int(rt * SR * 0.6)]
    y = np.zeros(len(h))
    y[: len(x)] = x
    return y + h * mezcla


def tono(f0, f1, dur, forma='seno'):
    t = t_(dur)
    f = f0 * (f1 / f0) ** (t / dur)
    ph = np.cumsum(f) / SR
    return np.sin(2 * np.pi * ph) if forma == 'seno' else 2 * (ph % 1) - 1


def norm(x, pico=0.9):
    m = np.abs(x).max()
    return x * (pico / m) if m > 0 else x


EF = {}


def efecto(nombre, variantes=1):
    def deco(fn):
        EF[nombre] = (fn, variantes)
        return fn
    return deco


@efecto('picar', 4)
def picar(v, g):
    # el pico contra la piedra: chasquido metálico, la piedra que cruje y un golpe sordo
    dur = 0.45
    t = t_(dur)
    f = 2400 + 600 * g.random()
    clic = sum(a * np.sin(2 * np.pi * f * r * t + g.random() * 6) * np.exp(-t * d) for r, a, d in ((1, 1, 60), (1.47, 0.6, 80), (2.09, 0.4, 110), (2.8, 0.25, 140)))
    piedra = bp(ruido(len(t), g.integers(1e6)), 1800 + 900 * g.random(), 1.1) * np.exp(-t * 30)
    sordo = np.sin(2 * np.pi * (120 + 30 * g.random()) * t) * np.exp(-t * 35)
    migas = np.zeros(len(t))
    for _ in range(5):  # trocitos que caen
        i = int((0.04 + g.random() * 0.2) * SR)
        k = int(0.02 * SR)
        migas[i:i + k] += hp(ruido(k, g.integers(1e6)), 3000) * np.exp(-np.arange(k) / SR * 200) * 0.25
    return sala(norm(clic * 0.35 + piedra * 0.9 + sordo * 0.6 + migas), 0.5, 0.2)


@efecto('romper', 2)
def romper(v, g):
    # la pared se viene abajo: crujido, rocas que caen y polvo
    dur = 1.0
    t = t_(dur)
    x = lp(ruido(len(t), g.integers(1e6)), 2200) * np.exp(-t * 7) * 0.8
    x += np.sin(2 * np.pi * (70 + 20 * np.exp(-t * 10)) * t) * np.exp(-t * 8) * 0.9
    for _ in range(12):
        i = int((0.03 + g.random() * 0.55) * SR)
        k = int(0.06 * SR)
        f = 300 + g.random() * 900
        x[i:i + k] += bp(ruido(k, g.integers(1e6)), f, 2) * np.exp(-np.arange(k) / SR * 60) * (0.5 + g.random() * 0.5)
    return sala(norm(x), 0.9, 0.3, 3500)


@efecto('excavar', 2)
def excavar(v, g):
    t = t_(0.4)
    x = lp(ruido(len(t), g.integers(1e6)), 900 + 300 * g.random()) * env(0.4, 0.01, 9)
    x += np.sin(2 * np.pi * 90 * t) * np.exp(-t * 20) * 0.4
    return sala(norm(x, 0.8), 0.4, 0.2, 3000)


@efecto('metal', 2)
def metal_(v, g):
    # el pico rebota en algo que no puede romper
    t = t_(0.7)
    f = 1100 + 200 * g.random()
    x = sum(a * np.sin(2 * np.pi * f * r * t) * np.exp(-t * d) for r, a, d in ((1, 1, 9), (2.76, 0.5, 14), (5.4, 0.3, 20), (8.9, 0.15, 30)))
    x += hp(ruido(len(t), g.integers(1e6)), 4000) * np.exp(-t * 80) * 0.4
    return sala(norm(x, 0.7), 0.6, 0.25)


@efecto('golpe', 4)
def golpe(v, g):
    # el arma entra en carne: un golpe sordo con un crujido húmedo
    t = t_(0.35)
    x = np.sin(2 * np.pi * (90 + 50 * np.exp(-t * 40) + 20 * g.random()) * t) * np.exp(-t * 18)
    x += lp(ruido(len(t), g.integers(1e6)), 2500) * np.exp(-t * 45) * 0.8
    x += bp(ruido(len(t), g.integers(1e6)), 700 + 300 * g.random(), 3) * np.exp(-t * 25) * 0.5
    return sala(norm(np.tanh(x * 1.8)), 0.4, 0.15)


@efecto('zas', 3)
def zas(v, g):
    # el tajo cortando el aire
    dur = 0.28
    t = t_(dur)
    n = ruido(len(t), g.integers(1e6))
    out = np.zeros(len(t))
    blq = 256
    for i in range(0, len(t), blq):
        fc = 600 + 3500 * np.sin(np.pi * min(1, i / len(t))) * (0.8 + 0.3 * g.random())
        b, a = signal.iirpeak(fc / (SR / 2), 1.5)
        out[i:i + blq] = signal.lfilter(b, a, n[i:i + blq])
    e = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.5
    return norm(out * e, 0.6)


@efecto('dano', 2)
def dano(v, g):
    # te dan: golpe en el pecho y un quejido ahogado
    t = t_(0.5)
    x = np.sin(2 * np.pi * (70 + 60 * np.exp(-t * 30)) * t) * np.exp(-t * 12)
    ph = np.cumsum(180 * (1 - 0.3 * t / 0.5) * (1 + 0.02 * np.sin(2 * np.pi * 30 * t))) / SR
    voz = (2 * (ph % 1) - 1)
    voz = bp(voz, 650, 3) + 0.5 * bp(voz, 1100, 4)
    x += voz * env(0.5, 0.02, 7) * 0.35
    x += lp(ruido(len(t), g.integers(1e6)), 1500) * np.exp(-t * 40) * 0.5
    return sala(norm(np.tanh(x * 1.5)), 0.4, 0.15)


@efecto('muere', 3)
def muere(v, g):
    # la criatura cae: un chillido que baja y se deshace en un chapoteo
    dur = 0.8
    t = t_(dur)
    f0 = 300 + 200 * g.random()
    ph = np.cumsum(f0 * (0.35 + 0.65 * np.exp(-t * 3)) * (1 + 0.04 * np.sin(2 * np.pi * 23 * t))) / SR
    voz = 2 * (ph % 1) - 1
    voz = bp(voz, 900, 2.5) + 0.6 * bp(voz, 1700, 3) + 0.4 * lp(voz, 500)
    x = voz * env(dur, 0.01, 4.5) * 0.6
    chap = lp(ruido(len(t), g.integers(1e6)), 1200) * np.exp(-np.clip(t - 0.15, 0, None) * 12) * (t > 0.15) * 0.6
    return sala(norm(x + chap), 0.6, 0.25, 4000)


@efecto('alerta', 3)
def alerta(v, g):
    # te ha visto: un gruñido corto que sube
    dur = 0.55
    t = t_(dur)
    f0 = 85 + 40 * g.random()
    ph = np.cumsum(f0 * (1 + 0.5 * t / dur) * (1 + 0.08 * ruido(len(t), g.integers(1e6)).clip(-2, 2) * 0.3)) / SR
    voz = 2 * (ph % 1) - 1
    x = bp(voz, 500, 2) + 0.7 * bp(voz, 1200, 3) + 0.3 * lp(voz, 300)
    x += bp(ruido(len(t), g.integers(1e6)), 900, 1.5) * 0.25
    return sala(norm(np.tanh(x * 2) * env(dur, 0.04, 3.5), 0.7), 0.5, 0.25, 4000)


@efecto('rugido')
def rugido(v, g):
    # el jefe despierta: un rugido grave con muchas gargantas
    dur = 2.2
    t = t_(dur)
    x = np.zeros(len(t))
    for k in range(5):
        f0 = 55 * (1 + 0.07 * k) * (1 + 0.3 * np.sin(np.pi * t / dur))
        ph = np.cumsum(f0 * (1 + 0.03 * np.sin(2 * np.pi * (17 + k) * t))) / SR
        x += 2 * (ph % 1) - 1
    x = bp(x, 400, 1.5) + 0.8 * bp(x, 900, 2) + 0.6 * lp(x, 200)
    x += lp(ruido(len(t), g.integers(1e6)), 1800) * 0.5
    e = np.clip(t / 0.15, 0, 1) * np.clip((dur - t) / 0.8, 0, 1)
    return sala(norm(np.tanh(x * 2.5) * e), 1.6, 0.4, 3500)


@efecto('temblor')
def temblor(v, g):
    t = t_(1.0)
    x = lp(ruido(len(t), g.integers(1e6)), 180) * 2 + np.sin(2 * np.pi * 38 * t) * 0.6
    x += lp(ruido(len(t), g.integers(1e6)), 1500) * 0.15
    return sala(norm(x * env(1.0, 0.02, 3.5)), 1.0, 0.3, 2500)


@efecto('latido')
def latido_(v, g):
    return sala(norm(latido(1.0), 0.9), 1.2, 0.3, 3000)


@efecto('recoger', 3)
def recoger(v, g):
    # algo a la mochila: una nota de celesta y un roce de tela
    n = ['E6', 'G6', 'B6'][v]
    x = celesta(hz(n), 0.6, 0.7)
    x[: int(0.08 * SR)] += hp(ruido(int(0.08 * SR), g.integers(1e6)), 3000) * np.exp(-np.arange(int(0.08 * SR)) / SR * 50) * 0.15
    return sala(norm(x, 0.55), 0.5, 0.2)


@efecto('crear')
def crear(v, g):
    # en la mesa: dos martillazos y algo que brilla
    out = np.zeros(int(1.2 * SR))
    for k, t0 in enumerate((0.0, 0.22)):
        t = t_(0.5)
        x = sum(a * np.sin(2 * np.pi * 1500 * r * t) * np.exp(-t * d) for r, a, d in ((1, 1, 14), (2.4, 0.5, 20), (4.1, 0.3, 30)))
        x += lp(ruido(len(t), g.integers(1e6)), 3000) * np.exp(-t * 60) * 0.5
        i = int(t0 * SR)
        out[i:i + len(x)] += x * (0.8 if k else 1)
    br = celesta(hz('C7'), 0.8, 0.5) + celesta(hz('G7'), 0.8, 0.3)
    i = int(0.42 * SR)
    out[i:i + len(br)] += br[: len(out) - i] * 0.6
    return sala(norm(out, 0.7), 0.6, 0.25)


@efecto('comer')
def comer(v, g):
    out = np.zeros(int(0.6 * SR))
    for k in range(3):
        i = int(k * 0.15 * SR)
        n = int(0.09 * SR)
        out[i:i + n] += bp(ruido(n, g.integers(1e6)), 1400 + 400 * g.random(), 1.5) * np.exp(-np.arange(n) / SR * 40)
    return norm(out, 0.6)


@efecto('colocar', 2)
def colocar(v, g):
    t = t_(0.3)
    x = np.sin(2 * np.pi * (160 + 40 * g.random()) * t) * np.exp(-t * 25) + bp(ruido(len(t), g.integers(1e6)), 900, 2) * np.exp(-t * 40) * 0.6
    return sala(norm(x, 0.7), 0.4, 0.2)


@efecto('error')
def error(v, g):
    t = t_(0.25)
    x = np.sin(2 * np.pi * 150 * t) * np.exp(-t * 14) + 0.4 * np.sin(2 * np.pi * 300 * t) * np.exp(-t * 20)
    out = np.zeros(int(0.45 * SR))
    out[: len(x)] += x
    out[int(0.14 * SR): int(0.14 * SR) + len(x)] += x * 0.8
    return norm(out, 0.5)


@efecto('menu')
def menu(v, g):
    t = t_(0.08)
    x = np.sin(2 * np.pi * 1300 * t) * np.exp(-t * 90) + bp(ruido(len(t), 5), 3000, 2) * np.exp(-t * 120) * 0.4
    return norm(x, 0.35)


@efecto('nivel')
def nivel(v, g):
    # sube una habilidad: arpa que sube y un brillo de campanas
    out = np.zeros(int(2.4 * SR))
    for k, n in enumerate(['D4', 'F4', 'A4', 'D5', 'F5', 'A5']):
        x = arpa(hz(n), 1.8, 0.6, brillo=0.6, sem=k)
        i = int(k * 0.07 * SR)
        out[i:i + len(x)] += x
    c = celesta(hz('D7'), 1.4, 0.4)
    i = int(0.42 * SR)
    out[i:i + len(c)] += c
    return sala(norm(out, 0.6), 1.4, 0.35)


@efecto('victoria')
def victoria(v, g):
    # un guardián cae: metales y coro en re mayor, con campana
    dur = 3.5
    out = np.zeros(int((dur + 1.5) * SR))
    for n in ['D3', 'A3', 'D4', 'F#4', 'A4']:
        x = metal(hz(n), dur * 0.6, 0.8)
        out[: len(x)] += x * 0.5
        y = coro(hz(n) * 2, dur * 0.7, 0.5, 'a', ataque=0.2)
        out[: len(y)] += y * 0.25
    c = campana(hz('D5'), dur + 1, 0.5)
    out[: len(c)] += c[: len(out)]
    return sala(norm(out, 0.75), 2.0, 0.35)


@efecto('paso_piedra', 4)
def paso_piedra(v, g):
    t = t_(0.12)
    x = bp(ruido(len(t), g.integers(1e6)), 1200 + 600 * g.random(), 1.4) * np.exp(-t * 55) + np.sin(2 * np.pi * 90 * t) * np.exp(-t * 60) * 0.3
    return norm(x, 0.35)


@efecto('paso_hierba', 4)
def paso_hierba(v, g):
    t = t_(0.16)
    x = hp(ruido(len(t), g.integers(1e6)), 2500) * np.exp(-t * 30) * (0.6 + 0.4 * np.sin(2 * np.pi * 40 * t))
    return norm(x, 0.25)


@efecto('paso_agua', 3)
def paso_agua(v, g):
    t = t_(0.25)
    x = bp(ruido(len(t), g.integers(1e6)), 700 + 300 * g.random(), 2) * np.exp(-t * 18)
    f = 600 + 400 * g.random()
    x += np.sin(2 * np.pi * np.cumsum(f * (1 + 2 * (1 - np.exp(-t * 30)))) / SR) * np.exp(-t * 30) * 0.3
    return norm(x, 0.35)


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    import soundfile as sf
    salida = pathlib.Path(sys.argv[1])
    salida.mkdir(parents=True, exist_ok=True)
    total = 0
    for nombre, (fn, nv) in EF.items():
        for v in range(nv):
            g = np.random.default_rng(zlib.crc32(nombre.encode()) + v * 7919)
            x = fn(v, g)
            m = np.abs(x).max()
            if m > 0.85:  # la sala se suma después, y el Vorbis se pasa un poco: que nada sature
                x = x * (0.85 / m)
            x = x.astype(np.float32)
            ruta = salida / (f'{nombre}_{v}.ogg' if nv > 1 else f'{nombre}.ogg')
            with sf.SoundFile(ruta, 'w', SR, 1, format='OGG', subtype='VORBIS', compression_level=0.5) as f:
                for i in range(0, len(x), 32768):
                    f.write(x[i:i + 32768])
            total += ruta.stat().st_size
    print(f'{sum(v for _, v in EF.values())} efectos, {total / 1e3:.0f} kB')


if __name__ == '__main__':
    main()
