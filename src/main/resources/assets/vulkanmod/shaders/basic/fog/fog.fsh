#version 450
#extension GL_KHR_shader_subgroup_ballot : enable
layout(early_fragment_tests) in;


layout(input_attachment_index = 0, binding =0) uniform subpassInput Depth;


layout(location = 0) out vec4 fragColor;

void main() {
   //fragColor = subpassLoad(Color).rgba;
    //vec3 color = subpassLoad(Color).rgb;

    if(subgroupBroadcastFirst(subpassLoad(Depth).r) < 1) discard;

    fragColor = vec4(0,0,1, 1);

}
