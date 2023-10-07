package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.render.VirtualBuffer;
import net.vulkanmod.render.virtualSegmentBuffer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;

import static net.vulkanmod.render.chunk.DrawBuffers.tVirtualBufferIdx;
import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;

public class AreaUploadManager {
    public static AreaUploadManager INSTANCE;

    //TODO: Might replace this with custom implementation later (to allow faster Key Swapping)
    private final Long2ObjectArrayMap<ObjectArrayFIFOQueue<SubCopyCommand>> DistinctBuffers = new Long2ObjectArrayMap<>(8);

    public static void createInstance() {
        INSTANCE = new AreaUploadManager();
    }

//    ObjectArrayList<virtualSegmentBuffer>[] recordedUploads;
    ObjectArrayList<DrawBuffers.ParametersUpdate>[] updatedParameters;
    ObjectArrayList<Runnable>[] frameOps;
    CommandPool.CommandBuffer[] commandBuffers;

    int currentFrame;

    public void createLists(int frames) {
        this.commandBuffers = new CommandPool.CommandBuffer[frames];
//        this.recordedUploads = new ObjectArrayList[frames];
        this.updatedParameters = new ObjectArrayList[frames];
        this.frameOps = new ObjectArrayList[frames];

        for (int i = 0; i < frames; i++) {
//            this.recordedUploads[i] = new ObjectArrayList<>();
            this.updatedParameters[i] = new ObjectArrayList<>();
            this.frameOps[i] = new ObjectArrayList<>();
        }
    }
    public void editkey(long ik, long k) {
        Validate.isTrue(currentFrame == Renderer.getCurrentFrame());
        if(!this.DistinctBuffers.containsKey(ik)) return;

        final ObjectArrayFIFOQueue<SubCopyCommand> aTstObjectArrayFIFOQueue = DistinctBuffers.remove(ik);

    //        for(var id : DistinctBuffers[currentFrame.values()) {
    //            id.clear();
    //        }
        this.DistinctBuffers.put(k, aTstObjectArrayFIFOQueue);
    //        waitUploads();
    }
    public void submitUploads() {
        Validate.isTrue(currentFrame == Renderer.getCurrentFrame());
        if(this.DistinctBuffers.isEmpty()|| commandBuffers[currentFrame] == null) return;

        try(MemoryStack stack = MemoryStack.stackPush()) {
            final long l = Vulkan.getStagingBuffer(currentFrame).getId();
            for (var queueEntry : DistinctBuffers.long2ObjectEntrySet()) {
                final ObjectArrayFIFOQueue<SubCopyCommand> value = queueEntry.getValue();
                final VkBufferCopy.Buffer copyRegions = VkBufferCopy.malloc(value.size(), stack);
                for (VkBufferCopy vkBufferCopy : copyRegions) {
                    SubCopyCommand a = value.dequeue();
                    vkBufferCopy.set(a.offset(), a.dstOffset(), a.bufferSize());
                }

                long bufferPointerSuperSet = queueEntry.getLongKey();
                vkCmdCopyBuffer(commandBuffers[currentFrame].getHandle(), l, bufferPointerSuperSet, copyRegions);
            }
        }

        this.DistinctBuffers.clear();

        TransferQueue.submitCommands(this.commandBuffers[currentFrame]);
    }
    public void submitUpload(long k) {
        Validate.isTrue(currentFrame == Renderer.getCurrentFrame());
        if(this.DistinctBuffers.isEmpty()) return;
        if(!this.DistinctBuffers.containsKey(k)) return;
        if(commandBuffers[currentFrame] == null) return;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            final long l = Vulkan.getStagingBuffer(currentFrame).getId();
            final ObjectArrayFIFOQueue<SubCopyCommand> value = DistinctBuffers.remove(k);
            final VkBufferCopy.Buffer copyRegions = VkBufferCopy.malloc(value.size(), stack);
            for (VkBufferCopy vkBufferCopy : copyRegions) {
                var a = value.dequeue();
                vkBufferCopy.set(a.offset(), a.dstOffset(), a.bufferSize());
            }

            TransferQueue.uploadSuperSet(commandBuffers[currentFrame], copyRegions, l, k);
        }
        submit2();

    }
    public void extracted() {
        if(tVirtualBufferIdx.isEmpty()) return;
        if(commandBuffers[currentFrame] == null) return;
        var srcStaging = Vulkan.getStagingBuffer(this.currentFrame).getId();
        tVirtualBufferIdx.uploadSubset(srcStaging, commandBuffers[currentFrame]);
    }
    public void submit2() {
        TransferQueue.submitCommands(this.commandBuffers[currentFrame]);
    }
    public void uploadAsync2(VirtualBuffer virtualBuffer, long bufferId, long dstBufferSize, long dstOffset, long bufferSize, long src) {
        Validate.isTrue(currentFrame == Renderer.getCurrentFrame());
        Validate.isTrue(dstOffset<dstBufferSize);

        beginIfNeeded();
//            this.commandBuffers[currentFrame] = GraphicsQueue.getInstance().beginCommands();

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(this.currentFrame);
        stagingBuffer.copyBuffer2((int) bufferSize, src);

//        TransferQueue.uploadBufferCmd(this.commandBuffers[currentFrame], stagingBuffer.getId(), stagingBuffer.getOffset(), bufferId, dstOffset, bufferSize);
        final SubCopyCommand k = new SubCopyCommand(stagingBuffer.getOffset(), dstOffset, bufferSize);
//        this.recordedUploads[this.currentFrame].add(k);
        virtualBuffer.addSubCpy(k);
    }

    private void beginIfNeeded() {
        if(commandBuffers[currentFrame] == null)
            this.commandBuffers[currentFrame] = TransferQueue.beginCommands();
    }

    public void uploadAsync(virtualSegmentBuffer uploadSegment, long bufferId, long dstOffset, long bufferSize, long src) {
        Validate.isTrue(currentFrame == Renderer.getCurrentFrame());

        beginIfNeeded();
//            this.commandBuffers[currentFrame] = Device.getGraphicsQueue().beginCommands();

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(this.currentFrame);
        stagingBuffer.copyBuffer2((int) bufferSize, src);

//        TransferQueue.uploadBufferCmd(this.commandBuffers[currentFrame], stagingBuffer.getId(), stagingBuffer.getOffset(), bufferId, dstOffset, bufferSize);
        //        this.recordedUploads[this.currentFrame].enqueue(aTst);
        if(!this.DistinctBuffers.containsKey(bufferId)) this.DistinctBuffers.put(bufferId, new ObjectArrayFIFOQueue<>());
        this.DistinctBuffers.get(bufferId).enqueue(new SubCopyCommand(stagingBuffer.getOffset(), dstOffset, bufferSize));
//        return aTst;
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

        beginIfNeeded();

        TransferQueue.uploadBufferCmd(this.commandBuffers[currentFrame], src.getId(), 0, dst.getId(), 0, src.getBufferSize());
    }

    public void updateFrame(int frame) {
        this.waitUploads(currentFrame);
//        submitUploads(false); //Submit Prior Frame's pending uploads (i.e. remove the residual uploads)
        this.currentFrame = frame;

        executeFrameOps(frame);
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

    public void waitUploads() {
        this.waitUploads(currentFrame);
    }
    private void waitUploads(int frame) {
        CommandPool.CommandBuffer commandBuffer = commandBuffers[frame];
        if(commandBuffer == null)
            return;
        Synchronization.waitFence(commandBuffers[frame].getFence());

//        for(AreaBuffer.Segment uploadSegment : this.recordedUploads[frame]) {
//            uploadSegment.setReady();
//        }

        for(DrawBuffers.ParametersUpdate parametersUpdate : this.updatedParameters[frame]) {
            parametersUpdate.setDrawParameters();
        }

        this.commandBuffers[frame].reset();
        this.commandBuffers[frame] = null;
//        this.recordedUploads[frame].clear();
    }

    public synchronized void waitAllUploads() {
        for(int i = 0; i < this.commandBuffers.length; ++i) {
            waitUploads(i);
        }
    }

}
