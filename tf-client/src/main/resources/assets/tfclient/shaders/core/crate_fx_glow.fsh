#version 150

// (1.3.106) Lo que suma luz en la cinemática de las crates (halos, hadas, luciérnagas, haz, polvo, runas, pilar, premios).
// Se dibuja como el agua (shader y VertexBuffer propios, SceneFx), no con el lote del juego: en el juego del dueño no
// salía ninguno (el agua, sí). Hace lo mismo que position_color_tex de Minecraft.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a == 0.0) discard;
    fragColor = color * ColorModulator;
}
