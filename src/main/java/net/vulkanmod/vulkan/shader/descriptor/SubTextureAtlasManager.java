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

        final VulkanImage vulkanImage = GlTexture.getTexture(Minecraft.getInstance().getTextureManager().getTexture(s).getId()).getVulkanImage();

        final VSubTextureAtlas v = new VSubTextureAtlas(vulkanImage, 16);
        subTextureAtlasObjectArrayMap.put(s, v);
        return v;
    }

    public static void unRegisterSubTexAtlas(ResourceLocation s) {

        subTextureAtlasObjectArrayMap.remove(s).unload();
    }


    public static VSubTextureAtlas getSubTexAtlas(ResourceLocation s) {
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
