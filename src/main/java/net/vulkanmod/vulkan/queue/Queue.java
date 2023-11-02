package net.vulkanmod.vulkan.queue;

import net.vulkanmod.vulkan.Device;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK10.*;

public abstract class Queue {

    protected CommandPool commandPool;

    private final VkQueue queue;

    public synchronized CommandPool.CommandBuffer beginCommands() {
        return this.commandPool.beginCommands();
    }

    Queue(MemoryStack stack, int familyIndex) {
        this(stack, familyIndex, true);
    }

    Queue(MemoryStack stack, int familyIndex, boolean initCommandPool) {
        PointerBuffer pQueue = stack.mallocPointer(1);
        vkGetDeviceQueue(Device.device, familyIndex, 0, pQueue);
        this.queue = new VkQueue(pQueue.get(0), Device.device);

        if(initCommandPool)
            this.commandPool = new CommandPool(familyIndex);
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

}
