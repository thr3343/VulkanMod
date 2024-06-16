#version 450
#include "fog.glsl"


layout(binding = 3) uniform sampler2D Sampler0[];

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0[4], texCoord0);
    color *= vertexColor;
    float fragmentDistance = -((gl_FragCoord.z) * -2.0 + 1.0);
    fragColor = color;
}

/*
#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;
*/


