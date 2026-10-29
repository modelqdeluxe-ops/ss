#!/usr/bin/env python3
"""La música y los efectos grabados de Corazón Dormido (0.8): se bajan de su sitio, se comprueban y se preparan.

  python3 corazon-dormido/herramientas/audio/bajar.py <carpeta-del-juego> [--cache <carpeta>]

Escribe <carpeta-del-juego>/musica/<tema>.ogg y <carpeta-del-juego>/sonidos/<efecto>[_n].ogg (los nombres que piden
js/musica.js y js/sonido.js). No van al repo: los baja el workflow de Android al compilar, después de componer la
música y los efectos sintetizados, y los sustituye; si una descarga falla o su huella no cuadra, ese tema o ese
efecto se queda el sintetizado (el juego suena igual, con lo de antes).

Licencias (comprobadas en la página de cada uno; están también en CREDITOS.md y en los créditos del juego):
  - Música: Kevin MacLeod (incompetech.com), CC BY 4.0. Hay que citarlo: CREDITOS.md y el juego lo hacen.
  - Efectos: OpenGameArt, todos CC0 (artisticdude, rubberduck, qubodup/Fantozzi, bart, peludo).
Necesita ffmpeg (con libvorbis) y numpy; para el .7z de los pasos, py7zr.
"""
import hashlib
import io
import pathlib
import subprocess
import sys
import urllib.request
import zipfile

import numpy as np

OGA = 'https://opengameart.org/sites/default/files/'
MAC = 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/'

# ---------------------------------------------------------------- música (Kevin MacLeod, CC BY 4.0)
# tema del juego → (título, archivo, huella)
MUSICA = {
    'titulo': ('Heart of Nowhere', 'Heart%20of%20Nowhere.mp3', '7696504c14f14cea3267aff3fd3765f3275c4b967febf1dc1ac1b6e9c28844d7'),
    'interior': ('Immersed', 'Immersed.mp3', '870fa4f642816b2207cf74e48c36cbbbd147df99be24f08f29aa26ac0597a44b'),
    'selva': ('Ascending the Vale', 'Ascending%20the%20Vale.mp3', 'a0ab3aae9a942d797c381df8d914dfef453e7e455c1c6333d1916d59ba005353'),
    'cienaga': ('Mirage', 'Mirage.mp3', 'a15986eafae8c6b495c72ad0b942533c31bc0bbf4701b9d94d080687566c30e5'),
    'ciudad': ('Gregorian Chant', 'Gregorian%20Chant.mp3', 'e88cf0fc0121544767afd1b6eedb90944e4f36892f4fa6c0d10553004945abfd'),
    'cripta': ('Oppressive Gloom', 'Oppressive%20Gloom.mp3', 'd630686bde6c92f832fb0cb16f6ce9009bf502c7db701139329af2a7683b1020'),
    'jefe': ('Darkling', 'Darkling.mp3', '71fcef204297b7cdbfd85b287687ab32cba65f08dd4d2f66299a041f370ac945'),
    'jefe2': ('Five Armies', 'Five%20Armies.mp3', 'f56746c90756db04675a80ebd29e22253177a6fcc16f56602da62af9eae8d733'),
}

# ---------------------------------------------------------------- efectos (OpenGameArt, CC0)
# paquete → (archivo en OGA, huella)
PAQUETES = {
    'rpg': ('rpg_sound_pack.zip', 'f80754a9c04854e30c9da89d0186540ad097309d98b41387b6e83029e2601421'),          # artisticdude
    'criaturas': ('80-CC0-creature-SFX_0.zip', '5d4002eea42f278e76543bb36e8ddd2cb978588a13271984191d8228f5d26768'),  # rubberduck
    'cien': ('100-CC0-SFX_0.zip', 'a5c135878c132f1c59cca54e60061c296cd0ac27ad031ca2c41b8cd5cab3c706'),           # rubberduck
    'roturas': ('sfx_breaking_and_falling.zip', 'e6ee04d91c5f4d30cfda1260d2c9d1faf96fda36319287215fbd07bcb1a80451'),  # rubberduck
    'pasos': ('Fantozzi-footsteps.7z', '415d360e0911c1c1c5355ee94023b772e6f4044f59b9b580610e7dbffd1840fd'),      # qubodup (Fantozzi)
    'latido': ('heartbeat_slow_0.wav', 'ce5d2bbe4c9e2a8843fc76419070b9e88f71b2aa4ba7226a5f50a6b45d13be60'),      # bart
    'chapoteo': ('splash2_0.wav', 'ad9472b711666de2d43c3b102d155a5d1118b1fcd0ecd29cb63177256ab63f31'),           # peludo
}
# efecto del juego → variantes: (paquete, archivo dentro, opciones). Opciones: tono (velocidad), desde/hasta (s),
# filtro (de ffmpeg), vol (dB de más o de menos).
RP = 'RPG Sound Pack/'
EFECTOS = {
    'picar': [('roturas', 'bfh1_rock_falling_04.ogg', {}), ('roturas', 'bfh1_rock_falling_05.ogg', {}), ('roturas', 'bfh1_rock_falling_06.ogg', {}), ('roturas', 'bfh1_rock_hit_01.ogg', {})],
    'romper': [('roturas', 'bfh1_rock_breaking_01.ogg', {}), ('roturas', 'bfh1_rock_breaking_02.ogg', {})],
    'excavar': [('roturas', 'bfh1_rock_falling_01.ogg', {}), ('roturas', 'bfh1_rock_falling_03.ogg', {})],
    'metal': [('cien', 'metal_06.ogg', {}), ('cien', 'metal_09.ogg', {})],
    'golpe': [('cien', 'hit_01.ogg', {}), ('cien', 'hit_02.ogg', {}), ('roturas', 'bfh1_hit_06.ogg', {}), ('roturas', 'bfh1_hit_10.ogg', {})],
    'zas': [('rpg', RP + 'battle/swing.wav', {}), ('rpg', RP + 'battle/swing2.wav', {}), ('rpg', RP + 'battle/swing3.wav', {})],
    'dano': [('cien', 'slam_03.ogg', {}), ('criaturas', 'hurt_04.ogg', {})],
    'muere': [('criaturas', 'monster_03.ogg', {}), ('criaturas', 'monster_06.ogg', {}), ('rpg', RP + 'NPC/giant/giant2.wav', {})],
    'alerta': [('criaturas', 'grunt_03.ogg', {}), ('criaturas', 'grunt_04.ogg', {}), ('criaturas', 'grunt_05.ogg', {})],
    'rugido': [('criaturas', 'roar_02.ogg', {'tono': 0.72})],
    'temblor': [('roturas', 'bfh1_rock_falling_09.ogg', {'tono': 0.55, 'filtro': 'lowpass=f=1800'})],
    'latido': [('latido', '', {'hasta': 0.85})],
    'recoger': [('cien', 'plop_02.ogg', {}), ('cien', 'plop_01.ogg', {}), ('rpg', RP + 'inventory/bubble.wav', {'vol': -4})],
    'crear': [('cien', 'tools_02.ogg', {})],
    'comer': [('criaturas', 'eat_01.ogg', {})],
    'colocar': [('cien', 'wooden_01.ogg', {}), ('cien', 'wooden_03.ogg', {})],
    'error': [('rpg', RP + 'interface/interface6.wav', {})],
    'menu': [('rpg', RP + 'interface/interface1.wav', {'vol': -3})],
    'nivel': [('cien', 'bell_01.ogg', {})],
    'victoria': [('cien', 'gong_01.ogg', {})],
    'paso_piedra': [('pasos', f'Fantozzi-footsteps/ogg/Fantozzi-Stone{p}.ogg', {}) for p in ('L1', 'R1', 'L2', 'R2')],
    'paso_hierba': [('pasos', f'Fantozzi-footsteps/ogg/Fantozzi-Sand{p}.ogg', {'filtro': 'lowpass=f=6000'}) for p in ('L1', 'R1', 'L2', 'R2')],
    'paso_agua': [('cien', 'splash_01.ogg', {}), ('cien', 'splash_02.ogg', {}), ('chapoteo', '', {'desde': 0.6, 'hasta': 1.2})],
}


def bajar(url, huella, cache):
    nombre = cache / url.rsplit('/', 1)[1]
    if not nombre.exists() or hashlib.sha256(nombre.read_bytes()).hexdigest() != huella:
        req = urllib.request.Request(url, headers={'User-Agent': 'corazon-dormido-build'})
        with urllib.request.urlopen(req, timeout=120) as r:
            nombre.write_bytes(r.read())
    datos = nombre.read_bytes()
    if hashlib.sha256(datos).hexdigest() != huella:
        raise ValueError(f'la huella de {url} no cuadra (¿ha cambiado el archivo?)')
    return datos


def abrir(paquete, datos, dentro):
    """Los bytes de un archivo dentro de un paquete (zip o 7z) o el propio archivo."""
    if not dentro:
        return datos
    if PAQUETES[paquete][0].endswith('.zip'):
        with zipfile.ZipFile(io.BytesIO(datos)) as z:
            return z.read(next(n for n in z.namelist() if n.endswith(dentro) and '__MACOSX' not in n))
    import tempfile
    import py7zr
    with tempfile.TemporaryDirectory() as t, py7zr.SevenZipFile(io.BytesIO(datos)) as z:
        z.extractall(path=t)
        return (pathlib.Path(t) / dentro).read_bytes()


def decodificar(datos, sr, canales, filtro=''):
    cmd = ['ffmpeg', '-v', 'error', '-i', 'pipe:0'] + (['-af', filtro] if filtro else []) + ['-ac', str(canales), '-ar', str(sr), '-f', 'f32le', 'pipe:1']
    x = np.frombuffer(subprocess.run(cmd, input=datos, capture_output=True, check=True).stdout, dtype=np.float32)
    return x.reshape(-1, canales).copy()


def codificar(x, sr, ruta, calidad):
    ruta.parent.mkdir(parents=True, exist_ok=True)
    cmd = ['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(sr), '-ac', str(x.shape[1]), '-i', 'pipe:0', '-c:a', 'libvorbis', '-q:a', str(calidad), str(ruta)]
    subprocess.run(cmd, input=x.astype(np.float32).tobytes(), check=True)


def recortar(x, sr, umbral_db):
    """Quita el silencio del principio y del final."""
    a = np.abs(x).max(axis=1)
    if not a.max():
        return x
    hay = np.where(a > a.max() * 10 ** (umbral_db / 20))[0]
    return x[max(0, hay[0] - int(sr * 0.005)): hay[-1] + int(sr * 0.02)]


def efecto(paquete, datos, dentro, o, ruta):
    sr = 32000
    filtros = []
    if o.get('tono'):
        filtros.append(f"asetrate={int(sr * o['tono'])},aresample={sr}")
    if o.get('filtro'):
        filtros.append(o['filtro'])
    x = decodificar(abrir(paquete, datos, dentro), sr, 1, ','.join(filtros))
    if o.get('desde') or o.get('hasta'):
        x = x[int(o.get('desde', 0) * sr): int(o['hasta'] * sr) if o.get('hasta') else None]
    x = recortar(x, sr, -42)
    x = x / max(np.abs(x).max(), 1e-6) * 10 ** ((-2 + o.get('vol', 0)) / 20)  # pico a -2 dB
    n = min(len(x), int(sr * 0.02))
    x[-n:] *= np.linspace(1, 0, n)[:, None]  # sin chasquido al final
    codificar(x, sr, ruta, 4)


def tema(datos, ruta):
    sr = 44100
    x = recortar(decodificar(datos, sr, 2), sr, -50)
    rms = np.sqrt(np.mean(x ** 2))
    x = x * min(10 ** (-18 / 20) / max(rms, 1e-6), 0.95 / max(np.abs(x).max(), 1e-6))  # unos -18 dB de media, sin pasarse
    # entra y sale suave (el tema se repite: js/musica.js lo pone en bucle)
    a, b = int(sr * 0.3), int(sr * 2.5)
    x[:a] *= np.linspace(0, 1, a)[:, None]
    x[-b:] *= np.linspace(1, 0, b)[:, None]
    codificar(x, sr, ruta, 2)


def main():
    args = sys.argv[1:]
    if not args:
        raise SystemExit(__doc__)
    juego = pathlib.Path(args[0])
    cache = pathlib.Path(args[args.index('--cache') + 1]) if '--cache' in args else juego / '.cache-audio'
    cache.mkdir(parents=True, exist_ok=True)
    malos = 0
    for n, (titulo, archivo, huella) in MUSICA.items():
        try:
            tema(bajar(MAC + archivo, huella, cache), juego / 'musica' / f'{n}.ogg')
            print(f'música {n}: «{titulo}» ({(juego / "musica" / f"{n}.ogg").stat().st_size / 1e6:.1f} MB)', flush=True)
        except Exception as e:  # noqa: BLE001 (que una descarga no pare la compilación)
            malos += 1
            print(f'música {n}: se queda la compuesta ({e})', flush=True)
    paquetes = {}
    for p, (archivo, huella) in PAQUETES.items():
        try:
            paquetes[p] = bajar(OGA + archivo, huella, cache)
        except Exception as e:  # noqa: BLE001
            print(f'paquete {p}: no se pudo bajar ({e})', flush=True)
    for nombre, variantes in EFECTOS.items():
        for k, (p, dentro, o) in enumerate(variantes):
            ruta = juego / 'sonidos' / (f'{nombre}_{k}.ogg' if len(variantes) > 1 else f'{nombre}.ogg')
            if p not in paquetes:
                malos += 1
                continue
            try:
                efecto(p, paquetes[p], dentro, o, ruta)
            except Exception as e:  # noqa: BLE001
                malos += 1
                print(f'efecto {ruta.name}: se queda el sintetizado ({e})', flush=True)
    total = sum(f.stat().st_size for d in ('musica', 'sonidos') for f in (juego / d).glob('*.ogg'))
    print(f'audio listo: {total / 1e6:.1f} MB en total' + (f', {malos} sin grabar (siguen los sintetizados)' if malos else ''))


if __name__ == '__main__':
    main()
