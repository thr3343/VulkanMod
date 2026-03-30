package net.vulkanmod.mixin.render.block;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.vulkanmod.render.chunk.build.frapi.helper.NormalHelper;
import net.vulkanmod.render.chunk.cull.QuadFacing;
import net.vulkanmod.render.model.quad.ModelQuadView;
import net.vulkanmod.render.model.quad.ModelQuadFlags;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.vulkanmod.render.model.quad.ModelQuad.VERTEX_SIZE;

@Mixin(BakedQuad.class)
public abstract class BakedQuadM implements ModelQuadView {

    @Shadow @Final protected Direction direction;
    @Shadow @Final protected int tintIndex;

    @Shadow
    public abstract Vector3fc position(int i);

    @Shadow
    public abstract long packedUV(int i);

    private int flags;
    private int normal;
    private QuadFacing facing;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(Vector3fc position0, Vector3fc position1,
                        Vector3fc position2, Vector3fc position3,
                        long packedUV0, long packedUV1,
                        long packedUV2, long packedUV3,
                        int tintIndex, Direction direction,
                        TextureAtlasSprite sprite, boolean shade, int lightEmission,
                        CallbackInfo ci) {
        this.flags = ModelQuadFlags.getQuadFlags(this, direction);

        int packedNormal = NormalHelper.computePackedNormal(this);
        this.normal = packedNormal;
        this.facing = QuadFacing.fromNormal(packedNormal);
    }

    @Override
    public int getFlags() {
        return flags;
    }

    @Override
    public float getX(int idx) {
        return this.position(idx).x();
    }

    @Override
    public float getY(int idx) {
        return this.position(idx).y();
    }

    @Override
    public float getZ(int idx) {
        return this.position(idx).z();
    }

    @Override
    public int getColor(int idx) {
        return 0xFFFFFFFF;
    }

    @Override
    public float getU(int idx) {
        return UVPair.unpackU(this.packedUV(idx));
    }

    @Override
    public float getV(int idx) {
        return UVPair.unpackV(this.packedUV(idx));
    }

    @Override
    public int getColorIndex() {
        return this.tintIndex;
    }

    @Override
    public Direction lightFace() {
        return this.direction;
    }

    @Override
    public Direction getFacingDirection() {
        return this.direction;
    }

    @Override
    public QuadFacing getQuadFacing() {
        return this.facing;
    }

    @Override
    public int getNormal() {
        return this.normal;
    }

    @Override
    public boolean isTinted() {
        return this.tintIndex != -1;
    }

    private static int vertexOffset(int vertexIndex) {
        return vertexIndex * VERTEX_SIZE;
    }
}
