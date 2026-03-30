package net.vulkanmod.mixin.render.frapi;

import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.color.block.BlockColors;

@Mixin(BlockRenderDispatcher.class)
public interface BlockRenderDispatcherAccessor {
    @Accessor("blockColors")
    BlockColors getBlockColors();
}
