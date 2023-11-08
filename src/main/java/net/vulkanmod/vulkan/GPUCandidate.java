package net.vulkanmod.vulkan;

import org.lwjgl.vulkan.VkPhysicalDevice;

public record GPUCandidate(VkPhysicalDevice physicalDevice, String deviceName, int deviceType) {
}
