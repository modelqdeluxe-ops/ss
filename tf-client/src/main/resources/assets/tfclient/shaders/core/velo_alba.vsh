#version 150

// El Velo del Alba (SanctuaryFx): una esfera de radio 1 (la malla se hace una vez) que se pone en su sitio, relativa a
// la cámara
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Center;
uniform float Radius;

out vec3 vPos;

void main() {
    vPos = Center + Position * Radius;
    gl_Position = ProjMat * ModelViewMat * vec4(vPos, 1.0);
}
