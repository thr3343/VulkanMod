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

layout(push_constant) readonly uniform  PushConstant{
   layout(offset = 32) vec4 ColorModulator;
};

layout(location = 0) flat in uint baseInstance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec4 overlayColor;
layout(location = 3) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    const uint uniformBaseInstance = subgroupBroadcastFirst(baseInstance);
    vec4 color = texture(Sampler0[uniformBaseInstance], texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= vertexColor * ColorModulator;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}

/*
#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec4 normal;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= vertexColor * ColorModulator;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
*/
