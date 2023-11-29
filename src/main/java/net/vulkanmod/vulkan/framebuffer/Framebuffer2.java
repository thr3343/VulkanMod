package net.vulkanmod.vulkan.framebuffer;


import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.EnumMap;

import static net.vulkanmod.vulkan.Device.findDepthFormat;
import static net.vulkanmod.vulkan.Vulkan.getDevice;
import static net.vulkanmod.vulkan.framebuffer.Framebuffer2.AttachmentTypes.COLOR;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class Framebuffer2 {

    private final int colorID = 0;
    private final int depthID = 0;
    public static final int DEFAULT_FORMAT = Vulkan.getSwapChain().isBGRAformat ? VK_FORMAT_B8G8R8A8_UNORM : VK_FORMAT_R8G8B8A8_UNORM;
    private long frameBuffer;


    private static final int depthFormat = findDepthFormat();
    private static final ObjectArrayList<FramebufferInfo> frameBuffers = new ObjectArrayList<>(8);
    public int width, height;
    public final long renderPass;


    //    private List<VulkanImage> images;
    private VulkanImage colorAttachment;
//    protected VulkanImage depthAttachment;
    private final imageAttachmentReference[] attachments;
    private final AttachmentTypes[] attachmentTypes;

    private final EnumMap<AttachmentTypes, Attachment> attachment = new EnumMap<>(AttachmentTypes.class);

    public void bindImageReference(AttachmentTypes attachmentTypes, VulkanImage colorAttachment) {
        this.attachment.get(attachmentTypes).bindImageReference(colorAttachment.width, colorAttachment.height, colorAttachment.getImageView());
    }

//    @Override
//    public boolean equals(Object obj) {
//       return obj instanceof Framebuffer && this.framebufferInfo == ((Framebuffer) (obj)).framebufferInfo;
//    }
//    public Framebuffer(int width, int height, int format) {
//        this(width, height, format, false);
//    }
//
//    public Framebuffer(int width, int height, int format, boolean blur) {
//        this.format = format;
//        this.depthFormat = Vulkan.findDepthFormat();
//        this.width = width;
//        this.height = height;
//
//        this.colorAttachment = VulkanImage.createTextureImage(format, 1, width, height, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, 0, blur, true);
//
//        createDepthResources(blur);
////        createFramebuffers(width, height);
//    }

    public enum AttachmentTypes
    {
        COLOR(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, DEFAULT_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT),
        DEPTH(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, depthFormat, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT),
        RESOLVE(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, DEFAULT_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);

        private final int layout, format, usage;

        AttachmentTypes(int depthAttachmentOptimal, int depthFormat, int i) {

            this.layout = depthAttachmentOptimal;
            format = depthFormat;
            this.usage = i;
        }
    }
    //make Framebuffer Dependent on another FrameBuffer
//    public Framebuffer2(Framebuffer2 framebuffer2, AttachmentTypes... attachmentTypes) {
//
//    }
    public Framebuffer2(VulkanImage colorAttachment, AttachmentTypes... attachmentTypes) {
        this.width = colorAttachment.width;
        this.height = colorAttachment.height;

        this.colorAttachment = colorAttachment;

        this.attachmentTypes = attachmentTypes;


        attachments = new imageAttachmentReference[attachmentTypes.length];
        this.renderPass=createRenderPass(this.attachmentTypes);

        createDepthResources(false);
        this.frameBuffer=createFramebuffers(this.attachmentTypes);
//        attachment = new Attachment[0];
//        colorID = 0;
    }

    //try to separate thte idea/concept of FrameBuffer/Render target and resolution
    public Framebuffer2(AttachmentTypes... attachmentTypes)
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

    private int getMultiSampleCount() {
        return 0;
    }

    //    public void setSize(int width, int height)
//    {
//        if(this)
//        this.frameBuffer=createFramebuffers(attachmentTypes);
//    }
    private  long createFramebuffers(AttachmentTypes[] attachmentTypes) {
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
                    .renderPass(renderPass)
                    .width(width)
                    .height(height)
                    .layers(1)
                    .attachmentCount(this.attachmentTypes.length)
                    .pAttachments(null);


            if (vkCreateFramebuffer(getDevice(), framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create framebuffer");
            }
            FramebufferInfo framebufferInfo1 = new FramebufferInfo(width, height, pFramebuffer.get(0), attachments);
            if(!frameBuffers.contains(framebufferInfo1))
            {
                frameBuffers.add(framebufferInfo1);
            }
            return (pFramebuffer.get(0));

        }
    }

    private long createRenderPass(AttachmentTypes[] attachmentTypes) {

        try(MemoryStack stack = stackPush()) {

            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.callocStack(this.attachmentTypes.length, stack);
            VkAttachmentReference.Buffer attachmentRefs = VkAttachmentReference.callocStack(this.attachmentTypes.length, stack);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.callocStack(1, stack);
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
            subpass.colorAttachmentCount(1);


            int i = 0;
            for(var attach : this.attachment.values())
            {
                AttachmentTypes attachmentType = attach.attachmentType;
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
            for(var attachRef : attachmentRefs)
            {
                addAttachment(attachments, attachRef, renderPass1, attachmentTypes);
            }

            return renderPass1;
        }
    }

    private static VkAttachmentReference.Buffer getAtachBfr(Attachment attach, MemoryStack stack) {
        return VkAttachmentReference.malloc(1, stack).attachment(attach.BindingID).layout(attach.attachmentType.layout);
    }

    private void addAttachment(VkAttachmentDescription.Buffer attachments, VkAttachmentReference vkAttachmentReference, long renderPass, AttachmentTypes[] attachmentTypes) {
        int i = vkAttachmentReference.attachment();
        final VkAttachmentDescription attachment = attachments.get(i);
        this.attachments[i]=new imageAttachmentReference(renderPass, attachment.loadOp(), attachment.storeOp(), attachmentTypes[i]);
    }

    protected void createDepthResources(boolean blur) {

//        this.depthAttachment = VulkanImage.createDepthImage(depthFormat, this.width, this.height,
//                VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT/* | VK_IMAGE_USAGE_SAMPLED_BIT*/,
//                blur, false);

//        VkCommandBuffer commandBuffer = Vulkan.beginImmediateCmd();
//        //Not Sure if we need this
//        this.depthAttachment.transitionImageLayout(stackPush(), commandBuffer, VK_IMAGE_LAYOUT_UNDEFINED);
//        Vulkan.endImmediateCmd();

    }
    //TODO: Start Multiple Framebuffers at the same time...
    public void beginRendering(VkCommandBuffer commandBuffer, MemoryStack stack) {

        if(!initialised()) return;

        VkRect2D renderArea = VkRect2D.malloc(stack);
        renderArea.offset().set(0, 0);
        renderArea.extent().set(this.width, this.height);
        VkClearValue.Buffer clearValues = VkClearValue.malloc(this.attachment.size(), stack);
        final LongBuffer longs = stack.mallocLong(attachment.size());

        for(var a : attachment.values())
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
                .renderPass(this.renderPass)
                .renderArea(renderArea)
                .framebuffer(this.frameBuffer)
                .pClearValues(clearValues)
                .clearValueCount(this.attachmentTypes.length);

        vkCmdBeginRenderPass(commandBuffer, renderingInfo, VK_SUBPASS_CONTENTS_INLINE);
    }

    private boolean initialised() {
        return this.width!=0 && this.height!=0;
    }

    public void bindAsTexture() {
//        this.colorAttachment.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        VTextureSelector.bindFramebufferTexture(this.colorAttachment);
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

    public void cleanUp() {

        for (final FramebufferInfo a : frameBuffers) {
            vkDestroyFramebuffer(getDevice(), a.frameBuffer, null);
        }
        vkDestroyRenderPass(getDevice(), this.renderPass, null);

        if(colorAttachment!=null) this.colorAttachment.free();
//        this.depthAttachment.free();
    }

//    public long getDepthImageView() { return depthAttachment.getImageView(); }
//
//    public VulkanImage getDepthAttachment() { return depthAttachment; }

    public VulkanImage getColorAttachment() { return colorAttachment; }

    public int getFormat() {
        return this.attachment.get(COLOR).format;
    }

//    public void setFormat(int format) {
//        this.format = format;
//    }

    public int getDepthFormat() {
        return depthFormat;
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

    //    public void setDepthFormat(int depthFormat) {
//        this.depthFormat = depthFormat;
//    }
    //attachments don't care about Res, but does care about the number if Images (i.e. Attachments) Format+LoadStoreOps+layouts afaik
    private record imageAttachmentReference(long parentRenderPass, int loadOp, int storeOp, AttachmentTypes attachmentTypes){};

    //framebuffers can use any renderPass, as long as the renderpass matches the AttachmentImageInfos configuration used to create the framebuffer handle: (i.e.attachment count + format (as long as the res Matches))
    private record FramebufferInfo(int width, int height, long frameBuffer, imageAttachmentReference... attachments){};
    public void recreate(int width, int height) {
        this.width = width;
        this.height = height;
        this.frameBuffer = checkForFrameBuffers();
//        this.depthFormat = findDepthFormat();
//        depthAttachment.free();
        if(colorAttachment!=null) this.colorAttachment.free();
//        createDepthResources(false);
    }

    private long checkForFrameBuffers() {
        for (final FramebufferInfo a : frameBuffers) {
            if (a.width== width && a.height ==this.height) {
                System.out.println("FrameBuffer-->:"+width+"{-->}"+height);
                return a.frameBuffer;
            }
        }
        System.out.println("FAIL!");
        return createFramebuffers(this.attachmentTypes); //Not sure best way to handle this rn...
    }
}

