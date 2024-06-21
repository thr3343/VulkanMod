package net.vulkanmod.vulkan.pass;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;

public class DefaultMainPass implements MainPass {

    public static DefaultMainPass create() {
        return new DefaultMainPass();
    }

    private final Framebuffer2 mainFramebuffer;




    DefaultMainPass() {
        this.mainFramebuffer = new Framebuffer2(Vulkan.getSwapChain().getWidth(), Vulkan.getSwapChain().getHeight());

        createRenderPasses();
    }

    @Override
    public RenderPass2 getMainRenderPass() {
        return mainFramebuffer.renderPass2;
    }

    @Override
    public Framebuffer2 getMainFrameBuffer() {
        return mainFramebuffer;
    }

    private void createRenderPasses() {
//        RenderPass.Builder builder = RenderPass.builder(this.mainFramebuffer);
//        builder.getColorAttachmentInfo().setFinalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        RenderPass2 mainRenderPass = new RenderPass2(AttachmentTypes.PRESENT_SAMPLED, AttachmentTypes.DEPTH_SAMPLED);

        this.mainFramebuffer.bindRenderPass(mainRenderPass);

//        // Create an auxiliary RenderPass needed in case of main target rebinding
//        builder = RenderPass.builder(this.mainFramebuffer);
//        builder.getColorAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE);
//        builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE);
//        builder.getColorAttachmentInfo().setFinalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
//
//        this.auxRenderPass = builder.build();
    }

    private void switchRenderPass(RenderPass2 mainRenderPass)
    {
        this.mainFramebuffer.bindRenderPass(mainRenderPass);
    }

    @Override
    public void begin(VkCommandBuffer commandBuffer, MemoryStack stack) {

        this.mainFramebuffer.beginRendering(commandBuffer, stack);

        VkViewport.Buffer pViewport = mainFramebuffer.viewport(stack);
        vkCmdSetViewport(commandBuffer, 0, pViewport);

        VkRect2D.Buffer pScissor = mainFramebuffer.scissor(stack);
        vkCmdSetScissor(commandBuffer, 0, pScissor);
    }

    @Override
    public void end(VkCommandBuffer commandBuffer) {
//        Renderer.getInstance().endRenderPass(commandBuffer);
        vkCmdEndRenderPass(commandBuffer);

        int result = vkEndCommandBuffer(commandBuffer);
        if(result != VK_SUCCESS) {
            throw new RuntimeException("Failed to record command buffer:" + result);
        }
    }

    public void rebindMainTarget() {
//        SwapChain swapChain = Vulkan.getSwapChain();
//        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
//
//        // Do not rebind if the framebuffer is already bound
//        RenderPass boundRenderPass = Renderer.getInstance().getBoundRenderPass();
//        if(boundRenderPass == this.mainRenderPass || boundRenderPass == this.auxRenderPass)
//            return;
//
//        Renderer.getInstance().endRenderPass(commandBuffer);
//
//        try(MemoryStack stack = MemoryStack.stackPush()) {
//            swapChain.beginRenderPass(commandBuffer, this.auxRenderPass, stack);
//        }

    }

    @Override
    public void bindAsTexture() {
//        SwapChain swapChain = Vulkan.getSwapChain();
//        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
//
//        // Check if render pass is using the framebuffer
//        RenderPass boundRenderPass = Renderer.getInstance().getBoundRenderPass();
//        if(boundRenderPass == this.mainRenderPass || boundRenderPass == this.auxRenderPass)
//            Renderer.getInstance().endRenderPass(commandBuffer);
//
//        try (MemoryStack stack = MemoryStack.stackPush()) {
//            swapChain.getColorAttachment().transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
//        }
//
//        VTextureSelector.bindTexture(swapChain.getColorAttachment());
    }

    public int getColorAttachmentGlId() {
        SwapChain swapChain = Vulkan.getSwapChain();
        return swapChain.getColorAttachmentGlId();
    }
}
