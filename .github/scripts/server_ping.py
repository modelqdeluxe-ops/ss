"""Ping de la lista de servidores de Minecraft (protocolo 1.20.1): versión, jugadores y, si es Forge, los mods que pide."""
import json
import socket
import struct
import sys

host = sys.argv[1] if len(sys.argv) > 1 else "216.163.187.40"
port = int(sys.argv[2]) if len(sys.argv) > 2 else 19001


def varint(n):
    out = b""
    while True:
        b = n & 0x7F
        n >>= 7
        out += bytes([b | (0x80 if n else 0)])
        if not n:
            return out


def read_varint_sock(s):
    n = 0
    for i in range(5):
        b = s.recv(1)[0]
        n |= (b & 0x7F) << (7 * i)
        if not b & 0x80:
            return n


class Buf:
    def __init__(self, data):
        self.d, self.i = data, 0

    def byte(self):
        v = self.d[self.i]
        self.i += 1
        return v

    def varint(self):
        n = 0
        for k in range(5):
            b = self.byte()
            n |= (b & 0x7F) << (7 * k)
            if not b & 0x80:
                return n

    def ushort(self):
        v = struct.unpack(">H", self.d[self.i:self.i + 2])[0]
        self.i += 2
        return v

    def utf(self):
        n = self.varint()
        v = self.d[self.i:self.i + n].decode("utf-8", "replace")
        self.i += n
        return v


def decode_forge(s):
    """ServerStatusPing.decodeOptimized de Forge: 15 bits por carácter."""
    size = ord(s[0]) | (ord(s[1]) << 15)
    out = bytearray()
    buffer = bits = 0
    for ch in s[2:]:
        while bits >= 8:
            out.append(buffer & 0xFF)
            buffer >>= 8
            bits -= 8
        buffer |= (ord(ch) & 0x7FFF) << bits
        bits += 15
    while len(out) < size:
        out.append(buffer & 0xFF)
        buffer >>= 8
        bits -= 8
    b = Buf(bytes(out[:size]))
    truncated = b.byte() != 0
    mods = []
    for _ in range(b.ushort()):
        flag = b.varint()
        channels, ignore_server_only = flag >> 1, flag & 1
        mod_id = b.utf()
        version = "(solo servidor)" if ignore_server_only else b.utf()
        required = []
        for _ in range(channels):
            name, ver, req = b.utf(), b.utf(), b.byte() != 0
            if req:
                required.append(name)
        mods.append((mod_id, version, required))
    return truncated, mods


s = socket.create_connection((host, port), timeout=8)
hs = varint(0) + varint(763) + varint(len(host)) + host.encode() + struct.pack(">H", port) + varint(1)
s.sendall(varint(len(hs)) + hs)
s.sendall(b"\x01\x00")
read_varint_sock(s)
read_varint_sock(s)
length = read_varint_sock(s)
data = b""
while len(data) < length:
    data += s.recv(length - len(data))
status = json.loads(data)

print("Versión:", json.dumps(status.get("version"), ensure_ascii=False))
print("Jugadores: %s/%s" % (status.get("players", {}).get("online"), status.get("players", {}).get("max")))
forge = status.get("forgeData")
if not forge:
    print("Servidor sin Forge (vanilla/Paper/Spigot)")
    sys.exit(0)
print("Forge, fmlNetworkVersion:", forge.get("fmlNetworkVersion"))
if forge.get("d"):
    truncated, mods = decode_forge(forge["d"])
    print("Lista truncada:", truncated)
    print("Mods del servidor (%d):" % len(mods))
    for mod_id, version, required in sorted(mods):
        extra = ("  canales obligatorios: " + ", ".join(required)) if required else ""
        print("  - %s %s%s" % (mod_id, version, extra))
