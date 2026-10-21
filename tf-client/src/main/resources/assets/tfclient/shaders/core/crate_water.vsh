#version 150

// (1.3.99) El agua del pantano de las hadas (la cinemática de las crates): posición en el mundo de la escena, con la
// cámara y la proyección de la cinemática.
in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 wp;
out vec4 data;
out vec2 fuv;

void main() {
    wp = Position;
    data = Color;
    fuv = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
