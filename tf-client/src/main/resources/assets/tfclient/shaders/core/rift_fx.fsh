#version 150

// El desgarro de la grieta 3.0 y su círculo de runas (TF Client 1.3.88). Sin texturas: todo sale de cuentas, así que
// el borde es suave a cualquier tamaño (el dueño: «bordes dentados»). Sin atan en el remolino (1.3.89: hacía una raya).
//
// Color del vértice: r = semilla, g = modo (0 desgarro, 1 círculo del suelo), b = cuánto está abierto / 1.4,
// a = intensidad / 2. Salida con alfa premultiplicado (ONE, 1-SRC_ALPHA): dentro de la rasgadura, el vacío opaco;
// el borde y el halo suman luz sin tapar.
//
// El cuadro del desgarro mide QW × QH bloques (RiftRenderer.TEAR_QW/QH = tools/bosses/grieta3.py TEAR) y la rasgadura,
// TH de alto y TW de ancho como mucho.

uniform float GameTime;

in vec4 vColor;
in vec2 vUV;

out vec4 fragColor;

const float QW = 17.0;
const float QH = 27.0;
const float TH = 20.0;
const float TW = 7.5;
const float RING_R = 13.5;

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

float noise1(float x) {
    float i = floor(x);
    float f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(hash11(i), hash11(i + 1.0), f);
}

float fbm1(float x) {
    return 0.5 * noise1(x) + 0.25 * noise1(x * 2.1 + 3.7) + 0.125 * noise1(x * 4.3 + 9.1) + 0.0625 * noise1(x * 8.7 + 1.3);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm2(vec2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * noise2(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return s;
}

vec3 starfield(vec2 p, float t) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float h = hash12(i);
    if (h > 0.12) return vec3(0.0);
    vec2 c = vec2(hash12(i + 7.0), hash12(i + 13.0)) * 0.6 + 0.2;
    float d = length(f - c);
    float tw = 0.55 + 0.45 * sin(t * (1.0 + 4.0 * hash12(i + 3.0)) + h * 80.0);
    float b = smoothstep(0.16, 0.0, d);
    return mix(vec3(0.85, 0.75, 1.0), vec3(1.0, 0.8, 0.95), hash12(i + 21.0)) * b * b * tw * 1.6;
}

vec4 tear(vec2 uv, float seed, float open, float inten, float t) {
    vec2 p = vec2((uv.x - 0.5) * QW, (uv.y - 0.5) * QH);
    float y = p.y;
    float hy = y / (TH * 0.5);
    // la línea del centro ondula despacio
    float cx = 0.55 * sin(y * 0.31 + seed + t * 0.35) + 0.25 * sin(y * 0.93 - t * 0.6 + seed * 2.0);
    // ancho: forma de lente con el borde dentado (fractal, pero suave: se dibuja con antialias)
    float prof = pow(max(0.0, 1.0 - hy * hy), 0.6);
    // (sin dientes finos: el dueño se quejó de los bordes dentados; ondas amplias que se mueven despacio)
    float jag = (fbm1(y * 0.55 + seed * 3.0 + t * 0.05) - 0.5) * 1.6 + (noise1(y * 1.4 - t * 0.3 + seed) - 0.5) * 0.45;
    float breathe = 1.0 + 0.07 * sin(t * 1.3 + y * 0.2);
    float hw = max(0.0, (TW * 0.5 + jag) * prof) * open * breathe;
    float d = abs(p.x - cx) - hw;
    if (abs(hy) >= 1.0) d = max(d, (abs(hy) - 1.0) * TH * 0.5 + 0.2);
    float aa = fwidth(d) * 1.2 + 0.015;
    float inside = 1.0 - smoothstep(-aa, aa, d);
    // dentro: el vacío que gira, estrellas a dos profundidades y el ojo del vórtice
    vec2 q = vec2(p.x - cx, y);
    vec2 qe = q * vec2(1.5, 0.42);
    float r = length(qe);
    // (1.3.89) el remolino ya no sale del ángulo (atan): su corte dibujaba una raya recta del ojo hacia un lado y el ojo
    // era un «estallido» de rayos (el dueño: «esa línea parece vidrio quebrado»). Ahora se gira el plano más cuanto más
    // cerca del ojo (un torbellino) y el ruido se lee ahí: sin cortes ni puntos raros.
    float tw = 2.6 / (1.0 + r * 0.45) - t * 0.35;
    float ct = cos(tw);
    float st = sin(tw);
    vec2 w = vec2(ct * qe.x - st * qe.y, st * qe.x + ct * qe.y);
    float sw = fbm2(w * 0.55 + vec2(seed, seed * 0.37) + vec2(0.0, t * 0.05));
    vec3 voidc = mix(vec3(0.02, 0.004, 0.05), vec3(0.42, 0.10, 0.70), smoothstep(0.25, 0.85, sw));
    voidc += vec3(0.12, 0.02, 0.22) * (1.0 - smoothstep(0.0, 1.0, r * 0.12));
    voidc += vec3(0.85, 0.40, 1.0) * exp(-r * 0.5) * (0.6 + 0.4 * sin(t * 2.1)) * 1.1;
    voidc += starfield(q * 2.0 + vec2(0.0, t * 0.12), t) * 0.9;
    voidc += starfield(q * 4.3 + vec2(t * 0.07, 3.0), t) * 0.5;
    // más oscuro junto al borde: se mira hacia dentro
    voidc *= mix(0.55, 1.0, smoothstep(0.0, -0.9, d));
    // el borde: una línea caliente casi blanca, una banda magenta y chispas que corren
    float rim = exp(-abs(d) * 6.0);
    float band = exp(-abs(d) * 1.6);
    float spark = 0.85 + 0.3 * noise1(y * 1.3 - t * 2.5 + seed);
    vec3 rimc = (vec3(1.0, 0.88, 1.0) * rim * 1.7 + vec3(0.85, 0.22, 1.0) * band * 0.85) * spark;
    // el halo de fuera
    float od = max(d, 0.0);
    vec3 glowc = vec3(0.55, 0.14, 0.95) * (exp(-od * 0.6) * 0.5 + exp(-od * 0.17) * 0.2);
    glowc *= smoothstep(1.25, 0.55, abs(hy));
    float border = smoothstep(0.0, 0.1, uv.x) * smoothstep(1.0, 0.9, uv.x) * smoothstep(0.0, 0.07, uv.y) * smoothstep(1.0, 0.93, uv.y);
    vec3 col = voidc * inside + (rimc * (open > 0.02 ? 1.0 : 0.0) + glowc * (1.0 - inside)) * inten * border;
    return vec4(col, inside);
}

vec4 ring(vec2 uv, float seed, float inten, float t) {
    vec2 p = (uv - 0.5) * 2.0 * RING_R;
    float r = length(p);
    float a = atan(p.y, p.x);
    float l1 = exp(-abs(r - 12.95) * 10.0);
    float l2 = exp(-abs(r - 11.45) * 10.0);
    float l3 = exp(-abs(r - 10.1) * 14.0) * 0.6;
    // runas: 48 casillas que giran despacio, con trazos al azar dentro
    // (vuelta completa = 48 casillas exactas: sin costura donde atan salta de -π a π)
    float n = 48.0;
    float rot = fract((a + t * 0.05) / 6.2831853) * n;
    float cell = floor(rot);
    float fr = fract(rot);
    float rr = (r - 11.6) / 1.2;
    float h1 = hash12(vec2(cell, seed));
    float h2 = hash12(vec2(cell, seed + 7.0));
    float g = 0.0;
    float wide = 0.07;
    g += smoothstep(wide, 0.0, abs(fr - 0.5)) * step(0.25, h1) * smoothstep(0.0, 0.1, rr) * smoothstep(1.0, 0.9, rr);
    g += smoothstep(0.08, 0.0, abs(rr - (0.25 + 0.5 * h2))) * smoothstep(0.42, 0.25, abs(fr - 0.5));
    g += smoothstep(0.08, 0.0, abs(rr - (0.75 - 0.4 * h1))) * smoothstep(0.42, 0.3, abs(fr - 0.5)) * step(0.5, h2);
    g *= step(0.0, rr) * step(rr, 1.0);
    float pulse = 0.55 + 0.45 * sin(t * 1.4 - cell * 0.45);
    vec3 col = vec3(0.72, 0.26, 1.0) * (l1 + l2 + l3) * 0.9 + vec3(0.95, 0.5, 1.0) * g * pulse;
    // charco de luz hacia el centro
    col += vec3(0.32, 0.07, 0.55) * exp(-r * 0.2) * 0.3;
    col *= smoothstep(RING_R, RING_R - 0.4, r);
    return vec4(col * inten, 0.0);
}

void main() {
    float t = GameTime * 1200.0;
    float seed = vColor.r * 100.0;
    float inten = vColor.a * 2.0;
    vec4 c = vColor.g > 0.5 ? ring(vUV, seed, inten, t) : tear(vUV, seed, vColor.b * 1.4, inten, t);
    fragColor = c;
}
