package net.vulkanmod.render.chunk;

import net.vulkanmod.render.VirtualBuffer;
import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.render.virtualSegmentBuffer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.IndirectBuffer;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.util.VUtil;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.sun.jna.Native.POINTER_SIZE;
import static org.lwjgl.system.Checks.check;
import static org.lwjgl.system.JNI.*;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.vulkan.VK10.*;

public class DrawBuffers {

    private static final int VERTEX_SIZE = TerrainShaderManager.TERRAIN_VERTEX_FORMAT.getVertexSize();
    private static final int INDEX_SIZE = Short.BYTES;
    static final VirtualBuffer tVirtualBufferIdx = new VirtualBuffer(16777216, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, TerrainRenderType.TRANSLUCENT, MemoryTypes.GPU_MEM);
//    static final VirtualBuffer drawIndirectCmdBuffer = new VirtualBuffer(1048576, VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT, TerrainRenderType.TRANSLUCENT, MemoryTypes.GPU_MEM);
    public final int index;
    private final Vector3i origin;
    private boolean allocated = false;

    private final StaticQueue<DrawParameters> Squeue = new StaticQueue<>(512);
    private final StaticQueue<DrawParameters> Tqueue = new StaticQueue<>(512);

    public AreaBuffer SVertexBuffer;
    public AreaBuffer TVertexBuffer;


    public DrawBuffers(int index, Vector3i origin) {
        this.index=index;
        this.origin = origin;
    }

    public static void cleanUp() {
        tVirtualBufferIdx.cleanUp();
    }

    public void allocateBuffers() {

        this.SVertexBuffer = new AreaBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, 4194304, VERTEX_SIZE);
        this.TVertexBuffer = new AreaBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, 1048576, VERTEX_SIZE);

        this.allocated = true;
    }

    public DrawParameters upload(int xOffset, int yOffset, int zOffset, UploadBuffer buffer, DrawParameters drawParameters, TerrainRenderType renderType) {
        int vertexOffset = drawParameters.vertexOffset;
        int firstIndex = 0;
        final int xOffset1 = (xOffset & 127);
        final int zOffset1 = (zOffset & 127);
        drawParameters.baseInstance= yOffset<<18|zOffset1<<9|xOffset1;

        if(!buffer.indexOnly()) {

           if(renderType!=TerrainRenderType.TRANSLUCENT) this.SVertexBuffer.upload(buffer.getVertexBuffer(), drawParameters.vertexBufferSegment, buffer.vertSize());
           else this.TVertexBuffer.upload(buffer.getVertexBuffer(), drawParameters.vertexBufferSegment, buffer.vertSize());
           //            drawParameters.vertexOffset = drawParameters.vertexBufferSegment.getOffset() / VERTEX_SIZE;
           vertexOffset = drawParameters.vertexBufferSegment.getOffset() / VERTEX_SIZE;

            //debug
//            if(drawParameters.vertexBufferSegment.getOffset() % VERTEX_SIZE != 0) {
//                throw new RuntimeException("misaligned vertex buffer");
//            }
        }

        if(!buffer.autoIndices()) {
            firstIndex = configureIndexFormat(buffer.indexBuffer(), drawParameters, buffer.indexSize());
        }

//        AreaUploadManager.INSTANCE.enqueueParameterUpdate(
//                new ParametersUpdate(drawParameters, buffer.indexCount, firstIndex, vertexOffset));

        drawParameters.indexCount = buffer.indexCount();
        drawParameters.firstIndex = firstIndex;
        drawParameters.vertexOffset = vertexOffset;



        buffer.release();

        return drawParameters;
    }

    private int configureIndexFormat(long buffer, DrawParameters drawParameters, int indexSize) {

        if(drawParameters.indexBufferSegment==null || !tVirtualBufferIdx.isAlreadyLoaded(drawParameters.index, indexSize))
        {
            tVirtualBufferIdx.addFreeableRange(drawParameters.indexBufferSegment);
            drawParameters.indexBufferSegment = tVirtualBufferIdx.allocSubSection(this.index, drawParameters.index, indexSize, TerrainRenderType.TRANSLUCENT);
        }

        AreaUploadManager.INSTANCE.uploadAsync2(tVirtualBufferIdx,
                tVirtualBufferIdx.bufferPointerSuperSet,
                tVirtualBufferIdx.size_t,
                drawParameters.indexBufferSegment.i2(),
                drawParameters.indexBufferSegment.size_t(),
                buffer);


//            drawParameters.firstIndex = drawParameters.indexBufferSegment.getOffset() / INDEX_SIZE;
        return drawParameters.indexBufferSegment.i2() / INDEX_SIZE;
    }

    public void buildDrawBatchesIndirect(IndirectBuffer indirectBuffer, double camX, double camY, double camZ, boolean isTranslucent, long layout, long address, long functionAddress2, long functionAddress1, long functionAddress) {
        int stride = 20;


        try (MemoryStack stack = MemoryStack.stackPush()) {

            //            if (isTranslucent) {
//                vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
//            }
            FloatBuffer pValues = stack.floats((float) (this.origin.x/* + (drawParameters.baseInstance&0x7f)*/ - camX), (float) -camY, (float) (this.origin.z /*+ (drawParameters.baseInstance >> 7 & 0x7f)*/ - camZ));
            long pValues1 = memAddress(pValues);
            callPJPV(address, layout, VK_SHADER_STAGE_VERTEX_BIT, 0, pValues.remaining() << 2, pValues1, functionAddress2);

            final int size = (isTranslucent ? Tqueue : Squeue).size();
            uploadIndirectCommands(indirectBuffer, isTranslucent, stack.malloc(20 * size));

            long pBuffers = stack.npointer((isTranslucent ? TVertexBuffer : SVertexBuffer).getId());
            long pOffsets = stack.npointer(0);
            callPPPV(address, 0, 1, pBuffers, pOffsets, functionAddress1);

//            pipeline.bindDescriptorSets(Drawer.getCommandBuffer(), WorldRenderer.getInstance().getUniformBuffers(), Drawer.getCurrentFrame());

            long buffer = indirectBuffer.getId();
            long offset = indirectBuffer.getOffset();
            callPJJV(address, buffer, offset, size, stride, functionAddress);

//            fakeIndirectCmd(Drawer.getCommandBuffer(), indirectBuffer, drawCount, uboBuffer);

//        MemoryUtil.memFree(byteBuffer);
        }
    }

    private void uploadIndirectCommands(IndirectBuffer indirectBuffer, boolean isTranslucent, ByteBuffer byteBuffer) {
        int drawCount = 0;

        long bufferPtr = MemoryUtil.memAddress0(byteBuffer);

        //            if (isTranslucent) {
//                vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
//            }
        for(var iterator = (isTranslucent ? Tqueue : Squeue).iterator(isTranslucent); iterator.hasNext(); ) {
            DrawParameters drawParameters = iterator.next();

            long ptr = bufferPtr + (drawCount * 20L);
            MemoryUtil.memPutInt(ptr, drawParameters.indexCount);
            MemoryUtil.memPutInt(ptr + 4, 1);
            MemoryUtil.memPutInt(ptr + 8, drawParameters.firstIndex);
            //            MemoryUtil.memPutInt(ptr + 12, drawParameters.vertexBufferSegment.getOffset() / VERTEX_SIZE);
            MemoryUtil.memPutInt(ptr + 12, drawParameters.vertexOffset);
            //            MemoryUtil.memPutInt(ptr + 12, drawParameters.vertexBufferSegment.getOffset());
            MemoryUtil.memPutInt(ptr + 16, drawParameters.baseInstance);

            drawCount++;
        }


        byteBuffer.position(0);

        indirectBuffer.recordCopyCmd(byteBuffer);
    }

    private static void fakeIndirectCmd(VkCommandBuffer commandBuffer, IndirectBuffer indirectBuffer, int drawCount, ByteBuffer offsetBuffer) {
        Pipeline pipeline = TerrainShaderManager.getTerrainDirectShader();
//        Drawer.getInstance().bindPipeline(pipeline);
        pipeline.bindDescriptorSets(Renderer.getCommandBuffer(), Renderer.getCurrentFrame());
//        pipeline.bindDescriptorSets(Drawer.getCommandBuffer(), WorldRenderer.getInstance().getUniformBuffers(), Drawer.getCurrentFrame());

        ByteBuffer buffer = indirectBuffer.getByteBuffer();
        long address = MemoryUtil.memAddress0(buffer);
        long offsetAddress = MemoryUtil.memAddress0(offsetBuffer);
        int baseOffset = (int) indirectBuffer.getOffset();
        long offset;
        int stride = 20;

        int indexCount;
        int instanceCount;
        int firstIndex;
        int vertexOffset;
        int firstInstance;
        for(int i = 0; i < drawCount; ++i) {
            offset = i * stride + baseOffset + address;

            indexCount    = MemoryUtil.memGetInt(offset + 0);
            instanceCount = MemoryUtil.memGetInt(offset + 4);
            firstIndex    = MemoryUtil.memGetInt(offset + 8);
            vertexOffset  = MemoryUtil.memGetInt(offset + 12);
            firstInstance = MemoryUtil.memGetInt(offset + 16);


            long uboOffset = i * 16 + offsetAddress;

            nvkCmdPushConstants(commandBuffer, pipeline.getLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, 12, uboOffset);

            vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
        }
    }


    public void buildDrawBatchesDirect(double camX, double camY, double camZ, boolean isTranslucent, long layout, long address, boolean vertexFetchFix, long functionAddress2, long functionAddress1, long functionAddress) {

        try (MemoryStack stack = MemoryStack.stackGet().push()) {
            FloatBuffer pValues = stack.floats((float) (this.origin.x/* + (drawParameters.baseInstance&0x7f)*/ - camX), (float) -camY, (float) (this.origin.z /*+ (drawParameters.baseInstance >> 7 & 0x7f)*/ - camZ));
            long pValues1 = memAddress(pValues);
            callPJPV(address, layout, VK_SHADER_STAGE_VERTEX_BIT, 0, 12, pValues1, functionAddress2);

            final long npointer = stack.npointer((isTranslucent ? TVertexBuffer : SVertexBuffer).getId());
            final long npointer1 = stack.nmalloc(POINTER_SIZE, POINTER_SIZE);

            callPPPV(address, 0, 1, npointer, npointer1, functionAddress1);





            if (vertexFetchFix) drawIndexedBatched(isTranslucent, address, npointer1, npointer, functionAddress1, functionAddress);
            else drawIndexedBatchedBindless(isTranslucent, address, functionAddress);

        }
    }

    private void drawIndexedBatchedBindless(boolean isTranslucent, long address, long functionAddress) {
        for (DrawParameters drawParameters : (isTranslucent ? Tqueue : Squeue)) {
            callPV(address, drawParameters.indexCount, 1, drawParameters.firstIndex, drawParameters.vertexOffset, drawParameters.baseInstance, functionAddress);
        }
    }
    private void drawIndexedBatched(boolean isTranslucent, long address, long npointer1, long npointer, long functionAddress1, long functionAddress) {
        for (DrawParameters drawParameters : (isTranslucent ? Tqueue : Squeue)) {

            VUtil.UNSAFE.putLong(npointer1, drawParameters.vertexOffset*20L);

            callPPPV(address, 0, 1, npointer, npointer1, functionAddress1);
            callPV(address, drawParameters.indexCount, 1, drawParameters.firstIndex, 0, drawParameters.baseInstance, functionAddress);

        }
    }

    public void releaseBuffers() {
        if(!this.allocated)
            return;
        tVirtualBufferIdx.freeRange(this.index);
        this.SVertexBuffer.freeBuffer();

        this.TVertexBuffer = null;
        this.allocated = false;
    }

    public boolean isAllocated() {
        return allocated;
    }

    public void addMeshlet(DrawParameters renderSection, TerrainRenderType a) {
        (a==TerrainRenderType.TRANSLUCENT ? this.Tqueue : this.Squeue).add(renderSection);
    }

    public void clear() {
        Squeue.clear();
        Tqueue.clear();
    }


    public static class DrawParameters {
        private final int index;
        int indexCount;
        int firstIndex;
        int vertexOffset;
        public int baseInstance;
        AreaBuffer.Segment vertexBufferSegment = new AreaBuffer.Segment();
        virtualSegmentBuffer indexBufferSegment;
        boolean ready = false;

        DrawParameters(int index) {
            this.index = index;

        }

        public void reset(ChunkArea chunkArea) {
            this.indexCount = 0;
            this.firstIndex = 0;
            this.vertexOffset = 0;

            int segmentOffset = this.vertexBufferSegment.getOffset();
            if(chunkArea != null && chunkArea.drawBuffers.isAllocated() && segmentOffset != -1) {
//                this.chunkArea.drawBuffers.vertexBuffer.setSegmentFree(segmentOffset);

              if(indexBufferSegment==null)  chunkArea.drawBuffers.SVertexBuffer.setSegmentFree(this.vertexBufferSegment);
              else
              {
                  chunkArea.drawBuffers.TVertexBuffer.setSegmentFree(this.vertexBufferSegment);
                  tVirtualBufferIdx.addFreeableRange(indexBufferSegment);
              }

            }
        }
    }

    public record ParametersUpdate(DrawParameters drawParameters, int indexCount, int firstIndex, int vertexOffset) {

        public void setDrawParameters() {
            this.drawParameters.indexCount = indexCount;
            this.drawParameters.firstIndex = firstIndex;
            this.drawParameters.vertexOffset = vertexOffset;
            this.drawParameters.ready = true;
        }
    }

}
