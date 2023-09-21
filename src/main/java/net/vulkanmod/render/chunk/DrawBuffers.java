package net.vulkanmod.render.chunk;

import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.render.virtualSegmentBuffer;
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

import static net.vulkanmod.render.VBOUtil.TvirtualBufferIdx;
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
    public final int areaIndex;
    private final Vector3i origin;
    private static final long vkCmdDrawIndexed = Vulkan.getDevice().getCapabilities().vkCmdDrawIndexed;
    private static final long vkCmdBindVertexBuffers = Vulkan.getDevice().getCapabilities().vkCmdBindVertexBuffers;
    private static final long functionAddress = Vulkan.getDevice().getCapabilities().vkCmdPushConstants;

    private boolean allocated = false;
    public AreaBuffer SVertexBuffer;
    public AreaBuffer TVertexBuffer;
//    public AreaBuffer indexBuffer;

    public DrawBuffers(int index, Vector3i origin) {
        this.areaIndex = index;

        this.origin = origin;
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
        drawParameters.baseInstance= yOffset<<14|zOffset1<<7|xOffset1;
//        drawParameters.xOffset= xOffset1;
//        drawParameters.yOffset= yOffset;
//        drawParameters.zOffset= zOffset1;
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
//            drawParameters.firstIndex = drawParameters.indexBufferSegment.getOffset() / INDEX_SIZE;
            firstIndex = this.configureIndexFormat(drawParameters, drawParameters.index, buffer, xOffset, yOffset)/INDEX_SIZE;;
        }

//        AreaUploadManager.INSTANCE.enqueueParameterUpdate(
//                new ParametersUpdate(drawParameters, buffer.indexCount, firstIndex, vertexOffset));

        drawParameters.indexCount = buffer.indexCount();
        drawParameters.firstIndex = firstIndex;
        drawParameters.vertexOffset = vertexOffset;

//        Renderer.getDrawer().getQuadsIndexBuffer().checkCapacity(buffer.indexCount() * 2 / 3);

        buffer.release();

        return drawParameters;
    }

    private int configureIndexFormat(DrawParameters drawParameters, int index, UploadBuffer parameters, int xOffset, int zOffset) {
//        boolean bl = !parameters.format().equals(this.vertexFormat);

        final int size = parameters.indexSize();
        if(drawParameters.indexBufferSegment==null || !TvirtualBufferIdx.isAlreadyLoaded(drawParameters.index, size))
        {
            drawParameters.indexBufferSegment = TvirtualBufferIdx.allocSubSection(this.areaIndex, index, size, TerrainRenderType.TRANSLUCENT);
        }


        AreaUploadManager.INSTANCE.uploadAsync2(TvirtualBufferIdx, TvirtualBufferIdx.bufferPointerSuperSet, TvirtualBufferIdx.size_t, drawParameters.indexBufferSegment.i2(), size, parameters.getIndexBuffer());
//            this.vertOff= fakeVertexBuffer.i2()>>5;
//        final int floor = (Mth.floor( xOffset - WorldRenderer.getCameraPos().x));
        return drawParameters.indexBufferSegment.i2();
    }

    public void buildDrawBatchesIndirect(IndirectBuffer indirectBuffer, double camX, double camY, double camZ, boolean isTranslucent, long layout, VkCommandBuffer commandBuffer) {
        int stride = 20;

        int drawCount = 0;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = stack.calloc(20 * sSectionQueue.size());
            long bufferPtr = memAddress0(byteBuffer);


            var iterator = (isTranslucent ? this.tSectionQueue : this.sSectionQueue).iterator(isTranslucent);
            while (iterator.hasNext()) {
                DrawParameters drawParameters = iterator.next();





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


            LongBuffer pVertexBuffer = stack.longs((isTranslucent ? TVertexBuffer : SVertexBuffer).getId());
            LongBuffer pOffset = stack.longs(0);
            vkCmdBindVertexBuffers(commandBuffer, 0, pVertexBuffer, pOffset);
            float a = (float) (this.origin.x/* + (drawParameters.baseInstance&0x7f)*/ - camX);
            float b = (float) -camY;
            float c = (float) (this.origin.z /*+ (drawParameters.baseInstance >> 7 & 0x7f)*/ - camZ);

            vkCmdPushConstants(commandBuffer, layout, VK_SHADER_STAGE_VERTEX_BIT, 0, stack.floats(a, b, c));
        }

//            pipeline.bindDescriptorSets(Drawer.getCommandBuffer(), WorldRenderer.getInstance().getUniformBuffers(), Drawer.getCurrentFrame());
        vkCmdDrawIndexedIndirect(commandBuffer, indirectBuffer.getId(), indirectBuffer.getOffset(), drawCount, stride);

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

    public void buildDrawBatchesDirect(double camX, double camY, double camZ, boolean isTranslucent, long layout, long address1, boolean noBindless) {



        try (MemoryStack stack = MemoryStack.stackGet().push()) {

            FloatBuffer pValues = stack.floats((float) (this.origin.x/* + (drawParameters.baseInstance&0x7f)*/ - camX), (float) -camY, (float) (this.origin.z /*+ (drawParameters.baseInstance >> 7 & 0x7f)*/ - camZ));
            callPJPV(address1, layout, VK_SHADER_STAGE_VERTEX_BIT, 0, 12, memAddress0(pValues), functionAddress);

            final long npointer = stack.npointer((isTranslucent ? TVertexBuffer : SVertexBuffer).getId());
            final long nmalloc = stack.nmalloc(POINTER_SIZE, POINTER_SIZE);
            MemoryUtil.memPutAddress(nmalloc, 0);
            if (!noBindless) {
                callPPPV(address1, 0, 1, npointer, nmalloc, vkCmdBindVertexBuffers);
            }

            if (noBindless) drawIndexed(isTranslucent, address1, npointer, nmalloc);
            else drawIndexedBindless(isTranslucent, address1);

        }

    }

    private void drawIndexedBindless(boolean isTranslucent, long address1) {
        for (Iterator<DrawParameters> iterator = (isTranslucent ? this.tSectionQueue : sSectionQueue).iterator(isTranslucent); iterator.hasNext(); ) {
            DrawParameters drawParameters = iterator.next();
            callPV(address1, drawParameters.indexCount, 1, drawParameters.firstIndex, drawParameters.vertexOffset, drawParameters.baseInstance, vkCmdDrawIndexed);
        }
    }
    private void drawIndexed(boolean isTranslucent, long address1, long npointer, long nmalloc) {
        for (Iterator<DrawParameters> iterator = (isTranslucent ? this.tSectionQueue : sSectionQueue).iterator(isTranslucent); iterator.hasNext(); ) {
            DrawParameters drawParameters = iterator.next();
            VUtil.UNSAFE.putInt(nmalloc, drawParameters.vertexOffset * 20);
            callPPPV(address1, 0, 1, npointer, nmalloc, vkCmdBindVertexBuffers);
            callPV(address1, drawParameters.indexCount, 1, drawParameters.firstIndex, 0, drawParameters.baseInstance, vkCmdDrawIndexed);
        }
    }

    public void releaseBuffers() {
        if(!this.allocated)
            return;

        this.SVertexBuffer.freeBuffer();
//        this.TVertexBuffer.freeBuffer();
        TvirtualBufferIdx.freeRange(this.areaIndex);
//        this.indexBuffer.freeBuffer();

        this.SVertexBuffer = null;
//        this.indexBuffer = null;
        this.allocated = false;
    }

    public boolean isAllocated() {
        return allocated;
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

        DrawParameters(int index, boolean translucent) {
            this.index = index;
//            if(translucent) {
//                indexBufferSegment = new AreaBuffer.Segment();
//            }
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
                  TvirtualBufferIdx.addFreeableRange(indexBufferSegment);
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
