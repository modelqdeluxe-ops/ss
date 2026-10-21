#version 150

// (1.3.99) Las pasadas a pantalla completa de la cinemática de las crates (cielo, resplandor, imagen final): un
// cuadrado que tapa la pantalla, sin matrices.
in vec3 Position;

out vec2 uv;
out vec2 ndc;

void main() {
    ndc = Position.xy;
    uv = Position.xy * 0.5 + 0.5;
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
