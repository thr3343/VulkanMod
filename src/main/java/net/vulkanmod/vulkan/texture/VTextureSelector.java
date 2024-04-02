package net.vulkanmod.vulkan.texture;

import net.vulkanmod.Initializer;

import java.nio.ByteBuffer;

public abstract class VTextureSelector {
    public static final int SIZE = 8;

    private static final VulkanImage[] boundTextures = new VulkanImage[SIZE];

    private static final int[] levels = new int[SIZE];

    private static final VulkanImage whiteTexture = VulkanImage.createWhiteTexture();

    private static int activeTexture = 0;

    public static void bindTexture(VulkanImage texture) {
        boundTextures[0] = texture;
    }

    public static void bindTexture(int i, VulkanImage texture) {
        if(i < 0 || i > 7) {
            Initializer.LOGGER.error(String.format("On Texture binding: index %d out of range [0, 7]", i));
            return;
        }

        boundTextures[i] = texture;
        levels[i] = -1;
    }

    public static void bindImage(int i, VulkanImage texture, int level) {
        if(i < 0 || i > 7) {
            Initializer.LOGGER.error(String.format("On Texture binding: index %d out of range [0, 7]", i));
            return;
        }

        boundTextures[i] = texture;
        levels[i] = level;
    }

    public static void uploadSubTexture(int mipLevel, int width, int height, int xOffset, int yOffset, int unpackSkipRows, int unpackSkipPixels, int unpackRowLength, ByteBuffer buffer) {
        VulkanImage texture = boundTextures[activeTexture];

        if(texture == null)
            throw new NullPointerException("Texture is null at index: " + activeTexture);

        texture.uploadSubTextureAsync(mipLevel, width, height, xOffset, yOffset, unpackSkipRows, unpackSkipPixels, unpackRowLength, buffer);
    }
    //todo: DescriptorIndexing + Sampler Array
    // group by res to in case it/  reduce potential overhead + allow additional optimisations e.g.
    // Also group by vertex + frag access flags to permit additional optimisations e.g.
    public static int getTextureIdx(String name) {
        return switch (name) {
            case "Sampler0" -> 0; //Reserve for Terrain Atlas + Onion Texture
            case "DiffuseSampler" -> 0; //PostEffect Shaders
            case "Sampler1" -> 1; //Hurt/Damage (tint) overlay
            case "SamplerProj" -> 1; //End portal Effect
            case "Sampler2" -> 2; //Reserve for lightmaps
            case "Sampler3" -> 3; //Small 64x64  Mob Textures
            case "Sampler4" -> 4; // Large 256x256 mob textures + Maybe GUI textures
            case "Sampler5" -> 5; //TileEntities
            case "Sampler6" -> 6; //Armor Trim + misc Overlay textures like Players e.g.
            case "Sampler7" -> 7; //Font/ Atlas
            //case "Sampler8" -> 7; // Non-Uniform textures
            //... /Misc./Aux textures e.g.
            // + Maybe Mod textures can use 16+ indices
            default -> throw new IllegalStateException("Unknown sampler name: " + name);
        };
    }
    public static int getTextureBinding(String name) {
        return switch (name) {
            case "DiffuseSampler" -> 0;
            case "Sampler0" -> 2;
            case "Sampler2", "SamplerProj" -> 3;
            case "Sampler1" -> 4;
            default -> throw new IllegalStateException("Unknown sampler name: " + name);
        };
    }

    public static VulkanImage getImage(int i) {
        return boundTextures[i];
    }

    public static void setLightTexture(VulkanImage texture) {
        boundTextures[2] = texture;
    }

    public static void setOverlayTexture(VulkanImage texture) {
        boundTextures[1] = texture;
    }

    public static void setActiveTexture(int activeTexture) {
        if(activeTexture < 0 || activeTexture > 7) {
            throw new IllegalStateException(String.format("On Texture binding: index %d out of range [0, 7]", activeTexture));
        }

        VTextureSelector.activeTexture = activeTexture;
    }

    public static VulkanImage getBoundTexture(int i) { return boundTextures[i]; }

    public static VulkanImage getWhiteTexture() { return whiteTexture; }
}
