package net.vulkanmod.vulkan.shader.descriptor;

import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;

import static org.lwjgl.vulkan.VK10.*;

public class ImageDescriptor implements Descriptor {

    private final int descriptorType;
    private final int binding;
    public final String qualifier;
    public final String name;
    public final int imageIdx;

    public final boolean isInput;
    public boolean useSampler;
    public boolean isReadOnlyLayout;
    private int layout;
    private int mipLevel = -1;

    public ImageDescriptor(int binding, String type, String name, int imageIdx) {
        this(binding, type, name, imageIdx, false);
    }

    public ImageDescriptor(int binding, String type, String name, int imageIdx, boolean isInputAttachment) {
        this.binding = binding;
        this.qualifier = type;
        this.name = name;
        this.isInput = isInputAttachment;
        this.useSampler = !isInputAttachment;
        this.imageIdx = imageIdx;

        descriptorType = isInputAttachment ? VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT : VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        setLayout(isInputAttachment ?  VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }

    @Override
    public int getBinding() {
        return binding;
    }

    @Override
    public int getType() {
        return descriptorType;
    }

    @Override
    public int getStages() {
        return useSampler ? VK_SHADER_STAGE_ALL_GRAPHICS : VK_SHADER_STAGE_FRAGMENT_BIT;
    }

    public void setLayout(int layout) {
        this.layout = layout;
        this.isReadOnlyLayout = layout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    }

    public int getLayout() {
        return layout;
    }

    public void setMipLevel(int mipLevel) {
        this.mipLevel = mipLevel;
    }

    public int getMipLevel() {
        return mipLevel;
    }

    public VulkanImage getImage() {
        return VTextureSelector.getImage(this.imageIdx);
    }

    public long getImageView(VulkanImage image) {
        long view;

        if(mipLevel == -1)
            view = image.getImageView();
        else
            view = image.getLevelImageView(mipLevel);

        return view;
    }

    public static class State {
        long imageView, sampler;

        public State(long imageView, long sampler) {
            set(imageView, sampler);
        }

        public void set(long imageView, long sampler) {
            this.imageView = imageView;
            this.sampler = sampler;
        }

        public boolean isCurrentState(long imageView, long sampler) {
            return this.imageView == imageView && this.sampler == sampler;
        }

    }
}
