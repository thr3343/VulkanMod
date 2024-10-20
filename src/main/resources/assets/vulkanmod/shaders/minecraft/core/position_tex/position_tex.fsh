#version 450
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_KHR_shader_subgroup_ballot : enable
layout(binding = 3) uniform sampler2D Sampler0[];

layout(push_constant) readonly uniform pushConstant{
    layout(offset = 32) vec4 ColorModulator;
};

layout(location = 0) in flat uint baseInstance;
layout(location = 1) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    const uint uniformBaseInstance = subgroupBroadcastFirst(baseInstance);
    vec4 color = texture(Sampler0[uniformBaseInstance], texCoord0);
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
