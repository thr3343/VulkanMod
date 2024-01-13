package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;

import static net.vulkanmod.vulkan.memory.MemoryTypes.GPU_MEM;
import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;

public class ArenaBuffer extends Buffer {

    final static int BlockSize_t = 20*512;
    int suballocs;
    int lastIndex, currentOffset;

//    final StaticArray<Integer> subCopyIndices;
    final Int2IntArrayMap baseOffsets;
    final IntArrayList freeOffsets;
    private int byteMark;
    int usedBytes2;
    CommandPool.CommandBuffer commandBuffer;
    public final ObjectArrayFIFOQueue<SubCopyCommand> subCmdUploads = new ObjectArrayFIFOQueue<>(128);

    public ArenaBuffer(int type, int suballocs) {
        super(type, GPU_MEM);
        createBuffer(BlockSize_t*suballocs);
//        this.BlockSize_t = align;
        this.suballocs = suballocs;

//        subCopyIndices = new StaticArray<>(suballocs);
        baseOffsets = new Int2IntArrayMap(suballocs);
        freeOffsets = new IntArrayList(suballocs);
        for(int i = 0; i<BlockSize_t*suballocs; i+=BlockSize_t)
        {
            freeOffsets.push(i);
        }
    }



    public void uploadSubAlloc(long ptr, int index, int size_t)
    {

        int BaseOffset = baseOffsets.computeIfAbsent(index, i -> addSubAlloc(index));

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
        stagingBuffer.copyBuffer2(BlockSize_t, ptr);

        if(commandBuffer==null)
        {
            commandBuffer = TransferQueue.beginCommands();
        }

        TransferQueue.uploadBufferCmd(commandBuffer.getHandle(), Vulkan.getStagingBuffer().getId(), stagingBuffer.getOffset(), this.id, BaseOffset, size_t);


    }

    void extracted(long id1) {
        if(commandBuffer==null)
        {
            commandBuffer = TransferQueue.beginCommands();
        }
        TransferQueue.BufferBarrier(commandBuffer.getHandle(), id1, BlockSize_t*suballocs);
    }


    public void SubmitAll()
    {
        if(commandBuffer==null) return;


        TransferQueue.submitCommands(commandBuffer);
        Synchronization.INSTANCE.addCommandBuffer(commandBuffer);

        commandBuffer = null;
    }

    private int addSubAlloc(int index) {
        baseOffsets.put(index, freeOffsets.popInt());
        byteMark += BlockSize_t;
        usedBytes2 += BlockSize_t;
        return baseOffsets.get(index);
    }

    void rem(int index) {
        if(isBaseOffsetEmpty(index)) return;
        freeOffsets.push(baseOffsets.remove(index));


        usedBytes2 -= BlockSize_t;
    }

    public boolean isBaseOffsetEmpty(int index)
    {
        return !baseOffsets.containsKey(index);
    }

    public int getBaseOffset(int index)
    {
        return baseOffsets.get(index);
    }

    public int getIndex(int a)
    {
        return currentOffset* BlockSize_t;
    }
}
