package net.vulkanmod.config.api.page;

import net.vulkanmod.config.api.VkModSettingsEntryBuilder;
import net.vulkanmod.config.option.OptionPage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VkPagesBuilder {
    private final Map<String, VkPageOptionsBuilder> pageBuilders = new LinkedHashMap<>();

    public VkPageOptionsBuilder withPage(String name, VkModSettingsEntryBuilder parent) {
        VkPageOptionsBuilder builder = new VkPageOptionsBuilder(parent);
        pageBuilders.put(name, builder);
        return builder;
    }

    public List<OptionPage> build() {
        final List<OptionPage> pages = new ArrayList<>();
        for (Map.Entry<String, VkPageOptionsBuilder> entry : pageBuilders.entrySet()) {
            pages.add(new OptionPage(
                    entry.getKey(),
                    entry.getValue().build()
            ));
        }
        return pages;
    }
}
