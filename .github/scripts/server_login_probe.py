"""Simula los primeros pasos del inicio de sesión de Minecraft 1.20.1 (sin cuenta) y muestra qué responde el servidor."""
import json
import socket
import struct
import sys
import time
import uuid

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


def mc_string(s):
    data = s.encode()
    return varint(len(data)) + data


def read_varint(sock):
    n = 0
    for i in range(5):
        chunk = sock.recv(1)
        if not chunk:
            raise EOFError("el servidor cerró la conexión")
        b = chunk[0]
        n |= (b & 0x7F) << (7 * i)
        if not b & 0x80:
            return n


def read_exact(sock, n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise EOFError("el servidor cerró la conexión")
        data += chunk
    return data


def probe(label, address_field):
    print(f"\n=== {label} ===")
    start = time.time()
    try:
        sock = socket.create_connection((host, port), timeout=10)
    except Exception as e:
        print("No conecta por TCP:", e)
        return
    print(f"TCP conectado en {time.time() - start:.2f}s")
    sock.settimeout(15)
    handshake = varint(0) + varint(763) + mc_string(address_field) + struct.pack(">H", port) + varint(2)
    sock.sendall(varint(len(handshake)) + handshake)
    login = varint(0) + mc_string("TFProbe") + b"\x01" + uuid.uuid4().bytes
    sock.sendall(varint(len(login)) + login)
    try:
        length = read_varint(sock)
        payload = read_exact(sock, length)
    except socket.timeout:
        print("Sin respuesta en 15 s: el servidor no contesta al inicio de sesión (así se queda en 'Conectando').")
        return
    except Exception as e:
        print("Error leyendo la respuesta:", e)
        return
    packet_id = payload[0]
    names = {0x00: "Disconnect", 0x01: "Encryption Request (pide cuenta premium)", 0x02: "Login Success (modo offline)",
             0x03: "Set Compression", 0x04: "Login Plugin Request (handshake de Forge u otro)"}
    print(f"Respuesta en {time.time() - start:.2f}s: paquete 0x{packet_id:02x} = {names.get(packet_id, 'desconocido')}")
    if packet_id == 0x00:
        n, i = 0, 1
        shift = 0
        while True:
            b = payload[i]
            i += 1
            n |= (b & 0x7F) << shift
            shift += 7
            if not b & 0x80:
                break
        print("Motivo:", payload[i:i + n].decode("utf-8", "replace")[:1500])
    elif packet_id == 0x03:
        print("El servidor activa compresión; el inicio de sesión sigue normalmente.")
    sock.close()


probe("Cliente Forge (marca FML3)", f"{host}\0FML3\0")
probe("Cliente sin Forge", host)
