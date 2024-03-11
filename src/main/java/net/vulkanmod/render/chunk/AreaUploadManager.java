package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkMemoryBarrier;

import static org.lwjgl.vulkan.VK10.*;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;

public class AreaUploadManager {
    public static final int FRAME_NUM = 2;
    public static AreaUploadManager INSTANCE;

    public static void createInstance() {
        INSTANCE = new AreaUploadManager();
    }

    ObjectArrayList<AreaBuffer.Segment>[] recordedUploads;
    CommandPool.CommandBuffer[] commandBuffers;

    LongOpenHashSet dstBuffers = new LongOpenHashSet();

    int currentFrame;

    public void init() {
        this.commandBuffers = new CommandPool.CommandBuffer[FRAME_NUM];
        this.recordedUploads = new ObjectArrayList[FRAME_NUM];

        for (int i = 0; i < FRAME_NUM; i++) {
            this.recordedUploads[i] = new ObjectArrayList<>();
        }
    }

    public synchronized void submitUploads(int mask) {
        if(this.recordedUploads[this.currentFrame].isEmpty())
            return;

        TransferQueue.submitCommands(this.commandBuffers[currentFrame], mask);
    }

    public void uploadAsync(AreaBuffer.Segment uploadSegment, long bufferId, long dstOffset, long bufferSize, ByteBuffer src) {

        if(commandBuffers[currentFrame] == null)
            this.commandBuffers[currentFrame] = TransferQueue.beginCommands();

        VkCommandBuffer commandBuffer = commandBuffers[currentFrame].getHandle();

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
        stagingBuffer.copyBuffer((int) bufferSize, src);

        if(!dstBuffers.add(bufferId)) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer bufferBarrier = VkBufferMemoryBarrier.calloc(1, stack);
                bufferBarrier.sType$Default();
                bufferBarrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                bufferBarrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                bufferBarrier.srcQueueFamilyIndex(TransferQueue.getFamilyIndex());
                bufferBarrier.dstQueueFamilyIndex(TransferQueue.getFamilyIndex());
                bufferBarrier.buffer(bufferId);
                bufferBarrier.size(~0);


                vkCmdPipelineBarrier(commandBuffer,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0,
                        null,
                        bufferBarrier,
                        null);
            }

            dstBuffers.clear();
        }

        TransferQueue.uploadBufferCmd(commandBuffer, stagingBuffer.getId(), stagingBuffer.getOffset(), bufferId, dstOffset, bufferSize);

        this.recordedUploads[this.currentFrame].add(uploadSegment);
    }

    public void updateFrame() {
        this.currentFrame = (this.currentFrame + 1) % FRAME_NUM;
        waitUploads(this.currentFrame);

        this.dstBuffers.clear();
    }

    private void waitUploads(int frame) {
        CommandPool.CommandBuffer commandBuffer = commandBuffers[frame];
        if(commandBuffer == null)
            return;
        Synchronization.waitSubmit(commandBuffer);

        for(AreaBuffer.Segment uploadSegment : this.recordedUploads[frame]) {
            uploadSegment.setReady();
        }

//        this.commandBuffers[frame].reset();
        this.commandBuffers[frame] = null;
        this.recordedUploads[frame].clear();
    }

    public synchronized void waitAllUploads() {
        for(int i = 0; i < this.commandBuffers.length; ++i) {
            waitUploads(i);
        }
    }

}
