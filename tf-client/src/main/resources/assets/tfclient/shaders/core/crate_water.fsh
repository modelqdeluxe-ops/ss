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

// escamas del agua que cae (celdas de Voronoi): x = distancia al centro más cercano, y = al segundo, z = valor de la celda
vec3 cells(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0, id = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            vec2 o = vec2(hash(i + g), hash(i + g + 17.3)) * 0.8 + 0.1;
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
    return vec3(d1, d2, id);
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
        // la cascada (como en la imagen del dueño): agua translúcida turquesa con escamas irregulares que bajan, el borde
        // de cada escama brillante, fino y roto en destellos (no una rejilla: el dueño, «nada de texturas cuadriculadas»),
        // vetas claras que bajan, destellos redondos pequeños y espuma abajo
        float height = shore * 16.0;
        vec2 fp = vec2(fuv.x * 4.6, fuv.y * 3.6 + t * 5.0);
        vec3 vc = cells(fp);
        float edge = vc.y - vc.x;
        // (el ancho del borde según lo que mide un píxel: de lejos no parpadea, se funde)
        float aa = max(fwidth(edge), 1e-4);
        float rim = 1.0 - smoothstep(0.0, 0.07 + aa * 1.5, edge);
        rim *= clamp(0.07 / (0.07 + aa * 2.0), 0.0, 1.0);
        float broken = smoothstep(0.30, 0.70, noise(fp * 2.2 + vec2(3.7, -t * 2.0)));
        float streak = noise(vec2(fuv.x * 5.0, fuv.y * 0.35 + t * 2.6)) * 0.6 + noise(vec2(fuv.x * 11.0, fuv.y * 0.6 + t * 3.4)) * 0.4;
        col = mix(vec3(0.00, 0.28, 0.38), vec3(0.04, 0.58, 0.70), clamp(vc.z * 0.45 + streak * 0.7 - 0.05, 0.0, 1.0));
        col = mix(col, vec3(0.62, 1.0, 1.0), rim * (0.30 + 0.65 * broken));
        col += vec3(0.10, 0.45, 0.45) * smoothstep(0.62, 0.9, streak) * 0.5;
        // destellos: puntos redondos pequeños que bajan con el agua y parpadean
        vec2 gp = vec2(fuv.x * 7.0, fuv.y * 7.0 + t * 6.0);
        vec2 gi = floor(gp), gf = fract(gp) - 0.5;
        vec2 go = vec2(hash(gi), hash(gi + 3.3)) - 0.5;
        float gd = length(gf - go * 0.6);
        float tw = step(0.82, hash(gi + 9.1)) * (0.5 + 0.5 * sin(t * 6.0 + hash(gi) * 40.0));
        col = mix(col, vec3(0.92, 1.0, 1.0), (1.0 - smoothstep(0.0, 0.09 + fwidth(gd), gd)) * tw * 0.85);
        // espuma abajo: mucha en la cascada alta, poca en los saltos de la escalera
        float foam = smoothstep(1.3, 0.0, fuv.y) * (0.6 + 0.4 * noise(vec2(fuv.x * 5.0, t * 4.0))) * mix(0.1, 0.7, smoothstep(1.5, 4.0, height));
        col = mix(col, vec3(0.85, 1.0, 1.0), foam);
        col *= mix(0.8, 1.15, light);
        col += vec3(0.6, 1.0, 1.0) * beam * 0.12;
        alpha = 0.84 + 0.12 * foam + 0.06 * rim;
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
        // el fondo de baldosas visto a través del agua: un mosaico de píxeles (1/8 de bloque) de turquesas distintos, como
        // en la imagen del dueño; de lejos se funde en su color medio (si no, parpadearía)
        vec2 px = floor(q * 8.0);
        float mos = hash(px + cell * 13.0) - 0.5;
        float far = clamp(length(fwidth(q * 8.0)) * 0.9, 0.0, 1.0);
        col *= 1.0 + mos * 0.30 * (1.0 - far);
        float c = caustic(q * 1.4, t);
        // (turquesa muy claro, no blanco: en la imagen los brillos del agua siguen siendo turquesa; manchas de luz suaves,
        // no una red de rayitas)
        col = mix(col, vec3(0.30, 0.92, 0.95), smoothstep(0.36, 0.62, c) * 0.75);
        col += vec3(0.0, 0.20, 0.22) * smoothstep(0.22, 0.46, c);
        col += vec3(0.10, 0.70, 0.75) * glow * 0.3;
        // el dibujo del agua del juego, que se mueve, muy suave (sus líneas claras en blanco salían como rayitas sueltas)
        col += vec3(0.04, 0.22, 0.22) * smoothstep(0.6, 0.9, still(p)) * (1.0 - far);
        // el reflejo del santuario (verde y turquesa) y el del haz de sol
        vec3 refl = mix(vec3(0.01, 0.24, 0.34), vec3(0.12, 0.56, 0.66), clamp(n.x * 4.0 + 0.5, 0.0, 1.0));
        col = mix(col, refl, fres * 0.15);
        vec3 L = normalize(LightDir);
        float spec = pow(max(dot(reflect(-L, n), v), 0.0), 60.0);
        col += vec3(1.0, 0.92, 0.70) * spec * (0.5 + 2.2 * beam);
        col += vec3(0.85, 1.0, 0.85) * beam * 0.14;
        // destellos sueltos
        float g = noise(p * 9.0 + vec2(t * 1.5, -t)) * noise(p * 13.0 - vec2(t, t * 1.2));
        col += vec3(0.50, 1.0, 1.0) * smoothstep(0.55, 0.75, g) * 0.8;
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
