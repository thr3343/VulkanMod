#version 450

layout(binding = 1) uniform UBO{
    vec4 ColorModulator;
    vec4 FogColor;
    float FogStart;
    float FogEnd;
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in float vertexDistance;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;

    float alpha = smoothstep(FogEnd, FogStart, vertexDistance);
    fragColor = vec4(color.rgb, color.a * alpha);
}
