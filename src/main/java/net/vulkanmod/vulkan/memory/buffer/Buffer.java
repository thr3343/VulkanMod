package net.vulkanmod.vulkan.memory.buffer;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryType;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;

public abstract class Buffer {
    public final MemoryType type;
    public final int usage;

    protected long id;
    protected long allocation;

    protected long bufferSize;
    protected long usedBytes;
    protected long offset;

    protected long dataPtr;
    protected long pAddress;

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
        boolean useBDA = (this.usage & (VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT)) != 0;
        this.pAddress = useBDA ? this.getBufferDeviceAddress() : 0;
    }

    protected long getBufferDeviceAddress() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferDeviceAddressInfo deviceAddressInfo = VkBufferDeviceAddressInfo.calloc(stack)
                    .sType$Default().buffer(this.id);

            return VK12.vkGetBufferDeviceAddress(Vulkan.getVkDevice(), deviceAddressInfo);
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

    public void setBufferSize(long size) {
        this.bufferSize = size;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setAllocation(long allocation) {
        this.allocation = allocation;
    }

    public BufferInfo getBufferInfo() {
        return new BufferInfo(this.id, this.allocation, this.bufferSize, this.type.getType());
    }

    public long getBDA() {
        return this.pAddress;
    }

    public record BufferInfo(long id, long allocation, long bufferSize, MemoryType.Type type) {

    }
}
