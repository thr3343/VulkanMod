package net.vulkanmod.render.chunk.build.thread;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.vulkanmod.render.shader.PipelineManager;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.TerrainBuilder;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.render.vertex.VertexBuilder;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public class ThreadBuilderPack {
    private static Function<TerrainRenderType, TerrainBuilder> terrainBuilderConstructor;

    public static void defaultTerrainBuilderConstructor() {
        terrainBuilderConstructor = renderType -> {
            int size = TerrainRenderType.getLayer(renderType)
                                        .bufferSize() / DefaultVertexFormat.BLOCK.getVertexSize();

            boolean compressedFormat = PipelineManager.terrainVertexFormat == CustomVertexFormat.COMPRESSED_TERRAIN;
            VertexBuilder vertexBuilder = compressedFormat ? new VertexBuilder.CompressedVertexBuilder() : new VertexBuilder.DefaultVertexBuilder();
            return new TerrainBuilder(size, vertexBuilder);
        };
    }

    public static void setTerrainBuilderConstructor(Function<TerrainRenderType, TerrainBuilder> constructor) {
        terrainBuilderConstructor = constructor;
    }

    private final Map<TerrainRenderType, TerrainBuilder> builders;

    public ThreadBuilderPack() {
        var map = new EnumMap<TerrainRenderType, TerrainBuilder>(TerrainRenderType.class);
        Arrays.stream(TerrainRenderType.values()).forEach(
                terrainRenderType -> map.put(terrainRenderType,
                        terrainBuilderConstructor.apply(terrainRenderType))
        );
        builders = map;
    }

    public TerrainBuilder builder(TerrainRenderType renderType) {
        return this.builders.get(renderType);
    }

    public void freeAll() {
        this.builders.values().forEach(TerrainBuilder::free);
    }

}
