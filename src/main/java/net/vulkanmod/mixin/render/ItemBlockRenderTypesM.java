package net.vulkanmod.mixin.render;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(ItemBlockRenderTypes.class)
public class ItemBlockRenderTypesM {

    @Shadow @Final private static Map<Block, RenderType> TYPE_BY_BLOCK;

    static {
        TYPE_BY_BLOCK.remove(Blocks.GLASS_PANE);
        TYPE_BY_BLOCK.put(Blocks.GLASS_PANE, RenderType.translucent());
        TYPE_BY_BLOCK.remove(Blocks.IRON_BARS);
        TYPE_BY_BLOCK.put(Blocks.IRON_BARS, RenderType.translucent());
        TYPE_BY_BLOCK.remove(Blocks.CHAIN);
        TYPE_BY_BLOCK.put(Blocks.CHAIN, RenderType.translucent());
    }
}
