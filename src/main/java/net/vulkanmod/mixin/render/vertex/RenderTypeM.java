package net.vulkanmod.mixin.render.vertex;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderType.class)
public class RenderTypeM {

//    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;create(Ljava/lang/String;Lcom/mojang/blaze3d/vertex/VertexFormat;Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;ILnet/minecraft/client/renderer/RenderType$CompositeState;)Lnet/minecraft/client/renderer/RenderType$CompositeRenderType;"))
//    private static RenderType.CompositeRenderType crumbling(String string, VertexFormat vertexFormat, VertexFormat.Mode mode, int i, RenderType.CompositeState compositeState)
//    {
//
//        return null;
//    }
}
