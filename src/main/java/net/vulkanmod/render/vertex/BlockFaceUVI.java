package net.vulkanmod.render.vertex;

public class BlockFaceUVI {
    public final int[] uvs;
    public final int rotation;
    //TODO; Might jst use int2float casts instead to avoid class/Intefcae swicthing e.g.
    // i.e. storing ints as floats

    // TODO: not sure if a dedicated AF/MSAA Mode is need to switch speciifc texture ranged from Atlas to Unistched IntegerUV/Indexing mdode
    //  i.e. UV and Indexing mode
    public BlockFaceUVI(int[] uvs, int rotation) {
        this.uvs = uvs;
        this.rotation = rotation;
    }
}
