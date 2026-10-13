"""Genera las texturas de las piedras de protección de TF Claims (una por tamaño de zona).

1.3.66 (el dueño: «las texturas son exageradas; la primera y la segunda se parecen, tonos grises»): rediseño limpio,
pixel a pixel en 32x32. Piedra pulida y lisa (sin ruido), con su canto de luz arriba a la izquierda; una placa embutida
con un remate de hierro (de oro en las de pago: 250, 300 y 500) y sus remaches; en el centro, del color del nivel:
  - front: las letras TF en relieve (la cara que mira a quien la pone).
  - side:  una gema tallada (rombo con sus caras de luz y sombra) y debajo el tamaño de la zona («64»).
  - top:   una gema redonda tallada: aquí empieza la protección, que sube hasta el cielo.
  - bottom: la placa sola.
Solo brillan de noche la gema, las letras y el número (capa _glow a plena luz), no el marco: nada de rayos ni neón por
todo el bloque. Los 10 colores van como una escala de rareza (verde → turquesa → celeste → azul → violeta → magenta →
coral → rojo → naranja → oro) y son los mismos que el borde de la zona (ClaimTier) y el nombre del objeto
(ClaimBlocks.colorForTier).

Uso: python3 tools/gen_claim_blocks.py [--preview DIR]
"""
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "tfclient")
TEX = os.path.join(ROOT, "textures", "block", "claims")
N = 32

# tamaño (como en ClaimTier) → color de la gema
TIERS = [
    ("10x10", (92, 214, 120)),
    ("25x25", (40, 206, 192)),
    ("40x40", (76, 178, 255)),
    ("64x64", (86, 112, 255)),
    ("80x80", (156, 98, 255)),
    ("100x100", (228, 82, 216)),
    ("150x150", (255, 104, 140)),
    ("250x250", (240, 66, 66)),
    ("300x300", (255, 146, 40)),
    ("500x500", (255, 212, 70)),
]
PAID = {"250x250", "300x300", "500x500"}

DARK = (16, 18, 24)
IRON = {"hi": (214, 220, 232), "mid": (156, 164, 182), "lo": (98, 104, 122)}
GOLD = {"hi": (255, 232, 150), "mid": (232, 178, 62), "lo": (166, 108, 30)}


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def gem_shades(c):
    return {"hi": mix(c, (255, 255, 255), 0.55), "lt": mix(c, (255, 255, 255), 0.22), "md": c,
            "dk": mix(c, (0, 0, 0), 0.28), "dd": mix(c, (0, 0, 0), 0.5)}


def base_block(seed, trim):
    """La piedra pulida con la placa embutida y su remate (hierro u oro)."""
    rnd = random.Random(seed)
    px = {}
    for y in range(N):
        for x in range(N):
            v = rnd.choice((-3, -1, 0, 0, 1, 3))
            px[x, y] = (46 + v, 48 + v, 60 + v)
    for i in range(N):  # cantos del bloque: luz arriba/izquierda, sombra abajo/derecha
        px[i, 0] = px[0, i] = (72, 75, 90)
        px[i, N - 1] = px[N - 1, i] = (24, 26, 34)
    px[0, N - 1] = px[N - 1, 0] = (40, 42, 52)
    for y in range(4, N - 4):  # la placa, hundida
        for x in range(4, N - 4):
            v = rnd.choice((-2, 0, 0, 1))
            px[x, y] = (33 + v, 35 + v, 45 + v)
    for i in range(4, N - 4):
        px[i, 4] = px[4, i] = (23, 25, 33)          # sombra de dentro arriba/izquierda
        px[i, N - 5] = px[N - 5, i] = (54, 57, 70)  # luz de dentro abajo/derecha
    for i in range(3, N - 3):  # el remate
        px[i, 3] = px[3, i] = trim["hi"]
        px[i, N - 4] = px[N - 4, i] = trim["lo"]
    px[3, N - 4] = px[N - 4, 3] = trim["mid"]
    for cx, cy in ((3, 3), (N - 4, 3), (3, N - 4), (N - 4, N - 4)):  # remaches
        px[cx, cy] = trim["hi"]
    for cx, cy in ((5, 5), (N - 7, 5), (5, N - 7), (N - 7, N - 7)):
        px[cx, cy], px[cx + 1, cy], px[cx, cy + 1], px[cx + 1, cy + 1] = trim["hi"], trim["mid"], trim["mid"], trim["lo"]
    return px


DIGITS = {
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"], "3": ["111", "001", "011", "001", "111"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
}


def number(text, y, color):
    """El número (radio de la zona) en cifras de 3x5 centradas, con su sombra."""
    w = len(text) * 4 - 1
    x0 = (N - w) // 2
    cells, shadow = {}, set()
    for i, ch in enumerate(text):
        for r, row in enumerate(DIGITS[ch]):
            for c, bit in enumerate(row):
                if bit == "1":
                    cells[x0 + i * 4 + c, y + r] = color
    for (x, y2) in cells:
        if (x + 1, y2 + 1) not in cells:
            shadow.add((x + 1, y2 + 1))
    return cells, shadow


def letters(sh):
    """T y F en relieve: cara del color, canto de luz arriba y sombra abajo/derecha."""
    cells = {}

    def rect(x0, y0, x1, y1):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                cells[x, y] = sh["md"]
    rect(9, 11, 14, 12)
    rect(11, 13, 12, 20)
    rect(17, 11, 22, 12)
    rect(17, 13, 18, 20)
    rect(19, 15, 21, 16)
    for (x, y) in list(cells):
        if (x, y - 1) not in cells:
            cells[x, y] = sh["hi"]
        elif (x + 1, y) not in cells or (x, y + 1) not in cells:
            cells[x, y] = sh["dk"]
    return cells


def diamond_gem(sh, cx, cy, r):
    """Gema en rombo tallada: cuatro caras (luz arriba a la izquierda), filo oscuro y un brillo."""
    cells = {}
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            if abs(dx) + abs(dy) <= r:
                if abs(dx) + abs(dy) > r - 1:
                    cells[x, y] = sh["dd"]
                elif dx <= 0 and dy <= 0:
                    cells[x, y] = sh["lt"]
                elif dx > 0 and dy <= 0:
                    cells[x, y] = sh["md"]
                elif dx <= 0:
                    cells[x, y] = sh["dk"]
                else:
                    cells[x, y] = sh["dd"]
    hx, hy = int(cx - r / 2.5), int(cy - r / 2.5)
    cells[hx, hy] = sh["hi"]
    cells[hx + 1, hy] = sh["hi"]
    cells[hx, hy + 1] = sh["hi"]
    return cells


def round_gem(sh, cx, cy, r):
    """Gema redonda tallada (vista desde arriba): anillo oscuro, corona, mesa clara y brillo."""
    cells = {}
    for y in range(N):
        for x in range(N):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if d <= r:
                if d > r - 1.2:
                    cells[x, y] = sh["dd"]
                elif d > r * 0.55:
                    cells[x, y] = sh["dk"] if (x + 0.5 - cx) + (y + 0.5 - cy) > 0 else sh["md"]
                else:
                    cells[x, y] = sh["lt"] if (x + 0.5 - cx) + (y + 0.5 - cy) < 1 else sh["md"]
    cells[int(cx - r / 2.2), int(cy - r / 2.2)] = sh["hi"]
    cells[int(cx - r / 2.2) + 1, int(cy - r / 2.2)] = sh["hi"]
    return cells


def face(kind, label, color, seed):
    sh = gem_shades(color)
    trim = GOLD if label in PAID else IRON
    px = base_block(seed, trim)
    lit = {}
    if kind == "front":
        lit = letters(sh)
    elif kind == "side":
        lit = diamond_gem(sh, 16, 13, 7.5)
        cells, shadow = number(label.split("x")[0], 22, sh["lt"])
        for c in shadow:
            px[c] = DARK
        lit.update(cells)
    elif kind == "top":
        lit = round_gem(sh, 16, 16, 8.5)
    # sombra de lo que va en relieve (abajo a la derecha)
    for (x, y) in lit:
        if (x + 1, y + 1) not in lit and 4 < x + 1 < N - 4 and 4 < y + 1 < N - 4 and kind != "side":
            px[x + 1, y + 1] = DARK
    for c, col in lit.items():
        px[c] = col
    img = Image.new("RGBA", (N, N))
    for (x, y), col in px.items():
        img.putpixel((x, y), col + (255,))
    over = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for (x, y), col in lit.items():
        over.putpixel((x, y), col + (255,))
    return img, over


FACES = ("front", "side", "top", "bottom")


def build():
    os.makedirs(TEX, exist_ok=True)
    out = {}
    for label, color in TIERS:
        name = f"proteccion_{label}"
        for i, kind in enumerate(FACES):
            img, over = face(kind, label, color, seed=1000 + i * 17)
            img.save(os.path.join(TEX, f"{name}_{kind}.png"), optimize=True)
            if kind != "bottom":
                over.save(os.path.join(TEX, f"{name}_{kind}_glow.png"), optimize=True)
            out[name, kind] = img
    write_models()
    return out


def write_models():
    import json
    bs_dir = os.path.join(ROOT, "blockstates")
    bm_dir = os.path.join(ROOT, "models", "block", "claims")
    im_dir = os.path.join(ROOT, "models", "item")
    for d in (bs_dir, bm_dir, im_dir):
        os.makedirs(d, exist_ok=True)
    for label, _ in TIERS:
        name = f"proteccion_{label}"
        t = f"tfclient:block/claims/{name}"
        # Cubo con la piedra y encima, un pelo por fuera, las líneas de luz a plena luz (se ven de noche)
        faces = {
            "north": ("front", "#front", "#front_glow"),
            "south": ("side", "#side", "#side_glow"),
            "east": ("side", "#side", "#side_glow"),
            "west": ("side", "#side", "#side_glow"),
            "up": ("top", "#top", "#top_glow"),
            "down": ("bottom", "#bottom", None),
        }
        base = {d: {"uv": [0, 0, 16, 16], "texture": tex, "cullface": d} for d, (_, tex, _) in faces.items()}
        glow = {d: {"uv": [0, 0, 16, 16], "texture": g, "cullface": d,
                    "forge_data": {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}}
                for d, (_, _, g) in faces.items() if g}
        model = {
            "parent": "minecraft:block/block",
            "render_type": "minecraft:cutout",
            "textures": {
                "particle": f"{t}_side",
                "front": f"{t}_front", "side": f"{t}_side", "top": f"{t}_top", "bottom": f"{t}_bottom",
                "front_glow": f"{t}_front_glow", "side_glow": f"{t}_side_glow", "top_glow": f"{t}_top_glow",
            },
            "elements": [
                {"from": [0, 0, 0], "to": [16, 16, 16], "faces": base},
                {"from": [-0.01, -0.01, -0.01], "to": [16.01, 16.01, 16.01], "shade": False, "faces": glow},
            ],
            "display": {
                "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
            },
        }
        with open(os.path.join(bm_dir, f"{name}.json"), "w") as f:
            json.dump(model, f, indent=2)
        variants = {f"facing={d}": {"model": f"tfclient:block/claims/{name}", **({"y": y} if y else {})}
                    for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
        with open(os.path.join(bs_dir, f"{name}.json"), "w") as f:
            json.dump({"variants": variants}, f, indent=2)
        # En el inventario se ve como el bloque: la cara de las letras a la izquierda
        with open(os.path.join(im_dir, f"{name}.json"), "w") as f:
            json.dump({"parent": f"tfclient:block/claims/{name}"}, f, indent=2)


# --------------------------------------------------------------------------------------- vista previa

def iso(front, side, top, s=8):
    """Cubo en isométrica (como en el inventario): arriba, la cara de las letras a la izquierda y un lado a la derecha."""
    w = N * s
    f = front.resize((w, w), Image.NEAREST)
    sd = side.resize((w, w), Image.NEAREST)
    tp = top.resize((w, w), Image.NEAREST)
    W = int(w * 2 * 0.866) + 4
    H = int(w * 2) + 4
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    hx = w * 0.866
    # cara izquierda (front): esquina de arriba-izquierda en (0, w/2)
    def paste(img, coeffs, shade):
        dark = Image.new("RGBA", img.size, (0, 0, 0, int(255 * shade)))
        im = Image.alpha_composite(img, dark)
        t = im.transform((W, H), Image.AFFINE, coeffs, resample=Image.NEAREST)
        out.alpha_composite(t)
    # Para cada cara, la transformación inversa (destino → origen)
    # Izquierda: u va de (0, w/2) a (hx, w), v hacia abajo
    import numpy as np
    def inv(ax, ay, bx, by, ox, oy):
        m = np.array([[ax, bx, ox], [ay, by, oy], [0, 0, 1]], float)
        mi = np.linalg.inv(m)
        return tuple(mi[0]) + tuple(mi[1])
    paste(f, inv(hx / w, 0.5, 0, 1, 0, w / 2), 0.18)
    paste(sd, inv(hx / w, -0.5, 0, 1, hx, w), 0.38)
    paste(tp, inv(hx / w, 0.5, -hx / w, 0.5, hx, 0), 0.0)
    return out


def preview(dirname, faces):
    os.makedirs(dirname, exist_ok=True)
    # 1) Las caras del de 40x40 (el cian de la imagen) grandes
    row = Image.new("RGBA", (4 * 32 * 10 + 5 * 24, 32 * 10 + 70), (12, 14, 22, 255))
    d = ImageDraw.Draw(row)
    for i, kind in enumerate(FACES):
        img = faces["proteccion_40x40", kind].resize((320, 320), Image.NEAREST)
        x = 24 + i * (320 + 24)
        row.paste(img, (x, 24))
        d.text((x, 24 + 320 + 14), kind, fill=(220, 230, 240))
    row.save(os.path.join(dirname, "claim_faces_40x40.png"))
    # 2) Los diez tamaños en 3D
    tiles = []
    for label, _ in TIERS:
        name = f"proteccion_{label}"
        tiles.append((label, iso(faces[name, "front"], faces[name, "side"], faces[name, "top"], s=5)))
    tw, th = tiles[0][1].size
    cols = 5
    sheet = Image.new("RGBA", (cols * (tw + 30) + 30, 2 * (th + 60) + 30), (12, 14, 22, 255))
    d = ImageDraw.Draw(sheet)
    for i, (label, t) in enumerate(tiles):
        x = 30 + (i % cols) * (tw + 30)
        y = 30 + (i // cols) * (th + 60)
        sheet.alpha_composite(t, (x, y))
        d.text((x + tw // 2 - 20, y + th + 12), label, fill=(220, 230, 240))
    sheet.save(os.path.join(dirname, "claim_blocks_3d.png"))
    # 2b) De noche: la piedra casi a oscuras y las líneas de luz a plena luz (la capa _glow)
    night = Image.new("RGBA", sheet.size, (4, 5, 10, 255))
    d = ImageDraw.Draw(night)
    for i, (label, _) in enumerate(TIERS):
        name = f"proteccion_{label}"
        def dark(img):
            return Image.alpha_composite(img, Image.new("RGBA", img.size, (0, 0, 0, 200)))
        fr = dark(faces[name, "front"]); sd = dark(faces[name, "side"]); tp = dark(faces[name, "top"])
        _, gf = face("front", dict(TIERS)[label], 1000)
        _, gs = face("side", dict(TIERS)[label], 1017)
        _, gt = face("top", dict(TIERS)[label], 1034)
        fr.alpha_composite(gf); sd.alpha_composite(gs); tp.alpha_composite(gt)
        t = iso(fr, sd, tp, s=5)
        x = 30 + (i % cols) * (tw + 30)
        y = 30 + (i // cols) * (th + 60)
        night.alpha_composite(t, (x, y))
        d.text((x + tw // 2 - 20, y + th + 12), label, fill=(200, 210, 220))
    night.save(os.path.join(dirname, "claim_blocks_night.png"))
    # 3) Las 4 caras de los 10 tamaños
    grid = Image.new("RGBA", (4 * 32 * 4 + 5 * 8 + 90, 10 * (32 * 4 + 8) + 8), (12, 14, 22, 255))
    d = ImageDraw.Draw(grid)
    for r, (label, _) in enumerate(TIERS):
        y = 8 + r * (128 + 8)
        d.text((8, y + 58), label, fill=(220, 230, 240))
        for c, kind in enumerate(FACES):
            grid.paste(faces[f"proteccion_{label}", kind].resize((128, 128), Image.NEAREST), (90 + c * (128 + 8), y))
    grid.save(os.path.join(dirname, "claim_faces_all.png"))


if __name__ == "__main__":
    faces = build()
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1], faces)
    print("ok:", len(TIERS), "piedras")
