package net.vulkanmod.vulkan.queue;

import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.system.Checks.check;
import static org.lwjgl.system.JNI.callPPI;
import static org.lwjgl.system.JNI.callPPJI;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public enum Queue {


    GraphicsQueue(QueueFamilyIndices.graphicsFamily),
    TransferQueue(QueueFamilyIndices.transferFamily),
    PresentQueue(QueueFamilyIndices.presentFamily);

    public final long Queue; //CPU optimization: Use VkQueue handle (8 Bytes) instead of full VkQueue object (>3KB) to be more CacheLine friendly
    public final CommandPool commandPool;
    private static final long vkQueueSubmit = Vulkan.getFunctionPointers().vkQueueSubmit;
    private static final long vkQueuePresentKHR = Vulkan.getFunctionPointers().vkQueuePresentKHR;
    private CommandPool.CommandBuffer currentCmdBuffer;

    Queue(int computeFamily) {

        commandPool = new CommandPool(computeFamily);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pQueue = stack.mallocPointer(1);
            JNI.callPPV(Vulkan.getDevicePtr(), computeFamily, 0, pQueue.address(), Vulkan.getFunctionPointers().vkGetDeviceQueue);
            this.Queue = pQueue.get(0);
        }

    }

    public CommandPool.CommandBuffer beginCommands() {

        return commandPool.beginCommands();
    }

//    public abstract long submitCommands(CommandPool.CommandBuffer commandBuffer);

    public void cleanUp() {
        commandPool.cleanUp();
    }


    public long copyBufferCmd(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try (MemoryStack stack = stackPush()) {

            CommandPool.CommandBuffer commandBuffer = beginCommands();

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1, stack);
            copyRegion.size(size);
            copyRegion.srcOffset(srcOffset);
            copyRegion.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);

            submitCommands(commandBuffer);
            Synchronization.INSTANCE.addCommandBuffer(commandBuffer);

            return commandBuffer.fence;
        }
    }

    public void uploadBufferImmediate(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try (MemoryStack stack = stackPush()) {
            CommandPool.CommandBuffer commandBuffer = beginCommands();

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1, stack);
            copyRegion.size(size);
            copyRegion.srcOffset(srcOffset);
            copyRegion.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);

            submitCommands(commandBuffer);
            VK10.vkWaitForFences(Vulkan.getDevice(), commandBuffer.fence, true, -1);
            commandBuffer.reset();
        }
    }


    public void uploadBufferCmd(CommandPool.CommandBuffer commandBuffer, long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try (MemoryStack stack = stackPush()) {

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1, stack);
            copyRegion.size(size);
            copyRegion.srcOffset(srcOffset);
            copyRegion.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);
        }
    }


//    public abstract long submitCommands(CommandPool.CommandBuffer commandBuffer);


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

    public long submitCommands(CommandPool.CommandBuffer commandBuffer) {

        return commandPool.submitCommands(this, commandBuffer, this.Queue);
    }

    public void waitIdle() {
//        vkQueueWaitIdle(Vulkan.getTransferQueue());
    }

    public void uploadSuperSet(CommandPool.CommandBuffer commandBuffer, VkBufferCopy.Buffer copyRegions, long srcBuffer, long dstBuffer) {
        vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegions);

    }

    public int vkQueuePresentKHR(VkPresentInfoKHR presentInfo) {
        return callPPI(this.Queue, presentInfo.address(), vkQueuePresentKHR);
    }
    public int vkQueueSubmit(VkSubmitInfo submitInfo, long fence) {
       return callPPJI(this.Queue, 1, submitInfo.address(), fence, vkQueueSubmit);
    }
}
