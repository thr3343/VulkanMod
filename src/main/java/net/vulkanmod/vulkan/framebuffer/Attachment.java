package net.vulkanmod.vulkan.framebuffer;

import static net.vulkanmod.vulkan.framebuffer.AttachmentTypes.RESOLVE;
import static net.vulkanmod.vulkan.framebuffer.AttachmentTypes.COLOR;
import static org.lwjgl.vulkan.VK10.*;

public class Attachment
{
    int width, height;
    long imageView;
    final int format;
    final int loadOp, storeOp;
    final int BindingID;
    final AttachmentTypes attachmentType;
    final int samples;
    final AttachmentTypes dependencies=null;

    public Attachment(int format, int bindingID, AttachmentTypes attachmentType) {
//        this.width = width;
//        this.height = height;
//        this.imageView = imageView;
        this.format = format;
        BindingID = bindingID;
        this.attachmentType = attachmentType;
        this.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        this.storeOp = (attachmentType == COLOR) ? VK_ATTACHMENT_STORE_OP_STORE : VK_ATTACHMENT_STORE_OP_DONT_CARE;
        this.samples = attachmentType == RESOLVE ? 0 : VK_SAMPLE_COUNT_1_BIT;
    }

    public void bindImageReference(int width, int height, long imageView)
    {
        this.width=width;
        this.height=height;
        this.imageView=imageView;
    }
}
