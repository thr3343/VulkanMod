package net.vulkanmod.vulkan.shader.descriptor;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
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
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.shader.UniformState;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

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

    private final DescriptorAbstractionArray initialisedFragSamplers;
    private final DescriptorAbstractionArray initialisedVertSamplers;
    private final boolean[] isUpdated = {false, false};

    private final IntOpenHashSet newTex = new IntOpenHashSet(32);
    private final int setID;
    private int currentSamplerSize = SAMPLER_MAX_LIMIT_DEFAULT;
    private int MissingTexID = -1;
    private final long[] descriptorSets = new long[MAX_SETS];


    public BindlessDescriptorSet(int setID, int vertTextureLimit, int fragTextureLimit) {
        this.setID = setID;

        try(MemoryStack stack = MemoryStack.stackPush()) {

            for (int i = 0; i < 2; i++) {
                this.descriptorSets[i] = DescriptorManager.allocateDescriptorSet(stack, fragTextureLimit);
            }

            initialisedVertSamplers = new DescriptorAbstractionArray(vertTextureLimit, VK_SHADER_STAGE_VERTEX_BIT, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VERTEX_SAMPLER_ID);
            initialisedFragSamplers = new DescriptorAbstractionArray(fragTextureLimit, VK_SHADER_STAGE_FRAGMENT_BIT, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, FRAG_SAMPLER_ID);
        }
    }


    public void registerTexture(int binding, int TextureID)
    {

//        if(!GlTexture.hasImageResource(TextureID))
//        {
//            Initializer.LOGGER.error("SKipping Image: "+TextureID);
//            return;
//        }


        final boolean needsUpdate = (binding == 0 ? this.initialisedFragSamplers : initialisedVertSamplers)
                .registerTexture(TextureID, 0);


        if(needsUpdate)
        {
            newTex.add(TextureID);
            this.forceDescriptorUpdate();
        }


    }

    public int getTexture(int binding, int TextureID)
    {
        return (binding == 0 ? initialisedFragSamplers : initialisedVertSamplers).TextureID2SamplerIdx(TextureID);
    }

    //TODO: may remove this, as using texture broadcast seems to have the same performance
    private void setupHardcodedTextures() {

        //TODO: make this handle Vanilla's Async texture Loader
        // so images can be loaded and register asynchronously without risk of Undefined behaviour or Uninitialised descriptors
        // + Reserving texture Slots must use resourceLocation as textureIDs are not Determinate due to the Async Texture Loading
        this.MissingTexID = MissingTextureAtlasSprite.getTexture().getId();

        final TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        if(this.setID==0) {
//            this.initialisedFragSamplers.registerTexture(this.MissingTexID);
            this.initialisedFragSamplers.registerImmutableTexture(this.MissingTexID, 0);
            this.initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(Sheets.BANNER_SHEET).getId(), 1);
            this.initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(TextureAtlas.LOCATION_PARTICLES).getId(), 2);
            this.initialisedFragSamplers.registerImmutableTexture(textureManager.getTexture(InventoryMenu.BLOCK_ATLAS).getId(), 3);
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
    }


    public long updateAndBind(int frame, long uniformId, InlineUniformBlock uniformStates)
    {
        if (this.MissingTexID == -1) {
            setupHardcodedTextures();
        }
        try(MemoryStack stack = stackPush()) {
            checkInlineUniformState(frame, stack, uniformStates);
            if(!this.isUpdated[frame]){

                final int NUM_UBOs = 1;
                final int NUM_INLINE_UBOs = uniformStates.uniformState().length;
                final int capacity = this.initialisedVertSamplers.currentSize() + this.initialisedFragSamplers.currentSize() + NUM_UBOs + NUM_INLINE_UBOs;
                VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.calloc(capacity, stack);
                final long currentSet = descriptorSets[frame];


                //TODO + Partial Selective Sampler Updates:
                // only updating newly added/changed Image Samplers
                // instead of thw whole Array each time

                updateUBOs(uniformId, stack, 0, descriptorWrites, currentSet);
                updateInlineUniformBlocks(stack, descriptorWrites, currentSet, uniformStates);
//
                updateImageSamplers(stack, descriptorWrites, currentSet, this.initialisedVertSamplers);
                updateImageSamplers(stack, descriptorWrites, currentSet, this.initialisedFragSamplers);


                this.isUpdated[frame] = false;

                this.newTex.clear();

                vkUpdateDescriptorSets(DEVICE, descriptorWrites.rewind(), null);


            }
//            if(this.initialisedFragSamplers.currentSize()==0)
//            {
//                forceDescriptorUpdate();
//            }
            return this.descriptorSets[frame];

        }
    }

    private void checkInlineUniformState(int frame, MemoryStack stack, InlineUniformBlock uniformStates) {
        if (UniformState.FogColor.requiresUpdate()) {

            VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.calloc(uniformStates.uniformState().length, stack);


            updateInlineUniformBlocks(stack, descriptorWrites, this.descriptorSets[frame], uniformStates);
//
            descriptorWrites.rewind();
            vkUpdateDescriptorSets(DEVICE, descriptorWrites, null);

            UniformState.FogColor.setUpdateState(this.isUpdated[0] | this.isUpdated[1]);
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

    private void updateImageSamplers(MemoryStack stack, VkWriteDescriptorSet.Buffer descriptorWrites, long currentSet) {
        updateImageSamplers(stack, descriptorWrites, currentSet, this.initialisedVertSamplers);
        updateImageSamplers(stack, descriptorWrites, currentSet, this.initialisedFragSamplers);
    }

    private static void updateInlineUniformBlocks(MemoryStack stack, VkWriteDescriptorSet.Buffer descriptorWrites, long currentSet, InlineUniformBlock uniformStates) {

        int offset = 0;
        final Camera playerPos = Minecraft.getInstance().gameRenderer.getMainCamera();
        final int effectiveRenderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
        FogRenderer.setupFog(playerPos,
                FogRenderer.FogMode.FOG_TERRAIN,
                effectiveRenderDistance,
                false,
                1);
        //TODO: can't specify static offsets in shader without Spec constants/Stringify Macro Hacks
        for(UniformState inlineUniform : uniformStates.uniformState()) {

            final long ptr = switch (inlineUniform)
            {
                default -> inlineUniform.getMappedBufferPtr().ptr;
                case FogStart -> stack.nfloat(RenderSystem.getShaderFogStart());
                case FogEnd -> stack.nfloat(RenderSystem.getShaderFogEnd());
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
    private VulkanImage getSamplerImage(int texId1, int samplerIndex) {

        if (!GlTexture.hasImage(texId1))
        {
            Initializer.LOGGER.error("UnInitialised Image!: "+texId1 +"+"+samplerIndex+" Skipping...");

            return GlTexture.getTexture(MissingTexID).getVulkanImage();
        }
        return GlTexture.getTexture(texId1).getVulkanImage();
    }




    public void removeImage(int id) {
        this.initialisedFragSamplers.removeTexture(id);

    }


   /* //TODO:
    // Designed to mimic PushConstants by pushing a new stack, but for Descriptor sets instead
    // A new DescriptorSet is allocated, allowing capacity to be expanded like PushConstants
    // Intended to handle Postprocess renderPasses + if UBO slots exceed capacity
    public void pushDescriptorSet(int frame, VkCommandBuffer commandBuffer)
    {

        try(MemoryStack stack = MemoryStack.stackPush())
        {

            long allocateDescriptorSet = DescriptorManager.allocateDescriptorSet(stack, SAMPLER_MAX_LIMIT_DEFAULT);

            final long descriptorSet = this.descriptorSets[frame];

            VkCopyDescriptorSet.Buffer vkCopyDescriptorSet = VkCopyDescriptorSet.calloc(4, stack);

            VkCopyDescriptorSet uboCopy = vkCopyDescriptorSet.get(VERT_UBO_ID);
            uboCopy.sType$Default();
            uboCopy.srcSet(descriptorSet);
            uboCopy.srcBinding(VERT_UBO_ID);
            uboCopy.dstBinding(VERT_UBO_ID);
            uboCopy.dstSet(allocateDescriptorSet);
            uboCopy.descriptorCount(1);

            VkCopyDescriptorSet uboCopy2 = vkCopyDescriptorSet.get(FRAG_UBO_ID);
            uboCopy2.sType$Default();
            uboCopy2.srcSet(descriptorSet);
            uboCopy2.srcBinding(FRAG_UBO_ID);
            uboCopy2.dstBinding(FRAG_UBO_ID);
            uboCopy2.dstSet(allocateDescriptorSet);
            uboCopy2.descriptorCount(INLINE_UNIFORM_SIZE);

            VkCopyDescriptorSet vertSmplrCopy = vkCopyDescriptorSet.get(VERTEX_SAMPLER_ID);
            vertSmplrCopy.sType$Default();
            vertSmplrCopy.srcSet(descriptorSet);
            vertSmplrCopy.srcBinding(VERTEX_SAMPLER_ID);
            vertSmplrCopy.dstBinding(VERTEX_SAMPLER_ID);
            vertSmplrCopy.dstSet(allocateDescriptorSet);
            vertSmplrCopy.descriptorCount(VERT_SAMPLER_MAX_LIMIT);

            VkCopyDescriptorSet fragSmplrCopy = vkCopyDescriptorSet.get(FRAG_SAMPLER_ID);
            fragSmplrCopy.sType$Default();
            fragSmplrCopy.srcSet(descriptorSet);
            fragSmplrCopy.srcBinding(FRAG_SAMPLER_ID);
            fragSmplrCopy.dstBinding(FRAG_SAMPLER_ID); //Make this store Framebuffers instead when using PostProcess passes
            fragSmplrCopy.dstSet(allocateDescriptorSet);
            fragSmplrCopy.descriptorCount(currentSamplerSize);


            vkUpdateDescriptorSets(DEVICE, null, vkCopyDescriptorSet);

            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, Renderer.getLayout(), 0, stack.longs(allocateDescriptorSet), null);


        }

    }*/

    public void forceDescriptorUpdate() {
        Arrays.fill(this.isUpdated, false);
    }


    public boolean checkCapacity() {

        return initialisedFragSamplers.checkCapacity();
    }

    void resizeSamplerArrays()
    {
        int newLimit = checkCapacity() ?  initialisedFragSamplers.resize() : initialisedFragSamplers.currentSize();
        try(MemoryStack stack = MemoryStack.stackPush()) {

            this.descriptorSets[0]= DescriptorManager.allocateDescriptorSet(stack, newLimit);
            this.descriptorSets[1]= DescriptorManager.allocateDescriptorSet(stack, newLimit);

            Initializer.LOGGER.info("Resized {} to {}", this.setID, newLimit);

        }
        this.currentSamplerSize = newLimit;
        forceDescriptorUpdate();
    }

    public long getSet(int currentFrame) {
        return this.descriptorSets[currentFrame];
    }

    public boolean needsUpdate(int frame) {
        return !this.isUpdated[frame];
    }

    public boolean isTexUnInitialised(int shaderTexture) {
        return this.newTex.contains(shaderTexture);
    }
}
