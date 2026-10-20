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
uniform float Time;

out vec4 fragColor;

float hash1(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

float noise1(float x) {
    float i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(hash1(i), hash1(i + 1.0), f);
}

// los rayos de sol que bajan del hueco (como en la imagen del dueño): un abanico de haces finos y suaves que salen del
// hueco de las copas (arriba en el centro) y bajan hasta la escalera de agua, hechos aquí, píxel a píxel (con bandas de
// geometría salían «tablones» de borde duro en el juego)
float godRays(vec2 p, vec2 sun, float aspect) {
    vec2 d = p - sun;
    d.x *= aspect;
    float r = length(d);
    float ang = atan(d.x, -d.y);
    float s = pow(noise1(ang * 9.0 + 3.1 + Time * 0.006), 2.0) * 0.6
            + pow(noise1(ang * 21.0 - 7.7 - Time * 0.009), 2.5) * 0.4;
    float down = 1.0 - smoothstep(0.25, 0.60, abs(ang));
    // (en la imagen del dueño llegan hasta la escalera de agua, no hasta el altar)
    return s * down * exp(-r * 1.45) * smoothstep(0.0, 0.05, r) * (1.0 - smoothstep(0.32, 0.62, r));
}

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
    // (lo que oscurece, comprimido cuando es mucho: alrededor de lo que brilla suave —el halo de un farol, un hada— dibujaba
    // un aro oscuro y salían «discos» en el juego; el detalle fino de las texturas, diferencias pequeñas, sigue igual)
    vec3 dc = c - blurred;
    float dl = dot(dc, vec3(0.299, 0.587, 0.114));
    dc /= 1.0 + max(-dl, 0.0) * 6.0;
    c = max(c + dc * 0.40, 0.0);
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
            rays += max(s - 0.45, 0.0) * decay;
            decay *= 0.92;
        }
        rays *= SunVis / 32.0;
    }
    vec2 ts = vec2(textureSize(SceneSampler, 0));
    float aspect = ts.x / ts.y;
    vec2 sd = (uv - SunPos) * vec2(aspect, 1.0);
    // el foco del sol en el hueco (crema cálido, como en la imagen) y sus rayos
    vec3 shafts = vec3(1.0, 0.90, 0.64) * (godRays(uv, SunPos, aspect) * 0.8 + exp(-dot(sd, sd) * 30.0) * 0.45) * SunVis;
    vec3 col = c + b * BloomStrength + (rays * vec3(1.0, 0.92, 0.70) + shafts) * RayStrength;
    // la curva de tono, normalizada: lo que llega a 1 sale casi blanco (los brillos del agua, los faroles y el sol, como
    // en la imagen del dueño; sin normalizar nada pasaba de 0,79)
    col = (1.0 - exp(-max(col, 0.0) * Exposure)) / (1.0 - exp(-Exposure));
    // colores: sombras verde azuladas (la imagen: nada amarillento en lo oscuro), luces cálidas, más saturación
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, col * vec3(0.84, 1.0, 1.16) + vec3(0.0, 0.006, 0.012), (1.0 - smoothstep(0.0, 0.5, l)) * 0.7);
    // (solo lo amarillo y naranja: ni el turquesa del agua ni el rosa de las flores)
    col = mix(col, col * vec3(1.08, 1.0, 0.88), smoothstep(0.5, 1.0, l) * 0.2 * smoothstep(0.0, 0.15, min(col.r, col.g) - col.b));
    l = dot(col, vec3(0.299, 0.587, 0.114));
    // (medido en el juego: con 1,3 salía un 15 % más saturado que la imagen del dueño)
    col = max(mix(vec3(l), col, 1.12 + 0.15 * smoothstep(0.4, 0.8, l)), 0.0);
    col = mix(col, col * col * (3.0 - 2.0 * col), 0.45);
    vec2 q = uv - 0.5;
    col *= 1.0 - dot(q, q) * 0.35;      // (poca: en la imagen del dueño las esquinas también brillan)
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
