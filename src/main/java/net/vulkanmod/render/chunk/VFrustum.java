package net.vulkanmod.render.chunk;

import net.minecraft.world.phys.AABB;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public class VFrustum {
    private Vector4f viewVector = new Vector4f();
    private double camX;
    private double camY;
    private double camZ;

    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Vector4f[] vFrustum = new Vector4f[6];
    private final Matrix4f matrix = new Matrix4f();

    public VFrustum offsetToFullyIncludeCameraCube(int offset) {
        double d0 = Math.floor(this.camX / (double)offset) * (double)offset;
        double d1 = Math.floor(this.camY / (double)offset) * (double)offset;
        double d2 = Math.floor(this.camZ / (double)offset) * (double)offset;
        double d3 = Math.ceil(this.camX / (double)offset) * (double)offset;
        double d4 = Math.ceil(this.camY / (double)offset) * (double)offset;
        double d5 = Math.ceil(this.camZ / (double)offset) * (double)offset;

        while(this.intersectAab((float)(d0 - this.camX), (float)(d1 - this.camY), (float)(d2 - this.camZ), (float)(d3 - this.camX), (float)(d4 - this.camY), (float)(d5 - this.camZ)) >= 0) {
            this.camZ -= (this.viewVector.z() * 4.0F);
            this.camX -= (this.viewVector.x() * 4.0F);
            this.camY -= (this.viewVector.y() * 4.0F);
        }

        return this;
    }

    public void setCamOffset(double camX, double camY, double camZ) {
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
    }

    public void calculateFrustum(Matrix4f modelViewMatrix, Matrix4f projMatrix) {
        projMatrix.mul(modelViewMatrix, this.matrix);
        this.frustum.set(this.matrix, false);
//        float invl;
//        float nxX = this.matrix.m03() + this.matrix.m00();
//        float nxY = this.matrix.m13() + this.matrix.m10();
//        float nxZ = this.matrix.m23() + this.matrix.m20();
//        float nxW = this.matrix.m33() + this.matrix.m30();
//        this.vFrustum[0].set(nxX, nxY, nxZ, nxW);
//        float pxX = this.matrix.m03() - this.matrix.m00();
//        float pxY = this.matrix.m13() - this.matrix.m10();
//        float pxZ = this.matrix.m23() - this.matrix.m20();
//        float pxW = this.matrix.m33() - this.matrix.m30();
//        this.vFrustum[1].set(pxX, pxY, pxZ, pxW);
//        float nyX = this.matrix.m03() + this.matrix.m01();
//        float nyY = this.matrix.m13() + this.matrix.m11();
//        float nyZ = this.matrix.m23() + this.matrix.m21();
//        float nyW = this.matrix.m33() + this.matrix.m31();
//        this.vFrustum[2].set(nyX, nyY, nyZ, nyW);
//        float pyX = this.matrix.m03() - this.matrix.m01();
//        float pyY = this.matrix.m13() - this.matrix.m11();
//        float pyZ = this.matrix.m23() - this.matrix.m21();
//        float pyW = this.matrix.m33() - this.matrix.m31();
//        this.vFrustum[3].set(pyX, pyY, pyZ, pyW);
//        float nzX = this.matrix.m03() + this.matrix.m02();
//        float nzY = this.matrix.m13() + this.matrix.m12();
//        float nzZ = this.matrix.m23() + this.matrix.m22();
//        float nzW = this.matrix.m33() + this.matrix.m32();
//        this.vFrustum[4].set(nzX, nzY, nzZ, nzW);
//        float pzX = this.matrix.m03() - this.matrix.m02();
//        float pzY = this.matrix.m13() - this.matrix.m12();
//        float pzZ = this.matrix.m23() - this.matrix.m22();
//        float pzW = this.matrix.m33() - this.matrix.m32();
//        this.vFrustum[5].set(pzX, pzY, pzZ, pzW);
        this.viewVector = this.matrix.transformTranspose(new Vector4f(0.0F, 0.0F, 1.0F, 0.0F));
    }

    public int cubeInFrustum(float x1, float y1, float z1, float x2, float y2, float z2) {
        float f = (float)(x1 - this.camX);
        float f1 = (float)(y1 - this.camY);
        float f2 = (float)(z1 - this.camZ);
        float f3 = (float)(x2 - this.camX);
        float f4 = (float)(y2 - this.camY);
        float f5 = (float)(z2 - this.camZ);
        return this.intersectAab(f, f1, f2, f3, f4, f5);
    }

    private int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return this.frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean isVisible(AABB aABB) {
        return this.cubeInFrustum(aABB.minX, aABB.minY, aABB.minZ, aABB.maxX, aABB.maxY, aABB.maxZ);
    }

    private boolean cubeInFrustum(double d, double e, double f, double g, double h, double i) {
        float j = (float)(d - this.camX);
        float k = (float)(e - this.camY);
        float l = (float)(f - this.camZ);
        float m = (float)(g - this.camX);
        float n = (float)(h - this.camY);
        float o = (float)(i - this.camZ);
        return this.frustum.testAab(j, k, l, m, n, o);
    }
}
