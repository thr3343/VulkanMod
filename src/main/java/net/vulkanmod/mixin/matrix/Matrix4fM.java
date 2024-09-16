package net.vulkanmod.mixin.matrix;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Matrix4f.class)
public abstract class Matrix4fM {

    @Shadow public abstract Matrix4f perspective(float fovy, float aspect, float zNear, float zFar, boolean zZeroToOne);
    @Shadow public abstract Matrix4f ortho(float left, float right, float bottom, float top, float zNear, float zFar, boolean zZeroToOne);

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public Matrix4f setOrtho(float left, float right, float bottom, float top, float zNear, float zFar) {
        //Flip ortho Top and Bottom for Vulkan NDC
        return new Matrix4f().setOrtho(left, right, top, bottom, zNear, zFar, true);
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public Matrix4f ortho(float left, float right, float bottom, float top, float zNear, float zFar) {
        //Flip ortho Top and Bottom for Vulkan NDC
        return this.ortho(left, right, top, bottom, zNear, zFar, true);
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public Matrix4f perspective(float fovy, float aspect, float zNear, float zFar) {
        return this.perspective(fovy, aspect, zNear, zFar, true);
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public Matrix4f setPerspective(float fovy, float aspect, float zNear, float zFar) {
        return new Matrix4f().setPerspective(fovy, aspect, zNear, zFar, true);
    }

    @WrapOperation(method = "setPerspective(FFFFZ)Lorg/joml/Matrix4f;",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;_m11(F)Lorg/joml/Matrix4f;"), remap = false)
    private Matrix4f flipPerspectiveY(Matrix4f instance, float m11, Operation<Matrix4f> original)
    {
        //invertNDCY or flipPerspectiveY

        //Invert the y component of the Perspective Matrix
        return original.call(instance, m11*-1.0f);
    }

}
