package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;
import static org.lwjgl.vulkan.VK10.*;

import java.nio.ByteBuffer;

public class AreaUploadManager {
    public static final int FRAME_NUM = 2;
    public static AreaUploadManager INSTANCE;

    public static void createInstance() {
        INSTANCE = new AreaUploadManager();
    }

    private final ObjectArrayList<AreaBuffer.Segment>[] recordedUploads = new ObjectArrayList[FRAME_NUM];
    private final CommandPool.CommandBuffer[] commandBuffers = new CommandPool.CommandBuffer[FRAME_NUM];

    Long2ObjectArrayMap< ObjectArrayFIFOQueue<SubCopyCommand>> dstBuffers = new Long2ObjectArrayMap<>(8);



    int currentFrame;

    public void init() {

        for (int i = 0; i < FRAME_NUM; i++) {
            this.recordedUploads[i] = new ObjectArrayList<>();
        }
    }


    public void swapBuffers(long srcBuffer, long dstBuffer)
    {
        if(!dstBuffers.containsKey(srcBuffer)) return;// throw new RuntimeException("NOBuffer");
        dstBuffers.put(dstBuffer,dstBuffers.remove(srcBuffer));
    }

    public void submitUploads() {
        if(dstBuffers.isEmpty()) return;
        if(this.recordedUploads[this.currentFrame].isEmpty()) {
            return;
        }
        if(commandBuffers[currentFrame] == null)
        {
            this.commandBuffers[currentFrame] = TransferQueue.beginCommands();
//            TransferQueue.GigaBarrier(this.commandBuffers[currentFrame].getHandle(), VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
        }

        TransferQueue.MultiBufferBarriers(this.commandBuffers[currentFrame].getHandle(),
                dstBuffers.keySet(),
                VK_ACCESS_TRANSFER_WRITE_BIT,
                0,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT);


           

        TransferQueue.uploadBufferCmds(this.commandBuffers[currentFrame], Vulkan.getStagingBuffer().getId(), dstBuffers.long2ObjectEntrySet());
            
        


        dstBuffers.clear();
        
//        TransferQueue.GigaBarrier(this.commandBuffers[currentFrame].getHandle());
        TransferQueue.submitCommands(this.commandBuffers[currentFrame]);
    }

    public void uploadAsync(AreaBuffer.Segment uploadSegment, long bufferId, int dstOffset, int bufferSize, ByteBuffer src) {




        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
        stagingBuffer.copyBuffer(bufferSize, src);



        if(!dstBuffers.containsKey(bufferId))
        {
            dstBuffers.put(bufferId, new ObjectArrayFIFOQueue<>(32));
        }
        dstBuffers.get(bufferId).enqueue(new SubCopyCommand(stagingBuffer.getOffset(), dstOffset, bufferSize));
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
        Synchronization.waitFence(commandBuffers[frame].getFence());

        for(AreaBuffer.Segment uploadSegment : this.recordedUploads[frame]) {
            uploadSegment.setReady();
        }

        this.commandBuffers[frame].reset();
        this.commandBuffers[frame] = null;
        this.recordedUploads[frame].clear();
    }

    public synchronized void waitAllUploads() {
        for(int i = 0; i < this.commandBuffers.length; ++i) {
            waitUploads(i);
        }
    }

}
