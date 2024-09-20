package net.vulkanmod.vulkan.framebuffer;

public record AttachFeatures(AttachmentTypes... attachmentTypes) {
    public static AttachFeatures add(AttachmentTypes... attachmentTypes) {
        return new AttachFeatures(attachmentTypes);
    }
}

