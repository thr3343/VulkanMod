package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.memory.buffer.StagingBuffer;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class MemoryTypes {
    public static MemoryType GPU_MEM;
    public static MemoryType BAR_MEM;
    public static MemoryType HOST_MEM;

    public static void createMemoryTypes() {

        for (int memoryTypeIndex = 0; memoryTypeIndex < DeviceManager.memoryProperties.memoryTypeCount(); ++memoryTypeIndex) {
            VkMemoryType memoryType = DeviceManager.memoryProperties.memoryTypes(memoryTypeIndex);
            VkMemoryHeap heap = DeviceManager.memoryProperties.memoryHeaps(memoryType.heapIndex());
            int propertyFlags = memoryType.propertyFlags();

            if (propertyFlags == VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) {
                GPU_MEM = new MemoryType(MemoryType.Type.DEVICE_LOCAL, memoryType, heap, memoryTypeIndex, propertyFlags);
            }

            if (propertyFlags == (VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) {
                BAR_MEM = new MemoryType(MemoryType.Type.BAR_LOCAL, memoryType, heap, memoryTypeIndex, propertyFlags);
            }

            if (propertyFlags == (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) {
                HOST_MEM = new MemoryType(MemoryType.Type.HOST_LOCAL, memoryType, heap, memoryTypeIndex, propertyFlags);
            }
        }

        if (GPU_MEM != null && BAR_MEM != null && HOST_MEM != null)
            return;

        // Could not find 1 or more MemoryTypes, need to use fallback
        for (int memoryTypeIndex = 0; memoryTypeIndex < DeviceManager.memoryProperties.memoryTypeCount(); ++memoryTypeIndex) {
            VkMemoryType memoryType = DeviceManager.memoryProperties.memoryTypes(memoryTypeIndex);
            VkMemoryHeap heap = DeviceManager.memoryProperties.memoryHeaps(memoryType.heapIndex());

            // GPU mappable memory
            if ((memoryType.propertyFlags() & (VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)) == (VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)) {
                GPU_MEM = BAR_MEM = new MemoryType(MemoryType.Type.BAR_LOCAL, memoryType, heap, memoryTypeIndex, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
            }

            if ((memoryType.propertyFlags() & (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) == (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) {
                HOST_MEM = new MemoryType(MemoryType.Type.HOST_LOCAL, memoryType, heap, memoryTypeIndex, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            }

            if (GPU_MEM != null && BAR_MEM != null && HOST_MEM != null)
                return;
        }

        // Could not find device memory, fallback to host memory
        GPU_MEM = BAR_MEM = HOST_MEM;
    }
}
