package net.vulkanmod.vulkan.queue;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.IntBuffer;
import java.util.stream.IntStream;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class QueueFamilyIndices {

    // We use Integer to use null as the empty value
    public static int graphicsFamily, presentFamily, transferFamily, computeFamily = -1;
    public static boolean hasTransferQueue;

    public static boolean findQueueFamilies(VkPhysicalDevice device) {


        try (MemoryStack stack = stackPush()) {

            IntBuffer queueFamilyCount = stack.ints(0);

            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);

            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount.get(0), stack);

            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

            //            for(int i = 0; i < queueFamilies.capacity() || !indices.isComplete();i++) {
            for (int i = 0; i < queueFamilies.capacity(); i++) {
                int queueFlags = queueFamilies.get(i).queueFlags();

                if ((queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    graphicsFamily = i;
//                        vkGetPhysicalDeviceSurfaceSupportKHR(device, i, Vulkan.getSurface(), presentSupport);

                    if ((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                        presentFamily = i;
                    }
                } else if ((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                    computeFamily = i;
                } else if ((queueFlags & VK_QUEUE_TRANSFER_BIT) != 0) {
                    hasTransferQueue=true;
                    transferFamily = i;
                }

                if (presentFamily == -1) {
//                        vkGetPhysicalDeviceSurfaceSupportKHR(device, i, Vulkan.getSurface(), presentSupport);

                    if ((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                        presentFamily = i;
                    }
                }

                if (isComplete()) break;
            }

            if (transferFamily == -1) {
                hasTransferQueue=false;
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

            if (graphicsFamily == -1)
                throw new RuntimeException("Unable to find queue family with graphics support.");
            if (presentFamily == -1)
                throw new RuntimeException("Unable to find queue family with present support.");
            if (computeFamily == -1)
                throw new RuntimeException("Unable to find queue family with compute support.");

            System.out.println("-==Queue Family Configuration==-");
            System.out.println("    graphicsFamily -> "+graphicsFamily);
            System.out.println("    transferFamily -> "+transferFamily);
            System.out.println("    presentFamily  -> "+presentFamily);
            System.out.println("    computeFamily  -> "+computeFamily);

        }
        return isComplete();
    }

    public static boolean isComplete() {
        return graphicsFamily != -1 && presentFamily != -1 && transferFamily != -1 && computeFamily != -1;
    }

    public static boolean isSuitable() {
        return graphicsFamily != -1 && presentFamily != -1;
    }

    public static int[] unique() {
        return IntStream.of(graphicsFamily, presentFamily, transferFamily, computeFamily).distinct().toArray();
    }

    public static int[] array() {
        return new int[]{graphicsFamily, presentFamily};
    }
}
