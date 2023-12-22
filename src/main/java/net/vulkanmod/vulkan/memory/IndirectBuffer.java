package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.queue.CommandPool;

import java.nio.ByteBuffer;

import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;

public class IndirectBuffer extends Buffer {
    CommandPool.CommandBuffer commandBuffer;

    public IndirectBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT, type);
        this.createBuffer(size);
    }

    public void recordCopyCmd(ByteBuffer byteBuffer) {
        int size = byteBuffer.remaining();

        // Cache aligned buffer size for efficiency
        int alignedSize = Util.align(size, this.type.getAlignment());
        if (alignedSize > this.bufferSize - this.usedBytes) {
            int newSize = Math.max(alignedSize, this.bufferSize * 2); // Consider alternative resizing strategies
            resizeBuffer(newSize);
        }

        if (this.type.mappable()) {
            this.type.copyToBuffer(this, alignedSize, byteBuffer);
        } else {
            if (commandBuffer == null)
                commandBuffer = DeviceManager.getTransferQueue().beginCommands();

            StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
            stagingBuffer.recordCopyCmd(size, byteBuffer);

            TransferQueue.uploadBufferCmd(commandBuffer, stagingBuffer.id, stagingBuffer.offset, this.getId(), this.getUsedBytes(), size);
        }

        offset = usedBytes;
        usedBytes += alignedSize;
    }

    private void resizeBuffer() {
        MemoryManager.getInstance().addToFreeable(this);
        int newSize = this.bufferSize + (this.bufferSize >> 1);
        this.createBuffer(newSize);
        this.usedBytes = 0;
    }

    public void submitUploads() {
        if (commandBuffer == null)
            return;

        DeviceManager.getTransferQueue().submitCommands(commandBuffer);
        Synchronization.INSTANCE.addCommandBuffer(commandBuffer);
        commandBuffer = null;
    }

    //debug
    public ByteBuffer getByteBuffer() {
        return this.data.getByteBuffer(0, this.bufferSize);
    }
}
