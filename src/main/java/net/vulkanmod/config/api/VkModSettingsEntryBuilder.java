package net.vulkanmod.config.api;

import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.vulkanmod.config.api.page.VkPageOptionsBuilder;
import net.vulkanmod.config.api.page.VkPagesBuilder;
import net.vulkanmod.config.gui.ModSettingsEntry;
import net.vulkanmod.config.option.OptionPage;

import java.util.List;
import java.util.function.Supplier;

public class VkModSettingsEntryBuilder {
    private FormattedText modName;
    private Supplier<Identifier> iconSupplier;
    private Supplier<List<OptionPage>> optionPageSupplier;
    private Runnable onApply;
    private final VkPagesBuilder pageBuilder = new VkPagesBuilder();

    private Supplier<List<OptionPage>> getOptionPageSupplier() {
        if (optionPageSupplier != null) {
            return optionPageSupplier;
        }
        return this.pageBuilder::build;
    }

    public VkModSettingsEntryBuilder setModName(FormattedText modName) {
        this.modName = modName;
        return this;
    }

    public VkModSettingsEntryBuilder setIcon(Identifier icon) {
        this.iconSupplier = () -> icon;
        return this;
    }

    public VkModSettingsEntryBuilder setIconSupplier(Supplier<Identifier> iconSupplier) {
        this.iconSupplier = iconSupplier;
        return this;
    }

    public VkModSettingsEntryBuilder setOptionPageSupplier(Supplier<List<OptionPage>> optionPageSupplier) {
        this.optionPageSupplier = optionPageSupplier;
        return this;
    }

    public VkPageOptionsBuilder withPage(String name) {
        return pageBuilder.withPage(name, this);
    }

    public VkModSettingsEntryBuilder setOnApply(Runnable onApply) {
        this.onApply = onApply;
        return this;
    }

    public ModSettingsEntry build() {
        return new ModSettingsEntry(
                modName,
                iconSupplier,
                getOptionPageSupplier(),
                onApply
        );
    }
}
