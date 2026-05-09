package net.vulkanmod.vulkan.memory.buffer;

import net.vulkanmod.render.chunk.buffer.UploadManager;
import net.vulkanmod.render.chunk.util.Util;
import net.vulkanmod.render.texture.ImageUploadHelper;
import net.vulkanmod.vulkan.memory.MemoryType;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.system.libc.LibCString.nmemcpy;
import static org.lwjgl.vulkan.VK10.*;

public class StagingBuffer extends Buffer {

    public StagingBuffer(String stagingBuffer, long defaultSize, MemoryType type1) {
        this(defaultSize, stagingBuffer, type1);
    }

    public StagingBuffer(long size, String stagingBuffer, MemoryType type1) {
        super(stagingBuffer, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, type1);
        this.createBuffer(size);
    }

    public void copyBuffer(int size, ByteBuffer byteBuffer) {
        this.copyBuffer(size, MemoryUtil.memAddress(byteBuffer));
    }

    public void copyBuffer(int size, long scrPtr) {
        if (size > this.bufferSize) {
            throw new IllegalArgumentException("Upload size is greater than staging buffer size.");
        }

        if (size > this.bufferSize - this.usedBytes) {
            submitUploads();
        }

        nmemcpy(this.dataPtr + this.usedBytes, scrPtr, size);

        this.offset = this.usedBytes;
        this.usedBytes += size;
    }

    public void align(int alignment) {
        long alignedOffset = Util.align(usedBytes, alignment);

        if (alignedOffset > this.bufferSize) {
            submitUploads();
            alignedOffset = 0;
        }

        this.usedBytes = alignedOffset;
    }

    private void submitUploads() {
        // Submit all recorded uploads before resetting the buffer
        // (deferring waits to submit barrier at end frame)

        if (this.type == MemoryType.BAR_MEM) {
            UploadManager.INSTANCE.submitUploads();
        }
        else ImageUploadHelper.INSTANCE.submitCommands();

        this.reset();
    }
}
