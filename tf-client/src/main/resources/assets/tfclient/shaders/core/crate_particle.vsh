#version 150

// (1.3.109) Las partículas de las crates (CrateFxParticle) sin paquete de shaders: como el particle del juego, pero a
// plena luz siempre (no lee el mapa de luz) y con la distancia para la niebla.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    vertexDistance = length(view.xyz);
    texCoord0 = UV0;
    vertexColor = Color;
}
