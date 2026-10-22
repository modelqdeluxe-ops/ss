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
    return s * down * exp(-r * 1.1) * smoothstep(0.0, 0.05, r) * (1.0 - smoothstep(0.40, 0.75, r));
}

// (1.3.106, el dueño: «líneas blancas en las texturas cuando se mueve la cámara, se notan muchísimo») suavizado de
// bordes (FXAA, la versión corta de Lottes): las aristas de un píxel y los bordes de los píxeles de las texturas, que al
// moverse la cámara saltaban de píxel en píxel como rayas, se funden con sus vecinos a lo largo de su dirección
vec3 fxaa(sampler2D tex, vec2 p, vec2 px) {
    vec3 nw = texture(tex, p + vec2(-1.0, -1.0) * px).rgb;
    vec3 ne = texture(tex, p + vec2(1.0, -1.0) * px).rgb;
    vec3 sw = texture(tex, p + vec2(-1.0, 1.0) * px).rgb;
    vec3 se = texture(tex, p + vec2(1.0, 1.0) * px).rgb;
    vec3 m = texture(tex, p).rgb;
    vec3 k = vec3(0.299, 0.587, 0.114);
    float lnw = dot(nw, k), lne = dot(ne, k), lsw = dot(sw, k), lse = dot(se, k), lm = dot(m, k);
    float lmin = min(lm, min(min(lnw, lne), min(lsw, lse)));
    float lmax = max(lm, max(max(lnw, lne), max(lsw, lse)));
    vec2 dir = vec2(-((lnw + lne) - (lsw + lse)), (lnw + lsw) - (lne + lse));
    float reduce = max((lnw + lne + lsw + lse) * (0.25 / 8.0), 1.0 / 128.0);
    float rcpMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + reduce);
    dir = clamp(dir * rcpMin, vec2(-8.0), vec2(8.0)) * px;
    vec3 a = 0.5 * (texture(tex, p + dir * (1.0 / 3.0 - 0.5)).rgb + texture(tex, p + dir * (2.0 / 3.0 - 0.5)).rgb);
    vec3 b = a * 0.5 + 0.25 * (texture(tex, p - dir * 0.5).rgb + texture(tex, p + dir * 0.5).rgb);
    float lb = dot(b, k);
    return (lb < lmin || lb > lmax) ? a : b;
}

float linearDepth(float d) {
    float z = d * 2.0 - 1.0;
    return 2.0 * Clip.x * Clip.y / (Clip.y + Clip.x - z * (Clip.y - Clip.x));
}

void main() {
    vec3 c = fxaa(SceneSampler, uv, 1.0 / vec2(textureSize(SceneSampler, 0)));
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
    // (y lo que aclara, también: dibujaba un borde blanco en cada arista de las texturas; el dueño, «líneas blancas,
    // texturas no bien definidas»)
    dc /= 1.0 + abs(dl) * 8.0;
    c = max(c + dc * 0.35, 0.0);
    c = mix(c, blurred, coc);
    vec3 b = texture(BloomSampler, uv).rgb;
    vec3 rays = vec3(0.0);
    if (SunVis > 0.001) {
        vec2 stp = (SunPos - uv) / 32.0;
        // (1.3.107, el dueño: «tus rayas aún se ven»: cada cosa que brillaba —una luciérnaga, un hada, la espuma— dejaba
        // una ristra de rayitas hacia el sol, una por paso, que se movían con la cámara) el punto de partida, distinto en
        // cada píxel (las rayitas se funden en un velo suave) y solo hace rayos lo claro del hueco del sol
        vec2 p = uv + stp * fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
        vec2 asp = vec2(float(textureSize(SceneSampler, 0).x) / float(textureSize(SceneSampler, 0).y), 1.0);
        float decay = 1.0;
        for (int i = 0; i < 32; i++) {
            p += stp;
            // solo lo muy claro hace rayos, y se apagan pronto (haces que salen del sol, no un velo por toda la imagen)
            vec3 s = texture(BloomSampler, clamp(p, 0.001, 0.999)).rgb;
            float nearSun = 1.0 - smoothstep(0.10, 0.24, length((p - SunPos) * asp));
            rays += max(s - 0.45, 0.0) * decay * nearSun;
            decay *= 0.92;
        }
        rays *= SunVis / 32.0;
    }
    vec2 ts = vec2(textureSize(SceneSampler, 0));
    float aspect = ts.x / ts.y;
    vec2 sd = (uv - SunPos) * vec2(aspect, 1.0);
    // el foco del sol en el hueco (crema cálido, como en la imagen) y sus rayos
    vec3 shafts = vec3(1.0, 0.90, 0.64) * (godRays(uv, SunPos, aspect) * 1.35 + exp(-dot(sd, sd) * 30.0) * 0.30) * SunVis;
    // (1.3.105, el dueño: «el resplandor del sol tapa un poco los items») los rayos y el foco del sol se ponen aquí
    // encima de todo: solo en lo que queda detrás de la crate (la ruleta, el premio y la crate están a su distancia), y
    // el florecer del haz, algo menos a esa distancia (lo de delante, el agua, como antes)
    float behind = smoothstep(Focus.x + 1.0, Focus.x + 4.0, dist);
    float atCrate = smoothstep(Focus.x - 3.0, Focus.x - 1.5, dist) * (1.0 - behind);
    vec3 col = c + b * BloomStrength * (1.0 - 0.4 * atCrate) + (rays * vec3(1.0, 0.92, 0.70) + shafts) * RayStrength * behind;
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
    col = mix(col, col * col * (3.0 - 2.0 * col), 0.85);
    // los brillos más fuertes tiran a blanco (como los del agua de la imagen)
    float hmax = max(col.r, max(col.g, col.b));
    col = mix(col, vec3(hmax), smoothstep(0.78, 0.97, dot(col, vec3(0.299, 0.587, 0.114))) * 0.25);
    vec2 q = uv - 0.5;
    col *= 1.0 - dot(q, q) * 0.35;      // (poca: en la imagen del dueño las esquinas también brillan)
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
