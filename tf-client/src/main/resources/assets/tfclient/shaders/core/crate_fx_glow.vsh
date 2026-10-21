#version 150

// (1.3.106) Lo que suma luz en la cinemática de las crates (halos, hadas, luciérnagas, haz, polvo, runas, pilar, premios).
// Se dibuja como el agua (shader y VertexBuffer propios, SceneFx), no con el lote del juego: en el juego del dueño no
// salía ninguno (el agua, sí). Hace lo mismo que position_color_tex de Minecraft.
in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
}
