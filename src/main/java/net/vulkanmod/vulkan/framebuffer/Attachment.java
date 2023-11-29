package net.vulkanmod.vulkan.framebuffer;

import static net.vulkanmod.vulkan.framebuffer.Framebuffer2.AttachmentTypes.*;
import static org.lwjgl.vulkan.VK10.*;

public class Attachment
{
    int width, height;
    long imageView;
    final int format;
    final int load, store;
    final int BindingID;
    final Framebuffer2.AttachmentTypes attachmentType;
    final int samples;
    final Framebuffer2.AttachmentTypes dependencies=null;

    public Attachment(int format, int bindingID, Framebuffer2.AttachmentTypes attachmentType) {
//        this.width = width;
//        this.height = height;
//        this.imageView = imageView;
        this.format = format;
        BindingID = bindingID;
        this.attachmentType = attachmentType;
        this.load = VK_ATTACHMENT_LOAD_OP_CLEAR;
        this.store = attachmentType == COLOR ? VK_ATTACHMENT_STORE_OP_STORE : VK_ATTACHMENT_STORE_OP_DONT_CARE;
        this.samples = attachmentType == RESOLVE ? 0 : VK_SAMPLE_COUNT_1_BIT;
    }

    public void bindImageReference(int width, int height, long imageView)
    {
        this.width=width;
        this.height=height;
        this.imageView=imageView;
    }
}
