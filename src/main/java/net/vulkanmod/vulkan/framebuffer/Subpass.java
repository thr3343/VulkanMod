package net.vulkanmod.vulkan.framebuffer;

import org.lwjgl.vulkan.VK10;

import java.util.Arrays;

//public class SubpassReference {
public class Subpass {

    private final int subPassID;
    private final int srcSub;
    private final int dstSub;
    private final int srcStage;
    private final int dstStage;
    private final int srcAccess;
    private final int dstAccess;
    private final subStatesModifiers[] attachmentTypes;
//    private final int colorAttachments;
//    private final int depthAttachments;
//    private final int inputAttachments;
//    private final int resolveAttachments;

    public Subpass(int subPassID, int src, int dst, int srcStage, int dstStage, int srcAccess, int dstAccess, subStatesModifiers... attachmentTypes) {

        this.subPassID = subPassID;
        this.srcSub = src;
        this.dstSub = dst;
        this.srcStage = srcStage;
        this.dstStage = dstStage;
        this.srcAccess = srcAccess;
        this.dstAccess = dstAccess;
        this.attachmentTypes = attachmentTypes;
//        this.colorAttachments = 1;
//        this.depthAttachments = (int) Arrays.stream(attachmentTypes).filter(x -> x == subStatesModifiers.DEPTH).count();
//
//        this.inputAttachments = (int) Arrays.stream(attachmentTypes).filter(x -> x == subStatesModifiers.INPUT).count();
//        this.resolveAttachments = (int) Arrays.stream(attachmentTypes).filter(x -> x == subStatesModifiers.RESOLVE).count();
    }

    public int getSubPassID() {
        return subPassID;
    }

    public subStatesModifiers getAttachmentType(int attachmentID) {
        return attachmentTypes[attachmentID];
    }

    public int getSrcSub() {
        return srcSub;
    }

    public int getDstSub() {
        return dstSub;
    }
//
//    public int getColorAttachments() {
//        return colorAttachments;
//    }
//
//    public int getDepthAttachments() {
//        return depthAttachments;
//    }
//
//    public int getInputAttachments() {
//        return inputAttachments;
//    }
//
//    public int getResolveAttachments() {
//        return resolveAttachments;
//    }

    public int getSrcStage() {
        return srcStage;
    }

    public int getDstStage() {
        return dstStage;
    }

    public int getSrcAccess() {
        return srcAccess;
    }

    public int getDstAccess() {
        return dstAccess;
    }

    public int getAttachmentCount(subStatesModifiers subStatesModifiers) {
        return (int) Arrays.stream(attachmentTypes).filter(x -> x == subStatesModifiers).count();
    }

    //Modify the state of a given attachment per SubPass
    public enum subStatesModifiers
    {

        INPUT,
        SAMPLED,
        RESOLVE,
        COLOR,
//        PRESENT,
        DEPTH,
        DISABLED,
        DEFAULT,
        NONE;
        //Change required layout based on current subpass/attachment state
        //Does not effect Initial and final layout, only per subpass layouts
        public int checkLayout(int defaultLayout)
        {
            return switch (this)
            {
                case SAMPLED, INPUT -> VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                case COLOR ->  VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
                case DEPTH ->  VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
//                case PRESENT ->  KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
                default -> 0;
            };
        }

    }
}
