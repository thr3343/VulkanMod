package net.vulkanmod.vulkan.queue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSynchronization2.vkQueueSubmit2KHR;
import static org.lwjgl.vulkan.VK10.*;

public class CommandPool {

    private static final boolean sync2 = DeviceManager.checkExt(KHRSynchronization2.VK_KHR_SYNCHRONIZATION_2_EXTENSION_NAME);

    private final long id;

    private final List<CommandBuffer> submittedCmdBuffers = new ObjectArrayList<>();
    private final java.util.Queue<CommandBuffer> availableCmdBuffers = new ArrayDeque<>();

    CommandPool(int queueFamilyIndex) {
        try (MemoryStack stack = stackPush()) {

            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            poolInfo.queueFamilyIndex(queueFamilyIndex);
            poolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);

            if (vkCreateCommandPool(Vulkan.getVkDevice(), poolInfo, null, pCommandPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool");
            }

            this.id = pCommandPool.get(0);
        }
    }

    public CommandBuffer getCommandBuffer() {
        try (MemoryStack stack = stackPush()) {
            return getCommandBuffer(stack);
        }
    }

    public CommandBuffer getCommandBuffer(MemoryStack stack) {
        if (availableCmdBuffers.isEmpty()) {
            allocateCommandBuffers(stack);
        }

        CommandBuffer commandBuffer = availableCmdBuffers.poll();
        return commandBuffer;
    }

    private void allocateCommandBuffers(MemoryStack stack) {
        final int size = 10;

        VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
        allocInfo.sType$Default();
        allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
        allocInfo.commandPool(id);
        allocInfo.commandBufferCount(size);

        PointerBuffer pCommandBuffer = stack.mallocPointer(size);
        vkAllocateCommandBuffers(Vulkan.getVkDevice(), allocInfo, pCommandBuffer);

        for (int i = 0; i < size; ++i) {
            VkCommandBuffer vkCommandBuffer = new VkCommandBuffer(pCommandBuffer.get(i), Vulkan.getVkDevice());
            CommandBuffer commandBuffer = new CommandBuffer(this, vkCommandBuffer);
            availableCmdBuffers.add(commandBuffer);
        }
    }

    public void addToAvailable(CommandBuffer commandBuffer) {
        this.availableCmdBuffers.add(commandBuffer);
    }

    public void addToSubmitted(CommandBuffer commandBuffer) {
        this.submittedCmdBuffers.add(commandBuffer);
    }

    public void resetAll() {
        this.submittedCmdBuffers.forEach(CommandBuffer::reset);
        this.submittedCmdBuffers.clear();
    }

    public void cleanUp() {
        vkResetCommandPool(Vulkan.getVkDevice(), id, VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT);
        vkDestroyCommandPool(Vulkan.getVkDevice(), id, null);
    }

    public long getId() {
        return id;
    }

    public static class CommandBuffer {
        public final CommandPool commandPool;
        public final VkCommandBuffer handle;
        public long fence; // Same functionality as fence handles

        boolean submitted;
        boolean recording;

        public CommandBuffer(CommandPool commandPool, VkCommandBuffer handle) {
            this.commandPool = commandPool;
            this.handle = handle;
        }

        public VkCommandBuffer getHandle() {
            return handle;
        }

        public long getFence() {
            return fence;
        }

        /** Emulates functionality of vkWaitForFences() */
        public void wait(Queue queue) {
            queue.waitSubmits(this.fence);
        }

        public boolean isSubmitted() {
            return submitted;
        }

        public boolean isRecording() {
            return recording;
        }

        public void begin(MemoryStack stack) {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            vkBeginCommandBuffer(this.handle, beginInfo);

            this.recording = true;
        }

        
        public void submitCommands(MemoryStack stack, Queue queue, long waitStage) {

            vkEndCommandBuffer(this.handle);

            final long submitFence = queue.submitFenceAdd(); // Has same function as individual fence

            // macOS compat: Branch can be removed once LWJGL 3.3.4+ is guaranteed (MC 26.1+)
            if (sync2) {
                var commandBufferSubmitInfo = VkCommandBufferSubmitInfo.calloc(1, stack).sType$Default()
                        .commandBuffer(this.handle);

                var mainSemaphoreSubmitInfo = VkSemaphoreSubmitInfo.calloc(1, stack).sType$Default()
                        .semaphore(queue.getQueueSemaphore())
                        .stageMask(waitStage)
                        .value(submitFence);

                var submitInfo = VkSubmitInfo2.calloc(1, stack).sType$Default()
                        .pSignalSemaphoreInfos(mainSemaphoreSubmitInfo) // No additional Waits, only Signal
                        .pCommandBufferInfos(commandBufferSubmitInfo);

                vkQueueSubmit2KHR(queue.vkQueue(), submitInfo, 0);
            }
            else {
                var timelineSemaphoreSubmitInfo = VkTimelineSemaphoreSubmitInfo.calloc(stack).sType$Default()
                        .pSignalSemaphoreValues(stack.longs(submitFence));

                // Most early submits don't depend on each other; can be submitted out of order without dst wait stage (afaik allows driver to reorder submits freely)
                // (only the main submit at end frame depends on these)
                var submitInfo = VkSubmitInfo.calloc(stack).sType$Default()
                        .pNext(timelineSemaphoreSubmitInfo)
                        .pSignalSemaphores(stack.longs(queue.getQueueSemaphore()))
                        .pWaitDstStageMask(stack.ints(VK13.VK_PIPELINE_STAGE_NONE)) // No wait stage
                        .pCommandBuffers(stack.pointers(this.handle));

                vkQueueSubmit(queue.vkQueue(), submitInfo, 0);
            }

            this.recording = false;
            this.submitted = true;
            this.fence = submitFence;
            this.commandPool.addToSubmitted(this);
        }

        public void reset() {
            this.submitted = false;
            this.recording = false;
            this.commandPool.addToAvailable(this);
        }
    }

    @Override
    public String toString() {
        return "submitted: " + submittedCmdBuffers.size() + " available: " + availableCmdBuffers.size();
    }
}
