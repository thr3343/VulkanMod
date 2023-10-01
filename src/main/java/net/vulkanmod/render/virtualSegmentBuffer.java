package net.vulkanmod.render;

import net.vulkanmod.render.vertex.TerrainRenderType;

public record virtualSegmentBuffer(int areaGlobalIndex, int subIndex, int i2, int size_t, long allocation,
                                   TerrainRenderType r) {

}
