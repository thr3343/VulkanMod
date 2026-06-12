package net.vulkanmod.config.option;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Platform;
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
        VideoModeManager.checkConfigVideoMode(config);
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
                                .setTranslator(InactivityFpsLimit::caption)
                }),
                new OptionBlock("", new Option<?>[]{
                        new RangeOption(Component.translatable("options.guiScale"),
                                0, Math.max(window.calculateScale(0, minecraft.isEnforceUnicode()), mcOptions.guiScale().get()), 1,
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
                        new CyclingOption<>(Component.translatable("options.attackIndicator"),
                                            AttackIndicatorStatus.values(),
                                            value -> mcOptions.attackIndicator().set(value),
                                            () -> mcOptions.attackIndicator().get())
                                .setTranslator(AttackIndicatorStatus::caption),
                        new SwitchOption(Component.translatable("options.autosaveIndicator"),
                                value -> mcOptions.showAutosaveIndicator().set(value),
                                () -> mcOptions.showAutosaveIndicator().get())
                })
        };
    }

    public static OptionBlock[] getGraphicsOpts() {
        var texFilteringOption = new CyclingOption<>(Component.translatable("options.textureFiltering"),
                                                     TextureFilteringMethod.values(),
                                                     value -> {
                                                         var oldValue = mcOptions.textureFiltering()
                                                                                        .get();

                                                         if ((oldValue == TextureFilteringMethod.ANISOTROPIC && value != TextureFilteringMethod.ANISOTROPIC)
                                                             || (value == TextureFilteringMethod.ANISOTROPIC && oldValue != TextureFilteringMethod.ANISOTROPIC)) {
                                                             minecraft.delayTextureReload();
                                                             WorldRenderer.getInstance()
                                                                          .resetSampler();
                                                         }

                                                         mcOptions.textureFiltering()
                                                                         .set(value);
                                                     },
                                                     () -> mcOptions.textureFiltering()
                                                                           .get())
                .setTranslator(TextureFilteringMethod::caption)
                .setTooltip(value -> switch (value) {
                    case NONE -> Component.translatable("options.textureFiltering.none.tooltip");
                    case RGSS -> Component.translatable("options.textureFiltering.rgss.tooltip");
                    case ANISOTROPIC -> Component.translatable("options.textureFiltering.anisotropic.tooltip");
                })
                .setImpact(PerformanceImpact.MEDIUM);



        var maxAnisotropyOption = new RangeOption(Component.translatable("options.maxAnisotropy"),
                                                  1, 3, 1,
                                                  value -> {
                                                      var oldValue = mcOptions.maxAnisotropyBit()
                                                                                     .get();

                                                      if (mcOptions.textureFiltering().get() == TextureFilteringMethod.ANISOTROPIC
                                                          && !oldValue.equals(value)) {
                                                          minecraft.delayTextureReload();
                                                          WorldRenderer.getInstance()
                                                                       .resetSampler();
                                                      }

                                                      mcOptions.maxAnisotropyBit()
                                                                      .set(value);
                                                  },
                                                  () -> mcOptions.maxAnisotropyBit()
                                                                        .get())
                .setTranslator((value) -> Component.translatable("options.multiplier", Integer.toString(1 << value)))
                .setTooltip(v -> Component.translatable("options.maxAnisotropy.tooltip"));

        maxAnisotropyOption.setActivationFn(() -> texFilteringOption.getNewValue() == TextureFilteringMethod.ANISOTROPIC);
        texFilteringOption.setOnChange(maxAnisotropyOption::updateActiveState);

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
                                .setTranslator(PrioritizeChunkUpdates::caption)
                }),
                new OptionBlock("", new Option<?>[]{
                        new CyclingOption<>(Component.translatable("options.graphics.preset"),
                                new GraphicsPreset[]{GraphicsPreset.FAST, GraphicsPreset.FANCY, GraphicsPreset.CUSTOM},
                                value -> mcOptions.graphicsPreset().set(value),
                                () -> mcOptions.graphicsPreset().get())
                                .setTranslator(g -> Component.translatable(g.getKey())),
                        texFilteringOption,
                        maxAnisotropyOption,
                        new CyclingOption<>(Component.translatable("options.particles"),
                                            new ParticleStatus[]{ParticleStatus.MINIMAL, ParticleStatus.DECREASED, ParticleStatus.ALL},
                                            value -> mcOptions.particles().set(value),
                                            () -> mcOptions.particles().get())
                                .setImpact(PerformanceImpact.MEDIUM)
                                .setTranslator(ParticleStatus::caption),
                        new CyclingOption<>(Component.translatable("options.renderClouds"),
                                            CloudStatus.values(),
                                            value -> mcOptions.cloudStatus().set(value),
                                            () -> mcOptions.cloudStatus().get())
                                .setTranslator(CloudStatus::caption),
                        new RangeOption(Component.translatable("options.renderCloudsDistance"),
                                        2, 128, 1,
                                        value -> mcOptions.cloudRange().set(value),
                                        () -> mcOptions.cloudRange().get()),
                        new SwitchOption(Component.translatable("options.cutoutLeaves"),
                                         value -> mcOptions.cutoutLeaves().set(value),
                                         () -> mcOptions.cutoutLeaves().get())
                                .setTooltip(value -> Component.translatable("options.cutoutLeaves.tooltip")),
                        new RangeOption(Component.translatable("options.chunkFade"),
                                        0, 40, 1,
                                        (value) -> mcOptions.chunkSectionFadeInTime().set(value / 20.0),
                                        () -> (int) (mcOptions.chunkSectionFadeInTime().get() * 20))
                                .setTranslator(value -> Component.literal(String.valueOf(value / 20.0f)))
                                .setTooltip(v -> Component.translatable("options.chunkFade.tooltip")),
                        // TODO: improved transparency
//                        new SwitchOption(Component.translatable("options.improvedTransparency"),
//                                         value -> minecraftOptions.improvedTransparency().set(value),
//                                         () -> minecraftOptions.improvedTransparency().get())
//                                .setTooltip(Component.translatable("options.improvedTransparency.tooltip")),
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
                                        2, 20, 1,
                                        value -> mcOptions.entityDistanceScaling().set(value / 4.0),
                                        () -> (int) (mcOptions.entityDistanceScaling().get() * 4.0))
                                        .setImpact(PerformanceImpact.HIGH)
                                        .setTranslator(value -> Component.literal(String.valueOf(value / 4.0))),
                        new CyclingOption<>(Component.translatable("options.mipmapLevels"),
                                new Integer[]{0,1,2,3,4},
                                value -> {
                                    mcOptions.mipmapLevels().set(value);
                                    minecraft.updateMaxMipLevel(value);
                                    minecraft.delayTextureReload();
                                },
                                () -> mcOptions.mipmapLevels().get())
                                .setTranslator(v -> Component.literal(String.valueOf(v)))
                                .setImpact(PerformanceImpact.LOW),
                        new RangeOption(Component.translatable("options.weatherRadius"),
                                        3, 10, 1,
                                        value -> mcOptions.weatherRadius().set(value),
                                        () -> mcOptions.weatherRadius().get())
                                .setTooltip(value -> Component.translatable("options.weatherRadius.tooltip")),
                        new SwitchOption(Component.translatable("options.vignette"),
                                         value -> mcOptions.vignette().set(value),
                                         () -> mcOptions.vignette().get())
                                .setTooltip(value -> Component.translatable("options.vignette.tooltip")),
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
                        new SwitchOption(Component.translatable("vulkanmod.options.wayland"),
                                         v -> config.useWayland = v,
                                         () -> config.useWayland)
                                .setActivationFn(Platform::isLinux)
                                .setTooltip(v -> Component.translatable("vulkanmod.options.wayland.tooltip")),
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