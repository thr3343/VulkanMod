package net.vulkanmod.vulkan.shader.descriptor;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.vulkanmod.vulkan.texture.VTextureAtlas;

public class TexureRegionManager {

    private final Int2ObjectOpenHashMap<DescriptorAbstractionArray> tetxureRegionMap/* = new Int2IntOpenHashMap()*/; //alignedIDs
    private final int currentRange;

    public TexureRegionManager(int currentRange, int DescriptorBinding) {
        this.currentRange = currentRange;
        this.tetxureRegionMap = new Int2ObjectOpenHashMap(8);

        addSubRegion(0, 32); //hardcoded textures
    }

    public void addSubRegion(int baseIndex, int range)
    {
//        this.tetxureRegionMap.put(baseIndex, new DescriptorArray())
    }

    //TODO; may ned to use a regon like like Vanil/aMojnag to manged resreved.Atla/tetxure blocks
    public void registerIndexedAtlasArray(VTextureAtlas vTextureAtlas)
    {
        //VTextureAtlas sharea asingle textureID,
    }

}
