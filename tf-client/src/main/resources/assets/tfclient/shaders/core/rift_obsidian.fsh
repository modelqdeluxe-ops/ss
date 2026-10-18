#version 150

// La obsidiana de la grieta 3.0 (TF Client 1.3.88): facetas sin textura. La luz de cada cara (de arriba, la morada del
// desgarro y el brillo del borde) viene ya en el color del vértice (RiftRenderer.light); aquí se le suman las vetas que
// laten (ruido «de cresta» sobre las coordenadas de la cara), destellos en la obsidiana y el brillo de cristales y
// puntas. UV1 = (material, vena×100): 0 roca, 1 cristal, 2 esquirla, 3 punta.

uniform float GameTime;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec4 vColor;
in vec2 vUV;
flat in ivec2 vMat;
in float vLight;
in float vDist;

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
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
    for (int i = 0; i < 4; i++) {
        s += a * noise2(p);
        p = p * 2.07 + vec2(5.3, 1.9);
        a *= 0.5;
    }
    return s;
}

void main() {
    float t = GameTime * 1200.0;
    int mat = vMat.x;
    float vein = float(vMat.y) / 100.0;
    vec3 c = vColor.rgb * mix(0.62, 1.0, vLight);
    // vetas que brillan y laten (despacio, como si corriera algo por dentro)
    vec2 p = vUV * 0.6;
    float n = fbm2(p + vec2(0.0, t * 0.04));
    float ridge = 1.0 - abs(2.0 * n - 1.0);
    float v = smoothstep(0.955, 0.99, ridge) * vein;
    float pulse = 0.6 + 0.4 * sin(t * 1.6 + n * 14.0);
    vec3 veinCol = mix(vec3(0.55, 0.12, 1.0), vec3(1.0, 0.62, 1.0), smoothstep(0.95, 0.995, ridge));
    c += veinCol * v * pulse * 1.5;
    // destellos sueltos en la obsidiana: puntos redondos y suaves (1.3.89; antes eran cuadraditos duros, «píxeles»)
    vec2 sp = vUV * 9.0;
    vec2 cell = floor(sp);
    float g = hash12(cell);
    vec2 ctr = vec2(hash12(cell + 17.0), hash12(cell + 31.0)) * 0.6 + 0.2;
    float dd = length(fract(sp) - ctr);
    float aa = fwidth(sp.x) + fwidth(sp.y);
    float dot1 = 1.0 - smoothstep(0.03, 0.03 + max(aa, 0.04), dd);
    if (mat == 0) c += vec3(0.55, 0.35, 0.9) * dot1 * step(0.96, g) * (0.5 + 0.5 * sin(t * 2.5 + g * 60.0)) * 0.6;
    if (mat == 1 || mat == 2) {
        // cristal: brillo que sube por dentro
        float k = 0.5 + 0.5 * sin(vUV.y * 1.7 - t * 2.2);
        c += vec3(0.42, 0.10, 0.85) * (0.25 + 0.35 * k);
    } else if (mat == 3) {
        // las puntas de las garras arden un poco
        c += vec3(0.45, 0.12, 0.85) * (0.35 + 0.15 * sin(t * 2.0 + vUV.x));
    }
    float fog = clamp((vDist - FogStart) / max(0.001, FogEnd - FogStart), 0.0, 1.0);
    c = mix(c, FogColor.rgb, fog * FogColor.a);
    fragColor = vec4(c, vColor.a);
}
