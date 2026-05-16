package net.vulkanmod.vulkan.queue;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.stream.IntStream;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK10.*;

public abstract class Queue {
    private static VkDevice device;
    private static QueueFamilyIndices queueFamilyIndices;

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

    Queue(MemoryStack stack, int familyIndex) {
        this(stack, familyIndex, true);
    }

    Queue(MemoryStack stack, int familyIndex, boolean initCommandPool) {
        PointerBuffer pQueue = stack.mallocPointer(1);
        vkGetDeviceQueue(DeviceManager.vkDevice, familyIndex, 0, pQueue);
        this.vkQueue = new VkQueue(pQueue.get(0), DeviceManager.vkDevice);

        this.commandPool = initCommandPool ? new CommandPool(familyIndex) : null;

        this.queueSemaphore = initCommandPool ? getQueueSemaphore(stack) : VK_NULL_HANDLE;

        LongBuffer pSubmitValue = MemoryUtil.memAllocLong(1);
        LongBuffer pSemaphores = MemoryUtil.memAllocLong(1);

        vkSemaphoreWaitInfo = VkSemaphoreWaitInfo.calloc()
                .sType$Default()
                .semaphoreCount(1)
                .pSemaphores(pSemaphores.put(0, this.queueSemaphore))
                .pValues(pSubmitValue);

        this.pSubmitValue = MemoryUtil.memAddress(pSubmitValue);
    }

    private static long getQueueSemaphore(MemoryStack stack) {
        VkSemaphoreTypeCreateInfo semaphoreTypeCreateInfo = VkSemaphoreTypeCreateInfo.calloc(stack)
                .sType$Default()
                .semaphoreType(VK12.VK_SEMAPHORE_TYPE_TIMELINE)
                .initialValue(0);

        VkSemaphoreCreateInfo semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack)
                .sType$Default()
                .pNext(semaphoreTypeCreateInfo);

        LongBuffer pPointer = stack.mallocLong(1);

        VK12.vkCreateSemaphore(Vulkan.getVkDevice(), semaphoreCreateInfo, null, pPointer);
        return pPointer.get(0);
    }

    public synchronized void submitCommands(CommandPool.CommandBuffer commandBuffer) {
        try (MemoryStack stack = stackPush()) {
            commandBuffer.submitCommands(stack, this, VK13.VK_PIPELINE_STAGE_2_NONE);
        }
    }

    public synchronized void submitCommands(CommandPool.CommandBuffer commandBuffer, long waitStage) {
        try (MemoryStack stack = stackPush()) {
            commandBuffer.submitCommands(stack, this, waitStage);
        }
    }

    public VkQueue vkQueue() {
        return this.vkQueue;
    }

    public void cleanUp() {
        if (commandPool != null) {
            commandPool.cleanUp();
            vkDestroySemaphore(Vulkan.getVkDevice(), this.queueSemaphore, null);
        }
        MemoryUtil.memFree(vkSemaphoreWaitInfo.pSemaphores());
        MemoryUtil.memFree(vkSemaphoreWaitInfo.pValues());
        vkSemaphoreWaitInfo.free();
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

    public long submitFenceAdd() {
        return ++submitFence;
    }

    public long submitFence() {
        return submitFence;
    }

    public long getQueueSemaphore() {
        return this.queueSemaphore;
    }

    public void waitSubmits() {
        waitSubmits(this.submitFence);
    }

    // Functionally identical to Synchronisation.waitFences(), but for a specific queue
    public void waitSubmits(long submitFence) {
        VUtil.UNSAFE.putLong(pSubmitValue, submitFence);
        VK12.vkWaitSemaphores(device, vkSemaphoreWaitInfo, VUtil.UINT64_MAX);
    }

    public static QueueFamilyIndices getQueueFamilies() {
        if (device == null)
            device = Vulkan.getVkDevice();

        if (queueFamilyIndices == null) {
            queueFamilyIndices = findQueueFamilies(device.getPhysicalDevice());
        }
        return queueFamilyIndices;
    }

    public static QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device) {
        QueueFamilyIndices indices = new QueueFamilyIndices();

        try (MemoryStack stack = stackPush()) {

            IntBuffer queueFamilyCount = stack.ints(0);

            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);

            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount.get(0), stack);

            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

            IntBuffer presentSupport = stack.ints(VK_FALSE);

            for (int i = 0; i < queueFamilies.capacity(); i++) {
                int queueFlags = queueFamilies.get(i).queueFlags();

                if ((queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    indices.graphicsFamily = i;

                    vkGetPhysicalDeviceSurfaceSupportKHR(device, i, Vulkan.getSurface(), presentSupport);

                    if (presentSupport.get(0) == VK_TRUE) {
                        indices.presentFamily = i;
                    }
                } else if ((queueFlags & (VK_QUEUE_GRAPHICS_BIT)) == 0
                        && (queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                    indices.computeFamily = i;
                } else if ((queueFlags & (VK_QUEUE_COMPUTE_BIT | VK_QUEUE_GRAPHICS_BIT)) == 0
                        && (queueFlags & VK_QUEUE_TRANSFER_BIT) != 0) {
                    indices.transferFamily = i;
                }

                if (indices.presentFamily == -1) {
                    vkGetPhysicalDeviceSurfaceSupportKHR(device, i, Vulkan.getSurface(), presentSupport);

                    if (presentSupport.get(0) == VK_TRUE) {
                        indices.presentFamily = i;
                    }
                }

                if (indices.isComplete())
                    break;
            }

            if (indices.presentFamily == -1) {
                // Some drivers will not show present support even if some queue supports it
                // Use compute queue as fallback

                indices.presentFamily = indices.computeFamily;
                Initializer.LOGGER.warn("Using compute queue as present fallback");
            }

            // In case there's no dedicated transfer queue, we need choose another one
            // preferably a different one from the already selected queues
            if (indices.transferFamily == -1) {

                int transferIndex = -1;
                for (int i = 0; i < queueFamilies.capacity(); i++) {
                    int queueFlags = queueFamilies.get(i).queueFlags();

                    if ((queueFlags & VK_QUEUE_TRANSFER_BIT) != 0) {
                        if (transferIndex == -1)
                            transferIndex = i;

                        if ((queueFlags & (VK_QUEUE_GRAPHICS_BIT)) == 0) {
                            indices.transferFamily = i;

                            if (i != indices.computeFamily)
                                break;

                            transferIndex = i;
                        }
                    }
                }

                if (transferIndex == -1)
                    throw new RuntimeException("Failed to find queue family with transfer support");

                indices.transferFamily = transferIndex;
            }

            if (indices.computeFamily == -1) {
                for (int i = 0; i < queueFamilies.capacity(); i++) {
                    int queueFlags = queueFamilies.get(i).queueFlags();

                    if ((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                        indices.computeFamily = i;
                        break;
                    }
                }
            }

            if (indices.graphicsFamily == VK_QUEUE_FAMILY_IGNORED)
                throw new RuntimeException("Unable to find queue family with graphics support.");
            if (indices.presentFamily == VK_QUEUE_FAMILY_IGNORED)
                throw new RuntimeException("Unable to find queue family with present support.");
            if (indices.computeFamily == VK_QUEUE_FAMILY_IGNORED)
                throw new RuntimeException("Unable to find queue family with compute support.");

            return indices;
        }
    }

    public static class QueueFamilyIndices {
        public int graphicsFamily = VK_QUEUE_FAMILY_IGNORED;
        public int presentFamily = VK_QUEUE_FAMILY_IGNORED;
        public int transferFamily = VK_QUEUE_FAMILY_IGNORED;
        public int computeFamily = VK_QUEUE_FAMILY_IGNORED;

        public boolean isComplete() {
            return graphicsFamily != -1 && presentFamily != -1 && transferFamily != -1 && computeFamily != -1;
        }

        public boolean isSuitable() {
            return graphicsFamily != -1 && presentFamily != -1;
        }

        public int[] unique() {
            return IntStream.of(graphicsFamily, presentFamily, transferFamily, computeFamily).distinct().toArray();
        }

        public int[] array() {
            return new int[]{graphicsFamily, presentFamily};
        }
    }
}
