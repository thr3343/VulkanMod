package net.vulkanmod.vulkan.memory;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class ExtBuffer extends Buffer{
    public ExtBuffer(int usage, MemoryTypes type) {
        super(usage, type);
    }


    public void createBufferExt(ByteBuffer bufferSize) {
        this.data= MemoryUtil.memAllocPointer(1);
        //Ignoring Budgeting+size checks as these are temp Buffers for steutemp setu[.uplaods e.g.
        this.data.put(0, bufferSize); //recycle pData for the File handle/Contents e.g.

        MemoryManager.getInstance().createBuffer(this, bufferSize.capacity(), usage, this.type.flags);
    }

    @Override
    public void freeBuffer() {
        super.freeBuffer();
    }
}
