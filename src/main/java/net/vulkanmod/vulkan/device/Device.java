package net.vulkanmod.vulkan.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.lwjgl.glfw.GLFW.GLFW_PLATFORM_WIN32;
import static org.lwjgl.glfw.GLFW.glfwGetPlatform;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

public class Device {
    final VkPhysicalDevice physicalDevice;
    final VkPhysicalDeviceProperties2 properties;
    final VkPhysicalDeviceVulkan11Properties vk11Properties;
    final VkPhysicalDeviceMultiDrawPropertiesEXT multiDrawPropertiesEXT;
    final VkPhysicalDeviceDriverProperties driverProperties;
    final VkExtensionProperties.Buffer extensionProperties;
    public final VkPhysicalDeviceFeatures2 availableFeatures;
    public final VkPhysicalDeviceVulkan11Features availableFeatures11;
    public final VkPhysicalDeviceVulkan12Features availableFeatures12;

    private final int vendorId;
    public final String vendorIdString;
    public final String deviceName;
    public final String driverVersion;
    public final String vkVersion;
    public final int deviceType;

    private boolean drawIndirectSupported;
    private boolean directMultiDrawSupported;

    public Device(VkPhysicalDevice physicalDevice) {
        this.physicalDevice = physicalDevice;

//        this.properties = VkPhysicalDeviceProperties.malloc();
//        vkGetPhysicalDeviceProperties(physicalDevice, properties);
//
//        this.vk11Properties = VkPhysicalDeviceVulkan11Properties.calloc();
//        vkGetPhysicalDeviceProperties2();

        this.extensionProperties = getAvailableExtension(physicalDevice);

        this.properties = VkPhysicalDeviceProperties2.calloc().sType$Default();
        this.vk11Properties = VkPhysicalDeviceVulkan11Properties.calloc().sType$Default();
        this.driverProperties = VkPhysicalDeviceDriverProperties.calloc().sType$Default();
        this.multiDrawPropertiesEXT = VkPhysicalDeviceMultiDrawPropertiesEXT.calloc().sType$Default();
        this.properties.pNext(this.driverProperties);
        this.properties.pNext(this.vk11Properties);
        if (this.hasExtension("VK_EXT_multi_draw")) {
            this.properties.pNext(this.multiDrawPropertiesEXT);
            this.directMultiDrawSupported = true;
        }

        VK12.vkGetPhysicalDeviceProperties2(physicalDevice, this.properties);

        this.vendorId = properties.properties().vendorID();
        this.vendorIdString = decodeVendor(properties.properties().vendorID());
        this.deviceName = properties.properties().deviceNameString();
        this.driverVersion = decodeDvrVersion(properties.properties().driverVersion(), properties.properties().vendorID());
        this.vkVersion = decDefVersion(properties.properties().apiVersion());
        this.deviceType = properties.properties().deviceType();

        this.availableFeatures = VkPhysicalDeviceFeatures2.calloc().sType$Default();

        this.availableFeatures11 = VkPhysicalDeviceVulkan11Features.malloc().sType$Default();
        this.availableFeatures.pNext(this.availableFeatures11);

        this.availableFeatures12 = VkPhysicalDeviceVulkan12Features.malloc().sType$Default();
        this.availableFeatures.pNext(this.availableFeatures12);

        vkGetPhysicalDeviceFeatures2(this.physicalDevice, this.availableFeatures);

        if (this.availableFeatures.features().multiDrawIndirect() && this.availableFeatures11.shaderDrawParameters())
            this.drawIndirectSupported = true;

    }

    public boolean hasExtension(String s) {
        return this.extensionProperties.stream().anyMatch(pr -> pr.extensionNameString().equals(s));
    }

    public Set<String> getUnsupportedExtensions(Set<String> requiredExtensions) {
        try (MemoryStack stack = stackPush()) {

            IntBuffer extensionCount = stack.ints(0);

            vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, extensionCount, null);

            VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.malloc(extensionCount.get(0), stack);

            vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, extensionCount, availableExtensions);

            Set<String> extensions = availableExtensions.stream()
                                                        .map(VkExtensionProperties::extensionNameString)
                                                        .collect(toSet());

            Set<String> unsupportedExtensions = new HashSet<>(requiredExtensions);
            unsupportedExtensions.removeAll(extensions);

            return unsupportedExtensions;
        }
    }

    public boolean isDrawIndirectSupported() {
        return drawIndirectSupported;
    }

    public boolean hasDirectMultiDraw() {
        return directMultiDrawSupported;
    }

    // Added these to allow detecting GPU vendor, to allow handling vendor specific circumstances:
    // (e.g. such as in case we encounter a vendor specific driver bug)
    public boolean isAMD() {
        return vendorId == 0x1022 || vendorId == 0x1002;
    }

    public boolean isNvidia() {
        return vendorId == 0x10DE;
    }

    public boolean isIntel() {
        return vendorId == 0x8086;
    }

    public VkPhysicalDeviceProperties properties() {
        return this.properties.properties();
    }

    public VkPhysicalDeviceVulkan11Properties getVk11Properties() {
        return vk11Properties;
    }

    public VkPhysicalDeviceMultiDrawPropertiesEXT getMultiDrawPropertiesEXT() {
        return multiDrawPropertiesEXT;
    }

    public String driverInfo() {
        return String.format(
                Locale.ROOT, "%s %s %s", this.vkVersion, this.driverProperties.driverNameString(), this.driverProperties.driverInfoString()
        );
    }

    private static String decodeVendor(int i) {
        return switch (i) {
            case (0x10DE) -> "Nvidia";
            case (0x1022), (0x1002) -> "AMD"; // AMD has two deviceIds, apparently
            case (0x8086) -> "Intel";
            case (0x1010) -> "Imagination Technologies";
            case (0x13B5) -> "ARM";
            case (0x5143) -> "Qualcomm";
            case (0x106B) -> "Apple";
            case (0x14E4) -> "Broadcom";
            case (0x1AE0) -> "Google"; // Not sure about this, SwiftShader devices have this id
            case (0x10005) -> "Mesa"; // Honeykrisp on Apple devices has this vendorId for some reason
            default -> "undef"; //Either AMD or Unknown Driver version/vendor and.or Encoding Scheme
        };
    }

    // Should Work with AMD: https://gpuopen.com/learn/decoding-radeon-vulkan-versions/

    static String decDefVersion(int v) {
        return VK_VERSION_MAJOR(v) + "." + VK_VERSION_MINOR(v) + "." + VK_VERSION_PATCH(v);
    }

    // 0x10DE = Nvidia: https://pcisig.com/membership/member-companies?combine=Nvidia
    // https://registry.khronos.org/vulkan/specs/1.3-extensions/man/html/VkPhysicalDeviceProperties.html
    // this should work with Nvidia + AMD but is not guaranteed to work with intel drivers in Windows and more obscure/Exotic Drivers/vendors
    private static String decodeDvrVersion(int v, int i) {
        return switch (i) {
            case (0x10DE) -> decodeNvidia(v); //Nvidia
            case (0x1022), (0x1002) -> decDefVersion(v); //AMD
            case (0x8086) -> decIntelVersion(v); //Intel
            default -> decDefVersion(v); //Either AMD or Unknown Driver Encoding Scheme
        };
    }

    // Source: https://www.intel.com/content/www/us/en/support/articles/000005654/graphics.html
    // Won't Work with older Drivers (15.45 And.or older)
    // May not work as this uses Guess work+Assumptions
    private static String decIntelVersion(int v) {
        return (glfwGetPlatform() == GLFW_PLATFORM_WIN32) ? (v >>> 14) + "." + (v & 0x3fff) : decDefVersion(v);
    }


    private static String decodeNvidia(int v) {
        return (v >>> 22 & 0x3FF) + "." + (v >>> 14 & 0xff) + "." + (v >>> 6 & 0xff) + "." + (v & 0xff);
    }

    public static VkExtensionProperties.Buffer getAvailableExtension(VkPhysicalDevice device) {
        int[] extensionCount = new int[1];
        vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, null);

        VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.malloc(extensionCount[0]);
        vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, availableExtensions);

        return availableExtensions;
    }

}
