#!/usr/bin/env python3
"""Escribe los clanes de la prueba de Mohist en <mundo>/tfclient/clanes.json (con el servidor parado): «Clan Prueba»
[TF] con Ana (líder) y Beto, «Otro Clan» [OTR] con Carlos; Dani y Eva sin clan. UUID sin cuenta (online-mode=false),
como los calcula Minecraft: UUID.nameUUIDFromBytes("OfflinePlayer:" + nombre). Quita los «chatClan» de antes."""
import hashlib
import json
import os
import sys
import uuid


def offline(name):
    b = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    b[6] = b[6] & 0x0F | 0x30
    b[8] = b[8] & 0x3F | 0x80
    return str(uuid.UUID(bytes=bytes(b)))


path = sys.argv[1]
os.makedirs(os.path.dirname(path), exist_ok=True)
data = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
ids = {n: offline(n) for n in ["Ana", "Beto", "Carlos", "Dani", "Eva"]}
clans = data.setdefault("clanes", {})
clans["clanprueba"] = {"nombre": "Clan Prueba", "etiqueta": "TF", "color": "#55FFFF", "lider": ids["Ana"],
                       "miembros": [ids["Ana"], ids["Beto"]]}
clans["otroclan"] = {"nombre": "Otro Clan", "etiqueta": "OTR", "color": "#FF5555", "lider": ids["Carlos"],
                     "miembros": [ids["Carlos"]]}
players = data.setdefault("jugadores", {})
for name, clan in [("Ana", "clanprueba"), ("Beto", "clanprueba"), ("Carlos", "otroclan"), ("Dani", None), ("Eva", None)]:
    p = players.setdefault(ids[name], {})
    p.pop("chatClan", None)
    if clan:
        p["clan"] = clan
    else:
        p.pop("clan", None)
json.dump(data, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("clanes de prueba en", path)
