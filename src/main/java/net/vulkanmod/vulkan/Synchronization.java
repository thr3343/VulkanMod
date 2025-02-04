package net.vulkanmod.vulkan;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkSemaphoreWaitInfo;

import java.nio.LongBuffer;
import java.util.EnumMap;
import java.util.Objects;

import static org.lwjgl.vulkan.VK10.*;

public class Synchronization {
    private static final int ALLOCATION_SIZE = 50;

    public static final Synchronization INSTANCE = new Synchronization();

    private static final long tmSemaphore = DeviceManager.getTransferQueue().getTmSemaphore();
    private static final long tmSemaphore2 = DeviceManager.getGraphicsQueue().getTmSemaphore();

    private final LongBuffer fences = MemoryUtil.memAllocLong(2);
    private int idx;

    private final ObjectArrayList<CommandPool.CommandBuffer> commandBuffers = new ObjectArrayList<>();

    public synchronized void addCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
        this.addFence(commandBuffer.submitId, commandBuffer.commandPool.queueType);
        this.commandBuffers.add(commandBuffer);
    }
    public synchronized void addFence(long fence, Queue.Family queueType) {
        if (idx == ALLOCATION_SIZE)
            waitFences();

        fences.put(queueType.ordinal(), fence);
        idx++;
    }

    public synchronized void waitFences() {
        if (idx == 0)
            return;

        VkDevice device = Vulkan.getVkDevice();

        try (MemoryStack stack = MemoryStack.stackPush()) {

            VkSemaphoreWaitInfo vkSemaphoreWaitInfo = VkSemaphoreWaitInfo.calloc(stack)
                    .sType$Default()
                    .semaphoreCount(2)
                    .pSemaphores(stack.longs(tmSemaphore2, tmSemaphore))
                    .pValues(fences);

            VK12.vkWaitSemaphores(device, vkSemaphoreWaitInfo, VUtil.UINT64_MAX);

        }

        this.commandBuffers.forEach(CommandPool.CommandBuffer::reset);
        this.commandBuffers.clear();

        fences.put(0).put(0).rewind();
        idx = 0;
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
