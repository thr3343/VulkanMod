package net.vulkanmod.vulkan.shader.descriptor;


import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.texture.VTextureAtlas;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;


//Manages SubTexture -> Un-Stitched Atlases for Anisotropic Filtering / MSAA Handling
public class SubTexManager {
    private static final Int2ObjectOpenHashMap<ResourceLocation> SuBTexArrays = new Int2ObjectOpenHashMap<>(8);
    private static final Object2ObjectOpenHashMap<ResourceLocation, VTextureAtlas> SuBTexArrays2 = new Object2ObjectOpenHashMap<>(8);


    public static ResourceLocation getSuBTexArrayNameFromId(int SuBTexID) {
        for (IntIterator iterator = SuBTexArrays.keySet().iterator(); iterator.hasNext(); ) {
            int suBtexBaseRange = iterator.next();
            int nexBaseOffset = iterator.nextInt();

            if(suBtexBaseRange <= SuBTexID && nexBaseOffset> SuBTexID)
            {
                return SuBTexArrays.get(suBtexBaseRange);
            }

        }
        return null;
    }

    public static int getBaseOffset(ResourceLocation resourceLocation)
    {
        return SuBTexArrays2.get(resourceLocation).getBaseDescriptorIndex();
    }


//    public static void AddSuBTExregion(VTextureAtlas vTextureAtlas, DescriptorAbstractionArray dstDescriptorArray)
    public static void AddSuBTExregion(ResourceLocation subResource)
    {


        if(SuBTexArrays2.containsKey(subResource)) return;

        VTextureAtlas vTextureAtlas = new VTextureAtlas(1024,
                512,
                16,
                subResource,
                VK_FORMAT_R8G8B8A8_UNORM,
                32);


        final DescriptorAbstractionArray descriptorAbstractionArray = Renderer.getDescriptorSetArray().getInitialisedFragSamplers();
        descriptorAbstractionArray.queryCapacity(vTextureAtlas.maxLength);

        SuBTexArrays2.put(vTextureAtlas.subResource, vTextureAtlas);
        SuBTexArrays.put(descriptorAbstractionArray.getCurrentOffset(), vTextureAtlas.subResource);
        vTextureAtlas.registerTextures(descriptorAbstractionArray);

    }

//    public static VulkanImage getSuBTexAtlasArrayRegion(int subTexID) {
    public static VulkanImage getSubTexture(int subTexID) {
        return SuBTexArrays2.get(getSuBTexArrayNameFromId(subTexID)).getImageFromID(subTexID);
    }

    //Upload a Tile to a reserved SubTEx Range
//    public static void addSubTexRegionTile(ResourceLocation name, int x, int y, int xOffset, int yOffset, NativeImage[] nativeImages) {
    public static boolean uploadTile(ResourceLocation nameSpace, int x, int y, int xOffset, int yOffset, NativeImage[] nativeImages) {
        if(nameSpace.toString().contains("minecraft:block")||nameSpace.toString().contains("minecraft:item")) {

            Initializer.LOGGER.error(nameSpace.toString() + x + "->"+ y);

            final int mipmap = 1;
            final NativeImage nativeImage = nativeImages[0]; //Ignoring Mipmaps for now
            final int capacity = 16 * 16 * nativeImage.format().components();
            final ByteBuffer pixels = MemoryUtil.memByteBuffer(nativeImage.pixels, capacity);
            SuBTexArrays2.get(InventoryMenu.BLOCK_ATLAS).addOrUpdateImage(mipmap, x, y, 0, pixels);
            return true;
        }
        return false;

    }

//    public static int getSuBTexId(ResourceLocation locationParticles) {
//        return Sub;
//    }
}
