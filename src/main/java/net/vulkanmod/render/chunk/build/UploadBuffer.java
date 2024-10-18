package net.vulkanmod.render.chunk.build;

import net.vulkanmod.render.vertex.TerrainBufferBuilder;

import java.nio.ByteBuffer;

public class UploadBuffer {
    public final int indexCount;
    public final boolean autoIndices;
    public final boolean indexOnly;
    private final ByteBuffer vertexBuffer;
    private final ByteBuffer indexBuffer;
    private final long hostId;
    private final int vtxOffset;
    private final int idxOffset;

    public UploadBuffer(TerrainBufferBuilder.RenderedBuffer renderedBuffer, long id) {
        TerrainBufferBuilder.DrawState drawState = renderedBuffer.drawState();
        this.indexCount = drawState.indexCount();
        this.autoIndices = drawState.sequentialIndex();
        this.indexOnly = drawState.indexOnly();
        this.hostId = id;
        this.vtxOffset= renderedBuffer.vtxOffset();
        this.idxOffset= renderedBuffer.idxOffset();

        if (!this.indexOnly)
            this.vertexBuffer = (renderedBuffer.vertexBuffer());
        else
            this.vertexBuffer = null;

        if (!drawState.sequentialIndex())
            this.indexBuffer = (renderedBuffer.indexBuffer());
        else
            this.indexBuffer = null;
    }

    public long getHostId() {
        return hostId;
    }

    public int getVtxOffset() {
        return vtxOffset;
    }

    public int getIdxOffset() {
        return idxOffset;
    }

    public int indexCount() {
        return indexCount;
    }

    public ByteBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public ByteBuffer getIndexBuffer() {
        return indexBuffer;
    }

    public void release() {
//        if (vertexBuffer != null)
//            MemoryUtil.memFree(vertexBuffer);
//        if (indexBuffer != null)
//            MemoryUtil.memFree(indexBuffer);
    }
}
