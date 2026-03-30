package net.vulkanmod.mixin.vertex;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DefaultVertexFormat.class)
public class DefaultVertexFormatM {

    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexFormat$Builder;build()Lcom/mojang/blaze3d/vertex/VertexFormat;", ordinal = 14))
    private static VertexFormat fixMissingPaddingFormat(VertexFormat.Builder instance) {
        return VertexFormat.builder()
                           .add("Position", VertexFormatElement.POSITION)
                           .add("Color", VertexFormatElement.COLOR)
                           .add("Normal", VertexFormatElement.NORMAL)
                           .padding(1) // Add missing padding
                           .add("LineWidth", VertexFormatElement.LINE_WIDTH)
                           .build();
    }
}
