#version 450

const vec4 pos[] = { vec4(-1, -1, 0, 1),  vec4(3, -1, 0, 1), vec4(-1, 3, 0, 1) };

void main() {
    gl_Position = pos[gl_VertexIndex];
}