package net.vulkanmod.render;

import net.vulkanmod.render.vertex.TerrainRenderType;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;

public class VBOUtil {

    public static final VirtualBuffer TvirtualBufferIdx = new VirtualBuffer(16777216, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, TerrainRenderType.TRANSLUCENT);
}
