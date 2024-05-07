package net.vulkanmod.vulkan.texture;

import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import it.unimi.dsi.fastutil.shorts.Short2LongOpenHashMap;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkSamplerReductionModeCreateInfo;

import java.nio.LongBuffer;

import static net.vulkanmod.vulkan.Vulkan.getVkDevice;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_SAMPLER_REDUCTION_MODE_MAX;
import static org.lwjgl.vulkan.VK12.VK_SAMPLER_REDUCTION_MODE_MIN;

public abstract class SamplerManager {
    static final float MIP_BIAS = -0.5f;

    static final Short2LongMap SAMPLERS = new Short2LongOpenHashMap();

    public static long getTextureSampler(byte maxLod, byte flags, byte anisotropy) {
        short key = (short) (flags | (maxLod << 8));
        long sampler = SAMPLERS.getOrDefault(key, 0L);

        if (sampler == 0L) {
            sampler = createTextureSampler(maxLod, anisotropy, flags);
            SAMPLERS.put(key, sampler);
        }

        return sampler;
    }

    private static long createTextureSampler(byte maxLod, byte anisotropy, byte flags) {
        Validate.isTrue(
                (flags & (REDUCTION_MIN_BIT | REDUCTION_MAX_BIT)) != (REDUCTION_MIN_BIT | REDUCTION_MAX_BIT)
        );

        try (MemoryStack stack = stackPush()) {

            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack);
            samplerInfo.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);

            if ((flags & LINEAR_FILTERING_BIT) != 0) {
                samplerInfo.magFilter(VK_FILTER_LINEAR);
                samplerInfo.minFilter(VK_FILTER_LINEAR);
            } else {
                samplerInfo.magFilter(VK_FILTER_NEAREST);
                samplerInfo.minFilter(VK_FILTER_NEAREST);
            }

            if ((flags & CLAMP_BIT) != 0) {
                samplerInfo.addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
                samplerInfo.addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
                samplerInfo.addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
            } else {
                samplerInfo.addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT);
                samplerInfo.addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT);
                samplerInfo.addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT);
            }

            //TODO: AnisoTropic filtering only applies if MipMaps are also Enabled
            if((flags & (USE_MIPMAPS_BIT|USE_ANISOTROPIC_BIT)) != 0) {
                samplerInfo.anisotropyEnable(true);
                samplerInfo.maxAnisotropy(anisotropy);
            } else {
                samplerInfo.anisotropyEnable(false);
                samplerInfo.maxAnisotropy(0.0f);
            }

            samplerInfo.borderColor(VK_BORDER_COLOR_INT_OPAQUE_WHITE);
            samplerInfo.unnormalizedCoordinates(false);
            samplerInfo.compareEnable(false);
            samplerInfo.compareOp(VK_COMPARE_OP_ALWAYS);
            //TODO: Check overriding MIPMAPS flag like this is correct _(Possible code typo: MIPMAPs not updated correctly atm)_
            if (maxLod>1||(flags & USE_MIPMAPS_BIT) != 0) {
                samplerInfo.mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR);
                samplerInfo.maxLod(VK_LOD_CLAMP_NONE);
                samplerInfo.minLod(0.0F);
                samplerInfo.mipLodBias(MIP_BIAS);
            } else {
                samplerInfo.mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST);
                samplerInfo.maxLod(0.0F);
                samplerInfo.minLod(0.0F);
            }

            //Reduction Mode
            if ((flags & (REDUCTION_MAX_BIT | REDUCTION_MIN_BIT)) != 0) {
                VkSamplerReductionModeCreateInfo reductionModeInfo = VkSamplerReductionModeCreateInfo.calloc(stack);
                reductionModeInfo.sType$Default();
                reductionModeInfo.reductionMode((flags & REDUCTION_MAX_BIT) != 0 ? VK_SAMPLER_REDUCTION_MODE_MAX : VK_SAMPLER_REDUCTION_MODE_MIN);
                samplerInfo.pNext(reductionModeInfo.address());
            }

            LongBuffer pTextureSampler = stack.mallocLong(1);

            if (vkCreateSampler(getVkDevice(), samplerInfo, null, pTextureSampler) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create texture sampler");
            }

            return pTextureSampler.get(0);
        }
    }

    public static void cleanUp() {
        for (long id : SAMPLERS.values()) {
            vkDestroySampler(DeviceManager.vkDevice, id, null);
        }
    }

    public static final byte LINEAR_FILTERING_BIT = 1;
    public static final byte CLAMP_BIT = 2;
    public static final byte USE_MIPMAPS_BIT = 4;
    public static final byte REDUCTION_MIN_BIT = 8;
    public static final byte REDUCTION_MAX_BIT = 16;
    public static final byte USE_ANISOTROPIC_BIT = 32;
}
