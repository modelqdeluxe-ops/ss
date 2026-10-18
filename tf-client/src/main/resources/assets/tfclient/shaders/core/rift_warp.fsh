#version 150

// La distorsión del espacio-tiempo de las grietas (TF Client 1.3.91; el dueño: «al romper el cristal de la grieta, que
// desate su poder y se distorsione el espacio-tiempo hasta matar al boss, y se cierre la grieta»). Se aplica a la imagen
// ya hecha del mundo (RiftWarp: con o sin paquete de shaders), antes de la mano y la interfaz.
//
// Por cada grieta (hasta dos, las más fuertes; y el viaje al Void, en el centro de la pantalla):
//  - lente gravitatoria: a la distancia r del centro se ve lo que hay en |r − a/r|, como alrededor de una masa (el
//    anillo de Einstein; dentro de él, lo del otro lado y al revés). El desgarro mismo (r < ~R) no se deforma.
//  - remolino que gira más cerca del centro, ondas gravitatorias que salen y la onda de choque (un anillo que barre la
//    pantalla al estallar el cristal; hacia dentro al cerrarse), con aberración cromática donde más se mueve la imagen.
//  - destello del color de la grieta al abrirse y al cerrarse.
// En toda la pantalla, cerca de una grieta activa: ondula un poco, aberración y viñeta de su color.
//
// Unidades: p = (x · ancho/alto, y) en uv; R (radio de la grieta en pantalla) en esas unidades.

uniform sampler2D Sampler0;   // la imagen del mundo (copia)
uniform vec4 Src0;            // centro (uv), R, fuerza de la lente
uniform vec4 Src1;
uniform vec4 Fx0;             // remolino (rad), onda de choque (radio en R; < 0 = no), ondas (amplitud en R), destello
uniform vec4 Fx1;
uniform vec4 Tint0;           // color de la grieta y cuánto tiñe
uniform vec4 Tint1;
uniform vec4 Glob;            // ondulación de toda la pantalla, aberración, viñeta, —
uniform vec2 ScreenSize;
uniform float WarpTime;

in vec2 texCoord;
out vec4 fragColor;

vec2 warpOne(vec2 p, vec4 src, vec4 fx, float aspect, inout float moved) {
    if (src.w <= 0.0001 && fx.y < 0.0 && fx.z <= 0.0 && abs(fx.x) <= 0.0001) return p;
    vec2 c = vec2(src.x * aspect, src.y);
    vec2 d = p - c;
    float r = length(d) + 1e-5;
    float R = max(src.z, 1e-3);
    vec2 dir = d / r;
    // el desgarro mismo queda como está; la deformación empieza en su borde
    float keep = smoothstep(0.35 * R, 1.1 * R, r);
    float a = src.w * R * R * keep;
    float rl = abs(r - a / r);
    float sw = fx.x * exp(-r / (1.6 * R)) * keep * (0.75 + 0.25 * sin(WarpTime * 0.8));
    float wave = fx.z * R * sin(r / R * 8.0 - WarpTime * 5.0) * exp(-r / (3.0 * R)) * keep;
    float shock = fx.y >= 0.0 ? 0.35 * R * exp(-pow((r / R - fx.y) * 2.2, 2.0)) : 0.0;
    float r2 = max(0.0, rl - wave - shock);
    vec2 nd = vec2(cos(sw) * dir.x - sin(sw) * dir.y, sin(sw) * dir.x + cos(sw) * dir.y) * r2;
    moved += abs(r2 - r) / R + abs(sw) * 0.3 * keep;
    return c + nd;
}

float glowOf(vec2 p, vec4 src, vec4 fx, float aspect) {
    if (fx.w <= 0.0) return 0.0;
    float r = length(p - vec2(src.x * aspect, src.y)) / max(src.z, 1e-3);
    return fx.w * (exp(-r * 0.8) * 0.8 + exp(-r * 0.25) * 0.25);
}

void main() {
    float aspect = ScreenSize.x / ScreenSize.y;
    float t = WarpTime;
    vec2 p = vec2(texCoord.x * aspect, texCoord.y);
    // el espacio-tiempo ondula un poco en toda la pantalla
    p += Glob.x * vec2(sin(p.y * 9.0 + t * 1.9) + 0.4 * sin(p.y * 23.0 - t * 2.7),
                       cos(p.x * 7.0 - t * 1.6) + 0.4 * cos(p.x * 19.0 + t * 2.3)) * 0.5;
    float moved = 0.0;
    vec2 q = warpOne(p, Src0, Fx0, aspect, moved);
    q = warpOne(q, Src1, Fx1, aspect, moved);
    vec2 uv = vec2(q.x / aspect, q.y);
    vec2 duv = uv - texCoord;
    float ca = min(moved, 1.5) * 0.05;
    vec2 rad = (texCoord - 0.5) * Glob.y;
    vec3 col;
    col.r = texture(Sampler0, clamp(texCoord + duv * (1.0 + ca) + rad, 0.001, 0.999)).r;
    col.g = texture(Sampler0, clamp(uv, 0.001, 0.999)).g;
    col.b = texture(Sampler0, clamp(texCoord + duv * (1.0 - ca) - rad, 0.001, 0.999)).b;
    // destellos del color de cada grieta
    vec2 p0 = vec2(texCoord.x * aspect, texCoord.y);
    col += Tint0.rgb * glowOf(p0, Src0, Fx0, aspect);
    col += Tint1.rgb * glowOf(p0, Src1, Fx1, aspect);
    // viñeta del color de la grieta
    vec2 v = (texCoord - 0.5) * vec2(aspect, 1.0);
    float vig = smoothstep(0.35, 1.0, length(v) / (0.5 * sqrt(aspect * aspect + 1.0)));
    col = mix(col, col * 0.6 + Tint0.rgb * 0.22, vig * Glob.z);
    fragColor = vec4(col, 1.0);
}
