#version 450
layout (constant_id = 0) const bool USE_FOG = true;
#include "light.glsl"

layout(binding = 0) uniform UniformBufferObject {
   mat4 MVP;
   mat4 ProjMat;
};

layout(binding = 1) uniform UBO{
    vec4 FogColor;
    float FogStart;
    float FogEnd;
};

layout(binding = 2) uniform sampler2D Sampler0;

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    color *= vertexColor;
    float fragmentDistance = -ProjMat[3].z / ((gl_FragCoord.z) * -2.0 + 1.0 - ProjMat[2].z);
    fragColor = USE_FOG ? linear_fog(color, fragmentDistance, FogStart, FogEnd, FogColor) : color;
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


