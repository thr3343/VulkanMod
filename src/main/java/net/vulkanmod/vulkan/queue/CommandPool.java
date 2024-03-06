package net.vulkanmod.vulkan.queue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
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

        try(MemoryStack stack = stackPush()) {

            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            poolInfo.queueFamilyIndex(queueFamilyIndex);
            poolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);

            if (vkCreateCommandPool(Vulkan.getDevice(), poolInfo, null, pCommandPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool");
            }

            this.id = pCommandPool.get(0);
        }
    }

    public CommandBuffer beginCommands() {

        try(MemoryStack stack = stackPush()) {
            final int size = 10;

            if(availableCmdBuffers.isEmpty()) {

                VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
                allocInfo.sType$Default();
                allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
                allocInfo.commandPool(id);
                allocInfo.commandBufferCount(size);

                PointerBuffer pCommandBuffer = stack.mallocPointer(size);
                vkAllocateCommandBuffers(Vulkan.getDevice(), allocInfo, pCommandBuffer);



                for(int i = 0; i < size; ++i) {


                    CommandBuffer commandBuffer = new CommandBuffer(new VkCommandBuffer(pCommandBuffer.get(i), Vulkan.getDevice()));
                    commandBuffers.add(commandBuffer);
                    availableCmdBuffers.add(commandBuffer);
                }

            }

            CommandBuffer commandBuffer = availableCmdBuffers.poll();

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            vkBeginCommandBuffer(commandBuffer.handle, beginInfo);

//            current++;

            return commandBuffer;
        }
    }

    public long submitCommands(CommandBuffer commandBuffer, VkQueue queue) {

        try(MemoryStack stack = stackPush()) {


            vkEndCommandBuffer(commandBuffer.handle);
            int currentIdx = Synchronization.getValue();
            commandBuffer.updateSubmitId(Synchronization.updateValue());
//            final int x = Synchronization.updateValue();
            VkTimelineSemaphoreSubmitInfo timelineInfo3 = VkTimelineSemaphoreSubmitInfo.calloc(stack)
                    .sType$Default()
                    .waitSemaphoreValueCount(1)
                    .pWaitSemaphoreValues(stack.longs(0))
                    .signalSemaphoreValueCount(1)
                    .pSignalSemaphoreValues(stack.longs(commandBuffer.getSubmitId()/*-1*/)); //TODO:!



            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pNext(timelineInfo3);
            submitInfo.waitSemaphoreCount(1);
            submitInfo.pWaitSemaphores(stack.longs(Synchronization.tSemaphore));
            submitInfo.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT));
            submitInfo.pSignalSemaphores(stack.longs(Synchronization.tSemaphore));
            submitInfo.pCommandBuffers(stack.pointers(commandBuffer.handle));

            vkQueueSubmit(queue, submitInfo, 0);
            return 1;
        }
    }

    public void addToAvailable(CommandBuffer commandBuffer) {
        this.availableCmdBuffers.add(commandBuffer);
    }

    public void cleanUp() {
        vkResetCommandPool(Vulkan.getDevice(), id, VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT);
        vkDestroyCommandPool(Vulkan.getDevice(), id, null);
    }

    public class CommandBuffer {
        final VkCommandBuffer handle;

        boolean submitted;
        boolean recording;
        private long submitId;

        public CommandBuffer(VkCommandBuffer handle) {
            this.handle = handle;

        }

        public VkCommandBuffer getHandle() {
            return handle;
        }



        public boolean isSubmitted() {
            return submitted;
        }

        public boolean isRecording() {
            return recording;
        }

        public void reset() {
            this.submitted = false;
            this.recording = false;
//            this.submitId = 0;
            addToAvailable(this);
        }

        public long getSubmitId() {
            return this.submitId;
        }

        public void updateSubmitId(int i) {
            this.submitId=i;
        }
    }
}
