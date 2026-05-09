package net.vulkanmod.vulkan.memory.buffer;

import net.vulkanmod.vulkan.memory.MemoryType;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

public class VertexBuffer extends Buffer {

    public VertexBuffer(int size) {
        this(size, MemoryType.HOST_MEM);
    }

    public VertexBuffer(int size, MemoryType type) {
        super("Vertex buffer", VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, type);
        this.createBuffer(size);
    }

}
