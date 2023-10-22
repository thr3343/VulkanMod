package net.vulkanmod.vulkan.queue;

import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.IntBuffer;
import java.util.stream.IntStream;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class QueueFamilyIndices {


    public static boolean findQueueFamilies(VkPhysicalDevice device) {

        try (MemoryStack stack = stackPush()) {

            IntBuffer queueFamilyCount = stack.ints(0);

            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);

            if (queueFamilyCount.get(0) == 1) {
                computeFamily = transferFamily = presentFamily = graphicsFamily = 0;
                return true;
            }

            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount.get(0), stack);

            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

            //            for(int i = 0; i < queueFamilies.capacity() || !indices.isComplete();i++) {
            for (int i = 0; i < queueFamilies.capacity(); i++) {
                int queueFlags = queueFamilies.get(i).queueFlags();

                if ((queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    graphicsFamily = i;


                    if ((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                        presentFamily = i;
                    }
                } else if ((queueFlags & (VK_QUEUE_GRAPHICS_BIT)) == 0
                        && (queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                    computeFamily = i;
                } else if ((queueFlags & (VK_QUEUE_COMPUTE_BIT | VK_QUEUE_GRAPHICS_BIT)) == 0
                        && (queueFlags & VK_QUEUE_TRANSFER_BIT) != 0) {
                    transferFamily = i;
                }

                if (presentFamily == null) {

                    if ((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                        presentFamily = i;
                    }
                }

                if (isComplete()) break;
            }

            if (transferFamily == null) {

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

            if (computeFamily == null) {
                for (int i = 0; i < queueFamilies.capacity(); i++) {
                    int queueFlags = queueFamilies.get(i).queueFlags();

                    if ((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                        computeFamily = i;
                        break;
                    }
                }
            }

            if (graphicsFamily == null)
                throw new RuntimeException("Unable to find queue family with graphics support.");
            if (presentFamily == null)
                throw new RuntimeException("Unable to find queue family with present support.");
            if (computeFamily == null)
                throw new RuntimeException("Unable to find queue family with compute support.");

            return isComplete();
        }
    }

    public enum Family {
        Graphics,
        Transfer,
        Compute
    }


        // We use Integer to use null as the empty value
        public static Integer graphicsFamily;
        public static Integer presentFamily;
        public static Integer transferFamily;
        public static Integer computeFamily;

        public static boolean isComplete() {
            return graphicsFamily != null && presentFamily != null && transferFamily != null && computeFamily != null;
        }

        public static boolean isSuitable() {
            return graphicsFamily != null && presentFamily != null;
        }

        public static int[] unique() {
            return IntStream.of(graphicsFamily, presentFamily, transferFamily, computeFamily).distinct().toArray();
        }

        public static int[] array() {
            return new int[]{graphicsFamily, presentFamily};
        }

}
