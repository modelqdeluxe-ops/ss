"""Las skins de los bots (/tf bots, 1.3.109): firmadas por Mojang, para que el TAB (y el cliente) las enseñe.

Las skins de un perfil solo se ven si su «textures» va firmada por Mojang (el cliente comprueba la firma). MineSkin
(api.mineskin.org) guarda miles ya subidas a cuentas de Minecraft, con su valor y su firma. Este script:

  1. lista skins públicas de MineSkin (--listar N) y baja cada imagen (64x64, de textures.minecraft.net) a una carpeta;
  2. hace una hoja con las caras para elegirlas a ojo (--hoja);
  3. con las elegidas (--elegir ids.txt) pide su valor y firma y escribe
     src/main/resources/tfclient/bots/skins.json [{"value", "signature", "slim"}];
  4. --comprobar: baja las de skins.json (las firmadas de verdad) y mira que no haya dos parecidas (cara o cuerpo).

(1.3.109, el dueño: «veo muchos repetidos, la regla es únicos» y «pon skins creíbles de jugadores reales, no genéricas»)
las de MineSkin más recientes son casi todas plantillas iguales; se bajaron 1 400, se quitaron las de plantilla y las
repetidas (hoja), se eligieron a ojo de cuerpo entero las que parecen de jugadores de verdad (ropa, pelo; sin mobs,
memes ni personajes famosos) y de esas, 68 sin dos parecidas (cuerpo de frente ≥ 30 y cara ≥ 32 de diferencia media).
Los nombres (nombres.json, tools/bots/nombres_bots.py) van emparejados con la skin del mismo número (chica o chico).

Uso: python3 -I tools/bots/skins_bots.py <carpeta> --listar 1400 | --hoja | --elegir <ids.txt> | --comprobar
"""
import io
import json
import os
import sys
import time
import urllib.request

from PIL import Image, ImageDraw

UA = {"User-Agent": "TFClient-tools/1.0 (bots)"}
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "tfclient", "bots",
                   "skins.json")


def get(url, binary=False):
    for i in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                data = r.read()
                return data if binary else json.loads(data)
        except Exception as e:  # noqa: BLE001
            if i == 3:
                raise
            time.sleep(2 + i * 2)


def listar(d, n):
    os.makedirs(os.path.join(d, "png"), exist_ok=True)
    skins, after = [], None
    while len(skins) < n:
        url = "https://api.mineskin.org/v2/skins?size=48" + (f"&after={after}" if after else "")
        page = get(url)
        got = page.get("skins", [])
        if not got:
            break
        skins += got
        after = (page.get("pagination", {}).get("next") or {}).get("after") or got[-1].get("uuid")
        time.sleep(0.5)
    skins = skins[:n]
    for s in skins:
        f = os.path.join(d, "png", s["uuid"] + ".png")
        if not os.path.exists(f):
            try:
                open(f, "wb").write(get("https://textures.minecraft.net/texture/" + s["texture"], binary=True))
            except Exception:  # noqa: BLE001 (alguna textura ya no está: se salta)
                pass
    json.dump(skins, open(os.path.join(d, "lista.json"), "w"))
    print("skins:", len(skins))


def face(png):
    try:
        im = Image.open(io.BytesIO(open(png, "rb").read())).convert("RGBA")
    except Exception:  # noqa: BLE001 (una imagen rota)
        return None
    if im.size[1] < 32:
        return None
    f = im.crop((8, 8, 16, 16))
    hat = im.crop((40, 8, 48, 16))
    f.alpha_composite(hat)
    return f


def bland(fc):
    """¿Una cara de plantilla (piel lisa y poco más) o casi vacía? Esas no: en el TAB todas iguales."""
    px = [p for p in fc.getdata() if p[3] > 0]
    if len(px) < 56:
        return True
    cols = {(r // 24, g // 24, b // 24) for r, g, b, a in px}
    return len(cols) <= 4


def hoja(d):
    skins = json.load(open(os.path.join(d, "lista.json")))
    # sin repetidas (misma cara) ni de plantilla
    seen, keep = set(), []
    for s in skins:
        f = os.path.join(d, "png", s["uuid"] + ".png")
        if not os.path.exists(f):
            continue
        fc = face(f)
        if fc is None or bland(fc):
            continue
        key = fc.tobytes()
        if key in seen:
            continue
        seen.add(key)
        keep.append(s)
    skins = keep
    json.dump(skins, open(os.path.join(d, "candidatas.json"), "w"))
    S, cols = 64, 16
    rows = (len(skins) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (S + 6), rows * (S + 18)), (30, 30, 36))
    dr = ImageDraw.Draw(sheet)
    for i, s in enumerate(skins):
        fc = face(os.path.join(d, "png", s["uuid"] + ".png"))
        x, y = (i % cols) * (S + 6), (i // cols) * (S + 18)
        if fc is not None:
            sheet.paste(fc.resize((S, S), Image.NEAREST), (x, y), fc.resize((S, S), Image.NEAREST))
        dr.text((x + 2, y + S + 2), str(i), fill=(255, 255, 0))
    sheet.save(os.path.join(d, "hoja.png"))
    print("hoja:", os.path.join(d, "hoja.png"))


def elegir(d, ids_file):
    skins = json.load(open(os.path.join(d, "candidatas.json")))
    idx = [int(x) for x in open(ids_file).read().split()]
    out = []
    for i in idx:
        s = get("https://api.mineskin.org/v2/skins/" + skins[i]["uuid"])["skin"]
        data = s["texture"]["data"]
        out.append({"value": data["value"], "signature": data["signature"], "slim": s.get("variant") == "slim"})
        time.sleep(0.3)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    json.dump(out, open(OUT, "w"), indent=1)
    print("skins firmadas:", len(out), "->", os.path.normpath(OUT))


def comprobar(d):
    import base64
    skins = json.load(open(OUT))
    figs = []
    os.makedirs(os.path.join(d, "firmadas"), exist_ok=True)
    for i, s in enumerate(skins):
        url = json.loads(base64.b64decode(s["value"]))["textures"]["SKIN"]["url"]
        f = os.path.join(d, "firmadas", f"{i}.png")
        open(f, "wb").write(get(url, binary=True))
        im = Image.open(f).convert("RGBA")
        fc = face(f)
        body = im.crop((20, 20, 28, 32))
        figs.append((list(fc.getdata()), list(body.getdata())))
    def diff(a, b):
        return sum(abs(x[k] - y[k]) for x, y in zip(a, b) for k in range(3)) / (3 * len(a))
    bad = []
    for i in range(len(figs)):
        for j in range(i + 1, len(figs)):
            if diff(figs[i][0], figs[j][0]) < 32 and diff(figs[i][1], figs[j][1]) < 30:
                bad.append((i, j))
    print(len(skins), "skins;", "parecidas:", bad or "ninguna")


if __name__ == "__main__":
    folder = sys.argv[1]
    if "--comprobar" in sys.argv:
        comprobar(folder)
    if "--listar" in sys.argv:
        listar(folder, int(sys.argv[sys.argv.index("--listar") + 1]))
    if "--hoja" in sys.argv:
        hoja(folder)
    if "--elegir" in sys.argv:
        elegir(folder, sys.argv[sys.argv.index("--elegir") + 1])
