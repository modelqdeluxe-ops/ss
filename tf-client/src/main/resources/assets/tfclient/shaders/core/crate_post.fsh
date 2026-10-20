#version 150

// (1.3.99) La imagen final de la cinemática de las crates (el Bosque de las Hadas): la escena con su resplandor, un
// brillo soñador (mezcla «pantalla» del resplandor), los rayos de sol que salen del sol (desenfoque radial de lo que
// brilla hacia donde está), los colores del atardecer (sombras frías violeta, luces cálidas, algo más de saturación),
// una curva suave que levanta los medios y redondea las luces, y la viñeta.
in vec2 uv;

uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;
uniform vec2 SunPos;
uniform float SunVis;
uniform float BloomStrength;
uniform float RayStrength;
uniform float Exposure;

out vec4 fragColor;

void main() {
    vec3 c = texture(SceneSampler, uv).rgb;
    vec3 b = texture(BloomSampler, uv).rgb;
    vec3 rays = vec3(0.0);
    if (SunVis > 0.001) {
        vec2 stp = (SunPos - uv) / 32.0;
        vec2 p = uv;
        float decay = 1.0;
        for (int i = 0; i < 32; i++) {
            p += stp;
            rays += texture(BloomSampler, clamp(p, 0.001, 0.999)).rgb * decay;
            decay *= 0.95;
        }
        rays *= SunVis / 32.0;
    }
    vec3 col = c + b * BloomStrength + rays * RayStrength * vec3(1.0, 0.82, 0.58);
    col = 1.0 - (1.0 - clamp(col, 0.0, 4.0)) * (1.0 - b * 0.3);
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, col * vec3(0.84, 0.88, 1.14), (1.0 - smoothstep(0.0, 0.55, l)) * 0.35);
    col = mix(col, col * vec3(1.08, 1.0, 0.86), smoothstep(0.45, 1.0, l) * 0.3);
    col = mix(vec3(l), col, 1.12);
    col = 1.0 - exp(-max(col, 0.0) * Exposure);
    vec2 q = uv - 0.5;
    col *= 1.0 - dot(q, q) * 0.7;
    fragColor = vec4(col, 1.0);
}
