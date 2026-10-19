#version 150

// El viaje por el agujero de gusano (TF Client 1.3.91; el dueño: «una escena como si viajara por un agujero de gusano
// con la realidad alterada, con imágenes del Overworld, el Nether y el End; y de regreso lo mismo pero End, Nether y
// Overworld; algo súper épico»). Lo dibuja TravelFx sobre toda la pantalla (y sobre la de carga al cambiar de mundo).
//
// 1.3.92 (el dueño: «hazlo limpio: se ven cuadrados; no quiero ver cuadrados, quiero ver una textura completa, limpia,
// no fragmentos; y después del End debe haber una antesala al Void»): fuera las losetas de bloques, los cubos que
// pasaban girando y las texturas de píxeles (portal, estrellas del End). Ahora cada tramo es una textura continua y
// suave (ruido filtrado por lo que mide un píxel: nada más fino que ~2 píxeles; y la vuelta del túnel sin costura):
//  - Overworld: cielo con nubes y, abajo, campos, bosques, cerezos y ríos; el horizonte da vueltas con el túnel.
//  - Nether: magma que fluye, con venas de lava brillante sobre roca roja oscura, y ascuas.
//  - End: nebulosa violeta con corrientes claras (el color de la piedra del End) y estrellas.
//  - La antesala del Void: las nebulosas del cielo del Void con hebras doradas del disco y, al fondo, el agujero negro
//    que crece; al llegar se cruza: crece hasta llenar la pantalla y de su anillo sale la luz.
//  - Entre tramo y tramo, una puerta: un anillo de luz en las paredes (morado, el del Nether; turquesa con estrellas, el
//    del End; violeta con oro, el del Void) y su membrana, un remolino suave que se cruza con un fogonazo.
// A la ida: Overworld → Nether → End → antesala del Void; a la vuelta (Back): antesala → End → Nether → Overworld.
//
// 1.3.96 (el dueño: «que al menos muestre la dimensión; en el Nether hay fuego de textura, pero no sé si estoy viendo el
// Nether de verdad; y el tramo final al Void me parece pobre»): cada tramo ES su dimensión. Se ve su panorama de verdad
// (equirectangular: el Overworld, del panorama del menú de cada uno, TunnelPanoramas; el Nether, el End y el Void,
// capturados en el juego, tools/void/panoramas.py) con una cámara que avanza (acerca) y mira alrededor, y el túnel queda
// como un velo de luz por encima (más en los bordes). A través de cada puerta ya se ve la dimensión siguiente. En el
// Void, la cámara mira a su agujero negro de verdad, que crece con una lente gravitatoria hasta tragarte; al llegar, la
// luz dorada del Alba llena la pantalla.
// La realidad se rasga al empezar (Open) y la luz del fondo llena la pantalla al llegar (Exit). Vista previa sin juego:
// tools/void/tunel_preview.py (mismos tiempos que TravelFx).

uniform sampler2D Sampler1;   // las nebulosas del cielo del Void (grieta_cielo.png, equirectangular)
uniform sampler2D Sampler2;   // (1.3.96) el Overworld: el panorama del menú, equirectangular (TunnelPanoramas)
uniform sampler2D Sampler3;   // el Nether (tunel_nether.png)
uniform sampler2D Sampler4;   // el End (tunel_end.png)
uniform sampler2D Sampler5;   // el Void (tunel_void.png)
uniform float HasOverworld;   // 1 si se pudo hacer el panorama del Overworld
uniform float WTime;          // segundos
uniform float Travel;         // camino recorrido por el túnel
uniform vec3 Gates;           // dónde están las tres puertas (en camino)
uniform float Open;           // 0..1 el desgarro se abre
uniform float Exit;           // 0..1 la luz del fondo llena la pantalla; 1..2 se va
uniform float Back;           // 1 = la vuelta
uniform vec2 ScreenSize;

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265;
const float TAU = 6.2831853;
const float K = 0.55;

// Lo que mide un píxel en el ruido de las paredes (lo pone scene)
float FOOT = 0.01;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
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

// Ruido fractal filtrado: cada octava se apaga (hacia su media) antes de ser más fina que ~2 píxeles (foot = lo que
// mide un píxel en las unidades de p)
float fbm(vec3 p, float foot) {
    float s = 0.0;
    float a = 0.5;
    float fr = 1.0;
    for (int i = 0; i < 5; i++) {
        float k = 1.0 - smoothstep(0.22, 0.5, fr * foot);
        s += a * mix(0.5, noise3(p), k);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        fr *= 2.03;
        a *= 0.5;
    }
    return s / 0.96875;
}

// Un punto de la pared en el espacio del ruido: la vuelta del túnel (u, en vueltas) como un círculo, sin costura
vec3 wallP(float u, float w, float ru, float sw) {
    return vec3(cos(u * TAU) * ru, sin(u * TAU) * ru, w * sw);
}

vec3 overworld(float u, float w) {
    // cielo con nubes arriba y, abajo, campos, bosques, cerezos y ríos; el horizonte da vueltas con el túnel
    vec3 q = wallP(u, w, 1.4, 0.3) + vec3(0.0, 0.0, -WTime * 0.05);
    float n = fbm(q, FOOT * 1.4);
    float n2 = fbm(q * 2.2 + 5.0, FOOT * 3.1);
    float e = sin(u * TAU - 0.6);
    float land = smoothstep(-0.05, 0.25, -e + 0.35 * (n - 0.5));
    vec3 sky = mix(vec3(0.42, 0.64, 0.97), vec3(0.8, 0.89, 1.0), clamp(e * 0.5 + 0.5, 0.0, 1.0));
    sky = mix(sky, vec3(1.0, 0.99, 0.97), smoothstep(0.55, 0.82, n2) * 0.85);
    vec3 g = mix(vec3(0.2, 0.46, 0.14), vec3(0.44, 0.7, 0.23), smoothstep(0.3, 0.7, n2));
    g = mix(g, vec3(0.09, 0.28, 0.1), smoothstep(0.62, 0.8, n) * 0.8);
    g = mix(g, vec3(0.97, 0.64, 0.8), smoothstep(0.78, 0.92, n2) * 0.75);
    float river = smoothstep(0.035, 0.0, abs(n - 0.42)) * (1.0 - smoothstep(0.25, 0.6, FOOT * 28.0));
    g = mix(g, vec3(0.28, 0.52, 0.95), river * 0.85);
    return mix(sky, g, land);
}

vec3 nether(float u, float w) {
    // magma que fluye: roca roja oscura y venas de lava que brillan (crestas de un ruido deformado por otro)
    vec3 q = wallP(u, w, 1.5, 0.42);
    float n = fbm(q + vec3(0.0, 0.0, -WTime * 0.25), FOOT * 1.5);
    float m = fbm(q * 1.6 + n * 1.8 + vec3(3.1, 1.7, WTime * 0.12), FOOT * 2.4);
    float vein = 1.0 - abs(2.0 * m - 1.0);
    float veinK = 1.0 - smoothstep(0.25, 0.6, FOOT * 2.4 * 6.0);
    vein = mix(0.35, pow(vein, 7.0), veinK);
    vec3 rock = mix(vec3(0.10, 0.015, 0.02), vec3(0.42, 0.06, 0.05), smoothstep(0.25, 0.75, n));
    vec3 lava = mix(vec3(1.0, 0.32, 0.04), vec3(1.0, 0.86, 0.42), vein);
    vec3 c = rock + lava * vein * 2.4;
    c += vec3(0.9, 0.22, 0.04) * smoothstep(0.6, 0.85, n) * 0.6;
    return c;
}

vec3 endWorld(float u, float w) {
    // nebulosa violeta con corrientes claras (el amarillo pálido de la piedra del End) que se retuercen
    vec3 q = wallP(u, w, 1.3, 0.28) + vec3(0.0, 0.0, -WTime * 0.08);
    float n = fbm(q, FOOT * 1.3);
    float m = fbm(q * 1.4 + vec3(n * 1.5, 4.0, 2.0), FOOT * 1.8);
    float stream = 1.0 - abs(2.0 * m - 1.0);
    stream = mix(0.3, pow(stream, 5.0), 1.0 - smoothstep(0.25, 0.6, FOOT * 1.8 * 5.0));
    vec3 c = mix(vec3(0.04, 0.015, 0.09), vec3(0.36, 0.12, 0.62), smoothstep(0.2, 0.8, n));
    c += vec3(0.95, 0.9, 0.6) * stream * 0.55;
    c += vec3(0.25, 0.85, 0.75) * smoothstep(0.72, 0.9, n) * 0.25;
    return c;
}

vec3 voidWorld(float u, float w) {
    // las nebulosas del cielo del Void, oscuras, con hebras doradas del disco del agujero que giran hacia el fondo
    float v = 0.5 - 0.3 * cos(w * 0.16 + 0.7 * sin(u * TAU));
    vec3 neb = texture(Sampler1, vec2(u + w * 0.015, v)).rgb;
    neb = pow(neb, vec3(1.35)) * 0.9;
    vec3 q = wallP(u, w, 1.0, 0.3);
    float n = fbm(q + vec3(0.0, 0.0, -WTime * 0.1), FOOT);
    float sp = 0.5 + 0.5 * sin(u * TAU * 3.0 + w * 0.9 - WTime * 0.6 + n * 3.0);
    float gold = mix(0.2, pow(sp, 14.0), 1.0 - smoothstep(0.2, 0.5, FOOT * 9.0));
    return neb + vec3(1.0, 0.68, 0.32) * gold * 0.65 * smoothstep(0.35, 0.7, n);
}

vec3 worldWall(float world, float u, float w) {
    if (world < 0.5) return overworld(u, w);
    if (world < 1.5) return nether(u, w);
    if (world < 2.5) return endWorld(u, w);
    return voidWorld(u, w);
}

// El color de la niebla de cada tramo hacia el fondo (que brilla); en la antesala, oscuro: al fondo, el agujero
vec3 fogColor(float world) {
    if (world < 0.5) return vec3(1.0, 0.9, 0.7);
    if (world < 1.5) return vec3(1.0, 0.36, 0.08);
    if (world < 2.5) return vec3(0.55, 0.32, 0.9);
    return vec3(0.16, 0.06, 0.28);
}

// Qué tramo hay a esa profundidad: 0 Overworld, 1 Nether, 2 End, 3 la antesala del Void
float worldAt(float w) {
    float k = w < Gates.x ? 0.0 : (w < Gates.y ? 1.0 : (w < Gates.z ? 2.0 : 3.0));
    return Back > 0.5 ? 3.0 - k : k;
}

// La puerta i lleva al tramo que hay detrás de ella; su color
vec3 gateColor(float i) {
    float to = worldAt((i < 0.5 ? Gates.x : (i < 1.5 ? Gates.y : Gates.z)) + 0.01);
    if (to < 0.5) return vec3(1.0, 0.9, 0.6);       // al Overworld: luz de día
    if (to < 1.5) return vec3(0.68, 0.25, 1.0);      // al Nether: el morado de su portal
    if (to < 2.5) return vec3(0.3, 0.95, 0.82);      // al End: el turquesa de su portal
    return vec3(0.55, 0.2, 0.9);                     // al Void: violeta
}

// La membrana de una puerta (vista de frente; pm en el disco unidad): un remolino suave de su color
vec3 membrane(float i, vec2 pm) {
    float rr = length(pm);
    float an = atan(pm.y, pm.x) + rr * 3.0 - WTime * 1.6;
    vec3 q = vec3(cos(an) * rr * 2.2, sin(an) * rr * 2.2, i * 7.0 + WTime * 0.2);
    float n = fbm(q, 0.02);
    vec3 gc = gateColor(i);
    vec3 c = gc * (0.25 + 1.1 * n * n) + vec3(1.0) * pow(n, 6.0) * 0.6;
    // (la del Void: oscura con un borde dorado)
    float to = worldAt((i < 0.5 ? Gates.x : (i < 1.5 ? Gates.y : Gates.z)) + 0.01);
    if (to > 2.5) c = mix(c * 0.35, vec3(1.0, 0.7, 0.3), smoothstep(0.7, 0.98, rr) * 0.7);
    return c;
}

// (1.3.96) El panorama de un tramo en la dirección d (x este, y arriba, z sur; longitud 0 = sur, en el centro)
vec3 pano(float world, vec3 d) {
    vec2 uv = vec2(atan(d.x, d.z) / TAU + 0.5, clamp(0.5 - asin(clamp(d.y, -1.0, 1.0)) / PI, 0.002, 0.998));
    if (world < 0.5) return textureLod(Sampler2, uv, 0.0).rgb;
    if (world < 1.5) return textureLod(Sampler3, uv, 0.0).rgb;
    if (world < 2.5) return textureLod(Sampler4, uv, 0.0).rgb;
    return textureLod(Sampler5, uv, 0.0).rgb;
}

// Cuánto se ha avanzado dentro del tramo «world» (0..1; en el del Void, hacia el agujero)
float segment(float world) {
    float k = Back > 0.5 ? 3.0 - world : world;
    float a = k < 0.5 ? 0.0 : (k < 1.5 ? Gates.x : (k < 2.5 ? Gates.y : Gates.z));
    float b = k < 0.5 ? Gates.x : (k < 1.5 ? Gates.y : (k < 2.5 ? Gates.z : Gates.z + 9.0));
    return clamp((Travel - a) / (b - a), 0.0, 1.0);
}

// La cámara de cada tramo: hacia dónde mira el centro (yaw, pitch) y cuánto acerca. Mira a lo que se reconoce: el
// mar de lava del Nether, las columnas del End (y su dragón), el agujero negro del Void
vec3 camDir(float world, vec2 q) {
    float s = segment(world);
    float yaw, pitch, f;
    if (world > 2.5) {
        // el agujero negro del Void (VoidSky.HOLE: 9° al este del sur, 22,6° de altura); a la ida se acerca hasta tragarte
        yaw = 0.157 + 0.04 * sin(WTime * 0.6);
        pitch = 0.394 + 0.03 * sin(WTime * 0.5);
        f = Back > 0.5 ? 1.1 : 1.0 + 1.8 * s * s + 9.0 * smoothstep(0.0, 0.75, Exit);
        // la lente del agujero: la luz se curva a su alrededor
        float l = (Back > 0.5 ? 0.004 : 0.004 + 0.03 * s) / max(dot(q, q), 0.0025);
        q *= 1.0 + l;
    } else {
        float base = world < 0.5 ? 0.0 : (world < 1.5 ? -0.25 : 0.05);
        yaw = base + 0.55 * sin(WTime * 0.35 + world * 1.7) + 0.5 * (s - 0.5);
        pitch = world < 1.5 ? -0.12 : -0.02;
        pitch += 0.06 * sin(WTime * 0.6 + world);
        f = 0.85 + 0.6 * s;
    }
    vec3 d = normalize(vec3(-q.x, q.y, f));
    // inclinar (pitch, hacia arriba +) y girar (yaw, desde el sur hacia el este +)
    float cp = cos(pitch), sp = sin(pitch);
    d = vec3(d.x, d.y * cp + d.z * sp, -d.y * sp + d.z * cp);
    float cy = cos(yaw), sy = sin(yaw);
    return vec3(d.x * cy + d.z * sy, d.y, -d.x * sy + d.z * cy);
}

// Lo que se ve a la distancia r del centro, en el ángulo a
vec3 scene(float r, float a) {
    float rr = max(r, 0.004);
    float w = K / rr + Travel;
    float u = a / TAU + 0.045 * w + 0.02 * WTime;
    // lo que mide un píxel en la pared: a lo largo (en profundidad) K/r² por píxel; alrededor, 1/(2πr) vueltas
    float px = 1.0 / ScreenSize.y;
    FOOT = max(K / (rr * rr) * px * 0.42, px / rr * 1.4);
    float world = worldAt(w);
    vec3 wall = worldWall(world, u, w);
    // (1.3.96) la dimensión de verdad (su panorama) y, por encima, el túnel como un velo de luz (más en los bordes); sin
    // el panorama del Overworld, su pared de antes
    vec2 q = vec2(cos(a), sin(a)) * r;
    vec3 view = pano(world, camDir(world, q));
    float veil = 0.1 + 0.24 * smoothstep(0.55, 1.1, r);
    if (world < 0.5 && HasOverworld < 0.5) veil = 1.0;
    vec3 col = mix(view, wall, veil);
    // las puertas: un anillo de luz en las paredes y, más allá, su membrana
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float g = i == 0 ? Gates.x : (i == 1 ? Gates.y : Gates.z);
        float dg = w - g;
        vec3 gc = gateColor(fi);
        float band = exp(-dg * dg * 6.0);
        // (1.3.96: la de una puerta ya cruzada, que se va por los bordes, tiñe menos: que mande la dimensión)
        col = mix(col, gc * 1.6, band * (g > Travel ? 0.6 : 0.25));
        col += gc * exp(-dg * dg * 60.0) * 1.2;
        if (g > Travel && dg > 0.0) {
            float rg = K / (g - Travel);
            vec2 pm = vec2(cos(a), sin(a)) * r / rg;
            col = mix(col, membrane(fi, pm), 0.18);
        }
    }
    // niebla del color del tramo hacia el fondo (que brilla); y donde la pared ya es más fina que un par de píxeles
    // (hacia el centro), su color medio
    float depth = w - Travel;
    float f = 1.0 - exp(-depth * 0.08);
    float aaf = smoothstep(0.08, 0.3, FOOT);
    vec3 fc = fogColor(world);
    col = mix(col, fc * 0.9, aaf * 0.8 * veil);
    col = mix(col, fc * 1.35, f * (world > 2.5 ? 0.12 : 0.3));
    // rayas de velocidad
    float lane = floor(fract(a / TAU) * 110.0);
    float hs = hash11(lane + 17.0);
    float dash = exp(-fract(w * 0.35 + hs * 7.0) * 12.0) * step(0.86, hs) * smoothstep(0.08, 0.5, r);
    col += mix(vec3(1.0), fc, 0.3) * dash * 1.2;
    return col;
}

// Ascuas, chispas y motas de luz que pasan volando (redondas y suaves)
vec3 motes(float r, float a) {
    float w = K / max(r, 0.004) + Travel;
    float world = worldAt(w);
    vec2 g = vec2(fract(a / TAU) * 70.0, w * 2.2);
    vec2 gi = floor(g);
    float h = hash13(vec3(gi, 31.0));
    if (h < 0.93) return vec3(0.0);
    vec2 cpos = vec2(hash13(vec3(gi, 7.0)), hash13(vec3(gi, 11.0))) * 0.8 + 0.1;
    float d = length((fract(g) - cpos) * vec2(1.0, 0.6));
    float b = smoothstep(0.22, 0.0, d) * smoothstep(0.05, 0.3, r);
    vec3 col = world < 0.5 ? vec3(1.0, 0.95, 0.75) : (world < 1.5 ? vec3(1.0, 0.55, 0.12) : (world < 2.5 ? vec3(0.8, 0.6, 1.0) : vec3(1.0, 0.75, 0.4)));
    return col * b * 1.4;
}

void main() {
    float aspect = ScreenSize.x / ScreenSize.y;
    vec2 p = (texCoord - 0.5) * vec2(aspect, 1.0);
    float t = WTime;
    // el túnel se curva: su centro se mueve
    vec2 c = vec2(0.09 * sin(t * 0.9) + 0.04 * sin(t * 2.3), 0.07 * cos(t * 0.7) + 0.03 * sin(t * 1.7)) * Open;
    vec2 q = p - c;
    float r = length(q);
    float a = atan(q.y, q.x);
    vec3 col = scene(r, a);
    // (un poco de aberración: rojo hacia fuera y azul hacia dentro, en los bordes)
    float edge = smoothstep(0.35, 0.9, r);
    col *= vec3(1.0 + 0.06 * edge, 1.0, 1.0 - 0.04 * edge);
    col += motes(r, a);
    // al cruzar una puerta, fogonazo de su color
    for (int i = 0; i < 3; i++) {
        float g = i == 0 ? Gates.x : (i == 1 ? Gates.y : Gates.z);
        float x = Travel - g;
        col = mix(col, gateColor(float(i)) * 1.3, exp(-x * x * 18.0) * 0.7);
    }
    // viñeta
    col *= 1.0 - 0.4 * smoothstep(0.55, 1.1, length(p));
    // la salida: la luz del fondo llena la pantalla (a la ida, sale del anillo del agujero)
    vec3 dest = Back > 0.5 ? vec3(1.0, 0.96, 0.88) : vec3(1.0, 0.9, 0.66);
    float er = Back > 0.5 ? Exit * 1.4 : smoothstep(0.45, 1.0, Exit) * 1.6;
    float light = smoothstep(er, er - 0.45, r) * step(0.001, Exit);
    col = mix(col, dest * 1.4, light);
    col = mix(col, dest * 1.5, smoothstep(0.85, 1.05, Exit));
    // el desgarro que se abre al empezar
    float ang = a;
    float jag = 0.05 * (fbm(vec3(cos(ang) * 2.0, sin(ang) * 2.0, t * 1.3), 0.0) - 0.5) + 0.02 * sin(ang * 11.0 + t * 4.0);
    float R = 0.02 + 1.3 * Open * Open;
    float d = length(q * vec2(1.0, 1.0 + 1.6 * (1.0 - Open))) - R * (1.0 + jag);
    float inside = smoothstep(0.012, -0.012, d);
    float rim = exp(-abs(d) * 45.0) * (1.0 - smoothstep(0.85, 1.0, Open));
    vec3 rimc = Back > 0.5 ? vec3(1.0, 0.9, 0.7) : vec3(0.85, 0.6, 1.0);
    float alpha = clamp(inside + rim, 0.0, 1.0) * (1.0 - smoothstep(1.0, 1.9, Exit));
    // (sin premultiplicar: lo mezcla ONE_MINUS_SRC_ALPHA con el mundo de detrás)
    vec3 outc = col * inside + rimc * rim * 1.6;
    vec3 rgb = alpha > 0.001 ? outc / alpha : vec3(0.0);
    fragColor = vec4(min(rgb, vec3(4.0)), alpha);
}
