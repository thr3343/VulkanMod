package net.vulkanmod.render.texture;

import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;

public class ImageUploadHelper {

    public static final ImageUploadHelper INSTANCE = new ImageUploadHelper();
    private long submitId;
    final Queue queue;
    private CommandPool.CommandBuffer currentCmdBuffer;

    public ImageUploadHelper() {
        queue = DeviceManager.getGraphicsQueue();
    }

    public long submitCommands() {
        if (this.currentCmdBuffer == null) {
            return this.submitId;
        }

        queue.submitCommands(this.currentCmdBuffer);
        Synchronization.INSTANCE.addCommandBuffer(this.currentCmdBuffer);
        this.submitId = Math.min(submitId, this.currentCmdBuffer.submitId);
        this.currentCmdBuffer = null;
        return this.submitId;
    }

    public CommandPool.CommandBuffer getOrStartCommandBuffer() {
        if (this.currentCmdBuffer == null) {
            this.submitId = Long.MAX_VALUE;
            this.currentCmdBuffer = this.queue.beginCommands();
        }

        return this.currentCmdBuffer;
    }

    public CommandPool.CommandBuffer getCommandBuffer() {
        return this.currentCmdBuffer;
    }
}
