package net.vulkanmod.vulkan.shader.descriptor;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Drawer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.shader.UniformState;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memPutAddress;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;

public class BindlessDescriptorSet {

    private static final VkDevice DEVICE = Vulkan.getVkDevice();

    private static final int SAMPLER_MAX_LIMIT_DEFAULT = 16; //set to 16 for Mac Compatibility due to a MoltenVk Bug/issue: https://github.com/KhronosGroup/MoltenVK/issues/2227
    static final int VERT_UBO_ID = 0, FRAG_UBO_ID = 1, VERTEX_SAMPLER_ID = 2, FRAG_SAMPLER_ID = 3;

    private static final int MAX_SETS = 2;
    private static final int NEW_TEXTURE = 0;
    private static final int OUT_OF_SET_SPACE = 1;
    private final DescriptorAbstractionArray initialisedFragSamplers;
    private final DescriptorAbstractionArray initialisedVertSamplers;
    private final ObjectArrayList<SubSet> DescriptorStack = new ObjectArrayList<>(16);

    private final boolean[] needsUpdate = {true, true};

    private final IntOpenHashSet newTex = new IntOpenHashSet(32);
    private final int setID;
    private final int vertTextureLimit;
    private final int fragTextureLimit;
    private int currentSamplerSize = SAMPLER_MAX_LIMIT_DEFAULT;
    private int MissingTexID = -1;


//    private final long[] descriptorSets = new long[MAX_SETS];

//    private final Int2LongOpenHashMap[] AuxSets = new Int2LongOpenHashMap[MAX_SETS]; //Only required for Systems w/ Low texture Slot limits (<=4096)
    private long activeSet, currentSet;

    static final int maxPerStageDescriptorSamplers = 16;//DeviceManager.deviceProperties.limits().maxPerStageDescriptorSamplers();
    private int subSetIndex;
    private int boundSubSet;//,  subSetIndex;


    public BindlessDescriptorSet(int setID, int vertTextureLimit, int fragTextureLimit) {
        this.setID = setID;
        this.vertTextureLimit = vertTextureLimit;
        this.fragTextureLimit = fragTextureLimit;


        initialisedVertSamplers = new DescriptorAbstractionArray(vertTextureLimit, VK_SHADER_STAGE_VERTEX_BIT, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VERTEX_SAMPLER_ID);
        initialisedFragSamplers = new DescriptorAbstractionArray(fragTextureLimit, VK_SHADER_STAGE_FRAGMENT_BIT, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, FRAG_SAMPLER_ID);

        DescriptorStack.add(new SubSet(0, vertTextureLimit, fragTextureLimit, fragTextureLimit));
    }


    public void registerTexture(int binding, int TextureID)
    {
        //TODO: Select needed SuBSet based on texture Range: Switching +mrebingin SuBSets as needed to Page in-Out Required texture ranges




            //TODO: DescriptorView

        //BaseSmaplerIndex of currentDescriptorSet
//        int samplerOffset =


        final boolean isNewTex = (binding == 0 ? this.initialisedFragSamplers
         : this.initialisedVertSamplers).registerTexture(TextureID);

        if(binding!=0) return;


        final int samplerIndex = this.initialisedFragSamplers.TextureID2SamplerIdx(TextureID);
        int subSetIndex = getSubSetIndex(samplerIndex);
        if(isNewTex)
        {
            if(subSetIndex>=DescriptorStack.size())
            {
                this.DescriptorStack.add(subSetIndex, pushDescriptorSet(maxPerStageDescriptorSamplers));
            }

            this.DescriptorStack.get(subSetIndex).addTexture(TextureID, samplerIndex);
            newTex.add(TextureID);
            this.forceDescriptorUpdate();
            return;

        }



        //Change SuBSet if the texture range exceeds maxPerStageDescriptorSamplers
       if(subSetIndex!=boundSubSet && subSetIndex<DescriptorStack.size())
        {
            boundSubSet = subSetIndex;
           this.subSetIndex=subSetIndex;
//          DescriptorManager.BindAllSets(Renderer.getCurrentFrame(), Renderer.getCommandBuffer());

            try(MemoryStack stack = MemoryStack.stackPush())
            {

                //TODO: Check disturbed Sets
                vkCmdBindDescriptorSets(Renderer.getCommandBuffer(),
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        Renderer.getLayout(),
                        0,
                        stack.longs(this.DescriptorStack.get(subSetIndex).getSetHandle(Renderer.getCurrentFrame())),
                        null);
            }
        }

      /*  int samplerIndex = getSamplerIndex(binding, TextureID);
        final boolean b = samplerIndex == -1;
        final boolean b1 = subSetIndex >= DescriptorStack.size();
        if(b1 ||b)
        {
//            subSetIndex++;
//                subSetIndex= this.DescriptorStack.size();
            this.DescriptorStack.push(this.pushDescriptorSet());
//                subSetIndex=max;
            samplerIndex = this.DescriptorStack.top().registerTexture(binding, TextureID);
        }


        //Skip rendering current pipeline if texture is not registered yet
        if(b1 ||  b || samplerIndex>0)
        {

            newTex.add(TextureID);
            this.forceDescriptorUpdate();

        }
        textureId2SamplerIndex.put(TextureID, samplerIndex);
        subSetIndex = textureId2SamplerIndex.get(TextureID) / maxPerStageDescriptorSamplers;
        if(subSetIndex>=DescriptorStack.size())
        {
            this.DescriptorStack.push(this.pushDescriptorSet());
//                subSetIndex=max;
            samplerIndex = this.DescriptorStack.top().registerTexture(binding, TextureID);
            textureId2SamplerIndex.put(TextureID, samplerIndex);
        }
        else if(boundSubSet!=subSetIndex) {
            boundSubSet=subSetIndex;
            DescriptorManager.BindAllSets(Renderer.getCurrentFrame(), Renderer.getCommandBuffer());
        }*/
//        DescriptorManager.BindAllSets(Renderer.getCurrentFrame(), Renderer.getCommandBuffer());
//        if(subSetIndex != samplerIndex / maxPerStageDescriptorSamplers)
//        {
//            subSetIndex = samplerIndex / maxPerStageDescriptorSamplers;
//        }



        //TODO: Push new set if Capacity is exhausted





    }

    //    private long getCurrentSet(int currentTextureRange, int frame) {
//        if(currentTextureRange>AuxSets[frame].size())
//        {
//            AuxSets[frame].computeIfAbsent(currentTextureRange, integer -> pushDescriptorSet(frame, maxPerStageDescriptorSamplers));
//        }
//        return AuxSets[frame].get(currentTextureRange);
//    }

    public int getTexture(int binding, int TextureID)
    {
//        if(binding!=0) return 0;
        final int i = (binding == 0 ? initialisedFragSamplers : initialisedVertSamplers).TextureID2SamplerIdx(TextureID);
//        return DescriptorStack.get(getSubSetIndex(i)).getAlignedIDs().get(TextureID);
        return i - DescriptorStack.get(getSubSetIndex(i)).getBaseIndex();
    }

    //TODO: may remove this, as using texture broadcast seems to have the same performance
    private void setupHardcodedTextures() {

        //TODO: make this handle Vanilla's Async texture Loader
        // so images can be loaded and register asynchronously without risk of Undefined behaviour or Uninitialised descriptors
        // + Reserving texture Slots must use resourceLocation as textureIDs are not Determinate due to the Async Texture Loading
        this.MissingTexID = MissingTextureAtlasSprite.getTexture().getId();

        final TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        //TODO: need to check this writes to reference or Not
        if(this.setID==0) {
//            this.initialisedFragSamplers.registerTexture(this.MissingTexID);
            this.initialisedFragSamplers.registerImmutableTexture(this.MissingTexID, 0);
            this.initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(Sheets.BANNER_SHEET).getId(), 1);
            this.initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(TextureAtlas.LOCATION_PARTICLES).getId(), 2);
            this.initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(BeaconRenderer.BEAM_LOCATION).getId(), 4);
            this.initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(TheEndPortalRenderer.END_SKY_LOCATION).getId(), 5);
            this.initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(TheEndPortalRenderer.END_PORTAL_LOCATION).getId(), 6);
            this.initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(ItemRenderer.ENCHANTED_GLINT_ITEM).getId(), 7);
            this.initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(new ResourceLocation("textures/environment/clouds.png")/*LevelRenderer.CLOUDS_LOCATION*/).getId(), 8);
            this.initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(new ResourceLocation("textures/misc/shadow.png")/*EntityRenderDispatcher.SHADOW_RENDER_TYPE*/).getId(), 9);
            this.initialisedVertSamplers.registerImmutableTexture(6, 0);
            this.initialisedVertSamplers.registerImmutableTexture(8, 1);
        }
        else {
            this.initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(InventoryMenu.BLOCK_ATLAS).getId(), 0);
            this.initialisedVertSamplers.registerImmutableTexture(6, 0);
        }

        this.DescriptorStack.get(0).getAlignedIDs().putAll(this.initialisedFragSamplers.getAlignedIDs());


    }


    public long updateAndBind(int frame, long uniformId, InlineUniformBlock uniformStates)
    {
        boundSubSet=subSetIndex=0; //reset subsetindex balc to the default baseSubset

        if (this.MissingTexID == -1) {
            setupHardcodedTextures();
        }
        try(MemoryStack stack = stackPush()) {
            checkInlineUniformState(frame, stack, uniformStates);
            if(this.needsUpdate[frame]){

                //TODO: may use an array of DescriptorAbstractionArrays instead, w/ each having a internal pool of new textures/SmaplerIndices
                for(SubSet currentSet : this.DescriptorStack) {

                    final long setID = currentSet.getSetHandle(frame);
                    final int baseSamplerOffset = currentSet.getBaseIndex();

                    final int NUM_UBOs = 1;
                    final int NUM_INLINE_UBOs = uniformStates.uniformState().length;
                    final int fragSize = Math.min(currentSet.getAlignedIDs().size(), maxPerStageDescriptorSamplers);
                    final int capacity = fragSize + initialisedVertSamplers.currentSize() + NUM_UBOs + NUM_INLINE_UBOs;
                    VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.calloc(capacity, stack);
//                    final long currentSet = descriptorSets[frame];


                    //TODO + Partial Selective Sampler Updates:
                    // only updating newly added/changed Image Samplers
                    // instead of thw whole Array each time

                    updateUBOs(uniformId, stack, 0, descriptorWrites, setID);
                    updateInlineUniformBlocks(stack, descriptorWrites, setID, uniformStates);
                    //TODO: May remove VertxSampler updates, as they are mostly immutable textures

                    updateImageSamplers(stack, descriptorWrites, setID, this.initialisedVertSamplers, 0);
                    updateImageSamplers2(stack, descriptorWrites, currentSet, this.initialisedFragSamplers, setID);




                    vkUpdateDescriptorSets(DEVICE, descriptorWrites.rewind(), null);
                }
                this.needsUpdate[frame] = false;

                this.newTex.clear();

            }
//            if(this.initialisedFragSamplers.currentSize()==0)
//            {
//                forceDescriptorUpdate();
//            }
            //Only get Base set for Binding: i.e. th default 0th DescriptorSet handle
            return this.DescriptorStack.get(0).getSetHandle(frame);

        }
    }

    private void checkInlineUniformState(int frame, MemoryStack stack, InlineUniformBlock uniformStates) {
        //Don't update Inlines twice if update is pending]
        if (!this.needsUpdate[frame] && (UniformState.FogColor.requiresUpdate()||UniformState.FogEnd.requiresUpdate())) {

            VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.calloc(uniformStates.uniformState().length, stack);

            //TODO; Propagating Inline Uniform Updates
            updateInlineUniformBlocks(stack, descriptorWrites, this.DescriptorStack.get(0).getSetHandle(frame), uniformStates);
//
            descriptorWrites.rewind();
            vkUpdateDescriptorSets(DEVICE, descriptorWrites, null);

            UniformState.FogColor.setUpdateState(false);
            UniformState.FogStart.setUpdateState(false);
            UniformState.FogEnd.setUpdateState(false);
            //TODO: Don't update sets twice if only Inlines require update
            DescriptorManager.updateAllSets();
        }
    }


    void updateUBOs(long uniformId, MemoryStack stack, int x, VkWriteDescriptorSet.Buffer descriptorWrites, long currentSet) {


        VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.calloc(1, stack);
        bufferInfos.buffer(uniformId);
        bufferInfos.offset(x);
        bufferInfos.range(Drawer.INITIAL_UB_SIZE);  //Udescriptors seem to be untyped: reserve range, but can fit anything + within the range


        //TODO: used indexed UBOs to workaound biding for new ofstes + adding new pipeline Layouts: (as long as max bound UBO Limits is sufficient)
        VkWriteDescriptorSet uboDescriptorWrite = descriptorWrites.get();
        uboDescriptorWrite.sType$Default();
        uboDescriptorWrite.dstBinding(VERT_UBO_ID);
        uboDescriptorWrite.dstArrayElement(0);
        uboDescriptorWrite.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
        uboDescriptorWrite.descriptorCount(1);
        uboDescriptorWrite.pBufferInfo(bufferInfos);
        uboDescriptorWrite.dstSet(currentSet);

    }

    private static void updateInlineUniformBlocks(MemoryStack stack, VkWriteDescriptorSet.Buffer descriptorWrites, long currentSet, InlineUniformBlock uniformStates) {

        int offset = 0;
        //TODO: can't specify static offsets in shader without Spec constants/Stringify Macro Hacks
        for(UniformState inlineUniform : uniformStates.uniformState()) {

            final long ptr = switch (inlineUniform)
            {
                default -> inlineUniform.getMappedBufferPtr().ptr;
                case GameTime -> stack.nfloat(RenderSystem.getShaderGameTime());
                case LineWidth -> stack.nfloat(RenderSystem.getShaderLineWidth());
//                case FogColor -> VRenderSystem.getShaderFogColor().ptr;
            };

            VkWriteDescriptorSetInlineUniformBlock inlineUniformBlock = VkWriteDescriptorSetInlineUniformBlock.calloc(stack)
                    .sType$Default();
            memPutAddress(inlineUniformBlock.address() + VkWriteDescriptorSetInlineUniformBlock.PDATA, ptr);
            VkWriteDescriptorSetInlineUniformBlock.ndataSize(inlineUniformBlock.address(), inlineUniform.getByteSize());

            //TODO: used indexed UBOs to workaound biding for new ofstes + adding new pipeline Layouts: (as long as max bound UBO Limits is sufficient)
            VkWriteDescriptorSet uboDescriptorWrite = descriptorWrites.get();
            uboDescriptorWrite.sType$Default();
            uboDescriptorWrite.pNext(inlineUniformBlock);
            uboDescriptorWrite.dstBinding(FRAG_UBO_ID);
            uboDescriptorWrite.dstArrayElement(offset);
            uboDescriptorWrite.descriptorType(VK13.VK_DESCRIPTOR_TYPE_INLINE_UNIFORM_BLOCK);
            uboDescriptorWrite.descriptorCount(inlineUniform.getByteSize());
            uboDescriptorWrite.dstSet(currentSet);
            offset += inlineUniform.getByteSize();
        }
    }


    private void updateImageSamplers(MemoryStack stack, VkWriteDescriptorSet.Buffer descriptorWrites, long currentSet, DescriptorAbstractionArray descriptorArray, int baseSamplerOffset) {
        //TODO: Need DstArrayIdx, ImageView, or DstArrayIdx, TextureID to enumerate/initialise the DescriptorArray
        if(descriptorArray.currentSize()==0) return;
//        if(SubSetSize>maxPerStageDescriptorSamplers) throw new RuntimeException(this.setID +"->"+SubSetSize+">"+maxPerStageDescriptorSamplers);
//        if(SubSetSize==0) return;

        final int subSetSize = Math.min(descriptorArray.currentSize()-baseSamplerOffset, maxPerStageDescriptorSamplers);


        for (int samplerIndex = 0; samplerIndex < subSetSize; samplerIndex++) {

            final int texId1 = descriptorArray.getAlignedIDs2().get(samplerIndex + baseSamplerOffset);

            boolean b = !GlTexture.hasImageResource(texId1);
            if(!b) b =  !GlTexture.hasImage(texId1);
            VulkanImage image = GlTexture.getTexture(b ? MissingTexID : texId1).getVulkanImage();

            image.readOnlyLayout();


            //Can assign ANY image to a Sampler: might decouple smapler form image creation + allocifNeeded selectively If Sampler needed
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(image.getImageView())
                    .sampler(image.getSampler());

            final VkWriteDescriptorSet vkWriteDescriptorSet = descriptorWrites.get();
            vkWriteDescriptorSet.sType$Default()
                    .dstBinding(descriptorArray.getBinding())
                    .dstArrayElement(samplerIndex)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo)
                    .dstSet(currentSet);




        }
    }

    private void updateImageSamplers2(MemoryStack stack, VkWriteDescriptorSet.Buffer descriptorWrites, SubSet currentSet, DescriptorAbstractionArray descriptorArray, long setHandle) {
        //TODO: Need DstArrayIdx, ImageView, or DstArrayIdx, TextureID to enumerate/initialise the DescriptorArray
        if(descriptorArray.currentSize()==0) return;
//        if(SubSetSize>maxPerStageDescriptorSamplers) throw new RuntimeException(this.setID +"->"+SubSetSize+">"+maxPerStageDescriptorSamplers);
//        if(SubSetSize==0) return;

//        final int subSetSize = Math.min(descriptorArray.currentSize()-baseSamplerOffset, maxPerStageDescriptorSamplers);


        for (Int2IntMap.Entry texId : currentSet.getAlignedIDs().int2IntEntrySet()) {
            {
                final int texId1 = texId.getIntKey();
                final int samplerIndex = Math.max(0, texId.getIntValue());


                boolean b = !GlTexture.hasImageResource(texId1);
                if (!b) b = !GlTexture.hasImage(texId1);
                VulkanImage image = GlTexture.getTexture(b ? MissingTexID : texId1).getVulkanImage();

                image.readOnlyLayout();


                //Can assign ANY image to a Sampler: might decouple smapler form image creation + allocifNeeded selectively If Sampler needed
                VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                        .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .imageView(image.getImageView())
                        .sampler(image.getSampler());

                final VkWriteDescriptorSet vkWriteDescriptorSet = descriptorWrites.get();
                vkWriteDescriptorSet.sType$Default()
                        .dstBinding(descriptorArray.getBinding())
                        .dstArrayElement(samplerIndex)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1)
                        .pImageInfo(imageInfo)
                        .dstSet(setHandle);


            }
        }
    }


    public void removeImage(int id) {
        int samplerIndex = initialisedFragSamplers.getAlignedIDs().get(id);

        this.initialisedFragSamplers.removeTexture(id);

        this.DescriptorStack.get(getSubSetIndex(samplerIndex)).removeTexture(id); //TODO: make subsets unsized to simplify + streamline management


    }

    private static int getSubSetIndex(int samplerIndex) {
        return samplerIndex / maxPerStageDescriptorSamplers;
    }

    //TODO:
    // Designed to mimic PushConstants by pushing a new stack, but for Descriptor sets instead
    // A new DescriptorSet is allocated, allowing capacity to be expanded like PushConstants
    // Intended for Semi-Bindless systems w/ very low texture limits (i.e. Intel iGPus, MoltenVK e.g.)
    public SubSet pushDescriptorSet(int initialSize)
    {

//        try(MemoryStack stack = MemoryStack.stackPush())
        {

            SubSet allocateDescriptorSet = new SubSet(DescriptorStack.size() * maxPerStageDescriptorSamplers, this.vertTextureLimit, this.fragTextureLimit, initialSize);


            Initializer.LOGGER.info("Pushed SetID {} + SubSet {} textureRange: {} -> {}", this.setID, DescriptorStack.size(), DescriptorStack.size()*maxPerStageDescriptorSamplers, DescriptorStack.size()*maxPerStageDescriptorSamplers+maxPerStageDescriptorSamplers);

            return allocateDescriptorSet;
        }

    }

    public void forceDescriptorUpdate() {
        Arrays.fill(this.needsUpdate, true);
    }


    public boolean checkCapacity() {
        return DescriptorStack.get(0).checkCapacity();
    }


    //Only used when in Bindless Mode: Semi-bindless used fixed set ranges instead


    //TODO: should nyl be used in fully bindless mode w/ one global subset  (to avoid updating baseOffsets when resizing)
    void resizeSamplerArrays()
    {
        for(SubSet baseSubSet : this.DescriptorStack) {
            int newLimit = baseSubSet.checkCapacity() ? baseSubSet.resize() : baseSubSet.currentSize();
            try (MemoryStack stack = MemoryStack.stackPush()) {

                baseSubSet.allocSets(newLimit, stack);

                Initializer.LOGGER.info("Resized SetID {} to {}", this.setID, newLimit);

            }
        }
//        this.currentSamplerSize = newLimit;
        forceDescriptorUpdate();
    }

    public long getSet(int currentFrame) {
        //TODO!
        return this.DescriptorStack.get(subSetIndex).getSetHandle(currentFrame);
    }

    public boolean needsUpdate(int frame) {
        return this.needsUpdate[frame];
    }

    public boolean isTexUnInitialised(int shaderTexture) {
        return this.newTex.contains(shaderTexture);
    }

    public int getSetID() {
        return this.setID;
    }

    public void checkSubSets() {
//        subSetIndex=boundSubSet=0;
        final int samplerCount = this.initialisedFragSamplers.currentSize();
        int requiredSubsets = samplerCount / maxPerStageDescriptorSamplers;
        if(requiredSubsets>this.DescriptorStack.size()-1) {
            for (int i = 0; i < requiredSubsets; i++) {

//                int fragCount = Math.min(samplerCount, maxPerStageDescriptorSamplers);


                {
                    this.DescriptorStack.add(pushDescriptorSet(maxPerStageDescriptorSamplers));
                }
//                samplerCount-=maxPerStageDescriptorSamplers;

            }
        }

    }
}
