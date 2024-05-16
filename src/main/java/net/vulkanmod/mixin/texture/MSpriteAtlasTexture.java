package net.vulkanmod.mixin.texture;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.interfaces.VAbstractTextureI;
import net.vulkanmod.vulkan.shader.descriptor.SubTexManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TextureAtlas.class)
public class MSpriteAtlasTexture {

    @Shadow @Final private ResourceLocation location;

    @Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextureUtil;prepareImage(IIII)V"))
    private void redirect(int id, int maxLevel, int width, int height) {
        boolean ArrayTexture = this.location.getPath().equals("textures/atlas/blocks.png");

        if(ArrayTexture)
        {
            SubTexManager.AddSuBTExregion(this.location);
        }

       else
        {
            VulkanImage image = new VulkanImage.Builder(width, height, 1, 1).setMipLevels(maxLevel + 1).createVulkanImage();
            ((VAbstractTextureI)(this)).setVulkanImage(image);
            ((VAbstractTextureI)(this)).bindTexture();
        }
    }

    //TODO: redirect to VTextureAtlas Unstitcher
    @Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;uploadFirstFrame()V"))
    private void redirect2(TextureAtlasSprite instance) {
        instance.uploadFirstFrame();
    }

    /**
     * @author
     */
    @Overwrite
    public void updateFilter(SpriteLoader.Preparations data) {
        //this.setFilter(false, data.maxLevel > 0);
    }
}
