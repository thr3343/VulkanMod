package net.vulkanmod.render.shader;

import net.vulkanmod.vulkan.shader.PipelineConfig;
import net.vulkanmod.vulkan.shader.SPIRVUtils;

import java.util.EnumSet;

import static org.lwjgl.vulkan.VK10.*;

public class PipelineConfigs {
    public static final PipelineConfig.UB TERRAIN_UB0 = PipelineConfig.UB.builder(0, VK_SHADER_STAGE_VERTEX_BIT)
                                                    .addUniform("mat4", "MVP")
                                                    .addUniform("int", "CurrentTime")
                                                    .build();

    public static final PipelineConfig.UB TERRAIN_UB1 = PipelineConfig.UB.builder(1, VK_SHADER_STAGE_FRAGMENT_BIT)
                                                                  .addUniform("vec4", "FogColor")
                                                                  .addUniform("float", "FogEnvironmentalStart")
                                                                  .addUniform("float", "FogEnvironmentalEnd")
                                                                  .addUniform("float", "FogRenderDistanceStart")
                                                                  .addUniform("float", "FogRenderDistanceEnd")
                                                                  .addUniform("float", "FogSkyEnd")
                                                                  .addUniform("float", "FogCloudsEnd")
                                                                  .addUniform("float", "AlphaCutout")
                                                                  .addUniform("vec2", "TextureSize")
                                                                  .addUniform("vec2", "TexelSize")
                                                                  .addUniform("int", "UseRgss")
                                                                  .build();

    public static final PipelineConfig.UB TERRAIN_PC = PipelineConfig.UB.builder(0, VK_SHADER_STAGE_VERTEX_BIT) // Binding ignored
                                                                 .addUniform("vec2", "ab")
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
                                                        .setSpecConstants(EnumSet.of(SPIRVUtils.SpecConstant.FAST_TEX))
                                                        .build();

    static final PipelineConfig TERRAIN_EARLY_Z_CONFIG = PipelineConfig.builder()
                                                                       .withShader(SPIRVUtils.ShaderKind.VERTEX_SHADER, "terrain/terrain")
                                                                       .withShader(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, "terrain_earlyZ/terrain_earlyZ")
                                                                       .addUB(TERRAIN_UB0)
                                                                       .addUB(TERRAIN_UB1)
                                                                       .setPushConstants(TERRAIN_PC)
                                                                       .addImageDescriptor(2, "sampler2D", "Sampler0", 0)
                                                                       .addImageDescriptor(3, "sampler2D", "LightTexture", 2)
                                                                       .setSpecConstants(EnumSet.of(SPIRVUtils.SpecConstant.FAST_TEX))
                                                                       .build();

}
