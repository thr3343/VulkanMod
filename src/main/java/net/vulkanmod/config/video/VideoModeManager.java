package net.vulkanmod.config.video;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import com.mojang.blaze3d.platform.Monitor;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import java.util.*;

public final class VideoModeManager {

    private static List<VideoModeSet> availableSets = List.of();
    private static final Long2ObjectMap<List<VideoModeSet>> monitorModes = new Long2ObjectOpenHashMap<>();
    private static VideoMode currentOsMode = new VideoMode(800, 600, 8, 60);
    private static VideoMode selectedMode = currentOsMode;

    private VideoModeManager() {}

    public static void init() {
        monitorModes.clear();
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor != 0L) {
            monitorModes.put(monitor, loadVideoModeSets(monitor));
        }
        currentOsMode = getCurrentVideoMode(monitor);
        rebuildAvailableSets();
        selectedMode = findClosestMatch(currentOsMode).bestMode();
    }

    @SuppressWarnings("unused")
    public static void init(Long2ObjectMap<Monitor> monitors) {
        monitorModes.clear();

        if (monitors != null && !monitors.isEmpty()) {
            for (long monitor : monitors.keySet()) {
                monitorModes.put(monitor, loadVideoModeSets(monitor));
            }
        } else {
            long primary = GLFW.glfwGetPrimaryMonitor();
            if (primary != 0L) {
                monitorModes.put(primary, loadVideoModeSets(primary));
            }
        }

        long primaryMonitor = GLFW.glfwGetPrimaryMonitor();
        currentOsMode = getCurrentVideoMode(primaryMonitor);
        rebuildAvailableSets();
        selectedMode = findClosestMatch(currentOsMode).bestMode();
    }

    @SuppressWarnings("unused")
    public static void addMonitorVideoModes(long monitor) {
        monitorModes.put(monitor, loadVideoModeSets(monitor));
        rebuildAvailableSets();
    }

    @SuppressWarnings("unused")
    public static void removeMonitor(long monitor) {
        monitorModes.remove(monitor);
        rebuildAvailableSets();
    }

    @SuppressWarnings("unused")
    public static VideoMode selectedMode() { return selectedMode; }
    public static void selectMode(VideoMode mode) { selectedMode = mode; }

    public static List<VideoModeSet> availableSets() { return availableSets; }
    public static VideoMode currentOsMode() { return currentOsMode; }

    private static void rebuildAvailableSets() {
        Map<String, NavigableSet<Integer>> merged = new LinkedHashMap<>();

        for (List<VideoModeSet> sets : monitorModes.values()) {
            for (VideoModeSet set : sets) {
                String key = set.width() + "x" + set.height() + "@" + set.bitDepth();
                merged.computeIfAbsent(key, k -> new TreeSet<>()).addAll(set.refreshRates());
            }
        }

        List<VideoModeSet> rebuilt = new ArrayList<>(merged.size());
        for (var entry : merged.entrySet()) {
            String[] parts = entry.getKey().split("@");
            String[] res = parts[0].split("x");
            rebuilt.add(new VideoModeSet(
                    Integer.parseInt(res[0]),
                    Integer.parseInt(res[1]),
                    Integer.parseInt(parts[1]),
                    entry.getValue()
            ));
        }

        rebuilt.sort(Comparator
                .comparingInt(VideoModeSet::width)
                .thenComparingInt(VideoModeSet::height)
                .thenComparingInt(VideoModeSet::bitDepth)
                .reversed());

        availableSets = List.copyOf(rebuilt);
    }

    private static VideoMode getCurrentVideoMode(long monitor) {
        GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
        if (vidMode == null) return new VideoMode(1920, 1080, 8, 60);
        return new VideoMode(vidMode.width(), vidMode.height(), vidMode.redBits(), vidMode.refreshRate());
    }

    private static List<VideoModeSet> loadVideoModeSets(long monitor) {
        GLFWVidMode.Buffer buffer = GLFW.glfwGetVideoModes(monitor);
        if (buffer == null) return List.of();

        Map<String, Set<Integer>> map = new LinkedHashMap<>();

        for (int i = 0; i < buffer.limit(); i++) {
            buffer.position(i);
            int r = buffer.redBits();
            if (r < 8 || buffer.greenBits() != r || buffer.blueBits() != r) continue;

            String key = buffer.width() + "x" + buffer.height() + "@" + r;
            map.computeIfAbsent(key, k -> new TreeSet<>()).add(buffer.refreshRate());
        }

        List<VideoModeSet> sets = new ArrayList<>();
        for (var entry : map.entrySet()) {
            String[] parts = entry.getKey().split("@");
            String[] res = parts[0].split("x");
            int bitDepth = Integer.parseInt(parts[1]);
            sets.add(new VideoModeSet(
                    Integer.parseInt(res[0]),
                    Integer.parseInt(res[1]),
                    bitDepth,
                    entry.getValue()
            ));
        }

        sets.sort(Comparator
                .comparingInt(VideoModeSet::width)
                .thenComparingInt(VideoModeSet::height)
                .thenComparingInt(VideoModeSet::bitDepth)
                .reversed());

        return sets;
    }

    public static VideoModeSet findSetFor(VideoMode mode) {
        return availableSets.stream()
                .filter(s -> s.width() == mode.width() && s.height() == mode.height())
                .findFirst()
                .orElseGet(() -> new VideoModeSet(mode.width(), mode.height(), mode.bitDepth(), Set.of(mode.refreshRate())));
    }

    private static VideoModeSet findClosestMatch(VideoMode mode) {
        return availableSets.stream()
                .min(Comparator.comparingInt((VideoModeSet s) ->
                        Math.abs(s.width() - mode.width()) * 10000 +
                                Math.abs(s.height() - mode.height()) * 100 +
                                Math.abs(s.bitDepth() - mode.bitDepth())))
                .orElseGet(() -> new VideoModeSet(mode.width(), mode.height(), 8, Set.of(60)));
    }

    @SuppressWarnings("unused")
    public static VideoModeSet getDummy() {
        return new VideoModeSet(-1, -1, -1, Set.of(-1));
    }
}
