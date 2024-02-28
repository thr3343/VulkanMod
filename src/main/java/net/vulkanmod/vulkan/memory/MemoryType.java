package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.DeviceManager;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public enum MemoryType {
    GPU_MEM(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, true),

    BAR_MEM(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, true);
//    HOST_MEM(Type.HOST_LOCAL, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, 0);

    final long maxSize;
    private final int flags;
    private long usedBytes;

    MemoryType(int preferredFlags, boolean useVRAM) {

//        this.maxSize = maxSize;
//        this.resizableBAR = size > 0xD600000;


        final int heapFlag = useVRAM ? VK_MEMORY_HEAP_DEVICE_LOCAL_BIT : 0;
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

        throw new RuntimeException("Unsupported MemoryType: "+this.name());

    }

    void createBuffer(Buffer buffer, int size)
    {


        final int usage = buffer.usage | (this.equals(BAR_MEM) ? 0 : VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT);

        MemoryManager.getInstance().createBuffer(buffer, size, usage, this.flags);
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

      else VUtil.memcpy(byteBuffer, buffer.data.getByteBuffer(0, buffer.bufferSize), byteBuffer.remaining(), dstOffset);
    }

    final boolean mappable() { return !this.equals(GPU_MEM); }

    public int usedBytes() { return (int) (this.usedBytes >> 20); }

    public int getMaxSize() { return (int) (maxSize >> 20); }

//    public int checkUsage(int usage) {
//        return (usage & this.flags) !=0 ? usage : this.flags;
//    }
}
