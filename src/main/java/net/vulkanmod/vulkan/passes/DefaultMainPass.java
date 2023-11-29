package net.vulkanmod.vulkan.passes;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import net.vulkanmod.vulkan.queue.Queue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

import static org.lwjgl.vulkan.VK10.*;

public class DefaultMainPass implements MainPass {

    public static DefaultMainPass PASS = new DefaultMainPass();

    @Override
    public void begin(VkCommandBuffer commandBuffer, MemoryStack stack) {
        SwapChain swapChain = Vulkan.getSwapChain();
        Queue.GraphicsQueue.GigaBarrier(commandBuffer);
        swapChain.colorAttachmentLayout(stack, commandBuffer, Renderer.getCurrentImage());
        swapChain.beginRenderPass(commandBuffer, stack);
        Renderer.getInstance().setBoundFramebuffer(swapChain);

        VkViewport.Buffer pViewport = swapChain.viewport(stack);
        vkCmdSetViewport(commandBuffer, 0, pViewport);

        VkRect2D.Buffer pScissor = swapChain.scissor(stack);
        vkCmdSetScissor(commandBuffer, 0, pScissor);
    }

    @Override
    public void end(VkCommandBuffer commandBuffer) {

//        Framebuffer.endRenderPass(commandBuffer);
//
//        try (MemoryStack stack = stackPush()) {
//            this.hdrFinalFramebuffer.getColorAttachment()
//                    .transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
//
//            SwapChain swapChain = Vulkan.getSwapChain();
//
////            getSwapChain().colorAttachmentLayout(stack, commandBuffer, currentFrame);
//
////            swapChain.beginDynamicRendering(commandBuffer, stack);
//            swapChain.beginRenderPass(commandBuffer, stack);
//
//            clearAttachments(0x100, swapChain.width, swapChain.height);
//            VRenderSystem.disableDepthTest();
//        }
//
////        VRenderSystem.disableDepthTest();
//        VRenderSystem.disableCull();
//        RenderSystem.disableBlend();
//
//        DrawUtil.drawFramebuffer(this.blitGammaShader, this.hdrFinalFramebuffer.getColorAttachment());


        Queue.GraphicsQueue.GigaBarrier(commandBuffer);
//        try(MemoryStack stack = MemoryStack.stackPush()) {
//            Vulkan.getSwapChain().presentLayout(stack, commandBuffer, Renderer.getCurrentImage());
//        }
        Renderer.getInstance().endRenderPass(commandBuffer);


        int result = vkEndCommandBuffer(commandBuffer);
        if(result != VK_SUCCESS) {
            throw new RuntimeException("Failed to record command buffer:" + result);
        }
    }
}
