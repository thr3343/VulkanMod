package net.vulkanmod.vulkan.texture;

import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.shader.descriptor.DescriptorManager;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.system.MemoryStack.stackPush;

public class VSubTextureAtlas {

    private final VulkanImage basetextureAtlas;
    private final int baseTileSize;
    private final int tileWidth;
    private final int tileHeight;
    public final VulkanImage[] TextureArray; //may make dierct handle to reduce GC overhead
    //Allows Stitched and Unstitched Variants/Atlases to exist simultaneously at the same time

    //TODO: Can't handle mismatched Block resolutions
    // + 

    public VSubTextureAtlas(VulkanImage basetextureAtlas, int baseTileSize) {
        this.basetextureAtlas = basetextureAtlas;
        this.baseTileSize = baseTileSize;
        this.tileWidth = basetextureAtlas.width/baseTileSize;
        this.tileHeight = basetextureAtlas.height/baseTileSize;
        this.TextureArray = new VulkanImage[tileHeight*tileWidth];

        VulkanImage.Builder a =  VulkanImage.builder(baseTileSize, baseTileSize).setAnisotropy(true).setMipLevels(basetextureAtlas.mipLevels);

        for(int y = 0 ; y < this.TextureArray.length; y++)
        {
            TextureArray[y]= VulkanImage.createTextureImage(a);
        }

    }

    //local GPU2GPU copies
    public void unStitch()
    {
        try (MemoryStack stack = stackPush()) {
            final CommandPool.CommandBuffer handle = DeviceManager.getGraphicsQueue().getCommandBuffer();
            basetextureAtlas.transferSrcLayout(stack, handle.getHandle());
            Synchronization.waitFence(DeviceManager.getGraphicsQueue().submitCommands(handle));

        }
        //TODO: update mipmaps when needed/changed
        for (int i = 0; i < basetextureAtlas.mipLevels; i++) {
            for(int y = 0 ; y < this.tileHeight; y++)
            {
                for (int x = 0; x < this.tileWidth; x++) {
                    basetextureAtlas.copySubTileTexture(baseTileSize, x, y, TextureArray[getTileIndex(y, x)], i);
                }
            }
        }
        Synchronization.INSTANCE.waitFences();
        DescriptorManager.registerTextureArray(1, this);
        DescriptorManager.updateAllSets();
        DescriptorManager.resizeAllSamplerArrays();
    }

    private int getTileIndex(int y, int x) {
        return (y * tileWidth) + x;
    }

    public void cleanup()
    {
        for(int y = 0 ; y < this.TextureArray.length; y++)
        {
            TextureArray[y].free();
        }
    }

    private enum modes {
        ATLAS,
        ARRAY_TEX,
        TEX_ARRAY;
    }
}