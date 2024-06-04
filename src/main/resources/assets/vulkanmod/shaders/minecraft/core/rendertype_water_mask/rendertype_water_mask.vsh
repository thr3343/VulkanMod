#version 460

layout(location = 0) in vec3 Position;

layout(binding = 0) uniform readonly UniformBufferObject {
   mat4 MVP[8];

};

void main() {
    gl_Position = MVP[gl_BaseInstance & 7] * vec4(Position, 1.0)
;
}

/*
#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    gl_Position = MVP * vec4(Position, 1.0);
}
*/
