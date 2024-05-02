#version 450

#include "light.glsl"

layout(binding = 3) uniform sampler2DArray Sampler0[];


layout(location = 0) in vec4 vertexColor;
layout(location = 1) in float texCoord0;
//layout(location = 3) in vec4 normal;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 texCoordIndex = vec3(0,1,texCoord0);
    vec4 color = texture(Sampler0[3], texCoordIndex);
    if (color.a < 0.5f) {
        discard;
    }
    fragColor = color * vertexColor;
}
