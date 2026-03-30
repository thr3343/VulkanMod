package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.ByteBuffer;

@Mixin(TextureAtlasSprite.class)
public class TextureAtlasSpriteMixin {
    @Shadow @Final private SpriteContents contents;
    @Shadow @Final private int padding;
    @Shadow @Final private int x;
    @Shadow @Final private int y;

    @Overwrite
    public void uploadSpriteUbo(ByteBuffer byteBuffer, int i, int maxMipLevel, int width, int height, int uboSize) {
        for (int n = 0; n <= maxMipLevel; n++) {
            Std140Builder.intoBuffer(MemoryUtil.memSlice(byteBuffer, i + n * uboSize, uboSize))
                         .putMat4f(new Matrix4f().ortho2D(0.0F, width >> n, height >> n, 0))
                         .putMat4f(
                                 new Matrix4f()
                                         .translate(this.x >> n, this.y >> n, 0.0F)
                                         .scale(this.contents.width() + this.padding * 2 >> n, this.contents.height() + this.padding * 2 >> n, 1.0F)
                         )
                         .putFloat((float)this.padding / this.contents.width())
                         .putFloat((float)this.padding / this.contents.height())
                         .putInt(n);
        }
    }
}
