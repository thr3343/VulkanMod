package net.vulkanmod.vulkan.framebuffer;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDescription;

import java.nio.LongBuffer;
import java.util.EnumMap;

import static net.vulkanmod.vulkan.Device.findDepthFormat;
import static net.vulkanmod.vulkan.Vulkan.getDevice;
import static net.vulkanmod.vulkan.framebuffer.RenderPass2.AttachmentTypes.COLOR;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public class RenderPass2 {

    public static final int DEFAULT_FORMAT = Vulkan.getSwapChain().isBGRAformat ? VK_FORMAT_B8G8R8A8_UNORM : VK_FORMAT_R8G8B8A8_UNORM;
    static final int depthFormat = findDepthFormat();
    final imageAttachmentReference[] attachments;
    final RenderPass2.AttachmentTypes[] attachmentTypes;


//    private final Framebuffer2.imageAttachmentReference[] attachments;


    final EnumMap<RenderPass2.AttachmentTypes, Attachment> attachment = new EnumMap<>(RenderPass2.AttachmentTypes.class);
    public final long renderPass;

    private static VkAttachmentReference.Buffer getAtachBfr(Attachment attach, MemoryStack stack) {
        return VkAttachmentReference.malloc(1, stack).attachment(attach.BindingID).layout(attach.attachmentType.layout);
    }

    public int getFormat(AttachmentTypes attachmentTypes) {
        return this.attachment.get(attachmentTypes).format;
    }


    public enum AttachmentTypes
    {
        COLOR(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, DEFAULT_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT),
        DEPTH(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, depthFormat, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT),
        RESOLVE(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, DEFAULT_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);

        private final int layout;
        final int format;
        final int usage;

        AttachmentTypes(int depthAttachmentOptimal, int depthFormat, int i) {

            this.layout = depthAttachmentOptimal;
            format = depthFormat;
            this.usage = i;
        }
    }
    
    //renderPasses exist completely separately from resolution and VkImages; allowing them to be fully independnat from any form of Framebuffer + improves.faciliates Abtraction e and/or Modualrity.g.
    public RenderPass2(RenderPass2.AttachmentTypes... attachmentTypes)
    {
//        this.width = extent2D.width();
//        this.height = extent2D.height();

        this.attachmentTypes = attachmentTypes;
        for (int i = 0; i < attachmentTypes.length; i++) {
            attachment.put(attachmentTypes[i], new Attachment(attachmentTypes[i].format, i, attachmentTypes[i]));
        }

        attachments = new imageAttachmentReference[attachmentTypes.length];
        this.renderPass=createRenderPass(attachmentTypes);

//        createDepthResources(false);


    }
    private long createRenderPass(RenderPass2.AttachmentTypes[] attachmentTypes) {

        try(MemoryStack stack = stackPush()) {

            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.callocStack(this.attachmentTypes.length, stack);
            VkAttachmentReference.Buffer attachmentRefs = VkAttachmentReference.callocStack(this.attachmentTypes.length, stack);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.callocStack(1, stack);
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
            subpass.colorAttachmentCount(1);


            int i = 0;
            for(var attach : this.attachment.values())
            {
                RenderPass2.AttachmentTypes attachmentType = attach.attachmentType;
                VkAttachmentDescription colorAttachment = attachments.get(i)
                        .format(attach.format)
                        .samples(attach.samples)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                        .storeOp(attachmentType == COLOR ? VK_ATTACHMENT_STORE_OP_STORE : VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        .finalLayout(attachmentType == COLOR ? VK_IMAGE_LAYOUT_PRESENT_SRC_KHR : attachmentType.layout);

                attachmentRefs.get(attach.BindingID).set(attach.BindingID, attachmentType.layout);

                switch (attachmentType) {
                    case COLOR -> subpass.pColorAttachments(getAtachBfr(attach, stack));
                    case DEPTH -> subpass.pDepthStencilAttachment(attachmentRefs.get(attach.BindingID));
                    case RESOLVE -> subpass.pResolveAttachments(getAtachBfr(attach, stack));
                }

                i++;
            }







            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.callocStack(stack);
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
            renderPassInfo.pAttachments(attachments);
            renderPassInfo.pSubpasses(subpass);
            //renderPassInfo.pDependencies(dependency);

            LongBuffer pRenderPass = stack.mallocLong(1);

            final var a = vkCreateRenderPass(getDevice(), renderPassInfo, null, pRenderPass);
            if(a!=VK_SUCCESS) {
                throw new RuntimeException("Failed to Create FrameBuffer!: "+a);
            }

            final long renderPass1 = pRenderPass.get(0);
//            for(var attachRef : attachmentRefs)
//            {
//                addAttachment(attachments, attachRef, renderPass1, attachmentTypes);
//            }

            return renderPass1;
        }
    }

    //    public void setDepthFormat(int depthFormat) {
//        this.depthFormat = depthFormat;
//    }
    //attachments don't care about Res, but does care about the number if Images (i.e. Attachments) Format+LoadStoreOps+layouts afaik
    record imageAttachmentReference(long parentRenderPass, int loadOp, int storeOp, RenderPass2.AttachmentTypes attachmentTypes){};



    public void bindImageReference(AttachmentTypes attachmentTypes, VulkanImage colorAttachment) {
        this.attachment.get(attachmentTypes).bindImageReference(colorAttachment.width, colorAttachment.height, colorAttachment.getImageView());
    }
    public void cleanUp() {

//        for (final FramebufferInfo a : frameBuffers) {
//            vkDestroyFramebuffer(getDevice(), a.frameBuffer, null);
//        }
        vkDestroyRenderPass(getDevice(), this.renderPass, null);

//        if(colorAttachment!=null) this.colorAttachment.free();
//        this.depthAttachment.free();
    }
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
