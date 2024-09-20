package net.vulkanmod.vulkan.framebuffer;

import static net.vulkanmod.vulkan.framebuffer.AttachmentTypes.*;
import static org.lwjgl.vulkan.VK10.*;

public class Attachment
{
//    public int width;
//    public int height;
    long imageView;
    final int format;
    final int loadOp, storeOp;
    final int BindingID;
    public final AttachmentTypes type;
    public final int samples;
    final AttachmentTypes dependencies=null;

    public Attachment(int format, int bindingID, AttachmentTypes type, int samples) {
//        this.width = width;
//        this.height = height;
//        this.imageView = imageView;
        this.format = format;
        BindingID = bindingID;
        this.type = type;
        this.loadOp = (!type.resolve) ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        this.storeOp = (samples==1 && type.color) ? VK_ATTACHMENT_STORE_OP_STORE : VK_ATTACHMENT_STORE_OP_DONT_CARE;
        this.samples = type.resolve ? VK_SAMPLE_COUNT_1_BIT : samples;
    }

    public void bindImageReference(long imageView)
    {
//        this.width=width;
//        this.height=height;
        this.imageView=imageView;
    }

//    public int getStage() {
//        return switch (this.type){
//
//            case PRESENT, PRESENT_SAMPLED, COLOR, COLOR_SAMPLED, PRESENT_RESOLVE, RESOLVE_COLOR, PRESERVE -> VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
//            case DEPTH, DEPTH_SAMPLED, RESOLVE_DEPTH -> VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT|VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
//            case INPUT_COLOR -> VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
//            case INPUT_DEPTH -> VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
//        };
//    }
}
