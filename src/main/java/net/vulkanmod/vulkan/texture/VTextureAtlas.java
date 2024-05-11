package net.vulkanmod.vulkan.texture;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.vulkanmod.Initializer;
import org.lwjgl.vulkan.VK10;

import java.nio.ByteBuffer;
import java.util.function.Predicate;

public class VTextureAtlas implements SpriteSource.Output {
    private final int maxLength, rowPitch;
    private final int divisor;
    private final ResourceLocation resourceLocation;
    private final int textureID;
    private final int baseTextureIndex;

    private final VulkanImage[] vulkanImageSubtexArray;
    //TODO: Can ignore/bypass Unstitcher for this due to exploiting VulkanImage.uploadSubTextureAsync: as only one block at a time is co[ied (Excluding special exceptions such as Fluids+Bell e.g.)
    public VTextureAtlas(int width, int height, int baseTextureSize, ResourceLocation resourceLocation, int textureID, int baseTextureIndex) {
        this.divisor = baseTextureSize;
        this.resourceLocation = resourceLocation;
        this.textureID = textureID;
        this.baseTextureIndex = baseTextureIndex;

        this.maxLength = width*height/(baseTextureSize*baseTextureSize);

        this.rowPitch=width/baseTextureSize;

        vulkanImageSubtexArray = new VulkanImage[this.maxLength];

//        Unstitcher unstitcher = new Unstitcher(resourceLocation, Collections.singletonList(new Unstitcher.Region(TextureAtlas.LOCATION_PARTICLES,
//                0,
//                0,
//                width,
//                height), 16, 16));
//
//        unstitcher.run(Minecraft.getInstance().getResourceManager(), this);



        Initializer.LOGGER.info("SubTex: "+resourceLocation+"--->" +maxLength);

        //TODO: Suballocate from same VKMemory to improve texture read Perf/Locality


        for (int i = 0; i < vulkanImageSubtexArray.length; i++) {
            vulkanImageSubtexArray[i] = new VulkanImage.Builder(baseTextureSize, baseTextureSize)
                    .setMipLevels(1)
                    .setFormat(VK10.VK_FORMAT_R8G8B8A8_UNORM)
                    .isSubImage(true)
//                    .setUsage(VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                    .createVulkanImage();

        }



    }
    //TODO: Handle Multi-Tile Copies
    public void addOrUpdateImage(int x, int y, ByteBuffer buffer)
    {
        vulkanImageSubtexArray[getArrayIndex(x, y)].uploadWholeImage(1, divisor, divisor, buffer);
    }

    private int getArrayIndex(int x, int y) {
        return x * rowPitch + y;
    }

    public VulkanImage getImage(int x, int y)
    {
        return vulkanImageSubtexArray[getArrayIndex(x, y)];
    }


    //TODO: Fix Tall grass Artifacts by Excluding Anisotropy based on texture



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

    public int spatialSize() {
        return this.maxLength;
    }

    @Override
    public void add(ResourceLocation resourceLocation, Resource resource) {
        Initializer.LOGGER.error(resourceLocation+"="+resource);
    }

    @Override
    public void add(ResourceLocation resourceLocation, SpriteSource.SpriteSupplier spriteSupplier) {
        Initializer.LOGGER.error(resourceLocation+"="+spriteSupplier.toString());
    }

    @Override
    public void removeAll(Predicate<ResourceLocation> predicate) {

    }


    enum Mode{
        UV,
        INDEXED;
    }
}
