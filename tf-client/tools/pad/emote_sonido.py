"""La fanfarria del emote del clan (1.3.159): «¡Por el clan!», 4,4 s, en Re mayor, al ritmo de la animación
(ClanEmoteClient): impulso (redoble y un soplo que sube), el salto (golpe grave, platillo y el «ta-ta-taaa» de metales),
la caída (golpe), la pose (acordes de metales Re–Sol–La–Re con coro y campanitas que brillan) y el final (arpegio de
celesta hacia arriba y platillo). Sintetizado aquí (numpy), con su sala (reverberación por convolución) y el pico a −1 dB;
mono para que suene en el sitio del jugador.
Uso: python3 tools/pad/emote_sonido.py [--wav salida.wav]  → assets/tfclient/sounds/clanes/emote.ogg (con ffmpeg)."""
import os
import subprocess
import sys
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'sounds', 'clanes', 'emote.ogg'))
SR = 44100
DUR = 4.4
rng = np.random.default_rng(159)
mix = np.zeros(int(SR * (DUR + 1.2)))


def note(n):
    """Frecuencia de una nota MIDI."""
    return 440.0 * 2 ** ((n - 69) / 12)


def add(sig, at, gain=1.0):
    i = int(at * SR)
    j = min(len(mix), i + len(sig))
    mix[i:j] += sig[:j - i] * gain


def env(n, a, d, s, r, hold):
    """ADSR de n muestras (a, d, r en s; s el nivel; hold: cuánto dura la nota antes de soltar)."""
    t = np.arange(n) / SR
    e = np.where(t < a, t / max(a, 1e-4), np.where(t < a + d, 1 - (1 - s) * (t - a) / max(d, 1e-4), s))
    rel = np.clip((t - hold) / max(r, 1e-4), 0, 1)
    return e * (1 - rel)


def lowpass(x, cut):
    """Filtro de un polo (cut: frecuencia o array de frecuencias por muestra)."""
    cut = np.broadcast_to(np.asarray(cut, dtype=float), x.shape)
    a = 1 - np.exp(-2 * np.pi * cut / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a[i] * (x[i] - acc)
        y[i] = acc
    return y


def brass(midi, length, gain=0.3, bright=1.0):
    n = int((length + 0.35) * SR)
    t = np.arange(n) / SR
    f = note(midi) * (1 + 0.004 * np.sin(2 * np.pi * 5.2 * t) * np.clip(t / 0.25, 0, 1))
    ph = np.cumsum(f) / SR
    saw = 2 * (ph % 1) - 1
    saw += 0.5 * (2 * ((ph * 1.003) % 1) - 1)
    e = env(n, 0.03, 0.12, 0.8, 0.25, length)
    cut = (900 + 3800 * bright * e)
    return lowpass(saw, cut) * e * gain


def bell(freq, length=1.6, gain=0.15):
    n = int(length * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for k, (m, a, d) in enumerate([(1, 1, 1.4), (2.76, 0.5, 2.6), (5.4, 0.25, 4.5), (8.9, 0.12, 7)]):
        s += a * np.sin(2 * np.pi * freq * m * t) * np.exp(-d * t)
    return s * gain * np.clip(t / 0.003, 0, 1)


def noise(n):
    return rng.standard_normal(n)


def drum(freq=55, length=0.9, gain=0.8):
    n = int(length * SR)
    t = np.arange(n) / SR
    f = freq * (1 + 2.5 * np.exp(-30 * t))
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-5 * t)
    s += 0.25 * lowpass(noise(n), 1800) * np.exp(-25 * t)
    return s * gain


def cymbal(length=2.2, gain=0.22):
    n = int(length * SR)
    t = np.arange(n) / SR
    x = noise(n)
    hp = x - lowpass(x, 5000)
    return hp * np.exp(-2.2 * t) * gain * np.clip(t / 0.004, 0, 1)


def snare_roll(length, gain=0.18):
    n = int(length * SR)
    t = np.arange(n) / SR
    x = noise(n)
    hits = 0.5 + 0.5 * np.cos(2 * np.pi * 28 * t)
    crescendo = (t / length) ** 1.6
    return (x - lowpass(x, 1200)) * hits * crescendo * gain


def whoosh(length, gain=0.25):
    n = int(length * SR)
    t = np.arange(n) / SR
    cut = 300 + 5000 * (t / length) ** 2
    return lowpass(noise(n), cut) * (t / length) ** 1.5 * gain


def choir(midis, length, gain=0.08):
    n = int((length + 0.5) * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for m in midis:
        for det in (-0.006, 0.0, 0.007):
            f = note(m) * (1 + det)
            ph = 2 * np.pi * f * t + 0.6 * np.sin(2 * np.pi * 4.8 * t)
            s += np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.15 * np.sin(3 * ph)
    e = env(n, 0.35, 0.2, 0.85, 0.5, length)
    return lowpass(s, 2500) * e * gain / len(midis)


# 0–0,45: impulso
add(snare_roll(0.48), 0.0)
add(whoosh(0.5), 0.0)
# 0,45: el salto
add(drum(52, 1.0, 0.9), 0.45)
add(cymbal(2.4, 0.2), 0.45)
for m, at, ln in ((62, 0.48, 0.12), (66, 0.48, 0.12), (69, 0.48, 0.12)):
    add(brass(m, ln, 0.22), at)
for m, at, ln in ((69, 0.66, 0.1), (74, 0.82, 0.45), (78, 0.82, 0.45), (81, 0.82, 0.45)):
    add(brass(m, ln, 0.2, 1.2), at)
# 1,05: la caída
add(drum(45, 0.6, 0.7), 1.05)
# 1,35–3,45: la pose (Re – Sol – La – Re)
chords = [((50, 62, 66, 69), 1.35, 0.5), ((55, 62, 67, 71), 1.88, 0.5), ((57, 64, 69, 73), 2.41, 0.5), ((50, 62, 66, 69, 74), 2.94, 0.6)]
for midis, at, ln in chords:
    for m in midis:
        add(brass(m, ln, 0.11 if m > 55 else 0.15, 0.8), at)
    add(drum(60, 0.4, 0.35), at)
add(choir((62, 66, 69, 74), 2.1), 1.35)
penta = [74, 76, 78, 81, 83, 86, 88, 90]
for i in range(16):
    at = 1.45 + i * 0.125 + rng.uniform(-0.02, 0.02)
    add(bell(note(penta[rng.integers(len(penta))]), 1.2, 0.07), at)
# 3,55: el final
for i, m in enumerate((74, 78, 81, 86, 90, 93)):
    add(bell(note(m), 1.6, 0.12), 3.55 + i * 0.045)
add(cymbal(1.6, 0.16), 3.55)
add(drum(48, 0.8, 0.5), 3.55)

# la sala: convolución con una cola de ruido que se apaga (1,1 s)
ir_n = int(1.1 * SR)
ir = noise(ir_n) * np.exp(-4.2 * np.arange(ir_n) / SR)
ir[0] = 1.0
ir /= np.sqrt(np.sum(ir ** 2))
wet = np.convolve(mix, ir)[:len(mix)]
out = 0.78 * mix + 0.32 * wet
end = int((DUR + 0.25) * SR)
out = out[:end]
fade = int(0.25 * SR)
out[-fade:] *= np.linspace(1, 0, fade)
out /= np.max(np.abs(out)) / 10 ** (-1 / 20)

wav = sys.argv[sys.argv.index('--wav') + 1] if '--wav' in sys.argv else OUT[:-4] + '.wav'
with wave.open(wav, 'wb') as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((out * 32767).astype('<i2').tobytes())
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', OUT], check=True)
if '--wav' not in sys.argv:
    os.remove(wav)
print(OUT, round(len(out) / SR, 2), 's')
