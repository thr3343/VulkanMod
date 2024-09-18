package net.vulkanmod.vulkan.memory;

import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

public abstract class MemoryType {
    final Type type;
    final int heapIndex;
    final long maxSize;

    MemoryType(Type type, VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap) {
        this.type = type;
        this.heapIndex = vkMemoryType.heapIndex();
        this.maxSize = vkMemoryHeap.size();
    }

    abstract void createBuffer(Buffer buffer, int size);

    abstract void copyToBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer);

    abstract void copyFromBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer);

    abstract boolean mappable();

    public Type getType() {
       return this.type;
    }

    public enum Type {
        DEVICE_LOCAL,
        BAR_LOCAL,
        HOST_LOCAL
    }
}
