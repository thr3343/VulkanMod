package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;
import net.vulkanmod.vulkan.util.VUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;
import static net.vulkanmod.vulkan.util.VUtil.align;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;

public class UniformBuffers {

    private int bufferSize;
    private int usedBytes;

    public List<UniformBuffer> uniformBuffers;

    private final static int minOffset = (int) DeviceManager.deviceProperties.limits().minUniformBufferOffsetAlignment();
    private final int framesSize = Renderer.getFramesNum();

    CommandPool.CommandBuffer commandBuffer;

    public UniformBuffers(int size) {
        createUniformBuffers(size, MemoryType.BAR_MEM);
    }

    public UniformBuffers(int size, MemoryType memoryType) {
        createUniformBuffers(size, memoryType);
    }

    private void createUniformBuffers(int size, MemoryType memoryType) {
        this.bufferSize = size;

        uniformBuffers = new ArrayList<>(framesSize);

        for (int i = 0; i < framesSize; ++i) {
            uniformBuffers.add(new UniformBuffer(this.bufferSize, memoryType));
        }
    }

    public void uploadUBO(ByteBuffer buffer, int offset, int frame) {
        int size = buffer.remaining();
        int alignedSize = align(size, minOffset);
        if (alignedSize > this.bufferSize - this.usedBytes) {
            resizeBuffer((this.bufferSize + alignedSize) * 2);
        }

        uniformBuffers.get(frame).uploadUBO(buffer, offset);
        usedBytes += alignedSize;
    }

    public static int getAlignedSize(int uploadSize) {
        return align(uploadSize, minOffset);
    }

    public void checkCapacity(int size) {
        if (size > this.bufferSize - this.usedBytes) {
            reset();
        }
    }

    public void updateOffset(int alignedSize) {
        usedBytes += alignedSize;
    }

    public void setOffset(int alignedSize) {
        usedBytes = alignedSize;
    }

    private void resizeBuffer(int newSize) {

        for (UniformBuffer uniformBuffer : uniformBuffers) {
            uniformBuffer.resizeBuffer(newSize);
        }

        this.bufferSize = newSize;

        System.out.println("resized UniformBuffer to: " + newSize);
    }

    public void submitUploads() {
        if (commandBuffer == null)
            return;

        DeviceManager.getTransferQueue().submitCommands(commandBuffer);
        Synchronization.INSTANCE.addCommandBuffer(commandBuffer);
        commandBuffer = null;
    }

    public void free() {
        uniformBuffers.forEach(Buffer::freeBuffer);
    }

    public void reset() {
        usedBytes = 0;
    }

    public int getUsedBytes() {
        return usedBytes;
    }

    public long getPointer(int frame) {
        return this.uniformBuffers.get(frame).data.get(0) + usedBytes;
    }

    public long getId(int i) {
        return uniformBuffers.get(i).getId();
    }

    public UniformBuffer getUniformBuffer(int i) {
        return uniformBuffers.get(i);
    }

    public class UniformBuffer extends Buffer {

        protected UniformBuffer(int size, MemoryType memoryType) {
            super(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, memoryType);
            this.createBuffer(size);
        }

        public void uploadUBO(ByteBuffer buffer, int offset) {
            if (this.type.mappable()) {
                VUtil.memcpy(buffer, this.data.getByteBuffer(0, bufferSize), offset);
            } else {
                if (commandBuffer == null)
                    commandBuffer = DeviceManager.getTransferQueue().beginCommands();

                int size = buffer.remaining();

                StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
                stagingBuffer.copyBuffer(size, buffer);

                TransferQueue.uploadBufferCmd(commandBuffer.getHandle(), stagingBuffer.id, stagingBuffer.offset, this.id, offset, size);
            }
        }

        private void resizeBuffer(int newSize) {
            this.type.freeBuffer(this);
            createBuffer(newSize);
        }
    }

}
