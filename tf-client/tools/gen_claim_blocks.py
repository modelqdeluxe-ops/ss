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

# las letras TF (6 de ancho cada una, 2 de hueco, 10 de alto)
GLYPH_T = ["######", "######", "..##..", "..##..", "..##..", "..##..", "..##..", "..##..", "..##..", "..##.."]
GLYPH_F = ["######", "######", "##....", "##....", "#####.", "#####.", "##....", "##....", "##....", "##...."]


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


# 1.3.113 (el dueño: «no me gusta el diseño de las piedras, sus contornos y eso; dale rework a todas y no pongas en
# todas las caras el TF»): fuera el marco biselado de 2 px, el panel hundido y los remaches. Ahora es un bloque de piedra
# oscura de verdad (grano en dos escalas y alguna grieta, como la pizarra profunda pulida), con un canto de 1 px apenas
# marcado, y por dentro **vetas de cristal** del color del nivel que brillan de noche (como la obsidiana llorosa). Las
# letras TF, grabadas y del color, **solo en la cara de delante**; los lados, solo piedra y vetas; arriba, las vetas
# salen de un cristal en bruto en el centro; abajo, solo piedra. Las de pago (250, 300 y 500), con pepitas de oro en la
# piedra (como la piedra negra dorada) y las vetas más gruesas.
STONE = (38, 40, 50)
STONE_PAID = (40, 36, 40)


def noise2(rnd, cell):
    """Ruido de valores suave (celdas de `cell` px, interpolado y que da la vuelta): para el grano grande."""
    n = N // cell
    g = [[rnd.uniform(-1, 1) for _ in range(n)] for _ in range(n)]
    out = {}
    for y in range(N):
        for x in range(N):
            fx, fy = x / cell, y / cell
            x0, y0 = int(fx) % n, int(fy) % n
            x1, y1 = (x0 + 1) % n, (y0 + 1) % n
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = g[y0][x0] + (g[y0][x1] - g[y0][x0]) * tx
            b = g[y1][x0] + (g[y1][x1] - g[y1][x0]) * tx
            out[x, y] = a + (b - a) * ty
    return out


def stone(seed, paid):
    """La piedra: grano grande y fino, alguna grieta oscura y el canto de 1 px (luz arriba-izquierda, sombra abajo-derecha,
    muy suave)."""
    rnd = random.Random(seed)
    base = STONE_PAID if paid else STONE
    big, mid = noise2(rnd, 8), noise2(rnd, 4)
    px = {}
    for y in range(N):
        for x in range(N):
            v = big[x, y] * 7 + mid[x, y] * 4 + rnd.choice((-2, -1, 0, 0, 1, 2))
            px[x, y] = tuple(max(0, min(255, int(c + v))) for c in base)
    # grietas: cortas, de 1 px, un poco más oscuras
    for _ in range(3):
        x, y = rnd.randrange(N), rnd.randrange(N)
        dx = rnd.choice((-1, 1))
        for _ in range(rnd.randint(4, 8)):
            if 0 <= x < N and 0 <= y < N:
                px[x, y] = mix(px[x, y], (6, 6, 9), 0.55)
            y += 1
            x += dx if rnd.random() < 0.5 else 0
    if paid:  # pepitas de oro (2-3 px, con su brillo)
        for _ in range(7):
            x, y = rnd.randrange(2, N - 3), rnd.randrange(2, N - 3)
            px[x, y] = (214, 170, 74)
            px[x + 1, y] = (150, 112, 44)
            if rnd.random() < 0.6:
                px[x, y + 1] = (120, 88, 34)
            px[x - 1, y - 1] = mix(px[x - 1, y - 1], (246, 220, 140), 0.35)
    for i in range(N):
        px[i, 0] = mix(px[i, 0], (255, 255, 255), 0.10)
        px[0, i] = mix(px[0, i], (255, 255, 255), 0.07)
        px[i, N - 1] = mix(px[i, N - 1], (0, 0, 0), 0.40)
        px[N - 1, i] = mix(px[N - 1, i], (0, 0, 0), 0.30)
    return px


def veins(rnd, starts, steps, avoid=frozenset(), branches=1):
    """Vetas que serpentean (el rumbo gira poco a poco, como una grieta de verdad) desde (x, y, ángulo en grados); alguna
    se abre en dos. Sin salirse del bloque ni pisar lo de `avoid`."""
    import math
    cells, seen = [], set()
    todo = [(float(x), float(y), math.radians(a), steps) for x, y, a in starts]
    while todo:
        x, y, ang, left = todo.pop(0)
        turn = 0.0
        for k in range(left):
            c = (int(round(x)), int(round(y)))
            if not (1 <= c[0] < N - 1 and 1 <= c[1] < N - 1) or c in avoid:
                break
            if c not in seen:
                seen.add(c)
                cells.append(c)
            turn = max(-0.5, min(0.5, turn + rnd.uniform(-0.22, 0.22)))
            ang += turn * 0.5
            x += math.cos(ang)
            y += math.sin(ang)
            if branches > 0 and k > 3 and rnd.random() < 0.09:
                branches -= 1
                todo.append((x, y, ang + rnd.choice((-1, 1)) * rnd.uniform(0.6, 1.0), max(3, (left - k) // 2)))
    return cells


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
    """Una cara (lit: lo que va también en la capa _glow, a plena luz)."""
    paid = label in PAID
    rnd = random.Random(seed * 7 + len(label))
    px = stone(seed, paid)
    lit = {}
    dim, core, hot = mix(color, (0, 0, 0), 0.45), mix(color, (255, 255, 255), 0.12), mix(color, (255, 255, 255), 0.55)
    avoid = set()
    if kind == "front":
        cells = letters()
        avoid = {(x + dx, y + dy) for x, y in cells for dx in (-2, -1, 0, 1, 2) for dy in (-2, -1, 0, 1, 2)}
        # dos vetas: una baja desde debajo de la T y otra sube desde encima de la F
        vs = veins(rnd, [(10, 24, 100), (21, 7, -75)], 12, avoid=avoid)
    elif kind == "side":
        vs = veins(rnd, [(rnd.randint(7, 12), 1, 80), (rnd.randint(20, 25), N - 2, -95)], 22, branches=2)
    elif kind == "top":
        c0 = N // 2
        vs = veins(rnd, [(c0 + 3, c0, 10), (c0 - 4, c0 - 1, 200), (c0, c0 + 3, 95), (c0 - 1, c0 - 4, -80)], 12, branches=2)
    else:
        vs = []
    for i, (x, y) in enumerate(vs):
        lit[x, y] = hot if i % 6 == 0 else core
        if paid and (x + 1, y) not in vs and x + 1 < N - 1:
            lit.setdefault((x + 1, y), dim)
        # el halo oscuro de la veta en la piedra (sin brillo)
        for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c = (x + ddx, y + ddy)
            if c not in lit and 0 < c[0] < N - 1 and 0 < c[1] < N - 1:
                px[c] = mix(px[c], color, 0.12)
    if kind == "top":
        # el cristal en bruto del centro
        c0 = N // 2
        shape = ["..##..", ".####.", "######", "######", ".####.", "..##.."]
        for r, row in enumerate(shape):
            for cidx, ch in enumerate(row):
                if ch == "#":
                    x, y = c0 - 3 + cidx, c0 - 3 + r
                    lit[x, y] = hot if (r < 2 or cidx < 2) else core if r < 4 else dim
        for x, y in [(c0 - 4, c0 - 1), (c0 + 3, c0), (c0 - 1, c0 - 4), (c0, c0 + 3)]:
            px[x, y] = (6, 7, 10)
    if kind == "front":
        cells = letters()
        # grabadas: el surco (sombra abajo-derecha, 1 px) y la letra del color, con luz arriba y sombra abajo
        for x, y in cells:
            for ddx, ddy in ((1, 1), (1, 0), (0, 1)):
                c = (x + ddx, y + ddy)
                if c not in cells:
                    px[c] = (8, 8, 12)
                    lit.pop(c, None)
            for ddx, ddy in ((-1, 0), (0, -1), (-1, -1)):
                c = (x + ddx, y + ddy)
                if c not in cells:
                    px[c] = mix(px[c], (0, 0, 0), 0.35)
                    lit.pop(c, None)
        for x, y in cells:
            lit[x, y] = hot if (x, y - 1) not in cells else mix(color, (0, 0, 0), 0.22) if (x, y + 1) not in cells else core
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
