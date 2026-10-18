#version 150

// Un cuadro que llena la pantalla (TravelFx lo dibuja en coordenadas de recorte, sin matrices)
in vec3 Position;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}
