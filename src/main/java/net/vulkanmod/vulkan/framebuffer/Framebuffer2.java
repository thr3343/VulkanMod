package net.vulkanmod.vulkan.framebuffer;


import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.VRenderSystem;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static net.vulkanmod.vulkan.Vulkan.getDevice;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.VK10.*;

public class Framebuffer2 {

    private final int colorID = 0;
    private final int depthID = 0;
    private long frameBuffer=VK_NULL_HANDLE;



    private static final ObjectArrayList<FramebufferInfo> frameBuffers = new ObjectArrayList<>(8);
    public int width, height;
    public RenderPass2 renderPass2;


    //    private List<VulkanImage> images;


    public Framebuffer2(int width, int height) {
        this.width=width;
        this.height=height;

    }


    //Framebuffers are dependent on RenderPasses/Attachment Configurations though; so unlike RenderPasses they can't be fully independent / Fully Modular e.g.
    public void bindRenderPass(RenderPass2 renderPass2)
    {
        this.renderPass2=renderPass2;
        this.frameBuffer = this.frameBuffer==VK_NULL_HANDLE ? createFramebuffers(renderPass2.attachmentTypes) : checkForFrameBuffers();
    }



    private int getMultiSampleCount() {
        return 0;
    }

    //Framebuffers can use any renderPass, as long as the Attachment Ref Configs Match
    private  long createFramebuffers(RenderPass2.AttachmentTypes[] attachmentTypes) {
        try (MemoryStack stack = stackPush()) {

            //attachments = stack.mallocLong(1);
            LongBuffer pFramebuffer = stack.mallocLong(1);

            VkFramebufferAttachmentImageInfo.Buffer vkFramebufferAttachmentImageInfo = VkFramebufferAttachmentImageInfo.calloc(attachmentTypes.length, stack);
            int i=0;
            for(var attachmentImageInfo : attachmentTypes) {

                VkFramebufferAttachmentImageInfo vkFramebufferAttachmentImageInfos = vkFramebufferAttachmentImageInfo.get(i)
                        .sType$Default()
                        .pNext(NULL)
                        .flags(0)
                        .width(width)
                        .height(height)
                        .pViewFormats(stack.ints(attachmentImageInfo.format))
                        .layerCount(1)
                        .usage(attachmentImageInfo.usage);
                i++;
            }


            VkFramebufferAttachmentsCreateInfo vkFramebufferAttachmentsCreateInfo = VkFramebufferAttachmentsCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachmentImageInfos(vkFramebufferAttachmentImageInfo);
            // Lets allocate the create info struct once and just update the pAttachments field each iteration
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(vkFramebufferAttachmentsCreateInfo)
                    .flags(VK12.VK_FRAMEBUFFER_CREATE_IMAGELESS_BIT)
                    .renderPass(this.renderPass2.renderPass)
                    .width(width)
                    .height(height)
                    .layers(1)
                    .attachmentCount(this.renderPass2.attachmentTypes.length)
                    .pAttachments(null);


            if (vkCreateFramebuffer(getDevice(), framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create framebuffer");
            }
            FramebufferInfo framebufferInfo1 = new FramebufferInfo(width, height, pFramebuffer.get(0), this.renderPass2.attachments);
            if(!frameBuffers.contains(framebufferInfo1))
            {
                frameBuffers.add(framebufferInfo1);
            }
            return (pFramebuffer.get(0));

        }
    }

    //TODO: Start Multiple Framebuffers at the same time...
    //Thinks its Ol to put beginrenderPas here, as you cannot render to multiple Framebuffers at a time excluding exts e.g.
    public void beginRendering(VkCommandBuffer commandBuffer, MemoryStack stack) {

        if(!initialised()) return;

        VkRect2D renderArea = VkRect2D.malloc(stack);
        renderArea.offset().set(0, 0);
        renderArea.extent().set(this.width, this.height);

        VkClearValue.Buffer clearValues = VkClearValue.malloc(this.renderPass2.attachment.size(), stack);
        final LongBuffer longs = stack.mallocLong(renderPass2.attachment.size());

        for(var a : renderPass2.attachment.values())
        {
            switch (a.attachmentType) {
                case COLOR -> clearValues.get(a.BindingID).color().float32(VRenderSystem.clearColor);
                case DEPTH -> clearValues.get(a.BindingID).depthStencil().set(1.0f, 0);
            }
            longs.put(a.BindingID, a.imageView);
        }
        VkRenderPassAttachmentBeginInfo vkRenderPassAttachmentBeginInfo = VkRenderPassAttachmentBeginInfo.calloc(stack)
                .sType$Default()
                .pAttachments(longs);
        //Clear Color value is ignored if Load Op is Not set to Clear



        VkRenderPassBeginInfo renderingInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType$Default()
                .pNext(vkRenderPassAttachmentBeginInfo)
                .renderPass(this.renderPass2.renderPass)
                .renderArea(renderArea)
                .framebuffer(this.frameBuffer)
                .pClearValues(clearValues)
                .clearValueCount(this.renderPass2.attachmentTypes.length);

        vkCmdBeginRenderPass(commandBuffer, renderingInfo, VK_SUBPASS_CONTENTS_INLINE);
    }

    private boolean initialised() {
        return this.width!=0 && this.height!=0 && this.renderPass2!=null;
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

    public VkRect2D.Buffer scissor(MemoryStack stack) {
        VkRect2D.Buffer scissor = VkRect2D.malloc(1, stack);
        scissor.offset().set(0, 0);
        scissor.extent().set(this.width, this.height);

        return scissor;
    }
    public int getDepthFormat() {
        return RenderPass2.depthFormat;
    }

    public void cleanUp() {
        for (final FramebufferInfo a : frameBuffers) {
            vkDestroyFramebuffer(getDevice(), a.frameBuffer, null);
        }

        this.renderPass2.cleanUp();
    }


    //framebuffers can use any renderPass, as long as the renderpass matches the AttachmentImageInfos configuration used to create the framebuffer handle: (i.e.attachment count + format (as long as the res Matches))
    private record FramebufferInfo(int width, int height, long frameBuffer, RenderPass2.imageAttachmentReference... attachments){};

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        this.frameBuffer = this.frameBuffer==VK_NULL_HANDLE ? createFramebuffers(this.renderPass2.attachmentTypes) : checkForFrameBuffers();
    }

    private long checkForFrameBuffers() {
        for (final FramebufferInfo a : frameBuffers) {
            if (a.width== width && a.height ==this.height) {
                System.out.println("FrameBuffer-->:"+width+"{-->}"+height);
                return a.frameBuffer;
            }
        }
        System.out.println("FAIL!");
        return createFramebuffers(this.renderPass2.attachmentTypes); //Not sure best way to handle this rn...
    }
}

