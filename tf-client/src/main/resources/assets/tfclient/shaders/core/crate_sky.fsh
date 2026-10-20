#version 150

// (1.3.99) El cielo del Santuario de las Hadas (la cinemática de las crates), lo poco que se ve por el hueco de las
// copas: un día claro, azul verdoso pálido arriba, casi blanco y cálido hacia el sol (más claro que el blanco de la
// pantalla: el resplandor y los rayos salen de él), con velos finos de nube que se mueven.
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
    float sunAmt = max(dot(d, normalize(SunDir)), 0.0);

    vec3 horizon = vec3(0.86, 0.92, 0.84), zenith = vec3(0.48, 0.76, 0.86);
    vec3 col = mix(horizon, zenith, smoothstep(0.0, 0.8, h));
    // velos de nube, teñidos de oro hacia el sol
    vec2 cp = d.xz / (max(h, 0.05) + 0.2) * 0.6 + vec2(Time * 0.003, Time * 0.0012);
    float cl = smoothstep(0.5, 0.85, fbm(cp * 1.3)) * smoothstep(0.02, 0.15, h);
    col = mix(col, mix(vec3(0.95, 0.97, 0.95), vec3(1.0, 0.95, 0.80), pow(sunAmt, 4.0)), cl * 0.5);
    // el sol: el disco, la corona y el resplandor cálido alrededor
    col += vec3(1.0, 0.96, 0.82) * pow(sunAmt, 60.0) * 1.2 + vec3(1.0, 0.92, 0.70) * pow(sunAmt, 8.0) * 0.45;
    fragColor = vec4(col, 1.0);
}
