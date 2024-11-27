package net.vulkanmod.vulkan.queue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class CommandPool {
    final long id;

    private final List<CommandBuffer> commandBuffers = new ObjectArrayList<>();
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
            commandBuffers.add(commandBuffer);
            availableCmdBuffers.add(commandBuffer);
        }
    }



    public void addToAvailable(CommandBuffer commandBuffer) {
        this.availableCmdBuffers.add(commandBuffer);
    }

    public void cleanUp() {
//        for (CommandBuffer commandBuffer : commandBuffers) {
//            vkDestroyFence(Vulkan.getVkDevice(), commandBuffer.fence, null);
//        }
        vkResetCommandPool(Vulkan.getVkDevice(), id, VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT);
        vkDestroyCommandPool(Vulkan.getVkDevice(), id, null);
    }

    public long getId() {
        return id;
    }

    public static class CommandBuffer {
        public final CommandPool commandPool;
        public final VkCommandBuffer handle;
        public int submitId; //Emulates fence Functionality:

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

            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkSemaphoreWaitInfo vkSemaphoreWaitInfo = VkSemaphoreWaitInfo.calloc(stack)
                        .sType$Default()
                        .semaphoreCount(1)
                        .pSemaphores(stack.longs(queue.getTmSemaphore()))
                        .pValues(stack.longs(this.submitId));


                VK12.vkWaitSemaphores(Vulkan.getVkDevice(), vkSemaphoreWaitInfo, VUtil.UINT64_MAX);
            }
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

            int submitId = queue.submitCount().incrementAndGet(); //Has same function as individual fence

            VkTimelineSemaphoreSubmitInfo timelineSemaphoreSubmitInfo = VkTimelineSemaphoreSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pSignalSemaphoreValues(stack.longs(submitId));

            //TODO: No Wait,just Submit
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pNext(timelineSemaphoreSubmitInfo);
            submitInfo.pSignalSemaphores(stack.longs(queue.getTmSemaphore()));
            submitInfo.pWaitDstStageMask(stack.ints(VK13.VK_PIPELINE_STAGE_NONE)); //Clarify its no wait
            submitInfo.pCommandBuffers(stack.pointers(this.handle));

            vkQueueSubmit(queue.queue(), submitInfo, 0);

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
