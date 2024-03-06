package net.vulkanmod.vulkan;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class Synchronization {
    private static final int ALLOCATION_SIZE = 50;

    public static final Synchronization INSTANCE = new Synchronization();

    private final LongBuffer value = MemoryUtil.memAllocLong(1);


    public static int submits;
    public static int idx = 0;

    private static final ObjectArrayList<CommandPool.CommandBuffer> commandBuffers = new ObjectArrayList<>();
    public static final long tSemaphore;

    static {
        try (MemoryStack stack = MemoryStack.stackPush()) {
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


    public static void addSubmit(CommandPool.CommandBuffer commandBuffer) {

        if(idx >= ALLOCATION_SIZE) {
            waitSemaphores();
        }

//        idx++;
//        commandBuffer.updateSubmitId(this.submits + this.idx);

        commandBuffers.add(commandBuffer);
    }

//    public int updateValue()
//    {
//        if(idx == ALLOCATION_SIZE)
//            waitSemaphores();
//        idx++;
//        return this.submits + this.idx;
//    }


    public static void waitSemaphores() {
        //TODO: TimelineSemaphore Index
        if(idx == 0) return;

        VkDevice device = Vulkan.getDevice();


        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreWaitInfo waitInfo = VkSemaphoreWaitInfo.calloc(stack)
                    .sType$Default()
                    .flags(0)
                    .semaphoreCount(1)
                    .pSemaphores(stack.longs(tSemaphore))
                    .pValues(stack.longs(submits));


            VK12.vkWaitSemaphores(device, waitInfo, VUtil.UINT64_MAX);
        }

        submits+=idx;

//        //VK12.vkWaitSemaphores(device, tmSemWaitInfo, VUtil.UINT64_MAX)
//        commandBuffers.forEach(CommandPool.CommandBuffer::reset);
        commandBuffers.clear();


        idx = 0;
    }



    public static void waitSubmit(CommandPool.CommandBuffer commandBuffer) {

        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreWaitInfo waitInfo = VkSemaphoreWaitInfo.calloc(stack)
                    .sType$Default()
                    .flags(0)
                    .semaphoreCount(1)
                    .pSemaphores(stack.longs(tSemaphore))
                    .pValues(stack.longs(commandBuffer.getSubmitId()));


            VK12.vkWaitSemaphores(Vulkan.getDevice(), waitInfo, VUtil.UINT64_MAX);
        }
        commandBuffer.reset();


    }

    public boolean checkSemaphoreStatus(long fence) {
        VkDevice device = Vulkan.getDevice();

        final int i = VK12.vkGetSemaphoreCounterValue(device, tSemaphore, value);
        return i == VK_SUCCESS;
    }

    public static int updateValue() {

        idx++;
        return submits + idx;
    }
    public static int getValue()
    {
        return submits + idx;
    }
}
