package net.vulkanmod.vulkan.texture;

import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import org.lwjgl.system.MemoryStack;

import java.util.Arrays;

import static org.lwjgl.system.MemoryStack.stackPush;

public class VSubTextureAtlas {

    private final ResourceLocation basetextureAtlas;
    private final int baseTileSize;
    private final int tileWidth;
    private final int tileHeight;
    public final VulkanImage[] TextureArray; //may make dierct handle to reduce GC overhead
    private final int baseID;
    private static final int MAX_IMAGE_LAYERS = DeviceManager.deviceProperties.limits().maxImageArrayLayers(); //Should always be 2048
    private int perSliceMipmaps;
    private boolean isLoaded;
    //Allows Stitched and Unstitched Variants/Atlases to exist simultaneously at the same time

    //TODO: Mixin to Block region lists to allow handling of Non-Uniform/Mismatched Block texture resolutions
    // + 

    public VSubTextureAtlas(ResourceLocation basetextureAtlas, VulkanImage vulkanImage, int baseTileSize, int BaseID) {
        this.basetextureAtlas = basetextureAtlas;
        this.baseID = BaseID;
        this.baseTileSize = baseTileSize;
        this.tileWidth = vulkanImage.width/baseTileSize;
        this.tileHeight = vulkanImage.height/baseTileSize;
        int layerCount = tileHeight * tileWidth;
        this.TextureArray = new VulkanImage[layerCount / MAX_IMAGE_LAYERS];
        load(baseTileSize, vulkanImage.mipLevels);

    }

    private void load(int baseTileSize, int mipLevels) {
        VulkanImage.Builder a =  VulkanImage.builder(baseTileSize, baseTileSize).setLayers(MAX_IMAGE_LAYERS).setAnisotropy(true).setMipLevels(mipLevels);


        Arrays.setAll(TextureArray, t ->VulkanImage.createTextureImage(a));
        this.perSliceMipmaps=mipLevels;
    }

    //local GPU2GPU copies
    public void unStitch(int mipLevels)
    {
        VulkanImage basetextureAtlas = GlTexture.getTexture(this.baseID).getVulkanImage();
        final int requiredMipLevels = mipLevels + 1; //Is always 1 if Mipmaps are disabled
        if(basetextureAtlas.mipLevels != perSliceMipmaps)
        {
            this.unload();
            this.load(16, requiredMipLevels);
        }
        else if(isLoaded) return;
        try (MemoryStack stack = stackPush()) {
            final CommandPool.CommandBuffer handle = DeviceManager.getGraphicsQueue().getCommandBuffer();
            basetextureAtlas.transferSrcLayout(stack, handle.getHandle());
            for(VulkanImage slice : TextureArray)
            {
                slice.transferDstLayout(stack, handle.getHandle());
            }

            Synchronization.waitFence(DeviceManager.getGraphicsQueue().submitCommands(handle));

        }

        for (int i = 0; i < requiredMipLevels; i++) {
            for(int y = 0 ; y < this.tileHeight; y++)
            {
                for (int x = 0; x < this.tileWidth; x++) {
                    final int tileIndex = getTileIndex(y, x);
                    basetextureAtlas.copySubTileTexture(baseTileSize, x, y, TextureArray[getSliceIndex(tileIndex)], i, tileIndex%MAX_IMAGE_LAYERS);
                }
            }
        }
        Synchronization.INSTANCE.waitFences();
        this.isLoaded=true;
    }

    private int getTileIndex(int y, int x) {
        return ((y * tileWidth) + x);
    }

    private int getSliceIndex(int s) {
        return s>>Integer.bitCount(MAX_IMAGE_LAYERS-1);
    }

    public void unload()
    {
        for (VulkanImage image : this.TextureArray) {
            image.doFree();
        }
        this.isLoaded=false;
    }

    public int getBaseTileSize() {
        return baseTileSize;
    }

    public int getTileWidth() {
        return tileWidth;
    }

    public int getTileHeight() {
        return tileHeight;
    }

    public int getBaseID() {
        return baseID;
    }
}