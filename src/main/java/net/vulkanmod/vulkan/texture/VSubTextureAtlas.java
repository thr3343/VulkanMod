package net.vulkanmod.vulkan.texture;

import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.system.MemoryStack.stackPush;

public class VSubTextureAtlas {

    private final ResourceLocation basetextureAtlas;
    private final int baseTileSize;
    private final int tileWidth;
    private final int tileHeight;
    public final VulkanImage[] TextureArray; //may make dierct handle to reduce GC overhead
    private final int baseID;
    private final int MipMaps;
    private final int layerCount;
    private static final int MAX_IMAGE_LAYERS = 1024;
    private boolean isLoaded;
    //Allows Stitched and Unstitched Variants/Atlases to exist simultaneously at the same time

    //TODO: Can't handle mismatched Block resolutions
    // + 

    public VSubTextureAtlas(ResourceLocation basetextureAtlas, VulkanImage vulkanImage, int baseTileSize, int BaseID) {
        this.basetextureAtlas = basetextureAtlas;
        this.baseID = BaseID;
        this.baseTileSize = baseTileSize;
        this.tileWidth = vulkanImage.width/baseTileSize;
        this.tileHeight = vulkanImage.height/baseTileSize;
        this.layerCount = tileHeight * tileWidth;
        this.TextureArray = new VulkanImage[layerCount / MAX_IMAGE_LAYERS];
        this.MipMaps=vulkanImage.mipLevels;
        load(baseTileSize, vulkanImage.mipLevels);

    }

    private void load(int baseTileSize, int mipLevels) {
        VulkanImage.Builder a =  VulkanImage.builder(baseTileSize, baseTileSize).setLayers(1024).setAnisotropy(true).setMipLevels(mipLevels);


        for(int y = 0 ; y < this.TextureArray.length; y++)
        {
            TextureArray[y]= VulkanImage.createTextureImage(a);
        }
    }

    //local GPU2GPU copies
    public void unStitch(int mipLevels)
    {
        VulkanImage basetextureAtlas = GlTexture.getTexture(this.baseID).getVulkanImage();
//        if(isLoaded) return;
        try (MemoryStack stack = stackPush()) {
            final CommandPool.CommandBuffer handle = DeviceManager.getGraphicsQueue().getCommandBuffer();
            basetextureAtlas.transferSrcLayout(stack, handle.getHandle());
            for(VulkanImage slice : TextureArray)
            {
                slice.transferDstLayout(stack, handle.getHandle());
            }

            Synchronization.waitFence(DeviceManager.getGraphicsQueue().submitCommands(handle));

        }

        for (int i = 0; i < mipLevels+1; i++) {
            for(int y = 0 ; y < this.tileHeight; y++)
            {
                for (int x = 0; x < this.tileWidth; x++) {
                    final int tileIndex = getTileIndex(y, x);
                    basetextureAtlas.copySubTileTexture(baseTileSize, x, y, TextureArray[getSliceIndex(tileIndex)], i, tileIndex&1023);
                }
            }
        }
        Synchronization.INSTANCE.waitFences();
        isLoaded = true;
    }

    private int getTileIndex(int y, int x) {
        return ((y * tileWidth) + x);
    }

    private int getSliceIndex(int s) {
        return s>>10;
    }

    public void unload()
    {
        for(int y = 0 ; y < this.TextureArray.length; y++)
        {
            TextureArray[y].doFree();
        }
        isLoaded = false;
    }

    private enum modes {
        ATLAS,
        ARRAY_TEX,
        TEX_ARRAY;
    }
}