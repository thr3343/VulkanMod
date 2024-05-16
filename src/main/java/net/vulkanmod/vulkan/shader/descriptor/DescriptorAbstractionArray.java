package net.vulkanmod.vulkan.shader.descriptor;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.texture.VTextureAtlas;
import net.vulkanmod.vulkan.util.VUtil;

public class DescriptorAbstractionArray {

//    private final Int2IntArrayMap textureID2DescIDMap = new Int2IntArrayMap(32);


    int maxSize;
    private final int shaderStage;
    private final int descriptorType;
    private final int descriptorBinding;

    private int currentDescIndex;
    private final Int2IntOpenHashMap tetxureRegionMap; //alignedIDs
    private final Int2IntOpenHashMap texID2DescIdx; //alignedIDs
    private final IntArrayFIFOQueue FreeIDs=new IntArrayFIFOQueue(32);

    /*Abstracts between OpenGl texture Bindings and and Initialised descrtior indicies for this particualr dEscriptir Binding*/
    public DescriptorAbstractionArray(int baseSamplerIndex, int maxSize, int shaderStage, int descriptorType, int descriptorBinding) {
        this.maxSize = maxSize;
        this.shaderStage = shaderStage;
        this.descriptorType = descriptorType;
        this.descriptorBinding = descriptorBinding;

        currentDescIndex = baseSamplerIndex; //Thanks to Partially Bound, don't need to worry about initialising all the handles in the Descriptor Array
        this.texID2DescIdx = new Int2IntOpenHashMap(maxSize);


/*        Arrays.fill(texIds, -1);
        Arrays.fill(descriptorIndices, -1);
        Arrays.fill(textureSamplerHndls, -1);*/
        tetxureRegionMap = new Int2IntOpenHashMap(8);
        tetxureRegionMap.put(0, maxSize);
    }

    //Initialise a specific descriptor handle
    //TODO: Maybe bypass TextureIDs for Reserved slots + (e.g. Atlases)
    // + May alos be optimal as a tmep workaround for Async Texture Atlas loader
    //Used for hardcoding "hot Paths" for single etyre only pipelines + Dealing w/ Async Textures (i.e. tetxures that utlsie async Stitching such as Atlases e.g.)

    //Requies a ResourceLocation to enforce ID/Slot HardCoding + Guarentee Alignment /correctness + Validation e.g.
    public void reserveTextureIDDescriptor(int idx, int textureID) {
        //Initialize Arrays to zero, as that will default to index texture index 0 which is Missingno/Missing Deture rn
        //+ Actsu as a failsafe to avoid crashes/Driver fails as well


//        int idx = getResourceLocationIDfromBinding(resourceLocation.toString());

        if(idx<0||textureID==-1) throw new RuntimeException();

        if (texID2DescIdx.containsKey(textureID)) throw new RuntimeException();


        texID2DescIdx.put(textureID, idx);

//        alignedIDs.put(texID, idx);

        currentDescIndex = Math.max(idx, currentDescIndex);

    }

    private int getResourceLocationIDfromBinding(String string) {
        return switch (string) {
            case "minecraft:missing/0" -> 0;
            case "minecraft:textures/atlas/blocks.png" -> 1;
            case "minecraft:textures/atlas/particles.png" -> 2;
            case "minecraft:textures/atlas/gui.png" -> 3;
            case "minecraft:dynamic/light_map_1" -> 6;
            default -> throw new IllegalStateException("Unexpected value: " + string);
        };
    }

    //Add a new textureID registation/index to the Descripotr Array
    public boolean registerTexture(int texID) {
        if (texID2DescIdx.containsKey(texID)) return false;
//        if (texIds[texID] != 0 && texIds[texID] != imageView)
//            throw new RuntimeException(texIds[texID] + " != " + imageView);
//        texIds[texID] = ++samplerRange;
//        textureSamplerHndls[samplerRange] = imageView;

        final int samplerIndex = !this.FreeIDs.isEmpty() ? this.FreeIDs.dequeueInt() : currentDescIndex++;
        texID2DescIdx.put(texID, samplerIndex);
        return true;

    }

    //Hardcode a texture to a fixed Sampler Index: Avoids the need to fallback to Non-Uniform Indexing
    public boolean registerImmutableTexture(int texID, int SamplerIndex) {

        if (currentDescIndex <= SamplerIndex) throw new RuntimeException();
        if (texID2DescIdx.containsKey(texID)) return false;
//        if (texIds[texID] != 0 && texIds[texID] != imageView)
//            throw new RuntimeException(texIds[texID] + " != " + imageView);
//        texIds[texID] = ++samplerRange;
//        textureSamplerHndls[samplerRange] = imageView;


        texID2DescIdx.put(texID, SamplerIndex);
        return true;

    }    //Add a new textureID registation/index to the Descripotr Array
    public boolean registerArrayTexture(int arrayTexID) {
        if (texID2DescIdx.containsKey(arrayTexID)) return false;
//        if (texIds[texID] != 0 && texIds[texID] != imageView)
//            throw new RuntimeException(texIds[texID] + " != " + imageView);
//        texIds[texID] = ++samplerRange;
//        textureSamplerHndls[samplerRange] = imageView;

        final int v = !this.FreeIDs.isEmpty() ? this.FreeIDs.dequeueInt() : currentDescIndex++;
        texID2DescIdx.put(arrayTexID, v);
        return true;

    }

    //Exploits Base Instancetexture Dynamic Base tex Params being likietd to 65535 textures Max
    //(Dynamically Uniform texture Indicies)
    // Also allow Arraytex to be terates speratly from GLtetxure for ease of simplity/ texture Management e.g.
    public static boolean isArrayTex(int texID)
    {
//        final int i = VTextureAtlas.SUBALLOCIMG_BITMASK ^ VTextureAtlas.SUBALLOCIMG_BITMASK;
        return texID >= VTextureAtlas.SUBALLOCIMG_BITMASK;
    }

    //TODO; may ned to use a regon like like Vanil/aMojnag to manged resreved.Atla/tetxure blocks
    public void registerIndexedAtlasArray(VTextureAtlas vTextureAtlas)
    {
        //VTextureAtlas sharea asingle textureID,
    }

    //Convert textureIDs to SamplerIndices
    public int TextureID2SamplerIdx(int textureID) {
//        return this.texIds[textureID];
        if(this.texID2DescIdx.containsKey(textureID))
        {
            return this.texID2DescIdx.get(textureID);
        }
        else return 0;
    }

    //Convert a TextureID to an Imageview
//    public long TextureID2imageView(int textureID) {
//        return textureSamplerHndls[this.TextureID2SamplerIdx(textureID)];
//    }
    //TODO; Handle Freeing Images + VUID-vkDestroyImage-image-01000 (Free During Use)
    //
    //Using LongIterator to avoid Unboxing

    public Int2IntOpenHashMap getAlignedIDs() {
        return texID2DescIdx;
    }

    public int currentSize() {
        return this.texID2DescIdx.size();//this.samplerRange;
    }
    public int getCurrentDescriptorIndex() {
        return this.currentDescIndex;
    }

    public int getStage() {
        return this.shaderStage;
    }

    public int getBinding() {
        return this.descriptorBinding;
    }

    public boolean removeTexture(int id) {

        if(!this.texID2DescIdx.containsKey(id)) return false;
       int freedDescIdx = this.texID2DescIdx.remove(id);
        Initializer.LOGGER.info("Freeing: "+freedDescIdx);
        this.FreeIDs.enqueue(freedDescIdx);
//
//        //TODO; Fix Descriptor Holes: gaps/Incontiguity when Texture Slots are Freed/removed
//
//        samplerRange = Math.min(samplerRange, freedDescIdx);
        return true;
    }

    public int getDescType() {
        return this.descriptorType;
    }

    public boolean checkCapacity() {
        final boolean b = this.currentDescIndex >= maxSize;
        if(b && !this.FreeIDs.isEmpty()) Initializer.LOGGER.error("Descriptor Fragmentation/Holes: "+this.FreeIDs.size());
        return b;
    }

    public int resize() {
        final int align = VUtil.align(this.currentDescIndex, 64);
        return this.maxSize = align==maxSize ? align+64 : align;
    }

    public void queryCapacity(int maxLength) {

    }

    public int getCurrentOffset() { return  this.currentDescIndex; }
}