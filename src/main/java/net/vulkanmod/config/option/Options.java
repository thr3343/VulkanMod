package net.vulkanmod.config.option;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.gui.*;
import net.vulkanmod.config.video.*;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.chunk.build.light.LightMode;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.device.DeviceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public abstract class Options {

    public static boolean fullscreenDirty = false;

    private static final Config config = Initializer.CONFIG;
    private static final Minecraft minecraft = Minecraft.getInstance();
    private static final Window window = minecraft.getWindow();
    private static final net.minecraft.client.Options mcOptions = minecraft.options;

    public static List<OptionPage> getOptionPages() {
        List<OptionPage> optionPages = new ArrayList<>();

        OptionPage page = new OptionPage(
                Component.translatable("vulkanmod.options.pages.video").getString(),
                Options.getVideoOpts()
        );
        optionPages.add(page);

        page = new OptionPage(
                Component.translatable("vulkanmod.options.pages.graphics").getString(),
                Options.getGraphicsOpts()
        );
        optionPages.add(page);

        page = new OptionPage(
                Component.translatable("vulkanmod.options.pages.optimizations").getString(),
                Options.getOptimizationOpts()
        );
        optionPages.add(page);

        page = new OptionPage(
                Component.translatable("vulkanmod.options.pages.other").getString(),
                Options.getOtherOpts()
        );
        optionPages.add(page);

        return optionPages;
    }

    public static OptionBlock[] getVideoOpts() {
        VideoModeManager.selectBestMonitor(window);
        var resolutions = VideoModeManager.getVideoResolutions();

        var videoMode = config.videoMode;
        var videoModeSet = VideoModeManager.getVideoModeSet(videoMode);

        if (videoModeSet == null) {
            videoModeSet = resolutions[resolutions.length - 1];
            videoMode = videoModeSet.getVideoMode();
        }

        VideoModeManager.selectedVideoMode = videoMode;
        var refreshRates = videoModeSet.getRefreshRates();

        var windowModeOption = new CyclingOption<>(Component.translatable("vulkanmod.options.windowMode"),
                                                   WindowMode.values(),
                                                   value -> {
                                                       boolean exclusiveFullscreen = value == WindowMode.EXCLUSIVE_FULLSCREEN;
                                                       mcOptions.fullscreen()
                                                                       .set(exclusiveFullscreen);

                                                       config.windowMode = value.mode;
                                                       fullscreenDirty = true;
                                                   },
                                                   () -> WindowMode.fromValue(config.windowMode))
                .setTranslator(value -> Component.translatable(WindowMode.getComponentName(value)));

        CyclingOption<Integer> refreshRateOption = (CyclingOption<Integer>) new CyclingOption<>(
                Component.translatable("vulkanmod.options.refreshRate"),
                refreshRates.toArray(new Integer[0]),
                (value) -> {
                    VideoModeManager.selectedVideoMode.refreshRate = value;
                    VideoModeManager.applySelectedVideoMode();

                    if (mcOptions.fullscreen().get()) {
                        fullscreenDirty = true;
                    }
                },
                () -> VideoModeManager.selectedVideoMode.refreshRate)
                .setTranslator(refreshRate -> Component.nullToEmpty(refreshRate.toString()))
                .setActivationFn(() -> windowModeOption.getNewValue() == WindowMode.EXCLUSIVE_FULLSCREEN);

        Option<VideoModeSet> resolutionOption = new CyclingOption<>(
                Component.translatable("options.fullscreen.resolution"),
                resolutions,
                (value) -> {
                    VideoModeManager.selectedVideoMode = value.getVideoMode(refreshRateOption.getNewValue());
                    VideoModeManager.applySelectedVideoMode();

                    if (mcOptions.fullscreen().get()) {
                        fullscreenDirty = true;
                    }
                },
                () -> {
                    var selectedVideoMode = VideoModeManager.selectedVideoMode;
                    var selectedVideoModeSet = VideoModeManager.getVideoModeSet(selectedVideoMode);

                    return selectedVideoModeSet != null ? selectedVideoModeSet : VideoModeSet.getDummy();
                })
                .setTranslator(resolution -> Component.nullToEmpty(resolution.toString()))
                .setActivationFn(() -> windowModeOption.getNewValue() == WindowMode.EXCLUSIVE_FULLSCREEN);

        resolutionOption.setOnChange(() -> {
            VideoModeSet newSet = resolutionOption.getNewValue();
            Integer[] rates = newSet.getRefreshRates().toArray(new Integer[0]);
            refreshRateOption.setValues(rates);
            refreshRateOption.setNewValue(rates[rates.length - 1]);
        });

        windowModeOption.setOnChange(() -> {
            resolutionOption.updateActiveState();
            refreshRateOption.updateActiveState();
        });

        return new OptionBlock[]{
                new OptionBlock("", new Option<?>[]{
                        windowModeOption,
                        resolutionOption,
                        refreshRateOption,
                        new RangeOption(Component.translatable("options.framerateLimit"),
                                10, 260, 10,
                                value -> Component.nullToEmpty(value == 260
                                        ? Component.translatable("options.framerateLimit.max").getString()
                                        : String.valueOf(value)),
                                value -> {
                                    mcOptions.framerateLimit().set(value);
                                    minecraft.getFramerateLimitTracker().setFramerateLimit(value);
                                },
                                () -> mcOptions.framerateLimit().get()),
                        new SwitchOption(Component.translatable("options.vsync"),
                                value -> {
                                    mcOptions.enableVsync().set(value);
                                    window.updateVsync(value);
                                },
                                () -> mcOptions.enableVsync().get()),
                        new CyclingOption<>(Component.translatable("options.inactivityFpsLimit"),
                                InactivityFpsLimit.values(),
                                value -> mcOptions.inactivityFpsLimit().set(value),
                                () -> mcOptions.inactivityFpsLimit().get())
                                .setTranslator(v -> Component.translatable(v.getKey()))
                }),
                new OptionBlock("", new Option<?>[]{
                        new RangeOption(Component.translatable("options.guiScale"),
                                0, window.calculateScale(0, minecraft.isEnforceUnicode()), 1,
                                value -> Component.translatable(value == 0 ? "options.guiScale.auto" : String.valueOf(value)),
                                value -> {
                                    mcOptions.guiScale().set(value);
                                    minecraft.resizeDisplay();
                                },
                                () -> mcOptions.guiScale().get()),
                        new RangeOption(Component.translatable("options.gamma"),
                                0, 100, 1,
                                value -> Component.translatable(switch (value) {
                                    case 0 -> "options.gamma.min";
                                    case 50 -> "options.gamma.default";
                                    case 100 -> "options.gamma.max";
                                    default -> String.valueOf(value);
                                }),
                                value -> mcOptions.gamma().set(value * 0.01),
                                () -> (int) (mcOptions.gamma().get() * 100.0))
                }),
                new OptionBlock("", new Option<?>[]{
                        new SwitchOption(Component.translatable("options.viewBobbing"),
                                value -> mcOptions.bobView().set(value),
                                () -> mcOptions.bobView().get()),
                        new RangeOption(Component.translatable("options.fovEffectScale"),
                                0, 100, 1,
                                value -> mcOptions.fovEffectScale().set(value / 100.0),
                                () -> (int) (mcOptions.fovEffectScale().get() * 100))
                                .setTooltip(value -> Component.translatable("options.fovEffectScale.tooltip")),
                        new RangeOption(Component.translatable("options.glintSpeed"),
                                0, 100, 1,
                                value -> mcOptions.glintSpeed().set(value / 100.0),
                                () -> (int) (mcOptions.glintSpeed().get() * 100))
                                .setTooltip(value -> Component.translatable("options.glintSpeed.tooltip")),
                        new RangeOption(Component.translatable("options.glintStrength"),
                                0, 100, 1,
                                value -> mcOptions.glintStrength().set(value / 100.0),
                                () -> (int) (mcOptions.glintStrength().get() * 100))
                                .setTooltip(value -> Component.translatable("options.glintStrength.tooltip")),
                        new CyclingOption<>(Component.translatable("options.attackIndicator"),
                                AttackIndicatorStatus.values(),
                                value -> mcOptions.attackIndicator().set(value),
                                () -> mcOptions.attackIndicator().get())
                                .setTranslator(v -> Component.translatable(v.getKey())),
                        new SwitchOption(Component.translatable("options.autosaveIndicator"),
                                value -> mcOptions.showAutosaveIndicator().set(value),
                                () -> mcOptions.showAutosaveIndicator().get())
                })
        };
    }

    public static OptionBlock[] getGraphicsOpts() {
        return new OptionBlock[]{
                new OptionBlock("", new Option<?>[]{
                        new RangeOption(Component.translatable("options.renderDistance"),
                                2, 32, 1,
                                value -> mcOptions.renderDistance().set(value),
                                () -> mcOptions.renderDistance().get())
                                .setTooltip(v -> Component.literal("Chunk render distance"))
                                .setImpact(PerformanceImpact.HIGH),
                        new RangeOption(Component.translatable("options.simulationDistance"),
                                5, 32, 1,
                                value -> mcOptions.simulationDistance().set(value),
                                () -> mcOptions.simulationDistance().get()),
                        new CyclingOption<>(Component.translatable("options.prioritizeChunkUpdates"),
                                PrioritizeChunkUpdates.values(),
                                value -> mcOptions.prioritizeChunkUpdates().set(value),
                                () -> mcOptions.prioritizeChunkUpdates().get())
                                .setTranslator(v -> Component.translatable(v.getKey()))
                }),
                new OptionBlock("", new Option<?>[]{
                        new CyclingOption<>(Component.translatable("options.graphics"),
                                new GraphicsStatus[]{GraphicsStatus.FAST, GraphicsStatus.FANCY},
                                value -> mcOptions.graphicsMode().set(value),
                                () -> mcOptions.graphicsMode().get())
                                .setTranslator(g -> Component.translatable(g.getKey())),
                        new CyclingOption<>(Component.translatable("options.particles"),
                                new ParticleStatus[]{ParticleStatus.MINIMAL, ParticleStatus.DECREASED, ParticleStatus.ALL},
                                value -> mcOptions.particles().set(value),
                                () -> mcOptions.particles().get())
                                .setImpact(PerformanceImpact.MEDIUM)
                                .setTranslator(p -> Component.translatable(p.getKey())),
                        new CyclingOption<>(Component.translatable("options.renderClouds"),
                                CloudStatus.values(),
                                value -> mcOptions.cloudStatus().set(value),
                                () -> mcOptions.cloudStatus().get())
                                .setTranslator(c -> Component.translatable(c.getKey())),
                        new RangeOption(Component.translatable("options.renderCloudsDistance"),
                                2, 128, 1,
                                value -> mcOptions.cloudRange().set(value),
                                () -> mcOptions.cloudRange().get()),
                        new CyclingOption<>(Component.translatable("options.ao"),
                                new Integer[]{LightMode.FLAT, LightMode.SMOOTH, LightMode.SUB_BLOCK},
                                value -> {
                                    mcOptions.ambientOcclusion().set(value > LightMode.FLAT);
                                    config.ambientOcclusion = value;
                                    minecraft.levelRenderer.allChanged();
                                },
                                () -> config.ambientOcclusion)
                                .setTranslator(value -> Component.translatable(switch (value) {
                                    case LightMode.FLAT -> "options.off";
                                    case LightMode.SMOOTH -> "options.on";
                                    case LightMode.SUB_BLOCK -> "vulkanmod.options.ao.subBlock";
                                    default -> "vulkanmod.options.unknown";
                                }))
                                .setTooltip(value -> value == LightMode.SUB_BLOCK
                                ? Component.translatable("vulkanmod.options.ao.subBlock.tooltip")
                                : Component.empty())
                                .setImpact(PerformanceImpact.LOW),
                        new RangeOption(Component.translatable("options.biomeBlendRadius"),
                                0, 7, 1,
                                value -> Component.nullToEmpty("%d x %d".formatted(value * 2 + 1, value * 2 + 1)),
                                value -> {
                                    mcOptions.biomeBlendRadius().set(value);
                                    minecraft.levelRenderer.allChanged();
                                },
                                () -> mcOptions.biomeBlendRadius().get())
                }),
                new OptionBlock("", new Option<?>[]{
                        new SwitchOption(Component.translatable("options.entityShadows"),
                                value -> mcOptions.entityShadows().set(value),
                                () -> mcOptions.entityShadows().get())
                                .setImpact(PerformanceImpact.LOW),
                        new RangeOption(Component.translatable("options.entityDistanceScaling"),
                                50, 500, 25,
                                value -> mcOptions.entityDistanceScaling().set(value * 0.01),
                                () -> (int)(mcOptions.entityDistanceScaling().get() * 100))
                                .setImpact(PerformanceImpact.HIGH),
                        new CyclingOption<>(Component.translatable("options.mipmapLevels"),
                                new Integer[]{0,1,2,3,4},
                                value -> {
                                    mcOptions.mipmapLevels().set(value);
                                    minecraft.updateMaxMipLevel(value);
                                    minecraft.delayTextureReload();
                                },
                                () -> mcOptions.mipmapLevels().get())
                                .setTranslator(v -> Component.literal(String.valueOf(v)))
                                .setImpact(PerformanceImpact.LOW)
                })
        };
    }

    public static OptionBlock[] getOptimizationOpts() {
        return new OptionBlock[]{
                new OptionBlock("", new Option<?>[]{
                        new CyclingOption<>(Component.translatable("vulkanmod.options.advCulling"),
                                new Integer[]{1, 2, 3, 10},
                                value -> config.advCulling = value,
                                () -> config.advCulling)
                                .setTranslator(v -> Component.translatable(switch (v) {
                                    case 1 -> "vulkanmod.options.advCulling.aggressive";
                                    case 2 -> "vulkanmod.options.advCulling.normal";
                                    case 3 -> "vulkanmod.options.advCulling.conservative";
                                    case 10 -> "options.off";
                                    default -> "vulkanmod.options.unknown";
                                }))
                                .setTooltip(v -> v <= 3 ? Component.translatable("vulkanmod.options.advCulling.tooltip") : Component.empty())
                                .setImpact(PerformanceImpact.HIGH),
                        new SwitchOption(Component.translatable("vulkanmod.options.entityCulling"),
                                v -> config.entityCulling = v,
                                () -> config.entityCulling)
                                .setTooltip(v -> Component.translatable("vulkanmod.options.entityCulling.tooltip"))
                                .setImpact(PerformanceImpact.HIGH),
                        new SwitchOption(Component.translatable("vulkanmod.options.uniqueOpaqueLayer"),
                                v -> {
                                    config.uniqueOpaqueLayer = v;
                                    TerrainRenderType.updateMapping();
                                    minecraft.levelRenderer.allChanged();
                                },
                                () -> config.uniqueOpaqueLayer)
                                .setTooltip(v -> Component.translatable("vulkanmod.options.uniqueOpaqueLayer.tooltip"))
                                .setImpact(PerformanceImpact.HIGH),
                        new SwitchOption(Component.translatable("vulkanmod.options.backfaceCulling"),
                                v -> {
                                    config.backFaceCulling = v;
                                    minecraft.levelRenderer.allChanged();
                                },
                                () -> config.backFaceCulling)
                                .setTooltip(v -> Component.translatable("vulkanmod.options.backfaceCulling.tooltip"))
                                .setImpact(PerformanceImpact.HIGH),
                        new SwitchOption(Component.translatable("vulkanmod.options.indirectDraw"),
                                v -> config.indirectDraw = v,
                                () -> config.indirectDraw)
                                .setTooltip(v -> Component.translatable("vulkanmod.options.indirectDraw.tooltip"))
                                .setImpact(PerformanceImpact.HIGH)
                })
        };
    }

    public static OptionBlock[] getOtherOpts() {
        return new OptionBlock[]{
                new OptionBlock("", new Option<?>[]{
                        new RangeOption(Component.translatable("vulkanmod.options.builderThreads"),
                                0, Runtime.getRuntime().availableProcessors() - 1, 1,
                                value -> {
                                    config.builderThreads = value;
                                    WorldRenderer.getInstance().getTaskDispatcher().createThreads(value);
                                },
                                () -> config.builderThreads)
                                .setTranslator(v -> v == 0
                                ? Component.translatable("vulkanmod.options.builderThreads.auto")
                                : Component.literal(String.valueOf(v))),
                        new RangeOption(Component.translatable("vulkanmod.options.frameQueue"),
                                2, 5, 1,
                                value -> {
                                    config.frameQueueSize = value;
                                    Renderer.scheduleSwapChainUpdate();
                                },
                                () -> config.frameQueueSize)
                                .setTooltip(v -> Component.translatable("vulkanmod.options.frameQueue.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.textureAnimations"),
                                v -> config.textureAnimations = v,
                                () -> config.textureAnimations)
                }),
                new OptionBlock("", new Option<?>[]{
                        new CyclingOption<>(Component.translatable("vulkanmod.options.deviceSelector"),
                                IntStream.range(-1, DeviceManager.suitableDevices.size())
                                        .boxed()
                                        .toArray(Integer[]::new),
                                value -> config.device = value,
                                () -> config.device)
                                .setTranslator(v -> Component.translatable(
                                        v == -1 ? "vulkanmod.options.deviceSelector.auto"
                                                : DeviceManager.suitableDevices.get(v).deviceName))
                                .setTooltip(v -> Component.literal(
                                Component.translatable("vulkanmod.options.deviceSelector.tooltip").getString() + ": " +
                                        DeviceManager.device.deviceName))
                })
        };
    }
}