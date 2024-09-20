package net.vulkanmod.vulkan.pass;

import net.vulkanmod.vulkan.framebuffer.Framebuffer2;
import net.vulkanmod.vulkan.framebuffer.RenderPass2;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

public interface MainPass {

    void begin(VkCommandBuffer commandBuffer, MemoryStack stack);

    void end(VkCommandBuffer commandBuffer);

    default void mainTargetBindWrite() {}

    default void mainTargetUnbindWrite() {}

    default void rebindMainTarget() {}

    default void bindAsTexture() {}

    default int getColorAttachmentGlId() {
        return -1;
    }
    RenderPass2 getMainRenderPass();
    Framebuffer2 getMainFrameBuffer();

}
