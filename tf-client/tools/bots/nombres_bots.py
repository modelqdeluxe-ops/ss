"""Los nombres de los bots (/tf bots): como los de jugadores de verdad, sin dos iguales ni hechos con el mismo molde,
y que NO use ninguna cuenta de Minecraft (se mira en la API de Mojang). Escribe
src/main/resources/tfclient/bots/nombres.json (el nombre n va con la skin n de skins.json).

Por qué así (1.3.110). El dueño, con una captura del TAB: «los nombres son iguales, genera nombres únicos sin patrones
idénticos» y «¿por qué siempre usas un apellido tipo Balón Rojo, Oso Marino? Investiga cómo usan los nombres de
Minecraft las personas reales». Los de la 1.3.109 eran casi todos «Palabra_Palabra» en español (Bichito_Azul,
Bruma_Andina, Leon_de_Plata…), algo que casi nadie hace. Medido en 190 222 nombres reales de cuentas de Minecraft Java
(una muestra repartida por todo huggingface.co/datasets/mudkipdev/minecraft-usernames, 51,5 millones):
  - largo medio 9,5 (lo más común, de 7 a 10);
  - 37 % acaban en cifras (sobre todo 2 o 4: «jamiepig14», «Zach0624», «Santii1422»); 5 % con cifras en medio
    («edu4rd00»); 1 % empiezan por cifra;
  - solo 14 % llevan «_» (y 2,6 % dos o más); 32 % todo en minúsculas, 29 % con Mayúscula y lo demás en minúsculas,
    28 % en CamelCase («OutlawJellyBean»), 5 % en MAYÚSCULAS;
  - «xX…Xx» 0,1 % y «x»/«i» delante («xNacho») 0,7 %: casi nadie.
Los de raíz hispana (6 131 de la muestra) son igual: apodo + cifras («Mati39», «nico0425», «juancu17»), nombre +
iniciales («sebastianrv», «NicoPL», «SRTJuan»), letras repetidas («Tomiinoo», «NicooWe»), «xd» («gamer93xd»),
«Its»/«The» + nombre («ItsDanielTime»), palabras en inglés en CamelCase y apodos inventados («Iynox», «Xuxixo»).
Así que se generan con esas plantillas en esas proporciones (semilla fija: sale siempre lo mismo), con estas reglas:
  - nada de «Adjetivo + Sustantivo» (ni «Balón Rojo» ni «SneakyOtter»): es lo que el dueño ya no quiere ver;
  - ningún MOLDE (la forma: minúsculas «a», Mayúscula «Aa», MAYÚSCULAS «AA», cifras «9», «_») más de 4 veces (los de
    una sola palabra, «a» y «Aa», 9-10: «fovesel» y «francomedina» no se parecen), como mucho el 45 % con cifras, cada
    plantilla como mucho su parte según lo medido (TOPE, nunca más de 3) y ningún apodo dos veces (en el TAB se ven 10
    o 20 a la vez: nada debe parecer de serie);
  - ninguna raíz repetida (dos nombres no comparten 5 letras seguidas, ni apellido, ni dos inventados el final) ni nombres de una sola palabra corriente;
  - cada coletilla (xD, Rex, god, Ok…) una sola vez, «The»/«Its»/«Soy» delante dos en total y letras repetidas
    («Tomiii») en 3 como mucho;
  - los de chica, en las skins de chica (CHICAS); los demás, de chico o neutros.

Uso: [MOJANG_CACHE=<archivo.json>] python3 -I tools/bots/nombres_bots.py
"""
import json
import os
import random
import re
import sys
import time
import urllib.error
import urllib.request
from collections import Counter

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "tfclient", "bots",
                   "nombres.json")
UA = {"User-Agent": "TFClient-tools/1.0 (bots)", "Content-Type": "application/json"}
SKINS = 68
# Las skins de chica (mirado a ojo en la hoja de skins_bots.py --hoja)
CHICAS = {3, 7, 9, 18, 19, 20, 21, 23, 24, 28, 29, 34, 38, 39, 43, 53, 54, 57, 58, 61, 63, 67}

CHICO = ("mati nico santi tomi juancu fede facu lauti benja thiago gonza rodri fran emi gabo migue nahu joaco tobi "
         "pato toto maxi seba kevin brayan dylan axel bruno leo iker lucho pipe ivo dante gael simon teo enzo "
         "ramiro lisandro bauti valen hernan jere franco ciro uriel milo alan joel tadeo bastian elias dami "
         "lucca marcos nacho rafa german tincho chino flaco lean mauri cris alexis jonas yago kike lalo beto "
         "ruben sergi oscar pepo coco ezequiel thomas mateo dario").split()
CHICA = ("sofi cami mili flor mica abril delfi jaz juli xime lore agus lu mora pili rocio aye yami naty euge belu "
         "caro meli ari luz maite oriana renata trini zoe ema lola martu vicky tati brisa ambar sol "
         "pau anto romi luli gime nati vale ceci dani josefina emilia isa candela male fiore juana olivia "
         "ainhoa noa kiara aitana lara alma nina").split()
INGLES = ("Wolf Frost Pixel Night Storm Panda Blaze Lava Toast Pickle Noodle Bean Cactus Ghost Ninja Llama Mango "
          "Waffle Taco Cookie Moon Echo Byte Turtle Rocket Muffin Potato Biscuit Comet Raven Fox Owl Badger Otter "
          "Sneaky Lazy Salty Crispy Fluffy Silent Lucky Grumpy Frosty Spicy Sleepy Rusty Dusty Happy").split()
ADJ_EN = set("Sneaky Lazy Salty Crispy Fluffy Silent Lucky Grumpy Frosty Spicy Sleepy Rusty Dusty Happy".split())
# Apellidos (en la muestra hay muchos «nombre + apellido»: «ivangallego», «AxelMartins», «KevinBoland»)
APELLIDO = ("ramos rojas vega soto paz luna mora rios nunez sosa ortiz campos molina peralta acosta medina ibarra "
            "toledo bustos godoy ferreyra quiroga villalba aguirre cabrera").split()
# Los apodos inventados, como los de verdad de la muestra («Iynox», «Xuxixo», «Nilokan», «Dosukeii», «Vexuji»):
# dos o tres sílabas fáciles de leer y un final corto
CONS = "b d f g k l m n r s t v x z ch sh".split()
VOC = "a e i o u".split()
FIN = ["x", "z", "n", "s", "ii", "ik", "an", "on", "el", "", ""]


def inventado(rng):
    s = "".join(rng.choice(CONS) + rng.choice(VOC) for _ in range(rng.choice((2, 2, 3))))
    fin = rng.choice(FIN)
    return s + (fin if not fin or fin[0] not in "aeiou" else fin[1:] or "n")


def cifras(rng, n):
    if n == 4 and rng.random() < 0.4:
        return str(rng.randint(2005, 2014))
    return "".join(str(rng.randint(0, 9)) for _ in range(n))


def iniciales(rng, n):
    while True:
        s = "".join(rng.choice("bcdfghjklmnprstvz") for _ in range(n))
        if "tf" not in s:  # «TF» parecería puesto por el servidor
            return s


def dobla(nombre):
    """«tomi» → «tomii», «santi» → «santiii»: la última vocal, dos o tres veces (si acaba en consonante, igual)."""
    return nombre + nombre[-1] * (1 + (len(nombre) % 2)) if re.search(r"[aeiou]$", nombre) else nombre


def leet(s):
    for a, b in (("a", "4"), ("e", "3"), ("o", "0"), ("i", "1")):
        if a in s[1:]:
            i = s.index(a, 1)
            return s[:i] + b + s[i + 1:]
    return s


# Plantillas (las cuentas medidas en la muestra van entre paréntesis): (nombre, peso, función)
def plantillas(rng, nom):
    N = nom.capitalize()
    en = lambda: rng.choice([w for w in INGLES if w not in ADJ_EN])  # noqa: E731
    return [
        ("apodo+2cifras", 5, lambda: (dobla(nom) if rng.random() < 0.15 else nom + rng.choice(["", iniciales(rng, 1)])) + cifras(rng, 2)),
        ("Apodo+2cifras", 5, lambda: (dobla(N) if rng.random() < 0.15 else N) + cifras(rng, 2)),
        ("apodo+4cifras", 2, lambda: nom + cifras(rng, 4)),
        ("apodo_2cifras", 2, lambda: nom + "_" + cifras(rng, 2)),
        ("apodo+cifras+letra", 2, lambda: nom + cifras(rng, 2) + rng.choice("xzk")),
        ("cifra+Apodo", 1, lambda: str(rng.randint(1, 9)) + N + rng.choice(["", cifras(rng, 2)])),
        ("nombre+apellido", 3, lambda: nom + rng.choice(APELLIDO)),
        ("NombreApellido", 3, lambda: N + rng.choice(APELLIDO).capitalize()),
        ("NombreApellido+cifras", 2, lambda: N + rng.choice(APELLIDO).capitalize() + cifras(rng, 2)),
        ("Apodo+4cifras", 4, lambda: (dobla(N) if rng.random() < 0.2 else N) + cifras(rng, 4)),
        ("apodo+3cifras", 3, lambda: nom + cifras(rng, 3)),
        ("Apodo+1cifra", 3, lambda: N + rng.choice(["", iniciales(rng, 1)]) + cifras(rng, 1)),
        ("apodo+iniciales", 6, lambda: nom + iniciales(rng, rng.choice((2, 3)))),
        ("INICIALES+Apodo", 2, lambda: iniciales(rng, rng.choice((2, 3))).upper() + N),
        ("Apodo+INICIALES", 2, lambda: N + iniciales(rng, 2).upper()),
        ("Its/The+Apodo", 2, lambda: rng.choice(["Its", "The", "Soy"]) + N + rng.choice(["", "Time", "Rex", "Gg", ""])),
        ("Palabra inglesa+cifras", 3, lambda: rng.choice([en(), en().lower(), en() + "x", en().upper()]) + cifras(rng, rng.choice((2, 3, 4)))),
        ("Apodo+The+Palabra", 1, lambda: N + "The" + en()),
        ("inventado", 8, lambda: inventado(rng).capitalize()),
        ("inventado minúsculas", 4, lambda: inventado(rng)),
        ("apodo repetido", 4, lambda: dobla(nom) + nom[-1] if re.search(r"[aeiou]$", nom) else nom + "x"),
        ("Apodo repetido", 3, lambda: dobla(N) + N[-1] if re.search(r"[aeiou]$", nom) else N + "z"),
        ("inventado+cifras", 4, lambda: inventado(rng) + cifras(rng, rng.choice((1, 2, 3)))),
        ("apodo+xd", 2, lambda: nom + rng.choice(["xd", "xD", "XD"]) + rng.choice(["", cifras(rng, 2)])),
        ("Apodo repetido+sílaba", 3, lambda: dobla(N) + rng.choice(["We", "Uwu", "Xz", "Ok", "Yt"])),
        ("Apodo_cifras", 2, lambda: N + "_" + cifras(rng, rng.choice((2, 4)))),
        ("_Apodo_", 1, lambda: "_" + N + "_"),
        ("apodo_iniciales", 3, lambda: nom + "_" + iniciales(rng, rng.choice((1, 2)))),
        ("leet+cifras", 2, lambda: leet(nom + rng.choice(["", iniciales(rng, 1)])) + cifras(rng, 2)),
        ("MAYÚSCULAS+cifras", 1, lambda: leet(nom).upper() + cifras(rng, 2)),
        ("apodo+palabra", 3, lambda: nom + rng.choice(["crack", "pro", "gamer", "mc", "uwu", "god"]) + rng.choice(["", cifras(rng, 2)])),
        ("inicial+apodo+cifras", 2, lambda: rng.choice("dmjklt") + nom + cifras(rng, 2)),
        ("dos apodos", 2, lambda: nom + rng.choice([n for n in CHICO + CHICA if n != nom])[:3] + rng.choice(["", cifras(rng, 4)])),
        ("El_Apodo", 1, lambda: ("La_" if nom in CHICA else "El_") + N + rng.choice(["", "x"])),
        ("apodo+xx", 2, lambda: nom + rng.choice(["xx", "zz", "kk"])),
        ("diminutivo+cifras", 2, lambda: re.sub(r"[aeiou]$", "", nom) + ("ita" if nom in CHICA else "ito") + cifras(rng, 2)),
        ("The+Palabra+cifras", 1, lambda: "The" + en() + cifras(rng, 3)),
        ("xApodo", 1, lambda: "x" + N + rng.choice(["", cifras(rng, 2)])),
    ]


def molde(name):
    out = []
    for t in re.findall(r"[A-Z][a-z]+|[A-Z]+(?![a-z])|[a-z]+|[0-9]+|_", name):
        if t == "_":
            out.append("_")
        elif t.isdigit():
            out.append("9" * min(len(t), 4))
        elif t.islower():
            out.append("a")
        elif t.isupper():
            out.append("AA" if len(t) > 1 else "A")
        else:
            out.append("Aa")
    return "".join(out)


def raices(name):
    letras = re.sub(r"[^a-z]", "", leet_inverso(name.lower()))
    return {letras[i:i + 5] for i in range(len(letras) - 4)}


def leet_inverso(s):
    return s.translate(str.maketrans("4301", "aeoi"))


CACHE = os.environ.get("MOJANG_CACHE")  # opcional: un .json donde guardar lo ya preguntado entre ejecuciones


def tomados(names):
    """Los que ya son de una cuenta (la API de Mojang, de 10 en 10)."""
    cache = json.load(open(CACHE)) if CACHE and os.path.exists(CACHE) else {}
    out = {n.lower() for n in names if cache.get(n.lower()) is True}
    names = [n for n in names if n.lower() not in cache]
    for i in range(0, len(names), 10):
        part = names[i:i + 10]
        for k in range(6):
            try:
                req = urllib.request.Request("https://api.minecraftservices.com/minecraft/profile/lookup/bulk/byname",
                                             data=json.dumps(part).encode(), headers=UA)
                with urllib.request.urlopen(req, timeout=30) as r:
                    ya = {p["name"].lower() for p in json.loads(r.read())}
                out |= ya
                cache.update({n.lower(): n.lower() in ya for n in part})
                break
            except urllib.error.HTTPError as e:
                if e.code not in (403, 429) or k == 5:
                    raise
                time.sleep(15 + k * 15)
        time.sleep(1.5)
    if CACHE:
        with open(CACHE, "w") as f:
            json.dump(cache, f)
    return out


def candidatos(rng, skin, n=1500):
    lista = CHICA if skin in CHICAS else CHICO
    out, vistos = [], set()
    while len(out) < n:
        nom = rng.choice(lista)
        pls = plantillas(rng, nom)
        nombre, _, f = rng.choices(pls, weights=[p[1] for p in pls])[0]
        cand = f()
        # de 7 a 16 (en la muestra el largo medio es 9,5; los cortos y sencillos ya son casi todos de alguien)
        if re.fullmatch(r"[A-Za-z0-9_]{7,16}", cand) and cand.lower() not in vistos:
            vistos.add(cand.lower())
            rasgos = {"ap:" + a for a in APELLIDO if a in cand.lower()}
            if nombre.startswith("inventado"):
                rasgos.add("fin:" + re.sub(r"[^a-z]", "", cand.lower())[-3:])
            out.append((cand, nombre, nom, molde(cand), frozenset(raices(cand) | rasgos), coletilla(cand),
                        cand.lower().startswith(DELANTE), bool(re.search(r"\d$", cand)),
                        nom if nom in leet_inverso(cand.lower()) else None))
    return out


# Cuántas veces puede salir cada plantilla: su parte de los 68 según su peso, con algo de holgura (y nunca más de 3)
_PESOS = {p[0]: p[1] for p in plantillas(random.Random(0), "nico")}
TOPE = {k: 1 if w == 1 else min(6 if k.startswith("inventado") else 3, round(w / sum(_PESOS.values()) * SKINS * 2.2))
        for k, w in _PESOS.items()}  # las raras (peso 1: «_Delfi_», «xLisandro», «9Nico»…), una sola vez
# Cada molde, 4 veces como mucho; los de una sola palabra sin cifras («fovesel», «francomedina», «Razinon»), 9-10: no se
# parecen entre sí y en la muestra son casi un tercio de todos
MAX_MOLDE = {"a": 10, "Aa": 9}
# Las coletillas (xD, Rex, god, Ok…) se notan en el TAB: cada una, una vez; y «The»/«Its»/«Soy» delante, dos en total
COLETILLAS = "xd rex time gg we uwu ok xz yt god crack pro gamer mc".split()
DELANTE = ("the", "its", "soy")
# «Tomiii», «Rocioooo»: tres o más letras iguales seguidas, como mucho en 3 nombres (en la muestra es raro)
REPITE = re.compile(r"([A-Za-z])\1\1", re.I)


def coletilla(name):
    low = re.sub(r"\d+$", "", name.lower())
    return next((c for c in COLETILLAS if low.endswith(c) and len(low) > len(c) + 2), None)


# «Pixelx27», «Stormx616», «Owlx954» o «Dami_59», «Jere_2006», «Facu_27»: con tres ya se ve el molde
TOPE.update({"Palabra inglesa+cifras": 2, "Apodo_cifras": 2})
# Con tres de una misma plantilla poco común ya se ve el molde («LLBGonza», «TZFBastian», «HJZJoaco»): dos; las de
# apodo + cifras (las más comunes de verdad) siguen con tres
COMUNES = {"apodo+2cifras", "Apodo+2cifras", "apodo+3cifras", "apodo+4cifras", "Apodo+4cifras", "Apodo+1cifra"}
TOPE.update({k: min(v, 2) for k, v in TOPE.items() if k not in COMUNES and not k.startswith("inventado")})
# Como en la muestra: el 37 % acaba en cifras (aquí, como mucho el 45 %)
MAX_CIFRAS = round(SKINS * 0.45)


def elegir(pool, ocupados, salir=True):
    """Prueba el orden normal de las skins y, si alguna se queda sin nombre, otros órdenes."""
    for intento in range(300):
        orden = list(range(len(pool)))
        if intento:
            random.Random(intento).shuffle(orden)
        r = elegir_en_orden(pool, ocupados, orden)
        if r:
            return r
    if salir:
        sys.exit("no salen 68 nombres con estas reglas")
    return None


def elegir_en_orden(pool, ocupados, orden, fijos=None):
    """Elige en ese orden; «fijos» (skin → candidato) se quedan como están y cuentan para las reglas."""
    elegidos, info, moldes, usos, raiz, apodos = [None] * len(pool), [None] * len(pool), Counter(), Counter(), set(), set()
    con_cifras = 0
    colas, delante, repetidas = set(), 0, 0
    picks = dict(fijos or {})
    for skin in list(picks) + list(orden):
        hecho, mejor = None, None
        for i, (cand, nombre, nom, m, rz, cola, dl, cif, apodo) in enumerate(pool[skin]):
            if skin in (fijos or {}):
                if i != fijos[skin]:
                    continue
                hecho = (cand, nombre, m, rz, cola, dl, cif, apodo)
                break
            if cand.lower() in ocupados or usos[nombre] >= TOPE[nombre] or moldes[m] >= MAX_MOLDE.get(m, 4):
                continue
            if rz & raiz or (apodo and apodo in apodos) or (cif and con_cifras >= MAX_CIFRAS):
                continue
            if (cola and cola in colas) or (dl and delante >= 2):
                continue
            if REPITE.search(cand) and repetidas >= 3:
                continue
            # la plantilla y el molde menos usados hasta ahora (así no se agotan las comunes antes de tiempo)
            peso = (usos[nombre] / TOPE[nombre] + moldes[m] / MAX_MOLDE.get(m, 4), i)
            if mejor is None or peso < mejor:
                mejor, hecho = peso, (cand, nombre, m, rz, cola, dl, cif, apodo)
                picks[skin] = i
        if not hecho:
            return None
        cand, nombre, m, rz, cola, dl, cif, apodo = hecho
        elegidos[skin] = cand
        info[skin] = (nombre, m)
        moldes[m] += 1
        usos[nombre] += 1
        raiz |= rz
        con_cifras += cif
        delante += dl
        repetidas += bool(REPITE.search(cand))
        if cola:
            colas.add(cola)
        if apodo:
            apodos.add(apodo)
    return elegidos, info, moldes, picks


def main():
    rng = random.Random(1310)
    pool = [candidatos(rng, skin) for skin in range(SKINS)]
    # se mira en Mojang de una vez los primeros candidatos de cada skin y se elige solo entre los libres; si no salen
    # los 68 con las reglas, se miran 40 más de cada una
    k, r = 0, None
    while r is None:
        k += 40
        if k > 1500:
            sys.exit("no salen 68 nombres con estas reglas")
        ya = tomados(sorted({c[0] for fila in pool for c in fila[:k]}))
        libres = [[c for c in fila[:k] if c[0].lower() not in ya] for fila in pool]
        print("con %d candidatos por skin: %d libres (de %d)" % (k, sum(map(len, libres)), 68 * k))
        r = elegir(libres, set(), salir=False)
    elegidos, info, moldes, _ = r
    for skin, (n, (nombre, m)) in enumerate(zip(elegidos, info)):
        print("%2d %-16s %-24s %s" % (skin, n, nombre, m))
    # la comprobación final, otra vez y de una
    assert len({n.lower() for n in elegidos}) == len(elegidos)
    assert not tomados(elegidos), "alguno ya existe"
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(elegidos, f, indent=1, ensure_ascii=False)
    n = len(elegidos)
    pc = lambda k: "%d%%" % round(100 * k / n)  # noqa: E731
    print("\n%d nombres, %d moldes (el que más, %d veces); largo medio %.1f" % (n, len(moldes), max(moldes.values()),
                                                                              sum(map(len, elegidos)) / n))
    print("acaban en cifras %s, con _ %s, minúsculas %s, CamelCase %s" % (
        pc(sum(bool(re.search(r"\d$", x)) for x in elegidos)), pc(sum("_" in x for x in elegidos)),
        pc(sum(x == x.lower() for x in elegidos)), pc(sum(bool(re.search(r"[a-z][A-Z]", x)) for x in elegidos))))
    print("->", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
