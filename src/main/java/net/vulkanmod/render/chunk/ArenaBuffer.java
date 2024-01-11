package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.vulkanmod.render.chunk.util.StaticArray;
import net.vulkanmod.vulkan.DeviceManager;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;

import static net.vulkanmod.vulkan.memory.MemoryTypes.GPU_MEM;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;

public class ArenaBuffer extends Buffer {

    final static int BlockSize_t = 20*512;
    int suballocs;
    int lastIndex, currentOffset;

    final StaticArray<Integer> subCopyIndices;
    final Int2IntArrayMap baseOffsets;
    private int byteMark;
    int usedBytes2;
    private CommandPool.CommandBuffer commandBuffer;

    public final ObjectArrayFIFOQueue<SubCopyCommand> subCmdUploads = new ObjectArrayFIFOQueue<>(128);

    public ArenaBuffer(int type, int suballocs) {
        super(type, GPU_MEM);
        createBuffer(BlockSize_t*suballocs);
//        this.BlockSize_t = align;
        this.suballocs = suballocs;

        subCopyIndices = new StaticArray<>(suballocs);
        baseOffsets = new Int2IntArrayMap(suballocs);
    }



    public void uploadSubAlloc(long ptr, int index)
    {

        int BaseOffset = baseOffsets.computeIfAbsent(index, i -> addSubAlloc(index));
        if(commandBuffer == null)
            commandBuffer = DeviceManager.getTransferQueue().beginCommands();
        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
        stagingBuffer.copyBuffer2(BlockSize_t, ptr);

        TransferQueue.uploadBufferCmd(commandBuffer.getHandle(), stagingBuffer.getId(), stagingBuffer.getOffset(), this.getId(), BaseOffset, BlockSize_t);

        subCmdUploads.enqueue(new SubCopyCommand(stagingBuffer.getOffset(), BaseOffset, BlockSize_t));

    }


    public void SubmitAll()
    {
        if(commandBuffer == null||subCmdUploads.isEmpty())
            if(commandBuffer == null)
                return;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer vkBufferCopies = VkBufferCopy.malloc(subCmdUploads.size(), stack);
            while (!subCmdUploads.isEmpty()) {
                for (var a : vkBufferCopies) {
                    SubCopyCommand subCopyCommand = subCmdUploads.dequeue();
                    a.set(subCopyCommand.srcOffset(), subCopyCommand.dstOffset(), subCopyCommand.bufferSize());
                }
            }
            vkCmdCopyBuffer(commandBuffer.getHandle(), Vulkan.getStagingBuffer().getId(), this.id, vkBufferCopies);
        }

        DeviceManager.getTransferQueue().submitCommands(commandBuffer);
        Synchronization.INSTANCE.addCommandBuffer(commandBuffer);
        commandBuffer = null;
    }

    private int addSubAlloc(int index) {
        baseOffsets.put(index, byteMark);
        byteMark += BlockSize_t;
        usedBytes2 += BlockSize_t;
        return baseOffsets.get(index);
    }

    void rem(int index) {
        if(isBaseOffsetEmpty(index)) return;
        baseOffsets.remove(index);


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
