package net.vulkanmod.render.vertex;

import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.vulkan.VRenderSystem;

import java.util.*;

public enum TerrainRenderType {
//    SOLID(RenderType.solid(), 0.0f),
    CUTOUT_MIPPED(RenderType.cutoutMipped(), 0.5f),
//    CUTOUT(RenderType.cutout(), 0.1f),
    TRANSLUCENT(RenderType.translucent(), 0.0f);
//    TRIPWIRE(RenderType.tripwire(), 0.1f);

    public static final EnumSet<TerrainRenderType> COMPACT_RENDER_TYPES = EnumSet.of(CUTOUT_MIPPED, TRANSLUCENT);
//    public static final EnumSet<TerrainRenderType> SEMI_COMPACT_RENDER_TYPES = EnumSet.of(CUTOUT, CUTOUT_MIPPED, TRANSLUCENT);


    public final int maxSize;
    final float alphaCutout;

    TerrainRenderType(RenderType renderType, float alphaCutout) {
        this.maxSize = renderType.bufferSize();
        this.alphaCutout = alphaCutout;
    }

    public static TerrainRenderType get(String renderType) {
        return switch (renderType)
        {
            case "solid", "cutout_mipped", "cutout" -> CUTOUT_MIPPED;
            case "translucent", "tripwire" -> TRANSLUCENT;
            default -> throw new IllegalStateException("Unexpected value: " + renderType);
        };
    }
}
