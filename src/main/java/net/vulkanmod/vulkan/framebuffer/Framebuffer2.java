package net.vulkanmod.vulkan.framebuffer;


import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.EnumMap;

import static net.vulkanmod.vulkan.Vulkan.getDevice;
import static net.vulkanmod.vulkan.Vulkan.getSwapChain;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class Framebuffer2 {

    private static final ObjectArrayList<FramebufferInfo> frameBuffers = new ObjectArrayList<>(8);
    private final EnumMap<AttachmentTypes, VulkanImage> images = new EnumMap<>(AttachmentTypes.class);
    private long frameBuffer=VK_NULL_HANDLE;

    public int width, height;
    public RenderPass2 renderPass2;
    private int boundImages = 0;
    private boolean swapChainMode;
    private AttachmentTypes presentState=null;


    public Framebuffer2(int width, int height) {
        this.width=width;
        this.height=height;

//        this.swapChainMode = swapChainMode;
    }


    //Framebuffers are dependent on RenderPasses/Attachment Configurations though; so unlike RenderPasses they can't be fully independent / Fully Modular e.g.
    public void bindRenderPass(RenderPass2 renderPass2)
    {
        this.renderPass2=renderPass2;
        this.frameBuffer = this.frameBuffer==VK_NULL_HANDLE ? createFramebuffers(renderPass2.attachmentTypes) : checkForFrameBuffers();
        this.boundImages=renderPass2.attachmentTypes.length;

        presentState = renderPass2.presentKey;

        swapChainMode=renderPass2.presentKey!=null;
        initImages(renderPass2);


    }

    private void initImages(RenderPass2 renderPass2) {
        for(var a : renderPass2.attachment.values())
        {
            if(/*this.swapChainMode && */a.type.present/* ==presentState*/) continue;
            VulkanImage textureImage = VulkanImage.createTextureImage(a, width, height);
            this.images.put(a.type, textureImage);
            renderPass2.bindImageReference(a.type, textureImage);
        }
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


            if (vkCreateFramebuffer(getDevice(), framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
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

        VkRect2D renderArea = VkRect2D.malloc(stack);
        renderArea.offset().set(0, 0);
        renderArea.extent().set(this.width, this.height);

        var clearValues = VkClearValue.malloc(this.boundImages, stack);
        final LongBuffer longs = stack.mallocLong(this.boundImages);

        if(this.swapChainMode) this.renderPass2.bindImageReference(presentState,  getSwapChain().getColorAttachment());

        for(var a : renderPass2.attachment.values()) {
//            if(a.loadOp!=VK_ATTACHMENT_LOAD_OP_CLEAR) continue;
            if(a.type.color) clearValues.get(a.BindingID).color().float32(VRenderSystem.clearColor);
            else clearValues.get(a.BindingID).depthStencil().set(1.0f, 0);

            longs.put(a.BindingID, a.imageView);
        }
        VkRenderPassAttachmentBeginInfo attachmentBeginInfo = VkRenderPassAttachmentBeginInfo.calloc(stack)
                .sType$Default()
                .pAttachments(longs);
        //Clear Color value is ignored if Load Op is Not set to Clear



        VkRenderPassBeginInfo renderingInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType$Default().pNext(attachmentBeginInfo)
                .renderPass(this.renderPass2.renderPass)
                .renderArea(renderArea)
                .framebuffer(this.frameBuffer)
                .pClearValues(clearValues).clearValueCount(this.boundImages);

        vkCmdBeginRenderPass(commandBuffer, renderingInfo, VK_SUBPASS_CONTENTS_INLINE);
    }

    // Allows for the ability to avoid null FrameBuffers
    // (Might be useful for handling buggy Mods)
    private boolean initialised() {
        return this.width != 0 && this.height != 0 && this.renderPass2 != null;
    }

    public void cleanUp() {
        frameBuffers.forEach(a -> vkDestroyFramebuffer(getDevice(), a.frameBuffer, null));
        this.renderPass2.cleanUp();
    }


    //framebuffers can use any renderPass, as long as the renderpass matches the AttachmentImageInfos configuration used to create the framebuffer handle: (i.e.attachment count + format (as long as the res Matches))
    private record FramebufferInfo(int width, int height, long frameBuffer, AttachmentTypes[] attachments){};

    public void setSize(int width, int height) {
        if(this.width==width && this.height==height) return;
        this.width = width;
        this.height = height;
        this.frameBuffer = this.frameBuffer==VK_NULL_HANDLE ? createFramebuffers(this.renderPass2.attachmentTypes) : checkForFrameBuffers();

        this.images.values().forEach(VulkanImage::free);

        initImages(this.renderPass2);
    }

    private long checkForFrameBuffers() {
        for (final FramebufferInfo a : frameBuffers) {
            if (a.width== width && a.height ==this.height) return a.frameBuffer;
        }
        return createFramebuffers(this.renderPass2.attachmentTypes); //Not sure best way to handle this rn...
    }
}

