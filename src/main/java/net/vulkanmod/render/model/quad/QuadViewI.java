package net.vulkanmod.render.model.quad;

import net.minecraft.core.Direction;

public interface QuadViewI {

    int getFlags();

    float getX(int idx);

    float getY(int idx);

    float getZ(int idx);

    int getColor(int idx);

    int getU(int idx);

    int getV(int idx);

    int getColorIndex();

    Direction getFacingDirection();

    default boolean isTinted() {
        return this.getColorIndex() != -1;
    }


}
