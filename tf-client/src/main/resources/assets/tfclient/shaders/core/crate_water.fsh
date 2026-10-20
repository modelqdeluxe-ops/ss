#version 150

// (1.3.99) El agua del pantano de las hadas, con shader propio (el dueño: «más azulita, tipo tráileres de Minecraft o
// Minecraft Dungeons»; no la textura del juego): azul profundo y turquesa en las orillas, ondas que se mueven, cáusticas
// de luz, el reflejo del cielo de noche, el brillo del haz en el agua, destellos, la luz que sube del fondo (linternas
// de mar) y espuma en las orillas; las cascadas, con vetas blancas que bajan y espuma abajo.
// data: r = orilla (0..1), g = cascada (1) o superficie (0), b = brillo del fondo (0..1), a = luz de la escena.
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

void main() {
    float t = Time / 20.0;
    vec3 v = normalize(CamPos - wp);
    float shore = data.r, fall = data.g, glow = data.b, light = data.a;
    vec3 col;
    float alpha;
    if (fall > 0.5) {
        // la cascada: agua que cae, con vetas que bajan deprisa y espuma abajo
        vec2 q = vec2(fuv.x * 3.0, fuv.y * 0.9 + t * 3.2);
        float streak = smoothstep(0.45, 0.95, noise(vec2(q.x * 2.2, q.y)) * 0.7 + noise(vec2(q.x * 6.0, q.y * 2.0)) * 0.3);
        float base = noise(vec2(fuv.x * 1.5, fuv.y * 0.5 + t * 2.0));
        col = mix(vec3(0.03, 0.24, 0.66), vec3(0.10, 0.50, 0.92), base);
        col = mix(col, vec3(0.80, 0.94, 1.0), streak * 0.6);
        float foam = smoothstep(1.4, 0.0, fuv.y) * (0.6 + 0.4 * noise(vec2(fuv.x * 5.0, t * 4.0)));
        col = mix(col, vec3(0.92, 0.98, 1.0), foam * 0.8);
        col += vec3(0.15, 0.55, 0.85) * glow * 0.3;
        col *= mix(0.5, 1.0, light);
        alpha = 0.8 + 0.15 * streak + 0.1 * foam;
    } else {
        vec2 p = wp.xz;
        float e = 0.08;
        float h0 = waves(p, t);
        vec3 n = normalize(vec3(-(waves(p + vec2(e, 0.0), t) - h0) / e * 0.18, 1.0, -(waves(p + vec2(0.0, e), t) - h0) / e * 0.18));
        float fres = 0.04 + 0.96 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
        vec3 deep = vec3(0.01, 0.08, 0.30), shallow = vec3(0.03, 0.40, 0.85);
        col = mix(deep, shallow, clamp(0.2 + 0.45 * shore + 0.55 * glow, 0.0, 1.0));
        // la luz que dibuja el agua y la que sube del fondo
        float c = caustic(p * 0.9, t);
        col += vec3(0.20, 0.75, 1.0) * pow(c, 2.0) * (0.25 + 0.5 * glow);
        col += vec3(0.10, 0.70, 0.95) * glow * 0.3;
        // el reflejo del cielo de noche y del bosque, y el del haz de luz
        vec3 refl = mix(vec3(0.03, 0.05, 0.14), vec3(0.16, 0.12, 0.32), clamp(n.x * 4.0 + 0.5, 0.0, 1.0));
        col = mix(col, refl, fres * 0.7);
        vec3 L = normalize(LightDir);
        float beam = exp(-dot(wp.xz - BeamPos.xz, wp.xz - BeamPos.xz) / 9.0);
        float spec = pow(max(dot(reflect(-L, n), v), 0.0), 60.0);
        col += vec3(1.0, 0.88, 0.62) * spec * (0.6 + 2.5 * beam);
        col += vec3(1.0, 0.9, 0.7) * beam * 0.25;
        // destellos sueltos
        float g = noise(p * 9.0 + vec2(t * 1.5, -t)) * noise(p * 13.0 - vec2(t, t * 1.2));
        col += vec3(0.7, 0.95, 1.0) * smoothstep(0.55, 0.75, g) * 0.9;
        // espuma en las orillas
        float foam = smoothstep(0.35, 0.95, shore * (0.75 + 0.5 * noise(p * 3.0 + vec2(t * 0.6, t * 0.4))));
        col = mix(col, vec3(0.80, 0.93, 1.0), foam * 0.55);
        col *= mix(0.6, 1.05, light);
        alpha = clamp(0.80 + fres * 0.15 + foam * 0.15, 0.0, 0.97);
    }
    float d = length(CamPos - wp);
    float f = clamp((d - FogStart) / max(0.001, FogEnd - FogStart), 0.0, 1.0);
    col = mix(col, FogColor.rgb, f);
    fragColor = vec4(col, alpha);
}
