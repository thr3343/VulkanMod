package net.vulkanmod.render.chunk;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;

import java.util.function.Function;

import static net.vulkanmod.Initializer.LOGGER;
import static net.vulkanmod.vulkan.shader.SPIRVUtils.compileShaderAbsoluteFile;

public abstract class TerrainShaderManager {
    public static VertexFormat TERRAIN_VERTEX_FORMAT;

    public static void setTerrainVertexFormat(VertexFormat format) {
        TERRAIN_VERTEX_FORMAT = format;
    }

    static GraphicsPipeline terrainDirectShader2;
    public static GraphicsPipeline terrainDirectShader;

    private static Function<TerrainRenderType, GraphicsPipeline> shaderGetter;

    public static void init() {
        setTerrainVertexFormat(CustomVertexFormat.COMPRESSED_TERRAIN);
        createBasicPipelines();
        setDefaultShader();
    }

    public static void setDefaultShader() {
        setShaderGetter(renderType -> Initializer.CONFIG.indirectDraw ? terrainDirectShader2 : terrainDirectShader);
    }

    private static void createBasicPipelines() {
        String resourcePath = SPIRVUtils.class.getResource("/assets/vulkanmod/shaders/basic").toExternalForm();
        SPIRVUtils.SPIRV Vert = compileShaderAbsoluteFile(String.format("%s/%s/%s.vsh", resourcePath, "terrain_direct", "terrain_direct"), SPIRVUtils.ShaderKind.VERTEX_SHADER);
        SPIRVUtils.SPIRV Frag  = compileShaderAbsoluteFile(String.format("%s/%s/%s.fsh", resourcePath, "terrain_direct", "terrain_direct_solid"), SPIRVUtils.ShaderKind.FRAGMENT_SHADER);
        SPIRVUtils.SPIRV Frag2  = compileShaderAbsoluteFile(String.format("%s/%s/%s.fsh", resourcePath, "terrain_direct", "terrain_direct_translucent"), SPIRVUtils.ShaderKind.FRAGMENT_SHADER);
        terrainDirectShader = createPipeline(Vert, Frag);
        terrainDirectShader2 = createPipeline(Vert, Frag2);
    }

    private static GraphicsPipeline createPipeline(SPIRVUtils.SPIRV Vert, SPIRVUtils.SPIRV Frag) {

        Pipeline.Builder pipelineBuilder = new Pipeline.Builder(CustomVertexFormat.COMPRESSED_TERRAIN, String.format("basic/%s/%s", "terrain_direct", "terrain_direct"));
        pipelineBuilder.parseBindingsJSON();
        pipelineBuilder.compileShaders2(Vert, Frag);
        return pipelineBuilder.createGraphicsPipeline();
    }

    public static GraphicsPipeline getTerrainShader(TerrainRenderType renderType) {
        return renderType==TerrainRenderType.TRANSLUCENT ? terrainDirectShader2 : terrainDirectShader;
    }

    public static void setShaderGetter(Function<TerrainRenderType, GraphicsPipeline> consumer) {
        shaderGetter = consumer;
    }

    public static GraphicsPipeline getTerrainDirectShader() {
        return terrainDirectShader;
    }

    public static GraphicsPipeline getTerrainDirectShader2() {
        return terrainDirectShader2;
    }

    public static void destroyPipelines() {
        terrainDirectShader2.cleanUp();
        terrainDirectShader.cleanUp();
    }
}
