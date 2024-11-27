package net.vulkanmod.vulkan.memory;

public abstract class Buffer {
    protected long id;
    protected long allocation;

    protected long bufferSize;
    protected long usedBytes;
    protected long offset;

    protected MemoryType type;
    protected int usage;

    protected long dataPtr;

    protected Buffer(int usage, MemoryType type) {
        //TODO: check usage
        this.usage = usage;
        this.type = type;

    }

    protected void createBuffer(long bufferSize) {
        this.type.createBuffer(this, bufferSize);

        if (this.type.mappable()) {
            this.dataPtr = MemoryManager.getInstance().Map(this.allocation).get(0);
        }
    }

    public void scheduleFree() {
        MemoryManager.getInstance().addToFreeable(this);
    }

    public void reset() {
        usedBytes = 0;
    }

    public long getAllocation() {
        return allocation;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getOffset() {
        return offset;
    }

    public long getId() {
        return id;
    }

    public long getBufferSize() {
        return bufferSize;
    }

    public long getDataPtr() {
        return dataPtr;
    }

    protected void setBufferSize(long size) {
        this.bufferSize = size;
    }

    protected void setId(long id) {
        this.id = id;
    }

    protected void setAllocation(long allocation) {
        this.allocation = allocation;
    }

    public BufferInfo getBufferInfo() {
        return new BufferInfo(this.id, this.allocation, this.bufferSize, this.type.getType());
    }

    public record BufferInfo(long id, long allocation, long bufferSize, MemoryType.Type type) {

    }
}
