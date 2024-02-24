package net.vulkanmod.mixin.vertex;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DefaultVertexFormat.class)
public class DefaultVertexFormatM {
    @Mutable

    @Shadow @Final public static VertexFormatElement ELEMENT_POSITION;

    @Shadow @Final public static VertexFormatElement ELEMENT_UV0;

    @Shadow @Final public static VertexFormatElement ELEMENT_COLOR;

    @Shadow @Final public static VertexFormatElement ELEMENT_UV2;
    @Shadow @Final @Mutable public static final VertexFormat PARTICLE;


    @Shadow @Final @Mutable public static final VertexFormat POSITION_COLOR_TEX_LIGHTMAP;

    @Shadow @Final public static VertexFormat POSITION_COLOR_TEX;
    //TODO: HACK: Share + Reorder VertexAttributes to improve VAF perf + Vertex Cache Hit rate
    // Complete overkill, prob has minimal perf improvement
    static {
        PARTICLE = new VertexFormat(new ImmutableMap.Builder<String, VertexFormatElement>().put("Position", ELEMENT_POSITION).put("Color", ELEMENT_COLOR).put("UV0", ELEMENT_UV0).put("UV2", ELEMENT_UV2).build());
        //BLOCK = new VertexFormat(new ImmutableMap.Builder<String, VertexFormatElement>().put("Position", ELEMENT_POSITION).put("Color", ELEMENT_COLOR).put("UV0", ELEMENT_UV0).put("UV2", ELEMENT_UV2).build());
        POSITION_COLOR_TEX_LIGHTMAP = POSITION_COLOR_TEX;
    }

}
