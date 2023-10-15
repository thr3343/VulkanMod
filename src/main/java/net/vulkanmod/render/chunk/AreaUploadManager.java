package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkFenceCreateInfo;

import java.nio.LongBuffer;

import static net.vulkanmod.render.chunk.DrawBuffers.tVirtualBufferIdx;
import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;
import static org.lwjgl.vulkan.VK10.*;

public class AreaUploadManager {
    public static AreaUploadManager INSTANCE;

    //TODO: Might replace this with custom implementation later (to allow faster Key Swapping)
    private final Long2ObjectArrayMap<ObjectArrayFIFOQueue<SubCopyCommand>> DistinctBuffers = new Long2ObjectArrayMap<>(8);
    private long[] fenceArray;

    public static void createInstance() {
        INSTANCE = new AreaUploadManager();
    }

    final ObjectArrayList<CommandPool.CommandBuffer> Submits = new ObjectArrayList<>(8);

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
        this.fenceArray = new long[frames];

        for (int i = 0; i < frames; i++) {
//            this.recordedUploads[i] = new ObjectArrayList<>();
            this.updatedParameters[i] = new ObjectArrayList<>();
            this.frameOps[i] = new ObjectArrayList<>();
            this.fenceArray[i] = createFence();
        }
    }

    private long createFence() {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pFence = stack.mallocLong(1);
            vkCreateFence(Vulkan.getDevice(), fenceInfo, null, pFence);
            return pFence.get(0);
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
        if(!this.DistinctBuffers.isEmpty()) extracted1();
        if (commandBuffers[currentFrame] == null) return;
        fenceArray[currentFrame] = TransferQueue.submitCommands2(Submits, fenceArray[currentFrame]);
    }

    private void extracted1() {
        beginIfNeeded();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            final long l = Vulkan.getStagingBuffer(currentFrame).getId();
            for (final var queueEntry : DistinctBuffers.long2ObjectEntrySet()) {
                final var value = queueEntry.getValue();
                final var copyRegions = VkBufferCopy.malloc(value.size(), stack);
                for (VkBufferCopy vkBufferCopy : copyRegions) {
                    SubCopyCommand a = value.dequeue();
                    vkBufferCopy.set(a.offset(), a.dstOffset(), a.bufferSize());
                }

                vkCmdCopyBuffer(commandBuffers[currentFrame].getHandle(), l, queueEntry.getLongKey(), copyRegions);
            }
        }

        this.DistinctBuffers.clear();

        Submits.add(0, commandBuffers[currentFrame]);
    }

    public void extracted() {
        if(tVirtualBufferIdx.isEmpty()) return;
        CommandPool.CommandBuffer commandBuffer = TransferQueue.beginCommands();
        tVirtualBufferIdx.uploadSubset(Vulkan.getStagingBuffer(this.currentFrame).getId(), commandBuffer);
        Submits.push(commandBuffer);
    }

    public SubCopyCommand uploadAsync2(long dstBufferSize, long dstOffset, long bufferSize, long src) {
        Validate.isTrue(currentFrame == Renderer.getCurrentFrame());
        Validate.isTrue(dstOffset<dstBufferSize);


//            this.commandBuffers[currentFrame] = GraphicsQueue.getInstance().beginCommands();

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(this.currentFrame);
        stagingBuffer.copyBuffer2((int) bufferSize, src);

//        TransferQueue.uploadBufferCmd(this.commandBuffers[currentFrame], stagingBuffer.getId(), stagingBuffer.getOffset(), bufferId, dstOffset, bufferSize);
        return new SubCopyCommand(stagingBuffer.getOffset(), dstOffset, bufferSize);
    }

    private void beginIfNeeded() {
        if(commandBuffers[currentFrame] == null)
            this.commandBuffers[currentFrame] = TransferQueue.beginCommands();
    }

    public void uploadAsync(long bufferId, long dstOffset, long bufferSize, long src) {
        Validate.isTrue(currentFrame == Renderer.getCurrentFrame());

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

        CommandPool.CommandBuffer commandBuffer = TransferQueue.beginCommands();
        TransferQueue.uploadBufferCmd(commandBuffer, src.getId(), 0, dst.getId(), 0, src.getBufferSize());
        Submits.add(0, commandBuffer);
    }

    public void copyImmediate(Buffer src, Buffer dst) {
        if(dst.getBufferSize() < src.getBufferSize()) {
            throw new IllegalArgumentException("dst buffer is smaller than src buffer.");
        }

        CommandPool.CommandBuffer commandBuffer = TransferQueue.beginCommands();
        TransferQueue.uploadBufferCmd(commandBuffer, src.getId(), 0, dst.getId(), 0, src.getBufferSize());
        Synchronization.waitFence(TransferQueue.submitCommands(commandBuffer));
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
        if(Submits.isEmpty()) return;

//        if(Synchronization.checkFenceStatus(fenceArray[currentFrame]))
        Synchronization.waitFence(fenceArray[currentFrame]);

        //        for(AreaBuffer.Segment uploadSegment : this.recordedUploads[frame]) {
//            uploadSegment.setReady();
//        }

        Submits.forEach(CommandPool.CommandBuffer::reset);

        for(DrawBuffers.ParametersUpdate parametersUpdate : this.updatedParameters[frame]) {
            parametersUpdate.setDrawParameters();
        }

//        this.commandBuffers[frame].reset();
        this.commandBuffers[frame] = null;
        Submits.clear();
//        this.recordedUploads[frame].clear();
    }

}
