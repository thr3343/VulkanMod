#version 450

const vec4 pos[] = { vec4(-1, -1, 0, 1),  vec4(1, -1, 0, 1), vec4(-1, 1, 0, 1) };
const vec2 uv[] = { vec2(0, 1),  vec2(2, 1), vec2(0, -1) };



void main() {

    gl_Position = pos[gl_VertexIndex];
}