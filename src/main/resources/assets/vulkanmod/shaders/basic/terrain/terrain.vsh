#version 460

#include "light.glsl"

layout(binding = 0) uniform UniformBufferObject {
    vec3 dummy;
};

layout(push_constant) uniform pushConstant {
    mat4 MVP;
    mat4 ModelViewMat;
};

layout(binding = 3) uniform sampler2D Sampler2;


layout(location = 0) out float vertexDistance;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) out vec2 texCoord0;
//layout(location = 3) out vec4 normal;

//Compressed Vertex
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in uint UV0;
layout(location = 3) in ivec2 UV2;
//layout(location = 4) in vec3 Normal;

const float UV_INV = 1.0 / 65536.0;
const vec3 FP16_MAX_EXPONENT = vec3(1.0/1024.0);

void main() {
    const vec3 baseOffset = bitfieldExtract(ivec3(gl_InstanceIndex)>> ivec3(0, 16, 8), 0, 8);
    const vec3 pos = fma(Position, FP16_MAX_EXPONENT, baseOffset);
    const vec4 xyz = vec4(pos, 1);
    gl_Position = MVP * xyz;

    vertexDistance = length((ModelViewMat * xyz).xyz);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = unpackUnorm2x16(UV0);
//    normal = MVP * vec4(Normal, 0.0);
}

//Default Vertex
//layout(location = 0) in vec3 Position;
//layout(location = 1) in vec4 Color;
//layout(location = 2) in vec2 UV0;
//layout(location = 3) in ivec2 UV2;
//layout(location = 4) in vec3 Normal;

//void main() {
//    gl_Position = MVP * vec4(Position, 1.0);
//
//    vertexDistance = length((ModelViewMat * vec4(Position, 1.0)).xyz);
//    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
//    texCoord0 = UV0;
//    //    normal = MVP * vec4(Normal, 0.0);
//}