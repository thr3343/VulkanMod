package net.vulkanmod.config;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import org.lwjgl.system.MemoryStack;

import static net.vulkanmod.render.chunk.WorldRenderer.taskDispatcher;
import static net.vulkanmod.vulkan.Device.device;
import static net.vulkanmod.vulkan.Device.deviceInfo;
import net.vulkanmod.vulkan.VRenderSystem;

public class Options {
    private static final int max = Runtime.getRuntime().availableProcessors();
    static net.minecraft.client.Options minecraftOptions = Minecraft.getInstance().options;
    static Config config = Initializer.CONFIG;
    static Window window = Minecraft.getInstance().getWindow();
    public static boolean fullscreenDirty = false;
    private static int priorFrameQueue;
    public static final boolean drawIndirectSupported = deviceInfo.isDrawIndirectSupported();

    public static Option<?>[] getVideoOpts() {
        return new Option[] {
                new CyclingOption<>("Resolution",
                        VideoResolution.getVideoResolutions(),
                        resolution -> Component.literal(resolution.toString()),
                        (value) -> {
                            config.resolution = value;
                            fullscreenDirty = true;
                        },
                        () -> config.resolution)
                        .setTooltip(Component.literal("Only works on fullscreen")),
                new SwitchOption("Windowed Fullscreen",
                        value -> {
                            config.windowedFullscreen = value;
                            fullscreenDirty = true;
                        },
                        () -> config.windowedFullscreen)
                        .setTooltip(Component.nullToEmpty("Might not work properly")),
                new SwitchOption("Fullscreen",
                        value -> {
                            minecraftOptions.fullscreen().set(value);
//                            window.toggleFullScreen();
                            fullscreenDirty = true;
                        },
                        () -> minecraftOptions.fullscreen().get()),
                new RangeOption("Max Framerate", 10, 260, 10,
                        value -> value == 260 ? "Unlimited" : String.valueOf(value),
                        value -> {
                            minecraftOptions.framerateLimit().set(value);
                            window.setFramerateLimit(value);
                        },
                        () -> minecraftOptions.framerateLimit().get()),
                new SwitchOption("VSync",
                        value -> {
                            minecraftOptions.enableVsync().set(value);
                            Minecraft.getInstance().getWindow().updateVsync(value);
                        },
                        () -> minecraftOptions.enableVsync().get()),
                new CyclingOption<>("Gui Scale",
                        new Integer[]{0, 1, 2, 3, 4},
                        value -> value == 0 ? Component.literal("Auto") : Component.literal(value.toString()),
                        (value) -> {
                            minecraftOptions.guiScale().set(value);
                            Minecraft.getInstance().resizeDisplay();
                        },
                        () -> minecraftOptions.guiScale().get()),
                new RangeOption("Brightness", 0, 100, 1,
                        value -> {
                          if(value == 0) return Component.translatable("options.gamma.min").getString();
                          else if(value == 50) return Component.translatable("options.gamma.default").getString();
                          else if(value == 100) return Component.translatable("options.gamma.max").getString();
                          return value.toString();
                        },
                        value -> minecraftOptions.gamma().set(value * 0.01),
                        () -> (int) (minecraftOptions.gamma().get() * 100.0)),
                new SwitchOption("Smooth Lighting",
                        value -> minecraftOptions.ambientOcclusion().set(value),
                        () -> minecraftOptions.ambientOcclusion().get()),
                new SwitchOption("View Bobbing",
                        (value) -> minecraftOptions.bobView().set(value),
                        () -> minecraftOptions.bobView().get()),
                new CyclingOption<>("Attack Indicator",
                        AttackIndicatorStatus.values(),
                        value -> Component.translatable(value.getKey()),
                        value -> minecraftOptions.attackIndicator().set(value),
                        () -> minecraftOptions.attackIndicator().get()),
                new SwitchOption("Autosave Indicator",
                        value -> minecraftOptions.showAutosaveIndicator().set(value),
                        () -> minecraftOptions.showAutosaveIndicator().get()),
                new RangeOption("Distortion Effects", 0, 100, 1,
                        value -> minecraftOptions.screenEffectScale().set(value * 0.01),
                        () -> (int)(minecraftOptions.screenEffectScale().get() * 100.0f))
                        .setTooltip(Component.translatable("options.screenEffectScale.tooltip")),
                new RangeOption("FOV Effects", 0, 100, 1,
                        value -> minecraftOptions.fovEffectScale().set(value * 0.01),
                        () -> (int)(minecraftOptions.fovEffectScale().get() * 100.0f))
                        .setTooltip(Component.translatable("options.fovEffectScale.tooltip"))
        };
    }

    public static Option<?>[] getGraphicsOpts() {
        return new Option[] {
                new CyclingOption<>("Graphics",
                        new GraphicsStatus[]{GraphicsStatus.FAST, GraphicsStatus.FANCY},
                        graphicsMode -> Component.translatable(graphicsMode.getKey()),
                        value -> minecraftOptions.graphicsMode().set(value),
                        () -> minecraftOptions.graphicsMode().get()
                ),
                new CyclingOption<>("Particles",
                        new ParticleStatus[]{ParticleStatus.MINIMAL, ParticleStatus.DECREASED, ParticleStatus.ALL},
                        particlesMode -> Component.translatable(particlesMode.getKey()),
                        value -> minecraftOptions.particles().set(value),
                        () -> minecraftOptions.particles().get()),
                new CyclingOption<>("Clouds",
                        CloudStatus.values(),
                        value -> Component.translatable(value.getKey()),
                        value -> minecraftOptions.cloudStatus().set(value),
                        () -> minecraftOptions.cloudStatus().get()),
                new SwitchOption("Unique opaque layer",
                        value -> {
                            config.uniqueOpaqueLayer = value;
                            Minecraft.getInstance().levelRenderer.allChanged();
                        },
                        () -> config.uniqueOpaqueLayer)
                        .setTooltip(Component.nullToEmpty("""
                        Improves performance by using a unique render layer for opaque terrain rendering.
                        It changes distant grass aspect and may cause unexpected texture behaviour""")),
                new RangeOption("Biome Blend Radius", 0, 7, 1,
                        value -> {
                    int v = value * 2 + 1;
                    return v + " x " + v;
                        },
                        (value) -> {
                            minecraftOptions.biomeBlendRadius().set(value);
                            Minecraft.getInstance().levelRenderer.allChanged();
                        },
                        () -> minecraftOptions.biomeBlendRadius().get()),
                new CyclingOption<>("Chunk Builder Mode",
                        PrioritizeChunkUpdates.values(),
                        value -> Component.translatable(value.getKey()),
                        value -> minecraftOptions.prioritizeChunkUpdates().set(value),
                        () -> minecraftOptions.prioritizeChunkUpdates().get()),
                new RangeOption("Render Distance", 2, 32, 1,
                        (value) -> {
                            minecraftOptions.renderDistance().set(value);
                            Minecraft.getInstance().levelRenderer.needsUpdate();
                        },
                        () -> minecraftOptions.renderDistance().get()),
                new RangeOption("Simulation Distance", 5, 32, 1,
                        (value) -> {
                            minecraftOptions.simulationDistance().set(value);
                        },
                        () -> minecraftOptions.simulationDistance().get()),
                new SwitchOption("Entity Shadows",
                        value -> minecraftOptions.entityShadows().set(value),
                        () -> minecraftOptions.entityShadows().get()),
                new RangeOption("Entity Distance", 50, 500, 25,
                        value -> minecraftOptions.entityDistanceScaling().set(value * 0.01),
                        () -> minecraftOptions.entityDistanceScaling().get().intValue() * 100),
                new CyclingOption<>("Particles",
                        new ParticleStatus[]{ParticleStatus.MINIMAL, ParticleStatus.DECREASED, ParticleStatus.ALL},
                        particlesMode -> Component.translatable(particlesMode.getKey()),
                        value -> minecraftOptions.particles().set(value),
                        () -> minecraftOptions.particles().get()),
                new SwitchOption("Unique opaque layer",
                        value -> {
                            config.uniqueOpaqueLayer = value;
                            Minecraft.getInstance().levelRenderer.allChanged();
                        },
                        () -> config.uniqueOpaqueLayer)
                        .setTooltip(Component.nullToEmpty("""
                        Improves performance by using a unique render layer for opaque terrain rendering.
                        It changes distant grass aspect and may cause unexpected texture behaviour""")),
                new SwitchOption("Animations",
                        value -> config.animations = value,
                        () -> config.animations),
                new SwitchOption("Render Sky",
                        value -> {
                            config.renderSky = value;
                            Minecraft.getInstance().levelRenderer.allChanged();
                        },
                        () -> config.renderSky),
                new CyclingOption<>("Mipmap Levels",
                        new Integer[]{0, 1, 2, 3, 4},
                        value -> Component.nullToEmpty(value.toString()),
                        value -> {
                            minecraftOptions.mipmapLevels().set(value);
                            Minecraft.getInstance().delayTextureReload();
                        },
                        () -> minecraftOptions.mipmapLevels().get())
        };
    }

    public static Option<?>[] getOtherOpts() {
        return new Option[] {
                new RangeOption("Render queue size", 1,
                        5, 1,
                        value -> {
                            config.frameQueueSize = value;
                            Renderer.scheduleSwapChainUpdate();
                        }, () -> config.frameQueueSize)
                        .setTooltip(Component.nullToEmpty("""
                Higher values might help stabilize frametime
                but will increase input lag""")),
                new SwitchOption("Gui Optimizations",
                        value -> config.guiOptimizations = value,
                        () -> config.guiOptimizations)
                        .setTooltip(Component.nullToEmpty("""
                                Enable Gui optimizations (Stats bar, Chat, Debug Hud)
                                Might break mod compatibility
                                Restart is needed to take effect""")),
                new CyclingOption<>("Advanced Chunk Culling",
                        new Integer[]{1, 2, 3, 10},
                        value -> {
                            String t = switch (value) {
                                case 1 -> "Aggressive";
                                case 2 -> "Normal";
                                case 3 -> "Conservative";
                                case 10 -> "Off";
                                default -> "Unk";
                            };
                            return Component.nullToEmpty(t);
                        },
                        value -> config.advCulling = value,
                        () -> config.advCulling)
                        .setTooltip(Component.nullToEmpty("""
                                Use a culling algorithm that might improve performance by
                                reducing the number of non visible chunk sections rendered.
                                """)),
                new SwitchOption("Entity Culling",
                        value -> config.entityCulling = value,
                        () -> config.entityCulling)
                        .setTooltip(Component.nullToEmpty("""
                                Enables culling for entities on not visible sections.""")),
                new SwitchOption("Indirect Draw",
                        value -> config.indirectDraw = drawIndirectSupported ? value : false,
                        () -> drawIndirectSupported && config.indirectDraw)
                        .setTooltip(Component.nullToEmpty(
                                "Supported by GPU?: " + drawIndirectSupported + "\n" +
                                        "\n" +
                                        "Reduces CPU overhead but increases GPU overhead.\n" +
                                        "Enabling it might help in CPU limited systems.\n")),
                new SwitchOption("Per RenderType AreaBuffers",
                        value -> {
                            //fre before updating the Config Value
                            Minecraft.getInstance().levelRenderer.allChanged();
                            config.perRenderTypeAreaBuffers = value;
                        },
                        () -> config.perRenderTypeAreaBuffers).setTooltip(Component.nullToEmpty("""
                        Improves GPU Performance
                        But increases VRAM Usage slightly
                        May vary on System and/or GPU configuration""")),
                new SwitchOption("GigaBarriers",
                        value -> {
                            config.useGigaBarriers = value;
                        },
                        () -> config.useGigaBarriers).setTooltip(Component.nullToEmpty("""
                        (Debugging feature)
                        Only Use to fix/troubleshoot game-breaking bugs/crashes
                        Greatly decreases performance if enabled""")),
                new RangeOption("Chunk Load Threads", 1, max, 1,
                        value -> {
                            config.chunkLoadFactor = value;
                            taskDispatcher.stopThreads();
                            taskDispatcher.resizeThreads(value);
                            WorldRenderer.getInstance().allChanged();
                        },
                        () -> config.chunkLoadFactor)
                        .setTooltip(Component.nullToEmpty(
                                "The number of Threads utilized for uploading chunks \n" +
                                        "More threads will greatly improve Chunk load speed" +
                                        "But may cause stuttering if set to high\n" +
                                        "Max Recommended value is " + max / 2 + " threads on This CPU")),
                new RangeOption("buildLimit", 8, 512, 8,
                        value -> {
                            config.buildLimit = value;
                        },
                        () -> config.buildLimit).setTooltip(Component.nullToEmpty("""
                        Max ChunkTask Limit per frame
                        Throttles Chunk Load speed if reduced
                        Multiplied by Active Chunk load Threads to reduce throttling
                        Originally wasn't intended to handle MultiThreaded Workloads
                        VERY BUGGED ATM""")),
                new RangeOption("SSAA", 0, 3, 1,
                        value -> switch (value) {
                            case 1 -> "2x SSAA";
                            case 2 -> "4x SSAA";
                            case 3 -> "8x SSAA";
                            default -> "Off";
                        },

                        value -> {

                            config.ssaaPreset = value;

//                            VRenderSystem.setMultiSampleState(value);
                            VRenderSystem.setSampleState(config.ssaaPreset);
                        },
                        () -> config.ssaaPreset)
                        .setTooltip(Component.nullToEmpty("""
                        SuperSampling Anti-Aliasing"""))
        };


    }

    public static void applyOptions(Config config, Option<?>[][] optionPages) {
        for(Option<?>[] options : optionPages) {
            for(Option<?> option : options) {
                option.apply();
            }
        }

        config.write();
    }
}
