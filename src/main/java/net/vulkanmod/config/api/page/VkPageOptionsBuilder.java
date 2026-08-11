package net.vulkanmod.config.api.page;

import net.vulkanmod.config.api.VkModSettingsEntryBuilder;
import net.vulkanmod.config.gui.OptionBlock;

import java.util.LinkedHashMap;
import java.util.Map;

public class VkPageOptionsBuilder {
    private final VkModSettingsEntryBuilder parent;
    private final Map<String, VkOptionsBuilder> optionBlockBuilders = new LinkedHashMap<>();

    public VkPageOptionsBuilder(VkModSettingsEntryBuilder parent) {
        this.parent = parent;
    }

    public VkOptionsBuilder withOptionBlock(String name) {
        VkOptionsBuilder builder = new VkOptionsBuilder(this);
        optionBlockBuilders.put(name, builder);
        return builder;
    }

    public VkModSettingsEntryBuilder finish() {
        return parent;
    }

    public OptionBlock[] build() {
        final OptionBlock[] blocks = new OptionBlock[optionBlockBuilders.size()];
        int i = 0;
        for (Map.Entry<String, VkOptionsBuilder> entry : optionBlockBuilders.entrySet()) {
            blocks[i] = new OptionBlock(
                    entry.getKey(),
                    entry.getValue().build()
            );
            i++;
        }
        return blocks;
    }
}
