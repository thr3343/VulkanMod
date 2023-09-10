package net.vulkanmod.render.chunk.build;

import net.vulkanmod.render.chunk.util.Util;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class UploadBuffer {

    public final int indexCount;
    public final boolean autoIndices;
    public final boolean indexOnly;
    private final long vertexBuffer;
    private final long indexBuffer;
    public final int vertSize;
    public final int indexSize;

    //debug
    private boolean released = false;

    public UploadBuffer(TerrainBufferBuilder.RenderedBuffer renderedBuffer) {
        TerrainBufferBuilder.DrawState drawState = renderedBuffer.drawState();
        this.indexCount = drawState.indexCount();
        this.autoIndices = drawState.sequentialIndex();
        this.indexOnly = drawState.indexOnly();
        this.vertSize =renderedBuffer.size();
        this.indexSize =renderedBuffer.size2();
        if(!this.indexOnly)
            this.vertexBuffer = Util.createCopy(renderedBuffer.vertexBufferPtr(), renderedBuffer.size());
        else
            this.vertexBuffer = 0;

        if(!drawState.sequentialIndex())
            this.indexBuffer = Util.createCopy(renderedBuffer.indexBufferPtr(), renderedBuffer.size2());
        else
            this.indexBuffer = 0;
    }

    public int indexCount() { return indexCount; }

    public long getVertexBuffer() { return vertexBuffer; }

    public long getIndexBuffer() { return indexBuffer; }

    public void release() {
        if(vertexBuffer != 0)
            MemoryUtil.nmemFree(vertexBuffer);
        if(indexBuffer != 0)
            MemoryUtil.nmemFree(indexBuffer);
        this.released = true;
    }
}
