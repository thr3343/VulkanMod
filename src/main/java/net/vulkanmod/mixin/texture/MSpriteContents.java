package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.vulkanmod.render.texture.SpriteUtil;
import net.vulkanmod.vulkan.shader.descriptor.SubTextureAtlasManager;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
public class MSpriteContents {

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void checkUpload(int i, int j, int k, int l, NativeImage[] nativeImages, CallbackInfo ci) {
        if(!SpriteUtil.shouldUpload())
            ci.cancel();

        SpriteUtil.addTransitionedLayout(VTextureSelector.getBoundTexture(0));
    }




    @Mixin(SpriteContents.InterpolationData.class)
    static
    class InterpolationDataM
    {
        @Dynamic @Final @Shadow SpriteContents field_21757; //Synthetic
        @Shadow @Final private NativeImage[] activeFrame;

        @Inject(method = "uploadInterpolatedFrame(IILnet/minecraft/client/renderer/texture/SpriteContents$Ticker;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/SpriteContents;upload(IIII[Lcom/mojang/blaze3d/platform/NativeImage;)V"), cancellable = true)
        private void ibjectSuBTExCopy(int i, int j, SpriteContents.Ticker ticker, CallbackInfo ci)
        {
            if(!SpriteUtil.shouldUpload())
                ci.cancel();

            SubTextureAtlasManager.upload(field_21757.name(), i, j, 0, 0, this.activeFrame);
        }
    }

}
