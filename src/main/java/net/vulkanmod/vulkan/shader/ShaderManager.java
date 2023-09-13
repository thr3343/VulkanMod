package net.vulkanmod.vulkan.shader;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.render.vertex.CustomVertexFormat;

import static net.vulkanmod.vulkan.shader.ShaderSPIRVUtils.compileShaderFile;

public class ShaderManager {
    public static VertexFormat TERRAIN_VERTEX_FORMAT;

    public static ShaderManager shaderManager;
    private final ShaderSPIRVUtils.SPIRV vertShaderSPIRV1 = compileShaderFile("basic/terrain_direct/terrain_direct" + ".vsh", ShaderSPIRVUtils.ShaderKind.VERTEX_SHADER);
    private final ShaderSPIRVUtils.SPIRV fragShaderSPIRV1 = compileShaderFile("basic/terrain_direct/terrain_direct_solid" + ".fsh", ShaderSPIRVUtils.ShaderKind.FRAGMENT_SHADER);
    private final ShaderSPIRVUtils.SPIRV fragShaderSPIRV2 = compileShaderFile("basic/terrain_direct/terrain_direct_translucent" + ".fsh", ShaderSPIRVUtils.ShaderKind.FRAGMENT_SHADER);

    public static void init() {
        setTerrainVertexFormat(CustomVertexFormat.COMPRESSED_TERRAIN);
        shaderManager = new ShaderManager();
    }

    public static void setTerrainVertexFormat(VertexFormat format) {
        TERRAIN_VERTEX_FORMAT = format;
    }

    public static ShaderManager getInstance() { return shaderManager; }

//    Pipeline terrainIndirectShader;
    public final Pipeline terrainDirectShader;
    public final Pipeline terrainDirectShader2;

    public ShaderManager() {
        //        this.terrainShader = createPipeline("terrain");

        this.terrainDirectShader = createPipeline("terrain_direct", vertShaderSPIRV1, fragShaderSPIRV1);
        this.terrainDirectShader2 = createPipeline("terrain_direct", vertShaderSPIRV1, fragShaderSPIRV2);
    }

    private Pipeline createPipeline(String name, ShaderSPIRVUtils.SPIRV vertShaderSPIRV11, ShaderSPIRVUtils.SPIRV fragShaderSPIRV11) {
        String path = String.format("basic/%s/%s", name, name);

        Pipeline.Builder pipelineBuilder = new Pipeline.Builder(TERRAIN_VERTEX_FORMAT, path);
        pipelineBuilder.parseBindingsJSON();
        pipelineBuilder.compileShaders2(vertShaderSPIRV11, fragShaderSPIRV11);
        return pipelineBuilder.createPipeline();
    }


    //    public Pipeline getTerrainIndirectShader(RenderType renderType) {
//        return terrainIndirectShader;
//    }

    public void destroyPipelines() {
//        this.terrainIndirectShader.cleanUp();
        this.terrainDirectShader.cleanUp();
    }
}
