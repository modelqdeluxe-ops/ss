#version 150

// El Corazón del Alba (SanctuaryFx): un cuadro que mira a la cámara (o la columna de luz, que gira solo en vertical)
in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0 * 2.0 - 1.0;
}
