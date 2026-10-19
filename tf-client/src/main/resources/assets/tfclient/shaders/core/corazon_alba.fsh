#version 150

// El Corazón del Alba (SanctuaryFx, 1.3.96): lo que queda del Alba, sobre el templo del santuario. Late (dos golpes,
// como un corazón, cada 1,6 s) y a cada latido suelta un anillo de luz; rayos que giran despacio. Mode 1: la columna
// de luz que sube de él al cielo (para encontrar el santuario desde lejos; y, más fina, la de cada Pozo de Luz).
// Mode 2: el círculo de luz de un Pozo, en el suelo (donde se cura la corrosión): borde que brilla, runas que giran y
// el centro más claro. Mode 3: el velo del Portal del Alba (remolino dorado y chispas). Mezcla aditiva.
uniform float STime;
uniform float Mode;
uniform float Alpha;

in vec2 uv;

out vec4 fragColor;

void main() {
    vec3 col;
    float fade;
    float ph = mod(STime, 1.6);
    float beat = exp(-pow(ph / 0.09, 2.0)) + 0.6 * exp(-pow((ph - 0.28) / 0.09, 2.0));
    if (Mode < 0.5) {
        float r = length(uv);
        float a = atan(uv.y, uv.x);
        float core = exp(-r * r * 70.0) * (1.5 + 0.7 * beat);
        float halo = exp(-r * 5.5) * (0.42 + 0.3 * beat);
        float rays = pow(0.5 + 0.5 * cos(a * 6.0 + STime * 0.35), 18.0) * exp(-r * 2.8) * 0.65
                + pow(0.5 + 0.5 * cos(a * 10.0 - STime * 0.5 + 1.3), 30.0) * exp(-r * 3.6) * 0.4;
        float k = ph / 1.6;
        float ring = exp(-pow((r - 0.18 - 0.6 * k) * 16.0, 2.0)) * (1.0 - k) * (1.0 - k) * 0.5;
        col = vec3(1.0, 0.84, 0.52) * (halo + rays + ring) + vec3(1.0, 0.97, 0.9) * core;
        fade = 1.0 - smoothstep(0.75, 1.0, r);
    } else if (Mode > 2.5) {
        // el velo del portal: uv.x de lado a lado, uv.y de abajo a arriba (el hueco es 5 × 8)
        vec2 p = uv * vec2(1.0, 1.6);
        float r = length(p);
        float a = atan(p.y, p.x);
        float swirl = 0.5 + 0.5 * sin(a * 4.0 + r * 7.0 - STime * 2.6);
        float swirl2 = 0.5 + 0.5 * sin(a * 7.0 - r * 11.0 + STime * 1.7);
        float spark = pow(fract(sin(dot(floor(uv * vec2(14.0, 22.0) + vec2(0.0, -STime * 3.0)), vec2(12.9898, 78.233))) * 43758.5453), 30.0);
        float edge = max(abs(uv.x), abs(uv.y));
        float rim = exp(-pow((edge - 0.93) * 18.0, 2.0));
        float body = 1.0 - smoothstep(0.9, 1.0, edge);
        col = vec3(1.0, 0.82, 0.48) * (0.28 + 0.38 * swirl + 0.2 * swirl2) + vec3(1.0, 0.96, 0.88) * (exp(-r * r * 2.5) * 0.5 + spark * 1.2 + rim * 0.6);
        fade = body;
    } else if (Mode > 1.5) {
        // el círculo de luz del pozo (uv: el suelo, de -1 a 1 en el radio del círculo)
        float r = length(uv);
        float a = atan(uv.y, uv.x);
        float fill = (1.0 - smoothstep(0.7, 1.0, r)) * (0.14 + 0.05 * sin(STime * 1.8));
        float ring = exp(-pow((r - 0.93) * 20.0, 2.0)) * (0.75 + 0.25 * beat);
        float runes = step(0.55, fract(a * 16.0 / 6.2831 + STime * 0.06)) * exp(-pow((r - 0.76) * 28.0, 2.0)) * 0.45;
        float inner = exp(-r * r * 7.0) * 0.3;
        col = vec3(1.0, 0.84, 0.5) * (fill + ring + runes) + vec3(1.0, 0.97, 0.88) * inner;
        fade = 1.0 - smoothstep(0.97, 1.0, r);
    } else {
        // la columna: uv.x de lado a lado, uv.y de abajo (el corazón) a arriba
        float y = uv.y * 0.5 + 0.5;
        float x = uv.x;
        float body = exp(-x * x * 9.0) * 0.22 + exp(-x * x * 60.0) * 0.3;
        float flow = 0.5 + 0.5 * sin(y * 60.0 - STime * 2.4 + 2.0 * sin(y * 7.0 + STime * 0.4));
        flow = pow(flow, 4.0) * exp(-x * x * 14.0) * 0.18;
        col = vec3(1.0, 0.86, 0.58) * (body * (0.85 + 0.15 * beat) + flow);
        fade = smoothstep(0.0, 0.04, y) * pow(1.0 - y, 1.6) * (1.0 - smoothstep(0.7, 1.0, abs(x)));
    }
    float lum = max(col.r, max(col.g, col.b));
    fragColor = vec4(col / max(lum, 1e-4), clamp(lum, 0.0, 1.0) * fade * Alpha);
}
