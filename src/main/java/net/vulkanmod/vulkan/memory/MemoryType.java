package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.DeviceManager;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public enum MemoryType {
    GPU_MEM(Type.DEVICE_LOCAL, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, VK_MEMORY_HEAP_DEVICE_LOCAL_BIT),

    BAR_MEM(Type.STAGING_LOCAL, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, VK_MEMORY_HEAP_DEVICE_LOCAL_BIT);
//    HOST_MEM(Type.HOST_LOCAL, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, 0);

    private final Type type;
    final long maxSize;
    private final int flags;
    private long usedBytes;

    MemoryType(Type type, int preferredFlags, int heapFlag) {
        this.type = type;
//        this.maxSize = maxSize;
//        this.resizableBAR = size > 0xD600000;


        if(DeviceManager.memoryProperties.memoryTypeCount()==1)
        {
            VkMemoryType memoryType = DeviceManager.memoryProperties.memoryTypes(0);
            VkMemoryHeap memoryHeap = DeviceManager.memoryProperties.memoryHeaps(0);
            this.maxSize = memoryHeap.size();
            this.flags = memoryType.propertyFlags();
            return;
        }

        for(VkMemoryType memoryType : DeviceManager.memoryProperties.memoryTypes()) {

            VkMemoryHeap memoryHeap = DeviceManager.memoryProperties.memoryHeaps(memoryType.heapIndex());


            final int flag = memoryType.propertyFlags();
            if (flag == preferredFlags&&memoryHeap.flags()==heapFlag) {
                this.maxSize = memoryHeap.size();
                this.flags = flag;

                return;
            }
        }

        throw new RuntimeException();

    }

    void createBuffer(Buffer buffer, int size)
    {


        final int usage = switch (this.type)
        {
            case DEVICE_LOCAL, HOST_LOCAL -> buffer.usage|VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
            case STAGING_LOCAL -> buffer.usage;
        };
        MemoryManager.getInstance().createBuffer(buffer, size,
                usage,
                this.flags);
        this.usedBytes+=size;
    }
    void copyToBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer)
    {
         if(this.equals(GPU_MEM)){
             StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
             stagingBuffer.copyBuffer((int) bufferSize, byteBuffer);
             DeviceManager.getTransferQueue().copyBufferCmd(stagingBuffer.id, stagingBuffer.offset, buffer.getId(), buffer.getUsedBytes(), bufferSize);
         }
         else VUtil.memcpy(byteBuffer, buffer.data.getByteBuffer(0, (int) buffer.bufferSize), (int) bufferSize, buffer.getUsedBytes());
    }


    void freeBuffer(Buffer buffer)
    {
        MemoryManager.getInstance().addToFreeable(buffer);
        this.usedBytes-=buffer.bufferSize;
    }


//    abstract void copyToBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer);
//    abstract void copyFromBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer);

    /**
     * Replace data from byte 0
     */
    public void uploadBuffer(Buffer buffer, ByteBuffer byteBuffer, int dstOffset)
    {
      if(this.equals(GPU_MEM))
      {
          int bufferSize = byteBuffer.remaining();
          StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
          stagingBuffer.copyBuffer(bufferSize, byteBuffer);

          DeviceManager.getTransferQueue().copyBufferCmd(stagingBuffer.id, stagingBuffer.offset, buffer.getId(), dstOffset, bufferSize);
      }

      else VUtil.memcpy(byteBuffer, buffer.data.getByteBuffer(0, (int) buffer.bufferSize), byteBuffer.remaining(), dstOffset);
    }

    final boolean mappable() { return this.ordinal()!=0; }

    public int usedBytes() { return (int) (this.usedBytes >> 20); }

    public long getMaxSize() { return maxSize >> 20; }

    public final Type getType() {
       return this.type;
    }

    public enum Type {
        DEVICE_LOCAL,
        STAGING_LOCAL,
        HOST_LOCAL
    }
}
