package net.vulkanmod.vulkan.framebuffer;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.EnumMap;

import static net.vulkanmod.vulkan.Device.findDepthFormat;
import static net.vulkanmod.vulkan.Vulkan.getDevice;
import static net.vulkanmod.vulkan.framebuffer.AttachmentTypes.COLOR;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public class RenderPass2 {
    public static final int DEFAULT_FORMAT = Vulkan.getSwapChain().isBGRAformat ? VK_FORMAT_B8G8R8A8_UNORM : VK_FORMAT_R8G8B8A8_UNORM;
    static final int depthFormat = findDepthFormat();
    final AttachmentTypes[] attachmentTypes;

    final EnumMap<AttachmentTypes, Attachment> attachment = new EnumMap<>(AttachmentTypes.class);
    public final long renderPass;


    //RenderPasses exist completely separately from resolution and VkImages + Allowing them to be fully independent of any form of Framebuffer + allows for Abstraction/Modularity
    public RenderPass2(AttachmentTypes... attachmentTypes)
    {


        this.attachmentTypes = attachmentTypes;
        for (int i = 0; i < attachmentTypes.length; i++) {
            attachment.put(attachmentTypes[i], new Attachment(attachmentTypes[i].format, i, attachmentTypes[i]));
        }

        this.renderPass=createRenderPass();

    }
    //Hide/Conceal ugly Struct Buffer BoilerPlate
    private static VkAttachmentReference.Buffer getAtachBfr(Attachment attach, MemoryStack stack) {
        return VkAttachmentReference.malloc(1, stack).attachment(attach.BindingID).layout(attach.attachmentType.layout);
    }

    private long createRenderPass() {

        try(MemoryStack stack = stackPush()) {

            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(this.attachmentTypes.length, stack);
            VkAttachmentReference.Buffer attachmentRefs = VkAttachmentReference.calloc(this.attachmentTypes.length, stack);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
            subpass.colorAttachmentCount(1);


            int i = 0;
            for(var attach : this.attachment.values())
            {
                attachments.get(i)
                        .format(attach.format)
                        .samples(attach.samples)
                        .loadOp(attach.loadOp)
                        .storeOp(attach.storeOp)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        .finalLayout(attach.attachmentType == COLOR ? VK_IMAGE_LAYOUT_PRESENT_SRC_KHR : attach.attachmentType.layout);

                attachmentRefs.get(attach.BindingID).set(attach.BindingID, attach.attachmentType.layout);

                switch (attach.attachmentType) {
                    case COLOR -> subpass.pColorAttachments(getAtachBfr(attach, stack));
                    case DEPTH -> subpass.pDepthStencilAttachment(attachmentRefs.get(attach.BindingID));
                    case RESOLVE -> subpass.pResolveAttachments(getAtachBfr(attach, stack));
                }

                i++;
            }
            final VkSubpassDependency.Buffer subpassDependencies = this.attachment.containsKey(COLOR) ? VkSubpassDependency.calloc(1, stack)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                    .srcAccessMask(0)
                    .dstAccessMask(0)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT): null;


            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack);
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
            renderPassInfo.pAttachments(attachments);
            renderPassInfo.pSubpasses(subpass);
            renderPassInfo.pDependencies(subpassDependencies);

            LongBuffer pRenderPass = stack.mallocLong(1);

            final var a = vkCreateRenderPass(getDevice(), renderPassInfo, null, pRenderPass);
            if(a!=VK_SUCCESS) {
                throw new RuntimeException("Failed to Create FrameBuffer!: "+a);
            }

            return pRenderPass.get(0);
        }
    }
    //attachments don't care about Res, but does care about the number if Images (i.e. Attachments) Format+LoadStoreOps+layouts afaik

    record imageAttachmentReference(long parentRenderPass, int loadOp, int storeOp, AttachmentTypes attachmentTypes){};


    public void bindImageReference(AttachmentTypes attachmentTypes, VulkanImage colorAttachment) {
        this.attachment.get(attachmentTypes).bindImageReference(colorAttachment.width, colorAttachment.height, colorAttachment.getImageView());
    }



    private int getMultiSampleCount() {
        return 0;
    }
    public int getFormat(AttachmentTypes attachmentTypes) {
        return this.attachment.get(attachmentTypes).format;
    }

    public void cleanUp() { vkDestroyRenderPass(getDevice(), this.renderPass, null); }
    public void addAttachment(AttachmentTypes attachmentTypes)
    {

    }
    public void removeAttachment(AttachmentTypes attachmentTypes)
    {

    }
    public void enableAttachment(AttachmentTypes attachmentTypes)
    {

    }
    public void disableAttachment(AttachmentTypes attachmentTypes)
    {

    }


}
