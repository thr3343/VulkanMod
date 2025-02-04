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

    public static final Synchronization INSTANCE = new Synchronization(ALLOCATION_SIZE);

    private static final long tmSemaphore = DeviceManager.getTransferQueue().getTmSemaphore();
    private static final long tmSemaphore2 = DeviceManager.getGraphicsQueue().getTmSemaphore();

    private final EnumMap<Queue.Family, LongBuffer> fences = new EnumMap<>(Queue.Family.class);
    private int idx = 0;

    private final ObjectArrayList<CommandPool.CommandBuffer> commandBuffers = new ObjectArrayList<>();

    Synchronization(int allocSize) {
        this.fences.put(Queue.Family.Graphics, MemoryUtil.memAllocLong(allocSize));
        this.fences.put(Queue.Family.Transfer, MemoryUtil.memAllocLong(allocSize));
    }

    public synchronized void addCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
        this.addFence(commandBuffer.submitId, commandBuffer.commandPool.queueType);
        this.commandBuffers.add(commandBuffer);
    }
    public synchronized void addFence(long fence, Queue.Family queueType) {
        if (idx == ALLOCATION_SIZE)
            waitFences();

        fences.get(queueType).put(fence);
        idx++;
    }

    public synchronized void waitFences() {
        if (idx == 0)
            return;

        VkDevice device = Vulkan.getVkDevice();

        try (MemoryStack stack = MemoryStack.stackPush()) {

            for (var submitIds : fences.entrySet()) {

                LongBuffer submitIdSet = submitIds.getValue();

                long semaphore = submitIds.getKey() == Queue.Family.Transfer ? tmSemaphore : tmSemaphore2;
                //TODO: Merge Multiple Semaphore Waits
                VkSemaphoreWaitInfo vkSemaphoreWaitInfo = VkSemaphoreWaitInfo.calloc(stack)
                        .sType$Default()
                        .semaphoreCount(1)
                        .pSemaphores(stack.longs(semaphore))
                        .pValues(submitIdSet);

                VK12.vkWaitSemaphores(device, vkSemaphoreWaitInfo, VUtil.UINT64_MAX);


                submitIdSet.rewind();
            }
        }

        this.commandBuffers.forEach(CommandPool.CommandBuffer::reset);
        this.commandBuffers.clear();


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
