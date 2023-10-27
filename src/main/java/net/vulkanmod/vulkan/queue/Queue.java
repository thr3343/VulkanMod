package net.vulkanmod.vulkan.queue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK10.*;

public enum Queue {
    GraphicsQueue(QueueFamilyIndices.graphicsFamily, true),
    TransferQueue(QueueFamilyIndices.transferFamily, true),
    ComputeQueue(QueueFamilyIndices.computeFamily, false),
    PresentQueue(QueueFamilyIndices.presentFamily, false);
    private CommandPool.CommandBuffer currentCmdBuffer;
    private final CommandPool commandPool;


    private final VkQueue queue;

    public CommandPool.CommandBuffer beginCommands() {
        return this.commandPool.beginCommands();
    }

    Queue(int familyIndex) {
        this(familyIndex, true);
    }

    Queue(int familyIndex, boolean initCommandPool) {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(Device.device, familyIndex, 0, pQueue);
            this.queue = new VkQueue(pQueue.get(0), Device.device);

            this.commandPool = initCommandPool ? new CommandPool(familyIndex) : null;
        }
    }

    public synchronized long submitCommands(CommandPool.CommandBuffer commandBuffer) {
        return this.commandPool.submitCommands(commandBuffer, queue);
    }

    public VkQueue queue() { return this.queue; }

    public void cleanUp() {
        if(commandPool != null)
            commandPool.cleanUp();
    }

    public void waitIdle() {
        vkQueueWaitIdle(queue);
    }

    public long copyBufferCmd(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try(MemoryStack stack = stackPush()) {

            CommandPool.CommandBuffer commandBuffer = beginCommands();

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.malloc(1, stack);
            copyRegion.size(size);
            copyRegion.srcOffset(srcOffset);
            copyRegion.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);

            this.submitCommands(commandBuffer);
            Synchronization.INSTANCE.addCommandBuffer(commandBuffer);

            return commandBuffer.fence;
        }
    }

    public void uploadBufferImmediate(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try(MemoryStack stack = stackPush()) {
            CommandPool.CommandBuffer commandBuffer = this.beginCommands();

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.malloc(1, stack);
            copyRegion.size(size);
            copyRegion.srcOffset(srcOffset);
            copyRegion.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);

            this.submitCommands(commandBuffer);
            vkWaitForFences(Vulkan.getDevice(), commandBuffer.fence, true, VUtil.UINT64_MAX);
            commandBuffer.reset();
        }
    }

    public void uploadBufferCmd(CommandPool.CommandBuffer commandBuffer, long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try(MemoryStack stack = stackPush()) {

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.malloc(1, stack);
            copyRegion.size(size);
            copyRegion.srcOffset(srcOffset);
            copyRegion.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);
        }
    }

    public void startRecording() {
        currentCmdBuffer = beginCommands();
    }

    public void endRecordingAndSubmit() {
        long fence = submitCommands(currentCmdBuffer);
        Synchronization.INSTANCE.addCommandBuffer(currentCmdBuffer);

        currentCmdBuffer = null;
    }

    public CommandPool.CommandBuffer getCommandBuffer() {
        if (currentCmdBuffer != null) {
            return currentCmdBuffer;
        } else {
            return beginCommands();
        }
    }

    public long endIfNeeded(CommandPool.CommandBuffer commandBuffer) {
        if (currentCmdBuffer != null) {
            return VK_NULL_HANDLE;
        } else {
            return submitCommands(commandBuffer);
        }
    }

    public void uploadSuperSet(CommandPool.CommandBuffer commandBuffer, VkBufferCopy.Buffer copyRegions, long src, long bufferPointerSuperSet) {
        vkCmdCopyBuffer(commandBuffer.getHandle(), src, bufferPointerSuperSet, copyRegions);
    }

    public synchronized long submitCommands2(ObjectArrayList<CommandPool.CommandBuffer> handle, long fence1) {
        return this.commandPool.submitCommands2(queue, handle, fence1);
    }

}
