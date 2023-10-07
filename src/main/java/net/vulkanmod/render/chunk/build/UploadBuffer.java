package net.vulkanmod.render.chunk.build;

import net.vulkanmod.render.chunk.util.Util;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import org.lwjgl.system.MemoryUtil;

public record UploadBuffer(int indexCount, boolean autoIndices, boolean indexOnly, long vertexBuffer, long indexBuffer, int vertSize, int indexSize){

    //debug
//    private boolean released = false;

    public static UploadBuffer GetUploadBuffer(TerrainBufferBuilder.RenderedBuffer renderedBuffer) {
        TerrainBufferBuilder.DrawState drawState = renderedBuffer.drawState();
        var indexOnly = drawState.indexOnly();
        var vertSize =renderedBuffer.size();
        var indexSize =renderedBuffer.size2();
        long vertexBuffer = !indexOnly ? Util.createCopy(renderedBuffer.vertexBufferPtr(), renderedBuffer.size()) : 0;

        long indexBuffer = !drawState.sequentialIndex() ? Util.createCopy(renderedBuffer.indexBufferPtr(), renderedBuffer.size2()) : 0;

        return new UploadBuffer(drawState.indexCount(), drawState.sequentialIndex(), indexOnly, vertexBuffer, indexBuffer, vertSize, indexSize);
    }

    public int indexCount() { return indexCount; }

    public long getVertexBuffer() { return vertexBuffer; }

    public long getIndexBuffer() { return indexBuffer; }

    public void release() {
        if(vertexBuffer != 0)
            MemoryUtil.nmemFree(vertexBuffer);
        if(indexBuffer != 0)
            MemoryUtil.nmemFree(indexBuffer);
//        this.released = true;
    }
}
