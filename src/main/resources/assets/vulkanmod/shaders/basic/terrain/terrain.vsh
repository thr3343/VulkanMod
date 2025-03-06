#version 460

#include "light.glsl"
#include "fog.glsl"

// #extension GL_KHR_shader_subgroup_ballot : enable
// #extension GL_KHR_shader_subgroup_quad : enable
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_scalar_block_layout : require
#extension GL_EXT_shader_explicit_arithmetic_types_int16 : require
//#extension GL_EXT_shader_explicit_arithmetic_types_float16 : require

//Hypothesis: Do Direct float loads have performance improvements (On GPUs supporting shaderFloat16)
/* struct Vertex2 {
    f16vec4 Position;
    f16vec2 UV0;
    uint PackedColor;
}; */


//Functions like std::bit_cast() data is read directly with no conversions
struct Vertex {
    i16vec4 Position;
    u16vec2 UV0;
    uint PackedColor; //explicit unorm type doesn't exist: must use packedUnorm instead
};

//enable additional compiler optimisations with restrict + readonly
layout(buffer_reference, std430, buffer_reference_align = 16) buffer restrict readonly BufferReference {
    Vertex vertices[];
};

layout (binding = 0) uniform UniformBufferObject {
    mat4 MVP;
};

//Long support hacked in via "ScreenSize" Uniform Entry in .json
layout (push_constant, scalar) uniform pushConstant {
    BufferReference vtxBuffer;
    vec3 ModelOffset;
};

layout (binding = 3) uniform sampler2D Sampler2;


layout (location = 0) out vec4 vertexColor;
layout (location = 1) out vec2 texCoord0;
layout (location = 2) out float vertexDistance;

#define COMPRESSED_VERTEX

const float UV_INV = 1.0 / 32768.0;
const vec3 POSITION_INV = vec3(1.0 / 2048.0);
const vec3 POSITION_OFFSET = vec3(4.0);

vec4 getVertexPosition(Vertex vertex) {
    const vec3 baseOffset = bitfieldExtract(ivec3(gl_InstanceIndex) >> ivec3(0, 16, 8), 0, 8);

    #ifdef COMPRESSED_VERTEX
        return vec4(fma(vertex.Position.xyz, POSITION_INV, ModelOffset + baseOffset), 1.0);
    #else
        return vec4(vertex.Position.xyz + baseOffset, 1.0);
    #endif
}

void main() {
    const Vertex vertex = vtxBuffer.vertices[gl_VertexIndex];
    const vec4 pos = getVertexPosition(vertex);
    gl_Position = MVP * pos;

    vertexDistance = fog_distance(pos.xyz, 0);
    //const vec4 Color = subgroupQuadBroadcast(unpackUnorm4x8(vertex.PackedColor), 0);
    const vec4 Color = unpackUnorm4x8(vertex.PackedColor);
    vertexColor = Color * sample_lightmap2(Sampler2, vertex.Position.a);

    //Hypothesis: Per Quad Freq Pulling for additional Compression
    //uint quadIndex = gl_VertexIndex / 6u;
    //vec2 uv = subgroupQuadBroadcast(uvBuffer[quadIndex], 0);
    texCoord0 = vertex.UV0 * UV_INV;
}