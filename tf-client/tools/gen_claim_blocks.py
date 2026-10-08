"""Genera las texturas de las piedras de protección de TF Claims (una por tamaño de zona).

El diseño es el de la imagen del dueño (piedra oscura, marco de neón con esquinas en escuadra, tornillos y las letras
TF), redibujado pixel a pixel en 32x32 (su arte era de 30x30 celdas: se le añade un píxel de piedra alrededor) y
simétrico. Las caras:
  - front: el marco con las letras TF (la cara que mira a quien la pone).
  - side:  el marco con el núcleo de la zona (un rombo de luz).
  - top:   el marco con el anillo del centro de la zona (aquí empieza la protección, que sube hasta el cielo).
  - bottom: el marco apagado, sin centro.
Cada cara tiene su capa de brillo (_glow: solo las líneas de luz), que el modelo dibuja a plena luz para que se vea de
noche. El color del neón es el del concreto que marcaba cada tamaño en Fantastic Claims (blanco, gris claro, cian,
azul claro, lima, amarillo, naranja, rosa, magenta y morado), en versión neón.

Uso: python3 tools/gen_claim_blocks.py [--preview DIR]
"""
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "tfclient")
TEX = os.path.join(ROOT, "textures", "block", "claims")
N = 32

# tamaño (como en ClaimTier) → color del neón
TIERS = [
    ("10x10", (236, 246, 255)),
    ("25x25", (172, 186, 202)),
    ("40x40", (0, 229, 255)),
    ("64x64", (66, 158, 255)),
    ("80x80", (112, 255, 64)),
    ("100x100", (255, 222, 48)),
    ("150x150", (255, 138, 32)),
    ("250x250", (255, 104, 192)),
    ("300x300", (236, 64, 250)),
    ("500x500", (152, 84, 255)),
]

# Piedra: la de fuera del marco (más clara) y la de dentro (más oscura y lisa), como en la imagen
STONE_OUT = [(26, 28, 36), (33, 35, 44), (40, 42, 52), (48, 50, 60), (56, 58, 68), (66, 68, 78)]
STONE_OUT_W = [10, 18, 22, 20, 16, 8]
STONE_IN = [(14, 18, 26), (20, 24, 32), (26, 29, 38), (32, 35, 44), (40, 42, 52)]
STONE_IN_W = [8, 20, 28, 26, 14]
GROOVE = (6, 12, 20)          # el canal oscuro en el que va la línea de luz
BOLT = [(88, 90, 102), (70, 72, 84), (52, 54, 64)]  # tornillo: luz, medio, sombra


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shades(c):
    """Tonos del neón: brillo (casi blanco), medio, apagado, rescoldo."""
    return {
        "#": mix(c, (255, 255, 255), 0.38),
        "+": c,
        "=": mix(c, (0, 0, 0), 0.28),
        "~": mix(c, (8, 14, 22), 0.6),
    }


def stone_layer(seed):
    rnd = random.Random(seed)
    px = {}
    for y in range(N):
        for x in range(N):
            inside = 6 <= x <= 25 and 6 <= y <= 25
            pal, w = (STONE_IN, STONE_IN_W) if inside else (STONE_OUT, STONE_OUT_W)
            px[x, y] = rnd.choices(pal, w)[0]
    return px


def frame_cells():
    """Las celdas de luz del marco (en 32x32) con su tono, y las del canal oscuro. Simétrico en las 4 esquinas."""
    glow, groove, bolt = {}, set(), {}
    lo, hi = 5, 26  # líneas del marco (en el 30x30 del dueño eran 4 y 25)

    def line(cells):
        n = len(cells)
        for i, c in enumerate(cells):
            glow[c] = "=" if i in (0, n - 1) else ("#" if i % 2 == 0 else "+")

    # Lados del marco (cortados en las esquinas, donde van los tornillos y la escuadra)
    line([(x, lo) for x in range(8, 24)])
    line([(x, hi) for x in range(8, 24)])
    line([(lo, y) for y in range(8, 24)])
    line([(hi, y) for y in range(8, 24)])
    for x in range(8, 24):
        groove.update({(x, lo - 1), (x, lo + 1), (x, hi - 1), (x, hi + 1)})
    for y in range(8, 24):
        groove.update({(lo - 1, y), (lo + 1, y), (hi - 1, y), (hi + 1, y)})

    # Esquina arriba-izquierda (se refleja en las otras tres)
    corner_glow = {
        (4, 4): "#", (5, 4): "#", (6, 4): "=",   # escuadra de fuera
        (4, 5): "#", (4, 6): "=",
        (7, 0): "~", (7, 1): "+", (7, 2): "#", (7, 3): "=",   # rayo hacia el borde de arriba
        (0, 7): "~", (1, 7): "+", (2, 7): "#", (3, 7): "=",   # y hacia el de la izquierda
    }
    corner_groove = {(3, 3), (4, 3), (5, 3), (6, 3), (3, 4), (3, 5), (3, 6), (8, 1), (8, 2), (6, 1), (6, 2),
                     (1, 8), (2, 8), (1, 6), (2, 6)}
    for (x, y), t in corner_glow.items():
        for mx, my in ((x, y), (N - 1 - x, y), (x, N - 1 - y), (N - 1 - x, N - 1 - y)):
            glow[mx, my] = t
    for (x, y) in corner_groove:
        for c in ((x, y), (N - 1 - x, y), (x, N - 1 - y), (N - 1 - x, N - 1 - y)):
            groove.add(c)
    # Tornillos 2x2 en las cuatro esquinas, siempre con la luz arriba a la izquierda
    for bx, by in ((5, 5), (N - 7, 5), (5, N - 7), (N - 7, N - 7)):
        for (dx, dy), t in {(0, 0): 0, (1, 0): 1, (0, 1): 1, (1, 1): 2}.items():
            bolt[bx + dx, by + dy] = t
    for c in glow:
        groove.discard(c)
    for c in bolt:
        groove.discard(c)
    return glow, groove, bolt


def letters():
    """T y F (en el 30x30 del dueño: T en x 8-13, F en x 16-21, filas 11-19); aquí +1."""
    cells = {}
    def rect(x0, y0, x1, y1):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                cells[x, y] = "#" if (x + y) % 2 == 0 else "+"
    rect(9, 12, 14, 13)    # T: barra
    rect(11, 14, 12, 20)   # T: palo
    rect(17, 12, 22, 13)   # F: barra de arriba
    rect(17, 14, 18, 20)   # F: palo
    rect(19, 16, 21, 17)   # F: barra del medio
    return cells


def core_diamond(r_out, filled_r, ring=True):
    cells = {}
    cx = cy = 15.5
    for y in range(N):
        for x in range(N):
            d = abs(x - cx) + abs(y - cy)
            if ring and abs(d - r_out) < 0.6:
                cells[x, y] = "+" if (x + y) % 2 else "#"
            elif d <= filled_r:
                cells[x, y] = "#" if d <= filled_r - 1.5 else "+"
    return cells


def outline(cells):
    out = set()
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            c = (x + dx, y + dy)
            if c not in cells and 0 <= c[0] < N and 0 <= c[1] < N:
                out.add(c)
    return out


def face(kind, color, seed):
    sh = shades(color)
    base = stone_layer(seed)
    glow, groove, bolt = frame_cells()
    centre = {}
    if kind == "front":
        centre = letters()
    elif kind == "side":
        centre = core_diamond(5, 2)
    elif kind == "top":
        centre = core_diamond(7, 3, ring=True)
        centre.update({c: "=" for c in core_diamond(4, -1, ring=True)})
    dim = kind == "bottom"
    for c in outline(centre):
        base[c] = GROOVE
    for c in groove:
        base[c] = GROOVE
    for c, t in bolt.items():
        base[c] = BOLT[t]
    glow_px = {}
    for c, t in list(glow.items()) + list(centre.items()):
        col = sh[t]
        if dim:
            col = mix(col, (10, 14, 22), 0.45)
        base[c] = col
        glow_px[c] = col
    img = Image.new("RGBA", (N, N))
    for (x, y), col in base.items():
        img.putpixel((x, y), col + (255,))
    over = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    if not dim:
        for (x, y), col in glow_px.items():
            over.putpixel((x, y), col + (255,))
    return img, over


FACES = ("front", "side", "top", "bottom")


def build():
    os.makedirs(TEX, exist_ok=True)
    out = {}
    for label, color in TIERS:
        name = f"proteccion_{label}"
        for i, kind in enumerate(FACES):
            img, over = face(kind, color, seed=1000 + i * 17)
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
