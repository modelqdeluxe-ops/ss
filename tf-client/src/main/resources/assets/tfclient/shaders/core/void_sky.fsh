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
//
// 1.3.90 (el dueño, con captura: «con shaders se ve pixelado cuando me muevo; anima más el agujero negro y el cielo,
// las nubes o manchas moradas»): el muaré era del giro del disco (ángulo − ω(r)·t: cada radio gira a su velocidad y el
// dibujo se enrollaba sin fin; con horas de juego, espirales más finas que un píxel). Ahora gira en conjunto y lo
// diferencial va en dos capas que se renuevan y se funden (diskNoise). Más vida: el disco gira el doble de rápido, con
// grumos calientes que orbitan y laten, chorros por el eje con nudos que suben, el anillo de fotones vibra y las
// nebulosas fluyen, laten y giran hacia el agujero. Todo lo que cambia con el tiempo da vueltas exactas en una hora
// (VoidTime vuelve a 0 cada 3600 s): sin saltos.
//
// 1.3.92 (el dueño: «la animación del agujero negro es demasiado rápida, debe ser lenta y que se note el movimiento del
// anillo; el círculo negro tiene los bordes con píxeles dentados; hazla un poquito más detallada; y con la distorsión
// de la grieta, a veces el agujero negro como que bugueaba»): el disco da una vuelta cada ~84 s (antes ~16 s) y todo lo
// demás va más despacio (grumos, chorros, el anillo); el borde de la sombra se suaviza con el parámetro de impacto del
// rayo (b = |p × v|; cae dentro si b < 3√3/2): un píxel y medio de degradado, sin escalones (antes 0 píxeles entre negro
// y el anillo); el disco lleva hebras finas a lo largo de la órbita y carriles de polvo oscuros (filtrados como el resto);
// y la furia de una grieta ya no cambia el tamaño del agujero ni hace parpadear el anillo (eso era el «bug»): solo más
// brillo y el color de la grieta, que entra y sale despacio (VoidSky).

uniform sampler2D Sampler0;   // panorama de nebulosas (equirectangular, el de rift_sky.py)
uniform float VoidTime;       // segundos
uniform vec3 HoleDir;         // hacia dónde está el agujero (mundo)
uniform vec3 DiskNormal;      // normal del disco (mundo)
uniform float Quality;        // 0.35..1: menos o más pasos
uniform float AtFar;          // 1 = después del paquete de shaders (vacio/AfterShaders): solo donde no hay nada
uniform float Rage;           // 0..1: una grieta activa cerca (1.3.91, RiftWarp): el agujero se enfurece
uniform float RageMother;     // 1 = la grieta madre (carmesí)

in vec3 vDir;
out vec4 fragColor;

const float PI = 3.14159265;
const float DIST = 8.0;       // distancia de la cámara al agujero (1.3.88: 12)
const float RIN = 2.35;       // borde de dentro del disco (algo más allá de la órbita estable)
const float ROUT = 10.5;      // borde de fuera

// El giro del disco: de conjunto (igual en todos los radios: no enrolla) y lo diferencial en capas de SPIN_P segundos
const float SPIN_P = 30.0;                          // 3600/30 = 120 ciclos exactos por hora
const float SPIN_K = 0.3;                           // velocidad (la 1.3.89: 0,8; la 1.3.90: 1,6, «demasiado rápido»)
const float SPIN_RIG = 6.2831853 * 43.0 / 3600.0;   // ≈ ω(r=4)·SPIN_K, en vueltas exactas por hora (una cada ~84 s)
const float BC = 2.5980762;                         // parámetro de impacto crítico (3√3/2): menos, cae dentro
const float NEB_P = 40.0;                           // capas del remolino de las nebulosas (90 por hora)
const float JET_L = 15.0;                           // largo de los chorros (radios)

// Lo que mide un píxel (en radianes de cielo); lo pone main()
float PIX = 0.001;
// La distancia de la cámara al agujero
float D = DIST;

// Una velocidad angular redondeada a vueltas exactas por hora (sin salto cuando VoidTime vuelve a 0)
float qw(float w) {
    return 6.2831853 * floor(w * 3600.0 / 6.2831853 + 0.5) / 3600.0;
}

// Un punto que recorre un círculo en el espacio del ruido a «speed» unidades por segundo (una vuelta por hora): lo
// que se mueve con él cambia sin parar y sin salto a la hora
vec3 drift(float speed, float seed) {
    float a = 6.2831853 * VoidTime / 3600.0 + seed;
    float R = speed * 3600.0 / 6.2831853;
    return vec3(cos(a) * R, sin(a) * R, seed * 17.0);
}

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

vec3 rotY(vec3 d, float a) {
    float ca = cos(a);
    float sa = sin(a);
    return vec3(ca * d.x - sa * d.z, d.y, sa * d.x + ca * d.z);
}

// La nebulosa en la dirección r (ya girada con el cielo): la textura se lee desplazada por un ruido lento que cambia
// con el tiempo (fluye, se deforma) y su luz late a manchas grandes
vec3 nebAt(vec3 r) {
    vec3 wq = r * 2.3 + drift(0.06, 1.0);
    vec2 warp = vec2(noise3(wq), noise3(wq + vec3(5.2, 1.3, 7.7))) - 0.5;
    float u = fract(atan(r.z, r.x) / (2.0 * PI) + warp.x * 0.04);
    float v = clamp(0.5 - asin(clamp(r.y, -1.0, 1.0)) / PI + warp.y * 0.035, 0.001, 0.999);
    // (textureLod: sin mipmaps, y así el salto de u en la costura no cambia nada)
    vec3 neb = textureLod(Sampler0, vec2(u, v), 0.0).rgb;
    neb = pow(neb, vec3(1.45)) * 0.95;
    float glow = noise3(r * 1.7 + drift(0.05, 3.0));
    return neb * (0.55 + 1.0 * glow * glow);
}

// Junto al agujero, la nebulosa gira en remolino y cae hacia él: se lee de donde estaba hace «tau» segundos (más
// lejos y girada hacia atrás), más cuanto más cerca
vec3 swirl(vec3 d, float tau) {
    vec3 f = normalize(HoleDir);
    vec3 b1 = normalize(cross(f, vec3(0.0, 1.0, 0.0)));
    vec3 b2 = cross(f, b1);
    float th = acos(clamp(dot(d, f), -1.0, 1.0));
    float w = exp(-th * 1.5);
    float al = atan(dot(d, b2), dot(d, b1)) - w * tau * 0.025 / (th + 0.15);
    th *= 1.0 + w * tau * 0.006;
    return cos(th) * f + sin(th) * (cos(al) * b1 + sin(al) * b2);
}

// El cielo sin agujero: nebulosas que giran (dos vueltas por hora), fluyen y caen en remolino hacia el agujero (en dos
// capas que se renuevan cada NEB_P segundos y se funden: el remolino no se enrolla), estrellas y el abismo por debajo
vec3 background(vec3 d) {
    float a = VoidTime * 6.2831853 * 2.0 / 3600.0;
    float ph = fract(VoidTime / NEB_P);
    float wa = 1.0 - abs(2.0 * ph - 1.0);
    vec3 na = nebAt(rotY(swirl(d, ph * NEB_P), a));
    vec3 nb = nebAt(rotY(swirl(d, fract(ph + 0.5) * NEB_P), a));
    vec3 col = na * wa + nb * (1.0 - wa);
    vec3 r = rotY(d, a);
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

// El ruido del disco en una capa (0 o 1): el giro de conjunto más lo diferencial de los «tau» segundos que lleva la
// capa (se renueva cada SPIN_P, desplazada para que no se repita). El ruido se filtra contando cuánto lo estira el
// giro diferencial a lo largo del radio (r·|∂rot/∂r| = 1,5·ω·SPIN_K·tau): nunca más fino que ~2 píxeles.
vec2 diskNoise(float ang, float r, float omega, float foot, float layer) {
    float ph = fract(VoidTime / SPIN_P + 0.5 * layer);
    float tau = ph * SPIN_P;
    float cyc = mod(floor(VoidTime / SPIN_P + 0.5 * layer), 3600.0 / SPIN_P);
    vec3 off = vec3(cyc * 7.13, cyc * 3.71, cyc * 5.29);
    float rot = ang - SPIN_RIG * VoidTime - (omega * SPIN_K - SPIN_RIG) * tau;
    float sh = 1.5 * omega * SPIN_K * tau;
    float cr = cos(rot);
    float sr = sin(rot);
    float n1 = fbmf(vec3(cr * r, sr * r, r * 0.4) * 0.75 + off, foot * 0.75 * sqrt(1.0 + sh * sh), 4);
    float s2 = 1.6 * sh / (r * 2.6);
    float n2 = fbmf(vec3(r * 2.6, cr * 1.6, sr * 1.6) + 7.0 + off, foot * 2.6 * sqrt(1.0 + s2 * s2), 4);
    return vec2(n1, n2);
}

// Grumos calientes que orbitan cada uno a su velocidad (vueltas exactas por hora) y laten
float hotspots(float ang, float r) {
    float s = 0.0;
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float ri = RIN + 0.55 + 3.6 * fract(fi * 0.618 + 0.13);
        float a = fi * 2.39996 + qw(2.0 / (ri * sqrt(ri)) * SPIN_K) * VoidTime;
        float da = (mod(ang - a + PI, 2.0 * PI) - PI) * ri;
        float dr = r - ri;
        float life = 0.5 + 0.5 * sin(qw(0.07 + 0.02 * fi) * VoidTime + fi * 1.7);
        s += life * exp(-dr * dr / 0.08 - da * da / 1.4);
    }
    return s;
}

// La luz y lo que tapa el disco en el punto q (en el plano del disco); «foot» = lo que mide un píxel ahí (en radios)
vec4 disk(vec3 q, float r, vec3 v, vec3 n, vec3 e1, vec3 e2, float foot) {
    float ang = atan(dot(q, e2), dot(q, e1));
    float omega = 2.0 / (r * sqrt(r));
    // las dos capas, fundidas sin perder contraste (la que se renueva pesa 0)
    vec2 la = diskNoise(ang, r, omega, foot, 0.0);
    vec2 lb = diskNoise(ang, r, omega, foot, 1.0);
    float wa = 1.0 - abs(2.0 * fract(VoidTime / SPIN_P) - 1.0);
    float wb = 1.0 - wa;
    float kn = inversesqrt(wa * wa + wb * wb);
    float n1 = clamp(0.5 + (wa * (la.x - 0.5) + wb * (lb.x - 0.5)) * kn, 0.0, 1.0);
    float n2 = clamp(0.5 + (wa * (la.y - 0.5) + wb * (lb.y - 0.5)) * kn, 0.0, 1.0);
    // grumos grandes que giran y arcos estirados a lo largo de la órbita (anillos de distinta densidad); el ruido
    // de los arcos va en (radio, ángulo) con el ángulo comprimido: hebras largas, nunca más finas que 2 píxeles
    float lanes = 0.5 + 0.5 * sin(r * 5.2 + 3.0 * n1);
    lanes = mix(0.5, lanes, 1.0 - smoothstep(0.12, 0.3, foot * 5.2 / 6.2832));
    float arcs = smoothstep(0.28, 0.82, n2);
    float dens = (0.2 + 1.2 * n1 * n1) * (0.4 + 0.9 * arcs) * (0.78 + 0.44 * lanes);
    // (1.3.92) más detalle: hebras finas a lo largo de la órbita (ruido en radio fino y ángulo comprimido, que gira con
    // el disco) y carriles de polvo oscuros; los dos se apagan antes de ser más finos que ~2 píxeles
    float rot0 = ang - SPIN_RIG * VoidTime;
    float fil = fbmf(vec3(r * 7.5, cos(rot0) * 2.2, sin(rot0) * 2.2) + 3.3, foot * 7.5, 4);
    dens *= 0.72 + 0.56 * fil;
    float dw = foot * 11.0 / 6.2832;
    float dust = smoothstep(0.82, 0.97, 0.5 + 0.5 * sin(r * 11.0 + 5.0 * n2 + 2.0 * fil));
    dens *= 1.0 - 0.55 * dust * (1.0 - smoothstep(0.1, 0.25, dw));
    dens += 1.5 * hotspots(ang, r);
    dens *= smoothstep(RIN * 0.92, RIN * 1.3, r) * (1.0 - smoothstep(ROUT * 0.45, ROUT, r));
    // (el borde de fuera llega casi hasta la cámara: lo que pasaría a su lado se difumina, sin una lámina rara de lado)
    dens *= smoothstep(-0.9 * D, -0.5 * D, dot(q, normalize(HoleDir)));
    // el disco respira despacio (y con una grieta activa, más brillo y los grumos más vivos, sin parpadeos)
    dens *= 0.9 + 0.12 * sin(qw(0.2) * VoidTime);
    dens *= 1.0 + 0.45 * Rage;
    dens += 1.1 * Rage * hotspots(ang, r);
    float temp = pow(RIN / r, 1.5);
    // Doppler (la luz que llega a la cámara va por -v) y el corrimiento al rojo de la gravedad
    vec3 tang = normalize(cross(n, q));
    float beta = clamp(sqrt(0.5 / max(r - 1.0, 0.4)), 0.0, 0.7);
    float cosT = dot(tang, -normalize(v));
    float g = sqrt(1.0 - beta * beta) / (1.0 - beta * cosT);
    g *= sqrt(max(1.0 - 1.0 / r, 0.05));
    float I = dens * (0.35 + 1.9 * temp) * pow(g, 3.0) * 1.9;
    vec3 c = ramp(temp * g * 1.15);
    // (furia: se tiñe del color de la grieta)
    c = mix(c, mix(vec3(0.75, 0.35, 1.0), vec3(1.0, 0.25, 0.4), RageMother), 0.3 * Rage);
    float a = clamp(dens * (0.45 + 0.6 * temp), 0.0, 0.92);
    return vec4(c * I, a);
}

// Los chorros por el eje del disco: un hilo azul-violeta con nudos que suben (ondas con vueltas exactas por hora)
vec3 jet(vec3 p, vec3 n) {
    float h = dot(p, n);
    float ah = abs(h);
    if (ah < 1.2 || ah > JET_L) return vec3(0.0);
    float w = 0.1 + 0.045 * ah;
    float d2 = dot(p - n * h, p - n * h) / (w * w);
    if (d2 > 9.0) return vec3(0.0);
    float side = h > 0.0 ? 0.0 : 2.1;
    float k = 0.45 + 0.35 * sin(ah * 1.15 - qw(0.8) * VoidTime + side)
            + 0.25 * sin(ah * 2.9 - qw(1.2) * VoidTime + side * 3.0)
            + 0.15 * sin(ah * 0.47 - qw(0.3) * VoidTime + side * 5.0);
    float fade = smoothstep(1.2, 2.6, ah) * (1.0 - smoothstep(JET_L * 0.35, JET_L, ah));
    float core = exp(-d2);
    vec3 c = mix(vec3(0.42, 0.25, 0.95), vec3(0.85, 0.9, 1.0), exp(-d2 * 3.0));
    return c * core * fade * max(k, 0.05) * 0.5 * (1.0 + 1.5 * Rage);
}

void main() {
    // Con paquete de shaders se dibuja al final, con la profundidad de la escena: como si estuviera en el fondo (lo
    // que haya delante, bloques, jefes o la mano, lo tapa; solo pinta donde la escena dejó la profundidad en 1)
    gl_FragDepth = AtFar > 0.5 ? 0.9999995 : gl_FragCoord.z;
    vec3 dir = normalize(vDir);
    // lo que mide un píxel (antes de cualquier bucle: las derivadas solo valen fuera de él)
    PIX = max(length(fwidth(dir)), 1e-5);
    vec3 f = normalize(HoleDir);
    vec3 n = normalize(DiskNormal);
    vec3 e1 = normalize(cross(n, f));
    vec3 e2 = cross(n, e1);

    D = DIST;
    vec3 p = -f * D;
    vec3 v = dir;
    vec3 hc = cross(p, v);
    float h2 = dot(hc, hc);

    // El borde de la sombra según el parámetro de impacto (ver abajo): 0 dentro, 1 fuera, ~1,5 píxeles de degradado.
    // Solo para los rayos que van hacia el agujero (dot(p, v) < 0): b = |p × v| también es ~0 para los que van justo al
    // revés, y en la 1.3.92 salía en el lado opuesto del cielo otro agujero, un círculo negro sin disco (el dueño: «hay
    // 2 agujeros»). Al cambiar de lado b vale D (8) ≫ BC: sin salto.
    float b = dot(p, v) < 0.0 ? sqrt(h2) : 1e3;
    float bw = max(PIX * D * 0.8, 0.002);
    float edgeK = smoothstep(BC - bw, BC + bw, b);
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
        // (junto a los chorros, pasos más cortos: son finos)
        float hj = dot(p, n);
        float dj = length(p - n * hj);
        if (abs(hj) < JET_L) dt = min(dt, max(0.12, 0.5 * dj));
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
                // (lo que cruza el disco después de pasar junto al agujero —sus imágenes de segundo orden— solo fuera
                // de la sombra, con su mismo degradado: con pasos finitos, junto al borde salían puntos sueltos)
                if (minR < 2.0) dc *= edgeK;
                col += (1.0 - alpha) * dc.rgb;
                alpha += (1.0 - alpha) * dc.a;
            }
        }
        col += (1.0 - alpha) * jet(p, n) * length(v) * dt;
        travel += length(v) * dt;
        p = pn;
        float rn = length(p);
        if (rn < 1.0) {
            captured = true;
            break;
        }
        if (rn > D * 2.6 && dot(p, v) > 0.0) break;
    }

    // (las ondas de luz que dan la vuelta al anillo, despacio: se ve cómo gira)
    float ra = atan(dot(dir, e2), dot(dir, e1));
    float wave = 0.72 + 0.28 * sin(ra * 3.0 - qw(0.35) * VoidTime) + 0.12 * sin(ra * 7.0 + qw(0.5) * VoidTime);
    vec3 ringC = vec3(1.0, 0.86, 0.7) * 0.95 * wave * (1.0 + 0.5 * Rage);
    vec3 halo = vec3(0.55, 0.22, 0.95) * 0.26 + vec3(1.0, 0.55, 0.3) * 0.14;
    // Si cae dentro o no lo dice el parámetro de impacto (b = |p × v|; cae si b < 3√3/2), no los pasos del bucle: cerca
    // de la esfera de fotones el rayo da vueltas y, con pasos finitos, algunos que caen «escapaban» (puntos sueltos
    // dentro de la sombra) o al revés
    // El anillo de fotones, también según b (fino y brillante justo fuera de la sombra, nunca más fino que un píxel), y
    // un halo violeta que se abre, con un poco de naranja cerca
    float ringB = exp(-pow((b - BC - 0.01) / max(0.03, PIX * D * 1.5), 2.0));
    vec3 bg = ringC * ringB;
    if (b > BC + 2.0 * bw && !captured) {
        vec3 neb = background(normalize(v));
        // Junto al agujero la nebulosa (que la lente estira en remolinos) se apaga: el disco y la sombra mandan
        bg += neb * mix(0.3, 1.0, smoothstep(2.2, 9.0, minR));
        bg += vec3(0.55, 0.22, 0.95) * exp(-max(minR - 1.5, 0.0) * 0.42) * 0.26;
        bg += vec3(1.0, 0.55, 0.3) * exp(-max(minR - 1.5, 0.0) * 1.1) * 0.14;
    } else {
        bg += halo;
    }
    // El borde de la sombra, suave: un degradado de ~1,5 píxeles según b (continuo de un píxel al de al lado)
    bg *= edgeK;
    vec3 outc = col + (1.0 - alpha) * bg;
    // Tono: que lo muy brillante no se queme en blanco plano
    outc = vec3(1.0) - exp(-outc * 1.35);
    // Tramado suave (sin escalones en el degradado oscuro)
    outc += (hash13(vec3(gl_FragCoord.xy, 3.0)) - 0.5) / 255.0;
    fragColor = vec4(outc, 1.0);
}
