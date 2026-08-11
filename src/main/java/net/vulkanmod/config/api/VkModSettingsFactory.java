package net.vulkanmod.config.api;

import net.vulkanmod.config.gui.ModSettingsEntry;

public interface VkModSettingsFactory {
    ModSettingsEntry build(VkModSettingsEntryBuilder builder);
}
