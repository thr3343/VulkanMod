package net.vulkanmod.render.chunk.build.frapi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.MatrixUtil;

import java.util.Arrays;
import java.util.List;

import net.fabricmc.fabric.api.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.FabricLayerRenderState;
import net.fabricmc.fabric.api.renderer.v1.render.ItemRenderTypeGetter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.vulkanmod.mixin.render.frapi.ItemRendererAccessor;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.vulkanmod.render.chunk.build.frapi.helper.ColorHelper;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import org.jspecify.annotations.Nullable;


/**
 * Used during item buffering to support geometry added through {@link FabricLayerRenderState#emitter()}.
 */
public class ItemRenderContext extends AbstractRenderContext {
	private static final int GLINT_COUNT = ItemStackRenderState.FoilType.values().length;

	private ItemDisplayContext itemDisplayContext;
	private MultiBufferSource vertexConsumerProvider;
	private int lightmap;
	private int[] tints;

	private RenderType defaultLayer;
	private ItemRenderTypeGetter renderTypeGetter;
	private ItemStackRenderState.FoilType defaultGlint;
    private boolean ignoreQuadGlint;

	private PoseStack.Pose specialGlintEntry;
	private final VertexConsumer[] vertexConsumerCache = new VertexConsumer[3 * GLINT_COUNT];

	public void renderModel(ItemDisplayContext itemDisplayContext, PoseStack matrixStack,
	                        MultiBufferSource bufferSource, int lightmap, int overlay, int[] tints,
	                        List<BakedQuad> modelQuads, MeshView mesh, RenderType renderType,
	                        @Nullable ItemRenderTypeGetter renderTypeGetter,
	                        ItemStackRenderState.FoilType foilType, boolean ignoreQuadGlint) {
		this.itemDisplayContext = itemDisplayContext;
		this.matrices = matrixStack.last();
		this.vertexConsumerProvider = bufferSource;
		this.lightmap = lightmap;
		this.overlay = overlay;
		this.tints = tints;

		defaultLayer = renderType;
		this.renderTypeGetter = renderTypeGetter;
		defaultGlint = foilType;
        this.ignoreQuadGlint = ignoreQuadGlint;

		bufferQuads(modelQuads, mesh);

		this.matrices = null;
		this.vertexConsumerProvider = null;
		this.tints = null;

		specialGlintEntry = null;
		Arrays.fill(vertexConsumerCache, null);
	}

    private void bufferQuads(List<BakedQuad> vanillaQuads, MeshView mesh) {
        QuadEmitter emitter = getEmitter();

        final int vanillaQuadCount = vanillaQuads.size();

        for (int i = 0; i < vanillaQuadCount; i++) {
            final BakedQuad q = vanillaQuads.get(i);
            emitter.fromBakedQuad(q);
            emitter.emit();
        }

        mesh.outputTo(emitter);
    }

	@Override
	protected void bufferQuad(MutableQuadViewImpl quad) {
        final VertexConsumer vertexConsumer = getVertexConsumer(quad.atlas(), quad.renderLayer(), quad.glint());

        tintQuad(quad);
        shadeQuad(quad, quad.emissive());
        bufferQuad(quad, vertexConsumer);
	}

	private void tintQuad(MutableQuadViewImpl quad) {
		int tintIndex = quad.tintIndex();

		if (tintIndex != -1 && tintIndex < tints.length) {
			final int tint = tints[tintIndex];

			for (int i = 0; i < 4; i++) {
				quad.color(i, ColorHelper.multiplyColor(tint, quad.color(i)));
			}
		}
	}

	private void shadeQuad(MutableQuadViewImpl quad, boolean emissive) {
		if (emissive) {
			for (int i = 0; i < 4; i++) {
				quad.lightmap(i, LightTexture.FULL_BRIGHT);
			}
		} else {
			final int lightmap = this.lightmap;

			for (int i = 0; i < 4; i++) {
				quad.lightmap(i, ColorHelper.maxBrightness(quad.lightmap(i), lightmap));
			}
		}
	}

	private VertexConsumer getVertexConsumer(QuadAtlas quadAtlas, @org.jspecify.annotations.Nullable ChunkSectionLayer quadRenderLayer, ItemStackRenderState.@Nullable FoilType quadGlint) {
		RenderType layer;
		ItemStackRenderState.FoilType glint;

		if (renderTypeGetter != null) {
			layer = renderTypeGetter.renderType(quadAtlas, quadRenderLayer);

			if (layer == null) {
				layer = defaultLayer;
			}
		} else {
			layer = defaultLayer;
		}

		if (ignoreQuadGlint || quadGlint == null) {
			glint = defaultGlint;
		} else {
			glint = quadGlint;
		}

		int cacheIndex;

		if (layer == Sheets.translucentItemSheet()) {
			cacheIndex = 0;
		} else if (layer == Sheets.cutoutBlockSheet()) {
			cacheIndex = GLINT_COUNT;
		} else if (layer == Sheets.translucentBlockItemSheet()) {
			cacheIndex = 2 * GLINT_COUNT;
		} else {
			return createVertexConsumer(layer, glint);
		}

		cacheIndex += glint.ordinal();
		VertexConsumer vertexConsumer = vertexConsumerCache[cacheIndex];

		if (vertexConsumer == null) {
			vertexConsumer = createVertexConsumer(layer, glint);
			vertexConsumerCache[cacheIndex] = vertexConsumer;
		}

		return vertexConsumer;
	}

	private VertexConsumer createVertexConsumer(RenderType layer, ItemStackRenderState.FoilType glint) {
		if (glint == ItemStackRenderState.FoilType.SPECIAL) {
			if (specialGlintEntry == null) {
				specialGlintEntry = matrices.copy();

				if (itemDisplayContext == ItemDisplayContext.GUI) {
					MatrixUtil.mulComponentWise(specialGlintEntry.pose(), 0.5F);
				} else if (itemDisplayContext.firstPerson()) {
					MatrixUtil.mulComponentWise(specialGlintEntry.pose(), 0.75F);
				}
			}

			return ItemRendererAccessor.getSpecialFoilBuffer(vertexConsumerProvider, layer, specialGlintEntry);
		}

		return ItemRenderer.getFoilBuffer(vertexConsumerProvider, layer, true, glint != ItemStackRenderState.FoilType.NONE);
	}

}
