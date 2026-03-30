package net.vulkanmod.render.vertex;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.ExtendedRenderType;
import net.vulkanmod.vulkan.VRenderSystem;

import java.util.EnumSet;
import java.util.function.Function;

public enum TerrainRenderType {
    SOLID(0.0f),
    CUTOUT(0.5f),
    TRANSLUCENT(0.01f),
    TRIPWIRE(0.1f);

    public static final TerrainRenderType[] VALUES = TerrainRenderType.values();

    public static final EnumSet<TerrainRenderType> COMPACT_RENDER_TYPES = EnumSet.of(CUTOUT, TRANSLUCENT);
    public static final EnumSet<TerrainRenderType> SEMI_COMPACT_RENDER_TYPES = EnumSet.of(SOLID, CUTOUT, TRANSLUCENT);

    private static Function<TerrainRenderType, TerrainRenderType> remapper;

    static {
        SEMI_COMPACT_RENDER_TYPES.add(CUTOUT);
        SEMI_COMPACT_RENDER_TYPES.add(TRANSLUCENT);

        COMPACT_RENDER_TYPES.add(TRANSLUCENT);
    }

    public final float alphaCutout;

    TerrainRenderType(float alphaCutout) {
        this.alphaCutout = alphaCutout;
    }

    public void setCutoutUniform() {
        VRenderSystem.alphaCutout = this.alphaCutout;
    }

    public static TerrainRenderType get(RenderType renderType) {
        return ((ExtendedRenderType)renderType).getTerrainRenderType();
    }

    public static TerrainRenderType get(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> SOLID;
            case CUTOUT -> CUTOUT;
            case TRANSLUCENT -> TRANSLUCENT;
            case TRIPWIRE -> TRIPWIRE;
        };
    }

    public static TerrainRenderType get(String name) {
        return switch (name) {
            case "solid" -> TerrainRenderType.SOLID;
            case "cutout" -> TerrainRenderType.CUTOUT;
            case "translucent" -> TerrainRenderType.TRANSLUCENT;
            case "tripwire" -> TerrainRenderType.TRIPWIRE;
            default -> null;
        };
    }

    public static ChunkSectionLayer getLayer(TerrainRenderType renderType) {
        return switch (renderType) {
            case SOLID -> ChunkSectionLayer.SOLID;
            case CUTOUT -> ChunkSectionLayer.CUTOUT;
            case TRANSLUCENT -> ChunkSectionLayer.TRANSLUCENT;
            case TRIPWIRE -> ChunkSectionLayer.TRIPWIRE;
        };
    }

    public static void updateMapping() {
        if (Initializer.CONFIG.uniqueOpaqueLayer) {
            remapper = (renderType) -> switch (renderType) {
                case SOLID, CUTOUT -> TerrainRenderType.CUTOUT;
                case TRANSLUCENT, TRIPWIRE -> TerrainRenderType.TRANSLUCENT;
            };
        } else {
            remapper = (renderType) -> switch (renderType) {
                case SOLID -> TerrainRenderType.SOLID;
                case CUTOUT -> TerrainRenderType.CUTOUT;
                case TRANSLUCENT, TRIPWIRE -> TerrainRenderType.TRANSLUCENT;
            };
        }
    }

    public static TerrainRenderType getRemapped(TerrainRenderType renderType) {
        return remapper.apply(renderType);
    }
}
