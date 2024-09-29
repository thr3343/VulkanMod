package net.vulkanmod.vulkan.pass;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.*;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.util.DrawUtil;
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

    private RenderPass2 mainRenderPass;


    DefaultMainPass() {
        this.mainFramebuffer = new Framebuffer2(Vulkan.getSwapChain().getWidth(), Vulkan.getSwapChain().getHeight());

        createRenderPasses();
    }

    @Override
    public RenderPass2 getMainRenderPass() {
        return mainRenderPass;
    }

    @Override
    public Framebuffer2 getMainFrameBuffer() {
        return mainFramebuffer;
    }

    private void createRenderPasses() {

//        RenderPass.Builder builder = RenderPass.builder(this.mainFramebuffer);
//        builder.getColorAttachmentInfo().setFinalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);


        //TODO: poosble t LogicOp?Colour belnd w. translucent Apha w.o clearcoloura nd.or priot
        // Maybe Store Op None to allow Indefine valeus to be masked out in final blend pass

        //TODO: Simultaneous/Async Passes; Render multiple Pipelines/Shaders at the Same Time
        // Tempral ferquencies: i.e Per-Tick Rendering render Differnt atathcmenst at diif freqs: t consvere hardware resources (ala Exordium)
        //  Resuce output.result of static elements:
        //  * Maybe w/ Preserve/StoreOpStore/NoClear
        //  * May be possible to further reduce with Player Movment chnages:Per angl/PLaye Mov+POV check, which is prob less freq than per Tick
        //      * i.e. per angle on unuque angle changes
        // --->
        // Out Of order rasterisation: Render Passes independently of each other + Simultaneous;y
        // Overlapping rendering. Execution
        //  reduce./Fix PipelineBuubles/Stores: Losses/Remove RasterisatioN Roder Bottlnecks/Stalls e./g/
        // --->
        // Pass 0 -> Sky+Backhground / Clear Pass:
        //      No Deps: 0 -> 0: Per Region Local: Fully Async/Independent
        //      Direct to SwapChain/Present Attachment
        //      Swapchain Target avoids blending issues: all subapsess can be blended.layered.applied ontop: disregarding order
        //      Overwrites SwapChain:  Replacess Attachment Clear
        //      Render per-Tick
        // -
        // Pass 1 -> Heavy pass: terrain : FrameBuffer0
        //      Ext -> 0 : depnd on prior terrain
        // -
        // pass 2 -> light-Aux No-Z GUI pass : FrameBuffer1
        //      Ext -> 1
        //      nODept clears, disparent render can allow Dpetht o be ignored/pverrwirtten e.g.
        //      NO Depth: can just be overlapped Blended onTop
        // -
        // pass 3 -> Final Blend.Composite/CCombine./Mereg Pass
        //      ext 0 -> 2: Depends on all Composites
        //      Blend Attachments: Framebuffer Blending / LogicOP
        // ---
        // Issues:
        // * Ponential for OIT: Seems too easy/good to be true: poisbel Clear Colout Contamination + and.or BlendOp perf Killer(s)...
        //  back ground.Colour Clear Contamination:
        //      Use UnDefined Values...
        //      Mask out background...
        // * Blend Constant Colour: Replace Attachment Clear...
        //      Mask Out Contaminants
        // * FrameBuffer Blending
        //  Unknown if possible.fesible:
        //      is Clear Colour Contamination
        //      is Perf Overeahd from Attahcment blending...
        //      is Wastes Bandwidth...
        // * Bandiwidth Overehad:
        //  Do we have enough bandwidth for concurrent streaming...
        //      nSight is approx 15% rasterisation max iirc: https://developer.nvidia.com/blog/advanced-api-performance-async-compute-and-overlap/
        //      Potential Unused/Untapped Rasterisatiom Perf/Resoruces/potneital.coap[bilties...
        //      Forward+
        // Vk_EXT_FRAGMENT_SHADER_INTERLOCK


        Subpass SubExecDepPass = new Subpass(0,
                VK_SUBPASS_EXTERNAL,
                0,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                0,
                0,
                Subpass.subStatesModifiers.COLOR,
                Subpass.subStatesModifiers.DISABLED,
                Subpass.subStatesModifiers.DEPTH);

        Subpass SubExecDepPass1 = new Subpass(1,
                VK_SUBPASS_EXTERNAL,
                0,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                0,
                0,
                Subpass.subStatesModifiers.DISABLED,
                Subpass.subStatesModifiers.COLOR,
                Subpass.subStatesModifiers.DEPTH);
        //TODO: Evict to VRAM flush:... Confirm....
        Subpass subpassReference2 = new Subpass(2,
                1,
                1,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                0,
                VK_ACCESS_INPUT_ATTACHMENT_READ_BIT,
                Subpass.subStatesModifiers.COLOR,
                Subpass.subStatesModifiers.INPUT,
                Subpass.subStatesModifiers.DISABLED);
        this.mainRenderPass = new RenderPass2(new Subpass[]{SubExecDepPass, SubExecDepPass1, subpassReference2}, AttachmentTypes.PRESENT, AttachmentTypes.COLOR, AttachmentTypes.DEPTH);


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

        boolean a = mainRenderPass.getCurrentSubpassIndex()==0;
        mainRenderPass.nextSubPass(commandBuffer, 2);

       if(!a) {
           final Attachment attachment = mainRenderPass.attachment.get(AttachmentTypes.COLOR);
//        final Attachment attachment1 = mainRenderPass.attachment.get(AttachmentTypes.DEPTH);
           VTextureSelector.bindTexture(0, attachment.getVkImage());
//        VTextureSelector.bindTexture(1, attachment1.getVkImage());

           DrawUtil.fastBlit2();
       }


        vkCmdEndRenderPass(commandBuffer);
        mainRenderPass.resetSubpassState();

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
