package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.render.chunk.util.Util;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.render.virtualSegmentBuffer;
import net.vulkanmod.vulkan.memory.*;
import org.apache.commons.lang3.Validate;

import java.util.LinkedList;

import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

public class AreaBuffer {
    private final MemoryType memoryType;
    private final int usage;

    private final LinkedList<Segment> freeSegments = new LinkedList<>();
    private final ObjectArrayList<virtualSegmentBuffer> usedSegments = new ObjectArrayList<>();

    private final int elementSize;

    private Buffer buffer;

    int size;
    int used;
//    public final ObjectArrayList<ATst> uploadsed = new ObjectArrayList<>();


    public AreaBuffer(int usage, int size, int elementSize) {

        this.usage = usage;
        this.elementSize = elementSize;
        this.memoryType = MemoryTypes.GPU_MEM;

        this.buffer = this.allocateBuffer(size);
        this.size = size;

        freeSegments.add(new Segment(0, size));
    }

    private Buffer allocateBuffer(int size) {

        return this.usage == VK_BUFFER_USAGE_VERTEX_BUFFER_BIT ? new VertexBuffer(size, memoryType) : new IndexBuffer(size, memoryType);
    }

    public virtualSegmentBuffer  upload(int subIndex, long byteBuffer, int vertSize, TerrainRenderType r, virtualSegmentBuffer uploadSegment, int index) {

        if(vertSize % elementSize != 0)
            throw new RuntimeException("unaligned byteBuffer");


        final virtualSegmentBuffer v;
        if(uploadSegment==null || !this.isAlreadyLoaded(subIndex, vertSize, r))
        {
            v = findSegment(subIndex, vertSize, index, r);
            usedSegments.add(v);
            this.used += v.size_t();
        }
        else
        {
            v = uploadSegment;
        }





//        if(segment.size() - size > 0) {
//            freeSegments.add(new virtualSegment(index, segment.offset() + size, segment.size() - size));
//        }
//
//        final virtualSegment v = new virtualSegment(index, segment.offset(), size);

        Validate.isTrue(v.i2() >=0);
        Validate.isTrue(v.size_t() > 0);
        Validate.isTrue(v.i2() < this.buffer.getBufferSize());
        Validate.isTrue(v.size_t() < this.buffer.getBufferSize());

        AreaUploadManager.INSTANCE.uploadAsync(v, this.buffer.getId(), v.i2(), vertSize, byteBuffer);

//        uploadSegment.offset() = segment.offset();
//        uploadSegment.size = size;
//        uploadSegment.status = Segment.PENDING_BIT;


        return v;
    }

//    private void removeSegment(int index) {
//        var a = usedSegments.remove(index);
//        this.used-= a!=null ? a.size() : 0;
//    }

    //Allows segment suballocations to be skipped if the size is equal to or smaller: Should help to reduce fragmentation
    private boolean isAlreadyLoaded(int subIndex, int size1, TerrainRenderType r) {
        for(var a : usedSegments)
        {
            if(a.subIndex() == subIndex && a.size_t() >= size1 && a.r()==r)
            {
                return true;
            }

        }
        setSegmentFree(subIndex);
        return false;
    }

    public virtualSegmentBuffer findSegment(int subIndex, int size, int index, TerrainRenderType r) {
        //        if(freeSegments.isEmpty()) return new virtualSegment(index, 0, size);
        for (int i = 0; i < freeSegments.size(); i++) {
            Segment segment1 = freeSegments.get(i);
            if (segment1.size >= size) {
                virtualSegmentBuffer segment = new virtualSegmentBuffer(index, subIndex, segment1.offset, size, -1, r);
                if(segment1.size-size>0) freeSegments.set(i, new Segment(segment.i2() + size, segment1.size - size));
                else freeSegments.remove(i);
                return segment;
            }
        }

        return this.reallocate(size, subIndex, index, r);


    }

    public virtualSegmentBuffer reallocate(int uploadSize, int subIndex, int index, TerrainRenderType r) {
        int oldSize = this.size;
        int increment = this.size >> 1;

        if(increment <= uploadSize) {
            increment *= 2;
        }
        //TODO check size
        if(increment <= uploadSize)
            throw new RuntimeException();

        int newSize = oldSize + increment;


        Buffer buffer = this.allocateBuffer(newSize);

//        AreaUploadManager.INSTANCE.submitUploads();
//        AreaUploadManager.INSTANCE.waitUploads();
        AreaUploadManager.INSTANCE.copy(this.buffer, buffer);

        this.buffer.freeBuffer();
        this.buffer = buffer;

        this.size = newSize;

        int offset = Util.align(oldSize, elementSize);

        virtualSegmentBuffer segment = new virtualSegmentBuffer(index, subIndex, offset, uploadSize, -1, r);

        freeSegments.add(new Segment(offset + segment.size_t(), increment - segment.size_t()));
        return segment;
    }

    public void setSegmentFree(int k) {
        Segment e = null;
        for (int i = 0; i < usedSegments.size(); i++) {
            var a = usedSegments.get(i);
            if (a.subIndex() == k/* && a.r() == r*/) {
                var segment = usedSegments.remove(i);
                e = new Segment(segment.i2(), segment.size_t());
                extracted(e);
                this.used -= segment.size_t();
                break;
            }

        }
        if(e!=null)
        {
            extracted(e);
        }


    }

    private void extracted(Segment e) {
        for (int i = 0; i < freeSegments.size(); i++) {
            final Segment segment = freeSegments.get(i);
            if(isAdjacent(e, segment))
            {
                freeSegments.set(i, new Segment(segment.offset, segment.size + e.size));
                return;
            }
        }
//        freeSegments.add(e);
    }

    private boolean isAdjacent(Segment e, Segment segment) {
        return segment.offset + segment.size == e.offset;
    }

    public long getId() {
        return this.buffer.getId();
    }

    public void freeBuffer() {
        this.buffer.freeBuffer();
        this.freeSegments.clear();
        this.usedSegments.clear();
//        this.globalBuffer.freeSubAllocation(subAllocation);
    }

    public record Segment(int offset, int size) {}

//    //Debug
//    public List<Segment> findConflicts(int offset) {
//        List<Segment> segments = new ArrayList<>();
//        Segment segment = this.usedSegments.get(offset);
//
//        for(Segment s : this.usedSegments.values()) {
//            if((s.offset() >= segment.offset() && s.offset() < (segment.offset() + segment.size))
//              || (segment.offset() >= s.offset() && segment.offset() < (s.offset() + s.size))) {
//                segments.add(s);
//            }
//        }
//
//        return segments;
//    }

//    public static boolean checkRanges(Segment s1, Segment s2) {
//        return (s1.offset() >= s2.offset() && s1.offset() < (s2.offset() + s2.size)) || (s2.offset() >= s1.offset() && s2.offset() < (s1.offset() + s1.size));
//    }
//
//    public Segment getSegment(int offset) {
//        return this.usedSegments.get(offset);
//    }
}
