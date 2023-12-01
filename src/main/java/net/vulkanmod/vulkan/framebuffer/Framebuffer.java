package net.vulkanmod.vulkan.framebuffer;

import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public class Framebuffer {
    public static final int DEFAULT_FORMAT = VK_FORMAT_R8G8B8A8_UNORM;

//    private long id;

    protected int format;
    protected int depthFormat;
    protected int width, height;
    private VulkanImage colorAttachment;
    protected VulkanImage depthAttachment;

    //GL compatibility
    public Framebuffer(VulkanImage colorAttachment) {
        this.width = colorAttachment.width;
        this.height = colorAttachment.height;

        this.colorAttachment = colorAttachment;

        this.depthFormat = SwapChain.getDefaultDepthFormat();
        this.depthAttachment = VulkanImage.createDepthImage(depthFormat, this.width, this.height,
                VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                false, false);
    }

    public void bindAsTexture(VkCommandBuffer commandBuffer, MemoryStack stack) {
        this.colorAttachment.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        VTextureSelector.bindFramebufferTexture(this.colorAttachment);
    }



    public void cleanUp() {
        if(this.colorAttachment != null)
            this.colorAttachment.free();

        if(this.depthAttachment != null)
            this.depthAttachment.free();

    }


    public int getWidth() { return this.width; }

    public int getHeight() { return this.height; }

    public int getFormat() { return this.format; }

    public int getDepthFormat() { return this.depthFormat; }
}
