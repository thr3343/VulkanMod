package net.vulkanmod.vulkan.shader.descriptor;

import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.texture.VSubTextureAtlas;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryUtil;

public class SubTextureAtlasManager {

    private static final Object2ObjectArrayMap<ResourceLocation, VSubTextureAtlas> subTextureAtlasObjectArrayMap = new Object2ObjectArrayMap<>(2);

    //using ResourceLocation instead of names for parity with vanilla
    public static VSubTextureAtlas registerSubTexAtlas(ResourceLocation s) {
        if(subTextureAtlasObjectArrayMap.containsKey(s)) return subTextureAtlasObjectArrayMap.get(s);
        final int id = Minecraft.getInstance().getTextureManager().getTexture(s).getId();
        final VulkanImage vulkanImage = GlTexture.getTexture(id).getVulkanImage();

        final VSubTextureAtlas v = new VSubTextureAtlas(s, vulkanImage, 16, id);
        subTextureAtlasObjectArrayMap.put(s, v);
        return v;
    }

    public static void unRegisterSubTexAtlas(ResourceLocation s) {

        subTextureAtlasObjectArrayMap.remove(s).unload();
    }


    public static VSubTextureAtlas getSubTexAtlas(ResourceLocation s) {
        return subTextureAtlasObjectArrayMap.get(s);
    }
    public static VSubTextureAtlas getOrCreateSubTexAtlas(ResourceLocation s) {
        if(!subTextureAtlasObjectArrayMap.containsKey(s))
        {
            final int id = Minecraft.getInstance().getTextureManager().getTexture(s).getId();
            subTextureAtlasObjectArrayMap.put(s, new VSubTextureAtlas(s, GlTexture.getTexture(id).getVulkanImage(),16, id));
        }
        return subTextureAtlasObjectArrayMap.get(s);
    }

    public static void cleanupAll() {
        subTextureAtlasObjectArrayMap.forEach((location, vSubTextureAtlas) -> vSubTextureAtlas.unload());
    }

    //nessacery due to Mojnag;s Async Texture loading system: availabiloty fo tetxure is not determinate: i..e
    public static boolean checkTextureState(ResourceLocation blockAtlas) {
        return GlTexture.getTexture(Minecraft.getInstance().getTextureManager().getTexture(blockAtlas).getId()).getVulkanImage()!=null;
    }

    public static boolean hasSubTextAtlas(ResourceLocation location)
    {
        return subTextureAtlasObjectArrayMap.containsKey(location);
    }

    public static void upload(ResourceLocation name, int i, int j, int k, int l, NativeImage[] nativeImages) {
//        if(name.toString().contains("vibration")) return;
//        if(nativeImages.length>1) return;
        if(/*name.toString().contains("minecraft:block/repeating_command_block_front") && */subTextureAtlasObjectArrayMap.containsKey(InventoryMenu.BLOCK_ATLAS))
//        if(name.toString().contains("minecraft:block/fire_0") && subTextureAtlasObjectArrayMap.containsKey(InventoryMenu.BLOCK_ATLAS))
//        if(name.toString().contains("minecraft:block") && subTextureAtlasObjectArrayMap.containsKey(InventoryMenu.BLOCK_ATLAS))
        {
            for (int m = 0; m < nativeImages.length; m++) {
                //TODO: Check MemoryBuffer is aligned
                subTextureAtlasObjectArrayMap.get(InventoryMenu.BLOCK_ATLAS).uploadSubTileAsync(i, j, m, 0, 0, l, MemoryUtil.memByteBuffer(nativeImages[m].pixels, nativeImages[m].getWidth() * nativeImages[m].getHeight() * 4));
            }
        }


    }
}
