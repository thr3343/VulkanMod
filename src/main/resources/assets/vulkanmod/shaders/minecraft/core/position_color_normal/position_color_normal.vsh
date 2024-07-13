#version 460

#include "fog.glsl"

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(binding = 0) uniform UniformBufferObject {
    layout(offset = 0) mat4 MVP[32];
};

layout(location = 0) out float vertexDistance;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) out vec4 normal;

void main() {
    gl_Position = MVP[gl_BaseInstance & 31] * vec4(Position, 1.0);

    vertexDistance = fog_distance(Position.xyz, 0);
    vertexColor = Color;
    normal = MVP * vec4(Normal, 0.0);
}
