package net.vulkanmod.config.option;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.video.VideoModeManager;
import net.vulkanmod.config.video.VideoModeSet;
import net.vulkanmod.config.gui.OptionBlock;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.chunk.build.light.LightMode;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.shader.descriptor.DescriptorManager;

import java.util.stream.IntStream;

public abstract class Options {
    private static final net.minecraft.client.Options minecraftOptions = Minecraft.getInstance().options;
    private static Config config = Initializer.CONFIG;
    private static final Window window = Minecraft.getInstance().getWindow();
    public static boolean fullscreenDirty = false;

    public static OptionBlock[] getVideoOpts() {
        var videoMode = config.videoMode;
        var videoModeSet = VideoModeManager.getFromVideoMode(videoMode);

        if (videoModeSet == null) {
            videoModeSet = VideoModeSet.getDummy();
            videoMode = videoModeSet.getVideoMode(-1);
        }

        VideoModeManager.selectedVideoMode = videoMode;
        var refreshRates = videoModeSet.getRefreshRates();

        CyclingOption<Integer> RefreshRate = (CyclingOption<Integer>) new CyclingOption<>(
                Component.translatable("Refresh Rate"),
                refreshRates.toArray(new Integer[0]),
                (value) -> {
                    VideoModeManager.selectedVideoMode.refreshRate = value;
                    VideoModeManager.applySelectedVideoMode();

                    if (minecraftOptions.fullscreen().get())
                        fullscreenDirty = true;
                },
                () -> VideoModeManager.selectedVideoMode.refreshRate)
                .setTranslator(refreshRate -> Component.nullToEmpty(refreshRate.toString()));

        Option<VideoModeSet> resolutionOption = new CyclingOption<>(
                Component.translatable("Resolution"),
                VideoModeManager.getVideoResolutions(),
                (value) -> {
                    VideoModeManager.selectedVideoMode = value.getVideoMode(RefreshRate.getNewValue());
                    VideoModeManager.applySelectedVideoMode();

                    if (minecraftOptions.fullscreen().get())
                        fullscreenDirty = true;
                },
                () -> {
                    var videoMode1 = VideoModeManager.selectedVideoMode;
                    var videoModeSet1 = VideoModeManager.getFromVideoMode(videoMode1);

                    if (videoModeSet1 == null) {
                        videoModeSet1 = VideoModeSet.getDummy();
                    }

                    return videoModeSet1;
                })
                .setTranslator(resolution -> Component.nullToEmpty(resolution.toString()));

        resolutionOption.setOnChange(() -> {
            var videoMode1 = resolutionOption.getNewValue();
            var refreshRates1 = videoMode1.getRefreshRates();
            RefreshRate.setValues(refreshRates1.toArray(new Integer[0]));
            RefreshRate.setNewValue(refreshRates1.get(refreshRates1.size() - 1));
        });

        return new OptionBlock[] {
                new OptionBlock("", new Option<?>[]{
                        resolutionOption,
                        RefreshRate,
                        new SwitchOption(Component.translatable("vulkanmod.options.windowedFullscreen"),
                                value -> {
                                    config.windowedFullscreen = value;
                                    fullscreenDirty = true;
                                },
                                () -> config.windowedFullscreen),
                        new SwitchOption(Component.translatable("Fullscreen"),
                                value -> {
                                    minecraftOptions.fullscreen().set(value);
//                            window.toggleFullScreen();
                                    fullscreenDirty = true;
                                },
                                () -> minecraftOptions.fullscreen().get()),
                        new RangeOption(Component.translatable("Max Framerate"),
                                10, 260, 10,
                                value -> Component.nullToEmpty(value == 260 ? "Unlimited" : String.valueOf(value)),
                                value -> {
                                    minecraftOptions.framerateLimit().set(value);
                                    window.setFramerateLimit(value);
                                },
                                () -> minecraftOptions.framerateLimit().get()),
                        new SwitchOption(Component.translatable("VSync"),
                                value -> {
                                    minecraftOptions.enableVsync().set(value);
                                    Minecraft.getInstance().getWindow().updateVsync(value);
                                },
                                () -> minecraftOptions.enableVsync().get()),
                }),
                new OptionBlock("", new Option<?>[]{
                        new CyclingOption<>(Component.translatable("Gui Scale"),
                                getGuiScaleValues(),
                                (value) -> {
                                    minecraftOptions.guiScale().set(value);
                                    Minecraft.getInstance().resizeDisplay();
                                },
                                () -> minecraftOptions.guiScale().get())
                                .setTranslator(value -> value == 0 ? Component.literal("Auto") : Component.literal(value.toString())),
                        new RangeOption(Component.translatable("Brightness"),
                                0, 100, 1,
                                value -> {
                                    if (value == 0) return Component.translatable("options.gamma.min");
                                    else if (value == 50) return Component.translatable("options.gamma.default");
                                    else if (value == 100) return Component.translatable("options.gamma.max");
                                    return Component.literal(String.valueOf(value));
                                },
                                value -> minecraftOptions.gamma().set(value * 0.01),
                                () -> (int) (minecraftOptions.gamma().get() * 100.0)),
                }),
                new OptionBlock("", new Option<?>[]{
                        new SwitchOption(Component.translatable("View Bobbing"),
                                (value) -> minecraftOptions.bobView().set(value),
                                () -> minecraftOptions.bobView().get()),
                        new CyclingOption<>(Component.translatable("Attack Indicator"),
                                AttackIndicatorStatus.values(),
                                value -> minecraftOptions.attackIndicator().set(value),
                                () -> minecraftOptions.attackIndicator().get())
                                .setTranslator(value -> Component.translatable(value.getKey())),
                        new SwitchOption(Component.translatable("Autosave Indicator"),
                                value -> minecraftOptions.showAutosaveIndicator().set(value),
                                () -> minecraftOptions.showAutosaveIndicator().get()),
                })
        };
    }

    public static OptionBlock[] getGraphicsOpts() {
        return new OptionBlock[] {
                new OptionBlock("", new Option<?>[]{
                        new RangeOption(Component.translatable("Render Distance"),
                                2, 32, 1,
                                (value) -> {
                                    minecraftOptions.renderDistance().set(value);
                                },
                                () -> minecraftOptions.renderDistance().get()),
                        new RangeOption(Component.translatable("Simulation Distance"),
                                5, 32, 1,
                                (value) -> {
                                    minecraftOptions.simulationDistance().set(value);
                                },
                                () -> minecraftOptions.simulationDistance().get()),
                        new CyclingOption<>(Component.translatable("Chunk Builder Mode"),

                                PrioritizeChunkUpdates.values(),
                                value -> minecraftOptions.prioritizeChunkUpdates().set(value),
                                () -> minecraftOptions.prioritizeChunkUpdates().get())
                                .setTranslator(value -> Component.translatable(value.getKey())),
                }),
                new OptionBlock("", new Option<?>[]{
                        new CyclingOption<>(Component.translatable("Graphics"),
                                new GraphicsStatus[]{GraphicsStatus.FAST, GraphicsStatus.FANCY},
                                value -> minecraftOptions.graphicsMode().set(value),
                                () -> minecraftOptions.graphicsMode().get())
                                .setTranslator(graphicsMode -> Component.translatable(graphicsMode.getKey())),
                        new CyclingOption<>(Component.translatable("Particles"),
                                new ParticleStatus[]{ParticleStatus.MINIMAL, ParticleStatus.DECREASED, ParticleStatus.ALL},
                                value -> minecraftOptions.particles().set(value),
                                () -> minecraftOptions.particles().get())
                                .setTranslator(particlesMode -> Component.translatable(particlesMode.getKey())),
                        new CyclingOption<>(Component.translatable("Clouds"),
                                CloudStatus.values(),
                                value -> minecraftOptions.cloudStatus().set(value),
                                () -> minecraftOptions.cloudStatus().get())
                                .setTranslator(value -> Component.translatable(value.getKey())),
                        new CyclingOption<>(Component.translatable("Smooth Lighting"),
                                new Integer[]{LightMode.FLAT, LightMode.SMOOTH, LightMode.SUB_BLOCK},
                                (value) -> {
                                    if (value > LightMode.FLAT)
                                        minecraftOptions.ambientOcclusion().set(true);
                                    else
                                        minecraftOptions.ambientOcclusion().set(false);

                                    Initializer.CONFIG.ambientOcclusion = value;

                                    Minecraft.getInstance().levelRenderer.allChanged();
                                },
                                () -> Initializer.CONFIG.ambientOcclusion)
                                .setTranslator(value -> switch (value) {
                                    case LightMode.FLAT -> Component.literal("Off");
                                    case LightMode.SMOOTH -> Component.literal("On");
                                    case LightMode.SUB_BLOCK -> Component.literal("On (Sub-block)");
                                    default -> Component.literal("Unk");
                                })
                                .setTooltip(Component.nullToEmpty("""
                                On (Sub-block): Enables smooth lighting for non full block (experimental).""")),
                        new SwitchOption(Component.translatable("Unique opaque layer"),
                                value -> {
                                    config.uniqueOpaqueLayer = value;
                                    Minecraft.getInstance().levelRenderer.allChanged();
                                },
                                () -> config.uniqueOpaqueLayer)
                                .setTooltip(Component.translatable("vulkanmod.options.uniqueOpaqueLayer.tooltip")),
                        new RangeOption(Component.translatable("Biome Blend Radius"),
                                0, 7, 1,
                                value -> {
                                    int v = value * 2 + 1;
                                    return Component.nullToEmpty("%d x %d".formatted(v, v));
                                },
                                (value) -> {
                                    minecraftOptions.biomeBlendRadius().set(value);
                                    Minecraft.getInstance().levelRenderer.allChanged();
                                },
                                () -> minecraftOptions.biomeBlendRadius().get()),
                }),
                new OptionBlock("", new Option<?>[]{
                        new SwitchOption(Component.translatable("Entity Shadows"),
                                value -> minecraftOptions.entityShadows().set(value),
                                () -> minecraftOptions.entityShadows().get()),
                        new RangeOption(Component.translatable("Entity Distance"),
                                50, 500, 25,
                                value -> minecraftOptions.entityDistanceScaling().set(value * 0.01),
                                () -> minecraftOptions.entityDistanceScaling().get().intValue() * 100),
                        new CyclingOption<>(Component.translatable("Mipmap Levels"),
                                new Integer[]{0, 1, 2, 3, 4},
                                value -> {
                                    minecraftOptions.mipmapLevels().set(value);
                                    Minecraft.getInstance().updateMaxMipLevel(value);
                                    Minecraft.getInstance().delayTextureReload();
                                    DescriptorManager.setTextureState(true);
                                },
                                () -> minecraftOptions.mipmapLevels().get())
                                .setTranslator(value -> Component.nullToEmpty(value.toString())),
                        new CyclingOption<>(Component.translatable("Anisotropic Filtering"),
                                new Integer[]{1, 2, 4, 8, 16},
                                value -> {
                                    config.af=(value);
                                    DescriptorManager.setTextureState(true);
                                    DescriptorManager.updateAllSets();
                                    WorldRenderer.getInstance().allChanged(); //Actually needed to flush the outdated UV data
                                },
                                () -> config.af)
                                .setTranslator(value -> Component.nullToEmpty(value==1 ? "Off" : value.toString())),
                        new CyclingOption<>(Component.translatable("Multisample Anti-aliasing"),  new Integer[]{1, 2, 4, 8},
                                value -> {

                                    config.msaaPreset = value;
                                    DescriptorManager.setTextureState(true);
                                    DescriptorManager.updateAllSets();
                                    VRenderSystem.setSampleCountFromPreset(config.msaaPreset);
                                    VRenderSystem.reInit();
                                    WorldRenderer.getInstance().allChanged(); //Actually needed to flush the outdated UV data
                                },
                                () -> config.msaaPreset)
                                .setTranslator(value -> Component.nullToEmpty(switch (value) {
                                    case 2 -> "2x";
                                    case 4 -> "4x";
                                    case 8 -> "8x";
                                    default -> "Off";
                                })),
                        new CyclingOption<>(Component.translatable("MSAA Mode"), new Boolean[]{false, true},
                                value -> config.minSampleShading = value,
                                () -> config.minSampleShading)
                                .setTranslator(value -> Component.nullToEmpty((value ? "SSAA" : "MSAA"))).setTooltip(Component.translatable("vulkanmod.options.minSampleShading.tooltip")),
                })
        };
    }

    public static OptionBlock[] getOptimizationOpts() {
        return new OptionBlock[] {
                new OptionBlock("", new Option[] {
                        new CyclingOption<>(Component.translatable("Advanced Chunk Culling"),
                                new Integer[]{1, 2, 3, 10},
                                value -> config.advCulling = value,
                                () -> config.advCulling)
                                .setTranslator(value -> {
                                    String t = switch (value) {
                                        case 1 -> "Aggressive";
                                        case 2 -> "Normal";
                                        case 3 -> "Conservative";
                                        case 10 -> "Off";
                                        default -> "Unk";
                                    };
                                    return Component.nullToEmpty(t);
                                })
                                .setTooltip(Component.translatable("vulkanmod.options.advCulling.tooltip")),
                        new SwitchOption(Component.translatable("Entity Culling"),
                                value -> config.entityCulling = value,
                                () -> config.entityCulling)
                                .setTooltip(Component.translatable("vulkanmod.options.entityCulling.tooltip")),
                        new SwitchOption(Component.translatable("Indirect Draw"),
                                value -> config.indirectDraw = value,
                                () -> config.indirectDraw)
                                .setTooltip(Component.translatable("vulkanmod.options.indirectDraw.tooltip")),
                })
        };

    }

    public static OptionBlock[] getOtherOpts() {
        return new OptionBlock[] {
                new OptionBlock("", new Option[] {
                        new RangeOption(Component.translatable("Render queue size"),
                                2, 5, 1,
                                value -> {
                                    config.frameQueueSize = value;
                                    Renderer.scheduleSwapChainUpdate();
                                }, () -> config.frameQueueSize)
                                .setTooltip(Component.translatable("vulkanmod.options.frameQueue.tooltip")),
                        new CyclingOption<>(Component.translatable("Device selector"),
                                IntStream.range(-1, DeviceManager.suitableDevices.size()).boxed().toArray(Integer[]::new),
                                value -> config.device = value,
                                () -> config.device)
                                .setTranslator(value -> {
                                    String t;

                                    if (value == -1)
                                        t = "Auto";
                                    else
                                        t = DeviceManager.suitableDevices.get(value).deviceName;

                                    return Component.nullToEmpty(t);
                                })
                                .setTooltip(Component.nullToEmpty(
                                String.format("Current device: %s", DeviceManager.device.deviceName)))
                })
        };

    }

    static Integer[] getGuiScaleValues() {
        int max = window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode());

        Integer[] values = new Integer[max];

        for (int i = 0; i < max; i++) {
            values[i] = i;
        }

        return values;
    }

    public static int getMiplevels()
    {
        return minecraftOptions.mipmapLevels().get();
    }
}
