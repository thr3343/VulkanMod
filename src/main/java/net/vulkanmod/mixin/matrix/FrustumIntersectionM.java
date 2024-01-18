package net.vulkanmod.mixin.matrix;

import org.joml.FrustumIntersection;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FrustumIntersection.class)
public class FrustumIntersectionM {
    @Shadow @Final private Vector4f[] planes;
    @Shadow @Final public static int PLANE_MASK_NX;
    @Shadow private float nxX;
    @Shadow private float nxY;
    @Shadow private float nxZ;
    @Shadow private float nxW;
    @Shadow @Final public static int PLANE_PX;
    @Shadow @Final public static int PLANE_MASK_PX;
    @Shadow private float pxX;
    @Shadow private float pxY;
    @Shadow private float pxZ;
    @Shadow private float pxW;
    @Shadow @Final public static int PLANE_NY;
    @Shadow @Final public static int PLANE_MASK_NY;
    @Shadow private float nyX;
    @Shadow private float nyY;
    @Shadow private float nyZ;
    @Shadow private float nyW;
    @Shadow @Final public static int PLANE_PY;
    @Shadow @Final public static int PLANE_MASK_PY;
    @Shadow private float pyX;
    @Shadow private float pyY;
    @Shadow private float pyZ;
    @Shadow private float pyW;
    @Shadow @Final public static int PLANE_NZ;
    @Shadow @Final public static int PLANE_MASK_NZ;
    @Shadow private float nzX;
    @Shadow private float nzY;
    @Shadow private float nzZ;
    @Shadow private float nzW;
    @Shadow @Final public static int PLANE_PZ;
    @Shadow @Final public static int PLANE_MASK_PZ;
    @Shadow private float pzX;
    @Shadow private float pzY;
    @Shadow private float pzZ;
    @Shadow private float pzW;
    @Shadow @Final public static int INSIDE;
    @Shadow @Final public static int INTERSECT;

    @Shadow @Final public static int OUTSIDE;

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int mask, int startPlane) {
        //Modified version of intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int mask, int startPlane)
        //Which skips testing all Frustum planes + tests the specified plane only
        Vector4f p = planes[startPlane];
        if (p.x * (p.x < 0 ? minX : maxX) + p.y * (p.y < 0 ? minY : maxY) + p.z * (p.z < 0 ? minZ : maxZ) < -p.w) {
            return startPlane;
        }

        return OUTSIDE;
    }
}
