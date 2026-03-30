package net.vulkanmod.mixin.render.vertex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.vulkanmod.mixin.matrix.PoseAccessor;
import net.vulkanmod.render.util.MathUtil;
import net.vulkanmod.render.vertex.format.I32_SNorm;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(VertexConsumer.class)
public interface VertexConsumerM {

    @Shadow void addVertex(float f, float g, float h, int i, float j, float k, int l, int m, float n, float o,
                   float p);

    @Overwrite
    default void putBulkData(PoseStack.Pose pose, BakedQuad bakedQuad, float[] brightness, float r, float g, float b, float a,
                            int[] lights, int overlay) {
        Vector3fc vector3fc = bakedQuad.direction().getUnitVec3f();
        Matrix4f matrix4f = pose.pose();
        boolean trustedNormals = ((PoseAccessor)(Object)pose).trustedNormals();
        int packedNormal = MathUtil.packTransformedNorm(pose.normal(), trustedNormals, vector3fc.x(), vector3fc.y(), vector3fc.z());

        int lightEmission = bakedQuad.lightEmission();

        for (int l = 0; l < 4; l++) {
            Vector3fc quadPos = bakedQuad.position(l);
            long packedUV = bakedQuad.packedUV(l);
            float br = brightness[l];
            int color = ColorUtil.RGBA.pack(r * br, g * br, b * br, a);
            int light = LightTexture.lightCoordsWithEmission(lights[l], lightEmission);

            float x = quadPos.x();
            float y = quadPos.y();
            float z = quadPos.z();
            float tx = MathUtil.transformX(matrix4f, x, y, z);
            float ty = MathUtil.transformY(matrix4f, x, y, z);
            float tz = MathUtil.transformZ(matrix4f, x, y, z);

            float u = UVPair.unpackU(packedUV);
            float v = UVPair.unpackV(packedUV);
            this.addVertex(tx, ty, tz, color, u, v, overlay, light, I32_SNorm.unpackX(packedNormal), I32_SNorm.unpackY(packedNormal), I32_SNorm.unpackZ(packedNormal));
        }
    }
}
