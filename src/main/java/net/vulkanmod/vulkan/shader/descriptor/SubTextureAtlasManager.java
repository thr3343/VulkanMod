package net.vulkanmod.vulkan.shader.descriptor;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.texture.VSubTextureAtlas;
import net.vulkanmod.vulkan.texture.VulkanImage;

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
}
