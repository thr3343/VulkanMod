#version 460

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;

layout(binding = 0) uniform readonly UniformBufferObject {
   mat4 MVP[32];
};

layout(location = 0) invariant flat out uint baseInstance;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) out vec2 texCoord0;
layout(location = 3) out float vertexDistance;

void main() {
    gl_Position = MVP[gl_BaseInstance & 31] * vec4(Position, 1.0);
    baseInstance = gl_BaseInstance >> 16;

    vertexColor = Color;
    texCoord0 = UV0;
    vertexDistance = length((MVP[(gl_BaseInstance+1) & 31] * vec4(Position, 1.0)).xyz);
}

/*
#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    gl_Position = MVP * vec4(Position, 1.0);


    vertexColor = Color;
    texCoord0 = UV0;
}
*/
