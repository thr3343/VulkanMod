#version 450
#include "fog.glsl"

layout(binding = 3) uniform sampler2D Sampler0[];

layout(binding = 1) uniform UBO{
    layout(offset = 16) float FogStart;
    layout(offset = 20) float FogEnd;
    layout(offset = 24) float GlintAlpha;
};

layout(push_constant) readonly uniform pushConstant{
    layout(offset = 32) vec4 ColorModulator;
};

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0[7], texCoord0) * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    float fade = linear_fog_fade(vertexDistance, FogStart, FogEnd) * GlintAlpha;
    fragColor = vec4(color.rgb * fade, color.a);
}