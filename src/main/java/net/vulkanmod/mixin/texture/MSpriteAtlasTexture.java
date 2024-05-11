package net.vulkanmod.mixin.texture;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.interfaces.VAbstractTextureI;
import net.vulkanmod.vulkan.shader.descriptor.TextureArray;
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
//        VulkanImage image;
//        if(ArrayTexture)
//        {
//            VTextureAtlas vTextureAtlas = new VTextureAtlas(width, height, 16, this.location, id, 32);
//            image = vTextureAtlas.getImage(0, 0);
//            ((VAbstractTextureI)(this)).setVulkanImage(image);
//            ((VAbstractTextureI)(this)).bindTexture();
//        }
//        else {
//            image = new VulkanImage.Builder(width, height).setMipLevels(maxLevel + 1).createVulkanImage();
//        }
//        ((VAbstractTextureI)(this)).setVulkanImage(image);
//        ((VAbstractTextureI)(this)).bindTexture();

        if(ArrayTexture)   TextureArray.createNewAtlasArray(this.location, id, width, height, 16);


       else
        {
            VulkanImage image = new VulkanImage.Builder(width, height).setMipLevels(maxLevel + 1).createVulkanImage();
            ((VAbstractTextureI)(this)).setVulkanImage(image);
            ((VAbstractTextureI)(this)).bindTexture();
        }
    }

/*    //TODO: redirect to VTextureAtlas Unstitcher
    @Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;uploadFirstFrame()V"))
    private void redirect2(TextureAtlasSprite instance) {
       *//* if(this.location.getPath().equals("textures/atlas/blocks.png"))
        {
            TextureArray.addSubRegion(instance.getX(), instance.getY(), instance.atlasLocation(), instance.contents().originalImage);
        }
        else *//*instance.uploadFirstFrame();
    }*/

    /**
     * @author
     */
    @Overwrite
    public void updateFilter(SpriteLoader.Preparations data) {
        //this.setFilter(false, data.maxLevel > 0);
    }
}
