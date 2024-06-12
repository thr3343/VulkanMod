package net.vulkanmod.vulkan.texture;

public class VTextureAtlas {
    private final VulkanImage basetextureAtlas;
    private final int baseTileSize;
    private final int tileWidth;
    private final int tileHeight;
    private final VulkanImage[] TextureArray;
    //Allows Stitched and Unstitched Variants/Atlases to exist simultaneously at the same time
    public VTextureAtlas(VulkanImage basetextureAtlas, int baseTileSize) {
        this.basetextureAtlas = basetextureAtlas;
        this.baseTileSize = baseTileSize;
        this.tileWidth = basetextureAtlas.width/baseTileSize;
        this.tileHeight = basetextureAtlas.height/baseTileSize;
        this.TextureArray = new VulkanImage[tileWidth*tileHeight];

    }

    //local GPU2GPu copies
    public void unStitch()
    {
        for(int x = 0 ; x < this.tileWidth; x++)
        {
            for (int y = 0; y < this.tileHeight; y++) {
//                basetextureAtlas.uploadSubTextureAsync();
            }
        }
    }

    private enum modes {
        ATLAS,
        ARRAY_TEX,
        TEX_ARRAY;
    }
}