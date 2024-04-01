package net.vulkanmod.render.chunk.buffer;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.vulkanmod.render.chunk.SubCopyCommand;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;

import static net.vulkanmod.vulkan.queue.Queue.GraphicsQueue;

import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;

public class UploadManager {
    public static UploadManager INSTANCE;

    public static void createInstance() {
        INSTANCE = new UploadManager();
    }

    CommandPool.CommandBuffer commandBuffer;

//    LongOpenHashSet dstBuffers = new LongOpenHashSet();
    Long2ObjectArrayMap< ObjectArrayFIFOQueue<SubCopyCommand>> dstBuffers = new Long2ObjectArrayMap<>(8);

    public void swapBuffers(long srcBuffer, long dstBuffer)
    {
        if(!dstBuffers.containsKey(srcBuffer)) return;// throw new RuntimeException("NOBuffer");
        dstBuffers.put(dstBuffer,dstBuffers.remove(srcBuffer));
    }


    public void submitUploads() {

        if(dstBuffers.isEmpty()) return;

        if(commandBuffer == null)
        {
            this.commandBuffer = GraphicsQueue.beginCommands();
//            GraphicsQueue.GigaBarrier(this.commandBuffers[currentFrame].getHandle(), VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
        }

        GraphicsQueue.MultiBufferBarriers(this.commandBuffer.getHandle(),
                dstBuffers.keySet(),
                VK_ACCESS_INDEX_READ_BIT,
                0,
                VK_PIPELINE_STAGE_VERTEX_INPUT_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT);




        GraphicsQueue.uploadBufferCmds(this.commandBuffer, Vulkan.getStagingBuffer().getId(), dstBuffers.long2ObjectEntrySet());


        dstBuffers.clear();

//        GraphicsQueue.GigaBarrier(this.commandBuffer.getHandle());
        GraphicsQueue.submitCommands(this.commandBuffer);
    }

    public void recordUpload(long bufferId, int dstOffset, int bufferSize, ByteBuffer src) {


        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
        stagingBuffer.copyBuffer(bufferSize, src);

        if(!dstBuffers.containsKey(bufferId))
        {
            dstBuffers.put(bufferId, new ObjectArrayFIFOQueue<>(32));
        }

        dstBuffers.get(bufferId).enqueue(new SubCopyCommand(stagingBuffer.getOffset(), dstOffset, bufferSize));

    }

    public void copyBuffer(Buffer src, Buffer dst) {
        copyBuffer(src, 0, dst, 0, src.getBufferSize());
    }

    public void copyBuffer(Buffer src, int srcOffset, Buffer dst, int dstOffset, int size) {
//        if (this.commandBuffer == null)
//            this.commandBuffer = GraphicsQueue.beginCommands();
//
//        VkCommandBuffer commandBuffer = this.commandBuffer.getHandle();
//
//        GraphicsQueue.BufferBarrier(commandBuffer,
//                src.getId(),
//                ~0,
//                VK_ACCESS_TRANSFER_WRITE_BIT,
//                VK_ACCESS_TRANSFER_WRITE_BIT,
//                VK_PIPELINE_STAGE_TRANSFER_BIT,
//                VK_PIPELINE_STAGE_TRANSFER_BIT);

        GraphicsQueue.uploadBufferImmediate(src.getId(), srcOffset, dst.getId(), dstOffset, size);
    }

    public void waitUploads() {
        if (this.commandBuffer == null)
            return;

        Synchronization.INSTANCE.addCommandBuffer(this.commandBuffer);

        this.commandBuffer = null;
        this.dstBuffers.clear();
    }

}
