"""Texturas de las partículas propias de las crates (CrateFxParticle): blancas (se tiñen con el color de la rareza) y
con alfa normal, como las partículas translúcidas del juego (1.3.112: premultiplicadas salían como cuadrados claros con
un punto). El juego descarta el alfa menor de 0,1: el borde de cada una llega justo ahí (sin aro).

- crate_glow.png: un orbe suave con el centro más denso.
- crate_spark.png: una estrella de cuatro puntas, fina, con el centro brillante.

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
            # por debajo de 0,1 el juego lo descarta: lo que no llega, fuera del todo (sin aro de píxeles sueltos)
            px[x, y] = (255, 255, 255, int(round(a * 255)) if a >= 0.1 else 0)
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, name))


def glow(u, v):
    r = math.hypot(u, v)
    if r >= 0.98:
        return 0.0
    # centro denso y caída suave que llega a 0,1 en el borde
    return 0.1 + 0.9 * (1 - r / 0.98) ** 2.2


def spark(u, v):
    r = math.hypot(u, v)
    if r >= 0.98:
        return 0.0
    ax, ay = abs(u), abs(v)
    ray = max(math.exp(-(ay / (0.09 * (1 - ax) + 0.02)) ** 2) * (1 - ax) ** 1.3,
              math.exp(-(ax / (0.09 * (1 - ay) + 0.02)) ** 2) * (1 - ay) ** 1.3)
    core = math.exp(-r * r * 30.0)
    a = max(ray, core)
    return a if a >= 0.1 else 0.0


if __name__ == "__main__":
    save("crate_glow.png", glow)
    save("crate_spark.png", spark)
    print("ok:", os.path.normpath(OUT))
