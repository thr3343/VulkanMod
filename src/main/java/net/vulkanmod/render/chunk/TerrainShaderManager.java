package net.vulkanmod.render.chunk;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.build.ThreadBuilderPack;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;

import java.util.function.Function;

import static net.vulkanmod.render.vertex.TerrainRenderType.CUTOUT;
import static net.vulkanmod.render.vertex.TerrainRenderType.CUTOUT_MIPPED;
import static net.vulkanmod.vulkan.shader.SPIRVUtils.compileShaderAbsoluteFile;

public abstract class TerrainShaderManager {
    private static final String resourcePath1 = SPIRVUtils.class.getResource("/assets/vulkanmod/shaders/").toExternalForm();
    public static VertexFormat TERRAIN_VERTEX_FORMAT;

    public static void setTerrainVertexFormat(VertexFormat format) {
        TERRAIN_VERTEX_FORMAT = format;
    }

    public static GraphicsPipeline terrainShader2;
    public static GraphicsPipeline terrainShader;

    private static Function<TerrainRenderType, GraphicsPipeline> shaderGetter;

    public static void init() {
        setTerrainVertexFormat(CustomVertexFormat.COMPRESSED_TERRAIN);
        createBasicPipelines();
        setDefaultShader();
        ThreadBuilderPack.defaultTerrainBuilderConstructor();
    }

    public static void setDefaultShader() {
        setShaderGetter(renderType -> terrainShader);
    }

    private static void createBasicPipelines() {
        terrainShader2 = createPipeline("terrain", "terrain_Z", "terrain_Z");
        terrainShader = createPipeline("terrain", "terrain", "terrain");
    }

    private static GraphicsPipeline createPipeline(String basePath, String vertPath, String fragPath) {
        String pathJ = String.format("basic/%s/%s", basePath, basePath);
        String pathV = String.format("basic/%s/%s", basePath, vertPath);
        String pathF = String.format("basic/%s/%s", basePath, fragPath);

        Pipeline.Builder pipelineBuilder = new Pipeline.Builder(CustomVertexFormat.COMPRESSED_TERRAIN, pathJ);
        pipelineBuilder.parseBindingsJSON();


        var vertShaderSPIRV = compileShaderAbsoluteFile(String.format("%s%s.vsh", resourcePath1, pathV), SPIRVUtils.ShaderKind.VERTEX_SHADER);
        var fragShaderSPIRV = compileShaderAbsoluteFile(String.format("%s%s.fsh", resourcePath1, pathF), SPIRVUtils.ShaderKind.FRAGMENT_SHADER);
        pipelineBuilder.compileShaders2(vertShaderSPIRV, fragShaderSPIRV);
        return pipelineBuilder.createGraphicsPipeline();
    }

    public static GraphicsPipeline getTerrainShader(TerrainRenderType renderType) {
        return renderType==CUTOUT?terrainShader:terrainShader2;
    }

    public static void setShaderGetter(Function<TerrainRenderType, GraphicsPipeline> consumer) {
        shaderGetter = consumer;
    }

    public static void destroyPipelines() {
//        terrainIndirectShader.cleanUp();
        terrainShader.cleanUp();
    }
}
