package net.vulkanmod.render.vertex;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.Device;
import net.vulkanmod.vulkan.memory.MemoryManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCString;

import java.nio.ByteBuffer;

public class TerrainBufferBuilder {
    private static final Logger LOGGER = Initializer.LOGGER;
    private static final MemoryUtil.MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);
    private static final int minHostAlignment = Vulkan.getDevice().getHostPointerAlignment();

    private int capacity;
    protected long bufferPtr;
    protected int nextElementByte;
    private int vertexCount;

    private int renderedBufferCount;
    private int renderedBufferPointer;

    private final VertexFormat format;

    private boolean building;

    private final QuadSorter quadSorter = new QuadSorter();

    private boolean needsSorting;
    private boolean indexOnly;

    protected VertexBuilder vertexBuilder;

    public TerrainBufferBuilder(int size) {
        this.bufferPtr = ALLOCATOR.aligned_alloc(minHostAlignment, size);
        this.capacity = size;

        this.format = PipelineManager.TERRAIN_VERTEX_FORMAT;
        this.vertexBuilder = PipelineManager.TERRAIN_VERTEX_FORMAT == CustomVertexFormat.COMPRESSED_TERRAIN
                ? new VertexBuilder.CompressedVertexBuilder() : new VertexBuilder.DefaultVertexBuilder();
    }

    public void ensureCapacity() {
        this.ensureCapacity(this.format.getVertexSize() * 4);
    }

    private void ensureCapacity(int size) {
        if (this.nextElementByte + size > this.capacity) {
            this.resize((this.nextElementByte + size)<< 1);
        }
    }
    //TODO: Resize Desyncs
    private void resize(int i) {
        //TODO: Realloc does not preserve alignment: May use manual padding instead to avoid memcpys
        final long prevPtr = this.bufferPtr;
        final int oldSize = this.capacity;
        this.bufferPtr = ALLOCATOR.aligned_alloc(minHostAlignment, i);
        LibCString.nmemcpy(this.bufferPtr, prevPtr, oldSize);
        MemoryManager.getInstance().addFrameOp(() -> ALLOCATOR.aligned_free(prevPtr));

        LOGGER.info("Needed to grow BufferBuilder buffer: Old size {} bytes, new size {} bytes.", this.capacity, i);
        if (this.bufferPtr == 0L) {
            throw new OutOfMemoryError("Failed to resize buffer from " + this.capacity + " bytes to " + i + " bytes");
        }
//        else if ((this.bufferPtr & 4095) !=0 ) {
//            throw new IllegalStateException("Bad Alignment!:" + (this.bufferPtr & 4095));
//        }
        else {
            this.capacity = i;
        }
    }

    public void setupQuadSorting(float x, float y, float z) {
        this.quadSorter.setQuadSortOrigin(x, y, z);
        this.needsSorting = true;
    }

    public QuadSorter.SortState getSortState() {
        return this.quadSorter.getSortState();
    }

    public void restoreSortState(QuadSorter.SortState sortState) {
        this.vertexCount = sortState.vertexCount;
        this.nextElementByte = this.renderedBufferPointer;

        this.quadSorter.restoreSortState(sortState);

        this.indexOnly = true;
    }

    public void setIndexOnly() {
        this.indexOnly = true;
    }

    public void beginNextBatch() {
        if (this.building) {
            Initializer.LOGGER.error("Already building!");
        } else {
            this.building = true;
        }
    }

    public void setupQuadSortingPoints() {
        this.quadSorter.setupQuadSortingPoints(this.bufferPtr + this.renderedBufferPointer, this.vertexCount, this.format);
    }

    public boolean isCurrentBatchEmpty() {
        return this.vertexCount == 0;
    }

    @Nullable
    public RenderedBuffer endCurrentBatch() {
        this.ensureDrawing();
        if (this.isCurrentBatchEmpty()) {
            this.reset();
            return null;
        } else {
            RenderedBuffer renderedBuffer = this.storeCurrentBatch();
            this.reset();
            return renderedBuffer;
        }
    }

    private void ensureDrawing() {
        if (!this.building) {
            Initializer.LOGGER.error("Not building!");
        }
    }
    //TODO: Store Per size increments
    private RenderedBuffer storeCurrentBatch() {
        int indexCount = this.vertexCount / 4 * 6;
        int vertexBufferSize = !this.indexOnly ? this.vertexCount * this.format.getVertexSize() : 0;
        VertexFormat.IndexType indexType = VertexFormat.IndexType.least(indexCount);

        boolean sequentialIndexing;
        int size;

        if (this.needsSorting) {
            int indexBufferSize = indexCount * indexType.bytes;
            this.ensureCapacity(indexBufferSize);

            this.quadSorter.putSortedQuadIndices(this, indexType);

            sequentialIndexing = false;
            this.nextElementByte += indexBufferSize;
            size = vertexBufferSize + indexBufferSize;
        } else {
            sequentialIndexing = true;
            size = vertexBufferSize;
        }

        int ptr = this.renderedBufferPointer;
        this.renderedBufferPointer += size;
        ++this.renderedBufferCount;

        DrawState drawState = new DrawState(this.format.getVertexSize(), this.vertexCount, indexCount, indexType, this.indexOnly, sequentialIndexing);
        return new RenderedBuffer(ptr, drawState);
    }

    public void reset() {
        this.building = false;
        this.vertexCount = 0;

        this.indexOnly = false;
        this.needsSorting = false;
    }

    void releaseRenderedBuffer() {
        if (this.renderedBufferCount > 0 && --this.renderedBufferCount == 0) {
            this.clear();
        }

    }

    public void clear() {
        if (this.building) {
            return;
        }

        this.discard();
    }
    //TODO: Move to full clear/reset: only reset per Tick
    public void discard() {
        this.renderedBufferCount = 0;
        this.renderedBufferPointer = 0;
        this.nextElementByte = 0;
    }

    public boolean building() {
        return this.building;
    }

    public void endVertex() {
        this.nextElementByte += this.vertexBuilder.getStride();
        ++this.vertexCount;
    }

    public void vertex(float x, float y, float z, int color, float u, float v, int light, int packedNormal) {
        final long ptr = this.bufferPtr + this.nextElementByte;
        this.vertexBuilder.vertex(ptr, x, y, z, color, u, v, light, packedNormal);
        this.endVertex();
    }

    public void setBlockAttributes(BlockState blockState) {
    }

    public long getPtr() {
        return this.bufferPtr + this.nextElementByte;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public void free() {
        this.reset();
        this.discard();
//        this.extBuffer.freeBuffer();
        ALLOCATOR.aligned_free(this.bufferPtr);
    }

    public class RenderedBuffer {
        private final int pointer;
        private final DrawState drawState;
        private boolean released;
        //TODO: Why isn't pointer retained: premature reset confirm
        RenderedBuffer(int pointer, DrawState drawState) {
            this.pointer = pointer;
            this.drawState = drawState;
        }

        public ByteBuffer vertexBuffer() {
            int start = this.pointer + this.drawState.vertexBufferStart();
            int end = this.pointer + this.drawState.vertexBufferEnd();
            return MemoryUtil.memByteBuffer(TerrainBufferBuilder.this.bufferPtr + start, end - start);
        }
        //TODO: Broken Index Sorting (Likely bad, outdated or desynced Offset)
        public ByteBuffer indexBuffer() {
            int start = this.pointer + this.drawState.indexBufferStart();
            int end = this.pointer + this.drawState.indexBufferEnd();
            return MemoryUtil.memByteBuffer(TerrainBufferBuilder.this.bufferPtr + start, end - start);
        }

        public DrawState drawState() {
            return this.drawState;
        }

        public boolean isEmpty() {
            return this.drawState.vertexCount == 0;
        }

        public void release() {
            if (this.released) {
                Initializer.LOGGER.error("Buffer has already been released!");
            } else {
                TerrainBufferBuilder.this.releaseRenderedBuffer();
                this.released = true;
            }
        }
    }

    public record DrawState(int vertexSize, int vertexCount, int indexCount, VertexFormat.IndexType indexType,
                            boolean indexOnly, boolean sequentialIndex) {

        public int vertexBufferSize() {
            return this.vertexCount * this.vertexSize;
        }

        public int vertexBufferStart() {
            return 0;
        }

        public int vertexBufferEnd() {
            return this.vertexBufferSize();
        }

        public int indexBufferStart() {
            return this.indexOnly ? 0 : this.vertexBufferEnd();
        }

        public int indexBufferEnd() {
            return this.indexBufferStart() + this.indexBufferSize();
        }

        private int indexBufferSize() {
            return this.sequentialIndex ? 0 : this.indexCount * this.indexType.bytes;
        }

        public int bufferSize() {
            return this.indexBufferEnd();
        }

        public int vertexCount() {
            return this.vertexCount;
        }

        public int indexCount() {
            return this.indexCount;
        }

        public VertexFormat.IndexType indexType() {
            return this.indexType;
        }

        public boolean indexOnly() {
            return this.indexOnly;
        }

        public boolean sequentialIndex() {
            return this.sequentialIndex;
        }
    }
}
