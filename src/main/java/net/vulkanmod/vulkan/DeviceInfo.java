package net.vulkanmod.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.lwjgl.glfw.GLFW.GLFW_PLATFORM_WIN32;
import static org.lwjgl.glfw.GLFW.glfwGetPlatform;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkEnumerateInstanceVersion;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2;

public class DeviceInfo {

    public static final String cpuInfo;

    final VkPhysicalDevice physicalDevice;
    final VkPhysicalDeviceProperties2 properties;


    private final int vendorId;
    public final String vendorIdString;
    public final String deviceName;
    public final String driverVersion;
    public final String driverId;
    public final String vkDriverVersion;
    public final String vkInstanceLoaderVersion;

    public final VkPhysicalDeviceFeatures2 availableFeatures;

//    public final VkPhysicalDeviceVulkan13Features availableFeatures13;
//    public final boolean vulkan13Support;

    private boolean drawIndirectSupported;

    static {
        CentralProcessor centralProcessor = new SystemInfo().getHardware().getProcessor();
        cpuInfo = String.format("%s", centralProcessor.getProcessorIdentifier().getName()).replaceAll("\\s+", " ");

    }

    public DeviceInfo(VkPhysicalDevice device) {
        this.physicalDevice = device;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceVulkan12Properties vulkan12Properties = VkPhysicalDeviceVulkan12Properties.malloc(stack).sType$Default();

            this.properties = VkPhysicalDeviceProperties2.calloc()
                    .sType$Default()
                    .pNext(vulkan12Properties);
            VK11.vkGetPhysicalDeviceProperties2(physicalDevice, properties);
            var properties = this.properties.properties();

            //Much More Robust VK1.2 Check, uses the actual/real driver version instead of the Instance ver
            if((properties.apiVersion() >>> 12 & 0x3FF) < 2) throw new RuntimeException("Vulkan 1.2 not available");

            this.vendorId = properties.vendorID();
            this.vendorIdString = decodeVendor(properties.vendorID());
            this.deviceName = properties.deviceNameString();
            this.driverId = getVkDriverId(vulkan12Properties.driverID());
            this.driverVersion = vulkan12Properties.driverInfoString();
            this.vkDriverVersion = decDefVersion(properties.apiVersion());
            this.vkInstanceLoaderVersion = decDefVersion(getInstVkVer());

            this.availableFeatures = VkPhysicalDeviceFeatures2.calloc();
            this.availableFeatures.sType$Default();

            VkPhysicalDeviceVulkan11Features availableFeatures11 = VkPhysicalDeviceVulkan11Features.malloc(stack)
                    .sType$Default();
            this.availableFeatures.pNext(availableFeatures11);

            //Vulkan 1.3
//        this.availableFeatures13 = VkPhysicalDeviceVulkan13Features.malloc();
//        this.availableFeatures13.sType$Default();
//        this.availableFeatures11.pNext(this.availableFeatures13.address());
//
//        this.vulkan13Support = this.device.getCapabilities().apiVersion == VK_API_VERSION_1_3;

            vkGetPhysicalDeviceFeatures2(this.physicalDevice, this.availableFeatures);

            if (this.availableFeatures.features().multiDrawIndirect() && availableFeatures11.shaderDrawParameters())
                this.drawIndirectSupported = true;
        }

    }
//https://registry.khronos.org/vulkan/specs/1.3-extensions/man/html/VkDriverId.html
    private String getVkDriverId(int i) {
        return switch (i)
        {
                    case 1 -> "AMD (Proprietary)";
                    case 2 -> "AMD (Open Source)";
                    case 3 -> "MESA (RADV)";
                    case 4 -> "NVIDIA (Proprietary)";
                    case 5 -> "Intel (Proprietary) Windows";
                    case 6 -> "Intel MESA (Open Source)";
                    case 7 -> "Imagination (Proprietary)";
                    case 8 -> "Qualcomm (Proprietary)";
                    case 9 -> "ARM (Proprietary)";
                    case 10 -> "GOOGLE_SWIFTSHADER";
                    case 11 -> "GGP (Proprietary)";
                    case 12 -> "Broadcom (Proprietary)";
                    case 13 -> "MESA (LLVMPIPE)";
                    case 14 -> "MoltenVK";

                    case 18 -> "MESA (Turnip)";
                    case 19 -> "MESA (V3DV)";
                    case 20 -> "MESA (PANVK)";
                    case 21 -> "Samsung (Proprietary)";
                    case 22 -> "MESA (VENUS)";
                    case 23 -> "MESA (DOZEN)";
                    case 24 -> "MESA (NVK)";
                    case 25 -> "Imagination (Open Source) MESA";
                    case 26 -> "MESA (AGXV)";
                    default -> "N/A";
        };
    }

    private static String decodeVendor(int i) {
        return switch (i) {
            case (0x10DE) -> "Nvidia";
            case (0x1022) -> "AMD";
            case (0x8086) -> "Intel";
            default -> "undef"; //Either AMD or Unknown Driver version/vendor and.or Encoding Scheme
        };
    }

    //Should Work with AMD: https://gpuopen.com/learn/decoding-radeon-vulkan-versions/

    static String decDefVersion(int v) {
        return VK_VERSION_MAJOR(v) + "." + VK_VERSION_MINOR(v) + "." + VK_VERSION_PATCH(v);
    }
    //0x10DE = Nvidia: https://pcisig.com/membership/member-companies?combine=Nvidia
    //https://registry.khronos.org/vulkan/specs/1.3-extensions/man/html/VkPhysicalDeviceProperties.html
    //this should work with Nvidia + AMD but is not guaranteed to work with intel drivers in Windows and more obscure/Exotic Drivers/vendors
    private static String decodeDvrVersion(int v, int i) {
        return switch (i) {
            case (0x10DE) -> decodeNvidia(v); //Nvidia
            case (0x1022) -> decDefVersion(v); //AMD
            case (0x8086) -> decIntelVersion(v); //Intel
            default -> decDefVersion(v); //Either AMD or Unknown Driver Encoding Scheme
        };
    }

    //Source: https://www.intel.com/content/www/us/en/support/articles/000005654/graphics.html
    //Won't Work with older Drivers (15.45 And.or older)
    //May not work as this uses Guess work+Assumptions
    private static String decIntelVersion(int v) {
        return (glfwGetPlatform()==GLFW_PLATFORM_WIN32) ? (v >>> 14) + "." + (v & 0x3fff) : decDefVersion(v);
    }


    private static String decodeNvidia(int v) {
        return (v >>> 22 & 0x3FF) + "." + (v >>> 14 & 0xff) + "." + (v >>> 6 & 0xff) + "." + (v & 0xff);
    }

    static int getInstVkVer() {
        try(MemoryStack stack = stackPush())
        {
            var a = stack.mallocInt(1);
            vkEnumerateInstanceVersion(a);
            return a.get(0);
        }
    }

    private String unsupportedExtensions(Set<String> requiredExtensions) {

        try(MemoryStack stack = stackPush()) {

            IntBuffer extensionCount = stack.ints(0);

            vkEnumerateDeviceExtensionProperties(physicalDevice, (String)null, extensionCount, null);

            VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.mallocStack(extensionCount.get(0), stack);

            vkEnumerateDeviceExtensionProperties(physicalDevice, (String)null, extensionCount, availableExtensions);

            Set<String> extensions = availableExtensions.stream()
                    .map(VkExtensionProperties::extensionNameString)
                    .collect(toSet());

            requiredExtensions.removeAll(extensions);

            return "Unsupported extensions: " + Arrays.toString(requiredExtensions.toArray());
        }
    }

    public static String debugString(PointerBuffer ppPhysicalDevices, Set<String> requiredExtensions, VkInstance instance) {
        try (MemoryStack stack = stackPush()) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("\n");

            for(int i = 0; i < ppPhysicalDevices.capacity();i++) {
                VkPhysicalDevice device = new VkPhysicalDevice(ppPhysicalDevices.get(i), instance);

                VkPhysicalDeviceProperties deviceProperties = VkPhysicalDeviceProperties.callocStack(stack);
                vkGetPhysicalDeviceProperties(device, deviceProperties);

                DeviceInfo info = new DeviceInfo(device);

                stringBuilder.append(String.format("Device %d: ", i)).append(info.deviceName).append("\n");
                stringBuilder.append(info.unsupportedExtensions(requiredExtensions)).append("\n");

                DeviceManager.SurfaceProperties surfaceProperties = DeviceManager.querySurfaceProperties(device, stack);
                boolean swapChainAdequate = surfaceProperties.formats.hasRemaining() && surfaceProperties.presentModes.hasRemaining() ;
                stringBuilder.append("Swapchain supported: ").append(swapChainAdequate ? "true" : "false").append("\n");
            }

            return stringBuilder.toString();
        }
    }

    public boolean isDrawIndirectSupported() {
        return drawIndirectSupported;
    }

    //Added these to allow detecting GPU vendor, to allow handling vendor specific circumstances:
    // (e.g. such as in case we encounter a vendor specific driver bug)
    public boolean isAMD() { return vendorId == 0x1022; }
    public boolean isNvidia() { return vendorId == 0x10DE; }
    public boolean isIntel() { return vendorId == 0x8086; }
}
