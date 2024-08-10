package net.vulkanmod.mixin.texture;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.world.inventory.InventoryMenu;
import net.vulkanmod.vulkan.shader.descriptor.SubTextureAtlasManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AtlasSet.StitchResult.class)
public class AtlasSetM {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void getPreparations(TextureAtlas textureAtlas, SpriteLoader.Preparations preparations, CallbackInfo ci)
    {
        //TODO: Determine Base tile size of each preparations list
        // + are preparations split per Block size, are resolutions uniform: if so SubTexAtlases can be created per preparations list

        //Hardcoded temporarily: as only BLOCK_ATLAS is used
        if(textureAtlas.location().equals(InventoryMenu.BLOCK_ATLAS))
        {
            SubTextureAtlasManager.addPreparations(textureAtlas.location(), preparations);
        }
    }
}
