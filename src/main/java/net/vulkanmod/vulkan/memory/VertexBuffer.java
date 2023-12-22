package net.vulkanmod.vulkan.memory;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

public class VertexBuffer extends Buffer {

    public VertexBuffer(int size) {
        this(size, MemoryTypes.HOST_MEM);
    }

    public VertexBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, type);
        this.createBuffer(size);

        // Cache the offset and used bytes for efficiency
        this.offset = 0;
        this.usedBytes = 0;
    }

    public void copyToVertexBuffer(long vertexSize, long vertexCount, ByteBuffer byteBuffer) {
        int bufferSize = (int) (vertexSize * vertexCount);

        // Check if the buffer needs resizing
        if (bufferSize > this.bufferSize - this.usedBytes) {

            // Resize the buffer using the new size
            int newSize = Math.max(bufferSize, this.bufferSize * 2);
            resizeBuffer(newSize);
        }

        // Copy the data to the buffer
        this.type.copyToBuffer(this, bufferSize, byteBuffer);

        // Update the offset and used bytes
        this.offset += bufferSize;
        this.usedBytes += bufferSize;
    }

    private void resizeBuffer(int newSize) {
        MemoryManager.getInstance().addToFreeable(this);
        this.createBuffer(newSize);

//        System.out.println("resized vertexBuffer to: " + newSize);
    }
}
