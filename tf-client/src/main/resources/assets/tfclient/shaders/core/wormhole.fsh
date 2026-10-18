#version 150

// El viaje por el agujero de gusano (TF Client 1.3.91; el dueño: «una escena como si viajara por un agujero de gusano
// con la realidad alterada, con imágenes del Overworld, el Nether y el End; y de regreso lo mismo pero End, Nether y
// Overworld; algo súper épico»). Lo dibuja TravelFx sobre toda la pantalla (y sobre la de carga al cambiar de mundo).
//
//  - La realidad se rasga (Open): en el centro se abre un desgarro de borde vivo que crece hasta llenar la pantalla;
//    fuera, el mundo (que RiftWarp deforma).
//  - El túnel: cada píxel a la distancia r del centro ve la pared a la profundidad K/r (más el camino recorrido,
//    Travel); el túnel se retuerce (twist) y su centro se mueve (se curva).
//  - Las paredes de cada mundo, con texturas del propio Minecraft (WormholeTextures las junta): el Overworld, sus
//    bloques (hierba, hojas, cerezo, troncos) con ventanas a su cielo; el Nether, netherrack, ladrillo, nilio y
//    piedra luminosa con ríos de lava animada; el End, piedra del End, púrpur y obsidiana con ventanas a las estrellas
//    del portal del End sobre su cielo.
//  - Bloques de verdad, en 3D, que pasan girando (los de cada mundo: la realidad hecha trozos).
//  - Entre mundo y mundo, una puerta (Gates): el marco (obsidiana para el Nether, piedra del End con ojos para el End)
//    y su membrana (el portal del Nether que gira, o las estrellas), que crecen al acercarse; al cruzarla, fogonazo.
//  - Rayas de velocidad, aberración cromática, niebla del color de cada mundo hacia el fondo, que brilla.
//  - La salida (Exit): la luz del fondo (lila al Void, blanca y cálida al volver) llena la pantalla y se va.
// A la ida: Overworld → Nether → End; a la vuelta (Back): End → Nether → Overworld.

uniform sampler2D Sampler0;   // panorama del menú: las 4 caras del horizonte seguidas
uniform sampler2D Sampler1;   // 4×4 losetas de bloques: filas 0-1 Overworld, 2 Nether, 3 End
uniform sampler2D Sampler2;   // lava (tira de fotogramas)
uniform sampler2D Sampler3;   // portal del Nether (tira)
uniform sampler2D Sampler4;   // estrellas del portal del End
uniform sampler2D Sampler5;   // cielo del End
uniform float WTime;          // segundos
uniform float Travel;         // camino recorrido por el túnel
uniform vec2 Gates;           // dónde están las dos puertas (en camino)
uniform float Open;           // 0..1 el desgarro se abre
uniform float Exit;           // 0..1 la luz del fondo llena la pantalla; 1..2 se va
uniform float Back;           // 1 = la vuelta
uniform vec2 Frames;          // fotogramas de la lava y del portal
uniform vec2 ScreenSize;

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265;
const float K = 0.55;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x), mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

// Una loseta del atlas (0..15) en f (0..1)
vec3 tile(float idx, vec2 f) {
    vec2 cell = vec2(mod(idx, 4.0), floor(idx / 4.0));
    return texture(Sampler1, (cell + clamp(fract(f), 0.002, 0.998)) / 4.0).rgb;
}

// Un fotograma de una tira animada
vec3 strip(sampler2D s, float frames, vec2 f, float fps) {
    float fr = mod(floor(WTime * fps), max(frames, 1.0));
    return texture(s, vec2(fract(f.x), (fr + clamp(fract(f.y), 0.002, 0.998)) / max(frames, 1.0))).rgb;
}

// Bloques: un poco de relieve (bordes más oscuros) y brillo distinto por loseta
vec3 block(float idx, vec2 tq, float seed) {
    vec2 ti = floor(tq);
    vec2 f = fract(tq);
    vec3 c = tile(idx, f) * (0.72 + 0.36 * hash12(ti + seed));
    vec2 e = min(f, 1.0 - f);
    return c * (0.78 + 0.22 * smoothstep(0.0, 0.08, min(e.x, e.y)));
}

vec3 overworld(float u, float w) {
    // bloques del Overworld (hierba, hojas, cerezos, troncos, tierra, piedra) con ventanas a su cielo del atardecer
    vec2 tq = vec2(u * 24.0, w * 1.4);
    vec2 ti = floor(tq);
    float h = hash12(ti);
    float id = h < 0.24 ? 0.0 : (h < 0.36 ? 4.0 : (h < 0.52 ? 6.0 : (h < 0.62 ? 5.0 : (h < 0.72 ? 7.0 : (h < 0.84 ? 2.0 : (h < 0.93 ? 1.0 : 3.0))))));
    vec3 c = block(id, tq, 5.0);
    float win = smoothstep(0.55, 0.62, noise2(vec2(u * 6.0, w * 0.3 + 4.0)));
    vec3 sky = texture(Sampler0, vec2(fract(u * 1.0 + 0.1), 0.08 + 0.32 * fract(w * 0.05))).rgb * 1.15;
    return mix(c, sky, win);
}

vec3 nether(float u, float w) {
    vec2 tq = vec2(u * 24.0, w * 1.5);
    vec2 ti = floor(tq);
    float h = hash12(ti);
    float id = h < 0.58 ? 8.0 : (h < 0.8 ? 9.0 : (h < 0.93 ? 11.0 : 10.0));
    vec3 c = block(id, tq, 3.0);
    if (id == 10.0) c *= 1.9;
    // ríos de lava que corren a lo largo
    float lane = abs(fract(u * 5.0 + 0.12 * sin(w * 0.35 + u * 9.0)) - 0.5);
    float lava = smoothstep(0.11, 0.06, lane);
    vec3 lv = strip(Sampler2, Frames.x, vec2(u * 24.0, w * 0.8 + WTime * 0.35), 7.0) * 1.7;
    c = mix(c, lv, lava);
    c += vec3(1.0, 0.32, 0.04) * smoothstep(0.3, 0.06, lane) * 0.35;
    return c;
}

vec3 endWorld(float u, float w) {
    vec2 tq = vec2(u * 22.0, w * 1.3);
    vec2 ti = floor(tq);
    float h = hash12(ti);
    float id = h < 0.55 ? 12.0 : (h < 0.8 ? 13.0 : (h < 0.93 ? 14.0 : 15.0));
    vec3 c = block(id, tq, 7.0);
    if (id == 15.0) c *= 1.5;
    // ventanas al vacío del End: sus estrellas en dos capas sobre su cielo
    float win = smoothstep(0.5, 0.58, noise2(vec2(u * 7.0, w * 0.32 - WTime * 0.05)));
    vec3 sky = texture(Sampler5, vec2(u * 4.0, w * 0.2)).rgb * vec3(0.35, 0.18, 0.5);
    vec3 s1 = texture(Sampler4, vec2(u * 2.0 + WTime * 0.01, w * 0.12)).rgb * vec3(0.35, 0.95, 0.85);
    vec3 s2 = texture(Sampler4, vec2(u * 3.3 - WTime * 0.017, w * 0.19 + 0.37)).rgb * vec3(0.75, 0.45, 1.0);
    vec3 vd = sky + (s1 + s2) * 1.2;
    return mix(c, vd, win);
}

vec3 worldWall(float world, float u, float w) {
    if (world < 0.5) return overworld(u, w);
    if (world < 1.5) return nether(u, w);
    return endWorld(u, w);
}

vec3 fogColor(float world) {
    if (world < 0.5) return vec3(1.0, 0.74, 0.5);
    if (world < 1.5) return vec3(1.0, 0.33, 0.07);
    return vec3(0.5, 0.28, 0.85);
}

// Qué mundo hay a esa profundidad
float worldAt(float w) {
    float k = w < Gates.x ? 0.0 : (w < Gates.y ? 1.0 : 2.0);
    return Back > 0.5 ? 2.0 - k : k;
}

// La puerta i: ¿la del Nether (true) o la del End?
bool netherGate(float i) {
    return Back > 0.5 ? i > 0.5 : i < 0.5;
}

// Lo que se ve a la distancia r del centro, en el ángulo a (sin aberración)
vec3 scene(float r, float a) {
    float w = K / max(r, 0.004) + Travel;
    float u = a / (2.0 * PI) + 0.045 * w + 0.025 * WTime;
    float world = worldAt(w);
    vec3 col = worldWall(world, u, w);
    // las puertas: el marco (la pared a esa profundidad) y, más allá, su membrana
    for (int i = 0; i < 2; i++) {
        float g = i == 0 ? Gates.x : Gates.y;
        bool ng = netherGate(float(i));
        float dg = w - g;
        float frame = smoothstep(0.75, 0.45, abs(dg));
        if (frame > 0.0) {
            vec2 tq = vec2(u * 30.0, w * 2.0);
            vec3 fc = ng ? block(14.0, tq, 9.0) * 0.9 : block(12.0, tq, 9.0);
            if (!ng && mod(floor(tq.x), 4.0) == 0.0) fc = mix(fc, vec3(0.2, 0.9, 0.7), 0.6);
            col = mix(col, fc, frame);
        }
        if (g > Travel && dg > 0.0) {
            // la membrana, vista de frente: un disco de radio K/(g − Travel)
            float rg = K / (g - Travel);
            vec2 pm = vec2(cos(a), sin(a)) * r / rg;
            vec3 mc = ng ? strip(Sampler3, Frames.y, pm * 1.5 + 0.5, 12.0) * vec3(1.2, 0.9, 1.4)
                         : (texture(Sampler4, pm * 0.8 + WTime * 0.02).rgb * vec3(0.4, 1.0, 0.9) + vec3(0.02, 0.05, 0.08));
            col = mix(col, mc, 0.55);
        }
    }
    // niebla del color del mundo hacia el fondo, que brilla; y donde las losetas ya son más pequeñas que un par de
    // píxeles (hacia el centro), su color medio (sin ruido ni rayas)
    float depth = w - Travel;
    float f = 1.0 - exp(-depth * 0.08);
    float px = 1.0 / ScreenSize.y;
    float tilePx = min(r * r / (K * 1.5) , r * 2.0 * PI / 26.0) / px;
    float aaf = 1.0 - smoothstep(1.5, 4.0, tilePx);
    col = mix(col, fogColor(world) * 0.9, aaf * 0.85);
    col = mix(col, fogColor(world) * 1.35, f * 0.85);
    // rayas de velocidad
    float lane = floor(fract(a / (2.0 * PI)) * 110.0);
    float hs = hash11(lane + 17.0);
    float dash = exp(-fract(w * 0.35 + hs * 7.0) * 12.0) * step(0.86, hs) * smoothstep(0.08, 0.5, r);
    col += mix(vec3(1.0), fogColor(world), 0.3) * dash * 1.4;
    return col;
}

// Bloques de verdad que pasan girando por el túnel (la realidad se rompe en trozos): cubos en 3D con las texturas de
// cada mundo (hierba, troncos, hojas y cerezo; netherrack, ladrillo, nilio y piedra luminosa; piedra del End, púrpur
// y obsidiana). (1.3.91, el dueño: nada de «imágenes» ni «fotos» que salten: bloques.) Cada píxel lanza su rayo
// desde la cámara y mira con qué cubo choca primero (caja girada: prueba de las tres franjas).
const int CUBES = 40;

// Las losetas de un cubo según su mundo y su tipo: x = arriba, y = lados, z = abajo
vec3 cubeTiles(float world, float h) {
    if (world < 0.5) {
        if (h < 0.34) return vec3(0.0, 1.0, 2.0);       // bloque de hierba
        if (h < 0.5) return vec3(5.0);                  // tronco de roble
        if (h < 0.68) return vec3(4.0);                 // hojas de roble
        if (h < 0.84) return vec3(6.0);                 // hojas de cerezo
        return vec3(3.0);                               // piedra
    }
    if (world < 1.5) {
        if (h < 0.4) return vec3(8.0);                  // netherrack
        if (h < 0.62) return vec3(9.0);                 // ladrillo del Nether
        if (h < 0.82) return vec3(11.0, 8.0, 8.0);      // nilio carmesí
        return vec3(10.0);                              // piedra luminosa
    }
    if (h < 0.45) return vec3(12.0);                    // piedra del End
    if (h < 0.7) return vec3(13.0);                     // púrpur
    if (h < 0.88) return vec3(14.0);                    // obsidiana
    return vec3(15.0);                                  // obsidiana llorosa
}

mat3 rotAxis(vec3 ax, float an) {
    float c = cos(an), s = sin(an), k = 1.0 - c;
    return mat3(c + ax.x * ax.x * k, ax.y * ax.x * k + ax.z * s, ax.z * ax.x * k - ax.y * s,
                ax.x * ax.y * k - ax.z * s, c + ax.y * ax.y * k, ax.z * ax.y * k + ax.x * s,
                ax.x * ax.z * k + ax.y * s, ax.y * ax.z * k - ax.x * s, c + ax.z * ax.z * k);
}

vec4 blocks(vec2 q) {
    vec3 rd = normalize(vec3(q, 1.0));
    float best = 1e9;
    vec4 res = vec4(0.0);
    for (int i = 0; i < CUBES; i++) {
        float fi = float(i);
        // (los que ya pasaron vuelven a salir al fondo: si se llega tarde, siguen pasando)
        float dz = 1.6 + fi * 0.85 + 0.4 * hash11(fi + 41.0) - Travel;
        float cyc = dz < 0.1 ? ceil((0.1 - dz) / (float(CUBES) * 0.85)) : 0.0;
        dz += cyc * float(CUBES) * 0.85;
        if (dz > 15.0) continue;
        float zc = Travel + dz;
        float sd = fi + cyc * 57.0;
        float th = fi * 2.39996 + 0.6 + cyc * 1.3;
        float rho = (0.38 + 0.4 * hash11(sd + 3.0)) * K;
        vec3 cc = vec3(cos(th) * rho, sin(th) * rho, dz);
        float hs = (0.06 + 0.04 * hash11(sd + 13.0)) * K;
        // (rápido: si el rayo pasa lejos del cubo, ni se mira)
        vec3 oc = cc;
        float tc = dot(oc, rd);
        vec3 off = rd * tc - oc;
        if (dot(off, off) > 3.1 * hs * hs) continue;
        vec3 ax = normalize(vec3(hash11(sd + 5.0) - 0.5, hash11(sd + 6.0) - 0.5, hash11(sd + 7.0) - 0.5) + 0.001);
        mat3 m = rotAxis(ax, WTime * (0.6 + 1.2 * hash11(sd + 8.0)) + sd * 1.7);
        mat3 mt = transpose(m);
        vec3 o = mt * (-cc);
        vec3 d = mt * rd;
        vec3 inv = 1.0 / d;
        vec3 t1 = (-hs - o) * inv;
        vec3 t2 = (hs - o) * inv;
        vec3 tmn = min(t1, t2);
        vec3 tmx = max(t1, t2);
        float tn = max(max(tmn.x, tmn.y), tmn.z);
        float tf = min(min(tmx.x, tmx.y), tmx.z);
        if (tf < max(tn, 0.0) || tn > best) continue;
        best = tn;
        vec3 pl = (o + d * tn) / hs;               // en el cubo, -1..1
        float world = worldAt(zc);
        vec3 ids = cubeTiles(world, hash11(sd + 23.0));
        vec2 uv;
        float idx, shade;
        if (tn == tmn.y) {
            // arriba o abajo
            uv = pl.xz * 0.5 + 0.5;
            bool up = pl.y > 0.0;
            idx = up ? ids.x : ids.z;
            shade = up ? 1.0 : 0.5;
        } else if (tn == tmn.x) {
            uv = vec2(pl.z * 0.5 + 0.5, 0.5 - pl.y * 0.5);
            idx = ids.y;
            shade = 0.6;
        } else {
            uv = vec2(pl.x * 0.5 + 0.5, 0.5 - pl.y * 0.5);
            idx = ids.y;
            shade = 0.8;
        }
        vec3 c = tile(idx, uv);
        vec2 e = min(uv, 1.0 - uv);
        c *= 0.8 + 0.2 * smoothstep(0.0, 0.07, min(e.x, e.y));
        bool glow = idx == 10.0 || idx == 15.0;
        // la luz del fondo del túnel les da de frente y el color del mundo por detrás
        vec3 lit = c * (glow ? 1.7 : shade * 1.15) + fogColor(world) * 0.12;
        lit = mix(lit, fogColor(world) * 1.2, smoothstep(5.0, 14.0, dz) * 0.85);
        float a = smoothstep(0.1, 0.35, dz) * smoothstep(15.0, 11.0, dz);
        res = vec4(lit, a);
    }
    return res;
}

// Motas de cada mundo que pasan volando (pétalos, ascuas, chispas del End)
vec3 motes(float r, float a) {
    float w = K / max(r, 0.004) + Travel;
    float world = worldAt(w);
    vec2 g = vec2(fract(a / (2.0 * PI)) * 70.0, w * 2.2);
    vec2 gi = floor(g);
    float h = hash12(gi + 31.0);
    if (h < 0.93) return vec3(0.0);
    vec2 cpos = vec2(hash12(gi + 7.0), hash12(gi + 11.0)) * 0.8 + 0.1;
    float d = length((fract(g) - cpos) * vec2(1.0, 0.6));
    float b = smoothstep(0.22, 0.0, d) * smoothstep(0.05, 0.3, r);
    vec3 col = world < 0.5 ? vec3(1.0, 0.62, 0.82) : (world < 1.5 ? vec3(1.0, 0.55, 0.12) : vec3(0.8, 0.45, 1.0));
    return col * b * 1.6;
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
    // aberración cromática: cada color, un poco más cerca o más lejos
    vec3 col;
    col.r = scene(r * 1.012, a).r;
    vec3 mid = scene(r, a);
    col.g = mid.g;
    col.b = scene(r * 0.988, a).b;
    col += motes(r, a);
    vec4 bl = blocks(q);
    col = mix(col, bl.rgb, bl.a);
    // al cruzar una puerta, fogonazo
    for (int i = 0; i < 2; i++) {
        float g = i == 0 ? Gates.x : Gates.y;
        float x = Travel - g;
        float fl = exp(-x * x * 18.0);
        col = mix(col, netherGate(float(i)) ? vec3(0.75, 0.35, 1.0) : vec3(0.5, 1.0, 0.9), fl * 0.7);
    }
    // viñeta
    col *= 1.0 - 0.4 * smoothstep(0.55, 1.1, length(p));
    // la salida: la luz del fondo llena la pantalla
    vec3 dest = Back > 0.5 ? vec3(1.0, 0.96, 0.88) : vec3(0.88, 0.78, 1.0);
    float er = Exit * 1.4;
    float light = smoothstep(er, er - 0.45, r) * step(0.001, Exit);
    col = mix(col, dest * 1.4, light);
    col = mix(col, dest * 1.5, smoothstep(0.85, 1.05, Exit));
    // el desgarro que se abre al empezar
    float ang = a;
    float jag = 0.05 * (noise2(vec2(ang * 2.5, t * 1.3)) - 0.5) + 0.02 * sin(ang * 11.0 + t * 4.0);
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
