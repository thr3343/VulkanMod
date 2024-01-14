package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;

import static net.vulkanmod.vulkan.memory.MemoryTypes.GPU_MEM;
import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;
import static org.lwjgl.system.MemoryStack.stackPush;

public class ArenaBuffer extends Buffer {

    private static final int BlockSize_t = 20*512;

    //    final StaticArray<Integer> subCopyIndices;
    private final Int2IntArrayMap baseOffsets;
    private final IntArrayList freeOffsets;
    private int suballocs;
    private CommandPool.CommandBuffer commandBuffer;
    final ObjectArrayFIFOQueue<SubCopyCommand> subCmdUploads = new ObjectArrayFIFOQueue<>(128);

    public ArenaBuffer(int type, int suballocs) {
        super(type, GPU_MEM);
        this.suballocs = suballocs;
        createBuffer(BlockSize_t* this.suballocs);
//        this.BlockSize_t = align;
//        this.suballocs = suballocs;

//        subCopyIndices = new StaticArray<>(suballocs);
        baseOffsets = new Int2IntArrayMap(this.suballocs);
        freeOffsets = new IntArrayList(this.suballocs);
        populateFreeSections(this.suballocs, 0);
    }

    private void populateFreeSections(int suballocs1, int offset) {
        for(int i = offset; i<BlockSize_t* suballocs1; i+=BlockSize_t)
        {
            freeOffsets.push(i);
        }
    }


    public void uploadSubAlloc(long ptr, int index, int size_t)
    {

        if(freeOffsets.isEmpty()) reSize();

        int BaseOffset = baseOffsets.computeIfAbsent(index, i -> addSubAlloc(index));

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
        stagingBuffer.copyBuffer2(BlockSize_t, ptr);



        subCmdUploads.enqueue(new SubCopyCommand(stagingBuffer.getOffset(), BaseOffset, size_t));

    }


    public void SubmitAll()
    {
        if(commandBuffer==null) return;


        TransferQueue.submitCommands(commandBuffer);
        Synchronization.INSTANCE.addCommandBuffer(commandBuffer);

        commandBuffer = null;
    }

    void copyAll(boolean execDep) {
        if(subCmdUploads.isEmpty()) return;
        if(commandBuffer==null) commandBuffer = TransferQueue.beginCommands();
        if(execDep) TransferQueue.BufferBarrier(commandBuffer.getHandle(), this.id, ~0);

        try(MemoryStack stack = stackPush()) {

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.malloc(subCmdUploads.size(), stack);
            for(var subCpy : copyRegion)  {
                SubCopyCommand subCopyCommand = subCmdUploads.dequeue();
                subCpy.set(subCopyCommand.srcOffset(), subCopyCommand.dstOffset(), subCopyCommand.bufferSize());
            }

            TransferQueue.uploadBufferCmds(commandBuffer, Vulkan.getStagingBuffer().getId(), this.id, copyRegion);
        }


    }

    private int addSubAlloc(int index) {
        baseOffsets.put(index, freeOffsets.popInt());
//        usedBytes2 += BlockSize_t;
        return baseOffsets.get(index);
    }

    void rem(int index) {
        if(isBaseOffsetEmpty(index)) return;
        freeOffsets.push(baseOffsets.remove(index));


//        usedBytes2 -= BlockSize_t;
    }

    public boolean isBaseOffsetEmpty(int index)
    {
        return !baseOffsets.containsKey(index);
    }

    public int getBaseOffset(int index)
    {
        return baseOffsets.get(index);
    }


    public void reSize()
    {
        long prevId = this.id;
        int prevSize_t = BlockSize_t*suballocs;


        this.SubmitAll();

        TransferQueue.waitIdle();

        suballocs <<= 1;
        int newSize_t = prevSize_t << 1;
        this.freeBuffer();

        this.createBuffer(newSize_t);

        TransferQueue.uploadBufferImmediate(prevId, 0, this.id, 0, prevSize_t);




        populateFreeSections(this.suballocs, prevSize_t);

    }

    public void flushAll()
    {
        baseOffsets.clear();
        freeOffsets.clear();
        usedBytes=offset=0;


        populateFreeSections(this.suballocs, 0);
    }

}
