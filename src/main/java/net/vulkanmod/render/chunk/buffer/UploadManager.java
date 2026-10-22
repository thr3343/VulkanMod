package net.vulkanmod.render.chunk.buffer;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.memory.buffer.StagingBuffer;
import net.vulkanmod.vulkan.memory.buffer.StagingBuffers;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkMemoryBarrier;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public final class UploadManager {
    private static final StagingBuffer chunkStaging = new StagingBuffer(true);
    private static final Queue queue = DeviceManager.getTransferQueue();
    private static CommandPool.CommandBuffer commandBuffer;

    public static void free() {
        chunkStaging.scheduleFree();
    }

    public static void submitUploads() {
        if (commandBuffer == null)
            return;

        queue.submitCommands(commandBuffer);

        Synchronization.INSTANCE.addCommandBuffer(commandBuffer);

        commandBuffer = null;
    }

    public static void recordUpload(Buffer buffer, long dstOffset, long bufferSize, ByteBuffer src) {

        // Can get away with not needing n-buffered staging: Segments are not uploaded per frame

        chunkStaging.copyBuffer((int) bufferSize, src);

        beginCommands();
        VkCommandBuffer commandBuffer = UploadManager.commandBuffer.getHandle();
        
		// False Positive: Apparently WaW Barrers are not needed if ranges do not overlap
		// (SyncVal can only correctly track Sizes that are 4k-aligned apparently)

        TransferQueue.uploadBufferCmd(commandBuffer, chunkStaging.getId(), chunkStaging.getOffset(), buffer.getId(), dstOffset, bufferSize);
    }

    public static void copyBuffer(Buffer src, Buffer dst) {
        copyBuffer(src, 0, dst, 0, src.getBufferSize());
    }

    public static void copyBuffer(Buffer src, long srcOffset, Buffer dst, long dstOffset, long size) {
        beginCommands();

        VkCommandBuffer commandBuffer = UploadManager.commandBuffer.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier.Buffer bufferMemoryBarrier = VkBufferMemoryBarrier.calloc(1, stack);
            bufferMemoryBarrier.sType$Default();
            bufferMemoryBarrier.buffer(src.getId());
            bufferMemoryBarrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            bufferMemoryBarrier.dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            bufferMemoryBarrier.size(VK_WHOLE_SIZE);

            vkCmdPipelineBarrier(commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0,
                    null,
                    bufferMemoryBarrier,
                    null);
        }

        TransferQueue.uploadBufferCmd(commandBuffer, src.getId(), srcOffset, dst.getId(), dstOffset, size);
    }

    public void syncUploads() {
        submitUploads();

        Synchronization.INSTANCE.waitFences();
    }

    private static void beginCommands() {
        if (commandBuffer == null)
            commandBuffer = queue.beginCommands();
    }

}
