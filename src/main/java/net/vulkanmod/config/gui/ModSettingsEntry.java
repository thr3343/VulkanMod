package net.vulkanmod.config.gui;

import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.config.option.OptionPage;

import java.util.List;
import java.util.function.Supplier;

public class ModSettingsEntry {
    public final FormattedText modName;
    public final Supplier<ResourceLocation> iconSupplier;
    private final Supplier<List<OptionPage>> optionPageSupplier;
    private final Runnable onApply;

    private ResourceLocation icon;
    List<OptionPage> pages;

    public ModSettingsEntry(FormattedText modName, Supplier<ResourceLocation> iconSupplier, Supplier<List<OptionPage>> optionPageSupplier, Runnable onApply) {
        this.modName = modName;
        this.iconSupplier = iconSupplier;
        this.optionPageSupplier = optionPageSupplier;
        this.onApply = onApply;
    }

    public List<OptionPage> initPages() {
        this.pages = this.optionPageSupplier.get();
        return this.pages;
    }

    public List<OptionPage> getPages() {
        return pages;
    }

    public ResourceLocation getIcon() {
        if (this.icon == null) {
            this.icon = this.iconSupplier.get();
        }

        return icon;
    }

    public void runOnApply() {
        onApply.run();
    }
}
