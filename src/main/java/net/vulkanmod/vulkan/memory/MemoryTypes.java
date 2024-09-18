package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class MemoryTypes {
    public static MemoryType GPU_MEM;
    public static MemoryType BAR_MEM;
    public static MemoryType HOST_MEM;

    private static final int DEVICE_LOCAL_HEAP = VK_MEMORY_HEAP_DEVICE_LOCAL_BIT; //Guarantees Device_Local Memory
    private static final int HOST_LOCAL_HEAP = 0; //Guarantees Host-Only memory

    public static void createMemoryTypes() {

        for (int i = 0; i < DeviceManager.memoryProperties.memoryTypeCount(); ++i) {
            VkMemoryType memoryType = DeviceManager.memoryProperties.memoryTypes(i);
            VkMemoryHeap heap = DeviceManager.memoryProperties.memoryHeaps(memoryType.heapIndex());

            //GPU only Memory
            if (memoryType.propertyFlags() == VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) {
                if(heap.flags() == DEVICE_LOCAL_HEAP)
                    GPU_MEM = new DeviceLocalMemory(memoryType, heap);

            }

            if (memoryType.propertyFlags() == (VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) {
                if(heap.flags() == DEVICE_LOCAL_HEAP)
                    BAR_MEM = new DeviceMappableMemory(memoryType, heap);

            }

            if (memoryType.propertyFlags() == (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) {
                if(heap.flags() == HOST_LOCAL_HEAP) //Prevent HOST_MEM from using the Device local heap by accident
                    HOST_MEM = new HostLocalUncachedMemory(memoryType, heap);
            }
        }

        if (GPU_MEM != null && BAR_MEM != null && HOST_MEM != null)
            return;

        // Could not find 1 or more MemoryTypes, need to use fallback
        //TODO: Refactor/repurpose as ReBAR mode
        // Can be exploited as iGPUs typically almost always have ReBAR as well
        for (int i = 0; i < DeviceManager.memoryProperties.memoryTypeCount(); ++i) {
            VkMemoryType memoryType = DeviceManager.memoryProperties.memoryTypes(i);
            VkMemoryHeap heap = DeviceManager.memoryProperties.memoryHeaps(memoryType.heapIndex());

            //TODO: Check this is correct
            if ((memoryType.propertyFlags() & (VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) == (VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
                GPU_MEM = new DeviceLocalMemory(memoryType, heap);
            }

            if ((memoryType.propertyFlags() & (VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)) == (VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)) {
                BAR_MEM = new DeviceMappableMemory(memoryType, heap);

            }

            if ((memoryType.propertyFlags() & (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) == (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) {
                HOST_MEM = new HostLocalFallbackMemory(memoryType, heap);
            }

            if (GPU_MEM != null && BAR_MEM != null && HOST_MEM != null)
                return;
        }

        // Could not find device memory, fallback to host memory
        GPU_MEM = HOST_MEM = BAR_MEM;
    }

    public static class DeviceLocalMemory extends MemoryType {

        DeviceLocalMemory(VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap) {
            super(Type.DEVICE_LOCAL, vkMemoryType, vkMemoryHeap);
        }

        @Override
        void createBuffer(Buffer buffer, int size) {
            MemoryManager.getInstance().createBuffer(buffer, size,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | buffer.usage,
                    VK_MEMORY_HEAP_DEVICE_LOCAL_BIT);
        }

        @Override
        void copyToBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer) {
            StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
            stagingBuffer.copyBuffer((int) bufferSize, byteBuffer);

            DeviceManager.getTransferQueue().copyBufferCmd(stagingBuffer.id, stagingBuffer.offset, buffer.getId(), buffer.getUsedBytes(), bufferSize);
        }

        @Override
        void copyFromBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer) {
            // TODO
        }

        public long copyBuffer(Buffer src, Buffer dst) {
            if (dst.bufferSize < src.bufferSize) {
                throw new IllegalArgumentException("dst size is less than src size.");
            }

            return DeviceManager.getTransferQueue().copyBufferCmd(src.getId(), 0, dst.getId(), 0, src.bufferSize);
        }

        @Override
        boolean mappable() {
            return false;
        }
    }

    static abstract class MappableMemory extends MemoryType {

        MappableMemory(Type type, VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap) {
            super(type, vkMemoryType, vkMemoryHeap);
        }

        @Override
        void copyToBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer) {
            VUtil.memcpy(byteBuffer, buffer.data.getByteBuffer(0, (int) buffer.bufferSize), (int) bufferSize, buffer.getUsedBytes());
        }

        @Override
        void copyFromBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer) {
            VUtil.memcpy(buffer.data.getByteBuffer(0, (int) buffer.bufferSize), byteBuffer, 0);
        }

        @Override
        boolean mappable() {
            return true;
        }
    }
    //Removing VK_MEMORY_PROPERTY_HOST_CACHED_BIT enables Write Combining optimizations, which can boost transfer speed and bandwidth
    //https://gpuopen.com/learn/using-d3d12-heap-type-gpu-upload/#recommendations
    static class HostLocalUncachedMemory extends MappableMemory {

        HostLocalUncachedMemory(VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap) {
            super(Type.HOST_LOCAL, vkMemoryType, vkMemoryHeap);
        }

        @Override
        void createBuffer(Buffer buffer, int size) {

            MemoryManager.getInstance().createBuffer(buffer, size,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | buffer.usage,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        }

        void copyToBuffer(Buffer buffer, long dstOffset, long bufferSize, ByteBuffer byteBuffer) {
            VUtil.memcpy(byteBuffer, buffer.data.getByteBuffer((int) 0, (int) buffer.bufferSize), (int) bufferSize, dstOffset);
        }

        void copyBuffer(Buffer src, Buffer dst) {
            VUtil.memcpy(src.data.getByteBuffer(0, src.bufferSize),
                    dst.data.getByteBuffer(0, dst.bufferSize), src.bufferSize, 0);

//            copyBufferCmd(src.getId(), 0, dst.getId(), 0, src.bufferSize);
        }
    }

    static class HostLocalFallbackMemory extends MappableMemory {

        HostLocalFallbackMemory(VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap) {
            super(Type.HOST_LOCAL, vkMemoryType, vkMemoryHeap);
        }

        @Override
        void createBuffer(Buffer buffer, int size) {
            MemoryManager.getInstance().createBuffer(buffer, size,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | buffer.usage,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        }
    }

    static class DeviceMappableMemory extends MappableMemory {

        DeviceMappableMemory(VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap) {
            super(Type.BAR_LOCAL, vkMemoryType, vkMemoryHeap);
        }

        @Override
        void createBuffer(Buffer buffer, int size) {
            MemoryManager.getInstance().createBuffer(buffer, size,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | buffer.usage,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        }
    }
}
