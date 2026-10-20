"""Los sonidos del viaje por el agujero de gusano y de la luz del Void (1.3.96): sintetizados aquí, sin muestras, sin golpes.

    python3 tools/void/sonidos_viaje.py [nombres, p. ej. luz/pozo_entrar] [--wav carpeta] [--medir]

Como los de la grieta del cielo (sonidos_cielo.py, de donde salen las herramientas): el dueño quiere «un sonido fino
corrido», no golpes uno tras otro. El viaje sonaba con varios de Minecraft (el portal, el cristal que se rompe, una
explosión, el ghast, el dragón...). Ahora (en assets/tfclient/sounds/viaje/ y luz/):

  viaje/ida      13 s, a los tiempos de TravelFx (se rasga a 1-2,2 s; puertas a 4,4 / 6,8 / 9,2 s): el mundo que se
                 deforma, el desgarro, el viento del túnel que corre cada vez más; el Overworld (aire claro y un acorde
                 mayor), el Nether (retumbo grave, lava que borbotea, un coro oscuro), el End (un brillo alto y quieto, un
                 lamento lejano que baja) y el Void (un grave que tira hacia abajo y el coro dorado del Alba que sube); en
                 cada puerta, un soplo que sube y cambia el acorde
  viaje/vuelta   igual al revés: Void, End, Nether y el Overworld al final
  viaje/llegada  la luz al llegar: un acorde que florece y un brillo que se queda
  (1.3.97, el dueño: «los pozos de luz tienen un sonido feo y el del santuario también»: con los instrumentos de
  instrumentos.py, arpa, coro y campanas, en vez de acordes de senos)
  luz/pozo_entrar  al entrar en un Pozo de Luz: glissando de arpa que sube, un «aah» y una campana
  luz/velo_entrar  al entrar en el Velo del Alba: el coro que florece, el arpa y dos campanas
  luz/velo_salir   al salir de la luz: cuatro notas de arpa que bajan
  luz/sin_luz      al quedarte sin luz: un coro grave en menor que crece, con un susurro
"""
import os
import subprocess
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'bosses'))
from sonidos_cielo import (bloom, band_sweep, choir, medir, noise2, pad, pan, shimmer, smooth, stereo_reverb, sub, swell, tear,  # noqa: E402
                           tt, whoosh)
from sonidos_grieta import SR, bp, hp, lp, lp_sweep, norm, reverb, write_wav  # noqa: E402

RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'sounds'))
# = TravelFx
OPEN0, OPEN1, GATES = 1.0, 2.2, (4.4, 6.8, 9.2)


def seg(t, a, b, fade=0.5):
    """1 dentro del tramo [a, b], con fundidos suaves de «fade» s."""
    return smooth(a - fade, a + fade, t) * (1 - smooth(b - fade, b + fade, t))


def overworld(t, k):
    air = noise2(len(t), 200)
    for c in range(2):
        air[:, c] = bp(air[:, c], 900, 5000)
    chord = bloom(t, -1, (293.66, 369.99, 440.0, 587.33), attack=0.1, decay=1e9, seed=201, bright=6000)
    return air * 0.18 + chord * 1.6 * (1 + 0.15 * np.sin(2 * np.pi * 0.4 * t))[:, None]


def nether(t, k):
    rum = noise2(len(t), 210)
    for c in range(2):
        rum[:, c] = lp(rum[:, c], 140)
    # la lava que borbotea: ruido medio que sube y baja a ritmos lentos y desiguales (suave, sin chasquidos)
    bub = noise2(len(t), 211)
    for c in range(2):
        bub[:, c] = bp(bub[:, c], 180, 700)
    am = (0.5 + 0.5 * np.sin(2 * np.pi * 3.1 * t + 2 * np.sin(2 * np.pi * 0.7 * t))) ** 3
    dark = choir(t, (73.42, 87.31, 110.0), seed=212, vib=0.006)
    return rum * 1.4 + bub * am[:, None] * 0.5 + dark * 1.8 + pan(sub(t, 41, 38), 0) * 0.5


def end(t, k):
    hi = shimmer(t, (659.26, 739.99, 830.61, 1046.5, 1318.5), seed=220)
    air = noise2(len(t), 221)
    for c in range(2):
        air[:, c] = bp(air[:, c], 400, 2400)
    # un lamento lejano que baja (como el del dragón, pero hecho de coro)
    moan = choir(t, (220.0,), seed=222, vib=0.02)
    moan = moan * (np.exp(-((t - (t[0] + 1.2)) / 0.9) ** 2))[:, None]
    return hi * 0.5 + air * 0.12 + moan * 1.5


def void(t, k):
    pull = pan(sub(t, 48, 30, 0.8), 0) * 0.9
    gold = choir(t, (293.66, 369.99, 440.0), seed=230, vib=0.005) * smooth(t[0], t[0] + 2.5, t)[:, None]
    swirl = noise2(len(t), 231)
    for c in range(2):
        swirl[:, c] = bp(swirl[:, c], 120, 900)
    pos = np.sin(2 * np.pi * np.cumsum(0.3 + 0.8 * smooth(t[0], t[-1], t)) / SR)
    swirl = swirl * np.stack([0.5 - 0.35 * pos, 0.5 + 0.35 * pos], 1)
    return pull + gold * 1.8 + swirl * 0.5


WORLDS = [overworld, nether, end, void]


def _viaje(back):
    dur = 13.4
    t = tt(dur)
    x = np.zeros((len(t), 2))
    # el mundo se deforma (0-1 s) y se rasga (1-2,2 s)
    warp = pan(sub(t, 30, 55, 1.2), 0) * (smooth(0, 1.0, t) * (1 - smooth(1.2, 2.4, t)))[:, None]
    rip = tear(t, OPEN0, OPEN1, seed=240) * 0.8
    # el viento del túnel, cada vez más rápido (sube el filtro con la velocidad)
    wind = noise2(len(t), 241)
    wind = band_sweep(wind, 300, 2600, 1.2) * (smooth(1.4, 2.6, t) * (1 - smooth(12.6, 13.3, t)))[:, None] * 0.6
    x += warp * 0.9 + rip + wind
    order = [3, 2, 1, 0] if back else [0, 1, 2, 3]
    bounds = [OPEN1, GATES[0], GATES[1], GATES[2], 13.4]
    for i, w in enumerate(order):
        a, b = bounds[i], bounds[i + 1]
        m = seg(t, a, b + (0.0 if i < 3 else 0.3), 0.45)
        sl = m > 1e-4
        part = np.zeros((len(t), 2))
        part[sl] = WORLDS[w](t[sl], i)
        x += part * m[:, None]
    # en cada puerta, un soplo que sube (sin golpe)
    for g in GATES:
        x += whoosh(t, g, 0.35, 0.5, 800, 6000, seed=int(g * 10)) * 0.5
        x += pan(sub(t, 40, 80, 1.5), 0) * swell(t, g, 0.3, 0.4)[:, None] * 0.6
    if not back:
        # (a la ida, al final, el tirón del agujero negro que sube hacia la luz)
        x += whoosh(t, 12.2, 1.6, 0.8, 300, 4000, seed=250) * 0.4
    return stereo_reverb(x, 3.0, 2600, 0.35, 251 if back else 252)


def ida():
    return _viaje(False)


def vuelta():
    return _viaje(True)


def llegada():
    t = tt(4.0)
    ch = bloom(t, 0.3, (293.66, 440.0, 587.33, 739.99, 880.0, 1174.66), attack=0.35, decay=1.8, seed=260, bright=7000)
    sh = shimmer(t, (1760, 2217.46, 2637.02), seed=261) * swell(t, 0.6, 0.5, 1.6)[:, None]
    air = whoosh(t, 0.25, 0.3, 0.9, 6000, 600, seed=262)
    return stereo_reverb(ch * 2.4 + sh * 0.2 + air * 0.35, 3.0, 4000, 0.45, 263)


def _gliss(notes, gap, vel, seed, at=0.0, pan_from=-0.5, pan_to=0.5):
    """Un glissando de arpa (estéreo): las notas una tras otra, abriéndose de izquierda a derecha."""
    from instrumentos import arpa, hz, pan2, place
    out = np.zeros((int((at + gap * len(notes) + 4.0) * SR), 2))
    for k, nme in enumerate(notes):
        p = pan_from + (pan_to - pan_from) * k / max(1, len(notes) - 1)
        place(out, pan2(arpa(hz(nme), 3.5, vel * (0.75 + 0.25 * k / len(notes)), seed=seed + k), p), at + k * gap)
    return out


def pozo_entrar():
    """(1.3.97) Entrar en un Pozo de Luz: un glissando de arpa que sube (Re mayor pentatónica), un «aah» que se abre y una
    campana suave al final. Cálido y corto."""
    from instrumentos import campana, coro, hz, pan2, place, sala
    t = tt(3.4)
    x = _gliss(['D4', 'E4', 'F#4', 'A4', 'B4', 'D5', 'E5', 'F#5', 'A5'], 0.055, 0.9, 610)[: len(t)]
    ch = coro(t, [hz(n) for n in ('A3', 'D4', 'F#4')], seed=611) * swell(t, 0.7, 0.55, 1.3)[:, None]
    place(x, ch, 0.0, 0.9)
    place(x, pan2(campana(hz('D6'), 3.0, 0.6, seed=612), 0.2), 0.55, 0.35)
    return sala(x, 3.0, 0.4, 5000, seed=613)


def velo_entrar():
    """Entrar en el Velo del Alba (el santuario): el coro que florece en un acorde amplio, el arpa que sube y dos
    campanas: llegas a casa."""
    from instrumentos import campana, coro, hz, pan2, place, sala
    t = tt(4.2)
    x = _gliss(['A3', 'D4', 'F#4', 'A4', 'D5', 'F#5', 'A5', 'D6'], 0.075, 0.85, 620)[: len(t)]
    ch = coro(t, [hz(n) for n in ('D3', 'A3', 'D4', 'F#4', 'A4')], seed=621) * swell(t, 0.9, 0.75, 1.8)[:, None]
    place(x, ch, 0.0, 1.0)
    place(x, pan2(campana(hz('A5'), 3.5, 0.55, seed=622), -0.3), 0.6, 0.35)
    place(x, pan2(campana(hz('D6'), 3.5, 0.5, seed=623), 0.3), 0.95, 0.3)
    return sala(x, 3.8, 0.42, 5000, seed=624)


def velo_salir():
    """Salir de la luz: cuatro notas de arpa que bajan, despacio, y un aire frío muy bajito."""
    from instrumentos import hz, place, sala
    t = tt(3.2)
    x = _gliss(['A4', 'F#4', 'D4', 'A3'], 0.16, 0.7, 630, pan_from=0.4, pan_to=-0.4)[: len(t)]
    air = whoosh(t, 0.5, 0.5, 0.9, 2500, 500, seed=631)
    place(x, air, 0.0, 0.18)
    return sala(x, 3.0, 0.4, 3500, seed=632)


def sin_luz():
    """Quedarse sin luz: un coro grave en menor que crece, el arpa que cae a lo grave y un susurro lejano."""
    from instrumentos import coro, hz, place, sala
    t = tt(3.6)
    ch = coro(t, [hz(n) for n in ('B1', 'F#2', 'B2', 'D3')], seed=641) * swell(t, 1.3, 1.1, 1.2)[:, None]
    x = np.zeros((len(t), 2))
    place(x, ch, 0.0, 1.2)
    place(x, _gliss(['F#3', 'D3', 'B2', 'F#2'], 0.22, 0.6, 642, pan_from=0.3, pan_to=-0.3)[: len(t)], 0.2)
    wh = noise2(len(t), 643)
    for c in range(2):
        wh[:, c] = bp(wh[:, c], 1600, 3800)
    am = (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(4 + 2 * np.sin(2 * np.pi * 0.7 * t)) / SR)) ** 4
    place(x, wh * (am * swell(t, 1.6, 1.2, 0.9))[:, None], 0.0, 0.12)
    return sala(x, 3.4, 0.45, 2500, seed=644)


SOUNDS = {
    'viaje/ida': ida,
    'viaje/vuelta': vuelta,
    'viaje/llegada': llegada,
    'luz/pozo_entrar': pozo_entrar,
    'luz/velo_entrar': velo_entrar,
    'luz/velo_salir': velo_salir,
    'luz/sin_luz': sin_luz,
}
PEAK = {'luz/pozo_entrar': 0.55, 'luz/velo_entrar': 0.6, 'luz/velo_salir': 0.45, 'luz/sin_luz': 0.6}


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
