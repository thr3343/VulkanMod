vec4 projection_from_position(vec4 position) {
    vec4 projection = position * 0.5;
    projection.xy += projection.w;
    return projection;
}
