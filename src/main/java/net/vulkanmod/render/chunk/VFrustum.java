package net.vulkanmod.render.chunk;

import net.minecraft.world.phys.AABB;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import static org.joml.FrustumIntersection.*;

public class VFrustum {
    private static final int allPlanes = PLANE_MASK_NX | PLANE_MASK_PX | PLANE_MASK_NY | PLANE_MASK_PY | PLANE_MASK_NZ | PLANE_MASK_PZ;
    private Vector4f viewVector = new Vector4f();
    private double camX, camY, camZ;

    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f matrix = new Matrix4f();

    public VFrustum offsetToFullyIncludeCameraCube(int offset) {
        double d0 = Math.floor(this.camX / (double)offset) * (double)offset;
        double d1 = Math.floor(this.camY / (double)offset) * (double)offset;
        double d2 = Math.floor(this.camZ / (double)offset) * (double)offset;
        double d3 = Math.ceil(this.camX / (double)offset) * (double)offset;
        double d4 = Math.ceil(this.camY / (double)offset) * (double)offset;
        double d5 = Math.ceil(this.camZ / (double)offset) * (double)offset;

        while(this.testLeftFrustumPlane((float)(d0 - this.camX), (float)(d1 - this.camY), (float)(d2 - this.camZ), (float)(d3 - this.camX), (float)(d4 - this.camY), (float)(d5 - this.camZ))) {
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
    //Test all Frustum Planes, but skipping left plane
    private int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return this.frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ, ~0 ^ (PLANE_MASK_NX));
    }
    //test only one Frustum plane
    /*intersectAabFast*/
    private boolean testLeftFrustumPlane(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return this.frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ, allPlanes, PLANE_NX)==PLANE_NX;
    }

    public boolean isVisible(AABB aABB) {
        return this.AABBInFrustum(aABB.minX, aABB.minY, aABB.minZ, aABB.maxX, aABB.maxY, aABB.maxZ);
    }

    public boolean AABBInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        float j = (float)(minX - this.camX);
        float k = (float)(minY - this.camY);
        float l = (float)(minZ - this.camZ);
        float m = (float)(maxX - this.camX);
        float n = (float)(maxY - this.camY);
        float o = (float)(maxZ - this.camZ);
        return testLeftFrustumPlane(j, k, l, m, n, o);
//        return this.frustum.intersectAab(j, k, l, m, n, o, PLANE_MASK_NX, PLANE_NX)==OUTSIDE;
    }
}
