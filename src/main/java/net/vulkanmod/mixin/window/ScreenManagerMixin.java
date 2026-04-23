package net.vulkanmod.mixin.window;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.MonitorCreator;
import com.mojang.blaze3d.platform.ScreenManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.vulkanmod.config.video.VideoModeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenManager.class)
public class ScreenManagerMixin {

    @Shadow
    @Final
    private Long2ObjectMap<Monitor> monitors;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void getMonitors(MonitorCreator monitorCreator, CallbackInfo ci) {
        VideoModeManager.init(this.monitors);
    }

    @Inject(method = "onMonitorChange", at = @At("RETURN"))
    private void onMonitorChange(long monitor, int event, CallbackInfo ci) {
        if (event == 262145) {
            VideoModeManager.addMonitorVideoModes(monitor);
        } else if (event == 262146) {
            VideoModeManager.removeMonitor(monitor);
        }
    }
}
