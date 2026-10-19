#version 150

// El Corazón del Alba (SanctuaryFx, 1.3.96): lo que queda del Alba, sobre el templo del santuario. Late (dos golpes,
// como un corazón, cada 1,6 s) y a cada latido suelta un anillo de luz; rayos que giran despacio. Mode 1: la columna
// de luz que sube de él al cielo (para encontrar el santuario desde lejos). Mezcla aditiva.
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
