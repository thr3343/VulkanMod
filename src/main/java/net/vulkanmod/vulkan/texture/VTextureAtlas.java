package net.vulkanmod.vulkan.texture;

public class VTextureAtlas {
    //Can be customised w/ Stitcher<SpriteContents>  to allow for "SubAtlases"

    //net.minecraft.client.renderer.texture.Stitcher.Region sem to eb sued tp s[ecify X/YtexelCoords(pixels_ corod, which are then tralated to TexCoord/UVos
    // +Tetxures seem to be allocated in Alpabetical order ... w.tihin these Stitcher.Region "Subregions, aka: net.minecraft.client.renderer.texture.Stitcher.storage"
    //
    //TODO: Known Subregions:
    // * JungleTrapdoor: 469, 160
    // * Holder[entry=SpriteContents{name=minecraft:block/kelp_plant, frameCount=20, height=16, width=16}, width=16, height=16]
    //  AbsoluteCoords: 496,192
    // Holder[entry=SpriteContents{name=minecraft:block/kelp_plant, frameCount=20, height=16, width=16}, width=16, height=16]

//    /net.minecraft.client.renderer.texture.Stitcher.Holder seen to disctate these Subretion Allocs/Wrapping  e.e.i.e.ele,e.sld
    public VTextureAtlas(int size_t, int divisor, int maxLength) {
    }

    enum Mode{
        UV,
        INDEXED;
    }
}
