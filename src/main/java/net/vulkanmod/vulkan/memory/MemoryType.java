package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.memory.buffer.Buffer;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

public abstract class MemoryType {
    final Type type;
    public final VkMemoryType vkMemoryType;
    public final long maxSize;
    public final int typeBits;

    MemoryType(Type type, VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap, int memoryTypeIndex) {
        this.type = type;
        this.vkMemoryType = vkMemoryType;
        this.maxSize = vkMemoryHeap.size();
        this.typeBits = 1 << memoryTypeIndex;
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
