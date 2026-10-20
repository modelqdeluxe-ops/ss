#version 150

// (1.3.99) El cielo del Bosque de las Hadas (la cinemática de las crates): el atardecer mágico. Cada píxel es la
// dirección en que mira la cámara: de dorado en el horizonte (más cálido hacia el sol) a rosa, violeta y azul noche
// arriba, con nubes finas teñidas de rosa y oro, las primeras estrellas que titilan y el sol con su corona (más claro
// que el blanco de la pantalla: el resplandor y los rayos salen de él).
in vec2 ndc;

uniform vec3 CamRight;
uniform vec3 CamUp;
uniform vec3 CamFwd;
uniform vec2 TanHalf;
uniform vec3 SunDir;
uniform float Time;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

void main() {
    vec3 d = normalize(CamFwd + ndc.x * TanHalf.x * CamRight + ndc.y * TanHalf.y * CamUp);
    float h = d.y;
    vec3 sunFlat = normalize(vec3(SunDir.x, 0.0, SunDir.z));
    vec3 dFlat = normalize(vec3(d.x, 0.0, d.z) + vec3(1e-4));
    float toward = max(dot(dFlat, sunFlat), 0.0);
    float sunAmt = max(dot(d, SunDir), 0.0);

    vec3 horizon = vec3(1.00, 0.66, 0.40), low = vec3(0.95, 0.52, 0.60), mid = vec3(0.42, 0.32, 0.66), zenith = vec3(0.10, 0.11, 0.33);
    vec3 col = mix(horizon, low, smoothstep(0.0, 0.12, h));
    col = mix(col, mid, smoothstep(0.10, 0.38, h));
    col = mix(col, zenith, smoothstep(0.32, 0.9, h));
    col += vec3(1.0, 0.52, 0.22) * pow(toward, 3.0) * (1.0 - smoothstep(0.0, 0.55, h)) * 0.5;

    // nubes finas, más cerca del horizonte, teñidas de rosa (y de oro hacia el sol)
    vec2 cp = d.xz / (max(h, 0.02) + 0.15) * 0.55 + vec2(Time * 0.0025, Time * 0.001);
    float cl = smoothstep(0.48, 0.82, fbm(cp * 1.2)) * smoothstep(0.0, 0.05, h) * (1.0 - smoothstep(0.3, 0.6, h));
    vec3 cloud = mix(vec3(0.96, 0.58, 0.66), vec3(1.0, 0.82, 0.55), pow(toward, 2.0));
    col = mix(col, cloud * 1.08, cl * 0.6);

    // las primeras estrellas
    vec2 sp = vec2(atan(d.z, d.x), asin(clamp(d.y, -1.0, 1.0))) * 70.0;
    vec2 cell = floor(sp);
    float s = hash(cell);
    float dd = length(fract(sp) - vec2(hash(cell + 1.3), hash(cell + 2.7)));
    float star = step(0.982, s) * smoothstep(0.12, 0.0, dd) * smoothstep(0.28, 0.62, h) * (0.55 + 0.45 * sin(Time * 0.12 * (0.5 + s) + s * 90.0));
    col += vec3(0.92, 0.94, 1.0) * star * (1.0 - cl) * 0.9;

    // el sol: disco, corona y resplandor
    col += vec3(1.0, 0.88, 0.66) * smoothstep(0.9993, 0.9997, sunAmt) * 2.2;
    col += vec3(1.0, 0.62, 0.34) * pow(sunAmt, 40.0) * 0.7 + vec3(1.0, 0.5, 0.32) * pow(sunAmt, 5.0) * 0.22;

    if (h < 0.0) col = mix(col, vec3(0.50, 0.40, 0.56), smoothstep(0.0, -0.2, h));
    fragColor = vec4(col, 1.0);
}
