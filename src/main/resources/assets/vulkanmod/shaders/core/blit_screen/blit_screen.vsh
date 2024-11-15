#version 450

layout(location = 0) in vec3 Position;

layout(location = 0) out vec2 texCoord;

void main() {
    vec2 screenPos = Position.xy * 2.0 - 1.0;
    gl_Position = vec4(screenPos.x, screenPos.y, 1.0, 1.0);
    texCoord = Position.xy;
}
