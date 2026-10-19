#version 150

// La grieta del cielo (SkyRiftClient): un cuadro que mira a la cámara, lejos, en la dirección de la grieta
in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec3 vDir;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0 * 2.0 - 1.0;
    vDir = Position;
}
