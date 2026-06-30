package net.vulkanmod.vulkan.queue;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static net.vulkanmod.vulkan.queue.QueueFamilyIndices.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public enum Queue {

    graphicsQueue(graphicsFamily.queueFamily, true),
    transferQueue(transferFamily.queueFamily, true),
    computeQueue(computeFamily.queueFamily, true),
    presentQueue(presentFamily.queueFamily, false);

    private static final VkDevice device = DeviceManager.vkDevice;
    private final VkQueue vkQueue;
    private final long queueSemaphore, pSubmitValue;

    private final CommandPool commandPool;
    private final VkSemaphoreWaitInfo vkSemaphoreWaitInfo;
    private long submitFence;

    public synchronized CommandPool.CommandBuffer beginCommands() {
        try (MemoryStack stack = stackPush()) {
            CommandPool.CommandBuffer commandBuffer = this.commandPool.getCommandBuffer(stack);
            commandBuffer.begin(stack);

            return commandBuffer;
        }
    }

    Queue(int familyIndex, boolean initCommandPool) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(DeviceManager.vkDevice, familyIndex, 0, pQueue);
            this.vkQueue = new VkQueue(pQueue.get(0), DeviceManager.vkDevice);

            this.commandPool = initCommandPool ? new CommandPool(familyIndex) : null;

            this.queueSemaphore = initCommandPool ? getQueueSemaphore(stack) : VK_NULL_HANDLE;

            if (initCommandPool) {
                LongBuffer pSubmitValue = MemoryUtil.memAllocLong(1);
                LongBuffer pSemaphores = MemoryUtil.memAllocLong(1);

                vkSemaphoreWaitInfo = VkSemaphoreWaitInfo.calloc()
                        .sType$Default()
                        .semaphoreCount(1)
                        .pSemaphores(pSemaphores.put(0, this.queueSemaphore))
                        .pValues(pSubmitValue);
                this.pSubmitValue = MemoryUtil.memAddress(pSubmitValue);
            } else {
                vkSemaphoreWaitInfo = null;
                this.pSubmitValue = 0L;
            }
        }
    }

    private long getQueueSemaphore(MemoryStack stack) {
        VkSemaphoreTypeCreateInfo semaphoreTypeCreateInfo = VkSemaphoreTypeCreateInfo.calloc(stack)
                .sType$Default()
                .semaphoreType(VK12.VK_SEMAPHORE_TYPE_TIMELINE);

        VkSemaphoreCreateInfo semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack)
                .sType$Default()
                .pNext(semaphoreTypeCreateInfo);

        LongBuffer pPointer = stack.mallocLong(1);

        VK12.vkCreateSemaphore(Vulkan.getVkDevice(), semaphoreCreateInfo, null, pPointer);
        return pPointer.get(0);
    }
    /**
     * Enqueues current command buffer to this queue
     * Defaults to Stage None to minimize submit/ sync overhead
     * */
    public void addPending(CommandPool.CommandBuffer commandBuffer) {
        commandBuffer.enqueue(VK13.VK_PIPELINE_STAGE_2_NONE);
    }
    /**
     * Enqueues current command buffer to this queue
     * @param waitStage reorder/barrier commands if necessary (allows optimizing out fences)
     * */
    public void addPending(CommandPool.CommandBuffer commandBuffer, long waitStage) {
        commandBuffer.enqueue(waitStage);
    }

    /** Execute this specific command buffer immediately: ignoring other pending cmds  */
    public void executeImmediate(CommandPool.CommandBuffer commandBuffer, long waitStage) {
        commandBuffer.enqueue(waitStage);
        this.commandPool.executePending(this,true);
    }

    /** Execute this specific command buffer immediately: ignoring other pending cmds */
    public void executeImmediate(CommandPool.CommandBuffer commandBuffer) {
        commandBuffer.enqueue(VK13.VK_PIPELINE_STAGE_2_NONE);
        this.commandPool.executePending(this, true);
    }

    /** Executes all currently pending command buffers */
    public void executePendingCmds() {
        this.commandPool.executePending(this, false);
    }


    /* Transfer ops */

    public long copyBufferCmd(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try (MemoryStack stack = stackPush()) {

            CommandPool.CommandBuffer commandBuffer = beginCommands();

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            copyRegion.size(size);
            copyRegion.srcOffset(srcOffset);
            copyRegion.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);

            this.addPending(commandBuffer);

            return commandBuffer.fence;
        }
    }

    public void uploadBufferImmediate(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try (MemoryStack stack = stackPush()) {
            CommandPool.CommandBuffer commandBuffer = this.beginCommands();

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            copyRegion.size(size);
            copyRegion.srcOffset(srcOffset);
            copyRegion.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);

            this.executeImmediate(commandBuffer);
            commandBuffer.wait(this);
            commandBuffer.reset();
        }
    }

    public void uploadBufferCmd(VkCommandBuffer commandBuffer, long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try (MemoryStack stack = stackPush()) {

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            copyRegion.size(size);
            copyRegion.srcOffset(srcOffset);
            copyRegion.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion);
        }
    }

    public VkQueue vkQueue() {
        return this.vkQueue;
    }

    public void cleanUp() {
        if (commandPool != null) {
            commandPool.cleanUp();
            vkDestroySemaphore(Vulkan.getVkDevice(), this.queueSemaphore, null);
            MemoryUtil.memFree(vkSemaphoreWaitInfo.pSemaphores());
            MemoryUtil.memFree(vkSemaphoreWaitInfo.pValues());
            vkSemaphoreWaitInfo.free();
        }
    }


    public void resetAll() {
        this.commandPool.resetAll();
    }

    public void waitIdle() {
        vkQueueWaitIdle(vkQueue);
    }

    public CommandPool getCommandPool() {
        return commandPool;
    }

    /**
     * Increments the queue fence's value (used when executing submits)
     */
    public long submitFenceAdd() {
        return ++submitFence;
    }

    public long submitFence() {
        return submitFence;
    }

    public long getQueueSemaphore() {
        return this.queueSemaphore;
    }

    /**
     * Functionally identical to Synchronisation.waitFences(), but for a specific queue
     */

    public void waitSubmits() {
        waitSubmits(this.submitFence);
    }

    /**
     * Functionally identical to Synchronisation.waitFences(), but for a specific queue
     * @param submitFence specific submit/command buffer to wait on (Optional arg to optimize out for all submits (if only a specific submit wait is needed)
     */
    public void waitSubmits(long submitFence) {
        VUtil.UNSAFE.putLong(pSubmitValue, submitFence);
        VK12.vkWaitSemaphores(device, vkSemaphoreWaitInfo, VUtil.UINT64_MAX);
    }


    /** Used for debugging infos */

    @Override
    public String toString() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            var pPtr = stack.nmalloc(Long.BYTES);
            VK12.nvkGetSemaphoreCounterValue(device, this.queueSemaphore, pPtr);
            return commandPool.toString() + ": submitFence: " + VUtil.UNSAFE.getLong(pPtr);
        }
    }
}
