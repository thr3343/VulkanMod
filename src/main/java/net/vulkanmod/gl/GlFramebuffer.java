package net.vulkanmod.gl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.texture.ImageUtil;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static org.lwjgl.vulkan.VK11.VK_ATTACHMENT_LOAD_OP_LOAD;
import static org.lwjgl.vulkan.VK11.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

public class GlFramebuffer {
    private static int idCounter = 1;

    private static final Int2ReferenceOpenHashMap<GlFramebuffer> map = new Int2ReferenceOpenHashMap<>();
    private static GlFramebuffer boundFramebuffer;
    private static GlFramebuffer readFramebuffer;

    public static void resetBoundFramebuffer() {
        boundFramebuffer = null;
    }

    public static void beginRendering(GlFramebuffer glFramebuffer) {
        boolean begunRendering = glFramebuffer.beginRendering();

        if (begunRendering) {
            Framebuffer framebuffer = glFramebuffer.framebuffer;
            int viewWidth = framebuffer.getWidth();
            int viewHeight = framebuffer.getHeight();

            Renderer.setInvertedViewport(0, 0, viewWidth, viewHeight);
            Renderer.setScissor(0, 0, viewWidth, viewHeight);

            // TODO: invert cull instead of disabling
            VRenderSystem.disableCull();
        }

        boundFramebuffer = glFramebuffer;
    }

    public static int genFramebufferId() {
        int id = idCounter;
        map.put(id, new GlFramebuffer(id));
        idCounter++;
        return id;
    }

    public static void bindFramebuffer(int target, int id) {

        if (id == 0) {
            Renderer.getInstance().endRenderPass();

            if (Renderer.isRecording()) {
                RenderTarget renderTarget = Minecraft.getInstance().getMainRenderTarget();
                renderTarget.bindWrite(true);
            }

            boundFramebuffer = null;
            return;
        }

        GlFramebuffer glFramebuffer = map.get(id);

        if (glFramebuffer == null)
            throw new NullPointerException("No Framebuffer with ID: %d ".formatted(id));

        switch (target) {
            case GL30.GL_DRAW_FRAMEBUFFER , GL30.GL_FRAMEBUFFER -> {
                if (glFramebuffer.framebuffer != null) {
                    beginRendering(glFramebuffer);
                }

                boundFramebuffer = glFramebuffer;
            }
            case GL30.GL_READ_FRAMEBUFFER -> {
                readFramebuffer = glFramebuffer;
            }
        }

    }

    public static void deleteFramebuffer(int id) {
        if (id == 0) {
            return;
        }

        boundFramebuffer = map.remove(id);

        if (boundFramebuffer == null)
            throw new NullPointerException("bound framebuffer is null");

        boundFramebuffer.cleanUp(true);
        boundFramebuffer = null;
    }

    public static void framebufferTexture2D(int target, int attachment, int texTarget, int texture, int level) {
        if (attachment != GL30.GL_COLOR_ATTACHMENT0 && attachment != GL30.GL_DEPTH_ATTACHMENT) {
            throw new UnsupportedOperationException();
        }
        if (texTarget != GL11.GL_TEXTURE_2D) {
            throw new UnsupportedOperationException();
        }
        if (level != 0) {
            throw new UnsupportedOperationException();
        }

        boundFramebuffer.setAttachmentTexture(attachment, texture);
    }

    public static void framebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        if (boundFramebuffer == null)
            return;

        boundFramebuffer.setAttachmentRenderbuffer(attachment, renderbuffer);
    }

    public static void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        // TODO: add missing parameters
        ImageUtil.blitFramebuffer(boundFramebuffer.colorAttachment, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1);
    }

    public static int glCheckFramebufferStatus(int target) {
        //TODO
        return GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    public static GlFramebuffer getBoundFramebuffer() {
        return boundFramebuffer;
    }

    public static GlFramebuffer getFramebuffer(int id) {
        return map.get(id);
    }

    private final int id;
    Framebuffer framebuffer;
    RenderPass renderPass;

    VulkanImage colorAttachment;
    VulkanImage depthAttachment;

    GlFramebuffer(int i) {
        this.id = i;
    }

    boolean beginRendering() {
        return Renderer.getInstance().beginRendering(this.renderPass, this.framebuffer);
    }

    void setAttachmentTexture(int attachment, int texture) {
        GlTexture glTexture = GlTexture.getTexture(texture);

        if (glTexture == null)
            throw new NullPointerException(String.format("Texture %d is null", texture));

        if (glTexture.vulkanImage == null)
            return;

        switch (attachment) {
            case (GL30.GL_COLOR_ATTACHMENT0) -> this.setColorAttachment(glTexture.getVulkanImage());
            case (GL30.GL_DEPTH_ATTACHMENT) -> this.setDepthAttachment(glTexture.getVulkanImage());

            default -> throw new IllegalStateException("Unexpected value: " + attachment);
        }
    }

    void setAttachmentRenderbuffer(int attachment, int texture) {
        GlRenderbuffer renderbuffer = GlRenderbuffer.getRenderbuffer(texture);

        if (renderbuffer == null)
            throw new NullPointerException(String.format("Texture %d is null", texture));

        if (renderbuffer.vulkanImage == null)
            return;

        switch (attachment) {
            case (GL30.GL_COLOR_ATTACHMENT0) -> this.setColorAttachment(renderbuffer.getVulkanImage());
            case (GL30.GL_DEPTH_ATTACHMENT) -> this.setDepthAttachment(renderbuffer.getVulkanImage());

            default -> throw new IllegalStateException("Unexpected value: " + attachment);
        }
    }

    void setColorAttachment(VulkanImage image) {
        this.colorAttachment = image;
        createAndBind();
    }

    void setDepthAttachment(VulkanImage image) {
        //TODO check if texture is in depth format
        this.depthAttachment = image;
        createAndBind();
    }

    void createAndBind() {
        // Cannot create without color attachment
        if (this.colorAttachment == null)
            return;

        if (this.framebuffer != null) {
            this.cleanUp(false);
        }

        boolean hasDepthImage = this.depthAttachment != null;
        VulkanImage depthImage = this.depthAttachment;

        this.framebuffer = Framebuffer.builder(this.colorAttachment, depthImage).build();
        RenderPass.Builder builder = RenderPass.builder(this.framebuffer);

        builder.getColorAttachmentInfo()
                .setLoadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                .setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        if (hasDepthImage)
            builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_LOAD_OP_LOAD);

        this.renderPass = builder.build();

        GlFramebuffer.beginRendering(this);
    }

    public Framebuffer getFramebuffer() {
        return framebuffer;
    }

    public RenderPass getRenderPass() {
        return renderPass;
    }

    void cleanUp(boolean freeAttachments) {
        this.framebuffer.cleanUp(freeAttachments);
        this.renderPass.cleanUp();

        this.framebuffer = null;
        this.renderPass = null;
    }
}
