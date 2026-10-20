#version 150

// (1.3.99) El agua del Santuario de las Hadas, con shader propio (la imagen del dueño: un estanque turquesa que brilla,
// con el dibujo del agua de Minecraft y el resplandor que sale de ella, «porque es un lugar mágico»): turquesa claro, la
// textura del agua del juego (la que se mueve, del atlas de bloques) para las líneas de luz, cáusticas, el reflejo del
// santuario, el brillo del haz de sol, destellos, la luz que sube del fondo y espuma en las orillas; las cascadas, con
// la textura del agua que corre, vetas blancas que bajan y espuma abajo.
// data: r = orilla (0..1; en las cascadas, su altura / 16), g = cascada (1) o superficie (0), b = brillo del fondo
// (0..1), a = luz de la escena.
// fuv: en las cascadas, (a lo ancho, metros desde abajo).
in vec3 wp;
in vec4 data;
in vec2 fuv;

uniform sampler2D Sampler0;
uniform vec4 StillUV;
uniform vec3 CamPos;
uniform vec3 LightDir;
uniform vec3 BeamPos;
uniform float Time;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

out vec4 fragColor;

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

// cáusticas: la red de líneas de luz que dibuja el agua al moverse
float caustic(vec2 p, float t) {
    float c = 0.0;
    vec2 q = p;
    for (int i = 0; i < 3; i++) {
        q = vec2(q.x + sin(q.y * 1.7 + t * 0.9 + float(i)), q.y + cos(q.x * 1.5 - t * 0.8 + float(i) * 1.3));
        c += 1.0 / (1.0 + 18.0 * abs(sin(q.x * 1.3) * sin(q.y * 1.3)));
    }
    return c / 3.0;
}

// el dibujo del agua del juego (gris: sus líneas claras), sin mipmaps (con fract cambiaría de nivel en cada borde)
float still(vec2 p) {
    return textureLod(Sampler0, mix(StillUV.xy, StillUV.zw, fract(p)), 0.0).r;
}

// la piel del agua del estanque: la textura del agua del juego (16×16, la quieta), de 0 a 1, entera y fina; de lejos,
// cuando un píxel de la textura es más pequeño que uno de la pantalla, se funde en su valor medio (si no, parpadearía)
float farOf(vec2 p) {
    return smoothstep(0.7, 1.6, length(fwidth(p * 16.0)));
}

float skin(vec2 p) {
    return mix(clamp((still(p) - 0.66) / 0.30, 0.0, 1.0), 0.16, farOf(p));
}

// el color del agua del estanque sobre su base: las líneas claras de la textura en turquesa muy claro y, sobre ellas,
// píxeles que centellean (la escarcha de la imagen del dueño). {@code cell}: el tamaño del destello
vec3 waterLook(vec3 base, float sk, vec2 p, vec2 cell, float t) {
    vec3 col = base * (0.88 + 0.30 * sk);
    col = mix(col, vec3(0.42, 1.0, 1.0), smoothstep(0.40, 0.95, sk) * 0.30);
    vec2 tx = floor(p * cell);
    float tw = smoothstep(0.55, 1.0, sin(t * 5.0 + hash(tx) * 60.0)) * step(0.55, hash(tx + 7.7));
    col = mix(col, vec3(0.90, 1.0, 1.0), smoothstep(0.65, 1.0, sk) * tw * 0.75 * (1.0 - farOf(p)));
    return col;
}

// las escamas del agua (como en la imagen del dueño): celdas redondeadas (Voronoi con el espacio ondulado: bordes curvos,
// no rectos como un cristal roto), de borde fino y claro que se rompe en destellos. x = el borde, y = los destellos,
// z = el tono de la celda. De lejos, cuando la celda mide pocos píxeles, el borde se funde (no parpadea).
vec3 scales(vec2 p, float t) {
    vec2 w = p + (vec2(noise(p * 1.3 + vec2(0.0, t * 0.4)), noise(p * 1.3 + vec2(3.1, -t * 0.4))) - 0.5) * 0.7;
    vec2 i = floor(w), f = fract(w);
    float d1 = 8.0, d2 = 8.0, id = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            vec2 o = vec2(hash(i + g), hash(i + g + 17.3)) * 0.7 + 0.15;
            float d = length(g + o - f);
            if (d < d1) {
                d2 = d1;
                d1 = d;
                id = hash(i + g + 5.7);
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    float e = d2 - d1;
    float far = smoothstep(0.08, 0.3, length(fwidth(w)));
    float line = (1.0 - smoothstep(0.0, 0.045 + fwidth(e), e)) * (1.0 - far);
    float brk = noise(w * 4.0 + vec2(t * 1.7, -t));
    // (el borde, roto: unos tramos brillan y otros casi no se ven)
    float glit = line * smoothstep(0.50, 0.78, brk);
    return vec3(line * (0.25 + 0.75 * smoothstep(0.25, 0.6, brk)), glit, mix(id, 0.5, far));
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
        // la cascada, como en la imagen del dueño: agua translúcida turquesa con escamas redondeadas alargadas hacia abajo
        // que bajan, su borde fino brillando en destellos, vetas de luz que bajan y espuma blanca donde cae; la misma agua
        // (las mismas escamas y destellos) que el estanque
        float height = shore * 16.0;
        vec2 fp = vec2(fuv.x, fuv.y + t * 2.4);
        vec3 sc = scales(vec2(fp.x * 5.6, fp.y * 3.8), t);
        float vein = noise(vec2(fuv.x * 9.0, fp.y * 0.5)) * noise(vec2(fuv.x * 4.0 + 2.3, fp.y * 0.3 - 1.1));
        col = mix(vec3(0.01, 0.28, 0.38), vec3(0.05, 0.50, 0.60), sc.z * 0.75 + smoothstep(0.25, 0.6, vein) * 0.35);
        col = mix(col, vec3(0.40, 0.92, 1.0), sc.x * 0.45);
        col = mix(col, vec3(0.92, 1.0, 1.0), sc.y * 0.80);
        // espuma blanca donde cae: mucha en la cascada alta y en los chorros de los lados, poca en los saltos de la escalera
        float foam = smoothstep(1.1, 0.0, fuv.y) * (0.6 + 0.4 * noise(vec2(fuv.x * 6.0, fuv.y * 3.0 + t * 5.0))) * mix(0.55, 0.9, smoothstep(1.5, 4.0, height));
        col = mix(col, vec3(0.90, 1.0, 1.0), foam);
        // el golpe del agua contra el estanque: blanco que brilla (y florece con el resplandor), como en la imagen
        col += vec3(0.55, 0.85, 0.85) * smoothstep(0.45, 0.0, fuv.y) * (0.6 + 0.4 * noise(vec2(fuv.x * 9.0, t * 6.0)));
        col *= mix(0.86, 1.10, light);
        col += vec3(0.6, 1.0, 1.0) * beam * 0.10;
        alpha = 0.80 + 0.18 * max(foam, sc.y);     // (translúcida, como en la imagen: detrás se adivina la piedra)
    } else {
        // el estanque, como en la imagen del dueño: agua TRANSPARENTE, turquesa, a través de la que se ven los bloques de
        // verdad del fondo (su mosaico de píxeles, con la textura del juego), con la textura del agua encima, manchas de luz
        // suaves y, en las ondas, el reflejo de las cascadas en columnas de destellos
        vec2 p = wp.xz;
        float e = 0.08;
        float h0 = waves(p, t);
        vec3 n = normalize(vec3(-(waves(p + vec2(e, 0.0), t) - h0) / e * 0.16, 1.0, -(waves(p + vec2(0.0, e), t) - h0) / e * 0.16));
        float fres = 0.04 + 0.96 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
        col = vec3(0.02, 0.42, 0.54) + vec3(0.04, 0.30, 0.33) * glow;
        float c = caustic(p * 1.4 + n.xz * 0.35, t);
        col += vec3(0.10, 0.35, 0.32) * smoothstep(0.36, 0.62, c);
        col = waterLook(col, skin(p), p, vec2(16.0), t);
        // las mismas escamas que la cascada, más suaves (de ellas son las líneas claras y los destellos del agua)
        vec3 sc = scales(p * 2.2 + n.xz * 0.3, t);
        col = mix(col, vec3(0.45, 0.95, 1.0), sc.x * 0.30);
        col = mix(col, vec3(0.92, 1.0, 1.0), sc.y * 0.60);
        // el reflejo de las cascadas (blanco turquesa) en las ondas menudas: columnas de destellos que bajan hacia la cámara
        vec3 nf = normalize(n + vec3(noise(p * 6.0 + vec2(t * 1.3, 0.0)) - 0.5, 0.0, noise(p * 6.5 - vec2(0.0, t * 1.1)) - 0.5) * 0.5);
        float rc = cascadeSeen(wp, reflect(-v, nf));
        // (rotos en destellos, como en la imagen: no una mancha)
        rc *= smoothstep(0.52, 0.78, noise(p * vec2(9.0, 4.0) + vec2(t * 0.8, -t * 1.6)));
        col += vec3(0.75, 1.0, 1.0) * rc * (0.25 + 1.2 * fres);
        vec3 L = normalize(LightDir);
        float spec = pow(max(dot(reflect(-L, nf), v), 0.0), 60.0);
        col += vec3(1.0, 0.92, 0.70) * spec * (0.5 + 2.2 * beam);
        col += vec3(0.85, 1.0, 0.85) * beam * 0.10;
        // espuma en las orillas y al pie de los chorros
        float foam = smoothstep(0.35, 0.95, shore * (0.75 + 0.5 * noise(p * 3.0 + vec2(t * 0.6, t * 0.4))));
        col = mix(col, vec3(0.88, 1.0, 1.0), foam * 0.6);
        col *= mix(0.86, 1.10, light);
        // transparente de cerca (se ve el fondo) y más opaca de lejos o con espuma o destellos
        alpha = clamp(0.40 + fres * 0.45 + foam * 0.45 + rc * 0.3, 0.0, 0.96);
    }
    float d = length(CamPos - wp);
    float f = clamp((d - FogStart) / max(0.001, FogEnd - FogStart), 0.0, 1.0);
    col = mix(col, FogColor.rgb, f);
    fragColor = vec4(col, alpha);
}
