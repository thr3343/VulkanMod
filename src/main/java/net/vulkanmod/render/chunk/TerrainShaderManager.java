package net.vulkanmod.render.chunk;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;

import java.util.function.Consumer;
import java.util.function.Function;

public abstract class TerrainShaderManager {
    public static VertexFormat TERRAIN_VERTEX_FORMAT;

    public static void setTerrainVertexFormat(VertexFormat format) {
        TERRAIN_VERTEX_FORMAT = format;
    }

    static GraphicsPipeline terrainIndirectShader;
    public static GraphicsPipeline terrainDirectShader;

    private static Function<TerrainRenderType, GraphicsPipeline> shaderGetter;

    public static void init() {
        setTerrainVertexFormat(CustomVertexFormat.COMPRESSED_TERRAIN);
        createBasicPipelines();
        setDefaultShader();
    }

    public static void setDefaultShader() {
        setShaderGetter(renderType -> Initializer.CONFIG.indirectDraw ? terrainIndirectShader : terrainDirectShader);
    }

    private static void createBasicPipelines() {
        terrainIndirectShader = createPipeline("terrain_indirect");
        terrainDirectShader = createPipeline("terrain_direct");
    }

    private static GraphicsPipeline createPipeline(String name) {
        String path = String.format("basic/%s/%s", name, name);

        Pipeline.Builder pipelineBuilder = new Pipeline.Builder(CustomVertexFormat.COMPRESSED_TERRAIN, path);
        pipelineBuilder.parseBindingsJSON();
        pipelineBuilder.compileShaders();
        return pipelineBuilder.createGraphicsPipeline();
    }

    public static GraphicsPipeline getTerrainShader(TerrainRenderType renderType) {
        return shaderGetter.apply(renderType);
    }

    public static void setShaderGetter(Function<TerrainRenderType, GraphicsPipeline> consumer) {
        shaderGetter = consumer;
    }

    public static GraphicsPipeline getTerrainDirectShader() {
        return terrainDirectShader;
    }

    public static GraphicsPipeline getTerrainIndirectShader() {
        return terrainIndirectShader;
    }

    public static void destroyPipelines() {
        terrainIndirectShader.cleanUp();
        terrainDirectShader.cleanUp();
    }
}
