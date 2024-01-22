package net.vulkanmod.interfaces;

import net.vulkanmod.vulkan.texture.VulkanImage;

public interface VAbstractTextureI {

    void vulkanMod$bindTexture();

    void vulkanMod$setId(int i);

    VulkanImage vulkanMod$getVulkanImage();

    void vulkanMod$setVulkanImage(VulkanImage image);
}
