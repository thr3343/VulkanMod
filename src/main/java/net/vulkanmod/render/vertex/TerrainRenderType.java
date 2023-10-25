package net.vulkanmod.render.vertex;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.vulkan.VRenderSystem;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.stream.Collectors;

public enum TerrainRenderType {
    SOLID(RenderType.solid(), 0.0f),
    CUTOUT_MIPPED(RenderType.cutoutMipped(), 0.5f),
    CUTOUT(RenderType.cutout(), 0.1f),
    TRANSLUCENT(RenderType.translucent(), 0.0f),
    TRIPWIRE(RenderType.tripwire(), 0.1f);

    public static final TerrainRenderType[] VALUES = TerrainRenderType.values();

    public static final EnumSet<TerrainRenderType> COMPACT_RENDER_TYPES = EnumSet.of(CUTOUT_MIPPED, TRANSLUCENT);
//    public static final ObjectArrayList<RenderType> SEMI_COMPACT_RENDER_TYPES = new ObjectArrayList<>();

    final float alphaCutout;
    public final int maxSize;

    TerrainRenderType(RenderType renderType, float alphaCutout) {
        this.alphaCutout = alphaCutout;
        this.maxSize=renderType.bufferSize();
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
