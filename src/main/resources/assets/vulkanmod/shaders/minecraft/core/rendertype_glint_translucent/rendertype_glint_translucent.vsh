#version 460

#include "fog.glsl"

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(binding = 0) uniform readonly UniformBufferObject {
   mat4 MVP[32];
};

layout(location = 0) out float vertexDistance;
layout(location = 1) out vec2 texCoord0;

void main() {
    gl_Position = MVP[gl_BaseInstance & 31] * vec4(Position, 1.0);

    vertexDistance = fog_distance(Position.xyz, 0);
    texCoord0 = (MVP[(gl_BaseInstance+1) & 31] * vec4(UV0, 0.0, 1.0)).xy;
}