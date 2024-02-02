package net.vulkanmod.vulkan.shader;

//Arrange to match the offical Struct layout of VkDescriptorSetLayoutBinding (For consitency+MemCpy+Alignmement+Acess patterns.locality optimisations)
public record DescriptorSetLayoutBinding(int binding,
                                         int descriptorType,
                                         int descriptorCount,
                                         int pImmutableSamplers,
                                         int stageFlags) {
}
