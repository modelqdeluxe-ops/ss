#version 150

// El cielo de The Void (TF Client 1.3.88, más grande y sin grano en la 1.3.89): un agujero negro gigante con su disco de
// acreción, como el de los shaders del End (Iterationt, Celestial...) y el Gargantua de Interstellar, sobre las
// nebulosas moradas del vacío.
//
// Cada píxel es un rayo de luz que sale de la cámara y se curva con la gravedad (la ecuación de los fotones de
// Schwarzschild escrita como una fuerza: a = -1.5 h² r / |r|^5, en radios de Schwarzschild). Si cae dentro (r < 1),
// negro: la sombra. Cada vez que cruza el plano del disco, suma su luz (por eso la parte de atrás del disco se ve
// curvada por encima y por debajo de la sombra, y aparece el anillo de fotones). Si se escapa, el fondo en la
// dirección con la que sale (las estrellas de alrededor se ven desplazadas: la lente gravitatoria).
// El disco gira como Kepler (más rápido dentro), es más caliente dentro (blanco → dorado → naranja → violeta) y el
// lado que viene hacia ti brilla más y más azul (Doppler), el que se va, más rojo y apagado.
//
// 1.3.89 (el dueño: «hazlo más grande e imponente» y, con captura, «los píxeles están como jodidos»): la cámara está más
// cerca (la sombra mide ~18° de radio, antes 12°) y nada del disco ni de las estrellas es más fino que un píxel: cada
// octava del ruido se apaga cuando su tamaño en pantalla (lo que mide un píxel a la distancia que ha recorrido el rayo)
// baja de ~2 píxeles, y las estrellas se dibujan como manchas de al menos un píxel con la misma luz total. Antes el ruido
// fino del disco (hebras de 0,07 radios) y la capa de estrellas más densa eran más pequeños que un píxel: grano y
// parpadeo.

uniform sampler2D Sampler0;   // panorama de nebulosas (equirectangular, el de rift_sky.py)
uniform float VoidTime;       // segundos
uniform vec3 HoleDir;         // hacia dónde está el agujero (mundo)
uniform vec3 DiskNormal;      // normal del disco (mundo)
uniform float Quality;        // 0.35..1: menos o más pasos

in vec3 vDir;
out vec4 fragColor;

const float PI = 3.14159265;
const float DIST = 8.0;       // distancia de la cámara al agujero (1.3.88: 12)
const float RIN = 2.35;       // borde de dentro del disco (algo más allá de la órbita estable)
const float ROUT = 10.5;      // borde de fuera

// Lo que mide un píxel (en radianes de cielo); lo pone main()
float PIX = 0.001;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(hash13(i), hash13(i + vec3(1.0, 0.0, 0.0)), f.x);
    float b = mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), f.x);
    float c = mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), f.x);
    float d = mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), f.x);
    return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
}

// Ruido fractal filtrado: «foot» es lo que mide un píxel en las unidades de p. Cada octava se apaga (hacia su valor
// medio, 0,5) antes de ser más fina que ~2 píxeles: sin grano ni muaré a ninguna distancia.
float fbmf(vec3 p, float foot, int oct) {
    float s = 0.0;
    float a = 0.5;
    float fr = 1.0;
    for (int i = 0; i < 5; i++) {
        if (i >= oct) break;
        float k = 1.0 - smoothstep(0.22, 0.5, fr * foot);
        s += a * mix(0.5, noise3(p), k);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        fr *= 2.03;
        a *= 0.5;
    }
    return s / (1.0 - pow(0.5, float(oct)));
}

// Una capa de estrellas: una por celda de una rejilla 3D sobre la dirección (las que tocan), que titilan. Cada
// estrella es una mancha de al menos un píxel con la misma luz (si fuera más pequeña, aparecería y desaparecería al
// mover la cámara); si las celdas ya son más pequeñas que un par de píxeles, la capa se apaga.
vec3 stars(vec3 d, float scale, float density, float size) {
    float pc = PIX * scale;                     // un píxel, en celdas
    float fade = 1.0 - smoothstep(0.18, 0.35, pc);
    if (fade <= 0.0) return vec3(0.0);
    vec3 q = d * scale;
    vec3 i = floor(q);
    vec3 f = fract(q);
    float h = hash13(i);
    if (h > density) return vec3(0.0);
    vec3 c = vec3(hash13(i + 11.0), hash13(i + 23.0), hash13(i + 37.0)) * 0.6 + 0.2;
    float dd = length(f - c);
    float rad = max(size * 0.45, pc * 0.9);
    float b = exp(-dd * dd / (rad * rad)) * min(1.0, (size * 0.45) * (size * 0.45) / (rad * rad));
    float tw = 0.6 + 0.4 * sin(VoidTime * (0.8 + 3.0 * hash13(i + 5.0)) + h * 60.0);
    float t = hash13(i + 71.0);
    vec3 col = t < 0.55 ? vec3(1.0, 0.97, 1.0) : (t < 0.8 ? vec3(0.82, 0.78, 1.0) : vec3(1.0, 0.78, 0.92));
    return col * b * tw * (0.6 + 1.6 * hash13(i + 3.0)) * fade;
}

// El cielo sin agujero: nebulosas que giran muy despacio, estrellas y el abismo oscuro por debajo
vec3 background(vec3 d) {
    float a = VoidTime * 0.0015;
    float ca = cos(a);
    float sa = sin(a);
    vec3 r = vec3(ca * d.x - sa * d.z, d.y, sa * d.x + ca * d.z);
    float u = fract(atan(r.z, r.x) / (2.0 * PI));
    float v = 0.5 - asin(clamp(r.y, -1.0, 1.0)) / PI;
    // (textureLod: sin mipmaps, y así el salto de u en la costura no cambia nada)
    vec3 neb = textureLod(Sampler0, vec2(u, v), 0.0).rgb;
    neb = pow(neb, vec3(1.45)) * 0.95;
    vec3 col = neb;
    col += stars(r, 110.0, 0.06, 0.30) * 1.7;
    col += stars(r.zxy, 260.0, 0.08, 0.34) * 1.0;
    col += stars(r.yzx, 520.0, 0.10, 0.40) * 0.6;
    // el abismo: más oscuro mirando hacia abajo
    col *= mix(0.32, 1.0, smoothstep(-0.85, -0.05, d.y));
    return col;
}

vec3 ramp(float x) {
    x = clamp(x, 0.0, 3.0);
    vec3 c0 = vec3(0.30, 0.05, 0.42);   // violeta (lo de fuera, frío)
    vec3 c1 = vec3(0.95, 0.22, 0.32);   // rosa rojizo
    vec3 c2 = vec3(1.00, 0.52, 0.16);   // naranja
    vec3 c3 = vec3(1.00, 0.82, 0.48);   // dorado
    vec3 c4 = vec3(1.00, 0.97, 0.92);   // blanco caliente
    vec3 c5 = vec3(0.82, 0.88, 1.00);   // blanco azulado (lo que viene hacia ti)
    if (x < 0.18) return mix(c0, c1, x / 0.18);
    if (x < 0.40) return mix(c1, c2, (x - 0.18) / 0.22);
    if (x < 0.75) return mix(c2, c3, (x - 0.40) / 0.35);
    if (x < 1.25) return mix(c3, c4, (x - 0.75) / 0.50);
    return mix(c4, c5, clamp((x - 1.25) / 1.2, 0.0, 1.0));
}

// La luz y lo que tapa el disco en el punto q (en el plano del disco); «foot» = lo que mide un píxel ahí (en radios)
vec4 disk(vec3 q, float r, vec3 v, vec3 n, vec3 e1, vec3 e2, float foot) {
    float ang = atan(dot(q, e2), dot(q, e1));
    float omega = 2.0 / (r * sqrt(r));
    float rot = ang - omega * VoidTime * 0.8;
    float cr = cos(rot);
    float sr = sin(rot);
    // grumos grandes que giran y arcos estirados a lo largo de la órbita (anillos de distinta densidad); el ruido
    // de los arcos va en (radio, ángulo) con el ángulo comprimido: hebras largas, nunca más finas que 2 píxeles
    float n1 = fbmf(vec3(cr * r, sr * r, r * 0.4) * 0.75, foot * 0.75, 4);
    float n2 = fbmf(vec3(r * 2.6, cr * 1.6, sr * 1.6) + 7.0, foot * 2.6, 4);
    float lanes = 0.5 + 0.5 * sin(r * 5.2 + 3.0 * n1);
    lanes = mix(0.5, lanes, 1.0 - smoothstep(0.12, 0.3, foot * 5.2 / 6.2832));
    float arcs = smoothstep(0.28, 0.82, n2);
    float dens = (0.2 + 1.2 * n1 * n1) * (0.4 + 0.9 * arcs) * (0.78 + 0.44 * lanes);
    dens *= smoothstep(RIN * 0.92, RIN * 1.3, r) * (1.0 - smoothstep(ROUT * 0.45, ROUT, r));
    // (el borde de fuera llega casi hasta la cámara: lo que pasaría a su lado se difumina, sin una lámina rara de lado)
    dens *= smoothstep(-0.9 * DIST, -0.5 * DIST, dot(q, normalize(HoleDir)));
    float temp = pow(RIN / r, 1.5);
    // Doppler (la luz que llega a la cámara va por -v) y el corrimiento al rojo de la gravedad
    vec3 tang = normalize(cross(n, q));
    float beta = clamp(sqrt(0.5 / max(r - 1.0, 0.4)), 0.0, 0.7);
    float cosT = dot(tang, -normalize(v));
    float g = sqrt(1.0 - beta * beta) / (1.0 - beta * cosT);
    g *= sqrt(max(1.0 - 1.0 / r, 0.05));
    float I = dens * (0.35 + 1.9 * temp) * pow(g, 3.0) * 1.9;
    vec3 c = ramp(temp * g * 1.15);
    float a = clamp(dens * (0.45 + 0.6 * temp), 0.0, 0.92);
    return vec4(c * I, a);
}

void main() {
    vec3 dir = normalize(vDir);
    // lo que mide un píxel (antes de cualquier bucle: las derivadas solo valen fuera de él)
    PIX = max(length(fwidth(dir)), 1e-5);
    vec3 f = normalize(HoleDir);
    vec3 n = normalize(DiskNormal);
    vec3 e1 = normalize(cross(n, f));
    vec3 e2 = cross(n, e1);

    vec3 p = -f * DIST;
    vec3 v = dir;
    vec3 hc = cross(p, v);
    float h2 = dot(hc, hc);

    vec3 col = vec3(0.0);
    float alpha = 0.0;
    float minR = 1e9;
    float travel = 0.0;
    bool captured = false;
    float qk = clamp(Quality, 0.0, 1.0);
    int steps = int(mix(90.0, 220.0, qk));
    float stepK = mix(0.13, 0.065, qk);
    for (int i = 0; i < 220; i++) {
        if (i >= steps || alpha > 0.985) break;
        float r = length(p);
        minR = min(minR, r);
        float dt = clamp(stepK * r, 0.02, 1.5);
        float r2 = r * r;
        vec3 acc = -1.5 * h2 * p / (r2 * r2 * r);
        v += acc * dt;
        vec3 pn = p + v * dt;
        float s0 = dot(p, n);
        float s1 = dot(pn, n);
        if (s0 * s1 < 0.0) {
            float k = s0 / (s0 - s1);
            vec3 q = mix(p, pn, k);
            float rq = length(q);
            if (rq > RIN * 0.9 && rq < ROUT) {
                // un píxel a esa distancia, y más grande cuanto más de canto se ve el disco
                float slant = max(abs(dot(normalize(v), n)), 0.08);
                float foot = PIX * (travel + length(v) * dt * k) / slant;
                vec4 dc = disk(q, rq, v, n, e1, e2, foot);
                col += (1.0 - alpha) * dc.rgb;
                alpha += (1.0 - alpha) * dc.a;
            }
        }
        travel += length(v) * dt;
        p = pn;
        float rn = length(p);
        if (rn < 1.0) {
            captured = true;
            break;
        }
        if (rn > DIST * 2.6 && dot(p, v) > 0.0) break;
    }

    vec3 bg = captured ? vec3(0.0) : background(normalize(v));
    // Junto al agujero la nebulosa (que la lente estira en remolinos) se apaga: el disco y la sombra mandan
    bg *= mix(0.3, 1.0, smoothstep(2.2, 9.0, minR));
    // El anillo de fotones (fino y brillante justo fuera de la sombra, nunca más fino que un píxel) y un halo violeta
    // que se abre, con un poco de naranja cerca
    if (!captured) {
        float ringW = max(0.11, PIX * DIST * 1.5);
        float ring = exp(-pow((minR - 1.55) / ringW, 2.0));
        bg += vec3(1.0, 0.86, 0.7) * ring * 0.95;
        bg += vec3(0.55, 0.22, 0.95) * exp(-max(minR - 1.5, 0.0) * 0.42) * 0.26;
        bg += vec3(1.0, 0.55, 0.3) * exp(-max(minR - 1.5, 0.0) * 1.1) * 0.14;
    }
    vec3 outc = col + (1.0 - alpha) * bg;
    // Tono: que lo muy brillante no se queme en blanco plano
    outc = vec3(1.0) - exp(-outc * 1.35);
    // Tramado suave (sin escalones en el degradado oscuro)
    outc += (hash13(vec3(gl_FragCoord.xy, 3.0)) - 0.5) / 255.0;
    fragColor = vec4(outc, 1.0);
}
