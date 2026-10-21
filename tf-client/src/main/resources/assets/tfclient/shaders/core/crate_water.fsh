#version 150

// (1.3.99) El agua del Santuario de las Hadas, con shader propio, limpia y continua (el dueño: nada de texturas quebradas,
// resquebrajadas ni cuadriculadas): el estanque, transparente (se ven los bloques del fondo) con manchas de luz suaves, el
// reflejo de las cascadas en destellos, el brillo del sol y espuma en las orillas; las cascadas, del mismo turquesa, con
// hilos finos que bajan, destellos pequeños y espuma abajo.
// data: r = orilla (0..1; en las cascadas, su altura / 16), g = cascada (1) o superficie (0), b = brillo del fondo
// (0..1), a = luz de la escena.
// fuv: en las cascadas, (a lo ancho, metros desde abajo).
in vec3 wp;
in vec4 data;
in vec2 fuv;

uniform vec3 CamPos;
uniform vec3 LightDir;
uniform vec3 BeamPos;
uniform float Time;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

out vec4 fragColor;

// el turquesa del agua, el mismo en el estanque y en las cascadas
const vec3 WATER_DEEP = vec3(0.01, 0.34, 0.46);
const vec3 WATER_LIGHT = vec3(0.05, 0.56, 0.66);

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float waves(vec2 p, float t) {
    float h = noise(p * 1.1 + vec2(t * 0.35, t * 0.22)) * 0.55;
    h += noise(p * 2.3 - vec2(t * 0.28, -t * 0.4)) * 0.3;
    h += noise(p * 5.1 + vec2(-t * 0.6, t * 0.5)) * 0.15;
    return h;
}

// cuánto se ve de una cascada en la dirección r desde p (para su reflejo en el estanque): los dos chorros de los lados
// (x de 3 a 6 a cada lado, delante en z = -7,3, de y = 1 a 5) y la escalera de agua con la cascada alta del fondo (x de -2 a 3,
// en z = -16, de y = 1 a 20); los mismos sitios que santuario.py
float cascadeSeen(vec3 p, vec3 r) {
    float k = 0.0;
    if (r.z < -0.02) {
        if (p.z > -7.3) {
            vec3 h = p + r * ((-7.3 - p.z) / r.z);
            float ax = abs(h.x - 0.5);
            k = max(k, smoothstep(2.3, 2.8, ax) * (1.0 - smoothstep(5.2, 5.7, ax)) * smoothstep(0.8, 1.3, h.y) * (1.0 - smoothstep(4.6, 5.2, h.y)));
        }
        vec3 h = p + r * ((-16.0 - p.z) / r.z);
        k = max(k, (1.0 - smoothstep(2.0, 2.8, abs(h.x - 0.5))) * smoothstep(0.8, 1.4, h.y) * (1.0 - smoothstep(18.0, 20.0, h.y)));
    }
    return k;
}

// cuánto le da el haz de sol (una columna desde la crate hacia el sol, con el borde marcado)
float beamAt(vec3 p) {
    vec3 d = p - (BeamPos + vec3(0.0, 0.6, 0.0));
    float off = length(d - LightDir * dot(d, LightDir));
    return (1.0 - smoothstep(1.7, 2.9, off)) * 0.9 + exp(-off * off / 18.0) * 0.3;
}

void main() {
    float t = Time / 20.0;
    vec3 v = normalize(CamPos - wp);
    float shore = data.r, fall = data.g, glow = data.b, light = data.a;
    float beam = beamAt(wp);
    vec3 col;
    float alpha;
    if (fall > 0.5) {
        // la cascada: agua limpia y continua que cae (el dueño: «nada de texturas quebradas, resquebrajadas ni
        // cuadriculadas; textura fina y completa; que caiga»): hilos finos verticales que bajan, sin celdas ni líneas, con
        // el mismo turquesa y la misma transparencia que el estanque, destellos pequeños y redondos y espuma abajo. De lejos
        // los hilos más finos se funden (no parpadean).
        float height = shore * 16.0;
        float y = fuv.y + t * 2.6;
        float fade = clamp(1.0 - fwidth(fuv.x) * 10.0, 0.0, 1.0);
        // (hilos suaves: ondas de seno a lo ancho, deformadas por un ruido grande; con ruido fino salían trazos oscuros
        // rectos —la rejilla del ruido—, el dueño: «líneas feas»)
        float wob = noise(vec2(fuv.x * 2.5, y * 0.25)) * 6.2832;
        float s1 = 0.5 + 0.5 * sin(fuv.x * 9.0 + wob + sin(y * 0.35) * 0.8);
        float s2 = 0.5 + 0.5 * sin(fuv.x * 21.0 - wob * 1.3 + y * 0.12);
        float strands = s1 * 0.65 + mix(0.5, s2, fade) * 0.35;
        col = mix(WATER_DEEP, WATER_LIGHT, smoothstep(0.15, 0.85, strands));
        col = mix(col, vec3(0.55, 0.95, 1.0), smoothstep(0.70, 0.95, strands) * 0.35);
        // destellos pequeños y redondos que bajan con el agua
        vec2 gp = vec2(fuv.x * 8.0, fuv.y * 8.0 + t * 7.0);
        vec2 gi = floor(gp), gf = fract(gp) - 0.5;
        float gd = length(gf - (vec2(hash(gi), hash(gi + 3.3)) - 0.5) * 0.6);
        float tw = step(0.86, hash(gi + 9.1)) * (0.5 + 0.5 * sin(t * 6.0 + hash(gi) * 40.0));
        col = mix(col, vec3(0.92, 1.0, 1.0), (1.0 - smoothstep(0.0, 0.07 + fwidth(gd), gd)) * tw * 0.7 * fade);
        // la espuma donde cae (el dueño: «la transición bien hecha, que no se vea cortado, así con todas»): abajo el agua se
        // vuelve espuma blanca con el borde de arriba irregular y que se mueve, con burbujas; alta en la cascada grande y en
        // los chorros de los lados, baja en los saltos de la escalera. Tapa la unión con el agua de abajo (que lleva su
        // espuma alrededor del pie y la bruma por delante: no hay línea recta)
        float edgeN = noise(vec2(fuv.x * 4.0, t * 2.5)) * 0.55 + noise(vec2(fuv.x * 11.0 + 3.0, t * 4.0)) * 0.45;
        float foamTop = mix(0.30, 0.95, smoothstep(1.5, 4.0, height)) * (0.6 + 0.8 * edgeN);
        float foam = 1.0 - smoothstep(foamTop * 0.45, foamTop, fuv.y + 0.125);
        foam *= 0.72 + 0.28 * noise(vec2(fuv.x * 14.0, fuv.y * 14.0 + t * 6.0));
        col = mix(col, vec3(0.92, 1.0, 1.0), foam * 0.92);
        col *= mix(0.86, 1.10, light);
        col += vec3(0.6, 1.0, 1.0) * beam * 0.10;
        alpha = 0.90 + 0.10 * foam;     // (translúcida; más no: se transparentaban las juntas del ladrillo de detrás como rayas)
    } else {
        // el estanque: agua transparente y limpia, del mismo turquesa que las cascadas, a través de la que se ven los
        // bloques de verdad del fondo; manchas de luz suaves que se mueven (sin líneas), el reflejo de las cascadas en
        // destellos sueltos y el brillo del sol
        vec2 p = wp.xz;
        float e = 0.08;
        float h0 = waves(p, t);
        vec3 n = normalize(vec3(-(waves(p + vec2(e, 0.0), t) - h0) / e * 0.16, 1.0, -(waves(p + vec2(0.0, e), t) - h0) / e * 0.16));
        float fres = 0.04 + 0.96 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
        col = mix(WATER_DEEP, WATER_LIGHT, 0.45) + vec3(0.04, 0.30, 0.33) * glow;
        float r = waves(p * 0.8 + vec2(5.3, 1.7), t * 0.8);
        col += vec3(0.05, 0.26, 0.26) * smoothstep(0.45, 0.78, r);
        // el reflejo de las cascadas (blanco turquesa) en las ondas menudas: destellos sueltos que bajan hacia la cámara
        vec3 nf = normalize(n + vec3(noise(p * 6.0 + vec2(t * 1.3, 0.0)) - 0.5, 0.0, noise(p * 6.5 - vec2(0.0, t * 1.1)) - 0.5) * 0.5);
        float rc = cascadeSeen(wp, reflect(-v, nf));
        // (destellos redondos y suaves: el producto de dos ruidos girados, sin la rejilla de uno solo)
        float gl = noise(p * 6.0 + vec2(t * 0.8, -t * 1.6)) * noise(vec2(p.x * 4.1 - p.y * 2.9, p.x * 2.7 + p.y * 4.3) * 1.4 - t * 0.9);
        rc *= smoothstep(0.20, 0.42, gl);
        col += vec3(0.75, 1.0, 1.0) * rc * (0.25 + 1.2 * fres);
        vec3 L = normalize(LightDir);
        float spec = pow(max(dot(reflect(-L, nf), v), 0.0), 60.0);
        col += vec3(1.0, 0.92, 0.70) * spec * (0.5 + 2.2 * beam);
        col += vec3(0.85, 1.0, 0.85) * beam * 0.10;
        // espuma en las orillas y al pie de los chorros
        // (con burbujas; alrededor del pie de cada chorro, blanca y ancha: tapa su unión con el estanque)
        float foam = smoothstep(0.25, 0.75, shore * (0.80 + 0.4 * noise(p * 3.0 + vec2(t * 0.6, t * 0.4))));
        foam *= 0.80 + 0.20 * noise(p * 9.0 + vec2(t * 1.5, -t * 1.2));
        col = mix(col, vec3(0.92, 1.0, 1.0), foam * 0.92);
        col *= mix(0.86, 1.10, light);
        // transparente de cerca (se ve el fondo) y más opaca de lejos o con espuma o destellos
        alpha = clamp(0.40 + fres * 0.45 + foam * 0.45 + rc * 0.3, 0.0, 0.96);
    }
    float d = length(CamPos - wp);
    float f = clamp((d - FogStart) / max(0.001, FogEnd - FogStart), 0.0, 1.0);
    col = mix(col, FogColor.rgb, f);
    fragColor = vec4(col, alpha);
}
