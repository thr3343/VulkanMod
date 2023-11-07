package net.vulkanmod.render.chunk;

import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.IndirectBuffer;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.util.Iterator;

import static net.vulkanmod.render.chunk.TerrainShaderManager.terrainDirectShader;
import static org.lwjgl.vulkan.VK10.*;

public class DrawBuffers {

    private static final int VERTEX_SIZE = TerrainShaderManager.TERRAIN_VERTEX_FORMAT.getVertexSize();
    private static final int INDEX_SIZE = Short.BYTES;
    public final int index;
    private final Vector3i origin;
    final StaticQueue<DrawBuffers.DrawParameters[]> sectionQueue = new StaticQueue<>(512);
    private boolean allocated = false;
    AreaBuffer vertexBuffer;
    AreaBuffer indexBuffer;
    public DrawBuffers(int areaIndex, Vector3i origin) {

        this.index = areaIndex;
        this.origin = origin;
    }

    public void allocateBuffers() {
        this.vertexBuffer = new AreaBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, 3500000, VERTEX_SIZE);
        this.indexBuffer = new AreaBuffer(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 1000000, INDEX_SIZE);

        this.allocated = true;
    }

    public DrawParameters upload(int xOffset, int yOffset, int zOffset, UploadBuffer buffer, DrawParameters drawParameters) {
        int vertexOffset = drawParameters.vertexOffset;
        int firstIndex = 0;
        drawParameters.baseInstance = encodeSectionOffset(xOffset, yOffset, zOffset);

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



        buffer.release();

        return drawParameters;
    }

    private static int encodeSectionOffset(int xOffset, int yOffset, int zOffset) {
        final int xOffset1 = (xOffset & 127);
        final int zOffset1 = (zOffset & 127);
        return yOffset <<18|zOffset1<<9|xOffset1;
    }

    public void buildDrawBatchesIndirect(IndirectBuffer indirectBuffer, TerrainRenderType terrainRenderType, double camX, double camY, double camZ) {
        int stride = 20;
        boolean isTranslucent = terrainRenderType == TerrainRenderType.TRANSLUCENT;

        int drawCount = 0;


        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = stack.malloc(20 * sectionQueue.size());

            long bufferPtr = MemoryUtil.memAddress0(byteBuffer);

            VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
            if (isTranslucent) {
                vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
            }
            updateChunkAreaOrigin(camX, camY, camZ, commandBuffer, stack.nmalloc(16));
            var iterator = sectionQueue.iterator(isTranslucent);
            while (iterator.hasNext()) {
                DrawParameters drawParameters = iterator.next()[terrainRenderType.ordinal()];

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
                MemoryUtil.memPutInt(ptr + 12, drawParameters.vertexOffset);
                MemoryUtil.memPutInt(ptr + 16, drawParameters.baseInstance);

                drawCount++;
            }

            byteBuffer.position(0);

            indirectBuffer.recordCopyCmd(byteBuffer);

            nvkCmdBindVertexBuffers(commandBuffer, 0, 1, (stack.npointer(vertexBuffer.getId())), (stack.npointer(0)));

//            pipeline.bindDescriptorSets(Drawer.getCommandBuffer(), WorldRenderer.getInstance().getUniformBuffers(), Drawer.getCurrentFrame());
//        pipeline.bindDescriptorSets(Renderer.getCommandBuffer(), Renderer.getCurrentFrame());
            vkCmdDrawIndexedIndirect(commandBuffer, indirectBuffer.getId(), indirectBuffer.getOffset(), drawCount, stride);
        }

//            fakeIndirectCmd(Drawer.getCommandBuffer(), indirectBuffer, drawCount, uboBuffer);

//        MemoryUtil.memFree(byteBuffer);

    }

    private void updateChunkAreaOrigin(double camX, double camY, double camZ, VkCommandBuffer commandBuffer, long ptr) {
        MemoryUtil.memPutFloat(ptr + 0, (float) (this.origin.x - camX));
        MemoryUtil.memPutFloat(ptr + 4, (float) -camY);
        MemoryUtil.memPutFloat(ptr + 8, (float) (this.origin.z - camZ));

        nvkCmdPushConstants(commandBuffer, terrainDirectShader.getLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, 12, ptr);
    }

    private static void fakeIndirectCmd(VkCommandBuffer commandBuffer, IndirectBuffer indirectBuffer, int drawCount, ByteBuffer offsetBuffer) {
        Pipeline pipeline = TerrainShaderManager.getTerrainDirectShader(null);
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

    public void buildDrawBatchesDirect(TerrainRenderType terrainRenderType, double camX, double camY, double camZ) {


        boolean isTranslucent = terrainRenderType == TerrainRenderType.TRANSLUCENT;

        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            nvkCmdBindVertexBuffers(commandBuffer, 0, 1, stack.npointer(vertexBuffer.getId()), stack.npointer(0));
            updateChunkAreaOrigin(camX, camY, camZ, commandBuffer, stack.nmalloc(16));
        }

        if(isTranslucent) {
            vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
        }


        for (Iterator<DrawBuffers.DrawParameters[]> iter = sectionQueue.iterator(isTranslucent); iter.hasNext(); ) {
            DrawParameters drawParameters = iter.next()[terrainRenderType.ordinal()];

            vkCmdDrawIndexed(commandBuffer, drawParameters.indexCount, 1, drawParameters.firstIndex, drawParameters.vertexOffset, drawParameters.baseInstance);

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

    public static class DrawParameters {
        int indexCount;
        int firstIndex;
        int vertexOffset;
        int baseInstance;
        AreaBuffer.Segment vertexBufferSegment = new AreaBuffer.Segment();
        AreaBuffer.Segment indexBufferSegment;
        boolean ready = false;

        DrawParameters(boolean translucent) {
            if(translucent) {
                indexBufferSegment = new AreaBuffer.Segment();
            }
        }

        public void reset(ChunkArea chunkArea) {
            this.indexCount = 0;
            this.firstIndex = 0;
            this.vertexOffset = 0;

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
