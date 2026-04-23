package net.vulkanmod.config.video;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public record VideoModeSet(int width, int height, int bitDepth, NavigableSet<Integer> refreshRates) {

    public VideoModeSet(int width, int height, int bitDepth, Collection<Integer> refreshRates) {
        this(width, height, bitDepth, new TreeSet<>(refreshRates));
    }

    public VideoMode bestMode() {
        return new VideoMode(width, height, bitDepth, refreshRates.last());
    }

    public VideoMode modeAtRate(int rate) {
        Integer closest = refreshRates.floor(rate);
        if (closest == null) closest = refreshRates.first();
        return new VideoMode(width, height, bitDepth, closest);
    }

    public boolean supportsRate(int rate) {
        return refreshRates.contains(rate);
    }

    @Override
    public @NotNull String toString() {
        return width + "×" + height;
    }

    public VideoMode getVideoMode(int refresh) {
        Integer closest = refreshRates.floor(refresh);
        if (closest == null) {
            closest = refreshRates.first();
        }

        return new VideoMode(this.width, this.height, this.bitDepth, closest);
    }

    public VideoMode getVideoMode() {
        int refreshRate = this.refreshRates.last();
        return new VideoMode(this.width, this.height, this.bitDepth, refreshRate);
    }
}
