package net.vulkanmod.render.shader;

import net.vulkanmod.vulkan.shader.PipelineConfig;
import net.vulkanmod.vulkan.shader.SPIRVUtils;

import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_ALL_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;

public class PipelineConfigs {
    public static final PipelineConfig.UB TERRAIN_UB0 = PipelineConfig.UB.builder(0, VK_SHADER_STAGE_VERTEX_BIT)
                                                    .addUniform("mat4", "MVP")
                                                    .build();

    public static final PipelineConfig.UB TERRAIN_UB1 = PipelineConfig.UB.builder(1, VK_SHADER_STAGE_ALL_GRAPHICS)
                                                                  .addUniform("vec4", "FogColor")
                                                                  .addUniform("float", "FogEnvironmentalStart")
                                                                  .addUniform("float", "FogEnvironmentalEnd")
                                                                  .addUniform("float", "FogRenderDistanceStart")
                                                                  .addUniform("float", "FogRenderDistanceEnd")
                                                                  .addUniform("float", "FogSkyEnd")
                                                                  .addUniform("float", "FogCloudsEnd")
                                                                  .addUniform("float", "AlphaCutout")
                                                                  .build();

    public static final PipelineConfig.UB TERRAIN_PC = PipelineConfig.UB.builder(0, VK_SHADER_STAGE_VERTEX_BIT) // Binding ignored
                                                                 .addUniform("vec3", "ModelOffset")
                                                                  .build();

    public static final PipelineConfig TERRAIN = PipelineConfig.builder()
                                                        .withShader(SPIRVUtils.ShaderKind.VERTEX_SHADER, "terrain/terrain")
                                                        .withShader(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, "terrain/terrain")
                                                        .addUB(TERRAIN_UB0)
                                                        .addUB(TERRAIN_UB1)
                                                        .setPushConstants(TERRAIN_PC)
                                                        .addImageDescriptor(2, "sampler2D", "Sampler0", 0)
                                                        .addImageDescriptor(3, "sampler2D", "LightTexture", 2)
                                                        .build();

    static final PipelineConfig TERRAIN_EARLY_Z_CONFIG = PipelineConfig.builder()
                                                                       .withShader(SPIRVUtils.ShaderKind.VERTEX_SHADER, "terrain/terrain")
                                                                       .withShader(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, "terrain_earlyZ/terrain_earlyZ")
                                                                       .addUB(TERRAIN_UB0)
                                                                       .addUB(TERRAIN_UB1)
                                                                       .setPushConstants(TERRAIN_PC)
                                                                       .addImageDescriptor(3, "sampler2D", "Sampler0", 0)
                                                                       .addImageDescriptor(4, "sampler2D", "LightTexture", 2)
                                                                       .build();

}
