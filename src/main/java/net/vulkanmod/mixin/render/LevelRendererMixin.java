package net.vulkanmod.mixin.render;

import com.google.gson.JsonSyntaxException;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.vulkan.Renderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow private @Nullable PostChain entityEffect;

    @Shadow @Final private Minecraft minecraft;

    @Shadow @Nullable private RenderTarget entityTarget;

    @Shadow @Final private static Logger LOGGER;

    
    
    @Inject(method = "renderLevel", at = @At(value = "RETURN"))
    private void setSuBPass(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci)
    {
        Renderer.getInstance().getMainPass().getMainRenderPass().nextSubPass(Renderer.getCommandBuffer(), 1);
    }
//    /**
//     * @author
//     */
//    @Overwrite
//    public void initOutline() {
//        if (this.entityEffect != null) {
//            this.entityEffect.close();
//        }
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
//    }

}