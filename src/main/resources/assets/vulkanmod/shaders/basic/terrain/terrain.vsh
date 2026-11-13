#version 460

#include "light.glsl"
#include "fog.glsl"

#extension GL_EXT_scalar_block_layout : require
#extension GL_EXT_spirv_intrinsics : require
#extension GL_EXT_buffer_reference2 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int16 : enable

layout (binding = 0) uniform UB0 {
    mat4 MVP;
};

layout (buffer_reference, buffer_reference_align = 2048, scalar) buffer readonly restrict SectionData {
    float SectionFadeFactors[512];
};

layout (buffer_reference, buffer_reference_align = 16, scalar) buffer readonly restrict Vertices {
    i16vec4 Position;
    u16vec2 UV0;
    uint PackedColor;
};

layout (push_constant, scalar) uniform pushConstant {
    restrict Vertices vertices;
    restrict SectionData chunkAreaInfo;
    vec3 ModelOffset;
};

layout (binding = 3) uniform sampler2D Sampler2;


layout (location = 0) out vec4 vertexColor;
layout (location = 1) out vec2 texCoord0;
layout (location = 2) out float sphericalVertexDistance;
layout (location = 3) out float cylindricalVertexDistance;
layout (location = 4) out flat float fadeFactor;

const float UV_INV = 1.0 / 32768.0;
const vec3 POSITION_INV = vec3(1.0 / 2048.0);
const vec3 POSITION_OFFSET = vec3(4.0);

spirv_instruction (id = 203)
ivec3 bitfieldExtractU(ivec3 value, int offset, int bits);

vec3 getVertexPosition() {
    const vec3 baseOffset = bitfieldExtractU(ivec3(gl_BaseInstance) >> ivec3(0, 6, 3), 0, 3) << 4;

    return fma(vertices[gl_VertexIndex].Position.xyz, POSITION_INV, ModelOffset + baseOffset);
}

void main() {
    const vec3 pos = getVertexPosition();
    gl_Position = MVP * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);

    const vec4 Color = unpackUnorm4x8(vertices[gl_VertexIndex].PackedColor);

    vertexColor = Color * sample_lightmap2(Sampler2, vertices[gl_VertexIndex].Position.a);
//    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    const int rSectionIndex = gl_BaseInstance;
    fadeFactor = chunkAreaInfo.SectionFadeFactors[rSectionIndex];

    texCoord0 = vertices[gl_VertexIndex].UV0 * UV_INV;
//    texCoord0 = UV0;
}