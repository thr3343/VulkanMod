package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.queue.CommandPool;

import static net.vulkanmod.vulkan.memory.MemoryTypes.GPU_MEM;
import static net.vulkanmod.vulkan.memory.MemoryTypes.HOST_MEM;
import static net.vulkanmod.vulkan.queue.Queue.GraphicsQueue;
import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;
import static org.lwjgl.system.libc.LibCString.nmemcpy;
import static org.lwjgl.vulkan.VK10.*;

public class ArenaBuffer extends Buffer {

    private static final int BlockSize_t = 20*512;

    //    final StaticArray<Integer> subCopyIndices;
    private final Int2IntArrayMap baseOffsets;
    private final IntArrayList freeOffsets;
    private int suballocs;
    private CommandPool.CommandBuffer commandBuffer;
    boolean needsResize = false;
    private int cmdSize;

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


    public void uploadSubAlloc(long bufferPtr, int index, int size_t)
    {

        if(freeOffsets.isEmpty()) {

            reSize(suballocs << 1);
        }

        int BaseOffset = baseOffsets.computeIfAbsent(index, i -> addSubAlloc(index));

        if(commandBuffer==null) {
            commandBuffer = GraphicsQueue.beginCommands();
//            GraphicsQueue.BufferBarrier(commandBuffer.getHandle(),
//                    this.id,
//                    ~0,
//                    VK_ACCESS_INDIRECT_COMMAND_READ_BIT,
//                    0,
//                    VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
//                    VK_PIPELINE_STAGE_TRANSFER_BIT);
            GraphicsQueue.BufferBarrier(commandBuffer.getHandle(),
                    this.id,
                    ~0,
                    0,
                    VK_ACCESS_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT);


        }
        GraphicsQueue.updateBuffer(commandBuffer, this.id, BaseOffset, bufferPtr, size_t);
        this.cmdSize+=size_t;
//        nmemcpy(this.data.get(0) + BaseOffset, bufferPtr, size_t);

//        subCmdUploads.enqueue(new SubCopyCommand(offset, BaseOffset, size_t));

    }


    public void SubmitAll()
    {
        if(commandBuffer==null) return;
//        Initializer.LOGGER.info(cmdSize);

        GraphicsQueue.submitCommands(commandBuffer);
        Synchronization.INSTANCE.addCommandBuffer(commandBuffer);
        needsResize=false;
        commandBuffer = null;
        cmdSize = 0;
    }

    void copyAll(boolean execDep) {



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

        needsResize=true;
        this.SubmitAll();

        GraphicsQueue.waitIdle();

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
