#version 460

layout(location = 0) in vec3 Position;

layout(binding = 0) uniform readonly UniformBufferObject {
   mat4 MVP[32];
};

layout(location = 0) out float vertexDistance;

void main() {
    gl_Position = MVP[gl_BaseInstance & 31] * vec4(Position, 1.0);

    vertexDistance = length((MVP[(gl_BaseInstance & 31)+1] * vec4(Position, 1.0)).xyz);
}
