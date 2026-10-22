#version 150

// (1.3.109) Las partículas de las crates, premultiplicadas (se pintan con ONE, ONE_MINUS_SRC_ALPHA): el alfa es el halo
// que tapa lo de detrás y el color, el halo más un centro que suma luz. El particle del juego descarta lo que tiene
// menos de 0,1 de alfa y cortaba el halo en un aro; aquí solo lo que no se ve. La niebla, también premultiplicada.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.004 && max(color.r, max(color.g, color.b)) < 0.004) discard;
    if (vertexDistance > FogStart) {
        float fog = vertexDistance < FogEnd ? smoothstep(FogStart, FogEnd, vertexDistance) : 1.0;
        color.rgb = mix(color.rgb, FogColor.rgb * color.a, fog * FogColor.a);
    }
    fragColor = color;
}
