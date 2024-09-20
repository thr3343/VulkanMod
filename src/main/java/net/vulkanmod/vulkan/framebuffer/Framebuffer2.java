package net.vulkanmod.vulkan.framebuffer;


import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.EnumMap;

import static net.vulkanmod.vulkan.Vulkan.*;
import static net.vulkanmod.vulkan.Vulkan.getSwapChain;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class Framebuffer2 {

    private static final ObjectArrayList<FramebufferInfo> frameBuffers = new ObjectArrayList<>(8);
    private final EnumMap<AttachmentTypes, VulkanImage> images = new EnumMap<>(AttachmentTypes.class);
    private long frameBuffer=VK_NULL_HANDLE;

    public int width, height;
    public RenderPass2 renderPass2;
    private boolean swapChainMode;
    private boolean reInitialised=false;
    private AttachmentTypes presentState=null;


    public Framebuffer2(int width, int height) {
        this.width=width;
        this.height=height;

//        this.swapChainMode = swapChainMode;
    }


    //Framebuffers are dependent on RenderPasses/Attachment Configurations though; so unlike RenderPasses they can't be fully independent / Fully Modular e.g.
    public void bindRenderPass(RenderPass2 renderPass2)
    {
//        if(this.renderPass2!=null)this.renderPass2.cleanUp();
        this.clearFrameBuffers();
        this.clearRenderPass();
        this.clearImages();

        this.renderPass2=renderPass2;
        this.frameBuffer = createFramebuffers(renderPass2.attachmentTypes);

        presentState = renderPass2.presentKey;

        swapChainMode=renderPass2.presentKey!=null;
        initImages(renderPass2);


    }

    private void initImages(RenderPass2 renderPass2) {
        for (var a : renderPass2.attachment.values()) {
            if (a.type.present) continue;
            VulkanImage textureImage = VulkanImage.createAttachmentImage(a, width, height);
            this.images.put(a.type, textureImage);
            renderPass2.bindImageReference(a.type, textureImage);
        }
        reInitialised=true;
    }

    //Framebuffers can use any renderPass, as long as the Attachment Ref Configs Match
    private  long createFramebuffers(AttachmentTypes[] attachmentTypes) {
        try (MemoryStack stack = stackPush()) {

            LongBuffer pFramebuffer = stack.mallocLong(1);

            VkFramebufferAttachmentImageInfo.Buffer AttachmentInfos = VkFramebufferAttachmentImageInfo.calloc(attachmentTypes.length, stack);

            for (int i = 0; i < attachmentTypes.length; i++) {

                AttachmentInfos.get(i).sType$Default()
                        .width(width).height(height)
                        .pViewFormats(stack.ints(attachmentTypes[i].format))
                        .layerCount(1)
                        .usage(attachmentTypes[i].usage);
            }


            VkFramebufferAttachmentsCreateInfo framebufferAttachmentsInfo = VkFramebufferAttachmentsCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachmentImageInfos(AttachmentInfos);

            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default().pNext(framebufferAttachmentsInfo)
                    .flags(VK12.VK_FRAMEBUFFER_CREATE_IMAGELESS_BIT)
                    .renderPass(this.renderPass2.renderPass)
                    .width(width).height(height).layers(1)
                    .attachmentCount(this.renderPass2.attachmentTypes.length)
                    .pAttachments(null);


            if (vkCreateFramebuffer(getVkDevice(), framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create framebuffer");
            }
            FramebufferInfo framebufferInfo1 = new FramebufferInfo(width, height, pFramebuffer.get(0), this.renderPass2.attachmentTypes);
            if(!frameBuffers.contains(framebufferInfo1)) frameBuffers.add(framebufferInfo1);
            return (pFramebuffer.get(0));

        }
    }

    //TODO: Start Multiple Framebuffers at the same time...
    //This could be member function of RenderPass2,
    // But as you cannot render to multiple Framebuffers at a time excluding exts e.g.
    // + You lose modularity of hotSwapping RenderPasses
    public void beginRendering(VkCommandBuffer commandBuffer, MemoryStack stack) {

        if(!initialised()) return;
        if(reInitialised)
        {
            checkTransitions(commandBuffer, stack);
            if(this.swapChainMode)

                getSwapChain().getColorAttachment().transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        }


        VkRect2D renderArea = VkRect2D.malloc(stack);
        renderArea.offset().set(0, 0);
        renderArea.extent().set(this.width, this.height);

        final int length = renderPass2.attachmentTypes.length;

        var clearValues = VkClearValue.malloc(length, stack);
        final LongBuffer longs = stack.mallocLong(length);

        if(this.swapChainMode) this.renderPass2.bindImageReference(presentState,  getSwapChain().getColorAttachment());
//Clear Color value is ignored if Load Op is Not set to Clear
        for(var a : renderPass2.attachment.values()) {
            if(a.type.color) clearValues.get(a.BindingID).color().float32(VRenderSystem.clearColor);
            else clearValues.get(a.BindingID).depthStencil().set(1.0f, 0);

            longs.put(a.BindingID, a.imageView);
        }
        VkRenderPassAttachmentBeginInfo attachmentBeginInfo = VkRenderPassAttachmentBeginInfo.calloc(stack)
                .sType$Default()
                .pAttachments(longs);




        VkRenderPassBeginInfo renderingInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType$Default().pNext(attachmentBeginInfo)
                .renderPass(this.renderPass2.renderPass)
                .renderArea(renderArea)
                .framebuffer(this.frameBuffer)
                .pClearValues(clearValues).clearValueCount(length);

        vkCmdBeginRenderPass(commandBuffer, renderingInfo, VK_SUBPASS_CONTENTS_INLINE);
    }

    private void checkTransitions(VkCommandBuffer commandBuffer, MemoryStack stack) {
        for(var img : this.images.keySet())
        {
            VulkanImage vulkanImage = this.images.get(img);
            vulkanImage.transitionImageLayout(stack, commandBuffer, img.initialLayout);
        }
        reInitialised=false;
    }

    // Allows for the ability to avoid null FrameBuffers
    // (Might be useful for handling buggy Mods)
    private boolean initialised() {
        return this.width != 0 && this.height != 0 && this.renderPass2 != null;
    }

    public void cleanUp() {
        clearFrameBuffers();
        clearRenderPass();
        clearImages();
    }

    private void clearFrameBuffers() {
        frameBuffers.forEach(a -> vkDestroyFramebuffer(getVkDevice(), a.frameBuffer, null));
        frameBuffers.clear();
        this.frameBuffer=VK_NULL_HANDLE;
    }

    private void clearRenderPass() {
        if(this.renderPass2 != null) this.renderPass2.cleanUp();
    }

    private void clearImages() {
        this.images.values().forEach(VulkanImage::free);
        this.images.clear();
    }


    //framebuffers can use any renderPass, as long as the renderpass matches the AttachmentImageInfos configuration used to create the framebuffer handle: (i.e.attachment count + format (as long as the res Matches))
    private record FramebufferInfo(int width, int height, long frameBuffer, AttachmentTypes[] attachments){};

    public void setSize(int width, int height) {
        if(this.width==width && this.height==height) return;
        this.width = width;
        this.height = height;
        this.clearImages();
        this.clearFrameBuffers();
        this.frameBuffer = createFramebuffers(this.renderPass2.attachmentTypes);

        initImages(this.renderPass2);
    }

    private long checkForFrameBuffers() {
        for (final FramebufferInfo a : frameBuffers) {
            if (a.width== width && a.height ==this.height) return a.frameBuffer;
        }
        return createFramebuffers(this.renderPass2.attachmentTypes); //Not sure best way to handle this rn...
    }

    //From FrameBuffer.class===
    public VkRect2D.Buffer scissor(MemoryStack stack) {
        VkRect2D.Buffer scissor = VkRect2D.malloc(1, stack);
        scissor.offset().set(0, 0);
        scissor.extent().set(this.width, this.height);

        return scissor;
    }


    public VkViewport.Buffer viewport(MemoryStack stack) {
        VkViewport.Buffer viewport = VkViewport.malloc(1, stack);
        viewport.x(0.0f);
        viewport.y(this.height);
        viewport.width(this.width);
        viewport.height(-this.height);
        viewport.minDepth(0.0f);
        viewport.maxDepth(1.0f);

        return viewport;
    }
}

