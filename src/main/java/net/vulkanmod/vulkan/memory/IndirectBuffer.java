package net.vulkanmod.vulkan.memory;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.TransferQueue;
import net.vulkanmod.vulkan.util.VUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;

public class IndirectBuffer extends Buffer {
    private static final int size_t = 20*512;
    CommandPool.CommandBuffer commandBuffer;

    private final Int2IntArrayMap baseOffsets = new Int2IntArrayMap(8);
    private final IntArrayFIFOQueue freeBaseOffsets = new IntArrayFIFOQueue(128);

    public IndirectBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT, type);
        this.createBuffer(size);

        for(int i =0 ;i<size; i+=size_t)
        {
            freeBaseOffsets.enqueue(i);
        }
    }

    public void recordCopyCmd(ByteBuffer byteBuffer, int index) {
        int size = byteBuffer.remaining();

        int pOffset = this.getBaseOffsetChecked(index);

        if(size > this.bufferSize - this.usedBytes) {
            resizeBuffer();
        }

        if(commandBuffer == null)
            commandBuffer = DeviceManager.getTransferQueue().beginCommands();

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
        stagingBuffer.copyBuffer(size, byteBuffer);

        TransferQueue.uploadBufferCmd(commandBuffer.getHandle(), stagingBuffer.id, stagingBuffer.offset, this.getId(), pOffset, size);

    }

    private int getBaseOffsetChecked(int index) {
        return this.baseOffsets.computeIfAbsent(index, t -> {


            usedBytes += size_t;

            return freeBaseOffsets.dequeueInt();

        });
    }

    public void freeOffset(int index)
    {
        if(this.baseOffsets.containsKey(index))
        {
            freeBaseOffsets.enqueue(baseOffsets.remove(index));
            usedBytes -= size_t;

        }
    }

    private void resizeBuffer() {
        MemoryManager.getInstance().addToFreeable(this);
        int newSize = this.bufferSize + (this.bufferSize >> 1);
        this.createBuffer(newSize);
//        this.usedBytes = 0;
    }

    public void submitUploads() {
        if(commandBuffer == null)
            return;

        DeviceManager.getTransferQueue().submitCommands(commandBuffer);
        Synchronization.INSTANCE.addCommandBuffer(commandBuffer);
        commandBuffer = null;
    }

    //debug
    public ByteBuffer getByteBuffer() {
        return this.data.getByteBuffer(0, this.bufferSize);
    }

    public int getBaseOffset(int index) {
        return this.baseOffsets.get(index);
    }
}
