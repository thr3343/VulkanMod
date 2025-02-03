package net.vulkanmod.vulkan.memory.buffer;

import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryType;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class IndexBuffer extends Buffer {

    public IndexType indexType;

    public IndexBuffer(int size, MemoryType type) {
        this(size, type, IndexType.UINT16);
    }

    public IndexBuffer(int size, MemoryType type, IndexType indexType) {
        super(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, type);
        this.indexType = indexType;

        this.createBuffer(size);
    }

    public void copyBuffer(ByteBuffer byteBuffer) {
        int size = byteBuffer.remaining();

        if(size > this.bufferSize - this.usedBytes) {
            resizeBuffer((this.bufferSize + size) * 2);
        }

        this.type.copyToBuffer(this, size, byteBuffer);
        offset = usedBytes;
        usedBytes += size;
    }

    private void resizeBuffer(long newSize) {
        MemoryManager.getInstance().addToFreeable(this);
        this.createBuffer(newSize);
    }

    public enum IndexType {
        UINT16(2, VK_INDEX_TYPE_UINT16),
        UINT32(4, VK_INDEX_TYPE_UINT32);

        public final int size;
        public final int type;

        IndexType(int size, int type) {
            this.size = size;
            this.type = type;
        }
    }


}
