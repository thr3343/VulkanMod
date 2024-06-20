#version 450
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_KHR_shader_subgroup_ballot : enable
#include "light.glsl"

layout(binding = 3, set = 1) uniform sampler2DArray Sampler0[];


layout(location = 0) in flat uint textureIndex;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec2 texCoord0;
//layout(location = 3) in vec4 normal;

layout(location = 0) out vec4 fragColor;

void main() {

    vec4 color = texture(Sampler0[nonuniformEXT(textureIndex>>10)], vec3(texCoord0, textureIndex&1023));
    if (color.a < 0.5f) {
        discard;
    }
    fragColor = color * vertexColor;
}
