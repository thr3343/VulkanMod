#version 450
#include "fog.glsl"

layout(binding = 3) uniform sampler2D Sampler0[];

layout(binding = 1) uniform UBO{
    vec4 ColorModulator;
    float FogStart;
    float FogEnd;
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec4 overlayColor;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in float vertexDistance;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0[0], texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    color *= vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    fragColor = color * linear_fog_fade(vertexDistance, FogStart, FogEnd);
}