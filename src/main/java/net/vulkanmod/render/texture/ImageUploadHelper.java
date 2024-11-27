package net.vulkanmod.render.texture;

import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;

public class ImageUploadHelper {

    public static final ImageUploadHelper INSTANCE = new ImageUploadHelper();

    private CommandPool.CommandBuffer currentCmdBuffer;

    public void submitCommands() {
        if (this.currentCmdBuffer == null) {
            return;
        }

        Queue.GraphicsQueue.submitCommands(this.currentCmdBuffer);
        Synchronization.INSTANCE.addCommandBuffer(this.currentCmdBuffer);

        this.currentCmdBuffer = null;
    }

    public CommandPool.CommandBuffer getOrStartCommandBuffer() {
        if (this.currentCmdBuffer == null) {
            this.currentCmdBuffer = Queue.GraphicsQueue.beginCommands();
        }

        return this.currentCmdBuffer;
    }

    public CommandPool.CommandBuffer getCommandBuffer() {
        return this.currentCmdBuffer;
    }
}
