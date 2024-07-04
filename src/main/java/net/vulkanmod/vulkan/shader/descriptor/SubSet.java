package net.vulkanmod.vulkan.shader.descriptor;

import org.lwjgl.system.MemoryStack;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;

public class SubSet {
    private static final int perSetMax = BindlessDescriptorSet.maxPerStageDescriptorSamplers;
    final DescriptorAbstractionArray initialisedFragSamplers;
    final DescriptorAbstractionArray initialisedVertSamplers;
    final long[] descriptorSets = new long[2];
    private final int baseOffset;  //Starting offset for FragSamplers
    int textureCounts;

    public SubSet(int baseOffset, int vertSize, int fragTextureLimit, int vertexSamplerId, int fragSamplerId) {
        this.baseOffset = baseOffset;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            allocSets(fragTextureLimit, stack);
        }

        initialisedFragSamplers = new DescriptorAbstractionArray(fragTextureLimit, VK_SHADER_STAGE_FRAGMENT_BIT, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, fragSamplerId);
        initialisedVertSamplers = new DescriptorAbstractionArray(vertSize, VK_SHADER_STAGE_VERTEX_BIT, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, vertexSamplerId);
    }
    //Can overwrite descSets where due to resetting descriptorPool
    void allocSets(int fragTextureLimit, MemoryStack stack) {
        for (int i = 0; i < 2; i++) {
            this.descriptorSets[i]= DescriptorManager.allocateDescriptorSet(stack, fragTextureLimit);
        }
    }

    public int registerTexture(int binding, int TextureID)
    {
        return (binding == 0 ? this.initialisedFragSamplers : initialisedVertSamplers)
                .registerTexture(TextureID, 0);
    }

    public void getSamplerArray(int binding, int texID, int SamplerIndex) {
        (binding == 0 ? this.initialisedFragSamplers : initialisedVertSamplers).registerImmutableTexture(texID, SamplerIndex);
    }

    public long getSetHandle(int frame) {
        return descriptorSets[frame];
    }

    public boolean checkCapacity() {
        return initialisedFragSamplers.checkCapacity();
    }

    public int TextureID2SamplerIdx(int binding, int TextureID) {
        return (binding == 0 ? this.initialisedFragSamplers : initialisedVertSamplers)
                .TextureID2SamplerIdx(TextureID);
    }

    public int getBaseIndex() {
        return this.baseOffset;
    }
}
