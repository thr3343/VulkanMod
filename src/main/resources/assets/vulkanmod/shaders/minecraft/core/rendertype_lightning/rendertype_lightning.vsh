#version 450

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(binding = 0) uniform readonly UniformBufferObject {
   mat4 MVP;
};

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out float vertexDistance;

void main() {
    gl_Position = MVP * vec4(Position, 1.0);


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
