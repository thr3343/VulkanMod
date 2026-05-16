package net.vulkanmod.mixin.wayland;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.vulkanmod.config.Platform;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerM {

    /**
     * Ensures Ctrl-based shortcuts work correctly.
     *
     * On native Wayland (i.e. not XWayland), GLFW fires both a key event and a char callback event
     * when a Ctrl+key combination is pressed, because the compositor delivers the unmodified
     * character as a separate char event regardless of held modifiers. This causes both the
     * shortcut action and a character insertion to happen simultaneously (e.g. Ctrl+v pastes and appends 'v').
     *
     * This mixin cancels that char event if the Ctrl modifier is set, thus fixing the issue.
     */
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void cancelCtrlCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        if (Platform.isWayLand() && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            ci.cancel();
        }
    }
}
