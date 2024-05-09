#version 460

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;
const vec2 ScreenSize = vec2(1920, 1080);
const float LineWidth = 2.0f;

layout(binding = 0) uniform readonly UniformBufferObject {
   mat4 ModelViewMat[16];
};

layout(location = 1) out float vertexDistance;
layout(location = 0) out vec4 vertexColor;

const float VIEW_SHRINK = 1.0 - (1.0 / 256.0);
const mat4 VIEW_SCALE = mat4(
    VIEW_SHRINK, 0.0, 0.0, 0.0,
    0.0, VIEW_SHRINK, 0.0, 0.0,
    0.0, 0.0, VIEW_SHRINK, 0.0,
    0.0, 0.0, 0.0, 1.0
);

void main() {
    vec4 linePosStart = ModelViewMat[gl_BaseInstance & 7] * vec4(Position, 1.0);
    vec4 linePosEnd = ModelViewMat[gl_BaseInstance & 7] * vec4(Position + Normal, 1.0);

    vec3 ndc1 = linePosStart.xyz / linePosStart.w;
    vec3 ndc2 = linePosEnd.xyz / linePosEnd.w;

    vec2 lineScreenDirection = normalize((ndc2.xy - ndc1.xy) * ScreenSize);
    vec2 lineOffset = vec2(-lineScreenDirection.y, lineScreenDirection.x) * LineWidth / ScreenSize;

    if (lineOffset.x < 0.0) {
        lineOffset *= -1.0;
    }

    int div = (gl_VertexIndex / 2);
    if (gl_VertexIndex - div * 2 == 0) {
        gl_Position = vec4((ndc1 + vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);
    } else {
        gl_Position = vec4((ndc1 - vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);
    }


    vertexColor = Color;
}

// #version 150
//
// in vec3 Position;
// in vec4 Color;
// in vec3 Normal;
//
// uniform mat4 ModelViewMat;
// uniform mat4 ProjMat;
// uniform float LineWidth;
// uniform vec2 ScreenSize;
//
// out float vertexDistance;
// out vec4 vertexColor;


