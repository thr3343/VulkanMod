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

    private final LongBuffer fences;
    private static int idx = 0;

    private final ObjectArrayList<CommandPool.CommandBuffer> commandBuffers = new ObjectArrayList<>();

    Synchronization(int allocSize) {
        this.fences = MemoryUtil.memAllocLong(allocSize);
    }

    public synchronized void addCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
        this.addFence(commandBuffer.getFence());
        this.commandBuffers.add(commandBuffer);
    }

    public synchronized void addFence(long fence) {
        if(idx >= ALLOCATION_SIZE)
            waitFences();

        fences.put(idx, fence);
        idx++;
    }

    public synchronized void waitFences() {

        if(idx == 0) return;

        VkDevice device = Vulkan.getDevice();

        fences.limit(idx);

        vkWaitForFences(device, fences, true, VUtil.UINT64_MAX);
        vkResetFences(device, fences);

        this.commandBuffers.forEach(CommandPool.CommandBuffer::reset);
        this.commandBuffers.clear();

        fences.limit(ALLOCATION_SIZE);
        idx = 0;
    }

    public static void waitFence(CommandPool.CommandBuffer commandBuffer) {
        if(idx == 0) return;
        VkDevice device = Vulkan.getDevice();

        final long fence = commandBuffer.getFence();
        vkWaitForFences(device, fence, true, VUtil.UINT64_MAX);
        vkResetFences(device, fence);
        commandBuffer.reset();
    }

    public static boolean checkFenceStatus(long fence) {
        VkDevice device = Vulkan.getDevice();
        return vkGetFenceStatus(device, fence) == VK_SUCCESS;
    }

}
