#version 150

// La grieta del cielo (TF Client 1.3.93; el dueño: «cuando coloque el NPC, una grieta grande se dibujará en el cielo,
// con una animación épica que asuste a todo el server, como que se rompe la dimensión»). La dibuja SkyRiftClient en
// un cuadro lejano que mira a la cámara (uv de -1 a 1; el alto es 1,4 veces el ancho):
//  - Grietas (Crack): el cielo se rompe como un cristal desde el centro, en ramas quebradas con horquillas, de luz
//    blanca y violeta, con un halo morado; de vez en cuando un rayo recorre alguna.
//  - El desgarro (Open): una herida vertical, de borde dentado y vivo, que se abre y deja ver el Void: sus nebulosas en
//    remolino, estrellas y, en el fondo, el agujero negro con su anillo de oro.
//  - Un aura oscura alrededor (el cielo se apaga cerca de la herida) y el fogonazo (Flash) al romperse.
//  - Cerrada (Open 0, Crack bajo) queda una cicatriz: la línea del desgarro y las grietas, apagadas.
// Todo con bordes suaves (lo que mide un píxel, fwidth): nada dentado. Vista previa sin juego:
// tools/void/grieta_cielo_preview.py.

uniform sampler2D Sampler0;   // las nebulosas del cielo del Void (grieta_cielo.png, equirectangular)
uniform float STime;          // segundos
uniform float Crack;          // 0..1 las grietas crecen desde el centro
uniform float Open;           // 0..1 el desgarro se abre
uniform float Flash;          // 0..1 fogonazo
uniform float Alpha;          // 0..1 todo (se apaga al cerrarse del todo)
uniform float AtFar;          // 1 = después del paquete de shaders: solo donde se ve el cielo

in vec2 uv;
in vec3 vDir;
out vec4 fragColor;

const float PI = 3.14159265;
const float TAU = 6.2831853;

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

float fbm2(vec2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * noise2(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return s / 0.9375;
}

// El eje del desgarro (algo en S) y su medio ancho a la altura y
float tearX(float y) {
    return 0.06 * sin(y * 2.2 + 0.7) + 0.025 * sin(y * 5.1 + 2.0);
}

float tearHalf(float y) {
    float k = clamp(1.0 - (y / 1.18) * (y / 1.18), 0.0, 1.0);
    return Open * 0.2 * pow(k, 0.7);
}

// Una rama de grieta: del centro hacia el ángulo th, quebrada; devuelve su luz (núcleo) y su halo
vec2 branch(vec2 p, float th, float len, float seed, float aa) {
    float r = length(p);
    if (r > len + 0.08 || r < 0.02) return vec2(0.0);
    float a = atan(p.y, p.x);
    // el ángulo de la rama a esa distancia: quebrada (ruido por tramos) y algo curvada
    float s = r * 6.0 + seed * 7.0;
    float wig = (noise2(vec2(s, seed * 3.1)) - 0.5) * 0.35 + (noise2(vec2(s * 3.0, seed)) - 0.5) * 0.08;
    float da = a - (th + wig * (0.4 + r));
    da = mod(da + PI, TAU) - PI;
    float lat = abs(da) * r;
    float tip = 1.0 - smoothstep(len * 0.75, len, r);
    float w = (0.004 + 0.012 * (1.0 - r / max(len, 0.01))) * tip;
    float core = smoothstep(w + aa, max(w - aa, 0.0), lat) * step(r, len);
    float halo = exp(-lat / (0.035 + 0.02 * tip)) * tip * step(r, len + 0.04);
    return vec2(core, halo);
}

void main() {
    gl_FragDepth = AtFar > 0.5 ? 0.9999995 : gl_FragCoord.z;
    vec2 p = uv * vec2(1.0, 1.4);
    float aa = max(length(fwidth(p)) * 0.8, 0.0008);
    float t = STime;
    float r = length(p);
    vec3 col = vec3(0.0);
    float alpha = 0.0;

    // El aura oscura: el cielo se apaga junto a la herida
    float aura = exp(-r * r * 1.6) * smoothstep(0.0, 0.6, Crack);
    col += vec3(0.05, 0.0, 0.09) * aura;
    alpha += aura * 0.55;

    // Las grietas: 11 ramas con dos horquillas cada una, que crecen con Crack
    vec2 cr = vec2(0.0);
    for (int i = 0; i < 11; i++) {
        float fi = float(i);
        float th = fi * TAU / 11.0 + (hash11(fi + 3.0) - 0.5) * 0.45;
        // (que ni las ramas ni sus horquillas lleguen al borde del cuadro)
        float len = (0.4 + 0.38 * hash11(fi + 11.0)) * Crack;
        // (las de arriba y abajo, más largas: siguen al desgarro)
        len *= 1.0 + 0.45 * abs(sin(th));
        // un rayo que la recorre de vez en cuando
        float flick = 0.65 + 0.35 * sin(t * (3.0 + hash11(fi) * 4.0) + fi * 2.1);
        float bolt = step(0.93, hash11(floor(t * 5.0) * 1.37 + fi * 7.1));
        float k = flick + bolt * 1.6;
        cr += branch(p, th, len, fi, aa) * k;
        for (int j = 0; j < 2; j++) {
            float fj = float(j);
            float at = len * (0.38 + 0.3 * fj);
            vec2 o = vec2(cos(th), sin(th)) * at;
            float th2 = th + (fj > 0.5 ? 0.55 : -0.5) * (0.7 + 0.6 * hash11(fi * 3.0 + fj));
            float len2 = len * (0.28 + 0.16 * hash11(fi + fj * 5.0));
            cr += branch(p - o, th2, len2, fi * 3.0 + fj + 20.0, aa) * k * 0.8;
        }
    }
    vec3 crackC = vec3(1.0, 0.95, 1.0) * cr.x * 1.5 + vec3(0.62, 0.25, 1.0) * cr.y * 0.9;
    col += crackC;
    alpha += clamp(cr.x + cr.y * 0.7, 0.0, 1.0);

    // El desgarro: por dentro, el Void
    float tx = tearX(p.y);
    float hw = tearHalf(p.y);
    float jag = (fbm2(vec2(p.y * 7.0, t * 0.35)) - 0.5) * 0.05 * smoothstep(0.0, 0.3, Open);
    float d = abs(p.x - tx) - hw * (1.0 + jag * 4.0) - jag * 0.2;
    float inside = smoothstep(aa, -aa, d) * step(0.001, Open);
    if (inside > 0.0) {
        // las nebulosas en remolino hacia el centro, el agujero negro y su anillo, estrellas
        vec2 q = vec2((p.x - tx) * 2.2, p.y);
        float qr = length(q);
        float qa = atan(q.y, q.x);
        float sw = 0.35 / (qr + 0.25) - t * 0.04;
        vec2 nuv = vec2(fract(qa / TAU + sw), clamp(0.3 + 0.35 * qr, 0.02, 0.98));
        vec3 neb = textureLod(Sampler0, nuv, 0.0).rgb;
        neb = pow(neb, vec3(1.3)) * 1.2;
        // (y un poco hacia donde miras: se mueve al mover la cámara, como algo que está detrás)
        vec3 dn = normalize(vDir);
        neb += textureLod(Sampler0, vec2(fract(atan(dn.z, dn.x) / TAU + 0.5), 0.5 - asin(clamp(dn.y, -1.0, 1.0)) / PI), 0.0).rgb * 0.25;
        float hole = 0.12 * smoothstep(0.2, 1.0, Open);
        float holeK = smoothstep(hole + aa * 2.0, hole - aa * 2.0, qr);
        float ring = exp(-pow((qr - hole * 1.15) / (hole * 0.12 + 0.004), 2.0)) * step(0.001, hole);
        float ang = qa + t * 0.25;
        vec3 ringC = vec3(1.0, 0.72, 0.36) * (0.75 + 0.35 * sin(ang * 3.0));
        vec2 sg = q * 26.0 + vec2(0.0, t * 0.3);
        float st = smoothstep(0.88, 1.0, hash12(floor(sg))) * smoothstep(0.35, 0.0, length(fract(sg) - 0.5));
        vec3 voidC = neb * (1.0 - holeK) + ringC * ring * 2.2 + vec3(0.9, 0.85, 1.0) * st * 0.8;
        voidC *= 1.0 - holeK;
        col = mix(col, voidC, inside);
        alpha = mix(alpha, 1.0, inside);
    }
    // el borde vivo del desgarro (blanco y violeta) y su resplandor
    float edge = exp(-abs(d) / (0.006 + aa)) * step(0.001, Open) * smoothstep(1.25, 0.9, abs(p.y));
    float glow = exp(-max(d, 0.0) / 0.09) * Open * smoothstep(1.3, 0.8, abs(p.y));
    col += vec3(1.0, 0.92, 1.0) * edge * 1.8 + vec3(0.7, 0.3, 1.0) * glow * 0.8;
    alpha += edge * 0.9 + glow * 0.45;
    // la cicatriz: cerrada, la línea del desgarro sigue brillando un poco
    float scar = exp(-abs(p.x - tx) / (0.004 + aa)) * smoothstep(1.15, 0.6, abs(p.y)) * (1.0 - Open) * smoothstep(0.0, 0.25, Crack);
    col += vec3(0.85, 0.6, 1.0) * scar * 1.2;
    alpha += scar * 0.8;

    // el fogonazo al romperse
    float fl = Flash * exp(-r * r * 3.0);
    col += vec3(1.0, 0.95, 1.0) * fl * 2.0;
    alpha += fl;

    alpha = clamp(alpha, 0.0, 1.0) * Alpha * smoothstep(1.0, 0.9, max(abs(uv.x), abs(uv.y)));
    vec3 rgb = alpha > 0.001 ? col / max(alpha, 0.001) : vec3(0.0);
    fragColor = vec4(min(rgb, vec3(4.0)), alpha);
}
