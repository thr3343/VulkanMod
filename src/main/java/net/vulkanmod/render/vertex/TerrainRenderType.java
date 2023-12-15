package net.vulkanmod.render.vertex;

import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.VRenderSystem;

import java.util.EnumSet;

public enum TerrainRenderType {
    SOLID(RenderType.solid(), 0.0f, RenderType.BIG_BUFFER_SIZE),
    CUTOUT_MIPPED(RenderType.cutoutMipped(), 0.5f, RenderType.SMALL_BUFFER_SIZE),
    CUTOUT(RenderType.cutout(), 0.1f, RenderType.SMALL_BUFFER_SIZE),
    TRANSLUCENT(RenderType.translucent(), 0.0f, RenderType.MEDIUM_BUFFER_SIZE),
    TRIPWIRE(RenderType.tripwire(), 0.1f, RenderType.MEDIUM_BUFFER_SIZE);

    public static final TerrainRenderType[] VALUES = TerrainRenderType.values();

    public static final EnumSet<TerrainRenderType> COMPACT_RENDER_TYPES = EnumSet.of(CUTOUT_MIPPED, TRANSLUCENT);
    public static final EnumSet<TerrainRenderType> ALL_RENDER_TYPES = EnumSet.allOf(TerrainRenderType.class);

    final float alphaCutout;
    public final int maxSize;  //Not sure if this should be changed to UINT16_INDEX_MAX * vertexSize
    public final int initialSize; //Ignored W/ Per RenderTy[e AreaBuffers

    TerrainRenderType(RenderType renderType, float alphaCutout, int initialSize) {
        this.alphaCutout = alphaCutout;
        this.maxSize = renderType.bufferSize();
        this.initialSize = initialSize;
    }

    public static EnumSet<TerrainRenderType> getActiveLayers() {
        return !Initializer.CONFIG.fastLeavesFix ? COMPACT_RENDER_TYPES : ALL_RENDER_TYPES;
    }

    public void setCutoutUniform() {
        VRenderSystem.alphaCutout = this.alphaCutout;
    }

    public static TerrainRenderType get(String renderType) {
        return switch (renderType)
        {
            case "solid" -> SOLID;
            case "cutout_mipped" -> CUTOUT_MIPPED;
            case "cutout" -> CUTOUT;
            case "translucent" -> TRANSLUCENT;
            case "tripwire" -> TRIPWIRE;
            default -> throw new IllegalStateException("Unexpected value: " + renderType);
        };
    }
}
