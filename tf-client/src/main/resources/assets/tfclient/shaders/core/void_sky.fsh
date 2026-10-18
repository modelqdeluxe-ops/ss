#version 150

// El cielo de The Void (TF Client 1.3.88): un agujero negro gigante con su disco de acreción, como el de los shaders
// del End (Iterationt, Celestial...) y el Gargantua de Interstellar, sobre las nebulosas moradas del vacío.
//
// Cada píxel es un rayo de luz que sale de la cámara y se curva con la gravedad (la ecuación de los fotones de
// Schwarzschild escrita como una fuerza: a = -1.5 h² r / |r|^5, en radios de Schwarzschild). Si cae dentro (r < 1),
// negro: la sombra. Cada vez que cruza el plano del disco, suma su luz (por eso la parte de atrás del disco se ve
// curvada por encima y por debajo de la sombra, y aparece el anillo de fotones). Si se escapa, el fondo en la
// dirección con la que sale (las estrellas de alrededor se ven desplazadas: la lente gravitatoria).
// El disco gira como Kepler (más rápido dentro), es más caliente dentro (blanco → dorado → naranja → violeta) y el
// lado que viene hacia ti brilla más y más azul (Doppler), el que se va, más rojo y apagado.

uniform sampler2D Sampler0;   // panorama de nebulosas (equirectangular, el de rift_sky.py)
uniform float VoidTime;       // segundos
uniform vec3 HoleDir;         // hacia dónde está el agujero (mundo)
uniform vec3 DiskNormal;      // normal del disco (mundo)
uniform float Quality;        // 0.4..1: menos o más pasos

in vec3 vDir;
out vec4 fragColor;

const float PI = 3.14159265;
const float DIST = 12.0;      // distancia de la cámara al agujero
const float RIN = 2.6;        // borde de dentro del disco (algo más allá de la órbita estable)
const float ROUT = 12.5;      // borde de fuera

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

float fbm(vec3 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * noise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

// Una capa de estrellas: una por celda de una rejilla 3D sobre la dirección (las que tocan), que titilan
vec3 stars(vec3 d, float scale, float density, float size) {
    vec3 q = d * scale;
    vec3 i = floor(q);
    vec3 f = fract(q);
    float h = hash13(i);
    if (h > density) return vec3(0.0);
    vec3 c = vec3(hash13(i + 11.0), hash13(i + 23.0), hash13(i + 37.0)) * 0.6 + 0.2;
    float dd = length(f - c);
    float tw = 0.6 + 0.4 * sin(VoidTime * (0.8 + 3.0 * hash13(i + 5.0)) + h * 60.0);
    float b = smoothstep(size, 0.0, dd);
    b = b * b;
    float t = hash13(i + 71.0);
    vec3 col = t < 0.55 ? vec3(1.0, 0.97, 1.0) : (t < 0.8 ? vec3(0.82, 0.78, 1.0) : vec3(1.0, 0.78, 0.92));
    return col * b * tw * (0.6 + 1.6 * hash13(i + 3.0));
}

// El cielo sin agujero: nebulosas que giran muy despacio, estrellas y el abismo oscuro por debajo
vec3 background(vec3 d) {
    float a = VoidTime * 0.0015;
    float ca = cos(a);
    float sa = sin(a);
    vec3 r = vec3(ca * d.x - sa * d.z, d.y, sa * d.x + ca * d.z);
    float u = fract(atan(r.z, r.x) / (2.0 * PI));
    float v = 0.5 - asin(clamp(r.y, -1.0, 1.0)) / PI;
    vec3 neb = texture(Sampler0, vec2(u, v)).rgb;
    neb = pow(neb, vec3(1.45)) * 0.95;
    vec3 col = neb;
    col += stars(r, 120.0, 0.05, 0.30) * 1.6;
    col += stars(r.zxy, 300.0, 0.08, 0.36) * 1.0;
    col += stars(r.yzx, 700.0, 0.12, 0.42) * 0.55;
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

// La luz y lo que tapa el disco en el punto q (en el plano del disco)
vec4 disk(vec3 q, float r, vec3 v, vec3 n, vec3 e1, vec3 e2) {
    float ang = atan(dot(q, e2), dot(q, e1));
    float omega = 2.0 / (r * sqrt(r));
    float rot = ang - omega * VoidTime * 0.8;
    vec3 np = vec3(cos(rot) * r, sin(rot) * r, r * 0.4);
    // grumos grandes que giran, arcos (estirados a lo largo de la órbita) y hebras finas
    float n1 = fbm(np * 0.8);
    float n2 = fbm(vec3(r * 4.5, cos(rot) * 2.2, sin(rot) * 2.2) + 7.0);
    float n3 = noise3(vec3(r * 14.0, cos(rot) * 5.0, sin(rot) * 5.0) + 3.0);
    float arcs = smoothstep(0.25, 0.85, n2);
    float dens = (0.18 + 1.25 * n1 * n1) * (0.35 + 0.95 * arcs) * (0.75 + 0.5 * n3);
    dens *= smoothstep(RIN * 0.92, RIN * 1.25, r) * (1.0 - smoothstep(ROUT * 0.5, ROUT, r));
    float temp = pow(RIN / r, 1.5);
    // Doppler (la luz que llega a la cámara va por -v) y el corrimiento al rojo de la gravedad
    vec3 tang = normalize(cross(n, q));
    float beta = clamp(sqrt(0.5 / max(r - 1.0, 0.4)), 0.0, 0.7);
    float cosT = dot(tang, -normalize(v));
    float g = sqrt(1.0 - beta * beta) / (1.0 - beta * cosT);
    g *= sqrt(max(1.0 - 1.0 / r, 0.05));
    float I = dens * (0.35 + 1.9 * temp) * pow(g, 3.0) * 1.6;
    vec3 c = ramp(temp * g * 1.15);
    float a = clamp(dens * (0.45 + 0.6 * temp), 0.0, 0.92);
    return vec4(c * I, a);
}

void main() {
    vec3 dir = normalize(vDir);
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
    bool captured = false;
    int steps = int(mix(80.0, 200.0, clamp(Quality, 0.0, 1.0)));
    float stepK = mix(0.14, 0.075, clamp(Quality, 0.0, 1.0));
    for (int i = 0; i < 200; i++) {
        if (i >= steps || alpha > 0.985) break;
        float r = length(p);
        minR = min(minR, r);
        float dt = clamp(stepK * r, 0.02, 1.8);
        float r2 = r * r;
        vec3 acc = -1.5 * h2 * p / (r2 * r2 * r);
        v += acc * dt;
        vec3 pn = p + v * dt;
        float s0 = dot(p, n);
        float s1 = dot(pn, n);
        if (s0 * s1 < 0.0) {
            vec3 q = mix(p, pn, s0 / (s0 - s1));
            float rq = length(q);
            if (rq > RIN * 0.9 && rq < ROUT) {
                vec4 dc = disk(q, rq, v, n, e1, e2);
                col += (1.0 - alpha) * dc.rgb;
                alpha += (1.0 - alpha) * dc.a;
            }
        }
        p = pn;
        float rn = length(p);
        if (rn < 1.0) {
            captured = true;
            break;
        }
        if (rn > DIST * 2.4 && dot(p, v) > 0.0) break;
    }

    vec3 bg = captured ? vec3(0.0) : background(normalize(v));
    // El anillo de fotones (fino y brillante justo fuera de la sombra) y un halo violeta que se abre
    if (!captured) {
        float ring = exp(-pow((minR - 1.55) * 9.0, 2.0));
        bg += vec3(1.0, 0.86, 0.7) * ring * 0.9;
        bg += vec3(0.55, 0.22, 0.95) * exp(-max(minR - 1.5, 0.0) * 0.45) * 0.22;
        bg += vec3(1.0, 0.55, 0.3) * exp(-max(minR - 1.5, 0.0) * 1.2) * 0.12;
    }
    vec3 outc = col + (1.0 - alpha) * bg;
    // Tono: que lo muy brillante no se queme en blanco plano
    outc = vec3(1.0) - exp(-outc * 1.35);
    // Tramado suave (sin escalones en el degradado oscuro)
    outc += (hash13(vec3(gl_FragCoord.xy, 3.0)) - 0.5) / 255.0;
    fragColor = vec4(outc, 1.0);
}
