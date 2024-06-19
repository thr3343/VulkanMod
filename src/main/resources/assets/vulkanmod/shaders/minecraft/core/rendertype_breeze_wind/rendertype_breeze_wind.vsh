#version 460

#include "fog.glsl"

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

layout(binding = 0) uniform UniformBufferObject {
    layout(offset = 0) mat4 MVP[8];
    layout(offset = 512) mat4 ModelViewMat;
    mat4 TextureMat;
};

layout(binding = 2) uniform sampler2D Sampler2;


layout(location = 0) out float vertexDistance;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) out vec4 lightMapColor;
layout(location = 3) out vec2 texCoord0;

void main() {
    gl_Position = MVP[gl_BaseInstance & 7] * vec4(Position, 1.0)
;

    vec4 pos = ModelViewMat * (Position, 1.0);
    //vertexDistance = fog_distance(pos.xyz, FogShape);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    vertexColor = Color * lightMapColor;

    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
}