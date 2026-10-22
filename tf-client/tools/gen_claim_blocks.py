"""Genera las texturas de las piedras de protección de TF Claims (una por tamaño de zona).

1.3.67 (el dueño: «la textura de las claims es una mierda exagerada otra vez: hazlas simplistas, sin números ni figuras
geométricas» y luego «tampoco las hagas tan mierda, ponle TF siempre, con un contorno bonito»): un bloque sencillo de
32x32. Un marco de piedra pulida (de oro en las de pago: 250, 300 y 500) y dentro un panel hundido del color del nivel,
con su luz arriba, y en el centro las letras TF gruesas, blancas, con su contorno oscuro del mismo color. Igual en los
cuatro lados y arriba; abajo, solo la piedra. De noche se ven el panel y las letras (capa _glow a plena luz). Los 10
colores (verde → turquesa → celeste → azul → violeta → magenta → coral → rojo → naranja → oro) son los del borde de la
zona (ClaimTier) y del nombre del objeto (ClaimBlocks.colorForTier).

1.3.109 (el dueño: «dale rework a la textura de las claims, me parecen horrendas; pon texturas buenas, oscuras»): piedra
negra pulida (de basalto pulido, con el canto biselado y cuatro remaches; las de pago, con el marco de oro viejo), un
panel hundido casi negro con un filo de luz del color del nivel y las letras TF grabadas, que brillan de ese color (no
blancas). De noche se ven el filo y las letras (capa _glow a plena luz, recortada: solo píxeles enteros).

Uso: python3 tools/gen_claim_blocks.py [--preview DIR]
"""
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "tfclient")
TEX = os.path.join(ROOT, "textures", "block", "claims")
N = 32

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

# el marco: piedra negra pulida o, en las de pago, oro viejo oscuro (base, canto de luz y de sombra, el borde del
# hueco y los remaches)
STONE = {"base": (36, 38, 48), "hi": (66, 70, 86), "lo": (14, 15, 20), "in_lo": (10, 11, 15), "in_hi": (54, 58, 72),
         "stud": (104, 110, 128), "stud_hi": (150, 156, 176)}
GOLD = {"base": (78, 60, 28), "hi": (150, 114, 52), "lo": (34, 24, 10), "in_lo": (22, 16, 6), "in_hi": (120, 92, 42),
        "stud": (190, 150, 72), "stud_hi": (238, 204, 120)}
PANEL = (16, 17, 23)

# las letras TF (6 de ancho cada una, 2 de hueco, 10 de alto)
GLYPH_T = ["######", "######", "..##..", "..##..", "..##..", "..##..", "..##..", "..##..", "..##..", "..##.."]
GLYPH_F = ["######", "######", "##....", "##....", "#####.", "#####.", "##....", "##....", "##....", "##...."]


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def frame(seed, m):
    """La piedra (o el oro) del marco: un grano muy suave (vetas oscuras) y el canto (2 px) con luz arriba/izquierda y
    sombra abajo/derecha."""
    rnd = random.Random(seed)
    px = {}
    for y in range(N):
        for x in range(N):
            v = rnd.choice((-3, -2, -1, 0, 0, 0, 1, 2))
            if rnd.random() < 0.04:
                v -= 6  # alguna veta
            px[x, y] = tuple(max(0, min(255, c + v)) for c in m["base"])
    for i in range(N):
        for j in range(2):
            px[i, j] = px[j, i] = m["hi"] if j == 0 else mix(m["hi"], m["base"], 0.5)
            px[i, N - 1 - j] = px[N - 1 - j, i] = m["lo"] if j == 0 else mix(m["lo"], m["base"], 0.5)
    # los remaches de las esquinas (2x2 con su brillo)
    for cx, cy in ((2, 2), (N - 4, 2), (2, N - 4), (N - 4, N - 4)):
        px[cx, cy] = m["stud_hi"]
        px[cx + 1, cy] = px[cx, cy + 1] = m["stud"]
        px[cx + 1, cy + 1] = mix(m["stud"], m["lo"], 0.5)
    return px


def letters():
    """Las celdas de TF, centradas."""
    w = len(GLYPH_T[0]) + 2 + len(GLYPH_F[0])
    ox, oy = (N - w) // 2, (N - len(GLYPH_T)) // 2
    cells = set()
    for r in range(len(GLYPH_T)):
        for c, ch in enumerate(GLYPH_T[r]):
            if ch == "#":
                cells.add((ox + c, oy + r))
        for c, ch in enumerate(GLYPH_F[r]):
            if ch == "#":
                cells.add((ox + len(GLYPH_T[0]) + 2 + c, oy + r))
    return cells


def face(kind, label, color, seed):
    """Una cara: el marco y, menos abajo, el panel hundido casi negro con su filo de luz y las letras TF grabadas, que
    brillan del color del nivel (lit: lo que va también en la capa _glow, a plena luz)."""
    m = GOLD if label in PAID else STONE
    px = frame(seed, m)
    lit = {}
    if kind != "bottom":
        p0, p1 = 6, N - 6
        # el hueco del panel: sombra arriba/izquierda, luz abajo/derecha (2 px)
        for i in range(4, N - 4):
            for j in range(2):
                px[i, 4 + j] = px[4 + j, i] = m["in_lo"]
                px[i, N - 5 - j] = px[N - 5 - j, i] = m["in_hi"] if j == 0 else mix(m["in_hi"], PANEL, 0.5)
        # el panel, casi negro, un pelo más claro abajo
        for y in range(p0, p1):
            for x in range(p0, p1):
                t = (y - p0) / (p1 - p0 - 1)
                px[x, y] = mix(PANEL, mix(PANEL, color, 0.10), t)
        # el filo de luz del color, por dentro del hueco (1 px), con las esquinas apagadas
        rim = mix(color, (0, 0, 0), 0.35)
        for i in range(p0 + 1, p1 - 1):
            lit[i, p0] = lit[i, p1 - 1] = lit[p0, i] = lit[p1 - 1, i] = rim
        # las letras grabadas: su sombra (oscura, sin brillo) y la letra del color, con luz arriba y sombra abajo
        cells = letters()
        outline = {(x + dx, y + dy) for x, y in cells for dx in (-1, 0, 1) for dy in (-1, 0, 1)} - cells
        for c in outline:
            if c in lit:
                del lit[c]
            px[c] = (6, 7, 10)
        face_c = mix(color, (255, 255, 255), 0.18)
        top_c = mix(color, (255, 255, 255), 0.6)
        low_c = mix(color, (0, 0, 0), 0.28)
        for x, y in cells:
            lit[x, y] = top_c if (x, y - 1) not in cells else low_c if (x, y + 1) not in cells else face_c
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
