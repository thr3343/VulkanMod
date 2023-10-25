package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.render.VirtualBuffer;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import org.apache.commons.lang3.Validate;

import static net.vulkanmod.render.chunk.DrawBuffers.tVirtualBufferIdx;
import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;

public class AreaUploadManager {
    public static final int FRAME_NUM = 2;
    public static AreaUploadManager INSTANCE;

    public static void createInstance() {
        INSTANCE = new AreaUploadManager();
    }

    ObjectArrayList<AreaBuffer.Segment>[] recordedUploads;
    ObjectArrayList<DrawBuffers.ParametersUpdate>[] updatedParameters;
    ObjectArrayList<Runnable>[] frameOps;
    CommandPool.CommandBuffer[] commandBuffers;

    int currentFrame;

    public void createLists() {

        this.commandBuffers = new CommandPool.CommandBuffer[FRAME_NUM];
        this.recordedUploads = new ObjectArrayList[FRAME_NUM];
        this.updatedParameters = new ObjectArrayList[FRAME_NUM];
        this.frameOps = new ObjectArrayList[FRAME_NUM];

        for (int i = 0; i < FRAME_NUM; i++) {
            this.recordedUploads[i] = new ObjectArrayList<>();
            this.updatedParameters[i] = new ObjectArrayList<>();
            this.frameOps[i] = new ObjectArrayList<>();
        }
    }

    public synchronized void submitUploads() {
        if(this.commandBuffers[currentFrame]==null)
            return;
        var srcStaging = Vulkan.getStagingBuffer(this.currentFrame).getId();
        tVirtualBufferIdx.uploadSubset(srcStaging, this.commandBuffers[currentFrame]);


        Device.getTransferQueue().submitCommands(this.commandBuffers[currentFrame]);
    }

    public void uploadAsync2(VirtualBuffer virtualBuffer, long bufferId, long dstBufferSize, long dstOffset, long bufferSize, long src) {
        Validate.isTrue(dstOffset<dstBufferSize);

        if(commandBuffers[currentFrame] == null)
            this.commandBuffers[currentFrame] = TransferQueue.beginCommands();
//            this.commandBuffers[currentFrame] = GraphicsQueue.getInstance().beginCommands();

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(this.currentFrame);
        stagingBuffer.copyBuffer2((int) bufferSize, src);

//        TransferQueue.uploadBufferCmd(this.commandBuffers[currentFrame], stagingBuffer.getId(), stagingBuffer.getOffset(), bufferId, dstOffset, bufferSize);
        final SubCopyCommand k = new SubCopyCommand(stagingBuffer.getOffset(), dstOffset, bufferSize);
//        this.recordedUploads[this.currentFrame].add(k);
        virtualBuffer.addSubCpy(k);
    }

    public void uploadAsync(AreaBuffer.Segment uploadSegment, long bufferId, long dstOffset, long bufferSize, long src) {

        if(commandBuffers[currentFrame] == null)
            this.commandBuffers[currentFrame] = Device.getTransferQueue().beginCommands();
//            this.commandBuffers[currentFrame] = Device.getGraphicsQueue().beginCommands();

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(this.currentFrame);
        stagingBuffer.copyBuffer2((int) bufferSize, src);

        TransferQueue.uploadBufferCmd(this.commandBuffers[currentFrame], stagingBuffer.getId(), stagingBuffer.getOffset(), bufferId, dstOffset, bufferSize);

        this.recordedUploads[this.currentFrame].add(uploadSegment);
    }

    public void enqueueParameterUpdate(DrawBuffers.ParametersUpdate parametersUpdate) {
        this.updatedParameters[this.currentFrame].add(parametersUpdate);
    }

    public void enqueueFrameOp(Runnable runnable) {
        this.frameOps[this.currentFrame].add(runnable);
    }

    public void copy(Buffer src, Buffer dst) {
        if(dst.getBufferSize() < src.getBufferSize()) {
            throw new IllegalArgumentException("dst buffer is smaller than src buffer.");
        }

        if(commandBuffers[currentFrame] == null)
            this.commandBuffers[currentFrame] = Device.getTransferQueue().beginCommands();

        TransferQueue.uploadBufferCmd(this.commandBuffers[currentFrame], src.getId(), 0, dst.getId(), 0, src.getBufferSize());
    }

    public void updateFrame() {
        this.currentFrame = (this.currentFrame + 1) % FRAME_NUM;
        waitUploads(this.currentFrame);
        executeFrameOps(this.currentFrame);
    }

    private void executeFrameOps(int frame) {
        for(DrawBuffers.ParametersUpdate parametersUpdate : this.updatedParameters[frame]) {
            parametersUpdate.setDrawParameters();
        }

        for(Runnable runnable : this.frameOps[frame]) {
            runnable.run();
        }

        this.updatedParameters[frame].clear();
        this.frameOps[frame].clear();
    }

    void waitUploads() {
        this.waitUploads(currentFrame);
    }
    private void waitUploads(int frame) {
        CommandPool.CommandBuffer commandBuffer = commandBuffers[frame];
        if(commandBuffer == null)
            return;
        Synchronization.waitFence(commandBuffers[frame].getFence());

        for(AreaBuffer.Segment uploadSegment : this.recordedUploads[frame]) {
            uploadSegment.setReady();
        }

        for(DrawBuffers.ParametersUpdate parametersUpdate : this.updatedParameters[frame]) {
            parametersUpdate.setDrawParameters();
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
