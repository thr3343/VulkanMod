#version 450
#extension GL_KHR_shader_subgroup_ballot : enable
layout(early_fragment_tests) in;


layout(input_attachment_index = 0, binding =1) uniform subpassInput Depth;
layout(binding = 0) uniform UBO {
    vec3 FogColor;
};

layout(location = 0) out vec4 fragColor;
const float x = 0.99975;
const float z = 1-x;
const vec3 fogColor = vec3(1,0,1);
void main() {
   //fragColor = subpassLoad(Color).rgba;
    //vec3 color = subpassLoad(Color).rgb;
    const float depth = subgroupBroadcastFirst(subpassLoad(Depth).r);
    if(depth == 1 || depth < x) discard;

    //fragColor = vec4(fogColor, (depth - x) / z);
    fragColor = vec4(FogColor,  (depth - x) / z);
}
