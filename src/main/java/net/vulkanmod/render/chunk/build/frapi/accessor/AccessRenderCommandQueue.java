package net.vulkanmod.render.chunk.build.frapi.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.renderer.v1.render.ItemRenderTypeGetter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;


import java.util.List;

public interface AccessRenderCommandQueue {
	void submitItem(
			PoseStack matrices,
			ItemDisplayContext displayContext,
			int light,
			int overlay,
			int outlineColors,
			int[] tintLayers,
			List<BakedQuad> quads,
			RenderType renderLayer,
			ItemStackRenderState.FoilType glintType,
			MeshView mesh,
			@Nullable ItemRenderTypeGetter renderTypeGetter
	);
}
