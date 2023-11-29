package net.vulkanmod.vulkan.framebuffer;

import static org.lwjgl.vulkan.VK10.*;

public enum AttachmentTypes {
        COLOR(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, RenderPass2.DEFAULT_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT),
        DEPTH(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, RenderPass2.depthFormat, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT),
        RESOLVE(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, RenderPass2.DEFAULT_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);

        final int layout, format, usage;

        AttachmentTypes(int layout, int format, int usage) {
            this.layout = layout;
            this.format = format;
            this.usage = usage;
        }
}
