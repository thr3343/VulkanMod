package net.vulkanmod.vulkan.memory;

public class ExtBuffer extends Buffer{

    public ExtBuffer(long bufferPtr, int size, int usage, MemoryType type) {
        super(usage, type);
        this.createBufferExt(bufferPtr, size, 0);

    }


    private void createBufferExt(long mappedBuffer, int size, int memoryOffset) {

        MemoryManager.getInstance().importBuffer(this, mappedBuffer, size, this.usage, 0, memoryOffset/*MemoryTypes.HOST_MEM.flags*/);
    }
}
