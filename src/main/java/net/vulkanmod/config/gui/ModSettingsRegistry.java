package net.vulkanmod.config.gui;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.api.VkModSettingsEntryBuilder;
import net.vulkanmod.config.api.VkModSettingsFactory;
import net.vulkanmod.config.option.Options;

import java.util.Set;

public class ModSettingsRegistry {
    private static final String CONFIG_ENTRY_POINT_KEY = "vulkanmod:mod_settings_registry";

    public static final ModSettingsRegistry INSTANCE = new ModSettingsRegistry();

    private final Set<ModSettingsEntry> modEntries = new ObjectArraySet<>();

    ModSettingsRegistry() {
        ModSettingsEntry vulkanModSettings = new ModSettingsEntry(Component.literal("VulkanMod").withStyle(ChatFormatting.DARK_RED),
                                                                  () -> Identifier.fromNamespaceAndPath("vulkanmod", "vlogo_transparent.png"),
                                                                  Options::getOptionPages,
                                                                  () -> Initializer.CONFIG.write());
        this.addModEntry(vulkanModSettings);

        // build and add vulkanmod settings entrypoints
        var entryPointContainers = FabricLoader.getInstance().getEntrypointContainers(CONFIG_ENTRY_POINT_KEY, VkModSettingsFactory.class);
        for (EntrypointContainer<VkModSettingsFactory> entryPointContainer : entryPointContainers) {
            VkModSettingsFactory modSettingsFactory = entryPointContainer.getEntrypoint();
            VkModSettingsEntryBuilder builder = new VkModSettingsEntryBuilder();

            this.addModEntry(modSettingsFactory.build(builder));
        }
    }

    public void addModEntry(ModSettingsEntry entry) {
        this.modEntries.add(entry);
    }

    public Set<ModSettingsEntry> getModEntries() {
        return modEntries;
    }
}
