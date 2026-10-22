"""Texturas de las partículas propias de las crates (1.3.109, CrateFxParticle), premultiplicadas: el alfa es el halo
(lo que tapa de detrás) y el color, el halo más un centro que suma luz por encima. Se tiñen con el color de la rareza
y se pintan con (ONE, ONE_MINUS_SRC_ALPHA): de día se ve su color sobre el cielo (sumando luz, salían bolas blancas)
y de noche brillan.

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


def save(name, alpha_fn, core_fn):
    im = Image.new("RGBA", (N, N))
    px = im.load()
    for y in range(N):
        for x in range(N):
            u = (x + 0.5) / N * 2 - 1
            v = (y + 0.5) / N * 2 - 1
            a = max(0.0, min(1.0, alpha_fn(u, v)))
            c = max(0.0, min(1.0, a + core_fn(u, v)))
            g = int(round(c * 255))
            px[x, y] = (g, g, g, int(round(a * 255)))
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, name))


def glow(u, v):
    r = math.hypot(u, v)
    if r >= 1:
        return 0.0
    return max(0.0, math.exp(-r * r * 5.5) * 0.62 - 0.01)


def glow_core(u, v):
    r = math.hypot(u, v)
    return math.exp(-r * r * 26.0) * 0.75


def spark(u, v):
    r = math.hypot(u, v)
    if r >= 1:
        return 0.0
    # cuatro rayos finos (en cruz) que se afilan, más un centro y un halo
    ax, ay = abs(u), abs(v)
    ray = max(math.exp(-(ay / (0.07 * (1 - ax) + 0.012)) ** 2) * (1 - ax) ** 1.6,
              math.exp(-(ax / (0.07 * (1 - ay) + 0.012)) ** 2) * (1 - ay) ** 1.6)
    halo = math.exp(-r * r * 9.0) * 0.22
    return (ray * 0.85 + halo) * (1 - r ** 6)


def spark_core(u, v):
    r = math.hypot(u, v)
    return math.exp(-r * r * 60.0)


if __name__ == "__main__":
    save("crate_glow.png", glow, glow_core)
    save("crate_spark.png", spark, spark_core)
    print("ok:", os.path.normpath(OUT))
