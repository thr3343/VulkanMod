package net.vulkanmod.vulkan.queue;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.IntBuffer;
import java.util.stream.IntStream;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK10.*;

public class QueueFamilyIndices {

    public static boolean findQueueFamilies(VkPhysicalDevice device) {

        try (MemoryStack stack = stackPush()) {

            IntBuffer queueFamilyCount = stack.ints(0);

            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);

            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount.get(0), stack);

            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

            IntBuffer presentSupport = stack.ints(VK_FALSE);

            for (int i = 0; i < queueFamilies.capacity(); i++) {
                int queueFlags = queueFamilies.get(i).queueFlags();

                if ((queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    graphicsFamily = i;

                    vkGetPhysicalDeviceSurfaceSupportKHR(device, i, Vulkan.getSurface(), presentSupport);

                    if (presentSupport.get(0) == VK_TRUE) {
                        presentFamily = i;
                    }
                } else if ((queueFlags & (VK_QUEUE_GRAPHICS_BIT)) == 0
                        && (queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                    computeFamily = i;
                } else if ((queueFlags & (VK_QUEUE_COMPUTE_BIT | VK_QUEUE_GRAPHICS_BIT)) == 0
                        && (queueFlags & VK_QUEUE_TRANSFER_BIT) != 0) {
                    transferFamily = i;
                }

                if (presentFamily == -1) {
                    vkGetPhysicalDeviceSurfaceSupportKHR(device, i, Vulkan.getSurface(), presentSupport);

                    if (presentSupport.get(0) == VK_TRUE) {
                        presentFamily = i;
                    }
                }

                if (isComplete())
                    break;
            }

            if (presentFamily == -1) {
                // Some drivers will not show present support even if some queue supports it
                // Use compute queue as fallback

                presentFamily = computeFamily;
                Initializer.LOGGER.warn("Using compute queue as present fallback");
            }

            if (transferFamily == -1) {

                int fallback = -1;
                for (int i = 0; i < queueFamilies.capacity(); i++) {
                    int queueFlags = queueFamilies.get(i).queueFlags();

                    if ((queueFlags & VK_QUEUE_TRANSFER_BIT) != 0) {
                        if (fallback == -1)
                            fallback = i;

                        if ((queueFlags & (VK_QUEUE_GRAPHICS_BIT)) == 0) {
                            transferFamily = i;

                            if (i != computeFamily)
                                break;
                            fallback = i;
                        }
                    }

                    if (fallback == -1)
                        throw new RuntimeException("Failed to find queue family with transfer support");

                    transferFamily = fallback;
                }
            }

            if (computeFamily == -1) {
                for (int i = 0; i < queueFamilies.capacity(); i++) {
                    int queueFlags = queueFamilies.get(i).queueFlags();

                    if ((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                        computeFamily = i;
                        break;
                    }
                }
            }

            if (graphicsFamily == VK_QUEUE_FAMILY_IGNORED)
                throw new RuntimeException("Unable to find queue family with graphics support.");
            if (presentFamily == VK_QUEUE_FAMILY_IGNORED)
                throw new RuntimeException("Unable to find queue family with present support.");
            if (computeFamily == VK_QUEUE_FAMILY_IGNORED)
                throw new RuntimeException("Unable to find queue family with compute support.");

            return isComplete();
        }
    }


        public static int graphicsFamily = VK_QUEUE_FAMILY_IGNORED;
        public static int presentFamily = VK_QUEUE_FAMILY_IGNORED;
        public static int transferFamily = VK_QUEUE_FAMILY_IGNORED;
        public static int computeFamily = VK_QUEUE_FAMILY_IGNORED;

        public static boolean isComplete() {
            return graphicsFamily != -1 && presentFamily != -1 && transferFamily != -1 && computeFamily != -1;
        }

        public boolean isSuitable() {
            return graphicsFamily != -1 && presentFamily != -1;
        }

        public static int[] unique() {
            return IntStream.of(graphicsFamily, presentFamily, transferFamily, computeFamily).distinct().toArray();
        }

        public int[] array() {
            return new int[]{graphicsFamily, presentFamily};
        }

}
