#version 450
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_KHR_shader_subgroup_ballot : enable
#include "fog.glsl"

layout(binding = 3) uniform sampler2D Sampler0[];

layout(binding = 1) uniform UBO{
    vec4 FogColor;
    float FogStart;
    float FogEnd;
};

layout(push_constant) readonly uniform PushConstant{
    layout(offset = 32) bool USE_FOG;
};

layout(location = 0) flat in uint baseInstance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in float vertexDistance;

layout(location = 0) out vec4 fragColor;

void main() {
    const uint uniformBaseInstance = subgroupBroadcastFirst(baseInstance);
    vec4 color = texture(Sampler0[uniformBaseInstance], texCoord0) * vertexColor;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = USE_FOG ? linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor) : color;
}
