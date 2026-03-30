package net.vulkanmod.render.engine;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.vulkanmod.vulkan.texture.SamplerManager;
import org.lwjgl.vulkan.VK10;

import java.util.OptionalDouble;

@Environment(EnvType.CLIENT)
public class VkSampler extends GpuSampler {
    private final AddressMode addressModeU;
    private final AddressMode addressModeV;
    private final FilterMode minFilter;
    private final FilterMode magFilter;
    private final int maxAnisotropy;
    private final float maxLod;
    private boolean closed;

    private final long id;

    public VkSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter,
                     int maxAnisotropy, OptionalDouble maxLod) {
        this.addressModeU = addressModeU;
        this.addressModeV = addressModeV;
        this.minFilter = minFilter;
        this.magFilter = magFilter;
        this.maxAnisotropy = maxAnisotropy;
        this.maxLod =  maxLod.isPresent() ? (byte) maxLod.getAsDouble() : 1000.0f;

        this.id = SamplerManager.getSampler(VkConst.of(addressModeU), VkConst.of(addressModeV),
                                            VkConst.of(minFilter), VkConst.of(magFilter),
                                            VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR, this.maxLod,
                                            maxAnisotropy > 1, maxAnisotropy, -1);
    }



    public long getId() {
        return this.id;
    }

    @Override
    public AddressMode getAddressModeU() {
        return this.addressModeU;
    }

    @Override
    public AddressMode getAddressModeV() {
        return this.addressModeV;
    }

    @Override
    public FilterMode getMinFilter() {
        return this.minFilter;
    }

    @Override
    public FilterMode getMagFilter() {
        return this.magFilter;
    }

    @Override
    public int getMaxAnisotropy() {
        return this.maxAnisotropy;
    }

    @Override
    public OptionalDouble getMaxLod() {
        return null;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
        }
    }

    public boolean isClosed() {
        return this.closed;
    }
}