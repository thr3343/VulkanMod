package net.vulkanmod.render.chunk.buffer;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkMemoryBarrier;

import java.nio.ByteBuffer;

import static net.vulkanmod.vulkan.queue.Queue.GraphicsQueue;
import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;
import static org.lwjgl.vulkan.VK10.*;

public class UploadManager {
    public static UploadManager INSTANCE;

    public static void createInstance() {
        INSTANCE = new UploadManager();
    }

    CommandPool.CommandBuffer commandBuffer;

    LongOpenHashSet dstBuffers = new LongOpenHashSet();

    public void submitUploads() {
        if (this.commandBuffer == null)
            return;

        TransferQueue.submitCommands(this.commandBuffer);

        Synchronization.INSTANCE.addCommandBuffer(this.commandBuffer);

        this.commandBuffer = null;
        this.dstBuffers.clear();
    }

    public void recordUpload(Buffer buffer, long dstOffset, long bufferSize, ByteBuffer src) {
        if (this.commandBuffer == null)
            this.commandBuffer = TransferQueue.beginCommands();

        VkCommandBuffer commandBuffer = this.commandBuffer.getHandle();
        ///TODO: Abstra staging uplaods to allow easy switcning between Default and ReBAr mode
        StagingBuffer stagingBuffer = Vulkan.getChunkStaging();
        stagingBuffer.copyBuffer((int) bufferSize, src);

        if (!this.dstBuffers.add(buffer.getId())) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack);
                barrier.sType$Default();
                barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);

                vkCmdPipelineBarrier(commandBuffer,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0,
                        barrier,
                        null,
                        null);
            }

            this.dstBuffers.clear();
        }

        TransferQueue.uploadBufferCmd(commandBuffer, stagingBuffer.getId(), stagingBuffer.getOffset(), buffer.getId(), dstOffset, bufferSize);
    }

    public void copyBuffer(Buffer src, Buffer dst) {
        copyBuffer(src, 0, dst, 0, src.getBufferSize());
    }

    public void copyBuffer(Buffer src, int srcOffset, Buffer dst, int dstOffset, int size) {
        if (this.commandBuffer == null)
            this.commandBuffer = GraphicsQueue.beginCommands();

        VkCommandBuffer commandBuffer = this.commandBuffer.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack);
            barrier.sType$Default();

            VkBufferMemoryBarrier.Buffer bufferMemoryBarriers = VkBufferMemoryBarrier.calloc(1, stack);
            VkBufferMemoryBarrier bufferMemoryBarrier = bufferMemoryBarriers.get(0);
            bufferMemoryBarrier.sType$Default();
            bufferMemoryBarrier.buffer(src.getId());
            bufferMemoryBarrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            bufferMemoryBarrier.dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            bufferMemoryBarrier.size(VK_WHOLE_SIZE);

            vkCmdPipelineBarrier(commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0,
                    barrier,
                    bufferMemoryBarriers,
                    null);
        }

        this.dstBuffers.add(dst.getId());
        //TODO: AMD recommends GraphicsQueue, not TransferQueue for Local2Local copies: has less bandwidth than Graphics Queue apparently: https://gpuopen.com/learn/using-d3d12-heap-type-gpu-upload/#recommendations
        GraphicsQueue.uploadBufferCmd(commandBuffer, src.getId(), srcOffset, dst.getId(), dstOffset, size);
    }

    public void syncUploads() {
        submitUploads();

        Synchronization.INSTANCE.waitFences();
    }

}
