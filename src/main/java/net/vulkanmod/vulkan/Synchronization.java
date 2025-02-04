package net.vulkanmod.vulkan;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkSemaphoreWaitInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memPutAddress;
import static org.lwjgl.vulkan.VK10.*;

public class Synchronization {
    private static final int ALLOCATION_SIZE = 50;

    public static final Synchronization INSTANCE = new Synchronization();

    private static final long pData = MemoryUtil.nmemAlignedAlloc(8, 32 + VkSemaphoreWaitInfo.SIZEOF);
    private static final long pData2 = pData +16;

    private static final VkSemaphoreWaitInfo vkSemaphoreWaitInfo;
    private int idx;

    static  {
        VUtil.UNSAFE.putLong(pData, DeviceManager.getGraphicsQueue().getTmSemaphore());
        VUtil.UNSAFE.putLong(pData+8, DeviceManager.getTransferQueue().getTmSemaphore());

        vkSemaphoreWaitInfo = VkSemaphoreWaitInfo.create(pData + 32)
                .sType$Default()
                .semaphoreCount(2);
        memPutAddress(vkSemaphoreWaitInfo.address() + VkSemaphoreWaitInfo.PSEMAPHORES, pData);
        memPutAddress(vkSemaphoreWaitInfo.address() + VkSemaphoreWaitInfo.PVALUES, pData+16);
    }

    private final ObjectArrayList<CommandPool.CommandBuffer> commandBuffers = new ObjectArrayList<>();

    public synchronized void addCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
        this.addFence(commandBuffer.submitId, commandBuffer.commandPool.queueType);
        this.commandBuffers.add(commandBuffer);
    }
    public synchronized void addFence(long fence, Queue.Family queueType) {
        if (idx == ALLOCATION_SIZE)
            waitFences();
        VUtil.UNSAFE.putLong((queueType.ordinal() * Long.BYTES) + pData2, fence);
        idx++;
    }

    public synchronized void waitFences() {
        if (idx == 0)
            return;

        VkDevice device = Vulkan.getVkDevice();

        VK12.vkWaitSemaphores(device, vkSemaphoreWaitInfo, VUtil.UINT64_MAX);

        this.commandBuffers.forEach(CommandPool.CommandBuffer::reset);
        this.commandBuffers.clear();

//        MemoryUtil.memSet(pData, 0, Long.BYTES * 2);
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
