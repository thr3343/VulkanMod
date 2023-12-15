package net.vulkanmod.render.chunk;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.memory.IndirectBuffer;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.EnumMap;
import java.util.Set;

import static net.vulkanmod.render.vertex.TerrainRenderType.TRANSLUCENT;
import static net.vulkanmod.render.vertex.TerrainRenderType.getActiveLayers;
import static org.lwjgl.vulkan.VK10.*;

public class DrawBuffers {

    private static final int VERTEX_SIZE = TerrainShaderManager.TERRAIN_VERTEX_FORMAT.getVertexSize();
    private static final int INDEX_SIZE = Short.BYTES;
    public final int areaIndex;
    private final Vector3i origin;
    private final int minHeight;

    private boolean allocated = false;
    AreaBuffer indexBuffer;
    AreaBuffer vertexBuffer;
    private final EnumMap<TerrainRenderType, AreaBuffer> areaBufferTypes = new EnumMap<>(TerrainRenderType.class);

    //Help JIT optimisations by hardcoding the queue size to the max possible ChunkArea limit
//    final StaticQueue<DrawParameters> sectionQueue = new StaticQueue<>(512);
    private final EnumMap<TerrainRenderType, StaticQueue<DrawParameters>> sectionQueues = new EnumMap<>(TerrainRenderType.class);

    public DrawBuffers(int areaIndex, Vector3i origin, int minHeight) {

        this.areaIndex = areaIndex;
        this.origin = origin;
        this.minHeight = minHeight;
    }

    public void allocateBuffers() {
//        getActiveLayers().forEach(t -> areaBufferTypes.put(t, new AreaBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, t.initialSize, VERTEX_SIZE)));
//        this.indexBuffer = new AreaBuffer(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 1000000, INDEX_SIZE);
        if(!Initializer.CONFIG.perRenderTypeAreaBuffers) vertexBuffer = new AreaBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, 3500000, VERTEX_SIZE);
        //Don't allocate sectionQueues in constructor to avoid Unnecessary Heap Allocations
        getActiveLayers().forEach(t -> sectionQueues.put(t, new StaticQueue<>(512)));
        this.allocated = true;
    }


    public DrawParameters upload(int xOffset, int yOffset, int zOffset, UploadBuffer buffer, DrawParameters drawParameters, TerrainRenderType r) {
        drawParameters= drawParameters == null ? new DrawParameters(r == TRANSLUCENT) : drawParameters;
        int vertexOffset = drawParameters.vertexOffset;
        int firstIndex = 0;
        drawParameters.baseInstance = encodeSectionOffset(xOffset, yOffset, zOffset);

        if(!buffer.indexOnly) {
            getAreaBufferCheckedAlloc(r).upload(buffer.getVertexBuffer(), drawParameters.vertexBufferSegment);
//            drawParameters.vertexOffset = drawParameters.vertexBufferSegment.getOffset() / VERTEX_SIZE;
            vertexOffset = drawParameters.vertexBufferSegment.getOffset() / VERTEX_SIZE;

            //debug
//            if(drawParameters.vertexBufferSegment.getOffset() % VERTEX_SIZE != 0) {
//                throw new RuntimeException("misaligned vertex buffer");
//            }
        }

        if(!buffer.autoIndices) {
            if (this.indexBuffer==null)
                this.indexBuffer = new AreaBuffer(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, TRANSLUCENT.initialSize, INDEX_SIZE);
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
        final int yOffset1 = (yOffset-this.minHeight & 127) ;
        return yOffset1 << 16 | zOffset1 << 8 | xOffset1;
    }

    private void updateChunkAreaOrigin(double camX, double camY, double camZ, VkCommandBuffer commandBuffer, long layout, FloatBuffer mPtr) {


        float camX1 = (float)(camX-(this.origin.x));
        float camY1 = (float)(camY-(this.origin.y));
        float camZ1 = (float)(camZ-(this.origin.z));
        VRenderSystem.translateMVP(-camX1, -camY1, -camZ1, mPtr);
        vkCmdPushConstants(commandBuffer, layout, VK_SHADER_STAGE_VERTEX_BIT, 0, mPtr);
//        VRenderSystem.translateMVP(camX1, camY1, camZ1);
    }
    public void buildDrawBatchesIndirect(IndirectBuffer indirectBuffer, TerrainRenderType terrainRenderType, double camX, double camY, double camZ, long layout) {

        final StaticQueue<DrawParameters> sectionQueue = this.sectionQueues.get(terrainRenderType);

        if(!hasRenderType(terrainRenderType)) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = stack.calloc(20 * sectionQueue.size());
            long bufferPtr = MemoryUtil.memAddress0(byteBuffer);


            boolean isTranslucent = terrainRenderType == TRANSLUCENT;

            VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
            if (isTranslucent) {
                vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
            }


            int drawCount = 0;
            for (var iterator = sectionQueue.iterator(isTranslucent); iterator.hasNext(); ) {

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



                drawCount++;
            }

            if(drawCount == 0) {
                return;
            }

//            if(drawCount!= size) Initializer.LOGGER.warn(drawCount+"-->"+ size);

            byteBuffer.position(0);

            indirectBuffer.recordCopyCmd(byteBuffer);


            nvkCmdBindVertexBuffers(commandBuffer, 0, 1, stack.npointer(getAreaBuffer(terrainRenderType).getId()), stack.npointer(0));

//            pipeline.bindDescriptorSets(Drawer.getCommandBuffer(), WorldRenderer.getInstance().getUniformBuffers(), Drawer.getCurrentFrame());
            updateChunkAreaOrigin(camX, camY, camZ, commandBuffer, layout, stack.mallocFloat(16));
            vkCmdDrawIndexedIndirect(commandBuffer, indirectBuffer.getId(), indirectBuffer.getOffset(), drawCount, 20);
        }

//            fakeIndirectCmd(Drawer.getCommandBuffer(), indirectBuffer, drawCount, uboBuffer);

//        MemoryUtil.memFree(byteBuffer);


    }

    public void buildDrawBatchesDirect(TerrainRenderType terrainRenderType, double camX, double camY, double camZ, long layout) {
        if(!this.hasRenderType(terrainRenderType)) return;
        boolean isTranslucent = terrainRenderType == TRANSLUCENT;

        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            nvkCmdBindVertexBuffers(commandBuffer, 0, 1, stack.npointer(getAreaBuffer(terrainRenderType).getId()), stack.npointer(0));
            updateChunkAreaOrigin(camX, camY, camZ, commandBuffer, layout, stack.mallocFloat(16));
        }


        if(isTranslucent) {
            vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
        }

        for (var iterator = this.sectionQueues.get(terrainRenderType).iterator(isTranslucent); iterator.hasNext(); ) {
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

        if(this.indexBuffer!=null) this.indexBuffer.freeBuffer();
        this.areaBufferTypes.clear();


        this.indexBuffer = null;
        this.vertexBuffer = null;
        this.allocated = false;
    }

    public boolean isAllocated() {
        return allocated;
    }

    public void addMeshlet(TerrainRenderType r, DrawParameters drawParameters) {
        this.sectionQueues.get(r).add(drawParameters);
    }

    public void clear() {
        this.sectionQueues.values().forEach(StaticQueue::clear);
    }

    public void addRenderTypes(Set<TerrainRenderType> renderTypes) {
        renderTypes.forEach(renderType ->  this.sectionQueues.computeIfAbsent(renderType, r->new StaticQueue<>(512)));
    }

//    public void clear(TerrainRenderType r) {
//        this.sectionQueues.get(r).clear();
//    }

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


        public void reset(DrawBuffers drawBuffers, TerrainRenderType r) {
                    this.indexCount = 0;
                    this.firstIndex = 0;
                    this.vertexOffset = 0;
            int segmentOffset = this.vertexBufferSegment.getOffset();
            if(drawBuffers.hasRenderType(r) && segmentOffset != -1) {
//                this.chunkArea.drawBuffers.vertexBuffer.setSegmentFree(segmentOffset);
                drawBuffers.getAreaBuffer(r).setSegmentFree(this.vertexBufferSegment);
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
