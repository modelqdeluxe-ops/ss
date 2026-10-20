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
//
// 1.3.97 (el dueño: «el agujero de gusano no parece que es uno, su animación dentro del túnel es una mierda, dale
// rework»): era el panorama de cada dimensión con una cámara y un velo encima, no un túnel. Ahora es un AGUJERO DE GUSANO:
//  - un túnel de verdad, en perspectiva, que serpentea (su centro se mueve más cuanto más lejos: bendAt) y gira; sus
//    paredes son la dimensión en la que estás estirada a lo largo del túnel (su panorama envuelto en las paredes, con
//    estela), con hebras del espacio-tiempo (vetas finas que corren hacia ti) y filamentos de luz en espiral;
//  - al fondo de cada tramo, la BOCA del agujero: una esfera que deja ver la dimensión siguiente entera (como en
//    Interstellar: el otro lado en ojo de pez, lo de detrás apretado contra el borde), con su anillo de luz y su halo;
//    crece al acercarte, las paredes de alrededor se tiñen de su luz y la cruzas con un fogonazo de su color;
//  - a la ida, la última boca es el agujero negro del Void (sombra, anillo de fotones, el disco de acreción que pasa por
//    delante y su imagen doblada alrededor, más brillante por un lado), y de su anillo sale la luz al llegar; a la
//    vuelta, la boca del Overworld, de día.
// La realidad se rasga al empezar (Open) y la luz del fondo llena la pantalla al llegar (Exit). Vista previa sin juego:
// tools/void/tunel_preview.py (mismos tiempos que TravelFx).

uniform sampler2D Sampler1;   // las nebulosas del cielo del Void (grieta_cielo.png, equirectangular): las paredes del Void
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
const float K = 0.55;         // la profundidad: a la distancia r del centro se ve el camino K/r por delante
const float END_GAP = 10.0;   // la boca del final, tras la última puerta (en camino)
const float TWIST = 0.035;    // lo que se retuercen las paredes (vueltas por unidad de camino)

// Lo que mide un píxel en el ruido de las paredes (lo pone tunnelWall; lo usa overworld)
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
    // (sin el panorama del Overworld) cielo con nubes arriba y, abajo, campos, bosques, cerezos y ríos
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
    return mix(sky, g, land);
}

// El color de la luz de cada tramo (hacia el fondo brilla)
vec3 fogColor(float world) {
    if (world < 0.5) return vec3(1.0, 0.9, 0.7);
    if (world < 1.5) return vec3(1.0, 0.36, 0.08);
    if (world < 2.5) return vec3(0.55, 0.32, 0.9);
    return vec3(0.62, 0.3, 1.0);
}

// Qué tramo hay a esa profundidad: 0 Overworld, 1 Nether, 2 End, 3 la antesala del Void
float worldAt(float w) {
    float k = w < Gates.x ? 0.0 : (w < Gates.y ? 1.0 : (w < Gates.z ? 2.0 : 3.0));
    return Back > 0.5 ? 3.0 - k : k;
}

// La luz de la boca que lleva al mundo «to» (la del final: a la ida el oro del agujero negro, a la vuelta el día)
vec3 mouthColor(float idx, float to) {
    if (idx > 2.5) return Back > 0.5 ? vec3(1.0, 0.95, 0.82) : vec3(1.0, 0.7, 0.32);
    if (to < 0.5) return vec3(1.0, 0.9, 0.6);       // al Overworld: luz de día
    if (to < 1.5) return vec3(0.75, 0.3, 1.0);      // al Nether: el morado de su portal
    if (to < 2.5) return vec3(0.3, 0.95, 0.82);     // al End: el turquesa de su portal
    return vec3(0.62, 0.28, 1.0);                   // al Void: violeta
}

// El panorama de un tramo en la dirección d (x este, y arriba, z sur; longitud 0 = sur, en el centro)
vec3 pano(float world, vec3 d) {
    vec2 uv = vec2(atan(d.x, d.z) / TAU + 0.5, clamp(0.5 - asin(clamp(d.y, -1.0, 1.0)) / PI, 0.002, 0.998));
    if (world < 0.5) {
        if (HasOverworld > 0.5) return textureLod(Sampler2, uv, 0.0).rgb;
        // (sin su panorama) cielo con nubes y campos
        float n = fbm(d * 3.0 + vec3(0.0, 0.0, WTime * 0.02), 0.02);
        vec3 sky = mix(vec3(0.62, 0.78, 1.0), vec3(0.32, 0.52, 0.95), clamp(d.y, 0.0, 1.0));
        sky = mix(sky, vec3(1.0), smoothstep(0.55, 0.75, n) * 0.7 * smoothstep(0.0, 0.2, d.y));
        vec3 land = mix(vec3(0.27, 0.52, 0.18), vec3(0.14, 0.34, 0.12), smoothstep(0.4, 0.7, n));
        return mix(land, sky, smoothstep(-0.03, 0.03, d.y));
    }
    if (world < 1.5) return textureLod(Sampler3, uv, 0.0).rgb;
    if (world < 2.5) return textureLod(Sampler4, uv, 0.0).rgb;
    return textureLod(Sampler5, uv, 0.0).rgb;
}

// Hacia dónde mira la boca de cada mundo: a lo que se reconoce (el sur del Overworld, el mar de lava del Nether, las
// columnas del End, el agujero negro del Void: VoidSky.HOLE, 9° al este del sur y 22,6° de altura)
vec3 aim(float world, vec3 d) {
    float yaw, pitch;
    if (world > 2.5) {
        yaw = 0.157;
        pitch = 0.394;
    } else if (world > 1.5) {
        yaw = 0.05;
        pitch = -0.04;
    } else if (world > 0.5) {
        yaw = -0.25;
        pitch = -0.16;
    } else {
        yaw = 0.0;
        pitch = 0.04;
    }
    yaw += 0.12 * sin(WTime * 0.3 + world);
    float cp = cos(pitch), sp = sin(pitch);
    d = vec3(d.x, d.y * cp + d.z * sp, -d.y * sp + d.z * cp);
    float cy = cos(yaw), sy = sin(yaw);
    return vec3(d.x * cy + d.z * sy, d.y, -d.x * sy + d.z * cy);
}

// El túnel serpentea: cuánto se aparta (en pantalla) su centro a la profundidad z delante de ti (nada a tus pies, más
// cuanto más lejos; las curvas cambian según avanzas)
vec2 curveAt(float s) {
    return vec2(sin(s * 0.21) + 0.5 * sin(s * 0.47 + 1.3), cos(s * 0.17 + 0.4) + 0.5 * sin(s * 0.39 + 2.1));
}

vec2 bendAt(float z) {
    return (curveAt(Travel + z) - curveAt(Travel)) * 0.07 * (1.0 - exp(-z * 0.22)) * Open;
}

// La pared del túnel en (u vueltas alrededor, w camino) a la distancia r del centro
vec3 tunnelWall(float world, float u, float w, float r) {
    float px = 1.0 / ScreenSize.y;
    float rr = max(r, 1e-3);
    // lo que mide un píxel: alrededor 1/(2πr) vueltas; en profundidad K/r² de camino
    float perU = px / (TAU * rr);
    float perW = K * px / (rr * rr);
    vec3 c;
    if (world < 0.5 && HasOverworld < 0.5) {
        FOOT = max(perW * 0.42, px / rr * 1.4);
        c = overworld(u, w);
    } else {
        // la dimensión envuelta en las paredes: su panorama (la longitud alrededor; la latitud, cerca del horizonte, va
        // y viene despacio por el camino), con estela hacia el fondo
        c = vec3(0.0);
        for (int k = 0; k < 3; k++) {
            float wk = w + float(k) * 0.3;
            float v = 0.5 - 0.24 * sin(wk * 0.13 + world * 1.9) - 0.06 * sin(u * TAU * 2.0 + wk * 0.05);
            vec2 uv = vec2(fract(u + 0.013 * wk), clamp(v, 0.05, 0.95));
            if (world > 2.5) c += pow(textureLod(Sampler1, uv, 0.0).rgb, vec3(1.2)) * 1.3;
            else if (world > 1.5) c += textureLod(Sampler4, uv, 0.0).rgb;
            else if (world > 0.5) c += textureLod(Sampler3, uv, 0.0).rgb;
            else c += textureLod(Sampler2, uv, 0.0).rgb;
        }
        c /= 3.0;
    }
    // hebras del espacio-tiempo: vetas finas alrededor y largas en profundidad, que corren hacia ti
    float fib = fbm(vec3(cos(u * TAU) * 6.0, sin(u * TAU) * 6.0, w * 0.22), max(perU * TAU * 6.0, perW * 0.22));
    float fib2 = fbm(vec3(cos(u * TAU) * 2.4, sin(u * TAU) * 2.4, w * 0.08 + 7.0), max(perU * TAU * 2.4, perW * 0.08));
    vec3 tint = fogColor(world);
    c = c * (0.5 + 1.05 * fib * fib) * 1.2 + tint * pow(fib2, 3.0) * 0.7;
    // filamentos de luz en espiral (nueve alrededor); se apagan donde ya son más finos que un píxel
    float N = 9.0;
    float f = u * N + w * 0.12 + 0.7 * fib2;
    float dl = abs(fract(f + 0.5) - 0.5);
    float lw = N * perU;
    float line = smoothstep(0.03 + lw * 1.2, 0.0, dl) * (1.0 - smoothstep(0.12, 0.4, lw));
    c += mix(vec3(1.0), tint, 0.45) * line * (0.35 + 0.75 * fib2);
    // (el End y el Void) estrellas que pasan estiradas por la velocidad
    if (world > 1.5) {
        vec2 sg = vec2(u * 160.0, w * 1.3);
        vec2 si = floor(sg);
        float sh = hash13(vec3(si, world * 13.0));
        if (sh > 0.86) {
            vec2 sc = vec2(hash13(vec3(si, 5.0)), hash13(vec3(si, 9.0))) * 0.6 + 0.2;
            vec2 sd = (fract(sg) - sc) * vec2(1.0, 0.35);
            float star = smoothstep(0.16, 0.0, length(sd)) * (1.0 - smoothstep(0.1, 0.35, perU * 160.0));
            c += mix(vec3(1.0), tint, 0.3) * star * (1.2 + 2.0 * (sh - 0.86) / 0.14);
        }
    }
    // donde la pared ya es más fina que un par de píxeles (hacia el fondo), su color medio, que brilla
    float aaf = smoothstep(0.05, 0.25, perW * 0.22);
    return mix(c, tint * 0.9, aaf * 0.85);
}

// Dentro de una boca (m: en su disco unidad): la dimensión siguiente entera, en ojo de pez (lo de detrás de ella,
// apretado contra el borde), con algo de su luz hacia el borde
vec3 sphereView(float world, vec2 m, vec3 mc) {
    float d = min(length(m), 1.0);
    float th = 2.5 * pow(d, 1.45);
    float ph = atan(m.y, m.x);
    vec3 dir = aim(world, vec3(-sin(th) * cos(ph), sin(th) * sin(ph), cos(th)));
    vec3 v = pano(world, dir) * 1.1;
    return mix(v, mc * 1.2, smoothstep(0.7, 1.0, d) * 0.4);
}

// La boca del final a la ida: el agujero negro del Void (m: en su disco unidad)
vec3 blackHole(vec2 m) {
    float d = length(m);
    float ang = atan(m.y, m.x);
    // detrás, el cielo del Void doblado alrededor (lente gravitatoria)
    vec2 lm = m * (1.0 + 0.16 / max(d * d, 0.05));
    vec3 bg = pano(3.0, aim(3.0, normalize(vec3(-lm.x * 0.9, lm.y * 0.9, 1.0)))) * 0.75;
    float shadow = smoothstep(0.425, 0.4, d);
    // el disco de acreción: una banda inclinada que pasa por delante y su imagen doblada alrededor de la sombra
    float y = m.y - 0.16 * m.x;
    float front = exp(-pow(y / (0.028 + 0.045 * d), 2.0)) * (1.0 - smoothstep(0.7, 1.0, d));
    float arc = exp(-pow((d - 0.5) / 0.045, 2.0)) * (1.0 - 0.6 * front);
    float sw = ang + WTime * 0.9 / max(d, 0.3);
    float n = fbm(vec3(cos(sw) * d * 5.0, sin(sw) * d * 5.0, WTime * 0.3), 0.02);
    float dop = 1.0 + 0.7 * (-m.x / max(d, 1e-3));
    vec3 disk = mix(vec3(1.0, 0.5, 0.18), vec3(1.0, 0.93, 0.78), n) * (0.55 + 0.9 * n) * dop;
    vec3 c = bg * (1.0 - shadow);
    c += disk * (front + arc * 0.85) * 1.7;
    // el anillo de fotones
    c += vec3(1.0, 0.85, 0.6) * exp(-pow((d - 0.432) / 0.008, 2.0)) * 2.2;
    return c;
}

// Ascuas, chispas y motas de luz que pasan volando (redondas y suaves)
vec3 motes(float r, float a, float w, float world) {
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
    float px = 1.0 / ScreenSize.y;
    // la cámara se balancea un poco
    vec2 c0 = vec2(0.045 * sin(t * 0.9) + 0.02 * sin(t * 2.3), 0.035 * cos(t * 0.7) + 0.015 * sin(t * 1.7)) * Open;
    // el centro del túnel a la profundidad que se ve en cada píxel (serpentea: se busca en tres pasos)
    vec2 c = c0;
    vec2 q = p - c;
    float r = max(length(q), 1e-4);
    for (int i = 0; i < 3; i++) {
        c = c0 + bendAt(K / r);
        q = p - c;
        r = max(length(q), 1e-4);
    }
    float a = atan(q.y, q.x);
    float w = K / r + Travel;
    // la boca que tienes delante: la de la puerta siguiente o, tras la última, la del final
    float g = Travel < Gates.x ? Gates.x : (Travel < Gates.y ? Gates.y : (Travel < Gates.z ? Gates.z : Gates.z + END_GAP));
    float idx = Travel < Gates.x ? 0.0 : (Travel < Gates.y ? 1.0 : (Travel < Gates.z ? 2.0 : 3.0));
    float dz = g - Travel;
    // (la del final no pasa de media pantalla hasta que llegas: entonces crece y de ella sale la luz)
    if (idx > 2.5) dz = max(dz, 1.2) / (1.0 + 4.0 * smoothstep(0.0, 0.75, Exit));
    float rg = K / max(dz, 0.02);
    vec2 m = (p - c0 - bendAt(dz)) / rg;
    float dm = length(m);
    float to = idx > 2.5 ? (Back > 0.5 ? 0.0 : 3.0) : worldAt(g + 0.01);
    vec3 mc = mouthColor(idx, to);
    // las paredes (las de más allá de la boca no se ven: se quedan en su borde)
    float wl = min(w, Travel + dz - 0.01);
    float world = worldAt(wl);
    float roll = 0.16 * t + 0.25 * sin(t * 0.37);
    float u = fract(a / TAU + roll / TAU + TWIST * wl);
    vec3 col = tunnelWall(world, u, wl, r);
    // la luz de la boca tiñe las paredes que tiene cerca; las de tu lado, algo más oscuras
    col = mix(col, mc * 1.3, exp(-max(Travel + dz - wl, 0.0) * 0.5) * 0.5);
    col *= 0.7 + 0.4 * (1.0 - smoothstep(0.25, 1.0, r));
    // rayas de velocidad (siguen la espiral)
    float lane = floor(u * 110.0);
    float hs = hash11(lane + 17.0);
    float dash = exp(-fract(wl * 0.35 + hs * 7.0) * 12.0) * step(0.88, hs) * smoothstep(0.08, 0.5, r);
    col += mix(vec3(1.0), fogColor(world), 0.3) * dash;
    col += motes(r, a + roll, wl, world);
    // la boca: la dimensión siguiente (o el agujero negro del Void), su anillo y su halo
    float e = max(px / rg, 1e-4);
    float inside = smoothstep(1.0 + e, 1.0 - e, dm);
    vec3 inner = idx > 2.5 && Back < 0.5 ? blackHole(m) : sphereView(to, m, mc);
    col = mix(col, inner, inside);
    float sd = (dm - 1.0) * rg;
    float big = smoothstep(0.004, 0.03, rg);
    col += mc * (exp(-pow(sd / max(0.004, 1.2 * px), 2.0)) * 1.3 + exp(-abs(sd) / 0.04) * 0.4) * big;
    // (un poco de aberración: rojo hacia fuera y azul hacia dentro, en los bordes)
    float edge = smoothstep(0.35, 0.9, length(p));
    col *= vec3(1.0 + 0.06 * edge, 1.0, 1.0 - 0.04 * edge);
    // al cruzar una puerta, fogonazo de su color
    for (int i = 0; i < 3; i++) {
        float gi = i == 0 ? Gates.x : (i == 1 ? Gates.y : Gates.z);
        float x = Travel - gi;
        col = mix(col, mouthColor(float(i), worldAt(gi + 0.01)) * 1.3, exp(-x * x * 18.0) * 0.7);
    }
    // viñeta
    col *= 1.0 - 0.4 * smoothstep(0.55, 1.1, length(p));
    // la salida: la luz llena la pantalla (a la ida, sale del anillo del agujero)
    vec3 dest = Back > 0.5 ? vec3(1.0, 0.96, 0.88) : vec3(1.0, 0.9, 0.66);
    vec2 pe = p - c0 - bendAt(dz);
    float er = Back > 0.5 ? Exit * 1.4 : smoothstep(0.45, 1.0, Exit) * 1.6;
    float light = smoothstep(er, er - 0.45, length(pe)) * step(0.001, Exit);
    col = mix(col, dest * 1.4, light);
    col = mix(col, dest * 1.5, smoothstep(0.85, 1.05, Exit));
    // el desgarro que se abre al empezar
    vec2 qo = p - c0;
    float ang = atan(qo.y, qo.x);
    float jag = 0.05 * (fbm(vec3(cos(ang) * 2.0, sin(ang) * 2.0, t * 1.3), 0.0) - 0.5) + 0.02 * sin(ang * 11.0 + t * 4.0);
    float R = 0.02 + 1.3 * Open * Open;
    float dd = length(qo * vec2(1.0, 1.0 + 1.6 * (1.0 - Open))) - R * (1.0 + jag);
    float ins = smoothstep(0.012, -0.012, dd);
    float rim = exp(-abs(dd) * 45.0) * (1.0 - smoothstep(0.85, 1.0, Open));
    vec3 rimc = Back > 0.5 ? vec3(1.0, 0.9, 0.7) : vec3(0.85, 0.6, 1.0);
    float alpha = clamp(ins + rim, 0.0, 1.0) * (1.0 - smoothstep(1.0, 1.9, Exit));
    // (sin premultiplicar: lo mezcla ONE_MINUS_SRC_ALPHA con el mundo de detrás)
    vec3 outc = col * ins + rimc * rim * 1.6;
    vec3 rgb = alpha > 0.001 ? outc / alpha : vec3(0.0);
    fragColor = vec4(min(rgb, vec3(4.0)), alpha);
}
