#!/usr/bin/env python3
"""Prepara las protecciones de la prueba de Mohist en <mundo>/claimblocks_data.json (con el servidor parado): la zona
grande de Ana (100x100 en 0,-60,0) con el grupo «Reino de Ana», en el que están Ana y Beto. Borra las demás zonas y
grupos de ese mundo de prueba.
   protecciones_prueba.py <mundo>/claimblocks_data.json"""
import hashlib
import json
import sys
import uuid


def offline(name):
    b = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    b[6] = b[6] & 0x0F | 0x30
    b[8] = b[8] & 0x3F | 0x80
    return str(uuid.UUID(bytes=bytes(b)))


ana, beto = offline("Ana"), offline("Beto")
grupo = "6a6f7269-6e6f-4000-8000-616e61000001"
madre = "6a6f7269-6e6f-4000-8000-616e61000002"
data = {
    "claims": [{
        "claimId": madre, "ownerUUID": ana, "ownerName": "Ana", "tierId": "claimstone_100x100", "radius": 100,
        "height": 60, "world": "minecraft:overworld", "x": 0, "y": -60, "z": 0, "createdAt": 1, "groupId": grupo,
        "members": [], "memberNames": [], "bannedPlayers": [],
        "flags": {"showWelcome": True, "welcomeMessage": "Bienvenido al Reino de Ana", "showLeave": False},
    }],
    "groups": [{"groupId": grupo, "name": "Reino de Ana", "motherClaimId": madre, "motherOwnerId": ana,
                "registered": [ana, beto]}],
}
json.dump(data, open(sys.argv[1], "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("protecciones de prueba en", sys.argv[1])
