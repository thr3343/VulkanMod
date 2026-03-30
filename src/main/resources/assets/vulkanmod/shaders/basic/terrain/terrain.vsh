#version 460

#include "light.glsl"
#include "fog.glsl"

layout (binding = 0) uniform UniformBufferObject {
    mat4 MVP;
    int CurrentTime;
};

layout(binding = 1) uniform UBO {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
    float AlphaCutout;
    ivec2 TextureSize;
    vec2 TexelSize;
    int UseRgss;
};

layout (binding = 2) uniform UBO2 {
    ivec4 SectionOffsets[128];
    vec4 SectionFadeFactors[128];
};

layout (push_constant) uniform pushConstant {
    vec3 ModelOffset;
};

layout (binding = 4) uniform sampler2D Sampler2;


layout (location = 0) out vec4 vertexColor;
layout (location = 1) out vec2 texCoord0;
layout (location = 2) out float sphericalVertexDistance;
layout (location = 3) out float cylindricalVertexDistance;
layout (location = 4) out flat float fadeFactor;

#define COMPRESSED_VERTEX

#ifdef COMPRESSED_VERTEX
    layout (location = 0) in ivec4 Position;
    layout (location = 1) in uvec2 UV0;
    layout (location = 2) in uint PackedColor;
#else
    layout (location = 0) in vec3 Position;
    layout (location = 1) in vec4 Color;
    layout (location = 2) in vec2 UV0;
    layout (location = 3) in ivec2 UV2;
    layout (location = 4) in vec3 Normal;
#endif

const float UV_INV = 1.0 / 32768.0;
const vec3 POSITION_INV = vec3(1.0 / 2048.0);
const vec3 POSITION_OFFSET = vec3(4.0);

vec3 getVertexPosition() {
    const int encOffset = SectionOffsets[gl_InstanceIndex >> 2][gl_InstanceIndex & 3];
    const vec3 baseOffset = bitfieldExtract(ivec3(encOffset) >> ivec3(0, 16, 8), 0, 8);

    #ifdef COMPRESSED_VERTEX
        return fma(Position.xyz, POSITION_INV, ModelOffset + baseOffset);
    #else
        return Position.xyz + ModelOffset + baseOffset;
    #endif
}

void main() {
    const vec3 pos = getVertexPosition();
    gl_Position = MVP * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);

    const vec4 Color = unpackUnorm4x8(PackedColor);

    vertexColor = Color * sample_lightmap2(Sampler2, Position.a);
//    vertexColor = Color * sample_lightmap(Sampler2, UV2);

    fadeFactor = SectionFadeFactors[gl_InstanceIndex >> 2][gl_InstanceIndex & 3];

    texCoord0 = UV0 * UV_INV;
//    texCoord0 = UV0;
}