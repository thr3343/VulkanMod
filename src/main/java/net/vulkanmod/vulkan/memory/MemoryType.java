package net.vulkanmod.vulkan.memory;

import java.nio.ByteBuffer;

public abstract class MemoryType {
    protected Type type;
    private final long propertyFlags;

    public MemoryType(Type type, long propertyFlags) {
        this.type = type;
        this.propertyFlags = propertyFlags;
    }

    abstract void createBuffer(Buffer buffer, int size);
    abstract void copyToBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer);


//    abstract void copyToBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer);
    abstract void copyFromBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer);

    /**
     * Replace data from byte 0
     */
    abstract void uploadBuffer(Buffer buffer, ByteBuffer byteBuffer);

    abstract boolean mappable();

    public Type getType() {
       return this.type;
    }

    public enum Type {
        DEVICE_LOCAL(0),
        HOST_LOCAL(1);

        private final int index;

        Type(int index) {

            this.index = index;
        }
    }
}
