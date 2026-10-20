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
    // (la imagen del dueño está nítida entera: el fondo no se desenfoca; solo lo que pasa pegado a la cámara)
    float coc = off > 0.0 ? 0.0 : smoothstep(0.0, Focus.y * 0.5, -off - 2.5) * 0.5;
    vec3 blurred = texture(BlurSampler, uv).rgb;
    // contraste local («claridad»): se separa la imagen de su versión desenfocada (la imagen del dueño tiene un 30 % más
    // de contraste local y un 40 % más de nitidez que sin esto)
    c = max(c + (c - blurred) * 0.42, 0.0);
    c = mix(c, blurred, coc);
    vec3 b = texture(BloomSampler, uv).rgb;
    vec3 rays = vec3(0.0);
    if (SunVis > 0.001) {
        vec2 stp = (SunPos - uv) / 32.0;
        vec2 p = uv;
        float decay = 1.0;
        for (int i = 0; i < 32; i++) {
            p += stp;
            // solo lo muy claro hace rayos, y se apagan pronto (haces que salen del sol, no un velo por toda la imagen)
            vec3 s = texture(BloomSampler, clamp(p, 0.001, 0.999)).rgb;
            rays += max(s - 0.25, 0.0) * decay;
            decay *= 0.92;
        }
        rays *= SunVis / 32.0;
    }
    vec3 col = c + b * BloomStrength + rays * RayStrength * vec3(1.0, 0.92, 0.70);
    // la curva de tono, normalizada: lo que llega a 1 sale casi blanco (los brillos del agua, los faroles y el sol, como
    // en la imagen del dueño; sin normalizar nada pasaba de 0,79)
    col = (1.0 - exp(-max(col, 0.0) * Exposure)) / (1.0 - exp(-Exposure));
    // colores: sombras verde azuladas (la imagen: nada amarillento en lo oscuro), luces cálidas, más saturación
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, col * vec3(0.84, 1.0, 1.16) + vec3(0.0, 0.006, 0.012), (1.0 - smoothstep(0.0, 0.5, l)) * 0.7);
    // (solo lo amarillo y naranja: ni el turquesa del agua ni el rosa de las flores)
    col = mix(col, col * vec3(1.08, 1.0, 0.88), smoothstep(0.5, 1.0, l) * 0.2 * smoothstep(0.0, 0.15, min(col.r, col.g) - col.b));
    l = dot(col, vec3(0.299, 0.587, 0.114));
    col = max(mix(vec3(l), col, 1.3 + 0.2 * smoothstep(0.4, 0.8, l)), 0.0);
    col = mix(col, col * col * (3.0 - 2.0 * col), 0.45);
    vec2 q = uv - 0.5;
    col *= 1.0 - dot(q, q) * 0.35;      // (poca: en la imagen del dueño las esquinas también brillan)
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
