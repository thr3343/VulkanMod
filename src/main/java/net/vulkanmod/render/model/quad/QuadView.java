package net.vulkanmod.render.model.quad;

import net.minecraft.core.Direction;

public interface QuadView {

    int vulkanMod$getFlags();

    float vulkanMod$getX(int idx);

    float vulkanMod$getY(int idx);

    float vulkanMod$getZ(int idx);

    int vulkanMod$getColor(int idx);

    float vulkanMod$getU(int idx);

    float vulkanMod$getV(int idx);

    int vulkanMod$getColorIndex();

    Direction vulkanMod$getFacingDirection();

    default boolean vulkanMod$isTinted() {
        return this.vulkanMod$getColorIndex() != -1;
    }


}
