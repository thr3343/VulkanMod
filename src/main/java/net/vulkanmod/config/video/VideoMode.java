package net.vulkanmod.config.video;

import org.jetbrains.annotations.NotNull;

public record VideoMode(int width, int height, int bitDepth, int refreshRate) {

    @Override
    public @NotNull String toString() {
        return width + "×" + height + (refreshRate > 0 ? " @ " + refreshRate + "Hz" : "");
    }

    public VideoMode withRefreshRate(int newRate) {
        return new VideoMode(width, height, bitDepth, newRate);
    }
}