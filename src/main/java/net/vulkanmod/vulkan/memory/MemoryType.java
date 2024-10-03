package net.vulkanmod.vulkan.memory;

import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

public abstract class MemoryType {
    final Type type;
    public final VkMemoryType vkMemoryType;
    public final VkMemoryHeap vkMemoryHeap;
    public final int memoryTypeIndex;

    MemoryType(Type type, VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap, int memoryTypeIndex) {
        this.type = type;
        this.vkMemoryType = vkMemoryType;
        this.vkMemoryHeap = vkMemoryHeap;
        this.memoryTypeIndex = memoryTypeIndex;
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
        HOST_LOCAL
    }
}
