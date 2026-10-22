#version 150

// (1.3.113, el dueño: «siguen las rayas blancas en la escena, antes de abrirla; con capturas no las aprecias») las
// rendijas: entre dos caras que se tocan (una columna y la cascada, dos bloques de la pared) la rasterización deja a
// veces una línea de 1-2 muestras sin pintar, y por ella se ve el cielo de fondo, claro (crema, (237,237,211) en la
// captura de la prueba, justo el horizonte de crate_sky), y al mover la cámara salta de sitio: rayas blancas que
// parpadean. Aquí, antes del resplandor y de bajar la imagen: una muestra que está mucho más lejos que lo que tiene a
// los dos lados (a 1-3 muestras, en horizontal o en vertical) es una rendija y toma el color de esos dos lados.
in vec2 uv;

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform vec2 InTexel;
uniform vec2 Clip;

out vec4 fragColor;

float lin(vec2 p) {
    float z = texture(DepthSampler, p).r * 2.0 - 1.0;
    return 2.0 * Clip.x * Clip.y / (Clip.y + Clip.x - z * (Clip.y - Clip.x));
}

void main() {
    vec3 c = texture(InSampler, uv).rgb;
    // (solo donde se ve el cielo: lo demás, tal cual y sin coste)
    if (texture(DepthSampler, uv).r < 0.99999) {
        fragColor = vec4(c, 1.0);
        return;
    }
    float d = lin(uv);
    vec3 acc = vec3(0.0);
    float n = 0.0;
    for (int axis = 0; axis < 2; axis++) {
        vec2 dir = axis == 0 ? vec2(InTexel.x, 0.0) : vec2(0.0, InTexel.y);
        vec2 pa = uv, pb = uv;
        bool fa = false, fb = false;
        for (int i = 1; i <= 3; i++) {
            if (fa) break;
            vec2 p = uv - dir * float(i);
            float dp = lin(p);
            if (dp < d - max(1.0, d * 0.15)) { fa = true; pa = p; }
        }
        for (int i = 1; i <= 3; i++) {
            if (fb) break;
            vec2 p = uv + dir * float(i);
            float dp = lin(p);
            if (dp < d - max(1.0, d * 0.15)) { fb = true; pb = p; }
        }
        if (fa && fb) {
            acc += texture(InSampler, pa).rgb + texture(InSampler, pb).rgb;
            n += 2.0;
        }
    }
    fragColor = vec4(n > 0.0 ? acc / n : c, 1.0);
}
