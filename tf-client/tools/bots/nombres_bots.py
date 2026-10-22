"""Los nombres de los bots (/tf bots, 1.3.109): inventados, únicos y que NO use ninguna cuenta de Minecraft (se mira
en la API de Mojang: 404 = libre). Escribe src/main/resources/tfclient/bots/nombres.json.

Uso: python3 -I tools/bots/nombres_bots.py [cuántos=68]
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "tfclient", "bots",
                   "nombres.json")
CANDIDATOS = """
LunaVerdeTF Tejon_Rojizo Mirtilo_22 PanchoKrafteo Tormenta_Azulina CamiReyes_07 Ozzymarin VientoAltoMC SirPepinilloX
Aldebaran_Joe NieblaDeAbril Kiwi_Salvaje_ MapacheLoco91 Ramiro_Pixel ZafiroOscuro_ Tomasito_Gamer Lirio_Nocturno
CometaFugaz_ Bruma_Andina ElChivoMinero Pistacho_Real Maripepa_MC Grillo_Saltarin CactusFeliz_ Olivia_Nube
Draco_Turquesa Lucerito_99 MangoBiche_ Teo_Constructor Rocio_Diamante Bastian_Llama ColibriVeloz Panqueque_77
SombraDeRoble Nacho_Picaron Tita_Brillos Gael_Hechicero Yerba_Mate_MC Pumita_Andes Flor_de_Cobre Zorrito_Gris
Martina_Rubi_ Kalimba_Tones Pedrito_Pala ElBarcoPirata Nube_de_Azucar Duende_Verde_ Coral_Marino_ Inti_Dorado
Valen_Arcoiris Chispa_Lunar Arandano_Fiel PolloAsadoXD Bichito_Azul Mora_Silvestre Leon_de_Plata Hongo_Saltador
Anita_Cuarzo Lince_Nevado Cafecito_Tibio Sol_de_Mayo_ Tamarindo_Rex Gato_Astral_ Paloma_Mensaje Rayito_Verde
Ulises_Errante Pepa_Calabaza Luciernaga_TF Benji_Escudo Quetzal_Rojo Mateo_Brujula Rio_Escondido Faro_del_Norte
Selva_Profunda Trueno_Manso Brisa_Marina_ Huellas_de_Oso Ocelote_Fiel Piedra_Lunar_ Lucas_Antorcha Mimo_Pixelado
Cereza_Fresca Halcon_Dormido Tortuga_Veloz Polen_Dorado Copito_Nevado Brujula_Rota Tizon_Ardiente Manzanilla_TF
""".split()


def libre(name):
    url = "https://api.mojang.com/users/profiles/minecraft/" + name
    for i in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "TFClient-tools/1.0"}), timeout=20) as r:
                return r.status == 204
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return True
            if e.code == 429:
                time.sleep(10 + i * 10)
                continue
            raise
    raise RuntimeError("sin respuesta: " + name)


def main():
    want = int(sys.argv[1]) if len(sys.argv) > 1 else 68
    seen, ok = set(), []
    for n in CANDIDATOS:
        if not re.fullmatch(r"[A-Za-z0-9_]{3,16}", n) or n.lower() in seen:
            print("no vale:", n)
            continue
        seen.add(n.lower())
        if libre(n):
            ok.append(n)
        else:
            print("ya existe:", n)
        time.sleep(0.25)
        if len(ok) >= want:
            break
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    json.dump(ok, open(OUT, "w"), indent=1, ensure_ascii=False)
    print("libres:", len(ok), "->", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
