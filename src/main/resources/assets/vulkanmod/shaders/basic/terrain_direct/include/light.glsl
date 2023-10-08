#ifndef COMMON_H
#define COMMON_H
//#define MAX_OFFSET_COUNT 512
const uint MAX_OFFSET_COUNT = 512u;

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texelFetch(lightMap, bitfieldExtract(uv, 4, 8), 0);
}
#endif
