package net.vulkanmod.mixin.render.buffer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.nio.ByteBuffer;

@Mixin(VertexFormat.class)
public class VertexFormatMixin {

    @Overwrite
    public GpuBuffer uploadImmediateVertexBuffer(ByteBuffer byteBuffer) {
        var buffer = Renderer.getDrawer().getGpuBuffers().getVertexBuffer();
        buffer.getBuffer().copyBuffer(byteBuffer, byteBuffer.remaining());
        int currentOffset = Math.toIntExact(buffer.getBuffer().getOffset());

        buffer.setOffset(currentOffset);
        return buffer;
    }

    @Overwrite
    public GpuBuffer uploadImmediateIndexBuffer(ByteBuffer byteBuffer) {
        var buffer = Renderer.getDrawer().getGpuBuffers().getIndexBuffer();
        buffer.getBuffer().copyBuffer(byteBuffer, byteBuffer.remaining());
        int currentOffset = Math.toIntExact(buffer.getBuffer().getOffset());

        buffer.setOffset(currentOffset);
        return buffer;
    }
}
