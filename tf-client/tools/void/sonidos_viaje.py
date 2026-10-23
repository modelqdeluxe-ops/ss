"""Los sonidos del viaje por el agujero de gusano y de la luz del Void (1.3.96): sintetizados aquí, sin muestras, sin golpes.

    python3 tools/void/sonidos_viaje.py [nombres, p. ej. luz/pozo_entrar] [--wav carpeta] [--medir]

Como los de la grieta del cielo (sonidos_cielo.py, de donde salen las herramientas): el dueño quiere «un sonido fino
corrido», no golpes uno tras otro. El viaje sonaba con varios de Minecraft (el portal, el cristal que se rompe, una
explosión, el ghast, el dragón...). Ahora (en assets/tfclient/sounds/viaje/ y luz/):

  viaje/ida      13 s, a los tiempos de TravelFx (se rasga a 1-2,2 s; puertas a 4,4 / 6,8 / 9,2 s). 1.3.98 (el dueño:
                 «sin sonido bueno»): la realidad cruje, se rasga y se abre con un braam; el viento del túnel que aletea
                 y gira, cada vez más rápido, y el tono de Shepard (la velocidad que no acaba); el Overworld (cuerdas en
                 Re mayor), el Nether (retumbo y coro de hombres oscuro), el End (coro alto y frío) y el Void (el tirón
                 grave y su coro); en cada puerta, un soplo que la cruza; al final, la luz del Alba (el coro en Re mayor)
  viaje/vuelta   igual al revés: Void, End, Nether y el Overworld al final
  viaje/llegada  la luz al llegar: un braam en Re mayor, luminoso y suave, con coro y cuerdas
  (1.3.97, el dueño: «los pozos de luz tienen un sonido feo y el del santuario también»: con los instrumentos de
  instrumentos.py, arpa, coro y campanas, en vez de acordes de senos)
  (1.3.115: sin coro ni campanas ni arpa; ver pozo_entrar)
  luz/pozo_entrar  al entrar en un Pozo de Luz: un soplo cálido, un colchón de senos que se abre y un brillo alto
  luz/velo_entrar  al entrar en el Velo del Alba: lo mismo, más grande y hondo
  luz/velo_salir   al salir de la luz: el soplo que se va y el colchón que se cierra
  luz/sin_luz      al quedarte sin luz: un zumbido grave en menor que crece, con un susurro
"""
import os
import subprocess
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'bosses'))
from sonidos_cielo import (braam, choir_o, cruje, glue, hall, rasgar, roll, shepard, slow_noise)  # noqa: E402
from sonidos_cielo import (bloom, band_sweep, choir, medir, noise2, pad, pan, shimmer, smooth, stereo_reverb, sub, swell, tear,  # noqa: E402
                           tt, whoosh)
from sonidos_grieta import SR, bp, hp, lp, lp_sweep, norm, reverb, write_wav  # noqa: E402

RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'sounds'))
# = TravelFx
OPEN0, OPEN1, GATES = 1.0, 2.2, (4.4, 6.8, 9.2)


def seg(t, a, b, fade=0.5):
    """1 dentro del tramo [a, b], con fundidos suaves de «fade» s."""
    return smooth(a - fade, a + fade, t) * (1 - smooth(b - fade, b + fade, t))


# (1.3.98, el dueño: «el agujero de gusano, sin sonido bueno»: el de la 1.3.96 eran acordes de senos con un brillo
# agudo; ahora con lo de la grieta del cielo: el crujido, el braam, el tono de Shepard, el coro de hombres y el viento)

def overworld(t, k):
    # cálido y abierto: cuerdas en Re mayor y aire claro
    air = noise2(len(t), 200)
    for c in range(2):
        air[:, c] = bp(air[:, c], 700, 3500)
    return pad(t, (146.83, 220.0, 293.66, 369.99), 700, 1300, seed=201) * 1.6 + air * 0.12


def nether(t, k):
    # el retumbo de la lava y un coro de hombres oscuro (Re menor)
    return roll(t, 0.9, seed=210, cutoff=220) * 0.8 + choir_o(t, (73.42, 110.0, 146.83, 174.61), seed=212, vib=0.006) * 2.2


def end(t, k):
    # frío y quieto: coro alto en La menor y cuerdas lejanas en Mi menor (sin brillos de cristal)
    return choir_o(t, (220.0, 261.63, 329.63), seed=222, vib=0.004) * 1.6 + pad(t, (82.41, 123.47, 164.81), 300, 700, seed=223) * 0.9


def void(t, k):
    # el tirón grave del Void y su coro (Si bemol sobre Re); al final de la ida, la luz del Alba que llega (Re mayor)
    pull = pan(sub(t, 44, 30, 0.8), 0) * 0.8
    dark = choir_o(t, (73.42, 116.54, 146.83, 174.61), seed=230, vib=0.006) * 1.8
    return pull + dark + roll(t, 0.5, seed=231, cutoff=180) * 0.6


WORLDS = [overworld, nether, end, void]


def _viaje(back):
    dur = 13.4
    t = tt(dur)
    x = np.zeros((len(t), 2))
    # el mundo cruje y se dobla (0-1,2 s), se rasga (1-2,4 s) y se abre el túnel con un braam
    x += cruje(t, smooth(0.0, 0.9, t) * (1 - smooth(1.3, 2.0, t)), 30 + 25 * smooth(0, 1.2, t), 1.0 + 0.3 * smooth(0, 1.2, t), seed=240) * 0.7
    x += pan(sub(t, 30, 52, 1.2), 0) * (smooth(0, 1.0, t) * (1 - smooth(1.4, 2.4, t)))[:, None] * 0.6
    x += rasgar(t, OPEN0, OPEN1 + 0.3, seed=241, f0=160, f1=900) * 0.7
    x += braam(t, 1.3, (36.71, 73.42, 110.0, 146.83, 174.61) if not back else (36.71, 73.42, 110.0, 146.83, 185.0), attack=0.3,
               hold=0.5, release=1.8, seed=242, bright=2600, drop=0.03) * 0.8
    # el viento del túnel, cada vez más rápido (el filtro sube con la velocidad; aletea y gira de un lado a otro)
    wind = band_sweep(noise2(len(t), 243), 260, 2400, 1.2)
    flutter = np.stack([slow_noise(len(t), 11, 244), slow_noise(len(t), 11, 245)], 1) ** 1.5 * 1.6
    pos = np.sin(2 * np.pi * np.cumsum(0.2 + 0.6 * smooth(2, 12, t)) / SR)
    wind = wind * flutter * np.stack([0.55 - 0.35 * pos, 0.55 + 0.35 * pos], 1)
    x += wind * (smooth(1.8, 3.0, t) * (1 - smooth(12.6, 13.3, t)))[:, None] * 0.55
    # la velocidad que no acaba: el tono de Shepard
    x += shepard(t, 0.0, speed=0.18) * (smooth(2.0, 3.5, t) * (1 - smooth(12.4, 13.2, t)))[:, None] * 0.45
    order = [3, 2, 1, 0] if back else [0, 1, 2, 3]
    bounds = [OPEN1, GATES[0], GATES[1], GATES[2], 13.4]
    for i, w in enumerate(order):
        a, b = bounds[i], bounds[i + 1]
        m = seg(t, a, b + (0.0 if i < 3 else 0.3), 0.45)
        sl = m > 1e-4
        part = np.zeros((len(t), 2))
        part[sl] = WORLDS[w](t[sl], i)
        x += part * m[:, None]
    # en cada puerta, un soplo que la cruza y un grave que sube (sin golpe)
    for g in GATES:
        x += whoosh(t, g, 0.35, 0.5, 600, 5000, seed=int(g * 10)) * 0.45
        x += pan(sub(t, 40, 70, 1.5), 0) * swell(t, g, 0.3, 0.4)[:, None] * 0.5
    if not back:
        # al final de la ida, la luz del Alba que se acerca: el coro en Re mayor que crece
        x += choir(t, (293.66, 369.99, 440.0, 587.33), seed=250, vib=0.004) * swell(t, 12.6, 1.6, 1.0)[:, None] * 1.6
    else:
        x += pad(t, (146.83, 220.0, 293.66, 369.99), 900, 2200, seed=251) * swell(t, 12.6, 1.4, 1.0)[:, None] * 1.4
    return hall(x, 3.2, 0.38, 2600, 252 if back else 253)


def ida():
    return _viaje(False)


def vuelta():
    return _viaje(True)


def llegada():
    """La luz al llegar: un braam en Re mayor, luminoso y suave, con el coro y las cuerdas (sin brillos de cristal)."""
    t = tt(4.5)
    x = braam(t, 0.1, (36.71, 73.42, 110.0, 146.83, 185.0), attack=0.35, hold=0.6, release=2.6, seed=260, bright=3200, drop=0.0,
              growl=0.1, drive=1.6) * 0.7
    x += choir(t, (293.66, 369.99, 440.0, 587.33), seed=261, vib=0.004) * swell(t, 0.5, 0.8, 2.4)[:, None] * 1.8
    x += pad(t, (146.83, 220.0, 293.66, 369.99), 1200, 2600, seed=262) * swell(t, 0.4, 0.6, 2.2)[:, None] * 1.2
    x += whoosh(t, 0.2, 0.3, 0.9, 5000, 600, seed=263) * 0.3
    return hall(x, 3.2, 0.45, 4000, 264)


def _gliss(notes, gap, vel, seed, at=0.0, pan_from=-0.5, pan_to=0.5):
    """Un glissando de arpa (estéreo): las notas una tras otra, abriéndose de izquierda a derecha."""
    from instrumentos import arpa, hz, pan2, place
    out = np.zeros((int((at + gap * len(notes) + 4.0) * SR), 2))
    for k, nme in enumerate(notes):
        p = pan_from + (pan_to - pan_from) * k / max(1, len(notes) - 1)
        place(out, pan2(arpa(hz(nme), 3.5, vel * (0.75 + 0.25 * k / len(notes)), seed=seed + k), p), at + k * gap)
    return out


# 1.3.115 (el dueño: «el sonido de entrar y salir de los pozos de luz del alba son horribles, parecen acordeones; pon un
# sonido mejor que no sean campanas ni sonidos de interruptores»): el «acordeón» era el coro sintetizado (sierras por
# formantes «aah», que suenan a armonio) y las campanas y el arpa, lo demás. Ahora la luz suena a **aire y calor**: un
# soplo que te envuelve, un colchón de senos puros (sin sierras: nada de acordeón) que se abre despacio y un brillo muy
# alto que tiembla; al salir, lo mismo al revés y apagándose. Sin golpes, sin campanas, sin clics.


def warm(t, freqs, seed, attack, decay, at=0.0, chorus=0.0035):
    """Colchón cálido de senos puros (tres voces apenas desafinadas por nota, estéreo), sin ataque."""
    r = np.random.default_rng(seed)
    out = np.zeros((len(t), 2))
    for f in freqs:
        for c in range(2):
            for v in range(3):
                det = 1 + r.uniform(-chorus, chorus)
                out[:, c] += np.sin(2 * np.pi * f * det * t + r.uniform(0, 6.28)) * (1.0 if f < 400 else 0.6)
    return out / (3 * len(freqs)) * swell(t, at, attack, decay)[:, None]


def pozo_entrar():
    """Entrar en un Pozo de Luz: un soplo cálido que te envuelve y sube, un colchón de senos en Re mayor que se abre y un
    brillo alto que tiembla. 2,6 s, suave."""
    t = tt(2.8)
    x = whoosh(t, 0.55, 0.5, 0.8, 300, 3200, seed=650) * 0.35
    x += warm(t, (146.83, 220.0, 293.66, 369.99), 651, 0.5, 1.0, at=0.5) * 0.9
    x += shimmer(t, (1174.66, 1479.98, 1760.0, 2349.32), seed=652) * swell(t, 0.8, 0.6, 0.9)[:, None] * 0.18
    x += np.stack([sub(t, 55, 73.42) * swell(t, 0.5, 0.45, 0.7)] * 2, 1) * 0.25
    return hall(x, 2.6, 0.4, 4500, 653)


def velo_entrar():
    """Entrar en el Velo del Alba (el santuario): lo mismo, más grande y más hondo; llegas a casa."""
    t = tt(3.6)
    x = whoosh(t, 0.8, 0.75, 1.1, 200, 4200, seed=660) * 0.4
    x += warm(t, (73.42, 146.83, 220.0, 293.66, 369.99, 440.0), 661, 0.8, 1.6, at=0.8) * 1.0
    x += shimmer(t, (1174.66, 1479.98, 1760.0, 2349.32, 2959.96), seed=662) * swell(t, 1.1, 0.8, 1.4)[:, None] * 0.2
    x += np.stack([sub(t, 41.2, 55.0) * swell(t, 0.8, 0.7, 1.2)] * 2, 1) * 0.3
    return hall(x, 3.4, 0.45, 4500, 663)


def velo_salir():
    """Salir de la luz: el soplo se va (de agudo a grave) y el colchón se apaga cerrándose; nada que suene a despedida
    triste ni a golpe."""
    t = tt(2.4)
    x = whoosh(t, 0.15, 0.15, 0.9, 2600, 250, seed=670) * 0.3
    pad_ = warm(t, (146.83, 220.0, 293.66), 671, 0.12, 0.8, at=0.12)
    for c in range(2):
        pad_[:, c] = lp_sweep(pad_[:, c], 3000, 300, 48)
    x += pad_ * 0.8
    return hall(x, 2.2, 0.35, 3000, 672)


def sin_luz():
    """Quedarse sin luz: un zumbido grave en menor que crece (senos, sin coro) y un susurro de aire lejano."""
    t = tt(3.4)
    x = warm(t, (61.74, 92.5, 123.47, 146.83), 680, 1.2, 1.4, at=1.2, chorus=0.006) * 1.1
    wh = noise2(len(t), 681)
    for c in range(2):
        wh[:, c] = bp(wh[:, c], 1400, 3600)
    am = (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(3 + 1.5 * np.sin(2 * np.pi * 0.6 * t)) / SR)) ** 4
    x += wh * (am * swell(t, 1.5, 1.2, 0.9))[:, None] * 0.1
    return hall(x, 3.2, 0.42, 2500, 682)


SOUNDS = {
    'viaje/ida': ida,
    'viaje/vuelta': vuelta,
    'viaje/llegada': llegada,
    'luz/pozo_entrar': pozo_entrar,
    'luz/velo_entrar': velo_entrar,
    'luz/velo_salir': velo_salir,
    'luz/sin_luz': sin_luz,
}
PEAK = {'luz/pozo_entrar': 0.55, 'luz/velo_entrar': 0.6, 'luz/velo_salir': 0.35, 'luz/sin_luz': 0.6}


def main():
    wav_dir = sys.argv[sys.argv.index('--wav') + 1] if '--wav' in sys.argv else None
    tmp = tempfile.mkdtemp()
    only = [a for a in sys.argv[1:] if '/' in a and not a.startswith('-') and a in SOUNDS]
    for name, fn in SOUNDS.items():
        if only and name not in only:
            continue
        x = fn()
        mono = x.ndim == 1
        if mono:
            x = hp(x, 40, 2)
        else:
            x = np.stack([hp(x[:, c], 28, 2) for c in range(2)], 1)
        # (1.3.98: el viaje, con la compresión lenta de la grieta del cielo: grande y lleno, sin subir el pico)
        if name.startswith('viaje/') and not mono:
            x = glue(x)
        x = norm(x, PEAK.get(name, 0.85))
        loud = np.nonzero((np.abs(x) if mono else np.abs(x).max(1)) > 0.85 * 10 ** (-60 / 20))[0]
        if len(loud):
            x = x[: min(len(x), loud[-1] + int(0.05 * SR))].copy()
            fo = int(0.3 * SR)
            x[-fo:] *= np.linspace(1, 0, fo) if mono else np.linspace(1, 0, fo)[:, None]
        if '--medir' in sys.argv:
            medir(name, x if not mono else np.stack([x, x], 1))
        wav = os.path.join(wav_dir or tmp, name.replace('/', '_') + '.wav')
        os.makedirs(os.path.dirname(wav), exist_ok=True)
        write_wav(wav, x)
        out = os.path.join(RES, name + '.ogg')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4',
                        '-ac', '1' if mono else '2', out], check=True)
        print(f'{name}: {len(x) / SR:.1f} s, {os.path.getsize(out) // 1024} KB')


if __name__ == '__main__':
    main()
