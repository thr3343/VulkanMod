package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.vulkanmod.vulkan.shader.UniformState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class FogRendererM {

    @Inject(method = "setupFog", at =@At(value = "RETURN"))
    private static void setInlineUniforms(Camera camera, FogRenderer.FogMode fogMode, float f, boolean bl, float g, CallbackInfo ci)
    {
        //FOG_SKY only sets FogStart to 0, can be optimized out to just FOG_TERRAIN
        if(fogMode.equals(FogRenderer.FogMode.FOG_TERRAIN))
        {
            final float shaderFogStart = RenderSystem.getShaderFogStart();
            final float shaderFogEnd = RenderSystem.getShaderFogEnd();
            UniformState.FogStart.getMappedBufferPtr().putFloat(0, shaderFogStart);
            UniformState.FogEnd.getMappedBufferPtr().putFloat(0, shaderFogEnd);
            UniformState.FogStart.setUpdateState(true);
            UniformState.FogEnd.setUpdateState(true);
        }
    }
}
