"""Genera las texturas de las piedras de protección de TF Claims (una por tamaño de zona).

1.3.67 (el dueño: «la textura de las claims es una mierda exagerada otra vez: hazlas simplistas, sin números ni figuras
geométricas»): un bloque sencillo, a 16x16 como los del juego. Un marco de piedra pulida (de oro en las de pago: 250, 300
y 500) y dentro un panel liso del color del nivel, hundido, con su luz arriba. Igual en los cuatro lados y arriba; abajo,
solo la piedra. De noche se ve el panel (capa _glow a plena luz). Los 10 colores (verde → turquesa → celeste → azul →
violeta → magenta → coral → rojo → naranja → oro) son los del borde de la zona (ClaimTier) y del nombre del objeto
(ClaimBlocks.colorForTier).

Uso: python3 tools/gen_claim_blocks.py [--preview DIR]
"""
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "tfclient")
TEX = os.path.join(ROOT, "textures", "block", "claims")
N = 16

# tamaño (como en ClaimTier) → color del panel
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

# el marco: piedra pulida gris o, en las de pago, oro viejo (base, luz del canto, sombra del canto)
STONE = {"base": (126, 128, 136), "hi": (158, 160, 168), "lo": (86, 88, 96), "in_lo": (92, 94, 102), "in_hi": (162, 164, 172)}
GOLD = {"base": (196, 152, 70), "hi": (236, 198, 112), "lo": (128, 90, 34), "in_lo": (120, 84, 32), "in_hi": (240, 206, 124)}


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def frame(seed, m):
    """La piedra (o el oro) del marco: un grano muy suave y el canto con luz arriba/izquierda y sombra abajo/derecha."""
    rnd = random.Random(seed)
    px = {}
    for y in range(N):
        for x in range(N):
            v = rnd.choice((-4, -2, 0, 0, 0, 2, 3))
            px[x, y] = tuple(max(0, min(255, c + v)) for c in m["base"])
    for i in range(N):
        px[i, 0] = px[0, i] = m["hi"]
        px[i, N - 1] = px[N - 1, i] = m["lo"]
    return px


def face(kind, label, color, seed):
    """Una cara: el marco y, menos abajo, el panel del color (hundido: sombra arriba/izquierda, luz abajo/derecha)."""
    m = GOLD if label in PAID else STONE
    px = frame(seed, m)
    lit = {}
    if kind != "bottom":
        for y in range(3, N - 3):
            for x in range(3, N - 3):
                t = (y - 3) / (N - 7)
                lit[x, y] = mix(mix(color, (255, 255, 255), 0.25), mix(color, (0, 0, 0), 0.18), t)
        for x in range(4, N - 4):
            lit[x, 3] = mix(color, (255, 255, 255), 0.5)  # la luz de arriba del panel
        for i in range(2, N - 2):
            px[i, 2] = px[2, i] = m["in_lo"]
            px[i, N - 3] = px[N - 3, i] = m["in_hi"]
    px.update(lit)
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
        _, gf = face("front", label, dict(TIERS)[label], 1000)
        _, gs = face("side", label, dict(TIERS)[label], 1017)
        _, gt = face("top", label, dict(TIERS)[label], 1034)
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
