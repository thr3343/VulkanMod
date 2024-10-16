#version 460

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(binding = 0) uniform UniformBufferObject {
   mat4 MVP[32];


};

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out float vertexDistance;

void main() {
    gl_Position = MVP[gl_BaseInstance & 31] * vec4(Position, 1.0)
;

    vertexDistance = length((MVP[(gl_BaseInstance+1) & 31] * vec4(Position, 1.0)).xyz);
    vertexColor = Color;
}

/*
#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float vertexDistance;
out vec4 vertexColor;

void main() {
    gl_Position = MVP * vec4(Position, 1.0);


    vertexColor = Color;
}
*/
