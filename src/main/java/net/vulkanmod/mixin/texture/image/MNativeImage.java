package net.vulkanmod.mixin.texture.image;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.ImageUtil;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Locale;

@Mixin(NativeImage.class)
public abstract class MNativeImage {

    @Shadow private long pixels;
    @Shadow private long size;

    @Shadow public abstract void close();


    @Shadow @Final private NativeImage.Format format;

    @Shadow public abstract int getWidth();

    @Shadow @Final private int width;
    @Shadow @Final private int height;

    @Shadow public abstract int getHeight();

    @Shadow public abstract void setPixelRGBA(int i, int j, int k);

    @Shadow public abstract int getPixelRGBA(int i, int j);

    @Shadow protected abstract void checkAllocated();

    private ByteBuffer buffer;


    @WrapOperation(method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZ)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryUtil;nmemAlloc(J)J"))
    private long setAlign(long size, Operation<Long> original)
    {
//        if(size>4096)
        {
            return MemoryUtil.nmemAlignedAlloc(4096, this.size);
        }
//        else return original.call(size);

    }
//
//    @WrapOperation(method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZ)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryUtil;nmemCalloc(JJ)J"))
//    private long setAlign2(long num, long size, Operation<Long> original)
//    {
//        if(size>4096)
//        {
//            return MemoryUtil.nmemAlignedAlloc(4096, this.size);
//        }
//        else return original.call(num, size);
//
//    }

//    @Inject(method = "read(Lcom/mojang/blaze3d/platform/NativeImage$Format;Ljava/nio/ByteBuffer;)Lcom/mojang/blaze3d/platform/NativeImage;", at = @At("HEAD"))
//    private static void checkAlign(NativeImage.Format format, ByteBuffer byteBuffer, CallbackInfoReturnable<NativeImage> cir)
//    {
//        final long value = MemoryUtil.memAddress(byteBuffer);
//
//        if((value & 4095)!=0)
//        {
//            throw new UnsupportedOperationException("Bad Alignment: "+ (value & 4095));
//        }
//    }
//
//    @WrapOperation(method = "read(Lcom/mojang/blaze3d/platform/NativeImage$Format;Ljava/nio/ByteBuffer;)Lcom/mojang/blaze3d/platform/NativeImage;",
//            at = @At(value = "INVOKE", target = "Lorg/lwjgl/stb/STBImage;stbi_load_from_memory(Ljava/nio/ByteBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)Ljava/nio/ByteBuffer;"))
//    private static ByteBuffer useAlignedAddress(ByteBuffer buffer, IntBuffer x, IntBuffer y, IntBuffer channels_in_file, int desired_channels, Operation<ByteBuffer> original)
//    {
//        var a = original.call(buffer, x, y, channels_in_file, desired_channels);
//
//        if((MemoryUtil.memAddress0(buffer) & 4095)!=0 && (buffer.capacity()>=a.capacity()))
//        {
//
////            a.rewind();
////
//            MemoryUtil.memCopy(a, buffer);
//            return buffer.rewind();
////            throw new UnsupportedOperationException("Bad PreLoad Alignment: "+ (value & 4095));
//        }
//
//        return a;
//    }

    @Inject(method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZ)V", at = @At("RETURN"))
    private void constr(NativeImage.Format format, int width, int height, boolean useStb, CallbackInfo ci) {
        if(this.pixels != 0) {
            buffer = MemoryUtil.memByteBuffer(this.pixels, (int)this.size);
        }
    }

    @Inject(method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZJ)V", at = @At("RETURN"))
    private void constr(NativeImage.Format format, int width, int height, boolean useStb, long pixels, CallbackInfo ci) {
        if(this.pixels != 0) {
            if((size&4095)==0  && (this.pixels&4095)!=0)
            {
                Initializer.LOGGER.error("Realigning {}", this.size);
//                //TODO: PreAlign FileHandle/Buffer to avoid pointless memcpys
                long pixels1 = this.pixels;
                this.pixels=MemoryUtil.nmemAlignedAlloc(4096, this.size);
                MemoryUtil.memCopy(pixels1, this.pixels, this.size);
                MemoryUtil.nmemFree(pixels1);
            }
            buffer = MemoryUtil.memByteBuffer(this.pixels, (int)this.size);
        }
    }

    /**
     * @author
     */
    @Overwrite
    private void _upload(int level, int xOffset, int yOffset, int unpackSkipPixels, int unpackSkipRows, int widthIn, int heightIn, boolean blur, boolean clamp, boolean mipmap, boolean autoClose) {
        RenderSystem.assertOnRenderThreadOrInit();

        VTextureSelector.uploadSubTexture(level, widthIn, heightIn, xOffset, yOffset, unpackSkipRows, unpackSkipPixels, this.getWidth(), this.buffer);

        if (autoClose) {
            this.close();
        }
    }

    /**
     * @author
     */
    @Overwrite
    public void downloadTexture(int level, boolean removeAlpha) {
        RenderSystem.assertOnRenderThread();

        ImageUtil.downloadTexture(VTextureSelector.getBoundTexture(0), this.pixels);

        if (removeAlpha && this.format.hasAlpha()) {
            if (this.format != NativeImage.Format.RGBA) {
                throw new IllegalArgumentException(String.format(Locale.ROOT, "getPixelRGBA only works on RGBA images; have %s", this.format));
            }

            for (long l = 0; l < this.width * this.height * 4L; l+=4) {
                int v =  MemoryUtil.memGetInt(this.pixels + l);

                //TODO
                if(Vulkan.getSwapChain().isBGRAformat)
                    v = ColorUtil.BGRAtoRGBA(v);

                v = v | 255 << this.format.alphaOffset();
                MemoryUtil.memPutInt(this.pixels + l, v);
            }
        }

    }

}
