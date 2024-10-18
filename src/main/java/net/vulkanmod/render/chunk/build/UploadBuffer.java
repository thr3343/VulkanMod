package net.vulkanmod.render.chunk.build;

import net.vulkanmod.render.vertex.TerrainBufferBuilder;

import java.nio.ByteBuffer;

public class UploadBuffer {
    public final int indexCount;
    public final boolean autoIndices;
    public final boolean indexOnly;
    private final int vertexBufferSize;
    private final int indexBufferSize;
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
            this.vertexBufferSize = (drawState.vertexBufferSize());
        else
            this.vertexBufferSize = 0;

        if (!drawState.sequentialIndex())
            this.indexBufferSize = (drawState.indexCount() * Short.BYTES);
        else
            this.indexBufferSize = 0;
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

    public int getVertexBuffer() {
        return vertexBufferSize;
    }

    public int getIndexBuffer() {
        return indexBufferSize;
    }

    public void release() {
//        if (vertexBuffer != null)
//            MemoryUtil.memFree(vertexBuffer);
//        if (indexBuffer != null)
//            MemoryUtil.memFree(indexBuffer);
    }
}
