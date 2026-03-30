package net.vulkanmod.render.chunk.build.frapi;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.BlockVertexConsumerProvider;
import net.fabricmc.fabric.api.renderer.v1.render.FabricBlockModelRenderer;
import net.fabricmc.fabric.api.renderer.v1.render.ItemRenderTypeGetter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderLayerHelper;
import net.fabricmc.fabric.mixin.client.indigo.renderer.BlockRenderDispatcherAccessor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.render.chunk.build.frapi.accessor.AccessLayerRenderState;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableMeshImpl;
import net.vulkanmod.render.chunk.build.frapi.render.BlockRenderContext;
import net.vulkanmod.render.chunk.build.frapi.render.SimpleBlockRenderContext;

/**
 * Fabric renderer implementation.
 */
public class VulkanModRenderer implements Renderer {
	public static final VulkanModRenderer INSTANCE = new VulkanModRenderer();

	private VulkanModRenderer() {}

	@Override
	public MutableMesh mutableMesh() {
		return new MutableMeshImpl();
	}

	@Override
	public void render(ModelBlockRenderer modelBlockRenderer, BlockAndTintGetter blockAndTintGetter,
					   BlockStateModel blockStateModel, BlockState blockState, BlockPos blockPos, PoseStack poseStack,
					   BlockVertexConsumerProvider blockVertexConsumerProvider, boolean cull, long seed, int overlay) {
		BlockRenderContext.POOL.get().render(blockAndTintGetter, blockStateModel, blockState, blockPos, poseStack, blockVertexConsumerProvider, cull, seed, overlay);
	}

	@Override
	public void render(PoseStack.Pose pose, BlockVertexConsumerProvider blockVertexConsumerProvider, BlockStateModel blockStateModel,
					   float v, float v1, float v2, int i, int i1, BlockAndTintGetter blockAndTintGetter,
					   BlockPos blockPos, BlockState blockState) {
		SimpleBlockRenderContext.POOL.get().bufferModel(pose, blockVertexConsumerProvider, blockStateModel, v, v1, v2, i, i1, blockAndTintGetter, blockPos, blockState);
	}

	@Override
	public void renderBlockAsEntity(BlockRenderDispatcher blockRenderDispatcher, BlockState state, PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay, BlockAndTintGetter blockView, BlockPos pos) {
		RenderShape blockRenderType = state.getRenderShape();

		if (blockRenderType != RenderShape.INVISIBLE) {
			BlockStateModel model = blockRenderDispatcher.getBlockModel(state);
			int tint = ((BlockRenderDispatcherAccessor) blockRenderDispatcher).getBlockColors().getColor(state, null, null, 0);
			float red = (tint >> 16 & 255) / 255.0F;
			float green = (tint >> 8 & 255) / 255.0F;
			float blue = (tint & 255) / 255.0F;
			FabricBlockModelRenderer.render(poseStack.last(), RenderLayerHelper.entityDelegate(bufferSource), model, red, green, blue, light, overlay, blockView, pos, state);
		}
	}

	@Override
	public QuadEmitter getLayerRenderStateEmitter(ItemStackRenderState.LayerRenderState layer) {
		return ((AccessLayerRenderState) layer).getMutableMesh().emitter();
	}

	@Override
	public void setLayerRenderTypeGetter(ItemStackRenderState.LayerRenderState layer,
										 ItemRenderTypeGetter renderTypeGetter) {
		((AccessLayerRenderState) layer).setRenderTypeGetter(renderTypeGetter);
	}
}
