package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.render.texture.SpriteUtil;
import net.vulkanmod.vulkan.shader.descriptor.SubTexManager;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
public class MSpriteContents {

    @Shadow @Final private ResourceLocation name;

    @Shadow @Final private int width;

    @Shadow @Final private int height;




    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void checkUpload(int width, int height, int xOffset, int yOffset, NativeImage[] nativeImages, CallbackInfo ci) {
        if(!SpriteUtil.shouldUpload())
            ci.cancel();

        if(SubTexManager.uploadTile(this.name, width, height, xOffset, yOffset, nativeImages))
            ci.cancel();

        SpriteUtil.addTransitionedLayout(VTextureSelector.getBoundTexture(0));
    }
}
