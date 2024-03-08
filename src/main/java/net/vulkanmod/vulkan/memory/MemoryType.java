package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.DeviceManager;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public enum MemoryType {
    GPU_MEM(true, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT),

    BAR_MEM(true, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
//    HOST_MEM(Type.HOST_LOCAL, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, 0);

    final long maxSize;
    private final int flags;
    private long usedBytes;

    //requiredFlags: MemType MUST have this flag(s) to be used
    //optimalFlags: MemType IDEALLY has these flags for Optimal performance, but are not strictly required to have these flags to be used

    MemoryType(boolean useVRAM, int... optimalFlags) {

//        this.maxSize = maxSize;
//        this.resizableBAR = size > 0xD600000;

        /*requiredFlags | */
        int optimalFlagMask = 0;
        for (int optimalFlag : optimalFlags) {
            optimalFlagMask |= optimalFlag;
        }

        final int heapFlag = useVRAM ? VK_MEMORY_HEAP_DEVICE_LOCAL_BIT : 0;


        for (int currentFlagCount = optimalFlags.length- 1; currentFlagCount >= 0; currentFlagCount--) {

            for (VkMemoryType memoryType : DeviceManager.memoryProperties.memoryTypes()) {

                VkMemoryHeap memoryHeap = DeviceManager.memoryProperties.memoryHeaps(memoryType.heapIndex());


                final int queriedMemFlags = memoryType.propertyFlags();
                final int queriedFlagCount = Integer.bitCount(queriedMemFlags);

                //Not all drivers have DEVICE_LOCAL only memType, == checks will cause false negatives
                final boolean hasRequiredFlags = (queriedFlagCount >= currentFlagCount) & VUtil.checkUsage(optimalFlagMask, queriedMemFlags);
                final boolean hasRequiredHeapType = memoryHeap.flags() == heapFlag;
                if (hasRequiredFlags & hasRequiredHeapType) {
                    this.maxSize = memoryHeap.size();
                    this.flags = queriedMemFlags;

                    return;
                }
            }
            optimalFlagMask ^= optimalFlags[currentFlagCount]; //remove each Property bit, based on varargs priority ordering from right to left
        }

        throw new RuntimeException("Unsupported MemoryType: "+this.name() + ": Try updating your driver and/or Vulkan version");

//        VkMemoryType memoryType = DeviceManager.memoryProperties.memoryTypes(0);
//        VkMemoryHeap memoryHeap = DeviceManager.memoryProperties.memoryHeaps(0);
//        this.maxSize = memoryHeap.size();
//        this.flags = memoryType.propertyFlags();
//            return;
//
//

    }

//    private static boolean getVRAMHeaps(int heapFlag) {
//        return DeviceManager.memoryProperties.memoryHeaps().stream().anyMatch(vkMemoryHeap -> vkMemoryHeap.flags() == heapFlag);
//    }

    void createBuffer(Buffer buffer, int size)
    {


        final int usage = buffer.usage | (this.equals(BAR_MEM) ? 0 : VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT);

        MemoryManager.getInstance().createBuffer(buffer, size, usage, this.flags);
        this.usedBytes+=size;
    }

//    void addSubCopy(Buffer buffer, long bufferSize, ByteBuffer byteBuffer)
//    {
//        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
//
//        var a = new SubCopyCommand()
//        if(subCpyContiguous)
//    }
//
//    void executeSubCopy(Buffer srcBuffer, Buffer dstBuffer)
//    {
//
//    }
    void copyToBuffer(Buffer buffer, int bufferSize, ByteBuffer byteBuffer)
    {
         if(this.equals(GPU_MEM)){
             StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
             stagingBuffer.copyBuffer(bufferSize, byteBuffer);
             DeviceManager.getTransferQueue().copyBufferCmd(stagingBuffer.id, stagingBuffer.offset, buffer.getId(), buffer.getUsedBytes(), bufferSize);
         }
         else VUtil.memcpy(byteBuffer, buffer.data.getByteBuffer(0, buffer.bufferSize), bufferSize, buffer.getUsedBytes());
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
