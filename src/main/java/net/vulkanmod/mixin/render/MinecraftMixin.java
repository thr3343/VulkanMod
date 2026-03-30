package net.vulkanmod.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.main.GameConfig;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.texture.SpriteUpdateUtil;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow public boolean noRender;
    @Shadow @Final public Options options;

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void forceGraphicsMode(GameConfig gameConfig, CallbackInfo ci) {
        var graphicsModeOption = this.options.graphicsPreset();

        if (graphicsModeOption.get() == GraphicsPreset.FABULOUS) {
            Initializer.LOGGER.error("Fabulous graphics mode not supported, forcing Fancy.");
            graphicsModeOption.set(GraphicsPreset.FANCY);
        }

        if (this.options.improvedTransparency().get()) {
            Initializer.LOGGER.error("Improved transparency currently not supported, forcing it off.");
            this.options.improvedTransparency().set(false);
        }
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V"))
    private void redirectResourceTick(boolean bl, CallbackInfo ci, @Local(ordinal = 0) int i, @Local(ordinal = 1) int j) {
        int n = Math.min(10, i) - 1;
        boolean doUpload = j == n;
        SpriteUpdateUtil.setDoUpload(doUpload);
    }

    @Inject(method = "close", at = @At(value = "HEAD"))
    public void close(CallbackInfo ci) {
        Vulkan.waitIdle();
    }


    @Inject(method = "close", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/VirtualScreen;close()V"))
    public void close2(CallbackInfo ci) {
        Vulkan.cleanUp();
    }

    @Inject(method = "resizeDisplay", at = @At("HEAD"))
    public void onResolutionChanged(CallbackInfo ci) {
        Renderer.scheduleSwapChainUpdate();
    }

    // Fixes crash when minimizing window before setScreen is called
    @Redirect(method = "setScreen", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;noRender:Z", opcode = Opcodes.PUTFIELD))
    private void keepVar(Minecraft instance, boolean value) {}

}
