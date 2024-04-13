#version 450

#include "light.glsl"

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

layout(binding = 0) uniform UniformBufferObject {
   mat4 MVP;
   vec3 Light0_Direction;
   vec3 Light1_Direction;
};

layout(binding = 2) uniform sampler2D Sampler2;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 texCoord0;
layout(location = 2) out vec2 texCoord1;
layout(location = 3) out vec3 normal;
layout(location = 4) out float vertexDistance;

void main() {
    gl_Position = MVP * vec4(Position, 1.0);


    vertexColor = /*minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color) * **/ texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
    //texCoord1 = UV1;
    normal = (MVP * vec4(Normal, 0.0)).xyz;
}

/*
#version 150

#moj_import <light.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec2 texCoord1;
out vec4 normal;
*/

