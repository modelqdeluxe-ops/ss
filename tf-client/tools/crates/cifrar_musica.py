"""Cifra la música de la escena de las crates para meterla en el mod (1.3.109).

El dueño: «no quiero un puto paquete de recursos, quiero que lo metas al mod cifrado y que suene en la escena de la
crate». El repo y las Releases son públicos: la música va cifrada (AES-128 en modo CTR) en
assets/tfclient/sounds/crates/musica.ogg y el mod la descifra al vuelo al sonar (SealedAudio y
SoundBufferLibraryMixin). Quien abra el jar no la puede escuchar ni copiar tal cual (la clave va en el mod, así que
frena a cualquiera, no a quien se empeñe).

Formato: «TFENC1» (6 bytes) + vector inicial (16) + el .ogg cifrado. La clave: los 16 primeros bytes del SHA-256 de
SealedAudio.SEED (la misma cadena que en el mod).

Uso: python3 -I tools/crates/cifrar_musica.py <musica.ogg> [--salida <ruta del .ogg cifrado>]
     (el .ogg ya hecho: 3:00, −15 LUFS; ver CRATES.md). Por defecto, la de la escena de las crates.
     (1.3.115) --salida assets/.../sounds/santuario/musica.ogg: la del santuario del Void (SealedAudio también la abre).
     python3 -I tools/crates/cifrar_musica.py --descifrar <cifrado.ogg> <salida.ogg>   (para volver a tocarla)
"""
import hashlib
import os
import sys

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

SEED = "TierrasFantasticas|santuario-de-las-hadas|1.3.109"
MAGIC = b"TFENC1"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "tfclient",
                   "sounds", "crates", "musica.ogg")


def descifrar(src, dst):
    raw = open(src, "rb").read()
    if raw[:len(MAGIC)] != MAGIC:
        sys.exit("no está cifrada: " + src)
    key = hashlib.sha256(SEED.encode("utf-8")).digest()[:16]
    iv = raw[len(MAGIC):len(MAGIC) + 16]
    dec = Cipher(algorithms.AES(key), modes.CTR(iv)).decryptor()
    open(dst, "wb").write(dec.update(raw[len(MAGIC) + 16:]) + dec.finalize())
    print(f"descifrada: {dst}")


def main():
    global OUT
    if sys.argv[1] == "--descifrar":
        descifrar(sys.argv[2], sys.argv[3])
        return
    if "--salida" in sys.argv:
        OUT = sys.argv[sys.argv.index("--salida") + 1]
    src = sys.argv[1]
    data = open(src, "rb").read()
    if data[:4] != b"OggS":
        sys.exit("no es un .ogg: " + src)
    key = hashlib.sha256(SEED.encode("utf-8")).digest()[:16]
    iv = os.urandom(16)
    enc = Cipher(algorithms.AES(key), modes.CTR(iv)).encryptor()
    body = enc.update(data) + enc.finalize()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(MAGIC + iv + body)
    # comprobación: se descifra igual
    dec = Cipher(algorithms.AES(key), modes.CTR(iv)).decryptor()
    assert dec.update(body) + dec.finalize() == data
    print(f"cifrada: {os.path.normpath(OUT)} ({len(data)} bytes de música)")


if __name__ == "__main__":
    main()
