package net.vulkanmod.vulkan.passes;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import static net.vulkanmod.vulkan.framebuffer.AttachmentTypes.PRESENT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;

public class DefaultMainPass implements MainPass {

    public static DefaultMainPass PASS = new DefaultMainPass();

    @Override
    public void begin(VkCommandBuffer commandBuffer, MemoryStack stack) {
        SwapChain swapChain = Vulkan.getSwapChain();
        swapChain.colorAttachmentLayout(stack, commandBuffer, Renderer.getCurrentImage());
/*TODO; try to separate the SwapChain from the FrameBuffer: The Framebuffer + Attachment Dosen't care what the image is; as long as the resolution + Formats Match
 * i.e. Closer to the Vulkan spec Definition: the Framebuffer is the render Target, not the Actual Image
 * and if renderPass + framebuffer shoudl be Separted; as renderPass+ farmebuffer are not dpendnst on one aniother/Are IIRC Mutually Exclusive AFAIk; so it shoudl be posibelt change.Switcj betwen renderPasses on teh Fly at will
 * (i..e bindRenderPass() Maybe...
 * Not sure if Modulairty shou;d be pushed to teh max Even more in this wayyl allowing the Dimentionelss properties of rendewerPasses to be exploited
 * (if if this is too crazy even for Vulkan tbh...)
 * */

//        Renderer.getInstance().tstRenderPass2.bindImageReference(PRESENT,  swapChain.getColorAttachment());

        Renderer.getInstance().tstFRAMEBUFFER_2.beginRendering(commandBuffer, stack);
//        Renderer.getInstance().setBoundFramebuffer(swapChain);

//        VkViewport.Buffer pViewport = swapChain.viewport(stack);
//        vkCmdSetViewport(commandBuffer, 0, pViewport);
//
//        VkRect2D.Buffer pScissor = swapChain.scissor(stack);
//        vkCmdSetScissor(commandBuffer, 0, pScissor);
    }

    @Override
    public void end(VkCommandBuffer commandBuffer) {
        Renderer.getInstance().endRenderPass(commandBuffer);

        int result = vkEndCommandBuffer(commandBuffer);
        if(result != VK_SUCCESS) {
            throw new RuntimeException("Failed to record command buffer:" + result);
        }
    }
}
