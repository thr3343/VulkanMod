package net.vulkanmod.vulkan;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.vulkan.VK10.*;

public class Synchronization {

    public static final Synchronization INSTANCE = new Synchronization();

    private final ObjectArrayList<CommandPool.CommandBuffer> commandBuffers = new ObjectArrayList<>();


    public synchronized void addCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
        this.commandBuffers.add(commandBuffer);
    }

    public synchronized void recycleCmdBuffers() {
        this.commandBuffers.forEach(CommandPool.CommandBuffer::reset);
        this.commandBuffers.clear();
    }

    public static void waitFence(long fence) {
        VkDevice device = Vulkan.getVkDevice();

        vkWaitForFences(device, fence, true, VUtil.UINT64_MAX);
    }

    public static boolean checkFenceStatus(long fence) {
        VkDevice device = Vulkan.getVkDevice();
        return vkGetFenceStatus(device, fence) == VK_SUCCESS;
    }

}
