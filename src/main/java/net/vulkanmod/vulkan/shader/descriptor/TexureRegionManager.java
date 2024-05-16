package net.vulkanmod.vulkan.shader.descriptor;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.vulkan.texture.VTextureAtlas;
import net.vulkanmod.vulkan.texture.VulkanImage;

public class TexureRegionManager {

    private final Int2LongOpenHashMap tetxureRegionMap/* = new Int2IntOpenHashMap()*/; //alignedIDs
    private final int baseSubTexID; //Starting range of this ArrayTEx SubRegion
    private final ResourceLocation assignedTexSuBresource;
    private final int currentRange;
    private int CurrentIdx;

    public TexureRegionManager(int currentRange, int baseSubTexID, ResourceLocation assignedTexSuBresource) {
        this.currentRange = currentRange;
        this.tetxureRegionMap = new Int2LongOpenHashMap(currentRange);


        this.baseSubTexID = baseSubTexID;
        this.assignedTexSuBresource = assignedTexSuBresource;
    }
    //Shouldn't ever need to free SubTex indices in most conceivable cases
    public int regsiterSubTex(VulkanImage vulkanImage, ResourceLocation resourceLocation) {

        if(this.tetxureRegionMap.containsValue(vulkanImage.getImageView())) return -1;

        final int currentIdx = this.CurrentIdx;
        tetxureRegionMap.put(currentIdx, vulkanImage.getImageView());
        this.CurrentIdx++;
        return currentIdx;

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
