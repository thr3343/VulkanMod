#version 450
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_KHR_shader_subgroup_ballot : enable
layout(early_fragment_tests) in;
#include "light.glsl"
#include "fog.glsl"

layout(binding = 3, set = 1) uniform sampler2DArray Sampler0[];

layout(binding = 1, set = 1) uniform UBO {
    vec4 FogColor;
    float FogStart;
    float FogEnd;
};


layout(location = 0) in flat uint textureIndex;
layout(location = 1) in vec2 texCoord0;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in float vertexDistance;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0[nonuniformEXT(textureIndex>>11)], vec3(texCoord0, textureIndex));
    fragColor = linear_fog(color * vertexColor, vertexDistance, FogStart, FogEnd, FogColor);
}
