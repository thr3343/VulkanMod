package net.vulkanmod.vulkan;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.queue.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static net.vulkanmod.vulkan.queue.Queue.*;
import static net.vulkanmod.vulkan.util.VUtil.asPointerBuffer;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public abstract class DeviceManager {
    public static List<DeviceInfo> suitableDevices;

    public static VkPhysicalDevice physicalDevice;
    public static VkDevice device;

    public static DeviceInfo deviceInfo;

    public static VkPhysicalDeviceProperties deviceProperties;
    public static VkPhysicalDeviceMemoryProperties memoryProperties;

    public static SurfaceProperties surfaceProperties;

    static void getAvailableDevices(VkInstance instance) {
        try(MemoryStack stack = stackPush()) {
            suitableDevices = new ObjectArrayList<>();

            IntBuffer deviceCount = stack.ints(0);

            vkEnumeratePhysicalDevices(instance, deviceCount, null);

            if(deviceCount.get(0) == 0) {
                throw new RuntimeException("Failed to find GPUs with Vulkan support");
            }

            PointerBuffer ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0));
            vkEnumeratePhysicalDevices(instance, deviceCount, ppPhysicalDevices);

            VkPhysicalDevice currentDevice;

            for(int i = 0; i < ppPhysicalDevices.capacity(); i++) {

                currentDevice = new VkPhysicalDevice(ppPhysicalDevices.get(i), instance);

                VkPhysicalDeviceProperties deviceProperties = VkPhysicalDeviceProperties.calloc(stack);
                vkGetPhysicalDeviceProperties(currentDevice, deviceProperties);

                if(isDeviceSuitable(currentDevice)) {
                    DeviceInfo device = new DeviceInfo(currentDevice);
                    suitableDevices.add(device);

                }
            }

        }
    }

    static void pickPhysicalDevice(VkInstance instance) {
        getAvailableDevices(instance);

        try(MemoryStack stack = stackPush()) {

            int device = Initializer.CONFIG.device;
            if(device >= 0 && device < suitableDevices.size())
                deviceInfo = suitableDevices.get(device);
            else {
                deviceInfo = autoPickDevice();
                Initializer.CONFIG.device = -1;
            }

            physicalDevice = deviceInfo.physicalDevice;
            QueueFamilyIndices.findQueueFamilies(physicalDevice);
            //Get device properties
            deviceProperties = VkPhysicalDeviceProperties.malloc();
            vkGetPhysicalDeviceProperties(physicalDevice, deviceProperties);

            memoryProperties = VkPhysicalDeviceMemoryProperties.malloc();
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);

            surfaceProperties = querySurfaceProperties(physicalDevice, stack);
        }
    }

    static DeviceInfo autoPickDevice() {
        ArrayList<DeviceInfo> integratedGPUs = new ArrayList<>();
        ArrayList<DeviceInfo> otherDevices = new ArrayList<>();

        boolean flag = false;

        DeviceInfo currentDevice = null;
        for(DeviceInfo device : suitableDevices) {
            currentDevice = device;

            int deviceType = device.properties.deviceType();
            if(deviceType == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                flag = true;
                break;
            }
            else if(deviceType == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU)
                integratedGPUs.add(device);
            else
                otherDevices.add(device);
        }

        if(!flag) {
            if(!integratedGPUs.isEmpty())
                currentDevice = integratedGPUs.get(0);
            else if(!otherDevices.isEmpty())
                currentDevice = otherDevices.get(0);
            else {
                //TODO debug string
//                Initializer.LOGGER.error(DeviceInfo.debugString(ppPhysicalDevices, Vulkan.REQUIRED_EXTENSION, instance));
                throw new RuntimeException("Failed to find a suitable GPU");
            }
        }

        return currentDevice;
    }

    static void createLogicalDevice() {

        try(MemoryStack stack = stackPush()) {

            int[] uniqueQueueFamilies = QueueFamilyIndices.unique();

            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(uniqueQueueFamilies.length, stack);

            for(int i = 0;i < uniqueQueueFamilies.length;i++) {
                VkDeviceQueueCreateInfo queueCreateInfo = queueCreateInfos.get(i);
                queueCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
                queueCreateInfo.queueFamilyIndex(uniqueQueueFamilies[i]);
                queueCreateInfo.pQueuePriorities(stack.floats(1.0f));
            }
            VkPhysicalDeviceVulkan11Features deviceVulkan11Features = VkPhysicalDeviceVulkan11Features.calloc(stack).sType$Default();
            VkPhysicalDeviceVulkan12Features deviceVulkan12Features = VkPhysicalDeviceVulkan12Features.calloc(stack).sType$Default();

            VkPhysicalDeviceFeatures2 deviceFeatures = VkPhysicalDeviceFeatures2.calloc(stack);
            deviceFeatures.sType$Default();
            deviceFeatures.features().samplerAnisotropy(deviceInfo.availableFeatures.features().samplerAnisotropy());
            deviceFeatures.features().logicOp(deviceInfo.availableFeatures.features().logicOp());
            deviceFeatures.features().multiDrawIndirect(deviceInfo.isDrawIndirectSupported());


            deviceVulkan11Features.shaderDrawParameters(deviceInfo.isDrawIndirectSupported());
            deviceVulkan12Features.descriptorIndexing(true);
            //core
            VkPhysicalDeviceDescriptorIndexingFeatures descriptorIndexingFeatures = VkPhysicalDeviceDescriptorIndexingFeatures.calloc(stack).sType$Default()
                    .runtimeDescriptorArray(true) //can specify DescriptorArray length at runtime
                    //binding
                    .descriptorBindingSampledImageUpdateAfterBind(true) //can change/update samplers after bind
                    .descriptorBindingPartiallyBound(true) //Don't have to initialise all Descriptors (as long as NVK_ULL_HANDLE isn't idnexed in the shader)
                    .descriptorBindingVariableDescriptorCount(true) //Allows Unbounded array length in shader
                    .descriptorBindingUpdateUnusedWhilePending(true)
                    //Indexing
                    .shaderSampledImageArrayNonUniformIndexing(true) //can use non-constexpr indices: in exchange for a perf penalty
                    .shaderUniformBufferArrayNonUniformIndexing(true); //can use non-constexpr indices: in exchange for a perf penalty

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
            createInfo.pQueueCreateInfos(queueCreateInfos);
            createInfo.pNext(descriptorIndexingFeatures).pNext(deviceVulkan12Features).pNext(deviceVulkan11Features);
            createInfo.pEnabledFeatures(deviceFeatures.features());
            createInfo.ppEnabledExtensionNames(asPointerBuffer(Vulkan.REQUIRED_EXTENSION));
            createInfo.ppEnabledLayerNames(Vulkan.ENABLE_VALIDATION_LAYERS ? asPointerBuffer(Vulkan.VALIDATION_LAYERS) : null);

            PointerBuffer pDevice = stack.pointers(VK_NULL_HANDLE);

            if(vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create logical device");
            }

            device = new VkDevice(pDevice.get(0), physicalDevice, createInfo, VK_API_VERSION_1_2);

//            PointerBuffer pQueue = stack.pointers(VK_NULL_HANDLE);
//
//            vkGetDeviceQueue(device, indices.graphicsFamily, 0, pQueue);
//            graphicsQueue = new VkQueue(pQueue.get(0), device);
//
//            vkGetDeviceQueue(device, indices.presentFamily, 0, pQueue);
//            presentQueue = new VkQueue(pQueue.get(0), device);
//
//            vkGetDeviceQueue(device, indices.transferFamily, 0, pQueue);
//            transferQueue = new VkQueue(pQueue.get(0), device);

        }
    }

    private static PointerBuffer getRequiredExtensions() {

        PointerBuffer glfwExtensions = glfwGetRequiredInstanceExtensions();

        if(Vulkan.ENABLE_VALIDATION_LAYERS) {

            MemoryStack stack = stackGet();

            PointerBuffer extensions = stack.mallocPointer(glfwExtensions.capacity() + 1);

            extensions.put(glfwExtensions);
            extensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));

            // Rewind the buffer before returning it to reset its position back to 0
            return extensions.rewind();
        }

        return glfwExtensions;
    }

    private static boolean isDeviceSuitable(VkPhysicalDevice device) {

        boolean extensionsSupported = checkDeviceExtensionSupport(device);
        boolean swapChainAdequate = false;

        if(extensionsSupported) {
            try(MemoryStack stack = stackPush()) {
                SurfaceProperties surfaceProperties = querySurfaceProperties(device, stack);
                swapChainAdequate = surfaceProperties.formats.hasRemaining() && surfaceProperties.presentModes.hasRemaining();
            }
        }

        boolean anisotropicFilterSupported = false;
        try(MemoryStack stack = stackPush()) {
            VkPhysicalDeviceFeatures supportedFeatures = VkPhysicalDeviceFeatures.malloc(stack);
            vkGetPhysicalDeviceFeatures(device, supportedFeatures);
            anisotropicFilterSupported = supportedFeatures.samplerAnisotropy();
        }

        return extensionsSupported && swapChainAdequate;
    }

    private static boolean checkDeviceExtensionSupport(VkPhysicalDevice device) {

        try(MemoryStack stack = stackPush()) {

            IntBuffer extensionCount = stack.ints(0);

            vkEnumerateDeviceExtensionProperties(device, (String)null, extensionCount, null);

            VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.malloc(extensionCount.get(0), stack);

            vkEnumerateDeviceExtensionProperties(device, (String)null, extensionCount, availableExtensions);

            Set<String> extensions = availableExtensions.stream()
                    .map(VkExtensionProperties::extensionNameString)
                    .collect(toSet());

            extensions.removeAll(Vulkan.REQUIRED_EXTENSION);

            return availableExtensions.stream()
                    .map(VkExtensionProperties::extensionNameString)
                    .collect(toSet())
                    .containsAll(Vulkan.REQUIRED_EXTENSION);
        }
    }

    // Use the optimal most performant depth format for the specific GPU
    // Nvidia performs best with 24 bit depth, while AMD is most performant with 32-bit float
    public static int findDepthFormat(boolean use24BitsDepthFormat) {
        int[] formats = use24BitsDepthFormat ? new int[]
                {VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_X8_D24_UNORM_PACK32, VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT}
                : new int[] {VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT};

        return findSupportedFormat(
                VK_IMAGE_TILING_OPTIMAL,
                VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT,
                formats);
    }

    private static int findSupportedFormat(int tiling, int features, int... formatCandidates) {
        try(MemoryStack stack = stackPush()) {

            VkFormatProperties props = VkFormatProperties.calloc(stack);

            for (int format : formatCandidates) {

                vkGetPhysicalDeviceFormatProperties(physicalDevice, format, props);

                if (tiling == VK_IMAGE_TILING_LINEAR && (props.linearTilingFeatures() & features) == features) {
                    return format;
                } else if (tiling == VK_IMAGE_TILING_OPTIMAL && (props.optimalTilingFeatures() & features) == features) {
                    return format;
                }

            }
        }

        throw new RuntimeException("Failed to find supported format");
    }

    public static void destroy() {
        GraphicsQueue.cleanUp();
        TransferQueue.cleanUp();
        PresentQueue.cleanUp();

        vkDestroyDevice(device, null);
    }

    public static Queue getGraphicsQueue() {
        return GraphicsQueue;
    }

    public static Queue getPresentQueue() {
        return PresentQueue;
    }

    public static Queue getTransferQueue() {
        return TransferQueue;
    }

//    public static ComputeQueue getComputeQueue() {
//        return computeQueue;
//    }

    public static SurfaceProperties querySurfaceProperties(VkPhysicalDevice device, MemoryStack stack) {

        long surface = Vulkan.getSurface();
        SurfaceProperties details = new SurfaceProperties();

        details.capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
        vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, details.capabilities);

        IntBuffer count = stack.ints(0);

        vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, null);

        if(count.get(0) != 0) {
            details.formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
            vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, details.formats);
        }

        vkGetPhysicalDeviceSurfacePresentModesKHR(device,surface, count, null);

        if(count.get(0) != 0) {
            details.presentModes = stack.mallocInt(count.get(0));
            vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, count, details.presentModes);
        }

        return details;
    }

    public static class SurfaceProperties {
        public VkSurfaceCapabilitiesKHR capabilities;
        public VkSurfaceFormatKHR.Buffer formats;
        public IntBuffer presentModes;
    }

}
