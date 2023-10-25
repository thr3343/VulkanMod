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
    CUTOUT_MIPPED(RenderType.cutoutMipped(), 0.5f),
    TRANSLUCENT(RenderType.translucent(), 0.0f);

    public static final TerrainRenderType[] VALUES = TerrainRenderType.values();

    public static final EnumSet<TerrainRenderType> COMPACT_RENDER_TYPES = EnumSet.of(CUTOUT_MIPPED, TRANSLUCENT);
//    public static final ObjectArrayList<RenderType> SEMI_COMPACT_RENDER_TYPES = new ObjectArrayList<>();

    final float alphaCutout;
    public final int maxSize;

    TerrainRenderType(RenderType renderType, float alphaCutout) {
        this.alphaCutout = alphaCutout;
        this.maxSize=renderType.bufferSize();
    }

    public static TerrainRenderType get(String renderType) {
        return switch (renderType)
        {
            case "solid", "cutout", "cutout_mipped" -> CUTOUT_MIPPED;
            case "translucent", "tripwire" -> TRANSLUCENT;
            default -> throw new IllegalStateException("Unexpected value: " + renderType);
        };
    }
}
