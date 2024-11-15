package net.vulkanmod.mixin.texture;

import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.Tickable;
import net.vulkanmod.render.texture.SpriteUtil;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.Queue;
import org.spongepowered.asm.mixin.*;

import java.util.Set;

@Mixin(TextureManager.class)
public abstract class MTextureManager {
    @Unique private static final Queue defaultTransferQueue = Vulkan.getDevice().isNvidia() ? DeviceManager.getTransferQueue() : DeviceManager.getGraphicsQueue();
    @Shadow @Final private Set<Tickable> tickableTextures;

    /**
     * @author
     */
    @Overwrite
    public void tick() {
        if (Renderer.skipRendering)
            return;

        //Debug D
        if (SpriteUtil.shouldUpload())
            defaultTransferQueue.startRecording();
        for (Tickable tickable : this.tickableTextures) {
            tickable.tick();
        }
        if (SpriteUtil.shouldUpload()) {
            SpriteUtil.transitionLayouts(defaultTransferQueue.getCommandBuffer().getHandle());
            defaultTransferQueue.endRecordingAndSubmit();
        }
    }
}
