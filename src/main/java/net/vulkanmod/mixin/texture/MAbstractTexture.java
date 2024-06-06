package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.interfaces.VAbstractTextureI;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AbstractTexture.class)
public abstract class MAbstractTexture implements VAbstractTextureI {
    @Shadow protected boolean blur;
    @Shadow protected boolean mipmap;
    @Shadow protected int id;

    @Shadow public abstract int getId();

    @Unique
    protected VulkanImage vulkanImage;

    /**
     * @author
     */
    @Overwrite
    public void bind() {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(this::bindTexture);
        } else {
            this.bindTexture();
        }
    }

    /**
     * @author
     */
    @Overwrite
    public void releaseId() {
        if(this.vulkanImage != null) {
            this.vulkanImage.free();
            this.vulkanImage = null;
        }
//        else
//            System.out.println("trying to free null image");

        TextureUtil.releaseTextureId(this.id);
    }

    public void setId(int i) {
        this.id = i;
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void setFilter(boolean blur, boolean mipmap) {

    }

    @Override
    public void bindTexture() {
        GlTexture.bindTexture(this.id);

        if (vulkanImage != null)
            VTextureSelector.bindTexture(vulkanImage);
    }

    public VulkanImage getVulkanImage() {
        if(vulkanImage != null)
            return vulkanImage;
        else {
            final GlTexture glTexture = GlTexture.getTexture(this.id);
            if(glTexture != null)
                return glTexture.getVulkanImage();
            else
                return ((VAbstractTextureI)MissingTextureAtlasSprite.getTexture()).getVulkanImage();
        }
    }

    public void setVulkanImage(VulkanImage image) {
        this.vulkanImage = image;

        if(this.id == -1)
            this.getId();
        GlTexture.setVulkanImage(this.id, this.vulkanImage);
    }
}
