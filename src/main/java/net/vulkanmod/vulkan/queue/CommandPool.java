package net.vulkanmod.vulkan.queue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class CommandPool {
    private static final int ALLOCATION_SIZE = 64;
    final AtomicInteger submits = new AtomicInteger(0);

    long id;

    private final List<CommandBuffer> commandBuffers = new ObjectArrayList<>();
    private final java.util.Queue<CommandBuffer> availableCmdBuffers = new ArrayDeque<>();
//    private final java.util.Queue<CommandBuffer> activeCmdBuffers = new ArrayDeque<>();
    final long tSemaphore;
    private AtomicInteger prevSubmitValue = new AtomicInteger();

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


            VkSemaphoreTypeCreateInfo semaphoreInfoTypeT = VkSemaphoreTypeCreateInfo.calloc(stack)
                    .sType$Default()
                    .semaphoreType(VK12.VK_SEMAPHORE_TYPE_TIMELINE)
                    .initialValue(0);

            VkSemaphoreCreateInfo semaphoreInfo2 = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
                    .pNext(semaphoreInfoTypeT);

            LongBuffer pSemaphore = stack.mallocLong(1);
            VK12.vkCreateSemaphore(Vulkan.getDevice(), semaphoreInfo2, null, pSemaphore);
            tSemaphore=pSemaphore.get(0);
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

    public void submitCommands(CommandBuffer commandBuffer, VkQueue queue, int mask) {

        try(MemoryStack stack = stackPush()) {


//            final int l = (int) getSemaphoreCounterValue(stack);
//            final boolean b = l != submits;
//            if(b)
//            {
////                Initializer.LOGGER.error("Expected value: "+submits + "Actual value: "+ l);
//                submits=l;
////                submits++;
//            }
            commandBuffer.updateSubmitId(submits.get());

            vkEndCommandBuffer(commandBuffer.handle);
//            final int x = Synchronization.updateValue();
            final boolean hasWaitOp = mask != 0;


            VkTimelineSemaphoreSubmitInfo timelineInfo3 = VkTimelineSemaphoreSubmitInfo.calloc(stack)
                    .sType$Default()
//                    .waitSemaphoreValueCount(1)
//                    .pWaitSemaphoreValues(stack.longs(hasWaitOp ? this.getValue() : 0))
                    .signalSemaphoreValueCount(1)
                    .pSignalSemaphoreValues(stack.longs(submits.incrementAndGet())); //TODO:!







            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pNext(timelineInfo3);
//            if(hasWaitOp)
//            {
//                submitInfo.waitSemaphoreCount(1);
//                submitInfo.pWaitSemaphores(stack.longs(this.tSemaphore));
//                submitInfo.pWaitDstStageMask(stack.ints(mask));
//            }
            submitInfo.pSignalSemaphores(stack.longs(this.tSemaphore));
            submitInfo.pCommandBuffers(stack.pointers(commandBuffer.handle));

            vkQueueSubmit(queue, submitInfo, 0);
//            addSubmit(commandBuffer);

        }
    }

    private long getSemaphoreCounterValue(MemoryStack stack) {
        LongBuffer tSemValue = stack.mallocLong(1);
        VK12.vkGetSemaphoreCounterValue(Vulkan.getDevice(), this.tSemaphore, tSemValue);

        return tSemValue.get(0);
    }

    public void addSubmit(CommandPool.CommandBuffer commandBuffer) {

//        if(idx >= ALLOCATION_SIZE) {
//            waitSemaphores();
//        }

//        idx++;
//        commandBuffer.updateSubmitId(this.submits + this.idx);

//        activeCmdBuffers.add(commandBuffer);
    }

    public void waitSemaphores() {
        //TODO: replace with GPU-Side Synchronisation + Split into per Queue tmSemaphores to skip Graphics Submits
        if(prevSubmitValue.get() == submits.get()) return;

        VkDevice device = Vulkan.getDevice();


        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreWaitInfo waitInfo = VkSemaphoreWaitInfo.calloc(stack)
                    .sType$Default()
                    .flags(0)
                    .semaphoreCount(1)
                    .pSemaphores(stack.longs(tSemaphore))
                    .pValues(stack.longs(prevSubmitValue.get()));


            VK12.vkWaitSemaphores(device, waitInfo, VUtil.UINT64_MAX);

//            VkSemaphoreSignalInfo vkSemaphoreSignalInfo =VkSemaphoreSignalInfo.calloc(stack)
//                    .sType$Default()
//                    .semaphore(this.tSemaphore)
//                    .value(this.submits+idx);
//            VK12.vkSignalSemaphore(Vulkan.getDevice(), vkSemaphoreSignalInfo);
            prevSubmitValue.set(submits.get());
        }

        commandBuffers.forEach(CommandBuffer::reset);
        commandBuffers.clear();

////        //VK12.vkWaitSemaphores(device, tmSemWaitInfo, VUtil.UINT64_MAX)
//        for (int i = 0; i < commandBuffers.size() && i<idx; i++) {
//            CommandBuffer commandBuffer = commandBuffers.get(i);
//            commandBuffer.reset();
//        }
//        for (int i = 0; i < commandBuffers.size() && i < idx; i++) {
//
//            commandBuffers.remove(i);
//        }



    }

    public void addToAvailable(CommandBuffer commandBuffer) {
        this.availableCmdBuffers.add(commandBuffer);
    }

    public void cleanUp() {
        vkResetCommandPool(Vulkan.getDevice(), id, VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT);
        vkDestroyCommandPool(Vulkan.getDevice(), id, null);
        vkDestroySemaphore(Vulkan.getDevice(), tSemaphore, null);
    }

    public void waitSubmit(CommandPool.CommandBuffer commandBuffer) {
        if(prevSubmitValue.get() == submits.get()) return; //Fence/Submit Skip: skip Waits if no Submits have actually occurred

        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreWaitInfo waitInfo = VkSemaphoreWaitInfo.calloc(stack)
                    .sType$Default()
                    .flags(0)
                    .semaphoreCount(1)
                    .pSemaphores(stack.longs(tSemaphore))
                    .pValues(stack.longs(commandBuffer.getSubmitId()));


            VK12.vkWaitSemaphores(Vulkan.getDevice(), waitInfo, VUtil.UINT64_MAX);
        }
//        commandBuffer.reset();


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

        public void updateSubmitId(long i) {
            this.submitId=i;
        }
    }
}
