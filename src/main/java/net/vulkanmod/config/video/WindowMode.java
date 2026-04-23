package net.vulkanmod.config.video;

import net.minecraft.network.chat.Component;

public sealed interface WindowMode permits WindowMode.Windowed, WindowMode.WindowedFullscreen, WindowMode.ExclusiveFullscreen {

    String translationKey();

    @SuppressWarnings("unused")
    boolean isFullscreen();

    record Windowed() implements WindowMode {
        public String translationKey() { return "vulkanmod.options.windowMode.windowed"; }
        public boolean isFullscreen() { return false; }
    }

    record WindowedFullscreen() implements WindowMode {
        public String translationKey() { return "vulkanmod.options.windowMode.windowedFullscreen"; }
        public boolean isFullscreen() { return true; }
    }

    record ExclusiveFullscreen() implements WindowMode {
        public String translationKey() { return "options.fullscreen"; }
        public boolean isFullscreen() { return true; }
    }

    WindowMode[] VALUES = { new Windowed(), new WindowedFullscreen(), new ExclusiveFullscreen() };

    @SuppressWarnings("unused")
    static WindowMode fromIndex(int index) {
        return VALUES[index % VALUES.length];
    }

    @SuppressWarnings("unused")
    static WindowMode fromMinecraftFullscreen(boolean mcFullscreen) {
        return mcFullscreen ? new ExclusiveFullscreen() : new Windowed();
    }

    static Component nameOf(WindowMode mode) {
        return Component.translatable(mode.translationKey());
    }
}
