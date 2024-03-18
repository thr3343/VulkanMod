package net.vulkanmod.vulkan.passes;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class LegacyMainPass implements MainPass {
    public static LegacyMainPass PASS = new LegacyMainPass();

    private RenderTarget mainTarget;

    private final RenderPass renderPass;

    LegacyMainPass() {
        this.mainTarget = Minecraft.getInstance().getMainRenderTarget();

        // Create a new RenderPass needed in case of main target rebinding
        SwapChain swapChain = Vulkan.getSwapChain();
        RenderPass.Builder builder = RenderPass.builder(swapChain);
        builder.getColorAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE);
        builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE);
        builder.getColorAttachmentInfo().setFinalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR); //According to Sync Validation must match Renderpass layout to Fix sync hazards

        this.renderPass = builder.build();
    }

    @Override
    public void begin(VkCommandBuffer commandBuffer, MemoryStack stack) {
        SwapChain swapChain = Vulkan.getSwapChain();

        if(Renderer.getInstance().getBoundRenderPass() == swapChain.getMainRenderPass())
            return;
        else
            Renderer.getInstance().endRenderPass();

//        swapChain.colorAttachmentLayout(stack, commandBuffer, Renderer.getCurrentImage());

        swapChain.beginRenderPass(commandBuffer, stack);
        Renderer.getInstance().setBoundFramebuffer(swapChain);

        VkViewport.Buffer pViewport = swapChain.viewport(stack);
        vkCmdSetViewport(commandBuffer, 0, pViewport);

        VkRect2D.Buffer pScissor = swapChain.scissor(stack);
        vkCmdSetScissor(commandBuffer, 0, pScissor);
    }

    @Override
    public void end(VkCommandBuffer commandBuffer) {
        Renderer.getInstance().endRenderPass(commandBuffer);

        try(MemoryStack stack = MemoryStack.stackPush()) {
            Vulkan.getSwapChain().presentLayout(stack, commandBuffer, Renderer.getCurrentImage());
        }

        int result = vkEndCommandBuffer(commandBuffer);
        if(result != VK_SUCCESS) {
            throw new RuntimeException("Failed to record command buffer:" + result);
        }
    }

    public void rebindMainTarget() {
        SwapChain swapChain = Vulkan.getSwapChain();
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();

        // Do not rebind if the framebuffer is already bound
        RenderPass boundRenderPass = Renderer.getInstance().getBoundRenderPass();
        if(boundRenderPass == swapChain.getMainRenderPass() || boundRenderPass == this.renderPass)
            return;

        Renderer.getInstance().endRenderPass(commandBuffer);

        try(MemoryStack stack = MemoryStack.stackPush()) {
            swapChain.beginRenderPass(commandBuffer, this.renderPass, stack);
        }

    }

    @Override
    public void bindAsTexture() {
        SwapChain swapChain = Vulkan.getSwapChain();
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();

        // Check if render pass is using the framebuffer
        RenderPass boundRenderPass = Renderer.getInstance().getBoundRenderPass();
        if(boundRenderPass == swapChain.getMainRenderPass() || boundRenderPass == this.renderPass)
            Renderer.getInstance().endRenderPass(commandBuffer);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            swapChain.readOnlyLayout(stack, commandBuffer, Renderer.getCurrentImage());
        }

        VTextureSelector.bindTexture(swapChain.getColorAttachment());
    }
}
