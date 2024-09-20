#version 450
layout(early_fragment_tests) in;
layout(input_attachment_index = 0, binding = 0) uniform subpassInput Color;
//layout(input_attachment_index = 1, binding = 1) uniform subpassInput Depth;



layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = subpassLoad(Color).bgra;
}
