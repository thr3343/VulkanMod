package net.vulkanmod.mixin.vertex;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.vulkanmod.interfaces.ExtendedVertexBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OutlineBufferSource.EntityOutlineGenerator.class)
public class EntityOutlineGeneratorM implements ExtendedVertexBuilder {

    @Unique
    private ExtendedVertexBuilder extDelegate;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void getExtBuilder(VertexConsumer vertexConsumer, int i, int j, int k, int l, CallbackInfo ci) {
        this.extDelegate = (ExtendedVertexBuilder) vertexConsumer;
    }

    @Override
    public void vulkanMod$vertex(float x, float y, float z, int packedColor, float u, float v, int overlay, int light, int packedNormal) {
        this.extDelegate.vulkanMod$vertex(x, y, z, packedColor, u, v, overlay, light, packedNormal);
//        this.delegate.vertex((double)f, (double)g, (double)h).color(this.defaultR, this.defaultG, this.defaultB, this.defaultA).uv(m, n);
    }
}
