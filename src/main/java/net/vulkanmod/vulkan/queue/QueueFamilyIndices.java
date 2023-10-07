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

    // Remove the Depreciated Boxed Integer Classes and use -1 instead (as a substitute for null)
    public static int graphicsFamily, presentFamily, transferFamily, computeFamily = -1;
    private static boolean hasDedicatedTransferQueue;

    public static boolean findQueueFamilies(VkPhysicalDevice device) {

        try(MemoryStack stack = stackPush()) {

            IntBuffer queueFamilyCount = stack.ints(0);

            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);

            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.malloc(queueFamilyCount.get(0), stack);

            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

            //Some Android Drivers are bugged and do not support vkGetPhysicalDeviceSurfaceSupportKHR() properly,
            //So we fallback to Compute Support instead (when determining present capability)

            //            for(int i = 0; i < queueFamilies.capacity() || !indices.isComplete();i++) {
            for (int i = 0; i < queueFamilies.capacity(); i++) {
                int queueFlags = queueFamilies.get(i).queueFlags();

                if ((queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    graphicsFamily = i;

                    if((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                        presentFamily = i;
                    }
                } else if ((queueFlags & (VK_QUEUE_GRAPHICS_BIT)) == 0
                        && (queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                    computeFamily = i;
                } else if ((queueFlags & (VK_QUEUE_COMPUTE_BIT | VK_QUEUE_GRAPHICS_BIT)) == 0
                        && (queueFlags & VK_QUEUE_TRANSFER_BIT) != 0) {
                    transferFamily = i;
                }

                if(presentFamily == -1) {

                    if((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                        presentFamily = i;
                    }
                }

                if(isComplete()) break;
            }

            if(transferFamily == -1) {

                int fallback = -1;
                for(int i = 0; i < queueFamilies.capacity(); i++) {
                    int queueFlags = queueFamilies.get(i).queueFlags();

                    if((queueFlags & VK_QUEUE_TRANSFER_BIT) != 0) {
                        if(fallback == -1)
                            fallback = i;

                        if ((queueFlags & (VK_QUEUE_GRAPHICS_BIT)) == 0) {
                            transferFamily = i;

                            if(i != computeFamily)
                                break;
                            fallback = i;
                        }
                    }

                    if(fallback == -1)
                        throw new RuntimeException("Failed to find queue family with transfer support");

                    transferFamily = fallback;
                }
            }

            if(computeFamily == -1) {
                for(int i = 0; i < queueFamilies.capacity(); i++) {
                    int queueFlags = queueFamilies.get(i).queueFlags();

                    if((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
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

            hasDedicatedTransferQueue =graphicsFamily!=transferFamily;

            Initializer.LOGGER.info("-==Queue Family Configuration==-");
            Initializer.LOGGER.info("    graphicsFamily -> "+graphicsFamily);
            Initializer.LOGGER.info("    transferFamily -> "+transferFamily);
            Initializer.LOGGER.info("    presentFamily  -> "+presentFamily);
            Initializer.LOGGER.info("    computeFamily  -> "+computeFamily);

        }
        return isComplete();
    }

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
