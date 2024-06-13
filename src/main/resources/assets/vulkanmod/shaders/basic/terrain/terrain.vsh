#version 460

#include "light.glsl"

layout (binding = 0, set = 1) uniform UniformBufferObject {
    layout(offset = 0) mat4 MVP0; //Not using Uniform indices in case hardcoded offsets have perf advantages/benefits
    layout(offset = 512) mat4 ModelViewMat;
};

layout (push_constant) readonly uniform  PushConstant {
    vec3 ChunkOffset;
};

layout (binding = 2, set = 1) uniform sampler2D Sampler2;


layout (location = 0) out flat uint textureIndex;
layout (location = 1) out vec4 vertexColor;
layout (location = 2) out vec2 texCoord0;
//layout(location = 3) out vec4 normal;

//Compressed Vertex
layout (location = 0) in ivec4 Position;
layout (location = 1) in vec4 Color;
layout (location = 2) in uvec2 UV0;
layout (location = 3) in ivec2 UV2;
//layout(location = 4) in vec3 Normal;

const float UV_INV = 1.0 / 32768.0;
const vec3 POSITION_INV = vec3(1.0 / 1024.0);

void main() {
    const vec3 baseOffset = bitfieldExtract(ivec3(gl_InstanceIndex) >> ivec3(0, 16, 8), 0, 8);
    const vec4 pos = vec4(fma(Position.xyz, POSITION_INV, ChunkOffset+baseOffset), 1.0);
    gl_Position = MVP0 * pos;

    textureIndex = Position.a;
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
//    vertexColor = Color * sample_lightmap2(Sampler2, Position.a);

//    vec2 baseTexCoord=vec2(1,0);


    texCoord0 = UV0*UV_INV;
//    normal = MVP * vec4(Normal, 0.0);
}

////Default Vertex
//layout(location = 0) in vec3 Position;
//layout(location = 1) in vec4 Color;
//layout(location = 2) in vec2 UV0;
//layout(location = 3) in ivec2 UV2;
//layout(location = 4) in vec3 Normal;
//
//void main() {
//    const vec3 baseOffset = bitfieldExtract(ivec3(gl_InstanceIndex) >> ivec3(0, 16, 8), 0, 8);
//    const vec4 pos = vec4(Position.xyz + baseOffset, 1.0);
//    gl_Position = MVP * pos;
//
//    vertexDistance = length((ModelViewMat * pos).xyz);
//    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
//    texCoord0 = UV0;
//    //    normal = MVP * vec4(Normal, 0.0);
//}