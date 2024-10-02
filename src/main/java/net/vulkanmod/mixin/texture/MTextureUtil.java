package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;

@Mixin(TextureUtil.class)
public class MTextureUtil {

    /**
     * @author
     */
    @Overwrite(remap = false)
    public static int generateTextureId() {
        RenderSystem.assertOnRenderThreadOrInit();
        return GlTexture.genTextureId();
    }

    /**
     * @author
     */
    @Overwrite(remap = false)
    public static void prepareImage(NativeImage.InternalGlFormat internalGlFormat, int id, int mipLevels, int width, int height) {
        RenderSystem.assertOnRenderThreadOrInit();
        GlTexture.bindTexture(id);
        GlTexture glTexture = GlTexture.getBoundTexture();
        VulkanImage image = glTexture.getVulkanImage();

        if(image == null || image.mipLevels != mipLevels || image.width != width || image.height != height) {
            if(image != null)
                image.free();

            image = new VulkanImage.Builder(width, height)
                    .setLinearFiltering(false)
                    .setClamp(false)
                    .createVulkanImage();

            glTexture.setVulkanImage(image);
            VTextureSelector.bindTexture(image);
        }
    }
}
