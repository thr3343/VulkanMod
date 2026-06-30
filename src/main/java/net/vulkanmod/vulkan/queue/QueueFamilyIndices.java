package net.vulkanmod.vulkan.queue;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.IntBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK10.*;

public enum QueueFamilyIndices {

    graphicsFamily(VK_QUEUE_GRAPHICS_BIT),
    transferFamily(VK_QUEUE_TRANSFER_BIT),
    computeFamily(VK_QUEUE_COMPUTE_BIT),
    presentFamily(VK_QUEUE_FAMILY_IGNORED);

    public final int queueFamily;

    QueueFamilyIndices(int flags) {

        // use queue family with most precise (lowest bit count) flags


        try (MemoryStack stack = stackPush()) {

            IntBuffer queueFamilyCount = stack.ints(0);

            vkGetPhysicalDeviceQueueFamilyProperties(DeviceManager.physicalDevice, queueFamilyCount, null);

            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount.get(0), stack);

            vkGetPhysicalDeviceQueueFamilyProperties(DeviceManager.physicalDevice, queueFamilyCount, queueFamilies);

            IntBuffer presentSupport = stack.ints(VK_FALSE);

            int Idealbits = Integer.MAX_VALUE;
            int matchedQueueIdx = 0;
            for (int queueFamilyIndex = 0; queueFamilyIndex < queueFamilies.capacity(); queueFamilyIndex++) {
                int queueFlags = queueFamilies.get(queueFamilyIndex).queueFlags();

                if(this.name().equals("presentFamily")) {
                    vkGetPhysicalDeviceSurfaceSupportKHR(DeviceManager.physicalDevice, queueFamilyIndex, Vulkan.getSurface(), presentSupport);

                    if (presentSupport.get(0) == VK_TRUE) {
                        this.queueFamily = matchedQueueIdx;
                        return;
                    }
                }


                final var distinctFlags = Integer.bitCount(queueFlags);
                if (distinctFlags < Idealbits && (queueFlags & flags) != 0) {
                    Idealbits = distinctFlags;
                    matchedQueueIdx = queueFamilyIndex;
                }
            }
            this.queueFamily = matchedQueueIdx;
        }

    }
}
