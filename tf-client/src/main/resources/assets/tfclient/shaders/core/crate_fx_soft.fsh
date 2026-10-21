#version 150

// (1.3.106) Lo translúcido de la cinemática de las crates (los pétalos). Se dibuja como el agua (shader y VertexBuffer
// propios, SceneFx), no con el lote del juego: en el juego del dueño no salía (el agua, sí). Hace lo mismo que position_color.
uniform vec4 ColorModulator;

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor;
    if (color.a == 0.0) discard;
    fragColor = color * ColorModulator;
}
