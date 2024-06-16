#version 450
#include "fog.glsl"

layout(binding = 3) uniform sampler2D Sampler0[];



layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord0;


layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0[9], clamp(texCoord0, 0.0, 1.0));
    color *= vertexColor;
    fragColor = color;
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
in vec2 texCoord0;

out vec4 fragColor;
*/


