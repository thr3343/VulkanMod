package net.vulkanmod.vulkan.passes;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

import static org.lwjgl.vulkan.VK10.*;

public class LegacyMainPass implements MainPass {
    public static LegacyMainPass PASS = new LegacyMainPass();

    private RenderTarget mainTarget;

    LegacyMainPass() {
        this.mainTarget = Minecraft.getInstance().getMainRenderTarget();
    }

    @Override
    public void begin(VkCommandBuffer commandBuffer, MemoryStack stack) {
    }

    @Override
    public void mainTargetBindWrite() {
        // Caching main target for efficiency
        mainTarget = mainTarget.orElseThrow(() -> new IllegalStateException("Main target not set"));
        mainTarget.bindWrite(true);
    }

    @Override
    public void mainTargetUnbindWrite() {
        mainTarget.unbindWrite();
    }

    @Override
    public void end(VkCommandBuffer commandBuffer) {
        // Handling errors more gracefully
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Renderer.getInstance().endRenderPass(commandBuffer);

            mainTarget.bindRead();

            SwapChain swapChain = Vulkan.getSwapChain();
            swapChain.colorAttachmentLayout(stack, commandBuffer, Renderer.getCurrentImage());

            // Refactoring for clarity
            swapChain.beginRenderPass(commandBuffer, stack);
            Renderer.getInstance().setBoundFramebuffer(swapChain);

            VkViewport.Buffer pViewport = swapChain.viewport(stack);
            vkCmdSetViewport(commandBuffer, 0, pViewport);

            VkRect2D.Buffer pScissor = swapChain.scissor(stack);
            vkCmdSetScissor(commandBuffer, 0, pScissor);

            // Disable blend for legacy pass
            VRenderSystem.disableBlend();

            mainTarget.blitToScreen(swapChain.getWidth(), swapChain.getHeight());
        } catch (RuntimeException e) {
            // More specific exception handling
            System.err.println("Failed to record main pass: " + e.getMessage());
        }

        // Handling end command buffer errors more gracefully
        int result = vkEndCommandBuffer(commandBuffer);
        if (result != VK_SUCCESS) {
            System.err.println("Failed to end command buffer: " + result);
        }
    }
}
