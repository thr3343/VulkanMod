package net.vulkanmod.render.chunk;

import net.vulkanmod.render.vertex.TerrainRenderType;

public record virtualSegment(int subIndex, int offset, int size, TerrainRenderType r) {

}
