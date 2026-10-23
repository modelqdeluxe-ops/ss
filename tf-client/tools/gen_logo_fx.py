"""Genera las texturas del efecto del emblema TF en el menú (como en la web):

- logo_glow.png: halo difuminado dorado (arriba) y azul zafiro (abajo) que late detrás del emblema.
- logo_shine.png: hoja de fotogramas (8x5) del destello diagonal que cruza el emblema, recortado a su silueta.
- sparkle.png: chispa de cuatro puntas que brilla en la gema de la corona.
Uso: python3 tools/gen_logo_fx.py
"""
import math
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

GUI = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "tfclient", "textures", "gui")
logo = Image.open(os.path.join(GUI, "logo.png")).convert("RGBA")
W, H = logo.size
alpha = logo.split()[3]

# --- Halo: silueta difuminada, más grande que el emblema (margen del 25 %) ---
pad = 0.25
gw, gh = int(W * (1 + 2 * pad)) // 2, int(H * (1 + 2 * pad)) // 2
sil = Image.new("L", (int(W * (1 + 2 * pad)), int(H * (1 + 2 * pad))), 0)
sil.paste(alpha, (int(W * pad), int(H * pad)))
sil = sil.resize((gw, gh), Image.LANCZOS).filter(ImageFilter.GaussianBlur(28))
color = Image.new("RGB", (gw, gh))
for y in range(gh):
    t = y / (gh - 1)
    top, bottom = (255, 196, 70), (70, 130, 255)
    c = tuple(int(top[i] + (bottom[i] - top[i]) * max(0, min(1, (t - 0.3) / 0.5))) for i in range(3))
    for x in range(gw):
        color.putpixel((x, y), c)
glow = color.convert("RGBA")
glow.putalpha(sil.point(lambda v: min(255, int(v * 1.35))))
glow.save(os.path.join(GUI, "logo_glow.png"), optimize=True)
print("logo_glow.png", glow.size, "margen", pad)

# --- Destello: banda diagonal (110 grados, como en la web) recortada a la silueta ---
COLS, ROWS = 8, 5
FRAMES = COLS * ROWS
fw, fh = W // 2, H // 2
mask = alpha.resize((fw, fh), Image.LANCZOS)
sheet = Image.new("RGBA", (fw * COLS, fh * ROWS), (0, 0, 0, 0))
angle = math.radians(20)  # inclinación de la banda respecto a la vertical
for f in range(FRAMES):
    p = f / (FRAMES - 1)
    center = -0.35 + p * 1.7  # recorre de fuera a fuera
    band = Image.new("L", (fw, fh), 0)
    px = band.load()
    for y in range(fh):
        for x in range(fw):
            u = (x / fw) + (y / fh - 0.5) * math.tan(angle) * (fh / fw)
            d = u - center
            # núcleo brillante y estela suave, como el degradado de la web
            v = math.exp(-(d / 0.045) ** 2) * 0.85 + math.exp(-((d + 0.06) / 0.09) ** 2) * 0.35
            px[x, y] = int(min(1.0, v) * 255)
    frame = Image.new("RGBA", (fw, fh), (255, 246, 222, 0))
    frame.putalpha(ImageChops.multiply(band, mask))  # banda recortada a la silueta del emblema
    sheet.paste(frame, ((f % COLS) * fw, (f // COLS) * fh))
sheet.save(os.path.join(GUI, "logo_shine.png"), optimize=True)
print("logo_shine.png", sheet.size, "fotogramas", FRAMES, "de", (fw, fh))

# --- Chispa de cuatro puntas ---
S = 256
spark = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(spark)
c = S / 2
for length, width, col in [(S * 0.48, S * 0.07, (255, 255, 255, 255)), (S * 0.3, S * 0.05, (255, 255, 255, 255))]:
    d.polygon([(c, c - length), (c + width, c), (c, c + length), (c - width, c)], fill=col)
    d.polygon([(c - length, c), (c, c - width), (c + length, c), (c, c + width)], fill=col)
halo = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(halo).ellipse([c - S * 0.16, c - S * 0.16, c + S * 0.16, c + S * 0.16], fill=(170, 210, 255, 200))
spark = Image.alpha_composite(halo.filter(ImageFilter.GaussianBlur(S * 0.06)), spark.filter(ImageFilter.GaussianBlur(1.2)))
spark = spark.resize((128, 128), Image.LANCZOS)
spark.save(os.path.join(GUI, "sparkle.png"), optimize=True)
print("sparkle.png", spark.size)
