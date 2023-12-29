package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.vulkanmod.vulkan.VRenderSystem;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow private @Nullable PostChain entityEffect;

    @Shadow @Final private Minecraft minecraft;

    @Shadow @Nullable private RenderTarget entityTarget;

    @Shadow @Final private static Logger LOGGER;

    /**
     * @author
     */
    @Inject(method = "initOutline", at=@At(value = "HEAD"))
    public void initOutline(CallbackInfo ci) {
        VRenderSystem.setPostFXState(this.entityEffect != null);
//
////        ResourceLocation resourceLocation = new ResourceLocation("shaders/post/entity_outline.json");
////
////        try {
////            this.entityEffect = new PostChain(this.minecraft.getTextureManager(), this.minecraft.getResourceManager(), this.minecraft.getMainRenderTarget(), resourceLocation);
////            this.entityEffect.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
////            this.entityTarget = this.entityEffect.getTempTarget("final");
////        } catch (IOException var3) {
////            LOGGER.warn("Failed to load shader: {}", resourceLocation, var3);
////            this.entityEffect = null;
////            this.entityTarget = null;
////        } catch (JsonSyntaxException var4) {
////            LOGGER.warn("Failed to parse shader: {}", resourceLocation, var4);
////            this.entityEffect = null;
////            this.entityTarget = null;
////        }
    }
//        @Redirect(method = "renderLevel", at=@At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
//        private void redirectClear2(int i, boolean bl) {}



}