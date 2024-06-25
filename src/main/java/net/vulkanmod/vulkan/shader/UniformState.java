package net.vulkanmod.vulkan.shader;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.vulkanmod.vulkan.memory.UniformBuffer;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.EnumSet;
//Might need to replace this with a less rigid system fpr Postprocess uniforms
public enum UniformState {
    ModelViewMat("mat4",4, 16, 512, 128),
    ProjMat("mat4",4, 16, 640, 128),
    MVP0("mat4",4, 16, 0, 64), //Used exclusively for Terrain and Particle Shaders
    MVP("mat4",4, 16, 64, 512),
    TextureMat("mat4",4, 16, 768, 256),
    EndPortalLayers("int",1,1, 0, 0),
    FogStart("float",1,1, 0, 0),
    FogEnd("float",1,1, 0, 0),
    LineWidth("float",1,1, 0, 0),
    GameTime("float",1,1, 0, 0),
    AlphaCutout("float",1,1, 0, 0),
    ScreenSize("vec2",2,2, 0, 0),

    //Custom Uniforms
    USE_FOG("int",1,1, 0, 0),

    //    InSize("vec2",2,2),
//    OutSize("vec2",2,2),
//    BlurDir("vec2",2,2),
//    ColorModulate("vec4",4,4),
//
//    Radius("float",1,1, 0, 0),
    Light0_Direction("vec4",4,4, 0, 0),
    Light1_Direction("vec4",4,4, 0, 0),
    ColorModulator("vec4",4,4, 0, 0),
    FogColor("vec4",4,4, 0, 0);

    public final String type;
    public final int align;
    public final int size;
    int currentOffset;

    int currentHash, newHash;
    private final MappedBuffer mappedBufferPtr;
    private boolean needsUpdate;

    private final Int2IntOpenHashMap hashedUniformOffsetMap;
    private int uniqueUniforms;


    UniformState(String vec3, int align, int size, int bankOffset, int maxLimit) {

        type = vec3;
        this.align = align;
        this.size = size;
        mappedBufferPtr = new MappedBuffer(size * Float.BYTES);
        hashedUniformOffsetMap = new Int2IntOpenHashMap(16);
    }


    public void uploadUniform(UniformBuffer uniformBuffer, int i)
    {


        MemoryUtil.memCopy(this.getMappedBufferPtr().ptr, uniformBuffer.getBasePointer() + uniformBuffer.getUsedBytes() + i, getByteSize());


    }

    private int getPositionByteOffset(int neededHash) {
        return this.hashedUniformOffsetMap.get(neededHash) * this.getByteSize();
    }
    private int getPositionByteOffset() {
        return this.uniqueUniforms * this.getByteSize();
    }

    public int getByteSize() {
        return this.size * Float.BYTES;
    }

    public boolean needsUpdate(int srcHash)
    {
        //hash the Uniform contents, then stroe the current offset

        //TODO: if need uodate then also update uniform offset/index
        // or perhaps pushing uniforms here instead
        this.newHash =srcHash;


        return this.needsUpdate = !this.hashedUniformOffsetMap.containsKey(srcHash);
    }

    public boolean requiresUpdate() { return this.needsUpdate; }

    public void resetAndUpdate()
    {
        this.currentHash=newHash;
        this.needsUpdate=false;
    }

    public static void resetAll()
    {
        for (UniformState uniformState : EnumSet.of(MVP0, MVP, ProjMat, ModelViewMat, TextureMat, ColorModulator, Light0_Direction, Light1_Direction, ScreenSize)) {
            uniformState.currentHash = 0;
            uniformState.currentOffset = 0;
            uniformState.needsUpdate=false;
            uniformState.hashedUniformOffsetMap.clear();
            uniformState.uniqueUniforms = 0;
        }
    }

    public ByteBuffer buffer() {
        return this.mappedBufferPtr.buffer;
    }

    public MappedBuffer getMappedBufferPtr() {
        return mappedBufferPtr;
    }

    public void storeCurrentOffset(int currentOffset) {
        this.currentOffset=currentOffset;
    }

    public int getOffsetFromHash() {
        return this.hashedUniformOffsetMap.get(this.currentHash);
    }

    public boolean hasUniqueHash() {
        return this.hashedUniformOffsetMap.containsKey(this.newHash);
    }

//    public void updateOffsetState(UniformBuffer uniformBuffers, int baseAlignment) {
//
//        if(hashedUniformOffsetMap.containsKey(this.newHash))
//        {
//            Renderer.getDrawer().updateUniformOffset2((hashedUniformOffsetMap.get(this.newHash)/ baseAlignment));
//        }
//    }

    public void setUpdateState(boolean b) {
        this.needsUpdate=b;
    }

    public long ptr() {
        return this.mappedBufferPtr.ptr;
    }

    public int getCurrentHash() {
        return newHash;
    }
}
