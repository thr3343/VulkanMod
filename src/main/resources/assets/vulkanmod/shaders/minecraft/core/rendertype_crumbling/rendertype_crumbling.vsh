#version 460

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
layout(location = 4) in vec3 Normal;

layout(binding = 0) uniform readonly UniformBufferObject {
   mat4 MVP[8];
};

layout(location = 0) out invariant flat uint baseInstance;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) out vec2 texCoord0;

void main() {
    gl_Position = MVP[gl_BaseInstance & 7] * vec4(Position, 1.0);
    baseInstance = gl_BaseInstance >> 16;
    vertexColor = Color;
    texCoord0 = UV0;
    //texCoord2 = UV2;
    //normal = (MVP * vec4(Normal, 0.0)).xyz;
}

/*
#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord0;
out vec2 texCoord2;
out vec4 normal;


*/
