"""Texturas de las partículas propias de las crates (1.3.109, CrateFxParticle): blancas, con la forma en el alfa (se
tiñen con el color de la rareza y se pintan sumando luz).

- crate_glow.png: un orbe suave (gaussiana) con el centro más denso.
- crate_spark.png: una estrella de cuatro puntas, fina, con el centro brillante y un halo muy suave.

Uso: python3 -I tools/crates/particulas_crates.py
"""
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "tfclient",
                   "textures", "particle")
N = 32


def save(name, alpha_fn):
    im = Image.new("RGBA", (N, N))
    px = im.load()
    for y in range(N):
        for x in range(N):
            u = (x + 0.5) / N * 2 - 1
            v = (y + 0.5) / N * 2 - 1
            a = max(0.0, min(1.0, alpha_fn(u, v)))
            px[x, y] = (255, 255, 255, int(round(a * 255)))
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, name))


def glow(u, v):
    r = math.hypot(u, v)
    if r >= 1:
        return 0.0
    return math.exp(-r * r * 5.0) * 0.85 + math.exp(-r * r * 22.0) * 0.35 - 0.012


def spark(u, v):
    r = math.hypot(u, v)
    if r >= 1:
        return 0.0
    # cuatro rayos finos (en cruz) que se afilan, más un centro y un halo
    ax, ay = abs(u), abs(v)
    ray = max(math.exp(-(ay / (0.07 * (1 - ax) + 0.012)) ** 2) * (1 - ax) ** 1.6,
              math.exp(-(ax / (0.07 * (1 - ay) + 0.012)) ** 2) * (1 - ay) ** 1.6)
    core = math.exp(-r * r * 60.0)
    halo = math.exp(-r * r * 9.0) * 0.22
    return (ray * 0.95 + core + halo) * (1 - r ** 6)


if __name__ == "__main__":
    save("crate_glow.png", glow)
    save("crate_spark.png", spark)
    print("ok:", os.path.normpath(OUT))
