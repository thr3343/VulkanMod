package net.vulkanmod.render.chunk;

import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.IndirectBuffer;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.ShaderManager;
import net.vulkanmod.vulkan.util.VUtil;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.Iterator;

import static org.lwjgl.system.Checks.check;
import static org.lwjgl.system.JNI.*;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memAddress0;
import static org.lwjgl.system.Pointer.POINTER_SIZE;
import static org.lwjgl.vulkan.VK10.*;

public class DrawBuffers {

    final StaticQueue<DrawParameters> sSectionQueue = new StaticQueue<>(512);
    final StaticQueue<DrawParameters> tSectionQueue = new StaticQueue<>(512);
    private static final int VERTEX_SIZE = ShaderManager.TERRAIN_VERTEX_FORMAT.getVertexSize();
    private static final int INDEX_SIZE = Short.BYTES;
    private final Vector3i origin;
    private static final long vkCmdDrawIndexed = Vulkan.getDevice().getCapabilities().vkCmdDrawIndexed;
    private static final long vkCmdBindVertexBuffers = Vulkan.getDevice().getCapabilities().vkCmdBindVertexBuffers;

    private boolean allocated = false;
    public AreaBuffer vertexBuffer;
    public AreaBuffer indexBuffer;

    public DrawBuffers(Vector3i origin) {

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
        final int xOffset1 = (xOffset & 127);
        final int zOffset1 = (zOffset & 127);
        drawParameters.baseInstance= yOffset<<14|zOffset1<<7|xOffset1;
//        drawParameters.xOffset= xOffset1;
//        drawParameters.yOffset= yOffset;
//        drawParameters.zOffset= zOffset1;
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

    public void buildDrawBatchesIndirect(IndirectBuffer indirectBuffer, double camX, double camY, double camZ, boolean isTranslucent, long layout) {
        int stride = 20;

        int drawCount = 0;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = stack.calloc(20 * sSectionQueue.size());
            long bufferPtr = memAddress0(byteBuffer);


            if (isTranslucent) {
                vkCmdBindIndexBuffer(Renderer.getCommandBuffer(), this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
            }

            var iterator = (isTranslucent ? this.tSectionQueue : this.sSectionQueue).iterator(isTranslucent);
            while (iterator.hasNext()) {
                DrawParameters drawParameters = iterator.next();



                if (drawParameters.indexCount == 0) {
                    continue;
                }

            //TODO
            if(!drawParameters.ready && drawParameters.vertexBufferSegment.getOffset() != -1) {
                if(!drawParameters.vertexBufferSegment.isReady())
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
                MemoryUtil.memPutInt(ptr + 16, drawParameters.baseInstance);


                drawCount++;
            }

            if (drawCount == 0) {
                return;
            }


            byteBuffer.position(0);

            indirectBuffer.recordCopyCmd(byteBuffer);


            LongBuffer pVertexBuffer = stack.longs(vertexBuffer.getId());
            LongBuffer pOffset = stack.longs(0);
            vkCmdBindVertexBuffers(Renderer.getCommandBuffer(), 0, pVertexBuffer, pOffset);
            float a = (float) (this.origin.x/* + (drawParameters.baseInstance&0x7f)*/ - camX);
            float b = (float) -camY;
            float c = (float) (this.origin.z /*+ (drawParameters.baseInstance >> 7 & 0x7f)*/ - camZ);

            vkCmdPushConstants(Renderer.getCommandBuffer(), layout, VK_SHADER_STAGE_VERTEX_BIT, 0, stack.floats(a, b, c));
        }

//            pipeline.bindDescriptorSets(Drawer.getCommandBuffer(), WorldRenderer.getInstance().getUniformBuffers(), Drawer.getCurrentFrame());
        vkCmdDrawIndexedIndirect(Renderer.getCommandBuffer(), indirectBuffer.getId(), indirectBuffer.getOffset(), drawCount, stride);

//            fakeIndirectCmd(Drawer.getCommandBuffer(), indirectBuffer, drawCount, uboBuffer);

//        MemoryUtil.memFree(byteBuffer);

    }

    private static void fakeIndirectCmd(VkCommandBuffer commandBuffer, IndirectBuffer indirectBuffer, int drawCount, ByteBuffer offsetBuffer) {
        Pipeline pipeline = ShaderManager.shaderManager.terrainDirectShader;
//        Drawer.getInstance().bindPipeline(pipeline);
//        pipeline.bindDescriptorSets(Renderer.getCommandBuffer(), Renderer.getCurrentFrame());
//        pipeline.bindDescriptorSets(Drawer.getCommandBuffer(), WorldRenderer.getInstance().getUniformBuffers(), Drawer.getCurrentFrame());

        ByteBuffer buffer = indirectBuffer.getByteBuffer();
        long address = memAddress0(buffer);
        long offsetAddress = memAddress0(offsetBuffer);
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

    public void buildDrawBatchesDirect(double camX, double camY, double camZ, boolean isTranslucent, long layout) {


        final VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        if(isTranslucent) {
            vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
        }

        try (MemoryStack stack = MemoryStack.stackGet().push()) {
            final long address = commandBuffer.address();

            FloatBuffer pValues = stack.floats((float) (this.origin.x/* + (drawParameters.baseInstance&0x7f)*/ - camX), (float) -camY, (float) (this.origin.z /*+ (drawParameters.baseInstance >> 7 & 0x7f)*/ - camZ));
            nvkCmdPushConstants(commandBuffer, layout, VK_SHADER_STAGE_VERTEX_BIT, 0, 12, memAddress0(pValues));

            final long npointer = stack.npointer(vertexBuffer.getId());
            final long nmalloc = stack.nmalloc(POINTER_SIZE, POINTER_SIZE);
            if(isTranslucent) drawTranslucent(address, npointer, nmalloc);
            else drawSolid(address, npointer, nmalloc);
        }

    }

    private void drawTranslucent(long address, long pVertexBuffer, long pointerAddress) {
        for (Iterator<DrawParameters> iterator = this.tSectionQueue.iterator(true); iterator.hasNext(); ) {
            final DrawParameters drawParameters = iterator.next();
            VUtil.UNSAFE.putInt(pointerAddress, drawParameters.vertexOffset*20);
            callPPPV(address, 0, 1, pVertexBuffer, pointerAddress, vkCmdBindVertexBuffers);
            callPV(address, drawParameters.indexCount, 1, drawParameters.firstIndex, 0, drawParameters.baseInstance, vkCmdDrawIndexed);
        }
    }    
    private void drawSolid(long address, long pVertexBuffer, long pointerAddress) {
        for (Iterator<DrawParameters> iterator = this.sSectionQueue.iterator(); iterator.hasNext(); ) {
            DrawParameters drawParameters = iterator.next();
            VUtil.UNSAFE.putInt(pointerAddress, drawParameters.vertexOffset * 20);
            callPPPV(address, 0, 1, pVertexBuffer, pointerAddress, vkCmdBindVertexBuffers);
            callPV(address, drawParameters.indexCount, 1, drawParameters.firstIndex, 0, drawParameters.baseInstance, vkCmdDrawIndexed);
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
        public int baseInstance;
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
