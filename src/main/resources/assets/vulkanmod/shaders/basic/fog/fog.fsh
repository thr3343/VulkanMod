#version 450
#extension GL_KHR_shader_subgroup_quad: enable
layout(early_fragment_tests) in;

layout(input_attachment_index = 0, binding = 1) uniform subpassInput Color;
layout(input_attachment_index = 1, binding = 2) uniform subpassInput Depth;

layout(binding = 0) uniform UBO {
    vec4 FogColor;
    float FogStart;
    float FogEnd;
};

layout(location = 0) out vec4 fragColor;

void main() {
   //fragColor = subpassLoad(Color).rgba;
    vec3 color = subpassLoad(Color).rgb;
    float depth = subpassLoad(Depth).r;

    fragColor = vec4(depth == 1 ? color : mix(color, FogColor.rgb, smoothstep(0.99875, 1, depth)), 1.0f);

}
