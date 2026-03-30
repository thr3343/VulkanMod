package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {

    @Accessor("pipeline")
    RenderPipeline pipeline();

    @Accessor("layeringTransform")
    LayeringTransform layeringTransform();

    @Accessor("outputTarget")
    OutputTarget outputTarget();

    @Accessor("textureTransform")
    TextureTransform textureTransform();
}
