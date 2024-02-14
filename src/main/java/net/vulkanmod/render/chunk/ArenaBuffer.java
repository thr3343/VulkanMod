package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.Buffer;
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
        populateFreeSections(0);
    }

    private void populateFreeSections(int offset) {
        freeOffsets.ensureCapacity(this.suballocs);
        for(int i = offset; i<BlockSize_t* this.suballocs; i+=BlockSize_t)
        {
            freeOffsets.push(i);
        }
    }


    public void uploadSubAlloc(int offset, int index, int size_t)
    {

        if(freeOffsets.isEmpty()) reSize(suballocs << 1);

        int BaseOffset = baseOffsets.computeIfAbsent(index, i -> addSubAlloc(index));





        subCmdUploads.enqueue(new SubCopyCommand(offset, BaseOffset, size_t));

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


    public void defaultState(int newSuballocCount)
    {
        if(newSuballocCount==suballocs) {
            flushAll();
            return;
        }
        this.reSize(newSuballocCount);
    }
    public void reSize(int newSuballocCount)
    {
        if(newSuballocCount==suballocs) return;
        long prevId = this.id;
        int prevSize_t = BlockSize_t*suballocs;


        this.SubmitAll();

        TransferQueue.waitIdle();

        int newSize_t = BlockSize_t * newSuballocCount;
        this.freeBuffer();

        this.createBuffer(newSize_t);

        final int min = Math.min(prevSize_t, newSize_t);
        TransferQueue.uploadBufferImmediate(prevId, 0, this.id, 0, min);

        boolean isDstShrink = prevSize_t > newSize_t;

        suballocs =newSuballocCount;
        if(isDstShrink)
        {
            flushAll();
            freeOffsets.trim(newSuballocCount);
        }
        else populateFreeSections(prevSize_t);

    }

    public void flushAll()
    {
        baseOffsets.clear();
        freeOffsets.clear();
        usedBytes=offset=0;


        populateFreeSections(0);
    }

}
