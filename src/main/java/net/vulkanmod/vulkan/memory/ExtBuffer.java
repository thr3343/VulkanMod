package net.vulkanmod.vulkan.memory;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class ExtBuffer extends Buffer{
    public ExtBuffer(int usage, MemoryType type) {
        super(usage, type);
    }


    public void createBufferExt(ByteBuffer mappedBuffer) {

        MemoryManager.getInstance().importBuffer(this, mappedBuffer, this.usage, 0/*MemoryTypes.HOST_MEM.flags*/);
    }
}
