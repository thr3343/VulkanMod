package net.vulkanmod.mixin.render;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.vulkanmod.interfaces.ExtendedVertexBuilder;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;

import static net.minecraft.world.level.biome.Biome.Precipitation.*;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow private @Nullable PostChain entityEffect;

    @Shadow @Final private Minecraft minecraft;

    @Shadow @Nullable private RenderTarget entityTarget;

    @Shadow @Final private static Logger LOGGER;

//    /**
//     * @author
//     */
//    @Overwrite

    @Shadow private int ticks;
    @Shadow @Final private float[] rainSizeX;
    @Shadow @Final private float[] rainSizeZ;
    @Shadow @Final private static ResourceLocation RAIN_LOCATION;

    @Shadow
    public static int getLightColor(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
        return 0;
    }

    @Shadow @Final private static ResourceLocation SNOW_LOCATION;

    /**
     * @author
     * @reason
     */
    @Overwrite
    private void renderSnowAndRain(LightTexture lightTexture, float f, double d, double e, double g) {
        float h = this.minecraft.level.getRainLevel(f);
        if (!(h <= 0.0F)) {
            lightTexture.turnOnLightLayer();
            Level level = this.minecraft.level;
            int i = Mth.floor(d);
            int j = Mth.floor(e);
            int k = Mth.floor(g);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferBuilder = tesselator.getBuilder();
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            int l = 5;
            if (Minecraft.useFancyGraphics()) {
                l = 10;
            }

            RenderSystem.depthMask(Minecraft.useShaderTransparency());
            int m = NONE.ordinal();
            float n = (float) this.ticks + f;
            RenderSystem.setShader(GameRenderer::getParticleShader);
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int o = k - l; o <= k + l; ++o) {
                for (int p = i - l; p <= i + l; ++p) {
                    int q = (o - k + 16) * 32 + p - i + 16;
                    double r = (double) this.rainSizeX[q] * 0.5;
                    double s = (double) this.rainSizeZ[q] * 0.5;
                    mutableBlockPos.set(p, e, o);
                    Biome biome = level.getBiome(mutableBlockPos).value();
                    if (biome.hasPrecipitation()) {
                        int t = level.getHeight(Heightmap.Types.MOTION_BLOCKING, p, o);
                        int u = j - l;
                        int v = j + l;
                        if (u < t) {
                            u = t;
                        }

                        if (v < t) {
                            v = t;
                        }

                        int w = Math.max(t, j);

                        if (u != v) {
                            RandomSource randomSource = RandomSource.create((long) p * p * 3121 + p * 45238971L ^ (long) o * o * 418711 + o * 13761L);

                            Biome.Precipitation precipitation = biome.getPrecipitationAt(mutableBlockPos.set(p, u, o));

                            double ac = (double) p + 0.5 - d;
                            double ad = (double) o + 0.5 - g;
                            float ae = (float) Math.sqrt(ac * ac + ad * ad) / (float) l;


                            int am = getLightColor(level, mutableBlockPos.set(p, w, o));

                            final ExtendedVertexBuilder bufferBuilder1 = (ExtendedVertexBuilder) (bufferBuilder);
                            switch (precipitation) {
                                case RAIN -> {
                                    if (m != RAIN.ordinal()) {
                                        if (m >= RAIN.ordinal()) {
                                            tesselator.end();
                                        }

                                        m = RAIN.ordinal();
                                        RenderSystem.setShaderTexture(0, RAIN_LOCATION);
                                        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                                    }

                                    int x = this.ticks & 131071;
                                    int y = p * p * 3121 + p * 45238971 + o * o * 418711 + o * 13761 & 255;
                                    float z = 3.0F + randomSource.nextFloat();
                                    float aa = -((float) (x + y) + f) / 32.0F * z;
                                    float ab = aa % 32.0F;

                                    float af = ((1.0F - ae * ae) * 0.5F + 0.5F) * h;

                                    final int packedColor = ColorUtil.packColorIntRGBA(1.0F, 1.0F, 1.0F, af);
                                    bufferBuilder1.vulkanMod$vertex2((float) (p - d - r + 0.5), (float) (v - e), (float) (o - g - s + 0.5), packedColor, 0.0F, u * 0.25F + ab, am);
                                    bufferBuilder1.vulkanMod$vertex2((float) (p - d + r + 0.5), (float) (v - e), (float) (o - g + s + 0.5), packedColor, 1.0F, u * 0.25F + ab, am);
                                    bufferBuilder1.vulkanMod$vertex2((float) (p - d + r + 0.5), (float) (u - e), (float) (o - g + s + 0.5), packedColor, 1.0F, v * 0.25F + ab, am);
                                    bufferBuilder1.vulkanMod$vertex2((float) (p - d - r + 0.5), (float) (u - e), (float) (o - g - s + 0.5), packedColor, 0.0F, v * 0.25F + ab, am);
                                }
                                case SNOW -> {
                                    if (m != SNOW.ordinal()) {
                                        if (m == RAIN.ordinal()) {
                                            tesselator.end();
                                        }

                                        m = SNOW.ordinal();
                                        RenderSystem.setShaderTexture(0, SNOW_LOCATION);
                                        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                                    }

                                    float ah = -((float) (this.ticks & 511) + f) / 512.0F;
                                    float ai = (float) (randomSource.nextDouble() + (double) n * 0.01 * (double) ((float) randomSource.nextGaussian()));
                                    float z = (float) (randomSource.nextDouble() + (double) (n * (float) randomSource.nextGaussian()) * 0.001);

                                    float al = ((1.0F - ae * ae) * 0.3F + 0.5F) * h;


                                    final int packedColor = ColorUtil.packColorIntRGBA(1.0F, 1.0F, 1.0F, al);
                                    bufferBuilder1.vulkanMod$vertex2((float) (p - d - r + 0.5), (float) (v - e), (float) (o - g - s + 0.5), packedColor, 0.0F + ai, u * 0.25F + ah + z, am);
                                    bufferBuilder1.vulkanMod$vertex2((float) (p - d + r + 0.5), (float) (v - e), (float) (o - g + s + 0.5), packedColor, 1.0F + ai, u * 0.25F + ah + z, am);
                                    bufferBuilder1.vulkanMod$vertex2((float) (p - d + r + 0.5), (float) (u - e), (float) (o - g + s + 0.5), packedColor, 1.0F + ai, v * 0.25F + ah + z, am);
                                    bufferBuilder1.vulkanMod$vertex2((float) (p - d - r + 0.5), (float) (u - e), (float) (o - g - s + 0.5), packedColor, 0.0F + ai, v * 0.25F + ah + z, am);
                                }
                            }
                        }
                    }
                }
            }

            if (m >= RAIN.ordinal()) {
                tesselator.end();
            }

            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            lightTexture.turnOffLightLayer();
        }
    }
//    public void initOutline() {
//        if (this.entityEffect != null) {
//            this.entityEffect.close();
//        }
//
////        ResourceLocation resourceLocation = new ResourceLocation("shaders/post/entity_outline.json");
////
////        try {
////            this.entityEffect = new PostChain(this.minecraft.getTextureManager(), this.minecraft.getResourceManager(), this.minecraft.getMainRenderTarget(), resourceLocation);
////            this.entityEffect.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
////            this.entityTarget = this.entityEffect.getTempTarget("final");
////        } catch (IOException var3) {
////            LOGGER.warn("Failed to load shader: {}", resourceLocation, var3);
////            this.entityEffect = null;
////            this.entityTarget = null;
////        } catch (JsonSyntaxException var4) {
////            LOGGER.warn("Failed to parse shader: {}", resourceLocation, var4);
////            this.entityEffect = null;
////            this.entityTarget = null;
////        }
//    }

}