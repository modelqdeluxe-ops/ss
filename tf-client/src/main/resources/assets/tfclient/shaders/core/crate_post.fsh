#version 150

// (1.3.99) La imagen final de la cinemática de las crates (el Santuario de las Hadas, como la imagen del dueño), como un
// paquete de shaders: un poco de profundidad de campo (lo de muy cerca y lo de muy lejos, suave; el fondo se sigue
// viendo), el resplandor de lo que brilla (el agua turquesa, las cascadas, el haz, los faroles, las amatistas), los
// rayos que salen del hueco del sol, colores vivos (sombras verde azuladas, luces cálidas, más saturación, algo de
// contraste), una curva suave que redondea las luces y la viñeta.
in vec2 uv;

uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;
uniform sampler2D BlurSampler;
uniform sampler2D DepthSampler;
uniform vec2 SunPos;
uniform float SunVis;
uniform float BloomStrength;
uniform float RayStrength;
uniform float Exposure;
uniform vec2 Clip;
uniform vec2 Focus;

out vec4 fragColor;

float linearDepth(float d) {
    float z = d * 2.0 - 1.0;
    return 2.0 * Clip.x * Clip.y / (Clip.y + Clip.x - z * (Clip.y - Clip.x));
}

void main() {
    vec3 c = texture(SceneSampler, uv).rgb;
    // profundidad de campo: lo que está lejos del foco (la crate) se desenfoca; lo de detrás, más
    float dist = linearDepth(texture(DepthSampler, uv).r);
    float off = dist - Focus.x;
    float coc = off > 0.0 ? smoothstep(0.0, Focus.y, off - 2.0) * 0.45 : smoothstep(0.0, Focus.y * 0.5, -off - 1.0) * 0.6;
    vec3 blurred = texture(BlurSampler, uv).rgb;
    c = mix(c, blurred, coc);
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
    vec3 col = c + b * BloomStrength + rays * RayStrength * vec3(1.0, 0.92, 0.70);
    col = 1.0 - exp(-max(col, 0.0) * Exposure);
    // colores: sombras verde azuladas, luces cálidas, más saturación y algo de contraste
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, col * vec3(0.82, 1.02, 1.04), (1.0 - smoothstep(0.0, 0.45, l)) * 0.5);
    col = mix(col, col * vec3(1.08, 1.0, 0.88), smoothstep(0.5, 1.0, l) * 0.2 * (1.0 - smoothstep(0.0, 0.3, col.b - col.r)));
    l = dot(col, vec3(0.299, 0.587, 0.114));
    col = max(mix(vec3(l), col, 1.4), 0.0);
    col = mix(col, col * col * (3.0 - 2.0 * col), 0.45);
    vec2 q = uv - 0.5;
    col *= 1.0 - dot(q, q) * 1.0;
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
