package net.vulkanmod.vulkan;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class Synchronization {
    private static final int ALLOCATION_SIZE = 50;

    public static final Synchronization INSTANCE = new Synchronization(ALLOCATION_SIZE);

    private final LongBuffer submitIds;
    private int idx = 0;

    private final ObjectArrayList<CommandPool.CommandBuffer> commandBuffers = new ObjectArrayList<>();

    Synchronization(int allocSize) {
        this.submitIds = MemoryUtil.memAllocLong(allocSize);
    }
    //TODO: Too Many cmdBuffers generated due to no ALLOCATION_SIZE waits
    public synchronized void addCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
//        this.addSubmitId(commandBuffer.getSubmitId());
        this.commandBuffers.add(commandBuffer);
    }

    public synchronized void addSubmitId(long submitId) {
        if (idx == ALLOCATION_SIZE)
            recycleCmdBuffers();

        submitIds.put(idx, submitId);
        idx++;
    }

    public synchronized void recycleCmdBuffers() {
//        if (idx == 0)
//            return;

//        VkDevice device = Vulkan.getVkDevice();
//
//        fences.limit(idx);

//        vkWaitForFences(device, fences, true, VUtil.UINT64_MAX);

        this.commandBuffers.forEach(CommandPool.CommandBuffer::reset);
        this.commandBuffers.clear();

        submitIds.limit(ALLOCATION_SIZE);
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
