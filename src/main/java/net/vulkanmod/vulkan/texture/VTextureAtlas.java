package net.vulkanmod.vulkan.texture;

import net.minecraft.client.renderer.texture.atlas.sources.Unstitcher;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;

public class VTextureAtlas {
    private final int maxLength, rowPitch;
    private final int divisor;
    private final ResourceLocation resourceLocation;
    private final int baseTextureIndex;

    private final VulkanImage[] vulkanImageSubtexArray;
    //TODO: Can ignore/bypass Unstitcher for this due to exploiting VulkanImage.uploadSubTextureAsync: as only one block at a time is co[ied (Excluding special exceptions such as Fluids+Bell e.g.)


    public VTextureAtlas(int width, int height, int divisor, ResourceLocation resourceLocation, int format, int usage, int baseTextureIndex) {
        this.divisor = divisor;
        this.resourceLocation = resourceLocation;
        this.baseTextureIndex = baseTextureIndex;

        this.maxLength = width*height/(divisor*divisor);

        this.rowPitch=width/divisor;

        vulkanImageSubtexArray = new VulkanImage[this.maxLength];

//        Unstitcher unstitcher = new Unstitcher(resourceLocation, null, 16, 16);

        Initializer.LOGGER.info("SubTex: "+resourceLocation+"--->" +maxLength);

        //TODO: Might need to suballocate images due to max Allocation limits;
        // Edit; nor atcually the case; perhaes VM I suballcoated images...
        //TODO: suballcoate images from same


        for (int i = 0; i < vulkanImageSubtexArray.length; i++) {
            vulkanImageSubtexArray[i] = new VulkanImage.Builder(divisor, divisor, 1, 1)
                    .setMipLevels(1)
                    .setFormat(format)
                    .addUsage(usage)
                    .createVulkanImage();

        }



    }
    //TODO: Handle Multi-Tile Copies
    public void addOrUpdateImage(int x, int y, long srcBuffer, ByteBuffer buffer)
    {
        vulkanImageSubtexArray[x*rowPitch+y].uploadSubTextureAsync(1, divisor, divisor, 0, 0,0,0, 0, buffer);
    }

    private static LongBuffer getLongBuffer(int divisor, int format, int usage, MemoryStack stack) {
        VkImageCreateInfo imageInfo = VkImageCreateInfo.callocStack(stack);
        imageInfo.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
        imageInfo.imageType(VK_IMAGE_TYPE_2D);
        imageInfo.extent().width(divisor);
        imageInfo.extent().height(divisor);
        imageInfo.extent().depth(1);
        imageInfo.mipLevels(1);
        imageInfo.arrayLayers(1);
        imageInfo.format(format);
        imageInfo.tiling(VK_IMAGE_TILING_OPTIMAL);
        imageInfo.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        imageInfo.usage(usage);
        imageInfo.samples(VK_SAMPLE_COUNT_1_BIT);
        LongBuffer longBuffer = stack.mallocLong(1);


        VK10.vkCreateImage(Vulkan.getVkDevice(), imageInfo, null, longBuffer);
        return longBuffer;
    }


    int UVtoTextureIndex(float u, float v)
    {
        return (int) (u*128*64 + v+128);
    }


    //used for applying Tetxureindex offsets into babseuVs so the tetxureIDs and DescritporIndicies are correct/alige
    int getBaseDescriptorIndex()
    {
        return this.baseTextureIndex;
    }

    public void cleanupAndFree() {
        for (VulkanImage vulkanImage : vulkanImageSubtexArray) {
            vulkanImage.free();

        }
    }


    enum Mode{
        UV,
        INDEXED;
    }
}
