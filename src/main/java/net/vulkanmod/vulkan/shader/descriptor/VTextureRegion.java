package net.vulkanmod.vulkan.shader.descriptor;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;

public class VTextureRegion {
    private final int baseIndex;
    private int samplerRange;
    private final Int2IntOpenHashMap texID2DescIdx; //alignedIDs

    private final Int2LongOpenHashMap DescriptorArray; //alignedIDs

    //Abstract ImageViews so they can be obtain extrenally + allow hostwapping +,poinetr=like behaviour e.g.

    private final IntArrayFIFOQueue FreeIDs=new IntArrayFIFOQueue(32);
    public VTextureRegion(int baseIndex, int length) {
        this.samplerRange=baseIndex;
        this.baseIndex=baseIndex;
        texID2DescIdx = new Int2IntOpenHashMap(length);
        DescriptorArray = new Int2LongOpenHashMap(length);
    }

    public boolean registerTexture(int texID, long imageView) {
        if (texID2DescIdx.containsKey(texID)) return false;
//        if (texIds[texID] != 0 && texIds[texID] != imageView)
//            throw new RuntimeException(texIds[texID] + " != " + imageView);
//        texIds[texID] = ++samplerRange;
//        textureSamplerHndls[samplerRange] = imageView;

        final int samplerIndex = !this.FreeIDs.isEmpty() ? this.FreeIDs.dequeueInt() : samplerRange++;
        texID2DescIdx.put(texID, samplerIndex);
//        final int samplerIndex = texID2DescIdx.get(texID);

        DescriptorArray.put(samplerIndex, imageView);
        return true;

    }
}
