package net.vulkanmod.vulkan.framebuffer;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.EnumMap;

import static net.vulkanmod.vulkan.Vulkan.getVkDevice;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class RenderPass2 {
    public static final int DEFAULT_FORMAT = Vulkan.getSwapChain().isBGRAformat ? VK_FORMAT_B8G8R8A8_UNORM : VK_FORMAT_R8G8B8A8_UNORM;
    static final int depthFormat = DeviceManager.findDepthFormat(true);
    final AttachmentTypes[] attachmentTypes;

    public final EnumMap<AttachmentTypes, Attachment> attachment = new EnumMap<>(AttachmentTypes.class);
    final AttachmentTypes presentKey;
    final Subpass[] subpassReferences;
    public final long renderPass;
    private int subpassIndex;


    //RenderPasses exist completely separately from resolution and VkImages + Allowing them to be fully independent of any form of Framebuffer + allows for Abstraction/Modularity
    public RenderPass2(Subpass[] subpassReferences, AttachmentTypes... attachmentTypes)
    {
        this.subpassReferences = subpassReferences;
        this.attachmentTypes = attachmentTypes;
        for (int i = 0; i < attachmentTypes.length; i++) {
            attachment.put(attachmentTypes[i], new Attachment(attachmentTypes[i].format, i, attachmentTypes[i], /*VRenderSystem.isSampleShadingEnable() ? VRenderSystem.getSampleCount() : */1));
        }
        this.presentKey= Arrays.stream(attachmentTypes).filter(attachmentTypes1 -> attachmentTypes1.present).findFirst().orElse(null);
        this.renderPass=createRenderPass();
    }
    //Hide/Conceal ugly Struct Buffer BoilerPlate
    private static VkAttachmentReference.Buffer getAtachBfr(Attachment attach, MemoryStack stack) {
        return VkAttachmentReference.malloc(1, stack).attachment(attach.BindingID).layout(attach.type.layout);
    }

    private long createRenderPass() {

        try(MemoryStack stack = stackPush()) {

            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(this.attachmentTypes.length, stack);

            final VkSubpassDependency.Buffer subpassDependencies = VkSubpassDependency.calloc(subpassReferences.length, stack);

            VkSubpassDescription.Buffer subpasses = VkSubpassDescription.calloc(subpassReferences.length, stack);


            final boolean hasResolve = Arrays.stream(this.attachmentTypes).anyMatch(attachmentTypes1 -> attachmentTypes1.resolve);
            final boolean hasDepth = Arrays.stream(this.attachmentTypes).anyMatch(attachmentTypes1 -> attachmentTypes1.depth);
            final boolean hasColor = Arrays.stream(this.attachmentTypes).anyMatch(attachmentTypes1 -> attachmentTypes1.color);
            for(Attachment attach : this.attachment.values())
            {

                //TODO; Combine Usage flags based on Subpass Modifers
                attachments.get(attach.BindingID)
                        .format(attach.format)
                        .samples(attach.samples)
                        .loadOp(attach.loadOp)
                        .storeOp(attach.storeOp)
                        .initialLayout(attach.type.initialLayout)
                        .finalLayout(attach.type.finalLayout);

            }


            for(var subPass : this.subpassReferences)
            {
                final int colorAttachments = subPass.getAttachmentCount(Subpass.subStatesModifiers.COLOR);
                final int resolveAttachments = subPass.getAttachmentCount(Subpass.subStatesModifiers.RESOLVE);
                final int inputAttachments = subPass.getAttachmentCount(Subpass.subStatesModifiers.INPUT) + subPass.getAttachmentCount(Subpass.subStatesModifiers.INPUT_DEPTH);
                final int attachmentCount = subPass.getAttachmentCount(Subpass.subStatesModifiers.DISABLED);
                IntBuffer disabled = stack.mallocInt(attachmentCount);


                final VkSubpassDescription subpassDef = subpasses.get();
                subpassDef.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
                subpassDef.colorAttachmentCount(1);

                //Empty: filled with Attachment references based on Type

                VkAttachmentReference.Buffer colorAttach = VkAttachmentReference.malloc(colorAttachments, stack);
                VkAttachmentReference.Buffer resolveAttach = VkAttachmentReference.malloc(resolveAttachments, stack);
                VkAttachmentReference.Buffer inputAttach = VkAttachmentReference.malloc(inputAttachments, stack);

                //TODO; Confirm if VK_ATTACHMENT_UNUSED or preserve attachments are faster

                for (Attachment attach : this.attachment.values()) {
                    //                    Attachment attachment =  this.attachment.get(attach);
                    int attachmentID = attach.BindingID;
                    final Subpass.subStatesModifiers modifier = subPass.getAttachmentType(attachmentID);

                    final boolean isDisabled = modifier.equals(Subpass.subStatesModifiers.DISABLED);
                    final int bindingID = isDisabled ? VK_ATTACHMENT_UNUSED : attach.BindingID;
                    final int layout = modifier.checkLayout(attach.type.layout);
                    VkAttachmentReference attachmentRef = VkAttachmentReference.malloc(stack)
                            .set(bindingID, layout);
                    //Skip attahcment if Disabled


                    switch (modifier) {
                        case COLOR -> colorAttach.put(attachmentRef);
                        case DEPTH -> subpassDef.pDepthStencilAttachment(attachmentRef);
                        case RESOLVE -> resolveAttach.put(attachmentRef);
                        case INPUT, INPUT_DEPTH -> inputAttach.put(attachmentRef);
                    }

//                    if(modifier== Subpass.subStatesModifiers.DISABLED)
//                    {
//                        disabled.put(bindingID);
//                    }


                }

                subpassDef.pColorAttachments(colorAttachments!=0 ? colorAttach.rewind() : null);
                subpassDef.pResolveAttachments(resolveAttachments!=0 ? resolveAttach.rewind() : null);
                subpassDef.pInputAttachments(inputAttachments!=0 ? inputAttach.rewind() : null);

                //Todo: Determine Stage and Access masks automatically
                subpassDependencies.get()
                        .srcSubpass(subPass.getSrcSub()).dstSubpass(subPass.getDstSub())
                        .srcStageMask(subPass.getSrcStage()).dstStageMask(subPass.getDstStage())
                        .srcAccessMask(subPass.getSrcAccess()).dstAccessMask(subPass.getDstAccess())
                        .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);


            }


            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachments)
                    .pSubpasses(subpasses.rewind())
                    .pDependencies(subpassDependencies.rewind());

            LongBuffer pRenderPass = stack.mallocLong(1);

            final var a = vkCreateRenderPass(getVkDevice(), renderPassInfo, null, pRenderPass);
            if(a!=VK_SUCCESS) {
                throw new RuntimeException("Failed to Create FrameBuffer!: "+a);
            }

            return pRenderPass.get(0);
        }
    }

    public VulkanImage getImage(Attachment attachment) {
        return  this.attachment.get(attachment).getVkImage();
    }

    public void bindImageReference(AttachmentTypes attachmentTypes, VulkanImage colorAttachment) {
        this.attachment.get(attachmentTypes).bindImageReference(colorAttachment);
    }

    //Framebuffer can use different renderPasses if compatible: SubPassState is mutually exclusive to the Framebuffer
    public void nextSubPass(VkCommandBuffer vkCommandBuffer, int targetSubPass)
    {
        if(subpassReferences.length==1) return;
        while(targetSubPass>this.subpassIndex)
        {
            //Cycle subPasses until the target subPassID/index is reached
            vkCmdNextSubpass(vkCommandBuffer, VK_SUBPASS_CONTENTS_INLINE);
            this.subpassIndex++;
        }
    }
    public int getFormat(AttachmentTypes attachmentTypes) {
        return this.attachment.get(attachmentTypes).format;
    }

    public void cleanUp() { vkDestroyRenderPass(getVkDevice(), this.renderPass, null); }


    public int getCurrentSubpassIndex() {
        return this.subpassIndex;
    }

    public void resetSubpassState() {
        this.subpassIndex=0;
    }
}
