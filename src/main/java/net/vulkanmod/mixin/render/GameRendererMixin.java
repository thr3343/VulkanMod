package net.vulkanmod.mixin.render;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.vulkanmod.vulkan.memory.MemoryManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final private Map<String, ShaderInstance> shaders;

    @Shadow private @Nullable static ShaderInstance positionShader;
    @Shadow private @Nullable static ShaderInstance positionColorShader;
    @Shadow private @Nullable static ShaderInstance positionColorTexShader;
    @Shadow private @Nullable static ShaderInstance positionTexShader;
    @Shadow private @Nullable static ShaderInstance positionTexColorShader;
    @Shadow private @Nullable static ShaderInstance particleShader;
    @Shadow private @Nullable static ShaderInstance positionTexColorNormalShader;
    @Shadow private @Nullable static ShaderInstance rendertypeSolidShader;
    @Shadow private @Nullable static ShaderInstance rendertypeCutoutMippedShader;
    @Shadow private @Nullable static ShaderInstance rendertypeCutoutShader;
    @Shadow private @Nullable static ShaderInstance rendertypeTranslucentShader;
    @Shadow private @Nullable static ShaderInstance rendertypeTranslucentMovingBlockShader;
    @Shadow private @Nullable static ShaderInstance rendertypeArmorCutoutNoCullShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntitySolidShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntityCutoutShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntityCutoutNoCullShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntityCutoutNoCullZOffsetShader;
    @Shadow private @Nullable static ShaderInstance rendertypeItemEntityTranslucentCullShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntityTranslucentCullShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntityTranslucentShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntityTranslucentEmissiveShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntitySmoothCutoutShader;
    @Shadow private @Nullable static ShaderInstance rendertypeBeaconBeamShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntityDecalShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntityNoOutlineShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntityShadowShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntityAlphaShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEyesShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEnergySwirlShader;
    @Shadow private @Nullable static ShaderInstance rendertypeLeashShader;
    @Shadow private @Nullable static ShaderInstance rendertypeWaterMaskShader;
    @Shadow private @Nullable static ShaderInstance rendertypeOutlineShader;
    @Shadow private @Nullable static ShaderInstance rendertypeArmorGlintShader;
    @Shadow private @Nullable static ShaderInstance rendertypeArmorEntityGlintShader;
    @Shadow private @Nullable static ShaderInstance rendertypeGlintTranslucentShader;
    @Shadow private @Nullable static ShaderInstance rendertypeGlintShader;
    @Shadow private @Nullable static ShaderInstance rendertypeGlintDirectShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntityGlintShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEntityGlintDirectShader;
    @Shadow private @Nullable static ShaderInstance rendertypeTextShader;
    @Shadow private @Nullable static ShaderInstance rendertypeTextIntensityShader;
    @Shadow private @Nullable static ShaderInstance rendertypeTextSeeThroughShader;
    @Shadow private @Nullable static ShaderInstance rendertypeTextIntensitySeeThroughShader;
    @Shadow private @Nullable static ShaderInstance rendertypeLightningShader;
    @Shadow private @Nullable static ShaderInstance rendertypeTripwireShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEndPortalShader;
    @Shadow private @Nullable static ShaderInstance rendertypeEndGatewayShader;
    @Shadow private @Nullable static ShaderInstance rendertypeLinesShader;
    @Shadow private @Nullable static ShaderInstance rendertypeCrumblingShader;

    @Shadow private static @Nullable ShaderInstance rendertypeTextBackgroundShader;
    @Shadow private static @Nullable ShaderInstance rendertypeTextBackgroundSeeThroughShader;
    @Shadow private static @Nullable ShaderInstance rendertypeGuiShader;
    @Shadow private static @Nullable ShaderInstance rendertypeGuiOverlayShader;
    @Shadow private static @Nullable ShaderInstance rendertypeGuiTextHighlightShader;
    @Shadow private static @Nullable ShaderInstance rendertypeGuiGhostRecipeOverlayShader;
    //TODO: use as base shaders for deduping...
//    @Shadow private @Nullable static ShaderInstance positionColorLightmapShader;
//    @Shadow private @Nullable static ShaderInstance positionColorTexLightmapShader;
//    @Shadow private @Nullable static ShaderInstance positionTexLightmapColorShader;

    @Shadow public ShaderInstance blitShader;

    @Shadow protected abstract ShaderInstance preloadShader(ResourceProvider resourceProvider, String string, VertexFormat vertexFormat);

    @Shadow private static @Nullable ShaderInstance rendertypeBreezeWindShader;

    @Inject(method = "reloadShaders", at = @At("HEAD"), cancellable = true)
    public void reloadShaders(ResourceProvider provider, CallbackInfo ci) {
        RenderSystem.assertOnRenderThread();
//        List<Program> list = Lists.newArrayList();
//        list.addAll(Program.Type.FRAGMENT.getPrograms().values());
//        list.addAll(Program.Type.VERTEX.getPrograms().values());
//        list.forEach(Program::close);
        List<Pair<ShaderInstance, Consumer<ShaderInstance>>> list1 = Lists.newArrayListWithCapacity(this.shaders.size());

        try {
            list1.add(Pair.of(new ShaderInstance(provider, "particle", DefaultVertexFormat.PARTICLE), (shaderInstance) -> {
                particleShader = shaderInstance;
            }));
            list1.add(Pair.of(new ShaderInstance(provider, "position", DefaultVertexFormat.POSITION), (shaderInstance) -> {
                positionShader = shaderInstance;
            }));

            ShaderInstance positionColor = new ShaderInstance(provider, "position_color", DefaultVertexFormat.POSITION_COLOR);
            list1.add(Pair.of(positionColor, (shaderInstance) -> positionColorShader = shaderInstance));
//            list1.add(Pair.of(new ShaderInstance(provider, "position_color_lightmap", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP), (shaderInstance) -> {
//               positionColorLightmapShader = shaderInstance;
//            }));
            final ShaderInstance positionColorTex = new ShaderInstance(provider, "position_color_tex", DefaultVertexFormat.POSITION_COLOR_TEX);
            list1.add(Pair.of(positionColorTex, (shaderInstance) -> {
                positionColorTexShader = shaderInstance;
            }));
//            list1.add(Pair.of(new ShaderInstance(provider, "position_color_tex_lightmap", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), (shaderInstance) -> {
//               positionColorTexLightmapShader = shaderInstance;
//            }));
            list1.add(Pair.of(new ShaderInstance(provider, "position_tex", DefaultVertexFormat.POSITION_TEX), (shaderInstance) -> {
                positionTexShader = shaderInstance;
            }));
            list1.add(Pair.of(new ShaderInstance(provider, "position_tex_color", DefaultVertexFormat.POSITION_TEX_COLOR), (shaderInstance) -> {
                positionTexColorShader = shaderInstance;
            }));
            list1.add(Pair.of(new ShaderInstance(provider, "position_tex_color_normal", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL), (shaderInstance) -> {
                positionTexColorNormalShader = shaderInstance;
            }));
//            list1.add(Pair.of(new ShaderInstance(provider, "position_tex_lightmap_color", DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR), (shaderInstance) -> {
//               positionTexLightmapColorShader = shaderInstance;
//            }));
            //TODO: only used for Falling Blocks,
            final ShaderInstance rendertypeSolid = new ShaderInstance(provider, "rendertype_solid", DefaultVertexFormat.BLOCK);
            list1.add(Pair.of(rendertypeSolid, (shaderInstance) -> {
                rendertypeSolidShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeSolid, (shaderInstance) -> {
                rendertypeCutoutMippedShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeSolid, (shaderInstance) -> {
                rendertypeCutoutShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeSolid, (shaderInstance) -> {
                rendertypeTranslucentShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeSolid, (shaderInstance) -> {
                rendertypeTranslucentMovingBlockShader = shaderInstance;
            }));
            //TODO: HACK: Use rendertype_entity_translucent_cull instead of rendertype_entity_cutout_no_cull
            // (Is potentially more performant)
            //rendertype_entity_cutout_no_cull: Allows Read Hurt animatio overlay
            final ShaderInstance rendertypeEntityFullAnim = new ShaderInstance(provider, "rendertype_entity_cutout_no_cull", DefaultVertexFormat.NEW_ENTITY);
            final ShaderInstance rendertypeEntityDef = new ShaderInstance(provider, "rendertype_entity_translucent_cull", DefaultVertexFormat.NEW_ENTITY);
            final ShaderInstance rendertypeEntityEarlyZ = new ShaderInstance(provider, "rendertype_entity_no_outline", DefaultVertexFormat.NEW_ENTITY);

            list1.add(Pair.of(rendertypeEntityDef, (shaderInstance) -> {
                rendertypeArmorCutoutNoCullShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeEntityEarlyZ, (shaderInstance) -> {
                rendertypeEntitySolidShader = shaderInstance;
            }));
            //No diff in these shaders
//            ShaderInstance rendertype_entity_cutout_no_cull = new ShaderInstance(provider, "rendertype_entity_cutout_no_cull", DefaultVertexFormat.NEW_ENTITY);

            list1.add(Pair.of(rendertypeEntityDef, (shaderInstance) -> {
                rendertypeEntityCutoutShader = shaderInstance;
            }));

            list1.add(Pair.of(rendertypeEntityFullAnim, (shaderInstance) -> {
                rendertypeEntityCutoutNoCullShader = shaderInstance;
            }));
//            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_entity_cutout_no_cull_z_offset", DefaultVertexFormat.POSITION_COLOR_TEX_OVERLAY_LIGHTMAP), (p_172654_) -> {
//               rendertypeEntityCutoutNoCullZOffsetShader = p_172654_;
//            }));
            list1.add(Pair.of(rendertypeEntityDef, (shaderInstance) -> {
                rendertypeEntityCutoutNoCullZOffsetShader = shaderInstance;
            }));

            list1.add(Pair.of(rendertypeEntityDef, (shaderInstance) -> {
                rendertypeItemEntityTranslucentCullShader = shaderInstance;
            }));
//            final ShaderInstance rendertypeEntityTranslucent = new ShaderInstance(provider, "rendertype_entity_translucent", DefaultVertexFormat.NEW_ENTITY);
            list1.add(Pair.of(rendertypeEntityDef, (shaderInstance) -> {
                rendertypeEntityTranslucentCullShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeEntityDef, (shaderInstance) -> {
                rendertypeEntityTranslucentShader = shaderInstance;
            }));
            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_entity_translucent_emissive", DefaultVertexFormat.NEW_ENTITY), shader -> {
                rendertypeEntityTranslucentEmissiveShader = shader;
            }));
            list1.add(Pair.of(rendertypeEntityDef, (shaderInstance) -> {
                rendertypeEntitySmoothCutoutShader = shaderInstance;
            }));
            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_beacon_beam", DefaultVertexFormat.BLOCK), (shaderInstance) -> {
                rendertypeBeaconBeamShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeEntityDef, (shaderInstance) -> {
                rendertypeEntityDecalShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeEntityEarlyZ, (shaderInstance) -> {
                rendertypeEntityNoOutlineShader = shaderInstance;
            }));
            //TODO: Possible GUI Lighting Glitches w/ ColorModulator
            final ShaderInstance rendertypeEntityNoLighting = new ShaderInstance(provider, "rendertype_entity_shadow", DefaultVertexFormat.NEW_ENTITY);
            list1.add(Pair.of(rendertypeEntityNoLighting, (shaderInstance) -> {
                rendertypeEntityShadowShader = shaderInstance;
            }));
            //Only used for EnderDragon death (//TODO: may crash)
            list1.add(Pair.of(positionColorTex, (shaderInstance) -> {
                rendertypeEntityAlphaShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeEntityNoLighting, (shaderInstance) -> {
                rendertypeEyesShader = shaderInstance;
            }));
            ShaderInstance energySwirl = new ShaderInstance(provider, "rendertype_energy_swirl", DefaultVertexFormat.NEW_ENTITY);
            list1.add(Pair.of(energySwirl, (shaderInstance) -> {
                rendertypeEnergySwirlShader = shaderInstance;
            }));
            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_leash", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP), (shaderInstance) -> {
                rendertypeLeashShader = shaderInstance;
            }));
            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_water_mask", DefaultVertexFormat.POSITION), (shaderInstance) -> {
                rendertypeWaterMaskShader = shaderInstance;
            }));
            //TODO: might break Glowing Effect (Even though its disabled currently due to RenderPass-related issues (i.e. Imageless Framebuffers meight help alot)
            list1.add(Pair.of(positionColorTex, (shaderInstance) -> {
                rendertypeOutlineShader = shaderInstance;
            }));
            final ShaderInstance rendertypeGlintTranslucent = new ShaderInstance(provider, "rendertype_glint_translucent", DefaultVertexFormat.POSITION_TEX);
            list1.add(Pair.of(rendertypeGlintTranslucent, (shaderInstance) -> {
                rendertypeArmorGlintShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeGlintTranslucent, (shaderInstance) -> {
                rendertypeArmorEntityGlintShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeGlintTranslucent, (shaderInstance) -> {
                rendertypeGlintTranslucentShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeGlintTranslucent, (shaderInstance) -> {
                rendertypeGlintShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeGlintTranslucent, (shaderInstance) -> {
                rendertypeGlintDirectShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeGlintTranslucent, (shaderInstance) -> {
                rendertypeEntityGlintShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeGlintTranslucent, (shaderInstance) -> {
                rendertypeEntityGlintDirectShader = shaderInstance;
            }));
            //TODO; Text Shaders can be replaced w/ positionColorTex if POSITION_COLOR_TEX_LIGHTMAP is replaced w/ POSITION_COLOR_TEX;
            //Text
            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_text", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), (shaderInstance) -> {
                rendertypeTextShader = shaderInstance;
            }));
            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_text_background", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP), (shaderInstance) -> {
                rendertypeTextBackgroundShader = shaderInstance;
            }));
            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_text_intensity", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), (shaderInstance) -> {
                rendertypeTextIntensityShader = shaderInstance;
            }));
            //TODO; Text Shaders can be replaced w/ positionColorTex if POSITION_COLOR_TEX_LIGHTMAP is replaced w/ POSITION_COLOR_TEX;
            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_text_see_through", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), (shaderInstance) -> {
                rendertypeTextSeeThroughShader = shaderInstance;
            }));
            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_text_background_see_through", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP), (shaderInstance) -> {
                rendertypeTextBackgroundSeeThroughShader = shaderInstance;
            }));
            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_text_intensity_see_through", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), (shaderInstance) -> {
                rendertypeTextIntensitySeeThroughShader = shaderInstance;
            }));

            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_lightning", DefaultVertexFormat.POSITION_COLOR), (shaderInstance) -> {
                rendertypeLightningShader = shaderInstance;
            }));
            list1.add(Pair.of(rendertypeSolid, (shaderInstance) -> {
                rendertypeTripwireShader = shaderInstance;
            }));
            ShaderInstance endPortalShader = new ShaderInstance(provider, "rendertype_end_portal", DefaultVertexFormat.POSITION);
            list1.add(Pair.of(endPortalShader, (shaderInstance) -> {
                rendertypeEndPortalShader = shaderInstance;
            }));
            list1.add(Pair.of(endPortalShader, (shaderInstance) -> {
                rendertypeEndGatewayShader = shaderInstance;
            }));
            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL), (shaderInstance) -> {
                rendertypeLinesShader = shaderInstance;
            }));
            //TODO Replace w/ positionColorTex
            list1.add(Pair.of(new ShaderInstance(provider, "rendertype_crumbling", DefaultVertexFormat.BLOCK), (shaderInstance) -> {
                rendertypeCrumblingShader = shaderInstance;
            }));

            list1.add(Pair.of(positionColor, (shaderInstance) -> {
                rendertypeGuiShader = shaderInstance;
            }));
            list1.add(Pair.of(positionColor, (shaderInstance) -> {
                rendertypeGuiOverlayShader = shaderInstance;
            }));
            list1.add(Pair.of(positionColor, (shaderInstance) -> {
                rendertypeGuiTextHighlightShader = shaderInstance;
            }));
            list1.add(Pair.of(positionColor, (shaderInstance) -> {
                rendertypeGuiGhostRecipeOverlayShader = shaderInstance;
            }));
            list1.add(Pair.of(energySwirl, (shaderInstance) -> {
                rendertypeBreezeWindShader = shaderInstance;
            }));
        } catch (IOException ioexception) {
            list1.forEach((pair) -> {
                pair.getFirst().close();
            });
            throw new RuntimeException("could not reload shaders", ioexception);
        }

        this.shutdownShaders();
        list1.forEach((pair) -> {
            ShaderInstance shaderinstance = pair.getFirst();
            this.shaders.put(shaderinstance.getName(), shaderinstance);
            pair.getSecond().accept(shaderinstance);
        });

        ci.cancel();
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    private void shutdownShaders() {
        RenderSystem.assertOnRenderThread();

        final var clearList = ImmutableList.copyOf(this.shaders.values());
        MemoryManager.getInstance().addFrameOp(() -> clearList.forEach((ShaderInstance::close)));

        this.shaders.clear();
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void preloadUiShader(ResourceProvider resourceProvider) {
        if (this.blitShader != null) {
            throw new RuntimeException("Blit shader already preloaded");
        } else {
            try {
                this.blitShader = new ShaderInstance(resourceProvider, "blit_screen", DefaultVertexFormat.POSITION_TEX);
            } catch (IOException var3) {
                throw new RuntimeException("could not preload blit shader", var3);
            }

            positionShader = this.preloadShader(resourceProvider, "position", DefaultVertexFormat.POSITION);
            positionColorShader = this.preloadShader(resourceProvider, "position_color", DefaultVertexFormat.POSITION_COLOR);
            final ShaderInstance positionColorTex = this.preloadShader(resourceProvider, "position_color_tex", DefaultVertexFormat.POSITION_COLOR_TEX);
            positionColorTexShader = positionColorTex;
            positionTexShader = this.preloadShader(resourceProvider, "position_tex", DefaultVertexFormat.POSITION_TEX);
            positionTexColorShader = this.preloadShader(resourceProvider, "position_tex_color", DefaultVertexFormat.POSITION_TEX_COLOR);
            rendertypeTextShader = positionColorTex;

            rendertypeGuiShader = positionColorShader;
            rendertypeGuiOverlayShader = positionColorShader;
        }
    }

//    @Redirect(method = "renderLevel", at = @At(value="INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
//    private void clear2hand(int i, boolean bl) {
//        Renderer.clearAttachments(0x100);
//    }

//    @Redirect(method = "render", at = @At(value="INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", ordinal = 0))
//    private void remClear(int i, boolean bl) {}

    /**
     * @author
     * @reason
     */
    @Overwrite
    public float getDepthFar() {
//        return this.getRenderDistance() * 4.0F;
        return Float.POSITIVE_INFINITY;
    }

}
