package net.vulkanmod.mixin.texture;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.vulkanmod.interfaces.VAbstractTextureI;
import net.vulkanmod.vulkan.shader.descriptor.SubTextureAtlasManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public class MSpriteAtlasTexture {

    @Shadow @Final private ResourceLocation location;

    @Shadow private int mipLevel;

    @Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextureUtil;prepareImage(IIII)V"))
    private void redirect(int id, int maxLevel, int width, int height) {
        VulkanImage image = new VulkanImage.Builder(width, height).setMipLevels(maxLevel + 1).createVulkanImage();
        ((VAbstractTextureI)(this)).setVulkanImage(image);
        ((VAbstractTextureI)(this)).bindTexture();
    }

    @Inject(method = "upload", at = @At("RETURN"))
    private void checkSubTexAtlasState(SpriteLoader.Preparations preparations, CallbackInfo ci)
    {
        if(this.location.equals(InventoryMenu.BLOCK_ATLAS))
        {
            //TODO: Check Tiles have been fully uploaded
            SubTextureAtlasManager.invokeUnstitch(this.location, this.mipLevel);
        }
    }

    /**
     * @author
     */
    @Overwrite
    public void updateFilter(SpriteLoader.Preparations data) {
        //this.setFilter(false, data.maxLevel > 0);
    }
}
