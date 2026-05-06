package net.vulkanmod.vulkan.queue;

import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayDeque;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class CommandPool {
    private final long id;

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
        public long submitId; // The submit number this cmd belongs to: Emulates fence Functionality:
                              // e.g. submitId = 50: this cmd was submitted on the 50th submit to this queue
        boolean submitted;
        boolean recording;

        public CommandBuffer(CommandPool commandPool, VkCommandBuffer handle) {
            this.commandPool = commandPool;
            this.handle = handle;
            this.submitId = 0;
        }

        public VkCommandBuffer getHandle() {
            return handle;
        }

        public long getSubmitId() {
            return submitId;
        }

        //Emulates functionality of vkWaitForFences()
        public void wait(Queue queue) {
            queue.waitSubmits(this.submitId);
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

        public void submitCommands(MemoryStack stack, Queue queue) {

            vkEndCommandBuffer(this.handle);

            long submitId = queue.submitCountAdd(); //Has same function as individual fence

            var timelineSemaphoreSubmitInfo = VkTimelineSemaphoreSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pSignalSemaphoreValues(stack.longs(submitId));

            // Avoiding Wait Stage + wait Value to allow Out Of Order Submits: (afaik this allows the driver to reorder submits freely)
            var submitInfo = VkSubmitInfo.calloc(stack).sType$Default()
                    .pNext(timelineSemaphoreSubmitInfo)
                    .pSignalSemaphores(stack.longs(queue.getTmSemaphore()))
                    .pWaitDstStageMask(stack.ints(VK13.VK_PIPELINE_STAGE_NONE)) //No wait stag
                    .pCommandBuffers(stack.pointers(this.handle));

            vkQueueSubmit(queue.vkQueue(), submitInfo, 0);

            this.recording = false;
            this.submitted = true;


            this.submitId = submitId;
        }

        public void reset() {
            this.submitted = false;
            this.recording = false;
            this.commandPool.addToAvailable(this);
        }
    }
}
