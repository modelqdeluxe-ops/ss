"""Genera las texturas de las piedras de protección de TF Claims (una por tamaño de zona).

1.3.67 (el dueño: «la textura de las claims es una mierda exagerada otra vez: hazlas simplistas, sin números ni figuras
geométricas» y luego «tampoco las hagas tan mierda, ponle TF siempre, con un contorno bonito»): un bloque sencillo de
32x32. Un marco de piedra pulida (de oro en las de pago: 250, 300 y 500) y dentro un panel hundido del color del nivel,
con su luz arriba, y en el centro las letras TF gruesas, blancas, con su contorno oscuro del mismo color. Igual en los
cuatro lados y arriba; abajo, solo la piedra. De noche se ven el panel y las letras (capa _glow a plena luz). Los 10
colores (verde → turquesa → celeste → azul → violeta → magenta → coral → rojo → naranja → oro) son los del borde de la
zona (ClaimTier) y del nombre del objeto (ClaimBlocks.colorForTier).

1.3.114: dibujadas a mano (ver el comentario de ramp()): marco tallado con cantoneras de metal, escudo de cristal (TF solo
delante) y gema tallada arriba.

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


# 1.3.114 (el dueño: «la textura de las piedras son una mierda, no le diste empeño»): dibujadas a mano, píxel a píxel,
# como las de un buen pack de texturas. Cada cara es un bloque tallado: un marco de piedra de 4 px con su bisel (luz
# arriba-izquierda, sombra abajo-derecha, y al revés en el canto que baja al panel hundido) y cuatro cantoneras de metal
# con su remache (cobre hasta la de 40, hierro hasta la de 150 y oro en las de pago: 250, 300 y 500). En el panel:
#   - delante: un escudo de cristal del color del nivel (con su arista central, luz y destello) con las letras TF en
#     relieve de metal: lo único que lleva TF;
#   - los lados: el mismo escudo de cristal, sin letras;
#   - arriba: una gema tallada en su engaste con cuatro garras y cuatro canales de luz hacia las esquinas;
#   - abajo: solo la piedra.
# De noche brilla el cristal (capa _glow a plena luz) con un halo tenue alrededor; el metal y las letras, no: el TF se
# lee en negro sobre el escudo encendido. La de 500 lleva además gemas engastadas en el marco.
# 1.3.115 (el dueño: «me gusta el escudo; lo que no me gusta es tanto punto y rayas»): piedra lisa (sin grano, desconchones
# ni grietas), cantoneras sin remache, el cristal sin rayas de reflejo ni estrella y la tapa sin canales.

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


def ramp(color):
    """Los 5 tonos del cristal: de casi negro a casi blanco, con el color del nivel en el medio."""
    return (mix(color, (0, 0, 0), 0.62), mix(color, (0, 0, 0), 0.34), color, mix(color, (255, 255, 255), 0.32),
            mix(color, (255, 255, 255), 0.70))


METALS = {
    "cobre": ((74, 36, 26), (128, 64, 40), (178, 98, 58), (222, 140, 88), (250, 198, 152)),
    "hierro": ((50, 52, 60), (96, 100, 110), (150, 155, 165), (200, 204, 212), (240, 242, 246)),
    "oro": ((92, 56, 16), (156, 104, 28), (214, 160, 46), (244, 206, 86), (255, 244, 178)),
}
STONES = {
    "pizarra": ((18, 20, 26), (28, 31, 39), (39, 43, 53), (53, 58, 70), (74, 80, 95)),
    "basalto": ((20, 17, 21), (30, 26, 31), (42, 37, 43), (57, 50, 57), (80, 72, 78)),
}


def metal_of(label):
    size = int(label.split("x")[0])
    return METALS["oro"] if label in PAID else METALS["cobre"] if size <= 40 else METALS["hierro"]


def stone_of(label):
    return STONES["basalto"] if label in PAID else STONES["pizarra"]


# El escudo: medio ancho de cada fila (16 de ancho, 20 de alto, centrado en el panel)
SHIELD_HALF = [7, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 7, 7, 6, 5, 4, 3, 2, 1]
SHIELD_TOP = 6
CX = N // 2

# Las letras TF en relieve (6 + 1 + 5 de ancho, 8 de alto)
RELIEF_T = ["######", "######", "..##..", "..##..", "..##..", "..##..", "..##..", "..##.."]
RELIEF_F = ["#####", "#####", "##...", "####.", "####.", "##...", "##...", "##..."]


def shield_cells():
    cells = set()
    for r, h in enumerate(SHIELD_HALF):
        for x in range(CX - h, CX + h):
            cells.add((x, SHIELD_TOP + r))
    return cells


def relief_cells():
    ox, oy = CX - 6, SHIELD_TOP + 3
    cells = set()
    for r in range(len(RELIEF_T)):
        for c, ch in enumerate(RELIEF_T[r]):
            if ch == "#":
                cells.add((ox + c, oy + r))
        for c, ch in enumerate(RELIEF_F[r]):
            if ch == "#":
                cells.add((ox + 7 + c, oy + r))
    return cells


def gem_cells():
    """La gema de arriba: un octógono de 12 px centrado."""
    cells = set()
    for y in range(12):
        for x in range(12):
            cx, cy = x - 5.5, y - 5.5
            if abs(cx) + abs(cy) <= 8.0 and abs(cx) <= 5.5 and abs(cy) <= 5.5:
                cells.add((CX - 6 + x, CX - 6 + y))
    return cells


def edge(cells, c):
    x, y = c
    return any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def carved(seed, label):
    """La piedra tallada: marco de 4 px biselado y panel hundido, con grano fino. Devuelve los píxeles."""
    rnd = random.Random(seed)
    s0, s1, s2, s3, s4 = stone_of(label)
    big = noise2(rnd, 8)
    px = {}
    for y in range(N):
        for x in range(N):
            frame = x < 4 or y < 4 or x > N - 5 or y > N - 5
            # (1.3.115, el dueño: «no me gusta tanto punto y rayas») lisa: solo una ondulación muy suave, sin puntos
            v = big[x, y] * 2.5
            base = s3 if frame else s1
            if frame and (x in (1, 2) or y in (1, 2)) and not (x > N - 4 or y > N - 4):
                base = mix(s3, s4, 0.25)
            px[x, y] = tuple(max(0, min(255, int(c + v))) for c in base)
    for i in range(N):
        # el canto de fuera
        px[i, 0] = s4
        px[0, i] = mix(s4, s3, 0.3)
        px[i, N - 1] = s0
        px[N - 1, i] = s0
        px[i, N - 2] = mix(px[i, N - 2], s0, 0.45)
        px[N - 2, i] = mix(px[N - 2, i], s0, 0.35)
    for i in range(3, N - 3):
        # el canto que baja al panel: arriba e izquierda en sombra, abajo y derecha con luz
        px[i, 3] = s0
        px[3, i] = mix(s0, s1, 0.3)
        px[i, N - 4] = s4
        px[N - 4, i] = mix(s3, s4, 0.6)
        # la sombra que cae en el panel desde el marco de arriba e izquierda
        if 4 <= i <= N - 5:
            px[i, 4] = mix(px[i, 4], s0, 0.55)
            px[4, i] = mix(px[4, i], s0, 0.4)
    px[3, N - 4] = s1
    px[N - 4, 3] = s1
    return px


def corners(px, label):
    """Las cantoneras de metal (5x5) con su remache, y en las de 300 y 500 una gema en el centro de cada lado."""
    m0, m1, m2, m3, m4 = metal_of(label)
    for ox, oy in ((0, 0), (N - 5, 0), (0, N - 5), (N - 5, N - 5)):
        for y in range(5):
            for x in range(5):
                c = m2
                if y == 0 or x == 0:
                    c = m4 if (x + y) < 3 else m3
                elif y == 4 or x == 4:
                    c = m0 if (x == 4 and y == 4) else m1
                px[ox + x, oy + y] = c


def studs(px, lit, label, color):
    """Gemas pequeñas engastadas en el centro de cada lado del marco (solo 300 y 500)."""
    if label != "500x500":
        return
    m0, m1, m2, m3, m4 = metal_of(label)
    g0, g1, g2, g3, g4 = ramp(color)
    for ox, oy in ((CX - 2, 0), (CX - 2, N - 4), (0, CX - 2), (N - 4, CX - 2)):
        for y in range(4):
            for x in range(4):
                px[ox + x, oy + y] = m3 if (x == 0 or y == 0) else m1 if (x == 3 or y == 3) else m2
        gem = {(ox + 1, oy + 1): g4, (ox + 2, oy + 1): g2, (ox + 1, oy + 2): g2, (ox + 2, oy + 2): g1}
        for c, col in gem.items():
            px[c] = col
            lit[c] = col


def halo(px, lit, cells, color, label):
    """El brillo del cristal en la piedra de alrededor (1 px, tenue; de noche también, muy oscuro)."""
    s0, s1, s2, s3, s4 = stone_of(label)
    for x, y in list(cells):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c = (x + dx, y + dy)
            if c in cells or not (4 <= c[0] < N - 4 and 4 <= c[1] < N - 4) or c in lit:
                continue
            px[c] = mix(px[c], color, 0.26)
            lit[c] = mix(color, (0, 0, 0), 0.74)


def shield(px, lit, label, color, letters_on):
    m0, m1, m2, m3, m4 = metal_of(label)
    g0, g1, g2, g3, g4 = ramp(color)
    cells = shield_cells()
    halo(px, lit, cells, color, label)
    rows = len(SHIELD_HALF)
    for x, y in cells:
        r = y - SHIELD_TOP
        if edge(cells, (x, y)):
            # el canto de metal: luz arriba-izquierda, sombra abajo-derecha
            left = x < CX
            px[x, y] = m4 if r == 0 and left else m3 if (r == 0 or left) and r < rows - 6 else m1 if not left else m2
            if r >= rows - 6 and not left:
                px[x, y] = m0 if r > rows - 3 else m1
            continue
        t = r / rows
        if x < CX:  # la mitad de la luz
            c = g4 if (r <= 2 and x <= CX - 4) else g3 if t < 0.45 else g2 if t < 0.8 else g1
        else:  # la de la sombra
            c = g2 if t < 0.35 else g1 if t < 0.75 else g0
        # la arista central: una columna de luz en la izquierda y su sombra a la derecha
        if x == CX - 1:
            c = g4 if t < 0.55 else g3
        elif x == CX:
            c = g1 if t < 0.6 else g0
        px[x, y] = c
        lit[x, y] = c
    if letters_on:
        rel = relief_cells()
        for x, y in rel:
            # el contorno del relieve: la sombra abajo a la derecha (casi negra) y un filo oscuro arriba a la izquierda,
            # para que el TF se lea en cualquier color (también el oro sobre oro de la de 500)
            for c, col in (((x + 1, y + 1), (8, 8, 12)), ((x + 1, y), (8, 8, 12)), ((x, y + 1), (8, 8, 12)),
                           ((x - 1, y), g1), ((x, y - 1), g1), ((x - 1, y - 1), g1)):
                if c not in rel and c in cells and not edge(cells, c):
                    if col != g1 or px.get(c) not in ((8, 8, 12),):
                        px[c] = col
                    lit.pop(c, None)
        for x, y in rel:
            top = (x, y - 1) not in rel
            left = (x - 1, y) not in rel
            bottom = (x, y + 1) not in rel
            right = (x + 1, y) not in rel
            c = m4 if (top and left) else m3 if (top or left) else m1 if (bottom or right) else m2
            px[x, y] = c
            lit.pop((x, y), None)


def gem(px, lit, label, color):
    """La gema de arriba en su engaste, con 4 garras y 4 canales de luz hacia las esquinas."""
    m0, m1, m2, m3, m4 = metal_of(label)
    g0, g1, g2, g3, g4 = ramp(color)
    cells = gem_cells()
    halo(px, lit, cells, color, label)
    for x, y in cells:
        if edge(cells, (x, y)):
            left_top = (x - CX) + (y - CX) < 0
            px[x, y] = m3 if left_top else m1
            continue
        dx, dy = x - CX + 0.5, y - CX + 0.5
        # la mesa (centro), las facetas de la corona con luz arriba-izquierda y el pabellón en sombra
        if abs(dx) <= 2 and abs(dy) <= 2:
            c = g3 if dx + dy < 0 else g2
        elif dx + dy < -2:
            c = g4 if dx < -2 and dy < -2 else g3
        elif dx + dy > 2:
            c = g0 if dx > 2 and dy > 2 else g1
        else:
            c = g2
        # las aristas de las facetas
        if abs(abs(dx) - abs(dy)) < 0.6 and max(abs(dx), abs(dy)) > 2:
            c = mix(c, (255, 255, 255), 0.35) if dx + dy < 0 else mix(c, (0, 0, 0), 0.35)
        px[x, y] = c
        lit[x, y] = c
    # las garras del engaste (N, S, E, O), de metal y sin brillo
    for c, col in (((CX - 1, CX - 7), m4), ((CX, CX - 7), m3), ((CX - 1, CX + 6), m2), ((CX, CX + 6), m1),
                   ((CX - 7, CX - 1), m4), ((CX - 7, CX), m3), ((CX + 6, CX - 1), m2), ((CX + 6, CX), m1)):
        px[c] = col
        lit.pop(c, None)


def face(kind, label, color, seed):
    """Una cara (lit: lo que va también en la capa _glow, a plena luz)."""
    px = carved(seed * 3 + len(label), label)
    lit = {}
    if kind == "front":
        shield(px, lit, label, color, True)
    elif kind == "side":
        shield(px, lit, label, color, False)
    elif kind == "top":
        gem(px, lit, label, color)
    corners(px, label)
    studs(px, lit, label, color)
    for c in [(x, y) for x in range(5) for y in range(5)]:
        for ox, oy in ((0, 0), (N - 5, 0), (0, N - 5), (N - 5, N - 5)):
            lit.pop((ox + c[0], oy + c[1]), None)
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
