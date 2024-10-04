package net.vulkanmod.vulkan.memory;

import java.nio.ByteBuffer;

public class ExtBuffer extends Buffer{
    public ExtBuffer(int usage, MemoryType type) {
        super(usage, type);
    }


    public void createBufferExt(ByteBuffer mappedBuffer, int width, int height, int memoryOffset) {

        MemoryManager.getInstance().importBuffer(this, mappedBuffer, this.usage, 0, width, height, memoryOffset/*MemoryTypes.HOST_MEM.flags*/);
    }
}
