package net.vulkanmod.mixin.render.block;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.vulkanmod.render.model.quad.QuadView;
import net.vulkanmod.render.model.quad.ModelQuadFlags;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.vulkanmod.render.model.quad.ModelQuad.VERTEX_SIZE;

@Mixin(BakedQuad.class)
public class BakedQuadM implements QuadView {

    @Shadow @Final protected int[] vertices;
    @Shadow @Final protected Direction direction;
    @Shadow @Final protected int tintIndex;
    @Unique
    private int flags;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(int[] vertices, int tintIndex, Direction direction, TextureAtlasSprite textureAtlasSprite, boolean shade, CallbackInfo ci) {
        this.flags = ModelQuadFlags.getQuadFlags(vertices, direction);
    }

    @Override
    public int vulkanMod$getFlags() {
        return flags;
    }

    @Override
    public float vulkanMod$getX(int idx) {
        return Float.intBitsToFloat(this.vertices[vertexOffset(idx) + 0]);
    }

    @Override
    public float vulkanMod$getY(int idx) {
        return Float.intBitsToFloat(this.vertices[vertexOffset(idx) + 1]);
    }

    @Override
    public float vulkanMod$getZ(int idx) {
        return Float.intBitsToFloat(this.vertices[vertexOffset(idx) + 2]);
    }

    @Override
    public int vulkanMod$getColor(int idx) {
        return this.vertices[vertexOffset(idx) + 3];
    }

    @Override
    public float vulkanMod$getU(int idx) {
        return Float.intBitsToFloat(this.vertices[vertexOffset(idx) + 4]);
    }

    @Override
    public float vulkanMod$getV(int idx) {
        return Float.intBitsToFloat(this.vertices[vertexOffset(idx) + 5]);
    }

    @Override
    public int vulkanMod$getColorIndex() {
        return this.tintIndex;
    }

    @Override
    public Direction vulkanMod$getFacingDirection() {
        return this.direction;
    }

    @Override
    public boolean vulkanMod$isTinted() {
        return this.tintIndex != -1;
    }

    @Unique
    private static int vertexOffset(int vertexIndex) {
        return vertexIndex * VERTEX_SIZE;
    }
}
