"""Los sonidos de la grieta del cielo (1.3.96): sintetizados aquí, sin muestras de nadie, y SIN GOLPES.

    python3 tools/void/sonidos_cielo.py [--wav carpeta] [--medir]

El dueño, de la 1.3.95: «no pongas sonidos tipo explosiones que se oyen secuenciales, quiero un sonido fino corrido».
Antes sonaban a la vez varios de Minecraft (trueno, explosión, «sonic boom», portal, chasquidos en cada salto de las
grietas): se oían como golpes uno detrás de otro. Ahora cada momento es UNA pista continua, hecha para su animación
(SkyRiftClient.look()), que sube y baja sin ataques secos: graves que suben, viento, un coro, un acorde que florece y el
desgarro como tela que se rasga. Estéreo (se oyen sin posición, como sonidos de interfaz). Salen en
assets/tfclient/sounds/cielo/ (OGG Vorbis):

  cielo/preludio   12 s de aparición: el cielo se apaga y se forma la tormenta; graves que suben y laten cada vez más
                   rápido, un coro grave que entra, viento que gira, retumbos lejanos (suaves) en los relámpagos y, al
                   final, una subida que lleva sin corte a «rompe» (se solapa 1 s con ella)
  cielo/rompe      se rompe (la telaraña crece 0-1,5 s, estalla a los 1,8 s, se abre hasta los 5,5 s): la subida sigue con
                   un aire grave que crece (sin nada de cristal), a los 1,8 s florece (grave que cae, soplo de aire y un acorde enorme, todo
                   con ataque suave), luego el desgarro (ruido que se rasga y se abre en estéreo, como las puntas) y se
                   queda en un zumbido que se apaga
  cielo/cierra     se sella (fogonazo a los 2,3 s): aspiración que sube, las puntas que se cosen (de los lados al
                   centro) y un «thoom» suave con el acorde que se apaga
  cielo/reabre     se vuelve a abrir (estalla a los 1,2 s): más oscuro, con un gruñido de coro grave al principio
  cielo/final      el final (fogonazo a los 2,5 s): la aspiración y un acorde mayor, luminoso, en paz
  cielo/aliento    abierta, cada 40-70 s: el Void respira (un soplo grave y lejano)

--medir: dice, por pista, el pico, el volumen y el «golpe» más fuerte (lo que más sube el volumen en 20 ms respecto a
los 60 ms de antes, en dB): por encima de ~10 dB se oiría como un golpe.
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


# ---------------------------------------------------------------------------------------------- pistas

FLICKS = (3.6, 6.4, 8.7, 10.3, 11.4)


def preludio():
    dur = 13.2
    t = tt(dur)
    a = (0.12 + 0.88 * (np.minimum(t, 12.0) / 12.0) ** 1.6) * (1 - smooth(12.0, 13.0, t))
    # grave que sube y late cada vez más rápido (sin golpes: una pulsación suave)
    rate = 0.7 + 1.9 * smooth(0, 12, t) ** 1.3
    throb = 0.55 + 0.45 * (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(rate) / SR)) ** 2
    s = sub(t, 31, 46, 1.2) * throb * a
    # acorde oscuro (re menor) que se abre; al final entra la tensión (sol sostenido)
    dark = pad(t, (36.71, 55.0, 73.42, 87.31), 140, 1300, seed=3) * a[:, None] * 1.8
    tension = pad(t, (103.83, 207.65), 300, 1800, seed=4) * (smooth(7.5, 12.0, t) * a)[:, None] * 1.2
    ch = choir(t, (146.83, 174.61, 220.0), seed=5) * (smooth(4.0, 11.0, t) * a)[:, None] * 2.2
    wind = noise2(len(t), 6)
    for c in range(2):
        wind[:, c] = bp(lp_sweep(wind[:, c], 300, 2600, 48), 150, 4000)
    # el viento gira (de un lado a otro, cada vez más rápido)
    pos = np.sin(2 * np.pi * np.cumsum(0.08 + 0.25 * smooth(0, 12, t)) / SR)
    wind = wind * np.stack([0.5 - 0.35 * pos, 0.5 + 0.35 * pos], 1) * (a * 0.55)[:, None]
    rum = sum(rumble(t, f, 0.35 + 0.12 * i, 30 + i) for i, f in enumerate(FLICKS))
    up = riser(t, 7.5, 12.6, 160, 820, seed=7) * (1 - smooth(12.4, 13.0, t))[:, None]
    x = pan(s, 0.0) * 0.9 + dark + tension + ch + wind + rum + up * 0.9
    return stereo_reverb(x, 3.5, 2200, 0.38, 11)[: len(t) + int(1.0 * SR)]


def _rompe(dur, brk, opened, chord, seed, dark=False):
    t = tt(dur)
    # sigue la subida del preludio (o empieza una corta) hasta que estalla
    up = riser(t, -0.6 if not dark else 0.0, brk, 820 if not dark else 300, 1500, seed=seed) * (1 - smooth(brk - 0.05, brk + 0.25, t))[:, None]
    # (sin brillo de cristal: el dueño no quiere cristal) la tensión crece con un aire grave que gira
    build = noise2(len(t), seed + 1)
    for c in range(2):
        build[:, c] = bp(build[:, c], 200, 1400)
    build = build * (smooth(-0.5, brk, t) * (1 - smooth(brk, brk + 0.4, t)))[:, None] * 0.35
    # florece: grave que cae, soplo y un acorde enorme (todo con ataque de 0,1-0,4 s)
    s = sub(t, 70, 26, 0.5) * swell(t, brk + 0.12, 0.14, 2.6)
    air = whoosh(t, brk + 0.15, 0.18, 2.0, 7000, 500, seed=seed + 2)
    ch = bloom(t, brk + 0.35, chord, attack=0.45, decay=4.0, seed=seed + 3)
    sh = shimmer(t, (1760, 2217, 2637, 3520, 4186), seed=seed + 4) * (smooth(brk + 0.3, brk + 1.5, t) * np.exp(-np.maximum(t - brk - 1.5, 0) / 3.5))[:, None]
    rip = tear(t, brk + 0.1, opened, seed=seed + 5)
    # la grieta abierta: su zumbido, que se va apagando
    hum = pad(t, (36.71, 55.0, 73.42), 200, 600, seed=seed + 6) * (smooth(brk + 1.5, opened, t) * np.exp(-np.maximum(t - opened, 0) / 2.0))[:, None]
    growl = 0
    if dark:
        # (al volver a abrirse) un gruñido de coro grave al principio
        growl = choir(t, (73.42, 77.78, 110.0), seed=seed + 7, vib=0.012) * (smooth(0, 0.9, t) * (1 - smooth(brk, brk + 0.8, t)))[:, None] * 3.0
    x = up * 0.8 + build + pan(s, 0.0) * 1.2 + air * 0.55 + ch * 2.6 + sh * 0.12 + rip * 0.5 + hum * 1.6 + growl
    return stereo_reverb(x, 4.2, 2400, 0.45, seed + 8)


def rompe():
    # re menor con novena, abierto: re, la, re, fa, la, mi
    return _rompe(10.0, 1.8, 5.5, (146.83, 220.0, 293.66, 349.23, 440.0, 659.26), 40)


def reabre():
    # más oscuro: si bemol menor sobre re (tensión)
    return _rompe(8.0, 1.2, 4.0, (116.54, 146.83, 233.08, 277.18, 349.23, 554.37), 60, dark=True)


def _cierra(dur, flash, chord, seed, peace=False):
    t = tt(dur)
    # aspiración: aire que sube y se cierra en el fogonazo
    k = smooth(0, flash, t) ** 2 * (1 - smooth(flash - 0.02, flash + 0.15, t))
    suck = noise2(len(t), seed)
    for c in range(2):
        suck[:, c] = lp_sweep(suck[:, c], 400, 7000, 40)
    s_up = sub(t, 28, 62, 1.5) * k
    # las puntas que se cosen: de los lados al centro
    zip_ = tear(t, 0.0, flash, seed=seed + 1, f0=2600, f1=500, spread=False)
    p = 0.85 * (1 - smooth(0, flash, t))
    zip_ = 0.6 * pan(zip_[:, 0] / 0.7071, -p) + 0.6 * pan(zip_[:, 1] / 0.7071, p)
    # el «thoom» suave y el acorde que se apaga (o que florece en paz, en el final)
    thoom = sub(t, 58, 36, 0.6) * swell(t, flash + 0.06, 0.08, 1.3)
    ch = bloom(t, flash + 0.1, chord, attack=0.25 if not peace else 0.5, decay=1.8 if not peace else 4.5, seed=seed + 2,
               bright=2500 if not peace else 5000)
    x = suck * k[:, None] * 0.5 + pan(s_up, 0.0) * 0.7 + zip_ * 0.45 + pan(thoom, 0.0) * 1.1 + ch * (2.2 if not peace else 3.0)
    if peace:
        # coro luminoso (re mayor) y un brillo que se queda
        x = x + choir(t, (293.66, 369.99, 440.0, 587.33), seed=seed + 3, vib=0.005) * (swell(t, flash + 0.6, 0.9, 3.5))[:, None] * 2.0
        x = x + shimmer(t, (1174.66, 1479.98, 1760.0, 2349.32), seed=seed + 4) * swell(t, flash + 0.8, 1.0, 3.0)[:, None] * 0.25
    return stereo_reverb(x, 4.0 if not peace else 5.0, 2600, 0.45, seed + 5)


def cierra():
    return _cierra(5.0, 2.3, (146.83, 220.0, 293.66, 349.23), 80)


def final():
    # re mayor con novena: luz, en paz
    return _cierra(8.0, 2.5, (146.83, 220.0, 293.66, 369.99, 440.0, 659.26), 100, peace=True)


def aliento():
    """El Void respira (abierta, cada 40-70 s): un soplo grave y lejano, muy bajo."""
    dur = 6.5
    t = tt(dur)
    env = np.sin(np.pi * np.clip(t / 6.0, 0, 1)) ** 2
    air = noise2(len(t), 120)
    for c in range(2):
        air[:, c] = bp(air[:, c], 90, 700)
    s = sub(t, 38, 33) * env * 0.6
    sh = shimmer(t, (880, 1318.5), seed=121) * (env ** 2)[:, None] * 0.05
    x = air * env[:, None] * 0.6 + pan(s, 0) + sh
    return stereo_reverb(x, 3.0, 1500, 0.4, 122) * 0.45


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
    jumps = [db[i] - 20 * np.log10(np.sqrt(np.mean(rms[i - 3:i] ** 2)) + 1e-12) for i in range(3, blocks) if db[i] > -40]
    j = max(jumps) if jumps else 0
    at = (int(np.argmax(jumps)) + 3) * 0.02 if jumps else 0
    print(f'  {name}: pico {np.max(np.abs(x)):.2f}, volumen {20 * np.log10(np.sqrt(np.mean(m ** 2)) + 1e-12):.1f} dB, '
          f'golpe más fuerte {j:.1f} dB (a los {at:.2f} s)')
    return j


def main():
    wav_dir = sys.argv[sys.argv.index('--wav') + 1] if '--wav' in sys.argv else None
    tmp = tempfile.mkdtemp()
    for name, fn in SOUNDS.items():
        x = fn()
        x = np.stack([hp(x[:, c], 28, 2) for c in range(2)], 1)
        x = norm(x, PEAK.get(name, 0.85))
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
