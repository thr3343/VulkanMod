package net.vulkanmod.mixin.compatibility.gl;

import org.lwjgl.opengl.GL13;
import org.lwjgl.system.NativeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(GL13.class)
public class GL13M {
    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glActiveTexture(@NativeType("GLenum") int texture) { /* compiled code */ }
}
