#version 150

// (1.3.99) El resplandor de la cinemática de las crates, a un cuarto de la resolución: Mode 0 baja la imagen y se
// queda con lo que brilla (lo que pasa de Threshold, con una rodilla suave); Mode 1, desenfoque gaussiano en una
// dirección (Dir, en píxeles de la salida; dos pasadas, horizontal y vertical, y otras dos más anchas); Mode 2 solo baja
// la imagen (el fondo desenfocado de la profundidad de campo).
in vec2 uv;

uniform sampler2D InSampler;
uniform vec2 InTexel;
uniform vec2 Dir;
uniform float Mode;
uniform float Threshold;

out vec4 fragColor;

vec3 bright(vec3 c) {
    float l = max(c.r, max(c.g, c.b));
    return c * smoothstep(Threshold, Threshold + 0.35, l);
}

void main() {
    if (Mode > 1.5) {
        // Mode 2: solo bajar (para el fondo desenfocado)
        vec3 acc = texture(InSampler, uv + InTexel * vec2(-1.0, -1.0)).rgb + texture(InSampler, uv + InTexel * vec2(1.0, -1.0)).rgb
                + texture(InSampler, uv + InTexel * vec2(-1.0, 1.0)).rgb + texture(InSampler, uv + InTexel * vec2(1.0, 1.0)).rgb;
        fragColor = vec4(acc * 0.25, 1.0);
        return;
    }
    if (Mode < 0.5) {
        vec3 acc = vec3(0.0);
        acc += bright(texture(InSampler, uv + InTexel * vec2(-1.0, -1.0)).rgb);
        acc += bright(texture(InSampler, uv + InTexel * vec2(1.0, -1.0)).rgb);
        acc += bright(texture(InSampler, uv + InTexel * vec2(-1.0, 1.0)).rgb);
        acc += bright(texture(InSampler, uv + InTexel * vec2(1.0, 1.0)).rgb);
        fragColor = vec4(acc * 0.25, 1.0);
        return;
    }
    float w[5] = float[5](0.227, 0.194, 0.121, 0.054, 0.016);
    vec3 acc = texture(InSampler, uv).rgb * w[0];
    for (int i = 1; i < 5; i++) {
        acc += texture(InSampler, uv + Dir * float(i)).rgb * w[i];
        acc += texture(InSampler, uv - Dir * float(i)).rgb * w[i];
    }
    fragColor = vec4(acc, 1.0);
}
