#version 150

// (1.3.106) Los rayos del premio de la cinemática de las crates. Se dibujan como el agua (shader y VertexBuffer propios,
// SceneFx), no con el lote del juego: en el juego del dueño no salían (el agua, sí). Hace lo mismo que position_color.
in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
}
