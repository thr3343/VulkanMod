package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.memory.buffer.Buffer;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

public abstract class MemoryType {
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

    public abstract void createBuffer(Buffer buffer, long size);

    public abstract void copyToBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer);

    public abstract void copyFromBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer);

    public abstract boolean mappable();

    public Type getType() {
       return this.type;
    }

    public enum Type {
        DEVICE_LOCAL,
        BAR_LOCAL,
        HOST_LOCAL
    }
}
