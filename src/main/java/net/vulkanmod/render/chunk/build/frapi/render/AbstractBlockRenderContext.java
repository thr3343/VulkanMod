package net.vulkanmod.render.chunk.build.frapi.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.vulkanmod.interfaces.color.BlockColorsExtended;
import net.vulkanmod.render.chunk.build.color.BlockColorRegistry;
import net.vulkanmod.render.chunk.build.frapi.VulkanModRenderer;
import net.vulkanmod.render.chunk.build.light.LightPipeline;
import net.vulkanmod.render.chunk.build.light.data.QuadLightData;
import org.jetbrains.annotations.Nullable;
import net.vulkanmod.render.chunk.build.frapi.helper.ColorHelper;
import net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class AbstractBlockRenderContext extends AbstractRenderContext {
	private static final Renderer RENDERER = VulkanModRenderer.INSTANCE;

	protected final BlockColorRegistry blockColorRegistry;

	private final MutableQuadViewImpl editorQuad = new MutableQuadViewImpl() {
		{
			data = new int[EncodingFormat.TOTAL_STRIDE];
			clear();
		}

		@Override
		public void emitDirectly() {
			renderQuad(this);
		}

	};

	protected BlockState blockState;
	protected BlockPos blockPos;
	protected BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();
	protected ChunkSectionLayer defaultLayer;

	protected BlockAndTintGetter renderRegion;

	protected final Object2ByteLinkedOpenHashMap<ShapePairKey> occlusionCache = new Object2ByteLinkedOpenHashMap<>(2048, 0.25F) {
		protected void rehash(int i) {
		}
	};

	protected final QuadLightData quadLightData = new QuadLightData();
	protected LightPipeline smoothLightPipeline;
	protected LightPipeline flatLightPipeline;

	protected boolean useAO;
	protected boolean defaultAO;

	protected RandomSource random;

	protected boolean enableCulling = true;
	protected int cullCompletionFlags;
	protected int cullResultFlags;

	protected AbstractBlockRenderContext() {
		this.occlusionCache.defaultReturnValue((byte) 127);

		BlockColors blockColors = Minecraft.getInstance().getBlockColors();
		this.blockColorRegistry = BlockColorsExtended.from(blockColors).getColorResolverMap();
	}

	protected void setupLightPipelines(LightPipeline flatLightPipeline, LightPipeline smoothLightPipeline) {
		this.flatLightPipeline = flatLightPipeline;
		this.smoothLightPipeline = smoothLightPipeline;
	}

	public void prepareForWorld(BlockAndTintGetter blockView, boolean enableCulling) {
		this.renderRegion = blockView;
		this.enableCulling = enableCulling;
	}

	public void prepareForBlock(BlockState blockState, BlockPos blockPos, boolean modelAo) {
		this.blockPos = blockPos;
		this.blockState = blockState;
		this.defaultLayer = ItemBlockRenderTypes.getChunkRenderType(blockState);

		this.useAO = Minecraft.useAmbientOcclusion();
		this.defaultAO = this.useAO && modelAo && blockState.getLightEmission() == 0;

		this.cullCompletionFlags = 0;
		this.cullResultFlags = 0;
	}

	public boolean isFaceCulled(@Nullable Direction face) {
		return !this.shouldRenderFace(face);
	}

	public boolean shouldRenderFace(Direction face) {
		if (face == null || !enableCulling) {
			return true;
		}

		final int mask = 1 << face.get3DDataValue();

		if ((cullCompletionFlags & mask) == 0) {
			cullCompletionFlags |= mask;

			if (this.faceNotOccluded(blockState, face)) {
				cullResultFlags |= mask;
				return true;
			} else {
				return false;
			}
		} else {
			return (cullResultFlags & mask) != 0;
		}
	}

	public boolean faceNotOccluded(BlockState blockState, Direction face) {
		BlockGetter blockGetter = this.renderRegion;

		BlockPos adjPos = tempPos.setWithOffset(blockPos, face);
		BlockState adjBlockState = blockGetter.getBlockState(adjPos);

		if (blockState.skipRendering(adjBlockState, face)) {
			return false;
		}

		if (adjBlockState.canOcclude()) {
			VoxelShape shape = blockState.getFaceOcclusionShape(face);

			if (shape.isEmpty())
				return true;

			VoxelShape adjShape = adjBlockState.getFaceOcclusionShape(face.getOpposite());

			if (adjShape.isEmpty())
				return true;

			if (shape == Shapes.block() && adjShape == Shapes.block()) {
				return false;
			}

			ShapePairKey blockStatePairKey = new ShapePairKey(shape, adjShape);

			byte b = occlusionCache.getAndMoveToFirst(blockStatePairKey);
			if (b != 127) {
				return b != 0;
			} else {
				boolean bl = Shapes.joinIsNotEmpty(shape, adjShape, BooleanOp.ONLY_FIRST);

				if (occlusionCache.size() == 2048) {
					occlusionCache.removeLastByte();
				}

				occlusionCache.putAndMoveToFirst(blockStatePairKey, (byte) (bl ? 1 : 0));
				return bl;
			}
		}

		return true;
	}

	public QuadEmitter getEmitter() {
		editorQuad.clear();
		return editorQuad;
	}

	@Override
	protected void bufferQuad(MutableQuadViewImpl quadView) {
		this.renderQuad(quadView);
	}

	protected abstract VertexConsumer getVertexConsumer(ChunkSectionLayer layer);

	private void renderQuad(MutableQuadViewImpl quad) {
		if (isFaceCulled(quad.cullFace())) {
			return;
		}

		endRenderQuad(quad);
	}

	protected void endRenderQuad(MutableQuadViewImpl quad) {}

	/** handles block color, common to all renders. */
	protected void tintQuad(MutableQuadViewImpl quad) {
		int tintIndex = quad.tintIndex();

		if (tintIndex != -1) {
			final int blockColor = getBlockColor(this.renderRegion, tintIndex);

			for (int i = 0; i < 4; i++) {
				quad.color(i, ColorHelper.multiplyColor(blockColor, quad.color(i)));
			}
		}
	}

	private int getBlockColor(BlockAndTintGetter region, int colorIndex) {
		BlockColor blockColor = this.blockColorRegistry.getBlockColor(this.blockState.getBlock());

		int color = blockColor != null ? blockColor.getColor(blockState, region, blockPos, colorIndex) : -1;
		return 0xFF000000 | color;
	}

	protected void shadeQuad(MutableQuadViewImpl quad, LightPipeline lightPipeline, boolean emissive, boolean vanillaShade) {
		QuadLightData data = this.quadLightData;

		// TODO: enhanced AO
		lightPipeline.calculate(quad, this.blockPos, data, quad.cullFace(), quad.lightFace(), quad.diffuseShade());

		if (emissive) {
			for (int i = 0; i < 4; i++) {
				quad.color(i, ColorHelper.multiplyRGB(quad.color(i), data.br[i]));
//				quad.lightmap(i, LightTexture.FULL_BRIGHT);
				data.lm[i] = LightTexture.FULL_BRIGHT;
			}
		} else {
			for (int i = 0; i < 4; i++) {
				quad.color(i, ColorHelper.multiplyRGB(quad.color(i), data.br[i]));
//				quad.lightmap(i, ColorHelper.maxBrightness(quad.lightmap(i), data.lm[i]));
				data.lm[i] = ColorHelper.maxBrightness(quad.lightmap(i), data.lm[i]);
			}
		}
	}

	public ChunkSectionLayer effectiveRenderLayer(@Nullable ChunkSectionLayer quadRenderLayer) {
		return quadRenderLayer == null ? defaultLayer : quadRenderLayer;
	}

	public void emitVanillaBlockQuads(BlockStateModel model, @Nullable BlockState state, Supplier<RandomSource> randomSupplier, Predicate<Direction> cullTest) {
		MutableQuadViewImpl quad = this.editorQuad;
//		final RenderMaterial defaultMaterial = state.getLightEmission() == 0 ? STANDARD_MATERIAL : NO_AO_MATERIAL;

		for (int i = 0; i <= ModelHelper.NULL_FACE_ID; i++) {
			final Direction cullFace = ModelHelper.faceFromIndex(i);

			if (cullTest.test(cullFace)) {
				// Skip entire quad list if possible.
				continue;
			}

			final List<BlockModelPart> parts = ((BlockStateModel) this).collectParts(random);
			final int partCount = parts.size();

			for (int j = 0; j < partCount; j++) {
				parts.get(j).emitQuads(quad, cullTest);
			}
		}

	}

	// TODO move elsewhere
	record ShapePairKey(VoxelShape first, VoxelShape second) {
		public boolean equals(Object object) {
			if (object instanceof ShapePairKey shapePairKey && this.first == shapePairKey.first && this.second == shapePairKey.second) {
				return true;
			}

			return false;
		}

		public int hashCode() {
			return System.identityHashCode(this.first) * 31 + System.identityHashCode(this.second);
		}
	}

}
