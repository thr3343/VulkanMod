package net.vulkanmod.vulkan.shader.descriptor;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.inventory.InventoryMenu;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.option.Options;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.render.texture.SpriteUtil;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.shader.UniformState;
import net.vulkanmod.vulkan.texture.SamplerManager;
import net.vulkanmod.vulkan.texture.VSubTextureAtlas;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.Checks.remainingSafe;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.vulkan.VK10.*;

public class DescriptorManager {
    private static final VkDevice DEVICE = Vulkan.getVkDevice();
    private static final int UNIFORM_POOLS = 1;
    private static final int VERT_SAMPLER_MAX_LIMIT = 4;
    private static final int SAMPLER_MAX_LIMIT_DEFAULT = 32; //set to 16 for Mac Compatibility w/ MoltenVK

    private static final int MAX_POOL_SAMPLERS = 16384; //MoltenVk Bug: https://github.com/KhronosGroup/MoltenVK/issues/2227
    static final int VERT_UBO_ID = 0, FRAG_UBO_ID = 1, VERTEX_SAMPLER_ID = 2, FRAG_SAMPLER_ID = 3;
    private static final int bindingsSize = 4;

    private static final long descriptorSetLayout;
    private static final long globalDescriptorPoolArrayPool;

    private static final int  PER_SET_ALLOCS = 2; //Sets used per BindlessDescriptorSet
    private static final int MAX_SETS = 2;// * PER_SET_ALLOCS;
    private static final Int2ObjectArrayMap<BindlessDescriptorSet> sets = new Int2ObjectArrayMap<>(MAX_SETS);
//    private static final IntOpenHashSet newTex = new IntOpenHashSet(32);

    private static final int MISSING_TEX_ID = 24;

    private static final InlineUniformBlock uniformStates = new InlineUniformBlock(FRAG_UBO_ID,  UniformState.FogColor, UniformState.FogStart, UniformState.FogEnd, UniformState.GameTime, UniformState.LineWidth);
    private static final int INLINE_UNIFORM_SIZE = uniformStates.size_t();




    private static int texturePool = 0;
    private static boolean textureState = true;
    private static boolean needsReload = true;
    private static final int TOTAL_SETS = 2;


    static {


        try (MemoryStack stack = stackPush()) {


            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(bindingsSize, stack);
            IntBuffer bindingFlags = stack.callocInt(bindingsSize);



            bindings.get(VERT_UBO_ID)
                    .binding(VERT_UBO_ID)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

            bindingFlags.put(VERT_UBO_ID, 0);

            final long textureSampler = SamplerManager.getTextureSampler((byte) 0, (byte) 0, (byte) 0);
            bindings.get(FRAG_UBO_ID)
                    .binding(FRAG_UBO_ID)
                    .descriptorCount(INLINE_UNIFORM_SIZE)
                    .descriptorType(VK13.VK_DESCRIPTOR_TYPE_INLINE_UNIFORM_BLOCK)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            bindingFlags.put(FRAG_UBO_ID, 0);

            bindings.get(VERTEX_SAMPLER_ID)
                    .binding(VERTEX_SAMPLER_ID)
                    .descriptorCount(VERT_SAMPLER_MAX_LIMIT)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImmutableSamplers(stack.longs(textureSampler, textureSampler, textureSampler, textureSampler))
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

            bindingFlags.put(VERTEX_SAMPLER_ID, VK12.VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT);


            bindings.get(FRAG_SAMPLER_ID)
                    .binding(FRAG_SAMPLER_ID)
                    .descriptorCount(MAX_POOL_SAMPLERS / MAX_SETS) //Try to avoid Out of Pool errors on AMD
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            bindingFlags.put(FRAG_SAMPLER_ID, VK12.VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT | VK12.VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT);


            VkDescriptorSetLayoutBindingFlagsCreateInfo setLayoutBindingsFlags = VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack)
                    .sType$Default()
                    .bindingCount(bindingFlags.capacity())
                    .pBindingFlags(bindingFlags);


            VkDescriptorSetLayoutCreateInfo vkDescriptorSetLayoutCreateInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(setLayoutBindingsFlags)
                    .pBindings(bindings);

            LongBuffer pDescriptorSetLayout = stack.mallocLong(1);

            Vulkan.checkResult(vkCreateDescriptorSetLayout(DEVICE, vkDescriptorSetLayoutCreateInfo, null, pDescriptorSetLayout), "Failed to create descriptor set layout");

            descriptorSetLayout=pDescriptorSetLayout.get(0);


            globalDescriptorPoolArrayPool = createGlobalDescriptorPool();


        }
    }



    static long allocateDescriptorSet(MemoryStack stack, int samplerMaxLimitDefault) {


        VkDescriptorSetVariableDescriptorCountAllocateInfo variableDescriptorCountAllocateInfo = VkDescriptorSetVariableDescriptorCountAllocateInfo.calloc(stack)
                .sType$Default()
                .pDescriptorCounts(stack.ints(samplerMaxLimitDefault));


        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack);
        allocInfo.sType$Default();
        allocInfo.pNext(variableDescriptorCountAllocateInfo);
        allocInfo.descriptorPool(globalDescriptorPoolArrayPool);
        allocInfo.pSetLayouts(stack.longs(descriptorSetLayout));

        texturePool+=samplerMaxLimitDefault;

        LongBuffer dLongBuffer = stack.mallocLong(1);

        Vulkan.checkResult(vkAllocateDescriptorSets(DEVICE, allocInfo, dLongBuffer), "Failed to allocate descriptor sets");
        return dLongBuffer.get(0);
    }

    public static long createGlobalDescriptorPool()
    {
        try(MemoryStack stack = stackPush()) {
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(3, stack);


                VkDescriptorPoolSize uniformBufferPoolSize = poolSizes.get(0);
                uniformBufferPoolSize.type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
                uniformBufferPoolSize.descriptorCount(1);

                VkDescriptorPoolSize uniformBufferPoolSize2 = poolSizes.get(1);
                uniformBufferPoolSize2.type(VK13.VK_DESCRIPTOR_TYPE_INLINE_UNIFORM_BLOCK);
                uniformBufferPoolSize2.descriptorCount(INLINE_UNIFORM_SIZE); //Byte Count/Size For Inline Uniform block

                VkDescriptorPoolSize textureSamplerPoolSize = poolSizes.get(2);
                textureSamplerPoolSize.type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
                textureSamplerPoolSize.descriptorCount(MAX_POOL_SAMPLERS);

            VkDescriptorPoolInlineUniformBlockCreateInfo inlineUniformBlockCreateInfo = VkDescriptorPoolInlineUniformBlockCreateInfo.calloc(stack)
                    .sType$Default()
                    .maxInlineUniformBlockBindings(1);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
            poolInfo.pNext(inlineUniformBlockCreateInfo);
            poolInfo.pPoolSizes(poolSizes);
            poolInfo.maxSets(MAX_SETS * TOTAL_SETS); //The real descriptor pool size is pPoolSizes * maxSets: not the individual descriptorPool sizes

            LongBuffer pDescriptorPool = stack.mallocLong(1);

            if(vkCreateDescriptorPool(DEVICE, poolInfo, null, pDescriptorPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor pool");
            }

            return pDescriptorPool.get(0);


        }

    }


    public static long getDescriptorSetLayout() {
        return descriptorSetLayout;
    }




    public static void cleanup()
    {
        vkResetDescriptorPool(DEVICE, globalDescriptorPoolArrayPool, 0);
        vkDestroyDescriptorSetLayout(DEVICE, descriptorSetLayout, null);
        vkDestroyDescriptorPool(DEVICE, globalDescriptorPoolArrayPool, null);
        sets.clear();
        SubTextureAtlasManager.cleanupAll();


    }

    public static void registerTexture(int setID, int imageIdx, int shaderTexture) {
        sets.get(setID).registerTexture(imageIdx, shaderTexture);


    }

    public static void BindAllSets(int currentFrame, VkCommandBuffer commandBuffer) {


            try(MemoryStack stack = MemoryStack.stackPush())
            {
                LongBuffer a = stack.mallocLong(sets.size());
                sets.forEach((key, value) -> a.put(key, value.getSet(currentFrame)));
                vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, Renderer.getLayout(),0, a, null);
            }

    }

    public static void removeImage(int setID, int textureID) {
        sets.get(setID).removeImage(textureID);
    }


    public static void updateAllSets() {
        sets.forEach((integer, bindlessDescriptorSet) -> bindlessDescriptorSet.forceDescriptorUpdate());
    }


//    public static boolean isTexUnInitialised(int textureID)
//    {
//        return newTex.contains(textureID);
//    }


    //todo: MSAA. Anisotropic Filtering:
    // Allow Reserving ranges in Descriptor Array, to store Unsitched Textures for AF/MSAA
    // e.g. Block atlas needs a 2048 range to be reserved when using AF/MSAA mode
    // + Sampler Indices need to be provided to the Vertex Buffer UVs when

    //TODO: Texture VRAM Usage
    public static String[] getDebugInfo()
    {
        return new String[]{""};
    }


    public static void addDescriptorSet(int SetID, BindlessDescriptorSet bindlessDescriptorSet) {
        if(sets.size()> MAX_SETS*PER_SET_ALLOCS) throw new RuntimeException("Too Many DescriptorSets!: "+SetID +">"+MAX_SETS/PER_SET_ALLOCS+"-1");

        sets.put(bindlessDescriptorSet.getSetID(), bindlessDescriptorSet);
    }

    public static void updateAndBindAllSets(int frame, long uniformId, VkCommandBuffer commandBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {


            boolean hasResized = false;
            for (Int2ObjectMap.Entry<BindlessDescriptorSet> set : sets.int2ObjectEntrySet()) {
                final BindlessDescriptorSet value = set.getValue();
                hasResized |= value.checkCapacity();
            }

            if(hasResized)
            {
                resizeAllSamplerArrays();
            }

            if(needsReload && GlTexture.checkTextureState(InventoryMenu.BLOCK_ATLAS, Options.getMiplevels()) && SubTextureAtlasManager.hasSubTextAtlas(InventoryMenu.BLOCK_ATLAS))
            {
                final VSubTextureAtlas vSubTextureAtlas = SubTextureAtlasManager.getSubTexAtlas(InventoryMenu.BLOCK_ATLAS);
                if(Initializer.CONFIG.isDynamicState()){
                    vSubTextureAtlas.unStitch(Options.getMiplevels());
                    DescriptorManager.registerTextureArray(1, vSubTextureAtlas);
                }
                else
                {
                    DescriptorManager.unregisterTextureArray(1);
                    SubTextureAtlasManager.unRegisterSubTexAtlas(InventoryMenu.BLOCK_ATLAS);
                }
                DescriptorManager.updateAllSets();
                DescriptorManager.resizeAllSamplerArrays();
                textureState=false;
                needsReload=false;
                SpriteUtil.setDoUpload(true);
            }

//            final int capacity = getCapacity(frame);
//            final boolean needsUpdate = checkUpdateState(frame);
            //TODO; Set 1 is not updated unless a Descriptor Array resize is used
//            LongBuffer updatedSets = stack.mallocLong(sets.size());

            for (BindlessDescriptorSet bindlessDescriptorSet : sets.values()) {
                bindlessDescriptorSet.updateAndBind(frame, uniformId, uniformStates);
            }




            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, Renderer.getLayout(), 0, stack.longs(sets.get(0).getSetHandle(frame), sets.get(1).getSetHandle(frame)), null);


            //Reset Frag PushConstant range to default state
            //Set shaderColor to White first (Fixes Mojang splash visibility)
            vkCmdPushConstants(commandBuffer, Renderer.getLayout(), VK_SHADER_STAGE_FRAGMENT_BIT, 32, stack.floats(1,1,1,1));
        }


    }

    public static void setTextureState(boolean textureState1)
    {
//        textureState=DynamicState!=DynamicState1;
        needsReload=textureState1;

    }

    public static boolean isNeedsReload() {
        return needsReload;
    }

    /*    private static boolean checkUpdateState(int frame) {
        boolean capacity = false;
        for (BindlessDescriptorSet bindlessDescriptorSet : sets.values()) {
            capacity |= bindlessDescriptorSet.needsUpdate(frame);;
        }
        return capacity;
    }

    private static int getCapacity(int frame) {
        int capacity = 0;
        for (BindlessDescriptorSet bindlessDescriptorSet : sets.values()) {
            int writesSize = bindlessDescriptorSet.getWritesSize(frame);
            capacity += writesSize;
        }
        return capacity;
    }*/

    //TODO: Descriptor pool resizing if Texture count > or exceeds MAX_POOL_SAMPLERS
    public static void resizeAllSamplerArrays()
    {
        Vulkan.waitIdle();
        //Reset pool to avoid Fragmentation:
        // requires all Sets to be reallocated but avoids Descriptor Fragmentation
        vkResetDescriptorPool(DEVICE, DescriptorManager.globalDescriptorPoolArrayPool, 0);
        texturePool=0;

        for (Int2ObjectMap.Entry<BindlessDescriptorSet> set : sets.int2ObjectEntrySet()) {

            set.getValue().resizeSamplerArrays();
        }
    }


    public static int getTexture(int setID, int imageIdx, int shaderTexture) {
        return sets.get(setID).getTexture(imageIdx, shaderTexture);
    }

    public static boolean isTexUnInitialised(int setID, int shaderTexture) {
        return sets.get(setID).isTexUnInitialised(shaderTexture);
    }

    public static int getMaxPoolSamplers() {
        return MAX_POOL_SAMPLERS;
    }
    //TODO; Allows VSubTextureAtlas to be hotswapped between DescriptorSets without the need to hardcode them

    public static void registerTextureArray(int i, VSubTextureAtlas vSubTextureAtlas) {
        sets.get(i).registerTextureArray(vSubTextureAtlas);
    }

    public static void resetSamplerState(int setID)
    {
        sets.get(setID).resetDescriptorState();
    }

    public static void unregisterTextureArray(int i) {
        sets.get(i).unregisterTextureArray();
    }
}
