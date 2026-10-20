#version 150

// El Velo del Alba (SanctuaryFx, 1.3.96): la burbuja de luz que guarda el santuario del Void (lo último del Alba, que
// levantó la Orden: dentro, el Void no bebe tu luz). Una película de luz como la de una pompa (tornasol de oro, rosa y
// perla que fluye),
// cortinas de aurora que suben, chispas que titilan, un borde que brilla más cuanto más de lado se mira (Fresnel), un
// cinturón de oro donde toca el suelo y una onda que se abre desde donde la cruzas. Mezcla aditiva (brilla sobre el
// Void oscuro); no escribe profundidad.
uniform float STime;
uniform vec3 Center;
uniform float Radius;
uniform vec4 Ripple;
uniform float Inside;
uniform float Ground;
uniform float Alpha;
// (1.3.97) 1: la paleta violeta del Vacío (el aura de la armadura del Vacío en su altar) en vez de oro, rosa y perla
uniform float Violet;

in vec3 vPos;

out vec4 fragColor;

float h3(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h3(i), h3(i + vec3(1, 0, 0)), f.x), mix(h3(i + vec3(0, 1, 0)), h3(i + vec3(1, 1, 0)), f.x), f.y),
            mix(mix(h3(i + vec3(0, 0, 1)), h3(i + vec3(1, 0, 1)), f.x), mix(h3(i + vec3(0, 1, 1)), h3(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    return 0.55 * noise(p) + 0.3 * noise(p * 2.1 + 7.3) + 0.15 * noise(p * 4.3 + 1.7);
}

void main() {
    vec3 n = normalize(vPos - Center);
    vec3 v = normalize(vPos);
    float ndv = abs(dot(n, v));
    float fres = pow(1.0 - ndv, 2.5);
    float az = atan(n.z, n.x);
    // la película: su grosor fluye despacio hacia arriba y da el tornasol
    float th = fbm(n * 2.4 + vec3(0.0, -STime * 0.07, STime * 0.02));
    float film = th * 2.6 + ndv * 1.2 + STime * 0.04;
    // (solo oro, rosa y perla: con el arcoíris entero, el verde sobre el oro daba un borde verde oliva en el juego)
    vec3 gold = mix(vec3(1.0, 0.8, 0.46), vec3(0.62, 0.3, 1.0), Violet);
    vec3 rose = mix(vec3(1.0, 0.64, 0.84), vec3(0.92, 0.4, 1.0), Violet);
    vec3 pearl = mix(vec3(0.74, 0.9, 1.0), vec3(0.78, 0.66, 1.0), Violet);
    float ph = fract(film) * 3.0;
    vec3 irid = ph < 1.0 ? mix(gold, rose, ph) : ph < 2.0 ? mix(rose, pearl, ph - 1.0) : mix(pearl, gold, ph - 2.0);
    vec3 col = mix(gold, irid, 0.55);
    // cortinas de aurora que suben y ondas
    float curtain = pow(0.5 + 0.5 * sin(az * 5.0 + th * 5.0 + STime * 0.12), 4.0) * smoothstep(-0.3, 0.5, n.y);
    float wave = pow(0.5 + 0.5 * sin(n.y * 11.0 - STime * 0.9 + th * 3.0), 10.0);
    // chispas: puntos al azar que titilan
    vec3 sp = n * 46.0;
    vec3 cell = floor(sp);
    vec3 at = cell + vec3(h3(cell), h3(cell + 3.1), h3(cell + 7.7));
    float pick = step(0.82, h3(cell + 11.3));
    float tw = pow(0.5 + 0.5 * sin(STime * (1.2 + 2.0 * h3(cell + 5.5)) + 40.0 * h3(cell + 2.2)), 6.0);
    float spark = pick * tw * exp(-dot(sp - at, sp - at) * 30.0);
    // el cinturón de oro donde el Velo toca el suelo
    float belt = exp(-pow((n.y - Ground) * 20.0, 2.0)) * (0.85 + 0.15 * sin(az * 9.0 - STime * 0.6));
    // la onda de cuando se cruza: un anillo que se abre desde ese punto
    float rip = 0.0;
    if (Ripple.w >= 0.0) {
        float ang = acos(clamp(dot(n, normalize(Ripple.xyz)), -1.0, 1.0));
        float d = ang - 0.05 - Ripple.w * 1.4;
        rip = exp(-d * d * 90.0) * exp(-Ripple.w * 1.3) * 0.5 + exp(-ang * ang * 25.0) * exp(-Ripple.w * 4.0) * 0.08;
    }
    col = mix(col, mix(vec3(1.0, 0.95, 0.88), vec3(0.9, 0.8, 1.0), Violet), clamp(fres * 0.6 + spark + rip * 1.5 + belt * 0.5, 0.0, 1.0));
    float a = 0.05 + 0.55 * fres + 0.06 * wave + 0.1 * curtain * (0.4 + 0.6 * th) + 0.6 * spark + 0.3 * belt + rip;
    // desde dentro, más suave (que no tape la vista) pero cálido: el cielo se ve dorado
    if (Inside > 0.5) a = a * 0.7 + 0.025 * (0.6 + 0.4 * n.y);
    fragColor = vec4(col, clamp(a, 0.0, 0.9) * Alpha);
}
