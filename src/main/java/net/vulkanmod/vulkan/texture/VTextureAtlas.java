package net.vulkanmod.vulkan.texture;

import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.shader.descriptor.DescriptorAbstractionArray;
import net.vulkanmod.vulkan.shader.descriptor.TexureRegionManager;

import java.nio.ByteBuffer;

public class VTextureAtlas {
    public final int maxLength;
    private final int rowPitch;
    private final int divisor;
    public final ResourceLocation subResource;
    private int baseTextureIndex;

    private final VulkanImage[] vulkanImageSubtexArray;
    public static final int SUBALLOCIMG_BITMASK = 0xFFFF;

    final TexureRegionManager texureRegionManager;
    private static short currentSubTexOffset;
    //TODO: Can ignore/bypass Unstitcher for this due to exploiting VulkanImage.uploadSubTextureAsync: as only one block at a time is co[ied (Excluding special exceptions such as Fluids+Bell e.g.)


    public VTextureAtlas(int width, int height, int divisor, ResourceLocation subResource, int format, int baseTextureIndex) {
        this.divisor = divisor;
        this.subResource = subResource;

//        this.baseTextureIndex = baseTextureIndex; //Not harcoding this Rn for simplity purposes ATM

        this.maxLength = width*height/(divisor*divisor);

        this.rowPitch=width/divisor;

        vulkanImageSubtexArray = new VulkanImage[this.maxLength];



//        Unstitcher unstitcher = new Unstitcher(resourceLocation, null, 16, 16);

        Initializer.LOGGER.info("SubTex: "+ subResource +"--->" +maxLength);

        //TODO: Might need to suballocate images due to max Allocation limits;
        // Edit; nor atcually the case; perhaes VM I suballcoated images...
        //TODO: suballcoate images from same

        //Preallocate textures
        for (int i = 0; i < vulkanImageSubtexArray.length; i++) {
            vulkanImageSubtexArray[i] = new VulkanImage.Builder(divisor, divisor, 1, 1)
                    .createVulkanImage();

        }


        texureRegionManager = new TexureRegionManager(this.maxLength, SUBALLOCIMG_BITMASK, this.subResource);
    }



    public void registerTextures(DescriptorAbstractionArray descriptorSetArray)
    {
        this.baseTextureIndex = descriptorSetArray.getCurrentDescriptorIndex();
        for (VulkanImage vulkanImage : vulkanImageSubtexArray) {
            int subTexID = this.texureRegionManager.regsiterSubTex(vulkanImage, this.subResource);
            descriptorSetArray.registerArrayTexture(subTexID /*(currentSubTexOffset++) << 16 | SUBALLOCIMG_BITMASK*/);

        }
    }

    //TODO: Handle Multi-Tile Copies
    public void addOrUpdateImage(int mipmap, int x, int y, long srcBuffer, ByteBuffer buffer)
    {

        int tiletexX = x / divisor;
        int tiletexY = y / divisor;
        final int i = tiletexX * rowPitch + tiletexY;
        vulkanImageSubtexArray[i].uploadSubTextureAsync(0, divisor, divisor, 0, 0,0,0, divisor, buffer);
    }


    int UVtoTextureIndex(float u, float v)
    {
        return (int) (u*128*64 + v+128);
    }


    //Used to Apply Uv offsets when Descriptor indexing
    public int getBaseDescriptorIndex()
    {
        return this.baseTextureIndex;
    }

    public void cleanupAndFree() {
        for (VulkanImage vulkanImage : vulkanImageSubtexArray) {
            vulkanImage.free();

        }
    }

    public VulkanImage getImageFromID(int subTexID) {
        return this.vulkanImageSubtexArray[subTexID-this.baseTextureIndex];
    }


    enum Mode{
        UV,
        NONUNIFORM_INDEXED, //for > 2048 tetxures

        ARRayTEx; //for < 2048 Layers
    }
}
