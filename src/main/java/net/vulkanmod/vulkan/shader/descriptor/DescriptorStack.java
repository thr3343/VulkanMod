/*
package net.vulkanmod.vulkan.shader.descriptor;

import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import it.unimi.dsi.fastutil.longs.Long2LongArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.vulkan.VK10.*;

//Abtracts DescriptorsetManagement internally, as if it was only One DescriptorSet
// Mostly indeded for compatibility with Systems suppring Bindless rendering, but have very low tetxure limits (e.g. MoltenVK + Intel iGPus e.g.)

//TODO: may replace this w. an array of DescriptorAbstractionArrays instead
// EDit; Decided to so this, as while less cahce friendly, will facilitate descirptorStakc management dratsically
@Deprecated
public class DescriptorStack {

    final int SetID;
    private static final int perSetMax = 256;
    final int maxSets;
    final Int2LongArrayMap[] loadedSets;
    final short[] textureCounts;
    int currentSetIndex; //TODO: needs to be per set: CONFIRM
    int initializedSets; //TODO: needs to be per set: CONFIRM
    private final Long2ObjectArrayMap<DescriptorAbstractionArray> initialisedFragSamplers;
    private final Long2ObjectArrayMap<DescriptorAbstractionArray> initialisedVertSamplers;
    public DescriptorStack(int setID, int perSetMax, int maxSets, int fragTextureLimit, int fragTextureLimit1, MemoryStack stack) {
        SetID = setID;
//        this.perSetMax = perSetMax;
        this.maxSets = maxSets;
        loadedSets = new Int2LongArrayMap[2];
        textureCounts = new short[maxSets];
        final long k = DescriptorManager.allocateDescriptorSet(stack, fragTextureLimit);
        final long k1 = DescriptorManager.allocateDescriptorSet(stack, fragTextureLimit1);
        this.loadedSets[0].put(0, k);
        this.loadedSets[1].put(0, k1);

        initialisedFragSamplers = new Long2ObjectArrayMap<>();
        initialisedVertSamplers = new Long2ObjectArrayMap<>();
        //TODO: Need to make sure SetIndices are aligned
        initialisedFragSamplers.put(0, new DescriptorAbstractionArray(perSetMax, VK_SHADER_STAGE_FRAGMENT_BIT, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, setID));
        initialisedVertSamplers.put(0, new DescriptorAbstractionArray(perSetMax, VK_SHADER_STAGE_VERTEX_BIT, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, setID));
    }

    public long getBaseSet(int frame)
    {
        return loadedSets[frame].get(0);
    }

    //get the currently active set
    public long getCurrentSet(int frame)
    {
        return loadedSets[frame].get(currentSetIndex);
    }

//    public void checkSets(int frame)
//    {
////        if(textureCounts[currentSetIndex]>=perSetMax)
//        if(currentSetIndex>initializedSets)
//        {
//            initializedSets++;
//            loadedSets[frame].put(currentSetIndex, DescriptorManager.pushDescriptorSets(this.SetID, this.perSetMax));
//        }
//    }

    public boolean registerTexture(int binding, int textureID, int frame)
    {
        boolean isNewTex = (binding == 1 ? initialisedVertSamplers : initialisedFragSamplers).get(currentSetIndex).registerTexture(textureID, binding)==0;


        //get sampler index

        //Resets to zero for each respective set
        final int descIndex = (binding == 1 ? initialisedVertSamplers : initialisedFragSamplers).get(currentSetIndex).TextureID2SamplerIdx(textureID);// - getBaseSamplerIndex();
        currentSetIndex= descIndex /perSetMax;

        //TODO: need a newtext List to allow updating each DescSet range respectively if needed (likely in updateandBind)
        if(isNewTex)
        {
//            checkSets(frame);
            textureCounts[currentSetIndex]++;
        }

        if(textureCounts[currentSetIndex]>perSetMax) throw new RuntimeException();
        return isNewTex;
    }

    public DescriptorAbstractionArray getSamplerArray(int binding, int setID)
    {
        return (binding == 1 ? initialisedVertSamplers : initialisedFragSamplers).get(setID);
    }

    private int getBaseSamplerIndex() {
        return perSetMax * currentSetIndex;
    }

    public void removeTexture(int samplerIndex)
    {
        textureCounts[samplerIndex/perSetMax]--;
    }


    public Int2LongArrayMap getAllLoadedSets(int frame) {
        return this.loadedSets[frame];
    }

    public int TextureID2SamplerIdx(int binding, int textureID) {
        return (binding == 1 ? initialisedVertSamplers : initialisedFragSamplers).get(currentSetIndex).TextureID2SamplerIdx(textureID);
    }

    public void allocateSet(MemoryStack stack, int newLimit) {


        initializedSets++;
        this.loadedSets[0].put(initializedSets, DescriptorManager.allocateDescriptorSet(stack, newLimit));
        this.loadedSets[1].put(initializedSets, DescriptorManager.allocateDescriptorSet(stack, newLimit));
    }
}
*/
