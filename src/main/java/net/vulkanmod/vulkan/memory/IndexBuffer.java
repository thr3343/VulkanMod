package net.vulkanmod.vulkan.memory;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;

public class IndexBuffer extends Buffer {

    public IndexType indexType = IndexType.SHORT;

    public IndexBuffer(int size) {
        this(size, MemoryTypes.HOST_MEM);
    }

    public IndexBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, type);
        this.createBuffer(size);
    }

    public void copyBuffer(ByteBuffer buffer) {
        int size = buffer.remaining();

        //debug
//        this.idxs = new short[buffer.remaining() / 2];
//        buffer.asShortBuffer().get(idxs);

        // Cached aligned size
        int alignedSize = Util.align(size * indexType.size, type.getAlignment());
        if (alignedSize > this.bufferSize - this.usedBytes) {
            resizeBuffer(alignedSize + usedBytes);
        } else {
            this.type.copyToBuffer(this, alignedSize, buffer);
            offset = usedBytes;
            usedBytes += alignedSize;
        }
    }

    private void resizeBuffer(int newSize) {
        MemoryManager.getInstance().addToFreeable(this);
        this.createBuffer(newSize);

//        System.out.println("resized vertexBuffer to: " + newSize);
    }

    public enum IndexType {
        SHORT(2),
        INT(4);

        public final int size;

        IndexType(int size) {
            this.size = size;
        }
    }

}
