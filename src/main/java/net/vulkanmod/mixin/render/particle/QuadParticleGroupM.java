package net.vulkanmod.mixin.render.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.renderer.culling.Frustum;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = QuadParticleGroup.class, priority = 999)
public class QuadParticleGroupM {

    @WrapOperation(method = "extractRenderState",
                   at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/Frustum;pointInFrustum(DDD)Z"))
    private boolean particleWithinSections(Frustum instance, double x, double y, double z, Operation<Boolean> original) {
        return !cull(WorldRenderer.getInstance(), x, y, z) && instance.pointInFrustum(x,y, z);
    }

    @Unique
    private static boolean cull(WorldRenderer worldRenderer, double x, double y, double z) {
        RenderSection section = worldRenderer.getSectionGrid().getSectionAtBlockPos((int) x, (int) y, (int) z);
        return section != null && section.getLastFrame() != worldRenderer.getLastFrame();
    }
}
