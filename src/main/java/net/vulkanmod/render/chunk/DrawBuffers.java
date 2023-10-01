package net.vulkanmod.render.chunk;

import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.IndirectBuffer;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.Checks.check;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.vulkan.VK10.*;

public class DrawBuffers {

    private static final int VERTEX_SIZE = TerrainShaderManager.TERRAIN_VERTEX_FORMAT.getVertexSize();
    private static final int INDEX_SIZE = Short.BYTES;

    private boolean allocated = false;
    AreaBuffer vertexBuffer;
    AreaBuffer indexBuffer;
    private final StaticQueue<DrawParameters> Squeue = new StaticQueue<>(512);
    private final StaticQueue<DrawParameters> Tqueue = new StaticQueue<>(512);

    public void allocateBuffers() {
        this.vertexBuffer = new AreaBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, 3500000, VERTEX_SIZE);
        this.indexBuffer = new AreaBuffer(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 1000000, INDEX_SIZE);

        this.allocated = true;
    }

    public DrawParameters upload(UploadBuffer buffer, DrawParameters drawParameters) {
        int vertexOffset = drawParameters.vertexOffset;
        int firstIndex = 0;

        if(!buffer.indexOnly) {
            this.vertexBuffer.upload(buffer.getVertexBuffer(), drawParameters.vertexBufferSegment);
//            drawParameters.vertexOffset = drawParameters.vertexBufferSegment.getOffset() / VERTEX_SIZE;
            vertexOffset = drawParameters.vertexBufferSegment.getOffset() / VERTEX_SIZE;

            //debug
//            if(drawParameters.vertexBufferSegment.getOffset() % VERTEX_SIZE != 0) {
//                throw new RuntimeException("misaligned vertex buffer");
//            }
        }

        if(!buffer.autoIndices) {
            this.indexBuffer.upload(buffer.getIndexBuffer(), drawParameters.indexBufferSegment);
//            drawParameters.firstIndex = drawParameters.indexBufferSegment.getOffset() / INDEX_SIZE;
            firstIndex = drawParameters.indexBufferSegment.getOffset() / INDEX_SIZE;
        }

//        AreaUploadManager.INSTANCE.enqueueParameterUpdate(
//                new ParametersUpdate(drawParameters, buffer.indexCount, firstIndex, vertexOffset));

        drawParameters.indexCount = buffer.indexCount;
        drawParameters.firstIndex = firstIndex;
        drawParameters.vertexOffset = vertexOffset;

        Renderer.getDrawer().getQuadsIndexBuffer().checkCapacity(buffer.indexCount * 2 / 3);

        buffer.release();

        return drawParameters;
    }

    public void buildDrawBatchesIndirect(IndirectBuffer indirectBuffer, TerrainRenderType renderType, double camX, double camY, double camZ, boolean isTranslucent) {
        int stride = 20;

        int drawCount = 0;


        try (MemoryStack stack = MemoryStack.stackPush()) {
            Pipeline pipeline;
            LongBuffer pVertexBuffer;
            LongBuffer pOffset;

            StaticQueue<DrawParameters> drawParameters1 = isTranslucent ? Tqueue : Squeue;

            ByteBuffer byteBuffer = stack.calloc(20 * drawParameters1.size());
            ByteBuffer uboBuffer = stack.calloc(16 * drawParameters1.size());

            long bufferPtr = MemoryUtil.memAddress0(byteBuffer);
            long uboPtr = MemoryUtil.memAddress0(uboBuffer);

            pipeline = TerrainShaderManager.getTerrainIndirectShader();

            if (isTranslucent) {
                vkCmdBindIndexBuffer(Renderer.getCommandBuffer(), this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
            }

            var iterator = drawParameters1.iterator(isTranslucent);
            while (iterator.hasNext()) {
                DrawParameters drawParameters = iterator.next();


                if (drawParameters.indexCount == 0) {
                    continue;
                }

                //TODO
                if (!drawParameters.ready && drawParameters.vertexBufferSegment.getOffset() != -1) {
                    if (!drawParameters.vertexBufferSegment.isReady())
                        continue;
                    drawParameters.ready = true;
                }

                long ptr = bufferPtr + (drawCount * 20L);
                MemoryUtil.memPutInt(ptr, drawParameters.indexCount);
                MemoryUtil.memPutInt(ptr + 4, 1);
                MemoryUtil.memPutInt(ptr + 8, drawParameters.firstIndex);
                //            MemoryUtil.memPutInt(ptr + 12, drawParameters.vertexBufferSegment.getOffset() / VERTEX_SIZE);
                MemoryUtil.memPutInt(ptr + 12, drawParameters.vertexOffset);
                //            MemoryUtil.memPutInt(ptr + 12, drawParameters.vertexBufferSegment.getOffset());
                MemoryUtil.memPutInt(ptr + 16, 0);

                ptr = uboPtr + (drawCount * 16L);
                MemoryUtil.memPutFloat(ptr, (float) ((double) drawParameters.x - camX));
                MemoryUtil.memPutFloat(ptr + 4, (float) ((double) drawParameters.y - camY));
                MemoryUtil.memPutFloat(ptr + 8, (float) ((double) drawParameters.z - camZ));

                drawCount++;
            }

            if (drawCount == 0) {
                return;
            }


            byteBuffer.position(0);

            indirectBuffer.recordCopyCmd(byteBuffer);

            pipeline.getManualUBO().setSrc(uboPtr, 16 * drawCount);

            pVertexBuffer = stack.longs(vertexBuffer.getId());
            pOffset = stack.longs(0);

            vkCmdBindVertexBuffers(Renderer.getCommandBuffer(), 0, pVertexBuffer, pOffset);

//            pipeline.bindDescriptorSets(Drawer.getCommandBuffer(), WorldRenderer.getInstance().getUniformBuffers(), Drawer.getCurrentFrame());

            vkCmdDrawIndexedIndirect(Renderer.getCommandBuffer(), indirectBuffer.getId(), indirectBuffer.getOffset(), drawCount, stride);

//            fakeIndirectCmd(Drawer.getCommandBuffer(), indirectBuffer, drawCount, uboBuffer);

//        MemoryUtil.memFree(byteBuffer);
        }
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

    public void buildDrawBatchesDirect(Pipeline pipeline, double camX, double camY, double camZ, boolean isTranslucent) {

        if(isTranslucent)
            vkCmdBindIndexBuffer(Renderer.getCommandBuffer(), this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);

        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();


        try (MemoryStack stack = MemoryStack.stackGet().push()) {
            final long bufferPtr = stack.nmalloc(12);

            final long npointer = stack.npointer(vertexBuffer.getId());
            final long npointer1 = stack.nmalloc(Pointer.POINTER_SIZE);
            for (DrawParameters drawParameters : (isTranslucent ? Tqueue : Squeue)) {

                MemoryUtil.memPutFloat(bufferPtr + 0, (float) (drawParameters.x - camX));
                MemoryUtil.memPutFloat(bufferPtr + 4, (float) (drawParameters.y - camY));
                MemoryUtil.memPutFloat(bufferPtr + 8, (float) (drawParameters.z - camZ));

                VUtil.UNSAFE.putLong(npointer1, drawParameters.vertexOffset*VERTEX_SIZE);
                nvkCmdPushConstants(commandBuffer, pipeline.getLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, 12, bufferPtr);
                nvkCmdBindVertexBuffers(commandBuffer, 0, 1, npointer, npointer1);
                vkCmdDrawIndexed(commandBuffer, drawParameters.indexCount, 1, drawParameters.firstIndex, 0, 0);

            }

        }
    }

    public void releaseBuffers() {
        if(!this.allocated)
            return;

        this.vertexBuffer.freeBuffer();
        this.indexBuffer.freeBuffer();

        this.vertexBuffer = null;
        this.indexBuffer = null;
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
        private int x;
        private int y;
        private int z;
        int indexCount;
        int firstIndex;
        int vertexOffset;
        AreaBuffer.Segment vertexBufferSegment = new AreaBuffer.Segment();
        AreaBuffer.Segment indexBufferSegment;
        boolean ready = false;

        DrawParameters(int x, int y, int z, boolean translucent) {
            this.x = x;
            this.y = y;
            this.z = z;
            if(translucent) {
                indexBufferSegment = new AreaBuffer.Segment();
            }
        }

        public void reset(int x, int y, int z, ChunkArea chunkArea) {
            this.indexCount = 0;
            this.firstIndex = 0;
            this.vertexOffset = 0;
            this.x=x;
            this.y=y;
            this.z=z;

            int segmentOffset = this.vertexBufferSegment.getOffset();
            if(chunkArea != null && chunkArea.drawBuffers.isAllocated() && segmentOffset != -1) {
//                this.chunkArea.drawBuffers.vertexBuffer.setSegmentFree(segmentOffset);
                chunkArea.drawBuffers.vertexBuffer.setSegmentFree(this.vertexBufferSegment);
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
