#version 460

#include "light.glsl"
#include "fog.glsl"

layout (binding = 0) uniform UniformBufferObject {
    mat4 MVP;
};

layout (push_constant) readonly uniform  PushConstant {
    vec3 ChunkOffset;
};

layout (binding = 3) uniform sampler2D Sampler2;

layout (location = 0) out flat uint textureIndex;
layout (location = 1) out float vertexDistance;
layout (location = 2) out vec4 vertexColor;
layout (location = 3) out vec2 texCoord0;

//Compressed Vertex
layout (location = 0) in ivec4 Position;
layout (location = 1) in vec4 Color;
layout (location = 2) in uvec2 UV0;
layout (location = 3) in ivec2 UV2;
//TODO: Use a Buffer Device Address + Storage buffer to store the 3D UVs

const float UV_INV = 1.0 / 32768.0;
//const vec3 POSITION_INV = vec3(1.0 / 1024.0);
const vec3 POSITION_INV = vec3(1.0 / 2048.0);
const vec3 POSITION_OFFSET = vec3(4.0);

void main() {
    const vec3 baseOffset = bitfieldExtract(ivec3(gl_InstanceIndex) >> ivec3(0, 16, 8), 0, 8);
    const vec4 pos = vec4(fma(Position.xyz, POSITION_INV, ChunkOffset + baseOffset), 1.0);
    gl_Position = MVP * pos;
    textureIndex = Position.a;
    vertexDistance = fog_distance(pos.xyz, 0);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0 * UV_INV;
}