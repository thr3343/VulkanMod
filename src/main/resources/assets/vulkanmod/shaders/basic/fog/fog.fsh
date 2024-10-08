#version 450
#extension GL_KHR_shader_subgroup_ballot : enable
layout(early_fragment_tests) in;


layout(input_attachment_index = 0, binding =0) uniform subpassInput Color;

layout(location = 0) out vec4 fragColor;
const float x = 0.99975;
const float z = 1-x;
const vec3 fogColor = vec3(1,0,1);
void main() {

    fragColor = subpassLoad(Color);
}
