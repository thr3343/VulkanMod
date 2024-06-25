#version 460

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(binding = 0) uniform UniformBufferObject {
   mat4 MVP[32];
};

layout(location = 0) out float vertexDistance;
layout(location = 1) out vec2 texCoord0;

void main() {
    gl_Position = MVP[gl_BaseInstance & 31] * vec4(Position, 1.0)
;

    vertexDistance = length((MVP[(gl_BaseInstance+1) & 31] * vec4(Position, 1.0)).xyz);
    texCoord0 = (MVP[(gl_BaseInstance+2) & 31] * vec4(UV0, 0.0, 1.0)).xy;
}

/*
#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 TextureMat;

out float vertexDistance;
out vec2 texCoord0;

void main() {
    gl_Position = MVP[gl_BaseInstance & 31] * vec4(Position, 1.0)
;

    vertexDistance = length((MVP[(gl_BaseInstance+1) & 31] * vec4(Position, 1.0)).xyz);
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
}
*/
