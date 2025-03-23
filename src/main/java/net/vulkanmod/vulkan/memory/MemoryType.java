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

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

public class MemoryType {
    final Type type;
    public final int heapIndex;
    public final long maxSize;
    public final int typeBits;
    public final int properties;

    MemoryType(Type type, VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap, int memoryTypeIndex, int properties) {
        this.type = type;
        this.heapIndex = vkMemoryType.heapIndex();
        this.maxSize = vkMemoryHeap.size();
        this.typeBits = 1 << memoryTypeIndex;
        this.properties = properties;
    }

    public void createBuffer(Buffer buffer, long size) {
        MemoryManager.getInstance().createBuffer(buffer, size,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | buffer.usage
        );
    }

    public void copyToBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer)
    {
       if(!this.mappable()) {
           StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
           stagingBuffer.copyBuffer((int) bufferSize, byteBuffer);

           DeviceManager.getTransferQueue().copyBufferCmd(stagingBuffer.getId(), stagingBuffer.getOffset(), buffer.getId(), buffer.getUsedBytes(), bufferSize);
       }
       else {
           VUtil.memcpy(byteBuffer, buffer, bufferSize);
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
        return this.type != Type.DEVICE_LOCAL;
    }

    public Type getType() {
       return this.type;
    }

    public enum Type {
        DEVICE_LOCAL,
        BAR_LOCAL,
        HOST_LOCAL
    }
}
