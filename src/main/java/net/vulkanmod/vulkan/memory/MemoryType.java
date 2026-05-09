package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.memory.buffer.StagingBuffer;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public enum MemoryType {

    GPU_MEM(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT),
    BAR_MEM(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT),
    HOST_MEM(VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

    public final int heapIndex;
    public final long maxSize;
    public final int typeBits;
    public final int properties;

    MemoryType(int memPropertyBits) {

        int heapInfos = getMemType(memPropertyBits);

        int heapIndex = heapInfos & 255;
        int memoryTypeIndex = (heapInfos >> 8) & 255;
        int propertyFlags = (heapInfos >> 16);

        this.heapIndex = memoryTypeIndex;
        this.maxSize = DeviceManager.memoryProperties.memoryHeaps(heapIndex).size();
        this.typeBits = 1 << memoryTypeIndex;
        this.properties = propertyFlags;
    }

    private int getMemType(int property) {
        // Spec Guarantees Availability of DEVICE_LOCAL || (HOST_VISIBLE | HOST_COHERENT)
        for (int memoryTypeIndex = 0; memoryTypeIndex < DeviceManager.memoryProperties.memoryTypeCount(); ++memoryTypeIndex) {

            VkMemoryType memoryType = DeviceManager.memoryProperties.memoryTypes(memoryTypeIndex);

            if ((memoryType.propertyFlags() & property) == property) {
                return property << 16 | memoryTypeIndex << 8 | memoryType.heapIndex();
            }
        }

        // Get highest performing Host visible mem
        // Spec states that mem types are ordered based on performance
        VkMemoryType memoryType = DeviceManager.memoryProperties.memoryTypes(0);

        return memoryType.propertyFlags() << 16 | memoryType.heapIndex();
    }

    public void createBuffer(Buffer buffer, long size) {
        MemoryManager.getInstance().createBuffer(buffer, size,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | buffer.usage, properties
        );
    }

    public void copyToBuffer(Buffer buffer, ByteBuffer src, long size, long srcOffset, long dstOffset) {
        if(!this.mappable()) {
            StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
            stagingBuffer.copyBuffer((int) size, src);

            DeviceManager.getTransferQueue().copyBufferCmd(stagingBuffer.getId(), stagingBuffer.getOffset(), buffer.getId(), dstOffset, size);
        }
        else {
            VUtil.memcpy(src, buffer, size, srcOffset, dstOffset);
        }
    }

    public void copyBuffer(Buffer src, Buffer dst) {
        if (dst.getBufferSize() < src.getBufferSize()) {
            throw new IllegalArgumentException("dst size is less than src size.");
        }

        DeviceManager.getTransferQueue().copyBufferCmd(src.getId(), 0, dst.getId(), 0, src.getBufferSize());
    }

    public void copyFromBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer){
        if(this.mappable())
        {
            MemoryUtil.memCopy(buffer.getDataPtr(), MemoryUtil.memAddress(byteBuffer), bufferSize);
            VUtil.memcpy(buffer, byteBuffer, bufferSize);
        }
        /* else {
            //TODO:
        }*/

    }

    public boolean mappable() {
        return this != GPU_MEM;
    }
}
