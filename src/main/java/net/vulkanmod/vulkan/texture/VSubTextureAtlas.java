package net.vulkanmod.vulkan.texture;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.shader.descriptor.DescriptorManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;

public class VSubTextureAtlas {
    public static final Int2ObjectOpenHashMap<VulkanImage> registeredSubImages = new Int2ObjectOpenHashMap<>(2048);
    private final VulkanImage basetextureAtlas;
    private final int baseTileSize;
    private final int tileWidth;
    private final int tileHeight;
    public final VulkanImage[] TextureArray; //may make dierct handle to reduce GC overhead
    //Allows Stitched and Unstitched Variants/Atlases to exist simultaneously at the same time

    //TODO: assubed all block tetxures are uniform in terms of resolution: will break horribly if block res in the Atlas varies
    public VSubTextureAtlas(VulkanImage basetextureAtlas, int baseTileSize) {
        this.basetextureAtlas = basetextureAtlas;
        this.baseTileSize = baseTileSize;
        this.tileWidth = basetextureAtlas.width/baseTileSize;
        this.tileHeight = basetextureAtlas.height/baseTileSize;
        this.TextureArray = new VulkanImage[tileHeight*tileWidth];

        VulkanImage.Builder a =  VulkanImage.builder(baseTileSize, baseTileSize).setAnisotropy(true).setMipLevels(1);
        //Check Row Major order
        int subTexID = 65536;
        for(int y = 0 ; y < this.tileHeight; y++)
        {
            for (int x = 0; x < this.tileWidth; x++) {


                final VulkanImage textureImage = VulkanImage.createTextureImage(a);
                final int i = (y * tileWidth) + x;
                TextureArray[i]= textureImage;
//                subTexID++;
            }
        }

    }

    //local GPU2GPU copies
    public void unStitch()
    {
        try (MemoryStack stack = stackPush()) {
            final CommandPool.CommandBuffer handle = DeviceManager.getGraphicsQueue().getCommandBuffer();
            basetextureAtlas.transferSrcLayout(stack, handle.getHandle());
            DeviceManager.getGraphicsQueue().submitCommands(handle);
            Vulkan.waitIdle();
        }
        //TODO: Synchronise SubTextureIDs and Descriptor indices
        // Check Row-Major order + MipMap copies + Reset layout
        for(int y = 0 ; y < this.tileHeight; y++)
        {
            for (int x = 0; x < this.tileWidth; x++) {
                final int targetTileX = this.tileWidth - x - 1;
                final int targetTileY = this.tileHeight - y - 1;
                final int i = (y * tileWidth) + x;
                registeredSubImages.put(i+65536,TextureArray[(y * tileWidth) + x]);
                basetextureAtlas.copySubTileTexture(baseTileSize, x, y, TextureArray[(y * tileWidth) + x], 0);
            }
        }
        Synchronization.INSTANCE.waitFences();
        DescriptorManager.registerTextureArray(1);
        DescriptorManager.updateAllSets();
    }

    public void cleanup()
    {
        for(int y = 0 ; y < this.tileHeight; y++)
        {
            for (int x = 0; x < this.tileWidth; x++) {
                TextureArray[(y * tileWidth) + x].free();
            }
        }
    }

    private enum modes {
        ATLAS,
        ARRAY_TEX,
        TEX_ARRAY;
    }
}