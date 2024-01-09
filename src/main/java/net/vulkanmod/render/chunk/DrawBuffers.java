package net.vulkanmod.render.chunk;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.memory.IndirectBuffer;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.EnumMap;

import static org.lwjgl.vulkan.VK10.*;

public class DrawBuffers {

    private static final int VERTEX_SIZE = PipelineManager.TERRAIN_VERTEX_FORMAT.getVertexSize();
    private static final int INDEX_SIZE = Short.BYTES;
    final int index;
    private final Vector3i origin;
    private final int minHeight;

    private boolean allocated = false;
    AreaBuffer vertexBuffer, indexBuffer;
    private final EnumMap<TerrainRenderType, Boolean> tst = new EnumMap<>(TerrainRenderType.class);
    private final EnumMap<TerrainRenderType, AreaBuffer> areaBufferTypes = new EnumMap<>(TerrainRenderType.class);

    //Need ugly minHeight Parameter to fix custom world heights (exceeding 384 Blocks in total)
    public DrawBuffers(int index, Vector3i origin, int minHeight) {

        this.index = index;
        this.origin = origin;
        this.minHeight = minHeight;
    }

    public void allocateBuffers() {
        tst.put(TerrainRenderType.CUTOUT_MIPPED, false);
        tst.put(TerrainRenderType.TRANSLUCENT, false);
        if(!Initializer.CONFIG.perRenderTypeAreaBuffers) vertexBuffer = new AreaBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, 2097152 /*RenderType.BIG_BUFFER_SIZE>>1*/, VERTEX_SIZE);

        this.allocated = true;
    }

    public void upload(int xOffset, int yOffset, int zOffset, UploadBuffer buffer, DrawParameters drawParameters, TerrainRenderType renderType) {
        int vertexOffset = drawParameters.vertexOffset;
        int firstIndex = 0;
        drawParameters.baseInstance = encodeSectionOffset(xOffset, yOffset, zOffset);

        if(!buffer.indexOnly) {
            this.getAreaBufferCheckedAlloc(renderType).upload(buffer.getVertexBuffer(), drawParameters.vertexBufferSegment);
//            drawParameters.vertexOffset = drawParameters.vertexBufferSegment.getOffset() / VERTEX_SIZE;
            vertexOffset = drawParameters.vertexBufferSegment.getOffset() / VERTEX_SIZE;
            this.tst.put(renderType, true);

            //debug
//            if(drawParameters.vertexBufferSegment.getOffset() % VERTEX_SIZE != 0) {
//                throw new RuntimeException("misaligned vertex buffer");
//            }
        }

        if(!buffer.autoIndices) {
            if (this.indexBuffer==null)
                this.indexBuffer = new AreaBuffer(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 786432 /*RenderType.SMALL_BUFFER_SIZE*/, INDEX_SIZE);
            this.indexBuffer.upload(buffer.getIndexBuffer(), drawParameters.indexBufferSegment);
//            drawParameters.firstIndex = drawParameters.indexBufferSegment.getOffset() / INDEX_SIZE;
            firstIndex = drawParameters.indexBufferSegment.getOffset() / INDEX_SIZE;
            this.tst.put(renderType, true);
        }

//        AreaUploadManager.INSTANCE.enqueueParameterUpdate(
//                new ParametersUpdate(drawParameters, buffer.indexCount, firstIndex, vertexOffset));

        drawParameters.indexCount = buffer.indexCount;
        drawParameters.firstIndex = firstIndex;
        drawParameters.vertexOffset = vertexOffset;



        buffer.release();


    }
    //Exploit Pass by Reference to allow all keys to be the same AreaBufferObject (if perRenderTypeAreaBuffers is disabled)
    private AreaBuffer getAreaBufferCheckedAlloc(TerrainRenderType r) {
        return this.areaBufferTypes.computeIfAbsent(r, t -> Initializer.CONFIG.perRenderTypeAreaBuffers ? new AreaBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, r.initialSize, VERTEX_SIZE) : this.vertexBuffer);
    }
    private AreaBuffer getAreaBuffer(TerrainRenderType r) {
        return this.areaBufferTypes.get(r);
    }

    private boolean hasRenderType(TerrainRenderType r) {
        return this.areaBufferTypes.containsKey(r);
    }

    private int encodeSectionOffset(int xOffset, int yOffset, int zOffset) {
        final int xOffset1 = (xOffset & 127);
        final int zOffset1 = (zOffset & 127);
        final int yOffset1 = (yOffset-this.minHeight & 127);
        return yOffset1 << 16 | zOffset1 << 8 | xOffset1;
    }

    private void updateChunkAreaOrigin(double camX, double camY, double camZ, VkCommandBuffer commandBuffer, long layout, FloatBuffer mPtr) {

            float x = (float)(camX-(this.origin.x));
            float y = (float)(camY-(this.origin.y));
            float z = (float)(camZ-(this.origin.z));

            Matrix4f MVP = new Matrix4f().set(VRenderSystem.MVP.buffer.asFloatBuffer());
            Matrix4f MV = new Matrix4f().set(VRenderSystem.modelViewMatrix.buffer.asFloatBuffer());

            MVP.translate(-x, -y, -z).get(mPtr);
            MV.translate(-x, -y, -z).get(16,mPtr);

            vkCmdPushConstants(commandBuffer, layout, VK_SHADER_STAGE_VERTEX_BIT, 0, mPtr);
    }
    public void buildDrawBatchesIndirect(IndirectBuffer indirectBuffer, StaticQueue<DrawParameters> queue, TerrainRenderType terrainRenderType, double camX, double camY, double camZ, long layout) {


        if(queue==null || queue.size() == 0) return;


        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        MemoryStack stack = MemoryStack.stackPush();
        boolean isTranslucent = terrainRenderType == TerrainRenderType.TRANSLUCENT;

        if(tst.get(terrainRenderType)) {
            uploadIndirectCmds(indirectBuffer, queue, terrainRenderType, stack, isTranslucent);
        }

        if (isTranslucent) {
            vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
        }
        LongBuffer pVertexBuffer = stack.longs(getAreaBuffer(terrainRenderType).getId());
        LongBuffer pOffset = stack.longs(0);
        vkCmdBindVertexBuffers(commandBuffer, 0, pVertexBuffer, pOffset);

//            pipeline.bindDescriptorSets(Drawer.getCommandBuffer(), WorldRenderer.getInstance().getUniformBuffers(), Drawer.getCurrentFrame());
        updateChunkAreaOrigin(camX, camY, camZ, commandBuffer, layout, stack.mallocFloat(32));
        vkCmdDrawIndexedIndirect(commandBuffer, indirectBuffer.getId(), indirectBuffer.getBaseOffset(this.index), queue.size(), 20);

//            fakeIndirectCmd(Drawer.getCommandBuffer(), indirectBuffer, drawCount, uboBuffer);

//        MemoryUtil.memFree(byteBuffer);
        MemoryStack.stackPop();

    }

    private void uploadIndirectCmds(IndirectBuffer indirectBuffer, StaticQueue<DrawParameters> queue, TerrainRenderType terrainRenderType, MemoryStack stack, boolean isTranslucent) {
        int drawCount = 0;
        ByteBuffer byteBuffer = stack.calloc(20 * queue.size());
        long bufferPtr = MemoryUtil.memAddress0(byteBuffer);



        for (var iterator = queue.iterator(isTranslucent); iterator.hasNext(); drawCount++) {

            DrawParameters drawParameters = iterator.next();

            //Debug
//            BlockPos o = section.origin;
////            BlockPos pos = new BlockPos(-2188, 65, -1674);
//
////            Vec3 cameraPos = WorldRenderer.getCameraPos();
//            BlockPos pos = new BlockPos(Minecraft.getInstance().getCameraEntity().blockPosition());
//            if(o.getX() <= pos.getX() && o.getY() <= pos.getY() && o.getZ() <= pos.getZ() &&
//                    o.getX() + 16 >= pos.getX() && o.getY() + 16 >= pos.getY() && o.getZ() + 16 >= pos.getZ()) {
//                System.nanoTime();
//
//                }
//
//            }


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
            MemoryUtil.memPutInt(ptr + 16, drawParameters.baseInstance);


        }


        byteBuffer.position(0);

        indirectBuffer.recordCopyCmd(byteBuffer, this.index);

        tst.put(terrainRenderType, false);
    }

    private static void fakeIndirectCmd(VkCommandBuffer commandBuffer, IndirectBuffer indirectBuffer, int drawCount, ByteBuffer offsetBuffer) {
        Pipeline pipeline = PipelineManager.getTerrainDirectShader(null);
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

    public void buildDrawBatchesDirect(StaticQueue<DrawParameters> queue, TerrainRenderType terrainRenderType, double camX, double camY, double camZ, long layout) {
        if(queue==null || queue.size() == 0) return;
        boolean isTranslucent = terrainRenderType == TerrainRenderType.TRANSLUCENT;

        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            nvkCmdBindVertexBuffers(commandBuffer, 0, 1, stack.npointer(getAreaBuffer(terrainRenderType).getId()), stack.npointer(0));
            updateChunkAreaOrigin(camX, camY, camZ, commandBuffer, layout, stack.mallocFloat(32));
        }


        if(isTranslucent) {
            vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
        }

        for (var iterator = queue.iterator(isTranslucent); iterator.hasNext(); ) {
            final DrawParameters drawParameters = iterator.next();
            vkCmdDrawIndexed(commandBuffer, drawParameters.indexCount, 1, drawParameters.firstIndex, drawParameters.vertexOffset, drawParameters.baseInstance);

        }
    }

    public void releaseBuffers() {
        if(!this.allocated)
            return;

        if(this.vertexBuffer==null) {
            this.areaBufferTypes.values().forEach(AreaBuffer::freeBuffer);
        }
        else this.vertexBuffer.freeBuffer();
        this.areaBufferTypes.clear();
        if(this.indexBuffer!=null) this.indexBuffer.freeBuffer();

        this.tst.put(TerrainRenderType.CUTOUT_MIPPED, false);
        this.tst.put(TerrainRenderType.TRANSLUCENT, false);
        this.vertexBuffer = null;
        this.indexBuffer = null;
        this.allocated = false;
    }

    public boolean isAllocated() {
        return allocated;
    }

    public static class DrawParameters {
        int indexCount, firstIndex, vertexOffset, baseInstance;
        final AreaBuffer.Segment vertexBufferSegment = new AreaBuffer.Segment();
        final AreaBuffer.Segment indexBufferSegment;
        boolean ready = false;

        DrawParameters(boolean translucent) {
            indexBufferSegment = translucent ? new AreaBuffer.Segment() : null;
        }

        public void reset(DrawBuffers drawBuffers, TerrainRenderType r) {
            this.indexCount = 0;
            this.firstIndex = 0;
            this.vertexOffset = 0;

            int segmentOffset = this.vertexBufferSegment.getOffset();
            if(drawBuffers.hasRenderType(r) && segmentOffset != -1) {
//                this.chunkArea.drawBuffers.vertexBuffer.setSegmentFree(segmentOffset);
                drawBuffers.getAreaBuffer(r).setSegmentFree(this.vertexBufferSegment);
                drawBuffers.tst.put(r, true);
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
