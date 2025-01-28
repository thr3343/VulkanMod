package net.vulkanmod.interfaces;

import net.minecraft.client.renderer.ShaderInstance;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.util.MappedBuffer;

import java.util.function.Supplier;

public interface ShaderMixed {

    static ShaderMixed of(ShaderInstance compiledShaderProgram) {
        return (ShaderMixed) compiledShaderProgram;
    }

    void setPipeline(GraphicsPipeline graphicsPipeline);

    GraphicsPipeline getPipeline();

    void setupUniformSuppliers(UBO ubo);

    Supplier<MappedBuffer> getUniformSupplier(String name);

    void setDoUniformsUpdate();
}
