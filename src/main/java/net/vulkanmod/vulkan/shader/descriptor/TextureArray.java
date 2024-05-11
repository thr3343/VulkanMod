package net.vulkanmod.vulkan.shader.descriptor;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.vulkan.texture.VTextureAtlas;
import net.vulkanmod.vulkan.texture.VulkanImage;

import java.nio.ByteBuffer;

public class TextureArray {

    Int2LongOpenHashMap SamplerID2Texture = new Int2LongOpenHashMap(32);

    private static final Long2ObjectOpenHashMap<VTextureAtlas> loadedTextures = new Long2ObjectOpenHashMap<>(128);

    private static final Int2ObjectArrayMap<VTextureRegion> textureArray = new Int2ObjectArrayMap<>(8);

    static
    {
        AddRegion(0, 32);
        AddRegion(2048, 512);
    }

    public static void AddRegion(VTextureAtlas VTextureAtlas, int baseIndex)
    {
//        SamplerID2Texture.put(0, vTextureAtlas.getImage(0,0));

        textureArray.put(baseIndex, new VTextureRegion(baseIndex, VTextureAtlas.spatialSize()));
    }

    public static void createNewAtlasArray(ResourceLocation resourceLocation, int textureId, int width, int height, int baseTextureSize)
    {
        if(!loadedTextures.containsKey(textureId))
        {
            final VTextureAtlas v = new VTextureAtlas(width, height, baseTextureSize, resourceLocation, textureId, 32);
            loadedTextures.put(textureId, v);
            AddRegion(v, 32);
        }
    }

    public static void AddRegion(int baseIndex, int length)
    {
//        SamplerID2Texture.put(0, vTextureAtlas.getImage(0,0));

        textureArray.put(baseIndex, new VTextureRegion(baseIndex, length));
    }


    public static void registerTexture(int binding, int TextureID, VulkanImage vulkanImage)
    {
        //TODO: Get region Offset
    }
    public static void addSubRegion(int x, int y, int resourceLocation, ByteBuffer nativeImage)
    {
        //TODO: Get region Offset

        loadedTextures.get(resourceLocation /*resourceLocation.hashCode()*/).addOrUpdateImage(x, y, nativeImage);
    }


}
