package net.vulkanmod.mixin.render.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.ShaderInstance;
import net.vulkanmod.render.VBO;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
public class VertexBufferM {

    @Unique
    private VBO vbo;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void constructor(CallbackInfo ci) {
        vbo = new VBO();
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenBuffers()I"))
    private int doNothing() {
        return 0;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenVertexArrays()I"))
    private int doNothing2() {
        return 0;
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void bind() {}

    /**
     * @author
     * @reason
     */
    @Overwrite
    public static void unbind() {}

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void upload(BufferBuilder.RenderedBuffer buffer) {
        vbo.upload(buffer);
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void drawWithShader(Matrix4f viewMatrix, Matrix4f projectionMatrix, ShaderInstance shader) {
        vbo.drawWithShader(viewMatrix, projectionMatrix, shader);
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void draw() {
        vbo.drawChunkLayer();
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void close() {
        vbo.close();
    }
}
