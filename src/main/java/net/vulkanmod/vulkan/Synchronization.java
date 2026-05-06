package net.vulkanmod.vulkan;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.vulkan.VK10.*;

/***
 * Synchronization utility to sync in frame ops that need to be completed before executing main cmd buffer.
 */
public class Synchronization {

    public static final Synchronization INSTANCE = new Synchronization();

    private final ObjectArrayList<CommandPool.CommandBuffer> semaphoreCbs = new ObjectArrayList<>();


    public synchronized void addCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
        this.semaphoreCbs.add(commandBuffer);
    }

    public void recycleCmdBuffers() {
        this.semaphoreCbs.forEach(CommandPool.CommandBuffer::reset);
        this.semaphoreCbs.clear();
    }

    public void scheduleCbReset() {
        MemoryManager.getInstance().addFrameOp(
                this::recycleCmdBuffers
        );
    }
}
