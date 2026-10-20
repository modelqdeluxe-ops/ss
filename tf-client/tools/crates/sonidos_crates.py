"""Los sonidos de la cinemática de las crates (TF Client 1.3.99): sintetizados aquí, sin muestras de nadie.

    python3 -I tools/crates/sonidos_crates.py [--wav carpeta]

Escribe OGG Vorbis en assets/tfclient/sounds/crates/ (los pide CrateCinematicScreen como tfclient:crates.<nombre>).
Todo en Re mayor, a los tiempos de la cinemática del Bosque de las Hadas (20 ticks = 1 s): la llave aparece a los
1,0 s, entra a los 3,3, gira a los 3,6, las runas a los 3,8, tiembla a los 4,2, se abre a los 5,1, la ruleta de los
6,4 a los 12,6 y el premio a los 12,9; dura 18 s.

  ambiente        el bosque de las hadas al atardecer: la cascada, las hojas con el viento, grillos, campanitas de
                  las hadas y algún mirlo que canta lejos (19 s, estéreo)
  musica          la música de la cinemática, a sus tiempos (18 s, estéreo): cuerdas y arpa al llegar, celesta con la
                  llave, coro y campana al girar, trémolo que sube al temblar, acorde grande al abrirse, la ruleta con
                  un ostinato que se frena, silencio para el premio y un final tranquilo
  llave_aparece   brillo que sube (la llave sale de un remolino de luz)
  llave_entra     roce metálico y el «clac» de la llave en la cerradura
  llave_gira      trinquete, el golpe del cierre y una campana
  runas           zumbido que sube y brillo de cristal (el anillo de runas)
  tiembla         retumbo y la crate que traquetea
  apertura        soplo, acorde que florece, golpe grave sin tambor y una lluvia de destellos
  tic             cada premio que pasa por delante en la ruleta (el mod le cambia el tono con la velocidad)
  parada          la ruleta se para: una campanita y el brillo que espera
  premio1..3      el premio: común/rara, épica, legendaria/mítica (cada vez más grande)
"""
import os
import subprocess
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'void'))
sys.path.insert(0, os.path.join(HERE, '..', 'bosses'))
from instrumentos import arpa, campana, celesta, espectro, flauta, hz, pan2, place, sala  # noqa: E402
from sonidos_cielo import bloom, choir, noise2, pad, shimmer, smooth, swell, whoosh  # noqa: E402
from sonidos_grieta import SR, bp, hp, lp, rng, write_wav  # noqa: E402

RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'sounds', 'crates'))

# los tiempos de la cinemática (s)
KEY_APPEAR, KEY_IN, KEY_TURN, RUNES, TREMBLE, OPEN = 1.0, 3.3, 3.6, 3.8, 4.2, 5.1
RING, SPIN0, SPIN1, REVEAL, END = 5.5, 6.4, 12.6, 12.9, 18.0


def tt(dur):
    return np.arange(int(dur * SR)) / SR


def norm(x, peak):
    return x / max(1e-9, np.max(np.abs(x))) * peak


def fade_out(x, sec):
    n = min(len(x), int(sec * SR))
    env = np.ones(len(x))
    env[-n:] = np.linspace(1, 0, n) ** 1.5
    return x * (env[:, None] if x.ndim == 2 else env)


# ------------------------------------------------------------------------------------------- piezas pequeñas
def modal(freqs, decays, amps, dur, seed=1):
    """Un golpe en algo duro (metal, madera): modos que resuenan y se apagan, con un clic muy corto de ataque."""
    r = rng(seed)
    t = tt(dur)
    x = np.zeros(len(t))
    for f, d, a in zip(freqs, decays, amps):
        x += a * np.sin(2 * np.pi * f * t + r.uniform(0, 6)) * np.exp(-t / d)
    clk = r.standard_normal(int(0.003 * SR)) * np.linspace(1, 0, int(0.003 * SR))
    x[:len(clk)] += hp(clk, 2000) * 0.6
    return x * smooth(0, 0.0008, t)


def thud(f, dur, decay, seed=2):
    t = tt(dur)
    return np.sin(2 * np.pi * f * t * (1 - 0.25 * t / dur)) * np.exp(-t / decay) * smooth(0, 0.003, t)


def chirp(f0, f1, dur, shape='down', harm=0.12, trill=0.0):
    """Un trino de pájaro: un seno que barre de f0 a f1 (abajo, arriba-abajo o con trémolo de tono)."""
    t = tt(dur)
    u = t / dur
    if shape == 'down':
        f = f0 + (f1 - f0) * u ** 0.6
    elif shape == 'up':
        f = f0 + (f1 - f0) * u ** 1.4
    else:
        f = f0 + (f1 - f0) * np.sin(np.pi * u)
    if trill:
        f = f * (1 + 0.06 * np.sin(2 * np.pi * trill * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) + harm * np.sin(2 * ph)
    return x * np.sin(np.pi * u) ** 1.3


def bubble(f, dur, seed):
    """Una gota en el agua: el «plic» de una burbuja (seno que sube rápido y se apaga)."""
    t = tt(dur)
    fr = f * (1 + 1.8 * t / dur)
    return np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.exp(-t / (dur * 0.28)) * smooth(0, 0.001, t)


def sparkle(dur, count, seed, lo=1800, hi=5200, start=0.0, spread=1.0):
    """Destellos: notas altas de celesta sueltas (de la pentatónica de Re) que se apagan."""
    r = rng(seed)
    out = np.zeros((int(dur * SR), 2))
    notes = ['D6', 'E6', 'F#6', 'A6', 'B6', 'D7', 'A5', 'B5']
    for i in range(count):
        at = start + r.random() ** 1.6 * spread * (dur - start - 0.6)
        n = hz(notes[r.integers(len(notes))])
        if not lo <= n <= hi:
            n = hz('A6')
        place(out, pan2(celesta(n, 1.2, 0.35 + 0.4 * r.random(), seed=seed * 7 + i), r.uniform(-0.8, 0.8)), at, 1.0)
    return out


# --------------------------------------------------------------------------------------------- el ambiente
def ambiente():
    dur = END + 1.0
    t = tt(dur)
    n = len(t)
    r = rng(1399)
    out = np.zeros((n, 2))
    # la cascada, detrás: un rumor de agua que no para (grave y ancho) y su siseo
    roar = noise2(n, 17)
    for c in range(2):
        roar[:, c] = bp(roar[:, c], 160, 1900) * 0.9 + bp(roar[:, c], 1900, 6000) * 0.22
    swirl = 0.8 + 0.2 * np.sin(2 * np.pi * 0.21 * t + 0.4) + 0.12 * lp(r.standard_normal(n), 3) * 6
    out += roar * swirl[:, None] * 0.05
    # el viento en las hojas, con ráfagas lentas
    wind = noise2(n, 11)
    for c in range(2):
        wind[:, c] = bp(wind[:, c], 350, 2600) * 0.5 + bp(wind[:, c], 2600, 7000) * 0.15
    gust = 0.45 + 0.55 * smooth(-1, 1, np.sin(2 * np.pi * 0.07 * t + 1.0) + 0.6 * np.sin(2 * np.pi * 0.13 * t + 2.2))
    out += wind * gust[:, None] * 0.035
    # gotas en el estanque
    for i in range(int(dur * 9)):
        at = r.random() * (dur - 0.1)
        f = 500 + r.random() ** 1.5 * 1400
        place(out, pan2(bubble(f, 0.03 + r.random() * 0.05, i), r.uniform(-0.3, 0.3)), at, 0.035 + 0.04 * r.random())

    # grillos del atardecer: trinos cortos en grupos, cada uno con su ritmo y su sitio
    def cricket(p, f0, rate, gap, seed, gain):
        rr = rng(seed)
        at = rr.random() * gap
        while at < dur - 0.3:
            pulses = rr.integers(3, 5)
            for j in range(pulses):
                d = 0.018
                tc = tt(d)
                x = np.sin(2 * np.pi * f0 * tc) * np.sin(np.pi * tc / d) ** 2
                place(out, pan2(x, p), at + j / rate, gain)
            at += gap * (0.85 + 0.3 * rr.random())

    for k in range(5):
        cricket(r.uniform(-0.9, 0.9), 4300 + 500 * r.random(), 28 + 6 * r.random(), 0.55 + 0.4 * r.random(), 1500 + k,
                0.010 + 0.008 * r.random())

    # las campanitas de las hadas: notas sueltas, muy agudas y suaves (pentatónica de Re), con un brillo
    penta = ['D6', 'E6', 'F#6', 'A6', 'B6', 'D7']
    at = 0.4
    k = 0
    while at < dur - 1.5:
        p = r.uniform(-0.8, 0.8)
        for j in range(r.integers(1, 4)):
            nt = penta[r.integers(len(penta))]
            place(out, pan2(celesta(hz(nt), 1.6, 0.5, seed=1600 + k * 5 + j), p), at + j * 0.11, 0.05 + 0.03 * r.random())
        if r.random() < 0.5:
            place(out, sparkle(0.8, 4, 1700 + k) * 0.5, at + 0.05, 0.3)
        k += 1
        at += 1.1 + r.random() * 1.8

    # un mirlo lejos que canta al atardecer (y otro que contesta)
    def warble(at, p, seed):
        rr = rng(seed)
        k = at
        for j in range(rr.integers(5, 8)):
            f0 = 1700 + rr.random() * 1400
            d = 0.07 + rr.random() * 0.1
            x = chirp(f0, f0 * rr.uniform(0.75, 1.35), d, 'updown' if rr.random() < 0.6 else 'down', 0.15)
            place(out, pan2(lp(x, 4500), p), k, 0.035 + 0.02 * rr.random())
            k += d + 0.03 + rr.random() * 0.07

    k = 0
    at = 1.4
    while at < dur - 2.5:
        warble(at, r.uniform(-0.9, 0.9), 100 + k)
        k += 1
        at += 3.2 + r.random() * 2.6
    out = sala(out, 3.0, 0.26, 5200, seed=1399)[:n]
    out = np.stack([hp(out[:, c], 90) for c in range(2)], 1)
    out *= smooth(0, 0.8, t)[:, None]
    return norm(fade_out(out, 1.6), 0.55)


# ---------------------------------------------------------------------------------------------- la música
CH = {
    'D': ('D2', ['D3', 'F#3', 'A3', 'E4'], ['D3', 'A3', 'D4', 'F#4', 'A4', 'D5']),
    'Bm': ('B1', ['B2', 'F#3', 'B3', 'D4'], ['B2', 'F#3', 'B3', 'D4', 'F#4', 'B4']),
    'G': ('G1', ['G2', 'D3', 'G3', 'B3'], ['G2', 'D3', 'G3', 'B3', 'D4', 'G4']),
    'Em': ('E2', ['E3', 'G3', 'B3', 'D4'], ['E3', 'B3', 'E4', 'G4', 'B4', 'E5']),
    'A': ('A1', ['A2', 'E3', 'A3', 'C#4'], ['A2', 'E3', 'A3', 'C#4', 'E4', 'A4']),
    'Asus': ('A1', ['A2', 'E3', 'A3', 'D4'], ['A2', 'E3', 'A3', 'D4', 'E4', 'A4']),
}


def strings(dur, chord, seed, cut=(700, 1400), trem=0.0):
    t = tt(dur)
    x = pad(t, [hz(v) for v in CH[chord][1]], cut[0], cut[1], seed=seed, spread=0.0035, voices=3)
    for c in range(2):
        x[:, c] = lp(x[:, c], 1700, 2)
    if trem:
        x *= (0.65 + 0.35 * np.sin(2 * np.pi * trem * t))[:, None]
    return x


def musica():
    n = int((END + 4.0) * SR)
    out = np.zeros((n, 2))
    r = rng(2026)

    def chord_block(name, t0, t1, gain, cut=(700, 1400), trem=0.0, attack=0.6, release=0.8, seed=0):
        dur = t1 - t0 + release
        t = tt(dur)
        x = strings(dur, name, 40 + seed, cut, trem)
        env = smooth(0, attack, t) * (1 - smooth(t1 - t0, dur, t))
        place(out, x * env[:, None], t0, gain)

    def harp_run(name, t0, t1, step, gain, order=(0, 1, 2, 3, 4, 5, 4, 3), seed=0, rit=0.0):
        k, at = 0, t0
        while at < t1:
            j = order[k % len(order)]
            v = (0.55 + 0.15 * (k % 4 == 0) + 0.1 * r.random())
            place(out, pan2(arpa(hz(CH[name][2][j]), 2.6, v, seed=seed + k), -0.45 + 0.9 * j / 5), at + r.uniform(-0.008, 0.008), gain)
            k += 1
            at += step * (1 + rit * ((at - t0) / max(1e-6, t1 - t0)) ** 2)

    # la llegada (ya junto a la crate): cuerdas y arpa tranquilas
    chord_block('D', 0.05, 1.7, 0.40, attack=0.9)
    harp_run('D', 0.15, 1.6, 0.26, 0.45, seed=100)
    # la llave aparece: celesta que sube; vuela: Si m y la flauta
    for k, nt in enumerate(('A4', 'D5', 'F#5', 'A5', 'D6', 'F#6')):
        place(out, pan2(celesta(hz(nt), 2.0, 0.7, seed=400 + k), -0.6 + 0.24 * k), KEY_APPEAR + k * 0.075, 0.38)
    chord_block('Bm', 1.5, 2.8, 0.36)
    harp_run('Bm', 1.7, 2.7, 0.26, 0.38, order=(0, 2, 1, 3, 2, 4, 3, 5), seed=200)
    for k, (nt, at, d) in enumerate((('F#5', 1.65, 0.34), ('E5', 2.0, 0.34), ('D5', 2.35, 0.28), ('A4', 2.6, 0.45))):
        place(out, pan2(flauta(hz(nt), d, 0.7, seed=300 + k), -0.1), at, 0.38)
    # se alinea y entra: Sol, quieto
    chord_block('G', 2.7, KEY_TURN + 0.3, 0.30, cut=(600, 1000))
    # gira: el coro entra en Re, suave
    t = tt(1.8)
    ch = choir(t, [hz('D4'), hz('F#4'), hz('A4')], seed=7) * (smooth(0, 0.5, t) * (1 - smooth(1.1, 1.8, t)))[:, None]
    place(out, ch, RUNES, 0.30)
    # tiembla: trémolo de cuerdas en La que sube
    chord_block('A', TREMBLE, OPEN, 0.42, cut=(600, 2600), trem=11.0, attack=0.7, release=0.05, seed=5)
    t = tt(OPEN - TREMBLE)
    rise = noise2(len(t), 21)
    for c in range(2):
        rise[:, c] = bp(rise[:, c], 1200, 4500)
    place(out, rise * (smooth(0, OPEN - TREMBLE, t) ** 3)[:, None] * 0.10, TREMBLE, 1.0)
    # ¡se abre!: Re mayor grande (cuerdas, coro, campana), un golpe grave sin tambor y el arpa en glissando
    t = tt(3.4)
    big = pad(t, [hz(v) for v in ('D3', 'A3', 'D4', 'F#4', 'A4', 'D5')], 1200, 3200, seed=31, spread=0.004, voices=3)
    big *= (smooth(0, 0.06, t) * np.exp(-t / 1.6))[:, None]
    place(out, big, OPEN, 0.55)
    ch = choir(t, [hz('D4'), hz('A4'), hz('D5'), hz('F#5')], seed=33) * (smooth(0, 0.12, t) * np.exp(-t / 1.4))[:, None]
    place(out, ch, OPEN, 0.40)
    place(out, pan2(campana(hz('D5'), 4.0, 0.8, seed=35), -0.2), OPEN + 0.02, 0.45)
    place(out, pan2(campana(hz('A5'), 4.0, 0.6, seed=36), 0.25), OPEN + 0.05, 0.35)
    place(out, pan2(thud(36.7, 1.6, 0.55) + 0.3 * thud(73.4, 1.6, 0.3), 0.0), OPEN, 0.55)
    gl = ['D3', 'F#3', 'A3', 'D4', 'F#4', 'A4', 'D5', 'F#5', 'A5', 'D6']
    for k, nt in enumerate(gl):
        place(out, pan2(arpa(hz(nt), 2.4, 0.7, seed=500 + k, bright=1.2), -0.7 + 1.4 * k / len(gl)), OPEN + 0.08 + k * 0.045, 0.45)
    # se forma el anillo: destellos
    place(out, sparkle(2.2, 14, 600), RING, 0.5)
    # la ruleta: Si m, Sol, Mi m, La; un ostinato de arpa que se va frenando y las cuerdas que suben
    slow = SPIN0 + 2.9
    plan = [('Bm', SPIN0, SPIN0 + 1.6), ('G', SPIN0 + 1.6, SPIN0 + 3.2), ('Em', SPIN0 + 3.2, SPIN0 + 4.8), ('Asus', SPIN0 + 4.8, SPIN1)]
    for i, (name, a, b) in enumerate(plan):
        chord_block(name, a, b, 0.30 + 0.05 * i, cut=(700 + 300 * i, 1300 + 500 * i), attack=0.4, release=0.4, seed=60 + i)
        step = 0.17 if a < slow else 0.17 + (a - slow) * 0.06
        harp_run(name, a, b, step, 0.40 + 0.04 * i, order=(0, 3, 1, 4, 2, 5, 3, 4), seed=700 + 50 * i, rit=0.6 if a >= slow else 0.0)
        # el bajo en negras
        bt = a
        while bt < b:
            tb = tt(0.5)
            fb = hz(CH[name][0])
            xb = (np.sin(2 * np.pi * fb * tb) + 0.3 * np.sin(4 * np.pi * fb * tb)) * smooth(0, 0.02, tb) * np.exp(-tb / 0.35)
            place(out, xb, bt, 0.16)
            bt += 0.5 if a < slow else 0.62
    # un poco antes de pararse, se queda en suspenso (La con la cuarta, muy bajo) y deja sitio al premio
    t = tt(0.9)
    sus = strings(0.9, 'Asus', 90, (900, 1200)) * (smooth(0, 0.1, t) * (1 - smooth(0.25, 0.9, t)))[:, None]
    place(out, sus, SPIN1, 0.22)
    # el final: Re tranquilo, arpa y celesta
    chord_block('D', REVEAL + 0.9, END - 1.0, 0.30, attack=1.4, release=1.2, seed=95)
    harp_run('D', REVEAL + 1.1, END - 0.8, 0.36, 0.34, seed=900)
    for k, (nt, at) in enumerate((('D6', REVEAL + 1.7), ('A5', REVEAL + 2.15), ('F#5', REVEAL + 2.6), ('E5', REVEAL + 3.05),
                                  ('D5', REVEAL + 3.6))):
        place(out, pan2(celesta(hz(nt), 2.4, 0.6, seed=950 + k), 0.2), at, 0.42)
    out = sala(out, 3.6, 0.32, 4800, seed=2026)[:n]
    out = np.stack([hp(out[:, c], 35, 2) for c in range(2)], 1)
    out = out[:int(END * SR)]
    return norm(fade_out(out, 1.2), 0.85)


# ----------------------------------------------------------------------------------------------- los efectos
def llave_aparece():
    dur = 2.2
    t = tt(dur)
    out = np.zeros((len(t), 2))
    for k, nt in enumerate(('A5', 'D6', 'F#6', 'A6', 'D7')):
        place(out, pan2(celesta(hz(nt), 1.6, 0.6, seed=10 + k), -0.5 + 0.25 * k), 0.05 + k * 0.06, 0.6)
    air = whoosh(t, 0.35, 0.3, 0.6, 2500, 9000, seed=12)
    out += air * 0.08
    out += shimmer(t, [hz('A6'), hz('E7'), hz('A7')], seed=14) * swell(t, 0.5, 0.4, 0.7)[:, None] * 0.08
    out = sala(out, 2.2, 0.3, 6000, seed=15)[:len(t)]
    return norm(fade_out(out, 0.5), 0.7)


def llave_entra():
    dur = 0.9
    t = tt(dur)
    r = rng(20)
    # el roce: ruido por resonancias de metal
    scrape = r.standard_normal(len(t))
    scrape = bp(scrape, 2000, 2300) + bp(scrape, 3300, 3600) * 0.7 + bp(scrape, 5100, 5500) * 0.5
    scrape *= smooth(0, 0.03, t) * (1 - smooth(0.12, 0.17, t)) * 0.5
    # el clac: modos de metal y un golpe sordo
    clack = np.zeros(len(t))
    m = modal([1850, 2930, 4410, 6720], [0.05, 0.035, 0.025, 0.015], [1.0, 0.7, 0.5, 0.3], 0.5, seed=21)
    clack[int(0.16 * SR):int(0.16 * SR) + len(m)] += m[:len(t) - int(0.16 * SR)]
    th = thud(140, 0.3, 0.06)
    clack[int(0.16 * SR):int(0.16 * SR) + len(th)] += th[:len(t) - int(0.16 * SR)] * 0.7
    x = pan2(scrape + clack, 0.1)
    x = sala(x, 1.2, 0.18, 5000, seed=22)[:len(t)]
    return norm(fade_out(x, 0.2), 0.8)


def llave_gira():
    dur = 2.4
    t = tt(dur)
    x = np.zeros(len(t))
    for k, at in enumerate((0.0, 0.07, 0.14)):
        m = modal([3100, 4700, 6900], [0.012, 0.009, 0.006], [1, 0.6, 0.3], 0.08, seed=30 + k)
        i = int(at * SR)
        x[i:i + len(m)] += m * 0.5
    clunk = modal([880, 1460, 2350, 3700], [0.09, 0.06, 0.04, 0.02], [1.0, 0.8, 0.5, 0.25], 0.6, seed=34)
    i = int(0.28 * SR)
    x[i:i + len(clunk)] += clunk * 0.9
    th = thud(90, 0.4, 0.09)
    x[i:i + len(th)] += th * 0.8
    out = pan2(x, 0.05)
    place(out, pan2(campana(hz('D6'), 2.0, 0.7, seed=36), -0.1), 0.32, 0.35)
    place(out, pan2(celesta(hz('A6'), 1.5, 0.5, seed=37), 0.3), 0.38, 0.3)
    out = sala(out, 2.0, 0.25, 5500, seed=38)[:len(t)]
    return norm(fade_out(out, 0.6), 0.8)


def runas():
    dur = 2.6
    t = tt(dur)
    out = whoosh(t, 0.9, 0.8, 0.9, 300, 4000, seed=40) * 0.18
    hum = np.sin(2 * np.pi * hz('D2') * t) + 0.4 * np.sin(2 * np.pi * hz('A2') * t)
    out += pan2(hum * swell(t, 0.8, 0.7, 1.0), 0.0) * 0.18
    out += shimmer(t, [hz('A5'), hz('E6'), hz('A6'), hz('D7')], seed=42) * swell(t, 0.9, 0.6, 1.0)[:, None] * 0.22
    out = sala(out, 2.4, 0.3, 6000, seed=43)[:len(t)]
    return norm(fade_out(out, 0.6), 0.65)


def tiembla():
    dur = 1.1
    t = tt(dur)
    r = rng(50)
    rumble = lp(r.standard_normal(len(t)), 110) * smooth(0, 0.6, t) * (1 - smooth(0.85, 1.1, t)) * 3.0
    x = rumble.copy()
    at = 0.05
    k = 0
    while at < 0.92:
        m = modal([310 + r.random() * 60, 720 + r.random() * 90, 1400], [0.03, 0.02, 0.01], [1.0, 0.6, 0.2], 0.1, seed=51 + k)
        i = int(at * SR)
        x[i:i + len(m)] += m[:len(x) - i] * (0.4 + 0.5 * at)
        at += 0.12 - 0.07 * at + r.random() * 0.03
        k += 1
    out = pan2(x, 0.0)
    out = sala(out, 1.0, 0.15, 4000, seed=52)[:len(t)]
    return norm(fade_out(out, 0.15), 0.75)


def apertura():
    dur = 4.0
    t = tt(dur)
    out = whoosh(t, 0.06, 0.05, 0.7, 400, 8000, seed=60) * 0.3
    out += bloom(t, 0.05, [hz(v) for v in ('D5', 'F#5', 'A5', 'D6')], attack=0.04, decay=1.4, seed=61, bright=6000) * 0.5
    place(out, pan2(thud(36.7, 2.0, 0.6) + 0.35 * thud(73.4, 2.0, 0.35), 0.0), 0.0, 0.55)
    place(out, pan2(campana(hz('D6'), 3.0, 0.8, seed=62), 0.2), 0.04, 0.3)
    out += sparkle(dur, 22, 63, start=0.15, spread=0.7) * 0.45
    # la tapa: un crujido de madera corto
    cr = modal([420, 980, 1700], [0.05, 0.03, 0.02], [1, 0.6, 0.3], 0.2, seed=64)
    place(out, pan2(cr, -0.15), 0.0, 0.35)
    out = sala(out, 3.4, 0.32, 5200, seed=65)[:len(t)]
    return norm(fade_out(out, 1.0), 0.9)


def tic():
    dur = 0.22
    t = tt(dur)
    x = np.sin(2 * np.pi * hz('A5') * t) * np.exp(-t / 0.06) + 0.35 * np.sin(2 * np.pi * hz('A6') * t) * np.exp(-t / 0.025)
    x *= smooth(0, 0.002, t)
    x[:int(0.002 * SR)] += hp(rng(70).standard_normal(int(0.002 * SR)), 3000) * 0.2
    return norm(fade_out(pan2(x, 0.0), 0.05), 0.7)


def parada():
    dur = 1.6
    t = tt(dur)
    out = np.zeros((len(t), 2))
    place(out, pan2(campana(hz('A5'), 1.6, 0.8, seed=80), 0.0), 0.0, 0.5)
    place(out, pan2(thud(110, 0.4, 0.08), 0.0), 0.0, 0.4)
    out += shimmer(t, [hz('E6'), hz('A6'), hz('C#7')], seed=81) * swell(t, 0.3, 0.25, 0.9)[:, None] * 0.12
    out = sala(out, 1.8, 0.28, 5500, seed=82)[:len(t)]
    return norm(fade_out(out, 0.5), 0.75)


def premio(level):
    dur = 5.0 + level
    t = tt(dur)
    out = np.zeros((len(t), 2))
    # el glissando del arpa hasta arriba y la campana
    gl = ['D4', 'F#4', 'A4', 'D5', 'F#5', 'A5', 'D6']
    for k, nt in enumerate(gl):
        place(out, pan2(arpa(hz(nt), 2.6, 0.7, seed=100 + k, bright=1.3), -0.6 + 1.2 * k / len(gl)), k * 0.04, 0.5)
    place(out, pan2(campana(hz('D6'), 4.0, 0.9, seed=110), 0.0), 0.28, 0.45)
    # el acorde que florece (Re mayor), más grande cuanto mejor el premio
    voices = ['D4', 'A4', 'D5', 'F#5'] + (['A5'] if level >= 2 else []) + (['D3', 'D6'] if level >= 3 else [])
    out += bloom(t, 0.12, [hz(v) for v in voices], attack=0.1, decay=1.6 + 0.5 * level, seed=111, bright=4500) * (0.45 + 0.08 * level)
    if level >= 2:
        ch = choir(t, [hz('D4'), hz('F#4'), hz('A4'), hz('D5')], seed=112)
        out += ch * swell(t, 0.25, 0.2, 1.8 + 0.6 * level)[:, None] * (0.32 + 0.1 * (level - 2))
        for k, nt in enumerate(('D6', 'A5', 'F#5', 'D5')):
            place(out, pan2(campana(hz(nt), 3.0, 0.6, seed=113 + k), 0.4 - 0.27 * k), 0.5 + k * 0.18, 0.3)
    if level >= 3:
        # metales graves suaves (sierras filtradas en Re) y un golpe grave sin tambor
        tb = tt(dur)
        brass = pad(tb, [hz('D2'), hz('A2'), hz('D3'), hz('F#3')], 500, 2200, seed=120, spread=0.003, voices=3)
        out += brass * swell(tb, 0.25, 0.2, 2.2)[:, None] * 0.5
        place(out, pan2(thud(36.7, 2.4, 0.8), 0.0), 0.0, 0.6)
        place(out, pan2(campana(hz('D7'), 3.0, 0.5, seed=121), 0.5), 0.9, 0.25)
    out += sparkle(dur, 14 + 10 * level, 130 + level, start=0.1, spread=0.85) * 0.45
    out = sala(out, 3.8, 0.34, 5200, seed=140 + level)[:len(t)]
    return norm(fade_out(out, 1.6), 0.9)


SOUNDS = {
    'ambiente': ambiente, 'musica': musica, 'llave_aparece': llave_aparece, 'llave_entra': llave_entra,
    'llave_gira': llave_gira, 'runas': runas, 'tiembla': tiembla, 'apertura': apertura, 'tic': tic, 'parada': parada,
    'premio1': lambda: premio(1), 'premio2': lambda: premio(2), 'premio3': lambda: premio(3),
}


def main():
    wav_dir = sys.argv[sys.argv.index('--wav') + 1] if '--wav' in sys.argv else None
    only = [a for a in sys.argv[1:] if a in SOUNDS]
    tmp = tempfile.mkdtemp()
    os.makedirs(RES, exist_ok=True)
    for name, fn in SOUNDS.items():
        if only and name not in only:
            continue
        x = fn()
        espectro(x, 'crates/' + name)
        wav = os.path.join(wav_dir or tmp, f'crates_{name}.wav')
        os.makedirs(os.path.dirname(wav), exist_ok=True)
        write_wav(wav, x)
        out = os.path.join(RES, f'{name}.ogg')
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', out], check=True)
        print(f'   → {os.path.relpath(out)} ({os.path.getsize(out) // 1024} KB)')


if __name__ == '__main__':
    main()
