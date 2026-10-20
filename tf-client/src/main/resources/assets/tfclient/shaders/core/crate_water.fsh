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
uniform vec4 FlowUV;
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

// el agua que corre del juego (sus hilos verticales: la mitad izquierda de la textura, como la dibuja Minecraft)
float flow(vec2 p) {
    vec2 f = fract(p);
    return textureLod(Sampler0, mix(FlowUV.xy, FlowUV.zw, vec2(f.x * 0.5, f.y * 0.5)), 0.0).r;
}

// la piel del agua, igual en el estanque y en las cascadas (el dueño: la cascada tiene que ser la misma agua que el
// estanque): la textura del agua del juego (16×16), de 0 a 1, entera y fina: la quieta en el estanque y la que corre (hilos
// verticales) en las cascadas; de lejos, cuando un píxel de la textura es más pequeño que uno de la pantalla, se funde en su
// valor medio (si no, parpadearía)
float farOf(vec2 p) {
    return smoothstep(0.7, 1.6, length(fwidth(p * 16.0)));
}

float skin(vec2 p) {
    return mix(clamp((still(p) - 0.66) / 0.30, 0.0, 1.0), 0.16, farOf(p));
}

float skinFall(vec2 p) {
    return mix(clamp((flow(p) - 0.62) / 0.24, 0.0, 1.0), 0.15, farOf(p));
}

// el color del agua sobre su base, el MISMO en el estanque y en las cascadas: las líneas claras de la textura en turquesa
// muy claro y, sobre ellas, píxeles que centellean (la escarcha de la imagen del dueño). {@code cell}: el tamaño del
// destello (en la cascada, alargado hacia abajo, como el agua que cae)
vec3 waterLook(vec3 base, float sk, vec2 p, vec2 cell, float t) {
    vec3 col = base * (0.88 + 0.30 * sk);
    col = mix(col, vec3(0.42, 1.0, 1.0), smoothstep(0.40, 0.95, sk) * 0.30);
    vec2 tx = floor(p * cell);
    float tw = smoothstep(0.55, 1.0, sin(t * 5.0 + hash(tx) * 60.0)) * step(0.55, hash(tx + 7.7));
    col = mix(col, vec3(0.90, 1.0, 1.0), smoothstep(0.65, 1.0, sk) * tw * 0.75 * (1.0 - farOf(p)));
    return col;
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
        // la cascada: la misma agua que el estanque (misma textura, mismo color, mismas manchas de luz y destellos), que
        // baja; algo más clara arriba y en los hilos donde el agua corre más
        float height = shore * 16.0;
        // (baja deprisa, como el agua que corre del juego)
        vec2 fp = vec2(fuv.x, fuv.y + t * 2.6);
        float run = noise(vec2(fuv.x * 3.0, fp.y * 0.35));
        vec3 base = mix(vec3(0.03, 0.30, 0.38), vec3(0.05, 0.50, 0.58), run);
        // vetas de luz que bajan con el agua (en la cascada, la luz del agua corre hacia abajo, no hace remolinos)
        float vein = noise(vec2(fuv.x * 9.0, fp.y * 0.5)) * noise(vec2(fuv.x * 4.0 + 2.3, fp.y * 0.3 - 1.1));
        base = mix(base, vec3(0.30, 0.92, 0.95), smoothstep(0.30, 0.62, vein) * 0.55);
        col = waterLook(base, skinFall(fp), fp, vec2(16.0, 4.0), t);
        // espuma abajo: mucha en la cascada alta, poca en los saltos de la escalera
        float foam = smoothstep(1.3, 0.0, fuv.y) * (0.6 + 0.4 * noise(vec2(fuv.x * 5.0, t * 4.0))) * mix(0.35, 0.7, smoothstep(1.5, 4.0, height));
        col = mix(col, vec3(0.85, 1.0, 1.0), foam);
        col *= mix(0.86, 1.10, light);
        col += vec3(0.6, 1.0, 1.0) * beam * 0.10;
        alpha = 0.94 + 0.05 * foam;     // (casi opaca: si no, se transparentan las filas de ladrillo de detrás como rayas)
    } else {
        vec2 p = wp.xz;
        float e = 0.08;
        float h0 = waves(p, t);
        vec3 n = normalize(vec3(-(waves(p + vec2(e, 0.0), t) - h0) / e * 0.16, 1.0, -(waves(p + vec2(0.0, e), t) - h0) / e * 0.16));
        float fres = 0.04 + 0.96 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
        // el agua es clara: se ve el fondo de baldosas (con la refracción de las ondas), iluminado de turquesa, con la red de
        // líneas de luz encima (como en la imagen del dueño)
        vec2 q = p + n.xz * 0.35;
        vec2 cell = floor(q), fq = fract(q);
        float edge = min(min(fq.x, 1.0 - fq.x), min(fq.y, 1.0 - fq.y));
        float grid = 1.0 - smoothstep(0.02, 0.07, edge);
        vec3 deep = vec3(0.03, 0.20, 0.30), shallow = vec3(0.04, 0.42, 0.52);
        col = mix(deep, shallow, clamp(0.3 + 0.35 * shore + 0.5 * glow + (hash(cell) - 0.5) * 0.35, 0.0, 1.0));
        col *= 1.0 - grid * 0.16;
        // las manchas de luz que dibuja el agua en el fondo (cáusticas, suaves)
        float c = caustic(q * 1.4, t);
        col = mix(col, vec3(0.30, 0.92, 0.95), smoothstep(0.36, 0.62, c) * 0.60);
        col += vec3(0.0, 0.18, 0.20) * smoothstep(0.22, 0.46, c);
        col += vec3(0.10, 0.70, 0.75) * glow * 0.3;
        col = waterLook(col, skin(p), p, vec2(16.0), t);
        // el reflejo del santuario (verde y turquesa) y el del haz de sol
        vec3 refl = mix(vec3(0.01, 0.24, 0.34), vec3(0.12, 0.56, 0.66), clamp(n.x * 4.0 + 0.5, 0.0, 1.0));
        col = mix(col, refl, fres * 0.15);
        vec3 L = normalize(LightDir);
        float spec = pow(max(dot(reflect(-L, n), v), 0.0), 60.0);
        col += vec3(1.0, 0.92, 0.70) * spec * (0.5 + 2.2 * beam);
        col += vec3(0.85, 1.0, 0.85) * beam * 0.14;
        // espuma en las orillas
        float foam = smoothstep(0.35, 0.95, shore * (0.75 + 0.5 * noise(p * 3.0 + vec2(t * 0.6, t * 0.4))));
        col = mix(col, vec3(0.85, 1.0, 1.0), foam * 0.45);
        col *= mix(0.86, 1.10, light);
        // (el fondo ya va dibujado aquí, con su luz: casi opaca)
        alpha = clamp(0.88 + fres * 0.1, 0.0, 0.97);
    }
    float d = length(CamPos - wp);
    float f = clamp((d - FogStart) / max(0.001, FogEnd - FogStart), 0.0, 1.0);
    col = mix(col, FogColor.rgb, f);
    fragColor = vec4(col, alpha);
}
