"""Las partituras de la banda sonora. Cada función devuelve la pieza ya mezclada (estéreo, en bucle perfecto).

Melodías en texto: «nota:pulsos[:intensidad]», con «-» para los silencios. Ejemplo: "A4:1 D5:1 F5:1.5 E5:0.5".
El motivo del Corazón (la–re–fa–mi… re) vuelve en el título, la Ciudad y la Cripta.
"""
import functools

import numpy as np

from motor import (SR, Pieza, arpa, bajo, caja, campana, celesta, chelo, coro, cuerdas, dron, flauta, gota, hz,
                   latido, marimba, metal, piano, platillo, sonaja, tambor_marco, taiko, timbal, viento)

_azar = np.random.default_rng(2024)


@functools.lru_cache(maxsize=4096)
def _nota(inst, f, dur, vel, extra):
    return inst(f, dur, vel=vel, **dict(extra))


def nota(inst, n, dur, vel=0.7, **kw):
    return _nota(inst, round(hz(n), 3), round(dur, 3), round(vel, 3), tuple(sorted(kw.items())))


def octava(texto, k):
    """La misma melodía k octavas más arriba (o abajo si k < 0)."""
    out = []
    for tok in texto.split():
        p = tok.split(':')
        if p[0] != '-':
            p[0] = p[0][:-1] + str(int(p[0][-1]) + k)
        out.append(':'.join(p))
    return ' '.join(out)


def melodia(P, inst, texto, compas, pan=0.0, gan=1.0, rev=0.35, legato=1.0, humano=0.012, **kw):
    """Pone una melodía empezando en el compás dado. Devuelve los pulsos que dura."""
    b = 0.0
    for tok in texto.split():
        partes = tok.split(':')
        n, pulsos = partes[0], float(partes[1])
        vel = float(partes[2]) if len(partes) > 2 else 0.7
        if n != '-':
            x = nota(inst, n, pulsos * 60 / P.bpm * legato, vel, **kw)
            P.poner(x, P.tiempo(compas, b) + _azar.normal(0, humano), pan, gan, rev)
        b += pulsos
    return b


def acorde(P, inst, notas, compas, pulsos, pan=0.0, gan=1.0, rev=0.4, abre=0.4, vel=0.6, **kw):
    """Las notas de un acorde a la vez, abiertas en el panorama."""
    for k, n in enumerate(notas):
        p = pan + (k / max(1, len(notas) - 1) - 0.5) * abre
        P.poner(nota(inst, n, pulsos * 60 / P.bpm, vel, **kw), P.tiempo(compas) + _azar.normal(0, 0.01), p, gan, rev)


def golpe(P, x, compas, pulso, pan=0.0, gan=1.0, rev=0.25):
    P.poner(x, P.tiempo(compas, pulso), pan, gan, rev)


# ================================================================ TÍTULO: «Corazón Dormido»
def titulo():
    bpm, C = 66, 28
    P = Pieza(C * 4 * 60 / bpm, bpm, rt60=4.2, humedo=0.36, oscuro=8000)
    prog = [['D3', 'A3', 'D4', 'F4'], ['Bb2', 'F3', 'Bb3', 'D4'], ['G2', 'D3', 'Bb3', 'D4'], ['A2', 'E3', 'A3', 'C#4'],
            ['D3', 'A3', 'D4', 'F4'], ['F2', 'C3', 'A3', 'C4'], ['G2', 'D3', 'Bb3', 'D4'], ['A2', 'E3', 'G3', 'C#4']]
    raices = ['D2', 'Bb1', 'G1', 'A1', 'D2', 'F1', 'G1', 'A1']
    # el latido, siempre: un poco más fuerte cuando entra el tema
    for c in range(C):
        golpe(P, latido(0.55 if c < 4 or c >= 24 else 0.7), c, 0, gan=0.55, rev=0.2)
        golpe(P, latido(0.35), c, 2, gan=0.4, rev=0.2)
    P.poner(dron(hz('D2'), C * 4 * 60 / bpm - 2, 0.35), 0, 0, 0.45, 0.3)
    # introducción: el coro solo, como algo que se despierta
    for c in range(4):
        acorde(P, coro, ['D4', 'F4', 'A4'] if c % 2 == 0 else ['D4', 'G4', 'Bb4'], c, 4, vel=0.4, vocal='o', ataque=1.4)
    # A: el piano dice el motivo sobre las cuerdas
    tema = ["A4:1 D5:1 F5:1.5 E5:0.5", "D5:2 C5:1 A4:1", "Bb4:1.5 A4:0.5 G4:1 F4:1", "E4:3 A4:1",
            "A4:1 D5:1 F5:1.5 E5:0.5", "D5:1.5 E5:0.5 F5:1 G5:1", "F5:1 E5:1 D5:1 Bb4:1", "C#5:3 -:1"]
    for k in range(8):
        c = 4 + k
        acorde(P, cuerdas, prog[k], c, 4, vel=0.45, gan=0.8, ataque=0.9)
        P.poner(nota(bajo, raices[k], 4 * 60 / bpm, 0.6), P.tiempo(c), 0, 0.45, 0.2)
        melodia(P, piano, tema[k], c, pan=0.1, gan=1.3, rev=0.4)
        for b in range(4):  # el piano acompaña con el acorde partido, muy suave
            P.poner(nota(piano, prog[k][1 + b % 3], 0.9, 0.35), P.tiempo(c, b + 0.5), -0.25, 0.5, 0.45)
    # B: entra el coro con la melodía, las campanas marcan cada compás
    temaB = ["D5:2 F5:1 A5:1", "Bb5:2 A5:1 F5:1", "G5:1.5 F5:0.5 E5:1 D5:1", "E5:4",
             "F5:2 E5:1 D5:1", "C5:2 A4:1 C5:1", "D5:1 Bb4:1 G4:1 Bb4:1", "A4:4"]
    for k in range(8):
        c = 12 + k
        acorde(P, cuerdas, [octava(n + ':1', 1).split(':')[0] if n[-1] == '2' else n for n in prog[k]], c, 4, vel=0.6, gan=0.9, ataque=0.6)
        acorde(P, coro, prog[k][1:], c, 4, vel=0.3, gan=0.7, vocal='a')
        P.poner(nota(bajo, raices[k], 4 * 60 / bpm, 0.7), P.tiempo(c), 0, 0.5, 0.2)
        melodia(P, coro, temaB[k], c, pan=0.0, gan=1.0, rev=0.5, vocal='a', voces=4, ataque=0.25)
        melodia(P, cuerdas, octava(temaB[k], -1), c, pan=-0.2, gan=0.5, ataque=0.2)
        P.poner(nota(campana, prog[k][2][:-1] + '5', 4.5, 0.4), P.tiempo(c), 0.3, 0.5, 0.6)
    # C: vuelve el motivo en el piano solo, más lento, y se apaga hacia el latido
    for k in range(4):
        c = 20 + k
        acorde(P, cuerdas, prog[k], c, 4, vel=0.35, gan=0.6, ataque=1.2)
        melodia(P, piano, tema[k], c, pan=0.1, gan=0.9)
    for k in range(4):
        c = 24 + k
        acorde(P, coro, ['D4', 'F4', 'A4'] if k % 2 == 0 else ['C#4', 'E4', 'A4'], c, 4, vel=0.3, vocal='o', ataque=1.2)
    golpe(P, platillo(0.25, 4.5, crece=1), 26, 2, gan=0.6, rev=0.5)
    return P.render(-17)


# ================================================================ INTERIOR: la cueva (Raíces, Arcilla, Ruinas)
def interior():
    bpm, C = 72, 40
    P = Pieza(C * 4 * 60 / bpm, bpm, rt60=4.8, oscuro=4200, humedo=0.42)
    prog = [['A2', 'E3', 'B3', 'C4'], ['F2', 'C3', 'E3', 'A3'], ['C3', 'G3', 'B3', 'E4'], ['E2', 'B2', 'D3', 'G3'],
            ['D2', 'A2', 'E3', 'F3'], ['A2', 'E3', 'A3', 'C4'], ['F2', 'C3', 'A3', 'E4'], ['E2', 'B2', 'E3', 'G#3']]
    total = C * 4 * 60 / bpm
    P.poner(dron(hz('A1'), total - 3, 0.3, sem=1), 0, -0.1, 0.8, 0.4)
    P.poner(viento(total, 0.06, 420), 0, 0, 1, 0.5)
    for c in range(C):
        k = c % 8
        # arpa: el acorde partido, subiendo y bajando
        orden = [0, 1, 2, 3, 2, 1, 2, 3] if c % 2 == 0 else [0, 2, 1, 3, 2, 3, 1, 2]
        for b, i in enumerate(orden):
            n = prog[k][i]
            if b == 0:
                n = prog[k][0]
            P.poner(nota(arpa, n, 2.4, 0.45 + 0.1 * (b % 4 == 0), brillo=0.35), P.tiempo(c, b * 0.5) + _azar.normal(0, 0.008), -0.35 + 0.1 * (i - 1.5), 0.55, 0.45)
        if c >= 8:
            acorde(P, cuerdas, prog[k][1:], c, 4, vel=0.28, gan=0.45, ataque=1.4, voces=4)
        # gotas sueltas, como en la cueva
        for _ in range(2):
            if _azar.random() < 0.55:
                P.poner(gota(vel=0.18), P.tiempo(c, _azar.random() * 4), _azar.uniform(-0.8, 0.8), 0.6, 0.8)
    # la celesta: una melodía que aparece y se va
    frases = ["E5:1.5 D5:0.5 C5:2", "A4:3 -:1", "B4:1 C5:1 E5:2", "D5:3 -:1",
              "F5:1.5 E5:0.5 D5:1 C5:1", "E5:4", "C5:1 B4:1 A4:2", "G#4:3 -:1"]
    for rep, c0 in enumerate((8, 24)):
        for k in range(8):
            melodia(P, celesta, frases[k], c0 + k, pan=0.3, gan=0.8 if rep == 0 else 0.7, rev=0.6)
    frases2 = ["A3:2 C4:2", "F3:4", "G3:2 E3:2", "B2:4", "D3:2 F3:2", "E3:4", "C3:2 E3:2", "E3:4"]
    for k in range(8):
        melodia(P, chelo, frases2[k], 16 + k, pan=-0.2, gan=0.55, rev=0.45)
    for c in (0, 16, 32):
        golpe(P, campana(hz('A2'), 6, 0.35), c, 0, pan=0.2, gan=0.5, rev=0.7)
    return P.render(-19)


# ================================================================ SELVA ESMERALDA
def selva():
    bpm, C = 88, 40
    P = Pieza(C * 4 * 60 / bpm, bpm, rt60=2.6, oscuro=6500, humedo=0.3)
    prog = [['E3', 'B3', 'D4', 'G4'], ['A2', 'E3', 'G3', 'C#4'], ['C3', 'G3', 'B3', 'E4'], ['D3', 'A3', 'C4', 'F#4']]
    raices = ['E2', 'A1', 'C2', 'D2']
    ost = [['E4', 'B4', 'D5', 'B4', 'G4', 'B4', 'D5', 'B4'], ['A4', 'E4', 'C#5', 'E4', 'G4', 'E4', 'C#5', 'E4'],
           ['C5', 'G4', 'B4', 'G4', 'E4', 'G4', 'B4', 'G4'], ['D5', 'A4', 'C5', 'A4', 'F#4', 'A4', 'C5', 'A4']]
    for c in range(C):
        k = c % 4
        intro = c < 4
        for b, n in enumerate(ost[k]):
            P.poner(nota(marimba, n, 1.2, 0.55 if b % 2 == 0 else 0.4), P.tiempo(c, b * 0.5) + _azar.normal(0, 0.006), 0.35 if b % 2 else 0.2, 0.7, 0.25)
        if not intro:
            P.poner(nota(bajo, raices[k], 4 * 60 / bpm, 0.65), P.tiempo(c), 0, 0.75, 0.15)
            golpe(P, tambor_marco(0.6), c, 0, -0.2, 0.8)
            golpe(P, tambor_marco(0.35), c, 1.5, -0.2, 0.7)
            golpe(P, tambor_marco(0.5), c, 2, -0.2, 0.8)
            for b in range(8):
                golpe(P, sonaja(0.35 if b % 2 else 0.2), c, b * 0.5 + 0.02, 0.5, 0.6, 0.1)
        if c >= 8:
            acorde(P, cuerdas, prog[k][1:], c, 4, vel=0.35, gan=0.55, ataque=0.8, voces=4)
    tema = ["B4:1.5 D5:0.5 E5:2", "C#5:1 B4:1 A4:2", "G4:1.5 A4:0.5 B4:1 E5:1", "D5:3 -:1",
            "E5:1 F#5:1 G5:1.5 F#5:0.5", "E5:2 C#5:2", "B4:1 C5:1 D5:1 E5:1", "F#5:3 -:1"]
    for rep, c0 in enumerate((8, 24)):
        for k in range(8):
            melodia(P, flauta, tema[k], c0 + k, pan=-0.15, gan=0.9 if rep == 0 else 1.0, rev=0.35)
    # puente: la flauta calla, el chelo canta abajo
    puente = ["E3:2 G3:2", "A3:3 C#4:1", "B3:2 G3:2", "A3:4", "G3:2 B3:2", "C#4:2 E4:2", "D4:2 C4:2", "B3:4"]
    for k in range(8):
        melodia(P, chelo, puente[k], 16 + k, pan=0.2, gan=0.6)
    for c in (7, 23, 39):
        golpe(P, platillo(0.3, 2.7, crece=1), c, 1, 0.3, 0.5, 0.4)
    return P.render(-17)


# ================================================================ CIÉNAGA DE LA PODRE
def cienaga():
    bpm, C = 56, 24
    total = C * 4 * 60 / bpm
    P = Pieza(total, bpm, rt60=3.6, oscuro=3200, humedo=0.4)
    P.poner(dron(hz('C#2'), total - 3, 0.4, sem=4), 0, 0, 0.9, 0.3)
    P.poner(viento(total, 0.05, 300, sem=9), 0, 0, 1, 0.4)
    for c in range(C):
        golpe(P, timbal(0.45, hz('C#2')), c, 0, 0, 0.7, 0.3)
        if c % 2 == 1:
            golpe(P, timbal(0.25, hz('C#2')), c, 2.5, 0, 0.6, 0.3)
        # croar: pulsos de cuerda apagada que suben de tono
        if c >= 4 and c % 4 in (1, 3):
            for b, n in enumerate(['C#3', 'D3', 'C#3', 'E3'][: 2 + c % 3]):
                P.poner(nota(arpa, n, 0.5, 0.5, brillo=0.1), P.tiempo(c, 1 + b * 0.33), -0.5, 0.5, 0.3)
    # el coro en racimos que chocan (do#, re, mi) y se deshacen
    racimos = [['C#4', 'D4', 'G#4'], ['C#4', 'E4', 'A4'], ['D4', 'F#4', 'A4'], ['C#4', 'D4', 'G#4']]
    for k, c in enumerate(range(4, 20, 4)):
        acorde(P, coro, racimos[k], c, 7, vel=0.35, vocal='u', ataque=2.0, gan=0.8, abre=0.8)
    # el chelo, muy abajo
    linea = ["C#3:3 D3:1", "E3:2 D3:2", "C#3:4", "-:4", "G#2:3 A2:1", "B2:2 A2:1 G#2:1", "A2:2 D3:2", "C#3:4"]
    for rep in (0, 1):
        for k in range(8):
            melodia(P, chelo, linea[k], 4 + rep * 8 + k, pan=-0.25, gan=0.75, legato=1.0)
    # campanas desafinadas a lo lejos
    for c in range(2, C, 3):
        golpe(P, campana(hz('D5') * (1.0 + 0.012 * (c % 2)), 5, 0.25), c, 1, 0.6, 0.5, 0.75)
        golpe(P, campana(hz('C#5'), 5, 0.2), c, 1.5, -0.6, 0.45, 0.75)
    for c in range(0, C, 2):
        P.poner(gota(500 + 200 * _azar.random(), 0.22), P.tiempo(c, _azar.random() * 4), _azar.uniform(-0.7, 0.7), 0.7, 0.7)
    return P.render(-19)


# ================================================================ CIUDAD VELADA
def ciudad():
    bpm, C = 70, 32
    P = Pieza(C * 4 * 60 / bpm, bpm, rt60=3.8, oscuro=5200, humedo=0.38)
    prog = [['F2', 'C3', 'Ab3', 'C4'], ['Db2', 'Ab2', 'F3', 'Ab3'], ['Ab2', 'Eb3', 'Ab3', 'C4'], ['Eb2', 'Bb2', 'G3', 'Bb3'],
            ['Bb1', 'F2', 'Db3', 'F3'], ['C2', 'Ab2', 'C3', 'F3'], ['C2', 'G2', 'E3', 'Bb3'], ['F2', 'C3', 'F3', 'Ab3']]
    for c in range(C):
        k = c % 8
        # la celesta: arpegio de caja de música
        for b, i in enumerate([1, 2, 3, 2, 3, 2, 1, 2]):
            n = prog[k][i]
            oct_ = int(n[-1]) + 2
            P.poner(nota(celesta, n[:-1] + str(oct_), 1.6, 0.4 if b % 2 else 0.5), P.tiempo(c, b * 0.5) + _azar.normal(0, 0.006), 0.35, 0.55, 0.5)
        P.poner(nota(bajo, prog[k][0], 4 * 60 / bpm, 0.55), P.tiempo(c), 0, 0.65, 0.2)
        if c >= 8:
            acorde(P, coro, prog[k][1:], c, 4, vel=0.25, vocal='a', gan=0.6, ataque=1.0)
        if c >= 16:
            acorde(P, cuerdas, prog[k][1:], c, 4, vel=0.35, gan=0.6, ataque=0.8)
    # el chelo canta el motivo del Corazón, en fa menor
    tema = ["C4:1 F4:1 Ab4:1.5 G4:0.5", "F4:2 Eb4:1 C4:1", "Eb4:1.5 C4:0.5 Ab3:2", "Bb3:3 -:1",
            "Db4:1 C4:1 Bb3:1 Ab3:1", "C4:2 F4:2", "E4:1.5 F4:0.5 G4:1 Bb4:1", "Ab4:3 -:1"]
    for k in range(8):
        melodia(P, chelo, tema[k], 8 + k, pan=-0.15, gan=0.9)
    # el piano lo repite arriba, con el coro detrás
    for k in range(8):
        melodia(P, piano, octava(tema[k], 1), 16 + k, pan=0.1, gan=0.9)
    for k in range(8):
        melodia(P, coro, octava(tema[k], 1), 24 + k, gan=0.55, vocal='o', voces=4, ataque=0.3)
    for c in (0, 8, 16, 24):
        golpe(P, campana(hz('F3'), 6, 0.3), c, 0, -0.3, 0.45, 0.7)
    return P.render(-18)


# ================================================================ LA CRIPTA DE LOS VELADORES
def cripta():
    bpm, C = 48, 20
    total = C * 4 * 60 / bpm
    P = Pieza(total, bpm, rt60=5.5, oscuro=3600, humedo=0.45)
    P.poner(dron(hz('B1'), total - 3, 0.45, sem=6), 0, 0, 0.9, 0.35)
    for c in range(C):
        golpe(P, latido(0.5), c, 0, 0, 0.75, 0.25)
        if c % 2 == 0:
            golpe(P, campana(hz('B2'), 8, 0.45), c, 0, 0.15, 0.7, 0.8)
    # coro grave en acordes disminuidos que se arrastran
    acs = [['B2', 'D3', 'F3'], ['C3', 'E3', 'G3'], ['B2', 'D3', 'F3'], ['A#2', 'C#3', 'E3'],
           ['B2', 'D3', 'F#3'], ['G2', 'B2', 'D3'], ['F#2', 'A#2', 'C#3'], ['B2', 'D3', 'F3']]
    for k in range(8):
        acorde(P, coro, acs[k], 2 + k * 2, 8, vel=0.4, vocal='o', ataque=2.5, gan=0.85, abre=0.6)
    # cuerdas agudas, un hilo que tiembla
    for k, n in enumerate(['F#5', 'G5', 'F5', 'F#5']):
        P.poner(nota(cuerdas, n, 4 * 4 * 60 / bpm, 0.25, ataque=3.0, voces=3), P.tiempo(4 + k * 4), 0.3, 0.35, 0.6)
    # el motivo del Corazón, roto, en la celesta
    melodia(P, celesta, "F#4:1 B4:1 D5:1.5 C#5:0.5 B4:4", 10, pan=-0.3, gan=0.6, rev=0.7)
    melodia(P, celesta, "F#4:1 B4:1 D5:1.5 C5:0.5 B4:4", 16, pan=-0.3, gan=0.5, rev=0.7)
    return P.render(-19)


# ================================================================ JEFE (los guardianes y los titanes)
def _pelea(bpm, tono, prog, raices, ost, tema, sem=0):
    C = 48
    P = Pieza(C * 4 * 60 / bpm, bpm, rt60=2.4, oscuro=6000, humedo=0.25)
    def sube(n, k):
        return n[:-1] + str(int(n[-1]) + k)
    for c in range(C):
        k = c % len(prog)
        sec = c // 16  # 0: entra, 1: tema, 2: el coro y la subida
        # taikos: el patrón de la pelea
        patron = [(0, 1.0), (1.5, 0.6), (2, 0.85), (3, 0.5), (3.5, 0.7)] if sec != 2 or c % 16 < 12 else [(b * 0.5, 0.6 + 0.05 * b) for b in range(8)]
        for b, v in patron:
            golpe(P, taiko(v, 1.0 if b % 2 == 0 else 1.3), c, b, -0.1 if b % 1 else 0.1, 0.9, 0.25)
        golpe(P, caja(0.45), c, 1, 0.2, 0.55, 0.2)
        golpe(P, caja(0.5), c, 3, 0.2, 0.6, 0.2)
        # ostinato de cuerdas a corcheas
        for b, n in enumerate(ost[k]):
            P.poner(nota(cuerdas, n, 0.3, 0.75 if b % 4 == 0 else 0.55, ataque=0.01, suelta=0.15, voces=3, brillo=1.2),
                    P.tiempo(c, b * 0.5), -0.3, 0.65, 0.2)
        P.poner(nota(bajo, raices[k], 4 * 60 / bpm * 0.95, 0.9), P.tiempo(c), 0, 0.9, 0.1)
        P.poner(nota(bajo, raices[k], 2 * 60 / bpm * 0.9, 0.7), P.tiempo(c, 2.5), 0, 0.7, 0.1)
        if sec >= 1:
            acorde(P, metal, prog[k], c, 1.5 if sec == 1 else 3.5, vel=0.75, gan=0.6, rev=0.3)
        if sec == 2:
            acorde(P, coro, [sube(n, 1) for n in prog[k][1:]], c, 4, vel=0.45, vocal='a', gan=0.8, ataque=0.3)
        if c % 8 == 7:
            golpe(P, platillo(0.4, 60 / bpm * 4, crece=1), c, 0, 0.4, 0.5, 0.4)
        if c % 8 == 0:
            golpe(P, platillo(0.5, 3), c, 0, -0.4, 0.5, 0.4)
    for rep, c0 in enumerate((16, 32)):
        for k, fr in enumerate(tema):
            melodia(P, metal if rep == 0 else coro, fr, c0 + k, pan=0.1, gan=0.9 if rep == 0 else 0.85, rev=0.3, **({'vocal': 'a', 'voces': 4, 'ataque': 0.08} if rep else {}))
            melodia(P, cuerdas, fr, c0 + k, pan=-0.1, gan=0.55, rev=0.3, ataque=0.05)
    return P.render(-14)


def jefe():
    prog = [['D3', 'A3', 'D4', 'F4'], ['Bb2', 'F3', 'Bb3', 'D4'], ['C3', 'G3', 'C4', 'E4'], ['A2', 'E3', 'A3', 'C#4']] * 2
    prog += [['G2', 'D3', 'G3', 'Bb3'], ['Bb2', 'F3', 'Bb3', 'D4'], ['A2', 'E3', 'A3', 'C#4'], ['A2', 'E3', 'G3', 'C#4']] * 2
    raices = ['D2', 'Bb1', 'C2', 'A1'] * 2 + ['G1', 'Bb1', 'A1', 'A1'] * 2
    ost = [['D4', 'D4', 'F4', 'D4', 'E4', 'D4', 'C4', 'D4'], ['D4', 'D4', 'F4', 'D4', 'Bb3', 'D4', 'F4', 'D4'],
           ['E4', 'E4', 'G4', 'E4', 'C4', 'E4', 'G4', 'E4'], ['C#4', 'C#4', 'E4', 'C#4', 'A3', 'C#4', 'E4', 'C#4']] * 2
    ost += [['D4', 'D4', 'G4', 'D4', 'Bb3', 'D4', 'G4', 'D4'], ['F4', 'F4', 'Bb4', 'F4', 'D4', 'F4', 'Bb4', 'F4'],
            ['E4', 'E4', 'A4', 'E4', 'C#4', 'E4', 'A4', 'E4'], ['E4', 'G4', 'A4', 'C#5', 'A4', 'G4', 'E4', 'C#4']] * 2
    tema = ["D4:1.5 A4:0.5 A4:2", "Bb4:1 A4:1 G4:1 F4:1", "G4:1.5 E4:0.5 C4:2", "C#4:2 E4:2",
            "D4:1.5 A4:0.5 D5:2", "C5:1 Bb4:1 A4:1 F4:1", "E4:1.5 G4:0.5 E4:1 C4:1", "A4:4",
            "G4:2 Bb4:2", "D5:1.5 C5:0.5 Bb4:2", "A4:1 G4:1 E4:2", "C#4:4",
            "G4:1 A4:1 Bb4:2", "F4:1 Bb4:1 D5:2", "E5:1.5 D5:0.5 C#5:2", "A4:2 C#5:2"]
    return _pelea(140, 'D', prog, raices, ost, tema)


def jefe2():
    """La pelea de las bandas (Grul y el Centinela): más grave y pesada, en do menor."""
    prog = [['C3', 'G3', 'C4', 'Eb4'], ['Ab2', 'Eb3', 'Ab3', 'C4'], ['F2', 'C3', 'F3', 'Ab3'], ['G2', 'D3', 'G3', 'B3']] * 2
    prog += [['Ab2', 'Eb3', 'Ab3', 'C4'], ['Bb2', 'F3', 'Bb3', 'D4'], ['G2', 'D3', 'G3', 'B3'], ['G2', 'D3', 'F3', 'B3']] * 2
    raices = ['C2', 'Ab1', 'F1', 'G1'] * 2 + ['Ab1', 'Bb1', 'G1', 'G1'] * 2
    ost = [['C4', 'G3', 'C4', 'Eb4', 'D4', 'C4', 'G3', 'C4'], ['C4', 'Ab3', 'C4', 'Eb4', 'C4', 'Ab3', 'Eb3', 'Ab3'],
           ['C4', 'F3', 'Ab3', 'C4', 'Ab3', 'F3', 'C4', 'F3'], ['B3', 'G3', 'D4', 'G3', 'B3', 'D4', 'F4', 'D4']] * 2
    ost += [['C4', 'Ab3', 'Eb4', 'Ab3', 'C4', 'Eb4', 'Ab4', 'Eb4'], ['D4', 'Bb3', 'F4', 'Bb3', 'D4', 'F4', 'Bb4', 'F4'],
            ['B3', 'G3', 'D4', 'G3', 'B3', 'D4', 'G4', 'D4'], ['B3', 'D4', 'F4', 'Ab4', 'G4', 'F4', 'D4', 'B3']] * 2
    tema = ["C4:2 G4:2", "Ab4:1.5 G4:0.5 Eb4:2", "F4:1 Eb4:1 C4:2", "D4:3 B3:1",
            "C4:2 Eb4:1 G4:1", "C5:2 Bb4:1 Ab4:1", "Ab4:1 G4:1 F4:1 Eb4:1", "D4:4",
            "Eb4:2 Ab4:2", "F4:2 Bb4:2", "G4:1.5 F4:0.5 D4:2", "B3:4",
            "C4:1 Eb4:1 Ab4:2", "Bb4:1 D5:1 F5:2", "Eb5:1.5 D5:0.5 B4:2", "G4:2 B4:2"]
    return _pelea(124, 'C', prog, raices, ost, tema, sem=3)


TEMAS = {'titulo': titulo, 'interior': interior, 'selva': selva, 'cienaga': cienaga, 'ciudad': ciudad,
         'cripta': cripta, 'jefe': jefe, 'jefe2': jefe2}
