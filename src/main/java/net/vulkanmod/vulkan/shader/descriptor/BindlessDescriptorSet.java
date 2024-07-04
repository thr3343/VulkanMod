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

import java.util.Arrays;

import static net.vulkanmod.vulkan.shader.descriptor.DescriptorManager.INLINE_UNIFORM_SIZE;
import static net.vulkanmod.vulkan.shader.descriptor.DescriptorManager.VERT_SAMPLER_MAX_LIMIT;
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

    private final ObjectArrayList<SubSet> DescriptorStack = new ObjectArrayList<>(16);

    private final boolean[] needsUpdate = {true, true};

    private final IntOpenHashSet newTex = new IntOpenHashSet(32);
    private final int setID;
    private final int vertTextureLimit;
    private final int fragTextureLimit;
    private int currentSamplerSize = SAMPLER_MAX_LIMIT_DEFAULT;
    private int MissingTexID = -1;

    private Int2IntOpenHashMap textureId2SamplerIndex = new Int2IntOpenHashMap(32);
//    private final long[] descriptorSets = new long[MAX_SETS];

//    private final Int2LongOpenHashMap[] AuxSets = new Int2LongOpenHashMap[MAX_SETS]; //Only required for Systems w/ Low texture Slot limits (<=4096)
    private long activeSet, currentSet;

    static final int maxPerStageDescriptorSamplers = 16;//DeviceManager.deviceProperties.limits().maxPerStageDescriptorSamplers();
    private int boundSubSet,  subSetIndex;


    public BindlessDescriptorSet(int setID, int vertTextureLimit, int fragTextureLimit) {
        this.setID = setID;
        this.vertTextureLimit = vertTextureLimit;
        this.fragTextureLimit = fragTextureLimit;

        DescriptorStack.push(new SubSet(0, vertTextureLimit, fragTextureLimit, VERTEX_SAMPLER_ID, FRAG_SAMPLER_ID));
    }


    public void registerTexture(int binding, int TextureID)
    {
        //TODO: Select needed SuBSet based on texture Range: Switching +mrebingin SuBSets as needed to Page in-Out Required texture ranges


            if(binding!=0) return;
//        if(!GlTexture.hasImageResource(TextureID))
//        {
//            Initializer.LOGGER.error("SKipping Image: "+TextureID);
//            return;
//        }
//        int currentTextureRange = TextureID / maxPerStageDescriptorSamplers;  //get the requiredDescriptorSetIndex: avoids needing to enumerate/iterate manually each time = Exploits "lossy" nature of iDiv  Ops
        //Get needed Set based on TextureIDRange
//        currentSet=getCurrentSet(currentTextureRange, frame);

        //TODO: On Systems w/ limited Texture slots: chnage resizing behavour so a new Set is alloctaed instead of the resiizng the current one
        // This si in rdoer to avoid DescritprPool vffragmentation hell when resising the entire DescriptorStack

        //TODO: Can exploit using one SamplerArray for multiple DescriptorSets: as samplerOffsets relative to each Descriptor set is known and based on maxPerStageDescriptorSamplers
        // e.g. like Dynamic tetxureSlice scaling w/ SuBTextureAtlasArrays


        //TODO: Edit: will instead retain the current syatwm of resizing via Vraibel Descriptorlengths, but pushong a new set onto the stack if the maxPerStageDescriptorSamplers limit is exceeded

        //BaseSmaplerIndex of currentDescriptorSet
//        int samplerOffset =
        int SubSetIndex;

        if(textureId2SamplerIndex.containsKey(TextureID))
        {
            final int samplerIndex = textureId2SamplerIndex.get(TextureID);
            SubSetIndex = samplerIndex / maxPerStageDescriptorSamplers;

        }
        else {
            int samplerIndex = getSamplerIndex(binding, TextureID);

            if(samplerIndex==-1)
            {
                final SubSet k = this.pushDescriptorSet();
                samplerIndex = k.registerTexture(binding, TextureID) + k.getBaseIndex();

                this.DescriptorStack.add(k);
            }
            newTex.add(TextureID);
            this.forceDescriptorUpdate();

            SubSetIndex = samplerIndex / maxPerStageDescriptorSamplers;
            this.subSetIndex=SubSetIndex;
            textureId2SamplerIndex.put(TextureID, samplerIndex);
            return;
        }
        this.subSetIndex=SubSetIndex;
       if(subSetIndex!=boundSubSet)
       {
           boundSubSet=subSetIndex;
           DescriptorManager.BindAllSets(Renderer.getCurrentFrame(), Renderer.getCommandBuffer());
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

    private int getSamplerIndex(int binding, int TextureID) {
        for(SubSet subSet : DescriptorStack)
        {
            if(!subSet.checkCapacity())
            {
                final int i = subSet.registerTexture(binding, TextureID);
                if(i!=-1) return i+subSet.getBaseIndex();
            }
        }
        return -1;
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
        return DescriptorStack.get(subSetIndex).TextureID2SamplerIdx(binding, TextureID);
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
            this.DescriptorStack.get(0).initialisedFragSamplers.registerImmutableTexture(this.MissingTexID, 0);
            this.DescriptorStack.get(0).initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(Sheets.BANNER_SHEET).getId(), 1);
            this.DescriptorStack.get(0).initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(TextureAtlas.LOCATION_PARTICLES).getId(), 2);
            this.DescriptorStack.get(0).initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(BeaconRenderer.BEAM_LOCATION).getId(), 4);
            this.DescriptorStack.get(0).initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(TheEndPortalRenderer.END_SKY_LOCATION).getId(), 5);
            this.DescriptorStack.get(0).initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(TheEndPortalRenderer.END_PORTAL_LOCATION).getId(), 6);
            this.DescriptorStack.get(0).initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(ItemRenderer.ENCHANTED_GLINT_ITEM).getId(), 7);
            this.DescriptorStack.get(0).initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(new ResourceLocation("textures/environment/clouds.png")/*LevelRenderer.CLOUDS_LOCATION*/).getId(), 8);
            this.DescriptorStack.get(0).initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(new ResourceLocation("textures/misc/shadow.png")/*EntityRenderDispatcher.SHADOW_RENDER_TYPE*/).getId(), 9);
            this.DescriptorStack.get(0).initialisedVertSamplers.registerImmutableTexture(6, 0);
            this.DescriptorStack.get(0).initialisedVertSamplers.registerImmutableTexture(8, 1);
        }
        else {
            this.DescriptorStack.get(0).initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(InventoryMenu.BLOCK_ATLAS).getId(), 0);
            this.DescriptorStack.get(0).initialisedVertSamplers.registerImmutableTexture(6, 0);
        }
        textureId2SamplerIndex.putAll(this.DescriptorStack.get(0).initialisedFragSamplers.getAlignedIDs());
//        textureId2SamplerIndex.putAll(this.DescriptorStack.get(0).initialisedVertSamplers.getAlignedIDs());
    }


    public long updateAndBind(int frame, long uniformId, InlineUniformBlock uniformStates)
    {
        subSetIndex=0;
        boundSubSet=0;
        if (this.MissingTexID == -1) {
            setupHardcodedTextures();
        }
        try(MemoryStack stack = stackPush()) {
            checkInlineUniformState(frame, stack, uniformStates);
            if(this.needsUpdate[frame]){

                //TODO: may use an array of DescriptorAbstractionArrays instead, w/ each having a internal pool of new textures/SmaplerIndices
                for(SubSet currentSet : this.DescriptorStack) {

                    final long setID = currentSet.getSetHandle(frame);

                    final int NUM_UBOs = 1;
                    final int NUM_INLINE_UBOs = uniformStates.uniformState().length;
                    final int capacity = currentSet.initialisedVertSamplers.currentSize() + currentSet.initialisedFragSamplers.currentSize() + NUM_UBOs + NUM_INLINE_UBOs;
                    VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.calloc(capacity, stack);
//                    final long currentSet = descriptorSets[frame];


                    //TODO + Partial Selective Sampler Updates:
                    // only updating newly added/changed Image Samplers
                    // instead of thw whole Array each time

                    updateUBOs(uniformId, stack, 0, descriptorWrites, setID);
                    updateInlineUniformBlocks(stack, descriptorWrites, setID, uniformStates);
//
                    updateImageSamplers(stack, descriptorWrites, setID, currentSet.initialisedVertSamplers);
                    updateImageSamplers(stack, descriptorWrites, setID, currentSet.initialisedFragSamplers);


                    this.needsUpdate[frame] = false;

                    this.newTex.clear();

                    vkUpdateDescriptorSets(DEVICE, descriptorWrites.rewind(), null);
                }


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


    private void updateImageSamplers(MemoryStack stack, VkWriteDescriptorSet.Buffer descriptorWrites, long currentSet, DescriptorAbstractionArray descriptorArray) {
        //TODO: Need DstArrayIdx, ImageView, or DstArrayIdx, TextureID to enumerate/initialise the DescriptorArray
        if(descriptorArray.currentSize()==0) return;
        if(descriptorArray.currentSize()>maxPerStageDescriptorSamplers) throw new RuntimeException(this.setID +"->"+descriptorArray.currentSize()+">"+maxPerStageDescriptorSamplers);

        for (Int2IntMap.Entry texId : descriptorArray.getAlignedIDs().int2IntEntrySet()) {


            final int texId1 = texId.getIntKey();
            final int samplerIndex = texId.getIntValue();

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


    public void removeImage(int id) {

//        int subSetIndex = getSubSetIndex(samplerIndex);
        textureId2SamplerIndex.remove(id);
        this.DescriptorStack.forEach(subSet -> subSet.initialisedFragSamplers.removeTexture(id));

    }

    private int getSubSetIndex(int samplerIndex) {
        return samplerIndex / maxPerStageDescriptorSamplers;
    }


    //TODO:
    // Designed to mimic PushConstants by pushing a new stack, but for Descriptor sets instead
    // A new DescriptorSet is allocated, allowing capacity to be expanded like PushConstants
    // Intended for Semi-Bindless systems w/ very low texture limits (i.e. Intel iGPus, MoltenVK e.g.)
    public SubSet pushDescriptorSet()
    {

        try(MemoryStack stack = MemoryStack.stackPush())
        {

            SubSet allocateDescriptorSet = new SubSet(DescriptorStack.size()*maxPerStageDescriptorSamplers, this.vertTextureLimit, this.fragTextureLimit, VERTEX_SAMPLER_ID, FRAG_SAMPLER_ID);

            final long descriptorSet = this.DescriptorStack.get(0).getSetHandle(0);
            VkCopyDescriptorSet.Buffer vkCopyDescriptorSet = VkCopyDescriptorSet.calloc(8, stack);
            for (int i = 0; i < MAX_SETS; i++) {



                VkCopyDescriptorSet uboCopy = vkCopyDescriptorSet.get();
                uboCopy.sType$Default();
                uboCopy.pNext(0);
                uboCopy.srcSet(descriptorSet);
                uboCopy.srcBinding(VERT_UBO_ID);
                uboCopy.dstBinding(VERT_UBO_ID);
                uboCopy.dstSet(allocateDescriptorSet.getSetHandle(i));
                uboCopy.descriptorCount(1);

                VkCopyDescriptorSet uboCopy2 = vkCopyDescriptorSet.get();
                uboCopy2.sType$Default();
                uboCopy2.pNext(0);
                uboCopy2.srcSet(descriptorSet);
                uboCopy2.srcBinding(FRAG_UBO_ID);
                uboCopy2.dstBinding(FRAG_UBO_ID);
                uboCopy2.dstSet(allocateDescriptorSet.getSetHandle(i));
                uboCopy2.descriptorCount(INLINE_UNIFORM_SIZE);

                VkCopyDescriptorSet vertSmplrCopy = vkCopyDescriptorSet.get();
                vertSmplrCopy.sType$Default();
                vertSmplrCopy.pNext(0);
                vertSmplrCopy.srcSet(descriptorSet);
                vertSmplrCopy.srcBinding(VERTEX_SAMPLER_ID);
                vertSmplrCopy.dstBinding(VERTEX_SAMPLER_ID);
                vertSmplrCopy.dstSet(allocateDescriptorSet.getSetHandle(i));
                vertSmplrCopy.descriptorCount(VERT_SAMPLER_MAX_LIMIT);

                //TODO: Use malloc style for Frag Samplers instead

//                //Copy thr default samplers from Base SubSet 0
                VkCopyDescriptorSet fragSmplrCopy = vkCopyDescriptorSet.get();
                fragSmplrCopy.sType$Default();
                fragSmplrCopy.pNext(0);
                fragSmplrCopy.srcSet(descriptorSet);
                fragSmplrCopy.srcBinding(FRAG_SAMPLER_ID);
                fragSmplrCopy.dstBinding(FRAG_SAMPLER_ID); //Make this store Framebuffers instead when using PostProcess passes
                fragSmplrCopy.dstSet(allocateDescriptorSet.getSetHandle(i));
                fragSmplrCopy.descriptorCount(maxPerStageDescriptorSamplers); //Only copy first 32 Samplers


            }
            vkUpdateDescriptorSets(DEVICE, null, vkCopyDescriptorSet.rewind());

            Initializer.LOGGER.info("Pushed SetID {} + SubSet {} textureRange: {} -> {}", this.setID, DescriptorStack.size(), DescriptorStack.size()*maxPerStageDescriptorSamplers, DescriptorStack.size()*maxPerStageDescriptorSamplers+maxPerStageDescriptorSamplers);

//            this.currentSet=allocateDescriptorSet.getSetHandle(frame);
            //TODO: Use current deferred texture registration approach for now: and binding immediately (if needed) without DescriptorWrites: deferring needed Descriptor Updates untill the next frame
            // Edit; Current set does need to be bound, as new tetxures are deferred untill next frame
//            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, Renderer.getLayout2(1), 0, stack.longs(allocateDescriptorSet), null);


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

    //TODO: If SubsetExcceds MAxSamplers: create a new suBSet
    // Othwerwise if SuBSet excceeds currentMax but not MaxSamplers, resize the Set instead
    // Thsisi setip in a way that  avoids flushing + recreating all sets when in semi-Bindless mode
    // and onyl usign resizing when ion Fully bindless mode
    void resizeSamplerArrays()
    {
        SubSet baseSubSet = DescriptorStack.get(0);
        int newLimit = checkCapacity() ?  baseSubSet.initialisedFragSamplers.resize() : baseSubSet.initialisedFragSamplers.currentSize();
        try(MemoryStack stack = MemoryStack.stackPush()) {

            baseSubSet.allocSets(maxPerStageDescriptorSamplers, stack);

            Initializer.LOGGER.info("Resized SetID {} to {}", this.setID, newLimit);

        }
        this.currentSamplerSize = newLimit;
        forceDescriptorUpdate();
    }

    public long getSet(int currentFrame) {
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
}
