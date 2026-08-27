package net.vulkanmod.render.texture;

import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import org.lwjgl.vulkan.VK13;

public class ImageUploadHelper {

    public static final ImageUploadHelper INSTANCE = new ImageUploadHelper();

    final Queue queue;
    private CommandPool.CommandBuffer currentCmdBuffer;

    public ImageUploadHelper() {
        queue = DeviceManager.getGraphicsQueue();
    }

    public void submitCommands() {
        this.submitCommands(true);
    }

    public void submitCommands(boolean wait) {
        if (this.currentCmdBuffer == null) {
            return;
        }

        SpriteUpdateUtil.transitionLayouts();

        queue.submitCommands(this.currentCmdBuffer, wait ? VK13.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT | VK13.VK_PIPELINE_STAGE_2_COPY_BIT : VK13.VK_PIPELINE_STAGE_2_NONE); // Wait for animation passes + texture writes

        this.currentCmdBuffer = null;
    }

    public CommandPool.CommandBuffer getOrStartCommandBuffer() {
        if (this.currentCmdBuffer == null) {
            this.currentCmdBuffer = this.queue.beginCommands();
        }

        return this.currentCmdBuffer;
    }

    public CommandPool.CommandBuffer getCommandBuffer() {
        return this.currentCmdBuffer;
    }
}
